package cn.myscore.app;

import org.json.JSONObject;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class ParsersTest {
    private final String teams="[{\"homeAway\":\"away\",\"team\":{\"displayName\":\"Away\"},\"score\":\"0\"},{\"homeAway\":\"home\",\"team\":{\"displayName\":\"Home\"},\"score\":\"1\"}]";
    private JSONObject espn(String status,String extras) throws Exception {
        return new JSONObject("{\"events\":[{\"id\":\"42\",\"date\":\"2026-10-24T11:30Z\",\"competitions\":[{\"id\":\"42\",\"competitors\":"+teams+",\"status\":"+status+extras+"}]}]}");
    }
    @Test public void homeAwayOrderDoesNotDependOnArrayPosition() throws Exception {
        Match m=Parsers.espn(espn("{\"type\":{\"state\":\"in\",\"name\":\"STATUS_FIRST_HALF\"},\"displayClock\":\"11'\"}",""),Competition.find("epl"),123).get(0);
        assertEquals("Home",m.home);assertEquals("Away",m.away);assertEquals("1 : 0",m.score());assertEquals("11'",m.status);assertEquals(123,m.updated);
    }
    @Test public void upcomingGamesHaveNoInventedZeroScore() throws Exception {
        Match m=Parsers.espn(espn("{\"type\":{\"state\":\"pre\",\"name\":\"STATUS_SCHEDULED\"}}",",\"timeValid\":false"),Competition.find("epl"),1).get(0);
        assertFalse(m.hasScore());assertFalse(m.timeConfirmed);
    }
    @Test public void missingScoresStayUnknownDuringLiveGame() throws Exception {
        JSONObject root=espn("{\"type\":{\"state\":\"in\"}}","");
        root.getJSONArray("events").getJSONObject(0).getJSONArray("competitions").getJSONObject(0).getJSONArray("competitors").getJSONObject(0).remove("score");
        assertFalse(Parsers.espn(root,Competition.find("nba"),1).get(0).hasScore());
    }
    @Test public void f1SessionsUseIndividualStartTimesAndStatus() throws Exception {
        JSONObject root=new JSONObject("{\"events\":[{\"id\":\"gp\",\"name\":\"United States GP\",\"competitions\":[{\"id\":\"p\",\"date\":\"2026-10-24T17:30Z\",\"type\":{\"abbreviation\":\"FP3\"},\"status\":{\"type\":{\"state\":\"pre\"}}},{\"id\":\"r\",\"date\":\"2026-10-25T20:00Z\",\"type\":{\"abbreviation\":\"Race\"},\"status\":{\"period\":12,\"type\":{\"state\":\"in\"}},\"competitors\":[{\"order\":2,\"athlete\":{\"displayName\":\"Second\"}},{\"order\":1,\"athlete\":{\"displayName\":\"First\"}}]}]}]}");
        List<Match> list=Parsers.espn(root,Competition.find("f1"),1);
        assertEquals(2,list.size());assertEquals("2026-10-25",list.get(0).day().toString());assertEquals("第 12 圈",list.get(1).status);assertEquals("1. First",list.get(1).results.get(0));
    }
    @Test public void esportsRetainsSeriesScoreWithoutInventingMapWinner() throws Exception {
        JSONObject root=new JSONObject("{\"results\":[{\"id\":7,\"discipline_id\":1,\"team1_id\":654,\"team2_id\":2,\"status\":\"current\",\"team1_score\":1,\"team2_score\":0,\"team1\":{\"name\":\"Spirit\"},\"team2\":{\"name\":\"Other\"},\"games\":[{\"number\":1,\"status\":\"finished\",\"map_name\":\"de_anubis\",\"winner_clan_score\":null,\"loser_clan_score\":null}]}]}");
        Match m=Parsers.bo3(root,Competition.find("cs2"),1).get(0);assertEquals("1 : 0",m.score());assertEquals("第 1 图 · anubis · 已结束",m.results.get(0));
    }
    @Test public void lolFiltersWorldsEvenWhenSourceReturnsOtherLeagues() throws Exception {
        JSONObject root=new JSONObject("{\"results\":[{\"id\":1,\"discipline_id\":3,\"tournament\":{\"name\":\"Worlds 2026\"}},{\"id\":2,\"discipline_id\":3,\"tournament\":{\"name\":\"LPL 2026\"}}]}");
        assertEquals(1,Parsers.bo3(root,Competition.find("lol"),1).size());
    }
    @Test(expected=Exception.class) public void malformedSourceIsAnErrorNotAnEmptySchedule() throws Exception {Parsers.espn(new JSONObject("{\"error\":\"blocked\"}"),Competition.find("epl"),1);}
}
