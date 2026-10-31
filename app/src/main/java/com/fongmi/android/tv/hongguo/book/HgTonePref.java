package com.fongmi.android.tv.hongguo.book;

import com.github.catvod.utils.Prefers;

/**
 * 红果听书音色选择落盘（T-02）：按 bookId 存一份 tone_id，下次打开
 * 本书沿用；缺失/0＝未选，取流走服务端默认（batch_play 推荐音色）。
 * 只存选择本身，不存音色表——表每次现拉 toneinfo（服务端会增删音色，
 * 存了表迟早过期）。与 HgDevice 的身份持久化同走 Prefers。
 */
public final class HgTonePref {

    private static final String KEY_PREFIX = "hongguo_tone_pick_";

    private HgTonePref() {
    }

    /** 本书已选音色 id；未选回 0。 */
    public static long toneOf(String bookId) {
        if (bookId == null || bookId.isEmpty()) return 0;
        try {
            String s = Prefers.getString(KEY_PREFIX + bookId);
            if (s == null || s.isEmpty()) return 0;
            return Long.parseLong(s.trim());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static void save(String bookId, long toneId) {
        if (bookId == null || bookId.isEmpty() || toneId <= 0) return;
        try {
            Prefers.put(KEY_PREFIX + bookId, String.valueOf(toneId));
        } catch (Throwable ignored) {
        }
    }
}
