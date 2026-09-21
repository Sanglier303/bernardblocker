package com.local.focusfence.security;

import android.view.accessibility.AccessibilityNodeInfo;
import com.local.focusfence.core.SystemScreenPolicy;
import com.local.focusfence.util.NodeWalker;

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
        boolean adminBridge=PinGuard.CONTROL_DEVICE_ADMIN.equals(scope)
                && isSettingsPackage(authorizedPackage) && isPermissionController(pkg);
        if (authorizedPackage != null && !authorizedPackage.isEmpty()
                && !authorizedPackage.equals(pkg) && !adminBridge) return false;
        if (root != null && root.getPackageName() != null
                && !pkg.contentEquals(root.getPackageName())) return false;
        if (PinGuard.CONTROL_SYSTEM.equals(scope)) {
            // Only a freshly verified owner PIN can grant this explicit package-level access.
            return authorizedPackage != null && authorizedPackage.equals(pkg);
        }
        boolean settings = isSettingsPackage(pkg);
        boolean controller = isPermissionController(pkg);
        if (!settings && !controller && !isPackageInstaller(pkg)) return false;
        StringBuilder text = new StringBuilder();
        NodeWalker.visit(root, 900, n -> {
            if (n.isVisibleToUser() && text.length() < 24000) {
                if (n.getText() != null) text.append(n.getText()).append('\n');
                if (n.getContentDescription() != null) text.append(n.getContentDescription()).append('\n');
            }
        });
        String cls = className == null ? "" : className.toString();
        boolean flow = SystemScreenPolicy.matchesFlow(scope, cls, text.toString());
        if (PinGuard.CONTROL_DEVICE_ADMIN.equals(scope)) {
            // Must run BEFORE rejecting non-Settings packages (unreachable in 0.4.3).
            return (settings || controller) && flow;
        }
        if (PinGuard.CONTROL_UPDATE.equals(scope)) {
            if (settings) return flow;
            // A Settings app-info page containing 'Bernard' is NOT an install confirmation.
            String lower = cls.toLowerCase(Locale.ROOT);
            return (controller || isPackageInstaller(pkg))
                    && (lower.endsWith(".packageinstalleractivity")
                    || lower.endsWith(".installstart") || lower.endsWith(".installstaging")
                    || lower.endsWith(".installinstalling") || lower.endsWith(".installsuccess")
                    || lower.endsWith(".installfailed")
                    || (text.toString().contains("Bernard Bloqueur")
                    && (SystemScreenPolicy.normalized(text.toString()).contains("mettre a jour")
                    || SystemScreenPolicy.normalized(text.toString()).contains("update this app"))));
        }
        return settings && flow;
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
        return NodeWalker.any(root, max, n -> {
            if (!n.isVisibleToUser()) return false;
            String id = n.getViewIdResourceName();
            if (id == null) return false;
            int slash = id.lastIndexOf('/');
            return ids.contains(slash >= 0 ? id.substring(slash + 1) : id);
        });
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
        return NodeWalker.any(root, 1200, n -> n.isVisibleToUser()
                && ((n.getText() != null && n.getText().toString().contains(needle))
                || (n.getContentDescription() != null && n.getContentDescription().toString().contains(needle))));
    }
}
