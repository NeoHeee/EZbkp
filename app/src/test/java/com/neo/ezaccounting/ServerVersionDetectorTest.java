package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ServerVersionDetectorTest {
    @Test
    public void parsesJavascriptStringAndNormalizesPrefix() {
        assertEquals("0.9.2", ServerVersionDetector.parseVersion("\"0.9.2\""));
        assertEquals("1.0.0-beta.1", ServerVersionDetector.parseVersion("\"v1.0.0-beta.1\""));
    }

    @Test
    public void rejectsMissingOrUnsafeValues() {
        assertNull(ServerVersionDetector.parseVersion("\"\""));
        assertNull(ServerVersionDetector.parseVersion("\"<script>\""));
        assertNull(ServerVersionDetector.parseVersion(null));
    }

    @Test
    public void parsesOfficialApiEnvelope() {
        assertEquals("0.9.2", ServerVersionDetector.parseApiResponse(
                "{\"success\":true,\"result\":{\"version\":\"0.9.2\"}}"));
        assertNull(ServerVersionDetector.parseApiResponse(
                "{\"success\":false,\"result\":{\"version\":\"0.9.2\"}}"));
        assertEquals("0.8.0", ServerVersionDetector.parseApiResponse(
                "{\"result\":{\"version\":\"0.8.0\"}}"));
        assertTrue(ServerVersionDetector.API_PATH.endsWith("/systems/version.json"));
    }
}
