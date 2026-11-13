package com.fongmi.android.tv.ui.activity;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * txt 分发判定（纯逻辑、零 Android 依赖，harness 可单机验）：只三分——
 * 书源（Legado 式 JSON）／小说正文／拒收。调用方喂文件头样本（≤512KB）
 * 与总大小（取不到填 -1），整本判定不读全文件，大正文靠流式落盘不在这里处理。
 * <p>
 * 书源判定口径与现行导入器（LegadoStore.parseListTolerant）对齐：内容是
 * JSON（裸对象或数组）且带 bookSourceUrl 特征字段——最终裁定仍在
 * LegadoStore.importText（它逐项验 bookSourceUrl 非空），这里只是分发预判；
 * 是 JSON 却没有书源特征的一律拒收（不许当正文吞，JSON 当小说索引出来是垃圾）。
 * 正文判定：非 JSON 的长文本——有章节行（第X章/回/节… 或 Chapter N）或
 * 够长（≥4KB）；太短（&lt;256B）与含 NUL 的二进制冒充一律拒收。
 * 编码：书源按生态惯例只认 UTF-8（ASCII 特征字段与编码无关，字节级扫）；
 * 正文兼容 UTF-16（BOM 豁免 NUL 检查、靠长度过），GBK/Big5 靠长度规则过
 * （章节行正则只对 UTF-8 解码命中，GBK 短篇无章节行会被拒——已知边缘，记账）。
 */
public final class TxtFileJudge {

    public enum Kind {BOOK_SOURCE, NOVEL, UNKNOWN}

    /** 太短不认：短于此值的纯文本既不是书源也不是正文 */
    private static final long MIN_BYTES = 256;
    /** 够长即正文：达到此长度的非 JSON 文本不再要求章节行 */
    private static final long LONG_BYTES = 4096;

    private static final Pattern CHAPTER = Pattern.compile(
            "第[0-9零一二三四五六七八九十百千万两]+[章节回卷集部篇]|Chapter\\s+\\d+",
            Pattern.MULTILINE);

    private TxtFileJudge() {
    }

    /**
     * @param sample    文件头样本（头部若干字节，可为全文）
     * @param wholeFile sample 是否就是整个文件
     * @param totalSize 文件总字节数，取不到填 -1（此时以 sample 长度为下限推断）
     */
    public static Kind judge(byte[] sample, boolean wholeFile, long totalSize) {
        if (sample == null || sample.length == 0) return Kind.UNKNOWN;
        int off = 0;
        boolean utf16 = false;
        if (sample.length >= 3
                && (sample[0] & 0xFF) == 0xEF && (sample[1] & 0xFF) == 0xBB && (sample[2] & 0xFF) == 0xBF) {
            off = 3; // UTF-8 BOM
        } else if (sample.length >= 2
                && (((sample[0] & 0xFF) == 0xFF && (sample[1] & 0xFF) == 0xFE)
                || ((sample[0] & 0xFF) == 0xFE && (sample[1] & 0xFF) == 0xFF))) {
            utf16 = true; // UTF-16 BE/LE BOM
        }
        if (!utf16) {
            int i = off;
            while (i < sample.length && isBlank(sample[i])) i++;
            if (i < sample.length && (sample[i] == '[' || sample[i] == '{')) {
                // JSON 形态：特征字段是纯 ASCII，ISO-8859-1 字节级映射后扫，不受编码影响
                String head = new String(sample, StandardCharsets.ISO_8859_1);
                return head.contains("bookSourceUrl") ? Kind.BOOK_SOURCE : Kind.UNKNOWN;
            }
            // 二进制冒充：正文 txt（UTF-8/GBK/Big5）不含 NUL 字节
            for (int j = off; j < sample.length; j++) {
                if (sample[j] == 0) return Kind.UNKNOWN;
            }
        }
        if (wholeFile && sample.length < MIN_BYTES) return Kind.UNKNOWN;
        if (totalSize >= 0 && totalSize < MIN_BYTES) return Kind.UNKNOWN;
        long effective = totalSize >= 0 ? totalSize : sample.length;
        if (effective >= LONG_BYTES) return Kind.NOVEL;
        // 短文本：有章节行才认正文（正则只对 UTF-8/ASCII 解码有效，见类头注）
        String text = new String(sample, StandardCharsets.UTF_8);
        if (CHAPTER.matcher(text).find()) return Kind.NOVEL;
        return Kind.UNKNOWN;
    }

    private static boolean isBlank(byte b) {
        return b == ' ' || b == '\t' || b == '\n' || b == '\r';
    }
}
