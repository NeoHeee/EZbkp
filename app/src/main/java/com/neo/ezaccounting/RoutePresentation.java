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

    public static String routeStatusText(int activeType,
                                         RouteCoordinator.Snapshot snapshot) {
        StringBuilder text = new StringBuilder();
        text.append("选择方式：自动管理\n");
        text.append("当前：").append(routeName(activeType)).append('\n');
        if (snapshot == null) {
            text.append("\n尚未完成测速");
            return text.toString();
        }
        text.append("\n本地：").append(probeLabel(snapshot.local()));
        text.append("\n公网：").append(probeLabel(snapshot.publicRoute()));
        text.append("\n\n自动规则：综合当前网络匹配、延迟和近期稳定性选择线路；网络变化后自动重新评估。");
        return text.toString();
    }

    private static String probeLabel(RouteManager.ProbeResult result) {
        return result == null ? "未检测" : result.label() + " · " + result.diagnostic();
    }
}
