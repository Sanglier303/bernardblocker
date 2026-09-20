package com.bernard.fixture;
public class FixtureActivity extends android.app.Activity {
 @Override protected void onCreate(android.os.Bundle b){super.onCreate(b);android.widget.TextView t=new android.widget.TextView(this);t.setText("TEST ONLY\n\nApplication de test du quota Jeux.\nCe n’est pas un jeu réel.");t.setGravity(android.view.Gravity.CENTER);t.setTextSize(24);setContentView(t);getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
}
