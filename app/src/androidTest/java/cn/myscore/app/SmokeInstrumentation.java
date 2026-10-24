package cn.myscore.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.time.LocalDate;
import java.util.List;

/** Emulator-only functional checks. Synthetic events stay inside a disposable test calendar. */
public final class SmokeInstrumentation extends Instrumentation {
    private int checks;
    private Activity activity;
    private Context context;
    private long calendar=-1;
    private java.util.Map<String,?> originalSettings,originalLinks;
    private boolean network,details;
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);network=arguments!=null&&"true".equals(arguments.getString("network"));details=arguments!=null&&"true".equals(arguments.getString("details"));start();}
    private void check(boolean condition,String description){if(!condition)throw new AssertionError(description);checks++;android.util.Log.i("MyScoreTests","PASS "+description);}
    @Override public void onStart() {
        Bundle report=new Bundle();int outcome=Activity.RESULT_OK;
        try {
            context=getTargetContext();
            check(android.os.Build.HARDWARE.equals("ranchu")||android.os.Build.HARDWARE.equals("goldfish"),"test runner is restricted to an emulator");
            originalSettings=new java.util.HashMap<>(context.getSharedPreferences("settings",0).getAll());
            originalLinks=new java.util.HashMap<>(context.getSharedPreferences("calendar-links",0).getAll());
            activity=startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            check(findText(activity.getWindow().getDecorView(),"MyScore")!=null,"native match screen opens");
            click("设置");check(findText(activity.getWindow().getDecorView(),"单场订阅")!=null,"settings navigation");
            click("比赛");click("CS2");click("LOL");click("全部");
            check(findText(activity.getWindow().getDecorView(),"MyScore")!=null,"sport filters stay interactive during network loads");
            calendarChecks();
            checks+=UiChecks.run(this,activity);
            if(details){DetailChecks.run(this,context);checks+=DetailChecks.checks;}
            if(network){LocalDate day=Match.today(System.currentTimeMillis());Repository.Snapshot snapshot=new Repository(context).fetch(day,day,"全部",true);StringBuilder sources=new StringBuilder();for(Repository.Batch batch:snapshot.batches){sources.append(batch.competition.id).append(": ").append(batch.error.isEmpty()?"OK "+batch.matches.size():batch.error).append('\n');check(batch.error.isEmpty(),"device source: "+batch.competition.id+" "+batch.error);}report.putString("sources",sources.toString());}
            report.putInt("checks",checks);report.putString("stream","\nPASS: "+checks+" Android runtime checks.\n");
        } catch(Throwable e) {
            outcome=Activity.RESULT_CANCELED;report.putString("stream","\nFAIL after "+checks+" checks: "+e+"\n"+android.util.Log.getStackTraceString(e));
        } finally {
            if(context!=null&&originalSettings!=null){restore(context.getSharedPreferences("settings",0),originalSettings);restore(context.getSharedPreferences("calendar-links",0),originalLinks);
                if(calendar>0)context.getContentResolver().delete(syncUri(ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI,calendar)),null,null);
            }
        }
        finish(outcome,report);
    }
    private void restore(SharedPreferences prefs,java.util.Map<String,?> values){SharedPreferences.Editor e=prefs.edit().clear();for(java.util.Map.Entry<String,?> entry:values.entrySet()){Object v=entry.getValue();String k=entry.getKey();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);}e.commit();}
    private void calendarChecks() throws Exception {
        check(CalendarSync.permitted(context),"calendar permissions granted on test device");
        context.getSharedPreferences("calendar-links",0).edit().clear().commit();
        ContentValues cv=new ContentValues();cv.put(CalendarContract.Calendars.ACCOUNT_NAME,"myscore-functional-tests");cv.put(CalendarContract.Calendars.ACCOUNT_TYPE,CalendarContract.ACCOUNT_TYPE_LOCAL);
        cv.put(CalendarContract.Calendars.NAME,"MyScore tests");cv.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,"MyScore tests");cv.put(CalendarContract.Calendars.OWNER_ACCOUNT,"myscore-functional-tests");
        cv.put(CalendarContract.Calendars.CALENDAR_COLOR,0xff176b48);cv.put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,CalendarContract.Calendars.CAL_ACCESS_OWNER);
        cv.put(CalendarContract.Calendars.VISIBLE,1);cv.put(CalendarContract.Calendars.SYNC_EVENTS,1);cv.put(CalendarContract.Calendars.ALLOWED_REMINDERS,"1");cv.put(CalendarContract.Calendars.MAX_REMINDERS,5);
        Uri created=context.getContentResolver().insert(syncUri(CalendarContract.Calendars.CONTENT_URI),cv);
        check(created!=null,"disposable local calendar created");calendar=ContentUris.parseId(created);
        SharedPreferences prefs=context.getSharedPreferences("settings",0);prefs.edit().putLong("calendar_id",calendar).remove("calendar_enabled").remove("sync_epl").remove("remind_minutes").commit();
        CalendarSync sync=new CalendarSync(context);check(!sync.calendars().isEmpty(),"writable calendars enumerated");check(sync.bestCalendar()!=null,"a default writable calendar is selected without a chooser");
        long now=System.currentTimeMillis();Match m=new Match();m.id="espn:epl:instrumentation:1";m.league="epl";m.group="足球";m.source="TEST ONLY";
        m.home="Test home";m.away="Test away";m.state="scheduled";m.timeConfirmed=true;m.start=Match.today(now).plusDays(1).atTime(19,0).atZone(Match.ZONE).toInstant().toEpochMilli();
        check(sync.subscribe(m,now).inserted==1,"single match subscribes to the calendar");
        check(sync.isSubscribed(m.id),"subscription state is visible");
        long id=context.getSharedPreferences("calendar-links",0).getLong("match:"+m.id,-1);
        check(eventCount()==1,"one calendar event for one subscription");check(reminderMinutes(id)==5,"subscription reminder is five minutes");
        sync.subscribe(m,now);check(eventCount()==1,"repeated subscribe is idempotent");
        long movedStart=m.start+10L*24*60*60_000;
        CalendarSync outsideWindow=new CalendarSync(context,previous->{Match moved=Match.fromJson(previous.json());moved.start=movedStart;return moved;});
        check(outsideWindow.sync(List.of(),now).updated==1,"subscribed identity reconciles beyond seven days");
        try(Cursor event=context.getContentResolver().query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,id),new String[]{CalendarContract.Events.DTSTART},null,null,null)){check(event!=null&&event.moveToFirst()&&event.getLong(0)==movedStart,"rescheduled subscription moves to the distant date");}
        check(eventCount()==1&&reminderMinutes(id)==5,"distant reschedule keeps one subscription and its reminder");
        CalendarSync unavailable=new CalendarSync(context,previous->{throw new java.io.IOException("source temporarily unavailable");});
        check(unavailable.sync(List.of(),now).failed==1,"unavailable identity is reported without inventing cancellation");
        check(eventCount()==1&&reminderMinutes(id)==5,"missing source preserves existing subscription and reminder");
        m.start=movedStart+2*60*60_000L;check(sync.sync(List.of(m),now).updated==1,"rescheduled match updates the subscribed event");check(eventCount()==1,"reschedule keeps one event");
        try(Cursor event=context.getContentResolver().query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,id),new String[]{CalendarContract.Events.DTSTART},null,null,null)){check(event!=null&&event.moveToFirst()&&event.getLong(0)==m.start,"rescheduled start persisted");}
        m.state="delayed";sync.sync(List.of(m),now);check(reminderMinutes(id)==-1,"postponed match stops its old reminder");
        m.state="scheduled";sync.sync(List.of(m),now);check(reminderMinutes(id)==5,"resuming a postponed match restores the five-minute reminder");
        Match other=new Match();other.id="espn:epl:instrumentation:2";other.league="epl";other.group="足球";other.source="TEST ONLY";other.home="Other";other.away="Match";other.state="scheduled";other.timeConfirmed=true;other.start=m.start+3600_000;
        sync.sync(List.of(m,other),now);check(eventCount()==1,"unsubscribed matches are never imported automatically");
        m.state="cancelled";sync.sync(List.of(m),now);check(eventCount()==0,"cancelled subscription removes its owned event");
        ContentValues personal=new ContentValues();personal.put(CalendarContract.Events.CALENDAR_ID,calendar);personal.put(CalendarContract.Events.TITLE,"Personal event must survive");personal.put(CalendarContract.Events.DTSTART,m.start);personal.put(CalendarContract.Events.DTEND,m.start+3600_000);personal.put(CalendarContract.Events.EVENT_TIMEZONE,"Asia/Shanghai");
        Uri personalUri=context.getContentResolver().insert(CalendarContract.Events.CONTENT_URI,personal);check(personalUri!=null,"personal control event created");
        Match foreign=new Match();foreign.id="espn:epl:instrumentation:3";foreign.league="epl";foreign.group="足球";foreign.source="TEST ONLY";foreign.home="Foreign";foreign.away="Link";foreign.state="scheduled";foreign.timeConfirmed=true;foreign.start=m.start+7200_000;
        context.getSharedPreferences("calendar-links",0).edit().putLong("match:"+foreign.id,ContentUris.parseId(personalUri)).commit();
        check(sync.unsubscribe(foreign.id).cancelled==1,"removing a subscription is idempotent");
        try(Cursor personalEvent=context.getContentResolver().query(personalUri,new String[]{CalendarContract.Events.TITLE},null,null,null)){check(personalEvent!=null&&personalEvent.moveToFirst()&&personalEvent.getString(0).equals("Personal event must survive"),"foreign event is never deleted by a stale subscription link");}
        check(eventCount()==1,"foreign event remains as the only calendar row");
        check(sync.subscriptions().isEmpty(),"no subscription record remains after cleanup");
    }
    private Uri syncUri(Uri uri){return uri.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER,"true").appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME,"myscore-functional-tests").appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE,CalendarContract.ACCOUNT_TYPE_LOCAL).build();}
    private int eventCount(){try(Cursor c=context.getContentResolver().query(CalendarContract.Events.CONTENT_URI,new String[]{"_id"},"calendar_id=? AND deleted=0",new String[]{Long.toString(calendar)},null)){return c==null?0:c.getCount();}}
    private int reminderMinutes(long id){try(Cursor c=context.getContentResolver().query(CalendarContract.Reminders.CONTENT_URI,new String[]{CalendarContract.Reminders.MINUTES},"event_id=?",new String[]{Long.toString(id)},null)){return c!=null&&c.moveToFirst()?c.getInt(0):-1;}}
    private View findText(View root,String text){if(root instanceof TextView && ((TextView)root).getText().toString().equals(text))return root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){View found=findText(g.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private void click(String label){runOnMainSync(()->{View target=findText(activity.getWindow().getDecorView(),label);check(target!=null,"control exists: "+label);while(target!=null&&!target.isClickable())target=target.getParent() instanceof View?(View)target.getParent():null;check(target!=null&&target.performClick(),"control works: "+label);});waitForIdleSync();}
}
