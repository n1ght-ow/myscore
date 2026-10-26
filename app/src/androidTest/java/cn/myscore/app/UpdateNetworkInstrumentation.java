package cn.myscore.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.Instrumentation;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import org.json.JSONObject;

/** Explicit updater-only diagnostic. Uses temporary downloads without touching app preferences. */
public final class UpdateNetworkInstrumentation extends Instrumentation {
    private final StringBuilder report=new StringBuilder();
    private String mode="native";
    private long cleanupId;
    private volatile Activity opened;
    @Override public void callActivityOnCreate(Activity activity,Bundle state){super.callActivityOnCreate(activity,state);opened=activity;}
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);if(arguments!=null){mode=arguments.getString("mode","native");cleanupId=Long.parseLong(arguments.getString("downloadId","0"));}start();}
    private void log(String value){report.append(value).append('\n');android.util.Log.i("MyScoreUpdateProbe",value);Bundle status=new Bundle();status.putString("stream",value+"\n");sendStatus(0,status);}
    @Override public void onStart(){
        Bundle result=new Bundle();int outcome=Activity.RESULT_OK;
        try{
            if(mode.equals("cleanup")){cleanupDiagnostic();result.putString("stream",report.toString());finish(outcome,result);return;}
            log("uid="+android.os.Process.myUid());
            JSONObject root=new JSONObject(Repository.get(UpdateRelease.LATEST_API,java.util.Map.of("Accept","application/vnd.github+json","User-Agent","MyScore/2.2")));
            UpdateRelease release=UpdateRelease.parse(root);
            JSONObject asset=null;
            for(int i=0;i<root.getJSONArray("assets").length();i++){
                JSONObject candidate=root.getJSONArray("assets").getJSONObject(i);
                if(candidate.getString("browser_download_url").equals(release.url))asset=candidate;
            }
            if(asset==null)throw new IllegalStateException("Missing APK asset");
            String api=asset.getString("url");
            log("release="+release.version+" bytes="+release.size);
            if(mode.equals("native"))nativeDownload(release);
            else {
                probe("browser",release.url,release);probe("asset-api",api,release);
                if(mode.equals("system")){systemDownload("browser",release.url,false);systemDownload("asset-api",api,true);}
            }
        }catch(Throwable e){outcome=Activity.RESULT_CANCELED;log("ERROR "+e.getClass().getSimpleName()+": "+e.getMessage());}
        result.putString("stream","\n"+report);finish(outcome,result);
    }
    private void cleanupDiagnostic(){
        if(cleanupId<=0)throw new IllegalArgumentException("Expected a known diagnostic download ID");
        DownloadManager manager=getTargetContext().getSystemService(DownloadManager.class);
        try(Cursor cursor=manager.query(new DownloadManager.Query().setFilterById(cleanupId))){
            if(cursor==null||!cursor.moveToFirst()){log("diagnostic download already removed");return;}
            String title=cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE));
            String local=cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
            if(!"MyScore update connection check".equals(title)||local==null||!local.contains("/cn.myscore.app/files/updater-diagnostic/"))throw new IllegalStateException("Refusing to remove a non-diagnostic download");
        }
        require(manager.remove(cleanupId)==1,"temporary system diagnostic download removed");
    }
    private void require(boolean value,String message){if(!value)throw new AssertionError(message);log("PASS "+message);}
    private void nativeDownload(UpdateRelease release)throws Exception {
        android.content.Context context=getTargetContext();UpdateRepository repository=new UpdateRepository(context);
        java.util.Map<String,?> original=new java.util.HashMap<>(repository.preferences.getAll());
        if(repository.downloadId()!=0)throw new IllegalStateException("An existing user download must finish before this diagnostic");
        File file=repository.file(release);if(file.exists())throw new IllegalStateException("Existing update file is preserved; cannot use it as a diagnostic target");
        if(!file.getParentFile().isDirectory()&&!file.getParentFile().mkdirs())throw new java.io.IOException("Cannot create diagnostic download directory");
        String token=java.util.UUID.randomUUID().toString();Activity activity=null;
        try {
            log("waiting for MyScore foreground activity");
            context.startActivity(new android.content.Intent(context,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            long foregroundDeadline=SystemClock.elapsedRealtime()+30000;
            while(opened==null&&SystemClock.elapsedRealtime()<foregroundDeadline)Thread.sleep(50);
            if(opened==null)throw new IllegalStateException("Open MyScore before running the transfer diagnostic");
            activity=opened;waitForIdleSync();
            repository.preferences.edit().putString("release",release.saved()).putLong("download_id",-1).putString("transfer_token",token).putInt("status",DownloadManager.STATUS_PENDING).putLong("updated",System.currentTimeMillis()).commit();
            context.startForegroundService(new android.content.Intent(context,UpdateService.class).putExtra("token",token));
            long deadline=SystemClock.elapsedRealtime()+10000;
            while(!UpdateService.running(token)&&repository.progress().status()!=DownloadManager.STATUS_SUCCESSFUL&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(50);
            require(UpdateService.running(token)||repository.progress().status()==DownloadManager.STATUS_SUCCESSFUL,"app-owned transfer starts on this device");
            Activity launched=activity;runOnMainSync(launched::finish);activity=null;
            long started=SystemClock.elapsedRealtime();deadline=started+120000;long lastBytes=-1;
            while(SystemClock.elapsedRealtime()<deadline){
                UpdateRepository.Progress progress=repository.progress();
                if(progress.downloaded()!=lastBytes){log("native bytes="+progress.downloaded()+" status="+progress.status());lastBytes=progress.downloaded();}
                if(progress.status()==DownloadManager.STATUS_SUCCESSFUL)break;
                Thread.sleep(100);
            }
            require(repository.progress().status()==DownloadManager.STATUS_SUCCESSFUL,"native download finishes after leaving the app");
            release.verifyFile(file);require(file.length()==release.size,"downloaded release size and SHA-256 match GitHub metadata");
            log("native completed elapsedMs="+(SystemClock.elapsedRealtime()-started));
            UpdateRelease restored=new UpdateRepository(context).pending();require(restored!=null&&restored.version.equals(release.version),"download state survives repository recreation");
            android.content.pm.PackageInfo archive=context.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
            android.content.pm.PackageInfo installed=context.getPackageManager().getPackageInfo(context.getPackageName(),android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
            require(archive!=null&&installed.packageName.equals(archive.packageName)&&java.util.Arrays.equals(archive.signingInfo.getApkContentsSigners(),installed.signingInfo.getApkContentsSigners()),"real downloaded APK has the installed app package and signing certificate");
            if(archive.getLongVersionCode()<=installed.getLongVersionCode()){
                try{UpdateRepository.validateArchive(archive,installed,release.version);throw new AssertionError("Downgrade accepted");}catch(java.io.IOException expected){log("PASS current or older release remains blocked from installation");}
            }
            repository.preferences.edit().putString("verified",release.sha256).commit();
            Uri uri=new Uri.Builder().scheme("content").authority(context.getPackageName()+".updates").appendPath("apk").appendPath(release.version).build();
            try(InputStream input=context.getContentResolver().openInputStream(uri)){require(input!=null&&input.read()==0x50&&input.read()==0x4b,"verified APK is readable through the installer content URI");}
            try{context.getContentResolver().openFileDescriptor(uri,"w");throw new AssertionError("Writable update URI accepted");}catch(java.io.FileNotFoundException expected){log("PASS installer content URI refuses writes");}
            try{context.getContentResolver().openInputStream(uri.buildUpon().appendPath("../settings.xml").build());throw new AssertionError("Traversal accepted");}catch(java.io.FileNotFoundException expected){log("PASS installer content URI refuses paths outside the verified APK");}
            repository.preferences.edit().remove("verified").commit();
            try{context.getContentResolver().openInputStream(uri);throw new AssertionError("Unverified APK accepted");}catch(java.io.FileNotFoundException expected){log("PASS unverified APK cannot be opened by the installer provider");}
        } finally {
            repository.clear();if(activity!=null){Activity launched=activity;runOnMainSync(launched::finish);}
            android.content.SharedPreferences.Editor editor=repository.preferences.edit().clear();
            for(java.util.Map.Entry<String,?> entry:original.entrySet()){Object value=entry.getValue();if(value instanceof String)editor.putString(entry.getKey(),(String)value);else if(value instanceof Long)editor.putLong(entry.getKey(),(Long)value);else if(value instanceof Integer)editor.putInt(entry.getKey(),(Integer)value);else if(value instanceof Boolean)editor.putBoolean(entry.getKey(),(Boolean)value);}
            editor.commit();if(file.exists())file.delete();
        }
    }
    private void probe(String name,String address,UpdateRelease release){
        long started=SystemClock.elapsedRealtime();
        try{
            URL url=new URL(address);
            for(int redirects=0;redirects<=5;redirects++){
                HttpURLConnection connection=(HttpURLConnection)url.openConnection();
                try{
                    connection.setConnectTimeout(12000);connection.setReadTimeout(12000);connection.setInstanceFollowRedirects(false);
                    connection.setRequestProperty("User-Agent","MyScore/2.2 Android");connection.setRequestProperty("Accept","application/octet-stream");
                    int code=connection.getResponseCode();log(name+" host="+url.getHost()+" HTTP="+code);
                    if(code>=300&&code<400){url=new URL(url,connection.getHeaderField("Location"));continue;}
                    if(code!=200)return;
                    MessageDigest digest=MessageDigest.getInstance("SHA-256");long count=0;
                    try(InputStream input=connection.getInputStream()){
                        byte[] buffer=new byte[32768];int read;
                        while((read=input.read(buffer))!=-1){count+=read;if(count>release.size||SystemClock.elapsedRealtime()-started>45000)throw new java.io.IOException("Download bound exceeded");digest.update(buffer,0,read);}
                    }
                    StringBuilder actual=new StringBuilder();for(byte value:digest.digest())actual.append(String.format(java.util.Locale.ROOT,"%02x",value));
                    log(name+" bytes="+count+" sha256Matches="+(count==release.size&&release.sha256.equals(actual.toString()))+" elapsedMs="+(SystemClock.elapsedRealtime()-started));return;
                }finally{connection.disconnect();}
            }
            log(name+" too many redirects");
        }catch(Exception e){log(name+" "+e.getClass().getSimpleName()+" elapsedMs="+(SystemClock.elapsedRealtime()-started));}
    }
    private void systemDownload(String name,String address,boolean binary)throws Exception{
        DownloadManager manager=getTargetContext().getSystemService(DownloadManager.class);
        File directory=new File(getTargetContext().getExternalFilesDir(null),"updater-diagnostic");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new java.io.IOException("Cannot create diagnostic directory");
        File file=new File(directory,name+"-"+System.currentTimeMillis()+".apk");
        long id=0;int lastStatus=-1,lastReason=-1;long lastBytes=-1;
        try{
            DownloadManager.Request request=new DownloadManager.Request(Uri.parse(address)).setTitle("MyScore update connection check").setAllowedOverMetered(true).setAllowedOverRoaming(false).setDestinationUri(Uri.fromFile(file));
            if(binary)request.addRequestHeader("Accept","application/octet-stream").addRequestHeader("User-Agent","MyScore/2.2 Android");
            id=manager.enqueue(request);long deadline=SystemClock.elapsedRealtime()+35000;
            while(SystemClock.elapsedRealtime()<deadline){
                try(Cursor cursor=manager.query(new DownloadManager.Query().setFilterById(id))){
                    if(cursor==null||!cursor.moveToFirst()){log("system-"+name+" missing");return;}
                    int status=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    int reason=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                    long bytes=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    if(status!=lastStatus||reason!=lastReason||bytes!=lastBytes){log("system-"+name+" status="+status+" reason="+reason+" bytes="+bytes);lastStatus=status;lastReason=reason;lastBytes=bytes;}
                    if(status==DownloadManager.STATUS_FAILED||status==DownloadManager.STATUS_SUCCESSFUL)return;
                }
                Thread.sleep(1000);
            }
            log("system-"+name+" still waiting after 35s");
        }finally{if(id!=0)manager.remove(id);if(file.exists()&&!file.delete())log("diagnostic file cleanup failed");}
    }
}
