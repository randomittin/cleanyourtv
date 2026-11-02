package io.github.randomittin.voiceassistant;

import android.Manifest;
import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The overlay: a dark panel at the bottom of the screen that listens, transcribes, asks the chosen
 * brain, and shows and speaks the answer. OK asks a follow-up, Left/Right switches brain, Down opens
 * settings, Back closes. Reached from the remote's mic button (ASSIST and friends) or the launcher.
 */
public class AssistActivity extends Activity {

    private enum State { LISTENING, TRANSCRIBING, THINKING, ANSWERED, ERROR, TYPE_ONLY }

    private static final int REQUEST_MIC = 41;
    private static final String TAG = Recorder.TAG;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final Conversation conversation = new Conversation();

    private Settings settings;
    private Clients clients;
    private Recorder recorder;
    private TextToSpeech tts;
    private boolean ttsReady;
    private String pendingSpeech;
    private int generation;
    private State state = State.ERROR;
    private boolean awaitingPermission;
    private boolean openingSettings;

    private TextView brainView;
    private TextView statusView;
    private TextView questionView;
    private TextView answerView;
    private TextView hintView;
    private View levelBar;
    private View levelTrack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new Settings(this);
        clients = new Clients(settings);
        BrainIdentity.apply(this);
        buildUi();
        tts = new TextToSpeech(this, status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (!ttsReady) {
                Log.w(TAG, "TextToSpeech unavailable (status " + status + "); answers are shown only");
            } else if (pendingSpeech != null) {
                speak(pendingSpeech);
                pendingSpeech = null;
            }
        });
        route(getIntent(), true);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        route(intent, false);
    }

    /** A typed query from WEB_SEARCH goes straight to the brain; anything else starts listening. */
    private void route(Intent intent, boolean firstLaunch) {
        String query = intent == null ? null : intent.getStringExtra(SearchManager.QUERY);
        if (query != null && !query.trim().isEmpty()) {
            stopEverything();
            submitQuestion(query.trim());
            return;
        }
        if (!firstLaunch && state == State.LISTENING) {
            recorder.finishNow();
            return;
        }
        if (!firstLaunch && (state == State.TRANSCRIBING || state == State.THINKING)) {
            return;
        }
        startListening();
    }

    @Override
    protected void onResume() {
        super.onResume();
        openingSettings = false;
        refreshBrain();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!awaitingPermission && !openingSettings) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        stopEverything();
        network.shutdownNow();
        if (tts != null) {
            tts.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (event.getRepeatCount() == 0) {
                    onOk();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (event.getRepeatCount() == 0) {
                    switchBrain();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_MENU:
                openSettings();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private void onOk() {
        switch (state) {
            case LISTENING:
                recorder.finishNow();
                break;
            case TYPE_ONLY:
                askTyped();
                break;
            case ANSWERED:
            case ERROR:
                startListening();
                break;
            default:
                break;
        }
    }

    // ----- listening -----

    private void startListening() {
        stopEverything();
        String missingKey = settings.sttMissingKeyMessage();
        if (missingKey != null) {
            fail(missingKey + " Press OK to type your question, or Down to open settings.", State.TYPE_ONLY);
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            awaitingPermission = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
            return;
        }
        state = State.LISTENING;
        setStatus("Listening...", Ui.ACCENT);
        answerView.setText("");
        questionView.setText("");
        hintView.setText("Speak now.   OK: done   Left/Right: Claude or ChatGPT   Back: close");
        levelTrack.setVisibility(View.VISIBLE);
        setLevel(0);
        recorder = new Recorder(new Recorder.Listener() {
            @Override
            public void onLevel(double rms, double threshold) {
                setLevel(rms);
            }

            @Override
            public void onFinished(Recorder.Result result) {
                recorder = null;
                heard(result);
            }

            @Override
            public void onFailed(String message) {
                recorder = null;
                fail(message + " Press OK to type your question, or press the mic button to try again.",
                        State.TYPE_ONLY);
            }
        });
        recorder.start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_MIC) {
            return;
        }
        awaitingPermission = false;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            fail("Microphone permission was denied. Press OK to type your question, or allow it in the "
                    + "TV's app settings.", State.TYPE_ONLY);
        }
    }

    private void heard(Recorder.Result result) {
        if (result.peakRms <= EndpointDetector.SILENT_MIC_PEAK) {
            fail("No audio from the remote mic (" + result.source + ", level " + Math.round(result.peakRms)
                    + "). Check the remote is paired and the mic button works, then press OK.", State.ERROR);
            return;
        }
        if (result.reason == EndpointDetector.Decision.NO_SPEECH) {
            fail("I did not hear anything. Press OK and speak.", State.ERROR);
            return;
        }
        state = State.TRANSCRIBING;
        setStatus("Transcribing...", Ui.ACCENT);
        levelTrack.setVisibility(View.GONE);
        hintView.setText("Back: close");
        final byte[] wav = WavEncoder.encode(result.pcm, result.pcm.length, Recorder.SAMPLE_RATE);
        final int gen = ++generation;
        network.execute(() -> {
            try {
                final String text = clients.transcribe(wav);
                main.post(() -> {
                    if (gen == generation) {
                        submitQuestion(text);
                    }
                });
            } catch (ApiException e) {
                Log.w(TAG, "transcription failed: " + e.kind + " " + e.httpStatus);
                main.post(() -> {
                    if (gen == generation) {
                        fail(ErrorText.describe(clients.sttLabel(), e) + " Press OK to try again.", State.ERROR);
                    }
                });
            }
        });
    }

    // ----- asking -----

    private void askTyped() {
        Ui.promptText(this, "Type your question", "", false, "Ask", text -> {
            if (!text.isEmpty()) {
                submitQuestion(text);
            }
        });
    }

    private void submitQuestion(String question) {
        final String brain = settings.brain();
        final String label = Settings.brainLabel(brain);
        boolean hasKey = Settings.BRAIN_CHATGPT.equals(brain) ? settings.hasOpenAiKey() : settings.hasAnthropicKey();
        questionView.setText("“" + question + "”");
        if (!hasKey) {
            fail(label + " needs an API key. Press Down to open settings, or Left/Right to switch.", State.ERROR);
            return;
        }
        stopSpeech();
        conversation.addUser(question);
        final List<Conversation.Message> history = conversation.messages();
        state = State.THINKING;
        levelTrack.setVisibility(View.GONE);
        answerView.setText("");
        setStatus("Asking " + label + "...", Ui.ACCENT);
        hintView.setText("Back: close");
        final int gen = ++generation;
        network.execute(() -> {
            try {
                final String answer = clients.ask(brain, history);
                main.post(() -> {
                    if (gen == generation) {
                        answered(label, answer);
                    }
                });
            } catch (ApiException e) {
                Log.w(TAG, label + " failed: " + e.kind + " " + e.httpStatus);
                main.post(() -> {
                    if (gen == generation) {
                        conversation.dropTrailingUser();
                        fail(ErrorText.describe(label, e) + " Press OK to ask again.", State.ERROR);
                    }
                });
            }
        });
    }

    private void answered(String label, String answer) {
        conversation.addAssistant(answer);
        state = State.ANSWERED;
        setStatus(label, Ui.ACCENT);
        answerView.setTextColor(Ui.TEXT_PRIMARY);
        answerView.setText(answer);
        hintView.setText("OK: ask a follow-up   Left/Right: switch brain   Down: settings   Back: close");
        speak(answer);
    }

    private void fail(String message, State next) {
        state = next;
        levelTrack.setVisibility(View.GONE);
        setStatus("Problem", Ui.WARNING);
        answerView.setTextColor(Ui.WARNING);
        answerView.setText(message);
        hintView.setText("OK: " + (next == State.TYPE_ONLY ? "type a question" : "try again")
                + "   Down: settings   Back: close");
    }

    // ----- brain, settings, speech -----

    private void switchBrain() {
        if (state == State.TRANSCRIBING || state == State.THINKING) {
            return;
        }
        settings.setBrain(Settings.BRAIN_CLAUDE.equals(settings.brain())
                ? Settings.BRAIN_CHATGPT : Settings.BRAIN_CLAUDE);
        BrainIdentity.apply(this);
        refreshBrain();
    }

    private void refreshBrain() {
        String brain = settings.brain();
        brainView.setText("◀  " + Settings.brainLabel(brain) + "  ▶");
    }

    private void openSettings() {
        stopEverything();
        openingSettings = true;
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void speak(String text) {
        if (!ttsReady) {
            pendingSpeech = text;
            return;
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "answer");
    }

    private void stopSpeech() {
        pendingSpeech = null;
        if (tts != null && ttsReady) {
            tts.stop();
        }
    }

    private void stopEverything() {
        generation++;
        if (recorder != null) {
            recorder.cancel();
            recorder = null;
        }
        stopSpeech();
    }

    // ----- views -----

    private void setStatus(String text, int color) {
        statusView.setText(text);
        statusView.setTextColor(color);
    }

    private void setLevel(double rms) {
        double fraction = Math.min(1.0, Math.sqrt(rms / 4000.0));
        int width = (int) (levelTrack.getWidth() * fraction);
        ViewGroup.LayoutParams lp = levelBar.getLayoutParams();
        lp.width = Math.max(width, Ui.dp(this, 6));
        levelBar.setLayoutParams(lp);
    }

    private TextView text(int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.panel);
        int pad = Ui.dp(this, 28);
        panel.setPadding(pad, pad - Ui.dp(this, 6), pad, pad - Ui.dp(this, 6));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        brainView = text(22, Ui.TEXT_SECONDARY, true);
        statusView = text(22, Ui.ACCENT, true);
        statusView.setGravity(Gravity.END);
        header.addView(brainView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(statusView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        panel.addView(header);

        levelTrack = new FrameLayout(this);
        levelTrack.setBackgroundColor(0x33FFFFFF);
        levelBar = new View(this);
        levelBar.setBackgroundColor(Ui.ACCENT);
        ((FrameLayout) levelTrack).addView(levelBar, new FrameLayout.LayoutParams(Ui.dp(this, 6),
                ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 6));
        trackParams.topMargin = Ui.dp(this, 12);
        panel.addView(levelTrack, trackParams);

        questionView = text(24, Ui.TEXT_SECONDARY, false);
        questionView.setMaxLines(2);
        questionView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        qp.topMargin = Ui.dp(this, 12);
        panel.addView(questionView, qp);

        answerView = text(34, Ui.TEXT_PRIMARY, false);
        answerView.setLineSpacing(0, 1.1f);
        answerView.setMinLines(2);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin = Ui.dp(this, 10);
        panel.addView(answerView, ap);

        hintView = text(18, Color.argb(200, 176, 186, 197), false);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = Ui.dp(this, 16);
        panel.addView(hintView, hp);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        int margin = Ui.dp(this, 48);
        panelParams.setMargins(margin, margin, margin, margin);
        root.addView(panel, panelParams);
        setContentView(root);
        refreshBrain();
    }
}
