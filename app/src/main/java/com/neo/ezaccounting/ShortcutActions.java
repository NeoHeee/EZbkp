package com.neo.ezaccounting;

import android.content.Intent;

public final class ShortcutActions {
    public static final String SETTINGS = "com.neo.ezaccounting.action.SETTINGS";

    private ShortcutActions() {}

    public static String read(Intent intent) {
        if (intent == null) return null;
        String action = intent.getAction();
        return SETTINGS.equals(action) ? action : null;
    }
}
