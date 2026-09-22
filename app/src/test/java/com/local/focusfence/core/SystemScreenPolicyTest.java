package com.local.focusfence.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class SystemScreenPolicyTest {
 @Test public void lateSettingsEventCannotTargetBernard(){assertFalse(SystemScreenPolicy.matchesWindow("com.android.settings",3,"com.local.focusfence",7));}
 @Test public void otherWindowOfSamePackageIsNotCurrent(){assertFalse(SystemScreenPolicy.matchesWindow("com.android.settings",3,"com.android.settings",4));}
 @Test public void absentWindowIsNotEvidence(){assertFalse(SystemScreenPolicy.matchesWindow("com.android.settings",-1,"com.android.settings",-1));}
 @Test public void matchingWindowIsRecognized(){assertTrue(SystemScreenPolicy.matchesWindow("com.android.settings",3,"com.android.settings",3));}
 @Test public void widgetCannotReplaceActivityClass(){assertFalse(SystemScreenPolicy.isActivityClass("com.android.settings","android.widget.Switch"));assertFalse(SystemScreenPolicy.isActivityClass("com.android.settings","com.android.settings.widget.SettingsMainSwitchBar"));}
 @Test public void realAdminClassSurvivesWidgetEvent(){assertTrue(SystemScreenPolicy.isActivityClass("com.android.settings","com.android.settings.DeviceAdminAdd"));}
 @Test public void apostrophesAndAccentsAreNormalized(){assertTrue(SystemScreenPolicy.matchesFlow("device_admin","android.widget.LinearLayout","Activer cet administrateur de l’appareil"));}
 @Test public void updateGrantCannotAuthorizeAppInfo(){assertFalse(SystemScreenPolicy.matchesFlow("update","com.android.settings.applications.InstalledAppDetails","Bernard Bloqueur Forcer l’arrêt Désinstaller"));}
 @Test public void usageGrantCannotAuthorizeGenericSpecialAccess(){assertFalse(SystemScreenPolicy.matchesFlow("usage","com.android.settings.SpecialAccess","Special app access"));}
 @Test public void unknownSourcesIsAnUpdatePermissionSurface(){assertTrue(SystemScreenPolicy.matchesFlow("update","android.widget.FrameLayout","Autoriser depuis cette source"));}
 @Test public void appInfoSuboptionCannotAuthorizeUpdateFlow(){
  assertFalse(SystemScreenPolicy.matchesFlow("update","com.android.settings.SubSettings","Bernard Bloqueur\nFORCE STOP\nInstall unknown apps"));
  assertFalse(SystemScreenPolicy.matchesFlow("update","com.android.settings.SubSettings","Bernard Bloqueur\nForcer l’arrêt\nInstaller applis inconnues"));
  assertFalse(SystemScreenPolicy.matchesFlow("update","com.android.settings.applications.InstalledAppDetails","Allow from this source"));
  assertTrue(SystemScreenPolicy.matchesFlow("update","com.android.settings.Settings$ManageExternalSourcesActivity","Allow from this source"));
 }
}
