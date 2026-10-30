package cn.myscore.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

/** Exercises the production shared launcher on installed broadcaster apps, without changing data. */
public final class WatchAppInstrumentation extends Instrumentation {
    private String url;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        url = args.getString("url");
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            ActivityMonitor monitor = addMonitor(MainActivity.class.getName(), null, false);
            try (android.os.ParcelFileDescriptor command = getUiAutomation().executeShellCommand(
                    "am start -n cn.myscore.app/.MainActivity")) {
                // Start from the shell so Android's background launch policy cannot stall the probe.
                Thread.sleep(1000);
            }
            Activity activity = waitForMonitorWithTimeout(monitor, 15000);
            removeMonitor(monitor);
            if (activity == null) throw new IllegalStateException("MainActivity did not start within 15 seconds");
            long deadline = android.os.SystemClock.elapsedRealtime() + 10000;
            while (!activity.hasWindowFocus() && android.os.SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(100);
            }
            if (!activity.hasWindowFocus()) throw new IllegalStateException("MyScore did not become foreground");
            runOnMainSync(() -> new WatchLauncher(activity, WatchRepository.get(activity)).launch(
                new WatchLinks.Link("probe", "probe", "probe", "", url, WatchLinks.Kind.ROOM)));
            Thread.sleep(5000);
            result.putString("stream", "Shared WatchLauncher dispatched " + url
                + "; inspect foreground app and playback screen separately.\n");
            finish(Activity.RESULT_OK, result);
        } catch (Exception e) {
            result.putString("stream", e.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
