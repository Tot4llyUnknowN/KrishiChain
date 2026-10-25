package com.kuet.krishichain.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.kuet.krishichain.R;
import com.kuet.krishichain.algo.BellmanFord;
import com.kuet.krishichain.algo.Dijkstra;
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
import java.util.Set;

public class GraphLabActivity extends AppCompatActivity {
    private static final int ALGO_DIJKSTRA = 0;
    private static final int ALGO_BELLMAN = 1;
    private static final int ALGO_NEGATIVE_CYCLE = 2;
    private static final int ALGO_KRUSKAL = 3;
    private static final int ALGO_PRIM = 4;

    private ActivityGraphLabBinding binding;
    private NetworkSnapshot snap;
    private String resultText = "";

    /** Small adapter so we can use a lambda-like callback for Spinner selection. */
    private static class OnSelect implements AdapterView.OnItemSelectedListener {
        private final Runnable action;

        OnSelect(Runnable action) {
            this.action = action;
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            action.run();
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }

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

        ArrayAdapter<CharSequence> algoAdapter = ArrayAdapter.createFromResource(
                this, R.array.lab_algorithms, android.R.layout.simple_spinner_item);
        algoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerAlgo.setAdapter(algoAdapter);

        binding.graphView.setListener(new GraphView.Listener() {
            @Override
            public void onNodeTapped(int index) {
                binding.spinnerSource.setSelection(index); // tap a place = make it the source
            }

            @Override
            public void onProgress(int applied, int total) {
                if (total > 0 && applied >= total) {
                    binding.textInfo.setText(resultText);
                } else if (total > 0) {
                    binding.textInfo.setText(getString(R.string.lab_progress, applied, total));
                }
            }

            @Override
            public void onPlayStateChanged(boolean playing) {
                binding.buttonPlay.setText(playing ? R.string.button_pause : R.string.button_play);
            }
        });

        binding.seekSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                binding.graphView.setDelayMs(600 - progress * 6); // right = faster
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
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
                        binding.textInfo.setText(e.getMessage());
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

        // One undirected line per road pair.
        List<int[]> pairList = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (EdgeEntity e : s.edges()) {
            Integer u = s.idToIndex.get(e.fromId);
            Integer v = s.idToIndex.get(e.toId);
            if (u == null || v == null) {
                continue;
            }
            long k = Math.min(u, v) * 1000L + Math.max(u, v);
            if (seen.add(k)) {
                pairList.add(new int[]{u, v});
            }
        }
        binding.graphView.setNetwork(names, types, lat, lon,
                pairList.toArray(new int[0][]));

        ArrayAdapter<NodeEntity> sourceAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, s.nodes);
        sourceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerSource.setAdapter(sourceAdapter);

        List<String> destNames = new ArrayList<>();
        destNames.add(getString(R.string.lab_none));
        destNames.addAll(java.util.Arrays.asList(names));
        ArrayAdapter<String> destAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, destNames);
        destAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerDest.setAdapter(destAdapter);

        Runnable markers = () -> binding.graphView.setMarkers(
                binding.spinnerSource.getSelectedItemPosition(),
                binding.spinnerDest.getSelectedItemPosition() - 1);
        binding.spinnerSource.setOnItemSelectedListener(new OnSelect(markers));
        binding.spinnerDest.setOnItemSelectedListener(new OnSelect(markers));
        markers.run();

        binding.buttonRun.setEnabled(true);
        binding.buttonPlay.setEnabled(true);
        binding.buttonStep.setEnabled(true);
        binding.buttonEnd.setEnabled(true);
        binding.textInfo.setText(R.string.lab_ready);
    }

    /** Runs the chosen algorithm (a few milliseconds on 40 nodes) and starts the replay. */
    private void run() {
        if (snap == null) {
            return;
        }
        int src = binding.spinnerSource.getSelectedItemPosition();
        int dst = binding.spinnerDest.getSelectedItemPosition() - 1; // -1 = none
        int algo = binding.spinnerAlgo.getSelectedItemPosition();

        List<Step> steps;
        boolean showDist = false;
        int start = -1;
        List<Integer> cycle = new ArrayList<>();

        switch (algo) {
            case ALGO_DIJKSTRA: {
                PathResult r = Dijkstra.run(snap.buildGraph(false), src);
                steps = r.steps;
                showDist = true;
                start = src;
                resultText = pathText(r, src, dst);
                break;
            }
            case ALGO_BELLMAN:
            case ALGO_NEGATIVE_CYCLE: {
                PathResult r = BellmanFord.run(
                        snap.buildNetGraph(algo == ALGO_NEGATIVE_CYCLE), src);
                steps = r.steps;
                showDist = true;
                start = src;
                if (r.hasNegativeCycle) {
                    cycle = r.negativeCycle;
                    StringBuilder sb = new StringBuilder();
                    for (int v : cycle) {
                        if (sb.length() > 0) {
                            sb.append(" → ");
                        }
                        sb.append(snap.nodes.get(v).name);
                    }
                    resultText = getString(R.string.lab_result_cycle, sb.toString());
                } else {
                    resultText = pathText(r, src, dst);
                }
                break;
            }
            case ALGO_KRUSKAL: {
                MstResult r = Kruskal.run(snap.buildUndirectedOnce());
                steps = r.steps;
                resultText = getString(R.string.lab_result_mst,
                        r.edges.size(), r.totalCost, r.operations);
                break;
            }
            default: {
                MstResult r = Prim.run(snap.buildUndirectedOnce(), src);
                steps = r.steps;
                start = src;
                resultText = getString(R.string.lab_result_mst,
                        r.edges.size(), r.totalCost, r.operations);
                break;
            }
        }
        binding.graphView.setMarkers(src, dst);
        binding.graphView.loadSteps(steps, showDist, start, cycle);
        binding.graphView.play();
    }

    private String pathText(PathResult r, int src, int dst) {
        if (dst < 0) {
            return getString(R.string.lab_result_run, r.steps.size());
        }
        String from = snap.nodes.get(src).name;
        String to = snap.nodes.get(dst).name;
        if (!r.isReachable(dst)) {
            return getString(R.string.lab_unreachable, to);
        }
        return getString(R.string.lab_result_path, from, to, r.dist[dst]);
    }
}