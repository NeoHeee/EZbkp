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
    }

    public boolean hasConfiguredRoute() {
        return !localUrl.isEmpty() || !publicUrl.isEmpty();
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
    }

    public void markPageFailure() {
        switchPolicy.recordFailure(activeType);
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
            RouteManager.ProbeResult selected = RouteManager.selectForMode(mode,
                    raw.local, raw.publicRoute, lastRoute);
            RouteManager.Selection selection = new RouteManager.Selection(selected,
                    raw.local, raw.publicRoute);
            Snapshot snapshot = new Snapshot(selection, mode, activeUrl, activeType,
                    System.currentTimeMillis());
            lastSnapshot = snapshot;
            saveLatencies(snapshot);
            host.onRouteSnapshot(snapshot, trigger);

            if (trigger == Trigger.MANUAL_SPEED_TEST) {
                host.onRouteStable(snapshot, "测速完成", trigger);
                drainPending();
                return;
            }

            if (mode != RouteMode.AUTO) {
                handleForcedMode(snapshot, trigger);
                drainPending();
                return;
            }

            RouteSwitchPolicy.Decision decision = switchPolicy.evaluate(activeType,
                    selection, snapshot.checkedAt, true);
            if (decision.shouldSwitch && decision.target != null) {
                activate(decision.target, snapshot, decision.reason, trigger);
            } else if (activeUrl != null && snapshot.activeReachable()) {
                host.onRouteStable(snapshot, decision.reason, trigger);
            } else {
                host.onRouteUnavailable(snapshot, decision.reason, trigger);
            }
            drainPending();
        });
    }

    private void handleForcedMode(Snapshot snapshot, Trigger trigger) {
        RouteManager.ProbeResult target = snapshot.selection == null ? null :
                snapshot.selection.selected;
        if (target == null || !target.reachable) {
            host.onRouteUnavailable(snapshot,
                    snapshot.mode == RouteMode.LOCAL ? "固定的本地线路不可用" :
                            "固定的公网线路不可用", trigger);
            return;
        }
        activate(target, snapshot, "按手动线路模式切换", trigger);
    }

    private void activate(RouteManager.ProbeResult target, Snapshot snapshot, String reason,
                          Trigger trigger) {
        boolean changed = activeUrl == null || !activeUrl.equals(target.url);
        activeUrl = target.url;
        activeType = target.type;
        preferences.edit().putString(KEY_LAST_ROUTE, activeUrl).apply();
        if (changed) switchPolicy.recordSwitch(snapshot.checkedAt);
        switchPolicy.recordSuccess(activeType);
        Snapshot activated = new Snapshot(snapshot.selection, snapshot.mode, activeUrl,
                activeType, snapshot.checkedAt);
        lastSnapshot = activated;
        host.onRouteActivated(target, activated, changed, reason, trigger);
    }

    private void saveLatencies(Snapshot snapshot) {
        RouteManager.ProbeResult local = snapshot.local();
        RouteManager.ProbeResult remote = snapshot.publicRoute();
        preferences.edit()
                .putLong(KEY_LOCAL_LATENCY, local == null ? -1L : local.latencyMs)
                .putLong(KEY_PUBLIC_LATENCY, remote == null ? -1L : remote.latencyMs)
                .apply();
    }

    private Trigger chooseHigherPriority(Trigger current, Trigger next) {
        if (current == null) return next;
        if (next == Trigger.MANUAL_MODE_CHANGE || next == Trigger.RETRY ||
                next == Trigger.MANUAL_SPEED_TEST) return next;
        if (current == Trigger.NETWORK_CHANGE && next == Trigger.PAGE_FAILURE) return next;
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
