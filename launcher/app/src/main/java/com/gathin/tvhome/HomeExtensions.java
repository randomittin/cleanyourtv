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
        return extensions;
    }
}
