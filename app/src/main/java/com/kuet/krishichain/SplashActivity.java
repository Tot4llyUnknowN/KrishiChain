package com.kuet.krishichain;

import android.content.Intent;
import android.os.Bundle;
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
import com.kuet.krishichain.databinding.DialogAuthBinding;

public class SplashActivity extends AppCompatActivity {

    private ActivitySplashBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.buttonLogin.setOnClickListener(v -> showAuthDialog(false));
        binding.buttonRegister.setOnClickListener(v -> showAuthDialog(true));
        binding.textGuest.setOnClickListener(v -> openMainActivity());
    }

    private void openMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void showAuthDialog(boolean isRegisterMode) {
        DialogAuthBinding db = DialogAuthBinding.inflate(LayoutInflater.from(this));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(db.getRoot())
                .setCancelable(true)
                .create();

        FirebaseUser current = FirebaseRepository.getCurrentUser();
        if (current != null) {
            db.textCurrentStatus.setText("Signed in as: " + FirebaseRepository.getUserDisplayName());
            db.buttonSignOut.setVisibility(View.VISIBLE);
        } else {
            db.textCurrentStatus.setText("Currently: Not signed in");
            db.buttonSignOut.setVisibility(View.GONE);
        }

        if (isRegisterMode) {
            db.buttonSignIn.setVisibility(View.GONE);
            db.buttonRegister.setVisibility(View.VISIBLE);
        } else {
            db.buttonSignIn.setVisibility(View.VISIBLE);
            db.buttonRegister.setVisibility(View.GONE);
        }

        db.buttonSignIn.setOnClickListener(v -> {
            String email = db.editAuthEmail.getText() != null ? db.editAuthEmail.getText().toString().trim() : "";
            String pass = db.editAuthPassword.getText() != null ? db.editAuthPassword.getText().toString().trim() : "";
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

        db.buttonRegister.setOnClickListener(v -> {
            String email = db.editAuthEmail.getText() != null ? db.editAuthEmail.getText().toString().trim() : "";
            String pass = db.editAuthPassword.getText() != null ? db.editAuthPassword.getText().toString().trim() : "";
            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(pass)) {
                Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }
            FirebaseRepository.signUpWithEmail(email, pass, new FirebaseRepository.CompleteCallback() {
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

        db.buttonGuest.setOnClickListener(v -> {
            FirebaseRepository.signInAsGuest(new FirebaseRepository.CompleteCallback() {
                @Override
                public void onSuccess(String message) {
                    dialog.dismiss();
                    openMainActivity();
                }

                @Override
                public void onError(String error) {
                    dialog.dismiss();
                    openMainActivity();
                }
            });
        });

        db.buttonSignOut.setOnClickListener(v -> {
            FirebaseRepository.signOut();
            db.textCurrentStatus.setText("Currently: Not signed in");
            db.buttonSignOut.setVisibility(View.GONE);
            Toast.makeText(SplashActivity.this, "Signed out", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }
}
