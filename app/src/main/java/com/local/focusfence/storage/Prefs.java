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
    private static final String OBSOLETE_ADB_TAMPER_REASON = "Le débogage ADB est actif et peut contourner Bernard";

    private final SharedPreferences sp;
    public SharedPreferences raw(){return sp;}

    public Prefs(Context context) {
        sp = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
        migrateObsoleteAdbTamperLock();
    }

    /** 0.4.7 treated normal owner ADB use as tampering. Clear only that exact obsolete lock. */
    private void migrateObsoleteAdbTamperLock() {
        java.util.Map<String,?> values=sp.getAll();
        if(Boolean.TRUE.equals(values.get(K_TAMPER_LOCK))
                &&OBSOLETE_ADB_TAMPER_REASON.equals(values.get(K_TAMPER_REASON))) {
            sp.edit().putBoolean(K_TAMPER_LOCK,false).remove(K_TAMPER_REASON).remove(K_TAMPER_AT).commit();
        }
    }

    private static final Object RULES_LOCK=new Object();
    private static final String GOOD_RULES="app_rules_last_good_v47";
    private static final String BAD_RULES="app_rules_invalid_v47";
    public List<AppRule> getAppRules() { synchronized(RULES_LOCK) {
        java.util.Map<String,?> values=sp.getAll();
        Object primary=values.get(K_APP_RULES);
        try {
            if(primary!=null&&!(primary instanceof String))throw new IllegalArgumentException("Type invalide");
            if(primary==null&&values.containsKey(GOOD_RULES))throw new IllegalArgumentException("Règles supprimées");
            String raw=primary==null?"[]":(String)primary;
            List<AppRule> rules=RuleCodec.decode(raw);
            if(!values.containsKey(GOOD_RULES)&&!Boolean.TRUE.equals(values.get(BAD_RULES)))
                sp.edit().putString(K_APP_RULES,raw).putString(GOOD_RULES,raw).apply();
            return rules;
        } catch(IllegalArgumentException e) {
            if(!Boolean.TRUE.equals(values.get(BAD_RULES)))sp.edit().putBoolean(BAD_RULES,true).apply();
            setTamperLock("Règles d’application illisibles : réparation avec le code nécessaire");
            Object good=values.get(GOOD_RULES);
            try { if(good instanceof String)return RuleCodec.decode((String)good); }
            catch(IllegalArgumentException ignored) { /* No trustworthy recovery snapshot. */ }
            return new ArrayList<>();
        }
    } }
    public boolean appRulesCorrupt() { synchronized(RULES_LOCK) {
        getAppRules();return Boolean.TRUE.equals(sp.getAll().get(BAD_RULES));
    } }
    public boolean appRulesUnavailable() { synchronized(RULES_LOCK) {
        if(!appRulesCorrupt())return false;
        Object good=sp.getAll().get(GOOD_RULES);
        try { if(good instanceof String){RuleCodec.decode((String)good);return false;} }
        catch(IllegalArgumentException ignored) { }
        return true;
    } }
    /** Called only after explicit owner confirmation. A valid snapshot is preferred to any reset. */
    public boolean repairAppRules(boolean allowReset) { synchronized(RULES_LOCK) {
        if(!com.local.focusfence.security.PinGuard.isAuthorized()||!appRulesCorrupt())return false;
        Object good=sp.getAll().get(GOOD_RULES);String restored=null;
        try { if(good instanceof String){RuleCodec.decode((String)good);restored=(String)good;} }
        catch(IllegalArgumentException ignored) { }
        if(restored==null&&!allowReset)return false;
        if(restored==null)restored="[]";
        // Retain the damaged value for recovery; never erase usage or silently release the lock.
        Object damaged=sp.getAll().get(K_APP_RULES);
        return sp.edit().putString("app_rules_damaged_v47",String.valueOf(damaged))
                .putString(K_APP_RULES,restored).putString(GOOD_RULES,restored).putBoolean(BAD_RULES,false).commit();
    } }

    public AppRule getAppRule(String pkg) {
        for (AppRule r : getAppRules()) {
            if (r.packageName.equals(pkg)) return r;
        }
        return null;
    }

    public void saveAppRule(AppRule rule) { synchronized(RULES_LOCK) {
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
    } }

    public void removeAppRule(String pkg) { synchronized(RULES_LOCK) {
        List<AppRule> rules = getAppRules();
        rules.removeIf(r -> r.packageName.equals(pkg));
        saveAppRules(rules);
    } }

    private void saveAppRules(List<AppRule> rules) { synchronized(RULES_LOCK) {
        if(appRulesCorrupt())throw new IllegalStateException("Répare d’abord les règles illisibles dans les réglages.");
        String raw=RuleCodec.encode(rules);
        if(!sp.edit().putString(K_APP_RULES,raw).putString(GOOD_RULES,raw).commit())
            throw new IllegalStateException("Les règles n’ont pas pu être enregistrées.");
    } }

    /** Keys that alter enforcement, excluding journal/diagnostic writes to prevent self-trigger loops. */
    public static boolean affectsProtection(String key){
        return key==null||key.equals(K_APP_RULES)||key.equals(BAD_RULES)||key.startsWith("games_")
                ||key.equals(K_SHORT_ENABLED)||key.equals(K_SHORT_LIMIT)||key.equals(K_SHORT_START)
                ||key.equals(K_SHORT_END)||key.equals(K_SHORT_STORIES)||key.startsWith("feature_")
                ||key.equals(K_TAMPER_LOCK)||key.equals(K_TAMPER_REASON);
    }
    /** Publish a complete edited rule in one preferences transaction. No counter is touched. */
    public void saveGroup(boolean games,boolean enabled,int limit,int start,int end,Set<String> packages,boolean[] features){
        if(limit<0||limit>1440||start<0||start>=1440||end<0||end>=1440)
            throw new IllegalArgumentException("Règle hors limites");
        SharedPreferences.Editor e=sp.edit();
        if(games){
            e.putBoolean(K_GAMES_ENABLED,enabled).putInt(K_GAMES_LIMIT,limit)
                    .putInt(K_GAMES_START,start).putInt(K_GAMES_END,end)
                    .putStringSet(K_GAMES_PACKAGES,new HashSet<>(packages));
        }else{
            if(features==null||features.length!=FEATURES.length)throw new IllegalArgumentException("Sources manquantes");
            e.putBoolean(K_SHORT_ENABLED,enabled).putInt(K_SHORT_LIMIT,limit)
                    .putInt(K_SHORT_START,start).putInt(K_SHORT_END,end);
            for(int n=0;n<FEATURES.length;n++)e.putBoolean("feature_"+FEATURES[n],features[n]);
        }
        e.apply();
    }

    private boolean protectedBoolean(String key,boolean fallback) {
        Object value=sp.getAll().get(key);
        if(value==null)return fallback;
        if(value instanceof Boolean)return (Boolean)value;
        setTamperLock("Réglage illisible : "+key+". Vérification avec le code nécessaire.");return fallback;
    }
    private Set<String> protectedPackages(String key) {
        Object value=sp.getAll().get(key);Set<String> out=new LinkedHashSet<>();
        if(value==null)return out;
        if(value instanceof Set<?>) {
            for(Object item:(Set<?>)value) {
                if(!(item instanceof String)){setTamperLock("Liste d’applications illisible");continue;}
                out.add((String)item);
            }
        }else setTamperLock("Liste d’applications illisible");
        return out;
    }

    private int boundedInt(String key,int fallback,int maximum) {
        try { int value=sp.getInt(key,fallback);if(value>=0&&value<=maximum)return value; }
        catch(ClassCastException ignored) {}
        setTamperLock("Réglage illisible ou hors limites : "+key+". Corrige cette limite avec le code.");
        return fallback;
    }

    public boolean gamesEnabled() { return protectedBoolean(K_GAMES_ENABLED, false); }
    public void setGamesEnabled(boolean v) { sp.edit().putBoolean(K_GAMES_ENABLED, v).apply(); }
    public Set<String> gamePackages() { return protectedPackages(K_GAMES_PACKAGES); }
    public void setGamePackages(Set<String> v) { sp.edit().putStringSet(K_GAMES_PACKAGES, new HashSet<>(v)).apply(); }
    public int gamesLimitMinutes() { return boundedInt(K_GAMES_LIMIT, 45, 1440); }
    public void setGamesLimitMinutes(int v) { sp.edit().putInt(K_GAMES_LIMIT, Math.max(0, Math.min(1440, v))).apply(); }
    public int gamesStartMinute() { return boundedInt(K_GAMES_START, 18 * 60, 1439); }
    public void setGamesStartMinute(int v) { sp.edit().putInt(K_GAMES_START, Math.max(0, Math.min(1439, v))).apply(); }
    public int gamesEndMinute() { return boundedInt(K_GAMES_END, 23 * 60, 1439); }
    public void setGamesEndMinute(int v) { sp.edit().putInt(K_GAMES_END, Math.max(0, Math.min(1439, v))).apply(); }

    public boolean shortEnabled() { return protectedBoolean(K_SHORT_ENABLED, true); }
    public void setShortEnabled(boolean v) { sp.edit().putBoolean(K_SHORT_ENABLED, v).apply(); }
    public int shortLimitMinutes() { return boundedInt(K_SHORT_LIMIT, 20, 1440); }
    public void setShortLimitMinutes(int v) { sp.edit().putInt(K_SHORT_LIMIT, Math.max(0, Math.min(1440, v))).apply(); }
    public int shortStartMinute() { return boundedInt(K_SHORT_START, 8 * 60, 1439); }
    public void setShortStartMinute(int v) { sp.edit().putInt(K_SHORT_START, Math.max(0, Math.min(1439, v))).apply(); }
    public int shortEndMinute() { return boundedInt(K_SHORT_END, 22 * 60, 1439); }
    public void setShortEndMinute(int v) { sp.edit().putInt(K_SHORT_END, Math.max(0, Math.min(1439, v))).apply(); }
    public boolean includeStories() { return protectedBoolean(K_SHORT_STORIES, true); }
    public void setIncludeStories(boolean v) { sp.edit().putBoolean(K_SHORT_STORIES, v).apply(); }
    public boolean diagnosticMode() { return sp.getBoolean(K_DIAG, false); }
    public void setDiagnosticMode(boolean v) { sp.edit().putBoolean(K_DIAG, v).apply();if(!v)DetectorEvidence.clear(this); }

    public boolean tamperLock(){
        Object value=sp.getAll().get(K_TAMPER_LOCK);
        return value!=null&&(!(value instanceof Boolean)||(Boolean)value);
    }
    public String tamperReason(){ Object v=sp.getAll().get(K_TAMPER_REASON);return v instanceof String?(String)v:"Protection anti-contournement active"; }
    public long tamperAt(){ Object v=sp.getAll().get(K_TAMPER_AT);return v instanceof Long?(Long)v:0L; }
    public void setTamperLock(String reason){
        if(Boolean.TRUE.equals(sp.getAll().get(K_TAMPER_LOCK))) return;
        sp.edit().putBoolean(K_TAMPER_LOCK,true)
                .putString(K_TAMPER_REASON,reason==null?"Protection anti-contournement active":reason)
                .putLong(K_TAMPER_AT,System.currentTimeMillis()).apply();
    }
    public void clearTamperLock(){
        sp.edit().putBoolean(K_TAMPER_LOCK,false).remove(K_TAMPER_REASON).remove(K_TAMPER_AT).apply();
    }
    public boolean deviceAdminSeen(){ return protectedBoolean(K_DEVICE_ADMIN_SEEN,false); }
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
        return protectedBoolean("feature_"+name,def);
    }
    public void setFeature(String name,boolean value){sp.edit().putBoolean("feature_"+name,value).apply();}
    public boolean anyShortFeature(){for(String n:FEATURES)if(featureEnabled(n))return true;return false;}
    public boolean hasActiveProtection(){
        if(appRulesCorrupt())return true;
        if(shortEnabled() && anyShortFeature())return true;
        if(gamesEnabled() && !gamePackages().isEmpty())return true;
        for(AppRule r:getAppRules())if(r.enabled)return true;
        return false;
    }
    public boolean hasFiniteGoal(){
        if(shortEnabled() && anyShortFeature() && shortLimitMinutes()>0)return true;
        if(gamesEnabled() && !gamePackages().isEmpty() && gamesLimitMinutes()>0)return true;
        for(AppRule r:getAppRules())if(r.enabled && (r.alwaysBlocked || r.dailyLimitMinutes>0))return true;
        return false;
    }
    public boolean needsUsage(){
        if(gamesEnabled()&&!gamePackages().isEmpty()&&gamesLimitMinutes()>0)return true;
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
    public void stopDetectorCounting(){
        if(detectorCounting())sp.edit().putBoolean("detector_counting_v31",false).apply();
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
