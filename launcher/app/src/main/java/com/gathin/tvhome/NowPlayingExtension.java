package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The Now Playing card in HomeHost.npSlot: art, title, artist, progress and prev / play-pause / next. Reads media
 * sessions through the GuardRevival listener component; without the notification-listener grant the card stays GONE.
 * Callbacks, not polling; registered only while Home is resumed.
 */
final class NowPlayingExtension implements HomeExtension, ResumeAware, MinuteAware, KeyAware {
    private static final int ART_MAX_PX = 256;
    private static final int PROGRESS_MAX = 1000;

    private HomeHost host;
    private MediaSessionManager manager;
    private ComponentName listener;
    private View card;
    private ImageView art;
    private TextView title, artist, elapsed, total;
    private View progressRow;
    private ProgressBar progress;
    private ImageButton prev, playPause, next;

    private boolean started;
    private final List<MediaController> controllers = new ArrayList<>();
    private final List<MediaController.Callback> callbacks = new ArrayList<>();
    private MediaController current;
    private NowPlayingModel.Snapshot shown;
    private ValueAnimator animator;
    private long lastShownSecond = -1;
    private Bitmap artSource;
    private int appliedPlayIcon;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = list -> rebuild(list);

    @Override public void attach(HomeHost host, ForkKeys keys) {
        if (!keys.nowPlaying) return;
        this.host = host;
        Activity activity = host.activity();
        manager = (MediaSessionManager) activity.getSystemService(Activity.MEDIA_SESSION_SERVICE);
        listener = host.mediaSessionListener();
        ViewGroup slot = host.npSlot();
        card = LayoutInflater.from(activity).inflate(R.layout.np_card, slot, false);
        slot.addView(card);
        art = card.findViewById(R.id.npArt);
        title = card.findViewById(R.id.npTitle);
        artist = card.findViewById(R.id.npArtist);
        elapsed = card.findViewById(R.id.npElapsed);
        total = card.findViewById(R.id.npTotal);
        progressRow = card.findViewById(R.id.npProgressRow);
        progress = card.findViewById(R.id.npProgress);
        prev = card.findViewById(R.id.npPrev);
        playPause = card.findViewById(R.id.npPlayPause);
        next = card.findViewById(R.id.npNext);
        applyFonts(activity);
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 14f * host.density());
            }
        });
        art.setClipToOutline(true);
        prev.setOnClickListener(v -> { if (current != null) current.getTransportControls().skipToPrevious(); });
        next.setOnClickListener(v -> { if (current != null) current.getTransportControls().skipToNext(); });
        playPause.setOnClickListener(v -> {
            if (current == null || shown == null) return;
            if (shown.state == NowPlayingModel.STATE_PLAYING) current.getTransportControls().pause();
            else current.getTransportControls().play();
        });
        card.setOnClickListener(v -> openSession());
        setHidden();
        start();
    }

    @Override public void detach() {
        stop();
        if (card != null && card.getParent() instanceof ViewGroup) ((ViewGroup) card.getParent()).removeView(card);
        if (host != null) host.npSlot().setVisibility(View.GONE);
        card = null;
        host = null;
    }

    @Override public void onResume() {
        if (host != null) start();
    }

    @Override public void onPause() {
        stop();
    }

    @Override public void onMinute() {
        if (host == null || shown == null || !NowPlayingModel.cardVisible(shown)) return;
        // Not animating (hidden, reduced motion, or paused): keep the static progress current once a minute.
        if (animator == null || !animator.isRunning()) renderProgress(SystemClock.elapsedRealtime());
    }

    @Override public boolean onKey(KeyEvent event) {
        return false;
    }

    @Override public View focusScope() {
        return card != null && card.getVisibility() == View.VISIBLE ? card : null;
    }

    private void start() {
        if (started || manager == null) return;
        try {
            manager.addOnActiveSessionsChangedListener(sessionsChanged, listener, host.handler());
            started = true;
            rebuild(manager.getActiveSessions(listener));
        } catch (SecurityException notGranted) {
            // Notification access is not granted: stay hidden. The next onResume tries again.
            started = false;
            setHidden();
        }
    }

    private void stop() {
        cancelAnimator();
        if (manager != null) {
            try {
                manager.removeOnActiveSessionsChangedListener(sessionsChanged);
            } catch (SecurityException ignored) {
                // Nothing registered without the grant.
            }
        }
        started = false;
        clearControllers();
    }

    private void clearControllers() {
        for (int i = 0; i < controllers.size(); i++) controllers.get(i).unregisterCallback(callbacks.get(i));
        controllers.clear();
        callbacks.clear();
        current = null;
    }

    private void rebuild(List<MediaController> sessions) {
        if (host == null) return;
        clearControllers();
        if (sessions != null) {
            for (MediaController controller : sessions) {
                MediaController.Callback cb = new MediaController.Callback() {
                    @Override public void onPlaybackStateChanged(PlaybackState state) { refresh(); }
                    @Override public void onMetadataChanged(MediaMetadata metadata) { refresh(); }
                    @Override public void onSessionDestroyed() { refresh(); }
                };
                controller.registerCallback(cb, host.handler());
                controllers.add(controller);
                callbacks.add(cb);
            }
        }
        refresh();
    }

    private void refresh() {
        if (host == null) return;
        List<NowPlayingModel.Snapshot> snaps = new ArrayList<>(controllers.size());
        for (MediaController controller : controllers) snaps.add(snapshot(controller));
        NowPlayingModel.Snapshot pick = NowPlayingModel.reduce(snaps);
        if (!NowPlayingModel.cardVisible(pick)) {
            setHidden();
            return;
        }
        current = null;
        for (MediaController controller : controllers) {
            if (controller.getSessionToken().equals(pick.token)) current = controller;
        }
        shown = pick;
        setVisible();
        render();
    }

    private static NowPlayingModel.Snapshot snapshot(MediaController controller) {
        PlaybackState ps = controller.getPlaybackState();
        MediaMetadata md = controller.getMetadata();
        int state = NowPlayingModel.STATE_OTHER;
        long pos = 0;
        long updated = 0;
        float speed = 0f;
        if (ps != null) {
            if (ps.getState() == PlaybackState.STATE_PLAYING) state = NowPlayingModel.STATE_PLAYING;
            else if (ps.getState() == PlaybackState.STATE_PAUSED) state = NowPlayingModel.STATE_PAUSED;
            pos = ps.getPosition();
            updated = ps.getLastPositionUpdateTime();
            speed = ps.getPlaybackSpeed();
        }
        String t = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
        String a = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        long dur = md == null ? 0 : md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        return new NowPlayingModel.Snapshot(controller.getSessionToken(), state, t, a, pos, dur, updated, speed);
    }

    private void render() {
        title.setText(shown.title == null || shown.title.isEmpty() ? card.getContext().getString(R.string.np_unknown_title) : shown.title);
        artist.setText(shown.artist == null ? "" : shown.artist);
        artist.setVisibility(shown.artist == null || shown.artist.isEmpty() ? View.GONE : View.VISIBLE);
        int icon = shown.state == NowPlayingModel.STATE_PLAYING ? R.drawable.np_ic_pause : R.drawable.np_ic_play;
        if (icon != appliedPlayIcon) {
            playPause.setImageResource(icon);
            appliedPlayIcon = icon;
        }
        card.setFocusable(sessionActivity() != null);
        applyArt();
        progressRow.setVisibility(NowPlayingModel.showProgress(shown) ? View.VISIBLE : View.GONE);
        total.setText(NowPlayingModel.formatTime(shown.durationMs));
        restartProgress();
    }

    private void applyArt() {
        MediaMetadata md = current == null ? null : current.getMetadata();
        Bitmap src = null;
        if (md != null) {
            src = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (src == null) src = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (src == null) src = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        }
        if (src != null) {
            if (src == artSource) return;
            artSource = src;
            art.setImageBitmap(downsample(src));
            return;
        }
        artSource = null;
        Drawable icon = null;
        try {
            if (current != null) icon = card.getContext().getPackageManager().getApplicationIcon(current.getPackageName());
        } catch (Exception ignored) {
            // Falls through to the launcher icon.
        }
        if (icon == null) icon = card.getContext().getApplicationInfo().loadIcon(card.getContext().getPackageManager());
        art.setImageDrawable(icon);
    }

    private static Bitmap downsample(Bitmap src) {
        int longest = Math.max(src.getWidth(), src.getHeight());
        if (longest <= ART_MAX_PX) return src;
        float scale = ART_MAX_PX / (float) longest;
        int w = Math.max(1, Math.round(src.getWidth() * scale));
        int h = Math.max(1, Math.round(src.getHeight() * scale));
        return Bitmap.createScaledBitmap(src, w, h, true);
    }

    private void restartProgress() {
        cancelAnimator();
        long now = SystemClock.elapsedRealtime();
        renderProgress(now);
        boolean shownNow = card.getVisibility() == View.VISIBLE;
        if (!NowPlayingModel.animateProgress(shown, shownNow, host.reducedMotion(), ValueAnimator.areAnimatorsEnabled())) return;
        long remaining = NowPlayingModel.remainingRealMs(shown, now);
        if (remaining <= 0) return;
        final NowPlayingModel.Snapshot snap = shown;
        ValueAnimator va = ValueAnimator.ofFloat(NowPlayingModel.fractionAt(snap, now), 1f);
        va.setInterpolator(new android.view.animation.LinearInterpolator());
        va.setDuration(remaining);
        va.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            progress.setProgress(Math.round(f * PROGRESS_MAX));
            long second = NowPlayingModel.positionAt(snap, SystemClock.elapsedRealtime()) / 1000L;
            if (second != lastShownSecond) {
                lastShownSecond = second;
                elapsed.setText(NowPlayingModel.formatTime(second * 1000L));
            }
        });
        animator = va;
        va.start();
    }

    private void renderProgress(long nowElapsed) {
        if (!NowPlayingModel.showProgress(shown)) return;
        progress.setProgress(Math.round(NowPlayingModel.fractionAt(shown, nowElapsed) * PROGRESS_MAX));
        long pos = NowPlayingModel.positionAt(shown, nowElapsed);
        lastShownSecond = pos / 1000L;
        elapsed.setText(NowPlayingModel.formatTime(pos));
    }

    private void cancelAnimator() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    private void setVisible() {
        host.npSlot().setVisibility(View.VISIBLE);
        card.setVisibility(View.VISIBLE);
    }

    private void setHidden() {
        cancelAnimator();
        shown = null;
        current = null;
        if (card == null) return;
        card.setFocusable(false);
        card.setVisibility(View.GONE);
        host.npSlot().setVisibility(View.GONE);
        art.setImageDrawable(null);
        artSource = null;
    }

    private PendingIntent sessionActivity() {
        return current == null ? null : current.getSessionActivity();
    }

    private void openSession() {
        PendingIntent pi = sessionActivity();
        if (pi == null) return;
        try {
            pi.send();
        } catch (PendingIntent.CanceledException ignored) {
            // The app withdrew its intent; nothing to open.
        }
    }

    private void applyFonts(Activity activity) {
        title.setTypeface(UiFont.get(activity, R.string.font_medium));
        artist.setTypeface(UiFont.get(activity, R.string.font_body));
        elapsed.setTypeface(UiFont.get(activity, R.string.font_body));
        total.setTypeface(UiFont.get(activity, R.string.font_body));
    }
}
