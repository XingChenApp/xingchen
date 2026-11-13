package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.App;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.google.common.net.HttpHeaders;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Future;

import okhttp3.Response;

public class Download {

    private final File file;
    private final String url;
    private Callback callback;
    private Future<?> future;
    private String tag;
    private volatile boolean canceled;
    /** 仅 jar 下载（createForJar）且代理改写过地址时有值：代理路下载失败后用它直连再下一次。 */
    private String fallbackUrl;

    public static Download create(String url, File file) {
        return new Download(url, file);
    }

    /** jar 下载专用入口：GitHub 地址默认套代理（App 更新/环境包等其它链不受影响）。 */
    public static Download createForJar(String url, File file) {
        String proxied = GithubProxy.apply(url);
        Download download = new Download(proxied, file);
        if (!proxied.equals(url)) download.fallbackUrl = url;
        return download;
    }

    public Download(String url, File file) {
        this.tag = url;
        this.url = url;
        this.file = file;
    }

    public Download tag(String tag) {
        this.tag = tag;
        return this;
    }

    public File get() {
        try {
            doInBackground();
            return file;
        } catch (RuntimeException e) {
            if (fallbackUrl == null) throw e;
            // 代理前缀路失败：用原 URL 直连再下一次（直连实例不再带 fallback，不会递归重试）。
            SpiderDebug.log("jar-download", "proxy download failed, retry direct url=%s error=%s", fallbackUrl, e.getMessage());
            return new Download(fallbackUrl, file).get();
        }
    }

    public void start(Callback callback) {
        this.callback = callback;
        this.canceled = false;
        future = Task.submit(this::doInBackground);
    }

    public Download cancel() {
        canceled = true;
        if (future != null) future.cancel(true);
        OkHttp.cancel(tag);
        Path.clear(file);
        future = null;
        return this;
    }

    private void doInBackground() {
        try (Response res = OkHttp.newCall(url, tag).execute()) {
            if (!res.isSuccessful()) throw new IOException("Download failed: HTTP " + res.code());
            if (res.body() == null) throw new IOException("Download failed: empty response");
            boolean completed = download(res.body().byteStream(), getLength(res));
            if (!completed || canceled) {
                Path.clear(file);
                return;
            }
            if (callback != null) App.post(() -> {
                if (!canceled) callback.success(file);
            });
        } catch (Exception e) {
            Path.clear(file);
            if (canceled || isCanceled(e)) return;
            if (callback != null) App.post(() -> callback.error(e.getMessage()));
            else throw new RuntimeException(e.getMessage(), e);
        }
    }

    private boolean download(InputStream is, long length) throws IOException {
        try (BufferedInputStream input = new BufferedInputStream(is); FileOutputStream os = new FileOutputStream(Path.create(file))) {
            byte[] buffer = new byte[16384];
            int readBytes;
            int lastProgress = -1;
            long totalBytes = 0;
            long startTime = System.currentTimeMillis();
            long lastNotifyTime = startTime;
            long lastNotifyBytes = 0;
            if (callback != null) App.post(() -> callback.progress(length > 0 ? 0 : -1, 0, length, 0, 0));
            while ((readBytes = input.read(buffer)) != -1) {
                if (canceled || Thread.currentThread().isInterrupted()) return false;
                totalBytes += readBytes;
                os.write(buffer, 0, readBytes);
                if (callback == null) continue;
                long now = System.currentTimeMillis();
                int progress = length > 0 ? (int) (totalBytes * 100.0 / length) : -1;
                boolean shouldNotify = progress != lastProgress || now - lastNotifyTime >= 1000;
                if (!shouldNotify) continue;
                long deltaTime = Math.max(1, now - lastNotifyTime);
                long speed = (totalBytes - lastNotifyBytes) * 1000 / deltaTime;
                long elapsed = now - startTime;
                lastProgress = progress;
                lastNotifyTime = now;
                lastNotifyBytes = totalBytes;
                long bytes = totalBytes;
                long total = length;
                App.post(() -> callback.progress(progress, bytes, total, speed, elapsed));
            }
            if (length > 0 && totalBytes != length) throw new IOException("Download incomplete");
            return !canceled;
        }
    }

    private boolean isCanceled(Exception e) {
        String message = e.getMessage();
        return "Canceled".equals(message) || "Socket closed".equals(message);
    }

    private long getLength(Response res) {
        try {
            String header = res.header(HttpHeaders.CONTENT_LENGTH);
            return header != null ? Long.parseLong(header) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    public interface Callback {

        void progress(int progress);

        default void progress(int progress, long bytes, long total, long speed, long elapsed) {
            progress(progress);
        }

        void error(String msg);

        void success(File file);
    }
}
