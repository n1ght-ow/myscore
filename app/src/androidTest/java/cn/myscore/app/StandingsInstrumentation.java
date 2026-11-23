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
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class StandingsInstrumentation extends Instrumentation {
    private String theme;
    private Activity activity;
    private int checks;
    private boolean cs2Only;

    @Override public void runOnMainSync(Runnable action) {
        Throwable[] failure = new Throwable[1];
        super.runOnMainSync(() -> {
            try { action.run(); }
            catch (Throwable caught) { failure[0] = caught; }
        });
        if (failure[0] instanceof Error error) throw error;
        if (failure[0] != null) throw new RuntimeException(failure[0]);
    }

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        theme = arguments == null ? "dark" : arguments.getString("theme", "dark");
        cs2Only = arguments != null && "cs2".equals(arguments.getString("sport"));
        start();
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
            Activity home = startActivitySync(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            if (cs2Only) chooseCs2OnHome(home);
            ActivityMonitor monitor = addMonitor(StandingsActivity.class.getName(), null, false);
            try {
                click(home, "Standings");
                activity = waitForMonitorWithTimeout(monitor, 5000);
            } finally { removeMonitor(monitor); }
            check(activity != null, "ranking destination opens from home");
            if (cs2Only) check(selected("CS2"), "CS2 opens directly from its home filter");
            List<String> sports = cs2Only ? List.of("CS2") : List.of("Premier League", "Champions League", "FA Cup", "Carabao Cup", "NBA", "NFL", "F1", "CS2");
            for (String label : sports) {
                click(activity, label);
                waitForData(label);
                inspect();
                capture(label);
                String other = switch (label) { case "NBA" -> "West"; case "NFL" -> "NFC"; case "F1" -> "Constructors"; default -> ""; };
                if (!other.isEmpty() && !existsTag("adaptive-columns")) {
                    click(activity, other);
                    inspect();
                    capture(other);
                }
                if (label.equals("FA Cup") || label.equals("Carabao Cup")) cycleCup(label);
                if (label.equals("CS2")) inspectCs2();
            }
            click(activity, "Back");
            awaitHome(home);
            result.putString("stream", "\nPASS: " + checks + " native standings checks (" + theme + ").\n");
        } catch (Throwable failure) {
            outcome = Activity.RESULT_CANCELED;
            result.putString("stream", "\nFAIL after " + checks + ": " + android.util.Log.getStackTraceString(failure));
        } finally {
            settings.edit().putString("theme", original).commit();
        }
        finish(outcome, result);
    }

    private void chooseCs2OnHome(Activity home) {
        runOnMainSync(() -> {
            View item = find(home.getWindow().getDecorView(), "CS2", false);
            while (item != null && !item.isClickable() && item.getParent() instanceof View parent) item = parent;
            check(item != null && item.performClick(), "select CS2 home filter");
        });
        waitForIdleSync();
    }

    private void inspectCs2() throws Exception {
        check(selected("Global"), "global VRS starts selected");
        assertFirstRank("1");
        click(activity, "Next page");
        assertFirstRank("31");
        inspect();
        click(activity, "Previous page");
        assertFirstRank("1");
        for (String region : List.of("Europe", "Americas", "Asia")) {
            click(activity, region);
            check(selected(region), "region selected: " + region);
            assertFirstRank("1");
            inspect();
        }
        capture("CS2-Asia");
    }

    private void assertFirstRank(String rank) {
        runOnMainSync(() -> {
            View root = activity.getWindow().getDecorView();
            LinearLayout table = root.findViewWithTag("standings-table");
            LinearLayout names = (LinearLayout)table.getChildAt(0);
            check(names.getChildCount() > 1 && names.getChildCount() <= 31, "VRS renders at most 30 teams per page");
            ViewGroup row = (ViewGroup)names.getChildAt(1);
            check(rank.contentEquals(((TextView)row.getChildAt(0)).getText()), "first rank is " + rank);
        });
    }

    private boolean selected(String label) {
        boolean[] selected = new boolean[1];
        runOnMainSync(() -> {
            View tab = find(activity.getWindow().getDecorView(), label, true);
            selected[0] = tab != null && tab.isSelected();
        });
        return selected[0];
    }

    private void waitForData(String label) {
        long deadline = SystemClock.uptimeMillis() + 60_000;
        while (SystemClock.uptimeMillis() < deadline) {
            if (existsTag("standings-table") || existsTag("standings-round")) return;
            boolean[] failed = new boolean[1];
            runOnMainSync(() -> failed[0] = find(activity.getWindow().getDecorView(), "暂时无法获取赛事数据，请刷新重试", false) != null);
            if (failed[0]) break;
            SystemClock.sleep(150);
        }
        throw new AssertionError(label + " did not return live standings");
    }

    private void inspect() {
        runOnMainSync(() -> {
            List<View> tables = tagged(activity.getWindow().getDecorView(), "standings-table");
            for (View table : tables) {
                LinearLayout grid = (LinearLayout)table;
                LinearLayout names = (LinearLayout)grid.getChildAt(0);
                HorizontalScrollView horizontal = grid.findViewWithTag("standings-metrics");
                LinearLayout numbers = (LinearLayout)horizontal.getChildAt(0);
                check(names.getChildCount() == numbers.getChildCount(), "one numeric row per identity");
                for (int index = 0; index < names.getChildCount(); index++) {
                    View name = names.getChildAt(index), values = numbers.getChildAt(index);
                    check(name.getTop() == values.getTop() && name.getHeight() == values.getHeight(), "shared row line " + index);
                    inspectText(name);
                    inspectText(values);
                }
                if (numbers.getWidth() > horizontal.getWidth()) {
                    int left = names.getLeft();
                    horizontal.scrollTo(horizontal.getWidth(), 0);
                    check(horizontal.getScrollX() > 0 && names.getLeft() == left, "scrolling metrics never moves identities");
                    horizontal.scrollTo(0, 0);
                }
            }
            for (View round : tagged(activity.getWindow().getDecorView(), "standings-round")) inspectText(round);
        });
    }

    private void inspectText(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView text && text.getLayout() != null && text.getText().length() > 0) {
            if ("standings-metric".equals(text.getTag())) check(text.getLineCount() == 1, "metric stays on one line: " + text.getText());
            int width = text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight();
            for (int line = 0; line < text.getLineCount(); line++) {
                float extent = text.getLayout().getLineMax(line);
                check(text.getLayout().getEllipsisCount(line) == 0 && extent <= width + 2,
                    "text fits: " + text.getText() + " requires " + extent + " in " + width);
            }
            check(text.getLayout().getHeight() <= text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom() + 1,
                "text height fits: " + text.getText() + " requires " + text.getLayout().getHeight() + " in " + text.getHeight());
        }
        if (view instanceof ViewGroup group) for (int index = 0; index < group.getChildCount(); index++) inspectText(group.getChildAt(index));
    }

    private void awaitHome(Activity home) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        boolean[] returned = new boolean[1];
        while (!returned[0] && SystemClock.uptimeMillis() < deadline) {
            runOnMainSync(() -> returned[0] = home.hasWindowFocus() && home.getWindow().getDecorView().isShown());
            if (!returned[0]) SystemClock.sleep(50);
        }
        check(returned[0], "back returns to original home");
    }

    private void cycleCup(String label) {
        runOnMainSync(() -> {
            LinearLayout panel = (LinearLayout)tagged(activity.getWindow().getDecorView(), "standings-round").get(0);
            ViewGroup parent = (ViewGroup)panel.getParent();
            if (!(parent.getChildAt(0) instanceof HorizontalScrollView scope)) return;
            ViewGroup options = (ViewGroup)scope.getChildAt(0);
            for (int index = options.getChildCount() - 1; index >= 0; index--) {
                View round = options.getChildAt(index);
                if (!round.isSelected()) { round.performClick(); return; }
            }
        });
        waitForIdleSync();
        waitForData(label);
        inspect();
    }

    private void capture(String screen) throws Exception {
        waitForIdleSync();
        SystemClock.sleep(350);
        Bitmap image = NativeCapture.take(this, activity.getWindow());
        check(image != null, "captured " + screen);
        File folder = new File(getTargetContext().getExternalFilesDir(null), "standings-review");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new Exception("Could not create " + folder);
        try (FileOutputStream file = new FileOutputStream(new File(folder, theme + "-" + screen + ".png"))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, file);
        } finally { image.recycle(); }
    }

    private boolean existsTag(String tag) {
        boolean[] result = new boolean[1];
        runOnMainSync(() -> result[0] = activity.getWindow().getDecorView().findViewWithTag(tag) != null);
        waitForIdleSync();
        return result[0];
    }

    private void click(Activity on, String label) {
        runOnMainSync(() -> {
            View target = find(on.getWindow().getDecorView(), label, true);
            check(target != null && target.performClick(), "click " + label);
        });
        waitForIdleSync();
    }

    private static View find(View view, String label, boolean clickable) {
        if ((!clickable || view.isClickable()) && ((view instanceof TextView text && label.contentEquals(text.getText()))
            || label.equals(view.getContentDescription()))) return view;
        if (view instanceof ViewGroup group) for (int index = 0; index < group.getChildCount(); index++) {
            View found = find(group.getChildAt(index), label, clickable);
            if (found != null) return found;
        }
        return null;
    }

    private static List<View> tagged(View root, String tag) {
        List<View> result = new ArrayList<>();
        if (tag.equals(root.getTag())) result.add(root);
        if (root instanceof ViewGroup group) for (int index = 0; index < group.getChildCount(); index++) result.addAll(tagged(group.getChildAt(index), tag));
        return result;
    }

    private void check(boolean passes, String detail) {
        if (!passes) throw new AssertionError(detail);
        checks++;
    }
}
