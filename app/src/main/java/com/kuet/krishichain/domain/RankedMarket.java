package com.kuet.krishichain.domain;

import com.kuet.krishichain.algo.MarketOption;

import java.util.List;

/** A market option plus the route details the screens need. */
public final class RankedMarket {
    public final MarketOption option;
    public final double km;
    public final int minutes;
    public final List<Integer> pathNodeIds; // origin ... market (database ids)

    public RankedMarket(MarketOption option, double km, int minutes, List<Integer> pathNodeIds) {
        this.option = option;
        this.km = km;
        this.minutes = minutes;
        this.pathNodeIds = pathNodeIds;
    }
}