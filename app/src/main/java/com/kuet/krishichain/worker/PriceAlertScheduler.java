package com.kuet.krishichain.worker;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class PriceAlertScheduler {
    private static final String UNIQUE_NAME = "price_alerts";

    private PriceAlertScheduler() { }

    /** Safe to call many times: KEEP means an existing schedule is not replaced. */
    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                PriceAlertWorker.class, 6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    /** Runs the check once, right now (used by the Home button for the demo). */
    public static void runNow(Context context) {
        WorkManager.getInstance(context).enqueue(
                new OneTimeWorkRequest.Builder(PriceAlertWorker.class).build());
    }
}