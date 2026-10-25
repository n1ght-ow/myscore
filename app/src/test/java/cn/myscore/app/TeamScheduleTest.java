package cn.myscore.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class TeamScheduleTest {
    private static Team team(String league,String id,String name){Team t=new Team();t.league=league;t.id=id;t.name=name;return t;}
    private static JSONObject competitor(String side,String id,String name,String score)throws Exception{
        JSONObject team=new JSONObject().put("id",id).put("displayName",name);
        JSONObject c=new JSONObject().put("homeAway",side).put("team",team);
        if(score!=null)c.put("score",new JSONObject().put("displayValue",score));
        return c;
    }
    private static JSONObject event(String eventId,String homeId,String home,String awayId,String away,String homeScore,String awayScore,String state,String name)throws Exception{
        JSONArray competitors=new JSONArray()
            .put(competitor("home",homeId,home,homeScore))
            .put(competitor("away",awayId,away,awayScore));
        JSONObject status=new JSONObject().put("type",new JSONObject().put("name",name).put("state",state));
        JSONObject competition=new JSONObject().put("id",eventId).put("date","2026-10-24T14:00Z").put("competitors",competitors).put("status",status);
        return new JSONObject().put("id",eventId).put("date","2026-10-24T14:00Z").put("competitions",new JSONArray().put(competition));
    }
    private static JSONObject schedule(JSONObject... events)throws Exception{
        JSONObject root=new JSONObject().put("team",new JSONObject().put("id","359").put("displayName","Arsenal"));
        JSONArray list=new JSONArray();for(JSONObject e:events)list.put(e);
        return root.put("events",list);
    }

    @Test public void espnMergesResultsAndFixturesForTheRequestedTeam()throws Exception{
        Team arsenal=team("epl","359","Arsenal");
        JSONObject results=schedule(event("1","359","Arsenal","368","Everton","2","0","post","STATUS_FULL_TIME"));
        JSONObject fixtures=schedule(event("2","360","Liverpool","359","Arsenal",null,null,"pre","STATUS_SCHEDULED"));
        List<Match> matches=TeamSchedule.parseEspn(arsenal,results,fixtures,100);
        assertEquals(2,matches.size());
        assertTrue(matches.stream().allMatch(m->"359".equals(m.homeId)||"359".equals(m.awayId)));
        Match future=matches.stream().filter(m->m.id.endsWith(":2")).findFirst().orElseThrow();
        assertTrue(future.scheduled());
        assertFalse(future.hasScore());
    }
    @Test public void duplicateFixtureAcrossPagesIsKeptOnce()throws Exception{
        JSONObject page=schedule(event("1","359","Arsenal","368","Everton","2","0","post","STATUS_FULL_TIME"));
        assertEquals(1,TeamSchedule.parseEspn(team("epl","359","Arsenal"),page,page,100).size());
    }
    @Test public void anotherTeamsScheduleIsRejected()throws Exception{
        JSONObject root=schedule();root.getJSONObject("team").put("id","999");
        try{TeamSchedule.verifyTeam(root,team("epl","359","Arsenal"));fail("mismatched team id must not be shown");}
        catch(Exception expected){assertTrue(expected.getMessage().contains("其他球队"));}
    }
    @Test public void eventsWithoutTheRequestedTeamAreDropped()throws Exception{
        JSONObject root=schedule(event("1","360","Liverpool","368","Everton","1","0","post","STATUS_FULL_TIME"));
        assertTrue(TeamSchedule.parseEspn(team("epl","359","Arsenal"),root,null,100).isEmpty());
    }

    @Test public void worldsScheduleKeepsOnlyTheRequestedTeam()throws Exception{
        JSONObject root=new JSONObject().put("status","0").put("lastUpTime","2026-10-25 10:00:00");
        JSONArray list=new JSONArray();
        list.put(new JSONObject().put("GameName","2026 全球总决赛").put("bMatchId","11").put("MatchDate","2026-10-20 20:00:00").put("MatchStatus","3").put("TeamA","t1").put("TeamB","gen").put("TeamShortNameA","T1").put("TeamShortNameB","GEN").put("ScoreA","3").put("ScoreB","1"));
        list.put(new JSONObject().put("GameName","2026 全球总决赛").put("bMatchId","12").put("MatchDate","2026-10-21 20:00:00").put("MatchStatus","3").put("TeamA","blg").put("TeamB","tes").put("TeamShortNameA","BLG").put("TeamShortNameB","TES").put("ScoreA","3").put("ScoreB","2"));
        root.put("msg",list);
        List<Match> matches=TeamSchedule.parseWorlds(team("lol","gen","GEN"),root,100);
        assertEquals(1,matches.size());assertEquals("qq:lol:11",matches.get(0).id);assertEquals("final",matches.get(0).state);
    }

    private static JSONObject fiveRow(String matchId,String homeId,String awayId,String homeScore,String awayScore,String matchStatus,String ts)throws Exception{
        JSONObject mc=new JSONObject()
            .put("id",matchId).put("ts",ts).put("match_status",matchStatus).put("status",matchStatus.equals("2")?"past":"future")
            .put("format","3").put("tt_stage_desc","淘汰赛 半决赛")
            .put("home_score",homeScore).put("opponent_score",awayScore)
            .put("home_info",new JSONObject().put("id",homeId).put("disp_name","Team Spirit").put("logo","https://oss.5eplay.com/sport/csgo/team/a.png"))
            .put("opponent_info",new JSONObject().put("id",awayId).put("disp_name","MOUZ").put("logo","https://oss.5eplay.com/sport/csgo/team/b.png"));
        return new JSONObject().put("mc_info",mc).put("tt_info",new JSONObject().put("disp_name","EPL S24"));
    }
    private static JSONObject fivePage(JSONObject... rows)throws Exception{
        JSONArray list=new JSONArray();for(JSONObject row:rows)list.put(row);
        return new JSONObject().put("success",true).put("data",new JSONObject().put("items",new JSONObject().put("matches",list)));
    }
    @Test public void fiveScheduleKeepsSourceScoreAndTeamOrder()throws Exception{
        JSONObject root=fivePage(fiveRow("csgo_mc_1","csgo_tm_7020","csgo_tm_4494","2","0","2","1791652200"));
        Match m=TeamSchedule.parseFive(team("cs2","csgo_tm_7020","Team Spirit"),root,100).get(0);
        assertEquals("five:cs2:csgo_mc_1",m.id);assertEquals("2",m.homeScore);assertEquals("0",m.awayScore);assertEquals("final",m.state);
        assertEquals("EPL S24 · BO3 · 淘汰赛 半决赛",m.detail);assertTrue(m.timeConfirmed);
        TeamScheduleRow row=TeamScheduleRow.match(m,team("cs2","csgo_tm_7020","Team Spirit"));
        assertEquals("2–0",row.score);assertEquals("胜",row.result);assertTrue(row.home);assertEquals("MOUZ",row.opponent);
        assertEquals("",row.side);
    }
    @Test public void fiveLiveAndUpcomingScoresStayUnknownUntilPublished()throws Exception{
        Team spirit=team("cs2","csgo_tm_7020","Team Spirit");
        Match live=TeamSchedule.parseFive(spirit,fivePage(fiveRow("csgo_mc_2","csgo_tm_7020","csgo_tm_4494","","","1","1791652200")),100).get(0);
        assertEquals("live",live.state);assertFalse(live.hasScore());
        Match future=TeamSchedule.parseFive(spirit,fivePage(fiveRow("csgo_mc_3","csgo_tm_7020","csgo_tm_4494","0","0","0","1791652200")),100).get(0);
        assertEquals("scheduled",future.state);assertFalse(future.hasScore());
        assertTrue(TeamScheduleRow.match(future,spirit).score.isEmpty());
    }
    @Test public void fiveScheduleIgnoresAnotherPairing()throws Exception{
        JSONObject root=fivePage(fiveRow("csgo_mc_4","csgo_tm_11861","csgo_tm_4494","2","0","2","1791652200"));
        assertTrue(TeamSchedule.parseFive(team("cs2","csgo_tm_7020","Team Spirit"),root,100).isEmpty());
    }

    @Test public void awayTeamScoreIsPresentedInTheTeamsOwnOrder()throws Exception{
        Match m=new Match();m.id="m";m.league="epl";m.group="足球";m.home="Liverpool";m.away="Arsenal";m.homeId="360";m.awayId="359";
        m.homeScore="1";m.awayScore="2";m.state="final";m.status="已结束";m.timeConfirmed=true;m.start=1_793_165_200_000L;
        TeamScheduleRow row=TeamScheduleRow.match(m,team("epl","359","Arsenal"));
        assertEquals("Liverpool",row.opponent);
        assertEquals("2–1",row.score);assertEquals("胜",row.result);assertFalse(row.home);assertEquals("客场",row.side);
    }
    @Test public void scheduledRowDoesNotInventAScoreOrResult()throws Exception{
        Match m=new Match();m.id="m";m.league="epl";m.group="足球";m.home="Arsenal";m.away="Everton";m.homeId="359";m.awayId="368";
        m.state="scheduled";m.status="未开始";m.timeConfirmed=true;m.start=1_793_165_200_000L;
        TeamScheduleRow row=TeamScheduleRow.match(m,team("epl","359","Arsenal"));
        assertEquals("",row.score);assertEquals("",row.result);assertEquals("未开始",row.state);assertTrue(row.home);
    }
    @Test public void unknownStartTimeIsNotFabricated()throws Exception{
        Match m=new Match();m.id="m";m.league="epl";m.group="足球";m.home="Arsenal";m.away="Everton";m.homeId="359";m.awayId="368";m.state="scheduled";
        TeamScheduleRow row=TeamScheduleRow.match(m,team("epl","359","Arsenal"));
        assertEquals("日期待定",row.date);assertEquals("待定",row.time);
    }
    @Test public void resultLabelOnlyAcceptsNumericScores(){
        assertEquals("胜",TeamScheduleRow.result("2","1"));assertEquals("负",TeamScheduleRow.result("1","2"));assertEquals("平",TeamScheduleRow.result("1","1"));
        assertEquals("",TeamScheduleRow.result("—","1"));assertEquals("",TeamScheduleRow.result("",""));
    }

    @Test public void teamIdentityRoundTripsAndMissingIdsStayUnusable()throws Exception{
        Match m=new Match();m.league="epl";m.home="Arsenal";m.away="Everton";m.homeId="359";m.awayId="368";m.homeLogo="https://a.espncdn.com/359.png";
        Team home=Team.of(m,true);assertEquals("359",home.id);assertEquals("Arsenal",home.name);
        Team restored=Team.fromJson(new JSONObject(home.json().toString()));
        assertEquals("epl",restored.league);assertEquals("359",restored.id);assertEquals("Arsenal",restored.name);assertTrue(restored.usable());
        Match c=new Match();c.league="epl";c.home="Arsenal";c.away="Everton";
        assertNull(Team.of(c,true));
    }
}
