package com.neo.ezaccounting;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class RouteStatusDialogPage {
    private RouteStatusDialogPage() {}

    public static void show(Activity activity, String title, int activeType,
                            RouteCoordinator.Snapshot snapshot, Runnable onSpeedTest) {
        if (activity == null || activity.isFinishing()) return;
        final AlertDialog[] holder = new AlertDialog[1];
        View content = create(activity, title, activeType, snapshot,
                () -> {
                    if (holder[0] != null) holder[0].dismiss();
                }, () -> {
                    if (holder[0] != null) holder[0].dismiss();
                    if (onSpeedTest != null) onSpeedTest.run();
                });
        AlertDialog dialog = new AlertDialog.Builder(activity).setView(content).create();
        holder[0] = dialog;
        UiComponents.show(dialog);
    }

    static View create(Activity activity, String title, int activeType,
                       RouteCoordinator.Snapshot snapshot,
                       Runnable onClose, Runnable onSpeedTest) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(UiTheme.surface(activity));

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 20), dp(activity, 18),
                dp(activity, 20), dp(activity, 18));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text(activity, title == null ? "线路状态" : title,
                23, UiTheme.primaryText(activity), true);
        header.addView(heading, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = text(activity, "×", 28, UiTheme.secondaryText(activity), false);
        UiComponents.styleIconButton(close);
        close.setContentDescription("关闭线路状态");
        close.setOnClickListener(view -> onClose.run());
        header.addView(close, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));
        root.addView(header, fullWrap(activity, 8));

        TextView description = text(activity,
                "自动管理会优先保持当前可用线路，仅在网络变化或连接失败时重新评估。",
                13.5f, UiTheme.secondaryText(activity), false);
        description.setLineSpacing(0, 1.16f);
        root.addView(description, fullWrap(activity, 16));

        LinearLayout overview = card(activity);
        overview.addView(summaryRow(activity, "当前线路",
                RoutePresentation.routeName(activeType), true));
        addDivider(activity, overview);
        overview.addView(summaryRow(activity, "当前网络",
                snapshot == null ? "未识别" : snapshot.networkLabel, false));
        addDivider(activity, overview);
        overview.addView(summaryRow(activity, "选线原因",
                snapshot == null ? "尚未完成线路评估" : snapshot.decisionReason, false));
        root.addView(overview, fullWrap(activity, 18));

        root.addView(sectionTitle(activity, "线路诊断"), fullWrap(activity, 8));
        root.addView(routeCard(activity, "局域网线路",
                snapshot == null ? null : snapshot.local()), fullWrap(activity, 10));
        root.addView(routeCard(activity, "公网线路",
                snapshot == null ? null : snapshot.publicRoute()), fullWrap(activity, 18));

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button dismiss = new Button(activity);
        dismiss.setText("关闭");
        UiComponents.styleGoldAction(dismiss);
        dismiss.setOnClickListener(view -> onClose.run());
        Button speed = new Button(activity);
        speed.setText("手动测速");
        UiComponents.stylePrimary(speed);
        speed.setOnClickListener(view -> onSpeedTest.run());
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        left.rightMargin = dp(activity, 6);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        right.leftMargin = dp(activity, 6);
        buttons.addView(dismiss, left);
        buttons.addView(speed, right);
        root.addView(buttons, fullWrap(activity, 0));
        return scroll;
    }

    private static View summaryRow(Activity activity, String label, String value,
                                   boolean emphasize) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 16), dp(activity, 13),
                dp(activity, 16), dp(activity, 13));
        TextView name = text(activity, label, 14, UiTheme.secondaryText(activity), false);
        row.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.38f));
        TextView detail = text(activity, safe(value, "未检测"), emphasize ? 15.5f : 14,
                emphasize ? UiTheme.accentDark(activity) : UiTheme.primaryText(activity),
                emphasize);
        detail.setGravity(Gravity.END);
        detail.setLineSpacing(0, 1.12f);
        row.addView(detail, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.62f));
        return row;
    }

    private static View routeCard(Activity activity, String title,
                                  RouteManager.ProbeResult result) {
        LinearLayout card = card(activity);
        card.setPadding(dp(activity, 16), dp(activity, 14),
                dp(activity, 16), dp(activity, 14));

        LinearLayout top = new LinearLayout(activity);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(activity, title, 15, UiTheme.primaryText(activity), true);
        top.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        String status = result == null ? "未检测" : result.label();
        TextView badge = text(activity, status, 12.5f,
                result != null && result.reachable ? UiTheme.accentDark(activity) :
                        UiTheme.secondaryText(activity), true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(activity, 10), dp(activity, 5),
                dp(activity, 10), dp(activity, 5));
        badge.setBackground(badgeBackground(activity,
                result != null && result.reachable));
        top.addView(badge);
        card.addView(top, fullWrap(activity, 10));

        if (result == null || !result.isConfigured()) {
            TextView empty = text(activity,
                    result == null ? "尚未进行线路探测" : "未配置地址",
                    13, UiTheme.secondaryText(activity), false);
            card.addView(empty, fullWrap(activity, 0));
            return card;
        }

        String message = result.webVerified ? "WebView 已实际加载成功" :
                result.verificationPending ? "独立探测未通过，等待网页实际验证" :
                        result.reachable ? "服务器连接正常" :
                                safe(result.errorMessage, "线路暂时不可用");
        TextView outcome = text(activity, message, 13,
                result.reachable ? UiTheme.accentDark(activity) :
                        UiTheme.secondaryText(activity), false);
        outcome.setLineSpacing(0, 1.15f);
        card.addView(outcome, fullWrap(activity, 12));

        LinearLayout resultRows = new LinearLayout(activity);
        resultRows.setOrientation(LinearLayout.VERTICAL);
        resultRows.setBackground(detailBackground(activity));
        resultRows.addView(detailRow(activity, "HTTP 状态",
                result.statusCode > 0 ? String.valueOf(result.statusCode) : "未返回"));
        addCompactDivider(activity, resultRows);
        resultRows.addView(detailRow(activity, "总耗时",
                result.latencyMs > 0 ? result.latencyMs + " ms" : "未记录"));
        card.addView(resultRows, fullWrap(activity, 12));

        TextView phaseLabel = text(activity, "分阶段耗时", 12.5f,
                UiTheme.secondaryText(activity), true);
        card.addView(phaseLabel, fullWrap(activity, 7));
        LinearLayout phaseTop = new LinearLayout(activity);
        phaseTop.setOrientation(LinearLayout.HORIZONTAL);
        phaseTop.addView(metricCell(activity, "DNS", result.dnsMs), metricParams(activity, true));
        phaseTop.addView(metricCell(activity, "TCP", result.tcpMs), metricParams(activity, false));
        card.addView(phaseTop, fullWrap(activity, 6));
        LinearLayout phaseBottom = new LinearLayout(activity);
        phaseBottom.setOrientation(LinearLayout.HORIZONTAL);
        phaseBottom.addView(metricCell(activity, "TLS", result.tlsMs), metricParams(activity, true));
        phaseBottom.addView(metricCell(activity, "HTTP", result.httpMs), metricParams(activity, false));
        card.addView(phaseBottom, fullWrap(activity, 12));

        if (!result.resolvedAddresses.isEmpty()) {
            TextView addressLabel = text(activity, "解析地址", 12.5f,
                    UiTheme.secondaryText(activity), true);
            card.addView(addressLabel, fullWrap(activity, 6));
            TextView addresses = text(activity,
                    result.resolvedAddresses.replace(", ", "\n"), 11.5f,
                    UiTheme.secondaryText(activity), false);
            addresses.setTypeface(Typeface.MONOSPACE);
            addresses.setLineSpacing(dp(activity, 2), 1f);
            addresses.setTextIsSelectable(true);
            addresses.setPadding(dp(activity, 12), dp(activity, 10),
                    dp(activity, 12), dp(activity, 10));
            addresses.setBackground(detailBackground(activity));
            card.addView(addresses, fullWrap(activity, 10));
        }

        if (result.redirectCount > 0 ||
                (result.finalUrl != null && result.url != null &&
                        !result.finalUrl.equals(result.url))) {
            TextView redirect = text(activity,
                    "重定向 " + result.redirectCount + " 次" +
                            (result.finalUrl == null ? "" : "\n最终地址：" + result.finalUrl),
                    12, UiTheme.tertiaryText(activity), false);
            redirect.setTextIsSelectable(true);
            redirect.setLineSpacing(0, 1.12f);
            card.addView(redirect, fullWrap(activity, 0));
        }
        return card;
    }

    private static View detailRow(Activity activity, String label, String value) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 12), dp(activity, 9),
                dp(activity, 12), dp(activity, 9));
        TextView name = text(activity, label, 12.5f,
                UiTheme.secondaryText(activity), false);
        row.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView detail = text(activity, value, 13,
                UiTheme.primaryText(activity), true);
        detail.setGravity(Gravity.END);
        row.addView(detail);
        return row;
    }

    private static View metricCell(Activity activity, String label, long value) {
        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(dp(activity, 12), dp(activity, 9),
                dp(activity, 12), dp(activity, 9));
        cell.setBackground(detailBackground(activity));
        TextView name = text(activity, label, 11.5f,
                UiTheme.tertiaryText(activity), false);
        TextView timing = text(activity, value < 0 ? "不适用" : value + " ms",
                13, UiTheme.primaryText(activity), true);
        timing.setPadding(0, dp(activity, 3), 0, 0);
        cell.addView(name);
        cell.addView(timing);
        return cell;
    }

    private static LinearLayout.LayoutParams metricParams(Activity activity, boolean left) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (left) params.rightMargin = dp(activity, 3);
        else params.leftMargin = dp(activity, 3);
        return params;
    }

    private static GradientDrawable detailBackground(Activity activity) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiTheme.softAccent(activity));
        background.setCornerRadius(dp(activity, 11));
        return background;
    }

    private static void addCompactDivider(Activity activity, LinearLayout parent) {
        View divider = new View(activity);
        divider.setBackgroundColor(UiTheme.border(activity));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 1));
        params.leftMargin = dp(activity, 12);
        params.rightMargin = dp(activity, 12);
        parent.addView(divider, params);
    }

    private static LinearLayout card(Activity activity) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiTheme.surface(activity));
        background.setStroke(dp(activity, 1), UiTheme.border(activity));
        background.setCornerRadius(dp(activity, UiComponents.CARD_RADIUS_DP));
        card.setBackground(background);
        return card;
    }

    private static GradientDrawable badgeBackground(Activity activity, boolean reachable) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(reachable ? UiTheme.softAccent(activity) : UiTheme.softGold(activity));
        background.setCornerRadius(dp(activity, 12));
        return background;
    }

    private static TextView sectionTitle(Activity activity, String value) {
        return text(activity, value, 14.5f, UiTheme.primaryText(activity), true);
    }

    private static void addDivider(Activity activity, LinearLayout parent) {
        View divider = new View(activity);
        divider.setBackgroundColor(UiTheme.border(activity));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 1));
        params.leftMargin = dp(activity, 16);
        params.rightMargin = dp(activity, 16);
        parent.addView(divider, params);
    }

    private static TextView text(Activity activity, String value, float size,
                                 int color, boolean bold) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setIncludeFontPadding(false);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private static LinearLayout.LayoutParams fullWrap(Activity activity, int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(activity, bottomMargin);
        return params;
    }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static int dp(Activity activity, float value) {
        return UiComponents.dp(activity, value);
    }
}
