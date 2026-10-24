package cn.myscore.app;

import android.Manifest;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Only events tagged by this installation can ever be changed. */
public final class CalendarSync {
    public interface Resolver {Match resolve(Match previous) throws Exception;}
    public static final class CalendarChoice {
        public long id; public String name;
        public CalendarChoice(long id,String name) {this.id=id;this.name=name;}
        @Override public String toString(){return name;}
    }
    public static final class Result {
        public int inserted,updated,cancelled,failed;
        public String message() {return "新增 "+inserted+" · 更新 "+updated+" · 取消 "+cancelled+(failed>0?" · 失败 "+failed:"");}
    }
    private static final Object LOCK=new Object();
    private final Context context;
    private final SharedPreferences preferences,links;
    private final Resolver resolver;
    public CalendarSync(Context c) {
        this(c,new MatchResolver());
    }
    public CalendarSync(Context c,Resolver resolver) {
        context=c.getApplicationContext();preferences=context.getSharedPreferences("settings",Context.MODE_PRIVATE);
        links=context.getSharedPreferences("calendar-links",Context.MODE_PRIVATE);
        this.resolver=resolver;
    }
    public static boolean permitted(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED && c.checkSelfPermission(Manifest.permission.WRITE_CALENDAR)==PackageManager.PERMISSION_GRANTED;
    }
    public List<CalendarChoice> calendars() {
        List<CalendarChoice> list=new ArrayList<>();if(!permitted(context))return list;
        try(Cursor cur=context.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,
            new String[]{"_id","calendar_displayName","account_name"},
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL+">=? AND "+CalendarContract.Calendars.SYNC_EVENTS+"=1",
            new String[]{Integer.toString(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)},null)) {
            if(cur!=null)while(cur.moveToNext())list.add(new CalendarChoice(cur.getLong(0),cur.getString(1)+" · "+cur.getString(2)));
        }
        return list;
    }
    public Result sync(List<Match> matches,long now) {
        Result result=new Result();
        if(!preferences.getBoolean("calendar_enabled",false)||!permitted(context))return result;
        long calendar=preferences.getLong("calendar_id",-1);if(calendar<0)return result;
        synchronized(LOCK) {
            List<Match> reconciled=new ArrayList<>(matches);
            java.util.Set<String> seen=new java.util.HashSet<>();for(Match m:matches)seen.add(m.id);
            for(Map.Entry<String,?> saved:links.getAll().entrySet()) {
                if(!saved.getKey().startsWith("match:") || !(saved.getValue() instanceof Long))continue;
                String identity=saved.getKey().substring("match:".length());long existing=(Long)saved.getValue();
                if(seen.contains(identity))continue;
                try {
                    if(!ownsEvent(existing,identity)||eventStart(existing)<=now)continue;
                    String record=links.getString("record:"+identity,"");
                    if(record.isEmpty()){result.failed++;continue;}
                    Match prior=Match.fromJson(new org.json.JSONObject(record));
                    Match refreshed=preferences.getBoolean("sync_"+prior.league,true)?resolver.resolve(prior):prior;
                    if(refreshed==null||!identity.equals(refreshed.id))throw new IllegalStateException("比赛复核身份不匹配");
                    reconciled.add(refreshed);seen.add(identity);
                }catch(Exception e){result.failed++;}
            }
            for(Match m:reconciled) {
                String key="match:"+m.id;long eventId=links.getLong(key,-1);
                boolean selected=preferences.getBoolean("sync_"+m.league,true);
                try {
                    boolean owns=eventId>0 && ownsEvent(eventId,m.id);
                    // Removing a subscription stops future reminders created by MyScore.
                    if(owns && (m.cancelled()||!selected)) {
                        if(m.start>now || m.cancelled()) {
                            context.getContentResolver().delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,eventId),null,null);
                            links.edit().remove(key).remove("record:"+m.id).remove("fingerprint:"+m.id).apply();result.cancelled++;
                        }
                        continue;
                    }
                    if(!selected || m.cancelled())continue;
                    if(m.finalised() || m.live()) {
                        if(owns) {
                            context.getContentResolver().delete(CalendarContract.Reminders.CONTENT_URI,"event_id=?",new String[]{Long.toString(eventId)});
                            if(m.timeConfirmed&&m.start>0){ContentValues actual=new ContentValues();actual.put(CalendarContract.Events.DTSTART,m.start);actual.put(CalendarContract.Events.DTEND,m.start+Rules.estimatedDuration(m.group));context.getContentResolver().update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,eventId),actual,null,null);}
                            links.edit().remove("fingerprint:"+m.id).putString("record:"+m.id,m.json().toString()).apply();
                        }
                        continue;
                    }
                    // Do not create timed entries for postponed/TBD events. Existing reminders are removed.
                    if(!m.scheduled() || !m.timeConfirmed) {
                        if(owns){context.getContentResolver().delete(CalendarContract.Reminders.CONTENT_URI,"event_id=?",new String[]{Long.toString(eventId)});links.edit().remove("fingerprint:"+m.id).apply();}
                        continue;
                    }
                    if(!owns && !Rules.shouldImport(m,now))continue;
                    if(m.start<=now)continue;
                    Competition c=Competition.find(m.league);
                    String fingerprint=m.start+"|"+m.displayTitle()+"|"+preferences.getInt("remind_minutes",15)+"|"+calendar+"|"+m.state;
                    if(owns && fingerprint.equals(links.getString("fingerprint:"+m.id,"")))continue;
                    // Never move a personal event to another calendar. Recreate only a verified owned event.
                    if(owns && eventCalendar(eventId)!=calendar) {
                        context.getContentResolver().delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,eventId),null,null);owns=false;
                    }
                    ContentValues event=new ContentValues();
                    event.put(CalendarContract.Events.CALENDAR_ID,calendar);
                    event.put(CalendarContract.Events.TITLE,(c==null?m.group:c.name)+" · "+m.displayTitle());
                    event.put(CalendarContract.Events.DTSTART,m.start);
                    event.put(CalendarContract.Events.DTEND,m.start+Rules.estimatedDuration(m.group));
                    event.put(CalendarContract.Events.EVENT_TIMEZONE,"Asia/Shanghai");
                    event.put(CalendarContract.Events.DESCRIPTION,"MyScore:"+m.id+"\n北京时间 · "+m.source+"\n"+m.url+"\n日程结束时间为估算。比赛可能改期，请以应用最新赛程为准。");
                    event.put(CalendarContract.Events.CUSTOM_APP_PACKAGE,context.getPackageName());
                    event.put(CalendarContract.Events.CUSTOM_APP_URI,"myscore://match/"+Uri.encode(m.id));
                    event.put(CalendarContract.Events.HAS_ALARM,1);
                    event.put(CalendarContract.Events.AVAILABILITY,CalendarContract.Events.AVAILABILITY_FREE);
                    event.put(CalendarContract.Events.STATUS,CalendarContract.Events.STATUS_CONFIRMED);
                    if(owns) {
                        context.getContentResolver().update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,eventId),event,null,null);result.updated++;
                    } else {
                        Uri inserted=context.getContentResolver().insert(CalendarContract.Events.CONTENT_URI,event);
                        if(inserted==null)throw new IllegalStateException("日历未返回新事件");
                        eventId=ContentUris.parseId(inserted);
                        // Persist ownership immediately so failed reminder creation can be retried safely.
                        links.edit().putLong(key,eventId).commit();result.inserted++;
                    }
                    context.getContentResolver().delete(CalendarContract.Reminders.CONTENT_URI,"event_id=?",new String[]{Long.toString(eventId)});
                    ContentValues reminder=new ContentValues();reminder.put(CalendarContract.Reminders.EVENT_ID,eventId);
                    reminder.put(CalendarContract.Reminders.MINUTES,preferences.getInt("remind_minutes",15));
                    reminder.put(CalendarContract.Reminders.METHOD,CalendarContract.Reminders.METHOD_ALERT);
                    if(context.getContentResolver().insert(CalendarContract.Reminders.CONTENT_URI,reminder)==null)throw new IllegalStateException("日历未接受提醒");
                    links.edit().putString("fingerprint:"+m.id,fingerprint).putString("record:"+m.id,m.json().toString()).apply();
                } catch(Exception e) {result.failed++;android.util.Log.w("MyScore","Calendar sync failed: "+e.getClass().getSimpleName());}
            }
            preferences.edit().putLong("calendar_checked",now).putString("calendar_result",result.message()).apply();
        }
        return result;
    }
    private boolean ownsEvent(long id,String matchId) {
        try(Cursor cur=context.getContentResolver().query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,id),
            new String[]{CalendarContract.Events.DESCRIPTION,CalendarContract.Events.CUSTOM_APP_PACKAGE,CalendarContract.Events.DELETED},null,null,null)) {
            return cur!=null && cur.moveToFirst() && cur.getInt(2)==0 && context.getPackageName().equals(cur.getString(1)) && cur.getString(0)!=null && cur.getString(0).startsWith("MyScore:"+matchId+"\n");
        }
    }
    private long eventCalendar(long id) {
        try(Cursor cur=context.getContentResolver().query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,id),new String[]{CalendarContract.Events.CALENDAR_ID},null,null,null)) {
            return cur!=null && cur.moveToFirst()?cur.getLong(0):-1;
        }
    }
    private long eventStart(long id) {
        try(Cursor cur=context.getContentResolver().query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,id),new String[]{CalendarContract.Events.DTSTART},null,null,null)){return cur!=null&&cur.moveToFirst()?cur.getLong(0):0;}
    }
    public int linkedEvents() {
        int count=0;for(Map.Entry<String,?> e:links.getAll().entrySet())if(e.getKey().startsWith("match:"))count++;return count;
    }
}
