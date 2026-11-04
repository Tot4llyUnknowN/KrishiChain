package com.kuet.krishichain.domain;

import com.google.gson.Gson;
import com.kuet.krishichain.algo.Dijkstra;
import com.kuet.krishichain.algo.MarketOption;
import com.kuet.krishichain.algo.PathResult;
import com.kuet.krishichain.data.EdgeEntity;
import com.kuet.krishichain.data.NetworkJson;
import com.kuet.krishichain.data.NodeEntity;
import com.kuet.krishichain.data.PriceEntity;
import com.kuet.krishichain.data.WeatherEntity;

import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Rigorous test suite validating all domain algorithms, formulas, and corner cases:
 * - Net Profit & Delivered Cost equations
 * - Spoilage capping (0% to 100%) and safe division
 * - Quantity economics: 1 kg (dispatch fee penalty) vs 2,000 kg (bulk amortization)
 * - Zero, normal, and excessive production costs
 * - Weather rain penalties (2x time, 1.4x transport cost)
 * - 64-district Bangladesh national highway reachability
 */
public class MarketRankerCornerCasesTest {

    private List<NodeEntity> testNodes;
    private List<EdgeEntity> testEdges;

    @Before
    public void setup() {
        testNodes = new ArrayList<>();
        // Node 1: Bogura (Origin)
        NodeEntity n1 = new NodeEntity();
        n1.id = 1;
        n1.name = "Bogura";
        n1.lat = 24.8465;
        n1.lon = 89.3778;
        testNodes.add(n1);

        // Node 2: Sirajganj (Intermediate)
        NodeEntity n2 = new NodeEntity();
        n2.id = 2;
        n2.name = "Sirajganj";
        n2.lat = 24.4534;
        n2.lon = 89.7008;
        testNodes.add(n2);

        // Node 3: Dhaka (Capital Wholesale Hub)
        NodeEntity n3 = new NodeEntity();
        n3.id = 3;
        n3.name = "Dhaka";
        n3.lat = 23.8103;
        n3.lon = 90.4125;
        testNodes.add(n3);

        testEdges = new ArrayList<>();
        // Road 1-2: Bogura -> Sirajganj (70 km, 90 mins, cost 350 BDT/ton, toll 0)
        EdgeEntity e1 = new EdgeEntity();
        e1.fromId = 1; e1.toId = 2; e1.km = 70; e1.minutes = 90; e1.cost = 350; e1.toll = 0;
        testEdges.add(e1);
        EdgeEntity e1Rev = new EdgeEntity();
        e1Rev.fromId = 2; e1Rev.toId = 1; e1Rev.km = 70; e1Rev.minutes = 90; e1Rev.cost = 350; e1Rev.toll = 0;
        testEdges.add(e1Rev);

        // Road 2-3: Sirajganj -> Dhaka (130 km, 180 mins, cost 650 BDT/ton, toll 250 (Jamuna Bridge))
        EdgeEntity e2 = new EdgeEntity();
        e2.fromId = 2; e2.toId = 3; e2.km = 130; e2.minutes = 180; e2.cost = 650; e2.toll = 250;
        testEdges.add(e2);
        EdgeEntity e2Rev = new EdgeEntity();
        e2Rev.fromId = 3; e2Rev.toId = 2; e2Rev.km = 130; e2Rev.minutes = 180; e2Rev.cost = 650; e2Rev.toll = 250;
        testEdges.add(e2Rev);
    }

