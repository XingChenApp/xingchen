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

    /**
     * 点击下标一次性承接槽（face1#4 尾巴，簇3；2026-11-15 身份绑定断根，
     * 账 hk-player-seam-audit-20261115 毛病8）：HkRouter.playRoute→
     * resolveLazyRule 链的 MY_POSITION 取值在点击方（列表/选集面）手里，
     * play 标准入参（key/flag/id）不带下标、api/hk 层无现成载体可达。
     * 点击方在发起播放前调 {@link #setClickPosition(int, String, String)}
     * 放下标（带规则名＋被点集址身份），HkRouter.playRoute 入口以
     * {@link #consumeClickPosition(String, String)} 按身份取走（读后
     * 复位 -1）；未放/身份不合时恒 -1＝官 LazyRuleParser.parse 无
     * position 重载的缺省实参。
     *
     * <p>旧形是裸 int 全局单槽：无身份、无清口——预热/历史预解析/下一集
     * 预加载等推测性解析与真播同抢一槽（预热先行时真播丢位），被分流
     * 的起播（小说早分流等）又留残值被无关解析领走。现形三件：①身份
     * 不合即残值，首个接触者清掉回 -1；②推测性解析（playerContentParseOnly
     * 与 HkProxyPrewarm）经 begin/endClickPositionSuppressed 抑制对包裹，
     * 期间 consume 不领不清（真播的槽留给真播）；③被分流/失败的起播经
     * {@link #clearClickPosition} 主动清口，不留残值。</p>
     */
    private static volatile int clickPosition = -1;
    private static volatile String clickPositionRule = "";
    private static volatile String clickPositionUrl = "";
    private static final ThreadLocal<Boolean> CLICK_POSITION_SUPPRESSED =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static void setClickPosition(int position, String ruleTitle, String episodeUrl) {
        clickPositionRule = ruleTitle == null ? "" : ruleTitle;
        clickPositionUrl = episodeUrl == null ? "" : episodeUrl;
        clickPosition = position;
    }

    public static int consumeClickPosition(String ruleTitle, String episodeUrl) {
        if (Boolean.TRUE.equals(CLICK_POSITION_SUPPRESSED.get())) return -1;
        int p = clickPosition;
        String r = clickPositionRule;
        String u = clickPositionUrl;
        clearClickPosition();
        if (p < 0) return -1;
        boolean match = (ruleTitle == null ? "" : ruleTitle).equals(r)
                && (episodeUrl == null ? "" : episodeUrl).equals(u);
        return match ? p : -1;
    }

    /** 起播被分流/失败等不经 playRoute 的收口：主动清槽，不留残值。 */
    public static void clearClickPosition() {
        clickPosition = -1;
        clickPositionRule = "";
        clickPositionUrl = "";
    }

    /**
     * 推测性解析抑制对（begin/end 须同线程 try/finally 配对）：抑制期间
     * consumeClickPosition 恒回 -1 且不清槽——预热/预解析/预加载不是
     * 真播，不许领走（也不许销毁）真播的点击位。begin 回旧值供 end 还原
     * （嵌套安全）。
     */
    public static boolean beginClickPositionSuppressed() {
        boolean prev = Boolean.TRUE.equals(CLICK_POSITION_SUPPRESSED.get());
        CLICK_POSITION_SUPPRESSED.set(Boolean.TRUE);
        return prev;
    }

    public static void endClickPositionSuppressed(boolean prev) {
        CLICK_POSITION_SUPPRESSED.set(prev);
    }

    /**
     * 直达历史行形态标记（毛病2，hk-player-seam-audit-20261115）：直达行
     * 的形态（直达 vs 详情）旧形靠「行内 episodeUrl 与键中 id 相等」自证，
     * 而 episodeUrl 每次切集都被改写——形态随之翻转、再入走错门。形态
     * 改记在键的 id 段前缀上：键由 getHistoryKey 在建行时一次写定、播放
     * 期从不改写，是行里唯一不动的字段。未带标记的旧行仍回退旧等值判
     * （未翻转过的旧行判得对；已被翻转的旧行信息已失、无从恢复）。
     */
    public static final String DIRECT_KEY_MARK = "hkdirect:";

    /**
     * 品类标记（再入四断点 乙/丙，账 history-reentry-loss-20261115）：借道
     * 直达行建行时把首入分流的品类裁决一并写进键 id 段（紧随直达标记），
     * 再入先读标记定归属——首入按条目级信号（claimSignal 等）分流、再入
     * 只剩整规则级判据时，条目级借道（普通视频规则帖内的「播放音频」钮）
     * 必被判回视频。整规则判据只作无标记旧行的回退。值与 HkFavorite.kind
     * 同字面，两处共用。
     */
    public static final String KIND_KEY_MARK = "hkkind:";
    public static final String KIND_VIDEO = "video";
    public static final String KIND_NOVEL = "novel";
    public static final String KIND_AUDIO = "audio";
    public static final String KIND_MUSIC = "music";

    public static boolean isDirectKeyId(String keyId) {
        return keyId != null && keyId.startsWith(DIRECT_KEY_MARK);
    }

    /** 键 id 段的品类标记（无标记回 ""）：直达标记之后紧随 hkkind:<kind>:。 */
    public static String kindOfKeyId(String keyId) {
        if (keyId == null) return "";
        String rest = keyId.startsWith(DIRECT_KEY_MARK)
                ? keyId.substring(DIRECT_KEY_MARK.length()) : keyId;
        if (!rest.startsWith(KIND_KEY_MARK)) return "";
        int end = rest.indexOf(':', KIND_KEY_MARK.length());
        return end < 0 ? "" : rest.substring(KIND_KEY_MARK.length(), end);
    }

    /** 剥键标记（直达＋品类）回原始 id：标记只活在键里，不许漏进地址字段。 */
    public static String stripDirectKeyMark(String keyId) {
        if (keyId == null) return "";
        String rest = keyId;
        if (rest.startsWith(DIRECT_KEY_MARK)) {
            rest = rest.substring(DIRECT_KEY_MARK.length());
        }
        if (rest.startsWith(KIND_KEY_MARK)) {
            int end = rest.indexOf(':', KIND_KEY_MARK.length());
            if (end >= 0) rest = rest.substring(end + 1);
        }
        return rest;
    }

    /** 行形态判定（三处共用）：键带直达标记，或（旧行）集址与键 id 相等。 */
    public static boolean isDirectForm(String keyId, String episodeUrl) {
        if (isDirectKeyId(keyId)) return true;
        return episodeUrl != null && !episodeUrl.isEmpty() && episodeUrl.equals(keyId);
    }

    /**
     * 单集选集载荷（再入断点甲的最低形）：直达行只存得下被点那一集的
     * 线名/集名/集址，再入按行自带字段拼成扁平载荷 [{name,url}] 交
     * startHkPlay——播放器摆盘至少有被点那一集与真线名（线名走 hk_line
     * 交接参），不再传空载荷让装配点整盘收手。历史/收藏两处共用。
     */
    public static String singleEpisodePayload(String episodeName, String episodeUrl) {
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("name", episodeName == null ? "" : episodeName);
            o.put("url", episodeUrl == null ? "" : episodeUrl);
            return new org.json.JSONArray().put(o).toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * 集址是 PlayData JSON（urls ≥2）时展开成逐线条目（线名取 PlayData
     * 线名）；非 PlayData 或单线回 null（原条目照旧）。线名归一口径与
     * HkListActivity.namesOf 同形（空名补「线路N」、重名后缀序号）——
     * main 源集够不到 mobile 的 namesOf，此处同口径复刻，两路自此同名。
     */
    private static java.util.List<HkDetailItem> expandPlayDataItem(HkDetailItem di) {
        String raw = di.getUrl() == null ? "" : di.getUrl().trim();
        if (!raw.startsWith("{")) return null;
        HkPlayData pd = HkHttpParser.getPlayData(raw);
        if (pd == null || pd.getUrls() == null || pd.getUrls().size() < 2) return null;
        java.util.List<String> names = new ArrayList<>();
        java.util.Set<String> used = new java.util.HashSet<>();
        for (int i = 0; i < pd.getUrls().size(); i++) {
            String n = pd.getNames() != null && i < pd.getNames().size() ? pd.getNames().get(i) : "";
            n = n == null ? "" : n.trim();
            if (n.isEmpty()) n = "线路" + (i + 1);
            if (!used.add(n)) n = n + " " + (i + 1);
            used.add(n);
            names.add(n);
        }
        java.util.List<HkDetailItem> out = new ArrayList<>();
        for (int i = 0; i < pd.getUrls().size(); i++) {
            HkDetailItem copy = new HkDetailItem();
            copy.setTitle(di.getTitle());
            copy.setPic(di.getPic());
            copy.setDesc(di.getDesc());
            copy.setColType(di.getColType());
            copy.setUrl(HkHttpParser.expandLineUrl(pd, raw, i));
            copy.setLine(names.get(i));
            out.add(copy);
        }
        return out;
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
        // 条目结构上进不来）——旧「整页扫＋资格判定五关」整段摘除。
        // 准入门（账 §十三，他 2026-11-13 准入规矩）：段过
        // HkChapters.admitEpisodeRuns 才许进表（认段级结构，不逐条判
        // 形态），干跑 ACTION 的剔除归准入门一处所有。
        // 第二遍分拣（HkEpisodeSorter：剔图片直链/详情自链/标签单例，治本
        // 通用层）以（段，线路）为单位运行（账 §十一）：聚类只在段内整理，
        // 不许跨段并簇；分拣后的幸存者再按线路合并建 Flag（同名线路一面）。
        // 全被剔光的线路不建空 Flag。
        String pageUrl = detail.getPageUrl() != null && !detail.getPageUrl().isEmpty()
                ? detail.getPageUrl() : (itemUrl == null ? "" : itemUrl);
        java.util.Map<String, List<HkDetailItem>> byLine = new java.util.LinkedHashMap<>();
        for (List<HkDetailItem> run : HkChapters.admitEpisodeRuns(
                HkChapters.collectEpisodeRuns(detail.getRawItems()), pageUrl)) {
            java.util.Map<String, List<HkDetailItem>> runByLine = new java.util.LinkedHashMap<>();
            for (HkDetailItem di : run) {
                // 毛病3(iii)：集址本身是 PlayData 多线 JSON 时，收编期就按
                // 线展开成逐线条目（线名＝PlayData 线名）——旧形整串塞进
                // 条目自带的那一条线，播放时恒取首线、其余线（连带各自
                // 请求头）结构上不可达。展开走 HkHttpParser.expandLineUrl，
                // 与列表路同一打包口径。
                java.util.List<HkDetailItem> expanded = expandPlayDataItem(di);
                if (expanded != null) {
                    for (HkDetailItem ex : expanded) {
                        runByLine.computeIfAbsent(ex.getLine(), k -> new ArrayList<>()).add(ex);
                    }
                    continue;
                }
                String lineName = di.getLine() == null || di.getLine().isEmpty() ? "默认" : di.getLine();
                runByLine.computeIfAbsent(lineName, k -> new ArrayList<>()).add(di);
            }
            for (java.util.Map.Entry<String, List<HkDetailItem>> re : runByLine.entrySet()) {
                List<HkDetailItem> items = re.getValue();
                List<String> urls = new ArrayList<>(items.size());
                for (HkDetailItem di : items) urls.add(di.getUrl() == null ? "" : di.getUrl().trim());
                boolean[] keep = HkEpisodeSorter.select(urls, pageUrl);
                List<HkDetailItem> keptLine = byLine.computeIfAbsent(re.getKey(), k -> new ArrayList<>());
                for (int i = 0; i < items.size(); i++) {
                    if (keep[i]) keptLine.add(items.get(i));
                }
            }
        }
        List<Flag> flags = new ArrayList<>();
        for (java.util.Map.Entry<String, List<HkDetailItem>> e : byLine.entrySet()) {
            List<HkDetailItem> items = e.getValue();
            // 候选表（保序）：集名/地址/回退名标记/原始标题
            List<String> candNames = new ArrayList<>();
            List<String> candUrls = new ArrayList<>();
            List<Boolean> candFallback = new ArrayList<>();
            List<String> candTitles = new ArrayList<>();
            for (HkDetailItem di : items) {
                boolean fallbackName = di.getTitle() == null || di.getTitle().isEmpty();
                candNames.add(fallbackName ? ("第" + (candNames.size() + 1) + "集") : di.getTitle());
                candUrls.add(di.getUrl() == null ? "" : di.getUrl().trim());
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
