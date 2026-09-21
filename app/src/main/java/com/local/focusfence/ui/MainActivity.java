package com.local.focusfence.ui;

import android.app.*;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import com.local.focusfence.R;
import com.local.focusfence.core.Rules;
import com.local.focusfence.model.AppRule;
import com.local.focusfence.security.PinGuard;
import com.local.focusfence.security.FortressPolicy;
import com.local.focusfence.storage.*;
import com.local.focusfence.util.*;
import org.json.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static com.local.focusfence.ui.Ui.*;

/** Native Android screens, backed by the same preferences and counters as the blocker. */
public final class MainActivity extends Activity {
    private Prefs prefs; private Journal journal;
    private String page="home", lastTab="home"; private int intro=0, rewardIndex=0, historyRange=7;
    private LinearLayout shell,body; private ScrollView scroll;
    private Draft draft; private boolean selectingGames;
    private java.util.List<AppEntry> apps;
    private String pendingExport="";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean pinPromptInFlight;
    private final Runnable refresh=new Runnable(){public void run(){if(page.equals("home")){int y=scroll==null?0:scroll.getScrollY();render();if(scroll!=null)scroll.post(()->scroll.scrollTo(0,y));}handler.postDelayed(this,5000);}};
    private final Runnable pinExpiryCheck=new Runnable(){public void run(){if(isProtectedPage(page)&&!PinGuard.isAuthorized()){requestPin(page);return;}handler.postDelayed(this,2000);}};
    private static final String[] TABS={"home","limits","rewards","history"};
    private static final String[] TAB_NAMES={"Accueil","Limites","Récompenses","Historique"};
    private static final String[] TAB_ICONS={"home","shield","gift","history"};
    private static final int[] REWARD_GOALS={1,3,7,14,30};
    private static final String[] REWARD_NAMES={"Première victoire","L'art de la pause","Bernard en fête","Gardien du temps","Le grand gardien"};
    private static final int[] REWARD_IMAGES={R.drawable.scene_victory,R.drawable.scene_pause,R.drawable.scene_party,R.drawable.scene_limits,R.drawable.scene_guardian};

