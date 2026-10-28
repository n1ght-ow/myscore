package cn.myscore.app;

import android.app.DownloadManager;
import android.app.Notification;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** User-started download continues with the app's network after leaving the update page. */
public final class UpdateService extends Service {
    private static final String CHANNEL="app-updates";
    private static final int NOTIFICATION=24702;
    private static volatile String active="";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Future<?> task;
    private String token="";
    private UpdateRepository repository;
    private NotificationManager notifications;
    static boolean running(String token){return !token.isEmpty()&&token.equals(active);}
    @Override public void onCreate(){super.onCreate();repository=new UpdateRepository(this);notifications=getSystemService(NotificationManager.class);notifications.createNotificationChannel(new NotificationChannel(CHANNEL,"应用更新",NotificationManager.IMPORTANCE_LOW));}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String requested=intent==null?repository.token():intent.getStringExtra("token");
        if(requested==null||!repository.owns(requested)){stopSelf(startId);return START_NOT_STICKY;}
        if(repository.preferences.getInt("status",0)==DownloadManager.STATUS_SUCCESSFUL){stopSelf(startId);return START_NOT_STICKY;}
        if(requested.equals(token)&&task!=null&&!task.isDone())return START_REDELIVER_INTENT;
        if(task!=null)task.cancel(true);
        token=requested;active=requested;
        try {
            foreground(notification(0,0));
            task=worker.submit(()->download(requested,startId));
        } catch(RuntimeException e){repository.changed(requested,DownloadManager.STATUS_FAILED,0,"无法启动后台下载，请返回更新页重试");stopSelf(startId);}
        return START_REDELIVER_INTENT;
    }
    private Notification notification(long bytes,long total){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,UpdateActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        int percent=total<=0?0:(int)Math.min(100,bytes*100/total);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("MyScore 更新下载").setContentText(total<=0?"正在连接下载服务器…":"已下载 "+percent+"%").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setProgress(100,percent,total<=0).build();
    }
    private void foreground(Notification notification){if(android.os.Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);else startForeground(NOTIFICATION,notification);}
    @SuppressLint("UsableSpace")
    private void download(String requested,int startId){
        File partial=null;
        try {
            UpdateRelease release=repository.pending();if(release==null)throw new java.io.IOException("下载信息已失效，请重新检查更新");
            File destination=repository.file(release);partial=new File(destination.getParentFile(),destination.getName()+"."+requested+".part");
            long usable=partial.getParentFile()==null?0:partial.getParentFile().getUsableSpace();
            if(usable>0&&usable<release.size+1024*1024)throw new java.io.IOException("存储空间不足，请清理后重新下载");
            final long[] last={0};
            for(int attempt=0;attempt<3;attempt++) {
                if(!repository.owns(requested)||Thread.currentThread().isInterrupted())return;
                repository.changed(requested,DownloadManager.STATUS_PENDING,0,"");
                try {
                    UpdateTransfer.download(release,partial,bytes->{
                        long now=android.os.SystemClock.elapsedRealtime();
                        if(now-last[0]>=1000||bytes==release.size){last[0]=now;repository.changed(requested,DownloadManager.STATUS_RUNNING,bytes,"");if(running(requested))foreground(notification(bytes,release.size));}
                    },()->!repository.owns(requested),attempt==1,true);
                    synchronized(UpdateRepository.LOCK) {
                        if(!repository.owns(requested)||Thread.currentThread().isInterrupted())return;
                        if(!partial.renameTo(destination))throw new java.io.IOException("无法保存安装包，请检查存储空间后重试");
                        repository.changed(requested,DownloadManager.STATUS_SUCCESSFUL,release.size,"");
                    }
                    return;
                } catch(java.io.IOException e) {
                    if(attempt==2||!retryable(e)||Thread.currentThread().isInterrupted()||!repository.owns(requested))throw e;
                    Thread.sleep((attempt+1)*1500L);
                }
            }
        } catch(Exception e){repository.changed(requested,DownloadManager.STATUS_FAILED,0,UpdateTransfer.failure(e));}
        finally {
            if(partial!=null&&partial.exists())partial.delete();
            if(requested.equals(active))active="";
            stopSelf(startId);
        }
    }
    private static boolean retryable(java.io.IOException error) {
        if (error instanceof java.net.SocketTimeoutException) return true;
        if (error instanceof java.io.InterruptedIOException) return false;
        if (error instanceof java.net.UnknownHostException
            || error instanceof java.net.ConnectException
            || error instanceof java.net.NoRouteToHostException
            || error instanceof javax.net.ssl.SSLException) return true;
        String message=error.getMessage();
        if (message!=null&&(message.contains("安装包")||message.contains("校验")
            ||message.contains("大小")||message.contains("跳转")
            ||message.contains("失效")||message.contains("无法保存")
            ||message.contains("HTTP 4"))) return false;
        return true;
    }
    @Override public void onTimeout(int startId,int fgsType){repository.changed(token,DownloadManager.STATUS_FAILED,0,"下载超时，请返回更新页重新下载");stopSelf();}
    @Override public void onDestroy(){if(token.equals(active))active="";if(task!=null)task.cancel(true);worker.shutdownNow();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}
