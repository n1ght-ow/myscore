package cn.myscore.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Resolves the watch destination for a match without blocking the list or detail UI.
 *
 * <p>Static destinations (Douyu / Huya / Migu league zones) are always available immediately.
 * This repository upgrades them in the background: Bilibili full-series recordings for finished
 * Worlds, CS2 tier-one events and Spirit matches, and exact Migu match pages when both Chinese team names and kickoff time agree.
 * A finished CS2 match outside tier-one events and without Spirit keeps no entry rather than an unrelated live room.
 * Everything is cached and every network failure falls back to the previous safe destination.</p>
 */
final class WatchRepository {
    private static final String TAG = "MyScoreWatch";
    private static final long BILI_TTL = 15L * 60_000L;
    private static final long BILI_FAIL = 5L * 60_000L;
    private static final long MIGU_FOUND_TTL = 12L * 60 * 60_000L;
    private static final long MIGU_FAIL = 10L * 60_000L;
    private static final long PACK_TTL = 24L * 60 * 60_000L;
    private static final String DEFAULT_PACK = "1002581,1002601,1003861,1003862,1003863,1003864,"
        + "1003865,1003866,1004041,1004121,1004261,1004262,1004281,1004321,1004262,1004281,"
        + "1004322,1004261,1004421,1004422,1002781,1004301,1004641,1004761,1005061,1005261,"
        + "1005301,1005321,1005361,1005362,1005341,1005342,1005521,1005722,1005721,1015749,"
        + "1015761,1015760,1015762,1015763,1015768,1015786,1015790,1015812,1015813,1015814,"
        + "1015815,1015816,1015817,1015820,1015819,1015821,1015837,1015836";
    private static final String UA = "Mozilla/5.0 (Linux; Android 14; 23127PN0CC) "
        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36";
    private static volatile WatchRepository instance;

    private record BiliState(long fetched, List<BilibiliVods.Episode> episodes) {}

    private final Context context;
    private final File dir;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newFixedThreadPool(2);
    private final Map<String, MiguMatches.Hit> miguHits = new ConcurrentHashMap<>();
    private final Map<String, Long> miguChecked = new ConcurrentHashMap<>();
    private final Map<String, Long> miguFailed = new ConcurrentHashMap<>();
    private final Map<String, List<Runnable>> miguWaiters = new LinkedHashMap<>();

    private final Object biliLock = new Object();
    private final Map<BilibiliVods.Source, List<Runnable>> biliWaiters = new LinkedHashMap<>();
    private final Map<BilibiliVods.Source, BiliState> bili = new ConcurrentHashMap<>();
    private final Map<BilibiliVods.Source, Long> biliFailedUntil = new ConcurrentHashMap<>();

    private volatile String packId;
    private volatile long packFetched;

    private WatchRepository(Context context) {
        this.context = context.getApplicationContext();
        this.dir = new File(this.context.getCacheDir(), "watch-v1");
        if (!dir.exists()) dir.mkdirs();
        for (BilibiliVods.Source source : BilibiliVods.Source.values()) loadBili(source);
        loadMigu();
    }

    static WatchRepository get(Context context) {
        WatchRepository local = instance;
        if (local == null) {
            synchronized (WatchRepository.class) {
                local = instance;
                if (local == null) instance = local = new WatchRepository(context);
            }
        }
        return local;
    }

    /** Always safe to call on the UI thread. Returns the best cached destination. */
    WatchLinks.Link link(Match match) {
        if (match == null) return null;
        BilibiliVods.Source source = BilibiliVods.source(match);
        if (source != null) {
            BiliState state = bili.get(source);
            BilibiliVods.Episode episode = state == null ? null : BilibiliVods.find(state.episodes, match);
            return episode == null ? WatchLinks.biliPending(match) : WatchLinks.biliVideo(episode.bvid(), match);
        }
        if ("epl".equals(match.league) || "nba".equals(match.league)) {
            MiguMatches.Hit hit = miguHits.get(key(match));
            if (hit == null) return WatchLinks.of(match);
            String homeCn = MiguTeams.chinese(match.league, match.home);
            String awayCn = MiguTeams.chinese(match.league, match.away);
            return WatchLinks.miguMatch(hit.pID(), match.league,
                homeCn == null ? hit.left() : homeCn,
                awayCn == null ? hit.right() : awayCn,
                match.finalised());
        }
        return WatchLinks.of(match);
    }

