package com.local.focusfence.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.local.focusfence.R;
import com.local.focusfence.security.PinGuard;

import static com.local.focusfence.ui.Ui.*;

/** Four-digit local administrator gate for Bernard's settings and anti-tamper system screens. */
public final class PinActivity extends Activity {
    public static final String EXTRA_TARGET_PAGE = "target_page";
    public static final String EXTRA_GUARD_MODE = "guard_mode";

    private final StringBuilder digits = new StringBuilder(4);
    private TextView dots;
    private TextView message;
    private TextView heading;
    private TextView subtitle;
    private String target = "";
    private boolean guardMode;
    private char[] setupFirst;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        target = getIntent().getStringExtra(EXTRA_TARGET_PAGE);
        if (target == null) target = "";
        guardMode = getIntent().getBooleanExtra(EXTRA_GUARD_MODE, false);
        PinGuard.ensureConfigured(this);
        render();
    }

    private boolean setupMode() {
        return !PinGuard.isConfigured(this);
    }

    private void render() {
        LinearLayout root = col(this);
        root.setBackgroundColor(PAPER);
        insets(this, root, false);

        LinearLayout body = col(this);
        int width = getResources().getConfiguration().screenWidthDp;
        int margin = Math.max(24, (width - 430) / 2);
        pad(body, margin, 30, margin, 28);
        root.addView(body, lp(-1, -1));

        body.addView(image(this, R.drawable.scene_block, 190, 25), lp(-1, dp(this, 190)));
        space(body, 24);

        heading = title(this, "", 29);
        body.addView(heading);
        space(body, 8);
        subtitle = muted(this, "", 14);
        body.addView(subtitle);
        space(body, 24);

        dots = text(this, "○  ○  ○  ○", 31, FOREST, true);
        dots.setGravity(Gravity.CENTER);
        body.addView(dots, lp(-1, -2));
        space(body, 10);
        message = muted(this, "", 13);
        message.setGravity(Gravity.CENTER);
        body.addView(message, lp(-1, -2));
        space(body, 20);

        int[][] keys = {{1,2,3},{4,5,6},{7,8,9}};
        for (int[] row : keys) {
            LinearLayout r = row(this);
            for (int i = 0; i < row.length; i++) {
                final int value = row[i];
                TextView b = keypad(String.valueOf(value), () -> append(value));
                LinearLayout.LayoutParams p = weight();
                if (i > 0) p.leftMargin = dp(this, 10);
                r.addView(b, p);
            }
            body.addView(r, lp(-1, -2));
            space(body, 10);
        }

        LinearLayout last = row(this);
        last.addView(new View(this), weight());
        LinearLayout.LayoutParams zeroP = weight();
        zeroP.leftMargin = dp(this, 10);
        last.addView(keypad("0", () -> append(0)), zeroP);
        LinearLayout.LayoutParams backP = weight();
        backP.leftMargin = dp(this, 10);
        last.addView(keypad("⌫", this::backspace), backP);
        body.addView(last, lp(-1, -2));

        space(body, 20);
        body.addView(button(this, setupMode() ? "Annuler la configuration" : "Annuler", false, this::cancel), lp(-1, -2));

        setContentView(root);
        updateCopy();
        refreshDots();
        refreshLockout();
    }

    private void updateCopy() {
        if (setupMode()) {
            if (setupFirst == null) {
                heading.setText("Créer le code administrateur");
                subtitle.setText("Choisis 4 chiffres. Bernard ne stockera pas le code, seulement un vérificateur privé sur ce téléphone.");
            } else {
                heading.setText("Confirmer le code");
                subtitle.setText("Entre exactement les mêmes 4 chiffres une seconde fois.");
            }
        } else {
            heading.setText("Bernard garde les réglages");
            subtitle.setText(guardMode
                    ? "Ce réglage Android peut désactiver la protection. Entre le code pour continuer."
                    : "Entre le code pour modifier les limites ou les paramètres.");
        }
    }

    private TextView keypad(String label, Runnable click) {
        TextView b = button(this, label, false, click);
        b.setTextSize(22);
        b.setMinHeight(dp(this, 58));
        return b;
    }

    private void append(int value) {
        if (!setupMode() && PinGuard.lockoutRemainingMs(this) > 0) {
            refreshLockout();
            return;
        }
        if (digits.length() >= 4) return;
        digits.append(value);
        refreshDots();
        if (digits.length() == 4) submit();
    }

    private void backspace() {
        if (digits.length() > 0) digits.deleteCharAt(digits.length() - 1);
        message.setText("");
        refreshDots();
    }

    private void refreshDots() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i > 0) s.append("  ");
            s.append(i < digits.length() ? "●" : "○");
        }
        dots.setText(s.toString());
    }

    private void refreshLockout() {
        long remaining = PinGuard.lockoutRemainingMs(this);
        if (remaining > 0 && message != null) {
            message.setText("Trop d’essais. Réessaie dans " + Math.max(1, (remaining + 999) / 1000) + " s.");
            message.postDelayed(this::refreshLockout, Math.min(1000, remaining));
        }
    }

    private void submit() {
        char[] pin = digits.toString().toCharArray();
        digits.setLength(0);
        refreshDots();

        if (setupMode()) {
            if (setupFirst == null) {
                setupFirst = pin.clone();
                java.util.Arrays.fill(pin, '\0');
                message.setText("");
                updateCopy();
                return;
            }
            boolean same = java.util.Arrays.equals(setupFirst, pin);
            if (!same) {
                java.util.Arrays.fill(setupFirst, '\0');
                java.util.Arrays.fill(pin, '\0');
                setupFirst = null;
                message.setText("Les deux codes ne correspondent pas. Recommence.");
                updateCopy();
                return;
            }
            java.util.Arrays.fill(setupFirst, '\0');
            setupFirst = null;
            if (!PinGuard.setPin(this, pin)) {
                message.setText("Impossible d’enregistrer le code. Réessaie.");
                updateCopy();
                return;
            }
            finishAuthorized();
            return;
        }

        if (!PinGuard.verify(this, pin)) {
            message.setText(PinGuard.lockoutRemainingMs(this) > 0
                    ? "Trop d’essais. Bernard attend un peu."
                    : "Code incorrect.");
            refreshLockout();
            return;
        }
        finishAuthorized();
    }

    private void finishAuthorized() {
        if (guardMode) PinGuard.authorizeSystemControl();
        if (!target.isEmpty()) {
            Intent i = new Intent(this, MainActivity.class)
                    .putExtra("page", target)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        }
        finish();
    }

    private void cancel() {
        if (setupFirst != null) {
            java.util.Arrays.fill(setupFirst, '\0');
            setupFirst = null;
        }
        if (guardMode) goHome();
        else finish();
    }

    private void goHome() {
        Intent h = new Intent(Intent.ACTION_MAIN);
        h.addCategory(Intent.CATEGORY_HOME);
        h.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(h);
        finish();
    }

    @Override public void onBackPressed() {
        cancel();
    }
}
