package com.local.focusfence.security;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.content.pm.ResolveInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.local.focusfence.storage.Prefs;
import com.local.focusfence.model.AppRule;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;
/** Dedicated emulator DO phase. Does not disable ADB or apply destructive user restrictions. */
@RunWith(AndroidJUnit4.class)
public class DeviceOwnerPolicyTest {
 private Context c;private DevicePolicyManager d;private Prefs p;
 @Before public void before(){c=InstrumentationRegistry.getInstrumentation().getTargetContext();d=(DevicePolicyManager)c.getSystemService(Context.DEVICE_POLICY_SERVICE);assertTrue("CI must really provision Device Owner",FortressPolicy.isDeviceOwner(c));p=new Prefs(c);p.raw().edit().clear().commit();p.setShortEnabled(false);}
 @After public void after(){d.setPackagesSuspended(FortressPolicy.admin(c),new String[]{"com.bernard.fixture"},false);d.setUninstallBlocked(FortressPolicy.admin(c),c.getPackageName(),false);}
 @Test public void uninstallAloneIsNotFortress(){d.setUninstallBlocked(FortressPolicy.admin(c),c.getPackageName(),true);assertTrue(d.isUninstallBlocked(FortressPolicy.admin(c),c.getPackageName()));assertFalse("Partial state must not be announced as complete",FortressPolicy.isEnforced(c));assertFalse(FortressPolicy.missingPolicies(c).isEmpty());}
 @Test public void suspendedFixtureIsRecoveredAfterRuleRemoval()throws Exception{
  AppRule rule=new AppRule();rule.packageName="com.bernard.fixture";rule.label="Fixture";p.saveAppRule(rule);
  assertTrue(FortressPolicy.setFailSafeSuspended(c,true));assertTrue(d.isPackageSuspended(FortressPolicy.admin(c),rule.packageName));
  p.removeAppRule(rule.packageName);assertTrue(FortressPolicy.setFailSafeSuspended(c,false));assertFalse(d.isPackageSuspended(FortressPolicy.admin(c),rule.packageName));
 }
 @Test public void androidPartialSuspensionFailureIsReported()throws Exception{
  ResolveInfo home=c.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0);assertNotNull(home);assertNotNull(home.activityInfo);
  p.setGamesEnabled(true);p.setGamePackages(new HashSet<>(Arrays.asList("com.bernard.fixture",home.activityInfo.packageName)));
  assertFalse("Default launcher cannot be suspended; success would be false",FortressPolicy.setFailSafeSuspended(c,true));
  assertTrue("Independent target must still be suspended",d.isPackageSuspended(FortressPolicy.admin(c),"com.bernard.fixture"));
  assertFalse(d.isPackageSuspended(FortressPolicy.admin(c),home.activityInfo.packageName));assertTrue(p.tamperLock());assertFalse(FortressPolicy.lastFailure(c).isEmpty());
  assertTrue(FortressPolicy.setFailSafeSuspended(c,false));
 }
}
