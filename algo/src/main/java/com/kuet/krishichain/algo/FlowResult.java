package com.kuet.krishichain.algo;

import java.util.List;

/** Output of Max Flow: value, per-edge flow, and the minimum cut (bottleneck edges). */
public final class FlowResult {
    public final double maxFlow;
    public final List<FlowEdge> edges;     // every original edge with its final flow
    public final List<FlowEdge> cutEdges;  // saturated edges from the source side to the sink side
    public final boolean[] sourceSide;     // true = node is on the source side of the cut
    public final List<Step> steps;

    public FlowResult(double maxFlow, List<FlowEdge> edges, List<FlowEdge> cutEdges,
                      boolean[] sourceSide, List<Step> steps) {
        this.maxFlow = maxFlow;
        this.edges = edges;
        this.cutEdges = cutEdges;
        this.sourceSide = sourceSide;
        this.steps = steps;
    }
}