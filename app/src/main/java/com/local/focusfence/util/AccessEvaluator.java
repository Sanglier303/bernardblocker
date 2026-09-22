package com.local.focusfence.util;

import android.content.Context;
import com.local.focusfence.core.AccessPolicy;
import com.local.focusfence.core.AccessPolicy.Reason;
import com.local.focusfence.core.Rules;
import com.local.focusfence.detector.ShortSurfaceDetector.Surface;
import com.local.focusfence.model.AppRule;
import com.local.focusfence.security.BypassAppDetector;
import com.local.focusfence.storage.Journal;
import com.local.focusfence.storage.Prefs;
import org.json.JSONException;
import org.json.JSONObject;

/** One rule evaluator for first block, overlay refresh and deferred Instagram verification. */
public final class AccessEvaluator {
    private final Context context;
    private final Prefs prefs;
    private final Journal journal;
    public AccessEvaluator(Context c) {
        context=c.getApplicationContext();prefs=new Prefs(context);journal=new Journal(context);
    }
    public static final class Decision {
        public final Reason reason;
        public final String scope,label;
        public final int limit,start,end;
        public final long used;
        Decision(Reason r,String scope,String label,long used,int limit,int start,int end) {
            this.reason=r;this.scope=scope;this.label=label;this.used=used;this.limit=limit;this.start=start;this.end=end;
        }
        public boolean blocked(){return reason!=Reason.ALLOWED;}
        public boolean social(){return "social".equals(scope);}
        public boolean schedule(){return reason==Reason.SCHEDULE;}
        public String message(){
            switch(reason){
                case TAMPER:return "Bernard a détecté une tentative de contournement.";
                case CONTAINER:return "Cet espace parallèle est bloqué pendant la protection Bernard.";
                case ALWAYS_BLOCKED:return label+" est bloquée par ta règle.";
                case USAGE_PERMISSION:return "L’accès aux données d’utilisation a été retiré.";
                case QUOTA:return social()?"La limite de scroll infini est atteinte.":"games".equals(scope)?"Le quota Jeux du jour est atteint.":"La limite du jour est atteinte pour "+label+".";
                case SCHEDULE:return social()?"Le scroll infini fait une pause.":"games".equals(scope)?"Les jeux sont en pause pour l’instant.":label+" n’est pas autorisée à cette heure.";
                default:return "Accès autorisé par les règles actuelles.";
            }
        }
        public String detail(Prefs p){
            if(reason==Reason.TAMPER)return p.tamperReason()+". Entre le code dans Bernard pour réactiver.";
            if(reason==Reason.CONTAINER)return "Les applications clonées ou isolées pourraient contourner les limites sociales.";
            if(reason==Reason.ALWAYS_BLOCKED)return "Règle d’application entière, indépendante du quota social. Modifiable avec le code.";
            if(reason==Reason.USAGE_PERMISSION)return "Restaure l’autorisation avec le code. Le temps disponible n’est pas vérifiable.";
            if(reason==Reason.QUOTA)return "Quota quotidien de cette règle épuisé. Remise à zéro à minuit ; les horaires et les autres règles restent applicables.";
            if(reason==Reason.SCHEDULE){
                String remaining=limit<=0?"Quota illimité":Rules.remainingMinutes(used,limit)+" min restantes aujourd’hui";
                return "Plage de cette règle : "+Rules.clock(start)+" → "+Rules.clock(end)+". "+remaining+". Les autres règles restent applicables.";
            }
            return "";
        }
        public String displayKey(Prefs p){return scope+"|"+message()+"|"+detail(p);}
        public JSONObject diagnostic(String pkg,Surface surface) {
            JSONObject o=new JSONObject();
            try{o.put("at",System.currentTimeMillis()).put("package",pkg).put("surface",surface==null?"":surface.name())
                    .put("reason",reason.name()).put("scope",scope).put("ruleLabel",label).put("usedMs",used)
                    .put("limitMinutes",limit).put("remainingMs",Rules.remaining(used,limit)).put("startMinute",start).put("endMinute",end);}
            catch(JSONException e){throw new IllegalStateException(e);}
            return o;
        }
    }
    private Decision rule(String scope,String label,boolean enabled,boolean permanent,boolean needsUsage,
                          boolean hasUsage,long used,int limit,int minute,int start,int end){
        return new Decision(AccessPolicy.decide(enabled,prefs.tamperLock(),permanent,needsUsage,hasUsage,used,limit,minute,start,end),scope,label,used,limit,start,end);
    }
    private static Decision stronger(Decision a,Decision b){return AccessPolicy.strongest(a.reason,b.reason)==a.reason?a:b;}
    public Decision evaluate(String pkg,Surface surface){return evaluate(pkg,surface,TimeUtils.nowMinute());}
    public Decision evaluate(String pkg,Surface surface,int minute){
        boolean usage=PermissionUtils.hasUsageAccess(context);
        Decision result=new Decision(Reason.ALLOWED,"none","",0,0,0,0);
        if(prefs.hasActiveProtection()&&BypassAppDetector.isKnownContainer(context,pkg))
            return new Decision(Reason.CONTAINER,"container","",0,0,0,0);
        if(prefs.gamesEnabled()&&prefs.gamePackages().contains(pkg)){
            int cap=prefs.gamesLimitMinutes();long used=usage&&cap>0?UsageUtils.todayUsageMs(context,prefs.gamePackages()):0;
            result=stronger(result,rule("games","Jeux",true,false,true,usage,used,cap,minute,prefs.gamesStartMinute(),prefs.gamesEndMinute()));
        }
        AppRule r=prefs.getAppRule(pkg);
        if(r!=null&&r.enabled){
            long used=usage&&r.dailyLimitMinutes>0&&!r.alwaysBlocked?UsageUtils.todayUsageMs(context,pkg):0;
            result=stronger(result,rule("app",r.label,true,r.alwaysBlocked,true,usage,used,r.dailyLimitMinutes,minute,r.startMinute,r.endMinute));
        }
        if(surface!=null&&prefs.shortEnabled()&&prefs.featureEnabled(surface.name())){
            result=stronger(result,rule("social","Scroll infini",true,false,false,true,journal.shortMs(),prefs.shortLimitMinutes(),minute,prefs.shortStartMinute(),prefs.shortEndMinute()));
        }
        return result;
    }
    /** Last observed block only, not a current-state promise. No message or screen text retained. */
    public void record(String pkg,Surface surface,Decision d){
        JSONObject entry=d.diagnostic(pkg,surface);
        prefs.raw().edit().putString("last_block_v45",entry.toString()).apply();
        com.local.focusfence.storage.DecisionLog.record(prefs,entry);
    }
}
