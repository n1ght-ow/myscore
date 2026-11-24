package cn.myscore.app;

import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class StandingsStore {
    private static final long FRESH_MS = 10 * 60_000L;
    private static final long RETAIN_MS = 30 * 24 * 60 * 60_000L;
    private static final int MAX_BYTES = 1_000_000;
    private static final Object DISK_LOCK = new Object();
    private static final Map<String, Snapshot> HOT = new ConcurrentHashMap<>();
    private final File directory;

    record Snapshot(Standings data, long fetched) {
        boolean fresh(long now) { return usable(now) && now - fetched < FRESH_MS; }
        boolean usable(long now) { return fetched > 0 && now >= fetched && now - fetched < RETAIN_MS; }
    }

    StandingsStore(File cacheDirectory) { directory = new File(cacheDirectory, "standings-v1"); }

    Snapshot hot(String sport, long now) {
        Snapshot result = HOT.get(sport);
        return result != null && result.usable(now) ? result : null;
    }

    Snapshot read(String sport, long now) {
        Snapshot result = hot(sport, now);
        if (result != null) return result;
        synchronized (DISK_LOCK) {
            result = hot(sport, now);
            if (result != null) return result;
            try {
                File path = path(sport);
                if (path.length() > MAX_BYTES) return null;
                byte[] bytes = new AtomicFile(path).readFully();
                if (bytes.length > MAX_BYTES) return null;
                result = decode(sport, new JSONObject(new String(bytes, StandardCharsets.UTF_8)));
                if (!result.usable(now)) return null;
                HOT.put(sport, result);
                return result;
            } catch (Exception unavailable) { return null; }
        }
    }

    void write(String sport, Snapshot snapshot) {
        synchronized (DISK_LOCK) {
            Snapshot previous = HOT.get(sport);
            if (previous != null && previous.fetched() > snapshot.fetched()) return;
            HOT.put(sport, snapshot);
            AtomicFile file = new AtomicFile(path(sport));
            FileOutputStream stream = null;
            try {
                byte[] bytes = encode(sport, snapshot).toString().getBytes(StandardCharsets.UTF_8);
                if (bytes.length > MAX_BYTES) return;
                stream = file.startWrite();
                stream.write(bytes);
                file.finishWrite(stream);
            } catch (Exception unavailable) {
                if (stream != null) file.failWrite(stream);
            }
        }
    }

    private File path(String sport) { return new File(directory, sport + ".json"); }

    static JSONObject encode(String sport, Snapshot snapshot) throws JSONException {
        Standings data = snapshot.data();
        JSONArray sections = new JSONArray(), rounds = new JSONArray();
        for (Standings.Section section : data.sections()) {
            JSONArray rows = new JSONArray();
            for (Standings.Row row : section.rows()) {
                rows.put(new JSONArray().put(row.rank()).put(row.name()).put(row.subtitle()).put(row.logo())
                    .put(new JSONArray(row.values())).put(row.status()).put(row.teamId()));
            }
            sections.put(new JSONArray().put(section.title()).put(new JSONArray(section.columns())).put(rows));
        }
        for (Standings.Round round : data.rounds()) {
            JSONArray ties = new JSONArray();
            for (Standings.Tie tie : round.ties()) {
                ties.put(new JSONArray().put(tie.date()).put(tie.stage()).put(encode(tie.first())).put(encode(tie.second())).put(tie.result()));
            }
            rounds.put(new JSONArray().put(round.label()).put(ties).put(round.started()));
        }
        return new JSONObject().put("sport", sport).put("fetched", snapshot.fetched()).put("source", data.source())
            .put("season", data.season()).put("sections", sections).put("rounds", rounds);
    }

    static Snapshot decode(String sport, JSONObject root) throws JSONException {
        if (!sport.equals(root.getString("sport"))) throw new JSONException("Standings cache identity mismatch");
        List<Standings.Section> sections = new ArrayList<>();
        JSONArray sectionData = root.getJSONArray("sections");
        for (int index = 0; index < sectionData.length(); index++) {
            JSONArray section = sectionData.getJSONArray(index), rowData = section.getJSONArray(2);
            List<Standings.Row> rows = new ArrayList<>();
            for (int item = 0; item < rowData.length(); item++) {
                JSONArray row = rowData.getJSONArray(item);
                rows.add(new Standings.Row(row.getString(0), row.getString(1), row.getString(2), row.getString(3),
                    strings(row.getJSONArray(4)), row.getString(5), row.length() > 6 ? row.optString(6) : ""));
            }
            sections.add(new Standings.Section(section.getString(0), strings(section.getJSONArray(1)), rows));
        }
        List<Standings.Round> rounds = new ArrayList<>();
        JSONArray roundData = root.getJSONArray("rounds");
        for (int index = 0; index < roundData.length(); index++) {
            JSONArray round = roundData.getJSONArray(index), tieData = round.getJSONArray(1);
            List<Standings.Tie> ties = new ArrayList<>();
            for (int item = 0; item < tieData.length(); item++) {
                JSONArray tie = tieData.getJSONArray(item);
                ties.add(new Standings.Tie(tie.getString(0), tie.getString(1), side(tie.getJSONArray(2)), side(tie.getJSONArray(3)), tie.getString(4)));
            }
            rounds.add(new Standings.Round(round.getString(0), ties, round.getBoolean(2)));
        }
        return new Snapshot(new Standings(root.getString("source"), root.getString("season"), sections, rounds), root.getLong("fetched"));
    }

    private static JSONArray encode(Standings.Side side) {
        return new JSONArray().put(side.name()).put(side.logo()).put(side.score()).put(side.winner());
    }

    private static Standings.Side side(JSONArray side) throws JSONException {
        return new Standings.Side(side.getString(0), side.getString(1), side.getString(2), side.getBoolean(3));
    }

    private static List<String> strings(JSONArray array) throws JSONException {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) values.add(array.getString(index));
        return values;
    }
}
