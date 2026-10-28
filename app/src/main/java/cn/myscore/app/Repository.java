package cn.myscore.app;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.*;

public final class Repository {
    public static final class Batch {
        public final Competition competition;
        public final List<Match> matches=new ArrayList<>();
        public long fetched=0;
        public long retryAt=0;
        public String error="";
        public Batch(Competition c) {competition=c;}
    }
    public static final class Snapshot {
        public final List<Batch> batches=new ArrayList<>();
        private List<Match> sorted;
        public List<Match> matches() {
            if(sorted!=null)return sorted;
            List<Match> all=new ArrayList<>();for(Batch b:batches) all.addAll(b.matches);
            all.sort(Comparator.comparingLong((Match m)->m.start==0?Long.MAX_VALUE:m.start).thenComparing(m->m.id));
            sorted=all;return sorted;
        }
        public boolean failed() {for(Batch b:batches)if(!b.error.isEmpty())return true;return false;}
        /** A newer exact-match detail can arrive before the next scoreboard poll. */
        boolean updateMatch(Match next) {
            for(int b=0;b<batches.size();b++) {
                Batch current=batches.get(b);
                if(!current.competition.id.equals(next.league))continue;
                for(int i=0;i<current.matches.size();i++) {
                    Match previous=current.matches.get(i);
                    if(!previous.id.equals(next.id))continue;
                    if(next.updated<=previous.updated)return false;
                    // Day memories may share the old batch. Replace it rather than mutating it.
                    Batch replacement=new Batch(current.competition);
                    replacement.fetched=current.fetched;replacement.retryAt=current.retryAt;replacement.error=current.error;
                    replacement.matches.addAll(current.matches);replacement.matches.set(i,next);
                    batches.set(b,replacement);sorted=null;return true;
                }
            }
            return false;
        }
        public void merge(Batch batch) {
            for(int i=0;i<batches.size();i++)if(batches.get(i).competition.id.equals(batch.competition.id)) {batches.set(i,batch);sorted=null;return;}
            batches.add(batch);sorted=null;
        }
    }
    private static final ExecutorService NETWORK=Executors.newFixedThreadPool(16);
    private record Request(String url,Map<String,String> headers){}
    private static final SingleFlight<Request,String> REQUESTS=new SingleFlight<>();
    private final Context context;
    private static final Object CACHE_LOCK=new Object();
    private static final java.util.concurrent.atomic.AtomicLong CACHE_WRITES=new java.util.concurrent.atomic.AtomicLong();
    private record Failure(int count,long until,String message){}
    private final Map<String,Failure> failures=new ConcurrentHashMap<>();
    public Repository(Context c) {context=c.getApplicationContext();}

    public Snapshot cached(LocalDate from, LocalDate to, String group) {
        Snapshot result=new Snapshot();
        for(Competition c:Competition.ALL) if(group.equals("全部")||c.group.equals(group)) result.batches.add(load(c,from,to));
        return result;
    }
    public Snapshot fetch(LocalDate from, LocalDate to, String group, boolean force) {
        return fetch(from,to,group,force,null);
    }
    /** Reports each source as soon as it finishes, so the first cards do not wait for every feed. */
    public Snapshot fetch(LocalDate from, LocalDate to, String group, boolean force, java.util.function.Consumer<Batch> onBatch) {
        Snapshot result=new Snapshot();
        java.util.concurrent.CompletionService<Batch> completed=new java.util.concurrent.ExecutorCompletionService<>(NETWORK);
        Map<Future<Batch>,Competition> tasks=new HashMap<>();
        int count=0;
        for(Competition c:Competition.ALL) if(group.equals("全部")||c.group.equals(group)) {tasks.put(completed.submit(()->fetchOne(c,from,to,force)),c);count++;}
        Set<String> added=new HashSet<>();
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(RefreshPolicy.REFRESH_DEADLINE);
        try {
            for(int i=0;i<count;i++) {
                Future<Batch> task;
                long remaining=deadline-System.nanoTime();
                try{task=remaining>0?completed.poll(remaining,TimeUnit.NANOSECONDS):completed.poll();}
                catch(InterruptedException e){Thread.currentThread().interrupt();return result;}
                if(task==null) {
                    // A hung source must not keep every other source and the next tick waiting.
                    android.util.Log.w("MyScore","Source refresh deadline reached");
                    for(Future<Batch> pending:tasks.keySet())if(!pending.isDone())pending.cancel(true);
                    addTimedOut(tasks,result,added,from,to,onBatch);
                    break;
                }
                Competition c=tasks.get(task);
                if(c==null||added.contains(c.id))continue;
                Batch batch;
                try{batch=task.get();}
                catch(InterruptedException e){Thread.currentThread().interrupt();return result;}
                catch(ExecutionException e){batch=load(c,from,to);batch.error="来源暂不可用，请稍后重试";batch.retryAt=recordFailure(c.id+":"+from+":"+to,failures.get(c.id+":"+from+":"+to),batch.error).until();android.util.Log.w("MyScore","Source task failed",e.getCause());}
                catch(CancellationException e){batch=load(c,from,to);batch.error="来源响应超时";batch.retryAt=recordFailure(c.id+":"+from+":"+to,failures.get(c.id+":"+from+":"+to),batch.error).until();}
                added.add(c.id);result.batches.add(batch);
                if(onBatch!=null)onBatch.accept(batch);
            }
        } finally {
            for(Future<Batch> pending:tasks.keySet())if(!pending.isDone())pending.cancel(true);
        }
        return result;
    }

