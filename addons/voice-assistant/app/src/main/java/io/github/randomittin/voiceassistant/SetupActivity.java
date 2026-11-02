package io.github.randomittin.voiceassistant;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.TextView;

/**
 * Provisioning over ADB, so nobody types a long key with a remote:
 * {@code am start -n io.github.randomittin.voiceassistant/.SetupActivity --es anthropic_key ... --es openai_key ... --es brain claude}.
 * Writes only what the intent carries, shows what was saved (never the keys), then closes.
 * Without extras it just opens the settings screen.
 */
public class SetupActivity extends Activity {

    static final String EXTRA_ANTHROPIC = "anthropic_key";
    static final String EXTRA_OPENAI = "openai_key";
    static final String EXTRA_BRAIN = "brain";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String anthropic = intent.getStringExtra(EXTRA_ANTHROPIC);
        String openai = intent.getStringExtra(EXTRA_OPENAI);
        String brain = intent.getStringExtra(EXTRA_BRAIN);
        if (anthropic == null && openai == null && brain == null) {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
            return;
        }

        Settings settings = new Settings(this);
        StringBuilder saved = new StringBuilder();
        if (anthropic != null && !anthropic.trim().isEmpty()) {
            settings.setAnthropicKey(anthropic);
            saved.append("Anthropic key saved.\n");
        }
        if (openai != null && !openai.trim().isEmpty()) {
            settings.setOpenAiKey(openai);
            saved.append("OpenAI key saved.\n");
        }
        if (brain != null) {
            String wanted = brain.trim().toLowerCase(java.util.Locale.ROOT);
            if (Settings.isBrain(wanted)) {
                settings.setBrain(wanted);
                saved.append("Answers from ").append(Settings.brainLabel(wanted)).append(".\n");
            } else {
                saved.append("Unknown brain \"").append(wanted).append("\": use claude or chatgpt.\n");
            }
        }
        if (saved.length() == 0) {
            saved.append("Nothing to save: the extras were empty.\n");
        }
        saved.append("\nAnthropic key: ").append(settings.hasAnthropicKey() ? "set" : "not set")
                .append("\nOpenAI key: ").append(settings.hasOpenAiKey() ? "set" : "not set")
                .append("\nBrain: ").append(Settings.brainLabel(settings.brain()));

        TextView view = new TextView(this);
        view.setText(saved.toString());
        view.setTextSize(30);
        view.setTextColor(Ui.TEXT_PRIMARY);
        view.setBackgroundColor(0xFF10151B);
        view.setGravity(Gravity.CENTER);
        setContentView(view);
        new Handler(Looper.getMainLooper()).postDelayed(this::finish, 4000);
    }
}
