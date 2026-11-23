package cn.myscore.app;

import java.util.List;
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
}
