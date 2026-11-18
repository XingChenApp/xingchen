package com.fongmi.android.tv.utils.download;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.DownloadRecord;
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

    // 路径认领锁：add 的「去重扫表→定保存路径→入表」必须整段串行——
    // 批量入队在后台线程、单集入队在界面线程，两边并发 add 时若查认领
    // 与占路径被插队，两条同名任务能各拿到同一个未落盘路径（根见
    // addLocked 定路径段注释）。
    private final Object addLock = new Object();

    // 件 1 落盘：restore 只跑一次（谁先触发谁跑）；进度落盘节流账
    // （上次落盘时刻＋百分比），DB 只是展示/恢复口径，不跟回调同频写库。
    private final java.util.concurrent.atomic.AtomicBoolean restoreStarted =
            new java.util.concurrent.atomic.AtomicBoolean();
    // 恢复完成信号：restore 收口处置位＋发等待者/监听——呈现层判读任务表
    // 是否可信的唯一口（未完成时空表只是「还没灌回来」，不许当成没任务）。
    private volatile boolean restoreCompleted;
    private final List<Runnable> restoreWaiters = new ArrayList<>();
    private final Map<String, long[]> persistMarks = new ConcurrentHashMap<>();

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

        /** 件 1 恢复对账完成（restore 灌回内存表＋收编后只发一发）：完成前
         * 任务表可能还是空的，呈现层收到这发才把读到的表当真。 */
        default void onRestoreCompleted() {
        }
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
        // 件 2：每次状态迁移后对齐前台服务存亡（有在途才活）
        com.fongmi.android.tv.service.DownloadService.sync();
    }

    /**
     * 件 2「全部暂停」（通知动作口）：定案口径——在途转 PAUSED 的
     * 同时 WAITING 全部转 PAUSED（队列清空），恢复靠他逐个点继续。
     * 先翻 WAITING 再暂停在途：pause() 内部会补泵，顺序反了队首会被
     * 泵起来与全停语义打架。
     */
    public void pauseAllActive() {
        for (DownloadTask task : tasks.values()) {
            if (task.getStatus() == DownloadTask.STATUS_WAITING) {
                task.setStatus(DownloadTask.STATUS_PAUSED);
                persist(task);
                notifyChanged(task);
            }
        }
        for (DownloadTask task : tasks.values()) {
            if (task.getStatus() == DownloadTask.STATUS_DOWNLOADING
                    || task.getStatus() == DownloadTask.STATUS_MERGING) {
                pause(task.getId());
            }
        }
    }

    /** 件 1：状态迁移即落盘（强制写） */
    private void persist(DownloadTask task) {
        persistMarks.put(task.getId(), new long[]{System.currentTimeMillis(), task.getProgress()});
        DownloadStore.save(task);
    }

    // 件 4 采样：每任务 3 秒滑动窗口的 (时刻, 真实字节, 分片数) 环；
    // 速度在取值拍上算（通知 1 秒拍/列表绑定），不在字节回调里算。
    private static class SampleRing {
        final java.util.ArrayDeque<long[]> samples = new java.util.ArrayDeque<>();
        long lastComputeMs;
    }

    private final Map<String, SampleRing> sampleRings = new ConcurrentHashMap<>();

    /** 件 4：进度回调喂样本（字节/分片双口径），顺手按 ≥500ms 节拍刷新任务的速度/ETA 字段 */
    private void feedSample(DownloadTask task) {
        SampleRing ring = sampleRings.computeIfAbsent(task.getId(), k -> new SampleRing());
        long now = System.currentTimeMillis();
        synchronized (ring) {
            ring.samples.addLast(new long[]{now, task.getRealBytes(), task.isM3u8() ? task.getDownloadedBytes() : -1});
            while (!ring.samples.isEmpty() && now - ring.samples.peekFirst()[0] > 3000) ring.samples.pollFirst();
            if (now - ring.lastComputeMs < 500) return;
            ring.lastComputeMs = now;
            computeSpeed(task, ring, now);
        }
    }

    private void computeSpeed(DownloadTask task, SampleRing ring, long now) {
        if (ring.samples.size() < 2) return;
        long[] first = ring.samples.peekFirst();
        long[] last = ring.samples.peekLast();
        long dtMs = last[0] - first[0];
        if (dtMs <= 0) return;
        long dBytes = last[1] - first[1];
        task.setSpeedBps(dBytes < 0 ? 0 : dBytes * 1000 / dtMs);
        long speed = task.getSpeedBps();
        if (task.isM3u8()) {
            // m3u8 总字节未知：ETA 走分片口径（剩余片数 ÷ 窗口内片/秒）
            long dSegs = (first[2] >= 0 && last[2] >= 0) ? last[2] - first[2] : -1;
            if (dSegs > 0 && task.getTotalBytes() > 0) {
                long remainSegs = task.getTotalBytes() - task.getDownloadedBytes();
                task.setEtaSeconds(remainSegs <= 0 ? 0 : remainSegs * dtMs / (dSegs * 1000));
            } else {
                task.setEtaSeconds(-1);
            }
        } else if (speed > 0 && task.getTotalBytes() > 0) {
            long remain = task.getTotalBytes() - task.getDownloadedBytes();
            task.setEtaSeconds(remain <= 0 ? 0 : remain / speed);
        } else {
            task.setEtaSeconds(-1);
        }
    }

    /** 件 4：暂停/完成/出错时清采样——速度归零、ETA 清空，不许留冻结旧值骗人 */
    private void clearSample(DownloadTask task) {
        sampleRings.remove(task.getId());
        task.setSpeedBps(0);
        task.setEtaSeconds(-1);
    }

    /** 件 4：合计速度（通知正文与列表同源） */
    public long getAggregateSpeed() {
        long sum = 0;
        for (DownloadTask task : tasks.values()) {
            if (task.getStatus() == DownloadTask.STATUS_DOWNLOADING) sum += Math.max(0, task.getSpeedBps());
        }
        return sum;
    }

    /** 件 1：进度落盘节流——距上次 ≥1 秒或进度跨 ≥1% 才写 */
    private void persistProgress(DownloadTask task) {
        long now = System.currentTimeMillis();
        long[] mark = persistMarks.get(task.getId());
        if (mark != null && now - mark[0] < 1000 && Math.abs(task.getProgress() - mark[1]) < 1) return;
        persist(task);
    }

    /** 线程数设置 1-32，默认 8 */
    public static int getThreadCount(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_THREADS, 8);
    }

    public static void setThreadCount(Context context, int count) {
        count = Math.max(1, Math.min(32, count));
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_THREADS, count).apply();
    }

    private static final String KEY_MAX_CONCURRENT = "xingchen.download_max_concurrent";

    /** 件 3：同时下载任务数 1-5，默认 2（单任务内部已多线程分块，任务级并发是乘法关系） */
    public static int getMaxConcurrent(Context context) {
        int v = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_MAX_CONCURRENT, 2);
        return Math.max(1, Math.min(5, v));
    }

    public static void setMaxConcurrent(Context context, int count) {
        count = Math.max(1, Math.min(5, count));
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_MAX_CONCURRENT, count).apply();
        // 调高即时补位；调低不杀在途，只停新启动（pumpQueue 自然收口）
        get().pumpQueue();
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
        return add(url, name, headers, null, null, 0);
    }

    /**
     * 全参 add（件 1 落盘＋件 5 选档）：pic/qualityName/variantBandwidth 随任务
     * 落盘。同 url 去重口径（件 1 定）：已有 COMPLETED 且文件完好→不新建、
     * 提示已下载过并返回既有任务；已有 PAUSED/ERROR→走 resume 语义不新建；
     * 已在途→返回既有任务。只有「不同 url 撞同名」才保留 _1 改名。
     */
    public DownloadTask add(String url, String name, Map<String, String> headers,
                            String pic, String qualityName, long variantBandwidth) {
        return addInternal(url, name, headers, pic, qualityName, variantBandwidth, true);
    }

    /**
     * 音乐侧专用入口：不走同 url 去重。音乐有自己的 pending 账与去重
     * （MusicDownloadStore 只查在途三态），已完成的歌再下一次是它的
     * 正常流程；下载列表的「已下载过」口径不许压到音乐头上。落盘与
     * 队列与 add 同路。
     */
    public DownloadTask addDirect(String url, String name, Map<String, String> headers) {
        return addInternal(url, name, headers, null, null, 0, false);
    }

    private DownloadTask addInternal(String url, String name, Map<String, String> headers,
                                     String pic, String qualityName, long variantBandwidth, boolean dedupe) {
        if (url == null || url.isEmpty()) {
            Notify.show("下载链接为空");
            return null;
        }
        synchronized (addLock) {
            return addLocked(url, name, headers, pic, qualityName, variantBandwidth, dedupe);
        }
    }

    private DownloadTask addLocked(String url, String name, Map<String, String> headers,
                                   String pic, String qualityName, long variantBandwidth, boolean dedupe) {
        restore();
        for (DownloadTask existing : dedupe ? tasks.values() : java.util.Collections.<DownloadTask>emptyList()) {
            if (!url.equals(existing.getUrl())) continue;
            switch (existing.getStatus()) {
                case DownloadTask.STATUS_COMPLETED: {
                    File f = existing.getFilePath() == null ? null : new File(existing.getFilePath());
                    if (f != null && f.exists()) {
                        Notify.show("已下载过: " + existing.getName());
                        return existing;
                    }
                    // 文件已不在：旧条目按对账口径本该是「文件不存在」的
                    // 出错态，重新下载直接摘旧建新，不留同 url 双条目。
                    delete(existing.getId());
                    break;
                }
                case DownloadTask.STATUS_PAUSED:
                case DownloadTask.STATUS_ERROR: {
                    if (headers != null) existing.setHeaders(DownloadStore.copyHeaders(headers));
                    Notify.show("已在下载列表，继续下载: " + existing.getName());
                    resume(existing.getId(), headers);
                    return existing;
                }
                default: {
                    Notify.show("已在下载中: " + existing.getName());
                    return existing;
                }
            }
            break;
        }
        Context context = App.get();
        if (name == null || name.isEmpty()) name = guessFileName(url);
        DownloadTask task = new DownloadTask(url, name);
        task.setHeaders(DownloadStore.copyHeaders(headers));
        task.setPic(pic);
        task.setQualityName(qualityName);
        task.setVariantBandwidth(variantBandwidth);
        // 确定保存路径：唯一性要对两本账都成立——盘上已有文件，以及任务
        // 表里已被别的任务认领的路径。只查盘必漏一大批：m3u8 的目标文件
        // 合并前根本不在盘上（分片在 <名>.mp4.segs 里），HTTP 的也要等
        // 下载器线程落笔，排队中的任务更是一直没文件。两条同名任务（单集
        // 入队 name 只有剧名、批量里集名为空/同名都会撞）因此能拿到同一
        // 路径＝共用同一个文件与同一个 .segs 分片目录：m3u8 分片按序号
        // 互相跳过续用、合并出的是另一集（154 真机「点这个播另一个」），
        // HTTP 则后写覆盖先写。_N 后缀顺延到两本账都空为止（音乐侧
        // findPendingFile 本就按 名/_N 两形认文件，同口径）。
        File dir = getDownloadDir(context);
        if (!dir.exists()) dir.mkdirs();
        String ext = task.isM3u8() ? ".mp4" : getExtension(url);
        java.util.Set<String> claimed = new java.util.HashSet<>();
        for (DownloadTask t : tasks.values()) {
            if (t.getFilePath() != null) claimed.add(t.getFilePath());
        }
        String baseName = sanitizeFileName(name);
        File target = new File(dir, baseName + ext);
        int i = 1;
        while (target.exists() || claimed.contains(target.getAbsolutePath())) {
            target = new File(dir, baseName + "_" + (i++) + ext);
        }
        task.setFilePath(target.getAbsolutePath());
        task.setStatus(DownloadTask.STATUS_WAITING);
        tasks.put(task.getId(), task);
        persist(task);
        notifyChanged(task);
        pumpQueue();
        Notify.show("已加入下载: " + name);
        return task;
    }

    /**
     * 件 3 队列泵：数在途（DOWNLOADING＋MERGING）＜上限才按 createTime
     * 先后从 WAITING 里取任务启动。complete/error/pause/delete 收尾处
     * 与上限调高时都会补一泵。WAITING 任务没有下载器，删它无屏障问题。
     */
    public void pumpQueue() {
        int limit = getMaxConcurrent(App.get());
        while (true) {
            int active = 0;
            DownloadTask next = null;
            for (DownloadTask t : tasks.values()) {
                if (t.getStatus() == DownloadTask.STATUS_DOWNLOADING
                        || t.getStatus() == DownloadTask.STATUS_MERGING) {
                    active++;
                } else if (t.getStatus() == DownloadTask.STATUS_WAITING) {
                    if (next == null || t.getCreateTime() < next.getCreateTime()) next = t;
                }
            }
            if (active >= limit || next == null) return;
            start(next, next.getHeaders());
        }
    }

    /** 件 3/8：WAITING 排位（按 createTime 第几位，1 基）；非 WAITING 返回 0 */
    public int queuePosition(String taskId) {
        DownloadTask self = tasks.get(taskId);
        if (self == null || self.getStatus() != DownloadTask.STATUS_WAITING) return 0;
        int pos = 1;
        for (DownloadTask t : tasks.values()) {
            if (t.getStatus() == DownloadTask.STATUS_WAITING
                    && t.getCreateTime() < self.getCreateTime()) pos++;
        }
        return pos;
    }

    public DownloadTask add(String url, String name) {
        return add(url, name, null);
    }

    private void start(DownloadTask task, Map<String, String> headers) {
        if (headers != null) task.setHeaders(DownloadStore.copyHeaders(headers));
        task.setStatus(DownloadTask.STATUS_DOWNLOADING);
        task.setError(null);
        persist(task);
        notifyChanged(task);
        Context context = App.get();
        int threads = getThreadCount(context);
        File target = new File(task.getFilePath());
        if (task.isM3u8()) {
            M3u8Downloader downloader = new M3u8Downloader(task.getUrl(), target, headers, threads, new M3u8Downloader.Listener() {
                @Override
                public void onProgress(int downloadedSegments, int totalSegments, long bytes) {
                    task.setTotalBytes(totalSegments);
                    task.setDownloadedBytes(downloadedSegments);
                    task.setRealBytes(bytes);
                    feedSample(task);
                    persistProgress(task);
                    notifyChanged(task);
                }
                @Override
                public void onMerging() {
                    task.setStatus(DownloadTask.STATUS_MERGING);
                    persist(task);
                    notifyChanged(task);
                }
                @Override
                public void onComplete(File file) {
                    task.setStatus(DownloadTask.STATUS_COMPLETED);
                    task.setDownloadedBytes(task.getTotalBytes());
                    task.setFinishTime(System.currentTimeMillis());
                    clearSample(task);
                    m3u8Downloaders.remove(task.getId());
                    persist(task);
                    notifyChanged(task);
                    pumpQueue();
                    enforceCapacity();
                    Notify.show("下载完成: " + task.getName());
                }
                @Override
                public void onError(String msg) {
                    task.setStatus(DownloadTask.STATUS_ERROR);
                    task.setError(msg);
                    clearSample(task);
                    m3u8Downloaders.remove(task.getId());
                    persist(task);
                    notifyChanged(task);
                    pumpQueue();
                }
            }, task.getVariantBandwidth());
            m3u8Downloaders.put(task.getId(), downloader);
            downloader.start();
        } else {
            HttpDownloader downloader = new HttpDownloader(task.getUrl(), target, headers, threads, new HttpDownloader.Listener() {
                @Override
                public void onProgress(long downloaded, long total) {
                    task.setDownloadedBytes(downloaded);
                    task.setTotalBytes(total);
                    task.setRealBytes(downloaded);
                    feedSample(task);
                    persistProgress(task);
                    notifyChanged(task);
                }
                @Override
                public void onComplete(File file) {
                    task.setStatus(DownloadTask.STATUS_COMPLETED);
                    task.setFinishTime(System.currentTimeMillis());
                    clearSample(task);
                    httpDownloaders.remove(task.getId());
                    persist(task);
                    notifyChanged(task);
                    pumpQueue();
                    enforceCapacity();
                    Notify.show("下载完成: " + task.getName());
                }
                @Override
                public void onError(String msg) {
                    task.setStatus(DownloadTask.STATUS_ERROR);
                    task.setError(msg);
                    clearSample(task);
                    httpDownloaders.remove(task.getId());
                    persist(task);
                    notifyChanged(task);
                    pumpQueue();
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
        clearSample(task);
        persist(task);
        notifyChanged(task);
        pumpQueue();
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
                        enqueue(t, headers);
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
        enqueue(task, headers);
    }

    /**
     * 件 3：继续＝置 WAITING 进队尾再泵。C-25 屏障只管不双写，
     * 排队语义接在它后面——resume 不再绕过队列直 start。
     */
    private void enqueue(DownloadTask task, Map<String, String> headers) {
        if (headers != null) task.setHeaders(DownloadStore.copyHeaders(headers));
        if (task.getStatus() == DownloadTask.STATUS_PAUSED
                || task.getStatus() == DownloadTask.STATUS_ERROR) {
            task.setStatus(DownloadTask.STATUS_WAITING);
            task.setError(null);
            persist(task);
            notifyChanged(task);
        }
        pumpQueue();
    }

    public void resume(String taskId) {
        resume(taskId, null);
    }

    public void delete(String taskId) {
        pause(taskId);
        DownloadTask task = tasks.remove(taskId);
        persistMarks.remove(taskId);
        DownloadStore.delete(taskId);
        // 先取走屏障再清表（顺序反了快照永远为空、延期删除永不触发）
        StopBarrier barrier = stopping.remove(taskId);
        if (task != null && task.getFilePath() != null) {
            File file = new File(task.getFilePath());
            // 未完成 m3u8 的分片目录随任务一并删（DL-H3-5）：推导与下载侧同形（父目录＋目标名＋.segs）；
            // 已完成任务合并时已自清、HTTP 从无此目录，不存在即空转，原删除口径不变
            File segsDir = new File(file.getParent(), file.getName() + ".segs");
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
                    deleteSegmentsDir(segsDir);
                }, "download-delete-wait");
                deleteWaiter.setDaemon(true);
                deleteWaiter.start();
            } else {
                file.delete();
                deleteSegmentsDir(segsDir);
            }
        }
        if (task != null) notifyChanged(task);
    }

    /** 分片目录删除：与 M3u8Downloader.deleteDir 同形（分片平铺在目录里，一层删净再删目录本身） */
    private static void deleteSegmentsDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) f.delete();
        dir.delete();
    }

    /**
     * 件 1 恢复对账：幂等只跑一次，触发点＝DownloadActivity 首开 /
     * DownloadService 启动 / 首次 add / 设置页副题读点，谁先到谁触发。不在 App.onCreate
     * 同步做，全程后台线程：读记录→逐条对账→灌回内存表→扫盘收编孤儿。
     */
    public void restore() {
        if (!restoreStarted.compareAndSet(false, true)) return;
        com.fongmi.android.tv.utils.Task.execute(() -> {
            java.util.Set<String> knownPaths = new java.util.HashSet<>();
            for (DownloadRecord record : DownloadStore.loadAll()) {
                DownloadTask task = DownloadStore.toTask(record);
                if (task.getFilePath() != null) knownPaths.add(task.getFilePath());
                reconcile(task);
                // 本会话已先 add 出同 id 活任务时以活的为准，不覆盖
                if (tasks.putIfAbsent(task.getId(), task) == null) {
                    DownloadStore.save(task);
                    notifyChanged(task);
                }
            }
            adoptOrphans(knownPaths);
            markRestoreCompleted();
            onRestoreDone();
        });
    }

    /** 恢复对账是否已完成：false 时 getTasks() 读到的空表不作数 */
    public boolean isRestoreCompleted() {
        return restoreCompleted;
    }

    /**
     * 等恢复完成的一次性回调（给不常驻监听的呈现读点用，如设置页副题）：
     * 已完成立即主线程发；未完成登记，完成当刻发后即摘，不留常驻。
     */
    public void runWhenRestored(Runnable action) {
        if (action == null) return;
        synchronized (restoreWaiters) {
            if (!restoreCompleted) {
                restoreWaiters.add(action);
                return;
            }
        }
        App.post(action);
    }

    /** 恢复完成收口：先置位再发信号，保证回调里回读 isRestoreCompleted() 已为真 */
    private void markRestoreCompleted() {
        List<Runnable> waiters;
        synchronized (restoreWaiters) {
            restoreCompleted = true;
            waiters = new ArrayList<>(restoreWaiters);
            restoreWaiters.clear();
        }
        App.post(() -> {
            for (Runnable waiter : waiters) waiter.run();
            for (Listener l : new ArrayList<>(listeners)) l.onRestoreCompleted();
        });
    }

    /** 对账口径（件 1 定）：完成看文件在不在；在途一律翻暂停不自启；
     * 暂停/出错原样。条目只有他亲手删或清空才消失，文件没了也保留标错。 */
    private void reconcile(DownloadTask task) {
        File file = task.getFilePath() == null ? null : new File(task.getFilePath());
        switch (task.getStatus()) {
            case DownloadTask.STATUS_COMPLETED: {
                if (file == null || !file.exists()) {
                    task.setStatus(DownloadTask.STATUS_ERROR);
                    task.setError("文件不存在，可能已被删除");
                } else if (task.isM3u8()) {
                    if (task.getTotalBytes() > 0) task.setDownloadedBytes(task.getTotalBytes());
                } else {
                    task.setDownloadedBytes(file.length());
                }
                break;
            }
            case DownloadTask.STATUS_DOWNLOADING:
            case DownloadTask.STATUS_WAITING:
            case DownloadTask.STATUS_MERGING: {
                // 被杀时在途：恢复为已暂停，等他点继续（不用点就自己跑的算风险账）
                task.setStatus(DownloadTask.STATUS_PAUSED);
                break;
            }
            default:
                break;
        }
    }

    /**
     * 孤儿文件收编（升级首跑必现：存量下载全是无记录孤儿）：下载目录里
     * 无记录的普通文件补建 COMPLETED 记录。排除：旁车 .progress/.id、
     * 临时 .tmp、.segs 分片目录（无 url 无从续传，留盘不动不建条目）；
     * 带 .progress 的是没下完的半截（身份/地址无从考），同样不收编。
     */
    private void adoptOrphans(java.util.Set<String> knownPaths) {
        Context context = App.get();
        java.util.Set<File> dirs = new java.util.LinkedHashSet<>();
        dirs.add(getDownloadDir(context));
        // 换过目录后内置默认根也扫：换根前落在那里的无记录存量不能因为
        // 换根就再也收编不进来（有记录的靠记录里的绝对路径对账，不靠扫）
        dirs.add(defaultDownloadDir(context));
        File legacy = new File(context.getFilesDir(), "Download");
        if (legacy.exists()) dirs.add(legacy);
        // 历次用过的自选根全进扫描集（DL-H3-3）：自选→自选换根后，旧根的无记录存量同样收编
        for (String root : getUsedCustomRoots(context)) {
            if (root != null && !root.isEmpty()) dirs.add(new File(root));
        }
        for (File dir : dirs) adoptOrphansIn(dir, knownPaths);
    }

    /** 换根当场收编（DL-H3-4）：restore 每进程只跑一次，换新根后新根里的无记录存量
     * 原形要等下次启动才进列表——换根成功后由下载页调本口，对新根单独跑一遍同一收编口径 */
    public void adoptNewRoot(File dir) {
        if (dir == null) return;
        com.fongmi.android.tv.utils.Task.execute(() -> {
            java.util.Set<String> knownPaths = new java.util.HashSet<>();
            for (DownloadTask task : tasks.values()) {
                if (task.getFilePath() != null) knownPaths.add(task.getFilePath());
            }
            adoptOrphansIn(dir, knownPaths);
        });
    }

    /** 单根收编：口径与 restore 的 adoptOrphans 完全一致（排除旁车/临时/半截，有记录的不重建） */
    private void adoptOrphansIn(File dir, java.util.Set<String> knownPaths) {
        if (dir == null) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (!file.isFile() || file.length() == 0) continue;
            String name = file.getName();
            if (name.endsWith(".progress") || name.endsWith(".id") || name.endsWith(".tmp")) continue;
            if (new File(file.getAbsolutePath() + ".progress").exists()) continue;
            if (knownPaths.contains(file.getAbsolutePath())) continue;
            DownloadTask task = new DownloadTask("orphan-" + file.lastModified() + "-" + name.hashCode(),
                    "", stripExtension(name), file.lastModified());
            task.setFilePath(file.getAbsolutePath());
            task.setStatus(DownloadTask.STATUS_COMPLETED);
            task.setDownloadedBytes(file.length());
            task.setTotalBytes(file.length());
            task.setFinishTime(file.lastModified());
            // url 无从考：留空串占位（DownloadTask 构造对 null 已兼容判 m3u8）
            if (tasks.putIfAbsent(task.getId(), task) == null) {
                DownloadStore.save(task);
                notifyChanged(task);
            }
        }
    }

    private String stripExtension(String name) {
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(0, idx) : name;
    }

    /** 恢复收尾钩子：件 6 容量清理触发点之二（对账完后跑一轮） */
    private void onRestoreDone() {
        enforceCapacity();
    }


    // ---------- 件 6：容量上限与自动清理 ----------

    private static final String KEY_MAX_BYTES = "xingchen.download_max_bytes";

    /** 容量上限（字节，0＝不限，默认不限——自动删人东西的功能不许默认开） */
    public static long getMaxBytes(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_MAX_BYTES, 0L);
    }

    public static void setMaxBytes(Context context, long bytes) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_MAX_BYTES, Math.max(0, bytes)).apply();
        // 调低到已超限时即刻触发一轮（触发点之三）
        get().enforceCapacity();
    }

    /** 一轮清理的待报清单（标题＋释放字节＋上限值），呈现口径见 DownloadActivity/通知 */
    public static class CapacityReport {
        public final java.util.List<String> names;
        public final long freedBytes;
        public final long limitBytes;

        CapacityReport(java.util.List<String> names, long freedBytes, long limitBytes) {
            this.names = names;
            this.freedBytes = freedBytes;
            this.limitBytes = limitBytes;
        }
    }

    private static volatile CapacityReport pendingReport;

    /** 取走待报清单（取后即清，只报一次） */
    public static CapacityReport takePendingReport() {
        CapacityReport r = pendingReport;
        pendingReport = null;
        return r;
    }

    private final java.util.concurrent.atomic.AtomicBoolean capacityRunning =
            new java.util.concurrent.atomic.AtomicBoolean();

    /** 单个任务的占盘口径：已完成看文件长；在途/暂停 HTTP 看半截文件长、m3u8 加 .segs 目录总长 */
    private long diskUsage(DownloadTask task) {
        if (task.getFilePath() == null) return 0;
        File file = new File(task.getFilePath());
        long size = file.exists() ? file.length() : 0;
        if (task.isM3u8()) {
            File segs = new File(file.getParent(), file.getName() + ".segs");
            if (segs.isDirectory()) {
                File[] parts = segs.listFiles();
                if (parts != null) for (File part : parts) if (part.isFile()) size += part.length();
            }
        }
        return size;
    }

    /** 件 8：占用总量公开口（下载页副行「占用 X」用；含文件 stat，只许后台线程调） */
    public long totalUsageBytes() {
        return totalUsage();
    }

    /** 占用总量（按记录算，不全盘扫目录——孤儿文件不计入、不归它管） */
    private long totalUsage() {
        long sum = 0;
        for (DownloadTask task : tasks.values()) {
            if (task.getStatus() == DownloadTask.STATUS_COMPLETED
                    || task.getStatus() == DownloadTask.STATUS_DOWNLOADING
                    || task.getStatus() == DownloadTask.STATUS_PAUSED
                    || task.getStatus() == DownloadTask.STATUS_MERGING
                    || task.getStatus() == DownloadTask.STATUS_WAITING) {
                sum += diskUsage(task);
            }
        }
        return sum;
    }

    /**
     * 容量清理一轮（触发点：任务完成后/restore 对账后/上限调低时）：
     * 超限时循环挑 COMPLETED 里 lastActivity＝max(finishTime,lastPlayTime)
     * 最小的一条（最久没碰过）走正规 delete，直到不超限或无可清。
     * 永不清未完成任务；清完仍超限只出报告不硬删。删必报告、不静默。
     */
    public void enforceCapacity() {
        long limit = getMaxBytes(App.get());
        if (limit <= 0) return;
        if (!capacityRunning.compareAndSet(false, true)) return;
        com.fongmi.android.tv.utils.Task.execute(() -> {
            try {
                java.util.List<String> cleaned = new java.util.ArrayList<>();
                long freed = 0;
                while (totalUsage() > limit) {
                    DownloadTask victim = null;
                    long victimActivity = Long.MAX_VALUE;
                    for (DownloadTask task : tasks.values()) {
                        if (task.getStatus() != DownloadTask.STATUS_COMPLETED) continue;
                        if (task.getFilePath() == null || !new File(task.getFilePath()).exists()) continue;
                        long activity = Math.max(task.getFinishTime(), task.getLastPlayTime());
                        if (activity < victimActivity) {
                            victimActivity = activity;
                            victim = task;
                        }
                    }
                    if (victim == null) break;
                    long size = diskUsage(victim);
                    cleaned.add(victim.getName());
                    freed += size;
                    delete(victim.getId());
                }
                if (!cleaned.isEmpty()) {
                    pendingReport = new CapacityReport(cleaned, freed, limit);
                    presentCapacityReport();
                }
            } finally {
                capacityRunning.set(false);
            }
        });
    }

    /** 清理报告呈现：DownloadActivity 在前台时由它 refresh 时弹页内弹窗；
     * 不在前台挂一条通知（点开进下载页），清单留置到下次进页再弹。 */
    private void presentCapacityReport() {
        if (DownloadActivityForeground.isForeground()) return;
        App.post(() -> {
            try {
                CapacityReport report = pendingReport;
                if (report == null) return;
                android.content.Intent intent = new android.content.Intent(App.get(),
                        com.fongmi.android.tv.ui.activity.DownloadActivity.class);
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
                android.app.PendingIntent pending = android.app.PendingIntent.getActivity(App.get(), 2, intent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
                android.app.Notification notification = new androidx.core.app.NotificationCompat.Builder(App.get(), Notify.DEFAULT)
                        .setSmallIcon(com.fongmi.android.tv.R.drawable.ic_notification)
                        .setContentTitle("下载容量自动清理")
                        .setContentText("已按容量上限自动清理 " + report.names.size() + " 个最久未看的下载，共 "
                                + DownloadTask.formatSize(report.freedBytes))
                        .setContentIntent(pending)
                        .setAutoCancel(true)
                        .build();
                androidx.core.app.NotificationManagerCompat.from(App.get()).notify(Notify.ID + 4, notification);
            } catch (Exception ignored) {
                // 通知发不出不拦清理本身；清单仍留置，进下载页时照样弹
            }
        });
    }

    /** DownloadActivity 前后台标记（件 6 报告呈现分流用，Activity 生命周期里维护） */
    public static class DownloadActivityForeground {
        private static volatile boolean foreground;

        public static void setForeground(boolean value) {
            foreground = value;
        }

        public static boolean isForeground() {
            return foreground;
        }
    }

    /** 任务最近播放时刻回写（件 6 容量口径的 lastActivity 一半） */
    /** 按落盘路径查任务（标题回读/播放时刻回写共用同一口） */
    public DownloadTask findByPath(String filePath) {
        if (filePath == null) return null;
        for (DownloadTask task : tasks.values()) {
            if (filePath.equals(task.getFilePath())) return task;
        }
        return null;
    }

    public void touchPlayed(String filePath) {
        DownloadTask task = findByPath(filePath);
        if (task == null) return;
        task.setLastPlayTime(System.currentTimeMillis());
        persist(task);
    }

    private static final String KEY_DIR_PATH = "xingchen.download_dir_path";
    private static final String KEY_DIR_URI = "xingchen.download_dir_uri";
    /** 历次用过的自选根清单（DL-H3-3）：换根后旧根里的无记录存量仍要进收编扫描集，靠这份清单找回 */
    private static final String KEY_USED_ROOTS = "xingchen.download_dir_used_roots";

    /** 内置默认根：app 私有外部目录 XingChen 子目录 */
    private static File defaultDownloadDir(Context context) {
        return new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "XingChen");
    }

    /** 他亲手选定的下载根（绝对路径，未选过为空串） */
    public static String getCustomDirPath(Context context) {
        String path = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DIR_PATH, "");
        return path == null ? "" : path;
    }

    /** 他选目录时的 SAF tree uri（只作下次选目录的初始位置与留底，落盘不走它） */
    public static String getCustomDirUri(Context context) {
        String uri = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DIR_URI, "");
        return uri == null ? "" : uri;
    }

    public static void setCustomDir(Context context, String path, String uri) {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // 换根留痕（DL-H3-3）：旧根与新根都记进用过的根清单，收编扫描集才找得回旧根的无记录存量
        java.util.Set<String> used = new java.util.HashSet<>(prefs.getStringSet(KEY_USED_ROOTS, java.util.Collections.emptySet()));
        String old = getCustomDirPath(context);
        if (!old.isEmpty()) used.add(old);
        if (path != null && !path.isEmpty()) used.add(path);
        prefs.edit()
                .putString(KEY_DIR_PATH, path == null ? "" : path)
                .putString(KEY_DIR_URI, uri == null ? "" : uri)
                .putStringSet(KEY_USED_ROOTS, used)
                .apply();
    }

    /** 历次用过的自选根（收编扫描集用，DL-H3-3） */
    public static java.util.Set<String> getUsedCustomRoots(Context context) {
        java.util.Set<String> used = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_USED_ROOTS, java.util.Collections.emptySet());
        return used == null ? java.util.Collections.emptySet() : used;
    }

    /**
     * SAF tree uri → 绝对路径（下载内核全是路径制，选目录只认能落成真实
     * 路径的位置）：只认系统外部存储提供方——primary 卷映射内部存储根，
     * 其余卷（SD 卡）映射 /storage/&lt;卷id&gt;；别的提供方（云盘/下载
     * 提供方等）没有稳定真实路径，返回 null，由界面提示换位置。
     */
    public static String pathFromTreeUri(Uri uri) {
        if (uri == null) return null;
        if (!"com.android.externalstorage.documents".equals(uri.getAuthority())) return null;
        String docId;
        try {
            docId = DocumentsContract.getTreeDocumentId(uri);
        } catch (Exception e) {
            return null;
        }
        if (docId == null) return null;
        int sep = docId.indexOf(':');
        if (sep < 0) return null;
        String volume = docId.substring(0, sep);
        String rel = docId.substring(sep + 1);
        File base = "primary".equalsIgnoreCase(volume)
                ? Environment.getExternalStorageDirectory()
                : new File("/storage/" + volume);
        return rel == null || rel.isEmpty() ? base.getAbsolutePath()
                : new File(base, rel).getAbsolutePath();
    }

    /** 可写探针：建目录＋写一个探针文件再删掉，全过才算这个目录真能落盘（须在后台线程调） */
    public static boolean probeWritable(File dir) {
        if (dir == null) return false;
        try {
            if (!dir.exists() && !dir.mkdirs()) return false;
            if (!dir.isDirectory()) return false;
            File probe = new File(dir, ".xingchen_probe");
            java.io.FileOutputStream out = new java.io.FileOutputStream(probe);
            try {
                out.write(0);
                out.flush();
            } finally {
                out.close();
            }
            if (probe.exists() && !probe.delete()) probe.deleteOnExit();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 下载目录唯一真源（件 7＋换目录）：他亲手选定的根优先；没选过走
     * 内置默认（app 私有外部目录 XingChen 子目录，旧版 filesDir/Download
     * 仍在用时用旧的）。已下载与进行中任务的路径都在记录里存绝对路径，
     * 换根只影响新任务落点，不动存量、不搬家。本方法即实际落盘口径，
     * 展示也调它。
     */
    public static File getDownloadDir(Context context) {
        String custom = getCustomDirPath(context);
        if (!custom.isEmpty()) return new File(custom);
        File dir = defaultDownloadDir(context);
        // 兼容旧版：filesDir/Download
        File legacy = new File(context.getFilesDir(), "Download");
        if (legacy.exists() && !dir.exists()) return legacy;
        return dir;
    }

    /** 件 7：真实落盘路径（下载页目录卡展示唯一口径） */
    public static String getDownloadDirPath(Context context) {
        return getDownloadDir(context).getAbsolutePath();
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
