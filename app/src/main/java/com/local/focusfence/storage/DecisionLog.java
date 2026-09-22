package com.local.focusfence.storage;
import org.json.*;
/** Local, bounded refusal evidence. Only these known fields can be recorded. */
public final class DecisionLog {
    private static final String KEY="block_decisions_v46";
    private static final Object LOCK=new Object();
    private DecisionLog(){}
    public static JSONArray entries(Prefs p){try{return new JSONArray(p.raw().getString(KEY,"[]"));}catch(JSONException|ClassCastException e){return new JSONArray();}}
    public static JSONObject latest(Prefs p){JSONArray a=entries(p);return a.optJSONObject(a.length()-1);}
    public static void record(Prefs p,JSONObject data){synchronized(LOCK){try{
        JSONObject entry=new JSONObject();
        for(String k:new String[]{"at","package","scope","surface","reason","usedMs","remainingMs","limitMinutes","startMinute","endMinute"})if(data.has(k))entry.put(k,data.get(k));
        JSONArray old=entries(p);JSONObject last=old.optJSONObject(old.length()-1);long now=entry.optLong("at");
        boolean same=last!=null&&now>=last.optLong("at")&&now-last.optLong("at")<5000;
        for(String k:new String[]{"package","scope","surface","reason","limitMinutes","startMinute","endMinute"})same=same&&entry.optString(k).equals(last==null?"":last.optString(k));
        if(same)return;
        JSONArray next=new JSONArray();for(int i=Math.max(0,old.length()-19);i<old.length();i++)next.put(old.get(i));
        next.put(entry);p.raw().edit().putString(KEY,next.toString()).apply();
    }catch(JSONException ignored){/* Diagnostics must not affect enforcement. */}}}
}
