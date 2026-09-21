package com.local.focusfence.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import com.local.focusfence.BuildConfig;
import com.local.focusfence.security.PinGuard;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Signed GitHub updater. */
public final class UpdateManager {
    private static final String PREFS="bernard_updates_v1";
    private static final String K_AUTO="auto";
    private static final String K_LAST_CHECK="last_check";
    private static final String K_LAST_ERROR="last_error";
    private static final String K_READY_CODE="ready_code";
    private static final String K_READY_NAME="ready_name";
    private static final String K_READY_SHA="ready_sha";
    private static final String K_READY_PATH="ready_path";
    private static final String K_PROMPTED_CODE="prompted_code";
    private static final String K_WAITING_PERMISSION="waiting_install_permission";
    private static final long CHECK_INTERVAL_MS=6L*60L*60L*1000L;
    private static final long MAX_APK_BYTES=80L*1024L*1024L;
    private static final int MAX_MANIFEST_BYTES=64*1024;
    private static final String MANIFEST_URL="https://github.com/Sanglier303/bernardblocker/releases/latest/download/update-manifest.json";
    private static final String RELEASE_PREFIX="https://github.com/Sanglier303/bernardblocker/releases/download/v";
    private static final String EXPECTED_CERT_SHA256="8d62697bb934eb9fbaaff5a92c954575a036e8bf54a03988d4ddc6287055eecc";
    private static final String ACTION_INSTALL_STATUS="com.local.focusfence.UPDATE_INSTALL_STATUS";
    static final String EXTRA_VERSION_CODE="version_code";

