package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShelfPlanTest {
    private static final List<ShelfItem> WATCH = Arrays.asList(ShelfItem.watchNext("Show", null, "intent:a", "p", 40));
    private static final List<ShelfItem> APPS = Arrays.asList(ShelfItem.app("com.x", "X"), ShelfItem.app("com.y", "Y"));
    private static final List<ShelfItem> NONE = Collections.emptyList();

    @Test public void continueWatchingWinsWhenItHasCards() {
        ShelfPlan plan = ShelfPlan.choose(true, true, WATCH, APPS);
        assertEquals(ShelfPlan.Source.WATCH_NEXT, plan.source);
        assertEquals(WATCH, plan.items);
    }

    @Test public void emptyWatchNextFallsBackToRecentApps() {
        assertEquals(ShelfPlan.Source.RECENT_APPS, ShelfPlan.choose(true, false, NONE, APPS).source);
        assertEquals(ShelfPlan.Source.RECENT_APPS, ShelfPlan.choose(false, true, WATCH, APPS).source);
    }

    @Test public void bothEmptyHidesTheRow() {
        ShelfPlan plan = ShelfPlan.choose(true, true, NONE, NONE);
        assertEquals(ShelfPlan.Source.NONE, plan.source);
        assertTrue(plan.items.isEmpty());
    }

    @Test public void recentAppsAreOnlyReadWhenTheyCanBeShown() {
        assertTrue(ShelfPlan.needsRecent(false, true, 5));
        assertTrue(ShelfPlan.needsRecent(true, false, 0));
        assertFalse(ShelfPlan.needsRecent(true, false, 3));
        assertFalse(ShelfPlan.needsRecent(false, false, 0));
    }

    @Test public void logLineNamesSourceCountAndFirstTitle() {
        assertEquals("source=WATCH_NEXT rows=1 first=Show", ShelfPlan.choose(true, false, WATCH, NONE).logLine());
        assertEquals("source=NONE rows=0 first=", ShelfPlan.choose(true, true, NONE, NONE).logLine());
    }
}
