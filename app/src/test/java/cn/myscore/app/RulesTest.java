package cn.myscore.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class RulesTest {
    @Test public void invalidDatesRemainUnknown(){assertEquals(0,Match.parseTime("TBD"));assertEquals(0,Match.parseTime(""));}
    @Test public void midnightUtcConvertsToBeijingDay(){assertEquals("2026-10-25",Match.today(Match.parseTime("2026-10-24T16:01:00Z")).toString());}
    @Test public void durationCoversSurvivingCompetitionGroups(){
        assertEquals(4*60*60_000L,Rules.estimatedDuration("NFL"));
        assertEquals(3*60*60_000L,Rules.estimatedDuration("NBA"));
        assertEquals(3*60*60_000L,Rules.estimatedDuration("CS2"));
        assertEquals(2*60*60_000L,Rules.estimatedDuration("足球"));
    }
    @Test public void interruptionStatesOverrideLiveAndFinalState(){
        assertEquals("delayed",Rules.state("STATUS_POSTPONED","pre"));assertEquals("cancelled",Rules.state("STATUS_CANCELED","post"));
        assertEquals("live",Rules.state("STATUS_HALFTIME","in"));assertEquals("final",Rules.state("STATUS_FINAL_OT","post"));
        assertEquals("unknown",Rules.state("NEW_UNKNOWN_STATUS",""));
    }
    @Test public void cachedUnknownScoresNeverBecomeZero(){Match m=new Match();m.id="x";Match read=Match.fromJson(m.json());assertFalse(read.hasScore());assertEquals("",read.homeScore);}
}
