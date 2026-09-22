from pathlib import Path
import hashlib
for path,want in {'.github/workflows/build.yml': 'cfbda8edb60f28d15650170326d01490091567ef49e9df7286d6f80d1c592983', 'app/build.gradle': 'd603d147ee76a88c6e2ac58f3f870d0c72568dc56795f8ba6d667e267ee966c0', 'app/src/main/java/com/local/focusfence/service/FocusAccessibilityService.java': 'a04d80cff79e8db9de2400fe48f6ed75596fcf4bbe620c9ca0a52f3e3ae5bd4f', 'app/src/main/java/com/local/focusfence/storage/Prefs.java': '291d69e1335808569bf310a6145d50a131b13282cd838198ae67ab82dacc4ff7', 'app/src/main/java/com/local/focusfence/ui/MainActivity.java': '6242ab7c274d5651117c94e64fc833f5e764bb0196184da128415f20f38239b5', 'app/src/main/java/com/local/focusfence/util/DiagnosticReport.java': 'ad818c468aeec69d81adadbd6fda31a05b46be2aec8974864bde23cadda8db80'}.items():
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest()==want, "Unexpected baseline: "+path

def edit(path, before, after):
    p=Path(path); s=p.read_text(); assert s.count(before)==1,(path,s.count(before),before[:90]);p.write_text(s.replace(before,after))

service='app/src/main/java/com/local/focusfence/service/FocusAccessibilityService.java'
edit(service,'private WindowManager windows;private View overlay;private String blockedPackage="";', '''private WindowManager windows;private View overlay;private String blockedPackage="";
    private AccessEvaluator access;
    private ShortSurfaceDetector.Surface blockedSurface;
    private String overlayKey="";
    private static java.lang.ref.WeakReference<FocusAccessibilityService> running=new java.lang.ref.WeakReference<>(null);
    private final SharedPreferences.OnSharedPreferenceChangeListener ruleListener=(sp,key)->{
        if(this.connected&&Prefs.affectsProtection(key))requestSample();
    };
    /** Opening Bernard never disables enforcement, but an old overlay must not cover its editor. */
    public static void onBernardForeground(){
        FocusAccessibilityService s=running.get();
        if(s!=null&&s.connected){s.removeOverlay();s.requestSample();}
    }''')
edit(service,'registerInstalledBrowsers();\n        FortressPolicy.apply(this);','''access=new AccessEvaluator(this);running=new java.lang.ref.WeakReference<>(this);
        prefs.raw().unregisterOnSharedPreferenceChangeListener(ruleListener);
        prefs.raw().registerOnSharedPreferenceChangeListener(ruleListener);
        registerInstalledBrowsers();
        FortressPolicy.apply(this);''')
edit(service,'if(!connected)return;flush();tracking=false;', '''if(!connected)return;flush();tracking=false;
        if(prefs.detectorCounting())prefs.setDetectorStatus(prefs.detectorStatus(),false);''')
edit(service,'if(!awake){removeOverlay();return;}if(overlay!=null)return;', '''if(!awake){removeOverlay();return;}
        if(overlay!=null){
            // Re-evaluate even while the overlay covers the launcher. Rules, quotas, midnight
            // and automatic window opening must all invalidate an obsolete blocking decision.
            AccessibilityNodeInfo current=getRootInActiveWindow();
            try{
                String pkg=current==null||current.getPackageName()==null?"":current.getPackageName().toString();
                if(!pkg.equals(getPackageName())&&current!=null&&guardSystemScreen(pkg,current))return;
            }finally{if(current!=null)current.recycle();}
            AccessEvaluator.Decision fresh=access.evaluate(blockedPackage,blockedSurface);
            if(!fresh.blocked())removeOverlay();
            else{
                if(!fresh.displayKey(prefs).equals(overlayKey))showBlock(blockedPackage,blockedSurface,fresh,false);
                return;
            }
        }''')
