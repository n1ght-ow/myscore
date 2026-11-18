package cn.myscore.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Targeted native smoke for national-team schedules reached from source-match crests. A live ESPN
 * read is opt-in with {@code -e network true}; a successful empty schedule is valid because
 * fixtures are not assumed.
 */
public final class NationalTeamsInstrumentation extends Instrumentation {
    private static final String NORWAY_MATCH_ID = "espn:national:401861194:401861194";
    private int checks;
    private boolean network;
    private String matchDate = "";
    private Activity main;
    private Context context;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        network = args != null && "true".equals(args.getString("network"));
        String value = args == null ? null : args.getString("matchDate");
        matchDate = value == null ? "" : value.trim();
        start();
    }

    @Override public void runOnMainSync(Runnable action) {
        Throwable[] failure = {null};
        super.runOnMainSync(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure[0] = error;
            }
        });
        if (failure[0] instanceof Error error) throw error;
        if (failure[0] != null) throw new RuntimeException(failure[0]);
    }

    @Override public void onStart() {
        Bundle report = new Bundle();
        int outcome = Activity.RESULT_OK;
        StringBuilder liveSummary = new StringBuilder();
        List<String> liveFailures = new ArrayList<>();
        try {
            LocalDate requested = null;
            if (!matchDate.isEmpty()) {
                try {
                    requested = LocalDate.parse(matchDate);
                } catch (RuntimeException error) {
                    throw new AssertionError("matchDate must be yyyy-MM-dd: " + matchDate);
                }
                check(network, "matchDate requires network=true");
            }
            context = getTargetContext();
            main = startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            waitForIdleSync();
            waitForMainText("N1ghtScore", 15_000);
            View root = main.getWindow().getDecorView();
            check(findText(root, "N1ghtScore") != null || findText(root, "赛程") != null, "score list opens");
            check(findFilter(root, "LOL") == null, "obsolete LOL sport filter is absent");

            clickFilter("足球");
            View football = main.getWindow().getDecorView();
            check(findFilter(football, "国家队") == null, "national-team chooser is absent");

            if (requested != null) {
                List<Match> matches = new Repository(context)
                    .fetch(requested, requested, "足球", true).matches();
                Match target = null;
                int found = 0;
                for (Match match : matches) {
                    if (NORWAY_MATCH_ID.equals(match.id)) {
                        target = match;
                        found++;
                    }
                }
                check(found == 1, requested + " football date has exactly one Norway match 401861194; found " + found);
                boolean home = target != null && "464".equals(target.homeId);
                check(target != null && (home || "464".equals(target.awayId)), "Norway match keeps its identity");
                liveSummary.append("matchDate ").append(requested)
                    .append(": Norway 401861194 x").append(found).append('\n');
                openCrest(target, home);
            }
            if (network) {
                for (NationalTeams.Entry entry : NationalTeams.FOLLOWED) {
                    liveSummary.append(live(entry, liveFailures));
                }
                check(liveFailures.isEmpty(), "all FOLLOWED national schedules verified: " + String.join("; ", liveFailures));
            }
            report.putString("sources", liveSummary.toString());
            report.putString("stream", "\nPASS: " + checks + " national-team checks.\n"
                + (network ? liveSummary : "Live ESPN check skipped; rerun with -e network true.\n"));
        } catch (Throwable error) {
            outcome = Activity.RESULT_CANCELED;
            report.putString("sources", liveSummary.toString());
            report.putString("stream", "\nFAIL after " + checks + ": " + android.util.Log.getStackTraceString(error) + "\n" + liveSummary);
        }
        finish(outcome, report);
    }

    private void openCrest(Match match, boolean home) throws Exception {
        Ui ui = new Ui(context, false);
        ScoreAdapter adapter = new ScoreAdapter(ui, (ScoreAdapter.Host)main);
        runOnMainSync(() -> adapter.updateRows(List.of(ScoreRow.match(match))));
        ActivityMonitor monitor = addMonitor(TeamActivity.class.getName(), null, false);
        boolean[] clicked = {false};
        runOnMainSync(() -> {
            View row = adapter.getView(0, null, null);
            int width = ui.dp(328);
            row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            row.layout(0, 0, width, row.getMeasuredHeight());
            String name = home ? match.home : match.away;
            View crest = findDescription(row, "查看 " + name + " 的赛程");
            check(crest != null, "Norway crest appears on the match card");
            clicked[0] = crest.performClick();
        });
        Activity page = waitForMonitorWithTimeout(monitor, 10_000);
        removeMonitor(monitor);
        check(clicked[0], "Norway crest clicks");
        check(page != null, "Norway crest opens a team page");
        waitForIdleSync();
        waitForPageText(page, "球队赛程", 8_000);
        waitForPageText(page, "挪威", 8_000);
        Team team = team(page);
        check(team != null, "team page receives a team identity");
        check(team.league.equals(NationalTeams.LEAGUE) && team.id.equals("464") && team.name.equals("挪威"),
            "team page identity is Norway's national team");
        check(!page.isFinishing(), "team page stays open");
        sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        waitForIdleSync();
    }

    private String live(NationalTeams.Entry entry, List<String> failures) {
        try {
            Team team = entry.team();
            check(team.usable(), "FOLLOWED entry is routable: " + entry.name());
            check(team.logo.equals("https://a.espncdn.com/i/teamlogos/countries/500/" + entry.flag() + ".png"),
                "FOLLOWED crest is source-backed: " + entry.name());
            TeamRepository.Result result = fetch(team);
            check(result.error.isEmpty(), "live ESPN schedule loads for " + entry.name() + ": " + result.error);
            check(result.fetched > 0, "live ESPN schedule reports a fetch time for " + entry.name());
            check(result.source.contains("ESPN"), "live schedule names ESPN for " + entry.name() + ": " + result.source);
            check(result.matches.stream().allMatch(match -> team.id.equals(match.homeId) || team.id.equals(match.awayId)),
                "live schedule keeps " + entry.name() + " identity");
            return entry.name() + ": " + result.source + " · " + result.matches.size() + " matches\n";
        } catch (Throwable error) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            failures.add(entry.name() + ": " + message);
            return entry.name() + ": FAIL " + message + "\n";
        }
    }

    private TeamRepository.Result fetch(Team team) throws Exception {
        TeamRepository repository = new TeamRepository(context);
        TeamRepository.Result result = repository.fetch(team, true, false);
        for (int attempt = 0; attempt < 2 && !result.error.isEmpty(); attempt++) {
            Thread.sleep(1200);
            result = repository.fetch(team, true, false);
        }
        return result;
    }

    private void clickFilter(String group) {
        runOnMainSync(() -> {
            View target = findFilter(main.getWindow().getDecorView(), group);
            if (target == null) target = clickable(findText(main.getWindow().getDecorView(), group));
            check(target != null, "sport filter exists: " + group);
            check(target.performClick(), "sport filter works: " + group);
        });
        waitForIdleSync();
    }

    private Team team(Activity page) throws Exception {
        Field field = TeamActivity.class.getDeclaredField("team");
        field.setAccessible(true);
        Team[] value = {null};
        runOnMainSync(() -> {
            try {
                value[0] = (Team)field.get(page);
            } catch (IllegalAccessException error) {
                throw new AssertionError(error);
            }
        });
        return value[0];
    }

    private void waitForMainText(String text, long timeout) throws Exception {
        waitForText(main, text, timeout);
    }

    private void waitForPageText(Activity page, String text, long timeout) throws Exception {
        waitForText(page, text, timeout);
    }

    private void waitForText(Activity page, String text, long timeout) throws Exception {
        long until = SystemClock.uptimeMillis() + timeout;
        do {
            boolean[] ready = {false};
            runOnMainSync(() -> ready[0] = findText(page.getWindow().getDecorView(), text) != null);
            if (ready[0]) return;
            Thread.sleep(50);
            waitForIdleSync();
        } while (SystemClock.uptimeMillis() < until);
        throw new AssertionError("text did not appear: " + text);
    }

    private static View findFilter(View root, String group) {
        CharSequence description = root.getContentDescription();
        if (root.isClickable() && description != null && description.toString().startsWith(group + "，")) return root;
        if (root instanceof TextView text && root.isClickable() && group.contentEquals(text.getText())) return root;
        if (root instanceof ViewGroup parent) {
            for (int i = 0; i < parent.getChildCount(); i++) {
                View found = findFilter(parent.getChildAt(i), group);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View findText(View root, String text) {
        if (root instanceof TextView view && text.contentEquals(view.getText())) return root;
        if (root instanceof ViewGroup parent) {
            for (int i = 0; i < parent.getChildCount(); i++) {
                View found = findText(parent.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View findDescription(View root, String text) {
        CharSequence description = root.getContentDescription();
        if (description != null && text.contentEquals(description)) return root;
        if (root instanceof ViewGroup parent) {
            for (int i = 0; i < parent.getChildCount(); i++) {
                View found = findDescription(parent.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View clickable(View view) {
        while (view != null && !view.isClickable()) view = view.getParent() instanceof View ? (View)view.getParent() : null;
        return view;
    }

    private void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks++;
        android.util.Log.i("MyScoreNationalTeams", "PASS " + description);
    }
}
