package cn.myscore.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.time.LocalDate;
import java.util.List;
import static org.junit.Assert.*;

public class DomesticEsportsTest {
    private static final LocalDate DAY=LocalDate.of(2026,10,29);
    private static long at(LocalDate day,int hour){return day.atTime(hour,0).atZone(Match.ZONE).toInstant().toEpochMilli();}
    private static JSONObject team(String id,String name,String logo)throws Exception{
        return new JSONObject().put("id",id).put("disp_name",name).put("logo",logo);
    }
    private static JSONObject row(String id,long start,String grade,String label)throws Exception{
        JSONObject mc=new JSONObject().put("id",id).put("plan_ts",String.valueOf(start/1000))
            .put("format","3").put("tt_stage_desc","淘汰赛 半决赛")
            .put("t1_info",team("csgo_tm_1","NAVI","https://oss.5eplay.com/sport/csgo/team/a.png"))
            .put("t2_info",team("csgo_tm_2","FURIA","https://oss.5eplay.com/sport/csgo/team/b.png"));
        JSONObject tt=new JSONObject().put("disp_name","PGL 布加勒斯特大师赛 2026").put("grade",grade).put("grade_label",label);
        JSONObject state=new JSONObject().put("status","2").put("t1_score","2").put("t2_score","0")
            .put("bout_states",new JSONArray().put(new JSONObject().put("status","2").put("bout_num","1")
                .put("map_name","Mirage").put("t1_score","13").put("t2_score","7").put("result","t1")));
        return new JSONObject().put("mc_info",mc).put("tt_info",tt).put("state",state);
    }
    private static JSONObject page(JSONArray rows)throws Exception{
        return new JSONObject().put("success",true).put("data",new JSONObject().put("matches",rows));
    }
    private static JSONArray rows(int count,LocalDate day,int hourOffset)throws Exception{
        JSONArray list=new JSONArray();
        for(int i=0;i<count;i++)list.put(row("csgo_mc_"+(1000+i),at(day.plusDays(i),hourOffset),"2","S级赛事"));
        return list;
    }
    private static int pageOf(String url){return Integer.parseInt(url.substring(url.lastIndexOf("page=")+5));}

    @Test public void worldsUsesBeijingTimeAndRejectsFirstStand() throws Exception {
        JSONObject root=new JSONObject("{\"status\":\"0\",\"lastUpTime\":\"2026-10-24 10:35:06\",\"msg\":[{\"GameName\":\"2026全球总决赛\",\"bMatchId\":\"1\",\"bMatchName\":\"T1 vs GEN\",\"MatchDate\":\"2026-10-25 01:00:00\",\"MatchStatus\":\"1\",\"ScoreA\":\"0\",\"ScoreB\":\"0\"},{\"GameName\":\"2026全球先锋赛\",\"bMatchId\":\"2\"}]}");
        List<Match> ms=DomesticEsports.worlds(root,Match.parseTime("2026-10-24T19:00:00+08:00"));
        assertEquals(1,ms.size());assertEquals("2026-10-25",ms.get(0).day().toString());assertEquals("01:00",ms.get(0).time());assertEquals("T1",ms.get(0).home);assertFalse(ms.get(0).hasScore());assertEquals(Match.parseTime("2026-10-24T10:35:06+08:00"),ms.get(0).updated);
    }
    @Test public void completedWorldsPreservesActualSeriesScore() throws Exception {
        JSONObject root=new JSONObject("{\"status\":\"0\",\"msg\":[{\"GameName\":\"2026全球总决赛\",\"bMatchId\":\"1\",\"bMatchName\":\"T1 vs GEN\",\"MatchStatus\":\"3\",\"ScoreA\":\"3\",\"ScoreB\":\"1\"}]}");
        Match m=DomesticEsports.worlds(root,1).get(0);assertTrue(m.finalised());assertEquals("3 : 1",m.score());assertFalse(m.timeConfirmed);
    }
    @Test public void spiritHistoryUsesHomeOpponentScoresNotT1T2() throws Exception {
        JSONObject root=new JSONObject("{\"success\":true,\"data\":{\"items\":{\"matches\":[{\"mc_info\":{\"id\":\"csgo_mc_1\",\"status\":\"past\",\"home_info\":{\"id\":\"csgo_tm_7020\",\"disp_name\":\"Spirit\"},\"opponent_info\":{\"id\":\"other\",\"disp_name\":\"Aurora\"},\"home_score\":\"0\",\"opponent_score\":\"2\",\"t1_score\":\"2\",\"t2_score\":\"0\"}}]}}}");
        Match m=DomesticEsports.history(root,1).get(0);assertEquals("Spirit",m.home);assertEquals("0 : 2",m.score());
    }
    @Test public void discoveryShowsOnlyCompletedMapsAndFiltersExactSpiritId() throws Exception {
        JSONObject row=new JSONObject("{\"mc_info\":{\"id\":\"csgo_mc_1\",\"ts\":\"1791652200\",\"t1_info\":{\"id\":\"csgo_tm_7020\",\"disp_name\":\"Spirit\",\"logo\":\"//oss.5eplay.com/sport/csgo/team/a.png\"},\"t2_info\":{\"id\":\"other\",\"disp_name\":\"BB\"}},\"state\":{\"status\":\"1\",\"t1_score\":\"1\",\"t2_score\":\"0\",\"bout_states\":[{\"status\":\"2\",\"bout_num\":\"1\",\"map_name\":\"Anubis\",\"t1_score\":\"13\",\"t2_score\":\"10\",\"result\":\"t1\"},{\"status\":\"1\",\"bout_num\":\"2\",\"map_name\":\"Ancient\",\"t1_score\":\"2\",\"t2_score\":\"7\"}]}}");
        Match m=DomesticEsports.fiveMatch(row,1,false);assertEquals("1 : 0",m.score());assertEquals(1,m.results.size());assertTrue(m.results.get(0).contains("13 : 10"));
        assertTrue(m.timeConfirmed);assertEquals(1_791_652_200_000L,m.start);assertEquals("https://oss.5eplay.com/sport/csgo/team/a.png",m.homeLogo);
        assertFalse(DomesticEsports.spirit(new JSONObject("{\"id\":\"csgo_tm_7020_academy\"}"),new JSONObject()));
    }

