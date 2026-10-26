package cn.myscore.app;

import org.json.*;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class FootballMetricsTest {
    private JSONObject fixture()throws Exception{try(var in=getClass().getResourceAsStream("/details/epl.json")){return new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}}
    private MatchDetail parse(JSONObject root)throws Exception{Match match=Parsers.espn(new JSONObject().put("events",new JSONArray().put(root.getJSONObject("header"))),Competition.find("epl"),1).get(0);return DetailParsers.espn(root,match,100);}
    private DetailRows.Entry first(MatchDetail detail){MatchDetail.Table table=detail.tables.stream().filter(t->t.category.equals("首发")).findFirst().orElseThrow();return new DetailRows.Entry(table,table.rows.get(0),table.people.get(0));}
    @Test public void missingRatingAndProfilesNeverBecomeZeroOrAnEstimatedScore()throws Exception{
        DetailRows.Entry entry=first(parse(fixture()));for(String metric:List.of("评分","国籍","年龄","跑动","身价")){assertEquals("—",FootballMetrics.value(entry,metric));assertFalse(FootballMetrics.available(entry.table(),metric));}
        assertEquals("评分",FootballMetrics.valid("unknown"));
    }
    @Test public void sourceStatsPreserveRealZeroAndPartialPairsThroughCache()throws Exception{
        JSONObject root=fixture(),player=root.getJSONArray("rosters").getJSONObject(0).getJSONArray("roster").getJSONObject(0);
        player.put("stats",new JSONArray().put(new JSONObject().put("name","totalShots").put("displayValue","0")).put(new JSONObject().put("name","foulsCommitted").put("value",2)).put(new JSONObject().put("name","rating").put("displayValue","7.4")));
        DetailRows.Entry entry=first(MatchDetail.fromJson(parse(root).json()));
        assertEquals("0 / —",FootballMetrics.value(entry,"射门"));assertTrue(FootballMetrics.available(entry.table(),"射门"));
        assertEquals("— / 2",FootballMetrics.value(entry,"防守"));assertEquals("7.4",FootballMetrics.value(entry,"评分"));assertEquals("—",FootballMetrics.value(entry,"传球"));
    }
    @Test public void playerProfileOnlyUsesPublishedIdentityFields()throws Exception{
        JSONObject root=fixture(),athlete=root.getJSONArray("rosters").getJSONObject(0).getJSONArray("roster").getJSONObject(0).getJSONObject("athlete");
        athlete.put("citizenship","Japan").put("age",24);
        DetailRows.Entry entry=first(parse(root));assertEquals("Japan",FootballMetrics.value(entry,"国籍"));assertEquals("24",FootballMetrics.value(entry,"年龄"));
    }
    @Test public void eventSummaryKeepsMultipleGoalsCardsAndSubstitution()throws Exception{
        MatchDetail.Table table=new MatchDetail.Table("lineup","首发","球员","进球","助攻","黄牌","红牌","状态","换人");
        table.row("Player","2","1","1","0","已换下","76' 换下 · 换上 Other");
        String text=FootballMetrics.events(new DetailRows.Entry(table,table.rows.get(0),null));
        assertTrue(text.contains("进球 2"));assertTrue(text.contains("助攻 1"));assertTrue(text.contains("黄牌 1"));assertFalse(text.contains("红牌"));assertTrue(text.contains("76'"));
    }
    @Test public void olderCachedTablesAreStillReadableWithoutNewColumns(){
        MatchDetail.Table old=new MatchDetail.Table("lineup","首发","球员","状态");old.row("Player","首发");
        DetailRows.Entry entry=new DetailRows.Entry(old,old.rows.get(0),null);
        for(String metric:FootballMetrics.PERFORMANCE)assertEquals("—",FootballMetrics.value(entry,metric));assertEquals("",FootballMetrics.events(entry));
    }
}
