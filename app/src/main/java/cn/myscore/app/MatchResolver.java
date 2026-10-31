package cn.myscore.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Resolve imported identities independently of the visible date window. */
public final class MatchResolver implements CalendarSync.Resolver {
    interface Source {String get(String url)throws Exception;}
    private final Source source;
    private final Map<String,List<Match>> shared=new HashMap<>();
    public MatchResolver(){this(Repository::get);}
    MatchResolver(Source source){this.source=source;}
    @Override public void beginSync(){shared.clear();}
    @Override public Match resolve(Match previous) throws Exception {
        Competition c=Competition.find(previous.league);
        if(c==null)throw new Exception("无法识别已导入赛事");
        List<Match> candidates;long now=System.currentTimeMillis();
        if(previous.id.startsWith("espn:")) {
            String[] parts=previous.id.split(":");
            if(parts.length!=4 || !parts[2].matches("[0-9]+") || !parts[3].matches("[0-9]+"))throw new Exception("已导入赛事标识无效");
            if(c.id.equals("f1")) {
                int year=previous.day()==null?Match.today(now).getYear():previous.day().getYear();String key="f1:"+year;
                if(!shared.containsKey(key))shared.put(key,Parsers.espn(new JSONObject(source.get("https://site.api.espn.com/apis/site/v2/sports/racing/f1/scoreboard?dates="+year+"&limit=1000")),c,now));
                candidates=shared.get(key);
            } else {
                JSONObject root=new JSONObject(source.get("https://site.api.espn.com/apis/site/v2/sports/"+c.path+"/summary?event="+parts[2]));
                JSONObject header=root.optJSONObject("header");if(header==null)throw new Exception("已导入比赛详情未返回身份");
                candidates=Parsers.espn(new JSONObject().put("events",new JSONArray().put(header)),c,now);
            }
        } else if(previous.id.startsWith("qq:lol:")) {
            if(!shared.containsKey("worlds"))shared.put("worlds",DomesticEsports.worlds(new JSONObject(source.get(DomesticEsports.LOL)),now));
            candidates=shared.get("worlds");
        } else if(previous.id.startsWith("five:cs2:")) {
            String id=previous.id.substring("five:cs2:".length());if(!id.matches("csgo_mc_[0-9]+"))throw new Exception("CS2 比赛标识无效");
            JSONObject root=new JSONObject(source.get(DomesticEsports.FIVE+"matches/"+id+"/data"));
            JSONObject data=root.optJSONObject("data"),match=data==null?null:data.optJSONObject("match");
            if(!root.optBoolean("success")||match==null)throw new Exception("CS2 已导入比赛详情暂不可用");
            candidates=java.util.Collections.singletonList(DomesticEsports.fiveMatch(match,now,true));
        } else throw new Exception("来源暂不支持身份复核");
        for(Match m:candidates)if(m.id.equals(previous.id)){if(m.url.isEmpty())m.url=previous.url;return m;}
        // Missing never means cancelled.
        throw new Exception("来源尚未确认已导入比赛的新状态");
    }
}
