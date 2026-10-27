package cn.myscore.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Source facts only. Clocks are never advanced locally. */
public final class Match {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public String id = "", league = "", group = "", source = "", title = "";
    public String home = "", away = "", homeScore = "", awayScore = "";
    public String homeId="", awayId="", homeLogo="", awayLogo="";
    public String state = "unknown", status = "状态待确认", detail = "", url = "";
    public long start = 0, updated = 0;
    public boolean timeConfirmed = false;
    public final List<String> results = new ArrayList<>();

    public boolean live() { return "live".equals(state); }
    public boolean finalised() { return "final".equals(state); }
    public boolean cancelled() { return "cancelled".equals(state); }
    public boolean scheduled() { return "scheduled".equals(state); }
    public boolean hasScore() { return !homeScore.isEmpty() && !awayScore.isEmpty(); }
    public LocalDate day() { return start > 0 ? Instant.ofEpochMilli(start).atZone(ZONE).toLocalDate() : null; }
    public String time() { return Formats.kickoff(start, "时间待定"); }
    public String displayTitle() { return !title.isEmpty() ? title : home + " vs " + away; }
    public String score() { return hasScore() ? homeScore + " : " + awayScore : "—"; }
    public static long parseTime(String value) {
        try { return java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli(); }
        catch (RuntimeException e) { return 0; }
    }
    public static LocalDate today(long now) { return Instant.ofEpochMilli(now).atZone(ZONE).toLocalDate(); }
    public JSONObject json() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id).put("league", league).put("group", group).put("source", source).put("title", title)
                .put("home", home).put("away", away).put("homeScore", homeScore).put("awayScore", awayScore)
                .put("homeId",homeId).put("awayId",awayId).put("homeLogo",homeLogo).put("awayLogo",awayLogo)
                .put("state", state).put("status", status).put("detail", detail).put("url", url)
                .put("start", start).put("updated", updated).put("timeConfirmed", timeConfirmed)
                .put("results", new JSONArray(results));
        } catch (org.json.JSONException e) { throw new IllegalStateException(e); }
        return o;
    }
    public static Match fromJson(JSONObject o) {
        Match m = new Match();
        m.id=o.optString("id"); m.league=o.optString("league"); m.group=o.optString("group");
        m.source=o.optString("source"); m.title=o.optString("title"); m.home=o.optString("home"); m.away=o.optString("away");
        m.homeScore=o.optString("homeScore"); m.awayScore=o.optString("awayScore"); m.state=o.optString("state", "unknown");
        m.homeId=o.optString("homeId");m.awayId=o.optString("awayId");m.homeLogo=o.optString("homeLogo");m.awayLogo=o.optString("awayLogo");
        m.status=o.optString("status", "状态待确认"); m.detail=o.optString("detail"); m.url=o.optString("url");
        m.start=o.optLong("start"); m.updated=o.optLong("updated"); m.timeConfirmed=o.optBoolean("timeConfirmed");
        JSONArray a=o.optJSONArray("results"); if(a!=null) for(int i=0;i<a.length();i++) m.results.add(a.optString(i));
        return m;
    }
}
