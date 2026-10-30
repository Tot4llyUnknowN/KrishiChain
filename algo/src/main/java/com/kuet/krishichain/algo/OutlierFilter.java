package com.kuet.krishichain.algo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Robust statistical filter to reject crowd-sourced pricing outliers.
 * Reports deviating by more than thresholdPercent (default 40%) from
 * the median price of their (crop, nodeId) group are filtered out.
 */
public final class OutlierFilter {

    private final double thresholdPercent; // e.g. 0.40 for 40%

    public OutlierFilter(double thresholdPercent) {
        this.thresholdPercent = thresholdPercent;
    }

    public OutlierFilter() {
        this(0.40); // default 40% deviation allowed
    }

    public static class FilterResult {
        public final List<PriceReport> validReports;
        public final List<PriceReport> rejectedReports;
        public final Map<String, Double> medians;

        public FilterResult(List<PriceReport> valid, List<PriceReport> rejected, Map<String, Double> medians) {
            this.validReports = valid;
            this.rejectedReports = rejected;
            this.medians = medians;
        }
    }

    public FilterResult filter(List<PriceReport> reports) {
        Map<String, List<PriceReport>> groups = new HashMap<>();
        for (PriceReport r : reports) {
            String key = r.crop.toLowerCase() + "@" + r.nodeId;
            List<PriceReport> list = groups.get(key);
            if (list == null) {
                list = new ArrayList<>();
                groups.put(key, list);
            }
            list.add(r);
        }

        List<PriceReport> valid = new ArrayList<>();
        List<PriceReport> rejected = new ArrayList<>();
        Map<String, Double> medians = new HashMap<>();

        for (Map.Entry<String, List<PriceReport>> entry : groups.entrySet()) {
            List<PriceReport> list = entry.getValue();
            if (list.size() < 3) {
                // Not enough samples to detect outliers reliably; keep all
                for (PriceReport r : list) {
                    r.isOutlier = false;
                    valid.add(r);
                }
                double avg = 0;
                for (PriceReport r : list) avg += r.price;
                medians.put(entry.getKey(), list.isEmpty() ? 0 : avg / list.size());
                continue;
            }

            // Compute median
            List<Double> prices = new ArrayList<>();
            for (PriceReport r : list) prices.add(r.price);
            Collections.sort(prices);
            double median;
            int n = prices.size();
            if (n % 2 == 1) {
                median = prices.get(n / 2);
            } else {
                median = (prices.get(n / 2 - 1) + prices.get(n / 2)) / 2.0;
            }
            medians.put(entry.getKey(), median);

            for (PriceReport r : list) {
                double diff = Math.abs(r.price - median);
                if (median > 0 && (diff / median) > thresholdPercent) {
                    r.isOutlier = true;
                    rejected.add(r);
                } else {
                    r.isOutlier = false;
                    valid.add(r);
                }
            }
        }

        return new FilterResult(valid, rejected, medians);
    }
}
