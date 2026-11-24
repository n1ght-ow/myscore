package cn.myscore.app;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class StandingsEnglish {
    private static final DateTimeFormatter RETRIEVED =
        DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH).withZone(Match.ZONE);
    private static final String[] MONTHS = {
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    };
    private static final Pattern CUP_DATE = Pattern.compile("(\\d{1,2})月(\\d{1,2})日\\s+(\\d{1,2}:\\d{2})");
    private static final Pattern NAMED_AGGREGATE = Pattern.compile("(.+?) 总比分 (\\d+)[–-](\\d+) 晋级");
    private static final Pattern AGGREGATE = Pattern.compile("总比分 (\\d+)[–-](\\d+) 晋级");

    private static final Map<String, String> TEXT = Map.ofEntries(
        Map.entry("英超", "Premier League"),
        Map.entry("欧冠", "Champions League"),
        Map.entry("足总杯", "FA Cup"),
        Map.entry("卡拉宝杯", "Carabao Cup"),
        Map.entry("积分榜", "Standings"),
        Map.entry("联赛阶段", "League phase"),
        Map.entry("东部", "East"),
        Map.entry("西部", "West"),
        Map.entry("东区", "East"),
        Map.entry("北区", "North"),
        Map.entry("南区", "South"),
        Map.entry("西区", "West"),
        Map.entry("分区", "Division"),
        Map.entry("车手", "Drivers"),
        Map.entry("车队", "Constructors"),
        Map.entry("车手积分", "Drivers"),
        Map.entry("车队积分", "Constructors"),
        Map.entry("全球", "Global"),
        Map.entry("欧洲", "Europe"),
        Map.entry("美洲", "Americas"),
        Map.entry("亚洲", "Asia"),
        Map.entry("欧冠区", "Champions League"),
        Map.entry("欧联区", "Europa League"),
        Map.entry("欧协联区", "Conference League"),
        Map.entry("降级区", "Relegation"),
        Map.entry("前八区", "Top 8"),
        Map.entry("附加赛非种子区", "Playoffs unseeded"),
        Map.entry("附加赛种子区", "Playoffs seeded"),
        Map.entry("附加赛区", "Playoffs"),
        Map.entry("淘汰区", "Eliminated"),
        Map.entry("预赛", "Preliminary round"),
        Map.entry("资格赛", "Qualifying"),
        Map.entry("小组赛", "Group stage"),
        Map.entry("第一轮", "First round"),
        Map.entry("第二轮", "Second round"),
        Map.entry("第三轮", "Third round"),
        Map.entry("第四轮", "Fourth round"),
        Map.entry("第五轮", "Fifth round"),
        Map.entry("1/8决赛", "Round of 16"),
        Map.entry("1/4决赛", "Quarter-finals"),
        Map.entry("半决赛", "Semi-finals"),
        Map.entry("三四名决赛", "Third-place match"),
        Map.entry("决赛", "Final"),
        Map.entry("首回合", "First leg"),
        Map.entry("次回合", "Second leg"),
        Map.entry("重赛", "Replay"),
        Map.entry("进行中", "Live"),
        Map.entry("暂停", "Suspended"),
        Map.entry("延期", "Postponed"),
        Map.entry("中止", "Abandoned"),
        Map.entry("取消", "Cancelled"),
        Map.entry("待开赛", "Scheduled"),
        Map.entry("加时", "AET"),
        Map.entry("主队待定", "Home team TBD"),
        Map.entry("客队待定", "Away team TBD"),
        Map.entry("名次", "Rank"),
        Map.entry("顺位", "Rank"),
        Map.entry("战队", "Team"),
        Map.entry("球队", "Team")
    );

    private static final Map<String, String> TEAM_NAMES = Map.ofEntries(
        Map.entry("曼联", "Manchester United"),
        Map.entry("阿斯顿维拉", "Aston Villa"),
        Map.entry("布莱顿", "Brighton"),
        Map.entry("布伦特福德", "Brentford"),
        Map.entry("水晶宫", "Crystal Palace"),
        Map.entry("利物浦", "Liverpool"),
        Map.entry("伊普斯维奇", "Ipswich"),
        Map.entry("伯恩茅斯", "Bournemouth"),
        Map.entry("桑德兰", "Sunderland"),
        Map.entry("切尔西", "Chelsea"),
        Map.entry("诺丁汉森林", "Nottingham Forest"),
        Map.entry("曼城", "Manchester City"),
        Map.entry("阿森纳", "Arsenal"),
        Map.entry("赫尔城", "Hull"),
        Map.entry("富勒姆", "Fulham"),
        Map.entry("纽卡斯尔联", "Newcastle"),
        Map.entry("利兹联", "Leeds"),
        Map.entry("托特纳姆热刺", "Tottenham"),
        Map.entry("埃弗顿", "Everton"),
        Map.entry("考文垂", "Coventry"),
        Map.entry("西汉姆联", "West Ham"),
        Map.entry("狼队", "Wolves"),
        Map.entry("伯恩利", "Burnley"),
        Map.entry("莱斯特城", "Leicester"),
        Map.entry("南安普顿", "Southampton"),
        Map.entry("诺维奇", "Norwich"),
        Map.entry("沃特福德", "Watford"),
        Map.entry("谢菲尔德联", "Sheffield United"),
        Map.entry("卢顿", "Luton"),
        Map.entry("老鹰", "Atlanta Hawks"),
        Map.entry("凯尔特人", "Boston Celtics"),
        Map.entry("篮网", "Brooklyn Nets"),
        Map.entry("黄蜂", "Charlotte Hornets"),
        Map.entry("公牛", "Chicago Bulls"),
        Map.entry("骑士", "Cleveland Cavaliers"),
        Map.entry("独行侠", "Dallas Mavericks"),
        Map.entry("掘金", "Denver Nuggets"),
        Map.entry("活塞", "Detroit Pistons"),
        Map.entry("勇士", "Golden State Warriors"),
        Map.entry("火箭", "Houston Rockets"),
        Map.entry("步行者", "Indiana Pacers"),
        Map.entry("快船", "LA Clippers"),
        Map.entry("湖人", "Los Angeles Lakers"),
        Map.entry("灰熊", "Memphis Grizzlies"),
        Map.entry("热火", "Miami Heat"),
        Map.entry("雄鹿", "Milwaukee Bucks"),
        Map.entry("森林狼", "Minnesota Timberwolves"),
        Map.entry("鹈鹕", "New Orleans Pelicans"),
        Map.entry("尼克斯", "New York Knicks"),
        Map.entry("雷霆", "Oklahoma City Thunder"),
        Map.entry("魔术", "Orlando Magic"),
        Map.entry("76人", "Philadelphia 76ers"),
        Map.entry("太阳", "Phoenix Suns"),
        Map.entry("开拓者", "Portland Trail Blazers"),
        Map.entry("国王", "Sacramento Kings"),
        Map.entry("马刺", "San Antonio Spurs"),
        Map.entry("猛龙", "Toronto Raptors"),
        Map.entry("爵士", "Utah Jazz"),
        Map.entry("奇才", "Washington Wizards")
    );

    private StandingsEnglish() {}

    static String text(String value) {
        if (value == null || value.isBlank()) return "";
        String mapped = TEXT.get(value);
        if (mapped != null) return mapped;
        Matcher date = CUP_DATE.matcher(value);
        if (date.matches()) {
            int month = Integer.parseInt(date.group(1));
            if (month >= 1 && month <= 12) {
                return MONTHS[month - 1] + " " + Integer.parseInt(date.group(2)) + ", " + date.group(3);
            }
        }
        Matcher named = NAMED_AGGREGATE.matcher(value);
        if (named.matches()) {
            return named.group(1).trim() + " advance " + named.group(2) + "–" + named.group(3) + " on aggregate";
        }
        Matcher aggregate = AGGREGATE.matcher(value);
        if (aggregate.matches()) {
            return "Advanced " + aggregate.group(1) + "–" + aggregate.group(2) + " on aggregate";
        }
        if (value.contains(" · ")) {
            String[] parts = value.split(" · ", -1);
            StringBuilder translated = new StringBuilder();
            for (String part : parts) {
                if (translated.length() > 0) translated.append(" · ");
                translated.append(text(part));
            }
            return translated.toString();
        }
        if (value.startsWith("点球 ")) return "Pens " + value.substring(3);
        if (value.endsWith(" 榜单")) {
            String prefix = value.substring(0, value.length() - 3).trim();
            return prefix.matches("\\d{4}-\\d{2}-\\d{2}") ? "VRS " + prefix : prefix + " standings";
        }
        return value;
    }

    static String name(String value) {
        if (value == null || value.isBlank()) return "";
        String mapped = TEAM_NAMES.get(value);
        return mapped == null ? text(value) : mapped;
    }

    static String column(String competition, String value) {
        return switch (value) {
            case "积分" -> "Pts";
            case "场" -> "P";
            case "胜" -> "W";
            case "负" -> "L";
            case "平" -> "D";
            case "胜率" -> "Win %";
            case "胜差" -> "GB";
            case "净胜" -> "nfl".equals(competition) ? "Diff" : "GD";
            case "进球" -> "GF";
            case "失球" -> "GA";
            case "主场" -> "Home";
            case "客场" -> "Away";
            case "近10场" -> "L10";
            case "走势" -> "Streak";
            case "得分" -> "PF";
            case "失分" -> "PA";
            case "胜场" -> "W";
            case "扣分" -> "Ded";
            default -> text(value);
        };
    }

    static String retrieved(long millis) {
        return millis > 0 ? RETRIEVED.format(Instant.ofEpochMilli(millis)) : "";
    }
}
