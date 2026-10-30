package com.kuet.krishichain.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.kuet.krishichain.R;
import com.kuet.krishichain.data.NetworkLoader;
import com.kuet.krishichain.data.WeatherRepository;
import com.kuet.krishichain.provider.PriceContentProvider;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Foreground "started" service. It refreshes the local database, fetches weather (falling back
 * to the cache when offline) and summarizes prices (read through the ContentProvider).
 * Part G2 adds Firebase price reports here.
 */
public class SyncService extends Service {
    public static final String ACTION_SYNC_DONE = "com.kuet.krishichain.SYNC_DONE";
    public static final String EXTRA_SUMMARY = "summary";

    private static final String TAG = "KrishiChain";
    private static final String CHANNEL_ID = "sync";
    private static final int NOTIFICATION_ID = 1;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean running = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Must call startForeground within a few seconds of startForegroundService().
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        if (running) {
            return START_NOT_STICKY; // a sync is already in progress
        }
        running = true;
        worker.execute(() -> {
            String summary;
            try {
                Context app = getApplicationContext();
                NetworkLoader.loadIfNeeded(app);
                String weather;
                try {
                    WeatherRepository.refresh(app);
                    weather = getString(R.string.weather_ok);
                } catch (Exception e) {
                    Log.w(TAG, "Weather refresh failed: " + e.getMessage());
                    weather = getString(R.string.weather_fail);
                }
                String crowdSync = "";
                try {
                    int synced = com.kuet.krishichain.data.FirebaseRepository.syncCrowdPrices(app);
                    if (synced > 0) {
                        crowdSync = " · " + synced + " crowd prices synced";
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Firebase sync skipped or offline: " + e.getMessage());
                }
                summary = summarizePrices() + " · " + weather + crowdSync;
            } catch (Exception e) {
                Log.e(TAG, "Sync failed", e);
                summary = "Sync failed: " + e.getMessage();
            }
            try { Thread.sleep(5000); } catch (InterruptedException ignored) { }
            Intent done = new Intent(ACTION_SYNC_DONE);
            done.setPackage(getPackageName()); // only our own app receives it
            done.putExtra(EXTRA_SUMMARY, summary);
            sendBroadcast(done);

            running = false;
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
            stopSelf();
        });
        return START_NOT_STICKY;
    }

    /** Reads prices through the ContentProvider (component-to-component communication). */
    private String summarizePrices() {
        ContentResolver resolver = getContentResolver();
        Map<String, double[]> sums = new TreeMap<>(); // crop -> {sum, count}
        int rows = 0;
        try (Cursor c = resolver.query(PriceContentProvider.CONTENT_URI,
                new String[]{"crop", "price"}, null, null, null)) {
            while (c != null && c.moveToNext()) {
                double[] s = sums.get(c.getString(0));
                if (s == null) {
                    s = new double[2];
                    sums.put(c.getString(0), s);
                }
                s[0] += c.getDouble(1);
                s[1] += 1;
                rows++;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(rows).append(" prices");
        for (Map.Entry<String, double[]> e : sums.entrySet()) {
            sb.append(String.format(Locale.US, " · %s avg ৳%.1f/kg",
                    e.getKey(), e.getValue()[0] / e.getValue()[1]));
        }
        return sb.toString();
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                    getString(R.string.sync_channel_name), NotificationManager.IMPORTANCE_LOW));
        }
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(getString(R.string.sync_notif_title))
                .setContentText(getString(R.string.sync_notif_text))
                .setOngoing(true)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // started service, not a bound one
    }

    @Override
    public void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}