    /** Fill every selected source after the deadline, using a real cache when one exists. */
    private void addTimedOut(Map<Future<Batch>,Competition> tasks,Snapshot result,Set<String> added,LocalDate from,LocalDate to,java.util.function.Consumer<Batch> onBatch) {
        for(Map.Entry<Future<Batch>,Competition> entry:tasks.entrySet()) {
            Competition c=entry.getValue();if(added.contains(c.id))continue;
            Future<Batch> task=entry.getKey();Batch batch=null;
            if(task.isDone()&&!task.isCancelled()) {
                try{batch=task.get();}catch(Exception ignored){}
            }
            if(batch==null) {
                batch=load(c,from,to);batch.error="来源响应超时";
                String key=c.id+":"+from+":"+to;batch.retryAt=recordFailure(key,failures.get(key),batch.error).until();
            }
            added.add(c.id);result.batches.add(batch);
            if(onBatch!=null)onBatch.accept(batch);
        }
    }
    private Batch fetchOne(Competition c,LocalDate from,LocalDate to,boolean force) {
        Batch b=load(c,from,to);long now=System.currentTimeMillis();
        String key=c.id+":"+from+":"+to;Failure previous=failures.get(key);
        // A settled view is re-read only after a long interval, so a later reschedule or a
        // source correction can still reach an open page without polling every ten seconds.
        if(!force && b.error.isEmpty() && b.fetched>0 && RefreshPolicy.settled(b.matches)
            && now-b.fetched>=0 && now-b.fetched<RefreshPolicy.SETTLED_RECHECK) {
            android.util.Log.i("MyScorePerf","skip settled view source="+c.id);
            return b;
        }
        if(!force&&previous!=null&&previous.until>now){b.error=previous.message;b.retryAt=previous.until;return b;}
        long age=now-b.fetched;
        boolean allowTerminalUpdate=!force && b.error.isEmpty() && b.fetched>0
            && RefreshPolicy.settled(b.matches) && age>=RefreshPolicy.SETTLED_RECHECK;
        long ttl=to.isAfter(from)?300_000:RefreshPolicy.cacheTtl(b.matches,from,Match.today(now),now);
        // A previous failure must be retried for real after its backoff, not hidden by a fresh TTL.
        boolean retry=previous!=null||!b.error.isEmpty();
        if(!force && !retry && b.error.isEmpty() && b.fetched>0 && now-b.fetched>=0 && now-b.fetched<ttl) return b;
        try {
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            b.error="";
            List<Match> matches;
            if(c.id.equals("cs2")) matches=DomesticEsports.spirit(from,now);
            else if(c.id.equals("lol")) {matches=DomesticEsports.worlds(new JSONObject(get(DomesticEsports.LOL)),now);try{DetailSources.enrichWorlds(matches);}catch(Exception e){android.util.Log.i("MyScore","Worlds crests temporarily unavailable");}}
            else {
                matches=espn(c,from,to,now,force,retry,b);
            }
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            if(!force) matches=mergeTerminal(b.matches,matches,allowTerminalUpdate);
            b.matches.clear();
            boolean today=from.equals(to)&&from.equals(Match.today(now));
            for(Match m:matches) {
                if(m.day()==null) {
                    // Unknown-time entries cannot be assigned to a specific day; only surface them
                    // on today's view instead of duplicating them into every browsed date.
                    if(today)b.matches.add(m);
                } else if(!m.day().isBefore(from)&&!m.day().isAfter(to)) b.matches.add(m);
            }
            if(b.error.isEmpty()){b.fetched=System.currentTimeMillis();b.retryAt=0;failures.remove(key);}
            else b.retryAt=recordFailure(key,previous,b.error).until();
            save(b,from,to);
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();b.error="请求已取消";
        } catch(Exception e) {
            b.error=e instanceof java.net.SocketTimeoutException?"连接超时":e.getMessage()==null?"连接失败":e.getMessage();
            if(!Thread.currentThread().isInterrupted())b.retryAt=recordFailure(key,previous,b.error).until();
        }
        return b;
    }
    private Failure recordFailure(String key,Failure previous,String message){int count=previous==null?1:previous.count+1;if(failures.size()>100){long now=System.currentTimeMillis();failures.entrySet().removeIf(entry->entry.getValue().until()<=now);if(failures.size()>100)failures.clear();}Failure next=new Failure(count,System.currentTimeMillis()+RefreshPolicy.retry(RefreshPolicy.SCORES,count),message);failures.put(key,next);return next;}
    private List<Match> espn(Competition c,LocalDate from,LocalDate to,long now,boolean force,boolean retry,Batch view) throws Exception {
        if(c.id.equals("f1")) {
            String dates=from.minusDays(7).format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+to.plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE);
            return Parsers.espn(new JSONObject(get("https://site.api.espn.com/apis/site/v2/sports/"+c.path+"/scoreboard?dates="+dates+"&limit=1000")),c,now);
        }
        // Team-sport scoreboards reject date ranges, so fetch individual source dates on this
        // worker thread. Keeping both requests on one thread avoids a second pool that could
        // queue behind other screens and make the foreground deadline cancel healthy work.
        List<Batch> batches=new ArrayList<>();
        for(LocalDate d=from.minusDays(1);!d.isAfter(to);d=d.plusDays(1)) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            batches.add(fetchSourceDay(c,d,now,force,retry));
        }
        return mergeDays(view,batches);
    }
    private Batch fetchSourceDay(Competition c,LocalDate day,long now,boolean force,boolean retry)throws InterruptedException {
        Batch cached=load(c,day,day,"day_");
        LocalDate utc=InstantNow.utcDay(now);
        // Every match on this source date is final/cancelled and the last read succeeded.
        if(!force && cached.error.isEmpty() && cached.fetched>0 && RefreshPolicy.settled(cached.matches)
            && now-cached.fetched>=0 && now-cached.fetched<RefreshPolicy.SETTLED_RECHECK) {
            android.util.Log.i("MyScorePerf","skip settled source="+c.id+" day="+day);
            return cached;
        }
        long ttl=RefreshPolicy.cacheTtl(cached.matches,day,utc,now);
        if(!force && !retry && cached.fetched>0 && now-cached.fetched>=0 && now-cached.fetched<ttl)return cached;
        String url="https://site.api.espn.com/apis/site/v2/sports/"+c.path+"/scoreboard?dates="+day.format(DateTimeFormatter.BASIC_ISO_DATE)+"&limit=1000";
        try{
            List<Match> parsed=Parsers.espn(new JSONObject(get(url)),c,System.currentTimeMillis());
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            boolean allowTerminalUpdate=force || (cached.error.isEmpty() && cached.fetched>0
                && RefreshPolicy.settled(cached.matches) && now-cached.fetched>=RefreshPolicy.SETTLED_RECHECK);
            List<Match> merged=force?parsed:mergeTerminal(cached.matches,parsed,allowTerminalUpdate);
            Batch fresh=new Batch(c);fresh.matches.addAll(merged);fresh.fetched=System.currentTimeMillis();save(fresh,day,day,"day_");
            return fresh;
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw e;}
        catch(Exception e){android.util.Log.w("MyScore","Score date "+c.id+" "+day+" failed: "+e.getClass().getSimpleName()+": "+e.getMessage());cached.error=day+" 赛程未更新";return cached;}
    }
    static List<Match> mergeDays(Batch view,List<Batch> days)throws java.io.IOException {
        List<String> errors=new ArrayList<>();for(Batch day:days)if(!day.error.isEmpty())errors.add(day.error);
        view.error=String.join("；",errors);
        if(errors.size()==days.size())throw new java.io.IOException(view.error);
        Map<String,Match> unique=new java.util.LinkedHashMap<>();
        // Read stale days first, then let a successful source replace the same event identity.
        for(boolean failed:new boolean[]{true,false})for(Batch day:days)if((!day.error.isEmpty())==failed)for(Match m:day.matches){Match old=unique.get(m.id);if(old==null||m.updated>=old.updated)unique.put(m.id,m);}
        return new ArrayList<>(unique.values());
    }
    /**
     * Automatic reads keep a source-confirmed terminal match exactly as it was stored. A fresh
     * terminal entry replaces a live one; a terminal entry missing from a successful response is
     * retained, so a temporary source omission cannot erase a finished result.
     */
    static List<Match> mergeTerminal(List<Match> cached,List<Match> fresh) {
        return mergeTerminal(cached,fresh,false);
    }
    /**
     * @param allowTerminalUpdate after a long recheck, a source-confirmed terminal entry is
     *        allowed to replace the stored one so a corrected final score/cancellation recovers.
     */
    static List<Match> mergeTerminal(List<Match> cached,List<Match> fresh,boolean allowTerminalUpdate) {
        Map<String,Match> locked=new java.util.LinkedHashMap<>();
        if(cached!=null)for(Match m:cached)if(m!=null&&RefreshPolicy.terminal(m))locked.put(m.id,m);
        List<Match> merged=new ArrayList<>();Set<String> seen=new HashSet<>();
        if(fresh!=null)for(Match m:fresh) {
            if(m==null)continue;Match use=locked.get(m.id);
            if(seen.add(m.id))merged.add(allowTerminalUpdate?m:(use==null?m:use));
        }
        for(Match m:locked.values())if(seen.add(m.id))merged.add(m);
        return merged;
    }
    private static final class InstantNow {
        static LocalDate utcDay(long now){return java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneOffset.UTC).toLocalDate();}
    }
    public static String get(String url) throws Exception {
        return get(url,java.util.Collections.emptyMap());
    }
    static String get(String url,Map<String,String> headers) throws Exception {
        Request request=new Request(url,Map.copyOf(headers));
        return REQUESTS.execute(request,()->download(request.url,request.headers));
    }
    private static String download(String url,Map<String,String> headers) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
        // Keep the socket in the shared pool after a successful read: the scoreboard asks the
        // same host for several dates in a row, and a fresh TLS handshake per request is the
        // slowest part on a mobile network. Failed reads still drop the connection.
        boolean complete=false;
        try {
            if(!headers.isEmpty())connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(6_000);connection.setReadTimeout(8_000);
            connection.setRequestProperty("Accept","application/json");
            connection.setRequestProperty("User-Agent","MyScore-Personal/"+BuildConfig.VERSION_NAME+" Android");
            for(Map.Entry<String,String> h:headers.entrySet())connection.setRequestProperty(h.getKey(),h.getValue());
            if(connection.getResponseCode()!=200)throw new Exception("来源返回 HTTP "+connection.getResponseCode());
            try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192];int n;
                long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
                while((n=in.read(buffer))!=-1) {if(Thread.currentThread().isInterrupted())throw new InterruptedException();if(System.nanoTime()>deadline)throw new java.net.SocketTimeoutException("来源响应超时");if(out.size()+n>8_000_000)throw new Exception("来源响应过大");out.write(buffer,0,n);}
                String body=out.toString("UTF-8");
                complete=true;
                return body;
            }
        } finally {if(!complete)connection.disconnect();}
    }
    private File cacheFile(Competition c,LocalDate from,LocalDate to) {return cacheFile(c,from,to,"view_");}
    private File cacheFile(Competition c,LocalDate from,LocalDate to,String prefix) {
        File dir=new File(context.getCacheDir(),"scores-v1");
        if(!dir.exists()&&!dir.mkdirs())android.util.Log.w("MyScore","Cannot create cache directory");
        return new File(dir,prefix+c.id+"_"+from+"_"+to+".json");
    }
    private Batch load(Competition c,LocalDate from,LocalDate to) {return load(c,from,to,"view_");}
    private Batch load(Competition c,LocalDate from,LocalDate to,String prefix) {
        Batch b=new Batch(c);
        synchronized(CACHE_LOCK) {
            try {
                File file=cacheFile(c,from,to,prefix);if(!file.exists())return b;
                JSONObject root=new JSONObject(new String(new AtomicFile(file).readFully(),StandardCharsets.UTF_8));
                b.fetched=root.optLong("fetched");b.error=root.optString("error");JSONArray a=root.optJSONArray("matches");
                if(a!=null)for(int i=0;i<a.length();i++)if(a.optJSONObject(i)!=null)b.matches.add(Match.fromJson(a.optJSONObject(i)));
            } catch(Exception e) {android.util.Log.w("MyScore","Ignoring unreadable score cache");}
        }
        return b;
    }
    private void save(Batch b,LocalDate from,LocalDate to) {save(b,from,to,"view_");}
    private void save(Batch b,LocalDate from,LocalDate to,String prefix) {
        synchronized(CACHE_LOCK) {
            AtomicFile file=new AtomicFile(cacheFile(b.competition,from,to,prefix));FileOutputStream out=null;
            try {
                JSONArray a=new JSONArray();for(Match m:b.matches)a.put(m.json());
                JSONObject root=new JSONObject().put("fetched",b.fetched).put("error",b.error).put("matches",a);
                out=file.startWrite();out.write(root.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);
            } catch(Exception e) {if(out!=null)file.failWrite(out);android.util.Log.w("MyScore","Cannot persist score cache");}
            // Keep score cache bounded; no personal calendar data is stored here.
            if(CACHE_WRITES.incrementAndGet()%32==1) {
                File[] files=cacheFile(b.competition,from,to).getParentFile().listFiles();
                if(files!=null)for(File old:files)if(System.currentTimeMillis()-old.lastModified()>14L*24*60*60_000 && !old.delete())android.util.Log.d("MyScore","Cache cleanup deferred");
            }
        }
    }
}
