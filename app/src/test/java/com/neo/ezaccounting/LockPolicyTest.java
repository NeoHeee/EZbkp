package com.neo.ezaccounting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LockPolicyTest {
    @Test
    public void forcedRelockAlwaysWins() {
        assertTrue(LockPolicy.shouldRelock(0L, 1_000L, LockPolicy.COLD_START_ONLY, true));
    }

    @Test
    public void coldStartOnlyDoesNotRelockFromBackground() {
        assertFalse(LockPolicy.shouldRelock(1_000L, 999_999L, LockPolicy.COLD_START_ONLY, false));
    }

    @Test
    public void immediateRelockTriggersOnReturn() {
        assertTrue(LockPolicy.shouldRelock(1_000L, 1_001L, 0L, false));
    }

    @Test
    public void timedRelockUsesElapsedTime() {
        assertFalse(LockPolicy.shouldRelock(1_000L, 15_999L, 15_000L, false));
        assertTrue(LockPolicy.shouldRelock(1_000L, 16_000L, 15_000L, false));
    }

    @Test
    public void failedAttemptDelayEscalates() {
        assertEquals(0L, LockPolicy.lockoutDelayForFailedAttempts(2));
        assertEquals(5_000L, LockPolicy.lockoutDelayForFailedAttempts(3));
        assertEquals(30_000L, LockPolicy.lockoutDelayForFailedAttempts(5));
        assertEquals(60_000L, LockPolicy.lockoutDelayForFailedAttempts(7));
    }
}
