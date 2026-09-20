package com.local.focusfence.util;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

public final class UsageUtils {
    private UsageUtils() {}

    public static long todayUsageMs(Context context, String packageName) {
        return todayUsageMs(context, java.util.Collections.singleton(packageName));
    }

    public static long todayUsageMs(Context context, Set<String> packages) {
        if (packages == null || packages.isEmpty()) return 0L;
        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return 0L;
        long start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = System.currentTimeMillis();
        List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end);
        long total = 0L;
        if (stats != null) {
            for (UsageStats s : stats) {
                if (packages.contains(s.getPackageName())) total += Math.max(0L, s.getTotalTimeInForeground());
            }
        }
        return total;
    }
}
