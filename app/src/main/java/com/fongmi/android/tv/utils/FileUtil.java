package com.fongmi.android.tv.utils;

import android.content.Intent;
import android.net.Uri;
import android.os.StatFs;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.impl.Callback;
import com.github.catvod.utils.Path;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.Enumeration;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class FileUtil {

    public static File getWall(int index) {
        return Path.files("wallpaper_" + index);
    }

    public static File getWallCache() {
        return Path.files("wallpaper_cache");
    }

    public static void openFile(File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(getShareUri(file), FileUtil.getMimeType(file.getName()));
        App.get().startActivity(intent);
    }

    public static boolean gzipCompress(File target) {
        byte[] buffer = new byte[16384];
        File gz = new File(target.getAbsolutePath() + ".gz");
        boolean success = false;
        try (FileInputStream is = new FileInputStream(target); GZIPOutputStream os = new GZIPOutputStream(new FileOutputStream(gz))) {
            int read;
            while ((read = is.read(buffer)) > 0) os.write(buffer, 0, read);
            success = true;
        } catch (IOException e) {
            Log.e("FileUtil", "gzipCompress failed target=" + target.getAbsolutePath(), e);
        } finally {
            // 删除原文件必须以压缩成功为前提：失败时原文件保留、残缺 .gz 清掉，
            // 不许出现「原文件没了、压缩包又是坏的」静默丢数据。
            if (success) Path.clear(target);
            else Path.clear(gz);
        }
        return success;
    }

    public static boolean gzipDecompress(File target, File path) {
        byte[] buffer = new byte[16384];
        try (GZIPInputStream is = new GZIPInputStream(new BufferedInputStream(new FileInputStream(target))); BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(path))) {
            int read;
            while ((read = is.read(buffer)) != -1) os.write(buffer, 0, read);
            return true;
        } catch (Exception e) {
            Log.e("FileUtil", "gzipDecompress failed target=" + target.getAbsolutePath() + " out=" + path.getAbsolutePath(), e);
            // 失败不许留残缺输出冒充解压完成（调用方按文件存在性判断时会把残缺当成品，
            // 且 EPG 缓存这类存在性闸会让它永远不再重试）：清掉残缺，失败回 false。
            Path.clear(path);
            return false;
        }
    }

    public static boolean zipDecompress(File target, File path) {
        java.util.List<File> created = new java.util.ArrayList<>();
        try (ZipFile zip = new ZipFile(target)) {
            // 路径穿越校验（与 AppBackup.restore 的 safe/inside 同口径）：条目名带 ../
            // 能逃出目标目录——本函数被本地服务 /upload 的 zip 解压直调，局域网递来的
            // 压缩包不可信，越界条目一律跳过不写
            String base = path.getCanonicalPath();
            Enumeration<?> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) entries.nextElement();
                File out = new File(path, entry.getName());
                String outPath = out.getCanonicalPath();
                if (!outPath.equals(base) && !outPath.startsWith(base + File.separator)) continue;
                if (entry.isDirectory()) out.mkdirs();
                else {
                    if (!out.exists()) created.add(out);
                    Path.copy(zip.getInputStream(entry), out);
                }
            }
            return true;
        } catch (Exception e) {
            Log.e("FileUtil", "zipDecompress failed target=" + target.getAbsolutePath() + " out=" + path.getAbsolutePath(), e);
            // 失败回滚本次新建的文件，不留半棵目录树冒充解压完成；
            // 解压前已存在的文件（被覆盖的）无法还原，不动。
            for (int i = created.size() - 1; i >= 0; i--) Path.clear(created.get(i));
            return false;
        }
    }

    public static void clearCache(Callback callback) {
        Task.execute(() -> {
            Path.clear(Path.cache());
            App.post(callback::success);
        });
    }

    public static void getCacheSize(Callback callback) {
        Task.execute(() -> {
            String usage = byteCountToDisplaySize(getDirectorySize(Path.cache()));
            App.post(() -> callback.success(usage));
        });
    }

    public static long getDirectorySize(File dir) {
        long size = 0;
        if (dir == null) return 0;
        if (dir.isDirectory()) {
            for (File file : Path.list(dir)) {
                long child = getDirectorySize(file);
                if (size > Long.MAX_VALUE - child) return Long.MAX_VALUE;
                size += child;
            }
        }
        else size = dir.length();
        return size;
    }

    public static long getAvailableStorageSpace(File file) {
        return getStorageSpace(file).availableBytes();
    }

    public static StorageSpace getStorageSpace(File file) {
        try {
            File target = existingPath(file);
            if (target == null) return StorageSpace.unavailable();
            StatFs stat = new StatFs(target.getAbsolutePath());
            return StorageSpace.of(stat.getAvailableBytes(), stat.getTotalBytes());
        } catch (Exception e) {
            return StorageSpace.unavailable();
        }
    }

    private static File existingPath(File file) {
        File target = file;
        while (target != null && !target.exists()) target = target.getParentFile();
        return target;
    }

    public record StorageSpace(boolean available, long availableBytes, long totalBytes) {

        public static StorageSpace of(long availableBytes, long totalBytes) {
            boolean valid = availableBytes >= 0 && totalBytes > 0 && availableBytes <= totalBytes;
            return valid ? new StorageSpace(true, availableBytes, totalBytes) : unavailable();
        }

        public static StorageSpace unavailable() {
            return new StorageSpace(false, 0, 0);
        }
    }

    public static Uri getShareUri(String path) {
        return getShareUri(new File(path.replace("file://", "")));
    }

    public static Uri getShareUri(File file) {
        return FileProvider.getUriForFile(App.get(), App.get().getPackageName() + ".provider", file);
    }

    private static String getMimeType(String fileName) {
        String mimeType = URLConnection.guessContentTypeFromName(fileName);
        return TextUtils.isEmpty(mimeType) ? "*/*" : mimeType;
    }

    public static String byteCountToDisplaySize(long size) {
        if (size <= 0) return ResUtil.getString(R.string.none);
        String[] units = new String[]{"bytes", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
}
