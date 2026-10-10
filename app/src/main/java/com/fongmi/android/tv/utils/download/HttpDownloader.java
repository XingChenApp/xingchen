package com.fongmi.android.tv.utils.download;

import com.fongmi.android.tv.App;
import com.github.catvod.net.OkHttp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
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
    private ExecutorService executor;

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

    public void start() {
        new Thread(this::doDownload).start();
    }

    private void doDownload() {
        try {
            // 1. 获取文件长度，检查是否支持 Range
            long totalLength = getContentLength();
            boolean supportRange = checkRangeSupport();
            if (totalLength <= 0 || !supportRange || threadCount == 1) {
                // 不支持分块，单线程下载
                singleThreadDownload(totalLength);
                return;
            }
            // 2. 多线程分块下载
            multiThreadDownload(totalLength);
        } catch (Exception e) {
            if (!canceled && listener != null) {
                App.post(() -> listener.onError(e.getMessage()));
            }
        }
    }

    private long getContentLength() throws IOException {
        Request.Builder builder = new Request.Builder().url(url).head();
        for (Map.Entry<String, String> e : headers.entrySet()) builder.addHeader(e.getKey(), e.getValue());
        try (Response res = OkHttp.client().newCall(builder.build()).execute()) {
            String len = res.header("Content-Length");
            if (len != null) return Long.parseLong(len);
        } catch (Exception ignored) {}
        // HEAD 失败时用 GET 的 Range 0-0 探测
        Map<String, String> h = new HashMap<>(headers);
        h.put("Range", "bytes=0-0");
        Request.Builder b2 = new Request.Builder().url(url).get();
        for (Map.Entry<String, String> e : h.entrySet()) b2.addHeader(e.getKey(), e.getValue());
        try (Response res = OkHttp.client().newCall(b2.build()).execute()) {
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
            return res.code() == 206;
        }
    }

    private void singleThreadDownload(long totalLength) throws IOException {
        // 断点续传：已下载部分继续
        long downloaded = 0;
        if (targetFile.exists()) downloaded = targetFile.length();
        Map<String, String> h = new HashMap<>(headers);
        if (downloaded > 0 && totalLength > downloaded) {
            h.put("Range", "bytes=" + downloaded + "-");
        } else {
            downloaded = 0;
            if (targetFile.exists()) targetFile.delete();
        }
        Request.Builder builder = new Request.Builder().url(url).get();
        for (Map.Entry<String, String> e : h.entrySet()) builder.addHeader(e.getKey(), e.getValue());
        try (Response res = OkHttp.client().newCall(builder.build()).execute()) {
            if (!res.isSuccessful() && res.code() != 206) throw new IOException("HTTP " + res.code());
            if (res.body() == null) throw new IOException("empty body");
            try (InputStream is = res.body().byteStream();
                 RandomAccessFile raf = new RandomAccessFile(targetFile, "rw")) {
                raf.seek(downloaded);
                byte[] buf = new byte[32768];
                int len;
                long total = downloaded;
                while ((len = is.read(buf)) != -1) {
                    if (canceled) return;
                    raf.write(buf, 0, len);
                    total += len;
                    final long d = total;
                    final long t = totalLength > 0 ? totalLength : -1;
                    if (listener != null) App.post(() -> { if (!canceled) listener.onProgress(d, t); });
                }
            }
            if (!canceled && listener != null) {
                App.post(() -> listener.onComplete(targetFile));
            }
        }
    }

    private void multiThreadDownload(long totalLength) throws IOException {
        // 预分配文件
        try (RandomAccessFile raf = new RandomAccessFile(targetFile, "rw")) {
            raf.setLength(totalLength);
        }
        // 断点续传：读取已完成的分块记录
        File progressFile = new File(targetFile.getAbsolutePath() + ".progress");
        boolean[] blockDone = new boolean[threadCount];
        // 简化：暂不支持跨进程断点，只支持本次会话内取消后重下
        // 实际断点续传靠单线程模式的 Range 续传；多线程模式每次重新分块
        long blockSize = totalLength / threadCount;
        AtomicLong totalDownloaded = new AtomicLong(0);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<String> errors = new ArrayList<>();
        executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            final long start = index * blockSize;
            final long end = (index == threadCount - 1) ? totalLength - 1 : (index + 1) * blockSize - 1;
            executor.submit(() -> {
                try {
                    downloadBlock(start, end, totalDownloaded, totalLength);
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
        if (progressFile.exists()) progressFile.delete();
        if (listener != null) App.post(() -> listener.onComplete(targetFile));
    }

    private void downloadBlock(long start, long end, AtomicLong totalDownloaded, long totalLength) throws IOException {
        Map<String, String> h = new HashMap<>(headers);
        h.put("Range", "bytes=" + start + "-" + end);
        Request.Builder builder = new Request.Builder().url(url).get();
        for (Map.Entry<String, String> e : h.entrySet()) builder.addHeader(e.getKey(), e.getValue());
        try (Response res = OkHttp.client().newCall(builder.build()).execute()) {
            if (res.code() != 206) throw new IOException("Range not supported, HTTP " + res.code());
            if (res.body() == null) throw new IOException("empty body");
            try (InputStream is = res.body().byteStream();
                 RandomAccessFile raf = new RandomAccessFile(targetFile, "rw")) {
                raf.seek(start);
                byte[] buf = new byte[32768];
                int len;
                while ((len = is.read(buf)) != -1) {
                    if (canceled) return;
                    raf.write(buf, 0, len);
                    long d = totalDownloaded.addAndGet(len);
                    if (listener != null) App.post(() -> { if (!canceled) listener.onProgress(d, totalLength); });
                }
            }
        }
    }
}
