package cn.myscore.app;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Bounded HTTPS transfer using the app's network, including GitHub's asset redirects. */
final class UpdateTransfer {
    interface Progress {void changed(long bytes);}
    interface Connection {HttpURLConnection open(URL url)throws IOException;}
    private static final Set<String> HOSTS=Set.of("github.com","api.github.com","release-assets.githubusercontent.com","objects.githubusercontent.com");
    static void download(UpdateRelease release,File partial,Progress progress,BooleanSupplier cancelled)throws Exception {
        download(release,partial,progress,cancelled,url->(HttpURLConnection)url.openConnection());
    }
    static void download(UpdateRelease release,File partial,Progress progress,BooleanSupplier cancelled,Connection connections)throws Exception {
        download(release,partial,progress,cancelled,connections,release.assetUrl.isEmpty()?release.url:release.assetUrl);
    }
    static void download(UpdateRelease release,File partial,Progress progress,BooleanSupplier cancelled,boolean alternate)throws Exception {
        String address=alternate||release.assetUrl.isEmpty()?release.url:release.assetUrl;
        download(release,partial,progress,cancelled,url->(HttpURLConnection)url.openConnection(),address);
    }
    private static void download(UpdateRelease release,File partial,Progress progress,BooleanSupplier cancelled,Connection connections,String address)throws Exception {
        boolean success=false;long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(5);
        try {
            URL url=new URL(address);
            for(int redirects=0;redirects<=5;redirects++) {
                interrupted(cancelled,deadline);
                if(!"https".equals(url.getProtocol())||!HOSTS.contains(url.getHost())||url.getUserInfo()!=null||(url.getPort()!=-1&&url.getPort()!=443))throw new IOException("更新下载跳转地址不可信");
                HttpURLConnection connection=connections.open(url);
                try {
                    connection.setConnectTimeout(12000);connection.setReadTimeout(15000);connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);
                    connection.setRequestProperty("User-Agent","MyScore/"+BuildConfig.VERSION_NAME+" Android");
                    connection.setRequestProperty("Accept","application/octet-stream");connection.setRequestProperty("Accept-Encoding","identity");
                    connection.setRequestProperty("Cache-Control","no-cache");
                    int code=connection.getResponseCode();
                    if(code==301||code==302||code==303||code==307||code==308) {
                        String location=connection.getHeaderField("Location");
                        if(location==null||location.isBlank())throw new IOException("更新下载跳转缺少地址");
                        url=new URL(url,location);continue;
                    }
                    if(code!=200)throw new IOException("下载服务器返回 HTTP "+code+"，请稍后重试");
                    long length=connection.getContentLengthLong();
                    if(length>0&&length!=release.size)throw new IOException("安装包大小与发布信息不一致");
                    long bytes=0;progress.changed(0);
                    try(InputStream input=connection.getInputStream();FileOutputStream output=new FileOutputStream(partial)) {
                        byte[] buffer=new byte[32768];int read;
                        while((read=input.read(buffer))!=-1) {
                            interrupted(cancelled,deadline);
                            bytes+=read;if(bytes>release.size)throw new IOException("安装包大小超过发布信息");
                            output.write(buffer,0,read);progress.changed(bytes);
                        }
                        output.getFD().sync();
                    }
                    interrupted(cancelled,deadline);release.verifyFile(partial);success=true;return;
                } finally {connection.disconnect();}
            }
            throw new IOException("更新下载跳转次数过多，请稍后重试");
        } finally {if(!success&&partial.exists())partial.delete();}
    }
    private static void interrupted(BooleanSupplier cancelled,long deadline)throws IOException {
        if(Thread.currentThread().isInterrupted()||cancelled.getAsBoolean())throw new InterruptedIOException("下载已取消");
        if(System.nanoTime()>deadline)throw new java.net.SocketTimeoutException("下载超时，请检查网络后重试");
    }
    static String failure(Exception error) {
        if(error instanceof java.net.SocketTimeoutException)return "下载连接超时，请检查网络后重新下载";
        if(error instanceof java.net.UnknownHostException)return "无法解析下载服务器，请检查网络或 DNS 后重试";
        if(error instanceof javax.net.ssl.SSLException)return "下载安全连接失败，请检查网络和手机时间后重试";
        if(error instanceof IOException&&error.getMessage()!=null&&!error.getMessage().contains("https://"))return error.getMessage();
        return "下载未完成，请检查网络和存储空间后重试";
    }
}
