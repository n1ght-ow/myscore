package cn.myscore.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class TeamRepositoryTest {
    private static final long NOW=1_000_000_000_000L;
    private TeamRepository.Result result(String state,long age,long start) {
        TeamRepository.Result result=new TeamRepository.Result();result.fetched=NOW-age;
        Match match=new Match();match.state=state;match.start=start;match.timeConfirmed=start>0;
        result.matches.add(match);return result;
    }

    @Test public void returningToALiveTeamCannotReuseThirtyMinuteCache() {
        assertTrue(TeamRepository.fresh(result("live",1000,0),NOW));
        assertFalse(TeamRepository.fresh(result("live",30_000,0),NOW));
    }
    @Test public void aScheduledGameEnteringTheNearWindowInvalidatesTheIdleCache() {
        TeamRepository.Result result=result("scheduled",60_000,NOW+RefreshPolicy.NEAR_WINDOW+1);
        assertTrue(TeamRepository.fresh(result,NOW));
        assertFalse(TeamRepository.fresh(result,NOW+1));
    }
    @Test public void staticSchedulesKeepTheirThirtyMinuteCache() {
        assertTrue(TeamRepository.fresh(result("scheduled",29*60_000,NOW+86_400_000),NOW));
        assertFalse(TeamRepository.fresh(result("scheduled",30*60_000,NOW+86_400_000),NOW));
        assertTrue(TeamRepository.fresh(result("final",29*60_000,0),NOW));
    }
    @Test public void failedMissingAndFutureDatedReadsAreNeverFresh() {
        assertFalse(TeamRepository.fresh(null,NOW));
        assertFalse(TeamRepository.fresh(new TeamRepository.Result(),NOW));
        assertFalse(TeamRepository.fresh(result("scheduled",-1,0),NOW));
        TeamRepository.Result failed=result("scheduled",1000,0);failed.error="offline";
        assertFalse(TeamRepository.fresh(failed,NOW));
    }
    @Test public void unknownAndDelayedGamesDoNotKeepIdleCache() {
        for(String state:new String[]{"unknown","delayed"}) {
            assertTrue(TeamRepository.fresh(result(state,1000,0),NOW));
            assertFalse(TeamRepository.fresh(result(state,60_000,0),NOW));
        }
    }
    @Test public void anOpenTeamPageWakesAsItsFixtureApproaches() {
        assertEquals(30_000,RefreshPolicy.teamPollDelay(result("live",0,0).matches,NOW));
        assertEquals(30_000,RefreshPolicy.teamPollDelay(result("scheduled",0,NOW+60*60_000).matches,NOW));
        assertEquals(60_000,RefreshPolicy.teamPollDelay(result("scheduled",0,NOW+RefreshPolicy.NEAR_WINDOW+60_000).matches,NOW));
        assertEquals(RefreshPolicy.IDLE_POLL,RefreshPolicy.teamPollDelay(result("scheduled",0,NOW+86_400_000).matches,NOW));
    }
    @Test public void completedAndEmptyTeamSchedulesStopPolling() {
        assertEquals(0,RefreshPolicy.teamPollDelay(result("final",0,0).matches,NOW));
        assertEquals(0,RefreshPolicy.teamPollDelay(result("cancelled",0,0).matches,NOW));
        assertEquals(0,RefreshPolicy.teamPollDelay(new TeamRepository.Result().matches,NOW));
    }
}
