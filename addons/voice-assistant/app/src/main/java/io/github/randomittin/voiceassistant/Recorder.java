package io.github.randomittin.voiceassistant;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;

/**
 * Records 16 kHz mono PCM until the speaker stops, the cap is reached, or {@link #finishNow()}.
 * Tries VOICE_RECOGNITION first and falls back to MIC when that source will not open or delivers
 * pure digital silence (some TV remotes route only one of them). Callbacks run on the main thread.
 */
final class Recorder {

    static final String TAG = "VoiceAssistant";
    static final int SAMPLE_RATE = 16000;
    private static final int CHUNK_MS = 50;
    private static final int CHUNK_SAMPLES = SAMPLE_RATE * CHUNK_MS / 1000;
    /** How long a source may deliver exact zeros before the next source is tried. */
    private static final int DEAD_SOURCE_MS = 800;

    static final class Result {
        final byte[] pcm;
        final String source;
        final double peakRms;
        final boolean speechHeard;
        final EndpointDetector.Decision reason;

        Result(byte[] pcm, String source, double peakRms, boolean speechHeard, EndpointDetector.Decision reason) {
            this.pcm = pcm;
            this.source = source;
            this.peakRms = peakRms;
            this.speechHeard = speechHeard;
            this.reason = reason;
        }
    }

    interface Listener {
        void onLevel(double rms, double threshold);

        void onFinished(Result result);

        void onFailed(String message);
    }

    private static final int[] SOURCES = {MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC};

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private volatile boolean finishRequested;
    private volatile boolean cancelled;
    private Thread thread;

    Recorder(Listener listener) {
        this.listener = listener;
    }

    void start() {
        thread = new Thread(this::run, "voice-recorder");
        thread.start();
    }

    /** OK pressed: stop and use what has been heard so far. */
    void finishNow() {
        finishRequested = true;
    }

    /** Stop and discard; no callback. */
    void cancel() {
        cancelled = true;
    }

    private static String sourceName(int source) {
        return source == MediaRecorder.AudioSource.VOICE_RECOGNITION ? "VOICE_RECOGNITION" : "MIC";
    }

    private void run() {
        String lastProblem = "The microphone could not be opened.";
        for (int i = 0; i < SOURCES.length && !cancelled; i++) {
            boolean lastSource = i == SOURCES.length - 1;
            Outcome outcome = record(SOURCES[i], lastSource);
            if (outcome.result != null) {
                final Result result = outcome.result;
                if (!cancelled) {
                    main.post(() -> listener.onFinished(result));
                }
                return;
            }
            lastProblem = outcome.problem;
            Log.w(TAG, "AudioRecord source " + sourceName(SOURCES[i]) + " unusable: " + outcome.problem);
        }
        final String problem = lastProblem;
        if (!cancelled) {
            main.post(() -> listener.onFailed(problem));
        }
    }

    private static final class Outcome {
        final Result result;
        final String problem;

        Outcome(Result result, String problem) {
            this.result = result;
            this.problem = problem;
        }
    }

    @SuppressLint("MissingPermission") // AssistActivity holds RECORD_AUDIO before it starts a Recorder.
    private Outcome record(int source, boolean lastSource) {
        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer <= 0) {
            return new Outcome(null, "This TV reports no 16 kHz mono recording.");
        }
        AudioRecord audio = null;
        try {
            audio = new AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(minBuffer, CHUNK_SAMPLES * 2 * 4));
            if (audio.getState() != AudioRecord.STATE_INITIALIZED) {
                return new Outcome(null, "The microphone is busy or unavailable.");
            }
            audio.startRecording();
            if (audio.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                return new Outcome(null, "The microphone would not start.");
            }
            return readLoop(audio, source, lastSource);
        } catch (RuntimeException e) {
            return new Outcome(null, "The microphone failed: " + e.getClass().getSimpleName());
        } finally {
            if (audio != null) {
                audio.release();
            }
        }
    }

    private Outcome readLoop(AudioRecord audio, int source, boolean lastSource) {
        EndpointDetector detector = new EndpointDetector();
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        short[] chunk = new short[CHUNK_SAMPLES];
        byte[] bytes = new byte[CHUNK_SAMPLES * 2];
        EndpointDetector.Decision reason = EndpointDetector.Decision.CONTINUE;
        boolean anyNonZero = false;
        while (!cancelled) {
            int read = audio.read(chunk, 0, CHUNK_SAMPLES);
            if (read <= 0) {
                return new Outcome(null, "The microphone stopped delivering audio (" + read + ").");
            }
            for (int i = 0; i < read; i++) {
                if (chunk[i] != 0) {
                    anyNonZero = true;
                }
                bytes[2 * i] = (byte) chunk[i];
                bytes[2 * i + 1] = (byte) (chunk[i] >> 8);
            }
            pcm.write(bytes, 0, read * 2);
            final double rms = EndpointDetector.rms(chunk, read);
            reason = detector.feed(rms, read * 1000 / SAMPLE_RATE);
            final double threshold = detector.threshold();
            main.post(() -> listener.onLevel(rms, threshold));

            if (!anyNonZero && !lastSource && detector.elapsedMs() >= DEAD_SOURCE_MS) {
                return new Outcome(null, "delivered only silence");
            }
            if (finishRequested) {
                reason = EndpointDetector.Decision.END_OF_SPEECH;
            }
            if (reason != EndpointDetector.Decision.CONTINUE) {
                break;
            }
        }
        Log.i(TAG, "AudioRecord source=" + sourceName(source) + " peakRms=" + Math.round(detector.peakRms())
                + " threshold=" + Math.round(detector.threshold()) + " ms=" + detector.elapsedMs()
                + " speech=" + detector.speechStarted() + " reason=" + reason);
        byte[] data = pcm.toByteArray();
        return new Outcome(new Result(data, sourceName(source), detector.peakRms(),
                detector.speechStarted(), reason), null);
    }
}
