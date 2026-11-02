package com.gathin.tvhome;

/**
 * A fork feature that lives beside Home instead of inside MainActivity. Register it in {@link HomeExtensions}.
 * Optionally implement {@link ResumeAware}, {@link MinuteAware}, {@link KeyAware}, {@link TileNavAware} and
 * {@link FocusAware} for the matching events. Every call into an extension runs inside a try/catch: one that
 * throws is detached and dropped, and Home carries on without it.
 */
interface HomeExtension {
    /** Called once from onCreate after Home is wired. Add views to the host's slots here, synchronously. */
    void attach(HomeHost host, ForkKeys keys);

    /** Remove listeners, observers and animators; called from onDestroy, or after this extension failed. */
    void detach();
}
