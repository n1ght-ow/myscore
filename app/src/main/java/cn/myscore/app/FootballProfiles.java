package cn.myscore.app;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.*;

/** Public biographical sources only. FPL points, form and BPS are never match ratings. */
final class FootballProfiles {
    private static final ExecutorService NETWORK=Executors.newFixedThreadPool(3);
    private static final ConcurrentHashMap<String,Long> RETRY=new ConcurrentHashMap<>();
    private static final SingleFlight<String,JSONObject> REQUESTS=new SingleFlight<>();
    private final File dir;
    FootballProfiles(Context context){dir=new File(context.getCacheDir(),"football-profiles-v1");if(!dir.exists())dir.mkdirs();}
    void enrich(MatchDetail detail){
        Competition competition=Competition.find(detail.match.league);
        if(competition==null||!detail.match.group.equals("足球"))return;
        Map<String,Future<JSONObject>> teams=new LinkedHashMap<>();
        for(String id:List.of(detail.match.homeId,detail.match.awayId))if(id.matches("[0-9]+"))teams.put(id,NETWORK.submit(()->load("team-"+id,"https://site.api.espn.com/apis/site/v2/sports/soccer/"+competition.path.substring("soccer/".length())+"/teams/"+id+"/roster")));
        Future<JSONObject> fpl=NETWORK.submit(()->load("fpl","https://fantasy.premierleague.com/api/bootstrap-static/"));
        JSONObject players=await(fpl);boolean enriched=false;
        for(var team:teams.entrySet()){
            JSONObject roster=await(team.getValue());if(!DetailParsers.obj(roster,"team").optString("id").equals(team.getKey()))continue;
            JSONArray athletes=DetailParsers.arr(roster,"athletes");Map<String,JSONObject> identities=new HashMap<>();
            for(int i=0;i<athletes.length();i++){JSONObject athlete=DetailParsers.objAt(athletes,i);identities.put(athlete.optString("id"),athlete);}
            for(MatchDetail.Table table:detail.tables)if(table.tab.equals("lineup")&&table.teamId.equals(team.getKey()))for(int i=0;i<table.people.size();i++){
                MatchDetail.Person person=table.people.get(i);JSONObject athlete=identities.get(person.id());
                if(athlete==null||!normal(person.name()).equals(normal(athlete.optString("displayName"))))continue;
                String photo=photo(athlete,players);if(!photo.isBlank())table.people.set(i,new MatchDetail.Person(person.id(),person.name(),person.shortName(),photo,person.jersey(),person.position(),person.teamId(),person.team(),person.teamLogo(),person.starter(),person.absent()));
                set(table,i,"国籍",DetailParsers.val(athlete,"citizenship"));set(table,i,"年龄",DetailParsers.val(athlete,"age"));enriched=true;
            }
        }
        if(enriched)detail.fact("球员资料来源","ESPN · 英超官方 FPL（照片按姓名与出生日期匹配）");
        detail.fact("阵容资料版本","1");
    }
    static String photo(JSONObject athlete,JSONObject fpl){
        String supplied=DetailParsers.obj(athlete,"headshot").optString("href");
        if(supplied.startsWith("https://a.espncdn.com/")&&!supplied.contains("/teamlogos/")&&!supplied.contains("/jersey"))return supplied;
        JSONObject matched=match(athlete,DetailParsers.arr(fpl,"elements"));
        String code=matched==null?"":DetailParsers.val(matched,"code");
        return code.matches("[1-9][0-9]*")?"https://resources.premierleague.com/premierleague/photos/players/110x140/p"+code+".png":"";
    }
    String backup(MatchDetail.Person person){
        try{
            JSONObject roster=new JSONObject(new String(new AtomicFile(new File(dir,"team-"+person.teamId()+".json")).readFully(),StandardCharsets.UTF_8));
            JSONArray athletes=DetailParsers.arr(roster,"athletes");JSONObject athlete=null;
            for(int i=0;i<athletes.length();i++){JSONObject candidate=DetailParsers.objAt(athletes,i);if(candidate.optString("id").equals(person.id())&&normal(candidate.optString("displayName")).equals(normal(person.name())))athlete=candidate;}
            if(athlete==null)return "";
            JSONObject result=load("backup-"+person.id(),"https://www.thesportsdb.com/api/v1/json/123/searchplayers.php?p="+java.net.URLEncoder.encode(person.name(),"UTF-8"));
            return backupPhoto(athlete,result);
        }catch(Exception e){return "";}
    }
    static String backupPhoto(JSONObject athlete,JSONObject response){
        String dob=athlete.optString("dateOfBirth");if(dob.length()<10)return "";dob=dob.substring(0,10);
        JSONArray players=DetailParsers.arr(response,"player");String photo="";boolean found=false;
        for(int i=0;i<players.length();i++){
            JSONObject player=DetailParsers.objAt(players,i);
            if(!player.optString("strSport").equals("Soccer")||!dob.equals(player.optString("dateBorn"))||!normal(player.optString("strPlayer")).equals(normal(athlete.optString("displayName"))))continue;
            if(found)return "";found=true;
            String candidate=player.optString("strCutout");if(candidate.startsWith("https://r2.thesportsdb.com/images/media/player/"))photo=candidate;
        }
        return photo;
    }
    static JSONObject match(JSONObject athlete,JSONArray candidates){
        String dob=athlete.optString("dateOfBirth");if(dob.length()<10)return null;dob=dob.substring(0,10);
        Set<String> source=tokens(athlete.optString("displayName"));JSONObject found=null;
        for(int i=0;i<candidates.length();i++){
            JSONObject player=DetailParsers.objAt(candidates,i);if(!dob.equals(player.optString("birth_date")))continue;
            String full=player.optString("first_name")+" "+player.optString("second_name");
            boolean exact=normal(full).equals(normal(athlete.optString("displayName")))||!player.optString("known_name").isBlank()&&normal(player.optString("known_name")).equals(normal(athlete.optString("displayName")));
            Set<String> target=tokens(full);boolean complete=source.size()>=2&&target.containsAll(source);
            if(!exact&&!complete)continue;if(found!=null)return null;found=player;
        }
        return found;
    }
    static String normal(String value){return Normalizer.normalize(value,Normalizer.Form.NFKD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");}
    private static Set<String> tokens(String value){Set<String> result=new HashSet<>();for(String token:Normalizer.normalize(value,Normalizer.Form.NFKD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))if(!token.isBlank())result.add(token);return result;}
    private static void set(MatchDetail.Table table,int row,String key,String value){int column=table.columns.indexOf(key);if(column<0||value.isBlank()||row>=table.rows.size())return;List<String> values=new ArrayList<>(table.rows.get(row));while(values.size()<=column)values.add("—");if(FootballMetrics.missing(values.get(column))){values.set(column,value);table.rows.set(row,values);}}
    private JSONObject load(String key,String url){
        try{return REQUESTS.execute(dir.getAbsolutePath()+key,()->{
            File file=new File(dir,key+".json");JSONObject cached=null;long now=System.currentTimeMillis();
            try{cached=new JSONObject(new String(new AtomicFile(file).readFully(),StandardCharsets.UTF_8));}catch(Exception ignored){}
            if(cached!=null&&now-file.lastModified()<86400_000)return cached;
            if(now<RETRY.getOrDefault(key,0L))return cached==null?new JSONObject():cached;
            try{
                JSONObject data=new JSONObject(Repository.get(url));boolean valid=key.equals("fpl")?DetailParsers.arr(data,"elements").length()>0:key.startsWith("backup-")?data.has("player"):DetailParsers.arr(data,"athletes").length()>0;
                if(!valid)throw new IOException("Empty player source");
                AtomicFile atomic=new AtomicFile(file);FileOutputStream out=null;try{out=atomic.startWrite();out.write(data.toString().getBytes(StandardCharsets.UTF_8));atomic.finishWrite(out);}catch(Exception e){if(out!=null)atomic.failWrite(out);}
                return data;
            }catch(Exception e){RETRY.put(key,now+300_000);return cached==null?new JSONObject():cached;}
        });}catch(Exception e){return new JSONObject();}
    }
    private static JSONObject await(Future<JSONObject> task){try{return task.get(25,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();task.cancel(true);return new JSONObject();}catch(Exception e){task.cancel(true);return new JSONObject();}}
}
