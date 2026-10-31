package cn.myscore.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Proxy;
import java.util.List;

/** State transitions use detached views only; never writes calendar events or match caches. */
public final class SubscriptionVisibilityInstrumentation extends Instrumentation {
    private int checks;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle report = new Bundle();
        int result = Activity.RESULT_OK;
        Throwable[] failure = new Throwable[1];
        runOnMainSync(() -> {
            try {
                Ui ui = new Ui(getTargetContext(), true);
                for (boolean subscribed : new boolean[]{false, true}) {
                    for (String league : new String[]{"cs2", "nfl", "f1"}) {
                        ScoreAdapter phone = new ScoreAdapter(ui, host(ScoreAdapter.Host.class, subscribed));
                        PadScoreAdapter pad = new PadScoreAdapter(ui, host(PadScoreAdapter.Host.class, subscribed));
                        PadScoreAdapter ledger = new PadScoreAdapter(ui, host(PadScoreAdapter.Host.class, subscribed), true);
                        TeamScheduleAdapter team = new TeamScheduleAdapter(ui, host(TeamScheduleAdapter.Host.class, subscribed));
                        View[] recycled = new View[4];
                        for (String state : new String[]{"scheduled", "live", "final", "scheduled", "past-start", "unknown-time", "cancelled"}) {
                            Match m = new Match(); m.id = "in-memory-subscription"; m.league = league;
                            m.home = "Home"; m.away = "Away"; m.homeId = "home"; m.awayId = "away";
                            m.state = state.equals("past-start") || state.equals("unknown-time") ? "scheduled" : state;
                            m.timeConfirmed = !state.equals("unknown-time");
                            m.start = System.currentTimeMillis() + (state.equals("past-start") ? -60_000 : 3_600_000);
                            phone.updateRows(List.of(ScoreRow.match(m))); pad.updateRows(List.of(ScoreRow.match(m)));
                            ledger.updateRows(List.of(ScoreRow.match(m)));
                            Team t = new Team(); t.id = "home"; t.name = "Home";
                            team.updateRows(List.of(TeamScheduleRow.match(m, t)));
                            recycled[0] = phone.getView(0, recycled[0], null);
                            recycled[1] = pad.getView(0, recycled[1], null);
                            recycled[2] = ledger.getView(0, recycled[2], null);
                            recycled[3] = team.getView(0, recycled[3], null);
                            for (View row : recycled) {
                                View action = calendar(row);
                                require(action != null, "calendar view exists for recycling");
                                require(action.getVisibility() == (state.equals("scheduled") ? View.VISIBLE : View.GONE),
                                    league + " " + state + " subscribed=" + subscribed + " visibility");
                                View icon = ((ViewGroup) action).getChildAt(0);
                                require(icon instanceof Ui.Icon, "calendar icon child");
                                int expected = subscribed ? ui.primary : ui.muted;
                                require(((Ui.Icon) icon).color() == expected,
                                    league + " " + state + " subscribed=" + subscribed + " unsubscribed color");
                            }
                        }
                    }
                }
            } catch (Throwable e) { failure[0] = e; }
        });
        if (failure[0] == null) {
            checks += AlignmentChecks.run(this, getTargetContext());
            report.putString("stream", "PASS " + checks + " subscription visibility/alignment checks.\n");
        } else {
            result = Activity.RESULT_CANCELED;
            report.putString("stream", "FAIL after " + checks + ": " + android.util.Log.getStackTraceString(failure[0]));
        }
        finish(result, report);
    }
    private <T> T host(Class<T> type, boolean subscribed) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getName().equals("isSubscribed")) return subscribed;
            if (method.getName().equals("isBusy")) return false;
            if (method.getName().equals("watchLink")) return WatchLinks.of((Match) args[0]);
            return null;
        }));
    }
    private View calendar(View view) {
        CharSequence description = view.getContentDescription();
        if (description != null && description.toString().contains("订阅")) return view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            View found = calendar(group.getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
    private void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks++;
    }
}
