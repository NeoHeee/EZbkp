package com.neo.ezaccounting;

import java.net.URI;
import java.util.Locale;

public final class BackNavigationPolicy {
    public static final long EXIT_CONFIRM_WINDOW_MS = 1800L;

    public enum Action {
        WEB_BACK,
        RESTORE_SETTINGS,
        RECOVER_ERROR,
        GO_HOME,
        SHOW_EXIT_HINT,
        EXIT
    }

    private BackNavigationPolicy() {}

    public static Action decide(AppStateMachine.State state,
                                boolean canGoBack,
                                boolean hasConfiguredRoute,
                                boolean atHome,
                                long now,
                                long lastBackPressedAt) {
        AppStateMachine.State safeState = state == null ?
                AppStateMachine.State.INITIALIZING : state;

        if (safeState == AppStateMachine.State.SETTINGS && hasConfiguredRoute) {
            return Action.RESTORE_SETTINGS;
        }
        if (safeState == AppStateMachine.State.ERROR && hasConfiguredRoute) {
            return Action.RECOVER_ERROR;
        }
        if (safeState == AppStateMachine.State.READY ||
                safeState == AppStateMachine.State.LOADING_WEB) {
            if (canGoBack) return Action.WEB_BACK;
            if (!atHome) return Action.GO_HOME;
        }
        return now - lastBackPressedAt <= EXIT_CONFIRM_WINDOW_MS ?
                Action.EXIT : Action.SHOW_EXIT_HINT;
    }

    public static boolean isAtHome(String baseUrl, String currentUrl) {
        if (blank(baseUrl) || blank(currentUrl)) return true;
        try {
            URI base = new URI(baseUrl.trim());
            URI current = new URI(currentUrl.trim());
            if (!sameOrigin(base, current)) return false;
            if (!normalizePath(base.getPath()).equals(normalizePath(current.getPath()))) {
                return false;
            }
            if (!blank(current.getQuery())) return false;

            String baseFragment = normalizeFragment(base.getFragment());
            String currentFragment = normalizeFragment(current.getFragment());
            if (!baseFragment.isEmpty()) return baseFragment.equals(currentFragment);
            return currentFragment.isEmpty() || "/".equals(currentFragment);
        } catch (Exception ignored) {
            return baseUrl.trim().equalsIgnoreCase(currentUrl.trim());
        }
    }

    private static boolean sameOrigin(URI first, URI second) {
        return normalize(first.getScheme()).equals(normalize(second.getScheme())) &&
                normalize(first.getHost()).equals(normalize(second.getHost())) &&
                effectivePort(first) == effectivePort(second);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        String scheme = normalize(uri.getScheme());
        if ("https".equals(scheme)) return 443;
        if ("http".equals(scheme)) return 80;
        return -1;
    }

    private static String normalizePath(String value) {
        if (blank(value) || "/".equals(value)) return "/";
        String path = value.trim();
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static String normalizeFragment(String value) {
        if (blank(value)) return "";
        String fragment = value.trim();
        while (fragment.startsWith("#")) fragment = fragment.substring(1);
        return fragment;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
