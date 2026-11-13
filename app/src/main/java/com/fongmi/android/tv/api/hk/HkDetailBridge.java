package com.fongmi.android.tv.api.hk;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;

import java.util.ArrayList;
import java.util.List;

/**
 * 海阔详情 → 星辰 VideoActivity 数据模型桥接（M3）。
 * siteKey 用 {@code hk_<规则名>} 命名空间，播放拦截（M4）据此识别。
 */
public class HkDetailBridge {

    public static final String KEY_PREFIX = "hk_";

    public static String siteKey(String ruleTitle) {
        return KEY_PREFIX + (ruleTitle == null ? "" : ruleTitle);
    }

    public static boolean isHkKey(String key) {
        return key != null && key.startsWith(KEY_PREFIX);
    }

    public static Vod toVod(HkRule rule, String itemUrl, HkDetail detail) {
        Vod vod = new Vod();
        vod.setId(itemUrl == null ? "" : itemUrl);
        vod.setName(detail.getTitle());
        vod.setPic(detail.getPic());
        vod.setContent(detail.getContent());
        Site site = new Site();
        site.setKey(siteKey(rule.getTitle()));
        site.setName(rule.getTitle());
        vod.setSite(site);
        // 8.83 原版：从原始条目构建选集（按 line 分组，不再用预分类的 Line 模型）
        // 磁力/电驴/迅雷三形在段收编内放行（点后走星辰播放器迅雷引擎，
        // 用户拍板：海阔磁力走星辰，不维持原版）；ftp:// 不在放行之列
        // （放行口径见 HkRouter.isP2pPlayLink 的播放门）。
        // 第一遍换官形结构（账 §十）：集数只从 col_type 连续段里收（段外
        // 条目结构上进不来），段内再以干跑 ACTION 标复核（无锚场景的实测
        // 兜底，账 §九）——旧「整页扫＋资格判定五关」整段摘除。
        java.util.Map<String, List<HkDetailItem>> byLine = new java.util.LinkedHashMap<>();
        for (HkDetailItem di : HkChapters.collectEpisodeRuns(detail.getRawItems())) {
            if (di.getDryVerdict() == HkEpisodeDryClassifier.Verdict.ACTION) continue;
            String lineName = di.getLine() == null || di.getLine().isEmpty() ? "默认" : di.getLine();
            byLine.computeIfAbsent(lineName, k -> new ArrayList<>()).add(di);
        }
        // 第二遍：逐线路分拣（HkEpisodeSorter：剔图片直链/详情自链/标签单例，治本通用层），
        // 输入只是第一遍已过资格判定的候选——聚类只在集数区内整理，不再对整页条目运行；
        // 全被剔光的线路不建空 Flag。
        String pageUrl = detail.getPageUrl() != null && !detail.getPageUrl().isEmpty()
                ? detail.getPageUrl() : (itemUrl == null ? "" : itemUrl);
        List<Flag> flags = new ArrayList<>();
        for (java.util.Map.Entry<String, List<HkDetailItem>> e : byLine.entrySet()) {
            List<HkDetailItem> items = e.getValue();
            List<String> urls = new ArrayList<>(items.size());
            for (HkDetailItem di : items) urls.add(di.getUrl() == null ? "" : di.getUrl().trim());
            boolean[] keep = HkEpisodeSorter.select(urls, pageUrl);
            // 分拣后的候选表（保序）：集名/地址/回退名标记/原始标题
            List<String> candNames = new ArrayList<>();
            List<String> candUrls = new ArrayList<>();
            List<Boolean> candFallback = new ArrayList<>();
            List<String> candTitles = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                if (!keep[i]) continue;
                HkDetailItem di = items.get(i);
                boolean fallbackName = di.getTitle() == null || di.getTitle().isEmpty();
                candNames.add(fallbackName ? ("第" + (candNames.size() + 1) + "集") : di.getTitle());
                candUrls.add(urls.get(i));
                candFallback.add(fallbackName);
                candTitles.add(di.getTitle() == null ? "" : di.getTitle());
            }
            if (candUrls.isEmpty()) continue;
            // 同片多址归并（治本通用层，与章节链共用 HkEpisodeMerger）：分拣治「该剔的」，
            // 归并治「该并的」——同组条目折成一集、组内地址展开成同名同序的多条线路。
            // 判定窄口径、拿不准不并；不并时与改前逐字一致地建单个 Flag。
            HkEpisodeMerger.Plan plan = HkEpisodeMerger.plan(candUrls, candTitles, -1);
            if (!plan.merge) {
                Flag flag = Flag.create(e.getKey());
                flags.add(flag);
                for (int i = 0; i < candUrls.size(); i++) {
                    Episode episode = Episode.create(candNames.get(i), candUrls.get(i));
                    // 源没给集名的回退序号必须打标（与 Flag/VideoActivity 推送直建同口径）：
                    // Vod.setFlags 末尾 applySingleEpisodeTitle 据此把单集选集显示换成片名全名，
                    // 未打标时海阔单集内容选集只剩「第1集」（他 2026-10-23 单集显名定案）。
                    if (candFallback.get(i)) episode.setNumberName(true);
                    flag.getEpisodes().add(episode);
                }
                continue;
            }
            // 收缩集表：每集代表（折叠集取线路序首位、非折叠集取自身），
            // 每集在各线路下的地址下标（折叠集＝组内成员序，非折叠集恒为自身）。
            int episodeCount = 0;
            for (int v : plan.episodeIndexOf) episodeCount = Math.max(episodeCount, v + 1);
            boolean[] inGroup = new boolean[candUrls.size()];
            int[] repOf = new int[episodeCount];
            java.util.Arrays.fill(repOf, -1);
            int[][] urlIndexOf = new int[episodeCount][];
            for (HkEpisodeMerger.Group g : plan.groups) {
                int epIdx = plan.episodeIndexOf[g.members[0]];
                repOf[epIdx] = g.members[0];
                urlIndexOf[epIdx] = g.members;
                for (int m : g.members) inGroup[m] = true;
            }
            for (int i = 0; i < candUrls.size(); i++) {
                int epIdx = plan.episodeIndexOf[i];
                if (!inGroup[i] && repOf[epIdx] < 0) repOf[epIdx] = i;
                if (urlIndexOf[epIdx] == null) urlIndexOf[epIdx] = new int[]{i};
            }
            for (int line = 0; line < plan.lineNames.length; line++) {
                Flag flag = Flag.create(plan.lineNames[line]);
                flags.add(flag);
                for (int j = 0; j < episodeCount; j++) {
                    int[] srcs = urlIndexOf[j];
                    int src = srcs[Math.min(line, srcs.length - 1)];
                    Episode episode = Episode.create(candNames.get(repOf[j]), candUrls.get(src));
                    if (candFallback.get(repOf[j])) episode.setNumberName(true);
                    flag.getEpisodes().add(episode);
                }
            }
        }
        vod.setFlags(flags);
        return vod;
    }
}
