package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Pure rules of the Look panel: value cycling, dark flips, icon pack ordering. No Android types. */
final class LookLogic {
    private LookLogic() {}

    /** An installed icon pack: package name and the label the user knows it by. */
    static final class Pack {
        final String packageName;
        final String label;

        Pack(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }

    /**
     * The value {@code direction} steps (+1 right, -1 left) from {@code current}, wrapping at both ends. A current
     * value not in the list counts as sitting before the first (right gives the first) or after the last (left
     * gives the last). Returns null for an empty list.
     */
    static String cycle(List<String> values, String current, int direction) {
        if (values.isEmpty()) return null;
        int n = values.size();
        int at = values.indexOf(current);
        if (at < 0) return direction >= 0 ? values.get(0) : values.get(n - 1);
        int step = direction >= 0 ? 1 : -1;
        return values.get(((at + step) % n + n) % n);
    }

    /** True when the effective dark state differs from the one Home was built with. */
    static boolean darkFlipped(boolean atAttach, boolean now) {
        return atAttach != now;
    }

    /** "19:00-07:00", the Auto window shown beside the Dark row. */
    static String autoWindow(int fromHour, int toHour) {
        return String.format(Locale.ROOT, "%02d:00–%02d:00", fromHour, toHour);
    }

    /**
     * Icon pack choices in display order: the default (empty package) first, then installed packs by label
     * (case-insensitive, then package name). A package seen twice (it answers both pack actions) appears once; blank
     * packages are dropped; a blank label falls back to the package name.
     */
    static List<Pack> packOrder(List<Pack> installed, String defaultLabel) {
        List<Pack> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Pack p : installed) {
            if (p.packageName == null || p.packageName.trim().isEmpty() || !seen.add(p.packageName)) continue;
            String label = p.label == null || p.label.trim().isEmpty() ? p.packageName : p.label.trim();
            found.add(new Pack(p.packageName, label));
        }
        Collections.sort(found, (a, b) -> {
            int byLabel = a.label.compareToIgnoreCase(b.label);
            return byLabel != 0 ? byLabel : a.packageName.compareTo(b.packageName);
        });
        List<Pack> out = new ArrayList<>();
        out.add(new Pack("", defaultLabel));
        out.addAll(found);
        return out;
    }
}
