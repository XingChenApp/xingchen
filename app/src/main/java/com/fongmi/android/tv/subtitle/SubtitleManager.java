package com.fongmi.android.tv.subtitle;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.SubtitleSetting;
import com.github.catvod.net.OkHttp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 字幕管理器：多源搜索、下载、解压、应用到播放器。
 * 所有网络操作在后台线程执行，通过 Callback 回主线程。
 */
public class SubtitleManager {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final List<SubtitleProvider> PROVIDERS = new ArrayList<>();

    static {
        PROVIDERS.add(new OpenSubtitlesProvider());
        PROVIDERS.add(new SubHDProvider());
        PROVIDERS.add(new ShooterProvider());
        PROVIDERS.add(new ZimukuProvider());
    }

    public interface SearchCallback {
        void onResult(List<SubtitleInfo> items);
    }

    public interface DownloadCallback {
        void onDone(File file);   // 成功：本地字幕文件；失败：null
    }

    /** 后台搜索全部可用源，按用户语言偏好排序 */
    public static void search(String query, SearchCallback callback) {
        EXECUTOR.execute(() -> {
            List<SubtitleInfo> all = new ArrayList<>();
            for (SubtitleProvider p : PROVIDERS) {
                if (!p.isAvailable()) continue;
                try {
                    List<SubtitleInfo> items = p.search(query);
                    if (items != null) all.addAll(items);
                } catch (Throwable ignored) {
                }
            }
            String pref = SubtitleSetting.getLang();
            Collections.sort(all, Comparator.comparingInt(a -> a.matchScore(pref)));
            List<SubtitleInfo> out = all.size() > 40 ? all.subList(0, 40) : all;
            MAIN.post(() -> callback.onResult(out));
        });
    }

    /** 后台下载字幕到缓存，处理压缩包解压 */
    public static void download(SubtitleInfo info, DownloadCallback callback) {
        EXECUTOR.execute(() -> {
            File file = downloadSync(info);
            MAIN.post(() -> callback.onDone(file));
        });
    }

    /** 下载并应用到指定播放器（player 为 null 时只下载不应用） */
    public static void downloadAndApply(SubtitleInfo info, PlayerManager player, DownloadCallback callback) {
        download(info, file -> {
            if (file != null) applyToPlayer(player, file);
            callback.onDone(file);
        });
    }

    /**
     * 播放时自动匹配：按偏好语言搜第一个可用字幕并加载。
     * 在后台线程执行，成功后自动应用到播放器。
     */
    public static void autoMatch(PlayerManager player, String title) {
        if (TextUtils.isEmpty(title)) return;
        if (!SubtitleSetting.isAutoMatch()) return;
        EXECUTOR.execute(() -> {
            List<SubtitleInfo> all = new ArrayList<>();
            for (SubtitleProvider p : PROVIDERS) {
                if (!p.isAvailable()) continue;
                try {
                    List<SubtitleInfo> items = p.search(title);
                    if (items != null) all.addAll(items);
                } catch (Throwable ignored) {
                }
            }
            if (all.isEmpty()) return;
            String pref = SubtitleSetting.getLang();
            Collections.sort(all, Comparator.comparingInt(a -> a.matchScore(pref)));
            SubtitleInfo best = all.get(0);
            File file = downloadSync(best);
            final PlayerManager pm = player;
            if (file != null) MAIN.post(() -> applyToPlayer(pm, file));
        });
    }

    /** 把本地字幕文件应用到播放器（Exo/MPV 通用），player 为 null 时跳过 */
    public static void applyToPlayer(PlayerManager player, File file) {
        try {
            if (player != null && file != null) player.setSub(Sub.from(file.getAbsolutePath()));
        } catch (Throwable ignored) {
        }
    }

    // ---------------- 内部实现 ----------------

    private static File downloadSync(SubtitleInfo info) {
        try {
            String url = resolveRealUrl(info);
            if (TextUtils.isEmpty(url) || !url.startsWith("http")) return null;
            File dir = new File(App.get().getCacheDir(), "subtitles");
            if (!dir.exists()) dir.mkdirs();
            // 先下载到临时文件，根据内容判断是否为 zip
            String tmpName = "dl_" + System.currentTimeMillis() + ".tmp";
            File tmp = new File(dir, tmpName);
            if (!httpDownload(url, tmp)) return null;
            // 尝试按 zip 解压
            File extracted = tryUnzip(tmp, dir);
            if (extracted != null) {
                tmp.delete();
                return extracted;
            }
            // 不是 zip：按 format 重命名
            String ext = info.getFormat();
            if (TextUtils.isEmpty(ext)) ext = "srt";
            File out = new File(dir, "sub_" + System.currentTimeMillis() + "." + ext);
            if (tmp.renameTo(out)) return out;
            return tmp;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String resolveRealUrl(SubtitleInfo info) {
        String url = info.getDownloadUrl();
        try {
            if (url.startsWith("os://download/")) {
                long fileId = Long.parseLong(url.substring("os://download/".length()));
                return OpenSubtitlesProvider.resolveDownloadUrl(fileId);
            }
            if (url.startsWith("subhd://detail")) {
                return SubHDProvider.resolveDownloadUrl(url.substring("subhd://detail".length()));
            }
            if (url.startsWith("shooter://detail")) {
                return ShooterProvider.resolveDownloadUrl(url);
            }
            if (url.startsWith("zimuku://detail")) {
                return ZimukuProvider.resolveDownloadUrl(url);
            }
        } catch (Throwable ignored) {
        }
        return url;
    }

    private static boolean httpDownload(String url, File out) {
        try (okhttp3.Response res = OkHttp.newCall(url).execute()) {
            if (res.body() == null) return false;
            try (InputStream in = res.body().byteStream();
                 FileOutputStream fos = new FileOutputStream(out)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
            }
            return out.length() > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 如果是 zip，解压出第一个字幕文件；不是 zip 返回 null */
    private static File tryUnzip(File zipFile, File dir) {
        ZipFile zip = null;
        try {
            zip = new ZipFile(zipFile);
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName().toLowerCase();
                if (e.isDirectory()) continue;
                if (name.endsWith(".srt") || name.endsWith(".ass") || name.endsWith(".ssa") || name.endsWith(".vtt")) {
                    String ext = name.substring(name.lastIndexOf('.') + 1);
                    File out = new File(dir, "sub_" + System.currentTimeMillis() + "." + ext);
                    try (InputStream in = zip.getInputStream(e);
                         FileOutputStream fos = new FileOutputStream(out)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                    }
                    return out;
                }
            }
        } catch (Throwable ignored) {
            // 不是 zip
        } finally {
            try {
                if (zip != null) zip.close();
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
