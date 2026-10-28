package cn.myscore.app;

import org.junit.Test;
import java.time.LocalDate;
import java.util.List;
import static org.junit.Assert.*;

public class RefreshPolicyTest {
    private static final LocalDate TODAY=LocalDate.of(2026,10,27);
    private static final long NOW=1_000_000_000_000L;
    private static Match match(String state){return match(state,0);}
    private static Match match(String state,long start){Match m=new Match();m.id="m:"+state+":"+start;m.state=state;m.start=start;m.timeConfirmed=start>0;return m;}
    private static Repository.Batch batch(Match... matches){Repository.Batch b=new Repository.Batch(Competition.find("nba"));b.matches.addAll(List.of(matches));return b;}

    @Test public void allActiveSportsShareTheFasterCadence(){for(String group:new String[]{"足球","NBA","NFL","CS2","LOL"}){Match m=new Match();m.group=group;m.state="live";assertEquals(10_000,RefreshPolicy.detail(m));}Match f1=new Match();f1.group="F1";f1.state="live";assertEquals(15_000,RefreshPolicy.detail(f1));}
    @Test public void finishedDetailsUseLongCache(){Match m=new Match();m.state="final";assertEquals(21_600_000,RefreshPolicy.detail(m));}
    @Test public void slowRequestsDoNotAddAnotherWholeInterval(){assertEquals(7000,RefreshPolicy.remaining(10_000,3000));assertEquals(1000,RefreshPolicy.remaining(10_000,20_000));}
    @Test public void failuresBackOffIndependentlyAndRemainBounded(){assertEquals(10_000,RefreshPolicy.retry(10_000,0));assertEquals(20_000,RefreshPolicy.retry(10_000,1));assertEquals(40_000,RefreshPolicy.retry(10_000,2));assertEquals(120_000,RefreshPolicy.retry(10_000,100));}
    @Test public void retryNeverShortensAnIdleOrFinishedDetailInterval(){
        for(long interval:new long[]{RefreshPolicy.DETAIL_IDLE,RefreshPolicy.FINAL_DETAIL}) {
            assertEquals(interval,RefreshPolicy.retry(interval,0));
            assertTrue(RefreshPolicy.retry(interval,3)>=interval);
        }
    }
    @Test public void changedListStateInvalidatesFinishedDetail(){Match requested=new Match(),cached=new Match();requested.id=cached.id="same";requested.state="live";cached.state="final";assertFalse(RefreshPolicy.freshDetail(requested,cached,1000,2000));}
    @Test public void changedScoreInvalidatesDetail(){Match requested=new Match(),cached=new Match();requested.id=cached.id="same";requested.homeScore="2";cached.homeScore="1";assertFalse(RefreshPolicy.freshDetail(requested,cached,1000,2000));}
    @Test public void detailCacheAllowsCadenceJitterAndRejectsFutureTime(){Match m=new Match();m.state="live";assertTrue(RefreshPolicy.freshDetail(m,m,1000,10_000));assertFalse(RefreshPolicy.freshDetail(m,m,1000,10_500));assertFalse(RefreshPolicy.freshDetail(m,m,2000,1000));assertFalse(RefreshPolicy.freshDetail(m,m,0,100));}
    @Test public void differentMatchNeverReusesDetail(){Match a=new Match(),b=new Match();a.id="a";b.id="b";assertFalse(RefreshPolicy.freshDetail(a,b,1000,2000));}

    @Test public void terminalStatesAreLockedTogether(){
        Match finished=match("final"),cancelled=match("cancelled"),live=match("live");
        assertTrue(RefreshPolicy.terminal(finished));assertTrue(RefreshPolicy.terminal(cancelled));assertFalse(RefreshPolicy.terminal(live));
        assertTrue(RefreshPolicy.settled(List.of(finished,cancelled)));
        assertFalse(RefreshPolicy.settled(List.of(finished,live)));
        assertTrue(RefreshPolicy.settled(List.of()));
    }

