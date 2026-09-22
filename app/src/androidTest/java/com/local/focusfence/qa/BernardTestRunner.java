package com.local.focusfence.qa;
import android.app.UiAutomation;
import androidx.test.runner.AndroidJUnitRunner;
import java.io.File;
import java.io.PrintWriter;
import java.util.Map;
/** Test-only runner: bounded diagnostics do not require the main thread or an accessibility root. */
public final class BernardTestRunner extends AndroidJUnitRunner {
 @Override public UiAutomation getUiAutomation(){return super.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);}
 @Override public UiAutomation getUiAutomation(int flags){return super.getUiAutomation(flags|UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);}
 @Override public void onStart(){
  File evidence=new File(getTargetContext().getFilesDir(),"qa-thread-dump.txt");
  Thread watchdog=new Thread(()->{
   try{Thread.sleep(70_000L);try(PrintWriter out=new PrintWriter(evidence)){
    for(Map.Entry<Thread,StackTraceElement[]> entry:Thread.getAllStackTraces().entrySet()){
     out.println(entry.getKey().getName()+" "+entry.getKey().getState());
     for(StackTraceElement frame:entry.getValue())out.println("  at "+frame);
    }
   }}catch(Exception failure){System.err.println("QA watchdog: "+failure);}
  },"bernard-qa-watchdog");watchdog.setDaemon(true);watchdog.start();
  super.onStart();
 }
}
