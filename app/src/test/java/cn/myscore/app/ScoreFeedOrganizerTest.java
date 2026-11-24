package cn.myscore.app;

import org.junit.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

public class ScoreFeedOrganizerTest {
    private static Match match(String id, String league, String group, String state, long start) {
        Match m = new Match();
        m.id = id;
        m.league = league;
        m.group = group;
        m.state = state;
        m.start = start;
        m.timeConfirmed = start > 0;
        m.homeId = "h-" + id;
        m.awayId = "a-" + id;
        m.home = "Home " + id;
        m.away = "Away " + id;
        return m;
    }

    @Test
    public void weekStripCentersSelectedDateAndMarksToday() {
        LocalDate today = LocalDate.of(2026, 5, 10);
        LocalDate selected = LocalDate.of(2026, 5, 11);
        List<ScoreFeedOrganizer.DayChip> strip = ScoreFeedOrganizer.weekStrip(selected, today);
        assertEquals(7, strip.size());
        assertEquals(LocalDate.of(2026, 5, 8), strip.get(0).date());
        assertEquals(selected, strip.get(3).date());
        assertTrue(strip.get(3).selected());
        assertTrue(strip.get(2).today());
        assertEquals("Today", strip.get(2).weekday());
        assertFalse(strip.get(0).today());
    }

    @Test
    public void summarizeAndFilterSupportLiveAndSavedLenses() {
        Match live = match("1", "epl", "足球", "live", 1000);
        Match upcoming = match("2", "nba", "NBA", "scheduled", 2000);
        Match done = match("3", "cs2", "CS2", "final", 500);
        List<Match> all = List.of(live, upcoming, done);
        Set<String> saved = Set.of("2", "3");

        ScoreFeedOrganizer.FeedSummary summary = ScoreFeedOrganizer.summarize(all, saved);
        assertEquals(3, summary.total());
        assertEquals(1, summary.live());
        assertEquals(1, summary.upcoming());
        assertEquals(1, summary.completed());
        assertEquals(2, summary.saved());
        assertEquals(Integer.valueOf(1), summary.bySport().get("NBA"));

        assertEquals(1, ScoreFeedOrganizer.filter(all, "全部", ScoreFeedOrganizer.LENS_LIVE, saved).size());
        assertEquals("1", ScoreFeedOrganizer.filter(all, "全部", ScoreFeedOrganizer.LENS_LIVE, saved).get(0).id);
        assertEquals(2, ScoreFeedOrganizer.filter(all, "全部", ScoreFeedOrganizer.LENS_SAVED, saved).size());
        assertEquals(1, ScoreFeedOrganizer.filter(all, "NBA", ScoreFeedOrganizer.LENS_SAVED, saved).size());
    }

    @Test
    public void groupByCompetitionPrioritizesLiveLeaguesAndSplitsCs2Events() {
        Match eplFinal = match("1", "epl", "足球", "final", 100);
        Match nbaLive = match("2", "nba", "NBA", "live", 200);
        Match cs1 = match("3", "cs2", "CS2", "scheduled", 300);
        cs1.event = "IEM Cologne 2026";
        Match cs2 = match("4", "cs2", "CS2", "live", 250);
        cs2.event = "BLAST Open 2026";

        List<ScoreFeedOrganizer.CompetitionGroup> groups = ScoreFeedOrganizer.groupByCompetition(List.of(eplFinal, nbaLive, cs1, cs2));
        assertEquals(4, groups.size());
        // Live competitions come first (NBA before CS2 by competition order)
        assertEquals("nba", groups.get(0).leagueId());
        assertEquals(1, groups.get(0).liveCount());
        assertEquals("BLAST Open 2026", groups.get(1).title());
        assertTrue(groups.get(1).hasCs2Tournament());
        // Non-live competitions follow by priority (EPL before CS2)
        assertEquals("epl", groups.get(2).leagueId());
        assertTrue(groups.get(2).hasStandings());
        assertEquals("IEM Cologne 2026", groups.get(3).title());
    }

    @Test
    public void teamSnapshotComputesRealFormWithoutInventingUpcomingResults() {
        Team arsenal = new Team();
        arsenal.id = "359";
        arsenal.league = "epl";
        arsenal.name = "Arsenal";

        Match m1 = match("1", "epl", "足球", "final", 1000);
        m1.homeId = "359"; m1.home = "Arsenal"; m1.away = "Everton"; m1.homeScore = "2"; m1.awayScore = "0";
        Match m2 = match("2", "epl", "足球", "final", 2000);
        m2.awayId = "359"; m2.away = "Arsenal"; m2.home = "Liverpool"; m2.homeScore = "1"; m2.awayScore = "1";
        Match m3 = match("3", "epl", "足球", "final", 3000);
        m3.awayId = "359"; m3.away = "Arsenal"; m3.home = "City"; m3.homeScore = "3"; m3.awayScore = "1";
        Match future = match("4", "epl", "足球", "scheduled", 4000);
        future.homeId = "359"; future.home = "Arsenal"; future.away = "Chelsea";

        ScoreFeedOrganizer.TeamSnapshot snap = ScoreFeedOrganizer.summarizeTeam(arsenal, List.of(m1, m2, m3, future));
        assertEquals(1, snap.wins());
        assertEquals(1, snap.draws());
        assertEquals(1, snap.losses());
        assertEquals(1, snap.upcoming());
        assertEquals(3, snap.completed());
        assertEquals(3, snap.recentForm().size());
        assertEquals("L", snap.recentForm().get(0).result());
        assertEquals("D", snap.recentForm().get(1).result());
        assertEquals("W", snap.recentForm().get(2).result());
    }

    @Test
    public void standingsZonesClassifyKeyPositions() {
        assertEquals(ScoreFeedOrganizer.Zone.CHAMPIONS, ScoreFeedOrganizer.standingsZone("epl", "Premier League", 2, 20));
        assertEquals(ScoreFeedOrganizer.Zone.PLAY_IN, ScoreFeedOrganizer.standingsZone("epl", "Premier League", 5, 20));
        assertEquals(ScoreFeedOrganizer.Zone.RELEGATION, ScoreFeedOrganizer.standingsZone("epl", "Premier League", 19, 20));
        assertEquals(ScoreFeedOrganizer.Zone.QUALIFY, ScoreFeedOrganizer.standingsZone("nba", "Western", 4, 15));
        assertEquals(ScoreFeedOrganizer.Zone.PLAY_IN, ScoreFeedOrganizer.standingsZone("nba", "Western", 8, 15));
        assertEquals(ScoreFeedOrganizer.Zone.PODIUM_GOLD, ScoreFeedOrganizer.standingsZone("f1", "Drivers", 1, 20));
    }

    @Test
    public void statMagnitudeExtractsNumbersFromPercentagesAndRatios() {
        assertEquals(62.0, ScoreFeedOrganizer.statMagnitude("62%"), 0.01);
        assertEquals(53.0, ScoreFeedOrganizer.statMagnitude("18/34 (53%)"), 0.01);
        assertEquals(14.0, ScoreFeedOrganizer.statMagnitude("14"), 0.01);
        assertTrue(Double.isNaN(ScoreFeedOrganizer.statMagnitude("—")));
    }
}
