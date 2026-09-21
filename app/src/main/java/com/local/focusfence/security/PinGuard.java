package com.local.focusfence.security;

import android.os.SystemClock;
import android.util.Base64;

import java.security.MessageDigest;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Short-lived local authorization for Bernard's protected settings.
 *
 * The PIN itself is never stored in preferences or source. The verifier contains only a
 * PBKDF2-HMAC-SHA256 digest. Authorization deliberately lives in process memory so killing or
 * restarting Bernard locks the settings again instead of creating a persistent bypass token.
 */
public final class PinGuard {
    private static final byte[] SALT = Base64.decode("YTpfAEn2FSjEZTV7NRCywg==", Base64.DEFAULT);
    private static final byte[] EXPECTED = Base64.decode("ITxcCJDwNsvTe74zSb6WcgXFxDZ0ls8ArNdvEqRoQdU=", Base64.DEFAULT);
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final long AUTH_WINDOW_MS = 180_000L;
    private static final long LOCKOUT_MS = 30_000L;

    private static volatile long authorizedUntilElapsed;
    private static volatile long lockedUntilElapsed;
    private static volatile int failures;

    private PinGuard() {}

    public static boolean isAuthorized() {
        return SystemClock.elapsedRealtime() < authorizedUntilElapsed;
    }

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

    public static boolean verify(char[] pin) {
        if (lockoutRemainingMs() > 0) return false;
        byte[] actual = null;
        try {
            PBEKeySpec spec = new PBEKeySpec(pin, SALT, ITERATIONS, KEY_BITS);
            actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            spec.clearPassword();
            boolean ok = MessageDigest.isEqual(EXPECTED, actual);
            if (ok) {
                authorize();
                return true;
            }
        } catch (Exception ignored) {
            // Fail closed. A missing crypto provider must never unlock protected settings.
        } finally {
            java.util.Arrays.fill(pin, '\0');
            if (actual != null) java.util.Arrays.fill(actual, (byte) 0);
        }
        failures++;
        if (failures >= 5) {
            failures = 0;
            lockedUntilElapsed = SystemClock.elapsedRealtime() + LOCKOUT_MS;
        }
        return false;
    }
}
