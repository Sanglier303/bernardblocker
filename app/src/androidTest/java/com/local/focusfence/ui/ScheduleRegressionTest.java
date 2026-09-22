package com.local.focusfence.ui;

import android.app.UiAutomation;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.local.focusfence.core.AccessPolicy.Reason;
import com.local.focusfence.detector.ShortSurfaceDetector;
import com.local.focusfence.model.AppRule;
import com.local.focusfence.security.PinGuard;
import com.local.focusfence.service.FocusAccessibilityService;
import com.local.focusfence.storage.*;
import com.local.focusfence.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;

/** Real service/overlay regression tests plus editor saves; the fixture is not Instagram. */
@RunWith(AndroidJUnit4.class)
public class ScheduleRegressionTest {
    private static final String FIXTURE="com.bernard.fixture";
    private Context c;private Prefs p;private UiAutomation ui;
    @Before public void setup()throws Exception {
        c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        ui=InstrumentationRegistry.getInstrumentation().getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        android.accessibilityservice.AccessibilityServiceInfo info=ui.getServiceInfo();
        info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        ui.setServiceInfo(info);
        shell("settings put secure enabled_accessibility_services null");waitStopped();ui.performGlobalAction(2);
        p=new Prefs(c);p.raw().edit().clear().commit();p.setOnboardingDone(true);p.setShortEnabled(false);
        c.getSharedPreferences("bernard_updates_v1",0).edit().clear().putBoolean("auto",false).commit();
        c.getSharedPreferences("bernard_pin_v4",0).edit().clear().commit();PinGuard.ensureConfigured(c);PinGuard.lockNow();PinGuard.clearSystemControlAuthorization();
        shell("appops set "+c.getPackageName()+" GET_USAGE_STATS allow");
        shell("settings put global auto_time 1");shell("settings put global auto_time_zone 1");
    }
    @After public void cleanup()throws Exception {shell("settings put secure enabled_accessibility_services null");waitStopped();ui.performGlobalAction(2);PinGuard.lockNow();PinGuard.clearSystemControlAuthorization();}
    private void waitStopped(){for(int i=0;i<50&&Journal.monitoring;i++)SystemClock.sleep(100);SystemClock.sleep(200);}
    private void shell(String command)throws Exception {try(ParcelFileDescriptor fd=ui.executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] b=new byte[4096];while(in.read(b)!=-1){}}}
    private void startService()throws Exception {
        shell("settings put secure enabled_accessibility_services "+c.getPackageName()+"/com.local.focusfence.service.FocusAccessibilityService");shell("settings put secure accessibility_enabled 1");
        for(int n=0;n<60&&!Journal.monitoring;n++)SystemClock.sleep(100);assertTrue(Journal.monitoring);assertFalse(p.tamperReason(),p.tamperLock());
    }
    private void fixture(){Intent i=c.getPackageManager().getLaunchIntentForPackage(FIXTURE);assertNotNull(i);c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    private void games(boolean closed){int n=TimeUtils.nowMinute();p.saveGroup(true,true,60,closed?(n+15)%1440:0,closed?(n+30)%1440:0,Collections.singleton(FIXTURE),null);}
    private boolean hasOverlay(){
        boolean found=false;
        for(AccessibilityWindowInfo w:ui.getWindows()){
            try{if(w.getType()==AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY)found=true;}
            finally{w.recycle();}
        }
        return found;
    }
    private void awaitOverlay(boolean present){for(int n=0;n<60&&hasOverlay()!=present;n++)SystemClock.sleep(100);assertEquals("Overlay state",present,hasOverlay());}
    private static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static Object call(Object o,String name,Class<?>[] types,Object...args){try{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}catch(Exception e){throw new AssertionError(e);}}
    private static View view(View v,String text){if((v instanceof TextView&&text.contentEquals(((TextView)v).getText()))||(v.getContentDescription()!=null&&text.contentEquals(v.getContentDescription())))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View x=view(g.getChildAt(i),text);if(x!=null)return x;}}return null;}
    private static void click(MainActivity a,String text){
        View v=view(a.getWindow().getDecorView(),text);assertNotNull(text,v);
        if(v instanceof CompoundButton){
            // CompoundButton toggles even when performClick returns false (no OnClickListener).
            boolean before=((CompoundButton)v).isChecked();v.performClick();
            assertNotEquals(text,before,((CompoundButton)v).isChecked());
        }else assertTrue(text,v.performClick());
    }
    private static MainActivity.Draft draft(MainActivity a){try{return (MainActivity.Draft)field(a,"draft");}catch(Exception e){throw new AssertionError(e);}}
    private static void editSocial(MainActivity a){PinGuard.authorize();call(a,"editGroup",new Class<?>[]{boolean.class},false);}
    private ActivityScenario<MainActivity> main(){return ActivityScenario.launch(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));}
    @Test public void reopeningWindowRemovesExistingOverlayWithoutRestart()throws Exception {
        games(true);startService();fixture();awaitOverlay(true);
        assertTrue(UsageUtils.todayUsageMs(c,FIXTURE)<60*60000L);
        games(false);awaitOverlay(false);assertFalse(p.tamperLock());assertEquals(60,p.gamesLimitMinutes());
        fixture();SystemClock.sleep(1600);assertFalse(hasOverlay());
    }
    @Test public void narrowingWindowBlocksWhileDailyTimeRemains()throws Exception {
        games(false);startService();fixture();SystemClock.sleep(1600);assertFalse(hasOverlay());
        games(true);awaitOverlay(true);assertTrue(UsageUtils.todayUsageMs(c,FIXTURE)<60*60000L);
        assertEquals("SCHEDULE",new org.json.JSONObject(p.raw().getString("last_block_v45","{}")).getString("reason"));
    }
    @Test public void anotherRuleCannotBeBypassedByChangingGroupWindow()throws Exception {
        games(true);startService();fixture();awaitOverlay(true);
        AppRule r=new AppRule();r.packageName=FIXTURE;r.label="Test";r.alwaysBlocked=true;p.saveAppRule(r);
        games(false);SystemClock.sleep(1600);assertTrue(hasOverlay());
        assertEquals(Reason.ALWAYS_BLOCKED,new AccessEvaluator(c).evaluate(FIXTURE,null).reason);
        p.removeAppRule(FIXTURE);awaitOverlay(false);
    }
    @Test public void changingScheduleDoesNotClearSecurityLock()throws Exception {
        games(true);startService();p.setTamperLock("Test de protection");fixture();awaitOverlay(true);
        games(false);SystemClock.sleep(1600);assertTrue(hasOverlay());assertTrue(p.tamperLock());
        assertEquals(Reason.TAMPER,new AccessEvaluator(c).evaluate(FIXTURE,null).reason);
    }
    @Test public void socialOverlayReopensWithoutResettingOrChargingPausedTime()throws Exception {
        int minute=TimeUtils.nowMinute();p.setShortEnabled(true);p.setShortLimitMinutes(20);p.setShortStartMinute((minute+15)%1440);p.setShortEndMinute((minute+30)%1440);
        Journal j=new Journal(c);long noon=java.time.LocalDate.now().atTime(12,0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();j.today();j.addShort(noon,180000);
        startService();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try{Field f=FocusAccessibilityService.class.getDeclaredField("running");f.setAccessible(true);Object service=((java.lang.ref.WeakReference<?>)f.get(null)).get();
                ((ShortSurfaceDetector)field(service,"detector")).registerWholeAppPackage(FIXTURE,ShortSurfaceDetector.Surface.YOUTUBE_SHORTS);
            }catch(Exception e){throw new AssertionError(e);}
        });
        fixture();awaitOverlay(true);assertFalse("A blocked surface is not being counted",p.detectorCounting());assertEquals(180000,j.shortMs());
        p.setShortStartMinute(0);p.setShortEndMinute(0);awaitOverlay(false);assertEquals(180000,j.shortMs());
        fixture();for(int n=0;n<40&&!p.detectorCounting();n++)SystemClock.sleep(100);assertTrue(p.detectorCounting());
        ui.performGlobalAction(2);for(int n=0;n<40&&p.detectorCounting();n++)SystemClock.sleep(100);assertFalse(p.detectorCounting());
    }
    @Test public void openingBernardDismissesOverlayButDoesNotUnlockRules()throws Exception {
        games(true);startService();fixture();awaitOverlay(true);
        try(ActivityScenario<MainActivity> a=main()){awaitOverlay(false);assertFalse(PinGuard.isAuthorized());}
        fixture();awaitOverlay(true);
    }
    @Test public void manualEqualTimesAreRejectedAndAllDayToggleRestoresDraft(){
        p.setShortEnabled(true);p.setShortStartMinute(600);p.setShortEndMinute(720);
        try(ActivityScenario<MainActivity> a=main()){
            a.onActivity(x->{editSocial(x);MainActivity.Draft d=draft(x);
                click(x,"Toute la journée");assertTrue(d.allDay);click(x,"Toute la journée");assertFalse(d.allDay);assertEquals(600,d.start);assertEquals(720,d.end);
                d.start=d.end;call(x,"render",new Class<?>[]{});
                assertFalse(((Switch)view(x.getWindow().getDecorView(),"Toute la journée")).isChecked());click(x,"Enregistrer");assertEquals(600,p.shortStartMinute());assertEquals(720,p.shortEndMinute());
                click(x,"Toute la journée");click(x,"Enregistrer");assertEquals(0,p.shortStartMinute());assertEquals(0,p.shortEndMinute());
            });
        }
    }
    @Test public void invalidQuotaCannotPartiallySaveNewWindowOrBecomeUnlimited(){
        p.setShortEnabled(true);p.setShortStartMinute(0);p.setShortEndMinute(0);p.setShortLimitMinutes(20);
        try(ActivityScenario<MainActivity> a=main()){
            a.onActivity(x->{editSocial(x);MainActivity.Draft d=draft(x);d.allDay=false;d.start=600;d.end=720;
                for(String invalid:new String[]{"","1441","99999999999"}){
                    ((EditText)view(x.getWindow().getDecorView(),"Minutes autorisées par jour")).setText(invalid);click(x,"Enregistrer");
                    assertEquals(20,p.shortLimitMinutes());assertEquals(0,p.shortStartMinute());
                }
                ((EditText)view(x.getWindow().getDecorView(),"Minutes autorisées par jour")).setText("0");click(x,"Enregistrer");
                assertEquals(0,p.shortLimitMinutes());assertEquals(600,p.shortStartMinute());assertEquals(720,p.shortEndMinute());assertFalse(p.tamperLock());
            });
        }
    }
    @Test public void saveGroupIsAtomicAndDoesNotResetTheDailyCounter(){
        Journal j=new Journal(c);j.today();long noon=java.time.LocalDate.now().atTime(12,0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();j.addShort(noon,180000);
        List<String> observations=new ArrayList<>();
        SharedPreferences.OnSharedPreferenceChangeListener listener=(sp,key)->{if(Prefs.affectsProtection(key))observations.add(p.shortStartMinute()+":"+p.shortEndMinute()+":"+p.shortLimitMinutes());};
        boolean[] features=new boolean[Prefs.FEATURES.length];Arrays.fill(features,true);
        // Observe this transaction only. Registering on the instrumentation thread can also
        // catch a previously queued setup notification before the main-thread save starts.
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            p.raw().registerOnSharedPreferenceChangeListener(listener);
            try{p.saveGroup(false,true,30,600,720,Collections.emptySet(),features);}
            finally{p.raw().unregisterOnSharedPreferenceChangeListener(listener);}
        });
        assertFalse(observations.isEmpty());for(String s:observations)assertEquals("600:720:30",s);
        assertEquals(180000,j.shortMs());assertFalse(p.tamperLock());
    }
}
