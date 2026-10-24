package cn.myscore.app;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Periodic refresh of single-match subscriptions. It never imports unsubscribed fixtures. */
public final class CalendarJob extends JobService {
    private static final int ID=24701;
    private ExecutorService executor;
    private Future<?> task;
    public static void schedule(Context context) {
        JobScheduler scheduler=context.getSystemService(JobScheduler.class);
        if(scheduler==null)return;
        CalendarSync calendar=new CalendarSync(context);
        if(!CalendarSync.permitted(context)||!calendar.hasSubscriptions()) {scheduler.cancel(ID);return;}
        scheduler.schedule(new JobInfo.Builder(ID,new ComponentName(context,CalendarJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
            .setPeriodic(60*60_000L,15*60_000L).build());
    }
    @Override public boolean onStartJob(JobParameters params) {
        CalendarSync calendar=new CalendarSync(this);
        if(!CalendarSync.permitted(this)||!calendar.hasSubscriptions())return false;
        executor=Executors.newSingleThreadExecutor();
        task=executor.submit(()-> {
            boolean retry=false;
            try {
                CalendarSync.Result result=calendar.sync(Collections.emptyList(),System.currentTimeMillis());
                retry=result.failed>0;
            } catch(RuntimeException e) {retry=true;}
            finally {jobFinished(params,retry);if(executor!=null)executor.shutdown();}
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        if(task!=null)task.cancel(true);if(executor!=null)executor.shutdownNow();return true;
    }
}