    @Test
    public void testMarketOptionDirectFormula() {
        // Price: 35 BDT/kg, Qty: 1000 kg, ProdCost: 15 BDT/kg, Transport: 1200, Toll: 250, Spoilage: 5% (0.05)
        MarketOption opt = new MarketOption(3, "Dhaka", 35.0, 1000.0, 15.0, 1200.0, 250.0, 0.05);

        // Delivered Kg = 1000 * (1 - 0.05) = 950 kg
        double expectedDeliveredKg = 950.0;
        // Total Cost = ProdCost(15 * 1000 = 15000) + Transport(1200) + Toll(250) = 16,450
        double expectedTotalCost = 16450.0;
        // Revenue = 35.0 * 950 = 33,250
        double expectedRevenue = 33250.0;
        // Net Profit = Revenue - TotalCost = 33,250 - 16,450 = 16,800
        double expectedNetProfit = 16800.0;
        // Delivered Cost per Kg = 16450 / 950 = 17.315789...
        double expectedDeliveredCostPerKg = 16450.0 / 950.0;

        assertEquals(15.0, opt.prodCostPerKg, 1e-6);
        assertEquals(15000.0, opt.productionCost, 1e-6);
        assertEquals(expectedNetProfit, opt.netProfit, 1e-6);
        assertEquals(expectedDeliveredCostPerKg, opt.deliveredCostPerKg, 1e-6);
    }

    @Test
    public void testSpoilageCappingAndSafeDivision() {
        // Extreme transit where spoilage is 100% or exceeds 100%
        MarketOption opt100 = new MarketOption(1, "LostMarket", 40.0, 500.0, 10.0, 500.0, 0.0, 1.0);
        // deliveredKg is capped at minimum 0.001 to prevent divide by zero
        assertTrue(opt100.deliveredCostPerKg > 0);
        // Revenue is negligible: 40.0 * 0.001 = 0.04 BDT
        // Net profit is negative (Total cost = 5000 + 500 = 5500; Net = 0.04 - 5500 = -5499.96)
        assertEquals(-5499.96, opt100.netProfit, 1e-2);
    }

    @Test
    public void testSmallQuantityFavorsLocalMarketDueToDispatchFee() {
        // At 1 kg, inter-district shipping adds MarketRanker.BASE_INTERDISTRICT_DISPATCH_FEE (200 BDT)
        NetworkSnapshot snap = new NetworkSnapshot(testNodes, testEdges, Collections.emptyList());

        List<PriceEntity> prices = new ArrayList<>();
        // Bogura local price: 25 BDT/kg
        PriceEntity pLocal = new PriceEntity();
        pLocal.nodeId = 1; pLocal.crop = "potato"; pLocal.price = 25.0;
        prices.add(pLocal);

        // Dhaka distant price: 35 BDT/kg (+40% higher price!)
        PriceEntity pDhaka = new PriceEntity();
        pDhaka.nodeId = 3; pDhaka.crop = "potato"; pDhaka.price = 35.0;
        prices.add(pDhaka);

        // 1 kg of potato from Bogura (Node 1), base production cost 15 BDT/kg, spoilage 0.1%/hr
        RankingResult result = MarketRanker.rank(snap, prices, 1, 1.0, 15.0, 0.1);

        assertEquals(2, result.ranked.size());
        // Bogura MUST be ranked #1 for 1 kg:
        // Bogura: Local (0 hops out), transport=0, toll=0 -> Net Profit = 25 * 1 - 15 = +10 BDT.
        // Dhaka: Inter-district (2 hops), dispatch fee=200 BDT -> Net Profit = 35 * 0.995 - 15 - 200 - freight - toll ~ -180 BDT!
        assertEquals("Bogura", result.ranked.get(0).option.marketName);
        assertTrue("Bogura profit should be positive", result.ranked.get(0).option.netProfit > 0);
        assertEquals("Dhaka", result.ranked.get(1).option.marketName);
        assertTrue("Dhaka profit should be negative due to dispatch fee", result.ranked.get(1).option.netProfit < 0);
    }

