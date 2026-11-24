package cn.myscore.app;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class StandingsStoreTest {
    private static final long FETCHED = 1_795_000_000_000L;
    private static final Standings.Section SECTION = new Standings.Section("Drivers", List.of("Pts"),
        List.of(new Standings.Row("1", "Driver", "Team", "", List.of("0"), "")));
    private static final Standings.Round ROUND = new Standings.Round("Semi-finals", List.of(
        new Standings.Tie("Nov 23", "Second leg", new Standings.Side("Home", "https://a.espncdn.com/logo.png", "1", true),
            new Standings.Side("Away", "", "1", false), "Pens 5–4")), true);

    @Test public void snapshotPreservesSourceTimeValuesAndCupResults() throws Exception {
        StandingsStore.Snapshot snapshot = new StandingsStore.Snapshot(new Standings("Provider", "Season", List.of(SECTION), List.of(ROUND)), FETCHED);
        String saved = StandingsStore.encode("fa", snapshot).toString();
        assertEquals(snapshot, StandingsStore.decode("fa", new JSONObject(saved)));
        assertThrows(JSONException.class, () -> StandingsStore.decode("efl", new JSONObject(saved)));
        assertThrows(JSONException.class, () -> StandingsStore.decode("fa", new JSONObject("{\"sport\":\"fa\"}")));
    }

    @Test public void staleSnapshotsRemainVisibleButAreNotConsideredFresh() {
        StandingsStore.Snapshot snapshot = new StandingsStore.Snapshot(new Standings("Provider", "Season", List.of(SECTION), List.of()), FETCHED);
        assertTrue(snapshot.fresh(FETCHED));
        assertFalse(snapshot.fresh(FETCHED + 600_000));
        assertTrue(snapshot.usable(FETCHED + 600_000));
        assertFalse(snapshot.usable(FETCHED - 1));
        assertFalse(snapshot.usable(FETCHED + 30 * 86_400_000L));
    }

    @Test public void teamIdentityRoundTripsWithoutBreakingLegacyCache() throws Exception {
        Standings.Section linked = new Standings.Section("积分榜", List.of("场", "积分"),
            List.of(new Standings.Row("1", "阿森纳", "", "", List.of("11", "28"), "欧冠区", "359")));
        StandingsStore.Snapshot snapshot = new StandingsStore.Snapshot(
            new Standings("ESPN", "2026-27", List.of(linked), List.of()), FETCHED);

        StandingsStore.Snapshot roundTrip = StandingsStore.decode("epl",
            new JSONObject(StandingsStore.encode("epl", snapshot).toString()));
        assertEquals(snapshot, roundTrip);
        assertEquals("359", roundTrip.data().sections().get(0).rows().get(0).teamId());

        JSONObject legacy = new JSONObject()
            .put("sport", "epl")
            .put("fetched", FETCHED)
            .put("source", "ESPN")
            .put("season", "2026-27")
            .put("sections", new JSONArray().put(new JSONArray()
                .put("积分榜")
                .put(new JSONArray().put("场").put("积分"))
                .put(new JSONArray().put(new JSONArray()
                    .put("1").put("阿森纳").put("").put("")
                    .put(new JSONArray().put("11").put("28"))
                    .put("欧冠区")))))
            .put("rounds", new JSONArray());

        StandingsStore.Snapshot decoded = StandingsStore.decode("epl", legacy);
        assertEquals("", decoded.data().sections().get(0).rows().get(0).teamId());
        assertEquals("阿森纳", decoded.data().sections().get(0).rows().get(0).name());
        assertTrue(decoded.usable(FETCHED + 600_000));
    }
}
