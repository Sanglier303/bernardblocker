package com.local.focusfence.security;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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

/**
 * Optional Android Enterprise hardening.
 *
 * Nothing here is active on a normal install. If Bernard is provisioned as Device Owner, Android
 * itself enforces these restrictions underneath the AccessibilityService/PIN layer.
 */
public final class FortressPolicy {
    private FortressPolicy() {}

    public static ComponentName admin(Context context) {
        return new ComponentName(context, BernardDeviceAdminReceiver.class);
    }

    public static boolean isAdminActive(Context context) {
        DevicePolicyManager dpm=(DevicePolicyManager)context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm!=null && dpm.isAdminActive(admin(context));
    }

    public static boolean isDeviceOwner(Context context) {
        DevicePolicyManager dpm=(DevicePolicyManager)context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm!=null && dpm.isDeviceOwnerApp(context.getPackageName());
    }

    public static boolean apply(Context context) {
        DevicePolicyManager dpm=(DevicePolicyManager)context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if(dpm==null || !dpm.isDeviceOwnerApp(context.getPackageName())) return false;
        ComponentName admin=admin(context);
        String pkg=context.getPackageName();
        try {
            dpm.setUninstallBlocked(admin,pkg,true);
            // Only Bernard (plus system accessibility services, which Android always permits)
            // may be enabled as a third-party accessibility service in Fortress mode.
            dpm.setPermittedAccessibilityServices(admin,Collections.singletonList(pkg));
            if(Build.VERSION.SDK_INT>=30) {
                dpm.setUserControlDisabledPackages(admin, Collections.singletonList(pkg));
                dpm.setAutoTimeEnabled(admin,true);
                dpm.setAutoTimeZoneEnabled(admin,true);
            } else {
                dpm.setAutoTimeRequired(admin,true);
            }
            dpm.addUserRestriction(admin,UserManager.DISALLOW_SAFE_BOOT);
            dpm.addUserRestriction(admin,UserManager.DISALLOW_ADD_USER);
            dpm.addUserRestriction(admin,UserManager.DISALLOW_REMOVE_USER);
            dpm.addUserRestriction(admin,UserManager.DISALLOW_ADD_MANAGED_PROFILE);
            dpm.addUserRestriction(admin,UserManager.DISALLOW_DEBUGGING_FEATURES);
            dpm.addUserRestriction(admin,UserManager.DISALLOW_FACTORY_RESET);
            dpm.addUserRestriction(admin,UserManager.DISALLOW_GRANT_ADMIN);
            dpm.addUserRestriction(admin,UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
            // In Fortress mode, new apps are themselves an escape hatch (alternate social clients,
            // second browsers, clone tools). The administrator can temporarily relax the policy
            // from Bernard after entering the PIN.
            dpm.addUserRestriction(admin,UserManager.DISALLOW_INSTALL_APPS);
            dpm.addUserRestriction(admin,UserManager.DISALLOW_UNINSTALL_APPS);
            dpm.addUserRestriction(admin,UserManager.DISALLOW_APPS_CONTROL);
            if(Build.VERSION.SDK_INT>=35) dpm.addUserRestriction(admin,UserManager.DISALLOW_ADD_PRIVATE_PROFILE);
            if(Build.VERSION.SDK_INT>=28) {
                dpm.addUserRestriction(admin,UserManager.DISALLOW_USER_SWITCH);
                dpm.addUserRestriction(admin,UserManager.DISALLOW_CONFIG_DATE_TIME);
            }
            dpm.setShortSupportMessage(admin,"Bernard protège ces réglages. Utilise le code administrateur dans Bernard Bloqueur.");
            return true;
        } catch(SecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * When the accessibility service is disabled in Device Owner mode, Android itself suspends
     * the controlled apps. This gives Fortress mode a fail-closed path even though an accessibility
     * service cannot prevent its own shutdown.
     */
    public static boolean setFailSafeSuspended(Context context, boolean suspended) {
        DevicePolicyManager dpm=(DevicePolicyManager)context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if(dpm==null || !dpm.isDeviceOwnerApp(context.getPackageName())) return false;
        Set<String> targets=failSafePackages(context);
        if(targets.isEmpty()) return true;
        try {
            dpm.setPackagesSuspended(admin(context), targets.toArray(new String[0]), suspended);
            return true;
        } catch(SecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    private static Set<String> failSafePackages(Context context) {
        LinkedHashSet<String> out=new LinkedHashSet<>();
        Prefs prefs=new Prefs(context);
        out.addAll(prefs.gamePackages());
        for(AppRule r:prefs.getAppRules()) if(r.enabled) out.add(r.packageName);

        Collections.addAll(out,
                "com.instagram.android","com.instagram.lite",
                "com.facebook.katana","com.facebook.lite",
                "com.google.android.youtube",
                "com.zhiliaoapp.musically","com.ss.android.ugc.trill",
                "com.instagram.barcelona");

        // Browsers are an alternate route to the same social sites, so Fortress fail-safe also
        // suspends installed browser handlers until the administrator restores Bernard.
        try {
            Intent web=new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"));
            web.addCategory(Intent.CATEGORY_BROWSABLE);
            for(android.content.pm.ResolveInfo r:context.getPackageManager().queryIntentActivities(web,0)) {
                if(r.activityInfo!=null) out.add(r.activityInfo.packageName);
            }
        } catch(RuntimeException ignored) {}

        out.remove(context.getPackageName());
        out.remove("com.android.settings");
        out.remove("com.android.systemui");
        return out;
    }

    /** PIN-protected escape hatch for the administrator before deprovisioning/recovery. */
    public static boolean relax(Context context) {
        DevicePolicyManager dpm=(DevicePolicyManager)context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if(dpm==null || !dpm.isDeviceOwnerApp(context.getPackageName())) return false;
        ComponentName admin=admin(context);
        String pkg=context.getPackageName();
        try {
            dpm.setUninstallBlocked(admin,pkg,false);
            dpm.setPermittedAccessibilityServices(admin,null);
            if(Build.VERSION.SDK_INT>=30) {
                dpm.setUserControlDisabledPackages(admin,Collections.emptyList());
            }
            dpm.clearUserRestriction(admin,UserManager.DISALLOW_SAFE_BOOT);
            dpm.clearUserRestriction(admin,UserManager.DISALLOW_ADD_USER);
            dpm.clearUserRestriction(admin,UserManager.DISALLOW_REMOVE_USER);
            dpm.clearUserRestriction(admin,UserManager.DISALLOW_ADD_MANAGED_PROFILE);
            dpm.clearUserRestriction(admin,UserManager.DISALLOW_DEBUGGING_FEATURES);
            dpm.clearUserRestriction(admin,UserManager.DISALLOW_FACTORY_RESET);
            dpm.clearUserRestriction(admin,UserManager.DISALLOW_GRANT_ADMIN);
            dpm.clearUserRestriction(admin,UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
            dpm.clearUserRestriction(admin,UserManager.DISALLOW_INSTALL_APPS);
            dpm.clearUserRestriction(admin,UserManager.DISALLOW_UNINSTALL_APPS);
            dpm.clearUserRestriction(admin,UserManager.DISALLOW_APPS_CONTROL);
            if(Build.VERSION.SDK_INT>=35) dpm.clearUserRestriction(admin,UserManager.DISALLOW_ADD_PRIVATE_PROFILE);
            if(Build.VERSION.SDK_INT>=28) {
                dpm.clearUserRestriction(admin,UserManager.DISALLOW_USER_SWITCH);
                dpm.clearUserRestriction(admin,UserManager.DISALLOW_CONFIG_DATE_TIME);
            }
            return true;
        } catch(SecurityException | IllegalArgumentException e) {
            return false;
        }
    }
}
