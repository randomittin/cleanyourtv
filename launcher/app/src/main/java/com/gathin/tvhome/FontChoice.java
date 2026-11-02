package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Build;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The UI font the user picked (pref {@value #PREF_KEY} in the {@value Presets#LOOK_PREFS} prefs file, written by LookPrefs), and the Typeface for each
 * role of it. Outfit is the default and keeps the existing res/font family XMLs untouched; every other family is
 * one bundled variable TTF, weighted per role. Parsing and role mapping are pure (JVM-tested); only
 * {@link #typeface} touches Android.
 */
final class FontChoice {
    static final String PREF_KEY = "font";
    static final String OUTFIT = "outfit";
    static final String INTER = "inter";
    static final String MANROPE = "manrope";
    static final String DM_SANS = "dmsans";
    static final String PLUS_JAKARTA_SANS = "plusjakartasans";
    static final String SPACE_GROTESK = "spacegrotesk";

    /** Every selectable id, default first. */
    static final List<String> IDS = Collections.unmodifiableList(
            Arrays.asList(OUTFIT, INTER, MANROPE, DM_SANS, PLUS_JAKARTA_SANS, SPACE_GROTESK));

    /** The weight roles the layouts ask for through the font_* strings. */
    enum Role {
        BODY(400), REGULAR(400), LIGHT(300), EXTRALIGHT(200), MEDIUM(500);

        final int weight;

        Role(int weight) {
            this.weight = weight;
        }
    }

    private static final ConcurrentHashMap<String, Typeface> CACHE = new ConcurrentHashMap<>();

    private FontChoice() {}

    /** A stored value to a known id; null, blank, unknown or differently-cased input falls back to outfit. */
    static String parse(String raw) {
        if (raw == null) return OUTFIT;
        String id = raw.trim().toLowerCase(Locale.ROOT);
        return IDS.contains(id) ? id : OUTFIT;
    }

    static boolean isOutfit(String id) {
        return OUTFIT.equals(parse(id));
    }

    /** The Outfit font resource behind a font_* string, as a role; null for anything else (system family, other font). */
    static Role roleForOutfitResource(int resourceId) {
        if (resourceId == R.font.outfit) return Role.BODY;
        if (resourceId == R.font.outfit_regular) return Role.REGULAR;
        if (resourceId == R.font.outfit_light) return Role.LIGHT;
        if (resourceId == R.font.outfit_extralight) return Role.EXTRALIGHT;
        if (resourceId == R.font.outfit_medium) return Role.MEDIUM;
        return null;
    }

    /** The bundled TTF of a non-outfit id; 0 for outfit (it has per-role XML families instead). */
    static int fontResource(String id) {
        switch (parse(id)) {
            case INTER: return R.font.inter;
            case MANROPE: return R.font.manrope;
            case DM_SANS: return R.font.dmsans;
            case PLUS_JAKARTA_SANS: return R.font.plusjakartasans;
            case SPACE_GROTESK: return R.font.spacegrotesk;
            default: return 0;
        }
    }

    static String current(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Presets.LOOK_PREFS, Context.MODE_PRIVATE);
        return parse(prefs.getString(PREF_KEY, OUTFIT));
    }

    /**
     * The Typeface of one role in the chosen family, built once per process. API 28+ weights the variable font
     * with Typeface.create(base, weight, false); on 26-27 that overload only knows NORMAL/BOLD, so the font's
     * default instance (pinned to 400) is used for every role. Returns null for outfit: callers keep the
     * existing font_* path.
     */
    static Typeface typeface(Context context, String id, Role role) {
        String family = parse(id);
        int resource = fontResource(family);
        if (resource == 0) return null;
        String key = family + ':' + role.name();
        Typeface cached = CACHE.get(key);
        if (cached != null) return cached;
        Typeface base = context.getResources().getFont(resource);
        Typeface built = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? Typeface.create(base, role.weight, false) : base;
        Typeface raced = CACHE.putIfAbsent(key, built);
        return raced != null ? raced : built;
    }
}
