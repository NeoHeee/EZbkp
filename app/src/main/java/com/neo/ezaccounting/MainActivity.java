package com.neo.ezaccounting;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.FragmentActivity;

import java.net.URI;
import java.util.List;

public class MainActivity extends FragmentActivity implements
        RouteCoordinator.Host,
        WebViewController.Host,
        ErrorRecoveryPage.Listener,
        DownloadController.PermissionRequester {

    private static final String PREFS = "ez_accounting_prefs";
    private static final String KEY_LOCAL_URL = "local_url";
    private static final String KEY_LOCAL_ROUTE_RULES = "local_route_rules";
    private static final String KEY_PUBLIC_URL = "public_url";
    private static final String KEY_LAST_UPDATE_CHECK = "last_update_check";
    private static final String KEY_SHOW_QUICK_ACTIONS = "show_quick_actions";
    private static final String STATE_KEY = "app_state";
    private static final String WEB_URL_KEY = "web_url";
    private static final String BASE_URL_KEY = "base_url";
    private static final String WEB_VIEW_STATE_KEY = "web_view_state";
    private static final long AUTO_UPDATE_INTERVAL_MS = 24L * 60L * 60L * 1000L;
    private static final long BACKGROUND_STARTUP_PROBE_FALLBACK_MS = 2500L;
    private static final long PROGRESSIVE_PAGE_RETRY_DELAY_MS = 900L;
    private static final long QUICK_ACTIONS_PREWARM_DELAY_MS = 400L;

    private SharedPreferences preferences;
    private String localUrl;
    private String publicUrl;
    private List<LocalRouteRule> localRouteRules;
    private String pendingShortcutAction;
    private String lastWebUrl;
    private String restoredBaseUrl;
    private long lastBackPressedAt;
    private boolean screenReceiverRegistered;
    private boolean serverSettingsVisible;
    private boolean backgroundStartupProbePending;
    private int consecutivePageFailures;
    private FrameLayout appRoot;
    private FrameLayout webLayer;
    private FrameLayout overlayLayer;
    private TextView recoveryBanner;
    private TextView quickActionsButton;
    private Bundle pendingWebViewState;
    private boolean quickActionsEnabled = true;
    private String cachedServerVersion;
    private boolean serverVersionDetectionAttempted;
    private boolean serverVersionRequestPending;
    private Runnable pendingWifiPermissionRefresh;

    private final Runnable backgroundStartupProbe = this::runPendingBackgroundStartupProbe;
    private final Runnable progressivePageRetry = this::runProgressivePageRetry;
    private final Runnable quickActionsPrewarm = () -> {
        if (isFinishing() || isDestroyed() || stateMachine == null ||
                stateMachine.getState() == AppStateMachine.State.LOCKED ||
                stateMachine.getState() == AppStateMachine.State.SETTINGS) return;
        QuickActionsSheet.prewarm(this, quickActionsModel());
    };

    private ValueCallback<Uri[]> filePathCallback;
    private Uri pendingCameraUri;
    private WebViewController.Failure lastFailure;

    private AppStateMachine stateMachine;
    private AppStateMachine.State stateBeforeLock = AppStateMachine.State.INITIALIZING;
    private AppStateMachine.State stateBeforeSettings = AppStateMachine.State.INITIALIZING;
    private AppLifecycleCoordinator lifecycleCoordinator;
    private RouteCoordinator routeCoordinator;
    private NetworkMonitor networkMonitor;
    private DownloadController downloadController;
    private WebViewController webViewController;

    private final ActivityResultLauncher<Intent> appUnlockLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> handleAppUnlockResult(result.getResultCode()));

    private final ActivityResultLauncher<Intent> settingsVerificationLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    openSecuritySettings();
                } else if (lifecycleCoordinator != null) {
                    lifecycleCoordinator.finishExternalFlow();
                    restoreAfterExternalFlow();
                }
            });

    private final ActivityResultLauncher<Intent> securitySettingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (lifecycleCoordinator != null) lifecycleCoordinator.finishExternalFlow();
                restoreAfterExternalFlow();
            });

    private final ActivityResultLauncher<Intent> fileChooserLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (filePathCallback == null) return;
                filePathCallback.onReceiveValue(FileChooserSupport.parseResult(
                        result.getResultCode(), result.getData(), pendingCameraUri));
                filePathCallback = null;
                pendingCameraUri = null;
            });

    private final ActivityResultLauncher<String> legacyStoragePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (downloadController != null) {
                    downloadController.onLegacyPermissionResult(Boolean.TRUE.equals(granted));
                }
            });

    private final ActivityResultLauncher<String[]> wifiSsidPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean granted = Boolean.TRUE.equals(
                        result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                refreshLocalRouteForNetwork();
                Runnable refresh = pendingWifiPermissionRefresh;
                pendingWifiPermissionRefresh = null;
                if (refresh != null) refresh.run();
                if (granted) {
                    Toast.makeText(this,
                            "Wi-Fi 识别权限已更新，可继续配置局域网地址",
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this,
                            "未获得定位权限，将在无法识别 Wi-Fi 时使用公网地址",
                            Toast.LENGTH_LONG).show();
                }
            });

    private final BroadcastReceiver screenOffReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!Intent.ACTION_SCREEN_OFF.equals(intent.getAction()) ||
                    lifecycleCoordinator == null) return;
            lifecycleCoordinator.onScreenOff(System.currentTimeMillis(),
                    AppSecurity.isEnabled(MainActivity.this),
                    AppSecurity.isLockOnScreenOff(MainActivity.this));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiTheme.applySystemBars(this);
        createPersistentRoot();

        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        String legacyLocalUrl = preferences.getString(KEY_LOCAL_URL, "");
        localRouteRules = LocalRouteRules.decode(
                preferences.getString(KEY_LOCAL_ROUTE_RULES, ""), legacyLocalUrl);
        localUrl = LocalRouteRules.select(localRouteRules,
                WifiRouteContext.currentSsid(this), WifiRouteContext.isWifiConnected(this));
        publicUrl = preferences.getString(KEY_PUBLIC_URL, "");
        quickActionsEnabled = preferences.getBoolean(KEY_SHOW_QUICK_ACTIONS, true);
        updateQuickActionsVisibility();
        pendingShortcutAction = ShortcutActions.read(getIntent());
        lastWebUrl = savedInstanceState == null ? null :
                savedInstanceState.getString(WEB_URL_KEY);
        restoredBaseUrl = savedInstanceState == null ? null :
                savedInstanceState.getString(BASE_URL_KEY);
        pendingWebViewState = savedInstanceState == null ? null :
                savedInstanceState.getBundle(WEB_VIEW_STATE_KEY);

        AppStateMachine.State restored = AppStateMachine.restore(
                savedInstanceState == null ? null : savedInstanceState.getString(STATE_KEY),
                AppStateMachine.State.INITIALIZING);
        if (restored == AppStateMachine.State.LOCKED ||
                restored == AppStateMachine.State.SETTINGS) {
            restored = AppStateMachine.State.INITIALIZING;
        }
        stateMachine = new AppStateMachine(restored);
        lifecycleCoordinator = new AppLifecycleCoordinator();
        downloadController = new DownloadController(this, this);
        webViewController = new WebViewController(this, this, downloadController);
        routeCoordinator = new RouteCoordinator(preferences, this);
        routeCoordinator.setAddresses(localUrl, publicUrl);
        networkMonitor = new NetworkMonitor(this, this::onDefaultNetworkChanged);
        registerScreenOffReceiver();

        if (ShortcutActions.LOCK.equals(pendingShortcutAction) && AppSecurity.isEnabled(this)) {
            pendingShortcutAction = null;
        }
        handleLifecycleAction(lifecycleCoordinator.onCreate(AppSecurity.isEnabled(this)));
    }

    private void createPersistentRoot() {
        appRoot = new FrameLayout(this);
        appRoot.setBackgroundColor(UiTheme.background(this));

        webLayer = new FrameLayout(this);
        webLayer.setBackgroundColor(UiTheme.webBackground(this));
        appRoot.addView(webLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        overlayLayer = new FrameLayout(this);
        overlayLayer.setVisibility(View.GONE);
        appRoot.addView(overlayLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        quickActionsButton = new TextView(this);
        quickActionsButton.setText("⋮");
        quickActionsButton.setTextSize(28);
        quickActionsButton.setTextColor(Color.WHITE);
        quickActionsButton.setGravity(Gravity.CENTER);
        quickActionsButton.setMinWidth(dp(48));
        quickActionsButton.setMinHeight(dp(64));
        quickActionsButton.setPadding(dp(8), dp(8), dp(8), dp(8));
        quickActionsButton.setContentDescription("打开快捷中心");
        quickActionsButton.setTooltipText("快捷中心");
        quickActionsButton.setElevation(dp(8));
        GradientDrawable quickBackground = new GradientDrawable();
        int accent = UiTheme.accent(this);
        quickBackground.setColor(Color.argb(224, Color.red(accent),
                Color.green(accent), Color.blue(accent)));
        quickBackground.setCornerRadii(new float[]{dp(22), dp(22), 0, 0,
                0, 0, dp(22), dp(22)});
        quickActionsButton.setBackground(quickBackground);
        quickActionsButton.setOnClickListener(view -> openQuickActions());
        FrameLayout.LayoutParams quickParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        quickParams.setMargins(dp(16), dp(16), 0, dp(16));
        appRoot.addView(quickActionsButton, quickParams);

        recoveryBanner = new TextView(this);
        recoveryBanner.setTextSize(14);
        recoveryBanner.setTextColor(Color.WHITE);
        recoveryBanner.setGravity(Gravity.CENTER_VERTICAL);
        recoveryBanner.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        recoveryBanner.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bannerBackground = new GradientDrawable();
        bannerBackground.setColor(UiTheme.isDark(this) ?
                Color.rgb(145, 96, 18) : Color.rgb(145, 91, 10));
        bannerBackground.setCornerRadius(dp(14));
        recoveryBanner.setBackground(bannerBackground);
        recoveryBanner.setElevation(dp(8));
        recoveryBanner.setVisibility(View.GONE);
        FrameLayout.LayoutParams bannerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        bannerParams.setMargins(dp(16), dp(16), dp(16), dp(24));
        appRoot.addView(recoveryBanner, bannerParams);

        setContentView(appRoot);
    }

    private void showOverlay(View content) {
        overlayLayer.removeAllViews();
        overlayLayer.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlayLayer.setAlpha(1f);
        overlayLayer.setVisibility(View.VISIBLE);
        overlayLayer.bringToFront();
        recoveryBanner.bringToFront();
        quickActionsButton.setVisibility(View.GONE);
        hideRecoveryBanner();
    }

    private void hideOverlay() {
        overlayLayer.removeAllViews();
        overlayLayer.setVisibility(View.GONE);
        updateQuickActionsVisibility();
    }

    private void showRecoveryBanner(String text) {
        if (recoveryBanner == null) return;
        recoveryBanner.animate().cancel();
        recoveryBanner.setText(text);
        recoveryBanner.setAlpha(0f);
        recoveryBanner.setTranslationY(dp(12));
        recoveryBanner.setVisibility(View.VISIBLE);
        recoveryBanner.bringToFront();
        quickActionsButton.setVisibility(View.GONE);
        recoveryBanner.animate().alpha(1f).translationY(0f).setDuration(180L).start();
        recoveryBanner.announceForAccessibility(text);
    }

    private void hideRecoveryBanner() {
        if (recoveryBanner == null || recoveryBanner.getVisibility() != View.VISIBLE) return;
        recoveryBanner.animate().cancel();
        recoveryBanner.setVisibility(View.GONE);
        recoveryBanner.setAlpha(1f);
        recoveryBanner.setTranslationY(0f);
        updateQuickActionsVisibility();
    }

    private void updateQuickActionsVisibility() {
        if (quickActionsButton == null || overlayLayer == null || recoveryBanner == null) return;
        boolean visible = quickActionsEnabled &&
                overlayLayer.getVisibility() != View.VISIBLE &&
                recoveryBanner.getVisibility() != View.VISIBLE;
        quickActionsButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) quickActionsButton.bringToFront();
    }

    private void runProgressivePageRetry() {
        if (consecutivePageFailures != 1 || serverSettingsVisible || isFinishing() ||
                isDestroyed() || !webViewController.isCreated()) return;
        showRecoveryBanner("连接仍不稳定，正在重试当前页面…");
        webViewController.reload();
    }

    private void scheduleProgressivePageRetry() {
        getWindow().getDecorView().removeCallbacks(progressivePageRetry);
        getWindow().getDecorView().postDelayed(progressivePageRetry,
                PROGRESSIVE_PAGE_RETRY_DELAY_MS);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String action = ShortcutActions.read(intent);
        if (action == null) return;
        pendingShortcutAction = action;
        if (lifecycleCoordinator != null && lifecycleCoordinator.isInitialized() &&
                !lifecycleCoordinator.isAuthInProgress()) {
            getWindow().getDecorView().post(this::handlePendingShortcutAction);
        }
    }

    private void handleLifecycleAction(AppLifecycleCoordinator.Action action) {
        if (action == null) return;
        switch (action) {
            case INITIALIZE:
                initializeApp();
                break;
            case REQUEST_UNLOCK:
                requestAppUnlock();
                break;
            case FINISH_APP:
                finishAndRemoveTask();
                break;
            case NONE:
            default:
                break;
        }
    }

    private void initializeApp() {
        if (!lifecycleCoordinator.isInitialized()) lifecycleCoordinator.markInitialized();
        if (stateMachine.getState() == AppStateMachine.State.LOCKED) {
            transitionTo(AppStateMachine.State.INITIALIZING);
        }

        if (ShortcutActions.ROUTES.equals(pendingShortcutAction)) {
            pendingShortcutAction = null;
            showServerSettings();
        } else if (routeCoordinator.hasConfiguredRoute()) {
            if (routeCoordinator.activateFastStartRoute()) {
                scheduleBackgroundStartupProbe();
            } else {
                routeCoordinator.requestCheck(RouteCoordinator.Trigger.STARTUP);
            }
        } else {
            showServerSettings();
        }

        getWindow().getDecorView().post(() -> {
            handlePendingShortcutAction();
            scheduleAutomaticUpdateCheck();
        });
    }

    private void scheduleBackgroundStartupProbe() {
        backgroundStartupProbePending = true;
        getWindow().getDecorView().removeCallbacks(backgroundStartupProbe);
        getWindow().getDecorView().postDelayed(backgroundStartupProbe,
                BACKGROUND_STARTUP_PROBE_FALLBACK_MS);
    }

    private void runPendingBackgroundStartupProbe() {
        if (!backgroundStartupProbePending || isFinishing() || isDestroyed() ||
                serverSettingsVisible) return;
        backgroundStartupProbePending = false;
        routeCoordinator.requestCheck(RouteCoordinator.Trigger.BACKGROUND_STARTUP);
    }

    private void handlePendingShortcutAction() {
        if (pendingShortcutAction == null || lifecycleCoordinator.isAuthInProgress()) return;
        String action = pendingShortcutAction;
        pendingShortcutAction = null;
        if (ShortcutActions.ROUTES.equals(action)) {
            showServerSettings();
        } else if (ShortcutActions.SECURITY.equals(action)) {
            if (!AppSecurity.isEnabled(this) ||
                    System.currentTimeMillis() - lifecycleCoordinator.getLastUnlockAt() < 10_000L) {
                openSecuritySettings();
            } else {
                requestSecuritySettings();
            }
        } else if (ShortcutActions.LOCK.equals(action)) {
            lockImmediately();
        } else if (ShortcutActions.TOGGLE_QUICK_ACTIONS.equals(action)) {
            toggleQuickActions();
        }
    }

    private void toggleQuickActions() {
        quickActionsEnabled = !quickActionsEnabled;
        preferences.edit().putBoolean(KEY_SHOW_QUICK_ACTIONS, quickActionsEnabled).apply();
        updateQuickActionsVisibility();
        Toast.makeText(this, quickActionsEnabled ?
                "快捷入口已显示" : "快捷入口已隐藏", Toast.LENGTH_SHORT).show();
    }

    private void requestAppUnlock() {
        if (lifecycleCoordinator.isAuthInProgress() &&
                stateMachine.getState() == AppStateMachine.State.LOCKED) return;
        stateBeforeLock = stateMachine.getState();
        lifecycleCoordinator.beginAuth();
        transitionTo(AppStateMachine.State.LOCKED);
        Intent intent = new Intent(this, LockActivity.class);
        intent.putExtra(LockActivity.EXTRA_REASON, "请验证身份后进入应用");
        appUnlockLauncher.launch(intent);
    }

    private void handleAppUnlockResult(int resultCode) {
        AppLifecycleCoordinator.Action action = lifecycleCoordinator.onUnlockResult(
                resultCode == Activity.RESULT_OK, System.currentTimeMillis());
        if (action == AppLifecycleCoordinator.Action.FINISH_APP) {
            finishAndRemoveTask();
            return;
        }
        if (action == AppLifecycleCoordinator.Action.INITIALIZE) {
            initializeApp();
            return;
        }
        AppStateMachine.State target = stateBeforeLock;
        if (target == AppStateMachine.State.LOCKED) {
            target = webViewController.isCreated() ?
                    (webViewController.isPageReady() ? AppStateMachine.State.READY :
                            AppStateMachine.State.LOADING_WEB) :
                    AppStateMachine.State.INITIALIZING;
        }
        transitionTo(target);
        getWindow().getDecorView().post(this::handlePendingShortcutAction);
    }

    private void showServerSettings() {
        rememberCurrentWebUrl();
        serverSettingsVisible = true;
        stateBeforeSettings = stateMachine.getState();
        transitionTo(AppStateMachine.State.SETTINGS);
        String activeLocalUrl = routeCoordinator.getActiveType() == RouteManager.TYPE_LOCAL ?
                routeCoordinator.getActiveUrl() : null;
        showOverlay(ServerSettingsPage.create(this, localRouteRules, publicUrl, activeLocalUrl,
                new ServerSettingsPage.Listener() {
            @Override
            public void onSaved(List<LocalRouteRule> savedRules, String savedPublic) {
                localRouteRules = savedRules;
                publicUrl = savedPublic;
                refreshLocalRouteForNetwork();
                preferences.edit()
                        .putString(KEY_LOCAL_URL, localUrl)
                        .putString(KEY_LOCAL_ROUTE_RULES, LocalRouteRules.encode(localRouteRules))
                        .putString(KEY_PUBLIC_URL, publicUrl)
                        .apply();
                routeCoordinator.setAddresses(localUrl, publicUrl);
                serverSettingsVisible = false;
                lastFailure = null;
                hideOverlay();
                transitionTo(AppStateMachine.State.CHECKING_ROUTE);
                routeCoordinator.requestCheck(RouteCoordinator.Trigger.RETRY);
            }

            @Override
            public void onWifiPermissionRequested(Runnable refreshAfterPermission) {
                pendingWifiPermissionRefresh = refreshAfterPermission;
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                    wifiSsidPermissionLauncher.launch(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION});
                } else {
                    wifiSsidPermissionLauncher.launch(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION});
                }
            }

            @Override
            public void onTestAddress(String url,
                                      ServerSettingsPage.AddressTestCallback callback) {
                routeCoordinator.testLocalAddress(url, result -> {
                    if (result.reachable) {
                        callback.onResult(true, result.latencyMs > 0 ?
                                result.latencyMs + " ms" : "连接正常");
                    } else {
                        callback.onResult(false, result.diagnostic());
                    }
                });
            }

            @Override
            public void onClose() {
                if (routeCoordinator.hasConfiguredRoute()) {
                    showSettingsCenter();
                } else {
                    Toast.makeText(MainActivity.this, "请先保存至少一个服务器地址",
                            Toast.LENGTH_SHORT).show();
                }
            }
        }));
    }

    private void showSettingsCenter() {
        showSettingsCenter(true);
    }

    private void showSettingsCenter(boolean refreshServerVersion) {
        rememberCurrentWebUrl();
        serverSettingsVisible = true;
        stateBeforeSettings = stateMachine.getState();
        transitionTo(AppStateMachine.State.SETTINGS);
        String routeSummary = "自动管理 · " +
                routeName(routeCoordinator.getActiveType());
        SettingsCenterPage.Model model = new SettingsCenterPage.Model(
                routeSummary,
                quickActionsEnabled,
                AppSecurity.isEnabled(this) ? AppSecurity.getModeLabel(this) : "未开启保护",
                "v" + BuildConfig.VERSION_NAME,
                cachedServerVersion == null ?
                        (serverVersionDetectionAttempted ? "未检测到" : "检测中…") :
                        "v" + cachedServerVersion);
        showOverlay(SettingsCenterPage.create(this, model, new SettingsCenterPage.Listener() {
            @Override
            public void onClose() {
                handleNativeOverlayBack();
            }

            @Override
            public void onServerAddresses() {
                showServerSettings();
            }

            @Override
            public void onRouteStatus() {
                showRouteStatusDialog();
            }

            @Override
            public void onQuickActionsChanged(boolean enabled) {
                quickActionsEnabled = enabled;
                preferences.edit().putBoolean(KEY_SHOW_QUICK_ACTIONS, enabled).apply();
                updateQuickActionsVisibility();
            }

            @Override
            public void onSecuritySettings() {
                requestSecuritySettings();
            }

            @Override
            public void onCheckUpdate() {
                checkForUpdates(true);
            }

            @Override
            public void onWebViewInfo() {
                showWebViewInfo();
            }

            @Override
            public void onClearSiteData() {
                confirmClearSiteData();
            }
        }));
        if (refreshServerVersion) refreshServerVersion();
    }

    private void refreshServerVersion() {
        if (serverVersionRequestPending || !webViewController.isPageReady()) return;
        serverVersionRequestPending = true;
        webViewController.requestServerVersion(version -> {
            serverVersionRequestPending = false;
            serverVersionDetectionAttempted = true;
            cachedServerVersion = version;
            if (overlayLayer.getChildCount() == 1 &&
                    SettingsCenterPage.isRoot(overlayLayer.getChildAt(0))) {
                showSettingsCenter(false);
            }
        });
    }

    private void showLoadingScreen(String text) {
        UiTheme.applySystemBars(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(UiTheme.background(this));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(24), dp(24), dp(24), dp(24));
        box.addView(new ProgressBar(this));
        TextView message = new TextView(this);
        message.setText(text);
        message.setTextSize(15);
        message.setTextColor(UiTheme.secondaryText(this));
        message.setPadding(0, dp(14), 0, 0);
        box.addView(message);
        root.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        showOverlay(root);
    }

    @Override
    public void onRouteCheckStarted(RouteCoordinator.Trigger trigger) {
        if (trigger == RouteCoordinator.Trigger.BACKGROUND_STARTUP) return;
        if (trigger == RouteCoordinator.Trigger.MANUAL_SPEED_TEST) {
            Toast.makeText(this, "正在手动测速…", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!webViewController.isCreated() ||
                stateMachine.getState() == AppStateMachine.State.ERROR ||
                trigger == RouteCoordinator.Trigger.STARTUP) {
            transitionTo(AppStateMachine.State.CHECKING_ROUTE);
            showLoadingScreen("正在检测本地与公网线路…");
        }
    }

    @Override
    public void onRouteSnapshot(RouteCoordinator.Snapshot snapshot,
                                RouteCoordinator.Trigger trigger) {
        // Snapshot is retained by RouteCoordinator and rendered on demand.
    }

    @Override
    public void onRouteActivated(RouteManager.ProbeResult target,
                                 RouteCoordinator.Snapshot snapshot, boolean changed,
                                 String reason, RouteCoordinator.Trigger trigger) {
        serverSettingsVisible = false;
        hideRecoveryBanner();
        String oldBase = webViewController.getBaseUrl();
        if (oldBase == null) oldBase = restoredBaseUrl;
        String current = webViewController.currentUrl();
        if (current == null) current = lastWebUrl;
        String targetUrl = remapUrl(oldBase, current, target.url);
        if (changed) {
            cachedServerVersion = null;
            serverVersionDetectionAttempted = false;
        }
        restoredBaseUrl = target.url;

        if (!webViewController.isCreated()) {
            transitionTo(AppStateMachine.State.LOADING_WEB);
            webLayer.removeAllViews();
            Bundle restoreState = restoredBaseUrl != null && restoredBaseUrl.equals(target.url) ?
                    pendingWebViewState : null;
            pendingWebViewState = null;
            webLayer.addView(webViewController.create(target.url, targetUrl, restoreState),
                    new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
            hideOverlay();
        } else if (changed) {
            hideOverlay();
            webViewController.setBaseUrl(target.url);
            transitionTo(AppStateMachine.State.LOADING_WEB);
            webViewController.loadUrl(targetUrl);
        } else if (trigger == RouteCoordinator.Trigger.RETRY ||
                trigger == RouteCoordinator.Trigger.PAGE_FAILURE) {
            transitionTo(AppStateMachine.State.LOADING_WEB);
            hideOverlay();
            webViewController.reload();
        }

        if (changed && trigger != RouteCoordinator.Trigger.STARTUP &&
                trigger != RouteCoordinator.Trigger.FAST_START) {
            Toast.makeText(this, "已切换到" + routeName(target.type) +
                    "（" + target.latencyMs + " ms）", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRouteStable(RouteCoordinator.Snapshot snapshot, String reason,
                              RouteCoordinator.Trigger trigger) {
        if (trigger == RouteCoordinator.Trigger.MANUAL_SPEED_TEST) {
            showSpeedTestDialog(snapshot);
            return;
        }
        if (stateMachine.getState() == AppStateMachine.State.ERROR &&
                snapshot != null && snapshot.activeReachable() &&
                routeCoordinator.getActiveUrl() != null) {
            RouteManager.ProbeResult active = snapshot.selection.resultFor(
                    routeCoordinator.getActiveType());
            if (active != null) {
                onRouteActivated(active, snapshot, false, reason,
                        RouteCoordinator.Trigger.RETRY);
            }
            return;
        }
        if (stateMachine.getState() == AppStateMachine.State.CHECKING_ROUTE &&
                webViewController.isCreated()) {
            transitionTo(webViewController.isPageReady() ?
                    AppStateMachine.State.READY : AppStateMachine.State.LOADING_WEB);
        }
    }

    @Override
    public void onRouteUnavailable(RouteCoordinator.Snapshot snapshot, String reason,
                                   RouteCoordinator.Trigger trigger) {
        if (serverSettingsVisible) return;
        if (trigger == RouteCoordinator.Trigger.BACKGROUND_STARTUP &&
                webViewController.isCreated() &&
                stateMachine.getState() != AppStateMachine.State.ERROR) {
            return;
        }
        if (ProgressiveRecoveryPolicy.shouldDeferFullError(trigger,
                webViewController.isCreated(), consecutivePageFailures)) {
            scheduleProgressivePageRetry();
            return;
        }
        showErrorPage(reason, lastFailure, snapshot);
    }

    @Override
    public boolean onNavigationRequested(Uri uri) {
        if (uri == null) return true;
        String scheme = uri.getScheme();
        if (scheme == null) return true;
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            if (WebOriginPolicy.isSameOrigin(routeCoordinator.getActiveUrl(), uri.toString())) {
                return false;
            }
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
        if ("about".equalsIgnoreCase(scheme) &&
                "about:blank".equalsIgnoreCase(uri.toString())) return false;
        Toast.makeText(this, "已阻止不受支持的链接类型", Toast.LENGTH_SHORT).show();
        return true;
    }

    @Override
    public void onFileChooserRequested(ValueCallback<Uri[]> callback,
                                       WebChromeClient.FileChooserParams params) {
        if (filePathCallback != null) filePathCallback.onReceiveValue(null);
        filePathCallback = callback;
        try {
            FileChooserSupport.Request request = FileChooserSupport.create(this, params);
            pendingCameraUri = request.cameraUri;
            fileChooserLauncher.launch(request.chooserIntent);
        } catch (Exception error) {
            filePathCallback = null;
            pendingCameraUri = null;
            Toast.makeText(this, "无法打开照片或文件选择器", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onPageStarted(String url) {
        if (stateMachine.getState() != AppStateMachine.State.LOCKED &&
                stateMachine.getState() != AppStateMachine.State.SETTINGS) {
            transitionTo(AppStateMachine.State.LOADING_WEB);
        }
    }

    @Override
    public void onPageReady(String url) {
        lastWebUrl = url;
        lastFailure = null;
        consecutivePageFailures = 0;
        getWindow().getDecorView().removeCallbacks(progressivePageRetry);
        hideRecoveryBanner();
        routeCoordinator.markPageSuccess();
        if (backgroundStartupProbePending) {
            getWindow().getDecorView().removeCallbacks(backgroundStartupProbe);
            runPendingBackgroundStartupProbe();
        }
        if (stateMachine.getState() != AppStateMachine.State.LOCKED &&
                stateMachine.getState() != AppStateMachine.State.SETTINGS) {
            transitionTo(AppStateMachine.State.READY);
        }
        View decor = getWindow().getDecorView();
        decor.removeCallbacks(quickActionsPrewarm);
        decor.postDelayed(quickActionsPrewarm, QUICK_ACTIONS_PREWARM_DELAY_MS);
    }

    @Override
    public void onPageFailure(WebViewController.Failure failure) {
        backgroundStartupProbePending = false;
        getWindow().getDecorView().removeCallbacks(backgroundStartupProbe);
        getWindow().getDecorView().removeCallbacks(quickActionsPrewarm);
        lastFailure = failure;
        consecutivePageFailures++;
        rememberCurrentWebUrl();
        routeCoordinator.markPageFailure();
        showRecoveryBanner(consecutivePageFailures == 1 ?
                "连接出现波动，正在检查当前线路和备用线路…" :
                "连接再次失败，正在准备完整恢复选项…");
    }

    @Override
    public void onOpenQuickActions() {
        openQuickActions();
    }

    private void showErrorPage(String reason, WebViewController.Failure failure,
                               RouteCoordinator.Snapshot snapshot) {
        rememberCurrentWebUrl();
        getWindow().getDecorView().removeCallbacks(progressivePageRetry);
        serverSettingsVisible = false;
        transitionTo(AppStateMachine.State.ERROR);
        String title = failure == null ? "无法连接记账服务" : failure.title;
        String detail = failure == null ? reason :
                failure.detail + (reason == null || reason.isEmpty() ? "" : "\n" + reason);
        String browserUrl = browserTarget();
        ErrorRecoveryPage.Model model = new ErrorRecoveryPage.Model(title,
                "可以重新连接、手动测速或修改服务器地址。线路会根据当前网络自动选择。",
                detail, snapshot, browserUrl != null);
        showOverlay(ErrorRecoveryPage.create(this, model, this));
    }

    @Override
    public void onRetry() {
        routeCoordinator.requestCheck(RouteCoordinator.Trigger.RETRY);
    }

    @Override
    public void onSpeedTest() {
        routeCoordinator.manualSpeedTest();
    }

    @Override
    public void onEditAddresses() {
        showServerSettings();
    }

    @Override
    public void onOpenBrowser() {
        String target = browserTarget();
        if (target != null) openExternal(Uri.parse(target));
    }

    @Override
    public void requestLegacyStoragePermission() {
        legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    private void openQuickActions() {
        QuickActionsSheet.show(this, quickActionsModel(), new QuickActionsSheet.Listener() {
            @Override
            public void onHome() {
                if (routeCoordinator.getActiveUrl() != null) {
                    webViewController.loadUrl(routeCoordinator.getActiveUrl());
                }
            }

            @Override
            public void onSettings() {
                showSettingsCenter();
            }

            @Override
            public void onLock() {
                lockImmediately();
            }

        });
    }

    private QuickActionsSheet.Model quickActionsModel() {
        String latency = "待测速";
        RouteCoordinator.Snapshot snapshot = routeCoordinator.getLastSnapshot();
        if (snapshot != null && snapshot.selection != null) {
            RouteManager.ProbeResult active = snapshot.selection.resultFor(
                    routeCoordinator.getActiveType());
            if (active != null && active.reachable && active.latencyMs > 0) {
                latency = active.latencyMs + " ms";
            }
        }
        String security = AppSecurity.isEnabled(this) ?
                AppSecurity.getModeLabel(this) : "未开启保护";
        return new QuickActionsSheet.Model(
                routeName(routeCoordinator.getActiveType()), latency, security);
    }

    private void showRouteStatusDialog() {
        RouteCoordinator.Snapshot snapshot = routeCoordinator.getLastSnapshot();
        UiComponents.show(new AlertDialog.Builder(this)
                .setTitle("线路状态")
                .setMessage(routeStatusText(snapshot))
                .setNegativeButton("关闭", null)
                .setPositiveButton("手动测速", (dialog, which) -> routeCoordinator.manualSpeedTest())
                .create());
    }

    private void showSpeedTestDialog(RouteCoordinator.Snapshot snapshot) {
        UiComponents.show(new AlertDialog.Builder(this)
                .setTitle("测速完成")
                .setMessage(routeStatusText(snapshot))
                .setNegativeButton("关闭", null)
                .setPositiveButton("再次测速", (dialog, which) -> routeCoordinator.manualSpeedTest())
                .create());
    }

    private String routeStatusText(RouteCoordinator.Snapshot snapshot) {
        StringBuilder text = new StringBuilder();
        text.append("选择方式：自动管理\n");
        text.append("当前：").append(routeName(routeCoordinator.getActiveType())).append('\n');
        if (snapshot == null) {
            text.append("\n尚未完成测速");
            return text.toString();
        }
        text.append("\n本地：").append(probeLabel(snapshot.local()));
        text.append("\n公网：").append(probeLabel(snapshot.publicRoute()));
        text.append("\n\n自动规则：当前 Wi-Fi 命中时优先局域网；本地不可用时回退公网；网络变化后自动重新选择。");
        return text.toString();
    }

    private String probeLabel(RouteManager.ProbeResult result) {
        return result == null ? "未检测" : result.label() + " · " + result.diagnostic();
    }

    private void lockImmediately() {
        if (!AppSecurity.isEnabled(this)) {
            Toast.makeText(this, "请先启用进入 App 的安全验证", Toast.LENGTH_SHORT).show();
            return;
        }
        stateBeforeLock = stateMachine.getState();
        lifecycleCoordinator.requestImmediateRelock();
        transitionTo(AppStateMachine.State.LOCKED);
        Intent intent = new Intent(this, LockActivity.class);
        intent.putExtra(LockActivity.EXTRA_REASON, "请验证身份后进入应用");
        appUnlockLauncher.launch(intent);
    }

    private void requestSecuritySettings() {
        if (!AppSecurity.isEnabled(this)) {
            openSecuritySettings();
            return;
        }
        lifecycleCoordinator.beginAuth();
        stateBeforeSettings = stateMachine.getState();
        Intent intent = new Intent(this, LockActivity.class);
        intent.putExtra(LockActivity.EXTRA_REASON, "请先验证当前安全方式");
        settingsVerificationLauncher.launch(intent);
    }

    private void openSecuritySettings() {
        lifecycleCoordinator.beginAuth();
        stateBeforeSettings = stateMachine.getState();
        transitionTo(AppStateMachine.State.SETTINGS);
        securitySettingsLauncher.launch(new Intent(this, SecuritySettingsActivity.class));
    }

    private void restoreAfterExternalFlow() {
        if (stateBeforeSettings == AppStateMachine.State.SETTINGS &&
                routeCoordinator.hasConfiguredRoute()) {
            showSettingsCenter();
        } else if (webViewController.isCreated()) {
            hideOverlay();
            transitionTo(webViewController.isPageReady() ?
                    AppStateMachine.State.READY : AppStateMachine.State.LOADING_WEB);
        } else if (stateBeforeSettings == AppStateMachine.State.ERROR && lastFailure != null) {
            showErrorPage("返回安全设置前的连接错误", lastFailure,
                    routeCoordinator.getLastSnapshot());
        } else if (routeCoordinator.hasConfiguredRoute()) {
            routeCoordinator.requestCheck(RouteCoordinator.Trigger.RETRY);
        } else {
            showServerSettings();
        }
    }

    private void confirmClearSiteData() {
        UiComponents.show(new AlertDialog.Builder(this)
                .setTitle("清除登录与缓存")
                .setMessage("这会退出当前账号并清除网页缓存，但不会删除线路和安全验证配置。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清除", (dialog, which) -> {
                    webViewController.clearSiteData();
                    if (routeCoordinator.getActiveUrl() != null) {
                        webViewController.loadUrl(routeCoordinator.getActiveUrl());
                    }
                    Toast.makeText(this, "已清除登录与缓存", Toast.LENGTH_SHORT).show();
                })
                .create());
    }

    private void showWebViewInfo() {
        String packageName = webViewController.webViewPackageName();
        if (packageName == null) {
            Toast.makeText(this, "无法读取 WebView 信息", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_WEBVIEW_SETTINGS));
        } catch (Exception ignored) {
            Toast.makeText(this, packageName, Toast.LENGTH_LONG).show();
        }
    }

    private void checkForUpdates(boolean userInitiated) {
        if (userInitiated) Toast.makeText(this, "正在检查更新…", Toast.LENGTH_SHORT).show();
        UpdateChecker.checkAsync(BuildConfig.VERSION_NAME, result -> {
            preferences.edit().putLong(KEY_LAST_UPDATE_CHECK, System.currentTimeMillis()).apply();
            if (!result.success) {
                if (userInitiated) {
                    UiComponents.show(new AlertDialog.Builder(this)
                            .setTitle("检查更新失败")
                            .setMessage(result.error == null ? "网络请求失败" : result.error)
                            .setPositiveButton("知道了", null)
                            .create());
                }
                return;
            }
            if (!result.updateAvailable) {
                if (userInitiated) {
                    UiComponents.show(new AlertDialog.Builder(this)
                            .setTitle("已是最新版本")
                            .setMessage("当前版本：" + BuildConfig.VERSION_NAME +
                                    "\n最新版本：" + result.latestVersion)
                            .setPositiveButton("知道了", null)
                            .create());
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
            UiComponents.show(builder.create());
        });
    }

    private void scheduleAutomaticUpdateCheck() {
        long last = preferences.getLong(KEY_LAST_UPDATE_CHECK, 0L);
        if (System.currentTimeMillis() - last >= AUTO_UPDATE_INTERVAL_MS) {
            checkForUpdates(false);
        }
    }

    private void onDefaultNetworkChanged() {
        refreshLocalRouteForNetwork();
        if (lifecycleCoordinator == null || !lifecycleCoordinator.isInitialized() ||
                lifecycleCoordinator.isAuthInProgress() || !routeCoordinator.hasConfiguredRoute()) {
            return;
        }
        routeCoordinator.requestCheck(RouteCoordinator.Trigger.NETWORK_CHANGE);
    }

    private void refreshLocalRouteForNetwork() {
        localUrl = LocalRouteRules.select(localRouteRules,
                WifiRouteContext.currentSsid(this), WifiRouteContext.isWifiConnected(this));
        if (preferences != null) preferences.edit().putString(KEY_LOCAL_URL, localUrl).apply();
        if (routeCoordinator != null) routeCoordinator.setAddresses(localUrl, publicUrl);
    }

    private void openIntentUri(String uriString) {
        try {
            Intent intent = Intent.parseUri(uriString, Intent.URI_INTENT_SCHEME);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
                return;
            }
            String fallback = intent.getStringExtra("browser_fallback_url");
            if (fallback != null &&
                    (fallback.startsWith("https://") || fallback.startsWith("http://"))) {
                openExternal(Uri.parse(fallback));
                return;
            }
        } catch (Exception ignored) {
        }
        Toast.makeText(this, "无法安全地打开该链接", Toast.LENGTH_SHORT).show();
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "无法打开该链接", Toast.LENGTH_SHORT).show();
        }
    }

    private String browserTarget() {
        if (lastWebUrl != null && !lastWebUrl.trim().isEmpty()) return lastWebUrl;
        return routeCoordinator == null ? null : routeCoordinator.getActiveUrl();
    }

    private String remapUrl(String oldBase, String currentUrl, String newBase) {
        if (newBase == null || newBase.trim().isEmpty()) return newBase;
        if (oldBase == null || currentUrl == null) return newBase;
        try {
            if (!WebOriginPolicy.isSameOrigin(oldBase, currentUrl)) return newBase;
            URI current = new URI(currentUrl);
            URI next = new URI(newBase);
            return new URI(next.getScheme(), next.getAuthority(), current.getPath(),
                    current.getQuery(), current.getFragment()).toString();
        } catch (Exception ignored) {
            return newBase;
        }
    }

    private void rememberCurrentWebUrl() {
        if (webViewController == null) return;
        String current = webViewController.currentUrl();
        if (current != null && !current.trim().isEmpty()) lastWebUrl = current;
    }

    private String routeName(int type) {
        if (type == RouteManager.TYPE_LOCAL) return "本地线路";
        if (type == RouteManager.TYPE_PUBLIC) return "公网线路";
        return "尚未连接";
    }

    private void transitionTo(AppStateMachine.State next) {
        if (stateMachine.getState() != next) stateMachine.transitionTo(next);
    }

    protected final AppStateMachine.State currentAppState() {
        return stateMachine == null ? AppStateMachine.State.INITIALIZING : stateMachine.getState();
    }

    protected final EzBookkeepingPageDetector.PageIdentity cachedPageIdentity() {
        return webViewController == null ? EzBookkeepingPageDetector.PageIdentity.UNKNOWN :
                webViewController.getCachedPageIdentity();
    }

    protected final void resolveCurrentPageIdentity(
            ValueCallback<EzBookkeepingPageDetector.PageIdentity> callback) {
        if (webViewController == null) {
            callback.onReceiveValue(EzBookkeepingPageDetector.PageIdentity.UNKNOWN);
            return;
        }
        webViewController.resolvePageIdentity(callback);
    }

    protected final void refreshPageIdentityAfterNavigation() {
        if (webViewController != null) webViewController.refreshPageIdentityAfterNavigation();
    }

    protected final boolean handleNativeOverlayBack() {
        AppStateMachine.State state = currentAppState();
        if (state == AppStateMachine.State.SETTINGS) {
            if (!routeCoordinator.hasConfiguredRoute()) return false;
            serverSettingsVisible = false;
            if (stateBeforeSettings == AppStateMachine.State.ERROR && lastFailure != null) {
                showErrorPage("返回设置前的连接错误", lastFailure,
                        routeCoordinator.getLastSnapshot());
            } else if (webViewController.isCreated()) {
                hideOverlay();
                transitionTo(webViewController.isPageReady() ?
                        AppStateMachine.State.READY : AppStateMachine.State.LOADING_WEB);
            } else {
                hideOverlay();
                transitionTo(AppStateMachine.State.CHECKING_ROUTE);
                routeCoordinator.requestCheck(RouteCoordinator.Trigger.RETRY);
            }
            return true;
        }
        if (state == AppStateMachine.State.ERROR) {
            hideOverlay();
            showRecoveryBanner("正在重新检测线路并恢复页面…");
            if (webViewController.isCreated()) {
                transitionTo(webViewController.isPageReady() ?
                        AppStateMachine.State.READY : AppStateMachine.State.LOADING_WEB);
            } else {
                transitionTo(AppStateMachine.State.CHECKING_ROUTE);
            }
            routeCoordinator.requestCheck(RouteCoordinator.Trigger.RETRY);
            return true;
        }
        return false;
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
        if (downloadController != null) downloadController.register();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webViewController != null) webViewController.syncPageTheme();
        if (lifecycleCoordinator == null) return;
        handleLifecycleAction(lifecycleCoordinator.onResumed(System.currentTimeMillis(),
                AppSecurity.isEnabled(this), AppSecurity.getRelockTimeoutMs(this)));
    }

    @Override
    protected void onStop() {
        if (networkMonitor != null) networkMonitor.stop();
        if (downloadController != null) downloadController.unregister();
        if (lifecycleCoordinator != null) {
            lifecycleCoordinator.onStopped(System.currentTimeMillis(), isChangingConfigurations());
        }
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        rememberCurrentWebUrl();
        outState.putString(STATE_KEY, stateMachine.save());
        outState.putString(WEB_URL_KEY, lastWebUrl);
        outState.putString(BASE_URL_KEY, routeCoordinator.getActiveUrl());
        Bundle webState = new Bundle();
        webViewController.saveState(webState);
        if (!webState.isEmpty()) outState.putBundle(WEB_VIEW_STATE_KEY, webState);
    }

    @Override
    public void onBackPressed() {
        if ((stateMachine.getState() == AppStateMachine.State.READY ||
                stateMachine.getState() == AppStateMachine.State.LOADING_WEB) &&
                webViewController.canGoBack()) {
            webViewController.goBack();
            return;
        }
        if (stateMachine.getState() == AppStateMachine.State.SETTINGS &&
                routeCoordinator.hasConfiguredRoute()) {
            serverSettingsVisible = false;
            routeCoordinator.requestCheck(RouteCoordinator.Trigger.RETRY);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackPressedAt > 1800L) {
            lastBackPressedAt = now;
            Toast.makeText(this, "再按一次返回键退出", Toast.LENGTH_SHORT).show();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        getWindow().getDecorView().removeCallbacks(backgroundStartupProbe);
        getWindow().getDecorView().removeCallbacks(progressivePageRetry);
        getWindow().getDecorView().removeCallbacks(quickActionsPrewarm);
        unregisterScreenOffReceiver();
        if (downloadController != null) downloadController.unregister();
        if (networkMonitor != null) networkMonitor.stop();
        if (routeCoordinator != null) routeCoordinator.shutdown();
        QuickActionsSheet.release(this);
        if (webViewController != null) webViewController.destroy();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
