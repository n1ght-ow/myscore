package cn.myscore.app;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

final class DetailRepository {
    private final File dir;
    DetailRepository(Context c){dir=new File(c.getCacheDir(),"details-v2");if(!dir.exists())dir.mkdirs();}
    private File file(Match m){return new File(dir,m.id.replaceAll("[^a-zA-Z0-9_-]","_")+".json");}
    MatchDetail cached(Match m){try{File f=file(m);if(!f.exists())return null;MatchDetail d=MatchDetail.fromJson(new JSONObject(new String(new AtomicFile(f).readFully(),StandardCharsets.UTF_8)));return d.match.id.equals(m.id)?d:null;}catch(Exception e){return null;}}
    MatchDetail fetch(Match m,boolean force)throws Exception {
        MatchDetail old=cached(m);long ttl=m.finalised()?6*3600_000L:m.group.equals("F1")||m.group.equals("LOL")||m.group.equals("CS2")?60_000:30_000;
        if(!force&&old!=null&&System.currentTimeMillis()-old.fetched<ttl)return old;
        MatchDetail next=DetailSources.fetch(m);AtomicFile atomic=new AtomicFile(file(m));FileOutputStream out=null;try{out=atomic.startWrite();out.write(next.json().toString().getBytes(StandardCharsets.UTF_8));atomic.finishWrite(out);}catch(Exception e){if(out!=null)atomic.failWrite(out);}
        File[] oldFiles=dir.listFiles();if(oldFiles!=null)for(File f:oldFiles)if(System.currentTimeMillis()-f.lastModified()>14L*86400_000)f.delete();return next;
    }
}
