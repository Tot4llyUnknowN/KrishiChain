package com.kuet.krishichain.data;

import java.util.List;

/** Mirrors network.json. Fields we don't declare (like meta.assumptions) are ignored by Gson. */
public class NetworkJson {
    public Meta meta;
    public List<Node> nodes;
    public List<Edge> edges;
    public List<Price> prices;

    public static class Meta {
        public boolean demoPrices;
        public String distanceSource;
    }

    public static class Node {
        public int id;
        public String name;
        public String type;
        public double lat;
        public double lon;
    }

    public static class Edge {
        public int from;
        public int to;
        public double km;
        public int minutes;
        public double cost;
        public double credit;
        public double netCost;
        public double toll;
        public int capacity;
    }

    public static class Price {
        public int node;
        public String crop;
        public double price;
        public String source;
        public String date;
    }
}