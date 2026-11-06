package com.kuet.krishichain.ui;

import android.content.Intent;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.firebase.auth.FirebaseUser;
import com.kuet.krishichain.MainActivity;
import com.kuet.krishichain.SplashActivity;
import com.kuet.krishichain.data.FirebaseRepository;
import com.kuet.krishichain.databinding.DialogChangePasswordBinding;
import com.kuet.krishichain.databinding.TabMoreContentBinding;

public class MoreTabHelper {
    private final MainActivity activity;
    private final TabMoreContentBinding binding;

    public MoreTabHelper(MainActivity activity, TabMoreContentBinding binding) {
        this.activity = activity;
        this.binding = binding;
    }

    public void setup() {
        binding.buttonBack.setOnClickListener(v -> activity.selectTab(BottomNavHelper.Tab.HOME));

        // Bottleneck Analyzer entry point
        binding.cardBottleneckAnalyzer.setOnClickListener(v ->
                activity.startActivity(new Intent(activity, BottleneckActivity.class)));

        // Graph Lab Visualizer entry point
        binding.cardGraphLab.setOnClickListener(v ->
                activity.startActivity(new Intent(activity, GraphLabActivity.class)));

        // Account management
        updateAccountInfo();
        binding.cardChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        binding.cardLogout.setOnClickListener(v -> showLogoutConfirmation());

        binding.switchAutoSync.setOnCheckedChangeListener((btn, isChecked) -> {
            Toast.makeText(activity, isChecked ? "Auto sync (Wi-Fi) on" : "Auto sync off", Toast.LENGTH_SHORT).show();
        });
    }

    public void onTabSelected() {
        updateAccountInfo();
    }

    public void updateAccountInfo() {
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
            new AlertDialog.Builder(activity)
                    .setTitle("Guest Mode")
                    .setMessage("You are currently using KrishiChain as a guest. Password change is only available for registered email accounts. Would you like to go to registration?")
                    .setPositiveButton("Sign In / Register", (d, w) -> {
                        FirebaseRepository.signOut();
                        Intent intent = new Intent(activity, SplashActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        activity.startActivity(intent);
                        activity.finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        DialogChangePasswordBinding dialogBinding = DialogChangePasswordBinding.inflate(
                LayoutInflater.from(activity));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(dialogBinding.getRoot())
                .setCancelable(true)
                .create();

        dialogBinding.textUserAccountEmail.setText("Account: " + user.getEmail());

        dialogBinding.buttonCancelPassword.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.buttonSendResetEmail.setOnClickListener(v -> {
            FirebaseRepository.sendPasswordResetEmail(user.getEmail(), new FirebaseRepository.CompleteCallback() {
                @Override
                public void onSuccess(String message) {
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity, "Reset email sent to " + user.getEmail(), Toast.LENGTH_LONG).show());
                }

                @Override
                public void onError(String error) {
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity, error, Toast.LENGTH_SHORT).show());
                }
            });
        });

        dialogBinding.buttonSavePassword.setOnClickListener(v -> {
            String oldPass = dialogBinding.editCurrentPassword.getText() != null
                    ? dialogBinding.editCurrentPassword.getText().toString().trim() : "";
            String newPass = dialogBinding.editNewPassword.getText() != null
                    ? dialogBinding.editNewPassword.getText().toString().trim() : "";
            String confirmPass = dialogBinding.editConfirmPassword.getText() != null
                    ? dialogBinding.editConfirmPassword.getText().toString().trim() : "";

            if (TextUtils.isEmpty(oldPass)) {
                dialogBinding.editCurrentPassword.setError("Required");
                return;
            }
            if (TextUtils.isEmpty(newPass) || newPass.length() < 6) {
                dialogBinding.editNewPassword.setError("Minimum 6 characters");
                return;
            }
            if (!newPass.equals(confirmPass)) {
                dialogBinding.editConfirmPassword.setError("Passwords do not match");
                return;
            }

            dialogBinding.buttonSavePassword.setEnabled(false);
            FirebaseRepository.changePassword(oldPass, newPass, new FirebaseRepository.CompleteCallback() {
                @Override
                public void onSuccess(String message) {
                    activity.runOnUiThread(() -> {
                        dialogBinding.buttonSavePassword.setEnabled(true);
                        Toast.makeText(activity, message != null ? message : "Password updated successfully!", Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                    });
                }

                @Override
                public void onError(String error) {
                    activity.runOnUiThread(() -> {
                        dialogBinding.buttonSavePassword.setEnabled(true);
                        Toast.makeText(activity, error != null ? error : "Failed to change password", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });

        dialog.show();
    }

    private void showLogoutConfirmation() {
        FirebaseUser user = FirebaseRepository.getCurrentUser();
        String accountText = (user != null && user.getEmail() != null)
                ? user.getEmail() : "your local session";

        new AlertDialog.Builder(activity)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out of " + accountText + "?")
                .setPositiveButton("Log Out", (dialog, which) -> {
                    FirebaseRepository.signOut();
                    Intent intent = new Intent(activity, SplashActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    activity.startActivity(intent);
                    activity.finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
