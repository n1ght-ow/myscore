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
    static final DateTimeFormatter MONTH_DAY = date("MMM d");
    static final DateTimeFormatter YEAR_MONTH_DAY = date("M/d/yyyy");
    static final DateTimeFormatter WEEKDAY = date("EEE");
    static final DateTimeFormatter MONTH_DAY_WEEK = date("EEE, MMM d");
    static final DateTimeFormatter MONTH_DAY_FULL_WEEK = date("EEEE, MMM d");
    static final DateTimeFormatter YEAR_MONTH = date("MMM yyyy");
    static final DateTimeFormatter MONTH_DAY_DOT = date("MM.dd");
    static final DateTimeFormatter YEAR_MONTH_DAY_WEEK = date("EEE, MMM d, yyyy");
    /** Source timestamps arrive as Beijing-local text, without a zone suffix. */

    static DateTimeFormatter zoned(String pattern) {
        DateTimeFormatter formatter = ZONED.get(pattern);
        if (formatter != null) return formatter;
        return ZONED.computeIfAbsent(pattern, p -> DateTimeFormatter.ofPattern(displayPattern(p), Locale.ENGLISH).withZone(Match.ZONE));
    }

    static DateTimeFormatter date(String pattern) {
        DateTimeFormatter formatter = DATES.get(pattern);
        if (formatter != null) return formatter;
        return DATES.computeIfAbsent(pattern, p -> DateTimeFormatter.ofPattern(displayPattern(p), Locale.ENGLISH));
    }

    private static String displayPattern(String pattern) {
        return pattern
            .replace("yyyy年M月d日 EEEE", "EEEE, MMM d, yyyy")
            .replace("yyyy年M月d日 EEE", "EEE, MMM d, yyyy")
            .replace("yyyy年M月d日", "MMM d, yyyy")
            .replace("yyyy年M月", "MMM yyyy")
            .replace("M月d日 EEEE", "EEEE, MMM d")
            .replace("M月d日 EEE", "EEE, MMM d")
            .replace("M月d日", "MMM d");
    }

    /** Epoch millis as Beijing time; {@code fallback} when the source has no timestamp. */
    static String instant(long millis, String pattern, String fallback) {
        return millis > 0 ? zoned(pattern).format(Instant.ofEpochMilli(millis)) : fallback;
    }

    static String date(LocalDate day, DateTimeFormatter formatter, String fallback) {
        return day == null ? fallback : formatter.format(day);
    }

    /** "Updated HH:mm" without allocating a formatter for every bound row. */
    static String updated(long millis) {
        return millis > 0 ? "Updated " + TIME.format(Instant.ofEpochMilli(millis)) : "Update time unknown";
    }

    static String kickoff(long millis, String fallback) {
        return millis > 0 ? TIME.format(Instant.ofEpochMilli(millis)) : fallback;
    }
}
