package cn.myscore.app;

import android.app.Activity;
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
    private String tab="overview",error="";private boolean busy,active;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Runnable tick=()->load(false);
    private String rendered="";
    private long lastScroll;
    private final Runnable update=()->{if(active&&!isDestroyed())render(true);};
    @Override public void onCreate(Bundle saved){
        String theme=getSharedPreferences("settings",0).getString("theme","system");boolean dark=theme.equals("dark")||theme.equals("system")&&(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        setTheme(dark?R.style.AppThemeDark:R.style.AppTheme);super.onCreate(saved);ui=new Ui(this,dark);
        try{match=Match.fromJson(new JSONObject(getIntent().getStringExtra("match")));if(match.id.isBlank())throw new IllegalArgumentException();}catch(Exception e){finish();return;}
        if(saved!=null)tab=saved.getString("tab","overview");if(match.group.equals("F1")&&tab.equals("events"))tab="overview";repository=new DetailRepository(this);
        getWindow().setStatusBarColor(ui.bg);getWindow().setNavigationBarColor(ui.bg);if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        root=ui.column();root.setBackgroundColor(ui.bg);setContentView(root);root.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(i.left,i.top,i.right,i.bottom);}else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});root.requestApplyInsets();render(false);
    }
    @Override public void onResume(){super.onResume();active=true;if(match!=null)load(false);}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)Ui.highRefresh(getWindow());}
    @Override public void onPause(){active=false;handler.removeCallbacks(tick);handler.removeCallbacks(update);super.onPause();}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("tab",tab);}
    private void load(boolean force){
        if(force){Logos.retryFailures();rendered="";}
        handler.removeCallbacks(tick);if(busy||!active)return;busy=true;if(status!=null)status.setText("正在更新详情…");
        Match requested=match;boolean needsCache=detail==null;worker.submit(()->{
            if(needsCache){MatchDetail cached=repository.cached(requested);if(cached!=null)handler.post(()->{if(isDestroyed())return;detail=cached;match=cached.match;if(active)render(true);});}
            MatchDetail next=null;String problem="";try{next=repository.fetch(requested,force);}catch(Exception e){problem=e.getMessage()==null?"网络连接失败":e.getMessage();}MatchDetail result=next;String failure=problem;handler.post(()->{busy=false;if(isDestroyed())return;error=failure;if(result!=null){detail=result;match=result.match;}if(active){render(true);if(!match.finalised()&&!match.cancelled())handler.postDelayed(tick,match.group.equals("足球")||match.group.equals("NBA")||match.group.equals("NFL")?30_000:60_000);}});});
    }
    private FrameLayout iconButton(String kind,String description,Runnable click){FrameLayout b=new FrameLayout(this);b.addView(new Ui.Icon(this,kind,ui.text),new FrameLayout.LayoutParams(ui.dp(22),ui.dp(22),Gravity.CENTER));ui.clickable(b,ui.bg,24);b.setContentDescription(description);b.setFocusable(true);b.setOnClickListener(v->click.run());b.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));return b;}
    private void render(boolean preserve){
        if(isFinishing()||isDestroyed())return;
        if(preserve&&android.os.SystemClock.uptimeMillis()-lastScroll<200){handler.removeCallbacks(update);handler.postDelayed(update,200);return;}
        String key=detail==null?"empty:"+busy:detailKey();
        if(preserve&&rendered.equals(key)){if(status!=null)status.setText(statusText());return;}
        rendered=key;
        int y=preserve&&scroll!=null?scroll.getScrollY():0;root.removeAllViews();
        LinearLayout appbar=ui.row();appbar.setPadding(ui.dp(4),ui.dp(4),ui.dp(4),0);appbar.addView(iconButton("back","返回比赛列表",this::finish));Competition competition=Competition.find(match.league);TextView title=ui.text(competition==null?match.group:competition.name,16,ui.text,true);title.setGravity(Gravity.CENTER);appbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));appbar.addView(iconButton("refresh","刷新比赛详情",()->load(true)));root.addView(appbar);
        scoreHeader();tabs();root.addView(ui.divider());
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);scroll.setOnScrollChangeListener((v,x,sy,ox,oy)->lastScroll=SystemClock.uptimeMillis());body=ui.column();body.setPadding(ui.dp(20),ui.dp(16),ui.dp(20),ui.dp(28));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(!error.isEmpty()){TextView problem=ui.text("更新失败 · "+error+(detail==null?"":"\n正在显示缓存详情"),13,ui.error,false);body.addView(problem);ui.gap(body,10);body.addView(ui.button("重新加载详情",false,()->load(true)));ui.gap(body,20);}
        status=ui.text(statusText(),11,ui.muted,false);body.addView(status);
        if(detail!=null&&!detail.warning.isBlank()){ui.gap(body,8);body.addView(ui.text(detail.warning,12,ui.muted,false));}
        if(detail==null){empty(busy?"正在读取比赛数据":"比赛详情暂不可用",busy?"将显示来源提供的真实阵容、统计和比赛进程。":"请检查网络并重新加载。比分列表仍保留最近的来源记录。");}
        else if(tab.equals("overview"))overview();else if(tab.equals("stats"))statistics();else if(tab.equals("events"))events();else tables("lineup");
        if(preserve)scroll.post(()->scroll.scrollTo(0,y));
    }
    private String statusText(){return detail==null?(busy?"正在读取详情…":"尚未获取详情"):(System.currentTimeMillis()-detail.fetched>120_000?"缓存 · ":"")+detail.source+" · "+time(detail.fetched,"M月d日 HH:mm:ss")+" 更新";}
    private String detailKey(){try{org.json.JSONObject value=detail.json();value.remove("fetched");org.json.JSONObject score=value.optJSONObject("match");if(score!=null)score.remove("updated");return tab+":"+error+":"+value;}catch(org.json.JSONException e){return tab+":"+error+":"+detail.fetched;}}
    private void scoreHeader(){
        LinearLayout hero=ui.column();hero.setPadding(ui.dp(20),ui.dp(10),ui.dp(20),ui.dp(14));
        TextView state=ui.text(match.status+(match.timeConfirmed?" · "+time(match.start,"M月d日 HH:mm"):" · 时间待定"),12,ui.muted,true);state.setGravity(Gravity.CENTER);hero.addView(state);ui.gap(hero,14);
        if(match.group.equals("F1")){TextView title=ui.text(match.displayTitle(),22,ui.text,true);title.setGravity(Gravity.CENTER);hero.addView(title);}
        else {LinearLayout sides=ui.row();sides.setGravity(Gravity.CENTER);sides.addView(team(match.home,match.homeLogo),new LinearLayout.LayoutParams(0,-2,1));TextView score=ui.text(match.hasScore()?match.homeScore+" : "+match.awayScore:"VS",match.hasScore()?36:25,ui.text,true);score.setGravity(Gravity.CENTER);score.setPadding(ui.dp(12),0,ui.dp(12),0);sides.addView(score,new LinearLayout.LayoutParams(-2,-2));sides.addView(team(match.away,match.awayLogo),new LinearLayout.LayoutParams(0,-2,1));hero.addView(sides);}
        root.addView(hero);
    }
    private LinearLayout team(String name,String logo){LinearLayout side=ui.column();side.setGravity(Gravity.CENTER);side.addView(Logos.view(ui,logo,name,44));ui.gap(side,8);TextView t=ui.text(name,13,ui.text,true);t.setGravity(Gravity.CENTER);side.addView(t,new LinearLayout.LayoutParams(-1,-2));return side;}
    private String lineupName(){return switch(match.group){case "足球","LOL"->"阵容";case "F1"->"车手";case "CS2"->"选手";default->"球员";};}
    private void tabs(){LinearLayout row=ui.row();boolean f1=match.group.equals("F1");String[] keys=f1?new String[]{"overview","lineup","stats"}:new String[]{"overview","lineup","stats","events"};String[] names=f1?new String[]{"总览",lineupName(),"成绩"}:new String[]{"总览",lineupName(),"统计",match.group.equals("CS2")||match.group.equals("LOL")?"小局":"进程"};for(int i=0;i<keys.length;i++){String key=keys[i];LinearLayout item=ui.column();item.setGravity(Gravity.CENTER);ui.clickable(item,ui.bg,0);item.setFocusable(true);item.setSelected(tab.equals(key));item.setContentDescription(names[i]+(tab.equals(key)?"，已选中":""));TextView text=ui.text(names[i],14,tab.equals(key)?ui.text:ui.muted,tab.equals(key));text.setGravity(Gravity.CENTER);text.setMinHeight(ui.dp(48));item.addView(text,new LinearLayout.LayoutParams(-1,-2));View line=new View(this);line.setBackgroundColor(tab.equals(key)?ui.text:ui.bg);item.addView(line,new LinearLayout.LayoutParams(ui.dp(28),ui.dp(2)));item.setOnClickListener(v->{tab=key;render(false);});row.addView(item,new LinearLayout.LayoutParams(0,-2,1));}root.addView(row);}
    private void heading(String s){ui.gap(body,22);TextView title=ui.text(s,16,ui.text,true);body.addView(title);ui.gap(body,12);}
    private void overview(){
        tables("overview");heading("比赛信息");for(MatchDetail.Fact fact:detail.facts)fact(fact.label(),fact.value());
        if(!match.detail.isBlank()&&detail.facts.size()<2)fact("赛事信息",match.detail);
        if(!detail.stats.isEmpty()){heading("主要统计");for(int i=0;i<Math.min(5,detail.stats.size());i++)stat(detail.stats.get(i));body.addView(ui.button("查看全部技术统计",false,()->{tab="stats";render(false);}));}
        if(!detail.events.isEmpty()){heading("最新进程");for(int i=0;i<Math.min(3,detail.events.size());i++)event(detail.events.get(i));}
        ui.gap(body,22);body.addView(ui.text("比分与统计依来源发布，可能存在延迟。空缺表示尚未提供。",12,ui.muted,false));ui.gap(body,12);if(!match.url.isBlank())body.addView(ui.button("在来源网站查看",false,()->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(match.url)));}catch(android.content.ActivityNotFoundException e){status.setText("手机没有可用的浏览器");}}));
    }
    private void fact(String label,String value){if(label.length()>6){LinearLayout block=ui.column();block.setPadding(0,ui.dp(10),0,ui.dp(10));block.addView(ui.text(label,12,ui.muted,false));ui.gap(block,5);block.addView(ui.text(value,14,ui.text,true));body.addView(block);}else{LinearLayout row=ui.row();row.setGravity(Gravity.TOP);row.setPadding(0,ui.dp(9),0,ui.dp(9));TextView l=ui.text(label,13,ui.muted,false);row.addView(l,new LinearLayout.LayoutParams(ui.dp(80),-2));TextView v=ui.text(value,14,ui.text,false);row.addView(v,new LinearLayout.LayoutParams(0,-2,1));body.addView(row);}body.addView(ui.divider());}
    private void statistics(){if(!detail.stats.isEmpty()){heading("球队技术统计");LinearLayout names=ui.row();TextView home=ui.text(match.home,12,ui.muted,true),away=ui.text(match.away,12,ui.muted,true);away.setGravity(Gravity.END);names.addView(home,new LinearLayout.LayoutParams(0,-2,1));names.addView(away,new LinearLayout.LayoutParams(0,-2,1));body.addView(names);ui.gap(body,14);for(MatchDetail.Stat s:detail.stats)stat(s);}boolean has=tables("stats");if(!has&&detail.stats.isEmpty()){if(match.group.equals("CS2")){empty("选手统计在“选手”分栏", "包含全场及各图的 K/D/A、ADR、KAST 和 Rating。");body.addView(ui.button("查看选手统计",false,()->{tab="lineup";render(false);}));}else empty("暂无本场技术统计",missing());}}
    private void stat(MatchDetail.Stat s){LinearLayout row=ui.row();TextView h=ui.text(s.home(),16,ui.text,true),label=ui.text(s.label(),12,ui.muted,false),a=ui.text(s.away(),16,ui.text,true);h.setGravity(Gravity.START);a.setGravity(Gravity.END);label.setGravity(Gravity.CENTER);row.addView(h,new LinearLayout.LayoutParams(0,-2,1));row.addView(label,new LinearLayout.LayoutParams(0,-2,2));row.addView(a,new LinearLayout.LayoutParams(0,-2,1));body.addView(row);ui.gap(body,8);double hv=numeric(s.home()),av=numeric(s.away());if(hv>=0&&av>=0){if(hv+av>0){LinearLayout bar=ui.row();bar.addView(track(ui.text),new LinearLayout.LayoutParams(0,ui.dp(3),(float)hv));bar.addView(track(ui.tonal),new LinearLayout.LayoutParams(0,ui.dp(3),(float)av));body.addView(bar);}else body.addView(track(ui.line),new LinearLayout.LayoutParams(-1,ui.dp(3)));}else body.addView(ui.divider());ui.gap(body,18);}
    private View track(int color){View v=new View(this);v.setBackground(ui.shape(color,2));return v;}
    private static double numeric(String s){try{if(!s.matches("[0-9]+(?:\\.[0-9]+)?%?"))return -1;return Double.parseDouble(s.replace("%",""));}catch(RuntimeException e){return -1;}}
    private boolean tables(String key){boolean has=false;java.util.List<MatchDetail.Table> ordered=new ArrayList<>(detail.tables);if(key.equals("stats"))ordered.sort(Comparator.comparingInt(t->t.title.contains("出装")?1:0));for(MatchDetail.Table t:ordered)if(t.tab.equals(key)){has=true;heading(t.title);table(t);}if(!has&&key.equals("lineup"))empty("暂无本场"+lineupName()+"数据",missing());return has;}
    private void table(MatchDetail.Table table){
        int cols=table.columns.size();boolean gear=cols==2&&table.columns.get(1).equals("装备");
        if(gear){for(java.util.List<String> r:table.rows){LinearLayout block=ui.column();block.setPadding(ui.dp(14),ui.dp(12),ui.dp(14),ui.dp(12));block.setBackground(ui.shape(ui.surface,10));block.addView(ui.text(r.get(0),14,ui.text,true));ui.gap(block,7);block.addView(ui.text(r.size()>1?r.get(1):"—",13,ui.muted,false));body.addView(block);ui.gap(body,6);}return;}
        if(cols>=6){blockTable(table);return;}
        LinearLayout data=ui.column();data.setBackground(ui.shape(ui.surface,10));
        int avail=getResources().getDisplayMetrics().widthPixels-ui.dp(40)-ui.dp(24);
        int[] widths=fitWidths(table,avail);
        addTableRow(data,table.columns,widths,true);for(java.util.List<String> row:table.rows){data.addView(ui.divider());addTableRow(data,row,widths,false);}
        body.addView(data,new LinearLayout.LayoutParams(-1,-2));
    }
    private int[] fitWidths(MatchDetail.Table table,int avail){
        int cols=table.columns.size();int[] need=new int[cols];
        for(int i=0;i<cols;i++){int n=textWidth(table.columns.get(i));for(java.util.List<String> r:table.rows)if(i<r.size())n=Math.max(n,textWidth(r.get(i)));need[i]=Math.max(ui.dp(38),n);}
        int total=0;for(int n:need)total+=n;int[] widths=new int[cols];
        for(int i=0;i<cols;i++)widths[i]=Math.max(ui.dp(32),need[i]*avail/Math.max(1,total));
        return widths;
    }
    private int textWidth(String s){if(s==null||s.isEmpty())return 0;int w=0;for(int i=0;i<s.length();i++){char ch=s.charAt(i);w+=ch>0x2E80?ui.dp(13):ui.dp(7);}return w+ui.dp(8);}
    private void blockTable(MatchDetail.Table table){
        int cols=table.columns.size();
        for(java.util.List<String> row:table.rows){
            LinearLayout block=ui.column();block.setPadding(ui.dp(14),ui.dp(12),ui.dp(14),ui.dp(12));block.setBackground(ui.shape(ui.surface,10));
            block.addView(ui.text(row.isEmpty()?"—":row.get(0),14,ui.text,true));ui.gap(block,8);
            LinearLayout grid=ui.column();
            for(int i=1;i<cols;i+=2){
                LinearLayout line=ui.row();line.setGravity(Gravity.TOP);
                line.addView(field(table.columns.get(i),i<row.size()?row.get(i):"—"),new LinearLayout.LayoutParams(0,-2,1));
                if(i+1<cols)line.addView(field(table.columns.get(i+1),i+1<row.size()?row.get(i+1):"—"),new LinearLayout.LayoutParams(0,-2,1));else line.addView(new View(this),new LinearLayout.LayoutParams(0,-2,1));
                grid.addView(line);if(i+2<cols)ui.gap(grid,6);
            }
            block.addView(grid);body.addView(block);ui.gap(body,6);
        }
    }
    private LinearLayout field(String label,String value){LinearLayout f=ui.column();f.addView(ui.text(label,11,ui.muted,false));ui.gap(f,2);f.addView(ui.text(value==null||value.isBlank()?"—":value,13,ui.text,false));return f;}
    private void addTableRow(LinearLayout into,java.util.List<String> values,int[] widths,boolean header){LinearLayout row=ui.row();row.setGravity(Gravity.TOP);row.setPadding(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(12));for(int i=0;i<widths.length;i++){TextView cell=ui.text(i<values.size()?values.get(i):"—",header?11:13,header?ui.muted:ui.text,!header&&i==0);cell.setPadding(i==0?0:ui.dp(4),0,0,0);cell.setGravity(i==0?Gravity.START:Gravity.END);cell.setMaxLines(header?2:3);row.addView(cell,new LinearLayout.LayoutParams(widths[i],-2));}into.addView(row,new LinearLayout.LayoutParams(-1,-2));}
    private void events(){boolean tables=tables("events");if(!detail.events.isEmpty()){heading(match.group.equals("足球")?"关键事件":match.group.equals("NFL")?"得分进程":"比赛进程");for(MatchDetail.Event e:detail.events)event(e);}else if(!tables)empty("暂无比赛进程",missing());}
    private void event(MatchDetail.Event e){LinearLayout row=ui.row();row.setGravity(Gravity.TOP);row.setPadding(0,ui.dp(12),0,ui.dp(12));TextView clock=ui.text(e.time(),12,ui.muted,true);row.addView(clock,new LinearLayout.LayoutParams(ui.dp(65),-2));LinearLayout words=ui.column();words.addView(ui.text(e.title(),14,ui.text,true));if(!e.text().isBlank()){ui.gap(words,5);words.addView(ui.text(e.text(),13,ui.muted,false));}row.addView(words,new LinearLayout.LayoutParams(0,-2,1));body.addView(row);body.addView(ui.divider());}
    private String missing(){return match.scheduled()?"赛前数据尚未发布；确认阵容和比赛统计会随来源更新。":match.group.equals("F1")&&tab.equals("events")?"当前 F1 来源提供车手成绩和统计，不提供逐圈事件流。请查看车手与统计分栏。":match.group.equals("LOL")?"本场小局数据尚未发布或暂不可用；仅展示已结束小局，不推算当前阵容。":"来源暂未提供此项数据，空缺不代表 0。可刷新或在来源网站核对。";}
    private void empty(String title,String message){ui.gap(body,30);body.addView(ui.text(title,18,ui.text,true));ui.gap(body,10);body.addView(ui.text(message,14,ui.muted,false));}
    private static String time(long t,String pattern){return t>0?DateTimeFormatter.ofPattern(pattern,Locale.CHINA).withZone(Match.ZONE).format(Instant.ofEpochMilli(t)):"尚未获取";}
}
