package com.neo.ezaccounting;

import android.content.Intent;

public final class ShortcutActions {
    public static final String ROUTES = "com.neo.ezaccounting.action.ROUTES";
    public static final String SECURITY = "com.neo.ezaccounting.action.SECURITY";
    public static final String LOCK = "com.neo.ezaccounting.action.LOCK";
    public static final String TOGGLE_QUICK_ACTIONS =
            "com.neo.ezaccounting.action.TOGGLE_QUICK_ACTIONS";

    private ShortcutActions() {}

    public static String read(Intent intent) {
        if (intent == null) return null;
        String action = intent.getAction();
        if (ROUTES.equals(action) || SECURITY.equals(action) || LOCK.equals(action) ||
                TOGGLE_QUICK_ACTIONS.equals(action)) {
            return action;
        }
        return null;
    }
}
