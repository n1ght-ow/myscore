package cn.myscore.app;

import android.accounts.Account;
import android.app.Activity;
import android.app.job.JobScheduler;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cross-device subscription sync backed directly by system/Xiaomi CalendarProvider.
 * HyperOS CalendarProvider automatically uploads dirty rows (~30-45s) and pushes via MiCloud
 * without requiring Shizuku or a background daemon.
 */
final class CalendarCloudSync {
    static final String STORE="calendar-cloud";
    private static final String KEY_SUMMARY="summary";
    private static final int LEGACY_SHIZUKU_JOB_ID=24704;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static volatile Context application;
    private static volatile boolean legacyJobCleared;
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"myscore-calendar-cloud");
        t.setDaemon(true);
        return t;
    });

    private record Snapshot(
        boolean permitted,
        boolean hasCalendar,
        boolean localOnly,
        boolean hasXiaomiOption,
        String calendarName,
        long xiaomiCalendarId,
        String xiaomiCalendarName,
        int subscribed,
        int pendingUpload,
        int pendingDelete
    ) {
        String status() {
            return describeStatus(permitted,hasCalendar,localOnly,hasXiaomiOption,subscribed,pendingUpload,pendingDelete);
        }
    }

    static void initialize(Context context) {
        if(context==null)return;
        application=context.getApplicationContext();
        if(application==null)application=context;
        if(!legacyJobCleared) {
            legacyJobCleared=true;
            try {
                JobScheduler scheduler=application.getSystemService(JobScheduler.class);
                if(scheduler!=null)scheduler.cancel(LEGACY_SHIZUKU_JOB_ID);
            } catch(RuntimeException ignored) {}
        }
        if(CalendarSync.permitted(application))CalendarUpdates.start(application);
        refreshAsync(application);
    }

    static void opened(Context context) {
        initialize(context);
        Context app=application;
        if(app==null)return;
        if(CalendarSync.permitted(app))CalendarUpdates.start(app);
        WORKER.execute(()->{
            if(CalendarSync.permitted(app))requestSystemSync(app,-1L);
            writeSnapshot(app,inspect(app));
        });
    }

    static void background(){}

    static void resumed(Activity activity) {
        initialize(activity);
    }

    static void paused(Activity activity){}

    static void refreshAsync(Context context) {
        if(context==null)return;
        Context app=context.getApplicationContext();
        Context target=app==null?context:app;
        WORKER.execute(()->writeSnapshot(target,inspect(target)));
    }

    static String status(Context context) {
        if(context==null)return "";
        if(!CalendarSync.permitted(context))return describeStatus(false,false,false,false,0,0,0);
        SharedPreferences prefs=context.getSharedPreferences(STORE,Context.MODE_PRIVATE);
        String saved=prefs.getString(KEY_SUMMARY,"");
        return saved.isBlank()?describeStatus(true,true,false,true,0,0,0):saved;
    }

    static String describeStatus(
        boolean permitted,
        boolean hasCalendar,
        boolean localOnly,
        boolean hasXiaomiOption,
        int subscribed,
        int pendingUpload,
        int pendingDelete
    ) {
        if(!permitted)return "未开启日历权限 · 点此开启跨设备同步";
        if(!hasCalendar)return "未找到可写日历 · 请在系统日历启用账户";
        if(localOnly)return hasXiaomiOption?"当前为本机日历 · 点此切换至小米日历":"本机日历 · 登录小米账户后可跨设备同步";
        if(pendingUpload+pendingDelete>0)return "小米云同步中 · 约 1–3 分钟送达其他设备";
        if(subscribed>0)return "小米日历自动同步 · "+subscribed+" 场订阅已上云";
        return "小米日历自动同步 · 手机与平板自动互通";
    }

    static void changed(Context context,long calendar) {
        if(calendar<0||context==null)return;
        initialize(context);
        Context app=application;
        if(app==null)return;
        WORKER.execute(()->{
            if(!CalendarSync.permitted(app))return;
            requestSystemSync(app,calendar);
            writeSnapshot(app,inspect(app));
        });
    }

    static void manage(Activity activity,Runnable onUpdated) {
        if(activity==null||activity.isFinishing()||activity.isDestroyed())return;
        initialize(activity);
        Context app=activity.getApplicationContext();
        WORKER.execute(()->{
            CalendarSync sync=new CalendarSync(app);
            if(CalendarSync.permitted(app)) {
                sync.recoverLocal();
                CalendarJob.schedule(app);
                requestSystemSync(app,sync.calendarId());
            }
            Snapshot snap=inspect(app);
            writeSnapshot(app,snap);
            MAIN.post(()->{
                if(activity.isFinishing()||activity.isDestroyed())return;
                if(onUpdated!=null)onUpdated.run();
                if(snap.localOnly&&snap.hasXiaomiOption&&snap.xiaomiCalendarId>=0) {
                    Sheet.confirm(
                        activity,
                        "切换至小米日历",
                        "当前接收日历为「"+snap.calendarName+"」（仅保存在本机，不会上传云端）。\n\n切换为「"+snap.xiaomiCalendarName+"」后，手机与平板登录同一小米账户即可自动同步比赛订阅，无需 Shizuku。",
                        "切换至小米日历",
                        ()->{
                            sync.chooseCalendar(snap.xiaomiCalendarId,snap.xiaomiCalendarName);
                            opened(activity);
                            if(onUpdated!=null)onUpdated.run();
                        }
                    );
                    return;
                }
                StringBuilder body=new StringBuilder();
                body.append("当前接收日历：").append(snap.calendarName.isBlank()?"未选择":snap.calendarName).append('\n');
                if(snap.localOnly) {
                    body.append("同步状态：当前为本机日历，仅在本设备生效。在系统日历登录小米账户后即可跨设备同步。\n\n");
                } else if(snap.pendingUpload+snap.pendingDelete>0) {
                    body.append("同步状态：本机共 ").append(snap.subscribed).append(" 场订阅，其中 ")
                        .append(snap.pendingUpload+snap.pendingDelete).append(" 项改动正等待系统日历上云。\n\n");
                } else {
                    body.append("同步状态：本机已识别 ").append(snap.subscribed).append(" 场有效订阅，云端状态已对齐。\n\n");
                }
                body.append("跨设备同步时间与规则（无需 Shizuku）：\n")
                    .append("1. 订阅或取消后，系统日历通常在约 45 秒–3 分钟内自动上传至小米云，并通过系统推送在数秒内下发到同一小米账户的手机或平板；\n")
                    .append("2. 另一台设备开启日历权限后，前台运行会自动点亮或取消订阅图标，后台则在打开应用时立即认领并安排完场赛果通知；\n")
                    .append("3. 若想立即同步，可点上方「打开系统日历」下拉刷新一次小米日历。");
                Sheet.message(activity,"日历云同步",body.toString());
            });
        });
    }

    private static Snapshot inspect(Context app) {
        if(app==null||!CalendarSync.permitted(app)) {
            return new Snapshot(false,false,false,false,"",-1L,"",0,0,0);
        }
        CalendarSync sync=new CalendarSync(app);
        List<CalendarSync.CalendarChoice> choices=sync.calendars();
        long selectedId=sync.calendarId();
        CalendarSync.CalendarChoice selected=null;
        CalendarSync.CalendarChoice xiaomiChoice=null;
        Set<Long> localCalendars=new HashSet<>();
        for(CalendarSync.CalendarChoice c:choices) {
            if(c.local)localCalendars.add(c.id);
            if(c.xiaomi&&xiaomiChoice==null)xiaomiChoice=c;
            if(c.id==selectedId)selected=c;
        }
        if(selected==null&&!choices.isEmpty()) {
            selected=choices.get(0);
            if(selectedId<0)sync.chooseCalendar(selected.id,selected.name);
        } else if(selected==null&&selectedId>=0&&!sync.calendarName().isBlank()) {
            String remembered=sync.calendarName();
            boolean xiaomi=remembered.contains("小米");
            boolean local=!xiaomi&&remembered.contains("本机");
            selected=new CalendarSync.CalendarChoice(selectedId,remembered,"",local,xiaomi);
            if(local)localCalendars.add(selectedId);
        }
        if(selected==null) {
            return new Snapshot(true,false,false,false,"",-1L,"",sync.linkedEvents(),0,0);
        }
        Set<String> subscribedIds=sync.subscribedIds();
        int pendingUpload=0,pendingDelete=0;
        try(Cursor rows=app.getContentResolver().query(
            CalendarContract.Events.CONTENT_URI,
            new String[]{
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events._SYNC_ID,
                CalendarContract.Events.DIRTY,
                CalendarContract.Events.DELETED,
                CalendarContract.Events.CALENDAR_ID
            },
            CalendarContract.Events.CUSTOM_APP_PACKAGE+"=? AND "+CalendarContract.Events.DESCRIPTION+" LIKE ?",
            new String[]{app.getPackageName(),"MyScore:%"},
            null
        )) {
            if(rows!=null)while(rows.moveToNext()) {
                long calId=rows.getLong(4);
                if(localCalendars.contains(calId))continue;
                String desc=rows.getString(0);
                if(desc==null||!desc.startsWith("MyScore:"))continue;
                int nl=desc.indexOf('\n');
                if(nl<=8)continue;
                String matchId=desc.substring(8,nl);
                String syncId=rows.isNull(1)?"":rows.getString(1);
                boolean dirty=rows.getInt(2)!=0;
                boolean deleted=rows.getInt(3)!=0;
                if(deleted) {
                    if(dirty)pendingDelete++;
                } else if(subscribedIds.contains(matchId)) {
                    if(dirty||syncId==null||syncId.isBlank())pendingUpload++;
                }
            }
        } catch(RuntimeException ignored) {}
        return new Snapshot(
            true,
            true,
            selected.local,
            xiaomiChoice!=null,
            selected.name,
            xiaomiChoice==null?-1L:xiaomiChoice.id,
            xiaomiChoice==null?"":xiaomiChoice.name,
            subscribedIds.size(),
            pendingUpload,
            pendingDelete
        );
    }

    private static void writeSnapshot(Context app,Snapshot snap) {
        if(app==null||snap==null)return;
        SharedPreferences prefs=app.getSharedPreferences(STORE,Context.MODE_PRIVATE);
        String next=snap.status();
        if(!next.equals(prefs.getString(KEY_SUMMARY,""))||prefs.contains("error")||prefs.contains("connection")) {
            prefs.edit()
                .putString(KEY_SUMMARY,next)
                .remove("connection")
                .remove("error")
                .putLong("sent",prefs.getLong("generation",0))
                .apply();
        }
    }

    private static void requestSystemSync(Context app,long targetCalendar) {
        if(app==null||!CalendarSync.permitted(app))return;
        Bundle extras=new Bundle();
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL,true);
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED,true);
        boolean requestedAccount=false;
        String selection=targetCalendar>=0?"_id=? AND sync_events=1":"sync_events=1";
        String[] args=targetCalendar>=0?new String[]{Long.toString(targetCalendar)}:null;
        try(Cursor rows=app.getContentResolver().query(
            CalendarContract.Calendars.CONTENT_URI,
            new String[]{CalendarContract.Calendars.ACCOUNT_NAME,CalendarContract.Calendars.ACCOUNT_TYPE},
            selection,args,null
        )) {
            if(rows!=null) {
                Set<String> seen=new HashSet<>();
                while(rows.moveToNext()) {
                    String name=rows.getString(0),type=rows.getString(1);
                    if(name==null||name.isBlank()||type==null||type.isBlank())continue;
                    if(CalendarContract.ACCOUNT_TYPE_LOCAL.equals(type)||"LOCAL".equalsIgnoreCase(type))continue;
                    if(!seen.add(type+"|"+name))continue;
                    try {
                        ContentResolver.requestSync(new Account(name,type),CalendarContract.AUTHORITY,new Bundle(extras));
                        requestedAccount=true;
                    } catch(RuntimeException ignored) {}
                }
            }
        } catch(RuntimeException ignored) {}
        if(!requestedAccount) {
            try {
                ContentResolver.requestSync(null,CalendarContract.AUTHORITY,extras);
            } catch(RuntimeException ignored) {}
        }
    }
}
