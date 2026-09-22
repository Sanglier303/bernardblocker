from pathlib import Path
import shutil,sys
root=Path(sys.argv[1]); reviewed=Path(sys.argv[2])
J='app/src/main/java/com/local/focusfence/'
T='app/src/test/java/com/local/focusfence/'
def change(rel,old,new):
 p=root/rel;s=p.read_text();assert old in s,(rel,old[:100]);p.write_text(s.replace(old,new))
for rel in [J+'core/ClockPolicy.java',J+'core/Rules.java',J+'detector/ShortSurfaceDetector.java',J+'storage/Journal.java',T+'core/ClockPolicyTest.java',T+'detector/ShortSurfaceDetectorTest.java']:
 (root/rel).parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(reviewed/rel,root/rel)
change('app/build.gradle','versionCode 12','versionCode 13');change('app/build.gradle',"versionName '0.4.5'","versionName '0.4.6'")
f=J+'service/FocusAccessibilityService.java'
change(f,'import com.local.focusfence.core.Rules;','import com.local.focusfence.core.Rules;\nimport com.local.focusfence.core.ClockPolicy;')
change(f,'private long overlayAt,lastElapsed,lastWall,lastSample,lastPinLaunch;','private String lastZone="";\n    private long overlayAt,lastElapsed,lastWall,lastSample,lastPinLaunch;')
s=(reviewed/f).read_text();start=s.index('        if(Intent.ACTION_TIME_CHANGED.equals(action)');end=s.index('        if(Intent.ACTION_SCREEN_OFF',start)
p=root/f;new=p.read_text();a=new.index('        if(Intent.ACTION_TIME_CHANGED.equals(action)');b=new.index('        if(Intent.ACTION_SCREEN_OFF',a);p.write_text(new[:a]+s[start:end]+new[b:])
start=s.index('    private void flush(){');end=s.index('    private LimitDecision socialDecision',start)
p=root/f;new=p.read_text();a=new.index('    private void flush(){');b=new.index('    private void sample(){',a);p.write_text(new[:a]+s[start:end]+new[b:])
change(f,'handler.removeCallbacksAndMessages(null);queued=false;windowClass.clear();windowIds.clear();','handler.removeCallbacksAndMessages(null);queued=false;tracking=false;connected=false;\n        instagramRedirectPending=false;removeOverlay();windowClass.clear();windowIds.clear();')
change(f,'lastWall=System.currentTimeMillis();\n        IntentFilter','lastWall=System.currentTimeMillis();\n        lastZone=java.time.ZoneId.systemDefault().getId();prefs.stopDetectorCounting();\n        IntentFilter')
change(f,'@Override public void onInterrupt(){if(connected)flush();tracking=false;removeOverlay();if(journal!=null)journal.markIncomplete("Service interrompu par Android");}', '@Override public void onInterrupt(){if(connected)flush();tracking=false;instagramRedirectPending=false;handler.removeCallbacks(verifyInstagram);if(prefs!=null)prefs.stopDetectorCounting();removeOverlay();if(journal!=null)journal.markIncomplete("Service interrompu par Android");}')
change(f,'connected=false;tracking=false;handler.removeCallbacksAndMessages(null);','connected=false;tracking=false;instagramRedirectPending=false;Journal.monitoring=false;if(prefs!=null)prefs.stopDetectorCounting();handler.removeCallbacksAndMessages(null);')
f=J+'storage/Prefs.java';s=(reviewed/f).read_text();a=s.index('    public void stopDetectorCounting()');b=s.index('    public String detectorStatus()',a)
change(f,'    public String detectorStatus()',s[a:b]+'    public String detectorStatus()')
(root/(J+'storage/DecisionLog.java')).write_text('''package com.local.focusfence.storage;
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
''')
f=J+'util/AccessEvaluator.java'
change(f,'prefs.raw().edit().putString("last_block_v45",d.diagnostic(pkg,surface).toString()).apply();','JSONObject entry=d.diagnostic(pkg,surface);\n        prefs.raw().edit().putString("last_block_v45",entry.toString()).apply();\n        com.local.focusfence.storage.DecisionLog.record(prefs,entry);')
f=J+'util/DiagnosticReport.java'
change(f,'"bernard-diagnostic-v1"','"bernard-diagnostic-v2"')
change(f,'JSONObject sources=new JSONObject();','JSONArray appRules=new JSONArray();for(com.local.focusfence.model.AppRule r:p.getAppRules())appRules.put(new JSONObject().put("package",r.packageName).put("enabled",r.enabled).put("alwaysBlocked",r.alwaysBlocked).put("limitMinutes",r.dailyLimitMinutes).put("startMinute",r.startMinute).put("endMinute",r.endMinute));\n            quota.put("shortEnabled",p.shortEnabled()).put("gamesEnabled",p.gamesEnabled()).put("gamePackages",new JSONArray(p.gamePackages()));\n            JSONObject sources=new JSONObject();')
change(f,'.put("permissions",permissions).put("quota",quota).put("socialSources",sources)','.put("permissions",permissions).put("quota",quota).put("socialSources",sources).put("appRules",appRules).put("blockDecisions",DecisionLog.entries(p))')
f=J+'ui/MainActivity.java';p=root/f;s=p.read_text();s=s.replace('postDelayed(this,5000)','postDelayed(this,1000)').replace('postDelayed(refresh,5000)','postDelayed(refresh,1000)')
s=s.replace('value=remaining+" min";caption="restantes aujourd’hui";','value=Rules.remainingTime(used,cap);caption="restantes aujourd’hui";')
old='else if(!inWindow){value="En pause";caption="jusqu’à "+Rules.clock(start);}'
quota='else if(exhausted){value="0 min";caption="quota atteint aujourd’hui";}'
if s.index(old)<s.index(quota):
 s=s.replace(quota,'').replace(old,quota+'\n        '+old)
