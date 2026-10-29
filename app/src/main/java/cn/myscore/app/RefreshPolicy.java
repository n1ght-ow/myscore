package cn.myscore.app;

import java.time.LocalDate;
import java.util.List;

/**
 * One foreground cadence for the UI and its caches, with bounded failure backoff.
 * A source-confirmed final/cancelled match is locked: automatic refreshes neither
 * re-request a fully settled batch nor replace an already terminal match.
 */
final class RefreshPolicy {
    static final long SCORES=10_000;
    // Let a cadence-aligned request pass the cache even with small worker timing jitter.
    static final long SCORE_CACHE=SCORES-500;
    // A scheduled match within this window is checked at the near-start cadence.
    static final long NEAR_WINDOW=2*60*60_000L;
    static final long NEAR_CACHE=25_000;
    static final long NEAR_POLL=30_000L;
    static final long SLOW_CACHE=55_000;
    static final long SLOW_POLL=60_000;
    static final long IDLE_CACHE=25*60_000L;
    static final long IDLE_POLL=30*60_000L;
    // A fixture days away rarely changes; a revisit should reuse the last read instead of
    // starting another round of requests. Close-to-start matches still use NEAR_CACHE.
    static final long FUTURE_CACHE=2*60*60_000L;
    static final long PAST_CACHE=6*60*60_000L;
    static final long DETAIL_IDLE=10*60_000L;
    static final long FINAL_DETAIL=6*60*60_000L;
    // A refresh may finish a little late, but a hung source must not stop the foreground loop.
    static final long REFRESH_DEADLINE=30_000L;
    // A settled day is re-read this often so a later reschedule/correction can still appear.
    static final long SETTLED_RECHECK=6*60*60_000L;

    private RefreshPolicy() {}

    static boolean terminal(Match match) {
        return match != null && (match.finalised() || match.cancelled());
    }
    /** A successful list of finished matches (or a confirmed empty day) needs no fast polling. */
    static boolean settled(List<Match> matches) {
        if(matches == null) return false;
        for(Match match:matches) if(!terminal(match)) return false;
        return true;
    }
    private static boolean hasLive(List<Match> matches) {
        for(Match match:matches) if(match.live()) return true;
        return false;
    }
    private static boolean hasSlow(List<Match> matches) {
        for(Match match:matches) if(match.state.equals("delayed") || match.state.equals("unknown")) return true;
        return false;
    }
    /** Earliest confirmed kick-off/toss/session start; 0 when no scheduled time is known. */
    private static long nextConfirmedStart(List<Match> matches) {
        long next=0;
        for(Match match:matches) {
            if(!match.scheduled() || !match.timeConfirmed || match.start<=0) continue;
            if(next==0 || match.start<next) next=match.start;
        }
        return next;
    }
    private static boolean allBatchesSettled(List<Repository.Batch> batches) {
        if(batches == null || batches.isEmpty()) return false;
        for(Repository.Batch batch:batches) {
            if(batch == null || !batch.error.isEmpty() || !settled(batch.matches)) return false;
        }
        return true;
    }

    /**
     * How long a successful read may be reused. A fully settled list is handled by the caller
     * before this method and never reaches the network automatically.
     */
    static long cacheTtl(List<Match> matches,LocalDate day,LocalDate today,long now) {
        if(hasLive(matches)) return SCORE_CACHE;
        // A match starting soon must stay fresh even when it falls on tomorrow's date.
        if(!day.isBefore(today)) {
            long next=nextConfirmedStart(matches);
            if(next>0 && next-now<=NEAR_WINDOW) return NEAR_CACHE;
        }
        if(day.isBefore(today)) return PAST_CACHE;
        if(day.isAfter(today)) return FUTURE_CACHE;
        if(hasSlow(matches)) return SLOW_CACHE;
        return IDLE_CACHE;
    }

