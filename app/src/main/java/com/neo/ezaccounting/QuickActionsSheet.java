package com.neo.ezaccounting;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Arrays;
import java.util.List;

public final class QuickActionsSheet {
    public interface Listener {
        void onHome();
        void onRouteStatus();
        void onManualRoute();
        void onSpeedTest();
        void onOpenBrowser();
        void onEditAddresses();
        void onLock();
        void onSecuritySettings();
        void onCheckUpdate();
        void onWebViewInfo();
        void onClearSiteData();
    }

    public static final class Model {
        public final String mode;
        public final String route;
        public final String latency;
        public final String security;

        public Model(String mode, String route, String latency, String security) {
            this.mode = safe(mode, "自动选择");
            this.route = safe(route, "未选择线路");
            this.latency = safe(latency, "待测速");
            this.security = safe(security, "未开启保护");
        }

        private static String safe(String value, String fallback) {
            return value == null || value.trim().isEmpty() ? fallback : value.trim();
        }
    }

    private static final class ActionItem {
        final int icon;
        final String title;
        final String description;
        final Runnable action;
        final boolean dangerous;

        ActionItem(int icon, String title, String description, Runnable action) {
            this(icon, title, description, action, false);
        }

        ActionItem(int icon, String title, String description, Runnable action,
                   boolean dangerous) {
            this.icon = icon;
            this.title = title;
            this.description = description;
            this.action = action;
            this.dangerous = dangerous;
        }
    }

    private QuickActionsSheet() {}

