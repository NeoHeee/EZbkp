package com.neo.ezaccounting;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

public final class RouteCoordinator {
    public static final String KEY_LAST_ROUTE = "last_route";
    public static final String KEY_ROUTE_MODE = "route_mode";
    public static final String KEY_LOCAL_LATENCY = "local_latency_ms";
    public static final String KEY_PUBLIC_LATENCY = "public_latency_ms";

    private static final long NETWORK_CHECK_DEBOUNCE_MS = 1400L;

    public enum Trigger {
        STARTUP,
        FAST_START,
        BACKGROUND_STARTUP,
        NETWORK_CHANGE,
        PAGE_FAILURE,
        RETRY,
        MANUAL_SPEED_TEST,
        MANUAL_MODE_CHANGE
    }

    public interface Host {
        void onRouteCheckStarted(Trigger trigger);
        void onRouteSnapshot(Snapshot snapshot, Trigger trigger);
        void onRouteActivated(RouteManager.ProbeResult target, Snapshot snapshot,
                              boolean changed, String reason, Trigger trigger);
        void onRouteStable(Snapshot snapshot, String reason, Trigger trigger);
        void onRouteUnavailable(Snapshot snapshot, String reason, Trigger trigger);
    }

    public static final class Snapshot {
        public final RouteManager.Selection selection;
        public final RouteMode mode;
        public final String activeUrl;
        public final int activeType;
        public final long checkedAt;

        Snapshot(RouteManager.Selection selection, RouteMode mode, String activeUrl,
                 int activeType, long checkedAt) {
            this.selection = selection;
            this.mode = mode;
            this.activeUrl = activeUrl;
            this.activeType = activeType;
            this.checkedAt = checkedAt;
        }

        public RouteManager.ProbeResult local() {
            return selection == null ? null : selection.local;
        }

        public RouteManager.ProbeResult publicRoute() {
            return selection == null ? null : selection.publicRoute;
        }

        public boolean activeReachable() {
            if (selection == null) return false;
            RouteManager.ProbeResult active = selection.resultFor(activeType);
            return active != null && active.reachable;
        }
    }

    private final SharedPreferences preferences;
    private final Host host;
    private final RouteManager routeManager;
    private final RouteSwitchPolicy switchPolicy;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Runnable networkCheck = () -> performCheck(Trigger.NETWORK_CHANGE);

    private String localUrl = "";
    private String publicUrl = "";
    private String activeUrl;
    private int activeType = RouteManager.TYPE_NONE;
    private boolean checkInProgress;
    private Trigger pendingTrigger;
    private Snapshot lastSnapshot;
    private String lastWebVerifiedUrl;

    public RouteCoordinator(SharedPreferences preferences, Host host) {
        this(preferences, host, new RouteManager(), new RouteSwitchPolicy());
    }

    RouteCoordinator(SharedPreferences preferences, Host host, RouteManager routeManager,
                     RouteSwitchPolicy switchPolicy) {
        this.preferences = preferences;
        this.host = host;
        this.routeManager = routeManager;
        this.switchPolicy = switchPolicy;
    }

    public void setAddresses(String localUrl, String publicUrl) {
        this.localUrl = safe(localUrl);
        this.publicUrl = safe(publicUrl);
        if (lastWebVerifiedUrl != null &&
                !lastWebVerifiedUrl.equals(this.localUrl) &&
                !lastWebVerifiedUrl.equals(this.publicUrl)) {
            lastWebVerifiedUrl = null;
        }
    }

    public boolean hasConfiguredRoute() {
        return !localUrl.isEmpty() || !publicUrl.isEmpty();
    }

    public boolean activateFastStartRoute() {
        FastStartPolicy.Candidate candidate = FastStartPolicy.select(getMode(), localUrl,
                publicUrl, preferences.getString(KEY_LAST_ROUTE, ""));
        if (candidate == null) return false;

        long latency = candidate.type == RouteManager.TYPE_LOCAL ?
                preferences.getLong(KEY_LOCAL_LATENCY, -1L) :
                preferences.getLong(KEY_PUBLIC_LATENCY, -1L);
        RouteManager.ProbeResult target = new RouteManager.ProbeResult(candidate.url,
                candidate.type, true, latency, 0, RouteManager.ErrorKind.NONE,
                "使用缓存线路，后台测速中");
        RouteManager.Selection selection = new RouteManager.Selection(target,
                candidate.type == RouteManager.TYPE_LOCAL ? target : null,
                candidate.type == RouteManager.TYPE_PUBLIC ? target : null);
        Snapshot snapshot = new Snapshot(selection, getMode(), candidate.url, candidate.type,
                System.currentTimeMillis());
        activeUrl = candidate.url;
        activeType = candidate.type;
        lastSnapshot = snapshot;
        preferences.edit().putString(KEY_LAST_ROUTE, activeUrl).apply();
        switchPolicy.recordSuccess(activeType);
        host.onRouteActivated(target, snapshot, true, candidate.reason, Trigger.FAST_START);
        return true;
    }

