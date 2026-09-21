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
     * Locked mode gates the Android/OEM Settings app as a whole. A selective app-info-only
     * strategy proved brittle on Android 15 SPA Settings and left several equivalent routes
     * (Accessibility, Usage Access, Date & time, battery controls) available.
     *
     * Package installers and Play Store are gated only when they visibly reference Bernard, so
     * unrelated app installs/updates are not hijacked.
     */
    public static boolean isBernardControlScreen(String pkg, AccessibilityNodeInfo root,
                                                  CharSequence className) {
        if (!isSensitivePackage(pkg) || root == null) return false;

        if (isSettingsPackage(pkg)) return true;

        return visibleText(root, "Bernard Bloqueur")
                || visibleText(root, "com.local.focusfence");
    }

    private static boolean isSettingsPackage(String pkg) {
        return "com.android.settings".equals(pkg)
                || "com.samsung.android.settings".equals(pkg)
                || "com.miui.securitycenter".equals(pkg)
                || "com.coloros.safecenter".equals(pkg)
                || "com.oplus.safecenter".equals(pkg)
                || "com.oppo.safe".equals(pkg)
                || "com.huawei.systemmanager".equals(pkg);
    }

    private static boolean visibleText(AccessibilityNodeInfo root, String needle) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(needle);
        if (nodes == null) return false;
        for (AccessibilityNodeInfo n : nodes) {
            if (n != null && n.isVisibleToUser()) return true;
        }
        return false;
    }
}
