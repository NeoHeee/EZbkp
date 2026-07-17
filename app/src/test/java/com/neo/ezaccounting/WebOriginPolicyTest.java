package com.neo.ezaccounting;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WebOriginPolicyTest {
    @Test
    public void defaultAndExplicitPortsAreEquivalent() {
        assertTrue(WebOriginPolicy.isSameOrigin(
                "https://money.example.com", "https://money.example.com:443/accounts"));
        assertTrue(WebOriginPolicy.isSameOrigin(
                "http://192.168.1.10", "http://192.168.1.10:80/"));
    }

    @Test
    public void schemeAndPortArePartOfOrigin() {
        assertFalse(WebOriginPolicy.isSameOrigin(
                "https://money.example.com", "http://money.example.com"));
        assertFalse(WebOriginPolicy.isSameOrigin(
                "https://money.example.com", "https://money.example.com:8443"));
    }

    @Test
    public void differentHostsAreExternal() {
        assertFalse(WebOriginPolicy.isSameOrigin(
                "https://money.example.com", "https://login.example.com"));
    }

    @Test
    public void approvedExternalSchemesAreLimited() {
        assertTrue(WebOriginPolicy.isAllowedExternalScheme("mailto"));
        assertTrue(WebOriginPolicy.isAllowedExternalScheme("TEL"));
        assertFalse(WebOriginPolicy.isAllowedExternalScheme("file"));
    }
}
