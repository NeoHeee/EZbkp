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

    static boolean isPrivateHost(String value) {
        String host = value.split("[/?:]", 2)[0].toLowerCase();
        if (host.equals("localhost") || host.endsWith(".local")) return true;
        if (host.startsWith("10.") || host.startsWith("192.168.")) return true;
        if (host.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) return true;
        return host.matches("[0-9a-f:]+") || !host.contains(".");
    }
}
