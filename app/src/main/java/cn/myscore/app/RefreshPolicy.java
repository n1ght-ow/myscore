package cn.myscore.app;

/** One foreground cadence for the UI and its caches, with bounded failure backoff. */
final class RefreshPolicy {
    static final long SCORES=10_000;
    // Let a cadence-aligned request pass the cache even with small worker timing jitter.
    static final long SCORE_CACHE=SCORES-500;
    static long detail(Match match){return match.finalised()||match.cancelled()?6*3600_000L:match.group.equals("F1")?15_000:SCORES;}
    static long retry(long interval,int failures){return Math.min(120_000,interval*(1L<<Math.min(4,Math.max(0,failures))));}
    static long remaining(long interval,long elapsed){return Math.max(1000,interval-Math.max(0,elapsed));}
}
