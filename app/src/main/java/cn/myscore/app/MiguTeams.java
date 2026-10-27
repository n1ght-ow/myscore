package cn.myscore.app;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Chinese team names used by Migu's match search. ESPN supplies English names; Migu's public
 * search only returns match cards for Chinese names, so this table is the bridge.
 *
 * <p>Only clubs in the two configured competitions are listed. An unknown or renamed club
 * returns null and the caller keeps the league-zone fallback instead of guessing.</p>
 */
final class MiguTeams {
    private static final Map<String, String> BY_ENGLISH = new HashMap<>();

    static {
        // Premier League 2026-27.
        put("曼联", "Manchester United", "Man United", "Man Utd");
        put("阿斯顿维拉", "Aston Villa");
        put("布莱顿", "Brighton", "Brighton & Hove Albion", "Brighton and Hove Albion");
        put("布伦特福德", "Brentford");
        put("水晶宫", "Crystal Palace");
        put("利物浦", "Liverpool");
        put("伊普斯维奇", "Ipswich", "Ipswich Town");
        put("伯恩茅斯", "Bournemouth", "AFC Bournemouth");
        put("桑德兰", "Sunderland");
        put("切尔西", "Chelsea");
        put("诺丁汉森林", "Nottingham Forest", "Nottm Forest");
        put("曼城", "Manchester City", "Man City");
        put("阿森纳", "Arsenal");
        put("赫尔城", "Hull", "Hull City");
        put("富勒姆", "Fulham");
        put("纽卡斯尔联", "Newcastle", "Newcastle United");
        put("利兹联", "Leeds", "Leeds United");
        put("托特纳姆热刺", "Tottenham", "Tottenham Hotspur");
        put("埃弗顿", "Everton");
        put("考文垂", "Coventry", "Coventry City");
        put("西汉姆联", "West Ham", "West Ham United");
        put("狼队", "Wolverhampton", "Wolverhampton Wanderers", "Wolves");
        put("伯恩利", "Burnley");
        put("莱斯特城", "Leicester", "Leicester City");
        put("南安普顿", "Southampton");
        put("诺维奇", "Norwich", "Norwich City");
        put("沃特福德", "Watford");
        put("谢菲尔德联", "Sheffield United", "Sheffield Utd");
        put("卢顿", "Luton", "Luton Town");

        // NBA 2026-27.
        put("老鹰", "Atlanta Hawks", "Hawks");
        put("凯尔特人", "Boston Celtics", "Celtics");
        put("篮网", "Brooklyn Nets", "Nets");
        put("黄蜂", "Charlotte Hornets", "Hornets");
        put("公牛", "Chicago Bulls", "Bulls");
        put("骑士", "Cleveland Cavaliers", "Cavaliers", "Cavs");
        put("独行侠", "Dallas Mavericks", "Mavericks", "Mavs");
        put("掘金", "Denver Nuggets", "Nuggets");
        put("活塞", "Detroit Pistons", "Pistons");
        put("勇士", "Golden State Warriors", "Warriors");
        put("火箭", "Houston Rockets", "Rockets");
        put("步行者", "Indiana Pacers", "Pacers");
        put("快船", "LA Clippers", "Los Angeles Clippers", "Clippers");
        put("湖人", "Los Angeles Lakers", "Lakers");
        put("灰熊", "Memphis Grizzlies", "Grizzlies");
        put("热火", "Miami Heat", "Heat");
        put("雄鹿", "Milwaukee Bucks", "Bucks");
        put("森林狼", "Minnesota Timberwolves", "Timberwolves");
        put("鹈鹕", "New Orleans Pelicans", "Pelicans");
        put("尼克斯", "New York Knicks", "Knicks");
        put("雷霆", "Oklahoma City Thunder", "Thunder", "OKC Thunder");
        put("魔术", "Orlando Magic", "Magic");
        put("76人", "Philadelphia 76ers", "76ers", "Sixers");
        put("太阳", "Phoenix Suns", "Suns");
        put("开拓者", "Portland Trail Blazers", "Trail Blazers", "Blazers");
        put("国王", "Sacramento Kings", "Kings");
        put("马刺", "San Antonio Spurs");
        put("猛龙", "Toronto Raptors", "Raptors");
        put("爵士", "Utah Jazz", "Jazz");
        put("奇才", "Washington Wizards", "Wizards");
    }

    private MiguTeams() {}

    static String chinese(String team) {
        return team == null ? null : BY_ENGLISH.get(normalize(team));
    }

    /** League-aware lookup for the two short names that exist in both sports. */
    static String chinese(String league, String team) {
        String key = normalize(team);
        if ("epl".equals(league)) {
            if ("spurs".equals(key)) return "托特纳姆热刺";
            if ("wolves".equals(key)) return "狼队";
        } else if ("nba".equals(league)) {
            if ("spurs".equals(key)) return "马刺";
            if ("wolves".equals(key)) return "森林狼";
        }
        return BY_ENGLISH.get(key);
    }

    static boolean sameChinese(String a, String b) {
        String left = chineseKey(a), right = chineseKey(b);
        if (left.isEmpty() || right.isEmpty()) return false;
        return left.equals(right) || left.contains(right) || right.contains(left);
    }

    static String normalize(String value) {
        if (value == null) return "";
        String text = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT)
            .replace('&', ' ')
            .replace("'", "")
            .replaceAll("[^a-z0-9]+", " ")
            .trim();
        // Club suffixes do not distinguish teams in this table.
        return text.replaceAll("\\b(fc|afc|cf|sc|club|the|football|basketball)\\b", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    static String chineseKey(String value) {
        if (value == null) return "";
        return value.replaceAll("[\\s·\\-]", "")
            .replace("足球俱乐部", "")
            .replace("篮球俱乐部", "")
            .replace("俱乐部", "")
            .replace("队", "");
    }

    private static void put(String chinese, String... english) {
        for (String name : english) BY_ENGLISH.put(normalize(name), chinese);
    }
}
