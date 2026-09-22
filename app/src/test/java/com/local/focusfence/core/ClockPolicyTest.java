package com.local.focusfence.core;

import java.time.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClockPolicyTest {
    private static final String Z="Europe/Paris";
    private static final long W=Instant.parse("2026-09-22T10:00:00Z").toEpochMilli();
    private boolean event(boolean time,boolean zone,long correction){
        return ClockPolicy.mustLockOnNotification(time,zone,W,1000,Z,W+10_000+correction,11_000,Z);
    }
    @Test public void unchangedAutomaticClockNotificationDoesNotLock(){assertFalse(event(true,true,0));}
    @Test public void smallAutomaticForwardCorrectionDoesNotLock(){assertFalse(event(true,true,2000));}
    @Test public void smallAutomaticBackwardCorrectionDoesNotLock(){assertFalse(event(true,true,-2000));}
    @Test public void toleranceBoundaryIsExplicit(){assertFalse(event(true,true,5000));assertTrue(event(true,true,5001));assertTrue(event(true,true,-5001));}
    @Test public void manualClockStillLocksEvenWithoutDrift(){assertTrue(event(false,true,0));}
    @Test public void manualTimezoneStillLocksEvenWithoutDrift(){assertTrue(event(true,false,0));}
    @Test public void realTimezoneChangeStillLocks(){assertTrue(ClockPolicy.discontinuity(W,1000,Z,W+1000,2000,"UTC"));}
    @Test public void monotonicClockRollbackFailsClosed(){assertTrue(ClockPolicy.discontinuity(W,1000,Z,W,999,Z));}
    @Test public void smallCorrectionCannotAdvanceQuotaDay(){
        long before=LocalDate.of(2026,9,22).atTime(23,59,59).atZone(ZoneId.of(Z)).toInstant().toEpochMilli();
        assertTrue(ClockPolicy.discontinuity(before,1000,Z,before+2000,1000,Z));
    }
    @Test public void smallCorrectionCannotRewindQuotaDay(){
        long after=LocalDate.of(2026,9,23).atTime(0,0,1).atZone(ZoneId.of(Z)).toInstant().toEpochMilli();
        assertTrue(ClockPolicy.discontinuity(after,1000,Z,after-2000,1000,Z));
    }
    @Test public void naturalMidnightIsNotTampering(){
        long before=LocalDate.of(2026,9,22).atTime(23,59,59).atZone(ZoneId.of(Z)).toInstant().toEpochMilli();
        assertFalse(ClockPolicy.discontinuity(before,1000,Z,before+2000,3000,Z));
    }
    @Test public void daylightSavingTransitionIsNotTampering(){
        long before=Instant.parse("2026-10-25T00:59:59Z").toEpochMilli();
        assertFalse(ClockPolicy.discontinuity(before,1000,Z,before+2000,3000,Z));
    }
    @Test public void idleClockJumpAlsoFailsClosed(){assertTrue(ClockPolicy.discontinuity(W,1000,Z,W+60_000,2000,Z));}
    @Test public void invalidAndOverflowingAnchorsFailClosed(){
        assertTrue(ClockPolicy.discontinuity(W,1000,"invalid",W,1000,"invalid"));
        assertTrue(ClockPolicy.discontinuity(Long.MAX_VALUE,0,Z,Long.MAX_VALUE,1,Z));
        assertTrue(ClockPolicy.discontinuity(W,1000,null,W,1000,Z));
    }
}