needle='        if(prefs.tamperLock()){'
card='''        org.json.JSONObject lastBlock=DecisionLog.latest(prefs);
        if(lastBlock!=null){
            space(body,10);LinearLayout recent=card(this);recent.addView(title(this,"Dernier blocage constaté",15));space(recent,6);
            String when=java.time.Instant.ofEpochMilli(lastBlock.optLong("at")).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm:ss"));
            String reason=lastBlock.optString("reason");
            String cause="SCHEDULE".equals(reason)?"Hors horaires":"QUOTA".equals(reason)?"Quota quotidien atteint":"TAMPER".equals(reason)?"Protection anti-contournement":"ALWAYS_BLOCKED".equals(reason)?"Règle d’application entière":"USAGE_PERMISSION".equals(reason)?"Autorisation d’utilisation manquante":"Restriction de protection";
            recent.addView(muted(this,when+" · "+cause,12));space(recent,4);
            recent.addView(muted(this,lastBlock.optString("package")+" · règle "+lastBlock.optString("scope"),11));
            recent.addView(muted(this,"Solde de cette règle à cet instant : "+Rules.remainingTime(lastBlock.optLong("usedMs"),lastBlock.optInt("limitMinutes"))+". Événement passé, pas un état actuel.",11));body.addView(recent,lp(-1,-2));
        }
'''
assert needle in s;s=s.replace(needle,card+needle,1);p.write_text(s)
f='app/src/androidTest/java/com/local/focusfence/ui/AuditRegressionTest.java';s=(reviewed/f).read_text();a=s.index('    @Test public void staleCountingIndicatorClears');added=s[a:s.rfind('}')]
a=added.index('    @Test public void boundedDecisionEvidence');b=added.index('    @Test public void intervalCrossing',a)
added=added[:a]+'''    @Test public void boundedDecisionEvidenceNamesActualRuleWithoutPrivateContent()throws Exception {
        for(int i=0;i<25;i++)DecisionLog.record(p,new org.json.JSONObject().put("at",System.currentTimeMillis()).put("package","example.app"+i).put("scope","social").put("surface","INSTAGRAM_REELS").put("reason","SCHEDULE").put("usedMs",1000).put("remainingMs",1_199_000).put("limitMinutes",20).put("url","private").put("text","private").put("pin","private"));
        assertEquals(20,DecisionLog.entries(p).length());org.json.JSONObject last=DecisionLog.latest(p);
        assertEquals("SCHEDULE",last.getString("reason"));assertEquals(1_199_000,last.getLong("remainingMs"));
        DecisionLog.record(p,last);assertEquals(20,DecisionLog.entries(p).length());
        org.json.JSONObject exported=new org.json.JSONObject(com.local.focusfence.util.DiagnosticReport.export(c));
        assertEquals(20,exported.getJSONArray("blockDecisions").length());assertTrue(exported.has("appRules"));
        assertFalse(last.has("url"));assertFalse(last.has("text"));assertFalse(last.has("pin"));assertFalse(exported.has("pin"));
    }
'''+added[b:]
p=root/f;s=p.read_text();p.write_text(s[:s.rfind('}')]+added+'}\n')
(root/(T+'core/RemainingTimeTest.java')).write_text('''package com.local.focusfence.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class RemainingTimeTest {
 @Test public void lastMillisecondIsNotAdvertisedAsOneMinute(){assertEquals("1 s",Rules.remainingTime(59_999,1));}
 @Test public void lastMinuteIsExact(){assertEquals("1 min",Rules.remainingTime(0,1));assertEquals("59 s",Rules.remainingTime(1000,1));}
 @Test public void minuteAndSecondsAreShown(){assertEquals("1:30",Rules.remainingTime(30_000,2));}
 @Test public void exhaustedAndUnlimitedRemainDistinct(){assertEquals("0 min",Rules.remainingTime(60_000,1));assertEquals("Illimité",Rules.remainingTime(60_000,0));}
}
''')
s=(reviewed/'docs/AUDIT-v0.4.5.md').read_text();s=s.replace('0.4.5','0.4.6').replace('versionCode 12','versionCode 13')
s=s.replace('Base : `38b335551b225851af6865c47fb39ef36fb22cac` (0.4.4).','Analyse initiale : `38b335551b225851af6865c47fb39ef36fb22cac` (0.4.4). Intégration finale sur `36359e890284ce238c635c2dfb0e2294b33aa757` (0.4.5).')
s=s.replace('LimitDecision centralise les états : désactivé, autorisé, anti-contournement, blocage permanent, permission d\'usage manquante, hors horaires, quota atteint.','La version 0.4.5 intégrée en parallèle fournit déjà AccessPolicy / AccessEvaluator. Ils sont conservés comme décision commune plutôt que remplacés par une deuxième politique. Le quota épuisé reste prioritaire sur les horaires. Les états couverts sont autorisé, anti-contournement, blocage permanent, permission manquante, hors horaires et quota atteint.')
s=s.replace('Six parcours','Cinq parcours').replace('117 méthodes','124 méthodes').replace('14 cas de décision/affichage','4 cas d’affichage').replace("vérifient l'overlay après modification propriétaire simulée, le conflit entre règle globale et solde social, l'indicateur", "vérifient l'indicateur")
s=s.replace('La validation complète doit', 'Les corrections d’overlay, de saisie des horaires, de sauvegarde atomique et de course PIN de 0.4.5, ainsi que leurs tests, sont intégralement conservées. Les sections 3 et 4 décrivent donc des corrections déjà apportées en parallèle, pas de nouveaux changements exclusifs à 0.4.6.\n\nLa validation complète doit').replace('API 26 et API 35','API 26, API 35 et API 36')
(root/'docs/AUDIT-v0.4.6.md').write_text(s)
p=root/'CHANGELOG.md';s=p.read_text();p.write_text('# 0.4.6 — Faux blocages et cohérence des compteurs\n\n- Préserve les corrections d’horaires, d’éditeur, d’overlay et de PIN de 0.4.5.\n- Ne transforme plus les petites corrections automatiques d’heure en verrou durable ; les changements suspects restent protégés.\n- Empêche un ancien onglet Reels sélectionné de faire compter une conversation/profil/publication isolée.\n- Corrige l’attribution des intervalles à minuit et l’état après déconnexion du service.\n- Affiche le solde en secondes, le vrai motif du dernier refus et un diagnostic local borné.\n- Voir docs/AUDIT-v0.4.6.md pour la portée et les limites de validation.\n\n'+s)
