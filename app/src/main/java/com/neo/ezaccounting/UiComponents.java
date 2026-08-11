package com.neo.ezaccounting;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

public final class UiComponents {
    public static final int PAGE_HORIZONTAL_DP = 20;
    public static final int PAGE_TOP_DP = 24;
    public static final int PAGE_BOTTOM_DP = 36;
    public static final int CARD_RADIUS_DP = 16;
    public static final int MIN_TOUCH_DP = 48;
    public static final int CONTROL_HEIGHT_DP = 56;

    private UiComponents() {}

    public static GradientDrawable surface(Context context) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiTheme.surface(context));
        background.setCornerRadius(dp(context, CARD_RADIUS_DP));
        return background;
    }

    public static void stylePrimary(Button button) {
        Context context = button.getContext();
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setMinHeight(dp(context, CONTROL_HEIGHT_DP));
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{UiTheme.accentDark(context), UiTheme.accent(context)});
        background.setCornerRadius(dp(context, 14));
        button.setBackground(background);
    }

    public static void styleSecondary(Button button) {
        Context context = button.getContext();
        button.setAllCaps(false);
        button.setTextColor(UiTheme.primaryText(context));
        button.setMinHeight(dp(context, CONTROL_HEIGHT_DP));
        button.setBackground(surface(context));
    }

    public static void styleIconButton(TextView button) {
        Context context = button.getContext();
        button.setGravity(android.view.Gravity.CENTER);
        button.setMinWidth(dp(context, MIN_TOUCH_DP));
        button.setMinHeight(dp(context, MIN_TOUCH_DP));
        button.setClickable(true);
        button.setFocusable(true);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(UiTheme.softGold(context));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(
                UiTheme.isDark(context) ? 64 : 42, 23, 107, 91)), circle, null));
    }

    public static AlertDialog show(AlertDialog dialog) {
        dialog.show();
        styleDialog(dialog);
        return dialog;
    }

    public static void styleDialog(AlertDialog dialog) {
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window != null) {
            GradientDrawable background = surface(dialog.getContext());
            window.setBackgroundDrawable(background);
            window.getDecorView().setPadding(dp(dialog.getContext(), 8), 0,
                    dp(dialog.getContext(), 8), 0);
        }
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE));
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE));
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_NEUTRAL));
    }

    private static void styleDialogButton(Button button) {
        if (button == null) return;
        button.setAllCaps(false);
        button.setTextColor(UiTheme.accent(button.getContext()));
        button.setMinHeight(dp(button.getContext(), MIN_TOUCH_DP));
        button.setMinWidth(dp(button.getContext(), MIN_TOUCH_DP));
        ViewGroup.LayoutParams params = button.getLayoutParams();
        if (params != null && params.height > 0 && params.height < dp(button.getContext(), MIN_TOUCH_DP)) {
            params.height = dp(button.getContext(), MIN_TOUCH_DP);
            button.setLayoutParams(params);
        }
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
