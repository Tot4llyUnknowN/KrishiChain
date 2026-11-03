package com.kuet.krishichain;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;

public class KrishiChainApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Force light mode app-wide regardless of device system settings
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }
}
