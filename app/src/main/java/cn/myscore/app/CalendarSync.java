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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Single-match calendar subscriptions. Only events tagged by this installation can ever be changed. */
public final class CalendarSync {
    public interface Resolver {Match resolve(Match previous) throws Exception;}
    public static final int REMIND_MINUTES = 5;
    public static final class CalendarChoice {
        public final long id;
        public final String name, detail;
        public final boolean local, xiaomi;
        public CalendarChoice(long id, String name, String detail, boolean local, boolean xiaomi) {this.id=id;this.name=name;this.detail=detail;this.local=local;this.xiaomi=xiaomi;}
        @Override public String toString() {return name;}
    }
    public static final class Result {
        public int inserted,updated,cancelled,completed,failed;
        public String error="";
        public String message() {return "新增 "+inserted+" · 更新 "+updated+" · 取消 "+cancelled+" · 已结束移除 "+completed+(failed>0?" · 失败 "+failed:"");}
    }
    private static final Object LOCK=new Object();
    private final Context context;
    private final SharedPreferences preferences,links;
    private final Resolver resolver;
    public CalendarSync(Context c) {this(c,new MatchResolver());}
    public CalendarSync(Context c,Resolver resolver) {
        context=c.getApplicationContext();
        preferences=context.getSharedPreferences("settings",Context.MODE_PRIVATE);
        links=context.getSharedPreferences("calendar-links",Context.MODE_PRIVATE);
        this.resolver=resolver;
    }
    public static boolean permitted(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED && c.checkSelfPermission(Manifest.permission.WRITE_CALENDAR)==PackageManager.PERMISSION_GRANTED;
    }
    public long calendarId() {return preferences.getLong("calendar_id",-1);}
    public String calendarName() {return preferences.getString("calendar_name","");}
    public void chooseCalendar(long id,String name) {preferences.edit().putLong("calendar_id",id).putString("calendar_name",name).apply();}
    public CalendarChoice bestCalendar() {
        List<CalendarChoice> all=calendars();
        return all.isEmpty()?null:all.get(0);
    }
    public boolean hasCalendar(long id) {
        for(CalendarChoice choice:calendars())if(choice.id==id)return true;
        return false;
    }
    public boolean hasSubscriptions() {return !subscribedIds().isEmpty();}
    public boolean isSubscribed(String id) {return links.contains("match:"+id);}
    public Set<String> subscribedIds() {
        Set<String> ids=new HashSet<>();
        for(String key:links.getAll().keySet())if(key.startsWith("match:"))ids.add(key.substring(6));
        return ids;
    }
    public List<Match> subscriptions() {
        List<Match> list=new ArrayList<>();
        for(Map.Entry<String,?> entry:links.getAll().entrySet()) {
            if(!entry.getKey().startsWith("record:")||!(entry.getValue() instanceof String))continue;
            try {list.add(Match.fromJson(new org.json.JSONObject((String)entry.getValue())));}
            catch(Exception ignored) {}
        }
        list.sort(Comparator.comparingLong((Match m)->m.start==0?Long.MAX_VALUE:m.start));
        return list;
    }
    public List<CalendarChoice> calendars() {
        List<CalendarChoice> list=new ArrayList<>();
        if(!permitted(context))return list;
        try(Cursor cur=context.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,
            new String[]{"_id","calendar_displayName","account_name","account_type","isPrimary"},
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL+">=? AND "+CalendarContract.Calendars.SYNC_EVENTS+"=1 AND "+CalendarContract.Calendars.VISIBLE+"=1",
            new String[]{Integer.toString(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)},null)) {
            if(cur!=null)while(cur.moveToNext()) {
                long id=cur.getLong(0);
                String display=cur.getString(1),account=cur.getString(2),type=cur.getString(3);
                boolean local=CalendarContract.ACCOUNT_TYPE_LOCAL.equals(type)||"LOCAL".equalsIgnoreCase(type);
                String title=displayName(display,account,type,local);
                String detail=accountLabel(account,type,local);
                String name=title.equals(detail)?title:title+" · "+detail;
                String kind=(type+" "+account+" "+title).toLowerCase(Locale.ROOT);
                boolean xiaomi=kind.contains("miui")||kind.contains("xiaomi")||title.contains("小米");
                list.add(new CalendarChoice(id,name,detail,local,xiaomi));
            }
        } catch(RuntimeException ignored) {}
        list.sort(Comparator.comparing((CalendarChoice c)->!c.xiaomi).thenComparing(c->!c.local).thenComparing(c->c.name));
        return list;
    }
    private static String displayName(String display,String account,String type,boolean local) {
        String text=display==null?"":display;
        if(text.isBlank()||text.startsWith("calendar_displayname_")||text.equals("calendar")) {
            String kind=(type+" "+account).toLowerCase(Locale.ROOT);
            if(kind.contains("miui")||kind.contains("xiaomi"))return "小米日历";
            return local?"本机日历":"手机日历";
        }
        return text;
    }
    private static String accountLabel(String account,String type,boolean local) {
        if(account==null||account.isBlank()||account.matches("[0-9]+")) {
            String kind=type==null?"":type.toLowerCase(Locale.ROOT);
            if(kind.contains("miui")||kind.contains("xiaomi"))return "小米账户";
            return local?"本机":"账户";
        }
        if(CalendarContract.ACCOUNT_TYPE_LOCAL.equals(type)||"LOCAL".equalsIgnoreCase(type))return "本机";
        return account;
    }
    public Result subscribe(Match m,long now) {
        Result result=new Result();
        if(!permitted(context)) {result.failed=1;result.error="未开启日历权限";return result;}
        long calendar=calendarId();
        if(calendar<0) {result.failed=1;result.error="请先选择手机日历";return result;}
        if(m==null||m.id.isBlank()||m.cancelled()||!m.scheduled()||!m.timeConfirmed||m.start<=now) {
            result.failed=1;result.error="比赛时间未确定、已开始或已结束";return result;
        }
        synchronized(LOCK) {
            String key="match:"+m.id;
            try {
                long eventId=links.getLong(key,-1);
                boolean owns=eventId>0&&ownsEvent(eventId,m.id);
                if(eventId>0&&!owns) {removeLink(m.id);eventId=-1;}
                long existingCalendar=owns?eventCalendar(eventId):-1;
                long target=existingCalendar>=0?existingCalendar:calendar;
                int method=reminderMethod(target);
                if(owns) {updateEvent(eventId,m,target);result.updated++;}
                else {
                    eventId=insertEvent(m,calendar);
                    if(eventId<=0)throw new IllegalStateException("日历未返回新事件");
                    links.edit().putLong(key,eventId).commit();
                    result.inserted++;
                }
                ensureReminder(eventId,method);
                links.edit().putString("record:"+m.id,m.json().toString()).putString("fingerprint:"+m.id,fingerprint(m,target)).apply();
            } catch(Exception e) {
                result.failed++;result.error=readable(e);
                android.util.Log.w("MyScore","Calendar subscribe failed: "+e.getClass().getSimpleName());
            }
        }
        return result;
    }
    public Result unsubscribe(String id) {
        Result result=new Result();
        if(!permitted(context)) {result.failed=1;result.error="未开启日历权限";return result;}
        synchronized(LOCK) {
            long eventId=links.getLong("match:"+id,-1);
            try {
                if(eventId>0&&ownsEvent(eventId,id))context.getContentResolver().delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,eventId),null,null);
                removeLink(id);result.cancelled++;
            } catch(RuntimeException e) {result.failed++;result.error=readable(e);}
        }
        return result;
    }
    /** Updates only already-subscribed matches. It never creates events for unsubscribed matches. */
    public Result sync(List<Match> current,long now) {
        Result result=new Result();
        if(!permitted(context)) {result.failed=1;result.error="未开启日历权限";return result;}
        synchronized(LOCK) {
            Map<String,Match> byId=new HashMap<>();
            if(current!=null)for(Match m:current)byId.put(m.id,m);
            List<String> ids=new ArrayList<>();
            for(String key:new ArrayList<>(links.getAll().keySet()))if(key.startsWith("match:"))ids.add(key.substring(6));
            for(String id:ids) {
                long eventId=links.getLong("match:"+id,-1);
                if(eventId<=0) {removeLink(id);continue;}
                if(!ownsEvent(eventId,id)) {removeLink(id);continue;}
                try {
                    Match m=byId.get(id);
                    if(m==null) {
                        String record=links.getString("record:"+id,"");
                        if(record.isEmpty()) {result.failed++;continue;}
                        Match previous=Match.fromJson(new org.json.JSONObject(record));
                        m=previous.finalised()?previous:resolver.resolve(previous);
                        if(m==null||!id.equals(m.id)) {result.failed++;continue;}
                    }
                    if(m.finalised()) {
                        // Keep the historical calendar event, but stop tracking a completed match.
                        deleteReminders(eventId);
                        removeLink(id);result.completed++;continue;
                    }
                    long calendar=eventCalendar(eventId);
                    if(calendar<0)calendar=calendarId();
                    if(m.cancelled()) {
                        context.getContentResolver().delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,eventId),null,null);
                        removeLink(id);result.cancelled++;continue;
                    }
                    if(m.live()) {
                        deleteReminders(eventId);
                        links.edit().remove("fingerprint:"+id).apply();
                        if(m.timeConfirmed&&m.start>0)updateEvent(eventId,m,calendar);
                        saveRecord(id,m);result.updated++;continue;
                    }
                    if(!m.timeConfirmed||!m.scheduled()||m.start<=now) {
                        deleteReminders(eventId);
                        links.edit().remove("fingerprint:"+id).apply();
                        saveRecord(id,m);continue;
                    }
                    String fingerprint=fingerprint(m,calendar);
                    if(!fingerprint.equals(links.getString("fingerprint:"+id,""))) {
                        updateEvent(eventId,m,calendar);
                        ensureReminder(eventId,reminderMethod(calendar));
                        result.updated++;
                        links.edit().putString("record:"+id,m.json().toString()).putString("fingerprint:"+id,fingerprint).apply();
                    } else saveRecord(id,m);
                } catch(Exception e) {
                    result.failed++;
                    android.util.Log.w("MyScore","Calendar refresh failed: "+e.getClass().getSimpleName());
                }
            }
            preferences.edit().putLong("calendar_checked",now).putString("calendar_result",result.message()).apply();
        }
        return result;
    }
    public int linkedEvents() {return subscribedIds().size();}
    private long insertEvent(Match m,long calendar) {
        Uri inserted=context.getContentResolver().insert(CalendarContract.Events.CONTENT_URI,eventValues(m,calendar));
        return inserted==null?-1:ContentUris.parseId(inserted);
    }
    private void updateEvent(long eventId,Match m,long calendar) {
        context.getContentResolver().update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,eventId),eventValues(m,calendar),null,null);
    }
    private ContentValues eventValues(Match m,long calendar) {
        Competition c=Competition.find(m.league);
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
        return event;
    }
    private void ensureReminder(long eventId,int method) {
        context.getContentResolver().delete(CalendarContract.Reminders.CONTENT_URI,"event_id=?",new String[]{Long.toString(eventId)});
        if(tryReminder(eventId,method))return;
        if(method!=CalendarContract.Reminders.METHOD_DEFAULT&&tryReminder(eventId,CalendarContract.Reminders.METHOD_DEFAULT))return;
        throw new IllegalStateException("日历未接受提醒");
    }
    private boolean tryReminder(long eventId,int method) {
        ContentValues reminder=new ContentValues();
        reminder.put(CalendarContract.Reminders.EVENT_ID,eventId);
        reminder.put(CalendarContract.Reminders.MINUTES,REMIND_MINUTES);
        reminder.put(CalendarContract.Reminders.METHOD,method);
        return context.getContentResolver().insert(CalendarContract.Reminders.CONTENT_URI,reminder)!=null;
    }
    private void deleteReminders(long eventId) {
        context.getContentResolver().delete(CalendarContract.Reminders.CONTENT_URI,"event_id=?",new String[]{Long.toString(eventId)});
    }
    private int reminderMethod(long calendar) {
        try(Cursor cur=context.getContentResolver().query(ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI,calendar),
            new String[]{CalendarContract.Calendars.ALLOWED_REMINDERS},null,null,null)) {
            if(cur!=null&&cur.moveToFirst()) {
                String allowed=cur.getString(0);
                if(allowed!=null) {
                    if(allowed.contains("1"))return CalendarContract.Reminders.METHOD_ALERT;
                    if(allowed.contains("0"))return CalendarContract.Reminders.METHOD_DEFAULT;
                }
            }
        } catch(RuntimeException ignored) {}
        return CalendarContract.Reminders.METHOD_ALERT;
    }
    private String fingerprint(Match m,long calendar) {return m.start+"|"+m.displayTitle()+"|"+REMIND_MINUTES+"|"+calendar+"|"+m.state;}
    private void saveRecord(String id,Match m) {links.edit().putString("record:"+id,m.json().toString()).apply();}
    private void removeLink(String id) {links.edit().remove("match:"+id).remove("record:"+id).remove("fingerprint:"+id).apply();}
    private static String readable(Exception e) {String m=e.getMessage();return m==null||m.isBlank()?"日历写入失败":m;}
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
}
