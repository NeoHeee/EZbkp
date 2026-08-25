package com.neo.ezaccounting;

public final class RoutePresentation {
    private RoutePresentation() {}

    public static String routeName(int type) {
        if (type == RouteManager.TYPE_LOCAL) return "本地线路";
        if (type == RouteManager.TYPE_PUBLIC) return "公网线路";
        return "尚未连接";
    }

    public static QuickActionsSheet.Model quickActionsModel(int activeType,
                                                             RouteCoordinator.Snapshot snapshot,
                                                             String securityLabel) {
        String latency = "待测速";
        if (snapshot != null && snapshot.selection != null) {
            RouteManager.ProbeResult active = snapshot.selection.resultFor(activeType);
            if (active != null && active.reachable && active.latencyMs > 0) {
                latency = active.latencyMs + " ms";
            }
        }
        return new QuickActionsSheet.Model(routeName(activeType), latency, securityLabel);
    }

}
