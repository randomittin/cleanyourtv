package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ArtRetryTest {
    private static final ShelfItem A = ShelfItem.watchNext("A", "https://i/a", "intent:a", "com.a", 10);
    private static final ShelfItem B = ShelfItem.watchNext("B", "https://i/b", "intent:b", "com.b", -1);
    private static final ShelfItem APP = ShelfItem.app("com.c", "C");
    private static final List<ShelfItem> ITEMS = Arrays.asList(A, B, APP);

    private static Set<String> set(String... keys) {
        return new HashSet<>(Arrays.asList(keys));
    }

    @Test public void nothingLoadedMeansEveryCardIsRetried() {
        assertEquals(Arrays.asList(0, 1, 2), ArtRetry.missing(ITEMS, set(), k -> true));
    }

    @Test public void failedLoadsAreNeverRemembered() {
        // First pass: only A's art and badge arrived (the rest failed on UnknownHostException).
        Set<String> loaded = set("https://i/a", "badge:com.a");
        assertEquals(Arrays.asList(1, 2), ArtRetry.missing(ITEMS, loaded, k -> true));
        // Next resume or network event: the same call still lists the failed cards, until they load.
        assertEquals(Arrays.asList(1, 2), ArtRetry.missing(ITEMS, loaded, k -> true));
        loaded.addAll(set("https://i/b", "badge:com.b", "app:com.c"));
        assertTrue(ArtRetry.missing(ITEMS, loaded, k -> true).isEmpty());
    }

    @Test public void aMissingBadgeAloneTriggersARetry() {
        Set<String> loaded = set("https://i/a", "https://i/b", "badge:com.b", "app:com.c");
        assertEquals(Collections.singletonList(0), ArtRetry.missing(ITEMS, loaded, k -> true));
    }

    @Test public void unloadablePostersAreNotRetriedForever() {
        ShelfItem http = ShelfItem.watchNext("H", "http://insecure/h", "intent:h", null, -1);
        assertTrue(ArtRetry.missing(Collections.singletonList(http), set(), k -> k.startsWith("https")).isEmpty());
        ShelfItem none = ShelfItem.watchNext("N", null, "intent:n", null, -1);
        assertTrue(ArtRetry.missing(Collections.singletonList(none), set(), k -> true).isEmpty());
    }
}
