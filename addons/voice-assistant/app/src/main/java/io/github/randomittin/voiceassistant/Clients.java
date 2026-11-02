package io.github.randomittin.voiceassistant;

import org.json.JSONException;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The three network calls: speech-to-text, Claude and ChatGPT. Blocking; call off the main thread. */
final class Clients {

    static final String ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";
    static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";
    static final String OPENAI_TRANSCRIBE_URL = "https://api.openai.com/v1/audio/transcriptions";
    static final String DEEPGRAM_URL =
            "https://api.deepgram.com/v1/listen?model=nova-3&smart_format=true&detect_language=true";
    static final String ANTHROPIC_VERSION = "2023-06-01";

    private static final int CHAT_READ_TIMEOUT_MS = 45000;
    private static final int TRANSCRIBE_READ_TIMEOUT_MS = 30000;

    private final Settings settings;

    Clients(Settings settings) {
        this.settings = settings;
    }

    /** The label of the service {@link #transcribe} will call, for error messages. */
    String sttLabel() {
        return SttProvider.label(settings.sttProvider()) + " speech-to-text";
    }

    String transcribe(byte[] wav) throws ApiException {
        if (SttProvider.DEEPGRAM.equals(settings.sttProvider())) {
            return transcribeDeepgram(wav);
        }
        return transcribeOpenAi(wav);
    }

    private String transcribeDeepgram(byte[] wav) throws ApiException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Token " + settings.deepgramKey());
        String reply = HttpJson.post(DEEPGRAM_URL, headers, "audio/wav", wav, TRANSCRIBE_READ_TIMEOUT_MS);
        return ApiRequests.parseDeepgram(reply);
    }

    private String transcribeOpenAi(byte[] wav) throws ApiException {
        Multipart form = new Multipart("----voiceassistant" + System.nanoTime())
                .field("model", settings.sttModel())
                .field("response_format", "json")
                .file("file", "speech.wav", "audio/wav", wav);
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + settings.openAiKey());
        String reply = HttpJson.post(OPENAI_TRANSCRIBE_URL, headers, form.contentType(), form.build(),
                TRANSCRIBE_READ_TIMEOUT_MS);
        return ApiRequests.parseTranscription(reply);
    }

    String ask(String brain, List<Conversation.Message> history) throws ApiException {
        try {
            if (Settings.BRAIN_CHATGPT.equals(brain)) {
                return askChatGpt(history);
            }
            return askClaude(history);
        } catch (JSONException e) {
            throw new ApiException(ApiException.Kind.BAD_REQUEST, 0, "could not build the request");
        }
    }

    private String askClaude(List<Conversation.Message> history) throws ApiException, JSONException {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", settings.anthropicKey());
        headers.put("anthropic-version", ANTHROPIC_VERSION);
        byte[] body = ApiRequests.anthropicBody(settings.claudeModel(), history).getBytes(StandardCharsets.UTF_8);
        return ApiRequests.parseAnthropic(
                HttpJson.post(ANTHROPIC_URL, headers, "application/json", body, CHAT_READ_TIMEOUT_MS));
    }

    private String askChatGpt(List<Conversation.Message> history) throws ApiException, JSONException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + settings.openAiKey());
        byte[] body = ApiRequests.openAiBody(settings.openAiModel(), history).getBytes(StandardCharsets.UTF_8);
        return ApiRequests.parseOpenAi(
                HttpJson.post(OPENAI_CHAT_URL, headers, "application/json", body, CHAT_READ_TIMEOUT_MS));
    }
}
