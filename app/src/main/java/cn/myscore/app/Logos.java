package cn.myscore.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.AtomicFile;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.view.Gravity;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Actual source crests and artwork with a bounded, size-aware cache.
 * Rows are recycled, so images are rebound into existing views instead of allocating a new tree.
 */
final class Logos {
    private static final ExecutorService POOL=Executors.newFixedThreadPool(8);
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final int MEMORY_BYTES=16*1024*1024;
    private static final LruCache<String,Bitmap> MEMORY=new LruCache<String,Bitmap>(MEMORY_BYTES){@Override protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
    private static final ConcurrentHashMap<String,CompletableFuture<Bitmap>> IN_FLIGHT=new ConcurrentHashMap<>();
    private static final SingleFlight<String,byte[]> SOURCES=new SingleFlight<>();
    private static final Object DISK_LOCK=new Object();
    private static final ConcurrentHashMap<String,Long> FAILED=new ConcurrentHashMap<>();
    private static final AtomicLong DECODES=new AtomicLong();
    private static final AtomicLong CRAFTED=new AtomicLong();

    /** A recyclable image slot: create once, then call {@link #bind} for every row. */
    static final class Crest {
        final FrameLayout box;
        final ImageView image;
        final TextView initials;
        final int targetPx;
        final boolean artwork;
        final int foreground;
        private String pendingKey;
        private java.util.function.Supplier<String> backup;
        private Crest(FrameLayout box,ImageView image,TextView initials,int targetPx,boolean artwork,int foreground){this.box=box;this.image=image;this.initials=initials;this.targetPx=targetPx;this.artwork=artwork;this.foreground=foreground;}
    }

    static Crest crest(Ui ui,int size,boolean artwork) {
        return crest(ui,size,artwork,false);
    }
    private static Crest crest(Ui ui,int size,boolean artwork,boolean portrait) {
        FrameLayout box=new FrameLayout(ui.c);
        box.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        box.setLayoutParams(new android.widget.LinearLayout.LayoutParams(ui.dp(size),ui.dp(size)));
        if(artwork)box.setBackground(ui.shape(ui.tonal,8));box.setClipToOutline(artwork);
        TextView fallback=ui.text(initials(""),size>40?16:11,ui.muted,true);
        fallback.setGravity(Gravity.CENTER);
        fallback.setAutoSizeTextTypeUniformWithConfiguration(6,size>40?16:11,1,android.util.TypedValue.COMPLEX_UNIT_SP);fallback.setMaxLines(1);
        box.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
        ImageView image=portrait?new PortraitImage(ui.c):new ImageView(ui.c);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        box.addView(image,new FrameLayout.LayoutParams(-1,-1));
        Crest crest=new Crest(box,image,fallback,targetPixels(ui,size),artwork,ui.text);
        // Factory callers keep only the box; keep its request state alive with the view tree.
        box.setTag(crest);return crest;
    }

    static FrameLayout view(Ui ui,String url,String name,int size) {
        Crest crest=crest(ui,size,false);bind(crest,url,name);return crest.box;
    }
    static FrameLayout artwork(Ui ui,String url,String name,int size) {
        Crest crest=crest(ui,size,true);bind(crest,url,name);return crest.box;
    }
    static FrameLayout portrait(Ui ui,String url,String name,int size,java.util.function.Supplier<String> backup) {
        Crest crest=crest(ui,size,true,true);crest.backup=backup;bind(crest,url,name);return crest.box;
    }
    /** Official FPL photos include the torso; frame the upper part for a small circular avatar. */
    private static final class PortraitImage extends ImageView {
        private final android.graphics.Matrix crop=new android.graphics.Matrix();
        private android.graphics.drawable.Drawable previous;private int width,height;private Object url;
        PortraitImage(Context context){super(context);}
        @Override protected void onDraw(Canvas canvas){
            android.graphics.drawable.Drawable drawable=getDrawable();
            if(drawable!=previous||width!=getWidth()||height!=getHeight()||!java.util.Objects.equals(url,getTag())){
                previous=drawable;width=getWidth();height=getHeight();url=getTag();
                if(drawable!=null&&drawable.getIntrinsicWidth()>0&&drawable.getIntrinsicHeight()>0&&url instanceof String&&((String)url).startsWith("https://resources.premierleague.com/")){
                    float side=Math.min(drawable.getIntrinsicWidth(),drawable.getIntrinsicHeight()*.55f),scale=Math.max(width/side,height/side);
                    crop.setScale(scale,scale);crop.postTranslate((width-drawable.getIntrinsicWidth()*scale)/2f,0);setScaleType(ScaleType.MATRIX);setImageMatrix(crop);
                }else setScaleType(ScaleType.FIT_CENTER);
            }
            super.onDraw(canvas);
        }
    }

    static void bind(Crest crest,String url,String name) {
        if(crest==null)return;
        ImageView image=crest.image;TextView fallback=crest.initials;
        String label=initials(name);if(!label.contentEquals(fallback.getText()))fallback.setText(label);
        if(!allowed(url)) {
            crest.pendingKey=null;
            image.setImageDrawable(null);image.setTag(null);fallback.setVisibility(android.view.View.VISIBLE);return;
        }
        String key=key(url,crest);
        if(url.equals(image.getTag())&&image.getDrawable()!=null){fallback.setVisibility(android.view.View.GONE);return;}
        if(key.equals(crest.pendingKey))return;
        crest.pendingKey=null;
        image.setImageDrawable(null);image.setTag(url);
        Bitmap memory=MEMORY.get(key);
        if(memory!=null){image.setImageBitmap(memory);fallback.setVisibility(android.view.View.GONE);return;}
        fallback.setVisibility(android.view.View.VISIBLE);
        long now=System.currentTimeMillis();
        Long failed=FAILED.get(url);
        if(failed!=null&&now-failed<30_000)return;
        crest.pendingKey=key;
        Context app=image.getContext().getApplicationContext();
        CompletableFuture<Bitmap> future=new CompletableFuture<>(),existing=IN_FLIGHT.putIfAbsent(key,future);
        if(existing!=null)future=existing;
        else {
            CompletableFuture<Bitmap> request=future;
            int target=crest.targetPx;
            boolean artwork=crest.artwork;int foreground=crest.foreground;java.util.function.Supplier<String> backup=crest.backup;
            POOL.submit(()-> {
                try {
                    Bitmap bitmap=artwork?artworkBitmap(app,url,target,backup):crestBitmap(app,url,target,foreground,backup);
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
        java.lang.ref.WeakReference<Crest> target=new java.lang.ref.WeakReference<>(crest);
        future.whenComplete((bitmap,failure)->MAIN.post(()-> {
            Crest slot=target.get();
            if(slot==null||!key.equals(slot.pendingKey)||!url.equals(slot.image.getTag()))return;
            slot.pendingKey=null;
            if(failure==null){slot.image.setImageBitmap(bitmap);slot.initials.setVisibility(android.view.View.GONE);}
        }));
    }

    static boolean allowed(String url) {
        try{URI u=URI.create(url);String h=u.getHost();return "https".equals(u.getScheme())&&h!=null&&(h.equals("a.espncdn.com")||h.equals("resources.premierleague.com")||h.equals("r2.thesportsdb.com")||h.equals("oss.5eplay.com")||h.equals("img.crawler.qq.com")||h.equals("game.gtimg.cn")||h.equals("img1.gtimg.com")||h.equals("cdn.5eplay.com")||h.equals("img1.5eplay.com")||h.equals("ossweb-img.qq.com"));}catch(RuntimeException e){return false;}
    }
    private static Bitmap artworkBitmap(Context app,String url,int target,java.util.function.Supplier<String> backup)throws Exception {
        try{return load(app,url,target);}
        catch(Exception first){String alternate=backup==null?"":backup.get();if(!allowed(alternate)||alternate.equals(url))throw first;return load(app,alternate,target);}
    }
    /** The trimmed, themed square that is actually drawn; reused from the prepared cache when possible. */
    private static Bitmap crestBitmap(Context app,String url,int target,int foreground,java.util.function.Supplier<String> backup)throws Exception {
        int edge=Math.max(32,Math.min(512,target));
        // A prepared square is a tiny PNG of the exact draw size. Decoding it is far cheaper
        // than the full source image plus the alpha trim on every launch.
        Bitmap cached=prepared(app,url,edge,foreground);
        if(cached!=null)return cached;
        boolean primary=true;
        Bitmap bitmap;
        try{bitmap=load(app,url,Math.min(512,target*2));}
        catch(Exception first){primary=false;String alternate=backup==null?"":backup.get();if(!allowed(alternate)||alternate.equals(url))throw first;bitmap=load(app,alternate,target);}
        bitmap=prepareCrest(selectCrestVariant(bitmap,url,foreground),target,foreground);
        // A fallback image is not stored as the primary crest; a later refresh may pick the real asset.
        if(primary)savePrepared(app,url,edge,foreground,bitmap);
        return bitmap;
    }
    /**
     * Warm the crests of a day that was prefetched but not shown yet. The list then binds
     * images from memory instead of starting a download on every first visit.
     */
    static void warm(Ui ui,java.util.Collection<String> urls,int size) {
        if(urls==null||urls.isEmpty())return;
        Context app=ui.c.getApplicationContext();
        int target=targetPixels(ui,size),foreground=ui.text;
        for(String url:urls) {
            if(!allowed(url))continue;
            String key=url+"@"+target+":crest:"+foreground;
            if(MEMORY.get(key)!=null)continue;
            CompletableFuture<Bitmap> created=new CompletableFuture<>(),running=IN_FLIGHT.putIfAbsent(key,created);
            if(running!=null)continue;
            POOL.submit(()-> {
                try {
                    Bitmap bitmap=crestBitmap(app,url,target,foreground,null);
                    MEMORY.put(key,bitmap);
                    created.complete(bitmap);
                } catch(Throwable e) {
                    // A quiet warm must not block the visible bind from trying again.
                    created.completeExceptionally(e instanceof Exception?(Exception)e:new IOException("Image unavailable"));
                } finally {IN_FLIGHT.remove(key,created);}
            });
        }
    }
    static void retryFailures(){FAILED.clear();}

    /**
     * Follow the system's memory pressure instead of holding 16 MiB of crests for the whole
     * process lifetime. A returning foreground rebinds from the disk cache.
     */
    static void trimMemory(int level) {
        int before = MEMORY.size();
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE
            || level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
            || level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            MEMORY.evictAll();
            FAILED.clear();
        } else if (level == android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            MEMORY.trimToSize(MEMORY_BYTES / 4);
        } else if (level == android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
            || level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE) {
            MEMORY.trimToSize(MEMORY_BYTES / 2);
        } else {
            return;
        }
        android.util.Log.i("MyScorePerf", "crest memory trimmed level=" + level + " bytes=" + before + "->" + MEMORY.size());
    }
    static Bitmap load(Context c,String url)throws Exception {return load(c,url,256);}
    static Bitmap load(Context c,String url,int maxPx)throws Exception {
        if(!allowed(url))throw new IOException("Unsupported crest host");
        File dir=new File(c.getCacheDir(),"crests-v1");if(!dir.isDirectory()&&!dir.mkdirs()&&!dir.isDirectory())throw new IOException("Cannot create image cache");
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder key=new StringBuilder();for(byte b:digest)key.append(String.format(java.util.Locale.ROOT,"%02x",b));File file=new File(dir,key.toString());
        // Different draw sizes share source bytes, never a bitmap that another size recycles.
        byte[] bytes=SOURCES.execute(url,()->sourceBytes(url,file));
        int limit=Math.max(32,Math.min(512,maxPx));
        BitmapFactory.Options bounds=imageBounds(bytes);
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inSampleSize=1;
        while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>limit*2)options.inSampleSize*=2;
        if(options.inSampleSize>1)options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try{bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);}
        catch(OutOfMemoryError e){MEMORY.evictAll();throw new IOException("Image decode out of memory");}
        if(bitmap==null){synchronized(DISK_LOCK){new AtomicFile(file).delete();}throw new IOException("Image decode failed");}
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
        // Directory scans are cheap but not free; keep them off the common path.
        if(DECODES.incrementAndGet()%32==0)cleanup(dir);
        return bitmap;
    }
    private static byte[] sourceBytes(String url,File file)throws Exception {
        synchronized(DISK_LOCK) {
            if(file.exists()) {
                byte[] bytes;
                try(InputStream in=new AtomicFile(file).openRead()) {
                    bytes=bounded(in);
                } catch(InterruptedIOException e){throw e;}
                catch(IOException e){new AtomicFile(file).delete();bytes=null;}
                if(bytes!=null) {
                    try{imageBounds(bytes);return bytes;}
                    catch(IOException e){new AtomicFile(file).delete();}
                }
            }
        }
        HttpURLConnection con=(HttpURLConnection)new URL(url).openConnection();
        boolean complete=false;byte[] bytes;
        try {
            con.setConnectTimeout(6000);con.setReadTimeout(8000);con.setRequestProperty("User-Agent","MyScore-Personal/"+BuildConfig.VERSION_NAME+" Android");con.setRequestProperty("Accept","image/*");con.setInstanceFollowRedirects(false);
            if(con.getResponseCode()!=200)throw new IOException("Image HTTP "+con.getResponseCode());
            try(InputStream in=con.getInputStream()){bytes=bounded(in);}
            imageBounds(bytes);complete=true;
        } finally {if(!complete)con.disconnect();}
        synchronized(DISK_LOCK) {
            AtomicFile atomic=new AtomicFile(file);FileOutputStream out=null;
            try{out=atomic.startWrite();out.write(bytes);atomic.finishWrite(out);}
            catch(IOException e){if(out!=null)atomic.failWrite(out);android.util.Log.d("MyScore","Source crest cache skipped");}
        }
        return bytes;
    }
    private static BitmapFactory.Options imageBounds(byte[] bytes)throws IOException {
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
        if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>4096||bounds.outHeight>4096)throw new IOException("Invalid image bounds");
        return bounds;
    }
    private static void cleanup(File dir) {
        File[] old=dir.listFiles();if(old==null)return;
        long now=System.currentTimeMillis();
        for(File f:old)if(now-f.lastModified()>14L*86400_000&&!f.delete())android.util.Log.d("MyScore","Image cache cleanup deferred");
        cap(dir,300,64L*1024*1024);
    }
    /** Keep the disk cache bounded even when many teams/players are browsed. */
    private static void cap(File dir,int maxFiles,long maxBytes) {
        File[] files=dir.listFiles();
        if(files==null||files.length==0)return;
        long total=0;for(File file:files)total+=file.length();
        if(files.length<=maxFiles&&total<=maxBytes)return;
        Arrays.sort(files,Comparator.comparingLong(File::lastModified));
        int count=files.length;
        for(File file:files) {
            if(count<=maxFiles&&total<=maxBytes)break;
            long length=file.length();
            if(file.delete()){count--;total-=length;}
        }
    }
    /** The trimmed, colour-corrected square that is actually drawn, cached per draw size. */
    private static File preparedFile(Context c,String url,int edge,int foreground)throws Exception {
        File dir=new File(c.getCacheDir(),"crests-prepared-v1");
        if(!dir.isDirectory()&&!dir.mkdirs()&&!dir.isDirectory())throw new IOException("Cannot create prepared crest cache");
        byte[] digest=MessageDigest.getInstance("SHA-256").digest((url+"@"+edge+"@"+foreground).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder key=new StringBuilder();for(byte b:digest)key.append(String.format(java.util.Locale.ROOT,"%02x",b));
        return new File(dir,key.append(".png").toString());
    }
    private static Bitmap prepared(Context c,String url,int edge,int foreground) {
        try {
            File file=preparedFile(c,url,edge,foreground);if(!file.exists())return null;
            BitmapFactory.Options options=new BitmapFactory.Options();options.inPreferredConfig=Bitmap.Config.ARGB_8888;
            Bitmap bitmap=BitmapFactory.decodeFile(file.getAbsolutePath(),options);
            if(bitmap==null)return null;
            if(bitmap.getWidth()!=edge||bitmap.getHeight()!=edge){bitmap.recycle();return null;}
            return bitmap;
        } catch(Exception|OutOfMemoryError e) {return null;}
    }
    private static void savePrepared(Context c,String url,int edge,int foreground,Bitmap bitmap) {
        try {
            File file=preparedFile(c,url,edge,foreground),tmp=new File(file.getPath()+".tmp");
            try(OutputStream out=new FileOutputStream(tmp)){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Cannot encode prepared crest");}
            if(file.exists()&&!file.delete())throw new IOException("Cannot replace prepared crest");
            if(!tmp.renameTo(file)){tmp.delete();throw new IOException("Cannot store prepared crest");}
            if(CRAFTED.incrementAndGet()%64==0)cleanupPrepared(file.getParentFile());
        } catch(Exception|OutOfMemoryError e) {android.util.Log.d("MyScore","Prepared crest cache skipped");}
    }
    private static void cleanupPrepared(File dir) {
        File[] files=dir==null?null:dir.listFiles();if(files==null)return;
        long cutoff=System.currentTimeMillis()-21L*86400_000;
        for(File f:files)if(f.lastModified()<cutoff&&!f.delete())android.util.Log.d("MyScore","Prepared crest cleanup deferred");
        cap(dir,800,96L*1024*1024);
    }
    private static String key(String url,Crest crest){return url+"@"+crest.targetPx+(crest.artwork?":art":":crest:"+crest.foreground);}
    /** 5EPlay's merge_ team assets contain equal light/dark variants, not a wide team mark. */
    static Bitmap selectCrestVariant(Bitmap source,String url,int foreground){
        URI uri=URI.create(url);String path=uri.getPath();
        if(!"oss.5eplay.com".equals(uri.getHost())||path==null||!path.matches("/(cloudx/p/)?sport/csgo/team/merge_[^/]+\\.png")||Math.abs(source.getWidth()-source.getHeight()*2)>2)return source;
        int half=source.getWidth()/2,left=lightSurface(foreground)?0:half;
        Bitmap selected=Bitmap.createBitmap(source,left,0,half,source.getHeight());
        if(selected!=source)source.recycle();return selected;
    }
    private static boolean lightSurface(int foreground){return (((foreground>>16)&255)+((foreground>>8)&255)+(foreground&255))<384;}
    /** Consumes an owned decode. Keep the source's colours and white details; trim alpha only. */
    static Bitmap prepareCrest(Bitmap source,int targetPx,int foreground)throws IOException {
        int edge=Math.max(32,Math.min(512,targetPx));
        int width=source.getWidth(),height=source.getHeight();
        // The mark is drawn into an edge-square. A larger source adds a full-size int[] and
        // bitmap to every bind without visible detail, so bound the scan at 2x the draw size.
        int workLimit=Math.max(64,edge*2);
        if(Math.max(width,height)>workLimit){
            float scale=workLimit/(float)Math.max(width,height);
            int scaledWidth=Math.max(1,Math.round(width*scale)),scaledHeight=Math.max(1,Math.round(height*scale));
            Bitmap scaled=Bitmap.createScaledBitmap(source,scaledWidth,scaledHeight,true);
            if(scaled!=source)source.recycle();
            source=scaled;width=scaledWidth;height=scaledHeight;
        }
        int[] pixels=new int[width*height];
        source.getPixels(pixels,0,width,0,0,width,height);
        int left=width,top=height,right=-1,bottom=-1;double mass=0,dark=0,light=0;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            int pixel=pixels[y*width+x],alpha=pixel>>>24;if(alpha<8)continue;
            left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
            double weight=alpha/255.0;mass+=weight;
            int r=(pixel>>16)&255,g=(pixel>>8)&255,b=pixel&255;
            int high=Math.max(r,Math.max(g,b)),low=Math.min(r,Math.min(g,b));
            if(high-low<=16){if(high<96)dark+=weight;else if(low>220)light+=weight;}
        }
        if(right<left){source.recycle();throw new IOException("Empty crest image");}
        boolean lightSurface=lightSurface(foreground);
        boolean recolor=(!lightSurface&&light==0&&dark/mass>=.98)||(lightSurface&&dark==0&&light/mass>=.98);
        if(recolor){
            for(int i=0;i<pixels.length;i++)pixels[i]=(pixels[i]&0xff000000)|(foreground&0xffffff);
            Bitmap tinted=Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888);source.recycle();source=tinted;
        }
        int markWidth=right-left+1,markHeight=bottom-top+1;
        // Dense badges need more breathing room than a fine monogram or an open silhouette.
        float coverage=(float)(mass/(markWidth*(double)markHeight));
        float fill=.86f+.10f*(1-coverage);
        float scale=edge*fill/Math.max(markWidth,markHeight);
        float drawnWidth=markWidth*scale,drawnHeight=markHeight*scale;
        Bitmap result=Bitmap.createBitmap(edge,edge,Bitmap.Config.ARGB_8888);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        new Canvas(result).drawBitmap(source,new Rect(left,top,right+1,bottom+1),new RectF((edge-drawnWidth)/2,(edge-drawnHeight)/2,(edge+drawnWidth)/2,(edge+drawnHeight)/2),paint);
        source.recycle();return result;
    }
    private static int targetPixels(Ui ui,int size) {
        float density=ui.c.getResources().getDisplayMetrics().density;
        // 2x the layout size stays sharp on 420 dpi panels while keeping GPU textures small.
        return Math.max(48,Math.min(512,Math.round(size*density*2f)));
    }
    private static String safeHost(String url){try{return URI.create(url).getHost();}catch(RuntimeException e){return "image";}}
    private static byte[] bounded(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Image request cancelled");if(System.nanoTime()>deadline)throw new SocketTimeoutException("Image response timed out");if(out.size()+n>1_500_000)throw new IOException("Image too large");out.write(b,0,n);}return out.toByteArray();}
    private static String initials(String s){if(s==null||s.isBlank())return "—";String[] parts=s.trim().split("\\s+");return parts.length>1?(parts[0].substring(0,1)+parts[parts.length-1].substring(0,1)).toUpperCase(java.util.Locale.ROOT):s.substring(0,Math.min(2,s.length())).toUpperCase(java.util.Locale.ROOT);}
}
