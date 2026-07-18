package com.neo.ezaccounting;

public final class AppLifecycleCoordinator {
    public enum Action { NONE, INITIALIZE, REQUEST_UNLOCK, FINISH_APP }

    private boolean initialized;
    private boolean authInProgress;
    private long backgroundAt;
    private boolean forceRelock;
    private long lastUnlockAt;

    public Action onCreate(boolean securityEnabled) {
        if (securityEnabled) {
            authInProgress = true;
            return Action.REQUEST_UNLOCK;
        }
        return Action.INITIALIZE;
    }

    public void markInitialized() { initialized = true; }
    public boolean isInitialized() { return initialized; }
    public boolean isAuthInProgress() { return authInProgress; }
    public long getLastUnlockAt() { return lastUnlockAt; }

    public void beginAuth() { authInProgress = true; }

    public void finishExternalFlow() {
        authInProgress = false;
        backgroundAt = 0L;
    }

    public Action onUnlockResult(boolean success, long now) {
        authInProgress = false;
        backgroundAt = 0L;
        if (!success) return Action.FINISH_APP;
        forceRelock = false;
        lastUnlockAt = now;
        return initialized ? Action.NONE : Action.INITIALIZE;
    }

    public void onStopped(long now, boolean changingConfigurations) {
        if (!changingConfigurations && !authInProgress && initialized) backgroundAt = now;
    }

    public Action onResumed(long now, boolean securityEnabled, long timeoutMs) {
        if (!initialized || authInProgress || !securityEnabled) return Action.NONE;
        if (!LockPolicy.shouldRelock(backgroundAt, now, timeoutMs, forceRelock)) return Action.NONE;
        backgroundAt = 0L;
        forceRelock = false;
        authInProgress = true;
        return Action.REQUEST_UNLOCK;
    }

    public void onScreenOff(long now, boolean securityEnabled, boolean lockOnScreenOff) {
        if (initialized && !authInProgress && securityEnabled && lockOnScreenOff) {
            forceRelock = true;
            backgroundAt = now;
        }
    }

    public void requestImmediateRelock() {
        forceRelock = false;
        backgroundAt = 0L;
        authInProgress = true;
    }

    public Snapshot snapshot() {
        return new Snapshot(initialized, authInProgress, backgroundAt, forceRelock, lastUnlockAt);
    }

    public static final class Snapshot {
        public final boolean initialized;
        public final boolean authInProgress;
        public final long backgroundAt;
        public final boolean forceRelock;
        public final long lastUnlockAt;

        Snapshot(boolean initialized, boolean authInProgress, long backgroundAt,
                 boolean forceRelock, long lastUnlockAt) {
            this.initialized = initialized;
            this.authInProgress = authInProgress;
            this.backgroundAt = backgroundAt;
            this.forceRelock = forceRelock;
            this.lastUnlockAt = lastUnlockAt;
        }
    }
}
