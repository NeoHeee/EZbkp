package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ShortcutActionNamesTest {
    @Test
    public void shortcutActionsStayPackageScopedAndDistinct() {
        assertTrue(ShortcutActions.ROUTES.startsWith("com.neo.ezaccounting.action."));
        assertTrue(ShortcutActions.SECURITY.startsWith("com.neo.ezaccounting.action."));
        assertTrue(ShortcutActions.LOCK.startsWith("com.neo.ezaccounting.action."));
        assertTrue(ShortcutActions.UPDATE.startsWith("com.neo.ezaccounting.action."));
        assertTrue(!ShortcutActions.ROUTES.equals(ShortcutActions.SECURITY));
        assertTrue(!ShortcutActions.LOCK.equals(ShortcutActions.UPDATE));
    }
}
