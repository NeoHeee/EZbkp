package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RouteManagerTest {
    private RouteManager.ProbeResult route(String url, int type, boolean ok, long latency) {
        return new RouteManager.ProbeResult(url, type, ok, latency, ok ? 200 : 0);
    }

    @Test
    public void keepsLocalWhenLatencyDifferenceIsSmall() {
        RouteManager.ProbeResult local = route("http://local", RouteManager.TYPE_LOCAL, true, 180);
        RouteManager.ProbeResult remote = route("https://remote", RouteManager.TYPE_PUBLIC, true, 70);
        assertEquals(local, RouteManager.selectBest(local, remote, ""));
    }

    @Test
    public void selectsPublicWhenItIsClearlyFaster() {
        RouteManager.ProbeResult local = route("http://local", RouteManager.TYPE_LOCAL, true, 600);
        RouteManager.ProbeResult remote = route("https://remote", RouteManager.TYPE_PUBLIC, true, 90);
        assertEquals(remote, RouteManager.selectBest(local, remote, ""));
    }

    @Test
    public void keepsLastRouteWithinTolerance() {
        RouteManager.ProbeResult local = route("http://local", RouteManager.TYPE_LOCAL, true, 160);
        RouteManager.ProbeResult remote = route("https://remote", RouteManager.TYPE_PUBLIC, true, 80);
        assertEquals(remote, RouteManager.selectBest(local, remote, "https://remote"));
    }

    @Test
    public void returnsOnlyReachableRoute() {
        RouteManager.ProbeResult local = route("http://local", RouteManager.TYPE_LOCAL, false, 1700);
        RouteManager.ProbeResult remote = route("https://remote", RouteManager.TYPE_PUBLIC, true, 120);
        assertEquals(remote, RouteManager.selectBest(local, remote, ""));
    }

    @Test
    public void returnsNullWhenBothUnavailable() {
        RouteManager.ProbeResult local = route("http://local", RouteManager.TYPE_LOCAL, false, 1700);
        RouteManager.ProbeResult remote = route("https://remote", RouteManager.TYPE_PUBLIC, false, 2600);
        assertNull(RouteManager.selectBest(local, remote, ""));
    }
}
