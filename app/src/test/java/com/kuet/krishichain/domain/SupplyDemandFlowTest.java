package com.kuet.krishichain.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.kuet.krishichain.algo.MarketOption;
import com.kuet.krishichain.algo.Sorts;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SupplyDemandFlowTest {

    @Test
    public void testSmallSupplyDoesNotSplit() {
        // Market 1: Dhaka, demand = 250 tons, profit = 40 BDT/kg
        MarketOption opt1 = new MarketOption(1, "Dhaka", 50.0, 1000.0, 20.0, 500.0, 0.0, 0.0);
        RankedMarket m1 = new RankedMarket(opt1, 30.0, 45, Arrays.asList(2, 1), 250.0);

        List<RankedMarket> list = new ArrayList<>();
        list.add(m1);

        // Input 1000 kg (1 ton) -> less than 250 tons
        SupplyDemandAllocation alloc = SupplyDemandFlowRouter.allocate(list, 1000.0);
        assertFalse(alloc.isSplit);
        assertEquals(1, alloc.items.size());
        assertEquals(1.0, alloc.totalAllocatedTons, 0.001);
        assertEquals(1.0, alloc.items.get(0).allocatedTons, 0.001);
        assertFalse(alloc.items.get(0).isSaturated);
    }

    @Test
    public void testExcessSupplyDivertsToSecondMarket() {
        // Market 1: Dhaka, demand = 100 tons, net profit rate ~ 30 BDT/kg
        MarketOption opt1 = new MarketOption(1, "Dhaka", 50.0, 100000.0, 20.0, 1000.0, 0.0, 0.0);
        RankedMarket m1 = new RankedMarket(opt1, 30.0, 40, Arrays.asList(2, 1), 100.0);

        // Market 2: Gazipur, demand = 80 tons, net profit rate ~ 25 BDT/kg
        MarketOption opt2 = new MarketOption(2, "Gazipur", 45.0, 80000.0, 20.0, 500.0, 0.0, 0.0);
        RankedMarket m2 = new RankedMarket(opt2, 15.0, 20, Arrays.asList(2), 80.0);

        List<RankedMarket> list = new ArrayList<>();
        list.add(m1);
        list.add(m2);

        // Input 150 tons (150,000 kg): 100 tons to Dhaka (saturated), 50 tons to Gazipur
        SupplyDemandAllocation alloc = SupplyDemandFlowRouter.allocate(list, 150000.0);
        assertTrue(alloc.isSplit);
        assertEquals(2, alloc.items.size());
        assertEquals(150.0, alloc.totalAllocatedTons, 0.001);

        // Check Market 1 allocation
        assertEquals(100.0, alloc.items.get(0).allocatedTons, 0.001);
        assertTrue(alloc.items.get(0).isSaturated);

        // Check Market 2 allocation
        assertEquals(50.0, alloc.items.get(1).allocatedTons, 0.001);
        assertFalse(alloc.items.get(1).isSaturated);
    }

    @Test
    public void testSortingComparators() {
        MarketOption opt1 = new MarketOption(1, "Far Market High Profit", 60.0, 1000.0, 20.0, 500.0, 0.0, 0.0);
        RankedMarket m1 = new RankedMarket(opt1, 200.0, 180, Arrays.asList(2, 1), 100.0);

        MarketOption opt2 = new MarketOption(2, "Near Market Low Profit", 40.0, 1000.0, 20.0, 200.0, 0.0, 0.0);
        RankedMarket m2 = new RankedMarket(opt2, 20.0, 25, Arrays.asList(2), 50.0);

        List<RankedMarket> list = new ArrayList<>();
        list.add(m1);
        list.add(m2);

        // Sort by Best Profit
        Sorts.quickSort(list, RankedMarket.BY_BEST_PROFIT);
        assertEquals("Far Market High Profit", list.get(0).option.marketName);

        // Sort by Nearest Distance
        Sorts.quickSort(list, RankedMarket.BY_NEAREST_DISTANCE);
        assertEquals("Near Market Low Profit", list.get(0).option.marketName);
    }
}
