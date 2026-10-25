package cn.myscore.app;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;

/** DownloadManager owns the transfer; no storage permission or custom file provider. */
final class UpdateRepository {
    record Progress(int status,long downloaded,long total,int reason){}
    private final Context context;
    private final SharedPreferences preferences;
    private final DownloadManager downloads;
    UpdateRepository(Context context){this.context=context.getApplicationContext();preferences=this.context.getSharedPreferences("app-updates",0);downloads=this.context.getSystemService(DownloadManager.class);}
    UpdateRelease latest()throws Exception {
        return UpdateRelease.parse(new JSONObject(Repository.get(UpdateRelease.LATEST_API,Map.of("Accept","application/vnd.github+json","User-Agent","MyScore/"+BuildConfig.VERSION_NAME))));
    }
    long downloadId(){return preferences.getLong("download_id",0);}
    UpdateRelease pending(){try{return UpdateRelease.restore(preferences.getString("release",""));}catch(Exception e){return null;}}
    File file(UpdateRelease release)throws IOException {
        File directory=context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if(directory==null)throw new IOException("下载存储暂不可用，请稍后重试");
        return new File(new File(directory,"updates"),"MyScore-"+release.version+".apk");
    }
    long start(UpdateRelease release)throws Exception {
        if(downloads==null)throw new IOException("系统下载服务不可用，请在发布页下载");
        if(!release.newerThan(BuildConfig.VERSION_NAME))throw new IOException("当前已是最新版本");
        clear();File file=file(release);File directory=file.getParentFile();
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法创建更新下载目录");
        File[] old=directory.listFiles();if(old!=null)for(File entry:old)if(entry.isFile()&&entry.getName().matches("MyScore-[0-9]+\\.[0-9]+(?:\\.[0-9]+)?\\.apk")&&!entry.delete())throw new IOException("无法清理旧安装包，请稍后重试");
        DownloadManager.Request request=new DownloadManager.Request(Uri.parse(release.url));
        request.setTitle("MyScore "+release.version).setDescription("下载完成后返回 MyScore 安装更新")
            .setMimeType("application/vnd.android.package-archive").setAllowedOverMetered(true).setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context,Environment.DIRECTORY_DOWNLOADS,"updates/"+file.getName());
        long id=downloads.enqueue(request);
        if(!preferences.edit().putString("release",release.saved()).putLong("download_id",id).commit()){downloads.remove(id);throw new IOException("无法保存下载任务，请重试");}
        return id;
    }
    Progress progress()throws IOException {
        long id=downloadId();
        if(downloads==null||id==0)throw new IOException("下载任务已不存在，请重新下载");
        try(Cursor cursor=downloads.query(new DownloadManager.Query().setFilterById(id))){
            if(cursor==null||!cursor.moveToFirst()){clear();throw new IOException("下载任务已被移除，请重新下载");}
            Progress progress=new Progress(cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)));
            if(progress.status==DownloadManager.STATUS_FAILED){clear();throw new IOException("下载失败，请检查网络及存储空间后重试");}
            return progress;
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
            Uri uri=downloads.getUriForDownloadedFile(downloadId());
            if(uri==null||!"content".equals(uri.getScheme()))throw new IOException("无法打开安装包，请重新下载");
            return uri;
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
    void clear(){long id=downloadId();if(downloads!=null&&id!=0)downloads.remove(id);preferences.edit().clear().commit();}
}
