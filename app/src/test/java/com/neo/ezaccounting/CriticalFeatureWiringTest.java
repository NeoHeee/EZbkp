package com.neo.ezaccounting;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class CriticalFeatureWiringTest {
    @Test
    public void appLockActivitiesRemainRegisteredAndReachable() throws IOException {
        String manifest = readProjectFile("src/main/AndroidManifest.xml");
        String mainActivity = readProjectFile(
                "src/main/java/com/neo/ezaccounting/MainActivity.java");

        assertContains(manifest, "android:name=\".LockActivity\"");
        assertContains(manifest, "android:name=\".SecuritySettingsActivity\"");
        assertContains(mainActivity, "new Intent(this, LockActivity.class)");
        assertContains(mainActivity,
                "new Intent(this, SecuritySettingsActivity.class)");
    }

    @Test
    public void uploadAndDownloadControllersRemainConnected() throws IOException {
        String mainActivity = readProjectFile(
                "src/main/java/com/neo/ezaccounting/MainActivity.java");

        assertContains(mainActivity, "new DownloadController(this, this)");
        assertContains(mainActivity, "FileChooserSupport.create(this, params)");
        assertContains(mainActivity, "FileChooserSupport.parseResult(");
    }

    @Test
    public void quickCenterAndAutomaticRoutingRemainConnected() throws IOException {
        String mainActivity = readProjectFile(
                "src/main/java/com/neo/ezaccounting/MainActivity.java");

        assertContains(mainActivity, "QuickActionsSheet.prewarm(");
        assertContains(mainActivity, "QuickActionsSheet.show(");
        assertContains(mainActivity, "RouteStatusDialogPage.show(");
        assertContains(mainActivity, "new RouteCoordinator(preferences, this)");
        assertContains(mainActivity,
                "RouteCoordinator.Trigger.NETWORK_CHANGE");
    }

    @Test
    public void startupPipelineAndPageRestoreRemainConnected() throws IOException {
        String mainActivity = readProjectFile(
                "src/main/java/com/neo/ezaccounting/MainActivity.java");
        String webViewController = readProjectFile(
                "src/main/java/com/neo/ezaccounting/WebViewController.java");

        assertContains(mainActivity, "postDelayed(unlockPreload, UNLOCK_PRELOAD_DELAY_MS)");
        assertContains(mainActivity, "StartupPipeline.Stage.CONTENT_READY");
        assertContains(mainActivity, "quickActionsListener");
        assertContains(webViewController, "rememberPagePosition()");
        assertContains(webViewController, "restorePagePosition(url)");
    }

    @Test
    public void routeDiagnosticsRemainCompactAndExpandable() throws IOException {
        String routeStatus = readProjectFile(
                "src/main/java/com/neo/ezaccounting/RouteStatusDialogPage.java");

        assertContains(routeStatus, "details.setVisibility(View.GONE)");
        assertContains(routeStatus, "查看详情");
        assertContains(routeStatus, "outer.addView(buttons");
        assertContains(routeStatus, "phases.addView(metricCell(activity, \"HTTP\"");
    }

    @Test
    public void privacyBoundariesRemainExplicit() throws IOException {
        String manifest = readProjectFile("src/main/AndroidManifest.xml");
        String webView = readProjectFile(
                "src/main/java/com/neo/ezaccounting/WebViewController.java");
        String mainActivity = readProjectFile(
                "src/main/java/com/neo/ezaccounting/MainActivity.java");

        assertContains(manifest, "android:allowBackup=\"false\"");
        assertContains(manifest, "@xml/data_extraction_rules");
        assertContains(webView, "setAcceptThirdPartyCookies(webView, false)");
        assertContains(webView, "clearHttpAuthUsernamePassword()");
        assertContains(webView, "clearSslPreferences()");
        assertContains(mainActivity, "webViewController.setPreloadMode(true)");
        assertContains(mainActivity, "protected void onPause()");
    }

    @Test
    public void serverAddressBackReturnsToSettingsCenter() throws IOException {
        String mainActivity = readProjectFile(
                "src/main/java/com/neo/ezaccounting/MainActivity.java");

        assertContains(mainActivity, "serverAddressPageVisible = true");
        assertContains(mainActivity, "if (serverAddressPageVisible)");
        assertContains(mainActivity, "showSettingsCenter(false)");
    }

    private static String readProjectFile(String relativePath) throws IOException {
        Path path = Paths.get(relativePath);
        if (!Files.exists(path)) path = Paths.get("app").resolve(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static void assertContains(String source, String expected) {
        assertTrue("缺少关键功能接线：" + expected, source.contains(expected));
    }
}
