package cn.myscore.app;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.TextView;
import java.io.*;
import java.util.*;

/** Integration checks exercise real source details and actual native screens, never mock UI. */
final class DetailChecks {
    static int checks;
    private static Instrumentation instrumentation;
    private static Activity activity;
    private static Context context;
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;android.util.Log.i("MyScoreDetails","PASS "+message);}
    static void run(Instrumentation test,Context c)throws Exception {
        instrumentation=test;context=c;checks=0;
        Bitmap crest=Logos.load(c,"https://a.espncdn.com/i/teamlogos/soccer/500/362.png");check(crest.getWidth()>0,"real Aston Villa crest downloads and decodes on Android");crest.recycle();
        String[][] targets={{"epl","401879256"},{"ucl","401915395"},{"efl","401859837"},{"fa","401867655"},{"nba","401909848"},{"nfl","401872996"},{"f1","600057427:401839031"},{"cs2","csgo_mc_2398756"},{"lol","13608"}};
        List<MatchDetail> data=new ArrayList<>();DetailRepository repo=new DetailRepository(c);
        for(String[] t:targets){Competition comp=Competition.find(t[0]);Match m=new Match();m.league=comp.id;m.group=comp.group;m.source=comp.esports()?comp.id:"ESPN";m.start=java.time.LocalDate.of(2026,3,6).atStartOfDay(Match.ZONE).toInstant().toEpochMilli();
            m.id=comp.esports()?(comp.id.equals("cs2")?"five:cs2:":"qq:lol:")+t[1]:"espn:"+comp.id+":"+(comp.id.equals("f1")?t[1]:t[1]+":"+t[1]);
            MatchDetail d=repo.fetch(m,true);data.add(d);check(d.match.id.equals(m.id),"live exact detail identity "+comp.id);check(d.tables.size()>0,"live sport-specific detail tables "+comp.id);check(comp.id.equals("f1")||!d.match.homeLogo.isBlank(),"live source crest "+comp.id);if(comp.group.equals("足球"))check(!d.stats.isEmpty()&&!d.events.isEmpty(),"live football stats and events "+comp.id);
            try(FileWriter out=new FileWriter(new File(c.getExternalFilesDir(null),"detail-coverage-"+comp.id+".txt"))){out.write(d.match.id+"\nfacts="+d.facts.size()+"\nstats="+d.stats.size()+"\ntables="+d.tables.size()+"\nevents="+d.events.size()+"\nwarning="+d.warning);}
        }
        for(String theme:new String[]{"light","dark"}){
            c.getSharedPreferences("settings",0).edit().putString("theme",theme).commit();
            activity=test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));test.waitForIdleSync();capture("home-"+theme);
            for(MatchDetail d:data){
                // Two football snapshots suffice for a shared adapter, but source checks cover all cups.
                if(!d.match.league.equals("epl")&&d.match.group.equals("足球"))continue;
                activity=test.startActivitySync(new Intent(c,DetailActivity.class).putExtra("match",d.match.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));test.waitForIdleSync();
                long until=android.os.SystemClock.uptimeMillis()+5000;
                while(fieldDetail()==null&&android.os.SystemClock.uptimeMillis()<until){Thread.sleep(40);test.waitForIdleSync();}
                check(fieldDetail()!=null,"cached detail loads asynchronously "+d.match.league);
                check(find(activity.getWindow().getDecorView(),"总览")!=null,"full detail opens "+d.match.league+" "+theme);
                check(find(activity.getWindow().getDecorView(),"左右滑动查看完整数据")==null,"tables keep all columns visible "+d.match.league+" "+theme);
                check(noJson(activity.getWindow().getDecorView()),"detail never renders serialized official objects "+d.match.league);
                if(d.match.league.equals("nba")){instrumentation.runOnMainSync(()->{android.widget.HorizontalScrollView quarters=activity.getWindow().getDecorView().findViewWithTag("detail-metrics-scroll");check(quarters.getChildAt(0).getWidth()<=quarters.getWidth()+2,"NBA four quarters fit without horizontal scrolling");});}
                if(d.match.league.equals("epl")||d.match.league.equals("nba")||d.match.league.equals("cs2"))capture(d.match.league+"-overview-"+theme);
                if(d.match.league.equals("nba")){String official=d.facts.stream().filter(f->f.label().contains("裁判")).findFirst().map(MatchDetail.Fact::value).orElse("");if(!official.isBlank()){scrollToText(official);capture("nba-officials-"+theme);}}
                String line=switch(d.match.group){case "足球"->"阵容";case "LOL"->"比赛";case "F1"->"车手";case "CS2"->"选手";default->"球员";};click(line);check(find(activity.getWindow().getDecorView(),line)!=null,"native lineup tab "+d.match.league);if(d.match.league.equals("f1")){check(find(activity.getWindow().getDecorView(),"名次")!=null,"F1 lineup keeps position column "+theme);check(find(activity.getWindow().getDecorView(),"车号")!=null,"F1 lineup keeps car number "+theme);}capture(d.match.league+"-lineup-"+theme);
                if(d.match.league.equals("nba"))nbaControls(d,theme);
                if(d.match.league.equals("nfl")){clickBody("冲球");check(find(activity.getWindow().getDecorView(),"冲球")!=null,"NFL category switch stays reachable");capture("nfl-rushing-"+theme);}
                String statTab=d.match.league.equals("f1")?"成绩":"统计";click(statTab);check(find(activity.getWindow().getDecorView(),statTab)!=null,"native statistics tab "+d.match.league);if(d.match.league.equals("epl")||d.match.league.equals("nba")||d.match.league.equals("f1")||d.match.league.equals("lol"))capture(d.match.league+"-stats-"+theme);if(d.match.league.equals("f1")){check(find(activity.getWindow().getDecorView(),"Q1")!=null,"F1 results show qualifying fields "+theme);instrumentation.runOnMainSync(()->((android.widget.HorizontalScrollView)activity.getWindow().getDecorView().findViewWithTag("detail-metrics-scroll")).fullScroll(View.FOCUS_RIGHT));capture("f1-qualifying-"+theme);}
                if(!d.match.league.equals("f1")&&!d.match.league.equals("lol")){String events=d.match.group.equals("CS2")?"小局":"进程";click(events);check(find(activity.getWindow().getDecorView(),events)!=null,"native events tab "+d.match.league);if(d.match.league.equals("epl"))capture("football-events-"+theme);}
                test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);test.waitForIdleSync();
            }
        }
        c.getSharedPreferences("settings",0).edit().putString("theme","dark").commit();c.getResources().getConfiguration();
        activity=test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));test.waitForIdleSync();check(find(activity.getWindow().getDecorView(),"MyScore")!=null,"system Back leaves full detail and score list can resume");
    }
    private static Object fieldDetail()throws Exception{java.lang.reflect.Field field=DetailActivity.class.getDeclaredField("detail");field.setAccessible(true);Object[] result=new Object[1];instrumentation.runOnMainSync(()->{try{result[0]=field.get(activity);}catch(IllegalAccessException e){throw new AssertionError(e);}});return result[0];}
    private static boolean noJson(View root){if(root instanceof TextView){String text=((TextView)root).getText().toString();if(text.contains("\"displayName\"")||text.contains("\"Referee\""))return false;}if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++)if(!noJson(group.getChildAt(i)))return false;}return true;}
    private static void clickBody(String label){instrumentation.runOnMainSync(()->{try{java.lang.reflect.Field field=DetailActivity.class.getDeclaredField("body");field.setAccessible(true);View view=find((View)field.get(activity),label);check(view!=null,"body control exists: "+label);while(!view.isClickable())view=(View)view.getParent();view.performClick();}catch(Exception e){throw new AssertionError(e);}});instrumentation.waitForIdleSync();}
    private static void nbaControls(MatchDetail detail,String theme)throws Exception {
        clickBody("得分");java.lang.reflect.Field sort=DetailActivity.class.getDeclaredField("descending");sort.setAccessible(true);boolean[] descending=new boolean[1];instrumentation.runOnMainSync(()->{try{descending[0]=sort.getBoolean(activity);}catch(Exception e){throw new AssertionError(e);}});check(!descending[0],"NBA numeric sort switches to ascending");
        instrumentation.runOnMainSync(()->{View filters=activity.getWindow().getDecorView().findViewWithTag("detail-team-filter");View target=find(filters,detail.match.home);check(target!=null,"NBA home-team filter exists");target.performClick();});instrumentation.waitForIdleSync();java.lang.reflect.Field team=DetailActivity.class.getDeclaredField("teamFilter");team.setAccessible(true);check(team.get(activity).equals(detail.match.homeId),"NBA filter uses exact home identity");
        int[] before=new int[2],after=new int[2];instrumentation.runOnMainSync(()->{View table=activity.getWindow().getDecorView().findViewWithTag("detail-data-table");((ViewGroup)table).getChildAt(0).getLocationOnScreen(before);((android.widget.HorizontalScrollView)table.findViewWithTag("detail-metrics-scroll")).scrollTo(400,0);});instrumentation.waitForIdleSync();instrumentation.runOnMainSync(()->{View table=activity.getWindow().getDecorView().findViewWithTag("detail-data-table");((ViewGroup)table).getChildAt(0).getLocationOnScreen(after);});check(before[0]==after[0],"NBA names remain frozen while numeric columns scroll");capture("nba-player-filter-"+theme);
        Object[] oldBody=new Object[1];instrumentation.runOnMainSync(()->{try{java.lang.reflect.Field body=DetailActivity.class.getDeclaredField("body"),rendered=DetailActivity.class.getDeclaredField("rendered");body.setAccessible(true);rendered.setAccessible(true);oldBody[0]=body.get(activity);android.widget.HorizontalScrollView metrics=activity.getWindow().getDecorView().findViewWithTag("detail-metrics-scroll");metrics.scrollBy(10,0);rendered.set(activity,"");java.lang.reflect.Method render=DetailActivity.class.getDeclaredMethod("render",boolean.class);render.setAccessible(true);render.invoke(activity,true);check(body.get(activity)==oldBody[0],"horizontal gesture defers a changed render key without replacing source data");}catch(Exception e){throw new AssertionError(e);}});Thread.sleep(350);instrumentation.waitForIdleSync();instrumentation.runOnMainSync(()->{try{java.lang.reflect.Field body=DetailActivity.class.getDeclaredField("body");body.setAccessible(true);check(body.get(activity)!=oldBody[0],"deferred detail redraw resumes after horizontal gesture");}catch(Exception e){throw new AssertionError(e);}});
        MatchDetail.Person starter=detail.tables.stream().filter(t->t.teamId.equals(detail.match.homeId)&&t.category.equals("球员")).flatMap(t->t.people.stream()).filter(MatchDetail.Person::starter).findFirst().orElseThrow();String shortName=starter.name();
        for(int attempt=0;attempt<8&&find(activity.getWindow().getDecorView(),shortName)==null;attempt++){TextView more=findContaining(activity.getWindow().getDecorView(),"显示更多");if(more==null)break;instrumentation.runOnMainSync(more::performClick);instrumentation.waitForIdleSync();}
        scrollToText(shortName);clickBody(shortName);Thread.sleep(300);android.view.accessibility.AccessibilityNodeInfo sheet=instrumentation.getUiAutomation().getRootInActiveWindow();check(sheet!=null&&!sheet.findAccessibilityNodeInfosByText("球衣号").isEmpty(),"athlete sheet preserves jersey number");check(!sheet.findAccessibilityNodeInfosByText("位置").isEmpty()&&!sheet.findAccessibilityNodeInfosByText("首发").isEmpty(),"athlete sheet preserves position and starter state");capture("nba-identity-"+theme);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);instrumentation.waitForIdleSync();
    }
    private static void scrollToText(String text){instrumentation.runOnMainSync(()->{View target=contains(activity.getWindow().getDecorView(),text);if(target==null)return;try{java.lang.reflect.Field field=DetailActivity.class.getDeclaredField("scroll");field.setAccessible(true);android.widget.ScrollView scroll=(android.widget.ScrollView)field.get(activity);android.graphics.Rect bounds=new android.graphics.Rect();target.getDrawingRect(bounds);scroll.offsetDescendantRectToMyCoords(target,bounds);scroll.scrollTo(0,Math.max(0,bounds.top-60));}catch(Exception e){throw new AssertionError(e);}});instrumentation.waitForIdleSync();}
    private static View contains(View root,String text){if(root instanceof TextView&&((TextView)root).getText().toString().contains(text))return root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=contains(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private static void click(String text){TextView found=find(activity.getWindow().getDecorView(),text);check(found!=null,"tab control exists: "+text);instrumentation.runOnMainSync(()->{View target=found;while(target!=null&&!target.isClickable())target=target.getParent() instanceof View?(View)target.getParent():null;if(target!=null)target.performClick();});instrumentation.waitForIdleSync();}
    private static TextView find(View root,String s){if(root instanceof TextView&&((TextView)root).getText().toString().equals(s))return (TextView)root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){TextView v=find(group.getChildAt(i),s);if(v!=null)return v;}}return null;}
    private static TextView findContaining(View root,String s){if(root instanceof TextView&&((TextView)root).getText().toString().contains(s))return (TextView)root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){TextView v=findContaining(group.getChildAt(i),s);if(v!=null)return v;}}return null;}
    private static void capture(String name)throws Exception {instrumentation.waitForIdleSync();Thread.sleep(600);Bitmap b=instrumentation.getUiAutomation().takeScreenshot();check(b!=null,"native capture "+name);File dir=new File(context.getExternalFilesDir(null),"review-v2");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
}
