package cn.myscore.app;

import android.app.Instrumentation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.CalendarContract;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Uses SmokeInstrumentation's disposable emulator calendar, never a user's account. */
final class CalendarRecoveryChecks {
    private static int checks;
    static int run(Instrumentation test,Context context,long calendar)throws Exception {
        checks=0;
        if(!android.os.Build.HARDWARE.equals("ranchu")&&!android.os.Build.HARDWARE.equals("goldfish"))throw new AssertionError("emulator only");
        SharedPreferences links=context.getSharedPreferences("calendar-links",0);
        CalendarSync sync=new CalendarSync(context,previous->{throw new AssertionError("local recovery must not fetch a source");});
        Match match=new Match();match.id="espn:epl:999999999:999999999";match.league="epl";match.group="足球";
        match.source="TEST ONLY";match.home="Recovery home";match.away="Recovery away";match.state="scheduled";
        match.start=System.currentTimeMillis()+86_400_000L;match.timeConfirmed=true;
        check(sync.subscribe(match,System.currentTimeMillis()).inserted==1,"recovery fixture subscribed");
        long original=links.getLong("match:"+match.id,-1);
        links.edit().remove("match:"+match.id).remove("record:"+match.id).remove("fingerprint:"+match.id).commit();
        context.getContentResolver().delete(CalendarContract.Reminders.CONTENT_URI,"event_id=?",new String[]{Long.toString(original)});
        ContentValues ten=new ContentValues();ten.put("event_id",original);ten.put("minutes",10);ten.put("method",1);
        context.getContentResolver().insert(CalendarContract.Reminders.CONTENT_URI,ten);
        CalendarSync.Result recovered=sync.recoverLocal();
        check(recovered.failed==0&&sync.isSubscribed(match.id),"local recovery works with no score list or network");
        check(links.getLong("match:"+match.id,-1)==original,"shared calendar event is reused without inserting a duplicate");
        check(five(context,original),"a ten-minute reminder does not hide the missing five-minute reminder");
        Match record=sync.subscriptions().stream().filter(m->m.id.equals(match.id)).findFirst().orElseThrow();
        check(record.start==match.start&&record.source.equals(match.source)&&record.state.equals("unknown"),"calendar facts survive recovery without inventing a source state");
        check(sync.recoverLocal().inserted==0&&sync.recoverLocal().updated==0,"unchanged calendar recovery is idempotent");
        ContentValues copy=new ContentValues();copy.put("calendar_id",calendar);copy.put("title","英超 · Recovery home vs Recovery away");
        copy.put("description","MyScore:"+match.id+"\n北京时间 · TEST ONLY\nhttps://example.invalid/fixture\n");
        copy.put("customAppPackage",context.getPackageName());copy.put("customAppUri","myscore://match/"+Uri.encode(match.id));
        copy.put("dtstart",match.start+3_600_000);copy.put("dtend",match.start+7_200_000);copy.put("eventTimezone","Asia/Shanghai");
        context.getContentResolver().delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,original),null,null);
        long replacement=ContentUris.parseId(context.getContentResolver().insert(CalendarContract.Events.CONTENT_URI,copy));
        check(sync.recoverLocal().failed==0&&links.getLong("match:"+match.id,-1)==replacement,"deleted/recreated events rebind to their new local id");
        check(five(context,replacement),"recreated event regains five-minute reminder");
        CountDownLatch resolving=new CountDownLatch(1),release=new CountDownLatch(1);
        CalendarSync slow=new CalendarSync(context,previous->{resolving.countDown();release.await();return null;});
        Thread network=new Thread(()->slow.sync(List.of(),System.currentTimeMillis()));network.start();
        try {
            check(resolving.await(5,TimeUnit.SECONDS),"slow subscription resolver entered");
            long started=SystemClock.uptimeMillis();sync.recoverLocal();
            check(SystemClock.uptimeMillis()-started<2000,"local recovery does not wait for a blocked source request");
        }finally{release.countDown();network.join(5000);}
        CalendarUpdates.start(context);
        context.getContentResolver().delete(CalendarContract.Reminders.CONTENT_URI,"event_id=?",new String[]{Long.toString(replacement)});
        long until=SystemClock.uptimeMillis()+5000;
        while(!five(context,replacement)&&SystemClock.uptimeMillis()<until){Thread.sleep(50);test.waitForIdleSync();}
        check(five(context,replacement),"provider observer repairs reminders arriving after app startup");
        match.state="live";sync.syncKnown(List.of(match),System.currentTimeMillis());
        CalendarUpdates.start(context);CalendarUpdates.awaitIdle();
        check(!five(context,replacement),"local recovery never re-arms a live match");
        match.state="final";sync.syncKnown(List.of(match),System.currentTimeMillis());
        sync.recoverLocal();match.state="scheduled";sync.syncKnown(List.of(match),System.currentTimeMillis());
        check(!sync.isSubscribed(match.id)&&!five(context,replacement),"retired history is not resurrected by stale cached schedules");
        context.getContentResolver().delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,replacement),null,null);
        String foreign="espn:epl:999999998:999999998";
        copy.put("description","MyScore:"+foreign+"\nTEST ONLY\n");copy.put("customAppUri","https://example.invalid/unowned");
        Uri unrelated=context.getContentResolver().insert(CalendarContract.Events.CONTENT_URI,copy);
        sync.recoverLocal();
        check(!sync.isSubscribed(foreign),"description alone cannot adopt an event with a mismatched ownership URI");
        try(Cursor rows=context.getContentResolver().query(unrelated,new String[]{"_id"},null,null,null)){
            check(rows!=null&&rows.moveToFirst(),"unowned calendar control survives recovery");
        }
        context.getContentResolver().delete(unrelated,null,null);
        // Opposite local row ordering must still choose the same cloud identity on both devices.
        Uri cloudUri=CalendarContract.Events.CONTENT_URI.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER,"true")
            .appendQueryParameter("account_name","myscore-functional-tests").appendQueryParameter("account_type",CalendarContract.ACCOUNT_TYPE_LOCAL).build();
        copy.put("description","MyScore:"+match.id+"\nTEST ONLY\n");copy.put("customAppUri","myscore://match/"+Uri.encode(match.id));
        copy.put("_sync_id","test-cloud-z");copy.put("dirty",0);
        long canonical=ContentUris.parseId(context.getContentResolver().insert(cloudUri,copy));
        copy.put("_sync_id","test-cloud-a");
        long duplicate=ContentUris.parseId(context.getContentResolver().insert(cloudUri,copy));
        links.edit().putLong("match:"+match.id,duplicate).commit();
        sync.recoverLocal();
        check(links.getLong("match:"+match.id,-1)==canonical,"cloud identity determines duplicate winner instead of the linked local row");
        try(Cursor row=context.getContentResolver().query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,duplicate),new String[]{"deleted"},null,null,null)) {
            check(row!=null&&(!row.moveToFirst()||row.getInt(0)!=0),"acknowledged duplicate is removed from the shared calendar");
        }
        sync.unsubscribe(match.id);
        java.util.List<Long> cloudRequests=new java.util.ArrayList<>();
        CalendarSync cloud=new CalendarSync(context,previous->{throw new AssertionError("known changes must not refetch");},cloudRequests::add);
        match.state="scheduled";
        check(cloud.subscribe(match,System.currentTimeMillis()).failed==0&&!cloudRequests.isEmpty(),"successful subscription requests cloud sync");
        check(cloudRequests.stream().allMatch(id->id==calendar),"cloud request targets the actual event calendar");
        cloudRequests.clear();
        match.start+=3_600_000;match.updated=System.currentTimeMillis()+100;
        check(cloud.syncKnown(List.of(match),System.currentTimeMillis()).failed==0&&!cloudRequests.isEmpty(),"known reschedule requests cloud sync after its calendar update");
        cloudRequests.clear();cloud.syncKnown(List.of(match),System.currentTimeMillis());
        check(cloudRequests.isEmpty(),"unchanged schedule does not generate another cloud request");
        long stable=links.getLong("match:"+match.id,-1);
        ContentValues clean=new ContentValues();clean.put("dirty",0);
        context.getContentResolver().update(ContentUris.withAppendedId(cloudUri,stable),clean,null,null);
        links.edit().remove("fingerprint:"+match.id).commit();
        cloud.syncKnown(List.of(match),System.currentTimeMillis());
        try(Cursor row=context.getContentResolver().query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,stable),new String[]{"dirty"},null,null,null)) {
            check(row!=null&&row.moveToFirst()&&row.getInt(0)==0,"recovering a missing fingerprint does not dirty an unchanged event or recreate its reminder");
        }
        check(cloud.unsubscribe(match.id).failed==0&&!cloudRequests.isEmpty(),"successful unsubscribe requests cloud sync");
        cloudRequests.clear();cloud.unsubscribe(match.id);
        check(cloudRequests.isEmpty(),"removing an absent local link does not sync an unrelated calendar");
        CalendarSync rejecting=new CalendarSync(context,previous->null,id->{throw new SecurityException("test rejection");});
        check(rejecting.subscribe(match,System.currentTimeMillis()).failed==0&&rejecting.isSubscribed(match.id),"cloud request rejection does not undo a successful local subscription");
        check(rejecting.unsubscribe(match.id).failed==0&&!rejecting.isSubscribed(match.id),"cloud request rejection does not block local unsubscribe");
        return checks;
    }
    private static boolean five(Context c,long event){try(Cursor rows=c.getContentResolver().query(CalendarContract.Reminders.CONTENT_URI,new String[]{"_id"},"event_id=? AND minutes=5",new String[]{Long.toString(event)},null)){return rows!=null&&rows.moveToFirst();}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;android.util.Log.i("MyScoreRecoveryTests","PASS "+message);}
}
