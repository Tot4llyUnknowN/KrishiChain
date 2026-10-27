package com.kuet.krishichain.worker;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.kuet.krishichain.R;
import com.kuet.krishichain.data.NetworkLoader;
import com.kuet.krishichain.provider.PriceContentProvider;

import java.util.Map;
import java.util.TreeMap;

/**
 * Background job. It reads prices through the ContentProvider, compares each crop's average with
 * the average saved at the previous check, and notifies on a change of THRESHOLD or more.
 * The first run only saves a baseline.
 */
public class PriceAlertWorker extends Worker {
    private static final String CHANNEL_ID = "alerts";
    private static final String PREFS = "krishichain_prefs";
    private static final double THRESHOLD = 0.05; // 5%

    public PriceAlertWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @SuppressLint("MissingPermission")
    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        try {
            NetworkLoader.loadIfNeeded(ctx);
        } catch (Exception e) {
            return Result.retry();
        }

        Map<String, double[]> sums = new TreeMap<>(); // crop -> {sum, count}
        try (Cursor c = ctx.getContentResolver().query(PriceContentProvider.CONTENT_URI,
                new String[]{"crop", "price"}, null, null, null)) {
            while (c != null && c.moveToNext()) {
                double[] s = sums.get(c.getString(0));
                if (s == null) {
                    s = new double[2];
                    sums.put(c.getString(0), s);
                }
                s[0] += c.getDouble(1);
                s[1] += 1;
            }
        }

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = prefs.edit();
        int id = 100;
        for (Map.Entry<String, double[]> e : sums.entrySet()) {
            String crop = e.getKey();
            double avg = e.getValue()[0] / e.getValue()[1];
            String key = "alert_baseline_" + crop;
            if (prefs.contains(key)) {
                double old = prefs.getFloat(key, 0f);
                if (old > 0 && Math.abs(avg - old) / old >= THRESHOLD) {
                    notifyChange(ctx, id, crop, old, avg);
                }
            }
            edit.putFloat(key, (float) avg);
            id++;
        }
        edit.apply();
        return Result.success();
    }

    @SuppressLint("MissingPermission")
    private void notifyChange(Context ctx, int id, String crop, double old, double now) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm =
                    (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                    ctx.getString(R.string.alert_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT));
        }
        double pct = (now - old) / old * 100.0;
        String text = ctx.getString(R.string.alert_text,
                crop, ctx.getString(pct > 0 ? R.string.alert_up : R.string.alert_down),
                Math.abs(pct), old, now);
        NotificationManagerCompat.from(ctx).notify(id,
                new NotificationCompat.Builder(ctx, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.stat_sys_warning)
                        .setContentTitle(ctx.getString(R.string.alert_title))
                        .setContentText(text)
                        .setAutoCancel(true)
                        .build());
    }
}