p=Path(service);s=p.read_text();start=s.index('            int minute=TimeUtils.nowMinute();boolean usage=');end=s.index('        }catch(IllegalStateException ex)',start)
s=s[:start]+'''            ShortSurfaceDetector.Surface surface=null;
            boolean supported=prefs.shortEnabled()&&ensureDetectorSupport(pkg);
            if(supported)
                surface=detector.detect(pkg,root,activeClass(pkg,root),true,prefs.diagnosticMode());
            boolean selected=surface!=null&&prefs.shortEnabled()&&prefs.featureEnabled(surface.name());
            AccessEvaluator.Decision decision=access.evaluate(pkg,surface);
            if(supported)prefs.setDetectorStatus(detector.surfaceLabel(surface),selected&&!decision.blocked());
            if(decision.blocked()){
                if("com.instagram.android".equals(pkg)&&decision.social()){
                    // Never cover DMs for a selective social rule. The deferred verification
                    // uses the same evaluator, so changed schedules do not leave a redirect latch.
                    if(instagramRedirectPending)return;
                    instagramRedirectPending=true;instagramRedirectAt=SystemClock.elapsedRealtime();
                    access.record(pkg,surface,decision);
                    boolean redirected=detector.openInstagramMessages(root);
                    if(!redirected)redirected=openInstagramInbox();
                    if(!redirected)performGlobalAction(GLOBAL_ACTION_HOME);
                    if(SystemClock.elapsedRealtime()-lastInstagramNotice>3000){
                        lastInstagramNotice=SystemClock.elapsedRealtime();
                        Toast.makeText(this,decision.message()+" Messages Instagram accessibles.",Toast.LENGTH_SHORT).show();
                    }
                    handler.removeCallbacks(verifyInstagram);handler.postDelayed(verifyInstagram,350);
                    return;
                }
                showBlock(pkg,surface,decision,true);return;
            }
            tracking=selected;
''' +s[end:];p.write_text(s)
edit(service,'''stillBlocked=surface!=null&&prefs.shortEnabled()&&prefs.featureEnabled(surface.name())
                        &&(prefs.tamperLock()||!Rules.allowed(TimeUtils.nowMinute(),prefs.shortStartMinute(),prefs.shortEndMinute())
                        ||Rules.exhausted(journal.shortMs(),prefs.shortLimitMinutes()));''','''stillBlocked=access.evaluate(pkg,surface).blocked();''')
edit(service,'''    private void block(String pkg,String reason,String resume,boolean shortContent,boolean schedule){
        tracking=false;blockedPackage=pkg;overlayAt=SystemClock.elapsedRealtime();
        performGlobalAction(shortContent?GLOBAL_ACTION_BACK:GLOBAL_ACTION_HOME);''','''    private void showBlock(String pkg,ShortSurfaceDetector.Surface surface,AccessEvaluator.Decision decision,boolean navigateAway){
        removeOverlay();tracking=false;blockedPackage=pkg;blockedSurface=surface;overlayAt=SystemClock.elapsedRealtime();
        overlayKey=decision.displayKey(prefs);access.record(pkg,surface,decision);
        String reason=decision.message(),resume=decision.detail(prefs);
        boolean shortContent=decision.social(),schedule=decision.schedule();
        if(navigateAway)performGlobalAction(shortContent?GLOBAL_ACTION_BACK:GLOBAL_ACTION_HOME);''')
edit(service,'''connected=false;tracking=false;handler.removeCallbacksAndMessages(null);queued=false;''','''connected=false;tracking=false;handler.removeCallbacksAndMessages(null);queued=false;
        if(prefs!=null)prefs.raw().unregisterOnSharedPreferenceChangeListener(ruleListener);
        removeOverlay();if(running.get()==this)running.clear();''')
