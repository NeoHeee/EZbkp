package com.neo.ezaccounting;

import android.content.Intent;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ShortcutActionsTest {
    @Test
    public void acceptsKnownShortcutAction() {
        Intent intent = new Intent(ShortcutActions.UPDATE);
        assertEquals(ShortcutActions.UPDATE, ShortcutActions.read(intent));
    }

    @Test
    public void rejectsUnknownAction() {
        assertNull(ShortcutActions.read(new Intent("example.UNKNOWN")));
    }
}
