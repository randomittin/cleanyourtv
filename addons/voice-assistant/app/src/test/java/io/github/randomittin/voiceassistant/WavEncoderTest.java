package io.github.randomittin.voiceassistant;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class WavEncoderTest {

    @Test
    public void headerDescribesSixteenKilohertzMono16Bit() {
        byte[] pcm = new byte[3200];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (byte) i;
        }
        byte[] wav = WavEncoder.encode(pcm, pcm.length, 16000);
        assertEquals(44 + 3200, wav.length);
        assertEquals("RIFF", new String(wav, 0, 4, StandardCharsets.US_ASCII));
        assertEquals("WAVE", new String(wav, 8, 4, StandardCharsets.US_ASCII));
        assertEquals("fmt ", new String(wav, 12, 4, StandardCharsets.US_ASCII));
        assertEquals("data", new String(wav, 36, 4, StandardCharsets.US_ASCII));
        ByteBuffer b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(36 + 3200, b.getInt(4));
        assertEquals(16, b.getInt(16));
        assertEquals(1, b.getShort(20));
        assertEquals(1, b.getShort(22));
        assertEquals(16000, b.getInt(24));
        assertEquals(32000, b.getInt(28));
        assertEquals(2, b.getShort(32));
        assertEquals(16, b.getShort(34));
        assertEquals(3200, b.getInt(40));
        assertArrayEquals(pcm, Arrays.copyOfRange(wav, 44, wav.length));
    }

    @Test
    public void usesOnlyTheRequestedPcmLength() {
        byte[] pcm = {1, 2, 3, 4, 9, 9};
        byte[] wav = WavEncoder.encode(pcm, 4, 16000);
        assertEquals(48, wav.length);
        assertEquals(4, ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN).getInt(40));
    }

    @Test
    public void emptyAudioStillHasAValidHeader() {
        byte[] wav = WavEncoder.encode(new byte[0], 0, 16000);
        assertEquals(44, wav.length);
        assertEquals(36, ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN).getInt(4));
    }
}
