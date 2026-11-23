package cn.myscore.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LeagueStandingsTest {
    private static JSONObject stat(String name, String display) throws Exception {
        return new JSONObject().put("name", name).put("displayValue", display);
    }

    private static JSONObject stat(String name, String display, double value) throws Exception {
        return stat(name, display).put("value", value);
    }

    private static JSONObject team(String id, String name, String logo) throws Exception {
        JSONArray logos = new JSONArray();
        if (!logo.isEmpty()) logos.put(new JSONObject().put("href", logo));
        return new JSONObject().put("id", id).put("displayName", name).put("logos", logos);
    }

    private static JSONObject entry(JSONObject team, JSONObject... stats) throws Exception {
        JSONObject entry = new JSONObject().put("team", team).put("stats", new JSONArray());
        for (JSONObject stat : stats) entry.getJSONArray("stats").put(stat);
        return entry;
    }

    private static JSONObject standings(String seasonYears, JSONObject... entries) throws Exception {
        JSONArray rows = new JSONArray();
        for (JSONObject entry : entries) rows.put(entry);
        return new JSONObject()
            .put("seasonDisplayName", seasonYears)
            .put("entries", rows);
    }

    private static JSONObject child(String name, String abbreviation, JSONObject standings) throws Exception {
        return new JSONObject().put("name", name).put("abbreviation", abbreviation).put("standings", standings);
    }

    private static JSONObject root(String seasonYears, JSONObject... children) throws Exception {
        JSONArray groups = new JSONArray();
        for (JSONObject child : children) groups.put(child);
        return new JSONObject()
            .put("season", new JSONObject().put("displayName", seasonYears))
            .put("seasons", new JSONArray().put(new JSONObject().put("seasonYears", seasonYears)))
            .put("children", groups);
    }

    private static Competition competition(String id) {
        return Competition.find(id);
    }

    @Test public void usesVerifiedEspnEndpoints() throws Exception {
        assertEquals("https://site.api.espn.com/apis/v2/sports/soccer/eng.1/standings",
            LeagueStandings.endpoint(competition("epl")));
        assertEquals("https://site.api.espn.com/apis/v2/sports/soccer/uefa.champions/standings",
            LeagueStandings.endpoint(competition("ucl")));
        assertEquals("https://site.api.espn.com/apis/v2/sports/basketball/nba/standings",
            LeagueStandings.endpoint(competition("nba")));
        assertEquals("https://site.api.espn.com/apis/v2/sports/football/nfl/standings?level=3",
            LeagueStandings.endpoint(competition("nfl")));
    }

    @Test public void eplUsesSingleCompactTableAndSourcePoints() throws Exception {
        JSONObject city = entry(team("382", "Manchester City", "https://a.espncdn.com/city.png"),
            stat("rank", "2"), stat("gamesPlayed", "11"), stat("wins", "7"), stat("ties", "1"),
            stat("losses", "3"), stat("pointDifferential", "+8"), stat("points", "22"),
            stat("deductions", "", 0)).put("note", new JSONObject().put("description", "Champions League"));
        JSONObject arsenal = entry(team("359", "Arsenal", "https://a.espncdn.com/arsenal.png"),
            stat("rank", "1"), stat("gamesPlayed", "11"), stat("wins", "9"), stat("ties", "1"),
            stat("losses", "1"), stat("pointDifferential", "+10"), stat("points", "22"),
            stat("deductions", "6", 6)).put("note", new JSONObject().put("description", "Champions League"));
        JSONObject palace = entry(team("384", "Crystal Palace", "https://a.espncdn.com/cry.png"),
            stat("rank", "20"), stat("gamesPlayed", "11"), stat("wins", "1"), stat("ties", "4"),
            stat("losses", "6"), stat("pointDifferential", "-12"), stat("points", "7"),
            stat("deductions", "", 0)).put("note", new JSONObject().put("description", "Relegation"));
        String json = root("2026-27", child("2026-27 English Premier League", "2026-2027",
            standings("2026-27 English Premier League", city, arsenal, palace))).toString();

        Standings result = LeagueStandings.parse(competition("epl"), json);

        assertEquals("ESPN", result.source());
        assertEquals("2026-27", result.season());
        assertTrue(result.rounds().isEmpty());
        assertEquals(1, result.sections().size());
        Standings.Section section = result.sections().get(0);
        assertEquals("积分榜", section.title());
        assertEquals(List.of("场", "胜", "平", "负", "净胜", "积分", "扣分"), section.columns());
        assertEquals("阿森纳", section.rows().get(0).name());
        assertEquals("1", section.rows().get(0).rank());
        assertEquals(List.of("11", "9", "1", "1", "+10", "22", "6"), section.rows().get(0).values());
        assertEquals("https://a.espncdn.com/arsenal.png", section.rows().get(0).logo());
        assertEquals("欧冠区", section.rows().get(0).status());
        assertEquals("曼城", section.rows().get(1).name());
        assertEquals(List.of("11", "7", "1", "3", "+8", "22", "0"), section.rows().get(1).values());
        assertEquals("水晶宫", section.rows().get(2).name());
        assertEquals("降级区", section.rows().get(2).status());
    }

    @Test public void uclLeaguePhaseUsesNeutralSourceProvenStatuses() throws Exception {
        JSONObject first = entry(team("359", "Arsenal", "https://a.espncdn.com/arsenal.png"),
            stat("rank", "1"), stat("gamesPlayed", "4"), stat("wins", "3"), stat("ties", "1"),
            stat("losses", "0"), stat("pointDifferential", "+5"), stat("points", "10"),
            stat("deductions", "", 0)).put("note", new JSONObject().put("description", "Qualifies for round of 16"));
        JSONObject ninth = entry(team("999", "Playoff Club", ""),
            stat("rank", "9"), stat("gamesPlayed", "4"), stat("wins", "2"), stat("ties", "1"),
            stat("losses", "1"), stat("pointDifferential", "+2"), stat("points", "7"),
            stat("deductions", "", 0)).put("note", new JSONObject().put("description", "Knockout phase playoffs - seeded"));
        JSONObject seventeenth = entry(team("997", "Unseeded Club", ""),
            stat("rank", "17"), stat("gamesPlayed", "4"), stat("wins", "1"), stat("ties", "3"),
            stat("losses", "0"), stat("pointDifferential", "+1"), stat("points", "6"),
            stat("deductions", "", 0)).put("note", new JSONObject().put("description", "Knockout phase playoffs - unseeded"));
        JSONObject last = entry(team("998", "Eliminated Club", ""),
            stat("rank", "36"), stat("gamesPlayed", "4"), stat("wins", "0"), stat("ties", "1"),
            stat("losses", "3"), stat("pointDifferential", "-8"), stat("points", "1"),
            stat("deductions", "", 0)).put("note", new JSONObject().put("description", "Eliminated"));
        String json = root("2026-27", child("League Phase", "2026-27",
            standings("2026-27 UEFA Champions League", first, ninth, seventeenth, last))).toString();

        Standings result = LeagueStandings.parse(competition("ucl"), json);

        assertEquals("2026-27", result.season());
        assertEquals(1, result.sections().size());
        Standings.Section section = result.sections().get(0);
        assertEquals("联赛阶段", section.title());
        assertEquals("前八区", section.rows().get(0).status());
        assertEquals("附加赛种子区", section.rows().get(1).status());
        assertEquals("附加赛非种子区", section.rows().get(2).status());
        assertEquals("淘汰区", section.rows().get(3).status());
        assertEquals("阿森纳", section.rows().get(0).name());
        assertFalse(section.columns().contains("扣分"));
    }

    @Test public void nbaSplitsConferencesAndSortsBySourceSeed() throws Exception {
        JSONObject eastSecond = entry(team("17", "Brooklyn Nets", "https://a.espncdn.com/bkn.png"),
            stat("playoffSeed", "14"), stat("wins", "3"), stat("losses", "12"),
            stat("winPercent", ".200"), stat("gamesBehind", "10"));
        JSONObject eastFirst = entry(team("20", "Philadelphia 76ers", "https://a.espncdn.com/phi.png"),
            stat("playoffSeed", "1"), stat("wins", "14"), stat("losses", "3"),
            stat("winPercent", ".824"), stat("gamesBehind", "-"));
        JSONObject westFirst = entry(team("25", "San Antonio Spurs", "https://a.espncdn.com/sas.png"),
            stat("playoffSeed", "1"), stat("wins", "14"), stat("losses", "3"),
            stat("winPercent", ".824"), stat("gamesBehind", "-"));
        String json = root("2026-27",
            child("Eastern Conference", "East", standings("2026-27", eastSecond, eastFirst)),
            child("Western Conference", "West", standings("2026-27", westFirst))).toString();

        Standings result = LeagueStandings.parse(competition("nba"), json);

        assertEquals(2, result.sections().size());
        assertEquals("东部", result.sections().get(0).title());
        assertEquals("西部", result.sections().get(1).title());
        Standings.Section east = result.sections().get(0);
        assertEquals(List.of("胜", "负", "胜率", "胜差"), east.columns());
        assertEquals("76人", east.rows().get(0).name());
        assertEquals("1", east.rows().get(0).rank());
        assertEquals(List.of("14", "3", ".824", "-"), east.rows().get(0).values());
        assertEquals("篮网", east.rows().get(1).name());
        assertEquals("14", east.rows().get(1).rank());
    }

    @Test public void nflUsesConferenceDivisionSectionsAndSourceSeeds() throws Exception {
        JSONObject afcSecond = entry(team("4", "Cleveland Browns", "https://a.espncdn.com/cle.png"),
            stat("playoffSeed", "4"), stat("wins", "7"), stat("losses", "2"), stat("ties", "0"),
            stat("winPercent", ".778"), stat("pointDifferential", "+31"));
        JSONObject afcFirst = entry(team("5", "Cincinnati Bengals", "https://a.espncdn.com/cin.png"),
            stat("playoffSeed", "6"), stat("wins", "6"), stat("losses", "4"), stat("ties", "0"),
            stat("winPercent", ".600"), stat("pointDifferential", "+12"));
        JSONObject nfcFirst = entry(team("19", "New York Giants", "https://a.espncdn.com/nyg.png"),
            stat("playoffSeed", "3"), stat("wins", "5"), stat("losses", "3"), stat("ties", "0"),
            stat("winPercent", ".625"), stat("pointDifferential", "+18"));
        JSONObject afcEast = child("AFC East", "EAST", standings("2026", afcSecond, afcFirst));
        JSONObject nfcEast = child("NFC East", "EAST", standings("2026", nfcFirst));
        JSONObject afc = new JSONObject().put("name", "American Football Conference").put("abbreviation", "AFC")
            .put("children", new JSONArray().put(afcEast));
        JSONObject nfc = new JSONObject().put("name", "National Football Conference").put("abbreviation", "NFC")
            .put("children", new JSONArray().put(nfcEast));
        String json = root("2026", afc, nfc).toString();

        Standings result = LeagueStandings.parse(competition("nfl"), json);

        assertEquals(2, result.sections().size());
        assertEquals("AFC 东区", result.sections().get(0).title());
        assertEquals("NFC 东区", result.sections().get(1).title());
        Standings.Section section = result.sections().get(0);
        assertEquals(List.of("胜", "负", "平", "胜率", "净胜"), section.columns());
        assertEquals("Cleveland Browns", section.rows().get(0).name());
        assertEquals("4", section.rows().get(0).rank());
        assertEquals(List.of("7", "2", "0", ".778", "+31"), section.rows().get(0).values());
        assertEquals("Cincinnati Bengals", section.rows().get(1).name());
    }

    @Test public void emptyAndIncompletePayloadsFailInsteadOfShowingFakeStandings() throws Exception {
        try {
            LeagueStandings.parse(competition("epl"), root("2026-27").toString());
            fail("empty standings must fail");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("分组"));
        }
        try {
            LeagueStandings.parse(competition("ucl"), root("2026-27",
                child("Round of 16", "R16", standings("2026-27",
                    entry(team("1", "Arsenal", ""), stat("rank", "1"))))).toString());
            fail("non-league phase UCL data must not be shown as league phase");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("联赛阶段"));
        }
        try {
            LeagueStandings.parse(competition("nba"), root("2026-27",
                child("Eastern Conference", "East", standings("2026-27",
                    entry(team("20", "Philadelphia 76ers", ""), stat("playoffSeed", "1"))))).toString());
            fail("one NBA conference must not be presented as the complete standings");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("分区"));
        }
    }

    @Test public void invalidProviderSeedsStayBlank() throws Exception {
        JSONObject east = entry(team("20", "Philadelphia 76ers", ""),
            stat("playoffSeed", "0", 0), stat("rank", "0"), stat("wins", "1"), stat("losses", "1"));
        JSONObject west = entry(team("25", "San Antonio Spurs", ""),
            stat("playoffSeed", "-2"), stat("wins", "1"), stat("losses", "1"));
        String json = root("2026-27",
            child("Eastern Conference", "East", standings("2026-27", east)),
            child("Western Conference", "West", standings("2026-27", west))).toString();

        Standings result = LeagueStandings.parse(competition("nba"), json);

        assertEquals("", result.sections().get(0).rows().get(0).rank());
        assertEquals("", result.sections().get(1).rows().get(0).rank());
    }
}
