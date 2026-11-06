package com.kuet.krishichain.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseUser;
import com.kuet.krishichain.SplashActivity;
import com.kuet.krishichain.data.FirebaseRepository;
import com.kuet.krishichain.databinding.ActivityBenchmarkBinding;
import com.kuet.krishichain.databinding.DialogChangePasswordBinding;

public class BenchmarkActivity extends AppCompatActivity {

    private ActivityBenchmarkBinding binding;

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

        // Bottleneck Analyzer entry point
        binding.cardBottleneckAnalyzer.setOnClickListener(v ->
                startActivity(new Intent(this, BottleneckActivity.class)));

        // Graph Lab Visualizer entry point
        binding.cardGraphLab.setOnClickListener(v ->
                startActivity(new Intent(this, GraphLabActivity.class)));

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
}
