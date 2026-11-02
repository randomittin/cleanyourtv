package com.gathin.tvhome;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fork presets, pure part (no Android types, so it runs in plain JVM tests). Works on flat maps of primitives:
 * {@link Presets} converts the theme's JSONObject to such a map and the result back at the edge.
 */
final class PresetsMerge {
    static final List<String> LAYOUTS = Arrays.asList("dock", "cards", "classic", "sidebar");
    static final List<String> THEMES = Arrays.asList("calm", "glass", "midnight", "ember", "mono");
    static final List<String> DARK_MODES = Arrays.asList("off", "on", "auto");
    static final List<String> FONTS = FontChoice.IDS;

    static final String DEFAULT_LAYOUT = "classic";
    static final String DEFAULT_THEME = "calm";
    static final String DEFAULT_DARK = "off";
    static final String DEFAULT_FONT = "outfit";
    static final int DEFAULT_DARK_FROM = 19;
    static final int DEFAULT_DARK_TO = 7;

    /** Marker key put into the merged JSON while dark is in force (Config clears the wallpaper on it). */
    static final String DARK_ACTIVE_KEY = "dark_active";

    private PresetsMerge() {}

    /** Where preset key/value maps come from (assets in the app, a fake in tests); null for an unknown name. */
    interface Source {
        Map<String, Object> preset(String name) throws Exception;
    }

    /** A merge that may fail; see {@link #orUnchanged}. */
    interface Merge<T> {
        T get() throws Exception;
    }

    /**
     * The merge result, or {@code input} itself on ANY exception or when the merge has nothing to add
     * (null). A null overlay therefore stays null and is never turned into an empty object: Config tells
     * "no overlay" from "empty overlay" by {@code json == null}.
     */
    static <T> T orUnchanged(T input, Merge<T> merge) {
        try {
            T merged = merge.get();
            return merged != null ? merged : input;
        } catch (Exception e) {
            return input;
        }
    }

    /**
     * Keys to write over the theme's overlay, in merge order (layout preset, theme preset, dark preset, then the
     * Look panel's own picks), or null when the defaults are in force so the overlay must stay untouched.
     *
     * @param theme flat primitives of the theme's config.json (empty when there is none)
     * @param look  the picks saved by the Look panel (keys layout, theme_preset, dark_mode, font, icon_pack)
     */
    static Map<String, Object> overrides(Map<String, Object> theme, Map<String, String> look, Source src, LocalTime now)
            throws Exception {
        String themeLayout = pick(null, string(theme, "layout"), LAYOUTS, DEFAULT_LAYOUT);
        String themePreset = pick(null, string(theme, "theme_preset"), THEMES, DEFAULT_THEME);
        String themeDark = pick(null, string(theme, "dark_mode"), DARK_MODES, DEFAULT_DARK);
        String themeFont = pick(null, string(theme, "font"), FONTS, DEFAULT_FONT);
        String layout = pick(look.get("layout"), themeLayout, LAYOUTS, DEFAULT_LAYOUT);
        String preset = pick(look.get("theme_preset"), themePreset, THEMES, DEFAULT_THEME);
        String dark = pick(look.get("dark_mode"), themeDark, DARK_MODES, DEFAULT_DARK);
        String font = pick(look.get("font"), themeFont, FONTS, DEFAULT_FONT);

        Map<String, Object> out = new LinkedHashMap<>();
        if (!layout.equals(DEFAULT_LAYOUT)) putPreset(out, src, "layout-" + layout);
        if (!preset.equals(DEFAULT_THEME)) putPreset(out, src, "theme-" + preset);
        int from = hour(theme, "dark_from", DEFAULT_DARK_FROM);
        int to = hour(theme, "dark_to", DEFAULT_DARK_TO);
        if (ForkKeys.darkNow(dark, from, to, now)) {
            putPreset(out, src, "dark");
            out.put(DARK_ACTIVE_KEY, Boolean.TRUE);
        }
        // Picks that differ from what the theme alone resolves to; recorded so ForkKeys sees the same values.
        if (!layout.equals(themeLayout)) out.put("layout", layout);
        if (!preset.equals(themePreset)) out.put("theme_preset", preset);
        if (!dark.equals(themeDark)) out.put("dark_mode", dark);
        if (!font.equals(themeFont)) out.put("font", font);
        String themePack = string(theme, "icon_pack");
        String lookPack = look.get("icon_pack");
        if (lookPack != null && !lookPack.trim().equals(themePack == null ? "" : themePack.trim())) {
            out.put("icon_pack", lookPack.trim());
        }
        return out.isEmpty() ? null : out;
    }

    private static void putPreset(Map<String, Object> out, Source src, String name) throws Exception {
        Map<String, Object> preset = src.preset(name);
        if (preset != null) out.putAll(preset);
    }

    /** The first of {@code chosen}, {@code fallback} that is in {@code allowed}, else {@code def}. */
    static String pick(String chosen, String fallback, List<String> allowed, String def) {
        if (chosen != null && allowed.contains(chosen)) return chosen;
        if (fallback != null && allowed.contains(fallback)) return fallback;
        return def;
    }

    static String string(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof String ? (String) v : null;
    }

    static boolean bool(Map<String, Object> m, String key) {
        return Boolean.TRUE.equals(m.get(key));
    }

    /** An hour 0..23 from a number, else {@code def}. */
    static int hour(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        if (!(v instanceof Number)) return def;
        int h = ((Number) v).intValue();
        return h >= 0 && h <= 23 ? h : def;
    }
}
