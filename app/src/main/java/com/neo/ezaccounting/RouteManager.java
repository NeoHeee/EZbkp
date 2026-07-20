package com.neo.ezaccounting;

import android.os.Handler;
import android.os.Looper;

import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLException;

public final class RouteManager {
    public static final int TYPE_NONE = 0;
    public static final int TYPE_LOCAL = 1;
    public static final int TYPE_PUBLIC = 2;

    static final int LOCAL_TIMEOUT_MS = 1700;
    static final int PUBLIC_TIMEOUT_MS = 6000;
    static final int MAX_REDIRECTS = 5;

    private static final long LOCAL_TOLERANCE_MS = 250L;
    private static final long LAST_ROUTE_TOLERANCE_MS = 180L;
    private static final String PROBE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120 Mobile Safari/537.36 EZAccounting/1.5.10";

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
        public final String finalUrl;
        public final int redirectCount;
        public final String resolvedAddresses;
        public final boolean verificationPending;
        public final boolean webVerified;

        ProbeResult(String url, int type, boolean reachable, long latencyMs, int statusCode) {
            this(url, type, reachable, latencyMs, statusCode,
                    reachable ? ErrorKind.NONE : ErrorKind.UNKNOWN, null,
                    url, 0, "", false, false);
        }

        ProbeResult(String url, int type, boolean reachable, long latencyMs, int statusCode,
                    ErrorKind errorKind, String errorMessage) {
            this(url, type, reachable, latencyMs, statusCode, errorKind, errorMessage,
                    url, 0, "", false, false);
        }

        ProbeResult(String url, int type, boolean reachable, long latencyMs, int statusCode,
                    ErrorKind errorKind, String errorMessage, String finalUrl,
                    int redirectCount, String resolvedAddresses,
                    boolean verificationPending, boolean webVerified) {
            this.url = url;
            this.type = type;
            this.reachable = reachable;
            this.latencyMs = latencyMs;
            this.statusCode = statusCode;
            this.errorKind = errorKind == null ? ErrorKind.UNKNOWN : errorKind;
            this.errorMessage = errorMessage;
            this.finalUrl = finalUrl == null || finalUrl.trim().isEmpty() ? url : finalUrl;
            this.redirectCount = Math.max(0, redirectCount);
            this.resolvedAddresses = resolvedAddresses == null ? "" : resolvedAddresses.trim();
            this.verificationPending = verificationPending;
            this.webVerified = webVerified;
        }

        public boolean isConfigured() {
            return url != null && !url.trim().isEmpty();
        }

        public String label() {
            if (!isConfigured()) return "未配置";
            if (webVerified) return "网页访问正常";
            if (verificationPending) return "待网页验证";
            if (reachable && statusCode == 0) {
                return latencyMs > 0 ? "上次 " + latencyMs + " ms" : "待后台测速";
            }
            if (reachable) return latencyMs + " ms";
            return "不可用（" + errorLabel(errorKind) + "）";
        }

        public String diagnostic() {
            if (!isConfigured()) return "未配置地址";
            String network = networkDetails();
            if (webVerified) {
                String prior = errorKind == ErrorKind.NONE ? "" :
                        "；独立探测曾报告：" + failureDetail();
                return "WebView 已实际加载成功" + prior + network;
            }
            if (verificationPending) {
                return "独立探测未通过，已交由 WebView 实际验证：" +
                        failureDetail() + network;
            }
            if (reachable && statusCode == 0) {
                return "使用缓存线路，后台测速中" + network;
            }
            if (reachable) {
                return "HTTP " + statusCode + "，响应 " + latencyMs + " ms" + network;
            }
            String detail = failureDetail();
            if (latencyMs > 0) detail += "；探测耗时 " + latencyMs + " ms";
            if (statusCode > 0) detail += "（HTTP " + statusCode + "）";
            return detail + network;
        }

        ProbeResult asWebVerificationCandidate() {
            return new ProbeResult(url, type, true, latencyMs, statusCode,
                    errorKind, errorMessage, finalUrl, redirectCount, resolvedAddresses,
                    true, false);
        }

        ProbeResult asWebVerified(String actualUrl) {
            return new ProbeResult(url, type, true, latencyMs, statusCode,
                    errorKind, errorMessage,
                    actualUrl == null || actualUrl.trim().isEmpty() ? finalUrl : actualUrl,
                    redirectCount, resolvedAddresses, false, true);
        }

