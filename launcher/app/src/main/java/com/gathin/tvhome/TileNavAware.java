package com.gathin.tvhome;

/**
 * Consulted where Home's Down from the last tile row ends (and the single-row Down): the same place a Library
 * button takes the key. Only KEYCODE_DPAD_DOWN is offered. Return true after moving focus to the extension's own
 * view; Home swallows the key either way, so false just means nothing was there to take it.
 */
interface TileNavAware {
    boolean onTileVerticalKey(int position, int keyCode);
}
