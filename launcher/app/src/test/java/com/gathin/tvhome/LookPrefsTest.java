package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LookPrefsTest {
    @Test public void acceptsKnownValuesOnly() {
        assertTrue(LookPrefs.valid("layout", "dock"));
        assertTrue(LookPrefs.valid("theme_preset", "midnight"));
        assertTrue(LookPrefs.valid("dark_mode", "auto"));
        assertTrue(LookPrefs.valid("font", "jakarta"));
        assertFalse(LookPrefs.valid("layout", "grid"));
        assertFalse(LookPrefs.valid("font", "comic"));
        assertFalse(LookPrefs.valid("unknown", "x"));
        assertFalse(LookPrefs.valid(null, "x"));
        assertFalse(LookPrefs.valid("layout", null));
    }

    @Test public void iconPackIsAPackageNameOrEmptyForDefault() {
        assertTrue(LookPrefs.valid("icon_pack", ""));
        assertTrue(LookPrefs.valid("icon_pack", "com.randomittin.iconpack.tvclean"));
        assertFalse(LookPrefs.valid("icon_pack", "nodots"));
        assertFalse(LookPrefs.valid("icon_pack", "../evil"));
    }
}
