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
 * For one crop, quantity and origin:
 * 1) Dijkstra from the origin gives the cheapest route (transport + toll) to every place.
 * 2) Along each route we add up km, minutes, transport cost and tolls.
 * 3) Spoilage = spoilage%/hour x travel hours (capped at 100%).
 * 4) MarketOption computes net profit; Quick Sort ranks the markets.
 */
public final class MarketRanker {
    private MarketRanker() { }

    public static RankingResult rank(NetworkSnapshot snap, List<PriceEntity> prices,
                                     int originId, double qtyKg, double spoilagePctPerHour) {
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

            double spoilage = Math.min(1.0, spoilagePctPerHour / 100.0 * (minutes / 60.0));
            MarketOption option = new MarketOption(p.nodeId, snap.nodes.get(idx).name,
                    p.price, qtyKg, costPerTon * tons, tollPerTon * tons, spoilage);
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
}