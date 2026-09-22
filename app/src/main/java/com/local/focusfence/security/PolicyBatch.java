package com.local.focusfence.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Execute every requested change and check its real postcondition. Never roll back protection. */
public final class PolicyBatch {
    @FunctionalInterface public interface Action { void run() throws Exception; }
    @FunctionalInterface public interface Check { boolean get() throws Exception; }
    private final List<String> failures = new ArrayList<>();
    public void apply(String name, Action action, Check check) {
        try { action.run(); } catch (Exception e) { fail(name + ": " + e.getClass().getSimpleName()); }
        verify(name, check);
    }
    public void verify(String name, Check check) {
        try { if (!check.get()) fail(name + ": état non confirmé"); }
        catch (Exception e) { fail(name + ": " + e.getClass().getSimpleName()); }
    }
    public void fail(String message) { if (!failures.contains(message)) failures.add(message); }
    public boolean success() { return failures.isEmpty(); }
    public List<String> failures() { return Collections.unmodifiableList(new ArrayList<>(failures)); }
    /** Android returns failed packages, not a success boolean. Null is not evidence of success. */
    public void suspensionResult(String[] refused) {
        if (refused == null) fail("Suspension : résultat absent");
        else for (String pkg : refused) fail("Suspension refusée : " + pkg);
    }
}
