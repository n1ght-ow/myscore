package cn.myscore.app;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Watch destinations for the four configured competitions.
 *
 * <p>Migu league zones are the safe fallback; {@code WatchRepository} upgrades a league zone to
 * the exact match page when its public search returns a verified fixture. Worlds finished matches
 * upgrade from the Huya room to Bilibili's official full-series recording, with a "pending" Bilibili
 * card while the recording has not been published yet. F1, NFL and the cup competitions stay
 * unconfigured.</p>
 */
final class WatchLinks {
    static final class Link {
        final String provider, shortProvider, title, note, url;

        Link(String provider, String shortProvider, String title, String note, String url) {
            this.provider = provider;
            this.shortProvider = shortProvider;
            this.title = title;
            this.note = note;
            this.url = url;
        }
    }

    private static final Link EPL = new Link(
        "咪咕视频", "咪咕", "观看英超直播", "直播、回放与赛程",
        "https://www.miguvideo.com/p/home/0c40bbc85fa345bbba20f8e5fd11a922");
    private static final Link NBA = new Link(
        "咪咕视频", "咪咕", "观看 NBA 直播", "直播、回放与赛程",
        "https://www.miguvideo.com/p/home/3cd6ba04967742879aaa40bee02a99a6");
    private static final Link CS2 = new Link(
        "斗鱼", "斗鱼", "打开直播间", "CS2 赛事直播",
        "https://www.douyu.com/6657");
    private static final Link LOL = new Link(
        "虎牙", "虎牙", "观看本场直播", "LOL 赛事直播",
        "https://www.huya.com/149361");
    private static final Link BILI_PENDING = new Link(
        "哔哩哔哩", "B站", "录像待发布", "2026全球总决赛 · 稍后更新",
        BilibiliVods.SPACE);
    private static final DateTimeFormatter BILI_DAY = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA);

    private WatchLinks() {}

    static Link of(Match match) {
        if (match == null) return null;
        switch (match.league) {
            case "epl": return EPL;
            case "nba": return NBA;
            case "cs2": return CS2;
            case "lol": return LOL;
            default: return null;
        }
    }

    static Link biliPending() {
        return BILI_PENDING;
    }

    static Link biliVideo(String bvid, Match match) {
        String day = match == null || match.day() == null ? "" : BILI_DAY.format(match.day());
        return new Link("哔哩哔哩", "B站", "观看全场录像",
            "2026全球总决赛" + (day.isEmpty() ? "" : " · " + day),
            "https://www.bilibili.com/video/" + bvid);
    }

    static Link miguMatch(String pID, String league, String home, String away, boolean finished) {
        if (pID == null || pID.isBlank()) return "epl".equals(league) ? EPL : NBA;
        String competition = "epl".equals(league) ? "英超" : "NBA";
        String title = "观看本场" + (finished ? "回放" : "直播");
        String note = competition + " · " + home + " vs " + away;
        return new Link("咪咕视频", "咪咕", title, note,
            "https://www.miguvideo.com/p/live/" + pID);
    }
}
