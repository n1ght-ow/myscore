package cn.myscore.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

public final class StandingsActivity extends Activity {
    private static final String[] SPORTS = {"epl", "ucl", "fa", "efl", "nba", "nfl", "f1", "cs2"};
    private static final List<String> LABELS = List.of(
        "Premier League", "Champions League", "FA Cup", "Carabao Cup", "NBA", "NFL", "F1", "CS2");
    private static final int CS2_PAGE_SIZE = 30;
    private static final long FRESH_MS = 10 * 60_000L;
    private static final Map<String, Snapshot> CACHE = new ConcurrentHashMap<>();
    private record Snapshot(Standings data, long fetched) {}

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newFixedThreadPool(2);
    private final Map<String, TextView> sportTabs = new LinkedHashMap<>();
    private Bundle selections = new Bundle();
    private Ui ui;
    private StandingsBoard board;
    private LinearLayout shell, content;
    private TextView info;
    private FrameLayout refresh;
    private ScrollView scroll;
    private String selected;
    private Snapshot shown;
    private Future<?> request;
    private int generation;
    private int restoredPosition;
    private boolean failed, wide;

    @Override protected void onCreate(Bundle saved) {
        boolean dark = Ui.isDark(this);
        setTheme(dark ? R.style.AppThemeDark : R.style.AppTheme);
        super.onCreate(saved);
        ui = new Ui(this, dark);
        board = new StandingsBoard(ui);
        String initial = initialSport(getIntent().getStringExtra("group"));
        if (saved != null) {
            initial = saved.getString("sport", initial);
            Bundle savedScopes = saved.getBundle("scopes");
            if (savedScopes != null) selections = savedScopes;
            restoredPosition = saved.getInt("scroll");
        }
        build();
        selectSport(sportTabs.containsKey(initial) ? initial : SPORTS[0], false);
    }

    private static String initialSport(String group) {
        if ("NBA".equals(group)) return "nba";
        if ("NFL".equals(group)) return "nfl";
        if ("F1".equals(group)) return "f1";
        if ("CS2".equals(group)) return "cs2";
        return "epl";
    }

