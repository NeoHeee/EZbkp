from pathlib import Path
import re


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"Missing expected block: {label}")
    return text.replace(old, new, 1)


main = Path("app/src/main/java/com/neo/ezaccounting/MainActivity.java")
text = main.read_text(encoding="utf-8")

text = replace_once(
    text,
    "import android.content.Context;\nimport android.content.Intent;\n",
    "import android.content.BroadcastReceiver;\nimport android.content.Context;\nimport android.content.Intent;\nimport android.content.IntentFilter;\n",
    "broadcast imports",
)
text = text.replace("    private static final long BACKGROUND_RELOCK_MS = 15_000L;\n", "")
text = replace_once(
    text,
    "    private boolean authFlowInProgress;\n    private long backgroundAt;\n\n    private boolean twoFingerTapCandidate;",
    """    private boolean authFlowInProgress;
    private long backgroundAt;
    private boolean forceRelock;
    private boolean screenReceiverRegistered;

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

    private boolean twoFingerTapCandidate;""",
    "security lifecycle fields",
)
text = replace_once(
    text,
    '        publicUrl = preferences.getString(KEY_PUBLIC_URL, "");\n\n        if (AppSecurity.isEnabled(this)) {',
    '        publicUrl = preferences.getString(KEY_PUBLIC_URL, "");\n        registerScreenOffReceiver();\n\n        if (AppSecurity.isEnabled(this)) {',
    "screen receiver registration",
)
text = text.replace("EZAccounting/1.2.1", "EZAccounting/1.3.0")
text = replace_once(
    text,
    """        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(false);""",
    """        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setBuiltInZoomControls(false);""",
    "webview file access",
)
text = replace_once(
    text,
    "        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);",
    """        boolean secureOrigin = activeBaseUrl != null &&
                "https".equalsIgnoreCase(Uri.parse(activeBaseUrl).getScheme());
        settings.setMixedContentMode(secureOrigin ?
                WebSettings.MIXED_CONTENT_NEVER_ALLOW :
                WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);""",
    "mixed content policy",
)

quick_actions = """    private void openQuickActions() {
        CharSequence[] items = {
                "返回首页", "重新检测线路", "在浏览器中打开", "更换线路地址",
                "立即锁定", "进入 App 的安全验证", "清除登录与缓存", "WebView 信息"
        };
        new AlertDialog.Builder(this)
                .setTitle(activeRoute == ROUTE_LOCAL ? "隐藏功能菜单（当前：本地线路）" :
                        activeRoute == ROUTE_PUBLIC ? "隐藏功能菜单（当前：公网线路）" : "隐藏功能菜单")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0: if (webView != null) webView.loadUrl(activeBaseUrl); break;
                        case 1: launchPreferredRoute(false); break;
                        case 2:
                            if (webView != null) openExternal(Uri.parse(
                                    webView.getUrl() == null ? activeBaseUrl : webView.getUrl()));
                            break;
                        case 3: confirmChangeServer(); break;
                        case 4: lockImmediately(); break;
                        case 5: requestSecuritySettings(); break;
                        case 6: confirmClearSiteData(); break;
                        case 7:
                            try { startActivity(new Intent(Settings.ACTION_WEBVIEW_SETTINGS)); }
                            catch (Exception ignored) {
                                Toast.makeText(this, WebView.getCurrentWebViewPackage() == null ?
                                                "无法读取 WebView 信息" : WebView.getCurrentWebViewPackage().packageName,
                                        Toast.LENGTH_LONG).show();
                            }
                            break;
                    }
                })
                .setNegativeButton("关闭", null)
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

    private void requestSecuritySettings"""
text, count = re.subn(
    r"    private void openQuickActions\(\) \{.*?\n    \}\n\n    private void requestSecuritySettings",
    quick_actions,
    text,
    count=1,
    flags=re.S,
)
if count != 1:
    raise SystemExit("Unable to replace hidden actions menu")

navigation = """    private boolean handleNavigation(Uri uri) {
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

    private void openExternal"""
text, count = re.subn(
    r"    private boolean handleNavigation\(Uri uri\) \{.*?\n    \}\n\n    private void openExternal",
    navigation,
    text,
    count=1,
    flags=re.S,
)
if count != 1:
    raise SystemExit("Unable to replace navigation policy")

text = replace_once(
    text,
    """        if (requestCode == REQUEST_UNLOCK_APP) {
            authFlowInProgress = false;
            backgroundAt = 0;
            if (resultCode == Activity.RESULT_OK) {
                if (!appInitialized) initializeApp();""",
    """        if (requestCode == REQUEST_UNLOCK_APP) {
            authFlowInProgress = false;
            backgroundAt = 0;
            if (resultCode == Activity.RESULT_OK) {
                forceRelock = false;
                if (!appInitialized) initializeApp();""",
    "unlock result",
)

