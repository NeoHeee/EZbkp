package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class FastStartPolicyTest {
    @Test
    public void autoModeUsesLastSuccessfulLocalRoute() {
        FastStartPolicy.Candidate candidate = FastStartPolicy.select(
                "http://local", "https://remote");
        assertNotNull(candidate);
        assertEquals(RouteManager.TYPE_LOCAL, candidate.type);
        assertEquals("http://local", candidate.url);
    }

    @Test
    public void autoModePrefersMatchedLocalOverLastPublicRoute() {
        FastStartPolicy.Candidate candidate = FastStartPolicy.select(
                "http://local", "https://remote");
        assertNotNull(candidate);
        assertEquals(RouteManager.TYPE_LOCAL, candidate.type);
    }

    @Test
    public void singleConfiguredRouteStartsImmediately() {
        FastStartPolicy.Candidate local = FastStartPolicy.select("http://local", "");
        FastStartPolicy.Candidate remote = FastStartPolicy.select("", "https://remote");
        assertNotNull(local);
        assertNotNull(remote);
        assertEquals(RouteManager.TYPE_LOCAL, local.type);
        assertEquals(RouteManager.TYPE_PUBLIC, remote.type);
    }

    @Test
    public void autoModeWithTwoRoutesStartsMatchedLocalImmediately() {
        assertEquals(RouteManager.TYPE_LOCAL, FastStartPolicy.select(
                "http://local", "https://remote").type);
    }

    @Test
    public void staleHistoryDoesNotOverrideMatchedLocal() {
        assertEquals(RouteManager.TYPE_LOCAL, FastStartPolicy.select(
                "http://new-local", "https://new-remote").type);
    }
}
