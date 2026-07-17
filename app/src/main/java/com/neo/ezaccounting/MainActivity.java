package com.neo.ezaccounting;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
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

import androidx.fragment.app.FragmentActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class MainActivity extends FragmentActivity {
    private static final String PREFS = "ez_accounting_prefs";
    private static final String KEY_LOCAL_URL = "local_url";
    private static final String KEY_PUBLIC_URL = "public_url";
    private static final String KEY_LAST_ROUTE = "last_route";
    private static final String KEY_LOCAL_LATENCY = "local_latency_ms";
    private static final String KEY_PUBLIC_LATENCY = "public_latency_ms";
    private static final String KEY_LAST_UPDATE_CHECK = "last_update_check";
    private static final long AUTO_UPDATE_INTERVAL_MS = 24L * 60L * 60L * 1000L;

    private static final int FILE_CHOOSER_REQUEST = 1010;
    private static final int STORAGE_PERMISSION_REQUEST = 1011;
    private static final int REQUEST_UNLOCK_APP = 2001;
    private static final int REQUEST_VERIFY_SETTINGS = 2002;
    private static final int REQUEST_SECURITY_SETTINGS = 2003;

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

    private boolean appInitialized;
    private boolean authFlowInProgress;
    private long backgroundAt;
    private boolean forceRelock;
    private boolean screenReceiverRegistered;
    private boolean downloadReceiverRegistered;
    private boolean routeCheckInProgress;
    private long lastUnlockAt;
    private long lastDownloadId = -1L;
    private String lastDownloadFileName = "";
    private String pendingShortcutAction;
    private Uri pendingCameraUri;
    private RouteManager routeManager;
    private RouteManager.Selection lastRouteSelection;
    private NetworkMonitor networkMonitor;

    private final BroadcastReceiver downloadCompleteReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
            if (id == lastDownloadId) showDownloadedFileDialog(id, lastDownloadFileName);
        }
    };

    private final BroadcastReceiver screenOffReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction()) &&
                    appInitialized && !authFlowInProgress && AppSecurity.isEnabled(MainActivity.this) &&
                    AppSecurity.isLockOnScreenOff(MainActivity.this)) {
                forceRelock = true;
                backgroundAt = System.currentTimeMillis();
            }
        }
    };

    private boolean twoFingerTapCandidate;
    private long twoFingerTapStartedAt;
    private float twoFingerTapStartX;
    private float twoFingerTapStartY;
    private long firstTwoFingerTapAt;
    private float firstTwoFingerTapX;
    private float firstTwoFingerTapY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiTheme.applySystemBars(this);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        localUrl = preferences.getString(KEY_LOCAL_URL, "");
        publicUrl = preferences.getString(KEY_PUBLIC_URL, "");
        pendingShortcutAction = ShortcutActions.read(getIntent());
        routeManager = new RouteManager();
        networkMonitor = new NetworkMonitor(this, this::onDefaultNetworkChanged);
        registerScreenOffReceiver();
        registerDownloadReceiver();

        if (ShortcutActions.LOCK.equals(pendingShortcutAction) && AppSecurity.isEnabled(this)) {
            pendingShortcutAction = null;
        }

        if (AppSecurity.isEnabled(this)) {
            showLoadingScreen("请完成安全验证…");
            requestAppUnlock();
        } else {
            initializeApp();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String action = ShortcutActions.read(intent);
        if (action == null) return;
        pendingShortcutAction = action;
        if (appInitialized && !authFlowInProgress) {
            getWindow().getDecorView().post(this::handlePendingShortcutAction);
        }
    }

    private void initializeApp() {
        if (appInitialized) return;
        appInitialized = true;
        if (ShortcutActions.ROUTES.equals(pendingShortcutAction)) {
            pendingShortcutAction = null;
            showServerSetup();
        } else if (hasSavedRoutes()) {
            launchPreferredRoute(true);
        } else {
            showServerSetup();
        }
        getWindow().getDecorView().post(() -> {
            handlePendingShortcutAction();
            scheduleAutomaticUpdateCheck();
        });
    }

    private void handlePendingShortcutAction() {
        if (authFlowInProgress || pendingShortcutAction == null) return;
        String action = pendingShortcutAction;
        pendingShortcutAction = null;
        if (ShortcutActions.ROUTES.equals(action)) {
            showServerSetup();
        } else if (ShortcutActions.SECURITY.equals(action)) {
            if (!AppSecurity.isEnabled(this) || System.currentTimeMillis() - lastUnlockAt < 10000L) {
                openSecuritySettings();
            } else {
                requestSecuritySettings();
            }
        } else if (ShortcutActions.LOCK.equals(action)) {
            lockImmediately();
        } else if (ShortcutActions.UPDATE.equals(action)) {
            checkForUpdates(true);
        }
    }

    private void requestAppUnlock() {
        if (authFlowInProgress) return;
        authFlowInProgress = true;
        Intent intent = new Intent(this, LockActivity.class);
        intent.putExtra(LockActivity.EXTRA_REASON, "请验证身份后进入应用");
        startActivityForResult(intent, REQUEST_UNLOCK_APP);
    }

    private boolean hasSavedRoutes() {
        return !isBlank(localUrl) || !isBlank(publicUrl);
    }

    private void showServerSetup() {
        showingWebView = false;
        destroyWebViewIfNeeded();

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(UiTheme.background(this));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(28), dp(42), dp(28), dp(28));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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
        title.setTextColor(UiTheme.primaryText(this));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        content.addView(title, matchWrap(dp(10)));

        TextView description = new TextView(this);
        description.setText("可同时填写本地地址和公网地址。保存后，应用会在每次启动时自动优先测试本地线路，失败后再回退到公网线路。\n\n建议：\n本地地址填写 NAS 局域网地址\n公网地址填写反向代理 HTTPS 地址");
        description.setTextSize(14.5f);
        description.setTextColor(UiTheme.secondaryText(this));
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
        content.addView(connectButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        TextView note = new TextView(this);
        note.setText("进入记账界面后不会显示额外顶部栏。\n下拉页面可刷新；双指快速双击可打开隐藏功能菜单。\n公网访问建议使用有效 HTTPS 证书，应用不会忽略无效证书。\n地址与安全设置仅保存在本机。");
        note.setTextSize(12.5f);
        note.setTextColor(UiTheme.tertiaryText(this));
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
            preferences.edit().putString(KEY_LOCAL_URL, localUrl)
                    .putString(KEY_PUBLIC_URL, publicUrl).apply();
            launchPreferredRoute(true);
        });
        setContentView(scrollView);
    }

    private TextView createFieldLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(14.5f);
        label.setTextColor(UiTheme.primaryText(this));
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
        input.setTextColor(UiTheme.primaryText(this));
        input.setHintTextColor(UiTheme.tertiaryText(this));
        input.setPadding(dp(16), dp(13), dp(16), dp(13));
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiTheme.surface(this));
        background.setStroke(dp(1), UiTheme.border(this));
        background.setCornerRadius(dp(14));
        input.setBackground(background);
        return input;
    }

    private void launchPreferredRoute(boolean fromStartup) {
        recheckRoutes(fromStartup, false);
    }

    private void recheckRoutes(boolean fromStartup, boolean networkTriggered) {
        if (routeCheckInProgress || routeManager == null) return;
        routeCheckInProgress = true;
        if (fromStartup && webView == null) showLoadingScreen("正在并行检测可用线路…");
        String lastRoute = preferences.getString(KEY_LAST_ROUTE, "");
        routeManager.selectAsync(localUrl, publicUrl, lastRoute, selection -> {
            routeCheckInProgress = false;
            if (isFinishing() || isDestroyed()) return;
            applyRouteSelection(selection, fromStartup, networkTriggered);
        });
    }

    private void applyRouteSelection(RouteManager.Selection selection, boolean fromStartup,
                                     boolean networkTriggered) {
        lastRouteSelection = selection;
        preferences.edit()
                .putLong(KEY_LOCAL_LATENCY, selection.local == null ? -1L : selection.local.latencyMs)
                .putLong(KEY_PUBLIC_LATENCY,
                        selection.publicRoute == null ? -1L : selection.publicRoute.latencyMs)
                .apply();

        if (!selection.hasRoute()) {
            if (fromStartup || webView == null) {
                Toast.makeText(this, "没有可用线路，请检查地址或网络连接", Toast.LENGTH_LONG).show();
                showServerSetup();
            } else if (!networkTriggered) {
                Toast.makeText(this, "当前两条线路都不可用", Toast.LENGTH_LONG).show();
            }
            return;
        }

        RouteManager.ProbeResult selected = selection.selected;
        preferences.edit().putString(KEY_LAST_ROUTE, selected.url).apply();
        boolean changed = activeBaseUrl == null || !activeBaseUrl.equals(selected.url);
        String targetUrl = changed ? remapCurrentUrl(selected.url) : selected.url;
        activeBaseUrl = selected.url;
        activeRoute = selected.type;

        if (webView == null) {
            showWebClient(targetUrl);
        } else if (changed) {
            fallbackAttempted = false;
            webView.loadUrl(targetUrl);
            Toast.makeText(this, "已自动切换到" + routeName(activeRoute) +
                    "（" + selected.latencyMs + " ms）", Toast.LENGTH_SHORT).show();
        } else if (!fromStartup && !networkTriggered) {
            Toast.makeText(this, "当前使用" + routeName(activeRoute) +
                    "，延迟 " + selected.latencyMs + " ms", Toast.LENGTH_SHORT).show();
        }
    }

    private String remapCurrentUrl(String newBaseUrl) {
        if (webView == null || activeBaseUrl == null || webView.getUrl() == null) return newBaseUrl;
        try {
            URI oldBase = new URI(activeBaseUrl);
            URI current = new URI(webView.getUrl());
            if (!WebOriginPolicy.isSameOrigin(activeBaseUrl, current.toString())) return newBaseUrl;
            URI nextBase = new URI(newBaseUrl);
            return new URI(nextBase.getScheme(), nextBase.getAuthority(), current.getPath(),
                    current.getQuery(), current.getFragment()).toString();
        } catch (Exception ignored) {
            return newBaseUrl;
        }
    }

    private String routeName(int routeType) {
        return routeType == ROUTE_LOCAL ? "本地线路" :
                routeType == ROUTE_PUBLIC ? "公网线路" : "未知线路";
    }

    private void showLoadingScreen(String text) {
        showingWebView = false;
        UiTheme.applySystemBars(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(UiTheme.background(this));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(24), dp(24), dp(24), dp(24));
        box.addView(new android.widget.ProgressBar(this));
        TextView message = new TextView(this);
        message.setText(text);
        message.setTextSize(15);
        message.setTextColor(UiTheme.secondaryText(this));
        message.setPadding(0, dp(14), 0, 0);
        box.addView(message);
        root.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        setContentView(root);
    }

    private LinearLayout.LayoutParams matchWrap(int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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
            while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
            return normalized;
        } catch (URISyntaxException error) {
            return null;
        }
    }

    private boolean looksLikePrivateHost(String value) {
        String host = value.split("[/?:]", 2)[0].toLowerCase();
        return host.equals("localhost") || host.endsWith(".local") || host.startsWith("10.") ||
                host.startsWith("192.168.") || host.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*") ||
                host.matches("[0-9a-f:]+") || !host.contains(".");
    }

    private void showWebClient(String url) {
        showingWebView = true;
        hasLoadedSuccessfully = false;
        fallbackAttempted = false;
        UiTheme.applySystemBars(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(UiTheme.webBackground(this));
        swipeRefreshLayout = new SwipeRefreshLayout(this);
        swipeRefreshLayout.setColorSchemeColors(UiTheme.accent(this));
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(UiTheme.surface(this));
        swipeRefreshLayout.setOnRefreshListener(() -> { if (webView != null) webView.reload(); });
        webView = new WebView(this);
        webView.setBackgroundColor(UiTheme.webBackground(this));
        swipeRefreshLayout.addView(webView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(swipeRefreshLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        configureWebView();
        setupHiddenGestureMenu();
        webView.loadUrl(url);
    }

    @SuppressWarnings({"SetJavaScriptEnabled", "deprecation"})
    private void configureWebView() {
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
        boolean darkMode = UiTheme.isDark(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            settings.setAlgorithmicDarkeningAllowed(darkMode);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            settings.setForceDark(darkMode ? WebSettings.FORCE_DARK_ON : WebSettings.FORCE_DARK_OFF);
        }
        boolean secureOrigin = activeBaseUrl != null &&
                "https".equalsIgnoreCase(Uri.parse(activeBaseUrl).getScheme());
        settings.setMixedContentMode(secureOrigin ?
                WebSettings.MIXED_CONTENT_NEVER_ALLOW :
                WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(settings.getUserAgentString() + " EZAccounting/1.4.0");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(Uri.parse(url));
            }
            @Override public void onPageFinished(WebView view, String url) {
                hasLoadedSuccessfully = true;
                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    if (!hasLoadedSuccessfully && tryFallbackRoute()) return;
                    Toast.makeText(MainActivity.this, "页面加载失败，请检查线路地址和网络连接",
                            Toast.LENGTH_LONG).show();
                }
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler,
                                                     android.net.http.SslError error) {
                handler.cancel();
                if (!hasLoadedSuccessfully && tryFallbackRoute()) return;
                Toast.makeText(MainActivity.this, "HTTPS 证书无效，已阻止继续连接",
                        Toast.LENGTH_LONG).show();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    FileChooserSupport.Request request = FileChooserSupport.create(MainActivity.this, params);
                    pendingCameraUri = request.cameraUri;
                    startActivityForResult(request.chooserIntent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception error) {
                    filePathCallback = null;
                    pendingCameraUri = null;
                    Toast.makeText(MainActivity.this, "无法打开照片或文件选择器",
                            Toast.LENGTH_LONG).show();
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
            fallbackUrl = publicUrl; fallbackRoute = ROUTE_PUBLIC;
        } else if (activeRoute == ROUTE_PUBLIC && !isBlank(localUrl)) {
            fallbackUrl = localUrl; fallbackRoute = ROUTE_LOCAL;
        }
        if (fallbackUrl == null) return false;
        fallbackAttempted = true;
        activeRoute = fallbackRoute;
        activeBaseUrl = fallbackUrl;
        if (webView != null) {
            Toast.makeText(this, fallbackRoute == ROUTE_PUBLIC ?
                            "本地线路不可用，正在切换到公网线路" : "公网线路不可用，正在切换到本地线路",
                    Toast.LENGTH_SHORT).show();
            webView.loadUrl(fallbackUrl);
            return true;
        }
        return false;
    }

    private void setupHiddenGestureMenu() {
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
                    if (twoFingerTapCandidate && (event.getPointerCount() < 2 || movedTooMuch(event)))
                        twoFingerTapCandidate = false;
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    if (twoFingerTapCandidate && event.getPointerCount() == 2 &&
                            now - twoFingerTapStartedAt <= 300L)
                        registerTwoFingerTap(now, averageX(event), averageY(event));
                    twoFingerTapCandidate = false;
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    twoFingerTapCandidate = false;
                    break;
            }
            return false;
        });
    }

    private void registerTwoFingerTap(long now, float x, float y) {
        boolean secondTap = firstTwoFingerTapAt > 0 && now - firstTwoFingerTapAt <= 550L &&
                distance(x, y, firstTwoFingerTapX, firstTwoFingerTapY) <= dp(90);
        if (secondTap) {
            firstTwoFingerTapAt = 0;
            if (webView != null) {
                webView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                webView.post(this::openQuickActions);
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
        float total = 0;
        for (int i = 0; i < count; i++) total += event.getX(i);
        return count == 0 ? 0 : total / count;
    }

    private float averageY(MotionEvent event) {
        int count = Math.min(2, event.getPointerCount());
        float total = 0;
        for (int i = 0; i < count; i++) total += event.getY(i);
        return count == 0 ? 0 : total / count;
    }

    private float distance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void openQuickActions() {
        CharSequence[] items = {
                "返回首页", "线路状态与测速", "在浏览器中打开", "更换线路地址",
                "立即锁定", "进入 App 的安全验证", "检查更新",
                "清除登录与缓存", "WebView 信息"
        };
        String title = activeRoute == ROUTE_LOCAL ? "隐藏功能菜单（当前：本地线路）" :
                activeRoute == ROUTE_PUBLIC ? "隐藏功能菜单（当前：公网线路）" : "隐藏功能菜单";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0: if (webView != null) webView.loadUrl(activeBaseUrl); break;
                        case 1: showRouteStatusAndRetest(); break;
                        case 2:
                            if (webView != null) openExternal(Uri.parse(
                                    webView.getUrl() == null ? activeBaseUrl : webView.getUrl()));
                            break;
                        case 3: confirmChangeServer(); break;
                        case 4: lockImmediately(); break;
                        case 5: requestSecuritySettings(); break;
                        case 6: checkForUpdates(true); break;
                        case 7: confirmClearSiteData(); break;
                        case 8:
                            try { startActivity(new Intent(Settings.ACTION_WEBVIEW_SETTINGS)); }
                            catch (Exception ignored) {
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

    private void showRouteStatusAndRetest() {
        recheckRoutes(false, false);
        RouteManager.Selection selection = lastRouteSelection;
        String localStatus = selection == null || selection.local == null ? "等待测速" : selection.local.label();
        String publicStatus = selection == null || selection.publicRoute == null ?
                "等待测速" : selection.publicRoute.label();
        new AlertDialog.Builder(this)
                .setTitle("线路状态")
                .setMessage("当前线路：" + routeName(activeRoute) +
                        "\n本地线路：" + localStatus +
                        "\n公网线路：" + publicStatus +
                        "\n\n已在后台重新测速；网络变化时会自动重新判断并保留当前页面路径。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void lockImmediately() {
        if (!AppSecurity.isEnabled(this)) {
            Toast.makeText(this, "请先启用进入 App 的安全验证", Toast.LENGTH_SHORT).show();
            return;
        }
        forceRelock = false;
        backgroundAt = 0;
        requestAppUnlock();
    }

    private void requestSecuritySettings() {
        if (!AppSecurity.isEnabled(this)) { openSecuritySettings(); return; }
        authFlowInProgress = true;
        Intent intent = new Intent(this, LockActivity.class);
        intent.putExtra(LockActivity.EXTRA_REASON, "请先验证当前安全方式");
        startActivityForResult(intent, REQUEST_VERIFY_SETTINGS);
    }

    private void openSecuritySettings() {
        authFlowInProgress = true;
        startActivityForResult(new Intent(this, SecuritySettingsActivity.class), REQUEST_SECURITY_SETTINGS);
    }

    private boolean handleNavigation(Uri uri) {
        if (uri == null) return true;
        String scheme = uri.getScheme();
        if (scheme == null) return true;

        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            if (WebOriginPolicy.isSameOrigin(activeBaseUrl, uri.toString())) return false;
            openExternal(uri);
            return true;
        }
        if (WebOriginPolicy.isAllowedExternalScheme(scheme)) {
            openExternal(uri);
            return true;
        }
        if ("intent".equalsIgnoreCase(scheme)) {
            openIntentUri(uri.toString());
            return true;
        }
        if ("about".equalsIgnoreCase(scheme) && "about:blank".equalsIgnoreCase(uri.toString())) {
            return false;
        }
        Toast.makeText(this, "已阻止不受支持的链接类型", Toast.LENGTH_SHORT).show();
        return true;
    }

    private void openIntentUri(String uriString) {
        try {
            Intent intent = Intent.parseUri(uriString, Intent.URI_INTENT_SCHEME);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
                return;
            }
            String fallback = intent.getStringExtra("browser_fallback_url");
            if (fallback != null && (fallback.startsWith("https://") || fallback.startsWith("http://"))) {
                openExternal(Uri.parse(fallback));
                return;
            }
        } catch (Exception ignored) {
        }
        Toast.makeText(this, "无法安全地打开该链接", Toast.LENGTH_SHORT).show();
    }

    private void openExternal(Uri uri) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (ActivityNotFoundException error) {
            Toast.makeText(this, "无法打开该链接", Toast.LENGTH_SHORT).show();
        }
    }

    private DownloadListener createDownloadListener() {
        return (url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (url != null && (url.startsWith("blob:") || url.startsWith("data:"))) {
                Toast.makeText(this, "该导出文件由网页即时生成，请使用“在浏览器中打开”后下载",
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                            PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        STORAGE_PERMISSION_REQUEST);
                Toast.makeText(this, "授权后请再次点击下载", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                String guessed = URLUtil.guessFileName(url, contentDisposition, mimeType);
                String fileName = uniqueDownloadName(guessed);
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                request.addRequestHeader("User-Agent", userAgent);
                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null) request.addRequestHeader("Cookie", cookies);
                request.setTitle(fileName);
                request.setDescription("EZ记账正在下载");
                request.setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                        "EZ记账/" + fileName);
                DownloadManager manager =
                        (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                lastDownloadId = manager.enqueue(request);
                lastDownloadFileName = fileName;
                Toast.makeText(this, "已开始下载：" + fileName, Toast.LENGTH_SHORT).show();
            } catch (Exception error) {
                Toast.makeText(this, "下载失败，可尝试用浏览器打开", Toast.LENGTH_LONG).show();
            }
        };
    }

    private String uniqueDownloadName(String original) {
        String name = original == null || original.trim().isEmpty() ? "EZ记账导出" : original.trim();
        name = name.replaceAll("[\\/:*?\"<>|]", "_");
        int dot = name.lastIndexOf('.');
        String suffix = "-" + System.currentTimeMillis();
        if (dot > 0) return name.substring(0, dot) + suffix + name.substring(dot);
        return name + suffix;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            filePathCallback.onReceiveValue(
                    FileChooserSupport.parseResult(resultCode, data, pendingCameraUri));
            filePathCallback = null;
            pendingCameraUri = null;
            return;
        }
        if (requestCode == REQUEST_UNLOCK_APP) {
            authFlowInProgress = false;
            backgroundAt = 0;
            if (resultCode == Activity.RESULT_OK) {
                forceRelock = false;
                lastUnlockAt = System.currentTimeMillis();
                if (!appInitialized) initializeApp();
                else getWindow().getDecorView().post(this::handlePendingShortcutAction);
            } else {
                finishAndRemoveTask();
            }
            return;
        }
        if (requestCode == REQUEST_VERIFY_SETTINGS) {
            if (resultCode == Activity.RESULT_OK) openSecuritySettings();
            else { authFlowInProgress = false; backgroundAt = 0; }
            return;
        }
        if (requestCode == REQUEST_SECURITY_SETTINGS) {
            authFlowInProgress = false;
            backgroundAt = 0;
        }
    }

    private void checkForUpdates(boolean userInitiated) {
        if (userInitiated) Toast.makeText(this, "正在检查更新…", Toast.LENGTH_SHORT).show();
        UpdateChecker.checkAsync(BuildConfig.VERSION_NAME, result -> {
            preferences.edit().putLong(KEY_LAST_UPDATE_CHECK, System.currentTimeMillis()).apply();
            if (!result.success) {
                if (userInitiated) {
                    new AlertDialog.Builder(this)
                            .setTitle("检查更新失败")
                            .setMessage(result.error == null ? "网络请求失败" : result.error)
                            .setPositiveButton("知道了", null)
                            .show();
                }
                return;
            }
            if (!result.updateAvailable) {
                if (userInitiated) {
                    new AlertDialog.Builder(this)
                            .setTitle("已是最新版本")
                            .setMessage("当前版本：" + BuildConfig.VERSION_NAME +
                                    "\n最新版本：" + result.latestVersion)
                            .setPositiveButton("知道了", null)
                            .show();
                }
                return;
            }
            String notes = result.notes == null ? "暂无更新说明" : result.notes.trim();
            if (notes.length() > 1200) notes = notes.substring(0, 1200) + "…";
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(result.releaseName == null || result.releaseName.isEmpty() ?
                            "发现新版本 " + result.latestVersion : result.releaseName)
                    .setMessage("当前版本：" + BuildConfig.VERSION_NAME +
                            "\n最新版本：" + result.latestVersion + "\n\n" + notes)
                    .setNegativeButton("稍后", null);
            String downloadUrl = result.apkUrl == null || result.apkUrl.isEmpty() ?
                    result.releaseUrl : result.apkUrl;
            if (downloadUrl != null && !downloadUrl.isEmpty()) {
                builder.setPositiveButton("下载更新",
                        (dialog, which) -> openExternal(Uri.parse(downloadUrl)));
            }
            builder.show();
        });
    }

    private void scheduleAutomaticUpdateCheck() {
        long last = preferences.getLong(KEY_LAST_UPDATE_CHECK, 0L);
        if (System.currentTimeMillis() - last >= AUTO_UPDATE_INTERVAL_MS) {
            checkForUpdates(false);
        }
    }

    private void onDefaultNetworkChanged() {
        if (!appInitialized || authFlowInProgress || webView == null || !hasSavedRoutes()) return;
        recheckRoutes(false, true);
    }

    private void registerDownloadReceiver() {
        if (downloadReceiverRegistered) return;
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadCompleteReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(downloadCompleteReceiver, filter);
        }
        downloadReceiverRegistered = true;
    }

    private void unregisterDownloadReceiver() {
        if (!downloadReceiverRegistered) return;
        try { unregisterReceiver(downloadCompleteReceiver); } catch (Exception ignored) {}
        downloadReceiverRegistered = false;
    }

    private void showDownloadedFileDialog(long downloadId, String fileName) {
        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        Uri uri = manager.getUriForDownloadedFile(downloadId);
        if (uri == null || isFinishing()) return;
        String mime = manager.getMimeTypeForDownloadedFile(downloadId);
        new AlertDialog.Builder(this)
                .setTitle("下载完成")
                .setMessage(fileName == null || fileName.isEmpty() ? "文件已保存到下载目录" :
                        fileName + "\n已保存到下载目录/EZ记账")
                .setNegativeButton("关闭", null)
                .setPositiveButton("打开文件", (dialog, which) -> {
                    try {
                        Intent open = new Intent(Intent.ACTION_VIEW);
                        open.setDataAndType(uri, mime == null ? "*/*" : mime);
                        open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(open);
                    } catch (Exception error) {
                        Toast.makeText(this, "没有可打开该文件的应用", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
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
                .setMessage("这会退出当前账号并清除网页缓存，但不会删除线路和安全验证配置。")
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
                }).show();
    }

    private void registerScreenOffReceiver() {
        if (screenReceiverRegistered) return;
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenOffReceiver, filter);
        }
        screenReceiverRegistered = true;
    }

    private void unregisterScreenOffReceiver() {
        if (!screenReceiverRegistered) return;
        try {
            unregisterReceiver(screenOffReceiver);
        } catch (Exception ignored) {
        }
        screenReceiverRegistered = false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (networkMonitor != null) networkMonitor.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!appInitialized || authFlowInProgress || !AppSecurity.isEnabled(this)) return;
        if (LockPolicy.shouldRelock(backgroundAt, System.currentTimeMillis(),
                AppSecurity.getRelockTimeoutMs(this), forceRelock)) {
            backgroundAt = 0;
            forceRelock = false;
            requestAppUnlock();
        }
    }

    @Override
    protected void onStop() {
        if (networkMonitor != null) networkMonitor.stop();
        super.onStop();
        if (!isChangingConfigurations() && !authFlowInProgress && appInitialized)
            backgroundAt = System.currentTimeMillis();
    }

    @Override
    public void onBackPressed() {
        if (showingWebView && webView != null && webView.canGoBack()) { webView.goBack(); return; }
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
        unregisterScreenOffReceiver();
        unregisterDownloadReceiver();
        if (networkMonitor != null) networkMonitor.stop();
        if (routeManager != null) routeManager.shutdown();
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

    private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static class RouteSelection {
        final String url;
        final int routeType;
        RouteSelection(String url, int routeType) { this.url = url; this.routeType = routeType; }
    }
}
