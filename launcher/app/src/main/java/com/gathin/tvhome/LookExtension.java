package com.gathin.tvhome;

import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalTime;

/**
 * The Look pill (top right) and its panel, plus the dark Auto flip: when the effective dark state differs from
 * the one Home was built with, checked on resume before the first draw and on Home's minute tick, Home is rebuilt.
 */
final class LookExtension implements HomeExtension, ResumeAware, MinuteAware, KeyAware {
    private HomeHost host;
    private ForkKeys keys;
    private LookPanel panel;
    private TextView pill;
    private boolean darkAtAttach;

    @Override public void attach(HomeHost host, ForkKeys keys) {
        this.host = host;
        this.keys = keys;
        this.darkAtAttach = darkNow();
        if (!keys.lookPill) return;
        pill = buildPill();
        host.topButtons().addView(pill, 0);
    }

    @Override public void detach() {
        if (pill != null && pill.getParent() instanceof ViewGroup) ((ViewGroup) pill.getParent()).removeView(pill);
        pill = null;
        panel = null;
        host = null;
    }

    @Override public void onResume() {
        rebuildIfDarkFlipped();
    }

    @Override public void onPause() {}

    @Override public void onMinute() {
        rebuildIfDarkFlipped();
    }

    @Override public boolean onKey(KeyEvent event) {
        return panel != null && panel.isOpen() && panel.handleKey(event, host.activity().getCurrentFocus());
    }

    @Override public View focusScope() {
        return panel != null && panel.isOpen() ? panel.view() : null;
    }

    @Override public boolean ownsOpenPanel() {
        return panel != null && panel.isOpen();
    }

    private void rebuildIfDarkFlipped() {
        if (host == null) return;
        if (LookLogic.darkFlipped(darkAtAttach, darkNow())) {
            darkAtAttach = !darkAtAttach;   // one rebuild per flip even if another tick lands before the new Home attaches
            host.recreateHome();
        }
    }

    private boolean darkNow() {
        return ForkKeys.darkNow(keys.darkMode, keys.darkFromHour, keys.darkToHour, LocalTime.now());
    }

    private TextView buildPill() {
        HomeHost h = host;
        TextView p = new TextView(h.activity(), null, 0, R.style.TopButton);
        p.setId(R.id.btnLook);
        p.setText(R.string.look_title);
        p.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_look, 0, 0, 0);
        p.setNextFocusDownId(R.id.grid);
        // Up and the outer edge go nowhere, like every other pill, so the focus finder cannot jump far below.
        p.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            boolean rtl = v.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            return keyCode == KeyEvent.KEYCODE_DPAD_UP
                    || keyCode == (rtl ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT);
        });
        if (iconOnly(h)) {
            p.setContentDescription(p.getText());
            p.setText(null);
            int pad = Math.round(10 * h.density());
            p.setPaddingRelative(pad, 0, pad, 0);
            p.setCompoundDrawablePadding(0);
        }
        boolean iconOnly = iconOnly(h);
        int size = Math.round(42 * h.density());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                iconOnly ? size : ViewGroup.LayoutParams.WRAP_CONTENT, size);
        lp.setMarginStart(Math.round(10 * h.density()));
        p.setLayoutParams(lp);
        p.setOnClickListener(v -> {
            if (panel != null && panel.isOpen()) return;
            panel = new LookPanel(h, keys);
            panel.show(v);
        });
        return p;
    }

    /** Home shows its Settings pill as a bare icon (no label) exactly when icon_only_buttons is on. */
    private static boolean iconOnly(HomeHost h) {
        View settings = h.activity().findViewById(R.id.btnSettings);
        return settings instanceof TextView && ((TextView) settings).getText().length() == 0;
    }
}