    @Test public void settledMatchesSkipTheSource(){
        List<Match> done=List.of(match("final"),match("cancelled"));
        assertEquals(RefreshPolicy.PAST_CACHE,RefreshPolicy.cacheTtl(done,TODAY.minusDays(1),TODAY,NOW));
        Repository.Batch batch=batch(done.toArray(Match[]::new));
        assertEquals(RefreshPolicy.IDLE_POLL,RefreshPolicy.pollDelay(List.of(batch),TODAY,TODAY,NOW));
    }

    @Test public void liveMatchesKeepTheFastCadenceEvenOnAnOldDate(){
        assertEquals(RefreshPolicy.SCORE_CACHE,RefreshPolicy.cacheTtl(List.of(match("live")),TODAY.minusDays(1),TODAY,NOW));
        assertEquals(RefreshPolicy.SCORES,RefreshPolicy.pollDelay(List.of(batch(match("live"))),TODAY.minusDays(1),TODAY,NOW));
    }

    @Test public void pastDatesUseTheLongCacheAndDoNotPoll(){
        assertEquals(RefreshPolicy.PAST_CACHE,RefreshPolicy.cacheTtl(List.of(match("scheduled",NOW+3600_000L)),TODAY.minusDays(1),TODAY,NOW));
        assertEquals(0,RefreshPolicy.pollDelay(List.of(batch(match("scheduled",NOW+3600_000L))),TODAY.minusDays(1),TODAY,NOW));
        assertEquals(0,RefreshPolicy.pollDelay(List.of(batch(match("delayed"))),TODAY.minusDays(1),TODAY,NOW));
    }

    @Test public void futureDatesReuseTheLastRead(){
        assertEquals(RefreshPolicy.FUTURE_CACHE,RefreshPolicy.cacheTtl(List.of(match("scheduled",NOW+86_400_000L)),TODAY.plusDays(1),TODAY,NOW));
        assertEquals(RefreshPolicy.IDLE_POLL,RefreshPolicy.pollDelay(List.of(batch(match("scheduled",NOW+86_400_000L))),TODAY.plusDays(1),TODAY,NOW));
    }

    @Test public void aMatchStartingSoonStaysFreshOnTomorrowsDate(){
        assertEquals(RefreshPolicy.NEAR_CACHE,RefreshPolicy.cacheTtl(List.of(match("scheduled",NOW+60*60_000L)),TODAY.plusDays(1),TODAY,NOW));
    }

    @Test public void todayUsesStateAwareTtls(){
        assertEquals(RefreshPolicy.SLOW_CACHE,RefreshPolicy.cacheTtl(List.of(match("delayed")),TODAY,TODAY,NOW));
        assertEquals(RefreshPolicy.SLOW_CACHE,RefreshPolicy.cacheTtl(List.of(match("unknown")),TODAY,TODAY,NOW));
        assertEquals(RefreshPolicy.NEAR_CACHE,RefreshPolicy.cacheTtl(List.of(match("scheduled",NOW+60*60_000L)),TODAY,TODAY,NOW));
        assertEquals(RefreshPolicy.IDLE_CACHE,RefreshPolicy.cacheTtl(List.of(match("scheduled",NOW+5*60*60_000L)),TODAY,TODAY,NOW));
        assertEquals(RefreshPolicy.IDLE_CACHE,RefreshPolicy.cacheTtl(List.of(),TODAY,TODAY,NOW));
    }

    @Test public void todayUsesStateAwarePolling(){
        assertEquals(RefreshPolicy.SCORES,RefreshPolicy.pollDelay(List.of(batch(match("live"))),TODAY,TODAY,NOW));
        assertEquals(RefreshPolicy.SLOW_POLL,RefreshPolicy.pollDelay(List.of(batch(match("delayed"))),TODAY,TODAY,NOW));
        assertEquals(RefreshPolicy.NEAR_POLL,RefreshPolicy.pollDelay(List.of(batch(match("scheduled",NOW+60*60_000L))),TODAY,TODAY,NOW));
        assertEquals(RefreshPolicy.IDLE_POLL,RefreshPolicy.pollDelay(List.of(batch(match("scheduled",NOW+5*60*60_000L))),TODAY,TODAY,NOW));
        assertEquals(RefreshPolicy.IDLE_POLL,RefreshPolicy.pollDelay(List.of(batch()),TODAY,TODAY,NOW));
    }

