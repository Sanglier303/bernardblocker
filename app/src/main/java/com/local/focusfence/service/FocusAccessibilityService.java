package com.local.focusfence.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import com.local.focusfence.detector.ShortSurfaceDetector;
import com.local.focusfence.model.AppRule;
import com.local.focusfence.storage.Prefs;
import com.local.focusfence.ui.BlockActivity;
import com.local.focusfence.util.PermissionUtils;
import com.local.focusfence.util.TimeUtils;
import com.local.focusfence.util.UsageUtils;

import java.util.Set;

public final class FocusAccessibilityService extends AccessibilityService {
    private Prefs prefs;
    private final ShortSurfaceDetector detector = new ShortSurfaceDetector();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String currentPackage;
    private CharSequence lastWindowClassName;
    private boolean shortTracking;
    private String shortPackage;
    private long lastShortTick;
    private long lastBlockAt;
    private String lastBlockedPackage;

    private final Runnable shortTicker = new Runnable() {
        @Override public void run() {
            if (!shortTracking) return;
            AccessibilityNodeInfo root = getRootInActiveWindow();
            String pkg = root == null || root.getPackageName() == null ? null : root.getPackageName().toString();
            if (pkg == null || !pkg.equals(shortPackage)) {
                stopShortTracking();
                return;
            }
            ShortSurfaceDetector.Surface surface = detector.detect(
                    pkg, root, lastWindowClassName, prefs.includeStories(), prefs.diagnosticMode());
            if (surface == null) {
                stopShortTracking();
                return;
            }

            long now = System.currentTimeMillis();
            long delta = Math.max(0L, Math.min(2000L, now - lastShortTick));
            lastShortTick = now;
            long usage = prefs.addShortUsage(delta);
            if (shouldBlockShortContent(usage)) {
                blockShortSurface();
                return;
            }
            handler.postDelayed(this, 1000L);
        }
    };

    private final Runnable appTicker = new Runnable() {
        @Override public void run() {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            String pkg = root == null || root.getPackageName() == null ? null : root.getPackageName().toString();
            if (pkg == null || pkg.equals(getPackageName())) return;
            currentPackage = pkg;
            String reason = blockingReasonForPackage(pkg);
            if (reason != null) {
                showBlock(pkg, reason);
                return;
            }
            if (isPackageControlled(pkg)) handler.postDelayed(this, 5000L);
        }
    };

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        prefs = new Prefs(this);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (prefs == null) prefs = new Prefs(this);
        if (event == null) return;
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && event.getClassName() != null) {
            lastWindowClassName = event.getClassName();
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        String pkg = root != null && root.getPackageName() != null
                ? root.getPackageName().toString()
                : event.getPackageName() == null ? null : event.getPackageName().toString();
        if (pkg == null) return;

        currentPackage = pkg;
        if (pkg.equals(getPackageName())) {
            stopShortTracking();
            handler.removeCallbacks(appTicker);
            return;
        }

        String reason = blockingReasonForPackage(pkg);
        if (reason != null) {
            stopShortTracking();
            showBlock(pkg, reason);
            return;
        }

        handler.removeCallbacks(appTicker);
        if (isPackageControlled(pkg)) handler.postDelayed(appTicker, 5000L);

        if (!prefs.shortEnabled()) {
            stopShortTracking();
            return;
        }

        if (root != null && detector.isSupported(pkg)) {
            ShortSurfaceDetector.Surface surface = detector.detect(
                    pkg, root, lastWindowClassName, prefs.includeStories(), prefs.diagnosticMode());
            if (surface != null) {
                long usage = prefs.shortUsageMs();
                if (shouldBlockShortContent(usage)) {
                    stopShortTracking();
                    blockShortSurface();
                } else {
                    startShortTracking(pkg);
                }
            } else if (pkg.equals(shortPackage)) {
                stopShortTracking();
            }
        } else if (!pkg.equals(shortPackage)) {
            stopShortTracking();
        }
    }

    private boolean isPackageControlled(String pkg) {
        AppRule r = prefs.getAppRule(pkg);
        if (r != null && r.enabled) return true;
        return prefs.gamesEnabled() && prefs.gamePackages().contains(pkg);
    }

    private String blockingReasonForPackage(String pkg) {
        int now = TimeUtils.nowMinute();

        if (prefs.gamesEnabled()) {
            Set<String> games = prefs.gamePackages();
            if (games.contains(pkg)) {
                if (!TimeUtils.isInsideAllowedWindow(now, prefs.gamesStartMinute(), prefs.gamesEndMinute())) {
                    return "Ce jeu n'est pas autorisé à cette heure.";
                }
                int limit = prefs.gamesLimitMinutes();
                if (limit > 0 && PermissionUtils.hasUsageAccess(this)) {
                    long used = UsageUtils.todayUsageMs(this, games);
                    if (used >= limit * 60_000L) {
                        return "Le quota commun de jeux de " + limit + " min est atteint pour aujourd'hui.";
                    }
                }
            }
        }

        AppRule rule = prefs.getAppRule(pkg);
        if (rule != null && rule.enabled) {
            if (!TimeUtils.isInsideAllowedWindow(now, rule.startMinute, rule.endMinute)) {
                return rule.label + " n'est pas autorisé à cette heure.";
            }
            if (rule.dailyLimitMinutes > 0 && PermissionUtils.hasUsageAccess(this)) {
                long used = UsageUtils.todayUsageMs(this, pkg);
                if (used >= rule.dailyLimitMinutes * 60_000L) {
                    return "Le quota quotidien de " + rule.dailyLimitMinutes + " min est atteint pour " + rule.label + ".";
                }
            }
        }
        return null;
    }

    private boolean shouldBlockShortContent(long usageMs) {
        int now = TimeUtils.nowMinute();
        if (!TimeUtils.isInsideAllowedWindow(now, prefs.shortStartMinute(), prefs.shortEndMinute())) return true;
        int limit = prefs.shortLimitMinutes();
        return limit > 0 && usageMs >= limit * 60_000L;
    }

    private void startShortTracking(String pkg) {
        if (shortTracking && pkg.equals(shortPackage)) return;
        stopShortTracking();
        shortTracking = true;
        shortPackage = pkg;
        lastShortTick = System.currentTimeMillis();
        handler.postDelayed(shortTicker, 1000L);
    }

    private void stopShortTracking() {
        shortTracking = false;
        shortPackage = null;
        handler.removeCallbacks(shortTicker);
    }

    private void blockShortSurface() {
        stopShortTracking();
        performGlobalAction(GLOBAL_ACTION_BACK);
        Toast.makeText(this, "Quota Reels / Stories / Shorts atteint ou hors plage autorisée.", Toast.LENGTH_SHORT).show();
    }

    private void showBlock(String pkg, String reason) {
        long now = System.currentTimeMillis();
        if (pkg.equals(lastBlockedPackage) && now - lastBlockAt < 1200L) return;
        lastBlockedPackage = pkg;
        lastBlockAt = now;
        handler.removeCallbacks(appTicker);

        Intent i = new Intent(this, BlockActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra(BlockActivity.EXTRA_PACKAGE, pkg);
        i.putExtra(BlockActivity.EXTRA_REASON, reason);
        startActivity(i);
    }

    @Override public void onInterrupt() {}

    @Override public void onDestroy() {
        stopShortTracking();
        handler.removeCallbacks(appTicker);
        super.onDestroy();
    }
}
