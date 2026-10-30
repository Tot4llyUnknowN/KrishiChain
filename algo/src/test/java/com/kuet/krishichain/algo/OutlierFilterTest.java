package com.kuet.krishichain.algo;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OutlierFilterTest {

    @Test
    public void testRejectsExtremePriceOutliers() {
        List<PriceReport> reports = new ArrayList<>();
        // Market 1: Potato normal prices around 30
        reports.add(new PriceReport("potato", 28.0, 1000, 1));
        reports.add(new PriceReport("potato", 30.0, 1001, 1));
        reports.add(new PriceReport("potato", 32.0, 1002, 1));
        reports.add(new PriceReport("potato", 31.0, 1003, 1));
        // Fake / spam prices:
        reports.add(new PriceReport("potato", 100.0, 1004, 1)); // way too high
        reports.add(new PriceReport("potato", 5.0, 1005, 1));   // way too low

        OutlierFilter filter = new OutlierFilter(0.40); // 40% threshold
        OutlierFilter.FilterResult result = filter.filter(reports);

        assertEquals(4, result.validReports.size());
        assertEquals(2, result.rejectedReports.size());

        for (PriceReport valid : result.validReports) {
            assertFalse(valid.isOutlier);
            assertTrue(valid.price >= 20.0 && valid.price <= 45.0);
        }

        for (PriceReport rejected : result.rejectedReports) {
            assertTrue(rejected.isOutlier);
            assertTrue(rejected.price > 50.0 || rejected.price < 10.0);
        }
    }

    @Test
    public void testSmallSampleKept() {
        List<PriceReport> reports = new ArrayList<>();
        reports.add(new PriceReport("tomato", 40.0, 1000, 2));
        reports.add(new PriceReport("tomato", 60.0, 1001, 2));

        OutlierFilter filter = new OutlierFilter(0.40);
        OutlierFilter.FilterResult result = filter.filter(reports);

        // Less than 3 samples -> keep all
        assertEquals(2, result.validReports.size());
        assertEquals(0, result.rejectedReports.size());
    }
}
