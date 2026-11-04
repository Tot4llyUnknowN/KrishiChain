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
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.kuet.krishichain.data.AppDatabase;
import com.kuet.krishichain.data.CropInfo;
import com.kuet.krishichain.data.CropXmlParser;
import com.kuet.krishichain.data.FirebaseRepository;
import com.kuet.krishichain.data.NetworkDao;
import com.kuet.krishichain.data.NetworkLoader;
import com.kuet.krishichain.data.NodeEntity;
import com.kuet.krishichain.data.PriceEntity;
import com.kuet.krishichain.data.UserPreferences;
import com.kuet.krishichain.databinding.ActivityMainBinding;
import com.kuet.krishichain.databinding.DialogMoreToolsBinding;
import com.kuet.krishichain.domain.MarketRanker;
import com.kuet.krishichain.domain.NetworkSnapshot;
import com.kuet.krishichain.domain.RankedMarket;
import com.kuet.krishichain.domain.RankingResult;
import com.kuet.krishichain.receiver.ConnectivityReceiver;
import com.kuet.krishichain.service.SyncService;
import com.kuet.krishichain.ui.BenchmarkActivity;
import com.kuet.krishichain.ui.BottomNavHelper;
import com.kuet.krishichain.ui.BottleneckActivity;
import com.kuet.krishichain.ui.GraphLabActivity;
import com.kuet.krishichain.ui.HomeExtras;
import com.kuet.krishichain.ui.PlannerActivity;
import com.kuet.krishichain.ui.RankingActivity;
import com.kuet.krishichain.ui.ReportsActivity;
import com.kuet.krishichain.ui.RouteActivity;

