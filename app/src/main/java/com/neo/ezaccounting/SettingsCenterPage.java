package com.neo.ezaccounting;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class SettingsCenterPage {
    public interface Listener {
        void onClose();
        void onServerAddresses();
        void onRouteStatus();
        void onRouteMode();
        void onSpeedTest();
        void onInteractionSettings();
        void onSecuritySettings();
        void onCheckUpdate();
        void onWebViewInfo();
        void onClearSiteData();
    }

    public static final class Model {
        public final String routeSummary;
        public final String interactionSummary;
        public final String securitySummary;

        public Model(String routeSummary, String interactionSummary, String securitySummary) {
            this.routeSummary = routeSummary;
            this.interactionSummary = interactionSummary;
            this.securitySummary = securitySummary;
        }
    }

    private SettingsCenterPage() {}

    public static View create(Activity activity, Model model, Listener listener) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(UiTheme.background(activity));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 20), dp(activity, 24),
                dp(activity, 20), dp(activity, 36));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(header(activity, listener), fullWidth(activity, 22));

        addSection(activity, content, "连接与线路");
        LinearLayout connection = card(activity);
        addRow(activity, connection, "服务器地址", model.routeSummary,
                listener::onServerAddresses, false);
        addRow(activity, connection, "线路状态", "查看当前线路、延迟与可用性",
                listener::onRouteStatus, false);
        addRow(activity, connection, "线路模式", "自动选择、本地线路或公网线路",
                listener::onRouteMode, false);
        addRow(activity, connection, "重新测速", "检测两条线路的连接速度",
                listener::onSpeedTest, true);
        content.addView(connection, fullWidth(activity, 24));

        addSection(activity, content, "外观与交互");
        LinearLayout interaction = card(activity);
        addRow(activity, interaction, "快捷入口", model.interactionSummary,
                listener::onInteractionSettings, true);
        content.addView(interaction, fullWidth(activity, 24));

        addSection(activity, content, "安全与隐私");
        LinearLayout security = card(activity);
        addRow(activity, security, "应用锁", model.securitySummary,
                listener::onSecuritySettings, true);
        content.addView(security, fullWidth(activity, 24));

        addSection(activity, content, "诊断与维护");
        LinearLayout diagnostics = card(activity);
        addRow(activity, diagnostics, "检查更新", "获取最新正式版本",
                listener::onCheckUpdate, false);
        addRow(activity, diagnostics, "WebView 内核", "查看当前网页运行环境",
                listener::onWebViewInfo, true);
        content.addView(diagnostics, fullWidth(activity, 24));

        addSection(activity, content, "数据管理");
        LinearLayout data = card(activity);
        addRow(activity, data, "清除登录与网页数据", "退出账号并清理 Cookie 与缓存",
                listener::onClearSiteData, true, true);
        content.addView(data, fullWidth(activity, 0));
        return scroll;
    }

    private static View header(Context context, Listener listener) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(context, "设置", 28, UiTheme.primaryText(context), true);
        TextView subtitle = text(context, "连接、交互、安全与维护", 14,
                UiTheme.secondaryText(context), false);
        subtitle.setPadding(0, dp(context, 5), 0, 0);
        titles.addView(title);
        titles.addView(subtitle);
        row.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = text(context, "×", 28, UiTheme.secondaryText(context), false);
        close.setGravity(Gravity.CENTER);
        close.setMinWidth(dp(context, 48));
        close.setMinHeight(dp(context, 48));
        close.setContentDescription("关闭设置中心");
        close.setBackground(ripple(context, circle(context)));
        close.setOnClickListener(view -> listener.onClose());
        row.addView(close, new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)));
        return row;
    }

    private static void addSection(Context context, LinearLayout parent, String title) {
        TextView label = text(context, title, 13, UiTheme.secondaryText(context), true);
        label.setPadding(dp(context, 4), 0, 0, dp(context, 9));
        parent.addView(label);
    }

    private static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiTheme.surface(context));
        background.setCornerRadius(dp(context, 16));
        card.setBackground(background);
        card.setClipToOutline(true);
        return card;
    }

    private static void addRow(Context context, LinearLayout card, String title,
                               String summary, Runnable action, boolean last) {
        addRow(context, card, title, summary, action, last, false);
    }

    private static void addRow(Context context, LinearLayout card, String title,
                               String summary, Runnable action, boolean last,
                               boolean dangerous) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 16), dp(context, 14), dp(context, 12), dp(context, 14));
        row.setMinimumHeight(dp(context, 64));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(title + "。" + summary);
        row.setBackground(ripple(context, Color.TRANSPARENT));
        row.setOnClickListener(view -> action.run());

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        int titleColor = dangerous ? danger(context) : UiTheme.primaryText(context);
        TextView name = text(context, title, 16, titleColor, false);
        TextView detail = text(context, summary, 13,
                dangerous ? danger(context) : UiTheme.secondaryText(context), false);
        detail.setAlpha(dangerous ? 0.82f : 1f);
        detail.setPadding(0, dp(context, 4), 0, 0);
        labels.addView(name);
        labels.addView(detail);
        row.addView(labels, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrow = text(context, "›", 28,
                dangerous ? danger(context) : UiTheme.tertiaryText(context), false);
        arrow.setGravity(Gravity.CENTER);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(context, 32), dp(context, 48)));
        card.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!last) {
            View divider = new View(context);
            divider.setBackgroundColor(UiTheme.border(context));
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1));
            dividerParams.leftMargin = dp(context, 16);
            card.addView(divider, dividerParams);
        }
    }

    private static RippleDrawable ripple(Context context, int color) {
        GradientDrawable content = new GradientDrawable();
        content.setColor(color);
        return ripple(context, content);
    }

    private static RippleDrawable ripple(Context context, GradientDrawable content) {
        return new RippleDrawable(ColorStateList.valueOf(Color.argb(
                UiTheme.isDark(context) ? 54 : 34, 13, 148, 136)), content, null);
    }

    private static GradientDrawable circle(Context context) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(UiTheme.isDark(context) ? Color.rgb(40, 51, 52) :
                Color.rgb(235, 241, 240));
        return background;
    }

    private static TextView text(Context context, String value, float size,
                                 int color, boolean bold) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        text.setTextColor(color);
        text.setIncludeFontPadding(false);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private static int danger(Context context) {
        return UiTheme.isDark(context) ? Color.rgb(248, 113, 113) : Color.rgb(190, 24, 34);
    }

    private static LinearLayout.LayoutParams fullWidth(Context context, int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(context, bottomMargin);
        return params;
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
