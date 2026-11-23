package cn.myscore.app;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class EnglishUiInstrumentation extends Instrumentation {
    private int checks;
    private String theme;
    private String only;
    private final List<String> untranslated = new ArrayList<>();

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        theme = args == null ? "dark" : args.getString("theme", "dark");
        only = args == null ? "all" : args.getString("routes", "all");
        start();
    }

    @Override public void runOnMainSync(Runnable work) {
        Throwable[] failed = new Throwable[1];
        super.runOnMainSync(() -> { try { work.run(); } catch (Throwable failure) { failed[0] = failure; } });
        if (failed[0] instanceof Error error) throw error;
        if (failed[0] != null) throw new RuntimeException(failed[0]);
    }

    @Override public void onStart() {
        Context context = getTargetContext();
        SharedPreferences settings = context.getSharedPreferences("settings", 0);
        String original = settings.getString("theme", null);
        Bundle report = new Bundle();
        int code = Activity.RESULT_OK;
        try {
            check(android.os.Build.HARDWARE.equals("ranchu") || android.os.Build.HARDWARE.equals("goldfish"), "emulator only");
            settings.edit().putString("theme", theme).commit();
            if (!only.equals("detail")) chrome();
            if (!only.equals("chrome")) details();
            check(untranslated.isEmpty(), "Untranslated content: " + untranslated);
            report.putString("stream", "\nPASS: " + checks + " English UI checks (" + theme + ", " + only + ").\n");
        } catch (Throwable failure) {
            code = Activity.RESULT_CANCELED;
            android.util.Log.e("MyScoreEnglish", "Audit failed", failure);
            report.putString("stream", "\nFAIL after " + checks + ": " + android.util.Log.getStackTraceString(failure));
        } finally { settings.edit().putString("theme", original).commit(); }
        finish(code, report);
    }

    private void chrome() throws Exception {
        Activity main = launch(new Intent(getTargetContext(), MainActivity.class));
        waitFor(main, "Scores", 5000);
        checkAndCapture(main, "scores");
        click(main, "Choose date", true);
        checkDialog(main, "date");
        closeDialog(main);
        click(main, "Settings", false);
        waitFor(main, "Check for updates", 5000);
        checkAndCapture(main, "settings");
        boolean tablet = Ui.isPad(main);
        click(main, tablet ? "Help" : "Theme", false);
        checkDialog(main, tablet ? "help" : "appearance");
        closeDialog(main);
        end(main);
        Activity update = launch(new Intent(getTargetContext(), UpdateActivity.class));
        checkAndCapture(update, "update");
        end(update);
        Team arsenal = new Team();
        arsenal.league = "epl";
        arsenal.id = "359";
        arsenal.name = "Arsenal";
        Activity team = launch(new Intent(getTargetContext(), TeamActivity.class).putExtra("team", arsenal.json().toString()));
        waitFor(team, "Arsenal", 5000);
        checkAndCapture(team, "team");
        end(team);
    }

    private void details() throws Exception {
        String[][] targets = {{"epl", "espn:epl:401879256:401879256"}, {"nba", "espn:nba:401909848:401909848"},
            {"nfl", "espn:nfl:401872996:401872996"}, {"f1", "espn:f1:600057427:401839031"}, {"cs2", "five:cs2:csgo_mc_2398756"}};
        DetailRepository repository = new DetailRepository(getTargetContext());
        for (String[] target : targets) {
            Match request = new Match();
            request.league = target[0]; request.id = target[1]; request.group = Competition.find(target[0]).group;
            MatchDetail detail = repository.fetch(request, true);
            check(detail.match.id.equals(request.id), "real detail " + request.league);
            Activity screen = launch(new Intent(getTargetContext(), DetailActivity.class).putExtra("match", detail.match.json().toString()));
            waitFor(screen, "Overview", 5000);
            SystemClock.sleep(250);
            checkAndCapture(screen, request.league + "-overview");
            List<String> tabs = switch (request.league) {
                case "epl" -> List.of("Lineup", "Stats", "Events");
                case "nba", "nfl" -> List.of("Players", "Stats", "Events");
                case "f1" -> List.of("Drivers", "Results");
                default -> List.of("Players", "Maps");
            };
            for (String tab : tabs) {
                check(canClick(screen, tab), "detail tab " + tab + " for " + request.league);
                click(screen, tab, false);
                checkAndCapture(screen, request.league + "-" + tab.toLowerCase(java.util.Locale.ROOT));
            }
            end(screen);
        }
    }

    private Activity launch(Intent intent) { return startActivitySync(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
    private void end(Activity activity) { runOnMainSync(activity::finish); waitForIdleSync(); }

    private void closeDialog(Activity activity) {
        runOnMainSync(() -> Sheet.dismissAll(activity));
        waitForIdleSync();
    }

    private void checkDialog(Activity owner, String name) throws Exception {
        waitForIdleSync();
        SystemClock.sleep(400);
        java.lang.reflect.Field windows = Sheet.class.getDeclaredField("OPEN");
        windows.setAccessible(true);
        Map<?, ?> dialogs = (Map<?, ?>)windows.get(null);
        List<String> leftovers = new ArrayList<>();
        Dialog[] current = new Dialog[1];
        runOnMainSync(() -> {
            Object open = dialogs.get(owner);
            check(open instanceof List<?> list && !list.isEmpty(), "dialog root " + name);
            List<?> list = (List<?>)open;
            current[0] = (Dialog)list.get(list.size() - 1);
            inspect(current[0].getWindow().getDecorView(), name, leftovers);
        });
        if (!leftovers.isEmpty()) untranslated.add(name + ": " + leftovers);
        capture(current[0].getWindow(), name);
    }

    private void checkAndCapture(Activity activity, String route) throws IOException {
        waitForIdleSync();
        SystemClock.sleep(320);
        List<String> leftovers = new ArrayList<>();
        runOnMainSync(() -> inspect(activity.getWindow().getDecorView(), route, leftovers));
        if (!leftovers.isEmpty()) untranslated.add(route + ": " + leftovers);
        capture(activity.getWindow(), route);
        android.util.Log.i("MyScoreEnglish", "Checked " + route);
    }

    private void inspect(View view, String route, List<String> leftovers) {
        if (!view.isShown() || !view.getGlobalVisibleRect(new Rect())) return;
        if (containsHan(view.getContentDescription())) leftovers.add("accessibility: " + view.getContentDescription());
        if (view instanceof TextView text && text.getLayout() != null && text.length() > 0) {
            if (containsHan(text.getText())) leftovers.add(text.getText().toString());
            if (text.isClickable()) {
                for (int line = 0; line < text.getLineCount(); line++) {
                    check(text.getLayout().getEllipsisCount(line) == 0, route + " clips action " + text.getText());
                }
            }
        }
        if (view instanceof ViewGroup group) for (int index = 0; index < group.getChildCount(); index++) inspect(group.getChildAt(index), route, leftovers);
    }

    private static boolean containsHan(CharSequence text) {
        return text != null && text.toString().codePoints().anyMatch(code -> Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN);
    }

    private boolean canClick(Activity activity, String label) {
        boolean[] result = new boolean[1];
        runOnMainSync(() -> result[0] = find(activity.getWindow().getDecorView(), label, false) != null);
        return result[0];
    }

    private void waitFor(Activity activity, String label, long timeout) {
        long until = SystemClock.uptimeMillis() + timeout;
        while (SystemClock.uptimeMillis() < until && !canClick(activity, label)) SystemClock.sleep(100);
        check(canClick(activity, label), "loaded " + label);
    }

    private void click(Activity activity, String label, boolean prefix) {
        runOnMainSync(() -> {
            View match = find(activity.getWindow().getDecorView(), label, prefix);
            while (match != null && !match.isClickable() && match.getParent() instanceof View parent) match = parent;
            check(match != null && match.performClick(), "clicked " + label);
        });
        waitForIdleSync();
    }

    private static View find(View view, String label, boolean prefix) {
        if (view.getVisibility() != View.VISIBLE) return null;
        if (matches(view.getContentDescription(), label, prefix) || view instanceof TextView text && matches(text.getText(), label, prefix)) return view;
        if (view instanceof ViewGroup group) for (int index = 0; index < group.getChildCount(); index++) {
            View match = find(group.getChildAt(index), label, prefix);
            if (match != null) return match;
        }
        return null;
    }

    private static boolean matches(CharSequence value, String label, boolean prefix) {
        return value != null && (label.contentEquals(value) || prefix && value.toString().startsWith(label));
    }

    private void capture(android.view.Window window, String name) throws IOException {
        Bitmap image = NativeCapture.take(this, window);
        check(image != null, "capture " + name);
        File folder = new File(getTargetContext().getExternalFilesDir(null), "english-review");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Cannot create " + folder);
        try (FileOutputStream out = new FileOutputStream(new File(folder, theme + "-" + name + ".png"))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally { image.recycle(); }
    }

    private void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
}
