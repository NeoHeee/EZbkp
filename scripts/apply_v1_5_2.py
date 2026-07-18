from pathlib import Path


def replace_exact(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"Expected block not found in {path}: {old[:100]!r}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


replace_exact(
    "app/src/main/java/com/neo/ezaccounting/RouteCoordinator.java",
    """    public enum Trigger {
        STARTUP,
        NETWORK_CHANGE,""",
    """    public enum Trigger {
        STARTUP,
        FAST_START,
        BACKGROUND_STARTUP,
        NETWORK_CHANGE,""",
)

replace_exact(
    "app/src/main/java/com/neo/ezaccounting/RouteCoordinator.java",
    """    public boolean hasConfiguredRoute() {
        return !localUrl.isEmpty() || !publicUrl.isEmpty();
    }

    public RouteMode getMode() {""",
    """    public boolean hasConfiguredRoute() {
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

    public RouteMode getMode() {""",
)

replace_exact(
    "app/src/main/java/com/neo/ezaccounting/RouteCoordinator.java",
    """            RouteSwitchPolicy.Decision decision = switchPolicy.evaluate(activeType,
                    selection, snapshot.checkedAt, true);
            if (decision.shouldSwitch && decision.target != null) {
                activate(decision.target, snapshot, decision.reason, trigger);
            } else if (activeUrl != null && snapshot.activeReachable()) {
                host.onRouteStable(snapshot, decision.reason, trigger);
            } else {
                host.onRouteUnavailable(snapshot, decision.reason, trigger);
            }""",
    """            boolean allowPerformanceSwitch = trigger != Trigger.BACKGROUND_STARTUP;
            RouteSwitchPolicy.Decision decision = switchPolicy.evaluate(activeType,
                    selection, snapshot.checkedAt, allowPerformanceSwitch);
            if (decision.shouldSwitch && decision.target != null) {
                activate(decision.target, snapshot, decision.reason, trigger);
            } else if (activeUrl != null && snapshot.activeReachable()) {
                host.onRouteStable(snapshot, decision.reason, trigger);
            } else if (trigger == Trigger.BACKGROUND_STARTUP && activeUrl != null) {
                host.onRouteStable(snapshot, decision.reason, trigger);
            } else {
                host.onRouteUnavailable(snapshot, decision.reason, trigger);
            }""",
)

replace_exact(
    "app/src/main/java/com/neo/ezaccounting/RouteCoordinator.java",
    """        if (target == null || !target.reachable) {
            host.onRouteUnavailable(snapshot,
                    snapshot.mode == RouteMode.LOCAL ? "固定的本地线路不可用" :
                            "固定的公网线路不可用", trigger);
            return;
        }""",
    """        if (target == null || !target.reachable) {
            String reason = snapshot.mode == RouteMode.LOCAL ? "固定的本地线路不可用" :
                    "固定的公网线路不可用";
            if (trigger == Trigger.BACKGROUND_STARTUP && activeUrl != null) {
                host.onRouteStable(snapshot, reason + "，等待页面加载结果", trigger);
            } else {
                host.onRouteUnavailable(snapshot, reason, trigger);
            }
            return;
        }""",
)

replace_exact(
    "app/src/main/java/com/neo/ezaccounting/RouteCoordinator.java",
    """        if (current == Trigger.NETWORK_CHANGE && next == Trigger.PAGE_FAILURE) return next;
        return current;""",
    """        if ((current == Trigger.NETWORK_CHANGE || current == Trigger.BACKGROUND_STARTUP) &&
                next == Trigger.PAGE_FAILURE) return next;
        return current;""",
)

replace_exact(
    "app/src/main/java/com/neo/ezaccounting/MainActivity.java",
    """        } else if (routeCoordinator.hasConfiguredRoute()) {
            routeCoordinator.requestCheck(RouteCoordinator.Trigger.STARTUP);
        } else {
            showServerSettings();
        }

        getWindow().getDecorView().post(() -> {""",
    """        } else if (routeCoordinator.hasConfiguredRoute()) {
            if (routeCoordinator.activateFastStartRoute()) {
                scheduleBackgroundStartupProbe();
            } else {
                routeCoordinator.requestCheck(RouteCoordinator.Trigger.STARTUP);
            }
        } else {
            showServerSettings();
        }

        getWindow().getDecorView().post(() -> {""",
)

replace_exact(
    "app/src/main/java/com/neo/ezaccounting/MainActivity.java",
    """    private void handlePendingShortcutAction() {""",
    """    private void scheduleBackgroundStartupProbe() {
        getWindow().getDecorView().postDelayed(() -> {
            if (isFinishing() || isDestroyed() || serverSettingsVisible) return;
            routeCoordinator.requestCheck(RouteCoordinator.Trigger.BACKGROUND_STARTUP);
        }, 300L);
    }

    private void handlePendingShortcutAction() {""",
)

replace_exact(
    "app/src/main/java/com/neo/ezaccounting/MainActivity.java",
    """    public void onRouteCheckStarted(RouteCoordinator.Trigger trigger) {
        if (trigger == RouteCoordinator.Trigger.MANUAL_SPEED_TEST) {""",
    """    public void onRouteCheckStarted(RouteCoordinator.Trigger trigger) {
        if (trigger == RouteCoordinator.Trigger.BACKGROUND_STARTUP) return;
        if (trigger == RouteCoordinator.Trigger.MANUAL_SPEED_TEST) {""",
)

replace_exact(
    "app/src/main/java/com/neo/ezaccounting/MainActivity.java",
    """        if (changed && trigger != RouteCoordinator.Trigger.STARTUP) {""",
    """        if (changed && trigger != RouteCoordinator.Trigger.STARTUP &&
                trigger != RouteCoordinator.Trigger.FAST_START) {""",
)

replace_exact(
    "app/src/main/java/com/neo/ezaccounting/MainActivity.java",
    """    public void onRouteUnavailable(RouteCoordinator.Snapshot snapshot, String reason,
                                   RouteCoordinator.Trigger trigger) {
        if (serverSettingsVisible) return;
        showErrorPage(reason, lastFailure, snapshot);
    }""",
    """    public void onRouteUnavailable(RouteCoordinator.Snapshot snapshot, String reason,
                                   RouteCoordinator.Trigger trigger) {
        if (serverSettingsVisible) return;
        if (trigger == RouteCoordinator.Trigger.BACKGROUND_STARTUP &&
                webViewController.isCreated() &&
                stateMachine.getState() != AppStateMachine.State.ERROR) {
            return;
        }
        showErrorPage(reason, lastFailure, snapshot);
    }""",
)

replace_exact(
    "app/src/main/java/com/neo/ezaccounting/RouteManager.java",
    """        public String label() {
            if (!isConfigured()) return "未配置";
            if (reachable) return latencyMs + " ms";
            return "不可用（" + errorLabel(errorKind) + "）";
        }

        public String diagnostic() {
            if (!isConfigured()) return "未配置地址";
            if (reachable) {
                return "HTTP " + statusCode + "，响应 " + latencyMs + " ms";
            }""",
    """        public String label() {
            if (!isConfigured()) return "未配置";
            if (reachable && statusCode == 0) {
                return latencyMs > 0 ? "上次 " + latencyMs + " ms" : "待后台测速";
            }
            if (reachable) return latencyMs + " ms";
            return "不可用（" + errorLabel(errorKind) + "）";
        }

        public String diagnostic() {
            if (!isConfigured()) return "未配置地址";
            if (reachable && statusCode == 0) return "使用缓存线路，后台测速中";
            if (reachable) {
                return "HTTP " + statusCode + "，响应 " + latencyMs + " ms";
            }""",
)

replace_exact(
    "app/src/main/java/com/neo/ezaccounting/RouteManager.java",
    'connection.setRequestProperty("User-Agent", "EZAccounting/1.5.0");',
    'connection.setRequestProperty("User-Agent", "EZAccounting/1.5.2");',
)

replace_exact(
    "app/build.gradle",
    "versionCode 9\n        versionName '1.5.1'",
    "versionCode 10\n        versionName '1.5.2'",
)

android_ci = Path(".github/workflows/android.yml")
android_ci.write_text(android_ci.read_text(encoding="utf-8").replace("v1.5.1", "v1.5.2"),
                      encoding="utf-8")
signed = Path(".github/workflows/signed-release.yml")
signed.write_text(signed.read_text(encoding="utf-8").replace("v1.5.0", "v1.5.2"),
                  encoding="utf-8")

readme = Path("README.md")
text = readme.read_text(encoding="utf-8")
text = text.replace(
    "- 本地与公网线路并行测速\n",
    "- 有可复用线路时立即进入主页面，并在后台静默并行测速\n- 本地与公网线路并行测速\n",
    1,
)
section = """## v1.5.2 快速启动

- 自动模式优先复用上次成功线路，不再每次停留在检测页面
- 固定本地或固定公网模式直接进入对应线路
- 只配置一条线路时直接进入
- WebView开始加载后再进行后台静默测速
- 后台测速不覆盖当前页面，也不因轻微延迟差异立即切换
- 当前线路确实失败后，继续使用连续失败确认和备用线路回退
- 首次配置、地址变更或没有有效历史线路时，仍执行前台检测

"""
if section not in text:
    text = text.replace("## 线路模式\n", section + "## 线路模式\n", 1)
text = text.replace("- 当前版本：`1.5.0`", "- 当前版本：`1.5.2`")
readme.write_text(text, encoding="utf-8")
