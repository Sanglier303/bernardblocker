package com.local.focusfence.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class SystemFlowWindowsTest {
    private static final String PKG = "com.android.settings";
    private static final String ADMIN = PKG + ".applications.specialaccess.deviceadmin.DeviceAdminAdd";
    private static SystemFlowWindows returned() {
        SystemFlowWindows w = new SystemFlowWindows();
        w.begin("current", PKG, ADMIN); w.observeAuthorized(PKG, ADMIN, 41); w.complete("current", 1000);
        return w;
    }
    @Test public void onlyAnObservedReturnedActivityIsStale() {
        SystemFlowWindows w = new SystemFlowWindows();
        w.begin("current", PKG, ADMIN); w.observeAuthorized(PKG, ADMIN, 41);
        assertFalse(w.isCompleted(PKG, ADMIN, 41));
        w.complete("current", 1000); assertTrue(w.isCompleted(PKG, ADMIN, 41));
    }
    @Test public void missingObservationNeverRetiresAWindow() {
        SystemFlowWindows w = new SystemFlowWindows(); w.begin("current", PKG, ADMIN); w.complete("current", 1000);
        assertFalse(w.isCompleted(PKG, ADMIN, 41));
    }
    @Test public void otherPackageAndActivityCannotBeRecorded() {
        SystemFlowWindows w = new SystemFlowWindows(); w.begin("current", PKG, ADMIN);
        w.observeAuthorized("com.attacker", ADMIN, 41);
        w.observeAuthorized(PKG, PKG + ".SubSettings", 41); w.complete("current", 1000);
        assertFalse(w.isCompleted(PKG, ADMIN, 41));
    }
    @Test public void unobservedAndUnfocusedOtherWindowsStillNeedProtection() {
        SystemFlowWindows w = returned();
        assertFalse(w.isCompleted(PKG, ADMIN, 42));
        assertFalse(w.isCompleted(PKG, PKG + ".SubSettings", 41));
        assertFalse(w.isCompleted("com.other.settings", ADMIN, 41));
    }
    @Test public void unknownRootClassOrIdentifierNeverMatches() {
        SystemFlowWindows w = returned();
        assertFalse(w.isCompleted(PKG, null, 41));
        assertFalse(w.isCompleted(null, ADMIN, 41));
        assertFalse(w.isCompleted(PKG, ADMIN, -1));
    }
    @Test public void cancelledLaunchCannotRetireAWindow() {
        SystemFlowWindows w = new SystemFlowWindows(); w.begin("current", PKG, ADMIN);
        w.observeAuthorized(PKG, ADMIN, 41); w.cancelPending(); w.complete("current", 1000);
        assertFalse(w.isCompleted(PKG, ADMIN, 41));
    }
    @Test public void staleCreationEventCannotReactivateClosingWindow() {
        SystemFlowWindows w = returned(); w.windowAdded(41, 900);
        assertTrue(w.isCompleted(PKG, ADMIN, 41));
    }
    @Test public void newCreationEventDoesNotReuseOldCompletionEvidence() {
        SystemFlowWindows w = returned(); w.windowAdded(41, 1001);
        assertFalse(w.isCompleted(PKG, ADMIN, 41));
    }
    @Test public void unknownEventTimeFailsClosed() {
        SystemFlowWindows w = returned(); w.windowAdded(41, 0);
        assertFalse(w.isCompleted(PKG, ADMIN, 41));
    }
    @Test public void reconnectClearsAllRetirementEvidence() {
        SystemFlowWindows w = returned(); w.clear(); assertFalse(w.isCompleted(PKG, ADMIN, 41));
    }
    @Test public void nextFlowDoesNotCompleteEarlierUnreturnedActivity() {
        SystemFlowWindows w = new SystemFlowWindows();
        w.begin("current", PKG, ADMIN); w.observeAuthorized(PKG, ADMIN, 41);
        w.begin("current", PKG, PKG + ".SubSettings"); w.complete("current", 1000);
        assertFalse(w.isCompleted(PKG, ADMIN, 41));
    }
    @Test public void completedWindowMemoryIsBounded() {
        SystemFlowWindows w = new SystemFlowWindows();
        for (int n=0;n<9;n++) { w.begin("current", PKG, ADMIN); w.observeAuthorized(PKG, ADMIN, n); w.complete("current", 1000+n); }
        assertFalse(w.isCompleted(PKG, ADMIN, 0)); assertTrue(w.isCompleted(PKG, ADMIN, 8));
    }
    @Test public void lateResultCannotRetireNewerWindowOfSameActivity() {
        SystemFlowWindows w = new SystemFlowWindows();
        w.begin("old", PKG, ADMIN); w.observeAuthorized(PKG, ADMIN, 41);
        w.begin("new", PKG, ADMIN); w.observeAuthorized(PKG, ADMIN, 42);
        w.complete("old", 1000);
        assertFalse(w.isCompleted(PKG, ADMIN, 42));
        w.complete("new", 1100); assertTrue(w.isCompleted(PKG, ADMIN, 42));
    }
    @Test public void lateCancellationCannotDiscardNewerObservation() {
        SystemFlowWindows w = new SystemFlowWindows();
        w.begin("new", PKG, ADMIN); w.observeAuthorized(PKG, ADMIN, 42);
        w.cancelPending("old"); w.complete("new", 1000);
        assertTrue(w.isCompleted(PKG, ADMIN, 42));
    }
    @Test public void unknownOrEmptyResultTokenNeverCompletesFlow() {
        SystemFlowWindows w = new SystemFlowWindows();
        w.begin("new", PKG, ADMIN); w.observeAuthorized(PKG, ADMIN, 42);
        w.complete(null, 1000); w.complete("", 1000); w.complete("wrong", 1000);
        assertFalse(w.isCompleted(PKG, ADMIN, 42));
    }
    @Test public void oldResultAfterReconnectCannotCompleteNewObservation() {
        SystemFlowWindows w = new SystemFlowWindows();
        w.begin("before", PKG, ADMIN); w.observeAuthorized(PKG, ADMIN, 41); w.clear();
        w.begin("after", PKG, ADMIN); w.observeAuthorized(PKG, ADMIN, 42);
        w.complete("before", 1000); assertFalse(w.isCompleted(PKG, ADMIN, 42));
    }
}
