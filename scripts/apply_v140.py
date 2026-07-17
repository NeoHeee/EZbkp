from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


def write(path, text):
    (ROOT / path).write_text(text, encoding="utf-8")


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"missing patch anchor: {label}")
    return text.replace(old, new, 1)


def replace_regex(text, pattern, replacement, label):
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"regex patch failed ({count}): {label}")
    return updated


main_path = "app/src/main/java/com/neo/ezaccounting/MainActivity.java"
main = read(main_path)

main = replace_once(main,
'''    private static final String KEY_LAST_ROUTE = "last_route";\n''',
'''    private static final String KEY_LAST_ROUTE = "last_route";\n    private static final String KEY_LOCAL_LATENCY = "local_latency_ms";\n    private static final String KEY_PUBLIC_LATENCY = "public_latency_ms";\n    private static final String KEY_LAST_UPDATE_CHECK = "last_update_check";\n    private static final long AUTO_UPDATE_INTERVAL_MS = 24L * 60L * 60L * 1000L;\n''', "preference keys")

main = replace_once(main,
'''    private boolean screenReceiverRegistered;\n''',
'''    private boolean screenReceiverRegistered;\n    private boolean downloadReceiverRegistered;\n    private boolean routeCheckInProgress;\n    private long lastUnlockAt;\n    private long lastDownloadId = -1L;\n    private String lastDownloadFileName = "";\n    private String pendingShortcutAction;\n    private Uri pendingCameraUri;\n    private RouteManager routeManager;\n    private RouteManager.Selection lastRouteSelection;\n    private NetworkMonitor networkMonitor;\n\n    private final BroadcastReceiver downloadCompleteReceiver = new BroadcastReceiver() {\n        @Override\n        public void onReceive(Context context, Intent intent) {\n            if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;\n            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);\n            if (id == lastDownloadId) showDownloadedFileDialog(id, lastDownloadFileName);\n        }\n    };\n''', "experience fields")

main = replace_regex(main,
        r'''    @Override\n    protected void onCreate\(Bundle savedInstanceState\) \{.*?\n    \}\n\n    private void initializeApp\(\) \{.*?\n    \}\n''',
'''    @Override\n    protected void onCreate(Bundle savedInstanceState) {\n        super.onCreate(savedInstanceState);\n        UiTheme.applySystemBars(this);\n        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);\n        localUrl = preferences.getString(KEY_LOCAL_URL, "");\n        publicUrl = preferences.getString(KEY_PUBLIC_URL, "");\n        pendingShortcutAction = ShortcutActions.read(getIntent());\n        routeManager = new RouteManager();\n        networkMonitor = new NetworkMonitor(this, this::onDefaultNetworkChanged);\n        registerScreenOffReceiver();\n        registerDownloadReceiver();\n\n        if (ShortcutActions.LOCK.equals(pendingShortcutAction) && AppSecurity.isEnabled(this)) {\n            pendingShortcutAction = null;\n        }\n\n        if (AppSecurity.isEnabled(this)) {\n            showLoadingScreen("请完成安全验证…");\n            requestAppUnlock();\n        } else {\n            initializeApp();\n        }\n    }\n\n    @Override\n    protected void onNewIntent(Intent intent) {\n        super.onNewIntent(intent);\n        setIntent(intent);\n        String action = ShortcutActions.read(intent);\n        if (action == null) return;\n        pendingShortcutAction = action;\n        if (appInitialized && !authFlowInProgress) {\n            getWindow().getDecorView().post(this::handlePendingShortcutAction);\n        }\n    }\n\n    private void initializeApp() {\n        if (appInitialized) return;\n        appInitialized = true;\n        if (ShortcutActions.ROUTES.equals(pendingShortcutAction)) {\n            pendingShortcutAction = null;\n            showServerSetup();\n        } else if (hasSavedRoutes()) {\n            launchPreferredRoute(true);\n        } else {\n            showServerSetup();\n        }\n        getWindow().getDecorView().post(() -> {\n            handlePendingShortcutAction();\n            scheduleAutomaticUpdateCheck();\n        });\n    }\n\n    private void handlePendingShortcutAction() {\n        if (authFlowInProgress || pendingShortcutAction == null) return;\n        String action = pendingShortcutAction;\n        pendingShortcutAction = null;\n        if (ShortcutActions.ROUTES.equals(action)) {\n            showServerSetup();\n        } else if (ShortcutActions.SECURITY.equals(action)) {\n            if (!AppSecurity.isEnabled(this) || System.currentTimeMillis() - lastUnlockAt < 10000L) {\n                openSecuritySettings();\n            } else {\n                requestSecuritySettings();\n            }\n        } else if (ShortcutActions.LOCK.equals(action)) {\n            lockImmediately();\n        } else if (ShortcutActions.UPDATE.equals(action)) {\n            checkForUpdates(true);\n        }\n    }\n''', "onCreate and shortcut lifecycle")

