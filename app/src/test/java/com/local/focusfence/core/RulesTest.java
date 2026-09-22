package com.local.focusfence.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class RulesTest {
 @Test public void dayWindow(){assertTrue(Rules.allowed(1080,1080,1380));assertFalse(Rules.allowed(1079,1080,1380));}
 @Test public void endIsExclusive(){assertFalse(Rules.allowed(1380,1080,1380));assertTrue(Rules.allowed(1379,1080,1380));}
 @Test public void overnight(){assertTrue(Rules.allowed(30,1320,120));assertTrue(Rules.allowed(1400,1320,120));assertFalse(Rules.allowed(120,1320,120));assertFalse(Rules.allowed(800,1320,120));}
 @Test public void allDay(){for(int minute=0;minute<1440;minute++)assertTrue(Rules.allowed(minute,0,0));}
 @Test(expected=IllegalArgumentException.class) public void invalidNow(){Rules.allowed(-1,0,100);}
 @Test(expected=IllegalArgumentException.class) public void invalidEnd(){Rules.allowed(15,0,1440);}
 @Test public void boundaryExhausts(){assertTrue(Rules.exhausted(60_000,1));assertFalse(Rules.exhausted(59_999,1));}
 @Test public void unlimited(){assertFalse(Rules.exhausted(999_999,0));assertEquals(-1,Rules.remainingMinutes(999,0));}
 @Test public void remainingRoundsUp(){assertEquals(1,Rules.remainingMinutes(59_999,1));assertEquals(0,Rules.remainingMinutes(60_000,1));assertEquals(0,Rules.remainingMinutes(999_999,1));}
 @Test public void negativeUsageClamped(){assertEquals(20,Rules.remainingMinutes(-300,20));}
 @Test public void reachedLimitIsStillSuccess(){assertTrue(Rules.successful(true,true,true,60_000,1,120_000,2,true));}
 @Test public void unfinishedDayIsNotRewarded(){assertFalse(Rules.successful(false,true,true,0,20,0,45,true));}
 @Test public void unobservedDayIsNotRewarded(){assertFalse(Rules.successful(true,false,true,0,20,0,45,true));}
 @Test public void noGoalIsNotRewarded(){assertFalse(Rules.successful(true,true,false,0,0,0,0,true));}
 @Test public void pollingJitterDoesNotEraseUsageOrDenyReward(){assertTrue(Rules.successful(true,true,true,61_500,1,0,0,true));}
 @Test public void exceededShortGoal(){assertFalse(Rules.successful(true,true,true,61_501,1,0,0,true));}
 @Test public void exceededGameGoal(){assertFalse(Rules.successful(true,true,true,0,20,180_000,2,true));}
 @Test public void anotherApplicationExceeded(){assertFalse(Rules.successful(true,true,true,0,20,0,45,false));}
 @Test public void clock(){assertEquals("08:05",Rules.clock(485));}
}
