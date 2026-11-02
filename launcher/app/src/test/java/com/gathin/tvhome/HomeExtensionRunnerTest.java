package com.gathin.tvhome;

import android.view.KeyEvent;
import android.view.View;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HomeExtensionRunnerTest {
    private static final ForkKeys KEYS = ForkKeys.from(new HashMap<>());

    private static final class FakeTopBar implements HomeExtensionRunner.TopBar {
        int visibility = View.VISIBLE;
        @Override public int visibility() { return visibility; }
        @Override public void setVisibility(int v) { visibility = v; }
    }

    /** Records its calls; {@code failIn} names the call that throws. */
    private static class Probe implements HomeExtension, ResumeAware, MinuteAware, FocusAware, TileNavAware, KeyAware {
        final List<String> calls = new ArrayList<>();
        final FakeTopBar bar;
        String failIn = "";
        boolean hideBarOnAttach;
        boolean panel;
        boolean consume = true;
        boolean navResult;
        View scope = new View(null);

        Probe(FakeTopBar bar) { this.bar = bar; }

        private void record(String what) {
            calls.add(what);
            if (what.equals(failIn)) throw new IllegalStateException(what);
        }
        @Override public void attach(HomeHost host, ForkKeys keys) {
            if (hideBarOnAttach) bar.setVisibility(View.GONE);
            record("attach");
        }
        @Override public void detach() { calls.add("detach"); }
        @Override public void onResume() { record("onResume"); }
        @Override public void onPause() { record("onPause"); }
        @Override public void onMinute() { record("onMinute"); }
        @Override public void onTileFocused(int position, String pkg) { record("onTileFocused"); }
        @Override public boolean onTileVerticalKey(int position, int keyCode) { record("nav"); return navResult; }
        @Override public boolean onKey(KeyEvent event) { record("onKey"); return consume; }
        @Override public View focusScope() { return scope; }
        @Override public boolean ownsOpenPanel() { return panel; }
    }

    private static HomeExtensionRunner runner(FakeTopBar bar, List<String> logs, boolean inside, HomeExtension... ext) {
        return new HomeExtensionRunner(null, KEYS, Arrays.asList(ext), bar, (focus, scope) -> inside,
                (message, t) -> logs.add(message));
    }

    @Test public void anEmptyListIsANoOp() {
        FakeTopBar bar = new FakeTopBar();
        HomeExtensionRunner r = runner(bar, new ArrayList<>(), true);
        r.attachAll();
        r.onResume();
        r.onPause();
        r.onMinute();
        r.onTileFocused(0, "p");
        assertFalse(r.onTileVerticalKey(0, KeyEvent.KEYCODE_DPAD_DOWN));
        assertFalse(r.onKey(null, KeyEvent.KEYCODE_DPAD_DOWN, new View(null)));
        r.detachAll();
        assertEquals(0, r.activeCount());
    }

    @Test public void eventsReachEveryExtensionInOrder() {
        FakeTopBar bar = new FakeTopBar();
        Probe a = new Probe(bar), b = new Probe(bar);
        HomeExtensionRunner r = runner(bar, new ArrayList<>(), true, a, b);
        r.attachAll();
        r.onResume();
        r.onMinute();
        r.onTileFocused(3, "pkg");
        r.onPause();
        r.detachAll();
        assertEquals(Arrays.asList("attach", "onResume", "onMinute", "onTileFocused", "onPause", "detach"), a.calls);
        assertEquals(a.calls, b.calls);
    }

    @Test public void aFailingExtensionIsDetachedAloneAndTheOthersCarryOn() {
        FakeTopBar bar = new FakeTopBar();
        Probe bad = new Probe(bar), good = new Probe(bar);
        bad.failIn = "onMinute";
        List<String> logs = new ArrayList<>();
        HomeExtensionRunner r = runner(bar, logs, true, bad, good);
        r.attachAll();
        r.onMinute();
        r.onMinute();
        assertEquals(1, r.activeCount());
        assertEquals(Arrays.asList("attach", "onMinute", "detach"), bad.calls);
        assertEquals(Arrays.asList("attach", "onMinute", "onMinute"), good.calls);
        assertEquals(1, logs.size());
        assertTrue(logs.get(0).contains("Probe"));
    }

    @Test public void aFailureInAttachDropsThatExtensionOnly() {
        FakeTopBar bar = new FakeTopBar();
        Probe bad = new Probe(bar), good = new Probe(bar);
        bad.failIn = "attach";
        HomeExtensionRunner r = runner(bar, new ArrayList<>(), true, bad, good);
        r.attachAll();
        r.onResume();
        assertEquals(Arrays.asList("attach", "detach"), bad.calls);
        assertEquals(Arrays.asList("attach", "onResume"), good.calls);
    }

    @Test public void topButtonsComeBackWhenTheExtensionThatHidThemFailsInAttach() {
        FakeTopBar bar = new FakeTopBar();
        Probe bad = new Probe(bar);
        bad.hideBarOnAttach = true;
        bad.failIn = "attach";
        runner(bar, new ArrayList<>(), true, bad).attachAll();
        assertEquals(View.VISIBLE, bar.visibility);
    }

    @Test public void topButtonsComeBackWhenTheExtensionThatHidThemFailsLater() {
        FakeTopBar bar = new FakeTopBar();
        Probe bad = new Probe(bar);
        bad.hideBarOnAttach = true;
        bad.failIn = "onResume";
        HomeExtensionRunner r = runner(bar, new ArrayList<>(), true, bad);
        r.attachAll();
        assertEquals(View.GONE, bar.visibility);
        r.onResume();
        assertEquals(View.VISIBLE, bar.visibility);
    }

    @Test public void aFailingNeighbourDoesNotRestoreButtonsAnotherExtensionHid() {
        FakeTopBar bar = new FakeTopBar();
        Probe hider = new Probe(bar), other = new Probe(bar);
        hider.hideBarOnAttach = true;
        other.failIn = "onMinute";
        HomeExtensionRunner r = runner(bar, new ArrayList<>(), true, hider, other);
        r.attachAll();
        r.onMinute();
        assertEquals(View.GONE, bar.visibility);
    }

    @Test public void tileNavStopsAtTheFirstExtensionThatTakesIt() {
        FakeTopBar bar = new FakeTopBar();
        Probe a = new Probe(bar), b = new Probe(bar);
        b.navResult = true;
        HomeExtensionRunner r = runner(bar, new ArrayList<>(), true, a, b);
        assertTrue(r.onTileVerticalKey(5, KeyEvent.KEYCODE_DPAD_DOWN));
        assertEquals(Collections.singletonList("nav"), a.calls);
    }

    @Test public void aThrowingTileNavIsDroppedAndReportsNotTaken() {
        FakeTopBar bar = new FakeTopBar();
        Probe bad = new Probe(bar);
        bad.failIn = "nav";
        HomeExtensionRunner r = runner(bar, new ArrayList<>(), true, bad);
        assertFalse(r.onTileVerticalKey(0, KeyEvent.KEYCODE_DPAD_DOWN));
        assertEquals(0, r.activeCount());
    }

    @Test public void keysReachAnExtensionOnlyWhileFocusIsInsideItsScope() {
        FakeTopBar bar = new FakeTopBar();
        Probe p = new Probe(bar);
        View focus = new View(null);
        assertTrue(runner(bar, new ArrayList<>(), true, p).onKey(null, KeyEvent.KEYCODE_DPAD_RIGHT, focus));
        assertEquals(Collections.singletonList("onKey"), p.calls);
        Probe outside = new Probe(bar);
        assertFalse(runner(bar, new ArrayList<>(), false, outside).onKey(null, KeyEvent.KEYCODE_DPAD_RIGHT, focus));
        assertTrue(outside.calls.isEmpty());
        Probe noScope = new Probe(bar);
        noScope.scope = null;
        assertFalse(runner(bar, new ArrayList<>(), true, noScope).onKey(null, KeyEvent.KEYCODE_DPAD_RIGHT, focus));
        assertFalse(runner(bar, new ArrayList<>(), true, new Probe(bar)).onKey(null, KeyEvent.KEYCODE_DPAD_RIGHT, null));
    }

    @Test public void backGoesToAnExtensionOnlyWithAnOpenPanelAndOtherKeysNever() {
        FakeTopBar bar = new FakeTopBar();
        View focus = new View(null);
        Probe closed = new Probe(bar);
        assertFalse(runner(bar, new ArrayList<>(), true, closed).onKey(null, KeyEvent.KEYCODE_BACK, focus));
        assertTrue(closed.calls.isEmpty());
        Probe open = new Probe(bar);
        open.panel = true;
        assertTrue(runner(bar, new ArrayList<>(), true, open).onKey(null, KeyEvent.KEYCODE_BACK, focus));
        Probe other = new Probe(bar);
        other.panel = true;
        assertFalse(runner(bar, new ArrayList<>(), true, other).onKey(null, KeyEvent.KEYCODE_HOME, focus));
        assertTrue(other.calls.isEmpty());
    }

    @Test public void aThrowingKeyHandlerIsDroppedAndTheKeyIsNotConsumed() {
        FakeTopBar bar = new FakeTopBar();
        Probe bad = new Probe(bar);
        bad.failIn = "onKey";
        HomeExtensionRunner r = runner(bar, new ArrayList<>(), true, bad);
        assertFalse(r.onKey(null, KeyEvent.KEYCODE_DPAD_LEFT, new View(null)));
        assertEquals(0, r.activeCount());
    }

    @Test public void onlyDpadCentreAndMediaKeysAreOffered() {
        int[] offered = {KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS};
        for (int code : offered) assertTrue("offered " + code, HomeExtensionRunner.offered(code, false));
        int[] withheld = {KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_TV_INPUT, KeyEvent.KEYCODE_A, 4062};
        for (int code : withheld) assertFalse("withheld " + code, HomeExtensionRunner.offered(code, true));
    }

    @Test public void backIsOfferedOnlyWhileAnOpenPanelIsOwned() {
        assertFalse(HomeExtensionRunner.offered(KeyEvent.KEYCODE_BACK, false));
        assertFalse(HomeExtensionRunner.offered(KeyEvent.KEYCODE_ESCAPE, false));
        assertTrue(HomeExtensionRunner.offered(KeyEvent.KEYCODE_BACK, true));
        assertTrue(HomeExtensionRunner.offered(KeyEvent.KEYCODE_ESCAPE, true));
    }
}
