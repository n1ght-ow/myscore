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
import java.util.concurrent.atomic.AtomicLong;

/**
 * Actual source crests and artwork with a bounded, size-aware cache.
 * Rows are recycled, so images are rebound into existing views instead of allocating a new tree.
 */
final class Logos {
    private static final ExecutorService POOL=Executors.newFixedThreadPool(6);
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final int MEMORY_BYTES=16*1024*1024;
    private static final LruCache<String,Bitmap> MEMORY=new LruCache<String,Bitmap>(MEMORY_BYTES){@Override protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
    private static final ConcurrentHashMap<String,CompletableFuture<Bitmap>> IN_FLIGHT=new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String,Long> FAILED=new ConcurrentHashMap<>();
    private static final AtomicLong DECODES=new AtomicLong();

    /** A recyclable image slot: create once, then call {@link #bind} for every row. */
    static final class Crest {
        final FrameLayout box;
        final ImageView image;
        final TextView initials;
        final int targetPx;
        private Crest(FrameLayout box,ImageView image,TextView initials,int targetPx){this.box=box;this.image=image;this.initials=initials;this.targetPx=targetPx;}
    }

    static Crest crest(Ui ui,int size,boolean artwork) {
        FrameLayout box=new FrameLayout(ui.c);
        box.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        box.setLayoutParams(new android.widget.LinearLayout.LayoutParams(ui.dp(size),ui.dp(size)));
        // White backing keeps original dark crests readable in both themes.
        box.setBackground(ui.shape(artwork?ui.tonal:0xffFFFFFF,8));box.setClipToOutline(artwork);
        TextView fallback=ui.text(initials(""),size>40?16:11,artwork?ui.muted:0xff656870,true);
        fallback.setGravity(Gravity.CENTER);
        box.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
        ImageView image=new ImageView(ui.c);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int padding=artwork?0:ui.dp(3);
        image.setPadding(padding,padding,padding,padding);
        box.addView(image,new FrameLayout.LayoutParams(-1,-1));
        return new Crest(box,image,fallback,targetPixels(ui,size));
    }

    static FrameLayout view(Ui ui,String url,String name,int size) {
        Crest crest=crest(ui,size,false);bind(crest,url,name);return crest.box;
    }
    static FrameLayout artwork(Ui ui,String url,String name,int size) {
        Crest crest=crest(ui,size,true);bind(crest,url,name);return crest.box;
    }

    static void bind(Crest crest,String url,String name) {
        if(crest==null)return;
        ImageView image=crest.image;TextView fallback=crest.initials;
        fallback.setText(initials(name));
        if(!allowed(url)) {
            image.setImageDrawable(null);image.setTag(null);fallback.setVisibility(android.view.View.VISIBLE);return;
        }
        String key=key(url,crest.targetPx);
        if(url.equals(image.getTag())&&image.getDrawable()!=null){fallback.setVisibility(android.view.View.GONE);return;}
        image.setImageDrawable(null);image.setTag(url);
        Bitmap memory=MEMORY.get(key);
        if(memory!=null){image.setImageBitmap(memory);fallback.setVisibility(android.view.View.GONE);return;}
        fallback.setVisibility(android.view.View.VISIBLE);
        long now=System.currentTimeMillis();
        Long failed=FAILED.get(url);
        if(failed!=null&&now-failed<30_000)return;
        Context app=image.getContext().getApplicationContext();
        CompletableFuture<Bitmap> future=new CompletableFuture<>(),existing=IN_FLIGHT.putIfAbsent(key,future);
        if(existing!=null)future=existing;
        else {
            CompletableFuture<Bitmap> request=future;
            int target=crest.targetPx;
            POOL.submit(()-> {
                try {
                    Bitmap bitmap=load(app,url,target);
                    MEMORY.put(key,bitmap);
                    request.complete(bitmap);
                } catch(Throwable e) {
                    if(!(e instanceof OutOfMemoryError))android.util.Log.i("MyScore","Crest unavailable: "+safeHost(url));
                    else {MEMORY.evictAll();android.util.Log.w("MyScore","Crest cache cleared after low memory");}
                    if(FAILED.size()>256)FAILED.clear();
                    FAILED.put(url,System.currentTimeMillis());
                    request.completeExceptionally(e instanceof Exception?(Exception)e:new IOException("Image unavailable"));
                } finally {IN_FLIGHT.remove(key,request);}
            });
        }
        java.lang.ref.WeakReference<ImageView> target=new java.lang.ref.WeakReference<>(image);
        java.lang.ref.WeakReference<TextView> placeholder=new java.lang.ref.WeakReference<>(fallback);
        future.thenAccept(bitmap->MAIN.post(()-> {
            ImageView view=target.get();TextView text=placeholder.get();
            if(view==null||!url.equals(view.getTag())||!view.isAttachedToWindow())return;
            view.setImageBitmap(bitmap);
            if(text!=null)text.setVisibility(android.view.View.GONE);
        }));
    }

    static boolean allowed(String url) {
        try{URI u=URI.create(url);String h=u.getHost();return "https".equals(u.getScheme())&&h!=null&&(h.equals("a.espncdn.com")||h.equals("oss.5eplay.com")||h.equals("img.crawler.qq.com")||h.equals("game.gtimg.cn")||h.equals("img1.gtimg.com")||h.equals("cdn.5eplay.com")||h.equals("img1.5eplay.com")||h.equals("ossweb-img.qq.com"));}catch(RuntimeException e){return false;}
    }
    static void retryFailures(){FAILED.clear();}
    static Bitmap load(Context c,String url)throws Exception {return load(c,url,256);}
    static Bitmap load(Context c,String url,int maxPx)throws Exception {
        if(!allowed(url))throw new IOException("Unsupported crest host");
        File dir=new File(c.getCacheDir(),"crests-v1");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create image cache");
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder key=new StringBuilder();for(byte b:digest)key.append(String.format(java.util.Locale.ROOT,"%02x",b));File file=new File(dir,key.toString());
        byte[] bytes=null;if(file.exists())try(InputStream in=new FileInputStream(file)){bytes=bounded(in);}
        if(bytes==null){HttpURLConnection con=(HttpURLConnection)new URL(url).openConnection();try{con.setConnectTimeout(6000);con.setReadTimeout(8000);con.setRequestProperty("User-Agent","MyScore-Personal/0.9 Android");con.setRequestProperty("Accept","image/*");con.setInstanceFollowRedirects(false);if(con.getResponseCode()!=200)throw new IOException("Image HTTP "+con.getResponseCode());try(InputStream in=con.getInputStream()){bytes=bounded(in);}}finally{con.disconnect();}}
        int limit=Math.max(32,Math.min(512,maxPx));
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
        if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>4096||bounds.outHeight>4096)throw new IOException("Invalid image bounds");
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inSampleSize=1;
        while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>limit*2)options.inSampleSize*=2;
        if(options.inSampleSize>1)options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try{bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);}
        catch(OutOfMemoryError e){MEMORY.evictAll();throw new IOException("Image decode out of memory");}
        if(bitmap==null){file.delete();throw new IOException("Image decode failed");}
        int longest=Math.max(bitmap.getWidth(),bitmap.getHeight());
        if(longest>limit) {
            float scale=limit/(float)longest;
            int width=Math.max(1,Math.round(bitmap.getWidth()*scale)),height=Math.max(1,Math.round(bitmap.getHeight()*scale));
            try {
                Bitmap scaled=Bitmap.createScaledBitmap(bitmap,width,height,true);
                if(scaled!=bitmap)bitmap.recycle();
                bitmap=scaled;
            } catch(OutOfMemoryError e){bitmap.recycle();MEMORY.evictAll();throw new IOException("Image scale out of memory");}
        }
        if(!file.exists())try(OutputStream out=new FileOutputStream(file)){out.write(bytes);}
        // Directory scans are cheap but not free; keep them off the common path.
        if(DECODES.incrementAndGet()%32==0)cleanup(dir);
        return bitmap;
    }
    private static void cleanup(File dir) {
        File[] old=dir.listFiles();if(old==null)return;
        long now=System.currentTimeMillis();
        for(File f:old)if(now-f.lastModified()>14L*86400_000&&!f.delete())android.util.Log.d("MyScore","Image cache cleanup deferred");
    }
    private static String key(String url,int targetPx){return url+"@"+targetPx;}
    private static int targetPixels(Ui ui,int size) {
        float density=ui.c.getResources().getDisplayMetrics().density;
        // 2x the layout size stays sharp on 420 dpi panels while keeping GPU textures small.
        return Math.max(48,Math.min(512,Math.round(size*density*2f)));
    }
    private static String safeHost(String url){try{return URI.create(url).getHost();}catch(RuntimeException e){return "image";}}
    private static byte[] bounded(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>1_500_000)throw new IOException("Image too large");out.write(b,0,n);}return out.toByteArray();}
    private static String initials(String s){if(s==null||s.isBlank())return "—";String[] parts=s.trim().split("\\s+");return parts.length>1?(parts[0].substring(0,1)+parts[parts.length-1].substring(0,1)).toUpperCase(java.util.Locale.ROOT):s.substring(0,Math.min(2,s.length())).toUpperCase(java.util.Locale.ROOT);}
}