edit(service,'''connected=false;tracking=false;handler.removeCallbacksAndMessages(null);removeOverlay();try{''','''connected=false;tracking=false;handler.removeCallbacksAndMessages(null);
        if(prefs!=null)prefs.raw().unregisterOnSharedPreferenceChangeListener(ruleListener);
        if(running.get()==this)running.clear();removeOverlay();try{''')

prefs='app/src/main/java/com/local/focusfence/storage/Prefs.java'
edit(prefs,'    private int boundedInt(String key,int fallback,int maximum) {','''    /** Keys that alter enforcement, excluding journal/diagnostic writes to prevent self-trigger loops. */
    public static boolean affectsProtection(String key){
        return key==null||key.equals(K_APP_RULES)||key.startsWith("games_")
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

    private int boundedInt(String key,int fallback,int maximum) {''')

main='app/src/main/java/com/local/focusfence/ui/MainActivity.java'
edit(main,'import com.local.focusfence.core.Rules;', '''import com.local.focusfence.core.Rules;
import com.local.focusfence.core.RuleInput;
import com.local.focusfence.service.FocusAccessibilityService;''')
edit(main,'boolean enabled=true,always=false;int limit,start,end;String pkg="",label="";','''boolean enabled=true,always=false,allDay=false;int limit,start,end;String pkg="",label="",limitText=null;
        int savedStart=480,savedEnd=1320;
        void setAllDay(boolean value){
            if(value&&!allDay){savedStart=start;savedEnd=end;}
            if(!value&&allDay){start=savedStart;end=savedEnd;}
            allDay=value;
        }''')
edit(main,'''o.put("enabled",enabled).put("always",always).put("limit",limit)''','''o.put("enabled",enabled).put("always",always).put("allDay",allDay).put("savedStart",savedStart).put("savedEnd",savedEnd).put("limitText",limitText).put("limit",limit)''')
edit(main,'''d.end=o.optInt("end");d.pkg=o.optString("pkg");''','''d.end=o.optInt("end");d.allDay=o.optBoolean("allDay",d.start==d.end);d.savedStart=o.optInt("savedStart",480);d.savedEnd=o.optInt("savedEnd",1320);d.limitText=o.has("limitText")?o.optString("limitText"):null;d.pkg=o.optString("pkg");''')
edit(main,'super.onResume();pinPromptInFlight=false;','super.onResume();FocusAccessibilityService.onBernardForeground();pinPromptInFlight=false;')
edit(main,'''else if(!inWindow){value="En pause";caption="jusqu’à "+Rules.clock(start);}
        else if(cap<=0){value="Illimité";caption="quota désactivé";}
        else if(exhausted){value="0 min";caption="quota atteint aujourd’hui";}''','''else if(exhausted){value="0 min";caption="quota atteint aujourd’hui";}
        else if(!inWindow){value="En pause";caption="plage fermée jusqu’à "+Rules.clock(start);}
        else if(cap<=0){value="Illimité";caption="quota désactivé";}''')
