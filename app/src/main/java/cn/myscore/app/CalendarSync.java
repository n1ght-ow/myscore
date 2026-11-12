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

/** Single-match calendar subscriptions. Only events carrying the app's ownership markers change. */
public final class CalendarSync {
    public interface Resolver {
        default void beginSync() {}
        Match resolve(Match previous) throws Exception;
    }
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
    private static final Object SYNC_LOCK=new Object();
    private record Subscription(String id,long eventId,String record){}
    private final Context context;
    private final SharedPreferences preferences,links;
    private final Resolver resolver;
    private final java.util.function.LongConsumer cloudChanged;
    public CalendarSync(Context c) {this(c,new MatchResolver());}
    public CalendarSync(Context c,Resolver resolver) {
        this(c,resolver,null);
    }
    CalendarSync(Context c,Resolver resolver,java.util.function.LongConsumer cloudChanged) {
        context=c.getApplicationContext();
        preferences=context.getSharedPreferences("settings",Context.MODE_PRIVATE);
        links=context.getSharedPreferences("calendar-links",Context.MODE_PRIVATE);
        this.resolver=resolver;
        this.cloudChanged=cloudChanged==null?id->CalendarCloudSync.changed(context,id):cloudChanged;
    }
    private void requestCloud(long calendar) {
        try{cloudChanged.accept(calendar);}catch(RuntimeException e){android.util.Log.w("MyScore","Cloud request could not be queued: "+e.getClass().getSimpleName());}
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
        List<CalendarChoice> choices=calendars();
        for(CalendarChoice choice:choices)if(choice.id==id)return true;
        // HyperOS can temporarily return no calendar rows during cloud sync. Keep an
        // explicitly remembered choice; the provider still validates the actual write.
        return choices.isEmpty()&&id>=0&&id==calendarId()&&!calendarName().isBlank();
    }
    public boolean hasSubscriptions() {return !subscribedIds().isEmpty();}
    public boolean isSubscribed(String id) {return links.contains("match:"+id);}
    /** Adopt MyScore events already present in the shared calendar (possibly created on another device). */
    public int adopt(List<Match> matches) {
        if(matches==null||matches.isEmpty()||!permitted(context))return 0;
        Map<String,Match> known=new HashMap<>();
        for(Match m:matches)if(m!=null&&!m.id.isBlank())known.put(m.id,m);
        if(known.isEmpty())return 0;
        Result result=new Result();
        synchronized(LOCK) {
            int recovered=recoverOwnedEvents(known,result);
            if(result.failed>0)android.util.Log.w("MyScore","Calendar adoption incomplete: "+result.error);
            return recovered;
        }
    }
    /** Local-only recovery, independent of score requests and their network deadlines. */
    Result recoverLocal() {
        Result result=new Result();
        if(!permitted(context))return result;
        synchronized(LOCK){result.inserted=recoverOwnedEvents(null,result);}
        return result;
    }
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
                links.edit().remove("retired:"+m.id).apply();
                if(!links.contains(key)) {
                    recoverOwnedEvents(Map.of(m.id,m),result);
                    if(result.failed>0)return result;
                }
                long eventId=links.getLong(key,-1);
                boolean owns=eventId>0&&ownsEvent(eventId,m.id);
                if(eventId>0&&!owns) {removeLink(m.id);eventId=-1;}
                long existingCalendar=owns?eventCalendar(eventId):-1;
                long target=existingCalendar>=0?existingCalendar:calendar;
                if(owns) {updateEvent(eventId,m,target);result.updated++;}
                else {
                    eventId=insertEvent(m,calendar);
                    if(eventId<=0)throw new IllegalStateException("日历未返回新事件");
                    if(!links.edit().putLong(key,eventId).commit()) {
                        // Do not leave an event that this installation cannot manage later.
                        context.getContentResolver().delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,eventId),null,null);
                        throw new IllegalStateException("无法保存日历关联");
                    }
                    result.inserted++;
                }
                ensureAlarm(eventId,target);
                links.edit().putString("record:"+m.id,m.json().toString()).putString("fingerprint:"+m.id,fingerprint(m,target)).apply();
                requestCloud(target);
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
                Map<Long,Long> events=new HashMap<>();
                // A rapid remote cancel/resubscribe may leave more than one local event for
                // the same match. Cancel every strictly owned copy, not only the saved id.
                try(Cursor rows=context.getContentResolver().query(CalendarContract.Events.CONTENT_URI,
                    new String[]{CalendarContract.Events._ID,CalendarContract.Events.CALENDAR_ID,CalendarContract.Events.CUSTOM_APP_URI,CalendarContract.Events.DESCRIPTION},
                    CalendarContract.Events.CUSTOM_APP_PACKAGE+"=? AND "+CalendarContract.Events.DESCRIPTION+" LIKE ? AND "+CalendarContract.Events.DELETED+"=0",
                    new String[]{context.getPackageName(),"MyScore:"+id+"\n%"},null)) {
                    if(rows==null)throw new IllegalStateException("无法读取日历事件");
                    while(rows.moveToNext())if(("myscore://match/"+Uri.encode(id)).equals(rows.getString(2))
                        &&rows.getString(3)!=null&&rows.getString(3).startsWith("MyScore:"+id+"\n"))events.put(rows.getLong(0),rows.getLong(1));
                }
                if(eventId>0&&ownsEvent(eventId,id))events.put(eventId,eventCalendar(eventId));
                Set<Long> calendars=new HashSet<>();
                for(Map.Entry<Long,Long> event:events.entrySet())if(context.getContentResolver().delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,event.getKey()),null,null)>0)calendars.add(event.getValue());
                for(long calendar:calendars)requestCloud(calendar);
                removeLink(id);result.cancelled++;
            } catch(RuntimeException e) {result.failed++;result.error=readable(e);}
        }
        return result;
    }
    /** Updates only already-subscribed matches. It never creates events for unsubscribed matches. */
    public Result sync(List<Match> current,long now) {
        return sync(current,now,true);
    }
    /** Apply source data already obtained by the app, without resolving other subscriptions. */
    Result syncKnown(List<Match> current,long now) {return sync(current,now,false);}
    private Result sync(List<Match> current,long now,boolean resolveMissing) {
        Result result=new Result();
        if(!permitted(context)) {result.failed=1;result.error="未开启日历权限";return result;}
        if(Thread.currentThread().isInterrupted()){result.failed=1;result.error="请求已取消";return result;}
        Map<String,Match> byId=new HashMap<>();
        if(current!=null)for(Match m:current)byId.put(m.id,m);
        List<Subscription> pending=new ArrayList<>();
        synchronized(LOCK) {
            recoverOwnedEvents(byId,result);
            for(String key:new ArrayList<>(links.getAll().keySet())) {
                if(!key.startsWith("match:"))continue;
                String id=key.substring(6);
                if(!resolveMissing&&!byId.containsKey(id))continue;
                long eventId=links.getLong("match:"+id,-1);
                if(eventId<=0) {removeLink(id);continue;}
                try {if(!ownsEvent(eventId,id)) {removeLink(id);continue;}}
                catch(RuntimeException e) {result.failed++;continue;}
                String record=links.getString("record:"+id,"");
                if(record.isEmpty()&&byId.containsKey(id)) {record=byId.get(id).json().toString();if(!links.edit().putString("record:"+id,record).commit()){result.failed++;continue;}}
                if(record.isEmpty()) {result.failed++;continue;}
                pending.add(new Subscription(id,eventId,record));
            }
        }
        // Network resolution happens outside LOCK so a slow calendar refresh cannot block a
        // user tapping subscribe/unsubscribe. A single sync runs at a time via SYNC_LOCK.
        Map<String,Match> resolved=new HashMap<>();
        boolean interrupted=false;
        if(!pending.isEmpty()) synchronized(resolveMissing?SYNC_LOCK:resolved) {
            try {if(resolveMissing)resolver.beginSync();}
            catch(RuntimeException e) {android.util.Log.w("MyScore","Cannot prepare calendar resolver",e);}
            for(Subscription sub:pending) {
                try {
                    Match m=byId.get(sub.id);
                    if(m==null) {
                        Match previous=Match.fromJson(new org.json.JSONObject(sub.record));
                        m=previous.finalised()?previous:resolver.resolve(previous);
                    }
                    if(m==null||!sub.id.equals(m.id)) {result.failed++;continue;}
                    Match previous=Match.fromJson(new org.json.JSONObject(sub.record));
                    if(m.updated>0&&previous.updated>m.updated)continue;
                    resolved.put(sub.id,m);
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                    result.failed++;interrupted=true;break;
                } catch(Exception e) {
                    result.failed++;
                    android.util.Log.w("MyScore","Calendar refresh failed: "+e.getClass().getSimpleName());
                }
            }
        }
        synchronized(LOCK) {
            for(Subscription sub:pending) {
                Match m=resolved.get(sub.id);
                if(m==null)continue;
                try {
                    // A concurrent subscribe/unsubscribe may have changed or removed this link
                    // while the network read was in flight. Never apply a stale snapshot.
                    if(links.getLong("match:"+sub.id,-1)!=sub.eventId)continue;
                    if(!sub.record.equals(links.getString("record:"+sub.id,"")))continue;
                    if(!ownsEvent(sub.eventId,sub.id)) {removeLink(sub.id);continue;}
                    long calendar=eventCalendar(sub.eventId);
                    if(calendar<0)calendar=calendarId();
                    if(m.finalised()) {
                        // Keep the historical calendar event, but stop tracking a completed match.
                        if(m.timeConfirmed&&m.start>0)updateEvent(sub.eventId,m,calendar,false);
                        deleteReminders(sub.eventId);
                        links.edit().putLong("retired:"+sub.id,sub.eventId).apply();
                        removeLink(sub.id);result.completed++;continue;
                    }
                    if(m.cancelled()) {
                        if(context.getContentResolver().delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,sub.eventId),null,null)>0)
                            requestCloud(calendar);
                        removeLink(sub.id);result.cancelled++;continue;
                    }
                    if(m.live()) {
                        deleteReminders(sub.eventId);
                        links.edit().remove("fingerprint:"+sub.id).apply();
                        if(m.timeConfirmed&&m.start>0)updateEvent(sub.eventId,m,calendar,false);
                        saveRecord(sub.id,m);result.updated++;continue;
                    }
                    if(!m.timeConfirmed||!m.scheduled()||m.start<=now) {
                        if(m.timeConfirmed&&m.start>0)updateEvent(sub.eventId,m,calendar,false);
                        deleteReminders(sub.eventId);
                        links.edit().remove("fingerprint:"+sub.id).apply();
                        saveRecord(sub.id,m);continue;
                    }
                    String fingerprint=fingerprint(m,calendar);
                    if(!fingerprint.equals(links.getString("fingerprint:"+sub.id,""))) {
                        updateEvent(sub.eventId,m,calendar);
                        if(ensureAlarm(sub.eventId,calendar))requestCloud(calendar);
                        result.updated++;
                        links.edit().putString("record:"+sub.id,m.json().toString()).putString("fingerprint:"+sub.id,fingerprint).apply();
                    } else {
                        if(ensureAlarm(sub.eventId,calendar)){result.updated++;requestCloud(calendar);}
                        saveRecord(sub.id,m);
                    }
                } catch(Exception e) {
                    result.failed++;
                    android.util.Log.w("MyScore","Calendar apply failed: "+e.getClass().getSimpleName());
                }
            }
            if(resolveMissing&&!interrupted) preferences.edit().putLong("calendar_checked",now).putString("calendar_result",result.message()).apply();
        }
        return result;
    }
    public int linkedEvents() {return subscribedIds().size();}
    private long insertEvent(Match m,long calendar) {
        Uri inserted=context.getContentResolver().insert(CalendarContract.Events.CONTENT_URI,eventValues(m,calendar));
        return inserted==null?-1:ContentUris.parseId(inserted);
    }
    private void updateEvent(long eventId,Match m,long calendar) {
        updateEvent(eventId,m,calendar,true);
    }
    private void updateEvent(long eventId,Match m,long calendar,boolean alarm) {
        ContentValues values=eventValues(m,calendar);values.put(CalendarContract.Events.HAS_ALARM,alarm?1:0);
        // Read actual provider fields: a recovered cloud event has no local fingerprint yet.
        // A no-op UPDATE would dirty it and cause an upload/download feedback loop.
        String[] columns=values.keySet().toArray(new String[0]);
        try(Cursor row=context.getContentResolver().query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,eventId),columns,null,null,null)) {
            if(row==null)throw new IllegalStateException("无法读取日历事件");
            if(row.moveToFirst()) {
                boolean same=true;
                for(int i=0;i<columns.length;i++)if(!java.util.Objects.equals(values.getAsString(columns[i]),row.isNull(i)?null:row.getString(i))){same=false;break;}
                if(same)return;
            }
        }
        if(context.getContentResolver().update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,eventId),values,null,null)!=1)
            throw new IllegalStateException("日历事件未更新，请稍后重试");
        requestCloud(calendar);
    }
    /** Reuse an event created by this app on any device; repair a missing reminder for future matches. */
    private int recoverOwnedEvents(Map<String,Match> known,Result result) {
        if(known!=null&&known.isEmpty())return 0;
        int recovered=0;
        long now=System.currentTimeMillis();
        Map<String,OwnedEvent> owned=new HashMap<>();
        Map<String,List<OwnedEvent>> copies=new HashMap<>();
        // One ownership snapshot replaces one provider read per event and per existing link.
        try(Cursor rows=context.getContentResolver().query(CalendarContract.Events.CONTENT_URI,
            new String[]{CalendarContract.Events._ID,CalendarContract.Events.DESCRIPTION,CalendarContract.Events.CUSTOM_APP_URI,
                CalendarContract.Events.DTSTART,CalendarContract.Events.CALENDAR_ID,CalendarContract.Events.TITLE,
                CalendarContract.Events._SYNC_ID,CalendarContract.Events.DIRTY,CalendarContract.Events.HAS_ALARM},
            CalendarContract.Events.CUSTOM_APP_PACKAGE+"=? AND "+CalendarContract.Events.DELETED+"=0",new String[]{context.getPackageName()},CalendarContract.Events._ID+" DESC")) {
            if(rows==null)throw new IllegalStateException("无法读取日历事件");
            while(rows.moveToNext()) {
                String description=rows.getString(1);if(description==null||!description.startsWith("MyScore:"))continue;
                int newline=description.indexOf('\n');if(newline<=8)continue;
                String id=description.substring(8,newline);
                if(known!=null&&!known.containsKey(id))continue;
                if(!("myscore://match/"+Uri.encode(id)).equals(rows.getString(2)))continue;
                OwnedEvent event=new OwnedEvent(rows.getLong(0),rows.getLong(3),rows.getLong(4),rows.getString(5),description,rows.getString(6),rows.getInt(7)!=0,rows.getInt(8)==1);
                copies.computeIfAbsent(id+"\n"+event.calendar,key->new ArrayList<>()).add(event);
                OwnedEvent previous=owned.get(id);
                if(previous==null||event.eventId==links.getLong("match:"+id,-1))owned.put(id,event);
            }
        } catch(RuntimeException e) {result.failed++;result.error=readable(e);return 0;}
        // Rapid cancel/resubscribe can download a stale copy while a new event uploads.
        // Only deduplicate fully acknowledged copies in the SAME calendar. Cloud IDs give
        // every device the same ordering; local row IDs could make devices delete each other.
        Set<Long> deduplicated=new HashSet<>();
        for(List<OwnedEvent> group:copies.values()) {
            if(group.size()<2||group.stream().anyMatch(e->e.start<=now||e.dirty||e.sync==null||e.sync.isBlank()))continue;
            if(group.stream().map(e->e.sync).distinct().count()!=group.size())continue;
            OwnedEvent keep=group.stream().max(Comparator.comparing(e->e.sync)).orElseThrow();
            String id=keep.description.substring(8,keep.description.indexOf('\n'));
            try {
                for(OwnedEvent duplicate:group)if(duplicate!=keep) {
                    if(context.getContentResolver().delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,duplicate.eventId),null,null)>0)
                        deduplicated.add(duplicate.calendar);
                }
                if(owned.get(id).calendar==keep.calendar)owned.put(id,keep);
            }catch(RuntimeException e){result.failed++;result.error=readable(e);}
        }
        for(long calendar:deduplicated)requestCloud(calendar);
        SharedPreferences.Editor edit=links.edit();boolean changed=false;
        if(known==null)for(String key:links.getAll().keySet())if(key.startsWith("match:")&&!owned.containsKey(key.substring(6))) {
            String id=key.substring(6);edit.remove(key).remove("record:"+id).remove("fingerprint:"+id);changed=true;
        }
        List<OwnedEvent> reminders=new ArrayList<>();
        for(Map.Entry<String,OwnedEvent> entry:owned.entrySet()) {
            String id=entry.getKey();OwnedEvent event=entry.getValue();
            boolean linked=links.getLong("match:"+id,-1)==event.eventId;
            if(!linked&&links.getLong("retired:"+id,-1)==event.eventId)continue;
            Match match=known==null?null:known.get(id);
            if(known==null&&linked)try {
                String record=links.getString("record:"+id,"");if(!record.isBlank())match=Match.fromJson(new org.json.JSONObject(record));
            }catch(Exception ignored){}
            if(!linked&&(event.start<=now||(match!=null&&(match.finalised()||match.cancelled()))))continue;
            if(!linked) {
                edit.putLong("match:"+id,event.eventId).remove("fingerprint:"+id);changed=true;recovered++;
            }
            if(!links.contains("record:"+id)||(!linked&&known==null)) {
                if(match==null)match=calendarRecord(id,event);
                if(match!=null){edit.putString("record:"+id,match.json().toString());changed=true;}
            }
            if(event.start>now&&(match==null||match.scheduled()||match.state.equals("unknown")))reminders.add(event);
        }
        if(changed&&!edit.commit()){result.failed++;result.error="无法恢复日历关联";return 0;}
        // A five-minute row is not enough: HyperOS notifies for METHOD_ALERT, while a Xiaomi
        // account whose allowed list starts with METHOD_DEFAULT used to be left on that method.
        if(!reminders.isEmpty())try {
            Map<Long,Integer> found=new HashMap<>();
            try(Cursor rows=context.getContentResolver().query(CalendarContract.Reminders.CONTENT_URI,
                new String[]{CalendarContract.Reminders.EVENT_ID,CalendarContract.Reminders.METHOD},
                CalendarContract.Reminders.MINUTES+"=?",new String[]{Integer.toString(REMIND_MINUTES)},null)) {
                if(rows==null)throw new IllegalStateException("无法读取日历提醒");
                while(rows.moveToNext()) {
                    long id=rows.getLong(0);int method=rows.getInt(1);
                    Integer previous=found.get(id);
                    if(previous==null||method==CalendarContract.Reminders.METHOD_ALERT)found.put(id,method);
                }
            }
            Map<Long,Integer> methods=new HashMap<>();
            for(OwnedEvent event:reminders) {
                int method=methods.computeIfAbsent(event.calendar,this::reminderMethod);
                Integer actual=found.get(event.eventId);
                if(actual!=null&&actual==method&&event.alarm)continue;
                try {
                    if(ensureAlarm(event.eventId,event.calendar)){result.updated++;requestCloud(event.calendar);}
                }catch(RuntimeException e){result.failed++;result.error=readable(e);}
            }
        }catch(RuntimeException e){result.failed++;result.error=readable(e);}
        return recovered;
    }
    private record OwnedEvent(long eventId,long start,long calendar,String title,String description,String sync,boolean dirty,boolean alarm){}
    /** Calendar facts only; the source resolver must confirm the match's current state. */
    private Match calendarRecord(String id,OwnedEvent event) {
        String[] parts=id.split(":");Competition c=parts.length>1?Competition.find(parts[1]):null;
        if(c==null)return null;
        Match m=new Match();m.id=id;m.league=c.id;m.group=c.group;m.start=event.start;m.timeConfirmed=event.start>0;
        m.title=event.title==null?"":event.title;String prefix=c.name+" · ";if(m.title.startsWith(prefix))m.title=m.title.substring(prefix.length());
        m.status="日历订阅 · 等待来源确认";
        String[] lines=event.description.split("\n");String sourcePrefix="北京时间 · ";if(lines.length>1&&lines[1].startsWith(sourcePrefix))m.source=lines[1].substring(sourcePrefix.length());
        if(lines.length>2)m.url=lines[2];
        return m;
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
    private boolean ensureAlarm(long eventId,long calendar) {
        boolean changed=ensureReminder(eventId,reminderMethod(calendar));
        if(!eventHasAlarm(eventId)) {
            ContentValues alarm=new ContentValues();alarm.put(CalendarContract.Events.HAS_ALARM,1);
            if(context.getContentResolver().update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,eventId),alarm,null,null)!=1)
                throw new IllegalStateException("日历提醒状态未更新");
            changed=true;
        }
        return changed;
    }
    /** @return true when a reminder row was inserted or its method changed. */
    private boolean ensureReminder(long eventId,int method) {
        long current=reminderId(eventId,-1);
        if(current>0&&hasReminder(eventId,method))return deleteOtherReminders(eventId,reminderId(eventId,method))>0;
        if(current>0) {
            ContentValues values=new ContentValues();values.put(CalendarContract.Reminders.METHOD,method);
            if(context.getContentResolver().update(CalendarContract.Reminders.CONTENT_URI,values,"_id=?",new String[]{Long.toString(current)})==1
                &&hasReminder(eventId,method)) {
                deleteOtherReminders(eventId,reminderId(eventId,method));
                return true;
            }
        }
        long reminder=tryReminder(eventId,method);
        if(reminder<0&&method!=CalendarContract.Reminders.METHOD_DEFAULT)reminder=tryReminder(eventId,CalendarContract.Reminders.METHOD_DEFAULT);
        if(reminder<0) {
            if(hasReminder(eventId,method)||reminderId(eventId,-1)>0)return false;
            throw new IllegalStateException("日历未接受提醒");
        }
        deleteOtherReminders(eventId,reminder);
        return true;
    }
    private boolean hasReminder(long eventId,int method) {return reminderId(eventId,method)>0;}
    private long reminderId(long eventId,int method) {
        String selection="event_id=? AND minutes=?";
        String[] args=new String[]{Long.toString(eventId),Integer.toString(REMIND_MINUTES)};
        if(method>=0) {
            selection+=" AND method=?";
            args=new String[]{Long.toString(eventId),Integer.toString(REMIND_MINUTES),Integer.toString(method)};
        }
        try(Cursor cur=context.getContentResolver().query(CalendarContract.Reminders.CONTENT_URI,new String[]{"_id"},selection,args,null)) {
            if(cur==null)throw new IllegalStateException("无法读取日历提醒");
            return cur.moveToFirst()?cur.getLong(0):-1;
        }
    }
    private int deleteOtherReminders(long eventId,long keep) {
        return context.getContentResolver().delete(CalendarContract.Reminders.CONTENT_URI,"event_id=? AND _id<>?",new String[]{Long.toString(eventId),Long.toString(keep)});
    }
    private boolean eventHasAlarm(long eventId) {
        try(Cursor cur=context.getContentResolver().query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,eventId),
            new String[]{CalendarContract.Events.HAS_ALARM},null,null,null)) {
            if(cur==null)throw new IllegalStateException("无法读取日历事件");
            return cur.moveToFirst()&&cur.getInt(0)==1;
        }
    }
    private long tryReminder(long eventId,int method) {
        ContentValues reminder=new ContentValues();
        reminder.put(CalendarContract.Reminders.EVENT_ID,eventId);
        reminder.put(CalendarContract.Reminders.MINUTES,REMIND_MINUTES);
        reminder.put(CalendarContract.Reminders.METHOD,method);
        Uri inserted=context.getContentResolver().insert(CalendarContract.Reminders.CONTENT_URI,reminder);
        return inserted==null?-1:ContentUris.parseId(inserted);
    }
    private void deleteReminders(long eventId) {
        context.getContentResolver().delete(CalendarContract.Reminders.CONTENT_URI,"event_id=?",new String[]{Long.toString(eventId)});
    }
    private int reminderMethod(long calendar) {
        try(Cursor cur=context.getContentResolver().query(ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI,calendar),
            new String[]{CalendarContract.Calendars.ALLOWED_REMINDERS},null,null,null)) {
            if(cur!=null&&cur.moveToFirst())return preferredReminder(cur.getString(0));
        } catch(RuntimeException ignored) {}
        return CalendarContract.Reminders.METHOD_ALERT;
    }
    /** Prefer a status-bar alert wherever the calendar allows it, even if default is listed first. */
    private static int preferredReminder(String allowed) {
        boolean alert=false,alarm=false,fallback=false;
        if(allowed!=null)for(String part:allowed.split(",")) {
            String value=part.trim();
            if(value.equals("1"))alert=true;
            else if(value.equals("4"))alarm=true;
            else if(value.equals("0"))fallback=true;
        }
        if(alert)return CalendarContract.Reminders.METHOD_ALERT;
        if(alarm)return CalendarContract.Reminders.METHOD_ALARM;
        if(fallback)return CalendarContract.Reminders.METHOD_DEFAULT;
        return CalendarContract.Reminders.METHOD_ALERT;
    }
    private String fingerprint(Match m,long calendar) {return m.start+"|"+m.displayTitle()+"|"+REMIND_MINUTES+"|"+calendar+"|"+m.state;}
    private void saveRecord(String id,Match m) {links.edit().putString("record:"+id,m.json().toString()).apply();}
    private void removeLink(String id) {links.edit().remove("match:"+id).remove("record:"+id).remove("fingerprint:"+id).apply();}
    private static String readable(Exception e) {String m=e.getMessage();return m==null||m.isBlank()?"日历写入失败":m;}
    private boolean ownsEvent(long id,String matchId) {
        try(Cursor cur=context.getContentResolver().query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,id),
            new String[]{CalendarContract.Events.DESCRIPTION,CalendarContract.Events.CUSTOM_APP_PACKAGE,CalendarContract.Events.DELETED},null,null,null)) {
            // A null cursor means the provider is temporarily unavailable; do not treat that
            // as "not ours" and orphan the event by removing the link.
            if(cur==null)throw new IllegalStateException("无法读取日历事件");
            return cur.moveToFirst() && cur.getInt(2)==0 && context.getPackageName().equals(cur.getString(1)) && cur.getString(0)!=null && cur.getString(0).startsWith("MyScore:"+matchId+"\n");
        }
    }
    private long eventCalendar(long id) {
        try(Cursor cur=context.getContentResolver().query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,id),new String[]{CalendarContract.Events.CALENDAR_ID},null,null,null)) {
            return cur!=null && cur.moveToFirst()?cur.getLong(0):-1;
        }
    }
}
