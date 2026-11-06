package com.kuet.krishichain.ui;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.kuet.krishichain.MainActivity;
import com.kuet.krishichain.R;

public final class NotificationHelper {
    public static final String CHANNEL_SYNC = "sync_channel";
    public static final String CHANNEL_WEATHER = "weather_channel";

    public static final int NOTIF_ID_SYNC = 1001;
    public static final int NOTIF_ID_SYNC_RESULT = 1002;
    public static final int NOTIF_ID_MONSOON = 1003;

    private NotificationHelper() {}

    public static void initChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            NotificationChannel syncChannel = new NotificationChannel(
                    CHANNEL_SYNC,
                    "KrishiChain Data Sync",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            syncChannel.setDescription("Notifications about background and startup data synchronization");
            nm.createNotificationChannel(syncChannel);

            NotificationChannel weatherChannel = new NotificationChannel(
                    CHANNEL_WEATHER,
                    "Weather & Monsoon Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            weatherChannel.setDescription("Alerts for monsoon rainfall and highway speed reductions");
            nm.createNotificationChannel(weatherChannel);
        }
    }

    public static Notification buildSyncingNotification(Context context) {
        initChannels(context);
        Intent openApp = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(context, CHANNEL_SYNC)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("🔄 Syncing KrishiChain...")
                .setContentText("Updating wholesale market prices and weather...")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pi)
                .build();
    }

    public static void showSyncSuccessNotification(Context context, String summary) {
        initChannels(context);
        Intent openApp = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String text = (summary != null && !summary.isEmpty())
                ? summary
                : "Wholesale market prices and road network are up to date.";

        Notification notif = new NotificationCompat.Builder(context, CHANNEL_SYNC)
                .setSmallIcon(R.drawable.ic_leaf_logo)
                .setContentTitle("✅ Sync Successful")
                .setContentText(text)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pi)
                .build();

        NotificationManagerCompat.from(context).notify(NOTIF_ID_SYNC_RESULT, notif);
    }

    public static void showSyncFailedNotification(Context context, String reason) {
        initChannels(context);
        Intent openApp = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String text = (reason != null && !reason.isEmpty())
                ? reason
                : "Unable to reach server. Operating with cached offline data.";

        Notification notif = new NotificationCompat.Builder(context, CHANNEL_SYNC)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("⚠️ Sync Failed")
                .setContentText(text)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pi)
                .build();

        NotificationManagerCompat.from(context).notify(NOTIF_ID_SYNC_RESULT, notif);
    }

    public static void showMonsoonNotification(Context context) {
        initChannels(context);
        Intent openApp = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notif = new NotificationCompat.Builder(context, CHANNEL_WEATHER)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("🌧️ Monsoon Weather Alert")
                .setContentText("Heavy rain active (+20mm). Transport times +30% & profits will drop a bit.")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("Heavy monsoon rainfall active across highway corridors. Vehicle speeds reduced by 30%, increasing transit duration and perishability decay. Expect net profits to drop a bit on long-distance routes."))
                .setOngoing(true) // Stays in notification panel while monsoon is active
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi)
                .build();

        NotificationManagerCompat.from(context).notify(NOTIF_ID_MONSOON, notif);
    }

    public static void cancelMonsoonNotification(Context context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_MONSOON);
    }
}
