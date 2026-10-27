package cn.myscore.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class MiguMatchesTest {
    private static Match match(String league,String home,String away,String start){
        Match m=new Match();m.id="migu-test";m.league=league;m.home=home;m.away=away;
        m.start=Match.parseTime(start);return m;
    }
    private static JSONObject response() throws Exception {
        JSONArray items=new JSONArray();
        items.put(new JSONObject().put("widgetId","120000588291").put("confrontTeamLeftName","骑士")
            .put("confrontTeamRightName","森林狼").put("matchStartTime","2026-10-27 07:00:00")
            .put("competitionName","NBA常规赛"));
        items.put(new JSONObject().put("widgetId","120000584185").put("confrontTeamLeftName","曼联")
            .put("confrontTeamRightName","阿斯顿维拉").put("matchStartTime","2026-11-09 00:30:00")
            .put("competitionName","英超"));
        items.put(new JSONObject().put("widgetId","120000000000").put("confrontTeamLeftName","骑士")
            .put("confrontTeamRightName","森林狼").put("matchStartTime","2026-10-27 09:00:00")
            .put("competitionName","NBA常规赛"));
        items.put(new JSONObject().put("widgetId","120000000001").put("confrontTeamLeftName","湖人")
            .put("confrontTeamRightName","勇士").put("matchStartTime","2026-10-27 07:00:00")
            .put("competitionName","NBA常规赛"));
        return new JSONObject().put("code",200).put("body",new JSONObject().put("widgetResult",
            new JSONObject().put("items",items)));
    }
    @Test public void matchesNbaByChineseTeamsAndKickoffWindow() throws Exception {
        MiguMatches.Hit hit=MiguMatches.find(response(),match("nba","Cleveland Cavaliers","Minnesota Timberwolves","2026-10-27T07:00:00+08:00"),"nba");
        assertEquals("120000588291",hit.pID());
        assertEquals("骑士",hit.left());
    }
    @Test public void matchesEplDespiteOtherSportsInTheSameResponse() throws Exception {
        MiguMatches.Hit hit=MiguMatches.find(response(),match("epl","Manchester United","Aston Villa","2026-11-09T00:30:00+08:00"),"epl");
        assertEquals("120000584185",hit.pID());
    }
    @Test public void rejectsWrongTimeWrongTeamsWrongLeagueAndUnknownAliases() throws Exception {
        assertNull(MiguMatches.find(response(),match("nba","Cleveland Cavaliers","Minnesota Timberwolves","2026-10-27T10:00:00+08:00"),"nba"));
        assertNull(MiguMatches.find(response(),match("nba","Cleveland Cavaliers","Minnesota Timberwolves","2026-10-27T07:00:00+08:00"),"epl"));
        assertNull(MiguMatches.find(response(),match("epl","Unknown FC","Aston Villa","2026-11-09T00:30:00+08:00"),"epl"));
        assertNull(MiguMatches.find(response(),match("nba","Boston Celtics","Miami Heat","2026-10-27T07:00:00+08:00"),"nba"));
    }
    @Test public void teamAliasesCoverBothCompetitions() throws Exception {
        assertEquals("曼联",MiguTeams.chinese("Manchester United"));
        assertEquals("托特纳姆热刺",MiguTeams.chinese("Tottenham Hotspur"));
        assertEquals("快船",MiguTeams.chinese("LA Clippers"));
        assertEquals("76人",MiguTeams.chinese("Philadelphia 76ers"));
        assertEquals("马刺",MiguTeams.chinese("San Antonio Spurs"));
        assertNull(MiguTeams.chinese("Unknown FC"));
        assertEquals("托特纳姆热刺",MiguTeams.chinese("epl","Spurs"));
        assertEquals("马刺",MiguTeams.chinese("nba","Spurs"));
        assertEquals("狼队",MiguTeams.chinese("epl","Wolves"));
        assertEquals("森林狼",MiguTeams.chinese("nba","Wolves"));
    }

    @Test public void everyConfiguredEnglishNameHasAChineseAlias() throws Exception {
        String[] epl={"Manchester United","Aston Villa","Brighton & Hove Albion","Brentford","Crystal Palace",
            "Liverpool","Ipswich Town","AFC Bournemouth","Sunderland","Chelsea","Nottingham Forest",
            "Manchester City","Arsenal","Hull City","Fulham","Newcastle United","Leeds United",
            "Tottenham Hotspur","Everton","Coventry City"};
        String[] nba={"Atlanta Hawks","Boston Celtics","Brooklyn Nets","Charlotte Hornets","Chicago Bulls",
            "Cleveland Cavaliers","Dallas Mavericks","Denver Nuggets","Detroit Pistons","Golden State Warriors",
            "Houston Rockets","Indiana Pacers","LA Clippers","Los Angeles Lakers","Memphis Grizzlies",
            "Miami Heat","Milwaukee Bucks","Minnesota Timberwolves","New Orleans Pelicans","New York Knicks",
            "Oklahoma City Thunder","Orlando Magic","Philadelphia 76ers","Phoenix Suns","Portland Trail Blazers",
            "Sacramento Kings","San Antonio Spurs","Toronto Raptors","Utah Jazz","Washington Wizards"};
        for(String name:epl)assertNotNull(name,MiguTeams.chinese(name));
        for(String name:nba)assertNotNull(name,MiguTeams.chinese(name));
    }
}
