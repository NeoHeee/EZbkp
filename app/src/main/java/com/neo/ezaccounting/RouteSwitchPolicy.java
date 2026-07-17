package com.neo.ezaccounting;

public final class RouteSwitchPolicy {
    static final int FAILURE_THRESHOLD = 2;
    static final long SWITCH_COOLDOWN_MS = 60_000L;
    static final long MIN_LATENCY_GAIN_MS = 150L;
    static final double REQUIRED_LATENCY_RATIO = 0.70d;

    public static final class Decision {
        public final boolean shouldSwitch;
        public final RouteManager.ProbeResult target;
        public final String reason;

        private Decision(boolean shouldSwitch, RouteManager.ProbeResult target, String reason) {
            this.shouldSwitch = shouldSwitch;
            this.target = target;
            this.reason = reason;
        }

        static Decision stay(String reason) {
            return new Decision(false, null, reason);
        }

        static Decision switchTo(RouteManager.ProbeResult target, String reason) {
            return new Decision(true, target, reason);
        }
    }

    private int localFailures;
    private int publicFailures;
    private long lastSwitchAt;

    public void recordFailure(int routeType) {
        if (routeType == RouteManager.TYPE_LOCAL) localFailures++;
        else if (routeType == RouteManager.TYPE_PUBLIC) publicFailures++;
    }

    public void recordSuccess(int routeType) {
        if (routeType == RouteManager.TYPE_LOCAL) localFailures = 0;
        else if (routeType == RouteManager.TYPE_PUBLIC) publicFailures = 0;
    }

    public int failureCount(int routeType) {
        if (routeType == RouteManager.TYPE_LOCAL) return localFailures;
        if (routeType == RouteManager.TYPE_PUBLIC) return publicFailures;
        return 0;
    }

    public void recordSwitch(long now) {
        lastSwitchAt = now;
        localFailures = 0;
        publicFailures = 0;
    }

    public Decision evaluate(int activeType, RouteManager.Selection selection, long now,
                             boolean allowPerformanceSwitch) {
        if (selection == null || !selection.hasRoute()) return Decision.stay("没有可用线路");
        RouteManager.ProbeResult recommended = selection.selected;
        if (activeType == RouteManager.TYPE_NONE) {
            return Decision.switchTo(recommended, "首次选择可用线路");
        }
        if (recommended.type == activeType) {
            RouteManager.ProbeResult current = resultFor(activeType, selection);
            if (current != null && current.reachable) recordSuccess(activeType);
            return Decision.stay("当前线路仍为最佳选择");
        }

        RouteManager.ProbeResult current = resultFor(activeType, selection);
        RouteManager.ProbeResult candidate = recommended;
        int failures = failureCount(activeType);

        if (current == null || !current.reachable) {
            recordFailure(activeType);
            failures = failureCount(activeType);
            if (failures < FAILURE_THRESHOLD) {
                return Decision.stay("当前线路首次探测失败，等待再次确认");
            }
            return Decision.switchTo(candidate, "当前线路连续失败" + failures + "次");
        }

        if (failures >= FAILURE_THRESHOLD) {
            return Decision.switchTo(candidate, "当前页面连续加载失败" + failures + "次");
        }

        recordSuccess(activeType);
        if (!allowPerformanceSwitch) return Decision.stay("仅测速，不自动切换");
        if (lastSwitchAt > 0L && now - lastSwitchAt < SWITCH_COOLDOWN_MS) {
            return Decision.stay("处于线路切换冷却期");
        }

        long gain = current.latencyMs - candidate.latencyMs;
        boolean clearlyFaster = current.latencyMs > 0L && candidate.latencyMs > 0L &&
                gain >= MIN_LATENCY_GAIN_MS &&
                candidate.latencyMs <= Math.round(current.latencyMs * REQUIRED_LATENCY_RATIO);
        if (clearlyFaster) {
            return Decision.switchTo(candidate, "候选线路延迟明显更低");
        }
        return Decision.stay("延迟差异不足以触发切换");
    }

    private RouteManager.ProbeResult resultFor(int type, RouteManager.Selection selection) {
        if (type == RouteManager.TYPE_LOCAL) return selection.local;
        if (type == RouteManager.TYPE_PUBLIC) return selection.publicRoute;
        return null;
    }
}
