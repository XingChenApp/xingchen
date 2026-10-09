package com.fongmi.android.tv.utils.download;

import com.fongmi.android.tv.App;
import com.github.catvod.net.OkHttp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Request;
import okhttp3.Response;

/**
 * m3u8 下载器：解析播放列表，分片并发下载，按顺序合并
 */
public class M3u8Downloader {

    public interface Listener {
        void onProgress(int downloadedSegments, int totalSegments);
        void onMerging();
        void onComplete(File file);
        void onError(String msg);
    }

    private final String m3u8Url;
    private final File targetFile;
    private final Map<String, String> headers;
    private final int threadCount;
    private final Listener listener;
    private volatile boolean canceled;
    private ExecutorService executor;

    public M3u8Downloader(String m3u8Url, File targetFile, Map<String, String> headers, int threadCount, Listener listener) {
        this.m3u8Url = m3u8Url;
        this.targetFile = targetFile;
        this.headers = headers != null ? headers : new HashMap<>();
        this.threadCount = Math.max(1, Math.min(32, threadCount));
        this.listener = listener;
    }

    public void cancel() {
        canceled = true;
        if (executor != null) executor.shutdownNow();
    }

    public void start() {
        new Thread(this::doDownload).start();
    }

    private void doDownload() {
        try {
            // 1. 下载并解析 m3u8
            String content = fetchText(m3u8Url);
            List<String> segments = parseSegments(content, m3u8Url);
            if (segments.isEmpty()) {
                throw new IOException("m3u8 无分片");
            }
            // 2. 并发下载分片
            File tmpDir = new File(targetFile.getParent(), targetFile.getName() + ".segs");
            if (!tmpDir.exists()) tmpDir.mkdirs();
            int total = segments.size();
            AtomicInteger done = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(total);
            List<String> errors = new ArrayList<>();
            executor = Executors.newFixedThreadPool(threadCount);
            for (int i = 0; i < total; i++) {
                final int index = i;
                final String segUrl = segments.get(i);
                executor.submit(() -> {
                    try {
                        File segFile = new File(tmpDir, String.format("%05d.ts", index));
                        if (!segFile.exists() || segFile.length() == 0) {
                            downloadSegment(segUrl, segFile);
                        }
                        int d = done.incrementAndGet();
                        if (listener != null) App.post(() -> listener.onProgress(d, total));
                    } catch (Exception e) {
                        synchronized (errors) {
                            if (errors.isEmpty()) errors.add(e.getMessage());
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await(24, TimeUnit.HOURS);
            executor.shutdown();
            if (canceled) return;
            if (!errors.isEmpty()) throw new IOException(errors.get(0));
            // 3. 按顺序合并
            if (listener != null) App.post(() -> listener.onMerging());
            mergeSegments(tmpDir, total, targetFile);
            // 4. 清理分片
            deleteDir(tmpDir);
            if (!canceled && listener != null) App.post(() -> listener.onComplete(targetFile));
        } catch (Exception e) {
            if (!canceled && listener != null) {
                final String msg = e.getMessage();
                App.post(() -> listener.onError(msg));
            }
        }
    }

    private String fetchText(String url) throws IOException {
        Request.Builder builder = new Request.Builder().url(url).get();
        for (Map.Entry<String, String> e : headers.entrySet()) builder.addHeader(e.getKey(), e.getValue());
        try (Response res = OkHttp.client().newCall(builder.build()).execute()) {
            if (!res.isSuccessful() || res.body() == null) throw new IOException("HTTP " + res.code());
            return res.body().string();
        }
    }

    private List<String> parseSegments(String content, String baseUrl) throws Exception {
        List<String> segments = new ArrayList<>();
        URI base = new URI(baseUrl);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.ByteArrayInputStream(content.getBytes("UTF-8"))))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                // 相对 URL 转绝对
                String abs = base.resolve(line).toString();
                segments.add(abs);
            }
        }
        return segments;
    }

    private void downloadSegment(String url, File file) throws IOException {
        // 断点续传：分片已存在则跳过
        Request.Builder builder = new Request.Builder().url(url).get();
        for (Map.Entry<String, String> e : headers.entrySet()) builder.addHeader(e.getKey(), e.getValue());
        try (Response res = OkHttp.client().newCall(builder.build()).execute()) {
            if (!res.isSuccessful() || res.body() == null) throw new IOException("HTTP " + res.code());
            try (InputStream is = res.body().byteStream();
                 FileOutputStream fos = new FileOutputStream(file)) {
                byte[] buf = new byte[32768];
                int len;
                while ((len = is.read(buf)) != -1) {
                    if (canceled) return;
                    fos.write(buf, 0, len);
                }
            }
        }
    }

    private void mergeSegments(File tmpDir, int total, File target) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(target)) {
            byte[] buf = new byte[65536];
            for (int i = 0; i < total; i++) {
                if (canceled) return;
                File seg = new File(tmpDir, String.format("%05d.ts", i));
                try (FileInputStream fis = new FileInputStream(seg)) {
                    int len;
                    while ((len = fis.read(buf)) != -1) {
                        fos.write(buf, 0, len);
                    }
                }
            }
        }
    }

    private void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) f.delete();
        }
        dir.delete();
    }
}
