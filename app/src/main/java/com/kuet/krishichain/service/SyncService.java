package com.kuet.krishichain.service;

import android.app.Notification;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.kuet.krishichain.R;
import com.kuet.krishichain.data.FirebaseRepository;
import com.kuet.krishichain.data.NetworkLoader;
import com.kuet.krishichain.data.WeatherRepository;
import com.kuet.krishichain.provider.PriceContentProvider;
import com.kuet.krishichain.ui.NotificationHelper;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Foreground "started" service. It refreshes the local database, fetches weather (falling back
 * to the cache when offline) and summarizes prices (read through the ContentProvider).
 */
public class SyncService extends Service {
    public static final String ACTION_SYNC_DONE = "com.kuet.krishichain.SYNC_DONE";
    public static final String EXTRA_SUMMARY = "summary";

    private static final String TAG = "KrishiChain";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean running = false;

    private boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } else {
            NetworkInfo active = cm.getActiveNetworkInfo();
            return active != null && active.isConnected();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Must call startForeground within a few seconds of startForegroundService().
        Notification notification = NotificationHelper.buildSyncingNotification(this);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NotificationHelper.NOTIF_ID_SYNC, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NotificationHelper.NOTIF_ID_SYNC, notification);
        }

        if (running) {
            return START_NOT_STICKY; // a sync is already in progress
        }
        running = true;
        worker.execute(() -> {
            String summary;
            boolean online = false;
            boolean syncSuccess = false;
            Context app = getApplicationContext();

            try {
                online = isOnline(app);
                NetworkLoader.loadIfNeeded(app);

                String weather;
                try {
                    WeatherRepository.refresh(app);
                    weather = getString(R.string.weather_ok);
                    syncSuccess = true;
                } catch (Exception e) {
                    Log.w(TAG, "Weather refresh failed: " + e.getMessage());
                    weather = getString(R.string.weather_fail);
                }

                String crowdSync = "";
                try {
                    int synced = FirebaseRepository.syncCrowdPrices(app);
                    if (synced > 0) {
                        crowdSync = " · " + synced + " crowd prices synced";
                        syncSuccess = true;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Firebase sync skipped or offline: " + e.getMessage());
                }

                if (online || syncSuccess) {
                    summary = summarizePrices() + " · " + weather + crowdSync;
                    syncSuccess = true;
                } else {
                    summary = "Offline: using cached network and local prices";
                    syncSuccess = false;
                }
            } catch (Exception e) {
                Log.e(TAG, "Sync failed", e);
                summary = "Sync failed: " + e.getMessage();
                syncSuccess = false;
            }

            try { Thread.sleep(1500); } catch (InterruptedException ignored) { }

            Intent done = new Intent(ACTION_SYNC_DONE);
            done.setPackage(getPackageName()); // only our own app receives it
            done.putExtra(EXTRA_SUMMARY, summary);
            sendBroadcast(done);

            running = false;
            stopForeground(Service.STOP_FOREGROUND_REMOVE);

            // Post completion notification to panel
            if (online && syncSuccess) {
                NotificationHelper.showSyncSuccessNotification(app, "Wholesale market prices and road network are up to date.");
            } else {
                NotificationHelper.showSyncFailedNotification(app, "No internet connection. Operating with cached offline data.");
            }

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

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // started service, not a bound one
    }

    @Override
    public void onDestroy() {
        worker.shutdown();
    }
}
