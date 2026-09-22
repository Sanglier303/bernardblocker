from pathlib import Path
import sys
root=Path(sys.argv[1]);J='app/src/main/java/com/local/focusfence/';T='app/src/androidTest/java/com/local/focusfence/ui/'
def change(rel,old,new):
 p=root/rel;s=p.read_text();assert s.count(old)==1,(rel,s.count(old));p.write_text(s.replace(old,new))
change(J+'service/FocusAccessibilityService.java',
 'if(s!=null&&s.connected){s.removeOverlay();s.requestSample();}',
 '''if(s!=null&&s.connected){
            s.removeOverlay();
            // A returned Activity may resume before the old Settings accessibility root expires.
            // Invalidate metadata, never extend the just-revoked system authorization.
            if(Build.VERSION.SDK_INT>=33)s.clearCache();
            s.requestSample();
        }''')
change(J+'service/FocusAccessibilityService.java',
 '    private boolean guardSystemScreen(String pkg,AccessibilityNodeInfo root){',
 '''    private boolean staleSystemRoot(AccessibilityNodeInfo root){
        if(root==null||root.getWindowId()<0)return false;
        boolean available=false,present=false;
        java.util.List<AccessibilityWindowInfo> snapshot=new java.util.ArrayList<>();
        try{
            if(Build.VERSION.SDK_INT>=30){
                android.util.SparseArray<java.util.List<AccessibilityWindowInfo>> displays=getWindowsOnAllDisplays();
                for(int i=0;i<displays.size();i++)snapshot.addAll(displays.valueAt(i));
            }else snapshot.addAll(getWindows());
            available=!snapshot.isEmpty();
            for(AccessibilityWindowInfo w:snapshot)if(w.getId()==root.getWindowId())present=true;
            return SystemScreenPolicy.rootWindowIsStale(available,present);
        }catch(RuntimeException unavailable){return false;/* No evidence: retain the PIN guard. */}
        finally{for(AccessibilityWindowInfo w:snapshot)w.recycle();}
    }
    private boolean guardSystemScreen(String pkg,AccessibilityNodeInfo root){''')
change(J+'service/FocusAccessibilityService.java',
 '        if(!sensitive&&!userSwitcher&&!privateSpace)return false;\n        boolean allowed=',
 '''        if(!sensitive&&!userSwitcher&&!privateSpace)return false;
        // getRootInActiveWindow can briefly be the last touched, already closed window.
        // A positive live-window snapshot must show it is absent before deferring this event.
        // We do NOT ignore merely unfocused windows (split screen), or an unavailable snapshot.
        if(staleSystemRoot(root)){requestSample();return true;}
        boolean allowed=''')
change(J+'core/SystemScreenPolicy.java','    public static boolean matchesWindow(',
 '''    /** A missing snapshot is not permission. Only positive absence proves a stale root. */
    public static boolean rootWindowIsStale(boolean snapshotAvailable,boolean rootPresent){
        return snapshotAvailable&&!rootPresent;
    }

    public static boolean matchesWindow(''')
