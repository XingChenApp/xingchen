package com.fongmi.android.tv.api.hk;

import java.util.Locale;

/**
 * 海阔详情条目 → 播放页选集的资格判定（桥 {@link HkDetailBridge} 与
 * HkPageActivity 两份选集收集器共用同一判定，钉根账
 * playpage-episode-pollution-dig-20261111）。
 *
 * <p>断点回顾：旧收编层只按 url 形态（http／@lazyRule=／js:）判集数资格，
 * 详情页里动作项、跨源搜索入口、线路/排序钮、封面头卡的 url 形态与真集数
 * 相同，整页被扫进选集。本判定只用条目自带事实（col_type 角色＋url 自带
 * 的点击形态声明）分层过关：</p>
 *
 * <ol>
 *   <li>角色关：头卡 col_type（{@link #isHeaderColType} 口径，与 HkPageActivity
 *       合成详情头共用同一实现）一律出局——封面是元数据，只落 vod.pic；</li>
 *   <li>形态硬过滤：空／javascript:／动作 scheme 原样（{@link HkRouter#isActionUrl}
 *       正表）／video:// 前缀／@rule= 子列表导航（HkEpisodeSorter 刀一同口径）／
 *       ignoreVideo·ignoreMusic 标记／hiker:// 内部导航，一律出局；</li>
 *   <li>图片关（两入口同口径）：url 即条目自身封面（url==pic 的头卡模板，
 *       含无扩展名图链），或经官 UrlDetector 分流判为 image
 *       （{@link HkRouter#dealKind} 正表），一律出局；</li>
 *   <li>解析型条目（@lazyRule=／js:）：按基址自声明的点击形态判——带
 *       #noLoading# 且剥掉页面标记后无真实页面（空或 hiker://empty）的，
 *       是源自声明的即时动作：其点击求值去向恒为 refresh／hiker://empty、
 *       hiker://search、toast://、copy:// 一类非播放结果（样本框架
 *       renderQuickSearch／renderEpisodes 排序钮／线路签实测），回详情页
 *       分发、不进选集；基址是真实剧集页、或空基址取址型的，才是集数候选，
 *       其求值语义由点击／播放时的既有分流（HkRouter.detail ①、
 *       play＋checkPlayExit）把关。收编时不逐条预求值的理由：真集数的
 *       lazy 函数点开才 fetch 取址（逐条预求值＝整页逐集联网），动作
 *       lazy 函数带 putMyVar 等持久副作用（预求值会翻排序／线路变量），
 *       两头都不是收编层能付的价；</li>
 *   <li>P2P 三形（magnet/ed2k/thunder，两入口现行口径）与普通 http 链接
 *       过关；http 在线路内再由 HkEpisodeSorter 聚类整理。</li>
 * </ol>
 *
 * <p>禁项（账 §六.5）：不按标题黑名单、不拿 col_type 单字段当唯一判据、
 * 不给 Episode 加假集标记在 UI 藏——源头不收才是修。</p>
 */
public final class HkEpisodeQualifier {

    private HkEpisodeQualifier() {
    }

    /**
     * 详情头型 col_type 的唯一实现处（HkPageActivity.isHeaderColType 委托
     * 至此，页面合成详情头与选集资格共用）：detail_header 或 movie_1 系头卡。
     */
    public static boolean isHeaderColType(String colType) {
        if (colType == null) return false;
        String c = colType.trim().toLowerCase(Locale.ROOT);
        return c.equals("detail_header") || c.contains("movie_1_vertical_pic")
                || c.equals("movie_1") || c.equals("movie_1_left_pic");
    }

    /** 条目是否够格进播放页选集（colType／url／pic 均为条目自带事实）。 */
    public static boolean isEpisode(String colType, String url, String pic) {
        if (isHeaderColType(colType)) return false;
        if (url == null) return false;
        String u = url.trim();
        if (u.isEmpty()) return false;
        String low = u.toLowerCase(Locale.ROOT);
        if (low.startsWith("javascript:")) return false;
        // 动作 scheme 原样（toast/copy/input/confirm/select/download/hiker://empty 正表）
        if (HkRouter.isActionUrl(u)) return false;
        if (low.startsWith("video://")) return false;
        // @rule= 子列表导航（Sorter 刀一：同址另挂 @lazyRule= 也不救）
        if (u.contains("@rule=")) return false;
        if (low.contains("ignorevideo") || low.contains("ignoremusic")) return false;
        // hiker:// 内部导航（page/search/home 等）不是集数
        if (low.startsWith("hiker://")) return false;
        if (u.contains("@lazyRule=") || u.startsWith("js:")) {
            int lr = u.indexOf("@lazyRule=");
            String base = lr >= 0 ? u.substring(0, lr) : "";
            return !isInstantActionBase(base);
        }
        if (low.startsWith("magnet:") || low.startsWith("ed2k:") || low.startsWith("thunder:")) {
            return true;
        }
        if (low.startsWith("http://") || low.startsWith("https://")) {
            // url 即封面（头卡模板把封面链写进 url，含无扩展名图链）
            if (pic != null && u.equals(pic.trim())) return false;
            // 官 UrlDetector 图片分流（dealKind 正表，与点击分流同一判据）
            if ("image".equals(HkRouter.dealKind(u))) return false;
            return true;
        }
        return false;
    }

    /**
     * lazy 基址是否为「即时动作」声明：带 #noLoading#（点击免加载标记），
     * 且剥掉全部 #…# 页面标记后无真实页面（空或 hiker://empty）。
     * 空基址（$().lazyRule 取址型集数）无 #noLoading# 声明，不在此列；
     * 真实剧集页基址即使带 #noLoading# 标记，剥标记后仍有页面，不在此列。
     */
    private static boolean isInstantActionBase(String base) {
        if (!base.contains("#noLoading#")) return false;
        String rest = base.replaceAll("#[^#]*#", "").trim();
        return rest.isEmpty() || rest.equals("hiker://empty");
    }
}
