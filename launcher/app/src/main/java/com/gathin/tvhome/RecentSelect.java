package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Which packages the Recent apps row shows, from (package, last foreground time) pairs. Pure Java. */
final class RecentSelect {
    private static final String[] SYSTEM_PREFIXES = {
            "com.android.systemui", "com.android.settings", "com.android.launcher", "com.android.providers.",
            "com.android.tv.settings", "com.android.permissioncontroller", "com.google.android.permissioncontroller",
            "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx", "com.google.android.tvrecommendations",
            "com.google.android.packageinstaller", "com.android.packageinstaller", "com.android.shell", "com.android.inputmethod",
    };

    private RecentSelect() {}

    static final class Stamp {
        final String packageName;
        final long millis;

        Stamp(String packageName, long millis) {
            this.packageName = packageName;
            this.millis = millis;
        }
    }

    /** Packages that are the system's own plumbing (including the bare "android" package), never worth a card. */
    static boolean isSystemPackage(String pkg) {
        if (pkg == null || pkg.equals("android")) return true;
        for (String prefix : SYSTEM_PREFIXES) {
            if (pkg.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * Newest first, one entry per package, at most {@code limit}. Drops {@code excluded} packages (this launcher
     * and every HOME app), system packages and anything {@code launchable} rejects (no launcher activity).
     */
    static List<String> select(List<Stamp> stamps, Set<String> excluded, Predicate<String> launchable, int limit) {
        List<Stamp> sorted = new ArrayList<>(stamps);
        Collections.sort(sorted, new Comparator<Stamp>() {
            @Override public int compare(Stamp a, Stamp b) {
                return Long.compare(b.millis, a.millis);
            }
        });
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Stamp s : sorted) {
            if (out.size() >= limit) break;
            String pkg = s.packageName;
            if (pkg == null || !seen.add(pkg) || excluded.contains(pkg) || isSystemPackage(pkg)) continue;
            if (launchable.test(pkg)) out.add(pkg);
        }
        return out;
    }
}
