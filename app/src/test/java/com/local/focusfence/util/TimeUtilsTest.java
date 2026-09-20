package com.local.focusfence.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TimeUtilsTest {
    @Test public void daytimeWindow() {
        assertFalse(TimeUtils.isInsideAllowedWindow(17 * 60 + 59, 18 * 60, 23 * 60 + 30));
        assertTrue(TimeUtils.isInsideAllowedWindow(18 * 60, 18 * 60, 23 * 60 + 30));
        assertTrue(TimeUtils.isInsideAllowedWindow(23 * 60 + 30, 18 * 60, 23 * 60 + 30));
        assertFalse(TimeUtils.isInsideAllowedWindow(23 * 60 + 31, 18 * 60, 23 * 60 + 30));
    }

    @Test public void overnightWindow() {
        assertTrue(TimeUtils.isInsideAllowedWindow(23 * 60, 22 * 60, 2 * 60));
        assertTrue(TimeUtils.isInsideAllowedWindow(60, 22 * 60, 2 * 60));
        assertFalse(TimeUtils.isInsideAllowedWindow(12 * 60, 22 * 60, 2 * 60));
    }

    @Test public void equalBoundsMeanAllDay() {
        assertTrue(TimeUtils.isInsideAllowedWindow(0, 500, 500));
        assertTrue(TimeUtils.isInsideAllowedWindow(1439, 500, 500));
    }

    @Test public void boundsAreClamped() {
        assertTrue(TimeUtils.isInsideAllowedWindow(0, -50, 60));
        assertEquals("00:00", TimeUtils.formatMinute(-1));
        assertEquals("23:59", TimeUtils.formatMinute(9999));
    }
}