main = replace_regex(main,
        r'''    private void launchPreferredRoute\(boolean fromStartup\) \{.*?\n    \}\n\n    private RouteSelection determinePreferredRoute\(\) \{.*?\n    \}\n\n    private boolean isReachable\(String urlString, int timeoutMs\) \{.*?\n    \}\n''',
'''    private void launchPreferredRoute(boolean fromStartup) {\n        recheckRoutes(fromStartup, false);\n    }\n\n    private void recheckRoutes(boolean fromStartup, boolean networkTriggered) {\n        if (routeCheckInProgress || routeManager == null) return;\n        routeCheckInProgress = true;\n        if (fromStartup && webView == null) showLoadingScreen("正在并行检测可用线路…");\n        String lastRoute = preferences.getString(KEY_LAST_ROUTE, "");\n        routeManager.selectAsync(localUrl, publicUrl, lastRoute, selection -> {\n            routeCheckInProgress = false;\n            if (isFinishing() || isDestroyed()) return;\n            applyRouteSelection(selection, fromStartup, networkTriggered);\n        });\n    }\n\n    private void applyRouteSelection(RouteManager.Selection selection, boolean fromStartup,\n                                     boolean networkTriggered) {\n        lastRouteSelection = selection;\n        preferences.edit()\n                .putLong(KEY_LOCAL_LATENCY, selection.local == null ? -1L : selection.local.latencyMs)\n                .putLong(KEY_PUBLIC_LATENCY,\n                        selection.publicRoute == null ? -1L : selection.publicRoute.latencyMs)\n                .apply();\n\n        if (!selection.hasRoute()) {\n            if (fromStartup || webView == null) {\n                Toast.makeText(this, "没有可用线路，请检查地址或网络连接", Toast.LENGTH_LONG).show();\n                showServerSetup();\n            } else if (!networkTriggered) {\n                Toast.makeText(this, "当前两条线路都不可用", Toast.LENGTH_LONG).show();\n            }\n            return;\n        }\n\n        RouteManager.ProbeResult selected = selection.selected;\n        preferences.edit().putString(KEY_LAST_ROUTE, selected.url).apply();\n        boolean changed = activeBaseUrl == null || !activeBaseUrl.equals(selected.url);\n        String targetUrl = changed ? remapCurrentUrl(selected.url) : selected.url;\n        activeBaseUrl = selected.url;\n        activeRoute = selected.type;\n\n        if (webView == null) {\n            showWebClient(targetUrl);\n        } else if (changed) {\n            fallbackAttempted = false;\n            webView.loadUrl(targetUrl);\n            Toast.makeText(this, "已自动切换到" + routeName(activeRoute) +\n                    "（" + selected.latencyMs + " ms）", Toast.LENGTH_SHORT).show();\n        } else if (!fromStartup && !networkTriggered) {\n            Toast.makeText(this, "当前使用" + routeName(activeRoute) +\n                    "，延迟 " + selected.latencyMs + " ms", Toast.LENGTH_SHORT).show();\n        }\n    }\n\n    private String remapCurrentUrl(String newBaseUrl) {\n        if (webView == null || activeBaseUrl == null || webView.getUrl() == null) return newBaseUrl;\n        try {\n            URI oldBase = new URI(activeBaseUrl);\n            URI current = new URI(webView.getUrl());\n            if (!WebOriginPolicy.isSameOrigin(activeBaseUrl, current.toString())) return newBaseUrl;\n            URI nextBase = new URI(newBaseUrl);\n            return new URI(nextBase.getScheme(), nextBase.getAuthority(), current.getPath(),\n                    current.getQuery(), current.getFragment()).toString();\n        } catch (Exception ignored) {\n            return newBaseUrl;\n        }\n    }\n\n    private String routeName(int routeType) {\n        return routeType == ROUTE_LOCAL ? "本地线路" :\n                routeType == ROUTE_PUBLIC ? "公网线路" : "未知线路";\n    }\n''', "parallel route selection")

