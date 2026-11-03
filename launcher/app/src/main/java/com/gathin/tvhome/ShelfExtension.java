package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * The shelf under the grid: "Continue watching" (system Watch Next rows, with posters and progress) or, when that
 * is empty or off, "Recent apps". Reads on every resume, on a worker thread, and never polls. The view is added to
 * the shelf slot in attach (the slot is reserved INVISIBLE) and shown or hidden once the first read is back.
 * D-pad: Left/Right walk the row, Up returns to the grid, Down stays; Down from the grid's last row lands on the
 * first card.
 */
final class ShelfExtension implements HomeExtension, ResumeAware, TileNavAware, KeyAware {
    private static final String TAG = "TvHome.Shelf";
    private static final int SHELF_DP = 128, LABEL_DP = 24, RECENT_LIMIT = 12;

    private HomeHost host;
    private ForkKeys keys;
    private Context ctx;
    private ViewGroup slot;
    private LinearLayout root;
    private TextView label;
    private RecyclerView row;
    private LinearLayoutManager layoutManager;
    private ShelfAdapter adapter;
    private PosterLoader loader;
    private ExecutorService reader;
    private int generation;
    private boolean attached;
    private ConnectivityManager connectivity;
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override public void attach(HomeHost host, ForkKeys keys) {
        if (!keys.continueWatching && !keys.recentApps) return;
        this.host = host;
        this.keys = keys;
        this.ctx = host.activity();
        float density = host.density();
        slot = host.shelfSlot();
        loader = new PosterLoader(ctx);
        adapter = new ShelfAdapter(loader, density, host.reducedMotion(), this::launch);
        reader = Executors.newSingleThreadExecutor(runnable -> {
            Thread t = new Thread(runnable, "shelf-read");
            t.setDaemon(true);
            return t;
        });

        label = new TextView(ctx);
        label.setTextColor(0xCCFFFFFF);
        label.setTextSize(13);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding((int) (8 * density), 0, 0, 0);

        layoutManager = new LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false);
        row = new RecyclerView(ctx);
        row.setLayoutManager(layoutManager);
        row.setAdapter(adapter);
        row.setItemAnimator(null);
        row.setHasFixedSize(true);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setFocusable(false);
        row.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (LABEL_DP * density)));
        root.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                (int) ((SHELF_DP - LABEL_DP) * density)));
        slot.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (SHELF_DP * density)));
        attached = true;
        watchNetwork();
    }

    /** One retry of missing art whenever a default network appears (Wi-Fi after boot); event-driven, no polling. */
    private void watchNetwork() {
        connectivity = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity == null) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                host.handler().post(() -> {
                    if (attached && shown()) adapter.retryMissing();
                });
            }
        };
        try {
            connectivity.registerDefaultNetworkCallback(networkCallback);
        } catch (RuntimeException e) {
            Log.w(TAG, "network callback not registered: " + e);
            networkCallback = null;
        }
    }

    @Override public void detach() {
        if (!attached) return;
        attached = false;
        generation++;
        if (connectivity != null && networkCallback != null) {
            try {
                connectivity.unregisterNetworkCallback(networkCallback);
            } catch (RuntimeException ignored) {
                // Already unregistered.
            }
            networkCallback = null;
        }
        reader.shutdownNow();
        loader.shutdown();
        slot.removeView(root);
        slot.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        slot.setVisibility(View.GONE);
    }

    @Override public void onResume() {
        if (!attached) return;
        final int mine = ++generation;
        try {
            reader.execute(() -> {
                ShelfPlan plan = load();
                Log.i(TAG, plan.logLine());
                Handler handler = host.handler();
                handler.post(() -> apply(mine, plan));
            });
        } catch (RejectedExecutionException detached) {
            // Home is going away.
        }
    }

    @Override public void onPause() {
        // Nothing is polled or held open between resumes.
    }

    /** Worker thread: the Watch Next rows, then Recent apps only when the plan can use them. */
    private ShelfPlan load() {
        List<ShelfItem> watchNext = java.util.Collections.emptyList();
        if (keys.continueWatching) {
            watchNext = WatchNextFilter.filter(WatchNextReader.read(ctx.getContentResolver()), System.currentTimeMillis());
        }
        List<ShelfItem> recent = java.util.Collections.emptyList();
        if (ShelfPlan.needsRecent(keys.continueWatching, keys.recentApps, watchNext.size())) {
            recent = RecentApps.read(ctx, RECENT_LIMIT);
        }
        return ShelfPlan.choose(keys.continueWatching, keys.recentApps, watchNext, recent);
    }

    private void apply(int mine, ShelfPlan plan) {
        if (!attached || mine != generation) return;
        boolean hadFocus = root.hasFocus();
        int focusedPosition = hadFocus ? focusedPosition() : 0;
        label.setText(plan.source == ShelfPlan.Source.WATCH_NEXT ? R.string.shelf_continue_watching : R.string.shelf_recent_apps);
        boolean changed = adapter.setItems(plan.items);
        if (plan.items.isEmpty()) {
            slot.setVisibility(View.GONE);
            if (hadFocus) host.focusGridTile();
            return;
        }
        slot.setVisibility(View.VISIBLE);
        if (!changed) adapter.retryMissing();
        if (hadFocus && changed) {
            final int target = Math.max(0, Math.min(focusedPosition, plan.items.size() - 1));
            row.post(() -> {
                if (attached) focusCard(target);
            });
        }
    }

    private int focusedPosition() {
        View focus = root.findFocus();
        View itemView = focus == null ? null : row.findContainingItemView(focus);
        int position = itemView == null ? RecyclerView.NO_POSITION : row.getChildAdapterPosition(itemView);
        return position == RecyclerView.NO_POSITION ? 0 : position;
    }

    private boolean focusCard(int position) {
        View card = layoutManager.findViewByPosition(position);
        if (card == null) {
            layoutManager.scrollToPosition(position);
            return row.requestFocus();
        }
        return card.requestFocus();
    }

    private boolean shown() {
        return attached && slot.getVisibility() == View.VISIBLE && adapter.getItemCount() > 0;
    }

    @Override public boolean onTileVerticalKey(int position, int keyCode) {
        if (keyCode != KeyEvent.KEYCODE_DPAD_DOWN || !shown()) return false;
        return focusCard(0);
    }

    @Override public boolean onKey(KeyEvent event) {
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (event.getAction() == KeyEvent.ACTION_DOWN) host.focusGridTile();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                // Nothing lies below the shelf; swallowing keeps focus from wandering off the row.
                return true;
            default:
                return false;
        }
    }

    @Override public View focusScope() {
        return shown() ? root : null;
    }

    private void launch(ShelfItem item) {
        PackageManager pm = ctx.getPackageManager();
        String pkg = item.packageName != null ? item.packageName : LaunchSpec.packageOf(item.intentUri);
        Intent intent = item.kind == ShelfItem.Kind.WATCH_NEXT ? deepLink(pm, item.intentUri, pkg) : null;
        if (intent == null && pkg != null) intent = RecentApps.launchIntent(pm, pkg);
        if (intent == null) {
            Toast.makeText(ctx, R.string.not_available, Toast.LENGTH_SHORT).show();
            return;
        }
        if (pkg != null) LastOpened.record(ctx, pkg);
        host.openIntent(intent);
    }

    /** The publisher's intent_uri as an Intent, or null when it does not parse or nothing on the TV answers it. */
    private static Intent deepLink(PackageManager pm, String intentUri, String pkg) {
        if (!LaunchSpec.looksLaunchable(intentUri)) return null;
        Intent intent;
        try {
            intent = Intent.parseUri(intentUri.trim(), Intent.URI_INTENT_SCHEME);
        } catch (URISyntaxException e) {
            return null;
        }
        // A publisher's text must not hand out URI grants or smuggle a second intent in as a selector.
        intent.setSelector(null);
        intent.setFlags(intent.getFlags() & ~(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION));
        if (intent.getComponent() == null && intent.getPackage() == null && pkg != null) {
            // A bare https link would open in a browser; the row's own app is the one that published it.
            Intent owned = new Intent(intent).setPackage(pkg);
            if (owned.resolveActivity(pm) != null) return owned;
        }
        return intent.resolveActivity(pm) != null ? intent : null;
    }
}
