package cn.myscore.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.time.LocalDate;
import org.junit.Test;

/** The shared formatters must keep the exact Beijing-time text the UI already ships. */
public final class FormatsTest {
    private static final long BEIJING_2200 = 1793109600000L;

    @Test public void clockUsesBeijingTimeWithoutAllocatingPerRow() {
        Match match = new Match();
        match.start = BEIJING_2200;
        assertEquals("22:00", match.time());
        assertEquals("更新于 22:00", Formats.updated(BEIJING_2200));
        assertEquals("更新时间未知", Formats.updated(0));
        assertEquals("时间待定", Formats.kickoff(0, "时间待定"));
    }

    @Test public void dateLabelsStayChinese() {
        LocalDate day = LocalDate.of(2026, 10, 27);
        assertEquals("10月27日", Formats.MONTH_DAY.format(day));
        assertEquals("周二", Formats.WEEKDAY.format(day));
        assertEquals("2026年10月", Formats.YEAR_MONTH.format(day));
        assertEquals("日期待定", Formats.date(null, Formats.MONTH_DAY_WEEK, "日期待定"));
    }

    @Test public void lookupsReuseProcessWideInstances() {
        assertSame(Formats.TIME, Formats.zoned("HH:mm"));
        assertSame(Formats.WEEKDAY, Formats.date("EEE"));
        assertEquals("10/27 22:00", Formats.zoned("M/d HH:mm").format(java.time.Instant.ofEpochMilli(BEIJING_2200)));
    }
}
