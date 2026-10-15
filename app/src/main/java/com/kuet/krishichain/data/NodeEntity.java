package com.kuet.krishichain.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** One place: village, collection hub or market. */
@Entity(tableName = "nodes")
public class NodeEntity {
    @PrimaryKey
    public int id;
    public String name;
    public String type; // "village", "hub" or "market"
    public double lat;
    public double lon;

    /** Used by Spinners to show the place name. */
    @Override
    public String toString() {
        return name;
    }
}