main = replace_once(main,
'''        scrollView.setBackgroundColor(Color.rgb(245, 248, 248));\n''',
'''        scrollView.setBackgroundColor(UiTheme.background(this));\n''', "setup background")
main = main.replace('title.setTextColor(Color.rgb(20, 35, 35));',
                    'title.setTextColor(UiTheme.primaryText(this));')
main = main.replace('description.setTextColor(Color.rgb(80, 95, 95));',
                    'description.setTextColor(UiTheme.secondaryText(this));')
main = main.replace('label.setTextColor(Color.rgb(26, 51, 51));',
                    'label.setTextColor(UiTheme.primaryText(this));')
main = main.replace('note.setTextColor(Color.rgb(105, 120, 120));',
                    'note.setTextColor(UiTheme.tertiaryText(this));')
main = replace_once(main,
'''        input.setTextSize(16);\n        input.setPadding(dp(16), dp(13), dp(16), dp(13));\n        GradientDrawable background = new GradientDrawable();\n        background.setColor(Color.WHITE);\n        background.setStroke(dp(1), Color.rgb(196, 210, 208));\n''',
'''        input.setTextSize(16);\n        input.setTextColor(UiTheme.primaryText(this));\n        input.setHintTextColor(UiTheme.tertiaryText(this));\n        input.setPadding(dp(16), dp(13), dp(16), dp(13));\n        GradientDrawable background = new GradientDrawable();\n        background.setColor(UiTheme.surface(this));\n        background.setStroke(dp(1), UiTheme.border(this));\n''', "dark input")

main = replace_regex(main,
        r'''    private void showLoadingScreen\(String text\) \{.*?\n    \}\n''',
'''    private void showLoadingScreen(String text) {\n        showingWebView = false;\n        UiTheme.applySystemBars(this);\n        FrameLayout root = new FrameLayout(this);\n        root.setBackgroundColor(UiTheme.background(this));\n        LinearLayout box = new LinearLayout(this);\n        box.setOrientation(LinearLayout.VERTICAL);\n        box.setGravity(Gravity.CENTER);\n        box.setPadding(dp(24), dp(24), dp(24), dp(24));\n        box.addView(new android.widget.ProgressBar(this));\n        TextView message = new TextView(this);\n        message.setText(text);\n        message.setTextSize(15);\n        message.setTextColor(UiTheme.secondaryText(this));\n        message.setPadding(0, dp(14), 0, 0);\n        box.addView(message);\n        root.addView(box, new FrameLayout.LayoutParams(\n                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));\n        setContentView(root);\n    }\n''', "dark loading screen")

main = replace_regex(main,
        r'''    private void showWebClient\(String url\) \{.*?\n    \}\n''',
'''    private void showWebClient(String url) {\n        showingWebView = true;\n        hasLoadedSuccessfully = false;\n        fallbackAttempted = false;\n        UiTheme.applySystemBars(this);\n        FrameLayout root = new FrameLayout(this);\n        root.setBackgroundColor(UiTheme.webBackground(this));\n        swipeRefreshLayout = new SwipeRefreshLayout(this);\n        swipeRefreshLayout.setColorSchemeColors(UiTheme.accent(this));\n        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(UiTheme.surface(this));\n        swipeRefreshLayout.setOnRefreshListener(() -> { if (webView != null) webView.reload(); });\n        webView = new WebView(this);\n        webView.setBackgroundColor(UiTheme.webBackground(this));\n        swipeRefreshLayout.addView(webView, new ViewGroup.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));\n        root.addView(swipeRefreshLayout, new FrameLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));\n        setContentView(root);\n        configureWebView();\n        setupHiddenGestureMenu();\n        webView.loadUrl(url);\n    }\n''', "dark web client")

