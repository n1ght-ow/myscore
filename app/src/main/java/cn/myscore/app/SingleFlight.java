package cn.myscore.app;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/** Shares overlapping reads only; completed and failed reads never become an implicit cache. */
final class SingleFlight<K,V> {
    private static final int MAX_CANCEL_RETRIES=2;
    private final ConcurrentHashMap<K,FutureTask<V>> requests=new ConcurrentHashMap<>();
    V execute(K key,Callable<V> read)throws Exception {
        return execute(key,read,0);
    }
    private V execute(K key,Callable<V> read,int retries)throws Exception {
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();
        FutureTask<V> created=new FutureTask<>(read),running=requests.putIfAbsent(key,created);
        boolean owner=running==null;
        if(owner)running=created;
        try {
            if(owner)running.run();
            return running.get();
        }catch(ExecutionException e){
            Throwable cause=e.getCause();
            // A superseded screen may own the shared read. Its cancellation must not cancel
            // another screen that still needs the same URL.
            if(!owner&&(cause instanceof InterruptedException
                || cause instanceof java.io.InterruptedIOException && !(cause instanceof java.net.SocketTimeoutException))
                &&!Thread.currentThread().isInterrupted()){
                requests.remove(key,running);
                if(retries>=MAX_CANCEL_RETRIES)throw new InterruptedException("Shared request cancelled repeatedly");
                return execute(key,read,retries+1);
            }
            if(cause instanceof Exception)throw (Exception)cause;
            if(cause instanceof Error)throw (Error)cause;
            throw new IllegalStateException(cause);
        }finally{if(owner)requests.remove(key,created);}
    }
}
