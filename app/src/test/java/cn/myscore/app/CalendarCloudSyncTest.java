package cn.myscore.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CalendarCloudSyncTest {
    @Test
    public void statusExplainsMissingCalendarPermissionWithoutShizuku() {
        String status = CalendarCloudSync.describeStatus(false, false, false, false, 0, 0, 0);
        assertTrue(status.contains("未开启日历权限"));
        assertFalse(status.contains("Shizuku"));
    }

    @Test
    public void statusOffersXiaomiSwitchWhenLocalCalendarSelected() {
        assertEquals(
            "当前为本机日历 · 点此切换至小米日历",
            CalendarCloudSync.describeStatus(true, true, true, true, 1, 0, 0)
        );
        assertEquals(
            "本机日历 · 登录小米账户后可跨设备同步",
            CalendarCloudSync.describeStatus(true, true, true, false, 0, 0, 0)
        );
    }

    @Test
    public void statusReflectsPendingAndSyncedXiaomiSubscriptions() {
        assertEquals(
            "小米云同步中 · 约 1–3 分钟送达其他设备",
            CalendarCloudSync.describeStatus(true, true, false, true, 1, 1, 0)
        );
        assertEquals(
            "小米云同步中 · 约 1–3 分钟送达其他设备",
            CalendarCloudSync.describeStatus(true, true, false, true, 0, 0, 1)
        );
        assertEquals(
            "小米日历自动同步 · 1 场订阅已上云",
            CalendarCloudSync.describeStatus(true, true, false, true, 1, 0, 0)
        );
        assertEquals(
            "小米日历自动同步 · 手机与平板自动互通",
            CalendarCloudSync.describeStatus(true, true, false, true, 0, 0, 0)
        );
    }
}
