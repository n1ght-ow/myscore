package cn.myscore.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A team's own schedule from the same public feeds as the score list. No extra service is
 * contracted for team pages: ESPN exposes a per-team season schedule, 5EPlay a normalized team
 * feed, and the Worlds list is filtered by the source team id.
 */
final class TeamSchedule {
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
        if(c.id.equals("cs2")) {result.source="5EPlay · 战队赛程";result.matches.addAll(five(team,now));}
        else if(c.id.equals("lol")) {result.source="英雄联盟赛事官网 · Worlds";result.matches.addAll(worlds(team,now));}
        else {result.source="ESPN · 球队赛程";result.matches.addAll(espn(team,c,now));}
        result.fetched=System.currentTimeMillis();
        result.matches.sort(Comparator.comparingLong((Match m)->m.start==0?Long.MAX_VALUE:m.start).thenComparing(m->m.id));
        return result;
    }

    private static List<Match> espn(Team team,Competition c,long now)throws Exception {
        if(!team.id.matches("[0-9]+"))throw new Exception("球队标识无效");
        String base="https://site.api.espn.com/apis/site/v2/sports/"+c.path+"/teams/"+team.id+"/schedule";
        JSONObject results=new JSONObject(Repository.get(base));
        verifyTeam(results,team);
        JSONObject fixtures=null;
        // ESPN's soccer team page splits completed results and remaining fixtures into two calls.
        if(c.group.equals("足球")) {
            fixtures=new JSONObject(Repository.get(base+"?fixture=true"));
            verifyTeam(fixtures,team);
        }
        return parseEspn(team,results,fixtures,now);
    }
    static void verifyTeam(JSONObject root,Team team)throws Exception {
        JSONObject source=root.optJSONObject("team");
        if(source!=null&&!source.optString("id").isEmpty()&&!source.optString("id").equals(team.id))throw new Exception("来源返回了其他球队的赛程");
    }
    static List<Match> parseEspn(Team team,JSONObject results,JSONObject fixtures,long now)throws Exception {
        Map<String,Match> unique=new LinkedHashMap<>();
        List<JSONObject> pages=new ArrayList<>();
        pages.add(results);
        if(fixtures!=null)pages.add(fixtures);
        for(JSONObject page:pages) {
            if(page==null)continue;
            for(Match m:Parsers.espn(page,Competition.find(team.league),now)) {
                if(!team.id.equals(m.homeId)&&!team.id.equals(m.awayId))continue;
                Match old=unique.get(m.id);
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
            m.home=home.optString("disp_name","对阵待定");m.away=away.optString("disp_name","对阵待定");
            m.homeId=home.optString("id");m.awayId=away.optString("id");
            m.homeLogo=DetailSources.https(home.optString("logo"));m.awayLogo=DetailSources.https(away.optString("logo"));
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
                if(!homeScore.isEmpty()&&!awayScore.isEmpty()) {m.homeScore=homeScore;m.awayScore=awayScore;}
            }
            JSONObject tournament=obj(row,"tt_info");
            m.detail=(tournament.optString("disp_name")+(mc.optString("format").isEmpty()?"":" · BO"+mc.optString("format"))+(mc.optString("tt_stage_desc").isEmpty()?"":" · "+mc.optString("tt_stage_desc"))).trim();
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

    private static List<Match> worlds(Team team,long now)throws Exception {
        List<Match> matches=parseWorlds(team,new JSONObject(Repository.get(DomesticEsports.LOL)),now);
        try{DetailSources.enrichWorlds(matches);}
        catch(Exception e){android.util.Log.i("MyScore","Worlds crests temporarily unavailable");}
        return matches;
    }
    static List<Match> parseWorlds(Team team,JSONObject root,long now)throws Exception {
        List<Match> matches=new ArrayList<>();
        for(Match m:DomesticEsports.worlds(root,now))if(team.id.equals(m.homeId)||team.id.equals(m.awayId))matches.add(m);
        return matches;
    }
}
