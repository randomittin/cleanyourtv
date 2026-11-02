package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;

/**
 * The fork's extensions, in attach order. Register one with one line in {@link #create}. An empty list is
 * what a stock build runs with: every fan-out is then a loop over nothing.
 */
final class HomeExtensions {
    private HomeExtensions() {}

    static List<HomeExtension> create(ForkKeys keys) {
        List<HomeExtension> extensions = new ArrayList<>();
        extensions.add(new LookExtension());
        if (keys.nowPlaying) extensions.add(new NowPlayingExtension());
        if (keys.continueWatching || keys.recentApps) extensions.add(new ShelfExtension());
        extensions.add(new SidebarExtension());
        return extensions;
    }
}
