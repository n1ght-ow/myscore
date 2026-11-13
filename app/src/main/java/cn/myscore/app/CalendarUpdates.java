package cn.myscore.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One coalesced background writer for the exact source matches the app has already read. */
final class CalendarUpdates {
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{Thread thread=new Thread(r,"myscore-calendar-updates");thread.setDaemon(true);return thread;});
    private static final Map<String,Match> PENDING=new LinkedHashMap<>();
    private static final Map<String,String> CHECKED_ORPHANS=new HashMap<>();
    private static boolean running;
    private static boolean recoverPending;
    private static ContentObserver observer;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static Runnable calendarChanged;
    /** Called on resume, including after calendar permission is granted. Never waits for network. */
    static void start(Context context) {
        if(!CalendarSync.permitted(context))return;
        Context app=context.getApplicationContext();
        synchronized(PENDING) {
            if(observer==null) {
                calendarChanged=()->recover(app);
                ContentObserver candidate=new ContentObserver(MAIN) {
                    @Override public void onChange(boolean selfChange) {
                        MAIN.removeCallbacks(calendarChanged);MAIN.postDelayed(calendarChanged,200);
                    }
                };
                try {app.getContentResolver().registerContentObserver(CalendarContract.CONTENT_URI,true,candidate);observer=candidate;}
                catch(RuntimeException e){android.util.Log.w("MyScore","Calendar observer unavailable",e);}
            }
        }
        recover(app);
    }
    private static void recover(Context app) {
        synchronized(PENDING) {
            CHECKED_ORPHANS.clear();recoverPending=true;
            if(running)return;running=true;WORKER.execute(()->drain(app));
        }
    }
    static void observe(Context context,List<Match> matches) {
        if(matches==null||matches.isEmpty()||!CalendarSync.permitted(context))return;
        Context app=context.getApplicationContext();SharedPreferences links=app.getSharedPreferences("calendar-links",0);
        synchronized(PENDING) {
            for(Match match:matches) {
                if(match==null||match.id.isBlank())continue;
                Match previous=null;
                try{String record=links.getString("record:"+match.id,"");if(!record.isBlank())previous=Match.fromJson(new org.json.JSONObject(record));}catch(Exception ignored){}
                boolean linked=links.contains("match:"+match.id);
                if(linked) {
                    CHECKED_ORPHANS.remove(match.id);
                    if(previous!=null&&(previous.updated>match.updated||(!match.finalised()&&!match.cancelled()&&signature(previous).equals(signature(match)))))continue;
                } else {
                    if(signature(match).equals(CHECKED_ORPHANS.get(match.id)))continue;
                    CHECKED_ORPHANS.put(match.id,signature(match));
                }
                Match queued=PENDING.get(match.id);if(queued!=null&&queued.updated>match.updated)continue;
                try{PENDING.put(match.id,Match.fromJson(match.json()));}catch(RuntimeException ignored){}
            }
            if(CHECKED_ORPHANS.size()>2000)CHECKED_ORPHANS.clear();
            if(running||PENDING.isEmpty())return;running=true;
            WORKER.execute(()->drain(app));
        }
    }
    private static void drain(Context app) {
        for(;;) {
            List<Match> batch;
            boolean local;
            synchronized(PENDING){if(PENDING.isEmpty()&&!recoverPending){running=false;return;}local=recoverPending;recoverPending=false;batch=new ArrayList<>(PENDING.values());PENDING.clear();}
            try {
                CalendarSync sync=new CalendarSync(app);
                if(local){
                    long started=android.os.SystemClock.uptimeMillis();
                    CalendarSync.Result result=sync.recoverLocal();
                    if(result.inserted+result.cancelled>0)CalendarJob.schedule(app);
                    CalendarCloudSync.refreshAsync(app);
                    android.util.Log.i("MyScorePerf","calendar local recovery in "+(android.os.SystemClock.uptimeMillis()-started)+"ms links="+result.inserted+" reminders="+result.updated+" failed="+result.failed);
                    if(result.failed>0)android.util.Log.w("MyScore","Calendar recovery incomplete: "+result.error);
                }
                if(batch.isEmpty())continue;
                CalendarSync.Result result=sync.syncKnown(batch,System.currentTimeMillis());
                if(result.updated+result.completed+result.cancelled>0){CalendarJob.schedule(app);CalendarCloudSync.refreshAsync(app);}
                if(result.failed>0){synchronized(PENDING){for(Match m:batch)CHECKED_ORPHANS.remove(m.id);}android.util.Log.w("MyScore","Calendar source update incomplete: "+result.message());}
            }catch(RuntimeException e){synchronized(PENDING){for(Match m:batch)CHECKED_ORPHANS.remove(m.id);}android.util.Log.w("MyScore","Calendar source update failed",e);}
        }
    }
    private static String signature(Match m){return m.start+"|"+m.timeConfirmed+"|"+m.state+"|"+m.displayTitle();}
    static void awaitIdle()throws Exception {WORKER.submit(()->{}).get(10,java.util.concurrent.TimeUnit.SECONDS);}
}
