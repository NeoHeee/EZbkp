package com.neo.ezaccounting;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;

public final class UnifiedMainActivity extends MainActivity {
    private static final String PREFS = "ez_accounting_prefs";
    private static final String KEY_LOCAL_URL = "local_url";
    private static final String KEY_PUBLIC_URL = "public_url";

    private long unifiedLastBackPressedAt;
    private boolean pageIdentityCheckInProgress;
    private int queuedBackPresses;

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
        if (pageIdentityCheckInProgress) {
            queuedBackPresses = Math.min(1, queuedBackPresses + 1);
            return;
        }

        View root = getWindow().getDecorView();
        WebView webView = findWebView(root);
        boolean hasConfiguredRoute = hasConfiguredRoute();

        if (webView == null) {
            AppStateMachine.State state = containsEditText(root) ?
                    AppStateMachine.State.SETTINGS : AppStateMachine.State.ERROR;
            performBackDecision(state, null, hasConfiguredRoute,
                    false, true, null, System.currentTimeMillis());
            return;
        }

        String homeUrl = resolveHomeUrl(webView.getUrl());
        boolean urlAtHome = BackNavigationPolicy.isAtHome(homeUrl, webView.getUrl());
        pageIdentityCheckInProgress = true;

        webView.evaluateJavascript(EzBookkeepingPageDetector.homeDetectionScript(), result -> {
            pageIdentityCheckInProgress = false;
            if (isFinishing() || isDestroyed() || webView.getParent() == null) {
                queuedBackPresses = 0;
                return;
            }

            boolean domAtHome = EzBookkeepingPageDetector.isHomeResult(result);
            performBackDecision(AppStateMachine.State.READY, webView,
                    hasConfiguredRoute, webView.canGoBack(),
                    domAtHome || urlAtHome, homeUrl, System.currentTimeMillis());

            if (queuedBackPresses > 0 && !isFinishing() && !isDestroyed()) {
                queuedBackPresses--;
                getWindow().getDecorView().post(this::handleUnifiedBack);
            }
        });
    }

    private void performBackDecision(AppStateMachine.State state,
                                     WebView webView,
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
                if (webView != null) webView.goBack();
                resetExitConfirmation();
                break;
            case GO_HOME:
                returnToHome(webView, homeUrl);
                resetExitConfirmation();
                break;
            case RESTORE_SETTINGS:
            case RECOVER_ERROR:
                resetExitConfirmation();
                recreate();
                break;
            case EXIT:
                queuedBackPresses = 0;
                finishAndRemoveTask();
                break;
            case SHOW_EXIT_HINT:
            default:
                unifiedLastBackPressedAt = now;
                Toast.makeText(this, "再按一次返回键退出", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void returnToHome(WebView webView, String homeUrl) {
        if (webView == null || blank(homeUrl)) return;
        webView.clearHistory();
        webView.loadUrl(homeUrl);
        webView.postDelayed(() -> {
            if (webView.getParent() != null) webView.clearHistory();
        }, 1200L);
    }

    private String resolveHomeUrl(String currentUrl) {
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        String lastRoute = preferences.getString(RouteCoordinator.KEY_LAST_ROUTE, "");
        String localUrl = preferences.getString(KEY_LOCAL_URL, "");
        String publicUrl = preferences.getString(KEY_PUBLIC_URL, "");

        if (!blank(currentUrl)) {
            if (!blank(lastRoute) && WebOriginPolicy.isSameOrigin(lastRoute, currentUrl)) {
                return lastRoute;
            }
            if (!blank(localUrl) && WebOriginPolicy.isSameOrigin(localUrl, currentUrl)) {
                return localUrl;
            }
            if (!blank(publicUrl) && WebOriginPolicy.isSameOrigin(publicUrl, currentUrl)) {
                return publicUrl;
            }
        }
        if (!blank(lastRoute)) return lastRoute;
        if (!blank(localUrl)) return localUrl;
        return publicUrl;
    }

    private boolean hasConfiguredRoute() {
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        return !blank(preferences.getString(KEY_LOCAL_URL, "")) ||
                !blank(preferences.getString(KEY_PUBLIC_URL, ""));
    }

    private WebView findWebView(View view) {
        if (view instanceof WebView) return (WebView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            WebView found = findWebView(group.getChildAt(index));
            if (found != null) return found;
        }
        return null;
    }

    private boolean containsEditText(View view) {
        if (view instanceof EditText) return true;
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            if (containsEditText(group.getChildAt(index))) return true;
        }
        return false;
    }

    private void resetExitConfirmation() {
        unifiedLastBackPressedAt = 0L;
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
