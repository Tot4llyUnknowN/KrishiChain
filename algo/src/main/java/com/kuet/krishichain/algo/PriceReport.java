package com.kuet.krishichain.algo;

/** A crowd-sourced or seeded price report. Date is epoch millis (works on API 24). */
public final class PriceReport {
    public final String crop;
    public final double price;
    public final long dateMillis;
    public final int nodeId;

    public PriceReport(String crop, double price, long dateMillis, int nodeId) {
        this.crop = crop;
        this.price = price;
        this.dateMillis = dateMillis;
        this.nodeId = nodeId;
    }
}