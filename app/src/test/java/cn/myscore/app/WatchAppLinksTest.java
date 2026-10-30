package cn.myscore.app;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class WatchAppLinksTest {
    @Test public void roomRoutesRetainTheConfiguredDestination() {
        var douyu = WatchAppLinks.targets("https://www.douyu.com/6657").get(0);
        assertEquals("air.tv.douyu.android", douyu.packageName());
        assertEquals("douyuapp://room?liveType=0&rid=6657", douyu.url());
        var huya = WatchAppLinks.targets("https://www.huya.com/149361").get(0);
        assertEquals("com.duowan.kiwi", huya.packageName());
        assertTrue(decode(huya.url()).contains("hyaction=live&uid=1690094984"));
        assertTrue(WatchAppLinks.targets("https://www.huya.com/999").isEmpty());
    }

    @Test public void miguUsesMatchIdRatherThanContentIdForLiveAndReplay() throws Exception {
        for (boolean finished : new boolean[]{false, true}) {
            var link = WatchLinks.miguMatch("120000588291", "nba", "骑士", "森林狼", finished);
            var target = WatchAppLinks.targets(link.url).get(0);
            assertEquals("com.cmcc.cmvideo", target.packageName());
            JSONObject action = new JSONObject(decode(target.url()));
            assertEquals("WORLDCUP_DETAIL", action.getJSONObject("params").getString("pageID"));
            assertEquals("120000588291", action.getJSONObject("params").getJSONObject("extra").getString("mgdbID"));
        }
        var zone = WatchAppLinks.targets("https://www.miguvideo.com/p/home/3cd6ba04967742879aaa40bee02a99a6").get(0);
        assertEquals("3cd6ba04967742879aaa40bee02a99a6",
            new JSONObject(decode(zone.url())).getJSONObject("params").getString("pageID"));
    }

    @Test public void bilibiliSupportsPhoneAndHdWithoutLosingVideoOrSpace() {
        for (String url : new String[]{"https://www.bilibili.com/video/BV1uUnf6TEch", "https://space.bilibili.com/50329118"}) {
            var targets = WatchAppLinks.targets(url);
            assertEquals(2, targets.size());
            assertEquals("tv.danmaku.bili", targets.get(0).packageName());
            assertEquals("tv.danmaku.bilibilihd", targets.get(1).packageName());
            for (var target : targets) assertEquals(url, target.url());
        }
    }

    @Test public void unknownAndSpoofedHostsNeverAcquireAnAppRoute() {
        for (String url : new String[]{"https://www.douyu.com.evil.test/6657", "http://www.douyu.com/6657",
            "https://example.com/video/BV123", "https://www.miguvideo.com/p/live/123%22", "%%%"}) {
            assertTrue(url, WatchAppLinks.targets(url).isEmpty());
        }
        assertTrue(WatchAppLinks.targets(null).isEmpty());
    }

    private String decode(String url) {
        return URLDecoder.decode(URI.create(url).getRawQuery().split("=", 2)[1], StandardCharsets.UTF_8);
    }
}
