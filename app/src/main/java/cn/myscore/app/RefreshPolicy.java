package cn.myscore.app;

/** One foreground cadence for the UI and its caches, with bounded failure backoff. */
final class RefreshPolicy {
    static final long SCORES=10_000;
    // Let a cadence-aligned request pass the cache even with small worker timing jitter.
    static final long SCORE_CACHE=SCORES-500;
    static long detail(Match match){return match.finalised()||match.cancelled()?6*3600_000L:match.group.equals("F1")?15_000:SCORES;}
    static boolean freshDetail(Match requested,Match cached,long fetched,long now){
        // A list that reports a changed state/score must not be hidden by a six-hour final cache.
        if(!requested.id.equals(cached.id)||!requested.state.equals(cached.state)||!requested.homeScore.equals(cached.homeScore)||!requested.awayScore.equals(cached.awayScore))return false;
        long age=now-fetched;return fetched>0&&age>=0&&age<Math.max(1000,detail(requested)-500);
    }
    static long retry(long interval,int failures){return Math.min(120_000,interval*(1L<<Math.min(4,Math.max(0,failures))));}
    static long remaining(long interval,long elapsed){return Math.max(1000,interval-Math.max(0,elapsed));}
}
