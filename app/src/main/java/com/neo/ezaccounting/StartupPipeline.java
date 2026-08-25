package com.neo.ezaccounting;

import java.util.EnumMap;
import java.util.Map;

public final class StartupPipeline {
    public enum Stage {
        CREATED,
        AUTH_VISIBLE,
        PREWARMING,
        ROUTE_READY,
        WEBVIEW_READY,
        HOME_REQUESTED,
        HTML_READY,
        CONTENT_READY,
        REVEALED
    }

    public static final long UNLOCK_REVEAL_BUDGET_MS = 300L;
    public static final long QUICK_CENTER_FIRST_FRAME_BUDGET_MS = 32L;
    public static final long FIRST_CONTENT_BUDGET_MS = 4_000L;
    public static final long CONTENT_READY_SOFT_LIMIT_MS = 6_000L;

    private final Map<Stage, Long> timestamps = new EnumMap<>(Stage.class);
    private Stage latest = Stage.CREATED;

    public StartupPipeline(long createdAt) {
        timestamps.put(Stage.CREATED, createdAt);
    }

    public synchronized boolean advance(Stage stage, long now) {
        if (stage == null || stage.ordinal() < latest.ordinal()) return false;
        if (timestamps.containsKey(stage)) return false;
        timestamps.putIfAbsent(stage, now);
        if (stage.ordinal() > latest.ordinal()) latest = stage;
        return true;
    }

    public synchronized Stage latest() {
        return latest;
    }

    public synchronized long elapsed(Stage from, Stage to) {
        Long start = timestamps.get(from);
        Long end = timestamps.get(to);
        return start == null || end == null ? -1L : Math.max(0L, end - start);
    }

    public synchronized String metrics() {
        long coldStartMs = elapsed(Stage.CREATED, Stage.CONTENT_READY);
        return "cold_start_ms=" + coldStartMs +
                " cold_start_budget_ms=" + FIRST_CONTENT_BUDGET_MS +
                " cold_start_within_budget=" +
                (coldStartMs >= 0L && coldStartMs <= FIRST_CONTENT_BUDGET_MS) +
                " auth_to_reveal_ms=" + elapsed(Stage.AUTH_VISIBLE, Stage.REVEALED) +
                " prewarm_to_content_ms=" + elapsed(Stage.PREWARMING, Stage.CONTENT_READY) +
                " route_to_html_ms=" + elapsed(Stage.ROUTE_READY, Stage.HTML_READY);
    }
}
