package com.kuet.krishichain.ui;

import com.kuet.krishichain.data.EdgeEntity;
import com.kuet.krishichain.data.NodeEntity;
import com.kuet.krishichain.domain.NetworkSnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Feeds a NetworkSnapshot into a GraphView (one undirected line per road pair). */
public final class GraphBinder {
    private GraphBinder() { }

    public static void bind(GraphView view, NetworkSnapshot s) {
        int n = s.nodes.size();
        String[] names = new String[n];
        String[] types = new String[n];
        double[] lat = new double[n];
        double[] lon = new double[n];
        for (int i = 0; i < n; i++) {
            NodeEntity node = s.nodes.get(i);
            names[i] = node.name;
            types[i] = node.type;
            lat[i] = node.lat;
            lon[i] = node.lon;
        }
        List<int[]> pairList = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (EdgeEntity e : s.edges()) {
            Integer u = s.idToIndex.get(e.fromId);
            Integer v = s.idToIndex.get(e.toId);
            if (u == null || v == null) {
                continue;
            }
            if (seen.add(Math.min(u, v) * 1000L + Math.max(u, v))) {
                pairList.add(new int[]{u, v});
            }
        }
        view.setNetwork(names, types, lat, lon, pairList.toArray(new int[0][]));
    }
}