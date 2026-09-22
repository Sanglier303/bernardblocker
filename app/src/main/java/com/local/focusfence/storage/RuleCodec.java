package com.local.focusfence.storage;

import com.local.focusfence.model.AppRule;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict, all-or-nothing decoding. Bad records must never become an empty rule list. */
public final class RuleCodec {
    private RuleCodec() {}
    public static List<AppRule> decode(String raw) {
        try {
            if(raw==null||raw.length()>1_000_000)throw new IllegalArgumentException("Taille des règles invalide");
            JSONTokener tokens=new JSONTokener(raw); Object value=tokens.nextValue();
            if(!(value instanceof JSONArray)||tokens.nextClean()!=0)throw new IllegalArgumentException("Tableau de règles attendu");
            JSONArray a=(JSONArray)value;
            if(a.length()>2048)throw new IllegalArgumentException("Trop de règles");
            List<AppRule> rules=new ArrayList<>();Set<String> packages=new HashSet<>();
            for(int i=0;i<a.length();i++) {
                Object item=a.get(i);if(!(item instanceof JSONObject))throw new IllegalArgumentException("Règle incomplète");
                JSONObject o=(JSONObject)item;
                if(!(o.opt("package") instanceof String))throw new IllegalArgumentException("Application manquante");
                String pkg=o.getString("package");
                if(!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")||!packages.add(pkg))
                    throw new IllegalArgumentException("Identifiant d’application invalide ou dupliqué");
                for(String k:new String[]{"enabled","alwaysBlocked"})
                    if(o.has(k)&&!(o.get(k) instanceof Boolean))throw new IllegalArgumentException("Booléen invalide : "+k);
                if(o.has("label")&&(!(o.get("label") instanceof String)||o.getString("label").length()>512))
                    throw new IllegalArgumentException("Libellé invalide");
                for(String k:new String[]{"dailyLimitMinutes","startMinute","endMinute"}) {
                    if(!o.has(k))continue;
                    Object number=o.get(k);
                    if(!(number instanceof Integer)&&!(number instanceof Long))throw new IllegalArgumentException("Entier attendu : "+k);
                    long n=((Number)number).longValue(),max=k.equals("dailyLimitMinutes")?1440:1439;
                    if(n<0||n>max)throw new IllegalArgumentException("Valeur hors limites : "+k);
                }
                rules.add(AppRule.fromJson(o));
            }
            return rules;
        } catch(Exception e) { throw new IllegalArgumentException("Règles illisibles",e); }
    }
    public static String encode(List<AppRule> rules) {
        try {
            JSONArray array=new JSONArray();for(AppRule r:rules)array.put(r.toJson());
            String raw=array.toString();decode(raw);return raw;
        } catch(Exception e) { throw new IllegalArgumentException("Règles non enregistrées",e); }
    }
}
