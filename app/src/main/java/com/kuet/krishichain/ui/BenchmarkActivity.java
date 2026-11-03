package com.kuet.krishichain.ui;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

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

        binding.buttonBack.setOnClickListener(v -> finish());
        BottomNavHelper.setup(this, BottomNavHelper.Tab.MORE, binding.getRoot());
        binding.buttonBenchmark.setOnClickListener(v -> start());

        binding.tabLangBn.setOnClickListener(v -> setLanguage(true));
        binding.tabLangEn.setOnClickListener(v -> setLanguage(false));

        binding.switchOfflineMode.setOnCheckedChangeListener((btn, isChecked) -> {
            Toast.makeText(this, isChecked ? "Offline mode enabled" : "Online mode enabled", Toast.LENGTH_SHORT).show();
        });

        binding.switchAutoSync.setOnCheckedChangeListener((btn, isChecked) -> {
            Toast.makeText(this, isChecked ? "Auto sync (Wi-Fi) on" : "Auto sync off", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavHelper.setup(this, BottomNavHelper.Tab.MORE, binding.getRoot());
    }

    private void setLanguage(boolean isBangla) {
        if (isBangla) {
            binding.tabLangBn.setBackgroundResource(R.drawable.bg_tab_on);
            binding.tabLangBn.setTextColor(Color.WHITE);
            binding.tabLangEn.setBackgroundResource(R.drawable.bg_tab_off);
            binding.tabLangEn.setTextColor(Color.parseColor("#17261A"));
            Toast.makeText(this, "ভাষা: বাংলা নির্বাচিত", Toast.LENGTH_SHORT).show();
        } else {
            binding.tabLangEn.setBackgroundResource(R.drawable.bg_tab_on);
            binding.tabLangEn.setTextColor(Color.WHITE);
            binding.tabLangBn.setBackgroundResource(R.drawable.bg_tab_off);
            binding.tabLangBn.setTextColor(Color.parseColor("#17261A"));
            Toast.makeText(this, "Language: English selected", Toast.LENGTH_SHORT).show();
        }
    }

    private void start() {
        binding.buttonBenchmark.setEnabled(false);
        binding.progress.setVisibility(View.VISIBLE);
        final Context app = getApplicationContext();

        worker.execute(() -> {
            try {
                measure(GraphGenerator.random(40, 150, true, 1), 2); // JIT warm-up

                NetworkLoader.loadIfNeeded(app);
                NetworkSnapshot snap = NetworkSnapshot.load(AppDatabase.get(app).networkDao());
                Graph real = snap.buildNetGraph(false);
                double[] realTiming = measure(real, 10);

                Graph sparse = GraphGenerator.random(100, 300, true, 5);
                double[] sparseTiming = measure(sparse, 3);

                Graph dense = GraphGenerator.random(80, 800, true, 5);
                double[] denseTiming = measure(dense, 3);

                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    binding.textFloydReal.setText(String.format(Locale.US, "%.1f", realTiming[0]));
                    binding.textJohnsonReal.setText(String.format(Locale.US, "%.1f", realTiming[1]));

                    binding.textFloydSparse.setText(String.format(Locale.US, "%.1f", sparseTiming[0]));
                    binding.textJohnsonSparse.setText(String.format(Locale.US, "%.1f", sparseTiming[1]));

                    binding.textFloydDense.setText(String.format(Locale.US, "%.1f", denseTiming[0]));
                    binding.textJohnsonDense.setText(String.format(Locale.US, "%.1f", denseTiming[1]));

                    binding.progress.setVisibility(View.GONE);
                    binding.buttonBenchmark.setEnabled(true);
                    Toast.makeText(BenchmarkActivity.this, "Benchmark completed on device!", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    binding.progress.setVisibility(View.GONE);
                    binding.buttonBenchmark.setEnabled(true);
                    Toast.makeText(BenchmarkActivity.this, "Benchmark error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private static double[] measure(Graph g, int reps) {
        double bestF = Double.MAX_VALUE;
        double bestJ = Double.MAX_VALUE;
        for (int i = 0; i < reps; i++) {
            long t0 = System.nanoTime();
            FloydWarshall.run(g);
            long t1 = System.nanoTime();
            Johnson.run(g);
            long t2 = System.nanoTime();
            bestF = Math.min(bestF, (t1 - t0) / 1e6);
            bestJ = Math.min(bestJ, (t2 - t1) / 1e6);
        }
        return new double[]{bestF, bestJ};
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
