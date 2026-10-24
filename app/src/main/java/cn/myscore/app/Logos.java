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
    private static final ExecutorService POOL=Executors.newFixedThreadPool(3);
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    // Session cache avoids the initials flash when a card is rebuilt or its detail opens.
    private static final ConcurrentHashMap<String,Bitmap> SESSION=new ConcurrentHashMap<>();
    private static final LruCache<String,Bitmap> MEMORY=new LruCache<String,Bitmap>(24*1024*1024){@Override protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
    private static final ConcurrentHashMap<String,Long> FAILED=new ConcurrentHashMap<>();
    static FrameLayout view(Ui ui,String url,String name,int size) {
        FrameLayout box=new FrameLayout(ui.c);box.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        box.setLayoutParams(new android.widget.LinearLayout.LayoutParams(ui.dp(size),ui.dp(size)));
        // White backing keeps original dark crests readable in both themes.
        box.setBackground(ui.shape(0xffFFFFFF,8));
        TextView fallback=ui.text(initials(name),size>40?16:10,0xff656870,true);fallback.setGravity(Gravity.CENTER);box.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
        ImageView image=new ImageView(ui.c);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setPadding(ui.dp(3),ui.dp(3),ui.dp(3),ui.dp(3));box.addView(image,new FrameLayout.LayoutParams(-1,-1));
        if(!allowed(url))return box;
        Bitmap memory=SESSION.get(url);if(memory==null)memory=MEMORY.get(url);
        if(memory!=null){MEMORY.put(url,memory);SESSION.put(url,memory);image.setImageBitmap(memory);fallback.setVisibility(android.view.View.GONE);return box;}
        if(System.currentTimeMillis()-FAILED.getOrDefault(url,0L)<30_000)return box;
        Context app=ui.c.getApplicationContext();
        POOL.submit(()->{try{Bitmap b=load(app,url);MEMORY.put(url,b);SESSION.put(url,b);MAIN.post(()->{image.setImageBitmap(b);fallback.setVisibility(android.view.View.GONE);});}catch(Exception e){FAILED.put(url,System.currentTimeMillis());android.util.Log.i("MyScoreCrests","Crest unavailable on "+URI.create(url).getHost()+": "+e.getClass().getSimpleName());}});
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
        if(bytes==null){HttpURLConnection con=(HttpURLConnection)new URL(url).openConnection();try{con.setConnectTimeout(10000);con.setReadTimeout(12000);con.setRequestProperty("User-Agent","MyScore-Personal/0.2 Android");con.setRequestProperty("Accept","image/*");con.setInstanceFollowRedirects(false);if(con.getResponseCode()!=200)throw new IOException("Image HTTP "+con.getResponseCode());try(InputStream in=con.getInputStream()){bytes=bounded(in);}}finally{con.disconnect();}}
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);if(bounds.outWidth<1||bounds.outWidth>4096||bounds.outHeight>4096)throw new IOException();
        BitmapFactory.Options options=new BitmapFactory.Options();while(bounds.outWidth/(options.inSampleSize==0?1:options.inSampleSize)>256){options.inSampleSize=options.inSampleSize==0?2:options.inSampleSize*2;}
        Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(bitmap==null){file.delete();throw new IOException("Image decode failed");}
        if(!file.exists())try(OutputStream out=new FileOutputStream(file)){out.write(bytes);}
        File[] old=dir.listFiles();if(old!=null)for(File f:old)if(System.currentTimeMillis()-f.lastModified()>14L*86400_000)f.delete();
        return bitmap;
    }
    private static byte[] bounded(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>1_500_000)throw new IOException();out.write(b,0,n);}return out.toByteArray();}
    private static String initials(String s){if(s==null||s.isBlank())return "—";String[] parts=s.trim().split("\\s+");return parts.length>1?(parts[0].substring(0,1)+parts[parts.length-1].substring(0,1)).toUpperCase(java.util.Locale.ROOT):s.substring(0,Math.min(2,s.length())).toUpperCase(java.util.Locale.ROOT);}
}
