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

/** Emulator review for the LOL pairing, CS2 team panels and photo-free football formation. */
public final class DesignReviewInstrumentation extends Instrumentation {
    private Activity activity;private int checks;private String label="review";
    @Override public void onCreate(Bundle args){super.onCreate(args);if(args!=null)label=args.getString("label","review");start();}
    @Override public void onStart(){
        Bundle report=new Bundle();int outcome=Activity.RESULT_OK;Context context=getTargetContext();
        String old=context.getSharedPreferences("settings",0).getString("theme","dark");
        try{
            require(android.os.Build.HARDWARE.equals("ranchu")||android.os.Build.HARDWARE.equals("goldfish"),"emulator only");
            for(String theme:List.of("dark","light")){
                context.getSharedPreferences("settings",0).edit().putString("theme",theme).commit();
                football(theme);
                cs2(theme);
                lol(theme);
            }
            report.putString("stream","\nPASS: "+checks+" design review checks.\n");
        }catch(Throwable e){outcome=Activity.RESULT_CANCELED;report.putString("stream","\nFAIL after "+checks+": "+e.toString()+"\n"+android.util.Log.getStackTraceString(e));}
        finally{context.getSharedPreferences("settings",0).edit().putString("theme",old).commit();}
        finish(outcome,report);
    }
    private void football(String theme)throws Exception{
        open("espn:epl:401878767:401878767","足球","epl","lineup",null,null);
        View pitch=findTag("football-pitch");require(pitch!=null,"football formation renders");
        require(findAll(pitch,"football-pitch-player").size()==11,"eleven numbered players");
        require(findTag("football-event-legend")!=null,"event legend renders");
        require(countImages(pitch)==0,"formation no longer depends on photos");
        require(findTag("football-performance-picker")==null,"perspective filter row removed");
        require(findTag("football-player-metric")==null&&findTag("football-list-metric")==null,"no per-player metric values");
        scroll(240);capture(theme+"-football-pitch");
        scroll(760);capture(theme+"-football-lower");
        scroll(1320);capture(theme+"-football-bench");
        main(()->{ViewGroup filter=(ViewGroup)findTag("football-team-filter");filter.getChildAt(1).performClick();});settle();
        require(hasDescription(findTag("football-pitch"),"进球"),"goal marker reaches the away formation");
        scroll(300);capture(theme+"-football-away");
    }
    private void cs2(String theme)throws Exception{
        MatchDetail detail=open("five:cs2:csgo_mc_2398756","CS2","cs2","lineup","Mirage",null);
        List<View> headers=findAll(activity.getWindow().getDecorView(),"cs2-team-header");
        require(headers.size()==2,"two CS2 team headers");
        require(headers.get(0).getContentDescription()!=null&&headers.get(0).getContentDescription().toString().contains(detail.match.home),"first panel is the home team");
        require(headers.get(1).getContentDescription()!=null&&headers.get(1).getContentDescription().toString().contains(detail.match.away),"second panel is the away team");
        List<View> tables=findAll(activity.getWindow().getDecorView(),"detail-data-table");
        require(tables.size()==2,"two stacked player tables");
        List<HorizontalScrollView> scrollers=new ArrayList<>();for(View table:tables)scrollers.add((HorizontalScrollView)table.findViewWithTag("detail-metrics-scroll"));
        main(()->scrollers.get(0).scrollTo(ui().dp(90),0));settle();
        require(Math.abs(scrollers.get(1).getScrollX()-scrollers.get(0).getScrollX())<=1,"team tables scroll together");
        main(()->{for(HorizontalScrollView scroller:scrollers)scroller.scrollTo(0,0);});settle();
        scroll(220);capture(theme+"-cs2-first");
        scroll(800);capture(theme+"-cs2-second");
    }
    private void lol(String theme)throws Exception{
        open("qq:lol:13608","LOL","lol","lineup",null,null);
        View away=findTag("lol-kda-away"),home=findTag("lol-kda-home");require(away!=null&&home!=null,"paired KDA rows");
        require(home.getWidth()==away.getWidth()&&home.getWidth()>0,"same KDA column width");
        require(findAll(activity.getWindow().getDecorView(),"lol-table-header").size()==1,"one table header for all roles");
        require(findAll(activity.getWindow().getDecorView(),"lol-role-label").size()==5,"five role labels split the pairs");
        scroll(560);capture(theme+"-lol-pairing");
        scroll(900);capture(theme+"-lol-pairing-lower");
    }
    private MatchDetail open(String id,String group,String league,String tab,String category,String team)throws Exception{
        MatchDetail cached=new DetailRepository(getTargetContext()).cached(request(id,group,league));require(cached!=null,"real cached "+id);
        activity=startActivitySync(new Intent(getTargetContext(),DetailActivity.class).putExtra("match",cached.match.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitForIdleSync();for(int i=0;i<80&&field("detail")==null;i++)Thread.sleep(100);require(field("detail")!=null,"detail loads");
        main(()->{try{set("tab",tab);if(category!=null)set("categoryFilter",category);if(team!=null)set("teamFilter",team);render(false);((ScrollView)get("scroll")).scrollTo(0,0);}catch(Exception e){throw new AssertionError(e);}});
        settle();return cached;
    }
    private Match request(String id,String group,String league){Match m=new Match();m.id=id;m.group=group;m.league=league;return m;}
    private void scroll(int dp)throws Exception{main(()->((ScrollView)get("scroll")).scrollTo(0,ui().dp(dp)));settle();}
    private Ui ui(){return new Ui(activity,Ui.isDark(activity));}
    private int countImages(View view){int count=view instanceof ImageView?1:0;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)count+=countImages(group.getChildAt(i));return count;}
    private boolean hasDescription(View view,String text){CharSequence description=view.getContentDescription();if(description!=null&&description.toString().contains(text))return true;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)if(hasDescription(group.getChildAt(i),text))return true;return false;}
    private List<View> findAll(View view,String tag){List<View> result=new ArrayList<>();if(tag.equals(view.getTag()))result.add(view);if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)result.addAll(findAll(group.getChildAt(i),tag));return result;}
    private View findTag(String tag){return activity.getWindow().getDecorView().findViewWithTag(tag);}
    private void capture(String name)throws Exception{settle();Bitmap bitmap=getUiAutomation().takeScreenshot();require(bitmap!=null,"screenshot");File folder=new File(getTargetContext().getExternalFilesDir(null),"design-review/"+label);folder.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    private Object field(String name)throws Exception{Field f=DetailActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private Object get(String name){try{return field(name);}catch(Exception e){throw new AssertionError(e);}}
    private void set(String name,Object value){try{Field f=DetailActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}catch(Exception e){throw new AssertionError(e);}}
    private void render(boolean preserve){try{Method m=DetailActivity.class.getDeclaredMethod("render",boolean.class);m.setAccessible(true);m.invoke(activity,preserve);}catch(Exception e){throw new AssertionError(e);}}
    private void settle()throws Exception{waitForIdleSync();Thread.sleep(450);waitForIdleSync();}
    private void require(boolean okay,String message){if(!okay)throw new AssertionError(message);checks++;}
    private void main(Runnable action){Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable t){error[0]=t;}});if(error[0]!=null)throw new AssertionError(error[0]);}
}
