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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.*;

public final class Repository {
    public static final class Batch {
        public final Competition competition;
        public final List<Match> matches=new ArrayList<>();
        public long fetched=0;
        public String error="";
        public Batch(Competition c) {competition=c;}
    }
    public static final class Snapshot {
        public final List<Batch> batches=new ArrayList<>();
        public List<Match> matches() {
            List<Match> all=new ArrayList<>();for(Batch b:batches) all.addAll(b.matches);
            all.sort(Comparator.comparingLong((Match m)->m.start==0?Long.MAX_VALUE:m.start).thenComparing(m->m.id));
            return all;
        }
        public boolean failed() {for(Batch b:batches)if(!b.error.isEmpty())return true;return false;}
        public void merge(Batch batch) {
            for(int i=0;i<batches.size();i++)if(batches.get(i).competition.id.equals(batch.competition.id)) {batches.set(i,batch);return;}
            batches.add(batch);
        }
    }
    private static final ExecutorService NETWORK=Executors.newFixedThreadPool(10);
    private static final ExecutorService DATES=Executors.newFixedThreadPool(8);
    private final Context context;
    private static final Object CACHE_LOCK=new Object();
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
        for(int i=0;i<count;i++) {
            try {
                Future<Batch> task=completed.take();Batch batch;
                try{batch=task.get();}catch(ExecutionException e){batch=load(tasks.get(task),from,to);batch.error="来源暂不可用，请稍后重试";android.util.Log.w("MyScore","Source task failed",e.getCause());}
                result.batches.add(batch);
                if(onBatch!=null)onBatch.accept(batch);
            } catch(InterruptedException e) {for(Future<Batch> task:tasks.keySet())task.cancel(true);Thread.currentThread().interrupt();break;}
        }
        return result;
    }
    private Batch fetchOne(Competition c,LocalDate from,LocalDate to,boolean force) {
        Batch b=load(c,from,to);long now=System.currentTimeMillis();
        long ttl=to.isAfter(from)?300_000:c.esports()?60_000:15_000;
        if(!force && b.fetched>0 && now-b.fetched>=0 && now-b.fetched<ttl) return b;
        try {
            List<Match> matches;
            if(c.id.equals("cs2")) matches=DomesticEsports.spirit(from,now);
            else if(c.id.equals("lol")) {matches=DomesticEsports.worlds(new JSONObject(get(DomesticEsports.LOL)),now);try{DetailSources.enrichWorlds(matches);}catch(Exception e){android.util.Log.i("MyScore","Worlds crests temporarily unavailable");}}
            else {
                matches=espn(c,from,to,now,force);
            }
            b.matches.clear();
            for(Match m:matches) if(m.day()==null || (!m.day().isBefore(from) && !m.day().isAfter(to))) b.matches.add(m);
            b.fetched=now;b.error="";save(b,from,to);
        } catch(Exception e) {
            b.error=e instanceof java.net.SocketTimeoutException?"连接超时":e.getMessage()==null?"连接失败":e.getMessage();
        }
        return b;
    }
    private List<Match> espn(Competition c,LocalDate from,LocalDate to,long now,boolean force) throws Exception {
        if(c.id.equals("f1")) {
            String dates=from.minusDays(7).format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+to.plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE);
            return Parsers.espn(new JSONObject(get("https://site.api.espn.com/apis/site/v2/sports/"+c.path+"/scoreboard?dates="+dates+"&limit=1000")),c,now);
        }
        List<Future<List<Match>>> days=new ArrayList<>();
        // Team-sport scoreboards reject date ranges; fetch individual source dates.
        // Include the preceding source date to cover overnight games in Beijing time.
        for(LocalDate d=from.minusDays(1);!d.isAfter(to);d=d.plusDays(1)) {
            LocalDate day=d;
            days.add(DATES.submit(()-> {
                Batch cached=load(c,day,day,"day_");
                LocalDate utc=InstantNow.utcDay(now);
                long ttl=day.isAfter(utc)?30*60_000L:day.isBefore(utc.minusDays(1))?6*60*60_000L:15_000L;
                if(!force && cached.fetched>0 && now-cached.fetched>=0 && now-cached.fetched<ttl)return cached.matches;
                String url="https://site.api.espn.com/apis/site/v2/sports/"+c.path+"/scoreboard?dates="+day.format(DateTimeFormatter.BASIC_ISO_DATE)+"&limit=1000";
                List<Match> parsed=Parsers.espn(new JSONObject(get(url)),c,now);
                Batch fresh=new Batch(c);fresh.matches.addAll(parsed);fresh.fetched=now;save(fresh,day,day,"day_");
                return parsed;
            }));
        }
        Map<String,Match> unique=new java.util.LinkedHashMap<>();
        for(Future<List<Match>> f:days)for(Match m:f.get())unique.put(m.id,m);
        return new ArrayList<>(unique.values());
    }
    private static final class InstantNow {
        static LocalDate utcDay(long now){return java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneOffset.UTC).toLocalDate();}
    }
    private List<Match> esports(Competition c,LocalDate from,LocalDate to,long now) throws Exception {
        List<Match> all=new ArrayList<>();
        Map<String,String> p=new java.util.LinkedHashMap<>();
        p.put("page[limit]","100");p.put("sort","start_date");
        p.put("filter[matches.discipline_id][eq]",c.id.equals("cs2")?"1":"3");
        if(c.id.equals("cs2"))p.put("filter[matches.team_ids][overlap]","654");
        // API dates are interpreted with offsets; widen bounds and filter again in Shanghai time.
        p.put("filter[matches.start_date][gt]",from.minusDays(1).toString());
        p.put("filter[matches.start_date][lt]",to.plusDays(1).toString());
        p.put("with","teams,tournament,games");
        for(int page=0;page<5;page++) {
            p.put("page[offset]",Integer.toString(page*100));
            StringBuilder u=new StringBuilder("https://api.bo3.gg/api/v1/matches?");
            for(Map.Entry<String,String> item:p.entrySet())u.append(URLEncoder.encode(item.getKey(),"UTF-8")).append('=').append(URLEncoder.encode(item.getValue(),"UTF-8")).append('&');
            JSONObject root=new JSONObject(get(u.toString()));
            all.addAll(Parsers.bo3(root,c,now));
            JSONObject total=root.optJSONObject("total");
            if(total==null)throw new Exception("电竞来源缺少分页信息");
            if((page+1)*100>=total.optInt("count"))return all;
        }
        throw new Exception("电竞列表超过分页上限，覆盖尚不完整");
    }
    public static String get(String url) throws Exception {
        return get(url,java.util.Collections.emptyMap());
    }
    static String get(String url,Map<String,String> headers) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
        try {
            if(!headers.isEmpty())connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(6_000);connection.setReadTimeout(8_000);
            connection.setRequestProperty("Accept","application/json");
            connection.setRequestProperty("User-Agent","MyScore-Personal/0.3 Android");
            for(Map.Entry<String,String> h:headers.entrySet())connection.setRequestProperty(h.getKey(),h.getValue());
            if(connection.getResponseCode()!=200)throw new Exception("来源返回 HTTP "+connection.getResponseCode());
            try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192];int n;
                while((n=in.read(buffer))!=-1) {if(out.size()+n>8_000_000)throw new Exception("来源响应过大");out.write(buffer,0,n);}
                return out.toString("UTF-8");
            }
        } finally {connection.disconnect();}
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
                b.fetched=root.optLong("fetched");JSONArray a=root.optJSONArray("matches");
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
                JSONObject root=new JSONObject().put("fetched",b.fetched).put("matches",a);
                out=file.startWrite();out.write(root.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);
            } catch(Exception e) {if(out!=null)file.failWrite(out);android.util.Log.w("MyScore","Cannot persist score cache");}
            // Keep score cache bounded; no personal calendar data is stored here.
            File[] files=cacheFile(b.competition,from,to).getParentFile().listFiles();
            if(files!=null)for(File old:files)if(System.currentTimeMillis()-old.lastModified()>14L*24*60*60_000 && !old.delete())android.util.Log.d("MyScore","Cache cleanup deferred");
        }
    }
}