        private String failureDetail() {
            return errorMessage == null || errorMessage.trim().isEmpty() ?
                    errorLabel(errorKind) : errorMessage.trim();
        }

        private String networkDetails() {
            StringBuilder detail = new StringBuilder();
            if (redirectCount > 0) {
                detail.append("；重定向 ").append(redirectCount).append(" 次");
            }
            if (finalUrl != null && url != null && !finalUrl.equals(url)) {
                detail.append("；最终地址：").append(finalUrl);
            }
            if (!resolvedAddresses.isEmpty()) {
                detail.append("；解析：").append(resolvedAddresses);
            }
            return detail.toString();
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
            if (selected != null && selected.type == type) return selected;
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
                    () -> probe(localUrl, TYPE_LOCAL, LOCAL_TIMEOUT_MS), probes);
            CompletableFuture<ProbeResult> publicFuture = CompletableFuture.supplyAsync(
                    () -> probe(publicUrl, TYPE_PUBLIC, PUBLIC_TIMEOUT_MS), probes);
            ProbeResult local = localFuture.join();
            ProbeResult publicRoute = publicFuture.join();
            ProbeResult recommended = selectBest(local, publicRoute, null);
            Selection result = new Selection(recommended, local, publicRoute);
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    public void probeOnlyAsync(String url, int type, ProbeCallback callback) {
        int timeout = type == TYPE_LOCAL ? LOCAL_TIMEOUT_MS : PUBLIC_TIMEOUT_MS;
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
            if (lastSuccessfulUrl.equals(local.url) && local.webVerified) return local;
            if (lastSuccessfulUrl.equals(publicRoute.url) && publicRoute.webVerified) {
                return publicRoute;
            }
            if (lastSuccessfulUrl.equals(local.url) &&
                    comparableLatency(local) <= comparableLatency(publicRoute) +
                            LAST_ROUTE_TOLERANCE_MS) {
                return local;
            }
            if (lastSuccessfulUrl.equals(publicRoute.url) &&
                    comparableLatency(publicRoute) <= comparableLatency(local) +
                            LAST_ROUTE_TOLERANCE_MS) {
                return publicRoute;
            }
        }

        if (comparableLatency(local) <= comparableLatency(publicRoute) + LOCAL_TOLERANCE_MS) {
            return local;
        }
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

    static ProbeResult selectForModeWithWebFallback(RouteMode mode, ProbeResult local,
                                                     ProbeResult publicRoute,
                                                     String lastSuccessfulUrl,
                                                     boolean allowFallback) {
        ProbeResult selected = selectForMode(mode, local, publicRoute, lastSuccessfulUrl);
        if (selected != null || !allowFallback || !eligibleForWebVerification(publicRoute)) {
            return selected;
        }

        RouteMode safeMode = mode == null ? RouteMode.AUTO : mode;
        if (safeMode == RouteMode.PUBLIC) return publicRoute.asWebVerificationCandidate();
        if (safeMode == RouteMode.LOCAL) return null;

        boolean localConfigured = local != null && local.isConfigured();
        boolean localReachable = local != null && local.reachable;
        if (!localConfigured) return publicRoute.asWebVerificationCandidate();
        if (!localReachable && publicRoute.url != null &&
                publicRoute.url.equals(lastSuccessfulUrl)) {
            return publicRoute.asWebVerificationCandidate();
        }
        return null;
    }

    private static boolean eligibleForWebVerification(ProbeResult result) {
        return result != null && result.isConfigured() && !result.reachable &&
                result.errorKind != ErrorKind.UNCONFIGURED;
    }

    private static long comparableLatency(ProbeResult result) {
        return result == null || result.latencyMs <= 0 ? Long.MAX_VALUE / 4 : result.latencyMs;
    }

