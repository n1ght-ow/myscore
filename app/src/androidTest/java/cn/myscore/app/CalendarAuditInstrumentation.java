package cn.myscore.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentUris;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.CalendarContract;
import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only diagnosis of the reported source identity; never queries unrelated personal events. */
public final class CalendarAuditInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){Bundle report=new Bundle();int outcome=Activity.RESULT_OK;try{
        String id="five:cs2:csgo_mc_2399751";CalendarSync sync=new CalendarSync(getTargetContext());JSONObject result=new JSONObject();
        result.put("linked",sync.isSubscribed(id));
        String record=getTargetContext().getSharedPreferences("calendar-links",0).getString("record:"+id,"");
        if(!record.isBlank())result.put("record",new JSONObject(record));
        JSONArray events=new JSONArray();
        try(Cursor rows=getTargetContext().getContentResolver().query(CalendarContract.Events.CONTENT_URI,new String[]{"_id","title","dtstart","dtend","description","customAppPackage","deleted"},"customAppPackage=? AND description LIKE ?",new String[]{getTargetContext().getPackageName(),"MyScore:"+id+"%"},null)){
            if(rows!=null)while(rows.moveToNext()){
                long event=rows.getLong(0);JSONObject value=new JSONObject().put("eventId",event).put("title",rows.getString(1)).put("start",Formats.instant(rows.getLong(2),"yyyy-MM-dd HH:mm","unknown")).put("end",Formats.instant(rows.getLong(3),"yyyy-MM-dd HH:mm","unknown")).put("descriptionPrefix",rows.getString(4).substring(0,Math.min(60,rows.getString(4).length()))).put("package",rows.getString(5)).put("deleted",rows.getInt(6));
                java.lang.reflect.Method owns=CalendarSync.class.getDeclaredMethod("ownsEvent",long.class,String.class);owns.setAccessible(true);value.put("owned",owns.invoke(sync,event,id));
                JSONArray reminders=new JSONArray();try(Cursor r=getTargetContext().getContentResolver().query(CalendarContract.Reminders.CONTENT_URI,new String[]{"minutes","method"},"event_id=?",new String[]{Long.toString(event)},null)){if(r!=null)while(r.moveToNext())reminders.put(new JSONObject().put("minutes",r.getInt(0)).put("method",r.getInt(1)));}value.put("reminders",reminders);events.put(value);
            }
        }
        result.put("events",events);Match request=new Match();request.id=id;request.league="cs2";request.group="CS2";
        MatchDetail cached=new DetailRepository(getTargetContext()).cached(request);if(cached!=null)result.put("cached",summary(cached.match));
        try{result.put("liveSource",summary(new MatchResolver().resolve(cached==null?request:cached.match)));}catch(Exception e){result.put("sourceError",e.toString());}
        report.putString("stream",result.toString(2));
    }catch(Throwable e){outcome=Activity.RESULT_CANCELED;report.putString("stream",android.util.Log.getStackTraceString(e));}finish(outcome,report);}
    private JSONObject summary(Match m)throws Exception{return new JSONObject().put("id",m.id).put("start",Formats.instant(m.start,"yyyy-MM-dd HH:mm","unknown")).put("state",m.state).put("updated",Formats.instant(m.updated,"yyyy-MM-dd HH:mm:ss","unknown"));}
}
