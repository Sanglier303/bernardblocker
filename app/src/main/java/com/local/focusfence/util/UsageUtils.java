package com.local.focusfence.util;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.SystemClock;
import com.local.focusfence.core.UsageTimeline;
import java.time.*;
import java.util.*;

public final class UsageUtils {
    private UsageUtils() {}
    private static Map<String,Long> cache=Collections.emptyMap();
    private static LocalDate cacheDay; private static long cacheAt;
    public static synchronized Map<String,Long> today(Context c) {
        LocalDate date=LocalDate.now();long t=SystemClock.elapsedRealtime();
        if(!date.equals(cacheDay) || t-cacheAt>500L){cache=forDate(c,date);cacheDay=date;cacheAt=t;}
        return new HashMap<>(cache);
    }
    public static Map<String,Long> forDate(Context c,LocalDate date) {
        if(!PermissionUtils.hasUsageAccess(c)) return Collections.emptyMap();
        long start=date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end=Math.min(System.currentTimeMillis(),date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
        UsageStatsManager manager=(UsageStatsManager)c.getSystemService(Context.USAGE_STATS_SERVICE);
        if(manager==null || end<=start)return Collections.emptyMap();
        List<UsageTimeline.Event> events=new ArrayList<>();
        try {
            // Look back to recover a foreground session already open at midnight.
            UsageEvents stream=manager.queryEvents(start-86_400_000L,end);
            if(stream==null)return Collections.emptyMap();
            UsageEvents.Event e=new UsageEvents.Event();
            while(stream.hasNextEvent()){
                stream.getNextEvent(e);int type=e.getEventType(),kind=0;
                if(type==UsageEvents.Event.MOVE_TO_FOREGROUND)kind=UsageTimeline.OPEN;
                else if(type==UsageEvents.Event.MOVE_TO_BACKGROUND)kind=UsageTimeline.CLOSE;
                else if(type==UsageEvents.Event.SCREEN_NON_INTERACTIVE)kind=UsageTimeline.SCREEN_OFF;
                else if(type==UsageEvents.Event.SCREEN_INTERACTIVE)kind=UsageTimeline.SCREEN_ON;
                else if(type==UsageEvents.Event.DEVICE_SHUTDOWN || type==UsageEvents.Event.DEVICE_STARTUP)kind=UsageTimeline.SHUTDOWN;
                if(kind!=0){
                    // getClassName is public since API 21. Instance IDs are not exposed by the
                    // public UsageEvents SDK; never use hidden APIs/reflection for accounting.
                    // Different instances of the same Activity class remain indistinguishable.
                    events.add(new UsageTimeline.Event(e.getTimeStamp(),kind,e.getPackageName(),e.getClassName()));
                }
            }
            return UsageTimeline.count(events,start,end);
        } catch(SecurityException ex){return Collections.emptyMap();}
    }
    public static long sum(Map<String,Long> usage,Set<String> packages){long total=0;for(String pkg:packages)total+=usage.getOrDefault(pkg,0L);return total;}
    public static long todayUsageMs(Context c,String pkg){return today(c).getOrDefault(pkg,0L);}
    public static long todayUsageMs(Context c,Set<String> packages){return sum(today(c),packages);}
}
