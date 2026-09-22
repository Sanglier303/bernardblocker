package com.local.focusfence.core;

import java.time.Instant;
import java.time.ZoneId;

/** A clock-change notification alone is not evidence of tampering. */
public final class ClockPolicy {
    public static final long TOLERANCE_MS=5_000L;
    private ClockPolicy(){}
    public static boolean discontinuity(long previousWall,long previousElapsed,String previousZone,
                                        long wall,long elapsed,String zone){
        if(previousZone==null||zone==null||!previousZone.equals(zone)||elapsed<previousElapsed)return true;
        try{
            long expected=Math.addExact(previousWall,Math.subtractExact(elapsed,previousElapsed));
            long drift=Math.subtractExact(wall,expected);
            if(drift < -TOLERANCE_MS || drift > TOLERANCE_MS)return true;
            ZoneId z=ZoneId.of(zone);
            // Even a small adjustment must not move the quota into another calendar day.
            return !Instant.ofEpochMilli(expected).atZone(z).toLocalDate()
                    .equals(Instant.ofEpochMilli(wall).atZone(z).toLocalDate());
        }catch(RuntimeException invalid){return true;}
    }
    public static boolean mustLockOnNotification(boolean automaticTime,boolean automaticZone,
            long previousWall,long previousElapsed,String previousZone,long wall,long elapsed,String zone){
        return !automaticTime||!automaticZone||discontinuity(previousWall,previousElapsed,previousZone,wall,elapsed,zone);
    }
}
