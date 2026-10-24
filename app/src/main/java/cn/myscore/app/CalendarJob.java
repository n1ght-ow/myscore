package cn.myscore.app;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class CalendarJob extends JobService {
    private static final int ID=24701;
    private ExecutorService executor;
    private Future<?> task;
    public static void schedule(Context context) {
        JobScheduler scheduler=context.getSystemService(JobScheduler.class);
        if(scheduler==null)return;
        if(!context.getSharedPreferences("settings",MODE_PRIVATE).getBoolean("calendar_enabled",false)) {scheduler.cancel(ID);return;}
        scheduler.schedule(new JobInfo.Builder(ID,new ComponentName(context,CalendarJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
            .setPeriodic(60*60_000L,15*60_000L).build());
    }
    @Override public boolean onStartJob(JobParameters params) {
        if(!CalendarSync.permitted(this)||!getSharedPreferences("settings",MODE_PRIVATE).getBoolean("calendar_enabled",false))return false;
        executor=Executors.newSingleThreadExecutor();
        task=executor.submit(()-> {
            boolean retry=false;
            try {
                long now=System.currentTimeMillis();LocalDate from=Match.today(now);
                // Seven-day lookahead also catches already-imported matches moved later.
                Repository.Snapshot s=new Repository(this).fetch(from,from.plusDays(7),"全部",false);
                if(!Thread.currentThread().isInterrupted()) {
                    java.util.List<Match> confirmed=new java.util.ArrayList<>();
                    for(Repository.Batch b:s.batches)if(b.error.isEmpty())confirmed.addAll(b.matches);
                    CalendarSync.Result r=new CalendarSync(this).sync(confirmed,System.currentTimeMillis());
                    retry=s.failed()||r.failed>0;
                }
            } catch(RuntimeException e) {retry=true;}
            finally {jobFinished(params,retry);if(executor!=null)executor.shutdown();}
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        if(task!=null)task.cancel(true);if(executor!=null)executor.shutdownNow();return true;
    }
}
