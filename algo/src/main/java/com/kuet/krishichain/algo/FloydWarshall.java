package com.kuet.krishichain.algo;

/** Floyd-Warshall dynamic programming: O(V^3). Handles negative arcs, detects negative cycles. */
public final class FloydWarshall {
    private FloydWarshall() { }

    public static AllPairsResult run(Graph g) {
        int n = g.size();
        double[][] dist = new double[n][n];
        int[][] pred = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                dist[i][j] = (i == j) ? 0 : AllPairsResult.INF;
                pred[i][j] = -1;
            }
        }
        for (Edge e : g.allEdges()) {
            if (e.weight < dist[e.from][e.to]) { // keeps the cheapest of parallel arcs
                dist[e.from][e.to] = e.weight;
                pred[e.from][e.to] = e.from;
            }
        }

        // k = "allowed intermediate nodes are 0..k"
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                double dik = dist[i][k];
                if (dik == AllPairsResult.INF) {
                    continue;
                }
                for (int j = 0; j < n; j++) {
                    double dkj = dist[k][j];
                    if (dkj == AllPairsResult.INF) {
                        continue;
                    }
                    double cand = dik + dkj;
                    if (cand < dist[i][j]) {
                        dist[i][j] = cand;
                        pred[i][j] = pred[k][j];
                    }
                }
            }
        }

        for (int i = 0; i < n; i++) {
            if (dist[i][i] < 0) {
                return AllPairsResult.negativeCycle(n);
            }
        }
        return new AllPairsResult(dist, pred, false);
    }
}