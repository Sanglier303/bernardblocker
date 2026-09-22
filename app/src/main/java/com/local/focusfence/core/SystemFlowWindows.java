package com.local.focusfence.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Identifies a specific window whose owner-authorized Activity has returned a result.
 * This is stale-window evidence, NOT a system permission or a grace period. Callers must
 * still enforce every other window, including unfocused windows on other displays.
 * Confined to the Android main thread by the service/Activity that own it.
 */
public final class SystemFlowWindows {
    private static final int MAX_COMPLETED = 8;
    private String pendingPackage, pendingClass;
    private int pendingId = -1;
    private final Map<Integer, Completed> completed = new LinkedHashMap<>();
    private static final class Completed {
        final String pkg, cls;
        final long returnedAtUptime;
        Completed(String pkg, String cls, long time) {
            this.pkg = pkg; this.cls = cls; returnedAtUptime = time;
        }
    }
    /** Called only after an owner PIN and resolution of an explicit system component. */
    public void begin(String pkg, String cls) {
        cancelPending();
        if (pkg != null && !pkg.isEmpty() && cls != null && cls.startsWith(pkg + ".")) {
            pendingPackage = pkg; pendingClass = cls;
        }
    }
    /** Only a window positively matching the authorized Activity can later be retired. */
    public void observeAuthorized(String pkg, String cls, int windowId) {
        if (windowId >= 0 && pendingPackage != null && pendingPackage.equals(pkg)
                && pendingClass.equals(cls)) {
            pendingId = windowId;
        }
    }
    /** Android's real onActivityResult, not onResume or an accessibility event. */
    public void complete(long uptimeMillis) {
        if (pendingId >= 0 && uptimeMillis >= 0) {
            completed.remove(pendingId);
            completed.put(pendingId, new Completed(pendingPackage, pendingClass, uptimeMillis));
            while (completed.size() > MAX_COMPLETED)
                completed.remove(completed.keySet().iterator().next());
        }
        cancelPending();
    }
    public boolean isCompleted(String pkg, String cls, int windowId) {
        Completed old = completed.get(windowId);
        return old != null && old.pkg.equals(pkg) && old.cls.equals(cls);
    }
    /** A newly created window is a new challenge, even if Android reused its identifier. */
    public void windowAdded(int windowId, long eventUptimeMillis) {
        Completed old = completed.get(windowId);
        // A queued creation event from before the result must not resurrect the closing window.
        // Unknown timestamps fail closed: discard stale-window evidence, never grant access.
        if (old != null && (eventUptimeMillis <= 0 || eventUptimeMillis > old.returnedAtUptime))
            completed.remove(windowId);
    }
    public void cancelPending() { pendingPackage = null; pendingClass = null; pendingId = -1; }
    public void clear() { cancelPending(); completed.clear(); }
}
