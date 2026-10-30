package cn.myscore.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.nio.file.Files;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Recorded public Migu cards and intercepted intents; -e live true uses real networking. */
public final class WatchPlaybackInstrumentation extends Instrumentation {
    private int checks;
    private Activity activity;
    private boolean live;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        live = args != null && "true".equals(args.getString("live"));
        start();
    }

    @Override public void onStart() {
        Bundle report = new Bundle();
        int outcome = Activity.RESULT_OK;
        File cache = new File(getTargetContext().getCacheDir(), "watch-v1/migu.json");
        byte[] original = null;
        try {
            require(android.os.Build.HARDWARE.equals("ranchu")
                || android.os.Build.HARDWARE.equals("goldfish"), "emulator only");
            if (!live) {
                if (cache.exists()) original = Files.readAllBytes(cache.toPath());
                recordedResponses();
            }
            WatchRepository watches = WatchRepository.get(getTargetContext());
            Match nba = fixture("nba", "Cleveland Cavaliers", "Minnesota Timberwolves",
                "2026-10-27T07:00:00+08:00");
            Match epl = fixture("epl", "Manchester United", "Aston Villa",
                "2026-11-09T00:30:00+08:00");
            lookup(watches, nba, "120000588291");
            lookup(watches, epl, "120000584185");
            activity = startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            long deadline = System.currentTimeMillis() + 5_000;
            while (!activity.hasWindowFocus() && System.currentTimeMillis() < deadline) Thread.sleep(50);
            require(activity.hasWindowFocus(), "play activity is foreground");
            play(nba, "120000588291", false);
            play(epl, "120000584185", true);
            Match missing = fixture("epl", "Unknown FC", "Aston Villa", "2026-11-09T00:30:00+08:00");
            CountDownLatch done = new CountDownLatch(2);
            runOnMainSync(() -> {
                watches.resolveForOpen(missing, link -> {
                    require(link.kind == WatchLinks.Kind.ZONE, "missing fixture remains explicitly a zone");
                    done.countDown();
                });
                watches.resolveForOpen(missing, link -> done.countDown());
            });
            require(done.await(10, TimeUnit.SECONDS), "all no-result subscribers complete");
            CountDownLatch retry = new CountDownLatch(1);
            runOnMainSync(() -> watches.resolveForOpen(missing, link -> retry.countDown()));
            require(retry.await(10, TimeUnit.SECONDS), "manual retry completes during background backoff");
            CountDownLatch missingOpened = new CountDownLatch(1);
            List<String> destinations = new ArrayList<>();
            ActivityMonitor blocked = new ActivityMonitor() {
                @Override public ActivityResult onStartActivity(Intent intent) {
                    if (!Intent.ACTION_VIEW.equals(intent.getAction())) return null;
                    destinations.add(intent.getDataString());
                    return new ActivityResult(Activity.RESULT_OK, null);
                }
            };
            addMonitor(blocked);
            try {
                runOnMainSync(() -> {
                    new WatchLauncher(activity, watches).open(missing);
                    watches.resolveForOpen(missing, link -> missingOpened.countDown());
                });
                require(missingOpened.await(10, TimeUnit.SECONDS), "missing play lookup completes");
                waitForIdleSync();
                require(destinations.isEmpty(), "missing play never opens the league zone automatically");
                boolean explained = false;
                long explainDeadline = System.currentTimeMillis() + 3_000;
                while (!explained && System.currentTimeMillis() < explainDeadline) {
                    android.view.accessibility.AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
                    explained = root != null && !root.findAccessibilityNodeInfosByText("暂无本场播放链接").isEmpty();
                    if (!explained) Thread.sleep(100);
                }
                require(explained, "missing fixture shows an explicit explanation");
            } finally { removeMonitor(blocked); }
            report.putString("stream", "\nPASS: " + checks + " watch playback checks ("
                + (live ? "live network" : "recorded source responses") + ").\n");
        } catch (Throwable e) {
            outcome = Activity.RESULT_CANCELED;
            report.putString("stream", "\nFAIL after " + checks + ": " + e + "\n"
                + android.util.Log.getStackTraceString(e));
        } finally {
            if (activity != null) runOnMainSync(() -> activity.finish());
            if (!live) {
                try {
                    if (original == null) cache.delete();
                    else Files.write(cache.toPath(), original);
                } catch (IOException e) { throw new AssertionError("restore watch cache", e); }
            }
        }
        finish(outcome, report);
    }

    private void lookup(WatchRepository watches, Match match, String id) throws Exception {
        CountDownLatch done = new CountDownLatch(3);
        List<WatchLinks.Link> links = new ArrayList<>();
        runOnMainSync(() -> {
            require(watches.link(match).kind == WatchLinks.Kind.ZONE, "cold fixture has no cached direct link");
            watches.resolve(match, done::countDown);
            watches.resolveForOpen(match, link -> { links.add(link); done.countDown(); });
            watches.resolveForOpen(match, link -> { links.add(link); done.countDown(); });
        });
        require(done.await(60, TimeUnit.SECONDS), "background and both play subscribers complete");
        for (WatchLinks.Link link : links) {
            require(link.url.equals("https://www.miguvideo.com/p/live/" + id), match.league + " exact match URL");
        }
        CountDownLatch cached = new CountDownLatch(1);
        runOnMainSync(() -> watches.resolveForOpen(match, link -> cached.countDown()));
        require(cached.await(2, TimeUnit.SECONDS), "cached lookup completes immediately");
    }

    private void play(Match fixture, String id, boolean useDetail) throws Exception {
        // Different cache key, same source-published fixture: test a fresh play lookup.
        fixture.id += "-play";
        if (useDetail) {
            runOnMainSync(() -> activity.finish());
            activity = startActivitySync(new Intent(getTargetContext(), DetailActivity.class)
                .putExtra("match", fixture.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            long deadline = System.currentTimeMillis() + 5_000;
            while (!activity.hasWindowFocus() && System.currentTimeMillis() < deadline) Thread.sleep(50);
        }
        CountDownLatch opened = new CountDownLatch(1);
        List<String> urls = new ArrayList<>();
        ActivityMonitor monitor = new ActivityMonitor() {
            @Override public ActivityResult onStartActivity(Intent intent) {
                if (!Intent.ACTION_VIEW.equals(intent.getAction())) return null;
                urls.add(intent.getDataString());
                opened.countDown();
                return new ActivityResult(Activity.RESULT_OK, null);
            }
        };
        addMonitor(monitor);
        try {
            runOnMainSync(() -> {
                if (useDetail) {
                    try {
                        java.lang.reflect.Method open = DetailActivity.class.getDeclaredMethod("openWatch");
                        open.setAccessible(true);
                        open.invoke(activity);
                        open.invoke(activity);
                    } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                } else {
                    ((MainActivity) activity).openWatch(fixture);
                    ((MainActivity) activity).openWatch(fixture);
                }
            });
            require(opened.await(60, TimeUnit.SECONDS), "play opens an external destination");
            waitForIdleSync();
            require(urls.equals(List.of(WatchAppLinks.targets("https://www.miguvideo.com/p/live/" + id).get(0).url())),
                "double play tap opens the fixture once from " + (useDetail ? "detail" : "list"));
        } finally { removeMonitor(monitor); }
    }

    private Match fixture(String league, String home, String away, String start) {
        Match match = new Match();
        match.id = "watch-regression-" + league + "-" + System.nanoTime();
        match.league = league;
        match.home = home;
        match.away = away;
        match.start = Match.parseTime(start);
        match.timeConfirmed = true;
        return match;
    }

    private void recordedResponses() {
        URL.setURLStreamHandlerFactory(protocol -> !"https".equals(protocol) ? null : new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) {
                return new HttpURLConnection(url) {
                    private final ByteArrayOutputStream request = new ByteArrayOutputStream();
                    @Override public void connect() {}
                    @Override public void disconnect() {}
                    @Override public boolean usingProxy() { return false; }
                    @Override public OutputStream getOutputStream() { return request; }
                    @Override public int getResponseCode() throws IOException {
                        if (!url.getHost().equals("jadeite.migu.cn")
                            && !url.getHost().equals("app-sc.miguvideo.com")) {
                            throw new IOException("Unrelated network disabled in playback regression");
                        }
                        return 200;
                    }
                    @Override public InputStream getInputStream() throws IOException {
                        try { Thread.sleep(150); } catch (InterruptedException e) {
                            Thread.currentThread().interrupt(); throw new IOException(e);
                        }
                        String response = "{\"body\":{\"paramValue\":\"recorded-test-pack\"}}";
                        if (url.getHost().equals("jadeite.migu.cn")) {
                            boolean nba = request.toString(StandardCharsets.UTF_8).contains("骑士");
                            response = "{\"code\":200,\"body\":{\"widgetResult\":{\"items\":[{"
                                + "\"widgetId\":\"" + (nba ? "120000588291" : "120000584185") + "\","
                                + "\"competitionName\":\"" + (nba ? "NBA常规赛" : "英超") + "\","
                                + "\"confrontTeamLeftName\":\"" + (nba ? "森林狼" : "曼联") + "\","
                                + "\"confrontTeamRightName\":\"" + (nba ? "骑士" : "阿斯顿维拉") + "\","
                                + "\"matchStartTime\":\"" + (nba ? "2026-10-27 07:00:00" : "2026-11-09 00:30:00")
                                + "\"}]}}}";
                        }
                        return new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));
                    }
                };
            }
        });
    }

    private void require(boolean okay, String why) {
        if (!okay) throw new AssertionError(why);
        checks++;
    }
}
