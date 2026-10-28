package cn.myscore.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** No calendar writes, preference changes or synthetic cache entries. */
public final class TabletRegressionInstrumentation extends Instrumentation {
    private int checks;
    private boolean network;
    private boolean rotate;
    private boolean review;
    private String sourcesReport="";
    private String networkGroup="全部";
    @Override public void onCreate(Bundle args){super.onCreate(args);network=args!=null&&"true".equals(args.getString("network"));rotate=args!=null&&"true".equals(args.getString("rotate"));review=args!=null&&"true".equals(args.getString("review"));if(args!=null)networkGroup=args.getString("networkGroup","全部");start();}
    @Override public void runOnMainSync(Runnable action){
        Throwable[] error={null};super.runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});
        if(error[0]!=null)throw new AssertionError(error[0]);
    }
    @Override public void onStart(){
        Bundle report=new Bundle();int outcome=Activity.RESULT_OK;
        try{
            checks+=LayoutChecks.runSchedules(this,getTargetContext());
            checks+=SpaceUsageChecks.run(this,getTargetContext());
            checks+=AlignmentChecks.run(this,getTargetContext());
            runOnMainSync(this::adapterTransition);
            runOnMainSync(this::lolLayouts);
            // HyperOS may reject an instrumented app's background activity start. Launch as
            // the connected shell and use a bounded monitor instead of waiting indefinitely.
            Instrumentation.ActivityMonitor launch=addMonitor(MainActivity.class.getName(),null,false);
            try(android.os.ParcelFileDescriptor descriptor=getUiAutomation().executeShellCommand("am start -W -n cn.myscore.app/.MainActivity -f 0x10008000");
                java.io.FileInputStream output=new java.io.FileInputStream(descriptor.getFileDescriptor())){output.readAllBytes();}
            Activity activity=waitForMonitorWithTimeout(launch,10000);removeMonitor(launch);
            require(activity!=null,"main screen starts on the selected device");
            waitForIdleSync();Thread.sleep(700);waitForIdleSync();
            if(review)SpaceReview.run(this,activity);
            else tabletNavigation(activity);
            if(rotate&&Ui.isPad(activity)){
                if(activity.getResources().getConfiguration().orientation==Configuration.ORIENTATION_PORTRAIT)
                    activity=rotate(activity,android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                activity=rotate(activity,android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                tabletNavigation(activity);capture("portrait");
                activity=rotate(activity,android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                tabletNavigation(activity);capture("landscape");
                Activity restored=activity;runOnMainSync(()->restored.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));
            }
            if(network){
                StringBuilder sources=new StringBuilder();boolean success=true;
                java.time.LocalDate day=Match.today(System.currentTimeMillis());
                for(int offset:new int[]{0,1}){
                    Repository.Snapshot snapshot=new Repository(getTargetContext()).fetch(day.plusDays(offset),day.plusDays(offset),networkGroup,true);
                    for(Repository.Batch batch:snapshot.batches){sources.append(day.plusDays(offset)).append(' ').append(batch.competition.id).append(": ").append(batch.error.isEmpty()?"OK "+batch.matches.size():batch.error).append('\n');success&=batch.error.isEmpty();}
                }
                sourcesReport=sources.toString();report.putString("sources",sourcesReport);require(success,"physical-device source refresh completes for today and tomorrow");
            }
            report.putString("stream","\nPASS: "+checks+" tablet regression checks.\n"+sourcesReport);
        }catch(Throwable e){outcome=Activity.RESULT_CANCELED;report.putString("stream","\nFAIL after "+checks+": "+android.util.Log.getStackTraceString(e)+"\n"+sourcesReport);}
        finish(outcome,report);
    }
    private void adapterTransition(){
        Ui ui=new Ui(getTargetContext(),true);ScoreList list=new ScoreList(ui.c);
        PadScoreAdapter adapter=new PadScoreAdapter(ui,new PadScoreAdapter.Host(){
            public void selectMatch(Match m){}public void openMatch(Match m){}public void openTeam(Match m,boolean home){}
            public void toggleSubscription(Match m){}public void openWatch(Match m){}public WatchLinks.Link watchLink(Match m){return null;}
            public boolean isSubscribed(String id){return false;}public void showSourceStatus(){}public void resolveEmptySchedule(){}
        });
        adapter.attach(list);list.setAdapter(adapter);
        Match match=new Match();match.id="in-memory-only";match.group="NBA";match.league="nba";match.home="Home";match.away="Away";
        adapter.updateRows(List.of(ScoreRow.match(match)));layout(list,ui.dp(380),ui.dp(600));
        require(list.getChildAt(0) instanceof LinearLayout,"match row is attached before structural update");
        adapter.updateRows(List.of(ScoreRow.section("已结束"),ScoreRow.match(match)));
        adapter.setSelected(match.id);
        layout(list,ui.dp(380),ui.dp(600));
        require(list.getChildAt(0) instanceof TextView,"selection safely follows match-to-section replacement");
        require(list.getChildAt(1).findViewWithTag("watch-shortcut")!=null,"match row survives structural selection update");
        adapter.updateRows(List.of(ScoreRow.loading("正在读取赛程…")));adapter.setSelected("");
        layout(list,ui.dp(380),ui.dp(600));
        require(list.getChildAt(0) instanceof TextView,"loading replacement safely clears the selection");
    }
    private void lolLayouts(){
        for(int width:new int[]{280,320,360,560})for(float scale:new float[]{1f,1.3f,2f})for(boolean dark:new boolean[]{true,false}){
            Configuration config=new Configuration(getTargetContext().getResources().getConfiguration());config.fontScale=scale;config.screenWidthDp=width;
            Context context=getTargetContext().createConfigurationContext(config);Ui ui=new Ui(context,dark);
            Match match=new Match();match.id="memory-only-lol";match.group="LOL";match.league="lol";match.homeId="1";match.awayId="2";match.home="Home";match.away="Away";
            MatchDetail detail=new MatchDetail(match);
            try{
                JSONObject player=new JSONObject().put("playerName","Long Player Name").put("playerLocation","TOP").put("heroName","测试英雄");
                JSONArray teams=new JSONArray().put(new JSONObject().put("teamId","1").put("playerInfos",new JSONArray().put(player)))
                    .put(new JSONObject().put("teamId","2").put("playerInfos",new JSONArray().put(player)));
                detail.lolGames.add(LolGame.parse(new JSONObject().put("bo",1).put("matchStatus","2").put("teamInfos",teams),match));
            }catch(Exception e){throw new AssertionError(e);}
            LinearLayout body=ui.column();new LolMatchView(ui,detail,body,n->{}).games(1,false);
            layout(body,ui.dp(width),0);
            View panel=body.findViewWithTag("lol-lineup");require(panel!=null,"LOL lineup exists");
            inspectNames(panel,ui.dp(60));
            if(panel instanceof HorizontalScrollView horizontal){
                require(horizontal.getChildAt(0).getWidth()>=horizontal.getWidth(),"wide lineup can be scrolled without compressing columns");
            }
        }
    }
    private void inspectNames(View view,int minimum){
        if(view instanceof TextView text&&"Long Player Name".contentEquals(text.getText())){
            require(text.getWidth()>=minimum,"LOL player identity retains a readable width: "+text.getWidth()+" >= "+minimum);
            require(text.getLayout()!=null&&text.getLayout().getHeight()<=text.getHeight(),"LOL player name has no vertical clipping");
        }
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)inspectNames(group.getChildAt(i),minimum);
    }
    private void tabletNavigation(Activity activity)throws Exception{
        if(!field("pad").getBoolean(activity)){
            Method select=MainActivity.class.getDeclaredMethod("select",int.class,String.class,int.class);select.setAccessible(true);
            Object list=field("scoreList").get(activity),header=field("headerView").get(activity);
            int day=field("dayOffset").getInt(activity);String original=(String)field("group").get(activity);
            for(String group:new String[]{"NBA","NFL","F1","CS2","LOL","足球","全部"}){
                runOnMainSync(()->{try{
                    select.invoke(activity,0,group,day);
                    require(list!=null&&field("scoreList").get(activity)==list,"compact window retains its list while switching sport");
                    require(field("headerView").get(activity)==header,"compact window retains its header while switching sport");
                    View selected=findDescription((View)header,group+"，已选中");require(selected!=null&&selected.isSelected(),"compact filter follows "+group);
                }catch(Exception e){throw new AssertionError(e);}});waitForIdleSync();
            }
            runOnMainSync(()->{try{select.invoke(activity,0,original,day);}catch(Exception e){throw new AssertionError(e);}});return;
        }
        Method select=MainActivity.class.getDeclaredMethod("select",int.class,String.class,int.class);select.setAccessible(true);
        int day=field("dayOffset").getInt(activity);String original=(String)field("group").get(activity);
        for(String group:new String[]{"NBA","NFL","F1","CS2","LOL","足球","全部"}){
            runOnMainSync(()->{try{select.invoke(activity,0,group,day);}catch(Exception e){throw new AssertionError(e);}});waitForIdleSync();
            runOnMainSync(()->{try{
                View rail=(View)field("padRail").get(activity);
                require(countSelectedFilters(rail)==1,"exactly one sport filter is selected");
                View chosen=findDescription(rail,group+"，已选中");require(chosen!=null&&chosen.isSelected(),"rail follows selected sport "+group);
            }catch(Exception e){throw new AssertionError(e);}});
        }
        runOnMainSync(()->{try{select.invoke(activity,0,original,day);}catch(Exception e){throw new AssertionError(e);}});waitForIdleSync();Thread.sleep(600);waitForIdleSync();
        if(activity.getResources().getConfiguration().screenWidthDp - 128 < 850){
            runOnMainSync(()->{try{
                require(field("padFocusScroll").get(activity)==null,"narrow tablet has one full-height schedule, without a second empty/detail pane");
                View list=(View)field("padList").get(activity);
                require(list.getHeight()>activity.getWindow().getDecorView().getHeight()/2,"portrait schedule uses more than half the screen");
            }catch(Exception e){throw new AssertionError(e);}});
            return;
        }
        runOnMainSync(()->{try{
            ScrollView scroll=(ScrollView)field("padFocusScroll").get(activity);
            require(scroll!=null,"tablet reading pane exists");
            scroll.scrollTo(0,500);
            Method rebuild=MainActivity.class.getDeclaredMethod("rebuildPadFocus",boolean.class);rebuild.setAccessible(true);rebuild.invoke(activity,false);
        }catch(Exception e){throw new AssertionError(e);}});waitForIdleSync();
        runOnMainSync(()->{try{require(((ScrollView)field("padFocusScroll").get(activity)).getScrollY()==0,"a new reading selection starts at the top");}catch(Exception e){throw new AssertionError(e);}});
        runOnMainSync(()->{try{
            Ui.ReadingScroll scroll=(Ui.ReadingScroll)field("padFocusScroll").get(activity);
            long now=android.os.SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,20,20,0);
            scroll.dispatchTouchEvent(down);down.recycle();
            field("renderedPadFocus").set(activity,"pending-regression");
            Method rebuild=MainActivity.class.getDeclaredMethod("rebuildPadFocus",boolean.class);rebuild.setAccessible(true);rebuild.invoke(activity,true);
            require("pending-regression".equals(field("renderedPadFocus").get(activity)),"reading refresh waits while the user holds the pane");
            android.view.MotionEvent cancel=android.view.MotionEvent.obtain(now,now+1,android.view.MotionEvent.ACTION_CANCEL,20,20,0);
            scroll.dispatchTouchEvent(cancel);cancel.recycle();
        }catch(Exception e){throw new AssertionError(e);}});
        Thread.sleep(650);waitForIdleSync();
        runOnMainSync(()->{try{require(!"pending-regression".equals(field("renderedPadFocus").get(activity)),"reading refresh resumes after release");}catch(Exception e){throw new AssertionError(e);}});
    }
    private int countSelectedFilters(View view){
        int count=view.getContentDescription()!=null&&view.getContentDescription().toString().endsWith("，已选中")&&view.isSelected()&&!view.getContentDescription().toString().startsWith("比赛")?1:0;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)count+=countSelectedFilters(group.getChildAt(i));return count;
    }
    private View findDescription(View view,String text){if(view.getContentDescription()!=null&&text.contentEquals(view.getContentDescription()))return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=findDescription(group.getChildAt(i),text);if(found!=null)return found;}return null;}
    private Activity rotate(Activity activity,int orientation)throws Exception{
        String group=(String)field("group").get(activity);
        Instrumentation.ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
        runOnMainSync(()->activity.setRequestedOrientation(orientation));
        Activity recreated=waitForMonitorWithTimeout(monitor,10000);
        require(recreated!=null,"tablet recreates after orientation change");waitForIdleSync();Thread.sleep(1000);waitForIdleSync();
        if(monitor.getLastActivity()!=null)recreated=monitor.getLastActivity();removeMonitor(monitor);
        require(!recreated.isDestroyed(),"rotation leaves a live activity");
        sourcesReport+="orientation="+orientation+" config="+recreated.getResources().getConfiguration()+"\n";
        require(group.equals(field("group").get(recreated)),"selected sport survives rotation");return recreated;
    }
    private void capture(String name)throws Exception{
        waitForIdleSync();android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();require(bitmap!=null,"capture "+name);
        java.io.File directory=new java.io.File(getTargetContext().getExternalFilesDir(null),"device-regression");directory.mkdirs();
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(directory,name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
    }
    private static Field field(String name)throws Exception{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field;}
    private void layout(View view,int width,int height){view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,height>0?View.MeasureSpec.EXACTLY:View.MeasureSpec.UNSPECIFIED));view.layout(0,0,width,view.getMeasuredHeight());}
    private void require(boolean value,String description){if(!value)throw new AssertionError(description);checks++;}
}
