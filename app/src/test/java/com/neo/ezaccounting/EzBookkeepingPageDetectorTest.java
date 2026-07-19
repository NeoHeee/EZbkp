package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EzBookkeepingPageDetectorTest {
    @Test
    public void parsesTriStateJavascriptResults() {
        assertEquals(EzBookkeepingPageDetector.PageIdentity.HOME,
                EzBookkeepingPageDetector.parseIdentity("home"));
        assertEquals(EzBookkeepingPageDetector.PageIdentity.HOME,
                EzBookkeepingPageDetector.parseIdentity("\"home\""));
        assertEquals(EzBookkeepingPageDetector.PageIdentity.OTHER,
                EzBookkeepingPageDetector.parseIdentity("other"));
        assertEquals(EzBookkeepingPageDetector.PageIdentity.UNKNOWN,
                EzBookkeepingPageDetector.parseIdentity("unknown"));
        assertEquals(EzBookkeepingPageDetector.PageIdentity.UNKNOWN,
                EzBookkeepingPageDetector.parseIdentity("false"));
        assertEquals(EzBookkeepingPageDetector.PageIdentity.UNKNOWN,
                EzBookkeepingPageDetector.parseIdentity(null));
    }

    @Test
    public void compatibilityHelperOnlyAcceptsHome() {
        assertTrue(EzBookkeepingPageDetector.isHomeResult("\"home\""));
        assertFalse(EzBookkeepingPageDetector.isHomeResult("\"other\""));
        assertFalse(EzBookkeepingPageDetector.isHomeResult("\"unknown\""));
    }

    @Test
    public void scriptTargetsOnlyCurrentPageOwnedByMainView() {
        String script = EzBookkeepingPageDetector.homeDetectionScript();
        assertTrue(script.contains("getElementById('main-view')"));
        assertTrue(script.contains("querySelectorAll('.page-current')"));
        assertTrue(script.contains("closest('.view')===view"));
        assertTrue(script.contains(".home-summary-card"));
        assertTrue(script.contains(".overview-transaction-list"));
        assertTrue(script.contains("?'home':'other'"));
        assertTrue(script.contains("return 'unknown'"));
        assertFalse(script.contains("document.querySelector('.page-current')"));
    }
}