# Preserve the existing accessibility text expected by the older UI regression test.
edit(main,'''else if(!inWindow){value="En pause";caption="plage fermée jusqu’à "+Rules.clock(start);}''','''else if(!inWindow){value="En pause";caption="jusqu’à "+Rules.clock(start);}''')
edit(main,'''draft.end=game?prefs.gamesEndMinute():prefs.shortEndMinute();draft.packages''','''draft.end=game?prefs.gamesEndMinute():prefs.shortEndMinute();draft.allDay=draft.start==draft.end;draft.savedStart=game?1080:480;draft.savedEnd=game?1380:1320;draft.packages''')
edit(main,'''draft.end=r==null?0:r.endMinute;draft.enabled''','''draft.end=r==null?0:r.endMinute;draft.allDay=draft.start==draft.end;draft.enabled''')
edit(main,'''draft.limit=Math.max(1,(draft.limit<=0?5:draft.limit)-5);render();''','''draft.limit=Math.max(1,(draft.limit<=0?5:draft.limit)-5);draft.limitText=null;render();''')
edit(main,'''value.setText(draft.limit<=0?"0":String.valueOf(draft.limit));''','''value.setText(draft.limitText==null?String.valueOf(draft.limit):draft.limitText);''')
edit(main,'''draft.limit=Math.min(1440,draft.limit+5);render();''','''draft.limit=Math.min(1440,draft.limit+5);draft.limitText=null;render();''')
edit(main,'''value.addTextChangedListener(watcher(s->{try{draft.limit=Math.max(0,Math.min(1440,Integer.parseInt(s)));}catch(NumberFormatException ignored){draft.limit=0;}}));''','''value.addTextChangedListener(watcher(s->{draft.limitText=s;int parsed=RuleInput.quota(s);if(parsed>=0)draft.limit=parsed;}));''')
edit(main,'''all.setChecked(draft.start==draft.end);''','''all.setChecked(draft.allDay);''')
edit(main,'''all.setOnCheckedChangeListener((v,yes)->{if(yes){draft.start=0;draft.end=0;}else{draft.start=game?1080:480;draft.end=game?1380:1320;}render();});''','''all.setOnCheckedChangeListener((v,yes)->{draft.setAllDay(yes);render();});''')
edit(main,'''if(draft.start!=draft.end){space(times,8);''','''if(!draft.allDay){space(times,8);''')
edit(main,'''"La plage peut traverser minuit. L’heure de fin est exclue."''','''"La plage peut traverser minuit. L’heure de fin est exclue. Modifier les horaires ne remet pas le quota quotidien à zéro."''')
edit(main,'''        if(!requireAdminNow())return;
        if(game&&draft.enabled''','''        if(!requireAdminNow())return;
        if(draft==null)return;
        if(!draft.always&&draft.limitText!=null&&RuleInput.quota(draft.limitText)<0){toast("Entre un nombre de 0 à 1440 minutes. Seul 0 signifie sans quota.");return;}
        if(!draft.always&&!RuleInput.validWindow(draft.allDay,draft.start,draft.end)){toast("Choisis des heures différentes, ou active Toute la journée.");return;}
        if(draft.allDay||draft.always){draft.start=0;draft.end=0;}
        if(game&&draft.enabled''')
edit(main,'''        else if(game){prefs.setGamesEnabled(draft.enabled);prefs.setGamesLimitMinutes(draft.limit);prefs.setGamesStartMinute(draft.start);prefs.setGamesEndMinute(draft.end);prefs.setGamePackages(draft.packages);}
        else{prefs.setShortEnabled(draft.enabled);prefs.setShortLimitMinutes(draft.limit);prefs.setShortStartMinute(draft.start);prefs.setShortEndMinute(draft.end);for(int n=0;n<Prefs.FEATURES.length;n++)prefs.setFeature(Prefs.FEATURES[n],draft.features[n]);}''','''        else prefs.saveGroup(game,draft.enabled,draft.limit,draft.start,draft.end,draft.packages,draft.features);''')
# A whole-app rule or games membership may intentionally also apply to Instagram, including DMs.
edit(main,'''        if(prefs.tamperLock()){
            space(body,10);''','''        if(!prefs.getAppRules().isEmpty()){
            space(body,10);body.addView(muted(this,"Les règles d’application entière s’ajoutent aux quotas affichés. Vérifie aussi Mes limites si une application reste bloquée.",12));
        }
        if(prefs.tamperLock()){
            space(body,10);''')

report='app/src/main/java/com/local/focusfence/util/DiagnosticReport.java'
edit(report,'''                    .put("tamperLocked",p.tamperLock())''','''                    .put("lastObservedBlock",new JSONObject(p.raw().getString("last_block_v45","{}")))
                    .put("tamperLocked",p.tamperLock())''')
