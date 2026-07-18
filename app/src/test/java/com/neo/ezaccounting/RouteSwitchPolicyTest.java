package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouteSwitchPolicyTest {
    private RouteManager.ProbeResult route(String url, int type, boolean reachable,
                                           long latency) {
        return new RouteManager.ProbeResult(url, type, reachable, latency,
                reachable ? 200 : 0);
    }

    private RouteManager.Selection selection(RouteManager.ProbeResult selected,
                                             RouteManager.ProbeResult local,
                                             RouteManager.ProbeResult remote) {
        return new RouteManager.Selection(selected, local, remote);
    }

    @Test
    public void firstUnavailableProbeDoesNotSwitch() {
        RouteSwitchPolicy policy = new RouteSwitchPolicy();
        RouteManager.ProbeResult local = route("http://local", RouteManager.TYPE_LOCAL,
                false, 1700);
        RouteManager.ProbeResult remote = route("https://remote", RouteManager.TYPE_PUBLIC,
                true, 100);
        RouteSwitchPolicy.Decision decision = policy.evaluate(RouteManager.TYPE_LOCAL,
                selection(remote, local, remote), 10_000L, true);
        assertFalse(decision.shouldSwitch);
        assertEquals(1, policy.failureCount(RouteManager.TYPE_LOCAL));
    }

    @Test
    public void secondUnavailableProbeSwitchesToAlternate() {
        RouteSwitchPolicy policy = new RouteSwitchPolicy();
        RouteManager.ProbeResult local = route("http://local", RouteManager.TYPE_LOCAL,
                false, 1700);
        RouteManager.ProbeResult remote = route("https://remote", RouteManager.TYPE_PUBLIC,
                true, 100);
        RouteManager.Selection selection = selection(remote, local, remote);
        policy.evaluate(RouteManager.TYPE_LOCAL, selection, 10_000L, true);
        RouteSwitchPolicy.Decision decision = policy.evaluate(RouteManager.TYPE_LOCAL,
                selection, 12_000L, true);
        assertTrue(decision.shouldSwitch);
        assertEquals(remote, decision.target);
    }

    @Test
    public void twoPageFailuresSwitchEvenWhenProbePrefersCurrentRoute() {
        RouteSwitchPolicy policy = new RouteSwitchPolicy();
        RouteManager.ProbeResult local = route("http://local", RouteManager.TYPE_LOCAL,
                true, 40);
        RouteManager.ProbeResult remote = route("https://remote", RouteManager.TYPE_PUBLIC,
                true, 100);
        policy.recordFailure(RouteManager.TYPE_LOCAL);
        policy.recordFailure(RouteManager.TYPE_LOCAL);
        RouteSwitchPolicy.Decision decision = policy.evaluate(RouteManager.TYPE_LOCAL,
                selection(local, local, remote), 20_000L, true);
        assertTrue(decision.shouldSwitch);
        assertEquals(remote, decision.target);
    }

    @Test
    public void cooldownBlocksPerformanceOnlySwitch() {
        RouteSwitchPolicy policy = new RouteSwitchPolicy();
        policy.recordSwitch(10_000L);
        RouteManager.ProbeResult local = route("http://local", RouteManager.TYPE_LOCAL,
                true, 600);
        RouteManager.ProbeResult remote = route("https://remote", RouteManager.TYPE_PUBLIC,
                true, 90);
        RouteSwitchPolicy.Decision decision = policy.evaluate(RouteManager.TYPE_LOCAL,
                selection(remote, local, remote), 30_000L, true);
        assertFalse(decision.shouldSwitch);
    }

    @Test
    public void clearlyFasterRouteSwitchesAfterCooldown() {
        RouteSwitchPolicy policy = new RouteSwitchPolicy();
        policy.recordSwitch(10_000L);
        RouteManager.ProbeResult local = route("http://local", RouteManager.TYPE_LOCAL,
                true, 600);
        RouteManager.ProbeResult remote = route("https://remote", RouteManager.TYPE_PUBLIC,
                true, 90);
        RouteSwitchPolicy.Decision decision = policy.evaluate(RouteManager.TYPE_LOCAL,
                selection(remote, local, remote), 80_001L, true);
        assertTrue(decision.shouldSwitch);
        assertEquals(remote, decision.target);
    }
}
