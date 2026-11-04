package com.kuet.krishichain.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Shared state for farmer selections (Origin Location, Selected Crop, Quantity).
 * Ensures synchronization between Home tab, Map tab, and other screens.
 */
public final class UserPreferences {
    private static final String PREF_NAME = "krishichain_user_prefs";
    private static final String KEY_ORIGIN_ID = "selected_origin_id";
    private static final String KEY_CROP_KEY = "selected_crop_key";
    private static final String KEY_QUANTITY_KG = "selected_quantity_kg";
    private static final String KEY_PROD_COST = "selected_production_cost_per_kg";

    private UserPreferences() {}

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static int getOriginId(Context context, int defaultId) {
        return getPrefs(context).getInt(KEY_ORIGIN_ID, defaultId);
    }

    public static void setOriginId(Context context, int originId) {
        getPrefs(context).edit().putInt(KEY_ORIGIN_ID, originId).apply();
    }

    public static String getCropKey(Context context, String defaultKey) {
        return getPrefs(context).getString(KEY_CROP_KEY, defaultKey);
    }

    public static void setCropKey(Context context, String cropKey) {
        getPrefs(context).edit().putString(KEY_CROP_KEY, cropKey).apply();
    }

    public static double getQuantity(Context context, double defaultQty) {
        return (double) getPrefs(context).getFloat(KEY_QUANTITY_KG, (float) defaultQty);
    }

    public static void setQuantity(Context context, double qty) {
        getPrefs(context).edit().putFloat(KEY_QUANTITY_KG, (float) qty).apply();
    }

    public static double getProductionCost(Context context, double defaultCost) {
        return (double) getPrefs(context).getFloat(KEY_PROD_COST, (float) defaultCost);
    }

    public static void setProductionCost(Context context, double cost) {
        getPrefs(context).edit().putFloat(KEY_PROD_COST, (float) cost).apply();
    }
}
