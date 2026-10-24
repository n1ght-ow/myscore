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

public final class MainActivity extends Activity {
    private Ui ui;
    private SharedPreferences prefs;
    private Repository repository;
    private Repository.Snapshot snapshot=new Repository.Snapshot();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final ExecutorService cacheWorker=Executors.newSingleThreadExecutor();
    private LinearLayout root,body,nav,headerView;
    private FrameLayout stage;
    private ScrollView scroll;
    private TextView loadingText;
    private int tab=0,dayOffset=0,headerHeight=0,headerOffset=0;
    private String group="全部";
    private boolean active=false,busy=false,calendarBusy=false,pending=false,cacheReady=false;
    private LocalDate loadedDay;
    private CalendarSync calendarSync;
    private final Set<String> subscribed=new HashSet<>();
    private Match pendingSubscription;
    private long lastScroll;
    private String renderedContent="";
    private final java.util.Map<String,TextView> timestamps=new java.util.HashMap<>();
    private final java.util.Map<String,Match> visibleMatches=new java.util.HashMap<>();
    private final Runnable tick=()->load(false);
    private final Runnable renderSoon=()->{if(active&&!isDestroyed())render(true);};
    private static final String[] GROUPS={"全部","足球","NBA","NFL","F1","CS2","LOL"};

