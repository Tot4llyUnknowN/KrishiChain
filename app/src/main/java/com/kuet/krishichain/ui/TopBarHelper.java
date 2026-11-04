package com.kuet.krishichain.ui;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

import com.kuet.krishichain.R;
import com.kuet.krishichain.receiver.ConnectivityReceiver;

/**
 * Universal top status bar helper (Live Online / Offline Indicator).
 */
public final class TopBarHelper {

    private TopBarHelper() {}

    public static void setup(Activity activity, View root) {
        if (root == null || activity == null) return;
        TextView textOnline = root.findViewById(R.id.textTopOnline);
        if (textOnline != null) {
            boolean online = ConnectivityReceiver.isOnline(activity);
            updateStatus(textOnline, online);
        }
    }

    public static void updateStatus(TextView textOnline, boolean online) {
        if (textOnline == null) return;
        if (online) {
            textOnline.setText(R.string.net_online);
            textOnline.setTextColor(Color.parseColor("#1B7A2F"));
        } else {
            textOnline.setText(R.string.net_offline);
            textOnline.setTextColor(Color.parseColor("#C62828"));
        }
    }
}
