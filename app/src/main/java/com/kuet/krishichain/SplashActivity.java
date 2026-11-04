package com.kuet.krishichain;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.firebase.auth.FirebaseUser;
import com.kuet.krishichain.data.FirebaseRepository;
import com.kuet.krishichain.databinding.ActivitySplashBinding;
import com.kuet.krishichain.databinding.DialogLoginBinding;
import com.kuet.krishichain.databinding.DialogRegisterBinding;

public class SplashActivity extends AppCompatActivity {

    private ActivitySplashBinding binding;
    private DialogRegisterBinding activeRegisterBinding;
    private AlertDialog activeRegisterDialog;
    private boolean isVerifying = false;
    private final Handler verificationHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);

        // If user is already signed in with a VERIFIED permanent account, skip login page
        FirebaseUser current = FirebaseRepository.getCurrentUser();
        if (current != null && !current.isAnonymous() && current.isEmailVerified()) {
            openMainActivity();
            return;
        } else if (current != null && !current.isAnonymous() && !current.isEmailVerified()) {
            // Sign out unverified account from previous incomplete session
            FirebaseRepository.signOut();
        }

        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.buttonLogin.setOnClickListener(v -> showLoginDialog());
        binding.buttonRegister.setOnClickListener(v -> showRegisterDialog());
        binding.textGuest.setOnClickListener(v -> {
            FirebaseUser u = FirebaseRepository.getCurrentUser();
            if (u != null && (!u.isEmailVerified() || !u.isAnonymous())) {
                FirebaseRepository.signOut();
            }
            FirebaseRepository.signInAsGuest(new FirebaseRepository.CompleteCallback() {
                @Override
                public void onSuccess(String message) {
                    openMainActivity();
                }

                @Override
                public void onError(String error) {
                    openMainActivity();
                }
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If user is returning from email/browser, automatically verify in background
        if (activeRegisterDialog != null && activeRegisterDialog.isShowing()
                && activeRegisterBinding != null
                && activeRegisterBinding.layoutVerificationBox.getVisibility() == View.VISIBLE
                && !isVerifying) {
            startVerificationPolling(activeRegisterBinding, activeRegisterDialog);
        }
    }

    private void openMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void showLoginDialog() {
        DialogLoginBinding db = DialogLoginBinding.inflate(LayoutInflater.from(this));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(db.getRoot())
                .setCancelable(true)
                .create();

        db.buttonSignIn.setOnClickListener(v -> {
            String email = db.editLoginEmail.getText() != null ? db.editLoginEmail.getText().toString().trim() : "";
            String pass = db.editLoginPassword.getText() != null ? db.editLoginPassword.getText().toString().trim() : "";

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(pass)) {
                Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseRepository.signInWithEmail(email, pass, new FirebaseRepository.CompleteCallback() {
                @Override
                public void onSuccess(String message) {
                    Toast.makeText(SplashActivity.this, message, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    openMainActivity();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(SplashActivity.this, error, Toast.LENGTH_LONG).show();
                }
            });
        });

        dialog.show();
    }

    private void showRegisterDialog() {
        DialogRegisterBinding db = DialogRegisterBinding.inflate(LayoutInflater.from(this));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(db.getRoot())
                .setCancelable(true)
                .create();

        activeRegisterBinding = db;
        activeRegisterDialog = dialog;
        dialog.setOnDismissListener(d -> {
            isVerifying = false;
            verificationHandler.removeCallbacksAndMessages(null);
            activeRegisterBinding = null;
            activeRegisterDialog = null;
            FirebaseUser u = FirebaseRepository.getCurrentUser();
            if (u != null && !u.isAnonymous() && !u.isEmailVerified()) {
                // If user dismissed dialog without completing verification, clear session
                FirebaseRepository.signOut();
            }
        });

        db.buttonRegister.setOnClickListener(v -> {
            String email = db.editRegisterEmail.getText() != null ? db.editRegisterEmail.getText().toString().trim() : "";
            String pass = db.editRegisterPassword.getText() != null ? db.editRegisterPassword.getText().toString().trim() : "";

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(pass)) {
                Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }
            if (pass.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseRepository.signUpWithEmailAndVerification(email, pass, new FirebaseRepository.CompleteCallback() {
                @Override
                public void onSuccess(String message) {
                    Toast.makeText(SplashActivity.this, message, Toast.LENGTH_SHORT).show();
                    // Transition to verification box
                    db.layoutRegisterForm.setVisibility(View.GONE);
                    db.layoutVerificationBox.setVisibility(View.VISIBLE);
                    db.textVerificationNotice.setText("A verification link has been sent to " + email + ". Click the link in your email, then tap Verified via Link below:");
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(SplashActivity.this, error, Toast.LENGTH_LONG).show();
                }
            });
        });

        db.buttonVerifyLink.setOnClickListener(v -> startVerificationPolling(db, dialog));

        db.buttonResendCode.setOnClickListener(v -> {
            FirebaseRepository.resendVerificationEmail(new FirebaseRepository.CompleteCallback() {
                @Override
                public void onSuccess(String message) {
                    Toast.makeText(SplashActivity.this, message, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(SplashActivity.this, error, Toast.LENGTH_LONG).show();
                }
            });
        });

        dialog.show();
    }

    private void startVerificationPolling(DialogRegisterBinding db, AlertDialog dialog) {
        if (isVerifying) return;
        isVerifying = true;
        db.buttonVerifyLink.setText("");
        db.buttonVerifyLink.setEnabled(false);
        db.progressVerifying.setVisibility(View.VISIBLE);
        db.buttonResendCode.setEnabled(false);

        pollVerification(db, dialog, 1);
    }

    private void pollVerification(DialogRegisterBinding db, AlertDialog dialog, int attempt) {
        final int MAX_ATTEMPTS = 8;
        final long INTERVAL_MS = 1500;

        FirebaseRepository.checkEmailVerified(new FirebaseRepository.CompleteCallback() {
            @Override
            public void onSuccess(String message) {
                isVerifying = false;
                if (isDestroyed() || !dialog.isShowing()) return;
                db.progressVerifying.setVisibility(View.GONE);
                db.buttonVerifyLink.setText("✓ Verified!");
                Toast.makeText(SplashActivity.this, message, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                openMainActivity();
            }

            @Override
            public void onError(String error) {
                if (isDestroyed() || !dialog.isShowing()) {
                    isVerifying = false;
                    return;
                }
                if (attempt < MAX_ATTEMPTS) {
                    verificationHandler.postDelayed(() -> {
                        if (isVerifying && dialog.isShowing()) {
                            pollVerification(db, dialog, attempt + 1);
                        }
                    }, INTERVAL_MS);
                } else {
                    isVerifying = false;
                    db.progressVerifying.setVisibility(View.GONE);
                    db.buttonVerifyLink.setText("Verified via Link");
                    db.buttonVerifyLink.setEnabled(true);
                    db.buttonResendCode.setEnabled(true);
                    Toast.makeText(SplashActivity.this, "Email not verified yet. Please make sure you clicked the link in your email.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }
}
