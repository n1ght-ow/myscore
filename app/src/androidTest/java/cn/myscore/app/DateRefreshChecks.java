package cn.myscore.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.SystemClock;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Physical-device safe: real repository reads only, no calendar writes or synthetic caches. */
final class DateRefreshChecks {
    private static int checks;
    static int run(Instrumentation test,Activity activity)throws Exception {
        checks=0;
        await(test,()->!bool(activity,"busy")&&!bool(activity,"selectionLoading"),40000,"initial scores settle");
        int originalDay=field("dayOffset").getInt(activity);String originalGroup=(String)field("group").get(activity);
        ExecutorService executor=(ExecutorService)field("executor").get(activity);
        CountDownLatch blocked=new CountDownLatch(4),release=new CountDownLatch(1);
        List<Future<?>> blockers=new ArrayList<>();
        for(int i=0;i<4;i++)blockers.add(executor.submit(()->{blocked.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}}));
        try {
            check(blocked.await(5,TimeUnit.SECONDS),"foreground worker queue is controlled");
            select(test,activity,"NFL",originalDay+1);
            await(test,()->bool(activity,"busy")&&!bool(activity,"selectionLoading"),5000,"filtered day starts refresh");
            Future<?> first=(Future<?>)field("scoreRequest").get(activity);
            select(test,activity,"CS2",originalDay+1);
            check(field("scoreRequest").get(activity)==first&&!first.isCancelled(),"sport change keeps the in-flight all-source request");
            select(test,activity,"NFL",originalDay+2);
            check(first.isCancelled(),"date change cancels the obsolete date request");
            await(test,()->bool(activity,"busy")&&!bool(activity,"selectionLoading"),5000,"final filtered date starts refresh");
        }finally{release.countDown();for(Future<?> task:blockers)task.get(5,TimeUnit.SECONDS);}
        await(test,()->!bool(activity,"busy")&&!bool(activity,"selectionLoading"),40000,"all date sources settle");
        LocalDate target=Match.today(System.currentTimeMillis()).plusDays(originalDay+2);
        test.runOnMainSync(()->{try {
            Repository.Snapshot all=(Repository.Snapshot)field("allSnapshot").get(activity);
            check(target.equals(field("allSnapshotDay").get(activity)),"completed snapshot belongs to the final selected date");
            check(all!=null&&all.batches.size()==Competition.ALL.length,"NFL date change refreshes all nine competitions without opening All");
            Repository.Snapshot shown=(Repository.Snapshot)field("snapshot").get(activity);
            check(shown.batches.size()==1&&shown.batches.get(0).competition.id.equals("nfl"),"all-source refresh preserves the selected list filter");
            Method memory=MainActivity.class.getDeclaredMethod("snapshotFor",LocalDate.class,String.class);memory.setAccessible(true);
            Repository.Snapshot nba=(Repository.Snapshot)memory.invoke(activity,target,"NBA");
            check(nba!=null&&nba.batches.size()==1&&nba.batches.get(0).competition.id.equals("nba"),"unopened NBA list receives the new date snapshot");
        }catch(Exception e){throw new AssertionError(e);}});
        select(test,activity,originalGroup,originalDay);
        return checks;
    }
    private interface Condition{boolean get()throws Exception;}
    private static void await(Instrumentation test,Condition condition,long timeout,String description)throws Exception {
        long until=SystemClock.uptimeMillis()+timeout;boolean[] ready={false};
        while(!ready[0]&&SystemClock.uptimeMillis()<until){test.runOnMainSync(()->{try{ready[0]=condition.get();}catch(Exception e){throw new AssertionError(e);}});if(!ready[0])Thread.sleep(30);}
        check(ready[0],description);
    }
    private static void select(Instrumentation test,Activity activity,String group,int offset){test.runOnMainSync(()->{try{Method method=MainActivity.class.getDeclaredMethod("select",int.class,String.class,int.class);method.setAccessible(true);method.invoke(activity,0,group,offset);}catch(Exception e){throw new AssertionError(e);}});}
    private static Field field(String name)throws Exception{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field;}
    private static boolean bool(Activity activity,String name)throws Exception{return field(name).getBoolean(activity);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;android.util.Log.i("MyScoreDateTests","PASS "+message);}
}
