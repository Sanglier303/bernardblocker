package com.local.focusfence.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class RemainingTimeTest {
 @Test public void lastMillisecondIsNotAdvertisedAsOneMinute(){assertEquals("1 s",Rules.remainingTime(59_999,1));}
 @Test public void lastMinuteIsExact(){assertEquals("1 min",Rules.remainingTime(0,1));assertEquals("59 s",Rules.remainingTime(1000,1));}
 @Test public void minuteAndSecondsAreShown(){assertEquals("1:30",Rules.remainingTime(30_000,2));}
 @Test public void exhaustedAndUnlimitedRemainDistinct(){assertEquals("0 min",Rules.remainingTime(60_000,1));assertEquals("Illimité",Rules.remainingTime(60_000,0));}
}
