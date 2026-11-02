package com.gathin.tvhome;

import android.content.Context;

import java.time.LocalTime;
import java.util.Map;

/**
 * Fork-only keys, read from the SAME merged overlay Config sees (theme config.json + presets + Look picks), so
 * they are not added to Config's constructor.
 */
final class ForkKeys {
    final boolean lookPill, nowPlaying, continueWatching, recentApps, sidebar;
    /** "dock" | "cards" | "classic" | "sidebar". */
    final String layout;
    /** "calm" | "glass" | "midnight" | "ember" | "mono". */
    final String themePreset;
    /** "off" | "on" | "auto". */
    final String darkMode;
    final int darkFromHour, darkToHour;
    /** One of {@link FontChoice#IDS}. */
    final String font;
    /** "" = the launcher's default tiles. */
    final String iconPack;

    private ForkKeys(Map<String, Object> merged) {
        lookPill = PresetsMerge.bool(merged, "look_pill");
        nowPlaying = PresetsMerge.bool(merged, "now_playing");
        continueWatching = PresetsMerge.bool(merged, "continue_watching");
        recentApps = PresetsMerge.bool(merged, "recent_apps");
        sidebar = PresetsMerge.bool(merged, "sidebar");
        layout = PresetsMerge.pick(null, PresetsMerge.string(merged, "layout"), PresetsMerge.LAYOUTS, PresetsMerge.DEFAULT_LAYOUT);
        themePreset = PresetsMerge.pick(null, PresetsMerge.string(merged, "theme_preset"), PresetsMerge.THEMES, PresetsMerge.DEFAULT_THEME);
        darkMode = PresetsMerge.pick(null, PresetsMerge.string(merged, "dark_mode"), PresetsMerge.DARK_MODES, PresetsMerge.DEFAULT_DARK);
        darkFromHour = PresetsMerge.hour(merged, "dark_from", PresetsMerge.DEFAULT_DARK_FROM);
        darkToHour = PresetsMerge.hour(merged, "dark_to", PresetsMerge.DEFAULT_DARK_TO);
        font = PresetsMerge.pick(null, PresetsMerge.string(merged, "font"), PresetsMerge.FONTS, PresetsMerge.DEFAULT_FONT);
        String pack = PresetsMerge.string(merged, "icon_pack");
        iconPack = pack == null ? "" : pack.trim();
    }

    /** The keys the current theme, presets and Look picks resolve to. */
    static ForkKeys load(Context ctx) {
        return from(Presets.mergedFlat(ctx));
    }

    /** Pure counterpart of {@link #load}: flat primitives of the merged overlay (empty map = no overlay). */
    static ForkKeys from(Map<String, Object> merged) {
        return new ForkKeys(merged);
    }

    /**
     * Whether dark is in force at {@code now}. "on" always, "off" never; "auto" from {@code fromHour} (inclusive)
     * up to {@code toHour} (exclusive), wrapping midnight when from is later than to. from == to is never.
     */
    static boolean darkNow(String mode, int fromHour, int toHour, LocalTime now) {
        if ("on".equals(mode)) return true;
        if (!"auto".equals(mode) || fromHour == toHour) return false;
        int minute = now.getHour() * 60 + now.getMinute();
        int from = fromHour * 60;
        int to = toHour * 60;
        return fromHour < toHour ? minute >= from && minute < to : minute >= from || minute < to;
    }
}
