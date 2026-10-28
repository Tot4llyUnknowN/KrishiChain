package com.kuet.krishichain.domain;

import com.kuet.krishichain.algo.Graph;
import com.kuet.krishichain.data.EdgeEntity;
import com.kuet.krishichain.data.NetworkDao;
import com.kuet.krishichain.data.NodeEntity;
import com.kuet.krishichain.data.WeatherEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Database rows turned into pure-Java Graphs from the algo module.
 * Node ids in the database are 1..40; graph vertices are 0..39 (idToIndex converts).
 * 'graph' is weighted by transport cost + toll per ton. buildGraph(true) is weighted by minutes.
 * Both are never negative, so Dijkstra is valid.
 * Weather: each road's minutes are multiplied by min(MAX_FACTOR, 1 + RAIN_K * rainMm)
 * (heuristic ASSUMPTION), where rainMm is the average rain at the road's two ends.
 */
public final class NetworkSnapshot {
    public static final double RAIN_K = 0.05;
    public static final double MAX_FACTOR = 2.0;

    public final List<NodeEntity> nodes;
    public final Map<Integer, Integer> idToIndex = new HashMap<>();
    public final Graph graph;
    public final boolean weatherAdjusted;
    public final double maxRainMm;
    private final List<EdgeEntity> edges;
    private final Map<Long, EdgeEntity> edgeByPair = new HashMap<>();

    private NetworkSnapshot(List<NodeEntity> nodes, List<EdgeEntity> edges,
                            List<WeatherEntity> weather) {
        this.nodes = nodes;
        this.edges = edges;
        for (int i = 0; i < nodes.size(); i++) {
            idToIndex.put(nodes.get(i).id, i);
        }

        Map<Integer, Double> rain = new HashMap<>();
        double maxRain = 0;
        for (WeatherEntity w : weather) {
            rain.put(w.nodeId, w.rainMm);
            maxRain = Math.max(maxRain, w.rainMm);
        }
        this.weatherAdjusted = !weather.isEmpty();
        this.maxRainMm = maxRain;

        for (EdgeEntity e : edges) {
            if (weatherAdjusted) {
                Double a = rain.get(e.fromId);
                Double b = rain.get(e.toId);
                double r = ((a == null ? 0 : a) + (b == null ? 0 : b)) / 2.0;
                double factor = Math.min(MAX_FACTOR, 1 + RAIN_K * r);
                e.minutes = (int) Math.round(e.minutes * factor); // in memory only, not saved
            }
            edgeByPair.put(key(e.fromId, e.toId), e);
        }
        this.graph = buildGraph(false);
    }

    /** Call on a background thread. */
    public static NetworkSnapshot load(NetworkDao dao) {
        return new NetworkSnapshot(dao.getAllNodes(), dao.getAllEdges(), dao.getWeather());
    }

    public List<EdgeEntity> edges() {
        return edges;
    }

    /** byTime = false: weight is cost + toll (BDT per ton). byTime = true: weight is minutes. */
    public Graph buildGraph(boolean byTime) {
        Graph g = new Graph(nodes.size());
        for (EdgeEntity e : edges) {
            Integer u = idToIndex.get(e.fromId);
            Integer v = idToIndex.get(e.toId);
            if (u == null || v == null) {
                continue;
            }
            g.addEdge(u, v, byTime ? e.minutes : e.cost + e.toll);
        }
        return g;
    }

    /**
     * Net-cost graph for Bellman-Ford: weight = netCost = cost + toll - backhaulCredit.
     * Some arcs are negative. If 'broken' is true, the first road (both directions) gets
     * a huge fake credit, which creates a negative cycle on purpose (demo only).
     */
    public Graph buildNetGraph(boolean broken) {
        Graph g = new Graph(nodes.size());
        EdgeEntity first = edges.isEmpty() ? null : edges.get(0);
        for (EdgeEntity e : edges) {
            Integer u = idToIndex.get(e.fromId);
            Integer v = idToIndex.get(e.toId);
            if (u == null || v == null) {
                continue;
            }
            boolean isFirstRoad = broken && first != null
                    && key(e.fromId, e.toId) == key(first.fromId, first.toId)
                    || broken && first != null
                    && key(e.fromId, e.toId) == key(first.toId, first.fromId);
            g.addEdge(u, v, isFirstRoad ? -1000 : e.netCost);
        }
        return g;
    }

    /** One undirected road per pair (weight = cost + toll), for the spanning tree algorithms. */
    public Graph buildUndirectedOnce() {
        Graph g = new Graph(nodes.size());
        Set<Long> seen = new HashSet<>();
        for (EdgeEntity e : edges) {
            Integer u = idToIndex.get(e.fromId);
            Integer v = idToIndex.get(e.toId);
            if (u == null || v == null) {
                continue;
            }
            long k = Math.min(e.fromId, e.toId) * 1000L + Math.max(e.fromId, e.toId);
            if (seen.add(k)) {
                g.addEdge(u, v, e.cost + e.toll);
            }
        }
        return g;
    }

    public NodeEntity nodeById(int id) {
        Integer idx = idToIndex.get(id);
        return idx == null ? null : nodes.get(idx);
    }

    public EdgeEntity edge(int fromId, int toId) {
        return edgeByPair.get(key(fromId, toId));
    }

    private static long key(int fromId, int toId) {
        return fromId * 1000L + toId;
    }
}