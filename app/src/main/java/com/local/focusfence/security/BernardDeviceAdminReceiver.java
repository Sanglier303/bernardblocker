package com.local.focusfence.security;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

import com.local.focusfence.storage.Prefs;

/**
 * Device-admin component used for uninstall resistance on a normal install and as the admin
 * component when Bernard is provisioned as Device Owner.
 */
public final class BernardDeviceAdminReceiver extends DeviceAdminReceiver {
    @Override public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context,intent);
        new Prefs(context).setDeviceAdminSeen(true);
    }

    @Override public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context,intent);
        Prefs prefs=new Prefs(context);
        if(prefs.deviceAdminSeen()) {
            prefs.setTamperLock("La protection anti-désinstallation de Bernard a été désactivée");
        }
    }
}
