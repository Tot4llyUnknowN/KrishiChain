package com.kuet.krishichain.ui;

import android.content.Context;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.kuet.krishichain.R;
import com.kuet.krishichain.data.AppDatabase;
import com.kuet.krishichain.data.WeatherEntity;
import com.kuet.krishichain.data.WeatherRepository;
import com.kuet.krishichain.databinding.ActivityMainBinding;
import com.kuet.krishichain.worker.PriceAlertScheduler;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Weather and alert buttons of the Home screen. */
public class HomeExtras {
    private final AppCompatActivity activity;
    private final ActivityMainBinding binding;
    private boolean simulatedNow = false;

    public HomeExtras(AppCompatActivity activity, ActivityMainBinding binding) {
        this.activity = activity;
        this.binding = binding;
    }

    public void init() {
        final Context app = activity.getApplicationContext();
        PriceAlertScheduler.schedule(app);

        binding.buttonWeather.setOnClickListener(v -> {
            binding.textWeather.setText(R.string.weather_refreshing);
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
                    }
                    refreshWeatherText();
                });
            });
        });

        binding.buttonSimRain.setOnClickListener(v -> AppDatabase.IO.execute(() -> {
            if (simulatedNow) {
                WeatherRepository.clear(app);
            } else {
                WeatherRepository.simulate(app);
            }
            activity.runOnUiThread(this::refreshWeatherText);
        }));

        binding.buttonAlerts.setOnClickListener(v -> {
            PriceAlertScheduler.runNow(app);
            Toast.makeText(activity, R.string.alerts_started, Toast.LENGTH_LONG).show();
        });
    }

    /** Reads the cache and updates the weather line and the simulate button label. */
    public void refreshWeatherText() {
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
                binding.textWeather.setText(text);
                binding.buttonSimRain.setText(
                        simFinal ? R.string.button_sim_off : R.string.button_sim_on);
            });
        });
    }
}