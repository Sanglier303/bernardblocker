package com.local.focusfence.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.Toast;
import com.local.focusfence.BuildConfig;
import com.local.focusfence.security.FortressPolicy;
import com.local.focusfence.security.PinGuard;
import org.json.JSONObject;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Private, verified self-updates. Android remains the final signature/installation authority. */
public final class UpdateManager {
    private static final String PREFS="bernard_updates_v1";
    private static final String K_AUTO="auto", K_LAST_CHECK="last_check", K_LAST_ERROR="last_error";
    private static final String K_READY_CODE="ready_code", K_READY_NAME="ready_name", K_READY_SHA="ready_sha", K_READY_PATH="ready_path";
    private static final String K_PROMPTED_CODE="prompted_code", K_WAITING_PERMISSION="waiting_install_permission";
    private static final String K_SESSION="session_id", K_SESSION_CODE="session_code";
    static final String ACTION_INSTALL_STATUS="com.local.focusfence.UPDATE_INSTALL_STATUS";
    private static final String EXTRA_VERSION_CODE="version_code";
    private static final String MANIFEST_URL="https://github.com/Sanglier303/bernardblocker/releases/latest/download/update-manifest.json";
    private static final String RELEASE_PREFIX="https://github.com/Sanglier303/bernardblocker/releases/download/v";
    private static final long INTERVAL=6L*60*60*1000, MAX_APK_BYTES=80L*1024*1024;
    private static final int JOB_ID=303042;
    private static final ExecutorService EXEC=Executors.newSingleThreadExecutor(r -> new Thread(r,"BernardUpdater"));
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final AtomicBoolean CHECKING=new AtomicBoolean(), PREPARING=new AtomicBoolean();
    private static final List<Listener> listeners=new ArrayList<>();
    private static WeakReference<Activity> foreground=new WeakReference<>(null);
    private static Intent pendingConfirmation;
    private UpdateManager() {}
    public interface Listener { void onComplete(State state); }
    public static final class State {
        public final boolean autoEnabled,checking,ready,installing;
        public final long versionCode,lastCheckAt;
        public final String versionName,lastError;
        State(boolean auto,boolean checking,boolean ready,boolean installing,long code,String name,long last,String error){
            this.autoEnabled=auto;this.checking=checking;this.ready=ready;this.installing=installing;
            this.versionCode=code;this.versionName=name;this.lastCheckAt=last;this.lastError=error;
        }
    }
    private static SharedPreferences prefs(Context c){return c.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    public static boolean autoEnabled(Context c){return prefs(c).getBoolean(K_AUTO,true);}
    public static void setAutoEnabled(Context c,boolean enabled){prefs(c).edit().putBoolean(K_AUTO,enabled).apply();schedule(c);}
    public static void schedule(Context c){
        JobScheduler scheduler=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(scheduler==null)return;
        if(BuildConfig.DEBUG||!autoEnabled(c)){scheduler.cancel(JOB_ID);return;}
        if(scheduler.getPendingJob(JOB_ID)==null){
            scheduler.schedule(new JobInfo.Builder(JOB_ID,new ComponentName(c,UpdateJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(INTERVAL)
                    .setPersisted(true).setRequiresBatteryNotLow(true).build());
        }
    }
    private static File readyFile(Context c)throws IOException {
        File dir=new File(c.getFilesDir(),"updates").getCanonicalFile();
        String path=prefs(c).getString(K_READY_PATH,"");
        if(path.isEmpty())throw new IOException("APK téléchargée introuvable");
        File file=new File(path).getCanonicalFile();
        if(!dir.equals(file.getParentFile())||!file.isFile()||file.length()<=0||file.length()>MAX_APK_BYTES)
            throw new IOException("Fichier de mise à jour invalide");
        return file;
    }
    public static State state(Context c){
        SharedPreferences p=prefs(c);long code=p.getLong(K_READY_CODE,0);
        boolean ready=false;
        if(code>BuildConfig.VERSION_CODE){try{readyFile(c);ready=true;}catch(IOException ignored){}}
        if(code>0&&code<=BuildConfig.VERSION_CODE){clearReady(c);clearSession(c);code=0;}
        return new State(autoEnabled(c),CHECKING.get(),ready,p.getInt(K_SESSION,-1)>=0,
                code,p.getString(K_READY_NAME,""),p.getLong(K_LAST_CHECK,0),p.getString(K_LAST_ERROR,""));
    }
    public static void checkAutomatically(Context c){checkAutomatically(c,null);}
    static void checkAutomatically(Context c,Listener listener){check(c,false,listener);}
    public static synchronized void check(Context c,boolean force,Listener listener){
        Context app=c.getApplicationContext();
        if(CHECKING.get()){if(listener!=null)listeners.add(listener);return;}
        long now=System.currentTimeMillis(),last=prefs(app).getLong(K_LAST_CHECK,0);
        if(!force&&(BuildConfig.DEBUG||!autoEnabled(app)||(last>0&&now>=last&&now-last<INTERVAL))){
            if(listener!=null){State current=state(app);MAIN.post(() -> listener.onComplete(current));}return;
        }
        if(listener!=null)listeners.add(listener);
        CHECKING.set(true);
        EXEC.execute(() -> {
            try{performCheck(app);}catch(Exception e){error(app,e.getMessage());}
            finally{
                prefs(app).edit().putLong(K_LAST_CHECK,System.currentTimeMillis()).apply();
                List<Listener> done;State snapshot;
                synchronized(UpdateManager.class){CHECKING.set(false);done=new ArrayList<>(listeners);listeners.clear();snapshot=state(app);}
                for(Listener l:done)MAIN.post(() -> l.onComplete(snapshot));
            }
        });
    }
    private static void performCheck(Context app)throws Exception {
        JSONObject m;
        HttpURLConnection c=openHttps(MANIFEST_URL);
        try(InputStream in=c.getInputStream()){m=new JSONObject(new String(readLimited(in,65536),StandardCharsets.UTF_8));}
        finally{c.disconnect();}
        long code=m.getLong("versionCode");String name=m.getString("versionName").trim();
        String sha=UpdatePolicy.hex(m.getString("sha256")),cert=UpdatePolicy.hex(m.getString("certificateSha256"));
        UpdatePolicy.manifest(code,name,sha,cert);
        SharedPreferences p=prefs(app);
        if(code<=BuildConfig.VERSION_CODE){clearReady(app);error(app,"");return;}
        State old=state(app);
        if(old.ready&&old.versionCode==code){verifyApk(app,readyFile(app),code,name,sha);error(app,"");return;}
        File dir=new File(app.getFilesDir(),"updates");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Dossier de mise à jour inaccessible");
        File part=new File(dir,"download.apk.part"),apk=new File(dir,"Bernard-Bloqueur-v"+name+".apk");
        try{
            download(RELEASE_PREFIX+name+"/Bernard-Bloqueur-v"+name+".apk",part);
            verifyApk(app,part,code,name,sha);
            if(apk.exists()&&!apk.delete())throw new IOException("Ancienne APK verrouillée");
            if(!part.renameTo(apk))throw new IOException("Finalisation de la mise à jour impossible");
            if(!p.edit().putLong(K_READY_CODE,code).putString(K_READY_NAME,name).putString(K_READY_SHA,sha)
                    .putString(K_READY_PATH,apk.getAbsolutePath()).putString(K_LAST_ERROR,"").remove(K_PROMPTED_CODE).commit())
                throw new IOException("État de mise à jour non enregistré");
            File[] files=dir.listFiles();if(files!=null)for(File f:files)if(!f.equals(apk)&&f.getName().endsWith(".apk"))f.delete();
        }finally{if(part.exists())part.delete();}
    }
    private static HttpURLConnection openHttps(String raw)throws IOException {
        URL url=new URL(raw);
        for(int n=0;n<6;n++){
            if(!UpdatePolicy.trustedUrl(url))throw new IOException("Destination de mise à jour non autorisée");
            HttpURLConnection c=(HttpURLConnection)url.openConnection();
            c.setConnectTimeout(12000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent","Bernard-Bloqueur/"+BuildConfig.VERSION_NAME);
            try{
                int code=c.getResponseCode();
                if(code>=300&&code<400){String next=c.getHeaderField("Location");c.disconnect();if(next==null)throw new IOException("Redirection GitHub invalide");url=new URL(url,next);continue;}
                if(code!=200)throw new IOException("GitHub répond HTTP "+code);
                return c;
            }catch(IOException e){c.disconnect();throw e;}
        }
        throw new IOException("Trop de redirections GitHub");
    }
    private static byte[] readLimited(InputStream in,int limit)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int total=0;
        long deadline=SystemClock.elapsedRealtime()+60000;
        for(int n;(n=in.read(buffer))!=-1;){total+=n;if(total>limit||SystemClock.elapsedRealtime()>deadline)throw new IOException("Manifeste trop volumineux ou transfert trop lent");out.write(buffer,0,n);}
        return out.toByteArray();
    }
    private static void download(String url,File file)throws Exception {
        HttpURLConnection c=openHttps(url);
        try{
            long expected=c.getContentLengthLong(),total=0,deadline=SystemClock.elapsedRealtime()+5*60000;
            if(expected>MAX_APK_BYTES)throw new IOException("APK trop volumineuse");
            try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(file)){
                byte[] buffer=new byte[65536];
                for(int n;(n=in.read(buffer))!=-1;){total+=n;if(total>MAX_APK_BYTES||SystemClock.elapsedRealtime()>deadline)throw new IOException("Téléchargement trop volumineux ou trop lent");out.write(buffer,0,n);}
                out.getFD().sync();
            }
            if(total<=0||(expected>=0&&total!=expected))throw new IOException("Téléchargement incomplet");
        }finally{c.disconnect();}
    }
    static void verifyApk(Context app,File file,long code,String name,String sha)throws Exception {
        UpdatePolicy.manifest(code,name,sha,UpdatePolicy.CERTIFICATE);
        if(!file.isFile()||!sha.equals(sha256(file)))throw new SecurityException("SHA-256 de l’APK incorrect");
        PackageManager pm=app.getPackageManager();
        int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo incoming=pm.getPackageArchiveInfo(file.getAbsolutePath(),flags);
        if(incoming==null)throw new SecurityException("APK illisible");
        PackageInfo installed=pm.getPackageInfo(app.getPackageName(),flags);
        long actualCode=Build.VERSION.SDK_INT>=28?incoming.getLongVersionCode():incoming.versionCode;
        UpdatePolicy.candidate(app.getPackageName(),BuildConfig.VERSION_CODE,incoming.packageName,
                actualCode,code,incoming.versionName,name,incoming.applicationInfo==null?Integer.MAX_VALUE:incoming.applicationInfo.minSdkVersion,
                Build.VERSION.SDK_INT,digests(installed),digests(incoming));
    }
    private static Set<String> digests(PackageInfo info)throws Exception {
        Signature[] signatures=Build.VERSION.SDK_INT>=28&&info.signingInfo!=null?info.signingInfo.getApkContentsSigners():info.signatures;
        if(signatures==null||signatures.length==0)throw new SecurityException("Certificat absent");
        Set<String> out=new HashSet<>();for(Signature s:signatures)out.add(hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray())));return out;
    }
    private static String sha256(File file)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] b=new byte[65536];
        try(InputStream in=new FileInputStream(file)){for(int n;(n=in.read(b))!=-1;)digest.update(b,0,n);}return hex(digest.digest());
    }
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    private static void error(Context c,String message){prefs(c).edit().putString(K_LAST_ERROR,message==null?"Erreur de mise à jour":message).apply();}
    private static void clearReady(Context c){
        try{readyFile(c).delete();}catch(IOException ignored){}
        prefs(c).edit().remove(K_READY_CODE).remove(K_READY_NAME).remove(K_READY_SHA).remove(K_READY_PATH).remove(K_PROMPTED_CODE).remove(K_WAITING_PERMISSION).apply();
    }
    private static void clearSession(Context c){prefs(c).edit().remove(K_SESSION).remove(K_SESSION_CODE).apply();pendingConfirmation=null;}
    public static boolean hasPendingSystemFlow(Context c){
        return prefs(c).getBoolean(K_WAITING_PERMISSION,false)||prefs(c).getInt(K_SESSION,-1)>=0;
    }
    public static void onBackground(Activity a){if(foreground.get()==a)foreground.clear();}
    private static boolean usable(Activity a){return a!=null&&foreground.get()==a&&!a.isFinishing()&&!a.isDestroyed();}
    public static void onForeground(Activity a){
        foreground=new WeakReference<>(a);schedule(a);
        if(PinGuard.CONTROL_UPDATE.equals(PinGuard.systemControlScope()))PinGuard.clearSystemControlAuthorization();
        SharedPreferences p=prefs(a);State s=state(a);
        // A manual update must resume even when automatic checks have been switched off.
        if(p.getBoolean(K_WAITING_PERMISSION,false)){
            p.edit().remove(K_WAITING_PERMISSION).apply();PinGuard.clearSystemControlAuthorization();
            if(canInstall(a))installReady(a,true);else error(a,"Autorisation d’installation non accordée. Réessaie avec Installer.");return;
        }
        if(pendingConfirmation!=null){launchConfirmation(a);return;}
        if(BuildConfig.DEBUG||!autoEnabled(a))return;
        if(s.ready){offer(a,s);return;}
        check(a,false,result->{if(result.ready)offer(a,result);});
    }
    private static void offer(Activity a,State s){
        if(!usable(a)||!s.ready||s.installing||prefs(a).getLong(K_PROMPTED_CODE,0)==s.versionCode)return;
        prefs(a).edit().putLong(K_PROMPTED_CODE,s.versionCode).apply();
        new AlertDialog.Builder(a).setTitle("Mise à jour Bernard "+s.versionName)
                .setMessage("APK téléchargée, empreinte et certificat Bernard contrôlés. Android peut demander une confirmation.")
                .setNegativeButton("Plus tard",null).setPositiveButton("Installer",(d,w)->installReady(a,true)).show();
    }
    private static boolean canInstall(Context c){return FortressPolicy.isDeviceOwner(c)||c.getPackageManager().canRequestPackageInstalls();}
    public static void installReady(Activity a,boolean userInitiated){
        if(!PREPARING.compareAndSet(false,true))return;
        Context app=a.getApplicationContext();SharedPreferences p=prefs(app);
        long code=p.getLong(K_READY_CODE,0);String name=p.getString(K_READY_NAME,""),sha=p.getString(K_READY_SHA,"");
        EXEC.execute(()->{
            try{
                File file=readyFile(app);verifyApk(app,file,code,name,sha);
                MAIN.post(()->{
                    if(!usable(a)){PREPARING.set(false);error(app,"Rouvre Bernard pour continuer l’installation.");return;}
                    if(!canInstall(a)){
                        try{
                            Intent i=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+a.getPackageName()));
                            authorizeUpdateFlow(a,i);p.edit().putBoolean(K_WAITING_PERMISSION,true).apply();a.startActivity(i);
                        }catch(RuntimeException e){error(app,"Autorisation d’installation indisponible");}
                        finally{PREPARING.set(false);}return;
                    }
                    EXEC.execute(()->{try{commitInstall(app,file,code);}catch(Exception e){error(app,"Installation : "+e.getMessage());}finally{PREPARING.set(false);}});
                });
            }catch(Exception e){PREPARING.set(false);error(app,"Mise à jour refusée : "+e.getMessage());MAIN.post(()->{if(usable(a))Toast.makeText(a,p.getString(K_LAST_ERROR,""),Toast.LENGTH_LONG).show();});}
        });
    }
    private static PendingIntent statusReceiver(Context c,int session,long code){
        Intent status=new Intent(c,UpdateInstallReceiver.class).setAction(ACTION_INSTALL_STATUS)
                .setData(Uri.parse("bernard-update://session/"+session)).putExtra(EXTRA_VERSION_CODE,code);
        int flags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
        return PendingIntent.getBroadcast(c,session,status,flags);
    }
    private static void commitInstall(Context c,File file,long code)throws Exception {
        PackageInstaller installer=c.getPackageManager().getPackageInstaller();SharedPreferences p=prefs(c);
        int old=p.getInt(K_SESSION,-1);PackageInstaller.SessionInfo info=old<0?null:installer.getSessionInfo(old);
        if(info!=null&&c.getPackageName().equals(info.getAppPackageName())&&p.getLong(K_SESSION_CODE,0)==code){
            // A sealed session may be recommitted after process death to recover its callback.
            try(PackageInstaller.Session session=installer.openSession(old)){session.commit(statusReceiver(c,old,code).getIntentSender());}return;
        }
        if(old>=0){try{installer.abandonSession(old);}catch(RuntimeException ignored){}clearSession(c);}
        PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(c.getPackageName());params.setSize(file.length());
        if(Build.VERSION.SDK_INT>=31)params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        int id=installer.createSession(params);
        try(PackageInstaller.Session session=installer.openSession(id)){
            try(InputStream in=new FileInputStream(file);OutputStream out=session.openWrite("base.apk",0,file.length())){
                byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);session.fsync(out);
            }
            if(!p.edit().putInt(K_SESSION,id).putLong(K_SESSION_CODE,code).commit())throw new IOException("Session non enregistrée");
            session.commit(statusReceiver(c,id,code).getIntentSender());
        }catch(Exception e){try{installer.abandonSession(id);}catch(RuntimeException ignored){}clearSession(c);throw e;}
    }
    static void handleInstallStatus(Context c,Intent intent){
        SharedPreferences p=prefs(c);int session=intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-1);
        if(!ACTION_INSTALL_STATUS.equals(intent.getAction())||session<0||session!=p.getInt(K_SESSION,-2)
                ||intent.getLongExtra(EXTRA_VERSION_CODE,-1)!=p.getLong(K_SESSION_CODE,0))return;
        int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){
            Intent confirm=Build.VERSION.SDK_INT>=33?intent.getParcelableExtra(Intent.EXTRA_INTENT,Intent.class):(Intent)intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if(confirm==null){error(c,"Confirmation Android absente");return;}
            pendingConfirmation=confirm;error(c,"Confirmation Android nécessaire : rouvre Bernard ou appuie sur Continuer l’installation.");
            Activity a=foreground.get();if(usable(a))launchConfirmation(a);return;
        }
        clearSession(c);PinGuard.clearSystemControlAuthorization();
        if(status==PackageInstaller.STATUS_SUCCESS){clearReady(c);error(c,"");return;}
        String message=intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        error(c,"Installation Android : "+(message==null?"échec "+status:message));
    }
    private static void launchConfirmation(Activity a){
        if(!usable(a)||pendingConfirmation==null)return;
        Intent intent=pendingConfirmation;
        try{authorizeUpdateFlow(a,intent);a.startActivity(intent);pendingConfirmation=null;}
        catch(RuntimeException e){error(a,"Confirmation Android indisponible : "+e.getMessage());}
    }
    private static void authorizeUpdateFlow(Context c,Intent intent){
        ResolveInfo resolved=c.getPackageManager().resolveActivity(intent,0);
        if(resolved==null||resolved.activityInfo==null)throw new SecurityException("Écran système introuvable");
        ActivityInfo ai=resolved.activityInfo;
        if((ai.applicationInfo.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))==0)
            throw new SecurityException("L’installateur doit être une application système");
        intent.setComponent(new ComponentName(ai.packageName,ai.name));
        PinGuard.authorizeSystemControl(PinGuard.CONTROL_UPDATE,ai.packageName);
    }
}
