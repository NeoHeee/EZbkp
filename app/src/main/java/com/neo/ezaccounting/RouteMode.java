package com.neo.ezaccounting;

public enum RouteMode {
    AUTO,
    LOCAL,
    PUBLIC;

    public static RouteMode fromStored(String value) {
        if (value == null || value.trim().isEmpty()) return AUTO;
        try {
            return RouteMode.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return AUTO;
        }
    }

    public String label() {
        switch (this) {
            case LOCAL: return "固定本地线路";
            case PUBLIC: return "固定公网线路";
            default: return "自动选择";
        }
    }
}
