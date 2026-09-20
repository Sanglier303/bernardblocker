package com.local.focusfence.model;

import org.json.JSONException;
import org.json.JSONObject;

public final class AppRule {
    public String packageName;
    public String label;
    public boolean enabled = true;
    public boolean alwaysBlocked = false;
    public int dailyLimitMinutes = 60;
    public int startMinute = 0;
    public int endMinute = 1439;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("package", packageName);
        o.put("label", label);
        o.put("enabled", enabled);
        o.put("alwaysBlocked", alwaysBlocked);
        o.put("dailyLimitMinutes", dailyLimitMinutes);
        o.put("startMinute", startMinute);
        o.put("endMinute", endMinute);
        return o;
    }

    public static AppRule fromJson(JSONObject o) {
        AppRule r = new AppRule();
        r.packageName = o.optString("package", "");
        r.label = o.optString("label", r.packageName);
        r.enabled = o.optBoolean("enabled", true);
        r.alwaysBlocked = o.optBoolean("alwaysBlocked", false);
        r.dailyLimitMinutes = o.optInt("dailyLimitMinutes", 60);
        r.startMinute = o.optInt("startMinute", 0);
        r.endMinute = o.optInt("endMinute", 1439);
        return r;
    }
}
