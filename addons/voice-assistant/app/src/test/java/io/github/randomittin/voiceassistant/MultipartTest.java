package io.github.randomittin.voiceassistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class MultipartTest {

    @Test
    public void buildsFieldsFileAndClosingBoundary() {
        Multipart m = new Multipart("BOUND")
                .field("model", "gpt-4o-mini-transcribe")
                .file("file", "speech.wav", "audio/wav", new byte[]{'A', 'B'});
        String body = new String(m.build(), StandardCharsets.UTF_8);
        assertEquals("multipart/form-data; boundary=BOUND", m.contentType());
        assertEquals("--BOUND\r\n"
                + "Content-Disposition: form-data; name=\"model\"\r\n\r\n"
                + "gpt-4o-mini-transcribe\r\n"
                + "--BOUND\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\n"
                + "Content-Type: audio/wav\r\n\r\n"
                + "AB\r\n"
                + "--BOUND--\r\n", body);
    }

    @Test
    public void buildTwiceDoesNotDoubleTheClosingBoundary() {
        Multipart m = new Multipart("B").field("a", "b");
        m.build();
        String body = new String(m.build(), StandardCharsets.UTF_8);
        assertTrue(body.endsWith("--B--\r\n"));
        assertEquals(1, body.split("--B--", -1).length - 1);
    }
}