    @Test
    public void testBulkQuantityFavorsHighPriceDistantHub() {
        // At 2,000 kg (2 tons), the 200 BDT dispatch fee is amortized to 0.10 BDT/kg!
        NetworkSnapshot snap = new NetworkSnapshot(testNodes, testEdges, Collections.emptyList());

        List<PriceEntity> prices = new ArrayList<>();
        PriceEntity pLocal = new PriceEntity();
        pLocal.nodeId = 1; pLocal.crop = "potato"; pLocal.price = 25.0;
        prices.add(pLocal);

        PriceEntity pDhaka = new PriceEntity();
        pDhaka.nodeId = 3; pDhaka.crop = "potato"; pDhaka.price = 35.0;
        prices.add(pDhaka);

        RankingResult result = MarketRanker.rank(snap, prices, 1, 2000.0, 15.0, 0.1);

        assertEquals(2, result.ranked.size());
        // Dhaka MUST be ranked #1 for 2,000 kg:
        // Bogura Local: 25 * 2000 - 15 * 2000 = 20,000 BDT
        // Dhaka: 35 * 1991 - 30000 - (200 + 1000*2) - (250*2) ~ 36,980 BDT (+84% more profit!)
        assertEquals("Dhaka", result.ranked.get(0).option.marketName);
        assertEquals("Bogura", result.ranked.get(1).option.marketName);
        assertTrue("Dhaka profit should heavily beat Bogura",
                result.ranked.get(0).option.netProfit > result.ranked.get(1).option.netProfit);
    }

    @Test
    public void testZeroProductionCostAllowed() {
        MarketOption opt = new MarketOption(1, "Local", 30.0, 100.0, 0.0, 50.0, 0.0, 0.0);
        assertEquals(0.0, opt.prodCostPerKg, 1e-6);
        assertEquals(0.0, opt.productionCost, 1e-6);
        // Net profit = 30 * 100 - 0 - 50 = 2950 BDT
        assertEquals(2950.0, opt.netProfit, 1e-6);
    }

    @Test
    public void testExcessiveProductionCostHandledGracefully() {
        NetworkSnapshot snap = new NetworkSnapshot(testNodes, testEdges, Collections.emptyList());
        List<PriceEntity> prices = new ArrayList<>();
        PriceEntity p1 = new PriceEntity(); p1.nodeId = 1; p1.price = 20.0; prices.add(p1);
        PriceEntity p3 = new PriceEntity(); p3.nodeId = 3; p3.price = 25.0; prices.add(p3);

        // Farmer spent 50 BDT/kg (severe loss scenario)
        RankingResult result = MarketRanker.rank(snap, prices, 1, 1000.0, 50.0, 0.0);
        // All options yield negative profit, but ranker should not crash and should sort by least negative
        assertTrue(result.ranked.get(0).option.netProfit < 0);
        assertTrue(result.ranked.get(1).option.netProfit < 0);
        assertTrue(result.ranked.get(0).option.netProfit >= result.ranked.get(1).option.netProfit);
    }

