package com.kuet.krishichain.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Latest rain reading for one place. simulated = true for the demo switch. */
@Entity(tableName = "weather")
public class WeatherEntity {
    @PrimaryKey
    public int nodeId;
    public double rainMm;
    public long fetchedAt; // epoch millis
    public boolean simulated;
}