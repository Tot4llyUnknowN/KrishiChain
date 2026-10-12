package com.kuet.krishichain.algo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import org.junit.Test;

public class MstTest {

    /** MST = 0-1 (2), 1-2 (3), 1-4 (5), 0-3 (6) = 16. */
    private Graph known() {
        Graph g = new Graph(5);
        g.addUndirectedEdge(0, 1, 2);
        g.addUndirectedEdge(0, 3, 6);
        g.addUndirectedEdge(1, 2, 3);
        g.addUndirectedEdge(1, 3, 8);
        g.addUndirectedEdge(1, 4, 5);
        g.addUndirectedEdge(2, 4, 7);
        g.addUndirectedEdge(3, 4, 9);
        return g;
    }

    @Test
    public void kruskalKnownGraph() {
        MstResult r = Kruskal.run(known());
        assertEquals(16, r.totalCost, 1e-9);
        assertEquals(4, r.edges.size());
        assertTrue(r.spanning);
    }

    @Test
    public void primKnownGraph() {
        for (int start = 0; start < 5; start++) {
            MstResult r = Prim.run(known(), start);
            assertEquals(16, r.totalCost, 1e-9);
            assertEquals(4, r.edges.size());
            assertTrue(r.spanning);
        }
    }

    @Test
    public void kruskalEqualsPrimOnRandomConnectedGraphs() {
        for (long seed = 1; seed <= 10; seed++) {
            Random rnd = new Random(seed);
            int n = 30;
            Graph g = new Graph(n);
            for (int i = 0; i + 1 < n; i++) {
                g.addUndirectedEdge(i, i + 1, 1 + rnd.nextInt(30)); // chain keeps it connected
            }
            for (int k = 0; k < 60; k++) {
                int u = rnd.nextInt(n);
                int v = rnd.nextInt(n);
                if (u != v) {
                    g.addUndirectedEdge(u, v, 1 + rnd.nextInt(30));
                }
            }
            MstResult k1 = Kruskal.run(g);
            MstResult p1 = Prim.run(g, 0);
            assertTrue(k1.spanning && p1.spanning);
            assertEquals(n - 1, k1.edges.size());
            assertEquals(n - 1, p1.edges.size());
            assertEquals(k1.totalCost, p1.totalCost, 1e-9);
        }
    }

    @Test
    public void disconnectedGraphIsNotSpanning() {
        Graph g = new Graph(4);
        g.addUndirectedEdge(0, 1, 1);
        g.addUndirectedEdge(2, 3, 2);
        MstResult k = Kruskal.run(g);
        MstResult p = Prim.run(g, 0);
        assertFalse(k.spanning);
        assertFalse(p.spanning);
        assertEquals(3, k.totalCost, 1e-9); // forest: both components
        assertEquals(1, p.totalCost, 1e-9); // only the start component
    }
}