package com.local.focusfence.util;

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
