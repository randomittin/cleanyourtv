package io.github.randomittin.voiceassistant;

/** Which speech-to-text service transcribes the recording. Pure logic. */
public final class SttProvider {

    public static final String AUTO = "auto";
    public static final String DEEPGRAM = "deepgram";
    public static final String OPENAI = "openai";

    private SttProvider() {
    }

    public static boolean isChoice(String value) {
        return AUTO.equals(value) || DEEPGRAM.equals(value) || OPENAI.equals(value);
    }

    /** The service to use: auto picks Deepgram when its key is set, otherwise OpenAI. */
    public static String resolve(String choice, boolean hasDeepgramKey) {
        if (DEEPGRAM.equals(choice) || OPENAI.equals(choice)) {
            return choice;
        }
        return hasDeepgramKey ? DEEPGRAM : OPENAI;
    }

    /** The next choice in the settings cycle: auto, deepgram, openai. */
    public static String next(String choice) {
        if (AUTO.equals(choice)) {
            return DEEPGRAM;
        }
        return DEEPGRAM.equals(choice) ? OPENAI : AUTO;
    }

    public static String label(String provider) {
        return DEEPGRAM.equals(provider) ? "Deepgram" : OPENAI.equals(provider) ? "OpenAI" : "Auto";
    }

    /** The sentence to show when no usable key exists for the chosen service, or null when one does. */
    public static String missingKeyMessage(String choice, boolean hasDeepgramKey, boolean hasOpenAiKey) {
        String provider = resolve(choice, hasDeepgramKey);
        if (DEEPGRAM.equals(provider) && hasDeepgramKey || OPENAI.equals(provider) && hasOpenAiKey) {
            return null;
        }
        if (AUTO.equals(choice)) {
            return "Speech-to-text needs a Deepgram or an OpenAI API key.";
        }
        return "Speech-to-text is set to " + label(provider) + " but has no " + label(provider) + " API key.";
    }
}
