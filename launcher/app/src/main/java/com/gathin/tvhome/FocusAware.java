package com.gathin.tvhome;

/** A grid or dock tile took focus (after Home has moved its highlight). */
interface FocusAware {
    void onTileFocused(int position, String packageName);
}
