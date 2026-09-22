package com.local.focusfence.security;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
public class PolicyBatchTest {
 @Test public void falsePostconditionIsFailure(){PolicyBatch b=new PolicyBatch();b.apply("p",()->{},()->false);assertFalse(b.success());}
 @Test public void setterExceptionIsNotLostEvenIfStateAlreadyMatches(){PolicyBatch b=new PolicyBatch();b.apply("p",()->{throw new SecurityException();},()->true);assertFalse(b.success());}
 @Test public void laterPoliciesStillRunAfterFailure(){PolicyBatch b=new PolicyBatch();AtomicBoolean ran=new AtomicBoolean();b.apply("one",()->{throw new SecurityException();},()->false);b.apply("two",()->ran.set(true),ran::get);assertTrue(ran.get());assertFalse(b.success());}
 @Test public void unavailableGetterIsNotSuccess(){PolicyBatch b=new PolicyBatch();b.verify("p",()->{throw new IllegalStateException();});assertFalse(b.success());}
 @Test public void successfulOperationIsVerified(){PolicyBatch b=new PolicyBatch();AtomicBoolean ran=new AtomicBoolean();b.apply("p",()->ran.set(true),ran::get);assertTrue(b.success());}
 @Test public void nullSuspensionResultIsNotSuccess(){PolicyBatch b=new PolicyBatch();b.suspensionResult(null);assertFalse(b.success());}
 @Test public void refusedPackagesAreReported(){PolicyBatch b=new PolicyBatch();b.suspensionResult(new String[]{"a","b"});assertEquals(2,b.failures().size());assertFalse(b.success());}
 @Test public void emptyRefusalArrayIsSuccess(){PolicyBatch b=new PolicyBatch();b.suspensionResult(new String[0]);assertTrue(b.success());}
 @Test public void readOnlyVerificationNeverExecutesMutation(){PolicyBatch b=new PolicyBatch();b.verify("p",()->true);assertTrue(b.success());}
 @Test public void failureListCannotBeMutated(){PolicyBatch b=new PolicyBatch();b.fail("x");try{b.failures().clear();fail();}catch(UnsupportedOperationException expected){assertFalse(b.success());}}
}