    @Test public void onlyMajorSPlusAndSAreTierOne() throws Exception {
        for(String grade:new String[]{"1","2","7"})assertTrue(grade,DomesticEsports.isTierOne(grade,""));
        for(String grade:new String[]{"3","4","5","6","8","9","10",""})assertFalse(grade,DomesticEsports.isTierOne(grade,""));
        assertTrue(DomesticEsports.isTierOne("","Major"));
        assertTrue(DomesticEsports.isTierOne("","S+赛事"));
        assertTrue(DomesticEsports.isTierOne("","S级赛事"));
        assertFalse(DomesticEsports.isTierOne("","A级赛事"));
        assertFalse(DomesticEsports.isTierOne("","资格赛A级"));
        assertFalse(DomesticEsports.isTierOne("","表演赛"));
        assertFalse(DomesticEsports.tierOne(new JSONObject("{\"tt_info\":{\"grade\":\"3\",\"grade_label\":\"A级赛事\"}}")));
        assertTrue(DomesticEsports.tierOne(new JSONObject("{\"tt_info\":{\"grade\":\"7\",\"grade_label\":\"S+赛事\"}}")));
    }

    @Test public void tierOneMatchCarriesEventAndSeriesResult() throws Exception {
        Match m=DomesticEsports.tierOneMatch(row("csgo_mc_1",at(DAY,20),"7","S+赛事"),100);
        assertNotNull(m);assertEquals("PGL 布加勒斯特大师赛 2026",m.event);assertTrue(m.tierOne);
        assertEquals("2 : 0",m.score());assertEquals(1,m.results.size());assertTrue(m.results.get(0).contains("13 : 7"));
        assertEquals("PGL 布加勒斯特大师赛 2026 · BO3 · 淘汰赛 半决赛",m.detail);
        assertEquals("PGL 布加勒斯特大师赛 2026",MatchLabel.of(m));
    }

    @Test public void tierOneMatchNormalizesTbdAndNullCrest() throws Exception {
        JSONObject r=row("csgo_mc_2",at(DAY,18),"2","S级赛事");
        r.getJSONObject("mc_info").put("t1_info",team("","TBD","https://oss.5eplay.com/sport/csgo/team/null.png"));
        Match m=DomesticEsports.tierOneMatch(r,100);
        assertNotNull(m);assertEquals("对阵待定",m.home);assertEquals("",m.homeId);assertEquals("",m.homeLogo);
        assertEquals("FURIA",m.away);assertTrue(m.tierOne);
    }

