package com.kuet.krishichain.algo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Output of an all-pairs shortest path algorithm. pred[i][j] = node before j on the path i -> j. */
public final class AllPairsResult {
    public static final double INF = Double.POSITIVE_INFINITY;

    public final double[][] dist;
    public final int[][] pred;
    public final boolean hasNegativeCycle;

    public AllPairsResult(double[][] dist, int[][] pred, boolean hasNegativeCycle) {
        this.dist = dist;
        this.pred = pred;
        this.hasNegativeCycle = hasNegativeCycle;
    }

    public static AllPairsResult negativeCycle(int n) {
        return new AllPairsResult(new double[n][n], new int[n][n], true);
    }

    public boolean isReachable(int u, int v) {
        return !hasNegativeCycle && dist[u][v] != INF;
    }

    /** Vertex sequence u..v, or an empty list if unreachable or invalid. */
    public List<Integer> pathBetween(int u, int v) {
        List<Integer> path = new ArrayList<>();
        if (!isReachable(u, v)) {
            return path;
        }
        int cur = v;
        path.add(cur);
        while (cur != u) {
            cur = pred[u][cur];
            if (cur == -1) {
                return new ArrayList<>();
            }
            path.add(cur);
        }
        Collections.reverse(path);
        return path;
    }
}