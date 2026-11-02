package io.github.randomittin.voiceassistant;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Settings the D-pad can drive: every row is focusable, OK changes it. Keys are shown only as
 * "set" or "not set", never echoed.
 */
public class SettingsActivity extends Activity {

    private Settings settings;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new Settings(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF10151B);
        scroll.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 56);
        list.setPadding(pad, Ui.dp(this, 40), pad, Ui.dp(this, 40));
        scroll.addView(list);
        setContentView(scroll);
        render();
    }

    private void render() {
        list.removeAllViews();
        TextView title = new TextView(this);
        title.setText("Voice Assistant settings");
        title.setTextSize(34);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Ui.TEXT_PRIMARY);
        list.addView(title);

        TextView note = new TextView(this);
        note.setText("Move with Up/Down, press OK to change. Back closes.");
        note.setTextSize(20);
        note.setTextColor(Ui.TEXT_SECONDARY);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        np.bottomMargin = Ui.dp(this, 20);
        list.addView(note, np);

        row("Answers from", Settings.brainLabel(settings.brain()) + "  (OK switches)", v -> {
            settings.setBrain(Settings.BRAIN_CLAUDE.equals(settings.brain())
                    ? Settings.BRAIN_CHATGPT : Settings.BRAIN_CLAUDE);
            render();
        });
        row("Anthropic API key (Claude)", settings.hasAnthropicKey() ? "set" : "not set",
                v -> editKey("Anthropic API key", true));
        row("OpenAI API key (speech-to-text and ChatGPT)", settings.hasOpenAiKey() ? "set" : "not set",
                v -> editKey("OpenAI API key", false));
        row("Claude model", settings.claudeModel(), v -> Ui.promptText(this, "Claude model id",
                settings.claudeModel(), false, "Save", text -> {
                    settings.setClaudeModel(text);
                    render();
                }));
        row("ChatGPT model", settings.openAiModel(), v -> Ui.promptText(this, "ChatGPT model id",
                settings.openAiModel(), false, "Save", text -> {
                    settings.setOpenAiModel(text);
                    render();
                }));
        row("Speech-to-text model", settings.sttModel(), v -> Ui.promptText(this, "Speech-to-text model id",
                settings.sttModel(), false, "Save", text -> {
                    settings.setSttModel(text);
                    render();
                }));
        row("Done", "", v -> finish());
        list.getChildAt(Math.min(2, list.getChildCount() - 1)).requestFocus();
    }

    private void editKey(String title, boolean anthropic) {
        Ui.promptText(this, title + " (empty clears it)", "", true, "Save", text -> {
            if (anthropic) {
                settings.setAnthropicKey(text);
            } else {
                settings.setOpenAiKey(text);
            }
            render();
        });
    }

    private void row(String label, String value, View.OnClickListener onClick) {
        TextView row = new TextView(this);
        row.setText(value.isEmpty() ? label : label + ":  " + value);
        row.setTextSize(26);
        row.setTextColor(Ui.TEXT_PRIMARY);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackgroundResource(R.drawable.row);
        int pad = Ui.dp(this, 18);
        row.setPadding(pad, pad, pad, pad);
        row.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 8);
        list.addView(row, lp);
    }
}
