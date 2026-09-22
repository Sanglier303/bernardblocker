package com.local.focusfence.core;

/** Pure, deterministic rules shared by the UI and accessibility service. */
public final class Rules {
    private Rules() {}
    public static boolean allowed(int minute, int start, int end) {
        if (minute < 0 || minute >= 1440 || start < 0 || start >= 1440 || end < 0 || end >= 1440)
            throw new IllegalArgumentException("Minutes must be between 0 and 1439");
        if (start == end) return true; // Explicit all-day representation, including overnight windows.
        return start < end ? minute >= start && minute < end : minute >= start || minute < end;
    }
    public static boolean exhausted(long usedMs, int minutes) {
        return minutes > 0 && Math.max(0, usedMs) >= minutes * 60_000L;
    }
    public static long remaining(long usedMs, int minutes) {
        return minutes <= 0 ? -1 : Math.max(0, minutes * 60_000L - Math.max(0, usedMs));
    }
    public static int remainingMinutes(long usedMs, int minutes) {
        long ms = remaining(usedMs, minutes);
        return ms < 0 ? -1 : (int) ((ms + 59_999) / 60_000);
    }
    /** Reaching an enforced limit is a success, not a failure. No reward before a day ends. */
    public static boolean successful(boolean closed, boolean observed, boolean hasGoal,
                                     long shortMs, int shortLimit, long gamesMs, int gamesLimit,
                                     boolean otherGoalsMet) {
        return closed && observed && hasGoal && otherGoalsMet
                && (shortLimit <= 0 || shortMs <= shortLimit * 60_000L + 1_500L)
                && (gamesLimit <= 0 || gamesMs <= gamesLimit * 60_000L + 1_500L);
    }
    public static String clock(int minute) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
    }
}
