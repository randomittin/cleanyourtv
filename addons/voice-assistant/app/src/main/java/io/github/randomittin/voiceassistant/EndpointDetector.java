package io.github.randomittin.voiceassistant;

/**
 * Decides when the speaker has finished, from the RMS level of consecutive audio chunks.
 * Pure logic: no Android classes, so it is unit tested on the JVM.
 *
 * The speech threshold follows the room: it is {@link #FLOOR_MULTIPLIER} times a slowly tracked
 * noise floor (never below {@link #MIN_THRESHOLD}, floor capped at {@link #MAX_FLOOR}), so a noisy living room does not keep the
 * recording open and a quiet one does not cut the first word.
 */
public final class EndpointDetector {

    public enum Decision { CONTINUE, END_OF_SPEECH, MAX_DURATION, NO_SPEECH }

    /** Silence after speech that ends the recording. */
    public static final int SILENCE_END_MS = 1200;
    /** Hard cap on one recording. */
    public static final int MAX_DURATION_MS = 15000;
    /** Give up when nobody has spoken after this long. */
    public static final int NO_SPEECH_MS = 6000;
    /** Consecutive loud chunks that count as speech having started (ignores a single click). */
    public static final int SPEECH_CHUNKS_REQUIRED = 2;
    public static final double MIN_THRESHOLD = 250.0;
    public static final double FLOOR_MULTIPLIER = 3.0;
    /** The tracked floor counts for at most this much, so speech from the first chunk is still speech. */
    public static final double MAX_FLOOR = 800.0;
    /** Peak RMS at or under this over a whole recording means the mic delivered nothing. */
    public static final double SILENT_MIC_PEAK = 8.0;

    private double noiseFloor = -1;
    private double peakRms;
    private int elapsedMs;
    private int loudRun;
    private int silenceMs;
    private boolean speechStarted;

    /** Feeds one chunk's RMS (16-bit sample units) lasting {@code chunkMs}. */
    public Decision feed(double rms, int chunkMs) {
        elapsedMs += chunkMs;
        if (rms > peakRms) {
            peakRms = rms;
        }
        if (noiseFloor < 0) {
            noiseFloor = rms;
        }
        double threshold = threshold();
        boolean loud = rms >= threshold;

        if (!speechStarted) {
            if (loud) {
                loudRun++;
                if (loudRun >= SPEECH_CHUNKS_REQUIRED) {
                    speechStarted = true;
                    silenceMs = 0;
                }
            } else {
                loudRun = 0;
                noiseFloor = 0.9 * noiseFloor + 0.1 * rms;
            }
        } else if (loud) {
            silenceMs = 0;
        } else {
            silenceMs += chunkMs;
        }

        if (speechStarted && silenceMs >= SILENCE_END_MS) {
            return Decision.END_OF_SPEECH;
        }
        if (elapsedMs >= MAX_DURATION_MS) {
            return Decision.MAX_DURATION;
        }
        if (!speechStarted && elapsedMs >= NO_SPEECH_MS) {
            return Decision.NO_SPEECH;
        }
        return Decision.CONTINUE;
    }

    public double threshold() {
        return Math.max(MIN_THRESHOLD, Math.min(Math.max(noiseFloor, 0), MAX_FLOOR) * FLOOR_MULTIPLIER);
    }

    public boolean speechStarted() {
        return speechStarted;
    }

    public double peakRms() {
        return peakRms;
    }

    public int elapsedMs() {
        return elapsedMs;
    }

    /** True when the recording carried no signal at all (dead or muted mic path). */
    public boolean micWasSilent() {
        return peakRms <= SILENT_MIC_PEAK;
    }

    /** RMS of the first {@code count} 16-bit samples of {@code samples}. */
    public static double rms(short[] samples, int count) {
        if (count <= 0) {
            return 0;
        }
        double sum = 0;
        for (int i = 0; i < count; i++) {
            double s = samples[i];
            sum += s * s;
        }
        return Math.sqrt(sum / count);
    }
}
