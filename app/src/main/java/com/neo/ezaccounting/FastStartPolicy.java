package com.neo.ezaccounting;

public final class FastStartPolicy {
    public static final class Candidate {
        public final String url;
        public final int type;
        public final String reason;

        Candidate(String url, int type, String reason) {
            this.url = url;
            this.type = type;
            this.reason = reason;
        }
    }

    private FastStartPolicy() {}

    public static Candidate select(RouteMode mode, String localUrl, String publicUrl,
                                   String lastSuccessfulUrl) {
        RouteMode safeMode = mode == null ? RouteMode.AUTO : mode;
        String local = clean(localUrl);
        String remote = clean(publicUrl);
        String last = clean(lastSuccessfulUrl);

        if (safeMode == RouteMode.LOCAL) {
            return local.isEmpty() ? null : new Candidate(local, RouteManager.TYPE_LOCAL,
                    "按固定本地模式快速进入");
        }
        if (safeMode == RouteMode.PUBLIC) {
            return remote.isEmpty() ? null : new Candidate(remote, RouteManager.TYPE_PUBLIC,
                    "按固定公网模式快速进入");
        }
        if (!last.isEmpty()) {
            if (last.equals(local)) {
                return new Candidate(local, RouteManager.TYPE_LOCAL, "使用上次成功的本地线路");
            }
            if (last.equals(remote)) {
                return new Candidate(remote, RouteManager.TYPE_PUBLIC, "使用上次成功的公网线路");
            }
        }
        if (!local.isEmpty() && remote.isEmpty()) {
            return new Candidate(local, RouteManager.TYPE_LOCAL, "仅配置本地线路，直接进入");
        }
        if (local.isEmpty() && !remote.isEmpty()) {
            return new Candidate(remote, RouteManager.TYPE_PUBLIC, "仅配置公网线路，直接进入");
        }
        return null;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
