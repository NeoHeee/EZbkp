package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ServerAddressValidatorTest {
    @Test
    public void privateAddressDefaultsToHttp() {
        assertEquals("http://192.168.1.20:8080",
                ServerAddressValidator.normalize("192.168.1.20:8080/"));
    }

    @Test
    public void publicHostDefaultsToHttps() {
        assertEquals("https://money.example.com",
                ServerAddressValidator.normalize("money.example.com/"));
    }

    @Test
    public void preservesExplicitSchemeAndPath() {
        assertEquals("https://money.example.com/ez",
                ServerAddressValidator.normalize("https://money.example.com/ez/"));
    }

    @Test
    public void rejectsUnsupportedOrBrokenAddress() {
        assertNull(ServerAddressValidator.normalize("ftp://money.example.com"));
        assertNull(ServerAddressValidator.normalize("http://"));
    }

    @Test
    public void recognizesPrivateHostForms() {
        assertTrue(ServerAddressValidator.isPrivateHost("nas.local:8080"));
        assertTrue(ServerAddressValidator.isPrivateHost("10.0.0.2"));
        assertTrue(ServerAddressValidator.isPrivateHost("172.20.1.3"));
        assertTrue(ServerAddressValidator.isPrivateHost("fd00::20"));
        assertFalse(ServerAddressValidator.isPrivateHost("2606:4700:3031::6815:3cae"));
    }

    @Test
    public void cleartextIsLimitedToLocalRules() {
        assertEquals("http://192.168.1.20:8080",
                ServerAddressValidator.normalizeLocal("http://192.168.1.20:8080"));
        assertEquals("https://money.example.com",
                ServerAddressValidator.normalizeLocal("https://money.example.com"));
        assertNull(ServerAddressValidator.normalizeLocal("http://money.example.com"));
    }

    @Test
    public void publicRouteRequiresHttps() {
        assertEquals("https://money.example.com",
                ServerAddressValidator.normalizePublic("money.example.com"));
        assertNull(ServerAddressValidator.normalizePublic("http://money.example.com"));
        assertNull(ServerAddressValidator.normalizePublic("http://192.168.1.20"));
    }
}
