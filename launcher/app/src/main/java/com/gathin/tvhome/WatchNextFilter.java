package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns the raw Watch Next rows into the shelf's cards. The provider is read without a selection (a sideloaded
 * app may not pass one), so every rule lives here: rows with no deep link, finished episodes, stale rows and
 * old promotions are dropped, the rest are ordered newest engagement first. Pure Java, tested on the JVM.
 */
final class WatchNextFilter {
    static final int MAX_ITEMS = 12;
    /** A "continue" row at or past this percentage is a finished episode that lingers in the table. */
    static final int FINISHED_PERCENT = 95;
    static final long MAX_AGE_DAYS = 180;
    /** Type 2 ("new") rows are promotions; they only count while fresh. */
    static final long NEW_MAX_AGE_DAYS = 30;
    /** TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE and _NEW, as ints to keep this class off android.jar. */
    static final int TYPE_CONTINUE = 0;
    static final int TYPE_NEW = 2;

    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    private WatchNextFilter() {}

    /** One provider row. Absent columns are null (text), -1 (type) or 0 (times). */
    static final class Row {
        final String title, episodeTitle, posterUri, thumbnailUri, intentUri, packageName;
        final int type;
        final long engagedMillis, positionMillis, durationMillis;

        Row(String title, String episodeTitle, String posterUri, String thumbnailUri, String intentUri,
            String packageName, int type, long engagedMillis, long positionMillis, long durationMillis) {
            this.title = title;
            this.episodeTitle = episodeTitle;
            this.posterUri = posterUri;
            this.thumbnailUri = thumbnailUri;
            this.intentUri = intentUri;
            this.packageName = packageName;
            this.type = type;
            this.engagedMillis = engagedMillis;
            this.positionMillis = positionMillis;
            this.durationMillis = durationMillis;
        }
    }

    /** Percent played, 0..100, or -1 when the duration or the position is missing. */
    static int progressPercent(long positionMillis, long durationMillis) {
        if (durationMillis <= 0 || positionMillis <= 0) return -1;
        if (positionMillis >= durationMillis) return 100;
        return (int) (positionMillis * 100 / durationMillis);
    }

    static List<ShelfItem> filter(List<Row> rows, long nowMillis) {
        List<Row> kept = new ArrayList<>();
        for (Row row : rows) {
            if (isBlank(row.intentUri) || titleOf(row) == null) continue;
            if (row.engagedMillis > 0) {
                long ageMillis = nowMillis - row.engagedMillis;
                if (ageMillis > MAX_AGE_DAYS * DAY_MILLIS) continue;
                if (row.type == TYPE_NEW && ageMillis > NEW_MAX_AGE_DAYS * DAY_MILLIS) continue;
            }
            boolean continuing = row.type == TYPE_CONTINUE || row.type < 0;
            if (continuing && progressPercent(row.positionMillis, row.durationMillis) >= FINISHED_PERCENT) continue;
            kept.add(row);
        }
        // Stable: rows without an engagement time keep the provider's order, after every dated row.
        Collections.sort(kept, new Comparator<Row>() {
            @Override public int compare(Row a, Row b) {
                boolean aDated = a.engagedMillis > 0;
                boolean bDated = b.engagedMillis > 0;
                if (aDated != bDated) return aDated ? -1 : 1;
                return aDated ? Long.compare(b.engagedMillis, a.engagedMillis) : 0;
            }
        });
        List<ShelfItem> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Row row : kept) {
            if (items.size() >= MAX_ITEMS) break;
            if (!seen.add(row.intentUri)) continue;
            String poster = isBlank(row.posterUri) ? row.thumbnailUri : row.posterUri;
            String pkg = isBlank(row.packageName) ? LaunchSpec.packageOf(row.intentUri) : row.packageName;
            items.add(ShelfItem.watchNext(titleOf(row), isBlank(poster) ? null : poster, row.intentUri, pkg,
                    progressPercent(row.positionMillis, row.durationMillis)));
        }
        return items;
    }

    /** The row's title, its episode title when that is empty, or null when both are. */
    static String titleOf(Row row) {
        if (!isBlank(row.title)) return row.title.trim();
        if (!isBlank(row.episodeTitle)) return row.episodeTitle.trim();
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
