package com.local.focusfence.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Local administrator PIN gate.
 *
 * The PIN itself is never stored. Lockouts use Android's monotonic elapsed clock so changing the
 * wall clock cannot shorten them. A reboot restarts the full outstanding lockout duration.
 */
public final class PinGuard {
    private static final String PREFS = "bernard_pin_v4";
    private static final String K_SALT = "salt";
    private static final String K_HASH = "hash";
    private static final String K_FAILURES = "failures";
    private static final String K_LOCKOUT_LEVEL = "lockout_level";
    private static final String K_LOCKED_UNTIL_ELAPSED = "locked_until_elapsed";
    private static final String K_LOCK_DURATION = "lock_duration";
    private static final String K_LOCK_CREATED_ELAPSED = "lock_created_elapsed";
    private static final String K_LOCK_BOOT_COUNT = "lock_boot_count";
    private static final String K_LEGACY_LOCKED_UNTIL = "locked_until";

    public static final String CONTROL_NONE = "";
    public static final String CONTROL_ACCESSIBILITY = "accessibility";
    public static final String CONTROL_USAGE = "usage";
    public static final String CONTROL_DEVICE_ADMIN = "device_admin";
    public static final String CONTROL_UPDATE = "update";
    public static final String CONTROL_SYSTEM = "system";

    private static final int ITERATIONS = 180_000;
    private static final int KEY_BITS = 256;
    // Fixed owner verifier for PIN 1109. The clear-text PIN is never stored.
    private static final String BOOTSTRAP_SALT = "H3mSaWi0vLJt+uGs9GaCOw==";
    private static final String BOOTSTRAP_HASH = "lhU3btBI4H5/PpUL4m2o2n5cXXaw9MEPNd9yanniiFI=";
    private static final long AUTH_WINDOW_MS = 60_000L;
    private static final long SYSTEM_CONTROL_WINDOW_MS = 45_000L;
    private static final long[] LOCKOUTS_MS = {
            30_000L,
            2 * 60_000L,
            10 * 60_000L,
            60 * 60_000L,
            6 * 60 * 60_000L
    };

    private static volatile long authorizedUntilElapsed;
    private static volatile long systemControlUntilElapsed;
    private static volatile String systemControlScope = CONTROL_NONE;
    private static volatile String systemControlPackage = "";

    private PinGuard() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isConfigured(Context context) {
        SharedPreferences p = prefs(context);
        return !p.getString(K_SALT, "").isEmpty() && !p.getString(K_HASH, "").isEmpty();
    }

    /**
     * Personal Bernard build: restore the owner's fixed verifier after a data reset.
     * This prevents first-launch PIN takeover after clearing application data.
     */
    public static boolean ensureConfigured(Context context) {
        if (isConfigured(context)) return true;
        return prefs(context).edit()
                .putString(K_SALT, BOOTSTRAP_SALT)
                .putString(K_HASH, BOOTSTRAP_HASH)
                .putInt(K_FAILURES, 0)
                .putInt(K_LOCKOUT_LEVEL, 0)
                .remove(K_LOCKED_UNTIL_ELAPSED)
                .remove(K_LOCK_DURATION)
                .remove(K_LOCK_CREATED_ELAPSED)
                .remove(K_LOCK_BOOT_COUNT)
                .remove(K_LEGACY_LOCKED_UNTIL)
                .commit();
    }

    public static boolean setPin(Context context, char[] pin) {
        if (!valid(pin)) {
            wipe(pin);
            return false;
        }
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] hash = derive(pin, salt);
        wipe(pin);
        if (hash == null) return false;
        boolean ok = prefs(context).edit()
                .putString(K_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(K_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                .putInt(K_FAILURES, 0)
                .putInt(K_LOCKOUT_LEVEL, 0)
                .remove(K_LOCKED_UNTIL_ELAPSED)
                .remove(K_LOCK_DURATION)
                .remove(K_LOCK_CREATED_ELAPSED)
                .remove(K_LOCK_BOOT_COUNT)
                .remove(K_LEGACY_LOCKED_UNTIL)
                .commit();
        java.util.Arrays.fill(salt, (byte) 0);
        java.util.Arrays.fill(hash, (byte) 0);
        if (ok) authorize();
        return ok;
    }

    public static boolean isAuthorized() {
        return SystemClock.elapsedRealtime() < authorizedUntilElapsed;
    }

    /** Used by instrumentation and after a successful verification/setup. */
    public static void authorize() {
        authorizedUntilElapsed = SystemClock.elapsedRealtime() + AUTH_WINDOW_MS;
    }

    public static void lockNow() {
        authorizedUntilElapsed = 0L;
    }

    /**
     * Short-lived authorization for one Android system-control flow. It is scoped so authorizing
     * Usage Access cannot silently authorize an unrelated package-installer or app-info screen.
     */
    public static void authorizeSystemControl(String scope, String packageName) {
        systemControlScope = scope == null ? CONTROL_NONE : scope;
        systemControlPackage = packageName == null ? "" : packageName;
        systemControlUntilElapsed = SystemClock.elapsedRealtime() + SYSTEM_CONTROL_WINDOW_MS;
    }

    public static boolean isSystemControlAuthorized() {
        return SystemClock.elapsedRealtime() < systemControlUntilElapsed;
    }

    public static String systemControlScope() {
        return isSystemControlAuthorized() ? systemControlScope : CONTROL_NONE;
    }

    public static String systemControlPackage() {
        return isSystemControlAuthorized() ? systemControlPackage : "";
    }

    public static void clearSystemControlAuthorization() {
        systemControlUntilElapsed = 0L;
        systemControlScope = CONTROL_NONE;
        systemControlPackage = "";
    }

    private static int bootCount(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        } catch (Exception ignored) {
            return -1;
        }
    }

