package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Outline;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** The shelf's cards: poster (or app banner), title, progress bar and app badge. */
final class ShelfAdapter extends RecyclerView.Adapter<ShelfAdapter.Holder> {
    interface OnItem {
        void onClick(ShelfItem item);
    }

    private static final int CARD_W_DP = 142, CARD_H_DP = 80, BADGE_DP = 20, CORNER_DP = 12;
    /** Decoded poster ceiling (see PosterLoader.sampleSize): enough for a 2x TV, never more than 320x180. */
    private static final int ART_MAX_W = 320, ART_MAX_H = 180;

    private final PosterLoader loader;
    private final OnItem onItem;
    private final float density;
    private final boolean reducedMotion;
    private List<ShelfItem> items = new ArrayList<>();

    ShelfAdapter(PosterLoader loader, float density, boolean reducedMotion, OnItem onItem) {
        this.loader = loader;
        this.density = density;
        this.reducedMotion = reducedMotion;
        this.onItem = onItem;
    }

    /** Replaces the cards; false (and no change, so focus stays put) when they are the same list. */
    boolean setItems(List<ShelfItem> next) {
        if (items.equals(next)) return false;
        int before = items.size();
        items = new ArrayList<>(next);
        int common = Math.min(before, items.size());
        if (common > 0) notifyItemRangeChanged(0, common);
        if (items.size() > before) notifyItemRangeInserted(before, items.size() - before);
        if (items.size() < before) notifyItemRangeRemoved(items.size(), before - items.size());
        return true;
    }

    @Override public int getItemCount() {
        return items.size();
    }

    @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.shelf_item, parent, false);
        return new Holder(view);
    }

    @Override public void onBindViewHolder(Holder h, int position) {
        ShelfItem item = items.get(position);
        h.cancelLoads();
        h.title.setText(item.title);
        h.card.setOnClickListener(v -> onItem.onClick(item));
        h.poster.setImageDrawable(null);
        h.badge.setImageDrawable(null);
        h.badge.setVisibility(View.GONE);
        if (item.progress > 0) {
            h.progress.setProgress(item.progress);
            h.progress.setVisibility(View.VISIBLE);
        } else {
            h.progress.setVisibility(View.GONE);
        }
        if (item.kind == ShelfItem.Kind.APP) {
            h.art = loader.loadAppBanner(item.packageName, (int) (CARD_W_DP * density), (int) (CARD_H_DP * density),
                    bitmap -> h.poster.setImageBitmap(bitmap));
            return;
        }
        if (PosterLoader.isSupportedUri(item.posterUri)) {
            h.art = loader.loadPoster(item.posterUri, ART_MAX_W, ART_MAX_H, bitmap -> h.poster.setImageBitmap(bitmap));
        }
        if (item.packageName != null) {
            h.badgeLoad = loader.loadAppIcon(item.packageName, (int) (BADGE_DP * density), bitmap -> {
                h.badge.setImageBitmap(bitmap);
                h.badge.setVisibility(View.VISIBLE);
            });
        }
    }

    @Override public void onViewRecycled(Holder h) {
        h.cancelLoads();
        h.poster.setImageDrawable(null);
        h.badge.setImageDrawable(null);
    }

    final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView poster, badge;
        final TextView title;
        final ProgressBar progress;
        PosterLoader.Handle art, badgeLoad;

        Holder(View itemView) {
            super(itemView);
            card = itemView;
            poster = itemView.findViewById(R.id.shelf_poster);
            badge = itemView.findViewById(R.id.shelf_badge);
            title = itemView.findViewById(R.id.shelf_title);
            progress = itemView.findViewById(R.id.shelf_progress);
            final float radius = CORNER_DP * density;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            card.setClipToOutline(true);
            card.setOnFocusChangeListener((v, focused) -> {
                float scale = focused ? 1.08f : 1f;
                if (reducedMotion) {
                    v.setScaleX(scale);
                    v.setScaleY(scale);
                } else {
                    v.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
                }
                v.setTranslationZ(focused ? 8 * density : 0);
            });
        }

        void cancelLoads() {
            if (art != null) art.cancel();
            if (badgeLoad != null) badgeLoad.cancel();
            art = null;
            badgeLoad = null;
        }
    }
}
