package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/** The Look picks, in the {@value Presets#LOOK_PREFS} prefs file. The only writer of that file. */
final class LookPrefs {
    static final String LAYOUT = "layout";
    static final String THEME_PRESET = "theme_preset";
    static final String DARK_MODE = "dark_mode";
    static final String FONT = "font";
    static final String ICON_PACK = "icon_pack";

    private LookPrefs() {}

    /** Whether {@code value} is a pick {@code key} accepts. An icon pack is any package-name-shaped string, or "" for the default. */
    static boolean valid(String key, String value) {
        if (key == null || value == null) return false;
        switch (key) {
            case LAYOUT: return PresetsMerge.LAYOUTS.contains(value);
            case THEME_PRESET: return PresetsMerge.THEMES.contains(value);
            case DARK_MODE: return PresetsMerge.DARK_MODES.contains(value);
            case FONT: return PresetsMerge.FONTS.contains(value);
            case ICON_PACK: return value.isEmpty() || value.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+");
            default: return false;
        }
    }

    /** Stores a valid pick; false (nothing written) when key or value is unknown. */
    static boolean put(Context ctx, String key, String value) {
        if (!valid(key, value)) return false;
        prefs(ctx).edit().putString(key, value).apply();
        return true;
    }

    /** Drops every pick, so the theme's own values apply again. */
    static void clear(Context ctx) {
        prefs(ctx).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Presets.LOOK_PREFS, Context.MODE_PRIVATE);
    }
}
