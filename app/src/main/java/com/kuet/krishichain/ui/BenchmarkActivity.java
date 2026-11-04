package com.kuet.krishichain.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseUser;
import com.kuet.krishichain.R;
import com.kuet.krishichain.SplashActivity;
import com.kuet.krishichain.algo.AllPairsResult;
import com.kuet.krishichain.algo.FloydWarshall;
import com.kuet.krishichain.algo.Graph;
import com.kuet.krishichain.algo.GraphGenerator;
import com.kuet.krishichain.algo.Johnson;
import com.kuet.krishichain.data.AppDatabase;
import com.kuet.krishichain.data.FirebaseRepository;
import com.kuet.krishichain.data.NetworkLoader;
import com.kuet.krishichain.databinding.ActivityBenchmarkBinding;
import com.kuet.krishichain.databinding.DialogChangePasswordBinding;
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
        TopBarHelper.setup(this, binding.getRoot());
        binding.buttonBenchmark.setOnClickListener(v -> start());

        // Bottleneck Analyzer entry point
        binding.cardBottleneckAnalyzer.setOnClickListener(v ->
                startActivity(new Intent(this, BottleneckActivity.class)));

        // Account management
        updateAccountInfo();
        binding.cardChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        binding.cardLogout.setOnClickListener(v -> showLogoutConfirmation());

        binding.switchAutoSync.setOnCheckedChangeListener((btn, isChecked) -> {
            Toast.makeText(this, isChecked ? "Auto sync (Wi-Fi) on" : "Auto sync off", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavHelper.setup(this, BottomNavHelper.Tab.MORE, binding.getRoot());
        TopBarHelper.setup(this, binding.getRoot());
        updateAccountInfo();
    }

    private void updateAccountInfo() {
        FirebaseUser user = FirebaseRepository.getCurrentUser();
        if (user != null && !user.isAnonymous()) {
            String email = user.getEmail() != null ? user.getEmail() : "Registered User";
            binding.textAccountEmail.setText(email);
            binding.textAccountStatus.setText(user.isEmailVerified() ? "✓ Verified Account" : "⚠ Email Verification Pending");
            binding.textAccountStatus.setTextColor(user.isEmailVerified() ? Color.parseColor("#1B7A2F") : Color.parseColor("#F57C00"));
        } else {
            binding.textAccountEmail.setText("Guest Mode");
            binding.textAccountStatus.setText("Local session (unregistered)");
            binding.textAccountStatus.setTextColor(Color.parseColor("#6B7F72"));
        }
    }

    private void showChangePasswordDialog() {
        FirebaseUser user = FirebaseRepository.getCurrentUser();
        if (user == null || user.isAnonymous() || user.getEmail() == null) {
            new AlertDialog.Builder(this)
                    .setTitle("Guest Mode")
                    .setMessage("You are currently using KrishiChain as a guest. Password change is only available for registered email accounts. Would you like to go to registration?")
                    .setPositiveButton("Sign In / Register", (d, w) -> {
                        FirebaseRepository.signOut();
                        Intent intent = new Intent(this, SplashActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        DialogChangePasswordBinding db = DialogChangePasswordBinding.inflate(LayoutInflater.from(this));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(db.getRoot())
                .setCancelable(true)
                .create();

        db.textUserAccountEmail.setText("Account: " + user.getEmail());

        db.buttonSendResetEmail.setOnClickListener(v -> {
            FirebaseRepository.sendPasswordResetEmail(user.getEmail(), new FirebaseRepository.CompleteCallback() {
                @Override
                public void onSuccess(String message) {
                    Toast.makeText(BenchmarkActivity.this, message, Toast.LENGTH_LONG).show();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(BenchmarkActivity.this, error, Toast.LENGTH_SHORT).show();
                }
            });
        });

        db.buttonSavePassword.setOnClickListener(v -> {
            String current = db.editCurrentPassword.getText() != null ? db.editCurrentPassword.getText().toString().trim() : "";
            String newPass = db.editNewPassword.getText() != null ? db.editNewPassword.getText().toString().trim() : "";
            String confirm = db.editConfirmPassword.getText() != null ? db.editConfirmPassword.getText().toString().trim() : "";

            if (TextUtils.isEmpty(current)) {
                db.editCurrentPassword.setError("Enter current password");
                return;
            }
            if (TextUtils.isEmpty(newPass) || newPass.length() < 6) {
                db.editNewPassword.setError("Minimum 6 characters required");
                return;
            }
            if (!newPass.equals(confirm)) {
                db.editConfirmPassword.setError("Passwords do not match");
                return;
            }

            db.buttonSavePassword.setEnabled(false);
            FirebaseRepository.changePassword(current, newPass, new FirebaseRepository.CompleteCallback() {
                @Override
                public void onSuccess(String message) {
                    Toast.makeText(BenchmarkActivity.this, message, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }

                @Override
                public void onError(String error) {
                    db.buttonSavePassword.setEnabled(true);
                    Toast.makeText(BenchmarkActivity.this, error, Toast.LENGTH_LONG).show();
                }
            });
        });

        db.buttonCancelPassword.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out of KrishiChain?")
                .setPositiveButton("Log Out", (d, w) -> {
                    FirebaseRepository.signOut();
                    Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(this, SplashActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
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
