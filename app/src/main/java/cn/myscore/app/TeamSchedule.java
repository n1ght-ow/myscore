package cn.myscore.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * A team's own schedule from the same public feeds as the score list. No extra service is
 * contracted for team pages: ESPN exposes a per-team season schedule, 5EPlay a normalized team
 * feed.
 */
final class TeamSchedule {
    private static final ExecutorService WORKERS=Executors.newFixedThreadPool(4,r->{
        Thread t=new Thread(r,"myscore-team-schedule");
        t.setDaemon(true);
        return t;
    });
    static final class Result {
        final List<Match> matches=new ArrayList<>();
        long fetched=0;
        String source="", error="";
    }
    private TeamSchedule() {}

    static Result fetch(Team team,long now)throws Exception {
        Competition c=Competition.find(team.league);
        if(c==null)throw new Exception("未知赛事");
        if(c.id.equals("f1"))throw new Exception("F1 没有球队赛程");
        Result result=new Result();
        if(c.id.equals("cs2")) {result.source="5EPlay · 战队赛程 + 一线赛程";result.matches.addAll(five(team,now));}
        else {result.source="ESPN · 球队赛程";result.matches.addAll(espn(team,c,now));}
        result.fetched=System.currentTimeMillis();
        result.matches.sort(Comparator.comparingLong((Match m)->m.start==0?Long.MAX_VALUE:m.start).thenComparing(m->m.id));
        return result;
    }

