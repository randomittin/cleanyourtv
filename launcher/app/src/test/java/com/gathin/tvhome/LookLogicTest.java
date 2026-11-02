package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LookLogicTest {
    private static final List<String> LAYOUTS = Arrays.asList("classic", "dock", "cards", "sidebar");

    @Test public void cycleWrapsBothWays() {
        assertEquals("dock", LookLogic.cycle(LAYOUTS, "classic", 1));
        assertEquals("classic", LookLogic.cycle(LAYOUTS, "sidebar", 1));
        assertEquals("sidebar", LookLogic.cycle(LAYOUTS, "classic", -1));
        assertEquals("cards", LookLogic.cycle(LAYOUTS, "sidebar", -1));
    }

    @Test public void cycleFromUnknownLandsOnAnEnd() {
        assertEquals("classic", LookLogic.cycle(LAYOUTS, "bogus", 1));
        assertEquals("sidebar", LookLogic.cycle(LAYOUTS, null, -1));
        assertNull(LookLogic.cycle(Collections.<String>emptyList(), "x", 1));
        assertEquals("only", LookLogic.cycle(Collections.singletonList("only"), "only", 1));
    }

    @Test public void darkFlipIsAnyChangeOfEffectiveState() {
        assertTrue(LookLogic.darkFlipped(false, true));
        assertTrue(LookLogic.darkFlipped(true, false));
        assertFalse(LookLogic.darkFlipped(true, true));
        assertFalse(LookLogic.darkFlipped(false, false));
    }

    @Test public void autoWindowWrapsPastMidnight() {
        // 19:00 -> 07:00 crosses midnight; the state a rebuild compares is ForkKeys.darkNow's.
        assertTrue(ForkKeys.darkNow("auto", 19, 7, LocalTime.of(23, 59)));
        assertTrue(ForkKeys.darkNow("auto", 19, 7, LocalTime.of(0, 30)));
        assertFalse(ForkKeys.darkNow("auto", 19, 7, LocalTime.of(12, 0)));
        assertTrue(LookLogic.darkFlipped(
                ForkKeys.darkNow("auto", 19, 7, LocalTime.of(6, 59)),
                ForkKeys.darkNow("auto", 19, 7, LocalTime.of(7, 0))));
        assertEquals("19:00–07:00", LookLogic.autoWindow(19, 7));
    }

    @Test public void packsListDefaultFirstThenByLabelWithoutDuplicates() {
        List<LookLogic.Pack> in = new ArrayList<>();
        in.add(new LookLogic.Pack("com.z.pack", "zeta"));
        in.add(new LookLogic.Pack("com.b.pack", "Beta"));
        in.add(new LookLogic.Pack("com.z.pack", "zeta"));
        in.add(new LookLogic.Pack("com.a.pack", "alpha"));
        in.add(new LookLogic.Pack("", "blank"));
        in.add(new LookLogic.Pack("com.n.pack", " "));
        List<LookLogic.Pack> out = LookLogic.packOrder(in, "Default");
        List<String> pkgs = new ArrayList<>();
        for (LookLogic.Pack p : out) pkgs.add(p.packageName);
        assertEquals(Arrays.asList("", "com.a.pack", "com.b.pack", "com.n.pack", "com.z.pack"), pkgs);
        assertEquals("Default", out.get(0).label);
        assertEquals("com.n.pack", out.get(3).label);
    }

    @Test public void emptyInstallListGivesOnlyDefault() {
        List<LookLogic.Pack> out = LookLogic.packOrder(new ArrayList<LookLogic.Pack>(), "Default");
        assertEquals(1, out.size());
        assertEquals("", out.get(0).packageName);
    }
}
