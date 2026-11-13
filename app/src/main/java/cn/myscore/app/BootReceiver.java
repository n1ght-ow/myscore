package cn.myscore.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores subscription finish-window and audit jobs after reboot or in-place APK update. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
            && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        Http.init(context);
        CalendarJob.schedule(context);
    }
}
