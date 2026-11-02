package com.gathin.tvhome;

import org.junit.Test;

import java.time.LocalTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PresetsMergeTest {
    private static final LocalTime NOON = LocalTime.of(12, 0);
    private static final LocalTime NIGHT = LocalTime.of(23, 0);

    /** Each preset sets "from" to its own name, so the merge order shows in the result. */
    private static final PresetsMerge.Source SOURCE = name -> {
        Map<String, Object> m = new HashMap<>();
        m.put("from", name);
        m.put(name, true);
        if (name.equals("dark")) m.put("wallpaper", false);
        return m;
    };

    private static Map<String, Object> overrides(Map<String, Object> theme, Map<String, String> look, LocalTime now) throws Exception {
        return PresetsMerge.overrides(theme, look, SOURCE, now);
    }

    private static Map<String, String> look(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    @Test public void defaultsAddNothing() throws Exception {
        assertNull(overrides(Collections.emptyMap(), Collections.emptyMap(), NIGHT));
    }

    @Test public void picksThatEqualTheDefaultsAddNothing() throws Exception {
        Map<String, String> picks = look("layout", "classic", "theme_preset", "calm", "dark_mode", "off", "font", "outfit", "icon_pack", "");
        assertNull(overrides(Collections.emptyMap(), picks, NIGHT));
    }

    @Test public void aNullOverlayStaysNullAndIsNeverAnEmptyObject() {
        Object input = null;
        assertNull(PresetsMerge.orUnchanged(input, () -> null));
    }

    @Test public void anyExceptionReturnsTheInputUnchanged() {
        Object input = new Object();
        assertSame(input, PresetsMerge.orUnchanged(input, () -> { throw new IllegalStateException("bad asset"); }));
        assertSame(input, PresetsMerge.orUnchanged(input, () -> { throw new java.io.IOException("missing"); }));
        assertNull(PresetsMerge.orUnchanged(null, () -> { throw new RuntimeException("x"); }));
    }

    @Test public void aMergeResultReplacesTheInput() {
        Object input = new Object();
        Object merged = new Object();
        assertSame(merged, PresetsMerge.orUnchanged(input, () -> merged));
    }

    @Test public void aFailingSourceMakesOverridesThrowSoTheCallerKeepsTheOverlay() {
        PresetsMerge.Source broken = name -> { throw new java.io.FileNotFoundException(name); };
        try {
            PresetsMerge.overrides(Collections.emptyMap(), look("layout", "dock"), broken, NOON);
            org.junit.Assert.fail("expected the source failure to propagate");
        } catch (Exception expected) {
            assertTrue(expected instanceof java.io.FileNotFoundException);
        }
    }

    @Test public void presetsMergeInOrderLayoutThenThemeThenDark() throws Exception {
        Map<String, Object> out = overrides(Collections.emptyMap(),
                look("layout", "dock", "theme_preset", "glass", "dark_mode", "on"), NOON);
        assertEquals("dark", out.get("from"));
        assertEquals(Boolean.TRUE, out.get("layout-dock"));
        assertEquals(Boolean.TRUE, out.get("theme-glass"));
        assertEquals(Boolean.FALSE, out.get("wallpaper"));
        assertEquals(Boolean.TRUE, out.get(PresetsMerge.DARK_ACTIVE_KEY));
    }

    @Test public void themeJsonSelectionsApplyWithoutLookPicks() throws Exception {
        Map<String, Object> theme = new HashMap<>();
        theme.put("layout", "cards");
        theme.put("theme_preset", "ember");
        Map<String, Object> out = overrides(theme, Collections.emptyMap(), NOON);
        assertEquals(Boolean.TRUE, out.get("layout-cards"));
        assertEquals(Boolean.TRUE, out.get("theme-ember"));
        assertFalse(out.containsKey("layout"));
    }

    @Test public void aLookPickWinsOverTheTheme() throws Exception {
        Map<String, Object> theme = new HashMap<>();
        theme.put("layout", "cards");
        Map<String, Object> out = overrides(theme, look("layout", "dock"), NOON);
        assertEquals(Boolean.TRUE, out.get("layout-dock"));
        assertFalse(out.containsKey("layout-cards"));
        assertEquals("dock", out.get("layout"));
    }

    @Test public void pickingClassicOverAThemeLayoutDropsThePreset() throws Exception {
        Map<String, Object> theme = new HashMap<>();
        theme.put("layout", "dock");
        Map<String, Object> out = overrides(theme, look("layout", "classic"), NOON);
        assertEquals("classic", out.get("layout"));
        assertFalse(out.containsKey("layout-dock"));
    }

    @Test public void unknownPicksFallBackToTheDefaults() throws Exception {
        assertNull(overrides(Collections.emptyMap(), look("layout", "ribbon", "theme_preset", "neon", "dark_mode", "maybe"), NIGHT));
    }

    @Test public void darkAutoFollowsTheClockAndTheThemesHours() throws Exception {
        Map<String, String> auto = look("dark_mode", "auto");
        assertTrue(overrides(Collections.emptyMap(), auto, NIGHT).containsKey(PresetsMerge.DARK_ACTIVE_KEY));
        assertFalse(overrides(Collections.emptyMap(), auto, NOON).containsKey(PresetsMerge.DARK_ACTIVE_KEY));
        Map<String, Object> theme = new HashMap<>();
        theme.put("dark_from", 9);
        theme.put("dark_to", 17);
        assertTrue(overrides(theme, auto, NOON).containsKey(PresetsMerge.DARK_ACTIVE_KEY));
        assertFalse(overrides(theme, auto, NIGHT).containsKey(PresetsMerge.DARK_ACTIVE_KEY));
    }

    @Test public void anIconPackPickIsRecordedAndDefaultOverridesTheThemesPack() throws Exception {
        Map<String, Object> out = overrides(Collections.emptyMap(), look("icon_pack", "com.example.pack"), NOON);
        assertEquals("com.example.pack", out.get("icon_pack"));
        Map<String, Object> theme = new HashMap<>();
        theme.put("icon_pack", "com.theme.pack");
        assertEquals("", overrides(theme, look("icon_pack", ""), NOON).get("icon_pack"));
        assertNull(overrides(theme, look("icon_pack", "com.theme.pack"), NOON));
    }

    @Test public void lookPicksLiveInTheirOwnPrefsFile() {
        assertNotEquals(Config.SETTINGS_NAME, Presets.LOOK_PREFS);
        assertEquals("look", Presets.LOOK_PREFS);
    }
}