    static final class AppEntry {
        final String label,pkg;final Drawable icon;
        AppEntry(String label,String pkg,Drawable icon){this.label=label;this.pkg=pkg;this.icon=icon;}
    }
    static final class Draft {
        boolean enabled=true,always=false;int limit,start,end;String pkg="",label="";
        Set<String> packages=new LinkedHashSet<>();boolean[] features=new boolean[Prefs.FEATURES.length];
        JSONObject json(){JSONObject o=new JSONObject();try{o.put("enabled",enabled).put("always",always).put("limit",limit).put("start",start).put("end",end).put("pkg",pkg).put("label",label).put("packages",new JSONArray(packages));JSONArray f=new JSONArray();for(boolean b:features)f.put(b);o.put("features",f);}catch(JSONException ignored){}return o;}
        static Draft from(String raw){try{JSONObject o=new JSONObject(raw);Draft d=new Draft();d.enabled=o.optBoolean("enabled");d.always=o.optBoolean("always");d.limit=o.optInt("limit");d.start=o.optInt("start");d.end=o.optInt("end");d.pkg=o.optString("pkg");d.label=o.optString("label");JSONArray a=o.optJSONArray("packages"),f=o.optJSONArray("features");if(a!=null)for(int i=0;i<a.length();i++)d.packages.add(a.optString(i));if(f!=null)for(int i=0;i<Prefs.FEATURES.length;i++)d.features[i]=f.optBoolean(i);return d;}catch(JSONException e){return null;}}
    }
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);prefs=new Prefs(this);journal=new Journal(this);PinGuard.ensureConfigured(this);
        if(state!=null){page=state.getString("page","home");lastTab=state.getString("tab","home");intro=state.getInt("intro");rewardIndex=state.getInt("reward");selectingGames=state.getBoolean("selectingGames");pendingExport=state.getString("export","");draft=Draft.from(state.getString("draft","{}"));}
        else if(!prefs.onboardingDone())page="intro";
        else{
            String requested=getIntent().getStringExtra("page");
            if(requested!=null&&!requested.isEmpty())page=requested;
        }
        // Saved-instance state and exported launcher intents must never expose a protected page
        // before the PIN activity has had a chance to cover it.
        if(isProtectedPage(page)&&!PinGuard.isAuthorized()){
            final String target=page;
            page=prefs.onboardingDone()?"home":"intro";
            handler.post(()->requestPin(target));
        }
    }
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);pinPromptInFlight=false;String requested=i.getStringExtra("page");if(requested!=null&&!requested.isEmpty())navigate(requested);else render();}
    @Override protected void onResume(){
        super.onResume();PinGuard.clearSystemControlAuthorization();pinPromptInFlight=false;journal.today();handler.removeCallbacks(refresh);handler.removeCallbacks(pinExpiryCheck);
        if(!PinGuard.ensureConfigured(this)){
            // Fail closed if private storage cannot persist the owner verifier.
            finish();return;
        }
        if(isProtectedPage(page)&&!PinGuard.isAuthorized()){
            String target=page;page=prefs.onboardingDone()?"home":"intro";render();handler.postDelayed(refresh,5000);handler.post(()->requestPin(target));return;
        }
        render();handler.postDelayed(refresh,5000);
        if(isProtectedPage(page))handler.postDelayed(pinExpiryCheck,2000);
    }
    @Override protected void onPause(){handler.removeCallbacks(refresh);handler.removeCallbacks(pinExpiryCheck);super.onPause();}
    @Override protected void onStop(){
        // Authorization is foreground-only. Leaving Bernard, opening recents or another activity
        // immediately closes the administrator session.
        if(!isChangingConfigurations())PinGuard.lockNow();
        super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("page",page);out.putString("tab",lastTab);out.putInt("intro",intro);out.putInt("reward",rewardIndex);out.putBoolean("selectingGames",selectingGames);out.putString("export",pendingExport);if(draft!=null)out.putString("draft",draft.json().toString());}

    private boolean isProtectedPage(String next){
        if(Arrays.asList("limits","short","games","individual","select","settings","permissions").contains(next))return true;
        // The first-run presentation is public only before provisioning. Replaying it later must
        // not become a back door to the permission buttons.
        return "intro".equals(next)&&prefs.onboardingDone();
    }
    private void requestPin(String target){
        if(pinPromptInFlight||PinGuard.isAuthorized())return;
        pinPromptInFlight=true;
        Intent i=new Intent(this,PinActivity.class).putExtra(PinActivity.EXTRA_TARGET_PAGE,target);
        startActivity(i);
    }
    private boolean requireAdminNow(){
        if(PinGuard.isAuthorized())return true;
        requestPin(page);
        return false;
    }
    public void navigate(String next){
        if(isProtectedPage(next)&&!PinGuard.isAuthorized()){requestPin(next);return;}
        boolean leavingProtected=isProtectedPage(page)&&!isProtectedPage(next);
        pinPromptInFlight=false;
        View focus=getCurrentFocus();if(focus!=null){android.view.inputmethod.InputMethodManager im=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(im!=null)im.hideSoftInputFromWindow(focus.getWindowToken(),0);}
        page=next;for(String t:TABS)if(t.equals(next))lastTab=next;
        if(leavingProtected)PinGuard.lockNow();
        render();
        handler.removeCallbacks(pinExpiryCheck);if(isProtectedPage(page))handler.postDelayed(pinExpiryCheck,2000);
    }
    private void render(){
        if(isProtectedPage(page)&&!PinGuard.isAuthorized()){
            final String target=page;page=prefs.onboardingDone()?"home":"intro";
            handler.post(()->requestPin(target));
        }
        shell=col(this);shell.setBackgroundColor(PAPER);insets(this,shell,false);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        body=col(this);int width=getResources().getConfiguration().screenWidthDp;int margin=Math.max(18,(width-640)/2);pad(body,margin,0,margin,24);scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
        boolean root=Arrays.asList(TABS).contains(page);
        if(root)shell.addView(brand(),lp(-1,-2));
        else if(!page.equals("intro"))shell.addView(pageHeader(),lp(-1,-2));
        shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        switch(page){
            case "intro":onboarding();break;
            case "limits":limits();break;
            case "short":editor(false,false);break;
            case "games":editor(true,false);break;
            case "individual":editor(false,true);break;
            case "select":appPicker();break;
            case "rewards":rewards();break;
            case "reward":rewardDetail();break;
            case "history":history();break;
            case "settings":settings();break;
            case "permissions":permissions(false);break;
            default:home();
        }
        if(root)shell.addView(nav(),lp(-1,-2));
        setContentView(shell);
    }
    private View brand(){
        LinearLayout r=row(this);pad(r,18,12,12,12);r.addView(avatar(this,44));
        LinearLayout t=col(this);pad(t,11,0,0,0);t.addView(title(this,"Bernard Bloqueur",21));space(t,3);t.addView(muted(this,"Moins de scroll. Plus de vie.",12));r.addView(t,weight());
        r.addView(iconButton(this,"settings","Paramètres",()->navigate("settings")));return r;
    }
    private View pageHeader(){
        LinearLayout r=row(this);pad(r,8,8,16,12);r.addView(iconButton(this,"back","Retour",this::back));
        String s;switch(page){case "short":s="Scroll infini";break;case "games":s="Groupe Jeux";break;case "individual":s=draft==null?"Application":draft.label;break;case "select":s="Choisir les applications";break;case "permissions":s="Les deux autorisations";break;case "reward":s="Ta récompense";break;default:s="Paramètres";}
        TextView t=title(this,s,20);r.addView(t,weight());return r;
    }
    private View nav(){LinearLayout r=row(this);r.setBackgroundColor(SURFACE);pad(r,8,9,8,7);
        for(int i=0;i<4;i++){
            final String target=TABS[i];boolean on=target.equals(page);LinearLayout item=col(this);item.setGravity(Gravity.CENTER);pad(item,0,5,0,5);
            FrameLayout disc=new FrameLayout(this);disc.setBackground(round(this,on?SAGE:Color.TRANSPARENT,18));disc.addView(icon(this,TAB_ICONS[i],on?FOREST:MUTED,24),new FrameLayout.LayoutParams(dp(this,24),dp(this,24),Gravity.CENTER));item.addView(disc,lp(dp(this,52),dp(this,30)));
            TextView label=text(this,TAB_NAMES[i],11,on?FOREST:MUTED,on);label.setGravity(Gravity.CENTER);item.addView(label,top(this,4));item.setContentDescription(TAB_NAMES[i]);item.setFocusable(true);item.setOnClickListener(v->navigate(target));r.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }return r;
    }
    private void heading(String title,String subtitle){space(body,9);body.addView(Ui.title(this,title,29));space(body,6);body.addView(muted(this,subtitle,14));space(body,20);}
    private void onboarding(){
        space(body,20);
        if(intro==2){permissions(true);return;}
        TextView eyebrow=text(this,"🐗  BERNARD BLOQUEUR",12,FOREST,true);body.addView(eyebrow);space(body,20);
        body.addView(title(this,intro==0?"Un peu moins d’écran.\nUn peu plus de toi.":"Tes limites.\nBernard s’en occupe.",33));space(body,12);
        body.addView(muted(this,intro==0?"Un gardien à groin pour les moments où le scroll prend un peu trop de place.":"Tu choisis un temps et des horaires. Les messages restent disponibles, même quand les Reels font une pause.",16));space(body,24);
        body.addView(image(this,intro==0?R.drawable.scene_guardian:R.drawable.scene_home,250,26));space(body,22);
        LinearLayout info=card(this);
        if(intro==0){info.addView(title(this,"C’est toi qui décides",17));space(info,8);info.addView(muted(this,"Pas de compte, pas de classement. Tes règles et tes images restent sur ce téléphone.",14));}
        else{String[] a={"Reels, Stories et Shorts : un même quota","Jeux : un temps partagé entre tes jeux","Des images de Bernard, gagnées au fil des jours"};for(String s:a){LinearLayout row=row(this);row.addView(icon(this,"check",FOREST,19));TextView t=muted(this,s,14);pad(t,10,5,0,5);row.addView(t,weight());info.addView(row);}}
        body.addView(info);space(body,24);body.addView(button(this,intro==0?"Commencer":"Choisir mes autorisations",true,()->{intro++;render();}),lp(-1,-2));space(body,12);
        TextView dots=muted(this,(intro+1)+" / 3",12);dots.setGravity(Gravity.CENTER);body.addView(dots,lp(-1,-2));
    }
    private void home(){
        heading("Bonjour "+prefs.person()+" 👋","Aujourd’hui, Bernard veille à tes côtés.");
        FrameLayout hero=new FrameLayout(this);hero.setBackground(round(this,SAGE,25));hero.setClipToOutline(true);hero.addView(image(this,R.drawable.scene_home,215,25),new FrameLayout.LayoutParams(-1,dp(this,215)));
        body.addView(hero,lp(-1,dp(this,215)));space(body,16);
        long shorts=journal.shortMs();boolean usage=PermissionUtils.hasUsageAccess(this);long games=usage?UsageUtils.todayUsageMs(this,prefs.gamePackages()):0;
        boolean compact=getResources().getConfiguration().screenWidthDp>=360&&getResources().getConfiguration().fontScale<1.25f;
        LinearLayout cards=compact?row(this):col(this);cards.setGravity(Gravity.TOP);
        LinearLayout s=counterCard(false,shorts,true),g=counterCard(true,games,usage);
        if(compact){cards.addView(s,new LinearLayout.LayoutParams(0,-1,1));LinearLayout.LayoutParams l=new LinearLayout.LayoutParams(0,-1,1);l.leftMargin=dp(this,10);cards.addView(g,l);}else{cards.addView(s,lp(-1,-2));cards.addView(g,top(this,12));}body.addView(cards,lp(-1,-2));space(body,16);
        LinearLayout calm=row(this);calm.setBackground(round(this,SAGE,22));pad(calm,13,12,13,12);calm.addView(avatar(this,59));LinearLayout t=col(this);pad(t,12,0,0,0);t.addView(title(this,"Bernard approuve tes pauses",16));space(t,5);t.addView(muted(this,"La journée est encore en cours. Un pas après l’autre.",13));calm.addView(t,weight());body.addView(calm,lp(-1,-2));space(body,14);
        boolean a=PermissionUtils.isAccessibilityEnabled(this);
        LinearLayout status=row(this);status.addView(icon(this,a?"shield":"info",a?FOREST:RUST,19));TextView tt=text(this,a?(prefs.needsUsage()&&!usage?"Protection partielle : accès d’utilisation manquant":"Bernard est actif sur ce téléphone") : "Bernard attend ses autorisations",12,a?FOREST:RUST,false);pad(tt,8,4,0,4);status.addView(tt,weight());status.addView(icon(this,"chevron",MUTED,15));status.setOnClickListener(v->navigate("permissions"));body.addView(status,lp(-1,-2));
        space(body,10);
        long detectorAge=System.currentTimeMillis()-prefs.detectorStatusAt();
        String detectorText=detectorAge<30_000L&&!prefs.detectorStatus().isEmpty()
                ? (prefs.detectorCounting()?"Compteur actif · ":"Zone autorisée · ")+prefs.detectorStatus()
                : "Diagnostic social : ouvre Instagram pour voir ce que Bernard détecte";
        LinearLayout detectorCard=row(this);detectorCard.setBackground(round(this,prefs.detectorCounting()?SAGE:SURFACE,18));pad(detectorCard,12,11,12,11);
        detectorCard.addView(icon(this,prefs.detectorCounting()?"check":"info",prefs.detectorCounting()?FOREST:MUTED,18));
        TextView detectorLabel=text(this,detectorText,12,prefs.detectorCounting()?FOREST:MUTED,prefs.detectorCounting());pad(detectorLabel,8,0,0,0);detectorCard.addView(detectorLabel,weight());body.addView(detectorCard,lp(-1,-2));
        if(prefs.tamperLock()){
            space(body,10);
            LinearLayout warning=card(this);warning.setBackground(round(this,PALE_RED,20));warning.addView(title(this,"Protection anti-contournement active",17));space(warning,7);warning.addView(muted(this,prefs.tamperReason(),13));space(warning,12);warning.addView(button(this,"Déverrouiller avec le code",false,()->navigate("settings")),lp(-1,-2));body.addView(warning,lp(-1,-2));
        }
    }
    private LinearLayout counterCard(boolean game,long used,boolean known){
        LinearLayout c=card(this);pad(c,13,15,13,15);
        String label=game?"Jeux":"Scroll infini";
        boolean enabled=game?prefs.gamesEnabled()&&!prefs.gamePackages().isEmpty():prefs.shortEnabled()&&prefs.anyShortFeature();
        int cap=game?prefs.gamesLimitMinutes():prefs.shortLimitMinutes();
        int start=game?prefs.gamesStartMinute():prefs.shortStartMinute(),end=game?prefs.gamesEndMinute():prefs.shortEndMinute();
        int minute=TimeUtils.nowMinute();
        boolean inWindow=enabled&&Rules.allowed(minute,start,end);
        boolean tamper=enabled&&prefs.tamperLock();
        boolean exhausted=known&&cap>0&&Rules.exhausted(used,cap);
        int remaining=cap<=0?-1:Rules.remainingMinutes(used,cap);

        LinearLayout head=row(this);FrameLayout disc=new FrameLayout(this);disc.setBackground(round(this,SAGE,14));disc.addView(icon(this,game?"game":"play",FOREST,20),new FrameLayout.LayoutParams(dp(this,20),dp(this,20),Gravity.CENTER));head.addView(disc,lp(dp(this,34),dp(this,34)));TextView name=title(this,label,15);pad(name,8,0,0,0);head.addView(name,weight());c.addView(head);space(c,17);

        String value,caption;
        if(!enabled){value="Désactivé";caption="protection désactivée";}
        else if(tamper){value="Verrouillé";caption="protection anti-contournement";}
        else if(!known){value="Accès requis";caption="pour lire le compteur";}
        else if(!inWindow){value="En pause";caption="jusqu’à "+Rules.clock(start);}
        else if(cap<=0){value="Illimité";caption="quota désactivé";}
        else if(exhausted){value="0 min";caption="quota atteint aujourd’hui";}
        else{value=remaining+" min";caption="restantes aujourd’hui";}

        c.addView(text(this,value,26,tamper?RUST:FOREST,true));space(c,3);c.addView(muted(this,caption,12));space(c,14);
        c.addView(progress(this,known&&enabled?used:0,cap*60_000L));space(c,7);

        String detail="";
        if(known&&enabled){
            if(cap<=0)detail="Quota illimité";
            else if(!inWindow)detail=remaining+" min restantes aujourd’hui · "+duration(used)+" utilisées";
            else detail=duration(used)+" / "+cap+" min";
        }
        c.addView(muted(this,detail,11));space(c,13);
        c.addView(text(this,window(start,end),12,MUTED,false));space(c,7);
        TextView edit=text(this,"Voir la limite  ›",12,FOREST,true);edit.setMinHeight(dp(this,32));edit.setGravity(Gravity.CENTER_VERTICAL);c.addView(edit);
        c.setOnClickListener(v->editGroup(game));c.setFocusable(true);
        String a11y=label+", "+value+", "+caption;
        if(known&&enabled&&!inWindow&&cap>0)a11y+=", "+remaining+" min restantes aujourd’hui";
        c.setContentDescription(a11y+", modifier la limite");
        return c;
    }
    private void limits(){
        heading("Mes limites","Ce que Bernard garde pour toi.");
        LinearLayout note=row(this);note.setBackground(round(this,SAGE,20));pad(note,12,12,12,12);
        ImageView i=image(this,R.drawable.scene_limits,79,14);note.addView(i,lp(dp(this,106),dp(this,79)));LinearLayout t=col(this);pad(t,12,0,0,0);t.addView(title(this,"Chacun son rythme",16));space(t,5);t.addView(muted(this,"Les règles ci-dessous sont les tiennes. Bernard les applique.",13));note.addView(t,weight());body.addView(note,lp(-1,-2));space(body,16);
        body.addView(limitCard(false),lp(-1,-2));space(body,12);body.addView(limitCard(true),lp(-1,-2));space(body,24);
        body.addView(title(this,"Applications individuelles",19));space(body,6);body.addView(muted(this,"Une règle globale bloque aussi les messages de l’application.",12));space(body,12);
        java.util.List<AppRule> list=prefs.getAppRules();
        if(list.isEmpty()){LinearLayout empty=card(this);empty.addView(muted(this,"Aucune application limitée séparément.",14));space(empty,6);empty.addView(muted(this,"Les messages restent accessibles en l’absence de règle globale.",12));body.addView(empty,lp(-1,-2));}
        else{LinearLayout c=card(this);pad(c,12,4,12,4);for(AppRule r:list){LinearLayout row=appRow(r.label,r.packageName,false);LinearLayout txt=col(this);txt.addView(title(this,r.label,15));space(txt,4);txt.addView(muted(this,!r.enabled?"Désactivée":r.alwaysBlocked?"Application bloquée":quota(r.dailyLimitMinutes)+" · "+window(r.startMinute,r.endMinute),12));row.addView(txt,weight());row.addView(icon(this,"chevron",MUTED,16));row.setOnClickListener(v->editApp(r.packageName,r.label));c.addView(row,lp(-1,-2));}body.addView(c);}
        space(body,14);body.addView(button(this,"＋  Ajouter une règle",true,()->{selectingGames=false;navigate("select");}),lp(-1,-2));
    }
    private LinearLayout limitCard(boolean game){
        LinearLayout c=card(this);LinearLayout r=row(this);r.addView(icon(this,game?"game":"play",FOREST,23));TextView h=title(this,game?"Jeux":"Scroll infini",19);pad(h,9,0,0,0);r.addView(h,weight());boolean enabled=game?prefs.gamesEnabled():prefs.shortEnabled();r.addView(pill(this,enabled?"Actif":"En pause",enabled));c.addView(r);space(c,13);
        if(!game){c.addView(muted(this,"Instagram · Facebook · YouTube Shorts · TikTok · Threads",12));space(c,8);}
        LinearLayout bottom=row(this);LinearLayout txt=col(this);txt.addView(text(this,quota(game?prefs.gamesLimitMinutes():prefs.shortLimitMinutes()),20,FOREST,true));space(txt,5);txt.addView(muted(this,window(game?prefs.gamesStartMinute():prefs.shortStartMinute(),game?prefs.gamesEndMinute():prefs.shortEndMinute()),12));if(game){space(txt,5);txt.addView(muted(this,prefs.gamePackages().size()+" applications sélectionnées",12));}bottom.addView(txt,weight());TextView b=button(this,"Modifier",false,()->editGroup(game));pad(b,15,11,15,11);bottom.addView(b);c.addView(bottom);return c;
    }
    private void editGroup(boolean game){
        draft=new Draft();draft.enabled=game?prefs.gamesEnabled():prefs.shortEnabled();draft.limit=game?prefs.gamesLimitMinutes():prefs.shortLimitMinutes();draft.start=game?prefs.gamesStartMinute():prefs.shortStartMinute();draft.end=game?prefs.gamesEndMinute():prefs.shortEndMinute();draft.packages.addAll(prefs.gamePackages());for(int n=0;n<Prefs.FEATURES.length;n++)draft.features[n]=prefs.featureEnabled(Prefs.FEATURES[n]);navigate(game?"games":"short");
    }
    private void editApp(String pkg,String label){draft=new Draft();AppRule r=prefs.getAppRule(pkg);draft.pkg=pkg;draft.label=label;draft.limit=r==null?30:r.dailyLimitMinutes;draft.start=r==null?0:r.startMinute;draft.end=r==null?0:r.endMinute;draft.enabled=r==null||r.enabled;draft.always=r!=null&&r.alwaysBlocked;navigate("individual");}
    private void editor(boolean game,boolean individual){
        if(draft==null){navigate("limits");return;}
        space(body,5);body.addView(muted(this,individual?"Cette règle concerne l’application entière.":game?"Ces applications partagent le même temps quotidien.":"Un quota commun pour les sources que tu choisis.",14));space(body,20);
        body.addView(switchCard(game?"Activer le groupe Jeux":"Activer la protection",draft.enabled,v->draft.enabled=v),lp(-1,-2));space(body,14);
        if(individual){body.addView(switchCard("Bloquer en permanence",draft.always,v->{draft.always=v;render();}),lp(-1,-2));space(body,14);}
        if(!draft.always){
            LinearLayout quotaCard=card(this);quotaCard.addView(title(this,"Temps autorisé par jour",16));space(quotaCard,16);LinearLayout step=row(this);
            TextView minus=button(this,"−",false,()->{draft.limit=Math.max(1,(draft.limit<=0?5:draft.limit)-5);render();});step.addView(minus,lp(dp(this,49),dp(this,49)));
            EditText value=new EditText(this);value.setInputType(InputType.TYPE_CLASS_NUMBER);value.setSingleLine();value.setSelectAllOnFocus(true);value.setText(draft.limit<=0?"0":String.valueOf(draft.limit));value.setTextSize(28);value.setTextColor(FOREST);value.setGravity(Gravity.CENTER);value.setBackgroundColor(Color.TRANSPARENT);value.setContentDescription("Minutes autorisées par jour");step.addView(value,new LinearLayout.LayoutParams(0,dp(this,54),1));
            TextView plus=button(this,"＋",false,()->{draft.limit=Math.min(1440,draft.limit+5);render();});step.addView(plus,lp(dp(this,49),dp(this,49)));quotaCard.addView(step);space(quotaCard,9);TextView unit=muted(this,"minutes par jour · 0 = sans quota",12);unit.setGravity(Gravity.CENTER);quotaCard.addView(unit,lp(-1,-2));
            value.addTextChangedListener(watcher(s->{try{draft.limit=Math.max(0,Math.min(1440,Integer.parseInt(s)));}catch(NumberFormatException ignored){draft.limit=0;}}));body.addView(quotaCard,lp(-1,-2));space(body,14);
            LinearLayout times=card(this);times.addView(title(this,"Plage autorisée",16));space(times,10);Switch all=new Switch(this);all.setText("Toute la journée");all.setTextColor(INK);all.setChecked(draft.start==draft.end);all.setMinHeight(dp(this,44));all.setOnCheckedChangeListener((v,yes)->{if(yes){draft.start=0;draft.end=0;}else{draft.start=game?1080:480;draft.end=game?1380:1320;}render();});times.addView(all,lp(-1,-2));
            if(draft.start!=draft.end){space(times,8);LinearLayout r=row(this);r.addView(timeField("Début",true),weight());LinearLayout.LayoutParams p=weight();p.leftMargin=dp(this,10);r.addView(timeField("Fin",false),p);times.addView(r);space(times,10);times.addView(muted(this,"La plage peut traverser minuit. L’heure de fin est exclue.",11));}body.addView(times,lp(-1,-2));space(body,20);
        }
        if(game){body.addView(title(this,"Applications sélectionnées ("+draft.packages.size()+")",17));space(body,10);if(draft.packages.isEmpty())body.addView(muted(this,"Choisis les jeux à inclure dans ce quota.",14));else for(String pkg:new ArrayList<>(draft.packages)){LinearLayout r=appRow(appLabel(pkg),pkg,false);r.addView(title(this,appLabel(pkg),14),weight());TextView remove=button(this,"×",false,()->{draft.packages.remove(pkg);render();});remove.setContentDescription("Retirer "+appLabel(pkg));r.addView(remove,lp(dp(this,46),dp(this,46)));body.addView(r,lp(-1,-2));}
            space(body,12);body.addView(button(this,"Choisir les applications",false,()->{selectingGames=true;navigate("select");}),lp(-1,-2));
        }else if(!individual){body.addView(title(this,"Sources surveillées",17));space(body,9);LinearLayout sources=card(this);pad(sources,14,3,14,3);for(int n=0;n<Prefs.FEATURES.length;n++){final int index=n;Switch s=new Switch(this);s.setText(Prefs.FEATURE_LABELS[n]);s.setTextColor(INK);s.setTextSize(14);s.setChecked(draft.features[n]);s.setMinHeight(dp(this,49));s.setOnCheckedChangeListener((x,v)->draft.features[index]=v);sources.addView(s,lp(-1,-2));}body.addView(sources);space(body,10);body.addView(muted(this,"Le fil, Explore, Reels et Stories partagent le même quota. Les messages privés et le profil restent accessibles. Un Reel ouvert depuis un message est compté.",12));}
        space(body,24);body.addView(button(this,"Enregistrer",true,()->saveDraft(game,individual)),lp(-1,-2));space(body,10);
        if(individual&&prefs.getAppRule(draft.pkg)!=null)body.addView(button(this,"Supprimer cette règle",false,()->new AlertDialog.Builder(this).setTitle("Supprimer la règle ?").setMessage(draft.label+" ne sera plus limitée séparément.").setNegativeButton("Annuler",null).setPositiveButton("Supprimer",(d,w)->{if(!requireAdminNow())return;prefs.removeAppRule(draft.pkg);journal.settingsChanged();draft=null;navigate("limits");}).show()),lp(-1,-2));
        space(body,12);TextView foot=muted(this,"🐗  Bernard fermera la barrière au bon moment.",12);foot.setGravity(Gravity.CENTER);body.addView(foot,lp(-1,-2));
    }
    private LinearLayout switchCard(String title,boolean checked,java.util.function.Consumer<Boolean> change){LinearLayout c=card(this);pad(c,15,8,15,8);Switch s=new Switch(this);s.setText(title);s.setTextColor(INK);s.setTextSize(15);s.setMinHeight(dp(this,45));s.setChecked(checked);s.setOnCheckedChangeListener((v,b)->change.accept(b));c.addView(s,lp(-1,-2));return c;}
    private View timeField(String label,boolean start){LinearLayout c=col(this);c.setBackground(round(this,PAPER,14));pad(c,13,12,13,12);c.addView(muted(this,label,12));space(c,7);c.addView(title(this,Rules.clock(start?draft.start:draft.end),20));c.setOnClickListener(v->{int t=start?draft.start:draft.end;new TimePickerDialog(this,(w,h,m)->{if(start)draft.start=h*60+m;else draft.end=h*60+m;render();},t/60,t%60,true).show();});c.setFocusable(true);c.setContentDescription(label+" "+Rules.clock(start?draft.start:draft.end));return c;}
    private void saveDraft(boolean game,boolean individual){
        if(!requireAdminNow())return;
        if(game&&draft.enabled&&draft.packages.isEmpty()){toast("Choisis au moins une application pour le groupe Jeux.");return;}
        if(!game&&!individual&&draft.enabled){boolean any=false;for(boolean v:draft.features)any|=v;if(!any){toast("Choisis au moins une source, ou désactive la protection.");return;}}
        if(individual){AppRule r=new AppRule();r.packageName=draft.pkg;r.label=draft.label;r.enabled=draft.enabled;r.alwaysBlocked=draft.always;r.dailyLimitMinutes=draft.limit;r.startMinute=draft.start;r.endMinute=draft.end;prefs.saveAppRule(r);}
        else if(game){prefs.setGamesEnabled(draft.enabled);prefs.setGamesLimitMinutes(draft.limit);prefs.setGamesStartMinute(draft.start);prefs.setGamesEndMinute(draft.end);prefs.setGamePackages(draft.packages);}
        else{prefs.setShortEnabled(draft.enabled);prefs.setShortLimitMinutes(draft.limit);prefs.setShortStartMinute(draft.start);prefs.setShortEndMinute(draft.end);for(int n=0;n<Prefs.FEATURES.length;n++)prefs.setFeature(Prefs.FEATURES[n],draft.features[n]);}
        journal.settingsChanged();draft=null;toast("Limites enregistrées 🐗");navigate("limits");
    }
    private void appPicker(){
        if(selectingGames&&draft==null){editGroup(true);return;}
        space(body,5);EditText search=new EditText(this);search.setSingleLine(true);search.setHint("Rechercher une application");search.setTextSize(15);search.setTextColor(INK);search.setBackground(round(this,SURFACE,17));pad(search,15,13,15,13);search.setMinHeight(dp(this,50));body.addView(search,lp(-1,-2));space(body,12);
        TextView count=muted(this,selectingGames?draft.packages.size()+" sélectionnées":"La règle s’appliquera à toute l’application.",13);body.addView(count);space(body,8);
        LinearLayout list=col(this);body.addView(list,lp(-1,-2));
        Runnable populate=()->{
            list.removeAllViews();String filter=search.getText().toString().toLowerCase(Locale.FRENCH);
            for(AppEntry a:launcherApps()){
                if(!a.label.toLowerCase(Locale.FRENCH).contains(filter)&&!a.pkg.contains(filter))continue;
                LinearLayout row=appRow(a.label,a.pkg,false);row.addView(title(this,a.label,15),weight());
                if(selectingGames){CheckBox box=new CheckBox(this);box.setChecked(draft.packages.contains(a.pkg));box.setContentDescription(a.label);box.setOnCheckedChangeListener((b,on)->{if(on)draft.packages.add(a.pkg);else draft.packages.remove(a.pkg);count.setText(draft.packages.size()+" sélectionnées");});row.addView(box);row.setOnClickListener(v->box.setChecked(!box.isChecked()));}
                else{row.addView(icon(this,"chevron",MUTED,18));row.setOnClickListener(v->editApp(a.pkg,a.label));}list.addView(row,lp(-1,-2));
            }
            if(list.getChildCount()==0)list.addView(muted(this,"Aucune application correspondante.",14),top(this,18));
        };
        populate.run();search.addTextChangedListener(watcher(s->populate.run()));
        if(selectingGames){space(body,15);body.addView(button(this,"Valider la sélection",true,()->navigate("games")),lp(-1,-2));}
    }
    private java.util.List<AppEntry> launcherApps(){
        if(apps!=null)return apps;apps=new ArrayList<>();PackageManager pm=getPackageManager();Intent intent=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);Set<String> seen=new HashSet<>();
        for(ResolveInfo r:pm.queryIntentActivities(intent,0)){
            if(r.activityInfo==null)continue;String pkg=r.activityInfo.packageName;
            // Keep Android settings, launcher and emergency calling available for recovery.
            if(pkg.equals(getPackageName())||pkg.equals("com.android.settings")||pkg.contains("launcher")||pkg.equals("com.google.android.dialer")||pkg.equals("com.android.dialer")||!seen.add(pkg))continue;
            apps.add(new AppEntry(String.valueOf(r.loadLabel(pm)),pkg,r.loadIcon(pm)));
        }
        apps.sort(Comparator.comparing(a->a.label.toLowerCase(Locale.FRENCH)));return apps;
    }
    private String appLabel(String pkg){try{PackageManager pm=getPackageManager();return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg,0)));}catch(PackageManager.NameNotFoundException ex){return pkg;}}
    private LinearLayout appRow(String label,String pkg,boolean unused){
        LinearLayout row=row(this);pad(row,3,10,3,10);row.setMinimumHeight(dp(this,60));ImageView im=new ImageView(this);try{im.setImageDrawable(getPackageManager().getApplicationIcon(pkg));}catch(PackageManager.NameNotFoundException e){im.setImageResource(android.R.drawable.sym_def_app_icon);}im.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams ip=lp(dp(this,36),dp(this,36));ip.rightMargin=dp(this,12);row.addView(im,ip);return row;
    }
    private void rewards(){
        heading("Les récompenses","La petite collection de "+prefs.person()+".");
        LinearLayout introCard=card(this);LinearLayout r=row(this);r.addView(icon(this,"gift",FOREST,26));LinearLayout t=col(this);pad(t,12,0,0,0);t.addView(title(this,journal.successCount()+" journée"+(journal.successCount()==1?"":"s")+" tenue"+(journal.successCount()==1?"":"s"),19));space(t,5);t.addView(muted(this,"Les images gagnées restent à toi.",13));r.addView(t,weight());introCard.addView(r);space(introCard,13);
        introCard.addView(muted(this,"Une journée n’est validée qu’après minuit, avec un suivi complet et des limites inchangées. Aujourd’hui reste en cours.",13));body.addView(introCard,lp(-1,-2));space(body,20);
        for(int i=0;i<REWARD_GOALS.length;i+=2){LinearLayout row=row(this);row.setGravity(Gravity.TOP);row.addView(rewardTile(i),weight());if(i+1<REWARD_GOALS.length){LinearLayout.LayoutParams w=weight();w.leftMargin=dp(this,12);row.addView(rewardTile(i+1),w);}else{View blank=new View(this);LinearLayout.LayoutParams w=weight();w.leftMargin=dp(this,12);row.addView(blank,w);}body.addView(row,lp(-1,-2));space(body,12);}
        space(body,8);body.addView(muted(this,"Pas de tirage aléatoire, pas d’images à acheter, pas de collection perdue après une mauvaise journée.",12));
    }
    private LinearLayout rewardTile(int index){
        boolean unlocked=journal.unlocked(REWARD_GOALS[index]);LinearLayout card=card(this);pad(card,7,7,7,12);
        FrameLayout imageHolder=new FrameLayout(this);imageHolder.setBackground(round(this,SAGE,17));imageHolder.setClipToOutline(true);ImageView img=image(this,REWARD_IMAGES[index],135,17);
        if(!unlocked){ColorMatrix cm=new ColorMatrix();cm.setSaturation(0.1f);img.setColorFilter(new ColorMatrixColorFilter(cm));img.setAlpha(0.55f);}
        imageHolder.addView(img,new FrameLayout.LayoutParams(-1,dp(this,135)));
        if(!unlocked){FrameLayout lock=new FrameLayout(this);lock.setBackground(round(this,0xDCF7F2E8,24));lock.addView(icon(this,"lock",FOREST,22),new FrameLayout.LayoutParams(dp(this,22),dp(this,22),Gravity.CENTER));imageHolder.addView(lock,new FrameLayout.LayoutParams(dp(this,43),dp(this,43),Gravity.CENTER));}
        card.addView(imageHolder,lp(-1,dp(this,135)));space(card,10);TextView label=title(this,REWARD_NAMES[index],14);pad(label,4,0,4,0);card.addView(label);space(card,5);TextView sub=text(this,unlocked?"Débloquée ✓":REWARD_GOALS[index]+(REWARD_GOALS[index]==1?" journée tenue":" journées tenues"),11,unlocked?FOREST:MUTED,unlocked);pad(sub,4,0,4,0);card.addView(sub);
        card.setFocusable(true);card.setContentDescription(REWARD_NAMES[index]+", "+sub.getText());card.setOnClickListener(v->{if(unlocked){rewardIndex=index;navigate("reward");}else toast("Cette image se débloque après "+REWARD_GOALS[index]+" journée(s) suivie(s) et respectée(s).");});return card;
    }
    private void rewardDetail(){
        int i=Math.max(0,Math.min(REWARD_GOALS.length-1,rewardIndex));if(!journal.unlocked(REWARD_GOALS[i])){navigate("rewards");return;}
        heading("Bravo "+prefs.person()+" !","Une image de Bernard, rien que pour toi.");
        ImageView image=image(this,REWARD_IMAGES[i],300,26);image.setScaleType(ImageView.ScaleType.CENTER_CROP);body.addView(image,lp(-1,dp(this,300)));space(body,24);
        body.addView(title(this,REWARD_NAMES[i],25));space(body,8);body.addView(muted(this,REWARD_GOALS[i]+" journée(s) suivie(s) et respectée(s).\nLes petites pauses font leur chemin.",15));space(body,25);
        body.addView(button(this,"Enregistrer l’image",true,()->exportImage(i)),lp(-1,-2));space(body,12);body.addView(button(this,"Retour à ma collection",false,()->navigate("rewards")),lp(-1,-2));
    }
    private void history(){
        heading("Historique","Le temps observé, sans inventer les jours manquants.");
        LinearLayout tabs=row(this);for(int n:new int[]{7,30}){TextView b=button(this,n==7?"7 jours":"30 jours",n==historyRange,()->{historyRange=n;render();});LinearLayout.LayoutParams p=weight();if(n==30)p.leftMargin=dp(this,8);tabs.addView(b,p);}body.addView(tabs);space(body,20);
        java.util.List<JSONObject> days=journal.days();int count=0,success=0;long total=0;LocalDate after=LocalDate.now().minusDays(historyRange-1);
        for(JSONObject d:days)if(d.optString("date").compareTo(after.toString())>=0&&d.optBoolean("closed")&&d.optBoolean("eligible")){count++;total+=d.optLong("shortMs");if(d.optBoolean("success"))success++;}
        LinearLayout metrics=row(this);metrics.setGravity(Gravity.TOP);metrics.addView(metric(success+"/"+count,"jours tenus\nsur jours suivis"),weight());metrics.addView(metric(String.valueOf(journal.streak()),"jours de suite\nterminés"),weight());metrics.addView(metric(count==0?"–":(total/count/60_000)+" min","moyenne\nscroll infini"),weight());body.addView(metrics,lp(-1,-2));space(body,19);
        LinearLayout c=card(this);c.addView(title(this,"Temps passé",18));space(c,8);c.addView(muted(this,"● Scroll infini     ○ Jeux",12));space(c,15);
        if(count==0){c.addView(image(this,R.drawable.scene_home,143,18));space(c,14);c.addView(title(this,"On commence ici",18));space(c,8);c.addView(muted(this,"Aucune journée complète à afficher pour le moment. Le premier jour partiel n’est pas transformé en réussite.",14));}
        else{java.util.List<JSONObject> chart=new ArrayList<>();for(int offset=6;offset>=0;offset--){String key=LocalDate.now().minusDays(offset).toString();JSONObject found=null;for(JSONObject d:days)if(key.equals(d.optString("date")))found=d;chart.add(found);}c.addView(new WeekChart(this,chart),lp(-1,dp(this,170)));}
        body.addView(c,lp(-1,-2));space(body,20);body.addView(title(this,"Journal des journées",18));space(body,8);
        DateTimeFormatter format=DateTimeFormatter.ofPattern("EEEE d MMMM",Locale.FRENCH);
        for(JSONObject d:days){if(d.optString("date").compareTo(after.toString())<0)continue;LinearLayout row=card(this);pad(row,14,13,14,13);String status=!d.optBoolean("closed")?"En cours":d.optBoolean("success")?"Respectée":!d.optBoolean("eligible")?"Suivi incomplet":"Limite dépassée";LinearLayout head=row(this);TextView dt=title(this,LocalDate.parse(d.optString("date")).format(format),14);head.addView(dt,weight());head.addView(pill(this,status,d.optBoolean("success")));row.addView(head);space(row,8);row.addView(muted(this,"Scroll infini : "+duration(d.optLong("shortMs"))+"  ·  Jeux : "+duration(d.optLong("gamesMs")),12));if(!d.optBoolean("eligible")){space(row,6);row.addView(muted(this,d.optString("note","Suivi incomplet"),11));}body.addView(row,top(this,8));}
    }
    private LinearLayout metric(String value,String caption){LinearLayout c=col(this);pad(c,3,4,3,4);TextView v=text(this,value,25,FOREST,true);v.setGravity(Gravity.CENTER);c.addView(v,lp(-1,-2));space(c,7);TextView t=muted(this,caption,11);t.setGravity(Gravity.CENTER);c.addView(t,lp(-1,-2));return c;}
    private void permissions(boolean onboard){
        if(onboard){body.addView(title(this,"Bernard a besoin\nde deux accès",29));space(body,10);body.addView(muted(this,"Tu gardes le contrôle. Chaque autorisation s’active dans les paramètres Android.",15));space(body,23);}
        else{space(body,4);body.addView(muted(this,"Ces accès font partie de la protection. Leur modification est protégée par le code Bernard.",14));space(body,20);}
        body.addView(permissionCard(false),lp(-1,-2));space(body,14);body.addView(permissionCard(true),lp(-1,-2));space(body,20);
        LinearLayout privacy=card(this);privacy.addView(title(this,"🐗  Des permissions sensibles",17));space(privacy,9);privacy.addView(muted(this,"L’accessibilité peut lire la structure des écrans et effectuer un retour arrière. Bernard utilise les identifiants d’interface pour reconnaître les zones ciblées. Il n’enregistre pas tes messages, ni tes mots de passe.",13));space(privacy,9);privacy.addView(muted(this,"Aucune permission Internet. Pas de télémétrie. L’historique reste ici, sauf si tu l’exportes toi-même.",13));body.addView(privacy,lp(-1,-2));space(body,18);
        TextView help=button(this,"Le bouton Android est grisé ?",false,()->new AlertDialog.Builder(this).setTitle("Paramètres restreints").setMessage("Pour certains APK installés manuellement : Paramètres Android › Applications › Bernard Bloqueur › menu ⋮ › Autoriser les paramètres restreints. Reviens ensuite dans Accessibilité. Cette option dépend de la version Android.").setPositiveButton("Compris",null).show());body.addView(help,lp(-1,-2));
        if(onboard){space(body,22);body.addView(button(this,"Entrer dans l’application",true,()->{prefs.setOnboardingDone(true);navigate("home");}),lp(-1,-2));space(body,9);body.addView(muted(this,"Le code administrateur a été configuré avant cette présentation. Les limites et paramètres resteront protégés.",12));}
    }
    private LinearLayout permissionCard(boolean accessibility){
        boolean enabled=accessibility?PermissionUtils.isAccessibilityEnabled(this):PermissionUtils.hasUsageAccess(this);LinearLayout c=card(this);LinearLayout row=row(this);row.addView(icon(this,accessibility?"shield":"history",FOREST,27));LinearLayout text=col(this);pad(text,12,0,0,0);text.addView(title(this,accessibility?"Accessibilité":"Données d’utilisation",17));space(text,5);text.addView(muted(this,accessibility?"Reconnaître et bloquer les fils, Explore, Reels, Stories et Shorts.":"Compter le temps des jeux et des applications.",13));row.addView(text,weight());c.addView(row);space(c,15);c.addView(button(this,enabled?"Activé ✓ · Ouvrir":"Activer",!enabled,()->{
            if(!requireAdminNow())return;
            PinGuard.authorizeSystemControl(accessibility?PinGuard.CONTROL_ACCESSIBILITY:PinGuard.CONTROL_USAGE,"");
            Intent intent=new Intent(accessibility?Settings.ACTION_ACCESSIBILITY_SETTINGS:Settings.ACTION_USAGE_ACCESS_SETTINGS);
            if(!accessibility)intent.setData(Uri.parse("package:"+getPackageName()));
            try{startActivity(intent);}catch(ActivityNotFoundException e){startActivity(new Intent(accessibility?Settings.ACTION_ACCESSIBILITY_SETTINGS:Settings.ACTION_USAGE_ACCESS_SETTINGS));}
        }),lp(-1,-2));return c;
    }
    private void settings(){
        space(body,4);
        if(prefs.tamperLock()){
            LinearLayout tamper=card(this);tamper.setBackground(round(this,PALE_RED,20));tamper.addView(title(this,"Bernard a détecté une tentative de contournement",18));space(tamper,8);tamper.addView(muted(this,prefs.tamperReason(),13));space(tamper,12);tamper.addView(button(this,"Réactiver après vérification",true,()->{
                if(!requireAdminNow())return;
                if((getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)==0&&PermissionUtils.isAdbEnabled(this)){toast("Désactive d’abord le débogage ADB.");return;}
                if(!PermissionUtils.isAutomaticTimeEnabled(this)||!PermissionUtils.isAutomaticTimeZoneEnabled(this)){toast("Réactive d’abord l’heure et le fuseau automatiques.");return;}
                if(PermissionUtils.hasBernardAccessibilityShortcut(this)){toast("Désactive d’abord le raccourci d’accessibilité de Bernard.");return;}
                prefs.clearTamperLock();
                if(FortressPolicy.isDeviceOwner(this))FortressPolicy.setFailSafeSuspended(this,false);
                toast("Protection réactivée 🐗");render();
            }),lp(-1,-2));body.addView(tamper,lp(-1,-2));space(body,16);
        }
        boolean adminActive=FortressPolicy.isAdminActive(this);
        LinearLayout adminCard=card(this);LinearLayout ah=row(this);ah.addView(icon(this,"shield",adminActive?FOREST:MUTED,24));TextView at=title(this,"Protection anti-désinstallation",17);pad(at,10,0,0,0);ah.addView(at,weight());ah.addView(pill(this,adminActive?"Active":"Inactive",adminActive));adminCard.addView(ah);space(adminCard,9);
        adminCard.addView(muted(this,adminActive?"Android exige de retirer d’abord l’administration de Bernard avant une désinstallation.":"Recommandé hors Mode Forteresse : active Bernard comme administrateur de l’appareil pour ajouter une étape système avant la désinstallation.",12));
        if(!adminActive){space(adminCard,12);adminCard.addView(button(this,"Activer la protection",true,()->{
            if(!requireAdminNow())return;
            PinGuard.authorizeSystemControl(PinGuard.CONTROL_DEVICE_ADMIN,"");
            Intent add=new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            add.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,FortressPolicy.admin(this));
            add.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,"Bernard utilise ce rôle uniquement pour rendre la désinstallation plus difficile à contourner.");
            try{startActivity(add);}catch(ActivityNotFoundException e){toast("Écran administrateur indisponible");}
        }),lp(-1,-2));}
        body.addView(adminCard,lp(-1,-2));space(body,16);

        boolean fortress=FortressPolicy.isDeviceOwner(this);
        if(fortress)FortressPolicy.apply(this);
        LinearLayout fortressCard=card(this);LinearLayout fh=row(this);fh.addView(icon(this,"shield",fortress?FOREST:MUTED,24));TextView ft=title(this,"Mode Forteresse système",17);pad(ft,10,0,0,0);fh.addView(ft,weight());fh.addView(pill(this,fortress?"Actif":"Inactif",fortress));fortressCard.addView(fh);space(fortressCard,9);
        fortressCard.addView(muted(this,fortress?"Android bloque aussi la désinstallation, le contrôle de Bernard, le mode sans échec, les nouveaux utilisateurs et les changements d’heure.":"Optionnel : nécessite de provisionner Bernard comme propriétaire de l’appareil. Une installation normale reste protégée par PIN + accessibilité, mais ne peut pas neutraliser tous les pouvoirs du propriétaire Android.",12));
        if(fortress){space(fortressCard,12);fortressCard.addView(button(this,"Relâcher les politiques Forteresse",false,()->{if(!requireAdminNow())return;if(FortressPolicy.relax(this)){toast("Politiques Forteresse relâchées");render();}else toast("Impossible de modifier les politiques");}),lp(-1,-2));}
        body.addView(fortressCard,lp(-1,-2));space(body,16);

        LinearLayout profile=card(this);LinearLayout pr=row(this);pr.addView(avatar(this,50));LinearLayout text=col(this);pad(text,12,0,0,0);text.addView(title(this,prefs.person(),22));space(text,4);text.addView(muted(this,"Un sanglier pour garder le cap.",13));pr.addView(text,weight());profile.addView(pr);space(profile,15);profile.addView(button(this,"Modifier le prénom",false,()->{EditText name=new EditText(this);name.setSingleLine();name.setText(prefs.person());name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});new AlertDialog.Builder(this).setTitle("Comment Bernard t’appelle ?").setView(name).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",(d,w)->{prefs.setPerson(name.getText().toString());render();}).show();}),lp(-1,-2));body.addView(profile,lp(-1,-2));space(body,18);
        body.addView(button(this,"Autorisations Android",false,()->navigate("permissions")),lp(-1,-2));space(body,10);body.addView(button(this,"Exporter mon journal",false,this::exportJournal),lp(-1,-2));space(body,10);body.addView(button(this,"Revoir la présentation",false,()->{intro=0;navigate("intro");}),lp(-1,-2));space(body,24);
        body.addView(title(this,"Aperçus des blocages",19));space(body,7);body.addView(muted(this,"Ces deux boutons montrent le vrai écran de blocage, sans fermer une autre application.",13));space(body,12);body.addView(button(this,"Voir « limite atteinte »",false,()->previewBlock(false)),lp(-1,-2));space(body,10);body.addView(button(this,"Voir « pas encore »",false,()->previewBlock(true)),lp(-1,-2));space(body,24);
        body.addView(switchCard("Diagnostic technique local",prefs.diagnosticMode(),v->{if(requireAdminNow())prefs.setDiagnosticMode(v);}),lp(-1,-2));space(body,10);body.addView(muted(this,"Désactivé par défaut. Journalise uniquement les identifiants techniques d’interface dans Logcat : FocusFenceDetector.",12));space(body,20);
        LinearLayout about=card(this);about.addView(title(this,"Bernard Bloqueur 0.4.1",17));space(about,8);about.addView(muted(this,"Une application personnelle et locale. Android 8 ou plus récent. Illustrations de Bernard intégrées, sans téléchargement à l’usage.",13));space(about,10);about.addView(muted(this,"« Dopamine gratuite » est une plaisanterie, pas une mesure médicale. L’application mesure du temps d’écran, pas la dopamine.",12));space(about,10);about.addView(muted(this,"Les réglages et les écrans Android capables de désactiver Bernard sont protégés par code. Les changements d’horloge, la révocation de l’accès d’utilisation, les clients sociaux alternatifs et les sites sociaux ouverts dans un navigateur sont aussi traités comme des tentatives de contournement. Sans Mode Forteresse, le propriétaire du téléphone garde des moyens système avancés. En Mode Forteresse Device Owner, Bernard peut également bloquer la désinstallation, le mode sans échec et le changement d’utilisateur ; ADB/root et une récupération physique restent des privilèges système à part.",12));space(about,12);TextView license=button(this,"Licence et crédits",false,()->new AlertDialog.Builder(this).setTitle("Licence et crédits").setMessage("Code : GPL-3.0.\nDétection adaptée des idées de Nudge et Scrolless.\nGradle Wrapper : Apache-2.0.\nBernard : illustrations et référence fournies dans cette conversation.\nToutes les notices figurent dans le projet source.").setPositiveButton("Fermer",null).show());about.addView(license,lp(-1,-2));body.addView(about,lp(-1,-2));
    }
    private void previewBlock(boolean schedule){Intent i=new Intent(this,BlockActivity.class);i.putExtra("preview",true);i.putExtra("schedule",schedule);i.putExtra(BlockActivity.EXTRA_REASON,schedule?"Les jeux sont en pause pour l’instant.":"La limite des contenus courts est atteinte.");i.putExtra("resume",schedule?"Tu pourras y revenir à "+Rules.clock(prefs.gamesStartMinute())+".":"Tu pourras revenir demain, pendant ta plage autorisée.");startActivity(i);}
    private void exportJournal(){pendingExport="journal";Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"Bernard-journal-"+LocalDate.now()+".json");startActivityForResult(i,90);}
    private void exportImage(int index){pendingExport="image:"+index;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("image/png").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"Bernard-"+REWARD_GOALS[index]+"-jours.png");startActivityForResult(i,90);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=90||result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();try(OutputStream out=getContentResolver().openOutputStream(uri)){
        if(out==null)throw new IOException("Destination inaccessible");
        if(pendingExport.equals("journal"))out.write(journal.export().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        else if(pendingExport.startsWith("image:")){int index=Integer.parseInt(pendingExport.substring(6));if(index<0||index>=REWARD_GOALS.length||!journal.unlocked(REWARD_GOALS[index]))throw new IOException("Image non débloquée");Bitmap image=BitmapFactory.decodeResource(getResources(),REWARD_IMAGES[index]);image.compress(Bitmap.CompressFormat.PNG,100,out);image.recycle();}
        toast("Fichier enregistré");
    }catch(IOException|RuntimeException e){toast("Impossible d’enregistrer : "+e.getMessage());}pendingExport="";}
    @Override public void onBackPressed(){back();}
    private void back(){
        if(page.equals("intro")){if(intro>0){intro--;render();}else if(prefs.onboardingDone())navigate("settings");else finish();return;}
        if(page.equals("select")){navigate(selectingGames?"games":"limits");return;}
        if(page.equals("short")||page.equals("games")||page.equals("individual")){draft=null;navigate("limits");return;}
        if(page.equals("reward")){navigate("rewards");return;}
        if(page.equals("home")){finish();return;}
        if(page.equals("permissions")){navigate("settings");return;}
        navigate("home");
    }
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
    private static TextWatcher watcher(java.util.function.Consumer<String> update){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){update.accept(s.toString());}public void afterTextChanged(Editable e){}};}
    private static String quota(int minutes){return minutes<=0?"Sans quota":minutes+" min / jour";}
    private static String duration(long ms){long seconds=Math.max(0,ms)/1000;return seconds<60?seconds+" s":(seconds/60)+" min";}
    private static String window(int start,int end){return start==end?"Toute la journée":Rules.clock(start)+" → "+Rules.clock(end);}
    private static final class WeekChart extends View{
        final java.util.List<JSONObject> days;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        WeekChart(Context c,java.util.List<JSONObject>days){super(c);this.days=days;setContentDescription("Temps de contenus courts et de jeux sur les sept derniers jours. Voir le journal détaillé ci-dessous.");}
        protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),bottom=h-29;long max=60_000;for(JSONObject d:days)if(d!=null)max=Math.max(max,Math.max(d.optLong("shortMs"),d.optLong("gamesMs")));float each=w/7;p.setColor(LINE);p.setStrokeWidth(1);c.drawLine(0,bottom,w,bottom,p);for(int i=0;i<7;i++){JSONObject d=days.get(i);float center=each*(i+.5f);p.setTextSize(dp(getContext(),11));p.setTextAlign(Paint.Align.CENTER);p.setColor(MUTED);String label=LocalDate.now().minusDays(6-i).format(DateTimeFormatter.ofPattern("EEEEE",Locale.FRENCH));c.drawText(label,center,h-8,p);if(d==null){c.drawText("·",center,bottom-10,p);continue;}float shortH=(bottom-14)*d.optLong("shortMs")/max,gameH=(bottom-14)*d.optLong("gamesMs")/max;p.setColor(FOREST);c.drawRoundRect(center-11,bottom-shortH,center-1,bottom,4,4,p);p.setColor(0xFFB8C4A4);c.drawRoundRect(center+2,bottom-gameH,center+12,bottom,4,4,p);}}
    }
}
