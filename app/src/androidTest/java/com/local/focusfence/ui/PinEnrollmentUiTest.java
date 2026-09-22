package com.local.focusfence.ui;

import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.local.focusfence.security.PinGuard;
import com.local.focusfence.storage.Prefs;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.InputStream;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import static org.junit.Assert.*;

/** Exercises the actual PinActivity views and lifecycle; credentials belong only to this test APK. */
@RunWith(AndroidJUnit4.class)
public class PinEnrollmentUiTest {
 private Context c;private Prefs p;
 @Before public void setup()throws Exception{
  c=InstrumentationRegistry.getInstrumentation().getTargetContext();
  UiAutomation ui=InstrumentationRegistry.getInstrumentation().getUiAutomation();
  try(ParcelFileDescriptor fd=ui.executeShellCommand("settings put secure enabled_accessibility_services null");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] b=new byte[1024];while(in.read(b)!=-1){}}
  ServiceTestSupport.awaitStopped();p=new Prefs(c);p.raw().edit().clear().commit();
  c.getSharedPreferences("bernard_pin_v4",0).edit().clear().commit();PinGuard.lockNow();PinGuard.clearSystemControlAuthorization();
 }
 @After public void cleanup(){PinGuard.lockNow();PinGuard.clearSystemControlAuthorization();}
 private ActivityScenario<PinActivity> launch(){return ActivityScenario.launch(new Intent(c,PinActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));}
 private static View find(View v,String label){
  if(v instanceof TextView&&label.contentEquals(((TextView)v).getText()))return v;
  if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),label);if(found!=null)return found;}
  return null;
 }
 private void heading(ActivityScenario<PinActivity> a,String title){a.onActivity(x->assertNotNull(title,find(x.getWindow().getDecorView(),title)));}
 private void enter(ActivityScenario<PinActivity> a,String digits){for(char digit:digits.toCharArray())a.onActivity(x->{View key=find(x.getWindow().getDecorView(),String.valueOf(digit));assertNotNull(key);assertTrue(key.isEnabled());assertTrue(key.performClick());});}
 private void legacy()throws Exception{
  p.setOnboardingDone(true);byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);
  PBEKeySpec key=new PBEKeySpec("3957".toCharArray(),salt,180000,256);
  byte[] hash=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(key).getEncoded();key.clearPassword();
  c.getSharedPreferences("bernard_pin_v4",0).edit().putString("salt",Base64.encodeToString(salt,Base64.NO_WRAP)).putString("hash",Base64.encodeToString(hash,Base64.NO_WRAP)).commit();
 }
 @Test public void freshPinRequiresMatchingConfirmationThroughViews(){
  try(ActivityScenario<PinActivity> a=launch()){
   heading(a,"Créer le code administrateur");enter(a,"826493");heading(a,"Confirmer le code");
   assertFalse(PinGuard.isConfigured(c));assertFalse(PinGuard.isAuthorized());
   enter(a,"639482");heading(a,"Créer le code administrateur");assertFalse(PinGuard.isConfigured(c));
   enter(a,"826493");enter(a,"826493");assertTrue(PinGuard.isConfigured(c));assertFalse(PinGuard.needsUpgrade(c));
  }
 }
 @Test public void legacyPinCannotFinishBeforePrivateRotation()throws Exception{
  legacy();try(ActivityScenario<PinActivity> a=launch()){
   heading(a,"Bernard garde les réglages");enter(a,"3957");heading(a,"Choisir un nouveau code privé");
   assertFalse(PinGuard.isAuthorized());assertFalse(PinGuard.isSystemControlAuthorized());
   enter(a,"826493");heading(a,"Confirmer le code");assertTrue(PinGuard.needsUpgrade(c));
   enter(a,"826493");assertFalse(PinGuard.needsUpgrade(c));assertEquals(6,PinGuard.pinLength(c));
  }
 }
 @Test public void cancellingLegacyRotationDoesNotReplaceCredential()throws Exception{
  legacy();try(ActivityScenario<PinActivity> a=launch()){
   enter(a,"3957");enter(a,"826493");
   a.onActivity(x->{View cancel=find(x.getWindow().getDecorView(),"Annuler la configuration");assertNotNull(cancel);assertTrue(cancel.performClick());});
   assertTrue(PinGuard.needsUpgrade(c));assertFalse(PinGuard.isAuthorized());assertFalse(PinGuard.isPinChangeAuthorized());
  }
 }
 @Test public void backgroundedRotationReturnsToOldPinVerification()throws Exception{
  legacy();try(ActivityScenario<PinActivity> a=launch()){
   enter(a,"3957");enter(a,"826493");heading(a,"Confirmer le code");
   a.moveToState(Lifecycle.State.CREATED);a.moveToState(Lifecycle.State.RESUMED);
   heading(a,"Bernard garde les réglages");assertFalse(PinGuard.isPinChangeAuthorized());
   enter(a,"3957");heading(a,"Choisir un nouveau code privé");assertFalse(PinGuard.isAuthorized());
  }
 }
 @Test public void systemBackCancelsPrivateRotation()throws Exception{
  legacy();try(ActivityScenario<PinActivity> a=launch()){
   enter(a,"3957");assertTrue(PinGuard.isPinChangeAuthorized());
   assertTrue(InstrumentationRegistry.getInstrumentation().getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK));
   long end=android.os.SystemClock.elapsedRealtime()+4000;
   while(PinGuard.isPinChangeAuthorized()&&android.os.SystemClock.elapsedRealtime()<end)android.os.SystemClock.sleep(50);
   assertFalse(PinGuard.isPinChangeAuthorized());assertFalse(PinGuard.isAuthorized());assertTrue(PinGuard.needsUpgrade(c));
  }
 }
}
