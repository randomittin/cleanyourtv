package com.gathin.tvhome;

import java.util.Collections;
import java.util.List;

/**
 * Which row the shelf shows. Continue watching wins when it is on and has cards; otherwise Recent apps fills
 * in (when that key is on, or as the fallback for an empty Continue watching); with neither, the shelf hides.
 */
final class ShelfPlan {
    enum Source { WATCH_NEXT, RECENT_APPS, NONE }

    final Source source;
    final List<ShelfItem> items;

    private ShelfPlan(Source source, List<ShelfItem> items) {
        this.source = source;
        this.items = items;
    }

    static ShelfPlan choose(boolean continueWatching, boolean recentApps, List<ShelfItem> watchNext, List<ShelfItem> recent) {
        if (continueWatching && !watchNext.isEmpty()) return new ShelfPlan(Source.WATCH_NEXT, watchNext);
        if ((recentApps || continueWatching) && !recent.isEmpty()) return new ShelfPlan(Source.RECENT_APPS, recent);
        return new ShelfPlan(Source.NONE, Collections.<ShelfItem>emptyList());
    }

    /** Whether the Recent apps list has to be read at all, so the usage query is skipped when Watch Next fills the row. */
    static boolean needsRecent(boolean continueWatching, boolean recentApps, int watchNextCount) {
        return recentApps || continueWatching && watchNextCount == 0;
    }

    /** The one line the on-TV check reads: {@code source=WATCH_NEXT rows=3 first=Title}. */
    String logLine() {
        return "source=" + source + " rows=" + items.size() + " first=" + (items.isEmpty() ? "" : items.get(0).title);
    }
}
