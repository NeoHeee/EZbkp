package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class FastStartPolicyTest {
    @Test
    public void autoModeUsesLastSuccessfulLocalRoute() {
        FastStartPolicy.Candidate candidate = FastStartPolicy.select(RouteMode.AUTO,
                "http://local", "https://remote", "http://local");
        assertNotNull(candidate);
        assertEquals(RouteManager.TYPE_LOCAL, candidate.type);
        assertEquals("http://local", candidate.url);
    }

    @Test
    public void autoModePrefersMatchedLocalOverLastPublicRoute() {
        FastStartPolicy.Candidate candidate = FastStartPolicy.select(RouteMode.AUTO,
                "http://local", "https://remote", "https://remote");
        assertNotNull(candidate);
        assertEquals(RouteManager.TYPE_LOCAL, candidate.type);
    }

    @Test
    public void singleConfiguredRouteStartsImmediately() {
        FastStartPolicy.Candidate local = FastStartPolicy.select(RouteMode.AUTO,
                "http://local", "", "");
        FastStartPolicy.Candidate remote = FastStartPolicy.select(RouteMode.AUTO,
                "", "https://remote", "");
        assertNotNull(local);
        assertNotNull(remote);
        assertEquals(RouteManager.TYPE_LOCAL, local.type);
        assertEquals(RouteManager.TYPE_PUBLIC, remote.type);
    }

    @Test
    public void fixedModeDoesNotNeedSuccessfulHistory() {
        FastStartPolicy.Candidate local = FastStartPolicy.select(RouteMode.LOCAL,
                "http://local", "https://remote", "");
        FastStartPolicy.Candidate remote = FastStartPolicy.select(RouteMode.PUBLIC,
                "http://local", "https://remote", "");
        assertNotNull(local);
        assertNotNull(remote);
        assertEquals(RouteManager.TYPE_LOCAL, local.type);
        assertEquals(RouteManager.TYPE_PUBLIC, remote.type);
    }

    @Test
    public void autoModeWithTwoRoutesStartsMatchedLocalImmediately() {
        assertEquals(RouteManager.TYPE_LOCAL, FastStartPolicy.select(RouteMode.AUTO,
                "http://local", "https://remote", "").type);
    }

    @Test
    public void staleHistoryDoesNotOverrideMatchedLocal() {
        assertEquals(RouteManager.TYPE_LOCAL, FastStartPolicy.select(RouteMode.AUTO,
                "http://new-local", "https://new-remote", "http://old-local").type);
    }
}