(root/'app/src/test/java/com/local/focusfence/core/WindowSnapshotTest.java').write_text('''package com.local.focusfence.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class WindowSnapshotTest {
 @Test public void liveWindowMustStillBeGuarded(){assertFalse(SystemScreenPolicy.rootWindowIsStale(true,true));}
 @Test public void closedWindowCannotCreateAnotherPin(){assertTrue(SystemScreenPolicy.rootWindowIsStale(true,false));}
 @Test public void unavailableSnapshotDoesNotAuthorizeSettings(){assertFalse(SystemScreenPolicy.rootWindowIsStale(false,false));}
 @Test public void unknownSnapshotWithOldPresenceStillFailsClosed(){assertFalse(SystemScreenPolicy.rootWindowIsStale(false,true));}
}
''')
(root/(T+'ServiceTestSupport.java')).write_text('''package com.local.focusfence.ui;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import com.local.focusfence.service.FocusAccessibilityService;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
/** Wait for the real main-thread unbind, not the journal flag reset by another test. */
final class ServiceTestSupport {
 private ServiceTestSupport(){}
 static void awaitStopped(){
  long deadline=SystemClock.elapsedRealtime()+6000;
  do{
   AtomicBoolean connected=new AtomicBoolean(true);
   InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
    try{
     Field running=FocusAccessibilityService.class.getDeclaredField("running");running.setAccessible(true);
     Object service=((WeakReference<?>)running.get(null)).get();
     if(service==null){connected.set(false);return;}
     Field state=FocusAccessibilityService.class.getDeclaredField("connected");state.setAccessible(true);
     connected.set(state.getBoolean(service));
    }catch(ReflectiveOperationException e){throw new AssertionError(e);}
   });
   // The main-thread barrier above also waits for all writes made by onUnbind to finish.
   if(!connected.get())return;
   SystemClock.sleep(100);
  }while(SystemClock.elapsedRealtime()<deadline);
  throw new AssertionError("Accessibility service did not finish unbinding before test reset");
 }
}
''')
change(T+'ScheduleRegressionTest.java',
 '    private void waitStopped(){for(int i=0;i<50&&Journal.monitoring;i++)SystemClock.sleep(100);SystemClock.sleep(200);}',
 '    private void waitStopped(){ServiceTestSupport.awaitStopped();}')
change(T+'ScheduleRegressionTest.java','assertEquals(180000,j.shortMs());assertFalse(p.tamperLock());',
 'assertEquals(180000,j.shortMs());assertFalse(p.tamperReason(),p.tamperLock());')
change(T+'AuditRegressionTest.java',
 '            assertFalse("System grant is revoked on return",PinGuard.isSystemControlAuthorized());',
 '''            assertFalse("System grant is revoked on return",PinGuard.isSystemControlAuthorized());
            a.onActivity(x->x.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:"+x.getPackageName()))));
            await("Bernard garde les réglages");
            assertFalse("A completed admin flow must not authorize App info",PinGuard.isSystemControlAuthorized());''')
p=root/'docs/AUDIT-v0.4.6.md';s=p.read_text();s+='''

## Complément après la première CI Android (22 septembre)

Run 35723868663 : 124 tests JUnit passent et zéro erreur Lint ; Android 8 réussit 46 parcours, Android 15 et 16 échouent chacun sur un parcours. Ces échecs n’ont pas été ignorés ni relancés jusqu’à obtenir du vert.

- Android 15 : l’activation administrateur réussit, MainActivity reçoit son résultat puis reprend, mais une ancienne racine d’accessibilité Settings déclenche une nouvelle demande de PIN. Au retour dans Bernard, le cache d’accessibilité est invalidé sur API 33+. Avant une protection système, la racine est confrontée à la liste des fenêtres réellement interactives sur tous les écrans disponibles. Seule une absence positivement constatée fait rééchantillonner ; l’absence de données ne donne aucun droit. Une fenêtre simplement non focalisée reste contrôlée. Aucun délai d’autorisation supplémentaire, aucune désactivation temporaire des protections, aucun maintien du droit administrateur après le retour. Le parcours vérifie désormais également qu’ouvrir ensuite les informations de l’application redemande le PIN.
- Android 16 : une préparation de test se fondait sur un indicateur de journal déjà remis à faux par le test précédent et pouvait vider les préférences avant la fin effective de onUnbind. L’isolation attend désormais la vraie connexion du service et une barrière sur le thread principal avant de remettre les données à zéro. Les écritures de protection en production restent inchangées ; aucune assertion ni règle de sécurité n’est supprimée.
- Quatre tests purs supplémentaires contrôlent la politique de fraîcheur des fenêtres, soit 128 méthodes. Les résultats complets doivent être vérifiés sur le nouveau commit ; le résultat rouge du premier essai n’est pas présenté comme un succès final.
''';p.write_text(s)
