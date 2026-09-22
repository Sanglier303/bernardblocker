package com.local.focusfence.ui;
import android.content.Context;
import com.local.focusfence.security.PinGuard;
/** TEST APK ONLY. No credential is supplied to the production APK or a user's installation. */
public final class TestCredentials {
 private TestCredentials(){}
 public static char[] pin(){return "826493".toCharArray();}
 public static void seed(Context c){
  PinGuard.authorize();
  if(!PinGuard.setPin(c,pin()))throw new AssertionError("Test credential could not be persisted");
  PinGuard.lockNow();
 }
}
