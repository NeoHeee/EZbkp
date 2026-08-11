package com.neo.ezaccounting;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public final class SettingsCenterPage {
    private static final String ROOT_TAG = "settings_center_root";
    public interface Listener {
        void onClose();
        void onServerAddresses();
        void onRouteStatus();
        void onQuickActionsChanged(boolean enabled);
        void onSecuritySettings();
        void onCheckUpdate();
        void onWebViewInfo();
        void onClearSiteData();
    }

    public static final class Model {
        public final String routeSummary;
        public final boolean quickActionsEnabled;
        public final String securitySummary;
        public final String appVersion;
        public final String serverVersion;

        public Model(String routeSummary, boolean quickActionsEnabled, String securitySummary,
                     String appVersion, String serverVersion) {
            this.routeSummary = routeSummary;
            this.quickActionsEnabled = quickActionsEnabled;
            this.securitySummary = securitySummary;
            this.appVersion = appVersion;
            this.serverVersion = serverVersion;
        }
    }

    private SettingsCenterPage() {}

    public static View create(Activity activity, Model model, Listener listener) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setTag(ROOT_TAG);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(UiTheme.background(activity));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, UiComponents.PAGE_HORIZONTAL_DP),
                dp(activity, 12),
                dp(activity, UiComponents.PAGE_HORIZONTAL_DP),
                dp(activity, UiComponents.PAGE_BOTTOM_DP));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(header(activity, listener), fullWidth(activity, 16));

        LinearLayout settings = card(activity);
        addRow(activity, settings, "服务器地址", model.routeSummary,
                listener::onServerAddresses, false);
        addRow(activity, settings, "线路状态", "当前线路与延迟",
                listener::onRouteStatus, false);
        addSwitchRow(activity, settings, "快捷入口", model.quickActionsEnabled,
                listener::onQuickActionsChanged, false);
        addRow(activity, settings, "应用锁", model.securitySummary,
                listener::onSecuritySettings, false);
        addInfoRow(activity, settings, "App 版本", model.appVersion, false);
        addInfoRow(activity, settings, "ezBookkeeping 服务端", model.serverVersion, false);
        addRow(activity, settings, "检查更新", null,
                listener::onCheckUpdate, false);
        addRow(activity, settings, "WebView 内核", null,
                listener::onWebViewInfo, false);
        addRow(activity, settings, "清除登录与网页数据", null,
                listener::onClearSiteData, true, true);
        content.addView(settings, fullWidth(activity, 0));
        return scroll;
    }

    public static boolean isRoot(View view) {
        return view != null && ROOT_TAG.equals(view.getTag());
    }

    private static View header(Context context, Listener listener) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = text(context, "‹", 40, UiTheme.primaryText(context), false);
        UiComponents.styleIconButton(back);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("返回记账页面");
        back.setOnClickListener(view -> listener.onClose());
        row.addView(back, new LinearLayout.LayoutParams(dp(context, 52), dp(context, 52)));

        TextView title = text(context, "设置", 22, UiTheme.primaryText(context), false);
        title.setGravity(Gravity.CENTER);
        row.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        View balance = new View(context);
        balance.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(balance, new LinearLayout.LayoutParams(dp(context, 52), dp(context, 52)));
        return row;
    }

    private static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UiComponents.surface(context));
        card.setClipToOutline(true);
        return card;
    }

    private static void addRow(Context context, LinearLayout card, String title,
                               String summary, Runnable action, boolean last) {
        addRow(context, card, title, summary, action, last, false);
    }

    private static void addInfoRow(Context context, LinearLayout card, String title,
                                   String value, boolean last) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 18), dp(context, 10), dp(context, 18), dp(context, 10));
        row.setMinimumHeight(dp(context, 64));
        row.setContentDescription(title + "。" + value);

        TextView name = text(context, title, 15, UiTheme.primaryText(context), false);
        row.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView version = text(context, value, 14, UiTheme.secondaryText(context), false);
        version.setGravity(Gravity.END);
        version.setSingleLine(true);
        version.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(version, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!last) {
            View divider = new View(context);
            divider.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            divider.setBackgroundColor(UiTheme.border(context));
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1));
            dividerParams.leftMargin = dp(context, 16);
            card.addView(divider, dividerParams);
        }
    }

    private interface BooleanChangeListener {
        void onChanged(boolean enabled);
    }

    private static void addSwitchRow(Context context, LinearLayout card, String title,
                                     boolean checked, BooleanChangeListener listener,
                                     boolean last) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 18), dp(context, 8), dp(context, 14), dp(context, 8));
        row.setMinimumHeight(dp(context, 64));

        TextView name = text(context, title, 16, UiTheme.primaryText(context), false);
        row.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(context);
        toggle.setChecked(checked);
        toggle.setShowText(false);
        toggle.setMinWidth(dp(context, 52));
        toggle.setMinHeight(dp(context, 48));
        toggle.setContentDescription(title + (checked ? "已开启" : "已关闭"));
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{}
        };
        toggle.setTrackTintList(new ColorStateList(states, new int[]{
                UiTheme.accent(context), UiTheme.border(context)
        }));
        toggle.setThumbTintList(new ColorStateList(states, new int[]{
                UiTheme.surface(context), UiTheme.surface(context)
        }));
        toggle.setOnCheckedChangeListener((button, enabled) -> {
            button.setContentDescription(title + (enabled ? "已开启" : "已关闭"));
            listener.onChanged(enabled);
        });
        row.addView(toggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (!last) addDivider(context, card);
    }

    private static void addRow(Context context, LinearLayout card, String title,
                               String summary, Runnable action, boolean last,
                               boolean dangerous) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 18), dp(context, 10), dp(context, 10), dp(context, 10));
        row.setMinimumHeight(dp(context, 64));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(summary == null || summary.isEmpty() ? title :
                title + "。" + summary);
        row.setBackground(ripple(context, Color.TRANSPARENT));
        row.setOnClickListener(view -> action.run());

        int titleColor = dangerous ? danger(context) : UiTheme.primaryText(context);
        TextView name = text(context, title, 16, titleColor, false);
        row.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (summary != null && !summary.isEmpty()) {
            TextView detail = text(context, summary, 14,
                    dangerous ? danger(context) : UiTheme.secondaryText(context), false);
            detail.setAlpha(dangerous ? 0.82f : 1f);
            detail.setGravity(Gravity.END);
            detail.setSingleLine(true);
            detail.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.25f);
            detailParams.leftMargin = dp(context, 12);
            row.addView(detail, detailParams);
        }

        TextView arrow = text(context, "›", 30,
                dangerous ? danger(context) : UiTheme.tertiaryText(context), false);
        arrow.setGravity(Gravity.CENTER);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(context, 28), dp(context, 48)));
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

    private static void addDivider(Context context, LinearLayout card) {
        View divider = new View(context);
        divider.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        divider.setBackgroundColor(UiTheme.border(context));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1));
        params.leftMargin = dp(context, 18);
        card.addView(divider, params);
    }

    private static RippleDrawable ripple(Context context, GradientDrawable content) {
        return new RippleDrawable(ColorStateList.valueOf(Color.argb(
                UiTheme.isDark(context) ? 54 : 34, 23, 107, 91)), content, null);
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
        return UiTheme.danger(context);
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
