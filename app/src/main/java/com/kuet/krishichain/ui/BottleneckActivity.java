package com.kuet.krishichain.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.kuet.krishichain.R;
import com.kuet.krishichain.algo.FlowEdge;
import com.kuet.krishichain.algo.FlowResult;
import com.kuet.krishichain.algo.MaxFlow;
import com.kuet.krishichain.data.AppDatabase;
import com.kuet.krishichain.data.EdgeEntity;
import com.kuet.krishichain.data.NetworkDao;
import com.kuet.krishichain.data.NetworkLoader;
import com.kuet.krishichain.databinding.ActivityBottleneckBinding;
import com.kuet.krishichain.domain.NetworkSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BottleneckActivity extends AppCompatActivity {
    private static final double SOURCE_CAP = 1000.0;
    private static final double MARKET_DEMAND = 150.0;

    private ActivityBottleneckBinding binding;
    private NetworkSnapshot snap;
    private final List<Integer> villages = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBottleneckBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        final int pad = (int) (12 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left + pad, bars.top + pad, bars.right + pad, bars.bottom + pad);
            return insets;
        });

        binding.buttonBack.setOnClickListener(v -> finish());
        binding.cardCluster.setOnClickListener(v -> binding.spinnerCluster.performClick());

        binding.graphView.setListener(new GraphView.Listener() {
            @Override
            public void onNodeTapped(int index) {}

            @Override
            public void onProgress(int applied, int total) {}

            @Override
            public void onPlayStateChanged(boolean playing) {
                binding.buttonPlay.setText(playing ? R.string.button_pause : R.string.button_play);
            }
        });

        binding.buttonRun.setOnClickListener(v -> run());
        binding.buttonPlay.setOnClickListener(v -> binding.graphView.togglePlay());
        binding.buttonStep.setOnClickListener(v -> {
            binding.graphView.pause();
            binding.graphView.stepOnce();
        });
        binding.buttonEnd.setOnClickListener(v -> binding.graphView.jumpToEnd());

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
                        binding.textFlowInfo.setText(e.getMessage());
                    }
                });
            }
        });
    }

    private void onNetworkReady(NetworkSnapshot s) {
        snap = s;
        GraphBinder.bind(binding.graphView, s);

        List<String> items = new ArrayList<>();
        items.add(getString(R.string.bn_all));
        villages.clear();
        for (int i = 0; i < s.nodes.size(); i++) {
            if ("village".equals(s.nodes.get(i).type)) {
                villages.add(i);
                items.add(s.nodes.get(i).name);
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerCluster.setAdapter(adapter);
        binding.spinnerCluster.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                binding.textClusterDisplay.setText(items.get(position) + " ▾");
                run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        binding.buttonRun.setEnabled(true);
        binding.buttonPlay.setEnabled(true);
        binding.buttonStep.setEnabled(true);
        binding.buttonEnd.setEnabled(true);
        run();
    }

    private String nameOf(int vertex) {
        int n = snap.nodes.size();
        if (vertex == n) return getString(R.string.bn_farms);
        if (vertex == n + 1) return getString(R.string.bn_demand);
        return snap.nodes.get(vertex).name;
    }

    private void run() {
        if (snap == null) return;
        int n = snap.nodes.size();
        int superSource = n;
        int superSink = n + 1;
        MaxFlow flow = new MaxFlow(n + 2);

        for (EdgeEntity e : snap.edges()) {
            Integer u = snap.idToIndex.get(e.fromId);
            Integer v = snap.idToIndex.get(e.toId);
            if (u != null && v != null) {
                flow.addEdge(u, v, e.capacity);
            }
        }
        int choice = binding.spinnerCluster.getSelectedItemPosition();
        if (choice < 0) choice = 0;
        for (int i = 0; i < villages.size(); i++) {
            if (choice == 0 || choice - 1 == i) {
                flow.addEdge(superSource, villages.get(i), SOURCE_CAP);
            }
        }
        for (int i = 0; i < n; i++) {
            if ("market".equals(snap.nodes.get(i).type)) {
                flow.addEdge(i, superSink, MARKET_DEMAND);
            }
        }

        FlowResult r = flow.run(superSource, superSink);

        List<int[]> cutPairs = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (FlowEdge e : r.cutEdges) {
            sb.append(nameOf(e.from)).append(" → ").append(nameOf(e.to))
                    .append(String.format(Locale.US, " (%.0f/%.0f, full)\n", e.capacity, e.capacity));
            if (e.from < n && e.to < n) {
                cutPairs.add(new int[]{e.from, e.to});
            }
        }
        if (sb.length() == 0) {
            sb.append("No bottleneck edges found for this cluster.\n");
        }

        binding.textFlowInfo.setText(String.format(Locale.US, "Max flow: %.0f t/day", r.maxFlow));
        binding.textCut.setText(sb.toString().trim());
        binding.graphView.loadSteps(r.steps, false, -1, null);
        binding.graphView.setCut(cutPairs);
        binding.graphView.play();
    }
}
