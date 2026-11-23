package cn.myscore.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntConsumer;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Cs2EventActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Map<String, List<Cs2Tournament.Event>> editions = new LinkedHashMap<>();
    private final Map<String, Snapshot> cache = new LinkedHashMap<>();
    private record Snapshot(List<Cs2Tournament.Stage> stages, long fetched) {}

    private Ui ui;
    private ScrollView scroll;
    private LinearLayout body, content;
    private TextView eventPicker, source, eventDates;
    private View eventChevron, eventControl;
    private FrameLayout refresh;
    private LocalDate day;
    private Cs2Tournament.Event event;
    private List<Cs2Tournament.Event> catalog = List.of();
    private List<Cs2Tournament.Event> relatives = List.of();
    private Snapshot displayed;
    private Future<?> catalogRequest, graphRequest, relationsRequest;
    private int generation, stageIndex, groupIndex, restoreY;
    private String graphSelection = "";
    private boolean graphFailed, catalogFailed;

    @Override protected void onCreate(Bundle saved) {
        boolean dark = Ui.isDark(this);
        setTheme(dark ? R.style.AppThemeDark : R.style.AppTheme);
        super.onCreate(saved);
        ui = new Ui(this, dark);
        try { day = LocalDate.parse(getIntent().getStringExtra("day")); }
        catch (RuntimeException ignored) { day = LocalDate.now(Match.ZONE); }
        String id = saved == null ? getIntent().getStringExtra("event") : saved.getString("event");
        String name = saved == null ? getIntent().getStringExtra("name") : saved.getString("name");
        if (saved != null) {
            stageIndex = saved.getInt("stage");
            groupIndex = saved.getInt("group");
            restoreY = saved.getInt("scroll");
            graphSelection = saved.getString("graph", "");
        }
        if (validId(id)) event = new Cs2Tournament.Event(id, name == null || name.isBlank() ? "CS2 tournament" : name, "");
        build();
        loadCatalog();
        if (event != null) loadGraph();
        render(false);
    }

    private void build() {
        getWindow().setStatusBarColor(ui.bg);
        getWindow().setNavigationBarColor(ui.bg);
        getWindow().setBackgroundDrawable(new ColorDrawable(ui.bg));
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        LinearLayout root = ui.column();
        setContentView(root);
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
        root.requestApplyInsets();
        LinearLayout bar = ui.row();
        bar.setPaddingRelative(ui.dp(4), 0, ui.dp(4), 0);
        bar.addView(action("back", "Back", this::finish));
        TextView title = ui.text("Tournaments", 15, ui.muted, true);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        refresh = action("refresh", "Refresh tournament", () -> {
            if (event == null) loadCatalog();
            else loadGraph();
        });
        bar.addView(refresh);
        root.addView(bar);
        scroll = new ScrollView(this);
        FrameLayout reading = new FrameLayout(this);
        body = new LinearLayout(this) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int width = Math.min(MeasureSpec.getSize(widthSpec), ui.dp(940));
                super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightSpec);
            }
        };
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPaddingRelative(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(24));
        LinearLayout picker = ui.row();
        picker.setMinimumHeight(ui.dp(48));
        eventPicker = ui.text("Choose a tournament", 26, ui.text, true);
        eventPicker.setLetterSpacing(-.028f);
        eventPicker.setAccessibilityHeading(true);
        eventPicker.setPaddingRelative(0, ui.dp(8), ui.dp(8), ui.dp(8));
        picker.addView(eventPicker, new LinearLayout.LayoutParams(0, -2, 1));
        eventChevron = new Ui.Icon(this, "chevron", ui.muted);
        eventChevron.setRotation(90);
        picker.addView(eventChevron, new LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)));
        ui.clickable(picker, Color.TRANSPARENT, 12);
        picker.setFocusable(true);
        picker.setOnClickListener(view -> pickEvent());
        eventControl = picker;
        body.addView(picker, new LinearLayout.LayoutParams(-1, -2));
        eventDates = ui.text("", 12, ui.muted, false);
        body.addView(eventDates);
        ui.gap(body, 12);
        content = ui.column();
        body.addView(content, new LinearLayout.LayoutParams(-1, -2));
        ui.gap(body, 20);
        source = ui.text("", 12, ui.muted, false);
        source.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        body.addView(source);
        reading.addView(body, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        scroll.addView(reading, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private FrameLayout action(String icon, String label, Runnable run) {
        FrameLayout action = new FrameLayout(this);
        action.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        action.addView(new Ui.Icon(this, icon, ui.text), new FrameLayout.LayoutParams(ui.dp(20), ui.dp(20), Gravity.CENTER));
        action.setContentDescription(label);
        action.setTooltipText(label);
        ui.clickable(action, Color.TRANSPARENT, Ui.CONTROL_RADIUS);
        action.setFocusable(true);
        action.setOnClickListener(view -> run.run());
        return action;
    }

    private void loadCatalog() {
        if (catalogRequest != null) return;
        catalogFailed = false;
        catalogRequest = executor.submit(() -> {
            try {
                List<Cs2Tournament.Event> found = Cs2Tournament.catalog(day);
                main.post(() -> finishCatalog(found));
            } catch (Exception error) {
                main.post(() -> finishCatalog(null));
            }
        });
        header();
    }

    private void finishCatalog(List<Cs2Tournament.Event> found) {
        if (isDestroyed() || isFinishing()) return;
        catalogRequest = null;
        catalogFailed = found == null;
        if (found == null) { header(); return; }
        catalog = found;
        if (event != null && catalog.stream().noneMatch(entry -> entry.id().equals(event.id()))) {
            List<Cs2Tournament.Event> withCurrent = new ArrayList<>(catalog);
            withCurrent.add(0, event);
            catalog = withCurrent;
        }
        if (event == null && !catalog.isEmpty()) selectEvent(catalog.get(0));
        else if (event != null) {
            for (Cs2Tournament.Event entry : catalog) if (entry.id().equals(event.id())) { event = entry; break; }
            header();
        } else header();
    }

    private void pickEvent() {
        if (catalog.isEmpty()) { loadCatalog(); return; }
        List<Sheet.Item> options = new ArrayList<>();
        int selected = -1;
        for (Cs2Tournament.Event entry : catalog) {
            boolean same = event != null && (entry.id().equals(event.id()) || edition(entry.name()).equals(edition(event.name())));
            if (same) selected = options.size();
            options.add(new Sheet.Item(EnglishDisplay.tournament(entry.name()), displayDates(entry.dates()), same));
        }
        Sheet.choose(this, "Choose a tournament", "", options, selected, index -> selectEvent(catalog.get(index)));
    }

    private void selectEvent(Cs2Tournament.Event next) {
        if (event != null && event.id().equals(next.id())) return;
        if (graphRequest != null) graphRequest.cancel(true);
        if (relationsRequest != null) relationsRequest.cancel(true);
        graphRequest = null;
        relationsRequest = null;
        event = next;
        graphFailed = false;
        stageIndex = 0;
        groupIndex = 0;
        restoreY = 0;
        graphSelection = "";
        displayed = cache.get(event.id());
        relatives = editions.getOrDefault(edition(event.name()), List.of());
        loadGraph();
        render(true);
    }

    private void loadGraph() {
        if (event == null || graphRequest != null) return;
        graphFailed = false;
        int token = ++generation;
        Cs2Tournament.Event selected = event;
        graphRequest = executor.submit(() -> {
            try {
                List<Cs2Tournament.Stage> stages = Cs2Tournament.load(selected);
                main.post(() -> finishGraph(token, new Snapshot(stages, System.currentTimeMillis())));
            } catch (Exception error) {
                main.post(() -> finishGraph(token, null));
            }
        });
        if (relationsRequest != null) relationsRequest.cancel(true);
        relationsRequest = executor.submit(() -> {
            try {
                String endpoint = DomesticEsports.FIVE + "tournaments/" + selected.id() + "/introduction";
                List<Cs2Tournament.Event> found = related(selected, new JSONObject(Repository.get(endpoint)));
                main.post(() -> finishRelated(token, found));
            } catch (Exception error) {
                main.post(() -> finishRelated(token, List.of()));
            }
        });
        header();
    }

    private void finishGraph(int token, Snapshot result) {
        if (!current(token)) return;
        graphRequest = null;
        graphFailed = result == null;
        if (result == null) { header(); return; }
        boolean changed = displayed == null || !displayed.stages().equals(result.stages());
        displayed = result;
        cache.put(event.id(), result);
        if (changed) render(false); else header();
    }

    private void finishRelated(int token, List<Cs2Tournament.Event> found) {
        if (!current(token)) return;
        relationsRequest = null;
        if (found.isEmpty()) return;
        Map<String, Cs2Tournament.Event> unique = new LinkedHashMap<>();
        for (Cs2Tournament.Event entry : relatives) unique.put(entry.id(), entry);
        for (Cs2Tournament.Event entry : found) unique.put(entry.id(), entry);
        List<Cs2Tournament.Event> next = new ArrayList<>(unique.values());
        next.sort(Comparator.comparingInt(entry -> phase(entry.name())));
        for (Cs2Tournament.Event entry : next) if (entry.id().equals(event.id()) && !entry.dates().isBlank()) { event = entry; break; }
        if (next.equals(relatives)) return;
        relatives = next;
        editions.put(edition(event.name()), next);
        render(false);
    }

    private boolean current(int token) { return token == generation && !isFinishing() && !isDestroyed(); }

    static List<Cs2Tournament.Event> related(Cs2Tournament.Event event, JSONObject root) {
        JSONObject data = root.optJSONObject("data");
        JSONObject basic = data == null ? null : data.optJSONObject("basic");
        if (!root.optBoolean("success") || basic == null || !event.id().equals(basic.optString("tt_id"))) return List.of();
        String name = DetailParsers.text(basic, "tt_name_show", event.name());
        List<Cs2Tournament.Event> entries = new ArrayList<>();
        entries.add(new Cs2Tournament.Event(event.id(), name, dates(basic)));
        JSONArray candidates = data.optJSONArray("relative_tts");
        if (candidates == null) return entries;
        for (int index = 0; index < candidates.length(); index++) {
            JSONObject candidate = candidates.optJSONObject(index);
            if (candidate == null) continue;
            String id = DetailParsers.text(candidate, "id"), title = DetailParsers.text(candidate, "disp_name");
            if (validId(id) && !id.equals(event.id()) && !title.isBlank() && edition(name).equals(edition(title))
                && (phase(name) < 3 || phase(title) < 3)) {
                entries.add(new Cs2Tournament.Event(id, title, dates(candidate)));
            }
        }
        return entries;
    }

    private static boolean validId(String id) { return id != null && id.matches("csgo_tt_[0-9]+"); }

    private static String dates(JSONObject entry) {
        String start = DetailParsers.text(entry, "start_time");
        String end = DetailParsers.text(entry, "end_time");
        if (!start.matches("\\d{4}-\\d{2}-\\d{2}.*")) return "";
        start = start.substring(0, 10);
        return end.matches("\\d{4}-\\d{2}-\\d{2}.*") && !start.equals(end.substring(0, 10)) ? start + " — " + end.substring(0, 10) : start;
    }

    private static String edition(String title) { return title.replaceFirst("\\s*第[123一二三]阶段$", "").trim(); }

    private static int phase(String title) {
        if (title.matches(".*第[1一]阶段$")) return 1;
        if (title.matches(".*第[2二]阶段$")) return 2;
        return 3;
    }

    private static String phaseTitle(String title) {
        return switch (phase(title)) { case 1 -> "Stage 1"; case 2 -> "Stage 2"; default -> "Main event"; };
    }

    private void header() {
        String title = event == null ? "Choose a tournament" : relatives.size() > 1 ? edition(event.name()) : event.name();
        eventPicker.setText(EnglishDisplay.tournament(title));
        eventControl.setContentDescription(EnglishDisplay.tournament(title) + ", switch tournament");
        eventControl.setEnabled(catalogRequest == null);
        eventChevron.setAlpha(catalogRequest == null ? 1f : .45f);
        String dates = event == null ? "" : displayDates(event.dates());
        eventDates.setText(dates);
        eventDates.setVisibility(dates.isBlank() ? View.GONE : View.VISIBLE);
        boolean busy = graphRequest != null || (event == null && catalogRequest != null);
        refresh.setEnabled(!busy);
        refresh.setAlpha(busy ? .45f : 1f);
        source.setTextColor(graphFailed || catalogFailed && event == null ? ui.error : ui.muted);
        if (event == null) {
            source.setText(busy ? "Loading tournaments…" : catalogFailed ? "Could not load tournaments. Try refreshing." : "No tournaments from the source.");
        } else if (displayed == null) {
            source.setText(graphFailed ? "Could not load the tournament. Try refreshing." : "Loading tournament…");
        } else {
            String updated = Formats.instant(displayed.fetched(), "MMM d, HH:mm", "");
            String meta = "5EPlay · Retrieved " + updated + " (Beijing time)"
                + (graphFailed ? " · Refresh failed; showing last result" : busy ? " · Refreshing" : "");
            source.setText(meta);
        }
    }

    private void render(boolean reset) {
        header();
        int position = reset ? 0 : scroll.getScrollY();
        content.removeAllViews();
        if (relatives.size() > 1) {
            List<String> labels = new ArrayList<>();
            int selected = 0;
            for (Cs2Tournament.Event relative : relatives) {
                if (relative.id().equals(event.id())) selected = labels.size();
                labels.add(phaseTitle(relative.name()));
            }
            tabs(labels, selected, index -> selectEvent(relatives.get(index)));
        }
        if (displayed == null) return;
        List<Cs2Tournament.Stage> stages = displayed.stages();
        if (stages.isEmpty()) { message("No bracket or matchups published yet."); return; }
        stageIndex = Math.min(stageIndex, stages.size() - 1);
        Cs2Tournament.Stage stage = stages.get(stageIndex);
        List<Cs2Tournament.Group> groups = stage.groups();
        groupIndex = Math.min(groupIndex, Math.max(0, groups.size() - 1));
        if (stages.size() > 1 || groups.size() > 1) scope(stages);
        if (groups.isEmpty()) { message("No matchups published for this stage."); return; }
        Cs2Tournament.Group group = groups.get(groupIndex);
        new Cs2EventGraph(ui, this::openGame).render(content, group, graphSelection, value -> graphSelection = value);
        if (restoreY > 0) { position = restoreY; restoreY = 0; }
        int target = position;
        scroll.post(() -> scroll.scrollTo(0, target));
    }

    private void scope(List<Cs2Tournament.Stage> stages) {
        List<String> labels = new ArrayList<>();
        List<int[]> targets = new ArrayList<>();
        int selected = 0;
        for (int stage = 0; stage < stages.size(); stage++) {
            Cs2Tournament.Stage entry = stages.get(stage);
            for (int group = 0; group < Math.max(1, entry.groups().size()); group++) {
                if (stage == stageIndex && group == groupIndex) selected = labels.size();
                String title = EnglishDisplay.text(entry.label());
                if (entry.groups().size() > 1) {
                    String name = EnglishDisplay.text(entry.groups().get(group).label());
                    if (name.matches("Group [A-Z]")) title = name;
                    else if (!name.equals(title) && !name.isBlank()) title = name;
                }
                labels.add(title);
                targets.add(new int[]{stage, group});
            }
        }
        tabs(labels, selected, target -> {
            stageIndex = targets.get(target)[0];
            groupIndex = targets.get(target)[1];
            graphSelection = "";
            render(true);
        });
    }

    private static String displayDates(String source) {
        String[] edges = source.split(" — ");
        java.time.format.DateTimeFormatter format = java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.ENGLISH);
        try {
            LocalDate start = LocalDate.parse(edges[0]);
            if (edges.length == 1) return format.format(start);
            LocalDate end = LocalDate.parse(edges[1]);
            if (start.getYear() != end.getYear()) return source;
            return format.format(start) + "–" + (start.getMonth() == end.getMonth() ? end.getDayOfMonth() : format.format(end));
        } catch (RuntimeException error) { return source; }
    }

    private void tabs(List<String> labels, int current, IntConsumer selection) {
        HorizontalScrollView bar = new HorizontalScrollView(this);
        bar.setHorizontalScrollBarEnabled(false);
        LinearLayout row = ui.row();
        for (int index = 0; index < labels.size(); index++) {
            int item = index;
            TextView tab = ui.tab(labels.get(index), item == current, () -> { if (item != current) selection.accept(item); });
            row.addView(tab, new LinearLayout.LayoutParams(-2, -2));
            if (item == current) tab.post(() -> bar.scrollTo(Math.max(0, tab.getRight() - bar.getWidth()), 0));
        }
        bar.addView(row, new HorizontalScrollView.LayoutParams(-2, -2));
        content.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        ui.gap(content, 8);
    }

    private void message(String label) { content.addView(ui.text(label, 12, ui.muted, false)); }

    private void openGame(Cs2Tournament.Game game) {
        String id = game.id().startsWith("five:cs2:") ? game.id() : "five:cs2:" + game.id();
        Match match = new Match();
        match.id = id;
        match.league = "cs2";
        match.group = "CS2";
        match.source = "5EPlay";
        match.event = event.name();
        match.eventId = event.id();
        match.home = game.first().name();
        match.away = game.second().name();
        match.homeLogo = game.first().logo();
        match.awayLogo = game.second().logo();
        match.homeScore = game.first().score();
        match.awayScore = game.second().score();
        match.status = game.status();
        match.state = switch (game.status()) {
            case "已结束", "完赛" -> "final";
            case "进行中" -> "live";
            case "未开始", "待开赛", "即将开始" -> "scheduled";
            default -> "unknown";
        };
        startActivity(new Intent(this, DetailActivity.class).putExtra("match", match.json().toString()));
    }

    @Override protected void onSaveInstanceState(Bundle saved) {
        super.onSaveInstanceState(saved);
        if (event != null) {
            saved.putString("event", event.id());
            saved.putString("name", event.name());
        }
        saved.putInt("stage", stageIndex);
        saved.putInt("group", groupIndex);
        saved.putInt("scroll", scroll.getScrollY());
        saved.putString("graph", graphSelection);
    }

    @Override protected void onDestroy() {
        generation++;
        executor.shutdownNow();
        main.removeCallbacksAndMessages(null);
        Sheet.dismissAll(this);
        super.onDestroy();
    }
}
