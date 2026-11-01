package cn.myscore.app;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Retains a local edit across process death; Shizuku permission is granted in the foreground. */
public final class CalendarCloudJob extends JobService {
    private static final int ID=24704;
    private volatile int generation;
    static void schedule(Context context) {
        JobScheduler scheduler=context.getSystemService(JobScheduler.class);
        if(scheduler!=null)scheduler.schedule(new JobInfo.Builder(ID,new ComponentName(context,CalendarCloudJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setMinimumLatency(3000)
            .setBackoffCriteria(30_000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
    }
    static void cancel(Context context) {
        JobScheduler scheduler=context.getSystemService(JobScheduler.class);if(scheduler!=null)scheduler.cancel(ID);
    }
    @Override public boolean onStartJob(JobParameters params) {
        if(!CalendarSync.permitted(this))return false;
        int run=++generation;
        CalendarCloudSync.retry(this,ok->getMainExecutor().execute(()->{if(generation==run)jobFinished(params,!ok);}));
        return true;
    }
    @Override public boolean onStopJob(JobParameters params){generation++;return CalendarSync.permitted(this);}
}
