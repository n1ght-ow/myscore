package cn.myscore.app;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.json.JSONObject;

final class DetailRepository {
    private final File dir;
    private final Context context;
    DetailRepository(Context c){context=c.getApplicationContext();dir=new File(c.getCacheDir(),"details-v4");if(!dir.exists())dir.mkdirs();}
    private File file(Match m){return new File(dir,m.id.replaceAll("[^a-zA-Z0-9_-]","_")+".json");}
    MatchDetail cached(Match m){try{File f=file(m);if(!f.exists())return null;MatchDetail d=MatchDetail.fromJson(new JSONObject(new String(new AtomicFile(f).readFully(),StandardCharsets.UTF_8)));if(!d.match.id.equals(m.id))return null;d.bodySignature();return d;}catch(Exception e){return null;}}
    MatchDetail fetch(Match m,boolean force)throws Exception {
        MatchDetail old=cached(m);
        if(!force&&old!=null&&!needsSideRefresh(m,old)&&(!m.group.equals("足球")||old.facts.stream().anyMatch(f->f.label().equals("阵容资料版本")&&f.value().equals("1")))&&RefreshPolicy.freshDetail(m,old.match,old.fetched,System.currentTimeMillis()))return old;
        MatchDetail next=DetailSources.fetch(m);retainGames(old,next);if(m.group.equals("足球"))new FootballProfiles(context).enrich(next);next.bodySignature();AtomicFile atomic=new AtomicFile(file(m));FileOutputStream out=null;try{out=atomic.startWrite();out.write(next.json().toString().getBytes(StandardCharsets.UTF_8));atomic.finishWrite(out);}catch(Exception e){if(out!=null)atomic.failWrite(out);}
        File[] oldFiles=dir.listFiles();if(oldFiles!=null)for(File f:oldFiles)if(System.currentTimeMillis()-f.lastModified()>14L*86400_000)f.delete();return next;
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
