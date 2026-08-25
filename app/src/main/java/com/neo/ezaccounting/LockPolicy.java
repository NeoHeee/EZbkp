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

    public static int attemptsUntilNextDelay(int attempts) {
        if (attempts < 3) return 3 - attempts;
        if (attempts < 5) return 5 - attempts;
        if (attempts < 7) return 7 - attempts;
        return 0;
    }

    public static String failedAttemptMessage(boolean pattern, int attempts) {
        String kind = pattern ? "图形错误" : "密码错误";
        int remaining = attemptsUntilNextDelay(attempts);
        if (remaining <= 0) return kind + "，已连续失败 " + attempts + " 次";
        return kind + "，已连续失败 " + attempts + " 次；再错 " + remaining +
                " 次将进入等待";
    }
}
