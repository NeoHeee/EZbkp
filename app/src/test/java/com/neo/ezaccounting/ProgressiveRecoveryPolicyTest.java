package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProgressiveRecoveryPolicyTest {
    @Test
    public void firstPageFailureKeepsWebViewVisibleForOneRetry() {
        assertTrue(ProgressiveRecoveryPolicy.shouldDeferFullError(
                RouteCoordinator.Trigger.PAGE_FAILURE, true, 1));
    }

    @Test
    public void repeatedPageFailureShowsFullRecovery() {
        assertFalse(ProgressiveRecoveryPolicy.shouldDeferFullError(
                RouteCoordinator.Trigger.PAGE_FAILURE, true, 2));
    }

    @Test
    public void startupAndMissingWebViewDoNotDeferErrors() {
        assertFalse(ProgressiveRecoveryPolicy.shouldDeferFullError(
                RouteCoordinator.Trigger.STARTUP, true, 1));
        assertFalse(ProgressiveRecoveryPolicy.shouldDeferFullError(
                RouteCoordinator.Trigger.PAGE_FAILURE, false, 1));
    }
}
