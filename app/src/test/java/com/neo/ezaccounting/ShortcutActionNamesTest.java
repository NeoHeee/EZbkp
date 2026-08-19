package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ShortcutActionNamesTest {
    @Test
    public void shortcutActionsStayPackageScopedAndDistinct() {
        assertTrue(ShortcutActions.SETTINGS.startsWith("com.neo.ezaccounting.action."));
    }
}