import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "KrishiChain";
    private static final String[] CROP_KEYS = {"potato", "tomato", "onion"};

    private ActivityMainBinding binding;
    private ConnectivityReceiver connectivityReceiver;
    private BroadcastReceiver syncDoneReceiver;
    private boolean receiversRegistered = false;
    private HomeExtras homeExtras;

    private RankedMarket topMarket = null;
    private boolean isSyncingSelection = false;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    binding.textSync.setText(R.string.sync_no_notif);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        Log.d(TAG, "MainActivity onCreate");
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        final int pad = (int) (16 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        // 1. Crop Dropdown Spinner (clean layout)
        ArrayAdapter<CharSequence> cropAdapter = ArrayAdapter.createFromResource(
                this, R.array.crop_names, R.layout.item_spinner_selected);
        cropAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        binding.spinnerCrop.setAdapter(cropAdapter);
        binding.spinnerCrop.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!isSyncingSelection && position >= 0 && position < CROP_KEYS.length) {
                    UserPreferences.setCropKey(MainActivity.this, CROP_KEYS[position]);
                }
                updateRankingsAndProfits();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 2. Origin Dropdown Spinner listener
        binding.spinnerOrigin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                NodeEntity selected = (NodeEntity) binding.spinnerOrigin.getSelectedItem();
                if (!isSyncingSelection && selected != null) {
                    UserPreferences.setOriginId(MainActivity.this, selected.id);
                }
                updateRankingsAndProfits();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 3. Quantity Text Field listener
        binding.editQuantity.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!isSyncingSelection) {
                    UserPreferences.setQuantity(MainActivity.this, getQuantity());
                }
                updateRankingsAndProfits();
            }
        });

        // 3b. Base Production Cost Text Field listener
        binding.editProdCost.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!isSyncingSelection) {
                    UserPreferences.setProductionCost(MainActivity.this, getProdCost());
                }
                updateRankingsAndProfits();
            }
        });

        // 4. Header Profile & Settings
        binding.buttonHeaderProfile.setOnClickListener(v -> showProfileDialog());
        binding.buttonHeaderSettings.setOnClickListener(v ->
                startActivity(new Intent(this, BenchmarkActivity.class)));

        // 5. Action Cards Row (4 actions matching home.jpeg)
        binding.cardActionFind.setOnClickListener(v -> onFindClicked());
        binding.cardActionRoute.setOnClickListener(v -> openDefaultRoute());
        binding.cardActionPrices.setOnClickListener(v ->
                startActivity(new Intent(this, ReportsActivity.class)));
        binding.cardActionWeather.setOnClickListener(v -> toggleWeatherSimulation());

        // 6. See Details on Estimated Profit card
        binding.buttonSeeDetails.setOnClickListener(v -> openDefaultRoute());

        // 7. Top Markets section "See All →"
        binding.buttonSeeAllMarkets.setOnClickListener(v -> onFindClicked());

        // 8. Road Updates banner
        binding.bannerRoadUpdates.setOnClickListener(v ->
                startActivity(new Intent(this, BottleneckActivity.class)));

        // 9. Bottom Navigation
        BottomNavHelper.setup(this, BottomNavHelper.Tab.HOME, binding.getRoot());

        homeExtras = new HomeExtras(this);
        homeExtras.init();

        // Android 13+ runtime notification permission
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }

        loadData();
    }

    private double getQuantity() {
        String text = binding.editQuantity.getText().toString().trim();
        try {
            double val = Double.parseDouble(text);
            return val > 0 ? val : 1000.0;
        } catch (Exception e) {
            return 1000.0;
        }
    }

    private double getProdCost() {
        String text = binding.editProdCost.getText().toString().trim();
        try {
            double val = Double.parseDouble(text);
            return val > 0 ? val : 20.0;
        } catch (Exception e) {
            return 20.0;
        }
    }

    private void openDefaultRoute() {
        if (topMarket != null) {
            openRouteFor(topMarket);
            return;
        }

        NodeEntity origin = (NodeEntity) binding.spinnerOrigin.getSelectedItem();
        int originId = origin != null ? origin.id : 0;
        String originName = origin != null ? origin.name : "Khulna";

        int cropPos = binding.spinnerCrop.getSelectedItemPosition();
        String cropKey = (cropPos >= 0 && cropPos < CROP_KEYS.length) ? CROP_KEYS[cropPos] : "potato";
        String cropName = binding.spinnerCrop.getSelectedItem() != null
                ? binding.spinnerCrop.getSelectedItem().toString() : "Potato";

        Intent intent = new Intent(this, RouteActivity.class);
        intent.putExtra(RankingActivity.EXTRA_CROP_KEY, cropKey);
        intent.putExtra(RankingActivity.EXTRA_CROP_NAME, cropName);
        intent.putExtra(RankingActivity.EXTRA_QTY_KG, getQuantity());
        intent.putExtra(RankingActivity.EXTRA_ORIGIN_ID, originId);
        intent.putExtra(RankingActivity.EXTRA_ORIGIN_NAME, originName);
        intent.putExtra(RouteActivity.EXTRA_DEST_ID, 2); // Dhaka
        intent.putExtra(RouteActivity.EXTRA_PRICE, 42.50);
        startActivity(intent);
    }

    private void openRouteFor(RankedMarket rm) {
        if (rm == null) return;
        NodeEntity origin = (NodeEntity) binding.spinnerOrigin.getSelectedItem();
        int originId = origin != null ? origin.id : 0;
        String originName = origin != null ? origin.name : "Origin";

        int cropPos = binding.spinnerCrop.getSelectedItemPosition();
        String cropKey = (cropPos >= 0 && cropPos < CROP_KEYS.length) ? CROP_KEYS[cropPos] : "potato";
        String cropName = binding.spinnerCrop.getSelectedItem() != null
                ? binding.spinnerCrop.getSelectedItem().toString() : "Potato";

        Intent intent = new Intent(this, RouteActivity.class);
        intent.putExtra(RankingActivity.EXTRA_CROP_KEY, cropKey);
        intent.putExtra(RankingActivity.EXTRA_CROP_NAME, cropName);
        intent.putExtra(RankingActivity.EXTRA_QTY_KG, getQuantity());
        intent.putExtra(RankingActivity.EXTRA_ORIGIN_ID, originId);
        intent.putExtra(RankingActivity.EXTRA_ORIGIN_NAME, originName);
        intent.putExtra(RouteActivity.EXTRA_DEST_ID, rm.option.nodeId);
        intent.putExtra(RouteActivity.EXTRA_PRICE, rm.option.pricePerKg);
        startActivity(intent);
    }

    private void updateRankingsAndProfits() {
        NodeEntity origin = (NodeEntity) binding.spinnerOrigin.getSelectedItem();
        if (origin == null) return;

        int cropPos = binding.spinnerCrop.getSelectedItemPosition();
        final String cropKey = (cropPos >= 0 && cropPos < CROP_KEYS.length) ? CROP_KEYS[cropPos] : "potato";
        final int originId = origin.id;
        final double qty = getQuantity();
        final double prodCost = getProdCost();
        final Context app = getApplicationContext();

        AppDatabase.IO.execute(() -> {
            try {
                NetworkDao dao = AppDatabase.get(app).networkDao();
                NetworkSnapshot snapshot = NetworkSnapshot.load(dao);
                List<PriceEntity> prices = dao.getPricesForCrop(cropKey);
                double rate = 0;
                for (CropInfo info : CropXmlParser.parse(app)) {
                    if (info.key.equals(cropKey)) {
                        rate = info.spoilagePctPerHour;
                    }
                }
                final RankingResult result = MarketRanker.rank(snapshot, prices, originId, qty, prodCost, rate);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    displayRankings(result);
                });
            } catch (Exception e) {
                Log.w(TAG, "Ranking calculation failed: " + e.getMessage());
            }
        });
    }

    private void displayRankings(RankingResult result) {
        if (result == null || result.ranked.isEmpty()) return;

        RankedMarket best = result.ranked.get(0);
        topMarket = best;

        // Estimated profit
        binding.textEstimatedProfit.setText(String.format(Locale.US, "৳ %,d", Math.round(best.option.netProfit)));

        double nearestProfit = result.nearest != null ? result.nearest.option.netProfit : 0;
        double pctGain = 0;
        if (nearestProfit > 0 && result.nearest != best) {
            pctGain = ((best.option.netProfit - nearestProfit) / nearestProfit) * 100.0;
        }

        if (pctGain > 0.5) {
            binding.textProfitGain.setText(String.format(Locale.US, "+%.0f%% vs nearest market", pctGain));
        } else {
            binding.textProfitGain.setText("Top market choice · Highest profit");
        }

        // Market Card 1 (Gold 1)
        binding.cardMarket1.setVisibility(View.VISIBLE);
        binding.textMarket1Name.setText(best.option.marketName);
        binding.textMarket1Dist.setText(String.format(Locale.US, "📍 %.0f km", best.km));
        binding.textMarket1Profit.setText(String.format(Locale.US, "৳ %,d", Math.round(best.option.netProfit)));
        binding.textMarket1Gain.setText(String.format(Locale.US, "▲ %.0f%%", Math.max(1, pctGain)));
        binding.cardMarket1.setOnClickListener(v -> openRouteFor(best));

        // Market Card 2 (Silver 2)
        if (result.ranked.size() > 1) {
            RankedMarket m2 = result.ranked.get(1);
            binding.cardMarket2.setVisibility(View.VISIBLE);
            binding.textMarket2Name.setText(m2.option.marketName);
            binding.textMarket2Dist.setText(String.format(Locale.US, "📍 %.0f km", m2.km));
            binding.textMarket2Profit.setText(String.format(Locale.US, "৳ %,d", Math.round(m2.option.netProfit)));
            double g2 = (nearestProfit > 0 && result.nearest != m2)
                    ? ((m2.option.netProfit - nearestProfit) / nearestProfit) * 100.0 : 18.0;
            binding.textMarket2Gain.setText(String.format(Locale.US, "▲ %.0f%%", Math.max(1, g2)));
            binding.cardMarket2.setOnClickListener(v -> openRouteFor(m2));
        } else {
            binding.cardMarket2.setVisibility(View.GONE);
        }

        // Market Card 3 (Bronze 3)
        if (result.ranked.size() > 2) {
            RankedMarket m3 = result.ranked.get(2);
            binding.cardMarket3.setVisibility(View.VISIBLE);
            binding.textMarket3Name.setText(m3.option.marketName);
            binding.textMarket3Dist.setText(String.format(Locale.US, "📍 %.0f km", m3.km));
            binding.textMarket3Profit.setText(String.format(Locale.US, "৳ %,d", Math.round(m3.option.netProfit)));
            double g3 = (nearestProfit > 0 && result.nearest != m3)
                    ? ((m3.option.netProfit - nearestProfit) / nearestProfit) * 100.0 : 12.0;
            binding.textMarket3Gain.setText(String.format(Locale.US, "▲ %.0f%%", Math.max(1, g3)));
            binding.cardMarket3.setOnClickListener(v -> openRouteFor(m3));
        } else {
            binding.cardMarket3.setVisibility(View.GONE);
        }
    }

    private void showProfileDialog() {
        String userDisplay = FirebaseRepository.getUserDisplayName();
        boolean loggedIn = FirebaseRepository.getCurrentUser() != null
                && !FirebaseRepository.getCurrentUser().isAnonymous();

        new AlertDialog.Builder(this)
                .setTitle("KrishiChain Profile")
                .setMessage("Account: " + userDisplay)
                .setPositiveButton(loggedIn ? "Sign Out" : "Sign In / Register", (dialog, which) -> {
                    if (loggedIn) {
                        FirebaseRepository.signOut();
                        Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show();
                    } else {
                        startActivity(new Intent(this, SplashActivity.class));
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void toggleWeatherSimulation() {
        homeExtras.toggleSimulation(() -> {
            String wt = homeExtras.getLastWeatherText();
            if (wt != null && !wt.isEmpty()) {
                binding.textWeatherRoadInfo.setText(wt);
            }
            Toast.makeText(this, "Weather condition updated", Toast.LENGTH_SHORT).show();
        });
    }

    private void showMoreToolsDialog() {
        DialogMoreToolsBinding db = DialogMoreToolsBinding.inflate(LayoutInflater.from(this));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(db.getRoot())
                .setCancelable(true)
                .create();

        db.textDialogWeather.setText(homeExtras.getLastWeatherText());

        db.cardToolBottleneck.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(this, BottleneckActivity.class));
        });

        db.cardToolPlanner.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(this, PlannerActivity.class));
        });

        db.cardToolWeather.setOnClickListener(v -> {
            homeExtras.toggleSimulation(() -> {
                db.textDialogWeather.setText(homeExtras.getLastWeatherText());
                binding.textWeatherRoadInfo.setText(homeExtras.getLastWeatherText());
            });
            Toast.makeText(this, "Weather updated", Toast.LENGTH_SHORT).show();
        });

        db.buttonCloseDialog.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void startSync() {
        binding.textSync.setText(R.string.sync_running);
        ContextCompat.startForegroundService(this, new Intent(this, SyncService.class));
    }

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
                    if (isDestroyed()) return;
                    binding.textStatus.setText(getString(R.string.status_error, e.getMessage()));
                });
            }
        });
    }

    private void showData(List<NodeEntity> origins, int nodes, int edges, int prices, int dummy) {
        if (isDestroyed()) return;
        isSyncingSelection = true;
        ArrayAdapter<NodeEntity> originAdapter = new ArrayAdapter<>(
                this, R.layout.item_spinner_selected, origins);
        originAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        binding.spinnerOrigin.setAdapter(originAdapter);

        // Select the saved origin from UserPreferences
        int savedOriginId = UserPreferences.getOriginId(this, 1);
        for (int i = 0; i < origins.size(); i++) {
            if (origins.get(i).id == savedOriginId) {
                binding.spinnerOrigin.setSelection(i);
                break;
            }
        }

        // Also select saved crop from UserPreferences
        String savedCropKey = UserPreferences.getCropKey(this, "potato");
        for (int i = 0; i < CROP_KEYS.length; i++) {
            if (CROP_KEYS[i].equalsIgnoreCase(savedCropKey)) {
                binding.spinnerCrop.setSelection(i);
                break;
            }
        }

        // Restore saved quantity and production cost
        double savedQty = UserPreferences.getQuantity(this, 1000.0);
        binding.editQuantity.setText(String.format(Locale.US, "%.0f", savedQty));
        double savedProdCost = UserPreferences.getProductionCost(this, 20.0);
        binding.editProdCost.setText(String.format(Locale.US, "%.1f", savedProdCost));

        isSyncingSelection = false;

        binding.textStatus.setText(getString(R.string.status_loaded, nodes, edges, prices));
        binding.textDemoWarning.setVisibility(dummy > 0 ? View.VISIBLE : View.GONE);

        // Calculate initial estimates
        updateRankingsAndProfits();
    }

    private void onFindClicked() {
        double qty = getQuantity();
        if (qty <= 0) {
            binding.editQuantity.setError(getString(R.string.error_quantity));
            return;
        }
        NodeEntity origin = (NodeEntity) binding.spinnerOrigin.getSelectedItem();
        if (origin == null) return;

        int cropPos = binding.spinnerCrop.getSelectedItemPosition();
        String cropKey = (cropPos >= 0 && cropPos < CROP_KEYS.length) ? CROP_KEYS[cropPos] : "potato";
        String cropName = binding.spinnerCrop.getSelectedItem() != null
                ? binding.spinnerCrop.getSelectedItem().toString() : "Potato";

        Intent intent = new Intent(this, RankingActivity.class);
        intent.putExtra(RankingActivity.EXTRA_CROP_KEY, cropKey);
        intent.putExtra(RankingActivity.EXTRA_CROP_NAME, cropName);
        intent.putExtra(RankingActivity.EXTRA_QTY_KG, qty);
        intent.putExtra(RankingActivity.EXTRA_ORIGIN_ID, origin.id);
        intent.putExtra(RankingActivity.EXTRA_ORIGIN_NAME, origin.name);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavHelper.setup(this, BottomNavHelper.Tab.HOME, binding.getRoot());
        syncPreferencesWithMapTab();
    }

    private void syncPreferencesWithMapTab() {
        if (binding.spinnerOrigin.getAdapter() == null) return;
        int savedOriginId = UserPreferences.getOriginId(this, 1);
        String savedCropKey = UserPreferences.getCropKey(this, "potato");

        boolean changed = false;
        isSyncingSelection = true;

        NodeEntity currentOrigin = (NodeEntity) binding.spinnerOrigin.getSelectedItem();
        if (currentOrigin == null || currentOrigin.id != savedOriginId) {
            for (int i = 0; i < binding.spinnerOrigin.getCount(); i++) {
                NodeEntity n = (NodeEntity) binding.spinnerOrigin.getItemAtPosition(i);
                if (n != null && n.id == savedOriginId) {
                    binding.spinnerOrigin.setSelection(i);
                    changed = true;
                    break;
                }
            }
        }

        int currentCropPos = binding.spinnerCrop.getSelectedItemPosition();
        String currentCrop = (currentCropPos >= 0 && currentCropPos < CROP_KEYS.length) ? CROP_KEYS[currentCropPos] : "potato";
        if (!currentCrop.equalsIgnoreCase(savedCropKey)) {
            for (int i = 0; i < CROP_KEYS.length; i++) {
                if (CROP_KEYS[i].equalsIgnoreCase(savedCropKey)) {
                    binding.spinnerCrop.setSelection(i);
                    changed = true;
                    break;
                }
            }
        }

        isSyncingSelection = false;
        if (changed) {
            updateRankingsAndProfits();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        homeExtras.refreshWeatherText();
        binding.textNet.setText(ConnectivityReceiver.isOnline(this)
                ? R.string.net_online : R.string.net_offline);

        connectivityReceiver = new ConnectivityReceiver((online, regained) -> {
            binding.textNet.setText(online ? R.string.net_online : R.string.net_offline);
            if (regained) {
                startSync();
                homeExtras.refreshWeatherSilently();
            }
        });
        ContextCompat.registerReceiver(this, connectivityReceiver,
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION),
                ContextCompat.RECEIVER_EXPORTED);

        syncDoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                binding.textSync.setText(getString(R.string.sync_done,
                        intent.getStringExtra(SyncService.EXTRA_SUMMARY)));
                homeExtras.refreshWeatherText();
                updateRankingsAndProfits();
            }
        };
        ContextCompat.registerReceiver(this, syncDoneReceiver,
                new IntentFilter(SyncService.ACTION_SYNC_DONE),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        receiversRegistered = true;
    }

    @Override
    protected void onStop() {
        if (receiversRegistered) {
            unregisterReceiver(connectivityReceiver);
            unregisterReceiver(syncDoneReceiver);
            receiversRegistered = false;
        }
        super.onStop();
    }
}
