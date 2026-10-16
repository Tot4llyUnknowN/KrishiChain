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

/** Shows ranked markets. One ViewHolder per visible row; rows are recycled while scrolling. */
public class MarketAdapter extends RecyclerView.Adapter<MarketAdapter.VH> {

    public interface Listener {
        void onMarketClicked(RankedMarket market);
    }

    private final List<RankedMarket> items = new ArrayList<>();
    private int nearestNodeId = -1;
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
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

        b.textRank.setText("#" + (position + 1));
        b.textName.setText(m.option.marketName);
        b.textPrice.setText(c.getString(R.string.item_price, m.option.pricePerKg));
        b.textRoute.setText(c.getString(R.string.item_route, m.km, formatDuration(c, m.minutes)));
        b.textCosts.setText(c.getString(R.string.item_costs,
                m.option.transportCost, m.option.toll, m.option.spoilageFraction * 100.0));
        b.textProfit.setText(c.getString(R.string.item_profit, m.option.netProfit));
        b.textProfit.setTextColor(Color.parseColor(m.option.netProfit >= 0 ? "#2E7D32" : "#C62828"));

        boolean isBest = position == 0;
        boolean isNearest = m.option.nodeId == nearestNodeId;
        if (isBest || isNearest) {
            int text = isBest && isNearest ? R.string.tag_best_nearest
                    : isBest ? R.string.tag_best : R.string.tag_nearest;
            b.textTag.setText(text);
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