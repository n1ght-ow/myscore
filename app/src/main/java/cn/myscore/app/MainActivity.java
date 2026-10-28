/** Score list, tablet workspace, navigation rail and settings. */

package cn.myscore.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import cn.myscore.app.BuildConfig;
import cn.myscore.app.CalendarJob;
import cn.myscore.app.CalendarSync;
import cn.myscore.app.Competition;
import cn.myscore.app.DetailActivity;
import cn.myscore.app.DetailParsers;
import cn.myscore.app.DetailRepository;
import cn.myscore.app.Logos;
import cn.myscore.app.Match;
import cn.myscore.app.MatchDetail;
import cn.myscore.app.PadScoreAdapter;
import cn.myscore.app.R;
import cn.myscore.app.RefreshPolicy;
import cn.myscore.app.Repository;
import cn.myscore.app.Rules;
import cn.myscore.app.ScoreAdapter;
import cn.myscore.app.ScoreList;
import cn.myscore.app.ScoreRow;
import cn.myscore.app.Sheet;
import cn.myscore.app.Team;
import cn.myscore.app.TeamActivity;
import cn.myscore.app.Ui;
import cn.myscore.app.UpdateActivity;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class MainActivity
extends Activity
implements ScoreAdapter.Host {
    private Ui ui;
    private SharedPreferences prefs;
    private Repository repository;
    private Repository prefetchRepository;
    private Repository.Snapshot snapshot = new Repository.Snapshot();
    private Repository.Snapshot allSnapshot;
    private LocalDate allSnapshotDay;
    // A day the user already opened is answered from memory, even after switching away.
    private final LinkedHashMap<String,Repository.Snapshot> daySnapshots = new LinkedHashMap<String,Repository.Snapshot>(8,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,Repository.Snapshot> eldest) {return size() > 8;}
    };
    private final Map<String,Long> prefetchedAt = new HashMap<String,Long>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int pendingScorePosition = -1;
    private int pendingPadPosition = -1;
    private int pendingPadFocusScroll = -1;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final ExecutorService cacheWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService prefetchWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService calendarWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService focusWorker = Executors.newFixedThreadPool(2);
    private LinearLayout root;
    private LinearLayout body;
    private LinearLayout nav;
    private LinearLayout headerView;
    private LinearLayout filterTabs;
    private LinearLayout dateSelectorView;
    private LocalDate headerDay;
    private FrameLayout stage;
    private ScrollView scroll;
    private ScoreList scoreList;
    private ScoreAdapter scoreAdapter;
    private int tab = 0;
    private int dayOffset = 0;
    private int headerHeight = 0;
    private int headerOffset = 0;
    private String group = "全部";
    private boolean active = false;
    private boolean busy = false;
    private boolean calendarBusy = false;
    private boolean calendarQuerying = false;
    private boolean pending = false;
    private boolean cacheReady = false;
    private boolean pendingForce;
    private boolean selectionLoading;
    private long selectionVersion;
    private Future<?> scoreRequest;
    private Future<?> selectionCacheRequest;
    private LocalDate loadedDay;
    private CalendarSync calendarSync;
    private final Set<String> subscribed = new HashSet<String>();
    private Match pendingSubscription;
    private long lastScoreScroll;
    private String renderedScores = "";
    private String renderedSettings = "";
    private String renderedPadFocus = "";
    private final Runnable tick = () -> this.load(false);
    private final Runnable settleLoad = () -> {
        if (this.active && !this.isDestroyed()) {
            this.load(false);
        }
    };
    private Runnable prefetchTask;
    private String prefetchTaskKey;
    private Future<?> prefetchRequest;
    private final Runnable renderSoon = () -> {
        if (this.active && !this.isDestroyed()) {
            this.render(true);
        }
    };
    private static final String[] GROUPS = new String[]{"全部", "足球", "NBA", "NFL", "F1", "CS2", "LOL"};
    private boolean pad;
    private LinearLayout padRail;
    private ScoreList padList;
    private PadScoreAdapter padAdapter;
    private Ui.ReadingScroll padFocusScroll;
    private LinearLayout padFocusHost;
    private TextView padDayNumber;
    private TextView padDayMeta;
    private TextView padListStatus;
    private TextView padRailDay;
    private TextView padRailWeek;
    private final Map<String, TextView> railCounts = new LinkedHashMap<String, TextView>();
    private final Map<String, View> railDots = new LinkedHashMap<String, View>();
    private final Map<String, View> railFilters = new LinkedHashMap<>();
    private final Map<String, TextView> railLabels = new LinkedHashMap<>();
    private final Map<String, View> railBars = new LinkedHashMap<>();
    private DetailRepository detailRepository;
    private WatchRepository watches;
    private WatchLauncher watchLauncher;
    private Match padSelected;
    private String padSelectedId = "";
    private MatchDetail padDetail;
    private String padError = "";
    private boolean padFocusBusy;
    private long padFocusVersion;
    private Future<?> padFocusRequest;
    private Repository.Snapshot padSettingSources;
    private LocalDate padSettingSourcesDay;
    private boolean padSettingSourcesLoading;

    public void onCreate(Bundle state) {
        this.prefs = this.getSharedPreferences("settings", 0);
        boolean dark = Ui.isDark((Context)this);
        this.setTheme(dark ? R.style.AppThemeDark : R.style.AppTheme);
        super.onCreate(state);
        this.ui = new Ui((Context)this, dark);
        this.repository = new Repository((Context)this);
        // Prefetch owns a separate failure map so a quiet background miss never shows up as a
        // source error on the day the user later opens.
        this.prefetchRepository = new Repository((Context)this);
        this.calendarSync = new CalendarSync((Context)this);
        this.detailRepository = new DetailRepository((Context)this);
        this.watches = WatchRepository.get(this);
        this.watchLauncher = new WatchLauncher(this, this.watches);
        this.pad = Ui.isPad((Context)this);
        if (state != null) {
            this.tab = state.getInt("tab");
            if (state.getInt("navigation_schema", 0) < 2) {
                if (this.tab == 2) {
                    this.tab = 1;
                } else if (this.tab == 1) {
                    this.tab = 0;
                }
            }
            this.dayOffset = state.getInt("offset");
            this.group = state.getString("group", "全部");
            this.padSelectedId = state.getString("pad_selected", "");
            this.pendingScorePosition = state.getInt("score_position", -1);
            this.pendingPadPosition = state.getInt("pad_position", -1);
            this.pendingPadFocusScroll = state.getInt("pad_focus_scroll", -1);
            String pending = state.getString("pending_subscription", "");
            if (!pending.isEmpty()) {
                try {
                    this.pendingSubscription = Match.fromJson(new JSONObject(pending));
                } catch (Exception ignored) {
                    this.pendingSubscription = null;
                }
            }
        }
        this.getWindow().setStatusBarColor(this.ui.bg);
        this.getWindow().setNavigationBarColor(this.ui.bg);
        this.getWindow().setBackgroundDrawable((Drawable)new ColorDrawable(this.ui.bg));
        if (Build.VERSION.SDK_INT >= 30) {
            this.getWindow().setDecorFitsSystemWindows(false);
        }
        this.root = this.ui.column();
        this.setContentView((View)this.root);
        this.root.setOnApplyWindowInsetsListener((v, insets) -> {
            int inset = this.pad ? 0 : this.ui.pageInset();
            if (Build.VERSION.SDK_INT >= 30) {
                Insets b = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(b.left + inset, b.top, b.right + inset, b.bottom);
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft() + inset, insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight() + inset, insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        this.root.requestApplyInsets();
        this.loadedDay = Match.today(System.currentTimeMillis());
        this.refreshSubscribed();
        this.busy = true;
        this.render(false);
        LocalDate initialDay = this.from();
        String initialGroup = this.group;
        String initialTag = this.queryTag();
        this.cacheWorker.submit(() -> {
            Repository.Snapshot cached = this.repository.cached(initialDay, initialDay, initialGroup);
            this.handler.post(() -> {
                if (this.isDestroyed()) {
                    return;
                }
                this.cacheReady = true;
                if (initialTag.equals(this.queryTag())) {
                    this.busy = false;
                    this.snapshot = cached;
                    this.rememberAll(initialGroup, initialDay, cached);
                    this.render(true);
                }
                this.load(false);
            });
            if (CalendarSync.permitted((Context)this)) {
                this.ensureCalendar(false);
                this.handler.post(() -> {
                    if (!this.active || this.tab != 1 || this.isDestroyed()) {
                        return;
                    }
                    this.renderedSettings = "";
                    this.render(true);
                });
            }
            CalendarJob.schedule((Context)this);
        });
    }

    protected void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b);
        b.putInt("navigation_schema", 2);
        b.putInt("tab", this.tab);
        b.putInt("offset", this.dayOffset);
        b.putString("group", this.group);
        b.putString("pad_selected", this.padSelectedId);
        b.putInt("score_position", this.scoreList == null ? this.pendingScorePosition : this.scoreList.getFirstVisiblePosition());
        b.putInt("pad_position", this.padList == null ? this.pendingPadPosition : this.padList.getFirstVisiblePosition());
        b.putInt("pad_focus_scroll", this.padFocusScroll == null ? this.pendingPadFocusScroll : this.padFocusScroll.getScrollY());
        if (this.pendingSubscription != null) {
            b.putString("pending_subscription", this.pendingSubscription.json().toString());
        }
    }

    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) {
            Ui.highRefresh(this.getWindow());
        }
    }

    public void onResume() {
        super.onResume();
        this.active = true;
        this.refreshSubscribed();
        if (this.cacheReady) {
            this.render(true);
            this.load(false);
        }
        if (this.calendarSync.hasSubscriptions() && (this.tab == 1 || System.currentTimeMillis() - this.prefs.getLong("calendar_checked", 0L) > 3600000L)) {
            this.syncSubscriptions(false);
        }
    }

    public void onPause() {
        this.active = false;
        this.handler.removeCallbacks(this.tick);
        this.handler.removeCallbacks(this.renderSoon);
        this.handler.removeCallbacks(this.settleLoad);
        this.cancelPrefetch();
        super.onPause();
    }

    public void onDestroy() {
        this.active = false;
        Sheet.dismissAll(this);
        this.handler.removeCallbacksAndMessages(null);
        this.executor.shutdownNow();
        this.cacheWorker.shutdownNow();
        this.prefetchWorker.shutdownNow();
        this.calendarWorker.shutdownNow();
        this.focusWorker.shutdownNow();
        super.onDestroy();
    }

    private LocalDate from() {
        return Match.today(System.currentTimeMillis()).plusDays(this.dayOffset);
    }

    private LocalDate to() {
        return this.from();
    }

    private String queryTag() {
        return this.selectionVersion + ":" + this.tab + ":" + this.from() + ":" + this.to() + ":" + this.group;
    }

    private void load(boolean force) {
        this.handler.removeCallbacks(this.settleLoad);
        if (force) {
            Logos.retryFailures();
            this.renderedScores = "";
        }
        this.handler.removeCallbacks(this.tick);
        if (this.tab == 1 || !this.active) {
            return;
        }
        if (!this.loadedDay.equals(Match.today(System.currentTimeMillis()))) {
            this.loadedDay = Match.today(System.currentTimeMillis());
            this.select(this.tab, this.group, this.dayOffset, true);
            return;
        }
        if (this.busy || this.selectionLoading) {
            this.pending = true;
            this.pendingForce |= force;
            return;
        }
        boolean forced = force || this.pendingForce;
        this.pendingForce = false;
        this.busy = true;
        this.pending = false;
        String tag = this.queryTag();
        long version = this.selectionVersion;
        LocalDate f = this.from();
        LocalDate t = this.to();
        String g = this.group;
        long started = System.currentTimeMillis();
        java.util.concurrent.atomic.AtomicBoolean firstBatch = new java.util.concurrent.atomic.AtomicBoolean(true);
        this.scoreRequest = this.executor.submit(() -> {
            Repository.Snapshot result;
            try {
                result = this.repository.fetch(f, t, g, forced, batch -> this.handler.post(() -> {
                    if (this.isDestroyed() || !this.active || !tag.equals(this.queryTag())) {
                        return;
                    }
                    if (BuildConfig.DEBUG && firstBatch.compareAndSet(true, false)) {
                        Log.i("MyScorePerf", "first source batch in " + (System.currentTimeMillis() - started) + "ms");
                    }
                    this.snapshot.merge((Repository.Batch)batch);
                    this.handler.removeCallbacks(this.renderSoon);
                    this.handler.postDelayed(this.renderSoon, 150L);
                }));
            }
            catch (RuntimeException e) {
                Log.w((String)"MyScore", (String)"Score refresh interrupted", (Throwable)e);
                result = this.repository.cached(f, t, g);
                for (Repository.Batch batch2 : result.batches) {
                    if (!batch2.error.isEmpty()) continue;
                    batch2.error = "刷新意外中断，已保留上次数据";
                }
            }
            Repository.Snapshot next = result;
            this.handler.post(() -> {
                if (this.isDestroyed() || version != this.selectionVersion) {
                    return;
                }
                this.busy = false;
                if (!tag.equals(this.queryTag())) {
                    if (this.active) {
                        this.load(false);
                    }
                    return;
                }
                Log.i((String)"MyScorePerf", (String)("all sources in " + (System.currentTimeMillis() - started) + "ms"));
                this.handler.removeCallbacks(this.renderSoon);
                this.snapshot = next;
                this.rememberAll(g, f, next);
                if (this.active) {
                    this.render(true);
                }
                if (this.active) {
                    if (this.pending || !tag.equals(this.queryTag())) {
                        this.load(false);
                        return;
                    }
                    long now = System.currentTimeMillis();
                    long delay = RefreshPolicy.pollDelay(next.batches, f, Match.today(now), now);
                    long midnight = RefreshPolicy.untilNextDay(now);
                    this.handler.postDelayed(this.tick, delay > 0L ? Math.min(delay, midnight) : midnight);
                    this.schedulePrefetch(f, g);
                }
            });
        });
    }

    private void select(int nextTab, String nextGroup, int offset) {
        this.select(nextTab, nextGroup, offset, false);
    }

    private void select(int nextTab, String nextGroup, int offset, boolean force) {
        if (this.isFinishing() || this.isDestroyed()) {
            return;
        }
        if (!force && this.tab == nextTab && this.group.equals(nextGroup) && this.dayOffset == offset) {
            return;
        }
        this.handler.removeCallbacks(this.tick);
        this.handler.removeCallbacks(this.renderSoon);
        this.handler.removeCallbacks(this.settleLoad);
        this.cancelPrefetch();
        ++this.selectionVersion;
        if (this.scoreRequest != null) {
            this.scoreRequest.cancel(true);
        }
        this.busy = false;
        this.pending = false;
        this.pendingForce = false;
        if (this.selectionCacheRequest != null) {
            this.selectionCacheRequest.cancel(true);
            this.selectionCacheRequest = null;
        }
        boolean inPlace = !this.pad && nextTab == 0 && this.tab == 0 && this.scoreList != null;
        boolean padInPlace = this.pad && nextTab == 0 && this.tab == 0 && this.padList != null && this.padAdapter != null;
        if (inPlace) {
            this.scoreList.stopScrollForSelection();
        }
        if (padInPlace) this.padList.stopScrollForSelection();
        boolean wasToday = this.dayOffset == 0;
        this.tab = nextTab;
        this.group = nextGroup;
        this.dayOffset = offset;
        this.headerOffset = 0;
        this.lastScoreScroll = 0L;
        if (this.tab == 1) {
            this.selectionLoading = false;
            this.refreshSubscribed();
            if (this.pad) {
                this.buildPadScreen();
            } else {
                this.render(false);
            }
            this.syncSubscriptions(false);
            return;
        }
        this.selectionLoading = true;
        LocalDate day = this.from();
        String selectedGroup = this.group;
        String tag = this.queryTag();
        if (this.allSnapshotDay != null && !this.allSnapshotDay.equals(day)) {
            this.allSnapshot = null;
            this.allSnapshotDay = null;
        }
        if (this.padSettingSourcesDay != null && !this.padSettingSourcesDay.equals(day)) {
            this.padSettingSources = null;
            this.padSettingSourcesDay = null;
        }
        Repository.Snapshot local = this.snapshotFor(day, selectedGroup);
        this.snapshot = local != null ? local : new Repository.Snapshot();
        this.renderedScores = "";
        if (this.pad) {
            this.padDetail = null;
            this.padError = "";
            this.padSelected = null;
            ++this.padFocusVersion;
            if (this.padFocusRequest != null) {
                this.padFocusRequest.cancel(true);
                this.padFocusRequest = null;
            }
            this.padFocusBusy = false;
        }
        if (this.pad) {
            // A day/filter change only replaces the list and reading pane. Rebuilding the rail
            // and the whole three-column shell made every arrow tap tear down the workspace.
            if (padInPlace && wasToday == (offset == 0)) {
                this.renderPad(false);
            } else {
                this.buildPadScreen();
            }
        } else if (inPlace) {
            this.rebuildHeader();
        } else {
            this.render(false);
        }
        if (inPlace) {
            this.updateScoreRows();
        }
        if (this.scoreList != null) {
            this.scoreList.setSelection(0);
            this.lastScoreScroll = 0L;
        }
        if (padInPlace && this.padList != null) {
            this.padList.setSelection(0);
        }
        this.selectionCacheRequest = this.cacheWorker.submit(() -> {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            Repository.Snapshot cached = this.repository.cached(day, day, selectedGroup);
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            this.handler.post(() -> {
                if (this.isDestroyed() || !tag.equals(this.queryTag())) {
                    return;
                }
                this.selectionLoading = false;
                // Keep the in-memory day (and any batch that arrived while the disk read ran)
                // when it is at least as fresh as the same source on disk.
                for (Repository.Batch batch : this.snapshot.batches) {
                    Repository.Batch disk = cached.batches.stream().filter(b -> b.competition.id.equals(batch.competition.id)).findFirst().orElse(null);
                    if (disk != null && disk.fetched > batch.fetched) continue;
                    cached.merge(batch);
                }
                this.snapshot = cached;
                this.rememberAll(selectedGroup, day, cached);
                // Let a quick run of arrow taps settle first; only the final day pays for a
                // network round instead of every intermediate selection.
                this.scheduleLoad(150L);
                if (!tag.equals(this.queryTag())) {
                    return;
                }
                this.renderedScores = "";
                if (this.active) {
                    this.updateScoreRows();
                    if (this.pad) {
                        this.rebuildPadFocus(true);
                    }
                }
                if (BuildConfig.DEBUG) {
                    Log.i((String)"MyScorePerf", (String)("selection cache group=" + selectedGroup + " items=" + cached.matches().size()));
                }
            });
        });
    }

    private static Repository.Snapshot subset(Repository.Snapshot source, String group) {
        Repository.Snapshot result = new Repository.Snapshot();
        for (Repository.Batch batch : source.batches) {
            if (!"全部".equals(group) && !batch.competition.group.equals(group)) continue;
            result.batches.add(batch);
        }
        return result;
    }

    private void rememberAll(String selectedGroup, LocalDate day, Repository.Snapshot value) {
        this.rememberDay(day, selectedGroup, value);
        if ("全部".equals(selectedGroup)) {
            this.allSnapshot = value;
            this.allSnapshotDay = day;
            // the all-group snapshot now covers every source, so the settings-only copy is redundant.
            if (day.equals(this.padSettingSourcesDay)) {
                this.padSettingSources = null;
                this.padSettingSourcesDay = null;
            }
        } else if (this.allSnapshot != null && day.equals(this.allSnapshotDay)) {
            Repository.Snapshot merged = MainActivity.subset(this.allSnapshot, "全部");
            for (Repository.Batch batch : value.batches) {
                merged.merge(batch);
            }
            this.allSnapshot = merged;
            this.rememberDay(day, "全部", merged);
        }
    }

    private static String dayKey(LocalDate day, String group) {
        return day + "|" + group;
    }

    /** Bounded per-day memory: switching away and back must not re-read or re-request. */
    private void rememberDay(LocalDate day, String group, Repository.Snapshot value) {
        if (value == null) {
            return;
        }
        this.daySnapshots.put(MainActivity.dayKey(day, group), value);
    }

    private Repository.Snapshot snapshotFor(LocalDate day, String group) {
        Repository.Snapshot exact = this.daySnapshots.get(MainActivity.dayKey(day, group));
        if (exact != null) {
            return exact;
        }
        Repository.Snapshot everything = this.daySnapshots.get(MainActivity.dayKey(day, "全部"));
        if (everything != null && !"全部".equals(group)) {
            return MainActivity.subset(everything, group);
        }
        if (this.allSnapshot != null && day.equals(this.allSnapshotDay)) {
            return "全部".equals(group) ? this.allSnapshot : MainActivity.subset(this.allSnapshot, group);
        }
        return null;
    }

    private void scheduleLoad(long delay) {
        this.handler.removeCallbacks(this.settleLoad);
        this.handler.postDelayed(this.settleLoad, delay);
    }

    /**
     * Warm the next day once the current one has settled. The user browses forward day by day,
     * so the first tap on tomorrow should paint from memory instead of waiting for nine sources.
     */
    private void schedulePrefetch(LocalDate day, String group) {
        if (!this.active || this.isDestroyed() || this.tab != 0) {
            return;
        }
        if (this.snapshot.failed() || this.busy || this.selectionLoading) {
            return;
        }
        LocalDate next = day.plusDays(1L);
        String key = MainActivity.dayKey(next, group);
        if (this.daySnapshots.containsKey(key)) {
            return;
        }
        Long at = this.prefetchedAt.get(key);
        if (at != null && System.currentTimeMillis() - at < 10L * 60_000L) {
            return;
        }
        // A live score tick must not keep deferring the same prefetch forever.
        if (this.prefetchTask != null && key.equals(this.prefetchTaskKey)) {
            return;
        }
        if (this.prefetchTask != null) {
            this.handler.removeCallbacks(this.prefetchTask);
        }
        this.prefetchTaskKey = key;
        this.prefetchTask = () -> this.prefetch(next, group);
        this.handler.postDelayed(this.prefetchTask, 1_200L);
    }

    private void prefetch(LocalDate day, String group) {
        this.prefetchTask = null;
        this.prefetchTaskKey = null;
        if (!this.active || this.isDestroyed() || this.tab != 0) {
            return;
        }
        if (!group.equals(this.group) || !day.equals(this.from().plusDays(1L))) {
            return;
        }
        String key = MainActivity.dayKey(day, group);
        if (this.daySnapshots.containsKey(key)) {
            return;
        }
        this.prefetchedAt.put(key, System.currentTimeMillis());
        Repository source = this.prefetchRepository == null ? this.repository : this.prefetchRepository;
        this.prefetchRequest = this.prefetchWorker.submit(() -> {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            Repository.Snapshot result = source.fetch(day, day, group, false);
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            this.handler.post(() -> {
                if (this.isDestroyed()) {
                    return;
                }
                // A failed quiet read never becomes the day's memory cache; opening it retries.
                if (!result.failed() && !this.daySnapshots.containsKey(key)) {
                    this.rememberDay(day, group, result);
                }
                if (!result.failed()) {
                    this.warmCrests(result.matches());
                }
                if (BuildConfig.DEBUG) {
                    Log.i("MyScorePerf", "prefetch " + key + " items=" + result.matches().size() + " failed=" + result.failed());
                }
            });
        });
    }

    private void cancelPrefetch() {
        if (this.prefetchTask != null) {
            this.handler.removeCallbacks(this.prefetchTask);
            this.prefetchTask = null;
        }
        this.prefetchTaskKey = null;
        if (this.prefetchRequest != null) {
            this.prefetchRequest.cancel(true);
            this.prefetchRequest = null;
        }
    }

    /** Warm list-size crests for a prefetched day so its first paint already has them. */
    private void warmCrests(List<Match> matches) {
        if (matches == null || matches.isEmpty()) {
            return;
        }
        LinkedHashSet<String> urls = new LinkedHashSet<String>();
        int limit = Math.min(matches.size(), 16);
        for (int i = 0; i < limit; i++) {
            Match match = matches.get(i);
            if (match.homeLogo != null && !match.homeLogo.isBlank()) {
                urls.add(match.homeLogo);
            }
            if (match.awayLogo != null && !match.awayLogo.isBlank()) {
                urls.add(match.awayLogo);
            }
        }
        Logos.warm(this.ui, urls, this.pad ? 24 : 28);
    }

    private void rebuildHeader() {
        if (this.pad) {
            this.renderPad(false);
            return;
        }
        if (this.headerView == null || this.isFinishing() || this.isDestroyed()) {
            return;
        }
        // Keep the tapped controls and horizontal scroll position across sport changes.
        for (int i = 0; i < this.filterTabs.getChildCount(); i++) {
            TextView item = (TextView)this.filterTabs.getChildAt(i);
            String group = GROUPS[i];
            boolean selected = group.equals(this.group);
            if (item.isSelected() != selected) {
                this.ui.selectTab(item, selected);
                item.setContentDescription(group + (selected ? "，已选中" : "，筛选赛事"));
            }
        }
        if (!this.from().equals(this.headerDay)) {
            int index = this.headerView.indexOfChild(this.dateSelectorView);
            this.headerView.removeView(this.dateSelectorView);
            this.dateSelectorView = this.dateSelector();
            this.headerView.addView(this.dateSelectorView, index);
            this.headerView.post(this::syncHeaderHeight);
        }
        this.headerView.setTranslationY(0.0f);
    }

    private void render(boolean preserve) {
        if (this.isFinishing() || this.isDestroyed()) {
            return;
        }
        if (this.pad) {
            this.renderPad(preserve);
            return;
        }
        if (preserve && this.tab == 0 && this.scoreList != null) {
            if (this.scoreList.isInteracting() || SystemClock.uptimeMillis() - this.lastScoreScroll < 250L) {
                this.handler.removeCallbacks(this.renderSoon);
                this.handler.postDelayed(this.renderSoon, 250L);
                return;
            }
            this.updateScoreRows();
            return;
        }
        if (preserve && this.tab == 1 && this.body != null) {
            String key = this.settingsKey();
            if (key.equals(this.renderedSettings)) {
                return;
            }
            this.renderedSettings = key;
            this.body.removeAllViews();
            this.settings();
            return;
        }
        this.buildScreen();
    }

    private void buildScreen() {
        if (this.isFinishing() || this.isDestroyed()) {
            return;
        }
        if (this.pad) {
            this.buildPadScreen();
            return;
        }
        boolean scores = this.tab == 0;
        this.root.removeAllViews();
        this.filterTabs = null;
        this.dateSelectorView = null;
        this.headerDay = null;
        this.stage = new FrameLayout((Context)this);
        this.root.addView((View)this.stage, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, 0, 1.0f));
        if (scores) {
            this.scoreList = new ScoreList((Context)this);
            this.scoreList.setPadding(this.ui.dp(16.0f), this.ui.dp(154.0f), this.ui.dp(16.0f), this.ui.dp(24.0f));
            this.scoreList.setOnScrollPixels(this::onScoreScroll);
            this.scoreAdapter = new ScoreAdapter(this.ui, this);
            this.scoreAdapter.attach(this.scoreList);
            this.scoreList.setAdapter((ListAdapter)this.scoreAdapter);
            this.stage.addView((View)this.scoreList, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -1));
        } else {
            this.scroll = new ScrollView((Context)this);
            this.scroll.setFillViewport(true);
            this.scroll.setClipToPadding(false);
            this.scroll.setVerticalScrollBarEnabled(false);
            this.scroll.setPadding(0, this.ui.dp(50.0f), 0, 0);
            this.body = this.ui.column();
            this.body.setPadding(this.ui.dp(16.0f), this.ui.dp(4.0f), this.ui.dp(16.0f), this.ui.dp(24.0f));
            this.scroll.addView((View)this.body);
            this.stage.addView((View)this.scroll, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -1));
            this.scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> {
                if (this.headerHeight <= 0) {
                    return;
                }
                int delta = y - oy;
                if (delta > 0) {
                    this.headerOffset = Math.min(this.headerHeight, this.headerOffset + delta);
                } else if (delta < 0) {
                    this.headerOffset = 0;
                } else {
                    return;
                }
                this.headerView.setTranslationY((float)(-this.headerOffset));
            });
        }
        this.headerView = this.ui.column();
        this.headerView.setBackgroundColor(this.ui.bg);
        this.header(this.headerView);
        if (scores) {
            this.filters(this.headerView);
            this.dateSelectorView = this.dateSelector();
            this.headerView.addView(this.dateSelectorView);
        }
        this.headerView.addView(this.ui.divider());
        this.stage.addView((View)this.headerView, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -2, 48));
        this.headerHeight = this.measureHeader();
        this.headerOffset = 0;
        this.headerView.setTranslationY(0.0f);
        if (scores) {
            this.scoreList.setPadding(this.ui.dp(16.0f), this.headerHeight, this.ui.dp(16.0f), this.ui.dp(24.0f));
            this.renderedScores = "";
            this.updateScoreRows();
        } else {
            this.scroll.setPadding(0, this.headerHeight, 0, 0);
            this.renderedSettings = "";
            this.settings();
            this.renderedSettings = this.settingsKey();
        }
        this.root.addView(this.ui.divider());
        this.navigation();
        this.headerView.post(this::syncHeaderHeight);
    }

    private int measureHeader() {
        int width = this.root.getWidth() - this.root.getPaddingLeft() - this.root.getPaddingRight();
        if (width <= 0) {
            width = this.getResources().getDisplayMetrics().widthPixels;
        }
        this.headerView.measure(View.MeasureSpec.makeMeasureSpec((int)width, (int)0x40000000), View.MeasureSpec.makeMeasureSpec((int)0, (int)0));
        return Math.max(0, this.headerView.getMeasuredHeight());
    }

    private void syncHeaderHeight() {
        if (this.headerView == null || this.isFinishing() || this.isDestroyed()) {
            return;
        }
        int height = this.headerView.getHeight();
        if (height <= 0) {
            return;
        }
        this.headerHeight = height;
        if (this.tab == 0 && this.scoreList != null) {
            int left = this.ui.dp(16.0f), bottom = this.ui.dp(24.0f);
            if (this.scoreList.getPaddingTop() != height || this.scoreList.getPaddingLeft() != left || this.scoreList.getPaddingBottom() != bottom) {
                this.scoreList.setPadding(left, height, left, bottom);
            }
        } else if (this.scroll != null && this.scroll.getPaddingTop() != height) {
            this.scroll.setPadding(0, height, 0, 0);
        }
        this.headerView.setTranslationY((float)(-Math.min(this.headerOffset, height)));
    }

    private void onScoreScroll(int delta) {
        this.lastScoreScroll = SystemClock.uptimeMillis();
        if (this.headerView == null || this.headerHeight <= 0) {
            return;
        }
        if (delta > 0) {
            int next = Math.min(this.headerHeight, this.headerOffset + delta);
            if (next != this.headerOffset) {
                this.headerOffset = next;
                this.headerView.setTranslationY((float)(-this.headerOffset));
            }
        } else if (delta < 0 && this.headerOffset != 0) {
            this.headerOffset = 0;
            this.headerView.setTranslationY(0.0f);
        }
    }

    private void applyPendingScorePosition(int count) {
        if (this.pendingScorePosition < 0 || this.scoreList == null || count <= 0) return;
        int position = Math.min(this.pendingScorePosition, Math.max(0, this.scoreList.getCount() - 1));
        this.pendingScorePosition = -1;
        this.scoreList.post(() -> this.scoreList.setSelection(position));
    }
    private void applyPendingPadPosition(int count) {
        if (this.pendingPadPosition < 0 || this.padList == null || count <= 0) return;
        int position = Math.min(this.pendingPadPosition, Math.max(0, this.padList.getCount() - 1));
        this.pendingPadPosition = -1;
        this.padList.post(() -> this.padList.setSelection(position));
    }

    private void updateScoreRows() {
        if (this.pad) {
            this.renderPad(true);
            return;
        }
        if (this.scoreList == null || this.scoreAdapter == null) {
            return;
        }
        List<Match> all = this.snapshot.matches();
        this.watches.prepare(all, this::onWatchChanged);
        String signature = this.scoreSignature(all);
        if (signature.equals(this.renderedScores)) {
            return;
        }
        this.renderedScores = signature;
        long started = SystemClock.uptimeMillis();
        List<ScoreRow> rows = this.buildScoreRows(all);
        boolean relaid = this.scoreAdapter.updateRows(rows);
        if (relaid) {
            this.scoreList.resetScrollTracking();
        }
        this.applyPendingScorePosition(all.size());
        if (BuildConfig.DEBUG) {
            Log.i((String)"MyScorePerf", (String)("score rows rebound=" + rows.size() + " matches=" + all.size() + " relaid=" + relaid + " in " + (SystemClock.uptimeMillis() - started) + "ms"));
        }
    }

    private void onWatchChanged() {
        if (!this.active || this.isFinishing() || this.isDestroyed()) {
            return;
        }
        this.renderedScores = "";
        if (this.pad) {
            this.renderPad(true);
            return;
        }
        if (this.scoreAdapter != null) {
            this.scoreAdapter.notifyDataSetChanged();
        }
    }

    private void onPadWatchChanged() {
        if (!this.active || this.isFinishing() || this.isDestroyed()) {
            return;
        }
        this.renderPad(true);
    }

    private String scoreSignature(List<Match> all) {
        long now = System.currentTimeMillis();
        StringBuilder key = new StringBuilder(256);
        key.append(this.group).append('|').append(this.tab).append('|').append((this.busy || this.selectionLoading) && all.isEmpty()).append('|').append(this.snapshot.failed());
        for (Repository.Batch batch : this.snapshot.batches) {
            key.append('|').append(batch.competition.id).append(':').append(batch.error);
        }
        for (Match m : all) {
            key.append('|').append(m.id).append(',').append(m.league).append(',').append(m.group).append(',').append(m.home).append(',').append(m.away).append(',').append(m.homeScore).append(',').append(m.awayScore).append(',').append(m.state).append(',').append(m.status).append(',').append(m.start).append(',').append(m.timeConfirmed).append(',').append(m.title).append(',').append(m.homeId).append(',').append(m.awayId).append(',').append(m.homeLogo).append(',').append(m.awayLogo).append(',').append(this.subscribed.contains(m.id)).append(',').append(m.live() && now - m.updated > 120000L).append(',').append(m.source).append(',').append(m.updated / 60000L);
            WatchLinks.Link watch = this.watches.link(m);
            key.append(',').append(watch == null ? "" : watch.url).append(',').append(watch == null ? "" : watch.title);
            for (String result : m.results) {
                key.append(',').append(result);
            }
        }
        return key.toString();
    }

    private List<ScoreRow> buildScoreRows(List<Match> all) {
        String[] stages;
        ArrayList<ScoreRow> rows = new ArrayList<ScoreRow>();
        if (all.isEmpty()) {
            if (this.busy || this.selectionLoading) {
                rows.add(ScoreRow.loading("正在读取赛程…"));
                return rows;
            }
            if (this.snapshot.failed()) {
                int failures = 0;
                for (Repository.Batch batch : this.snapshot.batches) {
                    if (batch.error.isEmpty()) continue;
                    ++failures;
                }
                rows.add(ScoreRow.failure(failures + " 个赛事来源未更新 · 查看连接状态"));
            }
            rows.add(this.snapshot.failed() ? ScoreRow.empty("暂时无法获取比赛", "请检查网络后重新获取；连接失败不代表没有比赛。") : ScoreRow.empty(this.group.equals("全部") ? "这个日期没有已确认的比赛" : "当前赛事没有已确认的比赛", this.group.equals("全部") ? "可以切换日期或赛事。" : "可以切换赛事或日期。"));
            return rows;
        }
        if (this.snapshot.failed()) {
            int failures = 0;
            for (Repository.Batch batch : this.snapshot.batches) {
                if (batch.error.isEmpty()) continue;
                ++failures;
            }
            rows.add(ScoreRow.failure(failures + " 个赛事来源未更新 · 查看连接状态"));
        }
        for (String stage : stages = new String[]{"live", "scheduled", "delayed", "unknown", "final", "cancelled"}) {
            ArrayList<Match> stageMatches = new ArrayList<Match>();
            for (Match m : all) {
                if (!m.state.equals(stage)) continue;
                stageMatches.add(m);
            }
            if (stageMatches.isEmpty()) continue;
            rows.add(ScoreRow.section(Rules.stateLabel(stage) + " · " + stageMatches.size()));
            for (Match m : stageMatches) {
                rows.add(ScoreRow.match(m));
            }
        }
        return rows;
    }

    private String settingsKey() {
        StringBuilder key = new StringBuilder(128);
        key.append(this.tab).append('|').append(this.calendarBusy).append('|').append(this.calendarName()).append('|').append(this.calendarSync.linkedEvents()).append('|').append(this.prefs.getLong("calendar_checked", 0L)).append('|').append(this.snapshot.failed());
        for (Repository.Batch batch : this.snapshot.batches) {
            key.append('|').append(batch.competition.id).append(':').append(batch.error);
        }
        return key.toString();
    }

    private void header(LinearLayout parent) {
        LinearLayout header = this.ui.row();
        header.setMinimumHeight(this.ui.dp(56.0f));
        header.setPadding(this.ui.dp(20.0f), this.ui.dp(4.0f), this.ui.dp(12.0f), this.ui.dp(4.0f));
        if (this.tab == 0) {
            Ui.Icon mark = new Ui.Icon((Context)this, "mark", this.ui.text);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(this.ui.dp(24.0f), this.ui.dp(24.0f));
            mp.setMarginEnd(this.ui.dp(10.0f));
            header.addView((View)mark, (ViewGroup.LayoutParams)mp);
        }
        TextView heading = this.ui.text(this.tab == 0 ? "MyScore" : "设置", 24, this.ui.text, true);
        heading.setLetterSpacing(-0.025f);
        header.addView((View)heading, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        if (this.tab == 0) {
            header.addView((View)this.headerAction("refresh", "刷新比赛", () -> this.load(true)));
        } else {
            TextView version = this.ui.text(BuildConfig.VERSION_NAME, 12, this.ui.muted, false);
            version.setPadding(this.ui.dp(8.0f), 0, this.ui.dp(8.0f), 0);
            header.addView((View)version);
        }
        parent.addView((View)header);
    }

    private FrameLayout headerAction(String kind, String description, Runnable click) {
        FrameLayout button = new FrameLayout((Context)this);
        button.addView((View)new Ui.Icon((Context)this, kind, this.ui.text), (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(this.ui.dp(20.0f), this.ui.dp(20.0f), 17));
        this.ui.clickable((View)button, 0, 24);
        button.setContentDescription((CharSequence)description);
        button.setFocusable(true);
        button.setOnClickListener(v -> click.run());
        button.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(48.0f), this.ui.dp(48.0f)));
        return button;
    }

    private void filters(LinearLayout parent) {
        HorizontalScrollView horizontal = new HorizontalScrollView((Context)this);
        horizontal.setHorizontalScrollBarEnabled(false);
        horizontal.setClipToPadding(false);
        LinearLayout tabs = this.ui.row();
        this.filterTabs = tabs;
        tabs.setPadding(this.ui.dp(6.0f), 0, this.ui.dp(6.0f), 0);
        for (String g : GROUPS) {
            TextView item = this.ui.tab(g, g.equals(this.group), () -> this.select(this.tab, g, this.dayOffset));
            item.setContentDescription((CharSequence)(g + (g.equals(this.group) ? "，已选中" : "，筛选赛事")));
            tabs.addView((View)item, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-2, -2));
        }
        horizontal.addView((View)tabs);
        parent.addView((View)horizontal, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        int selectedIndex = 0;
        for (int i = 0; i < GROUPS.length; ++i) {
            if (!GROUPS[i].equals(this.group)) continue;
            selectedIndex = i;
        }
        int current = selectedIndex;
        horizontal.post(() -> {
            View selected = tabs.getChildAt(current);
            if (selected != null) {
                horizontal.scrollTo(Math.max(0, selected.getLeft() - this.ui.dp(20.0f)), 0);
            }
        });
        parent.addView(this.ui.divider());
    }

    private LinearLayout dateSelector() {
        LocalDate today = Match.today(System.currentTimeMillis());
        LocalDate selected = this.from();
        this.headerDay = selected;
        LinearLayout bar = this.ui.row();
        bar.setPadding(this.ui.dp(20.0f), this.ui.dp(4.0f), this.ui.dp(12.0f), this.ui.dp(4.0f));
        boolean large = this.getResources().getConfiguration().fontScale >= 1.5f;
        String date = Formats.date(selected, selected.getYear() == today.getYear() ? Formats.MONTH_DAY : Formats.YEAR_MONTH_DAY, "");
        String caption = large ? date + "\n" + (selected.equals(today) ? "今天 · " : "") + Formats.WEEKDAY.format(selected) : (selected.equals(today) ? "今天  ·  " : "") + date + " " + Formats.WEEKDAY.format(selected);
        TextView label = this.ui.text(caption + "  ▾", 13, this.ui.text, true);
        label.setGravity(16);
        label.setMinHeight(this.ui.dp(48.0f));
        label.setPadding(0, this.ui.dp(8.0f), this.ui.dp(8.0f), this.ui.dp(8.0f));
        this.ui.clickable((View)label, 0, 0);
        label.setFocusable(true);
        label.setContentDescription((CharSequence)("选择日期，当前" + Formats.MONTH_DAY_FULL_WEEK.format(selected)));
        label.setOnClickListener(v -> Sheet.date(this, this.from(), this::selectDay));
        bar.addView((View)label, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        if (!selected.equals(today)) {
            TextView reset = this.ui.text("今天", 12, this.ui.text, true);
            reset.setGravity(17);
            reset.setMinHeight(this.ui.dp(48.0f));
            reset.setPadding(this.ui.dp(8.0f), 0, this.ui.dp(8.0f), 0);
            this.ui.clickable((View)reset, 0, 0);
            reset.setFocusable(true);
            reset.setContentDescription((CharSequence)"回到今天");
            reset.setOnClickListener(v -> this.selectDay(today));
            bar.addView((View)reset, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-2, -2));
        }
        FrameLayout prev = this.headerAction("chevron", "前一天", () -> this.selectDay(selected.minusDays(1L)));
        prev.getChildAt(0).setRotation(180.0f);
        bar.addView((View)prev);
        bar.addView((View)this.headerAction("chevron", "后一天", () -> this.selectDay(selected.plusDays(1L))));
        return bar;
    }

    private void selectDay(LocalDate day) {
        LocalDate today = Match.today(System.currentTimeMillis());
        int offset = (int)ChronoUnit.DAYS.between(today, day);
        this.select(0, this.group, offset);
    }

    private void matchDetail(Match m) {
        this.startActivity(new Intent((Context)this, DetailActivity.class).putExtra("match", m.json().toString()));
    }

    @Override
    public void openMatch(Match match) {
        this.matchDetail(match);
    }

    @Override
    public void openWatch(Match match) {
        this.watchLauncher.open(match);
    }

    @Override
    public WatchLinks.Link watchLink(Match match) {
        return this.watches.link(match);
    }

    @Override
    public void openTeam(Match match, boolean home) {
        Team team = Team.of(match, home);
        if (team == null) {
            this.message("球队赛程暂不可用", "来源尚未提供这支球队的标识，稍后刷新后再试。");
            return;
        }
        this.startActivity(new Intent((Context)this, TeamActivity.class).putExtra("team", team.json().toString()));
    }

    @Override
    public void retryScores() {
        this.load(true);
    }

    @Override
    public void showSourceStatus() {
        this.sourceStatus();
    }

    @Override
    public boolean isSubscribed(String id) {
        return this.subscribed.contains(id);
    }

    private void settings() {
        this.section("单场订阅");
        LinearLayout calendar = this.ui.column();
        calendar.setBackground((Drawable)this.ui.panel());
        this.settingRow(calendar, "calendar-target", "接收日历", this.calendarName(), this::calendarChoice);
        this.settingRow(calendar, "subscriptions", "已订阅的比赛", this.calendarSync.linkedEvents() + " 场 · 提前 5 分钟提醒", this::subscribedList);
        long checked = this.prefs.getLong("calendar_checked", 0L);
        this.settingRow(calendar, "sync-subscriptions", this.calendarBusy ? "正在检查…" : "检查已订阅比赛", checked > 0L ? "上次检查 " + MainActivity.formatInstant(checked, "M月d日 HH:mm") : "同步改期与取消", () -> this.syncSubscriptions(true));
        this.settingRow(calendar, "calendar-open", "打开系统日历", "查看日程与提醒", this::openCalendar);
        this.body.addView((View)calendar);
        this.section("偏好与帮助");
        LinearLayout options = this.ui.column();
        options.setBackground((Drawable)this.ui.panel());
        this.settingRow(options, "appearance", "外观", this.themeName(), this::themeChoice);
        this.settingRow(options, "app-update", "检查更新", "当前版本 " + BuildConfig.VERSION_NAME, () -> this.startActivity(new Intent((Context)this, UpdateActivity.class)));
        this.settingRow(options, "sources", "数据来源与连接", this.snapshot.failed() ? "部分来源未更新" : "查看来源状态", this::sourceStatus);
        this.settingRow(options, "help", "使用帮助", "日历提醒与数据说明", this::help);
        this.body.addView((View)options);
        this.ui.gap(this.body, 24);
        TextView hint = this.ui.text("MyScore · " + BuildConfig.VERSION_NAME + "\n在比赛卡上点日历图标，订阅单场比赛。", 12, this.ui.muted, false);
        hint.setLineSpacing((float)this.ui.dp(6.0f), 1.0f);
        hint.setGravity(17);
        hint.setPadding(this.ui.dp(4.0f), 0, this.ui.dp(4.0f), 0);
        this.body.addView((View)hint);
    }

    private void section(String title) {
        this.ui.gap(this.body, 24);
        TextView h = this.ui.text(title, 13, this.ui.muted, true);
        h.setPadding(this.ui.dp(4.0f), 0, 0, this.ui.dp(12.0f));
        this.body.addView((View)h);
    }

    private void settingRow(LinearLayout into, String kind, String title, String detail, Runnable action) {
        if (into.getChildCount() > 0) {
            View line = this.ui.divider();
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, this.ui.dp(1.0f));
            lp.setMarginStart(this.ui.dp(16.0f));
            lp.setMarginEnd(this.ui.dp(16.0f));
            into.addView(line, (ViewGroup.LayoutParams)lp);
        }
        LinearLayout row = this.ui.row();
        row.setMinimumHeight(this.ui.dp(72.0f));
        row.setPadding(this.ui.dp(16.0f), this.ui.dp(16.0f), this.ui.dp(14.0f), this.ui.dp(16.0f));
        this.ui.clickable((View)row, 0, 20);
        row.setFocusable(true);
        FrameLayout symbol = new FrameLayout((Context)this);
        symbol.addView((View)new Ui.Icon((Context)this, kind, this.ui.muted), (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(this.ui.dp(20.0f), this.ui.dp(20.0f), 17));
        LinearLayout.LayoutParams symbolParams = new LinearLayout.LayoutParams(this.ui.dp(24.0f), this.ui.dp(40.0f));
        symbolParams.setMarginEnd(this.ui.dp(14.0f));
        row.addView((View)symbol, (ViewGroup.LayoutParams)symbolParams);
        LinearLayout text = this.ui.column();
        text.addView((View)this.ui.text(title, 15, this.ui.text, true));
        if (!detail.isBlank()) {
            this.ui.gap(text, 5);
            text.addView((View)this.ui.text(detail, 12, this.ui.muted, false));
        }
        row.addView((View)text, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(this.ui.dp(18.0f), this.ui.dp(18.0f));
        ip.setMarginStart(this.ui.dp(12.0f));
        row.addView((View)new Ui.Icon((Context)this, "chevron", this.ui.muted), (ViewGroup.LayoutParams)ip);
        row.setOnClickListener(v -> action.run());
        into.addView((View)row);
    }

    private void openCalendar() {
        try {
            this.startActivity(new Intent("android.intent.action.VIEW", Uri.parse((String)("content://com.android.calendar/time/" + System.currentTimeMillis()))));
        }
        catch (RuntimeException e) {
            this.message("没有找到日历应用", "请先安装或启用系统日历。");
        }
    }

    private void help() {
        this.message("使用帮助", "在比赛卡上点日历加号订阅，点勾选可取消。提醒由系统日历发送；小米 / 澎湃 OS 需要允许日历通知与后台运行。\n\n更换接收日历只影响新订阅，已有事件保留在原日历。\n\n体育由 ESPN 提供；CS2 仅 Spirit 主队，来自 5EPlay；LOL 仅 Worlds，来自英雄联盟赛事官网。只有进行中或临近开赛的比赛会自动检查；已结束的比赛只读取一次，全部结束后不再自动联网，点右上角可手动刷新。失败时延长重试间隔，连接失败保留真实缓存，缺失数据不补零。");
    }

    private String calendarName() {
        String name = this.calendarSync.calendarName();
        return name == null || name.isBlank() ? "选择手机日历" : name;
    }

    private void calendarChoice() {
        this.chooseCalendar();
    }

    private void chooseCalendar() {
        if (this.isFinishing() || this.isDestroyed()) {
            return;
        }
        if (!CalendarSync.permitted((Context)this)) {
            this.requestPermissions(new String[]{"android.permission.READ_CALENDAR", "android.permission.WRITE_CALENDAR"}, 42);
            return;
        }
        if (this.calendarQuerying) {
            return;
        }
        this.calendarQuerying = true;
        this.calendarWorker.submit(() -> {
            List<CalendarSync.CalendarChoice> choices;
            try {
                choices = this.calendarSync.calendars();
            }
            catch (RuntimeException e) {
                choices = Collections.emptyList();
            }
            List<CalendarSync.CalendarChoice> result = choices;
            this.handler.post(() -> {
                this.calendarQuerying = false;
                if (this.isDestroyed() || this.isFinishing()) {
                    return;
                }
                if (result.isEmpty()) {
                    Sheet.message(this, "没有可写入的日历", "请先在系统日历中启用一个本地日历或可写账户，再回来选择。");
                    return;
                }
                ArrayList<Sheet.Item> items = new ArrayList<Sheet.Item>();
                int selected = -1;
                for (int i = 0; i < result.size(); ++i) {
                    CalendarSync.CalendarChoice choice = (CalendarSync.CalendarChoice)result.get(i);
                    if (choice.id == this.calendarSync.calendarId()) {
                        selected = i;
                    }
                    items.add(new Sheet.Item(choice.name, choice.local ? "本机日历" : "可写账户", choice.id == this.calendarSync.calendarId()));
                }
                Sheet.choose(this, "选择接收比赛的日历", "订阅的比赛会写入这个日历，并提前 5 分钟提醒。", items, selected, index -> {
                    CalendarSync.CalendarChoice choice = (CalendarSync.CalendarChoice)result.get(index);
                    this.calendarSync.chooseCalendar(choice.id, choice.name);
                    CalendarJob.schedule((Context)this);
                    this.render(true);
                });
            });
        });
    }

    @Override
    public void toggleSubscription(Match m) {
        if (this.isFinishing() || this.isDestroyed()) {
            return;
        }
        if (this.calendarBusy) {
            Toast.makeText((Context)this, (CharSequence)"正在处理日历…", (int)0).show();
            return;
        }
        if (this.subscribed.contains(m.id)) {
            Sheet.confirm(this, "取消订阅", "从手机日历中移除「" + m.displayTitle() + "」及其提醒？", "取消订阅", () -> this.unsubscribeAsync(m));
            return;
        }
        if (!m.scheduled() || !m.timeConfirmed || m.start <= System.currentTimeMillis()) {
            Sheet.message(this, "暂时不能订阅", "这场比赛的开赛时间未确定、已开始或已结束。");
            return;
        }
        this.pendingSubscription = m;
        if (!CalendarSync.permitted((Context)this)) {
            this.requestPermissions(new String[]{"android.permission.READ_CALENDAR", "android.permission.WRITE_CALENDAR"}, 42);
            return;
        }
        this.subscribePending();
    }

    private boolean ensureCalendar(boolean complain) {
        long id = this.calendarSync.calendarId();
        if (id >= 0L && this.calendarSync.hasCalendar(id)) {
            return true;
        }
        CalendarSync.CalendarChoice best = this.calendarSync.bestCalendar();
        if (best == null) {
            if (complain) {
                Sheet.message(this, "没有可写入的日历", "请先在系统日历中启用一个本地或小米日历。");
            }
            return false;
        }
        this.calendarSync.chooseCalendar(best.id, best.name);
        return true;
    }

    private void subscribePending() {
        if (this.isFinishing() || this.isDestroyed()) {
            this.pendingSubscription = null;
            return;
        }
        Match m = this.pendingSubscription;
        if (m == null) {
            return;
        }
        this.pendingSubscription = null;
        this.calendarBusy = true;
        this.calendarWorker.submit(() -> {
            CalendarSync.Result result;
            try {
                result = this.ensureCalendar(false) ? this.calendarSync.subscribe(m, System.currentTimeMillis()) : MainActivity.noCalendarResult();
            }
            catch (RuntimeException e) {
                result = new CalendarSync.Result();
                result.failed = 1;
                result.error = "日历写入失败";
            }
            CalendarSync.Result finalResult = result;
            this.handler.post(() -> {
                this.calendarBusy = false;
                if (this.isDestroyed()) {
                    return;
                }
                this.refreshSubscribed();
                this.render(true);
                CalendarJob.schedule((Context)this);
                if (finalResult.inserted + finalResult.updated > 0) {
                    Toast.makeText((Context)this, (CharSequence)"已加入日历 · 提前 5 分钟提醒", (int)0).show();
                } else {
                    Sheet.message(this, "未能加入日历", finalResult.error.isEmpty() ? "请检查日历权限和目标日历。" : finalResult.error);
                }
            });
        });
    }

    private static CalendarSync.Result noCalendarResult() {
        CalendarSync.Result result = new CalendarSync.Result();
        result.failed = 1;
        result.error = "请先在系统日历中启用一个本地或小米日历。";
        return result;
    }

    private void unsubscribeAsync(Match m) {
        if (this.isFinishing() || this.isDestroyed()) {
            return;
        }
        this.calendarBusy = true;
        this.calendarWorker.submit(() -> {
            CalendarSync.Result result;
            try {
                result = this.calendarSync.unsubscribe(m.id);
            }
            catch (RuntimeException e) {
                result = new CalendarSync.Result();
                result.failed = 1;
                result.error = "日历读取失败";
            }
            CalendarSync.Result finalResult = result;
            this.handler.post(() -> {
                this.calendarBusy = false;
                if (this.isDestroyed()) {
                    return;
                }
                this.refreshSubscribed();
                this.render(true);
                CalendarJob.schedule((Context)this);
                if (finalResult.failed > 0) {
                    Sheet.message(this, "未能取消订阅", finalResult.error.isEmpty() ? "请检查日历权限后重试。" : finalResult.error);
                } else {
                    Toast.makeText((Context)this, (CharSequence)"已取消订阅", (int)0).show();
                }
            });
        });
    }

    private void subscribedList() {
        List<Match> list = this.calendarSync.subscriptions();
        if (list.isEmpty()) {
            Sheet.message(this, "还没有订阅", "在比赛卡上点“订阅”，把单场比赛加入手机日历。");
            return;
        }
        ArrayList<Sheet.Item> items = new ArrayList<Sheet.Item>();
        for (Match m : list) {
            items.add(new Sheet.Item(m.displayTitle(), MainActivity.formatInstant(m.start, "M月d日 HH:mm") + " · " + (Competition.find(m.league) == null ? m.league : Competition.find((String)m.league).name), false));
        }
        Sheet.choose(this, "已订阅的比赛", "点选一场可取消订阅。提醒由系统日历发送。", items, -1, index -> {
            Match m = (Match)list.get(index);
            Sheet.confirm(this, "取消订阅", "移除「" + m.displayTitle() + "」的日历事件和提醒？", "取消订阅", () -> this.unsubscribeAsync(m));
        });
    }

    private void syncSubscriptions(boolean feedback) {
        if (this.isFinishing() || this.isDestroyed()) {
            return;
        }
        if (this.calendarBusy) {
            return;
        }
        if (!this.calendarSync.hasSubscriptions()) {
            if (feedback) {
                Sheet.message(this, "还没有订阅", "在比赛卡上点“订阅”，把单场比赛加入手机日历。");
            }
            return;
        }
        this.calendarBusy = true;
        if (this.tab == 1) {
            this.render(true);
        }
        this.calendarWorker.submit(() -> {
            String text;
            try {
                CalendarSync.Result result = this.calendarSync.sync(Collections.emptyList(), System.currentTimeMillis());
                text = result.message();
            }
            catch (RuntimeException e) {
                text = "检查失败，请检查网络和日历权限后重试。";
            }
            String finalText = text;
            this.handler.post(() -> {
                this.calendarBusy = false;
                if (this.isDestroyed()) {
                    return;
                }
                if (this.active) {
                    this.refreshSubscribed();
                    this.render(true);
                    if (feedback) {
                        Sheet.message(this, "订阅检查结果", finalText + "\n请在系统日历确认提醒已显示。");
                    }
                }
            });
        });
    }

    private void refreshSubscribed() {
        this.subscribed.clear();
        this.subscribed.addAll(this.calendarSync.subscribedIds());
    }

    private String themeName() {
        return switch (this.prefs.getString("theme", "dark")) {
            case "dark" -> "深色";
            case "light" -> "浅色";
            default -> "跟随系统";
        };
    }

    private void themeChoice() {
        String[] values = new String[]{"dark", "light", "system"};
        String[] labels = new String[]{"深色", "浅色", "跟随系统"};
        String current = this.prefs.getString("theme", "dark");
        int selected = 0;
        for (int i = 0; i < values.length; ++i) {
            if (!values[i].equals(current)) continue;
            selected = i;
        }
        ArrayList<Sheet.Item> items = new ArrayList<Sheet.Item>();
        for (int i = 0; i < labels.length; ++i) {
            items.add(new Sheet.Item(labels[i], i == selected ? "当前使用" : "", i == selected));
        }
        Sheet.choose(this, "外观", "跟随系统会随手机的浅色 / 深色模式切换。", items, selected, index -> {
            this.prefs.edit().putString("theme", values[index]).apply();
            this.recreate();
        });
    }

    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != 42) {
            return;
        }
        if (!CalendarSync.permitted((Context)this)) {
            this.pendingSubscription = null;
            Sheet.message(this, "未开启日历权限", "比分查看不受影响。需要订阅时，请在系统应用权限中允许日历读写。");
            return;
        }
        if (this.pendingSubscription != null) {
            this.subscribePending();
        } else {
            this.chooseCalendar();
        }
    }

    private void sourceStatus() {
        Repository.Snapshot source = this.snapshot;
        if (this.pad) {
            source = this.padSourceSnapshot();
            if (source == null) {
                Sheet.message(this, "正在读取来源状态", "来源状态稍后就会读取完成，请再点一次。");
                return;
            }
        }
        StringBuilder content = new StringBuilder();
        for (Repository.Batch b : this.orderedSourceBatches(source)) {
            content.append(b.competition.name).append("\n").append(b.fetched > 0L ? "上次成功 " + MainActivity.formatInstant(b.fetched, "M月d日 HH:mm:ss") + " · " + b.matches.size() + " 场" : "尚未成功获取");
            if (!b.error.isEmpty()) {
                content.append("\n").append(b.error);
            }
            content.append("\n\n");
        }
        content.append("来源：ESPN、5EPlay、英雄联盟赛事官网。数据可能延迟；连接失败时保留最近一次成功记录。已结束的比赛和详情只读取一次，不再自动更新；可点刷新手动核对。");
        Sheet.message(this, "数据来源与连接", content.toString());
    }

    private void navigation() {
        this.nav = this.ui.row();
        this.nav.setBackgroundColor(this.ui.bg);
        this.nav.setPadding(this.ui.dp(20.0f), this.ui.dp(4.0f), this.ui.dp(20.0f), this.ui.dp(4.0f));
        String[] labels = new String[]{"比赛", "设置"};
        String[] icons = new String[]{"matches", "preferences"};
        for (int i = 0; i < 2; ++i) {
            int index = i;
            boolean selected = this.tab == i;
            LinearLayout item = this.ui.row();
            item.setGravity(17);
            item.setMinimumHeight(this.ui.dp(56.0f));
            this.ui.clickable((View)item, 0, 24);
            item.setFocusable(true);
            item.setSelected(selected);
            item.setContentDescription((CharSequence)(labels[i] + (selected ? "，已选中" : "")));
            item.addView((View)new Ui.Icon((Context)this, icons[i], selected ? this.ui.text : this.ui.muted), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(22.0f), this.ui.dp(22.0f)));
            TextView label = this.ui.text(labels[i], 14, selected ? this.ui.text : this.ui.muted, selected);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginStart(this.ui.dp(10.0f));
            item.addView((View)label, (ViewGroup.LayoutParams)lp);
            item.setOnClickListener(v -> this.select(index, this.group, this.dayOffset));
            this.nav.addView((View)item, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        }
        this.root.addView((View)this.nav);
    }

    private void message(String title, String text) {
        if (!this.isFinishing() && !this.isDestroyed()) {
            Sheet.message(this, title, text);
        }
    }

    private static String formatInstant(long millis, String pattern) {
        return Formats.instant(millis, pattern, "未获取");
    }

    private void buildPadScreen() {
        if (this.isFinishing() || this.isDestroyed()) {
            return;
        }
        this.root.removeAllViews();
        this.padList = null;
        this.padAdapter = null;
        this.padFocusScroll = null;
        this.padFocusHost = null;
        this.padRail = null;
        this.railCounts.clear();
        this.railDots.clear();
        this.railFilters.clear();
        this.railLabels.clear();
        this.railBars.clear();
        this.renderedPadFocus = "";
        this.padDayNumber = null;
        this.padDayMeta = null;
        this.padListStatus = null;
        this.padRailDay = null;
        this.padRailWeek = null;
        LinearLayout shell = this.ui.row();
        shell.setBackgroundColor(this.ui.bg);
        this.root.addView((View)shell, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, 0, 1.0f));
        this.padRail = this.padNavigationRail();
        ScrollView railScroll = new ScrollView(this);
        railScroll.setFillViewport(true);
        railScroll.setVerticalScrollBarEnabled(false);
        railScroll.addView(this.padRail);
        shell.addView(railScroll, new LinearLayout.LayoutParams(this.ui.dp(this.padRailWidth()), -1));
        shell.addView(this.verticalRule());
        if (this.tab == 0) {
            this.padScoreWorkspace(shell);
        } else {
            this.padSettingsWorkspace(shell);
        }
        if (this.tab == 0) {
            this.renderPad(true);
        } else {
            this.renderedSettings = this.settingsKey();
            this.updatePadChrome();
        }
    }

    private void renderPad(boolean preserve) {
        if (this.isFinishing() || this.isDestroyed()) {
            return;
        }
        if (this.tab == 1) {
            String key = this.settingsKey();
            if (this.padRail == null || !key.equals(this.renderedSettings)) {
                this.renderedSettings = key;
                this.buildPadScreen();
            }
            return;
        }
        if (this.padRail == null || this.padList == null || this.padAdapter == null) {
            this.buildPadScreen();
            return;
        }
        if (preserve && this.padList.isInteracting()) {
            this.handler.removeCallbacks(this.renderSoon);
            this.handler.postDelayed(this.renderSoon, 250L);
            return;
        }
        if (this.padDetail != null) this.snapshot.updateMatch(this.padDetail.match);
        List<Match> all = this.snapshot.matches();
        this.watches.prepare(all, this::onWatchChanged);
        this.padAdapter.updateRows(this.buildScoreRows(all));
        this.ensurePadSelection(all);
        this.padAdapter.setSelected(this.padSelectedId);
        this.applyPendingPadPosition(all.size());
        this.updatePadChrome();
        this.rebuildPadFocus(preserve);
    }

    private LinearLayout padNavigationRail() {
        LinearLayout rail = this.ui.column();
        rail.setBackgroundColor(this.ui.bg);
        rail.setPadding(this.ui.dp(14.0f), this.ui.dp(22.0f), this.ui.dp(12.0f), this.ui.dp(16.0f));
        LinearLayout brand = this.ui.row();
        brand.setGravity(16);
        brand.setPadding(this.ui.dp(2.0f), 0, 0, 0);
        Ui.Icon mark = new Ui.Icon((Context)this, "mark", this.ui.text);
        brand.addView((View)mark, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(28.0f), this.ui.dp(28.0f)));
        TextView word = this.ui.text("MyScore", 13, this.ui.text, true);
        word.setLetterSpacing(-0.01f);
        LinearLayout.LayoutParams wordParams = new LinearLayout.LayoutParams(-2, -2);
        wordParams.setMarginStart(this.ui.dp(8.0f));
        brand.addView((View)word, (ViewGroup.LayoutParams)wordParams);
        rail.addView((View)brand);
        this.ui.gap(rail, 22);
        rail.addView(this.padRailRule());
        this.ui.gap(rail, 12);
        rail.addView(this.padRailItem("matches", "比赛", this.tab == 0, () -> this.select(0, this.group, this.dayOffset)), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        this.ui.gap(rail, 3);
        rail.addView(this.padRailItem("preferences", "设置", this.tab == 1, () -> this.select(1, this.group, this.dayOffset)), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        this.ui.gap(rail, 18);
        rail.addView(this.padRailRule());
        this.ui.gap(rail, 14);
        TextView caption = this.ui.text(this.tab == 0 ? "赛事筛选" : "跳到赛事", 9, this.ui.muted, true);
        caption.setLetterSpacing(0.12f);
        caption.setPadding(this.ui.dp(8.0f), 0, 0, this.ui.dp(7.0f));
        rail.addView((View)caption);
        for (String g : GROUPS) {
            boolean selected = this.tab == 0 && g.equals(this.group);
            LinearLayout item = this.ui.row();
            item.setMinimumHeight(this.ui.dp(48.0f));
            item.setPadding(this.ui.dp(6.0f), 0, this.ui.dp(6.0f), 0);
            item.setGravity(16);
            this.ui.clickable((View)item, 0, 8);
            item.setFocusable(true);
            item.setSelected(selected);
            View bar = new View((Context)this);
            bar.setBackground(this.ui.shape(this.ui.primary, 2));
            bar.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
            item.addView(bar, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(2.0f), this.ui.dp(16.0f)));
            TextView label = this.ui.text(g, 12, selected ? this.ui.text : this.ui.muted, selected);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
            labelParams.setMarginStart(this.ui.dp(8.0f));
            item.addView((View)label, (ViewGroup.LayoutParams)labelParams);
            View dot = new View((Context)this);
            dot.setBackground(this.ui.shape(this.ui.primary, 4));
            dot.setVisibility(View.GONE);
            this.railDots.put(g, dot);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(this.ui.dp(5.0f), this.ui.dp(5.0f));
            dotParams.setMarginEnd(this.ui.dp(4.0f));
            item.addView(dot, (ViewGroup.LayoutParams)dotParams);
            TextView count = this.ui.text("", 9, this.ui.muted, false);
            count.setGravity(Gravity.END);
            this.railCounts.put(g, count);
            item.addView((View)count, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-2, -2));
            String state = this.tab == 0 ? (selected ? "，已选中" : "，筛选赛事") : "，跳转到赛程";
            item.setContentDescription((CharSequence)(g + state));
            this.railFilters.put(g, item);
            this.railLabels.put(g, label);
            this.railBars.put(g, bar);
            item.setOnClickListener(v -> this.select(0, g, this.dayOffset));
            rail.addView((View)item, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
            this.ui.gap(rail, 1);
        }
        View spacer = new View((Context)this);
        rail.addView(spacer, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(1, 0, 1.0f));
        rail.addView(this.padRailRule());
        this.ui.gap(rail, 12);
        TextView day = this.ui.text(Formats.MONTH_DAY_DOT.format(this.from()), 15, this.ui.text, true);
        day.setPadding(this.ui.dp(4.0f), 0, 0, 0);
        this.padRailDay = day;
        rail.addView((View)day);
        TextView zone = this.ui.text(Formats.WEEKDAY.format(this.from()) + " · 北京时间", 9, this.ui.muted, false);
        zone.setPadding(this.ui.dp(4.0f), 0, 0, 0);
        this.padRailWeek = zone;
        rail.addView((View)zone);
        this.ui.gap(rail, 8);
        TextView version = this.ui.text("MyScore " + BuildConfig.VERSION_NAME, 9, this.ui.muted, false);
        version.setPadding(this.ui.dp(4.0f), 0, 0, 0);
        rail.addView((View)version);
        return rail;
    }

    private View padRailItem(String icon, String label, boolean selected, Runnable action) {
        LinearLayout item = this.ui.row();
        item.setGravity(16);
        item.setMinimumHeight(this.ui.dp(48.0f));
        item.setPadding(this.ui.dp(8.0f), 0, this.ui.dp(8.0f), 0);
        this.ui.clickable((View)item, selected ? this.ui.tonal : 0, 12);
        item.setFocusable(true);
        item.setSelected(selected);
        Ui.Icon glyph = new Ui.Icon((Context)this, icon, selected ? this.ui.text : this.ui.muted);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(this.ui.dp(19.0f), this.ui.dp(19.0f));
        iconParams.setMarginStart(this.ui.dp(8.0f));
        item.addView((View)glyph, (ViewGroup.LayoutParams)iconParams);
        TextView text = this.ui.text(label, 13, selected ? this.ui.text : this.ui.muted, selected);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-2, -2);
        textParams.setMarginStart(this.ui.dp(9.0f));
        item.addView((View)text, (ViewGroup.LayoutParams)textParams);
        item.setContentDescription((CharSequence)(label + (selected ? "，已选中" : "")));
        item.setOnClickListener(v -> action.run());
        return item;
    }

    private View padRailRule() {
        View line = new View((Context)this);
        line.setBackgroundColor(this.ui.line);
        line.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, this.ui.dp(1.0f)));
        return line;
    }

    private void padScoreWorkspace(LinearLayout shell) {
        int screen = this.ui.c.getResources().getConfiguration().screenWidthDp;
        int available = Math.max(0, screen - this.padRailWidth());
        boolean stacked = available < 850;
        LinearLayout workspace = stacked ? this.ui.column() : this.ui.row();
        shell.addView(workspace, new LinearLayout.LayoutParams(0, -1, 1));
        int listDp = Math.min(480, Math.max(360, Math.round((float)available * 0.36f)));
        LinearLayout listPanel = this.ui.column();
        listPanel.setBackgroundColor(this.ui.bg);
        workspace.addView(listPanel, stacked ? new LinearLayout.LayoutParams(-1, 0, 1) : new LinearLayout.LayoutParams(this.ui.dp(listDp), -1));
        LinearLayout header = this.ui.column();
        header.setPadding(this.ui.dp(22.0f), this.ui.dp(18.0f), this.ui.dp(16.0f), this.ui.dp(6.0f));
        LinearLayout titleRow = this.ui.row();
        TextView title = this.ui.text("赛程", 26, this.ui.text, true);
        title.setLetterSpacing(-0.03f);
        titleRow.addView((View)title, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        titleRow.addView((View)this.padIconAction("refresh", "刷新比赛", () -> this.load(true)));
        header.addView((View)titleRow);
        this.ui.gap(header, 10);
        LinearLayout dateRow = this.ui.row();
        this.padDayNumber = this.ui.text(String.valueOf(this.from().getDayOfMonth()), 38, this.ui.text, true);
        this.padDayNumber.setLetterSpacing(-0.05f);
        this.padDayNumber.setIncludeFontPadding(false);
        dateRow.addView((View)this.padDayNumber, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-2, -2));
        LinearLayout dateWords = this.ui.column();
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        wp.setMarginStart(this.ui.dp(10.0f));
        this.padDayMeta = this.ui.text("", 11, this.ui.muted, false);
        dateWords.addView((View)this.padDayMeta);
        this.ui.gap(dateWords, 3);
        TextView dateHint = this.ui.text("北京时间", 10, this.ui.muted, false);
        dateWords.addView((View)dateHint);
        dateRow.addView((View)dateWords, (ViewGroup.LayoutParams)wp);
        if (this.dayOffset != 0) {
            TextView today = this.ui.chip("今天", false, () -> this.selectDay(Match.today(System.currentTimeMillis())));
            today.setTextSize(11.0f);
            today.setMinHeight(this.ui.dp(38.0f));
            today.setPadding(this.ui.dp(10.0f), 0, this.ui.dp(10.0f), 0);
            dateRow.addView((View)today);
        }
        FrameLayout prev = this.padIconAction("chevron", "前一天", () -> this.selectDay(this.from().minusDays(1L)));
        prev.getChildAt(0).setRotation(180.0f);
        dateRow.addView((View)prev);
        dateRow.addView((View)this.padIconAction("chevron", "后一天", () -> this.selectDay(this.from().plusDays(1L))));
        dateRow.addView((View)this.padIconAction("calendar", "选择日期", () -> Sheet.date(this, this.from(), this::selectDay)));
        header.addView((View)dateRow);
        this.ui.gap(header, 4);
        this.padListStatus = this.ui.text("", 11, this.ui.muted, false);
        header.addView((View)this.padListStatus);
        listPanel.addView((View)header);
        this.padList = new ScoreList((Context)this);
        this.padList.setPadding(this.ui.dp(16.0f), this.ui.dp(4.0f), this.ui.dp(16.0f), this.ui.dp(24.0f));
        this.padList.setClipToPadding(false);
        this.padList.setVerticalScrollBarEnabled(false);
        this.padAdapter = new PadScoreAdapter(this.ui, new PadScoreAdapter.Host(){

            @Override
            public void selectMatch(Match match) {
                if (match != null) {
                    MainActivity.this.selectPadMatch(match);
                }
            }

            @Override
            public void openMatch(Match match) {
                MainActivity.this.matchDetail(match);
            }

            @Override
            public void openTeam(Match match, boolean home) {
                MainActivity.this.openTeam(match, home);
            }

            @Override
            public void toggleSubscription(Match match) {
                MainActivity.this.toggleSubscription(match);
            }

            @Override
            public void openWatch(Match match) {
                MainActivity.this.openWatch(match);
            }

            @Override
            public WatchLinks.Link watchLink(Match match) {
                return MainActivity.this.watches.link(match);
            }

            @Override
            public boolean isSubscribed(String id) {
                return MainActivity.this.subscribed.contains(id);
            }

            @Override
            public void showSourceStatus() {
                MainActivity.this.sourceStatus();
            }
        });
        this.padAdapter.attach(this.padList);
        this.padList.setAdapter((ListAdapter)this.padAdapter);
        listPanel.addView((View)this.padList, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, 0, 1.0f));
        workspace.addView(stacked ? this.ui.divider() : this.verticalRule());
        this.padFocusScroll = new Ui.ReadingScroll(this);
        this.padFocusScroll.setFillViewport(true);
        this.padFocusScroll.setVerticalScrollBarEnabled(false);
        this.padFocusScroll.setClipToPadding(false);
        this.padFocusHost = this.ui.column();
        this.padFocusHost.setPadding(this.ui.dp(stacked ? 20 : 36), this.ui.dp(28.0f), this.ui.dp(stacked ? 20 : 40), this.ui.dp(40.0f));
        this.padFocusScroll.addView((View)this.padFocusHost);
        workspace.addView(this.padFocusScroll, stacked ? new LinearLayout.LayoutParams(-1, 0, 1) : new LinearLayout.LayoutParams(0, -1, 1));
    }

    private int padRailWidth() {
        return Math.round(128 * Math.min(1.5f, Math.max(1f, getResources().getConfiguration().fontScale)));
    }

    private FrameLayout padIconAction(String kind, String description, Runnable click) {
        FrameLayout button = new FrameLayout((Context)this);
        button.addView((View)new Ui.Icon((Context)this, kind, this.ui.text), (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(this.ui.dp(20.0f), this.ui.dp(20.0f), 17));
        this.ui.clickable((View)button, 0, 24);
        button.setContentDescription((CharSequence)description);
        button.setFocusable(true);
        button.setOnClickListener(v -> click.run());
        button.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(48.0f), this.ui.dp(48.0f)));
        return button;
    }

    private void selectPadMatch(Match match) {
        if (match == null) {
            return;
        }
        if (match.id.equals(this.padSelectedId)) {
            return;
        }
        this.padSelectedId = match.id;
        this.padSelected = match;
        this.padDetail = null;
        this.padError = "";
        ++this.padFocusVersion;
        if (this.padFocusRequest != null) {
            this.padFocusRequest.cancel(true);
            this.padFocusRequest = null;
        }
        this.padFocusBusy = false;
        if (this.padAdapter != null) {
            this.padAdapter.setSelected(this.padSelectedId);
        }
        this.rebuildPadFocus(false);
        this.loadPadDetail(match, false);
        this.watches.resolve(match, this::onPadWatchChanged);
    }

    private void ensurePadSelection(List<Match> all) {
        Match next = null;
        if (!this.padSelectedId.isBlank()) {
            for (Match m : all) {
                if (!m.id.equals(this.padSelectedId)) continue;
                next = m;
                break;
            }
        }
        // During rotation or a cold cache read the saved match is known before the list is.
        // Keep that pending id instead of replacing it with a default and losing the user's choice.
        if (next == null && !this.padSelectedId.isBlank() && all.isEmpty()) {
            return;
        }
        if (next == null) {
            for (Match m : all) {
                if (!m.live()) continue;
                next = m;
                break;
            }
        }
        if (next == null) {
            for (Match m : all) {
                if (!m.scheduled() && !m.state.equals("delayed")) continue;
                next = m;
                break;
            }
        }
        if (next == null && !all.isEmpty()) {
            next = all.get(0);
        }
        String nextId = next == null ? "" : next.id;
        boolean changed = !nextId.equals(this.padSelectedId);
        this.padSelectedId = nextId;
        this.padSelected = next;
        if (changed) {
            this.padDetail = null;
            this.padError = "";
            ++this.padFocusVersion;
            if (this.padFocusRequest != null) {
                this.padFocusRequest.cancel(true);
                this.padFocusRequest = null;
            }
            this.padFocusBusy = false;
        } else if (this.padDetail != null && !this.padDetail.match.id.equals(nextId)) {
            this.padDetail = null;
            this.padError = "";
            ++this.padFocusVersion;
        }
        if (next == null) {
            return;
        }
        boolean stale = this.padDetail != null
            && !RefreshPolicy.freshDetail(next, this.padDetail.match, this.padDetail.fetched, System.currentTimeMillis());
        if ((this.padDetail == null || stale) && !this.padFocusBusy) {
            this.loadPadDetail(next, false);
        }
        this.watches.resolve(next, this::onPadWatchChanged);
    }

    private void loadPadDetail(Match match, boolean force) {
        long version = ++this.padFocusVersion;
        this.padFocusBusy = true;
        this.padError = "";
        if (this.padFocusRequest != null) {
            this.padFocusRequest.cancel(true);
        }
        this.padFocusRequest = this.focusWorker.submit(() -> {
            MatchDetail cached = this.detailRepository.cached(match);
            this.handler.post(() -> {
                if (this.isDestroyed() || this.isFinishing() || version != this.padFocusVersion || this.padSelected == null || !match.id.equals(this.padSelected.id)) {
                    return;
                }
                if (cached != null) {
                    this.padDetail = cached;
                    boolean updated = this.snapshot.updateMatch(cached.match);
                    if (this.active) {
                        if (updated) this.renderPad(true); else this.rebuildPadFocus(true);
                    }
                    this.enrichPadProfiles(cached);
                }
            });
            MatchDetail next = null;
            String failure = "";
            try {
                next = this.detailRepository.fetch(match, force);
            }
            catch (Exception e) {
                failure = e.getMessage() == null ? "网络连接失败" : e.getMessage();
            }
            MatchDetail result = next;
            String problem = failure;
            this.handler.post(() -> {
                if (this.isDestroyed() || this.isFinishing() || version != this.padFocusVersion || this.padSelected == null || !match.id.equals(this.padSelected.id)) {
                    return;
                }
                this.padFocusBusy = false;
                boolean updated = false;
                if (result != null) {
                    this.padDetail = result;
                    if (result.match.updated >= this.padSelected.updated) this.padSelected = result.match;
                    updated = this.snapshot.updateMatch(result.match);
                    this.padSelectedId = result.match.id;
                    this.padError = "";
                } else {
                    this.padError = problem;
                }
                if (this.active) {
                    if (updated) this.renderPad(true); else this.rebuildPadFocus(true);
                }
                this.enrichPadProfiles(result != null ? result : this.padDetail);
            });
        });
    }

    private void enrichPadProfiles(MatchDetail target) {
        if (target == null) return;
        this.detailRepository.enrichProfiles(target, enriched -> this.handler.post(() -> {
            if (!this.active || this.isDestroyed() || this.isFinishing()) return;
            if (this.padSelected == null || enriched == target) return;
            if (!this.padSelected.id.equals(enriched.match.id)) return;
            if (this.padDetail != target && (this.padDetail == null || enriched.fetched != this.padDetail.fetched)) return;
            this.padDetail = enriched;
            if (enriched.match.updated >= this.padSelected.updated) this.padSelected = enriched.match;
            this.rebuildPadFocus(true);
        }));
    }

    private void rebuildPadFocus(boolean preserve) {
        if (this.padFocusHost == null) {
            return;
        }
        if (preserve && this.padFocusScroll != null && this.padFocusScroll.isInteracting()) {
            this.handler.removeCallbacks(this.renderSoon);
            this.handler.postDelayed(this.renderSoon, 250L);
            return;
        }
        String key = this.padFocusKey();
        // A live poll usually carries no visible change. Reusing the existing reading pane keeps
        // the 96dp crests and the whole detail card tree instead of rebuilding it every ten seconds.
        if (preserve && key.equals(this.renderedPadFocus)) {
            this.updatePadFocusVolatileText();
            if (BuildConfig.DEBUG) {
                Log.i("MyScorePerf", "pad focus unchanged; skip rebuild");
            }
            return;
        }
        this.renderedPadFocus = key;
        long started = SystemClock.uptimeMillis();
        int y = preserve && this.padFocusScroll != null ? this.padFocusScroll.getScrollY() : 0;
        if (this.pendingPadFocusScroll >= 0 && this.padDetail != null) {
            y = this.pendingPadFocusScroll;
            this.pendingPadFocusScroll = -1;
        }
        this.padFocusHost.removeAllViews();
        this.padFocusInto(this.padFocusHost);
        if (this.padFocusScroll != null) {
            if (!preserve) this.padFocusScroll.fling(0);
            int restoreY = y;
            Ui.ReadingScroll target = this.padFocusScroll;
            long version = this.padFocusVersion;
            target.post(() -> {if (target == this.padFocusScroll && version == this.padFocusVersion) target.scrollTo(0, restoreY);});
        }
        this.updatePadFocusVolatileText();
        if (BuildConfig.DEBUG) {
            Log.i("MyScorePerf", "pad focus rebuilt in " + (SystemClock.uptimeMillis() - started) + "ms");
        }
    }

    /**
     * Everything the reading pane renders, at the granularity it is shown. Fetch times are
     * included at minute precision because that is what the pane displays; second-by-second
     * refreshes must not tear down the view tree. Scores, states and detail content stay exact.
     */
    private String padFocusKey() {
        StringBuilder key = new StringBuilder(192);
        Match m = this.padSelected;
        key.append(this.group).append('|').append(this.dayOffset).append('|').append(this.ui.c.getResources().getConfiguration().screenWidthDp >= 1280 ? 2 : 1);
        if (m == null) {
            return key.append("|none|").append(this.snapshot.matches().isEmpty()).append('|').append(this.busy || this.selectionLoading).append('|').append(this.snapshot.failed()).toString();
        }
        boolean canSubscribe = m.scheduled() && m.timeConfirmed && m.start > System.currentTimeMillis();
        key.append('|').append(m.id).append(',').append(m.state).append(',').append(m.homeScore).append(',').append(m.awayScore)
            .append(',').append(m.start).append(',').append(m.timeConfirmed)
            .append(',').append(m.source)
            .append(',').append(m.home).append(',').append(m.away).append(',').append(m.title).append(',').append(m.url)
            .append(',').append(m.homeLogo).append(',').append(m.awayLogo)
            .append(',').append(this.subscribed.contains(m.id)).append(',').append(canSubscribe);
        key.append('|').append(this.watches.signature(m));
        key.append('|').append(this.padError);
        MatchDetail detail = this.padDetail;
        if (detail == null) {
            key.append("|detail:none");
        } else {
            key.append('|').append(detail.bodySignature()).append(',').append(detail.source);
        }
        List<Match> all = this.snapshot.matches();
        key.append("|related:").append(all.size());
        int shown = 0;
        for (Match other : all) {
            if (other.id.equals(m.id)) continue;
            if (shown++ >= 4) break;
            key.append(';').append(other.id).append(',').append(other.state).append(',').append(other.homeScore)
                .append(',').append(other.awayScore).append(',').append(other.start);
        }
        return key.toString();
    }

    private String padF1Meta(Match m) {
        return m.status + " · " + (m.timeConfirmed ? MainActivity.formatInstant(m.start, "M月d日 HH:mm") : "时间待定");
    }
    private String padNarrowMeta(Match m) {
        return m.status + " · " + (m.timeConfirmed ? MainActivity.formatInstant(m.start, "M月d日 HH:mm") + " · 北京时间" : "时间待定");
    }
    private String padUpdatedText(Match m) {
        return m.source + (m.updated > 0L ? " · 更新于 " + MainActivity.formatInstant(m.updated, "HH:mm") : "");
    }
    private String padFetchedText(Match m, MatchDetail d) {
        return "获取于 " + MainActivity.formatInstant(d.fetched, "M月d日 HH:mm")
            + (m.updated > 0L ? "\n来源发布 " + MainActivity.formatInstant(m.updated, "M月d日 HH:mm") : "");
    }
    /**
     * Live clocks update every poll but do not change the reading pane's structure. Keep those
     * labels in place instead of tearing the whole pane down each time.
     */
    private void updatePadFocusVolatileText() {
        if (this.padFocusHost == null || this.padSelected == null) return;
        Match m = this.padSelected;
        setPadFocusText("pad-focus-status", m.status);
        setPadFocusText("pad-focus-f1-meta", padF1Meta(m));
        setPadFocusText("pad-focus-narrow-meta", padNarrowMeta(m));
        setPadFocusText("pad-focus-updated", padUpdatedText(m));
        if (this.padDetail != null) {
            setPadFocusText("pad-focus-fetched", padFetchedText(m, this.padDetail));
        }
    }
    private void setPadFocusText(String tag, String value) {
        View view = this.padFocusHost.findViewWithTag(tag);
        if (view instanceof TextView && !value.contentEquals(((TextView)view).getText())) {
            ((TextView)view).setText(value);
        }
    }

    private void padFocusInto(LinearLayout host) {
        Match m = this.padSelected;
        if (m == null) {
            LinearLayout empty = this.ui.column();
            empty.setGravity(1);
            empty.setPadding(0, this.ui.dp(96.0f), 0, 0);
            empty.addView((View)new Ui.Icon((Context)this, "mark", this.ui.line), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(72.0f), this.ui.dp(72.0f)));
            this.ui.gap(empty, 28);
            boolean loading = this.busy || this.selectionLoading;
            TextView title = this.ui.text(loading ? "正在读取赛程" : this.snapshot.failed() ? "赛程暂未完整获取" : "选择一场比赛", 30, this.ui.text, true);
            title.setLetterSpacing(-0.03f);
            title.setGravity(17);
            empty.addView((View)title);
            this.ui.gap(empty, 10);
            TextView copy = this.ui.text(loading ? "正在读取所选日期与赛事，请稍候。" : this.snapshot.failed() ? "部分来源暂时无法连接，请刷新重试或查看来源状态。" : this.snapshot.matches().isEmpty() ? (this.group.equals("全部") ? "这个日期还没有已确认的比赛。" : "当前赛事在这个日期还没有已确认的比赛，切回「全部」或换个赛事。") : "从左侧赛程选择一场比赛，在这里查看比分、数据与进程。", 14, this.ui.muted, false);
            copy.setGravity(17);
            copy.setLineSpacing((float)this.ui.dp(4.0f), 1.0f);
            empty.addView((View)copy);
            host.addView((View)empty);
            return;
        }
        Competition competition = Competition.find(m.league);
        LinearLayout eyebrow = this.ui.row();
        TextView kicker = this.ui.text((competition == null ? m.group : competition.name) + "  /  " + Formats.MONTH_DAY_WEEK.format(m.day() == null ? this.from() : m.day()), 11, this.ui.muted, true);
        kicker.setLetterSpacing(0.06f);
        eyebrow.addView((View)kicker, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView updated = this.ui.text(padUpdatedText(m), 11, this.ui.muted, false);
        updated.setTag("pad-focus-updated");
        eyebrow.addView((View)updated);
        host.addView((View)eyebrow);
        this.ui.gap(host, 12);
        host.addView((View)this.padHero(m));
        this.ui.gap(host, 24);
        if (this.padDetail == null) {
            LinearLayout loading = this.padSurface();
            LinearLayout line = this.ui.row();
            line.addView((View)this.ui.text(this.padError.isEmpty() ? "正在读取比赛数据…" : "比赛数据暂不可用", 15, this.padError.isEmpty() ? this.ui.text : this.ui.error, true), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
            if (!this.padError.isEmpty()) {
                line.addView((View)this.ui.button("重试", false, () -> this.loadPadDetail(m, true)));
            }
            loading.addView((View)line);
            if (!this.padError.isEmpty()) {
                this.ui.gap(loading, 6);
                loading.addView((View)this.ui.text(this.padError, 12, this.ui.muted, false));
            }
            host.addView((View)loading);
            return;
        }
        MatchDetail d = this.padDetail;
        if (!this.padError.isEmpty()) {
            LinearLayout warning = this.padSurface();
            warning.addView((View)this.ui.text("暂未更新 · " + this.padError + "\n显示上次获取的数据", 12, this.ui.error, false));
            host.addView((View)warning);
            this.ui.gap(host, 16);
        }
        if (!d.warning.isBlank()) {
            TextView note = this.ui.text(d.warning, 12, this.ui.muted, false);
            note.setLineSpacing((float)this.ui.dp(3.0f), 1.0f);
            host.addView((View)note);
            this.ui.gap(host, 16);
        }
        boolean twoColumns = this.ui.c.getResources().getConfiguration().screenWidthDp >= 1280;
        LinearLayout left = this.ui.column();
        LinearLayout right = this.ui.column();
        if (twoColumns) {
            LinearLayout columns = this.ui.row();
            columns.setGravity(48);
            columns.addView((View)left, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
            View gap = new View((Context)this);
            columns.addView(gap, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(28.0f), 1));
            columns.addView((View)right, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
            this.padDetailColumns(left, right, d, m);
            host.addView((View)columns);
        } else {
            this.padDetailColumns(left, right, d, m);
            host.addView((View)left);
            this.ui.gap(host, 24);
            host.addView((View)right);
        }
        this.padRelated(host, m);
    }

    private void padRelated(LinearLayout host, Match selected) {
        List<Match> all = this.snapshot.matches();
        ArrayList<Match> others = new ArrayList<Match>();
        for (Match m : all) {
            if (m.id.equals(selected.id)) continue;
            others.add(m);
        }
        if (others.isEmpty()) {
            return;
        }
        this.ui.gap(host, 30);
        LinearLayout head = this.ui.row();
        head.addView((View)this.ui.text("同日其他比赛", 15, this.ui.text, true), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        head.addView((View)this.ui.text(Math.min(4, others.size()) + " / " + others.size() + " 场", 11, this.ui.muted, false));
        host.addView((View)head);
        this.ui.gap(host, 10);
        LinearLayout card = this.padSurface();
        card.setPadding(this.ui.dp(8.0f), this.ui.dp(4.0f), this.ui.dp(8.0f), this.ui.dp(4.0f));
        int shown = Math.min(4, others.size());
        for (int i = 0; i < shown; ++i) {
            Match m = (Match)others.get(i);
            LinearLayout row = this.ui.row();
            row.setPadding(this.ui.dp(10.0f), this.ui.dp(9.0f), this.ui.dp(10.0f), this.ui.dp(9.0f));
            row.setMinimumHeight(this.ui.dp(54.0f));
            this.ui.clickable((View)row, 0, 12);
            row.setFocusable(true);
            LinearLayout time = this.ui.column();
            time.addView((View)this.ui.text(m.time(), 12, this.ui.text, true));
            time.addView((View)this.ui.text(Rules.stateLabel(m.state), 9, m.live() ? this.ui.primary : this.ui.muted, false));
            row.addView((View)time, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(62.0f), -2));
            LinearLayout teams = this.ui.column();
            Competition comp = Competition.find(m.league);
            teams.addView((View)this.ui.text((comp == null ? m.group : comp.name) + " · " + m.home, 11, this.ui.text, true));
            this.ui.gap(teams, 2);
            teams.addView((View)this.ui.text(m.away, 11, this.ui.muted, false));
            row.addView((View)teams, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
            TextView score = this.ui.text(m.hasScore() ? m.homeScore + " – " + m.awayScore : "VS", 14, this.ui.text, true);
            score.setGravity(0x800005);
            row.addView((View)score, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-2, -2));
            row.setContentDescription((CharSequence)(m.displayTitle() + "，" + Rules.stateLabel(m.state) + (m.hasScore() ? "，比分 " + m.homeScore + " 比 " + m.awayScore : "")));
            row.setOnClickListener(v -> this.selectPadMatch(m));
            card.addView((View)row);
            if (i >= shown - 1) continue;
            card.addView(this.ui.divider());
        }
        host.addView((View)card);
    }

    private LinearLayout padHero(Match m) {
        boolean canSubscribe;
        boolean narrow = this.ui.c.getResources().getConfiguration().screenWidthDp < 1280;
        LinearLayout card = this.padSurface();
        card.setPadding(this.ui.dp(narrow ? 20.0f : 28.0f), this.ui.dp(narrow ? 18.0f : 24.0f), this.ui.dp(narrow ? 20.0f : 28.0f), this.ui.dp(narrow ? 16.0f : 22.0f));
        if (m.group.equals("F1")) {
            TextView title = this.ui.text(m.displayTitle(), 32, this.ui.text, true);
            title.setLetterSpacing(-0.03f);
            card.addView((View)title);
            this.ui.gap(card, 8);
            TextView f1Meta = this.ui.text(padF1Meta(m), 13, m.live() ? this.ui.primary : this.ui.muted, false);
            f1Meta.setTag("pad-focus-f1-meta");
            card.addView((View)f1Meta);
        } else if (narrow) {
            LinearLayout stacked = this.ui.column();
            stacked.addView((View)this.padHeroTeamRow(m, true), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
            this.ui.gap(stacked, 2);
            stacked.addView(this.ui.divider());
            this.ui.gap(stacked, 2);
            stacked.addView((View)this.padHeroTeamRow(m, false), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
            this.ui.gap(stacked, 12);
            TextView meta = this.ui.text(padNarrowMeta(m), 11, m.live() ? this.ui.primary : this.ui.muted, false);
            meta.setTag("pad-focus-narrow-meta");
            meta.setGravity(17);
            stacked.addView((View)meta);
            card.addView((View)stacked);
        } else {
            LinearLayout row = this.ui.row();
            row.setGravity(17);
            row.addView((View)this.padHeroTeam(m, true), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
            LinearLayout center = this.ui.column();
            center.setGravity(17);
            Ui.ScoreText score = this.ui.score(m.homeScore, m.awayScore, m.hasScore(), 64);
            score.setResultColors(this.ui.scoreColor(m, true), this.ui.scoreColor(m, false));
            center.addView((View)score, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
            this.ui.gap(center, 10);
            TextView state = this.ui.text(m.status, 14, m.live() ? this.ui.primary : this.ui.muted, true);
            state.setTag("pad-focus-status");
            state.setGravity(17);
            center.addView((View)state);
            this.ui.gap(center, 4);
            TextView when = this.ui.text(m.timeConfirmed ? MainActivity.formatInstant(m.start, "M月d日 HH:mm") + " · 北京时间" : "时间待定", 11, this.ui.muted, false);
            when.setGravity(17);
            center.addView((View)when);
            row.addView((View)center, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(220.0f), -2));
            row.addView((View)this.padHeroTeam(m, false), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
            card.addView((View)row);
        }
        this.ui.gap(card, 20);
        card.addView(this.ui.divider());
        WatchLinks.Link watch = this.watches.link(m);
        if (watch != null) {
            this.ui.gap(card, 6);
            Ui.WatchRow row = this.ui.watchRow();
            row.bind(watch, m.live(), () -> this.openWatch(m));
            card.addView((View)row);
            this.ui.gap(card, 10);
        } else {
            this.ui.gap(card, 16);
        }
        LinearLayout actions = this.ui.row();
        TextView open = this.ui.button("比赛详情", true, () -> this.matchDetail(m));
        actions.addView((View)open, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.3f));
        boolean isSubscribed = this.subscribed.contains(m.id);
        canSubscribe = m.scheduled() && m.timeConfirmed && m.start > System.currentTimeMillis();
        if (isSubscribed || canSubscribe) {
            View gap = new View((Context)this);
            actions.addView(gap, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(10.0f), 1));
            FrameLayout subscribe = this.padIconAction(isSubscribed ? "calendar-check" : "calendar-add", isSubscribed ? "取消比赛提醒" : "订阅比赛提醒", () -> this.toggleSubscription(m));
            subscribe.setSelected(isSubscribed);
            subscribe.setTooltipText(isSubscribed ? "取消提醒" : "订阅提醒");
            ((Ui.Icon)subscribe.getChildAt(0)).set(isSubscribed ? "calendar-check" : "calendar-add", isSubscribed ? this.ui.primary : this.ui.text);
            actions.addView(subscribe, new LinearLayout.LayoutParams(this.ui.dp(48), this.ui.dp(48)));
        }
        View gap2 = new View((Context)this);
        actions.addView(gap2, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(10.0f), 1));
        actions.addView((View)this.padIconAction("refresh", "刷新比赛数据", () -> this.loadPadDetail(m, true)));
        card.addView((View)actions);
        return card;
    }

    private LinearLayout padHeroTeamRow(Match m, boolean home) {
        LinearLayout row = this.ui.row();
        row.setMinimumHeight(this.ui.dp(64.0f));
        String name = home ? m.home : m.away;
        String logo = home ? m.homeLogo : m.awayLogo;
        String id = home ? m.homeId : m.awayId;
        FrameLayout slot = new FrameLayout((Context)this);
        slot.addView((View)Logos.view(this.ui, logo, name, 56), (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(this.ui.dp(56.0f), this.ui.dp(56.0f), 17));
        boolean available = id != null && !id.isBlank();
        slot.setFocusable(available);
        slot.setClickable(available);
        slot.setContentDescription(available ? "查看 " + name + " 的赛程" : null);
        slot.setOnClickListener(available ? v -> this.openTeam(m, home) : null);
        row.addView((View)slot, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(64.0f), this.ui.dp(64.0f)));
        TextView label = this.ui.text(name, 18, this.ui.text, true);
        label.setLetterSpacing(-0.02f);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        labelParams.setMarginStart(this.ui.dp(8.0f));
        row.addView((View)label, (ViewGroup.LayoutParams)labelParams);
        TextView score = this.ui.text(m.hasScore() ? (home ? m.homeScore : m.awayScore) : "—", 34, this.ui.scoreColor(m, home), true);
        score.setGravity(Gravity.END);
        score.setIncludeFontPadding(false);
        row.addView((View)score);
        row.setContentDescription((CharSequence)(name + (m.hasScore() ? "，比分 " + (home ? m.homeScore : m.awayScore) : "，比分尚未提供")));
        return row;
    }

    private LinearLayout padHeroTeam(Match m, boolean home) {
        LinearLayout side = this.ui.column();
        side.setGravity(17);
        String name = home ? m.home : m.away;
        String logo = home ? m.homeLogo : m.awayLogo;
        String id = home ? m.homeId : m.awayId;
        FrameLayout slot = new FrameLayout((Context)this);
        slot.addView((View)Logos.view(this.ui, logo, name, 96), (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(this.ui.dp(96.0f), this.ui.dp(96.0f), 17));
        boolean available = id != null && !id.isBlank();
        slot.setFocusable(available);
        slot.setClickable(available);
        slot.setContentDescription(available ? "查看 " + name + " 的赛程" : null);
        slot.setOnClickListener(available ? v -> this.openTeam(m, home) : null);
        side.addView((View)slot, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(112.0f), this.ui.dp(104.0f)));
        this.ui.gap(side, 10);
        TextView label = this.ui.text(name, 22, this.ui.text, true);
        label.setGravity(17);
        label.setLetterSpacing(-0.02f);
        side.addView((View)label, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        return side;
    }

    private void padDetailColumns(LinearLayout left, LinearLayout right, MatchDetail d, Match m) {
        LinearLayout card;
        if (!d.stats.isEmpty()) {
            this.padFocusHeading(left, "数据速览");
            card = this.padSurface();
            LinearLayout names = this.ui.row();
            TextView home = this.ui.text(m.home, 11, this.ui.text, true);
            TextView away = this.ui.text(m.away, 11, this.ui.text, true);
            away.setGravity(0x800005);
            names.addView((View)home, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
            names.addView((View)away, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
            card.addView((View)names);
            this.ui.gap(card, 8);
            card.addView(this.ui.divider());
            for (int i = 0; i < Math.min(6, d.stats.size()); ++i) {
                this.padFocusStat(card, d.stats.get(i));
                if (i >= Math.min(6, d.stats.size()) - 1) continue;
                card.addView(this.ui.divider());
            }
            left.addView((View)card);
            this.ui.gap(left, 24);
        }
        if (!d.facts.isEmpty()) {
            this.padFocusHeading(left, "比赛信息");
            card = this.padSurface();
            LinkedHashMap<String, List> facts = new LinkedHashMap<String, List>();
            for (MatchDetail.Fact f : d.facts) {
                String label = DetailParsers.label(DetailParsers.plain(f.label()));
                if (label.equals("阵容资料版本")) continue;
                String value = DetailParsers.plain(f.value());
                if (value.isBlank()) continue;
                facts.computeIfAbsent(label.isBlank() ? "信息" : label, k -> new ArrayList()).add(value);
            }
            int index = 0;
            for (Map.Entry fact : facts.entrySet()) {
                if (index++ >= 6) break;
                if (index > 1) {
                    card.addView(this.ui.divider());
                }
                LinearLayout row = this.ui.row();
                row.setGravity(48);
                row.setPadding(0, this.ui.dp(10.0f), 0, this.ui.dp(10.0f));
                row.addView((View)this.ui.text((String)fact.getKey(), 11, this.ui.muted, false), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
                TextView value = this.ui.text(String.join((CharSequence)"\n", new LinkedHashSet((Collection)fact.getValue())), 12, this.ui.text, false);
                value.setLineSpacing((float)this.ui.dp(3.0f), 1.0f);
                row.addView((View)value, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.5f));
                card.addView((View)row);
            }
            if (index == 0) {
                card.addView((View)this.ui.text("来源尚未提供更多比赛信息", 12, this.ui.muted, false));
            }
            left.addView((View)card);
            this.ui.gap(left, 24);
        }
        if (!d.events.isEmpty()) {
            this.padFocusHeading(right, "最新进程");
            card = this.padSurface();
            for (int i = 0; i < Math.min(5, d.events.size()); ++i) {
                this.padFocusEvent(card, d.events.get(i));
                if (i >= Math.min(5, d.events.size()) - 1) continue;
                card.addView(this.ui.divider());
            }
            right.addView((View)card);
            this.ui.gap(right, 24);
        }
        this.padFocusHeading(right, "数据来源");
        LinearLayout source = this.padSurface();
        source.addView((View)this.ui.text(d.source, 13, this.ui.text, true));
        this.ui.gap(source, 4);
        source.addView((View)this.ui.text(padFetchedText(m, d), 11, this.ui.muted, false));
        right.addView((View)source);
        if (!m.url.isBlank()) {
            this.ui.gap(right, 10);
            right.addView((View)this.ui.button("打开来源页面", false, () -> {
                try {
                    this.startActivity(new Intent("android.intent.action.VIEW", Uri.parse((String)m.url)));
                }
                catch (RuntimeException e) {
                    this.message("无法打开来源", "手机没有可用的浏览器。");
                }
            }), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void padFocusHeading(LinearLayout into, String title) {
        into.addView((View)this.ui.text(title, 15, this.ui.text, true));
        this.ui.gap(into, 10);
    }

    private void padFocusStat(LinearLayout card, MatchDetail.Stat stat) {
        LinearLayout row = this.ui.row();
        row.setPadding(0, this.ui.dp(10.0f), 0, this.ui.dp(10.0f));
        TextView home = this.ui.text(stat.home(), 14, this.ui.primary, true);
        TextView label = this.ui.text(stat.label(), 11, this.ui.muted, false);
        TextView away = this.ui.text(stat.away(), 14, this.ui.secondary, true);
        label.setGravity(17);
        away.setGravity(0x800005);
        row.addView((View)home, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        row.addView((View)label, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.4f));
        row.addView((View)away, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        card.addView((View)row);
        double h = MainActivity.numeric(stat.home());
        double a = MainActivity.numeric(stat.away());
        if (h >= 0.0 && a >= 0.0 && h + a > 0.0) {
            LinearLayout track = this.ui.row();
            View left = new View((Context)this);
            View right = new View((Context)this);
            left.setBackground((Drawable)this.ui.shape(this.ui.primary, 2));
            right.setBackground((Drawable)this.ui.shape(this.ui.secondary, 2));
            track.addView(left, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, this.ui.dp(3.0f), (float)h));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, this.ui.dp(3.0f), (float)a);
            rp.setMarginStart(this.ui.dp(4.0f));
            track.addView(right, (ViewGroup.LayoutParams)rp);
            card.addView((View)track);
        }
    }

    private void padFocusEvent(LinearLayout card, MatchDetail.Event e) {
        LinearLayout row = this.ui.row();
        row.setGravity(48);
        row.setPadding(0, this.ui.dp(10.0f), 0, this.ui.dp(10.0f));
        TextView clock = this.ui.text(e.time(), 11, this.ui.muted, true);
        row.addView((View)clock, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(48.0f), -2));
        LinearLayout words = this.ui.column();
        String title = DetailParsers.plain(e.title());
        String text = DetailParsers.plain(e.text());
        if (!title.isBlank()) {
            words.addView((View)this.ui.text(title, 12, this.ui.text, true));
            if (!text.isBlank()) {
                this.ui.gap(words, 3);
            }
        }
        if (!text.isBlank()) {
            words.addView((View)this.ui.text(text, 12, title.isBlank() ? this.ui.text : this.ui.muted, false));
        }
        row.addView((View)words, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        card.addView((View)row);
    }

    private LinearLayout padSurface() {
        LinearLayout card = this.ui.column();
        card.setBackground((Drawable)this.ui.panel());
        card.setPadding(this.ui.dp(18.0f), this.ui.dp(16.0f), this.ui.dp(18.0f), this.ui.dp(16.0f));
        return card;
    }

    private void updatePadChrome() {
        String updated;
        List<Match> all = this.snapshot.matches();
        Repository.Snapshot rememberedAll = this.daySnapshots.get(MainActivity.dayKey(this.from(), "全部"));
        boolean allSnapshotReady = (this.allSnapshot != null && this.allSnapshotDay != null && this.allSnapshotDay.equals(this.from())) || rememberedAll != null;
        boolean cachedAllReady = this.padSettingSources != null && this.padSettingSourcesDay != null && this.padSettingSourcesDay.equals(this.from());
        Repository.Snapshot countSource = allSnapshotReady
            ? (this.allSnapshot != null && this.from().equals(this.allSnapshotDay) ? this.allSnapshot : rememberedAll)
            : (cachedAllReady ? this.padSettingSources : null);
        boolean complete = allSourcesKnown(countSource);
        Repository.Snapshot countSnapshot = countSource != null ? countSource : this.snapshot;
        if (!complete && !this.padSettingSourcesLoading) {
            this.padSourceSnapshot();
        }
        List<Match> countedAll = countSnapshot.matches();
        long latest = 0L;
        for (Repository.Batch b : this.snapshot.batches) {
            latest = Math.max(latest, b.fetched);
        }
        updated = latest > 0L ? "更新于 " + MainActivity.formatInstant(latest, "HH:mm") : "正在读取";
        if (this.padListStatus != null) {
            String text = all.size() + " 场比赛 · " + (this.group.equals("全部") ? "全部赛事" : this.group) + " · " + updated;
            this.padListStatus.setText((CharSequence)text);
            boolean failed = this.snapshot.failed();
            this.padListStatus.setTextColor(failed ? this.ui.error : this.ui.muted);
        }
        if (this.padDayNumber != null) {
            this.padDayNumber.setText((CharSequence)String.valueOf(this.from().getDayOfMonth()));
            String month = Formats.YEAR_MONTH.format(this.from());
            String week = Formats.WEEKDAY.format(this.from());
            this.padDayMeta.setText((CharSequence)(month + " · " + week + (this.dayOffset == 0 ? " · 今天" : "")));
        }
        if (this.padRailDay != null) {
            this.padRailDay.setText((CharSequence)Formats.MONTH_DAY_DOT.format(this.from()));
        }
        if (this.padRailWeek != null) {
            this.padRailWeek.setText((CharSequence)(Formats.WEEKDAY.format(this.from()) + " · 北京时间"));
        }
        for (String g : GROUPS) {
            int c;
            boolean selected = this.tab == 0 && g.equals(this.group);
            View filter = this.railFilters.get(g);
            if (filter != null) {
                filter.setSelected(selected);
                filter.setContentDescription(g + (this.tab == 0 ? selected ? "，已选中" : "，筛选赛事" : "，跳转到赛程"));
                this.railBars.get(g).setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
                TextView label = this.railLabels.get(g);
                label.setTextColor(selected ? this.ui.text : this.ui.muted);
                label.setTypeface(label.getTypeface(), selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            }
            TextView count = this.railCounts.get(g);
            if (count == null) continue;
            View dot = this.railDots.get(g);
            boolean current = g.equals(this.group);
            // Unknown sources must read "—", not "0 场"; the latter looks like a confirmed empty day.
            if (!complete && !(current && this.groupSourcesKnown(g))) {
                count.setText((CharSequence)"—");
                count.setTextColor(this.ui.muted);
                if (dot == null) continue;
                dot.setVisibility(View.GONE);
                continue;
            }
            List<Match> groupMatches = current && !"全部".equals(g) ? all : countedAll;
            int live = 0;
            if (g.equals("全部")) {
                c = groupMatches.size();
                for (Match m : groupMatches) {
                    if (!m.live()) continue;
                    ++live;
                }
            } else {
                c = 0;
                for (Match m : groupMatches) {
                    if (!m.group.equals(g)) continue;
                    ++c;
                    if (!m.live()) continue;
                    ++live;
                }
            }
            count.setText((CharSequence)String.valueOf(c));
            count.setTextColor(live > 0 ? this.ui.primary : (this.tab == 0 && g.equals(this.group) ? this.ui.text : this.ui.muted));
            if (dot == null) continue;
            dot.setVisibility(live > 0 ? View.VISIBLE : View.GONE);
        }
        this.updatePadFocusVolatileText();
    }

    /** True only when every configured source has produced at least one successful read. */
    private static boolean allSourcesKnown(Repository.Snapshot source) {
        if (source == null || source.batches.isEmpty()) return false;
        for (Competition competition : Competition.ALL) {
            boolean known = false;
            for (Repository.Batch batch : source.batches) {
                if (!batch.competition.id.equals(competition.id)) continue;
                known = batch.fetched > 0 || !batch.matches.isEmpty();
                break;
            }
            if (!known) return false;
        }
        return true;
    }

    private boolean groupSourcesKnown(String group) {
        for (Competition competition : Competition.ALL) {
            if (!group.equals("全部") && !competition.group.equals(group)) continue;
            boolean known = false;
            for (Repository.Batch batch : this.snapshot.batches) if (batch.competition.id.equals(competition.id)) {known = batch.fetched > 0;break;}
            if (!known) return false;
        }
        return true;
    }

    private void padSettingsWorkspace(LinearLayout shell) {
        Repository.Snapshot sourceSnapshot = this.padSourceSnapshot();
        boolean stacked = getResources().getConfiguration().screenWidthDp - this.padRailWidth() < 850 || getResources().getConfiguration().fontScale >= 1.5f;
        ScrollView scroll = new ScrollView((Context)this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout host = this.ui.column();
        host.setPadding(this.ui.dp(stacked ? 24 : 48), this.ui.dp(34.0f), this.ui.dp(stacked ? 24 : 48), this.ui.dp(48.0f));
        scroll.addView((View)host);
        shell.addView((View)scroll, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -1, 1.0f));
        LinearLayout titleRow = this.ui.row();
        TextView title = this.ui.text("设置", 36, this.ui.text, true);
        title.setLetterSpacing(-0.04f);
        titleRow.addView((View)title, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView version = this.ui.text("MyScore " + BuildConfig.VERSION_NAME, 11, this.ui.muted, false);
        titleRow.addView((View)version);
        host.addView((View)titleRow);
        this.ui.gap(host, 6);
        host.addView((View)this.ui.text("管理日历订阅、外观与数据来源", 13, this.ui.muted, false));
        this.ui.gap(host, 26);
        long checked = this.prefs.getLong("calendar_checked", 0L);
        LinearLayout overview = this.ui.row();
        overview.setGravity(16);
        overview.setBackground((Drawable)this.ui.panel());
        overview.setClipToOutline(true);
        overview.addView((View)this.padSettingsOverviewCell("手机日历", this.calendarName(), "提前 5 分钟提醒", false, this::calendarChoice), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.05f));
        overview.addView(this.padSettingsRule(), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(1.0f), this.ui.dp(64.0f)));
        overview.addView((View)this.padSettingsOverviewCell("已订阅比赛", this.calendarSync.linkedEvents() + " 场", checked > 0L ? "上次检查 " + MainActivity.formatInstant(checked, "M月d日 HH:mm") : "单场订阅 · 提前 5 分钟提醒", false, this::subscribedList), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 0.95f));
        overview.addView(this.padSettingsRule(), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(1.0f), this.ui.dp(64.0f)));
        String sourceValue = sourceSnapshot == null ? "读取中" : !allSourcesKnown(sourceSnapshot) ? "部分读取" : (sourceSnapshot.failed() ? "部分未更新" : "全部已读取");
        String sourceDetail = sourceSnapshot == null ? "正在读取来源状态" : sourceSnapshot.batches.size() + " 个来源 · " + this.latestSourceLabel(sourceSnapshot);
        overview.addView((View)this.padSettingsOverviewCell("数据来源", sourceValue, sourceDetail, sourceSnapshot != null && sourceSnapshot.failed(), this::sourceStatus), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        if (stacked) {
            overview.setOrientation(LinearLayout.VERTICAL);
            for (int i=0;i<overview.getChildCount();i++) overview.getChildAt(i).setLayoutParams(new LinearLayout.LayoutParams(-1, i%2==0 ? -2 : this.ui.dp(1)));
        }
        host.addView((View)overview);
        this.ui.gap(host, 30);
        LinearLayout columns = this.ui.row();
        columns.setGravity(48);
        LinearLayout left = this.ui.column();
        LinearLayout right = this.ui.column();
        columns.addView((View)left, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.15f));
        View gap = new View((Context)this);
        columns.addView(gap, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(28.0f), 1));
        columns.addView((View)right, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        if (stacked) {
            columns.setOrientation(LinearLayout.VERTICAL);
            left.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            right.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            gap.setLayoutParams(new LinearLayout.LayoutParams(1, this.ui.dp(28)));
        }
        host.addView((View)columns);
        this.padSettingsHeading(left, "单场订阅");
        LinearLayout calendar = this.ui.column();
        calendar.setBackground((Drawable)this.ui.panel());
        this.settingRow(calendar, "calendar-target", "接收日历", this.calendarName(), this::calendarChoice);
        this.settingRow(calendar, "subscriptions", "已订阅的比赛", this.calendarSync.linkedEvents() + " 场 · 提前 5 分钟提醒", this::subscribedList);
        this.settingRow(calendar, "sync-subscriptions", this.calendarBusy ? "正在检查…" : "检查已订阅比赛", checked > 0L ? "上次检查 " + MainActivity.formatInstant(checked, "M月d日 HH:mm") : "同步改期与取消", () -> this.syncSubscriptions(true));
        this.settingRow(calendar, "calendar-open", "打开系统日历", "查看日程与提醒", this::openCalendar);
        left.addView((View)calendar);
        this.ui.gap(left, 28);
        this.padSettingsHeading(left, "数据来源");
        LinearLayout sources = this.ui.column();
        sources.setBackground((Drawable)this.ui.panel());
        List<Repository.Batch> orderedSources = this.orderedSourceBatches(sourceSnapshot);
        if (sourceSnapshot == null || orderedSources.isEmpty()) {
            sources.addView((View)this.padEmptySetting(sourceSnapshot == null ? "正在读取来源状态…" : "尚未读取来源"));
        } else {
            for (int i = 0; i < orderedSources.size(); ++i) {
                Repository.Batch b = orderedSources.get(i);
                LinearLayout row = this.ui.row();
                row.setPadding(this.ui.dp(16.0f), this.ui.dp(12.0f), this.ui.dp(16.0f), this.ui.dp(12.0f));
                View dot = new View((Context)this);
                dot.setBackground((Drawable)this.ui.shape(b.error.isEmpty() ? this.ui.primary : this.ui.error, 4));
                row.addView(dot, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(6.0f), this.ui.dp(6.0f)));
                TextView name = this.ui.text(b.competition.name, 13, this.ui.text, true);
                LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1.0f);
                np.setMarginStart(this.ui.dp(10.0f));
                row.addView((View)name, (ViewGroup.LayoutParams)np);
                String state = b.error.isEmpty() ? (b.fetched > 0L ? MainActivity.formatInstant(b.fetched, "HH:mm") + " · " + b.matches.size() + " 场" : "尚未获取") : b.error;
                TextView value = this.ui.text(state, 11, b.error.isEmpty() ? this.ui.muted : this.ui.error, false);
                value.setGravity(0x800005);
                row.addView((View)value);
                sources.addView((View)row);
                if (i == orderedSources.size() - 1) continue;
                sources.addView(this.ui.divider());
            }
        }
        left.addView((View)sources);
        this.ui.gap(left, 28);
        this.padSettingsHeading(right, "外观");
        LinearLayout appearance = this.ui.column();
        appearance.setBackground((Drawable)this.ui.panel());
        appearance.setPadding(this.ui.dp(16.0f), this.ui.dp(16.0f), this.ui.dp(16.0f), this.ui.dp(16.0f));
        appearance.addView((View)this.ui.text("跟随系统会随平板浅色 / 深色模式切换。", 12, this.ui.muted, false));
        this.ui.gap(appearance, 12);
        String theme = this.prefs.getString("theme", "dark");
        LinearLayout themeRow = this.ui.row();
        String[] values = new String[]{"dark", "light", "system"};
        String[] labels = new String[]{"深色", "浅色", "跟随系统"};
        for (int i = 0; i < values.length; ++i) {
            String value = values[i];
            boolean on = value.equals(theme);
            TextView chip = this.ui.chip(labels[i], on, () -> {
                this.prefs.edit().putString("theme", value).apply();
                this.recreate();
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1.0f);
            if (i > 0) {
                cp.setMarginStart(this.ui.dp(8.0f));
            }
            themeRow.addView((View)chip, (ViewGroup.LayoutParams)cp);
        }
        appearance.addView((View)themeRow);
        right.addView((View)appearance);
        this.ui.gap(right, 28);
        this.padSettingsHeading(right, "偏好与帮助");
        LinearLayout options = this.ui.column();
        options.setBackground((Drawable)this.ui.panel());
        this.settingRow(options, "app-update", "检查更新", "当前版本 " + BuildConfig.VERSION_NAME, () -> this.startActivity(new Intent((Context)this, UpdateActivity.class)));
        this.settingRow(options, "sources", "数据来源与连接", sourceSnapshot != null && sourceSnapshot.failed() ? "部分来源未更新" : "查看来源状态", this::sourceStatus);
        this.settingRow(options, "help", "使用帮助", "日历提醒与数据说明", this::help);
        right.addView((View)options);
        this.ui.gap(right, 28);
        this.padSettingsHeading(right, "关于");
        LinearLayout about = this.padSurface();
        about.addView((View)new Ui.Icon((Context)this, "mark", this.ui.text), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(32.0f), this.ui.dp(32.0f)));
        this.ui.gap(about, 10);
        about.addView((View)this.ui.text("MyScore " + BuildConfig.VERSION_NAME, 18, this.ui.text, true));
        this.ui.gap(about, 6);
        about.addView((View)this.ui.text("原生 Android · 无广告 · 无登录 · 北京时间\n赛程与比分来自公开来源，失败时保留真实缓存。", 11, this.ui.muted, false));
        right.addView((View)about);
        this.ui.gap(host, 34);
        TextView foot = this.ui.text("平板布局 · 三栏赛程工作台 · 比分、详情与来源一屏可达", 11, this.ui.muted, false);
        foot.setGravity(17);
        host.addView((View)foot);
    }

    private LinearLayout padSettingsOverviewCell(String label, String value, String detail, boolean error, Runnable action) {
        LinearLayout cell = this.ui.column();
        cell.setPadding(this.ui.dp(20.0f), this.ui.dp(15.0f), this.ui.dp(16.0f), this.ui.dp(15.0f));
        this.ui.clickable((View)cell, 0, 0);
        cell.setFocusable(true);
        LinearLayout top = this.ui.row();
        TextView title = this.ui.text(label, 10, this.ui.muted, true);
        top.addView((View)title, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        top.addView((View)new Ui.Icon((Context)this, "chevron", this.ui.muted), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(13.0f), this.ui.dp(13.0f)));
        cell.addView((View)top);
        this.ui.gap(cell, 7);
        TextView big = this.ui.text(value, 19, error ? this.ui.error : this.ui.text, true);
        big.setSingleLine(false);
        cell.addView((View)big);
        this.ui.gap(cell, 4);
        cell.addView((View)this.ui.text(detail, 10, this.ui.muted, false));
        cell.setContentDescription((CharSequence)(label + "，" + value + "，" + detail));
        cell.setOnClickListener(v -> action.run());
        return cell;
    }

    private View padSettingsRule() {
        View line = new View((Context)this);
        line.setBackgroundColor(this.ui.line);
        return line;
    }

    private List<Repository.Batch> orderedSourceBatches(Repository.Snapshot source) {
        List<Repository.Batch> ordered = new ArrayList<Repository.Batch>();
        if (source == null) {
            return ordered;
        }
        for (Competition competition : Competition.ALL) {
            for (Repository.Batch batch : source.batches) {
                if (batch.competition.id.equals(competition.id)) {
                    ordered.add(batch);
                    break;
                }
            }
        }
        return ordered;
    }

    /** The settings page reports every source, independent of the score-list filter. */
    private Repository.Snapshot padSourceSnapshot() {        LocalDate day = this.from();
        Repository.Snapshot remembered = this.daySnapshots.get(MainActivity.dayKey(day, "全部"));
        if (remembered != null) {
            return remembered;
        }
        if (this.allSnapshot != null && day.equals(this.allSnapshotDay)) {
            return this.allSnapshot;
        }
        if (this.padSettingSources != null && day.equals(this.padSettingSourcesDay)) {
            return this.padSettingSources;
        }
        if (!this.padSettingSourcesLoading) {
            this.padSettingSourcesLoading = true;
            this.cacheWorker.submit(() -> {
                Repository.Snapshot cached = this.repository.cached(day, day, "全部");
                this.handler.post(() -> {
                    if (this.isDestroyed()) {
                        return;
                    }
                    this.padSettingSourcesLoading = false;
                    this.padSettingSources = cached;
                    this.padSettingSourcesDay = day;
                    this.renderedSettings = "";
                    if (this.active && this.tab == 1) {
                        this.buildPadScreen();
                    } else if (this.active && this.tab == 0) {
                        this.updatePadChrome();
                    }
                });
            });
        }
        return null;
    }

    private String latestSourceLabel(Repository.Snapshot source) {
        if (source == null) {
            return "等待来源";
        }
        long latest = 0L;
        for (Repository.Batch b : source.batches) {
            latest = Math.max(latest, b.fetched);
        }
        return latest > 0L ? MainActivity.formatInstant(latest, "HH:mm") + " 更新" : "等待刷新";
    }

    private void padSettingsHeading(LinearLayout into, String title) {
        into.addView((View)this.ui.text(title, 13, this.ui.muted, true));
        this.ui.gap(into, 10);
    }

    private TextView padEmptySetting(String text) {
        TextView value = this.ui.text(text, 12, this.ui.muted, false);
        value.setPadding(this.ui.dp(16.0f), this.ui.dp(16.0f), this.ui.dp(16.0f), this.ui.dp(16.0f));
        return value;
    }

    private View verticalRule() {
        View line = new View((Context)this);
        line.setBackgroundColor(this.ui.line);
        line.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(1.0f), -1));
        return line;
    }

    private static double numeric(String value) {
        try {
            return value.matches("[0-9]+(?:\\.[0-9]+)?%?") ? Double.parseDouble(value.replace("%", "")) : -1.0;
        }
        catch (RuntimeException e) {
            return -1.0;
        }
    }
}
