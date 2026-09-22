package com.local.focusfence.update;
import org.junit.Test;
import java.net.URL;
import java.util.*;
import static org.junit.Assert.*;
public class UpdatePolicyTest {
 private static final String SHA=String.join("",Collections.nCopies(64,"a"));
 private Set<String> official(){return Collections.singleton(UpdatePolicy.CERTIFICATE);}
 @Test public void validManifest(){UpdatePolicy.manifest(11,"0.4.4",SHA,UpdatePolicy.CERTIFICATE);}
 @Test(expected=SecurityException.class) public void invalidHashIsRejected(){UpdatePolicy.manifest(11,"0.4.4",String.join("",Collections.nCopies(64,"z")),UpdatePolicy.CERTIFICATE);}
 @Test(expected=SecurityException.class) public void pathTraversalIsRejected(){UpdatePolicy.manifest(11,"../../bad",SHA,UpdatePolicy.CERTIFICATE);}
 @Test(expected=SecurityException.class) public void wrongManifestSignerIsRejected(){UpdatePolicy.manifest(11,"0.4.4",SHA,SHA);}
 @Test public void onlyGitHubHttpsEndpointsAreAllowed()throws Exception{
  assertTrue(UpdatePolicy.trustedUrl(new URL("https://release-assets.githubusercontent.com/test")));
  assertFalse(UpdatePolicy.trustedUrl(new URL("http://github.com/test")));
  assertFalse(UpdatePolicy.trustedUrl(new URL("https://github.com.evil.example/test")));
  assertFalse(UpdatePolicy.trustedUrl(new URL("https://user:pass@github.com/test")));
  assertFalse(UpdatePolicy.trustedUrl(new URL("https://127.0.0.1/test")));
 }
 @Test public void officialNewerCandidate(){UpdatePolicy.candidate("com.local.focusfence",10,"com.local.focusfence",11,11,"0.4.4","0.4.4",26,35,official(),official());}
 @Test(expected=SecurityException.class) public void downgradeIsRejected(){UpdatePolicy.candidate("com.local.focusfence",11,"com.local.focusfence",10,10,"0.4.3","0.4.3",26,35,official(),official());}
 @Test(expected=SecurityException.class) public void sameVersionIsRejected(){UpdatePolicy.candidate("com.local.focusfence",11,"com.local.focusfence",11,11,"0.4.4","0.4.4",26,35,official(),official());}
 @Test(expected=SecurityException.class) public void otherPackageIsRejected(){UpdatePolicy.candidate("com.local.focusfence",10,"evil.package",11,11,"0.4.4","0.4.4",26,35,official(),official());}
 @Test(expected=SecurityException.class) public void foreignSignerIsRejected(){UpdatePolicy.candidate("com.local.focusfence",10,"com.local.focusfence",11,11,"0.4.4","0.4.4",26,35,official(),Collections.singleton(SHA));}
 @Test(expected=SecurityException.class) public void debugInstallationIsRejected(){UpdatePolicy.candidate("com.local.focusfence",10,"com.local.focusfence",11,11,"0.4.4","0.4.4",26,35,Collections.singleton(SHA),official());}
 @Test(expected=SecurityException.class) public void incompatibleAndroidIsRejected(){UpdatePolicy.candidate("com.local.focusfence",10,"com.local.focusfence",11,11,"0.4.4","0.4.4",36,35,official(),official());}
 @Test(expected=SecurityException.class) public void versionNameMismatchIsRejected(){UpdatePolicy.candidate("com.local.focusfence",10,"com.local.focusfence",11,11,"0.4.2","0.4.4",26,35,official(),official());}
}