    private static List<Match> espn(Team team,Competition c,long now)throws Exception {
        if(!team.id.matches("[0-9]+"))throw new Exception("球队标识无效");
        String base=espnScheduleUrl(c.path,team.id);
        if(!c.group.equals("足球")) {
            JSONObject results=new JSONObject(Repository.get(base));
            verifyTeam(results,team);
            return parseEspn(team,results,null,now);
        }
        // ESPN's soccer team page splits completed results and remaining fixtures into two calls.
        List<String> urls=new ArrayList<>(List.of(base,base+"?fixture=true"));
        if(NationalTeams.follows(team)) {
            String nations=espnScheduleUrl(NationalTeams.NATIONS_PATH,team.id);
            urls.add(nations);urls.add(nations+"?fixture=true");
        }
        List<Future<JSONObject>> tasks=new ArrayList<>();
        try {
            for(String url:urls)tasks.add(WORKERS.submit(()->new JSONObject(Repository.get(url))));
            List<JSONObject> pages=new ArrayList<>();
            for(Future<JSONObject> task:tasks) {
                JSONObject page=awaitJson(task);
                verifyTeam(page,team);
                if(page.optJSONArray("events")==null)throw new Exception("来源未返回赛事列表");
                pages.add(page);
            }
            return parseEspn(team,now,pages.toArray(new JSONObject[0]));
        } finally {
            for(Future<JSONObject> task:tasks)if(!task.isDone())task.cancel(true);
        }
    }
    private static String espnScheduleUrl(String path,String id) {
        return "https://site.api.espn.com/apis/site/v2/sports/"+path+"/teams/"+id+"/schedule";
    }
    private static JSONObject awaitJson(Future<JSONObject> task)throws Exception {
        try{return task.get();}
        catch(ExecutionException e){
            if(e.getCause() instanceof InterruptedException ie)throw ie;
            if(e.getCause() instanceof Exception ex)throw ex;
            throw new RuntimeException(e.getCause());
        }
    }
    static void verifyTeam(JSONObject root,Team team)throws Exception {
        JSONObject source=root.optJSONObject("team");
        if(source!=null&&!source.optString("id").isEmpty()&&!source.optString("id").equals(team.id))throw new Exception("来源返回了其他球队的赛程");
    }
    static List<Match> parseEspn(Team team,JSONObject results,JSONObject fixtures,long now)throws Exception {
        return parseEspn(team,now,results,fixtures);
    }
    static List<Match> parseEspn(Team team,long now,JSONObject... pages)throws Exception {
        Map<String,Match> unique=new LinkedHashMap<>();
        Competition competition=Competition.find(team.league);
        boolean national=NationalTeams.LEAGUE.equals(team.league);
        for(JSONObject page:pages) {
            if(page==null)continue;
            for(Match m:Parsers.espn(page,competition,now)) {
                if(!team.id.equals(m.homeId)&&!team.id.equals(m.awayId))continue;
                Match old=unique.get(m.id);
                if(national&&old!=null&&RefreshPolicy.terminal(old)&&!RefreshPolicy.terminal(m))continue;
                if(old==null||m.updated>=old.updated)unique.put(m.id,m);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private static List<Match> five(Team team,long now)throws Exception {
        // The public team feed accepts the same ids as match rows, including hltv_ aliases.
        if(!team.id.matches("[a-zA-Z0-9_]+"))throw new Exception("球队标识无效");
        Map<String,Match> unique=new LinkedHashMap<>();
        for(String status:new String[]{"future","past"}) {
            JSONObject root=new JSONObject(Repository.get(DomesticEsports.FIVE+"team/matches_v1/"+team.id+"?page=1&limit=30&status="+status));
            if(!root.optBoolean("success"))throw new Exception("5EPlay 暂未返回该队赛程，请稍后刷新重试");
            for(Match m:parseFive(team,root,now))unique.put(m.id,m);
        }
        // The team feed's future page is often empty even when the team has a published tier-one
        // match; fill "接下来" from the same global tier-one list used by the main schedule.
        List<Match> enrichment=new ArrayList<>();
        try {
            LocalDate today=Match.today(now);
            for(Match m:DomesticEsports.tierOneMatches(today,today.plusDays(60),now)) {
                if(team.id.equals(m.homeId)||team.id.equals(m.awayId))enrichment.add(m);
            }
        } catch(Exception e) {
            android.util.Log.i("MyScore","CS2 team upcoming enrichment unavailable: "+e.getMessage());
        }
        return merge(new ArrayList<>(unique.values()),enrichment);
    }
    static List<Match> merge(List<Match> feed,List<Match> tierOne) {
        Map<String,Match> unique=new LinkedHashMap<>();
        if(feed!=null)for(Match m:feed)if(m!=null)unique.put(m.id,m);
        if(tierOne!=null)for(Match m:tierOne)if(m!=null)unique.putIfAbsent(m.id,m);
        return new ArrayList<>(unique.values());
    }
    static List<Match> parseFive(Team team,JSONObject root,long now)throws Exception {
        JSONObject data=root.optJSONObject("data");
        if(data==null)throw new Exception("5EPlay 战队赛程格式变化");
        JSONObject items=data.optJSONObject("items");
        JSONArray rows=items==null?null:items.optJSONArray("matches");
        if(rows==null)throw new Exception("5EPlay 战队赛程格式变化");
        List<Match> matches=new ArrayList<>();
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.optJSONObject(i);
            if(row==null)continue;
            JSONObject mc=obj(row,"mc_info"),home=obj(mc,"home_info"),away=obj(mc,"opponent_info");
            if(!team.id.equals(home.optString("id"))&&!team.id.equals(away.optString("id")))continue;
            String id=mc.optString("id");
            if(!id.matches("csgo_mc_[0-9]+"))continue;
            Match m=new Match();
            m.id="five:cs2:"+id;m.league="cs2";m.group="CS2";m.source="5EPlay";m.updated=now;
            m.home=DomesticEsports.teamName(home);m.away=DomesticEsports.teamName(away);
            m.homeId=DetailParsers.text(home,"id");m.awayId=DetailParsers.text(away,"id");
            m.homeLogo=DomesticEsports.crest(home);m.awayLogo=DomesticEsports.crest(away);
            m.homeRecord=DomesticEsports.rankTag(home);m.awayRecord=DomesticEsports.rankTag(away);
            m.start=seconds(mc.optString("ts"));if(m.start==0)m.start=seconds(mc.optString("plan_ts"));m.timeConfirmed=m.start>0;
            String status=mc.optString("match_status");
            m.state=switch(status) {
                case "0" -> "scheduled";
                case "1" -> "live";
                case "2" -> "final";
                default -> Rules.state(mc.optString("status"),"");
            };
            m.status=Rules.stateLabel(m.state);
            if(!m.scheduled()) {
                String homeScore=mc.optString("home_score"),awayScore=mc.optString("opponent_score");
                if(!homeScore.isEmpty()&&!awayScore.isEmpty()&&!"null".equals(homeScore)&&!"null".equals(awayScore)) {m.homeScore=homeScore;m.awayScore=awayScore;}
            }
            JSONObject tournament=obj(row,"tt_info");
            m.event=DetailParsers.text(tournament,"disp_name").trim();
            m.tierOne=DomesticEsports.tierOne(row);
            String format=DetailParsers.text(mc,"format"),stage=DetailParsers.text(mc,"tt_stage_desc");
            m.detail=(m.event+(format.isEmpty()?"":" · BO"+format)+(stage.isEmpty()?"":" · "+stage)).trim();
            m.url="https://www.5eplay.com/";
            matches.add(m);
        }
        return matches;
    }
    private static long seconds(String value) {
        try{return Long.parseLong(value)*1000;}
        catch(RuntimeException e){return 0;}
    }
    private static JSONObject obj(JSONObject parent,String key) {
        JSONObject value=parent.optJSONObject(key);
        return value==null?new JSONObject():value;
    }

}
