package com.neo.ezaccounting;

import java.util.Objects;

public final class LocalRouteRule {
    public final String name;
    public final String ssid;
    public final String url;

    public LocalRouteRule(String ssid, String url) {
        this("", ssid, url);
    }

    public LocalRouteRule(String name, String ssid, String url) {
        this.name = name == null ? "" : name.trim();
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
        return name.equals(rule.name) && ssid.equals(rule.ssid) && url.equals(rule.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, ssid, url);
    }
}
