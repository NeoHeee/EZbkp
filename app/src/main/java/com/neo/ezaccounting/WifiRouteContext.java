package com.neo.ezaccounting;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

public final class WifiRouteContext {
    private static long cachedNetworkHandle = -1L;
    private static String cachedSsid;

    private WifiRouteContext() {}

    public static boolean isWifiConnected(Context context) {
        return currentWifiNetwork(context) != null;
    }

    public static Network currentWifiNetwork(Context context) {
        ConnectivityManager manager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return null;
        Network active = manager.getActiveNetwork();
        if (isWifi(manager, active)) return active;
        for (Network network : manager.getAllNetworks()) {
            if (isWifi(manager, network)) return network;
        }
        return null;
    }

    private static boolean isWifi(ConnectivityManager manager, Network network) {
        NetworkCapabilities capabilities = network == null ? null :
                manager.getNetworkCapabilities(network);
        return capabilities != null &&
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    public static boolean canReadSsid(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED;
    }

    @SuppressWarnings("deprecation")
    public static String currentSsid(Context context) {
        Network network = currentWifiNetwork(context);
        if (network == null || !canReadSsid(context)) return null;
        long networkHandle = network.getNetworkHandle();
        WifiManager manager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = manager == null ? null : manager.getConnectionInfo();
        String ssid = info == null ? null : info.getSSID();
        if (ssid == null || WifiManager.UNKNOWN_SSID.equals(ssid)) {
            synchronized (WifiRouteContext.class) {
                return cachedNetworkHandle == networkHandle ? cachedSsid : null;
            }
        }
        if (ssid.length() >= 2 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length() - 1);
        }
        ssid = ssid.trim();
        if (ssid.isEmpty()) return null;
        synchronized (WifiRouteContext.class) {
            cachedNetworkHandle = networkHandle;
            cachedSsid = ssid;
        }
        return ssid;
    }

    public static synchronized void onNetworkLost(Network network) {
        if (network != null && network.getNetworkHandle() == cachedNetworkHandle) {
            cachedNetworkHandle = -1L;
            cachedSsid = null;
        }
    }
}
