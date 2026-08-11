package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PerformanceBudgetTest {
    private static final int ITERATIONS = 50_000;
    private static final long POLICY_BUDGET_MS = 2_000L;

    @Test
    public void coldStartRouteDecisionStaysWithinBudget() {
        long started = System.nanoTime();
        FastStartPolicy.Candidate result = null;
        for (int index = 0; index < ITERATIONS; index++) {
            result = FastStartPolicy.select("http://192.168.1.10:8080",
                    "https://money.example.com");
        }
        assertNotNull(result);
        assertWithinBudget("冷启动线路决策", started);
    }

    @Test
    public void firstQuickCenterModelPreparationStaysWithinBudget() {
        RouteManager.ProbeResult local = new RouteManager.ProbeResult(
                "http://local", RouteManager.TYPE_LOCAL, true, 32, 200);
        RouteManager.Selection selection = new RouteManager.Selection(local, local, null);
        RouteCoordinator.Snapshot snapshot = new RouteCoordinator.Snapshot(
                selection, local.url, local.type, 1L);
        long started = System.nanoTime();
        QuickActionsSheet.Model result = null;
        for (int index = 0; index < ITERATIONS; index++) {
            result = RoutePresentation.quickActionsModel(
                    RouteManager.TYPE_LOCAL, snapshot, "指纹或面容");
        }
        assertNotNull(result);
        assertWithinBudget("首次呼出状态准备", started);
    }

    @Test
    public void routeSwitchScoringStaysWithinBudget() {
        RouteManager.ProbeResult local = new RouteManager.ProbeResult(
                "http://local", RouteManager.TYPE_LOCAL, true, 60, 200)
                .withHealth(92, false);
        RouteManager.ProbeResult remote = new RouteManager.ProbeResult(
                "https://remote", RouteManager.TYPE_PUBLIC, true, 95, 200)
                .withHealth(98, false);
        long started = System.nanoTime();
        RouteManager.ProbeResult selected = null;
        for (int index = 0; index < ITERATIONS; index++) {
            selected = RouteManager.selectBest(local, remote, "http://local", true);
        }
        assertNotNull(selected);
        assertWithinBudget("线路切换评分", started);
    }

    private void assertWithinBudget(String operation, long startedNanos) {
        long elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000L;
        assertTrue(operation + "耗时 " + elapsedMs + " ms，超过预算 " +
                POLICY_BUDGET_MS + " ms", elapsedMs <= POLICY_BUDGET_MS);
    }
}
