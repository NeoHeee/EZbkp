package com.neo.ezaccounting;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
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

import java.lang.ref.WeakReference;

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

    private final Activity activity;
    private final Host host;
    private final DownloadController downloadController;

    private WebView webView;
    private String baseUrl;
    private boolean pageReady;

    private boolean twoFingerTapCandidate;
    private long twoFingerTapStartedAt;
    private float twoFingerTapStartX;
    private float twoFingerTapStartY;
    private long firstTwoFingerTapAt;
    private float firstTwoFingerTapX;
    private float firstTwoFingerTapY;

    public WebViewController(Activity activity, Host host,
                             DownloadController downloadController) {
        this.activity = activity;
        this.host = host;
        this.downloadController = downloadController;
    }

    public View create(String baseUrl, String initialUrl) {
        destroy();
        this.baseUrl = baseUrl;
        pageReady = false;

        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(UiTheme.webBackground(activity));

        webView = new WebView(activity);
        webView.setBackgroundColor(UiTheme.webBackground(activity));
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        activeController = new WeakReference<>(this);

        configure();
        setupHiddenGesture();
        loadUrl(initialUrl == null || initialUrl.trim().isEmpty() ? baseUrl : initialUrl);
        return root;
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

    public void loadUrl(String url) {
        if (webView == null || url == null || url.trim().isEmpty()) return;
        pageReady = false;
        webView.loadUrl(url);
    }

    public void reload() {
        if (webView != null) {
            pageReady = false;
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
        settings.setUserAgentString(settings.getUserAgentString() + " EZAccounting/1.5.5");

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
                host.onPageStarted(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                host.onPageReady(url);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (!request.isForMainFrame()) return;
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
                host.onPageFailure(new Failure(FailureType.HTTP,
                        "服务器返回错误", "HTTP " + status + " " +
                        errorResponse.getReasonPhrase(), status, request.getUrl().toString()));
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler,
                                           android.net.http.SslError error) {
                handler.cancel();
                host.onPageFailure(new Failure(FailureType.SSL,
                        "HTTPS证书验证失败",
                        error == null ? "证书无效，已阻止继续连接" : error.toString(),
                        error == null ? 0 : error.getPrimaryError(),
                        error == null ? currentUrl() : error.getUrl()));
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                host.onFileChooserRequested(callback, params);
                return true;
            }
        });
        webView.setDownloadListener(downloadController.createListener());
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
                webView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                webView.post(host::onOpenQuickActions);
            }
        } else {
            firstTwoFingerTapAt = now;
            firstTwoFingerTapX = x;
            firstTwoFingerTapY = y;
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
        if (webView != null) {
            webView.setOnTouchListener(null);
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        pageReady = false;
    }
}
