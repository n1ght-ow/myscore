package cn.myscore.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Regression checks for launcher packaging, refresh hints and retained native UI. */
final class UiChecks {
    static int run(Instrumentation test,Activity original)throws Exception {
        int checks=0;
        android.graphics.drawable.Drawable icon=original.getPackageManager().getApplicationIcon(original.getPackageName());
        require(icon instanceof android.graphics.drawable.AdaptiveIconDrawable,"installed launcher icon is adaptive");checks++;
        if(android.os.Build.VERSION.SDK_INT>=33){require(((android.graphics.drawable.AdaptiveIconDrawable)icon).getMonochrome()!=null,"themed launcher icon is present");checks++;}
        test.runOnMainSync(()->{
            Ui.highRefresh(original.getWindow());
            android.view.Display display=original.getWindow().getDecorView().getDisplay();
            android.view.Display.Mode current=display.getMode();float highest=current.getRefreshRate();
            for(android.view.Display.Mode mode:display.getSupportedModes())if(mode.getPhysicalWidth()==current.getPhysicalWidth()&&mode.getPhysicalHeight()==current.getPhysicalHeight())highest=Math.max(highest,mode.getRefreshRate());
            require(Math.abs(original.getWindow().getAttributes().preferredRefreshRate-highest)<0.1f,"window requests highest supported refresh at current resolution");
            require(original.getWindow().getAttributes().preferredDisplayModeId==0,"refresh hint does not force a resolution mode");
        });checks+=2;
        Method render=MainActivity.class.getDeclaredMethod("render",boolean.class);render.setAccessible(true);
        Field scoreList=field("scoreList"),scoreAdapter=field("scoreAdapter"),headerView=field("headerView");
        test.runOnMainSync(()->{try{
            render.invoke(original,true);
            Object previousList=scoreList.get(original),previousAdapter=scoreAdapter.get(original),previousHeader=headerView.get(original);
            View first=((ViewGroup)previousList).getChildAt(0);
            render.invoke(original,true);
            require(scoreList.get(original)==previousList,"automatic update retains the recycled scrolling list");
            require(scoreAdapter.get(original)==previousAdapter,"automatic update retains its adapter");
            require(headerView.get(original)==previousHeader,"automatic update retains the collapsing header");
            require(((ViewGroup)previousList).getChildAt(0)==first,"unchanged data retains visible rows");
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}});checks+=4;
        test.runOnMainSync(()->click(original.getWindow().getDecorView(),"设置"));test.waitForIdleSync();
        Bundle saved=new Bundle();test.runOnMainSync(()->test.callActivityOnSaveInstanceState(original,saved));
        require(saved.getInt("tab")==1&&saved.getInt("navigation_schema")==2,"settings state uses the two-tab schema");checks++;
        Instrumentation.ActivityMonitor monitor=test.addMonitor(MainActivity.class.getName(),null,false);
        test.runOnMainSync(original::recreate);
        Activity recreated=test.waitForMonitorWithTimeout(monitor,8000);test.removeMonitor(monitor);
        require(recreated!=null,"activity recreates");test.waitForIdleSync();checks++;
        require(hasText(test,recreated,"单场订阅"),"settings remains selected after recreation");checks++;
        test.runOnMainSync(()->click(recreated.getWindow().getDecorView(),"比赛"));test.waitForIdleSync();
        require(hasText(test,recreated,"MyScore"),"matches remain reachable after recreation");checks++;
        return checks;
    }
    private static Field field(String name)throws Exception{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field;}
    private static boolean hasText(Instrumentation test,Activity activity,String text){boolean[] visible=new boolean[1];test.runOnMainSync(()->visible[0]=find(activity.getWindow().getDecorView(),text)!=null);return visible[0];}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);android.util.Log.i("MyScoreUiTests","PASS "+message);}
    private static TextView find(View view,String text){if(view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return(TextView)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){TextView result=find(group.getChildAt(i),text);if(result!=null)return result;}}return null;}
    private static void click(View root,String text){View view=find(root,text);require(view!=null,"control exists: "+text);while(!view.isClickable()&&view.getParent() instanceof View)view=(View)view.getParent();view.performClick();}
}
