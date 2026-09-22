package com.local.focusfence.core;

import java.text.Normalizer;
import java.util.Locale;

/** Pure decisions: an event is not evidence that its source is still in the foreground. */
public final class SystemScreenPolicy {
    private SystemScreenPolicy() {}

    public static boolean matchesWindow(String eventPackage, int eventWindow,
                                        String activePackage, int activeWindow) {
        return eventPackage != null && eventPackage.equals(activePackage)
                && eventWindow >= 0 && eventWindow == activeWindow;
    }

    public static boolean isActivityClass(String pkg, String cls) {
        // Android panes, Toasts and widget events must not replace a known Activity class.
        return pkg != null && cls != null && cls.startsWith(pkg + ".")
                && (cls.endsWith("Activity") || cls.endsWith(".DeviceAdminAdd")
                || cls.endsWith(".Settings") || cls.endsWith(".SubSettings") || cls.endsWith(".DeviceAdminSettings"));
    }

    public static String normalized(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('’', '\'')
                .toLowerCase(Locale.ROOT);
    }

    /** Recognize the requested flow, never the app name alone or generic 'special access'. */
    public static boolean matchesFlow(String scope, String className, String screenText) {
        String cls = normalized(className), text = normalized(screenText);
        // App-info can list "Install unknown apps" as a sub-option. That text must not
        // turn an update-only grant into access to force-stop/clear-data/uninstall.
        if (cls.contains("installedappdetails") || cls.contains("applicationdetails")
                || cls.contains("appinfo")) return false;
        for (String line : text.split("\n")) {
            String label = line.trim();
            if (label.equals("force stop") || label.equals("forcer l'arret")
                    || label.equals("clear storage") || label.equals("effacer les donnees")
                    || label.equals("clear data")) return false;
        }
        if ("device_admin".equals(scope)) {
            return cls.endsWith(".deviceadminadd") || cls.endsWith("$deviceadminsettingsactivity")
                    || cls.endsWith(".deviceadminsettings")
                    || text.contains("device admin") || text.contains("administrateur de l'appareil")
                    || text.contains("administration de l'appareil")
                    || text.contains("activer cet administrateur");
        }
        if ("accessibility".equals(scope)) {
            return cls.contains("accessibility") || text.contains("accessibility")
                    || text.contains("accessibilite");
        }
        if ("usage".equals(scope)) {
            return cls.contains("usageaccess") || text.contains("usage access")
                    || text.contains("donnees d'utilisation") || text.contains("usage data access");
        }
        if ("update".equals(scope)) {
            return cls.contains("manageexternalsources") || cls.contains("externalsourcedetails")
                    || text.contains("install unknown apps") || text.contains("installer applis inconnues")
                    || text.contains("installation d'applis inconnues")
                    || text.contains("allow from this source") || text.contains("autoriser depuis cette source");
        }
        return false;
    }
}