    @Test
    public void testWeatherRainPenaltyDoublesTravelTimeAndIncreasesCosts() {
        // Clear snapshot (0 rain)
        NetworkSnapshot snapClear = new NetworkSnapshot(testNodes, testEdges, Collections.emptyList());

        // Monsoon snapshot (20 mm rain at each node)
        List<WeatherEntity> monsoon = new ArrayList<>();
        for (NodeEntity n : testNodes) {
            WeatherEntity w = new WeatherEntity();
            w.nodeId = n.id;
            w.rainMm = 20.0;
            monsoon.add(w);
        }
        // Need fresh edges because NetworkSnapshot mutates in-memory EdgeEntity
        List<EdgeEntity> monsoonEdges = new ArrayList<>();
        for (EdgeEntity e : testEdges) {
            EdgeEntity copy = new EdgeEntity();
            copy.fromId = e.fromId; copy.toId = e.toId; copy.km = e.km;
            copy.minutes = e.minutes; copy.cost = e.cost; copy.toll = e.toll;
            monsoonEdges.add(copy);
        }
        NetworkSnapshot snapMonsoon = new NetworkSnapshot(testNodes, monsoonEdges, monsoon);

        assertTrue(snapMonsoon.weatherAdjusted);
        assertEquals(20.0, snapMonsoon.maxRainMm, 1e-6);

        // Check edge between Bogura and Sirajganj (originally 90 mins, cost 350)
        EdgeEntity clearEdge = snapClear.edge(1, 2);
        EdgeEntity wetEdge = snapMonsoon.edge(1, 2);

        // RAIN_K = 0.05, r = 20 -> factor = min(2.0, 1 + 0.05 * 20) = 2.0
        // Minutes should double from 90 to 180
        assertEquals(180, wetEdge.minutes);
        // Cost factor = min(1.5, 1 + 0.02 * 20) = 1.4
        // Cost should increase from 350 to 350 * 1.4 = 490
        assertEquals(490, Math.round(wetEdge.cost));

        // Consequently, ranking with perishable crop (Tomato 1.5%/hr) should suffer higher spoilage & lower profit
        List<PriceEntity> prices = new ArrayList<>();
        PriceEntity p = new PriceEntity(); p.nodeId = 3; p.price = 50.0; prices.add(p);

        RankingResult rClear = MarketRanker.rank(snapClear, prices, 1, 1000.0, 20.0, 1.5);
        RankingResult rMonsoon = MarketRanker.rank(snapMonsoon, prices, 1, 1000.0, 20.0, 1.5);

        double profitClear = rClear.ranked.get(0).option.netProfit;
        double profitMonsoon = rMonsoon.ranked.get(0).option.netProfit;

        assertTrue("Monsoon net profit should be noticeably lower than clear weather", profitClear > profitMonsoon);
    }

    @Test
    public void testBangladeshFull64DistrictHighwayConnectivity() throws Exception {
        // Read the actual assets/network.json used by the app
        String jsonPath = "src/main/assets/network.json";
        if (!java.nio.file.Files.exists(Paths.get(jsonPath))) {
            jsonPath = "app/src/main/assets/network.json";
        }
        String content = new String(Files.readAllBytes(Paths.get(jsonPath)), StandardCharsets.UTF_8);
        NetworkJson data = new Gson().fromJson(content, NetworkJson.class);

        assertNotNull(data);
        assertEquals(64, data.nodes.size());
        assertTrue("Highway edges should be substantial (>= 180)", data.edges.size() >= 180);

        List<NodeEntity> nodes = new ArrayList<>();
        for (NetworkJson.Node n : data.nodes) {
            NodeEntity e = new NodeEntity();
            e.id = n.id;
            e.name = n.name;
            nodes.add(e);
        }
        List<EdgeEntity> edges = new ArrayList<>();
        for (NetworkJson.Edge n : data.edges) {
            EdgeEntity e = new EdgeEntity();
            e.fromId = n.from;
            e.toId = n.to;
            e.km = n.km;
            e.minutes = n.minutes;
            e.cost = n.cost;
            e.toll = n.toll;
            edges.add(e);
        }

        NetworkSnapshot snap = new NetworkSnapshot(nodes, edges, Collections.emptyList());

        // Test reachability from major farming origins across all 8 administrative divisions:
        // 1: Dhaka, 3: Gazipur, 10: Bogura, 14: Chattogram, 26: Khulna, 34: Rajshahi,
        // 38: Rangpur, 44: Sylhet, 52: Barishal, 60: Mymensingh
        int[] hubs = {1, 3, 10, 14, 26, 34, 38, 44, 52, 60};

        for (int hubId : hubs) {
            Integer hubIdx = snap.idToIndex.get(hubId);
            assertNotNull("Hub id " + hubId + " must exist", hubIdx);
            PathResult paths = Dijkstra.run(snap.graph, hubIdx);

            for (NodeEntity target : snap.nodes) {
                Integer targetIdx = snap.idToIndex.get(target.id);
                assertTrue("District " + target.name + " (id=" + target.id + ") must be reachable from hub id=" + hubId,
                        paths.isReachable(targetIdx));
                assertTrue("Distance must be non-negative", paths.dist[targetIdx] >= 0);
            }
        }
    }
}
