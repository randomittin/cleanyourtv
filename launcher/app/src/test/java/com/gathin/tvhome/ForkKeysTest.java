package com.gathin.tvhome;

import org.junit.Test;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ForkKeysTest {
    @Test public void autoRunsFromEveningToMorningAndEndsExclusive() {
        assertTrue(ForkKeys.darkNow("auto", 19, 7, LocalTime.of(23, 0)));
        assertTrue(ForkKeys.darkNow("auto", 19, 7, LocalTime.of(19, 0)));
        assertTrue(ForkKeys.darkNow("auto", 19, 7, LocalTime.of(0, 0)));
        assertFalse(ForkKeys.darkNow("auto", 19, 7, LocalTime.of(7, 0)));
        assertFalse(ForkKeys.darkNow("auto", 19, 7, LocalTime.of(18, 59)));
        assertTrue(ForkKeys.darkNow("auto", 19, 7, LocalTime.of(6, 59)));
    }

    @Test public void onAndOffIgnoreTheClock() {
        assertTrue(ForkKeys.darkNow("on", 19, 7, LocalTime.of(12, 0)));
        assertFalse(ForkKeys.darkNow("off", 19, 7, LocalTime.of(23, 0)));
    }

    @Test public void aDaytimeRangeIsHandled() {
        assertTrue(ForkKeys.darkNow("auto", 9, 17, LocalTime.of(9, 0)));
        assertTrue(ForkKeys.darkNow("auto", 9, 17, LocalTime.of(16, 59)));
        assertFalse(ForkKeys.darkNow("auto", 9, 17, LocalTime.of(17, 0)));
        assertFalse(ForkKeys.darkNow("auto", 9, 17, LocalTime.of(8, 59)));
    }

    @Test public void equalHoursAndUnknownModesAreNeverDark() {
        assertFalse(ForkKeys.darkNow("auto", 7, 7, LocalTime.of(7, 0)));
        assertFalse(ForkKeys.darkNow("sometimes", 19, 7, LocalTime.of(23, 0)));
        assertFalse(ForkKeys.darkNow(null, 19, 7, LocalTime.of(23, 0)));
    }

    @Test public void noOverlayGivesTheDefaults() {
        ForkKeys keys = ForkKeys.from(new HashMap<>());
        assertEquals("classic", keys.layout);
        assertEquals("calm", keys.themePreset);
        assertEquals("off", keys.darkMode);
        assertEquals("outfit", keys.font);
        assertEquals("", keys.iconPack);
        assertEquals(19, keys.darkFromHour);
        assertEquals(7, keys.darkToHour);
        assertFalse(keys.lookPill || keys.nowPlaying || keys.continueWatching || keys.recentApps || keys.sidebar);
    }

    @Test public void unknownValuesFallBackToTheDefaults() {
        Map<String, Object> m = new HashMap<>();
        m.put("layout", "ribbon");
        m.put("theme_preset", "neon");
        m.put("dark_mode", "maybe");
        m.put("font", "comic");
        m.put("dark_from", 31);
        ForkKeys keys = ForkKeys.from(m);
        assertEquals("classic", keys.layout);
        assertEquals("calm", keys.themePreset);
        assertEquals("off", keys.darkMode);
        assertEquals("outfit", keys.font);
        assertEquals(19, keys.darkFromHour);
    }

    @Test public void knownValuesAndFlagsAreRead() {
        Map<String, Object> m = new HashMap<>();
        m.put("layout", "sidebar");
        m.put("theme_preset", "ember");
        m.put("dark_mode", "auto");
        m.put("dark_from", 21);
        m.put("dark_to", 6);
        m.put("font", "manrope");
        m.put("icon_pack", " com.example.pack ");
        m.put("look_pill", true);
        m.put("sidebar", true);
        ForkKeys keys = ForkKeys.from(m);
        assertEquals("sidebar", keys.layout);
        assertEquals("ember", keys.themePreset);
        assertEquals("auto", keys.darkMode);
        assertEquals(21, keys.darkFromHour);
        assertEquals(6, keys.darkToHour);
        assertEquals("manrope", keys.font);
        assertEquals("com.example.pack", keys.iconPack);
        assertTrue(keys.lookPill && keys.sidebar);
        assertFalse(keys.nowPlaying);
    }
}
