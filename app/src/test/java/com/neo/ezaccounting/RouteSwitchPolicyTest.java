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
    public void firstUnavailableProbeDoesNotSwitchOnSameNetwork() {
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
    public void repeatedUnavailableProbeStillUsesPublic() {
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
    public void networkMismatchImmediatelyUsesEligiblePublicRoute() {
        RouteSwitchPolicy policy = new RouteSwitchPolicy();
        RouteManager.ProbeResult local = route("", RouteManager.TYPE_LOCAL, false, 1);
        RouteManager.ProbeResult remote = route("https://remote", RouteManager.TYPE_PUBLIC,
                true, 100);
        RouteSwitchPolicy.Decision decision = policy.evaluate(RouteManager.TYPE_LOCAL,
                selection(remote, local, remote), 10_000L, false);
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
    public void fasterPublicRouteDoesNotOverrideReachableLocalRoute() {
        RouteSwitchPolicy policy = new RouteSwitchPolicy();
        policy.recordSwitch(10_000L);
        RouteManager.ProbeResult local = route("http://local", RouteManager.TYPE_LOCAL,
                true, 600);
        RouteManager.ProbeResult remote = route("https://remote", RouteManager.TYPE_PUBLIC,
                true, 90);
        RouteSwitchPolicy.Decision decision = policy.evaluate(RouteManager.TYPE_LOCAL,
                selection(remote, local, remote), 80_001L, true);
        assertFalse(decision.shouldSwitch);
    }

    @Test
    public void matchedLocalRouteImmediatelyReplacesPublicRoute() {
        RouteSwitchPolicy policy = new RouteSwitchPolicy();
        RouteManager.ProbeResult local = route("http://local", RouteManager.TYPE_LOCAL,
                true, 300);
        RouteManager.ProbeResult remote = route("https://remote", RouteManager.TYPE_PUBLIC,
                true, 50);
        RouteSwitchPolicy.Decision decision = policy.evaluate(RouteManager.TYPE_PUBLIC,
                selection(local, local, remote), 20_000L, true);
        assertTrue(decision.shouldSwitch);
        assertEquals(local, decision.target);
    }

    @Test
    public void recentLocalFailureTemporarilyHoldsPublicRoute() {
        RouteSwitchPolicy policy = new RouteSwitchPolicy();
        policy.blockLocalRetry(10_000L);
        RouteManager.ProbeResult local = route("http://local", RouteManager.TYPE_LOCAL,
                true, 300);
        RouteManager.ProbeResult remote = route("https://remote", RouteManager.TYPE_PUBLIC,
                true, 50);
        RouteManager.Selection routes = selection(local, local, remote);
        assertFalse(policy.evaluate(RouteManager.TYPE_PUBLIC, routes, 15_000L, true)
                .shouldSwitch);
        assertTrue(policy.evaluate(RouteManager.TYPE_PUBLIC, routes, 40_001L, true)
                .shouldSwitch);
    }

    @Test
    public void pageFailureTriesReachableAlternateBeforeError() {
        RouteSwitchPolicy policy = new RouteSwitchPolicy();
        RouteManager.ProbeResult local = route("http://local", RouteManager.TYPE_LOCAL,
                true, 40);
        RouteManager.ProbeResult remote = route("https://remote", RouteManager.TYPE_PUBLIC,
                true, 100);
        RouteSwitchPolicy.Decision decision = policy.evaluateAfterPageFailure(
                RouteManager.TYPE_LOCAL, selection(local, local, remote), 20_000L, true);
        assertTrue(decision.shouldSwitch);
        assertEquals(remote, decision.target);
    }
}
