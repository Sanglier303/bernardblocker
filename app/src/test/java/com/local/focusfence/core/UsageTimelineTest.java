package com.local.focusfence.core;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class UsageTimelineTest{
 private UsageTimeline.Event e(long t,int type,String pkg){return new UsageTimeline.Event(t,type,pkg);}
 @Test public void singleSession(){assertEquals(Long.valueOf(50),UsageTimeline.count(Arrays.asList(e(10,1,"a"),e(60,2,"a")),0,100).get("a"));}
 @Test public void ongoingSession(){assertEquals(Long.valueOf(90),UsageTimeline.count(Arrays.asList(e(10,1,"a")),0,100).get("a"));}
 @Test public void midnightClipping(){assertEquals(Long.valueOf(40),UsageTimeline.count(Arrays.asList(e(10,1,"a"),e(90,2,"a")),50,100).get("a"));}
 @Test public void backgroundBeforeMidnightDoesNotLeak(){assertEquals(Long.valueOf(0),UsageTimeline.count(Arrays.asList(e(10,1,"a"),e(40,2,"a")),50,100).get("a"));}
 @Test public void appSwitchDoesNotDoubleCount(){Map<String,Long> m=UsageTimeline.count(Arrays.asList(e(10,1,"a"),e(30,1,"b"),e(35,2,"a")),0,100);assertEquals(Long.valueOf(20),m.get("a"));assertEquals(Long.valueOf(70),m.get("b"));}
 @Test public void duplicateResumeDoesNotResetSession(){assertEquals(Long.valueOf(90),UsageTimeline.count(Arrays.asList(e(10,1,"a"),e(30,1,"a")),0,100).get("a"));}
 @Test public void screenOffStopsCounter(){assertEquals(Long.valueOf(50),UsageTimeline.count(Arrays.asList(e(10,1,"a"),e(60,3,null)),0,100).get("a"));}
 @Test public void emptyRange(){assertTrue(UsageTimeline.count(Collections.emptyList(),100,50).isEmpty());}

 @Test public void screenOnRestartsExistingActivity(){assertEquals(Long.valueOf(80),UsageTimeline.count(Arrays.asList(e(10,1,"a"),e(50,3,null),e(60,4,null)),0,100).get("a"));}
 @Test public void latePauseOfPreviousActivityDoesNotStopNewActivity(){
  List<UsageTimeline.Event> e=Arrays.asList(new UsageTimeline.Event(10,1,"a","one"),new UsageTimeline.Event(30,1,"a","two"),new UsageTimeline.Event(35,2,"a","one"));
  assertEquals(Long.valueOf(90),UsageTimeline.count(e,0,100).get("a"));
 }
 @Test public void shutdownCannotResumeOldPackage(){assertEquals(Long.valueOf(40),UsageTimeline.count(Arrays.asList(e(10,1,"a"),e(50,5,null),e(60,4,null)),0,100).get("a"));}
 @Test public void outOfOrderInputIsDeterministic(){assertEquals(Long.valueOf(50),UsageTimeline.count(Arrays.asList(e(60,2,"a"),e(10,1,"a")),0,100).get("a"));}
}
