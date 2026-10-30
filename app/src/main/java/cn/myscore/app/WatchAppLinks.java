package cn.myscore.app;

import java.net.URI;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Public app routes, restricted to the destinations owned by WatchLinks. */
final class WatchAppLinks {
    record Target(String packageName, String url) {}

    static List<Target> targets(String url) {
        if (url == null) return List.of();
        try {
            URI uri = URI.create(url);
            if (!"https".equals(uri.getScheme())) return List.of();
            String host = uri.getHost(), path = uri.getPath();
            if ("www.douyu.com".equals(host) && path.matches("/[0-9]+")) {
                return List.of(new Target("air.tv.douyu.android",
                    "douyuapp://room?liveType=0&rid=" + path.substring(1)));
            }
            if ("www.huya.com".equals(host) && "/149361".equals(path)) {
                // The configured room's presenter UID, confirmed by Huya's public room data.
                return List.of(new Target("com.duowan.kiwi",
                    "yykiwi://homepage/index.html?banneraction=" + encode(
                        "https://diy-front.cdn.huya.com/zt/frontpage/cc/update.html?hyaction=live&uid=1690094984")));
            }
            if ("www.miguvideo.com".equals(host)) {
                String action;
                if (path.matches("/p/live/[0-9]+")) {
                    action = "{\"type\":\"JUMP_INNER_NEW_PAGE\",\"params\":{\"frameID\":\"default-frame\","
                        + "\"pageID\":\"WORLDCUP_DETAIL\",\"location\":\"h5_share\",\"extra\":{\"mgdbID\":\""
                        + path.substring(8) + "\"}}}";
                } else if (path.matches("/p/home/[a-f0-9]+")) {
                    action = "{\"type\":\"JUMP_INNER_NEW_PAGE\",\"params\":{\"frameID\":\"default-frame\","
                        + "\"pageID\":\"" + path.substring(8) + "\",\"location\":\"h5_share\"}}";
                } else return List.of();
                return List.of(new Target("com.cmcc.cmvideo",
                    "miguvideo://miguvideo?action=" + encode(action)));
            }
            if (("www.bilibili.com".equals(host) && path.matches("/video/BV[a-zA-Z0-9]+/?"))
                || ("space.bilibili.com".equals(host) && path.matches("/[0-9]+/?"))) {
                // Both Android editions accept their canonical HTTPS video and space URLs.
                return List.of(new Target("tv.danmaku.bili", url),
                    new Target("tv.danmaku.bilibilihd", url));
            }
        } catch (IllegalArgumentException ignored) {
            // Keep the original web fallback for malformed or unsupported destinations.
        }
        return List.of();
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private WatchAppLinks() {}
}
