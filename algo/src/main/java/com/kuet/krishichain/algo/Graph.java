package com.kuet.krishichain.algo;

import java.util.ArrayList;
import java.util.List;

/** Directed graph with adjacency lists. Vertices are 0..n-1. */
public class Graph {
    private final int n;
    private final List<List<Edge>> adj = new ArrayList<>();
    private final List<Edge> all = new ArrayList<>();

    public Graph(int n) {
        this.n = n;
        for (int i = 0; i < n; i++) {
            adj.add(new ArrayList<Edge>());
        }
    }

    public int size() {
        return n;
    }

    public void addEdge(int u, int v, double w) {
        Edge e = new Edge(u, v, w);
        adj.get(u).add(e);
        all.add(e);
    }

    /** Adds two arcs with the same weight. Never use with negative weights. */
    public void addUndirectedEdge(int u, int v, double w) {
        addEdge(u, v, w);
        addEdge(v, u, w);
    }

    public List<Edge> edgesFrom(int u) {
        return adj.get(u);
    }

    public List<Edge> allEdges() {
        return all;
    }
}