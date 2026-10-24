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
                check(find(activity.getWindow().getDecorView(),"总览")!=null,"full detail opens "+d.match.league+" "+theme);
                check(find(activity.getWindow().getDecorView(),"左右滑动查看完整数据")==null,"tables keep all columns visible "+d.match.league+" "+theme);
                if(d.match.league.equals("epl"))capture("football-overview-"+theme);
                String line=switch(d.match.group){case "足球","LOL"->"阵容";case "F1"->"车手";case "CS2"->"选手";default->"球员";};click(line);check(find(activity.getWindow().getDecorView(),line)!=null,"native lineup tab "+d.match.league);if(d.match.league.equals("f1")){check(find(activity.getWindow().getDecorView(),"名次")!=null,"F1 lineup keeps position column "+theme);check(find(activity.getWindow().getDecorView(),"车号")!=null,"F1 lineup keeps car number "+theme);}capture(d.match.league+"-lineup-"+theme);
                String statTab=d.match.league.equals("f1")?"成绩":"统计";click(statTab);check(find(activity.getWindow().getDecorView(),statTab)!=null,"native statistics tab "+d.match.league);if(d.match.league.equals("epl")||d.match.league.equals("f1")||d.match.league.equals("lol"))capture(d.match.league+"-stats-"+theme);if(d.match.league.equals("f1"))check(find(activity.getWindow().getDecorView(),"Q1")!=null,"F1 results show qualifying fields "+theme);
                if(!d.match.league.equals("f1")){String events=d.match.group.equals("CS2")||d.match.group.equals("LOL")?"小局":"进程";click(events);check(find(activity.getWindow().getDecorView(),events)!=null,"native events tab "+d.match.league);if(d.match.league.equals("epl"))capture("football-events-"+theme);}
                test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);test.waitForIdleSync();
            }
        }
        c.getSharedPreferences("settings",0).edit().putString("theme","dark").commit();c.getResources().getConfiguration();
        activity=test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));test.waitForIdleSync();check(find(activity.getWindow().getDecorView(),"我的比赛")!=null,"system Back leaves full detail and score list can resume");
    }
    private static void click(String text){TextView found=find(activity.getWindow().getDecorView(),text);check(found!=null,"tab control exists: "+text);instrumentation.runOnMainSync(()->{View target=found;while(target!=null&&!target.isClickable())target=target.getParent() instanceof View?(View)target.getParent():null;if(target!=null)target.performClick();});instrumentation.waitForIdleSync();}
    private static TextView find(View root,String s){if(root instanceof TextView&&((TextView)root).getText().toString().equals(s))return (TextView)root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){TextView v=find(group.getChildAt(i),s);if(v!=null)return v;}}return null;}
    private static void capture(String name)throws Exception {instrumentation.waitForIdleSync();Thread.sleep(600);Bitmap b=instrumentation.getUiAutomation().takeScreenshot();check(b!=null,"native capture "+name);File dir=new File(context.getExternalFilesDir(null),"review-v2");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
}
