package com.fongmi.android.tv.tts;

import java.util.ArrayList;
import java.util.List;

/**
 * 章节正文 → 合成段。纯函数、确定性（同文本同切分，断点续播可重算）。
 * <p>
 * 口径：按句切（句末标点＋收尾引号），再按目标长度合并；
 * 目标约 300 字（太短则请求太多、太长则首段延迟大），硬上限约 520 字。
 */
public class TtsSegmenter {

    /** 目标段长（字）。 */
    public static final int TARGET = 300;
    /** 硬上限（字），超长单句在此硬切。 */
    public static final int HARD_MAX = 520;

    public static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        String norm = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (norm.isEmpty()) return out;
        List<String> sentences = new ArrayList<>();
        // 先按段落切
        for (String para : norm.split("\n")) {
            String p = para.trim();
            if (p.isEmpty()) continue;
            sentences.addAll(splitSentences(p));
        }
        StringBuilder cur = new StringBuilder();
        for (String s : sentences) {
            if (s.length() > HARD_MAX) {
                // 超长单句：先落盘当前段，再硬切
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                out.addAll(hardSplit(s));
                continue;
            }
            if (cur.length() + s.length() > TARGET && cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(s);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    /** 按句末标点切句（含收尾引号 ”’"」）。 */
    private static List<String> splitSentences(String para) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int n = para.length();
        for (int i = 0; i < n; i++) {
            char c = para.charAt(i);
            cur.append(c);
            if (isEndPunct(c)) {
                // 收尾引号跟进
                while (i + 1 < n && isClosingQuote(para.charAt(i + 1))) {
                    cur.append(para.charAt(i + 1));
                    i++;
                }
                out.add(cur.toString());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static boolean isEndPunct(char c) {
        return c == '。' || c == '！' || c == '？' || c == '；'
                || c == '…' || c == '!' || c == '?' || c == ';';
    }

    private static boolean isClosingQuote(char c) {
        return c == '”' || c == '’' || c == '"' || c == '」' || c == '\'';
    }

    /** 超长句硬切：优先在 ，、 处断，不行按字数断。 */
    private static List<String> hardSplit(String s) {
        List<String> out = new ArrayList<>();
        int start = 0;
        int n = s.length();
        while (start < n) {
            int end = Math.min(start + HARD_MAX, n);
            if (end < n) {
                int cut = -1;
                for (int i = end; i > start + TARGET; i--) {
                    char c = s.charAt(i);
                    if (c == '，' || c == '、' || c == ',' || c == ' ') {
                        cut = i + 1;
                        break;
                    }
                }
                if (cut > 0) end = cut;
            }
            out.add(s.substring(start, end));
            start = end;
        }
        return out;
    }
}
