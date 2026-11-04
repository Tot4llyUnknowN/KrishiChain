package com.kuet.krishichain.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.krishichain.R;
import com.kuet.krishichain.algo.OutlierFilter;
import com.kuet.krishichain.algo.PriceReport;
import com.kuet.krishichain.algo.Sorts;
import com.kuet.krishichain.data.AppDatabase;
import com.kuet.krishichain.data.FirebaseRepository;
import com.kuet.krishichain.data.NetworkLoader;
import com.kuet.krishichain.data.NodeEntity;
import com.kuet.krishichain.data.PriceEntity;
import com.kuet.krishichain.data.UserPreferences;
import com.kuet.krishichain.databinding.ActivityReportsBinding;
import com.kuet.krishichain.databinding.DialogReportPriceBinding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ReportsActivity extends AppCompatActivity {

    private ActivityReportsBinding binding;
    private PriceReportAdapter adapter;
    private final List<PriceReport> rawReports = new ArrayList<>();
    private final List<PriceReport> displayedReports = new ArrayList<>();
    private final Map<Integer, String> nodeNames = new HashMap<>();
    private final List<NodeEntity> hubNodes = new ArrayList<>();
    private ListenerRegistration firestoreListener;
    private boolean isSortByPrice = true; // true = Sort by Price (High to Low), false = Sort by District (A to Z)
    private String currentCropKey = "potato";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReportsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        BottomNavHelper.setup(this, BottomNavHelper.Tab.PRICES, binding.getRoot());
        TopBarHelper.setup(this, binding.getRoot());

        binding.buttonBack.setOnClickListener(v -> finish());

        adapter = new PriceReportAdapter(nodeNames);
        binding.recyclerReports.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerReports.setAdapter(adapter);

        binding.tabLatestPrices.setOnClickListener(v -> setTab(false));
        binding.tabPriceAlerts.setOnClickListener(v -> setTab(true));

        currentCropKey = UserPreferences.getCropKey(this, "potato");
        updateCropHeader();

        // Sort by Price button (High -> Low)
        binding.buttonSortPrice.setOnClickListener(v -> {
            isSortByPrice = true;
            updateSortButtonsUI();
            filterAndDisplayReports();
            Toast.makeText(this, "Sorted by Price (Highest first)", Toast.LENGTH_SHORT).show();
        });

        // Sort by District button (A -> Z)
        binding.buttonSortDistrict.setOnClickListener(v -> {
            isSortByPrice = false;
            updateSortButtonsUI();
            filterAndDisplayReports();
            Toast.makeText(this, "Sorted by District (A to Z)", Toast.LENGTH_SHORT).show();
        });

        updateSortButtonsUI();

        binding.buttonSubmitPrice.setOnClickListener(v -> showReportPriceDialog());

        loadHubs();
        subscribeToReports();
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavHelper.setup(this, BottomNavHelper.Tab.PRICES, binding.getRoot());
        TopBarHelper.setup(this, binding.getRoot());
        String cropKey = UserPreferences.getCropKey(this, "potato");
        if (!cropKey.equalsIgnoreCase(currentCropKey)) {
            currentCropKey = cropKey;
            updateCropHeader();
            filterAndDisplayReports();
        }
    }

    private void updateCropHeader() {
        String emoji = "🥔";
        String cropName = "Potato";
        if (currentCropKey != null) {
            if (currentCropKey.contains("onion")) {
                emoji = "🧅";
                cropName = "Onion";
            } else if (currentCropKey.contains("tomato")) {
                emoji = "🍅";
                cropName = "Tomato";
            }
        }
        binding.textCropHeader.setText(String.format(Locale.US,
                "%s Prices for %s (64 Market Hubs)", emoji, cropName));
    }

    private void updateSortButtonsUI() {
        int colorActiveBg = Color.parseColor("#1B7A2F");
        int colorActiveText = Color.WHITE;
        int colorInactiveBg = Color.parseColor("#EDF6EE");
        int colorInactiveText = Color.parseColor("#1B4D2E");

        if (isSortByPrice) {
            binding.buttonSortPrice.setBackgroundTintList(ColorStateList.valueOf(colorActiveBg));
            binding.buttonSortPrice.setTextColor(colorActiveText);
            binding.buttonSortDistrict.setBackgroundTintList(ColorStateList.valueOf(colorInactiveBg));
            binding.buttonSortDistrict.setTextColor(colorInactiveText);
        } else {
            binding.buttonSortDistrict.setBackgroundTintList(ColorStateList.valueOf(colorActiveBg));
            binding.buttonSortDistrict.setTextColor(colorActiveText);
            binding.buttonSortPrice.setBackgroundTintList(ColorStateList.valueOf(colorInactiveBg));
            binding.buttonSortPrice.setTextColor(colorInactiveText);
        }
    }

    private void setTab(boolean alertsTab) {
        if (alertsTab) {
            binding.tabPriceAlerts.setBackgroundResource(R.drawable.bg_tab_on);
            binding.tabPriceAlerts.setTextColor(Color.WHITE);
            binding.tabLatestPrices.setBackgroundResource(R.drawable.bg_tab_off);
            binding.tabLatestPrices.setTextColor(Color.parseColor("#17261A"));
            binding.cardPriceAlert.setVisibility(View.VISIBLE);
        } else {
            binding.tabLatestPrices.setBackgroundResource(R.drawable.bg_tab_on);
            binding.tabLatestPrices.setTextColor(Color.WHITE);
            binding.tabPriceAlerts.setBackgroundResource(R.drawable.bg_tab_off);
            binding.tabPriceAlerts.setTextColor(Color.parseColor("#17261A"));
            binding.cardPriceAlert.setVisibility(View.GONE);
        }
    }

    private void loadHubs() {
        AppDatabase.IO.execute(() -> {
            try {
                NetworkLoader.loadIfNeeded(getApplicationContext());
            } catch (Exception ignored) {}
            List<NodeEntity> all = AppDatabase.get(this).networkDao().getAllNodes();
            hubNodes.clear();
            nodeNames.clear();
            for (NodeEntity n : all) {
                nodeNames.put(n.id, n.name);
                hubNodes.add(n);
            }
            // If rawReports is empty, seed from local Room DB prices
            if (rawReports.isEmpty()) {
                loadLocalPricesFallback();
            }
        });
    }

    private void loadLocalPricesFallback() {
        AppDatabase.IO.execute(() -> {
            List<PriceEntity> prices = AppDatabase.get(this).networkDao().getAllPrices();
            if (prices != null && !prices.isEmpty()) {
                long now = System.currentTimeMillis();
                List<PriceReport> local = new ArrayList<>();
                for (PriceEntity p : prices) {
                    local.add(new PriceReport(p.crop, p.price, now, p.nodeId, "Official DAM"));
                }
                runOnUiThread(() -> {
                    if (rawReports.isEmpty()) {
                        rawReports.addAll(local);
                        filterAndDisplayReports();
                    }
                });
            }
        });
    }

    private void filterAndDisplayReports() {
        if (rawReports.isEmpty()) {
            adapter.setReports(new ArrayList<>());
            return;
        }

        // 1. Filter out outliers using OutlierFilter (user specified: dont show outlier prices)
        OutlierFilter filter = new OutlierFilter(0.40);
        OutlierFilter.FilterResult filterResult = filter.filter(rawReports);

        // 2. Filter strictly for the selected crop
        List<PriceReport> validForCrop = new ArrayList<>();
        Set<Integer> seenNodes = new HashSet<>();
        for (PriceReport r : filterResult.validReports) {
            if (r.crop != null && r.crop.equalsIgnoreCase(currentCropKey)) {
                // Keep the freshest report per district node
                if (!seenNodes.contains(r.nodeId)) {
                    seenNodes.add(r.nodeId);
                    validForCrop.add(r);
                }
            }
        }

        // 3. Sort using CS algorithms
        if (isSortByPrice) {
            // Sort by Price (High to Low)
            Sorts.quickSort(validForCrop, new Comparator<PriceReport>() {
                @Override
                public int compare(PriceReport a, PriceReport b) {
                    return Double.compare(b.price, a.price);
                }
            });
        } else {
            // Sort by District (A to Z)
            Sorts.quickSort(validForCrop, new Comparator<PriceReport>() {
                @Override
                public int compare(PriceReport a, PriceReport b) {
                    String nameA = nodeNames.get(a.nodeId);
                    String nameB = nodeNames.get(b.nodeId);
                    if (nameA == null) nameA = "";
                    if (nameB == null) nameB = "";
                    return nameA.compareToIgnoreCase(nameB);
                }
            });
        }

        displayedReports.clear();
        displayedReports.addAll(validForCrop);
        adapter.setReports(displayedReports);
    }

    private void showReportPriceDialog() {
        DialogReportPriceBinding db = DialogReportPriceBinding.inflate(LayoutInflater.from(this));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(db.getRoot())
                .setCancelable(true)
                .create();

        ArrayAdapter<CharSequence> cropAdapter = ArrayAdapter.createFromResource(this,
                R.array.crop_names, android.R.layout.simple_spinner_item);
        cropAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        db.dialogSpinnerCrop.setAdapter(cropAdapter);

        // Pre-select current crop
        if ("tomato".equalsIgnoreCase(currentCropKey)) {
            db.dialogSpinnerCrop.setSelection(1);
        } else if ("onion".equalsIgnoreCase(currentCropKey)) {
            db.dialogSpinnerCrop.setSelection(2);
        } else {
            db.dialogSpinnerCrop.setSelection(0);
        }

        List<String> hubNames = new ArrayList<>();
        for (NodeEntity h : hubNodes) {
            hubNames.add(h.name);
        }
        ArrayAdapter<String> hubAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, hubNames);
        hubAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        db.dialogSpinnerHub.setAdapter(hubAdapter);

        db.dialogButtonSubmit.setOnClickListener(v -> {
            String priceStr = db.dialogEditPrice.getText().toString().trim();
            if (TextUtils.isEmpty(priceStr)) {
                db.dialogEditPrice.setError("Enter price");
                return;
            }
            double price;
            try {
                price = Double.parseDouble(priceStr);
                if (price <= 0) throw new NumberFormatException();
            } catch (Exception e) {
                db.dialogEditPrice.setError("Invalid price");
                return;
            }

            int hubIdx = db.dialogSpinnerHub.getSelectedItemPosition();
            if (hubIdx < 0 || hubIdx >= hubNodes.size()) return;
            int nodeId = hubNodes.get(hubIdx).id;
            String crop = db.dialogSpinnerCrop.getSelectedItem().toString().toLowerCase(Locale.ROOT);
            if (crop.contains("potato")) crop = "potato";
            else if (crop.contains("onion")) crop = "onion";
            else if (crop.contains("tomato")) crop = "tomato";

            final String cropFinal = crop;
            final double priceFinal = price;
            db.dialogButtonSubmit.setEnabled(false);
            FirebaseRepository.submitPriceReport(cropFinal, priceFinal, nodeId, new FirebaseRepository.CompleteCallback() {
                @Override
                public void onSuccess(String message) {
                    Toast.makeText(ReportsActivity.this, "Price report submitted to Firebase!", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }

                @Override
                public void onError(String error) {
                    FirebaseUser current = FirebaseRepository.getCurrentUser();
                    String reporter = current != null ? (current.getEmail() != null ? current.getEmail() : current.getUid()) : "local_user";
                    PriceReport local = new PriceReport(cropFinal, priceFinal, System.currentTimeMillis(), nodeId, reporter);
                    rawReports.add(0, local);
                    filterAndDisplayReports();
                    Toast.makeText(ReportsActivity.this, "Saved locally: " + error, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }
            });
        });

        db.dialogButtonCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void subscribeToReports() {
        FirebaseRepository.ensureAuth(new FirebaseRepository.AuthCallback() {
            @Override
            public void onSuccess(String uid) {
                attachFirestoreListener();
            }

            @Override
            public void onError(Exception e) {
                if (rawReports.isEmpty()) {
                    loadLocalPricesFallback();
                }
            }
        });
    }

    private void attachFirestoreListener() {
        if (firestoreListener != null) {
            firestoreListener.remove();
        }
        firestoreListener = FirebaseRepository.listenToReports(new FirebaseRepository.ReportsCallback() {
            @Override
            public void onSuccess(List<PriceReport> reports) {
                rawReports.clear();
                if (reports != null && !reports.isEmpty()) {
                    rawReports.addAll(reports);
                }

                // If Firestore has fewer than 64 reports, auto-seed the complete 64 districts' prices
                if (rawReports.size() < 64) {
                    FirebaseRepository.seed64DistrictPrices(getApplicationContext(), new FirebaseRepository.CompleteCallback() {
                        @Override
                        public void onSuccess(String message) {
                            // Seed committed; real-time listener will deliver updated reports
                        }

                        @Override
                        public void onError(String error) {
                            if (rawReports.isEmpty()) {
                                loadLocalPricesFallback();
                            }
                        }
                    });
                }

                if (rawReports.isEmpty()) {
                    loadLocalPricesFallback();
                } else {
                    filterAndDisplayReports();
                }
            }

            @Override
            public void onError(Exception e) {
                if (rawReports.isEmpty()) {
                    loadLocalPricesFallback();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (firestoreListener != null) {
            firestoreListener.remove();
            firestoreListener = null;
        }
    }
}
