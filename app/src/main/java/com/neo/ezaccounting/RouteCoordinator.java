package com.neo.ezaccounting;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

public final class RouteCoordinator {
    public static final String KEY_LAST_ROUTE = "last_route";
    public static final String KEY_LOCAL_LATENCY = "local_latency_ms";
    public static final String KEY_PUBLIC_LATENCY = "public_latency_ms";

    public enum Trigger {
        STARTUP,
        FAST_START,
        BACKGROUND_STARTUP,
        NETWORK_CHANGE,
        PAGE_FAILURE,
        RETRY,
        MANUAL_SPEED_TEST
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
        public final String activeUrl;
        public final int activeType;
        public final long checkedAt;

        Snapshot(RouteManager.Selection selection, String activeUrl, int activeType,
                 long checkedAt) {
            this.selection = selection;
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

    private String localUrl = "";
    private String publicUrl = "";
    private String activeUrl;
    private int activeType = RouteManager.TYPE_NONE;
    private boolean checkInProgress;
    private Trigger pendingTrigger;
    private Snapshot lastSnapshot;
    private String lastWebVerifiedUrl;
    private long routeGeneration;

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
        String previousLocalUrl = this.localUrl;
        String previousPublicUrl = this.publicUrl;
        this.localUrl = safe(localUrl);
        this.publicUrl = safe(publicUrl);
        if (!previousLocalUrl.equals(this.localUrl) ||
                !previousPublicUrl.equals(this.publicUrl)) routeGeneration++;
        if (!previousLocalUrl.equals(this.localUrl)) switchPolicy.onLocalCandidateChanged();
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
        FastStartPolicy.Candidate candidate = FastStartPolicy.select(localUrl, publicUrl);
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
        Snapshot snapshot = new Snapshot(selection, candidate.url, candidate.type,
                System.currentTimeMillis());
        activeUrl = candidate.url;
        activeType = candidate.type;
        lastSnapshot = snapshot;
        preferences.edit().putString(KEY_LAST_ROUTE, activeUrl).apply();
        switchPolicy.recordSuccess(activeType);
        host.onRouteActivated(target, snapshot, true, candidate.reason, Trigger.FAST_START);
        return true;
    }

    public String getActiveUrl() {
        return activeUrl;
    }

    public int getActiveType() {
        return activeType;
    }

    public void testLocalAddress(String url, RouteManager.ProbeCallback callback) {
        routeManager.probeOnlyAsync(url, RouteManager.TYPE_LOCAL, callback);
    }

    public Snapshot getLastSnapshot() {
        return lastSnapshot;
    }

    public void requestCheck(Trigger trigger) {
        Trigger safeTrigger = trigger == null ? Trigger.RETRY : trigger;
        if (safeTrigger == Trigger.NETWORK_CHANGE) {
            routeGeneration++;
        }
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
            Snapshot empty = new Snapshot(null, activeUrl, activeType,
                    System.currentTimeMillis());
            lastSnapshot = empty;
            host.onRouteUnavailable(empty, "尚未配置服务器地址", trigger);
            return;
        }

        checkInProgress = true;
        long generation = routeGeneration;
        String checkedLocalUrl = localUrl;
        String checkedPublicUrl = publicUrl;
        host.onRouteCheckStarted(trigger);
        routeManager.probeAllAsync(checkedLocalUrl, checkedPublicUrl, raw -> {
            checkInProgress = false;
            if (generation != routeGeneration ||
                    !checkedLocalUrl.equals(localUrl) ||
                    !checkedPublicUrl.equals(publicUrl)) {
                drainPending();
                return;
            }
            String lastRoute = preferences.getString(KEY_LAST_ROUTE, "");

            RouteManager.ProbeResult local = preserveWebVerification(raw.local);
            RouteManager.ProbeResult remote = preserveWebVerification(raw.publicRoute);
            boolean allowWebFallback = trigger != Trigger.MANUAL_SPEED_TEST &&
                    trigger != Trigger.PAGE_FAILURE;
            RouteManager.ProbeResult selected = RouteManager.selectWithWebFallback(
                    local, remote, lastRoute, allowWebFallback);

            if (selected != null && selected.verificationPending) {
                if (selected.type == RouteManager.TYPE_LOCAL) local = selected;
                if (selected.type == RouteManager.TYPE_PUBLIC) remote = selected;
            }

            RouteManager.Selection selection = new RouteManager.Selection(selected, local, remote);
            Snapshot snapshot = new Snapshot(selection, activeUrl, activeType,
                    System.currentTimeMillis());
            lastSnapshot = snapshot;
            saveLatencies(snapshot);
            host.onRouteSnapshot(snapshot, trigger);

            if (trigger == Trigger.MANUAL_SPEED_TEST) {
                host.onRouteStable(snapshot, "测速完成；网页实际访问结果优先于独立探测", trigger);
                drainPending();
                return;
            }

            boolean activeRouteEligible = activeType == RouteManager.TYPE_LOCAL ?
                    activeUrl != null && activeUrl.equals(localUrl) :
                    activeType == RouteManager.TYPE_PUBLIC ?
                            activeUrl != null && activeUrl.equals(publicUrl) : true;
            RouteSwitchPolicy.Decision decision = switchPolicy.evaluate(activeType,
                    selection, snapshot.checkedAt, activeRouteEligible);
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
        lastSnapshot = new Snapshot(selection, activeUrl, activeType,
                System.currentTimeMillis());
    }

    private void activate(RouteManager.ProbeResult target, Snapshot snapshot, String reason,
                          Trigger trigger) {
        boolean changed = activeUrl == null || !activeUrl.equals(target.url);
        int previousType = activeType;
        activeUrl = target.url;
        activeType = target.type;
        preferences.edit().putString(KEY_LAST_ROUTE, activeUrl).apply();
        if (changed) switchPolicy.recordSwitch(snapshot.checkedAt);
        if (changed && previousType == RouteManager.TYPE_LOCAL &&
                activeType == RouteManager.TYPE_PUBLIC && trigger != Trigger.NETWORK_CHANGE) {
            switchPolicy.blockLocalRetry(snapshot.checkedAt);
        }
        if (!target.verificationPending) switchPolicy.recordSuccess(activeType);
        Snapshot activated = new Snapshot(snapshot.selection, activeUrl,
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
        if (next == Trigger.RETRY || next == Trigger.MANUAL_SPEED_TEST) return next;
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
