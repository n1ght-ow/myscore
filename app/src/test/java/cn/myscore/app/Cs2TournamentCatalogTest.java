package cn.myscore.app;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class Cs2TournamentCatalogTest {
    @Test public void recentEventCanOpenBeforeOlderPagesFinish() throws Exception {
        String recent = page(100, "csgo_tt_1", "2026-11-23", "2026-11-24");
        String older = page(1, "csgo_tt_2", "2026-10-23", "2026-10-24");
        List<Cs2Tournament.Event> early = new ArrayList<>();
        CountDownLatch secondStarted = new CountDownLatch(1), opened = new CountDownLatch(1);
        List<Cs2Tournament.Event> events = Cs2Tournament.catalog(LocalDate.of(2026, 11, 24), url -> {
            if (url.endsWith("page=1")) {
                assertTrue("next page starts without waiting for the first", secondStarted.await(3, TimeUnit.SECONDS));
                return recent;
            }
            if (url.endsWith("page=2")) {
                secondStarted.countDown();
                if (!opened.await(3, TimeUnit.SECONDS)) throw new AssertionError("Recent catalog was not delivered early");
            }
            return older;
        }, first -> {
            early.addAll(first);
            opened.countDown();
        });
        assertEquals(List.of(events.get(0)), early);
        assertEquals(List.of("csgo_tt_1", "csgo_tt_2"), events.stream().map(Cs2Tournament.Event::id).collect(java.util.stream.Collectors.toList()));
    }

    @Test public void historicalDateWaitsForRelevantCatalogPage() throws Exception {
        String recent = page(100, "csgo_tt_1", "2026-11-23", "2026-11-24");
        String older = page(1, "csgo_tt_2", "2026-10-23", "2026-10-24");
        List<Cs2Tournament.Event> events = Cs2Tournament.catalog(LocalDate.of(2026, 10, 24),
            url -> url.endsWith("page=1") ? recent : older,
            ignored -> { throw new AssertionError("Should not open a recent tournament for a historical date"); });
        assertEquals("csgo_tt_2", events.get(0).id());
    }

    private static String page(int count, String id, String start, String end) throws Exception {
        JSONObject event = new JSONObject().put("id", id).put("disp_name", id).put("start_time", start).put("end_time", end);
        JSONArray rows = new JSONArray();
        for (int index = 0; index < count; index++) rows.put(new JSONObject().put("tt_info", event));
        return new JSONObject().put("success", true).put("data", new JSONObject().put("matches", rows)).toString();
    }
}
