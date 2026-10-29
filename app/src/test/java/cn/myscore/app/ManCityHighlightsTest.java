package cn.myscore.app;

import org.json.JSONObject;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.Assert.*;

public class ManCityHighlightsTest {
    private Match match(String league, String opponent, String date, String cityScore, String otherScore) {
        Match m = new Match(); m.league = league; m.home = "Manchester City"; m.homeId = "382";
        m.away = opponent; m.homeScore = cityScore; m.awayScore = otherScore; m.state = "final";
        m.start = Match.parseTime(date); return m;
    }
    private List<BilibiliVods.Episode> recordings() throws Exception {
        try (var in = getClass().getResourceAsStream("/watch/city-highlights.json")) {
            return BilibiliVods.parse(new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8)), BilibiliVods.Source.CITY_HIGHLIGHTS);
        }
    }
    @Test public void matchesRealCupHighlightIncludingPenaltyShootout() throws Exception {
        Match m = match("efl", "Brighton & Hove Albion", "2026-10-29T03:30:00+08:00", "2", "2");
        assertEquals("BV1hgsd6gE6a", BilibiliVods.find(recordings(), m).bvid());
        assertEquals(BilibiliVods.Source.CITY_HIGHLIGHTS, BilibiliVods.source(m));
        m.homeScore = "4"; m.awayScore = "3";
        assertNull(BilibiliVods.find(recordings(), m));
    }
    @Test public void acceptsShortChampionsLeagueHighlightsAndDelayedPublication() throws Exception {
        assertEquals("BV1g9HA6JEAh", BilibiliVods.find(recordings(), match("ucl", "AEK Athens", "2026-10-21T03:00:00+08:00", "5", "1")).bvid());
        assertEquals("BV19hn36VEY1", BilibiliVods.find(recordings(), match("ucl", "Paris Saint-Germain", "2026-10-15T03:00:00+08:00", "2", "4")).bvid());
        Match away = match("ucl", "FC Porto", "2026-09-09T03:00:00+08:00", "2", "0");
        away.home = "FC Porto"; away.homeId = "437"; away.away = "Manchester City"; away.awayId = "382";
        away.homeScore = "0"; away.awayScore = "2";
        assertEquals("BV1KBYb61EnD", BilibiliVods.find(recordings(), away).bvid());
    }
    @Test public void premierLeagueOtherClubsAndUnfinishedGamesAreExcluded() {
        Match m = match("epl", "Brighton", "2026-10-29T03:30:00+08:00", "2", "2");
        assertNull(BilibiliVods.source(m)); assertEquals("咪咕视频", WatchLinks.of(m).provider);
        m.league = "efl";
        for (String state : List.of("scheduled", "live", "delayed", "cancelled", "unknown")) {
            m.state = state; assertNull(BilibiliVods.source(m)); assertNull(WatchLinks.of(m));
        }
        m.state = "final"; m.home = "Manchester United"; m.homeId = "360";
        assertNull(BilibiliVods.source(m)); assertNull(WatchLinks.of(m));
        m.home = "Manchester City"; assertNull(BilibiliVods.source(m));
    }
    @Test public void highlightsHaveAccurateLabelsAndTheirOwnAccountCache() {
        Match m = match("fa", "Chelsea", "2026-05-16T23:00:00+08:00", "1", "0");
        WatchLinks.Link pending = WatchLinks.of(m), highlight = WatchLinks.biliVideo("BV1cSLJ6vERc", m);
        assertEquals("https://space.bilibili.com/1832904916", pending.url);
        assertEquals("集锦待发布", pending.title); assertEquals(WatchLinks.Kind.PENDING, pending.kind);
        assertEquals("观看本场集锦", highlight.title); assertEquals(WatchLinks.Kind.HIGHLIGHTS, highlight.kind);
        assertEquals("集锦", highlight.kind.label); assertFalse(highlight.title.contains("全场"));
        assertNotEquals(BilibiliVods.Source.SPIRIT.cache, BilibiliVods.Source.CITY_HIGHLIGHTS.cache);
        assertNotEquals(BilibiliVods.Source.WORLDS.cache, BilibiliVods.Source.CITY_HIGHLIGHTS.cache);
    }
    @Test public void rejectsWomenYouthPreviewWrongResultOldAndFutureVideos() {
        Match m = match("fa", "Chelsea", "2026-05-16T23:00:00+08:00", "1", "0");
        long published = Match.parseTime("2026-05-17T12:00:00+08:00") / 1000;
        for (String title : List.of("城民饭点 | 曼城女足1-0切尔西女足集锦，足总杯", "城民饭点 | 曼城U18 1-0切尔西集锦，青年足总杯",
            "城民饭点 | 曼城EDS 1-0切尔西集锦", "城民饭点 | 英超曼城1-0切尔西集锦", "城民饭点 | 欧冠曼城1-0切尔西集锦",
            "足总杯前瞻 | 回顾曼城1-0切尔西集锦", "城民饭点 | 曼城2-0切尔西集锦", "城民饭点 | 曼城1-0切尔西个人集锦")) {
            assertNull(title, ManCityHighlights.find(List.of(new BilibiliVods.Episode("BVinvalid", title, 500, published)), m));
        }
        String title = "城民饭点 | 足总杯曼城1-0切尔西集锦";
        assertNull(ManCityHighlights.find(List.of(new BilibiliVods.Episode("BVold", title, 500, published - 366L * 86400)), m));
        assertNull(ManCityHighlights.find(List.of(new BilibiliVods.Episode("BVlate", title, 500, published + 8L * 86400)), m));
        m.awayScore = ""; assertNull(ManCityHighlights.find(List.of(new BilibiliVods.Episode("BVscore", title, 500, published)), m));
    }
    @Test public void handlesResultWrittenWithOpponentFirstAndCupTitlesWithoutCompetitionName() {
        Match m = match("fa", "Arsenal", "2026-05-16T23:00:00+08:00", "0", "3");
        long published = Match.parseTime("2026-05-17T12:00:00+08:00") / 1000;
        assertNotNull(ManCityHighlights.find(List.of(new BilibiliVods.Episode("BVreverse", "【比赛集锦】阿森纳3-0曼城", 120, published)), m));
    }
    @Test public void shortHighlightMetadataSurvivesSourceSpecificParsing() throws Exception {
        JSONObject root = new JSONObject().put("data", new JSONObject().put("archives", new org.json.JSONArray()
            .put(new JSONObject().put("bvid", "BVshort").put("title", "【比赛集锦】曼城1-0切尔西").put("duration", 120))));
        assertEquals(1, BilibiliVods.parse(root, BilibiliVods.Source.CITY_HIGHLIGHTS).size());
        assertTrue(BilibiliVods.parse(root).isEmpty());
    }
}
