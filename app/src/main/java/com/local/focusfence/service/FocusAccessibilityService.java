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
import com.local.focusfence.detector.ShortSurfaceDetector;
import com.local.focusfence.model.AppRule;
import com.local.focusfence.security.PinGuard;
import com.local.focusfence.security.BypassAppDetector;
import com.local.focusfence.security.FortressPolicy;
import com.local.focusfence.security.TamperGuard;
import com.local.focusfence.storage.*;
import com.local.focusfence.ui.*;
import com.local.focusfence.util.*;
import java.util.*;

/** Local enforcement. Polling is independent of scrolling; UI events cannot postpone the timer. */
public final class FocusAccessibilityService extends AccessibilityService {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ShortSurfaceDetector detector=new ShortSurfaceDetector();
    private final Map<String,String> windowClass=new HashMap<>();
    private Prefs prefs;private Journal journal;private PowerManager power;private KeyguardManager keyguard;
    private WindowManager windows;private View overlay;private String blockedPackage="";
    private long overlayAt,lastElapsed,lastWall,lastSample,lastPinLaunch;private boolean tracking,connected,queued;
    private final Runnable update=()->{queued=false;sample();};
    private final Runnable tick=new Runnable(){public void run(){if(!connected)return;sample();handler.postDelayed(this,power!=null&&power.isInteractive()?1000:30_000);}};
    private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        String action=i.getAction();
        if(Intent.ACTION_TIME_CHANGED.equals(action)||Intent.ACTION_TIMEZONE_CHANGED.equals(action)){tracking=false;prefs.setTamperLock("Horloge ou fuseau horaire modifié");journal.markIncomplete("Horloge ou fuseau horaire modifié");}
        if(Intent.ACTION_SCREEN_OFF.equals(action)){flush();tracking=false;removeOverlay();}
        requestSample();
    }};
    @Override protected void onServiceConnected(){
        super.onServiceConnected();PinGuard.ensureConfigured(this);prefs=new Prefs(this);journal=new Journal(this);power=(PowerManager)getSystemService(POWER_SERVICE);keyguard=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);windows=(WindowManager)getSystemService(WINDOW_SERVICE);
        registerInstalledBrowsers();
        FortressPolicy.apply(this);
        if(prefs.deviceAdminSeen()&&!FortressPolicy.isAdminActive(this))prefs.setTamperLock("La protection anti-désinstallation de Bernard a été retirée");
        if(FortressPolicy.isDeviceOwner(this)&&!prefs.tamperLock())FortressPolicy.setFailSafeSuspended(this,false);
        if((getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)==0&&PermissionUtils.isAdbEnabled(this))prefs.setTamperLock("Le débogage ADB est actif et peut contourner Bernard");
        if(!PermissionUtils.isAutomaticTimeEnabled(this)||!PermissionUtils.isAutomaticTimeZoneEnabled(this))prefs.setTamperLock("L’heure ou le fuseau automatique est désactivé et pourrait réinitialiser les quotas");
        if(PermissionUtils.hasBernardAccessibilityShortcut(this))prefs.setTamperLock("Un raccourci d’accessibilité peut désactiver Bernard sans code PIN");
        journal.start();connected=true;lastElapsed=SystemClock.elapsedRealtime();lastWall=System.currentTimeMillis();
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_USER_PRESENT);filter.addAction(Intent.ACTION_TIME_CHANGED);filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,filter);
        handler.post(tick);
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
        String pkg=e.getPackageName()==null?"":e.getPackageName().toString();
        if(e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){
            if(e.getClassName()!=null)windowClass.put(pkg,e.getClassName().toString());
        }
        // Sensitive system surfaces are processed even while Bernard's block overlay is visible.
        // Otherwise the notification shade / quick settings could be used as a side door while
        // enforcement was effectively paused behind the overlay.
        if(TamperGuard.requiresImmediatePin(pkg)){
            AccessibilityNodeInfo active=getRootInActiveWindow();
            boolean allowed=PinGuard.isSystemControlAuthorized()
                    && TamperGuard.isAuthorizedControlScreen(
                    PinGuard.systemControlScope(),PinGuard.systemControlPackage(),
                    pkg,active,windowClass.get(pkg));
            if(active!=null)active.recycle();
            if(!allowed){
                removeOverlay();
                launchPinGuard(pkg);
                return;
            }
        } else if("com.android.systemui".equals(pkg)) {
            AccessibilityNodeInfo active=getRootInActiveWindow();
            boolean switcher=TamperGuard.isSystemUserSwitcher(pkg,active);
            if(active!=null)active.recycle();
            if(switcher&&!PinGuard.isSystemControlAuthorized()){
                removeOverlay();
                launchPinGuard(pkg);
                return;
            }
        } else if(!PinGuard.isSystemControlAuthorized()
                || !pkg.equals(PinGuard.systemControlPackage())) {
            AccessibilityNodeInfo active=getRootInActiveWindow();
            boolean privateSpace=TamperGuard.isPrivateSpaceSurface(pkg,active);
            if(active!=null)active.recycle();
            if(privateSpace){
                removeOverlay();
                launchPinGuard(pkg);
                return;
            }
        }

        // A normal blocked app cannot dismiss the overlay by changing its own window.
        if(overlay!=null)return;
        if(TamperGuard.isSensitivePackage(pkg)){handler.removeCallbacks(update);queued=true;handler.postDelayed(update,40);}
        else requestSample();
    }
    private void requestSample(){if(!queued){queued=true;handler.postDelayed(update,160);}}
    private void flush(){
        long elapsed=SystemClock.elapsedRealtime(),wall=System.currentTimeMillis();long delta=elapsed-lastElapsed;
        if(tracking&&delta>0){
            if(Math.abs((wall-lastWall)-delta)>5000){
                prefs.setTamperLock("Changement d’horloge détecté");journal.markIncomplete("Horloge modifiée");
            }else{
                // elapsedRealtime is monotonic: a delayed handler is real time spent on the
                // controlled surface, not free usage. Mark the day incomplete if the delay is
                // abnormal, but still charge the full interval so induced UI stalls cannot bypass
                // the quota.
                if(delta>5000)journal.markIncomplete("Comptage retardé pendant une lecture");
                journal.addShort(wall,delta);
            }
        }
        lastElapsed=elapsed;lastWall=wall;
    }
    private void sample(){
        if(!connected)return;flush();tracking=false;
        boolean awake=power!=null&&power.isInteractive()&&(keyguard==null||!keyguard.isKeyguardLocked());
        long now=SystemClock.elapsedRealtime();
        if(now-lastSample>=10_000){
            if((getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)==0&&PermissionUtils.isAdbEnabled(this))prefs.setTamperLock("Le débogage ADB est actif et peut contourner Bernard");
            if(!PermissionUtils.isAutomaticTimeEnabled(this)||!PermissionUtils.isAutomaticTimeZoneEnabled(this))prefs.setTamperLock("L’heure ou le fuseau automatique est désactivé et pourrait réinitialiser les quotas");
            if(PermissionUtils.hasBernardAccessibilityShortcut(this))prefs.setTamperLock("Un raccourci d’accessibilité peut désactiver Bernard sans code PIN");
            journal.sample(UsageUtils.today(this));lastSample=now;
        }
        if(!awake){removeOverlay();return;}if(overlay!=null)return;
        AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return;
        try{
            String pkg=root.getPackageName()==null?"":root.getPackageName().toString();
            if(pkg.isEmpty()||pkg.equals(getPackageName()))return;

            if(TamperGuard.isSensitivePackage(pkg)){
                if("com.android.systemui".equals(pkg)&&TamperGuard.isSystemUserSwitcher(pkg,root)
                        &&!PinGuard.isSystemControlAuthorized()){
                    launchPinGuard(pkg);return;
                }
                boolean allowed=PinGuard.isSystemControlAuthorized()
                        && TamperGuard.isAuthorizedControlScreen(
                        PinGuard.systemControlScope(),PinGuard.systemControlPackage(),
                        pkg,root,windowClass.get(pkg));
                if(TamperGuard.isBernardControlScreen(pkg,root,windowClass.get(pkg))&&!allowed)launchPinGuard(pkg);
                return;
            }
            if(TamperGuard.isPrivateSpaceSurface(pkg,root)
                    &&(!PinGuard.isSystemControlAuthorized()
                    || !pkg.equals(PinGuard.systemControlPackage()))){
                launchPinGuard(pkg);return;
            }

            int minute=TimeUtils.nowMinute();boolean usage=PermissionUtils.hasUsageAccess(this);
            if(prefs.hasActiveProtection()&&BypassAppDetector.isKnownContainer(this,pkg)){
                block(pkg,"Cet espace parallèle est bloqué pendant la protection Bernard.","Les applications clonées ou isolées pourraient contourner les limites sociales.",false,false);return;
            }
            if(prefs.gamesEnabled()&&prefs.gamePackages().contains(pkg)){
                if(prefs.tamperLock()){block(pkg,"Bernard a détecté une tentative de contournement.",prefs.tamperReason()+". Entre le code dans Bernard pour réactiver.",false,false);return;}
                if(!usage){block(pkg,"L’accès aux données d’utilisation a été retiré.","Bernard bloque les jeux jusqu’à ce que l’autorisation soit restaurée avec le code.",false,false);return;}
                if(!Rules.allowed(minute,prefs.gamesStartMinute(),prefs.gamesEndMinute())){block(pkg,"Les jeux sont en pause pour l’instant.","Prochaine ouverture à "+Rules.clock(prefs.gamesStartMinute())+".",false,true);return;}
                if(Rules.exhausted(UsageUtils.todayUsageMs(this,prefs.gamePackages()),prefs.gamesLimitMinutes())){block(pkg,"Le quota Jeux du jour est atteint.","Tu pourras rejouer demain pendant ta plage autorisée.",false,false);return;}
            }
            AppRule r=prefs.getAppRule(pkg);
            if(r!=null&&r.enabled){
                if(prefs.tamperLock()){block(pkg,"Bernard a détecté une tentative de contournement.",prefs.tamperReason()+". Entre le code dans Bernard pour réactiver.",false,false);return;}
                if(r.alwaysBlocked){block(pkg,r.label+" est bloquée par ta règle.","Cette règle reste active jusqu’à sa modification.",false,false);return;}
                if(r.dailyLimitMinutes>0&&!usage){block(pkg,"L’accès aux données d’utilisation a été retiré.","Bernard bloque cette application jusqu’à restauration de l’autorisation avec le code.",false,false);return;}
                if(!Rules.allowed(minute,r.startMinute,r.endMinute)){block(pkg,r.label+" n’est pas autorisée à cette heure.","Prochaine ouverture à "+Rules.clock(r.startMinute)+".",false,true);return;}
                if(r.dailyLimitMinutes>0&&Rules.exhausted(UsageUtils.todayUsageMs(this,pkg),r.dailyLimitMinutes)){block(pkg,"La limite du jour est atteinte pour "+r.label+".","Tu pourras revenir demain pendant ta plage autorisée.",false,false);return;}
            }
            if(prefs.shortEnabled()&&ensureDetectorSupport(pkg)){
                // Always identify Stories first; individual source switches decide whether to count them.
                ShortSurfaceDetector.Surface surface=detector.detect(pkg,root,windowClass.get(pkg),true,prefs.diagnosticMode());
                boolean selected=surface!=null&&prefs.featureEnabled(surface.name());
                prefs.setDetectorStatus(detector.surfaceLabel(surface),selected);
                if(selected){
                    boolean schedule=!Rules.allowed(minute,prefs.shortStartMinute(),prefs.shortEndMinute());
                    boolean antiTamper=prefs.tamperLock();
                    if(antiTamper||schedule||Rules.exhausted(journal.shortMs(),prefs.shortLimitMinutes())){
                        if("com.instagram.android".equals(pkg)){
                            // Selective Instagram blocking must never cover Direct Messages with a
                            // full-screen overlay. Redirect to the inbox and leave it interactive.
                            tracking=false;
                            boolean redirected=detector.openInstagramMessages(root);
                            if(!redirected) redirected=openInstagramInbox();
                            if(!redirected)performGlobalAction(GLOBAL_ACTION_HOME);
                            Toast.makeText(this,
                                    antiTamper?"Protection anti-contournement · messages accessibles":
                                    schedule?"Bernard ferme le scroll pour l’instant · messages accessibles":
                                            "Quota atteint · messages Instagram accessibles",
                                    Toast.LENGTH_SHORT).show();
                            handler.postDelayed(this::verifyInstagramRedirect,280);
                            return;
                        }
                        block(pkg,antiTamper?"Bernard a détecté une tentative de contournement.":schedule?"Le scroll infini fait une pause.":"La limite de scroll infini est atteinte.",antiTamper?prefs.tamperReason()+". Entre le code dans Bernard pour réactiver.":schedule?"Prochaine ouverture à "+Rules.clock(prefs.shortStartMinute())+".":"Tu pourras revenir demain pendant ta plage autorisée.",true,schedule);
                        return;
                    }else tracking=true;
                }
            }
        }catch(IllegalStateException ex){journal.markIncomplete("Écran momentanément inaccessible");}
        finally{root.recycle();}
    }
    private void launchPinGuard(String sourcePackage){
        long now=SystemClock.elapsedRealtime();
        if(now-lastPinLaunch<800)return;
        lastPinLaunch=now;
        try{
            Intent i=new Intent(this,PinActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(PinActivity.EXTRA_GUARD_MODE,true)
                    .putExtra(PinActivity.EXTRA_CONTROL_SCOPE,PinGuard.CONTROL_SYSTEM)
                    .putExtra(PinActivity.EXTRA_CONTROL_PACKAGE,sourcePackage==null?"":sourcePackage);
            startActivity(i);
        }catch(RuntimeException ignored){performGlobalAction(GLOBAL_ACTION_HOME);}
    }

    private void verifyInstagramRedirect(){
        if(!connected)return;
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null){requestSample();return;}
        try{
            String pkg=root.getPackageName()==null?"":root.getPackageName().toString();
            if(!"com.instagram.android".equals(pkg)){requestSample();return;}
            ShortSurfaceDetector.Surface surface=detector.detect(pkg,root,windowClass.get(pkg),true,false);
            if(surface!=null&&prefs.featureEnabled(surface.name())){
                // The click/deep-link did not actually land in a safe Instagram surface.
                performGlobalAction(GLOBAL_ACTION_HOME);
            }
        }finally{root.recycle();}
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
    private void block(String pkg,String reason,String resume,boolean shortContent,boolean schedule){
        tracking=false;blockedPackage=pkg;overlayAt=SystemClock.elapsedRealtime();
        performGlobalAction(shortContent?GLOBAL_ACTION_BACK:GLOBAL_ACTION_HOME);
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
    @Override public void onInterrupt(){tracking=false;removeOverlay();if(journal!=null)journal.markIncomplete("Service interrompu par Android");}
    @Override public boolean onUnbind(Intent intent){
        connected=false;tracking=false;
        if(prefs!=null)prefs.setTamperLock("Le service d’accessibilité de Bernard a été désactivé");
        if(FortressPolicy.isDeviceOwner(this))FortressPolicy.setFailSafeSuspended(this,true);
        if(journal!=null)journal.markIncomplete("Service d’accessibilité désactivé");
        return super.onUnbind(intent);
    }
    @Override public void onDestroy(){
        connected=false;tracking=false;handler.removeCallbacksAndMessages(null);removeOverlay();try{unregisterReceiver(receiver);}catch(IllegalArgumentException ignored){}if(journal!=null)journal.stop();super.onDestroy();
    }
}
