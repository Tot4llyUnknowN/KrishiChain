package com.kuet.krishichain.domain;

import com.kuet.krishichain.algo.MarketOption;

import java.util.Comparator;
import java.util.List;

/** A market option plus route details and wholesale demand capacity. */
public final class RankedMarket {
    public final MarketOption option;
    public final double km;
    public final int minutes;
    public final List<Integer> pathNodeIds; // origin ... market (database ids)
    public final double demandTons; // Daily demand absorption capacity (in metric tons)
    public double allocatedTons = 0; // Tons allocated by the supply-demand flow algorithm

    public static final Comparator<RankedMarket> BY_BEST_PROFIT = (a, b) ->
            Double.compare(b.option.netProfit, a.option.netProfit);

    public static final Comparator<RankedMarket> BY_NEAREST_DISTANCE = (a, b) -> {
        int cmp = Double.compare(a.km, b.km);
        if (cmp != 0) return cmp;
        return Integer.compare(a.minutes, b.minutes);
    };

    public RankedMarket(MarketOption option, double km, int minutes, List<Integer> pathNodeIds, double demandTons) {
        this.option = option;
        this.km = km;
        this.minutes = minutes;
        this.pathNodeIds = pathNodeIds;
        this.demandTons = demandTons;
    }

    public RankedMarket(MarketOption option, double km, int minutes, List<Integer> pathNodeIds) {
        this(option, km, minutes, pathNodeIds, 50.0);
    }
}
