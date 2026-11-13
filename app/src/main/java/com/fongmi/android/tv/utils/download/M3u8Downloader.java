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

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

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
    // C-25: 工作线程收尾闩。doDownload 返回（分片/合并的文件流已在各自
    // try-with-resources 里关掉）后才放下，DownloadManager 靠它确认旧线程
    // 真的停笔，才允许 resume 新建下载器写同一文件/同一分片目录。
    private final CountDownLatch finishedLatch = new CountDownLatch(1);
    private volatile Thread workerThread;
    // C-23: AES-128 密钥缓存（按解析后的密钥绝对地址），并发分片共用
    private final Map<String, byte[]> keyCache = new HashMap<>();

    /** 单个分片：地址 + 生效的加密信息（无加密为 null）+ 序号（算默认 IV 用） */
    private static class SegmentInfo {
        final String url;
        final KeyInfo key;
        final long sequence;

        SegmentInfo(String url, KeyInfo key, long sequence) {
            this.url = url;
            this.key = key;
            this.sequence = sequence;
        }
    }

    /** #EXT-X-KEY 解析结果：method 已校验，uri 已按播放列表地址解析为绝对地址 */
    private static class KeyInfo {
        final String method;
        final String uri;
        final byte[] iv; // 显式 IV，可为 null（此时按分片序号生成）

        KeyInfo(String method, String uri, byte[] iv) {
            this.method = method;
            this.uri = uri;
            this.iv = iv;
        }
    }

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

    /** C-25: 等工作线程收尾（含文件流关闭）；超时返回 false，不无限等。 */
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

    private void doDownload() {
        try {
            // 1. 下载并解析 m3u8
            String content = fetchText(m3u8Url);
            String playlistUrl = m3u8Url;
            // master 列表：选 BANDWIDTH 最高的变体
            if (isMasterPlaylist(content)) {
                String variantUrl = selectBestVariant(content, m3u8Url);
                if (variantUrl == null) {
                    throw new IOException("m3u8 无可用变体");
                }
                playlistUrl = variantUrl;
                content = fetchText(variantUrl);
            }
            List<SegmentInfo> segments = parsePlaylist(content, playlistUrl);
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
                final SegmentInfo seg = segments.get(i);
                executor.submit(() -> {
                    try {
                        if (canceled) return;
                        File segFile = new File(tmpDir, String.format("%05d.ts", index));
                        // C-23: 跳过只认正式分片名且非空；临时文件永不算完成。
                        // 旧正式分片（本修复前直写落盘的）无法区分是否半截，
                        // 沿用原口径认其完成（见交回报告说明），新下载一律走临时+改名。
                        if (!segFile.exists() || segFile.length() == 0) {
                            downloadSegmentToFinal(seg, segFile);
                        }
                        if (canceled) return;
                        int d = done.incrementAndGet();
                        if (listener != null) App.post(() -> { if (!canceled) listener.onProgress(d, total); });
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

    /**
     * 解析媒体播放列表：分片地址、#EXT-X-MEDIA-SEQUENCE 起始序号、
     * 以及对后续分片生效的 #EXT-X-KEY（C-23）。
     * METHOD 只接受 NONE / AES-128，其它（如 SAMPLE-AES）在解析期直接抛错，
     * 保证任务以可见原因失败，绝不静默产出不可播的合并文件。
     */
    private List<SegmentInfo> parsePlaylist(String content, String baseUrl) throws Exception {
        List<SegmentInfo> segments = new ArrayList<>();
        URI base = new URI(baseUrl);
        long mediaSequence = 0;
        KeyInfo currentKey = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.ByteArrayInputStream(content.getBytes("UTF-8"))))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                    try {
                        mediaSequence = Long.parseLong(line.substring("#EXT-X-MEDIA-SEQUENCE:".length()).trim());
                    } catch (NumberFormatException ignored) {
                    }
                    continue;
                }
                if (line.startsWith("#EXT-X-KEY:")) {
                    currentKey = parseKey(line.substring("#EXT-X-KEY:".length()), base);
                    continue;
                }
                if (line.startsWith("#")) continue;
                // 相对 URL 转绝对
                String abs = base.resolve(line).toString();
                long seq = mediaSequence + segments.size();
                segments.add(new SegmentInfo(abs, currentKey, seq));
            }
        }
        return segments;
    }

    /** 解析一条 #EXT-X-KEY 属性串；NONE 返回 null，其它不支持的 METHOD 抛 IOException */
    private KeyInfo parseKey(String attrs, URI base) throws IOException {
        Map<String, String> map = parseAttributes(attrs);
        String method = map.get("METHOD");
        if (method == null || method.isEmpty()) throw new IOException("m3u8 EXT-X-KEY 缺少 METHOD");
        if ("NONE".equals(method)) return null;
        if (!"AES-128".equals(method)) {
            throw new IOException("不支持的 HLS 加密方式: " + method);
        }
        String uri = map.get("URI");
        if (uri == null || uri.isEmpty()) throw new IOException("m3u8 EXT-X-KEY 缺少 URI");
        String absUri = base.resolve(uri).toString();
        byte[] iv = null;
        String ivText = map.get("IV");
        if (ivText != null && !ivText.isEmpty()) iv = parseIv(ivText);
        return new KeyInfo(method, absUri, iv);
    }

    /** 解析 KEY=VALUE,KEY="带逗号的值" 形式的 HLS 属性串 */
    private static Map<String, String> parseAttributes(String attrs) {
        Map<String, String> map = new HashMap<>();
        int i = 0;
        int n = attrs.length();
        while (i < n) {
            int eq = attrs.indexOf('=', i);
            if (eq < 0) break;
            String key = attrs.substring(i, eq).trim();
            String value;
            int vStart = eq + 1;
            if (vStart < n && attrs.charAt(vStart) == '"') {
                int vEnd = attrs.indexOf('"', vStart + 1);
                if (vEnd < 0) vEnd = n;
                value = attrs.substring(vStart + 1, vEnd);
                i = vEnd + 1;
                if (i < n && attrs.charAt(i) == ',') i++;
            } else {
                int vEnd = attrs.indexOf(',', vStart);
                if (vEnd < 0) vEnd = n;
                value = attrs.substring(vStart, vEnd).trim();
                i = vEnd + 1;
            }
            if (!key.isEmpty()) map.put(key, value);
        }
        return map;
    }

    /** IV 十六进制（可带 0x 前缀）转 16 字节；不足左补 0，超长取低 16 字节 */
    private static byte[] parseIv(String text) throws IOException {
        String hex = text.trim();
        if (hex.startsWith("0x") || hex.startsWith("0X")) hex = hex.substring(2);
        if (hex.length() % 2 == 1) hex = "0" + hex;
        byte[] raw = new byte[hex.length() / 2];
        try {
            for (int i = 0; i < raw.length; i++) {
                raw[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
        } catch (NumberFormatException e) {
            throw new IOException("m3u8 EXT-X-KEY 的 IV 格式错误: " + text);
        }
        byte[] iv = new byte[16];
        int copy = Math.min(16, raw.length);
        System.arraycopy(raw, raw.length - copy, iv, 16 - copy, copy);
        return iv;
    }

    /** 无显式 IV 时按 HLS 规范用分片序号生成 16 字节大端 IV */
    private static byte[] ivForSequence(long sequence) {
        byte[] iv = new byte[16];
        for (int i = 15; i >= 8; i--) {
            iv[i] = (byte) (sequence & 0xFF);
            sequence >>>= 8;
        }
        return iv;
    }

    /** 下载 AES-128 密钥（16 字节），带缓存；失败抛 IOException 使任务失败 */
    private byte[] fetchKey(String uri) throws IOException {
        synchronized (keyCache) {
            byte[] cached = keyCache.get(uri);
            if (cached != null) return cached;
        }
        Request.Builder builder = new Request.Builder().url(uri).get();
        for (Map.Entry<String, String> e : headers.entrySet()) builder.addHeader(e.getKey(), e.getValue());
        byte[] key;
        try (Response res = OkHttp.client().newCall(builder.build()).execute()) {
            if (!res.isSuccessful() || res.body() == null) {
                throw new IOException("下载 HLS 密钥失败: HTTP " + res.code());
            }
            key = res.body().bytes();
        }
        if (key == null || key.length != 16) {
            throw new IOException("HLS 密钥长度错误: 期望 16 字节, 实际 " + (key == null ? 0 : key.length) + " 字节");
        }
        synchronized (keyCache) {
            keyCache.put(uri, key);
        }
        return key;
    }

    /**
     * C-23 临时文件 + 完成才改名：先下到 .tmp（加密片再解密到 .dec.tmp），
     * 流完整读完且无异常（解密同理）才 rename 成正式分片名。
     * 取消/失败时临时文件不改名、随即删除，不会被下次运行当完成片跳过。
     */
    private void downloadSegmentToFinal(SegmentInfo seg, File segFile) throws Exception {
        File tmpFile = new File(segFile.getParent(), segFile.getName() + ".tmp");
        if (tmpFile.exists()) tmpFile.delete();
        downloadSegment(seg.url, tmpFile);
        if (canceled) {
            tmpFile.delete();
            return;
        }
        if (!tmpFile.exists() || tmpFile.length() == 0) {
            throw new IOException("分片下载为空: " + seg.url);
        }
        File readyFile = tmpFile;
        if (seg.key != null) {
            byte[] key = fetchKey(seg.key.uri);
            byte[] iv = seg.key.iv != null ? seg.key.iv : ivForSequence(seg.sequence);
            File decFile = new File(segFile.getParent(), segFile.getName() + ".dec.tmp");
            if (decFile.exists()) decFile.delete();
            try {
                decryptSegment(tmpFile, decFile, key, iv);
            } catch (Exception e) {
                decFile.delete();
                throw new IOException("分片解密失败: " + e.getMessage());
            }
            tmpFile.delete();
            if (canceled) {
                decFile.delete();
                return;
            }
            readyFile = decFile;
        }
        if (!readyFile.renameTo(segFile)) {
            // rename 跨设备/被占用时回退为拷贝后删除，仍以正式名为完成标记
            copyFile(readyFile, segFile);
            readyFile.delete();
        }
        if (!segFile.exists() || segFile.length() == 0) {
            throw new IOException("分片落盘失败: " + seg.url);
        }
    }

    /** AES/CBC/PKCS5Padding 流式解密一个分片；手动 update/doFinal 以便填充错误能抛出来（CipherInputStream 会吞 BadPadding） */
    private void decryptSegment(File in, File out, byte[] key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        try (InputStream fis = new FileInputStream(in);
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[32768];
            int len;
            while ((len = fis.read(buf)) != -1) {
                if (canceled) return;
                byte[] plain = cipher.update(buf, 0, len);
                if (plain != null && plain.length > 0) fos.write(plain);
            }
            byte[] tail = cipher.doFinal();
            if (tail != null && tail.length > 0) fos.write(tail);
        }
    }

    private void copyFile(File src, File dst) throws IOException {
        try (FileInputStream fis = new FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            int len;
            while ((len = fis.read(buf)) != -1) fos.write(buf, 0, len);
        }
    }

    /** 是否为 master 播放列表（含多码率变体） */
    private boolean isMasterPlaylist(String content) {
        return content != null && content.contains("#EXT-X-STREAM-INF");
    }

    /** 从 master 列表中选 BANDWIDTH 最高的变体 URL，失败返回第一条 */
    private String selectBestVariant(String content, String baseUrl) throws Exception {
        URI base = new URI(baseUrl);
        String bestUrl = null;
        String firstUrl = null;
        long bestBandwidth = -1;
        long bandwidth = -1;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.ByteArrayInputStream(content.getBytes("UTF-8"))))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    bandwidth = parseBandwidth(line);
                } else if (!line.isEmpty() && !line.startsWith("#")) {
                    String abs = base.resolve(line).toString();
                    if (firstUrl == null) firstUrl = abs;
                    if (bandwidth > bestBandwidth) {
                        bestBandwidth = bandwidth;
                        bestUrl = abs;
                    }
                    bandwidth = -1;
                }
            }
        }
        return bestUrl != null ? bestUrl : firstUrl;
    }

    private long parseBandwidth(String line) {
        int idx = line.indexOf("BANDWIDTH=");
        if (idx >= 0) {
            int start = idx + 10;
            int end = start;
            while (end < line.length() && Character.isDigit(line.charAt(end))) end++;
            try {
                return Long.parseLong(line.substring(start, end));
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    /** 把分片下到调用方给定的临时文件；写完后由调用方核验并改名，跳过逻辑不在此层 */
    private void downloadSegment(String url, File file) throws IOException {
        Request.Builder builder = new Request.Builder().url(url).get();
        for (Map.Entry<String, String> e : headers.entrySet()) builder.addHeader(e.getKey(), e.getValue());
        try (Response res = OkHttp.client().newCall(builder.build()).execute()) {
            if (!res.isSuccessful() || res.body() == null) throw new IOException("HTTP " + res.code());
            long expected = -1;
            String cl = res.header("Content-Length");
            if (cl != null) {
                try {
                    expected = Long.parseLong(cl.trim());
                } catch (NumberFormatException ignored) {
                }
            }
            long written = 0;
            try (InputStream is = res.body().byteStream();
                 FileOutputStream fos = new FileOutputStream(file)) {
                byte[] buf = new byte[32768];
                int len;
                while ((len = is.read(buf)) != -1) {
                    if (canceled) return;
                    fos.write(buf, 0, len);
                    written += len;
                }
            }
            if (!canceled && expected > 0 && written != expected) {
                throw new IOException("分片长度校验失败: 期望 " + expected + " 字节, 实际 " + written + " 字节 (" + url + ")");
            }
        }
    }

    private void mergeSegments(File tmpDir, int total, File target) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(target)) {
            byte[] buf = new byte[65536];
            for (int i = 0; i < total; i++) {
                if (canceled) return;
                // 只拼正式分片；临时文件（.tmp/.dec.tmp）永不参与合并
                File seg = new File(tmpDir, String.format("%05d.ts", i));
                if (!seg.exists() || seg.length() == 0) {
                    throw new IOException("分片缺失, 无法合并: 第 " + i + " 片");
                }
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
