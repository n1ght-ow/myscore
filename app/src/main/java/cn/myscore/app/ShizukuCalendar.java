package cn.myscore.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import rikka.shizuku.Shizuku;

/** Permission and connection lifecycle. It never runs arbitrary shell commands. */
final class ShizukuCalendar {
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static Context app;
    private static volatile ICalendarCloud service;
    private static volatile String label="正在连接 Shizuku";
    private static boolean initialized,binding,asked;
    private static long attempt;
    private static Shizuku.UserServiceArgs args;
    private static final ServiceConnection connection=new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name,IBinder binder) {
            binding=false;attempt++;service=ICalendarCloud.Stub.asInterface(binder);label="主动同步已连接";
            CalendarCloudSync.connected(app);
        }
        @Override public void onServiceDisconnected(ComponentName name) {binding=false;service=null;label="同步连接已断开";CalendarCloudSync.refresh(app);}
    };
    static synchronized void initialize(Context context) {
        if(initialized)return;initialized=true;app=context.getApplicationContext();
        args=new Shizuku.UserServiceArgs(new ComponentName(app,CalendarCloudService.class)).tag("myscore-calendar-cloud")
            .processNameSuffix("calendar_sync").daemon(false).debuggable(BuildConfig.DEBUG).version(1);
        Shizuku.addBinderReceivedListenerSticky(()->MAIN.post(ShizukuCalendar::connect));
        Shizuku.addBinderDeadListener(()->MAIN.post(()->{binding=false;attempt++;service=null;label="Shizuku 已停止";CalendarCloudSync.refresh(app);}));
        Shizuku.addRequestPermissionResultListener((code,result)->{if(code==24703)MAIN.post(ShizukuCalendar::connect);});
        MAIN.post(ShizukuCalendar::connect);
    }
    private static void connect() {
        try {
            if(!Shizuku.pingBinder()){service=null;label="请启动 Shizuku";CalendarCloudSync.refresh(app);return;}
            if(Shizuku.getVersion()<13){label="请更新 Shizuku";CalendarCloudSync.refresh(app);return;}
            if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){service=null;label="请授权日历同步";CalendarCloudSync.refresh(app);return;}
            if(service!=null&&service.asBinder().pingBinder())return;
            if(binding)return;binding=true;long token=++attempt;label="正在连接主动同步";
            CalendarCloudSync.refresh(app);
            Shizuku.bindUserService(args,connection);
            MAIN.postDelayed(()->{if(binding&&attempt==token){binding=false;label="同步连接超时，点此重试";CalendarCloudSync.refresh(app);}},10000);
        }catch(RuntimeException e){binding=false;service=null;label="同步连接不可用，点此重试";CalendarCloudSync.refresh(app);}
    }
    static void resumed(Activity activity) {
        initialize(activity);
        try {
            if(Shizuku.pingBinder()&&Shizuku.getVersion()>=13&&Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED
                &&!asked&&!Shizuku.shouldShowRequestPermissionRationale()){asked=true;Shizuku.requestPermission(24703);}
            else MAIN.post(ShizukuCalendar::connect);
        }catch(RuntimeException ignored){}
    }
    static String status(){return label;}
    static void reconnect(){MAIN.post(ShizukuCalendar::connect);}
    static boolean ready(){return service!=null&&service.asBinder().pingBinder();}
    static String request()throws Exception {
        if(!Shizuku.pingBinder()||Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED) {
            service=null;MAIN.post(ShizukuCalendar::connect);throw new SecurityException("Shizuku permission is unavailable");
        }
        ICalendarCloud current=service;
        if(current==null||!current.asBinder().pingBinder()){MAIN.post(ShizukuCalendar::connect);throw new IllegalStateException("Shizuku not connected");}
        try{return current.requestSync();}
        catch(android.os.DeadObjectException e){service=null;MAIN.post(ShizukuCalendar::connect);throw e;}
    }
    static void manage(Activity activity) {
        initialize(activity);
        try {
            if(Shizuku.pingBinder()) {
                if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){asked=true;Shizuku.requestPermission(24703);}
                else {connect();CalendarCloudSync.opened(activity);}
                return;
            }
        }catch(RuntimeException ignored){}
        Intent launch=activity.getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        try {activity.startActivity(launch!=null?launch:new Intent(Intent.ACTION_VIEW,Uri.parse("https://shizuku.rikka.app/download/")));}
        catch(RuntimeException e){Sheet.message(activity,"日历云同步", "请在 Shizuku 中启动服务并授权 MyScore。本机日历改动会保留。设备重启后请确认 Shizuku 已运行。");}
    }
}
