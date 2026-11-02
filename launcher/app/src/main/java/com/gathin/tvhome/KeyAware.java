package com.gathin.tvhome;

import android.view.KeyEvent;
import android.view.View;

/**
 * Key events for an extension's own views. Offered only for D-pad, centre/enter and media keys (see
 * {@link HomeExtensionRunner#offered}), and only while the focused view is {@link #focusScope()} or inside it.
 * BACK and ESCAPE are offered only while {@link #ownsOpenPanel()} is true.
 */
interface KeyAware {
    /** Return true to consume the event; false lets Home handle it as usual. */
    boolean onKey(KeyEvent event);

    /** The root of the extension's focusable views, or null when it has none on screen right now. */
    View focusScope();

    /** True while the extension has a panel open that Back should close. */
    default boolean ownsOpenPanel() {
        return false;
    }
}
