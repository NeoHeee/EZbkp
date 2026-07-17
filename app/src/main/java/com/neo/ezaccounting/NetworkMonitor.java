package com.neo.ezaccounting;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

public final class NetworkMonitor {
    public interface Listener {
        void onDefaultNetworkChanged();
    }

    private final ConnectivityManager connectivityManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Listener listener;
    private boolean registered;

    private final Runnable notifyChange = () -> {
        Listener current = listener;
        if (registered && current != null) current.onDefaultNetworkChanged();
    };

    private final ConnectivityManager.NetworkCallback callback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    schedule();
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                    if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        schedule();
                    }
                }

                @Override
                public void onLost(Network network) {
                    schedule();
                }
            };

    public NetworkMonitor(Context context, Listener listener) {
        connectivityManager = (ConnectivityManager)
                context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        this.listener = listener;
    }

    public void start() {
        if (registered || connectivityManager == null) return;
        try {
            connectivityManager.registerDefaultNetworkCallback(callback);
            registered = true;
        } catch (RuntimeException ignored) {
        }
    }

    public void stop() {
        handler.removeCallbacks(notifyChange);
        if (!registered || connectivityManager == null) return;
        try {
            connectivityManager.unregisterNetworkCallback(callback);
        } catch (RuntimeException ignored) {
        }
        registered = false;
    }

    private void schedule() {
        handler.removeCallbacks(notifyChange);
        handler.postDelayed(notifyChange, 1400L);
    }
}
