package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecentSelectTest {
    private static RecentSelect.Stamp s(String pkg, long t) {
        return new RecentSelect.Stamp(pkg, t);
    }

    @Test public void newestFirstOnePerPackage() {
        List<String> out = RecentSelect.select(
                Arrays.asList(s("a", 1), s("b", 5), s("a", 9), s("c", 3)), Collections.<String>emptySet(), p -> true, 10);
        assertEquals(Arrays.asList("a", "b", "c"), out);
    }

    @Test public void excludesLauncherHomeAppsAndSystemPackages() {
        List<RecentSelect.Stamp> stamps = Arrays.asList(
                s("com.gathin.tvhome", 9), s("com.vendor.home", 8), s("com.android.systemui", 7),
                s("android", 6), s("com.android.settings", 5), s("com.google.android.tvlauncher", 4), s("com.netflix.ninja", 3));
        List<String> out = RecentSelect.select(stamps,
                new HashSet<>(Arrays.asList("com.gathin.tvhome", "com.vendor.home")), p -> true, 10);
        assertEquals(Collections.singletonList("com.netflix.ninja"), out);
    }

    @Test public void dropsPackagesWithoutALauncherActivity() {
        List<String> out = RecentSelect.select(Arrays.asList(s("svc", 9), s("app", 8)), Collections.<String>emptySet(),
                p -> p.equals("app"), 10);
        assertEquals(Collections.singletonList("app"), out);
    }

    @Test public void limitCountsKeptPackagesNotInput() {
        List<String> out = RecentSelect.select(Arrays.asList(s("android", 9), s("a", 8), s("b", 7), s("c", 6)),
                Collections.<String>emptySet(), p -> true, 2);
        assertEquals(Arrays.asList("a", "b"), out);
    }

    @Test public void systemPackageCheck() {
        assertTrue(RecentSelect.isSystemPackage("com.android.launcher3"));
        assertTrue(RecentSelect.isSystemPackage(null));
        assertFalse(RecentSelect.isSystemPackage("com.google.android.youtube.tv"));
    }
}
