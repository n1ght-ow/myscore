package cn.myscore.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public final class Cs2EventInstrumentation extends Instrumentation {
    private String theme;
    private Activity activity;
    private int checks;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        theme = arguments == null ? "dark" : arguments.getString("theme", "dark");
        start();
    }

    @Override public void runOnMainSync(Runnable action) {
        Throwable[] failure = new Throwable[1];
        super.runOnMainSync(() -> {
            try { action.run(); } catch (Throwable caught) { failure[0] = caught; }
        });
        if (failure[0] instanceof Error error) throw error;
        if (failure[0] != null) throw new RuntimeException(failure[0]);
    }

    @Override public void onStart() {
        Context context = getTargetContext();
        SharedPreferences settings = context.getSharedPreferences("settings", 0);
        String original = settings.getString("theme", null);
        Bundle result = new Bundle();
        int outcome = Activity.RESULT_OK;
        try {
            check(android.os.Build.HARDWARE.equals("ranchu") || android.os.Build.HARDWARE.equals("goldfish"), "emulator only");
            settings.edit().putString("theme", theme).commit();
            Activity home = launch(MainActivity.class, null, null);
            requireText(home, "CS2");
            click(home, "CS2");
            ActivityMonitor monitor = addMonitor(Cs2EventActivity.class.getName(), null, false);
            try {
                click(home, "Tournaments");
                activity = waitForMonitorWithTimeout(monitor, 5000);
            } finally { removeMonitor(monitor); }
            check(activity != null, "CS2 home opens tournament overview");
            closeActivity();

            activity = launch(Cs2EventActivity.class, "csgo_tt_9303", "PGL 新加坡 Major 2026 第1阶段");
            await("cs2-swiss-flow");
            awaitText("Stage 2");
            requireText(activity, "0–0");
            inspectGraph("cs2-swiss-flow", false);
            capture("singapore-swiss");
            runOnMainSync(() -> {
                HorizontalScrollView graph = activity.getWindow().getDecorView().findViewWithTag("cs2-swiss-flow");
                View record = graph.findViewWithTag("cs2-record-1:0");
                check(record != null && record.performClick() && record.isSelected(), "select a Swiss record");
            });
            requireText(activity, "Round 2  ·  1–0");
            capture("singapore-flow");
            click(activity, "Stage 2");
            await("cs2-swiss-flow");
            requireText(activity, "Swiss stage");
            closeActivity();

            activity = launch(Cs2EventActivity.class, "csgo_tt_8245", "IEM 北京 2026");
            await("cs2-knockout-flow");
            requireText(activity, "Upper bracket");
            requireText(activity, "Lower bracket");
            inspectGraph("cs2-knockout-flow", false);
            capture("beijing-double");
            click(activity, "Lower bracket");
            await("cs2-knockout-flow");
            click(activity, "Playoffs");
            await("cs2-knockout-flow");
            requireText(activity, "Quarterfinals");
            requireText(activity, "Semifinals");
            requireText(activity, "Final");
            requireText(activity, "Playoff bracket");
            inspectGraph("cs2-knockout-flow", false);
            capture("beijing-single");
            openMatch();
            closeActivity();
            result.putString("stream", "\nPASS: " + checks + " CS2 event checks (" + theme + ").\n");
        } catch (Throwable failure) {
            outcome = Activity.RESULT_CANCELED;
            result.putString("stream", "\nFAIL after " + checks + ": " + android.util.Log.getStackTraceString(failure));
        } finally {
            settings.edit().putString("theme", original).commit();
        }
        finish(outcome, result);
    }

    private Activity launch(Class<? extends Activity> type, String event, String name) {
        Intent intent = new Intent(getTargetContext(), type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (event != null) intent.putExtra("event", event).putExtra("name", name);
        Activity started = startActivitySync(intent);
        waitForIdleSync();
        return started;
    }

    private void closeActivity() {
        runOnMainSync(() -> activity.finish());
        waitForIdleSync();
    }

    private void openMatch() {
        ActivityMonitor monitor = addMonitor(DetailActivity.class.getName(), null, false);
        Activity detail;
        try {
            runOnMainSync(() -> {
                View match = activity.getWindow().getDecorView().findViewWithTag("cs2-event-match");
                check(match != null && match.performClick(), "tournament match can open");
            });
            detail = waitForMonitorWithTimeout(monitor, 5000);
            check(detail != null, "match detail opens from diagram");
        } finally { removeMonitor(monitor); }
        runOnMainSync(detail::finish);
    }

    private void await(String tag) {
        long deadline = SystemClock.uptimeMillis() + 25_000;
        boolean[] present = new boolean[1];
        while (!present[0] && SystemClock.uptimeMillis() < deadline) {
            runOnMainSync(() -> present[0] = activity.getWindow().getDecorView().findViewWithTag(tag) != null);
            if (!present[0]) SystemClock.sleep(100);
        }
        check(present[0], "loaded " + tag);
        waitForIdleSync();
    }

    private void awaitText(String label) {
        long deadline = SystemClock.uptimeMillis() + 15_000;
        boolean[] present = new boolean[1];
        while (!present[0] && SystemClock.uptimeMillis() < deadline) {
            runOnMainSync(() -> present[0] = find(activity.getWindow().getDecorView(), label) != null);
            if (!present[0]) SystemClock.sleep(100);
        }
        check(present[0], "loaded " + label);
    }

    private void click(Activity on, String label) {
        runOnMainSync(() -> {
            View view = find(on.getWindow().getDecorView(), label);
            while (view != null && !view.isClickable() && view.getParent() instanceof View parent) view = parent;
            check(view != null && view.performClick(), "click " + label);
        });
        waitForIdleSync();
    }

    private void requireText(Activity on, String label) {
        runOnMainSync(() -> check(find(on.getWindow().getDecorView(), label) != null, "text " + label));
    }

    private static View find(View view, String label) {
        if (view.getVisibility() != View.VISIBLE) return null;
        if ((view instanceof TextView text && label.contentEquals(text.getText())) || label.equals(view.getContentDescription())) return view;
        if (view instanceof ViewGroup group) for (int index = 0; index < group.getChildCount(); index++) {
            View found = find(group.getChildAt(index), label);
            if (found != null) return found;
        }
        return null;
    }

    private void inspectGraph(String tag, boolean clipped) {
        runOnMainSync(() -> {
            HorizontalScrollView graph = activity.getWindow().getDecorView().findViewWithTag(tag);
            check(!clipped || graph.getChildAt(0).getWidth() > graph.getWidth(), "graph exceeds one viewport");
            inspect(graph.getChildAt(0));
        });
    }

    private void inspect(View view) {
        if (view.getVisibility() != View.VISIBLE || view.getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) return;
        if (view instanceof TextView text && text.getLayout() != null && text.length() > 0) {
            int width = text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight();
            for (int line = 0; line < text.getLineCount(); line++) {
                check(text.getLayout().getEllipsisCount(line) == 0 && text.getLayout().getLineMax(line) <= width + 2, "width fits: " + text.getText());
            }
            check(text.getLayout().getHeight() <= text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom() + 1,
                "height fits: " + text.getText());
        }
        if (view instanceof ViewGroup group) for (int index = 0; index < group.getChildCount(); index++) inspect(group.getChildAt(index));
    }

    private void capture(String name) throws IOException {
        waitForIdleSync();
        SystemClock.sleep(250);
        Bitmap image = NativeCapture.take(this, activity.getWindow());
        check(image != null, "screenshot " + name);
        File folder = new File(getTargetContext().getExternalFilesDir(null), "cs2-event-review");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Cannot create " + folder);
        try (FileOutputStream out = new FileOutputStream(new File(folder, theme + "-" + name + ".png"))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally { image.recycle(); }
    }

    private void check(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
        checks++;
    }
}
