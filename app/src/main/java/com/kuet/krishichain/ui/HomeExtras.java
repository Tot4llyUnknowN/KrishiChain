package com.kuet.krishichain.ui;

import android.content.Context;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.kuet.krishichain.R;
import com.kuet.krishichain.data.AppDatabase;
import com.kuet.krishichain.data.WeatherEntity;
import com.kuet.krishichain.data.WeatherRepository;
import com.kuet.krishichain.worker.PriceAlertScheduler;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Weather and alert utilities of the Home screen. */
public class HomeExtras {
    private final AppCompatActivity activity;
    private boolean simulatedNow = false;
    private String lastWeatherText = "";

    public HomeExtras(AppCompatActivity activity) {
        this.activity = activity;
    }

    public void init() {
        final Context app = activity.getApplicationContext();
        PriceAlertScheduler.schedule(app);
        refreshWeatherText();
        refreshWeatherSilently();
    }

    /** Silent automatic background refresh for live weather */
    public void refreshWeatherSilently() {
        final Context app = activity.getApplicationContext();
        AppDatabase.IO.execute(() -> {
            try {
                WeatherRepository.refresh(app);
            } catch (Exception ignored) {
            }
            activity.runOnUiThread(this::refreshWeatherText);
        });
    }

    public void refreshWeather(Runnable onComplete) {
        final Context app = activity.getApplicationContext();
        AppDatabase.IO.execute(() -> {
            boolean ok = true;
            try {
                WeatherRepository.refresh(app);
            } catch (Exception e) {
                ok = false;
            }
            final boolean success = ok;
            activity.runOnUiThread(() -> {
                if (!success) {
                    Toast.makeText(activity, R.string.weather_fail, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(activity, R.string.weather_ok, Toast.LENGTH_SHORT).show();
                }
                refreshWeatherText();
                if (onComplete != null) {
                    onComplete.run();
                }
            });
        });
    }

    public void toggleSimulation(Runnable onComplete) {
        final Context app = activity.getApplicationContext();
        AppDatabase.IO.execute(() -> {
            List<WeatherEntity> current = AppDatabase.get(app).networkDao().getWeather();
            boolean currentlySimulated = false;
            for (WeatherEntity w : current) {
                if (w.simulated) {
                    currentlySimulated = true;
                    break;
                }
            }
            final boolean switchToMonsoon = !currentlySimulated;
            boolean ok = true;
            if (switchToMonsoon) {
                WeatherRepository.simulate(app);
            } else {
                try {
                    int count = WeatherRepository.refresh(app);
                    android.util.Log.d("WeatherExtras", "Refreshed weather count: " + count);
                } catch (Exception e) {
                    android.util.Log.e("WeatherExtras", "Weather refresh failed: " + e.getMessage(), e);
                    ok = false;
                    WeatherRepository.clear(app);
                }
            }
            final boolean fetchOk = ok;
            activity.runOnUiThread(() -> {
                if (switchToMonsoon) {
                    Toast.makeText(activity, "⛈ Monsoon simulated: Highway delays & costs active", Toast.LENGTH_SHORT).show();
                } else {
                    if (fetchOk) {
                        Toast.makeText(activity, "🌤 Current live weather fetched from satellite", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(activity, "🌤 Weather reset to normal (offline)", Toast.LENGTH_SHORT).show();
                    }
                }
                refreshWeatherText(onComplete);
            });
        });
    }

    public void triggerAlertsNow() {
        final Context app = activity.getApplicationContext();
        PriceAlertScheduler.runNow(app);
        Toast.makeText(activity, R.string.alerts_started, Toast.LENGTH_LONG).show();
    }

    public boolean isSimulatedNow() {
        return simulatedNow;
    }

    public String getLastWeatherText() {
        return lastWeatherText;
    }

    /** Reads the cache and updates the weather status. */
    public void refreshWeatherText() {
        refreshWeatherText(null);
    }

    public void refreshWeatherText(Runnable callback) {
        final Context app = activity.getApplicationContext();
        AppDatabase.IO.execute(() -> {
            List<WeatherEntity> rows = AppDatabase.get(app).networkDao().getWeather();
            double max = 0;
            boolean sim = false;
            long when = 0;
            for (WeatherEntity w : rows) {
                max = Math.max(max, w.rainMm);
                sim = sim || w.simulated;
                when = Math.max(when, w.fetchedAt);
            }
            final String text;
            if (rows.isEmpty()) {
                text = activity.getString(R.string.weather_none);
            } else if (sim) {
                text = activity.getString(R.string.weather_sim, max);
            } else {
                text = activity.getString(R.string.weather_real,
                        new SimpleDateFormat("HH:mm dd MMM", Locale.US).format(new Date(when)), max);
            }
            final boolean simFinal = sim;
            activity.runOnUiThread(() -> {
                if (activity.isDestroyed()) {
                    return;
                }
                simulatedNow = simFinal;
                lastWeatherText = text;
                if (callback != null) {
                    callback.run();
                }
            });
        });
    }
}
