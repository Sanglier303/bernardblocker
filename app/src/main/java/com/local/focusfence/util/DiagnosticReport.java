package com.local.focusfence.util;

import android.content.Context;
import android.os.Build;
import com.local.focusfence.BuildConfig;
import com.local.focusfence.storage.*;
import com.local.focusfence.security.FortressPolicy;
import com.local.focusfence.update.UpdateManager;
import org.json.*;
import java.time.Instant;

/** Explicit, local export: no PIN, keys, message texts, account identifiers or telemetry. */
public final class DiagnosticReport {
    private DiagnosticReport() {}
    public static String export(Context c) {
        try {
            Prefs p=new Prefs(c);UpdateManager.State update=UpdateManager.state(c);
            JSONObject permissions=new JSONObject().put("accessibilityEnabled",PermissionUtils.isAccessibilityEnabled(c))
                    .put("serviceRunning",Journal.monitoring).put("usageAccess",PermissionUtils.hasUsageAccess(c))
                    .put("deviceAdmin",FortressPolicy.isAdminActive(c)).put("deviceOwner",FortressPolicy.isDeviceOwner(c));
            JSONObject quota=new JSONObject().put("shortUsedMs",new Journal(c).shortMs())
                    .put("shortCapMinutes",p.shortLimitMinutes()).put("shortStart",p.shortStartMinute()).put("shortEnd",p.shortEndMinute())
                    .put("gamesCapMinutes",p.gamesLimitMinutes()).put("gamesStart",p.gamesStartMinute()).put("gamesEnd",p.gamesEndMinute());
            JSONObject sources=new JSONObject();for(String feature:Prefs.FEATURES)sources.put(feature,p.featureEnabled(feature));
            return new JSONObject().put("format","bernard-diagnostic-v1").put("exportedAt",Instant.now().toString())
                    .put("versionName",BuildConfig.VERSION_NAME).put("versionCode",BuildConfig.VERSION_CODE)
                    .put("androidApi",Build.VERSION.SDK_INT).put("manufacturer",Build.MANUFACTURER).put("model",Build.MODEL)
                    .put("permissions",permissions).put("quota",quota).put("socialSources",sources)
                    .put("tamperLocked",p.tamperLock()).put("tamperReason",p.tamperLock()?p.tamperReason():"")
                    .put("updates",new JSONObject().put("auto",update.autoEnabled).put("ready",update.ready)
                            .put("installing",update.installing).put("lastCheck",update.lastCheckAt).put("error",update.lastError))
                    .toString(2);
        } catch(JSONException e) { throw new IllegalStateException("Diagnostic indisponible",e); }
    }
}
