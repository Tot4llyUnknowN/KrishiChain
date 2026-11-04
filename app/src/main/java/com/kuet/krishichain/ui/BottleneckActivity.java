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
import com.kuet.krishichain.data.UserPreferences;
import com.kuet.krishichain.databinding.ActivityBottleneckBinding;
import com.kuet.krishichain.domain.DistrictDemand;
import com.kuet.krishichain.domain.NetworkSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class BottleneckActivity extends AppCompatActivity {
    private ActivityBottleneckBinding binding;
    private NetworkSnapshot snap;
    private final List<Integer> districtNodeIndices = new ArrayList<>();

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
        TopBarHelper.setup(this, binding.getRoot());

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

    @Override
    protected void onResume() {
        super.onResume();
        TopBarHelper.setup(this, binding.getRoot());
    }

    private void onNetworkReady(NetworkSnapshot s) {
        snap = s;
        GraphBinder.bind(binding.graphView, s);

        List<String> items = new ArrayList<>();
        items.add("All Districts (National Network)");
        districtNodeIndices.clear();

        int savedOriginId = UserPreferences.getOriginId(this, 1);
        int initialPos = 0;

        for (int i = 0; i < s.nodes.size(); i++) {
            districtNodeIndices.add(i);
            items.add(s.nodes.get(i).name);
            if (s.nodes.get(i).id == savedOriginId) {
                initialPos = i + 1; // 1-based index after "All Districts"
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, R.layout.item_spinner_selected, items);
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        binding.spinnerCluster.setAdapter(adapter);

        if (initialPos > 0 && initialPos < items.size()) {
            binding.spinnerCluster.setSelection(initialPos);
        } else {
            binding.spinnerCluster.setSelection(0);
        }

        binding.spinnerCluster.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
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
        if (vertex == n) return "National Supply";
        if (vertex == n + 1) return "Terminal Demand";
        return snap.nodes.get(vertex).name;
    }

    private void run() {
        if (snap == null) return;
        int n = snap.nodes.size();
        int superSource = n;
        int superSink = n + 1;
        MaxFlow flow = new MaxFlow(n + 2);

        // Add all highway segments with capacity
        for (EdgeEntity e : snap.edges()) {
            Integer u = snap.idToIndex.get(e.fromId);
            Integer v = snap.idToIndex.get(e.toId);
            if (u != null && v != null) {
                double cap = (e.capacity > 0) ? e.capacity : 100.0;
                flow.addEdge(u, v, cap);
            }
        }

        int choice = binding.spinnerCluster.getSelectedItemPosition();
        if (choice < 0) choice = 0;

        if (choice == 0) {
            // National Agricultural Network:
            // Consuming districts absorb freight based on their individual DistrictDemand across all 8 divisions
            Set<Integer> consumerDistrictIds = new HashSet<>(Arrays.asList(
                    1, 2, 3, 8, 9, 14, 15, 16, 18, 25, 28, 35, 36, 43, 51, 55, 59
            ));
            for (int i = 0; i < n; i++) {
                int id = snap.nodes.get(i).id;
                if (consumerDistrictIds.contains(id)) {
                    double demand = DistrictDemand.getDemandTons(id);
                    flow.addEdge(i, superSink, demand);
                }
            }
            // Agricultural producing districts supply freight into the national highway grid
            for (int i = 0; i < n; i++) {
                int id = snap.nodes.get(i).id;
                if (!consumerDistrictIds.contains(id)) {
                    flow.addEdge(superSource, i, 75.0);
                } else if (id == 16 || id == 8 || id == 9) {
                    // Regional production hubs also output surplus
                    flow.addEdge(superSource, i, 50.0);
                }
            }
        } else {
            // Specific district supply source selected (choice - 1)
            int srcIndex = choice - 1;
            flow.addEdge(superSource, srcIndex, 2500.0); // Selected hub outputs up to 2500 tons/day into national highway grid

            // All other 63 districts absorb freight based on their individual DistrictDemand
            for (int i = 0; i < n; i++) {
                if (i != srcIndex) {
                    int destId = snap.nodes.get(i).id;
                    double demand = DistrictDemand.getDemandTons(destId);
                    flow.addEdge(i, superSink, demand);
                }
            }
        }

        FlowResult r = flow.run(superSource, superSink);

        List<int[]> cutPairs = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (FlowEdge e : r.cutEdges) {
            if (e.from < n && e.to < n) {
                sb.append("🚧 ").append(nameOf(e.from)).append(" → ").append(nameOf(e.to))
                        .append(String.format(Locale.US, " (%.0f/%.0f tons/day - Saturated Bottleneck)\n", e.capacity, e.capacity));
                cutPairs.add(new int[]{e.from, e.to});
            }
        }
        if (sb.length() == 0) {
            sb.append("No bottleneck highway corridors found; network easily carries total freight.");
        }

        binding.textFlowInfo.setText(String.format(Locale.US, "Max Freight Flow: %.0f tons/day", r.maxFlow));
        binding.textCut.setText(sb.toString().trim());
        binding.graphView.loadSteps(r.steps, false, -1, null);
        binding.graphView.setCut(cutPairs);
        binding.graphView.play();
    }
}
