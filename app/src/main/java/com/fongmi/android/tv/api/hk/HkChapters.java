package com.fongmi.android.tv.api.hk;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 选集收编的官形实现（钉根账 playpage-episode-pollution-dig-20261111 §十）：
 * 海阔 8.83 ArticleListFragment.getChapters 逐行移植。官方从不逐条判
 * 「是不是集数」——以被点条目为锚，只收与它相邻的同 col_type 连续段
 * （或 cls 含 playlist 的同组条目），段外条目结构上进不来；收编时只查
 * 四个排除项（header 型、url 空、url 含 ignoreVideo/ignoreMusic/
 * "@rule="），url 原样保留、播到该章时才求值。此前咱家三处收集器是
 * 「整页扫＋逐条资格判定」，整页扫必然拉进别的区块，只能不断加判定
 * 堵漏（他点名的「遇一个补一个」），本类即其结构替代。
 */
public final class HkChapters {

    private HkChapters() {
    }

    /** collect 结果：章节序（原 url 不求值）＋被点条目在其中的下标。 */
    public static final class Collected {
        public final List<HkItem> items;
        public final int selectedIndex;

        Collected(List<HkItem> items, int selectedIndex) {
            this.items = items;
            this.selectedIndex = selectedIndex;
        }
    }

    /** 官后向段排除：header 型／url 空／url 含 ignoreVideo、ignoreMusic、"@rule="。 */
    public static boolean isExcluded(String colType, String url) {
        if ("header".equals(colType)) return true;
        return isExcludedUrl(url);
    }

    /** 官前向段排除（无 header 项）：url 空／含 ignoreVideo、ignoreMusic、"@rule="——先于断段判定，排除项不断段。 */
    public static boolean isExcludedUrl(String url) {
        if (url == null || url.isEmpty()) return true;
        return url.contains("ignoreVideo") || url.contains("ignoreMusic") || url.contains("@rule=");
    }

    private static String clsOf(HkItem item) {
        String cls = item.getExtra("cls");
        return cls == null ? "" : cls;
    }

    private static String typeOf(HkItem item) {
        String t = item.getColType();
        return t == null ? "" : t;
    }

    private static String urlOf(HkItem item) {
        String u = item.getUrl();
        return u == null ? "" : u.trim();
    }

    /**
     * 官 getChapters 移植（页面收集器用）：锚 = clickedIndex。
     * 播放列表模式（被点条目 cls 含 "playlist"）：全页同 cls 条目按序收
     * （四排除跳过不中断）；普通模式：后向先回走到同 col_type 连续段段首，
     * 前向遇不同 col_type 立即断段。
     */
    public static Collected collect(List<HkItem> list, int clickedIndex) {
        List<HkItem> out = new ArrayList<>();
        if (list == null || clickedIndex < 0 || clickedIndex >= list.size()) {
            return new Collected(out, -1);
        }
        HkItem clicked = list.get(clickedIndex);
        String type = typeOf(clicked);
        String clickedCls = clsOf(clicked);
        boolean playList = !clickedCls.isEmpty() && clickedCls.contains("playlist");
        int start;
        if (playList) {
            start = 0;
        } else {
            start = clickedIndex;
            for (int i = clickedIndex - 1; i >= 0 && type.equals(typeOf(list.get(i))); i--) {
                start = i;
            }
        }
        for (int i = start; i < clickedIndex; i++) {
            HkItem it = list.get(i);
            if (isExcluded(it.getColType(), urlOf(it))) continue;
            if (playList && !clickedCls.equals(clsOf(it))) continue;
            out.add(it);
        }
        int selected = out.size();
        out.add(clicked);
        for (int i = clickedIndex + 1; i < list.size(); i++) {
            HkItem it = list.get(i);
            if (isExcludedUrl(urlOf(it))) continue;
            if (playList) {
                if (!clickedCls.equals(clsOf(it))) continue;
            } else if (!type.equals(typeOf(it))) {
                break;
            }
            out.add(it);
        }
        return new Collected(out, selected);
    }

    /** 可播形态（toVod 段内候选门）：http(s)／@lazyRule=／js:／磁力三形。 */
    public static boolean isPlayForm(String url) {
        if (url == null) return false;
        String t = url.trim();
        String low = t.toLowerCase(Locale.ROOT);
        if (low.startsWith("http://") || low.startsWith("https://")) return true;
        if (t.contains("@lazyRule=")) return true;
        if (low.startsWith("js:")) return true;
        return low.startsWith("magnet:") || low.startsWith("ed2k:") || low.startsWith("thunder:");
    }

    /**
     * toVod 用（官无此转换，按同一结构原则落，账 §十.3）：详情条目序列按
     * col_type 切连续段，段内只收可播形态且未被四排除的条目（排除项跳过
     * 不断段、type 变即断段）；保序返回。干跑 ACTION 复核由调用方按标做。
     */
    public static List<HkDetailItem> collectEpisodeRuns(List<HkDetailItem> rawItems) {
        List<HkDetailItem> out = new ArrayList<>();
        if (rawItems == null) return out;
        String runType = null;
        for (HkDetailItem di : rawItems) {
            String type = di.getColType() == null ? "" : di.getColType();
            if (!type.equals(runType)) runType = type;
            // 头卡是封面元数据（已落 vod.pic），url 常是封面图链，不成集
            if (HkEpisodeQualifier.isHeaderColType(di.getColType())) continue;
            String url = di.getUrl() == null ? "" : di.getUrl().trim();
            if (isExcluded(di.getColType(), url)) continue;
            if (!isPlayForm(url)) continue;
            out.add(di);
        }
        return out;
    }
}