    /** Stable fingerprint for the tablet reading-pane cache key. */
    String signature(Match match) {
        WatchLinks.Link link = link(match);
        return link == null ? "" : link.url + "|" + link.title + "|" + link.note;
    }

    /** Each recording source is shared by all its finished matches. */
    void prepare(List<Match> matches, Runnable changed) {
        if (matches == null) return;
        java.util.Set<BilibiliVods.Source> sources = new java.util.HashSet<>();
        for (Match match : matches) {
            BilibiliVods.Source source = BilibiliVods.source(match);
            if (source != null) sources.add(source);
        }
        for (BilibiliVods.Source source : sources) ensureBili(source, changed);
    }

    void resolve(Match match, Runnable changed) {
        if (match == null) return;
        BilibiliVods.Source source = BilibiliVods.source(match);
        if (source != null) {
            ensureBili(source, changed);
        } else if ("epl".equals(match.league) || "nba".equals(match.league)) {
            resolveMigu(match, changed, false);
        }
    }

    /** A play tap must wait for the fixture lookup, including an already running lookup. */
    void resolveForOpen(Match match, Consumer<WatchLinks.Link> ready) {
        WatchLinks.Link cached = link(match);
        if (cached == null || cached.kind != WatchLinks.Kind.ZONE) {
            main.post(() -> ready.accept(cached));
        } else {
            resolveMigu(match, () -> ready.accept(link(match)), true);
        }
    }

    private void ensureBili(BilibiliVods.Source source, Runnable changed) {
        long now = System.currentTimeMillis();
        BiliState state = bili.get(source);
        if (state != null && now - state.fetched() < BILI_TTL) return;
        synchronized (biliLock) {
            state = bili.get(source);
            if (state != null && now - state.fetched() < BILI_TTL) return;
            if (biliFailedUntil.getOrDefault(source, 0L) > now) return;
            List<Runnable> waiters = biliWaiters.get(source);
            if (waiters != null) {
                if (changed != null) waiters.add(changed);
                return;
            }
            waiters = new ArrayList<>();
            if (changed != null) waiters.add(changed);
            biliWaiters.put(source, waiters);
        }
        worker.execute(() -> {
            List<BilibiliVods.Episode> episodes = null;
            boolean complete = false;
            Log.i(TAG, "Bilibili fetch start " + source);
            try {
                Map<String, String> headers = new LinkedHashMap<>();
                headers.put("Referer", source.space());
                headers.put("User-Agent", source != BilibiliVods.Source.WORLDS ? "Mozilla/5.0" : UA);
                List<BilibiliVods.Episode> fetched = new ArrayList<>();
                for (int page = 1; page <= 20; page++) {
                    JSONObject root = new JSONObject(httpGet(source.api(page), headers, source != BilibiliVods.Source.WORLDS));
                    if (root.optInt("code", -1) != 0) throw new IOException("Bilibili code " + root.optInt("code"));
                    fetched.addAll(BilibiliVods.parse(root, source));
                    if (!fetched.isEmpty()) episodes = fetched;
                    if (source == BilibiliVods.Source.WORLDS) break;
                    JSONObject data = root.optJSONObject("data");
                    JSONObject pagination = data == null ? null : data.optJSONObject("page");
                    if (pagination == null || pagination.optInt("num") != page || pagination.optInt("size") <= 0)
                        throw new IOException("Bilibili pagination missing");
                    if ((long) page * pagination.optInt("size") >= pagination.optInt("total")) break;
                    if (page == 20) throw new IOException("Bilibili pagination limit");
                }
                if (fetched.isEmpty()) throw new IOException("Bilibili recordings empty");
                episodes = fetched;
                complete = true;
                Log.i(TAG, "Bilibili fetch episodes=" + episodes.size());
            } catch (Exception e) {
                Log.i(TAG, "Bilibili series unavailable: " + e.getMessage());
            }
            List<Runnable> waiters;
            synchronized (biliLock) {
                if (episodes != null) {
                    // Keep verified older hits if a later page is temporarily blocked.
                    if (!complete) {
                        Map<String, BilibiliVods.Episode> merged = new LinkedHashMap<>();
                        BiliState previous = bili.get(source);
                        if (previous != null) for (BilibiliVods.Episode e : previous.episodes()) merged.put(e.bvid(), e);
                        for (BilibiliVods.Episode e : episodes) merged.put(e.bvid(), e);
                        episodes = new ArrayList<>(merged.values());
                    }
                    long fetchedAt = System.currentTimeMillis();
                    bili.put(source, new BiliState(complete ? fetchedAt : fetchedAt - BILI_TTL + BILI_FAIL, episodes));
                    if (complete) biliFailedUntil.remove(source);
                    else biliFailedUntil.put(source, System.currentTimeMillis() + BILI_FAIL);
                    saveBili(source);
                } else {
                    biliFailedUntil.put(source, System.currentTimeMillis() + BILI_FAIL);
                }
                waiters = biliWaiters.remove(source);
            }
            if (!waiters.isEmpty()) main.post(() -> {
                for (Runnable waiter : waiters) {
                    try { waiter.run(); } catch (RuntimeException ignored) {}
                }
            });
        });
    }

