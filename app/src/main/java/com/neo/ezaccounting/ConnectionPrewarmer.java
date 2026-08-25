package com.neo.ezaccounting;

import android.os.SystemClock;
import android.util.Log;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class ConnectionPrewarmer {
    private static final String TAG = "LedgerlyStartup";
    private static final int CONNECT_TIMEOUT_MS = 1200;
    private static final int READ_TIMEOUT_MS = 800;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> activeTask;
    private String warmedKey;
    private long warmedAt;

    public synchronized void prewarm(String address) {
        prewarm(address, "default");
    }

    public synchronized void prewarm(String address, String networkKey) {
        String target = origin(address);
        if (target == null) return;
        String key = (networkKey == null ? "default" : networkKey) + "|" + target;
        long now = System.currentTimeMillis();
        if (key.equals(warmedKey) && now - warmedAt < 60_000L) return;
        if (activeTask != null) activeTask.cancel(true);
        warmedKey = key;
        warmedAt = now;
        activeTask = executor.submit(() -> connect(target));
    }

    private void connect(String target) {
        long startedAt = SystemClock.elapsedRealtime();
        HttpURLConnection connection = null;
        boolean success = false;
        long dnsMs = -1L;
        long connectMs = -1L;
        long httpMs = -1L;
        try {
            URL url = new URL(target);
            long phase = SystemClock.elapsedRealtime();
            InetAddress.getAllByName(url.getHost());
            dnsMs = SystemClock.elapsedRealtime() - phase;
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(true);
            connection.setRequestProperty("Connection", "keep-alive");
            connection.setRequestProperty("User-Agent", "Ledgerly-Preconnect/" +
                    BuildConfig.VERSION_NAME);
            phase = SystemClock.elapsedRealtime();
            connection.connect();
            connectMs = SystemClock.elapsedRealtime() - phase;
            phase = SystemClock.elapsedRealtime();
            int status = connection.getResponseCode();
            httpMs = SystemClock.elapsedRealtime() - phase;
            success = status > 0;
        } catch (Exception ignored) {
        } finally {
            if (connection != null) connection.disconnect();
            Log.i(TAG, "preconnect_ms=" +
                    (SystemClock.elapsedRealtime() - startedAt) +
                    " dns_ms=" + dnsMs +
                    " tls_connect_ms=" + connectMs +
                    " http_ms=" + httpMs + " success=" + success);
        }
    }

    public synchronized void cancel() {
        if (activeTask != null) activeTask.cancel(true);
        activeTask = null;
    }

    static String origin(String address) {
        if (address == null || address.trim().isEmpty()) return null;
        try {
            URI uri = new URI(address.trim());
            String scheme = uri.getScheme();
            String authority = uri.getRawAuthority();
            if (authority == null ||
                    !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                return null;
            }
            return new URI(scheme.toLowerCase(), authority, "/", null, null).toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    public synchronized void shutdown() {
        cancel();
        executor.shutdownNow();
    }
}
