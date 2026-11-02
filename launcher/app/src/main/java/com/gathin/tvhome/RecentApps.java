package com.gathin.tvhome;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The apps used most recently. With usage access (granted through appops, PACKAGE_USAGE_STATS) the source is
 * UsageStatsManager's foreground events of the last 14 days; without it, or when it has nothing, the apps Home
 * itself opened (LastOpened's prefs). Runs on a worker thread.
 */
final class RecentApps {
    private static final String TAG = "TvHome.Shelf";
    private static final long WINDOW_MILLIS = 14L * 24 * 60 * 60 * 1000;
    /** Same name LastOpened stores under; read here because LastOpened only answers one package at a time. */
    private static final String LAST_OPENED_PREFS = "last_opened";

    private RecentApps() {}

    static List<ShelfItem> read(Context ctx, int limit) {
        List<ShelfItem> items = new ArrayList<>();
        try {
            PackageManager pm = ctx.getPackageManager();
            Set<String> excluded = homeApps(ctx, pm);
            List<RecentSelect.Stamp> stamps = usageStamps(ctx);
            List<String> packages = stamps.isEmpty() ? new ArrayList<String>()
                    : RecentSelect.select(stamps, excluded, p -> launchIntent(pm, p) != null, limit);
            if (packages.isEmpty()) {
                packages = RecentSelect.select(lastOpenedStamps(ctx), excluded, p -> launchIntent(pm, p) != null, limit);
            }
            for (String pkg : packages) {
                items.add(ShelfItem.app(pkg, label(pm, pkg)));
            }
        } catch (Exception e) {
            Log.w(TAG, "Recent apps not readable: " + e);
        }
        return items;
    }

    /** The intent that starts {@code pkg} from a TV home: its Leanback launcher, else its ordinary launcher. */
    static Intent launchIntent(PackageManager pm, String pkg) {
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        return intent != null ? intent : pm.getLaunchIntentForPackage(pkg);
    }

    /** This launcher plus every other HOME app: they are never "recent". */
    private static Set<String> homeApps(Context ctx, PackageManager pm) {
        Set<String> out = new HashSet<>();
        out.add(ctx.getPackageName());
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo ri : pm.queryIntentActivities(home, 0)) {
            if (ri.activityInfo != null) out.add(ri.activityInfo.packageName);
        }
        return out;
    }

    @SuppressWarnings("deprecation") // MOVE_TO_FOREGROUND is the only foreground event on API 26-28 and equals ACTIVITY_RESUMED after.
    private static List<RecentSelect.Stamp> usageStamps(Context ctx) {
        List<RecentSelect.Stamp> out = new ArrayList<>();
        AppOpsManager ops = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null || ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.getPackageName())
                != AppOpsManager.MODE_ALLOWED) {
            return out;
        }
        UsageStatsManager usage = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usage == null) return out;
        long now = System.currentTimeMillis();
        UsageEvents events = usage.queryEvents(now - WINDOW_MILLIS, now);
        Map<String, Long> latest = new HashMap<>();
        UsageEvents.Event event = new UsageEvents.Event();
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() != UsageEvents.Event.MOVE_TO_FOREGROUND) continue;
            Long seen = latest.get(event.getPackageName());
            if (seen == null || seen < event.getTimeStamp()) latest.put(event.getPackageName(), event.getTimeStamp());
        }
        for (Map.Entry<String, Long> e : latest.entrySet()) out.add(new RecentSelect.Stamp(e.getKey(), e.getValue()));
        return out;
    }

    private static List<RecentSelect.Stamp> lastOpenedStamps(Context ctx) {
        List<RecentSelect.Stamp> out = new ArrayList<>();
        SharedPreferences prefs = ctx.getSharedPreferences(LAST_OPENED_PREFS, Context.MODE_PRIVATE);
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            if (e.getValue() instanceof Long) out.add(new RecentSelect.Stamp(e.getKey(), (Long) e.getValue()));
        }
        return out;
    }

    private static String label(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }
}
