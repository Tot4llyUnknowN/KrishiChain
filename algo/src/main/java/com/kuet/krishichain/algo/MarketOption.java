package com.kuet.krishichain.algo;

/** One candidate market for a farmer's crop, with its computed net profit. */
public final class MarketOption {
    public final int nodeId;
    public final String marketName;
    public final double pricePerKg;
    public final double transportCost;
    public final double toll;
    public final double spoilageFraction; // 0.0 .. 1.0 lost on the way
    public final double netProfit;

    public MarketOption(int nodeId, String marketName, double pricePerKg, double quantityKg,
                        double transportCost, double toll, double spoilageFraction) {
        this.nodeId = nodeId;
        this.marketName = marketName;
        this.pricePerKg = pricePerKg;
        this.transportCost = transportCost;
        this.toll = toll;
        this.spoilageFraction = spoilageFraction;
        this.netProfit = pricePerKg * quantityKg * (1.0 - spoilageFraction)
                - transportCost - toll;
    }
}