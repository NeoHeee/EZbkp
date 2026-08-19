package com.neo.ezaccounting;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LocalRouteRules {
    private LocalRouteRules() {}

    public static List<LocalRouteRule> decode(String json, String legacyLocalUrl) {
        List<LocalRouteRule> rules = new ArrayList<>();
        if (json != null && !json.trim().isEmpty()) {
            try {
                JSONArray array = new JSONArray(json);
                for (int index = 0; index < array.length(); index++) {
                    JSONObject item = array.optJSONObject(index);
                    if (item == null) continue;
                    String url = item.optString("url", "").trim();
                    if (!url.isEmpty()) {
                        rules.add(new LocalRouteRule(item.optString("name", ""),
                                item.optString("ssid", ""), url));
                    }
                }
            } catch (Exception ignored) {
                // Fall through to the legacy address so malformed preferences never strand users.
            }
        }
        if (rules.isEmpty() && legacyLocalUrl != null && !legacyLocalUrl.trim().isEmpty()) {
            rules.add(new LocalRouteRule("", legacyLocalUrl));
        }
        return Collections.unmodifiableList(rules);
    }

    public static String encode(List<LocalRouteRule> rules) {
        JSONArray array = new JSONArray();
        if (rules != null) {
            for (LocalRouteRule rule : rules) {
                if (rule == null || rule.url.isEmpty()) continue;
                JSONObject item = new JSONObject();
                try {
                    item.put("ssid", rule.ssid);
                    item.put("name", rule.name);
                    item.put("url", rule.url);
                    array.put(item);
                } catch (Exception ignored) {
                }
            }
        }
        return array.toString();
    }

    public static String select(List<LocalRouteRule> rules, String currentSsid,
                                boolean wifiConnected) {
        LocalRouteRule rule = selectRule(rules, currentSsid, wifiConnected);
        return rule == null ? "" : rule.url;
    }

    public static LocalRouteRule selectRule(List<LocalRouteRule> rules, String currentSsid,
                                             boolean wifiConnected) {
        if (rules == null || rules.isEmpty() || !wifiConnected) return null;
        String normalizedSsid = currentSsid == null ? "" : currentSsid.trim();
        if (wifiConnected && !normalizedSsid.isEmpty()) {
            for (LocalRouteRule rule : rules) {
                if (rule != null && !rule.ssid.isEmpty() &&
                        rule.ssid.equalsIgnoreCase(normalizedSsid)) {
                    return rule;
                }
            }
        }
        for (LocalRouteRule rule : rules) {
            if (rule != null && rule.isDefault()) return rule;
        }
        return null;
    }

    public static String selectExactWifiMatch(List<LocalRouteRule> rules, String currentSsid,
                                               boolean wifiConnected) {
        if (rules == null || rules.isEmpty() || !wifiConnected || currentSsid == null) return "";
        String normalizedSsid = currentSsid.trim();
        if (normalizedSsid.isEmpty()) return "";
        for (LocalRouteRule rule : rules) {
            if (rule != null && !rule.ssid.isEmpty() &&
                    rule.ssid.equalsIgnoreCase(normalizedSsid)) return rule.url;
        }
        return "";
    }
}
