package com.kuet.krishichain.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {NodeEntity.class, EdgeEntity.class, PriceEntity.class,
        WeatherEntity.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract NetworkDao networkDao();

    /** All database work runs here, never on the UI thread. */
    public static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private static volatile AppDatabase instance;

    public static AppDatabase get(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "krishichain.db")
                            .fallbackToDestructiveMigration() // development only
                            .build();
                }
            }
        }
        return instance;
    }
}