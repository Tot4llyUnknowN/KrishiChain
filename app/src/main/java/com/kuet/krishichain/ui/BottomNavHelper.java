package com.kuet.krishichain.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.kuet.krishichain.MainActivity;
import com.kuet.krishichain.R;

/** Fixed universal bottom navigation bar across all screens (Home, Map, Prices, More). */
public final class BottomNavHelper {
    public enum Tab {
        HOME, MAP, PRICES, MORE, NONE
    }

    private static final int COLOR_ACTIVE = Color.parseColor("#1B7A2F");
    private static final int COLOR_INACTIVE = Color.parseColor("#6B7F72");

    private BottomNavHelper() {}

    public static void setup(Activity activity, Tab currentTab, View root) {
        if (root == null) return;

        View navHome = root.findViewById(R.id.navHome);
        View navMap = root.findViewById(R.id.navMap);
        View navPrices = root.findViewById(R.id.navPrices);
        View navMore = root.findViewById(R.id.navMore);

        ImageView iconHome = root.findViewById(R.id.iconHome);
        ImageView iconMap = root.findViewById(R.id.iconMap);
        ImageView iconPrices = root.findViewById(R.id.iconPrices);
        ImageView iconMore = root.findViewById(R.id.iconMore);

        TextView textHome = root.findViewById(R.id.textHome);
        TextView textMap = root.findViewById(R.id.textMap);
        TextView textPrices = root.findViewById(R.id.textPrices);
        TextView textMore = root.findViewById(R.id.textMore);

        View indHome = root.findViewById(R.id.indicatorHome);
        View indMap = root.findViewById(R.id.indicatorMap);
        View indPrices = root.findViewById(R.id.indicatorPrices);
        View indMore = root.findViewById(R.id.indicatorMore);

        setTabState(currentTab == Tab.HOME, iconHome, textHome, indHome);
        setTabState(currentTab == Tab.MAP, iconMap, textMap, indMap);
        setTabState(currentTab == Tab.PRICES, iconPrices, textPrices, indPrices);
        setTabState(currentTab == Tab.MORE, iconMore, textMore, indMore);

        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                if (currentTab != Tab.HOME) {
                    launch(activity, MainActivity.class);
                }
            });
        }
        if (navMap != null) {
            navMap.setOnClickListener(v -> {
                if (currentTab != Tab.MAP) {
                    launch(activity, MarketMapActivity.class);
                }
            });
        }
        if (navPrices != null) {
            navPrices.setOnClickListener(v -> {
                if (currentTab != Tab.PRICES) {
                    launch(activity, ReportsActivity.class);
                }
            });
        }
        if (navMore != null) {
            navMore.setOnClickListener(v -> {
                if (currentTab != Tab.MORE) {
                    launch(activity, BenchmarkActivity.class);
                }
            });
        }
    }

    private static void setTabState(boolean active, ImageView icon, TextView text, View indicator) {
        int color = active ? COLOR_ACTIVE : COLOR_INACTIVE;
        if (icon != null) {
            icon.setImageTintList(ColorStateList.valueOf(color));
        }
        if (text != null) {
            text.setTextColor(color);
        }
        if (indicator != null) {
            indicator.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private static void launch(Activity activity, Class<?> targetClass) {
        Intent intent = new Intent(activity, targetClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);
    }
}