    public static void show(Activity activity, Model model, Listener listener) {
        if (activity == null || activity.isFinishing() || listener == null) return;

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(activity, 18), dp(activity, 10), dp(activity, 18),
                dp(activity, 14));
        sheet.setBackground(sheetBackground(activity));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            sheet.setElevation(dp(activity, 18));
        }

        sheet.addView(createHandle(activity));
        sheet.addView(createHeader(activity, dialog));
        sheet.addView(createStatusPanel(activity, model));

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(activity, 4), 0, dp(activity, 4));

        addSection(activity, content, "线路与访问", Arrays.asList(
                new ActionItem(android.R.drawable.ic_menu_info_details,
                        "查看线路状态", "查看当前线路、延迟和可用性", listener::onRouteStatus),
                new ActionItem(android.R.drawable.ic_menu_rotate,
                        "切换访问线路", "选择自动、本地或公网线路模式", listener::onManualRoute),
                new ActionItem(android.R.drawable.ic_menu_recent_history,
                        "重新测速", "重新检测本地与公网线路响应速度", listener::onSpeedTest),
                new ActionItem(android.R.drawable.ic_menu_edit,
                        "修改服务器地址", "修改本地与公网 ezBookkeeping 地址",
                        listener::onEditAddresses),
                new ActionItem(android.R.drawable.ic_menu_share,
                        "在浏览器中打开", "使用系统浏览器打开当前页面",
                        listener::onOpenBrowser)
        ), dialog);

        addSection(activity, content, "安全与控制", Arrays.asList(
                new ActionItem(android.R.drawable.ic_lock_lock,
                        "立即上锁", "立即隐藏账目并重新进行身份验证", listener::onLock),
                new ActionItem(android.R.drawable.ic_secure,
                        "安全验证设置", "设置指纹、PIN 或九宫格图形锁",
                        listener::onSecuritySettings)
        ), dialog);

        addSection(activity, content, "页面与维护", Arrays.asList(
                new ActionItem(android.R.drawable.ic_menu_view,
                        "回到首页", "返回 ezBookkeeping 主页面", listener::onHome),
                new ActionItem(android.R.drawable.stat_sys_download_done,
                        "检查新版本", "检查 GitHub Releases 中的正式更新",
                        listener::onCheckUpdate),
                new ActionItem(android.R.drawable.ic_menu_manage,
                        "内核信息", "查看或切换 Android System WebView",
                        listener::onWebViewInfo),
                new ActionItem(android.R.drawable.ic_menu_delete,
                        "清除登录与缓存", "退出当前登录并清理网页缓存",
                        listener::onClearSiteData, true)
        ), dialog);

        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sheet.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        sheet.addView(createCloseButton(activity, dialog));

        dialog.setContentView(sheet);
        dialog.setOnShowListener(ignored -> configureWindow(activity, dialog));
        dialog.show();
    }

    private static View createHandle(Context context) {
        View handle = new View(context);
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiTheme.border(context));
        background.setCornerRadius(dp(context, 3));
        handle.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(context, 40), dp(context, 4));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = dp(context, 10);
        handle.setLayoutParams(params);
        return handle;
    }

    private static View createHeader(Context context, Dialog dialog) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 4), 0, 0, dp(context, 10));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(context, "快捷功能", 22, UiTheme.primaryText(context));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        TextView subtitle = text(context, "线路、安全与维护工具", 13,
                UiTheme.secondaryText(context));
        subtitle.setPadding(0, dp(context, 3), 0, 0);
        texts.addView(title);
        texts.addView(subtitle);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = text(context, "×", 28, UiTheme.secondaryText(context));
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("关闭快捷功能");
        close.setBackground(ripple(context, Color.TRANSPARENT));
        close.setOnClickListener(view -> dialog.dismiss());
        row.addView(close, new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)));
        return row;
    }

    private static View createStatusPanel(Context context, Model model) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(context, 14), dp(context, 12), dp(context, 14),
                dp(context, 12));
        panel.setBackground(statusBackground(context));

        TextView label = text(context, "当前状态", 12, UiTheme.secondaryText(context));
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        panel.addView(label);

        HorizontalScrollView scroller = new HorizontalScrollView(context);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(false);
        LinearLayout chips = new LinearLayout(context);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, dp(context, 9), 0, 0);
        chips.addView(chip(context, "模式 · " + model.mode));
        chips.addView(chip(context, "线路 · " + model.route));
        chips.addView(chip(context, "延迟 · " + model.latency));
        chips.addView(chip(context, "保护 · " + model.security));
        scroller.addView(chips);
        panel.addView(scroller);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(context, 8);
        panel.setLayoutParams(params);
        return panel;
    }

    private static View chip(Context context, String value) {
        TextView chip = text(context, value, 12, UiTheme.primaryText(context));
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding(dp(context, 10), dp(context, 6), dp(context, 10),
                dp(context, 6));
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiTheme.surface(context));
        background.setCornerRadius(dp(context, 16));
        background.setStroke(dp(context, 1), UiTheme.border(context));
        chip.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(context, 8);
        chip.setLayoutParams(params);
        return chip;
    }

    private static void addSection(Context context, LinearLayout parent, String title,
                                   List<ActionItem> items, Dialog dialog) {
        TextView heading = text(context, title, 13, UiTheme.tertiaryText(context));
        heading.setTypeface(heading.getTypeface(), android.graphics.Typeface.BOLD);
        heading.setPadding(dp(context, 4), dp(context, 14), 0, dp(context, 7));
        parent.addView(heading);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBackground(context));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation(dp(context, 1));
        }
        for (int index = 0; index < items.size(); index++) {
            ActionItem item = items.get(index);
            card.addView(createActionRow(context, item, dialog));
            if (index < items.size() - 1) card.addView(divider(context));
        }
        parent.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static View createActionRow(Context context, ActionItem item, Dialog dialog) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 12), dp(context, 9), dp(context, 10), dp(context, 9));
        row.setMinimumHeight(dp(context, 66));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(ripple(context, Color.TRANSPARENT));
        row.setContentDescription(item.title + "，" + item.description);
        row.setOnClickListener(view -> {
            dialog.dismiss();
            item.action.run();
        });

        int foreground = item.dangerous ? danger(context) : UiTheme.accent(context);
        ImageView icon = new ImageView(context);
        icon.setImageResource(item.icon);
        icon.setColorFilter(foreground);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(context, 9), dp(context, 9), dp(context, 9), dp(context, 9));
        icon.setBackground(iconBackground(context, item.dangerous));
        icon.setContentDescription(null);
        row.addView(icon, new LinearLayout.LayoutParams(dp(context, 42), dp(context, 42)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(context, 12), 0, dp(context, 8), 0);
        TextView title = text(context, item.title, 15,
                item.dangerous ? danger(context) : UiTheme.primaryText(context));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        TextView description = text(context, item.description, 12,
                item.dangerous ? dangerSecondary(context) : UiTheme.secondaryText(context));
        description.setPadding(0, dp(context, 3), 0, 0);
        texts.addView(title);
        texts.addView(description);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrow = text(context, "›", 26,
                item.dangerous ? dangerSecondary(context) : UiTheme.tertiaryText(context));
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(context, 24), dp(context, 44)));
        return row;
    }

    private static View divider(Context context) {
        View divider = new View(context);
        divider.setBackgroundColor(UiTheme.border(context));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1));
        params.leftMargin = dp(context, 66);
        divider.setLayoutParams(params);
        return divider;
    }

    private static View createCloseButton(Context context, Dialog dialog) {
        TextView button = text(context, "关闭", 15, Color.WHITE);
        button.setTypeface(button.getTypeface(), android.graphics.Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(UiTheme.accent(context));
        fill.setCornerRadius(dp(context, 14));
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(45, 255, 255, 255)), fill, null));
        button.setOnClickListener(view -> dialog.dismiss());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 48));
        params.topMargin = dp(context, 12);
        button.setLayoutParams(params);
        return button;
    }

    private static void configureWindow(Activity activity, Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = UiTheme.isDark(activity) ? 0.62f : 0.48f;
        window.setAttributes(attributes);
        window.setNavigationBarColor(UiTheme.surface(activity));

        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int sideGap = dp(activity, 12);
        int maxWidth = dp(activity, 640);
        int width = Math.min(metrics.widthPixels - sideGap * 2, maxWidth);
        int height = Math.min((int) (metrics.heightPixels * 0.92f), dp(activity, 820));
        window.setLayout(width, height);
    }

    private static GradientDrawable sheetBackground(Context context) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiTheme.surface(context));
        float radius = dp(context, 24);
        background.setCornerRadii(new float[]{radius, radius, radius, radius,
                radius, radius, radius, radius});
        return background;
    }

    private static GradientDrawable cardBackground(Context context) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiTheme.isDark(context) ? Color.rgb(31, 41, 42) : Color.WHITE);
        background.setCornerRadius(dp(context, 18));
        background.setStroke(dp(context, 1), UiTheme.border(context));
        return background;
    }

    private static GradientDrawable statusBackground(Context context) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiTheme.isDark(context) ? Color.rgb(20, 55, 52) :
                Color.rgb(231, 250, 247));
        background.setCornerRadius(dp(context, 18));
        background.setStroke(dp(context, 1), UiTheme.isDark(context) ?
                Color.rgb(34, 98, 91) : Color.rgb(169, 230, 220));
        return background;
    }

    private static GradientDrawable iconBackground(Context context, boolean dangerous) {
        GradientDrawable background = new GradientDrawable();
        if (dangerous) {
            background.setColor(UiTheme.isDark(context) ? Color.rgb(72, 31, 35) :
                    Color.rgb(254, 235, 238));
        } else {
            background.setColor(UiTheme.isDark(context) ? Color.rgb(23, 63, 59) :
                    Color.rgb(225, 248, 244));
        }
        background.setCornerRadius(dp(context, 13));
        return background;
    }

    private static RippleDrawable ripple(Context context, int contentColor) {
        GradientDrawable content = new GradientDrawable();
        content.setColor(contentColor);
        return new RippleDrawable(ColorStateList.valueOf(
                Color.argb(UiTheme.isDark(context) ? 54 : 32, 13, 148, 136)), content, null);
    }

    private static TextView text(Context context, String value, float sizeSp, int color) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        text.setTextColor(color);
        text.setIncludeFontPadding(false);
        return text;
    }

    private static int danger(Context context) {
        return UiTheme.isDark(context) ? Color.rgb(248, 113, 113) : Color.rgb(220, 38, 38);
    }

    private static int dangerSecondary(Context context) {
        return UiTheme.isDark(context) ? Color.rgb(252, 165, 165) : Color.rgb(185, 28, 28);
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}