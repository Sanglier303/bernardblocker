package com.local.focusfence.security;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.telecom.TelecomManager;
/** Keep owner recovery and emergency calling reachable when a whole rule set is unreadable. */
public final class RecoveryPolicy {
 private RecoveryPolicy(){}
 public static boolean essential(Context c,String pkg){
  if(pkg==null||pkg.isEmpty()||pkg.equals(c.getPackageName())||pkg.equals("android")
     ||pkg.equals("com.android.systemui")||pkg.equals("com.android.settings")
     ||pkg.equals("com.android.phone")||pkg.equals("com.android.emergency"))return true;
  try{
   ResolveInfo home=c.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0);
   if(home!=null&&home.activityInfo!=null&&pkg.equals(home.activityInfo.packageName))return true;
   TelecomManager telecom=(TelecomManager)c.getSystemService(Context.TELECOM_SERVICE);
   if(telecom!=null&&pkg.equals(telecom.getDefaultDialerPackage()))return true;
  }catch(RuntimeException ignored){ /* Unknown apps do not gain a recovery exemption. */ }
  return false;
 }
}
