package cn.myscore.app;

import android.app.Activity;
import android.content.Intent;
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
import android.widget.TextView;
import org.json.JSONObject;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A team's own schedule, reached from its crest. The page shows only source-backed fixtures and
 * results; a failed refresh keeps the last real schedule and states its timestamp.
 */
public final class TeamActivity extends Activity {
    private Ui ui;
    private Team team;
    private TeamRepository repository;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout root;
    private ScoreList list;
    private TeamScheduleAdapter adapter;
    private TextView status,error;
    private HorizontalScrollView filterScroll;
    private LinearLayout filters;
    private String filter="upcoming";
    private CalendarSync calendarSync;
    private Match pendingSubscription;
    private boolean calendarBusy;
    private final ExecutorService calendarWorker=Executors.newSingleThreadExecutor();
    private Logos.Crest headerCrest;
    private String headerLogo="";
    private TeamRepository.Result result;
    private boolean busy,active;
    private boolean pendingForce;
    private final Runnable renderSoon=()->{if(active&&!isDestroyed())render();};
    private int pendingPosition=-1;
    private final Runnable tick=()->load(true);
    private boolean pad;
    private ScoreList padUpcomingList,padFinishedList;
    private TeamScheduleAdapter padUpcomingAdapter,padFinishedAdapter;
    private TextView padStatus,padError,padUpcomingCount,padFinishedCount;

