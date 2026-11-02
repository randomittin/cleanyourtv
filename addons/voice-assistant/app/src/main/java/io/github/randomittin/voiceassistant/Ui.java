package io.github.randomittin.voiceassistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

/** Small view helpers shared by the three screens. */
final class Ui {

    static final int TEXT_PRIMARY = 0xFFFFFFFF;
    static final int TEXT_SECONDARY = 0xFFB0BAC5;
    static final int ACCENT = 0xFF7DB8FF;
    static final int WARNING = 0xFFFFB86B;

    interface TextCallback {
        void onText(String text);
    }

    private Ui() {
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** A text dialog the TV's on-screen keyboard can fill in. Empty input is passed through as "". */
    static void promptText(Activity activity, String title, String initial, boolean secret,
                           String positiveLabel, TextCallback callback) {
        final EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setTextSize(24);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setInputType(secret
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT);
        input.setText(initial);
        input.setSelection(input.getText().length());
        int pad = dp(activity, 24);
        AlertDialog dialog = new AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(title)
                .setPositiveButton(positiveLabel, (d, w) -> callback.onText(input.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setView(input, pad, pad / 2, pad, 0);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                return true;
            }
            return false;
        });
        dialog.show();
    }
}