main = main.replace('@SuppressWarnings("SetJavaScriptEnabled")\n    private void configureWebView()',
                    '@SuppressWarnings({"SetJavaScriptEnabled", "deprecation"})\n    private void configureWebView()')
main = replace_once(main,
'''        settings.setMediaPlaybackRequiresUserGesture(false);\n        boolean secureOrigin = activeBaseUrl != null &&\n''',
'''        settings.setMediaPlaybackRequiresUserGesture(false);\n        boolean darkMode = UiTheme.isDark(this);\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {\n            settings.setAlgorithmicDarkeningAllowed(darkMode);\n        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {\n            settings.setForceDark(darkMode ? WebSettings.FORCE_DARK_ON : WebSettings.FORCE_DARK_OFF);\n        }\n        boolean secureOrigin = activeBaseUrl != null &&\n''', "web dark mode")
main = main.replace('EZAccounting/1.3.0', 'EZAccounting/1.4.0')

main = replace_regex(main,
        r'''        webView.setWebChromeClient\(new WebChromeClient\(\) \{.*?        \}\);\n        webView.setDownloadListener''',
'''        webView.setWebChromeClient(new WebChromeClient() {\n            @Override\n            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback,\n                                             FileChooserParams params) {\n                if (filePathCallback != null) filePathCallback.onReceiveValue(null);\n                filePathCallback = callback;\n                try {\n                    FileChooserSupport.Request request = FileChooserSupport.create(MainActivity.this, params);\n                    pendingCameraUri = request.cameraUri;\n                    startActivityForResult(request.chooserIntent, FILE_CHOOSER_REQUEST);\n                    return true;\n                } catch (Exception error) {\n                    filePathCallback = null;\n                    pendingCameraUri = null;\n                    Toast.makeText(MainActivity.this, "无法打开照片或文件选择器",\n                            Toast.LENGTH_LONG).show();\n                    return false;\n                }\n            }\n        });\n        webView.setDownloadListener''', "enhanced file chooser")

main = replace_regex(main,
        r'''    private void openQuickActions\(\) \{.*?\n    \}\n\n    private void lockImmediately''',
'''    private void openQuickActions() {\n        CharSequence[] items = {\n                "返回首页", "线路状态与测速", "在浏览器中打开", "更换线路地址",\n                "立即锁定", "进入 App 的安全验证", "检查更新",\n                "清除登录与缓存", "WebView 信息"\n        };\n        String title = activeRoute == ROUTE_LOCAL ? "隐藏功能菜单（当前：本地线路）" :\n                activeRoute == ROUTE_PUBLIC ? "隐藏功能菜单（当前：公网线路）" : "隐藏功能菜单";\n        new AlertDialog.Builder(this)\n                .setTitle(title)\n                .setItems(items, (dialog, which) -> {\n                    switch (which) {\n                        case 0: if (webView != null) webView.loadUrl(activeBaseUrl); break;\n                        case 1: showRouteStatusAndRetest(); break;\n                        case 2:\n                            if (webView != null) openExternal(Uri.parse(\n                                    webView.getUrl() == null ? activeBaseUrl : webView.getUrl()));\n                            break;\n                        case 3: confirmChangeServer(); break;\n                        case 4: lockImmediately(); break;\n                        case 5: requestSecuritySettings(); break;\n                        case 6: checkForUpdates(true); break;\n                        case 7: confirmClearSiteData(); break;\n                        case 8:\n                            try { startActivity(new Intent(Settings.ACTION_WEBVIEW_SETTINGS)); }\n                            catch (Exception ignored) {\n                                Toast.makeText(this, WebView.getCurrentWebViewPackage() == null ?\n                                                "无法读取 WebView 信息" :\n                                                WebView.getCurrentWebViewPackage().packageName,\n                                        Toast.LENGTH_LONG).show();\n                            }\n                            break;\n                    }\n                })\n                .setNegativeButton("关闭", null)\n                .show();\n    }\n\n    private void showRouteStatusAndRetest() {\n        recheckRoutes(false, false);\n        RouteManager.Selection selection = lastRouteSelection;\n        String localStatus = selection == null || selection.local == null ? "等待测速" : selection.local.label();\n        String publicStatus = selection == null || selection.publicRoute == null ?\n                "等待测速" : selection.publicRoute.label();\n        new AlertDialog.Builder(this)\n                .setTitle("线路状态")\n                .setMessage("当前线路：" + routeName(activeRoute) +\n                        "\\n本地线路：" + localStatus +\n                        "\\n公网线路：" + publicStatus +\n                        "\\n\\n已在后台重新测速；网络变化时会自动重新判断并保留当前页面路径。")\n                .setPositiveButton("知道了", null)\n                .show();\n    }\n\n    private void lockImmediately''', "quick actions")

