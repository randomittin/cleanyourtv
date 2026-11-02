package com.gathin.tvhome;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;

/** What an extension may use of Home. MainActivity implements it. */
interface HomeHost {
    Activity activity();

    /** Home's root FrameLayout (R.id.root). */
    ViewGroup root();

    /** Top-centre FrameLayout over the header (R.id.npSlot, GONE until used): the Now Playing card, 520dp x 96dp. */
    ViewGroup npSlot();

    /** Full-width FrameLayout under the grid (R.id.shelfSlot, 128dp while reserved): the shelf row. */
    ViewGroup shelfSlot();

    /** The header's button row (R.id.topButtons). */
    ViewGroup topButtons();

    /** The Look pill (R.id.btnLook) if an extension added one, else null. */
    View lookButton();

    /** The app grid (R.id.grid). */
    View gridView();

    float density();

    boolean reducedMotion();

    Handler handler();

    /**
     * The existing GuardRevival notification-listener component. MediaSessionManager.getActiveSessions needs a
     * listener component the user has enabled (cmd notification allow_listener); no new listener is declared.
     */
    ComponentName mediaSessionListener();

    /** Bumps the settings revision and recreates Home, the path Launcher settings use. */
    void recreateHome();

    /** Focus back on the grid: the last focused tile, else the first. */
    void focusGridTile();

    void openIntent(Intent intent);

    /** Drops a glass panel under {@code anchor}; Back dismisses it. */
    void showGlassPanel(View panel, View anchor, int widthDp);

    /** Left padding in px of the content column under the header (0 restores the stock 56dp). */
    void setContentInsetStart(int px);
}
