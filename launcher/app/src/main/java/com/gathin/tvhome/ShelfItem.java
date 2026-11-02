package com.gathin.tvhome;

import java.util.Objects;

/** One card on the shelf: a Watch Next entry (poster, progress) or a recently used app (banner). Pure data. */
final class ShelfItem {
    enum Kind { WATCH_NEXT, APP }

    final Kind kind;
    final String title;
    /** Poster or thumbnail URI of a Watch Next entry; null for an app. */
    final String posterUri;
    /** The publisher's intent_uri; null for an app. */
    final String intentUri;
    /** The app that owns the entry, or the app itself; may be null for a Watch Next row that names none. */
    final String packageName;
    /** 0..100, or -1 when the entry carries no usable playback position. */
    final int progress;

    private ShelfItem(Kind kind, String title, String posterUri, String intentUri, String packageName, int progress) {
        this.kind = kind;
        this.title = title;
        this.posterUri = posterUri;
        this.intentUri = intentUri;
        this.packageName = packageName;
        this.progress = progress;
    }

    static ShelfItem watchNext(String title, String posterUri, String intentUri, String packageName, int progress) {
        return new ShelfItem(Kind.WATCH_NEXT, title, posterUri, intentUri, packageName, progress);
    }

    static ShelfItem app(String packageName, String label) {
        return new ShelfItem(Kind.APP, label, null, null, packageName, -1);
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof ShelfItem)) return false;
        ShelfItem other = (ShelfItem) o;
        return kind == other.kind && progress == other.progress && Objects.equals(title, other.title)
                && Objects.equals(posterUri, other.posterUri) && Objects.equals(intentUri, other.intentUri)
                && Objects.equals(packageName, other.packageName);
    }

    @Override public int hashCode() {
        return Objects.hash(kind, title, posterUri, intentUri, packageName, progress);
    }
}