receiver_helpers = """    private void registerScreenOffReceiver() {
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
    protected void onResume() {
        super.onResume();
        if (!appInitialized || authFlowInProgress || !AppSecurity.isEnabled(this)) return;
        if (LockPolicy.shouldRelock(backgroundAt, System.currentTimeMillis(),
                AppSecurity.getRelockTimeoutMs(this), forceRelock)) {
            backgroundAt = 0;
            forceRelock = false;
            requestAppUnlock();
        }
    }"""
text, count = re.subn(
    r"    @Override\n    protected void onResume\(\) \{.*?\n    \}",
    receiver_helpers,
    text,
    count=1,
    flags=re.S,
)
if count != 1:
    raise SystemExit("Unable to replace onResume policy")

text = replace_once(
    text,
    """    protected void onDestroy() {
        destroyWebViewIfNeeded();
        super.onDestroy();
    }""",
    """    protected void onDestroy() {
        unregisterScreenOffReceiver();
        destroyWebViewIfNeeded();
        super.onDestroy();
    }""",
    "receiver cleanup",
)
main.write_text(text, encoding="utf-8")

gradle = Path("app/build.gradle")
text = gradle.read_text(encoding="utf-8")
text = text.replace("versionCode 4", "versionCode 5")
text = text.replace("versionName '1.2.1'", "versionName '1.3.0'")
if 'testImplementation "junit:junit:4.13.2"' not in text:
    text = text.replace(
        '    implementation "androidx.biometric:biometric:1.1.0"\n',
        '    implementation "androidx.biometric:biometric:1.1.0"\n    testImplementation "junit:junit:4.13.2"\n',
    )
gradle.write_text(text, encoding="utf-8")

signed = Path(".github/workflows/signed-release.yml")
text = signed.read_text(encoding="utf-8")
text = text.replace("EZ记账-v1.2.1-signed.apk", "EZ记账-v1.3.0-signed.apk")
text = text.replace("EZ记账-Signed-Release-v1.2.1", "EZ记账-Signed-Release-v1.3.0")
signed.write_text(text, encoding="utf-8")

readme = Path("README.md")
text = readme.read_text(encoding="utf-8")
text = text.replace(
    "- 冷启动验证；后台停留 15 秒后返回重新验证",
    "- 冷启动验证；后台自动锁定时间可选立即、15秒、1分钟、5分钟或仅完全退出后",
)
text = text.replace(
    "- 支持图片、账单和附件上传",
    "- 熄屏后立即锁定可选；隐藏菜单支持手动立即锁定\n- PIN和图形连续输错后启用递增等待限制\n- HTTPS线路禁止混合加载HTTP子资源，并按协议、域名、端口校验网页来源\n- 支持图片、账单和附件上传",
)
text = text.replace(
    "- **关闭安全验证**：恢复为直接进入应用。",
    "- **自动锁定时间**：立即、15秒、1分钟、5分钟或仅完全退出后。\n- **熄屏后立即锁定**：可单独开启或关闭。\n- **关闭安全验证**：恢复为直接进入应用。",
)
text = text.replace(
    "- 更换本地/公网地址\n- 进入 App 的安全验证",
    "- 更换本地/公网地址\n- 立即锁定\n- 进入 App 的安全验证",
)
text = text.replace("- 当前版本：`1.2.1`", "- 当前版本：`1.3.0`")
readme.write_text(text, encoding="utf-8")

final_android_ci = """name: Android CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  workflow_dispatch:

permissions:
  contents: read

jobs:
  build-debug:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Java 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      - name: Install Android platform
        run: sdkmanager 'platforms;android-35' 'build-tools;35.0.0'

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '8.9'

      - name: Run unit tests and build debug APK
        run: gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug

      - name: Prepare artifact
        shell: bash
        run: |
          mkdir -p dist
          cp app/build/outputs/apk/debug/app-debug.apk 'dist/EZ记账-v1.3.0-debug.apk'
          cd dist
          sha256sum 'EZ记账-v1.3.0-debug.apk' > 'EZ记账-v1.3.0-debug.apk.sha256'

      - name: Upload debug APK
        uses: actions/upload-artifact@v4
        with:
          name: EZ记账-Debug
          path: dist/*
          if-no-files-found: error
          retention-days: 30
"""
Path(".github/workflows/android.yml").write_text(final_android_ci, encoding="utf-8")
Path(".github/workflows/apply-v1.3.0.yml").unlink(missing_ok=True)
Path("scripts/apply_v130.py").unlink(missing_ok=True)
