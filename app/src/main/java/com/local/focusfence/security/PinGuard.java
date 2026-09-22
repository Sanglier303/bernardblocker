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
    public static final int NEW_PIN_LENGTH = 6;
    private static final String K_VERSION = "credential_version";
    private static final String K_LENGTH = "pin_length";
    private static volatile long pinChangeUntilElapsed;
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
        try {
            SharedPreferences p=prefs(context);
            byte[] salt=Base64.decode(p.getString(K_SALT,""),Base64.NO_WRAP);
            byte[] hash=Base64.decode(p.getString(K_HASH,""),Base64.NO_WRAP);
            int length=p.getInt(K_LENGTH,4),version=p.getInt(K_VERSION,0);
            boolean valid=salt.length>=16&&salt.length<=64&&hash.length==32
                    &&(length==4||length==NEW_PIN_LENGTH)&&version>=0&&version<=2
                    &&(version!=2||length==NEW_PIN_LENGTH);
            java.util.Arrays.fill(salt,(byte)0);java.util.Arrays.fill(hash,(byte)0);
            return valid;
        }catch(IllegalArgumentException|ClassCastException e){return false;}
    }
    /** No universal verifier is created or restored. Existing installations keep their credential. */
    public static boolean ensureConfigured(Context context) { return isConfigured(context); }
    public static boolean canEnroll(Context context) {
        if(!prefs(context).getAll().isEmpty())return false;
        java.util.Map<String,?> state=context.getSharedPreferences("focusfence",Context.MODE_PRIVATE).getAll();
        return !state.containsKey("pin_enrolled_v47")&&!Boolean.TRUE.equals(state.get("onboarding_v3"));
    }
    public static int pinLength(Context context) {
        try { int n=prefs(context).getInt(K_LENGTH,4);return n==NEW_PIN_LENGTH?n:4; }
        catch(ClassCastException e){return 4;}
    }
    public static boolean needsUpgrade(Context context) {
        if(!isConfigured(context))return false;
        try { return prefs(context).getInt(K_VERSION,0)<2; }catch(ClassCastException e){return true;}
    }
    public static boolean isPinChangeAuthorized() { return SystemClock.elapsedRealtime()<pinChangeUntilElapsed; }
    public static boolean beginPinChange() {
        if(!isAuthorized()&&!isPinChangeAuthorized())return false;
        authorizedUntilElapsed=0L;
        pinChangeUntilElapsed=SystemClock.elapsedRealtime()+AUTH_WINDOW_MS;
        clearSystemControlAuthorization();return true;
    }
    public static void clearPinChangeAuthorization(){pinChangeUntilElapsed=0L;}

    public static boolean setPin(Context context, char[] pin) {
        if (pin==null||pin.length!=NEW_PIN_LENGTH||!valid(pin)
                ||(!canEnroll(context)&&!isAuthorized()&&!isPinChangeAuthorized())) {
            wipe(pin);return false;
        }
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] hash = derive(pin, salt);
        wipe(pin);
        if (hash == null) return false;
        boolean ok = prefs(context).edit()
                .putString(K_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(K_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                .putInt(K_VERSION,2).putInt(K_LENGTH,NEW_PIN_LENGTH)
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
        if (ok) {
            context.getSharedPreferences("focusfence",Context.MODE_PRIVATE).edit().putBoolean("pin_enrolled_v47",true).commit();
            clearPinChangeAuthorization();authorize();
        }
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
        authorizedUntilElapsed = 0L;clearPinChangeAuthorization();
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
        if (lockoutRemainingMs(context) > 0 || !isConfigured(context) || !valid(pin) || pin.length!=pinLength(context)) {
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
            if(needsUpgrade(context)) {
                authorizedUntilElapsed=0L;clearSystemControlAuthorization();
                pinChangeUntilElapsed=SystemClock.elapsedRealtime()+AUTH_WINDOW_MS;
            }else authorize();
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
        if (pin == null || (pin.length != 4 && pin.length != NEW_PIN_LENGTH)) return false;
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