    @Test public void nearStartIsClampedToTheMinimumDelay(){
        long start=NOW+RefreshPolicy.NEAR_WINDOW+30_000L;
        assertEquals(60_000L,RefreshPolicy.pollDelay(List.of(batch(match("scheduled",start))),TODAY,TODAY,NOW));
    }

    @Test public void errorsRetryOnTheFastCadenceUnlessTheDateIsPast(){
        Repository.Batch failed=batch(match("scheduled",NOW+5*60*60_000L));failed.error="连接失败";
        assertEquals(RefreshPolicy.SLOW_POLL,RefreshPolicy.pollDelay(List.of(failed),TODAY,TODAY,NOW));
        failed.retryAt=NOW+45_000L;
        assertEquals(45_000L,RefreshPolicy.pollDelay(List.of(failed),TODAY,TODAY,NOW));
        Repository.Batch pastFailed=batch(match("scheduled",NOW+5*60*60_000L));pastFailed.error="连接失败";
        assertEquals(0,RefreshPolicy.pollDelay(List.of(pastFailed),TODAY.minusDays(1),TODAY,NOW));
    }

    @Test public void emptyFutureDateIsStillCheckedSlowly(){
        assertEquals(RefreshPolicy.IDLE_POLL,RefreshPolicy.pollDelay(List.of(batch()),TODAY.plusDays(1),TODAY,NOW));
    }

    @Test public void aDetailFetchedAfterBothSidesFinishedIsFrozen(){
        Match requested=match("final"),cached=match("final");
        assertTrue(RefreshPolicy.frozenDetail(requested,cached));
        Match cancelled=match("cancelled");cancelled.id=requested.id;
        assertFalse(RefreshPolicy.frozenDetail(cancelled,cached));
        Match live=match("live");live.id=requested.id;
        assertFalse(RefreshPolicy.frozenDetail(requested,live));
        assertFalse(RefreshPolicy.frozenDetail(live,cancelled));
        Match other=match("final");other.id="other";
        assertFalse(RefreshPolicy.frozenDetail(requested,other));
    }

    @Test public void correctedFinalScoresInvalidateFrozenDetails(){
        Match requested=match("final"),cached=match("final");
        requested.homeScore="2";cached.homeScore="1";
        assertFalse(RefreshPolicy.frozenDetail(requested,cached));
        cached.homeScore="2";requested.awayScore="3";cached.awayScore="2";
        assertFalse(RefreshPolicy.frozenDetail(requested,cached));
    }

    @Test public void scheduledDetailsUseTheIdleCadenceUntilCloseToStart(){
        assertEquals(RefreshPolicy.DETAIL_IDLE,RefreshPolicy.detail(match("scheduled",NOW+3*60*60_000L),NOW));
        assertEquals(RefreshPolicy.SLOW_POLL,RefreshPolicy.detail(match("scheduled",NOW+60*60_000L),NOW));
        assertEquals(RefreshPolicy.DETAIL_IDLE,RefreshPolicy.detail(match("scheduled",0),NOW));
        assertEquals(RefreshPolicy.SLOW_POLL,RefreshPolicy.detail(match("delayed"),NOW));
    }

    @Test public void midnightWakeKeepsRelativeDatesAnchored(){
        long noon=java.time.ZonedDateTime.of(2026,10,27,12,0,0,0,Match.ZONE).toInstant().toEpochMilli();
        assertEquals(12*60*60_000L+1000,RefreshPolicy.untilNextDay(noon));
    }
}
