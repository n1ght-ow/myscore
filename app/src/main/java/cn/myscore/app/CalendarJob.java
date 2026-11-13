package cn.myscore.app;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Non-resident background refresh & finish-window push for single-match subscriptions. */
public final class CalendarJob extends JobService {
    private static final int ID=24701;
    private static final int ID_FINISH=24705;
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"myscore-calendar-job");
        t.setDaemon(true);
        return t;
    });
    private final ConcurrentHashMap<Integer,Future<?>> tasks=new ConcurrentHashMap<>();

    public static void schedule(Context context) {
        if(context==null)return;
        Context app=context.getApplicationContext();
        if(app==null)app=context;
        JobScheduler scheduler=app.getSystemService(JobScheduler.class);
        if(scheduler==null)return;
        CalendarSync calendar=new CalendarSync(app);
        if(!CalendarSync.permitted(app)||!calendar.hasSubscriptions()) {
            scheduler.cancel(ID);
            scheduler.cancel(ID_FINISH);
            return;
        }
        ComponentName component=new ComponentName(app,CalendarJob.class);
        if(scheduler.getPendingJob(ID)==null) {
            scheduler.schedule(new JobInfo.Builder(ID,component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(60*60_000L,15*60_000L)
                .build());
        }
        long finishDelay=nextFinishCheckDelay(calendar.subscriptions(),System.currentTimeMillis());
        if(finishDelay<0) {
            scheduler.cancel(ID_FINISH);
        } else {
            scheduler.schedule(new JobInfo.Builder(ID_FINISH,component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setMinimumLatency(finishDelay)
                .setBackoffCriteria(60_000L,JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build());
        }
    }

    static long nextFinishCheckDelay(List<Match> subscriptions,long now) {
        if(subscriptions==null||subscriptions.isEmpty())return -1L;
        long best=Long.MAX_VALUE;
        for(Match m:subscriptions) {
            if(m==null||m.finalised()||m.cancelled())continue;
            long candidate;
            if(m.start<=0) {
                candidate=m.live()?8*60_000L:45*60_000L;
            } else {
                long expectedEnd=m.start+expectedFinishOffsetMs(m);
                if(now<expectedEnd)candidate=Math.max(2*60_000L,expectedEnd-now);
                else if(now-expectedEnd<3*3600_000L)candidate=8*60_000L;
                else if(now-expectedEnd<12*3600_000L)candidate=20*60_000L;
                else candidate=45*60_000L;
            }
            if(candidate<best)best=candidate;
        }
        return best==Long.MAX_VALUE?-1L:best;
    }

    static long expectedFinishOffsetMs(Match m) {
        return switch(m.group) {
            case "足球" -> 105*60_000L;
            case "NBA" -> 130*60_000L;
            case "NFL" -> 180*60_000L;
            case "F1" -> shortF1Session(m)?65*60_000L:100*60_000L;
            case "CS2", "LOL" -> esportsFinishOffsetMs(m);
            default -> 120*60_000L;
        };
    }

    private static boolean shortF1Session(Match m) {
        String text=(m.title+" "+m.detail+" "+m.status).toLowerCase(Locale.ROOT);
        return text.contains("练习")||text.contains("排位")||text.contains("冲刺")
            ||text.contains("practice")||text.contains("qualifying")||text.contains("sprint");
    }

    private static long esportsFinishOffsetMs(Match m) {
        String text=(m.detail+" "+m.status+" "+m.title+" "+m.event).toUpperCase(Locale.ROOT);
        if(text.contains("BO1"))return 45*60_000L;
        if(text.contains("BO5"))return 165*60_000L;
        return 95*60_000L;
    }

    @Override public boolean onStartJob(JobParameters params) {
        Http.init(this);
        CalendarSync calendar=new CalendarSync(this);
        if(!CalendarSync.permitted(this)||!calendar.hasSubscriptions()) {
            schedule(this);
            return false;
        }
        Context app=getApplicationContext();
        int jobId=params.getJobId();
        Future<?> submitted=WORKER.submit(()-> {
            boolean retry=false;
            try {
                CalendarSync.Result result=calendar.sync(Collections.emptyList(),System.currentTimeMillis());
                retry=result.failed>0;
            } catch(RuntimeException e) {retry=true;}
            finally {
                tasks.remove(jobId);
                jobFinished(params,retry);
                if(!retry) {
                    try{schedule(app);}catch(RuntimeException ignored){}
                }
            }
        });
        tasks.put(jobId,submitted);
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        Future<?> running=tasks.remove(params.getJobId());
        if(running!=null)running.cancel(true);
        return true;
    }
}
