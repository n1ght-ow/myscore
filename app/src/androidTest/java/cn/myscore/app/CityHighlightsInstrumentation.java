package cn.myscore.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

/** Read-only checks against each device's actual public-source watch cache. */
public final class CityHighlightsInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle report = new Bundle();
        int outcome = Activity.RESULT_OK;
        try {
            WatchRepository watches = WatchRepository.get(getTargetContext());
            Match cup = fixture("efl", "Brighton & Hove Albion", "2026-10-29T03:30:00+08:00", "2", "2");
            verify(watches.link(cup), "BV1hgsd6gE6a");
            Match champions = fixture("ucl", "AEK Athens", "2026-10-21T03:00:00+08:00", "5", "1");
            // This recording is 137 seconds: loading the persistent cache must not use the full-VOD floor.
            verify(watches.link(champions), "BV1g9HA6JEAh");
            cup.homeScore = "4"; cup.awayScore = "3";
            require(watches.link(cup).kind == WatchLinks.Kind.PENDING, "shootout score cannot replace the match score");
            cup.league = "epl";
            require(watches.link(cup).provider.equals("咪咕视频"), "Premier League retains Migu");
            cup.league = "efl"; cup.state = "live";
            require(watches.link(cup) == null, "cup highlight is post-match only");
            report.putString("stream", "PASS: exact cup highlight, persisted 137-second UCL highlight, labels, wrong-score pending, EPL Migu and unfinished-game exclusion. Real device cache only.\n");
        } catch (Throwable e) {
            outcome = Activity.RESULT_CANCELED;
            report.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(e));
        }
        finish(outcome, report);
    }
    private Match fixture(String league, String away, String date, String ownScore, String otherScore) {
        Match m = new Match(); m.league = league; m.home = "Manchester City"; m.homeId = "382";
        m.away = away; m.homeScore = ownScore; m.awayScore = otherScore;
        m.start = Match.parseTime(date); m.state = "final"; return m;
    }
    private void verify(WatchLinks.Link link, String bvid) {
        require(link != null && link.kind == WatchLinks.Kind.HIGHLIGHTS, "highlight is ready");
        require(link.url.equals("https://www.bilibili.com/video/" + bvid), "correct match video");
        require(link.title.equals("观看本场集锦") && link.kind.label.equals("集锦"), "accurate highlight label");
    }
    private void require(boolean okay, String description) { if (!okay) throw new AssertionError(description); }
}
