package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fork layout, theme and dark presets merged into the theme overlay before Config parses it. Merge order, later
 * wins: theme config.json, layout preset, theme preset, dark preset, Look picks. With the defaults (classic, calm,
 * dark off, no icon pack pick) the overlay comes back untouched, null included.
 */
final class Presets {
    private static final String TAG = "TvHome.Presets";
    /** Look panel picks. Its own file, so a theme push (which wipes Config's choices) leaves them alone. */
    static final String LOOK_PREFS = "look";
    private static final String[] LOOK_KEYS = {"layout", "theme_preset", "dark_mode", "font", "icon_pack"};

    private Presets() {}

    /**
     * {@code json} with the presets merged in; {@code json} itself (even null) when nothing applies or anything
     * fails. A theme key {@code look_reset: true} first clears the Look picks (recovery from a bad pick).
     */
    static JSONObject apply(Context ctx, JSONObject json) {
        return PresetsMerge.orUnchanged(json, () -> {
            Map<String, Object> theme = flat(json);
            Map<String, Object> over = PresetsMerge.overrides(theme, lookPicks(ctx, theme), new AssetSource(ctx), LocalTime.now());
            if (over == null) return null;
            JSONObject out = json == null ? new JSONObject() : new JSONObject(json.toString());
            for (Map.Entry<String, Object> e : over.entrySet()) out.put(e.getKey(), e.getValue());
            return out;
        });
    }

    /** The merged overlay as flat primitives, for {@link ForkKeys}; empty when there is no overlay. */
    static Map<String, Object> mergedFlat(Context ctx) {
        try {
            return flat(apply(ctx, Config.overlay(ctx)));
        } catch (Exception e) {
            Log.w(TAG, "no merged overlay: " + e.getMessage());
            return new HashMap<>();
        }
    }

    private static Map<String, String> lookPicks(Context ctx, Map<String, Object> theme) {
        SharedPreferences prefs = ctx.getSharedPreferences(LOOK_PREFS, Context.MODE_PRIVATE);
        if (PresetsMerge.bool(theme, "look_reset")) prefs.edit().clear().apply();
        Map<String, String> picks = new HashMap<>();
        for (String key : LOOK_KEYS) {
            String value = prefs.getString(key, null);
            if (value != null) picks.put(key, value);
        }
        return picks;
    }

    /** Booleans, strings and numbers of the top level; arrays and objects are not presets' business. */
    private static Map<String, Object> flat(JSONObject json) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (json == null) return out;
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object v = json.opt(key);
            if (v instanceof Boolean || v instanceof String || v instanceof Number) out.put(key, v);
        }
        return out;
    }

    /** presets/&lt;name&gt;.json from the APK's assets. */
    private static final class AssetSource implements PresetsMerge.Source {
        private final Context ctx;

        AssetSource(Context ctx) {
            this.ctx = ctx;
        }

        @Override public Map<String, Object> preset(String name) throws Exception {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try (InputStream in = ctx.getAssets().open("presets/" + name + ".json")) {
                byte[] chunk = new byte[2048];
                int n;
                while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
            }
            return flat(new JSONObject(buf.toString("UTF-8")));
        }
    }
}
