package com.local.focusfence.update;
import android.content.*;
import android.content.pm.PackageInstaller;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class UpdateReceiverTest {
 @Test public void unrelatedInstallerCallbackCannotClearPendingState(){
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();SharedPreferences p=c.getSharedPreferences("bernard_updates_v1",0);
  p.edit().clear().putInt("session_id",123).putLong("session_code",22).putString("ready_name","keep-me").commit();
  Intent i=new Intent(UpdateManager.ACTION_INSTALL_STATUS).putExtra(PackageInstaller.EXTRA_SESSION_ID,456).putExtra("version_code",22L).putExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_SUCCESS);
  UpdateManager.handleInstallStatus(c,i);assertEquals("keep-me",p.getString("ready_name",""));
  p.edit().clear().commit();
 }
 @Test public void wrongActionCannotClearPendingState(){
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();SharedPreferences p=c.getSharedPreferences("bernard_updates_v1",0);
  p.edit().clear().putInt("session_id",123).putLong("session_code",22).putString("ready_name","keep-me").commit();
  Intent i=new Intent("unrelated").putExtra(PackageInstaller.EXTRA_SESSION_ID,123).putExtra("version_code",22L).putExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_SUCCESS);
  UpdateManager.handleInstallStatus(c,i);assertEquals("keep-me",p.getString("ready_name",""));p.edit().clear().commit();
 }
    @Test public void networkJobPermissionsAndServiceAreDeclared()throws Exception {
        android.content.Context c=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,c.checkSelfPermission(android.Manifest.permission.ACCESS_NETWORK_STATE));
        assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,c.checkSelfPermission(android.Manifest.permission.RECEIVE_BOOT_COMPLETED));
        android.content.pm.ServiceInfo info=c.getPackageManager().getServiceInfo(new android.content.ComponentName(c,UpdateJobService.class),0);
        assertEquals(android.Manifest.permission.BIND_JOB_SERVICE,info.permission);
    }
}
