package com.kuet.krishichain.algo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Bellman-Ford: O(V * E). Handles negative arcs and detects a negative cycle
 * reachable from the source.
 */
public final class BellmanFord {
    private static final double EPS = 1e-9;

    private BellmanFord() { }

    public static PathResult run(Graph g, int source) {
        return run(g, source, true);
    }

    /** record = false skips the relax/update trace (used by Johnson and benchmarks). */
    public static PathResult run(Graph g, int source, boolean record) {
        int n = g.size();
        double[] dist = new double[n];
        int[] prev = new int[n];
        Arrays.fill(dist, PathResult.INF);
        Arrays.fill(prev, -1);
        dist[source] = 0;
        List<Step> steps = new ArrayList<>();
        List<Edge> edges = g.allEdges();

        int x = -1;
        for (int iter = 0; iter < n; iter++) {
            x = -1;
            for (Edge e : edges) {
                if (dist[e.from] == PathResult.INF) {
                    continue;
                }
                double cand = dist[e.from] + e.weight;
                if (record) {
                    steps.add(Step.relax(e.from, e.to, cand));
                }
                if (cand < dist[e.to] - EPS) {
                    dist[e.to] = cand;
                    prev[e.to] = e.from;
                    x = e.to;
                    if (record) {
                        steps.add(Step.update(e.from, e.to, cand));
                    }
                }
            }
            if (x == -1) {
                break; // no change in this pass: finished early
            }
        }

        if (x != -1) {
            // Still relaxing on pass n => a negative cycle is reachable.
            for (int i = 0; i < n; i++) {
                x = prev[x]; // walk back until surely inside the cycle
            }
            List<Integer> cycle = new ArrayList<>();
            int cur = x;
            do {
                cycle.add(cur);
                cur = prev[cur];
            } while (cur != x);
            Collections.reverse(cycle);
            steps.add(Step.negativeCycle(x));
            steps.add(Step.finish());
            return new PathResult(dist, prev, steps, true, cycle);
        }

        if (record) {
            steps.add(Step.finish());
        }
        return new PathResult(dist, prev, steps, false, new ArrayList<Integer>());
    }
}