package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AppStateMachineTest {
    @Test
    public void followsNormalStartupAndRecoveryFlow() {
        AppStateMachine machine = new AppStateMachine(AppStateMachine.State.INITIALIZING);
        assertTrue(machine.transitionTo(AppStateMachine.State.CHECKING_ROUTE));
        assertTrue(machine.transitionTo(AppStateMachine.State.LOADING_WEB));
        assertTrue(machine.transitionTo(AppStateMachine.State.READY));
        assertTrue(machine.transitionTo(AppStateMachine.State.ERROR));
        assertTrue(machine.transitionTo(AppStateMachine.State.CHECKING_ROUTE));
        assertTrue(machine.transitionTo(AppStateMachine.State.LOADING_WEB));
        assertEquals(AppStateMachine.State.LOADING_WEB, machine.getState());
    }

    @Test
    public void lockCanRestoreLoadingOrReadyState() {
        AppStateMachine machine = new AppStateMachine(AppStateMachine.State.READY);
        machine.transitionTo(AppStateMachine.State.LOCKED);
        machine.transitionTo(AppStateMachine.State.LOADING_WEB);
        machine.transitionTo(AppStateMachine.State.READY);
        assertEquals(AppStateMachine.State.READY, machine.getState());
    }

    @Test
    public void sameStateDoesNotEmitTransition() {
        AppStateMachine machine = new AppStateMachine(AppStateMachine.State.ERROR);
        assertFalse(machine.transitionTo(AppStateMachine.State.ERROR));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsSkippingFromInitializingDirectlyToReady() {
        AppStateMachine machine = new AppStateMachine(AppStateMachine.State.INITIALIZING);
        machine.transitionTo(AppStateMachine.State.READY);
    }

    @Test
    public void restoresUnknownValueToFallback() {
        assertEquals(AppStateMachine.State.INITIALIZING,
                AppStateMachine.restore("removed-state", AppStateMachine.State.INITIALIZING));
    }
}
