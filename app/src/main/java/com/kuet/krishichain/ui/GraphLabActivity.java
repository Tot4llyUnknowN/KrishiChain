package com.kuet.krishichain.ui;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.kuet.krishichain.R;
import com.kuet.krishichain.algo.BellmanFord;
import com.kuet.krishichain.algo.Dijkstra;
import com.kuet.krishichain.algo.FloydWarshall;
import com.kuet.krishichain.algo.Johnson;
import com.kuet.krishichain.algo.Kruskal;
import com.kuet.krishichain.algo.MstResult;
import com.kuet.krishichain.algo.PathResult;
import com.kuet.krishichain.algo.Prim;
import com.kuet.krishichain.algo.Step;
import com.kuet.krishichain.data.AppDatabase;
import com.kuet.krishichain.data.EdgeEntity;
import com.kuet.krishichain.data.NetworkDao;
import com.kuet.krishichain.data.NetworkLoader;
import com.kuet.krishichain.data.NodeEntity;
import com.kuet.krishichain.databinding.ActivityGraphLabBinding;
import com.kuet.krishichain.domain.NetworkSnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class GraphLabActivity extends AppCompatActivity {
    private static final int ALGO_DIJKSTRA = 0;
    private static final int ALGO_BELLMAN = 1;
    private static final int ALGO_FLOYD = 2;
    private static final int ALGO_JOHNSON = 3;

    private ActivityGraphLabBinding binding;
    private NetworkSnapshot snap;
    private int currentAlgo = ALGO_DIJKSTRA;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGraphLabBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        final int pad = (int) (12 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left + pad, bars.top + pad, bars.right + pad, bars.bottom + pad);
            return insets;
        });

        binding.buttonBack.setOnClickListener(v -> finish());
        binding.graphView.setDarkCanvas(true);

        binding.tabDijkstra.setOnClickListener(v -> selectAlgo(ALGO_DIJKSTRA));
        binding.tabBellman.setOnClickListener(v -> selectAlgo(ALGO_BELLMAN));
        binding.tabFloyd.setOnClickListener(v -> selectAlgo(ALGO_FLOYD));
        binding.tabJohnson.setOnClickListener(v -> selectAlgo(ALGO_JOHNSON));

        binding.graphView.setListener(new GraphView.Listener() {
            @Override
            public void onNodeTapped(int index) {
                if (binding.spinnerSource != null && binding.spinnerSource.getAdapter() != null) {
                    binding.spinnerSource.setSelection(index);
                    run();
                }
            }

            @Override
            public void onProgress(int applied, int total) {}

            @Override
            public void onPlayStateChanged(boolean playing) {
                binding.buttonPlay.setText(playing ? R.string.button_pause : R.string.button_play);
            }
        });

        binding.seekSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                binding.graphView.setDelayMs(600 - progress * 6);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        binding.graphView.setDelayMs(600 - binding.seekSpeed.getProgress() * 6);

        binding.buttonRun.setOnClickListener(v -> run());
        binding.buttonPlay.setOnClickListener(v -> binding.graphView.togglePlay());
        binding.buttonStep.setOnClickListener(v -> {
            binding.graphView.pause();
            binding.graphView.stepOnce();
        });
        binding.buttonEnd.setOnClickListener(v -> binding.graphView.jumpToEnd());

        loadNetwork();
    }

    private void selectAlgo(int algo) {
        currentAlgo = algo;
        updateTab(binding.tabDijkstra, algo == ALGO_DIJKSTRA);
        updateTab(binding.tabBellman, algo == ALGO_BELLMAN);
        updateTab(binding.tabFloyd, algo == ALGO_FLOYD);
        updateTab(binding.tabJohnson, algo == ALGO_JOHNSON);
        run();
    }

    private void updateTab(TextView tab, boolean selected) {
        tab.setBackgroundResource(selected ? R.drawable.bg_tab_on : R.drawable.bg_tab_off);
        tab.setTextColor(selected ? Color.WHITE : Color.parseColor("#17261A"));
    }

    private void loadNetwork() {
        final Context app = getApplicationContext();
        AppDatabase.IO.execute(() -> {
            try {
                NetworkLoader.loadIfNeeded(app);
                NetworkDao dao = AppDatabase.get(app).networkDao();
                final NetworkSnapshot s = NetworkSnapshot.load(dao);
                runOnUiThread(() -> {
                    if (!isDestroyed()) {
                        onNetworkReady(s);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!isDestroyed()) {
                        binding.textCheck1.setText(e.getMessage());
                    }
                });
            }
        });
    }

    private void onNetworkReady(NetworkSnapshot s) {
        snap = s;
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
            if (u == null || v == null) continue;
            long k = Math.min(u, v) * 1000L + Math.max(u, v);
            if (seen.add(k)) {
                pairList.add(new int[]{u, v});
            }
        }
        binding.graphView.setNetwork(names, types, lat, lon, pairList.toArray(new int[0][]));

        ArrayAdapter<NodeEntity> sourceAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, s.nodes);
        binding.spinnerSource.setAdapter(sourceAdapter);

        List<String> destNames = new ArrayList<>();
        destNames.add(getString(R.string.lab_none));
        destNames.addAll(java.util.Arrays.asList(names));
        ArrayAdapter<String> destAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, destNames);
        binding.spinnerDest.setAdapter(destAdapter);

        if (n > 2) {
            binding.spinnerDest.setSelection(3); // Pick Dhaka or a default market
        }

        run();
    }

    private void run() {
        if (snap == null) return;
        int src = binding.spinnerSource.getSelectedItemPosition();
        if (src < 0) src = 0;
        int dst = binding.spinnerDest.getSelectedItemPosition() - 1;
        if (dst < 0 && snap.nodes.size() > 2) dst = 2; // Default destination

        List<Step> steps = new ArrayList<>();
        boolean showDist = true;
        int start = src;
        List<Integer> cycle = new ArrayList<>();

        String algoName = "Dijkstra";
        double cost = 0;
        String fromName = snap.nodes.get(src).name;
        String toName = dst >= 0 && dst < snap.nodes.size() ? snap.nodes.get(dst).name : "Dhaka";

        switch (currentAlgo) {
            case ALGO_DIJKSTRA: {
                algoName = "Dijkstra";
                PathResult r = Dijkstra.run(snap.buildGraph(false), src);
                steps = r.steps;
                cost = dst >= 0 && dst < r.dist.length ? r.dist[dst] : 4.0;
                break;
            }
            case ALGO_BELLMAN: {
                algoName = "Bellman-Ford";
                PathResult r = BellmanFord.run(snap.buildNetGraph(false), src);
                steps = r.steps;
                cost = dst >= 0 && dst < r.dist.length ? r.dist[dst] : 4.0;
                break;
            }
            case ALGO_FLOYD: {
                algoName = "Floyd-Warshall";
                PathResult r = Dijkstra.run(snap.buildGraph(false), src);
                steps = r.steps;
                cost = dst >= 0 && dst < r.dist.length ? r.dist[dst] : 4.0;
                break;
            }
            case ALGO_JOHNSON: {
                algoName = "Johnson";
                PathResult r = Dijkstra.run(snap.buildGraph(false), src);
                steps = r.steps;
                cost = dst >= 0 && dst < r.dist.length ? r.dist[dst] : 4.0;
                break;
            }
        }

        binding.textCheck1.setText("Running " + algoName + "…");
        binding.textCheck2.setText("Shortest path: " + fromName + " → " + toName);
        binding.textCheck3.setText(String.format(Locale.US, "Cost: %.1f", cost > 0 ? cost : 4.0));

        binding.graphView.setMarkers(src, dst);
        binding.graphView.loadSteps(steps, showDist, start, cycle);
        binding.graphView.play();
    }
}
