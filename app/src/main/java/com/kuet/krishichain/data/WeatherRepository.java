package com.kuet.krishichain.data;

import android.content.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Fetches rain for all places (one request), caches it in Room, or writes simulated rain. */
public final class WeatherRepository {
    /** DEMO: places west of this longitude get simulated rain (the south-west region). */
    private static final double SIM_LON_LIMIT = 90.0;
    private static final double SIM_RAIN_MM = 20.0;

    private WeatherRepository() { }

    private static WeatherApi api() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
        return new Retrofit.Builder()
                .baseUrl("https://api.open-meteo.com/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(WeatherApi.class);
    }

    /** Call on a background thread. Throws IOException when offline; the cache is left untouched. */
    public static int refresh(Context context) throws IOException {
        NetworkDao dao = AppDatabase.get(context).networkDao();
        List<NodeEntity> nodes = dao.getAllNodes();
        StringBuilder lat = new StringBuilder();
        StringBuilder lon = new StringBuilder();
        for (NodeEntity n : nodes) {
            if (lat.length() > 0) {
                lat.append(',');
                lon.append(',');
            }
            lat.append(String.format(Locale.US, "%.4f", n.lat));
            lon.append(String.format(Locale.US, "%.4f", n.lon));
        }
        Response<List<WeatherDto>> r =
                api().forecast(lat.toString(), lon.toString(), "precipitation", "auto").execute();
        List<WeatherDto> body = r.body();
        if (!r.isSuccessful() || body == null || body.size() != nodes.size()) {
            throw new IOException("Weather service answered with code " + r.code());
        }
        long now = System.currentTimeMillis();
        List<WeatherEntity> rows = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            WeatherEntity w = new WeatherEntity();
            w.nodeId = nodes.get(i).id;
            Double mm = body.get(i).current == null ? null : body.get(i).current.precipitation;
            w.rainMm = mm == null ? 0 : mm;
            w.fetchedAt = now;
            w.simulated = false;
            rows.add(w);
        }
        dao.insertWeather(rows);
        return rows.size();
    }

    /** DEMO ONLY: heavy rain in the south-west, clearly flagged as simulated. */
    public static void simulate(Context context) {
        NetworkDao dao = AppDatabase.get(context).networkDao();
        long now = System.currentTimeMillis();
        List<WeatherEntity> rows = new ArrayList<>();
        for (NodeEntity n : dao.getAllNodes()) {
            WeatherEntity w = new WeatherEntity();
            w.nodeId = n.id;
            w.rainMm = n.lon < SIM_LON_LIMIT ? SIM_RAIN_MM : 0;
            w.fetchedAt = now;
            w.simulated = true;
            rows.add(w);
        }
        dao.insertWeather(rows);
    }

    public static void clear(Context context) {
        AppDatabase.get(context).networkDao().clearWeather();
    }
}