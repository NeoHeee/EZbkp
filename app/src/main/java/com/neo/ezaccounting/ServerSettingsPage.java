package com.neo.ezaccounting;

import android.app.Activity;
import android.graphics.Color;
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

public final class ServerSettingsPage {
    public interface Listener {
        void onSaved(String localUrl, String publicUrl);
    }

    private ServerSettingsPage() {}

    public static View create(Activity activity, String localUrl, String publicUrl,
                              Listener listener) {
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(UiTheme.background(activity));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(activity, 28), dp(activity, 42), dp(activity, 28), dp(activity, 28));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView logo = new TextView(activity);
        logo.setText("¥✓");
        logo.setTextSize(29);
        logo.setTextColor(Color.WHITE);
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable logoBackground = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(15, 118, 110), Color.rgb(14, 165, 164)});
        logoBackground.setCornerRadius(dp(activity, 24));
        logo.setBackground(logoBackground);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(
                dp(activity, 84), dp(activity, 84));
        logoParams.bottomMargin = dp(activity, 24);
        content.addView(logo, logoParams);

        TextView title = text(activity, "配置 ezBookkeeping 地址", 24,
                UiTheme.primaryText(activity), true);
        title.setGravity(Gravity.CENTER);
        content.addView(title, fullWrap(activity, 10));

        TextView description = text(activity,
                "可同时填写本地地址和公网地址。自动模式会并行测速，并在满足防抖条件后切换线路。\n\n" +
                        "建议：\n本地地址填写 NAS 局域网地址\n公网地址填写反向代理 HTTPS 地址",
                14.5f, UiTheme.secondaryText(activity), false);
        description.setGravity(Gravity.CENTER);
        description.setLineSpacing(0, 1.18f);
        content.addView(description, fullWrap(activity, 24));

        content.addView(text(activity, "本地地址", 14.5f,
                UiTheme.primaryText(activity), true), fullWrap(activity, 8));
        EditText localInput = input(activity, "http://192.168.1.100:8080", localUrl);
        content.addView(localInput, fullWrap(activity, 16));

        content.addView(text(activity, "公网地址", 14.5f,
                UiTheme.primaryText(activity), true), fullWrap(activity, 8));
        EditText publicInput = input(activity, "https://money.example.com", publicUrl);
        content.addView(publicInput, fullWrap(activity, 18));

        Button save = new Button(activity);
        save.setText("保存并连接");
        save.setTextSize(16);
        save.setTextColor(Color.WHITE);
        save.setAllCaps(false);
        GradientDrawable saveBackground = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(15, 118, 110), Color.rgb(13, 148, 136)});
        saveBackground.setCornerRadius(dp(activity, 14));
        save.setBackground(saveBackground);
        content.addView(save, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 52)));

        TextView note = text(activity,
                "进入记账界面后不会显示额外顶部栏。\n可在快捷中心刷新页面；双指快速双击可打开隐藏菜单。\n" +
                        "HTTPS证书无效时会阻止连接。地址和安全设置只保存在本机。",
                12.5f, UiTheme.tertiaryText(activity), false);
        note.setGravity(Gravity.CENTER);
        note.setLineSpacing(0, 1.15f);
        content.addView(note, fullWrap(activity, 22));

        save.setOnClickListener(v -> {
            String localRaw = localInput.getText().toString();
            String publicRaw = publicInput.getText().toString();
            if (blank(localRaw) && blank(publicRaw)) {
                localInput.setError("请至少填写一个地址");
                return;
            }
            String normalizedLocal = ServerAddressValidator.normalize(localRaw);
            String normalizedPublic = ServerAddressValidator.normalize(publicRaw);
            if (!blank(localRaw) && normalizedLocal == null) {
                localInput.setError("请输入有效的 HTTP 或 HTTPS 地址");
                return;
            }
            if (!blank(publicRaw) && normalizedPublic == null) {
                publicInput.setError("请输入有效的 HTTP 或 HTTPS 地址");
                return;
            }
            listener.onSaved(normalizedLocal == null ? "" : normalizedLocal,
                    normalizedPublic == null ? "" : normalizedPublic);
        });
        return scrollView;
    }

    private static EditText input(Activity activity, String hint, String value) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setText(value == null ? "" : value);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setTextSize(16);
        input.setTextColor(UiTheme.primaryText(activity));
        input.setHintTextColor(UiTheme.tertiaryText(activity));
        input.setPadding(dp(activity, 16), dp(activity, 13),
                dp(activity, 16), dp(activity, 13));
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiTheme.surface(activity));
        background.setStroke(dp(activity, 1), UiTheme.border(activity));
        background.setCornerRadius(dp(activity, 14));
        input.setBackground(background);
        return input;
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

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
