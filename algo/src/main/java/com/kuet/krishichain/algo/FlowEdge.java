package com.kuet.krishichain.algo;

/** One road in a finished flow: how much it can carry and how much it does carry. */
public final class FlowEdge {
    public final int from;
    public final int to;
    public final double capacity;
    public final double flow;

    public FlowEdge(int from, int to, double capacity, double flow) {
        this.from = from;
        this.to = to;
        this.capacity = capacity;
        this.flow = flow;
    }
}