    private void resolveMigu(Match match, Runnable changed, boolean forOpen) {
        String key = key(match);
        long now = System.currentTimeMillis();
        Long failed = miguFailed.get(key);
        if (!forOpen && failed != null && failed > now) return;
        Long checked = miguChecked.get(key);
        if (miguHits.containsKey(key) && checked != null && now - checked < MIGU_FOUND_TTL) {
            if (forOpen) main.post(changed);
            return;
        }
        synchronized (miguWaiters) {
            List<Runnable> waiters = miguWaiters.get(key);
            if (waiters != null) {
                if (changed != null) waiters.add(changed);
                return;
            }
            waiters = new ArrayList<>();
            if (changed != null) waiters.add(changed);
            miguWaiters.put(key, waiters);
        }
        worker.execute(() -> {
            MiguMatches.Hit hit = null;
            Log.i(TAG, "Migu lookup start " + match.league + " " + match.home + " vs " + match.away);
            try {
                String home = MiguTeams.chinese(match.league, match.home);
                String away = MiguTeams.chinese(match.league, match.away);
                if (home != null && away != null) {
                    String pack = packId();
                    if (pack == null || pack.isBlank()) throw new IOException("Migu search pack missing");
                    JSONObject response = new JSONObject(search(home + " " + away, pack));
                    hit = MiguMatches.find(response, match, match.league);
                }
            } catch (Exception e) {
                Log.i(TAG, "Migu match lookup unavailable: " + e.getMessage());
            }
            boolean found = hit != null;
            if (found) {
                Log.i(TAG, "Migu hit " + hit.pID() + " " + hit.left() + " vs " + hit.right());
                miguHits.put(key, hit);
                miguChecked.put(key, System.currentTimeMillis());
                miguFailed.remove(key);
                pruneMigu(System.currentTimeMillis());
                saveMigu();
            } else {
                miguFailed.put(key, System.currentTimeMillis() + MIGU_FAIL);
                if (miguFailed.size() > 200) {
                    long cleanupNow=System.currentTimeMillis();
                    miguFailed.entrySet().removeIf(entry -> entry.getValue()<=cleanupNow);
                    if (miguFailed.size() > 200) miguFailed.clear();
                }
            }
            List<Runnable> waiters;
            synchronized (miguWaiters) {
                waiters = miguWaiters.remove(key);
            }
            main.post(() -> {
                for (Runnable waiter : waiters) {
                    try { waiter.run(); } catch (RuntimeException e) {
                        Log.w(TAG, "Migu watch callback failed", e);
                    }
                }
            });
        });
    }

