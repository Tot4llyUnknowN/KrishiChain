package com.kuet.krishichain.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Uses Network Flow concepts (Capacitated Market Demand Sinks) to distribute supply.
 * When farmer supply exceeds the top market's demand capacity (e.g., 200 tons supply vs 100 tons demand in Dhaka),
 * the algorithm fills Market 1 up to its saturation cap and diverts the excess flow along the network
 * to the 2nd best reachable market!
 */
public final class SupplyDemandFlowRouter {

    private SupplyDemandFlowRouter() {}

    public static SupplyDemandAllocation allocate(List<RankedMarket> rankedMarkets, double supplyKg) {
        if (rankedMarkets == null || rankedMarkets.isEmpty() || supplyKg <= 0) {
            return new SupplyDemandAllocation(new ArrayList<>(), 0, 0, false);
        }

        double supplyTons = supplyKg / 1000.0;
        double remainingSupplyTons = supplyTons;

        List<SupplyDemandAllocation.Item> items = new ArrayList<>();
        double combinedProfit = 0;

        for (RankedMarket rm : rankedMarkets) {
            if (remainingSupplyTons <= 0.0001) {
                rm.allocatedTons = 0;
                continue;
            }

            double marketDemandTons = rm.demandTons;
            double takeTons = Math.min(remainingSupplyTons, marketDemandTons);
            boolean saturated = (takeTons >= marketDemandTons - 0.0001);

            // Calculate profit for this portion
            double profitPerKg = (rm.option.quantityKg > 0)
                    ? (rm.option.netProfit / rm.option.quantityKg)
                    : 0.0;
            double portionProfit = takeTons * 1000.0 * profitPerKg;

            rm.allocatedTons = takeTons;
            items.add(new SupplyDemandAllocation.Item(rm, takeTons, marketDemandTons, portionProfit, saturated));
            combinedProfit += portionProfit;
            remainingSupplyTons -= takeTons;
        }

        double totalAllocatedTons = supplyTons - Math.max(0.0, remainingSupplyTons);
        boolean isSplit = items.size() > 1;

        return new SupplyDemandAllocation(items, totalAllocatedTons, combinedProfit, isSplit);
    }
}
