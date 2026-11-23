package cn.myscore.app;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Disk cache for team schedules. A failed refresh keeps the last real schedule and reports why it
 * could not update; an empty successful response is cached as an empty schedule, never as a failure.
 */
final class TeamRepository {
    private static final Object LOCK=new Object();
    private static final java.util.concurrent.atomic.AtomicLong WRITES=new java.util.concurrent.atomic.AtomicLong();
    // Older CS2 team caches predate the tier-one upcoming-match enrichment; read them once, then refresh.
    private static final int CS2_TEAM_SCHEMA=1;
    static final class Result {
        final List<Match> matches=new ArrayList<>();
        long fetched=0;
        String source="", error="";
        boolean stale() {return fetched>0&&!error.isEmpty();}
    }
    private final Context context;
    TeamRepository(Context c){context=c.getApplicationContext();}

    Result cached(Team team) {
        Result result=new Result();
        synchronized(LOCK) {
            try {
                File file=file(team);
                if(!file.exists())return result;
                JSONObject root=new JSONObject(new String(new AtomicFile(file).readFully(),StandardCharsets.UTF_8));
                if(!key(team).equals(root.optString("team")))return result;
                boolean reset=root.optInt("schema",0)<cacheSchema(team);
                result.fetched=root.optLong("fetched");
                result.source=root.optString("source");
                JSONArray matches=root.optJSONArray("matches");
                if(matches!=null)for(int i=0;i<matches.length();i++)if(matches.optJSONObject(i)!=null)result.matches.add(Match.fromJson(matches.optJSONObject(i)));
                // Keep the last real list for a failed refresh, but never let it satisfy the freshness check.
                if(reset)result.fetched=0;
            } catch(Exception e) {
                android.util.Log.w("MyScore","Ignoring unreadable team cache");
            }
        }
        return result;
    }

    Result fetch(Team team,boolean force) {
        return fetch(team,force,true);
    }
    Result fetch(Team team,boolean force,boolean observeCalendar) {
        Result cached=cached(team);
        long now=System.currentTimeMillis();
        if(!force&&fresh(cached,now)){if(observeCalendar)CalendarUpdates.observe(context,cached.matches);return cached;}
        try {
            TeamSchedule.Result fresh=TeamSchedule.fetch(team,now);
            if(fresh.fetched<=0)fresh.fetched=now;
            save(team,fresh);
            Result result=new Result();
            result.matches.addAll(fresh.matches);result.fetched=fresh.fetched;result.source=fresh.source;
            if(observeCalendar)CalendarUpdates.observe(context,result.matches);return result;
        } catch(Exception e) {
            cached.error=friendly(e);
            return cached;
        }
    }
    static boolean fresh(Result result,long now) {
        if(result==null||!result.error.isEmpty()||result.fetched<=0)return false;
        if(result.matches.stream().anyMatch(Match::needsScoreRefresh))return false;
        long age=now-result.fetched;
        return age>=0&&age<RefreshPolicy.teamCacheTtl(result.matches,now);
    }
    static String friendly(Exception e) {
        if(e instanceof InterruptedException)return "Request cancelled";
        if(e instanceof java.net.SocketTimeoutException)return "Connection timed out";
        String message=e.getMessage();
        return message==null||message.isBlank()?"Connection failed":message;
    }
    private String key(Team team){return team.league+":"+team.id;}
    private static int cacheSchema(Team team) {
        if(team.league.equals("cs2"))return CS2_TEAM_SCHEMA;
        return NationalTeams.follows(team)?NationalTeams.SCHEDULE_SCHEMA:0;
    }
    private File file(Team team) {
        File dir=new File(context.getCacheDir(),"teams-v1");
        if(!dir.exists()&&!dir.mkdirs())android.util.Log.w("MyScore","Cannot create team cache directory");
        return new File(dir,team.league+"_"+team.id.replaceAll("[^a-zA-Z0-9_-]","_")+".json");
    }
    private void save(Team team,TeamSchedule.Result value) {
        synchronized(LOCK) {
            AtomicFile file=new AtomicFile(file(team));
            FileOutputStream out=null;
            try {
                JSONArray matches=new JSONArray();
                for(Match m:value.matches)matches.put(m.json());
                JSONObject root=new JSONObject().put("team",key(team)).put("fetched",value.fetched).put("source",value.source).put("schema",cacheSchema(team)).put("matches",matches);
                out=file.startWrite();out.write(root.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);
            } catch(Exception e) {
                if(out!=null)file.failWrite(out);
                android.util.Log.w("MyScore","Cannot persist team cache");
            }
            if(WRITES.incrementAndGet()%32==1) {
                File[] files=file(team).getParentFile().listFiles();
                if(files!=null)for(File old:files)if(System.currentTimeMillis()-old.lastModified()>14L*24*60*60_000&&!old.delete())android.util.Log.d("MyScore","Team cache cleanup deferred");
            }
        }
    }
}
