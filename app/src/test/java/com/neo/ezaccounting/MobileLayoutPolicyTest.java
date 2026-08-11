package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MobileLayoutPolicyTest {
    @Test
    public void narrowScreensUseSingleColumn() {
        assertTrue(MobileLayoutPolicy.useSingleColumn(360f, 1f));
        assertFalse(MobileLayoutPolicy.useSingleColumn(400f, 1f));
    }

    @Test
    public void largeTextUsesSingleColumnAtAnyPhoneWidth() {
        assertTrue(MobileLayoutPolicy.useSingleColumn(430f, 1.25f));
        assertTrue(MobileLayoutPolicy.useSingleColumn(600f, 1.5f));
    }
}
