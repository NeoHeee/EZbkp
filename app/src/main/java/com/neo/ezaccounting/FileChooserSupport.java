package com.neo.ezaccounting;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.webkit.WebChromeClient;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public final class FileChooserSupport {
    public static final class Request {
        public final Intent chooserIntent;
        public final Uri cameraUri;

        Request(Intent chooserIntent, Uri cameraUri) {
            this.chooserIntent = chooserIntent;
            this.cameraUri = cameraUri;
        }
    }

    private FileChooserSupport() {}

    public static Request create(Activity activity, WebChromeClient.FileChooserParams params)
            throws Exception {
        String[] accepts = params == null ? new String[0] : params.getAcceptTypes();
        boolean wantsImages = acceptsImages(accepts) || (params != null && params.isCaptureEnabled());
        boolean multiple = params != null &&
                params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE;

        Intent picker = createPickerIntent(accepts, wantsImages, multiple);
        ArrayList<Intent> initial = new ArrayList<>();
        Uri cameraUri = null;

        if (wantsImages) {
            File directory = new File(activity.getCacheDir(), "camera");
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IllegalStateException("无法创建相机临时目录");
            }
            File image = File.createTempFile("ez-photo-", ".jpg", directory);
            cameraUri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", image);
            Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            camera.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            camera.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            camera.setClipData(ClipData.newRawUri("EZ记账拍照", cameraUri));
            if (camera.resolveActivity(activity.getPackageManager()) != null) {
                initial.add(camera);
            }
        }

        Intent chooser = Intent.createChooser(picker,
                wantsImages ? "选择照片、拍照或浏览文件" : "选择账单或附件");
        if (!initial.isEmpty()) {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, initial.toArray(new Intent[0]));
        }
        return new Request(chooser, cameraUri);
    }

    private static Intent createPickerIntent(String[] accepts, boolean wantsImages, boolean multiple) {
        if (wantsImages && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent picker = new Intent(MediaStore.ACTION_PICK_IMAGES);
            picker.setType("image/*");
            if (multiple) {
                int max = Math.min(20, MediaStore.getPickImagesMaxLimit());
                if (max > 1) picker.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, max);
            }
            return picker;
        }

        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType(bestMimeType(accepts, wantsImages));
        picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);
        return picker;
    }

    public static Uri[] parseResult(int resultCode, Intent data, Uri cameraUri) {
        if (resultCode != Activity.RESULT_OK) return null;
        Set<Uri> uris = new LinkedHashSet<>();
        if (data != null) {
            ClipData clipData = data.getClipData();
            if (clipData != null) {
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    Uri uri = clipData.getItemAt(i).getUri();
                    if (uri != null) uris.add(uri);
                }
            }
            if (data.getData() != null) uris.add(data.getData());
        }
        if (uris.isEmpty() && cameraUri != null) uris.add(cameraUri);
        return uris.isEmpty() ? null : uris.toArray(new Uri[0]);
    }

    private static boolean acceptsImages(String[] accepts) {
        if (accepts == null || accepts.length == 0) return false;
        for (String accept : accepts) {
            if (accept != null && accept.toLowerCase().startsWith("image/")) return true;
        }
        return false;
    }

    private static String bestMimeType(String[] accepts, boolean wantsImages) {
        if (wantsImages) return "image/*";
        if (accepts != null) {
            for (String accept : accepts) {
                if (accept != null && !accept.trim().isEmpty() && accept.contains("/")) {
                    return accept.trim();
                }
            }
        }
        return "*/*";
    }
}
