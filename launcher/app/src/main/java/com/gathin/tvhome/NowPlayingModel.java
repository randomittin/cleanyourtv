package com.gathin.tvhome;

import java.util.List;
import java.util.Locale;

/** Pure rules for the Now Playing card: which session leads, where the position is, what shows. No android.*. */
final class NowPlayingModel {
    static final int STATE_PLAYING = 1;
    static final int STATE_PAUSED = 2;
    /** Buffering, stopped, error, none: never shown. */
    static final int STATE_OTHER = 0;

    private NowPlayingModel() {}

    /** One media session as the card sees it. {@code token} is opaque here (a MediaSession.Token in the app). */
    static final class Snapshot {
        final Object token;
        final int state;
        final String title;
        final String artist;
        final long positionMs;
        final long durationMs;
        /** SystemClock.elapsedRealtime() when positionMs was reported (PlaybackState.getLastPositionUpdateTime). */
        final long positionUpdateElapsedMs;
        final float speed;

        Snapshot(Object token, int state, String title, String artist, long positionMs, long durationMs,
                 long positionUpdateElapsedMs, float speed) {
            this.token = token;
            this.state = state;
            this.title = title;
            this.artist = artist;
            this.positionMs = positionMs;
            this.durationMs = durationMs;
            this.positionUpdateElapsedMs = positionUpdateElapsedMs;
            this.speed = speed;
        }
    }

    /** The session to show: a PLAYING one with the newest update, else the newest PAUSED, else null. */
    static Snapshot reduce(List<Snapshot> sessions) {
        Snapshot playing = null;
        Snapshot paused = null;
        for (Snapshot s : sessions) {
            if (s == null) continue;
            if (s.state == STATE_PLAYING) {
                if (playing == null || s.positionUpdateElapsedMs > playing.positionUpdateElapsedMs) playing = s;
            } else if (s.state == STATE_PAUSED) {
                if (paused == null || s.positionUpdateElapsedMs > paused.positionUpdateElapsedMs) paused = s;
            }
        }
        return playing != null ? playing : paused;
    }

    /** The card shows only for a playing or paused session. */
    static boolean cardVisible(Snapshot s) {
        return s != null && (s.state == STATE_PLAYING || s.state == STATE_PAUSED);
    }

    /** Live or unknown length (duration <= 0): no progress bar, no times. */
    static boolean showProgress(Snapshot s) {
        return s != null && s.durationMs > 0;
    }

    /** True when the progress should be animated: visible, playing, known length, motion allowed. */
    static boolean animateProgress(Snapshot s, boolean cardShown, boolean reducedMotion, boolean animatorsEnabled) {
        return cardVisible(s) && cardShown && !reducedMotion && animatorsEnabled
                && s.state == STATE_PLAYING && showProgress(s) && s.speed > 0f;
    }

    /** position + (now - updateTime) * speed while playing, clamped to [0, duration] (>= 0 when duration unknown). */
    static long positionAt(Snapshot s, long nowElapsedMs) {
        long pos = Math.max(0L, s.positionMs);
        if (s.state == STATE_PLAYING && s.speed > 0f) {
            long since = Math.max(0L, nowElapsedMs - s.positionUpdateElapsedMs);
            pos += (long) (since * (double) s.speed);
        }
        if (s.durationMs > 0) pos = Math.min(pos, s.durationMs);
        return Math.max(0L, pos);
    }

    /** Position as a fraction in [0, 1]; 0 when the length is unknown. */
    static float fractionAt(Snapshot s, long nowElapsedMs) {
        if (!showProgress(s)) return 0f;
        return Math.min(1f, Math.max(0f, positionAt(s, nowElapsedMs) / (float) s.durationMs));
    }

    /** Wall-clock ms until the end at the current speed; 0 when not advancing. */
    static long remainingRealMs(Snapshot s, long nowElapsedMs) {
        if (!showProgress(s) || s.speed <= 0f) return 0L;
        long left = s.durationMs - positionAt(s, nowElapsedMs);
        return Math.max(0L, (long) (left / (double) s.speed));
    }

    /** m:ss, or h:mm:ss from one hour; negative reads as 0:00. */
    static String formatTime(long ms) {
        long total = Math.max(0L, ms) / 1000L;
        long h = total / 3600L;
        long m = (total % 3600L) / 60L;
        long sec = total % 60L;
        if (h > 0) return String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec);
        return String.format(Locale.ROOT, "%d:%02d", m, sec);
    }
}
