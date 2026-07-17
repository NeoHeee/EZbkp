package com.neo.ezaccounting;

import android.content.Intent;

public final class ShortcutActions {
    public static final String ROUTES = "com.neo.ezaccounting.action.ROUTES";
    public static final String SECURITY = "com.neo.ezaccounting.action.SECURITY";
    public static final String LOCK = "com.neo.ezaccounting.action.LOCK";
    public static final String UPDATE = "com.neo.ezaccounting.action.UPDATE";

    private ShortcutActions() {}

    public static String read(Intent intent) {
        if (intent == null) return null;
        String action = intent.getAction();
        if (ROUTES.equals(action) || SECURITY.equals(action) || LOCK.equals(action) || UPDATE.equals(action)) {
            return action;
        }
        return null;
    }
}
