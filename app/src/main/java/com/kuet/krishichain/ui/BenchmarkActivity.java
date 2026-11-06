package com.kuet.krishichain.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.kuet.krishichain.MainActivity;

/**
 * Settings & Tools entry activity.
 * Seamlessly transitions to the unified fixed-bar More tab in MainActivity.
 */
public class BenchmarkActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("select_tab", 3);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }
}
