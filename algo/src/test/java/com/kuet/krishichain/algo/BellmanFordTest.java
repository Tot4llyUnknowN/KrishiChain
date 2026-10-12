package com.kuet.krishichain.algo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class BellmanFordTest {

    @Test
    public void handlesNegativeEdgeWithoutCycle() {
        Graph g = new Graph(4);
        g.addEdge(0, 1, 4);
        g.addEdge(0, 2, 5);
        g.addEdge(1, 2, -3); // backhaul credit
        g.addEdge(2, 3, 2);
        PathResult r = BellmanFord.run(g, 0);
        assertFalse(r.hasNegativeCycle);
        assertEquals(4, r.dist[1], 1e-9);
        assertEquals(1, r.dist[2], 1e-9);
        assertEquals(3, r.dist[3], 1e-9);
        assertEquals(Arrays.asList(0, 1, 2, 3), r.pathTo(3));
    }

    @Test
    public void detectsNegativeCycle() {
        Graph g = new Graph(3);
        g.addEdge(0, 1, 1);
        g.addEdge(1, 2, -1);
        g.addEdge(2, 1, -1); // cycle 1->2->1 totals -2
        PathResult r = BellmanFord.run(g, 0);
        assertTrue(r.hasNegativeCycle);
        assertTrue(r.negativeCycle.contains(1));
        assertTrue(r.negativeCycle.contains(2));
        assertTrue(r.pathTo(2).isEmpty());
    }

    @Test
    public void matchesDijkstraOnNonNegativeGraph() {
        Graph g = new Graph(4);
        g.addEdge(0, 1, 4);
        g.addEdge(0, 2, 1);
        g.addEdge(2, 1, 2);
        g.addEdge(1, 3, 1);
        PathResult a = Dijkstra.run(g, 0);
        PathResult b = BellmanFord.run(g, 0);
        for (int i = 0; i < 4; i++) {
            assertEquals(a.dist[i], b.dist[i], 1e-9);
        }
    }
}