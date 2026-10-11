package com.kuet.krishichain.algo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Edmonds-Karp: Ford-Fulkerson where every augmenting path is the SHORTEST one (BFS).
 * That choice guarantees O(V * E^2). Each arc is stored with a paired reverse arc
 * (ids 2k and 2k+1), whose capacity is the flow that can be "undone".
 *
 * Min cut: after the last BFS, the nodes still reachable from the source form the source side.
 * Every original arc from that side to the other side is saturated, and their capacities sum
 * to the max flow (max-flow min-cut theorem).
 */
public final class MaxFlow {
    private static final double EPS = 1e-9;

    private static final class Arc {
        final int from;
        final int to;
        final double orig;
        double cap;

        Arc(int from, int to, double orig) {
            this.from = from;
            this.to = to;
            this.orig = orig;
            this.cap = orig;
        }
    }

    private final int n;
    private final List<Arc> arcs = new ArrayList<>();
    private final List<List<Integer>> adj = new ArrayList<>();

    public MaxFlow(int n) {
        this.n = n;
        for (int i = 0; i < n; i++) {
            adj.add(new ArrayList<Integer>());
        }
    }

    /** Adds a directed road u -> v that can carry 'capacity' (tons/day). */
    public void addEdge(int u, int v, double capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Capacity must be >= 0");
        }
        arcs.add(new Arc(u, v, capacity));
        adj.get(u).add(arcs.size() - 1);
        arcs.add(new Arc(v, u, 0));
        adj.get(v).add(arcs.size() - 1);
    }

    public FlowResult run(int s, int t) {
        if (s == t) {
            throw new IllegalArgumentException("Source and sink must differ");
        }
        for (Arc a : arcs) {
            a.cap = a.orig; // makes run() repeatable
        }
        List<Step> steps = new ArrayList<>();
        double total = 0;
        boolean[] seen;

        while (true) {
            int[] parentArc = new int[n];
            Arrays.fill(parentArc, -1);
            seen = new boolean[n];
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            seen[s] = true;
            queue.add(s);
            while (!queue.isEmpty() && !seen[t]) {
                int u = queue.poll();
                for (int id : adj.get(u)) {
                    Arc a = arcs.get(id);
                    if (!seen[a.to] && a.cap > EPS) {
                        seen[a.to] = true;
                        parentArc[a.to] = id;
                        queue.add(a.to);
                    }
                }
            }
            if (!seen[t]) {
                break; // no augmenting path: 'seen' is exactly the source side of the min cut
            }

            List<Integer> path = new ArrayList<>();
            double bottleneck = Double.POSITIVE_INFINITY;
            for (int v = t; v != s; v = arcs.get(parentArc[v]).from) {
                path.add(parentArc[v]);
                bottleneck = Math.min(bottleneck, arcs.get(parentArc[v]).cap);
            }
            Collections.reverse(path); // source -> sink order for the animation
            for (int id : path) {
                arcs.get(id).cap -= bottleneck;
                arcs.get(id ^ 1).cap += bottleneck;
                steps.add(Step.augment(arcs.get(id).from, arcs.get(id).to, bottleneck));
            }
            total += bottleneck;
        }
        steps.add(Step.finish());

        List<FlowEdge> edges = new ArrayList<>();
        List<FlowEdge> cut = new ArrayList<>();
        for (int i = 0; i < arcs.size(); i += 2) {
            Arc a = arcs.get(i);
            FlowEdge fe = new FlowEdge(a.from, a.to, a.orig, a.orig - a.cap);
            edges.add(fe);
            if (seen[a.from] && !seen[a.to]) {
                cut.add(fe);
            }
        }
        return new FlowResult(total, edges, cut, seen, steps);
    }
}