package cn.myscore.app;

import android.app.Application;
import android.content.SharedPreferences;
import java.io.File;

/** Process-wide hooks for memory pressure; activities keep their own UI state. */
public final class MyScoreApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        removeLegacyLolData();
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
    private void removeLegacyLolData() {
        SharedPreferences settings=getSharedPreferences("data-migrations",MODE_PRIVATE);
        if(settings.getBoolean("retired-lol",false))return;
        boolean removed=deleteLegacyCache("scores-v1","(?:view_|day_)lol_.*\\.json(?:\\.bak|\\.new)?")
            & deleteLegacyCache("teams-v1","lol_.*\\.json(?:\\.bak|\\.new)?")
            & deleteLegacyCache("details-v4","qq_lol_[0-9]+\\.json(?:\\.bak|\\.new)?")
            & deleteLegacyCache("watch-v1","bilibili\\.json(?:\\.bak|\\.new)?");
        removeLegacyKeys("calendar-links","(?:match|record|fingerprint|retired):qq:lol:[0-9]+");
        removeLegacyKeys("result-notifications","sent:qq:lol:[0-9]+");
        if(removed)settings.edit().putBoolean("retired-lol",true).apply();
    }
    private boolean deleteLegacyCache(String directory,String pattern) {
        File folder=new File(getCacheDir(),directory);
        if(!folder.exists())return true;
        File[] files=folder.listFiles((dir,name)->name.matches(pattern));
        if(files==null)return false;
        boolean removed=true;
        for(File file:files)if(file.isFile()&&!file.delete()&&file.exists())removed=false;
        return removed;
    }
    private void removeLegacyKeys(String store,String pattern) {
        SharedPreferences prefs=getSharedPreferences(store,MODE_PRIVATE);
        SharedPreferences.Editor editor=prefs.edit();
        for(String key:prefs.getAll().keySet())if(key.matches(pattern))editor.remove(key);
        editor.apply();
    }
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Logos.trimMemory(level);
    }
}
