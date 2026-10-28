package cn.myscore.app;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.json.JSONObject;

final class DetailRepository {
    private static final Object CACHE_LOCK=new Object();
    private static final java.util.concurrent.atomic.AtomicLong WRITES=new java.util.concurrent.atomic.AtomicLong();
    private static final Set<String> ENRICHING=ConcurrentHashMap.newKeySet();
    private static final ExecutorService PROFILES=Executors.newSingleThreadExecutor(r->{
        Thread thread=new Thread(r,"myscore-football-profiles");
        thread.setDaemon(true);
        return thread;
    });
    private final File dir;
    private final Context context;
    DetailRepository(Context c){context=c.getApplicationContext();dir=new File(c.getCacheDir(),"details-v4");if(!dir.exists())dir.mkdirs();}
    private File file(Match m){return new File(dir,m.id.replaceAll("[^a-zA-Z0-9_-]","_")+".json");}
    MatchDetail cached(Match m){synchronized(CACHE_LOCK){try{File f=file(m);if(!f.exists())return null;MatchDetail d=MatchDetail.fromJson(new JSONObject(new String(new AtomicFile(f).readFully(),StandardCharsets.UTF_8)));if(!d.match.id.equals(m.id))return null;d.bodySignature();return d;}catch(Exception e){return null;}}}
    MatchDetail fetch(Match m,boolean force)throws Exception {
        MatchDetail old=cached(m);
        boolean migration=old!=null&&(needsSideRefresh(m,old)||(m.group.equals("足球")&&old.facts.stream().noneMatch(f->f.label().equals("阵容资料版本"))));
        long now=System.currentTimeMillis();
        // A detail already fetched after both sides reached a terminal state is the final record.
        // After SETTLED_RECHECK it is re-read once so a source correction can still land.
        boolean frozen=old!=null&&old.fetched>0&&RefreshPolicy.frozenDetail(m,old.match)
            && now-old.fetched>=0&&now-old.fetched<RefreshPolicy.SETTLED_RECHECK;
        if(!force&&old!=null&&!migration&&(frozen||RefreshPolicy.freshDetail(m,old.match,old.fetched,now)))return old;
        MatchDetail next=DetailSources.fetch(m);retainGames(old,next);
        if(m.group.equals("足球")&&next.facts.stream().noneMatch(f->f.label().equals("阵容资料版本")))next.fact("阵容资料版本","0");
        next.bodySignature();
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();
        return write(next);
    }
    /** Add expensive football profile data after the base detail is already visible. */
    void enrichProfiles(MatchDetail detail,Consumer<MatchDetail> done) {
        if(detail==null||!"足球".equals(detail.match.group)||hasProfiles(detail)) {done.accept(detail);return;}
        String key=detail.match.id+"|"+detail.fetched;
        if(!ENRICHING.add(key)) {done.accept(detail);return;}
        PROFILES.execute(() -> {
            MatchDetail enriched=null;
            try {
                enriched=MatchDetail.fromJson(detail.json());
                new FootballProfiles(context).enrich(enriched);
                MatchDetail stored=write(enriched);
                if(stored!=null)enriched=stored;
            } catch(Throwable e) {
                android.util.Log.i("MyScore","Football profiles deferred: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));
            } finally {ENRICHING.remove(key);}
            done.accept(enriched==null?detail:enriched);
        });
    }
    private static boolean hasProfiles(MatchDetail detail) {
        for(MatchDetail.Fact fact:detail.facts) {
            if(fact.label().equals("阵容资料版本")&&fact.value().equals("1"))return true;
        }
        return false;
    }
    private MatchDetail write(MatchDetail next) {
        synchronized(CACHE_LOCK) {
            // A tablet pane and a full detail page may finish the same match out of order.
            MatchDetail current=cached(next.match);
            if(current!=null&&current.fetched>next.fetched)return current;
            AtomicFile atomic=new AtomicFile(file(next.match));FileOutputStream out=null;
            try{out=atomic.startWrite();out.write(next.json().toString().getBytes(StandardCharsets.UTF_8));atomic.finishWrite(out);}catch(Exception e){if(out!=null)atomic.failWrite(out);}
            if(WRITES.incrementAndGet()%32==1){File[] oldFiles=dir.listFiles();if(oldFiles!=null)for(File f:oldFiles)if(System.currentTimeMillis()-f.lastModified()>14L*86400_000)f.delete();}
        }
        return next;
    }
    /** Old CS2 caches stored half labels or T/CT without the opening side; one live read adds roles. */
    static boolean needsSideRefresh(Match match,MatchDetail old){
        if(old==null||!match.group.equals("CS2"))return false;
        for(MatchDetail.Table table:old.tables){
            if(!table.tab.equals("events"))continue;
            boolean oldHalves=table.columns.contains("上半场"),sidesWithoutStart=table.columns.contains("T 方")&&!table.columns.contains("首开方");
            if(!oldHalves&&!sidesWithoutStart)continue;
            for(List<String> row:table.rows)for(int i=1;i<row.size();i++){String value=row.get(i);if(!value.equals("—")&&!value.equals("0"))return true;}
        }
        return false;
    }
    static void retainGames(MatchDetail old,MatchDetail next){
        if(old!=null&&old.match.id.equals(next.match.id)&&next.match.group.equals("LOL")&&next.lolGames.isEmpty()&&!old.lolGames.isEmpty()){
            next.lolGames.addAll(old.lolGames);next.lolFetched=old.lolFetched;next.warning="小局暂未更新，正在显示上次获取的阵容与统计。";
        }
    }
}
