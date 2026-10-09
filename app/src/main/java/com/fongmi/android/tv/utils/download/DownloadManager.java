package com.fongmi.android.tv.utils.download;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.ui.activity.DownloadActivity;
import com.fongmi.android.tv.utils.Notify;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 下载管理器：任务队列 + 调度
 */
public class DownloadManager {

    private static final String PREFS = "xingchen";
    private static final String KEY_THREADS = "xingchen.download_threads";

    private static DownloadManager instance;

    private final Map<String, DownloadTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, HttpDownloader> httpDownloaders = new HashMap<>();
    private final Map<String, M3u8Downloader> m3u8Downloaders = new HashMap<>();
    private final List<Listener> listeners = new ArrayList<>();

    public interface Listener {
        void onTaskChanged(DownloadTask task);
    }

    public static synchronized DownloadManager get() {
        if (instance == null) instance = new DownloadManager();
        return instance;
    }

    public void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void notifyChanged(DownloadTask task) {
        App.post(() -> {
            for (Listener l : new ArrayList<>(listeners)) l.onTaskChanged(task);
        });
    }

    /** 线程数设置 1-32，默认 8 */
    public static int getThreadCount(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_THREADS, 8);
    }

    public static void setThreadCount(Context context, int count) {
        count = Math.max(1, Math.min(32, count));
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_THREADS, count).apply();
    }

    public List<DownloadTask> getTasks() {
        List<DownloadTask> list = new ArrayList<>(tasks.values());
        list.sort((a, b) -> Long.compare(b.getCreateTime(), a.getCreateTime()));
        return list;
    }

    public DownloadTask getTask(String id) {
        return tasks.get(id);
    }

    /**
     * 添加下载任务
     * @param url 下载链接
     * @param name 文件名（可空，自动从 URL 取）
     * @param headers 请求头（可空）
     */
    public DownloadTask add(String url, String name, Map<String, String> headers) {
        if (url == null || url.isEmpty()) {
            Notify.show("下载链接为空");
            return null;
        }
        Context context = App.get();
        if (name == null || name.isEmpty()) name = guessFileName(url);
        DownloadTask task = new DownloadTask(url, name);
        // 确定保存路径
        File dir = getDownloadDir(context);
        if (!dir.exists()) dir.mkdirs();
        String ext = task.isM3u8() ? ".mp4" : getExtension(url);
        File target = new File(dir, sanitizeFileName(name) + ext);
        // 避免重名
        int i = 1;
        while (target.exists()) {
            target = new File(dir, sanitizeFileName(name) + "_" + (i++) + ext);
        }
        task.setFilePath(target.getAbsolutePath());
        tasks.put(task.getId(), task);
        notifyChanged(task);
        start(task, headers);
        Notify.show("已加入下载: " + name);
        return task;
    }

    public DownloadTask add(String url, String name) {
        return add(url, name, null);
    }

    private void start(DownloadTask task, Map<String, String> headers) {
        task.setStatus(DownloadTask.STATUS_DOWNLOADING);
        notifyChanged(task);
        Context context = App.get();
        int threads = getThreadCount(context);
        File target = new File(task.getFilePath());
        if (task.isM3u8()) {
            M3u8Downloader downloader = new M3u8Downloader(task.getUrl(), target, headers, threads, new M3u8Downloader.Listener() {
                @Override
                public void onProgress(int downloadedSegments, int totalSegments) {
                    task.setTotalBytes(totalSegments);
                    task.setDownloadedBytes(downloadedSegments);
                    notifyChanged(task);
                }
                @Override
                public void onMerging() {
                    task.setStatus(DownloadTask.STATUS_MERGING);
                    notifyChanged(task);
                }
                @Override
                public void onComplete(File file) {
                    task.setStatus(DownloadTask.STATUS_COMPLETED);
                    task.setDownloadedBytes(task.getTotalBytes());
                    m3u8Downloaders.remove(task.getId());
                    notifyChanged(task);
                    Notify.show("下载完成: " + task.getName());
                }
                @Override
                public void onError(String msg) {
                    task.setStatus(DownloadTask.STATUS_ERROR);
                    task.setError(msg);
                    m3u8Downloaders.remove(task.getId());
                    notifyChanged(task);
                }
            });
            m3u8Downloaders.put(task.getId(), downloader);
            downloader.start();
        } else {
            HttpDownloader downloader = new HttpDownloader(task.getUrl(), target, headers, threads, new HttpDownloader.Listener() {
                @Override
                public void onProgress(long downloaded, long total) {
                    task.setDownloadedBytes(downloaded);
                    task.setTotalBytes(total);
                    notifyChanged(task);
                }
                @Override
                public void onComplete(File file) {
                    task.setStatus(DownloadTask.STATUS_COMPLETED);
                    httpDownloaders.remove(task.getId());
                    notifyChanged(task);
                    Notify.show("下载完成: " + task.getName());
                }
                @Override
                public void onError(String msg) {
                    task.setStatus(DownloadTask.STATUS_ERROR);
                    task.setError(msg);
                    httpDownloaders.remove(task.getId());
                    notifyChanged(task);
                }
            });
            httpDownloaders.put(task.getId(), downloader);
            downloader.start();
        }
    }

    public void pause(String taskId) {
        DownloadTask task = tasks.get(taskId);
        if (task == null) return;
        HttpDownloader h = httpDownloaders.remove(taskId);
        if (h != null) h.cancel();
        M3u8Downloader m = m3u8Downloaders.remove(taskId);
        if (m != null) m.cancel();
        task.setStatus(DownloadTask.STATUS_PAUSED);
        notifyChanged(task);
    }

    public void resume(String taskId, Map<String, String> headers) {
        DownloadTask task = tasks.get(taskId);
        if (task == null) return;
        if (task.getStatus() == DownloadTask.STATUS_COMPLETED) return;
        start(task, headers);
    }

    public void resume(String taskId) {
        resume(taskId, null);
    }

    public void delete(String taskId) {
        pause(taskId);
        DownloadTask task = tasks.remove(taskId);
        if (task != null && task.getFilePath() != null) {
            new File(task.getFilePath()).delete();
        }
        if (task != null) notifyChanged(task);
    }

    private File getDownloadDir(Context context) {
        // 优先用 SAF 目录
        Uri uri = DownloadActivity.getDownloadDirUri(context);
        if (uri != null) {
            // SAF 目录：用 app 私有目录做中转，下载完成后再提醒用户
            // 简化：仍用私有目录，保证写入一定成功
        }
        File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "XingChen");
        // 兼容旧版：filesDir/Download
        File legacy = new File(context.getFilesDir(), "Download");
        if (legacy.exists() && !dir.exists()) return legacy;
        return dir;
    }

    private String guessFileName(String url) {
        try {
            String path = Uri.parse(url).getPath();
            if (path != null) {
                int idx = path.lastIndexOf('/');
                if (idx >= 0 && idx < path.length() - 1) {
                    String name = path.substring(idx + 1);
                    int q = name.indexOf('?');
                    if (q > 0) name = name.substring(0, q);
                    if (!name.isEmpty()) return name;
                }
            }
        } catch (Exception ignored) {}
        return "video_" + System.currentTimeMillis();
    }

    private String getExtension(String url) {
        try {
            String path = Uri.parse(url).getPath();
            if (path != null) {
                int idx = path.lastIndexOf('.');
                if (idx > path.lastIndexOf('/')) {
                    String ext = path.substring(idx);
                    if (ext.length() <= 5) return ext;
                }
            }
        } catch (Exception ignored) {}
        return ".mp4";
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[\\/:*?\"<>|]", "_");
    }
}
