package com.neo.ezaccounting;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class CloudflareDiagnosticDialogPage {
    public interface Listener { void onRun(String preferredIp); }

    private CloudflareDiagnosticDialogPage() {}

    public static void show(Activity activity, String preferredIp,
                            CloudflareDiagnosticManager.Report report,
                            boolean running, Listener listener) {
        if (activity == null || activity.isFinishing()) return;
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 20), dp(activity, 16), dp(activity, 20), dp(activity, 12));
        root.setBackgroundColor(UiTheme.surface(activity));

        TextView title = text(activity, "Cloudflare 线路诊断", 22,
                UiTheme.primaryText(activity), true);
        root.addView(title, full(activity, 8));
        TextView description = text(activity,
                "比较系统 DNS 与手动优选 IP。测试仅用于诊断，不会改变网页连接。",
                13, UiTheme.secondaryText(activity), false);
        description.setLineSpacing(0, 1.16f);
        root.addView(description, full(activity, 14));

        EditText input = new EditText(activity);
        input.setHint("CloudflareSpeedTest 优选 IPv4 或 IPv6");
        input.setText(preferredIp == null ? "" : preferredIp);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setTextColor(UiTheme.primaryText(activity));
        input.setHintTextColor(UiTheme.tertiaryText(activity));
        input.setBackground(fieldBackground(activity));
        input.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
        input.setMinHeight(dp(activity, 52));
        root.addView(input, full(activity, 14));

        if (report == null) {
            TextView empty = text(activity, running ? "正在执行三次安全连接测试…" :
                            "尚未测试。留空也可以只检查默认线路和 Cloudflare 节点。",
                    13, UiTheme.secondaryText(activity), false);
            root.addView(empty, full(activity, 12));
        } else {
            root.addView(probeCard(activity, "默认线路", report.normal), full(activity, 8));
            if (report.preferred != null) {
                root.addView(probeCard(activity, "手动优选 IP", report.preferred),
                        full(activity, 10));
            }
            TextView conclusion = text(activity, report.conclusion(), 13,
                    UiTheme.primaryText(activity), true);
            conclusion.setLineSpacing(0, 1.18f);
            conclusion.setPadding(dp(activity, 14), dp(activity, 12),
                    dp(activity, 14), dp(activity, 12));
            conclusion.setBackground(softBackground(activity));
            root.addView(conclusion, full(activity, 10));
        }

        TextView privacy = text(activity,
                "证书始终按原域名验证。不会保存 Cookie、凭据、公网 IP 或响应正文。",
                11.5f, UiTheme.tertiaryText(activity), false);
        privacy.setLineSpacing(0, 1.14f);
        root.addView(privacy, full(activity, 12));

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final AlertDialog[] holder = new AlertDialog[1];
        AlertDialog dialog = new AlertDialog.Builder(activity).setView(scroll)
                .setNegativeButton("关闭", null)
                .setPositiveButton(running ? "测试中" : "开始诊断", null).create();
        holder[0] = dialog;
        dialog.setOnShowListener(ignored -> {
            Button run = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            UiComponents.stylePrimary(run);
            run.setEnabled(!running);
            run.setOnClickListener(view -> {
                String value = input.getText() == null ? "" : input.getText().toString().trim();
                if (!value.isEmpty() &&
                        CloudflareDiagnosticManager.normalizePublicIp(value) == null) {
                    input.setError("请输入公网 IPv4 或 IPv6 地址");
                    return;
                }
                dialog.dismiss();
                if (listener != null) listener.onRun(value);
            });
            Button close = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            UiComponents.styleGoldAction(close);
        });
        UiComponents.show(dialog);
    }

    private static View probeCard(Activity activity, String name,
                                  CloudflareDiagnosticManager.Probe probe) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiTheme.surface(activity));
        background.setStroke(dp(activity, 1), UiTheme.border(activity));
        background.setCornerRadius(dp(activity, UiComponents.CARD_RADIUS_DP));
        card.setBackground(background);

        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(text(activity, name, 15, UiTheme.primaryText(activity), true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        String badgeValue = probe == null ? "未检测" : probe.reachable ?
                probe.successLabel() : "未通过";
        heading.addView(text(activity, badgeValue, 12,
                probe != null && probe.reachable ? UiTheme.accentDark(activity) :
                        UiTheme.secondaryText(activity), true));
        card.addView(heading, full(activity, 7));
        if (probe == null || !probe.reachable) {
            card.addView(text(activity, probe == null ? "无结果" : probe.error, 12.5f,
                    UiTheme.secondaryText(activity), false), full(activity, 0));
            return card;
        }
        String node = probe.colo.isEmpty() ? "节点未识别" : "Cloudflare 节点 " + probe.colo;
        String address = probe.address.isEmpty() ? "" : " · " + probe.address;
        card.addView(text(activity, node + address, 12,
                UiTheme.secondaryText(activity), false), full(activity, 8));
        String dns = probe.dnsMs < 0 ? "DNS：指定 IP" : "DNS：" + probe.dnsMs + " ms";
        card.addView(text(activity, dns, 11.5f, UiTheme.tertiaryText(activity), false),
                full(activity, 6));
        LinearLayout metrics = new LinearLayout(activity);
        metrics.addView(metric(activity, "TCP", probe.tcpMs), metricParams(activity, 0));
        metrics.addView(metric(activity, "TLS", probe.tlsMs), metricParams(activity, 1));
        metrics.addView(metric(activity, "HTTP", probe.httpMs), metricParams(activity, 2));
        metrics.addView(metric(activity, "总计", probe.totalMs), metricParams(activity, 3));
        card.addView(metrics, full(activity, 0));
        return card;
    }

    private static View metric(Activity activity, String label, long value) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(activity, 3), dp(activity, 7), dp(activity, 3), dp(activity, 7));
        box.setBackground(softBackground(activity));
        box.addView(text(activity, label, 10, UiTheme.tertiaryText(activity), false));
        box.addView(text(activity, value < 0 ? "--" : value + " ms", 11,
                UiTheme.primaryText(activity), true));
        return box;
    }

    private static LinearLayout.LayoutParams metricParams(Activity activity, int index) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.leftMargin = index == 0 ? 0 : dp(activity, 2);
        params.rightMargin = index == 3 ? 0 : dp(activity, 2);
        return params;
    }

    private static GradientDrawable fieldBackground(Activity activity) {
        GradientDrawable background = softBackground(activity);
        background.setStroke(dp(activity, 1), UiTheme.border(activity));
        return background;
    }

    private static GradientDrawable softBackground(Activity activity) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiTheme.softAccent(activity));
        background.setCornerRadius(dp(activity, 11));
        return background;
    }

    private static TextView text(Activity activity, String value, float size,
                                 int color, boolean bold) {
        TextView text = new TextView(activity);
        text.setText(value == null ? "" : value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setIncludeFontPadding(false);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private static LinearLayout.LayoutParams full(Activity activity, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(activity, bottom);
        return params;
    }

    private static int dp(Activity activity, float value) {
        return UiComponents.dp(activity, value);
    }
}
