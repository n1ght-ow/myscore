package cn.myscore.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Official Bilibili Worlds VOD index.
 *
 * <p>The account 50329118 publishes one full-series recording per match in the
 * "2026全球总决赛" series. Titles are stable: {@code 「2026全球总决赛」10月27日 CFO vs T1}. We only
 * parse public metadata; playback stays on Bilibili.</p>
 */
final class BilibiliVods {
    static final String MID = "50329118";
    static final String SERIES_ID = "5182712";
    static final String SERIES_NAME = "2026全球总决赛";
    static final String SPACE = "https://space.bilibili.com/50329118";
    static final String API = "https://api.bilibili.com/x/series/archives?mid=" + MID
        + "&series_id=" + SERIES_ID + "&only_normal=true&sort=desc&pn=1&ps=100";
    private static final Pattern TITLE = Pattern.compile(
        "(\\d{1,2})月(\\d{1,2})日\\s*([A-Za-z0-9.]+)\\s+vs\\s+([A-Za-z0-9.]+)", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA);

    record Episode(String bvid, String title, long duration, long pubdate) {}

    private BilibiliVods() {}

    static List<Episode> parse(JSONObject root) {
        List<Episode> episodes = new ArrayList<>();
        JSONObject data = root == null ? null : root.optJSONObject("data");
        JSONArray archives = data == null ? null : data.optJSONArray("archives");
        if (archives == null) return episodes;
        for (int i = 0; i < archives.length(); i++) {
            JSONObject a = archives.optJSONObject(i);
            if (a == null) continue;
            String bvid = a.optString("bvid");
            String title = a.optString("title");
            long duration = a.optLong("duration");
            // The series should only contain full series recordings. Keep a floor so a future
            // highlight clip accidentally added to it cannot replace the real match.
            if (bvid.isBlank() || title.isBlank() || duration < 900) continue;
            episodes.add(new Episode(bvid, title, duration, a.optLong("pubdate")));
        }
        return episodes;
    }

    static Episode find(List<Episode> episodes, Match match) {
        if (episodes == null || match == null || match.day() == null) return null;
        String home = team(match.home), away = team(match.away);
        if (home.isEmpty() || away.isEmpty() || home.equals(away)) return null;
        Set<String> wanted = Set.of(home, away);
        Episode best = null;
        for (Episode episode : episodes) {
            Matcher matcher = TITLE.matcher(episode.title());
            if (!matcher.find()) continue;
            int month, day;
            try {
                month = Integer.parseInt(matcher.group(1));
                day = Integer.parseInt(matcher.group(2));
                if (!LocalDate.of(match.day().getYear(), month, day).equals(match.day())) continue;
            } catch (RuntimeException e) {
                continue;
            }
            String left = team(matcher.group(3)), right = team(matcher.group(4));
            if (left.isEmpty() || right.isEmpty() || left.equals(right)) continue;
            if (!wanted.equals(Set.of(left, right))) continue;
            if (best == null || episode.pubdate() > best.pubdate()) best = episode;
        }
        return best;
    }

    static String note(Match match) {
        return SERIES_NAME + " · " + (match.day() == null ? "" : DAY.format(match.day()));
    }

    static String team(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
