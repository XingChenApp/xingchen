package com.fongmi.android.tv.utils.download;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.update.UpdateIdentity;
import com.fongmi.android.tv.utils.FileUtil;
import com.github.catvod.net.OkHttp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Request;
import okhttp3.Response;

/**
 * HTTP 多线程下载器，支持 Range 分块 + 断点续传
 */
public class HttpDownloader {

    public interface Listener {
        void onProgress(long downloaded, long total);
        void onComplete(File file);
        void onError(String msg);
    }

    private final String url;
    private final File targetFile;
    private final Map<String, String> headers;
    private final int threadCount;
    private final Listener listener;
    private volatile boolean canceled;
    /** 探测响应带回的内容校验头，组下载身份用（见 downloadIdentity）。 */
    private volatile String etag;
    private volatile String lastModified;
    private ExecutorService executor;
    // C-25: 工作线程收尾闩。doDownload 返回（各子路径的文件句柄已在各自
    // try-with-resources 里关掉）后才放下，DownloadManager 靠它确认旧线程
    // 真的停笔，才允许 resume 新建下载器写同一文件。
    private final CountDownLatch finishedLatch = new CountDownLatch(1);
    private volatile Thread workerThread;

    public HttpDownloader(String url, File targetFile, Map<String, String> headers, int threadCount, Listener listener) {
        this.url = url;
        this.targetFile = targetFile;
        this.headers = headers != null ? headers : new HashMap<>();
        this.threadCount = Math.max(1, Math.min(32, threadCount));
        this.listener = listener;
    }

    public void cancel() {
        canceled = true;
        if (executor != null) executor.shutdownNow();
        OkHttp.cancel(url);
    }

    /** C-25: 等工作线程收尾（含文件句柄关闭）；超时返回 false，不无限等。 */
    public boolean awaitFinished(long timeout, TimeUnit unit) throws InterruptedException {
        return finishedLatch.await(timeout, unit);
    }

    /** C-25: 工作线程是否已经收尾。 */
    public boolean isFinished() {
        return finishedLatch.getCount() == 0;
    }

    public void start() {
        Thread t = new Thread(() -> {
            try {
                doDownload();
            } finally {
                finishedLatch.countDown();
            }
        });
        workerThread = t;
        t.start();
    }

    /**
     * C-17: 落笔前空间预检。总长已知时按「最终文件大小 − 已在盘上的部分」
     * 算还需的新增空间（续传/预分配同文件不重复占），盘可用量查不到时不拦
     * （避免误杀），查到了且不够就抛中文错，走 doDownload 既有 onError 路，
     * 不许写到一半才炸。
     */
    private void ensureSpaceFor(long finalLength) throws IOException {
        if (finalLength <= 0) return;
        long existing = targetFile.exists() ? targetFile.length() : 0;
        long additional = finalLength - existing;
        if (additional <= 0) return;
        FileUtil.StorageSpace space = FileUtil.getStorageSpace(targetFile);
        if (!space.available()) return;
        if (space.availableBytes() < additional) {
            throw new IOException("存储空间不足：还需约 " + DownloadTask.formatSize(additional)
                    + "，当前可用仅 " + DownloadTask.formatSize(space.availableBytes())
                    + "，请清理空间后重试");
        }
    }

    private void doDownload() {
        try {
            // 1. 获取文件长度，检查是否支持 Range
            long totalLength = getContentLength();
            // C-17: 总长已知先预检空间，不足一开始就报错，不再往下探测/落笔
            ensureSpaceFor(totalLength);
            boolean supportRange = checkRangeSupport();
            // 本次内容的身份：两条下载路的续传都只认它，不认裸长度
            String identity = downloadIdentity(totalLength);
            if (totalLength <= 0 || !supportRange || threadCount == 1) {
                // 不支持分块，单线程下载
                singleThreadDownload(totalLength, identity);
                return;
            }
            // 2. 多线程分块下载
            multiThreadDownload(totalLength, identity);
        } catch (Exception e) {
            if (!canceled && listener != null) {
                App.post(() -> listener.onError(e.getMessage()));
            }
        }
    }

    private void captureValidators(Response res) {
        if (etag == null) etag = res.header("ETag");
        if (lastModified == null) lastModified = res.header("Last-Modified");
    }

