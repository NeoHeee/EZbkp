package com.neo.ezaccounting;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WebViewController {
    public enum FailureType {
        NETWORK,
        DNS,
        TIMEOUT,
        HTTP,
        SSL,
        UNKNOWN
    }

    public static final class Failure {
        public final FailureType type;
        public final String title;
        public final String detail;
        public final int code;
        public final String url;

        Failure(FailureType type, String title, String detail, int code, String url) {
            this.type = type;
            this.title = title;
            this.detail = detail;
            this.code = code;
            this.url = url;
        }
    }

    public interface Host {
        boolean onNavigationRequested(Uri uri);
        void onFileChooserRequested(ValueCallback<Uri[]> callback,
                                    WebChromeClient.FileChooserParams params);
        void onPageStarted(String url);
        void onPageReady(String url);
        void onPageFailure(Failure failure);
        void onOpenQuickActions();
    }

    private static WeakReference<WebViewController> activeController =
            new WeakReference<>(null);
    private static final ExecutorService metadataExecutor = Executors.newSingleThreadExecutor();

    private final Activity activity;
    private final Host host;
    private final DownloadController downloadController;

    private WebView webView;
    private ProgressBar pageProgress;
    private String baseUrl;
    private boolean pageReady;
    private boolean mainFrameFailed;
    private final PageIdentityCache pageIdentityCache = new PageIdentityCache();
    private boolean identityCheckInProgress;
    private final List<ValueCallback<EzBookkeepingPageDetector.PageIdentity>>
            pendingIdentityCallbacks = new ArrayList<>();
    private final Runnable pageIdentityRefresh = this::refreshPageIdentity;

    private boolean twoFingerTapCandidate;
    private long twoFingerTapStartedAt;
    private float twoFingerTapStartX;
    private float twoFingerTapStartY;
    private long firstTwoFingerTapAt;
    private float firstTwoFingerTapX;
    private float firstTwoFingerTapY;
    private boolean suppressNextIdentityRefresh;
    private boolean preloadMode;

    public WebViewController(Activity activity, Host host,
                             DownloadController downloadController) {
        this.activity = activity;
        this.host = host;
        this.downloadController = downloadController;
    }

    public View create(String baseUrl, String initialUrl) {
        return create(baseUrl, initialUrl, null);
    }

    public View create(String baseUrl, String initialUrl, Bundle restoredState) {
        destroy();
        this.baseUrl = baseUrl;
        pageReady = false;

        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(UiTheme.webBackground(activity));

        webView = new WebView(activity);
        webView.setBackgroundColor(UiTheme.webBackground(activity));
        webView.setContentDescription("记账页面");
        webView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        pageProgress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        pageProgress.setMax(100);
        pageProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(
                UiTheme.accent(activity)));
        pageProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(
                Color.TRANSPARENT));
        pageProgress.setContentDescription("页面加载进度");
        pageProgress.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        pageProgress.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(3));
        progressParams.gravity = android.view.Gravity.TOP;
        root.addView(pageProgress, progressParams);
        activeController = new WeakReference<>(this);

        configure();
        setupHiddenGesture();
        boolean restored = restoredState != null && webView.restoreState(restoredState) != null;
        if (restored) {
            pageReady = true;
            syncPageTheme();
            webView.post(() -> {
                refreshPageIdentity();
                host.onPageReady(currentUrl());
            });
        } else {
            loadUrl(initialUrl == null || initialUrl.trim().isEmpty() ? baseUrl : initialUrl);
        }
        return root;
    }

    public void setPreloadMode(boolean enabled) {
        preloadMode = enabled;
        if (webView == null) return;
        webView.setEnabled(!enabled);
        webView.setFocusable(!enabled);
        webView.setFocusableInTouchMode(!enabled);
        webView.setImportantForAccessibility(enabled ?
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS :
                View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        if (!enabled && pageReady) {
            syncPageTheme();
            refreshPageIdentity();
        }
    }

    public void stopLoading() {
        if (webView != null) webView.stopLoading();
    }

    public void saveState(Bundle outState) {
        if (webView != null && outState != null) webView.saveState(outState);
    }

    public void syncPageTheme() {
        if (webView == null) return;
        String scheme = UiTheme.isDark(activity) ? "dark" : "light";
        webView.evaluateJavascript("(function(){try{" +
                "document.documentElement.style.colorScheme='" + scheme + "';" +
                "document.documentElement.setAttribute('data-native-color-scheme','" + scheme + "');" +
                "var m=document.querySelector('meta[name=\"color-scheme\"]');" +
                "if(!m){m=document.createElement('meta');m.name='color-scheme';document.head.appendChild(m);}" +
                "m.content='light dark';window.dispatchEvent(new Event('native-theme-change'));" +
                "}catch(e){}})();", null);
    }

    public void requestServerVersion(ValueCallback<String> callback) {
        if (callback == null) return;
        String pageUrl = currentUrl();
        if (webView == null || !pageReady || pageUrl == null) {
            callback.onReceiveValue(null);
            return;
        }
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.flush();
        Uri page = Uri.parse(pageUrl);
        Uri endpoint = page.buildUpon().path(ServerVersionDetector.API_PATH)
                .clearQuery().fragment(null).build();
        String endpointUrl = endpoint.toString();
        String cookie = cookieManager.getCookie(endpointUrl);
        String userAgent = webView.getSettings().getUserAgentString();
        webView.evaluateJavascript(
                "(function(){try{return sessionStorage.getItem('ebk_user_session_token')||" +
                        "localStorage.getItem('ebk_user_token')||'';}catch(e){return '';}})();",
                rawToken -> {
                    String token = ServerVersionDetector.decodeJavascriptString(rawToken);
                    metadataExecutor.execute(() -> {
                        String version = fetchServerVersion(endpointUrl, cookie, userAgent,
                                pageUrl, token);
                        activity.runOnUiThread(() -> {
                            if (!activity.isFinishing() && !activity.isDestroyed()) {
                                callback.onReceiveValue(version);
                            }
                        });
                    });
                });
    }

    private String fetchServerVersion(String endpoint, String cookie, String userAgent,
                                      String referer, String token) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7");
            connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            if (userAgent != null && !userAgent.trim().isEmpty()) {
                connection.setRequestProperty("User-Agent", userAgent);
            }
            if (referer != null && !referer.trim().isEmpty()) {
                connection.setRequestProperty("Referer", referer);
            }
            if (cookie != null && !cookie.isEmpty()) {
                connection.setRequestProperty("Cookie", cookie);
            }
            if (token != null && !token.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buffer = new char[2048];
                int count;
                while ((count = reader.read(buffer)) >= 0 && body.length() < 32768) {
                    body.append(buffer, 0, Math.min(count, 32768 - body.length()));
                }
            }
            return ServerVersionDetector.parseApiResponse(body.toString());
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static boolean reloadActive() {
        WebViewController controller = activeController.get();
        if (controller == null || !controller.isCreated()) return false;
        controller.reload();
        return true;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean isCreated() {
        return webView != null;
    }

    public boolean isPageReady() {
        return pageReady;
    }

    public String currentUrl() {
        return webView == null ? null : webView.getUrl();
    }

    public void loadAsHome(String url) {
        if (webView == null || url == null || url.trim().isEmpty()) return;
        webView.clearHistory();
        loadUrl(url);
        WebView active = webView;
        active.postDelayed(() -> {
            if (webView == active && active.getParent() != null) active.clearHistory();
        }, 1200L);
    }

    public void resolvePageIdentity(
            ValueCallback<EzBookkeepingPageDetector.PageIdentity> callback) {
        if (callback == null) return;
        String currentUrl = currentUrl();
        EzBookkeepingPageDetector.PageIdentity cached = pageIdentityCache.getFor(currentUrl);
        if (cached != EzBookkeepingPageDetector.PageIdentity.UNKNOWN) {
            callback.onReceiveValue(cached);
            return;
        }
        pendingIdentityCallbacks.add(callback);
        refreshPageIdentity();
    }

    public void refreshPageIdentityAfterNavigation() {
        pageIdentityCache.invalidate();
        schedulePageIdentityRefresh();
    }

    public void loadUrl(String url) {
        if (webView == null || url == null || url.trim().isEmpty()) return;
        pageReady = false;
        pageIdentityCache.invalidate();
        webView.loadUrl(url);
    }

    public void reload() {
        if (webView != null) {
            pageReady = false;
            pageIdentityCache.invalidate();
            webView.reload();
        }
    }

    public boolean canGoBack() {
        return webView != null && webView.canGoBack();
    }

    public void goBack() {
        if (webView != null) webView.goBack();
    }

    public void clearSiteData() {
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        WebStorage.getInstance().deleteAllData();
        if (webView != null) {
            webView.clearCache(true);
            webView.clearHistory();
        }
    }

    public String webViewPackageName() {
        return WebView.getCurrentWebViewPackage() == null ? null :
                WebView.getCurrentWebViewPackage().packageName;
    }

    @SuppressWarnings({"SetJavaScriptEnabled", "deprecation"})
    private void configure() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        int textZoom = Math.round(activity.getResources().getConfiguration().fontScale * 100f);
        settings.setTextZoom(Math.max(100, Math.min(150, textZoom)));
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        boolean darkMode = UiTheme.isDark(activity);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            settings.setAlgorithmicDarkeningAllowed(darkMode);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            settings.setForceDark(darkMode ? WebSettings.FORCE_DARK_ON :
                    WebSettings.FORCE_DARK_OFF);
        }
        boolean secureOrigin = baseUrl != null &&
                "https".equalsIgnoreCase(Uri.parse(baseUrl).getScheme());
        settings.setMixedContentMode(secureOrigin ? WebSettings.MIXED_CONTENT_NEVER_ALLOW :
                WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(settings.getUserAgentString()
                + " Ledgerly/" + BuildConfig.VERSION_NAME);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return host.onNavigationRequested(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return host.onNavigationRequested(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                pageReady = false;
                mainFrameFailed = false;
                pageIdentityCache.invalidate();
                showPageProgress();
                host.onPageStarted(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (mainFrameFailed) return;
                pageReady = true;
                completePageProgress();
                syncPageTheme();
                if (!preloadMode) refreshPageIdentity();
                host.onPageReady(url);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (!request.isForMainFrame()) return;
                if (mainFrameFailed) return;
                mainFrameFailed = true;
                int code = error == null ? 0 : error.getErrorCode();
                String detail = error == null || error.getDescription() == null ?
                        "网页加载失败" : error.getDescription().toString();
                host.onPageFailure(fromWebError(code, detail, request.getUrl().toString()));
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                            WebResourceResponse errorResponse) {
                if (!request.isForMainFrame() || errorResponse == null) return;
                int status = errorResponse.getStatusCode();
                if (status < 500) return;
                if (mainFrameFailed) return;
                mainFrameFailed = true;
                host.onPageFailure(new Failure(FailureType.HTTP,
                        "服务器返回错误", "HTTP " + status + " " +
                        errorResponse.getReasonPhrase(), status, request.getUrl().toString()));
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler,
                                           android.net.http.SslError error) {
                handler.cancel();
                if (mainFrameFailed) return;
                mainFrameFailed = true;
                host.onPageFailure(new Failure(FailureType.SSL,
                        "HTTPS证书验证失败",
                        error == null ? "证书无效，已阻止继续连接" : error.toString(),
                        error == null ? 0 : error.getPrimaryError(),
                        error == null ? currentUrl() : error.getUrl()));
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (pageProgress == null) return;
                if (newProgress < 100) {
                    if (pageProgress.getVisibility() != View.VISIBLE) showPageProgress();
                    pageProgress.setProgress(Math.max(5, newProgress));
                } else {
                    completePageProgress();
                }
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                host.onFileChooserRequested(callback, params);
                return true;
            }
        });
        webView.setDownloadListener(downloadController.createListener());
    }

    private void showPageProgress() {
        if (pageProgress == null) return;
        pageProgress.animate().cancel();
        pageProgress.setAlpha(1f);
        pageProgress.setProgress(5);
        pageProgress.setVisibility(View.VISIBLE);
    }

    private void completePageProgress() {
        if (pageProgress == null || pageProgress.getVisibility() != View.VISIBLE) return;
        pageProgress.setProgress(100);
        pageProgress.animate().alpha(0f).setDuration(160L).withEndAction(() -> {
            if (pageProgress == null) return;
            pageProgress.setVisibility(View.GONE);
            pageProgress.setAlpha(1f);
        }).start();
    }

    private Failure fromWebError(int code, String detail, String url) {
        FailureType type;
        String title;
        switch (code) {
            case WebViewClient.ERROR_HOST_LOOKUP:
                type = FailureType.DNS;
                title = "无法解析服务器地址";
                break;
            case WebViewClient.ERROR_TIMEOUT:
                type = FailureType.TIMEOUT;
                title = "连接服务器超时";
                break;
            case WebViewClient.ERROR_CONNECT:
                type = FailureType.NETWORK;
                title = "无法连接服务器";
                break;
            default:
                type = FailureType.UNKNOWN;
                title = "页面加载失败";
                break;
        }
        return new Failure(type, title, detail, code, url);
    }

    private void setupHiddenGesture() {
        webView.setOnTouchListener((view, event) -> {
            long now = System.currentTimeMillis();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() == 2) {
                        twoFingerTapCandidate = true;
                        twoFingerTapStartedAt = now;
                        twoFingerTapStartX = averageX(event);
                        twoFingerTapStartY = averageY(event);
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (twoFingerTapCandidate &&
                            (event.getPointerCount() < 2 || movedTooMuch(event))) {
                        twoFingerTapCandidate = false;
                    }
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    if (twoFingerTapCandidate && event.getPointerCount() == 2 &&
                            now - twoFingerTapStartedAt <= 300L) {
                        registerTwoFingerTap(now, averageX(event), averageY(event));
                    }
                    twoFingerTapCandidate = false;
                    break;
                case MotionEvent.ACTION_UP:
                    if (suppressNextIdentityRefresh) {
                        suppressNextIdentityRefresh = false;
                    } else {
                        schedulePageIdentityRefresh();
                    }
                case MotionEvent.ACTION_CANCEL:
                    twoFingerTapCandidate = false;
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    private void registerTwoFingerTap(long now, float x, float y) {
        boolean secondTap = firstTwoFingerTapAt > 0L &&
                now - firstTwoFingerTapAt <= 550L &&
                distance(x, y, firstTwoFingerTapX, firstTwoFingerTapY) <= dp(90);
        if (secondTap) {
            firstTwoFingerTapAt = 0L;
            if (webView != null) {
                suppressNextIdentityRefresh = true;
                webView.removeCallbacks(pageIdentityRefresh);
                webView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                host.onOpenQuickActions();
            }
        } else {
            firstTwoFingerTapAt = now;
            firstTwoFingerTapX = x;
            firstTwoFingerTapY = y;
        }
    }

    private void schedulePageIdentityRefresh() {
        if (webView == null) return;
        String currentUrl = webView.getUrl();
        if (pageIdentityCache.isValidFor(currentUrl)) {
            return;
        }
        webView.removeCallbacks(pageIdentityRefresh);
        webView.postDelayed(pageIdentityRefresh, 220L);
    }

    private void refreshPageIdentity() {
        if (preloadMode) return;
        WebView active = webView;
        if (active == null || identityCheckInProgress) return;
        identityCheckInProgress = true;
        String identityUrl = active.getUrl();
        active.evaluateJavascript(EzBookkeepingPageDetector.homeDetectionScript(), result -> {
            identityCheckInProgress = false;
            if (webView != active) {
                deliverIdentityCallbacks(EzBookkeepingPageDetector.PageIdentity.UNKNOWN);
                return;
            }
            String currentUrl = active.getUrl();
            if (identityUrl == null ? currentUrl != null : !identityUrl.equals(currentUrl)) {
                deliverIdentityCallbacks(EzBookkeepingPageDetector.PageIdentity.UNKNOWN);
                return;
            }
            pageIdentityCache.update(identityUrl, EzBookkeepingPageDetector.parseIdentity(result));
            deliverIdentityCallbacks(pageIdentityCache.get());
        });
    }

    private void deliverIdentityCallbacks(EzBookkeepingPageDetector.PageIdentity identity) {
        if (pendingIdentityCallbacks.isEmpty()) return;
        List<ValueCallback<EzBookkeepingPageDetector.PageIdentity>> callbacks =
                new ArrayList<>(pendingIdentityCallbacks);
        pendingIdentityCallbacks.clear();
        for (ValueCallback<EzBookkeepingPageDetector.PageIdentity> callback : callbacks) {
            callback.onReceiveValue(identity);
        }
    }

    private boolean movedTooMuch(MotionEvent event) {
        return Math.abs(averageX(event) - twoFingerTapStartX) > dp(28) ||
                Math.abs(averageY(event) - twoFingerTapStartY) > dp(28);
    }

    private float averageX(MotionEvent event) {
        int count = Math.min(2, event.getPointerCount());
        float total = 0f;
        for (int i = 0; i < count; i++) total += event.getX(i);
        return count == 0 ? 0f : total / count;
    }

    private float averageY(MotionEvent event) {
        int count = Math.min(2, event.getPointerCount());
        float total = 0f;
        for (int i = 0; i < count; i++) total += event.getY(i);
        return count == 0 ? 0f : total / count;
    }

    private float distance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    public void destroy() {
        if (activeController.get() == this) activeController.clear();
        identityCheckInProgress = false;
        pendingIdentityCallbacks.clear();
        if (webView != null) {
            webView.removeCallbacks(pageIdentityRefresh);
            webView.setOnTouchListener(null);
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        if (pageProgress != null) {
            pageProgress.animate().cancel();
            pageProgress = null;
        }
        pageReady = false;
        mainFrameFailed = false;
        pageIdentityCache.invalidate();
    }
}
