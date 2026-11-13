package com.fongmi.android.tv.node;

import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import okhttp3.Request;
import okhttp3.Response;

/**
 * 只从远端 zip 里取一个条目，靠 HTTP Range 请求实现。
 *
 * <p>nodejs-mobile 官方只发布含三个架构的整包（57MB），而我们只要其中一个 libnode.so。
 * 先读末尾的中央目录定位条目偏移，再按需拉取那一段压缩数据，流量能省约三分之二。
 */
final class NodeRangeZip {

    /** 末尾先抓这么多字节，足够覆盖中央目录尾记录及少量条目头。 */
    private static final int TAIL = 128 * 1024;
    private static final int EOCD_SIG = 0x06054b50;
    private static final int CEN_SIG = 0x02014b50;
    private static final int LOC_SIG = 0x04034b50;

    interface Progress {
        void onProgress(long done, long total);
    }

    private NodeRangeZip() {
    }

    /**
     * @return null 表示成功，否则是失败原因（调用方可回退到整包下载）
     */
    static String extract(String url, String entryName, File target, Progress progress) {
        try {
            long total = contentLength(url);
            if (total <= 0) return "无法获取包大小";
            int tail = (int) Math.min(TAIL, total);
            byte[] end = range(url, total - tail, total - 1);
            if (end == null) return "读取中央目录失败";

            long cenOffset = centralOffset(end);
            if (cenOffset < 0) return "未找到中央目录";
            // 中央目录可能不在刚才那段里，单独按偏移取
            byte[] central = cenOffset >= total - tail
                    ? slice(end, (int) (cenOffset - (total - tail)), end.length)
                    : range(url, cenOffset, total - 1);
            if (central == null) return "读取中央目录内容失败";

            Entry entry = find(central, entryName);
            if (entry == null) return "包内未找到 " + entryName;

            // 本地文件头长度不定（含文件名和 extra），先取头部算出数据起点
            byte[] header = range(url, entry.offset, entry.offset + 30 - 1);
            if (header == null) return "读取条目头失败";
            ByteBuffer buffer = wrap(header);
            if (buffer.getInt(0) != LOC_SIG) return "条目头签名不符";
            int nameLen = buffer.getShort(26) & 0xFFFF;
            int extraLen = buffer.getShort(28) & 0xFFFF;
            long dataStart = entry.offset + 30 + nameLen + extraLen;

            SpiderDebug.log("node", "range fetch %s: %s bytes (of %s)", entryName, entry.compressed, total);
            return write(url, dataStart, entry, target, progress);
        } catch (Exception e) {
            SpiderDebug.log("node", e);
            return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }

    private static String write(String url, long start, Entry entry, File target, Progress progress) {
        if (target.getParentFile() != null) target.getParentFile().mkdirs();
        Request request = new Request.Builder().url(url).header("Range", "bytes=" + start + "-" + (start + entry.compressed - 1)).build();
        // 扫描 H14：先写临时文件、校验过才改名就位——此前直接写正式文件且成功条件
        // 仅「产出 >0 字节」，截断/错位的 206 响应能解出部分字节就被当成功，
        // 随后 writeMarker 把损坏的 libnode.so 钉成「已装好」（与 O7 叠加永久不可用）。
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (Response response = OkHttp.client(NodeRuntime.LIB_TRANSFER_TIMEOUT_MS).newCall(request).execute()) {
            if (response.code() != 206 || response.body() == null) return "Range 请求失败 HTTP " + response.code();
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            long written = 0;
            try (InputStream raw = new Counting(response.body().byteStream(), entry.compressed, progress);
                 InputStream in = entry.method == 0 ? raw : new InflaterInputStream(raw, new Inflater(true));
                 OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[65536];
                int len;
                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                    crc.update(buf, 0, len);
                    written += len;
                }
            }
            if (written <= 0) {
                tmp.delete();
                return "解出的文件为空";
            }
            if (written != entry.size) {
                tmp.delete();
                return "解出大小不符（期望 " + entry.size + "、实际 " + written + "）";
            }
            if (crc.getValue() != entry.crc) {
                tmp.delete();
                return "解出内容 CRC 不符";
            }
            if (target.exists() && !target.delete()) {
                tmp.delete();
                return "旧文件删除失败";
            }
            if (!tmp.renameTo(target)) {
                tmp.delete();
                return "临时文件改名失败";
            }
            target.setReadable(true, false);
            return null;
        } catch (Exception e) {
            tmp.delete();
            SpiderDebug.log("node", e);
            return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }

    private static long contentLength(String url) {
        try (Response response = OkHttp.client(NodeRuntime.LIB_TRANSFER_TIMEOUT_MS).newCall(new Request.Builder().url(url).head().build()).execute()) {
            String value = response.header("Content-Length");
            return TextUtils.isEmpty(value) ? -1 : Long.parseLong(value);
        } catch (Exception e) {
            return -1;
        }
    }

    private static byte[] range(String url, long from, long to) {
        Request request = new Request.Builder().url(url).header("Range", "bytes=" + from + "-" + to).build();
        try (Response response = OkHttp.client(NodeRuntime.LIB_TRANSFER_TIMEOUT_MS).newCall(request).execute()) {
            if (response.code() != 206 || response.body() == null) return null;
            return response.body().bytes();
        } catch (Exception e) {
            return null;
        }
    }

    /** 从尾部倒着找 EOCD，取中央目录起始偏移。 */
    private static long centralOffset(byte[] tail) {
        ByteBuffer buffer = wrap(tail);
        for (int i = tail.length - 22; i >= 0; i--) {
            if (buffer.getInt(i) == EOCD_SIG) return buffer.getInt(i + 16) & 0xFFFFFFFFL;
        }
        return -1;
    }

    private static Entry find(byte[] central, String name) {
        ByteBuffer buffer = wrap(central);
        int pos = 0;
        while (pos + 46 <= central.length) {
            if (buffer.getInt(pos) != CEN_SIG) break;
            int method = buffer.getShort(pos + 10) & 0xFFFF;
            // 中央目录定长段：CRC-32 在 +16、未压缩大小在 +24（此前都没读，H14 补上作校验据）
            long crc = buffer.getInt(pos + 16) & 0xFFFFFFFFL;
            long compressed = buffer.getInt(pos + 20) & 0xFFFFFFFFL;
            long size = buffer.getInt(pos + 24) & 0xFFFFFFFFL;
            int nameLen = buffer.getShort(pos + 28) & 0xFFFF;
            int extraLen = buffer.getShort(pos + 30) & 0xFFFF;
            int commentLen = buffer.getShort(pos + 32) & 0xFFFF;
            long offset = buffer.getInt(pos + 42) & 0xFFFFFFFFL;
            String entryName = new String(central, pos + 46, nameLen);
            if (name.equals(entryName)) return new Entry(offset, compressed, method, crc, size);
            pos += 46 + nameLen + extraLen + commentLen;
        }
        return null;
    }

    private static byte[] slice(byte[] source, int from, int to) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(to - from);
        out.write(source, from, to - from);
        return out.toByteArray();
    }

