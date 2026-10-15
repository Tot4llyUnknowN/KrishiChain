package com.kuet.krishichain.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** One directed road arc between two places. */
@Entity(tableName = "edges")
public class EdgeEntity {
    @PrimaryKey(autoGenerate = true)
    public long pk;
    public int fromId;
    public int toId;
    public double km;
    public int minutes;
    public double cost;     // transport cost per ton (BDT)
    public double credit;   // backhaul credit per ton (BDT)
    public double netCost;  // cost + toll - credit (may be negative)
    public double toll;
    public int capacity;    // tons per day
}