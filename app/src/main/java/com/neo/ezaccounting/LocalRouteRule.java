package com.neo.ezaccounting;

import java.util.Objects;

public final class LocalRouteRule {
    public final String ssid;
    public final String url;

    public LocalRouteRule(String ssid, String url) {
        this.ssid = ssid == null ? "" : ssid.trim();
        this.url = url == null ? "" : url.trim();
    }

    public boolean isDefault() {
        return ssid.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LocalRouteRule)) return false;
        LocalRouteRule rule = (LocalRouteRule) other;
        return ssid.equals(rule.ssid) && url.equals(rule.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ssid, url);
    }
}
