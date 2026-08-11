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
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

public final class QuickActionsSheet {
    private static final String TAG_ROUTE_VALUE = "quick-actions-route";
    private static final String TAG_LATENCY_VALUE = "quick-actions-latency";
    private static final String TAG_SECURITY_VALUE = "quick-actions-security";
    private static CachedSheet cachedSheet;
    public interface Listener {
        void onHome();
        void onSettings();
        void onLock();
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

        ActionItem(int icon, String title, String description, Runnable action) {
            this.icon = icon;
            this.title = title;
            this.description = description;
            this.action = action;
        }
    }

    private static final class CachedSheet {
        final Activity activity;
        Dialog dialog;
        TextView route;
        TextView latency;
        TextView security;
        Listener listener;

        CachedSheet(Activity activity) {
            this.activity = activity;
        }

        void show(Model model, Listener nextListener) {
            listener = nextListener;
            route.setText(model.route);
            latency.setText(model.latency);
            security.setText(model.security);
            if (!dialog.isShowing()) dialog.show();
        }

        void release() {
            listener = null;
            if (dialog != null) {
                dialog.setOnShowListener(null);
                if (dialog.isShowing()) dialog.dismiss();
            }
        }
    }

    private QuickActionsSheet() {}

    public static void show(Activity activity, Model model, Listener listener) {
        if (activity == null || activity.isFinishing() || listener == null) return;

        if (cachedSheet == null || cachedSheet.activity != activity) {
            release(cachedSheet == null ? null : cachedSheet.activity);
            cachedSheet = create(activity, model);
        }
        cachedSheet.show(model, listener);
    }

