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
    private static final long TTL=30*60_000L;
    private static final Object LOCK=new Object();
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
                result.fetched=root.optLong("fetched");
                result.source=root.optString("source");
                JSONArray matches=root.optJSONArray("matches");
                if(matches!=null)for(int i=0;i<matches.length();i++)if(matches.optJSONObject(i)!=null)result.matches.add(Match.fromJson(matches.optJSONObject(i)));
            } catch(Exception e) {
                android.util.Log.w("MyScore","Ignoring unreadable team cache");
            }
        }
        return result;
    }

    Result fetch(Team team,boolean force) {
        Result cached=cached(team);
        long now=System.currentTimeMillis();
        if(!force&&cached.fetched>0&&now-cached.fetched>=0&&now-cached.fetched<TTL)return cached;
        try {
            TeamSchedule.Result fresh=TeamSchedule.fetch(team,now);
            if(fresh.fetched<=0)fresh.fetched=now;
            save(team,fresh);
            Result result=new Result();
            result.matches.addAll(fresh.matches);result.fetched=fresh.fetched;result.source=fresh.source;
            return result;
        } catch(Exception e) {
            cached.error=friendly(e);
            return cached;
        }
    }
    static String friendly(Exception e) {
        if(e instanceof InterruptedException)return "请求已取消";
        if(e instanceof java.net.SocketTimeoutException)return "连接超时";
        String message=e.getMessage();
        return message==null||message.isBlank()?"连接失败":message;
    }
    private String key(Team team){return team.league+":"+team.id;}
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
                JSONObject root=new JSONObject().put("team",key(team)).put("fetched",value.fetched).put("source",value.source).put("matches",matches);
                out=file.startWrite();out.write(root.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);
            } catch(Exception e) {
                if(out!=null)file.failWrite(out);
                android.util.Log.w("MyScore","Cannot persist team cache");
            }
            File[] files=file(team).getParentFile().listFiles();
            if(files!=null)for(File old:files)if(System.currentTimeMillis()-old.lastModified()>14L*24*60*60_000&&!old.delete())android.util.Log.d("MyScore","Team cache cleanup deferred");
        }
    }
}
