package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Small image loader for the shelf: posters over https (or from content:// and android.resource://), app banners
 * and app icons. Two worker threads, an LruCache capped at min(8 MB, maxMemory/16), downsampled two-pass decode,
 * RGB_565 for posters. No library: the APK stays small and the TV's 1.7 GB is not spent on art.
 */
final class PosterLoader {
    private static final String TAG = "TvHome.Shelf";
    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS = 6000;

    interface Callback {
        void onLoaded(Bitmap bitmap);
    }

    /** Cancels a pending load; a cancelled load never calls back. */
    static final class Handle {
        private volatile boolean cancelled;

        void cancel() {
            cancelled = true;
        }
    }

    private final Context ctx;
    private final ExecutorService pool = Executors.newFixedThreadPool(2, runnable -> {
        Thread t = new Thread(runnable, "shelf-art");
        t.setDaemon(true);
        return t;
    });
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache;

    PosterLoader(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        long limit = Math.min(8L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 16);
        cache = new LruCache<String, Bitmap>((int) limit) {
            @Override protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
    }

    /** Largest power-of-two inSampleSize that keeps the decoded image at least maxW x maxH; 1 for bad input. */
    static int sampleSize(int srcW, int srcH, int maxW, int maxH) {
        if (srcW <= 0 || srcH <= 0 || maxW <= 0 || maxH <= 0) return 1;
        int sample = 1;
        while (srcW / (sample * 2) >= maxW && srcH / (sample * 2) >= maxH) sample *= 2;
        return sample;
    }

    /** True for the only poster schemes read: https, plus the local providers content and android.resource. */
    static boolean isSupportedUri(String uri) {
        if (uri == null) return false;
        String scheme = Uri.parse(uri).getScheme();
        return "https".equals(scheme) || "content".equals(scheme) || "android.resource".equals(scheme);
    }

    Handle loadPoster(String uri, int maxW, int maxH, Callback callback) {
        return submit("poster:" + uri, () -> decode(fetch(uri), maxW, maxH), callback);
    }

    Handle loadAppBanner(String pkg, int w, int h, Callback callback) {
        return submit("banner:" + pkg + ":" + w, () -> appBanner(pkg, w, h), callback);
    }

    Handle loadAppIcon(String pkg, int px, Callback callback) {
        return submit("icon:" + pkg + ":" + px, () -> {
            Drawable icon = ctx.getPackageManager().getApplicationIcon(pkg);
            Bitmap bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
            draw(icon, new Canvas(bitmap), 0, 0, px, px);
            return bitmap;
        }, callback);
    }

    void shutdown() {
        pool.shutdownNow();
        cache.evictAll();
    }

    private Handle submit(String key, Callable<Bitmap> job, Callback callback) {
        Handle handle = new Handle();
        Bitmap hit = cache.get(key);
        if (hit != null) {
            callback.onLoaded(hit);
            return handle;
        }
        try {
            pool.execute(() -> {
                if (handle.cancelled) return;
                Bitmap bitmap = null;
                try {
                    bitmap = job.call();
                } catch (Exception | OutOfMemoryError e) {
                    Log.w(TAG, "art not loaded (" + key + "): " + e);
                }
                if (bitmap == null) return;
                cache.put(key, bitmap);
                Bitmap loaded = bitmap;
                main.post(() -> {
                    if (!handle.cancelled) callback.onLoaded(loaded);
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException shutDown) {
            // Home is gone; nothing to draw into.
        }
        return handle;
    }

    private byte[] fetch(String uri) throws IOException {
        if (!isSupportedUri(uri)) return null;
        Uri parsed = Uri.parse(uri);
        if ("https".equals(parsed.getScheme())) return fetchHttps(new URL(uri));
        try (InputStream in = ctx.getContentResolver().openInputStream(parsed)) {
            return in == null ? null : readCapped(in);
        }
    }

    /** Follows up to three redirects by hand, and only to https: a redirect to http ends the load. */
    private static byte[] fetchHttps(URL start) throws IOException {
        URL url = start;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                int code = conn.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = conn.getHeaderField("Location");
                    if (location == null) return null;
                    URL next = new URL(url, location);
                    if (!"https".equals(next.getProtocol())) return null;
                    url = next;
                    continue;
                }
                if (code != HttpURLConnection.HTTP_OK) return null;
                try (InputStream in = conn.getInputStream()) {
                    return readCapped(in);
                }
            } finally {
                conn.disconnect();
            }
        }
        return null;
    }

    private static byte[] readCapped(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(32 * 1024);
        byte[] chunk = new byte[16 * 1024];
        int n;
        while ((n = in.read(chunk)) > 0) {
            if (out.size() + n > MAX_BYTES) return null;
            out.write(chunk, 0, n);
        }
        return out.toByteArray();
    }

    private static Bitmap decode(byte[] data, int maxW, int maxH) {
        if (data == null) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxW, maxH);
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
    }

    /** The app's TV banner when it has a banner-shaped one, else its icon centred on a transparent tile. */
    private Bitmap appBanner(String pkg, int w, int h) throws PackageManager.NameNotFoundException {
        PackageManager pm = ctx.getPackageManager();
        Drawable banner = null;
        Intent launch = RecentApps.launchIntent(pm, pkg);
        if (launch != null) banner = pm.getActivityBanner(launch);
        if (banner == null) banner = pm.getApplicationBanner(pkg);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        if (banner != null && AppAdapter.isBannerShaped(banner.getIntrinsicWidth(), banner.getIntrinsicHeight())) {
            draw(banner, canvas, 0, 0, w, h);
        } else {
            int side = h * 3 / 5;
            int left = (w - side) / 2;
            int top = (h - side) / 2;
            draw(pm.getApplicationIcon(pkg), canvas, left, top, left + side, top + side);
        }
        return bitmap;
    }

    private static void draw(Drawable drawable, Canvas canvas, int left, int top, int right, int bottom) {
        drawable.setBounds(left, top, right, bottom);
        drawable.draw(canvas);
    }
}
