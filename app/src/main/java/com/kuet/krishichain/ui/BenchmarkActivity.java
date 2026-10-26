package com.kuet.krishichain.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.kuet.krishichain.R;
import com.kuet.krishichain.algo.AllPairsResult;
import com.kuet.krishichain.algo.FloydWarshall;
import com.kuet.krishichain.algo.Graph;
import com.kuet.krishichain.algo.GraphGenerator;
import com.kuet.krishichain.algo.Johnson;
import com.kuet.krishichain.data.AppDatabase;
import com.kuet.krishichain.data.NetworkLoader;
import com.kuet.krishichain.databinding.ActivityBenchmarkBinding;
import com.kuet.krishichain.domain.NetworkSnapshot;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BenchmarkActivity extends AppCompatActivity {
    /** {vertices, arcs}. The first three are sparse, the last two dense. */
    private static final int[][] SCENARIOS = {
            {100, 300}, {200, 600}, {300, 900}, {100, 5000}, {200, 20000}};

    private ActivityBenchmarkBinding binding;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBenchmarkBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        final int pad = (int) (12 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left + pad, bars.top + pad, bars.right + pad, bars.bottom + pad);
            return insets;
        });
        binding.buttonBenchmark.setOnClickListener(v -> start());
    }

    private void start() {
        binding.buttonBenchmark.setEnabled(false);
        binding.progress.setVisibility(View.VISIBLE);
        binding.textResult.setText(R.string.bm_running);
        final Context app = getApplicationContext();

        worker.execute(() -> {
            final StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.US, "%-6s %4s %6s %9s %10s  %s%n",
                    "type", "V", "E", "Floyd ms", "Johnson ms", "faster"));
            try {
                measure(GraphGenerator.random(60, 200, true, 1), 3); // JIT warm-up, not shown

                NetworkLoader.loadIfNeeded(app);
                NetworkSnapshot snap = NetworkSnapshot.load(AppDatabase.get(app).networkDao());
                Graph real = snap.buildNetGraph(false);
                sb.append(row("real", real, 30));
                publish(sb.toString(), false);

                for (int[] s : SCENARIOS) {
                    Graph g = GraphGenerator.random(s[0], s[1], true, 7);
                    String label = s[1] > 2L * s[0] * 10 ? "dense" : "sparse";
                    sb.append(row(label, g, 3));
                    publish(sb.toString(), false);
                }
                publish(sb.toString(), true);
            } catch (Exception e) {
                sb.append("\nError: ").append(e.getMessage());
                publish(sb.toString(), true);
            }
        });
    }

    private String row(String label, Graph g, int reps) {
        double[] r = measure(g, reps);
        return String.format(Locale.US, "%-6s %4d %6d %9.2f %10.2f  %s%s%n",
                label, g.size(), g.allEdges().size(), r[0], r[1],
                r[0] < r[1] ? "Floyd" : "Johnson", r[2] == 1 ? "" : "  MISMATCH");
    }

    /** Best-of-N timing in milliseconds: {floyd, johnson, 1 if both gave the same matrix}. */
    private static double[] measure(Graph g, int reps) {
        double bestF = Double.MAX_VALUE;
        double bestJ = Double.MAX_VALUE;
        AllPairsResult f = null;
        AllPairsResult j = null;
        for (int i = 0; i < reps; i++) {
            long t0 = System.nanoTime();
            f = FloydWarshall.run(g);
            long t1 = System.nanoTime();
            j = Johnson.run(g);
            long t2 = System.nanoTime();
            bestF = Math.min(bestF, (t1 - t0) / 1e6);
            bestJ = Math.min(bestJ, (t2 - t1) / 1e6);
        }
        boolean same = f.hasNegativeCycle == j.hasNegativeCycle;
        for (int u = 0; same && !f.hasNegativeCycle && u < g.size(); u++) {
            for (int v = 0; v < g.size(); v++) {
                double a = f.dist[u][v];
                double b = j.dist[u][v];
                boolean bothInf = a == AllPairsResult.INF && b == AllPairsResult.INF;
                if (!bothInf && Math.abs(a - b) > 1e-6) {
                    same = false;
                    break;
                }
            }
        }
        return new double[]{bestF, bestJ, same ? 1 : 0};
    }

    private void publish(final String text, final boolean finished) {
        runOnUiThread(() -> {
            if (isDestroyed()) {
                return;
            }
            binding.textResult.setText(finished ? text + "\n" + getString(R.string.bm_done) : text);
            if (finished) {
                binding.progress.setVisibility(View.GONE);
                binding.buttonBenchmark.setEnabled(true);
            }
        });
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}