package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BackNavigationPolicyTest {
    @Test
    public void webHistoryHasHighestPriorityOnWebPage() {
        assertEquals(BackNavigationPolicy.Action.WEB_BACK,
                BackNavigationPolicy.decide(AppStateMachine.State.READY,
                        true, true, false, 10_000L, 0L));
    }

    @Test
    public void nonHomePageWithoutHistoryFallsBackToHome() {
        assertEquals(BackNavigationPolicy.Action.GO_HOME,
                BackNavigationPolicy.decide(AppStateMachine.State.READY,
                        false, true, false, 10_000L, 0L));
    }

    @Test
    public void settingsAndErrorRecoverBeforeExit() {
        assertEquals(BackNavigationPolicy.Action.RESTORE_SETTINGS,
                BackNavigationPolicy.decide(AppStateMachine.State.SETTINGS,
                        false, true, true, 10_000L, 0L));
        assertEquals(BackNavigationPolicy.Action.RECOVER_ERROR,
                BackNavigationPolicy.decide(AppStateMachine.State.ERROR,
                        false, true, true, 10_000L, 0L));
    }

    @Test
    public void rootPageRequiresSecondBackWithinWindow() {
        assertEquals(BackNavigationPolicy.Action.SHOW_EXIT_HINT,
                BackNavigationPolicy.decide(AppStateMachine.State.READY,
                        false, true, true, 10_000L, 0L));
        assertEquals(BackNavigationPolicy.Action.EXIT,
                BackNavigationPolicy.decide(AppStateMachine.State.READY,
                        false, true, true, 11_000L, 10_000L));
        assertEquals(BackNavigationPolicy.Action.SHOW_EXIT_HINT,
                BackNavigationPolicy.decide(AppStateMachine.State.READY,
                        false, true, true, 12_000L, 10_000L));
    }

    @Test
    public void recognizesHomeAcrossTrailingSlashAndDefaultPort() {
        assertTrue(BackNavigationPolicy.isAtHome(
                "https://money.example.com", "https://money.example.com/"));
        assertTrue(BackNavigationPolicy.isAtHome(
                "https://money.example.com:443/app/", "https://money.example.com/app"));
    }

    @Test
    public void queryFragmentAndDifferentPathAreNotHome() {
        assertFalse(BackNavigationPolicy.isAtHome(
                "https://money.example.com", "https://money.example.com/?account=1"));
        assertFalse(BackNavigationPolicy.isAtHome(
                "https://money.example.com", "https://money.example.com/#/transactions"));
        assertFalse(BackNavigationPolicy.isAtHome(
                "https://money.example.com/app", "https://money.example.com/app/accounts"));
    }

    @Test
    public void configuredBaseFragmentCanRepresentHome() {
        assertTrue(BackNavigationPolicy.isAtHome(
                "https://money.example.com/#/home", "https://money.example.com/#/home"));
        assertFalse(BackNavigationPolicy.isAtHome(
                "https://money.example.com/#/home", "https://money.example.com/#/settings"));
    }
}