    /**
     * Returns the remaining lockout using elapsedRealtime. If the phone rebooted during a lockout,
     * Bernard conservatively restarts the full lockout duration instead of trusting wall-clock time.
     */
    public static long lockoutRemainingMs(Context context) {
        SharedPreferences p = prefs(context);
        long duration = p.getLong(K_LOCK_DURATION, 0L);
        long until = p.getLong(K_LOCKED_UNTIL_ELAPSED, 0L);
        long created = p.getLong(K_LOCK_CREATED_ELAPSED, 0L);
        if (duration <= 0L || until <= 0L) {
            // Remove legacy wall-clock lockouts rather than letting a clock change shorten them.
            if (p.contains(K_LEGACY_LOCKED_UNTIL)) {
                p.edit().remove(K_LEGACY_LOCKED_UNTIL).apply();
            }
            return 0L;
        }

        long now = SystemClock.elapsedRealtime();
        int storedBoot = p.getInt(K_LOCK_BOOT_COUNT, -2);
        int currentBoot = bootCount(context);
        boolean rebooted = storedBoot >= 0 && currentBoot >= 0
                ? storedBoot != currentBoot
                : created > 0L && now + 5_000L < created;

        if (rebooted) {
            until = now + duration;
            p.edit()
                    .putLong(K_LOCKED_UNTIL_ELAPSED, until)
                    .putLong(K_LOCK_CREATED_ELAPSED, now)
                    .putInt(K_LOCK_BOOT_COUNT, currentBoot)
                    .apply();
        }

        long remaining = Math.max(0L, until - now);
        if (remaining == 0L) {
            p.edit()
                    .remove(K_LOCKED_UNTIL_ELAPSED)
                    .remove(K_LOCK_DURATION)
                    .remove(K_LOCK_CREATED_ELAPSED)
                    .remove(K_LOCK_BOOT_COUNT)
                    .apply();
        }
        return remaining;
    }

    public static boolean verify(Context context, char[] pin) {
        if (lockoutRemainingMs(context) > 0 || !isConfigured(context) || !valid(pin)) {
            wipe(pin);
            return false;
        }
        SharedPreferences p = prefs(context);
        byte[] salt;
        byte[] expected;
        try {
            salt = Base64.decode(p.getString(K_SALT, ""), Base64.NO_WRAP);
            expected = Base64.decode(p.getString(K_HASH, ""), Base64.NO_WRAP);
        } catch (IllegalArgumentException e) {
            wipe(pin);
            return false;
        }
        byte[] actual = derive(pin, salt);
        wipe(pin);
        boolean ok = actual != null && MessageDigest.isEqual(expected, actual);
        java.util.Arrays.fill(salt, (byte) 0);
        java.util.Arrays.fill(expected, (byte) 0);
        if (actual != null) java.util.Arrays.fill(actual, (byte) 0);
        if (ok) {
            prefs(context).edit()
                    .putInt(K_FAILURES, 0)
                    .putInt(K_LOCKOUT_LEVEL, 0)
                    .remove(K_LOCKED_UNTIL_ELAPSED)
                    .remove(K_LOCK_DURATION)
                    .remove(K_LOCK_CREATED_ELAPSED)
                    .remove(K_LOCK_BOOT_COUNT)
                    .remove(K_LEGACY_LOCKED_UNTIL)
                    .apply();
            authorize();
            return true;
        }

        SharedPreferences p2 = prefs(context);
        int failures = p2.getInt(K_FAILURES, 0) + 1;
        if (failures >= 5) {
            int level = Math.max(0, p2.getInt(K_LOCKOUT_LEVEL, 0));
            long delay = LOCKOUTS_MS[Math.min(level, LOCKOUTS_MS.length - 1)];
            long now = SystemClock.elapsedRealtime();
            p2.edit()
                    .putInt(K_FAILURES, 0)
                    .putInt(K_LOCKOUT_LEVEL, Math.min(level + 1, LOCKOUTS_MS.length - 1))
                    .putLong(K_LOCKED_UNTIL_ELAPSED, now + delay)
                    .putLong(K_LOCK_DURATION, delay)
                    .putLong(K_LOCK_CREATED_ELAPSED, now)
                    .putInt(K_LOCK_BOOT_COUNT, bootCount(context))
                    .remove(K_LEGACY_LOCKED_UNTIL)
                    .apply();
        } else {
            p2.edit().putInt(K_FAILURES, failures).apply();
        }
        return false;
    }

    private static boolean valid(char[] pin) {
        if (pin == null || pin.length != 4) return false;
        for (char c : pin) if (c < '0' || c > '9') return false;
        return true;
    }

    private static byte[] derive(char[] pin, byte[] salt) {
        PBEKeySpec spec = null;
        try {
            spec = new PBEKeySpec(pin, salt, ITERATIONS, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception ignored) {
            return null;
        } finally {
            if (spec != null) spec.clearPassword();
        }
    }

    private static void wipe(char[] value) {
        if (value != null) java.util.Arrays.fill(value, '\0');
    }
}
