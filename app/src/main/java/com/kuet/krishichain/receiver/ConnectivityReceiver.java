package com.kuet.krishichain.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/** Tells the screen when the device goes online or offline. */
public class ConnectivityReceiver extends BroadcastReceiver {

    public interface Listener {
        /** regained = true only for an offline to online transition (not the first report). */
        void onConnectivityChanged(boolean online, boolean regained);
    }

    private final Listener listener;
    private Boolean last = null;

    public ConnectivityReceiver(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean online = isOnline(context);
        boolean regained = last != null && !last && online;
        if (last == null || last != online) {
            last = online;
            listener.onConnectivityChanged(online, regained);
        }
    }

    /** "Connected" does not guarantee that the internet works; real calls still need error handling. */
    public static boolean isOnline(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = cm.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}