package com.kuet.krishichain.domain;

import java.util.HashMap;
import java.util.Map;

/**
 * Daily agricultural wholesale demand capacity (in metric tons/day) for Bangladesh district hubs.
 * Based on national urban population tiers, consumption scale, and wholesale market size.
 */
public final class DistrictDemand {

    private static final Map<Integer, Double> DEMAND_BY_ID = new HashMap<>();

    static {
        // Tier 1: Megacity & Principal National Wholesale Hubs (Kawran Bazar, Khatunganj, etc.)
        DEMAND_BY_ID.put(1, 250.0);   // Dhaka
        DEMAND_BY_ID.put(14, 180.0);  // Chattogram

        // Tier 2: Divisional Headquarters & Major Metro Hubs
        DEMAND_BY_ID.put(25, 120.0);  // Khulna
        DEMAND_BY_ID.put(35, 120.0);  // Rajshahi
        DEMAND_BY_ID.put(55, 110.0);  // Sylhet
        DEMAND_BY_ID.put(43, 110.0);  // Rangpur
        DEMAND_BY_ID.put(51, 100.0);  // Mymensingh
        DEMAND_BY_ID.put(59, 90.0);   // Barishal

        // Tier 3: High-Volume Industrial & Regional Supply Hubs
        DEMAND_BY_ID.put(2, 100.0);   // Gazipur (Tongi / Joydebpur)
        DEMAND_BY_ID.put(3, 90.0);    // Narayanganj
        DEMAND_BY_ID.put(16, 90.0);   // Cumilla
        DEMAND_BY_ID.put(36, 90.0);   // Bogura
        DEMAND_BY_ID.put(28, 85.0);   // Jashore
        DEMAND_BY_ID.put(4, 80.0);    // Tangail
        DEMAND_BY_ID.put(38, 75.0);   // Sirajganj
        DEMAND_BY_ID.put(18, 75.0);   // Brahmanbaria
        DEMAND_BY_ID.put(8, 70.0);    // Narsingdi
        DEMAND_BY_ID.put(37, 70.0);   // Pabna
        DEMAND_BY_ID.put(44, 70.0);   // Dinajpur
        DEMAND_BY_ID.put(15, 70.0);   // Cox's Bazar
        DEMAND_BY_ID.put(32, 65.0);   // Kushtia
        DEMAND_BY_ID.put(9, 65.0);    // Faridpur
        DEMAND_BY_ID.put(5, 65.0);    // Kishoreganj
        DEMAND_BY_ID.put(52, 65.0);   // Jamalpur
        DEMAND_BY_ID.put(56, 65.0);   // Moulvibazar

        // Tier 4: Other District Hubs default to 50.0 tons/day
    }

    private DistrictDemand() {}

    /**
     * Returns daily demand capacity in metric tons.
     */
    public static double getDemandTons(int nodeId) {
        Double d = DEMAND_BY_ID.get(nodeId);
        return (d != null) ? d : 50.0;
    }

    /**
     * Returns daily demand capacity in kilograms (tons * 1000).
     */
    public static double getDemandKg(int nodeId) {
        return getDemandTons(nodeId) * 1000.0;
    }
}
