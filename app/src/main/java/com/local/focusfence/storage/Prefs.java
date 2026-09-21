package com.local.focusfence.storage;

import android.content.Context;
import android.content.SharedPreferences;

import com.local.focusfence.model.AppRule;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Prefs {
    private static final String NAME = "focusfence";
    private static final String K_APP_RULES = "app_rules";
    private static final String K_GAMES_ENABLED = "games_enabled";
    private static final String K_GAMES_PACKAGES = "games_packages";
    private static final String K_GAMES_LIMIT = "games_limit";
    private static final String K_GAMES_START = "games_start";
    private static final String K_GAMES_END = "games_end";
    private static final String K_SHORT_ENABLED = "short_enabled";
    private static final String K_SHORT_LIMIT = "short_limit";
    private static final String K_SHORT_START = "short_start";
    private static final String K_SHORT_END = "short_end";
    private static final String K_SHORT_STORIES = "short_stories";
    private static final String K_SHORT_USAGE = "short_usage_ms";
    private static final String K_SHORT_DATE = "short_usage_date";
    private static final String K_DIAG = "diagnostic_mode";
    private static final String K_TAMPER_LOCK = "tamper_lock_v4";
    private static final String K_TAMPER_REASON = "tamper_reason_v4";
    private static final String K_TAMPER_AT = "tamper_at_v4";
    private static final String K_DEVICE_ADMIN_SEEN = "device_admin_seen_v41";

    private final SharedPreferences sp;
    public SharedPreferences raw(){return sp;}

    public Prefs(Context context) {
        sp = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public List<AppRule> getAppRules() {
        List<AppRule> out = new ArrayList<>();
        String raw = sp.getString(K_APP_RULES, "[]");
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                AppRule r = AppRule.fromJson(o);
                if (!r.packageName.isEmpty()) out.add(r);
            }
        } catch (Exception ignored) {}
        return out;
    }

    public AppRule getAppRule(String pkg) {
        for (AppRule r : getAppRules()) {
            if (r.packageName.equals(pkg)) return r;
        }
        return null;
    }

    public void saveAppRule(AppRule rule) {
        List<AppRule> rules = getAppRules();
        boolean replaced = false;
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).packageName.equals(rule.packageName)) {
                rules.set(i, rule);
                replaced = true;
                break;
            }
        }
        if (!replaced) rules.add(rule);
        saveAppRules(rules);
    }

    public void removeAppRule(String pkg) {
        List<AppRule> rules = getAppRules();
        rules.removeIf(r -> r.packageName.equals(pkg));
        saveAppRules(rules);
    }

    private void saveAppRules(List<AppRule> rules) {
        JSONArray a = new JSONArray();
        for (AppRule r : rules) {
            try { a.put(r.toJson()); } catch (Exception ignored) {}
        }
        sp.edit().putString(K_APP_RULES, a.toString()).apply();
    }

    public boolean gamesEnabled() { return sp.getBoolean(K_GAMES_ENABLED, false); }
    public void setGamesEnabled(boolean v) { sp.edit().putBoolean(K_GAMES_ENABLED, v).apply(); }
    public Set<String> gamePackages() { return new LinkedHashSet<>(sp.getStringSet(K_GAMES_PACKAGES, new HashSet<>())); }
    public void setGamePackages(Set<String> v) { sp.edit().putStringSet(K_GAMES_PACKAGES, new HashSet<>(v)).apply(); }
    public int gamesLimitMinutes() { return sp.getInt(K_GAMES_LIMIT, 45); }
    public void setGamesLimitMinutes(int v) { sp.edit().putInt(K_GAMES_LIMIT, Math.max(0, v)).apply(); }
    public int gamesStartMinute() { return sp.getInt(K_GAMES_START, 18 * 60); }
    public void setGamesStartMinute(int v) { sp.edit().putInt(K_GAMES_START, v).apply(); }
    public int gamesEndMinute() { return sp.getInt(K_GAMES_END, 23 * 60); }
    public void setGamesEndMinute(int v) { sp.edit().putInt(K_GAMES_END, v).apply(); }

    public boolean shortEnabled() { return sp.getBoolean(K_SHORT_ENABLED, true); }
    public void setShortEnabled(boolean v) { sp.edit().putBoolean(K_SHORT_ENABLED, v).apply(); }
    public int shortLimitMinutes() { return sp.getInt(K_SHORT_LIMIT, 20); }
    public void setShortLimitMinutes(int v) { sp.edit().putInt(K_SHORT_LIMIT, Math.max(0, v)).apply(); }
    public int shortStartMinute() { return sp.getInt(K_SHORT_START, 8 * 60); }
    public void setShortStartMinute(int v) { sp.edit().putInt(K_SHORT_START, v).apply(); }
    public int shortEndMinute() { return sp.getInt(K_SHORT_END, 22 * 60); }
    public void setShortEndMinute(int v) { sp.edit().putInt(K_SHORT_END, v).apply(); }
    public boolean includeStories() { return sp.getBoolean(K_SHORT_STORIES, true); }
    public void setIncludeStories(boolean v) { sp.edit().putBoolean(K_SHORT_STORIES, v).apply(); }
    public boolean diagnosticMode() { return sp.getBoolean(K_DIAG, false); }
    public void setDiagnosticMode(boolean v) { sp.edit().putBoolean(K_DIAG, v).apply(); }

    public boolean tamperLock(){ return sp.getBoolean(K_TAMPER_LOCK,false); }
    public String tamperReason(){ return sp.getString(K_TAMPER_REASON,"Protection anti-contournement active"); }
    public long tamperAt(){ return sp.getLong(K_TAMPER_AT,0L); }
    public void setTamperLock(String reason){
        if(tamperLock()) return;
        sp.edit().putBoolean(K_TAMPER_LOCK,true)
                .putString(K_TAMPER_REASON,reason==null?"Protection anti-contournement active":reason)
                .putLong(K_TAMPER_AT,System.currentTimeMillis()).apply();
    }
    public void clearTamperLock(){
        sp.edit().putBoolean(K_TAMPER_LOCK,false).remove(K_TAMPER_REASON).remove(K_TAMPER_AT).apply();
    }
    public boolean deviceAdminSeen(){ return sp.getBoolean(K_DEVICE_ADMIN_SEEN,false); }
    public void setDeviceAdminSeen(boolean value){ sp.edit().putBoolean(K_DEVICE_ADMIN_SEEN,value).apply(); }

    public synchronized long shortUsageMs() {
        rolloverShortUsageIfNeeded();
        return sp.getLong(K_SHORT_USAGE, 0L);
    }

    public synchronized long addShortUsage(long deltaMs) {
        rolloverShortUsageIfNeeded();
        long next = Math.max(0L, sp.getLong(K_SHORT_USAGE, 0L) + Math.max(0L, deltaMs));
        sp.edit().putLong(K_SHORT_USAGE, next).apply();
        return next;
    }

    public synchronized void resetShortUsage() {
        sp.edit().putString(K_SHORT_DATE, LocalDate.now().toString()).putLong(K_SHORT_USAGE, 0L).apply();
    }

    private void rolloverShortUsageIfNeeded() {
        String today = LocalDate.now().toString();
        String stored = sp.getString(K_SHORT_DATE, "");
        if (!today.equals(stored)) {
            sp.edit().putString(K_SHORT_DATE, today).putLong(K_SHORT_USAGE, 0L).apply();
        }
    }

    public String person(){return sp.getString("person", "Céline");}
    public void setPerson(String v){sp.edit().putString("person", v.trim().isEmpty()?"Céline":v.trim().substring(0, Math.min(40,v.trim().length()))).apply();}
    public boolean onboardingDone(){return sp.getBoolean("onboarding_v3",false);}
    public void setOnboardingDone(boolean value){sp.edit().putBoolean("onboarding_v3",value).apply();}
    public boolean featureEnabled(String name){
        boolean def=!name.endsWith("STORIES") || includeStories();
        return sp.getBoolean("feature_"+name,def);
    }
    public void setFeature(String name,boolean value){sp.edit().putBoolean("feature_"+name,value).apply();}
    public boolean anyShortFeature(){for(String n:FEATURES)if(featureEnabled(n))return true;return false;}
    public boolean hasFiniteGoal(){
        if(shortEnabled() && anyShortFeature() && shortLimitMinutes()>0)return true;
        if(gamesEnabled() && !gamePackages().isEmpty() && gamesLimitMinutes()>0)return true;
        for(AppRule r:getAppRules())if(r.enabled && (r.alwaysBlocked || r.dailyLimitMinutes>0))return true;
        return false;
    }
    public boolean needsUsage(){
        if(gamesEnabled()&&!gamePackages().isEmpty())return true;
        for(AppRule r:getAppRules())if(r.enabled&&!r.alwaysBlocked&&r.dailyLimitMinutes>0)return true;
        return false;
    }
    public static final String[] FEATURES={
            "INSTAGRAM_FEED","INSTAGRAM_EXPLORE","INSTAGRAM_REELS","INSTAGRAM_STORIES",
            "FACEBOOK_FEED","FACEBOOK_REELS","FACEBOOK_STORIES","YOUTUBE_SHORTS",
            "TIKTOK_FEED","THREADS_FEED"
    };
    public static final String[] FEATURE_LABELS={
            "Instagram · Fil","Instagram · Explore","Instagram · Reels","Instagram · Stories",
            "Facebook · Fil","Facebook · Reels","Facebook · Stories","YouTube · Shorts",
            "TikTok","Threads"
    };

    public void setDetectorStatus(String value, boolean counting){
        sp.edit().putString("detector_status_v31",value==null?"":value)
                .putBoolean("detector_counting_v31",counting)
                .putLong("detector_status_at_v31",System.currentTimeMillis()).apply();
    }
    public String detectorStatus(){return sp.getString("detector_status_v31","");}
    public boolean detectorCounting(){return sp.getBoolean("detector_counting_v31",false);}
    public long detectorStatusAt(){return sp.getLong("detector_status_at_v31",0L);}
    public String configSignature(){
        StringBuilder b=new StringBuilder();
        b.append(shortEnabled()).append(':').append(shortLimitMinutes()).append(':').append(shortStartMinute()).append(':').append(shortEndMinute());
        for(String f:FEATURES)b.append(':').append(featureEnabled(f));
        b.append('|').append(gamesEnabled()).append(':').append(gamesLimitMinutes()).append(':').append(gamesStartMinute()).append(':').append(gamesEndMinute()).append(':').append(new java.util.TreeSet<>(gamePackages()));
        java.util.List<AppRule> rules=getAppRules();rules.sort((a,c)->a.packageName.compareTo(c.packageName));
        for(AppRule r:rules)b.append('|').append(r.packageName).append(':').append(r.enabled).append(':').append(r.alwaysBlocked).append(':').append(r.dailyLimitMinutes).append(':').append(r.startMinute).append(':').append(r.endMinute);
        return b.toString();
    }
}
