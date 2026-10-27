package com.kuet.krishichain.provider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteQueryBuilder;

import com.kuet.krishichain.data.AppDatabase;
import com.kuet.krishichain.data.NetworkLoader;

import java.io.IOException;

/**
 * Read-only access to the price table for other components (and for adb).
 *   content://com.kuet.krishichain.provider/prices        all rows
 *   content://com.kuet.krishichain.provider/prices/5      the row with pk = 5
 * Columns: pk, nodeId, crop, price, source, date.
 */
public class PriceContentProvider extends ContentProvider {
    public static final String AUTHORITY = "com.kuet.krishichain.provider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/prices");

    private static final int PRICES = 1;
    private static final int PRICE_ID = 2;
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        MATCHER.addURI(AUTHORITY, "prices", PRICES);
        MATCHER.addURI(AUTHORITY, "prices/#", PRICE_ID);
    }

    @Override
    public boolean onCreate() {
        return true; // keep this fast: the database opens lazily in query()
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection,
                        @Nullable String selection, @Nullable String[] selectionArgs,
                        @Nullable String sortOrder) {
        Context ctx = getContext();
        int match = MATCHER.match(uri);
        if (ctx == null || match == UriMatcher.NO_MATCH) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
        try {
            NetworkLoader.loadIfNeeded(ctx); // makes adb work even before the app was opened
        } catch (IOException e) {
            return null;
        }

        String where = selection;
        if (match == PRICE_ID) {
            where = "pk = " + ContentUris.parseId(uri)
                    + (selection == null ? "" : " AND (" + selection + ")");
        }
        SupportSQLiteQuery query = SupportSQLiteQueryBuilder.builder("prices")
                .columns(projection)
                .selection(where, selectionArgs)
                .orderBy(sortOrder)
                .create();
        Cursor cursor = AppDatabase.get(ctx).getOpenHelper().getReadableDatabase().query(query);
        cursor.setNotificationUri(ctx.getContentResolver(), uri);
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        switch (MATCHER.match(uri)) {
            case PRICES:
                return "vnd.android.cursor.dir/vnd." + AUTHORITY + ".price";
            case PRICE_ID:
                return "vnd.android.cursor.item/vnd." + AUTHORITY + ".price";
            default:
                return null;
        }
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException("Prices are read-only here");
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("Prices are read-only here");
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values,
                      @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("Prices are read-only here");
    }
}