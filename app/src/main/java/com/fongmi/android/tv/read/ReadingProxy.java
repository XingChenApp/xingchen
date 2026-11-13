package com.fongmi.android.tv.read;

import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.clash.ReadingProxyGate;
import com.fongmi.android.tv.clash.SourceProxyPolicy;
import com.github.catvod.net.SourceGate;

import java.util.List;

/**
 * 阅读会话 → 节点归属的唯一换算口。
 * <p>
 * 归属口径（与「按源定向」设置页一致）：
 * <ul>
 * <li>书源（Legado 书源仓库）：kind=book，id=bookSourceUrl（legadoKey）——
 *     新增归属，设置页「书源」栏逐个开关；
 * <li>海阔规则：kind=hk，id=规则名——沿用海阔栏的开关；
 * <li>点播源（py/js/点播）：kind 与点播一致（py/js/vod），id=站点 key——
 *     沿用对应栏的开关，阅读不另设。
 * </ul>
 * 优先级：legadoKey ＞ ruleTitle ＞ siteKey（与改道路由一致）。
 */
public final class ReadingProxy {

    private ReadingProxy() {
    }

    private static boolean empty(String s) {
        return s == null || s.isEmpty();
    }

    /** 会话 → [kind, id]；无网络归属（本地书）返回 null。 */
    public static String[] ownership(ReadingSession s) {
        if (s == null) return null;
        if (!empty(s.legadoKey)) return new String[]{SourceProxyPolicy.KIND_BOOK, s.legadoKey};
        if (!empty(s.ruleTitle)) return new String[]{SourceProxyPolicy.KIND_HK, s.ruleTitle};
        if (!empty(s.siteKey)) return new String[]{SiteApi.proxyKindForKey(s.siteKey), s.siteKey};
        return null;
    }

    /** 线程打点（push）；无归属返回 null（调用方配对 pop 时传回 own 判空）。 */
    public static SourceGate.Mark push(String[] own) {
        if (own == null) return null;
        return SourceGate.push(own[0], own[1]);
    }

    /** 恢复打点；own 为 null 时不碰（避免把外层标记清掉）。 */
    public static void pop(String[] own, SourceGate.Mark prev) {
        if (own != null) SourceGate.pop(prev);
    }

    /** 图片/音频 URL 的 host 登记（供 Glide/Exo 等无打点线程认领）。 */
    public static void recordUrls(List<String> urls, ReadingSession s) {
        String[] own = ownership(s);
        if (own != null) ReadingProxyGate.recordUrls(urls, own[0], own[1]);
    }

    /** 单个 URL 的 host 登记。 */
    public static void recordUrl(String url, ReadingSession s) {
        String[] own = ownership(s);
        if (own != null) ReadingProxyGate.recordUrl(url, own[0], own[1]);
    }
}
