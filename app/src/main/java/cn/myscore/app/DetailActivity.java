/** Full-screen, source-backed match centre with a tablet dossier + tabbed reading pane. */

package cn.myscore.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
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
import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import cn.myscore.app.BuildConfig;
import cn.myscore.app.DetailParsers;
import cn.myscore.app.DetailRepository;
import cn.myscore.app.DetailRows;
import cn.myscore.app.DetailTableView;
import cn.myscore.app.FootballLineupView;
import cn.myscore.app.Logos;
import cn.myscore.app.Match;
import cn.myscore.app.MatchDetail;
import cn.myscore.app.PlayerStatsView;
import cn.myscore.app.R;
import cn.myscore.app.RefreshPolicy;
import cn.myscore.app.Rules;
import cn.myscore.app.Sheet;
import cn.myscore.app.Team;
import cn.myscore.app.TeamActivity;
import cn.myscore.app.TeamPanelsView;
import cn.myscore.app.Ui;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

public final class DetailActivity
extends Activity {
    static final String EXTRA_PARENT_TOURNAMENT = "parentTournament";
    private Ui ui;
    private Match match;
    private MatchDetail detail;
    private DetailRepository repository;
    private WatchRepository watches;
    private WatchLauncher watchLauncher;
    private Ui.WatchRow headerWatch;
    private View headerWatchGap;
    private LinearLayout root;
    private LinearLayout body;
    private LinearLayout content;
    private ScrollView scroll;
    private TextView status;
    private TextView headerTitle;
    private TextView headerState;
    private LinearLayout headerLivePill;
    private TextView headerLiveText;
    private TextView headerStart;
    private TextView headerHome;
    private TextView headerAway;
    private TextView headerHomeScore;
    private TextView headerAwayScore;
    private Ui.ScoreText headerScore;
    private Logos.Crest headerHomeCrest;
    private Logos.Crest headerAwayCrest;
    private FrameLayout headerHomeSlot;
    private FrameLayout headerAwaySlot;
    private LinearLayout headerRemindBtn;
    private Ui.Icon headerRemindIcon;
    private TextView headerRemindLabel;
    private TextView[] tabLabels;
    private String[] tabKeys;
    private String[] tabNames;
    private String tab = "overview";
    private String error = "";
    private boolean busy;
    private boolean active;
    private boolean chromeBuilt;
    private boolean fingerDown;
    private int failures;
    private boolean pendingForce;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService calendarWorker = Executors.newSingleThreadExecutor();
    private CalendarSync calendarSync;
    private boolean calendarBusy;
    private boolean pendingSubscribe;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener calendarListener = (prefs, key) -> {
        if (key == null || key.startsWith("match:")) {
            this.handler.post(() -> {
                if (this.active && !this.isDestroyed()) {
                    this.updateRemindControl();
                    if (this.pad) {
                        this.renderedPadLeft = "";
                        this.updatePadChrome();
                    }
                }
            });
        }
    };
    private final Runnable tick = () -> this.load(false);
    private String rendered = "";
    private long lastScroll;
    private String teamFilter = "all";
    private String categoryFilter = "";
    private String sortColumn = "";
    private String mapFilter = "";
    private boolean descending = true;
    private int metricsX;
    private int eventsLimit = 50;
    private int playerLimit = 16;
    private int pendingScrollY = -1;
    private final Map<String,Integer> tableScroll = new LinkedHashMap<>();
    private String renderedPadLeft = "";
    private final Runnable update = () -> {
        if (this.active && !this.isDestroyed()) {
            this.render(true);
        }
    };
    private boolean pad;
    private LinearLayout padLeftHost;
    private LinearLayout padTabs;
    private ScrollView padLeftScroll;
    private TextView padTitle;
    private TextView padSubtitle;
    private AutoCloseable networkWatch = () -> {};

    public void onCreate(Bundle saved) {
        boolean dark = Ui.isDark((Context)this);
        this.setTheme(dark ? R.style.AppThemeDark : R.style.AppTheme);
        super.onCreate(saved);
        this.ui = new Ui((Context)this, dark);
        this.pad = Ui.isPad((Context)this) && getResources().getConfiguration().screenWidthDp >= 1000;
        try {
            this.match = Match.fromJson(new JSONObject(this.getIntent().getStringExtra("match")));
            if (this.match.id.isBlank() || Competition.find(this.match.league) == null) {
                throw new IllegalArgumentException();
            }
        }
        catch (Exception e) {
            this.finish();
            return;
        }
        if (saved != null) {
            this.tab = saved.getString("tab", "overview");
            this.teamFilter = saved.getString("team", "all");
            this.categoryFilter = saved.getString("category", "");
            this.sortColumn = saved.getString("sort", "");
            this.descending = saved.getBoolean("descending", true);
            this.mapFilter = saved.getString("map", "");
            this.metricsX = saved.getInt("metricsX");
            this.eventsLimit = saved.getInt("eventsLimit", 50);
            this.playerLimit = saved.getInt("playerLimit", 16);
            this.pendingScrollY = saved.getInt("scrollY", -1);
            ArrayList<String> tableKeys = saved.getStringArrayList("table_keys");
            ArrayList<Integer> tableValues = saved.getIntegerArrayList("table_scroll");
            if (tableKeys != null && tableValues != null) {
                for (int i = 0; i < Math.min(tableKeys.size(), tableValues.size()); i++) {
                    this.tableScroll.put(tableKeys.get(i), tableValues.get(i));
                }
            }
        }
        if (this.match.group.equals("F1") && this.tab.equals("events")) {
            this.tab = "overview";
        }
        this.repository = new DetailRepository((Context)this);
        this.calendarSync = new CalendarSync((Context)this);
        this.getSharedPreferences("calendar-links", 0).registerOnSharedPreferenceChangeListener(this.calendarListener);
        this.watches = WatchRepository.get(this);
        this.watchLauncher = new WatchLauncher(this, this.watches);
        this.getWindow().setStatusBarColor(this.ui.bg);
        this.getWindow().setNavigationBarColor(this.ui.bg);
        this.getWindow().setBackgroundDrawable((Drawable)new ColorDrawable(this.ui.bg));
        if (Build.VERSION.SDK_INT >= 30) {
            this.getWindow().setDecorFitsSystemWindows(false);
        }
        this.root = this.ui.column();
        this.setContentView((View)this.root);
        this.root.setOnApplyWindowInsetsListener((v, insets) -> {
            int inset = this.pad ? 0 : Math.max(this.ui.pageInset(), this.ui.dp(Math.max(0, (getResources().getConfiguration().screenWidthDp - 720) / 2f)));
            if (Build.VERSION.SDK_INT >= 30) {
                Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(i.left + inset, i.top, i.right + inset, i.bottom);
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft() + inset, insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight() + inset, insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        this.root.requestApplyInsets();
        this.render(false);
    }

    public void onResume() {
        super.onResume();
        this.active = true;
        this.closeNetworkWatch();
        this.networkWatch = Http.onNetworkChanged(() -> this.handler.post(() -> {
            if (!this.active || this.isDestroyed()) {
                return;
            }
            if (!this.error.isEmpty()) {
                this.load(true);
            }
            this.resolveWatch();
        }));
        if (this.repository != null && !this.isFinishing()) {
            this.render(true);
            this.load(false);
            this.resolveWatch();
        }
    }

    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) {
            Ui.highRefresh(this.getWindow());
        }
    }

    public void onPause() {
        this.active = false;
        this.closeNetworkWatch();
        this.fingerDown = false;
        this.handler.removeCallbacks(this.tick);
        this.handler.removeCallbacks(this.update);
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
        Sheet.dismissAll(this);
        this.handler.removeCallbacksAndMessages(null);
        this.worker.shutdownNow();
        this.calendarWorker.shutdownNow();
        super.onDestroy();
    }

    private boolean canRemindMatch() {
        if (this.match == null || this.calendarSync == null) return false;
        if (this.calendarSync.isSubscribed(this.match.id)) return true;
        return this.match.scheduled() && this.match.timeConfirmed && this.match.start > System.currentTimeMillis();
    }

    private void toggleCalendar() {
        if (this.calendarBusy || this.match == null || this.calendarSync == null) return;
        boolean remove = this.calendarSync.isSubscribed(this.match.id);
        if (!remove && !(this.match.scheduled() && this.match.timeConfirmed && this.match.start > System.currentTimeMillis())) {
            Sheet.message(this, "Can't set reminder", "This match has no confirmed kickoff time or has already started.");
            return;
        }
        if (!CalendarSync.permitted(this)) {
            this.pendingSubscribe = true;
            this.requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR}, 44);
            return;
        }
        if (remove) {
            Sheet.confirm(this, "Remove reminder", "Remove “" + this.match.displayTitle() + "” from your calendar?", "Remove", () -> this.writeCalendar(true));
        } else {
            this.writeCalendar(false);
        }
    }

    private void writeCalendar(boolean remove) {
        if (this.calendarBusy || this.match == null) return;
        this.calendarBusy = true;
        this.updateRemindControl();
        Match target = this.match;
        this.calendarWorker.submit(() -> {
            CalendarSync.Result outcome;
            try {
                if (remove) {
                    outcome = this.calendarSync.unsubscribe(target.id);
                } else {
                    if (!this.calendarSync.hasCalendar(this.calendarSync.calendarId())) {
                        CalendarSync.CalendarChoice best = this.calendarSync.bestCalendar();
                        if (best == null) throw new IllegalStateException("Enable a local or system calendar first.");
                        this.calendarSync.chooseCalendar(best.id, best.name);
                    }
                    outcome = this.calendarSync.subscribe(target, System.currentTimeMillis());
                }
            } catch (RuntimeException e) {
                outcome = new CalendarSync.Result();
                outcome.failed = 1;
                outcome.error = e.getMessage() == null ? "Calendar write failed" : e.getMessage();
            }
            CalendarSync.Result completed = outcome;
            this.handler.post(() -> {
                this.calendarBusy = false;
                if (this.isDestroyed()) return;
                this.updateRemindControl();
                if (this.pad) {
                    this.renderedPadLeft = "";
                    this.updatePadChrome();
                }
                CalendarJob.schedule(this);
                if (completed.failed > 0) {
                    Sheet.message(this, remove ? "Couldn't remove reminder" : "Couldn't save reminder", completed.error);
                } else {
                    android.widget.Toast.makeText(this, remove ? "Removed from calendar" : "Saved · 5-min reminder set", android.widget.Toast.LENGTH_SHORT).show();
                    if (!remove) ResultNotify.requestIfNeeded(this);
                }
            });
        });
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != 44) return;
        boolean queued = this.pendingSubscribe;
        this.pendingSubscribe = false;
        if (CalendarSync.permitted(this)) {
            CalendarUpdates.start(this);
            CalendarCloudSync.opened(this);
            if (queued) this.toggleCalendar();
        } else {
            Sheet.message(this, "Calendar permission required", "Allow calendar access to save match reminders.");
        }
    }

    private LinearLayout buildRemindPill() {
        LinearLayout pill = this.ui.row();
        pill.setGravity(Gravity.CENTER);
        pill.setMinimumHeight(this.ui.dp(38));
        pill.setPadding(this.ui.dp(13), this.ui.dp(7), this.ui.dp(14), this.ui.dp(7));
        Ui.Icon ic = new Ui.Icon(this, "bell", this.ui.text);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(this.ui.dp(15), this.ui.dp(15));
        ip.setMarginEnd(this.ui.dp(6));
        pill.addView(ic, ip);
        TextView lbl = this.ui.text("Remind", 12, this.ui.text, true);
        pill.addView(lbl);
        pill.setFocusable(true);
        pill.setOnClickListener(v -> this.toggleCalendar());
        this.styleRemindPill(pill, ic, lbl);
        return pill;
    }

    private void styleRemindPill(LinearLayout pill, Ui.Icon ic, TextView lbl) {
        if (pill == null) return;
        boolean eligible = this.canRemindMatch();
        pill.setVisibility(eligible ? View.VISIBLE : View.GONE);
        if (!eligible) return;
        boolean saved = this.calendarSync != null && this.calendarSync.isSubscribed(this.match.id);
        ic.set(saved ? "calendar-check" : "bell", saved ? this.ui.primary : this.ui.text);
        lbl.setText(saved ? "Saved" : "Remind");
        lbl.setTextColor(saved ? this.ui.primary : this.ui.text);
        this.ui.clickableDrawable(pill, saved
            ? this.ui.outlined(this.ui.primarySoft, Ui.alpha(this.ui.primary, 100), Ui.PILL_RADIUS)
            : this.ui.outlined(this.ui.tonal, this.ui.border, Ui.PILL_RADIUS), Ui.PILL_RADIUS);
        pill.setEnabled(!this.calendarBusy);
        pill.setAlpha(this.calendarBusy ? 0.5f : 1f);
        pill.setContentDescription(saved ? "Remove calendar reminder" : "Set calendar reminder");
    }

    private void updateRemindControl() {
        this.styleRemindPill(this.headerRemindBtn, this.headerRemindIcon, this.headerRemindLabel);
    }

    private LinearLayout buildCompetitionPill() {
        boolean tournament = "cs2".equals(this.match.league) && this.match.eventId.matches("csgo_tt_[0-9]+");
        if (tournament && this.match.eventId.equals(this.getIntent().getStringExtra(EXTRA_PARENT_TOURNAMENT))) return null;
        String label = tournament ? "Bracket" : StandingsActivity.competitionLinkLabel(this.match.league);
        if (label.isBlank()) return null;
        String shortLabel = tournament ? "Tournament" : ("fa".equals(this.match.league) || "efl".equals(this.match.league)) ? "Cup Rounds" : "cs2".equals(this.match.league) ? "Rankings" : "Standings";
        int accent = this.ui.sportAccent(this.match.league, this.match.group);
        LinearLayout pill = this.ui.row();
        pill.setGravity(Gravity.CENTER);
        pill.setMinimumHeight(this.ui.dp(38));
        pill.setPadding(this.ui.dp(13), this.ui.dp(7), this.ui.dp(12), this.ui.dp(7));
        Ui.Icon ic = new Ui.Icon(this, tournament ? "bracket" : "trophy", accent);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(this.ui.dp(15), this.ui.dp(15));
        ip.setMarginEnd(this.ui.dp(6));
        pill.addView(ic, ip);
        pill.addView(this.ui.text(shortLabel, 12, this.ui.text, true));
        Ui.Icon chev = new Ui.Icon(this, "chevron", this.ui.muted);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(this.ui.dp(13), this.ui.dp(13));
        cp.setMarginStart(this.ui.dp(4));
        pill.addView(chev, cp);
        this.ui.clickableDrawable(pill, this.ui.outlined(this.ui.tonal, this.ui.border, Ui.PILL_RADIUS), Ui.PILL_RADIUS);
        pill.setTag(tournament ? "cs2-tournament-overview" : "match-competition");
        pill.setFocusable(true);
        pill.setOnClickListener(v -> {
            if (tournament) {
                this.startActivity(new Intent(this, Cs2EventActivity.class)
                    .putExtra("event", this.match.eventId)
                    .putExtra("name", this.match.event)
                    .putExtra("day", this.match.day() == null ? "" : this.match.day().toString()));
            } else {
                this.startActivity(StandingsActivity.forCompetition(this, this.match.league));
            }
        });
        return pill;
    }

    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("tab", this.tab);
        state.putString("team", this.teamFilter);
        state.putString("category", this.categoryFilter);
        state.putString("sort", this.sortColumn);
        state.putBoolean("descending", this.descending);
        state.putString("map", this.mapFilter);
        state.putInt("metricsX", this.metricsX);
        state.putInt("eventsLimit", this.eventsLimit);
        state.putInt("playerLimit", this.playerLimit);
        state.putInt("scrollY", this.scroll == null ? this.pendingScrollY : this.scroll.getScrollY());
        ArrayList<String> tableKeys = new ArrayList<>(this.tableScroll.keySet());
        ArrayList<Integer> tableValues = new ArrayList<>();
        for (String key : tableKeys) tableValues.add(this.tableScroll.get(key));
        state.putStringArrayList("table_keys", tableKeys);
        state.putIntegerArrayList("table_scroll", tableValues);
    }

    private void load(boolean force) {
        if (force) {
            Logos.retryFailures();
            this.rendered = "";
        }
        this.handler.removeCallbacks(this.tick);
        if (this.busy || !this.active) {
            this.pendingForce |= force;
            return;
        }
        boolean forced = force || this.pendingForce;
        this.pendingForce = false;
        this.busy = true;
        if (this.status != null) {
            this.status.setText((CharSequence)"Refreshing match details…");
        }
        Match requested = this.match;
        boolean needsCache = this.detail == null;
        this.worker.submit(() -> {
            MatchDetail cached;
            if (needsCache && (cached = this.repository.cached(requested)) != null) {
                this.handler.post(() -> {
                    if (this.isDestroyed()) {
                        return;
                    }
                    this.detail = cached;
                    if (cached.match.updated >= this.match.updated) {
                        this.match = cached.match;
                    }
                    if (this.active) {
                        this.render(true);
                    }
                    this.enrichProfiles(cached);
                });
            }
            MatchDetail next = null;
            String problem = "";
            try {
                next = this.repository.fetch(requested, forced);
            }
            catch (Exception e) {
                String message = displayText(e.getMessage());
                problem = message.isBlank() ? "Network connection failed" : message;
            }
            MatchDetail result = next;
            String failure = problem;
            this.handler.post(() -> {
                this.busy = false;
                if (this.isDestroyed()) {
                    return;
                }
                this.error = failure;
                int n = this.failures = failure.isEmpty() ? 0 : this.failures + 1;
                if (result != null) {
                    this.detail = result;
                    // The repository may answer from a shared/older cache; never roll the header
                    // back to an older match snapshot.
                    if (result.match.updated >= this.match.updated) {
                        this.match = result.match;
                    }
                }
                if (this.active) {
                    this.render(true);
                    this.resolveWatch();
                    this.enrichProfiles(result != null ? result : this.detail);
                    if (this.pendingForce) {
                        this.load(true);
                        return;
                    }
                    if (!this.match.finalised() && !this.match.cancelled() || !failure.isEmpty()) {
                        long interval = RefreshPolicy.detail(this.match, System.currentTimeMillis());
                        long elapsed = result == null ? 0L : Math.max(0L, System.currentTimeMillis() - result.fetched);
                        this.handler.postDelayed(this.tick, RefreshPolicy.remaining(RefreshPolicy.retry(interval, this.failures), elapsed));
                    } else {
                        // A finished detail is normally frozen, but re-read it once after the
                        // long settled interval so a source correction is not lost forever.
                        long age = result == null ? 0L : Math.max(0L, System.currentTimeMillis() - result.fetched);
                        this.handler.postDelayed(this.tick, Math.max(60_000L, RefreshPolicy.SETTLED_RECHECK - age));
                    }
                }
            });
        });
    }

    private void enrichProfiles(MatchDetail target) {
        if (target == null) return;
        this.repository.enrichProfiles(target, enriched -> this.handler.post(() -> {
            if (!this.active || this.isFinishing() || this.isDestroyed()) return;
            if (enriched == target) return;
            // A cached preview and the network result can be different objects with the same
            // identity/fetch stamp; the enriched result must still replace either one.
            if (this.detail != target && (this.detail == null
                || !enriched.match.id.equals(this.detail.match.id)
                || enriched.fetched != this.detail.fetched)) return;
            this.detail = enriched;
            if (enriched.match.updated >= this.match.updated) this.match = enriched.match;
            this.render(true);
        }));
    }

    private FrameLayout iconButton(String kind, String description, Runnable click) {        FrameLayout b = new FrameLayout((Context)this);
        b.addView((View)new Ui.Icon((Context)this, kind, this.ui.text), (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(this.ui.dp(22.0f), this.ui.dp(22.0f), 17));
        this.ui.clickable((View)b, 0, 24);
        b.setContentDescription((CharSequence)description);
        b.setFocusable(true);
        b.setOnClickListener(v -> click.run());
        b.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(48.0f), this.ui.dp(48.0f)));
        return b;
    }

    private void render(boolean preserve) {
        boolean changed;
        boolean first;
        if (this.isFinishing() || this.isDestroyed()) {
            return;
        }
        if (preserve && (this.fingerDown || SystemClock.uptimeMillis() - this.lastScroll < 200L)) {
            this.handler.removeCallbacks(this.update);
            this.handler.postDelayed(this.update, 200L);
            return;
        }
        if (this.pad) {
            this.renderPad(preserve);
            return;
        }
        first = !this.chromeBuilt;
        if (first) {
            this.buildChrome();
        } else {
            this.updateChrome();
        }
        String key = this.bodyKey();
        long started = SystemClock.uptimeMillis();
        changed = first || !key.equals(this.rendered);
        if (changed) {
            this.rendered = key;
            if (BuildConfig.DEBUG) {
                Log.i((String)"MyScorePerf", (String)("detail body rebuilt tab=" + this.tab + " detail=" + (this.detail != null)));
            }
            this.rebuildBody(preserve);
        } else {
            this.updateStatus();
        }
        if (BuildConfig.DEBUG) {
            Log.i((String)"MyScorePerf", (String)("detail update ms=" + (SystemClock.uptimeMillis() - started) + " rebuilt=" + changed));
        }
    }

    private void renderPad(boolean preserve) {
        boolean changed;
        boolean first;
        first = !this.chromeBuilt;
        if (first) {
            this.buildPadChrome();
        }
        this.updatePadChrome();
        String key = this.bodyKey();
        changed = first || !key.equals(this.rendered);
        if (changed) {
            this.rendered = key;
            this.rebuildPadBody(preserve);
        }
        this.updateStatus();
    }

    private void buildPadChrome() {
        this.renderedPadLeft = "";
        this.root.removeAllViews();
        LinearLayout bar = this.ui.row();
        bar.setPadding(this.ui.dp(10.0f), this.ui.dp(6.0f), this.ui.dp(14.0f), this.ui.dp(6.0f));
        bar.addView((View)this.iconButton("back", "Back", this::finish));
        LinearLayout titles = this.ui.column();
        this.padTitle = this.ui.text("", 19, this.ui.text, true);
        this.padTitle.setLetterSpacing(-0.02f);
        titles.addView((View)this.padTitle);
        this.ui.gap(titles, 3);
        this.padSubtitle = this.ui.text("", 11, this.ui.muted, false);
        titles.addView((View)this.padSubtitle);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        tp.setMarginStart(this.ui.dp(6.0f));
        bar.addView((View)titles, (ViewGroup.LayoutParams)tp);
        bar.addView((View)this.iconButton("refresh", "Refresh match details", () -> this.load(true)));
        this.root.addView((View)bar);
        this.root.addView(this.ui.divider());
        LinearLayout work = this.ui.row();
        this.padLeftScroll = new ScrollView((Context)this);
        this.padLeftScroll.setFillViewport(true);
        this.padLeftScroll.setVerticalScrollBarEnabled(false);
        this.padLeftScroll.setClipToPadding(false);
        this.padLeftHost = this.ui.column();
        this.padLeftHost.setPadding(this.ui.dp(22.0f), this.ui.dp(18.0f), this.ui.dp(22.0f), this.ui.dp(24.0f));
        this.padLeftScroll.addView((View)this.padLeftHost);
        this.trackTouch(this.padLeftScroll);
        work.addView((View)this.padLeftScroll, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(376.0f), -1));
        work.addView(this.padRule());
        LinearLayout reading = this.ui.column();
        work.addView((View)reading, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -1, 1.0f));
        this.padTabs = this.ui.row();
        this.padTabs.setPadding(this.ui.dp(24.0f), this.ui.dp(10.0f), this.ui.dp(24.0f), this.ui.dp(10.0f));
        reading.addView((View)this.padTabs);
        reading.addView(this.ui.divider());
        this.scroll = new ScrollView((Context)this);
        this.scroll.setFillViewport(true);
        this.scroll.setVerticalScrollBarEnabled(false);
        this.scroll.setClipToPadding(false);
        this.scroll.setPadding(this.ui.dp(24.0f), this.ui.dp(14.0f), this.ui.dp(28.0f), this.ui.dp(28.0f));
        this.scroll.setOnScrollChangeListener((v, x, sy, ox, oy) -> {
            this.lastScroll = SystemClock.uptimeMillis();
        });
        this.trackTouch(this.scroll);
        this.body = this.ui.column();
        this.scroll.addView((View)this.body);
        reading.addView((View)this.scroll, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, 0, 1.0f));
        this.root.addView((View)work, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, 0, 1.0f));
        this.buildPadTabs();
        this.chromeBuilt = true;
    }

    @SuppressLint(value={"ClickableViewAccessibility"})
    private void trackTouch(View view) {
        view.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == android.view.MotionEvent.ACTION_DOWN) {
                this.fingerDown = true;
            } else if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) {
                this.fingerDown = false;
                this.lastScroll = SystemClock.uptimeMillis();
            }
            return false;
        });
    }

    private void buildPadTabs() {
        String[] stringArray;
        String[] stringArray2;
        this.padTabs.removeAllViews();
        boolean f1 = this.match.group.equals("F1");
        boolean cs = this.match.group.equals("CS2");
        if (cs && this.tab.equals("stats")) {
            this.tab = "lineup";
        }
        if (cs) {
            stringArray2 = new String[]{"overview", "lineup", "events"};
            stringArray = new String[]{"Overview", "Players", "Maps"};
        } else if (f1) {
            stringArray2 = new String[]{"overview", "lineup", "stats"};
            stringArray = new String[]{"Overview", "Drivers", "Results"};
        } else {
            stringArray2 = new String[]{"overview", "lineup", "stats", "events"};
            stringArray = new String[]{"Overview", this.lineupName(), "Stats", "Events"};
        }
        this.tabKeys = stringArray2;
        this.tabNames = stringArray;
        this.tabLabels = new TextView[this.tabKeys.length];
        LinearLayout track = this.ui.segmentedTrack();
        for (int i = 0; i < this.tabKeys.length; ++i) {
            String key = this.tabKeys[i];
            TextView label = this.ui.tab(this.tabNames[i], this.tab.equals(key), () -> {
                this.tab = key;
                this.categoryFilter = "";
                this.sortColumn = "";
                this.metricsX = 0;
                this.playerLimit = 16;
                this.render(false);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) lp.setMarginStart(this.ui.dp(4));
            track.addView(label, lp);
            this.tabLabels[i] = label;
        }
        this.padTabs.addView(track, new LinearLayout.LayoutParams(-2, -2));
        this.updateTabs();
    }

    private void updatePadChrome() {
        if (this.padTitle != null) {
            this.padTitle.setText((CharSequence)displayText(MatchLabel.of(this.match)));
        }
        if (this.padSubtitle != null) {
            String when = this.match.timeConfirmed ? DetailActivity.time(this.match.start, "M/d HH:mm") : "Time TBD";
            this.padSubtitle.setText((CharSequence)(when + " · Beijing time"));
        }
        this.updateTabs();
        // The 404dp dossier is expensive to build. A live score tick usually changes only the
        // reading pane, so rebuild the left pane only when something it renders has changed.
        String leftKey = this.padLeftKey();
        if (!leftKey.equals(this.renderedPadLeft)) {
            this.renderedPadLeft = leftKey;
            this.rebuildPadLeft();
        }
    }

    private String padLeftKey() {
        StringBuilder key = new StringBuilder(256);
        key.append(this.match.json()).append('|').append(this.busy)
            .append('|').append(this.watches.signature(this.match));
        MatchDetail d = this.detail;
        key.append('|').append(d == null ? "none"
            : d.source + ":" + d.warning + ":" + d.bodySignature());
        return key.toString();
    }

    private void rebuildPadLeft() {
        if (this.padLeftHost == null) {
            return;
        }
        int y = this.padLeftScroll == null ? 0 : this.padLeftScroll.getScrollY();
        this.padLeftHost.removeAllViews();
        this.padLeftHost.addView((View)this.padScoreCard());
        if (this.detail != null) {
            Map<String, List<String>> facts = this.matchFacts();
            if (!facts.isEmpty()) {
                this.padLeftHeading("Match information");
                this.padLeftHost.addView(this.padFactsCard(facts));
            }
            this.padLeftHeading("Source");
            LinearLayout source = this.ui.column();
            source.addView((View)this.ui.text(displayText(this.detail.source), 12, this.ui.text, true));
            this.ui.gap(source, 4);
            source.addView((View)this.ui.text("Updated " + DetailActivity.time(this.detail.fetched, "M/d HH:mm:ss") + (this.match.updated > 0L ? "\nSource published " + DetailActivity.time(this.match.updated, "M/d HH:mm") : ""), 11, this.ui.muted, false));
            this.padLeftHost.addView((View)source);
            if (!this.match.url.isBlank()) {
                this.ui.gap(this.padLeftHost, 10);
                this.padLeftHost.addView((View)this.ui.textButton("Open source page", () -> this.openUrl()), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
            }
        } else {
            this.padLeftHeading("Match data");
            TextView loading = this.ui.text(this.busy ? "Loading match data…" : "Match data not loaded", 12, this.ui.muted, false);
            loading.setLineSpacing((float)this.ui.dp(3.0f), 1.0f);
            this.padLeftHost.addView((View)loading);
        }
        if (this.padLeftScroll != null) {
            this.padLeftScroll.post(() -> this.padLeftScroll.scrollTo(0, y));
        }
    }

    private LinearLayout padScoreCard() {
        int accent = this.ui.sportAccent(this.match.league, this.match.group);
        LinearLayout card = this.ui.column();
        card.setBackground(this.ui.heroPanel(accent));
        card.setPadding(this.ui.dp(18.0f), this.ui.dp(16.0f), this.ui.dp(18.0f), this.ui.dp(16.0f));
        LinearLayout top = this.ui.row();
        top.addView(this.ui.eyebrow(displayText(MatchLabel.of(this.match)), accent), new LinearLayout.LayoutParams(0, -2, 1f));
        if (this.match.live()) {
            top.addView(this.ui.liveBadge(displayText(this.match.status.isBlank() ? "LIVE" : this.match.status)));
        } else {
            top.addView(this.ui.badge(displayText(this.match.status.isBlank() ? Rules.stateLabel(this.match.state) : this.match.status), this.ui.tonal, this.ui.muted));
        }
        card.addView(top);
        this.ui.gap(card, 14);
        if (this.match.group.equals("F1")) {
            TextView title = this.ui.display(displayText(this.match.displayTitle()), 22, this.ui.text);
            card.addView((View)title);
        } else {
            card.addView((View)this.padTeamLine(true));
            this.ui.gap(card, 12);
            card.addView((View)this.padTeamLine(false));
        }
        LinearLayout quick = this.ui.row();
        LinearLayout remind = this.buildRemindPill();
        LinearLayout comp = this.buildCompetitionPill();
        if (remind.getVisibility() == View.VISIBLE) {
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (comp != null) rp.setMarginEnd(this.ui.dp(8));
            quick.addView(remind, rp);
        }
        if (comp != null) {
            quick.addView(comp, new LinearLayout.LayoutParams(remind.getVisibility() == View.VISIBLE ? 0 : -1, -2, remind.getVisibility() == View.VISIBLE ? 1f : 0f));
        }
        if (quick.getChildCount() > 0) {
            this.ui.gap(card, 14);
            card.addView(quick);
        }
        WatchLinks.Link watch = this.watches.link(this.match);
        if (watch != null) {
            this.ui.gap(card, 10);
            Ui.WatchRow row = this.ui.watchRow();
            row.bind(watch, this.match.live(), this::openWatch);
            card.addView((View)row);
        }
        return card;
    }

    private LinearLayout padTeamLine(boolean home) {
        LinearLayout row = this.ui.row();
        String name = home ? this.match.home : this.match.away;
        String logo = home ? this.match.homeLogo : this.match.awayLogo;
        String id = home ? this.match.homeId : this.match.awayId;
        FrameLayout crest = new FrameLayout((Context)this);
        crest.setBackground(this.ui.outlined(this.ui.tonal, this.ui.border, 14));
        crest.addView((View)Logos.view(this.ui, logo, name, 36), (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(this.ui.dp(36.0f), this.ui.dp(36.0f), Gravity.CENTER));
        boolean available = id != null && !id.isBlank();
        crest.setFocusable(available);
        crest.setClickable(available);
        crest.setContentDescription(available ? displayText("View " + name + " schedule") : null);
        crest.setOnClickListener(available ? v -> this.openTeam(home) : null);
        row.addView((View)crest, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(48.0f), this.ui.dp(48.0f)));
        TextView team = this.ui.text(name, 17, this.ui.text, true);
        team.setPadding(this.ui.dp(12.0f), 0, this.ui.dp(8.0f), 0);
        row.addView((View)team, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView score = this.ui.display(this.match.hasScore() ? this.match.displayScore(home) : "—", 32, this.ui.scoreColor(this.match, home));
        score.setMinWidth(this.ui.scoreColumnWidth(32, this.match));
        score.setGravity(Gravity.END);
        score.setIncludeFontPadding(false);
        row.addView((View)score);
        row.setContentDescription((CharSequence)displayText(name + (this.match.hasScore() ? ", score " + this.match.displayScore(home) : ", score unavailable")));
        return row;
    }

    private void padLeftHeading(String text) {
        this.ui.gap(this.padLeftHost, 20);
        this.padLeftHost.addView((View)this.ui.eyebrow(text, this.ui.muted));
        this.ui.gap(this.padLeftHost, 9);
    }

    private LinearLayout padStatsCard(List<MatchDetail.Stat> stats, int limit) {
        LinearLayout card = this.ui.column();
        card.setBackground((Drawable)this.ui.panel());
        card.setPadding(this.ui.dp(16.0f), this.ui.dp(14.0f), this.ui.dp(16.0f), this.ui.dp(14.0f));
        LinearLayout names = this.ui.row();
        TextView home = this.ui.text(this.match.home, 12, this.ui.primary, true);
        TextView away = this.ui.text(this.match.away, 12, this.ui.info, true);
        away.setGravity(Gravity.END);
        names.addView((View)home, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        names.addView((View)away, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        card.addView((View)names);
        this.ui.gap(card, 8);
        card.addView(this.ui.divider());
        int count = Math.min(limit, stats.size());
        for (int i = 0; i < count; ++i) {
            MatchDetail.Stat s = stats.get(i);
            card.addView(this.ui.statComparisonRow(this.statLabel(s.label()), s.home(), s.away()));
            if (i < count - 1) card.addView(this.ui.divider());
        }
        return card;
    }

    private LinearLayout padFactsCard(Map<String, List<String>> facts) {
        LinearLayout card = this.ui.column();
        card.setBackground((Drawable)this.ui.panel());
        card.setPadding(this.ui.dp(16.0f), this.ui.dp(10.0f), this.ui.dp(16.0f), this.ui.dp(10.0f));
        int index = 0;
        for (Map.Entry<String, List<String>> fact : facts.entrySet()) {
            if (index++ > 0) {
                card.addView(this.ui.divider());
            }
            LinearLayout row = this.ui.row();
            row.setGravity(48);
            row.setPadding(0, this.ui.dp(9.0f), 0, this.ui.dp(9.0f));
            row.addView((View)this.ui.text(fact.getKey(), 10, this.ui.muted, false), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
            TextView value = this.ui.text(String.join("\n", new LinkedHashSet<>(fact.getValue())), 12, this.ui.text, false);
            value.setLineSpacing((float)this.ui.dp(2.0f), 1.0f);
            row.addView((View)value, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.35f));
            card.addView((View)row);
        }
        return card;
    }

    private void rebuildPadBody(boolean preserve) {
        if (this.scroll == null) {
            return;
        }
        int y = preserve ? this.scroll.getScrollY() : 0;
        this.body = this.ui.column();
        this.scroll.removeAllViews();
        this.scroll.addView((View)this.body);
        this.status = null;
        if (!this.error.isEmpty()) {
            LinearLayout warning = this.panel();
            warning.addView((View)this.ui.text("Update failed · " + this.error + (this.detail == null ? "" : "\nShowing the last saved data"), 12, this.ui.error, false));
            this.ui.gap(warning, 10);
            warning.addView((View)this.ui.button("Reload", false, () -> this.load(true)));
            this.body.addView((View)warning);
            this.ui.gap(this.body, 14);
        }
        if (!(this.detail == null || this.detail.warning.isBlank() || this.match.group.equals("CS2") && !this.tab.equals("overview"))) {
            TextView note = this.ui.text(displayText(this.detail.warning), 12, this.ui.muted, false);
            note.setLineSpacing((float)this.ui.dp(3.0f), 1.0f);
            this.body.addView((View)note);
            this.ui.gap(this.body, 16);
        }
        if (this.detail == null) {
            this.empty(this.busy ? "Loading match data" : "Match details are unavailable", this.busy ? "Connecting to this match's source." : "Check your connection and reload. The match list keeps its last record.");
        } else if (this.tab.equals("overview")) {
            this.padOverview();
        } else if (this.tab.equals("stats")) {
            this.padStatistics();
        } else if (this.tab.equals("events")) {
            this.padEvents();
        } else if (this.match.group.equals("足球")) {
            new FootballLineupView(this.ui, this.detail, this.body).show(this.teamFilter, id -> {
                this.teamFilter = id;
                this.render(true);
            });
        } else if (this.match.group.equals("F1")) {
            this.f1Drivers();
        } else {
            this.roster("lineup");
        }
        this.scroll.post(() -> this.scroll.scrollTo(0, y));
        if (this.pendingScrollY >= 0 && this.detail != null) {
            int restored = this.pendingScrollY;
            this.pendingScrollY = -1;
            this.scroll.post(() -> this.scroll.scrollTo(0, restored));
        }
    }

    private void padOverview() {
        LinearLayout left = this.ui.column();
        LinearLayout right = this.ui.column();
        if (!this.detail.stats.isEmpty()) {
            this.headingInto(left, "Key stats");
            left.addView((View)this.padStatsCard(this.detail.stats, 8));
            this.ui.gap(left, 24);
        }
        if (this.tablesInto(right, "overview")) {
            this.ui.gap(right, 22);
        }
        if (!this.detail.events.isEmpty()) {
            this.headingInto(right, "Latest events");
            LinearLayout card = this.panel();
            for (int i = 0; i < Math.min(8, this.detail.events.size()); ++i) {
                this.event(card, this.detail.events.get(i));
            }
            right.addView((View)card);
            this.ui.gap(right, 24);
        }
        this.body.addView(this.ui.columns(left, right, 360, 24), new LinearLayout.LayoutParams(-1, -2));
    }

    private void padStatistics() {
        if (this.match.group.equals("F1")) {
            this.roster("stats");
            return;
        }
        boolean hasStats = !this.detail.stats.isEmpty();
        boolean hasTables = this.detail.tables.stream().anyMatch(t -> t.tab.equals("stats"));
        if (!hasStats && !hasTables) {
            this.empty("Match statistics are not available", this.missing());
            return;
        }
        if (hasStats && hasTables) {
            LinearLayout left = this.ui.column(), right = this.ui.column();
            this.headingInto(left, "Team comparison");
            left.addView(this.padStatsCard(this.detail.stats, this.detail.stats.size()));
            this.headingInto(right, "Players and splits");
            this.tablesInto(right, "stats");
            this.body.addView(this.ui.columns(left, right, 360, 24), new LinearLayout.LayoutParams(-1, -2));
        } else if (hasStats) {
            this.heading("Team comparison");
            this.body.addView((View)this.padStatsCard(this.detail.stats, this.detail.stats.size()));
        } else {
            this.tablesInto(this.body, "stats");
        }
    }

    private void padEvents() {
        if (this.match.group.equals("CS2")) {
            this.cs2Maps();
            return;
        }
        boolean found = this.tablesInto(this.body, "events");
        if (found) {
            this.ui.gap(this.body, 22);
        }
        if (!this.detail.events.isEmpty()) {
            this.heading(this.match.group.equals("足球") ? "Key events" : (this.match.group.equals("NFL") ? "Scoring plays" : "Play-by-play"));
            LinearLayout card = this.panel();
            for (int i = 0; i < Math.min(this.eventsLimit, this.detail.events.size()); ++i) this.event(card, this.detail.events.get(i));
            this.body.addView(card);
            if (this.detail.events.size() > this.eventsLimit) {
                this.ui.gap(this.body, 12);
                this.body.addView((View)this.ui.button("Load more events · " + (this.detail.events.size() - this.eventsLimit) + " remaining", false, () -> {
                    this.eventsLimit += 50;
                    this.render(true);
                }));
            }
        } else if (!found) {
            this.empty("No events are available", this.missing());
        }
    }

    private void openUrl() {
        if (this.match.url.isBlank()) {
            Sheet.message(this, "Source page", "The source did not provide a page to open.");
            return;
        }
        try {
            this.startActivity(new Intent("android.intent.action.VIEW", Uri.parse((String)this.match.url)));
        }
        catch (ActivityNotFoundException e) {
            Sheet.message(this, "Unable to open source", "No browser is available on this device.");
        }
    }

    private void openWatch() {
        this.watchLauncher.open(this.match);
    }

    private void resolveWatch() {
        if (this.watches == null || this.match == null) {
            return;
        }
        // A result that arrived while paused was intentionally ignored; rebind the cached
        // destination before asking for another upgrade so the header cannot stay stale.
        if (!this.pad && this.headerWatch != null) {
            WatchLinks.Link link = this.watches.link(this.match);
            this.headerWatch.bind(link, this.match.live(), this::openWatch);
            if (this.headerWatchGap != null) this.headerWatchGap.setVisibility(link != null ? View.VISIBLE : View.GONE);
        }
        this.watches.resolve(this.match, () -> {
            if (!this.active || this.isFinishing() || this.isDestroyed()) {
                return;
            }
            if (this.pad) {
                this.render(true);
            } else if (this.headerWatch != null) {
                WatchLinks.Link link = this.watches.link(this.match);
                this.headerWatch.bind(link, this.match.live(), this::openWatch);
                if (this.headerWatchGap != null) this.headerWatchGap.setVisibility(link != null ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void headingInto(LinearLayout into, String title) {
        into.addView((View)this.ui.text(displayText(title), 15, this.ui.text, true));
        this.ui.gap(into, 10);
    }

    private boolean tablesInto(LinearLayout into, String key) {
        return this.tablesInto(into, key, 0);
    }

    private boolean tablesInto(LinearLayout into, String key, int widthDp) {
        boolean found = false;
        for (MatchDetail.Table t : this.detail.tables) {
            if (!t.tab.equals(key)) continue;
            this.headingInto(into, t.title);
            int width = widthDp;
            String scrollKey = key + "|" + t.title;
            int scrollX = this.tableScroll.getOrDefault(scrollKey, 0);
            into.addView(new DetailTableView(this.ui, false, true, false, width)
                .simple(t, x -> {
                    this.tableScroll.put(scrollKey, x);
                    this.lastScroll = SystemClock.uptimeMillis();
                }, scrollX), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
            this.ui.gap(into, 18);
            found = true;
        }
        return found;
    }

    private View padRule() {
        View line = new View((Context)this);
        line.setBackgroundColor(this.ui.line);
        line.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(1.0f), -1));
        return line;
    }

    @SuppressLint(value={"ClickableViewAccessibility"})
    private void buildChrome() {
        this.root.removeAllViews();
        LinearLayout appbar = this.ui.row();
        appbar.setPadding(this.ui.dp(4.0f), this.ui.dp(2.0f), this.ui.dp(4.0f), 0);
        appbar.addView((View)this.iconButton("back", "Back", this::finish));
        TextView title = this.ui.text(displayText(MatchLabel.of(this.match)), 16, this.ui.text, true);
        title.setGravity(17);
        appbar.addView((View)title, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        appbar.addView((View)this.iconButton("refresh", "Refresh match details", () -> this.load(true)));
        this.root.addView((View)appbar);
        this.scroll = new ScrollView((Context)this);
        this.scroll.setFillViewport(true);
        this.scroll.setVerticalScrollBarEnabled(false);
        this.scroll.setOnScrollChangeListener((v, x, sy, ox, oy) -> {
            this.lastScroll = SystemClock.uptimeMillis();
        });
        this.scroll.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == 0) {
                this.fingerDown = true;
            } else if (action == 1 || action == 3) {
                this.fingerDown = false;
                this.lastScroll = SystemClock.uptimeMillis();
            }
            return false;
        });
        this.content = this.ui.column();
        this.scroll.addView((View)this.content);
        this.root.addView((View)this.scroll, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, 0, 1.0f));
        this.buildScoreHeader();
        this.buildTabs();
        this.body = this.ui.column();
        this.body.setPadding(this.ui.dp(16.0f), this.ui.dp(8.0f), this.ui.dp(16.0f), this.ui.dp(24.0f));
        this.content.addView((View)this.body);
        this.status = null;
        this.chromeBuilt = true;
    }

    private void buildScoreHeader() {
        int accent = this.ui.sportAccent(this.match.league, this.match.group);
        LinearLayout card = this.ui.column();
        card.setBackground(this.ui.heroPanel(accent));
        card.setPadding(this.ui.dp(18.0f), this.ui.dp(16.0f), this.ui.dp(18.0f), this.ui.dp(16.0f));

        LinearLayout heroTop = this.ui.row();
        TextView compBadge = this.ui.eyebrow(displayText(MatchLabel.of(this.match)), accent);
        heroTop.addView(compBadge, new LinearLayout.LayoutParams(0, -2, 1f));
        this.headerLivePill = this.ui.liveBadge(displayText(this.match.status.isBlank() ? "LIVE" : this.match.status));
        this.headerLiveText = (TextView) this.headerLivePill.getChildAt(1);
        this.headerLivePill.setVisibility(this.match.live() ? View.VISIBLE : View.GONE);
        heroTop.addView(this.headerLivePill);
        this.headerState = this.ui.badge(displayText(this.match.status.isBlank() ? Rules.stateLabel(this.match.state) : this.match.status), this.ui.tonal, this.ui.muted);
        this.headerState.setVisibility(this.match.live() ? View.GONE : View.VISIBLE);
        heroTop.addView(this.headerState);
        card.addView(heroTop);
        this.ui.gap(card, 14);

        if (this.match.group.equals("F1")) {
            this.headerTitle = this.ui.display(displayText(this.match.displayTitle()), 20, this.ui.text);
            this.headerTitle.setGravity(Gravity.CENTER);
            card.addView((View)this.headerTitle);
            this.ui.gap(card, 6);
            this.headerStart = this.ui.text(this.match.timeConfirmed ? DetailActivity.time(this.match.start, "MMM d · HH:mm") : "Time TBD", 12, this.ui.muted, false);
            this.headerStart.setGravity(Gravity.CENTER);
            card.addView((View)this.headerStart);
        } else if (this.getResources().getConfiguration().fontScale >= 1.5f || this.getResources().getConfiguration().fontScale >= 1.25f && this.ui.contentWidthDp() < 400) {
            this.headerHomeScore = this.stackedTeam(card, true);
            this.ui.gap(card, 14);
            this.headerAwayScore = this.stackedTeam(card, false);
            this.ui.gap(card, 10);
            this.headerStart = this.ui.text(this.match.timeConfirmed ? DetailActivity.time(this.match.start, "MMM d · HH:mm") : "Time TBD", 12, this.ui.muted, false);
            card.addView((View)this.headerStart);
        } else {
            LinearLayout row = this.ui.row();
            row.setGravity(Gravity.CENTER);
            LinearLayout homeSide = this.team(true);
            row.addView((View)homeSide, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.05f));
            LinearLayout center = this.ui.column();
            center.setGravity(Gravity.CENTER);
            this.headerScore = this.ui.score(this.match.displayScore(true), this.match.displayScore(false), this.match.hasScore(), 36);
            this.headerScore.setResultColors(this.ui.scoreColor(this.match, true), this.ui.scoreColor(this.match, false));
            center.addView((View)this.headerScore, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
            this.ui.gap(center, 6);
            this.headerStart = this.ui.text(this.match.timeConfirmed ? DetailActivity.time(this.match.start, "MMM d · HH:mm") : "Time TBD", 11, this.ui.muted, false);
            this.headerStart.setGravity(Gravity.CENTER);
            center.addView((View)this.headerStart, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
            row.addView((View)center, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.25f));
            LinearLayout awaySide = this.team(false);
            row.addView((View)awaySide, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.05f));
            card.addView((View)row);
            this.headerHome = (TextView)homeSide.getChildAt(2);
            this.headerAway = (TextView)awaySide.getChildAt(2);
        }

        // Integrated Quick Actions row: Remind + Standings/Bracket directly in Match Center hero
        LinearLayout quick = this.ui.row();
        this.headerRemindBtn = this.buildRemindPill();
        this.headerRemindIcon = (Ui.Icon) this.headerRemindBtn.getChildAt(0);
        this.headerRemindLabel = (TextView) this.headerRemindBtn.getChildAt(1);
        LinearLayout compPill = this.buildCompetitionPill();
        boolean hasRemind = this.headerRemindBtn.getVisibility() == View.VISIBLE;
        if (hasRemind) {
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (compPill != null) rp.setMarginEnd(this.ui.dp(8));
            quick.addView(this.headerRemindBtn, rp);
        } else {
            quick.addView(this.headerRemindBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        if (compPill != null) {
            quick.addView(compPill, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        if (hasRemind || compPill != null) {
            this.ui.gap(card, 14);
            card.addView(quick);
        }

        WatchLinks.Link watch = this.watches.link(this.match);
        this.headerWatchGap = new View((Context)this);
        this.headerWatchGap.setVisibility(watch != null ? View.VISIBLE : View.GONE);
        card.addView(this.headerWatchGap, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(1, this.ui.dp(10.0f)));
        this.headerWatch = this.ui.watchRow();
        this.headerWatch.bind(watch, this.match.live(), this::openWatch);
        card.addView((View)this.headerWatch);

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(this.ui.dp(16.0f), this.ui.dp(8.0f), this.ui.dp(16.0f), this.ui.dp(6.0f));
        this.content.addView((View)card, (ViewGroup.LayoutParams)cp);
    }

    private TextView stackedTeam(LinearLayout card, boolean home) {
        LinearLayout side = this.team(home);
        TextView name = (TextView)side.getChildAt(2);
        if (home) {
            this.headerHome = name;
        } else {
            this.headerAway = name;
        }
        View crest = side.getChildAt(0);
        side.removeView(crest);
        LinearLayout line = this.ui.row();
        line.addView(crest, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(56.0f), this.ui.dp(56.0f)));
        TextView score = this.ui.display(this.match.hasScore() ? this.match.displayScore(home) : "—", 28, this.ui.scoreColor(this.match, home));
        score.setGravity(Gravity.END);
        line.addView((View)score, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        card.addView((View)line);
        name.setGravity(Gravity.START);
        side.setGravity(Gravity.START);
        card.addView((View)side, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        return score;
    }

    private void updateChrome() {
        String stateText = displayText(this.match.status.isBlank() ? Rules.stateLabel(this.match.state) : this.match.status);
        if (this.headerLivePill != null) {
            this.headerLivePill.setVisibility(this.match.live() ? View.VISIBLE : View.GONE);
            if (this.headerLiveText != null) this.headerLiveText.setText(stateText.isBlank() ? "LIVE" : stateText);
        }
        if (this.headerState != null) {
            this.headerState.setVisibility(this.match.live() ? View.GONE : View.VISIBLE);
            this.headerState.setText(stateText);
        }
        if (this.match.group.equals("F1")) {
            if (this.headerTitle != null) {
                this.headerTitle.setText((CharSequence)displayText(this.match.displayTitle()));
            }
            if (this.headerStart != null) {
                this.headerStart.setText(this.match.timeConfirmed ? DetailActivity.time(this.match.start, "MMM d · HH:mm") : "Time TBD");
            }
        } else if (this.headerHome != null) {
            this.headerHome.setText((CharSequence)this.match.home);
            this.headerAway.setText((CharSequence)this.match.away);
            if (this.headerScore != null) {
                this.headerScore.setScore(this.match.displayScore(true), this.match.displayScore(false), this.match.hasScore());
                this.headerScore.setResultColors(this.ui.scoreColor(this.match, true), this.ui.scoreColor(this.match, false));
            } else if (this.headerHomeScore != null && this.headerAwayScore != null) {
                this.headerHomeScore.setText((CharSequence)this.match.displayScore(true));
                this.headerAwayScore.setText((CharSequence)this.match.displayScore(false));
                this.headerHomeScore.setTextColor(this.ui.scoreColor(this.match, true));
                this.headerAwayScore.setTextColor(this.ui.scoreColor(this.match, false));
            }
            if (this.headerStart != null) {
                this.headerStart.setText((CharSequence)(this.match.timeConfirmed ? DetailActivity.time(this.match.start, "MMM d · HH:mm") : "Time TBD"));
            }
            this.bindHeaderTeam(true);
            this.bindHeaderTeam(false);
        }
        this.updateRemindControl();
        this.updateTabs();
        this.updateStatus();
    }

    private void bindHeaderTeam(boolean home) {
        Logos.Crest crest = home ? this.headerHomeCrest : this.headerAwayCrest;
        FrameLayout slot = home ? this.headerHomeSlot : this.headerAwaySlot;
        if (crest == null || slot == null) return;
        String name = home ? this.match.home : this.match.away;
        String logo = home ? this.match.homeLogo : this.match.awayLogo;
        String id = home ? this.match.homeId : this.match.awayId;
        Logos.bind(crest, logo, name);
        boolean available = id != null && !id.isBlank();
        slot.setClickable(available);
        slot.setFocusable(available);
        slot.setContentDescription(available ? displayText("View " + name + " schedule") : null);
        slot.setOnClickListener(available ? v -> this.openTeam(home) : null);
    }

    private void updateStatus() {
        if (this.status != null) {
            this.status.setText((CharSequence)this.statusText());
        }
    }

    private String bodyKey() {
        StringBuilder key = new StringBuilder(96).append(this.tab).append(':').append(this.teamFilter).append(':').append(this.categoryFilter).append(':').append(this.mapFilter).append(':').append(this.sortColumn).append(':').append(this.descending).append(':').append(this.eventsLimit).append(':').append(this.playerLimit).append(':').append(this.error).append(':').append(this.match.group);
        key.append(':').append(this.match.eventId);
        if (this.detail == null) {
            key.append(":empty:").append(this.busy);
        } else {
            key.append(':').append(this.detail.bodySignature());
        }
        return key.toString();
    }

    private void rebuildBody(boolean preserve) {
        int y = preserve && this.scroll != null ? this.scroll.getScrollY() : 0;
        LinearLayout previous = this.body;
        this.body = this.ui.column();
        this.body.setPadding(this.ui.dp(16.0f), this.ui.dp(8.0f), this.ui.dp(16.0f), this.ui.dp(24.0f));
        if (this.content != null) {
            if (previous != null && previous.getParent() == this.content) this.content.removeView(previous);
            this.content.addView((View)this.body);
        }
        this.status = null;
        if (!this.error.isEmpty()) {
            LinearLayout warning = this.panel();
            warning.addView((View)this.ui.text("Update failed · " + this.error + (this.detail == null ? "" : "\nShowing the last saved data"), 13, this.ui.error, false));
            this.ui.gap(warning, 12);
            warning.addView((View)this.ui.button("Reload", false, () -> this.load(true)));
            this.body.addView((View)warning);
            this.ui.gap(this.body, 14);
        }
        if (this.detail == null) {
            this.empty(this.busy ? "Loading match data" : "Match details are unavailable", this.busy ? "Connecting to this match's source." : "Check your connection and reload. The match list keeps its last record.");
        } else if (this.tab.equals("overview")) {
            this.overview();
        } else if (this.tab.equals("stats")) {
            this.statistics();
        } else if (this.tab.equals("events")) {
            this.events();
        } else if (this.match.group.equals("足球")) {
            new FootballLineupView(this.ui, this.detail, this.body).show(this.teamFilter, id -> {
                this.teamFilter = id;
                this.render(true);
            });
        } else if (this.match.group.equals("F1")) {
            this.f1Drivers();
        } else {
            this.roster("lineup");
        }
        if (!(this.detail == null || this.detail.warning.isBlank() || this.match.group.equals("CS2") && !this.tab.equals("overview"))) {
            this.ui.gap(this.body, 16);
            this.body.addView((View)this.ui.text(displayText(this.detail.warning), 12, this.ui.muted, false));
        }
        this.status = this.ui.text(this.statusText(), 11, this.ui.muted, false);
        this.ui.gap(this.body, 16);
        this.body.addView((View)this.status);
        if (this.scroll != null) {
            this.scroll.post(() -> this.scroll.scrollTo(0, preserve ? y : 0));
            if (this.pendingScrollY >= 0 && this.detail != null) {
                int restored = this.pendingScrollY;
                this.pendingScrollY = -1;
                this.scroll.post(() -> this.scroll.scrollTo(0, restored));
            }
        }
    }

    private String statusText() {
        if (this.detail == null) {
            return this.busy ? "Loading details…" : "Details not loaded";
        }
        return (System.currentTimeMillis() - this.detail.fetched > 120000L ? "Cached · " : "") + displayText(this.detail.source) + " · " + DetailActivity.time(this.detail.fetched, "HH:mm:ss");
    }

    private LinearLayout team(boolean home) {
        String name = home ? this.match.home : this.match.away;
        String logo = home ? this.match.homeLogo : this.match.awayLogo;
        String id = home ? this.match.homeId : this.match.awayId;
        LinearLayout side = this.ui.column();
        side.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout slot = new FrameLayout((Context)this);
        Logos.Crest crest = Logos.crest(this.ui, 40, false);
        Logos.bind(crest, logo, name);
        if (home) {
            this.headerHomeCrest = crest;
            this.headerHomeSlot = slot;
        } else {
            this.headerAwayCrest = crest;
            this.headerAwaySlot = slot;
        }
        slot.addView((View)crest.box, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(this.ui.dp(40.0f), this.ui.dp(40.0f), Gravity.CENTER));
        this.ui.clickableDrawable(slot, this.ui.outlined(this.ui.tonal, this.ui.border, 16), 16);
        boolean available = id != null && !id.isBlank();
        slot.setClickable(available);
        slot.setFocusable(available);
        slot.setContentDescription(available ? displayText("View " + name + " schedule") : null);
        slot.setOnClickListener(available ? v -> this.openTeam(home) : null);
        side.addView((View)slot, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(56.0f), this.ui.dp(56.0f)));
        this.ui.gap(side, 8);
        TextView text = this.ui.text(name, 13, this.ui.text, true);
        text.setGravity(Gravity.CENTER);
        text.setMaxLines(2);
        side.addView((View)text, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        return side;
    }

    private void openTeam(boolean home) {
        Team team = Team.of(this.match, home);
        if (team == null) {
            Sheet.message(this, "Team schedule unavailable", "The source did not provide a complete identity for this team.");
            return;
        }
        this.startActivity(new Intent((Context)this, TeamActivity.class).putExtra("team", team.json().toString()));
    }

    private String lineupName() {
        return switch (this.match.group) {
            case "足球" -> "Lineup";
            case "F1" -> "Drivers";
            case "CS2" -> "Players";
            default -> "Players";
        };
    }

    private void buildTabs() {
        boolean f1 = this.match.group.equals("F1");
        boolean cs = this.match.group.equals("CS2");
        if (cs && this.tab.equals("stats")) {
            this.tab = "lineup";
        }
        if (cs) {
            this.tabKeys = new String[]{"overview", "lineup", "events"};
            this.tabNames = new String[]{"Overview", "Players", "Maps"};
        } else if (f1) {
            this.tabKeys = new String[]{"overview", "lineup", "stats"};
            this.tabNames = new String[]{"Overview", "Drivers", "Results"};
        } else {
            this.tabKeys = new String[]{"overview", "lineup", "stats", "events"};
            this.tabNames = new String[]{"Overview", this.lineupName(), "Stats", "Events"};
        }
        this.tabLabels = new TextView[this.tabKeys.length];
        LinearLayout track = this.ui.segmentedTrack();
        for (int i = 0; i < this.tabKeys.length; ++i) {
            String key = this.tabKeys[i];
            TextView label = this.ui.tab(this.tabNames[i], this.tab.equals(key), () -> {
                this.tab = key;
                this.categoryFilter = "";
                this.sortColumn = "";
                this.metricsX = 0;
                this.playerLimit = 16;
                this.render(false);
            });
            label.setPadding(this.ui.dp(6), this.ui.dp(8), this.ui.dp(6), this.ui.dp(8));
            label.setMinWidth(0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) lp.setMarginStart(this.ui.dp(4));
            track.addView(label, lp);
            this.tabLabels[i] = label;
        }
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.setMargins(this.ui.dp(16), this.ui.dp(6), this.ui.dp(16), this.ui.dp(6));
        this.content.addView(track, tp);
        this.updateTabs();
    }

    private void updateTabs() {
        if (this.tabLabels == null) {
            return;
        }
        for (int i = 0; i < this.tabLabels.length; ++i) {
            boolean on = this.tabKeys[i].equals(this.tab);
            this.ui.selectTab(this.tabLabels[i], on);
            this.tabLabels[i].setContentDescription((CharSequence)(this.tabNames[i] + (on ? ", selected" : "")));
        }
    }

    private LinearLayout panel() {
        LinearLayout card = this.ui.column();
        card.setBackground((Drawable)this.ui.panel());
        card.setPadding(this.ui.dp(16.0f), this.ui.dp(14.0f), this.ui.dp(16.0f), this.ui.dp(14.0f));
        return card;
    }

    private void heading(String text) {
        this.ui.gap(this.body, 16);
        this.body.addView((View)this.ui.eyebrow(displayText(text), this.ui.muted));
        this.ui.gap(this.body, 8);
    }

    private void overview() {
        int i;
        if (!this.match.group.equals("CS2")) {
            this.tables("overview");
        }
        if (!this.detail.stats.isEmpty()) {
            this.heading("Key stats");
            LinearLayout card = this.panel();
            this.teamNames(card);
            int statLimit = Math.min(4, this.detail.stats.size());
            for (i = 0; i < statLimit; ++i) {
                this.stat(card, this.detail.stats.get(i), i < statLimit - 1);
            }
            this.body.addView((View)card);
        }
        if (!this.detail.events.isEmpty()) {
            this.heading("Latest events");
            LinearLayout timeline = this.panel();
            for (i = 0; i < Math.min(5, this.detail.events.size()); ++i) {
                this.event(timeline, this.detail.events.get(i));
            }
            this.body.addView((View)timeline);
        }
        this.information();
        this.sourceLink();
    }


    private Map<String, List<String>> matchFacts() {
        Map<String, List<String>> facts = new LinkedHashMap<>();
        String competition = displayText(MatchLabel.of(this.match));
        for (MatchDetail.Fact f : this.detail.facts) {
            String label = DetailParsers.label(DetailParsers.plain(f.label()));
            if (label.equals("阵容资料版本")) continue;
            label = displayText(label);
            String value = displayText(DetailParsers.plain(f.value()));
            if (value.isBlank()) continue;
            if ("Competition".equals(label) && value.equals(competition)) continue;
            facts.computeIfAbsent(label.isBlank() ? "Information" : label, k -> new ArrayList()).add(value);
        }
        if (!this.match.group.equals("F1") && !this.match.group.equals("CS2") && !facts.containsKey("Venue")) {
            String venue = displayText(DetailParsers.plain(this.match.detail));
            if (!venue.isBlank()) facts.put("Venue", List.of(venue));
        }
        return facts;
    }

    private void information() {
        Map<String, List<String>> facts = this.matchFacts();
        if (facts.isEmpty()) return;
        LinearLayout card = this.panel();
        this.heading("Match information");
        int index = 0;
        for (Map.Entry<String, List<String>> fact : facts.entrySet()) {
            if (index++ > 0) {
                card.addView(this.ui.divider());
            }
            LinearLayout row = this.ui.row();
            row.setGravity(48);
            row.setPadding(0, this.ui.dp(10.0f), 0, this.ui.dp(10.0f));
            TextView label = this.ui.text(fact.getKey(), 12, this.ui.muted, false);
            TextView value = this.ui.text(String.join("\n", new LinkedHashSet<>(fact.getValue())), 13, this.ui.text, false);
            value.setLineSpacing((float)this.ui.dp(4.0f), 1.0f);
            row.addView((View)label, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
            row.addView((View)value, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 2.0f));
            card.addView((View)row);
        }
        this.body.addView((View)card);
    }

    private void sourceLink() {
        this.ui.gap(this.body, 16);
        this.body.addView((View)this.ui.textButton("Source and update time", () -> Sheet.message(this, "Source", displayText(this.detail.source) + "\nUpdated " + DetailActivity.time(this.detail.fetched, "M/d HH:mm:ss") + "\n\nData follows the source publication; fields it does not provide remain unknown.")));
        if (!this.match.url.isBlank()) {
            this.ui.gap(this.body, 8);
            this.body.addView((View)this.ui.textButton("Open source page", () -> {
                try {
                    this.startActivity(new Intent("android.intent.action.VIEW", Uri.parse((String)this.match.url)));
                }
                catch (ActivityNotFoundException e) {
                    Sheet.message(this, "Unable to open source", "No browser is available on this device.");
                }
            }));
        }
    }

    private void statistics() {
        if (this.match.group.equals("F1")) {
            this.roster("stats");
            return;
        }
        if (!this.detail.stats.isEmpty()) {
            this.heading("Team comparison");
            LinearLayout card = this.panel();
            this.teamNames(card);
            int statCount = this.detail.stats.size();
            for (int i = 0; i < statCount; ++i) {
                this.stat(card, this.detail.stats.get(i), i < statCount - 1);
            }
            this.body.addView((View)card);
        }
        if (!this.tables("stats") && this.detail.stats.isEmpty()) {
            if (this.match.group.equals("CS2")) {
                this.empty("Player data is grouped by overall and map", "Use the Players tab to compare K/D/A, ADR, KAST and source Rating.");
                this.body.addView((View)this.ui.button("View player data", false, () -> {
                    this.tab = "lineup";
                    this.render(false);
                }));
            } else {
                this.empty("Match statistics are not available", this.missing());
            }
        }
    }

    private void f1Drivers() {
        TreeMap<String, List<List<String>>> teams = new TreeMap<>();
        for (MatchDetail.Table table : this.detail.tables) {
            if (!table.tab.equals("lineup")) continue;
            for (List<String> row2 : table.rows) {
                int team = table.columns.indexOf("车队");
                int number = table.columns.indexOf("车号");
                teams.computeIfAbsent(DetailRows.cell(row2, team), key -> new ArrayList()).add(List.of(DetailRows.cell(row2, 0), DetailRows.cell(row2, number)));
            }
        }
        if (teams.isEmpty()) {
            this.empty("Drivers are not available", this.missing());
            return;
        }
        this.body.addView((View)this.ui.text("Teams and drivers", 15, this.ui.text, true));
        this.ui.gap(this.body, 4);
        this.body.addView((View)this.ui.text("Car numbers and this session's entry list", 11, this.ui.muted, false));
        this.ui.gap(this.body, 10);
        LinearLayout left = this.ui.column();
        LinearLayout right = this.ui.column();
        int total = teams.size();
        int split = (total + 1) / 2;
        int teamIndex = 0;
        for (Map.Entry<String, List<List<String>>> entry : teams.entrySet()) {
            LinearLayout column = teamIndex < split ? left : right;
            if (column.getChildCount() > 0) {
                this.ui.gap(column, 10);
            }
            LinearLayout card = this.ui.column();
            card.setBackground((Drawable)this.ui.panel());
            card.setPadding(this.ui.dp(14.0f), this.ui.dp(11.0f), this.ui.dp(14.0f), this.ui.dp(8.0f));
            card.addView((View)this.ui.text(entry.getKey(), 13, this.ui.text, true));
            this.ui.gap(card, 6);
            card.addView(this.ui.divider());
            List<List<String>> drivers = entry.getValue();
            drivers.sort(Comparator.comparing(row -> row.get(0)));
            for (int d = 0; d < drivers.size(); ++d) {
                if (d > 0) {
                    card.addView(this.ui.divider());
                }
                List<String> driver = drivers.get(d);
                LinearLayout row3 = this.ui.row();
                row3.setPadding(0, this.ui.dp(8.0f), 0, this.ui.dp(8.0f));
                TextView number = this.ui.text(driver.get(1), 13, this.ui.text, true);
                number.setGravity(Gravity.CENTER);
                number.setBackground((Drawable)this.ui.shape(this.ui.tonal, 8));
                number.setPadding(0, this.ui.dp(4.0f), 0, this.ui.dp(4.0f));
                number.setContentDescription((CharSequence)("Car number " + driver.get(1)));
                row3.addView((View)number, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(36.0f), -2));
                TextView driverName = this.ui.text(driver.get(0), 14, this.ui.text, true);
                LinearLayout.LayoutParams dnp = new LinearLayout.LayoutParams(0, -2, 1.0f);
                dnp.setMarginStart(this.ui.dp(10.0f));
                row3.addView((View)driverName, (ViewGroup.LayoutParams)dnp);
                card.addView((View)row3);
            }
            column.addView((View)card);
            teamIndex++;
        }
        this.body.addView((View)this.ui.columns(left, right, 260, 12), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
    }

    private void teamNames(LinearLayout card) {
        LinearLayout names = this.ui.row();
        TextView home = this.ui.text(this.match.home, 12, this.ui.primary, true);
        TextView away = this.ui.text(this.match.away, 12, this.ui.info, true);
        away.setGravity(Gravity.END);
        names.addView((View)home, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        names.addView((View)away, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        card.addView((View)names);
        this.ui.gap(card, 10);
        card.addView(this.ui.divider());
    }

    private void stat(LinearLayout card, MatchDetail.Stat stat, boolean hasNext) {
        card.addView(this.ui.statComparisonRow(this.statLabel(stat.label()), stat.home(), stat.away()));
        if (hasNext) {
            card.addView(this.ui.divider());
        }
    }

    private String statLabel(String label) {
        if (this.match.group.equals("足球")) {
            return switch (label) {
                case "进球" -> "Goals";
                case "助攻" -> "Assists";
                case "抢断" -> "Tackles";
                case "传球" -> "Passes";
                default -> displayText(label);
            };
        }
        return displayText(label);
    }

    private static String displayText(String value) {
        if (value == null) return "";
        return EnglishDisplay.text(value
            .replace("英超官方 FPL（照片按姓名与出生日期匹配）", "Official Premier League FPL (photos matched by name and date of birth)")
            .replace("英超官方 FPL (照片按姓名与出生日期匹配)", "Official Premier League FPL (photos matched by name and date of birth)"));
    }

    private boolean tables(String key) {
        boolean found = false;
        for (MatchDetail.Table t : this.detail.tables) {
            if (!t.tab.equals(key)) continue;
            this.heading(t.title);
            String scrollKey = key + "|" + t.title;
            int scrollX = this.tableScroll.getOrDefault(scrollKey, 0);
            this.body.addView(new DetailTableView(this.ui)
                .simple(t, x -> {
                    this.tableScroll.put(scrollKey, x);
                    this.lastScroll = SystemClock.uptimeMillis();
                }, scrollX), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
            found = true;
        }
        return found;
    }

    private void roster(String key) {
        if (this.match.group.equals("NBA") || this.match.group.equals("CS2")) {
            new TeamPanelsView(this.ui, this.detail, this.body).show(this.sortColumn, this.descending, sort -> {
                this.sortColumn = sort.column();
                this.descending = sort.descending();
                this.render(true);
            }, this.metricsX, value -> {
                this.metricsX = value;
                this.lastScroll = SystemClock.uptimeMillis();
            });
            return;
        }
        new PlayerStatsView(this.ui, this.detail, this.body).show(key, new PlayerStatsView.Selection(this.teamFilter, this.categoryFilter, this.sortColumn, this.descending), selection -> {
            if (!this.categoryFilter.isBlank() && !selection.category().equals(this.categoryFilter)) {
                this.metricsX = 0;
            }
            if (!selection.team().equals(this.teamFilter) || !selection.category().equals(this.categoryFilter)) {
                this.playerLimit = 16;
            }
            this.teamFilter = selection.team();
            this.categoryFilter = selection.category();
            this.sortColumn = selection.sort();
            this.descending = selection.descending();
            this.render(false);
        }, this.metricsX, value -> {
            this.metricsX = value;
            this.lastScroll = SystemClock.uptimeMillis();
        }, this.playerLimit, () -> {
            this.playerLimit = Math.min(400, this.playerLimit + 24);
            this.render(true);
        });
    }

    private void events() {
        if (this.match.group.equals("CS2")) {
            this.cs2Maps();
            return;
        }
        boolean found = this.tables("events");
        if (!this.detail.events.isEmpty()) {
            this.heading(this.match.group.equals("足球") ? "Key events" : (this.match.group.equals("NFL") ? "Scoring plays" : "Play-by-play"));
            LinearLayout timeline = this.panel();
            for (int i = 0; i < Math.min(this.eventsLimit, this.detail.events.size()); ++i) {
                this.event(timeline, this.detail.events.get(i));
            }
            this.body.addView((View)timeline);
            if (this.detail.events.size() > this.eventsLimit) {
                this.ui.gap(this.body, 12);
                this.body.addView((View)this.ui.button("Load more events · " + (this.detail.events.size() - this.eventsLimit) + " remaining", false, () -> {
                    this.eventsLimit += 50;
                    this.render(true);
                }));
            }
        } else if (!found) {
            this.empty("No events are available", this.missing());
        }
    }

    private void cs2Maps() {
        LinkedHashSet<String> maps = new LinkedHashSet<String>();
        for (MatchDetail.Table table : this.detail.tables) {
            if (!table.tab.equals("lineup") || table.category.isBlank() || table.category.equals("全场") || table.category.equals("伤停") || table.rows.isEmpty()) continue;
            maps.add(table.category);
        }
        if (maps.isEmpty()) {
            this.empty("Map data is not available for this match", this.missing());
            return;
        }
        String selected = maps.contains(this.mapFilter) ? this.mapFilter : (String)maps.iterator().next();
        HorizontalScrollView scroll = new HorizontalScrollView((Context)this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setTag((Object)"cs2-map-filter");
        LinearLayout chips = this.ui.row();
        int index = 0;
        for (String map : maps) {
            String mapText = displayText(map);
            TextView chip = this.ui.chip(mapText, map.equals(selected), () -> {
                this.mapFilter = map;
                this.render(true);
            });
            chip.setTag((Object)"cs2-map-chip");
            chip.setContentDescription((CharSequence)(mapText + (map.equals(selected) ? ", selected" : "")));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            if (index++ > 0) {
                params.setMarginStart(this.ui.dp(8.0f));
            }
            chips.addView((View)chip, (ViewGroup.LayoutParams)params);
            if (!map.equals(selected)) continue;
            chip.post(() -> scroll.scrollTo(Math.max(0, chip.getLeft() - this.ui.dp(8.0f)), 0));
        }
        scroll.addView((View)chips);
        this.body.addView((View)scroll, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        this.ui.gap(this.body, 16);
        MatchDetail.Table halves = null;
        for (MatchDetail.Table table : this.detail.tables) {
            if (!table.tab.equals("events") || !table.title.startsWith(selected + " · ")) continue;
            halves = table;
        }
        String scrollKey = "cs2|" + selected;
        int scrollX = this.tableScroll.getOrDefault(scrollKey, 0);
        new TeamPanelsView(this.ui, this.detail, this.body).scope(selected, this.sortColumn, this.descending, sort -> {
            this.sortColumn = sort.column();
            this.descending = sort.descending();
            this.render(true);
        }, scrollX, x -> {
            this.tableScroll.put(scrollKey, x);
            this.lastScroll = SystemClock.uptimeMillis();
        }, this.scoreCells(halves, this.match.home), this.scoreCells(halves, this.match.away));
        List<String> missing = this.missingMaps(maps);
        if (!missing.isEmpty()) {
            this.ui.gap(this.body, 16);
            TextView note = this.ui.text(String.join((CharSequence)", ", missing) + " have no per-map data", 12, this.ui.muted, false);
            note.setTag((Object)"map-missing");
            this.body.addView((View)note);
        }
        if (!this.detail.events.isEmpty()) {
            this.heading("Play-by-play");
            LinearLayout timeline = this.panel();
            for (int i = 0; i < Math.min(this.eventsLimit, this.detail.events.size()); ++i) {
                this.event(timeline, this.detail.events.get(i));
            }
            this.body.addView((View)timeline);
        }
    }

    private Map<String, String> scoreCells(MatchDetail.Table halves, String team) {
        LinkedHashMap<String, String> cells = new LinkedHashMap<String, String>();
        if (halves == null) {
            return cells;
        }
        int teamColumn = Math.max(0, halves.columns.indexOf("球队"));
        for (List<String> row : halves.rows) {
            int index;
            if (!DetailRows.cell(row, teamColumn).equals(team)) continue;
            int startColumn = halves.columns.indexOf("首开方");
            int tColumn = halves.columns.indexOf("T 方");
            int ctColumn = halves.columns.indexOf("CT 方");
            if (tColumn >= 0 && ctColumn >= 0) {
                String start = startColumn < 0 ? "" : DetailRows.cell(row, startColumn);
                String first = start.equals("CT") ? "CT 方" : "T 方";
                String second = first.equals("T 方") ? "CT 方" : "T 方";
                cells.put(first, DetailRows.cell(row, first.equals("T 方") ? tColumn : ctColumn));
                cells.put(second, DetailRows.cell(row, second.equals("T 方") ? tColumn : ctColumn));
            } else {
                for (String label : new String[]{"上半场", "下半场"}) {
                    index = halves.columns.indexOf(label);
                    if (index < 0) continue;
                    cells.put(label, DetailRows.cell(row, index));
                }
            }
            for (String label : new String[]{"加时", "总分"}) {
                index = halves.columns.indexOf(label);
                if (index < 0) continue;
                cells.put(label, DetailRows.cell(row, index));
            }
        }
        return cells;
    }

    private List<String> missingMaps(Set<String> maps) {
        ArrayList<String> missing = new ArrayList<String>();
        for (MatchDetail.Table score : this.detail.tables) {
            if (!score.tab.equals("overview")) continue;
            int nameColumn = Math.max(0, score.columns.indexOf("地图"));
            int statusColumn = score.columns.indexOf("状态");
            for (List<String> row : score.rows) {
                String name = DetailRows.cell(row, nameColumn).replaceFirst("^\\d+\\s+", "").trim();
                if (name.isBlank() || maps.contains(name) || missing.contains(name) || statusColumn >= 0 && !DetailRows.cell(row, statusColumn).equals("已结束")) continue;
                missing.add(name);
            }
        }
        return missing;
    }

    private void event(LinearLayout timeline, MatchDetail.Event event) {
        if (timeline.getChildCount() > 0) {
            timeline.addView(this.ui.divider());
        }
        LinearLayout row = this.ui.row();
        row.setGravity(Gravity.TOP);
        row.setPadding(0, this.ui.dp(10.0f), 0, this.ui.dp(10.0f));
        String title = displayText(DetailParsers.plain(event.title()));
        String text = displayText(DetailParsers.plain(event.text()));
        String combined = (title + " " + text).toLowerCase(java.util.Locale.ROOT);
        int pillBg = this.ui.tonal;
        int pillInk = this.ui.muted;
        if (combined.contains("goal") || combined.contains("touchdown") || combined.contains("penalty scored") || combined.contains("进球")) {
            pillBg = this.ui.primarySoft;
            pillInk = this.ui.primary;
        } else if (combined.contains("red card") || combined.contains("红牌") || combined.contains("sent off")) {
            pillBg = this.ui.errorSoft;
            pillInk = this.ui.error;
        } else if (combined.contains("yellow card") || combined.contains("黄牌")) {
            pillBg = this.ui.warnSoft;
            pillInk = this.ui.warn;
        } else if (combined.contains("substit") || combined.contains("换人")) {
            pillBg = this.ui.infoSoft;
            pillInk = this.ui.info;
        }
        String clockText = displayText(event.time());
        TextView clock = this.ui.badge(clockText.isBlank() ? "•" : clockText, pillBg, pillInk);
        LinearLayout.LayoutParams clockSlot = new LinearLayout.LayoutParams(this.ui.dp(54.0f), -2);
        clockSlot.setMarginEnd(this.ui.dp(12.0f));
        row.addView(clock, clockSlot);
        LinearLayout words = this.ui.column();
        if (!title.isBlank()) {
            words.addView((View)this.ui.text(title, 13, this.ui.text, true));
            this.ui.gap(words, 3);
        }
        if (!text.isBlank()) {
            words.addView((View)this.ui.text(text, 13, title.isBlank() ? this.ui.text : this.ui.muted, false));
        }
        row.addView((View)words, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        timeline.addView((View)row);
    }

    private String missing() {
        return this.match.scheduled() ? "Pre-match data has not been published" : "No data available";
    }

    private void empty(String title, String message) {
        this.ui.gap(this.body, 24);
        LinearLayout card = this.panel();
        card.addView((View)this.ui.text(displayText(title), 18, this.ui.text, true));
        this.ui.gap(card, 10);
        card.addView((View)this.ui.text(displayText(message), 14, this.ui.muted, false));
        this.body.addView((View)card);
    }

    private static String time(long value, String pattern) {
        return Formats.instant(value, pattern, "Not provided");
    }
}
