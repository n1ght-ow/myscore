package cn.myscore.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class RulesTest {
    @Test public void invalidDatesRemainUnknown(){assertEquals(0,Match.parseTime("TBD"));assertEquals(0,Match.parseTime(""));}
    @Test public void midnightUtcConvertsToBeijingDay(){assertEquals("2026-10-25",Match.today(Match.parseTime("2026-10-24T16:01:00Z")).toString());}
    @Test public void worldsFilterExcludesEwcMsiAndRegionalLeagues(){
        assertTrue(Rules.isWorlds("Worlds 2026","worlds-2026"));assertTrue(Rules.isWorlds("2025 World Championship","2025-world-championship"));
        assertFalse(Rules.isWorlds("Esports World Cup 2026","esports-world-cup-2026"));assertFalse(Rules.isWorlds("MSI 2026","msi-2026"));
        assertFalse(Rules.isWorlds("LCK 2026","lck-2026"));
    }
    @Test public void interruptionStatesOverrideLiveAndFinalState(){
        assertEquals("delayed",Rules.state("STATUS_POSTPONED","pre"));assertEquals("cancelled",Rules.state("STATUS_CANCELED","post"));
        assertEquals("live",Rules.state("STATUS_HALFTIME","in"));assertEquals("final",Rules.state("STATUS_FINAL_OT","post"));
        assertEquals("unknown",Rules.state("NEW_UNKNOWN_STATUS",""));
    }
    @Test public void cachedUnknownScoresNeverBecomeZero(){Match m=new Match();m.id="x";Match read=Match.fromJson(m.json());assertFalse(read.hasScore());assertEquals("",read.homeScore);}
}
