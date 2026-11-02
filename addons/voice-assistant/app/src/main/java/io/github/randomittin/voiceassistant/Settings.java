package io.github.randomittin.voiceassistant;

import android.content.Context;
import android.content.SharedPreferences;

/** Keys, brain choice and model ids, in this app's private SharedPreferences (backups are off). */
final class Settings {

    static final String BRAIN_CLAUDE = "claude";
    static final String BRAIN_CHATGPT = "chatgpt";

    static final String DEFAULT_CLAUDE_MODEL = "claude-haiku-5-5";
    static final String DEFAULT_OPENAI_MODEL = "gpt-4o-mini";
    static final String DEFAULT_STT_MODEL = "gpt-4o-mini-transcribe";

    private static final String FILE = "voice_assistant";
    private static final String K_ANTHROPIC = "anthropic_key";
    private static final String K_OPENAI = "openai_key";
    private static final String K_DEEPGRAM = "deepgram_key";
    private static final String K_STT = "stt";
    private static final String K_BRAIN = "brain";
    private static final String K_CLAUDE_MODEL = "claude_model";
    private static final String K_OPENAI_MODEL = "openai_model";
    private static final String K_STT_MODEL = "stt_model";

    private final SharedPreferences prefs;

    Settings(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    String anthropicKey() {
        return prefs.getString(K_ANTHROPIC, "");
    }

    String openAiKey() {
        return prefs.getString(K_OPENAI, "");
    }

    String deepgramKey() {
        return prefs.getString(K_DEEPGRAM, "");
    }

    void setDeepgramKey(String key) {
        prefs.edit().putString(K_DEEPGRAM, key.trim()).apply();
    }

    boolean hasDeepgramKey() {
        return !deepgramKey().isEmpty();
    }

    /** The saved choice: auto, deepgram or openai. */
    String sttChoice() {
        String value = prefs.getString(K_STT, SttProvider.AUTO);
        return SttProvider.isChoice(value) ? value : SttProvider.AUTO;
    }

    void setSttChoice(String choice) {
        prefs.edit().putString(K_STT, choice).apply();
    }

    /** The service that will really transcribe: deepgram or openai. */
    String sttProvider() {
        return SttProvider.resolve(sttChoice(), hasDeepgramKey());
    }

    /** Null when speech-to-text can run, else what to tell the user. */
    String sttMissingKeyMessage() {
        return SttProvider.missingKeyMessage(sttChoice(), hasDeepgramKey(), hasOpenAiKey());
    }

    void setAnthropicKey(String key) {
        prefs.edit().putString(K_ANTHROPIC, key.trim()).apply();
    }

    void setOpenAiKey(String key) {
        prefs.edit().putString(K_OPENAI, key.trim()).apply();
    }

    boolean hasAnthropicKey() {
        return !anthropicKey().isEmpty();
    }

    boolean hasOpenAiKey() {
        return !openAiKey().isEmpty();
    }

    String brain() {
        return BRAIN_CHATGPT.equals(prefs.getString(K_BRAIN, BRAIN_CLAUDE)) ? BRAIN_CHATGPT : BRAIN_CLAUDE;
    }

    void setBrain(String brain) {
        prefs.edit().putString(K_BRAIN, brain).apply();
    }

    static boolean isBrain(String value) {
        return BRAIN_CLAUDE.equals(value) || BRAIN_CHATGPT.equals(value);
    }

    static String brainLabel(String brain) {
        return BRAIN_CHATGPT.equals(brain) ? "ChatGPT" : "Claude";
    }

    String claudeModel() {
        return modelOrDefault(K_CLAUDE_MODEL, DEFAULT_CLAUDE_MODEL);
    }

    String openAiModel() {
        return modelOrDefault(K_OPENAI_MODEL, DEFAULT_OPENAI_MODEL);
    }

    String sttModel() {
        return modelOrDefault(K_STT_MODEL, DEFAULT_STT_MODEL);
    }

    void setClaudeModel(String model) {
        prefs.edit().putString(K_CLAUDE_MODEL, model.trim()).apply();
    }

    void setOpenAiModel(String model) {
        prefs.edit().putString(K_OPENAI_MODEL, model.trim()).apply();
    }

    void setSttModel(String model) {
        prefs.edit().putString(K_STT_MODEL, model.trim()).apply();
    }

    private String modelOrDefault(String key, String fallback) {
        String value = prefs.getString(key, "").trim();
        return value.isEmpty() ? fallback : value;
    }
}