    /**
     * 本次下载内容的身份串（与更新链 UpdateIdentity 同口径）：URL＋内容
     * 校验头（ETag 优先、Last-Modified 次之）＋总长，落目标文件旁车。
     * W7-02：身份串必须定长——UpdateIdentity.matches 只读 512 字节比对，
     * 把完整 URL 拼进身份串时，长 URL（签名直链）身份恒超 512、永远匹配
     * 不上、续传静默失效。故身份改走 sha256 哈希形态："dl:"＋十六进制
     * 摘要（3＋64＝67 字节，恒在 512 内）。本文件所有旁车/进度档的写读
     * 都只经本方法产出的 identity 变量（UpdateIdentity.write/matches、
     * writeProgress/readProgress 首行），改这一处即全口径齐。
     * 总长未知时无法确认在盘半截与本次内容的关系，返回 null＝不认旧半截；
     * 哈希算不出时同样返回 null（不认旧半截、走重下，不误认）。
     */
    private String downloadIdentity(long totalLength) {
        if (totalLength <= 0) return null;
        String validator = etag != null ? "etag:" + etag : lastModified != null ? "lm:" + lastModified : "none";
        String rawIdentity = url + "|" + validator + "|" + totalLength;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawIdentity.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashBytes.length * 2);
            for (byte hashByte : hashBytes) {
                hex.append(Character.forDigit((hashByte >> 4) & 0xF, 16));
                hex.append(Character.forDigit(hashByte & 0xF, 16));
            }
            return "dl:" + hex;
        } catch (Exception e) {
            return null;
        }
    }

    private long getContentLength() throws IOException {
        Request.Builder builder = new Request.Builder().url(url).head();
        for (Map.Entry<String, String> e : headers.entrySet()) builder.addHeader(e.getKey(), e.getValue());
        try (Response res = OkHttp.client().newCall(builder.build()).execute()) {
            captureValidators(res);
            String len = res.header("Content-Length");
            if (len != null) return Long.parseLong(len);
        } catch (Exception ignored) {}
        // HEAD 失败时用 GET 的 Range 0-0 探测
        Map<String, String> h = new HashMap<>(headers);
        h.put("Range", "bytes=0-0");
        Request.Builder b2 = new Request.Builder().url(url).get();
        for (Map.Entry<String, String> e : h.entrySet()) b2.addHeader(e.getKey(), e.getValue());
        try (Response res = OkHttp.client().newCall(b2.build()).execute()) {
            captureValidators(res);
            String cr = res.header("Content-Range");
            if (cr != null && cr.contains("/")) {
                return Long.parseLong(cr.substring(cr.lastIndexOf('/') + 1).trim());
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private boolean checkRangeSupport() throws IOException {
        Map<String, String> h = new HashMap<>(headers);
        h.put("Range", "bytes=0-0");
        Request.Builder builder = new Request.Builder().url(url).get();
        for (Map.Entry<String, String> e : h.entrySet()) builder.addHeader(e.getKey(), e.getValue());
        try (Response res = OkHttp.client().newCall(builder.build()).execute()) {
            captureValidators(res);
            return res.code() == 206;
        }
    }

    private void singleThreadDownload(long totalLength, String identity) throws IOException {
        // 断点续传先过身份门（与更新链同口径）：旁车对不上本次内容就不认
        // 这半截——只凭长度续传，同一路径换了内容来源时新内容会接在旧
        // 半截后面，最后只有长度核对，拼错的内容没人发现。身份缺失
        // （总长未知）同样不认；身份对得上但比总长还长的也不是正常半截。
        File progressFile = new File(targetFile.getAbsolutePath() + ".progress");
        long downloaded = 0;
        if (targetFile.exists() && targetFile.length() > 0) {
            if (identity != null && UpdateIdentity.matches(targetFile, identity)) {
                downloaded = targetFile.length();
                if (downloaded > totalLength) {
                    targetFile.delete();
                    downloaded = 0;
                }
            } else {
                targetFile.delete();
            }
        }
        if (identity != null && downloaded == totalLength && downloaded > 0
                && !isProgressComplete(progressFile, identity)) {
            // W7-01：长度已满但多线程进度未全完（或判不准）＝预分配零洞的
            // 半成品，不是成品：整档与旧进度一并作废、从 0 重下。不能留着
            // downloaded＝满长往下走——发不出 Range、全量响应会被接到满长
            // 文件尾巴、最后长度校验必炸；更不许当成品交付。
            targetFile.delete();
            if (progressFile.exists()) progressFile.delete();
            downloaded = 0;
        }
        if (identity != null) {
            if (downloaded == totalLength && isProgressComplete(progressFile, identity)) {
                // 身份对得上、长度已满、且多线程进度档不在或块全下完：才是
                // 本次内容的成品，不重复下载（与更新链整档命中同口径）；
                // 旁车使命结束，摘掉。
                UpdateIdentity.clearSidecar(targetFile);
                if (listener != null) App.post(() -> listener.onComplete(targetFile));
                return;
            }
            // 开下前先落旁车（与更新链 reconcileLocalFile 同口径）：中途
            // 崩溃/暂停留下的半截＋旁车，下次才能证明它属于本次内容。
            UpdateIdentity.write(targetFile, identity);
        }
        Map<String, String> h = new HashMap<>(headers);
        boolean sentRange = false;
        if (downloaded > 0 && totalLength > downloaded) {
            h.put("Range", "bytes=" + downloaded + "-");
            sentRange = true;
        }
        Request.Builder builder = new Request.Builder().url(url).get();
        for (Map.Entry<String, String> e : h.entrySet()) builder.addHeader(e.getKey(), e.getValue());
        try (Response res = OkHttp.client().newCall(builder.build()).execute()) {
            if (!res.isSuccessful() && res.code() != 206) throw new IOException("HTTP " + res.code());
            if (res.body() == null) throw new IOException("empty body");
            // C-22: 发了 Range 但服务器回 200 = 不支持续传、正在发全量，
            // 不能把全量接在半截文件后面，必须截断从 0 重写。
            boolean rangeIgnored = sentRange && res.code() == 200;
            long responseLength = -1;
            String cl = res.header("Content-Length");
            if (cl != null) {
                try {
                    responseLength = Long.parseLong(cl.trim());
                } catch (NumberFormatException ignored) {
                }
            }
            // C-17: 总长探测未知时用本次响应推最终大小，打开文件落笔前再预检一次；
            // 在 RandomAccessFile 打开/截断之前抛错，已下的半截文件不被破坏。
            long finalExpected;
            if (rangeIgnored) {
                finalExpected = responseLength > 0 ? responseLength : totalLength;
            } else if (totalLength > 0) {
                finalExpected = totalLength;
            } else if (responseLength > 0) {
                finalExpected = sentRange ? downloaded + responseLength : responseLength;
            } else {
                finalExpected = -1;
            }
            ensureSpaceFor(finalExpected);
            long total;
            try (InputStream is = res.body().byteStream();
                 RandomAccessFile raf = new RandomAccessFile(targetFile, "rw")) {
                if (rangeIgnored) {
                    raf.setLength(0);
                    raf.seek(0);
                    downloaded = 0;
                } else {
                    raf.seek(downloaded);
                }
                byte[] buf = new byte[32768];
                int len;
                total = downloaded;
                while ((len = is.read(buf)) != -1) {
                    if (canceled) return;
                    raf.write(buf, 0, len);
                    total += len;
                    final long d = total;
                    // 回 200 全量时总量以本次响应为准；206 续传时以探测到的总长为准
                    final long t = rangeIgnored
                            ? (responseLength > 0 ? responseLength : totalLength)
                            : (totalLength > 0 ? totalLength : (responseLength > 0 ? downloaded + responseLength : -1));
                    if (listener != null) App.post(() -> { if (!canceled) listener.onProgress(d, t); });
                }
            }
            if (canceled) return;
            // 写完核对最终长度：Range 被无视时期望 = 本次全量长度；
            // 续传 206 / 普通 200 时期望 = 已知总长（未知则用本次响应长度推导）。
            long expected = -1;
            if (rangeIgnored) {
                expected = responseLength > 0 ? responseLength : totalLength;
            } else if (totalLength > 0) {
                expected = totalLength;
            } else if (responseLength > 0) {
                expected = downloaded + responseLength;
                // 未发 Range 时 downloaded 已归零，上式即 responseLength
                if (!sentRange) expected = responseLength;
            }
            if (expected > 0) {
                long actual = targetFile.exists() ? targetFile.length() : -1;
                if (actual != expected) {
                    throw new IOException("下载长度校验失败: 期望 " + expected + " 字节, 实际 " + actual + " 字节");
                }
            }
            // 成品落定：身份旁车使命结束，摘掉（文件本体保留交付）
            if (identity != null) UpdateIdentity.clearSidecar(targetFile);
            if (listener != null) {
                App.post(() -> listener.onComplete(targetFile));
            }
        }
    }

    private void multiThreadDownload(long totalLength, String identity) throws IOException {
        // C-17: 预分配 setLength 前同样先预检空间，不足直接走 onError
        ensureSpaceFor(totalLength);
        File progressFile = new File(targetFile.getAbsolutePath() + ".progress");
        long blockSize = totalLength / threadCount;
        // 每块期望长度：末块 end 按 totalLength-1 收口，块长统一为 end-start+1，
        // 等价于 totalLength - start（非末块为 blockSize），不多算/漏算取模余数
        final long[] blockExpected = new long[threadCount];
        for (int i = 0; i < threadCount; i++) {
            long start = i * blockSize;
            long end = (i == threadCount - 1) ? totalLength - 1 : (i + 1) * blockSize - 1;
            blockExpected[i] = (end >= start) ? (end - start + 1) : 0;
        }
        // C-21: 每块实收字节数，latch 齐后逐块与期望块长核对
        final long[] blockReceived = new long[threadCount];
        final boolean[] blockDone = new boolean[threadCount];
        // 块长为 0 的空块（文件比线程数还小时出现）天然完成，不发请求
        for (int i = 0; i < threadCount; i++) {
            if (blockExpected[i] == 0) blockDone[i] = true;
        }
        // 断点续传先过身份门（与更新链同口径）：旁车＋进度档都对得上本次
        // 内容、且在盘文件正是预分配的总长时，才认进度档里已完成的块；
        // 任一对不上，旧半截与旧进度一律作废——只凭长度/进度认档，同一
        // 路径换了内容来源时会把新旧块拼成一个内容错误的文件。
        boolean[] saved = null;
        if (identity != null && UpdateIdentity.matches(targetFile, identity)
                && targetFile.exists() && targetFile.length() == totalLength) {
            saved = readProgress(progressFile, identity);
        }
        boolean resuming = saved != null;
        long resumedBytes = 0;
        if (resuming) {
            for (int i = 0; i < threadCount; i++) {
                if (saved[i]) blockDone[i] = true;
                if (blockDone[i]) {
                    blockReceived[i] = blockExpected[i];
                    resumedBytes += blockExpected[i];
                }
            }
        } else {
            if (progressFile.exists()) progressFile.delete();
            // 开下前先落旁车（与更新链同口径）：预分配文件＋进度档下次
            // 才能证明属于本次内容
            if (identity != null) UpdateIdentity.write(targetFile, identity);
            // 预分配文件
            try (RandomAccessFile raf = new RandomAccessFile(targetFile, "rw")) {
                raf.setLength(totalLength);
            }
            writeProgress(progressFile, identity, blockDone);
        }
        AtomicLong totalDownloaded = new AtomicLong(resumedBytes);
        List<Integer> pending = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) if (!blockDone[i]) pending.add(i);
        CountDownLatch latch = new CountDownLatch(pending.size());
        List<String> errors = new ArrayList<>();
        executor = Executors.newFixedThreadPool(threadCount);
        for (int pendingIndex : pending) {
            final int index = pendingIndex;
            final long start = index * blockSize;
            final long end = (index == threadCount - 1) ? totalLength - 1 : (index + 1) * blockSize - 1;
            executor.submit(() -> {
                try {
                    blockReceived[index] = downloadBlock(start, end, totalDownloaded, totalLength);
                    if (blockReceived[index] == blockExpected[index]) {
                        // 一块落定就把进度落盘：进程被杀/暂停后下次能接着下，
                        // 不再像旧实现那样每回全量重分配从零下
                        synchronized (blockDone) {
                            blockDone[index] = true;
                            writeProgress(progressFile, identity, blockDone);
                        }
                    }
                } catch (Exception e) {
                    synchronized (errors) {
                        if (errors.isEmpty()) errors.add(e.getMessage());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await(24, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor.shutdown();
        if (canceled) return;
        if (!errors.isEmpty()) {
            throw new IOException(errors.get(0));
        }
        // C-21: 逐块核对实收 == 块长；短响应留下的预分配零洞不许报成功
        for (int i = 0; i < threadCount; i++) {
            if (blockReceived[i] != blockExpected[i]) {
                throw new IOException("分块下载长度校验失败: 第 " + i + " 块期望 " + blockExpected[i]
                        + " 字节, 实际 " + blockReceived[i] + " 字节");
            }
        }
        long actualLength = targetFile.exists() ? targetFile.length() : -1;
        if (actualLength != totalLength) {
            throw new IOException("下载长度校验失败: 期望 " + totalLength + " 字节, 实际 " + actualLength + " 字节");
        }
        if (progressFile.exists()) progressFile.delete();
        // 成品落定：身份旁车使命结束，摘掉（文件本体保留交付）
        if (identity != null) UpdateIdentity.clearSidecar(targetFile);
        if (listener != null) App.post(() -> listener.onComplete(targetFile));
    }

    /**
     * W7-01：多线程进度档是否证明在盘文件块全下完（单线程路成品命中专用）。
     * 进度档不存在＝从未走过多线程预分配，长度满即可信，返回 true；存在时
     * 按本文件进度档格式判读（首行身份、次行逐块 0/1）：身份须与本次全等
     * （identity 为 null 时无从核对，按判不准处理）、标志行须非空且全 '1'
     * ——不按当前 threadCount 卡标志长度：留档时的分块数可与本次不同，
     * 原分块全 1 即原文件完整。任一判不准（格式坏/读不出/有 0）返回 false。
     */
    private boolean isProgressComplete(File progressFile, String identity) {
        if (!progressFile.exists()) return true;
        if (identity == null) return false;
        try (BufferedReader reader = new BufferedReader(new FileReader(progressFile))) {
            if (!identity.equals(reader.readLine())) return false;
            String flags = reader.readLine();
            if (flags == null || flags.isEmpty()) return false;
            for (int i = 0; i < flags.length(); i++) {
                if (flags.charAt(i) != '1') return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 读多线程进度档：首行身份、次行逐块 0/1。身份对不上/格式坏一律返回 null（当无进度）。 */
    private boolean[] readProgress(File progressFile, String identity) {
        if (identity == null || !progressFile.exists()) return null;
        try (BufferedReader reader = new BufferedReader(new FileReader(progressFile))) {
            if (!identity.equals(reader.readLine())) return null;
            String flags = reader.readLine();
            if (flags == null || flags.length() != threadCount) return null;
            boolean[] done = new boolean[threadCount];
            for (int i = 0; i < threadCount; i++) {
                char c = flags.charAt(i);
                if (c == '1') done[i] = true;
                else if (c != '0') return null;
            }
            return done;
        } catch (Exception e) {
            return null;
        }
    }

    /** 写多线程进度档（调用方负责串行化：首写在主下载线程，块完成时在 blockDone 锁内）。 */
    private void writeProgress(File progressFile, String identity, boolean[] done) {
        StringBuilder flags = new StringBuilder(done.length);
        for (boolean doneFlag : done) flags.append(doneFlag ? '1' : '0');
        try (FileOutputStream out = new FileOutputStream(progressFile)) {
            out.write(((identity == null ? "" : identity) + "\n" + flags + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // 进度写不下只是下次不能续传，不许影响本次下载
        }
    }

    /** @return 本块实际收到并写入的字节数（取消时返回已收部分，调用方在 canceled 时不做校验） */
    private long downloadBlock(long start, long end, AtomicLong totalDownloaded, long totalLength) throws IOException {
        Map<String, String> h = new HashMap<>(headers);
        h.put("Range", "bytes=" + start + "-" + end);
        Request.Builder builder = new Request.Builder().url(url).get();
        for (Map.Entry<String, String> e : h.entrySet()) builder.addHeader(e.getKey(), e.getValue());
        long received = 0;
        try (Response res = OkHttp.client().newCall(builder.build()).execute()) {
            if (res.code() != 206) throw new IOException("Range not supported, HTTP " + res.code());
            if (res.body() == null) throw new IOException("empty body");
            try (InputStream is = res.body().byteStream();
                 RandomAccessFile raf = new RandomAccessFile(targetFile, "rw")) {
                raf.seek(start);
                byte[] buf = new byte[32768];
                int len;
                while ((len = is.read(buf)) != -1) {
                    if (canceled) return received;
                    raf.write(buf, 0, len);
                    received += len;
                    long d = totalDownloaded.addAndGet(len);
                    if (listener != null) App.post(() -> { if (!canceled) listener.onProgress(d, totalLength); });
                }
            }
        }
        return received;
    }
}
