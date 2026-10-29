package com.kuet.krishichain;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.kuet.krishichain.data.AppDatabase;
import com.kuet.krishichain.data.NetworkDao;
import com.kuet.krishichain.data.NetworkLoader;
import com.kuet.krishichain.data.NodeEntity;
import com.kuet.krishichain.databinding.ActivityMainBinding;
import com.kuet.krishichain.receiver.ConnectivityReceiver;
import com.kuet.krishichain.service.SyncService;
import com.kuet.krishichain.ui.BenchmarkActivity;
import com.kuet.krishichain.ui.BottleneckActivity;
import com.kuet.krishichain.ui.GraphLabActivity;
import com.kuet.krishichain.ui.PlannerActivity;
import com.kuet.krishichain.ui.RankingActivity;


import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "KrishiChain";
    /** Same order as R.array.crop_names. These are the keys stored in the database. */
    private static final String[] CROP_KEYS = {"potato", "tomato", "onion"};

    private ActivityMainBinding binding;
    private ConnectivityReceiver connectivityReceiver;
    private BroadcastReceiver syncDoneReceiver;
    private boolean receiversRegistered = false;
    private com.kuet.krishichain.ui.HomeExtras homeExtras;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    binding.textSync.setText(R.string.sync_no_notif);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "MainActivity onCreate");
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        final int pad = (int) (16 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left + pad, bars.top + pad, bars.right + pad, bars.bottom + pad);
            return insets;
        });

        ArrayAdapter<CharSequence> cropAdapter = ArrayAdapter.createFromResource(
                this, R.array.crop_names, android.R.layout.simple_spinner_item);
        cropAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerCrop.setAdapter(cropAdapter);

        binding.buttonFind.setEnabled(false);
        binding.buttonFind.setOnClickListener(v -> onFindClicked());
        binding.buttonGraphLab.setOnClickListener(
                v -> startActivity(new Intent(this, GraphLabActivity.class)));
        binding.buttonBottleneck.setOnClickListener(
                v -> startActivity(new Intent(this, BottleneckActivity.class)));
        binding.buttonPlanner.setOnClickListener(
                v -> startActivity(new Intent(this, PlannerActivity.class)));
        binding.buttonReports.setOnClickListener(
                v -> startActivity(new Intent(this, com.kuet.krishichain.ui.ReportsActivity.class)));
        binding.buttonBenchmark.setOnClickListener(
                v -> startActivity(new Intent(this, BenchmarkActivity.class)));
        binding.buttonSync.setOnClickListener(v -> startSync());
        homeExtras = new com.kuet.krishichain.ui.HomeExtras(this, binding);
        homeExtras.init();

        // Android 13+ needs a runtime permission to show notifications.
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }

        loadData();
    }

    /** Starts the foreground service. Allowed because this Activity is visible right now. */
    private void startSync() {
        binding.textSync.setText(R.string.sync_running);
        ContextCompat.startForegroundService(this, new Intent(this, SyncService.class));
    }

    /** Loads network.json into Room (first run only), then fills the screen. */
    private void loadData() {
        final Context app = getApplicationContext();
        AppDatabase.IO.execute(() -> {
            try {
                NetworkLoader.loadIfNeeded(app);
                NetworkDao dao = AppDatabase.get(app).networkDao();
                final List<NodeEntity> origins = dao.getOrigins();
                final int nodes = dao.nodeCount();
                final int edges = dao.edgeCount();
                final int prices = dao.priceCount();
                final int dummy = dao.dummyPriceCount();
                runOnUiThread(() -> showData(origins, nodes, edges, prices, dummy));
            } catch (Exception e) {
                Log.e(TAG, "Loading failed", e);
                runOnUiThread(() -> {
                    if (isDestroyed()) {
                        return;
                    }
                    binding.textStatus.setText(getString(R.string.status_error, e.getMessage()));
                });
            }
        });
    }

    private void showData(List<NodeEntity> origins, int nodes, int edges, int prices, int dummy) {
        if (isDestroyed()) {
            return;
        }
        ArrayAdapter<NodeEntity> originAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, origins);
        originAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerOrigin.setAdapter(originAdapter);

        binding.textStatus.setText(getString(R.string.status_loaded, nodes, edges, prices));
        binding.textDemoWarning.setVisibility(dummy > 0 ? View.VISIBLE : View.GONE);
        binding.buttonFind.setEnabled(true);
    }

    private void onFindClicked() {
        String text = binding.editQuantity.getText().toString().trim();
        double qty = 0;
        try {
            qty = Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            // stays 0, handled below
        }
        if (qty <= 0) {
            binding.editQuantity.setError(getString(R.string.error_quantity));
            return;
        }
        NodeEntity origin = (NodeEntity) binding.spinnerOrigin.getSelectedItem();
        if (origin == null) {
            return;
        }

        // Explicit Intent: we name the exact Activity class to start, and pass data as extras.
        Intent intent = new Intent(this, RankingActivity.class);
        intent.putExtra(RankingActivity.EXTRA_CROP_KEY,
                CROP_KEYS[binding.spinnerCrop.getSelectedItemPosition()]);
        intent.putExtra(RankingActivity.EXTRA_CROP_NAME,
                binding.spinnerCrop.getSelectedItem().toString());
        intent.putExtra(RankingActivity.EXTRA_QTY_KG, qty);
        intent.putExtra(RankingActivity.EXTRA_ORIGIN_ID, origin.id);
        intent.putExtra(RankingActivity.EXTRA_ORIGIN_NAME, origin.name);
        startActivity(intent);
    }

    // ---------------- Receivers live only while the screen is visible ----------------

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "MainActivity onStart");
        homeExtras.refreshWeatherText();
        binding.textNet.setText(ConnectivityReceiver.isOnline(this)
                ? R.string.net_online : R.string.net_offline);

        connectivityReceiver = new ConnectivityReceiver((online, regained) -> {
            binding.textNet.setText(online ? R.string.net_online : R.string.net_offline);
            if (regained) {
                startSync(); // internet came back: refresh automatically
            }
        });
        // Only the protected system broadcast CONNECTIVITY_ACTION is received here.
        ContextCompat.registerReceiver(this, connectivityReceiver,
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION),
                ContextCompat.RECEIVER_EXPORTED);

        syncDoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                binding.textSync.setText(getString(R.string.sync_done,
                        intent.getStringExtra(SyncService.EXTRA_SUMMARY)));
                homeExtras.refreshWeatherText();
            }
        };
        // The service sends this inside our app only, so it is not exported.
        ContextCompat.registerReceiver(this, syncDoneReceiver,
                new IntentFilter(SyncService.ACTION_SYNC_DONE),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        receiversRegistered = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "MainActivity onResume");
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "MainActivity onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "MainActivity onStop");
        if (receiversRegistered) {
            unregisterReceiver(connectivityReceiver);
            unregisterReceiver(syncDoneReceiver);
            receiversRegistered = false;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "MainActivity onDestroy");
        super.onDestroy();
    }
}