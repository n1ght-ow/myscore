package cn.myscore.app;

import org.json.JSONObject;
import org.junit.Test;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class F1StandingsTest {
    private static final String DRIVERS = """
        {
          "MRData": {
            "StandingsTable": {
              "season": "2031",
              "StandingsLists": [{
                "season": "2031",
                "round": "8",
                "DriverStandings": [
                  {
                    "position": "1",
                    "positionText": "1",
                    "points": "44",
                    "wins": "2",
                    "Driver": {"givenName": "Ada", "familyName": "North"},
                    "Constructors": [{"name": "Comet Racing"}]
                  },
                  {
                    "position": "2",
                    "positionText": "2",
                    "points": "41",
                    "wins": "1",
                    "Driver": {"givenName": "Rui", "familyName": "Chen"},
                    "Constructors": [{"name": "Atlas Motors"}, {"name": "Comet Racing"}]
                  },
                  {
                    "position": "3",
                    "positionText": "3",
                    "points": "41",
                    "wins": "0",
                    "Driver": {"givenName": "Mina", "familyName": "Sol"},
                    "Constructors": [{"name": "Atlas Motors"}]
                  },
                  {
                    "position": "4",
                    "positionText": "4",
                    "points": "",
                    "wins": "0",
                    "Driver": {"givenName": "Missing", "familyName": "Points"},
                    "Constructors": [{"name": "Comet Racing"}]
                  }
                ]
              }]
            }
          }
        }
        """;

    private static final String CONSTRUCTORS = """
        {
          "MRData": {
            "StandingsTable": {
              "season": "2031",
              "StandingsLists": [{
                "season": "2031",
                "round": "8",
                "ConstructorStandings": [
                  {
                    "position": "1",
                    "positionText": "1",
                    "points": "85",
                    "wins": "3",
                    "Constructor": {"name": "Comet Racing"}
                  },
                  {
                    "position": "2",
                    "positionText": "2",
                    "points": "80",
                    "wins": "2",
                    "Constructor": {"name": "Atlas Motors"}
                  }
                ]
              }]
            }
          }
        }
        """;

    @Test public void bothTablesKeepProviderOrderAndVerifiedValues() throws Exception {
        Standings standings = F1Standings.parse(new JSONObject(DRIVERS), new JSONObject(CONSTRUCTORS));

        assertEquals("Jolpica F1", standings.source());
        assertEquals("2031", standings.season());
        assertTrue(standings.rounds().isEmpty());
        List<Standings.Section> sections = standings.sections();
        assertEquals(2, sections.size());

        Standings.Section drivers = sections.get(0);
        assertEquals("车手", drivers.title());
        assertEquals(List.of("积分", "胜场"), drivers.columns());
        List<Standings.Row> driverRows = drivers.rows();
        assertEquals(3, driverRows.size());
        assertEquals("1", driverRows.get(0).rank());
        assertEquals("Ada North", driverRows.get(0).name());
        assertEquals("Comet Racing", driverRows.get(0).subtitle());
        assertEquals(List.of("44", "2"), driverRows.get(0).values());
        assertEquals(List.of("41", "1"), driverRows.get(1).values());
        assertEquals("Atlas Motors / Comet Racing", driverRows.get(1).subtitle());
        assertEquals("Mina Sol", driverRows.get(2).name());
        assertEquals(List.of("41", "0"), driverRows.get(2).values());
        assertEquals("", driverRows.get(2).logo());

        Standings.Section constructors = sections.get(1);
        assertEquals("车队", constructors.title());
        assertEquals(List.of("积分", "胜场"), constructors.columns());
        List<Standings.Row> teamRows = constructors.rows();
        assertEquals("Comet Racing", teamRows.get(0).name());
        assertEquals(List.of("85", "3"), teamRows.get(0).values());
        assertEquals("Atlas Motors", teamRows.get(1).name());
        assertEquals("", teamRows.get(1).subtitle());
    }

    @Test public void missingConstructorTableReturnsOnlyDrivers() throws Exception {
        Standings standings = F1Standings.parse(new JSONObject(DRIVERS), null);

        List<Standings.Section> sections = standings.sections();
        assertEquals(1, sections.size());
        assertEquals("车手", sections.get(0).title());
        assertEquals("Jolpica F1", standings.source());
    }

    @Test public void missingDriverTableReturnsOnlyConstructors() throws Exception {
        Standings standings = F1Standings.parse(null, new JSONObject(CONSTRUCTORS));

        List<Standings.Section> sections = standings.sections();
        assertEquals(1, sections.size());
        assertEquals("车队", sections.get(0).title());
        assertEquals("Jolpica F1", standings.source());
    }

    @Test public void mismatchedTableSeasonsAreNeverMerged() throws Exception {
        JSONObject constructors = new JSONObject(CONSTRUCTORS.replace("\"2031\"", "\"2032\""));

        try {
            F1Standings.parse(new JSONObject(DRIVERS), constructors);
            fail("different source seasons must not be merged");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("赛季"));
        }
    }

    @Test public void tableWithoutAConfirmedSeasonIsNotMerged() throws Exception {
        JSONObject constructors = new JSONObject(CONSTRUCTORS);
        JSONObject table = constructors.getJSONObject("MRData").getJSONObject("StandingsTable");
        table.remove("season");
        table.getJSONArray("StandingsLists").getJSONObject(0).remove("season");

        Standings standings = F1Standings.parse(new JSONObject(DRIVERS), constructors);

        assertEquals(1, standings.sections().size());
        assertEquals("车手", standings.sections().get(0).title());
        assertEquals("2031", standings.season());
        assertEquals("Jolpica F1", standings.source());
    }

    @Test public void interruptedFetchStopsBeforeTheSecondTable() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        F1Standings.Fetcher fetcher = url -> {
            requests.incrementAndGet();
            throw new InterruptedIOException("cancelled");
        };

        try {
            F1Standings.load(fetcher);
            fail("interrupt must be rethrown");
        } catch (InterruptedIOException expected) {
            assertEquals("cancelled", expected.getMessage());
        } finally {
            assertTrue(Thread.interrupted());
        }
        assertEquals(1, requests.get());
    }

    @Test public void socketTimeoutDoesNotSetTheCallerInterruptFlag() throws Exception {
        F1Standings.Fetcher fetcher = url -> {
            if (url.contains("driverstandings")) throw new SocketTimeoutException("slow");
            return CONSTRUCTORS;
        };

        Standings standings = F1Standings.load(fetcher);

        assertEquals(1, standings.sections().size());
        assertEquals("车队", standings.sections().get(0).title());
        assertFalse(Thread.interrupted());
    }
}
