package cn.myscore.app;

/**
 * Watch destinations for the four configured competitions.
 *
 * <p>Migu league zones are the safe fallback; {@code WatchRepository} upgrades a league zone to
 * the exact match page when its public search returns a verified fixture. Worlds finished matches
 * upgrade from the Huya room to Bilibili's official full-series recording, with a "pending" Bilibili
 * card while the recording has not been published yet. Finished CS2 matches in tier-one events and
 * Spirit's matches use the configured CS2 account with the same replay/pending states; other
 * finished CS2 matches have no verified replay and return no entry instead of an unrelated live room.
 * Manchester City's finished cup matches use official highlights; other cup teams, F1 and NFL stay
 * unconfigured.</p>
 */
final class WatchLinks {
    enum Kind {
        ZONE("专区", "play"), ROOM("直播间", "play"), LIVE("直播", "play"),
        REPLAY("回放", "play"), HIGHLIGHTS("集锦", "play"), PENDING("待发布", "clock");

        final String label, icon;
        Kind(String label, String icon) { this.label = label; this.icon = icon; }
    }

    static final class Link {
        final String provider, shortProvider, title, note, url;
        final Kind kind;

        Link(String provider, String shortProvider, String title, String note, String url, Kind kind) {
            this.provider = provider;
            this.shortProvider = shortProvider;
            this.title = title;
            this.note = note;
            this.url = url;
            this.kind = kind;
        }
    }

    private static final Link EPL = new Link(
        "咪咕视频", "咪咕", "观看英超直播", "直播、回放与赛程",
        "https://www.miguvideo.com/p/home/0c40bbc85fa345bbba20f8e5fd11a922", Kind.ZONE);
    private static final Link NBA = new Link(
        "咪咕视频", "咪咕", "观看 NBA 直播", "直播、回放与赛程",
        "https://www.miguvideo.com/p/home/3cd6ba04967742879aaa40bee02a99a6", Kind.ZONE);
    private static final Link CS2 = new Link(
        "斗鱼", "斗鱼", "打开直播间", "CS2 赛事直播",
        "https://www.douyu.com/6657", Kind.ROOM);
    private static final Link LOL = new Link(
        "虎牙", "虎牙", "观看本场直播", "LOL 赛事直播",
        "https://www.huya.com/149361", Kind.ROOM);
    private static final Link BILI_PENDING = new Link(
        "哔哩哔哩", "B站", "录像待发布", "2026全球总决赛 · 稍后更新",
        BilibiliVods.SPACE, Kind.PENDING);

    private WatchLinks() {}

    static Link of(Match match) {
        if (match == null) return null;
        if (ManCityHighlights.supports(match)) return biliPending(match);
        switch (match.league) {
            case "epl": return EPL;
            case "nba": return NBA;
            case "cs2": return match.finalised() || match.cancelled() ? null : CS2;
            case "lol": return LOL;
            default: return null;
        }
    }

    static Link biliPending() {
        return BILI_PENDING;
    }

    static Link biliPending(Match match) {
        if (ManCityHighlights.supports(match)) return new Link("哔哩哔哩", "B站", "集锦待发布",
            "曼城 · 赛后集锦稍后更新", BilibiliVods.Source.CITY_HIGHLIGHTS.space(), Kind.PENDING);
        BilibiliVods.Source source = BilibiliVods.source(match);
        if (source == BilibiliVods.Source.WORLDS) return BILI_PENDING;
        if (source != BilibiliVods.Source.CS2) return null;
        return new Link("哔哩哔哩", "B站", "录像待发布", "CS2 · 稍后更新",
            BilibiliVods.Source.CS2.space(), Kind.PENDING);
    }

    static Link biliVideo(String bvid, Match match) {
        if (ManCityHighlights.supports(match)) return new Link("哔哩哔哩", "B站", "观看本场集锦",
            Competition.find(match.league).name + " · " + match.home + " vs " + match.away,
            "https://www.bilibili.com/video/" + bvid, Kind.HIGHLIGHTS);
        return new Link("哔哩哔哩", "B站", "观看全场录像",
            match == null ? BilibiliVods.SERIES_NAME : BilibiliVods.note(match),
            "https://www.bilibili.com/video/" + bvid, Kind.REPLAY);
    }

    static Link miguMatch(String pID, String league, String home, String away, boolean finished) {
        if (pID == null || pID.isBlank()) return "epl".equals(league) ? EPL : NBA;
        String competition = "epl".equals(league) ? "英超" : "NBA";
        String title = "观看本场" + (finished ? "回放" : "直播");
        String note = competition + " · " + home + " vs " + away;
        return new Link("咪咕视频", "咪咕", title, note,
            "https://www.miguvideo.com/p/live/" + pID, finished ? Kind.REPLAY : Kind.LIVE);
    }
}
