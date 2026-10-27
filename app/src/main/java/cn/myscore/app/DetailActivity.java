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
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import cn.myscore.app.BuildConfig;
import cn.myscore.app.Competition;
import cn.myscore.app.DetailParsers;
import cn.myscore.app.DetailRepository;
import cn.myscore.app.DetailRows;
import cn.myscore.app.DetailTableView;
import cn.myscore.app.FootballLineupView;
import cn.myscore.app.Logos;
import cn.myscore.app.LolMatchView;
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
import java.util.Collection;
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
    private Ui ui;
    private Match match;
    private MatchDetail detail;
    private DetailRepository repository;
    private WatchRepository watches;
    private Ui.WatchRow headerWatch;
    private LinearLayout root;
    private LinearLayout body;
    private LinearLayout content;
    private ScrollView scroll;
    private TextView status;
    private TextView headerTitle;
    private TextView headerState;
    private TextView headerStart;
    private TextView headerHome;
    private TextView headerAway;
    private TextView headerHomeScore;
    private TextView headerAwayScore;
    private Ui.ScoreText headerScore;
    private TextView[] tabLabels;
    private View[] tabRules;
    private String[] tabKeys;
    private String[] tabNames;
    private String tab = "overview";
    private String error = "";
    private boolean busy;
    private boolean active;
    private boolean chromeBuilt;
    private boolean fingerDown;
    private int selectedGame = -1;
    private int failures;
    private boolean pendingForce;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
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
    private TextView padTopState;

    public void onCreate(Bundle saved) {
        boolean dark = Ui.isDark((Context)this);
        this.setTheme(dark ? R.style.AppThemeDark : R.style.AppTheme);
        super.onCreate(saved);
        this.ui = new Ui((Context)this, dark);
        this.pad = Ui.isPad((Context)this);
        try {
            this.match = Match.fromJson(new JSONObject(this.getIntent().getStringExtra("match")));
            if (this.match.id.isBlank()) {
                throw new IllegalArgumentException();
            }
        }
        catch (Exception e) {
            this.finish();
            return;
        }
        if (saved != null) {
            this.tab = saved.getString("tab", "overview");
            this.selectedGame = saved.getInt("game", -1);
            this.teamFilter = saved.getString("team", "all");
            this.categoryFilter = saved.getString("category", "");
            this.sortColumn = saved.getString("sort", "");
            this.descending = saved.getBoolean("descending", true);
            this.mapFilter = saved.getString("map", "");
            this.metricsX = saved.getInt("metricsX");
            this.eventsLimit = saved.getInt("eventsLimit", 50);
            this.playerLimit = saved.getInt("playerLimit", 16);
        }
        if ((this.match.group.equals("F1") || this.match.group.equals("LOL")) && this.tab.equals("events")) {
            this.tab = "overview";
        }
        this.repository = new DetailRepository((Context)this);
        this.watches = WatchRepository.get(this);
        this.getWindow().setStatusBarColor(this.ui.bg);
        this.getWindow().setNavigationBarColor(this.ui.bg);
        this.getWindow().setBackgroundDrawable((Drawable)new ColorDrawable(this.ui.bg));
        if (Build.VERSION.SDK_INT >= 30) {
            this.getWindow().setDecorFitsSystemWindows(false);
        }
        this.root = this.ui.column();
        this.setContentView((View)this.root);
        this.root.setOnApplyWindowInsetsListener((v, insets) -> {
            int inset;
            int n = inset = this.pad ? 0 : this.ui.pageInset();
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
        this.fingerDown = false;
        this.handler.removeCallbacks(this.tick);
        this.handler.removeCallbacks(this.update);
        super.onPause();
    }

    public void onDestroy() {
        this.handler.removeCallbacksAndMessages(null);
        this.worker.shutdownNow();
        super.onDestroy();
    }

    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("tab", this.tab);
        state.putInt("game", this.selectedGame);
        state.putString("team", this.teamFilter);
        state.putString("category", this.categoryFilter);
        state.putString("sort", this.sortColumn);
        state.putBoolean("descending", this.descending);
        state.putString("map", this.mapFilter);
        state.putInt("metricsX", this.metricsX);
        state.putInt("eventsLimit", this.eventsLimit);
        state.putInt("playerLimit", this.playerLimit);
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
            this.status.setText((CharSequence)"正在更新详情…");
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
                });
            }
            MatchDetail next = null;
            String problem = "";
            try {
                next = this.repository.fetch(requested, forced);
            }
            catch (Exception e) {
                problem = e.getMessage() == null ? "网络连接失败" : e.getMessage();
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
                    this.match = result.match;
                }
                if (this.active) {
                    this.render(true);
                    this.resolveWatch();
                    if (this.pendingForce) {
                        this.load(true);
                        return;
                    }
                    if (!this.match.finalised() && !this.match.cancelled() || !failure.isEmpty()) {
                        long interval = RefreshPolicy.detail(this.match, System.currentTimeMillis());
                        long elapsed = result == null ? 0L : Math.max(0L, System.currentTimeMillis() - result.fetched);
                        this.handler.postDelayed(this.tick, RefreshPolicy.remaining(RefreshPolicy.retry(interval, this.failures), elapsed));
                    }
                }
            });
        });
    }

    private FrameLayout iconButton(String kind, String description, Runnable click) {
        FrameLayout b = new FrameLayout((Context)this);
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
        if (this.pad) {
            this.renderPad(preserve);
            return;
        }
        if (preserve && (this.fingerDown || SystemClock.uptimeMillis() - this.lastScroll < 200L)) {
            this.handler.removeCallbacks(this.update);
            this.handler.postDelayed(this.update, 200L);
            return;
        }
        boolean bl = first = !this.chromeBuilt;
        if (first) {
            this.buildChrome();
        } else {
            this.updateChrome();
        }
        String key = this.bodyKey();
        long started = SystemClock.uptimeMillis();
        boolean bl2 = changed = first || !key.equals(this.rendered);
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
        boolean bl = first = !this.chromeBuilt;
        if (first) {
            this.buildPadChrome();
        }
        this.updatePadChrome();
        String key = this.bodyKey();
        boolean bl2 = changed = first || !key.equals(this.rendered);
        if (changed) {
            this.rendered = key;
            this.rebuildPadBody(preserve);
        }
        this.updateStatus();
    }

    private void buildPadChrome() {
        this.root.removeAllViews();
        LinearLayout bar = this.ui.row();
        bar.setPadding(this.ui.dp(12.0f), this.ui.dp(10.0f), this.ui.dp(16.0f), this.ui.dp(10.0f));
        bar.addView((View)this.iconButton("back", "返回比赛列表", () -> ((DetailActivity)this).finish()));
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
        TextView live = this.ui.text("", 12, this.ui.muted, true);
        live.setGravity(0x800005);
        live.setPadding(this.ui.dp(8.0f), 0, this.ui.dp(12.0f), 0);
        this.padTopState = live;
        bar.addView((View)live);
        bar.addView((View)this.iconButton("refresh", "刷新比赛详情", () -> this.load(true)));
        this.root.addView((View)bar);
        this.root.addView(this.ui.divider());
        LinearLayout work = this.ui.row();
        this.padLeftScroll = new ScrollView((Context)this);
        this.padLeftScroll.setFillViewport(true);
        this.padLeftScroll.setVerticalScrollBarEnabled(false);
        this.padLeftScroll.setClipToPadding(false);
        this.padLeftHost = this.ui.column();
        this.padLeftHost.setPadding(this.ui.dp(26.0f), this.ui.dp(24.0f), this.ui.dp(26.0f), this.ui.dp(32.0f));
        this.padLeftScroll.addView((View)this.padLeftHost);
        work.addView((View)this.padLeftScroll, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(404.0f), -1));
        work.addView(this.padRule());
        LinearLayout reading = this.ui.column();
        work.addView((View)reading, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -1, 1.0f));
        this.padTabs = this.ui.row();
        this.padTabs.setPadding(this.ui.dp(30.0f), 0, this.ui.dp(30.0f), 0);
        reading.addView((View)this.padTabs);
        reading.addView(this.ui.divider());
        this.scroll = new ScrollView((Context)this);
        this.scroll.setFillViewport(true);
        this.scroll.setVerticalScrollBarEnabled(false);
        this.scroll.setClipToPadding(false);
        this.scroll.setPadding(this.ui.dp(32.0f), this.ui.dp(16.0f), this.ui.dp(36.0f), this.ui.dp(36.0f));
        this.scroll.setOnScrollChangeListener((v, x, sy, ox, oy) -> {
            this.lastScroll = SystemClock.uptimeMillis();
        });
        this.body = this.ui.column();
        this.scroll.addView((View)this.body);
        reading.addView((View)this.scroll, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, 0, 1.0f));
        this.root.addView((View)work, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, 0, 1.0f));
        this.buildPadTabs();
        this.chromeBuilt = true;
    }

    private void buildPadTabs() {
        String[] stringArray;
        String[] stringArray2;
        this.padTabs.removeAllViews();
        boolean f1 = this.match.group.equals("F1");
        boolean lol = this.match.group.equals("LOL");
        boolean cs = this.match.group.equals("CS2");
        if (cs && this.tab.equals("stats")) {
            this.tab = "lineup";
        }
        if (cs) {
            String[] stringArray3 = new String[3];
            stringArray3[0] = "overview";
            stringArray3[1] = "lineup";
            stringArray2 = stringArray3;
            stringArray3[2] = "events";
        } else if (f1 || lol) {
            String[] stringArray4 = new String[3];
            stringArray4[0] = "overview";
            stringArray4[1] = "lineup";
            stringArray2 = stringArray4;
            stringArray4[2] = "stats";
        } else {
            String[] stringArray5 = new String[4];
            stringArray5[0] = "overview";
            stringArray5[1] = "lineup";
            stringArray5[2] = "stats";
            stringArray2 = stringArray5;
            stringArray5[3] = "events";
        }
        this.tabKeys = stringArray2;
        if (cs) {
            String[] stringArray6 = new String[3];
            stringArray6[0] = "总览";
            stringArray6[1] = "选手";
            stringArray = stringArray6;
            stringArray6[2] = "小局";
        } else if (f1) {
            String[] stringArray7 = new String[3];
            stringArray7[0] = "总览";
            stringArray7[1] = "车手";
            stringArray = stringArray7;
            stringArray7[2] = "成绩";
        } else if (lol) {
            String[] stringArray8 = new String[3];
            stringArray8[0] = "总览";
            stringArray8[1] = "比赛";
            stringArray = stringArray8;
            stringArray8[2] = "统计";
        } else {
            String[] stringArray9 = new String[4];
            stringArray9[0] = "总览";
            stringArray9[1] = this.lineupName();
            stringArray9[2] = "统计";
            stringArray = stringArray9;
            stringArray9[3] = "进程";
        }
        this.tabNames = stringArray;
        this.tabLabels = new TextView[this.tabKeys.length];
        this.tabRules = new View[this.tabKeys.length];
        for (int i = 0; i < this.tabKeys.length; ++i) {
            String key = this.tabKeys[i];
            LinearLayout item = this.ui.column();
            item.setGravity(17);
            this.ui.clickable((View)item, 0, 0);
            item.setFocusable(true);
            TextView label = this.ui.text(this.tabNames[i], 14, this.tab.equals(key) ? this.ui.text : this.ui.muted, this.tab.equals(key));
            label.setGravity(17);
            label.setMinHeight(this.ui.dp(54.0f));
            label.setPadding(this.ui.dp(18.0f), 0, this.ui.dp(18.0f), 0);
            item.addView((View)label, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-2, -2));
            View underline = new View((Context)this);
            underline.setBackground((Drawable)this.ui.shape(this.tab.equals(key) ? this.ui.text : this.ui.bg, 2));
            item.addView(underline, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(28.0f), this.ui.dp(2.0f)));
            item.setOnClickListener(v -> {
                this.tab = key;
                this.categoryFilter = "";
                this.sortColumn = "";
                this.metricsX = 0;
                this.playerLimit = 16;
                this.render(false);
            });
            this.padTabs.addView((View)item, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-2, -2));
            this.tabLabels[i] = label;
            this.tabRules[i] = underline;
        }
        this.updateTabs();
    }

    private void updatePadChrome() {
        Competition competition = Competition.find(this.match.league);
        if (this.padTitle != null) {
            this.padTitle.setText((CharSequence)(competition == null ? this.match.group : competition.name));
        }
        if (this.padSubtitle != null) {
            String when = this.match.timeConfirmed ? DetailActivity.time(this.match.start, "M月d日 EEE HH:mm") : "时间待定";
            String extra = this.match.detail.isBlank() ? "" : " · " + this.match.detail;
            this.padSubtitle.setText((CharSequence)(when + extra + " · 北京时间 · " + this.match.source));
        }
        if (this.padTopState != null) {
            this.padTopState.setText((CharSequence)this.match.status);
            this.padTopState.setTextColor(this.match.live() ? this.ui.primary : (this.match.cancelled() ? this.ui.error : this.ui.muted));
        }
        this.updateTabs();
        this.rebuildPadLeft();
    }

    private void rebuildPadLeft() {
        if (this.padLeftHost == null) {
            return;
        }
        int y = this.padLeftScroll == null ? 0 : this.padLeftScroll.getScrollY();
        this.padLeftHost.removeAllViews();
        Competition competition = Competition.find(this.match.league);
        LinearLayout kicker = this.ui.row();
        TextView name = this.ui.text(competition == null ? this.match.group : competition.name, 11, this.ui.muted, true);
        name.setLetterSpacing(0.06f);
        kicker.addView((View)name, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView state = this.ui.text(this.match.live() && !this.match.status.isBlank() ? this.match.status : Rules.stateLabel(this.match.state), 11, this.match.live() ? this.ui.primary : (this.match.cancelled() ? this.ui.error : this.ui.muted), true);
        kicker.addView((View)state);
        this.padLeftHost.addView((View)kicker);
        this.ui.gap(this.padLeftHost, 10);
        this.padLeftHost.addView((View)this.padScoreCard());
        if (this.detail != null) {
            if (!this.detail.facts.isEmpty()) {
                this.padLeftHeading("比赛信息");
                this.padLeftHost.addView((View)this.padFactsCard(9));
                this.ui.gap(this.padLeftHost, 20);
            }
            if (!this.detail.warning.isBlank() && !this.match.group.equals("CS2")) {
                TextView note = this.ui.text(this.detail.warning, 11, this.ui.muted, false);
                note.setLineSpacing((float)this.ui.dp(3.0f), 1.0f);
                this.padLeftHost.addView((View)note);
                this.ui.gap(this.padLeftHost, 16);
            }
            this.padLeftHeading("数据来源");
            LinearLayout source = this.ui.column();
            source.addView((View)this.ui.text(this.detail.source, 12, this.ui.text, true));
            this.ui.gap(source, 4);
            source.addView((View)this.ui.text("获取于 " + DetailActivity.time(this.detail.fetched, "M月d日 HH:mm:ss") + (this.match.updated > 0L ? "\n来源发布 " + DetailActivity.time(this.match.updated, "M月d日 HH:mm") : ""), 11, this.ui.muted, false));
            this.padLeftHost.addView((View)source);
            if (!this.match.url.isBlank()) {
                this.ui.gap(this.padLeftHost, 10);
                this.padLeftHost.addView((View)this.ui.button("打开来源页面", false, () -> this.openUrl()), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
            }
        } else {
            this.padLeftHeading("比赛数据");
            TextView loading = this.ui.text(this.busy ? "正在读取比赛数据…" : "尚未获取比赛数据", 12, this.ui.muted, false);
            loading.setLineSpacing((float)this.ui.dp(3.0f), 1.0f);
            this.padLeftHost.addView((View)loading);
        }
        if (this.padLeftScroll != null) {
            this.padLeftScroll.post(() -> this.padLeftScroll.scrollTo(0, y));
        }
    }

    private LinearLayout padScoreCard() {
        LinearLayout card = this.ui.column();
        card.setBackground((Drawable)this.ui.panel());
        card.setPadding(this.ui.dp(20.0f), this.ui.dp(18.0f), this.ui.dp(20.0f), this.ui.dp(18.0f));
        if (this.match.group.equals("F1")) {
            TextView title = this.ui.text(this.match.displayTitle(), 22, this.ui.text, true);
            title.setLetterSpacing(-0.02f);
            card.addView((View)title);
            this.ui.gap(card, 8);
            card.addView((View)this.ui.text(this.match.status + " · " + (this.match.timeConfirmed ? DetailActivity.time(this.match.start, "M月d日 HH:mm") : "时间待定"), 12, this.match.live() ? this.ui.primary : this.ui.muted, false));
            if (!this.match.results.isEmpty()) {
                this.ui.gap(card, 14);
                card.addView(this.ui.divider());
                for (int i = 0; i < Math.min(3, this.match.results.size()); ++i) {
                    TextView row = this.ui.text(this.match.results.get(i), 12, this.ui.text, false);
                    row.setPadding(0, this.ui.dp(8.0f), 0, this.ui.dp(8.0f));
                    card.addView((View)row);
                }
            }
            return card;
        }
        card.addView((View)this.padTeamLine(true));
        this.ui.gap(card, 10);
        card.addView(this.ui.divider());
        this.ui.gap(card, 10);
        card.addView((View)this.padTeamLine(false));
        this.ui.gap(card, 14);
        TextView when = this.ui.text(this.match.timeConfirmed ? DetailActivity.time(this.match.start, "M月d日 EEE HH:mm") : "时间待定", 12, this.ui.text, true);
        card.addView((View)when);
        this.ui.gap(card, 3);
        TextView status = this.ui.text(this.match.live() && !this.match.status.isBlank() ? this.match.status : Rules.stateLabel(this.match.state) + (this.match.timeConfirmed ? " · 北京时间" : ""), 11, this.match.live() ? this.ui.primary : this.ui.muted, false);
        card.addView((View)status);
        if (!this.match.detail.isBlank()) {
            this.ui.gap(card, 3);
            TextView detail = this.ui.text(this.match.detail, 11, this.ui.muted, false);
            detail.setLineSpacing((float)this.ui.dp(2.0f), 1.0f);
            card.addView((View)detail);
        }
        WatchLinks.Link watch = this.watches.link(this.match);
        if (watch != null) {
            this.ui.gap(card, 8);
            card.addView(this.ui.divider());
            this.ui.gap(card, 2);
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
        crest.addView((View)Logos.view(this.ui, logo, name, 52), (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(this.ui.dp(52.0f), this.ui.dp(52.0f), 17));
        boolean available = id != null && !id.isBlank();
        crest.setFocusable(available);
        crest.setClickable(available);
        crest.setContentDescription(available ? "查看 " + name + " 的赛程" : null);
        crest.setOnClickListener(available ? v -> this.openTeam(home) : null);
        row.addView((View)crest, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(60.0f), this.ui.dp(52.0f)));
        TextView team = this.ui.text(name, 17, this.ui.text, true);
        team.setPadding(this.ui.dp(8.0f), 0, this.ui.dp(8.0f), 0);
        row.addView((View)team, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView score = this.ui.text(this.match.hasScore() ? (home ? this.match.homeScore : this.match.awayScore) : "—", 38, this.ui.scoreColor(this.match, home), true);
        score.setGravity(0x800005);
        score.setIncludeFontPadding(false);
        row.addView((View)score);
        row.setContentDescription((CharSequence)(name + (this.match.hasScore() ? "，比分 " + (home ? this.match.homeScore : this.match.awayScore) : "，比分尚未提供")));
        return row;
    }

    private void padLeftHeading(String text) {
        this.ui.gap(this.padLeftHost, 20);
        this.padLeftHost.addView((View)this.ui.text(text, 12, this.ui.muted, true));
        this.ui.gap(this.padLeftHost, 9);
    }

    private LinearLayout padStatsCard(List<MatchDetail.Stat> stats, int limit) {
        LinearLayout card = this.ui.column();
        card.setBackground((Drawable)this.ui.panel());
        card.setPadding(this.ui.dp(16.0f), this.ui.dp(12.0f), this.ui.dp(16.0f), this.ui.dp(12.0f));
        LinearLayout names = this.ui.row();
        TextView home = this.ui.text(this.match.home, 11, this.ui.text, true);
        TextView away = this.ui.text(this.match.away, 11, this.ui.text, true);
        away.setGravity(0x800005);
        names.addView((View)home, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        names.addView((View)away, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        card.addView((View)names);
        this.ui.gap(card, 6);
        card.addView(this.ui.divider());
        for (int i = 0; i < Math.min(limit, stats.size()); ++i) {
            MatchDetail.Stat s = stats.get(i);
            LinearLayout row = this.ui.row();
            row.setPadding(0, this.ui.dp(9.0f), 0, this.ui.dp(9.0f));
            TextView h = this.ui.text(s.home(), 13, this.ui.primary, true);
            TextView label = this.ui.text(s.label(), 10, this.ui.muted, false);
            TextView a = this.ui.text(s.away(), 13, this.ui.secondary, true);
            label.setGravity(17);
            a.setGravity(0x800005);
            row.addView((View)h, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
            row.addView((View)label, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.4f));
            row.addView((View)a, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
            card.addView((View)row);
            double hv = DetailActivity.numeric(s.home());
            double av = DetailActivity.numeric(s.away());
            if (hv >= 0.0 && av >= 0.0 && hv + av > 0.0) {
                LinearLayout track = this.ui.row();
                View left = new View((Context)this);
                View right = new View((Context)this);
                left.setBackground((Drawable)this.ui.shape(this.ui.primary, 2));
                right.setBackground((Drawable)this.ui.shape(this.ui.secondary, 2));
                track.addView(left, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, this.ui.dp(3.0f), (float)hv));
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, this.ui.dp(3.0f), (float)av);
                rp.setMarginStart(this.ui.dp(4.0f));
                track.addView(right, (ViewGroup.LayoutParams)rp);
                card.addView((View)track);
            }
            if (i >= Math.min(limit, stats.size()) - 1) continue;
            card.addView(this.ui.divider());
        }
        return card;
    }

    private LinearLayout padFactsCard(int limit) {
        LinearLayout card = this.ui.column();
        card.setBackground((Drawable)this.ui.panel());
        card.setPadding(this.ui.dp(16.0f), this.ui.dp(10.0f), this.ui.dp(16.0f), this.ui.dp(10.0f));
        LinkedHashMap<String, List> facts = new LinkedHashMap<String, List>();
        for (MatchDetail.Fact f : this.detail.facts) {
            String label = DetailParsers.label(DetailParsers.plain(f.label()));
            String value = DetailParsers.plain(f.value());
            if (value.isBlank()) continue;
            facts.computeIfAbsent(label.isBlank() ? "信息" : label, k -> new ArrayList()).add(value);
        }
        int index = 0;
        for (Map.Entry fact : facts.entrySet()) {
            if (index >= limit) break;
            if (index++ > 0) {
                card.addView(this.ui.divider());
            }
            LinearLayout row = this.ui.row();
            row.setGravity(48);
            row.setPadding(0, this.ui.dp(9.0f), 0, this.ui.dp(9.0f));
            row.addView((View)this.ui.text((String)fact.getKey(), 10, this.ui.muted, false), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
            TextView value = this.ui.text(String.join((CharSequence)"\n", new LinkedHashSet((Collection)fact.getValue())), 12, this.ui.text, false);
            value.setLineSpacing((float)this.ui.dp(2.0f), 1.0f);
            row.addView((View)value, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.35f));
            card.addView((View)row);
        }
        if (index == 0) {
            card.addView((View)this.ui.text("来源尚未提供更多比赛信息", 12, this.ui.muted, false));
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
        this.status = this.ui.text(this.statusText(), 11, this.ui.muted, false);
        this.body.addView((View)this.status);
        this.ui.gap(this.body, 12);
        if (!this.error.isEmpty()) {
            LinearLayout warning = this.panel();
            warning.addView((View)this.ui.text("暂未更新 · " + this.error + (this.detail == null ? "" : "\n显示上次获取的数据"), 12, this.ui.error, false));
            this.ui.gap(warning, 10);
            warning.addView((View)this.ui.button("重新加载", false, () -> this.load(true)));
            this.body.addView((View)warning);
            this.ui.gap(this.body, 14);
        }
        if (!(this.detail == null || this.detail.warning.isBlank() || this.match.group.equals("CS2") && !this.tab.equals("overview"))) {
            TextView note = this.ui.text(this.detail.warning, 12, this.ui.muted, false);
            note.setLineSpacing((float)this.ui.dp(3.0f), 1.0f);
            this.body.addView((View)note);
            this.ui.gap(this.body, 16);
        }
        if (this.detail == null) {
            this.empty(this.busy ? "正在读取比赛数据" : "比赛详情暂不可用", this.busy ? "正在连接本场比赛的数据来源。" : "请检查网络并重新加载，列表仍保留上次记录。");
        } else if (this.match.group.equals("LOL")) {
            this.lolPad();
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
    }

    private void padOverview() {
        int panelWidth;
        int width = this.ui.c.getResources().getConfiguration().screenWidthDp;
        boolean two = width >= 1280;
        LinearLayout columns = this.ui.row();
        columns.setGravity(48);
        LinearLayout left = this.ui.column();
        LinearLayout right = this.ui.column();
        if (two) {
            columns.addView((View)left, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
            columns.addView(this.hspace(26));
            columns.addView((View)right, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        }
        if (!this.detail.stats.isEmpty()) {
            this.headingInto(left, "数据速览");
            left.addView((View)this.padStatsCard(this.detail.stats, 8));
            this.ui.gap(left, 24);
        }
        if (this.detail.stats.isEmpty()) {
            this.headingInto(left, "比赛概览");
            LinearLayout emptyCard = this.panel();
            emptyCard.addView((View)this.ui.text("本场暂无更多概览数据，比赛信息见左侧。", 12, this.ui.muted, false));
            left.addView((View)emptyCard);
        }
        if (this.tablesInto(right, "overview", panelWidth = Math.max(440, (this.ui.c.getResources().getConfiguration().screenWidthDp - 404 - 72 - 26) / 2))) {
            this.ui.gap(right, 22);
        }
        if (!this.detail.events.isEmpty()) {
            this.headingInto(right, "最新进程");
            LinearLayout card = this.panel();
            for (int i = 0; i < Math.min(8, this.detail.events.size()); ++i) {
                this.event(card, this.detail.events.get(i));
            }
            right.addView((View)card);
            this.ui.gap(right, 24);
        }
        if (two) {
            this.body.addView((View)columns);
        } else {
            this.body.addView((View)left);
            this.ui.gap(this.body, 24);
            this.body.addView((View)right);
        }
    }

    private void padStatistics() {
        if (this.match.group.equals("F1")) {
            this.roster("stats");
            return;
        }
        boolean hasStats = !this.detail.stats.isEmpty();
        boolean hasTables = this.detail.tables.stream().anyMatch(t -> t.tab.equals("stats"));
        if (!hasStats && !hasTables) {
            this.empty("本场统计尚未提供", this.missing());
            return;
        }
        if (hasStats && hasTables) {
            LinearLayout columns = this.ui.row();
            columns.setGravity(48);
            LinearLayout left = this.ui.column();
            LinearLayout right = this.ui.column();
            columns.addView((View)left, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 0.9f));
            columns.addView(this.hspace(26));
            columns.addView((View)right, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.1f));
            this.body.addView((View)columns);
            this.headingInto(left, "球队对比");
            left.addView((View)this.padStatsCard(this.detail.stats, this.detail.stats.size()));
            this.headingInto(right, "球员与分段数据");
            this.tablesInto(right, "stats", Math.max(420, (this.ui.c.getResources().getConfiguration().screenWidthDp - 404 - 72 - 26) / 2));
        } else if (hasStats) {
            this.heading("球队对比");
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
            boolean two;
            int width = this.ui.c.getResources().getConfiguration().screenWidthDp;
            boolean bl = two = width >= 1280 && this.detail.events.size() > 6;
            this.heading(this.match.group.equals("足球") ? "关键事件" : (this.match.group.equals("NFL") ? "得分进程" : "比赛进程"));
            if (two) {
                LinearLayout columns = this.ui.row();
                columns.setGravity(48);
                LinearLayout left = this.ui.column();
                LinearLayout right = this.ui.column();
                columns.addView((View)left, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
                columns.addView(this.hspace(26));
                columns.addView((View)right, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
                int half = (Math.min(this.eventsLimit, this.detail.events.size()) + 1) / 2;
                LinearLayout lc = this.panel();
                LinearLayout rc = this.panel();
                for (int i = 0; i < Math.min(this.eventsLimit, this.detail.events.size()); ++i) {
                    if (i < half) {
                        this.event(lc, this.detail.events.get(i));
                        continue;
                    }
                    this.event(rc, this.detail.events.get(i));
                }
                left.addView((View)lc);
                right.addView((View)rc);
                this.body.addView((View)columns);
            } else {
                LinearLayout card = this.panel();
                for (int i = 0; i < Math.min(this.eventsLimit, this.detail.events.size()); ++i) {
                    this.event(card, this.detail.events.get(i));
                }
                this.body.addView((View)card);
            }
            if (this.detail.events.size() > this.eventsLimit) {
                this.ui.gap(this.body, 12);
                this.body.addView((View)this.ui.button("加载更多进程 · 剩余 " + (this.detail.events.size() - this.eventsLimit) + " 条", false, () -> {
                    this.eventsLimit += 50;
                    this.render(true);
                }));
            }
        } else if (!found) {
            this.empty("暂无比赛进程", this.missing());
        }
    }

    private void padSourceCard(LinearLayout into) {
        this.headingInto(into, "来源与更新时间");
        LinearLayout card = this.panel();
        card.addView((View)this.ui.text(this.detail.source, 13, this.ui.text, true));
        this.ui.gap(card, 6);
        card.addView((View)this.ui.text("获取于 " + DetailActivity.time(this.detail.fetched, "M月d日 HH:mm:ss") + (this.match.group.equals("LOL") ? "\n赛程发布 " + DetailActivity.time(this.match.updated, "M月d日 HH:mm:ss") : ""), 11, this.ui.muted, false));
        this.ui.gap(card, 12);
        card.addView((View)this.ui.button("打开来源页面", false, this::openUrl), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        into.addView((View)card);
    }

    private void openUrl() {
        if (this.match.url.isBlank()) {
            Sheet.message(this, "来源页面", "来源尚未提供可直接打开的页面。");
            return;
        }
        try {
            this.startActivity(new Intent("android.intent.action.VIEW", Uri.parse((String)this.match.url)));
        }
        catch (ActivityNotFoundException e) {
            Sheet.message(this, "无法打开来源", "手机没有可用的浏览器。");
        }
    }

    private void openWatch() {
        WatchLinks.Link link = this.watches.link(this.match);
        if (link == null) {
            return;
        }
        try {
            this.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link.url)));
        }
        catch (RuntimeException e) {
            Sheet.message(this, "无法打开直播", "手机没有可用的浏览器。");
        }
    }

    private void resolveWatch() {
        if (this.watches == null || this.match == null) {
            return;
        }
        this.watches.resolve(this.match, () -> {
            if (this.isFinishing() || this.isDestroyed()) {
                return;
            }
            if (this.pad) {
                this.render(true);
            } else if (this.headerWatch != null) {
                this.headerWatch.bind(this.watches.link(this.match), this.match.live(), this::openWatch);
            }
        });
    }

    private void headingInto(LinearLayout into, String title) {
        into.addView((View)this.ui.text(title, 15, this.ui.text, true));
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
            int width = widthDp > 0 ? widthDp : Math.max(420, this.ui.c.getResources().getConfiguration().screenWidthDp - 404 - 72);
            into.addView(new DetailTableView(this.ui, false, true, false, width).simple(t), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
            this.ui.gap(into, 18);
            found = true;
        }
        return found;
    }

    private View hspace(int dp) {
        View v = new View((Context)this);
        v.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(dp), 1));
        return v;
    }

    private View vspace(int dp) {
        View v = new View((Context)this);
        v.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(1, this.ui.dp(dp)));
        return v;
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
        appbar.setPadding(this.ui.dp(4.0f), this.ui.dp(4.0f), this.ui.dp(4.0f), this.ui.dp(2.0f));
        appbar.addView((View)this.iconButton("back", "返回比赛列表", () -> ((DetailActivity)this).finish()));
        Competition competition = Competition.find(this.match.league);
        TextView title = this.ui.text(competition == null ? this.match.group : competition.name, 16, this.ui.text, true);
        title.setGravity(17);
        appbar.addView((View)title, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        appbar.addView((View)this.iconButton("refresh", "刷新比赛详情", () -> this.load(true)));
        this.root.addView((View)appbar);
        this.buildTabs();
        this.root.addView(this.ui.divider());
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
        this.body = this.ui.column();
        this.body.setPadding(this.ui.dp(16.0f), this.ui.dp(12.0f), this.ui.dp(16.0f), this.ui.dp(24.0f));
        this.content.addView((View)this.body);
        this.status = null;
        this.chromeBuilt = true;
    }

    private void buildScoreHeader() {
        LinearLayout card = this.ui.column();
        card.setBackground((Drawable)this.ui.panel());
        card.setPadding(this.ui.dp(16.0f), this.ui.dp(16.0f), this.ui.dp(16.0f), this.ui.dp(16.0f));
        if (this.match.group.equals("F1")) {
            this.headerTitle = this.ui.text(this.match.displayTitle(), 18, this.ui.text, true);
            this.headerTitle.setGravity(17);
            card.addView((View)this.headerTitle);
            this.ui.gap(card, 8);
            this.headerState = this.ui.text(this.match.status + " · " + (this.match.timeConfirmed ? DetailActivity.time(this.match.start, "M/d HH:mm") : "时间待定"), 12, this.ui.muted, false);
            this.headerState.setGravity(17);
            card.addView((View)this.headerState);
        } else if (this.getResources().getConfiguration().fontScale >= 1.5f || this.getResources().getConfiguration().fontScale >= 1.25f && this.ui.contentWidthDp() < 400) {
            this.headerHomeScore = this.stackedTeam(card, true);
            this.ui.gap(card, 12);
            card.addView(this.ui.divider());
            this.ui.gap(card, 12);
            this.headerAwayScore = this.stackedTeam(card, false);
            this.ui.gap(card, 16);
            this.headerState = this.ui.text(this.match.status, 12, this.match.live() ? this.ui.primary : this.ui.muted, true);
            card.addView((View)this.headerState);
            this.headerStart = this.ui.text(this.match.timeConfirmed ? DetailActivity.time(this.match.start, "M/d HH:mm") : "时间待定", 12, this.ui.muted, false);
            card.addView((View)this.headerStart);
        } else {
            LinearLayout row = this.ui.row();
            row.setGravity(17);
            LinearLayout homeSide = this.team(true);
            row.addView((View)homeSide, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
            LinearLayout center = this.ui.column();
            center.setGravity(17);
            this.headerScore = this.ui.score(this.match.homeScore, this.match.awayScore, this.match.hasScore(), 34);
            this.headerScore.setResultColors(this.ui.scoreColor(this.match, true), this.ui.scoreColor(this.match, false));
            center.addView((View)this.headerScore, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
            this.ui.gap(center, 8);
            this.headerState = this.ui.text(this.match.status, 12, this.match.live() ? this.ui.primary : this.ui.muted, true);
            this.headerState.setGravity(17);
            center.addView((View)this.headerState, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
            this.ui.gap(center, 6);
            this.headerStart = this.ui.text(this.match.timeConfirmed ? DetailActivity.time(this.match.start, "M/d HH:mm") : "时间待定", 11, this.ui.muted, false);
            this.headerStart.setGravity(17);
            center.addView((View)this.headerStart, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
            row.addView((View)center, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.35f));
            LinearLayout awaySide = this.team(false);
            row.addView((View)awaySide, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
            card.addView((View)row);
            this.headerHome = (TextView)homeSide.getChildAt(2);
            this.headerAway = (TextView)awaySide.getChildAt(2);
        }
        WatchLinks.Link watch = this.watches.link(this.match);
        if (watch != null) {
            this.ui.gap(card, 10);
            card.addView(this.ui.divider());
            this.ui.gap(card, 2);
            this.headerWatch = this.ui.watchRow();
            this.headerWatch.bind(watch, this.match.live(), this::openWatch);
            card.addView((View)this.headerWatch);
        }
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(this.ui.dp(16.0f), this.ui.dp(16.0f), this.ui.dp(16.0f), this.ui.dp(4.0f));
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
        TextView score = this.ui.text(this.match.hasScore() ? (home ? this.match.homeScore : this.match.awayScore) : "—", 26, this.ui.scoreColor(this.match, home), true);
        score.setGravity(0x800005);
        line.addView((View)score, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        card.addView((View)line);
        name.setGravity(0x800003);
        side.setGravity(0x800003);
        card.addView((View)side, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        return score;
    }

    private void updateChrome() {
        if (this.match.group.equals("F1")) {
            if (this.headerTitle != null) {
                this.headerTitle.setText((CharSequence)this.match.displayTitle());
                this.headerState.setText((CharSequence)(this.match.status + " · " + (this.match.timeConfirmed ? DetailActivity.time(this.match.start, "M/d HH:mm") : "时间待定")));
            }
        } else if (this.headerHome != null) {
            this.headerHome.setText((CharSequence)this.match.home);
            this.headerAway.setText((CharSequence)this.match.away);
            if (this.headerScore != null) {
                this.headerScore.setScore(this.match.homeScore, this.match.awayScore, this.match.hasScore());
                this.headerScore.setResultColors(this.ui.scoreColor(this.match, true), this.ui.scoreColor(this.match, false));
            } else {
                this.headerHomeScore.setText((CharSequence)(this.match.hasScore() ? this.match.homeScore : "—"));
                this.headerAwayScore.setText((CharSequence)(this.match.hasScore() ? this.match.awayScore : "—"));
                this.headerHomeScore.setTextColor(this.ui.scoreColor(this.match, true));
                this.headerAwayScore.setTextColor(this.ui.scoreColor(this.match, false));
            }
            this.headerState.setText((CharSequence)this.match.status);
            this.headerState.setTextColor(this.match.live() ? this.ui.primary : this.ui.muted);
            this.headerStart.setText((CharSequence)(this.match.timeConfirmed ? DetailActivity.time(this.match.start, "M/d HH:mm") : "时间待定"));
        }
        this.updateTabs();
        this.updateStatus();
    }

    private void updateStatus() {
        if (this.status != null) {
            this.status.setText((CharSequence)this.statusText());
        }
    }

    private String bodyKey() {
        StringBuilder key = new StringBuilder(96).append(this.tab).append(':').append(this.selectedGame).append(':').append(this.teamFilter).append(':').append(this.categoryFilter).append(':').append(this.mapFilter).append(':').append(this.sortColumn).append(':').append(this.descending).append(':').append(this.eventsLimit).append(':').append(this.playerLimit).append(':').append(this.error).append(':').append(this.match.group);
        if (this.detail == null) {
            key.append(":empty:").append(this.busy);
        } else {
            key.append(':').append(this.detail.bodySignature());
        }
        return key.toString();
    }

    private void rebuildBody(boolean preserve) {
        int y = preserve && this.scroll != null ? this.scroll.getScrollY() : 0;
        this.body = this.ui.column();
        this.body.setPadding(this.ui.dp(16.0f), this.ui.dp(12.0f), this.ui.dp(16.0f), this.ui.dp(24.0f));
        if (this.content != null) {
            if (this.content.getChildCount() > 1) {
                this.content.removeViewAt(1);
            }
            this.content.addView((View)this.body);
        }
        this.status = this.ui.text(this.statusText(), 12, this.ui.muted, false);
        this.body.addView((View)this.status);
        this.ui.gap(this.body, 12);
        if (!this.error.isEmpty()) {
            LinearLayout warning = this.panel();
            warning.addView((View)this.ui.text("暂未更新 · " + this.error + (this.detail == null ? "" : "\n显示上次获取的数据"), 13, this.ui.error, false));
            this.ui.gap(warning, 12);
            warning.addView((View)this.ui.button("重新加载", false, () -> this.load(true)));
            this.body.addView((View)warning);
            this.ui.gap(this.body, 14);
        }
        if (!(this.detail == null || this.detail.warning.isBlank() || this.match.group.equals("CS2") && !this.tab.equals("overview"))) {
            this.body.addView((View)this.ui.text(this.detail.warning, 12, this.ui.muted, false));
            this.ui.gap(this.body, 12);
        }
        if (this.detail == null) {
            this.empty(this.busy ? "正在读取比赛数据" : "比赛详情暂不可用", this.busy ? "正在连接本场比赛的数据来源。" : "请检查网络并重新加载，列表仍保留上次记录。");
        } else if (this.match.group.equals("LOL")) {
            this.lol();
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
        if (this.scroll != null) {
            this.scroll.post(() -> this.scroll.scrollTo(0, preserve ? y : 0));
        }
    }

    private String statusText() {
        if (this.detail == null) {
            return this.busy ? "正在读取详情…" : "尚未获取详情";
        }
        if (this.match.group.equals("LOL")) {
            return this.detail.lolFetched > 0L ? "小局 " + DetailActivity.time(this.detail.lolFetched, "HH:mm:ss") + " 获取 · 赛程 " + DetailActivity.time(this.match.updated, "HH:mm:ss") + " 发布" : "赛程 " + DetailActivity.time(this.detail.fetched, "HH:mm:ss") + " 获取 · 小局尚未提供";
        }
        return (System.currentTimeMillis() - this.detail.fetched > 120000L ? "缓存 · " : "") + this.detail.source + " · " + DetailActivity.time(this.detail.fetched, "HH:mm:ss") + " 获取";
    }

    private LinearLayout team(boolean home) {
        String name = home ? this.match.home : this.match.away;
        String logo = home ? this.match.homeLogo : this.match.awayLogo;
        String id = home ? this.match.homeId : this.match.awayId;
        LinearLayout side = this.ui.column();
        side.setGravity(17);
        FrameLayout slot = new FrameLayout((Context)this);
        slot.addView((View)Logos.view(this.ui, logo, name, 44), (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(this.ui.dp(44.0f), this.ui.dp(44.0f), 17));
        this.ui.clickable((View)slot, 0, 28);
        boolean available = id != null && !id.isBlank();
        slot.setClickable(available);
        slot.setFocusable(available);
        slot.setContentDescription(available ? "查看 " + name + " 的赛程" : null);
        slot.setOnClickListener(available ? v -> this.openTeam(home) : null);
        side.addView((View)slot, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(56.0f), this.ui.dp(56.0f)));
        this.ui.gap(side, 4);
        TextView text = this.ui.text(name, 14, this.ui.text, true);
        text.setGravity(17);
        side.addView((View)text, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        return side;
    }

    private void openTeam(boolean home) {
        Team team = Team.of(this.match, home);
        if (team == null) {
            Sheet.message(this, "球队赛程暂不可用", "来源尚未提供这支球队的完整标识。");
            return;
        }
        this.startActivity(new Intent((Context)this, TeamActivity.class).putExtra("team", team.json().toString()));
    }

    private String lineupName() {
        return switch (this.match.group) {
            case "足球" -> "阵容";
            case "LOL" -> "比赛";
            case "F1" -> "车手";
            case "CS2" -> "选手";
            default -> "球员";
        };
    }

    private void buildTabs() {
        String[] stringArray;
        String[] stringArray2;
        LinearLayout row = this.ui.row();
        boolean f1 = this.match.group.equals("F1");
        boolean lol = this.match.group.equals("LOL");
        boolean cs = this.match.group.equals("CS2");
        if (cs && this.tab.equals("stats")) {
            this.tab = "lineup";
        }
        if (cs) {
            String[] stringArray3 = new String[3];
            stringArray3[0] = "overview";
            stringArray3[1] = "lineup";
            stringArray2 = stringArray3;
            stringArray3[2] = "events";
        } else if (f1 || lol) {
            String[] stringArray4 = new String[3];
            stringArray4[0] = "overview";
            stringArray4[1] = "lineup";
            stringArray2 = stringArray4;
            stringArray4[2] = "stats";
        } else {
            String[] stringArray5 = new String[4];
            stringArray5[0] = "overview";
            stringArray5[1] = "lineup";
            stringArray5[2] = "stats";
            stringArray2 = stringArray5;
            stringArray5[3] = "events";
        }
        this.tabKeys = stringArray2;
        if (cs) {
            String[] stringArray6 = new String[3];
            stringArray6[0] = "总览";
            stringArray6[1] = "选手";
            stringArray = stringArray6;
            stringArray6[2] = "小局";
        } else if (f1) {
            String[] stringArray7 = new String[3];
            stringArray7[0] = "总览";
            stringArray7[1] = "车手";
            stringArray = stringArray7;
            stringArray7[2] = "成绩";
        } else if (lol) {
            String[] stringArray8 = new String[3];
            stringArray8[0] = "总览";
            stringArray8[1] = "比赛";
            stringArray = stringArray8;
            stringArray8[2] = "统计";
        } else {
            String[] stringArray9 = new String[4];
            stringArray9[0] = "总览";
            stringArray9[1] = this.lineupName();
            stringArray9[2] = "统计";
            stringArray = stringArray9;
            stringArray9[3] = "进程";
        }
        this.tabNames = stringArray;
        this.tabLabels = new TextView[this.tabKeys.length];
        this.tabRules = new View[this.tabKeys.length];
        for (int i = 0; i < this.tabKeys.length; ++i) {
            String key = this.tabKeys[i];
            LinearLayout item = this.ui.column();
            item.setGravity(17);
            this.ui.clickable((View)item, this.ui.bg, 0);
            item.setFocusable(true);
            TextView label = this.ui.text(this.tabNames[i], 14, this.tab.equals(key) ? this.ui.text : this.ui.muted, this.tab.equals(key));
            label.setGravity(17);
            label.setMinHeight(this.ui.dp(48.0f));
            item.addView((View)label, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
            View underline = new View((Context)this);
            underline.setBackground((Drawable)this.ui.shape(this.tab.equals(key) ? this.ui.text : this.ui.bg, 2));
            item.addView(underline, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(24.0f), this.ui.dp(2.0f)));
            item.setOnClickListener(v -> {
                this.tab = key;
                this.categoryFilter = "";
                this.sortColumn = "";
                this.metricsX = 0;
                this.playerLimit = 16;
                this.render(false);
            });
            row.addView((View)item, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
            this.tabLabels[i] = label;
            this.tabRules[i] = underline;
        }
        this.root.addView((View)row);
        this.updateTabs();
    }

    private void updateTabs() {
        if (this.tabLabels == null) {
            return;
        }
        for (int i = 0; i < this.tabLabels.length; ++i) {
            boolean on = this.tabKeys[i].equals(this.tab);
            this.tabLabels[i].setTextColor(on ? this.ui.text : this.ui.muted);
            this.tabRules[i].setBackground((Drawable)this.ui.shape(on ? this.ui.text : this.ui.bg, 2));
            this.tabLabels[i].setSelected(on);
            View item = (View)this.tabLabels[i].getParent();
            item.setSelected(on);
            item.setContentDescription((CharSequence)(this.tabNames[i] + (on ? "，已选中" : "")));
        }
    }

    private LinearLayout panel() {
        LinearLayout card = this.ui.column();
        card.setBackground((Drawable)this.ui.panel());
        card.setPadding(this.ui.dp(16.0f), this.ui.dp(14.0f), this.ui.dp(16.0f), this.ui.dp(14.0f));
        return card;
    }

    private void heading(String text) {
        this.ui.gap(this.body, 24);
        this.body.addView((View)this.ui.text(text, 16, this.ui.text, true));
        this.ui.gap(this.body, 12);
    }

    private void overview() {
        int i;
        if (!this.match.group.equals("CS2")) {
            this.tables("overview");
        }
        if (!this.detail.stats.isEmpty()) {
            this.heading("数据速览");
            LinearLayout card = this.panel();
            this.teamNames(card);
            for (i = 0; i < Math.min(3, this.detail.stats.size()); ++i) {
                this.stat(card, this.detail.stats.get(i));
            }
            this.body.addView((View)card);
            this.ui.gap(this.body, 10);
            this.body.addView((View)this.ui.button("全部技术统计", false, () -> {
                this.tab = "stats";
                this.render(false);
            }));
        }
        if (!this.detail.events.isEmpty()) {
            this.heading("最新进程");
            LinearLayout timeline = this.panel();
            for (i = 0; i < Math.min(3, this.detail.events.size()); ++i) {
                this.event(timeline, this.detail.events.get(i));
            }
            this.body.addView((View)timeline);
        }
        this.information();
        this.sourceLink();
    }

    private void information() {
        this.heading("比赛信息");
        LinearLayout card = this.panel();
        LinkedHashMap<String, List<String>> facts = new LinkedHashMap<String, List<String>>();
        for (MatchDetail.Fact f : this.detail.facts) {
            String label = DetailParsers.label(DetailParsers.plain(f.label()));
            String value = DetailParsers.plain(f.value());
            if (value.isBlank()) continue;
            facts.computeIfAbsent(label.isBlank() ? "信息" : label, k -> new ArrayList()).add(value);
        }
        if (facts.isEmpty() && !this.match.detail.isBlank()) {
            facts.put("赛事", List.of(this.match.detail));
        }
        int index = 0;
        for (Map.Entry fact : facts.entrySet()) {
            if (index++ > 0) {
                card.addView(this.ui.divider());
            }
            LinearLayout row = this.ui.row();
            row.setGravity(48);
            row.setPadding(0, this.ui.dp(10.0f), 0, this.ui.dp(10.0f));
            TextView label = this.ui.text((String)fact.getKey(), 12, this.ui.muted, false);
            TextView value = this.ui.text(String.join((CharSequence)"\n", new LinkedHashSet((Collection)fact.getValue())), 13, this.ui.text, false);
            value.setLineSpacing((float)this.ui.dp(4.0f), 1.0f);
            row.addView((View)label, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
            row.addView((View)value, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 2.0f));
            card.addView((View)row);
        }
        if (index == 0) {
            card.addView((View)this.ui.text("来源尚未提供更多比赛信息", 13, this.ui.muted, false));
        }
        this.body.addView((View)card);
    }

    private void sourceLink() {
        this.ui.gap(this.body, 16);
        this.body.addView((View)this.ui.button("来源与更新时间", false, () -> Sheet.message(this, "数据来源", this.detail.source + "\n获取时间 " + DetailActivity.time(this.detail.fetched, "M月d日 HH:mm:ss") + (this.match.group.equals("LOL") ? "\n赛程发布时间 " + DetailActivity.time(this.match.updated, "M月d日 HH:mm:ss") + (this.detail.lolFetched > 0L ? "\n小局获取时间 " + DetailActivity.time(this.detail.lolFetched, "M月d日 HH:mm:ss") : "") : "") + "\n\n数据按来源发布，未提供的字段保持未知。")));
        if (!this.match.url.isBlank()) {
            this.ui.gap(this.body, 8);
            this.body.addView((View)this.ui.button("打开来源页面", false, () -> {
                try {
                    this.startActivity(new Intent("android.intent.action.VIEW", Uri.parse((String)this.match.url)));
                }
                catch (ActivityNotFoundException e) {
                    Sheet.message(this, "无法打开来源", "手机没有可用的浏览器。");
                }
            }));
        }
    }

    private void statistics() {
        boolean has;
        if (this.match.group.equals("F1")) {
            this.roster("stats");
            return;
        }
        if (!this.detail.stats.isEmpty()) {
            this.heading("球队对比");
            LinearLayout card = this.panel();
            this.teamNames(card);
            for (MatchDetail.Stat stat : this.detail.stats) {
                this.stat(card, stat);
            }
            this.body.addView((View)card);
        }
        if (!(has = this.tables("stats")) && this.detail.stats.isEmpty()) {
            if (this.match.group.equals("CS2")) {
                this.empty("选手数据按全场与地图分类", "在选手页比较 K/D/A、ADR、KAST 与来源 Rating。");
                this.body.addView((View)this.ui.button("查看选手数据", false, () -> {
                    this.tab = "lineup";
                    this.render(false);
                }));
            } else {
                this.empty("本场统计尚未提供", this.missing());
            }
        }
    }

    private void f1Drivers() {
        TreeMap<String, List> teams = new TreeMap<String, List>();
        for (MatchDetail.Table table : this.detail.tables) {
            if (!table.tab.equals("lineup")) continue;
            for (List<String> row2 : table.rows) {
                int team = table.columns.indexOf("车队");
                int number = table.columns.indexOf("车号");
                teams.computeIfAbsent(DetailRows.cell(row2, team), key -> new ArrayList()).add(List.of(DetailRows.cell(row2, 0), DetailRows.cell(row2, number)));
            }
        }
        if (teams.isEmpty()) {
            this.empty("参赛车手尚未提供", this.missing());
            return;
        }
        this.body.addView((View)this.ui.text("参赛车队与车手", 16, this.ui.text, true));
        this.ui.gap(this.body, 8);
        this.body.addView((View)this.ui.text("车号与本场参赛名单", 12, this.ui.muted, false));
        for (Map.Entry entry : teams.entrySet()) {
            this.heading((String)entry.getKey());
            LinearLayout card = this.panel();
            ((List)entry.getValue()).sort(Comparator.comparing(row -> (String)((List)row).get(0)));
            for (Object driverObj : (List)entry.getValue()) {
                List driver = (List)driverObj;
                LinearLayout row3 = this.ui.row();
                row3.setPadding(0, this.ui.dp(12.0f), 0, this.ui.dp(12.0f));
                TextView number = this.ui.text((String)driver.get(1), 18, this.ui.muted, true);
                number.setContentDescription((CharSequence)("车号 " + (String)driver.get(1)));
                row3.addView((View)number, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(48.0f), -2));
                row3.addView((View)this.ui.text((String)driver.get(0), 14, this.ui.text, true), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
                card.addView((View)row3);
            }
            this.body.addView((View)card);
        }
    }

    private void teamNames(LinearLayout card) {
        LinearLayout names = this.ui.row();
        TextView home = this.ui.text(this.match.home, 12, this.ui.text, true);
        TextView away = this.ui.text(this.match.away, 12, this.ui.text, true);
        away.setGravity(0x800005);
        names.addView((View)home, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        names.addView((View)away, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        card.addView((View)names);
        this.ui.gap(card, 10);
        card.addView(this.ui.divider());
    }

    private void stat(LinearLayout card, MatchDetail.Stat stat) {
        LinearLayout row = this.ui.row();
        row.setPadding(0, this.ui.dp(13.0f), 0, this.ui.dp(10.0f));
        TextView h = this.ui.text(stat.home(), 16, this.ui.primary, true);
        TextView label = this.ui.text(stat.label(), 12, this.ui.muted, false);
        TextView a = this.ui.text(stat.away(), 16, this.ui.secondary, true);
        label.setGravity(17);
        a.setGravity(0x800005);
        row.addView((View)h, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        row.addView((View)label, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 2.0f));
        row.addView((View)a, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        card.addView((View)row);
        double home = DetailActivity.numeric(stat.home());
        double away = DetailActivity.numeric(stat.away());
        if (home >= 0.0 && away >= 0.0 && home + away > 0.0) {
            LinearLayout track = this.ui.row();
            View left = new View((Context)this);
            View right = new View((Context)this);
            left.setBackground((Drawable)this.ui.shape(this.ui.primary, 2));
            right.setBackground((Drawable)this.ui.shape(this.ui.secondary, 2));
            track.addView(left, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, this.ui.dp(4.0f), (float)home));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, this.ui.dp(4.0f), (float)away);
            rp.setMarginStart(this.ui.dp(4.0f));
            track.addView(right, (ViewGroup.LayoutParams)rp);
            card.addView((View)track);
        }
        this.ui.gap(card, 12);
        card.addView(this.ui.divider());
    }

    private static double numeric(String value) {
        try {
            return value.matches("[0-9]+(?:\\.[0-9]+)?%?") ? Double.parseDouble(value.replace("%", "")) : -1.0;
        }
        catch (RuntimeException e) {
            return -1.0;
        }
    }

    private boolean tables(String key) {
        boolean found = false;
        for (MatchDetail.Table t : this.detail.tables) {
            if (!t.tab.equals(key)) continue;
            this.heading(t.title);
            this.body.addView(new DetailTableView(this.ui).simple(t), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
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
            this.heading(this.match.group.equals("足球") ? "关键事件" : (this.match.group.equals("NFL") ? "得分进程" : "比赛进程"));
            LinearLayout timeline = this.panel();
            for (int i = 0; i < Math.min(this.eventsLimit, this.detail.events.size()); ++i) {
                this.event(timeline, this.detail.events.get(i));
            }
            this.body.addView((View)timeline);
            if (this.detail.events.size() > this.eventsLimit) {
                this.ui.gap(this.body, 12);
                this.body.addView((View)this.ui.button("加载更多进程 · 剩余 " + (this.detail.events.size() - this.eventsLimit) + " 条", false, () -> {
                    this.eventsLimit += 50;
                    this.render(true);
                }));
            }
        } else if (!found) {
            this.empty("暂无比赛进程", this.missing());
        }
    }

    private void cs2Maps() {
        LinkedHashSet<String> maps = new LinkedHashSet<String>();
        for (MatchDetail.Table table : this.detail.tables) {
            if (!table.tab.equals("lineup") || table.category.isBlank() || table.category.equals("全场") || table.category.equals("伤停") || table.rows.isEmpty()) continue;
            maps.add(table.category);
        }
        if (maps.isEmpty()) {
            this.empty("本场小局数据尚未提供", this.missing());
            return;
        }
        String selected = maps.contains(this.mapFilter) ? this.mapFilter : (String)maps.iterator().next();
        HorizontalScrollView scroll = new HorizontalScrollView((Context)this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setTag((Object)"cs2-map-filter");
        LinearLayout chips = this.ui.row();
        int index = 0;
        for (String map : maps) {
            TextView chip = this.ui.chip(map, map.equals(selected), () -> {
                this.mapFilter = map;
                this.render(true);
            });
            chip.setTag((Object)"cs2-map-chip");
            chip.setContentDescription((CharSequence)(map + (map.equals(selected) ? "，已选中" : "")));
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
        TextView title = this.ui.text(selected, 15, this.ui.text, true);
        title.setTag((Object)"cs2-map-title");
        this.body.addView((View)title);
        this.ui.gap(this.body, 8);
        MatchDetail.Table halves = null;
        for (MatchDetail.Table table : this.detail.tables) {
            if (!table.tab.equals("events") || !table.title.startsWith(selected + " · ")) continue;
            halves = table;
        }
        new TeamPanelsView(this.ui, this.detail, this.body).scope(selected, this.sortColumn, this.descending, sort -> {
            this.sortColumn = sort.column();
            this.descending = sort.descending();
            this.render(true);
        }, 0, null, this.scoreCells(halves, this.match.home), this.scoreCells(halves, this.match.away));
        List<String> missing = this.missingMaps(maps);
        if (!missing.isEmpty()) {
            this.ui.gap(this.body, 16);
            TextView note = this.ui.text(String.join((CharSequence)"、", missing) + " 暂无逐图数据", 12, this.ui.muted, false);
            note.setTag((Object)"map-missing");
            this.body.addView((View)note);
        }
        if (!this.detail.events.isEmpty()) {
            this.heading("比赛进程");
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
        LinearLayout row = this.ui.row();
        row.setGravity(48);
        row.setPadding(0, this.ui.dp(12.0f), 0, this.ui.dp(12.0f));
        TextView clock = this.ui.text(event.time(), 11, this.ui.muted, true);
        row.addView((View)clock, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(56.0f), -2));
        LinearLayout words = this.ui.column();
        String title = DetailParsers.plain(event.title());
        String text = DetailParsers.plain(event.text());
        if (!title.isBlank()) {
            words.addView((View)this.ui.text(title, 13, this.ui.text, true));
            this.ui.gap(words, 4);
        }
        if (!text.isBlank()) {
            words.addView((View)this.ui.text(text, 13, title.isBlank() ? this.ui.text : this.ui.muted, false));
        }
        row.addView((View)words, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        timeline.addView((View)row);
        timeline.addView(this.ui.divider());
    }

    private void lol() {
        LolMatchView view = new LolMatchView(this.ui, this.detail, this.body, number -> {
            this.selectedGame = number;
            if (this.tab.equals("overview")) {
                this.tab = "lineup";
            }
            this.render(false);
        });
        view.teamSelection(this.teamFilter, id -> {
            this.teamFilter = id;
            this.render(false);
        });
        if (this.tab.equals("overview")) {
            view.overview();
            this.information();
            this.sourceLink();
        } else {
            view.games(this.selectedGame, this.tab.equals("stats"));
        }
    }

    private void lolPad() {
        LolMatchView view = new LolMatchView(this.ui, this.detail, this.body, number -> {
            this.selectedGame = number;
            if (this.tab.equals("overview")) {
                this.tab = "lineup";
            }
            this.render(false);
        });
        view.teamSelection(this.teamFilter, id -> {
            this.teamFilter = id;
            this.render(false);
        });
        if (this.tab.equals("overview")) {
            view.overview();
        } else {
            view.games(this.selectedGame, this.tab.equals("stats"));
        }
    }

    private String missing() {
        return this.match.scheduled() ? "赛前数据待发布" : "暂无数据";
    }

    private void empty(String title, String message) {
        this.ui.gap(this.body, 24);
        LinearLayout card = this.panel();
        card.addView((View)this.ui.text(title, 18, this.ui.text, true));
        this.ui.gap(card, 10);
        card.addView((View)this.ui.text(message, 14, this.ui.muted, false));
        this.body.addView((View)card);
    }

    private static String time(long value, String pattern) {
        return Formats.instant(value, pattern, "尚未提供");
    }
}
