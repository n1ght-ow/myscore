package cn.myscore.app;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Debounces bursts and retains a trailing request for edits made during the cooldown. */
final class SyncRequestQueue<T> {
    private final ScheduledExecutorService worker;
    private final Consumer<T> sender;
    private final long cooldownNanos,debounceMillis;
    private final Set<T> pending=new HashSet<>();
    private final Map<T,Long> sent=new HashMap<>();
    SyncRequestQueue(ScheduledExecutorService worker,Consumer<T> sender,long cooldownMillis,long debounceMillis) {
        this.worker=worker;this.sender=sender;this.cooldownNanos=TimeUnit.MILLISECONDS.toNanos(cooldownMillis);this.debounceMillis=debounceMillis;
    }
    synchronized void request(T target) {
        if(!pending.add(target))return;
        Long previous=sent.get(target);
        long remaining=previous==null?0:Math.max(0,cooldownNanos-(System.nanoTime()-previous));
        long delay=Math.max(debounceMillis,TimeUnit.NANOSECONDS.toMillis(remaining)+ (remaining>0?1:0));
        worker.schedule(()->send(target),delay,TimeUnit.MILLISECONDS);
    }
    private void send(T target) {
        long started=System.nanoTime();
        synchronized(this){pending.remove(target);sent.put(target,started);}
        try {sender.accept(target);}
        catch(RuntimeException e) {
            synchronized(this){if(Long.valueOf(started).equals(sent.get(target)))sent.remove(target);}
            // A rejected sync must not stop later calendar operations or the queue worker.
        }
    }
}
