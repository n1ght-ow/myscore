package cn.myscore.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.view.Gravity;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.concurrent.*;

/** Actual source crests, bounded memory/disk cache. Initials are explicitly a fallback. */
final class Logos {
    private static final ExecutorService POOL=Executors.newFixedThreadPool(8);
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final LruCache<String,Bitmap> MEMORY=new LruCache<String,Bitmap>(24*1024*1024){@Override protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
    private static final ConcurrentHashMap<String,CompletableFuture<Bitmap>> IN_FLIGHT=new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String,Long> FAILED=new ConcurrentHashMap<>();
    static FrameLayout view(Ui ui,String url,String name,int size) {
        FrameLayout box=new FrameLayout(ui.c);box.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        box.setLayoutParams(new android.widget.LinearLayout.LayoutParams(ui.dp(size),ui.dp(size)));
        // White backing keeps original dark crests readable in both themes.
        box.setBackground(ui.shape(0xffFFFFFF,8));
        TextView fallback=ui.text(initials(name),size>40?16:11,0xff656870,true);fallback.setGravity(Gravity.CENTER);box.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
        ImageView image=new ImageView(ui.c);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setPadding(ui.dp(3),ui.dp(3),ui.dp(3),ui.dp(3));box.addView(image,new FrameLayout.LayoutParams(-1,-1));
        if(!allowed(url))return box;
        Bitmap memory=MEMORY.get(url);
        if(memory!=null){image.setImageBitmap(memory);fallback.setVisibility(android.view.View.GONE);return box;}
        if(System.currentTimeMillis()-FAILED.getOrDefault(url,0L)<30_000)return box;
        Context app=ui.c.getApplicationContext();
        CompletableFuture<Bitmap> future=new CompletableFuture<>(),existing=IN_FLIGHT.putIfAbsent(url,future);
        if(existing!=null)future=existing;
        else{CompletableFuture<Bitmap> request=future;POOL.submit(()->{try{Bitmap b=load(app,url);MEMORY.put(url,b);request.complete(b);}catch(Exception e){if(FAILED.size()>256)FAILED.clear();FAILED.put(url,System.currentTimeMillis());request.completeExceptionally(e);}finally{IN_FLIGHT.remove(url,request);}});}
        java.lang.ref.WeakReference<ImageView> target=new java.lang.ref.WeakReference<>(image);
        java.lang.ref.WeakReference<TextView> placeholder=new java.lang.ref.WeakReference<>(fallback);
        future.thenAccept(b->MAIN.post(()->{ImageView view=target.get();TextView text=placeholder.get();if(view==null||!view.isAttachedToWindow())return;view.setImageBitmap(b);if(text!=null)text.setVisibility(android.view.View.GONE);}));
        return box;
    }
    static boolean allowed(String url) {
        try{URI u=URI.create(url);String h=u.getHost();return "https".equals(u.getScheme())&&h!=null&&(h.equals("a.espncdn.com")||h.equals("oss.5eplay.com")||h.equals("img.crawler.qq.com")||h.equals("game.gtimg.cn")||h.equals("img1.gtimg.com")||h.equals("cdn.5eplay.com")||h.equals("img1.5eplay.com")||h.equals("ossweb-img.qq.com"));}catch(RuntimeException e){return false;}
    }
    static void retryFailures(){FAILED.clear();}
    static Bitmap load(Context c,String url)throws Exception {
        if(!allowed(url))throw new IOException("Unsupported crest host");
        File dir=new File(c.getCacheDir(),"crests-v1");if(!dir.exists()&&!dir.mkdirs())throw new IOException();
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder key=new StringBuilder();for(byte b:digest)key.append(String.format(java.util.Locale.ROOT,"%02x",b));File file=new File(dir,key.toString());
        byte[] bytes=null;if(file.exists())try(InputStream in=new FileInputStream(file)){bytes=bounded(in);}
        if(bytes==null){HttpURLConnection con=(HttpURLConnection)new URL(url).openConnection();try{con.setConnectTimeout(6000);con.setReadTimeout(8000);con.setRequestProperty("User-Agent","MyScore-Personal/0.4 Android");con.setRequestProperty("Accept","image/*");con.setInstanceFollowRedirects(false);if(con.getResponseCode()!=200)throw new IOException("Image HTTP "+con.getResponseCode());try(InputStream in=con.getInputStream()){bytes=bounded(in);}}finally{con.disconnect();}}
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);if(bounds.outWidth<1||bounds.outWidth>4096||bounds.outHeight>4096)throw new IOException();
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>256)options.inSampleSize*=2;
        Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(bitmap==null){file.delete();throw new IOException("Image decode failed");}
        if(!file.exists())try(OutputStream out=new FileOutputStream(file)){out.write(bytes);}
        File[] old=dir.listFiles();if(old!=null)for(File f:old)if(System.currentTimeMillis()-f.lastModified()>14L*86400_000)f.delete();
        return bitmap;
    }
    private static byte[] bounded(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>1_500_000)throw new IOException();out.write(b,0,n);}return out.toByteArray();}
    private static String initials(String s){if(s==null||s.isBlank())return "—";String[] parts=s.trim().split("\\s+");return parts.length>1?(parts[0].substring(0,1)+parts[parts.length-1].substring(0,1)).toUpperCase(java.util.Locale.ROOT):s.substring(0,Math.min(2,s.length())).toUpperCase(java.util.Locale.ROOT);}
}