edit('app/build.gradle',"versionCode 11\n        versionName '0.4.4'","versionCode 12\n        versionName '0.4.5'")
edit('.github/workflows/build.yml','api: [26, 35]','api: [26, 35, 36]')
edit('.github/workflows/build.yml','Android API 26/35 validés','Android API 26/35/36 validés')
edit('.github/workflows/build.yml','docs/AUDIT-v0.4.4.md pour','docs/AUDIT-v0.4.5.md pour')
changelog=Path('CHANGELOG.md')
assert changelog.read_text().startswith('## 0.4.4\n'), 'Unexpected changelog'
changelog.write_text("## 0.4.5\n\n- Réévalue les écrans de blocage après modification des règles et à l'ouverture naturelle d'une plage.\n- Partage les décisions entre le blocage initial, les overlays et la vérification différée Instagram.\n- Ne confond plus durée vide/invalide et quota illimité, ni heures provisoirement égales et journée entière.\n- Sauvegarde les groupes en une transaction sans remettre les compteurs à zéro.\n- Conserve les autres règles et le verrou anti-contournement lors d'une modification de plage.\n- Corrige le diagnostic de comptage et ajoute le dernier motif de blocage à l'export local.\n- Étend la matrice de tests Android à l'API 36; détails dans docs/AUDIT-v0.4.5.md.\n\n" + changelog.read_text())

for path,want in {'.github/workflows/build.yml': 'd3982cd71c7ff8dd921f1373c6282fb294fadcc1d3eb09a60a9b9fc99343dfd9', 'app/build.gradle': 'aa9f65938b5e3eb77c93726c035a1d690930db3733515ed34d7ba67192160bbd', 'app/src/main/java/com/local/focusfence/service/FocusAccessibilityService.java': 'c7a5a81d2d41f41b0c5c040a8d4363d940bcfd67d2b193e8b16038349ed8efb0', 'app/src/main/java/com/local/focusfence/storage/Prefs.java': '66cfcd7afb3b671a225d554da417866b050ee1aebf3429d6aae86f48ab0d4078', 'app/src/main/java/com/local/focusfence/ui/MainActivity.java': '001810fd3e7bf61ec15ad70cb6517458e53f5ff0e05aa519413509d4e8d60f9f', 'app/src/main/java/com/local/focusfence/util/DiagnosticReport.java': '2ad5e4a044b4668606190f9947791b8d0e8d9db2a3e9ac525c7f4dc621600267', 'app/src/androidTest/java/com/local/focusfence/ui/ScheduleRegressionTest.java': '23dbc51a4b7de54408d9536e991fde2cbb934a39a7f9adb75b18d0202a75cc93', 'app/src/main/java/com/local/focusfence/core/AccessPolicy.java': '0356f4901f0afb2ece37c0d81b8e7c8160f50699c1fecf0b87790b9b354f6b60', 'app/src/main/java/com/local/focusfence/core/RuleInput.java': '6371eee77efae76a59ff4e4af90f9a1e76afbc5b74c3e35d22046d8572ca74de', 'app/src/main/java/com/local/focusfence/util/AccessEvaluator.java': '6e18d888a6cb4ee881663a4a59b1212a1a5e5a295ef1be5cff759771d5aaed01', 'app/src/test/java/com/local/focusfence/core/AccessPolicyTest.java': 'fe9f9a45904673e9316263de29294b9e668698e84ec14086a771c61f948cd6f9', 'app/src/test/java/com/local/focusfence/core/RuleInputTest.java': 'd31326a6c0434ce0adaa43a62a2e8e41afa175efcd8192afaa6d847fda69f657', 'docs/AUDIT-v0.4.5.md': 'fc5a455f833e73090802acf727b74b463e77b634cff4691836c77f5bc85f2932', 'CHANGELOG.md': '2841390584d4fba5562020b6a8007625a6db46198623ef104ebf35ae15aaf859'}.items():
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest()==want, "Output mismatch: "+path
    print('Verified',path,want)
