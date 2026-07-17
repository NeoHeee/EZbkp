package com.neo.ezaccounting;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class MainActivity extends Activity {
    private static final String PREFS = "ez_accounting_prefs";
    private static final String KEY_LOCAL_URL = "local_url";
    private static final String KEY_PUBLIC_URL = "public_url";
    private static final String KEY_LAST_ROUTE = "last_route";

    private static final int FILE_CHOOSER_REQUEST = 1010;
    private static final int STORAGE_PERMISSION_REQUEST = 1011;

    private static final int ROUTE_NONE = 0;
    private static final int ROUTE_LOCAL = 1;
    private static final int ROUTE_PUBLIC = 2;

    private SharedPreferences preferences;
    private WebView webView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ValueCallback<Uri[]> filePathCallback;
    private String localUrl;
    private String publicUrl;
    private String activeBaseUrl;
    private long lastBackPressedAt;
    private boolean showingWebView;
    private boolean hasLoadedSuccessfully;
    private boolean fallbackAttempted;
    private int activeRoute = ROUTE_NONE;

    private final Handler gestureHandler = new Handler(Looper.getMainLooper());
    private Runnable quickMenuRunnable;
    private boolean twoFingerGesturePending;
    private boolean quickMenuTriggered;
    private float gestureStartX;
    private float gestureStartY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        localUrl = preferences.getString(KEY_LOCAL_URL, "");
        publicUrl = preferences.getString(KEY_PUBLIC_URL, "");

        if (hasSavedRoutes()) {
            launchPreferredRoute(true);
        } else {
            showServerSetup();
        }
    }

    private boolean hasSavedRoutes() {
        return !isBlank(localUrl) || !isBlank(publicUrl);
    }

    private void showServerSetup() {
        showingWebView = false;
        destroyWebViewIfNeeded();

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(245, 248, 248));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        int horizontal = dp(28);
        content.setPadding(horizontal, dp(42), horizontal, dp(28));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView logo = new TextView(this);
        logo.setText("¥✓");
        logo.setTextSize(29);
        logo.setTextColor(Color.WHITE);
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable logoBackground = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(15, 118, 110), Color.rgb(14, 165, 164)});
        logoBackground.setCornerRadius(dp(24));
        logo.setBackground(logoBackground);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(84), dp(84));
        logoParams.bottomMargin = dp(24);
        content.addView(logo, logoParams);

        TextView title = new TextView(this);
        title.setText("配置 ezBookkeeping 地址");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(20, 35, 35));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        content.addView(title, matchWrap(dp(10)));

        TextView description = new TextView(this);
        description.setText("可同时填写本地地址和公网地址。保存后，应用会在每次启动时自动优先测试本地线路，失败后再回退到公网线路。\n\n建议：\n本地地址填写 NAS 局域网地址\n公网地址填写反向代理 HTTPS 地址");
        description.setTextSize(14.5f);
        description.setTextColor(Color.rgb(80, 95, 95));
        description.setGravity(Gravity.CENTER);
        description.setLineSpacing(0, 1.18f);
        content.addView(description, matchWrap(dp(24)));

        TextView localLabel = createFieldLabel("本地地址（优先）");
        content.addView(localLabel, matchWrap(dp(8)));

        EditText localInput = createUrlInput("http://192.168.1.100:8080", localUrl);
        content.addView(localInput, matchWrap(dp(16)));

        TextView publicLabel = createFieldLabel("公网地址（备用）");
        content.addView(publicLabel, matchWrap(dp(8)));

        EditText publicInput = createUrlInput("https://money.example.com", publicUrl);
        content.addView(publicInput, matchWrap(dp(18)));

        Button connectButton = new Button(this);
        connectButton.setText("保存并连接");
        connectButton.setTextSize(16);
        connectButton.setTextColor(Color.WHITE);
        connectButton.setAllCaps(false);
        GradientDrawable buttonBackground = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(15, 118, 110), Color.rgb(13, 148, 136)});
        buttonBackground.setCornerRadius(dp(14));
        connectButton.setBackground(buttonBackground);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        content.addView(connectButton, buttonParams);

        TextView note = new TextView(this);
        note.setText("进入记账界面后不会显示额外顶部栏。\n下拉页面可刷新；双指长按页面可打开隐藏功能菜单。\n公网访问建议使用有效 HTTPS 证书。应用不会忽略无效证书。\n地址仅保存在本机。 ");
        note.setTextSize(12.5f);
        note.setTextColor(Color.rgb(105, 120, 120));
        note.setGravity(Gravity.CENTER);
        note.setLineSpacing(0, 1.15f);
        content.addView(note, matchWrap(dp(22)));

        connectButton.setOnClickListener(v -> {
            String normalizedLocal = normalizeServerUrl(localInput.getText().toString());
            String normalizedPublic = normalizeServerUrl(publicInput.getText().toString());

            if (isBlank(localInput.getText().toString()) && isBlank(publicInput.getText().toString())) {
                localInput.setError("请至少填写一个地址");
                return;
            }
            if (!isBlank(localInput.getText().toString()) && normalizedLocal == null) {
                localInput.setError("请输入有效的 HTTP 或 HTTPS 地址");
                return;
            }
            if (!isBlank(publicInput.getText().toString()) && normalizedPublic == null) {
                publicInput.setError("请输入有效的 HTTP 或 HTTPS 地址");
                return;
            }

            localUrl = normalizedLocal == null ? "" : normalizedLocal;
            publicUrl = normalizedPublic == null ? "" : normalizedPublic;
            preferences.edit()
                    .putString(KEY_LOCAL_URL, localUrl)
                    .putString(KEY_PUBLIC_URL, publicUrl)
                    .apply();
            launchPreferredRoute(true);
        });

        setContentView(scrollView);
    }

    private TextView createFieldLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(14.5f);
        label.setTextColor(Color.rgb(26, 51, 51));
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        return label;
    }

    private EditText createUrlInput(String hint, String value) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setText(value == null ? "" : value);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setTextSize(16);
        input.setPadding(dp(16), dp(13), dp(16), dp(13));
        GradientDrawable inputBackground = new GradientDrawable();
        inputBackground.setColor(Color.WHITE);
        inputBackground.setStroke(dp(1), Color.rgb(196, 210, 208));
        inputBackground.setCornerRadius(dp(14));
        input.setBackground(inputBackground);
        return input;
    }

    private void launchPreferredRoute(boolean fromStartup) {
        showLoadingScreen("正在检测可用线路…");
        new Thread(() -> {
            RouteSelection selection = determinePreferredRoute();
            runOnUiThread(() -> {
                if (selection.url == null) {
                    Toast.makeText(MainActivity.this,
                            "没有可用线路，请检查本地地址、公网地址或网络连接",
                            Toast.LENGTH_LONG).show();
                    showServerSetup();
                    return;
                }
                activeBaseUrl = selection.url;
                activeRoute = selection.routeType;
                preferences.edit().putString(KEY_LAST_ROUTE, activeBaseUrl).apply();
                showWebClient(activeBaseUrl);
                if (!fromStartup) {
                    Toast.makeText(MainActivity.this,
                            selection.routeType == ROUTE_LOCAL ? "已切换到本地线路" : "已切换到公网线路",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private RouteSelection determinePreferredRoute() {
        if (!isBlank(localUrl) && isReachable(localUrl, 1400)) {
            return new RouteSelection(localUrl, ROUTE_LOCAL);
        }
        if (!isBlank(publicUrl) && isReachable(publicUrl, 2400)) {
            return new RouteSelection(publicUrl, ROUTE_PUBLIC);
        }
        return new RouteSelection(null, ROUTE_NONE);
    }

    private boolean isReachable(String urlString, int timeoutMs) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "EZAccounting/1.1.0");
            connection.connect();
            int code = connection.getResponseCode();
            return (code >= 200 && code < 400) || code == 401 || code == 403;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void showLoadingScreen(String text) {
        showingWebView = false;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(24), dp(24), dp(24), dp(24));

        android.widget.ProgressBar progress = new android.widget.ProgressBar(this);
        box.addView(progress);

        TextView message = new TextView(this);
        message.setText(text);
        message.setTextSize(15);
        message.setTextColor(Color.rgb(70, 86, 86));
        message.setPadding(0, dp(14), 0, 0);
        box.addView(message);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        root.addView(box, params);
        setContentView(root);
    }

    private LinearLayout.LayoutParams matchWrap(int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = bottomMargin;
        return params;
    }

    private String normalizeServerUrl(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty()) return null;

        if (!value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*$")) {
            value = looksLikePrivateHost(value) ? "http://" + value : "https://" + value;
        }

        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null) return null;
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return null;
            String normalized = uri.toString();
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        } catch (URISyntaxException error) {
            return null;
        }
    }

    private boolean looksLikePrivateHost(String value) {
        String host = value.split("[/?:]", 2)[0].toLowerCase();
        return host.equals("localhost") || host.endsWith(".local") ||
                host.startsWith("10.") || host.startsWith("192.168.") ||
                host.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*") ||
                host.matches("[0-9a-f:]+") || !host.contains(".");
    }

    private void showWebClient(String url) {
        showingWebView = true;
        hasLoadedSuccessfully = false;
        fallbackAttempted = false;

        FrameLayout root = new FrameLayout(this);
        swipeRefreshLayout = new SwipeRefreshLayout(this);
        swipeRefreshLayout.setColorSchemeColors(Color.rgb(13, 148, 136));
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (webView != null) webView.reload();
        });

        webView = new WebView(this);
        swipeRefreshLayout.addView(webView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(swipeRefreshLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        configureWebView();
        setupHiddenGestureMenu();
        webView.loadUrl(url);
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(settings.getUserAgentString() + " EZAccounting/1.1.0");

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                hasLoadedSuccessfully = true;
                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    if (!hasLoadedSuccessfully && tryFallbackRoute()) {
                        return;
                    }
                    Toast.makeText(MainActivity.this,
                            "页面加载失败，请检查线路地址和网络连接",
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler,
                                           android.net.http.SslError error) {
                handler.cancel();
                if (!hasLoadedSuccessfully && tryFallbackRoute()) {
                    return;
                }
                Toast.makeText(MainActivity.this,
                        "HTTPS 证书无效，已阻止继续连接",
                        Toast.LENGTH_LONG).show();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> newCallback,
                                             FileChooserParams fileChooserParams) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = newCallback;

                Intent intent;
                try {
                    intent = fileChooserParams.createIntent();
                } catch (Exception ignored) {
                    intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                }

                try {
                    startActivityForResult(Intent.createChooser(intent, "选择账单、图片或附件"),
                            FILE_CHOOSER_REQUEST);
                    return true;
                } catch (ActivityNotFoundException error) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "没有可用的文件选择器", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        webView.setDownloadListener(createDownloadListener());
    }

    private boolean tryFallbackRoute() {
        if (fallbackAttempted) return false;
        String fallbackUrl = null;
        int fallbackRoute = ROUTE_NONE;
        if (activeRoute == ROUTE_LOCAL && !isBlank(publicUrl)) {
            fallbackUrl = publicUrl;
            fallbackRoute = ROUTE_PUBLIC;
        } else if (activeRoute == ROUTE_PUBLIC && !isBlank(localUrl)) {
            fallbackUrl = localUrl;
            fallbackRoute = ROUTE_LOCAL;
        }
        if (fallbackUrl == null) return false;

        fallbackAttempted = true;
        activeRoute = fallbackRoute;
        activeBaseUrl = fallbackUrl;
        if (webView != null) {
            Toast.makeText(this,
                    fallbackRoute == ROUTE_PUBLIC ? "本地线路不可用，正在切换到公网线路" : "公网线路不可用，正在切换到本地线路",
                    Toast.LENGTH_SHORT).show();
            webView.loadUrl(fallbackUrl);
            return true;
        }
        return false;
    }

    private void setupHiddenGestureMenu() {
        quickMenuRunnable = () -> {
            if (!twoFingerGesturePending || quickMenuTriggered) return;
            quickMenuTriggered = true;
            if (webView != null) {
                webView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
            openQuickActions();
        };

        webView.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() >= 2 && !twoFingerGesturePending) {
                        twoFingerGesturePending = true;
                        quickMenuTriggered = false;
                        gestureStartX = averageX(event);
                        gestureStartY = averageY(event);
                        gestureHandler.postDelayed(quickMenuRunnable, 650);
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (twoFingerGesturePending) {
                        if (event.getPointerCount() < 2 || movedTooMuch(event)) {
                            cancelQuickMenuGesture();
                        }
                    }
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!quickMenuTriggered) cancelQuickMenuGesture();
                    if (quickMenuTriggered && event.getActionMasked() == MotionEvent.ACTION_UP) {
                        quickMenuTriggered = false;
                        return true;
                    }
                    break;
            }
            return false;
        });
    }

    private void cancelQuickMenuGesture() {
        twoFingerGesturePending = false;
        gestureHandler.removeCallbacks(quickMenuRunnable);
    }

    private boolean movedTooMuch(MotionEvent event) {
        float dx = Math.abs(averageX(event) - gestureStartX);
        float dy = Math.abs(averageY(event) - gestureStartY);
        return dx > dp(24) || dy > dp(24);
    }

    private float averageX(MotionEvent event) {
        int count = Math.min(2, event.getPointerCount());
        float total = 0f;
        for (int i = 0; i < count; i++) total += event.getX(i);
        return total / count;
    }

    private float averageY(MotionEvent event) {
        int count = Math.min(2, event.getPointerCount());
        float total = 0f;
        for (int i = 0; i < count; i++) total += event.getY(i);
        return total / count;
    }

    private void openQuickActions() {
        cancelQuickMenuGesture();
        CharSequence[] items = new CharSequence[]{
                "返回首页",
                "重新检测线路",
                "在浏览器中打开",
                "更换线路地址",
                "清除登录与缓存",
                "WebView 信息"
        };
        new AlertDialog.Builder(this)
                .setTitle(activeRoute == ROUTE_LOCAL ? "隐藏功能菜单（当前：本地线路）" :
                        activeRoute == ROUTE_PUBLIC ? "隐藏功能菜单（当前：公网线路）" : "隐藏功能菜单")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            if (webView != null) webView.loadUrl(activeBaseUrl);
                            break;
                        case 1:
                            launchPreferredRoute(false);
                            break;
                        case 2:
                            if (webView != null) {
                                openExternal(Uri.parse(webView.getUrl() == null ? activeBaseUrl : webView.getUrl()));
                            }
                            break;
                        case 3:
                            confirmChangeServer();
                            break;
                        case 4:
                            confirmClearSiteData();
                            break;
                        case 5:
                            try {
                                startActivity(new Intent(Settings.ACTION_WEBVIEW_SETTINGS));
                            } catch (Exception ignored) {
                                Toast.makeText(this, WebView.getCurrentWebViewPackage() == null ?
                                                "无法读取 WebView 信息" :
                                                WebView.getCurrentWebViewPackage().packageName,
                                        Toast.LENGTH_LONG).show();
                            }
                            break;
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private boolean handleNavigation(Uri uri) {
        String scheme = uri.getScheme();
        if (scheme == null) return false;
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            Uri base = Uri.parse(activeBaseUrl);
            if (base.getHost() != null && base.getHost().equalsIgnoreCase(uri.getHost())) {
                return false;
            }
            openExternal(uri);
            return true;
        }
        openExternal(uri);
        return true;
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "无法打开该链接", Toast.LENGTH_SHORT).show();
        }
    }

    private DownloadListener createDownloadListener() {
        return (url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                            PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        STORAGE_PERMISSION_REQUEST);
                Toast.makeText(this, "授权后请再次点击下载", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                request.addRequestHeader("User-Agent", userAgent);
                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null) request.addRequestHeader("Cookie", cookies);
                request.setTitle(fileName);
                request.setDescription("EZ记账正在下载");
                request.setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                manager.enqueue(request);
                Toast.makeText(this, "已开始下载：" + fileName, Toast.LENGTH_SHORT).show();
            } catch (Exception error) {
                Toast.makeText(this, "下载失败，可尝试用浏览器打开", Toast.LENGTH_LONG).show();
            }
        };
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    private void confirmChangeServer() {
        new AlertDialog.Builder(this)
                .setTitle("更换线路地址")
                .setMessage("将返回线路设置页。当前登录 Cookie 会保留，除非你主动清除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("继续", (dialog, which) -> showServerSetup())
                .show();
    }

    private void confirmClearSiteData() {
        new AlertDialog.Builder(this)
                .setTitle("清除登录与缓存")
                .setMessage("这会退出当前账号并清除网页缓存，但不会删除本地/公网地址配置。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清除", (dialog, which) -> {
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                    WebStorage.getInstance().deleteAllData();
                    if (webView != null) {
                        webView.clearCache(true);
                        webView.clearHistory();
                        webView.loadUrl(activeBaseUrl);
                    }
                    Toast.makeText(this, "已清除登录与缓存", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    @Override
    public void onBackPressed() {
        if (showingWebView && webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }

        long now = System.currentTimeMillis();
        if (showingWebView && now - lastBackPressedAt > 1800) {
            lastBackPressedAt = now;
            Toast.makeText(this, "再按一次返回键退出", Toast.LENGTH_SHORT).show();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        cancelQuickMenuGesture();
        destroyWebViewIfNeeded();
        super.onDestroy();
    }

    private void destroyWebViewIfNeeded() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        swipeRefreshLayout = null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class RouteSelection {
        final String url;
        final int routeType;

        RouteSelection(String url, int routeType) {
            this.url = url;
            this.routeType = routeType;
        }
    }
}
