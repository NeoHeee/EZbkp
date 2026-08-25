package com.neo.ezaccounting;

import java.net.URI;

public final class ServerAddressValidator {
    private ServerAddressValidator() {}

    public static String normalize(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty()) return null;
        if (!value.contains("://")) value = isPrivateHost(value) ? "http://" + value : "https://" + value;
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null) return null;
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return null;
            String normalized = uri.toString();
            while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
            return normalized;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String normalizeLocal(String raw) {
        String normalized = normalize(raw);
        if (normalized == null) return null;
        try {
            URI uri = new URI(normalized);
            if ("http".equalsIgnoreCase(uri.getScheme()) && !isPrivateHost(uri.getHost())) {
                return null;
            }
            return normalized;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String normalizePublic(String raw) {
        String normalized = normalize(raw);
        if (normalized == null) return null;
        try {
            return "https".equalsIgnoreCase(new URI(normalized).getScheme()) ? normalized : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    static boolean isPrivateHost(String value) {
        if (value == null) return false;
        String host = value.split("[/?:]", 2)[0].toLowerCase();
        if (host.equals("localhost") || host.endsWith(".local")) return true;
        if (host.startsWith("10.") || host.startsWith("127.") ||
                host.startsWith("169.254.") || host.startsWith("192.168.")) return true;
        if (host.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) return true;
        String ipv6 = value.toLowerCase().replace("[", "").replace("]", "");
        if (ipv6.contains(":")) {
            return ipv6.equals("::1") || ipv6.startsWith("fc") || ipv6.startsWith("fd") ||
                    ipv6.matches("fe[89ab].*");
        }
        return !host.contains(".");
    }
}
