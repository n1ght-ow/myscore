package cn.myscore.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Parsers {
    private Parsers() {}
    private static JSONObject obj(JSONObject o, String k) { JSONObject n=o.optJSONObject(k); return n==null?new JSONObject():n; }
    private static JSONArray arr(JSONObject o, String k) { JSONArray n=o.optJSONArray(k); return n==null?new JSONArray():n; }
    private static String value(JSONObject o, String k) {
        if(o.isNull(k)) return "";
        JSONObject nested=o.optJSONObject(k);
        return nested==null?o.optString(k, ""):nested.optString("displayValue", nested.optString("value", ""));
    }

    public static List<Match> espn(JSONObject root, Competition c, long fetched) throws Exception {
        if(!root.has("events")) throw new Exception("来源未返回赛事列表");
        JSONArray events=arr(root, "events");
        if(events.length()>=1000) throw new Exception("赛事列表达到来源上限，覆盖可能不完整");
        List<Match> list=new ArrayList<>();
        for(int i=0;i<events.length();i++) {
            JSONObject e=events.optJSONObject(i); if(e==null) continue;
            JSONArray comps=arr(e, "competitions");
            for(int j=0;j<comps.length();j++) {
                JSONObject comp=comps.optJSONObject(j); if(comp==null) continue;
                String eventId=e.optString("id");
                String compId=comp.optString("id");
                if(eventId.isEmpty() || compId.isEmpty()) continue;
                Match m=new Match(); m.id="espn:"+c.id+":"+eventId+":"+compId;
                m.league=c.id; m.group=c.group; m.source="ESPN"; m.updated=fetched;
                m.start=Match.parseTime(comp.optString("date", e.optString("date")));
                m.timeConfirmed=m.start>0 && comp.optBoolean("timeValid", true);
                JSONObject status=comp.optJSONObject("status"); if(status==null) status=obj(e, "status");
                JSONObject type=obj(status, "type");
                m.state=Rules.state(type.optString("name"), type.optString("state"));
                String raw=type.optString("name"), clock=status.optString("displayClock");
                m.status=Rules.stateLabel(m.state);
                if(m.live()) {
                    if(raw.contains("HALFTIME")) m.status="中场休息";
                    else if(raw.contains("END_PERIOD")) m.status="节间休息";
                    else if(c.group.equals("足球")) m.status=clock.isEmpty()?"进行中":clock;
                    else if(!c.group.equals("F1")) {
                        int period=status.optInt("period");
                        m.status=(period>4?"加时 "+(period-4):period>0?"第 "+period+" 节":"进行中")+(clock.isEmpty()?"":" · "+clock);
                    }
                }
                JSONArray links=arr(e, "links");
                for(int k=0;k<links.length();k++) {
                    String link=links.optJSONObject(k)==null?"":links.optJSONObject(k).optString("href");
                    if(link.startsWith("https://www.espn.com/")) { m.url=link; break; }
                }
                if(c.id.equals("f1")) {
                    String abbr=obj(comp, "type").optString("abbreviation");
                    String session=switch(abbr) {case "FP1"->"练习赛 1";case "FP2"->"练习赛 2";case "FP3"->"练习赛 3";case "Qual", "Q"->"排位赛";case "Race"->"正赛";case "Sprint"->"冲刺赛";case "Sprint Qual", "Sprint Shootout", "SQ"->"冲刺排位";default->abbr.isEmpty()?"场次待确认":abbr;};
                    m.title=e.optString("name", "F1")+" · "+session;
                    if(m.live() && (abbr.equals("Race") || abbr.equals("Sprint")) && status.optInt("period")>0) m.status="第 "+status.optInt("period")+" 圈";
                    JSONArray drivers=arr(comp,"competitors");
                    List<JSONObject> sorted=new ArrayList<>();
                    for(int k=0;k<drivers.length();k++) if(drivers.optJSONObject(k)!=null) sorted.add(drivers.optJSONObject(k));
                    sorted.sort(Comparator.comparingInt(o->o.optInt("order",999)));
                    if(!m.scheduled()) for(JSONObject driver:sorted) {
                        String name=obj(driver,"athlete").optString("displayName");
                        if(name.isEmpty()) continue;
                        String rank=driver.has("order")?driver.optInt("order")+". ":"";
                        m.results.add(rank+name);
                        if(m.results.size()==10) break;
                    }
                    m.detail="F1 · "+session;
                } else {
                    JSONArray teams=arr(comp,"competitors"); JSONObject home=null,away=null;
                    for(int k=0;k<teams.length();k++) {
                        JSONObject team=teams.optJSONObject(k); if(team==null) continue;
                        if(team.optString("homeAway").equals("home")) home=team;
                        if(team.optString("homeAway").equals("away")) away=team;
                    }
                    if(home==null || away==null) continue;
                    m.home=obj(home,"team").optString("displayName", "主队待定");
                    m.away=obj(away,"team").optString("displayName", "客队待定");
                    m.homeId=obj(home,"team").optString("id");m.awayId=obj(away,"team").optString("id");
                    m.homeLogo=logo(obj(home,"team"));m.awayLogo=logo(obj(away,"team"));
                    if(!m.scheduled()) { m.homeScore=value(home,"score"); m.awayScore=value(away,"score"); }
                    JSONArray hs=arr(home,"linescores"), as=arr(away,"linescores");
                    for(int k=0;k<Math.min(hs.length(), as.length());k++) {
                        JSONObject h=hs.optJSONObject(k),a=as.optJSONObject(k); if(h==null || a==null) continue;
                        String hv=value(h,"displayValue"),av=value(a,"displayValue");
                        if(hv.isEmpty()) hv=value(h,"value"); if(av.isEmpty()) av=value(a,"value");
                        if(!hv.isEmpty() && !av.isEmpty()) m.results.add("第 "+(k+1)+" 节  "+hv+" : "+av);
                    }
                    m.detail=obj(comp,"venue").optString("fullName");
                }
                list.add(m);
            }
        }
        return list;
    }

    public static String logo(JSONObject team) {
        String direct=team.optString("logo");if(direct.startsWith("https://"))return direct;
        JSONArray logos=arr(team,"logos");for(int i=0;i<logos.length();i++){JSONObject l=logos.optJSONObject(i);if(l!=null&&l.optString("href").startsWith("https://"))return l.optString("href");}return "";
    }

    public static List<Match> bo3(JSONObject root, Competition c, long fetched) throws Exception {
        if(!root.has("results")) throw new Exception("电竞来源未返回比赛列表");
        List<Match> list=new ArrayList<>(); JSONArray events=arr(root,"results");
        for(int i=0;i<events.length();i++) {
            JSONObject e=events.optJSONObject(i); if(e==null) continue;
            int discipline=e.optInt("discipline_id"); JSONObject tournament=obj(e,"tournament");
            if(c.id.equals("cs2") && !Rules.isSpirit(e.optInt("team1_id"),e.optInt("team2_id"),discipline)) continue;
            if(c.id.equals("lol") && (discipline!=3 || !Rules.isWorlds(tournament.optString("name"),tournament.optString("slug")))) continue;
            if(e.optString("id").isEmpty()) continue;
            Match m=new Match(); m.id="bo3:"+c.id+":"+e.optString("id"); m.league=c.id;m.group=c.group;m.source="BO3.gg";m.updated=fetched;
            m.home=obj(e,"team1").optString("name", "对阵待定");m.away=obj(e,"team2").optString("name", "对阵待定");
            m.start=Match.parseTime(e.optString("start_date"));m.timeConfirmed=m.start>0;
            m.state=Rules.state(e.optString("status"),"");m.status=Rules.stateLabel(m.state);
            if(!m.scheduled()) {m.homeScore=value(e,"team1_score");m.awayScore=value(e,"team2_score");}
            m.detail=tournament.optString("name")+(e.optInt("bo_type")>0?" · BO"+e.optInt("bo_type"):"");
            String slug=e.optString("slug"); if(slug.matches("[a-zA-Z0-9-]+")) m.url="https://bo3.gg/"+(c.id.equals("lol")?"lol/":"")+"matches/"+slug;
            JSONArray games=arr(e,"games"); List<JSONObject> sorted=new ArrayList<>();
            for(int k=0;k<games.length();k++) if(games.optJSONObject(k)!=null) sorted.add(games.optJSONObject(k));
            sorted.sort(Comparator.comparingInt(o->o.optInt("number")));
            for(JSONObject game:sorted) {
                if(!game.optString("status").equals("finished")) continue;
                String map=game.optString("map_name").replace("de_", "");
                String line="第 "+game.optInt("number")+(c.id.equals("cs2")?" 图":" 局")+(map.isEmpty()?"":" · "+map)+" · 已结束";
                // Never infer map winners or round scores from the series score.
                if(!game.isNull("winner_clan_score") && !game.isNull("loser_clan_score")) {
                    String winner=obj(game,"winner_team_clan").optString("clan_name",game.optString("winner_clan_name", ""));
                    if(!winner.isEmpty()) line+=" · "+winner+" "+value(game,"winner_clan_score")+":"+value(game,"loser_clan_score");
                }
                m.results.add(line);
            }
            list.add(m);
        }
        return list;
    }
}