    /**
     * Delay before the next foreground read. Zero means the visible day is settled or in the past,
     * so automatic polling stops; a manual refresh is still available.
     */
    static long pollDelay(List<Repository.Batch> batches,LocalDate day,LocalDate today,long now) {
        List<Match> matches=new java.util.ArrayList<>();
        if(batches != null) for(Repository.Batch batch:batches) if(batch != null) matches.addAll(batch.matches);
        if(hasLive(matches)) return SCORES;
        if(allBatchesSettled(batches)) return day.isBefore(today) ? 0 : IDLE_POLL;
        if(day.isBefore(today)) return 0;
        long retryAt=0;
        if(batches != null) for(Repository.Batch batch:batches) {
            if(batch == null || batch.error.isEmpty()) continue;
            if(batch.retryAt>0 && (retryAt==0 || batch.retryAt<retryAt)) retryAt=batch.retryAt;
        }
        if(retryAt>0) return Math.max(SCORES,Math.min(120_000,retryAt-now));
        if(batches != null) for(Repository.Batch batch:batches) if(batch != null && !batch.error.isEmpty()) return SLOW_POLL;
        if(hasSlow(matches)) return SLOW_POLL;
        long next=nextConfirmedStart(matches);
        if(next>0) {
            long until=next-now;
            if(until<=NEAR_WINDOW) return NEAR_POLL;
            return Math.min(IDLE_POLL,Math.max(60_000L,until-NEAR_WINDOW));
        }
        return IDLE_POLL;
    }

    /** A detail already fetched after both the list and the cache reached a terminal state. */
    static boolean frozenDetail(Match requested,Match cached) {
        return requested != null && cached != null && sameDetailState(requested,cached)
            && terminal(requested) && terminal(cached);
    }

    private static boolean sameDetailState(Match requested,Match cached) {
        return requested.id.equals(cached.id) && requested.state.equals(cached.state)
            && requested.homeScore.equals(cached.homeScore) && requested.awayScore.equals(cached.awayScore)
            && requested.homeShootout.equals(cached.homeShootout) && requested.awayShootout.equals(cached.awayShootout);
    }

    static long teamCacheTtl(List<Match> matches,long now) {
        if(hasLive(matches))return SCORE_CACHE;
        if(hasSlow(matches))return SLOW_CACHE;
        long next=nextConfirmedStart(matches);
        return next>0&&next-now<=NEAR_WINDOW?NEAR_CACHE:30*60_000L;
    }

    /** Wake before a scheduled fixture becomes live, even while its team page stays open. */
    static long teamPollDelay(List<Match> matches,long now) {
        if(hasLive(matches))return 30_000L;
        if(hasSlow(matches))return SLOW_POLL;
        long next=nextConfirmedStart(matches);
        return next>0?Math.min(IDLE_POLL,Math.max(NEAR_POLL,next-now-NEAR_WINDOW)):0;
    }

    static long detail(Match match,long now) {
        if(terminal(match)) return FINAL_DETAIL;
        if(match.live()) return match.group.equals("F1")?15_000:SCORES;
        if(match.state.equals("delayed") || match.state.equals("unknown")) return SLOW_POLL;
        if(match.scheduled()) {
            if(match.timeConfirmed && match.start>0 && match.start-now<=NEAR_WINDOW) return SLOW_POLL;
            return DETAIL_IDLE;
        }
        return SLOW_POLL;
    }
    /** Compatibility for callers that only need the current cadence. */
    static long detail(Match match) {
        return detail(match,System.currentTimeMillis());
    }

    static boolean freshDetail(Match requested,Match cached,long fetched,long now){
        // A list that reports a changed state/score must not be hidden by a six-hour final cache.
        if(!sameDetailState(requested,cached))return false;
        long age=now-fetched;return fetched>0&&age>=0&&age<Math.max(1000,detail(requested,now)-500);
    }
    static long retry(long interval,int failures){return interval>=120_000?interval:Math.min(120_000,interval*(1L<<Math.min(4,Math.max(0,failures))));}
    static long remaining(long interval,long elapsed){return Math.max(1000,interval-Math.max(0,elapsed));}
    /** Wake once at the next Beijing midnight so an open page re-anchors its relative date. */
    static long untilNextDay(long now) {
        long next=Match.today(now).plusDays(1).atStartOfDay(Match.ZONE).toInstant().toEpochMilli();
        return Math.max(1000L,next-now+1000L);
    }
}
