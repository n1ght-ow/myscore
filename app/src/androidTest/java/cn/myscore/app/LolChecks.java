package cn.myscore.app;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.Field;

/** Optional real-network acceptance. No synthetic roster or match is rendered. */
final class LolChecks {
    private static int checks;
    static int run(Instrumentation test,Context context)throws Exception {
        checks=0;Match requested=new Match();requested.id="qq:lol:13608";requested.league="lol";requested.group="LOL";
        MatchDetail actual=new DetailRepository(context).fetch(requested,true);
        require(actual.lolGames.size()==5,"real series supplies five distinct game snapshots");
        require(actual.lolGames.get(0).home.atRole("TOP")!=null&&actual.lolGames.get(0).away.atRole("TOP")!=null,"real lineup includes both top laners");
        Bitmap hero=Logos.load(context,actual.lolGames.get(0).away.atRole("TOP").icon());require(hero.getWidth()>0,"official champion artwork decodes on Android");hero.recycle();
        for(String theme:new String[]{"light","dark"}){
            context.getSharedPreferences("settings",0).edit().putString("theme",theme).commit();
            Activity activity=test.startActivitySync(new Intent(context,DetailActivity.class).putExtra("match",actual.match.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));test.waitForIdleSync();
            long until=android.os.SystemClock.uptimeMillis()+5000;
            while(field(test,activity,"detail")==null&&android.os.SystemClock.uptimeMillis()<until){Thread.sleep(40);test.waitForIdleSync();}
            require(field(test,activity,"detail")!=null,"real cache loads into native LoL page "+theme);
            click(test,activity,"比赛");require(has(test,activity,"双方阵容"),"paired lineup is reachable "+theme);require(has(test,activity,"目标与经济"),"objective section present "+theme);
            click(test,activity,"第 1 局");require(((Integer)field(test,activity,"selectedGame"))==1,"first game selected "+theme);
            require(has(test,activity,"C9 Thanatos")&&has(test,activity,"KC Canna"),"real opposing top laners stay paired "+theme);
            capture(test,context,"lol-match-"+theme);test.runOnMainSync(()->{View panel=activity.getWindow().getDecorView().findViewWithTag("lol-lineup");((ScrollView)read(activity,"scroll")).scrollTo(0,Math.max(0,panel.getTop()-Math.round(32*activity.getResources().getDisplayMetrics().density)));});capture(test,context,"lol-lineup-"+theme);test.runOnMainSync(()->((ScrollView)read(activity,"scroll")).fullScroll(View.FOCUS_DOWN));capture(test,context,"lol-lineup-bottom-"+theme);
            click(test,activity,"第 2 局");require(((Integer)field(test,activity,"selectedGame"))==2,"second game selected "+theme);
            Instrumentation.ActivityMonitor monitor=test.addMonitor(DetailActivity.class.getName(),null,false);test.runOnMainSync(activity::recreate);Activity recreated=test.waitForMonitorWithTimeout(monitor,8000);test.removeMonitor(monitor);require(recreated!=null,"LoL detail recreates "+theme);test.waitForIdleSync();require(((Integer)field(test,recreated,"selectedGame"))==2,"selected game survives recreation "+theme);
            long ready=android.os.SystemClock.uptimeMillis()+5000;while(field(test,recreated,"detail")==null&&android.os.SystemClock.uptimeMillis()<ready){Thread.sleep(40);test.waitForIdleSync();}
            click(test,recreated,"统计");require(has(test,recreated,"选手数据与出装"),"game-specific player stats present "+theme);capture(test,context,"lol-stats-"+theme);
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);test.waitForIdleSync();
        }
        return checks;
    }
    private static Object read(Activity activity,String name){try{Field field=DetailActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);}catch(Exception e){throw new AssertionError(e);}}
    private static Object field(Instrumentation test,Activity activity,String name){Object[] result=new Object[1];test.runOnMainSync(()->result[0]=read(activity,name));return result[0];}
    private static TextView find(View root,String text){if(root instanceof TextView&&text.contentEquals(((TextView)root).getText()))return (TextView)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){TextView found=find(g.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private static boolean has(Instrumentation test,Activity activity,String text){boolean[] result=new boolean[1];test.runOnMainSync(()->result[0]=find(activity.getWindow().getDecorView(),text)!=null);return result[0];}
    private static void click(Instrumentation test,Activity activity,String text){test.runOnMainSync(()->{View view=find(activity.getWindow().getDecorView(),text);require(view!=null,"control exists: "+text);while(view!=null&&!view.isClickable())view=view.getParent() instanceof View?(View)view.getParent():null;if(view==null)throw new AssertionError("No clickable ancestor");view.performClick();});test.waitForIdleSync();}
    private static void require(boolean result,String description){if(!result)throw new AssertionError(description);checks++;android.util.Log.i("MyScoreLolTests","PASS "+description);}
    private static void capture(Instrumentation test,Context context,String name)throws Exception {test.waitForIdleSync();Thread.sleep(900);Bitmap bitmap=test.getUiAutomation().takeScreenshot();require(bitmap!=null,"native screenshot "+name);File directory=new File(context.getExternalFilesDir(null),"review-lol");directory.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(directory,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
}
