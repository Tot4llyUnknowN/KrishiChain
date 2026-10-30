package com.kuet.krishichain.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.Nullable;
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
import com.kuet.krishichain.databinding.DialogAuthBinding;

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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReportsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new PriceReportAdapter(nodeNames);
        binding.recyclerReports.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerReports.setAdapter(adapter);

        setupSpinners();
        setupListeners();
        subscribeToReports();
    }

    private void setupSpinners() {
        ArrayAdapter<CharSequence> cropAdapter = ArrayAdapter.createFromResource(this,
                R.array.crop_names, android.R.layout.simple_spinner_item);
        cropAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerCrop.setAdapter(cropAdapter);

        AppDatabase.IO.execute(() -> {
            try {
                NetworkLoader.loadIfNeeded(getApplicationContext());
            } catch (Exception ignored) {}
            List<NodeEntity> all = AppDatabase.get(this).networkDao().getAllNodes();
            hubNodes.clear();
            nodeNames.clear();
            List<String> displayNames = new ArrayList<>();
            for (NodeEntity n : all) {
                nodeNames.put(n.id, n.name);
                if ("hub".equalsIgnoreCase(n.type) || "market".equalsIgnoreCase(n.type)) {
                    hubNodes.add(n);
                    displayNames.add(n.name + " (" + n.type + ")");
                }
            }

            runOnUiThread(() -> {
                ArrayAdapter<String> hubAdapter = new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_item, displayNames);
                hubAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                binding.spinnerMarket.setAdapter(hubAdapter);
                adapter.notifyDataSetChanged();
            });
        });
    }

    private void setupListeners() {
        updateAuthUi();
        binding.buttonManageAuth.setOnClickListener(v -> showAuthDialog());

        binding.buttonSubmit.setOnClickListener(v -> {
            String priceStr = binding.editPrice.getText().toString().trim();
            if (TextUtils.isEmpty(priceStr)) {
                binding.editPrice.setError("Enter a valid price");
                return;
            }
            double price;
            try {
                price = Double.parseDouble(priceStr);
                if (price <= 0) throw new NumberFormatException();
            } catch (Exception e) {
                binding.editPrice.setError("Enter a price greater than 0");
                return;
            }

            int hubIndex = binding.spinnerMarket.getSelectedItemPosition();
            if (hubIndex < 0 || hubIndex >= hubNodes.size()) {
                Toast.makeText(this, "Select a valid market", Toast.LENGTH_SHORT).show();
                return;
            }
            int nodeId = hubNodes.get(hubIndex).id;
            String crop = binding.spinnerCrop.getSelectedItem().toString().toLowerCase(Locale.ROOT);

            binding.buttonSubmit.setEnabled(false);
            FirebaseRepository.submitPriceReport(crop, price, nodeId, new FirebaseRepository.CompleteCallback() {
                @Override
                public void onSuccess(String message) {
                    binding.buttonSubmit.setEnabled(true);
                    binding.editPrice.setText("");
                    Toast.makeText(ReportsActivity.this, message, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    binding.buttonSubmit.setEnabled(true);
                    // Local fallback so submission is visible immediately even if Firestore rules are locked
                    FirebaseUser current = FirebaseRepository.getCurrentUser();
                    String reporter = (current != null && !current.isAnonymous() && current.getEmail() != null)
                            ? current.getEmail()
                            : (current != null ? current.getUid() : "local_user");
                    PriceReport localReport = new PriceReport(crop, price, System.currentTimeMillis(), nodeId, reporter);
                    currentReports.add(0, localReport);
                    adapter.setReports(currentReports);
                    binding.editPrice.setText("");
                    binding.textReportCount.setText(String.format(Locale.US, "%d reports (added local draft)", currentReports.size()));
                    Toast.makeText(ReportsActivity.this, "Added locally! (Firestore: " + error + ")", Toast.LENGTH_LONG).show();
                }
            });
        });

        binding.buttonSort.setOnClickListener(v -> {
            if (currentReports.isEmpty()) return;
            Sorts.mergeSort(currentReports, Sorts.byCropPriceDate());
            adapter.setReports(currentReports);
            Toast.makeText(this, "Sorted using MergeSort (stable: Crop → Price → Date)", Toast.LENGTH_SHORT).show();
        });

        binding.buttonFilterOutliers.setOnClickListener(v -> {
            if (currentReports.isEmpty()) return;
            OutlierFilter filter = new OutlierFilter(0.40);
            OutlierFilter.FilterResult result = filter.filter(currentReports);
            adapter.notifyDataSetChanged();
            String summary = String.format(Locale.US, "Filter applied: %d valid, %d outliers rejected (>40%% from median)",
                    result.validReports.size(), result.rejectedReports.size());
            binding.textReportCount.setText(summary);
            Toast.makeText(this, summary, Toast.LENGTH_LONG).show();
        });

        binding.buttonSyncToDb.setOnClickListener(v -> {
            binding.buttonSyncToDb.setEnabled(false);
            AppDatabase.IO.execute(() -> {
                try {
                    int count = FirebaseRepository.syncCrowdReports(getApplicationContext(), currentReports);
                    runOnUiThread(() -> {
                        binding.buttonSyncToDb.setEnabled(true);
                        Toast.makeText(this, "Synced " + count + " verified crowd prices to local Room database!", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        binding.buttonSyncToDb.setEnabled(true);
                        Toast.makeText(this, "Sync failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });
    }

    private void subscribeToReports() {
        firestoreListener = FirebaseRepository.listenToReports(new FirebaseRepository.ReportsCallback() {
            @Override
            public void onSuccess(List<PriceReport> reports) {
                currentReports.clear();
                if (reports != null && !reports.isEmpty()) {
                    currentReports.addAll(reports);
                    binding.textReportCount.setText(String.format(Locale.US, "%d live reports from Firestore", reports.size()));
                } else {
                    currentReports.addAll(FirebaseRepository.getDemoReports());
                    binding.textReportCount.setText(String.format(Locale.US, "%d demo crowd reports loaded (includes outliers)", currentReports.size()));
                }
                adapter.setReports(currentReports);
            }

            @Override
            public void onError(Exception e) {
                if (currentReports.isEmpty()) {
                    currentReports.addAll(FirebaseRepository.getDemoReports());
                    adapter.setReports(currentReports);
                }
                binding.textReportCount.setText(String.format(Locale.US,
                        "Firestore: %s\nLoaded %d demo crowd reports (with outliers for demo)",
                        e.getMessage(), currentReports.size()));
            }
        });
    }

    private void updateAuthUi() {
        FirebaseUser user = FirebaseRepository.getCurrentUser();
        if (user != null && !user.isAnonymous() && user.getEmail() != null) {
            binding.textAuthUser.setText("👤 " + user.getEmail());
            binding.buttonManageAuth.setText("Account");
        } else if (user != null && user.isAnonymous()) {
            String uid = user.getUid();
            binding.textAuthUser.setText("👤 Guest (" + uid.substring(0, Math.min(6, uid.length())) + "...)");
            binding.buttonManageAuth.setText("Sign In");
        } else {
            binding.textAuthUser.setText("👤 Guest Mode");
            binding.buttonManageAuth.setText("Sign In");
        }
    }

    private void showAuthDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        DialogAuthBinding dBinding = DialogAuthBinding.inflate(getLayoutInflater());
        builder.setView(dBinding.getRoot());
        android.app.AlertDialog dialog = builder.create();

        FirebaseUser current = FirebaseRepository.getCurrentUser();
        if (current != null && !current.isAnonymous() && current.getEmail() != null) {
            dBinding.textCurrentStatus.setText("Currently signed in as: " + current.getEmail());
            dBinding.editAuthEmail.setText(current.getEmail());
            dBinding.buttonSignOut.setVisibility(View.VISIBLE);
        } else if (current != null && current.isAnonymous()) {
            String uid = current.getUid();
            dBinding.textCurrentStatus.setText("Currently signed in as: Guest (" + uid.substring(0, Math.min(6, uid.length())) + "...)");
            dBinding.buttonSignOut.setVisibility(View.VISIBLE);
        } else {
            dBinding.textCurrentStatus.setText("Currently: Not signed in");
            dBinding.buttonSignOut.setVisibility(View.GONE);
        }

        dBinding.buttonSignIn.setOnClickListener(v -> {
            String email = dBinding.editAuthEmail.getText() != null ? dBinding.editAuthEmail.getText().toString().trim() : "";
            String password = dBinding.editAuthPassword.getText() != null ? dBinding.editAuthPassword.getText().toString().trim() : "";
            if (TextUtils.isEmpty(email) || !email.contains("@")) {
                dBinding.editAuthEmail.setError("Enter a valid email");
                return;
            }
            if (TextUtils.isEmpty(password) || password.length() < 6) {
                dBinding.editAuthPassword.setError("Password must be at least 6 characters");
                return;
            }
            dBinding.buttonSignIn.setEnabled(false);
            FirebaseRepository.signInWithEmail(email, password, new FirebaseRepository.CompleteCallback() {
                @Override
                public void onSuccess(String message) {
                    dialog.dismiss();
                    updateAuthUi();
                    Toast.makeText(ReportsActivity.this, message, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    dBinding.buttonSignIn.setEnabled(true);
                    Toast.makeText(ReportsActivity.this, error, Toast.LENGTH_LONG).show();
                }
            });
        });

        dBinding.buttonRegister.setOnClickListener(v -> {
            String email = dBinding.editAuthEmail.getText() != null ? dBinding.editAuthEmail.getText().toString().trim() : "";
            String password = dBinding.editAuthPassword.getText() != null ? dBinding.editAuthPassword.getText().toString().trim() : "";
            if (TextUtils.isEmpty(email) || !email.contains("@")) {
                dBinding.editAuthEmail.setError("Enter a valid email");
                return;
            }
            if (TextUtils.isEmpty(password) || password.length() < 6) {
                dBinding.editAuthPassword.setError("Password must be at least 6 characters");
                return;
            }
            dBinding.buttonRegister.setEnabled(false);
            FirebaseRepository.signUpWithEmail(email, password, new FirebaseRepository.CompleteCallback() {
                @Override
                public void onSuccess(String message) {
                    dialog.dismiss();
                    updateAuthUi();
                    Toast.makeText(ReportsActivity.this, message, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    dBinding.buttonRegister.setEnabled(true);
                    Toast.makeText(ReportsActivity.this, error, Toast.LENGTH_LONG).show();
                }
            });
        });

        dBinding.buttonGuest.setOnClickListener(v -> {
            dBinding.buttonGuest.setEnabled(false);
            FirebaseRepository.signInAsGuest(new FirebaseRepository.CompleteCallback() {
                @Override
                public void onSuccess(String message) {
                    dialog.dismiss();
                    updateAuthUi();
                    Toast.makeText(ReportsActivity.this, message, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    dBinding.buttonGuest.setEnabled(true);
                    Toast.makeText(ReportsActivity.this, error, Toast.LENGTH_SHORT).show();
                }
            });
        });

        dBinding.buttonSignOut.setOnClickListener(v -> {
            FirebaseRepository.signOut();
            dialog.dismiss();
            updateAuthUi();
            Toast.makeText(ReportsActivity.this, "Signed out", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (firestoreListener != null) {
            firestoreListener.remove();
        }
    }
}