    private static final ExecutorService EXEC=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"BernardUpdater");t.setDaemon(true);return t;
    });
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final AtomicBoolean CHECKING=new AtomicBoolean(false);

    private UpdateManager(){}

    public interface Listener{void onComplete(State state);}

    public static final class State{
        public final boolean autoEnabled,checking,ready;
        public final long versionCode,lastCheckAt;
        public final String versionName,lastError;
        State(boolean autoEnabled,boolean checking,boolean ready,long versionCode,String versionName,long lastCheckAt,String lastError){
            this.autoEnabled=autoEnabled;this.checking=checking;this.ready=ready;this.versionCode=versionCode;
            this.versionName=versionName==null?"":versionName;this.lastCheckAt=lastCheckAt;this.lastError=lastError==null?"":lastError;
        }
    }

    private static SharedPreferences prefs(Context c){
        return c.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }

    public static boolean autoEnabled(Context c){return prefs(c).getBoolean(K_AUTO,true);}
    public static void setAutoEnabled(Context c,boolean enabled){prefs(c).edit().putBoolean(K_AUTO,enabled).apply();}

    public static State state(Context c){
        Context app=c.getApplicationContext();SharedPreferences p=prefs(app);
        long code=p.getLong(K_READY_CODE,0L);String path=p.getString(K_READY_PATH,"");
        boolean ready=code>BuildConfig.VERSION_CODE&&!path.isEmpty()&&new File(path).isFile();
        if(code>0&&!ready){clearReady(app,false);code=0;}
        return new State(autoEnabled(app),CHECKING.get(),ready,code,p.getString(K_READY_NAME,""),
                p.getLong(K_LAST_CHECK,0L),p.getString(K_LAST_ERROR,""));
    }

    public static void checkAutomatically(Context c){
        if(BuildConfig.DEBUG||!autoEnabled(c))return;
        check(c,false,null);
    }

    public static void check(Context c,boolean force,Listener listener){
        Context app=c.getApplicationContext();
        if(!force&&(BuildConfig.DEBUG||!autoEnabled(app))){deliver(app,listener);return;}
        long now=System.currentTimeMillis(),last=prefs(app).getLong(K_LAST_CHECK,0L);
        if(!force&&last>0&&now>=last&&now-last<CHECK_INTERVAL_MS){deliver(app,listener);return;}
        if(!CHECKING.compareAndSet(false,true)){deliver(app,listener);return;}
        EXEC.execute(()->{
            try{performCheck(app);}
            catch(Exception e){
                String message=e.getMessage();
                if(message==null||message.trim().isEmpty())message=e.getClass().getSimpleName();
                prefs(app).edit().putString(K_LAST_ERROR,message).putLong(K_LAST_CHECK,System.currentTimeMillis()).apply();
            }finally{
                CHECKING.set(false);deliver(app,listener);
            }
        });
    }

    private static void performCheck(Context app)throws Exception{
        JSONObject manifest=fetchManifest();
        long code=manifest.getLong("versionCode");
        String name=manifest.getString("versionName").trim();
        String sha=normalizeHex(manifest.getString("sha256"));
        String cert=normalizeHex(manifest.getString("certificateSha256"));
        if(code<=0||!safeVersion(name)||sha.length()!=64||cert.length()!=64)throw new IOException("Manifest de mise à jour invalide");
        if(!EXPECTED_CERT_SHA256.equals(cert))throw new SecurityException("Certificat de release inattendu");
        SharedPreferences p=prefs(app);
        p.edit().putLong(K_LAST_CHECK,System.currentTimeMillis()).putString(K_LAST_ERROR,"").apply();

        if(code<=BuildConfig.VERSION_CODE){clearReady(app,true);return;}

        State existing=state(app);
        if(existing.ready&&existing.versionCode==code){
            File f=new File(p.getString(K_READY_PATH,""));
            verifyApk(app,f,code,name,sha,cert);return;
        }

        File dir=new File(app.getFilesDir(),"updates");
        if(!dir.exists()&&!dir.mkdirs())throw new IOException("Impossible de créer le dossier de mise à jour");
        String apkUrl=RELEASE_PREFIX+name+"/Bernard-Bloqueur-v"+name+".apk";
        File part=new File(dir,"Bernard-Bloqueur-v"+name+".apk.part");
        File apk=new File(dir,"Bernard-Bloqueur-v"+name+".apk");
        if(part.exists())part.delete();
        download(apkUrl,part);
        verifyApk(app,part,code,name,sha,cert);
        if(apk.exists()&&!apk.delete())throw new IOException("Ancienne APK de mise à jour verrouillée");
        if(!part.renameTo(apk))throw new IOException("Impossible de finaliser l’APK téléchargée");
        cleanupOtherApks(dir,apk);
        p.edit().putLong(K_READY_CODE,code).putString(K_READY_NAME,name).putString(K_READY_SHA,sha)
                .putString(K_READY_PATH,apk.getAbsolutePath()).putString(K_LAST_ERROR,"")
                .remove(K_PROMPTED_CODE).apply();
    }

    private static JSONObject fetchManifest()throws Exception{
        HttpURLConnection c=openHttps(MANIFEST_URL);
        try(InputStream in=c.getInputStream()){
            byte[] data=readLimited(in,MAX_MANIFEST_BYTES);
            return new JSONObject(new String(data,StandardCharsets.UTF_8));
        }finally{c.disconnect();}
    }

    private static void download(String url,File destination)throws Exception{
        HttpURLConnection c=openHttps(url);
        try{
            long length=c.getContentLengthLong();
            if(length>MAX_APK_BYTES)throw new IOException("APK de mise à jour anormalement volumineuse");
            long total=0;byte[] buffer=new byte[64*1024];
            try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(destination)){
                for(int n;(n=in.read(buffer))!=-1;){
                    total+=n;if(total>MAX_APK_BYTES)throw new IOException("APK de mise à jour trop volumineuse");
                    out.write(buffer,0,n);
                }
            }
            if(total<=0)throw new IOException("Téléchargement de mise à jour vide");
        }finally{c.disconnect();}
    }

    private static HttpURLConnection openHttps(String raw)throws IOException{
        URL current=new URL(raw);
        for(int redirect=0;redirect<6;redirect++){
            if(!"https".equalsIgnoreCase(current.getProtocol()))throw new IOException("Redirection de mise à jour non HTTPS refusée");
            HttpURLConnection c=(HttpURLConnection)current.openConnection();
            c.setConnectTimeout(12_000);c.setReadTimeout(30_000);c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent","Bernard-Bloqueur/"+BuildConfig.VERSION_NAME);
            c.setRequestProperty("Accept","application/octet-stream, application/json");
            int code=c.getResponseCode();
            if(code>=300&&code<400){
                String location=c.getHeaderField("Location");c.disconnect();
                if(location==null||location.isEmpty())throw new IOException("Redirection GitHub invalide");
                current=new URL(current,location);continue;
            }
            if(code!=HttpURLConnection.HTTP_OK){c.disconnect();throw new IOException("GitHub répond HTTP "+code);}
            return c;
        }
        throw new IOException("Trop de redirections GitHub");
    }

    private static byte[] readLimited(InputStream in,int max)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int total=0;
        for(int n;(n=in.read(buffer))!=-1;){total+=n;if(total>max)throw new IOException("Manifest de mise à jour trop volumineux");out.write(buffer,0,n);}
        return out.toByteArray();
    }

    private static void verifyApk(Context app,File apk,long expectedCode,String expectedName,String expectedSha,String expectedCert)throws Exception{
        if(apk==null||!apk.isFile())throw new IOException("APK téléchargée introuvable");
        if(!expectedSha.equals(sha256(apk)))throw new SecurityException("SHA-256 de l’APK incorrect");
        if(!EXPECTED_CERT_SHA256.equals(expectedCert))throw new SecurityException("Certificat déclaré incorrect");

        PackageManager pm=app.getPackageManager();
        PackageInfo archive=archiveInfo(pm,apk);
        if(archive==null)throw new SecurityException("APK téléchargée illisible");
        if(!app.getPackageName().equals(archive.packageName))throw new SecurityException("Package de mise à jour incorrect");
        long archiveCode=Build.VERSION.SDK_INT>=28?archive.getLongVersionCode():archive.versionCode;
        if(archiveCode!=expectedCode||archiveCode<=BuildConfig.VERSION_CODE)throw new SecurityException("versionCode de mise à jour incorrect");
        if(archive.versionName==null||!expectedName.equals(archive.versionName))throw new SecurityException("Nom de version de mise à jour incorrect");

        Set<String> current=signingDigests(installedInfo(pm,app.getPackageName()));
        Set<String> incoming=signingDigests(archive);
        if(current.size()!=1||!current.contains(EXPECTED_CERT_SHA256))
            throw new SecurityException("Cette installation de Bernard n’utilise pas la signature officielle");
        if(incoming.size()!=1||!incoming.contains(EXPECTED_CERT_SHA256))
            throw new SecurityException("Signature de la mise à jour refusée");
    }

    private static PackageInfo installedInfo(PackageManager pm,String pkg)throws PackageManager.NameNotFoundException{
        if(Build.VERSION.SDK_INT>=33)return pm.getPackageInfo(pkg,PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES));
        return pm.getPackageInfo(pkg,Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES);
    }

    private static PackageInfo archiveInfo(PackageManager pm,File apk){
        if(Build.VERSION.SDK_INT>=33)return pm.getPackageArchiveInfo(apk.getAbsolutePath(),PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES));
        return pm.getPackageArchiveInfo(apk.getAbsolutePath(),Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES);
    }

    private static Set<String> signingDigests(PackageInfo info)throws Exception{
        Signature[] signatures;
        if(Build.VERSION.SDK_INT>=28&&info.signingInfo!=null)signatures=info.signingInfo.getApkContentsSigners();
        else signatures=info.signatures;
        if(signatures==null||signatures.length==0)throw new SecurityException("APK sans certificat");
        Set<String> out=new HashSet<>();
        for(Signature signature:signatures){
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            out.add(hex(md.digest(signature.toByteArray())));
        }
        return out;
    }

    private static String sha256(File file)throws Exception{
        MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] b=new byte[64*1024];
        try(InputStream in=new FileInputStream(file)){for(int n;(n=in.read(b))!=-1;)md.update(b,0,n);}
        return hex(md.digest());
    }

    private static String hex(byte[] bytes){
        StringBuilder b=new StringBuilder(bytes.length*2);
        for(byte x:bytes)b.append(String.format(Locale.ROOT,"%02x",x&0xff));
        return b.toString();
    }
    private static String normalizeHex(String value){return value==null?"":value.replace(":","").trim().toLowerCase(Locale.ROOT);}
    private static boolean safeVersion(String name){return name!=null&&name.matches("[0-9]+\\.[0-9]+\\.[0-9]+");}

    private static void cleanupOtherApks(File dir,File keep){
        File[] files=dir.listFiles();if(files==null)return;
        for(File f:files)if(!f.equals(keep)&&(f.getName().endsWith(".apk")||f.getName().endsWith(".part")))f.delete();
    }

    private static void clearReady(Context c,boolean deleteFile){
        SharedPreferences p=prefs(c);String path=p.getString(K_READY_PATH,"");
        if(deleteFile&&!path.isEmpty())new File(path).delete();
        p.edit().remove(K_READY_CODE).remove(K_READY_NAME).remove(K_READY_SHA).remove(K_READY_PATH)
                .remove(K_PROMPTED_CODE).remove(K_WAITING_PERMISSION).apply();
    }

    private static void deliver(Context app,Listener listener){
        if(listener==null)return;MAIN.post(()->listener.onComplete(state(app)));
    }

    public static void onForeground(Activity activity){
        if(BuildConfig.DEBUG||!autoEnabled(activity))return;
        if(maybeContinuePendingInstall(activity))return;
        State current=state(activity);
        if(current.ready){maybeOfferReadyUpdate(activity,current);return;}
        check(activity,false,result->{if(result.ready)maybeOfferReadyUpdate(activity,result);});
    }

    private static void maybeOfferReadyUpdate(Activity activity,State state){
        if(activity.isFinishing()||!state.ready)return;
        SharedPreferences p=prefs(activity);
        if(p.getLong(K_PROMPTED_CODE,0L)==state.versionCode)return;
        p.edit().putLong(K_PROMPTED_CODE,state.versionCode).apply();
        new AlertDialog.Builder(activity)
                .setTitle("Mise à jour Bernard "+state.versionName)
                .setMessage("La nouvelle APK a été téléchargée et sa signature Bernard a été vérifiée. Android peut encore demander une confirmation d’installation.")
                .setNegativeButton("Plus tard",null)
                .setPositiveButton("Installer",(d,w)->installReady(activity,true))
                .show();
    }

    public static void installReady(Activity activity,boolean userInitiated){
        State s=state(activity);
        if(!s.ready){Toast.makeText(activity,"Aucune mise à jour prête",Toast.LENGTH_SHORT).show();return;}
        if(Build.VERSION.SDK_INT>=26&&!activity.getPackageManager().canRequestPackageInstalls()){
            requestInstallPermission(activity,userInitiated);return;
        }
        Context app=activity.getApplicationContext();SharedPreferences p=prefs(app);
        String path=p.getString(K_READY_PATH,""),sha=p.getString(K_READY_SHA,""),name=p.getString(K_READY_NAME,"");
        long code=p.getLong(K_READY_CODE,0L);
        EXEC.execute(()->{
            try{
                verifyApk(app,new File(path),code,name,sha,EXPECTED_CERT_SHA256);
                commitInstall(app,new File(path),code);
            }catch(Exception e){
                String message=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
                p.edit().putString(K_LAST_ERROR,"Installation refusée : "+message).apply();
                MAIN.post(()->Toast.makeText(activity,"Mise à jour refusée : "+message,Toast.LENGTH_LONG).show());
            }
        });
    }

    private static void requestInstallPermission(Activity activity,boolean userInitiated){
        try{
            Intent i=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName()));
            authorizeUpdateFlow(activity,i);
            prefs(activity).edit().putBoolean(K_WAITING_PERMISSION,true).apply();
            activity.startActivity(i);
        }catch(Exception e){
            if(userInitiated)Toast.makeText(activity,"Android ne propose pas l’autorisation d’installation pour Bernard.",Toast.LENGTH_LONG).show();
        }
    }

    private static boolean maybeContinuePendingInstall(Activity activity){
        SharedPreferences p=prefs(activity);
        if(!p.getBoolean(K_WAITING_PERMISSION,false))return false;
        if(Build.VERSION.SDK_INT<26||activity.getPackageManager().canRequestPackageInstalls()){
            p.edit().putBoolean(K_WAITING_PERMISSION,false).apply();
            installReady(activity,false);return true;
        }
        return false;
    }

    private static void commitInstall(Context app,File apk,long code)throws Exception{
        PackageInstaller installer=app.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(app.getPackageName());
        if(Build.VERSION.SDK_INT>=31)params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        int sessionId=installer.createSession(params);
        PackageInstaller.Session session=null;
        try{
            session=installer.openSession(sessionId);
            try(InputStream in=new FileInputStream(apk);OutputStream out=session.openWrite("base.apk",0,apk.length())){
                byte[] buffer=new byte[64*1024];
                for(int n;(n=in.read(buffer))!=-1;)out.write(buffer,0,n);
                session.fsync(out);
            }
            Intent status=new Intent(app,UpdateInstallReceiver.class).setAction(ACTION_INSTALL_STATUS).putExtra(EXTRA_VERSION_CODE,code);
            int flags=PendingIntent.FLAG_UPDATE_CURRENT;
            if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
            PendingIntent pending=PendingIntent.getBroadcast(app,sessionId,status,flags);
            session.commit(pending.getIntentSender());
        }catch(Exception e){
            try{installer.abandonSession(sessionId);}catch(Exception ignored){}
            throw e;
        }finally{if(session!=null)session.close();}
    }

    static void handleInstallStatus(Context context,Intent intent){
        int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        SharedPreferences p=prefs(context);
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){
            Intent confirm;
            if(Build.VERSION.SDK_INT>=33)confirm=intent.getParcelableExtra(PackageInstaller.EXTRA_INTENT,Intent.class);
            else confirm=(Intent)intent.getParcelableExtra(PackageInstaller.EXTRA_INTENT);
            if(confirm!=null){
                authorizeUpdateFlow(context,confirm);confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try{context.startActivity(confirm);}catch(Exception e){p.edit().putString(K_LAST_ERROR,"Confirmation Android indisponible").apply();}
            }
            return;
        }
        if(status==PackageInstaller.STATUS_SUCCESS){
            clearReady(context,true);p.edit().putString(K_LAST_ERROR,"").apply();return;
        }
        String message=intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        p.edit().putString(K_LAST_ERROR,"Installation Android : "+(message==null?"échec "+status:message)).apply();
    }

    private static void authorizeUpdateFlow(Context context,Intent intent){
        String pkg=null;ComponentName component=intent.getComponent();
        if(component!=null)pkg=component.getPackageName();
        if(pkg==null){
            ComponentName resolved=intent.resolveActivity(context.getPackageManager());
            if(resolved!=null)pkg=resolved.getPackageName();
        }
        if(pkg!=null&&!pkg.isEmpty())PinGuard.authorizeSystemControl(PinGuard.CONTROL_UPDATE,pkg);
    }
}
