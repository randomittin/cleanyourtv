package io.github.randomittin.voiceassistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

public class ApiRequestsTest {

    private static List<Conversation.Message> history() {
        Conversation c = new Conversation();
        c.addUser("What is the capital of France?");
        c.addAssistant("Paris.");
        c.addUser("And its population?");
        return c.messages();
    }

    @Test
    public void anthropicBodyHasModelSystemEffortAndAlternatingMessages() throws Exception {
        JSONObject body = new JSONObject(ApiRequests.anthropicBody("claude-haiku-5-5", history()));
        assertEquals("claude-haiku-5-5", body.getString("model"));
        assertEquals(ApiRequests.ANTHROPIC_MAX_TOKENS, body.getInt("max_tokens"));
        assertEquals(ApiRequests.SYSTEM_PROMPT, body.getString("system"));
        assertEquals("low", body.getJSONObject("output_config").getString("effort"));
        JSONArray messages = body.getJSONArray("messages");
        assertEquals(3, messages.length());
        assertEquals("user", messages.getJSONObject(0).getString("role"));
        assertEquals("assistant", messages.getJSONObject(1).getString("role"));
        assertEquals("And its population?", messages.getJSONObject(2).getString("content"));
        // Sampling parameters are rejected on current models, so none may be sent.
        assertFalse(body.has("temperature"));
        assertFalse(body.has("top_p"));
        assertFalse(body.has("top_k"));
    }

    @Test
    public void openAiBodyStartsWithSystemMessage() throws Exception {
        JSONObject body = new JSONObject(ApiRequests.openAiBody("gpt-4o-mini", history()));
        assertEquals("gpt-4o-mini", body.getString("model"));
        JSONArray messages = body.getJSONArray("messages");
        assertEquals(4, messages.length());
        assertEquals("system", messages.getJSONObject(0).getString("role"));
        assertEquals(ApiRequests.SYSTEM_PROMPT, messages.getJSONObject(0).getString("content"));
        assertEquals("user", messages.getJSONObject(1).getString("role"));
        assertTrue(body.has("max_completion_tokens"));
    }

    @Test
    public void systemPromptAsksForShortSpokenAnswers() {
        assertTrue(ApiRequests.SYSTEM_PROMPT.contains("one to three short"));
    }

    @Test
    public void specialCharactersSurviveJsonEscaping() throws Exception {
        Conversation c = new Conversation();
        c.addUser("Say \"hi\"\nand a backslash \\");
        JSONObject body = new JSONObject(ApiRequests.anthropicBody("m", c.messages()));
        assertEquals("Say \"hi\"\nand a backslash \\",
                body.getJSONArray("messages").getJSONObject(0).getString("content"));
    }

    @Test
    public void parseAnthropicJoinsTextBlocksAndSkipsOthers() throws Exception {
        String json = "{\"type\":\"message\",\"stop_reason\":\"end_turn\",\"content\":["
                + "{\"type\":\"thinking\",\"thinking\":\"\"},"
                + "{\"type\":\"text\",\"text\":\"Paris has \"},"
                + "{\"type\":\"text\",\"text\":\"about two million people.\"}]}";
        assertEquals("Paris has about two million people.", ApiRequests.parseAnthropic(json));
    }

    @Test
    public void parseAnthropicRefusalAndEmptyAreErrors() {
        try {
            ApiRequests.parseAnthropic("{\"stop_reason\":\"refusal\",\"content\":[]}");
            fail();
        } catch (ApiException e) {
            assertEquals(ApiException.Kind.REFUSED, e.kind);
        }
        try {
            ApiRequests.parseAnthropic("{\"stop_reason\":\"end_turn\",\"content\":[]}");
            fail();
        } catch (ApiException e) {
            assertEquals(ApiException.Kind.EMPTY, e.kind);
        }
        try {
            ApiRequests.parseAnthropic("not json");
            fail();
        } catch (ApiException e) {
            assertEquals(ApiException.Kind.EMPTY, e.kind);
        }
    }

    @Test
    public void parseOpenAiReadsFirstChoice() throws Exception {
        String json = "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"  Hello there. \"}}]}";
        assertEquals("Hello there.", ApiRequests.parseOpenAi(json));
    }

    @Test
    public void parseOpenAiNullContentIsEmptyError() {
        try {
            ApiRequests.parseOpenAi("{\"choices\":[{\"message\":{\"content\":null}}]}");
            fail();
        } catch (ApiException e) {
            assertEquals(ApiException.Kind.EMPTY, e.kind);
        }
    }

