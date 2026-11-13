package com.fongmi.android.tv.api.hk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 海阔详情选集分拣（纯逻辑，无 Android 依赖，可 JVM 单测）。
 *
 * 背景：规则详情页把整页链接一并返回，桥（HkDetailBridge）旧逻辑只认「是 http 链接」
 * 就收进选集，于是标签/栏目/页内锚点（影片介绍、用户评价、日本、演员名……）和
 * 海报展示卡（url 即封面图）全混进选集（用户 2026-10-23 两张真机截图实证）。
 * 本类在桥的通用层按链接与本片的关联分拣，一次覆盖全部规则，不针对单个小程序。
 *
 * 口径（用户 2026-10-23 定案，2026-11-07 两刀补订）：
 * 0. 地址带 @rule= 的条目硬剔（2026-11-07 刀一）：8.83 语义里 @rule= 是用
 *    子规则开新列表页的导航入口，永不可能是选集——旧形把它与 @lazyRule=
 *    混进「规则型永远保留」，是标签混进选集的误分类之一（诊断账
 *    bridge-tag-episodes-20261107 M2）；
 * 1. 图片直链（路径以图片后缀结尾）不进选集；
 * 2. 与详情页自身 URL 相同（去 fragment 后相等）的自链不进选集；
 * 3. 剩余 http 条目按 URL 模板聚类（路径中纯数字/长 id 段归一化、查询只看键名）：
 *    同线路内若存在 ≥2 条的同模板簇，优先保留 URL 含本片编号（详情页 URL 提取的主数字 id）的簇，
 *    无 id 命中时保留最大簇（并列最大都留），其余单例剔除；
 *    若全是单例（无任何 ≥2 簇）：原则整线路保持原样全收，不误删单集源；
 *    例外是编号门（2026-11-07 刀二，治词段标签全单例形态 M1）——线路内
 *    已有 ≥1 条含本片编号的条目、且落单的无编号条目 ≥2 条时，只保留含
 *    编号者；无任何条目含编号、或无编号落单只有 1 条时维持全收（单集源
 *    保护不动，不拿标题文案猜）；
 * 4. 带规则标记的条目（@lazyRule= / js: / magnet: / ed2k: / thunder:)
 *    是引擎解析型入口，永远保留，不参与聚类剔除。此「保留」只对已过
 *    段收编（HkChapters 连续段＋干跑 ACTION 复核，账 §十）的候选成立，
 *    不是对整页解析型条目的无条件放行。
 *
 * 输入 urls 是段收编后的同一线路候选，保序；本类的聚类/编号门只在
 * 候选内整理，不再充当整页扫入的过滤层（钉根账 playpage-episode-pollution-dig-20261111）。
 * 返回与输入等长的 keep 标记。
 */
public final class HkEpisodeSorter {

    private HkEpisodeSorter() {
    }

    private static final String[] IMAGE_EXTS = {
            ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".svg", ".avif", ".ico"
    };

