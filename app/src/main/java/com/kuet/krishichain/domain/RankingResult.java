package com.kuet.krishichain.domain;

import java.util.List;

public final class RankingResult {
    public final List<RankedMarket> ranked; // best first
    public final RankedMarket nearest;      // closest by road km (may be the origin itself)
    public final SupplyDemandAllocation allocation; // Supply-demand flow allocation

    public RankingResult(List<RankedMarket> ranked, RankedMarket nearest, SupplyDemandAllocation allocation) {
        this.ranked = ranked;
        this.nearest = nearest;
        this.allocation = allocation;
    }

    public RankingResult(List<RankedMarket> ranked, RankedMarket nearest) {
        this(ranked, nearest, null);
    }

    public RankedMarket best() {
        return ranked.isEmpty() ? null : ranked.get(0);
    }
}
