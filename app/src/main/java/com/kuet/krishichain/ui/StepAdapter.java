package com.kuet.krishichain.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.kuet.krishichain.R;
import com.kuet.krishichain.databinding.ItemStepBinding;
import com.kuet.krishichain.domain.RouteLeg;

import java.util.ArrayList;
import java.util.List;

/** One row per road segment of the route. */
public class StepAdapter extends RecyclerView.Adapter<StepAdapter.VH> {
    private final List<RouteLeg> items = new ArrayList<>();

    public void setItems(List<RouteLeg> legs) {
        items.clear();
        items.addAll(legs);
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
        holder.binding.textStepNo.setText(String.valueOf(position + 1));
        holder.binding.textStepTitle.setText(
                c.getString(R.string.step_title, leg.fromName, leg.toName));
        holder.binding.textStepDetail.setText(c.getString(R.string.step_detail,
                leg.km, Fmt.duration(c, leg.minutes), leg.cost + leg.toll));
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