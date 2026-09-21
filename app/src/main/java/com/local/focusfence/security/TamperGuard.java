package com.local.focusfence.security;

import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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

    // App-info labels used by AOSP/Pixel and common French translations. We intentionally
    // recognise the *surface* rather than only Bernard's title because Android 15's SPA app-info
    // page does not consistently expose the target package/app label to accessibility services.
    private static final String[] APP_INFO_MARKERS = {
            "force stop", "forcer l'arrêt", "forcer l’arret", "forcer l’arret", "arrêter",
            "uninstall", "désinstaller", "desinstaller",
            "storage & cache", "storage", "stockage et cache", "stockage",
            "permissions", "autorisations",
            "notifications", "open by default", "ouvrir par défaut",
            "mobile data & wi-fi", "données mobiles", "battery", "batterie"
    };

    private TamperGuard() {}

    public static boolean isSensitivePackage(String pkg) {
        return pkg != null && SENSITIVE_PACKAGES.contains(pkg);
    }

    /**
     * General Android settings remain usable. We gate:
     * 1) any sensitive system screen that explicitly references Bernard; and
     * 2) an app-info/control page (force stop/uninstall/storage/permissions), because those pages
     *    can terminate or neuter Bernard even when Android hides the target app name from the
     *    accessibility tree.
     */
    public static boolean isBernardControlScreen(String pkg, AccessibilityNodeInfo root,
                                                  CharSequence className) {
        if (!isSensitivePackage(pkg) || root == null) return false;

        if (visibleText(root, "Bernard Bloqueur") || visibleText(root, "com.local.focusfence")) {
            return true;
        }

        // Only treat Settings-style packages as generic app-info surfaces. Package installers and
        // Play Store still require an explicit Bernard label/package match to avoid hijacking
        // unrelated installs.
        if (!isSettingsPackage(pkg)) return false;

        String cls = className == null ? "" : className.toString().toLowerCase(Locale.ROOT);
        boolean classHint = cls.contains("appinfo")
                || cls.contains("installedappdetails")
                || cls.contains("applications.installed")
                || cls.contains("spaactivity");

        int markers = countVisibleMarkers(root);
        // Two independent app-info actions are a strong signal even on SPA/OEM pages. A specific
        // AppInfo/InstalledAppDetails class needs only one marker.
        return markers >= 2 || (classHint && markers >= 1);
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

    private static int countVisibleMarkers(AccessibilityNodeInfo root) {
        int count = 0;
        for (String marker : APP_INFO_MARKERS) {
            if (visibleText(root, marker)) {
                count++;
                if (count >= 2) return count;
            }
        }
        return count;
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
