from pathlib import Path
import re


def replace_exact(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"Expected block not found in {path}: {old[:120]!r}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


main = Path("app/src/main/java/com/neo/ezaccounting/MainActivity.java")
text = main.read_text(encoding="utf-8")
pattern = re.compile(
    r"    private void openQuickActions\(\) \{.*?\n    \}\n\n    private void showRouteStatusDialog\(\)",
    re.S,
)
replacement = r'''    private void openQuickActions() {
        QuickActionsSheet.show(this, quickActionsModel(), new QuickActionsSheet.Listener() {
            @Override
            public void onHome() {
                if (routeCoordinator.getActiveUrl() != null) {
                    webViewController.loadUrl(routeCoordinator.getActiveUrl());
                }
            }

            @Override
            public void onRouteStatus() {
                showRouteStatusDialog();
            }

            @Override
            public void onManualRoute() {
                showManualRouteDialog();
            }

            @Override
            public void onSpeedTest() {
                routeCoordinator.manualSpeedTest();
            }

            @Override
            public void onOpenBrowser() {
                MainActivity.this.onOpenBrowser();
            }

            @Override
            public void onEditAddresses() {
                showServerSettings();
            }

            @Override
            public void onLock() {
                lockImmediately();
            }

            @Override
            public void onSecuritySettings() {
                requestSecuritySettings();
            }

            @Override
            public void onCheckUpdate() {
                checkForUpdates(true);
            }

            @Override
            public void onWebViewInfo() {
                showWebViewInfo();
            }

            @Override
            public void onClearSiteData() {
                confirmClearSiteData();
            }
        });
    }

    private QuickActionsSheet.Model quickActionsModel() {
        String latency = "待测速";
        RouteCoordinator.Snapshot snapshot = routeCoordinator.getLastSnapshot();
        if (snapshot != null && snapshot.selection != null) {
            RouteManager.ProbeResult active = snapshot.selection.resultFor(
                    routeCoordinator.getActiveType());
            if (active != null && active.reachable && active.latencyMs > 0) {
                latency = active.latencyMs + " ms";
            }
        }
        String security = AppSecurity.isEnabled(this) ?
                AppSecurity.getModeLabel(this) : "未开启保护";
        return new QuickActionsSheet.Model(routeCoordinator.getMode().label(),
                routeName(routeCoordinator.getActiveType()), latency, security);
    }

    private void showRouteStatusDialog()'''
text, count = pattern.subn(replacement, text, count=1)
if count != 1:
    raise RuntimeError("Unable to replace openQuickActions in MainActivity.java")
main.write_text(text, encoding="utf-8")

replace_exact(
    "app/build.gradle",
    "versionCode 10\n        versionName '1.5.2'",
    "versionCode 11\n        versionName '1.5.3'",
)

replace_exact(
    "app/src/main/java/com/neo/ezaccounting/RouteManager.java",
    'connection.setRequestProperty("User-Agent", "EZAccounting/1.5.2");',
    'connection.setRequestProperty("User-Agent", "EZAccounting/1.5.3");',
)

readme = Path("README.md")
readme_text = readme.read_text(encoding="utf-8")
section = """## v1.5.3 快捷面板 UI

- 隐藏菜单改为底部弹出式快捷面板
- 顶部显示线路模式、当前线路、最近延迟和安全验证状态
- 功能按“线路与访问”“安全与控制”“页面与维护”分组
- 每项增加图标、主标题、副说明和触摸反馈
- 清除登录与缓存使用红色危险操作样式
- 支持深色模式、圆角卡片、状态标签和底部关闭按钮

"""
if section not in readme_text:
    readme_text = readme_text.replace("## v1.5.2 快速启动\n", section + "## v1.5.2 快速启动\n", 1)
readme_text = readme_text.replace("- 当前版本：`1.5.2`", "- 当前版本：`1.5.3`")
readme.write_text(readme_text, encoding="utf-8")
