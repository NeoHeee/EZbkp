package com.neo.ezaccounting;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public final class InteractionSettingsPage {
    public interface Listener {
        void onChanged(boolean showQuickActions);
        void onClose();
    }

    private InteractionSettingsPage() {}

    public static View create(Activity activity, boolean showQuickActions, Listener listener) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(UiTheme.background(activity));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, UiComponents.PAGE_HORIZONTAL_DP),
                dp(activity, UiComponents.PAGE_TOP_DP),
                dp(activity, UiComponents.PAGE_HORIZONTAL_DP),
                dp(activity, UiComponents.PAGE_BOTTOM_DP));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView titles = text(activity, "外观与交互\n快捷入口与移动端操作方式", 24,
                UiTheme.primaryText(activity));
        titles.setLineSpacing(dp(activity, 4), 1f);
        header.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = text(activity, "×", 28, UiTheme.secondaryText(activity));
        UiComponents.styleIconButton(close);
        close.setContentDescription("返回设置中心");
        close.setOnClickListener(view -> listener.onClose());
        header.addView(close, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));
        content.addView(header, marginBottom(activity, 24));

        TextView section = text(activity, "快捷中心", 13, UiTheme.secondaryText(activity));
        section.setPadding(dp(activity, 4), 0, 0, dp(activity, 9));
        content.addView(section);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 16), dp(activity, 14),
                dp(activity, 16), dp(activity, 14));
        card.setBackground(UiComponents.surface(activity));

        Switch toggle = new Switch(activity);
        toggle.setText("显示快捷入口");
        toggle.setTextSize(16);
        toggle.setTextColor(UiTheme.primaryText(activity));
        toggle.setChecked(showQuickActions);
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setMinHeight(dp(activity, 56));
        toggle.setContentDescription("控制记账页面右侧的快捷中心入口是否显示");
        card.addView(toggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = text(activity,
                "关闭后仍可双指快速双击记账页面，或长按应用图标重新切换。",
                13, UiTheme.secondaryText(activity));
        hint.setLineSpacing(0, 1.18f);
        hint.setPadding(0, dp(activity, 4), 0, 0);
        card.addView(hint);
        content.addView(card, marginBottom(activity, 24));

        TextView note = text(activity,
                "后续的主题、字体和页面恢复选项会继续收纳在此分组。",
                13, UiTheme.tertiaryText(activity));
        note.setLineSpacing(0, 1.16f);
        content.addView(note);

        toggle.setOnCheckedChangeListener((button, checked) -> listener.onChanged(checked));
        return scroll;
    }

    private static TextView text(Activity activity, String value, float size, int color) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private static LinearLayout.LayoutParams marginBottom(Activity activity, int value) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(activity, value);
        return params;
    }

    private static int dp(Activity activity, float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
