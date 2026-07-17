package com.neo.ezaccounting;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class ErrorRecoveryPage {
    public interface Listener {
        void onRetry();
        void onSpeedTest();
        void onSwitchLocal();
        void onSwitchPublic();
        void onUseAutomaticMode();
        void onEditAddresses();
        void onOpenBrowser();
    }

    public static final class Model {
        public final String title;
        public final String message;
        public final String detail;
        public final RouteCoordinator.Snapshot routeSnapshot;
        public final boolean canOpenBrowser;

        public Model(String title, String message, String detail,
                     RouteCoordinator.Snapshot routeSnapshot, boolean canOpenBrowser) {
            this.title = title;
            this.message = message;
            this.detail = detail;
            this.routeSnapshot = routeSnapshot;
            this.canOpenBrowser = canOpenBrowser;
        }
    }

    private ErrorRecoveryPage() {}

    public static View create(Activity activity, Model model, Listener listener) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(UiTheme.background(activity));

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(activity, 24), dp(activity, 36), dp(activity, 24), dp(activity, 28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView icon = new TextView(activity);
        icon.setText("!");
        icon.setGravity(Gravity.CENTER);
        icon.setTextSize(34);
        icon.setTextColor(Color.WHITE);
        icon.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(Color.rgb(217, 119, 6));
        iconBg.setShape(GradientDrawable.OVAL);
        icon.setBackground(iconBg);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                dp(activity, 72), dp(activity, 72));
        iconParams.bottomMargin = dp(activity, 20);
        root.addView(icon, iconParams);

        TextView title = text(activity, safe(model.title, "无法连接记账服务"), 23,
                UiTheme.primaryText(activity), true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, fullWrap(activity, 10));

        TextView message = text(activity, safe(model.message, "请检查网络或服务器地址"),
                15, UiTheme.secondaryText(activity), false);
        message.setGravity(Gravity.CENTER);
        message.setLineSpacing(0, 1.18f);
        root.addView(message, fullWrap(activity, 18));

        TextView diagnostics = text(activity, buildDiagnostics(model), 13.5f,
                UiTheme.secondaryText(activity), false);
        diagnostics.setPadding(dp(activity, 16), dp(activity, 14),
                dp(activity, 16), dp(activity, 14));
        diagnostics.setLineSpacing(0, 1.15f);
        diagnostics.setBackground(box(activity, UiTheme.surface(activity),
                UiTheme.border(activity), 14));
        root.addView(diagnostics, fullWrap(activity, 20));

        Button retry = primaryButton(activity, "重新连接");
        retry.setOnClickListener(v -> listener.onRetry());
        root.addView(retry, buttonParams(activity, 10));

        Button speed = secondaryButton(activity, "手动测速");
        speed.setOnClickListener(v -> listener.onSpeedTest());
        root.addView(speed, buttonParams(activity, 14));

        LinearLayout switchRow = new LinearLayout(activity);
        switchRow.setOrientation(LinearLayout.HORIZONTAL);
        switchRow.setWeightSum(2f);
        Button local = secondaryButton(activity, "切换本地");
        Button remote = secondaryButton(activity, "切换公网");
        local.setOnClickListener(v -> listener.onSwitchLocal());
        remote.setOnClickListener(v -> listener.onSwitchPublic());
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(activity, 50), 1f);
        left.rightMargin = dp(activity, 6);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(activity, 50), 1f);
        right.leftMargin = dp(activity, 6);
        switchRow.addView(local, left);
        switchRow.addView(remote, right);
        root.addView(switchRow, fullWrap(activity, 12));

        Button automatic = secondaryButton(activity, "恢复自动选择线路");
        automatic.setOnClickListener(v -> listener.onUseAutomaticMode());
        root.addView(automatic, buttonParams(activity, 12));

        LinearLayout toolsRow = new LinearLayout(activity);
        toolsRow.setOrientation(LinearLayout.HORIZONTAL);
        toolsRow.setWeightSum(2f);
        Button edit = secondaryButton(activity, "修改地址");
        Button browser = secondaryButton(activity, "浏览器打开");
        browser.setEnabled(model.canOpenBrowser);
        browser.setAlpha(model.canOpenBrowser ? 1f : 0.45f);
        edit.setOnClickListener(v -> listener.onEditAddresses());
        browser.setOnClickListener(v -> listener.onOpenBrowser());
        toolsRow.addView(edit, left);
        toolsRow.addView(browser, right);
        root.addView(toolsRow, fullWrap(activity, 0));

        return scroll;
    }

    private static String buildDiagnostics(Model model) {
        StringBuilder text = new StringBuilder();
        if (model.detail != null && !model.detail.trim().isEmpty()) {
            text.append("错误详情：").append(model.detail.trim()).append("\n\n");
        }
        RouteCoordinator.Snapshot snapshot = model.routeSnapshot;
        if (snapshot == null) {
            text.append("线路状态：尚未完成测速");
            return text.toString();
        }
        text.append("线路模式：").append(snapshot.mode.label()).append('\n');
        text.append("本地线路：").append(label(snapshot.local())).append('\n');
        text.append("公网线路：").append(label(snapshot.publicRoute()));
        return text.toString();
    }

    private static String label(RouteManager.ProbeResult result) {
        if (result == null) return "未检测";
        return result.label() + " · " + result.diagnostic();
    }

    private static Button primaryButton(Activity activity, String label) {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextSize(16);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(15, 118, 110), Color.rgb(13, 148, 136)});
        background.setCornerRadius(dp(activity, 14));
        button.setBackground(background);
        return button;
    }

    private static Button secondaryButton(Activity activity, String label) {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextSize(14.5f);
        button.setTextColor(UiTheme.primaryText(activity));
        button.setAllCaps(false);
        button.setBackground(box(activity, UiTheme.surface(activity),
                UiTheme.border(activity), 14));
        return button;
    }

    private static GradientDrawable box(Activity activity, int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(activity, 1), stroke);
        drawable.setCornerRadius(dp(activity, radius));
        return drawable;
    }

    private static TextView text(Activity activity, String value, float size, int color,
                                 boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private static LinearLayout.LayoutParams fullWrap(Activity activity, int bottomDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(activity, bottomDp);
        return params;
    }

    private static LinearLayout.LayoutParams buttonParams(Activity activity, int bottomDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 52));
        params.bottomMargin = dp(activity, bottomDp);
        return params;
    }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
