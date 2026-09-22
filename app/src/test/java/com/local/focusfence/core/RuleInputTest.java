package com.local.focusfence.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class RuleInputTest {
    @Test public void emptyDoesNotMeanUnlimited(){assertEquals(-1,RuleInput.quota(""));assertEquals(-1,RuleInput.quota(null));}
    @Test public void overflowAndOutOfRangeRejected(){for(String s:new String[]{"1441","999999999999","-1","1.5","a","  "})assertEquals(-1,RuleInput.quota(s));}
    @Test public void explicitZeroAndValidNumbersAccepted(){assertEquals(0,RuleInput.quota("0"));assertEquals(45,RuleInput.quota("45"));assertEquals(1440,RuleInput.quota("1440"));}
    @Test public void equalManualEndpointsAreNotImplicitAllDay(){assertFalse(RuleInput.validWindow(false,600,600));assertTrue(RuleInput.validWindow(true,600,600));}
    @Test public void overnightAndNormalAreValid(){assertTrue(RuleInput.validWindow(false,1320,120));assertTrue(RuleInput.validWindow(false,600,900));}
    @Test public void invalidTimeRemainsInvalidEvenAllDay(){assertFalse(RuleInput.validWindow(true,-1,0));assertFalse(RuleInput.validWindow(true,0,1440));}
}
