package com.neo.ezaccounting;

public final class ProgressiveRecoveryPolicy {
    private ProgressiveRecoveryPolicy() {}

    public static boolean shouldDeferFullError(RouteCoordinator.Trigger trigger,
                                               boolean hasWebView,
                                               int consecutivePageFailures) {
        return trigger == RouteCoordinator.Trigger.PAGE_FAILURE && hasWebView &&
                consecutivePageFailures == 1;
    }
}
