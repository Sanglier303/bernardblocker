package com.local.focusfence.core;

import java.util.*;

/** Replay visible foreground intervals, clipped to the requested local day. */
public final class UsageTimeline {
    public static final int OPEN = 1, CLOSE = 2, SCREEN_OFF = 3, SCREEN_ON = 4, SHUTDOWN = 5;
    public static final class Event {
        public final long at;
        public final int kind;
        public final String pkg, activity;
        public Event(long at, int kind, String pkg) { this(at, kind, pkg, ""); }
        public Event(long at, int kind, String pkg, String activity) {
            this.at = at; this.kind = kind; this.pkg = pkg;
            this.activity = activity == null ? "" : activity;
        }
    }
    public static Map<String, Long> count(List<Event> events, long start, long end) {
        Map<String, Long> totals = new HashMap<>();
        if (end <= start) return totals;
        String active = null, activity = "";
        long since = start;
        boolean interactive = true;
        List<Event> ordered = new ArrayList<>(events);
        ordered.sort(Comparator.comparingLong(e -> e.at));
        for (Event e : ordered) {
            if (e.at > end) break;
            if (e.kind == OPEN) {
                if (Objects.equals(active, e.pkg)) { activity = e.activity; continue; }
                if (interactive) add(totals, active, since, e.at, start, end);
                active = e.pkg; activity = e.activity; since = e.at;
            } else if (e.kind == CLOSE && Objects.equals(active, e.pkg)
                    && (activity.isEmpty() || e.activity.isEmpty() || activity.equals(e.activity))) {
                if (interactive) add(totals, active, since, e.at, start, end);
                active = null; activity = "";
            } else if (e.kind == SCREEN_OFF) {
                if (interactive) add(totals, active, since, e.at, start, end);
                interactive = false;
            } else if (e.kind == SCREEN_ON) {
                if (!interactive) since = e.at;
                interactive = true;
            } else if (e.kind == SHUTDOWN) {
                if (interactive) add(totals, active, since, e.at, start, end);
                active = null; activity = ""; interactive = false;
            }
        }
        if (interactive) add(totals, active, since, end, start, end);
        return totals;
    }
    private static void add(Map<String, Long> out, String pkg, long a, long b, long start, long end) {
        if (pkg == null) return;
        long d = Math.max(0, Math.min(b, end) - Math.max(a, start));
        out.put(pkg, out.getOrDefault(pkg, 0L) + d);
    }
}
