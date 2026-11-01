package cn.myscore.app;

import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncAdapterType;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.CalendarContract;

/** Real-device cloud request probe: no test events, account names, or settings changes. */
public final class CalendarCloudInstrumentation extends Instrumentation {
    private boolean state;
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);state=arguments!=null&&"true".equals(arguments.getString("state"));start();}
    @Override public void onStart(){
        Bundle report=new Bundle();int outcome=Activity.RESULT_OK;
        try {
            Context context=getTargetContext();
            if(state){readState(context,report);finish(Activity.RESULT_OK,report);return;}
            int visible=AccountManager.get(context).getAccountsByType("com.xiaomi").length;
            int calendars=0,allCalendars=0,adapters=0;
            for(SyncAdapterType adapter:ContentResolver.getSyncAdapterTypes())
                if("com.xiaomi".equals(adapter.accountType)&&CalendarContract.AUTHORITY.equals(adapter.authority))adapters++;
            try(Cursor rows=context.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,new String[]{"_id"},"account_type=? AND sync_events=1",new String[]{"com.xiaomi"},null)){
                if(rows!=null)calendars=rows.getCount();
            }
            try(Cursor rows=context.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,new String[]{"_id"},"account_type=?",new String[]{"com.xiaomi"},null)){
                if(rows!=null)allCalendars=rows.getCount();
            }
            report.putString("stream","\nCloud probe: visible Xiaomi accounts="+visible+", Xiaomi calendars="+allCalendars+", sync-enabled calendars="+calendars+", calendar adapters="+adapters+"\n");
            if(android.os.Build.VERSION.SDK_INT>=29&&visible>0) {
                getUiAutomation().adoptShellPermissionIdentity(android.Manifest.permission.READ_SYNC_SETTINGS,android.Manifest.permission.READ_SYNC_STATS);
                try {
                    android.accounts.Account account=AccountManager.get(context).getAccountsByType("com.xiaomi")[0];
                    report.putString("stream",report.getString("stream")+"syncable="+ContentResolver.getIsSyncable(account,CalendarContract.AUTHORITY)+", calendar auto-sync="+ContentResolver.getSyncAutomatically(account,CalendarContract.AUTHORITY)+", master auto-sync="+ContentResolver.getMasterSyncAutomatically()+"\n");
                }finally{getUiAutomation().dropShellPermissionIdentity();}
            }
            CalendarCloudSync.opened(context);
            Thread.sleep(1200);
        }catch(Throwable e){outcome=Activity.RESULT_CANCELED;report.putString("stream",android.util.Log.getStackTraceString(e));}
        finish(outcome,report);
    }
    private void readState(Context context,Bundle report)throws Exception {
        org.json.JSONObject root=new org.json.JSONObject();org.json.JSONArray events=new org.json.JSONArray(),calendars=new org.json.JSONArray();
        org.json.JSONArray hashes=new org.json.JSONArray();for(android.accounts.Account account:AccountManager.get(context).getAccountsByType("com.xiaomi"))hashes.put(Integer.toHexString(account.name.hashCode()));root.put("accountHashes",hashes);
        try(Cursor rows=context.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,new String[]{"_id","account_type","sync_events","visible"},null,null,null)){
            if(rows!=null)while(rows.moveToNext())calendars.put(new org.json.JSONObject().put("id",rows.getLong(0)).put("type",rows.getString(1)).put("sync",rows.getInt(2)).put("visible",rows.getInt(3)));
        }
        try(Cursor rows=context.getContentResolver().query(CalendarContract.Events.CONTENT_URI,
            new String[]{"_id","calendar_id","description","_sync_id","dirty","deleted","customAppPackage","customAppUri","dtstart"},"description LIKE ?",new String[]{"MyScore:%"},"_id DESC")){
            if(rows!=null)while(rows.moveToNext()){
                String description=rows.getString(2);int end=description.indexOf('\n');String id=end>8?description.substring(8,end):"invalid";
                events.put(new org.json.JSONObject().put("event",rows.getLong(0)).put("calendar",rows.getLong(1)).put("match",id).put("syncId",rows.isNull(3)?"":rows.getString(3)).put("dirty",rows.getInt(4)).put("deleted",rows.getInt(5)).put("package",rows.getString(6)).put("uri",rows.getString(7)).put("start",rows.getLong(8)).put("linked",context.getSharedPreferences("calendar-links",0).getLong("match:"+id,-1)));
            }
        }
        root.put("calendars",calendars).put("events",events).put("selectedCalendar",context.getSharedPreferences("settings",0).getLong("calendar_id",-1));
        report.putString("stream",root.toString(2));
    }
}