    private ProbeResult probe(String urlString, int type, int timeoutMs) {
        if (urlString == null || urlString.trim().isEmpty()) {
            return new ProbeResult(urlString, type, false, -1L, 0,
                    ErrorKind.UNCONFIGURED, "未配置地址");
        }

        String originalUrl = urlString.trim();
        String currentUrl = originalUrl;
        int redirects = 0;
        Set<String> resolved = new LinkedHashSet<>();
        long startedAt = System.nanoTime();

        try {
            while (true) {
                URL target = new URL(currentUrl);
                String scheme = target.getProtocol();
                if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                    return failure(originalUrl, type, startedAt, ErrorKind.HTTP,
                            "重定向到了不受支持的协议：" + scheme,
                            0, currentUrl, redirects, resolved);
                }
                resolveAddresses(target, resolved);

                HttpURLConnection connection = null;
                try {
                    connection = (HttpURLConnection) target.openConnection();
                    connection.setConnectTimeout(timeoutMs);
                    connection.setReadTimeout(timeoutMs);
                    connection.setInstanceFollowRedirects(false);
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("User-Agent", PROBE_USER_AGENT);
                    connection.setRequestProperty("Accept",
                            "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8");
                    connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7");
                    connection.setRequestProperty("Cache-Control", "no-cache");
                    connection.connect();
                    int code = connection.getResponseCode();

                    if (code >= 300 && code < 400) {
                        String location = connection.getHeaderField("Location");
                        if (location != null && !location.trim().isEmpty()) {
                            if (redirects >= MAX_REDIRECTS) {
                                return failure(originalUrl, type, startedAt, ErrorKind.HTTP,
                                        "重定向次数超过 " + MAX_REDIRECTS + " 次",
                                        code, currentUrl, redirects, resolved);
                            }
                            currentUrl = new URL(target, location).toString();
                            redirects++;
                            continue;
                        }
                    }

                    long latency = elapsedMs(startedAt);
                    boolean ok = (code >= 200 && code < 400) || code == 401 || code == 403;
                    return new ProbeResult(originalUrl, type, ok, latency, code,
                            ok ? ErrorKind.NONE : ErrorKind.HTTP,
                            ok ? null : "服务器返回 HTTP " + code,
                            currentUrl, redirects, joinAddresses(resolved), false, false);
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        } catch (SocketTimeoutException error) {
            return failure(originalUrl, type, startedAt, ErrorKind.TIMEOUT,
                    "连接或读取超时", 0, currentUrl, redirects, resolved);
        } catch (java.net.UnknownHostException error) {
            return failure(originalUrl, type, startedAt, ErrorKind.DNS,
                    "无法解析服务器地址：" + errorSummary(error),
                    0, currentUrl, redirects, resolved);
        } catch (SSLException error) {
            return failure(originalUrl, type, startedAt, ErrorKind.TLS,
                    "HTTPS握手失败：" + errorSummary(error),
                    0, currentUrl, redirects, resolved);
        } catch (ConnectException error) {
            return failure(originalUrl, type, startedAt, ErrorKind.CONNECTION,
                    "服务器拒绝连接：" + errorSummary(error),
                    0, currentUrl, redirects, resolved);
        } catch (Exception error) {
            return failure(originalUrl, type, startedAt, ErrorKind.UNKNOWN,
                    errorSummary(error), 0, currentUrl, redirects, resolved);
        }
    }

    private void resolveAddresses(URL target, Set<String> resolved) throws Exception {
        String host = target.getHost();
        if (host == null || host.trim().isEmpty()) return;
        InetAddress[] addresses = InetAddress.getAllByName(host);
        for (InetAddress address : addresses) {
            if (address != null && address.getHostAddress() != null) {
                resolved.add(address.getHostAddress());
            }
        }
    }

    private ProbeResult failure(String url, int type, long startedAt, ErrorKind kind,
                                String message, int statusCode, String finalUrl,
                                int redirects, Set<String> resolved) {
        return new ProbeResult(url, type, false, elapsedMs(startedAt), statusCode,
                kind, message, finalUrl, redirects, joinAddresses(resolved), false, false);
    }

    private String joinAddresses(Set<String> addresses) {
        if (addresses == null || addresses.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        for (String address : addresses) {
            if (result.length() > 0) result.append(", ");
            result.append(address);
        }
        return result.toString();
    }

    private String errorSummary(Throwable error) {
        if (error == null) return "未知网络错误";
        Throwable deepest = error;
        int depth = 0;
        while (deepest.getCause() != null && deepest.getCause() != deepest && depth < 8) {
            deepest = deepest.getCause();
            depth++;
        }
        String type = deepest.getClass().getSimpleName();
        String message = deepest.getMessage();
        if (message == null || message.trim().isEmpty()) return type;
        return type + "：" + message.trim();
    }

    private long elapsedMs(long startedAt) {
        return Math.max(1L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    public void shutdown() {
        coordinator.shutdownNow();
        probes.shutdownNow();
    }
}
