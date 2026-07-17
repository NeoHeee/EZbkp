package com.neo.ezaccounting;

import java.util.EnumSet;

public final class AppStateMachine {
    public enum State {
        LOCKED,
        INITIALIZING,
        CHECKING_ROUTE,
        LOADING_WEB,
        READY,
        ERROR,
        SETTINGS
    }

    public interface Listener {
        void onStateChanged(State previous, State current);
    }

    private State state;
    private Listener listener;

    public AppStateMachine(State initialState) {
        state = initialState == null ? State.INITIALIZING : initialState;
    }

    public State getState() { return state; }

    public void setListener(Listener listener) { this.listener = listener; }

    public boolean transitionTo(State next) {
        if (next == null || next == state) return false;
        if (!canTransition(state, next)) {
            throw new IllegalStateException("Invalid state transition: " + state + " -> " + next);
        }
        State previous = state;
        state = next;
        if (listener != null) listener.onStateChanged(previous, next);
        return true;
    }

    public String save() { return state.name(); }

    public static State restore(String value, State fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        try {
            return State.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    static boolean canTransition(State from, State to) {
        if (from == null || to == null) return false;
        switch (from) {
            case LOCKED:
                return EnumSet.of(State.INITIALIZING, State.CHECKING_ROUTE, State.LOADING_WEB,
                                State.READY, State.SETTINGS, State.ERROR)
                        .contains(to);
            case INITIALIZING:
                return EnumSet.of(State.CHECKING_ROUTE, State.SETTINGS, State.ERROR, State.LOCKED)
                        .contains(to);
            case CHECKING_ROUTE:
                return EnumSet.of(State.LOADING_WEB, State.READY, State.ERROR, State.SETTINGS,
                                State.LOCKED)
                        .contains(to);
            case LOADING_WEB:
                return EnumSet.of(State.READY, State.ERROR, State.CHECKING_ROUTE, State.LOCKED,
                                State.SETTINGS)
                        .contains(to);
            case READY:
                return EnumSet.of(State.LOCKED, State.CHECKING_ROUTE, State.ERROR, State.SETTINGS,
                                State.LOADING_WEB)
                        .contains(to);
            case ERROR:
                return EnumSet.of(State.CHECKING_ROUTE, State.LOADING_WEB, State.SETTINGS,
                                State.LOCKED, State.READY)
                        .contains(to);
            case SETTINGS:
                return EnumSet.of(State.CHECKING_ROUTE, State.READY, State.ERROR, State.LOCKED,
                                State.INITIALIZING)
                        .contains(to);
            default:
                return false;
        }
    }
}
