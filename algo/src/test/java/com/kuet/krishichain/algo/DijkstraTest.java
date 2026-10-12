package com.kuet.krishichain.algo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class DijkstraTest {

    private Graph sample() {
        Graph g = new Graph(4);
        g.addUndirectedEdge(0, 1, 4);
        g.addUndirectedEdge(0, 2, 1);
        g.addUndirectedEdge(2, 1, 2);
        g.addUndirectedEdge(1, 3, 1);
        g.addUndirectedEdge(2, 3, 5);
        return g;
    }

    @Test
    public void shortestDistances() {
        PathResult r = Dijkstra.run(sample(), 0);
        assertEquals(0, r.dist[0], 1e-9);
        assertEquals(3, r.dist[1], 1e-9);
        assertEquals(1, r.dist[2], 1e-9);
        assertEquals(4, r.dist[3], 1e-9);
    }

    @Test
    public void pathIsCorrect() {
        PathResult r = Dijkstra.run(sample(), 0);
        assertEquals(Arrays.asList(0, 2, 1, 3), r.pathTo(3));
    }

    @Test
    public void unreachableNodeHasEmptyPath() {
        Graph g = new Graph(3);
        g.addEdge(0, 1, 2);
        PathResult r = Dijkstra.run(g, 0);
        assertTrue(r.pathTo(2).isEmpty());
        assertEquals(PathResult.INF, r.dist[2], 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeEdgeIsRejected() {
        Graph g = new Graph(2);
        g.addEdge(0, 1, -1);
        Dijkstra.run(g, 0);
    }
}