package com.local.focusfence.storage;
import org.json.*;
public final class DetectorEvidence {
 private static final String KEY="detector_evidence_v47";
 private DetectorEvidence(){}
 public static synchronized void record(Prefs p,JSONObject evidence){
  if(!p.diagnosticMode()||evidence==null)return;
  JSONArray entries=entries(p);long now=System.currentTimeMillis();
  if(entries.length()>0&&now-entries.optJSONObject(entries.length()-1).optLong("at")<5000)return;
  try{evidence.put("at",now);JSONArray next=new JSONArray();for(int i=Math.max(0,entries.length()-11);i<entries.length();i++)next.put(entries.get(i));next.put(evidence);p.raw().edit().putString(KEY,next.toString()).apply();}
  catch(JSONException e){throw new IllegalStateException(e);}
 }
 public static synchronized JSONArray entries(Prefs p){
  if(!p.diagnosticMode())return new JSONArray();
  try{Object raw=p.raw().getAll().get(KEY);return raw instanceof String?new JSONArray((String)raw):new JSONArray();}
  catch(JSONException e){return new JSONArray();}
 }
 public static void clear(Prefs p){p.raw().edit().remove(KEY).apply();}
}