main = replace_regex(main,
        r'''    private DownloadListener createDownloadListener\(\) \{.*?\n    \}\n''',
'''    private DownloadListener createDownloadListener() {\n        return (url, userAgent, contentDisposition, mimeType, contentLength) -> {\n            if (url != null && (url.startsWith("blob:") || url.startsWith("data:"))) {\n                Toast.makeText(this, "该导出文件由网页即时生成，请使用“在浏览器中打开”后下载",\n                        Toast.LENGTH_LONG).show();\n                return;\n            }\n            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&\n                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=\n                            PackageManager.PERMISSION_GRANTED) {\n                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},\n                        STORAGE_PERMISSION_REQUEST);\n                Toast.makeText(this, "授权后请再次点击下载", Toast.LENGTH_SHORT).show();\n                return;\n            }\n            try {\n                String guessed = URLUtil.guessFileName(url, contentDisposition, mimeType);\n                String fileName = uniqueDownloadName(guessed);\n                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));\n                request.setMimeType(mimeType);\n                request.addRequestHeader("User-Agent", userAgent);\n                String cookies = CookieManager.getInstance().getCookie(url);\n                if (cookies != null) request.addRequestHeader("Cookie", cookies);\n                request.setTitle(fileName);\n                request.setDescription("EZ记账正在下载");\n                request.setNotificationVisibility(\n                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);\n                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,\n                        "EZ记账/" + fileName);\n                DownloadManager manager =\n                        (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);\n                lastDownloadId = manager.enqueue(request);\n                lastDownloadFileName = fileName;\n                Toast.makeText(this, "已开始下载：" + fileName, Toast.LENGTH_SHORT).show();\n            } catch (Exception error) {\n                Toast.makeText(this, "下载失败，可尝试用浏览器打开", Toast.LENGTH_LONG).show();\n            }\n        };\n    }\n\n    private String uniqueDownloadName(String original) {\n        String name = original == null || original.trim().isEmpty() ? "EZ记账导出" : original.trim();\n        name = name.replaceAll("[\\\\/:*?\\\"<>|]", "_");\n        int dot = name.lastIndexOf('.');\n        String suffix = "-" + System.currentTimeMillis();\n        if (dot > 0) return name.substring(0, dot) + suffix + name.substring(dot);\n        return name + suffix;\n    }\n''', "download enhancement")

main = replace_once(main,
'''        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {\n            filePathCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));\n            filePathCallback = null;\n            return;\n        }\n''',
'''        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {\n            filePathCallback.onReceiveValue(\n                    FileChooserSupport.parseResult(resultCode, data, pendingCameraUri));\n            filePathCallback = null;\n            pendingCameraUri = null;\n            return;\n        }\n''', "file chooser result")

main = replace_once(main,
'''            if (resultCode == Activity.RESULT_OK) {\n                forceRelock = false;\n                if (!appInitialized) initializeApp();\n''',
'''            if (resultCode == Activity.RESULT_OK) {\n                forceRelock = false;\n                lastUnlockAt = System.currentTimeMillis();\n                if (!appInitialized) initializeApp();\n                else getWindow().getDecorView().post(this::handlePendingShortcutAction);\n''', "unlock timestamp")

