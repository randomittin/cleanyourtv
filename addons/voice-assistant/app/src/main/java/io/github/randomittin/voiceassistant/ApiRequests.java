package io.github.randomittin.voiceassistant;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/** Request bodies for, and responses from, the Anthropic and OpenAI APIs. Pure logic. */
public final class ApiRequests {

    public static final String SYSTEM_PROMPT =
            "You are a voice assistant on a living-room TV. The user asks by voice and your answer is "
            + "shown in large text and read aloud. Answer in one to three short, plain spoken sentences. "
            + "No markdown, lists, emoji or URLs. If you are unsure or lack live data, say so briefly.";

    public static final int ANTHROPIC_MAX_TOKENS = 1024;
    public static final int OPENAI_MAX_TOKENS = 400;

    private ApiRequests() {
    }

    /** Messages API body. Effort is low: spoken answers are short and latency matters more than depth. */
    public static String anthropicBody(String model, List<Conversation.Message> history) throws JSONException {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("max_tokens", ANTHROPIC_MAX_TOKENS);
        body.put("system", SYSTEM_PROMPT);
        body.put("output_config", new JSONObject().put("effort", "low"));
        body.put("messages", messageArray(history));
        return body.toString();
    }

    /** Chat Completions body with the system prompt as the first message. */
    public static String openAiBody(String model, List<Conversation.Message> history) throws JSONException {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("max_completion_tokens", OPENAI_MAX_TOKENS);
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", SYSTEM_PROMPT));
        JSONArray rest = messageArray(history);
        for (int i = 0; i < rest.length(); i++) {
            messages.put(rest.get(i));
        }
        body.put("messages", messages);
        return body.toString();
    }

    private static JSONArray messageArray(List<Conversation.Message> history) throws JSONException {
        JSONArray array = new JSONArray();
        for (Conversation.Message m : history) {
            array.put(new JSONObject().put("role", m.role).put("content", m.text));
        }
        return array;
    }

    /** The text of a Messages API reply: every text block, in order. */
    public static String parseAnthropic(String json) throws ApiException {
        try {
            JSONObject root = new JSONObject(json);
            if ("refusal".equals(root.optString("stop_reason"))) {
                throw new ApiException(ApiException.Kind.REFUSED, 200, "Claude declined to answer that.");
            }
            JSONArray content = root.optJSONArray("content");
            StringBuilder text = new StringBuilder();
            if (content != null) {
                for (int i = 0; i < content.length(); i++) {
                    JSONObject block = content.getJSONObject(i);
                    if ("text".equals(block.optString("type"))) {
                        text.append(block.optString("text"));
                    }
                }
            }
            return nonEmpty(text.toString(), "Claude sent an empty answer.");
        } catch (JSONException e) {
            throw new ApiException(ApiException.Kind.EMPTY, 200, "Claude sent a reply this app cannot read.");
        }
    }

    /** The assistant text of a Chat Completions reply. */
    public static String parseOpenAi(String json) throws ApiException {
        try {
            JSONArray choices = new JSONObject(json).optJSONArray("choices");
            String text = "";
            if (choices != null && choices.length() > 0) {
                JSONObject message = choices.getJSONObject(0).optJSONObject("message");
                if (message != null && !message.isNull("content")) {
                    text = message.optString("content");
                }
            }
            return nonEmpty(text, "ChatGPT sent an empty answer.");
        } catch (JSONException e) {
            throw new ApiException(ApiException.Kind.EMPTY, 200, "ChatGPT sent a reply this app cannot read.");
        }
    }

    /** The transcript of an audio transcription reply ({"text": "..."}). */
    public static String parseTranscription(String json) throws ApiException {
        try {
            String text = new JSONObject(json).optString("text");
            return nonEmpty(text, "I did not catch any words.");
        } catch (JSONException e) {
            throw new ApiException(ApiException.Kind.EMPTY, 200, "Transcription sent a reply this app cannot read.");
        }
    }

    /** The transcript of a Deepgram prerecorded reply: results.channels[0].alternatives[0].transcript. */
    public static String parseDeepgram(String json) throws ApiException {
        try {
            JSONArray channels = new JSONObject(json).getJSONObject("results").optJSONArray("channels");
            String text = "";
            if (channels != null && channels.length() > 0) {
                JSONArray alternatives = channels.getJSONObject(0).optJSONArray("alternatives");
                if (alternatives != null && alternatives.length() > 0) {
                    text = alternatives.getJSONObject(0).optString("transcript");
                }
            }
            return nonEmpty(text, "I did not catch any words.");
        } catch (JSONException e) {
            throw new ApiException(ApiException.Kind.EMPTY, 200, "Deepgram sent a reply this app cannot read.");
        }
    }

    /**
     * The human message inside an API error body: {"error": {"message": "..."}} (Anthropic, OpenAI) or
     * {"err_code": "...", "err_msg": "..."} (Deepgram), or "" when absent.
     */
    public static String errorMessage(String json) {
        if (json == null || json.isEmpty()) {
            return "";
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONObject error = root.optJSONObject("error");
            if (error != null) {
                return error.optString("message");
            }
            return root.optString("err_msg");
        } catch (JSONException e) {
            return "";
        }
    }

    private static String nonEmpty(String text, String whenEmpty) throws ApiException {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw new ApiException(ApiException.Kind.EMPTY, 200, whenEmpty);
        }
        return trimmed;
    }
}
