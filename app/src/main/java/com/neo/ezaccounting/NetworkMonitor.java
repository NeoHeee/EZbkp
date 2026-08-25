package com.neo.ezaccounting;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

public final class NetworkMonitor {
    public interface Listener {
        void onNetworkChanging();
        void onDefaultNetworkChanged(NetworkState state);
    }

    private final ConnectivityManager connectivityManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Listener listener;
    private boolean registered;
    private NetworkState pendingState = NetworkState.none();
    private NetworkState deliveredState = NetworkState.none();

    private final Runnable notifyChange = () -> {
        Listener current = listener;
        if (!registered || current == null) return;
        NetworkState latest = currentState();
        pendingState = latest;
        deliveredState = latest;
        current.onDefaultNetworkChanged(latest);
    };

    private final ConnectivityManager.NetworkCallback callback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    schedule(currentState());
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                    if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        schedule(NetworkState.from(network, capabilities));
                    }
                }

                @Override
                public void onLost(Network network) {
                    WifiRouteContext.onNetworkLost(network);
                    schedule(NetworkState.none());
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

    private void schedule(NetworkState candidate) {
        NetworkState next = candidate == null ? NetworkState.none() : candidate;
        if (next.equals(pendingState) && next.equals(deliveredState)) return;
        pendingState = next;
        handler.post(() -> {
            Listener current = listener;
            if (registered && current != null) current.onNetworkChanging();
        });
        handler.removeCallbacks(notifyChange);
        handler.postDelayed(notifyChange, 350L);
    }

    NetworkState currentState() {
        if (connectivityManager == null) return NetworkState.none();
        try {
            Network active = connectivityManager.getActiveNetwork();
            return NetworkState.from(active,
                    active == null ? null : connectivityManager.getNetworkCapabilities(active));
        } catch (RuntimeException ignored) {
            return NetworkState.none();
        }
    }
}
