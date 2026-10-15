package com.kuet.krishichain.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Copies assets/network.json into Room.
 * It remembers a hash of the file, so when you rebuild network.json (for example with real prices)
 * the database is refreshed automatically on the next launch.
 */
public final class NetworkLoader {
    private static final String PREFS = "krishichain_prefs";
    private static final String KEY_HASH = "network_hash";

    private NetworkLoader() { }

    /** Call on a background thread. Returns true if the database was (re)filled. */
    public static boolean loadIfNeeded(Context context) throws IOException {
        Context app = context.getApplicationContext();
        String json = readAsset(app, "network.json");
        int hash = json.hashCode();

        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        AppDatabase db = AppDatabase.get(app);
        final NetworkDao dao = db.networkDao();

        if (prefs.getInt(KEY_HASH, 0) == hash && dao.nodeCount() > 0) {
            return false; // already loaded and unchanged
        }

        NetworkJson data = new Gson().fromJson(json, NetworkJson.class);

        final List<NodeEntity> nodes = new ArrayList<>();
        for (NetworkJson.Node n : data.nodes) {
            NodeEntity e = new NodeEntity();
            e.id = n.id;
            e.name = n.name;
            e.type = n.type;
            e.lat = n.lat;
            e.lon = n.lon;
            nodes.add(e);
        }
        final List<EdgeEntity> edges = new ArrayList<>();
        for (NetworkJson.Edge n : data.edges) {
            EdgeEntity e = new EdgeEntity();
            e.fromId = n.from;
            e.toId = n.to;
            e.km = n.km;
            e.minutes = n.minutes;
            e.cost = n.cost;
            e.credit = n.credit;
            e.netCost = n.netCost;
            e.toll = n.toll;
            e.capacity = n.capacity;
            edges.add(e);
        }
        final List<PriceEntity> prices = new ArrayList<>();
        for (NetworkJson.Price n : data.prices) {
            PriceEntity e = new PriceEntity();
            e.nodeId = n.node;
            e.crop = n.crop;
            e.price = n.price;
            e.source = n.source;
            e.date = n.date;
            prices.add(e);
        }

        // One transaction: either everything is stored or nothing is.
        db.runInTransaction(new Runnable() {
            @Override
            public void run() {
                dao.clearNodes();
                dao.clearEdges();
                dao.clearPrices();
                dao.insertNodes(nodes);
                dao.insertEdges(edges);
                dao.insertPrices(prices);
            }
        });
        prefs.edit().putInt(KEY_HASH, hash).apply();
        return true;
    }

    private static String readAsset(Context context, String name) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(name), StandardCharsets.UTF_8))) {
            char[] buf = new char[8192];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
        }
        return sb.toString();
    }
}