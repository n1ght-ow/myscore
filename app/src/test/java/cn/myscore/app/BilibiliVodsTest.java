package cn.myscore.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class BilibiliVodsTest {
    private static Match match(String home,String away,String start){
        Match m=new Match();m.id="lol-test";m.league="lol";m.group="LOL";m.home=home;m.away=away;
        m.start=Match.parseTime(start);m.state="final";return m;
    }
    private static JSONObject series() throws Exception {
        JSONArray a=new JSONArray();
        a.put(new JSONObject().put("bvid","BV1uUnf6TEch").put("title","「2026全球总决赛」10月27日 CFO vs T1").put("duration",3828).put("pubdate",1793037157L));
        a.put(new JSONObject().put("bvid","BV1rnnf6xEhx").put("title","「2026全球总决赛」10月27日 BLG vs KC").put("duration",3438).put("pubdate",1793040731L));
        a.put(new JSONObject().put("bvid","BVshort").put("title","「2026全球总决赛」10月27日 CFO vs T1").put("duration",65).put("pubdate",1793040731L));
        a.put(new JSONObject().put("bvid","BVwrongday").put("title","「2026全球总决赛」10月26日 CFO vs T1").put("duration",3600).put("pubdate",1793040731L));
        return new JSONObject().put("code",0).put("data",new JSONObject().put("archives",a));
    }
    @Test public void findsOfficialFullSeriesRecordingByDateAndTeams() throws Exception {
        Match m=match("CFO","T1","2026-10-27T01:00:00+08:00");
        BilibiliVods.Episode e=BilibiliVods.find(BilibiliVods.parse(series()),m);
        assertEquals("BV1uUnf6TEch",e.bvid());
        assertTrue(BilibiliVods.note(m).contains("10月27日"));
    }
    @Test public void acceptsReversedHomeAndAwayButRejectsShortClipsAndWrongDay() throws Exception {
        Match reversed=match("T1","CFO","2026-10-27T01:00:00+08:00");
        assertEquals("BV1uUnf6TEch",BilibiliVods.find(BilibiliVods.parse(series()),reversed).bvid());
        assertEquals("BV1rnnf6xEhx",BilibiliVods.find(BilibiliVods.parse(series()),match("BLG","KC","2026-10-27T02:00:00+08:00")).bvid());
        assertNull(BilibiliVods.find(BilibiliVods.parse(series()),match("CFO","T1","2026-10-28T01:00:00+08:00")));
    }
    @Test public void noMatchForUnknownTeams() throws Exception {
        assertNull(BilibiliVods.find(BilibiliVods.parse(series()),match("TES","GEN","2026-10-27T01:00:00+08:00")));
    }
}
