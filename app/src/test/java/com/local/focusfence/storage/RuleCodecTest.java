package com.local.focusfence.storage;
import org.junit.Test;
import com.local.focusfence.model.AppRule;
import java.util.Collections;
import static org.junit.Assert.*;
public class RuleCodecTest {
 private void rejects(String raw){try{RuleCodec.decode(raw);fail("Malformed rules accepted");}catch(IllegalArgumentException expected){}}
 @Test public void legacyDefaultsAreKept(){AppRule r=RuleCodec.decode("[{\"package\":\"com.example.app\"}]").get(0);assertEquals(60,r.dailyLimitMinutes);assertTrue(r.enabled);}
 @Test public void roundTrip(){AppRule r=new AppRule();r.packageName="com.example.app";r.label="Example";r.dailyLimitMinutes=1440;r.startMinute=1439;assertEquals(1439,RuleCodec.decode(RuleCodec.encode(Collections.singletonList(r))).get(0).startMinute);}
 @Test public void emptyIsLegitimate(){assertTrue(RuleCodec.decode("[]").isEmpty());}
 @Test public void missingPackageIsRejected(){rejects("[{}]");}
 @Test public void invalidPackageIsRejected(){rejects("[{\"package\":\"\"}]");}
 @Test public void duplicatesAreRejected(){rejects("[{\"package\":\"com.test.app\"},{\"package\":\"com.test.app\"}]");}
 @Test public void noPartialList(){rejects("[{\"package\":\"com.test.app\"},null]");}
 @Test public void brokenJsonIsRejected(){rejects("[{\"package\":");}
 @Test public void rootMustBeArray(){rejects("{}");}
 @Test public void noTrailingContent(){rejects("[] {} ");}
 @Test public void booleanStringIsRejected(){rejects("[{\"package\":\"com.test.app\",\"enabled\":\"false\"}]");}
 @Test public void negativeQuotaIsRejected(){rejects("[{\"package\":\"com.test.app\",\"dailyLimitMinutes\":-1}]");}
 @Test public void impossibleClockIsRejected(){rejects("[{\"package\":\"com.test.app\",\"endMinute\":1440}]");}
 @Test public void fractionalQuotaIsRejected(){rejects("[{\"package\":\"com.test.app\",\"dailyLimitMinutes\":1.2}]");}
 @Test public void nullRulesAreRejected(){rejects(null);}
}
