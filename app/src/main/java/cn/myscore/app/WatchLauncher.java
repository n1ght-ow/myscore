package cn.myscore.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.widget.Toast;

/** Shared play action for list cards, the tablet pane, and match details. */
final class WatchLauncher {
    private final Activity activity;
    private final WatchRepository watches;
    private boolean opening;
    private long lastLaunch;

    WatchLauncher(Activity activity, WatchRepository watches) {
        this.activity = activity;
        this.watches = watches;
    }

    void open(Match match) {
        if (opening || SystemClock.elapsedRealtime() - lastLaunch < 600) return;
        WatchLinks.Link cached = watches.link(match);
        if (cached == null) return;
        if (cached.kind != WatchLinks.Kind.ZONE) {
            launch(cached);
            return;
        }
        opening = true;
        Toast.makeText(activity, "Looking for this match…", Toast.LENGTH_SHORT).show();
        watches.resolveForOpen(match, link -> {
            opening = false;
            if (activity.isFinishing() || activity.isDestroyed()
                || !activity.getWindow().getDecorView().hasWindowFocus()) return;
            if (link == null) return;
            if (link.kind == WatchLinks.Kind.ZONE) {
                String league = "epl".equals(match.league) ? "Premier League" : "NBA";
                Sheet.confirm(activity, "No match link yet",
                    "We couldn't find this match on Migu yet. Try again shortly, or open the " + league + " zone and search there.",
                    "Open " + league + " zone", () -> launch(link));
            } else {
                launch(link);
            }
        });
    }

    void launch(WatchLinks.Link link) {
        lastLaunch = SystemClock.elapsedRealtime();
        for (WatchAppLinks.Target target : WatchAppLinks.targets(link.url)) {
            try {
                Intent destination = new Intent(Intent.ACTION_VIEW, Uri.parse(target.url()))
                    .setPackage(target.packageName());
                activity.startActivity(destination);
                return;
            } catch (android.content.ActivityNotFoundException | SecurityException ignored) {
            }
        }
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link.url)));
        } catch (RuntimeException e) {
            Sheet.message(activity, "Couldn't open playback", "No supported app or browser is available.");
        }
    }
}
