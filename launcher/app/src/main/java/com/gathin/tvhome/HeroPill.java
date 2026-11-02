package com.gathin.tvhome;

/**
 * dock_title_style "pill": the focused dock app's name in a small dark rounded pill directly above its tile,
 * like a macOS dock tooltip. Pure arithmetic and constants, so the placement is testable without a TV.
 */
final class HeroPill {
    private HeroPill() { }

    static final String STYLE_TEXT = "text";
    static final String STYLE_PILL = "pill";

    /** Name size in the pill. */
    static final float TEXT_SP = 16f;
    /** Pill fill: black at 70 percent, dark enough over any wallpaper or glow. */
    static final int FILL_COLOR = 0xB3000000;
    /** Larger than half the pill's height: GradientDrawable clamps it, which makes the ends fully round. */
    static final float CORNER_DP = 100f;
    static final float PAD_HORIZONTAL_DP = 14f;
    static final float PAD_VERTICAL_DP = 6f;
    /** Space between the pill's bottom edge and the dock tile's top edge, clear of the focus glass. */
    static final float GAP_DP = 22f;
    /** The pill never comes closer than this to either screen edge. */
    static final float EDGE_MARGIN_DP = 24f;

    /** A known dock_title_style, or text (today's look) for anything else, so a typo leaves Home as it was. */
    static String style(String value) {
        return value != null && STYLE_PILL.equalsIgnoreCase(value.trim()) ? STYLE_PILL : STYLE_TEXT;
    }

    /**
     * Left edge for a pill {@code width} wide centred on {@code centerX}, kept {@code margin} inside a screen
     * {@code screenW} wide. A pill wider than the room left between the margins starts at the margin.
     */
    static float clampX(float centerX, float width, float screenW, float margin) {
        float left = centerX - width / 2f;
        float max = screenW - margin - width;
        if (left > max) left = max;
        return left < margin ? margin : left;
    }
}
