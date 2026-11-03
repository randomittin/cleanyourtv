package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Which shelf cards still lack art and must be loaded again. A failed load (no network at boot, a timeout) is
 * never remembered as a failure: the card simply is not in the loaded set, so the next resume refresh or the
 * next "network available" event retries it. Pure Java.
 */
final class ArtRetry {
    private ArtRetry() {}

    /** The key of a card's main image: the poster URI, or the package for an app banner. */
    static String artKey(ShelfItem item) {
        return item.kind == ShelfItem.Kind.APP ? "app:" + item.packageName : item.posterUri;
    }

    /** The key of a Watch Next card's app badge, or null when the card has none. */
    static String badgeKey(ShelfItem item) {
        return item.kind == ShelfItem.Kind.WATCH_NEXT && item.packageName != null ? "badge:" + item.packageName : null;
    }

    /** Adapter positions whose main image or badge is wanted but not in {@code loaded}. */
    static List<Integer> missing(List<ShelfItem> items, Set<String> loaded, Predicate<String> loadable) {
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ShelfItem item = items.get(i);
            String art = artKey(item);
            String badge = badgeKey(item);
            boolean artMissing = art != null && (item.kind == ShelfItem.Kind.APP || loadable.test(art)) && !loaded.contains(art);
            boolean badgeMissing = badge != null && !loaded.contains(badge);
            if (artMissing || badgeMissing) positions.add(i);
        }
        return positions;
    }
}
