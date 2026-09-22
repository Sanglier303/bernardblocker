package com.local.focusfence.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class WindowSnapshotTest {
 @Test public void liveWindowMustStillBeGuarded(){assertFalse(SystemScreenPolicy.rootWindowIsStale(true,true));}
 @Test public void closedWindowCannotCreateAnotherPin(){assertTrue(SystemScreenPolicy.rootWindowIsStale(true,false));}
 @Test public void unavailableSnapshotDoesNotAuthorizeSettings(){assertFalse(SystemScreenPolicy.rootWindowIsStale(false,false));}
 @Test public void unknownSnapshotWithOldPresenceStillFailsClosed(){assertFalse(SystemScreenPolicy.rootWindowIsStale(false,true));}
}
