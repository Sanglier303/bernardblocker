package com.local.focusfence.core;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.local.focusfence.core.AccessPolicy.Reason.*;
public class AccessPolicyTest {
    private AccessPolicy.Reason social(int minute,int start,int end,long used,int cap){
        return AccessPolicy.decide(true,false,false,false,true,used,cap,minute,start,end);
    }
    @Test public void extendingWindowReopensWithSameRemainingQuota(){
        assertEquals(SCHEDULE,social(900,1080,1320,180000,20));
        assertEquals(ALLOWED,social(900,840,1320,180000,20));
        assertEquals(17,Rules.remainingMinutes(180000,20));
    }
    @Test public void narrowingWindowBlocksEvenWithTimeLeft(){
        assertEquals(ALLOWED,social(900,840,1320,180000,20));
        assertEquals(SCHEDULE,social(900,1080,1320,180000,20));
    }
    @Test public void scheduleChangeDoesNotResetExhaustedQuota(){
        assertEquals(QUOTA,social(900,1080,1320,1200000,20));
        assertEquals(QUOTA,social(900,0,0,1200000,20));
    }
    @Test public void unlimitedQuotaStillRespectsWindow(){
        assertEquals(SCHEDULE,social(900,1080,1320,180000,0));
        assertEquals(ALLOWED,social(900,0,0,Long.MAX_VALUE,0));
    }
    @Test public void timeWithinWindowAndQuotaIsAllowedForEveryMinute(){
        int[][] ranges={{480,1320},{1320,120},{0,0},{0,1},{1439,0}};
        for(int[] r:ranges)for(int m=0;m<1440;m++){
            boolean expected=r[0]==r[1] || (r[0]<r[1] ? m>=r[0]&&m<r[1] : m>=r[0]||m<r[1]);
            assertEquals(expected?ALLOWED:SCHEDULE,social(m,r[0],r[1],1,20));
        }
    }
    @Test public void automaticOpeningAndEndBoundary(){
        assertEquals(SCHEDULE,social(479,480,1320,1,20));
        assertEquals(ALLOWED,social(480,480,1320,1,20));
        assertEquals(SCHEDULE,social(1320,480,1320,1,20));
    }
    @Test public void newDailyCounterOnlyReopensInsideWindow(){
        assertEquals(QUOTA,social(1439,1320,120,1200000,20));
        assertEquals(ALLOWED,social(0,1320,120,0,20));
        assertEquals(SCHEDULE,social(0,480,1320,0,20));
    }
    @Test public void changingWindowDoesNotOverrideTamper(){
        assertEquals(TAMPER,AccessPolicy.decide(true,true,false,false,true,0,20,900,0,0));
    }
    @Test public void missingUsageStaysClosedForFiniteGames(){
        assertEquals(USAGE_PERMISSION,AccessPolicy.decide(true,false,false,true,false,0,60,900,0,0));
    }
    @Test public void independentAppRuleWinsOverSocialAllowance(){
        assertEquals(ALWAYS_BLOCKED,AccessPolicy.strongest(ALLOWED,ALWAYS_BLOCKED));
        assertEquals(QUOTA,AccessPolicy.strongest(SCHEDULE,QUOTA));
        assertEquals(TAMPER,AccessPolicy.strongest(TAMPER,ALLOWED));
    }
    @Test public void disabledRuleCannotRetainItsPreviousBlock(){
        assertEquals(ALLOWED,AccessPolicy.decide(false,true,true,true,false,999999,1,900,1080,1320));
    }
}
