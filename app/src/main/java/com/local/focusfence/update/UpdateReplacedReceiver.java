package com.local.focusfence.update;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
/** Reschedule after Android replaces this package; never modifies protection preferences. */
public final class UpdateReplacedReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent) {
        if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())){
            UpdateManager.state(c);UpdateManager.schedule(c);
        }
    }
}
