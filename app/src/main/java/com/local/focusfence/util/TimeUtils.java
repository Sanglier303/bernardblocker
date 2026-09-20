package com.local.focusfence.util;
import java.time.LocalTime;
import com.local.focusfence.core.Rules;
public final class TimeUtils {
    private TimeUtils() {}
    public static int nowMinute(){LocalTime t=LocalTime.now();return t.getHour()*60+t.getMinute();}
    public static boolean isInsideAllowedWindow(int now,int start,int end){return Rules.allowed(now,Math.max(0,Math.min(1439,start)),Math.max(0,Math.min(1439,end)));}
    public static String formatMinute(int minute){return Rules.clock(Math.max(0,Math.min(1439,minute)));}
}