    @Override public void onCreate(Bundle state) {
        prefs=getSharedPreferences("settings",MODE_PRIVATE);
        boolean dark=switch(prefs.getString("theme","system")) {case "dark"->true;case "light"->false;default->(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;};
        setTheme(dark?R.style.AppThemeDark:R.style.AppTheme);
        super.onCreate(state);ui=new Ui(this,dark);repository=new Repository(this);calendarSync=new CalendarSync(this);
        if(state!=null){tab=state.getInt("tab");if(state.getInt("navigation_schema",0)<2){if(tab==2)tab=1;else if(tab==1)tab=0;}dayOffset=state.getInt("offset");group=state.getString("group","全部");}
        getWindow().setStatusBarColor(ui.bg);getWindow().setNavigationBarColor(ui.surface);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        root=ui.column();root.setBackgroundColor(ui.bg);setContentView(root);
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
                cacheReady=true;busy=false;
                if(initialTag.equals(queryTag())){snapshot=cached;render(true);}load(false);
            });
            if(CalendarSync.permitted(this))ensureCalendar(false);
            CalendarJob.schedule(this);
        });
    }
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putInt("navigation_schema",2);b.putInt("tab",tab);b.putInt("offset",dayOffset);b.putString("group",group);}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)Ui.highRefresh(getWindow());}
    @Override public void onResume(){super.onResume();active=true;if(cacheReady)load(false);if(calendarSync.hasSubscriptions()&&System.currentTimeMillis()-prefs.getLong("calendar_checked",0)>60*60_000L)syncSubscriptions(false);}
    @Override public void onPause(){active=false;handler.removeCallbacks(tick);handler.removeCallbacks(renderSoon);super.onPause();}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);executor.shutdownNow();cacheWorker.shutdownNow();super.onDestroy();}
    private LocalDate from(){return Match.today(System.currentTimeMillis()).plusDays(dayOffset);}
    private LocalDate to(){return from();}
    private String queryTag(){return tab+":"+from()+":"+to()+":"+group;}
    private void load(boolean force) {
        if(force){Logos.retryFailures();renderedContent="";}
        handler.removeCallbacks(tick);
        if(tab==1 || !active)return;
        if(!loadedDay.equals(Match.today(System.currentTimeMillis()))){loadedDay=Match.today(System.currentTimeMillis());select(tab,group,dayOffset,true);return;}
        if(busy){pending=true;return;}
        busy=true;pending=false;String tag=queryTag();LocalDate f=from(),t=to();String g=group;long started=System.currentTimeMillis();final boolean[] first={true};final boolean stable=!snapshot.matches().isEmpty();
        if(loadingText!=null)loadingText.setText("正在更新比赛…");
        executor.submit(()-> {
            Repository.Snapshot next=repository.fetch(f,t,g,force,batch -> handler.post(()-> {
                if(first[0]){first[0]=false;android.util.Log.i("MyScorePerf","first source "+batch.competition.id+" in "+(System.currentTimeMillis()-started)+"ms");}
                if(isDestroyed()||!active||!tag.equals(queryTag()))return;
                snapshot.merge(batch);
                if(!stable){handler.removeCallbacks(renderSoon);handler.postDelayed(renderSoon,150);}
            }));
            handler.post(()-> {
                busy=false;if(isDestroyed())return;
                android.util.Log.i("MyScorePerf","all sources in "+(System.currentTimeMillis()-started)+"ms");
                handler.removeCallbacks(renderSoon);
                if(tag.equals(queryTag())){snapshot=next;if(active)render(true);}
                if(active){if(pending || !tag.equals(queryTag()))load(false);else handler.postDelayed(tick,next.failed()?60_000:15_000);}
            });
        });
    }
    private void select(int nextTab,String nextGroup,int offset) {
        select(nextTab,nextGroup,offset,false);
    }
    private void select(int nextTab,String nextGroup,int offset,boolean force) {
        if(!force&&tab==nextTab&&group.equals(nextGroup)&&dayOffset==offset)return;
        boolean sameQuery=group.equals(nextGroup)&&dayOffset==offset;
        handler.removeCallbacks(tick);handler.removeCallbacks(renderSoon);tab=nextTab;group=nextGroup;dayOffset=offset;headerOffset=0;
        if(tab==1||sameQuery&&!force){render(false);load(false);return;}
        snapshot=new Repository.Snapshot();render(false);String tag=queryTag();LocalDate day=from();String selectedGroup=group;
        cacheWorker.submit(()->{Repository.Snapshot cached=repository.cached(day,day,selectedGroup);handler.post(()->{if(isDestroyed()||!tag.equals(queryTag()))return;snapshot=cached;render(true);load(false);});});
    }
    private void render(boolean preserveScroll) {
        if(isFinishing()||isDestroyed())return;
        if(preserveScroll&&tab==0&&android.os.SystemClock.uptimeMillis()-lastScroll<200){handler.removeCallbacks(renderSoon);handler.postDelayed(renderSoon,200);return;}
        if(preserveScroll&&body!=null){
            String key=contentKey();
            if(tab==0&&key.equals(renderedContent)){
                for(Match m:snapshot.matches()){visibleMatches.put(m.id,m);TextView foot=timestamps.get(m.id);if(foot!=null)foot.setText(footnote(m));}
                return;
            }
            renderedContent=key;timestamps.clear();visibleMatches.clear();body.removeAllViews();
            if(tab==1)settings();else matches();return;
        }
        renderedContent=contentKey();timestamps.clear();visibleMatches.clear();
        int oldY=preserveScroll&&scroll!=null?scroll.getScrollY():0;
        android.util.Log.i("MyScorePerf","render tab="+tab+" y="+oldY+" cached="+!snapshot.matches().isEmpty());
        root.removeAllViews();
        stage=new FrameLayout(this);root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setPadding(0,ui.dp(tab==1?50:154),0,0);
        body=ui.column();body.setPadding(ui.dp(16),ui.dp(4),ui.dp(16),ui.dp(24));scroll.addView(body);
        stage.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        headerView=ui.column();headerView.setBackgroundColor(ui.bg);
        header(headerView);
        if(tab==0){filters(headerView);dateSelector(headerView);}
        headerView.addView(ui.divider());
        stage.addView(headerView,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        headerView.setTranslationY(-Math.min(headerOffset,headerHeight));
        if(tab==1)settings();else matches();
        root.addView(ui.divider());navigation();
        scroll.post(()-> {
            headerHeight=headerView.getHeight();scroll.setPadding(0,headerHeight,0,0);
            headerOffset=Math.min(headerOffset,headerHeight);headerView.setTranslationY(-headerOffset);
            if(preserveScroll)scroll.scrollTo(0,oldY);
            headerOffset=Math.min(headerOffset,scroll.getScrollY());headerView.setTranslationY(-headerOffset);
            scroll.setOnScrollChangeListener((v,x,y,ox,oy)-> {
                if(headerHeight<=0)return;
                int delta=y-oy;
                lastScroll=android.os.SystemClock.uptimeMillis();
                if(delta>0)headerOffset=Math.min(headerHeight,headerOffset+delta);
                else if(delta<0)headerOffset=0;
                headerView.setTranslationY(-headerOffset);
            });
        });
    }
    private String contentKey(){
        StringBuilder key=new StringBuilder(queryTag()).append(':').append(busy&&snapshot.matches().isEmpty());
        for(Competition c:Competition.ALL)for(Repository.Batch b:snapshot.batches)if(c.id.equals(b.competition.id))key.append('|').append(b.competition.id).append(':').append(b.error);
        for(Match m:snapshot.matches()){
            org.json.JSONObject value=m.json();value.remove("updated");
            key.append('|').append(value).append(':').append(m.live()&&System.currentTimeMillis()-m.updated>120_000).append(':').append(subscribed.contains(m.id)).append(':').append(m.scheduled()&&m.timeConfirmed&&m.start>System.currentTimeMillis());
        }
        return key.toString();
    }
    private void header(LinearLayout parent) {
        LinearLayout header=ui.row();header.setPadding(ui.dp(20),ui.dp(12),ui.dp(12),ui.dp(6));
        LinearLayout titles=ui.row();titles.setGravity(Gravity.CENTER_VERTICAL);
        String title=tab==0?"我的比赛":"设置";
        titles.addView(ui.text(title,22,ui.text,true));
        TextView meta=ui.text(tab==1?"MyScore · "+BuildConfig.VERSION_NAME:"北京时间",11,ui.muted,false);
        meta.setMaxLines(1);meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,-2,1);mp.setMarginStart(ui.dp(10));titles.addView(meta,mp);
        header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        if(tab==0){FrameLayout refresh=new FrameLayout(this);Ui.Icon icon=new Ui.Icon(this,"refresh",ui.primary);FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(ui.dp(22),ui.dp(22),Gravity.CENTER);refresh.addView(icon,ip);ui.clickable(refresh,Color.TRANSPARENT,24);refresh.setContentDescription("刷新比赛");refresh.setFocusable(true);refresh.setOnClickListener(v->load(true));header.addView(refresh,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));}
        parent.addView(header);
    }
    private void filters(LinearLayout parent) {
        LinearLayout holder=ui.row();
        HorizontalScrollView horizontal=new HorizontalScrollView(this);horizontal.setHorizontalScrollBarEnabled(false);
        LinearLayout chips=ui.row();chips.setPadding(ui.dp(20),0,ui.dp(12),0);
        for(String g:GROUPS){TextView chip=ui.text(g,14,g.equals(group)?ui.onPrimary:ui.muted,g.equals(group));chip.setGravity(Gravity.CENTER);chip.setMinWidth(ui.dp(56));chip.setMinHeight(ui.dp(48));chip.setPadding(ui.dp(14),0,ui.dp(14),0);ui.clickable(chip,g.equals(group)?ui.primary:ui.bg,24);chip.setSelected(g.equals(group));chip.setFocusable(true);chip.setContentDescription(g+(g.equals(group)?"，已选中":"，筛选赛事"));chip.setOnClickListener(v->select(tab,g,dayOffset));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.setMarginEnd(ui.dp(8));chips.addView(chip,cp);}
        horizontal.addView(chips);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.setMarginEnd(ui.dp(8));holder.addView(horizontal,hp);
        FrameLayout more=new FrameLayout(this);more.addView(new Ui.Icon(this,"chevron",ui.muted),new FrameLayout.LayoutParams(ui.dp(18),ui.dp(18),Gravity.CENTER));
        more.setContentDescription("更多赛事筛选");more.setFocusable(true);more.setOnClickListener(v->horizontal.smoothScrollBy(ui.dp(180),0));
        holder.addView(more,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
        int selectedIndex=0;for(int i=0;i<GROUPS.length;i++)if(GROUPS[i].equals(group))selectedIndex=i;
        final int current=selectedIndex;horizontal.post(()->{View selected=chips.getChildAt(current);if(selected!=null&&selected.getRight()>horizontal.getWidth())horizontal.scrollTo(selected.getRight()-horizontal.getWidth()+ui.dp(8),0);});
        final boolean[] overflow={false};Runnable refresh=()->{View child=horizontal.getChildAt(0);int max=child==null?0:Math.max(0,child.getWidth()-horizontal.getWidth());overflow[0]=max>0;more.setVisibility(overflow[0]&&horizontal.getScrollX()<max-1?View.VISIBLE:View.INVISIBLE);};
        horizontal.getViewTreeObserver().addOnGlobalLayoutListener(refresh::run);horizontal.setOnScrollChangeListener((v,x,y,ox,oy)->refresh.run());
        parent.addView(holder);
    }
    private void dateSelector(LinearLayout parent) {
        LocalDate today=Match.today(System.currentTimeMillis()),selected=from();
        LinearLayout bar=ui.row();bar.setPadding(ui.dp(20),ui.dp(4),ui.dp(20),ui.dp(4));
        FrameLayout prev=new FrameLayout(this);prev.addView(new Ui.Icon(this,"back",ui.primary),new FrameLayout.LayoutParams(ui.dp(20),ui.dp(20),Gravity.CENTER));
        ui.clickable(prev,Color.TRANSPARENT,24);prev.setFocusable(true);prev.setContentDescription("前一天");prev.setOnClickListener(v->selectDay(selected.minusDays(1)));
        bar.addView(prev,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
        TextView label=ui.text(selected.getYear()==today.getYear()?DateTimeFormatter.ofPattern("M月d日 EEE",Locale.CHINA).format(selected):DateTimeFormatter.ofPattern("yyyy年M月d日",Locale.CHINA).format(selected),15,ui.primary,true);
        label.setGravity(Gravity.CENTER);label.setMinHeight(ui.dp(48));label.setMaxLines(1);label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        ui.clickable(label,ui.tonal,14);label.setFocusable(true);label.setContentDescription("选择日期，当前"+DateTimeFormatter.ofPattern("M月d日 EEEE",Locale.CHINA).format(selected));
        label.setOnClickListener(v->Sheet.date(this,from(),this::selectDay));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,ui.dp(48),1);lp.setMarginStart(ui.dp(6));lp.setMarginEnd(ui.dp(6));bar.addView(label,lp);
        FrameLayout next=new FrameLayout(this);next.addView(new Ui.Icon(this,"chevron",ui.primary),new FrameLayout.LayoutParams(ui.dp(20),ui.dp(20),Gravity.CENTER));
        ui.clickable(next,Color.TRANSPARENT,24);next.setFocusable(true);next.setContentDescription("后一天");next.setOnClickListener(v->selectDay(selected.plusDays(1)));
        bar.addView(next,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
        if(!selected.equals(today)){TextView reset=ui.text("今天",12,ui.primary,true);reset.setGravity(Gravity.CENTER);reset.setMinHeight(ui.dp(48));reset.setPadding(ui.dp(8),0,ui.dp(4),0);reset.setFocusable(true);reset.setContentDescription("回到今天");reset.setOnClickListener(v->selectDay(today));bar.addView(reset,new LinearLayout.LayoutParams(-2,ui.dp(48)));}
        parent.addView(bar);
    }
    private void selectDay(LocalDate day) {
        LocalDate today=Match.today(System.currentTimeMillis());
        int offset=(int)java.time.temporal.ChronoUnit.DAYS.between(today,day);
        select(0,group,offset);
    }
    private void matches() {
        loadingText=null;String note=busy&&snapshot.matches().isEmpty()?"正在更新比赛…":null;
        if(note!=null){loadingText=ui.text(note,11,ui.muted,false);loadingText.setPadding(ui.dp(4),ui.dp(9),0,0);body.addView(loadingText);ui.gap(body,6);}
        if(snapshot.failed()) {
            int failures=0;for(Repository.Batch b:snapshot.batches)if(!b.error.isEmpty())failures++;
            TextView banner=ui.text(failures+" 个赛事来源未更新 · 查看连接状态",14,ui.error,true);banner.setPadding(ui.dp(14),ui.dp(14),ui.dp(14),ui.dp(14));ui.clickable(banner,ui.surface,12);banner.setFocusable(true);banner.setOnClickListener(v->sourceStatus());body.addView(banner);ui.gap(body,16);
        }
        List<Match> all=snapshot.matches();
        if(all.isEmpty()) {
            ui.gap(body,32);body.addView(ui.text(busy?"正在读取赛程":"这个日期没有已确认的比赛",20,ui.text,true));ui.gap(body,10);
            body.addView(ui.text(busy?"首次加载需要连接赛事来源。":"可以切换日期或赛事。连接失败的来源不代表没有比赛。",14,ui.muted,false));
            ui.gap(body,20);body.addView(ui.button("重新获取",false,()->load(true)));return;
        }
        String[] stages={"live","scheduled","delayed","unknown","final","cancelled"};
        for(String stage:stages) {
            List<Match> stageMatches=new ArrayList<>();for(Match m:all)if(m.state.equals(stage))stageMatches.add(m);
            if(stageMatches.isEmpty())continue;
            section(Rules.stateLabel(stage)+" · "+stageMatches.size());
            for(Match m:stageMatches)matchRow(m,true);
        }
    }
    private void section(String title){ui.gap(body,18);TextView h=ui.text(title,14,ui.text,true);h.setPadding(ui.dp(4),0,0,ui.dp(10));body.addView(h);}
    private void matchRow(Match m,boolean showLeague) {
        LinearLayout card=ui.column();card.setPadding(ui.dp(12),ui.dp(10),ui.dp(12),ui.dp(10));
        ui.clickable(card,ui.surface,12);card.setFocusable(true);card.setOnClickListener(v->matchDetail(m));
        Competition comp=Competition.find(m.league);long age=System.currentTimeMillis()-m.updated;boolean stale=age>120_000;
        LinearLayout meta=ui.row();TextView league=ui.text(comp==null?m.group:comp.name,12,ui.muted,true);meta.addView(league,new LinearLayout.LayoutParams(0,-2,1));
        String simpleState=Rules.stateLabel(m.state);
        if(m.live()||!m.status.equals(simpleState)){TextView state=ui.text(m.status,11,ui.text,true);state.setGravity(Gravity.END);meta.addView(state,new LinearLayout.LayoutParams(-2,-2));}card.addView(meta);ui.gap(card,9);
        if(m.group.equals("F1")) {
            card.addView(ui.text(m.displayTitle(),17,ui.text,true));
            for(int i=0;i<Math.min(m.results.size(),2);i++){ui.gap(card,5);card.addView(ui.text(m.results.get(i),12,ui.muted,false));}
        } else {
            teamRow(card,m.home,m.homeLogo,m.hasScore()?m.homeScore:"—");ui.gap(card,5);teamRow(card,m.away,m.awayLogo,m.hasScore()?m.awayScore:"—");
        }
        ui.gap(card,8);
        LinearLayout footRow=ui.row();
        TextView foot=ui.text(footnote(m),11,ui.muted,false);timestamps.put(m.id,foot);visibleMatches.put(m.id,m);footRow.addView(foot,new LinearLayout.LayoutParams(0,-2,1));
        boolean isSubscribed=subscribed.contains(m.id);boolean canSubscribe=m.scheduled()&&m.timeConfirmed&&m.start>System.currentTimeMillis();
        FrameLayout sub=new FrameLayout(this);
        Ui.Icon subIcon=new Ui.Icon(this,isSubscribed?"calendar-check":"calendar-add",isSubscribed?ui.onPrimary:(canSubscribe?ui.primary:ui.muted));
        sub.addView(subIcon,new FrameLayout.LayoutParams(ui.dp(22),ui.dp(22),Gravity.CENTER));
        ui.clickable(sub,isSubscribed?ui.primary:(canSubscribe?ui.tonal:Color.TRANSPARENT),24);
        sub.setFocusable(true);
        sub.setContentDescription(isSubscribed?"取消订阅 "+m.displayTitle()+"，提前 5 分钟提醒":canSubscribe?"订阅 "+m.displayTitle()+"，提前 5 分钟提醒":m.displayTitle()+" 已开始或已结束，暂不可订阅");
        sub.setOnClickListener(v->toggleSubscription(m));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48));pp.setMarginStart(ui.dp(8));footRow.addView(sub,pp);
        card.addView(footRow);
        if(stale&&m.live()){ui.gap(card,6);card.addView(ui.text("上次记录为进行中，当前状态待刷新",12,ui.error,false));}
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=ui.dp(6);body.addView(card,cp);
    }
    private String footnote(Match m){return (m.timeConfirmed?m.time():"时间待定")+" · "+m.source+"\n"+(System.currentTimeMillis()-m.updated>120_000?"缓存 · ":"")+(m.updated>0?formatInstant(m.updated,"M月d日 HH:mm:ss")+" 更新":"尚未获取");}
    private void teamRow(LinearLayout into,String name,String logo,String score) {
        LinearLayout row=ui.row();row.addView(Logos.view(ui,logo,name,25));TextView team=ui.text(name,15,ui.text,true);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMarginStart(ui.dp(10));row.addView(team,tp);
        TextView number=ui.text(score,20,ui.text,true);number.setGravity(Gravity.END);number.setMinWidth(ui.dp(32));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2);sp.setMarginStart(ui.dp(12));row.addView(number,sp);into.addView(row);
    }
    private void matchDetail(Match m) {
        m=visibleMatches.getOrDefault(m.id,m);
        startActivity(new Intent(this,DetailActivity.class).putExtra("match",m.json().toString()));
    }
    private void settings() {
        loadingText=null;
        section("单场订阅");
        LinearLayout calendar=ui.column();calendar.setBackground(ui.shape(ui.surface,14));
        settingRow(calendar,"接收日历",calendarName(),this::calendarChoice);
        settingRow(calendar,"已订阅的比赛",calendarSync.linkedEvents()+" 场 · 提前 5 分钟提醒",this::subscribedList);
        long checked=prefs.getLong("calendar_checked",0);
        settingRow(calendar,calendarBusy?"正在检查…":"检查已订阅比赛",checked>0?"上次检查 "+formatInstant(checked,"M月d日 HH:mm"):"同步改期与取消",()->syncSubscriptions(true));
        settingRow(calendar,"打开系统日历","查看日程与提醒",this::openCalendar);
        body.addView(calendar);
        section("偏好与帮助");LinearLayout options=ui.column();options.setBackground(ui.shape(ui.surface,14));
        settingRow(options,"外观",themeName(),this::themeChoice);
        settingRow(options,"数据来源与连接",snapshot.failed()?"部分来源未更新":"查看来源状态",this::sourceStatus);
        settingRow(options,"使用帮助","日历提醒与数据说明",this::help);body.addView(options);
        ui.gap(body,18);TextView hint=ui.text("在比赛卡上点日历图标，即可订阅单场比赛。",12,ui.muted,false);hint.setPadding(ui.dp(4),0,ui.dp(4),0);body.addView(hint);
    }
    private void settingRow(LinearLayout into,String title,String detail,Runnable action){
        if(into.getChildCount()>0){View line=ui.divider();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,ui.dp(1));lp.setMarginStart(ui.dp(16));lp.setMarginEnd(ui.dp(16));into.addView(line,lp);}
        LinearLayout row=ui.row();row.setMinimumHeight(ui.dp(68));row.setPadding(ui.dp(16),ui.dp(14),ui.dp(14),ui.dp(14));ui.clickable(row,Color.TRANSPARENT,12);row.setFocusable(true);
        LinearLayout text=ui.column();text.addView(ui.text(title,15,ui.text,true));if(!detail.isBlank()){ui.gap(text,5);text.addView(ui.text(detail,12,ui.muted,false));}row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(ui.dp(18),ui.dp(18));ip.setMarginStart(ui.dp(12));row.addView(new Ui.Icon(this,"chevron",ui.muted),ip);row.setOnClickListener(v->action.run());into.addView(row);
    }
    private void openCalendar(){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("content://com.android.calendar/time/"+System.currentTimeMillis())));}catch(android.content.ActivityNotFoundException e){message("没有找到日历应用","请先安装或启用系统日历。");}}
    private void help(){message("使用帮助","在比赛卡上点日历加号订阅，点勾选可取消。提醒由系统日历发送；小米 / 澎湃 OS 需要允许日历通知与后台运行。\n\n更换接收日历只影响新订阅，已有事件保留在原日历。\n\n体育由 ESPN 提供；CS2 仅 Spirit 主队，来自 5EPlay；LOL 仅 Worlds，来自英雄联盟赛事官网。体育约 15 秒刷新，电竞至少间隔 60 秒；连接失败时保留真实缓存，缺失数据不补零。");}
    private String calendarName(){String name=calendarSync.calendarName();return name==null||name.isBlank()?"选择手机日历":name;}
    private void calendarChoice(){chooseCalendar(null);}
    private void chooseCalendar(Runnable after) {
        if(!CalendarSync.permitted(this)){requestPermissions(new String[]{Manifest.permission.READ_CALENDAR,Manifest.permission.WRITE_CALENDAR},42);return;}
        List<CalendarSync.CalendarChoice> choices=calendarSync.calendars();
        if(choices.isEmpty()){Sheet.message(this,"没有可写入的日历","请先在系统日历中启用一个本地日历或可写账户，再回来选择。");return;}
        List<Sheet.Item> items=new ArrayList<>();int selected=-1;
        for(int i=0;i<choices.size();i++){CalendarSync.CalendarChoice choice=choices.get(i);if(choice.id==calendarSync.calendarId())selected=i;items.add(new Sheet.Item(choice.name,choice.local?"本机日历":"可写账户",choice.id==calendarSync.calendarId()));}
        Sheet.choose(this,"选择接收比赛的日历","订阅的比赛会写入这个日历，并提前 5 分钟提醒。",items,selected,index->{
            CalendarSync.CalendarChoice choice=choices.get(index);
            calendarSync.chooseCalendar(choice.id,choice.name);CalendarJob.schedule(this);render(true);
            if(after!=null)after.run();
        });
    }
    private void toggleSubscription(Match m) {
        if(subscribed.contains(m.id)) {
            Sheet.confirm(this,"取消订阅","从手机日历中移除「"+m.displayTitle()+"」及其提醒？","取消订阅",()->{
                CalendarSync.Result result=calendarSync.unsubscribe(m.id);
                refreshSubscribed();render(true);CalendarJob.schedule(this);
                if(result.failed>0)Sheet.message(this,"未能取消订阅",result.error.isEmpty()?"请检查日历权限后重试。":result.error);
                else Toast.makeText(this,"已取消订阅",Toast.LENGTH_SHORT).show();
            });
            return;
        }
        if(!(m.scheduled()&&m.timeConfirmed&&m.start>System.currentTimeMillis())){Sheet.message(this,"暂时不能订阅","这场比赛的开赛时间未确定、已开始或已结束。");return;}
        pendingSubscription=m;
        if(!CalendarSync.permitted(this)){requestPermissions(new String[]{Manifest.permission.READ_CALENDAR,Manifest.permission.WRITE_CALENDAR},42);return;}
        if(!ensureCalendar(true)){pendingSubscription=null;return;}
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
        CalendarSync.Result result=calendarSync.subscribe(m,System.currentTimeMillis());
        refreshSubscribed();render(true);CalendarJob.schedule(this);
        if(result.inserted+result.updated>0)Toast.makeText(this,"已加入日历 · 提前 5 分钟提醒",Toast.LENGTH_SHORT).show();
        else Sheet.message(this,"未能加入日历",result.error.isEmpty()?"请检查日历权限和目标日历。":result.error);
    }
    private void subscribedList() {
        List<Match> list=calendarSync.subscriptions();
        if(list.isEmpty()){Sheet.message(this,"还没有订阅","在比赛卡上点“订阅”，把单场比赛加入手机日历。");return;}
        List<Sheet.Item> items=new ArrayList<>();
        for(Match m:list)items.add(new Sheet.Item(m.displayTitle(),formatInstant(m.start,"M月d日 HH:mm")+" · "+(Competition.find(m.league)==null?m.league:Competition.find(m.league).name),false));
        Sheet.choose(this,"已订阅的比赛","点选一场可取消订阅。提醒由系统日历发送。",items,-1,index->{
            Match m=list.get(index);
            Sheet.confirm(this,"取消订阅","移除「"+m.displayTitle()+"」的日历事件和提醒？","取消订阅",()->{
                calendarSync.unsubscribe(m.id);refreshSubscribed();render(true);CalendarJob.schedule(this);Toast.makeText(this,"已取消订阅",Toast.LENGTH_SHORT).show();
            });
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
    private String themeName(){return switch(prefs.getString("theme","system")){case "dark"->"深色";case "light"->"浅色";default->"跟随系统";};}
    private void themeChoice(){
        String[] values={"system","light","dark"};String[] labels={"跟随系统","浅色","深色"};String current=prefs.getString("theme","system");int selected=0;
        for(int i=0;i<values.length;i++)if(values[i].equals(current))selected=i;
        List<Sheet.Item> items=new ArrayList<>();for(int i=0;i<labels.length;i++)items.add(new Sheet.Item(labels[i],i==selected?"当前使用":"",i==selected));
        Sheet.choose(this,"外观","跟随系统会随手机的浅色 / 深色模式切换。",items,selected,index->{prefs.edit().putString("theme",values[index]).apply();recreate();});
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code!=42)return;
        if(!CalendarSync.permitted(this)){pendingSubscription=null;Sheet.message(this,"未开启日历权限","比分查看不受影响。需要订阅时，请在系统应用权限中允许日历读写。");return;}
        if(pendingSubscription!=null){if(ensureCalendar(true))subscribePending();else pendingSubscription=null;}
        else chooseCalendar(null);
    }
    private void sourceStatus() {
        StringBuilder content=new StringBuilder();
        for(Repository.Batch b:snapshot.batches){content.append(b.competition.name).append("\n").append(b.fetched>0?"上次成功 "+formatInstant(b.fetched,"M月d日 HH:mm:ss")+" · "+b.matches.size()+" 场":"尚未成功获取");if(!b.error.isEmpty())content.append("\n").append(b.error);content.append("\n\n");}
        content.append("来源：ESPN、5EPlay、英雄联盟赛事官网。数据可能延迟；连接失败时保留最近一次成功记录。");
        Sheet.message(this,"数据来源与连接",content.toString());
    }
    private void navigation() {
        nav=ui.row();nav.setBackgroundColor(ui.surface);String[] labels={"比赛","设置"},icons={"scores","settings"};
        for(int i=0;i<2;i++){int index=i;LinearLayout item=ui.column();item.setGravity(Gravity.CENTER);item.setPadding(0,ui.dp(10),0,ui.dp(10));ui.clickable(item,tab==i?ui.tonal:ui.surface,0);item.setFocusable(true);item.setSelected(tab==i);item.setContentDescription(labels[i]+(tab==i?"，已选中":""));item.addView(new Ui.Icon(this,icons[i],tab==i?ui.primary:ui.muted),new LinearLayout.LayoutParams(ui.dp(24),ui.dp(24)));ui.gap(item,5);TextView label=ui.text(labels[i],12,tab==i?ui.primary:ui.muted,tab==i);label.setGravity(Gravity.CENTER);item.addView(label);item.setOnClickListener(v->select(index,group,dayOffset));nav.addView(item,new LinearLayout.LayoutParams(0,-2,1));}root.addView(nav);
    }
    private void message(String title,String text){if(!isFinishing()&&!isDestroyed())Sheet.message(this,title,text);}
    private void openLink(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(android.content.ActivityNotFoundException e){message("无法打开链接","手机没有可用的浏览器。");}}
    private static String formatInstant(long millis,String pattern){return millis>0?DateTimeFormatter.ofPattern(pattern,Locale.CHINA).withZone(Match.ZONE).format(Instant.ofEpochMilli(millis)):"未获取";}
}
