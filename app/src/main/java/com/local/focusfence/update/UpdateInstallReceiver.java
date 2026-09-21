package com.local.focusfence.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Receives PackageInstaller status for Bernard's verified self-update flow. */
public final class UpdateInstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent != null) UpdateManager.handleInstallStatus(context, intent);
    }
}
