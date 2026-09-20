package com.local.focusfence.ui;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import com.local.focusfence.storage.Prefs;

/** Shared visual preview. Runtime enforcement uses the same Ui.blockScreen as an accessibility overlay. */
public final class BlockActivity extends Activity {
    public static final String EXTRA_PACKAGE="package",EXTRA_REASON="reason";
    protected void onCreate(Bundle b){super.onCreate(b);render();}
    protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);render();}
    private void render(){
        Intent i=getIntent();String reason=i.getStringExtra(EXTRA_REASON),resume=i.getStringExtra("resume");
        if(reason==null)reason="La limite du jour est atteinte.";if(resume==null)resume="Tu pourras revenir pendant ta prochaine plage autorisée.";
        View root=Ui.blockScreen(this,new Prefs(this).person(),reason,resume,false,i.getBooleanExtra("schedule",false),this::finish,()->{startActivity(new Intent(this,MainActivity.class).putExtra("page","limits").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();});
        Ui.insets(this,root,true);setContentView(root);
    }
}
