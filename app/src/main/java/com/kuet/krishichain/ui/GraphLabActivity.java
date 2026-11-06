package com.kuet.krishichain.ui;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.kuet.krishichain.R;
import com.kuet.krishichain.algo.BellmanFord;
import com.kuet.krishichain.algo.Dijkstra;
import com.kuet.krishichain.algo.PathResult;
import com.kuet.krishichain.algo.Step;
import com.kuet.krishichain.data.AppDatabase;
import com.kuet.krishichain.data.NetworkDao;
import com.kuet.krishichain.data.NetworkLoader;
import com.kuet.krishichain.data.NodeEntity;
import com.kuet.krishichain.data.UserPreferences;
import com.kuet.krishichain.databinding.ActivityGraphLabBinding;
import com.kuet.krishichain.domain.NetworkSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GraphLabActivity extends AppCompatActivity {
    private static final int ALGO_DIJKSTRA = 0;
    private static final int ALGO_BELLMAN = 1;

    private ActivityGraphLabBinding binding;
    private NetworkSnapshot snap;
    private int currentAlgo = ALGO_DIJKSTRA;
    private int selectedSourceIndex = 0;
    private final List<String> districtNames = new ArrayList<>();

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
        binding.graphView.setDarkCanvas(false);

        binding.tabDijkstra.setOnClickListener(v -> selectAlgo(ALGO_DIJKSTRA));
        binding.tabBellman.setOnClickListener(v -> selectAlgo(ALGO_BELLMAN));

        binding.graphView.setListener(new GraphView.Listener() {
            @Override
            public void onNodeTapped(int index) {
                selectSource(index);
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

    @Override
    protected void onResume() {
        super.onResume();
        TopBarHelper.setup(this, binding.getRoot());
    }

    private void selectAlgo(int algo) {
        currentAlgo = algo;
        updateTab(binding.tabDijkstra, algo == ALGO_DIJKSTRA);
        updateTab(binding.tabBellman, algo == ALGO_BELLMAN);
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
        GraphBinder.bind(binding.graphView, s);

        districtNames.clear();
        for (NodeEntity node : s.nodes) {
            districtNames.add(node.name);
        }

        int savedOriginId = UserPreferences.getOriginId(this, 1);
        selectedSourceIndex = 0;
        for (int i = 0; i < s.nodes.size(); i++) {
            if (s.nodes.get(i).id == savedOriginId) {
                selectedSourceIndex = i;
                break;
            }
        }

        SearchableDistrictAdapter adapter = new SearchableDistrictAdapter(
                this, android.R.layout.simple_dropdown_item_1line, districtNames);
        binding.autoCompleteSource.setAdapter(adapter);
        binding.autoCompleteSource.setText(districtNames.get(selectedSourceIndex), false);

        binding.autoCompleteSource.setOnClickListener(v -> {
            binding.autoCompleteSource.selectAll();
            binding.autoCompleteSource.showDropDown();
        });
        binding.autoCompleteSource.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                binding.autoCompleteSource.selectAll();
                binding.autoCompleteSource.showDropDown();
            }
        });
        binding.autoCompleteSource.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = (String) parent.getItemAtPosition(position);
            int idx = districtNames.indexOf(selectedName);
            if (idx >= 0) {
                selectSource(idx);
            }
        });
        binding.autoCompleteSource.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (s == null) return;
                String val = s.toString().trim();
                for (int i = 0; i < districtNames.size(); i++) {
                    if (districtNames.get(i).equalsIgnoreCase(val)) {
                        if (selectedSourceIndex != i) {
                            selectSource(i);
                        }
                        break;
                    }
                }
            }
        });

        run();
    }

    private void selectSource(int index) {
        if (snap == null || index < 0 || index >= snap.nodes.size()) return;
        selectedSourceIndex = index;
        binding.autoCompleteSource.setText(districtNames.get(index), false);
        UserPreferences.setOriginId(this, snap.nodes.get(index).id);
        run();
    }

    private void run() {
        if (snap == null) return;
        int src = selectedSourceIndex;
        if (src < 0 || src >= snap.nodes.size()) src = 0;

        List<Step> steps = new ArrayList<>();
        boolean showDist = true;
        int start = src;
        List<Integer> cycle = new ArrayList<>();

        String algoName;
        PathResult r;
        switch (currentAlgo) {
            case ALGO_DIJKSTRA:
                algoName = "Dijkstra";
                r = Dijkstra.run(snap.buildGraph(false), src);
                steps = r.steps;
                break;
            case ALGO_BELLMAN:
            default:
                algoName = "Bellman-Ford";
                r = BellmanFord.run(snap.buildNetGraph(false), src);
                steps = r.steps;
                break;
        }

        int reachedCount = 0;
        double maxDist = 0;
        String farthestNode = "";
        double totalDist = 0;

        for (int i = 0; i < snap.nodes.size(); i++) {
            double d = r.dist[i];
            if (d < Double.POSITIVE_INFINITY) {
                reachedCount++;
                totalDist += d;
                if (d > maxDist) {
                    maxDist = d;
                    farthestNode = snap.nodes.get(i).name;
                }
            }
        }
        double avgDist = reachedCount > 1 ? totalDist / (reachedCount - 1) : 0;
        String fromName = snap.nodes.get(src).name;

        binding.textCheck1.setText("Running " + algoName + " (Single-Source Shortest Paths)…");
        binding.textCheck2.setText("Source: " + fromName + " → Shortest paths to all " + reachedCount + " districts");
        binding.textCheck3.setText(String.format(Locale.US, "Max: %.0f ৳ (%s) • Avg: %.0f ৳",
                maxDist, farthestNode, avgDist));

        binding.graphView.setMarkers(src, -1);
        binding.graphView.loadSteps(steps, showDist, start, cycle);
        binding.graphView.play();
    }
}
