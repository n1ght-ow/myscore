package cn.myscore.app;

import android.app.Application;

/** Process-wide hooks for memory pressure; activities keep their own UI state. */
public final class MyScoreApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Http.init(this);
        CalendarCloudSync.initialize(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            private int started;
            private boolean rotating;
            @Override public void onActivityStarted(android.app.Activity activity) {
                if(started++==0&&!rotating)CalendarCloudSync.opened(MyScoreApp.this);
                rotating=false;
            }
            @Override public void onActivityStopped(android.app.Activity activity) {
                started=Math.max(0,started-1);
                if(started==0){rotating=activity.isChangingConfigurations();if(!rotating)CalendarCloudSync.background();}
            }
            @Override public void onActivityCreated(android.app.Activity activity,android.os.Bundle state) {}
            @Override public void onActivityResumed(android.app.Activity activity) {CalendarCloudSync.resumed(activity);}
            @Override public void onActivityPaused(android.app.Activity activity) {CalendarCloudSync.paused(activity);}
            @Override public void onActivitySaveInstanceState(android.app.Activity activity,android.os.Bundle state) {}
            @Override public void onActivityDestroyed(android.app.Activity activity) {}
        });
    }
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Logos.trimMemory(level);
    }
}
