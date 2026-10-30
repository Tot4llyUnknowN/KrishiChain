package com.kuet.krishichain.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.kuet.krishichain.algo.PriceReport;
import com.kuet.krishichain.databinding.ItemPriceReportBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PriceReportAdapter extends RecyclerView.Adapter<PriceReportAdapter.ViewHolder> {

    private final List<PriceReport> reports = new ArrayList<>();
    private final Map<Integer, String> nodeNames;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US);

    public PriceReportAdapter(Map<Integer, String> nodeNames) {
        this.nodeNames = nodeNames;
    }

    public void setReports(List<PriceReport> newReports) {
        reports.clear();
        if (newReports != null) {
            reports.addAll(newReports);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemPriceReportBinding binding = ItemPriceReportBinding.inflate(inflater, parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PriceReport report = reports.get(position);
        String marketName = nodeNames != null && nodeNames.containsKey(report.nodeId)
                ? nodeNames.get(report.nodeId)
                : ("Hub #" + report.nodeId);

        String cropCapitalized = report.crop != null && !report.crop.isEmpty()
                ? Character.toUpperCase(report.crop.charAt(0)) + report.crop.substring(1)
                : "";

        holder.binding.textMarketCrop.setText(marketName + " · " + cropCapitalized);
        holder.binding.textPrice.setText(String.format(Locale.US, "৳%.1f / kg", report.price));

        if (report.isOutlier) {
            holder.binding.textStatusBadge.setText("OUTLIER REJECTED");
            holder.binding.textStatusBadge.setBackgroundColor(Color.parseColor("#FFEBEE"));
            holder.binding.textStatusBadge.setTextColor(Color.parseColor("#C62828"));
        } else {
            holder.binding.textStatusBadge.setText("VALID");
            holder.binding.textStatusBadge.setBackgroundColor(Color.parseColor("#E8F5E9"));
            holder.binding.textStatusBadge.setTextColor(Color.parseColor("#2E7D32"));
        }

        String reporterDisplay;
        if (report.reporterId != null && report.reporterId.contains("@")) {
            reporterDisplay = "By: " + report.reporterId;
        } else if (report.reporterId != null && report.reporterId.startsWith("usr_")) {
            reporterDisplay = "By: Guest (" + report.reporterId + ")";
        } else if (report.reporterId != null && report.reporterId.length() > 6) {
            reporterDisplay = "By: Guest (" + report.reporterId.substring(0, 6) + "...)";
        } else {
            reporterDisplay = "By: Guest";
        }

        String formattedDate = dateFormat.format(new Date(report.dateMillis));
        holder.binding.textReportMeta.setText(String.format("Reported %s · %s", formattedDate, reporterDisplay));
    }

    @Override
    public int getItemCount() {
        return reports.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemPriceReportBinding binding;

        ViewHolder(ItemPriceReportBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