    @Test
    public void parseTranscription() throws Exception {
        assertEquals("what time is it", ApiRequests.parseTranscription("{\"text\":\"what time is it\"}"));
        try {
            ApiRequests.parseTranscription("{\"text\":\"  \"}");
            fail();
        } catch (ApiException e) {
            assertEquals(ApiException.Kind.EMPTY, e.kind);
        }
    }

    @Test
    public void errorMessageReadsBothProvidersShape() {
        assertEquals("invalid x-api-key", ApiRequests.errorMessage(
                "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}"));
        assertEquals("Incorrect API key", ApiRequests.errorMessage(
                "{\"error\":{\"message\":\"Incorrect API key\",\"type\":\"invalid_request_error\"}}"));
        assertEquals("", ApiRequests.errorMessage("<html>"));
        assertEquals("", ApiRequests.errorMessage(""));
        assertEquals("", ApiRequests.errorMessage(null));
    }

    @Test
    public void statusClassificationAndErrorText() {
        assertEquals(ApiException.Kind.AUTH, ApiException.kindForStatus(401));
        assertEquals(ApiException.Kind.AUTH, ApiException.kindForStatus(403));
        assertEquals(ApiException.Kind.RATE_LIMIT, ApiException.kindForStatus(429));
        assertEquals(ApiException.Kind.SERVER, ApiException.kindForStatus(503));
        assertEquals(ApiException.Kind.BAD_REQUEST, ApiException.kindForStatus(400));

        String auth = ErrorText.describe("Claude",
                new ApiException(ApiException.Kind.AUTH, 401, "invalid x-api-key"));
        assertTrue(auth.contains("401") && auth.contains("API key"));
        String net = ErrorText.describe("OpenAI speech-to-text",
                new ApiException(ApiException.Kind.NETWORK, 0, "Unable to resolve host"));
        assertTrue(net.startsWith("No connection"));
        String bad = ErrorText.describe("Claude",
                new ApiException(ApiException.Kind.BAD_REQUEST, 404, "model: claude-x"));
        assertTrue(bad.contains("404") && bad.contains("model: claude-x"));
    }

    @Test
    public void parseDeepgramReadsFirstAlternativeOfFirstChannel() throws Exception {
        String json = "{\"metadata\":{\"request_id\":\"x\"},\"results\":{\"channels\":[{\"detected_language\":\"en\","
                + "\"alternatives\":[{\"transcript\":\"How tall is Everest?\",\"confidence\":0.99,\"words\":[]},"
                + "{\"transcript\":\"other\"}]},{\"alternatives\":[{\"transcript\":\"second channel\"}]}]}}";
        assertEquals("How tall is Everest?", ApiRequests.parseDeepgram(json));
    }

    @Test
    public void parseDeepgramEmptyOrMalformedIsAnError() {
        String[] bad = {
            "{\"results\":{\"channels\":[{\"alternatives\":[{\"transcript\":\"\"}]}]}}",
            "{\"results\":{\"channels\":[]}}",
            "{\"results\":{\"channels\":[{\"alternatives\":[]}]}}",
            "{\"no\":\"results\"}",
            "not json"
        };
        for (String json : bad) {
            try {
                ApiRequests.parseDeepgram(json);
                fail(json);
            } catch (ApiException e) {
                assertEquals(ApiException.Kind.EMPTY, e.kind);
            }
        }
    }

    @Test
    public void deepgramErrorBodyAndPaymentStatus() {
        assertEquals("Invalid credentials.", ApiRequests.errorMessage(
                "{\"err_code\":\"INVALID_AUTH\",\"err_msg\":\"Invalid credentials.\",\"request_id\":\"r\"}"));
        assertEquals(ApiException.Kind.PAYMENT, ApiException.kindForStatus(402));
        String text = ErrorText.describe("Deepgram speech-to-text",
                new ApiException(ApiException.Kind.PAYMENT, 402, ""));
        assertTrue(text.contains("402") && text.contains("credit"));
        String auth = ErrorText.describe("Deepgram speech-to-text",
                new ApiException(ApiException.Kind.AUTH, 401, "Invalid credentials."));
        assertTrue(auth.startsWith("Deepgram speech-to-text rejected the API key (401)"));
    }
}
