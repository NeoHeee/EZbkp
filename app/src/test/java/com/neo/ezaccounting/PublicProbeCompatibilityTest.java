package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PublicProbeCompatibilityTest {
    private RouteManager.ProbeResult route(String url, int type, boolean ok, long latency) {
        return new RouteManager.ProbeResult(url, type, ok, latency, ok ? 200 : 0);
    }

    private RouteManager.ProbeResult failedPublic(RouteManager.ErrorKind kind) {
        return new RouteManager.ProbeResult("https://remote", RouteManager.TYPE_PUBLIC,
                false, 6000, 0, kind, "测试失败原因");
    }

    @Test
    public void forcedPublicCanUseWebViewWhenProbeReportsTlsFailure() {
        RouteManager.ProbeResult local = route("http://local", RouteManager.TYPE_LOCAL, true, 20);
        RouteManager.ProbeResult selected = RouteManager.selectForModeWithWebFallback(
                RouteMode.PUBLIC, local, failedPublic(RouteManager.ErrorKind.TLS), "", true);
        assertTrue(selected.reachable);
        assertTrue(selected.verificationPending);
        assertEquals(RouteManager.ErrorKind.TLS, selected.errorKind);
    }

    @Test
    public void autoModeCanVerifyOnlyConfiguredPublicRoute() {
        RouteManager.ProbeResult local = new RouteManager.ProbeResult("",
                RouteManager.TYPE_LOCAL, false, -1, 0,
                RouteManager.ErrorKind.UNCONFIGURED, "未配置地址");
        RouteManager.ProbeResult selected = RouteManager.selectForModeWithWebFallback(
                RouteMode.AUTO, local, failedPublic(RouteManager.ErrorKind.TIMEOUT), "", true);
        assertTrue(selected.verificationPending);
        assertEquals(RouteManager.TYPE_PUBLIC, selected.type);
    }

    @Test
    public void autoModeCanRetryLastSuccessfulPublicRoute() {
        RouteManager.ProbeResult local = route("http://local", RouteManager.TYPE_LOCAL, false, 1700);
        RouteManager.ProbeResult selected = RouteManager.selectForModeWithWebFallback(
                RouteMode.AUTO, local, failedPublic(RouteManager.ErrorKind.TLS),
                "https://remote", true);
        assertTrue(selected.verificationPending);
    }

    @Test
    public void reachableLocalStillWinsOverUncertainPublicRoute() {
        RouteManager.ProbeResult local = route("http://local", RouteManager.TYPE_LOCAL, true, 80);
        RouteManager.ProbeResult selected = RouteManager.selectForModeWithWebFallback(
                RouteMode.AUTO, local, failedPublic(RouteManager.ErrorKind.TLS),
                "https://remote", true);
        assertEquals(local, selected);
        assertFalse(selected.verificationPending);
    }

    @Test
    public void pageFailureDisablesAutomaticWebFallback() {
        RouteManager.ProbeResult selected = RouteManager.selectForModeWithWebFallback(
                RouteMode.PUBLIC, null, failedPublic(RouteManager.ErrorKind.TLS), "", false);
        assertNull(selected);
    }

    @Test
    public void webVerifiedResultKeepsProbeDiagnostics() {
        RouteManager.ProbeResult verified = failedPublic(RouteManager.ErrorKind.TLS)
                .asWebVerified("https://remote/home");
        assertTrue(verified.reachable);
        assertTrue(verified.webVerified);
        assertTrue(verified.diagnostic().contains("WebView 已实际加载成功"));
        assertTrue(verified.diagnostic().contains("测试失败原因"));
    }

    @Test
    public void publicProbeUsesLongerTimeoutAndLimitedRedirects() {
        assertEquals(6000, RouteManager.PUBLIC_TIMEOUT_MS);
        assertEquals(5, RouteManager.MAX_REDIRECTS);
    }
}