    public RouteMode getMode() {
        return RouteMode.fromStored(preferences.getString(KEY_ROUTE_MODE, RouteMode.AUTO.name()));
    }

    public void setMode(RouteMode mode) {
        RouteMode safeMode = mode == null ? RouteMode.AUTO : mode;
        preferences.edit().putString(KEY_ROUTE_MODE, safeMode.name()).apply();
        requestCheck(Trigger.MANUAL_MODE_CHANGE);
    }

    public String getActiveUrl() {
        return activeUrl;
    }

    public int getActiveType() {
        return activeType;
    }

    public Snapshot getLastSnapshot() {
        return lastSnapshot;
    }

    public void requestCheck(Trigger trigger) {
        Trigger safeTrigger = trigger == null ? Trigger.RETRY : trigger;
        if (safeTrigger == Trigger.NETWORK_CHANGE) {
            mainHandler.removeCallbacks(networkCheck);
            mainHandler.postDelayed(networkCheck, NETWORK_CHECK_DEBOUNCE_MS);
            return;
        }
        mainHandler.removeCallbacks(networkCheck);
        performCheck(safeTrigger);
    }

    public void manualSpeedTest() {
        requestCheck(Trigger.MANUAL_SPEED_TEST);
    }

    public void markPageSuccess() {
        switchPolicy.recordSuccess(activeType);
        if (activeUrl == null || activeType == RouteManager.TYPE_NONE) return;
        lastWebVerifiedUrl = activeUrl;
        preferences.edit().putString(KEY_LAST_ROUTE, activeUrl).apply();
        promoteActiveSnapshotToWebVerified();
    }

    public void markPageFailure() {
        switchPolicy.recordFailure(activeType);
        if (activeUrl != null && activeUrl.equals(lastWebVerifiedUrl)) {
            lastWebVerifiedUrl = null;
        }
        requestCheck(Trigger.PAGE_FAILURE);
    }

    private void performCheck(Trigger trigger) {
        if (checkInProgress) {
            pendingTrigger = chooseHigherPriority(pendingTrigger, trigger);
            return;
        }
        if (!hasConfiguredRoute()) {
            Snapshot empty = new Snapshot(null, getMode(), activeUrl, activeType,
                    System.currentTimeMillis());
            lastSnapshot = empty;
            host.onRouteUnavailable(empty, "尚未配置服务器地址", trigger);
            return;
        }

        checkInProgress = true;
        host.onRouteCheckStarted(trigger);
        routeManager.probeAllAsync(localUrl, publicUrl, raw -> {
            checkInProgress = false;
            RouteMode mode = getMode();
            String lastRoute = preferences.getString(KEY_LAST_ROUTE, "");

            RouteManager.ProbeResult local = preserveWebVerification(raw.local);
            RouteManager.ProbeResult remote = preserveWebVerification(raw.publicRoute);
            boolean allowWebFallback = trigger != Trigger.MANUAL_SPEED_TEST &&
                    trigger != Trigger.PAGE_FAILURE;
            RouteManager.ProbeResult selected = RouteManager.selectForModeWithWebFallback(
                    mode, local, remote, lastRoute, allowWebFallback);

            if (selected != null && selected.verificationPending) {
                if (selected.type == RouteManager.TYPE_LOCAL) local = selected;
                if (selected.type == RouteManager.TYPE_PUBLIC) remote = selected;
            }

            RouteManager.Selection selection = new RouteManager.Selection(selected, local, remote);
            Snapshot snapshot = new Snapshot(selection, mode, activeUrl, activeType,
                    System.currentTimeMillis());
            lastSnapshot = snapshot;
            saveLatencies(snapshot);
            host.onRouteSnapshot(snapshot, trigger);

            if (trigger == Trigger.MANUAL_SPEED_TEST) {
                host.onRouteStable(snapshot, "测速完成；网页实际访问结果优先于独立探测", trigger);
                drainPending();
                return;
            }

            if (mode != RouteMode.AUTO) {
                handleForcedMode(snapshot, trigger);
                drainPending();
                return;
            }

            boolean allowPerformanceSwitch = trigger != Trigger.BACKGROUND_STARTUP;
            RouteSwitchPolicy.Decision decision = switchPolicy.evaluate(activeType,
                    selection, snapshot.checkedAt, allowPerformanceSwitch);
            if (decision.shouldSwitch && decision.target != null) {
                activate(decision.target, snapshot, decision.reason, trigger);
            } else if (activeUrl != null && snapshot.activeReachable()) {
                host.onRouteStable(snapshot, decision.reason, trigger);
            } else if (trigger == Trigger.BACKGROUND_STARTUP && activeUrl != null) {
                host.onRouteStable(snapshot,
                        "后台独立探测未通过，继续等待 WebView 实际访问结果", trigger);
            } else {
                host.onRouteUnavailable(snapshot, decision.reason, trigger);
            }
            drainPending();
        });
    }

