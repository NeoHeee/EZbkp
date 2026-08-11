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
    private WifiRouteContext() {}

    public static boolean isWifiConnected(Context context) {
        ConnectivityManager manager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = manager == null ? null : manager.getActiveNetwork();
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
        if (!isWifiConnected(context) || !canReadSsid(context)) return null;
        WifiManager manager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = manager == null ? null : manager.getConnectionInfo();
        String ssid = info == null ? null : info.getSSID();
        if (ssid == null || WifiManager.UNKNOWN_SSID.equals(ssid)) return null;
        if (ssid.length() >= 2 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length() - 1);
        }
        return ssid.trim().isEmpty() ? null : ssid.trim();
    }
}
