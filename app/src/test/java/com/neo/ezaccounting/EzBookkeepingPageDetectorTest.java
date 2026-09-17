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
    public void cachedIdentityTakesPriorityAndUnknownFallsBackToUrl() {
        assertTrue(EzBookkeepingPageDetector.resolveHome(
                EzBookkeepingPageDetector.PageIdentity.HOME, false));
        assertFalse(EzBookkeepingPageDetector.resolveHome(
                EzBookkeepingPageDetector.PageIdentity.OTHER, true));
        assertTrue(EzBookkeepingPageDetector.resolveHome(
                EzBookkeepingPageDetector.PageIdentity.UNKNOWN, true));
        assertFalse(EzBookkeepingPageDetector.resolveHome(null, false));
    }

    @Test
    public void scriptUsesRouterAndVersionCompatibleHomeMarkers() {
        String script = EzBookkeepingPageDetector.homeDetectionScript();
        assertTrue(script.contains("getElementById('main-view')"));
        assertTrue(script.contains("querySelectorAll('.page-current')"));
        assertTrue(script.contains("closest('.view')===view"));
        assertTrue(script.contains("view.f7View.router.currentRoute"));
        assertTrue(script.contains("page.f7Page.route"));
        assertTrue(script.contains("path.substring(hashAt+1)"));
        assertTrue(script.contains("new URL(path,location.href).pathname"));
        assertTrue(script.contains("path==='/')"));
        assertTrue(script.contains(".main-tabbar"));
        assertTrue(script.contains("#homepage-add-button"));
        assertTrue(script.contains(".home-summary-card"));
        assertTrue(script.contains(".overview-transaction-list"));
        assertTrue(script.contains("hasLegacyHomeWidgets?'home':'other'"));
        assertTrue(script.contains("return 'unknown'"));
        assertFalse(script.contains("document.querySelector('.page-current')"));
    }
}
