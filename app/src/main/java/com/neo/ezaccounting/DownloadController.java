package com.neo.ezaccounting;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.widget.Toast;

public final class DownloadController {
    public interface PermissionRequester {
        void requestLegacyStoragePermission();
    }

    private static final class PendingDownload {
        final String url;
        final String userAgent;
        final String contentDisposition;
        final String mimeType;

        PendingDownload(String url, String userAgent, String contentDisposition,
                        String mimeType) {
            this.url = url;
            this.userAgent = userAgent;
            this.contentDisposition = contentDisposition;
            this.mimeType = mimeType;
        }
    }

    private final Activity activity;
    private final PermissionRequester permissionRequester;
    private boolean receiverRegistered;
    private long lastDownloadId = -1L;
    private String lastDownloadFileName = "";
    private PendingDownload pendingDownload;

    private final BroadcastReceiver completionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
            if (id == lastDownloadId) showDownloadedFileDialog(id, lastDownloadFileName);
        }
    };

    public DownloadController(Activity activity, PermissionRequester permissionRequester) {
        this.activity = activity;
        this.permissionRequester = permissionRequester;
    }

    public DownloadListener createListener() {
        return (url, userAgent, contentDisposition, mimeType, contentLength) ->
                start(url, userAgent, contentDisposition, mimeType);
    }

    public void register() {
        if (receiverRegistered) return;
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(completionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            activity.registerReceiver(completionReceiver, filter);
        }
        receiverRegistered = true;
    }

    public void unregister() {
        if (!receiverRegistered) return;
        try {
            activity.unregisterReceiver(completionReceiver);
        } catch (Exception ignored) {
        }
        receiverRegistered = false;
    }

    public void onLegacyPermissionResult(boolean granted) {
        PendingDownload pending = pendingDownload;
        pendingDownload = null;
        if (!granted) {
            Toast.makeText(activity, "未授予存储权限，无法保存下载文件",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (pending != null) {
            start(pending.url, pending.userAgent, pending.contentDisposition,
                    pending.mimeType);
        }
    }

    private void start(String url, String userAgent, String contentDisposition,
                       String mimeType) {
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(activity, "下载地址无效", Toast.LENGTH_SHORT).show();
            return;
        }
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            Toast.makeText(activity,
                    "该文件由网页即时生成，请使用“在浏览器中打开”后下载",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED) {
            pendingDownload = new PendingDownload(url, userAgent, contentDisposition, mimeType);
            permissionRequester.requestLegacyStoragePermission();
            return;
        }

        try {
            String guessed = URLUtil.guessFileName(url, contentDisposition, mimeType);
            String fileName = uniqueName(guessed);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mimeType);
            if (userAgent != null && !userAgent.isEmpty()) {
                request.addRequestHeader("User-Agent", userAgent);
            }
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) request.addRequestHeader("Cookie", cookies);
            request.setTitle(fileName);
            request.setDescription("EZ记账正在下载");
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                    "EZ记账/" + fileName);
            DownloadManager manager =
                    (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            lastDownloadId = manager.enqueue(request);
            lastDownloadFileName = fileName;
            Toast.makeText(activity, "已开始下载：" + fileName, Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(activity, "下载失败，可尝试用浏览器打开", Toast.LENGTH_LONG).show();
        }
    }

    private String uniqueName(String original) {
        String name = original == null || original.trim().isEmpty() ?
                "EZ记账导出" : original.trim();
        name = name.replaceAll("[\\/:*?\"<>|]", "_");
        int dot = name.lastIndexOf('.');
        String suffix = "-" + System.currentTimeMillis();
        if (dot > 0) return name.substring(0, dot) + suffix + name.substring(dot);
        return name + suffix;
    }

    private void showDownloadedFileDialog(long downloadId, String fileName) {
        if (activity.isFinishing()) return;
        DownloadManager manager =
                (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        Uri uri = manager.getUriForDownloadedFile(downloadId);
        if (uri == null) return;
        String mime = manager.getMimeTypeForDownloadedFile(downloadId);
        new AlertDialog.Builder(activity)
                .setTitle("下载完成")
                .setMessage(fileName == null || fileName.isEmpty() ?
                        "文件已保存到下载目录" :
                        fileName + "\n已保存到下载目录/EZ记账")
                .setNegativeButton("关闭", null)
                .setPositiveButton("打开文件", (dialog, which) -> {
                    try {
                        Intent open = new Intent(Intent.ACTION_VIEW);
                        open.setDataAndType(uri, mime == null ? "*/*" : mime);
                        open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        activity.startActivity(open);
                    } catch (Exception error) {
                        Toast.makeText(activity, "没有可打开该文件的应用",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }
}
