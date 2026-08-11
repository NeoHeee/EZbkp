package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HomeRouteResolverTest {
    @Test
    public void currentOriginSelectsMatchingConfiguredHome() {
        assertEquals("http://192.168.1.10:8080",
                HomeRouteResolver.resolve("http://192.168.1.10:8080/accounts",
                        "https://money.example.com", "http://192.168.1.10:8080",
                        "https://money.example.com"));
    }

    @Test
    public void lastRouteIsFallbackWhenCurrentUrlIsUnavailable() {
        assertEquals("https://money.example.com",
                HomeRouteResolver.resolve(null, "https://money.example.com",
                        "", "https://backup.example.com"));
    }

    @Test
    public void configuredStateIgnoresWhitespace() {
        assertFalse(HomeRouteResolver.hasConfiguredRoute(" ", null));
        assertTrue(HomeRouteResolver.hasConfiguredRoute("", "https://money.example.com"));
    }
}
