package com.kuet.krishichain.algo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Random directed graphs for tests and the benchmark screen. */
public final class GraphGenerator {
    private GraphGenerator() { }

    /**
     * n nodes, m distinct arcs (no self loops). Weights are integers.
     * If allowNegative is true, weights are shifted by random potentials, so some arcs are negative
     * but a negative cycle is impossible (every cycle's potentials cancel out).
     */
    public static Graph random(int n, int m, boolean allowNegative, long seed) {
        Random rnd = new Random(seed);
        int maxEdges = n * (n - 1);
        if (m > maxEdges) {
            m = maxEdges;
        }
        int[] potential = new int[n];
        if (allowNegative) {
            for (int i = 0; i < n; i++) {
                potential[i] = rnd.nextInt(16);
            }
        }
        List<Integer> pairs = new ArrayList<>();
        for (int u = 0; u < n; u++) {
            for (int v = 0; v < n; v++) {
                if (u != v) {
                    pairs.add(u * n + v);
                }
            }
        }
        Collections.shuffle(pairs, rnd);

        Graph g = new Graph(n);
        for (int i = 0; i < m; i++) {
            int code = pairs.get(i);
            int u = code / n;
            int v = code % n;
            int w = 1 + rnd.nextInt(20);
            if (allowNegative) {
                w += potential[u] - potential[v];
            }
            g.addEdge(u, v, w);
        }
        return g;
    }
}