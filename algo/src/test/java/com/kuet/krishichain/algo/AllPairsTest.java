package com.kuet.krishichain.algo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class AllPairsTest {

    /** Classic textbook graph (CLRS 25.1), 0-indexed, with negative arcs and no negative cycle. */
    private Graph clrs() {
        Graph g = new Graph(5);
        g.addEdge(0, 1, 3);
        g.addEdge(0, 2, 8);
        g.addEdge(0, 4, -4);
        g.addEdge(1, 3, 1);
        g.addEdge(1, 4, 7);
        g.addEdge(2, 1, 4);
        g.addEdge(3, 0, 2);
        g.addEdge(3, 2, -5);
        g.addEdge(4, 3, 6);
        return g;
    }

    private static final double[][] CLRS_EXPECTED = {
            {0, 1, -3, 2, -4},
            {3, 0, -4, 1, -1},
            {7, 4, 0, 5, 3},
            {2, -1, -5, 0, -2},
            {8, 5, 1, 6, 0}
    };

    private void assertMatrix(double[][] expected, double[][] actual) {
        for (int i = 0; i < expected.length; i++) {
            for (int j = 0; j < expected.length; j++) {
                assertEquals("cell " + i + "," + j, expected[i][j], actual[i][j], 1e-9);
            }
        }
    }

    private Graph negativeCycleGraph() {
        Graph g = new Graph(3);
        g.addEdge(0, 1, 1);
        g.addEdge(1, 2, -3);
        g.addEdge(2, 1, 1); // cycle 1->2->1 totals -2
        return g;
    }

    @Test
    public void floydMatchesTextbook() {
        assertMatrix(CLRS_EXPECTED, FloydWarshall.run(clrs()).dist);
    }

    @Test
    public void johnsonMatchesTextbook() {
        assertMatrix(CLRS_EXPECTED, Johnson.run(clrs()).dist);
    }

    @Test
    public void floydDetectsNegativeCycle() {
        assertTrue(FloydWarshall.run(negativeCycleGraph()).hasNegativeCycle);
    }

    @Test
    public void johnsonDetectsNegativeCycle() {
        assertTrue(Johnson.run(negativeCycleGraph()).hasNegativeCycle);
    }

    @Test
    public void floydEqualsJohnsonOnRandomGraphs() {
        int[][] shapes = {{10, 15}, {30, 60}, {30, 400}, {60, 200}, {50, 2400}};
        for (int[] s : shapes) {
            for (long seed = 1; seed <= 5; seed++) {
                Graph g = GraphGenerator.random(s[0], s[1], true, seed);
                AllPairsResult f = FloydWarshall.run(g);
                AllPairsResult j = Johnson.run(g);
                assertFalse(f.hasNegativeCycle);
                assertFalse(j.hasNegativeCycle);
                for (int u = 0; u < s[0]; u++) {
                    for (int v = 0; v < s[0]; v++) {
                        assertEquals(f.dist[u][v], j.dist[u][v], 1e-9);
                    }
                }
            }
        }
    }

    @Test
    public void pathReconstructionMatchesDistance() {
        Graph g = clrs();
        AllPairsResult f = FloydWarshall.run(g);
        AllPairsResult j = Johnson.run(g);
        assertEquals(Arrays.asList(1, 3, 2), f.pathBetween(1, 2)); // 1->3->2 costs 1 + -5 = -4
        assertEquals(Arrays.asList(1, 3, 2), j.pathBetween(1, 2));
        assertEquals(Arrays.asList(2), f.pathBetween(2, 2));

        // Every reconstructed path must add up to the reported distance.
        for (int u = 0; u < 5; u++) {
            for (int v = 0; v < 5; v++) {
                assertEquals(f.dist[u][v], pathCost(g, f.pathBetween(u, v)), 1e-9);
                assertEquals(j.dist[u][v], pathCost(g, j.pathBetween(u, v)), 1e-9);
            }
        }
    }

    @Test
    public void unreachablePairsAreInfinite() {
        Graph g = new Graph(3);
        g.addEdge(0, 1, 5);
        AllPairsResult f = FloydWarshall.run(g);
        AllPairsResult j = Johnson.run(g);
        assertEquals(AllPairsResult.INF, f.dist[1][0], 0);
        assertEquals(AllPairsResult.INF, j.dist[2][1], 0);
        assertTrue(f.pathBetween(1, 0).isEmpty());
        assertTrue(j.pathBetween(0, 2).isEmpty());
    }

    @Test
    public void benchmarkRunsAndAgrees() {
        AllPairsBenchmark b = AllPairsBenchmark.run(80, 300, 7);
        assertTrue(b.sameAnswer);
        assertTrue(b.floydMillis >= 0 && b.johnsonMillis >= 0);
    }

    /** Sum of the cheapest arcs along a vertex path. */
    private double pathCost(Graph g, List<Integer> path) {
        double total = 0;
        for (int i = 0; i + 1 < path.size(); i++) {
            double best = Double.POSITIVE_INFINITY;
            for (Edge e : g.edgesFrom(path.get(i))) {
                if (e.to == path.get(i + 1) && e.weight < best) {
                    best = e.weight;
                }
            }
            total += best;
        }
        return total;
    }
}