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
            "com.google.android.apps.wellbeing",
            "com.samsung.android.lool",
            "com.samsung.android.sm",
            "com.miui.securitycenter",
            "com.miui.powerkeeper",
            "com.miui.packageinstaller",
            "com.coloros.safecenter",
            "com.oplus.safecenter",
            "com.oppo.safe",
            "com.coloros.phonemanager",
            "com.oneplus.security",
            "com.huawei.systemmanager"
    ));

    private TamperGuard() {}

    public static boolean isSensitivePackage(String pkg) {
        return pkg != null && SENSITIVE_PACKAGES.contains(pkg);
    }

    /**
     * Packages whose control surfaces are dangerous enough that Bernard should open the PIN gate
     * directly from the accessibility event, without waiting for a readable node tree.
     */
    public static boolean requiresImmediatePin(String pkg) {
        return isSettingsPackage(pkg)
                || isPackageInstaller(pkg)
                || "com.google.android.apps.wellbeing".equals(pkg)
                || "com.samsung.android.lool".equals(pkg)
                || "com.samsung.android.sm".equals(pkg)
                || "com.miui.powerkeeper".equals(pkg)
                || "com.coloros.phonemanager".equals(pkg)
                || "com.oneplus.security".equals(pkg)
                || "com.android.systemui".equals(pkg);
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

        if (isSettingsPackage(pkg) || isPackageInstaller(pkg)) return true;

        return visibleText(root, "Bernard Bloqueur")
                || visibleText(root, "com.local.focusfence");
    }

    private static boolean isPackageInstaller(String pkg) {
        return "com.google.android.packageinstaller".equals(pkg)
                || "com.android.packageinstaller".equals(pkg)
                || "com.samsung.android.packageinstaller".equals(pkg)
                || "com.miui.packageinstaller".equals(pkg);
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