    @Test public void tierOnePageStopsOnceItReachesTheRequestedDay() throws Exception {
        JSONArray list=new JSONArray();
        for(int i=0;i<98;i++)list.put(row("csgo_mc_"+(2000+i),at(DAY.plusDays(1),i%20),"2","S级赛事"));
        list.put(row("csgo_mc_3000",at(DAY,20),"2","S级赛事"));
        list.put(row("csgo_mc_3001",at(DAY.minusDays(1),20),"2","S级赛事"));
        int[] calls={0};
        DomesticEsports.Source source=url->{calls[0]++;return page(list).toString();};
        List<Match> matches=DomesticEsports.tierOneMatches(DAY,DAY,100,source);
        assertEquals(1,calls[0]);assertEquals(1,matches.size());assertEquals("five:cs2:csgo_mc_3000",matches.get(0).id);
    }

    @Test public void tierOnePageFetchesTheNextPageForAnOlderDay() throws Exception {
        int[] calls={0};
        DomesticEsports.Source source=url->{
            calls[0]++;
            int page=pageOf(url);
            if(page==1)return page(rows(100,DAY.plusDays(1),20)).toString();
            if(page==2)return page(new JSONArray().put(row("csgo_mc_4000",at(DAY,20),"2","S级赛事"))).toString();
            return page(new JSONArray()).toString();
        };
        List<Match> matches=DomesticEsports.tierOneMatches(DAY,DAY,100,source);
        assertEquals(2,calls[0]);assertEquals(1,matches.size());assertEquals("five:cs2:csgo_mc_4000",matches.get(0).id);
    }

    @Test public void tierOnePageStopsAtThePageLimitInsteadOfReturningAnEmptyDay() throws Exception {
        int[] calls={0};
        DomesticEsports.Source source=url->{calls[0]++;return page(rows(100,DAY.plusDays(1),20)).toString();};
        try{DomesticEsports.tierOneMatches(DAY,DAY,100,source);fail("an old day beyond the page limit must not look empty");}
        catch(Exception expected){
            assertTrue(expected.getMessage().contains("一线赛事"));
            assertEquals(DomesticEsports.MAX_TIER_PAGES,calls[0]);
        }
    }

    @Test public void tierOnePageKeepsOnlyTierOneRows() throws Exception {
        JSONArray list=new JSONArray();
        list.put(row("csgo_mc_5000",at(DAY,20),"3","A级赛事"));
        list.put(row("csgo_mc_5001",at(DAY,19),"2","S级赛事"));
        DomesticEsports.Source source=url->page(list).toString();
        List<Match> matches=DomesticEsports.tierOneMatches(DAY,DAY,100,source);
        assertEquals(1,matches.size());assertEquals("five:cs2:csgo_mc_5001",matches.get(0).id);
    }

    @Test public void tierOnePageDeduplicatesOverlappingPages() throws Exception {
        int[] calls={0};
        DomesticEsports.Source source=url->{
            calls[0]++;
            int page=pageOf(url);
            JSONArray list=new JSONArray();
            if(page==1)for(int i=0;i<99;i++)list.put(row("csgo_mc_"+(6000+i),at(DAY.plusDays(1),i%20),"2","S级赛事"));
            list.put(row("csgo_mc_7000",at(DAY,20),"2","S级赛事"));
            return page(list).toString();
        };
        List<Match> matches=DomesticEsports.tierOneMatches(DAY,DAY,100,source);
        assertEquals(1,matches.size());assertEquals("five:cs2:csgo_mc_7000",matches.get(0).id);assertEquals(2,calls[0]);
    }

    @Test public void mergeKeepsSpiritVersionOfADuplicateAndAddsOtherTierOneMatches() {
        Match spirit=new Match();spirit.id="five:cs2:csgo_mc_1";spirit.homeId="csgo_tm_7020";spirit.home="Spirit";
        Match other=new Match();other.id="five:cs2:csgo_mc_2";other.home="NAVI";
        Match duplicate=new Match();duplicate.id="five:cs2:csgo_mc_1";duplicate.home="NAVI";
        List<Match> merged=DomesticEsports.merge(List.of(spirit,other),List.of(duplicate));
        assertEquals(2,merged.size());assertSame(spirit,merged.get(0));assertEquals("csgo_mc_2",merged.get(1).id.substring("five:cs2:".length()));
    }

    @Test public void matchCacheRoundTripsEventAndTier() throws Exception {
        Match m=new Match();m.id="five:cs2:csgo_mc_1";m.league="cs2";m.event="EPL S24";m.tierOne=true;
        Match copy=Match.fromJson(m.json());
        assertEquals("EPL S24",copy.event);assertTrue(copy.tierOne);
        Match legacy=Match.fromJson(new JSONObject().put("id","five:cs2:csgo_mc_2").put("league","cs2"));
        assertEquals("",legacy.event);assertFalse(legacy.tierOne);
    }
}
