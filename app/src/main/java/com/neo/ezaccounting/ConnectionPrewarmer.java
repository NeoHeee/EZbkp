package com.neo.ezaccounting;

import android.os.SystemClock;
import android.util.Log;

import java.net.HttpURLConnection;
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

    public synchronized void prewarm(String address) {
        if (activeTask != null) activeTask.cancel(true);
        String target = origin(address);
        if (target == null) return;
        activeTask = executor.submit(() -> connect(target));
    }

    private void connect(String target) {
        long startedAt = SystemClock.elapsedRealtime();
        HttpURLConnection connection = null;
        boolean success = false;
        try {
            connection = (HttpURLConnection) new URL(target).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(true);
            connection.setRequestProperty("Connection", "keep-alive");
            connection.setRequestProperty("User-Agent", "Ledgerly-Preconnect/" +
                    BuildConfig.VERSION_NAME);
            int status = connection.getResponseCode();
            success = status > 0;
        } catch (Exception ignored) {
        } finally {
            if (connection != null) connection.disconnect();
            Log.i(TAG, "preconnect_ms=" +
                    (SystemClock.elapsedRealtime() - startedAt) +
                    " success=" + success);
        }
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
        if (activeTask != null) activeTask.cancel(true);
        executor.shutdownNow();
    }
}
