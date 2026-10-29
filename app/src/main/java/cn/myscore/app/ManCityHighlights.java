package cn.myscore.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Official men's first-team highlights; the Premier League keeps its existing broadcaster. */
final class ManCityHighlights {
    private static final Pattern OTHER_TEAM = Pattern.compile("女足|女子|青年|预备队|EDS|U\\s*\\d{2}(?!\\d)", Pattern.CASE_INSENSITIVE);
    private static final String CITY = "(?:曼城|我城)(?:客场|主场|十人|最终|常规时间|在客场|在主场)*";
    private static final String RESULT_WORDS = "(?:战平|不敌|击败|战胜|逆转|负于|大胜)?";
    private ManCityHighlights() {}

    static boolean supports(Match match) {
        return match != null && match.finalised()
            && ("ucl".equals(match.league) || "efl".equals(match.league) || "fa".equals(match.league))
            && (citySide(match, true) || citySide(match, false));
    }

    private static boolean citySide(Match match, boolean home) {
        String id = home ? match.homeId : match.awayId;
        String name = home ? match.home : match.away;
        if (!id.isBlank()) return id.equals("382");
        return List.of("manchester city", "man city", "曼城").contains(name.trim().toLowerCase(Locale.ROOT));
    }

    static BilibiliVods.Episode find(List<BilibiliVods.Episode> episodes, Match match) {
        if (!supports(match) || match.start <= 0 || !match.hasScore()) return null;
        boolean home = citySide(match, true);
        String own = home ? match.homeScore : match.awayScore, other = home ? match.awayScore : match.homeScore;
        if (!own.matches("\\d{1,2}") || !other.matches("\\d{1,2}")) return null;
        List<String> aliases = aliases(home ? match.away : match.home);
        if (aliases.isEmpty()) return null;
        List<String> quoted = new ArrayList<>();
        for (String alias : aliases) quoted.add(Pattern.quote(alias));
        String opponent = "(?:" + String.join("|", quoted) + ")";
        Pattern result = Pattern.compile(CITY + "\\s*" + own + "\\s*[-:：]\\s*" + other + "\\s*" + RESULT_WORDS + "\\s*" + opponent
            + "|" + opponent + "\\s*(?:客场|主场)?\\s*" + other + "\\s*[-:：]\\s*" + own + "\\s*" + RESULT_WORDS + "\\s*(?:曼城|我城)", Pattern.CASE_INSENSITIVE);
        BilibiliVods.Episode best = null;
        for (BilibiliVods.Episode episode : episodes) {
            String title = episode.title().trim();
            if (episode.duration() < 30 || OTHER_TEAM.matcher(title).find()) continue;
            if (!(title.startsWith("城民饭点") || title.startsWith("【比赛集锦】") || title.startsWith("集锦"))) continue;
            if (!title.contains("集锦") || title.contains("赛季集锦") || title.contains("个人集锦") || title.contains("回顾")
                || title.contains("季前") || title.contains("热身") || title.contains("友谊") || title.contains("社区盾")) continue;
            String competition = competition(title);
            if (competition != null && !competition.equals(match.league)) continue;
            // Titles omit match dates. Require the exact opponent/result and a bounded post-match upload.
            long published = episode.pubdate() * 1000L;
            if (published < match.start || published - match.start > 7L * 24 * 60 * 60_000) continue;
            String withoutPenalties = title.replaceAll("（[^）]*）|\\([^)]*\\)", "");
            if (!result.matcher(withoutPenalties).find()) continue;
            if (best == null || episode.pubdate() > best.pubdate()) best = episode;
        }
        return best;
    }

    private static String competition(String title) {
        if (title.contains("欧冠")) return "ucl";
        if (title.contains("足总杯")) return "fa";
        if (title.contains("联赛杯") || title.contains("卡拉宝杯") || title.contains("卡拉保杯")) return "efl";
        if (title.contains("英超")) return "epl";
        return null;
    }

    private static List<String> aliases(String name) {
        List<String> names = new ArrayList<>();
        if (name == null || name.isBlank()) return names;
        names.add(name.trim());
        String chinese = MiguTeams.chinese("epl", name);
        if (chinese != null) names.add(chinese);
        switch (BilibiliVods.team(name)) {
            case "AEKATHENS", "AEKATHENSFC", "AEK" -> names.addAll(List.of("雅典AEK", "AEK雅典"));
            case "PARISSAINTGERMAIN", "PARISSAINTGERMAINFC", "PSG" -> names.addAll(List.of("巴黎圣日耳曼", "巴黎"));
            case "FCPORTO", "PORTO" -> names.add("波尔图");
            case "REALMADRID" -> names.addAll(List.of("皇马", "皇家马德里"));
            case "INTERMILAN", "INTERNAZIONALE", "INTER" -> names.addAll(List.of("国际米兰", "国米"));
            case "ATLETICOMADRID", "ATLETICODEMADRID" -> names.addAll(List.of("马德里竞技", "马竞"));
            case "BAYERNMUNICH", "BAYERNMUNCHEN" -> names.addAll(List.of("拜仁慕尼黑", "拜仁"));
            case "BORUSSIADORTMUND", "DORTMUND" -> names.addAll(List.of("多特蒙德", "多特"));
            case "JUVENTUS" -> names.addAll(List.of("尤文图斯", "尤文"));
            case "BARCELONA", "FCBARCELONA" -> names.addAll(List.of("巴塞罗那", "巴萨"));
            default -> { }
        }
        return names;
    }
}