    @Override public void onCreate(Bundle saved) {
        boolean dark=Ui.isDark(this);
        setTheme(dark?R.style.AppThemeDark:R.style.AppTheme);
        super.onCreate(saved);ui=new Ui(this,dark);pad=Ui.isPad(this);
        try {
            String raw=getIntent().getStringExtra("team");
            team=Team.fromJson(new JSONObject(raw==null?"{}":raw));
        } catch(Exception e) {
            team=null;
        }
        if(team==null||!team.usable()){finish();return;}
        if(saved!=null){pendingPosition=saved.getInt("position",-1);filter=saved.getString("filter","all");}
        repository=new TeamRepository(this);calendarSync=new CalendarSync(this);
        getWindow().setStatusBarColor(ui.bg);getWindow().setNavigationBarColor(ui.bg);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(ui.bg));
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        root=ui.column();setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)-> {
            int inset=pad?0:ui.pageInset();
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(i.left+inset,i.top,i.right+inset,i.bottom);}
            else v.setPadding(insets.getSystemWindowInsetLeft()+inset,insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight()+inset,insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
        build();
        load(false);
    }
    @Override public void onResume(){super.onResume();active=true;if(repository==null||isFinishing())return;render();if(result==null||pendingForce||System.currentTimeMillis()-result.fetched>5*60_000)load(false);else schedule();}
    @Override public void onPause(){active=false;handler.removeCallbacks(tick);handler.removeCallbacks(renderSoon);super.onPause();}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);worker.shutdownNow();calendarWorker.shutdown();super.onDestroy();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)Ui.highRefresh(getWindow());}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putInt("position",list==null?pendingPosition:list.getFirstVisiblePosition());state.putString("filter",filter);}

    private void build() {
        if(pad){buildPad();return;}
        LinearLayout appbar=ui.row();
        appbar.setPadding(ui.dp(4),ui.dp(4),ui.dp(4),ui.dp(2));
        appbar.addView(iconButton("back","返回",this::finish));
        TextView title=ui.text("球队赛程",16,ui.text,true);title.setGravity(Gravity.CENTER);
        appbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        appbar.addView(iconButton("refresh","刷新球队赛程",()->load(true)));
        root.addView(appbar);

        LinearLayout card=ui.row();card.setBackgroundColor(ui.bg);
        card.setPadding(ui.dp(4),ui.dp(10),ui.dp(4),ui.dp(10));
        FrameLayout crest=new FrameLayout(this);
        headerCrest=Logos.crest(ui,56,false);headerLogo=team.logo;Logos.bind(headerCrest,headerLogo,team.name);
        crest.addView(headerCrest.box,new FrameLayout.LayoutParams(ui.dp(56),ui.dp(56),Gravity.CENTER));
        card.addView(crest,new LinearLayout.LayoutParams(ui.dp(56),ui.dp(56)));
        LinearLayout names=ui.column();
        TextView name=ui.text(team.name,22,ui.text,true);name.setLetterSpacing(-0.02f);names.addView(name);
        ui.gap(names,6);
        String competition=team.league.equals("cs2")?"CS2":team.competitionName();
        names.addView(ui.text(competition+" · 北京时间",12,ui.muted,false));
        LinearLayout.LayoutParams namesParams=new LinearLayout.LayoutParams(0,-2,1);
        namesParams.setMarginStart(ui.dp(14));card.addView(names,namesParams);
        LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(-1,-2);
        cardParams.setMargins(ui.dp(16),ui.dp(2),ui.dp(16),ui.dp(6));root.addView(card,cardParams);

        status=ui.text("正在读取球队赛程…",12,ui.muted,false);status.setPadding(ui.dp(20),ui.dp(2),ui.dp(20),ui.dp(10));root.addView(status);
        error=ui.text("",13,ui.error,false);error.setLineSpacing(ui.dp(4),1);
        error.setBackground(ui.shape(ui.tonal,Ui.CONTROL_RADIUS));error.setPadding(ui.dp(14),ui.dp(12),ui.dp(14),ui.dp(12));
        LinearLayout.LayoutParams errorParams=new LinearLayout.LayoutParams(-1,-2);errorParams.setMargins(ui.dp(16),0,ui.dp(16),ui.dp(10));
        error.setVisibility(View.GONE);root.addView(error,errorParams);

        filterScroll=new HorizontalScrollView(this);filterScroll.setHorizontalScrollBarEnabled(false);
        filters=ui.row();filters.setPadding(ui.dp(6),0,ui.dp(6),0);
        filterScroll.addView(filters);filterScroll.setVisibility(View.GONE);
        root.addView(filterScroll,new LinearLayout.LayoutParams(-1,-2));

        root.addView(ui.divider());
        list=new ScoreList(this);
        list.setDivider(null);list.setDividerHeight(0);list.setClipToPadding(false);list.setVerticalScrollBarEnabled(false);
        list.setPadding(ui.dp(16),ui.dp(6),ui.dp(16),ui.dp(24));
        list.setSelector(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        list.setCacheColorHint(android.graphics.Color.TRANSPARENT);
        adapter=new TeamScheduleAdapter(ui,new TeamScheduleAdapter.Host() {
            public void openMatch(Match match){startActivity(new Intent(TeamActivity.this,DetailActivity.class).putExtra("match",match.json().toString()));}
            public boolean isSubscribed(String id){return calendarSync.isSubscribed(id);}
            public boolean isBusy(){return calendarBusy;}
            public void toggleSubscription(Match match){toggleCalendar(match);}
        });
        adapter.attach(list);list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
    }

    private void buildPad(){
        LinearLayout bar=ui.row();bar.setPadding(ui.dp(12),ui.dp(10),ui.dp(16),ui.dp(10));
        bar.addView(iconButton("back","返回比赛列表",this::finish));
        TextView title=ui.text("球队赛程",17,ui.text,true);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMarginStart(ui.dp(6));bar.addView(title,tp);
        bar.addView(iconButton("refresh","刷新球队赛程",()->load(true)));
        root.addView(bar);root.addView(ui.divider());

        LinearLayout hero=ui.row();hero.setPadding(ui.dp(36),ui.dp(24),ui.dp(36),ui.dp(22));
        headerCrest=Logos.crest(ui,88,false);headerLogo=team.logo;Logos.bind(headerCrest,headerLogo,team.name);
        hero.addView(headerCrest.box,new LinearLayout.LayoutParams(ui.dp(88),ui.dp(88)));
        LinearLayout names=ui.column();LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-2,1);np.setMarginStart(ui.dp(22));
        TextView name=ui.text(team.name,38,ui.text,true);name.setLetterSpacing(-0.04f);names.addView(name);
        ui.gap(names,6);
        String competition=team.league.equals("cs2")?"CS2":team.competitionName();
        names.addView(ui.text(competition+" · 北京时间",12,ui.muted,false));
        ui.gap(names,8);
        padStatus=ui.text("正在读取球队赛程…",11,ui.muted,false);names.addView(padStatus);
        padError=ui.text("",12,ui.error,false);padError.setLineSpacing(ui.dp(3),1);padError.setVisibility(View.GONE);ui.gap(names,6);names.addView(padError);
        hero.addView(names,np);
        LinearLayout summary=ui.row();hero.addView(summary,new LinearLayout.LayoutParams(-2,-2));
        teamSummaryCounts=new TextView[3];
        String[] summaryLabels={"接下来","已结束","总场次"};
        for(int i=0;i<3;i++){LinearLayout metric=padMetric(summaryLabels[i],"");summary.addView(metric,new LinearLayout.LayoutParams(ui.dp(96),-2));teamSummaryCounts[i]=(TextView)metric.getTag();}
        root.addView(hero);root.addView(ui.divider());

        LinearLayout columns=ui.row();
        LinearLayout left=padColumn("接下来","进行中与即将开始的比赛",true);
        columns.addView(left,new LinearLayout.LayoutParams(0,-1,1));
        View rule=new View(this);rule.setBackgroundColor(ui.line);columns.addView(rule,new LinearLayout.LayoutParams(ui.dp(1),-1));
        LinearLayout right=padColumn("已结束","按时间倒序，仅显示来源确认的赛果",false);
        columns.addView(right,new LinearLayout.LayoutParams(0,-1,1));
        root.addView(columns,new LinearLayout.LayoutParams(-1,0,1));
        padUpcomingList.setAdapter(padUpcomingAdapter);
        padFinishedList.setAdapter(padFinishedAdapter);
        renderPad();
    }

    private LinearLayout padColumn(String title,String subtitle,boolean upcoming){
        LinearLayout column=ui.column();
        LinearLayout head=ui.row();head.setPadding(ui.dp(28),ui.dp(20),ui.dp(24),ui.dp(8));
        LinearLayout words=ui.column();
        words.addView(ui.text(title,24,ui.text,true));
        ui.gap(words,3);words.addView(ui.text(subtitle,11,ui.muted,false));
        head.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        TextView count=ui.text("",12,ui.muted,true);count.setGravity(Gravity.END);
        if(upcoming)padUpcomingCount=count;else padFinishedCount=count;
        head.addView(count);
        column.addView(head);
        ScoreList list=new ScoreList(this);
        list.setPadding(ui.dp(22),ui.dp(4),ui.dp(22),ui.dp(28));list.setClipToPadding(false);list.setVerticalScrollBarEnabled(false);
        if(upcoming){padUpcomingList=list;padUpcomingAdapter=padScheduleAdapter();}
        else{padFinishedList=list;padFinishedAdapter=padScheduleAdapter();}
        column.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        return column;
    }
    private TeamScheduleAdapter padScheduleAdapter(){
        return new TeamScheduleAdapter(ui,new TeamScheduleAdapter.Host(){
            public void openMatch(Match match){startActivity(new Intent(TeamActivity.this,DetailActivity.class).putExtra("match",match.json().toString()));}
            public boolean isSubscribed(String id){return calendarSync.isSubscribed(id);}
            public boolean isBusy(){return calendarBusy;}
            public void toggleSubscription(Match match){toggleCalendar(match);}
        });
    }
    private LinearLayout padMetric(String label,String value){
        LinearLayout block=ui.column();block.setPadding(ui.dp(10),0,ui.dp(10),0);
        TextView number=ui.text(value,22,ui.text,true);block.addView(number);
        block.addView(ui.text(label,10,ui.muted,false));
        block.setTag(number);
        return block;
    }

    private void renderPad(){
        if(headerLogo.isBlank()&&result!=null){
            for(Match m:result.matches){
                String logo=team.id.equals(m.homeId)?m.homeLogo:team.id.equals(m.awayId)?m.awayLogo:"";
                if(!logo.isBlank()){headerLogo=logo;Logos.bind(headerCrest,logo,team.name);break;}
            }
        }
        if(padStatus!=null){
            if(result==null)padStatus.setText(busy?"正在读取球队赛程…":"尚未获取球队赛程");
            else{
                String fetched=result.fetched>0?format(result.fetched,"M月d日 HH:mm")+" 获取":"尚未成功获取";
                padStatus.setText(result.source+" · 共 "+result.matches.size()+" 场 · "+fetched+(busy?" · 更新中":""));
                padStatus.setTextColor(result.error.isEmpty()?ui.muted:ui.error);
            }
        }
        if(padError!=null){
            boolean failed=result!=null&&!result.error.isEmpty();
            padError.setVisibility(failed?View.VISIBLE:View.GONE);
            if(failed)padError.setText((result.fetched>0?"球队赛程未更新 · ":"无法获取球队赛程 · ")+result.error+(result.fetched>0?"\n下方保留上次成功获取的赛程。":""));
        }
        List<TeamScheduleRow> upcoming=padRows(false),finished=padRows(true);
        if(padUpcomingAdapter!=null)padUpcomingAdapter.updateRows(upcoming);
        if(padFinishedAdapter!=null)padFinishedAdapter.updateRows(finished);
        if(result!=null){
            int up=0,fin=0;
            for(Match m:result.matches){if(m.finalised())fin++;else if(m.live()||m.scheduled()||m.state.equals("delayed"))up++;}
            if(padUpcomingCount!=null)padUpcomingCount.setText(up+" 场");
            if(padFinishedCount!=null)padFinishedCount.setText(fin+" 场");
            if(teamSummaryCounts!=null){teamSummaryCounts[0].setText(String.valueOf(up));teamSummaryCounts[1].setText(String.valueOf(fin));teamSummaryCounts[2].setText(String.valueOf(result.matches.size()));}
        }
    }
    private TextView[] teamSummaryCounts;
    private List<TeamScheduleRow> padRows(boolean finished){
        List<TeamScheduleRow> rows=new ArrayList<>();
        if(result==null){rows.add(TeamScheduleRow.note(busy?"正在读取球队赛程":"尚未获取球队赛程",busy?"正在连接球队的数据来源。":"点右上角刷新重试。"));return rows;}
        if(result.matches.isEmpty()){rows.add(TeamScheduleRow.note("该队暂未提供赛程",emptyMessage()));return rows;}
        List<Match> live=new ArrayList<>(),upcoming=new ArrayList<>(),done=new ArrayList<>(),other=new ArrayList<>();
        for(Match m:result.matches){if(m.live())live.add(m);else if(m.scheduled()||m.state.equals("delayed"))upcoming.add(m);else if(m.finalised())done.add(m);else other.add(m);}
        Comparator<Match> byStart=Comparator.comparingLong((Match m)->m.start==0?Long.MAX_VALUE:m.start).thenComparing(m->m.id);
        live.sort(byStart);upcoming.sort(byStart);done.sort(byStart.reversed());
        if(finished){
            for(Match m:done)rows.add(TeamScheduleRow.match(m,team));
            if(rows.isEmpty())rows.add(TeamScheduleRow.note("没有已结束的比赛","来源尚未返回这支球队的赛果。"));
        }else{
            if(!live.isEmpty()){rows.add(TeamScheduleRow.section("进行中 · "+live.size()));for(Match m:live)rows.add(TeamScheduleRow.match(m,team));}
            for(Match m:upcoming)rows.add(TeamScheduleRow.match(m,team));
            if(rows.isEmpty())rows.add(TeamScheduleRow.note("没有接下来的比赛","来源尚未返回这支球队的后续赛程。"));
        }
        return rows;
    }

    private FrameLayout iconButton(String kind,String description,Runnable click) {
        FrameLayout button=new FrameLayout(this);
        button.addView(new Ui.Icon(this,kind,ui.text),new FrameLayout.LayoutParams(ui.dp(22),ui.dp(22),Gravity.CENTER));
        ui.clickable(button,android.graphics.Color.TRANSPARENT,Ui.CONTROL_RADIUS);button.setContentDescription(description);button.setFocusable(true);
        button.setOnClickListener(v->click.run());
        button.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
        return button;
    }

    private void toggleCalendar(Match match) {
        if(calendarBusy)return;
        boolean remove=calendarSync.isSubscribed(match.id);
        if(!remove&&!(match.scheduled()&&match.timeConfirmed&&match.start>System.currentTimeMillis())) {
            Sheet.message(this,"暂时不能订阅","这场比赛的开赛时间未确定、已开始或已结束。");return;
        }
        if(!CalendarSync.permitted(this)) {
            pendingSubscription=match;
            requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR,android.Manifest.permission.WRITE_CALENDAR},42);return;
        }
        if(remove)Sheet.confirm(this,"取消订阅","从手机日历中移除「"+match.displayTitle()+"」及其提醒？","取消订阅",()->writeCalendar(match,true));
        else writeCalendar(match,false);
    }
    private void writeCalendar(Match match,boolean remove) {
        if(calendarBusy)return;
        calendarBusy=true;render();
        calendarWorker.submit(()-> {
            CalendarSync.Result outcome;
            try {
                if(remove)outcome=calendarSync.unsubscribe(match.id);
                else {
                    if(!calendarSync.hasCalendar(calendarSync.calendarId())) {
                        CalendarSync.CalendarChoice best=calendarSync.bestCalendar();
                        if(best==null)throw new IllegalStateException("请先在系统日历中启用一个本地或小米日历。");
                        calendarSync.chooseCalendar(best.id,best.name);
                    }
                    outcome=calendarSync.subscribe(match,System.currentTimeMillis());
                }
            } catch(RuntimeException e) {outcome=new CalendarSync.Result();outcome.failed=1;outcome.error=e.getMessage()==null?"日历写入失败":e.getMessage();}
            CalendarSync.Result completed=outcome;
            handler.post(()-> {
                calendarBusy=false;if(isDestroyed())return;
                render();CalendarJob.schedule(this);
                if(completed.failed>0)Sheet.message(this,remove?"未能取消订阅":"未能加入日历",completed.error);
                else android.widget.Toast.makeText(this,remove?"已取消订阅":"已加入日历 · 提前 5 分钟提醒",android.widget.Toast.LENGTH_SHORT).show();
            });
        });
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        if(code!=42)return;
        Match match=pendingSubscription;pendingSubscription=null;
        if(CalendarSync.permitted(this)){if(match!=null)toggleCalendar(match);}
        else Sheet.message(this,"未开启日历权限","需要订阅时，请在系统应用权限中允许日历读写。");
    }

    private void load(boolean force) {
        if(!active||isFinishing()||isDestroyed())return;
        if(busy){pendingForce|=force;return;}
        handler.removeCallbacks(tick);
        boolean forced=force||pendingForce;pendingForce=false;
        if(forced)Logos.retryFailures();
        busy=true;render();
        Team requested=team;boolean readCache=result==null;
        worker.submit(()-> {
            if(readCache) {
                TeamRepository.Result cached=repository.cached(requested);
                if(cached.fetched>0)handler.post(()->{if(isDestroyed())return;result=cached;render();});
            }
            TeamRepository.Result next=repository.fetch(requested,forced);
            handler.post(()-> {
                if(isDestroyed())return;
                busy=false;result=next;
                if(active){render();if(pendingForce)load(false);else schedule();}
            });
        });
    }

    /** A live match refreshes at the app's foreground cadence; a static schedule does not poll. */
    private void schedule() {
        handler.removeCallbacks(tick);
        if(!active||result==null)return;
        if(result.matches.stream().anyMatch(Match::live))handler.postDelayed(tick,30_000);
    }

    private void render() {
        if(!active||isFinishing()||isDestroyed())return;
        if(pad){renderPad();return;}
        if(list!=null&&list.isInteracting()){handler.removeCallbacks(renderSoon);handler.postDelayed(renderSoon,200);return;}
        handler.removeCallbacks(renderSoon);
        if(headerLogo.isBlank()&&result!=null) {
            for(Match m:result.matches) {
                String logo=team.id.equals(m.homeId)?m.homeLogo:team.id.equals(m.awayId)?m.awayLogo:"";
                if(!logo.isBlank()){headerLogo=logo;Logos.bind(headerCrest,logo,team.name);break;}
            }
        }
        updateStatus();
        updateFilters();
        List<TeamScheduleRow> rows=buildRows();
        boolean relaid=adapter.updateRows(rows);
        if(relaid&&pendingPosition>=0){int position=pendingPosition;pendingPosition=-1;list.post(()->list.setSelection(position));}
    }

    private void updateStatus() {
        if(status==null||error==null)return;
        if(result==null) {
            status.setText(busy?"正在读取球队赛程…":"尚未获取球队赛程");
            error.setVisibility(View.GONE);
            return;
        }
        String fetched=result.fetched>0?format(result.fetched,"M月d日 HH:mm")+" 获取":"尚未成功获取";
        if(result.error.isEmpty())status.setText(result.source+" · 共 "+result.matches.size()+" 场 · "+fetched+(busy?" · 更新中":""));
        else if(result.fetched>0)status.setText(result.source+" · 显示 "+format(result.fetched,"M月d日 HH:mm")+" 的记录");
        else status.setText("球队赛程暂不可用");
        boolean failed=!result.error.isEmpty();
        error.setVisibility(failed?View.VISIBLE:View.GONE);
        if(failed)error.setText((result.fetched>0?"球队赛程未更新 · ":"无法获取球队赛程 · ")+result.error+(result.fetched>0?"\n下方保留上次成功获取的赛程。":""));
    }

    private List<TeamScheduleRow> buildRows() {
        List<TeamScheduleRow> rows=new ArrayList<>();
        if(result==null) {
            rows.add(TeamScheduleRow.note(busy?"正在读取球队赛程":"尚未获取球队赛程",busy?"正在连接球队的数据来源。":"点右上角刷新重试。"));
            return rows;
        }
        if(result.matches.isEmpty()) {
            if(!result.error.isEmpty())rows.add(TeamScheduleRow.note("暂时无法获取赛程",result.error+"\n\n请检查网络后点右上角刷新重试。"));
            else rows.add(TeamScheduleRow.note("该队暂未提供赛程",emptyMessage()));
            return rows;
        }
        List<Match> live=new ArrayList<>(),upcoming=new ArrayList<>(),finished=new ArrayList<>(),other=new ArrayList<>();
        for(Match m:result.matches) {
            if(m.live())live.add(m);
            else if(m.scheduled()||m.state.equals("delayed"))upcoming.add(m);
            else if(m.finalised())finished.add(m);
            else other.add(m);
        }
        Comparator<Match> byStart=Comparator.comparingLong((Match m)->m.start==0?Long.MAX_VALUE:m.start).thenComparing(m->m.id);
        live.sort(byStart);upcoming.sort(byStart);finished.sort(byStart.reversed());other.sort(byStart);
        if(filter.equals("upcoming")) {
            append(rows,"进行中",live);
            append(rows,"接下来",upcoming);
        } else if(filter.equals("finished")) {
            append(rows,"已结束",finished);
        } else {
            append(rows,"进行中",live);
            append(rows,"接下来",upcoming);
            append(rows,"已结束",finished);
            append(rows,"其他状态",other);
        }
        rows.add(TeamScheduleRow.note("关于这些赛程",footnote()));
        return rows;
    }
    /** The two-chip switch appears only when both upcoming fixtures and results exist. */
    private void updateFilters() {
        if(filters==null)return;
        filters.removeAllViews();
        if(result==null){filterScroll.setVisibility(View.GONE);return;}
        int upcoming=0,finished=0;
        for(Match m:result.matches) {
            if(m.finalised())finished++;
            else if(m.live()||m.scheduled()||m.state.equals("delayed"))upcoming++;
        }
        if(upcoming==0||finished==0) {
            filterScroll.setVisibility(View.GONE);
            if(!filter.equals("all"))filter="all";
            return;
        }
        filterScroll.setVisibility(View.VISIBLE);
        option("全部","all",result.matches.size());
        option("接下来","upcoming",upcoming);
        option("已结束","finished",finished);
    }
    private void option(String label,String value,int count) {
        boolean selected=filter.equals(value);
        TextView chip=ui.tab(label+" "+count,selected,()->{filter=value;list.setSelection(0);render();});
        chip.setContentDescription(label+count+"场"+(selected?"，已选中":"，筛选球队赛程"));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2);
        params.setMarginEnd(ui.dp(8));filters.addView(chip,params);
    }
    private void append(List<TeamScheduleRow> rows,String title,List<Match> matches) {
        if(matches.isEmpty())return;
        rows.add(TeamScheduleRow.section(title+" · "+matches.size()));
        for(Match m:matches)rows.add(TeamScheduleRow.match(m,team));
    }
    private String emptyMessage() {
        return switch(team.league) {
            case "cs2" -> "5EPlay 暂未返回这支队伍的战队赛程。";
            case "lol" -> "官网暂未返回这支队伍在本届 Worlds 的赛程。";
            default -> "来源暂未返回这支队伍本赛季的赛程。";
        };
    }
    private String footnote() {
        String coverage=switch(team.league) {
            case "cs2" -> "5EPlay 战队赛程，最多显示 30 场近期已结束的比赛与接下来已发布的比赛。";
            case "lol" -> "只显示本应用覆盖的 Worlds 赛程，不包含其他联赛。";
            default -> "ESPN 本赛季球队赛程，合并已结束的比赛与接下来已发布的赛程。";
        };
        String note="比分为本队在前；点任意比赛查看完整详情。"+coverage;
        if(result!=null&&result.stale())note+="\n当前未能更新，以上为 "+format(result.fetched,"M月d日 HH:mm")+" 获取的记录。";
        return note;
    }
    private static String format(long value,String pattern) {
        return value>0?DateTimeFormatter.ofPattern(pattern,Locale.CHINA).withZone(Match.ZONE).format(Instant.ofEpochMilli(value)):"尚未提供";
    }
}
