package com.kuet.krishichain.algo;

/** A crowd-sourced or seeded price report. Date is epoch millis (works on API 24). */
public final class PriceReport {
    public final String crop;
    public final double price;
    public final long dateMillis;
    public final int nodeId;
    public final String reporterId;
    public boolean isOutlier = false;

    public PriceReport(String crop, double price, long dateMillis, int nodeId) {
        this(crop, price, dateMillis, nodeId, "");
    }

    public PriceReport(String crop, double price, long dateMillis, int nodeId, String reporterId) {
        this.crop = crop;
        this.price = price;
        this.dateMillis = dateMillis;
        this.nodeId = nodeId;
        this.reporterId = reporterId == null ? "" : reporterId;
    }
}
