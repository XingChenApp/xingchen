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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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

    // C-25: 暂停屏障。pause() 的现树调用方全在主线程（DownloadTaskAdapter /
    // MusicDownloadActivity 的点按、pauseAllLive），同步等旧线程会卡界面，所以
    // pause 只摘除+cancel 并在此登记屏障；resume 时先查屏障：已收尾才放行，
    // 没收尾就转到后台线程有界等待（上限 STOP_WAIT_TIMEOUT_MS），等到了
    // 才真正 start，超时则保持暂停并给中文提示，绝不让新旧两个下载器
    // 同时写同一个文件。
    private static final long STOP_WAIT_TIMEOUT_MS = 10_000L;
    private final Map<String, StopBarrier> stopping = new ConcurrentHashMap<>();
    private final Set<String> resumePending = ConcurrentHashMap.newKeySet();

    /** 被 pause 摘除的一组下载器（同一任务至多一种），等它们全部收尾才算停稳。 */
    private static class StopBarrier {
        private final HttpDownloader http;
        private final M3u8Downloader m3u8;

        StopBarrier(HttpDownloader http, M3u8Downloader m3u8) {
            this.http = http;
            this.m3u8 = m3u8;
        }

        boolean isFinished() {
            return (http == null || http.isFinished()) && (m3u8 == null || m3u8.isFinished());
        }

        /** 在总预算 timeoutMs 内依次等每个下载器收尾；任一超时即整体超时。 */
        boolean awaitFinished(long timeoutMs) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            if (http != null) {
                long remain = deadline - System.currentTimeMillis();
                if (remain <= 0 || !http.awaitFinished(remain, TimeUnit.MILLISECONDS)) return false;
            }
            if (m3u8 != null) {
                long remain = deadline - System.currentTimeMillis();
                if (remain <= 0 || !m3u8.awaitFinished(remain, TimeUnit.MILLISECONDS)) return false;
            }
            return true;
        }
    }

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
        // C-25: 登记屏障（主线程不等待）。resume/delete 见屏障未收尾时，
        // 先在后台等旧线程停笔，不许立即开新下载器/删文件与旧线程抢同一文件。
        if (h != null || m != null) stopping.put(taskId, new StopBarrier(h, m));
        task.setStatus(DownloadTask.STATUS_PAUSED);
        notifyChanged(task);
    }

    public void resume(String taskId, Map<String, String> headers) {
        DownloadTask task = tasks.get(taskId);
        if (task == null) return;
        if (task.getStatus() == DownloadTask.STATUS_COMPLETED) return;
        // 已有在跑的下载器时不重复开第二个写同一文件
        if (httpDownloaders.containsKey(taskId) || m3u8Downloaders.containsKey(taskId)) return;
        StopBarrier barrier = stopping.get(taskId);
        if (barrier != null) {
            if (barrier.isFinished()) {
                stopping.remove(taskId, barrier);
            } else {
                // C-25: 旧线程还没停笔——主线程立即返回，后台有界等待，
                // 等确认停稳后才开工；多次点继续只挂一个等待者。
                if (!resumePending.add(taskId)) return;
                Notify.show("正在等待上一个下载停稳…");
                // 一次性等待线程设守护：它有 10 秒上限自收尾，不许拖住进程退出
                Thread resumeWaiter = new Thread(() -> {
                    try {
                        boolean stopped = barrier.awaitFinished(STOP_WAIT_TIMEOUT_MS);
                        stopping.remove(taskId, barrier);
                        if (!stopped) {
                            DownloadTask t = tasks.get(taskId);
                            if (t != null && t.getStatus() == DownloadTask.STATUS_PAUSED) {
                                Notify.show("上一个下载还没停稳，请稍后再点继续");
                            }
                            return;
                        }
                        DownloadTask t = tasks.get(taskId);
                        if (t == null) return; // 等待期间被删除
                        if (t.getStatus() == DownloadTask.STATUS_COMPLETED) return;
                        if (httpDownloaders.containsKey(taskId) || m3u8Downloaders.containsKey(taskId)) return;
                        start(t, headers);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        resumePending.remove(taskId);
                    }
                }, "download-resume-wait");
                resumeWaiter.setDaemon(true);
                resumeWaiter.start();
                return;
            }
        }
        start(task, headers);
    }

    public void resume(String taskId) {
        resume(taskId, null);
    }

    public void delete(String taskId) {
        pause(taskId);
        DownloadTask task = tasks.remove(taskId);
        // 先取走屏障再清表（顺序反了快照永远为空、延期删除永不触发）
        StopBarrier barrier = stopping.remove(taskId);
        if (task != null && task.getFilePath() != null) {
            File file = new File(task.getFilePath());
            if (barrier != null && !barrier.isFinished()) {
                // C-25: 旧线程（尤其 m3u8 合并）可能还会写/重建目标文件，
                // 等它停笔后再删；超时（上限同 STOP_WAIT_TIMEOUT_MS）也删，
                // 删已摘除任务的文件句柄写的是已 unlink 的旧文件，不会污染新文件。
                // 一次性等待线程设守护：有上限自收尾，不许拖住进程退出。
                Thread deleteWaiter = new Thread(() -> {
                    try {
                        barrier.awaitFinished(STOP_WAIT_TIMEOUT_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    file.delete();
                }, "download-delete-wait");
                deleteWaiter.setDaemon(true);
                deleteWaiter.start();
            } else {
                file.delete();
            }
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