    public static boolean[] select(List<String> urls, String pageUrl) {
        int n = urls == null ? 0 : urls.size();
        boolean[] keep = new boolean[n];
        if (n == 0) return keep;
        String self = stripFragment(pageUrl == null ? "" : pageUrl.trim());
        // 第一遍：分类＋图片/自链硬剔
        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String url = urls.get(i) == null ? "" : urls.get(i).trim();
            String low = url.toLowerCase();
            // @rule= 硬剔（刀一）：子列表导航入口、永不是选集，
            // 先于一切保留口径判——同址另挂 @lazyRule= 也不救
            // （地址带 @rule= 即按导航入口论）。
            if (url.contains("@rule=")) continue;
            if (isRuleEntry(url, low)) {
                keep[i] = true;
                continue;
            }
            if (!low.startsWith("http://") && !low.startsWith("https://")) {
                keep[i] = true; // 非 http 异类（如 ftp）保守保留，口径外不处理
                continue;
            }
            if (isImageLink(low)) continue;
            if (!self.isEmpty() && stripFragment(url).equals(self)) continue;
            pool.add(i);
        }
        if (pool.isEmpty()) return keep;
        // 第二遍：模板聚类
        Map<String, List<Integer>> clusters = new LinkedHashMap<>();
        for (int i : pool) {
            clusters.computeIfAbsent(template(urls.get(i)), k -> new ArrayList<>()).add(i);
        }
        int maxSize = 0;
        for (List<Integer> c : clusters.values()) maxSize = Math.max(maxSize, c.size());
        if (maxSize < 2) {
            // 编号门（刀二，治全单例 M1）：词段型标签与真播放
            // 条目各自成模板、全单例时旧形整线全收，标签就此
            // 混进选集。保守门：本片编号可提取、线路内已有含
            // 编号条目（≥1）、且落单的无编号条目 ≥2 时，只留
            // 含编号者（含单例）；任一条件不齐维持全收——无
            // 编号可依时不猜，落单只有 1 条时不剔（可能就是
            // 单集的另一种地址形态）。
            String gateId = extractMainId(pageUrl);
            if (gateId != null) {
                int withId = 0, withoutId = 0;
                for (int i : pool) {
                    if (containsIdToken(urls.get(i), gateId)) withId++;
                    else withoutId++;
                }
                if (withId >= 1 && withoutId >= 2) {
                    for (int i : pool) {
                        if (containsIdToken(urls.get(i), gateId)) keep[i] = true;
                    }
                    return keep;
                }
            }
            for (int i : pool) keep[i] = true;
            return keep;
        }
        // 有 ≥2 簇：优先含本片编号的簇，其次最大簇
        String mainId = extractMainId(pageUrl);
        List<List<Integer>> chosen = new ArrayList<>();
        if (mainId != null) {
            for (List<Integer> c : clusters.values()) {
                if (c.size() < 2) continue;
                for (int i : c) {
                    if (containsIdToken(urls.get(i), mainId)) {
                        chosen.add(c);
                        break;
                    }
                }
            }
        }
        if (chosen.isEmpty()) {
            for (List<Integer> c : clusters.values()) {
                if (c.size() == maxSize) chosen.add(c);
            }
        }
        for (List<Integer> c : chosen) {
            for (int i : c) keep[i] = true;
        }
        return keep;
    }

    /**
     * 引擎解析型入口：带解析标记或非 http 播放协议，永远保留。
     * @rule= 不在此列（刀一）：它是子列表导航标记、已在第一遍
     * 硬剔，与 @lazyRule= 的播放解析语义正相反，不许再混同。
     */
    private static boolean isRuleEntry(String url, String low) {
        return url.contains("@lazyRule=")
                || url.startsWith("js:")
                || low.startsWith("magnet:") || low.startsWith("ed2k:") || low.startsWith("thunder:");
    }

    /** 图片直链：去掉查询/fragment 后路径以图片后缀结尾。 */
    private static boolean isImageLink(String lowUrl) {
        String s = stripFragment(lowUrl);
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        for (String ext : IMAGE_EXTS) {
            if (s.endsWith(ext)) return true;
        }
        return false;
    }

    private static String stripFragment(String url) {
        if (url == null) return "";
        int h = url.indexOf('#');
        return (h >= 0 ? url.substring(0, h) : url).trim();
    }

    /**
     * URL 模板：scheme://host 保留，路径逐段归一化（纯数字/长 id 段→{}，
     * 带扩展名的段只归一化主干），查询只保留排序后的键名集合（值因集而异，不进模板）。
     */
    static String template(String url) {
        String s = stripFragment(url == null ? "" : url.trim());
        String query = "";
        int q = s.indexOf('?');
        if (q >= 0) {
            query = s.substring(q + 1);
            s = s.substring(0, q);
        }
        StringBuilder out = new StringBuilder();
        int schemeEnd = s.indexOf("://");
        String rest = s;
        if (schemeEnd >= 0) {
            out.append(s, 0, schemeEnd + 3);
            rest = s.substring(schemeEnd + 3);
        }
        int slash = rest.indexOf('/');
        String host = slash >= 0 ? rest.substring(0, slash) : rest;
        out.append(host.toLowerCase());
        String path = slash >= 0 ? rest.substring(slash) : "";
        for (String seg : path.split("/", -1)) {
            out.append('/').append(normalizeSegment(seg));
        }
        if (!query.isEmpty()) {
            List<String> keys = new ArrayList<>();
            for (String pair : query.split("&")) {
                if (pair.isEmpty()) continue;
                int eq = pair.indexOf('=');
                keys.add(eq >= 0 ? pair.substring(0, eq) : pair);
            }
            keys.sort(null);
            out.append('?').append(String.join("&", keys));
        }
        return out.toString();
    }

    private static String normalizeSegment(String seg) {
        if (seg.isEmpty()) return seg;
        int dot = seg.lastIndexOf('.');
        if (dot > 0 && seg.length() - dot <= 6) {
            String stem = seg.substring(0, dot);
            if (isIdLike(stem)) return "{}" + seg.substring(dot);
            return seg;
        }
        return isIdLike(seg) ? "{}" : seg;
    }

    /**
     * id 型路径段：纯数字；或含 ≥4 位连续数字（如 play-12345-1-1 的主干）；
     * 或长度 ≥10 的字母数字混合段（长随机 id）。普通词（tag、play、video）不命中。
     */
    private static boolean isIdLike(String s) {
        if (s.isEmpty()) return false;
        boolean allDigit = true;
        int run = 0, maxRun = 0;
        boolean hasDigit = false, hasLetter = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                hasDigit = true;
                maxRun = Math.max(maxRun, ++run);
            } else {
                run = 0;
                allDigit = false;
                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) hasLetter = true;
            }
        }
        if (allDigit) return true;
        if (maxRun >= 4) return true;
        return s.length() >= 10 && hasDigit && hasLetter;
    }

    /** 从详情页 URL 提取主数字 id：路径＋查询里最长的 ≥3 位数字串（并列取第一个）。 */
    static String extractMainId(String pageUrl) {
        if (pageUrl == null) return null;
        String s = stripFragment(pageUrl.trim());
        String best = null;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                int j = i;
                while (j < s.length() && s.charAt(j) >= '0' && s.charAt(j) <= '9') j++;
                String run = s.substring(i, j);
                if (run.length() >= 3 && (best == null || run.length() > best.length())) best = run;
                i = j;
            } else {
                i++;
            }
        }
        return best;
    }

    /** url 的非字母数字分词里是否有与 id 完全相等的一段（避免 123 命中 1234）。 */
    static boolean containsIdToken(String url, String id) {
        if (url == null || id == null) return false;
        for (String token : url.split("[^A-Za-z0-9]+")) {
            if (token.equals(id)) return true;
        }
        return false;
    }
}
