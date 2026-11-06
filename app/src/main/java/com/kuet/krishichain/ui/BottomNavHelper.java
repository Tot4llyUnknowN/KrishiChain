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
        HOME(0), MAP(1), PRICES(2), MORE(3), NONE(-1);
        public final int order;
        Tab(int order) { this.order = order; }
    }

    private static final int COLOR_ACTIVE = Color.parseColor("#1B7A2F");
    private static final int COLOR_INACTIVE = Color.parseColor("#6B7F72");

    private BottomNavHelper() {}

    public static void setup(Activity activity, Tab currentTab, View root) {
        if (root == null) return;

        View navHome = root.findViewById(R.id.navHome);
        View navPrices = root.findViewById(R.id.navPrices);
        View navMap = root.findViewById(R.id.navMap);
        View navMore = root.findViewById(R.id.navMore);

        ImageView iconHome = root.findViewById(R.id.iconHome);
        ImageView iconPrices = root.findViewById(R.id.iconPrices);
        ImageView iconMap = root.findViewById(R.id.iconMap);
        ImageView iconMore = root.findViewById(R.id.iconMore);

        TextView textHome = root.findViewById(R.id.textHome);
        TextView textPrices = root.findViewById(R.id.textPrices);
        TextView textMap = root.findViewById(R.id.textMap);
        TextView textMore = root.findViewById(R.id.textMore);

        View indHome = root.findViewById(R.id.indicatorHome);
        View indPrices = root.findViewById(R.id.indicatorPrices);
        View indMap = root.findViewById(R.id.indicatorMap);
        View indMore = root.findViewById(R.id.indicatorMore);

        setTabState(currentTab == Tab.HOME, iconHome, textHome, indHome);
        setTabState(currentTab == Tab.PRICES, iconPrices, textPrices, indPrices);
        setTabState(currentTab == Tab.MAP, iconMap, textMap, indMap);
        setTabState(currentTab == Tab.MORE, iconMore, textMore, indMore);

        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                if (currentTab != Tab.HOME) {
                    launch(activity, currentTab, Tab.HOME, MainActivity.class);
                }
            });
        }
        if (navPrices != null) {
            navPrices.setOnClickListener(v -> {
                if (currentTab != Tab.PRICES) {
                    launch(activity, currentTab, Tab.PRICES, AllPricesActivity.class);
                }
            });
        }
        if (navMap != null) {
            navMap.setOnClickListener(v -> {
                if (currentTab != Tab.MAP) {
                    launch(activity, currentTab, Tab.MAP, MarketMapActivity.class);
                }
            });
        }
        if (navMore != null) {
            navMore.setOnClickListener(v -> {
                if (currentTab != Tab.MORE) {
                    launch(activity, currentTab, Tab.MORE, BenchmarkActivity.class);
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

    private static void launch(Activity activity, Tab currentTab, Tab targetTab, Class<?> targetClass) {
        Intent intent = new Intent(activity, targetClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        activity.startActivity(intent);
        if (currentTab != Tab.NONE && targetTab != Tab.NONE) {
            if (targetTab.order > currentTab.order) {
                activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            } else {
                activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            }
        }
    }
}
