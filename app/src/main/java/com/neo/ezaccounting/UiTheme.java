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
        return isDark(context) ? Color.rgb(16, 22, 23) : Color.rgb(245, 248, 248);
    }

    public static int surface(Context context) {
        return isDark(context) ? Color.rgb(27, 36, 37) : Color.WHITE;
    }

    public static int primaryText(Context context) {
        return isDark(context) ? Color.rgb(232, 241, 240) : Color.rgb(20, 35, 35);
    }

    public static int secondaryText(Context context) {
        return isDark(context) ? Color.rgb(174, 191, 189) : Color.rgb(80, 95, 95);
    }

    public static int tertiaryText(Context context) {
        return isDark(context) ? Color.rgb(133, 153, 151) : Color.rgb(105, 120, 120);
    }

    public static int border(Context context) {
        return isDark(context) ? Color.rgb(65, 84, 82) : Color.rgb(196, 210, 208);
    }

    public static int accent(Context context) {
        return isDark(context) ? Color.rgb(45, 212, 191) : Color.rgb(13, 148, 136);
    }

    public static int webBackground(Context context) {
        return isDark(context) ? Color.rgb(16, 22, 23) : Color.WHITE;
    }
}
