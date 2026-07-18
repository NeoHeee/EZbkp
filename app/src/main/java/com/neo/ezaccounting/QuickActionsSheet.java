package com.neo.ezaccounting;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
        dialog.setCanceledOnTouchOutside(false);

        FrameLayout overlay = new FrameLayout(activity);
        overlay.setPadding(dp(activity, 16), dp(activity, 24), dp(activity, 16),
                dp(activity, 24));
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setContentDescription("快捷功能遮罩，点击空白区域关闭");
        overlay.setOnClickListener(view -> dialog.dismiss());

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 18), dp(activity, 16), dp(activity, 18),
                dp(activity, 14));
        panel.setBackground(panelBackground(activity));
        panel.setClickable(true);
        panel.setFocusable(true);
        panel.setOnClickListener(view -> { });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            panel.setElevation(dp(activity, 20));
        }

        panel.addView(createHeader(activity, dialog));
        panel.addView(createStatusDashboard(activity, model));

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(activity, 4), 0, dp(activity, 4));

        addSectionHeading(activity, content, "常用操作", "页面、线路与安全控制");
        addTileGrid(activity, content, Arrays.asList(
                new ActionItem(android.R.drawable.ic_menu_view,
                        "回到首页", "返回记账主页面", listener::onHome),
                new ActionItem(android.R.drawable.ic_popup_sync,
                        "刷新当前页面", "重新加载正在查看的网页", () -> {
                            boolean refreshed = WebViewController.reloadActive();
                            Toast.makeText(activity,
                                    refreshed ? "正在刷新当前页面" : "当前页面尚未加载",
                                    Toast.LENGTH_SHORT).show();
                        }),
                new ActionItem(android.R.drawable.ic_menu_info_details,
                        "线路状态", "查看延迟与可用性", listener::onRouteStatus),
                new ActionItem(android.R.drawable.ic_menu_rotate,
                        "切换线路", "自动、本地或公网", listener::onManualRoute),
                new ActionItem(android.R.drawable.ic_menu_recent_history,
                        "重新测速", "检测两条线路速度", listener::onSpeedTest),
                new ActionItem(android.R.drawable.ic_lock_lock,
                        "立即上锁", "隐藏账目并重新验证", listener::onLock),
                new ActionItem(android.R.drawable.ic_secure,
                        "安全设置", "指纹、PIN 与图形锁", listener::onSecuritySettings)
        ), dialog);

        addSectionHeading(activity, content, "更多工具", "服务器、更新与运行环境");
        addTileGrid(activity, content, Arrays.asList(
                new ActionItem(android.R.drawable.ic_menu_edit,
                        "服务器地址", "修改本地和公网地址", listener::onEditAddresses),
                new ActionItem(android.R.drawable.ic_menu_share,
                        "浏览器打开", "交给系统浏览器访问", listener::onOpenBrowser),
                new ActionItem(android.R.drawable.stat_sys_download_done,
                        "检查更新", "获取最新正式版本", listener::onCheckUpdate),
                new ActionItem(android.R.drawable.ic_menu_manage,
                        "内核信息", "查看系统 WebView", listener::onWebViewInfo)
        ), dialog);

        addSectionHeading(activity, content, "数据与维护", "谨慎执行不可撤销操作");
        content.addView(createDangerAction(activity,
                new ActionItem(android.R.drawable.ic_menu_delete,
                        "清除登录与缓存", "退出当前账号并清理网页缓存",
                        listener::onClearSiteData, true), dialog));

        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView hint = text(activity, "点击卡片外区域可关闭", 12,
                UiTheme.tertiaryText(activity));
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(activity, 10), 0, 0);
        panel.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int width = Math.min(metrics.widthPixels - dp(activity, 32), dp(activity, 560));
        int height = Math.min((int) (metrics.heightPixels * 0.84f), dp(activity, 760));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                width, height, Gravity.CENTER);
        overlay.addView(panel, panelParams);

        dialog.setContentView(overlay);
        dialog.setOnShowListener(ignored -> {
            configureWindow(activity, dialog);
            animateIn(panel);
        });
        dialog.show();
    }

    private static View createHeader(Context context, Dialog dialog) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 2), 0, 0, dp(context, 12));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(context, "快捷中心", 22, UiTheme.primaryText(context));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView subtitle = text(context, "页面、线路、安全与维护工具", 13,
                UiTheme.secondaryText(context));
        subtitle.setPadding(0, dp(context, 3), 0, 0);
        texts.addView(title);
        texts.addView(subtitle);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = text(context, "×", 28, UiTheme.secondaryText(context));
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("关闭快捷中心");
        close.setBackground(ripple(context, circleBackground(context)));
        close.setOnClickListener(view -> dialog.dismiss());
        row.addView(close, new LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)));
        return row;
    }

    private static View createStatusDashboard(Context context, Model model) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(context, 12), dp(context, 12), dp(context, 12),
                dp(context, 12));
        panel.setBackground(statusBackground(context));

        TextView label = text(context, "当前状态", 12, UiTheme.secondaryText(context));
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(label);

        LinearLayout firstRow = new LinearLayout(context);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        firstRow.setPadding(0, dp(context, 9), 0, 0);
        firstRow.addView(statusCell(context, "线路模式", model.mode),
                weightedCellParams(context, true));
        firstRow.addView(statusCell(context, "当前线路", model.route),
                weightedCellParams(context, false));
        panel.addView(firstRow);

        LinearLayout secondRow = new LinearLayout(context);
        secondRow.setOrientation(LinearLayout.HORIZONTAL);
        secondRow.setPadding(0, dp(context, 8), 0, 0);
        secondRow.addView(statusCell(context, "最近延迟", model.latency),
                weightedCellParams(context, true));
        secondRow.addView(statusCell(context, "安全保护", model.security),
                weightedCellParams(context, false));
        panel.addView(secondRow);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(context, 8);
        panel.setLayoutParams(params);
        return panel;
    }

    private static View statusCell(Context context, String label, String value) {
        LinearLayout cell = new LinearLayout(context);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(dp(context, 11), dp(context, 9), dp(context, 11), dp(context, 9));
        cell.setBackground(statusCellBackground(context));

        TextView small = text(context, label, 11, UiTheme.tertiaryText(context));
        TextView main = text(context, value, 14, UiTheme.primaryText(context));
        main.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        main.setPadding(0, dp(context, 4), 0, 0);
        main.setMaxLines(1);
        cell.addView(small);
        cell.addView(main);
        return cell;
    }

    private static LinearLayout.LayoutParams weightedCellParams(Context context, boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (first) params.rightMargin = dp(context, 4);
        else params.leftMargin = dp(context, 4);
        return params;
    }

    private static void addSectionHeading(Context context, LinearLayout parent,
                                          String title, String subtitle) {
        LinearLayout heading = new LinearLayout(context);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(context, 2), dp(context, 12), 0, dp(context, 8));
        TextView name = text(context, title, 14, UiTheme.primaryText(context));
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView detail = text(context, subtitle, 11, UiTheme.tertiaryText(context));
        detail.setPadding(0, dp(context, 2), 0, 0);
        heading.addView(name);
        heading.addView(detail);
        parent.addView(heading);
    }

    private static void addTileGrid(Context context, LinearLayout parent,
                                    List<ActionItem> items, Dialog dialog) {
        for (int index = 0; index < items.size(); index += 2) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);

            if (index + 1 >= items.size()) {
                row.addView(createTile(context, items.get(index), dialog), fullTileParams());
            } else {
                row.addView(createTile(context, items.get(index), dialog),
                        tileParams(context, true));
                row.addView(createTile(context, items.get(index + 1), dialog),
                        tileParams(context, false));
            }

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = dp(context, 8);
            parent.addView(row, rowParams);
        }
    }

    private static LinearLayout.LayoutParams tileParams(Context context, boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (first) params.rightMargin = dp(context, 4);
        else params.leftMargin = dp(context, 4);
        return params;
    }

    private static LinearLayout.LayoutParams fullTileParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static View createTile(Context context, ActionItem item, Dialog dialog) {
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.START);
        tile.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 11));
        tile.setMinimumHeight(dp(context, 112));
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setBackground(ripple(context, tileBackground(context, false)));
        tile.setContentDescription(item.title + "，" + item.description);
        tile.setOnClickListener(view -> {
            dialog.dismiss();
            item.action.run();
        });

        ImageView icon = new ImageView(context);
        icon.setImageResource(item.icon);
        icon.setColorFilter(UiTheme.accent(context));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));
        icon.setBackground(iconBackground(context, false));
        icon.setContentDescription(null);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(context, 38), dp(context, 38)));

        TextView title = text(context, item.title, 14, UiTheme.primaryText(context));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(0, dp(context, 9), 0, 0);
        tile.addView(title);

        TextView description = text(context, item.description, 11,
                UiTheme.secondaryText(context));
        description.setPadding(0, dp(context, 3), 0, 0);
        description.setMaxLines(2);
        tile.addView(description);
        return tile;
    }

    private static View createDangerAction(Context context, ActionItem item, Dialog dialog) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 12), dp(context, 10), dp(context, 10), dp(context, 10));
        row.setMinimumHeight(dp(context, 66));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(ripple(context, tileBackground(context, true)));
        row.setContentDescription(item.title + "，" + item.description);
        row.setOnClickListener(view -> {
            dialog.dismiss();
            item.action.run();
        });

        ImageView icon = new ImageView(context);
        icon.setImageResource(item.icon);
        icon.setColorFilter(danger(context));
        icon.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));
        icon.setBackground(iconBackground(context, true));
        row.addView(icon, new LinearLayout.LayoutParams(dp(context, 40), dp(context, 40)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(context, 11), 0, dp(context, 6), 0);
        TextView title = text(context, item.title, 14, danger(context));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView description = text(context, item.description, 11, dangerSecondary(context));
        description.setPadding(0, dp(context, 3), 0, 0);
        texts.addView(title);
        texts.addView(description);
        row.addView(texts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrow = text(context, "›", 25, dangerSecondary(context));
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(context, 24), dp(context, 40)));
        return row;
    }

    private static void configureWindow(Activity activity, Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.setGravity(Gravity.CENTER);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = UiTheme.isDark(activity) ? 0.70f : 0.56f;
        attributes.width = WindowManager.LayoutParams.MATCH_PARENT;
        attributes.height = WindowManager.LayoutParams.MATCH_PARENT;
        window.setAttributes(attributes);
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT);
        window.setNavigationBarColor(UiTheme.background(activity));
        window.setStatusBarColor(UiTheme.background(activity));
    }

    private static void animateIn(View panel) {
        panel.setAlpha(0f);
        panel.setScaleX(0.92f);
        panel.setScaleY(0.92f);
        panel.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220L)
                .start();
    }

    private static GradientDrawable panelBackground(Context context) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiTheme.surface(context));
        background.setCornerRadius(dp(context, 24));
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

    private static GradientDrawable statusCellBackground(Context context) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiTheme.isDark(context) ? Color.rgb(26, 67, 63) : Color.WHITE);
        background.setCornerRadius(dp(context, 13));
        return background;
    }

    private static GradientDrawable tileBackground(Context context, boolean dangerous) {
        GradientDrawable background = new GradientDrawable();
        if (dangerous) {
            background.setColor(UiTheme.isDark(context) ? Color.rgb(56, 29, 33) :
                    Color.rgb(255, 245, 246));
            background.setStroke(dp(context, 1), UiTheme.isDark(context) ?
                    Color.rgb(104, 49, 57) : Color.rgb(252, 199, 207));
        } else {
            background.setColor(UiTheme.isDark(context) ? Color.rgb(31, 41, 42) : Color.WHITE);
            background.setStroke(dp(context, 1), UiTheme.border(context));
        }
        background.setCornerRadius(dp(context, 16));
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
        background.setCornerRadius(dp(context, 12));
        return background;
    }

    private static GradientDrawable circleBackground(Context context) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(UiTheme.isDark(context) ? Color.rgb(40, 51, 52) :
                Color.rgb(241, 245, 245));
        return background;
    }

    private static RippleDrawable ripple(Context context, GradientDrawable content) {
        return new RippleDrawable(ColorStateList.valueOf(
                Color.argb(UiTheme.isDark(context) ? 60 : 38, 13, 148, 136)),
                content, null);
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