    private void build() {
        getWindow().setStatusBarColor(ui.bg);
        getWindow().setNavigationBarColor(ui.bg);
        getWindow().setBackgroundDrawable(new ColorDrawable(ui.bg));
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        FrameLayout root = new FrameLayout(this);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        shell = new LinearLayout(this) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int width = Math.min(MeasureSpec.getSize(widthSpec), ui.dp("f1".equals(selected) ? 824 : comparison() ? 944 : 560));
                super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightSpec);
            }
        };
        shell.setOrientation(LinearLayout.VERTICAL);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        setContentView(root);
        root.requestApplyInsets();

        LinearLayout bar = ui.row();
        bar.setPaddingRelative(ui.dp(4), 0, ui.dp(4), 0);
        bar.addView(action("back", "Back", this::finish));
        TextView title = ui.text("Standings", 17, ui.text, true);
        title.setGravity(Gravity.CENTER);
        title.setAccessibilityHeading(true);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        refresh = action("refresh", "Refresh standings", this::load);
        bar.addView(refresh);
        shell.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        HorizontalScrollView sports = tabs(LABELS, -1, index -> selectSport(SPORTS[index], true));
        LinearLayout menu = (LinearLayout) sports.getChildAt(0);
        for (int index = 0; index < SPORTS.length; index++) sportTabs.put(SPORTS[index], (TextView)menu.getChildAt(index));
        shell.addView(sports, new LinearLayout.LayoutParams(-1, -2));
        shell.addView(ui.divider());

        scroll = new ScrollView(this);
        LinearLayout reading = ui.column();
        reading.setPaddingRelative(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(24));
        info = ui.text("", 12, ui.muted, false);
        info.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content = ui.column();
        reading.addView(content, new LinearLayout.LayoutParams(-1, -2));
        scroll.addView(reading, new ScrollView.LayoutParams(-1, -2));
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        content.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            boolean next = comparison() && right - left >= ui.dp(740 * Math.max(1, getResources().getConfiguration().fontScale));
            if (next != wide) {
                wide = next;
                main.post(() -> { if (!isDestroyed()) render(false); });
            }
        });
    }

    private FrameLayout action(String icon, String label, Runnable listener) {
        FrameLayout button = new FrameLayout(this);
        button.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        button.addView(new Ui.Icon(this, icon, ui.text), new FrameLayout.LayoutParams(ui.dp(20), ui.dp(20), Gravity.CENTER));
        ui.clickable(button, Color.TRANSPARENT, 24);
        button.setFocusable(true);
        button.setContentDescription(label);
        button.setTooltipText(label);
        button.setOnClickListener(view -> listener.run());
        return button;
    }

    private HorizontalScrollView tabs(List<String> labels, int selectedIndex, IntConsumer select) {
        HorizontalScrollView strip = new HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        LinearLayout row = ui.row();
        for (int index = 0; index < labels.size(); index++) {
            int item = index;
            TextView tab = ui.tab(labels.get(index), index == selectedIndex, () -> select.accept(item));
            row.addView(tab, new LinearLayout.LayoutParams(-2, -2));
            if (index == selectedIndex) reveal(tab);
        }
        strip.addView(row, new HorizontalScrollView.LayoutParams(-2, -2));
        return strip;
    }

    private void reveal(View view) {
        view.post(() -> {
            Rect bounds = new Rect();
            view.getDrawingRect(bounds);
            view.requestRectangleOnScreen(bounds, true);
        });
    }

    private boolean comparison() { return "nba".equals(selected) || "nfl".equals(selected) || "f1".equals(selected); }

    private void selectSport(String sport, boolean resetScroll) {
        if (sport.equals(selected)) return;
        cancelRequest();
        selected = sport;
        shown = CACHE.get(sport);
        failed = false;
        for (Map.Entry<String, TextView> entry : sportTabs.entrySet()) ui.selectTab(entry.getValue(), entry.getKey().equals(sport));
        reveal(sportTabs.get(sport));
        shell.requestLayout();
        if (shown == null || System.currentTimeMillis() - shown.fetched() >= FRESH_MS) load();
        render(resetScroll);
    }

    private void cancelRequest() {
        generation++;
        if (request != null) request.cancel(true);
        request = null;
    }

    private void load() {
        if (request != null) return;
        failed = false;
        Competition sport = Competition.find(selected);
        int token = ++generation;
        request = worker.submit(() -> {
            try {
                Standings data = switch (sport.id) {
                    case "fa", "efl" -> CupStandings.load(sport);
                    case "f1" -> F1Standings.load();
                    case "cs2" -> Cs2Standings.load();
                    default -> LeagueStandings.load(sport);
                };
                main.post(() -> complete(token, new Snapshot(data, System.currentTimeMillis())));
            } catch (Exception error) {
                main.post(() -> complete(token, null));
            }
        });
        updateInfo();
    }

    private void complete(int token, Snapshot received) {
        if (isDestroyed() || isFinishing() || token != generation) return;
        request = null;
        failed = received == null;
        if (failed) { updateInfo(); return; }
        Standings previous = shown == null ? null : shown.data();
        shown = received;
        CACHE.put(selected, received);
        if (received.data().equals(previous)) updateInfo();
        else render(false);
    }

    private void updateInfo() {
        boolean busy = request != null;
        refresh.setEnabled(!busy);
        refresh.setAlpha(busy ? 0.45f : 1f);
        info.setTextColor(failed ? ui.error : ui.muted);
        if (shown == null) {
            info.setText(failed ? "Unable to load standings. Refresh to try again." : "Loading standings...");
            return;
        }
        List<String> details = new ArrayList<>();
        String season = StandingsEnglish.text(shown.data().season());
        String source = StandingsEnglish.text(shown.data().source());
        String retrieved = StandingsEnglish.retrieved(shown.fetched());
        if (!season.isBlank()) details.add(season);
        if (!source.isBlank()) details.add(source);
        if (!retrieved.isBlank()) details.add("Retrieved " + retrieved);
        if (busy) details.add("Updating");
        if (failed) details.add("Refresh failed. Showing previous data.");
        info.setText(String.join(" · ", details));
    }

    private void render(boolean resetScroll) {
        int position = resetScroll ? 0 : scroll.getScrollY();
        updateInfo();
        content.removeAllViews();
        if (resetScroll) restoredPosition = 0;
        if (shown == null) {
            addStatus();
            return;
        }
        Standings data = shown.data();
        if (selected.equals("fa") || selected.equals("efl")) renderCup(data.rounds());
        else if (selected.equals("cs2")) renderCs2(data.sections());
        else renderRankings(StandingsBoard.panels(selected, data.sections()));
        if (restoredPosition > 0) {
            position = restoredPosition;
            restoredPosition = 0;
        }
        int target = position;
        int token = generation;
        scroll.post(() -> { if (token == generation) scroll.scrollTo(0, target); });
    }

    private void renderCup(List<Standings.Round> rounds) {
        if (rounds.isEmpty()) {
            addStatus();
            empty("No cup rounds published yet.");
            return;
        }
        List<String> labels = rounds.stream().map(Standings.Round::label).collect(Collectors.toList());
        int index = choose(labels, initialRound(rounds));
        if (rounds.size() > 1) {
            addScopes(labels, labels.stream().map(StandingsEnglish::text).collect(Collectors.toList()), index);
        }
        addStatus();
        Standings.Round round = rounds.get(index);
        if (rounds.size() == 1 && !round.label().isBlank()) {
            TextView heading = ui.text(StandingsEnglish.text(round.label()), 16, ui.text, true);
            heading.setAccessibilityHeading(true);
            content.addView(heading);
            ui.gap(content, 8);
        }
        if (round.ties().isEmpty()) empty("No matches published for this round.");
        else content.addView(board.round(round), new LinearLayout.LayoutParams(-1, -2));
    }

    static int initialRound(List<Standings.Round> rounds) {
        for (int index = rounds.size() - 1; index >= 0; index--) if (rounds.get(index).started()) return index;
        return 0;
    }

    private void renderCs2(List<Standings.Section> sections) {
        if (sections.isEmpty()) {
            addStatus();
            empty("No standings published yet.");
            return;
        }
        List<String> labels = sections.stream().map(Standings.Section::title).collect(Collectors.toList());
        int index = choose(labels, 0);
        if (sections.size() > 1) {
            addScopes(labels, labels.stream().map(StandingsEnglish::text).collect(Collectors.toList()), index);
        }
        addStatus();
        Standings.Section region = sections.get(index);
        int pages = Math.max(1, (region.rows().size() + CS2_PAGE_SIZE - 1) / CS2_PAGE_SIZE);
        String pageKey = "cs2.page." + region.title();
        int page = Math.min(selections.getInt(pageKey), pages - 1);
        int start = page * CS2_PAGE_SIZE;
        List<Standings.Row> rows = region.rows().subList(start, Math.min(start + CS2_PAGE_SIZE, region.rows().size()));
        Standings.Section visible = new Standings.Section(region.title(), region.columns(), rows);
        content.addView(board.table(visible, selected, "Team", "#", "Rank", sections), new LinearLayout.LayoutParams(-1, -2));
        if (pages == 1) return;

        ui.gap(content, 8);
        LinearLayout pager = ui.row();
        FrameLayout previous = action("chevron", "Previous page", () -> selectCs2Page(pageKey, page - 1));
        previous.getChildAt(0).setRotation(180);
        FrameLayout next = action("chevron", "Next page", () -> selectCs2Page(pageKey, page + 1));
        previous.setVisibility(page == 0 ? View.INVISIBLE : View.VISIBLE);
        next.setVisibility(page + 1 == pages ? View.INVISIBLE : View.VISIBLE);
        pager.addView(previous);
        TextView label = ui.text("Page " + (page + 1) + " / " + pages, 12, ui.muted, false);
        label.setGravity(Gravity.CENTER);
        pager.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        pager.addView(next);
        content.addView(pager, new LinearLayout.LayoutParams(-1, -2));
    }

    private void selectCs2Page(String key, int page) {
        selections.putInt(key, page);
        render(true);
    }

    private void renderRankings(List<StandingsBoard.Panel> panels) {
        if (panels.isEmpty()) {
            addStatus();
            empty("No standings published yet.");
            return;
        }
        List<Standings.Section> peers = panels.stream().flatMap(panel -> panel.sections().stream()).collect(Collectors.toList());
        if (wide && panels.size() == 2) {
            addStatus();
            LinearLayout left = ui.column(), right = ui.column();
            putPanel(left, panels.get(0), true, peers);
            putPanel(right, panels.get(1), true, peers);
            content.addView(ui.columns(left, right, 358, 24), new LinearLayout.LayoutParams(-1, -2));
        } else {
            List<String> labels = panels.stream().map(this::panelKey).collect(Collectors.toList());
            int index = choose(labels, 0);
            if (panels.size() > 1) {
                addScopes(labels, panels.stream().map(this::panelLabel).collect(Collectors.toList()), index);
            }
            addStatus();
            StandingsBoard.Panel panel = panels.get(index);
            if (selected.equals("f1") && panels.size() == 1) {
                String missing = panel.title().contains("车手") ? "Constructors" : "Drivers";
                empty("The source has not published " + missing + " standings.");
                ui.gap(content, 12);
            }
            putPanel(content, panel, panels.size() == 1 && !panelLabel(panel).equals("Standings"), peers);
        }
    }

    private String panelKey(StandingsBoard.Panel panel) {
        if (selected.equals("f1") && (panel.title().equals("车手") || panel.title().equals("车队"))) return panel.title() + "积分";
        return panel.title();
    }

    private String panelLabel(StandingsBoard.Panel panel) {
        return StandingsEnglish.text(panelKey(panel));
    }

    private void putPanel(LinearLayout into, StandingsBoard.Panel panel, boolean showTitle, List<Standings.Section> peers) {
        String identity = selected.equals("f1") ? panel.title().contains("车手") ? "Driver" : "Constructor" : "Team";
        board.panel(into, panel, panelLabel(panel), selected, identity, rankLabel(), rankDescription(), showTitle, peers);
    }

    private int choose(List<String> labels, int fallback) {
        int index = labels.indexOf(selections.getString(selected, ""));
        return index < 0 ? fallback : index;
    }

    private void addScopes(List<String> keys, List<String> labels, int index) {
        content.addView(tabs(labels, index, chosen -> {
            if (index == chosen) return;
            selections.putString(selected, keys.get(chosen));
            render(selected.equals("cs2"));
        }), new LinearLayout.LayoutParams(-1, -2));
        ui.gap(content, 8);
    }

    private void addStatus() {
        content.addView(info, new LinearLayout.LayoutParams(-1, -2));
        ui.gap(content, 8);
    }

    private String rankLabel() {
        return selected.equals("nfl") ? "Rank" : "#";
    }

    private String rankDescription() {
        return selected.equals("nfl") ? "Conference rank" : "Rank";
    }

    private void empty(String message) { content.addView(ui.text(message, 14, ui.muted, false)); }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("sport", selected);
        state.putBundle("scopes", selections);
        state.putInt("scroll", scroll.getScrollY());
    }

    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) Ui.highRefresh(getWindow());
    }

    @Override protected void onDestroy() {
        cancelRequest();
        worker.shutdownNow();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
