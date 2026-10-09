package com.neo.ezaccounting;

import android.net.Network;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public final class CloudflareDiagnosticManager {
    private static final int SAMPLE_COUNT = 3;
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS = 6000;

    public interface Callback { void onResult(Report report); }

    public static final class Probe {
        public final boolean reachable;
        public final boolean cloudflare;
        public final String address;
        public final String colo;
        public final long dnsMs;
        public final long tcpMs;
        public final long tlsMs;
        public final long httpMs;
        public final long totalMs;
        public final int successes;
        public final String error;

        Probe(boolean reachable, boolean cloudflare, String address, String colo,
              long dnsMs, long tcpMs, long tlsMs, long httpMs, long totalMs, int successes,
              String error) {
            this.reachable = reachable;
            this.cloudflare = cloudflare;
            this.address = address == null ? "" : address;
            this.colo = colo == null ? "" : colo;
            this.dnsMs = dnsMs;
            this.tcpMs = tcpMs;
            this.tlsMs = tlsMs;
            this.httpMs = httpMs;
            this.totalMs = totalMs;
            this.successes = successes;
            this.error = error == null ? "" : error;
        }

        public String successLabel() {
            return successes + "/" + SAMPLE_COUNT + " 成功";
        }
    }

    public static final class Report {
        public final Probe normal;
        public final Probe preferred;
        public final String preferredIp;
        public final String networkKey;
        public final long testedAt;

        Report(Probe normal, Probe preferred, String preferredIp,
               String networkKey, long testedAt) {
            this.normal = normal;
            this.preferred = preferred;
            this.preferredIp = preferredIp == null ? "" : preferredIp;
            this.networkKey = networkKey == null ? "" : networkKey;
            this.testedAt = testedAt;
        }

        public String conclusion() {
            return conclusion(normal, preferred);
        }
    }

    private static final class Sample {
        String address = "";
        String colo = "";
        long dnsMs;
        long tcpMs;
        long tlsMs;
        long httpMs;
        long totalMs;
        boolean cloudflare;
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<Socket> activeSockets = ConcurrentHashMap.newKeySet();
    private final AtomicLong generation = new AtomicLong();
    private Future<?> activeTask;

    public synchronized void diagnose(String publicUrl, String preferredIp,
                                      NetworkState networkState, Callback callback) {
        cancel();
        final long requestGeneration = generation.get();
        final String safeIp = normalizePublicIp(preferredIp);
        final NetworkState state = networkState == null ? NetworkState.none() : networkState;
        activeTask = executor.submit(() -> {
            Report report;
            try {
                URL base = new URL(publicUrl);
                if (!"https".equalsIgnoreCase(base.getProtocol())) {
                    throw new IllegalArgumentException("公网地址必须使用 HTTPS");
                }
                Probe normal = probe(base, null, state.network);
                Probe preferred = safeIp == null ? null : probe(base, safeIp, state.network);
                report = new Report(normal, preferred, safeIp, state.key(),
                        System.currentTimeMillis());
            } catch (Exception error) {
                Probe failed = new Probe(false, false, "", "", -1, -1, -1, -1,
                        -1, 0, safeMessage(error));
                report = new Report(failed, null, safeIp, state.key(),
                        System.currentTimeMillis());
            }
            Report delivered = report;
            mainHandler.post(() -> {
                if (generation.get() == requestGeneration) callback.onResult(delivered);
            });
        });
    }

    private Probe probe(URL base, String forcedIp, Network network) {
        List<Sample> samples = new ArrayList<>();
        String lastError = "未完成测试";
        for (int index = 0; index < SAMPLE_COUNT && !Thread.currentThread().isInterrupted(); index++) {
            try {
                Sample sample = sample(base, forcedIp, network);
                if (!sample.cloudflare) throw new IllegalStateException("响应未识别为 Cloudflare");
                samples.add(sample);
            } catch (Exception error) {
                lastError = safeMessage(error);
            }
        }
        if (samples.isEmpty()) {
            return new Probe(false, false, forcedIp, "", -1, -1, -1, -1,
                    -1, 0, lastError);
        }
        Sample representative = samples.get(samples.size() - 1);
        return new Probe(true, true, representative.address, representative.colo,
                median(samples, 0), median(samples, 1), median(samples, 2),
                median(samples, 3), median(samples, 4), samples.size(),
                samples.size() == SAMPLE_COUNT ? "" : lastError);
    }

    private Sample sample(URL base, String forcedIp, Network network) throws Exception {
        String host = base.getHost();
        int port = base.getPort() > 0 ? base.getPort() : 443;
        long dnsStarted = System.nanoTime();
        InetAddress address = forcedIp == null ? resolve(host, network)[0] :
                InetAddress.getByName(forcedIp);
        long dns = forcedIp == null ? elapsed(dnsStarted) : -1L;
        Socket raw = network == null ? new Socket() : network.getSocketFactory().createSocket();
        Socket active = raw;
        activeSockets.add(raw);
        long totalStarted = System.nanoTime();
        try {
            long phase = System.nanoTime();
            raw.connect(new InetSocketAddress(address, port), CONNECT_TIMEOUT_MS);
            long tcp = elapsed(phase);
            raw.setSoTimeout(READ_TIMEOUT_MS);

            SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                    .createSocket(raw, host, port, true);
            activeSockets.remove(raw);
            activeSockets.add(ssl);
            active = ssl;
            SSLParameters parameters = ssl.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            parameters.setServerNames(Collections.singletonList(new SNIHostName(host)));
            ssl.setSSLParameters(parameters);
            phase = System.nanoTime();
            ssl.startHandshake();
            long tls = elapsed(phase);

            String path = ServerVersionDetector.API_PATH;
            String request = "GET " + path + " HTTP/1.1\r\nHost: " + host +
                    "\r\nUser-Agent: Ledgerly/" + BuildConfig.VERSION_NAME +
                    "\r\nAccept: application/json\r\nConnection: close\r\n\r\n";
            OutputStream output = ssl.getOutputStream();
            output.write(request.getBytes(StandardCharsets.US_ASCII));
            output.flush();
            phase = System.nanoTime();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    ssl.getInputStream(), StandardCharsets.ISO_8859_1));
            String status = reader.readLine();
            long http = elapsed(phase);
            if (status == null || !status.startsWith("HTTP/")) {
                throw new IllegalStateException("服务器未返回有效 HTTP 响应");
            }
            String cfRay = "";
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0 && "cf-ray".equalsIgnoreCase(line.substring(0, colon).trim())) {
                    cfRay = line.substring(colon + 1).trim();
                }
            }
            Sample sample = new Sample();
            sample.address = address.getHostAddress();
            sample.colo = parseColo(cfRay);
            sample.cloudflare = !cfRay.isEmpty();
            sample.dnsMs = dns;
            sample.tcpMs = tcp;
            sample.tlsMs = tls;
            sample.httpMs = http;
            sample.totalMs = elapsed(totalStarted);
            return sample;
        } finally {
            activeSockets.remove(raw);
            activeSockets.remove(active);
            try { active.close(); } catch (Exception ignored) {}
            if (active != raw) try { raw.close(); } catch (Exception ignored) {}
        }
    }

    private InetAddress[] resolve(String host, Network network) throws Exception {
        return network == null ? InetAddress.getAllByName(host) : network.getAllByName(host);
    }

    static String normalizePublicIp(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String candidate = value.trim();
        if (!candidate.matches("[0-9A-Fa-f:.]+")) return null;
        if (candidate.indexOf(':') < 0 && !strictIpv4(candidate)) return null;
        try {
            InetAddress address = InetAddress.getByName(candidate);
            if (!(address instanceof Inet4Address) && !(address instanceof Inet6Address)) return null;
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() ||
                    address.isLinkLocalAddress() || address.isSiteLocalAddress() ||
                    address.isMulticastAddress() || reserved(address.getAddress())) return null;
            return address.getHostAddress();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean strictIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || !part.matches("[0-9]+")) return false;
            int number;
            try { number = Integer.parseInt(part); } catch (NumberFormatException error) {
                return false;
            }
            if (number < 0 || number > 255) return false;
        }
        return true;
    }

    private static boolean reserved(byte[] bytes) {
        if (bytes.length == 4) {
            int a = bytes[0] & 255, b = bytes[1] & 255, c = bytes[2] & 255;
            return a == 0 || a == 10 || a == 127 || a >= 224 ||
                    (a == 100 && b >= 64 && b <= 127) ||
                    (a == 169 && b == 254) || (a == 172 && b >= 16 && b <= 31) ||
                    (a == 192 && b == 168) || (a == 192 && b == 0) ||
                    (a == 192 && b == 0 && c == 2) ||
                    (a == 198 && (b == 18 || b == 19)) ||
                    (a == 198 && b == 51 && c == 100) ||
                    (a == 203 && b == 0 && c == 113);
        }
        return bytes.length == 16 && ((bytes[0] & 0xfe) == 0xfc ||
                ((bytes[0] & 255) == 0x20 && (bytes[1] & 255) == 0x01 &&
                        (bytes[2] & 255) == 0x0d && (bytes[3] & 255) == 0xb8));
    }

    static String conclusion(Probe normal, Probe preferred) {
        if (normal == null || !normal.reachable) return "默认线路未完成，暂时无法比较。";
        if (!normal.cloudflare) return "当前公网地址未识别为 Cloudflare。";
        if (preferred == null) return "填写 CloudflareSpeedTest 得到的公网 IP 后可进行对比。";
        if (!preferred.reachable) return "优选 IP未通过域名证书或 Cloudflare响应验证，不建议使用。";
        long saved = normal.totalMs - preferred.totalMs;
        int percent = normal.totalMs <= 0 ? 0 : (int) (saved * 100L / normal.totalMs);
        if (preferred.successes < SAMPLE_COUNT) return "优选 IP存在失败或波动，不建议长期使用。";
        if (saved >= 100 || percent >= 20) {
            return "优选 IP实际访问约改善 " + Math.max(0, percent) + "%（" + saved +
                    " ms），可作为本地 DNS 配置参考。";
        }
        if (saved > 0) return "优选 IP略有改善，但不足以证明长期使用更快。";
        return "优选 IP没有改善当前网络连接，不建议使用。";
    }

    private static long median(List<Sample> samples, int field) {
        List<Long> values = new ArrayList<>();
        for (Sample sample : samples) {
            values.add(field == 0 ? sample.dnsMs : field == 1 ? sample.tcpMs :
                    field == 2 ? sample.tlsMs : field == 3 ? sample.httpMs : sample.totalMs);
        }
        Collections.sort(values);
        return values.get(values.size() / 2);
    }

    private static String parseColo(String cfRay) {
        if (cfRay == null) return "";
        int dash = cfRay.lastIndexOf('-');
        return dash >= 0 && dash + 1 < cfRay.length() ?
                cfRay.substring(dash + 1).trim().toUpperCase(Locale.US) : "";
    }

    private static long elapsed(long started) {
        return Math.max(1L, (System.nanoTime() - started) / 1_000_000L);
    }

    private static String safeMessage(Throwable error) {
        if (error == null) return "未知错误";
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ?
                error.getClass().getSimpleName() : message.trim();
    }

    public synchronized void cancel() {
        generation.incrementAndGet();
        Future<?> task = activeTask;
        activeTask = null;
        if (task != null) task.cancel(true);
        for (Socket socket : activeSockets) {
            try { socket.close(); } catch (Exception ignored) {}
        }
        activeSockets.clear();
    }

    public void shutdown() {
        cancel();
        executor.shutdownNow();
    }
}
