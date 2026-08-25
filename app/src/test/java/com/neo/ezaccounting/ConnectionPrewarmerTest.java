package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ConnectionPrewarmerTest {
    @Test
    public void extractsOnlyHttpOrigins() {
        assertEquals("https://money.example.com/",
                ConnectionPrewarmer.origin("https://money.example.com/mobile/home?x=1"));
        assertEquals("http://192.168.1.8:8080/",
                ConnectionPrewarmer.origin("http://192.168.1.8:8080/path"));
        assertNull(ConnectionPrewarmer.origin("javascript:alert(1)"));
        assertNull(ConnectionPrewarmer.origin(""));
    }

    @Test
    public void phaseTimingsAppearInRouteDiagnostics() {
        RouteManager.ProbeResult result = new RouteManager.ProbeResult(
                "https://money.example.com", RouteManager.TYPE_PUBLIC,
                true, 80, 200).withTimings(8, -1, 40, 12);
        String diagnostic = result.diagnostic();
        org.junit.Assert.assertTrue(diagnostic.contains("DNS 8 ms"));
        org.junit.Assert.assertTrue(diagnostic.contains("TLS 40 ms"));
        org.junit.Assert.assertTrue(diagnostic.contains("HTTP 12 ms"));
    }
}
