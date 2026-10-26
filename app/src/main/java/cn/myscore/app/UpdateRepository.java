package cn.myscore.app;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;

/** App-owned background transfer; legacy system downloads never block the updater. */
final class UpdateRepository {
    static final Object LOCK=new Object();
    private static final java.util.concurrent.ExecutorService LEGACY_CLEANUP=java.util.concurrent.Executors.newSingleThreadExecutor(task->{Thread thread=new Thread(task,"old-update-cleanup");thread.setDaemon(true);return thread;});
    record Progress(int status,long downloaded,long total,int reason){}
    private final Context context;
    final SharedPreferences preferences;
    private final DownloadManager downloads;
    UpdateRepository(Context context){this.context=context.getApplicationContext();preferences=this.context.getSharedPreferences("app-updates",0);downloads=this.context.getSystemService(DownloadManager.class);}
    UpdateRelease latest()throws Exception {
        return UpdateRelease.parse(new JSONObject(Repository.get(UpdateRelease.LATEST_API,Map.of("Accept","application/vnd.github+json","User-Agent","MyScore/"+BuildConfig.VERSION_NAME))));
    }
    long downloadId(){return preferences.getLong("download_id",0);}
    UpdateRelease pending(){try{return UpdateRelease.restore(preferences.getString("release",""));}catch(Exception e){return null;}}
    File file(UpdateRelease release)throws IOException {
        return new File(new File(context.getFilesDir(),"updates"),"MyScore-"+release.version+".apk");
    }
    long start(UpdateRelease release)throws Exception {
        if(!release.newerThan(BuildConfig.VERSION_NAME))throw new IOException("当前已是最新版本");
        synchronized(LOCK) {
        clear();File file=file(release);File directory=file.getParentFile();
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法创建更新下载目录");
        File[] old=directory.listFiles();if(old!=null)for(File entry:old)if(entry.isFile()&&entry.getName().matches("MyScore-[0-9]+\\.[0-9]+(?:\\.[0-9]+)?\\.apk(?:\\.[a-f0-9-]{36}\\.part)?")&&!entry.delete())throw new IOException("无法清理旧安装包，请稍后重试");
        String token=java.util.UUID.randomUUID().toString();
        if(!preferences.edit().putString("release",release.saved()).putLong("download_id",-1).putString("transfer_token",token).putInt("status",DownloadManager.STATUS_PENDING).putLong("bytes",0).putLong("updated",System.currentTimeMillis()).commit())throw new IOException("无法保存下载任务，请重试");
        try {context.startForegroundService(new Intent(context,UpdateService.class).putExtra("token",token));}
        catch(RuntimeException e){preferences.edit().clear().commit();throw new IOException("无法启动下载，请返回更新页重新下载",e);}
        return -1;
        }
    }
    Progress progress()throws IOException {
        synchronized(LOCK) {
            long id=downloadId();if(id==0)throw new IOException("下载任务已不存在，请重新下载");
            if(id>0)throw new IOException("旧版系统下载任务已暂停，请点重新下载以使用修复后的下载方式");
            int status=preferences.getInt("status",DownloadManager.STATUS_FAILED);
            if(status==DownloadManager.STATUS_FAILED)throw new IOException(preferences.getString("error","下载已中断，请重新下载"));
            UpdateRelease release=pending();if(release==null)throw new IOException("下载信息已失效，请重新检查更新");
            if(status!=DownloadManager.STATUS_SUCCESSFUL&&!UpdateService.running(token())&&System.currentTimeMillis()-preferences.getLong("updated",0)>30000)throw new IOException("下载已中断，请重新下载");
            return new Progress(status,preferences.getLong("bytes",0),release.size,0);
        }
    }
    Uri verify(UpdateRelease release)throws Exception {
        try{
            if(progress().status!=DownloadManager.STATUS_SUCCESSFUL)throw new IOException("安装包尚未下载完成");
            File file=file(release);release.verifyFile(file);
            PackageManager manager=context.getPackageManager();
            PackageInfo archive=manager.getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);
            PackageInfo installed=manager.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
            validateArchive(archive,installed,release.version);
            if(!preferences.edit().putString("verified",release.sha256).commit())throw new IOException("无法保存安装包校验状态，请重试");
            return new Uri.Builder().scheme("content").authority(context.getPackageName()+".updates").appendPath("apk").appendPath(release.version).build();
        }catch(Exception e){clear();throw e;}
    }
    static void validateArchive(PackageInfo archive,PackageInfo installed,String version)throws IOException {
        if(archive==null||!installed.packageName.equals(archive.packageName)||!version.equals(archive.versionName))throw new IOException("安装包与 MyScore 版本不匹配");
        if(archive.getLongVersionCode()<=installed.getLongVersionCode())throw new IOException("此安装包不是更新版本");
        if(archive.signingInfo==null||installed.signingInfo==null||!signers(archive.signingInfo.getApkContentsSigners()).equals(signers(installed.signingInfo.getApkContentsSigners())))throw new IOException("安装包签名不一致，已停止安装");
    }
    private static Set<String> signers(Signature[] signatures)throws IOException {
        if(signatures==null||signatures.length==0)throw new IOException("安装包缺少签名");
        Set<String> values=new HashSet<>();for(Signature signature:signatures)values.add(Arrays.toString(signature.toByteArray()));return values;
    }
    String token(){return preferences.getString("transfer_token","");}
    boolean owns(String token){return !token.isEmpty()&&token.equals(token());}
    void changed(String token,int status,long bytes,String error){synchronized(LOCK){if(owns(token))preferences.edit().putInt("status",status).putLong("bytes",bytes).putLong("updated",System.currentTimeMillis()).putString("error",error).apply();}}
    void clear(){synchronized(LOCK){long id=downloadId();preferences.edit().clear().commit();if(id<0)context.stopService(new Intent(context,UpdateService.class));if(downloads!=null&&id>0)LEGACY_CLEANUP.submit(()->{try{downloads.remove(id);}catch(RuntimeException ignored){}});}}
}
