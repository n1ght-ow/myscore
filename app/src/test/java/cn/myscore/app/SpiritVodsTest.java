package cn.myscore.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.Assert.*;

public class SpiritVodsTest {
    private Match match(String away, String date) {
        Match m = new Match();
        m.league = "cs2"; m.home = "Spirit"; m.homeId = "csgo_tm_7020"; m.away = away;
        m.start = Match.parseTime(date + "T00:00:00+08:00"); m.state = "final";
        return m;
    }

    private List<BilibiliVods.Episode> recordings() throws Exception {
        try (var stream = getClass().getResourceAsStream("/watch/spirit.json")) {
            return BilibiliVods.parse(new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }

    @Test public void matchesPublishedSpiritRecordingsByExactDateAndBothTeams() throws Exception {
        assertEquals("BV1TisR6eEY7", BilibiliVods.find(recordings(), match("NAVI", "2026-10-29")).bvid());
        assertEquals("BV1EAx36cETN", BilibiliVods.find(recordings(), match("MOUZ", "2026-10-27")).bvid());
        assertEquals("BV13Gpn6KEFa", BilibiliVods.find(recordings(), match("Aurora", "2026-10-11")).bvid());
        assertNull(BilibiliVods.find(recordings(), match("NAVI", "2026-10-28")));
        assertNull(BilibiliVods.find(recordings(), match("MOUZ", "2026-10-29")));
        assertNull(BilibiliVods.find(recordings(), match("NAVI", "2025-10-29")));
    }

    @Test public void handlesAliasesReversedTeamsAndStageBeforeDate() throws Exception {
        Match reversed = match("Team Spirit", "2026-10-29");
        reversed.home = "Natus Vincere"; reversed.homeId = ""; reversed.awayId = "csgo_tm_7020";
        assertEquals("BV1TisR6eEY7", BilibiliVods.find(recordings(), reversed).bvid());
        assertEquals("BV1zKH36WE6V", BilibiliVods.find(recordings(), match("BetBoom", "2026-10-24")).bvid());
        assertEquals("BV1znH76ME39", BilibiliVods.find(recordings(), match("PARIVISION", "2026-10-04")).bvid());
        assertEquals("BV1pH886vEmd", BilibiliVods.find(recordings(), match("FUT", "2026-08-23")).bvid());
    }

    @Test public void excludesAcademyClipsPartialMapsAndInvalidDates() throws Exception {
        Match m = match("NAVI", "2026-10-29");
        long published = Match.parseTime("2026-10-29T08:00:00+08:00") / 1000;
        for (String title : List.of("【PGL】Spirit Academy vs NAVI 10月29日", "【PGL】Spirit vs NAVI Junior 10月29日",
            "【PGL】Spirit vs NAVI 10月29日 精彩集锦", "【PGL】Spirit vs NAVI 10月29日 MAP 1",
            "【PGL】Spirit vs NAVI 10月29日 第一局", "【2025PGL】Spirit vs NAVI 10月29日",
            "【PGL】Spirit vs NAVI 2月30日", "【PGL】Spirit vs NAVI 10月28日")) {
            assertNull(title, BilibiliVods.find(List.of(new BilibiliVods.Episode("BVclip", title, 3600, published)), m));
        }
        assertNull(BilibiliVods.find(List.of(new BilibiliVods.Episode("BVshort", "【PGL】Spirit vs NAVI 10月29日", 899, published)), m));
        assertNull(BilibiliVods.find(List.of(new BilibiliVods.Episode("BVold", "【PGL】Spirit vs NAVI 10月29日", 3600, published - 365L * 86400)), m));
    }

    @Test public void derivesMissingYearAtNewYearAndAcceptsDelayedUploads() {
        long january = Match.parseTime("2027-01-01T03:00:00+08:00") / 1000;
        var newYear = new BilibiliVods.Episode("BVnewyear", "【CS2】Spirit vs NAVI 12月31日", 7200, january);
        assertEquals("BVnewyear", BilibiliVods.find(List.of(newYear), match("NAVI", "2026-12-31")).bvid());
        assertNull(BilibiliVods.find(List.of(newYear), match("NAVI", "2027-12-31")));
        long november = Match.parseTime("2026-11-29T03:00:00+08:00") / 1000;
        var delayed = new BilibiliVods.Episode("BVdelayed", "【2026PGL】Spirit vs NAVI 10月29日", 7200, november);
        assertEquals("BVdelayed", BilibiliVods.find(List.of(delayed), match("NAVI", "2026-10-29")).bvid());
    }

    @Test public void onlyFinishedFirstTeamMatchesUseTheSpiritSource() {
        Match m = match("NAVI", "2026-10-29");
        assertEquals(BilibiliVods.Source.SPIRIT, BilibiliVods.source(m));
        for (String state : List.of("scheduled", "live", "postponed", "cancelled", "unknown")) {
            m.state = state; assertNull(BilibiliVods.source(m));
            assertEquals(WatchLinks.Kind.ROOM, WatchLinks.of(m).kind);
        }
        m.state = "final"; m.homeId = "academy-id"; m.home = "Spirit Academy";
        assertNull(BilibiliVods.source(m));
        m.home = "Spirit"; assertNull(BilibiliVods.source(m));
        m.homeId = ""; m.home = "Spirit Academy"; assertNull(BilibiliVods.source(m));
        m.home = "Team Spirit"; assertEquals(BilibiliVods.Source.SPIRIT, BilibiliVods.source(m));
    }

    @Test public void pendingAndReplayUseCs2ContextAndSeparateAccountAndCache() {
        Match m = match("NAVI", "2026-10-29");
        WatchLinks.Link pending = WatchLinks.biliPending(m), replay = WatchLinks.biliVideo("BV1TisR6eEY7", m);
        assertEquals("https://space.bilibili.com/474595627", pending.url);
        assertEquals(WatchLinks.Kind.PENDING, pending.kind);
        assertTrue(pending.note.contains("CS2"));
        assertEquals("https://www.bilibili.com/video/BV1TisR6eEY7", replay.url);
        assertEquals(WatchLinks.Kind.REPLAY, replay.kind);
        assertTrue(replay.note.contains("Spirit vs NAVI"));
        assertTrue(replay.note.contains("10月29日"));
        assertFalse(replay.note.contains("全球总决赛"));
        assertNotEquals(BilibiliVods.Source.WORLDS.cache, BilibiliVods.Source.SPIRIT.cache);
        assertTrue(BilibiliVods.Source.SPIRIT.api(2).endsWith("&pn=2"));
        assertEquals(BilibiliVods.API, BilibiliVods.Source.WORLDS.api(1));
    }

    @Test public void parserDropsShortVideosFromAccountIndex() throws Exception {
        JSONObject root = new JSONObject().put("data", new JSONObject().put("archives", new JSONArray()
            .put(new JSONObject().put("bvid", "BVshort").put("title", "【PGL】Spirit vs NAVI 10月29日").put("duration", 120))));
        assertTrue(BilibiliVods.parse(root).isEmpty());
    }
}
