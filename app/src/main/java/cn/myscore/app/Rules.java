package cn.myscore.app;

import java.time.LocalDate;
import java.util.Locale;

public final class Rules {
    private Rules() {}

    public static boolean shouldImport(Match m, long now) {
        if (!m.timeConfirmed || !m.scheduled() || m.start <= now || m.day() == null) return false;
        LocalDate today = Match.today(now);
        return !m.day().isBefore(today) && !m.day().isAfter(today.plusDays(1));
    }

    public static boolean isWorlds(String name, String slug) {
        String text=(name+" "+slug).toLowerCase(Locale.ROOT).replace('-', ' ');
        return !text.contains("esports world cup") && (text.matches(".*\\bworlds\\b.*") || text.contains("world championship"));
    }

    public static boolean isSpirit(int firstId, int secondId, int discipline) {
        return discipline==1 && (firstId==654 || secondId==654);
    }

    public static String state(String name, String rawState) {
        String n=name.toLowerCase(Locale.ROOT);
        if(n.contains("cancel") || n.contains("abandon")) return "cancelled";
        if(n.contains("postpon") || n.contains("delay") || n.contains("suspend")) return "delayed";
        if(n.contains("final") || n.equals("finished") || n.equals("defwin") || rawState.equals("post")) return "final";
        if(rawState.equals("in") || n.equals("current") || n.equals("running")) return "live";
        if(rawState.equals("pre") || n.equals("upcoming") || n.contains("scheduled")) return "scheduled";
        return "unknown";
    }

    public static long estimatedDuration(String group) {
        return switch(group) {
            case "NFL" -> 4*60*60_000L;
            case "NBA", "CS2", "LOL" -> 3*60*60_000L;
            default -> 2*60*60_000L;
        };
    }

    public static String stateLabel(String state) {
        return switch(state) {
            case "live" -> "进行中";
            case "scheduled" -> "未开始";
            case "final" -> "已结束";
            case "cancelled" -> "已取消";
            case "delayed" -> "延期 / 暂停";
            default -> "状态待确认";
        };
    }
}
