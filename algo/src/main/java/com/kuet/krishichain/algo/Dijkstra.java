package com.kuet.krishichain.algo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** Dijkstra with a binary heap: O((V + E) log V). Requires non-negative weights. */
public final class Dijkstra {
    private Dijkstra() { }

    public static PathResult run(Graph g, int source) {
        return run(g, source, true);
    }

    /** record = false skips the animation trace (used by benchmarks and Johnson). */
    public static PathResult run(Graph g, int source, boolean record) {
        for (Edge e : g.allEdges()) {
            if (e.weight < 0) {
                throw new IllegalArgumentException(
                        "Dijkstra needs non-negative weights; use Bellman-Ford or Johnson.");
            }
        }
        int n = g.size();
        double[] dist = new double[n];
        int[] prev = new int[n];
        boolean[] done = new boolean[n];
        Arrays.fill(dist, PathResult.INF);
        Arrays.fill(prev, -1);
        List<Step> steps = new ArrayList<>();

        PriorityQueue<double[]> pq = new PriorityQueue<>(new Comparator<double[]>() {
            @Override
            public int compare(double[] a, double[] b) {
                return Double.compare(a[0], b[0]);
            }
        });

        dist[source] = 0;
        pq.add(new double[]{0, source});

        while (!pq.isEmpty()) {
            double[] top = pq.poll();
            int u = (int) top[1];
            if (done[u]) {
                continue; // stale queue entry
            }
            done[u] = true;
            if (record) {
                steps.add(Step.visit(u, dist[u]));
            }

            for (Edge e : g.edgesFrom(u)) {
                double cand = dist[u] + e.weight;
                if (record) {
                    steps.add(Step.relax(u, e.to, cand));
                }
                if (cand < dist[e.to]) {
                    dist[e.to] = cand;
                    prev[e.to] = u;
                    if (record) {
                        steps.add(Step.update(u, e.to, cand));
                    }
                    pq.add(new double[]{cand, e.to});
                }
            }
        }
        if (record) {
            steps.add(Step.finish());
        }
        return new PathResult(dist, prev, steps, false, new ArrayList<Integer>());
    }
}