    private static ByteBuffer wrap(byte[] data) {
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static final class Entry {
        final long offset;
        final long compressed;
        final int method;
        /** 中央目录的 CRC-32（解压后内容）与未压缩大小，write() 校验用（H14） */
        final long crc;
        final long size;

        Entry(long offset, long compressed, int method, long crc, long size) {
            this.offset = offset;
            this.compressed = compressed;
            this.method = method;
            this.crc = crc;
            this.size = size;
        }
    }

    /** 按已读压缩字节数报进度——解压后的总长未知，用压缩流的进度更准。 */
    private static final class Counting extends InputStream {

        private final InputStream in;
        private final long total;
        private final Progress progress;
        private long done;
        private long lastPost;

        Counting(InputStream in, long total, Progress progress) {
            this.in = in;
            this.total = total;
            this.progress = progress;
        }

        @Override
        public int read() throws IOException {
            int value = in.read();
            if (value != -1) advance(1);
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int count = in.read(b, off, len);
            if (count > 0) advance(count);
            return count;
        }

        private void advance(int count) {
            done += count;
            if (progress != null && done - lastPost >= 1048576) {
                lastPost = done;
                progress.onProgress(done, total);
            }
        }

        @Override
        public void close() throws IOException {
            if (progress != null) progress.onProgress(done, total);
            in.close();
        }
    }
}
