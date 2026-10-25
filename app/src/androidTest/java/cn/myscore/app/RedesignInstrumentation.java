package cn.myscore.app;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Native screen review using real cached matches; never writes sample match data. */
public final class RedesignInstrumentation extends Instrumentation {
    private Activity activity;
    private int checks;
    private String label;
    private boolean compact;
    @Override public void onCreate(Bundle args){super.onCreate(args);label=args==null?"phone":args.getString("label","phone");compact=args!=null&&"true".equals(args.getString("compact"));start();}
    @Override public void onStart(){
        Bundle report=new Bundle();int outcome=Activity.RESULT_OK;
        Context context=getTargetContext();String old=context.getSharedPreferences("settings",0).getString("theme","dark");
        try{
            require(android.os.Build.HARDWARE.equals("ranchu")||android.os.Build.HARDWARE.equals("goldfish"),"emulator only");
            for(String theme:new String[]{"dark","light"}){
                context.getSharedPreferences("settings",0).edit().putString("theme",theme).commit();
                open(MainActivity.class,null,null);capture(theme+"-home");
                click("设置");capture(theme+"-settings");
                click("外观");capture(theme+"-sheet");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();
                open(UpdateActivity.class,null,null);capture(theme+"-update");
                Team team=new Team();team.id="359";team.league="epl";team.name="Arsenal";
                open(TeamActivity.class,"team",team.json().toString());capture(theme+"-team");
                String[][] targets={{"epl","401879256"},{"nba","401909848"},{"nfl","401872996"},{"f1","600057427:401839031"},{"cs2","csgo_mc_2398756"},{"lol","13608"}};
                for(String[] target:targets){
                    Competition comp=Competition.find(target[0]);Match requested=new Match();requested.league=comp.id;requested.group=comp.group;
                    requested.id=comp.esports()?(comp.id.equals("cs2")?"five:cs2:":"qq:lol:")+target[1]:"espn:"+comp.id+":"+(comp.id.equals("f1")?target[1]:target[1]+":"+target[1]);
                    MatchDetail cached=new DetailRepository(context).cached(requested);require(cached!=null,"real cached match "+target[0]);
                    open(DetailActivity.class,"match",cached.match.json().toString());
                    long until=android.os.SystemClock.uptimeMillis()+5000;
                    while(field("detail")==null&&android.os.SystemClock.uptimeMillis()<until){Thread.sleep(60);waitForIdleSync();}
                    capture(theme+"-"+target[0]+"-overview");
                    require(((ScrollView)field("scroll")).getHeight()>=new Ui(context,theme.equals("dark")).dp(80),"detail keeps a usable scrolling viewport");
                    String lineup=switch(comp.group){case "足球"->"阵容";case "LOL"->"比赛";case "F1"->"车手";case "CS2"->"选手";default->"球员";};
                    click(lineup);capture(theme+"-"+target[0]+"-lineup");
                    if(!compact){click(comp.group.equals("F1")?"成绩":"统计");capture(theme+"-"+target[0]+"-stats");}
                }
            }
            report.putString("stream","\nPASS: "+checks+" native interface checks ("+label+").\n");
        }catch(Throwable e){outcome=Activity.RESULT_CANCELED;report.putString("stream","\nFAIL after "+checks+": "+android.util.Log.getStackTraceString(e));}
        finally{context.getSharedPreferences("settings",0).edit().putString("theme",old).commit();}
        finish(outcome,report);
    }
    private void open(Class<?> screen,String key,String value)throws Exception{
        Intent intent=new Intent(getTargetContext(),screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if(key!=null)intent.putExtra(key,value);activity=startActivitySync(intent);waitForIdleSync();Thread.sleep(500);waitForIdleSync();
    }
    private Object field(String name)throws Exception{Field f=DetailActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private void require(boolean okay,String message){if(!okay)throw new AssertionError(message);checks++;}
    private void click(String text){runOnMainSync(()->{View found=find(activity.getWindow().getDecorView(),text);if(found==null)throw new AssertionError("missing "+text);while(!found.isClickable()&&found.getParent() instanceof View)found=(View)found.getParent();if(!found.performClick())throw new AssertionError("cannot click "+text);});waitForIdleSync();}
    private View find(View view,String value){if(view instanceof TextView&&value.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=find(((ViewGroup)view).getChildAt(i),value);if(found!=null)return found;}return null;}
    private void capture(String name)throws Exception{
        waitForIdleSync();Thread.sleep(200);Throwable[] failure={null};
        runOnMainSync(()->{try{inspect(activity.getWindow().getDecorView());}catch(Throwable e){failure[0]=e;}});
        if(failure[0]!=null)throw new AssertionError(name,failure[0]);
        Bitmap screenshot=getUiAutomation().takeScreenshot();require(screenshot!=null,"screen capture "+name);
        File folder=new File(getTargetContext().getExternalFilesDir(null),"review-2.0/"+label);folder.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}screenshot.recycle();
    }
    private void inspect(View view){
        if(view.getVisibility()!=View.VISIBLE)return;
        if(view instanceof TextView text&&text.length()>0&&text.getWidth()>0){
            android.text.Layout layout=text.getLayout();require(layout!=null,"text has native layout: "+text.getText());
            require(layout.getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom(),"text height fits: "+text.getText());
            require(layout.getLineEnd(layout.getLineCount()-1)==text.length(),"complete text: "+text.getText());
            for(int i=0;i<layout.getLineCount();i++)require(layout.getEllipsisCount(i)==0&&layout.getLineMax(i)<=layout.getWidth()+1,"text width fits: "+text.getText());
        }
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)inspect(group.getChildAt(i));
    }
}
