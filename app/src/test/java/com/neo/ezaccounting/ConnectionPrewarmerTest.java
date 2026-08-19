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
}
