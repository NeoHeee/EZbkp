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
        UiComponents.styleSecondary(dismiss);
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

        TextView detail = text(activity,
                result == null ? "尚未进行线路探测" : result.diagnostic(),
                13, UiTheme.secondaryText(activity), false);
        detail.setLineSpacing(0, 1.18f);
        detail.setTextIsSelectable(true);
        card.addView(detail, fullWrap(activity, 0));
        return card;
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
