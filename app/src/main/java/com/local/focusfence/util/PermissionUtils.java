package com.local.focusfence.util;

import android.accessibilityservice.AccessibilityService;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

import com.local.focusfence.service.FocusAccessibilityService;

public final class PermissionUtils {
    private PermissionUtils() {}

    public static boolean hasUsageAccess(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return false;
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    public static boolean isAutomaticTimeEnabled(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), Settings.Global.AUTO_TIME, 1) != 0;
        } catch (SecurityException ignored) {
            return true;
        }
    }

    public static boolean isAutomaticTimeZoneEnabled(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), Settings.Global.AUTO_TIME_ZONE, 1) != 0;
        } catch (SecurityException ignored) {
            return true;
        }
    }

    public static boolean isAdbEnabled(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, 0) != 0;
        } catch (SecurityException ignored) {
            return false;
        }
    }

    public static boolean hasBernardAccessibilityShortcut(Context context) {
        ComponentName expected = new ComponentName(context, FocusAccessibilityService.class);
        String full = expected.flattenToString();
        String shortName = expected.flattenToShortString();
        String[] keys = {
                "accessibility_shortcut_target_service",
                "accessibility_button_targets",
                "accessibility_qs_targets"
        };
        for (String key : keys) {
            try {
                String value = Settings.Secure.getString(context.getContentResolver(), key);
                if (!TextUtils.isEmpty(value)
                        && (value.contains(full) || value.contains(shortName)
                        || value.contains(context.getPackageName()))) return true;
            } catch (SecurityException ignored) {
                // Some OEM/Android versions hide specific shortcut settings from third-party apps.
                // An unreadable optional key must not crash the accessibility service.
            }
        }
        return false;
    }

    public static boolean isAccessibilityEnabled(Context context) {
        ComponentName expected = new ComponentName(context, FocusAccessibilityService.class);
        String enabled = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            ComponentName cn = ComponentName.unflattenFromString(splitter.next());
            if (expected.equals(cn)) return true;
        }
        return false;
    }
}
