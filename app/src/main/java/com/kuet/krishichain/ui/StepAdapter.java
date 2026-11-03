package com.kuet.krishichain.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.kuet.krishichain.R;
import com.kuet.krishichain.databinding.ItemStepBinding;
import com.kuet.krishichain.domain.RouteLeg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Displays visited district waypoints and legs on the optimal shortest path. */
public class StepAdapter extends RecyclerView.Adapter<StepAdapter.VH> {
    private final List<RouteLeg> items = new ArrayList<>();

    public void setItems(List<RouteLeg> legs) {
        items.clear();
        if (legs != null) {
            items.addAll(legs);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemStepBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        RouteLeg leg = items.get(position);
        Context c = holder.binding.getRoot().getContext();
        boolean isFirst = (position == 0);
        boolean isLast = (position == items.size() - 1);

        holder.binding.textStepNo.setText(String.valueOf(position + 1));
        holder.binding.viewTimelineLine.setVisibility(isLast ? View.INVISIBLE : View.VISIBLE);

        holder.binding.textStepTitle.setText(String.format(Locale.US, "%s → %s", leg.fromName, leg.toName));

        if (isLast) {
            holder.binding.textStepTypeBadge.setText("Destination");
            holder.binding.textStepTypeBadge.setBackgroundResource(R.drawable.bg_badge_gold);
        } else if (isFirst) {
            holder.binding.textStepTypeBadge.setText("Origin Leg");
            holder.binding.textStepTypeBadge.setBackgroundResource(R.drawable.bg_badge_ok);
        } else {
            holder.binding.textStepTypeBadge.setText("Crossed District");
            holder.binding.textStepTypeBadge.setBackgroundResource(R.drawable.bg_pill_white);
        }

        String tollText = leg.toll > 0 ? String.format(Locale.US, " · Toll ৳ %,d", Math.round(leg.toll)) : "";
        holder.binding.textStepDetail.setText(String.format(Locale.US, "%.0f km · ~%s%s · ৳ %,d",
                leg.km, Fmt.duration(c, leg.minutes), tollText, Math.round(leg.cost + leg.toll)));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemStepBinding binding;

        VH(ItemStepBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
