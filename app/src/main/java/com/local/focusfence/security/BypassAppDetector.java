package com.local.focusfence.security;

import android.content.Context;
import android.content.pm.PackageManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Heuristics for common app-cloning / parallel-profile containers used to escape package rules. */
public final class BypassAppDetector {
    private static final Set<String> KNOWN_PACKAGES = new HashSet<>(Arrays.asList(
            "com.lbe.parallel.intl",
            "com.lbe.parallel.intl.arm64",
            "com.excelliance.multiaccounts",
            "com.polestar.super.clone",
            "com.oasisfeng.island",
            "net.typeblog.shelter",
            "com.samsung.knox.securefolder"
    ));

    private static final String[] LABEL_MARKERS = {
            "parallel space",
            "dual space",
            "multiple accounts",
            "multi accounts",
            "2accounts",
            "super clone",
            "app cloner",
            "clone app",
            "secure folder",
            "dossier sécurisé",
            "island",
            "shelter"
    };

    private BypassAppDetector() {}

    public static boolean isKnownContainer(Context context, String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        if (KNOWN_PACKAGES.contains(pkg)) return true;
        try {
            PackageManager pm = context.getPackageManager();
            String label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
            return looksLikeBypassLabel(label);
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            return false;
        }
    }

    static boolean looksLikeBypassLabel(String label) {
        if (label == null) return false;
        String lower = label.trim().toLowerCase(Locale.ROOT);
        for (String marker : LABEL_MARKERS) if (lower.contains(marker)) return true;
        return false;
    }
}
