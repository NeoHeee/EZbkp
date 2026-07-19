package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EzBookkeepingPageDetectorTest {
    @Test
    public void parsesJavascriptBooleanResults() {
        assertTrue(EzBookkeepingPageDetector.isHomeResult("true"));
        assertTrue(EzBookkeepingPageDetector.isHomeResult("\"true\""));
        assertFalse(EzBookkeepingPageDetector.isHomeResult("false"));
        assertFalse(EzBookkeepingPageDetector.isHomeResult("null"));
        assertFalse(EzBookkeepingPageDetector.isHomeResult(null));
    }

    @Test
    public void scriptTargetsOfficialMobileHomeComponents() {
        String script = EzBookkeepingPageDetector.homeDetectionScript();
        assertTrue(script.contains(".page-current"));
        assertTrue(script.contains(".home-summary-card"));
        assertTrue(script.contains(".overview-transaction-list"));
    }
}
