package com.example.hikerview.utils;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import java.io.File;
import java.util.Objects;

/**
 * 照官 8.83 本体按需转写（官源 smali_classes2/com/example/hikerview/utils/UriUtils.smali）。
 * getRootDir：①团落码，逐字照官（含 requireNonNull 与重复取目录的官形态）。
 * getFilePath：2026-11-19 fileSelect 回流对齐批补落——官 UriUtils.getFilePath(Uri) 本体
 * ＝try 内委托 UriTool.uriToFilePath(uri, Application)，异常回 null；咱家无 Application
 * 静态口，签名带 Context，解析本体照 UriTool.smali（695-1620 行）逐分支转写：
 * file 协议直取路径、content 协议先取 document_id（getDocumentId→content://media/ 合成
 * →查 uri 本身 document_id 列覆盖），再按外置存储/Downloads/Media 三类文档分发，
 * ShareRoot: 前缀拼 getRootDir，余者回 null。
 */
public class UriUtils {

    public static String getRootDir(Context context) {
        if (!Objects.requireNonNull(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)).exists()) {
            Objects.requireNonNull(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)).mkdirs();
        }
        return Objects.requireNonNull(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)).getAbsolutePath();
    }

    /** 官 UriUtils.getFilePath：uri 为 null 或解析异常一律回 null。 */
    public static String getFilePath(Context context, Uri uri) {
        if (uri == null) return null;
        try {
            return uriToFilePath(context, uri);
        } catch (Exception e) {
            return null;
        }
    }

    /** 照 UriTool.uriToFilePath 逐分支转写（官源行 63-141）。 */
    private static String uriToFilePath(Context context, Uri uri) {
        String scheme = uri.getScheme();
        if ("file".equals(scheme)) {
            String path = uri.getPath();
            return path == null ? null : new File(path).getAbsolutePath();
        }
        if (!"content".equals(scheme)) return null;
        String docId = null;
        try {
            docId = DocumentsContract.getDocumentId(uri);
        } catch (Exception ignored) {
        }
        if (docId == null) {
            String uriStr = uri.toString();
            if (uriStr.startsWith("content://media/")) {
                String last = uriStr.substring(uriStr.lastIndexOf('/') + 1);
                if (uriStr.contains("/image")) {
                    docId = "image:" + last;
                } else if (uriStr.contains("/video")) {
                    docId = "video:" + last;
                } else if (uriStr.contains("/audio")) {
                    docId = "audio:" + last;
                }
            }
        }
        // 官形：再查 uri 本身的 document_id 列，非空则覆盖上面的 docId。
        try (Cursor c = context.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex("document_id");
                if (idx >= 0 && !c.isNull(idx)) docId = c.getString(idx);
            }
        } catch (Exception ignored) {
        }
        if (docId == null) return null;
        String authority = uri.getAuthority();
        if ("com.android.externalstorage.documents".equals(authority)) {
            String[] parts = docId.split(":");
            if (parts.length >= 2 && "primary".equalsIgnoreCase(parts[0])) {
                return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + parts[1];
            }
            return null;
        }
        if ("com.android.providers.downloads.documents".equals(authority)) {
            String[] parts = docId.split(":");
            long id;
            try {
                id = Long.parseLong(parts[parts.length - 1]);
            } catch (Exception e) {
                return null;
            }
            Uri downloadsUri = Build.VERSION.SDK_INT >= 29
                    ? ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                    : ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), id);
            return getDataColumn(context, downloadsUri, null, null);
        }
        if ("com.android.providers.media.documents".equals(authority) || "media".equals(authority)) {
            String[] parts = docId.split(":");
            if (parts.length < 2) return null;
            Uri contentUri;
            if ("image".equals(parts[0])) {
                contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            } else if ("video".equals(parts[0])) {
                contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            } else if ("audio".equals(parts[0])) {
                contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            } else {
                return null;
            }
            return getDataColumn(context, contentUri, "_id=?", new String[]{parts[1]});
        }
        if (docId.startsWith("ShareRoot:")) {
            return getRootDir(context) + docId.replace("ShareRoot:", "");
        }
        return null;
    }

    /** 照 UriTool.getDataColumn：只查 _data 一列，异常/无行回 null，游标必关。 */
    private static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, new String[]{"_data"}, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow("_data"));
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }
}
