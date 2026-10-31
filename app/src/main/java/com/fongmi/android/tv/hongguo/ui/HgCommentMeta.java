package com.fongmi.android.tv.hongguo.ui;

import java.util.Calendar;

/**
 * 评论行日期文本（T-06，他亲裁「加」）：数据源＝HongguoCommentApi
 * 解析的 create_timestamp——真回包 7 份样本 95 条全验：10 位秒级
 * （如 1792986027＝2026-10-31 01:20 CST，与同包 log_extra 里的
 * request_id 时间戳互证）。格式照官方截图 113856 同位形态
 * 「10-25 · 河北」中的日期段：本年内 MM-dd（零填充）、跨年
 * yyyy-MM-dd，时区随设备本地（官方同口径）。
 * 属地段未落：咱家读口（匿名 commentapi）的全部真回包全键普查
 * 无任何属地字段，官方评论/用户 RPC 模型全字段与官方包字符串
 * 表亦无 ip_location——字段名钉不到，不编造（账
 * t06-comment-date-region-mentions-20261031）。
 * created&lt;=0（本地回显条目）回空串，调用方不拼该段、不留空位。
 */
public final class HgCommentMeta {

    private HgCommentMeta() {
    }

    public static String dateText(long created) {
        if (created <= 0) return "";
        // 单位保险：已验恒为秒（10 位）；若将来回包改毫秒（13 位）按毫秒收
        long ms = created > 100_000_000_000L ? created : created * 1000L;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        Calendar now = Calendar.getInstance();
        int y = c.get(Calendar.YEAR);
        int m = c.get(Calendar.MONTH) + 1;
        int d = c.get(Calendar.DAY_OF_MONTH);
        if (y == now.get(Calendar.YEAR)) {
            return pad(m) + "-" + pad(d);
        }
        return y + "-" + pad(m) + "-" + pad(d);
    }

    private static String pad(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }
}
