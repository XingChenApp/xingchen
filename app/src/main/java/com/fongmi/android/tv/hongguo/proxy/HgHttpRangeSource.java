package com.fongmi.android.tv.hongguo.proxy;

import com.github.catvod.net.OkHttp;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 真 CDN 随机读源：每次 readAt 发一笔带 Range 的 OkHttp 请求，只取所需区间。
 *
 * <p>退路（如实写明）：上游若不认 Range（回 200 整文件而非 206），小文件
 * （≤{@link #WHOLE_FALLBACK_CAP}）整份读进内存缓存、之后 readAt 走缓存；
 * 大文件不整份入内存（A-11 防 OOM）——每笔 200 都从头流式丢弃到目标
 * 偏移再读窗口段，内存恒为窗口量级，代价是重复流量。正确性两种都保
 * 住，区间口径两种都丢。红果 CDN（字节系）实测认 Range，此退路只作
 * 保险，真机联调时以日志中 {@link #isRangeDegraded()} 为准核对。</p>
 */
public final class HgHttpRangeSource implements HgRandomSource {

    public static final String USER_AGENT =
            "com.phoenix.read/73932 (Linux; U; Android 14; zh_CN; 2509FPN0BC; Build/UQ1A.240205.09211156;tt-ok/3.12.13.20)";

    /** 整份入内存退路的上限：超过此长的大文件走流式窗口读，防整集 mp4 全量驻留堆内存（A-11）。 */
    private static final long WHOLE_FALLBACK_CAP = 32L * 1024 * 1024;

    private final String url;
    private final Map<String, String> extraHeaders;
    private volatile long length = -1;
    private volatile byte[] wholeFileFallback;
    private final AtomicLong fetched = new AtomicLong();
    private final AtomicInteger calls = new AtomicInteger();

    public HgHttpRangeSource(String url) {
        this(url, null);
    }

    public HgHttpRangeSource(String url, Map<String, String> extraHeaders) {
        this.url = url;
        this.extraHeaders = extraHeaders == null ? new HashMap<>() : new HashMap<>(extraHeaders);
    }

    /** 是否已退化为整份缓存（上游不支持 Range 时为 true）。 */
    public boolean isRangeDegraded() {
        return wholeFileFallback != null;
    }

    @Override
    public long length() throws IOException {
        if (length >= 0) return length;
        byte[] one = new byte[1];
        int n = readAt(0, one, 0, 1);
        if (n < 0) throw new IOException("上游空响应");
        return length;
    }

    @Override
    public int readAt(long pos, byte[] buf, int off, int len) throws IOException {
        byte[] whole = wholeFileFallback;
        if (whole != null) {
            if (pos >= whole.length) return -1;
            int n = (int) Math.min(len, whole.length - pos);
            System.arraycopy(whole, (int) pos, buf, off, n);
            return n;
        }
        Request.Builder b = new Request.Builder().url(url)
                .header("User-Agent", USER_AGENT)
                .header("Range", "bytes=" + pos + "-" + (pos + len - 1));
        for (Map.Entry<String, String> e : extraHeaders.entrySet()) b.header(e.getKey(), e.getValue());
        calls.incrementAndGet();
        try (Response resp = OkHttp.client().newCall(b.build()).execute()) {
            ResponseBody body = resp.body();
            if (body == null) throw new IOException("上游无响应体 code=" + resp.code());
            if (resp.code() == 200) {
                // 不认 Range。小文件整份读入作缓存退路（只发生一次）；
                // 大文件/长度未知不整份入内存：流式丢弃到 pos 再读窗口。
                long cl = body.contentLength();
                if (cl >= 0 && cl <= WHOLE_FALLBACK_CAP) {
                    byte[] all = body.bytes();
                    fetched.addAndGet(all.length);
                    if (length < 0) length = all.length;
                    wholeFileFallback = all;
                    if (pos >= all.length) return -1;
                    int n = (int) Math.min(len, all.length - pos);
                    System.arraycopy(all, (int) pos, buf, off, n);
                    return n;
                }
                if (length < 0 && cl >= 0) length = cl;
                InputStream in = body.byteStream();
                byte[] scratch = new byte[8192];
                long toSkip = pos;
                while (toSkip > 0) {
                    int n = in.read(scratch, 0, (int) Math.min(scratch.length, toSkip));
                    if (n < 0) return -1; // pos 已越过文件尾
                    fetched.addAndGet(n);
                    toSkip -= n;
                }
                int got = 0;
                while (got < len) {
                    int n = in.read(buf, off + got, len - got);
                    if (n < 0) break;
                    got += n;
                }
                fetched.addAndGet(got);
                return got == 0 ? -1 : got;
            }
            if (resp.code() != 206) throw new IOException("上游 Range 请求失败 code=" + resp.code());
            if (length < 0) {
                String cr = resp.header("Content-Range");
                if (cr != null) {
                    int slash = cr.lastIndexOf('/');
                    if (slash >= 0) {
                        try {
                            length = Long.parseLong(cr.substring(slash + 1).trim());
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
            InputStream in = body.byteStream();
            int got = 0;
            while (got < len) {
                int n = in.read(buf, off + got, len - got);
                if (n < 0) break;
                got += n;
            }
            fetched.addAndGet(got);
            return got == 0 ? -1 : got;
        }
    }

    @Override
    public long fetchedBytes() {
        return fetched.get();
    }

    @Override
    public int fetchCalls() {
        return calls.get();
    }

    @Override
    public void close() {
        wholeFileFallback = null;
    }
}
