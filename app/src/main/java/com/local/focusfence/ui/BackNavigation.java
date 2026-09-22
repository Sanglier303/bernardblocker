package com.local.focusfence.ui;
import android.app.Activity;
import android.os.Build;
import android.window.OnBackInvokedDispatcher;
/** API 36 no longer dispatches legacy onBackPressed by default. Keep identical cancellation rules. */
final class BackNavigation {
 private BackNavigation(){}
 static void install(Activity activity,Runnable back){
  if(Build.VERSION.SDK_INT>=33)activity.getOnBackInvokedDispatcher()
          .registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,back::run);
 }
}
