package com.local.focusfence.core;

import java.util.*;

/** Replay foreground intervals and clip to the requested day. Group sums do not double-count. */
public final class UsageTimeline {
    public static final int OPEN = 1, CLOSE = 2, SCREEN_OFF = 3;
    public static final class Event {
        public final long at; public final int kind; public final String pkg;
        public Event(long at, int kind, String pkg) { this.at=at; this.kind=kind; this.pkg=pkg; }
    }
    public static Map<String,Long> count(List<Event> events, long start, long end) {
        Map<String,Long> totals = new HashMap<>();
        if (end <= start) return totals;
        String active = null; long since = start;
        for (Event e: events) {
            if (e.at > end) break;
            if (e.kind == OPEN) {
                if (Objects.equals(active,e.pkg)) continue;
                add(totals,active,since,e.at,start,end);
                active=e.pkg; since=e.at;
            } else if (e.kind==SCREEN_OFF || (e.kind==CLOSE && Objects.equals(active,e.pkg))) {
                add(totals,active,since,e.at,start,end); active=null;
            }
        }
        add(totals,active,since,end,start,end);
        return totals;
    }
    private static void add(Map<String,Long> out,String pkg,long a,long b,long start,long end) {
        if(pkg==null) return;
        long d=Math.max(0,Math.min(b,end)-Math.max(a,start));
        out.put(pkg,out.getOrDefault(pkg,0L)+d);
    }
}
