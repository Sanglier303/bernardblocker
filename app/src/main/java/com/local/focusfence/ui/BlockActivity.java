package com.local.focusfence.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class BlockActivity extends Activity {
    public static final String EXTRA_PACKAGE = "package";
    public static final String EXTRA_REASON = "reason";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        render(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        render(intent);
    }

    private void render(Intent intent) {
        String reason = intent == null ? null : intent.getStringExtra(EXTRA_REASON);
        if (reason == null) reason = "Cette application est bloquée par FocusFence.";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(32), dp(48), dp(32), dp(48));
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(this);
        title.setText("FocusFence");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView text = new TextView(this);
        text.setText(reason);
        text.setTextSize(19);
        text.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = matchWrap();
        tp.setMargins(0, dp(24), 0, dp(28));
        root.addView(text, tp);

        Button home = new Button(this);
        home.setText("Retour à l'accueil");
        home.setOnClickListener(v -> {
            Intent h = new Intent(Intent.ACTION_MAIN);
            h.addCategory(Intent.CATEGORY_HOME);
            h.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(h);
            finish();
        });
        root.addView(home, matchWrap());

        setContentView(root);
    }

    @Override public void onBackPressed() {
        Intent h = new Intent(Intent.ACTION_MAIN);
        h.addCategory(Intent.CATEGORY_HOME);
        h.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(h);
        finish();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
