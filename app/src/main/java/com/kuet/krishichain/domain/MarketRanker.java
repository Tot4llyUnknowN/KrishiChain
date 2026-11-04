package com.kuet.krishichain.domain;

import com.kuet.krishichain.algo.Dijkstra;
import com.kuet.krishichain.algo.MarketOption;
import com.kuet.krishichain.algo.PathResult;
import com.kuet.krishichain.algo.Sorts;
import com.kuet.krishichain.data.EdgeEntity;
import com.kuet.krishichain.data.PriceEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * For one crop, quantity, base production cost and origin:
 * 1) Dijkstra from the origin gives the cheapest route (transport + toll) to every place.
 * 2) Along each route we add up km, minutes, transport cost and tolls.
 * 3) Inter-district dispatches incur a base dispatch/handling fee + variable weight freight.
 * 4) Spoilage = spoilage%/hour x travel hours (capped at 100%).
 * 5) MarketOption computes net profit; Quick Sort ranks the markets.
 */
public final class MarketRanker {
    /** Fixed dispatch and handling base charge for inter-district freight. */
    public static final double BASE_INTERDISTRICT_DISPATCH_FEE = 200.0;

    private MarketRanker() { }

    public static RankingResult rank(NetworkSnapshot snap, List<PriceEntity> prices,
                                     int originId, double qtyKg, double prodCostPerKg,
                                     double spoilagePctPerHour) {
        Integer originIdx = snap.idToIndex.get(originId);
        if (originIdx == null) {
            throw new IllegalArgumentException("Unknown origin id " + originId);
        }
        PathResult paths = Dijkstra.run(snap.graph, originIdx);
        double tons = qtyKg / 1000.0;

        List<MarketOption> options = new ArrayList<>();
        Map<Integer, RankedMarket> byNode = new HashMap<>();

        for (PriceEntity p : prices) {
            Integer idx = snap.idToIndex.get(p.nodeId);
            if (idx == null || !paths.isReachable(idx)) {
                continue;
            }
            List<Integer> path = paths.pathTo(idx);
            List<Integer> ids = new ArrayList<>();
            for (int vertex : path) {
                ids.add(snap.nodes.get(vertex).id);
            }

            double costPerTon = 0;
            double tollPerTon = 0;
            double km = 0;
            int minutes = 0;
            for (int i = 0; i + 1 < ids.size(); i++) {
                EdgeEntity e = snap.edge(ids.get(i), ids.get(i + 1));
                costPerTon += e.cost;
                tollPerTon += e.toll;
                km += e.km;
                minutes += e.minutes;
            }

            // Variable weight freight + base inter-district dispatch fee (if shipping out of origin)
            double transportCost = (ids.size() > 1)
                    ? (BASE_INTERDISTRICT_DISPATCH_FEE + (costPerTon * tons))
                    : 0.0;
            double tollCost = tollPerTon * tons;

            double spoilage = Math.min(1.0, spoilagePctPerHour / 100.0 * (minutes / 60.0));
            MarketOption option = new MarketOption(p.nodeId, snap.nodes.get(idx).name,
                    p.price, qtyKg, prodCostPerKg, transportCost, tollCost, spoilage);
            options.add(option);
            byNode.put(p.nodeId, new RankedMarket(option, km, minutes, ids));
        }

        Sorts.quickSort(options, Sorts.byNetProfitDesc());

        List<RankedMarket> ranked = new ArrayList<>();
        RankedMarket nearest = null;
        for (MarketOption o : options) {
            RankedMarket r = byNode.get(o.nodeId);
            ranked.add(r);
            if (nearest == null || r.km < nearest.km) {
                nearest = r;
            }
        }
        return new RankingResult(ranked, nearest);
    }

    /** Overload for backwards compatibility with default production cost. */
    public static RankingResult rank(NetworkSnapshot snap, List<PriceEntity> prices,
                                     int originId, double qtyKg, double spoilagePctPerHour) {
        return rank(snap, prices, originId, qtyKg, MarketOption.DEFAULT_PRODUCTION_COST_PER_KG, spoilagePctPerHour);
    }
}
