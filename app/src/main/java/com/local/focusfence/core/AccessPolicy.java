package com.local.focusfence.core;

/** Fresh decisions only: remaining daily time is not a permission outside the allowed window. */
public final class AccessPolicy {
    private AccessPolicy() {}
    public enum Reason { ALLOWED, SCHEDULE, QUOTA, USAGE_PERMISSION, ALWAYS_BLOCKED, TAMPER, CONTAINER }
    public static Reason decide(boolean enabled, boolean tamper, boolean permanent,
                                boolean needsUsage, boolean hasUsage, long used, int limit,
                                int minute, int start, int end) {
        if (!enabled) return Reason.ALLOWED;
        if (tamper) return Reason.TAMPER;
        if (permanent) return Reason.ALWAYS_BLOCKED;
        if (needsUsage && limit > 0 && !hasUsage) return Reason.USAGE_PERMISSION;
        // A used-up quota will not reopen at today's next window. Do not promise otherwise.
        if (Rules.exhausted(used, limit)) return Reason.QUOTA;
        if (!Rules.allowed(minute, start, end)) return Reason.SCHEDULE;
        return Reason.ALLOWED;
    }
    public static Reason strongest(Reason left, Reason right) {
        return left.ordinal() >= right.ordinal() ? left : right;
    }
}
