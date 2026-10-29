package cn.myscore.app;

import org.json.JSONObject;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.Assert.*;

public class ShootoutScoreTest {
    private JSONObject fixture() throws Exception {
        // Public ESPN summary for Manchester City-Brighton, event 401921834.
        try (var in=getClass().getResourceAsStream("/details/efl-penalties.json")) {
            return new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));
        }
    }
    private JSONObject competition(JSONObject root) throws Exception {return root.getJSONObject("header").getJSONArray("competitions").getJSONObject(0);}
    private Match parse(JSONObject root) throws Exception {
        return Parsers.espn(new JSONObject().put("events",new org.json.JSONArray().put(root.getJSONObject("header"))),Competition.find("efl"),123).get(0);
    }
    @Test public void separatesNormalAndShootoutScoresFromRealSource() throws Exception {
        Match m=parse(fixture());
        assertEquals("2",m.homeScore);assertEquals("2",m.awayScore);
        assertEquals("4",m.homeShootout);assertEquals("3",m.awayShootout);
        assertEquals("2(4)",m.displayScore(true));assertEquals("2(3)",m.displayScore(false));
        assertEquals("2(4) : 2(3)",m.score());assertTrue(m.outcome(true)>0);assertTrue(m.outcome(false)<0);
        assertFalse(m.needsScoreRefresh());
    }
    @Test public void ordinaryDrawDoesNotUseDefaultOrStrayPenaltyFields() throws Exception {
        JSONObject root=fixture();competition(root).getJSONObject("status").getJSONObject("type").put("name","STATUS_FULL_TIME");
        Match m=parse(root);
        assertFalse(m.hasShootout());assertEquals("2",m.displayScore(true));assertEquals(0,m.outcome(true));assertEquals(0,m.outcome(false));
        assertFalse(m.needsScoreRefresh());
    }
    @Test public void extraTimeWinnerUsesTheActualTotalWithoutPenaltySuffix() throws Exception {
        JSONObject root=fixture();JSONObject c=competition(root);
        c.getJSONObject("status").getJSONObject("type").put("name","STATUS_FINAL_AET");
        c.getJSONArray("competitors").getJSONObject(0).put("score","3");
        Match m=parse(root);
        assertEquals("3 : 2",m.score());assertFalse(m.hasShootout());assertTrue(m.outcome(true)>0);
    }
    @Test public void incompleteNegativeAndFractionalShootoutsStayUnknown() throws Exception {
        for(Object bad:new Object[]{JSONObject.NULL,-1,3.5,"unknown"}) {
            JSONObject root=fixture();competition(root).getJSONArray("competitors").getJSONObject(1).put("shootoutScore",bad);
            Match m=parse(root);assertFalse(m.hasShootout());assertEquals("2 : 2",m.score());assertEquals(0,m.outcome(true));
        }
    }
    @Test public void liveShootoutDoesNotPrematurelyColorAWinner() throws Exception {
        JSONObject root=fixture();competition(root).getJSONObject("status").getJSONObject("type").put("name","STATUS_PENALTIES").put("state","in");
        Match m=parse(root);assertTrue(m.live());assertEquals("点球大战",m.status);
        assertEquals("2(4)",m.displayScore(true));assertEquals(0,m.outcome(true));assertEquals(0,m.outcome(false));
    }
    @Test public void zeroPenaltiesAreAValidKnownScore() throws Exception {
        JSONObject root=fixture();JSONObject c=competition(root);
        c.getJSONArray("competitors").getJSONObject(0).put("score","0").put("shootoutScore",0);
        c.getJSONArray("competitors").getJSONObject(1).put("score","0").put("shootoutScore",3);
        Match m=parse(root);assertEquals("0(0) : 0(3)",m.score());assertTrue(m.outcome(true)<0);
    }
    @Test public void cacheRoundTripKeepsBothScoresAndLegacyDrawNeedsOneRefresh() throws Exception {
        Match m=parse(fixture()),copy=Match.fromJson(m.json());
        assertEquals(m.score(),copy.score());assertEquals(m.outcome(true),copy.outcome(true));
        JSONObject old=m.json();old.remove("homeShootout");old.remove("awayShootout");old.remove("scoreDataVersion");
        Match legacy=Match.fromJson(old);assertEquals("2 : 2",legacy.score());assertTrue(legacy.needsScoreRefresh());
        assertSame(m,Repository.mergeTerminal(List.of(legacy),List.of(m)).get(0));
        assertSame(m,Repository.mergeTerminal(List.of(m),List.of(legacy)).get(0));
        TeamRepository.Result schedule=new TeamRepository.Result();schedule.fetched=System.currentTimeMillis();schedule.matches.add(legacy);
        assertFalse(TeamRepository.fresh(schedule,System.currentTimeMillis()));
        schedule.matches.clear();schedule.matches.add(m);assertTrue(TeamRepository.fresh(schedule,System.currentTimeMillis()));
    }
    @Test public void detailAndTeamRowsUseTheSameWinnerFromBothPerspectives() throws Exception {
        JSONObject root=fixture();Match m=parse(root);
        MatchDetail detail=DetailParsers.espn(root,m,123);assertEquals("2(4) : 2(3)",detail.match.score());
        Team city=new Team();city.id=m.homeId;city.name=m.home;
        Team brighton=new Team();brighton.id=m.awayId;brighton.name=m.away;
        TeamScheduleRow own=TeamScheduleRow.match(m,city),opponent=TeamScheduleRow.match(m,brighton);
        assertEquals("2(4)–2(3)",own.score);assertEquals("胜",own.result);
        assertEquals("2(3)–2(4)",opponent.score);assertEquals("负",opponent.result);
    }
    @Test public void highlightMatchingStillUsesTheRegularMatchResult() throws Exception {
        Match m=parse(fixture());
        try(var in=getClass().getResourceAsStream("/watch/city-highlights.json")) {
            var episodes=BilibiliVods.parse(new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8)),BilibiliVods.Source.CITY_HIGHLIGHTS);
            assertEquals("BV1hgsd6gE6a",BilibiliVods.find(episodes,m).bvid());
        }
    }
    @Test public void changedPenaltyScoresInvalidateTheFrozenDetail() throws Exception {
        Match m=parse(fixture()),old=Match.fromJson(m.json());old.awayShootout="5";
        assertFalse(RefreshPolicy.frozenDetail(m,old));
        assertTrue(RefreshPolicy.frozenDetail(m,Match.fromJson(m.json())));
    }
    @Test public void teamScheduleReadsShootoutsNestedInsideScoreObjects() throws Exception {
        JSONObject root=fixture();var teams=competition(root).getJSONArray("competitors");
        for(int i=0;i<teams.length();i++){
            JSONObject side=teams.getJSONObject(i);
            side.put("score",new JSONObject().put("displayValue",side.getString("score")).put("shootoutScore",side.get("shootoutScore")));
            side.remove("shootoutScore");
        }
        Team city=new Team();city.league="efl";city.id="382";city.name="Manchester City";
        Match m=TeamSchedule.parseEspn(city,new JSONObject().put("events",new org.json.JSONArray().put(root.getJSONObject("header"))),null,123).get(0);
        assertEquals("2(4) : 2(3)",m.score());assertEquals("胜",TeamScheduleRow.match(m,city).result);
    }
}
