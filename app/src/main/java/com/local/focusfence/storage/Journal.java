package com.local.focusfence.storage;

import android.content.Context;
import com.local.focusfence.core.Rules;
import com.local.focusfence.model.AppRule;
import com.local.focusfence.util.PermissionUtils;
import com.local.focusfence.util.UsageUtils;
import org.json.*;
import java.time.*;
import java.util.*;

/** Private local journal. A current, unobserved or interrupted day NEVER unlocks a reward. */
public final class Journal {
    private static final Object LOCK = new Object();
    private static final String KEY="journal_v3", CURRENT="journal_current_v3", REWARDS="rewards_v3";
    public static volatile boolean monitoring=false;
    private final Context context; private final Prefs prefs;
    public Journal(Context c){context=c.getApplicationContext();prefs=new Prefs(context);}
    private JSONObject read(){try{return new JSONObject(prefs.raw().getString(KEY,"{}"));}catch(JSONException e){return new JSONObject();}}
    private void write(JSONObject all){prefs.raw().edit().putString(KEY,all.toString()).apply();}
    private static void put(JSONObject o,String k,Object v){try{o.put(k,v);}catch(JSONException e){throw new IllegalStateException(e);}}
    private JSONObject newDay(String date,boolean full){
        JSONObject d=new JSONObject();put(d,"date",date);put(d,"shortMs",0L);put(d,"gamesMs",0L);
        put(d,"eligible",full);put(d,"closed",false);put(d,"success",false);
        put(d,"note",full?"":"Suivi commencé en cours de journée");put(d,"signature",prefs.configSignature());
        put(d,"shortCap",prefs.shortEnabled()&&prefs.anyShortFeature()?prefs.shortLimitMinutes():0);
        put(d,"gamesCap",prefs.gamesEnabled()&&!prefs.gamePackages().isEmpty()?prefs.gamesLimitMinutes():0);
        put(d,"hasGoal",prefs.hasFiniteGoal());return d;
    }
    private boolean healthy(){return monitoring && (!prefs.needsUsage() || PermissionUtils.hasUsageAccess(context));}
    private JSONObject ensure(JSONObject all){
        String now=LocalDate.now().toString(),old=prefs.raw().getString(CURRENT,"");
        JSONObject d=all.optJSONObject(now);
        if(!old.isEmpty()&&!old.equals(now)){
            JSONObject previous=all.optJSONObject(old);
            boolean nextDay;
            try{nextDay=LocalDate.parse(old).plusDays(1).toString().equals(now);}catch(Exception e){nextDay=false;}
            if(previous!=null&&!previous.optBoolean("closed")){
                if(nextDay)updateUsage(previous,UsageUtils.forDate(context,LocalDate.parse(old)));
                boolean valid=healthy()&&nextDay&&previous.optBoolean("eligible")&&previous.optString("signature").equals(prefs.configSignature());
                put(previous,"closed",true);
                put(previous,"eligible",valid);
                boolean success=Rules.successful(true,valid,previous.optBoolean("hasGoal"),previous.optLong("shortMs"),previous.optInt("shortCap"),previous.optLong("gamesMs"),previous.optInt("gamesCap"),previous.optBoolean("otherMet",true));
                put(previous,"success",success);
                if(!valid&&previous.optString("note").isEmpty())put(previous,"note","Suivi incomplet ou réglages modifiés");
            }
            if(d==null){d=newDay(now,healthy()&&nextDay);put(all,now,d);}
            if(now.compareTo(old)<0){put(d,"eligible",false);put(d,"note","Horloge modifiée");}
            unlock(all);
        }
        if(d==null){
            d=newDay(now,false);
            // Preserve an existing FocusFence counter on upgrade. Never fabricate earlier days.
            if(now.equals(prefs.raw().getString("short_usage_date","")))put(d,"shortMs",prefs.raw().getLong("short_usage_ms",0L));
            put(all,now,d);
        }
        prefs.raw().edit().putString(CURRENT,now).apply();
        trim(all);return d;
    }
    private void updateUsage(JSONObject d,Map<String,Long> usage){
        long games=prefs.gamesEnabled()?UsageUtils.sum(usage,prefs.gamePackages()):0L;
        put(d,"gamesMs",Math.max(games,d.optLong("gamesMs")));
        boolean other=d.optBoolean("otherMet",true);
        for(AppRule r:prefs.getAppRules())if(r.enabled){
            long u=usage.getOrDefault(r.packageName,0L);
            int cap=r.alwaysBlocked?-1:r.dailyLimitMinutes;
            if((cap==-1&&u>1500L)||(cap>0&&u>cap*60_000L+1500L))other=false;
        }
        put(d,"otherMet",other);
    }
    public JSONObject today(){synchronized(LOCK){JSONObject a=read(),d=ensure(a);write(a);return copy(d);}}
    private static JSONObject copy(JSONObject x){try{return new JSONObject(x.toString());}catch(JSONException e){return new JSONObject();}}
    public long shortMs(){return today().optLong("shortMs");}
    public void start(){synchronized(LOCK){monitoring=false;JSONObject a=read(),d=ensure(a);put(d,"eligible",false);put(d,"note","Surveillance démarrée ou redémarrée aujourd'hui");write(a);monitoring=true;}}
    public void stop(){markIncomplete("Surveillance interrompue");monitoring=false;}
    public void markIncomplete(String reason){synchronized(LOCK){JSONObject a=read(),d=ensure(a);put(d,"eligible",false);put(d,"note",reason);write(a);}}
    public void settingsChanged(){markIncomplete("Réglages modifiés aujourd'hui");}
    public void sample(Map<String,Long> usage){synchronized(LOCK){
        JSONObject a=read(),d=ensure(a);updateUsage(d,usage);
        if(!healthy()){put(d,"eligible",false);put(d,"note","Autorisations ou surveillance manquantes");}
        if(!d.optString("signature").equals(prefs.configSignature())){put(d,"eligible",false);put(d,"note","Réglages modifiés aujourd'hui");}
        write(a);
    }}
    /** Account the final partial second when leaving a reel, and split at local midnight. */
    public void addShort(long endWall,long duration){synchronized(LOCK){
        if(duration<=0)return;
        JSONObject a=read();String today=LocalDate.now().toString();
        long start=endWall-duration;
        LocalDate startDate=Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate();
        long boundary=startDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if(boundary<endWall){add(a,startDate.toString(),boundary-start);ensure(a);add(a,today,endWall-boundary);}
        else{ensure(a);add(a,today,duration);}
        write(a);
    }}
    private void add(JSONObject a,String day,long duration){
        JSONObject d=a.optJSONObject(day);if(d==null||d.optBoolean("closed"))return;
        long value=d.optLong("shortMs")+Math.max(0,duration);
        // Never cap the history to a preference: lowering a limit must not erase consumption.
        put(d,"shortMs",value);
    }
    public List<JSONObject> days(){synchronized(LOCK){JSONObject a=read();ensure(a);write(a);List<JSONObject> d=new ArrayList<>();for(Iterator<String> it=a.keys();it.hasNext();){JSONObject x=a.optJSONObject(it.next());if(x!=null)d.add(copy(x));}d.sort((x,y)->y.optString("date").compareTo(x.optString("date")));return d;}}
    public int successCount(){return prefs.raw().getInt("success_total_v3",0);}
    private void unlock(JSONObject a){
        Set<String> won=new HashSet<>(prefs.raw().getStringSet("won_dates_v3",Collections.emptySet()));
        for(Iterator<String> it=a.keys();it.hasNext();){String day=it.next();JSONObject d=a.optJSONObject(day);if(d!=null&&d.optBoolean("closed")&&d.optBoolean("success"))won.add(day);}
        Set<String> rewards=new HashSet<>(prefs.raw().getStringSet(REWARDS,Collections.emptySet()));
        int[] targets={1,3,7,14,30};for(int n:targets)if(won.size()>=n)rewards.add("day_"+n);
        prefs.raw().edit().putStringSet("won_dates_v3",won).putInt("success_total_v3",won.size()).putStringSet(REWARDS,rewards).apply();
    }
    public boolean unlocked(int threshold){return prefs.raw().getStringSet(REWARDS,Collections.emptySet()).contains("day_"+threshold);}
    public int streak(){List<JSONObject>d=days();LocalDate expected=LocalDate.now().minusDays(1);int streak=0;for(JSONObject x:d){if(!x.optBoolean("closed"))continue;if(!x.optString("date").equals(expected.toString())||!x.optBoolean("success"))break;streak++;expected=expected.minusDays(1);}return streak;}
    private void trim(JSONObject all){List<String> keys=new ArrayList<>();for(Iterator<String> it=all.keys();it.hasNext();)keys.add(it.next());Collections.sort(keys);while(keys.size()>90){all.remove(keys.remove(0));}}
    public String export(){synchronized(LOCK){JSONObject out=new JSONObject();put(out,"format","bernard-bloqueur-journal-v1");put(out,"exportedAt",Instant.now().toString());JSONObject a=read();ensure(a);write(a);put(out,"days",a);put(out,"successfulDays",successCount());return out.toString();}}
}
