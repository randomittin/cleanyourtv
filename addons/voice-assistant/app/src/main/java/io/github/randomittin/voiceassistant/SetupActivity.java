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
 * {@code am start -n io.github.randomittin.voiceassistant/.SetupActivity --es anthropic_key ... --es openai_key ... --es deepgram_key ... --es stt auto --es brain claude}.
 * Writes only what the intent carries, shows what was saved (never the keys), then closes.
 * Without extras it just opens the settings screen.
 */
public class SetupActivity extends Activity {

    static final String EXTRA_ANTHROPIC = "anthropic_key";
    static final String EXTRA_OPENAI = "openai_key";
    static final String EXTRA_DEEPGRAM = "deepgram_key";
    static final String EXTRA_BRAIN = "brain";
    static final String EXTRA_STT = "stt";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String anthropic = intent.getStringExtra(EXTRA_ANTHROPIC);
        String openai = intent.getStringExtra(EXTRA_OPENAI);
        String deepgram = intent.getStringExtra(EXTRA_DEEPGRAM);
        String brain = intent.getStringExtra(EXTRA_BRAIN);
        String stt = intent.getStringExtra(EXTRA_STT);
        if (anthropic == null && openai == null && deepgram == null && brain == null && stt == null) {
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
        if (deepgram != null && !deepgram.trim().isEmpty()) {
            settings.setDeepgramKey(deepgram);
            saved.append("Deepgram key saved.\n");
        }
        if (stt != null) {
            String wanted = stt.trim().toLowerCase(java.util.Locale.ROOT);
            if (SttProvider.isChoice(wanted)) {
                settings.setSttChoice(wanted);
                saved.append("Speech-to-text: ").append(SttProvider.label(wanted)).append(".\n");
            } else {
                saved.append("Unknown stt \"").append(wanted).append("\": use auto, deepgram or openai.\n");
            }
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
                .append("\nDeepgram key: ").append(settings.hasDeepgramKey() ? "set" : "not set")
                .append("\nSpeech-to-text: ").append(SttProvider.label(settings.sttChoice()))
                .append(" (using ").append(SttProvider.label(settings.sttProvider())).append(")")
                .append("\nBrain: ").append(Settings.brainLabel(settings.brain()));

        BrainIdentity.apply(this);

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
