package com.fongmi.android.tv.api.hk;

import java.util.Locale;

/**
 * 详情头型 col_type 判定（唯一实现处，HkPageActivity.isHeaderColType
 * 委托至此，页面合成详情头共用）。
 *
 * <p>本类原是「选集资格判定五关」（整页扫＋逐条判定），2026-11-13 按
 * 账 playpage-episode-pollution-dig-20261111 §十换官形后整段摘除：
 * 选集收编改走 {@link HkChapters} 的连续段结构（页面收集器）＋段内
 * 干跑复核（toVod），逐条资格判定不再存在。此处只留与选集无关的
 * 头型判定。</p>
 */
public final class HkEpisodeQualifier {

    private HkEpisodeQualifier() {
    }

    /** 详情头型 col_type：detail_header 或 movie_1 系头卡。 */
    public static boolean isHeaderColType(String colType) {
        if (colType == null) return false;
        String c = colType.trim().toLowerCase(Locale.ROOT);
        return c.equals("detail_header") || c.contains("movie_1_vertical_pic")
                || c.equals("movie_1") || c.equals("movie_1_left_pic");
    }
}
