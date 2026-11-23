package cn.myscore.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CalendarCloudSyncTest {
    @Test
    public void statusExplainsMissingCalendarPermissionWithoutShizuku() {
        String status = CalendarCloudSync.describeStatus(false, false, false, false, 0, 0, 0);
        assertTrue(status.contains("Calendar permission is off"));
        assertFalse(status.contains("Shizuku"));
    }

    @Test
    public void statusOffersXiaomiSwitchWhenLocalCalendarSelected() {
        assertEquals(
            "Using this device's calendar · Tap to switch to Xiaomi Calendar",
            CalendarCloudSync.describeStatus(true, true, true, true, 1, 0, 0)
        );
        assertEquals(
            "This device's calendar · Sign in to a Xiaomi account for cross-device sync",
            CalendarCloudSync.describeStatus(true, true, true, false, 0, 0, 0)
        );
    }

    @Test
    public void statusReflectsPendingAndSyncedXiaomiSubscriptions() {
        assertEquals(
            "Syncing via Xiaomi Cloud · Usually reaches other devices in 1–3 minutes",
            CalendarCloudSync.describeStatus(true, true, false, true, 1, 1, 0)
        );
        assertEquals(
            "Syncing via Xiaomi Cloud · Usually reaches other devices in 1–3 minutes",
            CalendarCloudSync.describeStatus(true, true, false, true, 0, 0, 1)
        );
        assertEquals(
            "Xiaomi Calendar syncs automatically · 1 subscription uploaded",
            CalendarCloudSync.describeStatus(true, true, false, true, 1, 0, 0)
        );
        assertEquals(
            "Xiaomi Calendar syncs automatically · Phone and tablet stay in sync",
            CalendarCloudSync.describeStatus(true, true, false, true, 0, 0, 0)
        );
    }
}
