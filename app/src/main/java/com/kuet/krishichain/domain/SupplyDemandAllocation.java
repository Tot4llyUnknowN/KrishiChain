package com.kuet.krishichain.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Result of the Capacitated Supply-Demand Flow algorithm.
 * If total farmer supply exceeds Market #1 demand, excess flow routes to the 2nd best reachable market.
 */
public final class SupplyDemandAllocation {

    public static final class Item {
        public final RankedMarket market;
        public final double allocatedTons;
        public final double demandTons;
        public final double allocatedProfit;
        public final boolean isSaturated;

        public Item(RankedMarket market, double allocatedTons, double demandTons,
                    double allocatedProfit, boolean isSaturated) {
            this.market = market;
            this.allocatedTons = allocatedTons;
            this.demandTons = demandTons;
            this.allocatedProfit = allocatedProfit;
            this.isSaturated = isSaturated;
        }
    }

    public final List<Item> items;
    public final double totalAllocatedTons;
    public final double combinedNetProfit;
    public final boolean isSplit;

    public SupplyDemandAllocation(List<Item> items, double totalAllocatedTons,
                                  double combinedNetProfit, boolean isSplit) {
        this.items = (items != null) ? items : new ArrayList<>();
        this.totalAllocatedTons = totalAllocatedTons;
        this.combinedNetProfit = combinedNetProfit;
        this.isSplit = isSplit;
    }

    /**
     * Short summary for cards and banners (e.g. "100t → Dhaka (Full) + 100t → Gazipur").
     */
    public String getSummary() {
        if (items.isEmpty()) return "No markets allocated";
        if (items.size() == 1) {
            Item it = items.get(0);
            return String.format(Locale.US, "%.0ft → %s", it.allocatedTons, it.market.option.marketName);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(" + ");
            Item it = items.get(i);
            sb.append(String.format(Locale.US, "%.0ft → %s", it.allocatedTons, it.market.option.marketName));
            if (it.isSaturated) sb.append(" (Full)");
        }
        return sb.toString();
    }
}
