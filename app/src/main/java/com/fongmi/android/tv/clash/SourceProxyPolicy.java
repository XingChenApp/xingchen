package com.fongmi.android.tv.clash;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * 按源定向策略表：决定某个来源的流量走节点（本地混合口）还是直连。
 * <p>
 * 用户已拍板：默认全部直连；逐源单独开「走节点」。
 * <p>
 * key 分三类：
 * <ul>
 * <li>{@link #KIND_PY}：PY 点播源，按源标识（开工时与壳子侧确认标识口径，见 README 未决问题）；
 * <li>{@link #KIND_JS}：JS 点播源，按源标识；
 * <li>{@link #KIND_HTML}：HTML 网页源（插件页导入的单文件 .html，动态源 id=html_+基名），按源标识；
 * <li>{@link #KIND_HK}：海阔小程序，按规则名；
 * <li>{@link #KIND_BOOK}：书源（Legado 书源仓库），按 bookSourceUrl；阅读取数（正文/图片/音频/缓存下载）用。
 * <li>{@link #KIND_MUSIC}：音乐源（FreeJS 插件），按 srcUrl（音乐源的唯一身份，与桥的 key 同口径）；
 *     插件接口请求与脚本下载经桥/源管理打点后走本表决策。语义：单源开了即生效、
 *     不要求总开关先开（「需代理」是这类源的接入定案，见 源管理 的指定源镜像）。
 * <li>{@link #KIND_PIKPAK}：PikPak 网盘，id 固定为 DrivePikPak.ID（"pikpak"），仅作打点归属；
 *     不设单源开关、不查本表——默认带上：节点总开关开着、内核在跑就走节点
 *     （用户 2026-10-26 口径「直接给他带上就行了，还单独搞个开关？」）。
 * </ul>
 * 决策统一走 {@link #shouldProxy}：总开关（ClashSettings）关 → 一律直连；
 * 总开关开 → 查本表。
 */
public final class SourceProxyPolicy {

    public static final String KIND_VOD = "vod";
    public static final String KIND_PY = "py";
    public static final String KIND_JS = "js";
    /** HTML 网页源（单文件 .html 插件）：id=html_+基名，与 SiteApi 打点归属一致。 */
    public static final String KIND_HTML = "html";
    public static final String KIND_HK = "hk";
    /** 书源（Legado 书源仓库）：id=bookSourceUrl，与阅读打点一致。 */
    public static final String KIND_BOOK = "book";
    /** 音乐源（FreeJS 插件）：id=srcUrl，与桥 key()、源管理身份同口径。 */
    public static final String KIND_MUSIC = "music";
    /** PikPak 网盘：id 固定为 DrivePikPak.ID（"pikpak"），仅作打点归属，不设单源开关。 */
    public static final String KIND_PIKPAK = "pikpak";

    private static final String PREFS = "clash_source_policy";

    private SourceProxyPolicy() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(String kind, String id) {
        return kind + ":" + (id == null ? "" : id);
    }

    /** 某源是否开了「走节点」（默认关 = 直连）。 */
    public static boolean isEnabled(Context context, String kind, String id) {
        return prefs(context).getBoolean(key(kind, id), false);
    }

    /** 设置某源走节点 / 直连。 */
    public static void setEnabled(Context context, String kind, String id, boolean on) {
        prefs(context).edit().putBoolean(key(kind, id), on).apply();
    }

    /** 该源在本表里有没有显式条目（区分「从没写过」与「用户明确关了」：指定源镜像只补前者）。 */
    public static boolean hasEntry(Context context, String kind, String id) {
        return prefs(context).contains(key(kind, id));
    }

    /**
     * 壳子网络层决策入口：这个来源的请求要不要走本地节点口。
     * 音乐：单源开了走节点即生效，不再要求节点总开关先开（用户口径：开了就生效；
     * 音乐侧「需代理」指定源由源管理把定案镜像进本表，见 源管理）。
     * PikPak：默认带上，不设单源开关、不查本表，只看节点总开关（总开关开即走节点）。
     * 其它源：维持原语义，总开关关 → 一律直连。
     */
    public static boolean shouldProxy(Context context, String kind, String id) {
        if (KIND_MUSIC.equals(kind)) return isEnabled(context, kind, id);
        if (KIND_PIKPAK.equals(kind)) return ClashSettings.isEnabled(context);
        if (!ClashSettings.isEnabled(context)) return false;
        return isEnabled(context, kind, id);
    }

    /** 某 kind 下是否有任一源开了走节点（节点按需拉起、门控 enabled() 用）。 */
    public static boolean anyEnabled(Context context, String kind) {
        try {
            String prefix = kind + ":";
            java.util.Map<String, ?> all = prefs(context).getAll();
            if (all == null) return false;
            for (java.util.Map.Entry<String, ?> e : all.entrySet()) {
                if (e.getKey() != null && e.getKey().startsWith(prefix) && Boolean.TRUE.equals(e.getValue()))
                    return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** 列出已开走节点的源（设置页展示用）。 */
    public static List<String> listEnabled(Context context) {
        List<String> out = new ArrayList<>();
        for (String k : prefs(context).getAll().keySet()) {
            Object v = prefs(context).getAll().get(k);
            if (Boolean.TRUE.equals(v)) out.add(k);
        }
        return out;
    }

    /** 清空全表（恢复默认全部直连）。 */
    public static void clearAll(Context context) {
        prefs(context).edit().clear().apply();
    }
}
