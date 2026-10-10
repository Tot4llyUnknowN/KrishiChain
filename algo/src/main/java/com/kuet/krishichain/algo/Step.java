package com.kuet.krishichain.algo;

/** One recorded event of an algorithm run. GraphView replays these. */
public final class Step {
    public enum Type { VISIT, RELAX, UPDATE, NEGATIVE_CYCLE, AUGMENT, MST_ADD, MST_SKIP, FINISH }

    public final Type type;
    public final int from;     // edge start, or the node for VISIT / NEGATIVE_CYCLE
    public final int to;       // edge end, or -1
    public final double value; // distance / cost / flow associated with the event

    private Step(Type type, int from, int to, double value) {
        this.type = type;
        this.from = from;
        this.to = to;
        this.value = value;
    }

    /** Node u was finalized (popped from the queue) with distance d. */
    public static Step visit(int u, double d) {
        return new Step(Type.VISIT, u, -1, d);
    }

    /** Edge u->v was examined; candidate distance is d. */
    public static Step relax(int u, int v, double d) {
        return new Step(Type.RELAX, u, v, d);
    }

    /** Edge u->v improved the distance of v to d. */
    public static Step update(int u, int v, double d) {
        return new Step(Type.UPDATE, u, v, d);
    }

    public static Step negativeCycle(int node) {
        return new Step(Type.NEGATIVE_CYCLE, node, -1, 0);
    }

    /** Edge u->v carried 'amount' more flow in an augmenting path. */
    public static Step augment(int u, int v, double amount) {
        return new Step(Type.AUGMENT, u, v, amount);
    }

    /** Edge u-v was added to the spanning tree. */
    public static Step mstAdd(int u, int v, double w) {
        return new Step(Type.MST_ADD, u, v, w);
    }

    /** Edge u-v was rejected (it would form a cycle). */
    public static Step mstSkip(int u, int v, double w) {
        return new Step(Type.MST_SKIP, u, v, w);
    }

    public static Step finish() {
        return new Step(Type.FINISH, -1, -1, 0);
    }
}