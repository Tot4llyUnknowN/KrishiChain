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

/** Fixed universal bottom navigation bar across all screens (Home, Prices, Map, More). */
public final class BottomNavHelper {
    public enum Tab {
        HOME(0), PRICES(1), MAP(2), MORE(3), NONE(-1);
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

        updateTabUI(root, currentTab);

        if (activity instanceof MainActivity) {
            MainActivity main = (MainActivity) activity;
            if (navHome != null) navHome.setOnClickListener(v -> main.selectTab(Tab.HOME));
            if (navPrices != null) navPrices.setOnClickListener(v -> main.selectTab(Tab.PRICES));
            if (navMap != null) navMap.setOnClickListener(v -> main.selectTab(Tab.MAP));
            if (navMore != null) navMore.setOnClickListener(v -> main.selectTab(Tab.MORE));
        } else {
            if (navHome != null) navHome.setOnClickListener(v -> launch(activity, Tab.HOME, currentTab));
            if (navPrices != null) navPrices.setOnClickListener(v -> launch(activity, Tab.PRICES, currentTab));
            if (navMap != null) navMap.setOnClickListener(v -> launch(activity, Tab.MAP, currentTab));
            if (navMore != null) navMore.setOnClickListener(v -> launch(activity, Tab.MORE, currentTab));
        }
    }

    public static void updateTabUI(View root, Tab currentTab) {
        if (root == null) return;

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

    private static void launch(Activity activity, Tab targetTab, Tab currentTab) {
        if (targetTab == currentTab) return;
        Intent intent = new Intent(activity, MainActivity.class);
        intent.putExtra("select_tab", targetTab.order);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);
        activity.finish();
    }
}
