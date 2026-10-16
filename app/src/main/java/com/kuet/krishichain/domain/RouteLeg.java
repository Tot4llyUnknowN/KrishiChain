package com.kuet.krishichain.domain;

/** One road segment of a route. Cost and toll are for the whole load. */
public final class RouteLeg {
    public final String fromName;
    public final String toName;
    public final double km;
    public final int minutes;
    public final double cost;
    public final double toll;

    public RouteLeg(String fromName, String toName, double km, int minutes,
                    double cost, double toll) {
        this.fromName = fromName;
        this.toName = toName;
        this.km = km;
        this.minutes = minutes;
        this.cost = cost;
        this.toll = toll;
    }
}