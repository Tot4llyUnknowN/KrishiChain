package com.kuet.krishichain.algo;

import java.util.List;

/** Output of a minimum spanning tree (or forest) algorithm. */
public final class MstResult {
    public final List<Edge> edges;
    public final double totalCost;
    public final boolean spanning;  // false if the graph is disconnected
    public final long operations;   // algorithm-specific work counter (see each class)
    public final List<Step> steps;

    public MstResult(List<Edge> edges, double totalCost, boolean spanning,
                     long operations, List<Step> steps) {
        this.edges = edges;
        this.totalCost = totalCost;
        this.spanning = spanning;
        this.operations = operations;
        this.steps = steps;
    }
}