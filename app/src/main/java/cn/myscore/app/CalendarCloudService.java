package cn.myscore.app;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncRequest;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.provider.CalendarContract;

/** Shizuku UserService: only synchronizes Xiaomi calendars for the current device user. */
public final class CalendarCloudService extends ICalendarCloud.Stub {
    private final int owner;
    public CalendarCloudService(){owner=-1;}
    public CalendarCloudService(Context context)throws Exception {
        owner=context.getApplicationInfo().uid;
    }
    @Override public String requestSync() {
        if(owner<0||Binder.getCallingUid()!=owner)throw new SecurityException("Unexpected calendar sync caller");
        int uid=Process.myUid();if(uid!=2000&&uid!=0)throw new SecurityException("Calendar sync requires authorized Shizuku");
        long identity=Binder.clearCallingIdentity();
        try {
            int requested=0;
            // UserService is not an Android app process: AccountManager's cached Context uses
            // the client package. Call the same framework API with our actual shell attribution.
            android.os.IBinder binder=(android.os.IBinder)Class.forName("android.os.ServiceManager")
                .getMethod("getService",String.class).invoke(null,"account");
            Object manager=Class.forName("android.accounts.IAccountManager$Stub").getMethod("asInterface",android.os.IBinder.class).invoke(null,binder);
            Account[] accounts=(Account[])Class.forName("android.accounts.IAccountManager")
                .getMethod("getAccountsAsUser",String.class,int.class,String.class).invoke(manager,"com.xiaomi",owner/100000,"com.android.shell");
            for(Account account:accounts) {
                if(!"com.xiaomi".equals(account.type))continue;
                SyncRequest.Builder request=new SyncRequest.Builder().syncOnce().setSyncAdapter(account,CalendarContract.AUTHORITY)
                    .setExtras(new Bundle()).setManual(true);
                if(android.os.Build.VERSION.SDK_INT>=31)request.setScheduleAsExpeditedJob(true);
                else request.setExpedited(true);
                ContentResolver.requestSync(request.build());
                requested++;
            }
            return uid+"|"+requested;
        }catch(Exception e){throw new IllegalStateException("Xiaomi calendar request failed",e);}
        finally{Binder.restoreCallingIdentity(identity);}
    }
    @Override public void destroy() {
        int caller=Binder.getCallingUid();if(caller!=owner&&caller!=2000&&caller!=0)throw new SecurityException("Unexpected service owner");
        System.exit(0);
    }
}
