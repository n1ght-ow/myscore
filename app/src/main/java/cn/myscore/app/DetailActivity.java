package cn.myscore.app;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/** Full-screen, source-backed match centre. System Back returns to the score list. */
public final class DetailActivity extends Activity {
    private Ui ui;private Match match;private MatchDetail detail;private DetailRepository repository;
    private LinearLayout root,body;private ScrollView scroll;private TextView status;
    private TextView headerTitle,headerState,headerStart,headerHome,headerAway;
    private Ui.ScoreText headerScore;
    private TextView[] tabLabels;private View[] tabRules;private String[] tabKeys,tabNames;
    private String tab="overview",error="";private boolean busy,active,chromeBuilt,fingerDown;private int selectedGame=-1,failures;
    private boolean pendingForce;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Runnable tick=()->load(false);
    private String rendered="";
    private long lastScroll;private String teamFilter="all",categoryFilter="",sortColumn="";private boolean descending=true;private int metricsX,eventsLimit=50,playerLimit=16;
    private final Runnable update=()->{if(active&&!isDestroyed())render(true);};
    @Override public void onCreate(Bundle saved){
        boolean dark=Ui.isDark(this);
        setTheme(dark?R.style.AppThemeDark:R.style.AppTheme);super.onCreate(saved);ui=new Ui(this,dark);
        try{match=Match.fromJson(new JSONObject(getIntent().getStringExtra("match")));if(match.id.isBlank())throw new IllegalArgumentException();}catch(Exception e){finish();return;}
        if(saved!=null){tab=saved.getString("tab","overview");selectedGame=saved.getInt("game",-1);teamFilter=saved.getString("team","all");categoryFilter=saved.getString("category","");sortColumn=saved.getString("sort","");descending=saved.getBoolean("descending",true);metricsX=saved.getInt("metricsX");eventsLimit=saved.getInt("eventsLimit",50);playerLimit=saved.getInt("playerLimit",16);}if((match.group.equals("F1")||match.group.equals("LOL"))&&tab.equals("events"))tab="overview";repository=new DetailRepository(this);
        getWindow().setStatusBarColor(ui.bg);getWindow().setNavigationBarColor(ui.bg);getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(ui.bg));if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        root=ui.column();setContentView(root);root.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(i.left,i.top,i.right,i.bottom);}else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});root.requestApplyInsets();render(false);
    }
    @Override public void onResume(){super.onResume();active=true;if(match!=null)load(false);}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)Ui.highRefresh(getWindow());}
    @Override public void onPause(){active=false;fingerDown=false;handler.removeCallbacks(tick);handler.removeCallbacks(update);super.onPause();}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("tab",tab);state.putInt("game",selectedGame);state.putString("team",teamFilter);state.putString("category",categoryFilter);state.putString("sort",sortColumn);state.putBoolean("descending",descending);state.putInt("metricsX",metricsX);state.putInt("eventsLimit",eventsLimit);state.putInt("playerLimit",playerLimit);}
    private void load(boolean force){
        if(force){Logos.retryFailures();rendered="";}
        handler.removeCallbacks(tick);if(busy||!active){pendingForce|=force;return;}boolean forced=force||pendingForce;pendingForce=false;busy=true;if(status!=null)status.setText("正在更新详情…");
        Match requested=match;boolean needsCache=detail==null;worker.submit(()->{
            if(needsCache){MatchDetail cached=repository.cached(requested);if(cached!=null)handler.post(()->{if(isDestroyed())return;detail=cached;if(cached.match.updated>=match.updated)match=cached.match;if(active)render(true);});}
            MatchDetail next=null;String problem="";try{next=repository.fetch(requested,forced);}catch(Exception e){problem=e.getMessage()==null?"网络连接失败":e.getMessage();}MatchDetail result=next;String failure=problem;handler.post(()->{busy=false;if(isDestroyed())return;error=failure;failures=failure.isEmpty()?0:failures+1;if(result!=null){detail=result;match=result.match;}if(active){render(true);if(pendingForce){load(true);return;}if(!match.finalised()&&!match.cancelled()||!failure.isEmpty()){long interval=RefreshPolicy.detail(match);long elapsed=result==null?0:Math.max(0,System.currentTimeMillis()-result.fetched);handler.postDelayed(tick,RefreshPolicy.remaining(RefreshPolicy.retry(interval,failures),elapsed));}}});});
    }
    private FrameLayout iconButton(String kind,String description,Runnable click){FrameLayout b=new FrameLayout(this);b.addView(new Ui.Icon(this,kind,ui.text),new FrameLayout.LayoutParams(ui.dp(22),ui.dp(22),Gravity.CENTER));ui.clickable(b,ui.tonal,Ui.CONTROL_RADIUS);b.setContentDescription(description);b.setFocusable(true);b.setOnClickListener(v->click.run());b.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));return b;}
    private void render(boolean preserve){
        if(isFinishing()||isDestroyed())return;
        if(preserve&&(fingerDown||SystemClock.uptimeMillis()-lastScroll<200)){handler.removeCallbacks(update);handler.postDelayed(update,200);return;}
        boolean first=!chromeBuilt;
        if(first)buildChrome();else updateChrome();
        String key=bodyKey();long started=SystemClock.uptimeMillis();
        boolean changed=first||!key.equals(rendered);
        if(changed){rendered=key;if(BuildConfig.DEBUG)android.util.Log.i("MyScorePerf","detail body rebuilt tab="+tab+" detail="+(detail!=null));rebuildBody(preserve);}
        else updateStatus();
        if(BuildConfig.DEBUG)android.util.Log.i("MyScorePerf","detail update ms="+(SystemClock.uptimeMillis()-started)+" rebuilt="+changed);
    }
    @SuppressLint("ClickableViewAccessibility")
    private void buildChrome(){
        root.removeAllViews();
        LinearLayout appbar=ui.row();appbar.setPadding(ui.dp(4),ui.dp(4),ui.dp(4),ui.dp(2));appbar.addView(iconButton("back","返回比赛列表",this::finish));Competition competition=Competition.find(match.league);TextView title=ui.text(competition==null?match.group:competition.name,16,ui.text,true);title.setGravity(Gravity.CENTER);appbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));appbar.addView(iconButton("refresh","刷新比赛详情",()->load(true)));root.addView(appbar);
        buildScoreHeader();buildTabs();root.addView(ui.divider());
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);scroll.setOnScrollChangeListener((v,x,sy,ox,oy)->lastScroll=SystemClock.uptimeMillis());
        scroll.setOnTouchListener((v,event)-> {
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN)fingerDown=true;
            else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){fingerDown=false;lastScroll=SystemClock.uptimeMillis();}
            return false;
        });
        body=ui.column();body.setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(24));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        status=null;
        chromeBuilt=true;
    }
    private void buildScoreHeader(){
        LinearLayout card=ui.column();card.setBackground(ui.panel());card.setPadding(ui.dp(16),ui.dp(20),ui.dp(16),ui.dp(20));
        if(match.group.equals("F1")){
            headerTitle=ui.text(match.displayTitle(),18,ui.text,true);headerTitle.setGravity(Gravity.CENTER);card.addView(headerTitle);ui.gap(card,8);
            headerState=ui.text(match.status+" · "+(match.timeConfirmed?time(match.start,"M/d HH:mm"):"时间待定"),12,ui.muted,false);headerState.setGravity(Gravity.CENTER);card.addView(headerState);
        }else{
            LinearLayout row=ui.row();row.setGravity(Gravity.CENTER);LinearLayout homeSide=team(match.home,match.homeLogo);row.addView(homeSide,new LinearLayout.LayoutParams(0,-2,1));
            LinearLayout center=ui.column();center.setGravity(Gravity.CENTER);
            headerScore=ui.score(match.homeScore,match.awayScore,match.hasScore(),36);center.addView(headerScore,new LinearLayout.LayoutParams(-1,-2));ui.gap(center,8);
            headerState=ui.text(match.status,12,match.live()?ui.primary:ui.muted,true);headerState.setGravity(Gravity.CENTER);center.addView(headerState,new LinearLayout.LayoutParams(-1,-2));ui.gap(center,6);
            headerStart=ui.text(match.timeConfirmed?time(match.start,"M/d HH:mm"):"时间待定",11,ui.muted,false);headerStart.setGravity(Gravity.CENTER);center.addView(headerStart,new LinearLayout.LayoutParams(-1,-2));
            row.addView(center,new LinearLayout.LayoutParams(0,-2,1.35f));
            LinearLayout awaySide=team(match.away,match.awayLogo);row.addView(awaySide,new LinearLayout.LayoutParams(0,-2,1));card.addView(row);
            headerHome=(TextView)homeSide.getChildAt(2);
            headerAway=(TextView)awaySide.getChildAt(2);
        }
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(ui.dp(16),ui.dp(2),ui.dp(16),ui.dp(8));root.addView(card,cp);
    }
    /** Header text stays live without replacing the body or the scroll container. */
    private void updateChrome(){
        if(match.group.equals("F1")){
            if(headerTitle!=null){headerTitle.setText(match.displayTitle());headerState.setText(match.status+" · "+(match.timeConfirmed?time(match.start,"M/d HH:mm"):"时间待定"));}
        }else if(headerScore!=null){
            headerHome.setText(match.home);headerAway.setText(match.away);
            headerScore.setScore(match.homeScore,match.awayScore,match.hasScore());
            headerState.setText(match.status);headerState.setTextColor(match.live()?ui.primary:ui.muted);headerStart.setText(match.timeConfirmed?time(match.start,"M/d HH:mm"):"时间待定");
        }
        updateTabs();updateStatus();
    }
    private void updateStatus(){if(status!=null)status.setText(statusText());}
    private String bodyKey(){
        StringBuilder key=new StringBuilder(96).append(tab).append(':').append(selectedGame).append(':').append(teamFilter).append(':').append(categoryFilter).append(':').append(sortColumn).append(':').append(descending).append(':').append(eventsLimit).append(':').append(playerLimit).append(':').append(error).append(':').append(match.group);
        if(detail==null)key.append(":empty:").append(busy);else key.append(':').append(detail.bodySignature());
        return key.toString();
    }
    private void rebuildBody(boolean preserve){
        int y=preserve&&scroll!=null?scroll.getScrollY():0;
        body=ui.column();body.setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(24));
        if(scroll!=null){scroll.removeAllViews();scroll.addView(body);}
        status=ui.text(statusText(),11,ui.muted,false);
        body.addView(status);ui.gap(body,12);
        if(!error.isEmpty()){LinearLayout warning=panel();warning.addView(ui.text("暂未更新 · "+error+(detail==null?"":"\n显示上次获取的数据"),13,ui.error,false));ui.gap(warning,12);warning.addView(ui.button("重新加载",false,()->load(true)));body.addView(warning);ui.gap(body,14);}
        if(detail!=null&&!detail.warning.isBlank()){body.addView(ui.text(detail.warning,12,ui.muted,false));ui.gap(body,12);}
        if(detail==null)empty(busy?"正在读取比赛数据":"比赛详情暂不可用",busy?"正在连接本场比赛的数据来源。":"请检查网络并重新加载，列表仍保留上次记录。");
        else if(match.group.equals("LOL"))lol();else if(tab.equals("overview"))overview();else if(tab.equals("stats"))statistics();else if(tab.equals("events"))events();else roster("lineup");
        if(scroll!=null)scroll.post(()->scroll.scrollTo(0,preserve?y:0));
    }
    private String statusText(){if(detail==null)return busy?"正在读取详情…":"尚未获取详情";if(match.group.equals("LOL"))return detail.lolFetched>0?"小局 "+time(detail.lolFetched,"HH:mm:ss")+" 获取 · 赛程 "+time(match.updated,"HH:mm:ss")+" 发布":"赛程 "+time(detail.fetched,"HH:mm:ss")+" 获取 · 小局尚未提供";return (System.currentTimeMillis()-detail.fetched>120_000?"缓存 · ":"")+detail.source+" · "+time(detail.fetched,"HH:mm:ss")+" 获取";}
    private LinearLayout team(String name,String logo){LinearLayout side=ui.column();side.setGravity(Gravity.CENTER);side.addView(Logos.view(ui,logo,name,44));ui.gap(side,10);TextView text=ui.text(name,12,ui.text,true);text.setGravity(Gravity.CENTER);side.addView(text,new LinearLayout.LayoutParams(-1,-2));return side;}
    private String lineupName(){return switch(match.group){case "足球"->"阵容";case "LOL"->"比赛";case "F1"->"车手";case "CS2"->"选手";default->"球员";};}
    private void buildTabs(){
        LinearLayout row=ui.row();boolean f1=match.group.equals("F1"),lol=match.group.equals("LOL");tabKeys=f1||lol?new String[]{"overview","lineup","stats"}:new String[]{"overview","lineup","stats","events"};tabNames=f1?new String[]{"总览","车手","成绩"}:lol?new String[]{"总览","比赛","统计"}:new String[]{"总览",lineupName(),"统计",match.group.equals("CS2")?"小局":"进程"};
        tabLabels=new TextView[tabKeys.length];tabRules=new View[tabKeys.length];
        for(int i=0;i<tabKeys.length;i++){String key=tabKeys[i];LinearLayout item=ui.column();item.setGravity(Gravity.CENTER);ui.clickable(item,ui.bg,0);item.setFocusable(true);TextView label=ui.text(tabNames[i],14,tab.equals(key)?ui.primary:ui.muted,tab.equals(key));label.setGravity(Gravity.CENTER);label.setMinHeight(ui.dp(48));item.addView(label,new LinearLayout.LayoutParams(-1,-2));View underline=new View(this);underline.setBackground(ui.shape(tab.equals(key)?ui.primary:ui.bg,2));item.addView(underline,new LinearLayout.LayoutParams(ui.dp(24),ui.dp(3)));item.setOnClickListener(v->{tab=key;categoryFilter="";sortColumn="";metricsX=0;playerLimit=16;render(false);});row.addView(item,new LinearLayout.LayoutParams(0,-2,1));tabLabels[i]=label;tabRules[i]=underline;}
        root.addView(row);updateTabs();
    }
    private void updateTabs(){
        if(tabLabels==null)return;
        for(int i=0;i<tabLabels.length;i++){boolean on=tabKeys[i].equals(tab);tabLabels[i].setTextColor(on?ui.primary:ui.muted);tabRules[i].setBackground(ui.shape(on?ui.primary:ui.bg,2));tabLabels[i].setSelected(on);View item=(View)tabLabels[i].getParent();item.setSelected(on);item.setContentDescription(tabNames[i]+(on?"，已选中":""));}
    }
    private LinearLayout panel(){LinearLayout card=ui.column();card.setBackground(ui.panel());card.setPadding(ui.dp(16),ui.dp(14),ui.dp(16),ui.dp(14));return card;}
    private void heading(String text){ui.gap(body,24);body.addView(ui.text(text,16,ui.text,true));ui.gap(body,12);}
    private void overview(){
        tables("overview");
        if(!detail.stats.isEmpty()){heading("数据速览");LinearLayout card=panel();teamNames(card);for(int i=0;i<Math.min(3,detail.stats.size());i++)stat(card,detail.stats.get(i));body.addView(card);ui.gap(body,10);body.addView(ui.button("全部技术统计",false,()->{tab="stats";render(false);}));}
        if(!detail.events.isEmpty()){heading("最新进程");LinearLayout timeline=panel();for(int i=0;i<Math.min(3,detail.events.size());i++)event(timeline,detail.events.get(i));body.addView(timeline);}
        information();sourceLink();
    }
    private void information(){
        heading("比赛信息");LinearLayout card=panel();LinkedHashMap<String,List<String>> facts=new LinkedHashMap<>();for(MatchDetail.Fact f:detail.facts){String label=DetailParsers.label(DetailParsers.plain(f.label())),value=DetailParsers.plain(f.value());if(!value.isBlank())facts.computeIfAbsent(label.isBlank()?"信息":label,k->new ArrayList<>()).add(value);}
        if(facts.isEmpty()&&!match.detail.isBlank())facts.put("赛事",List.of(match.detail));int index=0;for(Map.Entry<String,List<String>> fact:facts.entrySet()){if(index++>0)card.addView(ui.divider());LinearLayout row=ui.row();row.setGravity(Gravity.TOP);row.setPadding(0,ui.dp(10),0,ui.dp(10));TextView label=ui.text(fact.getKey(),12,ui.muted,false),value=ui.text(String.join("\n",new LinkedHashSet<>(fact.getValue())),13,ui.text,false);value.setLineSpacing(ui.dp(4),1);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));row.addView(value,new LinearLayout.LayoutParams(0,-2,2));card.addView(row);}if(index==0)card.addView(ui.text("来源尚未提供更多比赛信息",13,ui.muted,false));body.addView(card);
    }
    private void sourceLink(){ui.gap(body,16);body.addView(ui.button("来源与更新时间",false,()->Sheet.message(this,"数据来源",detail.source+"\n获取时间 "+time(detail.fetched,"M月d日 HH:mm:ss")+(match.group.equals("LOL")?"\n赛程发布时间 "+time(match.updated,"M月d日 HH:mm:ss")+(detail.lolFetched>0?"\n小局获取时间 "+time(detail.lolFetched,"M月d日 HH:mm:ss"):""):"")+"\n\n数据按来源发布，未提供的字段保持未知。")));if(!match.url.isBlank()){ui.gap(body,8);body.addView(ui.button("打开来源页面",false,()->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(match.url)));}catch(android.content.ActivityNotFoundException e){Sheet.message(this,"无法打开来源","手机没有可用的浏览器。");}}));}}
    private void statistics(){
        if(match.group.equals("F1")){roster("stats");return;}
        if(!detail.stats.isEmpty()){heading("球队对比");LinearLayout card=panel();teamNames(card);for(MatchDetail.Stat stat:detail.stats)stat(card,stat);body.addView(card);}
        boolean has=tables("stats");if(!has&&detail.stats.isEmpty()){if(match.group.equals("CS2")){empty("选手数据按全场与地图分类", "在选手页比较 K/D/A、ADR、KAST 与来源 Rating。");body.addView(ui.button("查看选手数据",false,()->{tab="lineup";render(false);}));}else empty("本场统计尚未提供",missing());}
    }
    private void teamNames(LinearLayout card){LinearLayout names=ui.row();TextView home=ui.text(match.home,12,ui.text,true),away=ui.text(match.away,12,ui.text,true);away.setGravity(Gravity.END);names.addView(home,new LinearLayout.LayoutParams(0,-2,1));names.addView(away,new LinearLayout.LayoutParams(0,-2,1));card.addView(names);ui.gap(card,10);card.addView(ui.divider());}
    private void stat(LinearLayout card,MatchDetail.Stat stat){LinearLayout row=ui.row();row.setPadding(0,ui.dp(13),0,ui.dp(10));TextView h=ui.text(stat.home(),16,ui.primary,true),label=ui.text(stat.label(),12,ui.muted,false),a=ui.text(stat.away(),16,ui.secondary,true);label.setGravity(Gravity.CENTER);a.setGravity(Gravity.END);row.addView(h,new LinearLayout.LayoutParams(0,-2,1));row.addView(label,new LinearLayout.LayoutParams(0,-2,2));row.addView(a,new LinearLayout.LayoutParams(0,-2,1));card.addView(row);double home=numeric(stat.home()),away=numeric(stat.away());if(home>=0&&away>=0&&home+away>0){LinearLayout track=ui.row();View left=new View(this),right=new View(this);left.setBackground(ui.shape(ui.primary,2));right.setBackground(ui.shape(ui.secondary,2));track.addView(left,new LinearLayout.LayoutParams(0,ui.dp(4),(float)home));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,ui.dp(4),(float)away);rp.setMarginStart(ui.dp(4));track.addView(right,rp);card.addView(track);}ui.gap(card,12);card.addView(ui.divider());}
    private static double numeric(String value){try{return value.matches("[0-9]+(?:\\.[0-9]+)?%?")?Double.parseDouble(value.replace("%","")):-1;}catch(RuntimeException e){return -1;}}
    private boolean tables(String key){boolean found=false;for(MatchDetail.Table t:detail.tables)if(t.tab.equals(key)){heading(t.title);body.addView(new DetailTableView(ui).simple(t),new LinearLayout.LayoutParams(-1,-2));found=true;}return found;}
    private void roster(String key){new PlayerStatsView(ui,detail,body).show(key,new PlayerStatsView.Selection(teamFilter,categoryFilter,sortColumn,descending),selection->{if(!categoryFilter.isBlank()&&!selection.category().equals(categoryFilter))metricsX=0;if(!selection.team().equals(teamFilter)||!selection.category().equals(categoryFilter))playerLimit=16;teamFilter=selection.team();categoryFilter=selection.category();sortColumn=selection.sort();descending=selection.descending();render(false);},metricsX,value->{metricsX=value;lastScroll=SystemClock.uptimeMillis();},playerLimit,()->{playerLimit=Math.min(400,playerLimit+24);render(true);});}
    private void events(){boolean found=tables("events");if(!detail.events.isEmpty()){heading(match.group.equals("足球")?"关键事件":match.group.equals("NFL")?"得分进程":"比赛进程");LinearLayout timeline=panel();for(int i=0;i<Math.min(eventsLimit,detail.events.size());i++)event(timeline,detail.events.get(i));body.addView(timeline);if(detail.events.size()>eventsLimit){ui.gap(body,12);body.addView(ui.button("加载更多进程 · 剩余 "+(detail.events.size()-eventsLimit)+" 条",false,()->{eventsLimit+=50;render(true);}));}}else if(!found)empty("暂无比赛进程",missing());}
    private void event(LinearLayout timeline,MatchDetail.Event event){LinearLayout row=ui.row();row.setGravity(Gravity.TOP);row.setPadding(0,ui.dp(12),0,ui.dp(12));TextView clock=ui.text(event.time(),11,ui.muted,true);row.addView(clock,new LinearLayout.LayoutParams(ui.dp(56),-2));LinearLayout words=ui.column();String title=DetailParsers.plain(event.title()),text=DetailParsers.plain(event.text());if(!title.isBlank()){words.addView(ui.text(title,13,ui.text,true));ui.gap(words,4);}if(!text.isBlank())words.addView(ui.text(text,13,title.isBlank()?ui.text:ui.muted,false));row.addView(words,new LinearLayout.LayoutParams(0,-2,1));timeline.addView(row);timeline.addView(ui.divider());}
    private void lol(){LolMatchView view=new LolMatchView(ui,detail,body,number->{selectedGame=number;if(tab.equals("overview"))tab="lineup";render(false);});if(tab.equals("overview")){view.overview();information();sourceLink();}else view.games(selectedGame,tab.equals("stats"));}
    private String missing(){return match.scheduled()?"赛前数据尚未发布，确认阵容和统计会随来源更新。":"来源暂未提供本项数据，空缺不代表 0。";}
    private void empty(String title,String message){ui.gap(body,24);LinearLayout card=panel();card.addView(ui.text(title,18,ui.text,true));ui.gap(card,10);card.addView(ui.text(message,14,ui.muted,false));body.addView(card);}
    private static String time(long value,String pattern){return value>0?DateTimeFormatter.ofPattern(pattern,Locale.CHINA).withZone(Match.ZONE).format(Instant.ofEpochMilli(value)):"尚未提供";}
}
