package com.local.focusfence.storage;
import org.json.*;
/** Bounded, opt-in technical evidence. A corrupt record never affects access enforcement. */
public final class DetectorEvidence {
 private static final String KEY="detector_evidence_v47";
 private DetectorEvidence(){}
 public static synchronized void record(Prefs p,JSONObject evidence){
  if(!p.diagnosticMode()||evidence==null)return;
  JSONArray entries=entries(p);long now=System.currentTimeMillis();
  JSONObject last=entries.optJSONObject(entries.length()-1);
  long previous=last==null?0:last.optLong("at",0);
  if(previous>0&&now>=previous&&now-previous<5000)return;
  try{JSONObject fresh=new JSONObject(evidence.toString());fresh.put("at",now);JSONArray next=new JSONArray();
   for(int i=Math.max(0,entries.length()-11);i<entries.length();i++)next.put(entries.getJSONObject(i));
   next.put(fresh);p.raw().edit().putString(KEY,next.toString()).apply();}
  catch(JSONException ignored){/* Diagnostics are not an authorization or denial input. */}
 }
 public static synchronized JSONArray entries(Prefs p){
  JSONArray out=new JSONArray();if(!p.diagnosticMode())return out;
  try{Object raw=p.raw().getAll().get(KEY);if(!(raw instanceof String)||((String)raw).length()>65536)return out;
   JSONArray input=new JSONArray((String)raw);
   for(int i=Math.max(0,input.length()-12);i<input.length();i++){
    JSONObject entry=input.optJSONObject(i);if(entry!=null)out.put(entry);
   }
  }catch(JSONException ignored){}
  return out;
 }
 public static synchronized void clear(Prefs p){p.raw().edit().remove(KEY).apply();}
}
