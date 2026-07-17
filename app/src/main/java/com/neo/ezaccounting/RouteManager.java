package com.neo.ezaccounting;

import android.os.Handler;
import android.os.Looper;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RouteManager {
    public static final int TYPE_NONE = 0;
    public static final int TYPE_LOCAL = 1;
    public static final int TYPE_PUBLIC = 2;

    private static final long LOCAL_TOLERANCE_MS = 250L;
    private static final long LAST_ROUTE_TOLERANCE_MS = 180L;

    public interface Callback {
        void onResult(Selection selection);
    }

    public static final class ProbeResult {
        public final String url;
        public final int type;
        public final boolean reachable;
        public final long latencyMs;
        public final int statusCode;

        ProbeResult(String url, int type, boolean reachable, long latencyMs, int statusCode) {
            this.url = url;
            this.type = type;
            this.reachable = reachable;
            this.latencyMs = latencyMs;
            this.statusCode = statusCode;
        }

        public String label() {
            if (url == null || url.trim().isEmpty()) return "未配置";
            if (!reachable) return "不可用";
            return latencyMs + " ms";
        }
    }

    public static final class Selection {
        public final ProbeResult selected;
        public final ProbeResult local;
        public final ProbeResult publicRoute;

        Selection(ProbeResult selected, ProbeResult local, ProbeResult publicRoute) {
            this.selected = selected;
            this.local = local;
            this.publicRoute = publicRoute;
        }

        public boolean hasRoute() {
            return selected != null && selected.reachable;
        }
    }

    private final ExecutorService probes = Executors.newFixedThreadPool(2);
    private final ExecutorService coordinator = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void selectAsync(String localUrl, String publicUrl, String lastSuccessfulUrl, Callback callback) {
        coordinator.execute(() -> {
            CompletableFuture<ProbeResult> localFuture = CompletableFuture.supplyAsync(
                    () -> probe(localUrl, TYPE_LOCAL, 1700), probes);
            CompletableFuture<ProbeResult> publicFuture = CompletableFuture.supplyAsync(
                    () -> probe(publicUrl, TYPE_PUBLIC, 2600), probes);

            ProbeResult local = localFuture.join();
            ProbeResult publicRoute = publicFuture.join();
            ProbeResult selected = selectBest(local, publicRoute, lastSuccessfulUrl);
            Selection selection = new Selection(selected, local, publicRoute);
            mainHandler.post(() -> callback.onResult(selection));
        });
    }

    static ProbeResult selectBest(ProbeResult local, ProbeResult publicRoute, String lastSuccessfulUrl) {
        boolean localOk = local != null && local.reachable;
        boolean publicOk = publicRoute != null && publicRoute.reachable;
        if (!localOk && !publicOk) return null;
        if (localOk && !publicOk) return local;
        if (!localOk) return publicRoute;

        if (lastSuccessfulUrl != null && !lastSuccessfulUrl.trim().isEmpty()) {
            if (lastSuccessfulUrl.equals(local.url) && local.latencyMs <= publicRoute.latencyMs + LAST_ROUTE_TOLERANCE_MS) {
                return local;
            }
            if (lastSuccessfulUrl.equals(publicRoute.url) &&
                    publicRoute.latencyMs <= local.latencyMs + LAST_ROUTE_TOLERANCE_MS) {
                return publicRoute;
            }
        }

        if (local.latencyMs <= publicRoute.latencyMs + LOCAL_TOLERANCE_MS) return local;
        return publicRoute;
    }

    private ProbeResult probe(String urlString, int type, int timeoutMs) {
        if (urlString == null || urlString.trim().isEmpty()) {
            return new ProbeResult(urlString, type, false, -1L, 0);
        }

        HttpURLConnection connection = null;
        long startedAt = System.nanoTime();
        try {
            connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "EZAccounting/1.4.0");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            connection.connect();
            int code = connection.getResponseCode();
            long latency = Math.max(1L, (System.nanoTime() - startedAt) / 1_000_000L);
            boolean ok = (code >= 200 && code < 400) || code == 401 || code == 403;
            return new ProbeResult(urlString, type, ok, latency, code);
        } catch (Exception ignored) {
            long latency = Math.max(1L, (System.nanoTime() - startedAt) / 1_000_000L);
            return new ProbeResult(urlString, type, false, latency, 0);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public void shutdown() {
        coordinator.shutdownNow();
        probes.shutdownNow();
    }
}
