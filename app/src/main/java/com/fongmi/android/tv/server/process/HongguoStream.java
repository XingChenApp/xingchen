package com.fongmi.android.tv.server.process;

import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;

import com.fongmi.android.tv.hongguo.proxy.HgCencStream;
import com.fongmi.android.tv.hongguo.proxy.HgRangeInputStream;
import com.fongmi.android.tv.hongguo.proxy.HgStreamRegistry;
import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.impl.Process;

import java.io.ByteArrayInputStream;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;

/**
 * 红果本地代理解密出流：挂在 App 本地服务器（Nano）上，与 Proxy115 同款接法
 * （一个 Process 按路径前缀认领请求），不另起服务器框架。
 *
 * <p>路径：/hongguo/stream?t=&lt;注册表 token&gt;。播放器按 Range 请求，
 * 本类把区间交给 {@link HgCencStream}：只取所需上游区间、只解相关样本、
 * 补丁表同长改写透明套用——边下边解边出流，不整集落地。
 * 本类跑在 Nano 的 IO 线程上，取数与解密都不占主线程。</p>
 */
public class HongguoStream implements Process {

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.startsWith("/hongguo/");
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        try {
            String token = session.getParms().get("t");
            HgCencStream stream = HgStreamRegistry.get().open(token);
            if (stream == null) return Nano.error(Status.NOT_FOUND, "红果流不存在或已回收");
            long total = stream.length();
            long[] range = parseRange(session.getHeaders().get("range"), total);
            if (range == null) {
                Response res = newFixedLengthResponse(Status.RANGE_NOT_SATISFIABLE, Nano.MIME_PLAINTEXT, "");
                res.addHeader("Content-Range", "bytes */" + total);
                return res;
            }
            long start = range[0], end = range[1];
            long len = end - start + 1;
            boolean head = session.getMethod() == fi.iki.elonen.NanoHTTPD.Method.HEAD;
            Response res = newFixedLengthResponse(
                    len < total ? Status.PARTIAL_CONTENT : Status.OK,
                    "video/mp4",
                    head ? new ByteArrayInputStream(new byte[0]) : new HgRangeInputStream(stream, start, end + 1),
                    head ? 0 : len);
            res.addHeader("Content-Length", String.valueOf(len));
            res.addHeader("Accept-Ranges", "bytes");
            if (len < total) res.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + total);
            return res;
        } catch (java.io.IOException e) {
            // 上游取数/解密的 IO 失败（A-10）：与「流不存在 404」「Range
            // 非法 416」分开，给播放器一个可区分的上游失败码，不再与
            // 其余异常共用一档含糊文本。流式出流途中（HgRangeInputStream
            // .fill）的解密失败同为 IOException，由 Nano 出流层按连接
            // 失败收口，不会把错误文本当媒体数据喂给播放器。
            return Nano.error(Status.BAD_GATEWAY, "红果上游取数或解密失败: " + e.getMessage());
        } catch (Exception e) {
            return Nano.error("红果代理解密失败: " + e.getMessage());
        }
    }

    /** 解析 Range 头（口径同 Local.HttpRange）：返回 {start,end} 闭区间；非法返回 null。 */
    private static long[] parseRange(String header, long total) {
        long start = 0, end = total - 1;
        if (header != null && header.startsWith("bytes=")) {
            try {
                String[] parts = header.substring(6).split("-", 2);
                if (parts[0].isEmpty()) {
                    // 后缀式 bytes=-N：取尾 N 字节
                    long suffix = Long.parseLong(parts[1]);
                    if (suffix <= 0) return null;
                    start = Math.max(0, total - suffix);
                } else {
                    start = Long.parseLong(parts[0]);
                    if (parts.length > 1 && !parts[1].isEmpty()) end = Long.parseLong(parts[1]);
                }
                if (start >= total || start > end) return null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (end >= total) end = total - 1;
        return new long[]{start, end};
    }
}
