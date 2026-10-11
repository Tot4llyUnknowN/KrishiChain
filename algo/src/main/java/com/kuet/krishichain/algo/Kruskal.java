package com.kuet.krishichain.algo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Kruskal: sort edges by cost (our stable Merge Sort), then take each edge that joins two
 * different components (checked with Union-Find). Every arc of the Graph is treated as an
 * undirected road. O(E log E). On a disconnected graph it returns a minimum spanning forest.
 * operations = number of edges examined.
 */
public final class Kruskal {
    private Kruskal() { }

    public static MstResult run(Graph g) {
        int n = g.size();
        List<Edge> sorted = new ArrayList<>(g.allEdges());
        Sorts.mergeSort(sorted, new Comparator<Edge>() {
            @Override
            public int compare(Edge a, Edge b) {
                return Double.compare(a.weight, b.weight);
            }
        });

        int[] parent = new int[n];
        int[] rank = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }

        List<Edge> chosen = new ArrayList<>();
        List<Step> steps = new ArrayList<>();
        double total = 0;
        long ops = 0;

        for (Edge e : sorted) {
            if (chosen.size() == n - 1) {
                break;
            }
            ops++;
            int a = find(parent, e.from);
            int b = find(parent, e.to);
            if (a == b) {
                steps.add(Step.mstSkip(e.from, e.to, e.weight));
                continue;
            }
            if (rank[a] < rank[b]) {
                parent[a] = b;
            } else if (rank[a] > rank[b]) {
                parent[b] = a;
            } else {
                parent[b] = a;
                rank[a]++;
            }
            chosen.add(e);
            total += e.weight;
            steps.add(Step.mstAdd(e.from, e.to, e.weight));
        }
        steps.add(Step.finish());
        return new MstResult(chosen, total, chosen.size() == n - 1 || n <= 1, ops, steps);
    }

    /** Find with path compression (iterative). */
    private static int find(int[] parent, int x) {
        int root = x;
        while (parent[root] != root) {
            root = parent[root];
        }
        while (parent[x] != root) {
            int next = parent[x];
            parent[x] = root;
            x = next;
        }
        return root;
    }
}