package com.kuet.krishichain.domain;

import com.kuet.krishichain.algo.MarketOption;

import java.util.List;

/** A complete route with its totals and the resulting profit. */
public final class RouteResult {
    public final MarketOption option;   // profit numbers for this route
    public final List<RouteLeg> legs;   // empty if origin == destination
    public final List<String> placeNames;
    public final double km;
    public final int minutes;
    public final boolean byTime;

    public RouteResult(MarketOption option, List<RouteLeg> legs, List<String> placeNames,
                       double km, int minutes, boolean byTime) {
        this.option = option;
        this.legs = legs;
        this.placeNames = placeNames;
        this.km = km;
        this.minutes = minutes;
        this.byTime = byTime;
    }
}