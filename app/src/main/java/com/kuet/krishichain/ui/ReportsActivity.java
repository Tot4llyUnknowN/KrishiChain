package com.kuet.krishichain.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;
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
import com.kuet.krishichain.databinding.ActivityReportsBinding;
import com.kuet.krishichain.databinding.DialogReportPriceBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReportsActivity extends AppCompatActivity {

    private ActivityReportsBinding binding;
    private PriceReportAdapter adapter;
    private final List<PriceReport> currentReports = new ArrayList<>();
    private final Map<Integer, String> nodeNames = new HashMap<>();
    private final List<NodeEntity> hubNodes = new ArrayList<>();
    private ListenerRegistration firestoreListener;
    private boolean isAlertsTabSelected = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReportsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        BottomNavHelper.setup(this, BottomNavHelper.Tab.PRICES, binding.getRoot());

        binding.buttonBack.setOnClickListener(v -> finish());

        adapter = new PriceReportAdapter(nodeNames);
        binding.recyclerReports.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerReports.setAdapter(adapter);

        binding.tabLatestPrices.setOnClickListener(v -> setTab(false));
        binding.tabPriceAlerts.setOnClickListener(v -> setTab(true));

        binding.buttonMergeSort.setOnClickListener(v -> {
            if (currentReports.isEmpty()) return;
            Sorts.mergeSort(currentReports, Sorts.byCropPriceDate());
            adapter.setReports(currentReports);
            Toast.makeText(this, "Sorted using Merge Sort", Toast.LENGTH_SHORT).show();
        });

        binding.buttonFilterOutliers.setOnClickListener(v -> {
            if (currentReports.isEmpty()) return;
            OutlierFilter filter = new OutlierFilter(0.40);
            OutlierFilter.FilterResult result = filter.filter(currentReports);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "Filtered: " + result.rejectedReports.size() + " outliers marked", Toast.LENGTH_SHORT).show();
        });

        binding.buttonSubmitPrice.setOnClickListener(v -> showReportPriceDialog());

        loadHubs();
        subscribeToReports();
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavHelper.setup(this, BottomNavHelper.Tab.PRICES, binding.getRoot());
    }

    private void setTab(boolean alertsTab) {
        isAlertsTabSelected = alertsTab;
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
                if ("hub".equalsIgnoreCase(n.type) || "market".equalsIgnoreCase(n.type)) {
                    hubNodes.add(n);
                }
            }
            runOnUiThread(() -> adapter.notifyDataSetChanged());
        });
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
                    Toast.makeText(ReportsActivity.this, "Price report submitted!", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }

                @Override
                public void onError(String error) {
                    FirebaseUser current = FirebaseRepository.getCurrentUser();
                    String reporter = current != null ? (current.getEmail() != null ? current.getEmail() : current.getUid()) : "local_user";
                    PriceReport local = new PriceReport(cropFinal, priceFinal, System.currentTimeMillis(), nodeId, reporter);
                    currentReports.add(0, local);
                    adapter.setReports(currentReports);
                    Toast.makeText(ReportsActivity.this, "Report saved locally!", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }
            });
        });

        db.dialogButtonCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void subscribeToReports() {
        firestoreListener = FirebaseRepository.listenToReports(new FirebaseRepository.ReportsCallback() {
            @Override
            public void onSuccess(List<PriceReport> reports) {
                currentReports.clear();
                if (reports != null && !reports.isEmpty()) {
                    currentReports.addAll(reports);
                } else {
                    currentReports.addAll(FirebaseRepository.getDemoReports());
                }
                // Mark initial outliers
                new OutlierFilter(0.40).filter(currentReports);
                adapter.setReports(currentReports);
            }

            @Override
            public void onError(Exception e) {
                if (currentReports.isEmpty()) {
                    currentReports.addAll(FirebaseRepository.getDemoReports());
                    new OutlierFilter(0.40).filter(currentReports);
                    adapter.setReports(currentReports);
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
