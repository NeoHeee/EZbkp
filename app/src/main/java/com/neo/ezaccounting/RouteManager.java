package com.neo.ezaccounting;

import android.os.Handler;
import android.os.Looper;

import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLException;

public final class RouteManager {
    public static final int TYPE_NONE = 0;
    public static final int TYPE_LOCAL = 1;
    public static final int TYPE_PUBLIC = 2;

    private static final long LOCAL_TOLERANCE_MS = 250L;
    private static final long LAST_ROUTE_TOLERANCE_MS = 180L;

    public enum ErrorKind {
        NONE,
        UNCONFIGURED,
        TIMEOUT,
        DNS,
        TLS,
        CONNECTION,
        HTTP,
        UNKNOWN
    }

    public interface Callback {
        void onResult(Selection selection);
    }

    public interface ProbeCallback {
        void onResult(ProbeResult result);
    }

    public static final class ProbeResult {
        public final String url;
        public final int type;
        public final boolean reachable;
        public final long latencyMs;
        public final int statusCode;
        public final ErrorKind errorKind;
        public final String errorMessage;

        ProbeResult(String url, int type, boolean reachable, long latencyMs, int statusCode) {
            this(url, type, reachable, latencyMs, statusCode,
                    reachable ? ErrorKind.NONE : ErrorKind.UNKNOWN, null);
        }

        ProbeResult(String url, int type, boolean reachable, long latencyMs, int statusCode,
                    ErrorKind errorKind, String errorMessage) {
            this.url = url;
            this.type = type;
            this.reachable = reachable;
            this.latencyMs = latencyMs;
            this.statusCode = statusCode;
            this.errorKind = errorKind == null ? ErrorKind.UNKNOWN : errorKind;
            this.errorMessage = errorMessage;
        }

        public boolean isConfigured() {
            return url != null && !url.trim().isEmpty();
        }

        public String label() {
            if (!isConfigured()) return "未配置";
            if (reachable && statusCode == 0) {
                return latencyMs > 0 ? "上次 " + latencyMs + " ms" : "待后台测速";
            }
            if (reachable) return latencyMs + " ms";
            return "不可用（" + errorLabel(errorKind) + "）";
        }

        public String diagnostic() {
            if (!isConfigured()) return "未配置地址";
            if (reachable && statusCode == 0) return "使用缓存线路，后台测速中";
            if (reachable) {
                return "HTTP " + statusCode + "，响应 " + latencyMs + " ms";
            }
            String detail = errorMessage == null || errorMessage.trim().isEmpty() ?
                    errorLabel(errorKind) : errorMessage.trim();
            if (statusCode > 0) detail += "（HTTP " + statusCode + "）";
            return detail;
        }

        private static String errorLabel(ErrorKind kind) {
            switch (kind) {
                case UNCONFIGURED: return "未配置";
                case TIMEOUT: return "连接超时";
                case DNS: return "域名解析失败";
                case TLS: return "HTTPS握手失败";
                case CONNECTION: return "无法建立连接";
                case HTTP: return "服务器返回异常状态";
                case NONE: return "正常";
                default: return "未知错误";
            }
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

        public ProbeResult resultFor(int type) {
            if (type == TYPE_LOCAL) return local;
            if (type == TYPE_PUBLIC) return publicRoute;
            return null;
        }
    }

    private final ExecutorService probes = Executors.newFixedThreadPool(2);
    private final ExecutorService coordinator = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void selectAsync(String localUrl, String publicUrl, String lastSuccessfulUrl,
                            Callback callback) {
        probeAllAsync(localUrl, publicUrl, selection -> {
            ProbeResult selected = selectBest(selection.local, selection.publicRoute,
                    lastSuccessfulUrl);
            callback.onResult(new Selection(selected, selection.local, selection.publicRoute));
        });
    }

