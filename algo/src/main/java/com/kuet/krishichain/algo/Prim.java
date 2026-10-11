package com.kuet.krishichain.algo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Prim: start at one hub, repeatedly add the cheapest edge leaving the tree (binary heap).
 * Every arc of the Graph is treated as an undirected road. O(E log V).
 * On a disconnected graph it spans only the start node's component.
 * operations = heap pushes + heap polls.
 */
public final class Prim {
    private Prim() { }

    public static MstResult run(Graph g, int start) {
        int n = g.size();
        List<List<Edge>> adj = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            adj.add(new ArrayList<Edge>());
        }
        for (Edge e : g.allEdges()) {
            adj.get(e.from).add(e);
            adj.get(e.to).add(new Edge(e.to, e.from, e.weight)); // undirected view
        }

        boolean[] inTree = new boolean[n];
        PriorityQueue<Edge> pq = new PriorityQueue<>(11, new Comparator<Edge>() {
            @Override
            public int compare(Edge a, Edge b) {
                return Double.compare(a.weight, b.weight);
            }
        });

        List<Edge> chosen = new ArrayList<>();
        List<Step> steps = new ArrayList<>();
        double total = 0;
        long ops = 0;

        inTree[start] = true;
        for (Edge e : adj.get(start)) {
            pq.add(e);
            ops++;
        }
        while (!pq.isEmpty() && chosen.size() < n - 1) {
            Edge e = pq.poll();
            ops++;
            if (inTree[e.to]) {
                steps.add(Step.mstSkip(e.from, e.to, e.weight));
                continue;
            }
            inTree[e.to] = true;
            chosen.add(e);
            total += e.weight;
            steps.add(Step.mstAdd(e.from, e.to, e.weight));
            for (Edge next : adj.get(e.to)) {
                if (!inTree[next.to]) {
                    pq.add(next);
                    ops++;
                }
            }
        }
        steps.add(Step.finish());
        return new MstResult(chosen, total, chosen.size() == n - 1 || n <= 1, ops, steps);
    }
}