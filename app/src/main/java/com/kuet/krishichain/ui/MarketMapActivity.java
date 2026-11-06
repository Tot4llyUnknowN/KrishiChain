package com.kuet.krishichain.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.kuet.krishichain.MainActivity;

/**
 * 64 Market Hubs & Highway Map entry activity.
 * Seamlessly transitions to the unified fixed-bar Map tab in MainActivity.
 */
public class MarketMapActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("select_tab", 2);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }
}
