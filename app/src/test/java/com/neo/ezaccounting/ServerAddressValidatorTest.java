package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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
    }
}
