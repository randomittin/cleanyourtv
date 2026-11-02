package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Sidebar layout: a glass rail at the left edge takes over the header's button row. Active only when the layout
 * pick is "sidebar". Every rail button delegates to the Home view it replaces (performClick), so the Inputs,
 * Look and Settings behaviour stays in one place.
 *
 * <p>D-pad: this is {@link KeyAware} with {@link #focusScope()} = Home's root, so it sees every D-pad key while it
 * is attached (the contract only offers keys inside the scope, and the grid is outside any view this class owns).
 * It acts on exactly three cases and returns false for everything else, so Home's own navigation is untouched:
 * Left from a first-column tile and Up from the top row go to the rail (Home swallows Left at a row edge and has
 * nothing above once the top buttons are gone); on the rail Up/Down walk the items (clamped, no wrap), Right
 * returns to the grid, Left stays put. Chosen over nextFocusLeftId on tiles because tiles are recycled views.
 */
final class SidebarExtension implements HomeExtension, KeyAware, ResumeAware {
    private static final ComponentName ASSISTANT =
            new ComponentName("io.github.randomittin.voiceassistant", "io.github.randomittin.voiceassistant.AssistActivity");

    private HomeHost host;
    private View rail;
    private final List<View> buttons = new ArrayList<>();
    private int topBarVisibilityBefore = View.VISIBLE;
    private boolean topBarHidden;
    private boolean insetApplied;

    @Override
    public void attach(HomeHost host, ForkKeys keys) {
        if (!SidebarLayout.active(keys)) return;
        this.host = host;
        try {
            View inputs = host.activity().findViewById(R.id.btnInputs);
            View settings = host.activity().findViewById(R.id.btnSettings);
            if (settings == null) return;
            boolean assistant = assistantInstalled(host);
            List<Integer> items = SidebarLayout.items(inputs != null, host.lookButton() != null, assistant);

            ViewGroup root = host.root();
            LayoutInflater inflater = LayoutInflater.from(host.activity());
            ViewGroup railView = (ViewGroup) inflater.inflate(R.layout.sidebar_rail, root, false);
            for (int item : items) {
                ImageView button = (ImageView) inflater.inflate(R.layout.sidebar_item, railView, false);
                bind(button, item);
                railView.addView(button);
                buttons.add(button);
            }
            root.addView(railView);
            rail = railView;

            ViewGroup bar = host.topButtons();
            topBarVisibilityBefore = bar.getVisibility();
            bar.setVisibility(View.GONE);
            topBarHidden = true;
            host.setContentInsetStart(SidebarLayout.insetPx(host.density()));
            insetApplied = true;
            syncVisibility();
        } catch (Throwable t) {
            detach();
            throw t;
        }
    }

    @Override
    public void detach() {
        if (host == null) return;
        HomeHost h = host;
        host = null;
        try {
            if (rail != null && rail.getParent() instanceof ViewGroup) ((ViewGroup) rail.getParent()).removeView(rail);
        } finally {
            rail = null;
            buttons.clear();
            if (topBarHidden) {
                topBarHidden = false;
                h.topButtons().setVisibility(topBarVisibilityBefore);
            }
            if (insetApplied) {
                insetApplied = false;
                h.setContentInsetStart(0);
            }
        }
    }

    @Override
    public void onResume() {
        syncVisibility();
    }

    @Override
    public void onPause() {}

    @Override
    public View focusScope() {
        return rail == null || host == null ? null : host.root();
    }

    @Override
    public boolean onKey(KeyEvent event) {
        if (rail == null || host == null) return false;
        int keyCode = event.getKeyCode();
        View focus = host.activity().getCurrentFocus();
        if (focus == null) return false;
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        int index = indexOfRailButton(focus);
        if (index >= 0) return onRailKey(keyCode, down, index);
        if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_UP) return false;
        if (!gridTileEntersRail(focus, keyCode)) return false;
        // Swallow the ACTION_UP too, so Home never sees half a key press.
        if (down) {
            View first = firstVisibleButton();
            if (first == null) return false;
            first.requestFocus();
        }
        return true;
    }

    private boolean onRailKey(int keyCode, boolean down, int index) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) {
                    int target = nextVisible(index, keyCode);
                    if (target != index) buttons.get(target).requestFocus();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) host.focusGridTile();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return true;
            default:
                return false;
        }
    }

    private int nextVisible(int from, int keyCode) {
        int step = keyCode == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
        for (int i = from + step; i >= 0 && i < buttons.size(); i += step) {
            if (buttons.get(i).getVisibility() == View.VISIBLE) return i;
        }
        return from;
    }

    private View firstVisibleButton() {
        for (View b : buttons) if (b.getVisibility() == View.VISIBLE) return b;
        return null;
    }

    private int indexOfRailButton(View focus) {
        for (int i = 0; i < buttons.size(); i++) if (buttons.get(i) == focus) return i;
        return -1;
    }

    /** Whether {@code focus} is an app-grid tile whose {@code keyCode} should enter the rail. */
    private boolean gridTileEntersRail(View focus, int keyCode) {
        View grid = host.gridView();
        if (!(grid instanceof RecyclerView)) return false;
        RecyclerView recycler = (RecyclerView) grid;
        View tile = focus;
        while (tile != null && tile.getParent() != recycler) {
            android.view.ViewParent parent = tile.getParent();
            tile = parent instanceof View ? (View) parent : null;
        }
        if (tile == null || !(recycler.getLayoutManager() instanceof GridLayoutManager)) return false;
        GridLayoutManager lm = (GridLayoutManager) recycler.getLayoutManager();
        int position = recycler.getChildAdapterPosition(tile);
        if (position < 0) return false;
        int spanIndex = ((GridLayoutManager.LayoutParams) tile.getLayoutParams()).getSpanIndex();
        return SidebarLayout.gridKeyEntersRail(keyCode, lm.getOrientation() == RecyclerView.VERTICAL,
                spanIndex, position, lm.getSpanCount());
    }

    private void bind(ImageView button, int item) {
        switch (item) {
            case SidebarLayout.HOME:
                button.setImageResource(R.drawable.ic_side_home);
                button.setContentDescription(host.activity().getString(R.string.side_home));
                button.setOnClickListener(v -> host.focusGridTile());
                break;
            case SidebarLayout.INPUTS:
                button.setImageResource(R.drawable.ic_side_inputs);
                button.setContentDescription(host.activity().getString(R.string.side_inputs));
                button.setOnClickListener(v -> delegate(R.id.btnInputs));
                break;
            case SidebarLayout.LOOK:
                button.setImageResource(R.drawable.ic_side_look);
                button.setContentDescription(host.activity().getString(R.string.side_look));
                button.setOnClickListener(v -> {
                    View look = host.lookButton();
                    if (look != null) look.performClick();
                });
                break;
            case SidebarLayout.ASSISTANT:
                button.setImageResource(R.drawable.ic_side_search);
                button.setContentDescription(host.activity().getString(R.string.side_assistant));
                button.setOnClickListener(v -> host.openIntent(new Intent().setComponent(ASSISTANT)));
                break;
            default:
                button.setImageResource(R.drawable.ic_side_settings);
                button.setContentDescription(host.activity().getString(R.string.side_settings));
                button.setOnClickListener(v -> delegate(R.id.btnSettings));
                break;
        }
        button.setTag(item);
    }

    private void delegate(int id) {
        View target = host.activity().findViewById(id);
        if (target != null) target.performClick();
    }

    /** Inputs follows the pill it stands for (Home hides that when there is nothing to list); Look appears when the pill does. */
    private void syncVisibility() {
        if (host == null) return;
        for (View b : buttons) {
            int item = (Integer) b.getTag();
            if (item == SidebarLayout.INPUTS) {
                View inputs = host.activity().findViewById(R.id.btnInputs);
                b.setVisibility(inputs != null && inputs.getVisibility() != View.GONE ? View.VISIBLE : View.GONE);
            } else if (item == SidebarLayout.LOOK) {
                b.setVisibility(host.lookButton() != null ? View.VISIBLE : View.GONE);
            }
        }
    }

    private static boolean assistantInstalled(HomeHost host) {
        try {
            host.activity().getPackageManager().getActivityInfo(ASSISTANT, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
