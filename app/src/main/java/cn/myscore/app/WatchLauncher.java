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
        Toast.makeText(activity, "正在查找本场比赛…", Toast.LENGTH_SHORT).show();
        watches.resolveForOpen(match, link -> {
            opening = false;
            if (activity.isFinishing() || activity.isDestroyed()
                || !activity.getWindow().getDecorView().hasWindowFocus()) return;
            if (link == null) return;
            if (link.kind == WatchLinks.Kind.ZONE) {
                String league = "epl".equals(match.league) ? "英超" : "NBA";
                Sheet.confirm(activity, "暂无本场播放链接",
                    "暂时未能找到这场比赛的咪咕页面，请稍后重试。也可以前往" + league + "专区自行查找。",
                    "打开" + league + "专区", () -> launch(link));
            } else {
                launch(link);
            }
        });
    }

    private void launch(WatchLinks.Link link) {
        lastLaunch = SystemClock.elapsedRealtime();
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link.url)));
        } catch (RuntimeException e) {
            Sheet.message(activity, "无法打开直播", "手机没有可用的浏览器。");
        }
    }
}
