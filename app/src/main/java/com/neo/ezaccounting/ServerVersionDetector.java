package com.neo.ezaccounting;

public final class ServerVersionDetector {
    public static final String API_PATH = "/api/v1/systems/version.json";

    private ServerVersionDetector() {}

    public static String parseVersion(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        if (value.startsWith("v") || value.startsWith("V")) value = value.substring(1);
        if (value.isEmpty() || value.length() > 64 ||
                !value.matches("[0-9A-Za-z._+\\-]+")) return null;
        return value;
    }

    public static String parseApiResponse(String json) {
        if (json == null || !json.matches("(?s).*\\\"success\\\"\\s*:\\s*true.*")) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "\\\"version\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(json);
        return matcher.find() ? parseVersion(matcher.group(1)) : null;
    }
}
