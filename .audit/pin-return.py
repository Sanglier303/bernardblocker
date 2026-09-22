from pathlib import Path
import hashlib

def checked(path, digest):
    p = Path(path)
    assert hashlib.sha256(p.read_bytes()).hexdigest() == digest, path
    return p, p.read_text()

p,s=checked('app/src/main/java/com/local/focusfence/ui/MainActivity.java','001810fd3e7bf61ec15ad70cb6517458e53f5ff0e05aa519413509d4e8d60f9f')
before='''    @Override protected void onPause(){UpdateManager.onBackground(this);handler.removeCallbacks(refresh);handler.removeCallbacks(pinExpiryCheck);super.onPause();}
    @Override protected void onStop(){
        // Authorization is foreground-only. Leaving Bernard, opening recents or another activity
        // immediately closes the administrator session.
        if(!isChangingConfigurations())PinGuard.lockNow();
        super.onStop();
    }
'''
after='''    @Override protected void onPause(){
        // Revoke the session on departure, BEFORE a new PinActivity can authenticate.
        // Android may deliver this Activity's onStop after the PIN has already succeeded;
        // revoking there would erase the NEW grant and immediately launch another PIN.
        if(!isChangingConfigurations())PinGuard.lockNow();
        UpdateManager.onBackground(this);handler.removeCallbacks(refresh);handler.removeCallbacks(pinExpiryCheck);
        super.onPause();
    }
'''
assert s.count(before)==1
p.write_text(s.replace(before,after))

p,s=checked('app/src/androidTest/java/com/local/focusfence/ui/AuditRegressionTest.java','fbcf2cde7c69899c2f7f1b3cd9963a3cb0891436c3958d5de4f12e5b882d5caa')
extra='''
    @Test public void pauseLocksOldSessionButLateStopPreservesNewPinGrant(){
        try(ActivityScenario<MainActivity> a=launch()){
            a.onActivity(x->PinGuard.authorize());
            a.moveToState(androidx.lifecycle.Lifecycle.State.STARTED);
            assertFalse("The old session must close as soon as Main loses the foreground",PinGuard.isAuthorized());
            // Reproduce the observed ordering: Main paused, PIN succeeds, Main stops late.
            // The separate administrator journey above still enters the actual keypad.
            assertTrue(PinGuard.verify(c,new char[]{'1','1','0','9'}));
            a.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
            assertTrue("A late stop must not erase a newer successful PIN",PinGuard.isAuthorized());
            a.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
            assertTrue("The fresh grant must survive the return to Main",PinGuard.isAuthorized());
        }
    }
'''
at=s.rfind('\n}');assert at>=0
p.write_text(s[:at]+extra+s[at:])

p,s=checked('docs/AUDIT-v0.4.5.md','fc5a455f833e73090802acf727b74b463e77b634cff4691836c77f5bc85f2932')
extra='''
## Course supplémentaire de session PIN confirmée sur Android 15

Le run 35718752659 a conservé le logcat complet et l'arbre d'interface avant le nettoyage du test. Le 22 septembre 2026 à 11:00:30.835 UTC, PinActivity renvoyait vers MainActivity après validation; à 11:00:30.899, le callback onStop de l'ancienne MainActivity était traité; à 11:00:31.100, un nouvel écran PIN était créé. L'arbre confirme ce nouveau clavier vide. MainActivity.onStop appelait inconditionnellement PinGuard.lockNow, annulant donc la nouvelle autorisation au lieu de seulement fermer l'ancienne.

La révocation de la session d'administration est déplacée vers onPause, avant que l'écran PIN suivant puisse accorder une nouvelle session. Aucun allongement de délai ni exemption globale n'est ajouté; les autorisations temporaires des flux système restent distinctes. Un nouveau test instrumenté impose l'ordre pause, validation du PIN, arrêt tardif, reprise. Il vérifie à la fois la fermeture immédiate de l'ancienne session et la conservation de la nouvelle.

Une autre tentative antérieure, run 35716335674/API35, s'était interrompue après quinze minutes au début du deuxième test, sans trace suffisante pour identifier la cause. Elle n'est pas comptée comme une validation et ne constitue pas une preuve que le même défaut PIN explique cette interruption. La CI conserve désormais les journaux dès le début, y compris les buffers système, ainsi que l'état des processus. Les résultats finaux doivent toujours être lus sur le run livré.
'''
p.write_text(s+extra)
for path,want in {
    'app/src/main/java/com/local/focusfence/ui/MainActivity.java':'ff7adae817b5117857faa96cc8949ba13249050d57b31b85715eafbb86ab4556',
    'app/src/androidTest/java/com/local/focusfence/ui/AuditRegressionTest.java':'7b5943954b5846a9eb632deca2c92b6fadab43d9d8a19133ff09e9b6410debaf',
    'docs/AUDIT-v0.4.5.md':'0275afb47d4bf7766490fdd25344034c31f37da98ebe07a47d65b6755c33367b'
}.items():
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest()==want, path
    print('Verified',path)
