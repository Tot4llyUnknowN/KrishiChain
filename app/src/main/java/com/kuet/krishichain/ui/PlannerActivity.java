package com.kuet.krishichain.ui;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
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
import java.util.Locale;
import java.util.Set;

public class PlannerActivity extends AppCompatActivity {
    private static final int TIMING_REPS = 50;

    private ActivityPlannerBinding binding;
    private NetworkSnapshot snap;
    private Graph roads;
    private double allRoadsKm = 0;
    private int roadCount = 0;
    private int primStart = 0;
    private MstResult kruskal;
    private MstResult prim;
    private boolean isPrimSelected = false;

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

        binding.buttonBack.setOnClickListener(v -> finish());
        TopBarHelper.setup(this, binding.getRoot());
        binding.graphView.setDelayMs(150);
        binding.graphView.setListener(new GraphView.Listener() {
            @Override
            public void onNodeTapped(int index) {
                if (roads == null) return;
                primStart = index;
                isPrimSelected = true;
                compute();
                startReplay();
            }

            @Override
            public void onProgress(int applied, int total) {}

            @Override
            public void onPlayStateChanged(boolean playing) {
                binding.buttonPlay.setText(playing ? R.string.button_pause : R.string.button_play);
            }
        });

        binding.cardKruskal.setOnClickListener(v -> {
            isPrimSelected = false;
            startReplay();
        });

        binding.cardPrim.setOnClickListener(v -> {
            isPrimSelected = true;
            startReplay();
        });

        binding.buttonViewDetails.setOnClickListener(v -> showMstDetails());
        binding.buttonReplay.setOnClickListener(v -> startReplay());
        binding.buttonPlay.setOnClickListener(v -> binding.graphView.togglePlay());
        binding.buttonStep.setOnClickListener(v -> {
            binding.graphView.pause();
            binding.graphView.stepOnce();
        });

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
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void onNetworkReady(NetworkSnapshot s) {
        snap = s;
        GraphBinder.bind(binding.graphView, s);

        roads = new Graph(s.nodes.size());
        Set<Long> seen = new HashSet<>();
        for (EdgeEntity e : s.edges()) {
            Integer u = s.idToIndex.get(e.fromId);
            Integer v = s.idToIndex.get(e.toId);
            if (u == null || v == null) continue;
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

        compute();
        startReplay();
    }

    private void compute() {
        kruskal = Kruskal.run(roads);
        prim = Prim.run(roads, primStart);

        binding.textKruskalKm.setText(String.format(Locale.US, "%,.0f km", kruskal.totalCost));
        binding.textPrimKm.setText(String.format(Locale.US, "%,.0f km", prim.totalCost));
    }

    private void startReplay() {
        if (kruskal == null) return;
        if (isPrimSelected) {
            binding.graphView.loadSteps(prim.steps, false, primStart, null);
        } else {
            binding.graphView.loadSteps(kruskal.steps, false, -1, null);
        }
        binding.graphView.play();
    }

    private void showMstDetails() {
        if (kruskal == null) return;
        double saving = allRoadsKm <= 0 ? 0 : (allRoadsKm - kruskal.totalCost) / allRoadsKm * 100.0;
        String message = String.format(Locale.US,
                "Kruskal MST: %d roads · %,.1f km · %d operations\n" +
                "Prim MST (from %s): %d roads · %,.1f km · %d operations\n\n" +
                "The minimum spanning tree saves %.0f%% road construction compared to building all roads.\n" +
                "Totals are equal by theory.",
                kruskal.edges.size(), kruskal.totalCost, kruskal.operations,
                snap.nodes.get(primStart).name, prim.edges.size(), prim.totalCost, prim.operations,
                saving);

        new AlertDialog.Builder(this)
                .setTitle("Feeder Network Details")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }
}
