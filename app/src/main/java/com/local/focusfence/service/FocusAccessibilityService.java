package com.local.focusfence.service;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.*;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.Toast;
import com.local.focusfence.core.Rules;
import com.local.focusfence.core.ClockPolicy;
import com.local.focusfence.core.SystemScreenPolicy;
import com.local.focusfence.core.SystemFlowWindows;
import com.local.focusfence.detector.ShortSurfaceDetector;
import com.local.focusfence.model.AppRule;
import com.local.focusfence.security.PinGuard;
import com.local.focusfence.security.BypassAppDetector;
import com.local.focusfence.security.FortressPolicy;
import com.local.focusfence.security.TamperGuard;
import com.local.focusfence.storage.*;
import com.local.focusfence.update.UpdateManager;
import com.local.focusfence.ui.*;
import com.local.focusfence.util.*;
import java.util.*;

/** Local enforcement. Polling is independent of scrolling; UI events cannot postpone the timer. */
public final class FocusAccessibilityService extends AccessibilityService {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ShortSurfaceDetector detector=new ShortSurfaceDetector();
    private final Map<String,String> windowClass=new HashMap<>();
    private final Map<String,Integer> windowIds=new HashMap<>();
    private final SystemFlowWindows systemFlows=new SystemFlowWindows();
    private boolean receiverRegistered, instagramRedirectPending;
    private long instagramRedirectAt, lastInstagramNotice;
    private final Runnable verifyInstagram = this::verifyInstagramRedirect;
    private Prefs prefs;private Journal journal;private PowerManager power;private KeyguardManager keyguard;
    private WindowManager windows;private View overlay;private String blockedPackage="";
    private AccessEvaluator access;
    private ShortSurfaceDetector.Surface blockedSurface;
    private String overlayKey="";
    private static java.lang.ref.WeakReference<FocusAccessibilityService> running=new java.lang.ref.WeakReference<>(null);
    private final SharedPreferences.OnSharedPreferenceChangeListener ruleListener=(sp,key)->{
        if(this.connected&&Prefs.affectsProtection(key))requestSample();
    };
    /** Opening Bernard never disables enforcement, but an old overlay must not cover its editor. */
    public static void onBernardForeground(){
        FocusAccessibilityService s=running.get();
        if(s!=null&&s.connected){
            s.removeOverlay();
            // A returned Activity may resume before the old Settings accessibility root expires.
            // Invalidate metadata, never extend the just-revoked system authorization.
            if(Build.VERSION.SDK_INT>=33)s.clearCache();
            s.requestSample();
        }
    }
    /** Only an explicit, owner-authorized administrator Activity participates in this ledger. */
    public static String onSystemFlowStarted(String scope,String pkg,String cls){
        FocusAccessibilityService s=running.get();
        if(s==null||!s.connected)return "";
        s.systemFlows.cancelPending();
        if(Build.VERSION.SDK_INT>=28&&PinGuard.CONTROL_DEVICE_ADMIN.equals(scope)
                &&PinGuard.isSystemControlAuthorized()&&scope.equals(PinGuard.systemControlScope())
                &&pkg.equals(PinGuard.systemControlPackage())){
            String token=java.util.UUID.randomUUID().toString();
            s.systemFlows.begin(token,pkg,cls);return token;
        }
        return "";
    }
    public static void onSystemFlowReturned(String token){
        FocusAccessibilityService s=running.get();
        if(s!=null&&s.connected){s.systemFlows.complete(token,SystemClock.uptimeMillis());s.requestSample();}
    }
    public static void onSystemFlowLaunchFailed(String token){
        FocusAccessibilityService s=running.get();if(s!=null)s.systemFlows.cancelPending(token);
    }
    private String lastZone="";
    private long overlayAt,lastElapsed,lastWall,lastSample,lastPinLaunch;private boolean tracking,connected,queued;
    private final Runnable update=()->{queued=false;sample();};
    private final Runnable tick=new Runnable(){public void run(){if(!connected)return;sample();handler.postDelayed(this,power!=null&&power.isInteractive()?1000:30_000);}};
    private final Runnable releaseCheck=new Runnable(){public void run(){if(!connected)return;UpdateManager.checkAutomatically(FocusAccessibilityService.this);handler.postDelayed(this,6*60*60_000L);}};
    private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        String action=i.getAction();
        if(Intent.ACTION_TIME_CHANGED.equals(action)||Intent.ACTION_TIMEZONE_CHANGED.equals(action)){
            String zone=java.time.ZoneId.systemDefault().getId();
            if(ClockPolicy.mustLockOnNotification(PermissionUtils.isAutomaticTimeEnabled(c),
                    PermissionUtils.isAutomaticTimeZoneEnabled(c),lastWall,lastElapsed,lastZone,
                    System.currentTimeMillis(),SystemClock.elapsedRealtime(),zone)){
                tracking=false;prefs.setTamperLock("Horloge ou fuseau modifié : validation du propriétaire nécessaire");
                journal.markIncomplete("Horloge ou fuseau modifié");
            }
            // Harmless automatic corrections must neither reset usage nor create a persistent lock.
            flush();
        }
        if(Intent.ACTION_SCREEN_OFF.equals(action)){flush();tracking=false;PinGuard.lockNow();PinGuard.clearSystemControlAuthorization();removeOverlay();}
        requestSample();
    }};
    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        handler.removeCallbacksAndMessages(null);queued=false;tracking=false;connected=false;
        instagramRedirectPending=false;removeOverlay();windowClass.clear();windowIds.clear();systemFlows.clear();
        if(receiverRegistered){try{unregisterReceiver(receiver);}catch(IllegalArgumentException ignored){}receiverRegistered=false;}
        PinGuard.ensureConfigured(this);prefs=new Prefs(this);journal=new Journal(this);power=(PowerManager)getSystemService(POWER_SERVICE);keyguard=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);windows=(WindowManager)getSystemService(WINDOW_SERVICE);
        access=new AccessEvaluator(this);running=new java.lang.ref.WeakReference<>(this);
        if(!PinGuard.isConfigured(this))prefs.setTamperLock("Code administrateur absent ou illisible : configuration du propriétaire nécessaire");
        prefs.raw().unregisterOnSharedPreferenceChangeListener(ruleListener);
        prefs.raw().registerOnSharedPreferenceChangeListener(ruleListener);
        registerInstalledBrowsers();
        FortressPolicy.apply(this);
        if(prefs.deviceAdminSeen()&&!FortressPolicy.isAdminActive(this))prefs.setTamperLock("La protection anti-désinstallation de Bernard a été retirée");
        if(FortressPolicy.isDeviceOwner(this))FortressPolicy.setFailSafeSuspended(this,prefs.tamperLock());
        // ADB is an accepted owner workflow. It is diagnostic information, not a tamper condition.
        if(!PermissionUtils.isAutomaticTimeEnabled(this)||!PermissionUtils.isAutomaticTimeZoneEnabled(this))prefs.setTamperLock("L’heure ou le fuseau automatique est désactivé et pourrait réinitialiser les quotas");
        if(PermissionUtils.hasBernardAccessibilityShortcut(this))prefs.setTamperLock("Un raccourci d’accessibilité peut désactiver Bernard sans code PIN");
        journal.start();connected=true;lastElapsed=SystemClock.elapsedRealtime();lastWall=System.currentTimeMillis();
        lastZone=java.time.ZoneId.systemDefault().getId();prefs.stopDetectorCounting();
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_USER_PRESENT);filter.addAction(Intent.ACTION_TIME_CHANGED);filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,filter);
        receiverRegistered=true;handler.post(tick);handler.postDelayed(releaseCheck,15_000L);
    }
    private boolean ensureDetectorSupport(String pkg){
        if(detector.isSupported(pkg))return true;
        try{
            Intent web=new Intent(Intent.ACTION_VIEW,Uri.parse("https://example.com"));
            web.addCategory(Intent.CATEGORY_BROWSABLE);web.setPackage(pkg);
            java.util.List<android.content.pm.ResolveInfo> handlers=getPackageManager().queryIntentActivities(web,0);
            if(handlers!=null&&!handlers.isEmpty()){detector.registerBrowserPackage(pkg);return true;}

            // Alternate/clone clients commonly register themselves for the official social URLs.
            // Browsers were already handled above, so a remaining package-specific handler is
            // treated as a whole-app social surface rather than an unmonitored escape hatch.
            if(handlesUrl(pkg,"https://www.instagram.com/")){
                detector.registerWholeAppPackage(pkg,ShortSurfaceDetector.Surface.INSTAGRAM_FEED);return true;
            }
            if(handlesUrl(pkg,"https://www.facebook.com/")){
                detector.registerWholeAppPackage(pkg,ShortSurfaceDetector.Surface.FACEBOOK_FEED);return true;
            }
            if(handlesUrl(pkg,"https://www.tiktok.com/")){
                detector.registerWholeAppPackage(pkg,ShortSurfaceDetector.Surface.TIKTOK_FEED);return true;
            }
            if(handlesUrl(pkg,"https://www.threads.net/")){
                detector.registerWholeAppPackage(pkg,ShortSurfaceDetector.Surface.THREADS_FEED);return true;
            }

            android.content.pm.ApplicationInfo ai=getPackageManager().getApplicationInfo(pkg,0);
            String label=String.valueOf(getPackageManager().getApplicationLabel(ai)).trim().toLowerCase(java.util.Locale.ROOT);
            if(label.equals("instagram")||label.equals("instagram lite")||label.contains("instander")){
                detector.registerWholeAppPackage(pkg,ShortSurfaceDetector.Surface.INSTAGRAM_FEED);return true;
            }
            if(label.equals("facebook")||label.equals("facebook lite")){
                detector.registerWholeAppPackage(pkg,ShortSurfaceDetector.Surface.FACEBOOK_FEED);return true;
            }
            if(label.equals("tiktok")||label.contains("tik tok")){
                detector.registerWholeAppPackage(pkg,ShortSurfaceDetector.Surface.TIKTOK_FEED);return true;
            }
            if(label.equals("threads")||label.contains("threads")){
                detector.registerWholeAppPackage(pkg,ShortSurfaceDetector.Surface.THREADS_FEED);return true;
            }
        }catch(android.content.pm.PackageManager.NameNotFoundException|RuntimeException ignored){}
        return false;
    }

    private boolean handlesUrl(String pkg,String url){
        try{
            Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(url));i.addCategory(Intent.CATEGORY_BROWSABLE);i.setPackage(pkg);
            java.util.List<android.content.pm.ResolveInfo> result=getPackageManager().queryIntentActivities(i,0);
            return result!=null&&!result.isEmpty();
        }catch(RuntimeException ignored){return false;}
    }

    private void registerInstalledBrowsers(){
        try{
            Intent web=new Intent(Intent.ACTION_VIEW,Uri.parse("https://example.com"));
            web.addCategory(Intent.CATEGORY_BROWSABLE);
            for(android.content.pm.ResolveInfo r:getPackageManager().queryIntentActivities(web,0)){
                if(r.activityInfo!=null)detector.registerBrowserPackage(r.activityInfo.packageName);
            }
        }catch(RuntimeException ignored){}
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e){
        if(!connected||e==null)return;
        if(Build.VERSION.SDK_INT>=28&&e.getEventType()==AccessibilityEvent.TYPE_WINDOWS_CHANGED
                &&(e.getWindowChanges()&AccessibilityEvent.WINDOWS_CHANGE_ADDED)!=0)
            systemFlows.windowAdded(e.getWindowId(),e.getEventTime());
        String pkg=e.getPackageName()==null?"":e.getPackageName().toString();
        AccessibilityNodeInfo active=getRootInActiveWindow();
        try {
            String activePkg=active==null||active.getPackageName()==null?"":active.getPackageName().toString();
            boolean current=active!=null&&SystemScreenPolicy.matchesWindow(
                    pkg,e.getWindowId(),activePkg,active.getWindowId());
            if(current&&e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){
                String cls=e.getClassName()==null?"":e.getClassName().toString();
                if(SystemScreenPolicy.isActivityClass(pkg,cls)){
                    windowClass.put(pkg,cls);windowIds.put(pkg,active.getWindowId());
                }
            }
            // A delayed Settings event must not cover Bernard's own PIN, or another app.
            // Unknown/missing roots are sampled again; no permission is granted in that case.
            if(current&&!activePkg.equals(getPackageName())&&guardSystemScreen(activePkg,active))return;
            if(overlay!=null&&current&&!activePkg.equals(blockedPackage)
                    &&!activePkg.equals(getPackageName())&&!activePkg.equals("com.android.systemui")
                    &&!activePkg.contains("launcher")&&!TamperGuard.isSensitivePackage(activePkg)){
                removeOverlay();
            }
        } finally { if(active!=null)active.recycle(); }
        requestSample();
    }

    private String activeClass(String pkg,AccessibilityNodeInfo root){
        Integer id=windowIds.get(pkg);
        return root!=null&&id!=null&&id==root.getWindowId()?windowClass.get(pkg):null;
    }
    private boolean staleSystemRoot(AccessibilityNodeInfo root){
        if(root==null||root.getWindowId()<0)return false;
        boolean available=false,present=false;
        java.util.List<AccessibilityWindowInfo> snapshot=new java.util.ArrayList<>();
        try{
            if(Build.VERSION.SDK_INT>=30){
                android.util.SparseArray<java.util.List<AccessibilityWindowInfo>> displays=getWindowsOnAllDisplays();
                for(int i=0;i<displays.size();i++)snapshot.addAll(displays.valueAt(i));
            }else snapshot.addAll(getWindows());
            available=!snapshot.isEmpty();
            for(AccessibilityWindowInfo w:snapshot)if(w.getId()==root.getWindowId())present=true;
            return SystemScreenPolicy.rootWindowIsStale(available,present);
        }catch(RuntimeException unavailable){return false;/* No evidence: retain the PIN guard. */}
        finally{for(AccessibilityWindowInfo w:snapshot)w.recycle();}
    }
    private boolean guardSystemScreen(String pkg,AccessibilityNodeInfo root){
        boolean userSwitcher=TamperGuard.isSystemUserSwitcher(pkg,root);
        boolean privateSpace=TamperGuard.isPrivateSpaceSurface(pkg,root);
        boolean sensitive=TamperGuard.isBernardControlScreen(pkg,root,activeClass(pkg,root));
        if(!sensitive&&!userSwitcher&&!privateSpace)return false;
        // getRootInActiveWindow can briefly be the last touched, already closed window.
        // A positive live-window snapshot must show it is absent before deferring this event.
        // We do NOT ignore merely unfocused windows (split screen), or an unavailable snapshot.
        // A completed explicit Activity can retain its exit-animation window until onDestroy.
        // Retire only that previously observed window, never all Settings/unfocused windows.
        if(systemFlows.isCompleted(pkg,activeClass(pkg,root),root.getWindowId())
                ||staleSystemRoot(root)){requestSample();return true;}
        boolean allowed=PinGuard.isSystemControlAuthorized()&&(
                (userSwitcher||privateSpace)
                        ?PinGuard.CONTROL_SYSTEM.equals(PinGuard.systemControlScope())
                            &&pkg.equals(PinGuard.systemControlPackage())
                        :TamperGuard.isAuthorizedControlScreen(PinGuard.systemControlScope(),
                            PinGuard.systemControlPackage(),pkg,root,activeClass(pkg,root)));
        if(!allowed){removeOverlay();launchPinGuard(pkg);return true;}
        if(PinGuard.CONTROL_DEVICE_ADMIN.equals(PinGuard.systemControlScope()))
            systemFlows.observeAuthorized(pkg,activeClass(pkg,root),root.getWindowId());
        return false;
    }

    private void requestSample(){if(!queued){queued=true;handler.postDelayed(update,160);}}
    private void flush(){
        long elapsed=SystemClock.elapsedRealtime(),wall=System.currentTimeMillis();long delta=elapsed-lastElapsed;
        String zone=java.time.ZoneId.systemDefault().getId();
        // Check outside Reels too: otherwise a background clock change could renew the day.
        if(ClockPolicy.discontinuity(lastWall,lastElapsed,lastZone,wall,elapsed,zone)){
            prefs.setTamperLock("Horloge ou fuseau modifié : validation du propriétaire nécessaire");
            journal.markIncomplete("Horloge modifiée");
        }else if(tracking&&delta>0){
            // Do not turn deliberate main-thread stalls into free viewing time.
            if(delta>5000)journal.markIncomplete("Comptage retardé pendant une lecture");
            journal.addShort(wall,delta);
        }
        lastElapsed=elapsed;lastWall=wall;lastZone=zone;
    }
    private void sample(){
        if(!connected)return;flush();tracking=false;
        if(prefs.detectorCounting())prefs.setDetectorStatus(prefs.detectorStatus(),false);
        boolean awake=power!=null&&power.isInteractive()&&(keyguard==null||!keyguard.isKeyguardLocked());
        long now=SystemClock.elapsedRealtime();
        if(now-lastSample>=10_000){
            // ADB remains intentionally allowed; other tamper conditions are still checked below.
            if(!PermissionUtils.isAutomaticTimeEnabled(this)||!PermissionUtils.isAutomaticTimeZoneEnabled(this))prefs.setTamperLock("L’heure ou le fuseau automatique est désactivé et pourrait réinitialiser les quotas");
            if(PermissionUtils.hasBernardAccessibilityShortcut(this))prefs.setTamperLock("Un raccourci d’accessibilité peut désactiver Bernard sans code PIN");
            journal.sample(UsageUtils.today(this));lastSample=now;
        }
        if(!awake){removeOverlay();return;}
        if(overlay!=null){
            // Re-evaluate even while the overlay covers the launcher. Rules, quotas, midnight
            // and automatic window opening must all invalidate an obsolete blocking decision.
            AccessibilityNodeInfo current=getRootInActiveWindow();
            try{
                String pkg=current==null||current.getPackageName()==null?"":current.getPackageName().toString();
                if(!pkg.equals(getPackageName())&&current!=null&&guardSystemScreen(pkg,current))return;
            }finally{if(current!=null)current.recycle();}
            AccessEvaluator.Decision fresh=access.evaluate(blockedPackage,blockedSurface);
            if(!fresh.blocked())removeOverlay();
            else{
                if(!fresh.displayKey(prefs).equals(overlayKey))showBlock(blockedPackage,blockedSurface,fresh,false);
                return;
            }
        }
        AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return;
        try{
            String pkg=root.getPackageName()==null?"":root.getPackageName().toString();
            if(pkg.isEmpty()||pkg.equals(getPackageName()))return;

            if(guardSystemScreen(pkg,root))return;
            if(TamperGuard.isSensitivePackage(pkg))return;

            ShortSurfaceDetector.Surface surface=null;
            boolean supported=prefs.shortEnabled()&&ensureDetectorSupport(pkg);
            if(supported)
                surface=detector.detect(pkg,root,activeClass(pkg,root),true,prefs.diagnosticMode());
            if(supported&&prefs.diagnosticMode())com.local.focusfence.storage.DetectorEvidence.record(prefs,detector.diagnosticEvidence(pkg,root,surface));
            boolean selected=surface!=null&&prefs.shortEnabled()&&prefs.featureEnabled(surface.name());
            AccessEvaluator.Decision decision=access.evaluate(pkg,surface);
            if(supported)prefs.setDetectorStatus(detector.surfaceLabel(surface),selected&&!decision.blocked());
            if(decision.blocked()){
                if("com.instagram.android".equals(pkg)&&decision.social()){
                    // Never cover DMs for a selective social rule. The deferred verification
                    // uses the same evaluator, so changed schedules do not leave a redirect latch.
                    if(instagramRedirectPending)return;
                    instagramRedirectPending=true;instagramRedirectAt=SystemClock.elapsedRealtime();
                    access.record(pkg,surface,decision);
                    boolean redirected=detector.openInstagramMessages(root);
                    if(!redirected)redirected=openInstagramInbox();
                    if(!redirected)performGlobalAction(GLOBAL_ACTION_HOME);
                    if(SystemClock.elapsedRealtime()-lastInstagramNotice>3000){
                        lastInstagramNotice=SystemClock.elapsedRealtime();
                        Toast.makeText(this,decision.message()+" Messages Instagram accessibles.",Toast.LENGTH_SHORT).show();
                    }
                    handler.removeCallbacks(verifyInstagram);handler.postDelayed(verifyInstagram,350);
                    return;
                }
                showBlock(pkg,surface,decision,true);return;
            }
            tracking=selected;
        }catch(IllegalStateException ex){journal.markIncomplete("Écran momentanément inaccessible");}
        finally{root.recycle();}
    }
    private void launchPinGuard(String sourcePackage){
        long now=SystemClock.elapsedRealtime();
        if(now-lastPinLaunch<800)return;
        lastPinLaunch=now;
        try{
            if(PinActivity.isVisible())return;
            // A new PIN challenge authorizes its actual target, not an unrelated stale scope.
            String scope=PinGuard.CONTROL_SYSTEM;
            String scopedPackage=sourcePackage==null?"":sourcePackage;
            Intent i=new Intent(this,PinActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(PinActivity.EXTRA_GUARD_MODE,true)
                    .putExtra(PinActivity.EXTRA_CONTROL_SCOPE,scope)
                    .putExtra(PinActivity.EXTRA_CONTROL_PACKAGE,scopedPackage);
            startActivity(i);
        }catch(RuntimeException ignored){performGlobalAction(GLOBAL_ACTION_HOME);}
    }

    private void verifyInstagramRedirect(){
        if(!connected||!instagramRedirectPending)return;
        AccessibilityNodeInfo root=getRootInActiveWindow();
        boolean stillBlocked=false;
        try{
            String pkg=root==null||root.getPackageName()==null?"":root.getPackageName().toString();
            if("com.instagram.android".equals(pkg)){
                ShortSurfaceDetector.Surface surface=detector.detect(pkg,root,activeClass(pkg,root),true,false);
                stillBlocked=access.evaluate(pkg,surface).blocked();
            }
        }finally{if(root!=null)root.recycle();}
        if(stillBlocked&&SystemClock.elapsedRealtime()-instagramRedirectAt<1800){
            handler.postDelayed(verifyInstagram,250);return;
        }
        instagramRedirectPending=false;
        if(stillBlocked)performGlobalAction(GLOBAL_ACTION_HOME);
        requestSample();
    }
    private boolean openInstagramInbox(){
        try{
            Intent i=new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/direct/inbox/"));
            i.setPackage("com.instagram.android");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            return true;
        }catch(RuntimeException ignored){return false;}
    }
    private void showBlock(String pkg,ShortSurfaceDetector.Surface surface,AccessEvaluator.Decision decision,boolean navigateAway){
        removeOverlay();tracking=false;blockedPackage=pkg;blockedSurface=surface;overlayAt=SystemClock.elapsedRealtime();
        overlayKey=decision.displayKey(prefs);access.record(pkg,surface,decision);
        String reason=decision.message(),resume=decision.detail(prefs);
        boolean shortContent=decision.social(),schedule=decision.schedule();
        if(navigateAway)performGlobalAction(shortContent?GLOBAL_ACTION_BACK:GLOBAL_ACTION_HOME);
        overlay=Ui.blockScreen(this,prefs.person(),reason,resume,shortContent,schedule,()->{
            removeOverlay();if(!shortContent)performGlobalAction(GLOBAL_ACTION_HOME);requestSample();
        },()->{
            removeOverlay();Intent i=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);i.putExtra("page","limits");startActivity(i);
        });
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.START;
        overlay.setOnApplyWindowInsetsListener((v,insets)->{Ui.pad(v,0,0,0,0);v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        try{windows.addView(overlay,params);overlay.requestApplyInsets();}catch(RuntimeException e){overlay=null;performGlobalAction(GLOBAL_ACTION_HOME);journal.markIncomplete("Écran de blocage indisponible");}
    }
    private void removeOverlay(){if(overlay!=null){try{windows.removeView(overlay);}catch(IllegalArgumentException ignored){}overlay=null;}}
    @Override public void onInterrupt(){if(connected)flush();tracking=false;instagramRedirectPending=false;handler.removeCallbacks(verifyInstagram);if(prefs!=null)prefs.stopDetectorCounting();removeOverlay();if(journal!=null)journal.markIncomplete("Service interrompu par Android");}
    @Override public boolean onUnbind(Intent intent){
        if(connected)flush();
        systemFlows.clear();connected=false;tracking=false;instagramRedirectPending=false;Journal.monitoring=false;if(prefs!=null)prefs.stopDetectorCounting();handler.removeCallbacksAndMessages(null);queued=false;
        if(prefs!=null)prefs.raw().unregisterOnSharedPreferenceChangeListener(ruleListener);
        removeOverlay();if(running.get()==this)running.clear();
        // Android also unbinds a service during package replacement. That is not proof that
        // the user revoked its permission; the actual secure setting is the deciding evidence.
        boolean revoked=!PermissionUtils.isAccessibilityEnabled(this);
        if(prefs!=null&&revoked)prefs.setTamperLock("Le service d’accessibilité de Bernard a été désactivé");
        if(revoked&&FortressPolicy.isDeviceOwner(this))FortressPolicy.setFailSafeSuspended(this,true);
        if(journal!=null)journal.markIncomplete("Service d’accessibilité désactivé");
        return super.onUnbind(intent);
    }
    @Override public void onDestroy(){
        if(connected)flush();
        systemFlows.clear();connected=false;tracking=false;instagramRedirectPending=false;Journal.monitoring=false;if(prefs!=null)prefs.stopDetectorCounting();handler.removeCallbacksAndMessages(null);
        if(prefs!=null)prefs.raw().unregisterOnSharedPreferenceChangeListener(ruleListener);
        if(running.get()==this)running.clear();removeOverlay();try{if(receiverRegistered)unregisterReceiver(receiver);}catch(IllegalArgumentException ignored){}if(journal!=null)journal.stop();super.onDestroy();
    }
}
