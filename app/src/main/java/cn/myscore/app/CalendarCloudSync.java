package cn.myscore.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.util.Log;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Xiaomi ignores normal-app sync requests. Send only through the authorized shell service. */
final class CalendarCloudSync {
    static final String STORE="calendar-cloud";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static volatile Context application;
    private static boolean active;
    private static final ScheduledExecutorService WORKER=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"myscore-calendar-cloud");t.setDaemon(true);return t;});
    private static final SyncRequestQueue<String> REQUESTS=new SyncRequestQueue<>(WORKER,target->{
        if(!send())throw new IllegalStateException("Calendar sync is unavailable");
    },2_000,300);
    private static final Runnable poll=()->{
        if(!active||application==null)return;
        request();
        MAIN.postDelayed(CalendarCloudSync.poll,15_000);
    };
    static void initialize(Context context) {
        application=context.getApplicationContext();
        ShizukuCalendar.initialize(application);
    }
    static void opened(Context context) {
        initialize(context);active=true;
        MAIN.removeCallbacks(poll);MAIN.postDelayed(poll,15_000);
        request();
    }
    static void background(){active=false;MAIN.removeCallbacks(poll);}
    static void resumed(Activity activity){initialize(activity);ShizukuCalendar.resumed(activity);}
    static void paused(Activity activity){}
    static void connected(Context context){refresh(context);request();}
    static void refresh(Context context) {
        if(context==null)return;
        SharedPreferences prefs=context.getSharedPreferences(STORE,Context.MODE_PRIVATE);
        String label=ShizukuCalendar.status();
        if(!label.equals(prefs.getString("connection","")))prefs.edit().putString("connection",label).apply();
    }
    static String status(Context context) {
        SharedPreferences prefs=context.getSharedPreferences(STORE,Context.MODE_PRIVATE);
        String label=prefs.getString("connection","请启动 Shizuku");
        if(prefs.getLong("generation",0)>prefs.getLong("sent",0))return label+" · 改动待发送";
        if(!prefs.getString("error","").isEmpty())return label+" · "+prefs.getString("error","");
        long last=prefs.getLong("requested",0);
        return last>0&&ShizukuCalendar.ready()?label+" · 已请求同步":label;
    }
    static void changed(Context context,long calendar) {
        if(calendar<0)return;
        initialize(context);
        WORKER.execute(()->{
            if(!CalendarSync.permitted(application)||!xiaomiCalendar(calendar))return;
            SharedPreferences prefs=application.getSharedPreferences(STORE,Context.MODE_PRIVATE);
            // Persist before queueing: process death or Shizuku stopping must not lose an edit.
            if(!prefs.edit().putLong("generation",prefs.getLong("generation",0)+1).commit())
                Log.w("MyScore","Cannot persist calendar cloud request");
            CalendarCloudJob.schedule(application);
            REQUESTS.request("xiaomi");
        });
    }
    private static boolean xiaomiCalendar(long calendar) {
        try(Cursor rows=application.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,
            new String[]{CalendarContract.Calendars.ACCOUNT_TYPE},"_id=?",new String[]{Long.toString(calendar)},null)) {
            if(rows!=null&&rows.moveToFirst())return "com.xiaomi".equals(rows.getString(0));
        }catch(RuntimeException ignored){}
        return true; // Xiaomi sometimes hides Calendars while keeping Events readable.
    }
    private static void request(){if(application!=null)WORKER.execute(()->{if(CalendarSync.permitted(application))REQUESTS.request("xiaomi");});}
    private static boolean send() {
        if(application==null||!CalendarSync.permitted(application))return false;
        SharedPreferences prefs=application.getSharedPreferences(STORE,Context.MODE_PRIVATE);
        long generation=prefs.getLong("generation",0);
        if(!ShizukuCalendar.ready()){ShizukuCalendar.reconnect();refresh(application);return false;}
        try {
            String[] result=ShizukuCalendar.request().split("\\|");
            int uid=Integer.parseInt(result[0]),count=Integer.parseInt(result[1]);
            if((uid!=2000&&uid!=0)||count<=0)throw new IllegalStateException("No Xiaomi calendar account");
            prefs.edit().putLong("sent",generation).putLong("requested",System.currentTimeMillis()).remove("error").apply();
            CalendarCloudJob.cancel(application);
            Log.i("MyScore","Xiaomi calendar sync accepted through Shizuku; uid="+uid+" accounts="+count);
            return true;
        }catch(Exception e){
            prefs.edit().putString("error","请求失败，稍后重试").apply();
            Log.w("MyScore","Shizuku calendar request failed: "+e.getClass().getSimpleName());
            return false;
        }
    }
    /** Bounded reconnect for a persisted background job; no activity or permission dialog. */
    static void retry(Context context,java.util.function.Consumer<Boolean> completion) {
        initialize(context);retry(completion,0);
    }
    private static void retry(java.util.function.Consumer<Boolean> completion,int attempt) {
        WORKER.schedule(()->{
            boolean ok=send();
            if(ok||attempt>=9)completion.accept(ok);
            else retry(completion,attempt+1);
        },attempt==0?0:1,TimeUnit.SECONDS);
    }
}
