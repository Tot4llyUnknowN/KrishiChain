package com.kuet.krishichain.ui;

import android.content.Context;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.kuet.krishichain.R;
import com.kuet.krishichain.algo.Edge;
import com.kuet.krishichain.algo.Graph;
import com.kuet.krishichain.algo.Kruskal;
import com.kuet.krishichain.algo.MstResult;
import com.kuet.krishichain.algo.Prim;
import com.kuet.krishichain.data.AppDatabase;
import com.kuet.krishichain.data.EdgeEntity;
import com.kuet.krishichain.data.NetworkDao;
import com.kuet.krishichain.data.NetworkLoader;
import com.kuet.krishichain.databinding.ActivityPlannerBinding;
import com.kuet.krishichain.domain.NetworkSnapshot;

import java.util.HashSet;
import java.util.Set;

public class PlannerActivity extends AppCompatActivity {
    private static final int TIMING_REPS = 300;

    private ActivityPlannerBinding binding;
    private NetworkSnapshot snap;
    private Graph roads;           // one undirected road per pair, weight = km
    private double allRoadsKm;
    private int roadCount;
    private int primStart = 0;
    private MstResult kruskal;
    private MstResult prim;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlannerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        final int pad = (int) (12 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left + pad, bars.top + pad, bars.right + pad, bars.bottom + pad);
            return insets;
        });

        binding.graphView.setDelayMs(150);
        binding.graphView.setListener(new GraphView.Listener() {
            @Override
            public void onNodeTapped(int index) {
                if (roads == null) {
                    return;
                }
                primStart = index; // tap = choose Prim's starting hub
                binding.radioPrim.setChecked(true);
                compute();
                startReplay();
            }

            @Override
            public void onProgress(int applied, int total) {
                binding.textProgress.setText(getString(R.string.lab_progress, applied, total));
            }

            @Override
            public void onPlayStateChanged(boolean playing) {
                binding.buttonPlay.setText(playing ? R.string.button_pause : R.string.button_play);
            }
        });

        binding.radioAlgo.setOnCheckedChangeListener((group, id) -> {
            if (roads != null) {
                startReplay();
            }
        });
        binding.buttonReplay.setOnClickListener(v -> startReplay());
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
                        binding.textCompare.setText(e.getMessage());
                    }
                });
            }
        });
    }

    private void onNetworkReady(NetworkSnapshot s) {
        snap = s;
        GraphBinder.bind(binding.graphView, s);

        // One undirected road per pair, weighted by km.
        roads = new Graph(s.nodes.size());
        Set<Long> seen = new HashSet<>();
        for (EdgeEntity e : s.edges()) {
            Integer u = s.idToIndex.get(e.fromId);
            Integer v = s.idToIndex.get(e.toId);
            if (u == null || v == null) {
                continue;
            }
            if (seen.add(Math.min(u, v) * 1000L + Math.max(u, v))) {
                roads.addEdge(u, v, e.km);
                allRoadsKm += e.km;
                roadCount++;
            }
        }
        for (int i = 0; i < s.nodes.size(); i++) {
            if ("Khulna".equals(s.nodes.get(i).name)) {
                primStart = i;
            }
        }

        binding.buttonReplay.setEnabled(true);
        binding.buttonPlay.setEnabled(true);
        binding.buttonStep.setEnabled(true);
        binding.buttonEnd.setEnabled(true);
        compute();
        startReplay();
    }

    private static Set<Long> keys(MstResult r) {
        Set<Long> set = new HashSet<>();
        for (Edge e : r.edges) {
            set.add(Math.min(e.from, e.to) * 1000L + Math.max(e.from, e.to));
        }
        return set;
    }

    /** Runs both algorithms, times them, and writes the comparison. */
    private void compute() {
        kruskal = Kruskal.run(roads);
        prim = Prim.run(roads, primStart);

        long t0 = System.nanoTime();
        for (int i = 0; i < TIMING_REPS; i++) {
            Kruskal.run(roads);
        }
        double kUs = (System.nanoTime() - t0) / 1000.0 / TIMING_REPS;
        t0 = System.nanoTime();
        for (int i = 0; i < TIMING_REPS; i++) {
            Prim.run(roads, primStart);
        }
        double pUs = (System.nanoTime() - t0) / 1000.0 / TIMING_REPS;

        double saving = allRoadsKm <= 0 ? 0
                : (allRoadsKm - kruskal.totalCost) / allRoadsKm * 100.0;
        String same = getString(keys(kruskal).equals(keys(prim))
                ? R.string.planner_same : R.string.planner_diff);

        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.planner_summary, roadCount, allRoadsKm)).append('\n');
        sb.append(getString(R.string.planner_k, kruskal.edges.size(), kruskal.totalCost,
                kruskal.operations, kUs)).append('\n');
        sb.append(getString(R.string.planner_p, snap.nodes.get(primStart).name,
                prim.edges.size(), prim.totalCost, prim.operations, pUs)).append('\n');
        sb.append(getString(R.string.planner_saving, saving, same)).append("\n\n");
        sb.append(getString(R.string.planner_note));
        binding.textCompare.setText(sb.toString());
    }

    private void startReplay() {
        if (kruskal == null) {
            return;
        }
        if (binding.radioPrim.isChecked()) {
            binding.graphView.loadSteps(prim.steps, false, primStart, null);
        } else {
            binding.graphView.loadSteps(kruskal.steps, false, -1, null);
        }
        binding.graphView.play();
    }
}