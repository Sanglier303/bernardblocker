package com.local.focusfence.util;
import static org.junit.Assert.*;
import org.junit.Test;
public class TimeUtilsTest {
 @Test public void clampedFormatting(){assertEquals("00:00",TimeUtils.formatMinute(-1));assertEquals("23:59",TimeUtils.formatMinute(9999));}
 @Test public void forwardAndOvernight(){assertTrue(TimeUtils.isInsideAllowedWindow(1080,1080,1380));assertFalse(TimeUtils.isInsideAllowedWindow(1380,1080,1380));assertTrue(TimeUtils.isInsideAllowedWindow(20,1320,120));}
}
