package com.local.focusfence.security;

import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Recognises system surfaces that can disable, force-stop, clear or uninstall Bernard. */
public final class TamperGuard {
    private static final Set<String> SENSITIVE_PACKAGES = new HashSet<>(Arrays.asList(
            "com.android.settings",
            "com.samsung.android.settings",
            "com.android.systemui",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.android.vending",
            "com.miui.securitycenter",
            "com.miui.packageinstaller",
            "com.coloros.safecenter",
            "com.oplus.safecenter",
            "com.oppo.safe",
            "com.huawei.systemmanager"
    ));

    private TamperGuard() {}

    public static boolean isSensitivePackage(String pkg) {
        return pkg != null && SENSITIVE_PACKAGES.contains(pkg);
    }

    /**
     * A general Android settings screen remains usable. We gate only a surface that visibly
     * references Bernard itself, which covers app-info, Accessibility, Usage Access, uninstall,
     * force-stop, clear-data and permission detail screens without hijacking unrelated settings.
     */
    public static boolean isBernardControlScreen(String pkg, AccessibilityNodeInfo root) {
        if (!isSensitivePackage(pkg) || root == null) return false;
        List<AccessibilityNodeInfo> byName = root.findAccessibilityNodeInfosByText("Bernard Bloqueur");
        if (byName != null) {
            for (AccessibilityNodeInfo n : byName) {
                if (n != null && n.isVisibleToUser()) return true;
            }
        }
        // Some OEM app-info screens expose the package name rather than the user-facing label.
        List<AccessibilityNodeInfo> byPackage = root.findAccessibilityNodeInfosByText("com.local.focusfence");
        if (byPackage != null) {
            for (AccessibilityNodeInfo n : byPackage) {
                if (n != null && n.isVisibleToUser()) return true;
            }
        }
        return false;
    }
}
