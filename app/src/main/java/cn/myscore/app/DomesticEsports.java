package cn.myscore.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Public website feeds; no login, SDK, API key, or third-party relay. */
public final class DomesticEsports {
    public static final String LOL="https://lpl.qq.com/web201612/data/LOL_MATCH2_MATCH_HOMEPAGE_BMATCH_LIST.js";
    public static final String FIVE="https://esports-data.5eplaycdn.com/v1/api/csgo/";
    // Major (1), S+ (7) and S (2) are tier one; A/B/C, qualifiers and showmatches are not.
    static final String TIER_ONE="https://app.5eplay.com/api/tournament/session_list?game_type=1&game_status=0&grades=1%2C2%2C7&limit=100";
    static final int MAX_TIER_PAGES=8;
    interface Source {String get(String url) throws Exception;}
    private static JSONObject obj(JSONObject o,String k){JSONObject v=o.optJSONObject(k);return v==null?new JSONObject():v;}
    private static JSONArray arr(JSONObject o,String k){JSONArray v=o.optJSONArray(k);return v==null?new JSONArray():v;}
    private static String score(JSONObject o,String k){return o.isNull(k)?"":o.optString(k);}
    private static long seconds(String s){try{return Long.parseLong(s)*1000;}catch(RuntimeException e){return 0;}}
    private static long localTime(String s){try{return LocalDateTime.parse(s,Formats.SOURCE_TIMESTAMP).atZone(Match.ZONE).toInstant().toEpochMilli();}catch(RuntimeException e){return 0;}}
    static boolean isTierOne(String grade,String label){
        return "1".equals(grade)||"2".equals(grade)||"7".equals(grade)
            ||"Major".equals(label)||"S+赛事".equals(label)||"S级赛事".equals(label);
    }
    static boolean tierOne(JSONObject row){
        JSONObject mc=obj(row,"mc_info"),tt=obj(row,"tt_info");
        return isTierOne(DetailParsers.text(tt,"grade",DetailParsers.text(mc,"grade")),DetailParsers.text(tt,"grade_label"));
    }
    static String teamName(JSONObject team){
        String name=DetailParsers.text(team,"disp_name").trim();
        return name.isEmpty()||name.equalsIgnoreCase("TBD")||name.equals("待定")?"对阵待定":name;
    }
    static String crest(JSONObject team){
        String url=DetailSources.https(DetailParsers.text(team,"logo"));
        return url.endsWith("/null.png")?"":url;
    }
    static boolean spiritMatch(Match match){
        if(match==null)return false;
        if(!match.homeId.isEmpty()||!match.awayId.isEmpty()) {
            return "csgo_tm_7020".equals(match.homeId)||"csgo_tm_7020".equals(match.awayId);
        }
        return "SPIRIT".equals(BilibiliVods.cs2Team(match.home))||"SPIRIT".equals(BilibiliVods.cs2Team(match.away));
    }
    public static List<Match> worlds(JSONObject root,long fetched) throws Exception {
        if(!root.optString("status").equals("0") || root.optJSONArray("msg")==null)throw new Exception("LOL 官方赛程格式发生变化");
        List<Match> all=new ArrayList<>();JSONArray list=arr(root,"msg");long published=localTime(root.optString("lastUpTime"));
        for(int i=0;i<list.length();i++) {
            JSONObject e=list.optJSONObject(i);if(e==null)continue;
            String tournament=e.optString("GameName");if(!tournament.matches(".*全球总决赛.*") && !Rules.isWorlds(tournament,""))continue;
            String id=e.optString("bMatchId");if(id.isEmpty())continue;
            Match m=new Match();m.id="qq:lol:"+id;m.league="lol";m.group="LOL";m.source="英雄联盟赛事官网";
            m.updated=published>0&&published<=fetched+300_000?published:fetched;
            m.start=localTime(e.optString("MatchDate"));m.timeConfirmed=m.start>0;
            m.state=switch(e.optString("MatchStatus")){case "1"->"scheduled";case "2"->"live";case "3"->"final";default->"unknown";};
            m.status=Rules.stateLabel(m.state);String name=e.optString("bMatchName");String[] teams=name.split("(?i)\\s+vs\\s+");
            m.home=DetailParsers.text(e,"TeamShortNameA");m.away=DetailParsers.text(e,"TeamShortNameB");
            m.homeId=DetailParsers.text(e,"TeamA");m.awayId=DetailParsers.text(e,"TeamB");
            if(teams.length==2){if(m.home.isEmpty())m.home=teams[0];if(m.away.isEmpty())m.away=teams[1];}
            else if(!name.isEmpty())m.title=DetailParsers.plain(name);
            if(m.home.isEmpty())m.home="对阵待定";if(m.away.isEmpty())m.away="对阵待定";
            if(!m.scheduled()){m.homeScore=score(e,"ScoreA");m.awayScore=score(e,"ScoreB");}
            m.detail=DetailParsers.text(e,"GameName")+" · "+DetailParsers.text(e,"GameModeName")+" · "+DetailParsers.text(e,"GameProcName");m.url="https://lpl.qq.com/";
            all.add(m);
        }
        return all;
    }
    private static Match fiveBase(JSONObject mc,JSONObject tournament,long fetched) {
        Match m=new Match();m.id="five:cs2:"+mc.optString("id");m.league="cs2";m.group="CS2";m.source="5EPlay";m.updated=fetched;
        JSONObject home=obj(mc,"t1_info"),away=obj(mc,"t2_info");
        m.home=teamName(home);m.away=teamName(away);
        m.homeId=DetailParsers.text(home,"id");m.awayId=DetailParsers.text(away,"id");
        m.homeLogo=crest(home);m.awayLogo=crest(away);
        // The live feed uses ts for the scheduled start; the team feed may only carry plan_ts.
        m.start=seconds(mc.optString("ts"));if(m.start==0)m.start=seconds(mc.optString("plan_ts"));m.timeConfirmed=m.start>0;
        m.event=DetailParsers.text(tournament,"disp_name").trim();
        m.tierOne=isTierOne(DetailParsers.text(tournament,"grade",DetailParsers.text(mc,"grade")),DetailParsers.text(tournament,"grade_label"));
        String format=DetailParsers.text(mc,"format"),stage=DetailParsers.text(mc,"tt_stage_desc"),detail=m.event;
        if(!format.isEmpty())detail+=(detail.isEmpty()?"":" · ")+"BO"+format;
        if(!stage.isEmpty())detail+=(detail.isEmpty()?"":" · ")+stage;
        m.detail=detail;m.url="https://www.5eplay.com/";return m;
    }
    public static boolean spirit(JSONObject first,JSONObject second){return first.optString("id").equals("csgo_tm_7020")||second.optString("id").equals("csgo_tm_7020");}
    /** Any 5EPlay match may be opened from the main list or from a team page. */
    public static Match fiveMatch(JSONObject match,long fetched,boolean confirmed) throws Exception {
        JSONObject mc=obj(match,"mc_info");
        Match m=fiveBase(mc,obj(match,"tt_info"),fetched);JSONObject state=obj(match,confirmed?"global_state":"state");
        m.state=switch(state.optString("status")){case "0"->"scheduled";case "1"->"live";case "2"->"final";default->"unknown";};m.status=Rules.stateLabel(m.state);
        if(!m.scheduled()){m.homeScore=score(state,"t1_score");m.awayScore=score(state,"t2_score");}
        JSONArray maps=arr(confirmed?match:state,confirmed?"bouts_state":"bout_states");
        for(int i=0;i<maps.length();i++) {
            JSONObject map=maps.optJSONObject(i);if(map==null || !map.optString("status").equals("2"))continue;
            String text="第 "+map.optString("bout_num")+" 图 · "+map.optString("map_name");
            if(!confirmed && !score(map,"t1_score").isEmpty() && !score(map,"t2_score").isEmpty())text+=" · "+score(map,"t1_score")+" : "+score(map,"t2_score");
            String winner=map.optString("result");text+=winner.equals("t1")?" · "+m.home+" 获胜":winner.equals("t2")?" · "+m.away+" 获胜":" · 已结束";m.results.add(text);
        }
        return m;
    }
    static Match tierOneMatch(JSONObject row,long fetched) throws Exception {
        if(!tierOne(row))return null;
        JSONObject mc=obj(row,"mc_info");String id=mc.optString("id");
        if(!id.matches("csgo_mc_[0-9]+"))return null;
        return fiveMatch(row,fetched,false);
    }
    public static List<Match> tierOneMatches(LocalDate from,LocalDate to,long now) throws Exception {
        return tierOneMatches(from,to,now,Repository::get);
    }
    static List<Match> tierOneMatches(LocalDate from,LocalDate to,long now,Source source) throws Exception {
        if(to.isBefore(from))to=from;
        long fromStart=from.atStartOfDay(Match.ZONE).toInstant().toEpochMilli();
        long toEnd=to.plusDays(1).atStartOfDay(Match.ZONE).toInstant().toEpochMilli()-1;
        Map<String,Match> all=new LinkedHashMap<>();
        for(int page=1;page<=MAX_TIER_PAGES;page++) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            JSONObject root=new JSONObject(source.get(TIER_ONE+"&page="+page));
            if(!root.optBoolean("success"))throw new Exception("5EPlay 一线赛程读取失败");
            JSONArray rows=obj(root,"data").optJSONArray("matches");
            if(rows==null)throw new Exception("5EPlay 一线赛程格式变化");
            if(rows.length()==0)break;
            long oldest=Long.MAX_VALUE;
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.optJSONObject(i);if(row==null)continue;
                JSONObject mc=obj(row,"mc_info");
                long start=seconds(mc.optString("ts"));if(start==0)start=seconds(mc.optString("plan_ts"));
                if(start>0)oldest=Math.min(oldest,start);
                if(start<fromStart||start>toEnd)continue;
                Match m=tierOneMatch(row,now);if(m!=null)all.putIfAbsent(m.id,m);
            }
            // The list is newest-first. Once a page reaches before the requested day, later pages
            // cannot contain it; a short page is the end of the feed.
            if(oldest==Long.MAX_VALUE)throw new Exception("5EPlay 一线赛程缺少开赛时间");
            if(oldest<fromStart||rows.length()<100)break;
            if(page==MAX_TIER_PAGES)throw new Exception("CS2 一线赛事仅提供最近约 1 年");
        }
        return new ArrayList<>(all.values());
    }
    static List<Match> merge(List<Match> spirit,List<Match> tierOne) {
        Map<String,Match> all=new LinkedHashMap<>();
        if(spirit!=null)for(Match m:spirit)if(m!=null)all.put(m.id,m);
        // Keep the team-feed version of a duplicate so its home/opponent orientation stays stable.
        if(tierOne!=null)for(Match m:tierOne)if(m!=null)all.putIfAbsent(m.id,m);
        return new ArrayList<>(all.values());
    }
    public static List<Match> cs2(LocalDate from,LocalDate to,long now) throws Exception {
        return merge(spirit(from,now),tierOneMatches(from,to,now));
    }
    public static List<Match> history(JSONObject root,long fetched) throws Exception {
        if(!root.optBoolean("success"))throw new Exception("5EPlay 战队赛程返回失败");
        JSONObject data=obj(root,"data"),items=obj(data,"items");if(items.optJSONArray("matches")==null)throw new Exception("5EPlay 战队赛程格式变化");
        List<Match> all=new ArrayList<>();JSONArray rows=arr(items,"matches");
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.optJSONObject(i);if(row==null)continue;JSONObject mc=obj(row,"mc_info"),home=obj(mc,"home_info"),away=obj(mc,"opponent_info");
            if(!spirit(home,away))continue;
            Match m=fiveBase(mc,obj(row,"tt_info"),fetched);m.home=teamName(home);m.away=teamName(away);m.start=seconds(mc.optString("ts"));if(m.start==0)m.start=seconds(mc.optString("plan_ts"));m.timeConfirmed=m.start>0;
            m.homeId=DetailParsers.text(home,"id");m.awayId=DetailParsers.text(away,"id");m.homeLogo=crest(home);m.awayLogo=crest(away);
            m.state=switch(mc.optString("status")){case "past"->"final";case "future","upcoming"->"scheduled";case "live"->"live";default->"unknown";};m.status=Rules.stateLabel(m.state);
            // History is in home/opponent coordinates, not t1/t2 coordinates.
            if(!m.scheduled()){m.homeScore=score(mc,"home_score");m.awayScore=score(mc,"opponent_score");}
            all.add(m);
        }
        return all;
    }
    public static List<Match> spirit(LocalDate from,long now) throws Exception {
        Map<String,Match> all=new LinkedHashMap<>();
        for(String status:new String[]{"future","past"}) {
            for(int page=1;page<=5;page++) {
                JSONObject root=new JSONObject(Repository.get(FIVE+"team/matches_v1/csgo_tm_7020?page="+page+"&limit=30&status="+status));
                List<Match> parsed=history(root,now);for(Match m:parsed)all.put(m.id,m);
                int pages=obj(root,"data").optInt("total_page");
                boolean reachedOld=parsed.stream().anyMatch(m->m.day()!=null&&m.day().isBefore(from));
                if(page>=pages || (status.equals("past")&&reachedOld))break;
                if(page==5)throw new Exception("Spirit 战队赛程超过分页上限");
            }
        }
        boolean found=false;
        for(int page=1;page<=5&&!found;page++) {
            JSONObject root=new JSONObject(Repository.get("https://app.5eplay.com/api/tournament/session_list?game_status=1&game_type=1&grades=&page="+page+"&limit=20"));
            if(!root.optBoolean("success") || obj(root,"data").optJSONArray("matches")==null)throw new Exception("5EPlay 当前赛程读取失败");
            JSONArray rows=arr(obj(root,"data"),"matches");
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.optJSONObject(i);if(row==null)continue;JSONObject mc=obj(row,"mc_info");if(!spirit(obj(mc,"t1_info"),obj(mc,"t2_info")))continue;
                String id=mc.optString("id");if(!id.matches("csgo_mc_[0-9]+"))continue;
                Match m=fiveMatch(row,now,false);
                if(m.live()) {
                    JSONObject detail=new JSONObject(Repository.get(FIVE+"matches/"+id+"/data"));
                    if(!detail.optBoolean("success"))throw new Exception("Spirit 比赛详情暂不可用");
                    Match authoritative=fiveMatch(obj(obj(detail,"data"),"match"),now,true);
                    if(!authoritative.id.equals(m.id))throw new Exception("Spirit 比赛详情标识不匹配");
                    // Preserve only finished-map scores from the matching identity and source coordinates.
                    if(authoritative.home.equals(m.home)&&authoritative.away.equals(m.away)&&!m.results.isEmpty()) {authoritative.results.clear();authoritative.results.addAll(m.results);}
                    m=authoritative;found=true;
                }
                all.put(m.id,m);
            }
            if(rows.length()<20)break;
        }
        return new ArrayList<>(all.values());
    }
}
