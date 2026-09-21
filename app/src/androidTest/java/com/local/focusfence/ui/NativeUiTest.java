package com.local.focusfence.ui;

import android.app.UiAutomation;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.local.focusfence.core.Rules;
import com.local.focusfence.security.PinGuard;
import com.local.focusfence.storage.*;
import com.local.focusfence.util.TimeUtils;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.Assert.*;

/** Emulator-only fixtures. These values are never inserted into the shipping APK or user preferences. */
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class NativeUiTest {
    private Context c;private Prefs p;private UiAutomation automation;
    @Before public void before()throws Exception{
        c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        automation=InstrumentationRegistry.getInstrumentation().getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        android.accessibilityservice.AccessibilityServiceInfo info=automation.getServiceInfo();info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;automation.setServiceInfo(info);
        shell("settings put secure enabled_accessibility_services null");SystemClock.sleep(300);
        p=new Prefs(c);p.raw().edit().clear().commit();p.setOnboardingDone(true);Journal.monitoring=false;PinGuard.setPin(c,new char[]{'1','2','3','4'});PinGuard.authorize();PinGuard.clearSystemControlAuthorization();
        shell("appops set "+c.getPackageName()+" GET_USAGE_STATS allow");
    }
    @After public void after()throws Exception{shell("settings put secure enabled_accessibility_services null");Journal.monitoring=false;}
    private void shell(String command)throws Exception{try(ParcelFileDescriptor fd=automation.executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] b=new byte[4096];while(in.read(b)!=-1){}}}
    private void image(String name)throws Exception{
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();SystemClock.sleep(400);
        Bitmap bitmap=automation.takeScreenshot();assertNotNull("Screenshot must be rendered",bitmap);
        File dir=new File(c.getExternalFilesDir(null),"qa");assertTrue(dir.exists()||dir.mkdirs());try(OutputStream out=new FileOutputStream(new File(dir,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();
    }
    private View find(View v,String text,int[] skip){
        boolean match=v instanceof TextView && text.contentEquals(((TextView)v).getText());
        if(!match&&v.getContentDescription()!=null)match=text.contentEquals(v.getContentDescription());
        if(match&&skip[0]--==0)return v;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View f=find(g.getChildAt(i),text,skip);if(f!=null)return f;}}
        return null;
    }
    private void click(ActivityScenario<MainActivity> scenario,String label,int occurrence){scenario.onActivity(a->{View v=find(a.getWindow().getDecorView(),label,new int[]{occurrence});assertNotNull("Button not found: "+label,v);assertTrue("Button did not react: "+label,v.performClick());});}
    private boolean contains(AccessibilityNodeInfo node,String text){if(node==null)return false;boolean hit=node.getText()!=null&&node.getText().toString().contains(text);for(int i=0;!hit&&i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);hit=contains(child,text);if(child!=null)child.recycle();}return hit;}
    private boolean deviceContains(String text){AccessibilityNodeInfo root=automation.getRootInActiveWindow();boolean found=contains(root,text);if(root!=null)root.recycle();if(found)return true;for(android.view.accessibility.AccessibilityWindowInfo w:automation.getWindows()){AccessibilityNodeInfo n=w.getRoot();found=contains(n,text);if(n!=null)n.recycle();if(found)return true;}return false;}
    @Test public void a_nativeScreensAndSavedRule()throws Exception{
        JSONObject d=new Journal(c).today();d.put("shortMs",8*60_000L);p.raw().edit().putString("journal_v3",new JSONObject().put(LocalDate.now().toString(),d).toString()).commit();
        p.setGamesEnabled(true);p.setGamePackages(Collections.singleton("com.bernard.fixture"));
        p.raw().edit().putStringSet("rewards_v3",new HashSet<>(Arrays.asList("day_1","day_3","day_7"))).putInt("success_total_v3",7).commit();
        try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)){
            image("01-home");click(a,"Limites",0);image("02-limits");click(a,"Modifier",0);image("03-short-editor");
            click(a,"＋",0);click(a,"Enregistrer",0);assertEquals(25,p.shortLimitMinutes());
            click(a,"Modifier",1);image("04-games-editor");click(a,"Choisir les applications",0);image("05-app-picker");
            click(a,"Valider la sélection",0);click(a,"Enregistrer",0);assertTrue(p.gamePackages().contains("com.bernard.fixture"));
            click(a,"Récompenses",0);image("06-rewards");
            a.onActivity(x->x.navigate("reward"));image("07-reward-detail");click(a,"Retour à ma collection",0);
            click(a,"Historique",0);image("08-history");
            // Moving from a protected page to a public page intentionally closes the admin
            // session. Keep fixture-only authorization and navigation atomic on the activity
            // thread; production navigation must still require the PIN.
            a.onActivity(x->{PinGuard.authorize();x.navigate("settings");});image("09-settings");
            click(a,"Autorisations Android",0);image("10-permissions");
            a.onActivity(x->x.navigate("intro"));image("11-onboarding");click(a,"Commencer",0);image("12-onboarding-limits");click(a,"Choisir mes autorisations",0);click(a,"Entrer dans l’application",0);
            // Large text + smaller viewport: exercise a real configuration change and redraw.
            shell("wm size 960x1920");shell("wm density 420");SystemClock.sleep(600);image("13-small-home");
            shell("settings put system font_scale 1.3");SystemClock.sleep(800);image("14-large-text-home");
            shell("settings put system font_scale 1.0");shell("wm size 1080x2340");SystemClock.sleep(600);
        }
        Intent block=new Intent(c,BlockActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("preview",true).putExtra("reason","La limite des contenus courts est atteinte.");c.startActivity(block);SystemClock.sleep(600);image("15-block-preview");
        c.startActivity(new Intent(c,BlockActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("preview",true).putExtra("schedule",true).putExtra("reason","Les jeux sont en pause pour l’instant.").putExtra("resume","Prochaine ouverture à 18:00."));SystemClock.sleep(400);image("16-schedule-preview");
    }
    @Test public void b_firstDayCannotUnlock(){Journal journal=new Journal(c);assertFalse(journal.today().optBoolean("closed"));assertFalse(journal.today().optBoolean("eligible"));assertEquals(0,journal.successCount());assertFalse(journal.unlocked(1));}
    @Test public void c_completedObservedDayUnlocks()throws Exception{
        String yesterday=LocalDate.now().minusDays(1).toString();JSONObject d=new JSONObject().put("date",yesterday).put("shortMs",60_000).put("gamesMs",0).put("shortCap",20).put("gamesCap",0).put("eligible",true).put("closed",false).put("hasGoal",true).put("signature",p.configSignature());
        p.raw().edit().putString("journal_current_v3",yesterday).putString("journal_v3",new JSONObject().put(yesterday,d).toString()).commit();
        Journal.monitoring=true;Journal j=new Journal(c);j.today();assertEquals(1,j.successCount());assertTrue(j.unlocked(1));assertEquals(1,j.streak());
        j.markIncomplete("Test interruption");assertTrue("A earned image must remain unlocked",j.unlocked(1));
    }
    @Test public void d_changedRulesCannotUnlock()throws Exception{
        String yesterday=LocalDate.now().minusDays(1).toString();JSONObject d=new JSONObject().put("date",yesterday).put("shortMs",0).put("gamesMs",0).put("shortCap",20).put("eligible",true).put("closed",false).put("hasGoal",true).put("signature","not-the-current-rule");
        p.raw().edit().putString("journal_current_v3",yesterday).putString("journal_v3",new JSONObject().put(yesterday,d).toString()).commit();Journal.monitoring=true;Journal j=new Journal(c);j.today();assertEquals(0,j.successCount());
    }
    @Test public void e_accessibilityBlocksFixtureOutsideHours()throws Exception{
        assertNotNull("Fixture installed",c.getPackageManager().getLaunchIntentForPackage("com.bernard.fixture"));
        p.setGamePackages(Collections.singleton("com.bernard.fixture"));p.setGamesEnabled(true);int now=TimeUtils.nowMinute();p.setGamesStartMinute((now+60)%1440);p.setGamesEndMinute((now+120)%1440);
        shell("settings put secure enabled_accessibility_services "+c.getPackageName()+"/com.local.focusfence.service.FocusAccessibilityService");shell("settings put secure accessibility_enabled 1");SystemClock.sleep(2000);
        c.startActivity(c.getPackageManager().getLaunchIntentForPackage("com.bernard.fixture").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        boolean blocked=false;for(int n=0;n<15;n++){SystemClock.sleep(400);if(deviceContains("Les jeux sont en pause")){blocked=true;break;}}
        image("17-live-schedule-overlay");assertTrue("The native accessibility overlay must block the real test application",blocked);
    }
    @Test public void f_oneMinuteGameQuotaActuallyBlocks()throws Exception{
        p.setGamePackages(Collections.singleton("com.bernard.fixture"));p.setGamesEnabled(true);p.setGamesStartMinute(0);p.setGamesEndMinute(0);p.setGamesLimitMinutes(1);
        shell("settings put secure enabled_accessibility_services "+c.getPackageName()+"/com.local.focusfence.service.FocusAccessibilityService");shell("settings put secure accessibility_enabled 1");SystemClock.sleep(2000);
        c.startActivity(c.getPackageManager().getLaunchIntentForPackage("com.bernard.fixture").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        boolean blocked=false;for(int n=0;n<145;n++){SystemClock.sleep(500);if(deviceContains("Le quota Jeux du jour est atteint")){blocked=true;break;}}
        image("18-live-quota-overlay");assertTrue("The foreground-time quota must interrupt the real test application",blocked);
    }
    @Test public void g_closingMainActivityDoesNotDisableProtection()throws Exception{
        p.setGamePackages(Collections.singleton("com.bernard.fixture"));p.setGamesEnabled(true);int now=TimeUtils.nowMinute();p.setGamesStartMinute((now+60)%1440);p.setGamesEndMinute((now+120)%1440);
        shell("settings put secure enabled_accessibility_services "+c.getPackageName()+"/com.local.focusfence.service.FocusAccessibilityService");shell("settings put secure accessibility_enabled 1");SystemClock.sleep(1800);
        try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)){SystemClock.sleep(250);a.close();}
        SystemClock.sleep(400);
        c.startActivity(c.getPackageManager().getLaunchIntentForPackage("com.bernard.fixture").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        boolean blocked=false;for(int n=0;n<15;n++){SystemClock.sleep(400);if(deviceContains("Les jeux sont en pause")){blocked=true;break;}}
        assertTrue("Closing Bernard's activity must not stop its accessibility enforcement",blocked);
    }
    @Test public void h_missingUsageAccessFailsClosedForGames()throws Exception{
        p.setGamePackages(Collections.singleton("com.bernard.fixture"));p.setGamesEnabled(true);p.setGamesStartMinute(0);p.setGamesEndMinute(0);p.setGamesLimitMinutes(20);
        shell("appops set "+c.getPackageName()+" GET_USAGE_STATS deny");
        shell("settings put secure enabled_accessibility_services "+c.getPackageName()+"/com.local.focusfence.service.FocusAccessibilityService");shell("settings put secure accessibility_enabled 1");SystemClock.sleep(1800);
        c.startActivity(c.getPackageManager().getLaunchIntentForPackage("com.bernard.fixture").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        boolean blocked=false;for(int n=0;n<15;n++){SystemClock.sleep(400);if(deviceContains("L’accès aux données d’utilisation a été retiré")){blocked=true;break;}}
        assertTrue("Removing Usage Access must block rather than disable a finite game quota",blocked);
    }

    @Test public void i_appInfoIsProtectedByPin()throws Exception{
        shell("settings put secure enabled_accessibility_services "+c.getPackageName()+"/com.local.focusfence.service.FocusAccessibilityService");shell("settings put secure accessibility_enabled 1");SystemClock.sleep(1800);
        PinGuard.lockNow();
        Intent info=new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+c.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(info);
        boolean pin=false;for(int n=0;n<20;n++){SystemClock.sleep(300);if(deviceContains("Bernard garde les réglages")){pin=true;break;}}
        assertTrue("Bernard app-info / force-stop / clear-data surface must require the PIN",pin);
    }

    @Test public void j_pinVerifierAndPersistentLockout(){
        PinGuard.lockNow();
        assertFalse(PinGuard.verify(c,new char[]{'0','0','0','0'}));
        assertTrue(PinGuard.verify(c,new char[]{'1','2','3','4'}));
        PinGuard.lockNow();
        for(int n=0;n<5;n++)assertFalse(PinGuard.verify(c,new char[]{'9','9','9','9'}));
        assertTrue("Five wrong attempts must create a persistent lockout",PinGuard.lockoutRemainingMs(c)>0);
    }

    @Test public void k_protectedPageNeverRendersBeforePin()throws Exception{
        PinGuard.lockNow();
        Intent open=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("page","limits");
        try(ActivityScenario<MainActivity> ignored=ActivityScenario.launch(open)){
            boolean pin=false;for(int n=0;n<20;n++){SystemClock.sleep(200);if(deviceContains("Bernard garde les réglages")){pin=true;break;}}
            assertTrue("Protected page must be covered by PIN",pin);
            assertFalse("Limits must not flash behind PIN before authorization",deviceContains("Mes limites"));
        }
    }

    @Test public void l_expiredPinCannotSaveSettingsInRefreshRace()throws Exception{
        p.setShortLimitMinutes(20);
        PinGuard.authorize();
        try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)){
            click(a,"Limites",0);click(a,"Modifier",0);click(a,"＋",0);
            PinGuard.lockNow();
            click(a,"Enregistrer",0);
            boolean pin=false;for(int n=0;n<15;n++){SystemClock.sleep(200);if(deviceContains("Bernard garde les réglages")){pin=true;break;}}
            assertTrue("Sensitive action must recheck the PIN immediately",pin);
            assertEquals("Expired PIN must not mutate the quota",20,p.shortLimitMinutes());
        }
    }

    @Test public void m_usageGrantDoesNotAuthorizeAppInfo()throws Exception{
        shell("settings put secure enabled_accessibility_services "+c.getPackageName()+"/com.local.focusfence.service.FocusAccessibilityService");
        shell("settings put secure accessibility_enabled 1");SystemClock.sleep(1600);
        PinGuard.lockNow();
        PinGuard.authorizeSystemControl(PinGuard.CONTROL_USAGE,"");
        Intent info=new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:"+c.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(info);
        boolean pin=false;for(int n=0;n<20;n++){SystemClock.sleep(250);if(deviceContains("Bernard garde les réglages")){pin=true;break;}}
        assertTrue("A Usage Access grant must not become a wildcard for app-info / force-stop",pin);
    }

    @Test public void n_blockOverlayDoesNotPauseSystemTamperGuard()throws Exception{
        p.setGamePackages(Collections.singleton("com.bernard.fixture"));p.setGamesEnabled(true);
        int now=TimeUtils.nowMinute();p.setGamesStartMinute((now+60)%1440);p.setGamesEndMinute((now+120)%1440);
        shell("settings put secure enabled_accessibility_services "+c.getPackageName()+"/com.local.focusfence.service.FocusAccessibilityService");
        shell("settings put secure accessibility_enabled 1");SystemClock.sleep(1600);
        c.startActivity(c.getPackageManager().getLaunchIntentForPackage("com.bernard.fixture").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        boolean blocked=false;for(int n=0;n<15;n++){SystemClock.sleep(300);if(deviceContains("Les jeux sont en pause")){blocked=true;break;}}
        assertTrue("Fixture must be blocked before testing the overlay race",blocked);
        PinGuard.lockNow();
        c.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        boolean pin=false;for(int n=0;n<20;n++){SystemClock.sleep(250);if(deviceContains("Bernard garde les réglages")){pin=true;break;}}
        assertTrue("Opening Settings while a block overlay is visible must still trigger the PIN",pin);
    }

    @Test public void o_leavingProtectedPageClosesAdminSession()throws Exception{
        PinGuard.authorize();
        try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)){
            a.onActivity(x->x.navigate("limits"));
            assertTrue("Protected page should use the active administrator session",PinGuard.isAuthorized());
            a.onActivity(x->x.navigate("rewards"));
            assertFalse("Leaving a protected page must close the administrator session",PinGuard.isAuthorized());
        }
    }

}