    public void probeAllAsync(String localUrl, String publicUrl, Callback callback) {
        coordinator.execute(() -> {
            CompletableFuture<ProbeResult> localFuture = CompletableFuture.supplyAsync(
                    () -> probe(localUrl, TYPE_LOCAL, 1700), probes);
            CompletableFuture<ProbeResult> publicFuture = CompletableFuture.supplyAsync(
                    () -> probe(publicUrl, TYPE_PUBLIC, 2600), probes);
            ProbeResult local = localFuture.join();
            ProbeResult publicRoute = publicFuture.join();
            ProbeResult recommended = selectBest(local, publicRoute, null);
            Selection result = new Selection(recommended, local, publicRoute);
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    public void probeOnlyAsync(String url, int type, ProbeCallback callback) {
        int timeout = type == TYPE_LOCAL ? 1700 : 2600;
        coordinator.execute(() -> {
            ProbeResult result = probe(url, type, timeout);
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    static ProbeResult selectBest(ProbeResult local, ProbeResult publicRoute,
                                  String lastSuccessfulUrl) {
        boolean localOk = local != null && local.reachable;
        boolean publicOk = publicRoute != null && publicRoute.reachable;
        if (!localOk && !publicOk) return null;
        if (localOk && !publicOk) return local;
        if (!localOk) return publicRoute;

        if (lastSuccessfulUrl != null && !lastSuccessfulUrl.trim().isEmpty()) {
            if (lastSuccessfulUrl.equals(local.url) &&
                    local.latencyMs <= publicRoute.latencyMs + LAST_ROUTE_TOLERANCE_MS) {
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

    static ProbeResult selectForMode(RouteMode mode, ProbeResult local,
                                     ProbeResult publicRoute, String lastSuccessfulUrl) {
        RouteMode safeMode = mode == null ? RouteMode.AUTO : mode;
        if (safeMode == RouteMode.LOCAL) return local != null && local.reachable ? local : null;
        if (safeMode == RouteMode.PUBLIC) {
            return publicRoute != null && publicRoute.reachable ? publicRoute : null;
        }
        return selectBest(local, publicRoute, lastSuccessfulUrl);
    }

    private ProbeResult probe(String urlString, int type, int timeoutMs) {
        if (urlString == null || urlString.trim().isEmpty()) {
            return new ProbeResult(urlString, type, false, -1L, 0,
                    ErrorKind.UNCONFIGURED, "未配置地址");
        }

        HttpURLConnection connection = null;
        long startedAt = System.nanoTime();
        try {
            connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "EZAccounting/1.5.3");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            connection.connect();
            int code = connection.getResponseCode();
            long latency = elapsedMs(startedAt);
            boolean ok = (code >= 200 && code < 400) || code == 401 || code == 403;
            return new ProbeResult(urlString, type, ok, latency, code,
                    ok ? ErrorKind.NONE : ErrorKind.HTTP,
                    ok ? null : "服务器返回 HTTP " + code);
        } catch (SocketTimeoutException error) {
            return failure(urlString, type, startedAt, ErrorKind.TIMEOUT, "连接或读取超时");
        } catch (UnknownHostException error) {
            return failure(urlString, type, startedAt, ErrorKind.DNS, "无法解析服务器地址");
        } catch (SSLException error) {
            return failure(urlString, type, startedAt, ErrorKind.TLS, "HTTPS握手失败");
        } catch (ConnectException error) {
            return failure(urlString, type, startedAt, ErrorKind.CONNECTION, "服务器拒绝连接");
        } catch (Exception error) {
            String message = error.getMessage();
            return failure(urlString, type, startedAt, ErrorKind.UNKNOWN,
                    message == null || message.trim().isEmpty() ? "未知网络错误" : message);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private ProbeResult failure(String url, int type, long startedAt, ErrorKind kind,
                                String message) {
        return new ProbeResult(url, type, false, elapsedMs(startedAt), 0, kind, message);
    }

    private long elapsedMs(long startedAt) {
        return Math.max(1L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    public void shutdown() {
        coordinator.shutdownNow();
        probes.shutdownNow();
    }
}
