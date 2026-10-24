package cn.myscore.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private Ui ui;
    private SharedPreferences prefs;
    private Repository repository;
    private Repository.Snapshot snapshot=new Repository.Snapshot();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private LinearLayout root,body,nav;
    private ScrollView scroll;
    private TextView loadingText;
    private int tab=0,dayOffset=0;
    private String group="全部";
    private boolean active=false,busy=false,calendarBusy=false,pending=false,pendingCalendarPermission=false;
    private LocalDate loadedDay;
    private final Runnable tick=()->load(false);
    private static final String[] GROUPS={"全部","足球","NBA","NFL","F1","CS2","LOL"};

    @Override public void onCreate(Bundle state) {
        prefs=getSharedPreferences("settings",MODE_PRIVATE);
        boolean dark=switch(prefs.getString("theme","system")) {case "dark"->true;case "light"->false;default->(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;};
        setTheme(dark?R.style.AppThemeDark:R.style.AppTheme);
        super.onCreate(state);ui=new Ui(this,dark);repository=new Repository(this);
        if(state!=null){tab=state.getInt("tab");dayOffset=state.getInt("offset");group=state.getString("group","全部");}
        getWindow().setStatusBarColor(ui.bg);getWindow().setNavigationBarColor(ui.surface);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        root=ui.column();root.setBackgroundColor(ui.bg);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)-> {
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(b.left,b.top,b.right,b.bottom);}
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();loadedDay=Match.today(System.currentTimeMillis());
        snapshot=repository.cached(from(),to(),group);render(false);CalendarJob.schedule(this);
    }
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putInt("tab",tab);b.putInt("offset",dayOffset);b.putString("group",group);}
    @Override public void onResume(){super.onResume();active=true;load(false);if(prefs.getBoolean("calendar_enabled",false)&&System.currentTimeMillis()-prefs.getLong("calendar_checked",0)>60*60_000L)syncCalendar(false);}
    @Override public void onPause(){active=false;handler.removeCallbacks(tick);super.onPause();}
    @Override public void onDestroy(){executor.shutdownNow();super.onDestroy();}
    private LocalDate from(){return Match.today(System.currentTimeMillis()).plusDays(tab==1?0:dayOffset);}
    private LocalDate to(){return tab==1?from().plusDays(7):from();}
    private String queryTag(){return tab+":"+from()+":"+to()+":"+group;}
    private void load(boolean force) {
        if(force)Logos.retryFailures();
        handler.removeCallbacks(tick);
        if(tab==2 || !active)return;
        if(!loadedDay.equals(Match.today(System.currentTimeMillis()))){loadedDay=Match.today(System.currentTimeMillis());snapshot=repository.cached(from(),to(),group);render(false);}
        if(busy){pending=true;return;}
        busy=true;pending=false;String tag=queryTag();LocalDate f=from(),t=to();String g=group;
        if(loadingText!=null)loadingText.setText("正在更新比赛…");
        executor.submit(()-> {
            Repository.Snapshot next=repository.fetch(f,t,g,force);
            handler.post(()-> {
                busy=false;if(isDestroyed())return;
                if(tag.equals(queryTag())){snapshot=next;if(active)render(true);}
                if(active){if(pending || !tag.equals(queryTag()))load(false);else handler.postDelayed(tick,tab==1?300_000:next.failed()?60_000:15_000);}
            });
        });
    }
    private void select(int nextTab,String nextGroup,int offset) {
        handler.removeCallbacks(tick);tab=nextTab;group=nextGroup;dayOffset=offset;
        snapshot=repository.cached(from(),to(),group);render(false);load(false);
    }
    private void render(boolean preserveScroll) {
        int oldY=preserveScroll&&scroll!=null?scroll.getScrollY():0;
        root.removeAllViews();header();
        if(tab!=2){filters();if(tab==0)dateSelector();}
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        body=ui.column();body.setPadding(ui.dp(16),ui.dp(4),ui.dp(16),ui.dp(24));scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(tab==2)settings();else matches();
        root.addView(ui.divider());navigation();
        if(preserveScroll)scroll.post(()->scroll.scrollTo(0,oldY));
    }
    private void header() {
        LinearLayout header=ui.row();header.setPadding(ui.dp(20),ui.dp(18),ui.dp(12),ui.dp(12));
        LinearLayout titles=ui.column();String title=tab==0?"我的比赛":tab==1?"未来赛程":"设置";
        titles.addView(ui.text(title,24,ui.text,true));ui.gap(titles,6);
        titles.addView(ui.text(tab==2?"MyScore · 0.2.0":DateTimeFormatter.ofPattern("M月d日 EEEE",Locale.CHINA).format(from())+" · 北京时间",12,ui.muted,false));
        header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        if(tab!=2){FrameLayout refresh=new FrameLayout(this);Ui.Icon icon=new Ui.Icon(this,"refresh",ui.primary);FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(ui.dp(23),ui.dp(23),Gravity.CENTER);refresh.addView(icon,ip);ui.clickable(refresh,Color.TRANSPARENT,24);refresh.setContentDescription("刷新比赛");refresh.setFocusable(true);refresh.setOnClickListener(v->load(true));header.addView(refresh,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));}
        root.addView(header);
    }
    private void filters() {
        FrameLayout holder=new FrameLayout(this);
        HorizontalScrollView horizontal=new HorizontalScrollView(this);horizontal.setHorizontalScrollBarEnabled(false);
        LinearLayout chips=ui.row();chips.setPadding(ui.dp(20),0,ui.dp(48),0);
        for(String g:GROUPS){TextView chip=ui.text(g,14,g.equals(group)?ui.onPrimary:ui.muted,g.equals(group));chip.setGravity(Gravity.CENTER);chip.setMinWidth(ui.dp(56));chip.setMinHeight(ui.dp(48));chip.setPadding(ui.dp(14),0,ui.dp(14),0);ui.clickable(chip,g.equals(group)?ui.primary:ui.bg,24);chip.setSelected(g.equals(group));chip.setFocusable(true);chip.setContentDescription(g+(g.equals(group)?"，已选中":"，筛选赛事"));chip.setOnClickListener(v->select(tab,g,dayOffset));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.setMarginEnd(ui.dp(8));chips.addView(chip,cp);}
        horizontal.addView(chips);holder.addView(horizontal,new FrameLayout.LayoutParams(-1,-2));
        FrameLayout more=new FrameLayout(this);Ui.Icon chevron=new Ui.Icon(this,"chevron",ui.muted);more.addView(chevron,new FrameLayout.LayoutParams(ui.dp(16),ui.dp(16),Gravity.CENTER));
        more.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,new int[]{ui.bg&0x00FFFFFF,ui.bg}));
        more.setContentDescription("更多赛事筛选");more.setFocusable(true);more.setOnClickListener(v->horizontal.smoothScrollBy(ui.dp(180),0));
        holder.addView(more,new FrameLayout.LayoutParams(ui.dp(48),ui.dp(48),Gravity.END|Gravity.CENTER_VERTICAL));
        final boolean[] overflow={false};Runnable refresh=()->{View child=horizontal.getChildAt(0);int max=child==null?0:Math.max(0,child.getWidth()-horizontal.getWidth());overflow[0]=max>0;more.setVisibility(overflow[0]&&horizontal.getScrollX()<max-1?View.VISIBLE:View.GONE);};
        horizontal.getViewTreeObserver().addOnGlobalLayoutListener(refresh::run);horizontal.setOnScrollChangeListener((v,x,y,ox,oy)->refresh.run());
        root.addView(holder);
    }
    private void dateSelector() {
        LinearLayout dates=ui.row();dates.setPadding(ui.dp(20),ui.dp(8),ui.dp(20),0);
        for(int i=-1;i<=1;i++){int offset=i;TextView b=ui.text(i==-1?"昨天":i==0?"今天":"明天",14,dayOffset==i?ui.primary:ui.muted,dayOffset==i);b.setGravity(Gravity.CENTER);b.setMinHeight(ui.dp(48));ui.clickable(b,dayOffset==i?ui.tonal:ui.bg,12);b.setSelected(dayOffset==i);b.setFocusable(true);b.setOnClickListener(v->select(0,group,offset));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-2,1);if(i<1)bp.setMarginEnd(ui.dp(8));dates.addView(b,bp);}
        root.addView(dates);
    }
    private void matches() {
        if(tab==1){calendarSummary();ui.gap(body,16);}
        loadingText=null;String note=tab==1?(busy?"正在更新比赛…":"未来 7 天 · 赛程可能改期"):busy?"正在更新比赛…":null;
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
        if(tab==1) {
            LocalDate current=null;
            for(Match m:all) {
                if(m.finalised()||m.cancelled())continue;
                LocalDate date=m.day();
                if(date==null || !date.equals(current)){current=date;section(date==null?"时间待定":DateTimeFormatter.ofPattern("M月d日 EEEE",Locale.CHINA).format(date));}
                matchRow(m,true);
            }
        } else {
            String[] stages={"live","scheduled","delayed","unknown","final","cancelled"};
            for(String stage:stages) {
                List<Match> stageMatches=new ArrayList<>();for(Match m:all)if(m.state.equals(stage))stageMatches.add(m);
                if(stageMatches.isEmpty())continue;
                section(Rules.stateLabel(stage)+" · "+stageMatches.size());
                for(Match m:stageMatches)matchRow(m,true);
            }
        }
    }
    private void section(String title){ui.gap(body,18);TextView h=ui.text(title,14,ui.text,true);h.setPadding(ui.dp(4),0,0,ui.dp(10));body.addView(h);}
    private void matchRow(Match m,boolean showLeague) {
        LinearLayout card=ui.column();card.setPadding(ui.dp(12),ui.dp(10),ui.dp(12),ui.dp(10));
        ui.clickable(card,ui.surface,12);card.setFocusable(true);card.setOnClickListener(v->matchDetail(m));
        Competition comp=Competition.find(m.league);long age=System.currentTimeMillis()-m.updated;boolean stale=age>120_000;
        LinearLayout meta=ui.row();TextView league=ui.text(comp==null?m.group:comp.name,12,ui.muted,true);meta.addView(league,new LinearLayout.LayoutParams(0,-2,1));
        TextView state=ui.text(m.status,11,ui.text,true);state.setGravity(Gravity.END);meta.addView(state,new LinearLayout.LayoutParams(-2,-2));card.addView(meta);ui.gap(card,9);
        if(m.group.equals("F1")) {
            card.addView(ui.text(m.displayTitle(),17,ui.text,true));
            for(int i=0;i<Math.min(m.results.size(),2);i++){ui.gap(card,5);card.addView(ui.text(m.results.get(i),12,ui.muted,false));}
        } else {
            teamRow(card,m.home,m.homeLogo,m.hasScore()?m.homeScore:"—");ui.gap(card,5);teamRow(card,m.away,m.awayLogo,m.hasScore()?m.awayScore:"—");
        }
        ui.gap(card,8);String time=m.timeConfirmed?m.time():"开赛时间待定";
        String sourceAge=m.updated>0?formatInstant(m.updated,"HH:mm:ss")+" 更新":"未获取";
        TextView foot=ui.text(time+"  ·  "+(stale?"缓存 · ":"")+m.source+" "+sourceAge,11,ui.muted,false);card.addView(foot);
        if(stale&&m.live()){ui.gap(card,6);card.addView(ui.text("上次记录为进行中，当前状态待刷新",12,ui.error,false));}
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=ui.dp(6);body.addView(card,cp);
    }
    private void teamRow(LinearLayout into,String name,String logo,String score) {
        LinearLayout row=ui.row();row.addView(Logos.view(ui,logo,name,25));TextView team=ui.text(name,15,ui.text,true);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMarginStart(ui.dp(10));row.addView(team,tp);
        TextView number=ui.text(score,20,ui.text,true);number.setGravity(Gravity.END);number.setMinWidth(ui.dp(32));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2);sp.setMarginStart(ui.dp(12));row.addView(number,sp);into.addView(row);
    }
    private void matchDetail(Match m) {
        startActivity(new Intent(this,DetailActivity.class).putExtra("match",m.json().toString()));
    }
    private void calendarSummary() {
        LinearLayout block=ui.column();block.setPadding(ui.dp(16),ui.dp(16),ui.dp(16),ui.dp(16));block.setBackground(ui.shape(ui.tonal,14));
        block.addView(ui.text("让日历提醒下一场",18,ui.text,true));ui.gap(block,8);
        boolean enabled=prefs.getBoolean("calendar_enabled",false);
        block.addView(ui.text(enabled?"赛前一天同步 · 开赛前 "+prefs.getInt("remind_minutes",15)+" 分钟提醒":"启用后，关注赛事会在前一天写入手机日历。",13,ui.muted,false));ui.gap(block,14);
        block.addView(ui.button(enabled?"现在同步日历":"设置日历提醒",true,()->{if(enabled)syncCalendar(true);else beginCalendar();}));body.addView(block);
    }
    private void settings() {
        loadingText=null;
        section("日历提醒");
        LinearLayout calendar=ui.column();calendar.setPadding(ui.dp(16),ui.dp(8),ui.dp(16),ui.dp(16));calendar.setBackground(ui.shape(ui.surface,14));
        Switch toggle=new Switch(this);toggle.setText("自动同步至日历");toggle.setTextColor(ui.text);toggle.setTextSize(16);toggle.setMinHeight(ui.dp(56));toggle.setChecked(prefs.getBoolean("calendar_enabled",false));
        toggle.setOnCheckedChangeListener((b,checked)->{if(checked)beginCalendar();else{prefs.edit().putBoolean("calendar_enabled",false).apply();CalendarJob.schedule(this);render(true);}});calendar.addView(toggle);
        ui.gap(calendar,8);calendar.addView(ui.text("比赛前一天导入，提醒由系统日历发送。关闭同步后，已导入日程仍保留。",13,ui.muted,false));ui.gap(calendar,12);
        calendar.addView(ui.button(prefs.getString("calendar_name","选择手机日历"),false,this::beginCalendar));ui.gap(calendar,10);
        calendar.addView(ui.button("提前 "+prefs.getInt("remind_minutes",15)+" 分钟提醒",false,this::reminderChoice));ui.gap(calendar,10);
        calendar.addView(ui.button("选择同步赛事",false,this::competitionChoice));ui.gap(calendar,10);
        calendar.addView(ui.button(calendarBusy?"正在同步…":"现在同步日历",true,()->syncCalendar(true)));
        long checked=prefs.getLong("calendar_checked",0);if(checked>0){ui.gap(calendar,12);calendar.addView(ui.text("最近同步 "+formatInstant(checked,"M月d日 HH:mm")+"\n"+prefs.getString("calendar_result",""),12,ui.muted,false));}
        body.addView(calendar);
        section("显示与连接");body.addView(ui.button("外观 · "+themeName(),false,this::themeChoice));ui.gap(body,12);body.addView(ui.button("查看数据来源状态",false,this::sourceStatus));
        ui.gap(body,18);body.addView(ui.text("体育：ESPN\nCS2：5EPlay · 仅 Spirit 主队\nLOL：英雄联盟赛事官网 · 仅 Worlds\n前台体育约 15 秒刷新，电竞来源至少间隔 60 秒。网络失败时保留缓存和数据时间。",13,ui.muted,false));
        ui.gap(body,18);body.addView(ui.text("无需 Google 服务。后台日历同步受系统省电调度影响，打开应用会补同步。请在系统日历中允许通知及悬浮提醒。",13,ui.muted,false));
        ui.gap(body,18);body.addView(ui.button("打开系统日历",false,()->{Intent i=new Intent(Intent.ACTION_VIEW).setData(Uri.parse("content://com.android.calendar/time/"+System.currentTimeMillis()));try{startActivity(i);}catch(android.content.ActivityNotFoundException e){message("没有找到日历应用","请先在手机中安装或启用系统日历。");}}));
    }
    private void beginCalendar() {
        if(!CalendarSync.permitted(this)){pendingCalendarPermission=true;requestPermissions(new String[]{Manifest.permission.READ_CALENDAR,Manifest.permission.WRITE_CALENDAR},42);return;}
        try {
            List<CalendarSync.CalendarChoice> choices=new CalendarSync(this).calendars();
            if(choices.isEmpty()){message("没有可写入的日历","请先打开手机日历，启用一个本地或账号日历后重试。");render(true);return;}
            String[] names=new String[choices.size()];for(int i=0;i<names.length;i++)names[i]=choices.get(i).name;
            new AlertDialog.Builder(this).setTitle("选择接收赛事的日历").setItems(names,(d,w)->{
                CalendarSync.CalendarChoice chosen=choices.get(w);
                prefs.edit().putLong("calendar_id",chosen.id).putString("calendar_name",chosen.name).putBoolean("calendar_enabled",true).apply();
                CalendarJob.schedule(this);render(true);syncCalendar(true);
            }).setNegativeButton("取消",(d,w)->render(true)).setOnCancelListener(d->render(true)).show();
        }catch(RuntimeException e){message("无法读取手机日历","请检查日历权限和系统日历状态。");}
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==42&&pendingCalendarPermission){pendingCalendarPermission=false;if(CalendarSync.permitted(this))beginCalendar();else{render(true);message("未开启日历权限","比分查看不受影响。需要自动导入时，可在系统应用权限中开启日历读写。");}}}
    private void reminderChoice() {
        int[] minutes={0,5,15,30,60};String[] labels={"开赛时","提前 5 分钟","提前 15 分钟","提前 30 分钟","提前 1 小时"};int selected=2;for(int i=0;i<minutes.length;i++)if(minutes[i]==prefs.getInt("remind_minutes",15))selected=i;
        new AlertDialog.Builder(this).setTitle("日历提醒时间").setSingleChoiceItems(labels,selected,(d,w)->{prefs.edit().putInt("remind_minutes",minutes[w]).apply();d.dismiss();render(true);syncCalendar(false);}).setNegativeButton("取消",null).show();
    }
    private void competitionChoice() {
        String[] labels=new String[Competition.ALL.length];boolean[] selected=new boolean[labels.length];
        for(int i=0;i<labels.length;i++){labels[i]=Competition.ALL[i].name;selected[i]=prefs.getBoolean("sync_"+Competition.ALL[i].id,true);}
        new AlertDialog.Builder(this).setTitle("哪些赛事写入日历").setMultiChoiceItems(labels,selected,(d,w,on)->selected[w]=on).setPositiveButton("保存",(d,w)->{SharedPreferences.Editor e=prefs.edit();for(int i=0;i<labels.length;i++)e.putBoolean("sync_"+Competition.ALL[i].id,selected[i]);e.apply();syncCalendar(false);}).setNegativeButton("取消",null).show();
    }
    private void syncCalendar(boolean feedback) {
        if(calendarBusy)return;
        if(!prefs.getBoolean("calendar_enabled",false)||!CalendarSync.permitted(this)){if(feedback)beginCalendar();return;}
        calendarBusy=true;
        if(feedback){if(loadingText!=null)loadingText.setText("正在同步至手机日历…");if(tab==2)render(true);}
        executor.submit(()->{
            String text;
            try {
                LocalDate f=Match.today(System.currentTimeMillis());Repository.Snapshot data=repository.fetch(f,f.plusDays(7),"全部",feedback);
                List<Match> confirmed=new ArrayList<>();for(Repository.Batch b:data.batches)if(b.error.isEmpty())confirmed.addAll(b.matches);
                CalendarSync.Result result=new CalendarSync(this).sync(confirmed,System.currentTimeMillis());
                text=result.message()+(data.failed()?"\n部分来源连接失败，对应日程未改动。":"")+"\n请在系统日历检查提醒和通知权限。";
            }catch(RuntimeException e){text="同步失败，请检查网络和日历权限后重试。";}
            String finalText=text;
            handler.post(()->{calendarBusy=false;if(isDestroyed())return;if(active){render(true);if(feedback)message("日历同步结果",finalText);}});
        });
    }
    private String themeName(){return switch(prefs.getString("theme","system")){case "dark"->"深色";case "light"->"浅色";default->"跟随系统";};}
    private void themeChoice(){String[] values={"system","light","dark"};new AlertDialog.Builder(this).setTitle("外观").setItems(new String[]{"跟随系统","浅色","深色"},(d,w)->{prefs.edit().putString("theme",values[w]).apply();recreate();}).setNegativeButton("取消",null).show();}
    private void sourceStatus() {
        StringBuilder content=new StringBuilder();
        for(Repository.Batch b:snapshot.batches){content.append(b.competition.name).append("\n").append(b.fetched>0?"上次成功 "+formatInstant(b.fetched,"M月d日 HH:mm:ss")+" · "+b.matches.size()+" 场":"尚未成功获取");if(!b.error.isEmpty())content.append("\n").append(b.error);content.append("\n\n");}
        content.append("当前来源：ESPN、5EPlay、英雄联盟赛事官网。网站接口可能调整或限制访问。HLTV / SofaScore / BO3.gg 不作为默认来源。模拟器测试不等于手机移动网络验收。");
        message("数据来源与连接",content.toString());
    }
    private void navigation() {
        nav=ui.row();nav.setBackgroundColor(ui.surface);String[] labels={"比赛","赛程","设置"},icons={"scores","calendar","settings"};
        for(int i=0;i<3;i++){int index=i;LinearLayout item=ui.column();item.setGravity(Gravity.CENTER);item.setPadding(0,ui.dp(10),0,ui.dp(10));ui.clickable(item,tab==i?ui.tonal:ui.surface,0);item.setFocusable(true);item.setSelected(tab==i);item.setContentDescription(labels[i]+(tab==i?"，已选中":""));item.addView(new Ui.Icon(this,icons[i],tab==i?ui.primary:ui.muted),new LinearLayout.LayoutParams(ui.dp(24),ui.dp(24)));ui.gap(item,5);TextView label=ui.text(labels[i],12,tab==i?ui.primary:ui.muted,tab==i);label.setGravity(Gravity.CENTER);item.addView(label);item.setOnClickListener(v->select(index,group,dayOffset));nav.addView(item,new LinearLayout.LayoutParams(0,-2,1));}root.addView(nav);
    }
    private void message(String title,String text){if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("知道了",null).show();}
    private void openLink(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(android.content.ActivityNotFoundException e){message("无法打开链接","手机没有可用的浏览器。");}}
    private static String formatInstant(long millis,String pattern){return millis>0?DateTimeFormatter.ofPattern(pattern,Locale.CHINA).withZone(Match.ZONE).format(Instant.ofEpochMilli(millis)):"未获取";}
}
