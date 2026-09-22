package com.local.focusfence.ui;
import android.app.UiAutomation;
import android.content.*;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.local.focusfence.security.*;
import com.local.focusfence.storage.*;
import com.local.focusfence.util.AccessEvaluator;
import com.local.focusfence.model.AppRule;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class HardeningRegressionTest {
 private Context c;private Prefs p;
 @Before public void setup()throws Exception{
  c=InstrumentationRegistry.getInstrumentation().getTargetContext();
  UiAutomation ui=InstrumentationRegistry.getInstrumentation().getUiAutomation();
  try(ParcelFileDescriptor fd=ui.executeShellCommand("settings put secure enabled_accessibility_services null");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] b=new byte[1024];while(in.read(b)!=-1){}}
  ServiceTestSupport.awaitStopped();p=new Prefs(c);p.raw().edit().clear().commit();
  c.getSharedPreferences("bernard_pin_v4",0).edit().clear().commit();PinGuard.lockNow();PinGuard.clearSystemControlAuthorization();
 }
 @After public void cleanup(){PinGuard.lockNow();PinGuard.clearSystemControlAuthorization();}
 private AppRule rule(){AppRule r=new AppRule();r.packageName="com.bernard.fixture";r.label="Fixture";return r;}
 private long usage(){return new Journal(c).shortMs();}
 private void seedUsage(){long end=java.time.LocalDate.now().atTime(12,0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();new Journal(c).addShort(end,42000);}
 @Test public void freshInstallHasNoUniversalCredential(){assertFalse(PinGuard.ensureConfigured(c));assertTrue(PinGuard.canEnroll(c));assertTrue(PinGuard.setPin(c,TestCredentials.pin()));assertEquals(6,PinGuard.pinLength(c));assertFalse(PinGuard.canEnroll(c));}
 @Test public void replacementWithoutAuthenticationIsDenied(){TestCredentials.seed(c);assertFalse(PinGuard.setPin(c,"639482".toCharArray()));assertTrue(PinGuard.verify(c,TestCredentials.pin()));}
 @Test public void legacyCredentialOnlyGrantsPrivateRotation()throws Exception{
  p.setOnboardingDone(true);p.saveAppRule(rule());seedUsage();
  byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);
  char[] legacy="3957".toCharArray();PBEKeySpec key=new PBEKeySpec(legacy,salt,180000,256);
  byte[] hash=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(key).getEncoded();key.clearPassword();
  c.getSharedPreferences("bernard_pin_v4",0).edit().putString("salt",Base64.encodeToString(salt,Base64.NO_WRAP)).putString("hash",Base64.encodeToString(hash,Base64.NO_WRAP)).commit();
  assertTrue(PinGuard.needsUpgrade(c));assertTrue(PinGuard.verify(c,legacy));assertFalse(PinGuard.isAuthorized());assertFalse(PinGuard.isSystemControlAuthorized());
  assertTrue(PinGuard.isPinChangeAuthorized());assertTrue(PinGuard.beginPinChange());assertTrue(PinGuard.setPin(c,TestCredentials.pin()));
  assertFalse(PinGuard.needsUpgrade(c));assertEquals(1,p.getAppRules().size());assertEquals(42000,usage());
  PinGuard.lockNow();assertFalse(PinGuard.verify(c,"3957".toCharArray()));assertTrue(PinGuard.verify(c,TestCredentials.pin()));
 }
 @Test public void missingCredentialDoesNotReopenEnrollment(){TestCredentials.seed(c);c.getSharedPreferences("bernard_pin_v4",0).edit().clear().commit();assertFalse(PinGuard.ensureConfigured(c));assertFalse(PinGuard.canEnroll(c));}
 @Test public void malformedCredentialDoesNotReopenEnrollment(){p.setOnboardingDone(true);c.getSharedPreferences("bernard_pin_v4",0).edit().putBoolean("hash",true).commit();assertFalse(PinGuard.isConfigured(c));assertFalse(PinGuard.canEnroll(c));}
 @Test public void cancelledChangeRevokesGrant(){TestCredentials.seed(c);assertTrue(PinGuard.verify(c,TestCredentials.pin()));assertTrue(PinGuard.beginPinChange());PinGuard.lockNow();assertFalse(PinGuard.setPin(c,"639482".toCharArray()));}
 @Test public void firstRuleReadIsStable(){assertTrue(p.getAppRules().isEmpty());assertTrue(new Prefs(c).getAppRules().isEmpty());assertFalse(p.appRulesCorrupt());assertFalse(p.tamperLock());}
 @Test public void corruptRulesRetainLastValidSnapshot(){p.saveAppRule(rule());seedUsage();p.raw().edit().putString("app_rules","broken").commit();assertEquals(1,new Prefs(c).getAppRules().size());assertTrue(p.appRulesCorrupt());assertTrue(p.tamperLock());assertFalse(p.appRulesUnavailable());assertEquals(42000,usage());}
 @Test public void corruptRulesCannotBeSilentlyOverwritten(){p.saveAppRule(rule());p.raw().edit().putString("app_rules","broken").commit();try{p.saveAppRule(rule());fail("Corrupt rules replaced silently");}catch(IllegalStateException expected){}}
 @Test public void unauthenticatedRepairIsDenied(){p.saveAppRule(rule());p.raw().edit().putString("app_rules","broken").commit();assertFalse(p.repairAppRules(true));}
 @Test public void authenticatedRepairPreservesCounters(){TestCredentials.seed(c);p.saveAppRule(rule());seedUsage();p.raw().edit().putString("app_rules","broken").commit();assertTrue(p.appRulesCorrupt());assertTrue(PinGuard.verify(c,TestCredentials.pin()));assertTrue(p.repairAppRules(false));assertEquals(1,p.getAppRules().size());assertFalse(p.appRulesCorrupt());assertTrue(p.tamperLock());assertEquals(42000,usage());}
 @Test public void noSnapshotBlocksUntrustedAppsButKeepsRecovery(){p.raw().edit().putString("app_rules","broken").commit();assertTrue(p.appRulesUnavailable());assertTrue(new AccessEvaluator(c).evaluate("com.bernard.fixture",null).blocked());assertFalse(new AccessEvaluator(c).evaluate(c.getPackageName(),null).blocked());assertTrue(RecoveryPolicy.essential(c,"com.android.phone"));}
 @Test public void resetWithoutGoodSnapshotRequiresConfirmation(){p.raw().edit().putString("app_rules","broken").commit();PinGuard.authorize();assertFalse(p.repairAppRules(false));assertTrue(p.repairAppRules(true));assertFalse(p.appRulesCorrupt());assertTrue(p.tamperLock());}
 @Test public void concurrentRuleWritersDoNotLoseEntries()throws Exception{
  Thread[] workers=new Thread[8];for(int i=0;i<workers.length;i++){final int n=i;workers[i]=new Thread(()->{AppRule r=rule();r.packageName="com.bernard.fixture"+n;new Prefs(c).saveAppRule(r);});workers[i].start();}
  for(Thread worker:workers)worker.join(4000);assertEquals(8,p.getAppRules().size());
 }

 @Test public void diagnosticEvidenceIsOptInAndCleared()throws Exception{
  org.json.JSONObject evidence=new org.json.JSONObject().put("surface","UNKNOWN_OR_UTILITY");
  DetectorEvidence.record(p,evidence);assertEquals(0,DetectorEvidence.entries(p).length());
  p.setDiagnosticMode(true);DetectorEvidence.record(p,evidence);assertEquals(1,DetectorEvidence.entries(p).length());
  p.setDiagnosticMode(false);p.setDiagnosticMode(true);assertEquals(0,DetectorEvidence.entries(p).length());
 }
 @Test public void corruptDiagnosticEntriesDoNotInterruptProtection()throws Exception{
  p.setDiagnosticMode(true);p.raw().edit().putString("detector_evidence_v47","[null,4,false,{}]").commit();
  DetectorEvidence.record(p,new org.json.JSONObject().put("surface","UNKNOWN_OR_UTILITY"));
  assertEquals(2,DetectorEvidence.entries(p).length());
  org.json.JSONArray many=new org.json.JSONArray();for(int n=0;n<30;n++)many.put(new org.json.JSONObject().put("at",1).put("surface","UNKNOWN_OR_UTILITY"));
  p.raw().edit().putString("detector_evidence_v47",many.toString()).commit();
  DetectorEvidence.record(p,new org.json.JSONObject().put("surface","INSTAGRAM_REELS"));
  assertEquals(12,DetectorEvidence.entries(p).length());assertEquals("INSTAGRAM_REELS",DetectorEvidence.entries(p).getJSONObject(11).getString("surface"));
 }
 @Test public void detectorEvidenceDoesNotExportScreenText()throws Exception{
  android.view.accessibility.AccessibilityNodeInfo root=android.view.accessibility.AccessibilityNodeInfo.obtain();
  root.setVisibleToUser(true);root.setViewIdResourceName("com.instagram.android:id/message_list");root.setText("PRIVATE MESSAGE");root.setContentDescription("PRIVATE ACCOUNT");
  try{org.json.JSONObject exported=new com.local.focusfence.detector.ShortSurfaceDetector().diagnosticEvidence("com.instagram.android",root,null);String raw=exported.toString();assertFalse(raw.contains("PRIVATE"));assertTrue(raw.contains("message_list"));assertNull(new com.local.focusfence.detector.ShortSurfaceDetector().diagnosticEvidence("com.android.chrome",root,null));}finally{root.recycle();}
 }
}
