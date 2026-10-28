package cn.myscore.app;

import org.json.JSONObject;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class DomesticEsportsTest {
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
}
