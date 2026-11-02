package io.github.randomittin.voiceassistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SttProviderTest {

    @Test
    public void autoPrefersDeepgramWhenItsKeyIsSet() {
        assertEquals(SttProvider.DEEPGRAM, SttProvider.resolve(SttProvider.AUTO, true));
        assertEquals(SttProvider.OPENAI, SttProvider.resolve(SttProvider.AUTO, false));
    }

    @Test
    public void explicitChoiceWinsOverKeys() {
        assertEquals(SttProvider.OPENAI, SttProvider.resolve(SttProvider.OPENAI, true));
        assertEquals(SttProvider.DEEPGRAM, SttProvider.resolve(SttProvider.DEEPGRAM, false));
    }

    @Test
    public void autoWithEitherKeyIsUsable() {
        assertNull(SttProvider.missingKeyMessage(SttProvider.AUTO, true, false));
        assertNull(SttProvider.missingKeyMessage(SttProvider.AUTO, false, true));
        assertNull(SttProvider.missingKeyMessage(SttProvider.AUTO, true, true));
    }

    @Test
    public void autoWithNoKeyMentionsBothKeys() {
        String message = SttProvider.missingKeyMessage(SttProvider.AUTO, false, false);
        assertNotNull(message);
        assertTrue(message.contains("Deepgram") && message.contains("OpenAI"));
    }

    @Test
    public void explicitChoiceNeedsItsOwnKey() {
        String message = SttProvider.missingKeyMessage(SttProvider.DEEPGRAM, false, true);
        assertNotNull(message);
        assertTrue(message.contains("Deepgram"));
        assertNull(SttProvider.missingKeyMessage(SttProvider.DEEPGRAM, true, false));
        assertNotNull(SttProvider.missingKeyMessage(SttProvider.OPENAI, true, false));
        assertNull(SttProvider.missingKeyMessage(SttProvider.OPENAI, false, true));
    }

    @Test
    public void choiceValidationAndCycle() {
        assertTrue(SttProvider.isChoice("auto"));
        assertTrue(SttProvider.isChoice("deepgram"));
        assertTrue(SttProvider.isChoice("openai"));
        assertFalse(SttProvider.isChoice("whisper"));
        assertFalse(SttProvider.isChoice(null));
        assertEquals(SttProvider.DEEPGRAM, SttProvider.next(SttProvider.AUTO));
        assertEquals(SttProvider.OPENAI, SttProvider.next(SttProvider.DEEPGRAM));
        assertEquals(SttProvider.AUTO, SttProvider.next(SttProvider.OPENAI));
    }
}
