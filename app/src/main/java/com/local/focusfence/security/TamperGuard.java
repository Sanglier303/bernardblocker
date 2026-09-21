package com.local.focusfence.security;

import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Recognises system surfaces that can disable, force-stop, clear or route around Bernard. */
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

    private static final Set<String> USER_SWITCH_IDS = new HashSet<>(Arrays.asList(
            "multi_user_switch",
            "user_switcher_root",
            "user_switcher_grid",
            "user_switcher_fullscreen",
            "user_switcher_container",
            "qs_user_switch_dialog",
            "user_switcher_list"
    ));
    private static final Set<String> PRIVATE_SPACE_IDS = new HashSet<>(Arrays.asList(
            "ps_header_layout",
            "ps_lock_unlock_button",
            "ps_settings_button",
            "private_space_header",
            "private_space_container",
            "private_space_install_app_button"
    ));

    private TamperGuard() {}

    public static boolean isSensitivePackage(String pkg) {
        return pkg != null && SENSITIVE_PACKAGES.contains(pkg);
    }

    public static boolean requiresImmediatePin(String pkg) {
        return isSettingsPackage(pkg)
                || isPackageInstaller(pkg)
                || "com.google.android.apps.wellbeing".equals(pkg)
                || "com.samsung.android.lool".equals(pkg)
                || "com.samsung.android.sm".equals(pkg)
                || "com.miui.powerkeeper".equals(pkg)
                || "com.coloros.phonemanager".equals(pkg)
                || "com.oneplus.security".equals(pkg);
    }

    /**
     * The PIN grants only the requested system flow. Opening Usage Access does not create a
     * temporary wildcard that can be reused to jump to app-info / force-stop / uninstall.
     */
    public static boolean isAuthorizedControlScreen(String scope, String authorizedPackage,
                                                    String pkg, AccessibilityNodeInfo root,
                                                    CharSequence className) {
        if (scope == null || scope.isEmpty() || pkg == null) return false;
        if (authorizedPackage != null && !authorizedPackage.isEmpty()
                && !authorizedPackage.equals(pkg)) return false;

        if (PinGuard.CONTROL_SYSTEM.equals(scope)) {
            return authorizedPackage != null && !authorizedPackage.isEmpty()
                    && authorizedPackage.equals(pkg);
        }
        if (PinGuard.CONTROL_UPDATE.equals(scope)) {
            if (isSettingsPackage(pkg)) {
                String updateClass = className == null ? "" : className.toString().toLowerCase(Locale.ROOT);
                return updateClass.contains("manageexternal")
                        || updateClass.contains("externalsource")
                        || visibleTextAny(root,
                        "Install unknown apps", "Installer applis inconnues",
                        "Allow from this source", "Autoriser depuis cette source",
                        "Bernard Bloqueur");
            }
            if (isPackageInstaller(pkg)
                    || "com.google.android.permissioncontroller".equals(pkg)
                    || "com.android.permissioncontroller".equals(pkg)) {
                return visibleTextAny(root,"Bernard Bloqueur","com.local.focusfence")
                        || (className != null && className.toString().toLowerCase(Locale.ROOT).contains("packageinstaller"));
            }
            return false;
        }
        if (!isSettingsPackage(pkg)) return false;

        String cls = className == null ? "" : className.toString().toLowerCase(Locale.ROOT);
        if (PinGuard.CONTROL_ACCESSIBILITY.equals(scope)) {
            return cls.contains("accessibility")
                    || visibleTextAny(root, "Accessibility", "Accessibilité",
                    "Installed apps", "Applications installées");
        }
        if (PinGuard.CONTROL_USAGE.equals(scope)) {
            return cls.contains("usageaccess") || cls.contains("specialaccess")
                    || visibleTextAny(root, "Usage access", "Données d’utilisation",
                    "Données d'utilisation", "Accès aux données d’utilisation",
                    "Accès aux données d'utilisation");
        }
        if (PinGuard.CONTROL_DEVICE_ADMIN.equals(scope)) {
            boolean deviceAdminSurface=cls.contains("deviceadmin")
                    || cls.contains("devicepolicy")
                    || visibleTextAny(root,
                    "Device admin", "Device admin apps", "Activate this device admin app",
                    "Administrateur de l’appareil", "Administrateur de l'appareil",
                    "Applications d'administration de l'appareil",
                    "Activer cet administrateur", "Activer cet administrateur de l’appareil",
                    "Activer cet administrateur de l'appareil");
            if(isSettingsPackage(pkg)) return deviceAdminSurface;
            // Pixel/Google builds may hand the confirmation to PermissionController. The grant
            // remains limited to the device-admin scope and requires both Bernard and an
            // administration/activation signal, so it is not a wildcard permission grant.
            if(isPermissionController(pkg)) {
                return deviceAdminSurface
                        || (visibleTextAny(root,"Bernard Bloqueur","com.local.focusfence")
                        && visibleTextAny(root,"Activate","Activer","Device admin","Administrateur"));
            }
            return false;
        }
        return false;
    }

    /**
     * Locked mode gates Settings as a whole. Package installers / stores are gated when they
     * reference Bernard, while OEM control panels are treated as sensitive control surfaces.
     */
    public static boolean isBernardControlScreen(String pkg, AccessibilityNodeInfo root,
                                                  CharSequence className) {
        if (!isSensitivePackage(pkg) || root == null) return false;
        if (isSettingsPackage(pkg) || isPackageInstaller(pkg)) return true;
        return visibleText(root, "Bernard Bloqueur")
                || visibleText(root, "com.local.focusfence");
    }

    /**
     * Android 15 Private Space runs apps in another profile where this accessibility service may
     * not be present. Gate the launcher entry point before that profile becomes an escape hatch.
     */
    public static boolean isPrivateSpaceSurface(String pkg, AccessibilityNodeInfo root) {
        if (root == null || pkg == null) return false;
        String lowerPkg=pkg.toLowerCase(Locale.ROOT);
        boolean launcherLike=lowerPkg.contains("launcher")
                || lowerPkg.contains("nexuslauncher")
                || lowerPkg.equals("com.sec.android.app.launcher")
                || lowerPkg.equals("com.miui.home")
                || lowerPkg.contains("oplus.launcher")
                || lowerPkg.contains("coloros.launcher");
        if(!launcherLike) return false;
        if (containsAnyId(root, PRIVATE_SPACE_IDS, 1400)) return true;
        // OEM launchers sometimes strip resource names. Require two independent textual hints to
        // avoid hijacking arbitrary apps that merely mention the word "private".
        return visibleTextAny(root, "Private Space", "Espace privé", "Espace prive")
                && visibleTextAny(root, "Install", "Installer", "Lock", "Verrouiller", "Settings", "Paramètres");
    }

    /** Quick Settings user switching moves the user into a profile where Bernard may not exist. */
    public static boolean isSystemUserSwitcher(String pkg, AccessibilityNodeInfo root) {
        if (!"com.android.systemui".equals(pkg) || root == null) return false;
        return containsAnyId(root, USER_SWITCH_IDS, 1200);
    }

    private static boolean containsAnyId(AccessibilityNodeInfo root, Set<String> ids, int max) {
        java.util.ArrayDeque<AccessibilityNodeInfo> q = new java.util.ArrayDeque<>();
        q.add(root);
        int visited = 0;
        while (!q.isEmpty() && visited++ < max) {
            AccessibilityNodeInfo n = q.removeFirst();
            String id = n.getViewIdResourceName();
            if (id != null) {
                int slash = id.lastIndexOf('/');
                String suffix = slash >= 0 ? id.substring(slash + 1) : id;
                if (ids.contains(suffix)) return true;
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.addLast(child);
            }
        }
        return false;
    }

    private static boolean isPackageInstaller(String pkg) {
        return "com.google.android.packageinstaller".equals(pkg)
                || "com.android.packageinstaller".equals(pkg)
                || "com.samsung.android.packageinstaller".equals(pkg)
                || "com.miui.packageinstaller".equals(pkg);
    }

    private static boolean isPermissionController(String pkg) {
        return "com.google.android.permissioncontroller".equals(pkg)
                || "com.android.permissioncontroller".equals(pkg);
    }

    public static boolean isSettingsPackage(String pkg) {
        return "com.android.settings".equals(pkg)
                || "com.samsung.android.settings".equals(pkg)
                || "com.miui.securitycenter".equals(pkg)
                || "com.coloros.safecenter".equals(pkg)
                || "com.oplus.safecenter".equals(pkg)
                || "com.oppo.safe".equals(pkg)
                || "com.huawei.systemmanager".equals(pkg);
    }

    private static boolean visibleTextAny(AccessibilityNodeInfo root, String... needles) {
        if (root == null) return false;
        for (String needle : needles) if (visibleText(root, needle)) return true;
        return false;
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
