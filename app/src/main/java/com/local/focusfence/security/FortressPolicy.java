package com.local.focusfence.security;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.UserManager;
import com.local.focusfence.model.AppRule;
import com.local.focusfence.storage.Prefs;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Optional Device Owner policies. A normal installation never acquires these privileges. */
public final class FortressPolicy {
    private static final String SUSPENDED = "fortress_suspended_packages_v47";
    private FortressPolicy() {}
    public static ComponentName admin(Context c) { return new ComponentName(c, BernardDeviceAdminReceiver.class); }
    private static DevicePolicyManager manager(Context c) { return (DevicePolicyManager)c.getSystemService(Context.DEVICE_POLICY_SERVICE); }
    public static boolean isAdminActive(Context c) {
        try { DevicePolicyManager d=manager(c); return d!=null&&d.isAdminActive(admin(c)); }
        catch (RuntimeException e) { return false; }
    }
    public static boolean isDeviceOwner(Context c) {
        try { DevicePolicyManager d=manager(c); return d!=null&&d.isDeviceOwnerApp(c.getPackageName()); }
        catch (RuntimeException e) { return false; }
    }
    private static List<String> restrictions() {
        List<String> r=new ArrayList<>();
        Collections.addAll(r, UserManager.DISALLOW_SAFE_BOOT, UserManager.DISALLOW_ADD_USER,
                UserManager.DISALLOW_REMOVE_USER, UserManager.DISALLOW_ADD_MANAGED_PROFILE,
                UserManager.DISALLOW_DEBUGGING_FEATURES, UserManager.DISALLOW_FACTORY_RESET,
                UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, UserManager.DISALLOW_INSTALL_APPS,
                UserManager.DISALLOW_UNINSTALL_APPS, UserManager.DISALLOW_APPS_CONTROL);
        if(Build.VERSION.SDK_INT>=28) Collections.addAll(r,UserManager.DISALLOW_USER_SWITCH,UserManager.DISALLOW_CONFIG_DATE_TIME);
        if(Build.VERSION.SDK_INT>=34) r.add(UserManager.DISALLOW_GRANT_ADMIN);
        if(Build.VERSION.SDK_INT>=35) r.add(UserManager.DISALLOW_ADD_PRIVATE_PROFILE);
        return r;
    }
    private static void step(PolicyBatch b, boolean write, String name, PolicyBatch.Action action, PolicyBatch.Check check) {
        if(write) b.apply(name,action,check); else b.verify(name,check);
    }
    private static PolicyBatch policies(Context c, boolean enabled, boolean write) {
        PolicyBatch b=new PolicyBatch(); DevicePolicyManager d;
        try { d=manager(c); } catch(RuntimeException e) { b.fail("Gestionnaire de politiques indisponible");return b; }
        ComponentName a=admin(c); String pkg=c.getPackageName();
        if(d==null||!isDeviceOwner(c)) { b.fail("Rôle Device Owner absent"); return b; }
        step(b,write,"Anti-désinstallation",()->d.setUninstallBlocked(a,pkg,enabled),()->d.isUninstallBlocked(a,pkg)==enabled);
        step(b,write,"Services d’accessibilité",()->{
            if(!d.setPermittedAccessibilityServices(a,enabled?Collections.singletonList(pkg):null))
                throw new IllegalStateException("Services déjà actifs incompatibles");
        },()->enabled?Collections.singletonList(pkg).equals(d.getPermittedAccessibilityServices(a)):d.getPermittedAccessibilityServices(a)==null);
        if(Build.VERSION.SDK_INT>=30) {
            step(b,write,"Protection des données de Bernard",()->{
                List<String> existing=new ArrayList<>(d.getUserControlDisabledPackages(a));
                if(enabled&&!existing.contains(pkg))existing.add(pkg); if(!enabled)existing.remove(pkg);
                d.setUserControlDisabledPackages(a,existing);
            },()->d.getUserControlDisabledPackages(a).contains(pkg)==enabled);
            // Relax the restriction, not the user's automatic clock setting.
            if(enabled) {
                step(b,write,"Heure automatique",()->d.setAutoTimeEnabled(a,true),()->d.getAutoTimeEnabled(a));
                step(b,write,"Fuseau automatique",()->d.setAutoTimeZoneEnabled(a,true),()->d.getAutoTimeZoneEnabled(a));
            }
        } else step(b,write,"Heure automatique obligatoire",()->d.setAutoTimeRequired(a,enabled),()->d.getAutoTimeRequired()==enabled);
        for(String r:restrictions()) step(b,write,r,()->{
            if(enabled)d.addUserRestriction(a,r);else d.clearUserRestriction(a,r);
        },()->d.getUserRestrictions(a).getBoolean(r,false)==enabled);
        return b;
    }
    /** Read-only, checks every required policy, not merely uninstall protection. */
    public static boolean isEnforced(Context c) { return policies(c,true,false).success(); }
    public static List<String> missingPolicies(Context c) { return policies(c,true,false).failures(); }
    public static String lastFailure(Context c) {
        Object v=new Prefs(c).raw().getAll().get("fortress_failures_v47"); return v instanceof String?(String)v:"";
    }
    private static boolean record(Context c,String operation,PolicyBatch b) {
        new Prefs(c).raw().edit().putString("fortress_last_operation_v47",operation)
                .putString("fortress_failures_v47",String.join(" ; ",b.failures()))
                .putLong("fortress_checked_at_v47",System.currentTimeMillis()).apply();
        return b.success();
    }
    public static boolean apply(Context c) {
        if(!isDeviceOwner(c))return false;
        PolicyBatch b=policies(c,true,true);
        try { manager(c).setShortSupportMessage(admin(c),"Bernard protège ces réglages. Utilise le code administrateur dans Bernard Bloqueur."); }
        catch(RuntimeException e) { android.util.Log.w("BernardFortress","Optional support message unavailable",e); }
        if(!b.success())new Prefs(c).setTamperLock("Forteresse incomplète : vérification du propriétaire nécessaire");
        return record(c,"apply",b);
    }
    /** Attempts all removals even if one Android policy fails; reports partial recovery honestly. */
    public static boolean relax(Context c) {
        if(!isDeviceOwner(c))return false;
        PolicyBatch b=policies(c,false,true);
        if(!setFailSafeSuspended(c,false))b.fail("Certaines applications restent suspendues");
        return record(c,"relax",b);
    }
    public static boolean setFailSafeSuspended(Context c,boolean suspended) {
        if(!isDeviceOwner(c))return false;
        DevicePolicyManager d=manager(c);ComponentName a=admin(c);Prefs p=new Prefs(c);PolicyBatch result=new PolicyBatch();
        Set<String> remembered=new LinkedHashSet<>();
        Object saved=p.raw().getAll().get(SUSPENDED);
        if(saved instanceof Set<?>)for(Object v:(Set<?>)saved)if(v instanceof String)remembered.add((String)v);
        Set<String> targets=failSafePackages(c,!suspended);targets.addAll(remembered);
        // An absent app is not a failed suspension. Keep remembered targets for future recovery.
        List<String> installed=new ArrayList<>(),changes=new ArrayList<>();
        for(String pkg:targets) {
            try { c.getPackageManager().getApplicationInfo(pkg,0); installed.add(pkg);
                if(d.isPackageSuspended(a,pkg)!=suspended)changes.add(pkg);
            } catch(PackageManager.NameNotFoundException absent) { /* Not installed in this user. */ }
            catch(RuntimeException e) { result.fail(pkg+": état de suspension inconnu"); }
        }
        if(!changes.isEmpty()) {
            try { result.suspensionResult(d.setPackagesSuspended(a,changes.toArray(new String[0]),suspended)); }
            catch(RuntimeException e) { result.fail("Suspension : "+e.getClass().getSimpleName()); }
        }
        for(String pkg:installed) {
            result.verify("Suspension "+pkg,()->d.isPackageSuspended(a,pkg)==suspended);
            try { if(d.isPackageSuspended(a,pkg))remembered.add(pkg);else remembered.remove(pkg); }
            catch(PackageManager.NameNotFoundException|RuntimeException e) { result.fail(pkg+": état final inconnu"); }
        }
        if(!p.raw().edit().putStringSet(SUSPENDED,remembered).commit())result.fail("État de suspension non enregistré");
        if(!result.success())p.setTamperLock("Suspension Forteresse non confirmée : vérification du propriétaire nécessaire");
        return record(c,suspended?"suspend":"resume",result);
    }
    private static Set<String> failSafePackages(Context c,boolean recovering) {
        LinkedHashSet<String> out=new LinkedHashSet<>(); Prefs p=new Prefs(c);
        if(recovering||p.gamesEnabled())out.addAll(p.gamePackages());
        for(AppRule r:p.getAppRules())if(recovering||r.enabled)out.add(r.packageName);
        if(recovering||(p.shortEnabled()&&p.anyShortFeature())) {
            Collections.addAll(out,"com.instagram.android","com.instagram.lite","com.facebook.katana","com.facebook.lite",
                    "com.google.android.youtube","com.zhiliaoapp.musically","com.ss.android.ugc.trill","com.instagram.barcelona");
            try {
                Intent web=new Intent(Intent.ACTION_VIEW,Uri.parse("https://example.com"));web.addCategory(Intent.CATEGORY_BROWSABLE);
                for(android.content.pm.ResolveInfo r:c.getPackageManager().queryIntentActivities(web,0))
                    if(r.activityInfo!=null)out.add(r.activityInfo.packageName);
            } catch(RuntimeException e) { new Prefs(c).setTamperLock("Inventaire des navigateurs indisponible"); }
        }
        out.remove(c.getPackageName());out.remove("com.android.settings");out.remove("com.android.systemui");
        return out;
    }
}
