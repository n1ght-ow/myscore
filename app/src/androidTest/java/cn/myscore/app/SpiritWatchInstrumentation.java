package cn.myscore.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Exercises the production resolver against live public metadata or its real on-device cache. */
public final class SpiritWatchInstrumentation extends Instrumentation {
    private int checks;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle report = new Bundle();
        int outcome = Activity.RESULT_CANCELED;
        try {
            Match match = new Match();
            match.league = "cs2"; match.home = "Spirit"; match.homeId = "csgo_tm_7020";
            match.away = "NAVI"; match.state = "final";
            match.start = Match.parseTime("2026-10-29T00:00:00+08:00");
            WatchRepository watches = WatchRepository.get(getTargetContext());
            boolean cached = watches.link(match).kind == WatchLinks.Kind.REPLAY;
            if (!cached) {
                CountDownLatch changed = new CountDownLatch(1);
                runOnMainSync(() -> watches.resolve(match, changed::countDown));
                require(changed.await(90, TimeUnit.SECONDS), "resolver completes");
            }
            WatchLinks.Link link = watches.link(match);
            require(link.kind == WatchLinks.Kind.REPLAY, "verified recording available");
            require(link.url.equals("https://www.bilibili.com/video/BV1TisR6eEY7"), "exact Spirit/NAVI video");
            require(link.note.contains("Spirit vs NAVI") && !link.note.contains("全球总决赛"), "CS2 context");
            require(watches.signature(match).contains("BV1TisR6eEY7"), "tablet signature upgrades with replay");
            match.away = "Unknown opponent";
            require(watches.link(match).kind == WatchLinks.Kind.PENDING, "unmatched fixture stays pending");
            require(watches.link(match).url.equals(BilibiliVods.Source.CS2.space()), "pending opens configured account");
            for (String state : new String[]{"live", "scheduled"}) {
                match.state = state;
                require(watches.link(match).url.equals("https://www.douyu.com/6657"), state + " retains live room");
            }
            report.putString("stream", "PASS " + checks + " Spirit watch checks ("
                + (cached ? "real device cache" : "live production resolver") + ").\n");
            outcome = Activity.RESULT_OK;
        } catch (Throwable e) {
            report.putString("stream", "FAIL after " + checks + ": " + e + "\n" + android.util.Log.getStackTraceString(e));
        }
        finish(outcome, report);
    }
    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
