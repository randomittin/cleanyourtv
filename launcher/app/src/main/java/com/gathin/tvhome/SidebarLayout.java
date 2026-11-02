package com.gathin.tvhome;

import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/** Pure rules of the Sidebar layout: which rail items show, the content inset, and where D-pad keys go. */
final class SidebarLayout {
    static final int HOME = 0;
    static final int INPUTS = 1;
    static final int LOOK = 2;
    static final int ASSISTANT = 3;
    static final int SETTINGS = 4;

    static final int RAIL_WIDTH_DP = 88;
    static final int EDGE_MARGIN_DP = 16;
    static final int GAP_DP = 16;

    private SidebarLayout() {}

    /** The layout is on only for the "sidebar" pick. */
    static boolean active(ForkKeys keys) {
        return "sidebar".equals(keys.layout);
    }

    /** Rail items top to bottom; Home and Settings are always there, the rest only when their target exists. */
    static List<Integer> items(boolean inputs, boolean look, boolean assistant) {
        List<Integer> items = new ArrayList<>();
        items.add(HOME);
        if (inputs) items.add(INPUTS);
        if (look) items.add(LOOK);
        if (assistant) items.add(ASSISTANT);
        items.add(SETTINGS);
        return items;
    }

    static int railWidthPx(float density) {
        return Math.round(RAIL_WIDTH_DP * density);
    }

    /** Left padding of the content column: edge margin + rail + gap. */
    static int insetPx(float density) {
        return Math.round((EDGE_MARGIN_DP + RAIL_WIDTH_DP + GAP_DP) * density);
    }

    /** Next rail index for Up/Down: clamped to the rail, never wrapping, so the rail is no trap and no loop. */
    static int move(int index, int count, int keyCode) {
        if (count <= 0) return 0;
        int next = keyCode == KeyEvent.KEYCODE_DPAD_UP ? index - 1 : keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? index + 1 : index;
        return Math.max(0, Math.min(count - 1, next));
    }

    /**
     * Whether {@code keyCode} on a grid tile moves focus into the rail: Left from the first column (the first tile
     * of a one-row grid), and Up from the top row, which the hidden top buttons used to take.
     *
     * @param vertical  grid scrolls vertically (false = single row)
     * @param spanIndex the tile's column (vertical grid)
     * @param position  the tile's adapter position
     * @param spanCount columns of a vertical grid
     */
    static boolean gridKeyEntersRail(int keyCode, boolean vertical, int spanIndex, int position, int spanCount) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return vertical ? spanIndex == 0 : position == 0;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return !vertical || position < spanCount;
        return false;
    }
}
