package cn.myscore.app;

import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Read-only adapters for the exact event/session. No third-party proxy or API key. */
public final class DetailSources {
    private DetailSources(){}
    private static JSONObject teams;
    private static long teamsAt;
    private static String publicClient="";
    private static long clientAt;
    public static MatchDetail fetch(Match previous)throws Exception {
        Competition c=Competition.find(previous.league);if(c==null)throw new Exception("未知赛事");long now=System.currentTimeMillis();
        if(previous.id.startsWith("espn:")){
            String[] ids=previous.id.split(":");if(ids.length!=4||!ids[2].matches("[0-9]+")||!ids[3].matches("[0-9]+"))throw new Exception("比赛标识无效");
            if(c.id.equals("f1"))return f1(previous,ids,now);
            return DetailParsers.espn(new JSONObject(Repository.get("https://site.api.espn.com/apis/site/v2/sports/"+c.path+"/summary?event="+ids[2])),previous,now);
        }
        if(previous.id.startsWith("five:cs2:")){String id=previous.id.substring(9);if(!id.matches("csgo_mc_[0-9]+"))throw new Exception("比赛标识无效");return DetailParsers.five(new JSONObject(Repository.get(DomesticEsports.FIVE+"matches/"+id+"/data")),previous,now);}
        if(previous.id.startsWith("qq:lol:"))return lol(previous,now);
        throw new Exception("暂不支持该来源详情");
    }
    public static JSONObject assignment(String script)throws Exception {
        String s=script.trim();int first=s.indexOf('{'),last=s.lastIndexOf('}');if(first<0||last<first||first>100)throw new Exception("官网数据格式变化");
        // Data only: never execute JavaScript or accept expressions following the object.
        if(!s.substring(last+1).trim().matches(";?"))throw new Exception("官网数据格式变化");return new JSONObject(s.substring(first,last+1));
    }
    static synchronized void enrichWorlds(List<Match> list)throws Exception {
        if(teams==null||System.currentTimeMillis()-teamsAt>86400_000){teams=DetailParsers.obj(assignment(Repository.get("https://lpl.qq.com/web201612/data/LOL_MATCH2_TEAM_LIST.js")),"msg");teamsAt=System.currentTimeMillis();}
        for(Match m:list){m.homeLogo=https(DetailParsers.obj(teams,m.homeId).optString("TeamLogo"));m.awayLogo=https(DetailParsers.obj(teams,m.awayId).optString("TeamLogo"));}
    }
    static String https(String url){return url.startsWith("//")?"https:"+url:url.startsWith("http://img.crawler.qq.com/")?url.replaceFirst("http:","https:"):url;}
    private static synchronized String websiteClient(boolean refresh)throws Exception {
        if(refresh||publicClient.isEmpty()||System.currentTimeMillis()-clientAt>3600_000){String js=Repository.get("https://lpl.qq.com/web201612/js/after-data-1.js");Matcher m=Pattern.compile("Authorization\\s*:\\s*\"([^\"]{8,128})\"").matcher(js);if(!m.find())throw new Exception("官网公开客户端配置已变化");publicClient=m.group(1);clientAt=System.currentTimeMillis();}return publicClient;
    }
    private static MatchDetail lol(Match previous,long now)throws Exception {
        String mid=previous.id.substring(7);if(!mid.matches("[0-9]+"))throw new Exception("比赛标识无效");
        JSONObject base=assignment(Repository.get("https://lpl.qq.com/web201612/data/LOL_MATCH_DETAIL_"+mid+".js"));
        JSONObject rich=new JSONObject();String warning="";
        try{rich=new JSONObject(Repository.get("https://open.tjstats.com/match-auth-app/open/v1/compound/matchDetail?matchId="+mid,Collections.singletonMap("Authorization",websiteClient(false))));if(!rich.optBoolean("success")){if(previous.finalised()||previous.live())warning="官网暂未提供这场比赛的小局数据。";}}
        catch(Exception e){warning="小局数据暂时无法连接，保留官网赛程信息。";}
        MatchDetail detail=DetailParsers.lol(base,rich,previous,now);detail.warning=warning;
        try{enrichWorlds(Collections.singletonList(detail.match));}catch(Exception e){/* Detail TeamIcon remains valid. */}
        return detail;
    }
    private static MatchDetail f1(Match previous,String[] ids,long now)throws Exception {
        int year=previous.day()==null?Match.today(now).getYear():previous.day().getYear();JSONObject board=new JSONObject(Repository.get("https://site.api.espn.com/apis/site/v2/sports/racing/f1/scoreboard?dates="+year+"&limit=1000"));JSONObject event=null,session=null;
        JSONArray events=DetailParsers.arr(board,"events");for(int i=0;i<events.length();i++){JSONObject e=DetailParsers.objAt(events,i);if(e.optString("id").equals(ids[2])){event=e;JSONArray cs=DetailParsers.arr(e,"competitions");for(int j=0;j<cs.length();j++){JSONObject co=DetailParsers.objAt(cs,j);if(co.optString("id").equals(ids[3]))session=co;}}}
        if(event==null||session==null)throw new Exception("来源未找到该 F1 场次");
        JSONObject core=new JSONObject(Repository.get("https://sports.core.api.espn.com/v2/sports/racing/leagues/f1/events/"+ids[2]+"/competitions/"+ids[3]+"?lang=en&region=us"));if(!core.optString("id").equals(ids[3]))throw new Exception("F1 场次身份不匹配");
        JSONArray drivers=DetailParsers.arr(core,"competitors");ExecutorService workers=Executors.newFixedThreadPool(4);Map<String,Future<JSONObject>> tasks=new LinkedHashMap<>();
        try{for(int i=0;i<drivers.length();i++){JSONObject driver=DetailParsers.objAt(drivers,i);String id=driver.optString("id");if(!id.matches("[0-9]+"))continue;tasks.put(id,workers.submit(()->new JSONObject(Repository.get("https://sports.core.api.espn.com/v2/sports/racing/leagues/f1/events/"+ids[2]+"/competitions/"+ids[3]+"/competitors/"+id+"/statistics?lang=en&region=us"))));}
            Map<String,JSONObject> statistics=new LinkedHashMap<>();int failures=0;for(Map.Entry<String,Future<JSONObject>> t:tasks.entrySet()){try{statistics.put(t.getKey(),t.getValue().get());}catch(Exception e){failures++;}}
            MatchDetail d=DetailParsers.f1(event,session,core,statistics,previous,now);if(failures>0)d.warning=failures+" 位车手的统计暂未获取，空缺不代表 0。";return d;
        }finally{workers.shutdownNow();}
    }
}
