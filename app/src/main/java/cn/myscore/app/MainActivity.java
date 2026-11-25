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
    private View cs2EventsAction;
    private LinearLayout filterTabs;
    private LinearLayout dateSelectorView;
    private LinearLayout feedBarView;
    private LinearLayout headerLivePill;
    private TextView headerLiveText;
    private Ui.Icon headerGroupIcon;
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
    private String lens = ScoreFeedOrganizer.LENS_ALL;
    private String groupMode = ScoreFeedOrganizer.GROUP_COMPETITION;
    private boolean active = false;
    private boolean busy = false;
    private boolean calendarBusy = false;
    private boolean calendarQuerying = false;
    private boolean pending = false;
    private boolean cacheReady = false;
    private boolean pendingForce;
    private boolean selectionLoading;
    private long selectionVersion;
    private long scoreVersion;
    private Future<?> scoreRequest;
    private Future<?> selectionCacheRequest;
    private LocalDate loadedDay;
    private CalendarSync calendarSync;
    private final Set<String> subscribed = new HashSet<String>();
    private final Runnable calendarChanged = () -> {
        if (this.isDestroyed() || this.isFinishing()) return;
        this.refreshSubscribed();
        this.renderedSettings = "";
        if (this.active || this.getWindow().getDecorView().getWindowVisibility() == View.VISIBLE) {
            this.render(true);
        }
        if (this.active && this.hasWindowFocus() && !this.subscribed.isEmpty()) {
            ResultNotify.requestIfNeeded(this);
        }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener calendarListener = (preferences, key) -> {
        if (key == null || key.startsWith("match:")) {
            this.handler.removeCallbacks(this.calendarChanged);
            this.handler.postDelayed(this.calendarChanged, 32L);
        }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener cloudListener = (preferences, key) -> {
        this.handler.post(() -> {
            if (this.active && this.tab == 1 && !this.isDestroyed()) {
                this.renderedSettings = "";
                this.render(true);
            }
        });
    };
    private Match pendingSubscription;
    private boolean pendingChooseCalendar;
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
    private boolean partialRenderQueued;
    private final Runnable renderPartial = () -> {
        this.partialRenderQueued = false;
        this.renderSoon.run();
    };
    private final Runnable renderSoon = () -> {
        if (this.active && !this.isDestroyed()) {
            this.render(true);
        }
    };
    private static final String[] GROUPS = new String[]{"全部", "足球", "NBA", "NFL", "F1", "CS2"};
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
    private AutoCloseable networkWatch = () -> {};

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
        this.getSharedPreferences("calendar-links", 0).registerOnSharedPreferenceChangeListener(this.calendarListener);
        this.getSharedPreferences(CalendarCloudSync.STORE, 0).registerOnSharedPreferenceChangeListener(this.cloudListener);
        this.detailRepository = new DetailRepository((Context)this);
        this.watches = WatchRepository.get(this);
        this.watchLauncher = new WatchLauncher(this, this.watches);
        this.pad = Ui.isPad((Context)this);
        this.groupMode = this.prefs.getString("feed_group_mode", ScoreFeedOrganizer.GROUP_COMPETITION);
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
            if (!java.util.Arrays.asList(GROUPS).contains(this.group)) this.group = "全部";
            this.lens = state.getString("lens", ScoreFeedOrganizer.LENS_ALL);
            this.padSelectedId = state.getString("pad_selected", "");
            this.pendingScorePosition = state.getInt("score_position", -1);
            this.pendingPadPosition = state.getInt("pad_position", -1);
            this.pendingPadFocusScroll = state.getInt("pad_focus_scroll", -1);
            String pending = state.getString("pending_subscription", "");
            if (!pending.isEmpty()) {
                try {
                    this.pendingSubscription = Match.fromJson(new JSONObject(pending));
                    if (Competition.find(this.pendingSubscription.league) == null) this.pendingSubscription = null;
                } catch (Exception ignored) {
                    this.pendingSubscription = null;
                }
            }
        }
        this.getWindow().setStatusBarColor(this.ui.bg);
        this.getWindow().setNavigationBarColor(this.ui.navBg);
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
        String initialTag = this.queryTag();
        this.cacheWorker.submit(() -> {
            Repository.Snapshot cached = this.repository.cached(initialDay, initialDay, "全部");
            this.handler.post(() -> {
                if (this.isDestroyed()) {
                    return;
                }
                this.cacheReady = true;
                if (initialTag.equals(this.queryTag())) {
                    this.busy = false;
                    this.rememberAll("全部", initialDay, cached);
                    this.snapshot = subset(cached, this.group);
                    if (this.tab != 0 || !this.snapshot.matches().isEmpty()) this.render(true);
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
        b.putString("lens", this.lens);
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
            this.requestStartupPermissionsIfNeeded();
        }
    }

    private void requestStartupPermissionsIfNeeded() {
        if (!this.active || this.isFinishing() || this.isDestroyed() || !this.hasWindowFocus()) {
            return;
        }
        if (android.app.ActivityManager.isUserAMonkey() || "ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE)) {
            return;
        }
        ResultNotify.ensureChannel(this);
        boolean needCalendar = !CalendarSync.permitted((Context)this) && !this.prefs.getBoolean("asked_calendar_startup", false);
        SharedPreferences notifyPrefs = this.getSharedPreferences("result-notifications", 0);
        boolean needNotify = Build.VERSION.SDK_INT >= 33
            && this.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            && !notifyPrefs.getBoolean("asked_permission", false)
            && (needCalendar || !this.subscribed.isEmpty());
        if (!needCalendar && !needNotify) {
            return;
        }
        ArrayList<String> perms = new ArrayList<String>(3);
        if (needCalendar) {
            this.prefs.edit().putBoolean("asked_calendar_startup", true).apply();
            perms.add("android.permission.READ_CALENDAR");
            perms.add("android.permission.WRITE_CALENDAR");
        }
        if (needNotify) {
            notifyPrefs.edit().putBoolean("asked_permission", true).apply();
            perms.add("android.permission.POST_NOTIFICATIONS");
        }
        this.requestPermissions(perms.toArray(new String[0]), 44);
    }

    public void onResume() {
        super.onResume();
        this.active = true;
        this.closeNetworkWatch();
        this.networkWatch = Http.onNetworkChanged(() -> this.handler.post(() -> {
            if (!this.active || this.isDestroyed()) {
                return;
            }
            this.renderedSettings = "";
            if (this.tab == 1) {
                this.render(true);
            }
            if (this.snapshot.failed()) {
                this.load(true);
            }
        }));
        CalendarUpdates.start(this);
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
        this.closeNetworkWatch();
        this.handler.removeCallbacks(this.tick);
        this.handler.removeCallbacks(this.renderSoon);
        this.cancelPartialRender();
        this.handler.removeCallbacks(this.settleLoad);
        this.cancelPrefetch();
        super.onPause();
    }

    private void closeNetworkWatch() {
        try {
            this.networkWatch.close();
        } catch (Exception ignored) {}
        this.networkWatch = () -> {};
    }

    public void onDestroy() {
        this.active = false;
        this.getSharedPreferences("calendar-links", 0).unregisterOnSharedPreferenceChangeListener(this.calendarListener);
        this.getSharedPreferences(CalendarCloudSync.STORE, 0).unregisterOnSharedPreferenceChangeListener(this.cloudListener);
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
        long version = this.scoreVersion;
        LocalDate f = this.from();
        LocalDate t = this.to();
        long started = System.currentTimeMillis();
        java.util.concurrent.atomic.AtomicBoolean firstBatch = new java.util.concurrent.atomic.AtomicBoolean(true);
        this.scoreRequest = this.executor.submit(() -> {
            Repository.Snapshot result;
            try {
                result = this.repository.fetch(f, t, "全部", forced, batch -> this.handler.post(() -> {
                    if (this.isDestroyed() || version != this.scoreVersion) {
                        return;
                    }
                    if (BuildConfig.DEBUG && firstBatch.compareAndSet(true, false)) {
                        Log.i("MyScorePerf", "first source batch in " + (System.currentTimeMillis() - started) + "ms");
                    }
                    Repository.Snapshot all = this.snapshotFor(f, "全部");
                    all = all == null ? new Repository.Snapshot() : subset(all, "全部");
                    all.merge(batch);
                    this.rememberAll("全部", f, all);
                    this.snapshot = subset(all, this.group);
                    if (!this.partialRenderQueued) {
                        this.partialRenderQueued = true;
                        this.handler.postDelayed(this.renderPartial, 32L);
                    }
                }));
            }
            catch (RuntimeException e) {
                Log.w((String)"MyScore", (String)"Score refresh interrupted", (Throwable)e);
                result = this.repository.cached(f, t, "全部");
                for (Repository.Batch batch2 : result.batches) {
                    if (!batch2.error.isEmpty()) continue;
                    batch2.error = "Refresh was interrupted; the last data was kept";
                }
            }
            Repository.Snapshot next = result;
            this.handler.post(() -> {
                if (this.isDestroyed() || version != this.scoreVersion) {
                    return;
                }
                this.busy = false;
                Log.i((String)"MyScorePerf", (String)("all sources in " + (System.currentTimeMillis() - started) + "ms"));
                this.handler.removeCallbacks(this.renderSoon);
                this.cancelPartialRender();
                this.rememberAll("全部", f, next);
                this.snapshot = subset(next, this.group);
                if (this.active) {
                    this.render(true);
                }
                if (this.active) {
                    if (this.pending) {
                        this.load(false);
                        return;
                    }
                    long now = System.currentTimeMillis();
                    long delay = RefreshPolicy.pollDelay(next.batches, f, Match.today(now), now);
                    long midnight = RefreshPolicy.untilNextDay(now);
                    this.handler.postDelayed(this.tick, delay > 0L ? Math.min(delay, midnight) : midnight);
                    this.schedulePrefetch(f, this.group);
                }
            });
        });
    }

    private void cancelPartialRender() {
        this.partialRenderQueued = false;
        this.handler.removeCallbacks(this.renderPartial);
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
        this.cancelPartialRender();
        this.handler.removeCallbacks(this.settleLoad);
        this.cancelPrefetch();
        boolean keepScoreRequest = !force && nextTab == 0 && this.tab == 0 && offset == this.dayOffset && this.busy && this.scoreRequest != null && !this.scoreRequest.isDone();
        ++this.selectionVersion;
        if (!keepScoreRequest) ++this.scoreVersion;
        if (!keepScoreRequest && this.scoreRequest != null) {
            this.scoreRequest.cancel(true);
        }
        this.busy = keepScoreRequest;
        this.pending = false;
        if (!keepScoreRequest) this.pendingForce = false;
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
            Repository.Snapshot cached = this.repository.cached(day, day, "全部");
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
                Repository.Snapshot memory = this.snapshotFor(day, "全部");
                for (Repository.Batch batch : memory == null ? this.snapshot.batches : memory.batches) {
                    Repository.Batch disk = cached.batches.stream().filter(b -> b.competition.id.equals(batch.competition.id)).findFirst().orElse(null);
                    if (disk != null && disk.fetched > batch.fetched) continue;
                    cached.merge(batch);
                }
                this.rememberAll("全部", day, cached);
                this.snapshot = subset(cached, this.group);
                // Let a quick run of arrow taps settle first; only the final day pays for a
                // network round instead of every intermediate selection.
                if (!this.busy) this.scheduleLoad(150L);
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
        Repository.Snapshot everything = this.daySnapshots.get(MainActivity.dayKey(day, "全部"));
        if (everything != null) {
            return MainActivity.subset(everything, group);
        }
        Repository.Snapshot exact = this.daySnapshots.get(MainActivity.dayKey(day, group));
        if (exact != null) return exact;
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
        this.updateCs2EventsAction();
        this.refreshFilterChips();
        if (!this.from().equals(this.headerDay) && this.dateSelectorView != null) {
            int index = this.headerView.indexOfChild(this.dateSelectorView);
            if (index >= 0) {
                this.headerView.removeView(this.dateSelectorView);
                this.dateSelectorView = this.dateSelector();
                this.headerView.addView(this.dateSelectorView, index);
                this.headerView.post(this::syncHeaderHeight);
            }
        }
        this.refreshFeedBar();
        this.headerView.setTranslationY(0.0f);
    }

    private void refreshFilterChips() {
        if (this.filterTabs == null) return;
        for (int i = 0; i < this.filterTabs.getChildCount() && i < GROUPS.length; i++) {
            TextView item = (TextView) this.filterTabs.getChildAt(i);
            String g = GROUPS[i];
            boolean selected = g.equals(this.group);
            int accent = "全部".equals(g) ? this.ui.action : this.ui.sportAccent(null, g);
            int activeInk = "全部".equals(g) ? this.ui.onAction : android.graphics.Color.WHITE;
            item.setSelected(selected);
            item.setTextColor(selected ? activeInk : this.ui.muted);
            item.setTypeface(item.getTypeface(), selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            android.graphics.drawable.GradientDrawable bg = selected
                ? this.ui.shape(accent, Ui.PILL_RADIUS)
                : this.ui.outlined(this.ui.tonal, this.ui.border, Ui.PILL_RADIUS);
            this.ui.clickableDrawable(item, bg, Ui.PILL_RADIUS);
            item.setContentDescription(EnglishDisplay.sport(g) + (selected ? ", selected" : ", filter sport"));
        }
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
            this.scoreList.setPadding(this.ui.dp(16.0f), this.ui.dp(136.0f), this.ui.dp(16.0f), this.ui.dp(20.0f));
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
            this.dateSelectorView = this.dateSelector();
            this.headerView.addView(this.dateSelectorView);
            this.filters(this.headerView);
            this.feedBarView = this.ui.row();
            this.feedBarView.setPadding(this.ui.dp(16.0f), this.ui.dp(2.0f), this.ui.dp(16.0f), this.ui.dp(8.0f));
            this.headerView.addView(this.feedBarView);
            View events = this.cs2EventsAction();
            events.setPadding(this.ui.dp(16.0f), 0, this.ui.dp(16.0f), this.ui.dp(6.0f));
            this.cs2EventsAction = events;
            this.headerView.addView(events);
            this.updateCs2EventsAction();
            this.refreshFeedBar();
        }
        this.headerView.addView(this.ui.divider());
        this.stage.addView((View)this.headerView, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -2, 48));
        this.headerHeight = this.measureHeader();
        this.headerOffset = 0;
        this.headerView.setTranslationY(0.0f);
        if (scores) {
            this.scoreList.setPadding(this.ui.dp(16.0f), this.headerHeight + this.ui.dp(4.0f), this.ui.dp(16.0f), this.ui.dp(24.0f));
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
            int left = this.ui.dp(16.0f), bottom = this.ui.dp(20.0f);
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
        this.refreshFeedBar();
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
        key.append(this.group).append('|').append(this.lens).append('|').append(this.groupMode).append('|').append(this.tab).append('|').append((this.busy || this.selectionLoading) && all.isEmpty()).append('|').append(this.snapshot.failed());
        for (Repository.Batch batch : this.snapshot.batches) {
            key.append('|').append(batch.competition.id).append(':').append(batch.error);
        }
        for (Match m : all) {
            key.append('|').append(m.id).append(',').append(m.league).append(',').append(m.group).append(',').append(m.home).append(',').append(m.away).append(',').append(m.homeRecord).append(',').append(m.awayRecord).append(',').append(m.displayScore(true)).append(',').append(m.displayScore(false)).append(',').append(m.state).append(',').append(m.status).append(',').append(m.start).append(',').append(m.timeConfirmed).append(',').append(m.title).append(',').append(m.homeId).append(',').append(m.awayId).append(',').append(m.homeLogo).append(',').append(m.awayLogo).append(',').append(this.subscribed.contains(m.id)).append(',').append(m.live() && now - m.updated > 120000L).append(',').append(m.source).append(',').append(m.updated / 60000L);
            WatchLinks.Link watch = this.watches.link(m);
            key.append(',').append(watch == null ? "" : watch.url).append(',').append(watch == null ? "" : watch.title);
            for (String result : m.results) {
                key.append(',').append(result);
            }
        }
        return key.toString();
    }

    private List<ScoreRow> buildScoreRows(List<Match> all) {
        ArrayList<ScoreRow> rows = new ArrayList<ScoreRow>();
        List<Match> visible = ScoreFeedOrganizer.filter(all, this.group, this.lens, this.subscribed);
        if (visible.isEmpty()) {
            if (all.isEmpty() && (this.busy || this.selectionLoading)) {
                rows.add(ScoreRow.loading("Syncing live fixtures…"));
                return rows;
            }
            if (this.snapshot.failed()) {
                int failures = 0;
                for (Repository.Batch batch : this.snapshot.batches) {
                    if (!batch.error.isEmpty()) ++failures;
                }
                rows.add(ScoreRow.failure(failureSummary(failures)));
            }
            if (ScoreFeedOrganizer.LENS_LIVE.equals(this.lens)) {
                rows.add(ScoreRow.empty("No live matches right now", "Nothing is currently in play for this selection.", "Show all matches"));
                return rows;
            }
            if (ScoreFeedOrganizer.LENS_SAVED.equals(this.lens)) {
                rows.add(ScoreRow.empty("No saved matches on this day", "Tap Remind on any upcoming match card to track it here and in your calendar.", "Show all matches"));
                return rows;
            }
            rows.add(this.snapshot.failed()
                ? ScoreRow.empty("Schedule unavailable", "Couldn't connect, so matches can't be confirmed yet.", "Try again")
                : ScoreRow.empty(this.group.equals("全部") ? "No matches on this day" : "No " + EnglishDisplay.sport(this.group) + " matches on this day",
                    EnglishDisplay.text(Formats.MONTH_DAY_WEEK.format(this.from())) + " — no confirmed fixtures.\n" + (this.group.equals("全部") ? "Pick another day from the date strip above." : "Try another sport or date."),
                    this.group.equals("全部") ? "Choose date" : "All sports"));
            return rows;
        }
        if (this.snapshot.failed()) {
            int failures = 0;
            for (Repository.Batch batch : this.snapshot.batches) {
                if (!batch.error.isEmpty()) ++failures;
            }
            rows.add(ScoreRow.failure(failureSummary(failures)));
        }
        if (ScoreFeedOrganizer.GROUP_COMPETITION.equals(this.groupMode)) {
            List<ScoreFeedOrganizer.CompetitionGroup> groups = ScoreFeedOrganizer.groupByCompetition(visible);
            for (ScoreFeedOrganizer.CompetitionGroup g : groups) {
                rows.add(ScoreRow.competitionSection(g));
                for (Match m : g.matches()) {
                    rows.add(ScoreRow.match(m, true));
                }
            }
        } else {
            String[] stages = new String[]{"live", "scheduled", "delayed", "unknown", "final", "cancelled"};
            for (String stage : stages) {
                ArrayList<Match> stageMatches = new ArrayList<Match>();
                for (Match m : visible) {
                    if (!m.state.equals(stage)) continue;
                    stageMatches.add(m);
                }
                if (stageMatches.isEmpty()) continue;
                int liveCount = "live".equals(stage) ? stageMatches.size() : 0;
                rows.add(ScoreRow.statusSection(EnglishDisplay.text(Rules.stateLabel(stage)), stageMatches.size(), liveCount));
                for (Match m : stageMatches) {
                    rows.add(ScoreRow.match(m, false));
                }
            }
        }
        return rows;
    }

    private String settingsKey() {
        StringBuilder key = new StringBuilder(128);
        key.append(CalendarSync.permitted((Context)this)).append('|');
        key.append(CalendarCloudSync.status(this)).append('|');
        key.append(Http.epoch()).append('|');
        key.append(this.tab).append('|').append(this.calendarBusy).append('|').append(this.calendarName()).append('|').append(this.calendarSync.linkedEvents()).append('|').append(this.prefs.getLong("calendar_checked", 0L)).append('|').append(this.snapshot.failed());
        for (Repository.Batch batch : this.snapshot.batches) {
            key.append('|').append(batch.competition.id).append(':').append(batch.error);
        }
        return key.toString();
    }

    private void header(LinearLayout parent) {
        LinearLayout header = this.ui.row();
        header.setMinimumHeight(this.ui.dp(52.0f));
        header.setPadding(this.ui.dp(16.0f), this.ui.dp(6.0f), this.ui.dp(12.0f), this.ui.dp(4.0f));
        if (this.tab == 0) {
            FrameLayout brandBox = new FrameLayout(this);
            brandBox.setBackground(this.ui.outlined(this.ui.surface, this.ui.border, 10));
            Ui.Icon mark = new Ui.Icon((Context)this, "mark", this.ui.primary);
            brandBox.addView(mark, new FrameLayout.LayoutParams(this.ui.dp(18.0f), this.ui.dp(18.0f), Gravity.CENTER));
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(this.ui.dp(32.0f), this.ui.dp(32.0f));
            mp.setMarginEnd(this.ui.dp(10.0f));
            header.addView(brandBox, mp);
        }
        TextView heading = Ui.singleLine(this.ui.display(this.tab == 0 ? "N1ghtScore" : "Settings", 20, this.ui.text));
        header.addView((View)heading, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        if (this.tab == 0) {
            // Live counter pill (toggles Live-only lens)
            this.headerLivePill = this.ui.row();
            this.headerLivePill.setPadding(this.ui.dp(10.0f), this.ui.dp(5.0f), this.ui.dp(10.0f), this.ui.dp(5.0f));
            View dot = new View(this);
            dot.setBackground(this.ui.shape(this.ui.live, Ui.PILL_RADIUS));
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(this.ui.dp(6), this.ui.dp(6));
            dp.setMarginEnd(this.ui.dp(5));
            this.headerLivePill.addView(dot, dp);
            this.headerLiveText = Ui.singleLine(this.ui.text("LIVE", 11, this.ui.live, true));
            this.headerLivePill.addView(this.headerLiveText);
            this.headerLivePill.setFocusable(true);
            this.headerLivePill.setOnClickListener(v -> this.toggleLens(ScoreFeedOrganizer.LENS_LIVE));
            this.headerLivePill.setVisibility(View.GONE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(this.ui.dp(6.0f));
            header.addView(this.headerLivePill, lp);

            // Toggle Competition vs Status grouping
            FrameLayout groupToggle = new FrameLayout(this);
            boolean byLeague = ScoreFeedOrganizer.GROUP_COMPETITION.equals(this.groupMode);
            this.headerGroupIcon = new Ui.Icon(this, byLeague ? "layers" : "clock", this.ui.text);
            groupToggle.addView(this.headerGroupIcon, new FrameLayout.LayoutParams(this.ui.dp(18.0f), this.ui.dp(18.0f), Gravity.CENTER));
            this.ui.clickableDrawable(groupToggle, this.ui.outlined(this.ui.surface, this.ui.border, 12), 12);
            groupToggle.setContentDescription(byLeague ? "Grouped by competition · Tap to group by status" : "Grouped by status · Tap to group by competition");
            groupToggle.setTooltipText(byLeague ? "Grouped by competition" : "Grouped by status");
            groupToggle.setFocusable(true);
            groupToggle.setOnClickListener(v -> this.toggleGroupMode());
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(this.ui.dp(38.0f), this.ui.dp(38.0f));
            gp.setMarginEnd(this.ui.dp(6.0f));
            header.addView(groupToggle, gp);

            FrameLayout refresh = this.headerAction("refresh", "Refresh matches", () -> this.load(true));
            this.ui.clickableDrawable(refresh, this.ui.outlined(this.ui.surface, this.ui.border, 12), 12);
            refresh.setLayoutParams(new LinearLayout.LayoutParams(this.ui.dp(38.0f), this.ui.dp(38.0f)));
            header.addView(refresh);
        }
        parent.addView((View)header);
    }

    private void toggleGroupMode() {
        this.groupMode = ScoreFeedOrganizer.GROUP_COMPETITION.equals(this.groupMode)
            ? ScoreFeedOrganizer.GROUP_STATUS : ScoreFeedOrganizer.GROUP_COMPETITION;
        this.prefs.edit().putString("feed_group_mode", this.groupMode).apply();
        if (this.headerGroupIcon != null) {
            boolean byLeague = ScoreFeedOrganizer.GROUP_COMPETITION.equals(this.groupMode);
            this.headerGroupIcon.set(byLeague ? "layers" : "clock", this.ui.text);
        }
        this.renderedScores = "";
        this.updateScoreRows();
        Toast.makeText(this, ScoreFeedOrganizer.GROUP_COMPETITION.equals(this.groupMode)
            ? "Grouped by competition" : "Grouped by match status", Toast.LENGTH_SHORT).show();
    }

    private void toggleLens(String target) {
        this.lens = target.equals(this.lens) ? ScoreFeedOrganizer.LENS_ALL : target;
        this.renderedScores = "";
        this.refreshFeedBar();
        this.updateScoreRows();
    }

    private void refreshFeedBar() {
        if (this.feedBarView == null || this.tab != 0) return;
        List<Match> all = this.snapshot.matches();
        ScoreFeedOrganizer.FeedSummary summary = ScoreFeedOrganizer.summarize(all, this.subscribed);

        if (this.headerLivePill != null && this.headerLiveText != null) {
            if (summary.live() > 0) {
                boolean activeLive = ScoreFeedOrganizer.LENS_LIVE.equals(this.lens);
                this.headerLivePill.setVisibility(View.VISIBLE);
                this.headerLiveText.setText(summary.live() + " LIVE");
                this.headerLiveText.setTextColor(activeLive ? android.graphics.Color.WHITE : this.ui.live);
                this.ui.clickableDrawable(this.headerLivePill,
                    activeLive ? this.ui.shape(this.ui.live, Ui.PILL_RADIUS)
                        : this.ui.outlined(this.ui.liveSoft, Ui.alpha(this.ui.live, 95), Ui.PILL_RADIUS),
                    Ui.PILL_RADIUS);
            } else {
                this.headerLivePill.setVisibility(View.GONE);
                if (ScoreFeedOrganizer.LENS_LIVE.equals(this.lens)) this.lens = ScoreFeedOrganizer.LENS_ALL;
            }
        }

        this.feedBarView.removeAllViews();
        long latest = 0L;
        for (Repository.Batch b : this.snapshot.batches) latest = Math.max(latest, b.fetched);
        String modeHint = ScoreFeedOrganizer.GROUP_COMPETITION.equals(this.groupMode) ? "By league" : "By status";
        String syncText = latest > 0L ? "Synced " + MainActivity.formatInstant(latest, "HH:mm") : (this.busy ? "Syncing…" : "Ready");
        String statusLine = matchCount(all.size()) + " · " + modeHint + " · " + syncText;
        TextView info = this.ui.text(statusLine, 11, this.snapshot.failed() ? this.ui.warn : this.ui.dim, false);
        info.setSingleLine(true);
        info.setEllipsize(android.text.TextUtils.TruncateAt.END);
        this.feedBarView.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));

        if (summary.saved() > 0 || ScoreFeedOrganizer.LENS_SAVED.equals(this.lens)) {
            boolean savedOn = ScoreFeedOrganizer.LENS_SAVED.equals(this.lens);
            TextView savedChip = Ui.singleLine(this.ui.text("Saved " + summary.saved(), 11, savedOn ? this.ui.onPrimary : this.ui.primary, true));
            savedChip.setPadding(this.ui.dp(10), this.ui.dp(4), this.ui.dp(10), this.ui.dp(4));
            this.ui.clickableDrawable(savedChip,
                savedOn ? this.ui.shape(this.ui.primary, Ui.PILL_RADIUS)
                    : this.ui.outlined(this.ui.primarySoft, Ui.alpha(this.ui.primary, 90), Ui.PILL_RADIUS),
                Ui.PILL_RADIUS);
            savedChip.setFocusable(true);
            savedChip.setOnClickListener(v -> this.toggleLens(ScoreFeedOrganizer.LENS_SAVED));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
            sp.setMarginStart(this.ui.dp(8));
            this.feedBarView.addView(savedChip, sp);
        }
    }

    private View cs2EventsAction() {
        LinearLayout row = this.ui.row();
        LinearLayout pill = this.ui.row();
        pill.setPadding(this.ui.dp(12), this.ui.dp(8), this.ui.dp(12), this.ui.dp(8));
        this.ui.clickableDrawable(pill, this.ui.outlined(this.ui.warnSoft, Ui.alpha(this.ui.warn, 90), Ui.CONTROL_RADIUS), Ui.CONTROL_RADIUS);
        pill.addView(new Ui.Icon(this, "bracket", this.ui.warn), new LinearLayout.LayoutParams(this.ui.dp(16), this.ui.dp(16)));
        TextView label = Ui.singleLine(this.ui.text("CS2 Tournaments & Brackets", 12, this.ui.warn, true));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMarginStart(this.ui.dp(8));
        pill.addView(label, lp);
        pill.addView(new Ui.Icon(this, "chevron", this.ui.warn), new LinearLayout.LayoutParams(this.ui.dp(14), this.ui.dp(14)));
        pill.setFocusable(true);
        pill.setOnClickListener(v -> this.startActivity(new Intent(this, Cs2EventActivity.class).putExtra("day", this.from().toString())));
        row.addView(pill, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private void updateCs2EventsAction() {
        if (this.cs2EventsAction == null) return;
        int visibility = this.tab == 0 && this.group.equals("CS2") ? View.VISIBLE : View.GONE;
        if (this.cs2EventsAction.getVisibility() == visibility) return;
        this.cs2EventsAction.setVisibility(visibility);
        if (!this.pad && this.headerView != null) this.headerView.post(this::syncHeaderHeight);
    }

    private FrameLayout headerAction(String kind, String description, Runnable click) {
        FrameLayout button = new FrameLayout((Context)this);
        button.addView((View)new Ui.Icon((Context)this, kind, this.ui.text), (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(this.ui.dp(18.0f), this.ui.dp(18.0f), 17));
        this.ui.clickable((View)button, 0, 24);
        button.setContentDescription((CharSequence)description);
        button.setFocusable(true);
        button.setOnClickListener(v -> click.run());
        button.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(44.0f), this.ui.dp(44.0f)));
        return button;
    }

    private void filters(LinearLayout parent) {
        HorizontalScrollView horizontal = new HorizontalScrollView((Context)this);
        horizontal.setHorizontalScrollBarEnabled(false);
        horizontal.setClipToPadding(false);
        LinearLayout tabs = this.ui.row();
        this.filterTabs = tabs;
        tabs.setPadding(this.ui.dp(16.0f), this.ui.dp(6.0f), this.ui.dp(16.0f), this.ui.dp(6.0f));
        for (int i = 0; i < GROUPS.length; i++) {
            String g = GROUPS[i];
            String label = EnglishDisplay.sport(g);
            boolean selected = g.equals(this.group);
            int accent = "全部".equals(g) ? this.ui.action : this.ui.sportAccent(null, g);
            int activeInk = "全部".equals(g) ? this.ui.onAction : android.graphics.Color.WHITE;
            TextView item = this.ui.chip(label, selected, accent, activeInk, () -> this.select(this.tab, g, this.dayOffset));
            item.setMinHeight(this.ui.dp(36));
            item.setPadding(this.ui.dp(14), this.ui.dp(6), this.ui.dp(14), this.ui.dp(6));
            item.setContentDescription((CharSequence)(label + (selected ? ", selected" : ", filter sport")));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) lp.setMarginStart(this.ui.dp(7));
            tabs.addView((View)item, lp);
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
    }

    /** Modern interactive 7-day horizontal date rail + calendar modal shortcut. */
    private LinearLayout dateSelector() {
        LocalDate today = Match.today(System.currentTimeMillis());
        LocalDate selected = this.from();
        this.headerDay = selected;

        LinearLayout bar = this.ui.row();
        bar.setPadding(this.ui.dp(14.0f), this.ui.dp(4.0f), this.ui.dp(12.0f), this.ui.dp(4.0f));

        LinearLayout rail = this.ui.row();
        List<ScoreFeedOrganizer.DayChip> days = ScoreFeedOrganizer.weekStrip(selected, today);
        for (int i = 0; i < days.size(); i++) {
            ScoreFeedOrganizer.DayChip chip = days.get(i);
            LinearLayout cell = this.ui.column();
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(this.ui.dp(2), this.ui.dp(7), this.ui.dp(2), this.ui.dp(7));
            cell.setMinimumHeight(this.ui.dp(50));
            android.graphics.drawable.GradientDrawable cellBg = chip.selected()
                ? this.ui.shape(this.ui.primary, 13)
                : chip.today()
                ? this.ui.outlined(this.ui.primarySoft, Ui.alpha(this.ui.primary, 80), 13)
                : this.ui.shape(android.graphics.Color.TRANSPARENT, 13);
            this.ui.clickableDrawable(cell, cellBg, 13);
            cell.setFocusable(true);
            cell.setSelected(chip.selected());
            cell.setContentDescription(chip.weekday() + " " + chip.dayNumber() + (chip.selected() ? ", selected" : ""));

            TextView wd = this.ui.text(chip.weekday(), 10, chip.selected() ? this.ui.onPrimary : chip.today() ? this.ui.primary : this.ui.muted, chip.selected() || chip.today());
            wd.setSingleLine(true);
            wd.setGravity(Gravity.CENTER);
            TextView num = this.ui.display(chip.dayNumber(), 14, chip.selected() ? this.ui.onPrimary : this.ui.text);
            num.setGravity(Gravity.CENTER);
            cell.addView(wd);
            cell.addView(num);

            LocalDate targetDate = chip.date();
            cell.setOnClickListener(v -> this.selectDay(targetDate));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) cp.setMarginStart(this.ui.dp(3));
            rail.addView(cell, cp);
        }
        bar.addView(rail, new LinearLayout.LayoutParams(0, -2, 1f));

        FrameLayout calendarBtn = new FrameLayout(this);
        calendarBtn.addView(new Ui.Icon(this, "calendar", this.ui.text), new FrameLayout.LayoutParams(this.ui.dp(18), this.ui.dp(18), Gravity.CENTER));
        this.ui.clickableDrawable(calendarBtn, this.ui.outlined(this.ui.surface, this.ui.border, 13), 13);
        calendarBtn.setContentDescription("Open calendar date picker");
        calendarBtn.setFocusable(true);
        calendarBtn.setOnClickListener(v -> Sheet.date(this, this.from(), this::selectDay));
        LinearLayout.LayoutParams calLp = new LinearLayout.LayoutParams(this.ui.dp(42), this.ui.dp(50));
        calLp.setMarginStart(this.ui.dp(6));
        bar.addView(calendarBtn, calLp);
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
            this.message("Team schedule unavailable", "The source has not provided an ID for this team. Refresh and try again later.");
            return;
        }
        this.startActivity(new Intent((Context)this, TeamActivity.class).putExtra("team", team.json().toString()));
    }

    @Override
    public void openCompetition(ScoreRow sectionRow) {
        if (sectionRow == null) return;
        if ("cs2".equals(sectionRow.leagueId) && sectionRow.tournamentName != null && !sectionRow.tournamentName.isBlank()) {
            this.startActivity(new Intent(this, Cs2EventActivity.class)
                .putExtra("day", this.from().toString())
                .putExtra("tournament", sectionRow.tournamentName));
            return;
        }
        Intent intent = new Intent(this, StandingsActivity.class);
        if (sectionRow.leagueId != null && !sectionRow.leagueId.isBlank()) intent.putExtra("league", sectionRow.leagueId);
        if (sectionRow.sportGroup != null && !sectionRow.sportGroup.isBlank()) intent.putExtra("group", sectionRow.sportGroup);
        this.startActivity(intent);
    }

    @Override
    public void retryScores() {
        this.load(true);
    }

    @Override
    public void resolveEmptySchedule() {
        if (!ScoreFeedOrganizer.LENS_ALL.equals(this.lens)) {
            this.lens = ScoreFeedOrganizer.LENS_ALL;
            this.renderedScores = "";
            this.refreshFeedBar();
            this.updateScoreRows();
            return;
        }
        if (this.snapshot.failed()) this.load(true);
        else if (!this.group.equals("全部")) this.select(0, "全部", this.dayOffset);
        else Sheet.date(this, this.from(), this::selectDay);
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
        // Hero Subscriptions & Calendar Dashboard Card
        LinearLayout hero = this.ui.column();
        hero.setBackground(this.ui.heroPanel(this.ui.primary));
        hero.setPadding(this.ui.dp(18), this.ui.dp(16), this.ui.dp(18), this.ui.dp(16));
        LinearLayout heroTop = this.ui.row();
        heroTop.addView(this.ui.iconBadge("subscriptions", this.ui.primary, this.ui.primarySoft, 40, 20));
        LinearLayout heroCopy = this.ui.column();
        LinearLayout.LayoutParams hcp = new LinearLayout.LayoutParams(0, -2, 1f);
        hcp.setMarginStart(this.ui.dp(12));
        heroCopy.addView(this.ui.eyebrow("MATCH ALERTS HUB", this.ui.primary));
        heroCopy.addView(this.ui.display(CalendarSync.permitted((Context)this) ? matchCount(this.calendarSync.linkedEvents()) + " saved" : "Calendar access off", 18, this.ui.text));
        heroCopy.addView(this.ui.text(this.calendarTargetDetail(), 12, this.ui.muted, false));
        heroTop.addView(heroCopy, hcp);
        hero.addView(heroTop);
        this.ui.gap(hero, 14);
        LinearLayout heroBtns = this.ui.row();
        TextView manageBtn = this.ui.accentButton("Manage", this.ui.primary, this.ui.onPrimary, this::subscribedList);
        LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(0, -2, 1f);
        mLp.setMarginEnd(this.ui.dp(8));
        heroBtns.addView(manageBtn, mLp);
        TextView syncBtn = this.ui.accentButton(this.calendarBusy ? "Syncing…" : "Sync Now", this.ui.tonal, this.ui.text, () -> this.syncSubscriptions(true));
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, -2, 1f);
        sLp.setMarginEnd(this.ui.dp(8));
        heroBtns.addView(syncBtn, sLp);
        TextView calBtn = this.ui.accentButton("Calendar", this.ui.tonal, this.ui.text, this::openCalendar);
        heroBtns.addView(calBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        hero.addView(heroBtns);
        this.body.addView(hero);

        this.section("Appearance & feed");
        LinearLayout appearance = this.ui.column();
        appearance.setBackground((Drawable)this.ui.panel());
        appearance.setPadding(this.ui.dp(16), this.ui.dp(14), this.ui.dp(16), this.ui.dp(16));
        appearance.addView(this.ui.text("Theme", 13, this.ui.muted, true));
        this.ui.gap(appearance, 8);
        LinearLayout themeTrack = this.ui.segmentedTrack();
        String currentTheme = this.prefs.getString("theme", "dark");
        String[] themeVals = new String[]{"dark", "light", "system"};
        String[] themeLabels = new String[]{"Dark", "Light", "System"};
        for (int i = 0; i < themeVals.length; i++) {
            String val = themeVals[i];
            TextView t = this.ui.tab(themeLabels[i], val.equals(currentTheme), () -> {
                if (!val.equals(currentTheme)) {
                    this.prefs.edit().putString("theme", val).apply();
                    this.recreate();
                }
            });
            themeTrack.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        appearance.addView(themeTrack);
        this.ui.gap(appearance, 14);
        appearance.addView(this.ui.text("Default score grouping", 13, this.ui.muted, true));
        this.ui.gap(appearance, 8);
        LinearLayout groupTrack = this.ui.segmentedTrack();
        boolean byLeague = ScoreFeedOrganizer.GROUP_COMPETITION.equals(this.groupMode);
        groupTrack.addView(this.ui.tab("By Competition", byLeague, () -> {
            if (!ScoreFeedOrganizer.GROUP_COMPETITION.equals(this.groupMode)) {
                this.groupMode = ScoreFeedOrganizer.GROUP_COMPETITION;
                this.prefs.edit().putString("feed_group_mode", this.groupMode).apply();
                this.renderedScores = "";
                this.renderedSettings = "";
                this.render(true);
            }
        }), new LinearLayout.LayoutParams(0, -2, 1f));
        groupTrack.addView(this.ui.tab("By Status", !byLeague, () -> {
            if (!ScoreFeedOrganizer.GROUP_STATUS.equals(this.groupMode)) {
                this.groupMode = ScoreFeedOrganizer.GROUP_STATUS;
                this.prefs.edit().putString("feed_group_mode", this.groupMode).apply();
                this.renderedScores = "";
                this.renderedSettings = "";
                this.render(true);
            }
        }), new LinearLayout.LayoutParams(0, -2, 1f));
        appearance.addView(groupTrack);
        this.body.addView(appearance);

        this.section("Calendar & sync");
        LinearLayout calendar = this.ui.column();
        calendar.setBackground((Drawable)this.ui.panel());
        this.settingRow(calendar, "calendar-target", "Reminder calendar", this.calendarTargetDetail(), this::calendarChoice);
        long checked = this.prefs.getLong("calendar_checked", 0L);
        this.settingRow(calendar, "sync-subscriptions", this.calendarBusy ? "Checking…" : "Check subscriptions", checked > 0L ? "Last checked " + MainActivity.formatInstant(checked, "M月d日 HH:mm") : "Sync postponements, cancellations, and final results", () -> this.syncSubscriptions(true));
        this.settingRow(calendar, "calendar-cloud", "Calendar cloud sync", EnglishDisplay.text(CalendarCloudSync.status(this)), this::manageCloudSync);
        this.body.addView((View)calendar);

        this.section("Sources & updates");
        LinearLayout updates = this.ui.column();
        updates.setBackground((Drawable)this.ui.panel());
        this.settingRow(updates, "sources", "Data sources & connection", this.snapshot.failed() ? "Some sources need attention" : "All active feeds healthy", this::sourceStatus);
        this.settingRow(updates, "app-update", "Check for updates", "Version " + BuildConfig.VERSION_NAME, () -> this.startActivity(new Intent((Context)this, UpdateActivity.class)));
        this.settingRow(updates, "help", "Help & data notes", "Reminders, sync, and feed coverage", this::help);
        TextView about = this.ui.text("N1ghtScore · Native Android · No ads · Beijing time", 11, this.ui.dim, false);
        about.setPadding(this.ui.dp(16.0f), this.ui.dp(12.0f), this.ui.dp(16.0f), this.ui.dp(14.0f));
        updates.addView((View)about);
        this.body.addView((View)updates);
    }

    private void section(String title) {
        this.ui.gap(this.body, 18);
        TextView h = this.ui.eyebrow(title, this.ui.dim);
        h.setPadding(this.ui.dp(6.0f), 0, 0, this.ui.dp(8.0f));
        this.body.addView((View)h);
    }

    private void settingRow(LinearLayout into, String kind, String title, String detail, Runnable action) {
        if (into.getChildCount() > 0) {
            View line = this.ui.divider();
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, this.ui.dp(1.0f));
            lp.setMarginStart(this.ui.dp(62.0f));
            lp.setMarginEnd(this.ui.dp(16.0f));
            into.addView(line, (ViewGroup.LayoutParams)lp);
        }
        LinearLayout row = this.ui.row();
        row.setMinimumHeight(this.ui.dp(58.0f));
        row.setPadding(this.ui.dp(14.0f), this.ui.dp(10.0f), this.ui.dp(14.0f), this.ui.dp(10.0f));
        this.ui.clickable((View)row, 0, 14);
        row.setFocusable(true);
        int tint = "sources".equals(kind) && this.snapshot.failed() ? this.ui.error
            : kind.startsWith("calendar") || kind.contains("sub") ? this.ui.primary
            : "app-update".equals(kind) ? this.ui.info : this.ui.text;
        int fill = "sources".equals(kind) && this.snapshot.failed() ? this.ui.errorSoft
            : kind.startsWith("calendar") || kind.contains("sub") ? this.ui.primarySoft
            : "app-update".equals(kind) ? this.ui.infoSoft : this.ui.tonal;
        FrameLayout symbol = this.ui.iconBadge(kind, tint, fill, 36, 18);
        LinearLayout.LayoutParams symbolParams = new LinearLayout.LayoutParams(this.ui.dp(36.0f), this.ui.dp(36.0f));
        symbolParams.setMarginEnd(this.ui.dp(12.0f));
        row.addView((View)symbol, (ViewGroup.LayoutParams)symbolParams);
        LinearLayout text = this.ui.column();
        text.addView((View)Ui.singleLine(this.ui.text(title, 14, this.ui.text, true)));
        if (!detail.isBlank()) {
            this.ui.gap(text, 2);
            TextView dt = this.ui.text(detail, 12, this.ui.muted, false);
            dt.setMaxLines(2);
            dt.setEllipsize(android.text.TextUtils.TruncateAt.END);
            text.addView((View)dt);
        }
        row.addView((View)text, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(this.ui.dp(16.0f), this.ui.dp(16.0f));
        ip.setMarginStart(this.ui.dp(10.0f));
        row.addView((View)new Ui.Icon((Context)this, "chevron", this.ui.dim), (ViewGroup.LayoutParams)ip);
        row.setOnClickListener(v -> action.run());
        into.addView((View)row);
    }

    private void openCalendar() {
        try {
            this.startActivity(new Intent("android.intent.action.VIEW", Uri.parse((String)("content://com.android.calendar/time/" + System.currentTimeMillis()))));
        }
        catch (RuntimeException e) {
            this.message("Calendar app not found", "Install or enable the system calendar, then try again.");
        }
    }

    private void help() {
        this.message("Help", "Tap the calendar icon on a match card to subscribe; tap the check to unsubscribe. The system calendar reminds you 5 minutes before kickoff. After a subscribed match ends, an app task checks and posts the final result (the app does not need to stay open; on Xiaomi / HyperOS allow calendar access, notifications, autostart, and calendar notifications).\n\nDevice sync: after signing in to the same Xiaomi account with calendar access on phone and tablet, subscriptions and cancellations usually sync through Xiaomi Calendar in about 45 seconds to 3 minutes. Shizuku is not required. Changing the reminder calendar only affects new subscriptions; existing events stay in their original calendar.\n\nNetwork: domestic sources prefer a direct physical-interface path; overseas sources choose among proxy, direct, and the app's HTTPS relay, recovering after network or proxy changes.\n\nSports data comes from ESPN. Browse football by date (including national teams) and tap a crest for that team's schedule. CS2 comes from 5EPlay: top-tier events (Major / S+ / S) and all Team Spirit matches. Completed matches and details are read once and stop polling; use Refresh to check manually. Failed refreshes back off and keep the last real cache. Missing values are never replaced with zero.");
    }

    private String calendarName() {
        String name = this.calendarSync.calendarName();
        return name == null || name.isBlank() ? "Choose phone calendar" : name;
    }

    private String calendarTargetDetail() {
        return CalendarSync.permitted((Context)this) ? this.calendarName() : "Calendar access off · Tap to allow";
    }

    private String subscriptionsDetail() {
        return CalendarSync.permitted((Context)this)
            ? matchCount(this.calendarSync.linkedEvents()) + " · 5 min reminder"
            : "Allow calendar access to find subscriptions on this phone";
    }

    private void requestCalendarPermission(boolean chooseAfter) {
        if (this.isFinishing() || this.isDestroyed()) {
            return;
        }
        this.pendingChooseCalendar = chooseAfter;
        this.requestPermissions(new String[]{"android.permission.READ_CALENDAR", "android.permission.WRITE_CALENDAR"}, 42);
    }

    private void manageCloudSync() {
        if (this.isFinishing() || this.isDestroyed()) {
            return;
        }
        if (!CalendarSync.permitted((Context)this)) {
            this.requestCalendarPermission(false);
            return;
        }
        CalendarCloudSync.manage(this, () -> {
            if (this.isDestroyed() || this.isFinishing()) {
                return;
            }
            this.refreshSubscribed();
            this.renderedScores = "";
            this.renderedSettings = "";
            this.renderedPadFocus = "";
            this.render(true);
        });
    }

    private void calendarChoice() {
        this.chooseCalendar();
    }

    private void chooseCalendar() {
        if (this.isFinishing() || this.isDestroyed()) {
            return;
        }
        if (!CalendarSync.permitted((Context)this)) {
            this.requestCalendarPermission(true);
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
                    Sheet.message(this, "No writable calendar", "Enable a local calendar or writable account in the system calendar, then come back and choose it.");
                    return;
                }
                ArrayList<Sheet.Item> items = new ArrayList<Sheet.Item>();
                int selected = -1;
                for (int i = 0; i < result.size(); ++i) {
                    CalendarSync.CalendarChoice choice = (CalendarSync.CalendarChoice)result.get(i);
                    if (choice.id == this.calendarSync.calendarId()) {
                        selected = i;
                    }
                    items.add(new Sheet.Item(choice.name, choice.local ? "On this device" : "Writable account", choice.id == this.calendarSync.calendarId()));
                }
                Sheet.choose(this, "Choose reminder calendar", "Subscribed matches are written here with a 5-minute reminder.", items, selected, index -> {
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
            Toast.makeText((Context)this, (CharSequence)"Updating calendar…", (int)0).show();
            return;
        }
        if (this.subscribed.contains(m.id)) {
            Sheet.confirm(this, "Unsubscribe", "Remove \"" + EnglishDisplay.text(m.displayTitle()) + "\" and its reminders from the phone calendar?", "Unsubscribe", () -> this.unsubscribeAsync(m));
            return;
        }
        if (!m.scheduled() || !m.timeConfirmed || m.start <= System.currentTimeMillis()) {
            Sheet.message(this, "Can't subscribe yet", "This match has no confirmed start time, has started, or has finished.");
            return;
        }
        this.pendingSubscription = m;
        if (!CalendarSync.permitted((Context)this)) {
            this.requestCalendarPermission(false);
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
                Sheet.message(this, "No writable calendar", "Enable a local or Xiaomi calendar in the system calendar first.");
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
                result.error = "Could not write to calendar";
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
                    Toast.makeText((Context)this, (CharSequence)"Added to calendar · 5 min reminder", (int)0).show();
                    ResultNotify.requestIfNeeded(this);
                } else {
                    Sheet.message(this, "Couldn't add to calendar", finalResult.error.isEmpty() ? "Check calendar access and the selected calendar." : EnglishDisplay.text(finalResult.error));
                }
            });
        });
    }

    private static CalendarSync.Result noCalendarResult() {
        CalendarSync.Result result = new CalendarSync.Result();
        result.failed = 1;
        result.error = "Enable a local or Xiaomi calendar in the system calendar first.";
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
                result.error = "Could not read calendar";
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
                    Sheet.message(this, "Couldn't unsubscribe", finalResult.error.isEmpty() ? "Check calendar access and try again." : EnglishDisplay.text(finalResult.error));
                } else {
                    Toast.makeText((Context)this, (CharSequence)"Unsubscribed", (int)0).show();
                }
            });
        });
    }

    private void subscribedList() {
        if (!CalendarSync.permitted((Context)this)) {
            this.requestCalendarPermission(false);
            return;
        }
        List<Match> list = this.calendarSync.subscriptions();
        if (list.isEmpty()) {
            Sheet.message(this, "No subscriptions", "Tap Subscribe on a match card to add a single match to the phone calendar.");
            return;
        }
        ArrayList<Sheet.Item> items = new ArrayList<Sheet.Item>();
        for (Match m : list) {
            items.add(new Sheet.Item(EnglishDisplay.text(m.displayTitle()).toString(), MainActivity.formatInstant(m.start, "M月d日 HH:mm") + " · " + EnglishDisplay.text(MatchLabel.of(m)), false));
        }
        Sheet.choose(this, "Subscribed matches", "Choose a match to unsubscribe. The system calendar reminds you before kickoff and posts the final result.", items, -1, index -> {
            Match m = (Match)list.get(index);
            Sheet.confirm(this, "Unsubscribe", "Remove the calendar event and reminders for \"" + EnglishDisplay.text(m.displayTitle()) + "\"?", "Unsubscribe", () -> this.unsubscribeAsync(m));
        });
    }

    private void syncSubscriptions(boolean feedback) {
        if (this.isFinishing() || this.isDestroyed()) {
            return;
        }
        if (!CalendarSync.permitted((Context)this)) {
            if (feedback) {
                this.requestCalendarPermission(false);
            }
            return;
        }
        if (this.calendarBusy) {
            return;
        }
        if (!this.calendarSync.hasSubscriptions()) {
            if (feedback) {
                Sheet.message(this, "No subscriptions", "Tap Subscribe on a match card to add a single match to the phone calendar.");
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
                text = "Check failed. Check your network and calendar access, then try again.";
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
                        Sheet.message(this, "Subscription check", finalText + "\nConfirm that reminders appear in the system calendar.");
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
            case "dark" -> "Dark";
            case "light" -> "Light";
            default -> "System";
        };
    }

    private void themeChoice() {
        String[] values = new String[]{"dark", "light", "system"};
        String[] labels = new String[]{"Dark", "Light", "System"};
        String current = this.prefs.getString("theme", "dark");
        int selected = 0;
        for (int i = 0; i < values.length; ++i) {
            if (!values[i].equals(current)) continue;
            selected = i;
        }
        ArrayList<Sheet.Item> items = new ArrayList<Sheet.Item>();
        for (int i = 0; i < labels.length; ++i) {
            items.add(new Sheet.Item(labels[i], i == selected ? "Current" : "", i == selected));
        }
        Sheet.choose(this, "Appearance", "System follows the phone's light or dark setting.", items, selected, index -> {
            this.prefs.edit().putString("theme", values[index]).apply();
            this.recreate();
        });
    }

    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != 42 && code != 44) {
            return;
        }
        boolean chooseAfter = this.pendingChooseCalendar;
        this.pendingChooseCalendar = false;
        if (!CalendarSync.permitted((Context)this)) {
            this.pendingSubscription = null;
            this.renderedSettings = "";
            this.render(true);
            if (code == 42) {
                Sheet.message(this, "Calendar access is off", "Allow calendar access to subscribe to matches and sync fixtures through Xiaomi Calendar. Enable calendar read and write in system app permissions.");
            }
            return;
        }
        CalendarUpdates.start(this);
        CalendarUpdates.observe(this, this.snapshot.matches());
        CalendarCloudSync.opened(this);
        this.calendarWorker.submit(() -> {
            this.ensureCalendar(false);
            this.calendarSync.recoverLocal();
            CalendarJob.schedule((Context)this);
            this.handler.post(() -> {
                if (this.isDestroyed()) {
                    return;
                }
                this.refreshSubscribed();
                this.renderedScores = "";
                this.renderedSettings = "";
                this.renderedPadFocus = "";
                this.render(true);
                if (this.calendarSync.hasSubscriptions()) {
                    ResultNotify.requestIfNeeded(this);
                    this.syncSubscriptions(false);
                }
            });
        });
        if (this.pendingSubscription != null) {
            this.subscribePending();
        } else if (chooseAfter) {
            this.chooseCalendar();
        }
    }

    private void sourceStatus() {
        Repository.Snapshot source = this.snapshot;
        if (this.pad) {
            source = this.padSourceSnapshot();
            if (source == null) {
                Sheet.message(this, "Loading source status", "Source status will be ready shortly. Tap again in a moment.");
                return;
            }
        }
        StringBuilder content = new StringBuilder();
        content.append(EnglishDisplay.text(Http.statusSummary())).append("\n\n");
        for (Repository.Batch b : this.orderedSourceBatches(source)) {
            content.append(EnglishDisplay.text(b.competition.name)).append("\n").append(b.fetched > 0L ? "Last success " + MainActivity.formatInstant(b.fetched, "M月d日 HH:mm:ss") + " · " + matchCount(b.matches.size()) : "No successful fetch yet");
            if (!b.error.isEmpty()) {
                content.append("\n").append(EnglishDisplay.text(b.error));
            }
            content.append("\n\n");
        }
        content.append("Sources: ESPN and 5EPlay. Data may be delayed. When a connection fails, the last successful data is kept. Completed matches and details are read once and stop updating automatically; use Refresh to check manually.");
        Sheet.message(this, "Data sources & connection", content.toString());
    }

    private void navigation() {
        this.nav = this.ui.row();
        this.nav.setBackgroundColor(this.ui.navBg);
        this.nav.setPadding(this.ui.dp(14.0f), this.ui.dp(6.0f), this.ui.dp(14.0f), this.ui.dp(6.0f));
        String[] labels = new String[]{"Scores", "Standings", "Settings"};
        String[] icons = new String[]{"matches", "standings", "preferences"};
        for (int i = 0; i < labels.length; ++i) {
            int index = i;
            boolean selected = i != 1 && this.tab == (i == 0 ? 0 : 1);
            LinearLayout item = this.ui.column();
            item.setGravity(Gravity.CENTER);
            item.setMinimumHeight(this.ui.dp(52.0f));
            item.setPadding(this.ui.dp(4), this.ui.dp(4), this.ui.dp(4), this.ui.dp(4));
            this.ui.clickable((View)item, 0, 16);
            item.setFocusable(true);
            item.setSelected(selected);
            item.setContentDescription((CharSequence)(labels[i] + (selected ? ", selected" : "")));

            FrameLayout pill = new FrameLayout(this);
            if (selected) pill.setBackground(this.ui.shape(this.ui.primarySoft, Ui.PILL_RADIUS));
            View icon = new Ui.Icon((Context)this, icons[i], selected ? this.ui.primary : this.ui.muted);
            pill.addView(icon, new FrameLayout.LayoutParams(this.ui.dp(19.0f), this.ui.dp(19.0f), Gravity.CENTER));
            item.addView(pill, new LinearLayout.LayoutParams(this.ui.dp(52.0f), this.ui.dp(30.0f)));

            TextView label = this.ui.text(labels[i], 11, selected ? this.ui.primary : this.ui.muted, selected);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = this.ui.dp(3.0f);
            item.addView((View)label, lp);

            item.setOnClickListener(v -> {
                if (index == 1) this.openStandings();
                else this.select(index == 0 ? 0 : 1, this.group, this.dayOffset);
            });
            this.nav.addView((View)item, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        }
        this.root.addView((View)this.nav);
    }

    private void openStandings() {
        this.startActivity(new Intent(this, StandingsActivity.class).putExtra("group", this.group));
    }

    private void message(String title, String text) {
        if (!this.isFinishing() && !this.isDestroyed()) {
            Sheet.message(this, title, text);
        }
    }

    private static String formatInstant(long millis, String pattern) {
        return EnglishDisplay.text(Formats.instant(millis, pattern, "Not fetched")).toString();
    }

    private static String failureSummary(int failures) {
        return failures + (failures == 1 ? " source needs attention" : " sources need attention") + " · View connection status";
    }

    private static String matchCount(int count) {
        return count + (count == 1 ? " match" : " matches");
    }

    private static String watchDescription(WatchLinks.Link link) {
        if (link == null) return "";
        String note = EnglishDisplay.text(link.note);
        return EnglishDisplay.watchTitle(link) + ". " + EnglishDisplay.text(link.kind.label)
            + (note.isBlank() ? "" : " · " + note) + ". Opens in " + EnglishDisplay.text(link.provider) + ".";
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
        if (this.padFocusHost != null) this.ensurePadSelection(all);
        this.padAdapter.setSelected(this.padFocusHost == null ? "" : this.padSelectedId);
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
        brand.addView((View)mark, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(24.0f), this.ui.dp(24.0f)));
        TextView word = this.ui.text("N1ghtScore", 12, this.ui.text, true);
        word.setLetterSpacing(-0.01f);
        LinearLayout.LayoutParams wordParams = new LinearLayout.LayoutParams(-2, -2);
        wordParams.setMarginStart(this.ui.dp(6.0f));
        brand.addView((View)word, (ViewGroup.LayoutParams)wordParams);
        rail.addView((View)brand);
        this.ui.gap(rail, 24);
        rail.addView(this.padRailItem("matches", "Scores", this.tab == 0, () -> this.select(0, this.group, this.dayOffset)), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        this.ui.gap(rail, 3);
        rail.addView(this.padRailItem("standings", "Standings", false, this::openStandings), new LinearLayout.LayoutParams(-1, -2));
        this.ui.gap(rail, 3);
        rail.addView(this.padRailItem("preferences", "Settings", this.tab == 1, () -> this.select(1, this.group, this.dayOffset)), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        this.ui.gap(rail, 24);
        TextView caption = this.ui.text(this.tab == 0 ? "Sports" : "Jump to sport", 9, this.ui.muted, true);
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
            String labelText = EnglishDisplay.sport(g);
            TextView label = this.ui.text(labelText, 12, selected ? this.ui.text : this.ui.muted, selected);
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
            String state = this.tab == 0 ? (selected ? ", selected" : ", filter sport") : ", show scores for this sport";
            item.setContentDescription((CharSequence)(labelText + state));
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
        TextView zone = this.ui.text(EnglishDisplay.text(Formats.WEEKDAY.format(this.from())) + " · Beijing time", 9, this.ui.muted, false);
        zone.setPadding(this.ui.dp(4.0f), 0, 0, 0);
        this.padRailWeek = zone;
        rail.addView((View)zone);
        this.ui.gap(rail, 8);
        TextView version = this.ui.text("N1ghtScore " + BuildConfig.VERSION_NAME, 9, this.ui.muted, false);
        version.setPadding(this.ui.dp(4.0f), 0, 0, 0);
        rail.addView((View)version);
        return rail;
    }

    private View padRailItem(String icon, String label, boolean selected, Runnable action) {
        LinearLayout item = this.ui.row();
        item.setGravity(16);
        item.setMinimumHeight(this.ui.dp(48.0f));
        item.setPadding(this.ui.dp(8.0f), 0, this.ui.dp(8.0f), 0);
        this.ui.clickable((View)item, 0, 12);
        item.setFocusable(true);
        item.setSelected(selected);
        Ui.Icon glyph = new Ui.Icon((Context)this, icon, selected ? this.ui.text : this.ui.muted);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(this.ui.dp(19.0f), this.ui.dp(19.0f));
        item.addView((View)glyph, (ViewGroup.LayoutParams)iconParams);
        TextView text = this.ui.text(label, 13, selected ? this.ui.text : this.ui.muted, selected);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-2, -2);
        textParams.setMarginStart(this.ui.dp(9.0f));
        item.addView((View)text, (ViewGroup.LayoutParams)textParams);
        item.setContentDescription((CharSequence)(label + (selected ? ", selected" : "")));
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
        int listDp = Math.min(430, Math.max(336, Math.round((float)available * 0.34f)));
        LinearLayout listPanel = this.ui.column();
        listPanel.setBackgroundColor(this.ui.bg);
        // Narrow tablet: rail + full-height list. Wide tablet: rail + list + reading pane.
        workspace.addView(listPanel, stacked ? new LinearLayout.LayoutParams(-1, -1) : new LinearLayout.LayoutParams(this.ui.dp(listDp), -1));
        LinearLayout header = this.ui.column();
        header.setPadding(this.ui.dp(18.0f), this.ui.dp(14.0f), this.ui.dp(12.0f), this.ui.dp(4.0f));
        LinearLayout titleRow = this.ui.row();
        TextView title = this.ui.text("Scores", 22, this.ui.text, true);
        title.setLetterSpacing(-0.03f);
        titleRow.addView((View)title, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        titleRow.addView((View)this.padIconAction("refresh", "Refresh matches", () -> this.load(true)));
        header.addView((View)titleRow);
        this.ui.gap(header, 6);
        boolean largeDate = this.getResources().getConfiguration().fontScale >= 1.3f;
        LinearLayout dateBlock = this.ui.column();
        LinearLayout dateRow = this.ui.row();
        this.padDayNumber = this.ui.text(String.valueOf(this.from().getDayOfMonth()), 30, this.ui.text, true);
        this.padDayNumber.setLetterSpacing(-0.05f);
        this.padDayNumber.setIncludeFontPadding(false);
        dateRow.addView((View)this.padDayNumber, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-2, -2));
        LinearLayout dateWords = this.ui.column();
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        wp.setMarginStart(this.ui.dp(10.0f));
        this.padDayMeta = this.ui.text("", 11, this.ui.muted, false);
        dateWords.addView((View)this.padDayMeta);
        this.ui.gap(dateWords, 3);
        dateRow.addView((View)dateWords, (ViewGroup.LayoutParams)wp);
        LinearLayout controls = this.ui.row();
        if (this.dayOffset != 0) {
            TextView today = this.ui.chip("Today", false, () -> this.selectDay(Match.today(System.currentTimeMillis())));
            today.setTextSize(11.0f);
            today.setMinHeight(this.ui.dp(38.0f));
            today.setPadding(this.ui.dp(10.0f), 0, this.ui.dp(10.0f), 0);
            controls.addView((View)today);
        }
        FrameLayout prev = this.padIconAction("chevron", "Previous day", () -> this.selectDay(this.from().minusDays(1L)));
        prev.getChildAt(0).setRotation(180.0f);
        controls.addView((View)prev);
        controls.addView((View)this.padIconAction("chevron", "Next day", () -> this.selectDay(this.from().plusDays(1L))));
        controls.addView((View)this.padIconAction("calendar", "Choose date", () -> Sheet.date(this, this.from(), this::selectDay)));
        dateBlock.addView((View)dateRow);
        if (largeDate) {
            controls.setPadding(0, this.ui.dp(4.0f), 0, 0);
            dateBlock.addView((View)controls);
        } else {
            dateRow.addView((View)controls, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-2, -2));
        }
        header.addView((View)dateBlock);
        this.ui.gap(header, 4);
        this.padListStatus = this.ui.text("", 11, this.ui.muted, false);
        header.addView((View)this.padListStatus);
        View events = this.cs2EventsAction();
        events.setPadding(0, 0, 0, this.ui.dp(4.0f));
        this.cs2EventsAction = events;
        header.addView(events);
        this.updateCs2EventsAction();
        listPanel.addView((View)header);
        this.padList = new ScoreList((Context)this);
        this.padList.setPadding(this.ui.dp(16.0f), this.ui.dp(4.0f), this.ui.dp(16.0f), this.ui.dp(24.0f));
        this.padList.setClipToPadding(false);
        this.padList.setVerticalScrollBarEnabled(false);
        this.padAdapter = new PadScoreAdapter(this.ui, new PadScoreAdapter.Host(){

            @Override
            public void selectMatch(Match match) {
                if (match != null) {
                    if (stacked) MainActivity.this.matchDetail(match);
                    else MainActivity.this.selectPadMatch(match);
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
            public void openCompetition(ScoreRow sectionRow) {
                MainActivity.this.openCompetition(sectionRow);
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

            @Override
            public void resolveEmptySchedule() {
                MainActivity.this.resolveEmptySchedule();
            }
        }, stacked);
        this.padAdapter.attach(this.padList);
        this.padList.setAdapter((ListAdapter)this.padAdapter);
        listPanel.addView((View)this.padList, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, 0, 1.0f));
        if (stacked) return;
        workspace.addView(this.verticalRule());
        this.padFocusScroll = new Ui.ReadingScroll(this);
        this.padFocusScroll.setFillViewport(true);
        this.padFocusScroll.setVerticalScrollBarEnabled(false);
        this.padFocusScroll.setClipToPadding(false);
        this.padFocusHost = this.ui.column();
        this.padFocusHost.setPadding(this.ui.dp(stacked ? 18 : 28), this.ui.dp(20.0f), this.ui.dp(stacked ? 18 : 28), this.ui.dp(32.0f));
        this.padFocusScroll.addView((View)this.padFocusHost);
        workspace.addView(this.padFocusScroll, stacked ? new LinearLayout.LayoutParams(-1, 0, 1) : new LinearLayout.LayoutParams(0, -1, 1));
    }

    private int padRailWidth() {
        return Math.round(144 * Math.min(1.5f, Math.max(1f, getResources().getConfiguration().fontScale)));
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
                failure = e.getMessage() == null ? "Network connection failed" : e.getMessage();
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
        boolean narrowHero = this.ui.c.getResources().getConfiguration().screenWidthDp < 1040 || this.ui.c.getResources().getConfiguration().fontScale >= 1.4f;
        key.append(this.group).append('|').append(this.dayOffset).append('|').append(narrowHero ? 1 : 2);
        if (m == null) {
            return key.append("|none|").append(this.snapshot.matches().isEmpty()).append('|').append(this.busy || this.selectionLoading).append('|').append(this.snapshot.failed()).toString();
        }
        boolean canSubscribe = m.scheduled() && m.timeConfirmed && m.start > System.currentTimeMillis();
        key.append('|').append(m.id).append(',').append(m.state).append(',').append(m.displayScore(true)).append(',').append(m.displayScore(false))
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
            key.append(';').append(other.id).append(',').append(other.state).append(',').append(other.displayScore(true))
                .append(',').append(other.displayScore(false)).append(',').append(other.start);
        }
        return key.toString();
    }

    private String padF1Meta(Match m) {
        return EnglishDisplay.text(m.status) + " · " + (m.timeConfirmed ? MainActivity.formatInstant(m.start, "M月d日 HH:mm") : "Time TBD");
    }
    private String padNarrowMeta(Match m) {
        return EnglishDisplay.text(m.status) + " · " + (m.timeConfirmed ? MainActivity.formatInstant(m.start, "M月d日 HH:mm") + " · Beijing time" : "Time TBD");
    }
    private String padUpdatedText(Match m) {
        return m.updated > 0L ? "Updated " + MainActivity.formatInstant(m.updated, "HH:mm") : "Waiting for update";
    }
    private String padFetchedText(Match m, MatchDetail d) {
        return "Fetched " + MainActivity.formatInstant(d.fetched, "M月d日 HH:mm")
            + (m.updated > 0L ? "\nSource published " + MainActivity.formatInstant(m.updated, "M月d日 HH:mm") : "");
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
        String shown = EnglishDisplay.text(value).toString();
        View view = this.padFocusHost.findViewWithTag(tag);
        if (view instanceof TextView && !shown.contentEquals(((TextView)view).getText())) {
            ((TextView)view).setText(shown);
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
            TextView title = this.ui.text(loading ? "Loading scores" : this.snapshot.failed() ? "Schedule incomplete" : "Choose a match", 30, this.ui.text, true);
            title.setLetterSpacing(-0.03f);
            title.setGravity(17);
            empty.addView((View)title);
            this.ui.gap(empty, 10);
            TextView copy = this.ui.text(loading ? "Loading matches for the selected date and sport." : this.snapshot.failed() ? "Some sources can't be reached. Refresh or check source status." : this.snapshot.matches().isEmpty() ? (this.group.equals("全部") ? "No confirmed matches on this date yet." : "No confirmed " + EnglishDisplay.sport(this.group) + " matches on this date. Switch back to All or choose another sport.") : "Choose a match from the list to see its score, data, and timeline.", 14, this.ui.muted, false);
            copy.setGravity(17);
            copy.setLineSpacing((float)this.ui.dp(4.0f), 1.0f);
            empty.addView((View)copy);
            host.addView((View)empty);
            return;
        }
        LinearLayout eyebrow = this.ui.row();
        TextView kicker = Ui.singleLine(this.ui.text(EnglishDisplay.text(MatchLabel.of(m)), 11, this.ui.muted, true));
        kicker.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        kp.setMarginEnd(this.ui.dp(10));
        eyebrow.addView((View)kicker, (ViewGroup.LayoutParams)kp);
        TextView updated = Ui.singleLine(this.ui.text(padUpdatedText(m), 11, this.ui.muted, false));
        updated.setTag("pad-focus-updated");
        eyebrow.addView((View)updated);
        host.addView((View)eyebrow);
        this.ui.gap(host, 12);
        host.addView((View)this.padHero(m));
        this.ui.gap(host, 24);
        if (this.padDetail == null) {
            LinearLayout loading = this.padSurface();
            LinearLayout line = this.ui.row();
            line.addView((View)this.ui.text(this.padError.isEmpty() ? "Loading match data…" : "Match data unavailable", 15, this.padError.isEmpty() ? this.ui.text : this.ui.error, true), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
            if (!this.padError.isEmpty()) {
                line.addView((View)this.ui.button("Try again", false, () -> this.loadPadDetail(m, true)));
            }
            loading.addView((View)line);
            if (!this.padError.isEmpty()) {
                this.ui.gap(loading, 6);
                loading.addView((View)this.ui.text(EnglishDisplay.text(this.padError), 12, this.ui.muted, false));
            }
            host.addView((View)loading);
            return;
        }
        MatchDetail d = this.padDetail;
        if (!this.padError.isEmpty()) {
            LinearLayout warning = this.padSurface();
            warning.addView((View)this.ui.text("Not updated · " + EnglishDisplay.text(this.padError) + "\nShowing the last fetched data", 12, this.ui.error, false));
            host.addView((View)warning);
            this.ui.gap(host, 16);
        }
        if (!d.warning.isBlank()) {
            TextView note = this.ui.text(EnglishDisplay.text(d.warning), 12, this.ui.muted, false);
            note.setLineSpacing((float)this.ui.dp(3.0f), 1.0f);
            host.addView((View)note);
            this.ui.gap(host, 16);
        }
        LinearLayout left = this.ui.column();
        LinearLayout right = this.ui.column();
        this.padDetailColumns(left, right, d, m);
        host.addView(this.ui.columns(left, right, 360, 24), new LinearLayout.LayoutParams(-1, -2));
    }

    private LinearLayout padHero(Match m) {
        boolean canSubscribe;
        boolean narrow = this.ui.c.getResources().getConfiguration().screenWidthDp < 1040 || this.ui.c.getResources().getConfiguration().fontScale >= 1.4f;
        LinearLayout card = this.ui.column();
        card.setBackground(this.ui.heroPanel(this.ui.sportAccent(m.league, m.group)));
        if (narrow) {
            card.setPadding(this.ui.dp(18.0f), this.ui.dp(16.0f), this.ui.dp(18.0f), this.ui.dp(16.0f));
        } else {
            card.setPadding(this.ui.dp(22.0f), this.ui.dp(20.0f), this.ui.dp(22.0f), this.ui.dp(18.0f));
        }
        if (m.group.equals("F1")) {
            TextView title = this.ui.text(EnglishDisplay.text(m.displayTitle()), 32, this.ui.text, true);
            title.setLetterSpacing(-0.03f);
            card.addView((View)title);
            this.ui.gap(card, 8);
            TextView f1Meta = this.ui.text(padF1Meta(m), 13, m.live() ? this.ui.primary : this.ui.muted, false);
            f1Meta.setTag("pad-focus-f1-meta");
            card.addView((View)f1Meta);
        } else if (narrow) {
            LinearLayout stacked = this.ui.column();
            stacked.addView((View)this.padHeroTeamRow(m, true), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
            this.ui.gap(stacked, 8);
            stacked.addView((View)this.padHeroTeamRow(m, false), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
            this.ui.gap(stacked, 12);
            TextView meta = this.ui.text(padNarrowMeta(m), 11, m.live() ? this.ui.primary : this.ui.muted, false);
            meta.setTag("pad-focus-narrow-meta");
            meta.setGravity(Gravity.START);
            stacked.addView((View)meta);
            card.addView((View)stacked);
        } else {
            LinearLayout row = this.ui.row();
            row.setGravity(17);
            row.addView((View)this.padHeroTeam(m, true), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
            LinearLayout center = this.ui.column();
            center.setGravity(17);
            Ui.ScoreText score = this.ui.score(m.displayScore(true), m.displayScore(false), m.hasScore(), 46);
            score.setResultColors(this.ui.scoreColor(m, true), this.ui.scoreColor(m, false));
            score.setContentDescription(m.hasScore()
                ? "Score: " + EnglishDisplay.text(m.home) + " " + m.displayScore(true) + ", " + EnglishDisplay.text(m.away) + " " + m.displayScore(false)
                : "The source has not provided a score");
            center.addView((View)score, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
            this.ui.gap(center, 10);
            TextView state = this.ui.text(EnglishDisplay.text(m.status), 14, m.live() ? this.ui.primary : this.ui.muted, true);
            state.setTag("pad-focus-status");
            state.setGravity(17);
            center.addView((View)state);
            this.ui.gap(center, 4);
            TextView when = this.ui.text(m.timeConfirmed ? MainActivity.formatInstant(m.start, "M月d日 HH:mm") + " · Beijing time" : "Time TBD", 11, this.ui.muted, false);
            when.setGravity(17);
            center.addView((View)when);
            row.addView((View)center, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(188.0f), -2));
            row.addView((View)this.padHeroTeam(m, false), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
            card.addView((View)row);
        }
        if (this.padDetail != null && !this.padDetail.goals.isEmpty() && this.padDetail.match.id.equals(m.id)) {
            LinearLayout scorers = this.ui.goalScorersStrip(m, this.padDetail.goals);
            if (scorers != null) {
                this.ui.gap(card, 12);
                card.addView(scorers, new LinearLayout.LayoutParams(-1, -2));
            }
        }
        WatchLinks.Link watch = this.watches.link(m);
        if (watch != null) {
            this.ui.gap(card, 14);
            Ui.WatchRow row = this.ui.watchRow();
            row.bind(watch, m.live(), () -> this.openWatch(m));
            row.setContentDescription(watchDescription(watch));
            card.addView((View)row);
            this.ui.gap(card, 10);
        } else {
            this.ui.gap(card, 14);
        }
        LinearLayout actions = this.ui.row();
        TextView open = this.ui.button("Match details", true, () -> this.matchDetail(m));
        open.setPadding(this.ui.dp(22.0f), this.ui.dp(11.0f), this.ui.dp(22.0f), this.ui.dp(11.0f));
        actions.addView((View)open, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-2, -2));
        boolean isSubscribed = this.subscribed.contains(m.id);
        canSubscribe = m.scheduled() && m.timeConfirmed && m.start > System.currentTimeMillis();
        if (canSubscribe) {
            View gap = new View((Context)this);
            actions.addView(gap, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(8.0f), 1));
            FrameLayout subscribe = this.padIconAction(isSubscribed ? "calendar-check" : "calendar-add", isSubscribed ? "Remove match reminder" : "Add match reminder", () -> this.toggleSubscription(m));
            this.ui.clickable((View)subscribe, this.ui.tonal, 24);
            subscribe.setSelected(isSubscribed);
            subscribe.setTooltipText(isSubscribed ? "Remove reminder" : "Add reminder");
            ((Ui.Icon)subscribe.getChildAt(0)).set(isSubscribed ? "calendar-check" : "calendar-add", isSubscribed ? this.ui.primary : this.ui.muted);
            actions.addView(subscribe, new LinearLayout.LayoutParams(this.ui.dp(48), this.ui.dp(48)));
        }
        View gap2 = new View((Context)this);
        actions.addView(gap2, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(8.0f), 1));
        FrameLayout refresh = this.padIconAction("refresh", "Refresh match data", () -> this.loadPadDetail(m, true));
        this.ui.clickable((View)refresh, this.ui.tonal, 24);
        actions.addView(refresh, new LinearLayout.LayoutParams(this.ui.dp(48), this.ui.dp(48)));
        card.addView((View)actions);
        return card;
    }

    private LinearLayout padHeroTeamRow(Match m, boolean home) {
        LinearLayout row = this.ui.row();
        row.setMinimumHeight(this.ui.dp(52.0f));
        String name = home ? m.home : m.away;
        String logo = home ? m.homeLogo : m.awayLogo;
        String id = home ? m.homeId : m.awayId;
        FrameLayout slot = new FrameLayout((Context)this);
        slot.addView((View)Logos.view(this.ui, logo, name, 44), (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(this.ui.dp(44.0f), this.ui.dp(44.0f), Gravity.START | Gravity.CENTER_VERTICAL));
        boolean available = id != null && !id.isBlank();
        slot.setFocusable(available);
        slot.setClickable(available);
        slot.setContentDescription(available ? "View " + EnglishDisplay.text(name) + " schedule" : null);
        slot.setOnClickListener(available ? v -> this.openTeam(m, home) : null);
        row.addView((View)slot, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(52.0f), this.ui.dp(52.0f)));
        LinearLayout nameCol = this.ui.column();
        TextView label = Ui.singleLine(this.ui.text(EnglishDisplay.text(name), 16, this.ui.text, true));
        label.setLetterSpacing(-0.02f);
        nameCol.addView(label);
        String rec = home ? m.homeRecord : m.awayRecord;
        if (!rec.isEmpty()) {
            nameCol.addView(Ui.singleLine(this.ui.text(rec, 11, this.ui.dim, false)));
        }
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        labelParams.setMarginStart(this.ui.dp(8.0f));
        labelParams.setMarginEnd(this.ui.dp(8.0f));
        row.addView((View)nameCol, (ViewGroup.LayoutParams)labelParams);
        TextView score = Ui.singleLine(this.ui.fixtureScore(m.hasScore() ? m.displayScore(home) : "—", 28, this.ui.scoreColor(m, home)));
        score.setMinWidth(this.ui.scoreColumnWidth(28,m));
        score.setGravity(Gravity.CENTER);
        score.setIncludeFontPadding(false);
        row.addView((View)score);
        row.setContentDescription((CharSequence)(EnglishDisplay.text(name) + (m.hasScore() ? ", score " + m.displayScore(home) : ", score not provided")));
        return row;
    }

    private LinearLayout padHeroTeam(Match m, boolean home) {
        LinearLayout side = this.ui.column();
        side.setGravity(17);
        String name = home ? m.home : m.away;
        String logo = home ? m.homeLogo : m.awayLogo;
        String id = home ? m.homeId : m.awayId;
        String rec = home ? m.homeRecord : m.awayRecord;
        FrameLayout slot = new FrameLayout((Context)this);
        slot.addView((View)Logos.view(this.ui, logo, name, 64), (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(this.ui.dp(64.0f), this.ui.dp(64.0f), 17));
        boolean available = id != null && !id.isBlank();
        slot.setFocusable(available);
        slot.setClickable(available);
        slot.setContentDescription(available ? "View " + EnglishDisplay.text(name) + " schedule" : null);
        slot.setOnClickListener(available ? v -> this.openTeam(m, home) : null);
        side.addView((View)slot, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(68.0f), this.ui.dp(68.0f)));
        this.ui.gap(side, 8);
        TextView label = this.ui.text(EnglishDisplay.text(name), 16, this.ui.text, true);
        label.setGravity(17);
        label.setMaxLines(2);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setLetterSpacing(-0.02f);
        side.addView((View)label, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        if (!rec.isEmpty()) {
            this.ui.gap(side, 2);
            TextView rView = Ui.singleLine(this.ui.text(rec, 11, this.ui.dim, false));
            rView.setGravity(17);
            side.addView(rView, new LinearLayout.LayoutParams(-1, -2));
        }
        return side;
    }

    private void padDetailColumns(LinearLayout left, LinearLayout right, MatchDetail d, Match m) {
        LinearLayout card;
        if (!d.stats.isEmpty()) {
            this.padFocusHeading(left, "Match stats");
            card = this.padSurface();
            LinearLayout names = this.ui.row();
            TextView home = Ui.singleLine(this.ui.text(EnglishDisplay.text(m.home), 11, this.ui.text, true));
            TextView away = Ui.singleLine(this.ui.text(EnglishDisplay.text(m.away), 11, this.ui.text, true));
            away.setGravity(0x800005);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(0, -2, 1.0f);
            hlp.setMarginEnd(this.ui.dp(8));
            names.addView((View)home, (ViewGroup.LayoutParams)hlp);
            names.addView((View)away, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
            card.addView((View)names);
            this.ui.gap(card, 8);
            card.addView(this.ui.divider());
            for (int i = 0; i < Math.min(6, d.stats.size()); ++i) {
                this.padFocusStat(card, d.stats.get(i));
                if (i >= Math.min(6, d.stats.size()) - 1) continue;
                this.ui.gap(card, 8);
                card.addView(this.ui.divider());
            }
            left.addView((View)card);
            this.ui.gap(left, 24);
        }
        if (!d.facts.isEmpty()) {
            this.padFocusHeading(left, "Match info");
            card = this.padSurface();
            LinkedHashMap<String, List> facts = new LinkedHashMap<String, List>();
            for (MatchDetail.Fact f : d.facts) {
                String label = DetailParsers.label(DetailParsers.plain(f.label()));
                if (label.equals("阵容资料版本")) continue;
                String value = DetailParsers.plain(f.value());
                if (value.isBlank()) continue;
                facts.computeIfAbsent(EnglishDisplay.text(label.isBlank() ? "Info" : label), k -> new ArrayList()).add(EnglishDisplay.text(value));
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
                card.addView((View)this.ui.text("The source has not provided more match information", 12, this.ui.muted, false));
            }
            left.addView((View)card);
            this.ui.gap(left, 24);
        }
        if (!d.events.isEmpty()) {
            this.padFocusHeading(right, "Latest plays");
            card = this.padSurface();
            for (int i = 0; i < Math.min(5, d.events.size()); ++i) {
                if (i > 0) {
                    card.addView(this.ui.divider());
                }
                this.padFocusEvent(card, d.events.get(i));
            }
            right.addView((View)card);
            this.ui.gap(right, 24);
        }
        this.padFocusHeading(right, "Source");
        LinearLayout source = this.ui.column();
        source.addView((View)this.ui.text(EnglishDisplay.text(d.source), 13, this.ui.text, true));
        this.ui.gap(source, 4);
        source.addView((View)this.ui.text(padFetchedText(m, d), 11, this.ui.muted, false));
        right.addView((View)source);
        if (!m.url.isBlank()) {
            this.ui.gap(right, 10);
            right.addView((View)this.ui.textButton("Open source page", () -> {
                try {
                    this.startActivity(new Intent("android.intent.action.VIEW", Uri.parse((String)m.url)));
                }
                catch (RuntimeException e) {
                    this.message("Couldn't open source", "No browser is available on this device.");
                }
            }), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void padFocusHeading(LinearLayout into, String title) {
        into.addView((View)this.ui.text(title, 15, this.ui.text, true));
        this.ui.gap(into, 10);
    }

    private void padFocusStat(LinearLayout card, MatchDetail.Stat stat) {
        card.addView(this.ui.statComparisonRow(EnglishDisplay.text(stat.label()), EnglishDisplay.text(stat.home()), EnglishDisplay.text(stat.away())));
    }

    private void padFocusEvent(LinearLayout card, MatchDetail.Event e) {
        LinearLayout row = this.ui.row();
        row.setGravity(48);
        row.setPadding(0, this.ui.dp(8.0f), 0, this.ui.dp(8.0f));
        TextView clock = Ui.singleLine(this.ui.text(EnglishDisplay.text(e.time()), 11, this.ui.muted, true));
        clock.setMinWidth(this.ui.dp(50.0f));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.setMarginEnd(this.ui.dp(10.0f));
        row.addView((View)clock, (ViewGroup.LayoutParams)clp);
        LinearLayout words = this.ui.column();
        String title = EnglishDisplay.text(DetailParsers.plain(e.title()));
        String text = EnglishDisplay.text(DetailParsers.plain(e.text()));
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
        updated = latest > 0L ? "Updated " + MainActivity.formatInstant(latest, "HH:mm") : "Loading";
        if (this.padListStatus != null) {
            this.updateCs2EventsAction();
            String text = matchCount(all.size()) + " · " + (this.group.equals("全部") ? "All sports" : EnglishDisplay.sport(this.group)) + " · " + updated;
            this.padListStatus.setText((CharSequence)text);
            boolean failed = this.snapshot.failed();
            this.padListStatus.setTextColor(failed ? this.ui.error : this.ui.muted);
        }
        if (this.padDayNumber != null) {
            this.padDayNumber.setText((CharSequence)String.valueOf(this.from().getDayOfMonth()));
            String month = EnglishDisplay.text(Formats.YEAR_MONTH.format(this.from()));
            String week = EnglishDisplay.text(Formats.WEEKDAY.format(this.from()));
            this.padDayMeta.setText((CharSequence)(month + " · " + week + (this.dayOffset == 0 ? " · Today" : "")));
        }
        if (this.padRailDay != null) {
            this.padRailDay.setText((CharSequence)Formats.MONTH_DAY_DOT.format(this.from()));
        }
        if (this.padRailWeek != null) {
            this.padRailWeek.setText((CharSequence)(EnglishDisplay.text(Formats.WEEKDAY.format(this.from())) + " · Beijing time"));
        }
        for (String g : GROUPS) {
            int c;
            boolean selected = this.tab == 0 && g.equals(this.group);
            View filter = this.railFilters.get(g);
            if (filter != null) {
                filter.setSelected(selected);
                filter.setContentDescription(EnglishDisplay.sport(g) + (this.tab == 0 ? selected ? ", selected" : ", filter sport" : ", show scores for this sport"));
                this.railBars.get(g).setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
                TextView label = this.railLabels.get(g);
                label.setTextColor(selected ? this.ui.text : this.ui.muted);
                label.setTypeface(selected ? this.ui.medium : this.ui.regular);
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
        host.setPadding(this.ui.dp(stacked ? 20 : 32), this.ui.dp(24.0f), this.ui.dp(stacked ? 20 : 32), this.ui.dp(32.0f));
        scroll.addView((View)host);
        shell.addView((View)scroll, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -1, 1.0f));
        LinearLayout titleRow = this.ui.row();
        TextView title = this.ui.text("Settings", 28, this.ui.text, true);
        title.setLetterSpacing(-0.04f);
        titleRow.addView((View)title, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));

        host.addView((View)titleRow);
        this.ui.gap(host, 6);
        host.addView((View)this.ui.text("Manage calendar alerts, appearance, updates, and sources", 13, this.ui.muted, false));
        this.ui.gap(host, 18);
        long checked = this.prefs.getLong("calendar_checked", 0L);
        LinearLayout left = this.ui.column(), right = this.ui.column();
        host.addView(this.ui.columns(left, right, 360, 24), new LinearLayout.LayoutParams(-1, -2));
        this.padSettingsHeading(left, "Calendar & alerts");
        LinearLayout calendar = this.ui.column();
        calendar.setBackground((Drawable)this.ui.panel());
        this.settingRow(calendar, "calendar-target", "Reminder calendar", this.calendarTargetDetail(), this::calendarChoice);
        this.settingRow(calendar, "subscriptions", "Subscribed matches", this.subscriptionsDetail(), this::subscribedList);
        this.settingRow(calendar, "sync-subscriptions", this.calendarBusy ? "Checking…" : "Check subscriptions", checked > 0L ? "Last checked " + MainActivity.formatInstant(checked, "M月d日 HH:mm") : "Sync postponements, cancellations, and final results", () -> this.syncSubscriptions(true));
        this.settingRow(calendar, "calendar-open", "Open system calendar", "View events and reminders", this::openCalendar);
        this.settingRow(calendar, "calendar-cloud", "Calendar cloud sync", EnglishDisplay.text(CalendarCloudSync.status(this)), this::manageCloudSync);
        left.addView((View)calendar);
        this.ui.gap(left, 18);
        this.padSettingsHeading(left, "Appearance");
        LinearLayout appearance = this.ui.column();
        appearance.setBackground((Drawable)this.ui.panel());
        appearance.setPadding(this.ui.dp(16.0f), this.ui.dp(16.0f), this.ui.dp(16.0f), this.ui.dp(16.0f));
        appearance.addView((View)this.ui.text("System follows the tablet's light or dark setting.", 12, this.ui.muted, false));
        this.ui.gap(appearance, 12);
        String theme = this.prefs.getString("theme", "dark");
        LinearLayout themeRow = this.ui.row();
        String[] values = new String[]{"dark", "light", "system"};
        String[] labels = new String[]{"Dark", "Light", "System"};
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
        left.addView((View)appearance);
        this.ui.gap(left, 18);
        this.padSettingsHeading(left, "Updates & about");
        LinearLayout updates = this.ui.column();
        updates.setBackground((Drawable)this.ui.panel());
        this.settingRow(updates, "app-update", "Check for updates", "Current version " + BuildConfig.VERSION_NAME, () -> this.startActivity(new Intent((Context)this, UpdateActivity.class)));
        this.settingRow(updates, "help", "Help", "Calendar reminders and data notes", this::help);
        View aboutRule = this.ui.divider();
        LinearLayout.LayoutParams aboutRuleParams = new LinearLayout.LayoutParams(-1, this.ui.dp(1.0f));
        aboutRuleParams.setMarginStart(this.ui.dp(14.0f));
        aboutRuleParams.setMarginEnd(this.ui.dp(14.0f));
        updates.addView(aboutRule, (ViewGroup.LayoutParams)aboutRuleParams);
        TextView about = this.ui.text("Native Android · No ads · No sign-in · Beijing time\nSchedule and scores use public sources. Failed refreshes keep the last confirmed data.", 11, this.ui.muted, false);
        about.setPadding(this.ui.dp(14.0f), this.ui.dp(12.0f), this.ui.dp(14.0f), this.ui.dp(14.0f));
        updates.addView((View)about);
        left.addView((View)updates);
        this.padSettingsHeading(right, "Sources & diagnostics");
        LinearLayout sources = this.ui.column();
        sources.setBackground((Drawable)this.ui.panel());
        List<Repository.Batch> orderedSources = this.orderedSourceBatches(sourceSnapshot);
        if (sourceSnapshot == null || orderedSources.isEmpty()) {
            sources.addView((View)this.padEmptySetting(sourceSnapshot == null ? "Loading source status…" : "No sources loaded"));
        } else {
            for (int i = 0; i < orderedSources.size(); ++i) {
                Repository.Batch b = orderedSources.get(i);
                LinearLayout row = this.ui.row();
                row.setPadding(this.ui.dp(14.0f), this.ui.dp(8.0f), this.ui.dp(14.0f), this.ui.dp(8.0f));
                View dot = new View((Context)this);
                dot.setBackground((Drawable)this.ui.shape(b.error.isEmpty() ? this.ui.primary : this.ui.error, 4));
                row.addView(dot, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(6.0f), this.ui.dp(6.0f)));
                TextView name = this.ui.text(EnglishDisplay.text(b.competition.name), 13, this.ui.text, true);
                LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1.0f);
                np.setMarginStart(this.ui.dp(10.0f));
                row.addView((View)name, (ViewGroup.LayoutParams)np);
                String state = b.error.isEmpty() ? (b.fetched > 0L ? MainActivity.formatInstant(b.fetched, "HH:mm") + " · " + matchCount(b.matches.size()) : "Not fetched yet") : EnglishDisplay.text(b.error);
                TextView value = this.ui.text(state, 11, b.error.isEmpty() ? this.ui.muted : this.ui.error, false);
                value.setGravity(0x800005);
                row.addView(value, new LinearLayout.LayoutParams(0, -2, 1));
                sources.addView((View)row);
                if (i == orderedSources.size() - 1) continue;
                View line = this.ui.divider();
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, this.ui.dp(1.0f));
                lp.setMarginStart(this.ui.dp(30.0f));
                lp.setMarginEnd(this.ui.dp(14.0f));
                sources.addView(line, (ViewGroup.LayoutParams)lp);
            }
        }
        right.addView((View)sources);
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
