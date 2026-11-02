package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class NowPlayingModelTest {
    private static NowPlayingModel.Snapshot snap(String id, int state, long updated) {
        return new NowPlayingModel.Snapshot(id, state, "t", "a", 10_000, 100_000, updated, 1f);
    }

    private static NowPlayingModel.Snapshot snap(int state, long pos, long dur, long updated, float speed) {
        return new NowPlayingModel.Snapshot("x", state, "t", "a", pos, dur, updated, speed);
    }

    @Test public void emptyListPicksNothing() {
        assertNull(NowPlayingModel.reduce(new ArrayList<>()));
    }

    @Test public void playingBeatsNewerPaused() {
        NowPlayingModel.Snapshot playing = snap("p", NowPlayingModel.STATE_PLAYING, 100);
        NowPlayingModel.Snapshot paused = snap("q", NowPlayingModel.STATE_PAUSED, 900);
        assertSame(playing, NowPlayingModel.reduce(Arrays.asList(paused, playing)));
    }

    @Test public void newestPlayingWins() {
        NowPlayingModel.Snapshot old = snap("a", NowPlayingModel.STATE_PLAYING, 100);
        NowPlayingModel.Snapshot fresh = snap("b", NowPlayingModel.STATE_PLAYING, 200);
        assertSame(fresh, NowPlayingModel.reduce(Arrays.asList(old, fresh)));
    }

    @Test public void newestPausedWhenNonePlaying() {
        NowPlayingModel.Snapshot old = snap("a", NowPlayingModel.STATE_PAUSED, 100);
        NowPlayingModel.Snapshot fresh = snap("b", NowPlayingModel.STATE_PAUSED, 200);
        assertSame(fresh, NowPlayingModel.reduce(Arrays.asList(old, fresh)));
    }

    @Test public void otherStatesNeverPicked() {
        List<NowPlayingModel.Snapshot> list = Arrays.asList(snap("a", NowPlayingModel.STATE_OTHER, 500), null);
        assertNull(NowPlayingModel.reduce(list));
    }

    @Test public void cardVisibleOnlyForPlayingOrPaused() {
        assertFalse(NowPlayingModel.cardVisible(null));
        assertFalse(NowPlayingModel.cardVisible(snap("a", NowPlayingModel.STATE_OTHER, 1)));
        assertTrue(NowPlayingModel.cardVisible(snap("a", NowPlayingModel.STATE_PLAYING, 1)));
        assertTrue(NowPlayingModel.cardVisible(snap("a", NowPlayingModel.STATE_PAUSED, 1)));
    }

    @Test public void progressHiddenForLiveStreams() {
        assertFalse(NowPlayingModel.showProgress(snap(NowPlayingModel.STATE_PLAYING, 0, 0, 0, 1f)));
        assertFalse(NowPlayingModel.showProgress(snap(NowPlayingModel.STATE_PLAYING, 0, -1, 0, 1f)));
        assertTrue(NowPlayingModel.showProgress(snap(NowPlayingModel.STATE_PLAYING, 0, 1, 0, 1f)));
        assertEquals(0f, NowPlayingModel.fractionAt(snap(NowPlayingModel.STATE_PLAYING, 5000, 0, 0, 1f), 1000), 0f);
    }

    @Test public void positionAdvancesWhilePlaying() {
        NowPlayingModel.Snapshot s = snap(NowPlayingModel.STATE_PLAYING, 10_000, 100_000, 1000, 1f);
        assertEquals(15_000, NowPlayingModel.positionAt(s, 6000));
        NowPlayingModel.Snapshot fast = snap(NowPlayingModel.STATE_PLAYING, 10_000, 100_000, 1000, 2f);
        assertEquals(20_000, NowPlayingModel.positionAt(fast, 6000));
    }

    @Test public void positionFrozenWhilePaused() {
        NowPlayingModel.Snapshot s = snap(NowPlayingModel.STATE_PAUSED, 10_000, 100_000, 1000, 1f);
        assertEquals(10_000, NowPlayingModel.positionAt(s, 999_000));
    }

    @Test public void positionClamped() {
        NowPlayingModel.Snapshot s = snap(NowPlayingModel.STATE_PLAYING, 90_000, 100_000, 0, 1f);
        assertEquals(100_000, NowPlayingModel.positionAt(s, 50_000));
        assertEquals(1f, NowPlayingModel.fractionAt(s, 50_000), 0f);
        assertEquals(0, NowPlayingModel.positionAt(snap(NowPlayingModel.STATE_PAUSED, -1, 100_000, 0, 0f), 0));
        assertEquals(0, NowPlayingModel.positionAt(snap(NowPlayingModel.STATE_PLAYING, 5000, 100_000, 9000, 1f), 1000) - 5000);
    }

    @Test public void remainingRealMsAccountsForSpeed() {
        NowPlayingModel.Snapshot s = snap(NowPlayingModel.STATE_PLAYING, 50_000, 100_000, 0, 2f);
        assertEquals(25_000, NowPlayingModel.remainingRealMs(s, 0));
        assertEquals(0, NowPlayingModel.remainingRealMs(snap(NowPlayingModel.STATE_PAUSED, 50_000, 100_000, 0, 0f), 0));
    }

    @Test public void animateOnlyWhenVisiblePlayingAndMotionAllowed() {
        NowPlayingModel.Snapshot s = snap(NowPlayingModel.STATE_PLAYING, 0, 100_000, 0, 1f);
        assertTrue(NowPlayingModel.animateProgress(s, true, false, true));
        assertFalse(NowPlayingModel.animateProgress(s, false, false, true));
        assertFalse(NowPlayingModel.animateProgress(s, true, true, true));
        assertFalse(NowPlayingModel.animateProgress(s, true, false, false));
        assertFalse(NowPlayingModel.animateProgress(snap(NowPlayingModel.STATE_PAUSED, 0, 100_000, 0, 1f), true, false, true));
        assertFalse(NowPlayingModel.animateProgress(snap(NowPlayingModel.STATE_PLAYING, 0, 0, 0, 1f), true, false, true));
    }

    @Test public void formatsTimes() {
        assertEquals("0:00", NowPlayingModel.formatTime(-5));
        assertEquals("0:00", NowPlayingModel.formatTime(999));
        assertEquals("0:59", NowPlayingModel.formatTime(59_999));
        assertEquals("3:05", NowPlayingModel.formatTime(185_000));
        assertEquals("1:01:01", NowPlayingModel.formatTime(3_661_000));
    }
}
