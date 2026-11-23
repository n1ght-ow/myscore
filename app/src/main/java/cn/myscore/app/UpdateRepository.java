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
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

/** App-owned background transfer; legacy system downloads never block the updater. */
final class UpdateRepository {
    static final Object LOCK=new Object();
    private static final Pattern TAG_PATH=Pattern.compile("/n1ght-ow/myscore/releases/tag/v([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)(?:[/?#]|$)");
    private static final Pattern CONTENT_RANGE=Pattern.compile("bytes\\s+0-0/([1-9][0-9]{0,12})");
    private static final java.util.concurrent.ExecutorService LEGACY_CLEANUP=java.util.concurrent.Executors.newSingleThreadExecutor(task->{Thread thread=new Thread(task,"old-update-cleanup");thread.setDaemon(true);return thread;});
    record Progress(int status,long downloaded,long total,int reason){}
    private final Context context;
    final SharedPreferences preferences;
    private final DownloadManager downloads;
    UpdateRepository(Context context){this.context=context.getApplicationContext();preferences=this.context.getSharedPreferences("app-updates",0);downloads=this.context.getSystemService(DownloadManager.class);}
    UpdateRelease latest()throws Exception {
        try {
            return UpdateRelease.parse(new JSONObject(Repository.get(UpdateRelease.LATEST_API,Map.of("Accept","application/vnd.github+json","User-Agent","MyScore/"+BuildConfig.VERSION_NAME))));
        } catch(Exception apiError) {
            try {
                return latestFromWeb();
            } catch(Exception webError) {
                throw apiError;
            }
        }
    }
    private UpdateRelease latestFromWeb()throws Exception {
        String[] tagAndNotes=resolveLatestTagAndNotes();
        String version=tagAndNotes[0],notes=tagAndNotes[1];
        String assetsHtml="";
        try {
            assetsHtml=Http.get(UpdateRelease.REPOSITORY+"/releases/expanded_assets/v"+version,
                Map.of("Accept","text/html","User-Agent","MyScore/"+BuildConfig.VERSION_NAME),
                "text/html",u->"https".equalsIgnoreCase(u.getProtocol())&&"github.com".equalsIgnoreCase(u.getHost()),false);
        } catch(Exception ignored) {}
        String sums="";
        if(!assetsHtml.contains("sha256:")) {
            try {
                sums=Http.get(UpdateRelease.REPOSITORY+"/releases/download/v"+version+"/SHA256SUMS-"+version+".txt",
                    Map.of("Accept","text/plain","User-Agent","MyScore/"+BuildConfig.VERSION_NAME),
                    "text/plain",UpdateTransfer::trustedHost,false);
            } catch(Exception ignored) {}
        }
        String apkUrl=UpdateRelease.REPOSITORY+"/releases/download/v"+version+"/MyScore-"+version+".apk";
        long size=probeAssetSize(apkUrl);
        return UpdateRelease.fromWeb(version,assetsHtml,sums,size,notes);
    }
    private String[] resolveLatestTagAndNotes()throws Exception {
        URL url=new URL(UpdateRelease.LATEST_WEB);
        for(int attempt=0;attempt<2;attempt++) {
            URL current=url;
            for(int redirects=0;redirects<=5;redirects++) {
                if(!"https".equalsIgnoreCase(current.getProtocol())||!"github.com".equalsIgnoreCase(current.getHost()))
                    throw new IOException("Release page redirect target is not trusted");
                HttpURLConnection con=Http.openDownload(current,attempt);
                try {
                    con.setInstanceFollowRedirects(false);
                    con.setConnectTimeout(6000);
                    con.setReadTimeout(8000);
                    con.setRequestProperty("Accept","text/html");
                    con.setRequestProperty("User-Agent","MyScore/"+BuildConfig.VERSION_NAME+" Android");
                    int code=con.getResponseCode();
                    if(code==301||code==302||code==303||code==307||code==308) {
                        String loc=con.getHeaderField("Location");
                        if(loc==null||loc.isBlank())throw new IOException("Release page redirect is missing a location");
                        current=URI.create(current.toString()).resolve(loc.trim()).toURL();
                        Matcher m=TAG_PATH.matcher(current.getPath());
                        if(m.find()) {
                            String ver=m.group(1);
                            String notes=extractNotes(current);
                            return new String[]{ver,notes};
                        }
                        continue;
                    }
                    break;
                } catch(IOException e) {
                    if(attempt==1)throw e;
                    break;
                } finally {
                    con.disconnect();
                }
            }
        }
        throw new IOException("Could not read the latest release");
    }
    private String extractNotes(URL tagPage) {
        try {
            String html=Http.get(tagPage.toString(),Map.of("Accept","text/html","User-Agent","MyScore/"+BuildConfig.VERSION_NAME),
                "text/html",u->"https".equalsIgnoreCase(u.getProtocol())&&"github.com".equalsIgnoreCase(u.getHost()),false);
            Matcher m=Pattern.compile("(?s)<div[^>]*class=\"[^\"]*markdown-body[^\"]*\"[^>]*>(.*?)</div>").matcher(html);
            if(!m.find())return "";
            String body=m.group(1).replaceAll("(?i)<li[^>]*>","- ")
                .replaceAll("(?i)</(li|p|h[1-6]|br)\\s*>","\n")
                .replaceAll("<[^>]+>","")
                .replace("&amp;","&").replace("&lt;","<").replace("&gt;",">").replace("&quot;","\"").replace("&#39;","'");
            return body.trim();
        } catch(Exception e) {
            return "";
        }
    }
    private long probeAssetSize(String apkUrl)throws Exception {
        IOException last=null;
        for(int attempt=0;attempt<2;attempt++) {
            URL current=new URL(apkUrl);
            try {
                for(int redirects=0;redirects<=5;redirects++) {
                    if(!UpdateTransfer.trustedHost(current))throw new IOException("Update package address is not trusted");
                    HttpURLConnection con=Http.openDownload(current,attempt);
                    try {
                        con.setInstanceFollowRedirects(false);
                        con.setConnectTimeout(8000);
                        con.setReadTimeout(10000);
                        con.setRequestProperty("User-Agent","MyScore/"+BuildConfig.VERSION_NAME+" Android");
                        con.setRequestProperty("Accept","application/octet-stream");
                        con.setRequestProperty("Accept-Encoding","identity");
                        con.setRequestProperty("Range","bytes=0-0");
                        int code=con.getResponseCode();
                        if(code==301||code==302||code==303||code==307||code==308) {
                            String loc=con.getHeaderField("Location");
                            if(loc==null||loc.isBlank())throw new IOException("Update file redirect is missing a location");
                            current=new URL(current,loc);
                            continue;
                        }
                        if(code==206) {
                            String cr=con.getHeaderField("Content-Range");
                            try(InputStream in=con.getInputStream()){if(in!=null)in.read();}
                            Matcher m=cr==null?null:CONTENT_RANGE.matcher(cr.trim());
                            if(m!=null&&m.find())return Long.parseLong(m.group(1));
                        } else if(code==200) {
                            long len=con.getContentLengthLong();
                            if(len>0)return len;
                        }
                        throw new IOException("Could not verify the update package size (HTTP "+code+")");
                    } finally {
                        con.disconnect();
                    }
                }
            } catch(IOException e) {
                last=e;
            }
        }
        throw last!=null?last:new IOException("Could not verify the update package size");
    }
    long downloadId(){return preferences.getLong("download_id",0);}
    UpdateRelease pending(){try{return UpdateRelease.restore(preferences.getString("release",""));}catch(Exception e){return null;}}
    File file(UpdateRelease release)throws IOException {
        return new File(new File(context.getFilesDir(),"updates"),"MyScore-"+release.version+".apk");
    }
    long start(UpdateRelease release)throws Exception {
        if(!release.newerThan(BuildConfig.VERSION_NAME))throw new IOException("The current version is already up to date");
        synchronized(LOCK) {
        clear();File file=file(release);File directory=file.getParentFile();
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Could not create the update download directory");
        File[] old=directory.listFiles();if(old!=null)for(File entry:old)if(entry.isFile()&&entry.getName().matches("MyScore-[0-9]+\\.[0-9]+(?:\\.[0-9]+)?\\.apk(?:\\.[a-f0-9-]{36}\\.part)?")&&!entry.delete())throw new IOException("Could not clean up the old update package. Try again later");
        String token=java.util.UUID.randomUUID().toString();
        if(!preferences.edit().putString("release",release.saved()).putLong("download_id",-1).putString("transfer_token",token).putInt("status",DownloadManager.STATUS_PENDING).putLong("bytes",0).putLong("updated",System.currentTimeMillis()).commit())throw new IOException("Could not save the download task. Try again");
        try {context.startForegroundService(new Intent(context,UpdateService.class).putExtra("token",token));}
        catch(RuntimeException e){preferences.edit().clear().commit();throw new IOException("Could not start the download. Return to the update page and download again",e);}
        return -1;
        }
    }
    Progress progress()throws IOException {
        synchronized(LOCK) {
            long id=downloadId();if(id==0)throw new IOException("The download task no longer exists. Download again");
            if(id>0)throw new IOException("The legacy system download was paused. Download again to use the fixed transfer method");
            int status=preferences.getInt("status",DownloadManager.STATUS_FAILED);
            if(status==DownloadManager.STATUS_FAILED)throw new IOException(preferences.getString("error","The download was interrupted. Download again"));
            UpdateRelease release=pending();if(release==null)throw new IOException("Download information has expired. Check for updates again");
            if(status!=DownloadManager.STATUS_SUCCESSFUL&&!UpdateService.running(token())&&System.currentTimeMillis()-preferences.getLong("updated",0)>30000)throw new IOException("The download was interrupted. Download again");
            return new Progress(status,preferences.getLong("bytes",0),release.size,0);
        }
    }
    Uri verify(UpdateRelease release)throws Exception {
        try{
            if(progress().status!=DownloadManager.STATUS_SUCCESSFUL)throw new IOException("The update package has not finished downloading");
            File file=file(release);release.verifyFile(file);
            PackageManager manager=context.getPackageManager();
            PackageInfo archive=manager.getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);
            PackageInfo installed=manager.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
            validateArchive(archive,installed,release.version);
            if(!preferences.edit().putString("verified",release.sha256).commit())throw new IOException("Could not save the update package verification state. Try again");
            return new Uri.Builder().scheme("content").authority(context.getPackageName()+".updates").appendPath("apk").appendPath(release.version).build();
        }catch(Exception e){clear();throw e;}
    }
    static void validateArchive(PackageInfo archive,PackageInfo installed,String version)throws IOException {
        if(archive==null||!installed.packageName.equals(archive.packageName)||!version.equals(archive.versionName))throw new IOException("The update package does not match this N1ghtScore version");
        if(archive.getLongVersionCode()<=installed.getLongVersionCode())throw new IOException("This package is not a newer version");
        if(archive.signingInfo==null||installed.signingInfo==null||!signers(archive.signingInfo.getApkContentsSigners()).equals(signers(installed.signingInfo.getApkContentsSigners())))throw new IOException("Update package signature mismatch. Installation stopped");
    }
    private static Set<String> signers(Signature[] signatures)throws IOException {
        if(signatures==null||signatures.length==0)throw new IOException("The update package is missing a signature");
        Set<String> values=new HashSet<>();for(Signature signature:signatures)values.add(Arrays.toString(signature.toByteArray()));return values;
    }
    String token(){return preferences.getString("transfer_token","");}
    boolean owns(String token){return !token.isEmpty()&&token.equals(token());}
    void changed(String token,int status,long bytes,String error){synchronized(LOCK){if(owns(token))preferences.edit().putInt("status",status).putLong("bytes",bytes).putLong("updated",System.currentTimeMillis()).putString("error",error).apply();}}
    void clear(){synchronized(LOCK){long id=downloadId();preferences.edit().clear().commit();if(id<0)context.stopService(new Intent(context,UpdateService.class));if(downloads!=null&&id>0)LEGACY_CLEANUP.submit(()->{try{downloads.remove(id);}catch(RuntimeException ignored){}});}}
}
