package cn.myscore.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CupStandingsTest {
    private static JSONObject league(int season, String display, String... rounds) throws Exception {
        JSONArray entries = new JSONArray();
        for (String round : rounds) entries.put(new JSONObject().put("label", round));
        JSONObject calendar = new JSONObject().put("entries", entries);
        JSONObject seasonInfo = new JSONObject().put("year", season).put("displayName", display);
        return new JSONObject().put("season", seasonInfo).put("calendar", new JSONArray().put(calendar));
    }

    private static JSONObject root(JSONObject league, JSONObject... events) throws Exception {
        JSONArray list = new JSONArray();
        for (JSONObject event : events) list.put(event);
        return new JSONObject().put("leagues", new JSONArray().put(league)).put("events", list);
    }

    private static JSONObject side(String id, String name, String score, boolean winner, String position) throws Exception {
        JSONObject team = new JSONObject().put("id", id).put("displayName", name)
            .put("logo", "https://a.espncdn.com/i/teamlogos/soccer/500/" + id + ".png");
        JSONObject side = new JSONObject().put("homeAway", position).put("team", team);
        if (score != null) side.put("score", score);
        if (winner) side.put("winner", true);
        return side;
    }

    private static JSONObject series(String homeId, int homeAggregate, boolean homeWinner,
                                     String awayId, int awayAggregate, boolean awayWinner) throws Exception {
        JSONArray competitors = new JSONArray()
            .put(new JSONObject().put("id", homeId).put("aggregateScore", homeAggregate).put("winner", homeWinner))
            .put(new JSONObject().put("id", awayId).put("aggregateScore", awayAggregate).put("winner", awayWinner));
        return new JSONObject().put("completed", true).put("totalCompetitions", 2).put("competitors", competitors);
    }

    private static JSONObject event(int season, String slug, String id, String date, String state,
                                    boolean completed, String statusName, String detail, String alt,
                                    String note, int leg, JSONObject series,
                                    JSONObject home, JSONObject away) throws Exception {
        JSONObject type = new JSONObject().put("state", state).put("name", statusName)
            .put("detail", detail).put("completed", completed);
        JSONObject competition = new JSONObject().put("id", id).put("date", date)
            .put("status", new JSONObject().put("type", type))
            .put("altGameNote", alt)
            .put("competitors", new JSONArray().put(home).put(away));
        if (note != null && !note.isEmpty()) {
            competition.put("notes", new JSONArray().put(new JSONObject().put("text", note)));
        }
        if (leg > 0) {
            competition.put("leg", new JSONObject().put("value", leg).put("displayValue", leg == 1 ? "1st Leg" : "2nd Leg"));
        }
        if (series != null) competition.put("series", series);
        return new JSONObject().put("id", id).put("date", date)
            .put("season", new JSONObject().put("year", season).put("slug", slug))
            .put("competitions", new JSONArray().put(competition));
    }

    @Test public void twoLegSemifinalsUseProviderAggregateAndFutureZeroStaysUnknown() throws Exception {
        Competition efl = Competition.find("efl");
        JSONObject league = league(2025, "2025-26 English Carabao Cup", "Semifinals", "Final");
        JSONObject firstLeg = event(2025, "semifinals", "leg-1", "2026-01-13T20:00Z", "post", true,
            "STATUS_FULL_TIME", "FT", "Carabao Cup, Semifinals", "1st Leg", 1, null,
            side("361", "Newcastle United", "0", false, "home"),
            side("382", "Manchester City", "2", true, "away"));
        JSONObject secondLeg = event(2025, "semifinals", "leg-2", "2026-02-03T20:00Z", "post", true,
            "STATUS_FULL_TIME", "FT", "Carabao Cup, Semifinals",
            "2nd Leg - Arsenal advance 4-2 on aggregate", 2,
            series("359", 4, true, "363", 2, false),
            side("359", "Arsenal", "1", true, "home"),
            side("363", "Chelsea", "0", false, "away"));
        JSONObject finalMatch = event(2025, "final", "final", "2026-03-22T16:30Z", "pre", false,
            "STATUS_SCHEDULED", "Scheduled", "Carabao Cup, Final", "", 0, null,
            side("359", "Arsenal", "0", false, "home"),
            side("382", "Manchester City", "0", false, "away"));
        JSONObject stale = event(2024, "final", "old-final", "2025-03-16T16:30Z", "post", true,
            "STATUS_FULL_TIME", "FT", "Carabao Cup, Final", "", 0, null,
            side("1", "Old Home", "1", true, "home"),
            side("2", "Old Away", "0", false, "away"));

        Standings standings = CupStandings.parse(efl, root(league, finalMatch, secondLeg, stale, firstLeg), null,
            Match.parseTime("2026-02-05T00:00:00Z"));

        assertEquals("ESPN", standings.source());
        assertEquals("2025-26", standings.season());
        assertTrue(standings.sections().isEmpty());
        assertEquals(2, standings.rounds().size());
        assertEquals("半决赛", standings.rounds().get(0).label());
        assertTrue(standings.rounds().get(0).started());
        assertEquals(2, standings.rounds().get(0).ties().size());

        Standings.Tie first = standings.rounds().get(0).ties().get(0);
        assertEquals("Jan 14 04:00", first.date());
        assertEquals("首回合", first.stage());
        assertEquals("", first.result());
        assertFalse(first.first().winner());
        assertTrue(first.second().winner());

        Standings.Tie second = standings.rounds().get(0).ties().get(1);
        assertEquals("Feb 4 04:00", second.date());
        assertEquals("次回合", second.stage());
        assertEquals("Arsenal 总比分 4–2 晋级", second.result());
        assertTrue(second.first().winner());
        assertFalse(second.second().winner());

        Standings.Round finalRound = standings.rounds().get(1);
        assertEquals("决赛", finalRound.label());
        assertFalse(finalRound.started());
        assertEquals("", finalRound.ties().get(0).first().score());
        assertEquals("", finalRound.ties().get(0).second().score());
        assertFalse(finalRound.ties().get(0).first().winner());
        assertFalse(finalRound.ties().get(0).second().winner());
        assertEquals("待开赛", finalRound.ties().get(0).result());
    }

    @Test public void shootoutResultKeepsProviderPenaltyScoreAndUnplayedRoundStaysUnstarted() throws Exception {
        Competition fa = Competition.find("fa");
        JSONObject league = league(2026, "2026-27 English FA Cup", "First Round", "Second Round");
        JSONObject home = side("356", "Altrincham", "2", false, "home").put("shootoutScore", 2);
        JSONObject away = side("399", "Notts County", "2", true, "away").put("shootoutScore", 3);
        JSONObject penalties = event(2026, "first-round", "fa-1", "2026-11-07T17:30Z", "post", true,
            "STATUS_FINAL_PEN", "FT-Pens", "English FA Cup, First Round",
            "Notts County win 3-2 on penalties", 0, null, home, away);
        JSONObject nextRound = event(2026, "second-round", "fa-2", "2026-12-04T19:45Z", "pre", false,
            "STATUS_SCHEDULED", "Scheduled", "English FA Cup, Second Round", "", 0, null,
            side("359", "Arsenal", "0", false, "home"),
            side("360", "Liverpool", "0", false, "away"));

        Standings standings = CupStandings.parse(fa, root(league, nextRound, penalties), null,
            Match.parseTime("2027-01-01T00:00:00Z"));

        assertEquals("2026-27", standings.season());
        assertEquals(List.of("第一轮", "第二轮"), standings.rounds().stream().map(Standings.Round::label).toList());
        Standings.Round first = standings.rounds().get(0);
        assertTrue(first.started());
        assertEquals("Nov 8 01:30", first.ties().get(0).date());
        assertEquals("点球 2–3", first.ties().get(0).result());
        assertFalse(first.ties().get(0).first().winner());
        assertTrue(first.ties().get(0).second().winner());

        Standings.Round second = standings.rounds().get(1);
        assertFalse(second.started());
        assertEquals("", second.ties().get(0).first().score());
        assertEquals("", second.ties().get(0).second().score());
        assertEquals("待开赛", second.ties().get(0).result());
    }

    @Test public void nonFinalStatesAndPastScheduledMatchesNeverBecomeResults() throws Exception {
        Competition fa = Competition.find("fa");
        JSONObject league = league(2026, "2026-27 English FA Cup", "Third Round");
        JSONObject canceled = event(2026, "third-round", "canceled", "2026-01-03T15:00Z", "post", true,
            "STATUS_CANCELED", "Canceled", "English FA Cup, Third Round", "", 0, null,
            side("1", "Canceled Home", "0", true, "home"),
            side("2", "Canceled Away", "0", false, "away"));
        JSONObject postponed = event(2026, "third-round", "postponed", "2026-01-04T15:00Z", "post", true,
            "STATUS_POSTPONED", "Postponed", "English FA Cup, Third Round", "", 0, null,
            side("3", "Postponed Home", "0", true, "home"),
            side("4", "Postponed Away", "0", false, "away"));
        JSONObject abandoned = event(2026, "third-round", "abandoned", "2026-01-05T15:00Z", "post", true,
            "STATUS_ABANDONED", "Abandoned", "English FA Cup, Third Round", "", 0, null,
            side("5", "Abandoned Home", "1", true, "home"),
            side("6", "Abandoned Away", "0", false, "away"));
        JSONObject pastScheduled = event(2026, "third-round", "scheduled", "2026-01-06T15:00Z", "pre", false,
            "STATUS_SCHEDULED", "Scheduled", "English FA Cup, Third Round", "", 0, null,
            side("7", "Scheduled Home", "0", false, "home"),
            side("8", "Scheduled Away", "0", false, "away"));

        Standings standings = CupStandings.parse(fa, root(league, canceled, postponed, abandoned, pastScheduled), null,
            Match.parseTime("2026-11-23T00:00:00Z"));

        Standings.Round round = standings.rounds().get(0);
        assertFalse(round.started());
        assertEquals(List.of("取消", "延期", "中止", "待开赛"),
            round.ties().stream().map(Standings.Tie::result).toList());
        for (Standings.Tie tie : round.ties()) {
            assertEquals("", tie.first().score());
            assertEquals("", tie.second().score());
            assertFalse(tie.first().winner());
            assertFalse(tie.second().winner());
        }
    }
}