insert_before_confirm = '''\n    private void checkForUpdates(boolean userInitiated) {\n        if (userInitiated) Toast.makeText(this, "正在检查更新…", Toast.LENGTH_SHORT).show();\n        UpdateChecker.checkAsync(BuildConfig.VERSION_NAME, result -> {\n            preferences.edit().putLong(KEY_LAST_UPDATE_CHECK, System.currentTimeMillis()).apply();\n            if (!result.success) {\n                if (userInitiated) {\n                    new AlertDialog.Builder(this)\n                            .setTitle("检查更新失败")\n                            .setMessage(result.error == null ? "网络请求失败" : result.error)\n                            .setPositiveButton("知道了", null)\n                            .show();\n                }\n                return;\n            }\n            if (!result.updateAvailable) {\n                if (userInitiated) {\n                    new AlertDialog.Builder(this)\n                            .setTitle("已是最新版本")\n                            .setMessage("当前版本：" + BuildConfig.VERSION_NAME +\n                                    "\\n最新版本：" + result.latestVersion)\n                            .setPositiveButton("知道了", null)\n                            .show();\n                }\n                return;\n            }\n            String notes = result.notes == null ? "暂无更新说明" : result.notes.trim();\n            if (notes.length() > 1200) notes = notes.substring(0, 1200) + "…";\n            AlertDialog.Builder builder = new AlertDialog.Builder(this)\n                    .setTitle(result.releaseName == null || result.releaseName.isEmpty() ?\n                            "发现新版本 " + result.latestVersion : result.releaseName)\n                    .setMessage("当前版本：" + BuildConfig.VERSION_NAME +\n                            "\\n最新版本：" + result.latestVersion + "\\n\\n" + notes)\n                    .setNegativeButton("稍后", null);\n            String downloadUrl = result.apkUrl == null || result.apkUrl.isEmpty() ?\n                    result.releaseUrl : result.apkUrl;\n            if (downloadUrl != null && !downloadUrl.isEmpty()) {\n                builder.setPositiveButton("下载更新",\n                        (dialog, which) -> openExternal(Uri.parse(downloadUrl)));\n            }\n            builder.show();\n        });\n    }\n\n    private void scheduleAutomaticUpdateCheck() {\n        long last = preferences.getLong(KEY_LAST_UPDATE_CHECK, 0L);\n        if (System.currentTimeMillis() - last >= AUTO_UPDATE_INTERVAL_MS) {\n            checkForUpdates(false);\n        }\n    }\n\n    private void onDefaultNetworkChanged() {\n        if (!appInitialized || authFlowInProgress || webView == null || !hasSavedRoutes()) return;\n        recheckRoutes(false, true);\n    }\n\n    private void registerDownloadReceiver() {\n        if (downloadReceiverRegistered) return;\n        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {\n            registerReceiver(downloadCompleteReceiver, filter, Context.RECEIVER_NOT_EXPORTED);\n        } else {\n            registerReceiver(downloadCompleteReceiver, filter);\n        }\n        downloadReceiverRegistered = true;\n    }\n\n    private void unregisterDownloadReceiver() {\n        if (!downloadReceiverRegistered) return;\n        try { unregisterReceiver(downloadCompleteReceiver); } catch (Exception ignored) {}\n        downloadReceiverRegistered = false;\n    }\n\n    private void showDownloadedFileDialog(long downloadId, String fileName) {\n        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);\n        Uri uri = manager.getUriForDownloadedFile(downloadId);\n        if (uri == null || isFinishing()) return;\n        String mime = manager.getMimeTypeForDownloadedFile(downloadId);\n        new AlertDialog.Builder(this)\n                .setTitle("下载完成")\n                .setMessage(fileName == null || fileName.isEmpty() ? "文件已保存到下载目录" :\n                        fileName + "\\n已保存到下载目录/EZ记账")\n                .setNegativeButton("关闭", null)\n                .setPositiveButton("打开文件", (dialog, which) -> {\n                    try {\n                        Intent open = new Intent(Intent.ACTION_VIEW);\n                        open.setDataAndType(uri, mime == null ? "*/*" : mime);\n                        open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);\n                        startActivity(open);\n                    } catch (Exception error) {\n                        Toast.makeText(this, "没有可打开该文件的应用", Toast.LENGTH_SHORT).show();\n                    }\n                })\n                .show();\n    }\n'''
main = replace_once(main, '\n    private void confirmChangeServer() {', insert_before_confirm + '\n    private void confirmChangeServer() {',
                    "update and download helpers")

