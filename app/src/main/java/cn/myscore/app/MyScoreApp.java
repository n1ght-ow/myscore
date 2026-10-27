package cn.myscore.app;

import android.app.Application;

/** Process-wide hooks for memory pressure; activities keep their own UI state. */
public final class MyScoreApp extends Application {
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Logos.trimMemory(level);
    }
}
