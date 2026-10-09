package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CloudflareDiagnosticManagerTest {
    @Test
    public void acceptsPublicIpLiteralsAndRejectsUnsafeTargets() {
        assertEquals("1.1.1.1", CloudflareDiagnosticManager.normalizePublicIp("1.1.1.1"));
        assertNotNull(CloudflareDiagnosticManager.normalizePublicIp("2606:4700:4700::1111"));
        assertNull(CloudflareDiagnosticManager.normalizePublicIp("cloudflare.com"));
        assertNull(CloudflareDiagnosticManager.normalizePublicIp("1.2.3"));
        assertNull(CloudflareDiagnosticManager.normalizePublicIp("999.1.1.1"));
        assertNull(CloudflareDiagnosticManager.normalizePublicIp("127.0.0.1"));
        assertNull(CloudflareDiagnosticManager.normalizePublicIp("192.168.1.1"));
        assertNull(CloudflareDiagnosticManager.normalizePublicIp("100.64.0.1"));
        assertNull(CloudflareDiagnosticManager.normalizePublicIp("198.18.0.1"));
        assertNull(CloudflareDiagnosticManager.normalizePublicIp("2001:db8::1"));
    }

    @Test
    public void conclusionRequiresStableMaterialImprovement() {
        CloudflareDiagnosticManager.Probe normal = probe(true, 3, 1000);
        CloudflareDiagnosticManager.Probe faster = probe(true, 3, 700);
        CloudflareDiagnosticManager.Probe unstable = probe(true, 2, 600);
        CloudflareDiagnosticManager.Probe failed = probe(false, 0, -1);

        assertTrue(CloudflareDiagnosticManager.conclusion(normal, faster)
                .contains("改善 30%"));
        assertTrue(CloudflareDiagnosticManager.conclusion(normal, unstable)
                .contains("波动"));
        assertTrue(CloudflareDiagnosticManager.conclusion(normal, failed)
                .contains("未通过"));
    }

    private CloudflareDiagnosticManager.Probe probe(boolean reachable,
                                                     int successes, long totalMs) {
        return new CloudflareDiagnosticManager.Probe(reachable, reachable,
                "1.1.1.1", "HKG", 10, 30, 60, 100, totalMs, successes,
                reachable ? "" : "failed");
    }
}
