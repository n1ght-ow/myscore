package cn.myscore.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class Cs2StandingsTest {
    private static final LocalDate PUBLISHED = LocalDate.of(2026, 11, 2);
    private static final String HEADER = "| Standing | Points | Team Name | Roster | |";
    private static final String SEPARATOR = "| :- | -: | :- | :- | :- |";
    private static final String FIRST = "| 1 | 1960 | MOUZ | PR, Spinx, torzsi, xelex, xertioN | [details](details/0001.md) |";
    private static final String SECOND = "| 2 | 0 | Zero Points | alpha, beta | |";

    @Test public void latestCommonRequiresAllFourTabsOnOnePastDate() throws Exception {
        JSONArray entries = new JSONArray();
        addRelease(entries, 2026, "2026_10_05");
        addRelease(entries, 2026, "2026_11_02");
        addRelease(entries, 2026, "2026_12_07");
        entries.put(file("standings_global_2026_11_09.md"));
        entries.put(file("standings_europe_2026_11_09.md"));
        entries.put(file("standings_americas_2026_11_09.md"));
        entries.put(tree("standings_asia_2026_11_09.md"));
        entries.put(file("README.md"));
        entries.put(file("standings_global_2025_12_01.md"));

        assertEquals(LocalDate.of(2026, 11, 2),
            Cs2Standings.latestCommon(entries, 2026, LocalDate.of(2026, 11, 23)));
    }

    @Test public void latestCommonHandlesTodayAndYearTurnover() throws Exception {
        JSONArray entries = new JSONArray();
        addRelease(entries, 2027, "2027_01_04");
        addRelease(entries, 2027, "2027_01_11");

        assertEquals(LocalDate.of(2027, 1, 4),
            Cs2Standings.latestCommon(entries, 2027, LocalDate.of(2027, 1, 10)));
        assertEquals(LocalDate.of(2027, 1, 11),
            Cs2Standings.latestCommon(entries, 2027, LocalDate.of(2027, 1, 11)));
        assertNull(Cs2Standings.latestCommon(entries, 2026, LocalDate.of(2027, 1, 11)));
    }

    @Test public void latestCommonReturnsNullWhenNoDateIsComplete() throws Exception {
        JSONArray entries = new JSONArray();
        entries.put(file("standings_global_2026_11_02.md"));
        entries.put(file("standings_europe_2026_11_02.md"));
        entries.put(file("standings_americas_2026_11_02.md"));
        entries.put(file("standings_global_2026_11_09.md"));
        entries.put(file("standings_europe_2026_11_09.md"));
        entries.put(file("standings_americas_2026_11_09.md"));
        entries.put(tree("standings_asia_2026_11_09.md"));

        assertNull(Cs2Standings.latestCommon(entries, 2026, LocalDate.of(2026, 11, 23)));
    }

    @Test public void parsesEveryOfficialRegionHeading() throws Exception {
        for (String scope : List.of("global", "europe", "americas", "asia")) {
            Standings.Section section = Cs2Standings.parse(
                markdown(scope, "2026_11_02", FIRST, SECOND), scope, PUBLISHED);

            assertEquals(title(scope), section.title());
            assertEquals(List.of("积分"), section.columns());
            assertEquals(2, section.rows().size());

            Standings.Row first = section.rows().get(0);
            assertEquals("1", first.rank());
            assertEquals("MOUZ", first.name());
            assertEquals("PR, Spinx, torzsi, xelex, xertioN", first.subtitle());
            assertEquals("", first.logo());
            assertEquals(List.of("1960"), first.values());
            assertEquals("", first.status());

            Standings.Row second = section.rows().get(1);
            assertEquals("2", second.rank());
            assertEquals("Zero Points", second.name());
            assertEquals(List.of("0"), second.values());
        }
    }

    @Test public void rejectsHeadingThatDoesNotMatchScopeOrPublishedDate() {
        rejects(() -> Cs2Standings.parse(markdown("europe", "2026_11_02", FIRST), "global", PUBLISHED));
        rejects(() -> Cs2Standings.parse(markdown("global", "2026_11_09", FIRST), "global", PUBLISHED));
        rejects(() -> Cs2Standings.parse("### Standings as of 02/11/2026<br />\n\n" + table(FIRST),
            "global", PUBLISHED));
    }

    @Test public void rejectsMissingHeaderEmptyTablesAndCorruptResponses() {
        rejects(() -> Cs2Standings.parse("", "global", PUBLISHED));
        rejects(() -> Cs2Standings.parse("<html>rate limited</html>", "global", PUBLISHED));
        rejects(() -> Cs2Standings.parse(heading("global", "2026_11_02") + "\n\n" + table(),
            "global", PUBLISHED));
        rejects(() -> Cs2Standings.parse(markdown("global", "2026_11_02", FIRST)
            .replace(HEADER, "| Points | Standing | Team Name | Roster | |"), "global", PUBLISHED));
    }

    @Test public void rejectsRanksThatAreNotSequentialFromOne() {
        rejects(() -> Cs2Standings.parse(markdown("global", "2026_11_02",
            "| 2 | 1960 | MOUZ | PR, Spinx | |"), "global", PUBLISHED));
        rejects(() -> Cs2Standings.parse(markdown("global", "2026_11_02",
            FIRST, "| 3 | 1900 | Vitality | flameZ, ZywOo | |"), "global", PUBLISHED));
        rejects(() -> Cs2Standings.parse(markdown("global", "2026_11_02",
            FIRST, FIRST), "global", PUBLISHED));
    }

    @Test public void rejectsInvalidPointsButDoesNotInventMissingRoster() throws Exception {
        for (String points : List.of("-1", "1.5", "abc", "")) {
            rejects(() -> Cs2Standings.parse(markdown("global", "2026_11_02",
                "| 1 | " + points + " | MOUZ | PR, Spinx | |"), "global", PUBLISHED));
        }
        Standings.Section section = Cs2Standings.parse(markdown("global", "2026_11_02",
            "| 1 | 1960 | MOUZ |  | |"), "global", PUBLISHED);
        assertEquals("", section.rows().get(0).subtitle());
    }

    private static void addRelease(JSONArray entries, int year, String date) throws Exception {
        for (String scope : List.of("global", "europe", "americas", "asia")) {
            entries.put(file("standings_" + scope + "_" + date + ".md"));
        }
    }

    private static JSONObject file(String name) throws Exception {
        return new JSONObject().put("name", name).put("type", "file");
    }

    private static JSONObject tree(String name) throws Exception {
        return new JSONObject().put("name", name).put("type", "tree");
    }

    private static String markdown(String scope, String date, String... rows) {
        return heading(scope, date) + "\n\n" + table(rows);
    }

    private static String heading(String scope, String date) {
        return scope.equals("global")
            ? "### Standings as of " + date + "<br />"
            : "### Regional Standings for " + english(scope) + " as of " + date + "<br />";
    }

    private static String table(String... rows) {
        StringBuilder markdown = new StringBuilder()
            .append(HEADER).append('\n')
            .append(SEPARATOR).append('\n');
        for (String row : rows) markdown.append(row).append('\n');
        return markdown.toString();
    }

    private static String title(String scope) {
        return switch (scope) {
            case "global" -> "全球";
            case "europe" -> "欧洲";
            case "americas" -> "美洲";
            case "asia" -> "亚洲";
            default -> throw new IllegalArgumentException(scope);
        };
    }

    private static String english(String scope) {
        return switch (scope) {
            case "europe" -> "Europe";
            case "americas" -> "Americas";
            case "asia" -> "Asia";
            default -> throw new IllegalArgumentException(scope);
        };
    }

    @FunctionalInterface
    private interface Attempt {
        void run() throws Exception;
    }

    private static void rejects(Attempt attempt) {
        try {
            attempt.run();
            fail("corrupt or incomplete standings must be rejected");
        } catch (IOException expected) {
        } catch (Exception unexpected) {
            fail("expected IOException, got " + unexpected.getClass().getSimpleName());
        }
    }
}
