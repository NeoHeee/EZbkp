package com.neo.ezaccounting;

public final class LockPolicy {
    public static final long COLD_START_ONLY = -1L;

    private LockPolicy() {}

    public static boolean shouldRelock(long backgroundAt, long now, long timeoutMs, boolean forced) {
        if (forced) return true;
        if (backgroundAt <= 0 || timeoutMs == COLD_START_ONLY) return false;
        if (timeoutMs <= 0) return true;
        return now - backgroundAt >= timeoutMs;
    }

    public static long lockoutDelayForFailedAttempts(int attempts) {
        if (attempts >= 7) return 60_000L;
        if (attempts >= 5) return 30_000L;
        if (attempts >= 3) return 5_000L;
        return 0L;
    }
}
