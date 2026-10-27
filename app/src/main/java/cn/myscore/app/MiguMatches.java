package cn.myscore.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Exact match-card lookup inside Migu's public search response. */
final class MiguMatches {
    static final String SEARCH_URL = "https://jadeite.migu.cn/search/v3/open-search";
    static final String SETTINGS_URL = "https://app-sc.miguvideo.com/common/v1/settings/SEARCH_PACK_ID";
    private static final DateTimeFormatter START = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
    private static final long WINDOW_MS = 20 * 60_000L;

    record Hit(String pID, String left, String right, long start, String competition) {}

    private MiguMatches() {}

    /**
     * Finds a match card for the supplied fixture. Both team names and the kickoff window must
     * agree; the first search result is never trusted on its own.
     */
    static Hit find(JSONObject root, Match match, String league) {
        if (root == null || match == null) return null;
        String home = MiguTeams.chinese(league, match.home);
        String away = MiguTeams.chinese(league, match.away);
        if (home == null || away == null) return null;
        JSONObject body = root.optJSONObject("body");
        JSONObject widget = body == null ? null : body.optJSONObject("widgetResult");
        JSONArray items = widget == null ? null : widget.optJSONArray("items");
        if (items == null) return null;
        Hit best = null;
        long bestDelta = Long.MAX_VALUE;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String pID = item.optString("widgetId");
            if (pID.isBlank()) continue;
            String competition = item.optString("competitionName");
            if (!competitionMatches(competition, league)) continue;
            String left = item.optString("confrontTeamLeftName");
            String right = item.optString("confrontTeamRightName");
            boolean teams = MiguTeams.sameChinese(left, home) && MiguTeams.sameChinese(right, away)
                || MiguTeams.sameChinese(left, away) && MiguTeams.sameChinese(right, home);
            if (!teams) continue;
            long start = parseStart(item.optString("matchStartTime"));
            if (start <= 0 || match.start <= 0) continue;
            long delta = Math.abs(start - match.start);
            if (delta > WINDOW_MS || delta >= bestDelta) continue;
            best = new Hit(pID, item.optString("confrontTeamLeftName"),
                item.optString("confrontTeamRightName"), start, competition);
            bestDelta = delta;
        }
        return best;
    }

    static boolean competitionMatches(String competition, String league) {
        if (competition == null) return false;
        if ("epl".equals(league)) return competition.contains("英超") && !competition.contains("女");
        if ("nba".equals(league)) return competition.toUpperCase(Locale.ROOT).contains("NBA")
            && !competition.contains("经典");
        return false;
    }

    static long parseStart(String value) {
        try {
            return LocalDateTime.parse(value, START).atZone(Match.ZONE).toInstant().toEpochMilli();
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