    private String packId() {
        long now = System.currentTimeMillis();
        String cached = packId;
        if (cached != null && !cached.isBlank() && now - packFetched < PACK_TTL) return cached;
        try {
            JSONObject root = new JSONObject(httpGet(MiguMatches.SETTINGS_URL, Collections.emptyMap()));
            JSONObject body = root.optJSONObject("body");
            String value = body == null ? "" : body.optString("paramValue");
            if (!value.isBlank()) {
                packId = value;
                packFetched = now;
                saveMigu();
                return value;
            }
        } catch (Exception e) {
            Log.i(TAG, "Migu search settings unavailable: " + e.getMessage());
        }
        String fallback = cached == null || cached.isBlank() ? DEFAULT_PACK : cached;
        packId = fallback;
        packFetched = now;
        return fallback;
    }

    private String search(String keyword, String pack) throws Exception {
        String sid = UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT)
            + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT).substring(0, 16);
        JSONObject body = new JSONObject()
            .put("appVersion", "6.1.1.00")
            .put("ct", 101)
            .put("isCorrectWord", 1)
            .put("k", keyword)
            .put("mediaSource", 9_000_000)
            .put("packId", pack)
            .put("pageIdx", 1)
            .put("pageSize", 50)
            .put("sid", sid)
            .put("copyrightTerminal", 3)
            .put("searchScene", 2)
            .put("uiVersion", "A3.26.0");
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("appid", "miguvideo");
        headers.put("terminalid", "www");
        headers.put("Referer", "https://www.miguvideo.com/");
        headers.put("User-Agent", UA);
        return httpPost(MiguMatches.SEARCH_URL, body.toString(), headers);
    }

    private static String httpGet(String url, Map<String, String> headers) throws Exception {
        return httpGet(url, headers, false);
    }

    private static String httpGet(String url, Map<String, String> headers, boolean minimalHeaders) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(6_000);
            connection.setReadTimeout(10_000);
            // The account archive endpoint accepts the same minimal headers as the public space.
            if (!minimalHeaders) {
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            }
            connection.setRequestProperty("User-Agent", UA);
            for (Map.Entry<String, String> h : headers.entrySet()) connection.setRequestProperty(h.getKey(), h.getValue());
            if (connection.getResponseCode() != 200) throw new IOException("HTTP " + connection.getResponseCode());
            return read(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    private static String httpPost(String url, String body, Map<String, String> headers) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(6_000);
            connection.setReadTimeout(10_000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            connection.setRequestProperty("User-Agent", UA);
            for (Map.Entry<String, String> h : headers.entrySet()) connection.setRequestProperty(h.getKey(), h.getValue());
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            if (connection.getResponseCode() != 200) throw new IOException("HTTP " + connection.getResponseCode());
            return read(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    private static String read(InputStream input) throws Exception {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
            while ((n = in.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Watch request cancelled");
                if (System.nanoTime() > deadline) throw new SocketTimeoutException("Watch response timed out");
                if (out.size() + n > 8_000_000) throw new IOException("response too large");
                out.write(buffer, 0, n);
            }
            return out.toString("UTF-8");
        }
    }

    private String key(Match match) {
        return match.id + "|" + match.start + "|" + match.home + "|" + match.away;
    }

    private void loadBili(BilibiliVods.Source source) {
        File file = new File(dir, source.cache);
        if (!file.exists()) return;
        try {
            JSONObject root = new JSONObject(new String(new AtomicFile(file).readFully(), StandardCharsets.UTF_8));
            JSONArray archives = root.optJSONArray("archives");
            if (archives == null) return;
            List<BilibiliVods.Episode> episodes = new ArrayList<>();
            for (int i = 0; i < archives.length(); i++) {
                JSONObject a = archives.optJSONObject(i);
                if (a == null) continue;
                String bvid = a.optString("bvid");
                String title = a.optString("title");
                long duration = a.optLong("duration");
                if (!bvid.isBlank() && !title.isBlank() && duration >= source.minDuration()) {
                    episodes.add(new BilibiliVods.Episode(bvid, title, duration, a.optLong("pubdate")));
                }
            }
            if (!episodes.isEmpty()) bili.put(source, new BiliState(root.optLong("fetched"), episodes));
        } catch (Exception e) {
            Log.i(TAG, "Ignoring Bilibili watch cache");
        }
    }

    private synchronized void saveBili(BilibiliVods.Source source) {
        BiliState state = bili.get(source);
        if (state == null) return;
        try {
            JSONArray archives = new JSONArray();
            for (BilibiliVods.Episode e : state.episodes()) {
                archives.put(new JSONObject().put("bvid", e.bvid()).put("title", e.title())
                    .put("duration", e.duration()).put("pubdate", e.pubdate()));
            }
            JSONObject root = new JSONObject().put("fetched", state.fetched()).put("archives", archives);
            write(source.cache, root);
        } catch (Exception e) {
            Log.w(TAG, "Cannot persist Bilibili watch cache", e);
        }
    }

    private void loadMigu() {
        File file = new File(dir, "migu.json");
        if (!file.exists()) return;
        try {
            JSONObject root = new JSONObject(new String(new AtomicFile(file).readFully(), StandardCharsets.UTF_8));
            packId = root.optString("packId", "");
            packFetched = root.optLong("packFetched");
            JSONObject hits = root.optJSONObject("hits");
            if (hits == null) return;
            for (java.util.Iterator<String> it = hits.keys(); it.hasNext(); ) {
                String key = it.next();
                JSONObject h = hits.optJSONObject(key);
                if (h == null) continue;
                String pID = h.optString("pID");
                if (pID.isBlank()) continue;
                miguHits.put(key, new MiguMatches.Hit(pID, h.optString("left"), h.optString("right"),
                    h.optLong("start"), h.optString("competition")));
                miguChecked.put(key, h.optLong("fetched"));
            }
            pruneMigu(System.currentTimeMillis());
        } catch (Exception e) {
            Log.i(TAG, "Ignoring Migu watch cache");
        }
    }

    /** Keep the long-lived Migu hit cache bounded; old entries otherwise accumulate forever. */
    private void pruneMigu(long now) {
        if (miguHits.size() <= 400) return;
        long cutoff = now - 14L * 24 * 60 * 60_000;
        for (String key : new ArrayList<>(miguHits.keySet())) {
            Long checked = miguChecked.get(key);
            if (checked == null || checked < cutoff) {
                miguHits.remove(key);
                miguChecked.remove(key);
            }
        }
        if (miguHits.size() > 500) {
            List<String> keys = new ArrayList<>(miguHits.keySet());
            keys.sort(Comparator.comparingLong(key -> miguChecked.getOrDefault(key, 0L)));
            int excess = keys.size() - 400;
            for (int i = 0; i < excess; i++) {
                miguHits.remove(keys.get(i));
                miguChecked.remove(keys.get(i));
            }
        }
    }

    private synchronized void saveMigu() {
        try {
            JSONObject hits = new JSONObject();
            for (Map.Entry<String, MiguMatches.Hit> entry : miguHits.entrySet()) {
                MiguMatches.Hit h = entry.getValue();
                hits.put(entry.getKey(), new JSONObject().put("pID", h.pID())
                    .put("left", h.left()).put("right", h.right()).put("start", h.start())
                    .put("competition", h.competition()).put("fetched", miguChecked.getOrDefault(entry.getKey(), 0L)));
            }
            JSONObject root = new JSONObject().put("packId", packId == null ? "" : packId)
                .put("packFetched", packFetched).put("hits", hits);
            write("migu.json", root);
        } catch (Exception e) {
            Log.w(TAG, "Cannot persist Migu watch cache", e);
        }
    }

    private void write(String name, JSONObject root) {
        AtomicFile file=new AtomicFile(new File(dir,name));
        FileOutputStream out=null;
        try {
            out = file.startWrite();
            out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out);
        } catch (Exception e) {
            if (out != null) file.failWrite(out);
            Log.w(TAG, "Cannot write watch cache", e);
        }
    }
}
