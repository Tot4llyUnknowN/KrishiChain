package com.kuet.krishichain.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.kuet.krishichain.worker.PriceAlertScheduler;

/**
 * Runs after the phone restarts. It records the time and re-schedules the WorkManager
 * price-alert job. It does NOT start SyncService: recent Android versions restrict
 * starting data-sync foreground services from a boot receiver.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        context.getSharedPreferences("krishichain_prefs", Context.MODE_PRIVATE)
                .edit().putLong("last_boot_millis", System.currentTimeMillis()).apply();
        PriceAlertScheduler.schedule(context);
        Log.d("KrishiChain", "BootReceiver: device booted, alerts re-scheduled");
    }
}