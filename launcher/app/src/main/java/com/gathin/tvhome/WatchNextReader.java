package com.gathin.tvhome;

import android.content.ContentResolver;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the system's Continue watching table (docs/watch-next.md): no selection, because a caller without
 * ACCESS_ALL_EPG_DATA gets a SecurityException for one, and a named projection, because the provider's default
 * one fails on some TVs ("no such column: split_id"). Needs READ_TV_LISTINGS granted; without it, or on any
 * provider error, the answer is an empty list.
 */
final class WatchNextReader {
    private static final String TAG = "TvHome.Shelf";

    static final String[] PROJECTION = {
            TvContract.WatchNextPrograms._ID,
            TvContract.WatchNextPrograms.COLUMN_TITLE,
            TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE,
            TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
            TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI,
            TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
            TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
            TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE,
            TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
            TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
    };

    private static final int TITLE = 1, EPISODE_TITLE = 2, POSTER = 3, THUMBNAIL = 4, INTENT = 5, PACKAGE = 6,
            TYPE = 7, ENGAGED = 8, POSITION = 9, DURATION = 10;

    private static boolean warned;

    private WatchNextReader() {}

    /** Every row the provider shows this app, unfiltered and in provider order; empty on any failure. */
    static List<WatchNextFilter.Row> read(ContentResolver resolver) {
        List<WatchNextFilter.Row> rows = new ArrayList<>();
        Uri uri = TvContract.WatchNextPrograms.CONTENT_URI;
        try (Cursor c = resolver.query(uri, PROJECTION, null, null, null)) {
            if (c == null) return rows;
            while (c.moveToNext()) {
                rows.add(new WatchNextFilter.Row(
                        text(c, TITLE), text(c, EPISODE_TITLE), text(c, POSTER), text(c, THUMBNAIL),
                        text(c, INTENT), text(c, PACKAGE),
                        c.isNull(TYPE) ? -1 : c.getInt(TYPE),
                        number(c, ENGAGED), number(c, POSITION), number(c, DURATION)));
            }
        } catch (Exception e) {
            // SecurityException (permission not granted), SQLiteException (vendor provider), anything else.
            if (!warned) {
                warned = true;
                Log.w(TAG, "Watch Next not readable: " + e);
            }
            return new ArrayList<>();
        }
        return rows;
    }

    private static String text(Cursor c, int column) {
        return c.isNull(column) ? null : c.getString(column);
    }

    private static long number(Cursor c, int column) {
        return c.isNull(column) ? 0L : c.getLong(column);
    }
}
