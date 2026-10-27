package cn.myscore.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class WatchLinksTest {
    private Match match(String league){Match m=new Match();m.league=league;return m;}

    @Test public void leaguesWithBroadcasterEntriesResolveToStablePublicPages(){
        WatchLinks.Link epl=WatchLinks.of(match("epl"));
        assertEquals("咪咕视频",epl.provider);
        assertEquals("https://www.miguvideo.com/p/home/0c40bbc85fa345bbba20f8e5fd11a922",epl.url);
        WatchLinks.Link nba=WatchLinks.of(match("nba"));
        assertEquals("咪咕视频",nba.provider);
        assertEquals("https://www.miguvideo.com/p/home/3cd6ba04967742879aaa40bee02a99a6",nba.url);
        assertEquals("https://www.douyu.com/6657",WatchLinks.of(match("cs2")).url);
        assertEquals("https://www.huya.com/149361",WatchLinks.of(match("lol")).url);
    }

    @Test public void cupsAndUnconfiguredSportsHaveNoEntry(){
        for(String league:new String[]{"ucl","efl","fa","nfl","f1","unknown",""})assertNull(league,WatchLinks.of(match(league)));
        assertNull(WatchLinks.of(null));
    }

    @Test public void shortLabelsFitDenseCardsAndLongTitlesExplainDestination(){
        assertEquals("咪咕",WatchLinks.of(match("epl")).shortProvider);
        assertEquals("斗鱼",WatchLinks.of(match("cs2")).shortProvider);
        assertEquals("虎牙",WatchLinks.of(match("lol")).shortProvider);
        assertTrue(WatchLinks.of(match("nba")).title.contains("NBA"));
        assertTrue(WatchLinks.of(match("epl")).title.contains("英超"));
    }

    @Test public void directMiguAndBilibiliEntriesAreMatchSpecific(){
        WatchLinks.Link live=WatchLinks.miguMatch("120000588291","nba","骑士","森林狼",false);
        assertEquals("https://www.miguvideo.com/p/live/120000588291",live.url);
        assertTrue(live.title.contains("本场直播"));
        assertTrue(live.note.contains("骑士 vs 森林狼"));
        assertTrue(WatchLinks.miguMatch("120000584185","epl","曼联","阿斯顿维拉",true).title.contains("本场回放"));
        Match m=match("lol");m.start=Match.parseTime("2026-10-27T01:00:00+08:00");
        WatchLinks.Link video=WatchLinks.biliVideo("BV1uUnf6TEch",m);
        assertEquals("https://www.bilibili.com/video/BV1uUnf6TEch",video.url);
        assertEquals("B站",video.shortProvider);
        assertTrue(video.note.contains("10月27日"));
        assertEquals(BilibiliVods.SPACE,WatchLinks.biliPending().url);
    }
}
