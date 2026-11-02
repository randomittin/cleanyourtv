package com.gathin.tvhome;

import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

/**
 * Fans Home's events out to the extensions, each call isolated: an extension that throws is logged, detached (it
 * alone) and dropped, and if it had hidden the top buttons they come back. Android types are injected so the
 * policy runs in plain JVM tests.
 */
final class HomeExtensionRunner {
    /** Read and write the visibility of the header's button row. */
    interface TopBar {
        int visibility();

        void setVisibility(int visibility);
    }

    private final HomeHost host;
    private final ForkKeys keys;
    private final List<HomeExtension> active;
    private final Set<HomeExtension> hidTopBar = new HashSet<>();
    private final TopBar topBar;
    private final BiPredicate<View, View> focusInside;
    private final BiConsumer<String, Throwable> log;

    HomeExtensionRunner(HomeHost host, ForkKeys keys, List<HomeExtension> extensions, TopBar topBar,
                        BiPredicate<View, View> focusInside, BiConsumer<String, Throwable> log) {
        this.host = host;
        this.keys = keys;
        this.active = new ArrayList<>(extensions);
        this.topBar = topBar;
        this.focusInside = focusInside;
        this.log = log;
    }

    /** Extensions still running. */
    int activeCount() {
        return active.size();
    }

    void attachAll() {
        for (HomeExtension e : new ArrayList<>(active)) {
            int before = topBar.visibility();
            try {
                e.attach(host, keys);
            } catch (Throwable t) {
                fail(e, "attach", t, before);
                continue;
            }
            if (before == View.VISIBLE && topBar.visibility() != View.VISIBLE) hidTopBar.add(e);
        }
    }

    void detachAll() {
        for (HomeExtension e : new ArrayList<>(active)) {
            try {
                e.detach();
            } catch (Throwable t) {
                log.accept("extension " + name(e) + " failed in detach", t);
            }
        }
        active.clear();
    }

    void onResume() {
        for (HomeExtension e : new ArrayList<>(active)) {
            if (e instanceof ResumeAware) guarded(e, "onResume", () -> ((ResumeAware) e).onResume());
        }
    }

    void onPause() {
        for (HomeExtension e : new ArrayList<>(active)) {
            if (e instanceof ResumeAware) guarded(e, "onPause", () -> ((ResumeAware) e).onPause());
        }
    }

    void onMinute() {
        for (HomeExtension e : new ArrayList<>(active)) {
            if (e instanceof MinuteAware) guarded(e, "onMinute", () -> ((MinuteAware) e).onMinute());
        }
    }

    void onTileFocused(int position, String packageName) {
        for (HomeExtension e : new ArrayList<>(active)) {
            if (e instanceof FocusAware) guarded(e, "onTileFocused", () -> ((FocusAware) e).onTileFocused(position, packageName));
        }
    }

    /** True when an extension took Down from the last tile row (it moved focus to its own view). */
    boolean onTileVerticalKey(int position, int keyCode) {
        for (HomeExtension e : new ArrayList<>(active)) {
            if (!(e instanceof TileNavAware)) continue;
            try {
                if (((TileNavAware) e).onTileVerticalKey(position, keyCode)) return true;
            } catch (Throwable t) {
                fail(e, "onTileVerticalKey", t, topBar.visibility());
            }
        }
        return false;
    }

    /**
     * True when an extension consumed the key. Offered only if {@link #offered} says so and the focused view is
     * inside the extension's own {@link KeyAware#focusScope()}.
     */
    boolean onKey(KeyEvent event, int keyCode, View focus) {
        if (focus == null) return false;
        for (HomeExtension e : new ArrayList<>(active)) {
            if (!(e instanceof KeyAware)) continue;
            KeyAware aware = (KeyAware) e;
            try {
                View scope = aware.focusScope();
                if (scope == null || !focusInside.test(focus, scope)) continue;
                if (!offered(keyCode, aware.ownsOpenPanel())) continue;
                if (aware.onKey(event)) return true;
            } catch (Throwable t) {
                fail(e, "onKey", t, topBar.visibility());
            }
        }
        return false;
    }

    /**
     * Keys an extension may see: D-pad, centre/enter and media keys; BACK and ESCAPE only while it owns an open
     * panel. Everything else (Home, Menu, vendor keys, volume) stays Home's.
     */
    static boolean offered(int keyCode, boolean ownsOpenPanel) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_STOP:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                return ownsOpenPanel;
            default:
                return false;
        }
    }

    private void guarded(HomeExtension e, String what, Runnable call) {
        int before = topBar.visibility();
        try {
            call.run();
        } catch (Throwable t) {
            fail(e, what, t, before);
        }
    }

    /** Logs, detaches and drops only {@code e}; brings the top buttons back if {@code e} was the one that hid them. */
    private void fail(HomeExtension e, String what, Throwable t, int topBarBefore) {
        log.accept("extension " + name(e) + " failed in " + what + ", detached", t);
        active.remove(e);
        try {
            e.detach();
        } catch (Throwable detachFailure) {
            log.accept("extension " + name(e) + " failed in detach", detachFailure);
        }
        boolean hid = hidTopBar.remove(e) || topBarBefore == View.VISIBLE && topBar.visibility() != View.VISIBLE;
        if (hid && topBar.visibility() != View.VISIBLE) topBar.setVisibility(View.VISIBLE);
    }

    private static String name(HomeExtension e) {
        return e.getClass().getSimpleName();
    }
}
