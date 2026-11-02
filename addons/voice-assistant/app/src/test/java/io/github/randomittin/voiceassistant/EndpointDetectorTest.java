package io.github.randomittin.voiceassistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EndpointDetectorTest {

    private static final int CHUNK = 50;

    private static EndpointDetector.Decision feedFor(EndpointDetector d, double rms, int ms) {
        EndpointDetector.Decision last = EndpointDetector.Decision.CONTINUE;
        for (int t = 0; t < ms; t += CHUNK) {
            last = d.feed(rms, CHUNK);
            if (last != EndpointDetector.Decision.CONTINUE) {
                return last;
            }
        }
        return last;
    }

    @Test
    public void silenceOnlyGivesUpAfterNoSpeechWindow() {
        EndpointDetector d = new EndpointDetector();
        assertEquals(EndpointDetector.Decision.CONTINUE, feedFor(d, 40, EndpointDetector.NO_SPEECH_MS - CHUNK));
        assertEquals(EndpointDetector.Decision.NO_SPEECH, d.feed(40, CHUNK));
        assertFalse(d.speechStarted());
    }

    @Test
    public void speechThenSilenceEndsAfterTwelveHundredMs() {
        EndpointDetector d = new EndpointDetector();
        feedFor(d, 30, 500);
        assertEquals(EndpointDetector.Decision.CONTINUE, feedFor(d, 2000, 1000));
        assertTrue(d.speechStarted());
        assertEquals(EndpointDetector.Decision.CONTINUE,
                feedFor(d, 30, EndpointDetector.SILENCE_END_MS - CHUNK));
        assertEquals(EndpointDetector.Decision.END_OF_SPEECH, d.feed(30, CHUNK));
    }

    @Test
    public void pauseShorterThanSilenceWindowDoesNotEnd() {
        EndpointDetector d = new EndpointDetector();
        feedFor(d, 2000, 500);
        assertEquals(EndpointDetector.Decision.CONTINUE, feedFor(d, 30, 1000));
        assertEquals(EndpointDetector.Decision.CONTINUE, feedFor(d, 2000, 500));
        assertEquals(EndpointDetector.Decision.CONTINUE, feedFor(d, 30, 1000));
    }

    @Test
    public void continuousSpeechStopsAtFifteenSeconds() {
        EndpointDetector d = new EndpointDetector();
        assertEquals(EndpointDetector.Decision.CONTINUE, feedFor(d, 3000, EndpointDetector.MAX_DURATION_MS - CHUNK));
        assertEquals(EndpointDetector.Decision.MAX_DURATION, d.feed(3000, CHUNK));
    }

    @Test
    public void singleClickIsNotSpeech() {
        EndpointDetector d = new EndpointDetector();
        feedFor(d, 30, 300);
        d.feed(5000, CHUNK);
        feedFor(d, 30, 300);
        assertFalse(d.speechStarted());
    }

    @Test
    public void noisyRoomRaisesThresholdSoNoiseIsNotSpeech() {
        EndpointDetector d = new EndpointDetector();
        // Steady room noise at 400 sits under 3x its own floor and must never count as speech.
        feedFor(d, 400, 3000);
        assertFalse(d.speechStarted());
        assertTrue(d.threshold() >= 1000);
        // Speech clearly above it still registers.
        feedFor(d, 3000, 200);
        assertTrue(d.speechStarted());
    }

    @Test
    public void quietRoomUsesMinimumThreshold() {
        EndpointDetector d = new EndpointDetector();
        feedFor(d, 5, 300);
        assertEquals(EndpointDetector.MIN_THRESHOLD, d.threshold(), 0.001);
    }

    @Test
    public void peakAndSilentMicDetection() {
        EndpointDetector dead = new EndpointDetector();
        feedFor(dead, 0, 1000);
        assertTrue(dead.micWasSilent());

        EndpointDetector live = new EndpointDetector();
        feedFor(live, 20, 500);
        live.feed(1234, CHUNK);
        assertFalse(live.micWasSilent());
        assertEquals(1234, live.peakRms(), 0.001);
    }

    @Test
    public void rmsOfKnownSamples() {
        short[] samples = {3, -3, 3, -3, 99};
        assertEquals(3.0, EndpointDetector.rms(samples, 4), 1e-9);
        assertEquals(0.0, EndpointDetector.rms(samples, 0), 1e-9);
        short[] square = {1000, -1000};
        assertEquals(1000.0, EndpointDetector.rms(square, 2), 1e-9);
    }
}