main = replace_once(main,
'''    @Override\n    protected void onResume() {\n''',
'''    @Override\n    protected void onStart() {\n        super.onStart();\n        if (networkMonitor != null) networkMonitor.start();\n    }\n\n    @Override\n    protected void onResume() {\n''', "network monitor start")

main = replace_once(main,
'''    protected void onStop() {\n        super.onStop();\n        if (!isChangingConfigurations() && !authFlowInProgress && appInitialized)\n''',
'''    protected void onStop() {\n        if (networkMonitor != null) networkMonitor.stop();\n        super.onStop();\n        if (!isChangingConfigurations() && !authFlowInProgress && appInitialized)\n''', "network monitor stop")

main = replace_once(main,
'''    protected void onDestroy() {\n        unregisterScreenOffReceiver();\n        destroyWebViewIfNeeded();\n        super.onDestroy();\n''',
'''    protected void onDestroy() {\n        unregisterScreenOffReceiver();\n        unregisterDownloadReceiver();\n        if (networkMonitor != null) networkMonitor.stop();\n        if (routeManager != null) routeManager.shutdown();\n        destroyWebViewIfNeeded();\n        super.onDestroy();\n''', "experience cleanup")

write(main_path, main)

# Theme the custom security screens.
for path in [
    "app/src/main/java/com/neo/ezaccounting/LockActivity.java",
    "app/src/main/java/com/neo/ezaccounting/SecuritySettingsActivity.java",
]:
    text = read(path)
    text = replace_once(text, "        super.onCreate(savedInstanceState);\n",
                        "        super.onCreate(savedInstanceState);\n        UiTheme.applySystemBars(this);\n",
                        path + " system bars")
    text = text.replace("Color.rgb(245, 248, 248)", "UiTheme.background(this)")
    text = text.replace("Color.rgb(20, 35, 35)", "UiTheme.primaryText(this)")
    text = text.replace("Color.rgb(20, 45, 45)", "UiTheme.primaryText(this)")
    text = text.replace("Color.rgb(26, 51, 51)", "UiTheme.primaryText(this)")
    text = text.replace("Color.rgb(25, 50, 50)", "UiTheme.primaryText(this)")
    text = text.replace("Color.rgb(45, 70, 70)", "UiTheme.primaryText(this)")
    text = text.replace("Color.rgb(50, 70, 70)", "UiTheme.secondaryText(this)")
    text = text.replace("Color.rgb(80, 95, 95)", "UiTheme.secondaryText(this)")
    text = text.replace("Color.rgb(90, 105, 105)", "UiTheme.secondaryText(this)")
    text = text.replace("Color.rgb(105, 120, 120)", "UiTheme.tertiaryText(this)")
    text = text.replace("Color.WHITE, Color.rgb(196, 210, 208)",
                        "UiTheme.surface(this), UiTheme.border(this)")
    text = text.replace("Color.WHITE, Color.rgb(205, 218, 216)",
                        "UiTheme.surface(this), UiTheme.border(this)")
    write(path, text)

# Version and artifact names.
gradle_path = "app/build.gradle"
gradle = read(gradle_path)
gradle = gradle.replace("versionCode 6", "versionCode 7")
gradle = gradle.replace("versionName '1.3.1'", "versionName '1.4.0'")
write(gradle_path, gradle)

for path in [".github/workflows/android.yml", ".github/workflows/signed-release.yml"]:
    workflow = read(path).replace("v1.3.1", "v1.4.0")
    write(path, workflow)

readme_path = "README.md"
readme = read(readme_path)
section = '''\n\n## v1.4.0 体验增强\n\n- 桌面长按图标可进入线路设置、安全设置、立即锁定和检查更新\n- 跟随系统深色模式，并同步适配状态栏、导航栏和安全验证页面\n- 支持相机拍照、Android 系统照片选择器和多张图片选择\n- 下载文件使用独立目录和唯一文件名，完成后可直接打开\n- 本地与公网线路并行测速，优先保留上次成功线路并在网络变化时自动切换\n- 隐藏菜单可查看线路延迟并检查 GitHub Releases 更新\n'''
if "## v1.4.0 体验增强" not in readme:
    readme += section
write(readme_path, readme)

# The integration script is temporary and should not remain in the merged source.
Path(__file__).unlink()
print("v1.4.0 integration applied")
