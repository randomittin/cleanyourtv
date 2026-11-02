package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Look panel: a column of rows, each cycling one value with Left/Right (OK steps right). Every step writes
 * the pick through {@link LookPrefs#put} and rebuilds Home once through {@link HomeHost#recreateHome}. Focus
 * order is explicit (Up/Down stop at the ends), so the panel never loses or traps focus; Back is the popup's own.
 */
final class LookPanel {
    private static final String[] PACK_ACTIONS = {"org.adw.launcher.THEMES", "com.novalauncher.THEME"};

    private final HomeHost host;
    private final ForkKeys keys;
    private final Context ctx;
    private final View root;
    private final List<View> rows = new ArrayList<>();
    private boolean applied;
    private boolean open;

    LookPanel(HomeHost host, ForkKeys keys) {
        this.host = host;
        this.keys = keys;
        this.ctx = host.activity();
        this.root = LayoutInflater.from(ctx).inflate(R.layout.panel_look, null, false);
        LinearLayout list = root.findViewById(R.id.lookRows);
        addChoiceRow(list, LookPrefs.LAYOUT, R.string.look_row_layout, R.array.look_layout_ids, R.array.look_layout_labels, keys.layout);
        addChoiceRow(list, LookPrefs.THEME_PRESET, R.string.look_row_theme, R.array.look_theme_ids, R.array.look_theme_labels, keys.themePreset);
        addChoiceRow(list, LookPrefs.DARK_MODE, R.string.look_row_dark, R.array.look_dark_ids, R.array.look_dark_labels, keys.darkMode);
        addChoiceRow(list, LookPrefs.FONT, R.string.look_row_font, R.array.look_font_ids, R.array.look_font_labels, keys.font);
        addIconRow(list);
        addResetRow(list);
        chainFocus();
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                open = true;
                if (!rows.isEmpty()) rows.get(0).requestFocus();
            }

            @Override public void onViewDetachedFromWindow(View v) {
                open = false;
            }
        });
    }

    View view() {
        return root;
    }

    boolean isOpen() {
        return open;
    }

    /** Shows the panel under {@code anchor}; the first row takes focus when the window attaches. */
    void show(View anchor) {
        applied = false;
        host.showGlassPanel(root, anchor, 340);
    }

    /** Left/Right/OK on the focused row, for keys that reach the extension rather than the row. */
    boolean handleKey(KeyEvent event, View focused) {
        return focused != null && rows.contains(focused) && step(focused, event);
    }

    private boolean step(View row, KeyEvent event) {
        int code = event.getKeyCode();
        int direction;
        if (code == KeyEvent.KEYCODE_DPAD_LEFT) direction = -1;
        else if (code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_CENTER
                || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) direction = 1;
        else return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) ((Row) row.getTag()).step(direction);
        return true;
    }

    private abstract static class Row {
        abstract void step(int direction);
    }

    private void addChoiceRow(LinearLayout list, String key, int titleRes, int idsRes, int labelsRes, String current) {
        List<String> ids = new ArrayList<>(Arrays.asList(ctx.getResources().getStringArray(idsRes)));
        List<String> labels = Arrays.asList(ctx.getResources().getStringArray(labelsRes));
        List<String> values = new ArrayList<>();
        List<String> shown = new ArrayList<>();
        for (int i = 0; i < ids.size() && i < labels.size(); i++) {
            if (!LookPrefs.valid(key, ids.get(i))) continue;
            values.add(ids.get(i));
            shown.add(labels.get(i));
        }
        boolean dark = LookPrefs.DARK_MODE.equals(key);
        buildRow(list, ctx.getString(titleRes), values, shown, current, key, dark);
    }

    private void addIconRow(LinearLayout list) {
        PackageManager pm = ctx.getPackageManager();
        List<LookLogic.Pack> installed = new ArrayList<>();
        for (String action : PACK_ACTIONS) {
            try {
                for (ResolveInfo info : pm.queryIntentActivities(new Intent(action), 0)) {
                    installed.add(new LookLogic.Pack(info.activityInfo.packageName, String.valueOf(info.loadLabel(pm))));
                }
            } catch (RuntimeException e) {
                // A package manager that refuses the query leaves the row at Default only.
            }
        }
        List<LookLogic.Pack> packs = LookLogic.packOrder(installed, ctx.getString(R.string.look_icons_default));
        List<String> values = new ArrayList<>();
        List<String> shown = new ArrayList<>();
        for (LookLogic.Pack p : packs) {
            values.add(p.packageName);
            shown.add(p.label);
        }
        buildRow(list, ctx.getString(R.string.look_row_icons), values, shown, keys.iconPack, LookPrefs.ICON_PACK, false);
    }

    private void buildRow(LinearLayout list, String title, List<String> values, List<String> labels, String current,
                          String key, boolean showDarkWindow) {
        View row = newRow(list, title);
        TextView value = row.findViewById(R.id.lookRowValue);
        String[] now = {current};
        Runnable render = () -> {
            int at = values.indexOf(now[0]);
            String label = at >= 0 ? labels.get(at) : now[0];
            if (showDarkWindow && "auto".equals(now[0])) {
                label = label + "  " + LookLogic.autoWindow(keys.darkFromHour, keys.darkToHour);
            }
            value.setText("‹  " + label + "  ›");
        };
        render.run();
        row.setTag(new Row() {
            @Override void step(int direction) {
                String next = LookLogic.cycle(values, now[0], direction);
                if (next == null || next.equals(now[0])) return;
                now[0] = next;
                render.run();
                apply(() -> LookPrefs.put(ctx, key, next));
            }
        });
        attach(list, row);
    }

    private void addResetRow(LinearLayout list) {
        View row = newRow(list, ctx.getString(R.string.look_reset));
        ((TextView) row.findViewById(R.id.lookRowValue)).setText(null);
        row.setTag(new Row() {
            @Override void step(int direction) {
                if (direction > 0) apply(() -> LookPrefs.clear(ctx));
            }
        });
        attach(list, row);
    }

    /** Runs the write and rebuilds Home, once per opening (a held key cannot queue a second rebuild). */
    private void apply(Runnable write) {
        if (applied) return;
        applied = true;
        write.run();
        host.recreateHome();
    }

    private View newRow(LinearLayout list, String title) {
        View row = LayoutInflater.from(ctx).inflate(R.layout.look_row, list, false);
        ((TextView) row.findViewById(R.id.lookRowTitle)).setText(title);
        row.setId(View.generateViewId());
        return row;
    }

    private void attach(LinearLayout list, View row) {
        list.addView(row);
        rows.add(row);
        row.setOnClickListener(v -> ((Row) v.getTag()).step(1));
        row.setOnKeyListener((v, code, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN && event.getAction() != KeyEvent.ACTION_UP) return false;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) return step(v, event);
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) return step(v, event);
            return false;   // OK reaches the click listener; Up/Down follow the explicit chain
        });
    }

    /** Up/Down between rows by id; the ends point at themselves so focus cannot escape to Home behind the popup. */
    private void chainFocus() {
        for (int i = 0; i < rows.size(); i++) {
            View row = rows.get(i);
            row.setNextFocusUpId(rows.get(Math.max(0, i - 1)).getId());
            row.setNextFocusDownId(rows.get(Math.min(rows.size() - 1, i + 1)).getId());
            row.setNextFocusLeftId(row.getId());
            row.setNextFocusRightId(row.getId());
        }
    }
}
