package cn.myscore.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Handler;
import android.view.View;
import android.widget.ScrollView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Optional screenshot review using only the device's existing, real match caches. */
final class SpaceReview {
    static void run(Instrumentation test,Activity main)throws Exception{
        show(test,main,"five:cs2:csgo_mc_2399751","CS2","cs2","overview","cs2-overview");
        show(test,main,"five:cs2:csgo_mc_2399751","CS2","cs2","lineup","cs2-players");
        show(test,main,"five:cs2:csgo_mc_2399751","CS2","cs2","events","cs2-map");
        if(Ui.isPad(main)){
            show(test,main,"qq:lol:13641","LOL","lol","stats","lol-statistics");
            show(test,main,"espn:nfl:401873012:401873012","NFL","nfl","lineup","nfl-players");
        }
    }
    private static void show(Instrumentation test,Activity main,String id,String group,String league,String tab,String name)throws Exception{
        Match request=new Match();request.id=id;request.group=group;request.league=league;
        MatchDetail cached=new DetailRepository(test.getTargetContext()).cached(request);
        if(cached==null)throw new AssertionError("Review requires the real cached match "+id);
        Instrumentation.ActivityMonitor launch=test.addMonitor(DetailActivity.class.getName(),null,false);
        Intent intent=new Intent(main,DetailActivity.class).putExtra("match",cached.match.json().toString());
        test.runOnMainSync(()->main.startActivity(intent));
        Activity detail=test.waitForMonitorWithTimeout(launch,10000);test.removeMonitor(launch);
        if(detail==null)throw new AssertionError("Detail screen starts for "+id);
        test.waitForIdleSync();
        if(Ui.isPad(main)&&detail.getResources().getConfiguration().orientation!=Configuration.ORIENTATION_LANDSCAPE){
            Instrumentation.ActivityMonitor rotation=test.addMonitor(DetailActivity.class.getName(),null,false);Activity current=detail;
            test.runOnMainSync(()->current.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            Activity rotated=test.waitForMonitorWithTimeout(rotation,10000);test.removeMonitor(rotation);
            if(rotated==null)throw new AssertionError("Detail recreates in landscape");detail=rotated;test.waitForIdleSync();
        }
        Activity screen=detail;
        test.runOnMainSync(()->{try{
            // Freeze this review instance on its real cache, so refreshes cannot change screenshot geometry.
            set(screen,"active",false);((Handler)get(screen,"handler")).removeCallbacksAndMessages(null);
            set(screen,"match",cached.match);set(screen,"detail",cached);set(screen,"tab",tab);set(screen,"rendered","");
            Method render=DetailActivity.class.getDeclaredMethod("render",boolean.class);render.setAccessible(true);render.invoke(screen,false);
        }catch(Exception e){throw new AssertionError(e);}});
        test.waitForIdleSync();Thread.sleep(700);test.waitForIdleSync();
        test.runOnMainSync(()->invalidate(screen.getWindow().getDecorView()));test.waitForIdleSync();Thread.sleep(150);
        if(group.equals("LOL")&&screen.getResources().getConfiguration().screenWidthDp>=1000){
            View table=screen.getWindow().getDecorView().findViewWithTag("lol-stats-table");
            if(table==null||SpaceUsageChecks.findAll(table,"lol-stats-player").size()!=10)throw new AssertionError("Real LOL match shows both five-player teams");
        }
        capture(test,name);
        if(tab.equals("events")){
            test.runOnMainSync(()->{try{((ScrollView)get(screen,"scroll")).scrollTo(0,screen.getResources().getDisplayMetrics().heightPixels/3);}catch(Exception e){throw new AssertionError(e);}});
            test.waitForIdleSync();capture(test,name+"-lower");
        }
        test.runOnMainSync(screen::finish);test.waitForIdleSync();
    }
    private static Object get(Activity activity,String name)throws Exception{Field field=DetailActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);}
    private static void invalidate(View view){view.invalidate();if(view instanceof android.view.ViewGroup group)for(int i=0;i<group.getChildCount();i++)invalidate(group.getChildAt(i));}
    private static void set(Activity activity,String name,Object value)throws Exception{Field field=DetailActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(activity,value);}
    private static void capture(Instrumentation test,String name)throws Exception{
        Bitmap image;
        try(android.os.ParcelFileDescriptor fd=test.getUiAutomation().executeShellCommand("screencap -p");java.io.FileInputStream in=new java.io.FileInputStream(fd.getFileDescriptor())){image=android.graphics.BitmapFactory.decodeStream(in);}
        if(image==null)throw new AssertionError("Native screenshot "+name);
        boolean content=false;int first=image.getPixel(0,0);for(int y=0;y<image.getHeight();y+=Math.max(1,image.getHeight()/64))for(int x=0;x<image.getWidth();x+=Math.max(1,image.getWidth()/64))if(image.getPixel(x,y)!=first)content=true;
        if(!content){image.recycle();throw new AssertionError("Screenshot contains no visible UI: "+name);}
        File directory=new File(test.getTargetContext().getExternalFilesDir(null),"space-review");directory.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(directory,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
    }
}
