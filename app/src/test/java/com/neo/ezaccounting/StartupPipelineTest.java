package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StartupPipelineTest {
    @Test
    public void recordsOrderedColdStartAndUnlockMetrics() {
        StartupPipeline pipeline = new StartupPipeline(1_000L);
        assertTrue(pipeline.advance(StartupPipeline.Stage.AUTH_VISIBLE, 1_020L));
        assertTrue(pipeline.advance(StartupPipeline.Stage.PREWARMING, 1_220L));
        assertTrue(pipeline.advance(StartupPipeline.Stage.ROUTE_READY, 1_260L));
        assertTrue(pipeline.advance(StartupPipeline.Stage.WEBVIEW_READY, 1_280L));
        assertTrue(pipeline.advance(StartupPipeline.Stage.HOME_REQUESTED, 1_285L));
        assertTrue(pipeline.advance(StartupPipeline.Stage.HTML_READY, 1_410L));
        assertTrue(pipeline.advance(StartupPipeline.Stage.CONTENT_READY, 1_480L));
        assertTrue(pipeline.advance(StartupPipeline.Stage.REVEALED, 1_500L));
        assertEquals(480L, pipeline.elapsed(StartupPipeline.Stage.CREATED,
                StartupPipeline.Stage.CONTENT_READY));
        assertEquals(480L, pipeline.elapsed(StartupPipeline.Stage.AUTH_VISIBLE,
                StartupPipeline.Stage.REVEALED));
    }

    @Test
    public void ignoresDuplicateAndBackwardStages() {
        StartupPipeline pipeline = new StartupPipeline(10L);
        assertTrue(pipeline.advance(StartupPipeline.Stage.ROUTE_READY, 20L));
        assertFalse(pipeline.advance(StartupPipeline.Stage.AUTH_VISIBLE, 25L));
        assertFalse(pipeline.advance(StartupPipeline.Stage.ROUTE_READY, 30L));
        assertEquals(10L, pipeline.elapsed(StartupPipeline.Stage.CREATED,
                StartupPipeline.Stage.ROUTE_READY));
    }

    @Test
    public void productBudgetsMatchV170Targets() {
        assertEquals(300L, StartupPipeline.UNLOCK_REVEAL_BUDGET_MS);
        assertEquals(32L, StartupPipeline.QUICK_CENTER_FIRST_FRAME_BUDGET_MS);
        assertEquals(4_000L, StartupPipeline.FIRST_CONTENT_BUDGET_MS);
        assertEquals(6_000L, StartupPipeline.CONTENT_READY_SOFT_LIMIT_MS);
    }
}
