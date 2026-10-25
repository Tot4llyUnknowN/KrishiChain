package com.kuet.krishichain.algo;

/** Times Floyd-Warshall vs Johnson on the same graph. */
public final class AllPairsBenchmark {
    public final int nodes;
    public final int edges;
    public final double floydMillis;
    public final double johnsonMillis;
    public final boolean sameAnswer;

    private AllPairsBenchmark(int nodes, int edges, double floydMillis,
                              double johnsonMillis, boolean sameAnswer) {
        this.nodes = nodes;
        this.edges = edges;
        this.floydMillis = floydMillis;
        this.johnsonMillis = johnsonMillis;
        this.sameAnswer = sameAnswer;
    }

    public static AllPairsBenchmark run(int n, int m, long seed) {
        Graph g = GraphGenerator.random(n, m, true, seed);

        long t0 = System.nanoTime();
        AllPairsResult f = FloydWarshall.run(g);
        long t1 = System.nanoTime();
        AllPairsResult j = Johnson.run(g);
        long t2 = System.nanoTime();

        boolean same = true;
        for (int i = 0; i < n && same; i++) {
            for (int k = 0; k < n; k++) {
                double a = f.dist[i][k];
                double b = j.dist[i][k];
                boolean bothInf = (a == AllPairsResult.INF && b == AllPairsResult.INF);
                if (!bothInf && Math.abs(a - b) > 1e-6) {
                    same = false;
                    break;
                }
            }
        }
        return new AllPairsBenchmark(n, g.allEdges().size(),
                (t1 - t0) / 1e6, (t2 - t1) / 1e6, same);
    }
}