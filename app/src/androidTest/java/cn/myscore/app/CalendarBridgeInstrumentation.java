package cn.myscore.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.CalendarContract;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** Explicit two-device test of a real cached future fixture; restores its initial local state. */
public final class CalendarBridgeInstrumentation extends Instrumentation {
    private String requested="";private Match match;private CalendarSync sync;private boolean original;
    private Context context;private int command;private String action="ready",error="";
    @Override public void onCreate(Bundle args){super.onCreate(args);if(args!=null)requested=args.getString("match","");start();}
    @Override public void onStart(){
        Bundle report=new Bundle();int result=Activity.RESULT_OK;
        try {
            context=getTargetContext();sync=new CalendarSync(context);sync.recoverLocal();
            long now=System.currentTimeMillis();
            for(Match candidate:new Repository(context).cached(Match.today(now),Match.today(now),"NFL").matches())
                if(candidate.scheduled()&&candidate.timeConfirmed&&candidate.start>now&&(requested.isEmpty()?!sync.isSubscribed(candidate.id):requested.equals(candidate.id))){match=candidate;break;}
            if(match==null)throw new AssertionError("No real cached future NFL fixture for bridge test");
            original=sync.isSubscribed(match.id);
            new File(context.getFilesDir(),"cloud-bridge-command.json").delete();
            ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
            try(android.os.ParcelFileDescriptor descriptor=getUiAutomation().executeShellCommand("am start -W -n cn.myscore.app/.MainActivity -f 0x10008000");java.io.FileInputStream in=new java.io.FileInputStream(descriptor.getFileDescriptor())){in.readAllBytes();}
            Activity activity=waitForMonitorWithTimeout(monitor,10000);removeMonitor(monitor);
            if(activity==null)throw new AssertionError("Cannot launch foreground bridge activity");
            runOnMainSync(()->{try{java.lang.reflect.Method select=MainActivity.class.getDeclaredMethod("select",int.class,String.class,int.class);select.setAccessible(true);select.invoke(activity,0,"NFL",0);}catch(Exception e){throw new AssertionError(e);}});
            long until=SystemClock.uptimeMillis()+8*60_000;
            while(SystemClock.uptimeMillis()<until){
                File input=new File(context.getFilesDir(),"cloud-bridge-command.json");
                if(input.isFile())try {
                    JSONObject next=new JSONObject(new String(java.nio.file.Files.readAllBytes(input.toPath()),StandardCharsets.UTF_8));
                    if(next.getInt("sequence")>command){command=next.getInt("sequence");action=next.getString("action");error="";
                        if(action.equals("stop"))break;
                        if(action.equals("sync")) {
                            runOnMainSync(()->CalendarCloudSync.opened(context));
                        }
                        CalendarSync.Result out=action.equals("subscribe")?sync.subscribe(match,System.currentTimeMillis()):action.equals("unsubscribe")?sync.unsubscribe(match.id):new CalendarSync.Result();
                        if(out.failed>0)error=out.error;
                    }
                }catch(Exception ignored){}
                snapshot();Thread.sleep(250);
            }
            report.putString("stream","Bridge finished for "+match.id+"\n");
        }catch(Throwable e){result=Activity.RESULT_CANCELED;report.putString("stream",android.util.Log.getStackTraceString(e));}
        finally {
            if(match!=null&&sync!=null){if(original)sync.subscribe(match,System.currentTimeMillis());else sync.unsubscribe(match.id);try{action="restored";snapshot();Thread.sleep(2500);}catch(Exception ignored){}}
        }
        finish(result,report);
    }
    private void snapshot()throws Exception {
        JSONArray events=new JSONArray();
        try(Cursor rows=context.getContentResolver().query(CalendarContract.Events.CONTENT_URI,
            new String[]{"_id","description","_sync_id","dirty","deleted"},"description LIKE ?",new String[]{"MyScore:"+match.id+"\n%"},"_id DESC")) {
            if(rows!=null)while(rows.moveToNext())if(rows.getString(1).startsWith("MyScore:"+match.id+"\n"))events.put(new JSONObject().put("id",rows.getLong(0)).put("sync",rows.isNull(2)?"":rows.getString(2)).put("dirty",rows.getInt(3)).put("deleted",rows.getInt(4)));
        }
        JSONObject state=new JSONObject().put("match",match.id).put("original",original).put("sequence",command).put("action",action).put("error",error).put("linked",sync.isSubscribed(match.id)).put("events",events);
        AtomicFile file=new AtomicFile(new File(context.getFilesDir(),"cloud-bridge-state.json"));FileOutputStream out=file.startWrite();out.write(state.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);
    }
}
