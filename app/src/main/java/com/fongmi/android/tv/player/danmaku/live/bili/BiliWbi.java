package com.fongmi.android.tv.player.danmaku.live.bili;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

/**
 * Bilibili WBI 签名，照 dart_simple_live 的 bilibili_site.dart（getWbiKeys/getMixinKey/getWbiSign）对译。
 * 壳子点播侧全树无现成 WBI 实现（Java 与 JS 资产均已 grep 确认），故在本包落一份、只此一份：
 * mixinKey＝按固定置换表重排 (imgKey＋subKey) 取前 32 字符；
 * 签名＝参数加 wts、按 key 排序、value 剔除 !'()* 后 URL 编码拼串，再 md5(串＋mixinKey) 得 w_rid。
 * imgKey/subKey 从 /x/web-interface/nav 的 wbi_img 两个图片文件名提取（取 keys 的一跳在 BiliProvider）。
 */
public final class BiliWbi {

    private static final int[] MIXIN_KEY_ENC_TAB = {
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
            37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4, 22,
            25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    };

    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final char[] HEX_UPPER = "0123456789ABCDEF".toCharArray();

    private BiliWbi() {
    }

    public static String mixinKey(String imgKey, String subKey) {
        String origin = (imgKey == null ? "" : imgKey) + (subKey == null ? "" : subKey);
        StringBuilder builder = new StringBuilder(origin.length());
        for (int index : MIXIN_KEY_ENC_TAB) {
            if (index < origin.length()) builder.append(origin.charAt(index));
        }
        return builder.length() > 32 ? builder.substring(0, 32) : builder.toString();
    }

    /** 拼出带 wts 与 w_rid 的完整查询串（参数值先剔除 !'()* 再编码，与参照实现一致）。 */
    public static String buildSignedQuery(Map<String, String> params, String mixinKey, long wtsSeconds) {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        sorted.put("wts", String.valueOf(wtsSeconds));
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (query.length() > 0) query.append('&');
            query.append(entry.getKey()).append('=').append(percentEncode(filterValue(entry.getValue())));
        }
        String wRid = md5Hex(query + (mixinKey == null ? "" : mixinKey));
        return query + "&w_rid=" + wRid;
    }

    static String filterValue(String value) {
        if (value == null) return "";
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '!' || c == '\'' || c == '(' || c == ')' || c == '*') continue;
            builder.append(c);
        }
        return builder.toString();
    }

    static String percentEncode(String value) {
        StringBuilder builder = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            boolean unreserved = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '.' || c == '_' || c == '~';
            if (unreserved) builder.append((char) c);
            else builder.append('%').append(HEX_UPPER[c >>> 4]).append(HEX_UPPER[c & 0xF]);
        }
        return builder.toString();
    }

    static String md5Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(32);
            for (byte b : digest) builder.append(HEX[(b >>> 4) & 0xF]).append(HEX[b & 0xF]);
            return builder.toString();
        } catch (Exception error) {
            return "";
        }
    }
}
