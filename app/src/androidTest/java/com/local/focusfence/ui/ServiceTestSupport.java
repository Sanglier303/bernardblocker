package com.local.focusfence.ui;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import com.local.focusfence.service.FocusAccessibilityService;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
/** Wait for the real main-thread unbind, not the journal flag reset by another test. */
final class ServiceTestSupport {
 private ServiceTestSupport(){}
 static void awaitStopped(){
  long deadline=SystemClock.elapsedRealtime()+6000;
  do{
   AtomicBoolean connected=new AtomicBoolean(true);
   InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
    try{
     Field running=FocusAccessibilityService.class.getDeclaredField("running");running.setAccessible(true);
     Object service=((WeakReference<?>)running.get(null)).get();
     if(service==null){connected.set(false);return;}
     Field state=FocusAccessibilityService.class.getDeclaredField("connected");state.setAccessible(true);
     connected.set(state.getBoolean(service));
    }catch(ReflectiveOperationException e){throw new AssertionError(e);}
   });
   // The main-thread barrier above also waits for all writes made by onUnbind to finish.
   if(!connected.get())return;
   SystemClock.sleep(100);
  }while(SystemClock.elapsedRealtime()<deadline);
  throw new AssertionError("Accessibility service did not finish unbinding before test reset");
 }
}
