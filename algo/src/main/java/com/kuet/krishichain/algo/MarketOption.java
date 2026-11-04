package com.kuet.krishichain.algo;

/** One candidate market for a farmer's crop, with its computed net profit and delivered price. */
public final class MarketOption {
    public static final double DEFAULT_PRODUCTION_COST_PER_KG = 20.0; // BDT/kg default base production cost

    public final int nodeId;
    public final String marketName;
    public final double pricePerKg; // Destination market price
    public final double quantityKg;
    public final double prodCostPerKg; // Base production cost per kg inputted by the farmer
    public final double productionCost; // Base production cost = prodCostPerKg * quantityKg
    public final double transportCost; // Dependent on distance, dispatch charge, and road/weather factors
    public final double toll; // Highway & bridge tolls
    public final double spoilageFraction; // 0.0 .. 1.0 lost on the way (dependent on travel time)
    public final double deliveredCostPerKg; // Minimum cost per delivered kg
    public final double netProfit;

    public MarketOption(int nodeId, String marketName, double pricePerKg, double quantityKg,
                        double prodCostPerKg, double transportCost, double toll, double spoilageFraction) {
        this.nodeId = nodeId;
        this.marketName = marketName;
        this.pricePerKg = pricePerKg;
        this.quantityKg = quantityKg;
        this.prodCostPerKg = prodCostPerKg >= 0 ? prodCostPerKg : DEFAULT_PRODUCTION_COST_PER_KG;
        this.productionCost = this.prodCostPerKg * quantityKg;
        this.transportCost = transportCost;
        this.toll = toll;
        this.spoilageFraction = spoilageFraction;

        double deliveredKg = Math.max(0.001, quantityKg * (1.0 - spoilageFraction));
        this.deliveredCostPerKg = (this.productionCost + transportCost + toll) / deliveredKg;
        this.netProfit = (pricePerKg * deliveredKg) - this.productionCost - transportCost - toll;
    }

    public MarketOption(int nodeId, String marketName, double pricePerKg, double quantityKg,
                        double transportCost, double toll, double spoilageFraction) {
        this(nodeId, marketName, pricePerKg, quantityKg, 0.0, transportCost, toll, spoilageFraction);
    }
}
