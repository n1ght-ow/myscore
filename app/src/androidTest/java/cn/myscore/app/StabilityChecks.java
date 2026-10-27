package cn.myscore.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** In-memory request/gesture regressions. No generated matches or images enter app storage. */
final class StabilityChecks {
    private static int checks;
    static int run(Instrumentation test,Activity activity)throws Exception {
        checks=0;
        imageRequests(test,activity);
        test.runOnMainSync(()->adapterBindings(activity));
        selectionQueue(test,activity);
        gestures(test,activity);
        return checks;
    }

    @SuppressWarnings("unchecked")
    private static void imageRequests(Instrumentation test,Activity activity)throws Exception {
        Field flights=Logos.class.getDeclaredField("IN_FLIGHT");flights.setAccessible(true);
        ConcurrentHashMap<String,CompletableFuture<Bitmap>> requests=(ConcurrentHashMap<String,CompletableFuture<Bitmap>>)flights.get(null);
        Method key=Logos.class.getDeclaredMethod("key",String.class,Logos.Crest.class);key.setAccessible(true);
        String url="https://a.espncdn.com/myscore-in-memory-check-"+System.nanoTime()+".png";
        Logos.Crest[] slot={null};String[] identity={null};CompletableFuture<Bitmap> request=new CompletableFuture<>();
        Bitmap bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);
        try {
            test.runOnMainSync(()->{try{
                slot[0]=Logos.crest(new Ui(activity,true),28,false);
                identity[0]=(String)key.invoke(null,url,slot[0]);requests.put(identity[0],request);
                for(int i=0;i<100;i++)Logos.bind(slot[0],url,"Pending");
                check(request.getNumberOfDependents()==1,"100 binds share one pending image callback");
                check(slot[0].box.getTag()==slot[0],"standalone image view retains its request state");
            }catch(Exception e){throw new AssertionError(e);}});
            request.complete(bitmap);test.waitForIdleSync();
            test.runOnMainSync(()->{
                check(slot[0].image.getDrawable()!=null&&slot[0].initials.getVisibility()==View.GONE,"pending image reaches its view");
                Drawable previous=slot[0].image.getDrawable();
                Logos.bind(slot[0],url,"Pending");
                check(slot[0].image.getDrawable()==previous,"same completed image retains its drawable");
            });
            CompletableFuture<Bitmap> stale=new CompletableFuture<>();
            requests.put(identity[0],stale);
            test.runOnMainSync(()->{Logos.bind(slot[0],"","Other");Logos.bind(slot[0],url,"Pending");Logos.bind(slot[0],"","Other");});
            stale.complete(bitmap);test.waitForIdleSync();
            test.runOnMainSync(()->check(slot[0].image.getDrawable()==null&&"OT".contentEquals(slot[0].initials.getText()),"late image never replaces a recycled row's fallback"));
            CompletableFuture<Bitmap> failed=new CompletableFuture<>();requests.put(identity[0],failed);
            test.runOnMainSync(()->Logos.bind(slot[0],url,"Pending"));failed.completeExceptionally(new java.io.IOException("test only"));test.waitForIdleSync();
            CompletableFuture<Bitmap> retry=new CompletableFuture<>();requests.put(identity[0],retry);
            test.runOnMainSync(()->{Logos.bind(slot[0],url,"Pending");check(retry.getNumberOfDependents()==1,"failed image releases the slot for retry");});
            retry.complete(bitmap);test.waitForIdleSync();
        } finally {
            if(identity[0]!=null)requests.remove(identity[0]);
            test.runOnMainSync(()->{if(slot[0]!=null)Logos.bind(slot[0],"","");});bitmap.recycle();
        }
    }

    private static void adapterBindings(Activity activity) {
        Match match=new Match();match.id="memory-only";match.league="epl";match.group="足球";match.home="Home";match.away="Away";
        Match[] tapped={null};
        ScoreAdapter adapter=new ScoreAdapter(new Ui(activity,true),new ScoreAdapter.Host(){
            public void openMatch(Match value){}
            public void openTeam(Match value,boolean home){}
            public void toggleSubscription(Match value){tapped[0]=value;}
            public void openWatch(Match value){}
            public WatchLinks.Link watchLink(Match value){return null;}
            public void retryScores(){}
            public void showSourceStatus(){}
            public boolean isSubscribed(String id){return false;}
        });
        ScoreList parent=new ScoreList(activity);adapter.updateRows(List.of(ScoreRow.match(match)));
        View row=adapter.getView(0,null,parent),action=findCalendar(row);Drawable background=action.getBackground();
        for(int i=0;i<100;i++)row=adapter.getView(0,row,parent);
        check(action.getBackground()==background,"recycled score bindings retain the ripple background");
        Match next=Match.fromJson(match.json());next.home="Updated home";adapter.updateRows(List.of(ScoreRow.match(next)));adapter.getView(0,row,parent);action.performClick();
        check(tapped[0]==next,"recycled subscription action uses the latest source match");
        long started=SystemClock.elapsedRealtimeNanos();
        for(int i=0;i<1000;i++)row=adapter.getView(0,row,parent);
        android.util.Log.i("MyScoreStabilityTests","BENCH 1000 recycled score binds: "+(SystemClock.elapsedRealtimeNanos()-started)/1_000_000+"ms (detached view; not display FPS)");
    }

    private static void selectionQueue(Instrumentation test,Activity activity)throws Exception {
        ExecutorService worker=(ExecutorService)field("cacheWorker").get(activity);
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
        Future<?> blocker=worker.submit(()->{started.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        try {
            check(started.await(5,TimeUnit.SECONDS),"cache worker ready for rapid selection regression");
            test.runOnMainSync(()->{try{
                Method select=MainActivity.class.getDeclaredMethod("select",int.class,String.class,int.class);select.setAccessible(true);
                int day=field("dayOffset").getInt(activity);
                select.invoke(activity,0,"NFL",day);Future<?> old=(Future<?>)field("selectionCacheRequest").get(activity);
                select.invoke(activity,0,"F1",day);Future<?> middle=(Future<?>)field("selectionCacheRequest").get(activity);
                select.invoke(activity,0,"全部",day);
                check(old.isCancelled()&&middle.isCancelled(),"rapid filters cancel obsolete queued cache reads");
            }catch(Exception e){throw new AssertionError(e);}});
        } finally {release.countDown();blocker.get(5,TimeUnit.SECONDS);}
        test.waitForIdleSync();
    }

    private static void gestures(Instrumentation test,Activity activity)throws Exception {
        test.runOnMainSync(()->{try{
            ScoreList list=(ScoreList)field("scoreList").get(activity);
            Method render=MainActivity.class.getDeclaredMethod("render",boolean.class);render.setAccessible(true);
            long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,10,10,0);
            try{list.dispatchTouchEvent(down);}finally{down.recycle();}
            check(list.isInteracting(),"stationary finger is an active list gesture");
            field("renderedScores").set(activity,"");render.invoke(activity,true);
            check("".equals(field("renderedScores").get(activity)),"score refresh waits for the held gesture");
            MotionEvent cancel=MotionEvent.obtain(now,now+1,MotionEvent.ACTION_CANCEL,10,10,0);
            try{list.dispatchTouchEvent(cancel);}finally{cancel.recycle();}
            check(!list.isInteracting(),"cancelled touch releases the list");
            render.invoke(activity,true);
            check(!"".equals(field("renderedScores").get(activity)),"score refresh resumes after the gesture");
            // Test fling state through the same listener used by the platform ListView.
            Field state=ScoreList.class.getDeclaredField("scrollState");state.setAccessible(true);
            state.setInt(list,android.widget.AbsListView.OnScrollListener.SCROLL_STATE_FLING);
            check(list.isInteracting(),"fling keeps updates deferred after the finger lifts");
            state.setInt(list,android.widget.AbsListView.OnScrollListener.SCROLL_STATE_IDLE);
        }catch(Exception e){throw new AssertionError(e);}});
    }

    private static View findCalendar(View view){
        if(view.getContentDescription()!=null&&view.getContentDescription().toString().contains("暂不可订阅"))return view;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=findCalendar(group.getChildAt(i));if(found!=null)return found;}
        return null;
    }
    private static Field field(String name)throws Exception{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field;}
    private static void check(boolean okay,String message){if(!okay)throw new AssertionError(message);checks++;android.util.Log.i("MyScoreStabilityTests","PASS "+message);}
}
