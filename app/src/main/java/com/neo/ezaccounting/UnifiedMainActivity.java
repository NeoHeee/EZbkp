package com.neo.ezaccounting;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MotionEvent;
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

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event != null && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            resetExitConfirmation();
        }
        return super.dispatchTouchEvent(event);
    }

    private void handleUnifiedBack() {
        long now = System.currentTimeMillis();
        View root = getWindow().getDecorView();
        WebView webView = findWebView(root);
        boolean hasConfiguredRoute = hasConfiguredRoute();

        AppStateMachine.State state;
        boolean canGoBack = false;
        boolean atHome = true;
        String homeUrl = null;

        if (webView != null) {
            state = AppStateMachine.State.READY;
            canGoBack = webView.canGoBack();
            homeUrl = resolveHomeUrl(webView.getUrl());
            atHome = BackNavigationPolicy.isAtHome(homeUrl, webView.getUrl());
        } else if (containsEditText(root)) {
            state = AppStateMachine.State.SETTINGS;
        } else {
            state = AppStateMachine.State.ERROR;
        }

        BackNavigationPolicy.Action action = BackNavigationPolicy.decide(
                state, canGoBack, hasConfiguredRoute, atHome,
                now, unifiedLastBackPressedAt);

        switch (action) {
            case WEB_BACK:
                webView.goBack();
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
