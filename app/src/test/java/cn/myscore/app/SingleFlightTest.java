package cn.myscore.app;

import org.junit.Test;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class SingleFlightTest {
    @Test public void overlappingReadsShareOneSourceCall()throws Exception{
        SingleFlight<String,String> gate=new SingleFlight<>();AtomicInteger reads=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),joining=new CountDownLatch(1);ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            Future<String> first=pool.submit(()->gate.execute("same",()->{reads.incrementAndGet();entered.countDown();assertTrue(release.await(3,TimeUnit.SECONDS));return "real";}));assertTrue(entered.await(3,TimeUnit.SECONDS));
            Thread[] waiter=new Thread[1];Future<String> second=pool.submit(()->{waiter[0]=Thread.currentThread();joining.countDown();return gate.execute("same",()->{reads.incrementAndGet();return "wrong";});});assertTrue(joining.await(3,TimeUnit.SECONDS));
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(waiter[0].getState()!=Thread.State.WAITING&&System.nanoTime()<end)Thread.yield();assertEquals(Thread.State.WAITING,waiter[0].getState());release.countDown();
            assertEquals("real",first.get(3,TimeUnit.SECONDS));assertEquals("real",second.get(3,TimeUnit.SECONDS));assertEquals(1,reads.get());
        }finally{release.countDown();pool.shutdownNow();}
    }
    @Test public void completedReadsDoNotBecomeAStaleCache()throws Exception{SingleFlight<String,Integer> gate=new SingleFlight<>();assertEquals(Integer.valueOf(1),gate.execute("same",()->1));assertEquals(Integer.valueOf(2),gate.execute("same",()->2));}
    @Test public void failedReadCanBeRetriedAndPreservesCause()throws Exception{SingleFlight<String,String> gate=new SingleFlight<>();IOException cause=new IOException("offline");try{gate.execute("same",()->{throw cause;});fail();}catch(IOException error){assertSame(cause,error);}assertEquals("online",gate.execute("same",()->"online"));}
    @Test public void unrelatedSourcesProceedIndependently()throws Exception{SingleFlight<String,String> gate=new SingleFlight<>();ExecutorService pool=Executors.newSingleThreadExecutor();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);try{Future<String> slow=pool.submit(()->gate.execute("slow",()->{entered.countDown();release.await(3,TimeUnit.SECONDS);return "slow";}));assertTrue(entered.await(3,TimeUnit.SECONDS));assertEquals("fast",gate.execute("fast",()->"fast"));release.countDown();assertEquals("slow",slow.get(3,TimeUnit.SECONDS));}finally{release.countDown();pool.shutdownNow();}}
    @Test public void cancelledCallerDoesNotStartSourceRead()throws Exception{SingleFlight<String,String> gate=new SingleFlight<>();Thread.currentThread().interrupt();try{gate.execute("key",()->{fail("cancelled work ran");return "";});fail();}catch(InterruptedException expected){assertTrue(Thread.currentThread().isInterrupted());}finally{Thread.interrupted();}assertEquals("next",gate.execute("key",()->"next"));}
    @Test public void cancellingOldOwnerDoesNotCancelCurrentReader()throws Exception{
        SingleFlight<String,String> gate=new SingleFlight<>();ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch entered=new CountDownLatch(1),joining=new CountDownLatch(1),release=new CountDownLatch(1);Thread[] waiter=new Thread[1];
        try{
            Future<String> old=pool.submit(()->gate.execute("same",()->{entered.countDown();release.await();return "old";}));assertTrue(entered.await(3,TimeUnit.SECONDS));
            Future<String> current=pool.submit(()->{waiter[0]=Thread.currentThread();joining.countDown();return gate.execute("same",()->"current");});assertTrue(joining.await(3,TimeUnit.SECONDS));awaitWaiting(waiter[0]);old.cancel(true);
            assertEquals("current",current.get(3,TimeUnit.SECONDS));
        }finally{release.countDown();pool.shutdownNow();}
    }
    @Test public void cancellingWaiterLeavesOwnerRunning()throws Exception{
        SingleFlight<String,String> gate=new SingleFlight<>();ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch entered=new CountDownLatch(1),joining=new CountDownLatch(1),release=new CountDownLatch(1);Thread[] waiter=new Thread[1];
        try{
            Future<String> owner=pool.submit(()->gate.execute("same",()->{entered.countDown();release.await();return "source";}));assertTrue(entered.await(3,TimeUnit.SECONDS));
            Future<String> other=pool.submit(()->{waiter[0]=Thread.currentThread();joining.countDown();return gate.execute("same",()->{fail("duplicate source read");return "";});});assertTrue(joining.await(3,TimeUnit.SECONDS));awaitWaiting(waiter[0]);other.cancel(true);release.countDown();
            assertEquals("source",owner.get(3,TimeUnit.SECONDS));
        }finally{release.countDown();pool.shutdownNow();}
    }
    @Test public void interruptedIoOwnerDoesNotFailCurrentReader()throws Exception{
        SingleFlight<String,String> gate=new SingleFlight<>();ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),joining=new CountDownLatch(1);Thread[] waiter=new Thread[1];
        try{
            Future<String> old=pool.submit(()->gate.execute("image",()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.io.InterruptedIOException("cancelled image");}return "old";}));
            assertTrue(entered.await(3,TimeUnit.SECONDS));
            Future<String> current=pool.submit(()->{waiter[0]=Thread.currentThread();joining.countDown();return gate.execute("image",()->"current");});
            assertTrue(joining.await(3,TimeUnit.SECONDS));awaitWaiting(waiter[0]);old.cancel(true);
            assertEquals("current",current.get(3,TimeUnit.SECONDS));
        }finally{release.countDown();pool.shutdownNow();}
    }
    @Test public void sharedSocketTimeoutDoesNotStartImmediateRetry()throws Exception{
        SingleFlight<String,String> gate=new SingleFlight<>();ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),joining=new CountDownLatch(1);Thread[] waiter=new Thread[1];
        java.net.SocketTimeoutException timeout=new java.net.SocketTimeoutException("slow source");
        try{
            Future<String> owner=pool.submit(()->gate.execute("image",()->{entered.countDown();release.await();throw timeout;}));
            assertTrue(entered.await(3,TimeUnit.SECONDS));
            Future<String> current=pool.submit(()->{waiter[0]=Thread.currentThread();joining.countDown();return gate.execute("image",()->{fail("timeout must retain backoff");return "";});});
            assertTrue(joining.await(3,TimeUnit.SECONDS));awaitWaiting(waiter[0]);release.countDown();
            for(Future<String> request:java.util.List.of(owner,current))try{request.get(3,TimeUnit.SECONDS);fail();}catch(ExecutionException e){assertSame(timeout,e.getCause());}
        }finally{release.countDown();pool.shutdownNow();}
    }
    private void awaitWaiting(Thread thread){long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(thread.getState()!=Thread.State.WAITING&&System.nanoTime()<end)Thread.yield();assertEquals(Thread.State.WAITING,thread.getState());}
}
