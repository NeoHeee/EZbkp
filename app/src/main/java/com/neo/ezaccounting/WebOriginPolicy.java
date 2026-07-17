package com.neo.ezaccounting;

import java.net.URI;
import java.util.Locale;

public final class WebOriginPolicy {
    private WebOriginPolicy() {}

    public static boolean isSameOrigin(String baseUrl, String targetUrl) {
        try {
            URI base = new URI(baseUrl);
            URI target = new URI(targetUrl);
            String baseScheme = normalize(base.getScheme());
            String targetScheme = normalize(target.getScheme());
            String baseHost = normalize(base.getHost());
            String targetHost = normalize(target.getHost());
            if (baseScheme == null || targetScheme == null || baseHost == null || targetHost == null) return false;
            return baseScheme.equals(targetScheme)
                    && baseHost.equals(targetHost)
                    && effectivePort(base) == effectivePort(target);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isAllowedExternalScheme(String scheme) {
        String value = normalize(scheme);
        return "mailto".equals(value) || "tel".equals(value) || "sms".equals(value)
                || "geo".equals(value) || "market".equals(value);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        String scheme = normalize(uri.getScheme());
        if ("https".equals(scheme)) return 443;
        if ("http".equals(scheme)) return 80;
        return -1;
    }

    private static String normalize(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
