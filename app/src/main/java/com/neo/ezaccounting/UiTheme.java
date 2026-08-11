package com.neo.ezaccounting;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;

public final class UiTheme {
    private UiTheme() {}

    public static boolean isDark(Context context) {
        int mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static void applySystemBars(Activity activity) {
        boolean dark = isDark(activity);
        Window window = activity.getWindow();
        window.setStatusBarColor(background(activity));
        window.setNavigationBarColor(background(activity));
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (!dark && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (!dark && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    public static int background(Context context) {
        return isDark(context) ? Color.rgb(16, 22, 23) : Color.rgb(244, 240, 230);
    }

    public static int surface(Context context) {
        return isDark(context) ? Color.rgb(27, 36, 37) : Color.rgb(252, 250, 244);
    }

    public static int primaryText(Context context) {
        return isDark(context) ? Color.rgb(232, 241, 240) : Color.rgb(20, 35, 35);
    }

    public static int secondaryText(Context context) {
        return isDark(context) ? Color.rgb(174, 191, 189) : Color.rgb(72, 82, 77);
    }

    public static int tertiaryText(Context context) {
        return isDark(context) ? Color.rgb(133, 153, 151) : Color.rgb(96, 106, 100);
    }

    public static int border(Context context) {
        return isDark(context) ? Color.rgb(65, 84, 82) : Color.rgb(222, 213, 194);
    }

    public static int accent(Context context) {
        return isDark(context) ? Color.rgb(72, 198, 172) : Color.rgb(23, 107, 91);
    }

    public static int accentDark(Context context) {
        return isDark(context) ? Color.rgb(23, 107, 91) : Color.rgb(16, 60, 54);
    }

    public static int gold(Context context) {
        return isDark(context) ? Color.rgb(242, 184, 75) : Color.rgb(184, 126, 18);
    }

    public static int softAccent(Context context) {
        return isDark(context) ? Color.rgb(23, 63, 59) : Color.rgb(231, 240, 235);
    }

    public static int softGold(Context context) {
        return isDark(context) ? Color.rgb(66, 54, 29) : Color.rgb(250, 240, 211);
    }

    public static int danger(Context context) {
        return isDark(context) ? Color.rgb(248, 113, 113) : Color.rgb(190, 24, 34);
    }

    public static int webBackground(Context context) {
        return isDark(context) ? Color.rgb(16, 22, 23) : Color.WHITE;
    }
}
