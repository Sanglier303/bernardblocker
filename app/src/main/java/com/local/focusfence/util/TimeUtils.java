package com.local.focusfence.util;

import java.time.LocalTime;
import java.util.Locale;

public final class TimeUtils {
    private TimeUtils() {}

    public static int nowMinute() {
        LocalTime t = LocalTime.now();
        return t.getHour() * 60 + t.getMinute();
    }

    public static boolean isInsideAllowedWindow(int now, int start, int end) {
        start = clamp(start);
        end = clamp(end);
        if (start == end) return true;
        if (start < end) return now >= start && now <= end;
        return now >= start || now <= end;
    }

    public static String formatMinute(int minute) {
        minute = clamp(minute);
        return String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
    }

    private static int clamp(int minute) {
        return Math.max(0, Math.min(1439, minute));
    }
}
