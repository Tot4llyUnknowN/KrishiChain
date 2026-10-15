package com.kuet.krishichain.data;

/** Spoilage behaviour of one crop, read from res/xml/crops.xml. */
public class CropInfo {
    public final String key;
    public final double spoilagePctPerHour; // percent of the load lost per hour of travel

    public CropInfo(String key, double spoilagePctPerHour) {
        this.key = key;
        this.spoilagePctPerHour = spoilagePctPerHour;
    }
}