package com.local.focusfence.detector;
import org.junit.Test;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;
public class DetectorCorpusTest {
 private Set<String> values(JSONArray a)throws Exception{Set<String>s=new HashSet<>();for(int i=0;i<a.length();i++)s.add(a.getString(i));return s;}
 @Test public void syntheticCorpusUsesProductionClassifier()throws Exception{
  try(InputStream in=getClass().getResourceAsStream("/instagram-synthetic-v47.json")){
   assertNotNull(in);JSONArray fixtures=new JSONArray(new String(in.readAllBytes(),StandardCharsets.UTF_8));assertEquals(5,fixtures.length());
   for(int i=0;i<fixtures.length();i++){JSONObject f=fixtures.getJSONObject(i);ShortSurfaceDetector.Surface result=ShortSurfaceDetector.classifyInstagramForTest(values(f.getJSONArray("ids")),values(f.getJSONArray("selectedIds")),Collections.emptySet(),true);assertEquals(f.getString("name"),f.getString("expected"),result==null?"UTILITY":result.name());}
  }
 }
}
