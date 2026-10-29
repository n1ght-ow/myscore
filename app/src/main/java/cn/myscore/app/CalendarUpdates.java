package cn.myscore.app;

import android.content.Context;
import android.content.SharedPreferences;
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
    static void observe(Context context,List<Match> matches) {
        if(matches==null||matches.isEmpty()||!CalendarSync.permitted(context))return;
        Context app=context.getApplicationContext();SharedPreferences settings=app.getSharedPreferences("settings",0),links=app.getSharedPreferences("calendar-links",0);
        if(settings.getLong("calendar_id",-1)<0&&!hasLinks(links))return;
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
            synchronized(PENDING){if(PENDING.isEmpty()){running=false;return;}batch=new ArrayList<>(PENDING.values());PENDING.clear();}
            try {
                CalendarSync.Result result=new CalendarSync(app).syncKnown(batch,System.currentTimeMillis());
                if(result.failed>0){synchronized(PENDING){for(Match m:batch)CHECKED_ORPHANS.remove(m.id);}android.util.Log.w("MyScore","Calendar source update incomplete: "+result.message());}
            }catch(RuntimeException e){synchronized(PENDING){for(Match m:batch)CHECKED_ORPHANS.remove(m.id);}android.util.Log.w("MyScore","Calendar source update failed",e);}
        }
    }
    private static boolean hasLinks(SharedPreferences links){for(String key:links.getAll().keySet())if(key.startsWith("match:"))return true;return false;}
    private static String signature(Match m){return m.start+"|"+m.timeConfirmed+"|"+m.state+"|"+m.displayTitle();}
    static void awaitIdle()throws Exception {WORKER.submit(()->{}).get(10,java.util.concurrent.TimeUnit.SECONDS);}
}
