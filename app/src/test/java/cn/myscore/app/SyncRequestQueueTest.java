package cn.myscore.app;

import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class SyncRequestQueueTest {
    @Test public void burstForOneAccountCoalesces()throws Exception {
        ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
        try {
            AtomicInteger calls=new AtomicInteger();CountDownLatch sent=new CountDownLatch(1);
            SyncRequestQueue<String> queue=new SyncRequestQueue<>(worker,key->{calls.incrementAndGet();sent.countDown();},300,50);
            for(int i=0;i<50;i++)queue.request("xiaomi");
            assertTrue(sent.await(2,TimeUnit.SECONDS));assertEquals(1,calls.get());
        }finally{worker.shutdownNow();}
    }
    @Test public void editsAfterDispatchRetainATrailingRequest()throws Exception {
        ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
        try {
            AtomicInteger calls=new AtomicInteger();CountDownLatch first=new CountDownLatch(1),second=new CountDownLatch(1);
            SyncRequestQueue<String> queue=new SyncRequestQueue<>(worker,key->{if(calls.incrementAndGet()==1)first.countDown();else second.countDown();},150,5);
            queue.request("xiaomi");assertTrue(first.await(2,TimeUnit.SECONDS));
            for(int i=0;i<20;i++)queue.request("xiaomi");
            assertFalse(second.await(50,TimeUnit.MILLISECONDS));assertTrue(second.await(2,TimeUnit.SECONDS));assertEquals(2,calls.get());
        }finally{worker.shutdownNow();}
    }
    @Test public void accountsHaveIndependentCooldowns()throws Exception {
        ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
        try {
            CountDownLatch sent=new CountDownLatch(2);SyncRequestQueue<String> queue=new SyncRequestQueue<>(worker,key->sent.countDown(),10_000,5);
            queue.request("one");queue.request("two");assertTrue(sent.await(2,TimeUnit.SECONDS));
        }finally{worker.shutdownNow();}
    }
    @Test public void rejectionDoesNotPoisonLaterRequests()throws Exception {
        ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
        try {
            AtomicInteger calls=new AtomicInteger();CountDownLatch failed=new CountDownLatch(1),sent=new CountDownLatch(1);
            SyncRequestQueue<String> queue=new SyncRequestQueue<>(worker,key->{if(calls.incrementAndGet()==1){failed.countDown();throw new SecurityException("test rejection");}sent.countDown();},10_000,5);
            queue.request("xiaomi");assertTrue(failed.await(2,TimeUnit.SECONDS));
            worker.submit(()->{}).get(2,TimeUnit.SECONDS);
            queue.request("xiaomi");assertTrue(sent.await(2,TimeUnit.SECONDS));assertEquals(2,calls.get());
        }finally{worker.shutdownNow();}
    }
}
