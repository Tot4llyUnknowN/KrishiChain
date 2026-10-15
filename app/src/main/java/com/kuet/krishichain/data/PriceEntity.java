package com.kuet.krishichain.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Price of one crop at one place (BDT per kg). */
@Entity(tableName = "prices")
public class PriceEntity {
    @PrimaryKey(autoGenerate = true)
    public long pk;
    public int nodeId;
    public String crop; // lowercase: "potato", "tomato", "onion"
    public double price;
    public String source; // "DUMMY", "DAM bulletin", ...
    public String date;
}