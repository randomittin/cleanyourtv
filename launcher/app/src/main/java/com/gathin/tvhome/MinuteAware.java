package com.gathin.tvhome;

/** Once a minute while Home is resumed (rides Home's own minute tick; no timer of the extension's own). */
interface MinuteAware {
    void onMinute();
}
