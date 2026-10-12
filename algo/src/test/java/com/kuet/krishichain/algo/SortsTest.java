package com.kuet.krishichain.algo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.junit.Test;

public class SortsTest {

    private static final Comparator<Integer> INT_CMP = new Comparator<Integer>() {
        @Override
        public int compare(Integer a, Integer b) {
            return Integer.compare(a, b);
        }
    };

    private List<Integer> randomList(int n, long seed) {
        Random r = new Random(seed);
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(r.nextInt(50)); // many duplicates on purpose
        }
        return list;
    }

    @Test
    public void quickSortMatchesCollectionsSort() {
        for (int n : new int[]{0, 1, 2, 3, 10, 100, 1000}) {
            List<Integer> mine = randomList(n, n);
            List<Integer> expected = new ArrayList<>(mine);
            Collections.sort(expected);
            Sorts.quickSort(mine, INT_CMP);
            assertEquals(expected, mine);
        }
    }

    @Test
    public void mergeSortMatchesCollectionsSort() {
        for (int n : new int[]{0, 1, 2, 3, 10, 100, 1000}) {
            List<Integer> mine = randomList(n, n + 7);
            List<Integer> expected = new ArrayList<>(mine);
            Collections.sort(expected);
            Sorts.mergeSort(mine, INT_CMP);
            assertEquals(expected, mine);
        }
    }

    @Test
    public void quickSortHandlesAlreadySortedAndReversed() {
        List<Integer> asc = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            asc.add(i);
        }
        List<Integer> desc = new ArrayList<>(asc);
        Collections.reverse(desc);
        Sorts.quickSort(asc, INT_CMP);
        Sorts.quickSort(desc, INT_CMP);
        for (int i = 0; i < 5000; i++) {
            assertEquals(i, (int) asc.get(i));
            assertEquals(i, (int) desc.get(i));
        }
    }

    @Test
    public void marketsRankedByNetProfitDescending() {
        List<MarketOption> list = new ArrayList<>();
        // price/kg, qty kg, transport, toll, spoilage
        list.add(new MarketOption(1, "Near", 20, 1000, 500, 0, 0.00));    // 19500
        list.add(new MarketOption(2, "Far", 28, 1000, 3000, 200, 0.05));  // 23400
        list.add(new MarketOption(3, "Mid", 25, 1000, 1500, 100, 0.02));  // 22900
        Sorts.quickSort(list, Sorts.byNetProfitDesc());
        assertEquals("Far", list.get(0).marketName);
        assertEquals("Mid", list.get(1).marketName);
        assertEquals("Near", list.get(2).marketName);
        assertEquals(23400, list.get(0).netProfit, 1e-6);
    }

    @Test
    public void mergeSortIsStable() {
        List<PriceReport> list = new ArrayList<>();
        list.add(new PriceReport("potato", 30, 1, 10));
        list.add(new PriceReport("onion", 60, 2, 11));
        list.add(new PriceReport("potato", 25, 3, 12));
        list.add(new PriceReport("onion", 55, 4, 13));
        list.add(new PriceReport("potato", 28, 5, 14));
        Sorts.mergeSort(list, Sorts.byCropOnly());
        // Within each crop, the ORIGINAL order must be kept.
        assertEquals(11, list.get(0).nodeId);
        assertEquals(13, list.get(1).nodeId);
        assertEquals(10, list.get(2).nodeId);
        assertEquals(12, list.get(3).nodeId);
        assertEquals(14, list.get(4).nodeId);
    }

    @Test
    public void multiKeySortCropThenPriceThenDate() {
        List<PriceReport> list = new ArrayList<>();
        list.add(new PriceReport("potato", 28, 300, 1));
        list.add(new PriceReport("onion", 60, 100, 2));
        list.add(new PriceReport("potato", 28, 100, 3));
        list.add(new PriceReport("potato", 25, 500, 4));
        Sorts.mergeSort(list, Sorts.byCropPriceDate());
        assertEquals(2, list.get(0).nodeId); // onion
        assertEquals(4, list.get(1).nodeId); // potato 25
        assertEquals(3, list.get(2).nodeId); // potato 28, older
        assertEquals(1, list.get(3).nodeId); // potato 28, newer
        assertTrue(list.size() == 4);
    }
}