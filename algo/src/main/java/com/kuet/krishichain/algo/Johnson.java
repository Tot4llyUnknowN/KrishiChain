package com.kuet.krishichain.algo;

/**
 * Johnson's algorithm: O(V * E log V).
 * 1) Add a virtual node q with 0-weight arcs to every node; Bellman-Ford from q gives potentials h[].
 * 2) Reweight every arc: w'(u,v) = w(u,v) + h[u] - h[v]  (always >= 0).
 * 3) Run Dijkstra from every node on the reweighted graph.
 * 4) Undo the reweighting: d(u,v) = d'(u,v) - h[u] + h[v].
 * No animation traces are recorded: only the final matrix matters here.
 */
public final class Johnson {
    private Johnson() { }

    public static AllPairsResult run(Graph g) {
        int n = g.size();

        Graph aug = new Graph(n + 1);
        for (Edge e : g.allEdges()) {
            aug.addEdge(e.from, e.to, e.weight);
        }
        for (int v = 0; v < n; v++) {
            aug.addEdge(n, v, 0);
        }
        PathResult bf = BellmanFord.run(aug, n, false);
        if (bf.hasNegativeCycle) {
            return AllPairsResult.negativeCycle(n);
        }
        double[] h = bf.dist;

        Graph reweighted = new Graph(n);
        for (Edge e : g.allEdges()) {
            double w = e.weight + h[e.from] - h[e.to];
            if (w < 0) {
                w = 0; // only floating-point noise can make it slightly negative
            }
            reweighted.addEdge(e.from, e.to, w);
        }

        double[][] dist = new double[n][n];
        int[][] pred = new int[n][n];
        for (int u = 0; u < n; u++) {
            PathResult r = Dijkstra.run(reweighted, u, false);
            for (int v = 0; v < n; v++) {
                if (r.dist[v] == PathResult.INF) {
                    dist[u][v] = AllPairsResult.INF;
                    pred[u][v] = -1;
                } else {
                    dist[u][v] = r.dist[v] - h[u] + h[v];
                    pred[u][v] = r.prev[v];
                }
            }
        }
        return new AllPairsResult(dist, pred, false);
    }
}