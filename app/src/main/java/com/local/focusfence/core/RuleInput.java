package com.local.focusfence.core;

/** Invalid / empty input must never silently turn a finite quota into unlimited use. */
public final class RuleInput {
    private RuleInput() {}
    public static int quota(String text) {
        if (text == null || !text.matches("[0-9]{1,4}")) return -1;
        try { int n = Integer.parseInt(text); return n <= 1440 ? n : -1; }
        catch (NumberFormatException e) { return -1; }
    }
    public static boolean validWindow(boolean allDay, int start, int end) {
        return start >= 0 && start < 1440 && end >= 0 && end < 1440
                && (allDay || start != end);
    }
}
