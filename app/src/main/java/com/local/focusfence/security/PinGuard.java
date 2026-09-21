package com.local.focusfence.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Local administrator PIN gate.
 *
 * The PIN is deliberately NOT compiled into the APK. On first setup the administrator chooses
 * four digits; Bernard stores only a random per-install salt plus a PBKDF2-HMAC-SHA256 verifier in
 * the app's private storage. This avoids publishing the PIN in the open-source repository.
 */
public final class PinGuard {
    private static final String PREFS = "bernard_pin_v4";
    private static final String K_SALT = "salt";
    private static final String K_HASH = "hash";
    private static final int ITERATIONS = 180_000;
    private static final int KEY_BITS = 256;
    private static final long AUTH_WINDOW_MS = 180_000L;
    private static final long LOCKOUT_MS = 30_000L;

    private static volatile long authorizedUntilElapsed;
    private static volatile long lockedUntilElapsed;
    private static volatile int failures;

    private PinGuard() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isConfigured(Context context) {
        SharedPreferences p = prefs(context);
        return !p.getString(K_SALT, "").isEmpty() && !p.getString(K_HASH, "").isEmpty();
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
        failures = 0;
        lockedUntilElapsed = 0L;
        authorizedUntilElapsed = SystemClock.elapsedRealtime() + AUTH_WINDOW_MS;
    }

    public static void lockNow() {
        authorizedUntilElapsed = 0L;
    }

    public static long lockoutRemainingMs() {
        return Math.max(0L, lockedUntilElapsed - SystemClock.elapsedRealtime());
    }

    public static boolean verify(Context context, char[] pin) {
        if (lockoutRemainingMs() > 0 || !isConfigured(context) || !valid(pin)) {
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
            authorize();
            return true;
        }
        failures++;
        if (failures >= 5) {
            failures = 0;
            lockedUntilElapsed = SystemClock.elapsedRealtime() + LOCKOUT_MS;
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
