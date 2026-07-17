package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AppLifecycleCoordinatorTest {
    @Test
    public void unsecuredColdStartInitializesImmediately() {
        AppLifecycleCoordinator lifecycle = new AppLifecycleCoordinator();
        assertEquals(AppLifecycleCoordinator.Action.INITIALIZE,
                lifecycle.onCreate(false));
        assertFalse(lifecycle.isAuthInProgress());
    }

    @Test
    public void securedColdStartUnlocksThenInitializes() {
        AppLifecycleCoordinator lifecycle = new AppLifecycleCoordinator();
        assertEquals(AppLifecycleCoordinator.Action.REQUEST_UNLOCK,
                lifecycle.onCreate(true));
        assertTrue(lifecycle.isAuthInProgress());
        assertEquals(AppLifecycleCoordinator.Action.INITIALIZE,
                lifecycle.onUnlockResult(true, 1000L));
        assertEquals(1000L, lifecycle.getLastUnlockAt());
    }

    @Test
    public void canceledColdStartFinishesApplication() {
        AppLifecycleCoordinator lifecycle = new AppLifecycleCoordinator();
        lifecycle.onCreate(true);
        assertEquals(AppLifecycleCoordinator.Action.FINISH_APP,
                lifecycle.onUnlockResult(false, 1000L));
    }

    @Test
    public void backgroundTimeoutRequestsUnlock() {
        AppLifecycleCoordinator lifecycle = initializedLifecycle();
        lifecycle.onStopped(1000L, false);
        assertEquals(AppLifecycleCoordinator.Action.NONE,
                lifecycle.onResumed(10_000L, true, 15_000L));
        assertEquals(AppLifecycleCoordinator.Action.REQUEST_UNLOCK,
                lifecycle.onResumed(17_000L, true, 15_000L));
    }

    @Test
    public void configurationChangeDoesNotStartBackgroundTimer() {
        AppLifecycleCoordinator lifecycle = initializedLifecycle();
        lifecycle.onStopped(1000L, true);
        assertEquals(AppLifecycleCoordinator.Action.NONE,
                lifecycle.onResumed(100_000L, true, 0L));
    }

    @Test
    public void screenOffForcesRelockRegardlessOfTimeout() {
        AppLifecycleCoordinator lifecycle = initializedLifecycle();
        lifecycle.onScreenOff(1000L, true, true);
        assertEquals(AppLifecycleCoordinator.Action.REQUEST_UNLOCK,
                lifecycle.onResumed(1001L, true, LockPolicy.COLD_START_ONLY));
    }

    @Test
    public void externalAuthenticationDoesNotCreateFalseBackgroundLock() {
        AppLifecycleCoordinator lifecycle = initializedLifecycle();
        lifecycle.beginAuth();
        lifecycle.onStopped(1000L, false);
        lifecycle.finishExternalFlow();
        assertEquals(AppLifecycleCoordinator.Action.NONE,
                lifecycle.onResumed(100_000L, true, 0L));
    }

    private AppLifecycleCoordinator initializedLifecycle() {
        AppLifecycleCoordinator lifecycle = new AppLifecycleCoordinator();
        lifecycle.onCreate(false);
        lifecycle.markInitialized();
        return lifecycle;
    }
}
