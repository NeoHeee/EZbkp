package com.neo.ezaccounting;

public final class HomeRouteResolver {
    private HomeRouteResolver() {}

    public static String resolve(String currentUrl, String lastRoute,
                                 String localUrl, String publicUrl) {
        if (!blank(currentUrl)) {
            if (!blank(lastRoute) && WebOriginPolicy.isSameOrigin(lastRoute, currentUrl)) {
                return lastRoute;
            }
            if (!blank(localUrl) && WebOriginPolicy.isSameOrigin(localUrl, currentUrl)) {
                return localUrl;
            }
            if (!blank(publicUrl) && WebOriginPolicy.isSameOrigin(publicUrl, currentUrl)) {
                return publicUrl;
            }
        }
        if (!blank(lastRoute)) return lastRoute;
        if (!blank(localUrl)) return localUrl;
        return publicUrl;
    }

    public static boolean hasConfiguredRoute(String localUrl, String publicUrl) {
        return !blank(localUrl) || !blank(publicUrl);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
