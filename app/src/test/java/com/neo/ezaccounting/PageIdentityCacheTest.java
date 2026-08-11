package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PageIdentityCacheTest {
    @Test
    public void identityIsScopedToTheDetectedUrl() {
        PageIdentityCache cache = new PageIdentityCache();
        cache.update("https://money.example.com/#/home",
                EzBookkeepingPageDetector.PageIdentity.HOME);

        assertTrue(cache.isValidFor("https://money.example.com/#/home"));
        assertEquals(EzBookkeepingPageDetector.PageIdentity.HOME,
                cache.getFor("https://money.example.com/#/home"));
        assertEquals(EzBookkeepingPageDetector.PageIdentity.UNKNOWN,
                cache.getFor("https://money.example.com/#/transactions"));
    }

    @Test
    public void invalidationClearsUrlAndIdentityTogether() {
        PageIdentityCache cache = new PageIdentityCache();
        cache.update("https://money.example.com", EzBookkeepingPageDetector.PageIdentity.HOME);
        cache.invalidate();

        assertFalse(cache.isValidFor("https://money.example.com"));
        assertEquals(EzBookkeepingPageDetector.PageIdentity.UNKNOWN, cache.get());
    }
}
