package com.neo.ezaccounting;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class UpdateChecker {
    private static final String API_URL =
            "https://api.github.com/repos/NeoHeee/EZbkp/releases/latest";

    public interface Callback {
        void onResult(Result result);
    }

    public static final class Result {
        public final boolean success;
        public final boolean updateAvailable;
        public final String latestVersion;
        public final String releaseName;
        public final String notes;
        public final String releaseUrl;
        public final String apkUrl;
        public final String error;

        Result(boolean success, boolean updateAvailable, String latestVersion,
               String releaseName, String notes, String releaseUrl, String apkUrl, String error) {
            this.success = success;
            this.updateAvailable = updateAvailable;
            this.latestVersion = latestVersion;
            this.releaseName = releaseName;
            this.notes = notes;
            this.releaseUrl = releaseUrl;
            this.apkUrl = apkUrl;
            this.error = error;
        }
    }

    private UpdateChecker() {}

    public static void checkAsync(String currentVersion, Callback callback) {
        new Thread(() -> {
            Result result = check(currentVersion);
            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(result));
        }, "ez-update-check").start();
    }

    static Result check(String currentVersion) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(API_URL).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(6000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            connection.setRequestProperty("User-Agent", "EZAccounting/1.4.0");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return failure("GitHub 返回 HTTP " + code);
            }

            StringBuilder json = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) json.append(line);
            }

            JSONObject root = new JSONObject(json.toString());
            String tag = root.optString("tag_name", "");
            String latest = normalizeVersion(tag);
            String releaseName = root.optString("name", tag);
            String notes = root.optString("body", "暂无更新说明");
            String releaseUrl = root.optString("html_url", "");
            String apkUrl = "";
            JSONArray assets = root.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset == null) continue;
                    String name = asset.optString("name", "").toLowerCase();
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url", "");
                        break;
                    }
                }
            }
            boolean newer = compareVersions(latest, currentVersion) > 0;
            return new Result(true, newer, latest, releaseName, notes,
                    releaseUrl, apkUrl, null);
        } catch (Exception error) {
            return failure(error.getMessage() == null ? "网络请求失败" : error.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static Result failure(String error) {
        return new Result(false, false, "", "", "", "", "", error);
    }

    static String normalizeVersion(String version) {
        if (version == null) return "0";
        String normalized = version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        int dash = normalized.indexOf('-');
        if (dash >= 0) normalized = normalized.substring(0, dash);
        return normalized.isEmpty() ? "0" : normalized;
    }

    static int compareVersions(String left, String right) {
        String[] a = normalizeVersion(left).split("\\.");
        String[] b = normalizeVersion(right).split("\\.");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int av = i < a.length ? parsePart(a[i]) : 0;
            int bv = i < b.length ? parsePart(b[i]) : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private static int parsePart(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9].*$", ""));
        } catch (Exception ignored) {
            return 0;
        }
    }
}
