package com.kuet.krishichain.algo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Output of a single-source shortest path algorithm. */
public final class PathResult {
    public static final double INF = Double.POSITIVE_INFINITY;

    public final double[] dist;
    public final int[] prev;
    public final List<Step> steps;
    public final boolean hasNegativeCycle;
    public final List<Integer> negativeCycle; // empty if none

    public PathResult(double[] dist, int[] prev, List<Step> steps,
                      boolean hasNegativeCycle, List<Integer> negativeCycle) {
        this.dist = dist;
        this.prev = prev;
        this.steps = steps;
        this.hasNegativeCycle = hasNegativeCycle;
        this.negativeCycle = negativeCycle;
    }

    public boolean isReachable(int target) {
        return dist[target] != INF;
    }

    /** Vertex sequence source..target, or an empty list if unreachable / invalid. */
    public List<Integer> pathTo(int target) {
        List<Integer> path = new ArrayList<>();
        if (hasNegativeCycle || !isReachable(target)) {
            return path;
        }
        for (int v = target; v != -1; v = prev[v]) {
            path.add(v);
        }
        Collections.reverse(path);
        return path;
    }
}