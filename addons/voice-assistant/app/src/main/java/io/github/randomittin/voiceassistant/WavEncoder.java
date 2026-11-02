package io.github.randomittin.voiceassistant;

/** Wraps raw 16-bit little-endian mono PCM in a canonical 44-byte-header WAV file. */
public final class WavEncoder {

    public static final int HEADER_BYTES = 44;

    private WavEncoder() {
    }

    public static byte[] encode(byte[] pcm, int pcmLength, int sampleRate) {
        int channels = 1;
        int bitsPerSample = 16;
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        byte[] wav = new byte[HEADER_BYTES + pcmLength];
        putAscii(wav, 0, "RIFF");
        putInt(wav, 4, 36 + pcmLength);
        putAscii(wav, 8, "WAVE");
        putAscii(wav, 12, "fmt ");
        putInt(wav, 16, 16);
        putShort(wav, 20, 1);
        putShort(wav, 22, channels);
        putInt(wav, 24, sampleRate);
        putInt(wav, 28, byteRate);
        putShort(wav, 32, channels * bitsPerSample / 8);
        putShort(wav, 34, bitsPerSample);
        putAscii(wav, 36, "data");
        putInt(wav, 40, pcmLength);
        System.arraycopy(pcm, 0, wav, HEADER_BYTES, pcmLength);
        return wav;
    }

    private static void putAscii(byte[] out, int at, String s) {
        for (int i = 0; i < s.length(); i++) {
            out[at + i] = (byte) s.charAt(i);
        }
    }

    private static void putInt(byte[] out, int at, int v) {
        out[at] = (byte) v;
        out[at + 1] = (byte) (v >>> 8);
        out[at + 2] = (byte) (v >>> 16);
        out[at + 3] = (byte) (v >>> 24);
    }

    private static void putShort(byte[] out, int at, int v) {
        out[at] = (byte) v;
        out[at + 1] = (byte) (v >>> 8);
    }
}
