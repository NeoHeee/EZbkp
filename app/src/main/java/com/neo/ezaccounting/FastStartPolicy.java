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

    public static Candidate select(String localUrl, String publicUrl) {
        String local = clean(localUrl);
        String remote = clean(publicUrl);
        if (!local.isEmpty()) {
            return new Candidate(local, RouteManager.TYPE_LOCAL,
                    "当前网络匹配局域网地址，自动优先使用");
        }
        if (!remote.isEmpty()) {
            return new Candidate(remote, RouteManager.TYPE_PUBLIC,
                    "当前网络未匹配局域网地址，自动使用公网");
        }
        return null;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
