package cn.myscore.app;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide, immutable formatters. DateTimeFormatter is thread-safe, and creating one per
 * row bind was measurable on long lists and live ten-second refreshes.
 */
final class Formats {
    private Formats() {}

    private static final ConcurrentHashMap<String, DateTimeFormatter> ZONED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, DateTimeFormatter> DATES = new ConcurrentHashMap<>();

    /** Beijing wall-clock time for an epoch timestamp. */
    static final DateTimeFormatter TIME = zoned("HH:mm");
    static final DateTimeFormatter MONTH_DAY = date("M月d日");
    static final DateTimeFormatter YEAR_MONTH_DAY = date("yyyy/M/d");
    static final DateTimeFormatter WEEKDAY = date("EEE");
    static final DateTimeFormatter MONTH_DAY_WEEK = date("M月d日 EEE");
    static final DateTimeFormatter MONTH_DAY_FULL_WEEK = date("M月d日 EEEE");
    static final DateTimeFormatter YEAR_MONTH = date("yyyy年M月");
    static final DateTimeFormatter MONTH_DAY_DOT = date("MM.dd");
    static final DateTimeFormatter YEAR_MONTH_DAY_WEEK = date("yyyy年M月d日 EEE");
    /** Source timestamps arrive as Beijing-local text, without a zone suffix. */
    static final DateTimeFormatter SOURCE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA);

    static DateTimeFormatter zoned(String pattern) {
        DateTimeFormatter formatter = ZONED.get(pattern);
        if (formatter != null) return formatter;
        return ZONED.computeIfAbsent(pattern, p -> DateTimeFormatter.ofPattern(p, Locale.CHINA).withZone(Match.ZONE));
    }

    static DateTimeFormatter date(String pattern) {
        DateTimeFormatter formatter = DATES.get(pattern);
        if (formatter != null) return formatter;
        return DATES.computeIfAbsent(pattern, p -> DateTimeFormatter.ofPattern(p, Locale.CHINA));
    }

    /** Epoch millis as Beijing time; {@code fallback} when the source has no timestamp. */
    static String instant(long millis, String pattern, String fallback) {
        return millis > 0 ? zoned(pattern).format(Instant.ofEpochMilli(millis)) : fallback;
    }

    static String date(LocalDate day, DateTimeFormatter formatter, String fallback) {
        return day == null ? fallback : formatter.format(day);
    }

    /** "更新于 HH:mm" without allocating a formatter for every bound row. */
    static String updated(long millis) {
        return millis > 0 ? "更新于 " + TIME.format(Instant.ofEpochMilli(millis)) : "更新时间未知";
    }

    static String kickoff(long millis, String fallback) {
        return millis > 0 ? TIME.format(Instant.ofEpochMilli(millis)) : fallback;
    }
}
