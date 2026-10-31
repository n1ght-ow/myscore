package cn.myscore.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Public Bilibili match-video indexes for Worlds, CS2 and Manchester City.
 *
 * <p>The account 50329118 publishes one full-series recording per match in the
 * "2026全球总决赛" series. Titles are stable: {@code 「2026全球总决赛」10月27日 CFO vs T1}. The
 * configured CS2 account publishes full-series event recordings; tier-one matches and Team Spirit
 * are matched there. We only parse public metadata; playback stays on Bilibili.</p>
 */
final class BilibiliVods {
    static final String MID = "50329118";
    static final String SERIES_ID = "5182712";
    static final String SERIES_NAME = "2026全球总决赛";
    static final String SPACE = "https://space.bilibili.com/50329118";
    static final String API = "https://api.bilibili.com/x/series/archives?mid=" + MID
        + "&series_id=" + SERIES_ID + "&only_normal=true&sort=desc&pn=1&ps=100";
    enum Source {
        WORLDS(MID, "bilibili.json"), CS2("474595627", "bilibili-cs2.json"),
        CITY_HIGHLIGHTS("1832904916", "bilibili-city-highlights.json");
        final String mid, cache;
        Source(String mid, String cache) { this.mid = mid; this.cache = cache; }
        String space() { return "https://space.bilibili.com/" + mid; }
        int minDuration() { return this == CITY_HIGHLIGHTS ? 30 : 900; }
        String api(int page) {
            // "vs" is shared by the account's full-match titles; the old "Spirit" keyword hid every
            // other team's recording.
            return this == WORLDS ? API : "https://api.bilibili.com/x/series/recArchivesByKeywords?mid="
                + mid + "&keywords=" + (this == CITY_HIGHLIGHTS ? "%E9%9B%86%E9%94%A6" : "vs") + "&ps=100"
                + (page == 1 ? "" : "&pn=" + page);
        }
    }
    private static final Pattern CS2_TEAMS = Pattern.compile(
        "^【[^】]+】\\s*([A-Za-z0-9. -]+?)\\s+vs\\s+([A-Za-z0-9. -]+?)(?=\\s+(?:\\d{1,2}月|[\\p{IsHan}]))", Pattern.CASE_INSENSITIVE);
    private static final Pattern CS2_DAY = Pattern.compile("(\\d{1,2})月(\\d{1,2})日");
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(20\\d{2})(?!\\d)");
    private static final Pattern CLIP = Pattern.compile("集锦|精彩|高光|数据图|复盘|分析|剪辑|单图|第[一二三四五六七八九十0-9]+[局图]|\\b(?:map|game)\\s*\\d", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE = Pattern.compile(
        "(\\d{1,2})月(\\d{1,2})日\\s*([A-Za-z0-9.]+)\\s+vs\\s+([A-Za-z0-9.]+)", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA);

    record Episode(String bvid, String title, long duration, long pubdate) {}

    private BilibiliVods() {}

    static Source source(Match match) {
        if (match == null || !match.finalised()) return null;
        if (ManCityHighlights.supports(match)) return Source.CITY_HIGHLIGHTS;
        if ("lol".equals(match.league)) return Source.WORLDS;
        if (!"cs2".equals(match.league)) return null;
        // Every tier-one event final is published by the same CS2 account; Spirit's lower-tier
        // matches keep their existing replay coverage.
        return match.tierOne || DomesticEsports.spiritMatch(match) ? Source.CS2 : null;
    }

    static List<Episode> parse(JSONObject root) {
        return parse(root, Source.WORLDS);
    }

    static List<Episode> parse(JSONObject root, Source source) {
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
            // Full-match sources retain their 15-minute floor; City's official highlights are shorter.
            if (bvid.isBlank() || title.isBlank() || duration < source.minDuration()) continue;
            episodes.add(new Episode(bvid, title, duration, a.optLong("pubdate")));
        }
        return episodes;
    }

    static Episode find(List<Episode> episodes, Match match) {
        if (episodes == null || match == null || match.day() == null) return null;
        if (ManCityHighlights.supports(match)) return ManCityHighlights.find(episodes, match);
        if ("cs2".equals(match.league)) return findCs2(episodes, match);
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

    private static Episode findCs2(List<Episode> episodes, Match match) {
        if (source(match) != Source.CS2) return null;
        String home = cs2Team(match.home), away = cs2Team(match.away);
        if (home.isEmpty() || away.isEmpty() || home.equals(away)) return null;
        Set<String> wanted = Set.of(home, away);
        Episode best = null;
        for (Episode episode : episodes) {
            if (episode.duration() < 900 || CLIP.matcher(episode.title()).find()) continue;
            Matcher teams = CS2_TEAMS.matcher(episode.title()), date = CS2_DAY.matcher(episode.title());
            if (!teams.find() || !date.find()) continue;
            String left = cs2Team(teams.group(1)), right = cs2Team(teams.group(2));
            if (left.equals(right) || !wanted.equals(Set.of(left, right))) continue;
            try {
                // Titles often omit the year. Publication metadata prevents matching last year's fixture.
                LocalDate published = Instant.ofEpochSecond(episode.pubdate()).atZone(Match.ZONE).toLocalDate();
                Matcher year = YEAR.matcher(episode.title());
                boolean explicitYear = year.find();
                LocalDate day = LocalDate.of(explicitYear ? Integer.parseInt(year.group(1)) : published.getYear(),
                    Integer.parseInt(date.group(1)), Integer.parseInt(date.group(2)));
                if (!explicitYear && day.getMonthValue() == 12 && published.getMonthValue() == 1) day = day.minusYears(1);
                if (!day.equals(match.day()) || published.isBefore(day)) continue;
            } catch (RuntimeException e) { continue; }
            if (best == null || episode.pubdate() > best.pubdate()) best = episode;
        }
        return best;
    }

    static String cs2Team(String value) {
        String name = team(value);
        return switch (name) {
            case "TEAMSPIRIT" -> "SPIRIT";
            case "NATUSVINCERE" -> "NAVI";
            case "BETBOOM", "BETBOOMTEAM" -> "BB";
            case "PARIVISION" -> "PV";
            case "THEMONGOLZ" -> "MONGOLZ";
            default -> name;
        };
    }

    static String note(Match match) {
        return ("cs2".equals(match.league) ? "CS2 · " + match.home + " vs " + match.away : SERIES_NAME)
            + (match.day() == null ? "" : " · " + DAY.format(match.day()));
    }

    static String team(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
