package cn.myscore.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public final class EnglishDisplayTest {
    @Test public void mapsProviderLabelsAndLegacyCacheValues() {
        assertEquals("Football", EnglishDisplay.sport("足球"));
        assertEquals("Manchester City", EnglishDisplay.text("曼城"));
        assertEquals("Q3 · 07:12", EnglishDisplay.text("第 3 节 · 07:12"));
        assertEquals("Lap 53", EnglishDisplay.text("第 53 圈"));
        assertEquals("Nov 23 18:00", EnglishDisplay.text("11月23日 18:00"));
        assertEquals("World Cup qualifying · Europe", EnglishDisplay.text("世预赛 · 欧洲区"));
        assertEquals("PGL Singapore Major 2026 Stage 1", EnglishDisplay.tournament("PGL 新加坡 Major 2026 第1阶段"));
        assertEquals("Location", EnglishDisplay.text("地点"));
        assertEquals("Neutral site", EnglishDisplay.text("中立场地"));
        assertEquals("Surface", EnglishDisplay.text("场地类型"));
        assertEquals("Grass", EnglishDisplay.text("草地"));
        assertEquals("Yes", EnglishDisplay.text("是"));
    }

    @Test public void watchCopyDoesNotExposeProviderSearchVocabulary() {
        Match match = new Match();
        match.league = "epl";
        WatchLinks.Link link = WatchLinks.of(match);
        assertEquals("Watch Premier League", EnglishDisplay.watchTitle(link));
        assertEquals("Migu Video", EnglishDisplay.text(link.provider));
        assertEquals("咪咕视频", link.provider);
    }

    @Test public void untouchedNamesAndStyledTextRetainTheirIdentity() {
        StringBuilder text = new StringBuilder("Montréal — 2–1");
        assertSame(text, EnglishDisplay.text(text));
        assertEquals("The MongolZ", EnglishDisplay.text("The MongolZ"));
    }
}
