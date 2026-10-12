package com.kuet.krishichain.algo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MaxFlowTest {

    /** CLRS figure 26.6: s=0, v1=1, v2=2, v3=3, v4=4, t=5. Known max flow = 23. */
    private MaxFlow clrs() {
        MaxFlow f = new MaxFlow(6);
        f.addEdge(0, 1, 16);
        f.addEdge(0, 2, 13);
        f.addEdge(1, 3, 12);
        f.addEdge(2, 1, 4);
        f.addEdge(2, 4, 14);
        f.addEdge(3, 2, 9);
        f.addEdge(3, 5, 20);
        f.addEdge(4, 3, 7);
        f.addEdge(4, 5, 4);
        return f;
    }

    private double cutCapacity(FlowResult r) {
        double sum = 0;
        for (FlowEdge e : r.cutEdges) {
            sum += e.capacity;
        }
        return sum;
    }

    @Test
    public void clrsMaxFlowIs23() {
        FlowResult r = clrs().run(0, 5);
        assertEquals(23, r.maxFlow, 1e-9);
        assertEquals(23, cutCapacity(r), 1e-9);
        assertTrue(r.sourceSide[0]);
        assertTrue(!r.sourceSide[5]);
    }

    @Test
    public void flowConservationAndCapacityLimits() {
        FlowResult r = clrs().run(0, 5);
        double[] balance = new double[6];
        for (FlowEdge e : r.edges) {
            assertTrue(e.flow >= -1e-9 && e.flow <= e.capacity + 1e-9);
            balance[e.from] -= e.flow;
            balance[e.to] += e.flow;
        }
        for (int v = 1; v <= 4; v++) {
            assertEquals("node " + v, 0, balance[v], 1e-9);
        }
        assertEquals(23, balance[5], 1e-9);
    }

    @Test
    public void minCutEqualsMaxFlowOnRandomGraphs() {
        for (long seed = 1; seed <= 10; seed++) {
            int n = 20;
            Graph g = GraphGenerator.random(n, 70, false, seed);
            MaxFlow f = new MaxFlow(n);
            for (Edge e : g.allEdges()) {
                f.addEdge(e.from, e.to, e.weight);
            }
            FlowResult r = f.run(0, n - 1);
            assertEquals(r.maxFlow, cutCapacity(r), 1e-9);
        }
    }

    @Test
    public void superSourceSuperSinkFindsBottleneck() {
        // Villages 0,1 -> markets 2,3. Super source = 4, super sink = 5.
        MaxFlow f = new MaxFlow(6);
        f.addEdge(4, 0, 100);
        f.addEdge(4, 1, 100);
        f.addEdge(0, 2, 30);
        f.addEdge(0, 3, 10);
        f.addEdge(1, 3, 25);
        f.addEdge(2, 5, 20);
        f.addEdge(3, 5, 50);
        FlowResult r = f.run(4, 5);
        assertEquals(55, r.maxFlow, 1e-9);
        assertEquals(3, r.cutEdges.size());
        boolean found = false;
        for (FlowEdge e : r.cutEdges) {
            if (e.from == 2 && e.to == 5 && e.capacity == 20) {
                found = true;
            }
        }
        assertTrue("edge 2->5 must be a bottleneck", found);
        // Running twice gives the same answer.
        assertEquals(55, f.run(4, 5).maxFlow, 1e-9);
    }
}