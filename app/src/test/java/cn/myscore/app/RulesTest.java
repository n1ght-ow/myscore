package cn.myscore.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class RulesTest {
    private final long now=Match.parseTime("2026-10-24T19:00:00+08:00");
    private Match at(String date){Match m=new Match();m.start=Match.parseTime(date);m.timeConfirmed=true;m.state="scheduled";return m;}
    @Test public void importsTomorrowByShanghaiDateNotRolling24Hours(){
        assertTrue(Rules.shouldImport(at("2026-10-25T23:50:00+08:00"),now));
        assertFalse(Rules.shouldImport(at("2026-10-26T00:00:00+08:00"),now));
    }
    @Test public void catchesSameDayButNeverPastMatches(){
        assertTrue(Rules.shouldImport(at("2026-10-24T23:00:00+08:00"),now));
        assertFalse(Rules.shouldImport(at("2026-10-24T18:00:00+08:00"),now));
    }
    @Test public void tbdAndPostponedNeverCreateCalendarEntries(){
        Match m=at("2026-10-25T18:00:00+08:00");m.timeConfirmed=false;assertFalse(Rules.shouldImport(m,now));
        m.timeConfirmed=true;m.state="delayed";assertFalse(Rules.shouldImport(m,now));
        m.state="cancelled";assertFalse(Rules.shouldImport(m,now));
        m.state="live";assertFalse(Rules.shouldImport(m,now));
    }
    @Test public void invalidDatesRemainUnknown(){assertEquals(0,Match.parseTime("TBD"));assertEquals(0,Match.parseTime(""));}
    @Test public void midnightUtcConvertsToBeijingDay(){assertEquals("2026-10-25",Match.today(Match.parseTime("2026-10-24T16:01:00Z")).toString());}
    @Test public void spiritFilterExcludesAcademyAndOtherGames(){
        assertTrue(Rules.isSpirit(654,7498,1));assertTrue(Rules.isSpirit(7498,654,1));
        assertFalse(Rules.isSpirit(1309,7498,1));assertFalse(Rules.isSpirit(654,7498,3));
    }
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
