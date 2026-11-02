package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WatchNextFilterTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final long HOUR = 3_600_000L;

    private static WatchNextFilter.Row row(String title, String intent, int type, long engaged, long pos, long dur) {
        return new WatchNextFilter.Row(title, null, "https://img/" + title, null, intent, "com.app", type, engaged, pos, dur);
    }

    private static WatchNextFilter.Row continuing(String title, long engagedAgo) {
        return row(title, "intent:#Intent;package=com.app;S.t=" + title + ";end", 0, NOW - engagedAgo, 1000, 10000);
    }

    private static List<String> titles(List<ShelfItem> items) {
        List<String> out = new ArrayList<>();
        for (ShelfItem i : items) out.add(i.title);
        return out;
    }

    @Test public void progressIsPercentOrMinusOne() {
        assertEquals(10, WatchNextFilter.progressPercent(1000, 10000));
        assertEquals(99, WatchNextFilter.progressPercent(9999, 10000));
        assertEquals(100, WatchNextFilter.progressPercent(12000, 10000));
        assertEquals(-1, WatchNextFilter.progressPercent(0, 10000));
        assertEquals(-1, WatchNextFilter.progressPercent(500, 0));
        assertEquals(-1, WatchNextFilter.progressPercent(-5, 100));
    }

    @Test public void rowWithoutIntentUriIsDropped() {
        List<WatchNextFilter.Row> rows = Arrays.asList(
                row("none", null, 0, NOW, 1, 10), row("blank", "  ", 0, NOW, 1, 10), continuing("ok", HOUR));
        assertEquals(Arrays.asList("ok"), titles(WatchNextFilter.filter(rows, NOW)));
    }

    @Test public void finishedContinueRowIsDroppedAtNinetyFivePercent() {
        List<WatchNextFilter.Row> rows = Arrays.asList(
                row("p96", "intent:a", 0, NOW, 9600, 10000),
                row("p95", "intent:b", 0, NOW, 9500, 10000),
                row("p94", "intent:c", 0, NOW, 9400, 10000));
        assertEquals(Arrays.asList("p94"), titles(WatchNextFilter.filter(rows, NOW)));
    }

    @Test public void nextEpisodeRowIsKeptEvenWhenItsPositionIsFull() {
        List<WatchNextFilter.Row> rows = Collections.singletonList(row("next", "intent:a", 1, NOW, 10000, 10000));
        assertEquals(1, WatchNextFilter.filter(rows, NOW).size());
    }

    @Test public void rowEngagedOverHalfAYearAgoIsDropped() {
        List<WatchNextFilter.Row> rows = Arrays.asList(
                row("old", "intent:a", 0, NOW - 200 * DAY, 1, 10), row("edge", "intent:b", 0, NOW - 179 * DAY, 1, 10));
        assertEquals(Arrays.asList("edge"), titles(WatchNextFilter.filter(rows, NOW)));
    }

    @Test public void promotionRowOlderThanThirtyDaysIsDropped() {
        List<WatchNextFilter.Row> rows = Arrays.asList(
                row("stale", "intent:a", 2, NOW - 31 * DAY, 0, 0), row("fresh", "intent:b", 2, NOW - 2 * DAY, 0, 0));
        assertEquals(Arrays.asList("fresh"), titles(WatchNextFilter.filter(rows, NOW)));
    }

    @Test public void newestEngagementComesFirstAndUndatedRowsKeepProviderOrderLast() {
        List<WatchNextFilter.Row> rows = Arrays.asList(
                row("undated1", "intent:u1", 0, 0, 1, 10),
                continuing("older", 5 * DAY),
                row("undated2", "intent:u2", 0, 0, 1, 10),
                continuing("newest", HOUR));
        assertEquals(Arrays.asList("newest", "older", "undated1", "undated2"), titles(WatchNextFilter.filter(rows, NOW)));
    }

    @Test public void listIsCappedAtTwelve() {
        List<WatchNextFilter.Row> rows = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            rows.add(row("t" + i, "intent:" + i, 0, NOW - i * HOUR, 1, 10));
        }
        List<ShelfItem> items = WatchNextFilter.filter(rows, NOW);
        assertEquals(12, items.size());
        assertEquals("t0", items.get(0).title);
        assertEquals("t11", items.get(11).title);
    }

    @Test public void emptyTitleFallsBackToEpisodeTitleAndNeitherDropsTheRow() {
        WatchNextFilter.Row fallback = new WatchNextFilter.Row("", " Pilot ", null, null, "intent:a", "p", 0, NOW, 1, 10);
        WatchNextFilter.Row nameless = new WatchNextFilter.Row(null, null, null, null, "intent:b", "p", 0, NOW, 1, 10);
        List<ShelfItem> items = WatchNextFilter.filter(Arrays.asList(fallback, nameless), NOW);
        assertEquals(Arrays.asList("Pilot"), titles(items));
    }

    @Test public void duplicateDeepLinksKeepTheNewest() {
        List<WatchNextFilter.Row> rows = Arrays.asList(
                row("old", "intent:same", 0, NOW - DAY, 1, 10), row("new", "intent:same", 0, NOW - HOUR, 1, 10));
        assertEquals(Arrays.asList("new"), titles(WatchNextFilter.filter(rows, NOW)));
    }

    @Test public void posterFallsBackToThumbnailAndPackageToTheIntentUri() {
        WatchNextFilter.Row r = new WatchNextFilter.Row("T", null, "", "https://thumb", "intent:#Intent;package=com.x;end", null,
                0, NOW, 5000, 10000);
        ShelfItem item = WatchNextFilter.filter(Collections.singletonList(r), NOW).get(0);
        assertEquals("https://thumb", item.posterUri);
        assertEquals("com.x", item.packageName);
        assertEquals(50, item.progress);
    }

    @Test public void noPosterAtAllIsNull() {
        WatchNextFilter.Row r = new WatchNextFilter.Row("T", null, null, null, "intent:a", "p", 0, NOW, 0, 0);
        assertNull(WatchNextFilter.filter(Collections.singletonList(r), NOW).get(0).posterUri);
        assertTrue(WatchNextFilter.filter(Collections.<WatchNextFilter.Row>emptyList(), NOW).isEmpty());
    }
}
