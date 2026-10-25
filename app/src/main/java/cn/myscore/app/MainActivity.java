package cn.myscore.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity implements ScoreAdapter.Host {
    private Ui ui;
    private SharedPreferences prefs;
    private Repository repository;
    private Repository.Snapshot snapshot=new Repository.Snapshot();
    private Repository.Snapshot allSnapshot;
    private LocalDate allSnapshotDay;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final ExecutorService cacheWorker=Executors.newSingleThreadExecutor();
    private final ExecutorService calendarWorker=Executors.newSingleThreadExecutor();
    private LinearLayout root,body,nav,headerView;
    private FrameLayout stage;
    private ScrollView scroll;
    private ScoreList scoreList;
    private ScoreAdapter scoreAdapter;
    private int tab=0,dayOffset=0,headerHeight=0,headerOffset=0;
    private String group="全部";
    private boolean active=false,busy=false,calendarBusy=false,calendarQuerying=false,pending=false,cacheReady=false;
    private boolean pendingForce,selectionLoading;
    private long selectionVersion;
    private java.util.concurrent.Future<?> scoreRequest;
    private LocalDate loadedDay;
    private CalendarSync calendarSync;
    private final Set<String> subscribed=new HashSet<>();
    private Match pendingSubscription;
    private long lastScoreScroll;
    private String renderedScores="",renderedSettings="";
    private final Runnable tick=()->load(false);
    private final Runnable renderSoon=()->{if(active&&!isDestroyed())render(true);};
    private static final String[] GROUPS={"全部","足球","NBA","NFL","F1","CS2","LOL"};

    @Override public void onCreate(Bundle state) {
        prefs=getSharedPreferences("settings",MODE_PRIVATE);
        boolean dark=Ui.isDark(this);
        setTheme(dark?R.style.AppThemeDark:R.style.AppTheme);
        super.onCreate(state);ui=new Ui(this,dark);repository=new Repository(this);calendarSync=new CalendarSync(this);
        if(state!=null){tab=state.getInt("tab");if(state.getInt("navigation_schema",0)<2){if(tab==2)tab=1;else if(tab==1)tab=0;}dayOffset=state.getInt("offset");group=state.getString("group","全部");}
        getWindow().setStatusBarColor(ui.bg);getWindow().setNavigationBarColor(ui.bg);
        // The window paints the page colour once; the transparent root avoids a second full-screen fill.
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(ui.bg));
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        root=ui.column();setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)-> {
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(b.left,b.top,b.right,b.bottom);}
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();loadedDay=Match.today(System.currentTimeMillis());
        refreshSubscribed();busy=true;render(false);
        LocalDate initialDay=from();String initialGroup=group,initialTag=queryTag();
        cacheWorker.submit(()-> {
            Repository.Snapshot cached=repository.cached(initialDay,initialDay,initialGroup);
            handler.post(()-> {
                if(isDestroyed())return;
                cacheReady=true;
                if(initialTag.equals(queryTag())){busy=false;snapshot=cached;rememberAll(initialGroup,initialDay,cached);render(true);}load(false);
            });
            if(CalendarSync.permitted(this)) {
                ensureCalendar(false);
                handler.post(()->{if(!active||tab!=1||isDestroyed())return;renderedSettings="";render(true);});
            }
            CalendarJob.schedule(this);
        });
    }
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putInt("navigation_schema",2);b.putInt("tab",tab);b.putInt("offset",dayOffset);b.putString("group",group);}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)Ui.highRefresh(getWindow());}
    @Override public void onResume(){super.onResume();active=true;if(cacheReady)load(false);if(calendarSync.hasSubscriptions()&&System.currentTimeMillis()-prefs.getLong("calendar_checked",0)>60*60_000L)syncSubscriptions(false);}
    @Override public void onPause(){active=false;handler.removeCallbacks(tick);handler.removeCallbacks(renderSoon);super.onPause();}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);executor.shutdownNow();cacheWorker.shutdownNow();calendarWorker.shutdownNow();super.onDestroy();}
    private LocalDate from(){return Match.today(System.currentTimeMillis()).plusDays(dayOffset);}
    private LocalDate to(){return from();}
    private String queryTag(){return selectionVersion+":"+tab+":"+from()+":"+to()+":"+group;}

    private void load(boolean force) {
        if(force){Logos.retryFailures();renderedScores="";}
        handler.removeCallbacks(tick);
        if(tab==1 || !active)return;
        if(!loadedDay.equals(Match.today(System.currentTimeMillis()))){loadedDay=Match.today(System.currentTimeMillis());select(tab,group,dayOffset,true);return;}
        if(busy||selectionLoading){pending=true;pendingForce|=force;return;}
        boolean forced=force||pendingForce;pendingForce=false;
        busy=true;pending=false;String tag=queryTag();long version=selectionVersion;LocalDate f=from(),t=to();String g=group;long started=System.currentTimeMillis();final boolean[] first={true};final boolean incremental=snapshot.matches().isEmpty();
        scoreRequest=executor.submit(()-> {
            Repository.Snapshot next=repository.fetch(f,t,g,forced,batch -> handler.post(()-> {
                if(first[0]){first[0]=false;android.util.Log.i("MyScorePerf","first source "+batch.competition.id+" in "+(System.currentTimeMillis()-started)+"ms");}
                if(isDestroyed()||!active||!tag.equals(queryTag()))return;
                // With a cached list on screen, wait for the final snapshot instead of rebuilding nine times.
                if(incremental){snapshot.merge(batch);handler.removeCallbacks(renderSoon);handler.postDelayed(renderSoon,150);}
            }));
            handler.post(()-> {
                if(isDestroyed()||version!=selectionVersion)return;busy=false;
                if(!tag.equals(queryTag())){if(active)load(false);return;}
                android.util.Log.i("MyScorePerf","all sources in "+(System.currentTimeMillis()-started)+"ms");
                handler.removeCallbacks(renderSoon);
                if(tag.equals(queryTag())){snapshot=next;rememberAll(g,f,next);if(active)render(true);}
                if(active){if(pending || !tag.equals(queryTag()))load(false);else handler.postDelayed(tick,RefreshPolicy.remaining(RefreshPolicy.SCORES,System.currentTimeMillis()-started));}
            });
        });
    }
    private void select(int nextTab,String nextGroup,int offset) {
        select(nextTab,nextGroup,offset,false);
    }
    private void select(int nextTab,String nextGroup,int offset,boolean force) {
        if(!force&&tab==nextTab&&group.equals(nextGroup)&&dayOffset==offset)return;
        handler.removeCallbacks(tick);handler.removeCallbacks(renderSoon);
        selectionVersion++;if(scoreRequest!=null)scoreRequest.cancel(true);busy=false;pending=false;pendingForce=false;
        boolean inPlace=nextTab==0&&tab==0&&scoreList!=null;
        tab=nextTab;group=nextGroup;dayOffset=offset;headerOffset=0;lastScoreScroll=0;
        if(tab==1){selectionLoading=false;render(false);return;}
        selectionLoading=true;
        LocalDate day=from();String selectedGroup=group;String tag=queryTag();
        if(allSnapshotDay!=null&&!allSnapshotDay.equals(day)){allSnapshot=null;allSnapshotDay=null;}
        Repository.Snapshot local=null;
        if(allSnapshot!=null&&day.equals(allSnapshotDay)) {
            if("全部".equals(selectedGroup))local=allSnapshot;
            else {
                local=subset(allSnapshot,selectedGroup);
            }
        }
        snapshot=local!=null?local:new Repository.Snapshot();
        renderedScores="";
        if(inPlace)rebuildHeader();else render(false);
        if(inPlace)updateScoreRows();
        if(scoreList!=null){scoreList.setSelection(0);lastScoreScroll=0;}
        final boolean keepLocal=local!=null;
        cacheWorker.submit(()-> {
            Repository.Snapshot cached=repository.cached(day,day,selectedGroup);
            handler.post(()-> {
                if(isDestroyed()||!tag.equals(queryTag()))return;
                selectionLoading=false;
                if(keepLocal){for(Repository.Batch batch:snapshot.batches){Repository.Batch disk=cached.batches.stream().filter(b->b.competition.id.equals(batch.competition.id)).findFirst().orElse(null);if(disk==null||disk.fetched<=batch.fetched)cached.merge(batch);}}
                snapshot=cached;rememberAll(selectedGroup,day,cached);
                load(false);
                if(!tag.equals(queryTag()))return;
                renderedScores="";updateScoreRows();
                if(BuildConfig.DEBUG)android.util.Log.i("MyScorePerf","selection cache group="+selectedGroup+" items="+cached.matches().size());
            });
        });
    }
    private static Repository.Snapshot subset(Repository.Snapshot source,String group){
        Repository.Snapshot result=new Repository.Snapshot();
        for(Repository.Batch batch:source.batches)if("全部".equals(group)||batch.competition.group.equals(group))result.batches.add(batch);
        return result;
    }
    private void rememberAll(String selectedGroup,LocalDate day,Repository.Snapshot value){
        if("全部".equals(selectedGroup)){allSnapshot=value;allSnapshotDay=day;}
        else if(allSnapshot!=null&&day.equals(allSnapshotDay)){Repository.Snapshot merged=subset(allSnapshot,"全部");for(Repository.Batch batch:value.batches)merged.merge(batch);allSnapshot=merged;}
    }
    /** Rebuilds only the app bar layer; the score list keeps its instance, adapter and rows. */
    private void rebuildHeader() {
        if(headerView==null||isFinishing()||isDestroyed())return;
        headerView.removeAllViews();
        header(headerView);filters(headerView);dateSelector(headerView);headerView.addView(ui.divider());
        headerHeight=measureHeader();
        if(scoreList!=null)scoreList.setPadding(ui.dp(16),headerHeight,ui.dp(16),ui.dp(24));
        headerView.setTranslationY(0);headerView.post(this::syncHeaderHeight);
    }

    /** Preserved updates only touch the data layer; the header, list and scroll offset stay put. */
    private void render(boolean preserve) {
        if(isFinishing()||isDestroyed())return;
        if(preserve&&tab==0&&scoreList!=null) {
            if(SystemClock.uptimeMillis()-lastScoreScroll<250){handler.removeCallbacks(renderSoon);handler.postDelayed(renderSoon,250);return;}
            updateScoreRows();return;
        }
        if(preserve&&tab==1&&body!=null) {
            String key=settingsKey();
            if(key.equals(renderedSettings))return;
            renderedSettings=key;body.removeAllViews();settings();return;
        }
        buildScreen();
    }

    private void buildScreen() {
        if(isFinishing()||isDestroyed())return;
        boolean scores=tab==0;
        root.removeAllViews();
        stage=new FrameLayout(this);root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        if(scores) {
            scoreList=new ScoreList(this);
            scoreList.setPadding(ui.dp(16),ui.dp(154),ui.dp(16),ui.dp(24));
            scoreList.setOnScrollPixels(this::onScoreScroll);
            scoreAdapter=new ScoreAdapter(ui,this);
            scoreAdapter.attach(scoreList);
            scoreList.setAdapter(scoreAdapter);
            stage.addView(scoreList,new FrameLayout.LayoutParams(-1,-1));
        } else {
            scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
            scroll.setVerticalScrollBarEnabled(false);scroll.setPadding(0,ui.dp(50),0,0);
            body=ui.column();body.setPadding(ui.dp(16),ui.dp(4),ui.dp(16),ui.dp(24));scroll.addView(body);
            stage.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
            scroll.setOnScrollChangeListener((v,x,y,ox,oy)-> {
                if(headerHeight<=0)return;
                int delta=y-oy;
                if(delta>0)headerOffset=Math.min(headerHeight,headerOffset+delta);
                else if(delta<0)headerOffset=0;
                else return;
                headerView.setTranslationY(-headerOffset);
            });
        }
        headerView=ui.column();headerView.setBackgroundColor(ui.bg);
        header(headerView);
        if(scores){filters(headerView);dateSelector(headerView);}
        headerView.addView(ui.divider());
        stage.addView(headerView,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        headerHeight=measureHeader();
        headerOffset=0;headerView.setTranslationY(0);
        if(scores) {
            scoreList.setPadding(ui.dp(16),headerHeight,ui.dp(16),ui.dp(24));
            renderedScores="";updateScoreRows();
        } else {
            scroll.setPadding(0,headerHeight,0,0);
            renderedSettings="";settings();renderedSettings=settingsKey();
        }
        root.addView(ui.divider());navigation();
        headerView.post(this::syncHeaderHeight);
    }
    private int measureHeader() {
        int width=root.getWidth()-root.getPaddingLeft()-root.getPaddingRight();
        if(width<=0)width=getResources().getDisplayMetrics().widthPixels;
        headerView.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        return Math.max(0,headerView.getMeasuredHeight());
    }
    private void syncHeaderHeight() {
        if(headerView==null||isFinishing()||isDestroyed())return;
        int height=headerView.getHeight();
        if(height<=0)return;
        headerHeight=height;
        if(tab==0&&scoreList!=null)scoreList.setPadding(ui.dp(16),height,ui.dp(16),ui.dp(24));
        else if(scroll!=null)scroll.setPadding(0,height,0,0);
        headerView.setTranslationY(-Math.min(headerOffset,height));
    }
    private void onScoreScroll(int delta) {
        lastScoreScroll=SystemClock.uptimeMillis();
        if(headerView==null||headerHeight<=0)return;
        if(delta>0) {
            int next=Math.min(headerHeight,headerOffset+delta);
            if(next!=headerOffset){headerOffset=next;headerView.setTranslationY(-headerOffset);}
        } else if(delta<0&&headerOffset!=0) {
            headerOffset=0;headerView.setTranslationY(0);
        }
    }
    private void updateScoreRows() {
        if(scoreList==null||scoreAdapter==null)return;
        List<Match> all=snapshot.matches();
        String signature=scoreSignature(all);
        if(signature.equals(renderedScores))return;
        renderedScores=signature;
        long started=SystemClock.uptimeMillis();
        List<ScoreRow> rows=buildScoreRows(all);
        boolean relaid=scoreAdapter.updateRows(rows);
        if(relaid)scoreList.resetScrollTracking();
        if(BuildConfig.DEBUG)android.util.Log.i("MyScorePerf","score rows rebound="+rows.size()+" matches="+all.size()+" relaid="+relaid+" in "+(SystemClock.uptimeMillis()-started)+"ms");
    }
    private String scoreSignature(List<Match> all) {
        long now=System.currentTimeMillis();
        StringBuilder key=new StringBuilder(256);
        key.append(group).append('|').append(tab).append('|').append((busy||selectionLoading)&&all.isEmpty()).append('|').append(snapshot.failed());
        for(Repository.Batch batch:snapshot.batches)key.append('|').append(batch.competition.id).append(':').append(batch.error);
        for(Match m:all) {
            key.append('|').append(m.id).append(',').append(m.league).append(',').append(m.group).append(',')
                .append(m.home).append(',').append(m.away).append(',').append(m.homeScore).append(',').append(m.awayScore).append(',')
                .append(m.state).append(',').append(m.status).append(',').append(m.start).append(',').append(m.timeConfirmed).append(',')
                .append(m.title).append(',').append(m.homeLogo).append(',').append(m.awayLogo).append(',')
                .append(subscribed.contains(m.id)).append(',').append(m.live()&&now-m.updated>120_000).append(',').append(m.source).append(',').append(m.updated/60_000);
            for(String result:m.results)key.append(',').append(result);
        }
        return key.toString();
    }
    private List<ScoreRow> buildScoreRows(List<Match> all) {
        List<ScoreRow> rows=new ArrayList<>();
        if(all.isEmpty()) {
            // Never show "no matches" while a selection/source request is still running.
            if(busy||selectionLoading){rows.add(ScoreRow.loading("正在读取赛程…"));return rows;}
            if(snapshot.failed()) {
                int failures=0;for(Repository.Batch batch:snapshot.batches)if(!batch.error.isEmpty())failures++;
                rows.add(ScoreRow.failure(failures+" 个赛事来源未更新 · 查看连接状态"));
            }
            rows.add(snapshot.failed()?ScoreRow.empty("暂时无法获取比赛","请检查网络后重新获取；连接失败不代表没有比赛。"):ScoreRow.empty("这个日期没有已确认的比赛","可以切换日期或赛事。"));
            return rows;
        }
        if(snapshot.failed()) {
            int failures=0;for(Repository.Batch batch:snapshot.batches)if(!batch.error.isEmpty())failures++;
            rows.add(ScoreRow.failure(failures+" 个赛事来源未更新 · 查看连接状态"));
        }
        String[] stages={"live","scheduled","delayed","unknown","final","cancelled"};
        for(String stage:stages) {
            List<Match> stageMatches=new ArrayList<>();for(Match m:all)if(m.state.equals(stage))stageMatches.add(m);
            if(stageMatches.isEmpty())continue;
            rows.add(ScoreRow.section(Rules.stateLabel(stage)+" · "+stageMatches.size()));
            for(Match m:stageMatches)rows.add(ScoreRow.match(m));
        }
        return rows;
    }
    private String settingsKey() {
        StringBuilder key=new StringBuilder(128);
        key.append(tab).append('|').append(calendarBusy).append('|').append(calendarName()).append('|')
            .append(calendarSync.linkedEvents()).append('|').append(prefs.getLong("calendar_checked",0)).append('|').append(snapshot.failed());
        for(Repository.Batch batch:snapshot.batches)key.append('|').append(batch.competition.id).append(':').append(batch.error);
        return key.toString();
    }

    private void header(LinearLayout parent) {
        LinearLayout header=ui.row();header.setPadding(ui.dp(20),ui.dp(18),ui.dp(20),ui.dp(18));
        LinearLayout titles=ui.column();
        String title=tab==0?"MyScore":"设置";
        TextView heading=ui.text(title,28,ui.text,true);heading.setLetterSpacing(-0.025f);titles.addView(heading);
        ui.gap(titles,7);titles.addView(ui.text(tab==0?"赛程、比分与每一个关键时刻":"让关注与提醒，按你的节奏",12,ui.muted,false));
        header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        if(tab==0){FrameLayout refresh=new FrameLayout(this);Ui.Icon icon=new Ui.Icon(this,"refresh",ui.primary);FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(ui.dp(20),ui.dp(20),Gravity.CENTER);refresh.addView(icon,ip);ui.clickable(refresh,ui.tonal,Ui.CONTROL_RADIUS);refresh.setContentDescription("刷新比赛");refresh.setFocusable(true);refresh.setOnClickListener(v->load(true));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48));rp.setMarginStart(ui.dp(12));header.addView(refresh,rp);}
        parent.addView(header);
    }
    private void filters(LinearLayout parent) {
        LinearLayout holder=ui.row();
        HorizontalScrollView horizontal=new HorizontalScrollView(this);horizontal.setHorizontalScrollBarEnabled(false);
        LinearLayout chips=ui.row();chips.setPadding(ui.dp(20),0,ui.dp(12),0);
        for(String g:GROUPS){TextView chip=ui.chip(g,g.equals(group),()->select(tab,g,dayOffset));chip.setMinWidth(ui.dp(56));chip.setContentDescription(g+(g.equals(group)?"，已选中":"，筛选赛事"));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.setMarginEnd(ui.dp(8));chips.addView(chip,cp);}
        horizontal.addView(chips);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.setMarginEnd(ui.dp(8));holder.addView(horizontal,hp);
        FrameLayout more=new FrameLayout(this);more.addView(new Ui.Icon(this,"chevron",ui.muted),new FrameLayout.LayoutParams(ui.dp(18),ui.dp(18),Gravity.CENTER));
        ui.clickable(more,ui.bg,Ui.CONTROL_RADIUS);more.setContentDescription("更多赛事筛选");more.setFocusable(true);more.setOnClickListener(v->horizontal.smoothScrollBy(ui.dp(180),0));
        holder.addView(more,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
        int selectedIndex=0;for(int i=0;i<GROUPS.length;i++)if(GROUPS[i].equals(group))selectedIndex=i;
        final int current=selectedIndex;horizontal.post(()->{View selected=chips.getChildAt(current);if(selected!=null&&selected.getRight()>horizontal.getWidth())horizontal.scrollTo(selected.getRight()-horizontal.getWidth()+ui.dp(8),0);});
        final boolean[] overflow={false};Runnable refresh=()->{View child=horizontal.getChildAt(0);int max=child==null?0:Math.max(0,child.getWidth()-horizontal.getWidth());overflow[0]=max>0;more.setVisibility(overflow[0]&&horizontal.getScrollX()<max-1?View.VISIBLE:View.INVISIBLE);};
        horizontal.getViewTreeObserver().addOnGlobalLayoutListener(refresh::run);horizontal.setOnScrollChangeListener((v,x,y,ox,oy)->refresh.run());
        parent.addView(holder);
    }
    private void dateSelector(LinearLayout parent) {
        LocalDate today=Match.today(System.currentTimeMillis()),selected=from();
        LinearLayout bar=ui.row();bar.setPadding(ui.dp(20),ui.dp(12),ui.dp(20),ui.dp(12));
        FrameLayout prev=new FrameLayout(this);prev.addView(new Ui.Icon(this,"back",ui.primary),new FrameLayout.LayoutParams(ui.dp(20),ui.dp(20),Gravity.CENTER));
        ui.clickable(prev,ui.surface,Ui.CONTROL_RADIUS);prev.setFocusable(true);prev.setContentDescription("前一天");prev.setOnClickListener(v->selectDay(selected.minusDays(1)));
        bar.addView(prev,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
        TextView label=ui.text((selected.equals(today)?"今天 · ":"")+(selected.getYear()==today.getYear()?DateTimeFormatter.ofPattern("M月d日 EEE",Locale.CHINA).format(selected):DateTimeFormatter.ofPattern("yyyy年M月d日",Locale.CHINA).format(selected)),14,ui.text,true);
        label.setGravity(Gravity.CENTER);label.setMinHeight(ui.dp(48));
        ui.clickable(label,ui.surface,Ui.CONTROL_RADIUS);label.setFocusable(true);label.setContentDescription("选择日期，当前"+DateTimeFormatter.ofPattern("M月d日 EEEE",Locale.CHINA).format(selected));
        label.setOnClickListener(v->Sheet.date(this,from(),this::selectDay));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMarginStart(ui.dp(8));lp.setMarginEnd(ui.dp(8));bar.addView(label,lp);
        FrameLayout next=new FrameLayout(this);next.addView(new Ui.Icon(this,"chevron",ui.primary),new FrameLayout.LayoutParams(ui.dp(20),ui.dp(20),Gravity.CENTER));
        ui.clickable(next,ui.surface,Ui.CONTROL_RADIUS);next.setFocusable(true);next.setContentDescription("后一天");next.setOnClickListener(v->selectDay(selected.plusDays(1)));
        bar.addView(next,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
        if(!selected.equals(today)){TextView reset=ui.text("今天",12,ui.primary,true);reset.setGravity(Gravity.CENTER);reset.setMinHeight(ui.dp(48));reset.setPadding(ui.dp(8),0,ui.dp(4),0);reset.setFocusable(true);reset.setContentDescription("回到今天");reset.setOnClickListener(v->selectDay(today));bar.addView(reset,new LinearLayout.LayoutParams(-2,ui.dp(48)));}
        parent.addView(bar);
    }
    private void selectDay(LocalDate day) {
        LocalDate today=Match.today(System.currentTimeMillis());
        int offset=(int)java.time.temporal.ChronoUnit.DAYS.between(today,day);
        select(0,group,offset);
    }
    private void matchDetail(Match m) {startActivity(new Intent(this,DetailActivity.class).putExtra("match",m.json().toString()));}
    @Override public void openMatch(Match match) {matchDetail(match);}
    @Override public void retryScores() {load(true);}
    @Override public void showSourceStatus() {sourceStatus();}
    @Override public boolean isSubscribed(String id) {return subscribed.contains(id);}

    private void settings() {
        section("单场订阅");
        LinearLayout calendar=ui.column();calendar.setBackground(ui.panel());
        settingRow(calendar,"接收日历",calendarName(),this::calendarChoice);
        settingRow(calendar,"已订阅的比赛",calendarSync.linkedEvents()+" 场 · 提前 5 分钟提醒",this::subscribedList);
        long checked=prefs.getLong("calendar_checked",0);
        settingRow(calendar,calendarBusy?"正在检查…":"检查已订阅比赛",checked>0?"上次检查 "+formatInstant(checked,"M月d日 HH:mm"):"同步改期与取消",()->syncSubscriptions(true));
        settingRow(calendar,"打开系统日历","查看日程与提醒",this::openCalendar);
        body.addView(calendar);
        section("偏好与帮助");LinearLayout options=ui.column();options.setBackground(ui.panel());
        settingRow(options,"外观",themeName(),this::themeChoice);
        settingRow(options,"检查更新","当前版本 "+BuildConfig.VERSION_NAME,()->startActivity(new Intent(this,UpdateActivity.class)));
        settingRow(options,"数据来源与连接",snapshot.failed()?"部分来源未更新":"查看来源状态",this::sourceStatus);
        settingRow(options,"使用帮助","日历提醒与数据说明",this::help);body.addView(options);
        ui.gap(body,24);TextView hint=ui.text("MyScore · "+BuildConfig.VERSION_NAME+"\n在比赛卡上点日历图标，订阅单场比赛。",12,ui.muted,false);hint.setLineSpacing(ui.dp(6),1);hint.setGravity(Gravity.CENTER);hint.setPadding(ui.dp(4),0,ui.dp(4),0);body.addView(hint);
    }
    private void section(String title){ui.gap(body,24);TextView h=ui.text(title,14,ui.muted,true);h.setPadding(ui.dp(4),0,0,ui.dp(12));body.addView(h);}
    private void settingRow(LinearLayout into,String title,String detail,Runnable action){
        if(into.getChildCount()>0){View line=ui.divider();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,ui.dp(1));lp.setMarginStart(ui.dp(16));lp.setMarginEnd(ui.dp(16));into.addView(line,lp);}
        LinearLayout row=ui.row();row.setMinimumHeight(ui.dp(76));row.setPadding(ui.dp(16),ui.dp(16),ui.dp(14),ui.dp(16));ui.clickable(row,Color.TRANSPARENT,Ui.CARD_RADIUS);row.setFocusable(true);
        String kind=title.equals("外观")?"theme":title.equals("检查更新")?"download":title.contains("来源")||title.contains("帮助")?"info":title.contains("检查")||title.contains("正在")?"refresh":"calendar";
        FrameLayout symbol=new FrameLayout(this);symbol.setBackground(ui.shape(ui.tonal,Ui.CONTROL_RADIUS));symbol.addView(new Ui.Icon(this,kind,ui.primary),new FrameLayout.LayoutParams(ui.dp(20),ui.dp(20),Gravity.CENTER));LinearLayout.LayoutParams symbolParams=new LinearLayout.LayoutParams(ui.dp(40),ui.dp(40));symbolParams.setMarginEnd(ui.dp(14));row.addView(symbol,symbolParams);
        LinearLayout text=ui.column();text.addView(ui.text(title,15,ui.text,true));if(!detail.isBlank()){ui.gap(text,5);text.addView(ui.text(detail,12,ui.muted,false));}row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(ui.dp(18),ui.dp(18));ip.setMarginStart(ui.dp(12));row.addView(new Ui.Icon(this,"chevron",ui.muted),ip);row.setOnClickListener(v->action.run());into.addView(row);
    }
    private void openCalendar(){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("content://com.android.calendar/time/"+System.currentTimeMillis())));}catch(RuntimeException e){message("没有找到日历应用","请先安装或启用系统日历。");}}
    private void help(){message("使用帮助","在比赛卡上点日历加号订阅，点勾选可取消。提醒由系统日历发送；小米 / 澎湃 OS 需要允许日历通知与后台运行。\n\n更换接收日历只影响新订阅，已有事件保留在原日历。\n\n体育由 ESPN 提供；CS2 仅 Spirit 主队，来自 5EPlay；LOL 仅 Worlds，来自英雄联盟赛事官网。前台列表约 10 秒检查，进行中详情约 10 秒（F1 约 15 秒）；失败时延长重试间隔。来源本身可能延迟；连接失败保留真实缓存，缺失数据不补零。");}
    private String calendarName(){String name=calendarSync.calendarName();return name==null||name.isBlank()?"选择手机日历":name;}
    private void calendarChoice(){chooseCalendar(null);}
    /** Calendar provider queries stay off the main thread; the sheet is built after they finish. */
    private void chooseCalendar(Runnable after) {
        if(!CalendarSync.permitted(this)){requestPermissions(new String[]{Manifest.permission.READ_CALENDAR,Manifest.permission.WRITE_CALENDAR},42);return;}
        if(calendarQuerying)return;
        calendarQuerying=true;
        calendarWorker.submit(()-> {
            List<CalendarSync.CalendarChoice> choices;
            try{choices=calendarSync.calendars();}catch(RuntimeException e){choices=Collections.emptyList();}
            List<CalendarSync.CalendarChoice> result=choices;
            handler.post(()-> {
                calendarQuerying=false;
                if(isDestroyed()||isFinishing())return;
                if(result.isEmpty()){Sheet.message(this,"没有可写入的日历","请先在系统日历中启用一个本地日历或可写账户，再回来选择。");return;}
                List<Sheet.Item> items=new ArrayList<>();int selected=-1;
                for(int i=0;i<result.size();i++){CalendarSync.CalendarChoice choice=result.get(i);if(choice.id==calendarSync.calendarId())selected=i;items.add(new Sheet.Item(choice.name,choice.local?"本机日历":"可写账户",choice.id==calendarSync.calendarId()));}
                Sheet.choose(this,"选择接收比赛的日历","订阅的比赛会写入这个日历，并提前 5 分钟提醒。",items,selected,index->{
                    CalendarSync.CalendarChoice choice=result.get(index);
                    calendarSync.chooseCalendar(choice.id,choice.name);CalendarJob.schedule(this);render(true);
                    if(after!=null)after.run();
                });
            });
        });
    }
    @Override public void toggleSubscription(Match m) {
        if(calendarBusy){Toast.makeText(this,"正在处理日历…",Toast.LENGTH_SHORT).show();return;}
        if(subscribed.contains(m.id)) {
            Sheet.confirm(this,"取消订阅","从手机日历中移除「"+m.displayTitle()+"」及其提醒？","取消订阅",()->unsubscribeAsync(m));
            return;
        }
        if(!(m.scheduled()&&m.timeConfirmed&&m.start>System.currentTimeMillis())){Sheet.message(this,"暂时不能订阅","这场比赛的开赛时间未确定、已开始或已结束。");return;}
        pendingSubscription=m;
        if(!CalendarSync.permitted(this)){requestPermissions(new String[]{Manifest.permission.READ_CALENDAR,Manifest.permission.WRITE_CALENDAR},42);return;}
        subscribePending();
    }
    /** Uses the best local/Xiaomi calendar silently; only the settings button opens the chooser. */
    private boolean ensureCalendar(boolean complain) {
        long id=calendarSync.calendarId();
        if(id>=0&&calendarSync.hasCalendar(id))return true;
        CalendarSync.CalendarChoice best=calendarSync.bestCalendar();
        if(best==null){if(complain)Sheet.message(this,"没有可写入的日历","请先在系统日历中启用一个本地或小米日历。");return false;}
        calendarSync.chooseCalendar(best.id,best.name);
        return true;
    }
    private void subscribePending() {
        Match m=pendingSubscription;if(m==null)return;pendingSubscription=null;
        calendarBusy=true;
        calendarWorker.submit(()-> {
            CalendarSync.Result result;
            try {
                result=ensureCalendar(false)?calendarSync.subscribe(m,System.currentTimeMillis()):noCalendarResult();
            } catch(RuntimeException e) {
                result=new CalendarSync.Result();result.failed=1;result.error="日历写入失败";
            }
            CalendarSync.Result finalResult=result;
            handler.post(()-> {
                calendarBusy=false;if(isDestroyed())return;
                refreshSubscribed();render(true);CalendarJob.schedule(this);
                if(finalResult.inserted+finalResult.updated>0)Toast.makeText(this,"已加入日历 · 提前 5 分钟提醒",Toast.LENGTH_SHORT).show();
                else Sheet.message(this,"未能加入日历",finalResult.error.isEmpty()?"请检查日历权限和目标日历。":finalResult.error);
            });
        });
    }
    private static CalendarSync.Result noCalendarResult(){CalendarSync.Result result=new CalendarSync.Result();result.failed=1;result.error="请先在系统日历中启用一个本地或小米日历。";return result;}
    private void unsubscribeAsync(Match m) {
        calendarBusy=true;
        calendarWorker.submit(()-> {
            CalendarSync.Result result;
            try{result=calendarSync.unsubscribe(m.id);}catch(RuntimeException e){result=new CalendarSync.Result();result.failed=1;result.error="日历读取失败";}
            CalendarSync.Result finalResult=result;
            handler.post(()-> {
                calendarBusy=false;if(isDestroyed())return;
                refreshSubscribed();render(true);CalendarJob.schedule(this);
                if(finalResult.failed>0)Sheet.message(this,"未能取消订阅",finalResult.error.isEmpty()?"请检查日历权限后重试。":finalResult.error);
                else Toast.makeText(this,"已取消订阅",Toast.LENGTH_SHORT).show();
            });
        });
    }
    private void subscribedList() {
        List<Match> list=calendarSync.subscriptions();
        if(list.isEmpty()){Sheet.message(this,"还没有订阅","在比赛卡上点“订阅”，把单场比赛加入手机日历。");return;}
        List<Sheet.Item> items=new ArrayList<>();
        for(Match m:list)items.add(new Sheet.Item(m.displayTitle(),formatInstant(m.start,"M月d日 HH:mm")+" · "+(Competition.find(m.league)==null?m.league:Competition.find(m.league).name),false));
        Sheet.choose(this,"已订阅的比赛","点选一场可取消订阅。提醒由系统日历发送。",items,-1,index->{
            Match m=list.get(index);
            Sheet.confirm(this,"取消订阅","移除「"+m.displayTitle()+"」的日历事件和提醒？","取消订阅",()->unsubscribeAsync(m));
        });
    }
    private void syncSubscriptions(boolean feedback) {
        if(calendarBusy)return;
        if(!calendarSync.hasSubscriptions()){if(feedback)Sheet.message(this,"还没有订阅","在比赛卡上点“订阅”，把单场比赛加入手机日历。");return;}
        calendarBusy=true;if(feedback&&tab==1)render(true);
        executor.submit(()->{
            String text;
            try{CalendarSync.Result result=calendarSync.sync(Collections.emptyList(),System.currentTimeMillis());text=result.message();}
            catch(RuntimeException e){text="检查失败，请检查网络和日历权限后重试。";}
            String finalText=text;
            handler.post(()->{calendarBusy=false;if(isDestroyed())return;if(active){refreshSubscribed();render(true);if(feedback)Sheet.message(this,"订阅检查结果",finalText+"\n请在系统日历确认提醒已显示。");}});
        });
    }
    private void refreshSubscribed(){subscribed.clear();subscribed.addAll(calendarSync.subscribedIds());}
    private String themeName(){return switch(prefs.getString("theme","dark")){case "dark"->"深色";case "light"->"浅色";default->"跟随系统";};}
    private void themeChoice(){
        String[] values={"dark","light","system"};String[] labels={"深色","浅色","跟随系统"};String current=prefs.getString("theme","dark");int selected=0;
        for(int i=0;i<values.length;i++)if(values[i].equals(current))selected=i;
        List<Sheet.Item> items=new ArrayList<>();for(int i=0;i<labels.length;i++)items.add(new Sheet.Item(labels[i],i==selected?"当前使用":"",i==selected));
        Sheet.choose(this,"外观","跟随系统会随手机的浅色 / 深色模式切换。",items,selected,index->{prefs.edit().putString("theme",values[index]).apply();recreate();});
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code!=42)return;
        if(!CalendarSync.permitted(this)){pendingSubscription=null;Sheet.message(this,"未开启日历权限","比分查看不受影响。需要订阅时，请在系统应用权限中允许日历读写。");return;}
        if(pendingSubscription!=null)subscribePending();
        else chooseCalendar(null);
    }
    private void sourceStatus() {
        StringBuilder content=new StringBuilder();
        for(Repository.Batch b:snapshot.batches){content.append(b.competition.name).append("\n").append(b.fetched>0?"上次成功 "+formatInstant(b.fetched,"M月d日 HH:mm:ss")+" · "+b.matches.size()+" 场":"尚未成功获取");if(!b.error.isEmpty())content.append("\n").append(b.error);content.append("\n\n");}
        content.append("来源：ESPN、5EPlay、英雄联盟赛事官网。数据可能延迟；连接失败时保留最近一次成功记录。");
        Sheet.message(this,"数据来源与连接",content.toString());
    }
    private void navigation() {
        nav=ui.row();nav.setBackgroundColor(ui.bg);nav.setPadding(ui.dp(20),ui.dp(8),ui.dp(20),ui.dp(8));String[] labels={"比赛","设置"},icons={"scores","settings"};
        for(int i=0;i<2;i++){int index=i;boolean selected=tab==i;LinearLayout item=ui.row();item.setGravity(Gravity.CENTER);item.setMinimumHeight(ui.dp(52));ui.clickable(item,selected?ui.tonal:ui.bg,Ui.CARD_RADIUS);item.setFocusable(true);item.setSelected(selected);item.setContentDescription(labels[i]+(selected?"，已选中":""));item.addView(new Ui.Icon(this,icons[i],selected?ui.primary:ui.muted),new LinearLayout.LayoutParams(ui.dp(22),ui.dp(22)));TextView label=ui.text(labels[i],13,selected?ui.primary:ui.muted,selected);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.setMarginStart(ui.dp(10));item.addView(label,lp);item.setOnClickListener(v->select(index,group,dayOffset));LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-2,1);if(i>0)np.setMarginStart(ui.dp(12));nav.addView(item,np);}root.addView(nav);
    }
    private void message(String title,String text){if(!isFinishing()&&!isDestroyed())Sheet.message(this,title,text);}
    private static String formatInstant(long millis,String pattern){return millis>0?DateTimeFormatter.ofPattern(pattern,Locale.CHINA).withZone(Match.ZONE).format(Instant.ofEpochMilli(millis)):"未获取";}
}
