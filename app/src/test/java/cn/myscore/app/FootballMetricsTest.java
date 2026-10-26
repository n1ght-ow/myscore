package cn.myscore.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class FootballMetricsTest {
    @Test public void eventSummaryKeepsMultipleGoalsCardsAndSubstitution(){
        MatchDetail.Table table=new MatchDetail.Table("lineup","首发","球员","进球","助攻","黄牌","红牌","状态","换人");
        table.row("Player","2","1","1","0","已换下","76' 换下 · 换上 Other");
        String text=FootballMetrics.events(new DetailRows.Entry(table,table.rows.get(0),null));
        assertTrue(text.contains("进球 2"));assertTrue(text.contains("助攻 1"));assertTrue(text.contains("黄牌 1"));assertFalse(text.contains("红牌"));assertTrue(text.contains("76'"));
    }
    @Test public void olderCachedTablesAreStillReadableWithoutNewColumns(){
        MatchDetail.Table old=new MatchDetail.Table("lineup","首发","球员","状态");old.row("Player","首发");
        DetailRows.Entry entry=new DetailRows.Entry(old,old.rows.get(0),null);
        assertEquals("",FootballMetrics.events(entry));assertTrue(FootballMetrics.missing("—"));assertFalse(FootballMetrics.missing("0"));
    }
}
