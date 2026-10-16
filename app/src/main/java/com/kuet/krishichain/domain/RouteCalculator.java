package com.kuet.krishichain.domain;

import com.kuet.krishichain.algo.Dijkstra;
import com.kuet.krishichain.algo.MarketOption;
import com.kuet.krishichain.algo.PathResult;
import com.kuet.krishichain.data.EdgeEntity;
import com.kuet.krishichain.data.NodeEntity;

import java.util.ArrayList;
import java.util.List;

/** Dijkstra from origin to one market, on either the cost graph or the time graph. */
public final class RouteCalculator {
    private RouteCalculator() { }

    public static RouteResult compute(NetworkSnapshot snap, int originId, int destId,
                                      double pricePerKg, double qtyKg,
                                      double spoilagePctPerHour, boolean byTime) {
        Integer o = snap.idToIndex.get(originId);
        Integer d = snap.idToIndex.get(destId);
        if (o == null || d == null) {
            throw new IllegalArgumentException("Unknown place");
        }
        PathResult paths = Dijkstra.run(snap.buildGraph(byTime), o);
        if (!paths.isReachable(d)) {
            throw new IllegalStateException("No road route found");
        }
        List<Integer> path = paths.pathTo(d);
        double tons = qtyKg / 1000.0;

        List<RouteLeg> legs = new ArrayList<>();
        List<String> names = new ArrayList<>();
        double km = 0;
        int minutes = 0;
        double cost = 0;
        double toll = 0;
        for (int i = 0; i < path.size(); i++) {
            NodeEntity n = snap.nodes.get(path.get(i));
            names.add(n.name);
            if (i + 1 < path.size()) {
                NodeEntity next = snap.nodes.get(path.get(i + 1));
                EdgeEntity e = snap.edge(n.id, next.id);
                RouteLeg leg = new RouteLeg(n.name, next.name, e.km, e.minutes,
                        e.cost * tons, e.toll * tons);
                legs.add(leg);
                km += e.km;
                minutes += e.minutes;
                cost += leg.cost;
                toll += leg.toll;
            }
        }
        double spoilage = Math.min(1.0, spoilagePctPerHour / 100.0 * (minutes / 60.0));
        MarketOption option = new MarketOption(destId, snap.nodes.get(d).name,
                pricePerKg, qtyKg, cost, toll, spoilage);
        return new RouteResult(option, legs, names, km, minutes, byTime);
    }
}