    public static void prewarm(Activity activity, Model model) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || model == null) {
            return;
        }
        if (cachedSheet == null || cachedSheet.activity != activity) {
            release(cachedSheet == null ? null : cachedSheet.activity);
            cachedSheet = create(activity, model);
        }
    }

    public static void release(Activity activity) {
        if (cachedSheet == null || (activity != null && cachedSheet.activity != activity)) return;
        cachedSheet.release();
        cachedSheet = null;
    }

    private static CachedSheet create(Activity activity, Model model) {
        CachedSheet sheet = new CachedSheet(activity);

        Dialog dialog = new Dialog(activity);
        sheet.dialog = dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);

        FrameLayout overlay = new FrameLayout(activity);
        overlay.setPadding(dp(activity, 8), dp(activity, 12), dp(activity, 8),
                dp(activity, 8));
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setContentDescription("快捷功能遮罩，点击空白区域关闭");
        overlay.setOnClickListener(view -> dialog.dismiss());

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16),
                dp(activity, 10));
        panel.setBackground(panelBackground(activity));
        panel.setClickable(true);
        panel.setFocusable(true);
        panel.setOnClickListener(view -> { });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            panel.setElevation(dp(activity, 12));
        }

        panel.addView(createHeader(activity, dialog));
        panel.addView(createStatusDashboard(activity, model));
        sheet.route = panel.findViewWithTag(TAG_ROUTE_VALUE);
        sheet.latency = panel.findViewWithTag(TAG_LATENCY_VALUE);
        sheet.security = panel.findViewWithTag(TAG_SECURITY_VALUE);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(activity, 2));

        addSectionHeading(activity, content, "快捷操作", "常用页面、线路与安全控制");
        addActionRow(activity, content, Arrays.asList(
                new ActionItem(android.R.drawable.ic_menu_view,
                        "回到首页", "返回记账主页面", () -> {
                            if (sheet.listener != null) sheet.listener.onHome();
                        }),
                new ActionItem(android.R.drawable.ic_popup_sync,
                        "刷新当前页面", "重新加载正在查看的网页", () -> {
                            boolean refreshed = WebViewController.reloadActive();
                            Toast.makeText(activity,
                                    refreshed ? "正在刷新当前页面" : "当前页面尚未加载",
                                    Toast.LENGTH_SHORT).show();
                        }),
                new ActionItem(android.R.drawable.ic_lock_lock,
                        "立即上锁", "隐藏账目并重新验证", () -> {
                            if (sheet.listener != null) sheet.listener.onLock();
                        }),
                new ActionItem(android.R.drawable.ic_menu_preferences,
                        "设置中心", "查看全部设置与维护工具", () -> {
                            if (sheet.listener != null) sheet.listener.onSettings();
                        })
        ), dialog);

        panel.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = text(activity, "更多选项已收纳至设置中心", 12,
                UiTheme.tertiaryText(activity));
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(activity, 4), 0, 0);
        panel.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int width = Math.min(metrics.widthPixels - dp(activity, 16), dp(activity, 600));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                width, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        overlay.addView(panel, panelParams);

        dialog.setContentView(overlay);
        dialog.setOnShowListener(ignored -> {
            configureWindow(activity, dialog);
            animateIn(panel);
        });
        return sheet;
    }

    private static View createHeader(Context context, Dialog dialog) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 2), 0, 0, dp(context, 8));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(context, "快捷中心", 21, UiTheme.primaryText(context));
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
        row.addView(close, new LinearLayout.LayoutParams(dp(context, 42), dp(context, 42)));
        return row;
    }

    private static View createStatusDashboard(Context context, Model model) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(context, 10), dp(context, 9), dp(context, 10),
                dp(context, 9));
        panel.setBackground(statusBackground(context));

        TextView label = text(context, "当前状态", 12, UiTheme.secondaryText(context));
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(label);

        LinearLayout statusRow = new LinearLayout(context);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setPadding(0, dp(context, 6), 0, 0);
        statusRow.addView(statusCell(context, "当前线路", model.route, TAG_ROUTE_VALUE),
                statusCellParams(context, 0));
        statusRow.addView(statusCell(context, "最近延迟", model.latency, TAG_LATENCY_VALUE),
                statusCellParams(context, 1));
        statusRow.addView(statusCell(context, "安全保护", model.security, TAG_SECURITY_VALUE),
                statusCellParams(context, 2));
        panel.addView(statusRow);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(context, 8);
        panel.setLayoutParams(params);
        return panel;
    }

    private static View statusCell(Context context, String label, String value, String valueTag) {
        LinearLayout cell = new LinearLayout(context);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(dp(context, 10), dp(context, 7), dp(context, 10), dp(context, 7));
        cell.setBackground(statusCellBackground(context));

        TextView small = text(context, label, 11, UiTheme.tertiaryText(context));
        TextView main = text(context, value, 14, UiTheme.primaryText(context));
        main.setTag(valueTag);
        main.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        main.setPadding(0, dp(context, 2), 0, 0);
        main.setMaxLines(1);
        cell.addView(small);
        cell.addView(main);
        return cell;
    }

    private static LinearLayout.LayoutParams statusCellParams(Context context, int index) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (index > 0) params.leftMargin = dp(context, 3);
        if (index < 2) params.rightMargin = dp(context, 3);
        return params;
    }

    private static void addSectionHeading(Context context, LinearLayout parent,
                                          String title, String subtitle) {
        LinearLayout heading = new LinearLayout(context);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(context, 2), dp(context, 7), 0, dp(context, 5));
        TextView name = text(context, title, 14, UiTheme.primaryText(context));
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView detail = text(context, subtitle, 11, UiTheme.tertiaryText(context));
        detail.setPadding(0, dp(context, 2), 0, 0);
        heading.addView(name);
        heading.addView(detail);
        parent.addView(heading);
    }

    private static void addActionRow(Context context, LinearLayout parent,
                                     List<ActionItem> items, Dialog dialog) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        for (int index = 0; index < items.size(); index++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (index > 0) params.leftMargin = dp(context, 3);
            if (index < items.size() - 1) params.rightMargin = dp(context, 3);
            row.addView(createCompactAction(context, items.get(index), dialog), params);
        }
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static View createCompactAction(Context context, ActionItem item, Dialog dialog) {
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(dp(context, 4), dp(context, 8), dp(context, 4), dp(context, 7));
        tile.setMinimumHeight(dp(context, 78));
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setBackground(ripple(context, tileBackground(context)));
        tile.setContentDescription(item.title + "，" + item.description);
        tile.setOnClickListener(view -> {
            dialog.dismiss();
            item.action.run();
        });

        ImageView icon = new ImageView(context);
        icon.setImageResource(item.icon);
        icon.setColorFilter(UiTheme.accent(context));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(context, 7), dp(context, 7), dp(context, 7), dp(context, 7));
        icon.setBackground(iconBackground(context));
        icon.setContentDescription(null);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(context, 34), dp(context, 34)));

        TextView title = text(context, item.title, 11.5f, UiTheme.primaryText(context));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(0, dp(context, 5), 0, 0);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);
        tile.addView(title);
        return tile;
    }

    private static void configureWindow(Activity activity, Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.setGravity(Gravity.CENTER);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = UiTheme.isDark(activity) ? 0.58f : 0.46f;
        attributes.width = WindowManager.LayoutParams.MATCH_PARENT;
        attributes.height = WindowManager.LayoutParams.MATCH_PARENT;
        window.setAttributes(attributes);
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT);
        window.setNavigationBarColor(UiTheme.background(activity));
        window.setStatusBarColor(UiTheme.background(activity));
    }

    private static void animateIn(View panel) {
        panel.animate().cancel();
        panel.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        panel.setAlpha(0f);
        panel.setTranslationY(dp(panel.getContext(), 32));
        panel.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(160L)
                .withEndAction(() -> panel.setLayerType(View.LAYER_TYPE_NONE, null))
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
        background.setColor(UiTheme.softGold(context));
        background.setCornerRadius(dp(context, 18));
        background.setStroke(dp(context, 1), UiTheme.gold(context));
        return background;
    }

    private static GradientDrawable statusCellBackground(Context context) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiTheme.surface(context));
        background.setCornerRadius(dp(context, 13));
        return background;
    }

    private static GradientDrawable tileBackground(Context context) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiTheme.surface(context));
        background.setStroke(dp(context, 1), UiTheme.gold(context));
        background.setCornerRadius(dp(context, 16));
        return background;
    }

    private static GradientDrawable iconBackground(Context context) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiTheme.softGold(context));
        background.setCornerRadius(dp(context, 12));
        return background;
    }

    private static GradientDrawable circleBackground(Context context) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(UiTheme.softGold(context));
        return background;
    }

    private static RippleDrawable ripple(Context context, GradientDrawable content) {
        return new RippleDrawable(ColorStateList.valueOf(
                Color.argb(UiTheme.isDark(context) ? 64 : 42, 23, 107, 91)),
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

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
