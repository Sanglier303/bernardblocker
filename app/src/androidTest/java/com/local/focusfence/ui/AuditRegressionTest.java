package com.local.focusfence.ui;

import android.app.UiAutomation;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.local.focusfence.security.*;
import com.local.focusfence.storage.*;
import com.local.focusfence.update.UpdateManager;
import com.local.focusfence.util.NodeWalker;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

/** Regression journeys, not just screenshots or a pre-authorized fixture. */
@RunWith(AndroidJUnit4.class)
public class AuditRegressionTest {
    private Context c;private Prefs p;private UiAutomation ui;
    @Rule public final org.junit.rules.TestWatcher failureEvidence=new org.junit.rules.TestWatcher(){
        @Override protected void failed(Throwable error,org.junit.runner.Description test){dumpScreen(test.getMethodName()+"-failed");}
    };
    @Before public void setup()throws Exception {
        c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        ui=InstrumentationRegistry.getInstrumentation().getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        android.accessibilityservice.AccessibilityServiceInfo info=ui.getServiceInfo();
        info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                |android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        ui.setServiceInfo(info);
        shell("settings put secure enabled_accessibility_services null");
        ui.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);
        long end=SystemClock.elapsedRealtime()+5000;
        while(Journal.monitoring&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(100);
        SystemClock.sleep(300);
        DevicePolicyManager dpm=(DevicePolicyManager)c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if(dpm!=null&&dpm.isAdminActive(FortressPolicy.admin(c))){dpm.removeActiveAdmin(FortressPolicy.admin(c));SystemClock.sleep(400);}
        p=new Prefs(c);p.raw().edit().clear().commit();p.setOnboardingDone(true);
        c.getSharedPreferences("bernard_pin_v4",0).edit().clear().commit();PinGuard.ensureConfigured(c);
        c.getSharedPreferences("bernard_updates_v1",0).edit().clear().commit();
        PinGuard.lockNow();PinGuard.clearSystemControlAuthorization();Journal.monitoring=false;
        shell("appops set "+c.getPackageName()+" GET_USAGE_STATS allow");
        shell("settings put global auto_time 1");shell("settings put global auto_time_zone 1");
    }
    @After public void cleanup()throws Exception {
        shell("settings put secure enabled_accessibility_services null");ui.performGlobalAction(2);
        long until=SystemClock.elapsedRealtime()+5000;while(Journal.monitoring&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);
        DevicePolicyManager dpm=(DevicePolicyManager)c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if(dpm!=null&&dpm.isAdminActive(FortressPolicy.admin(c))){dpm.removeActiveAdmin(FortressPolicy.admin(c));SystemClock.sleep(300);}
        PinGuard.lockNow();PinGuard.clearSystemControlAuthorization();
    }
    private void dumpScreen(String name){
        if(c==null||ui==null)return;
        try{
            File dir=new File(c.getExternalFilesDir(null),"qa");if(!dir.exists()&&!dir.mkdirs())return;
            StringBuilder report=new StringBuilder();AccessibilityNodeInfo root=ui.getRootInActiveWindow();
            try{NodeWalker.visit(root,1800,n->report.append(n.getPackageName()).append(" | ").append(n.getClassName())
                    .append(" | ").append(n.getViewIdResourceName()).append(" | visible=").append(n.isVisibleToUser())
                    .append(" enabled=").append(n.isEnabled()).append(" clickable=").append(n.isClickable())
                    .append(" | ").append(n.getText()).append('\n'));}
            finally{if(root!=null)root.recycle();}
            try(OutputStream out=new FileOutputStream(new File(dir,name+".txt"))){out.write(report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            android.graphics.Bitmap shot=ui.takeScreenshot();
            if(shot!=null){try(OutputStream out=new FileOutputStream(new File(dir,name+".png"))){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{shot.recycle();}}
        }catch(Exception e){System.err.println("QA evidence unavailable: "+e);}
    }
    private void shell(String command)throws Exception {try(ParcelFileDescriptor fd=ui.executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] b=new byte[4096];while(in.read(b)!=-1){}}}
    private ActivityScenario<MainActivity> launch(){return ActivityScenario.launch(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));}
    private void service()throws Exception {shell("settings put secure enabled_accessibility_services "+c.getPackageName()+"/com.local.focusfence.service.FocusAccessibilityService");shell("settings put secure accessibility_enabled 1");long end=SystemClock.elapsedRealtime()+6000;while(!Journal.monitoring&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(100);assertTrue("Accessibility service must really start",Journal.monitoring);}
    private boolean text(String wanted){AccessibilityNodeInfo r=ui.getRootInActiveWindow();try{return NodeWalker.any(r,1800,n->n.isVisibleToUser()&&n.getText()!=null&&n.getText().toString().contains(wanted));}finally{if(r!=null)r.recycle();}}
    private void await(String wanted){
        for(int n=0;n<40&&!text(wanted);n++)SystemClock.sleep(150);
        boolean reached=text(wanted);
        // A TestWatcher runs after cleanup, when the failed screen may already be gone.
        if(!reached)dumpScreen("await-"+wanted.replaceAll("[^a-zA-Z0-9]","_")+"-before-cleanup");
        assertTrue("Screen not reached: "+wanted,reached);
    }
    private boolean action(String value,boolean byId){AccessibilityNodeInfo r=ui.getRootInActiveWindow();try{return NodeWalker.any(r,1800,n->n.isVisibleToUser()&&n.isEnabled()&&n.isClickable()&&(byId?n.getViewIdResourceName()!=null&&n.getViewIdResourceName().endsWith("/"+value):n.getText()!=null&&value.equalsIgnoreCase(n.getText().toString()))&&n.performAction(AccessibilityNodeInfo.ACTION_CLICK));}finally{if(r!=null)r.recycle();}}
    private void key(String value){
        if(!action(value,false)){
            AccessibilityNodeInfo r=ui.getRootInActiveWindow();try{NodeWalker.any(r,1200,n->n.isScrollable()&&n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD));}finally{if(r!=null)r.recycle();}
            SystemClock.sleep(150);assertTrue("PIN key not available: "+value,action(value,false));
        }
        SystemClock.sleep(100);
    }
    private void ownerPin(){key("1");key("1");key("0");key("9");}
    private View find(View v,String label){if(v instanceof TextView&&label.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup){for(int i=0;i<((ViewGroup)v).getChildCount();i++){View f=find(((ViewGroup)v).getChildAt(i),label);if(f!=null)return f;}}return null;}

    @Test public void actualPinThenAdminActivationReturnsHomeWithoutAnotherPin()throws Exception {
        try(ActivityScenario<MainActivity> a=launch()){
            service();a.onActivity(x->x.navigate("settings"));await("Bernard garde les réglages");ownerPin();
            await("Protection anti-désinstallation");
            a.onActivity(x->{View button=find(x.getWindow().getDecorView(),"Activer la protection");assertNotNull(button);assertTrue(button.performClick());});
            boolean androidScreen=false;
            for(int n=0;n<40;n++){SystemClock.sleep(150);AccessibilityNodeInfo r=ui.getRootInActiveWindow();try{if(r!=null&&r.getPackageName()!=null&&!c.getPackageName().contentEquals(r.getPackageName())&&text("Bernard Bloqueur")){androidScreen=true;break;}}finally{if(r!=null)r.recycle();}}
            assertTrue("Actual Android administrator confirmation must appear",androidScreen);
            for(int n=0;n<12;n++){SystemClock.sleep(200);assertFalse("PIN loop while reading Android confirmation",text("Bernard garde les réglages"));}
            dumpScreen("audit-device-admin-before-activate");
            boolean activated=false;
            for(int n=0;n<24&&!activated;n++){
                // Android attaches the listener to restricted_action, not the label button.
                // Both the actual emulator tree and AOSP DeviceAdminAdd confirm this.
                activated=action("restricted_action",true)||action("action_button",true)||action("Activate this device admin app",false)
                        ||action("Activate this device administrator",false)||action("Activate",false)||action("Activer",false);
                if(!activated)SystemClock.sleep(250);
            }
            assertTrue("Android activation button must be visible, enabled and clickable",activated);
            long until=SystemClock.elapsedRealtime()+5000;while(!FortressPolicy.isAdminActive(c)&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);
            assertTrue("Android must actually grant the role",FortressPolicy.isAdminActive(c));
            await("Bonjour");assertFalse("No PIN on successful return",text("Bernard garde les réglages"));
            assertFalse("System grant is revoked on return",PinGuard.isSystemControlAuthorized());
        }
    }
    @Test public void reusedPinActivityReadsNewGuardRequest()throws Exception {
        Intent initial=new Intent(c,PinActivity.class).putExtra(PinActivity.EXTRA_TARGET_PAGE,"settings").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try(ActivityScenario<PinActivity> a=ActivityScenario.launch(initial)){
            await("Bernard garde les réglages");
            a.onActivity(x->x.startActivity(new Intent(x,PinActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(PinActivity.EXTRA_GUARD_MODE,true).putExtra(PinActivity.EXTRA_CONTROL_SCOPE,PinGuard.CONTROL_USAGE).putExtra(PinActivity.EXTRA_CONTROL_PACKAGE,"com.android.settings")));
            SystemClock.sleep(200);ownerPin();SystemClock.sleep(500);
            assertEquals(PinGuard.CONTROL_USAGE,PinGuard.systemControlScope());assertEquals("com.android.settings",PinGuard.systemControlPackage());
        }
    }
    @Test public void updateGrantDoesNotOpenAppInfo()throws Exception {
        try(ActivityScenario<MainActivity> a=launch()){
            service();PinGuard.authorizeSystemControl(PinGuard.CONTROL_UPDATE,"com.android.settings");
            a.onActivity(x->x.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+x.getPackageName()))));
            await("Bernard garde les réglages");ownerPin();SystemClock.sleep(1200);
            assertFalse("A new owner PIN must not keep the wrong update scope and loop",text("Bernard garde les réglages"));
            assertEquals(PinGuard.CONTROL_SYSTEM,PinGuard.systemControlScope());
        }
    }
    @Test public void deviceAdminControllerBridgeIsReachableButAppInfoDenied(){
        AccessibilityNodeInfo n=AccessibilityNodeInfo.obtain();n.setPackageName("com.google.android.permissioncontroller");n.setVisibleToUser(true);n.setText("Activate this device admin app: Bernard Bloqueur");
        try{assertTrue(TamperGuard.isAuthorizedControlScreen(PinGuard.CONTROL_DEVICE_ADMIN,"com.android.settings","com.google.android.permissioncontroller",n,"android.widget.LinearLayout"));
            assertFalse(TamperGuard.isAuthorizedControlScreen(PinGuard.CONTROL_USAGE,"com.android.settings","com.google.android.permissioncontroller",n,"android.widget.LinearLayout"));}
        finally{n.recycle();}
    }
    @Test public void reducingQuotaDoesNotErasePreviouslyCountedTime(){
        // Use an interval wholly within today's date, including when CI runs at midnight.
        long end=java.time.LocalDate.now().atTime(12,0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        Journal j=new Journal(c);j.addShort(end,600000);p.setShortLimitMinutes(1);j.addShort(end+1000,1000);
        assertEquals(601000,j.shortMs());assertTrue(com.local.focusfence.core.Rules.exhausted(j.shortMs(),1));
    }
    @Test public void scheduleOnlyGamesDoNotRequireUsageAccess()throws Exception {
        p.setGamePackages(Collections.singleton("com.bernard.fixture"));p.setGamesEnabled(true);p.setGamesLimitMinutes(0);p.setGamesStartMinute(0);p.setGamesEndMinute(0);
        assertFalse(p.needsUsage());shell("appops set "+c.getPackageName()+" GET_USAGE_STATS deny");
        try(ActivityScenario<MainActivity> a=launch()){
            service();c.startActivity(c.getPackageManager().getLaunchIntentForPackage("com.bernard.fixture").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await("TEST ONLY");SystemClock.sleep(2000);assertTrue(text("TEST ONLY"));assertFalse(text("L’accès aux données d’utilisation a été retiré"));
        }
    }
    @Test public void cancelledInstallPermissionDoesNotLoopWhenAutoDisabled(){
        c.getSharedPreferences("bernard_updates_v1",0).edit().putBoolean("auto",false).putBoolean("waiting_install_permission",true).commit();
        try(ActivityScenario<MainActivity> a=launch()){
            a.onActivity(x->UpdateManager.onForeground(x));
            assertFalse(c.getSharedPreferences("bernard_updates_v1",0).getBoolean("waiting_install_permission",false));
            assertTrue(UpdateManager.state(c).lastError.contains("non accordée"));
        }
    }
    @Test public void invalidQuotaCannotSilentlyBecomeUnlimited(){
        p.raw().edit().putInt("short_limit",-4).putInt("games_start",9000).commit();
        assertTrue(p.shortLimitMinutes()>0);assertTrue(p.gamesStartMinute()<1440);assertTrue(p.tamperLock());
    }
    @Test public void diagnosticExportHasNoPinOrPrivateJournal()throws Exception {
        org.json.JSONObject d=new org.json.JSONObject(com.local.focusfence.util.DiagnosticReport.export(c));
        assertEquals(com.local.focusfence.BuildConfig.VERSION_NAME,d.getString("versionName"));
        assertTrue(d.has("permissions"));assertFalse(d.has("pin"));assertFalse(d.has("hash"));assertFalse(d.has("days"));
    }
    @Test public void pauseLocksOldSessionButLateStopPreservesNewPinGrant(){
        try(ActivityScenario<MainActivity> a=launch()){
            a.onActivity(x->PinGuard.authorize());
            a.moveToState(androidx.lifecycle.Lifecycle.State.STARTED);
            assertFalse("The old session must close as soon as Main loses the foreground",PinGuard.isAuthorized());
            // Reproduce the observed ordering: Main paused, PIN succeeds, Main stops late.
            // The separate administrator journey above still enters the actual keypad.
            assertTrue(PinGuard.verify(c,new char[]{'1','1','0','9'}));
            a.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
            assertTrue("A late stop must not erase a newer successful PIN",PinGuard.isAuthorized());
            a.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
            assertTrue("The fresh grant must survive the return to Main",PinGuard.isAuthorized());
        }
    }

    @Test public void staleCountingIndicatorClearsWhenNoSocialSurfaceIsVisible()throws Exception {
        try(ActivityScenario<MainActivity> a=launch()){
            service();p.setDetectorStatus("Reels",true);SystemClock.sleep(1500);
            assertFalse("Bernard itself must not keep a social counting flag",p.detectorCounting());
            shell("settings put secure enabled_accessibility_services null");
            long until=SystemClock.elapsedRealtime()+5000;while(Journal.monitoring&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);
            assertFalse(Journal.monitoring);assertFalse(p.detectorCounting());
        }
    }
    @Test public void boundedDecisionEvidenceNamesActualRuleWithoutPrivateContent()throws Exception {
        for(int i=0;i<25;i++)DecisionLog.record(p,new org.json.JSONObject().put("at",System.currentTimeMillis()).put("package","example.app"+i).put("scope","social").put("surface","INSTAGRAM_REELS").put("reason","SCHEDULE").put("usedMs",1000).put("remainingMs",1_199_000).put("limitMinutes",20).put("url","private").put("text","private").put("pin","private"));
        assertEquals(20,DecisionLog.entries(p).length());org.json.JSONObject last=DecisionLog.latest(p);
        assertEquals("SCHEDULE",last.getString("reason"));assertEquals(1_199_000,last.getLong("remainingMs"));
        DecisionLog.record(p,last);assertEquals(20,DecisionLog.entries(p).length());
        org.json.JSONObject exported=new org.json.JSONObject(com.local.focusfence.util.DiagnosticReport.export(c));
        assertEquals(20,exported.getJSONArray("blockDecisions").length());assertTrue(exported.has("appRules"));
        assertFalse(last.has("url"));assertFalse(last.has("text"));assertFalse(last.has("pin"));assertFalse(exported.has("pin"));
    }
    @Test public void intervalCrossingMidnightKeepsEachDaysRealUsageAndEligibility()throws Exception {
        java.time.LocalDate today=java.time.LocalDate.now(),yesterday=today.minusDays(1);
        long midnight=today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        org.json.JSONObject prior=new org.json.JSONObject().put("date",yesterday.toString()).put("shortMs",1000)
                .put("eligible",true).put("closed",false).put("hasGoal",true).put("shortCap",20)
                .put("signature",p.configSignature());
        p.raw().edit().putString("journal_current_v3",yesterday.toString()).putString("journal_v3",new org.json.JSONObject().put(yesterday.toString(),prior).toString()).commit();
        Journal.monitoring=true;Journal j=new Journal(c);j.addShort(midnight+500,1000);
        assertEquals(500,j.shortMs());assertTrue("Continuous midnight monitoring remains eligible",j.today().getBoolean("eligible"));
        org.json.JSONObject all=new org.json.JSONObject(p.raw().getString("journal_v3","{}"));
        assertEquals(1500,all.getJSONObject(yesterday.toString()).getLong("shortMs"));
    }
    @Test public void lateIntervalIsNotChargedToTodayWhenYesterdayAlreadyClosed()throws Exception {
        java.time.LocalDate today=java.time.LocalDate.now(),yesterday=today.minusDays(1);
        long midnight=today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        org.json.JSONObject prior=new org.json.JSONObject().put("date",yesterday.toString()).put("shortMs",60_000)
                .put("eligible",true).put("closed",true).put("success",true).put("hasGoal",true).put("shortCap",1);
        p.raw().edit().putString("journal_current_v3",today.toString()).putString("journal_v3",new org.json.JSONObject().put(yesterday.toString(),prior).toString()).commit();
        Journal j=new Journal(c);j.today();j.addShort(midnight-1000,5000);
        assertEquals(0,j.shortMs());org.json.JSONObject stored=new org.json.JSONObject(p.raw().getString("journal_v3","{}")).getJSONObject(yesterday.toString());
        assertEquals(65_000,stored.getLong("shortMs"));assertFalse(stored.getBoolean("success"));
    }
    @Test public void delayedMultiDayIntervalIsSplitAtEveryLocalMidnight()throws Exception {
        java.time.LocalDate today=java.time.LocalDate.now();java.time.ZoneId zone=java.time.ZoneId.systemDefault();
        long end=today.atStartOfDay(zone).toInstant().toEpochMilli()+1234;
        long start=today.minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()+1000;
        Journal j=new Journal(c);j.addShort(end,end-start);assertEquals(1234,j.shortMs());
        org.json.JSONObject all=new org.json.JSONObject(p.raw().getString("journal_v3","{}"));
        long expected=today.atStartOfDay(zone).toInstant().toEpochMilli()-today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        assertEquals(expected,all.getJSONObject(today.minusDays(1).toString()).getLong("shortMs"));
        assertFalse(all.getJSONObject(today.minusDays(2).toString()).getBoolean("success"));
    }
}
