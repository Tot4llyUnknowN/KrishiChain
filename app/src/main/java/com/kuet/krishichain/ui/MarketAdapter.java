package com.kuet.krishichain.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.kuet.krishichain.R;
import com.kuet.krishichain.databinding.ItemMarketBinding;
import com.kuet.krishichain.domain.RankedMarket;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shows ranked markets matching HTML mockup Screen 3. */
public class MarketAdapter extends RecyclerView.Adapter<MarketAdapter.VH> {

    public interface Listener {
        void onMarketClicked(RankedMarket market);
    }

    private final List<RankedMarket> items = new ArrayList<>();
    private int nearestNodeId = -1;
    private double qtyKg = 1000.0;
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setQuantity(double qty) {
        if (qty > 0) {
            this.qtyKg = qty;
        }
    }

    public void setItems(List<RankedMarket> newItems, int nearestNodeId) {
        items.clear();
        items.addAll(newItems);
        this.nearestNodeId = nearestNodeId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMarketBinding b = ItemMarketBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        final RankedMarket m = items.get(position);
        Context c = holder.binding.getRoot().getContext();
        ItemMarketBinding b = holder.binding;

        // Rank badge: #1 is Amber circle, others are pri2 circle
        if (position == 0) {
            b.textRank.setBackgroundResource(R.drawable.bg_badge_rank_1);
            b.textRank.setTextColor(Color.WHITE);
        } else {
            b.textRank.setBackgroundResource(R.drawable.bg_badge_rank_n);
            b.textRank.setTextColor(Color.parseColor("#1B7A2F"));
        }
        b.textRank.setText(String.valueOf(position + 1));

        b.textName.setText(m.option.marketName);
        b.textRoute.setText(String.format(Locale.US, "📍 %.0f km · %s · %.0ft demand",
                m.km, formatDuration(c, m.minutes), m.demandTons));

        if (m.allocatedTons > 0) {
            b.textCosts.setVisibility(View.VISIBLE);
            b.textCosts.setText(String.format(Locale.US, "Flow: %.0ft allocated%s",
                    m.allocatedTons, (m.allocatedTons >= m.demandTons - 0.001) ? " (Full)" : ""));
            b.textCosts.setTextColor(Color.parseColor("#1B7A2F"));
        } else {
            b.textCosts.setVisibility(View.GONE);
        }

        // Profit formatted cleanly matching mockup ৳42.50
        double perKg = qtyKg > 0 ? (m.option.netProfit / qtyKg) : m.option.netProfit;
        b.textProfit.setText(String.format(Locale.US, "৳%.2f", perKg));
        b.textProfit.setTextColor(Color.parseColor(m.option.netProfit >= 0 ? "#1B7A2F" : "#D32F2F"));

        boolean isBest = position == 0;
        boolean isNearest = m.option.nodeId == nearestNodeId;
        if (isBest || isNearest) {
            int text = isBest && isNearest ? R.string.tag_best_nearest
                    : isBest ? R.string.tag_best : R.string.tag_nearest;
            b.textTag.setText(text);
            b.textTag.setBackgroundResource(isBest ? R.drawable.bg_tag_best : R.drawable.bg_tag_nearest);
            b.textTag.setVisibility(View.VISIBLE);
        } else {
            b.textTag.setVisibility(View.GONE);
        }

        b.getRoot().setOnClickListener(v -> {
            if (listener != null) {
                listener.onMarketClicked(m);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static String formatDuration(Context c, int minutes) {
        if (minutes >= 60) {
            return c.getString(R.string.duration_hm, minutes / 60, minutes % 60);
        }
        return c.getString(R.string.duration_min, minutes);
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemMarketBinding binding;

        VH(ItemMarketBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
