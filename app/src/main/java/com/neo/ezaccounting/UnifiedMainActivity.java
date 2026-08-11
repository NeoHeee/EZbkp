package com.neo.ezaccounting;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;

public final class UnifiedMainActivity extends MainActivity {
    private long unifiedLastBackPressedAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleUnifiedBack();
            }
        });
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        getOnBackPressedDispatcher().onBackPressed();
    }

    private void handleUnifiedBack() {
        if (handleNativeOverlayBack()) {
            resetExitConfirmation();
            return;
        }

        boolean hasConfiguredRoute = hasConfiguredRouteForNavigation();
        AppStateMachine.State state = currentAppState();

        if (!hasWebContent()) {
            performBackDecision(state, hasConfiguredRoute,
                    false, true, null, System.currentTimeMillis());
            return;
        }

        String homeUrl = resolveHomeUrlForNavigation(currentWebUrl());
        if (canNavigateWebBack()) {
            performBackDecision(state, hasConfiguredRoute,
                    true, false, homeUrl, System.currentTimeMillis());
            return;
        }

        boolean urlAtHome = BackNavigationPolicy.isAtHome(homeUrl, currentWebUrl());
        resolveCurrentPageIdentity(identity -> {
            if (isFinishing() || isDestroyed() || !hasWebContent()) return;
            boolean atHome = EzBookkeepingPageDetector.resolveHome(identity, urlAtHome);
            performBackDecision(currentAppState(), hasConfiguredRouteForNavigation(),
                    canNavigateWebBack(), atHome, homeUrl, System.currentTimeMillis());
        });
    }

    private void performBackDecision(AppStateMachine.State state,
                                     boolean hasConfiguredRoute,
                                     boolean canGoBack,
                                     boolean atHome,
                                     String homeUrl,
                                     long now) {
        BackNavigationPolicy.Action action = BackNavigationPolicy.decide(
                state, canGoBack, hasConfiguredRoute, atHome,
                now, unifiedLastBackPressedAt);

        switch (action) {
            case WEB_BACK:
                navigateWebBack();
                refreshPageIdentityAfterNavigation();
                resetExitConfirmation();
                break;
            case GO_HOME:
                loadWebHome(homeUrl);
                resetExitConfirmation();
                break;
            case RESTORE_SETTINGS:
            case RECOVER_ERROR:
                resetExitConfirmation();
                handleNativeOverlayBack();
                break;
            case EXIT:
                finishAndRemoveTask();
                break;
            case SHOW_EXIT_HINT:
            default:
                unifiedLastBackPressedAt = now;
                Toast.makeText(this, "再按一次返回键退出", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void resetExitConfirmation() {
        unifiedLastBackPressedAt = 0L;
    }
}