    private RouteManager.ProbeResult preserveWebVerification(
            RouteManager.ProbeResult result) {
        if (result == null || result.reachable || lastWebVerifiedUrl == null ||
                result.url == null || !result.url.equals(lastWebVerifiedUrl)) {
            return result;
        }
        return result.asWebVerified(lastWebVerifiedUrl);
    }

    private void promoteActiveSnapshotToWebVerified() {
        RouteManager.ProbeResult local = lastSnapshot == null ? null : lastSnapshot.local();
        RouteManager.ProbeResult remote = lastSnapshot == null ? null : lastSnapshot.publicRoute();
        RouteManager.ProbeResult active = lastSnapshot == null || lastSnapshot.selection == null ?
                null : lastSnapshot.selection.resultFor(activeType);

        if (active == null) {
            active = new RouteManager.ProbeResult(activeUrl, activeType, true, -1L, 0,
                    RouteManager.ErrorKind.NONE, "WebView 已实际加载成功");
        } else {
            active = active.asWebVerified(activeUrl);
        }

        if (activeType == RouteManager.TYPE_LOCAL) local = active;
        if (activeType == RouteManager.TYPE_PUBLIC) remote = active;
        RouteManager.Selection selection = new RouteManager.Selection(active, local, remote);
        lastSnapshot = new Snapshot(selection, getMode(), activeUrl, activeType,
                System.currentTimeMillis());
    }

    private void handleForcedMode(Snapshot snapshot, Trigger trigger) {
        RouteManager.ProbeResult target = snapshot.selection == null ? null :
                snapshot.selection.selected;
        if (target == null || !target.reachable) {
            String reason = snapshot.mode == RouteMode.LOCAL ? "固定的本地线路不可用" :
                    "固定的公网线路独立探测未通过";
            if (trigger == Trigger.BACKGROUND_STARTUP && activeUrl != null) {
                host.onRouteStable(snapshot, reason + "，等待页面实际加载结果", trigger);
            } else {
                host.onRouteUnavailable(snapshot, reason, trigger);
            }
            return;
        }
        String reason = target.verificationPending ?
                "独立探测未通过，尝试由 WebView 实际验证" : "按手动线路模式切换";
        activate(target, snapshot, reason, trigger);
    }

    private void activate(RouteManager.ProbeResult target, Snapshot snapshot, String reason,
                          Trigger trigger) {
        boolean changed = activeUrl == null || !activeUrl.equals(target.url);
        activeUrl = target.url;
        activeType = target.type;
        preferences.edit().putString(KEY_LAST_ROUTE, activeUrl).apply();
        if (changed) switchPolicy.recordSwitch(snapshot.checkedAt);
        if (!target.verificationPending) switchPolicy.recordSuccess(activeType);
        Snapshot activated = new Snapshot(snapshot.selection, snapshot.mode, activeUrl,
                activeType, snapshot.checkedAt);
        lastSnapshot = activated;
        host.onRouteActivated(target, activated, changed, reason, trigger);
    }

    private void saveLatencies(Snapshot snapshot) {
        RouteManager.ProbeResult local = snapshot.local();
        RouteManager.ProbeResult remote = snapshot.publicRoute();
        SharedPreferences.Editor editor = preferences.edit();
        if (local != null && local.reachable && !local.verificationPending &&
                !local.webVerified && local.latencyMs > 0) {
            editor.putLong(KEY_LOCAL_LATENCY, local.latencyMs);
        }
        if (remote != null && remote.reachable && !remote.verificationPending &&
                !remote.webVerified && remote.latencyMs > 0) {
            editor.putLong(KEY_PUBLIC_LATENCY, remote.latencyMs);
        }
        editor.apply();
    }

    private Trigger chooseHigherPriority(Trigger current, Trigger next) {
        if (current == null) return next;
        if (next == Trigger.MANUAL_MODE_CHANGE || next == Trigger.RETRY ||
                next == Trigger.MANUAL_SPEED_TEST) return next;
        if ((current == Trigger.NETWORK_CHANGE || current == Trigger.BACKGROUND_STARTUP) &&
                next == Trigger.PAGE_FAILURE) return next;
        return current;
    }

    private void drainPending() {
        if (pendingTrigger == null) return;
        Trigger next = pendingTrigger;
        pendingTrigger = null;
        mainHandler.post(() -> performCheck(next));
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public void shutdown() {
        mainHandler.removeCallbacksAndMessages(null);
        routeManager.shutdown();
    }
}
