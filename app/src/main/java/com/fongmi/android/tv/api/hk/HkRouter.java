package com.fongmi.android.tv.api.hk;

import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.SourceGate;
import com.fongmi.android.tv.ui.hk.HkEngineManager;
import com.fongmi.android.tv.ui.hk.HkIWebViewParser;
import com.orhanobut.logger.Logger;

import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 海阔规则引擎路由层（M3，设计文档 §4.5）。
 *
 * <p>纯数据 API，供上层调用：</p>
 * <ul>
 *   <li>{@link #home} / {@link #search} → HkPageActivity（列表/搜索）</li>
 *   <li>{@link #detail} → VideoActivity 海阔分支（详情/线路/选集）</li>
 *   <li>{@link #play} → VideoActivity 播放前解析（M4 完整分流链）</li>
 * </ul>
 */
public class HkRouter {

    private static final String TAG = "HkRouter";

    private final HkRule rule;

    private final HkEngine engine;

    public HkRouter(HkRule rule) throws Exception {
        this.rule = rule;
        // 修法 A（b12 分类点不进去定案，见 ~/workspace/oneapp-reopen-diagnosis.md）：
        // 点击求值（putMyVar 写分类号）必须与页面刷新求值落在同一个运行时上。
        // 8.83 JSEngine 是进程单例；星辰对应物是 HkEngineManager 的常驻运行时。
        // 之前这里每次 new HkEngine → 写入随一次性实例丢弃，刷新读到默认值永远回首页。
        // ensureInit 每实例只跑一次（不重复 init、不清变量表）；destroy 由 HkEngine
        // 的 adopted 标记护住，常驻运行时永不销毁。
        HkJsRuntime rt = HkEngineManager.runtimeFor(rule);
        HkEngineManager.ensureInit(rt);
        this.engine = HkEngine.wrapResident(rule, rt);
    }

    /** 按规则名打开路由（规则不存在抛异常）。 */
    public static HkRouter open(String title) throws Exception {
        HkRule r = HkRuleManager.get().getRule(title);
        if (r == null) throw new IllegalArgumentException("hk rule not found: " + title);
        return new HkRouter(r);
    }

    public HkRule getRule() {
        return rule;
    }

    public HkEngine getEngine() {
        return engine;
    }

    /** 首页/分类列表。海阔分类即 url 替换词，直接复用 home。 */
    public List<HkItem> home(int page, String cls, String area, String year, String sort) {
        return engine.home(page, cls, area, year, sort);
    }

    /** 单页内置搜索。8.83 同款：返回独立 SearchResult。 */
    public List<SearchResult> search(String keyword, int page) {
        return engine.search(keyword, page);
    }

    /**
     * V4 详情：先看条目 URL 自带逻辑，再看规则的 detail_find_rule。
     *
     * <p>98/115 条规则的 detail_find_rule 为空，V4 全靠条目 URL 分流：</p>
     * <ol>
     *   <li>{@code @lazyRule=}（如大哥视频）：先求值；结果含 {@code #isVideo=true#}
     *       → 置 directPlayUrl 标记，调用方跳过 V4 直接播放；否则用求值结果 URL 继续；</li>
     *   <li>{@code @rule=js:}（如 MissAV）：{@code @rule=} 前为 pageUrl（MY_URL），
     *       js 部分求值出详情；</li>
     *   <li>纯 {@code js:}（$.toString 生成）：求值得到真实 URL 后继续；</li>
     *   <li>最后走原有 detail_find_rule 流程。</li>
     * </ol>
     */
    public HkDetail detail(String itemUrl, HkItem item, boolean fromSearch) {
        HkDetail detail = new HkDetail();
        if (item != null) {
            detail.setTitle(item.getTitle());
            detail.setPic(item.getPic());
            detail.setContent(item.getDesc());
        }
        try {
            rule.validate();
            // 环15：详情三件套（find_rule＋col_type）的取段在构造入口一次定死——
            // 搜索来路取 HkSearchEngine.fromRule 折叠后的 engine（全包唯一折叠点，
            // sdetail=="*"→detail 段、否则 sdetail 段），普通来路取规则 detail 段
            // 原值。旧形 fromSearch 只在 effectiveDetailRule 里换 find_rule 一件、
            // col_type 恒走 detail 段，sdetail_col_type 从未被消费。
            HkSearchEngine foldedEngine = fromSearch ? HkSearchEngine.fromRule(rule) : null;
            final String detailFindRule = foldedEngine != null
                    ? foldedEngine.getDetail_find_rule() : rule.getDetailFindRule();
            final String detailColType = foldedEngine != null
                    ? foldedEngine.getDetail_col_type() : rule.getDetailColType();
            String u = itemUrl == null ? "" : itemUrl.trim();

            // P1：hiker://page/ 子页面不进 V4（应由 openPage 打开新列表页）；
            // 若走到这里说明调用方漏拦截，直接返回空详情避免"0条线路"空 V4。
            if (u.startsWith("hiker://page/")) {
                detail.setLazyParseFailed(true);
                return detail;
            }

            // ① @lazyRule= 条目：先求值 → dealWithUrl 分流（官方：不进 V4）
            int lr = u.indexOf("@lazyRule=");
            if (lr >= 0) {
                // 官方 clickItem → dealLazyRule：分类切换类回调（putMyVar + refreshPage）
                // 只起副作用，靠 OnRefreshPageEvent 重刷列表，不进 V4。这里做"变化检测"：
                // 仅当本次求值新产生了 refreshPage 请求时才标记为分类切换，
                // 避免把之前残留的标记误判（consume 保证每次只消费一次）。
                boolean hadRefresh = engine.getJsRuntime().isRefreshRequested();
                String v = evalEntryLazy(u, lr);
                if (engine.getJsRuntime().consumeRefreshRequest() && !hadRefresh) {
                    detail.setTabSwitch(true);
                    return detail;
                }
                // lazyRule 求值失败（null/空）：标记解析失败，调用方不应推空 V4
                if (v == null || v.trim().isEmpty() || "hiker://empty".equals(v.trim())) {
                    detail.setLazyParseFailed(true);
                    return detail;
                }
                String vt = v.trim();
                String kind = dealKind(vt);
                if (!kind.isEmpty()) {
                    // 官方 dealWithUrl：video 直接播放；pics/x5/web/image/magnet 按种类分流，均不进 V4
                    if ("video".equals(kind)) detail.setDirectPlayUrl(vt);
                    else {
                        detail.setDealKind(kind);
                        detail.setDealUrl(vt);
                    }
                    return detail;
                }
                // 动作 URL（如 toast://）：绝不能被当成直接播放地址。
                // 8.83 无 isEmptyDetailRule 兜底；星辰旧逻辑把 toast:// 误判为可播地址导致进播放器
                // （如星集Media"查看更新日志"按钮返回 toast://更新日志加载失败）。
                if (isActionUrl(vt)) {
                    detail.setActionUrl(vt);
                    return detail;
                }
                // 未知种类：回退旧逻辑（无详情规则则直接播放，否则继续 V4 流程）
                if (vt.contains("#isVideo=true#") || isEmptyDetailRule() || looksLikeMediaUrl(vt)) {
                    detail.setDirectPlayUrl(vt);
                    return detail;
                }
                // @lazyRule= 有结果但无法识别种类：如果结果是有效 HTTP URL，默认按直接播放处理，
                // 避免落入 V4 空详情（官方 dealWithUrl 对未知 http URL 走 web 分流，不进 V4）
                if (vt.toLowerCase().startsWith("http")) {
                    detail.setDirectPlayUrl(vt);
                    return detail;
                }
                // 非 http 结果也标记为解析失败，不进 V4 空页面
                detail.setLazyParseFailed(true);
                return detail;
            }

            // ② 纯 js: 条目（$.toString 生成）：求值得到真实 URL
            if (u.startsWith("js:")) {
                // 与 @lazyRule= 分支一致：本次求值新产生了 refreshPage 请求 → 标记分类切换，不进 V4
                boolean hadRefresh = engine.getJsRuntime().isRefreshRequested();
                String v = evalEntryJs(u);
                if (engine.getJsRuntime().consumeRefreshRequest() && !hadRefresh) {
                    detail.setTabSwitch(true);
                    return detail;
                }
                if (v != null && !v.trim().isEmpty()) {
                    String vt = v.trim();
                    // 文本型结果（非 URL、非规则标记）：设为 textResult，调用方弹窗显示，不进 V4/播放
                    // （如"查看更新日志"按钮；官方文本按钮语义）
                    String kind = dealKind(vt);
                    boolean isUrl = vt.toLowerCase().startsWith("http") || vt.contains("@rule=")
                            || vt.contains("@lazyRule=") || vt.contains("@inputRule=")
                            || vt.contains("@confirmRule=") || vt.startsWith("js:")
                            || !kind.isEmpty();
                    if (!isUrl) {
                        detail.setTextResult(vt);
                        return detail;
                    }
                    u = vt;
                } else {
                    // 纯 js: 求值空：与 ① @lazyRule= 分支一致，标记解析失败，
                    // 不带 js: 串落入详情规则流程（否则空详情/误播）。
                    detail.setLazyParseFailed(true);
                    return detail;
                }
            }

            // ③ @rule=js: 条目：pageUrl 为 MY_URL，js 部分求值出详情
            int ar = u.indexOf("@rule=");
            if (ar >= 0) {
                // 8.83 原版：传完整 URL 给 JS（含 # 后真实地址），clean 只给非 JS 分支用
                String rawPageUrl = u.substring(0, ar).trim();
                String pageUrl = cleanDetailUrl(rawPageUrl);
                String entryRule = u.substring(ar + 6).trim();
                if (entryRule.startsWith("js:")) {
                    if (item != null) engine.getJsRuntime().setDetailParams(item.getExtra());
                    // 8.83 原版：详情条目直接进通用列表渲染，不做线路/选集分类
                    List<HkDetailItem> jsItems = fromJsDetail(entryRule, rawPageUrl, detailColType);
                    if (jsItems == null || jsItems.isEmpty()) {
                        // @rule=js: 求值出空详情：与 ① 分支一致，标记解析失败，不推空 V4
                        detail.setLazyParseFailed(true);
                        return detail;
                    }
                    detail.setPageUrl(pageUrl);
                    detail.setRawItems(jsItems);
                    classifyDetailItems(detail);
                    return detail;
                }
                u = pageUrl;
            }

            // ④ 原有 detail_find_rule 流程（find_rule 已按来路取段：搜索来路
            // 是折叠后的 sdetail/detail 段，见构造入口）
            String ruleText = effectiveDetailRule(detailFindRule);
            if (ruleText == null || ruleText.trim().isEmpty()) {
                // 无详情规则：若条目 URL 本身形如媒体直链 → 直接播放，避免 V4"加载失败"
                String cu = u.trim();
                if (looksLikeMediaUrl(cu)) detail.setDirectPlayUrl(cu);
                // 同 @lazyRule= 分支的 dealWithUrl 口径：裸磁力／ed2k／thunder／ftp 不进 V4，
                // 以 dealKind=magnet 交调用方走星辰播放器（isP2pPlayLink 门），
                // 否则这里回空详情、调用方只能报"加载失败"。
                else if (isP2pPlayLink(cu)) {
                    detail.setDealKind("magnet");
                    detail.setDealUrl(cu);
                }
                return detail;
            }
            // 8.83 原版：JS 分支拿完整 URL（含 #），clean 只给选择器分支用
            String rawUrl = u;
            String url = cleanDetailUrl(u);
            List<HkDetailItem> items;
            if (HkSelector.isJsRule(ruleText)) {
                // 官方 dealRule：点击条目的 extra 作为新规则的 params（MY_PARAMS）
                if (item != null) engine.getJsRuntime().setDetailParams(item.getExtra());
                items = fromJsDetail(ruleText, rawUrl, detailColType);
            } else {
                String html = getMarked(url);
                items = engine.getSelector().parseDetail(html, ruleText, url);
                // 选择器解析的条目若无 col_type，回退到本发取段的 col_type
                //（搜索来路＝折叠后的 sdetail_col_type，官方 dealRule 语义）
                String defaultColType = detailColType;
                if (!defaultColType.isEmpty()) {
                    for (HkDetailItem it : items) {
                        if (it.getColType() == null || it.getColType().isEmpty()) {
                            it.setColType(defaultColType);
                        }
                    }
                }
            }
            // 8.83 原版：详情条目直接进通用列表渲染，不做线路/选集分类
            detail.setPageUrl(url);
            detail.setRawItems(items);
        } catch (Throwable e) {
            // 取数/解析失败不许带着只有基础信息的 detail 冒充成功回：
            // 唯一调用方 VideoActivity 对 detail() 本就 try/catch 并走
            // error_detail 错误页，此处抛出去让失败显形（批 residual）。
            Logger.t(TAG).d("detail failed: %s", e.getMessage());
            throw new IllegalStateException("海阔详情加载失败：" + e.getMessage(), e);
        }
        classifyDetailItems(detail);
        return detail;
    }

    /**
     * 收编干跑分类（账 playpage-episode-pollution-dig-20261111 §九/§十）：
     * 对段收编候选（可播形态＋未被四排除，门与 HkChapters 段口径同）中的
     * JS 型解析条目逐条干跑求值判类，结论记到条目瞬态字段；toVod 段内
     * 读标剔 ACTION。分类只写标不删条目——详情页本体渲染仍用全量
     * rawItems，动作条目在详情页可点、只是不进选集。干跑在引擎串行队列
     * 内逐条跑（远程 fetch 回空、纯本地求值），量级＝候选条目数。
     */
    private void classifyDetailItems(HkDetail detail) {
        try {
            if (detail == null || detail.getRawItems() == null) return;
            String pageUrl = detail.getPageUrl() == null ? "" : detail.getPageUrl();
            for (HkDetailItem di : detail.getRawItems()) {
                String u = di.getUrl() == null ? "" : di.getUrl().trim();
                // 打标门与 toVod 段收编候选同口径（账 §十）：可播形态＋未被
                // 四排除＋JS 型 lazy 才值得干跑；其余条目不会进选集、不必验。
                if (!HkChapters.isPlayForm(u) || HkChapters.isExcluded(di.getColType(), u)) continue;
                if (!HkEpisodeDryClassifier.isJsLazy(u)) continue;
                // 逐条隔离（账 §十一）：单条干跑抛错只废该条（留无标＝
                // UNKNOWN 口径保留），不许整环中止令其后条目全数无标——
                // 128 真机《艾伦2026》整页动作项无标漏进选集的头号嫌疑。
                try {
                    di.setDryVerdict(HkEpisodeDryClassifier.classifyLazyItem(
                            engine.getJsRuntime(), u, pageUrl));
                } catch (Throwable itemError) {
                    // 干跑抛错必须落 UNKNOWN 标（本环既定口径），不许留 null
                    // 与「未分类」同形（批 residual）。
                    di.setDryVerdict(HkEpisodeDryClassifier.Verdict.UNKNOWN);
                    Logger.t(TAG).d("classifyDetailItems item failed url=%s: %s",
                            u, itemError.getMessage());
                }
            }
        } catch (Throwable e) {
            Logger.t(TAG).d("classifyDetailItems failed: %s", e.getMessage());
            // 整环失败：还没落标的干跑候选统一补 UNKNOWN，与「分类成功、
            // 本就无标」区分开（批 residual）。
            if (detail != null && detail.getRawItems() != null) {
                for (HkDetailItem di : detail.getRawItems()) {
                    if (di.getDryVerdict() != null) continue;
                    String u = di.getUrl() == null ? "" : di.getUrl().trim();
                    if (!HkChapters.isPlayForm(u) || HkChapters.isExcluded(di.getColType(), u)) continue;
                    if (!HkEpisodeDryClassifier.isJsLazy(u)) continue;
                    di.setDryVerdict(HkEpisodeDryClassifier.Verdict.UNKNOWN);
                }
            }
        }
    }

    /**
     * 条目 URL 里的 {@code @lazyRule=} 求值（与 play() 的 resolveLazyRule 同逻辑，
     * 返回原始求值结果；失败返回 null）。
     */
    private String evalEntryLazy(String fullUrl, int lrIndex) {
        try {
            String pageUrl = fullUrl.substring(0, lrIndex).trim();
            String r = decodeConflict(fullUrl.substring(lrIndex + 10).trim()).trim();
            boolean js = false;
            if (r.startsWith(".js:")) {
                r = r.substring(4);
                js = true;
            } else if (r.startsWith("js:")) {
                r = r.substring(3);
                js = true;
            }
            if (js) return engine.getJsRuntime().evalLazy(r, pageUrl);
            String html = getMarked(pageUrl);
            return engine.getSelector().evalField(html, r, pageUrl);
        } catch (Throwable e) {
            Logger.t(TAG).d("entry lazyRule eval failed: %s", e.getMessage());
            return null;
        }
    }

    /** 纯 js: 条目求值（$.toString 生成），期望返回真实 URL 字符串。 */
    private String evalEntryJs(String u) {
        try {
            return engine.getJsRuntime().evalLazy(u.substring(3), u);
        } catch (Throwable e) {
            Logger.t(TAG).d("entry js eval failed: %s", e.getMessage());
            return null;
        }
    }

    /**
     * 播放地址完整分流链（M4，设计文档 §6）。
     *
     * <p>顺序：</p>
     * <ol>
     *   <li>剥 {@code #isVideo=true#} / {@code #noLoading#} 等页面标识；</li>
     *   <li>拆 {@code ;} URL 增强（{@code url;method;encoding;{H@V&&H2@V2}}），header 进结果；</li>
     *   <li>{@code video://} 剥协议；</li>
     *   <li>{@code @lazyRule=}：{@code .js:} 走 JS 独立作用域求值，否则按选择器对剧集页提地址；</li>
     *   <li>{@code @rule=js:}：按 JS 求值；</li>
     *   <li>{@code js:} 整段：按 JS 求值；</li>
     *   <li>{@code {urls:[],names:[],headers:[],subtitle}} JSON：取首地址 + headers；</li>
     *   <li>直链或兜底：清理后的原样地址（播放器/嗅探继续处理）。</li>
     * </ol>
     *
     * <p>分流本体（playRoute）的全部出口最后统一过 {@link #checkPlayExit}
     * 出口收口：动作形态与不可播 scheme 不许原样交播放器。</p>
     */
    public HkPlay play(String episodeUrl) {
        return play(episodeUrl, null);
    }

    /**
     * 环34（硬门账 D §6.1）＋复核订正 B4：带条目 extra 的播放——官
     * X5WebViewParser.parse0 的 extra 是形参栈内值、随调用链传递；咱家同形：
     * itemExtra 只作形参经 playRoute → resolveWebRule / resolveLazyRule /
     * applyLazyResult 逐段传递，不落实例字段（旧形 playItemExtra 字段透传：
     * Router 按规则复用时，并发/重入的两次 play 会互相覆写、串 extra）。
     * extra 只由持条目的调用方传入（HkPageActivity 三个播放入口），解析器
     * 内不自造 extra 来源。
     */
    public HkPlay play(String episodeUrl, String itemExtra) {
        HkPlay out = playRoute(episodeUrl, itemExtra);
        checkPlayExit(out);
        return out;
    }

    /**
     * 播放出口收口（普查 G4/G5，hk-handover-audit-20261031）：playRoute 的每条
     * 返回路（含 applyLazyResult 直接 setUrl 的懒结果路——不经步骤 8 兜底）
     * 都在这里过同一张表：
     * <ul>
     *   <li>动作形态（{@link #isActionUrl}：toast:// / copy:// / input:// /
     *       confirm:// / select:// / download:// / hiker://empty）——与详情
     *       预解析路同等拦截：动作 URL 绝不能被当成直接播放地址（橘汁懒函数
     *       以 toast:// 回「取流失败」正是此形）。toast:// 的载荷就是给用户
     *       看的话，原样作报错文案；其余动作形态给通用文案。</li>
     *   <li>scheme 正表外的协议（hiker:// 导航串 home@/page/ 等 G5 漏网、
     *       以及任何未登记伪协议）——抛可见错「未解析成播放地址」，不再
     *       透传给播放器静默死（mpv 无此协议处理器、-13）。</li>
     * </ul>
     * 放行不动的：pics 漫画结果（地址位无意义、走阅读分流）、已标 playType
     * 的 P2P（ed2k/magnet/thunder/ftp 原样透传是既定语义）、空结果（维持
     * 既有空语义、各调用方自理）、无协议头的相对址/本地路径（播放器与嗅探
     * 的既有处理面）。
     */
    private void checkPlayExit(HkPlay out) {
        if (out == null) return;
        if (!out.getPics().isEmpty()) return;
        if (!out.getPlayType().isEmpty()) return;
        String u = out.getUrl();
        if (u == null || u.trim().isEmpty()) return;
        u = u.trim();
        if (isActionUrl(u)) {
            if (u.regionMatches(true, 0, "toast://", 0, "toast://".length())) {
                String msg = u.substring("toast://".length()).trim();
                if (!msg.isEmpty()) throw new IllegalStateException(msg);
            }
            throw new IllegalStateException("海阔规则返回了动作地址，无法直接播放，请换一条试试");
        }
        String scheme = schemeOf(u);
        if (scheme == null) return;
        if (PLAYABLE_SCHEMES.contains(scheme)) return;
        // P2P 四形未标 playType 时（如懒函数直回 magnet）同为合法终形：
        // hkPlayerContent 的 isP2pPlayLink 门会按实际地址分流迅雷边下边播
        if ("ed2k".equals(scheme) || "magnet".equals(scheme)
                || "thunder".equals(scheme) || "ftp".equals(scheme)) return;
        throw new IllegalStateException("海阔地址未解析成播放地址，请换一条试试");
    }

    /**
     * 地址的协议头（小写）：有 :// 取其头；只有冒号的（magnet:/ed2k: 型）头
     * 须是纯协议词（不含点号——带点号的是 host:port 形主机名、按无协议头
     * 处理）；都不合回 null（相对址/本地路径，维持透传）。
     */
    private static String schemeOf(String u) {
        int sep = u.indexOf("://");
        if (sep > 0) return u.substring(0, sep).toLowerCase();
        int colon = u.indexOf(':');
        if (colon > 0) {
            String head = u.substring(0, colon).toLowerCase();
            if (head.matches("[a-z][a-z0-9+-]*")) return head;
        }
        return null;
    }

    /** 出口正表：播放器/嗅探真能打开的协议。表外一律视为未解析（见 checkPlayExit）。 */
    private static final java.util.Set<String> PLAYABLE_SCHEMES = new java.util.HashSet<>(
            java.util.Arrays.asList("http", "https", "rtsp", "rtmp", "rtmps", "udp", "rtp",
                    "mms", "srt", "smb", "file", "content"));

    private HkPlay playRoute(String episodeUrl, String itemExtra) {
        HkPlay out = new HkPlay();
        if (episodeUrl == null) return out;
        String u = episodeUrl.trim();
        // -2. x5Play:// 剥壳（定根账 hk-sniff-route-miswire-20261113）：官家
        // x5Play 是播放器地址——PlayerChooser 三个 startPlayer 变体一律强制
        // 内置播放器、播放器取址口先 clearTag 剥前缀再 getPlayData；余量是
        // 播放数据 JSON（包装型规则 lazy 的标准回址）或普通网址。不剥则
        // "{...}" 载荷在步骤 2 被 splitEnhancement 当 ;{请求头} 块吃掉、只剩
        // "x5Play://" 裸串被出口正表抛「未解析成播放地址」，永远播不了。
        if (u.startsWith("x5Play://")) u = u.substring("x5Play://".length()).trim();
        // -1. webRule:// / x5Rule:// 网页嗅探（8.83 WebkitParser；G36：官
        // X5WebViewParseRulePair 两前缀同判同处理，咱家无 X5、系统 WebView
        // 顶替同走一条嗅探路）：必须在 @lazyRule/普通分流之前判定，
        // 否则伪协议串会被后续分流原样塞给播放器
        if (isWebSniffRule(u)) {
            // 与步骤 4 @lazyRule= 同口径：嗅探没出地址就是失败、停住抛可见错，
            // 不回空 HkPlay（调用方会把空地址组装成空 Result 静默死 00:00、无报错）
            if (!resolveWebRule(u, out, itemExtra) || out.isEmpty()) {
                throw new IllegalStateException("海阔网页嗅探未返回播放地址，请换一条试试");
            }
            return out;
        }
        // 0. @lazyRule= 优先提取（必须在 splitEnhancement 之前：JS 代码里含 {}/; 等字符）
        int lr0 = u.indexOf("@lazyRule=");
        String lazyRuleText = null;
        String lazyPageUrl = null;
        if (lr0 >= 0) {
            lazyPageUrl = u.substring(0, lr0).trim();
            lazyRuleText = decodeConflict(u.substring(lr0 + 10).trim());
            u = lazyPageUrl;
        }
        // 0.1 播放数据 JSON 必须最先认：urls 里的地址常带 #isVideo=true# 等标记，
        // 若先走下面第 1 步按 # 切串，JSON 会被拦腰截断、解析失败后整串塞给播放器（白屏）
        // 入场门照官 HttpParser.getPlayData 本体：仅 startsWith("{")＋endsWith("}")，
        // 无 "\"urls\"" 含串预卡——url-only JSON（只有单数 url）同样进 parsePlayJson；
        // 六处调用点（本处、@rule=js:/js: 结果、步骤 7、resolveWebRule、applyLazyResult）同形。
        if (lazyRuleText == null && u.startsWith("{") && u.endsWith("}")) {
            if (parsePlayJson(u, out)) return out;
        }
        // 0.5 P1：页面标识里的播放标记（剥 # 之前先提取）
        // #isM3u8# 强制 m3u8；#concat# 多段拼接；#fastPlayMode#/#threads=N# 极速模式
        if (u.contains("#isM3u8#")) out.setForceM3u8(true);
        if (u.contains("#concat#")) out.setConcat(true);
        if (u.contains("#fastPlayMode#")) out.setFastPlayMode(true);
        java.util.regex.Matcher tm = java.util.regex.Pattern.compile("#threads=(\\d+)#").matcher(u);
        if (tm.find()) {
            try {
                out.setThreads(Integer.parseInt(tm.group(1)));
                out.setFastPlayMode(true);
            } catch (Throwable ignored) {
            }
        }
        // 1.5 P1：pics:// 漫画多图模式（非视频，记入 pics 列表，UI 提示不支持但不崩）
        if (u.startsWith("pics://")) {
            parsePicsInto(u, out);
            return out;
        }
        // 1.6 P1：特殊协议识别（ed2k/magnet/thunder/ftp），原样透传给播放器。
        // 口径与 isP2pPlayLink / Thunder.match 的 scheme 判定看齐：只认 scheme 前缀，
        // 不要求 "//"（ed2k:/thunder: 无双斜杠形态同样进迅雷线，不走系统选应用）。
        String low = u.toLowerCase();
        if (low.startsWith("ed2k:")) out.setPlayType("ed2k");
        else if (low.startsWith("magnet:")) out.setPlayType("magnet");
        else if (low.startsWith("thunder:")) out.setPlayType("thunder");
        else if (low.startsWith("ftp://")) out.setPlayType("ftp");
        // 2. ; URL 增强（必须在 # 截断之前：8.83 是先从完整串解析 ;{...} 请求头块、
        // 再剥标记；旧顺序先按 # 一刀切，url#标记;{Referer@x} 的请求头块会被连带丢掉）
        u = splitEnhancement(u, out);
        // 2.5 # 页面标识截断（#threads 等已在 0.5 提取完毕）
        int hash = u.indexOf('#');
        if (hash >= 0) u = u.substring(0, hash).trim();
        // 3. video://
        if (u.startsWith("video://")) u = u.substring(8).trim();
        // 4. @lazyRule=（已在步骤0提取）
        if (lazyRuleText != null) {
            resolveLazyRule(lazyPageUrl, lazyRuleText, out, 0, itemExtra);
            // 照 8.83：懒解析没出地址就是失败、停住报错；
            // 旧代码继续拿剧集页地址走兜底，最终把网页塞给播放器（白屏/解析失败）。
            // 空结果不再回空 HkPlay：调用方（SiteApi.hkPlayerContent）会把空地址
            // 组装成空 Result 静默死（00:00、无报错），故这里直接抛可见错；
            // 播放链 SiteViewModel PLAYER 出错分支会把消息投成 Result.error，
            // 与迅雷解析失败抛中文真错同一口径。预热/漫画/列表等调用方均已
            // catch Throwable（或声明 throws），签名不用改。
            if (out.isEmpty()) {
                throw new IllegalStateException("海阔懒解析未返回播放地址，请换一条试试");
            }
            return out;
        }
        // 5. @rule=js:
        int ar = u.indexOf("@rule=");
        if (ar >= 0) {
            String pageUrl = u.substring(0, ar).trim();
            String ruleText = u.substring(ar + 6).trim();
            if (ruleText.startsWith("js:")) {
                String real = evalJsPlay(ruleText.substring(3), pageUrl);
                // 与步骤 4 @lazyRule= 同口径：js: 求值空就是失败、停住抛可见错，
                // 不落 pageUrl 兜底（否则网页被塞给播放器白屏/解析失败）
                if (real == null || real.isEmpty()) {
                    throw new IllegalStateException("海阔 @rule=js: 求值未返回播放地址，请换一条试试");
                }
                real = real.trim();
                if (real.startsWith("{") && real.endsWith("}") && parsePlayJson(real, out)) return out;
                // js: 结果是 webRule:// / x5Rule:// 嗅探串时走网页嗅探（P1/G36），不再原样塞播放器
                if (isWebSniffRule(real)) {
                    if (!resolveWebRule(real, out, itemExtra) || out.isEmpty()) {
                        throw new IllegalStateException("海阔网页嗅探未返回播放地址，请换一条试试");
                    }
                    return out;
                }
                applyLazyResult(real, out, 0, pageUrl, itemExtra);
                if (out.isEmpty()) {
                    throw new IllegalStateException("海阔 @rule=js: 懒解析未返回播放地址，请换一条试试");
                }
                return out;
            }
            u = pageUrl;
        }
        // 6. js: 整段
        if (u.startsWith("js:")) {
            String real = evalJsPlay(u.substring(3), u);
            // 与步骤 4 @lazyRule= 同口径：js: 求值空就是失败、停住抛可见错，
            // 不落步骤 8 兜底（否则 js: 原串会被原样塞给播放器）
            if (real == null || real.isEmpty()) {
                throw new IllegalStateException("海阔 js: 求值未返回播放地址，请换一条试试");
            }
            real = real.trim();
            if (real.startsWith("{") && real.endsWith("}") && parsePlayJson(real, out)) return out;
            // js: 结果是 webRule:// / x5Rule:// 嗅探串时走网页嗅探（P1/G36），不再原样塞播放器
            if (isWebSniffRule(real)) {
                if (!resolveWebRule(real, out, itemExtra) || out.isEmpty()) {
                    throw new IllegalStateException("海阔网页嗅探未返回播放地址，请换一条试试");
                }
                return out;
            }
            // js: 整段以自身为 MY_URL 求值，没有真实宿主页面，产出页传空
            applyLazyResult(real, out, 0, "", itemExtra);
            if (out.isEmpty()) {
                throw new IllegalStateException("海阔 js: 懒解析未返回播放地址，请换一条试试");
            }
            return out;
        }
        // 7. 播放数据 JSON（官 getPlayData 门：首 "{" 尾 "}"，无 urls 预卡）
        if (u.startsWith("{") && u.endsWith("}")) {
            if (parsePlayJson(u, out)) return out;
        }
        // 8. 兜底
        out.setUrl(u);
        return out;
    }

    /**
     * V2 分类 tab 点击：求值条目 url 里的 {@code @lazyRule=}，只为其副作用
     * （如 {@code putMyVar} 设置分类变量），不取播放地址。调用后由上层重新
     * {@code loadContent(true)} 刷新列表。求值异常抛 IllegalStateException
     * 给上层（HkPageActivity 既有 catch 会 toast「页签切换失败」）。
     *
     * @return 规则回调里是否调了 refreshPage（调用方据此决定是否重刷；当前
     * HkPageActivity 在 evalTab 后本来就会 loadContent(true)，故恒为 true 语义）
     */
    public boolean evalTab(String tabUrl) {
        if (tabUrl == null) return false;
        int lr = tabUrl.indexOf("@lazyRule=");
        if (lr < 0) return false;
        try {
            String r = decodeConflict(tabUrl.substring(lr + 10).trim()).trim();
            if (r.startsWith(".js:")) r = r.substring(4);
            else if (r.startsWith("js:")) r = r.substring(3);
            // 官方 LazyRuleParser.parseByJs：input = @lazyRule= 前的 URL 部分（lazyRule[0]）
            String prefix = tabUrl.substring(0, lr).trim();
            engine.getJsRuntime().evalLazy(r, prefix.isEmpty() ? null : prefix);
        } catch (Throwable e) {
            // 求值失败不许照常 return true 冒充成功：唯一调用方
            // HkPageActivity.onHeaderTabClick 对 evalTab 本就 try/catch 并
            // toast「页签切换失败」，抛出去走这条既有失败出口（批 residual）。
            Logger.t(TAG).d("tab eval failed: %s", e.getMessage());
            throw new IllegalStateException("页签分类求值失败：" + e.getMessage(), e);
        }
        // 规则标准写法是 setItem(...); refreshPage(true); —— 无论是否显式调用，
        // tab 点击后都需要重刷；显式调用时打日志便于排障。
        boolean asked = engine.getJsRuntime().consumeRefreshRequest();
        Logger.t(TAG).d("tab eval done, refreshPage asked=%s", asked);
        return true;
    }

    /**
     * 拆 {@code ;} URL 增强：{@code url;method;encoding;{K@V&&K2@V2}}。
     * header 块进结果，method/encoding 丢弃，只保留纯 url。
     */
    private String splitEnhancement(String u, HkPlay out) {
        int brace = u.indexOf('{');
        int braceEnd = u.lastIndexOf('}');
        if (brace > 0 && braceEnd > brace) {
            String inner = u.substring(brace + 1, braceEnd);
            for (String kv : inner.split("&&")) {
                int at = kv.indexOf('@');
                // 请求头值照 8.83 过 decodeConflictStr（＆＆/；；等全角转义要还原），否则发错头
                if (at > 0) out.putHeader(kv.substring(0, at).trim(), decodeConflict(kv.substring(at + 1).trim()));
            }
            u = (u.substring(0, brace) + u.substring(braceEnd + 1)).trim();
        }
        int semi = u.indexOf(';');
        if (semi >= 0) u = u.substring(0, semi).trim();
        return u;
    }

    /**
     * 网页嗅探串判据（G36）：webRule:// 与 x5Rule:// 两前缀同判——官
     * X5WebViewParseRulePair 对两前缀同判同处理（VideoUtil/JSEngine 求值
     * 结果检查均两认）；咱家不接 X5（他亲定），x5Rule 走系统 WebView 顶替、
     * 与 webRule 同入 {@link #resolveWebRule} 一条嗅探路。
     */
    private static boolean isWebSniffRule(String s) {
        // C-4/G36：官 WebkitParser:34 与 X5WebViewParser:34 均 String.startsWith
        // 大小写敏感；原 regionMatches(true,…) 大小写不敏感宽于官（"WebRule://"
        // 类变体官不认、咱原先认），收回官形。消费点五处（play 入口、@rule=js:
        // 结果、js: 结果、resolveWebRule 嵌套守卫、applyLazyResult）全经本判据
        // 把门才入 resolveWebRule，改判只影响大小写变体输入、小写前缀零变化。
        return s != null && (s.startsWith("webRule://") || s.startsWith("x5Rule://"));
    }

    /**
     * webRule:// / x5Rule:// 分流（8.83 WebkitParser.parse）：整串交嗅探全承载
     * （HkIWebViewParser.parseSync → 对应解析器＋HkWebkitHolder：全桥＋loadAllJs
     * 两拍＋extra/ua/js 透传），经 fy_bridge_app.finishParse 回真实地址；失败/
     * 超时回空并留痕，绝不把伪协议串原样塞给播放器。拿到真实地址后与 lazy
     * 同口径：先认 {urls} JSON，再当直链。
     * 双承载收归（2026-11-11）：旧路经 HkWebkitFetcher.parseWebRule 自建裸
     * WebView（无全桥/无注入、options 传 null、type 写死 "webkit"），依赖桥的
     * 嗅探式必空——整支摘除；拆串（页@解析js）与 type 按前缀真值都归解析器侧
     * （x5Rule→"x5"、webRule→"webkit"），本处不再自拆自传。
     */
    private boolean resolveWebRule(String webRuleUrl, HkPlay out, String itemExtra) {
        // 环34：带本次 play 的条目 extra（官 parse0 恒传 extra；无条目调用方为 null 与旧形同）。
        String real = HkIWebViewParser.parseSync(webRuleUrl, itemExtra);
        if (TextUtils.isEmpty(real)) {
            // 留痕用页址只为日志 host：按官前缀剥位后取首个 '@' 前段，不参与解析
            int prefixLen = webRuleUrl.startsWith("x5Rule://") ? 9 : 10;
            String body = webRuleUrl.length() > prefixLen ? webRuleUrl.substring(prefixLen) : "";
            int at = body.indexOf('@');
            String loadUrl = (at >= 0 ? body.substring(0, at) : body).trim();
            SpiderDebug.log("hk", "webRule parse empty rule=%s host=%s", rule.getTitle(), hostOf(loadUrl));
            return false;
        }
        real = real.trim();
        // x5Play:// 剥壳（同 playRoute 入口，三口齐）：嗅探结果同为一个取址口，
        // 剥在嵌套检查之前——叠形 x5Play://webRule:// 剥后仍是嗅探串、由下面
        // 嵌套支按失败收口，不许穿过去落 setUrl 当地址（复核微差订正）
        if (real.startsWith("x5Play://")) real = real.substring("x5Play://".length()).trim();
        if (isWebSniffRule(real)) {
            // 解析结果又是 webRule：不再递归嗅探，按空处理（防死循环）
            SpiderDebug.log("hk", "webRule nested result rule=%s", rule.getTitle());
            return false;
        }
        if (real.startsWith("{") && real.endsWith("}") && parsePlayJson(real, out)) return true;
        out.setUrl(splitEnhancement(real, out));
        return true;
    }

    /**
     * pics:// 串解析（play 步骤 1.5 与 applyLazyResult 的 pics 分支共用同一口径，
     * 防两处漂移）：清标记后先按行拆、再按字面 "&&" 拆——8.83 按字面 "&&" 分隔；
     * 旧写法 "[\\n&&]+" 是字符类，图片地址查询参数里的 & 会被切碎。
     *
     * @param picsUrl 以 {@code pics://} 开头的完整串
     */
    private static void parsePicsInto(String picsUrl, HkPlay out) {
        out.setPicsAttempted(true);
        String rest = HkParserKit.clearTag(picsUrl.substring(7).trim());
        for (String line : rest.split("\\n")) {
            for (String p : line.split("&&")) {
                String t = p.trim();
                if (!t.isEmpty()) out.addPic(t);
            }
        }
    }

    /**
     * pics:// 串拆分的共用出口（列表页/搜索页/详情页各入口一律走这里，
     * 不许再各自裸 split("&&")）：内部就是 {@link #parsePicsInto} 同一口径
     * （清标记、先按行拆、再按字面 "&&" 拆、trim 滤空段）。
     *
     * @param picsUrl 以 {@code pics://} 开头的完整串（大小写不敏感，调用方已判）
     * @return 拆出的图片条目（可空表，绝不为 null）
     */
    public static java.util.List<String> parsePics(String picsUrl) {
        HkPlay tmp = new HkPlay();
        if (picsUrl != null && picsUrl.length() >= 7) parsePicsInto(picsUrl, tmp);
        return tmp.getPics();
    }

    /**
     * 拆完是否有可加载的图：至少一条以 http:// 或 https:// 开头
     * （条目可带 {@code @Referer=} 等后缀，不影响前缀判定）。
     * 空体（"pics://" 后无内容）与全是非 http 条目都算无图——
     * 此判据只许作各入口的强制留底判据，**不许据此拦入口**
     * （fix4）：阅读器自带回退解析（会话图→章节串→router.play
     * 重取→书源），入口判空只是入口这一刻的拆分结果，判死
     * 会把整条回退链一并掐断（1.4.19 真机「漫画详情页都进不去」）。
     */
    public static boolean hasLoadablePic(java.util.List<String> pics) {
        if (pics == null) return false;
        for (String p : pics) {
            if (p == null) continue;
            String t = p.trim();
            if (t.regionMatches(true, 0, "http://", 0, 7)
                    || t.regionMatches(true, 0, "https://", 0, 8)) return true;
        }
        return false;
    }

    /**
     * 解析 {@code @lazyRule=}：{@code .js:}/{@code js:} 开头走 JS 独立作用域求值，
     * 否则把规则当选择器对剧集页提单个地址（{@code .js:} 值后缀由选择器内部处理）。
     */
    /**
     * 解析结果再分流：结果本身又是 {@code @lazyRule=} 串时递归解析下一段
     * （短剧两段式：第一段脚本返回的还是带懒规则的地址，海阔原版是循环解析到
     * 干净地址为止；旧实现直接对整串做 splitEnhancement，花括号匹配会跨进
     * 脚本里、把第二段懒规则连同地址一起毁掉，最终把 uri: 串塞给播放器报
     * Malformed URL——1.3.23 真机日志实证）。深度上限 3 防死循环。
     *
     * @param pageUrl 本次求值实际跑的页面地址（产出该结果的页面），
     *                结果是 pics 时记入 {@link HkPlay#setPageUrl}；调用方
     *                拿不到真实页面（如 js: 整段以自身为 MY_URL）时传空串。
     */
    private void applyLazyResult(String v, HkPlay out, int depth, String pageUrl, String itemExtra) {
        v = v.trim();
        // x5Play:// 剥壳（同 playRoute 入口，定根账 hk-sniff-route-miswire-20261113）：
        // 懒函数回 x5Play://{播放数据} 是包装型规则的标准回址，先剥壳再过下面的 JSON 门
        if (v.startsWith("x5Play://")) v = v.substring("x5Play://".length()).trim();
        // 懒解析直接返回播放数据 JSON 时当场拆开，否则整串会被当播放地址塞给播放器
        if (v.startsWith("{") && v.endsWith("}") && parsePlayJson(v, out)) return;
        // 懒解析结果是 webRule:// / x5Rule:// 嗅探串时走网页嗅探（P1/G36），不再原样塞播放器
        if (isWebSniffRule(v)) {
            resolveWebRule(v, out, itemExtra);
            return;
        }
        // 懒解析现算出的 pics:// 是漫画多图（与步骤 1.5 同语义）：记入 pics 列表
        // 供上层分流进阅读器，绝不许落到末尾当视频地址吞掉（复核报告 §3——
        // 此前无此分支，lazy 现算的漫画集被当播放地址、阅读器永远进不去）。
        // 产出页（本次求值实际跑的页面）一并记入 out，供分流时作会话页面地址。
        if (v.startsWith("pics://")) {
            parsePicsInto(v, out);
            if (pageUrl != null && !pageUrl.isEmpty()) out.setPageUrl(pageUrl);
            SpiderDebug.log("hk", "lazy resolved pics rule=%s pics=%d host=%s",
                    rule.getTitle(), out.getPics().size(), hostOf(out.getPageUrl()));
            return;
        }
        int lr = v.indexOf("@lazyRule=");
        if (lr >= 0) {
            if (depth >= 3) {
                SpiderDebug.log("hk", "lazy chain too deep rule=%s", rule.getTitle());
                return;
            }
            String page = v.substring(0, lr).trim();
            String nextRule = decodeConflict(v.substring(lr + 10).trim());
            // 页址部分的 ;增强段先拆（此时串里已无脚本，花括号匹配安全）
            page = splitEnhancement(page, out);
            int hash = page.indexOf('#');
            if (hash >= 0) page = page.substring(0, hash).trim();
            SpiderDebug.log("hk", "lazy chain depth=%d rule=%s host=%s", depth + 1, rule.getTitle(), hostOf(page));
            resolveLazyRule(page, nextRule, out, depth + 1, itemExtra);
            return;
        }
        out.setUrl(splitEnhancement(v, out));
        SpiderDebug.log("hk", "lazy resolved rule=%s -> %s", rule.getTitle(), hostOf(out.getUrl()));
    }

    private void resolveLazyRule(String pageUrl, String ruleText, HkPlay out, int depth, String itemExtra) {
        try {
            String r = ruleText.trim();
            boolean js = false;
            if (r.startsWith(".js:")) {
                r = r.substring(4);
                js = true;
            } else if (r.startsWith("js:")) {
                r = r.substring(3);
                js = true;
            }
            String v;
            if (js) {
                v = engine.getJsRuntime().evalLazy(r, pageUrl);
            } else {
                String html = getMarked(pageUrl);
                v = engine.getSelector().evalField(html, r, pageUrl);
            }
            if (!TextUtils.isEmpty(v)) {
                applyLazyResult(v, out, depth, pageUrl, itemExtra);
            } else {
                // 空结果也留痕到文件日志：规则解析失败（如福建兄妹抠不到 playUrl 时
                // 懒函数抛错/回空）此前只进 logcat，他发来的日志里看不见、定不了案。
                SpiderDebug.log("hk", "lazy play empty rule=%s host=%s", rule.getTitle(), hostOf(pageUrl));
            }
        } catch (Throwable e) {
            Logger.t(TAG).d("lazyRule failed: %s", e.getMessage());
            SpiderDebug.log("hk", "lazy play failed rule=%s host=%s err=%s", rule.getTitle(), hostOf(pageUrl), String.valueOf(e));
            // 求值抛错不许留空 out 退回：playRoute 步骤 4 的空检只能管
            // 「求值回空」，管不到「求值抛错后 out 残留半成品地址被出口
            // 当可播放行」；与步骤 5/6 js 路同口径抛可见错（批 residual）。
            throw new IllegalStateException("海阔懒解析执行失败，请换一条试试", e);
        }
    }

    private static String hostOf(String url) {
        if (url == null) return "";
        try {
            String h = java.net.URI.create(url.trim()).getHost();
            return h == null ? "" : h;
        } catch (Throwable e) {
            return "";
        }
    }

    private String evalJsPlay(String code, String pageUrl) {
        try {
            return engine.getJsRuntime().evalLazy(code, pageUrl);
        } catch (Throwable e) {
            Logger.t(TAG).d("js play failed: %s", e.getMessage());
            SpiderDebug.log("hk", "js play failed rule=%s host=%s err=%s", rule.getTitle(), hostOf(pageUrl), String.valueOf(e));
            return "";
        }
    }

    /**
     * 解析多线路/字幕 JSON：{@code {urls:[], names:[], headers:[], subtitle:'...'}}。
     * 三列按 8.83 PlayData 语义逐下标对齐保全在 HkPlay：urls 全收、names 空补「线路1..N」、
     * headers 元素两种形态都认（{@code "Cookie@xxx"} 字符串属同线路多对累积，
     * {@code {"Referer":"..."}} 对象即 8.83 的单线路头表）——绝不跨线路合并。
     * P1/G49：同时解析 audioUrls（音视频分离，整表）与 danmu/lyric/subtitle 附带字段。
     */
    private boolean parsePlayJson(String u, HkPlay out) {
        try {
            JSONObject o = new JSONObject(u);
            JSONArray urls = o.optJSONArray("urls");
            if (urls != null) {
                for (int i = 0; i < urls.length(); i++) out.addUrl(urls.optString(i, "").trim());
                if (urls.length() > 0) out.setUrl(urls.optString(0, "").trim());
            }
            // C-1 顺查附带：单数 url——官 PlayData.url 按名直填（HttpParser
            // .getPlayData）；其真消费在官 VideoUtil.getMemoryId（smali:845-867，
            // .line 113-114）：urls 为空且 url 非空时以 url 为准。官 getPosUrl
            // 不读 url 字段（urls 非空回 urls[0]、为空回入参原串），不作本支
            // 引证。码注一致：仅 urls 缺失/为空（JSONArray 为 null 或长度 0）
            // 且 out.url 未填时直读 url；urls 非空（含首元空串）不覆写 urls[0]，
            // 否则 url-only JSON 会落回 play 步骤 8 被整串当地址。
            if ((urls == null || urls.length() == 0) && out.getUrl().isEmpty()) {
                String singleUrl = o.optString("url", "").trim();
                if (!singleUrl.isEmpty()) out.setUrl(singleUrl);
            }
            JSONArray names = o.optJSONArray("names");
            if (names != null) {
                for (int i = 0; i < names.length(); i++) out.addName(names.optString(i, "").trim());
            }
            // 8.83 HttpParser.getPlayData：names 为空补「线路1..N」；多于 urls 的截断。
            // 环34 双路收敛：旧形对「部分 names」（非空但少于 urls）也逐个补名，
            // 与 Gson 路（HkHttpParser.getPlayData 只在 names 空时整表补、部分给
            // 多少留多少）分叉——双输入夹具 Fix34 样本1 实测抓出，就此收齐：
            // 部分 names 原样保留（消费点 getName 越界回首条，不依赖补满）。
            if (out.getNames().isEmpty()) {
                for (int i = 0; i < out.getUrls().size(); i++) out.addName("线路" + (i + 1));
            }
            // 复核订正 B3：截断加 urls 非空门，与 Gson 路 HkHttpParser.getPlayData
            // 同条件（其截断支在 urls 非空大支内）——urls 缺失/为空且 names 非空
            // 时保留 names 全量，旧形在此把 names 截到 0 条。
            while (!out.getUrls().isEmpty() && out.getNames().size() > out.getUrls().size() && !out.getNames().isEmpty())
                out.getNames().remove(out.getNames().size() - 1);
            // P1/G49：音视频分离——audioUrls 整表入 HkPlay（8.83 PlayData 是
            // List 全量；旧形只取首条塞单值字段＝绕补，G49 摘除归模型正路）
            JSONArray audioUrls = o.optJSONArray("audioUrls");
            if (audioUrls != null) {
                for (int i = 0; i < audioUrls.length(); i++) out.addAudioUrl(audioUrls.optString(i, "").trim());
            }
            JSONArray headers = o.optJSONArray("headers");
            if (headers != null) {
                java.util.Map<String, String> cur = new java.util.LinkedHashMap<>();
                for (int i = 0; i < headers.length(); i++) {
                    Object el = headers.opt(i);
                    if (el instanceof JSONObject) {
                        // 一个对象 = 一条线路的头表：先结算之前累积的同线路多对，再单独成表
                        if (!cur.isEmpty()) {
                            out.addLineHeaders(cur);
                            cur = new java.util.LinkedHashMap<>();
                        }
                        JSONObject ho = (JSONObject) el;
                        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
                        java.util.Iterator<String> keys = ho.keys();
                        while (keys.hasNext()) {
                            String k = keys.next();
                            String v = decodeConflict(ho.optString(k, "").trim());
                            if (!k.trim().isEmpty() && !v.isEmpty()) m.put(k.trim(), v);
                        }
                        out.addLineHeaders(m);
                    } else {
                        // 星辰内编码（expandedUrl）：同线路的 "K@V" 对分多条存，累积进当前表
                        String kv = headers.optString(i, "");
                        int at = kv.indexOf('@');
                        if (at > 0) cur.put(kv.substring(0, at).trim(), decodeConflict(kv.substring(at + 1).trim()));
                    }
                }
                if (!cur.isEmpty()) out.addLineHeaders(cur);
            }
            // C-1/G49：单数 header（Map）——官 PlayData.header（VideoUtil:1022
            // getHeader 真消费）与咱家 HkPlay 单路头表（getHeaders()/putHeader）
            // 同位；Gson 路（HkHttpParser.getPlayData 直填 HkPlayData.header）
            // 读得到，手工路原先独漏此键，{"urls":[…],"header":{…}} 的请求头
            // 整段丢失。读法与上面 headers 对象支同形：键 trim、值 trim 后过
            // decodeConflictStr，空键/空值不入；键缺失/非对象不入（官直填为 null）。
            JSONObject headerObj = o.optJSONObject("header");
            if (headerObj != null) {
                java.util.Iterator<String> hkeys = headerObj.keys();
                while (hkeys.hasNext()) {
                    String k = hkeys.next();
                    String v = decodeConflict(headerObj.optString(k, "").trim());
                    if (!k.trim().isEmpty() && !v.isEmpty()) out.putHeader(k.trim(), v);
                }
            }
            String sub = o.optString("subtitle", "").trim();
            if (!sub.isEmpty()) out.setSubtitle(sub);
            // G49/G59：附带字段 danmu/lyric 与 subtitle 同路入 HkPlay 承载
            // （旧形两键在解析层即丢弃——官 getPlayData 按名直填全 9 字段、
            // getMemoryId 靠整串传递到播放器；咱家等价承载即 HkPlay 全字段）。
            String dm = o.optString("danmu", "").trim();
            if (!dm.isEmpty()) out.setDanmu(dm);
            String ly = o.optString("lyric", "").trim();
            if (!ly.isEmpty()) out.setLyric(ly);
            return !out.isEmpty();
        } catch (Throwable e) {
            Logger.t(TAG).d("play json failed: %s", e.getMessage());
            return false;
        }
    }

    /**
     * P2 线路记忆（8.83 PlayerChooser.getSwitchIndex 语义）：键=规则名+剧集 id，
     * 默认 0。供播放器内线路切换入口调用（当前桥路径暂无切换 UI，只记不换）。
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, Integer> LINE_INDEX =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static int lineIndex(String ruleTitle, String episodeId) {
        if (ruleTitle == null || episodeId == null) return 0;
        Integer i = LINE_INDEX.get(ruleTitle + "|" + episodeId);
        return i == null ? 0 : Math.max(0, i);
    }

    public static void rememberLine(String ruleTitle, String episodeId, int index) {
        if (ruleTitle == null || episodeId == null || index < 0) return;
        LINE_INDEX.put(ruleTitle + "|" + episodeId, index);
    }

    /**
     * 冲突字符解码（官方 StringUtil.decodeConflictStr）：
     * {@code ？？}→{@code ?}、{@code ＆＆}→{@code &}、{@code ；；}→{@code ;}、{@code ，，}→{@code ,}。
     */
    private String decodeConflict(String s) {
        if (s == null) return "";
        return s.replace("？？", "?").replace("＆＆", "&").replace("；；", ";").replace("，，", ",");
    }

    public void destroy() {
        engine.destroy();
    }

    // ================= 内部 =================

    /**
     * 详情规则收口：最终选中的 find_rule 为空/"*" 视作无详情规则。
     * 环15 订正：取段（搜索来路折叠 vs 普通 detail 段）已在 detail() 构造
     * 入口经 HkSearchEngine.fromRule 唯一折叠点完成，此处不再自折 sdetail
     * ——旧形只换 find_rule 且 "*" 回落后仍取 detail 段，与官折叠
     * （"*" 时取 detail 段是折叠本身、不是回落）形似实异，且 col_type 不成对。
     */
    private String effectiveDetailRule(String findRule) {
        if (findRule == null || findRule.trim().isEmpty() || "*".equals(findRule.trim())) return null;
        return findRule;
    }

    /** 规则是否没有详情解析规则（此时 V4 必空，条目 lazyRule 求值结果应直接播放）。 */
    private boolean isEmptyDetailRule() {
        String r = rule.getDetailFindRule();
        return r == null || r.trim().isEmpty() || "*".equals(r.trim());
    }

    /** 结果是否形如媒体直链（去 # 标记后看 http 前缀与常见媒体后缀/关键字）。 */
    private static boolean looksLikeMediaUrl(String url) {
        if (url == null) return false;
        String l = url.toLowerCase();
        int h = l.indexOf('#');
        if (h >= 0) l = l.substring(0, h);
        l = l.trim();
        if (!l.startsWith("http")) return false;
        return l.contains(".m3u8") || l.contains(".mp4") || l.contains(".flv")
                || l.contains(".ts?") || l.contains(".ts&") || l.endsWith(".ts")
                || l.endsWith(".mkv") || l.endsWith(".avi") || l.contains("mime=video")
                || l.contains("mime=audio") || l.contains(".mp3") || l.contains(".wav");
    }

    /**
     * 官方 UrlDetector.isVideoOrMusic 同款判定（静态规则部分；含 @rule=/@lazyRule= 的返回 false，
     * 因需先解析）。
     */
    public static boolean isVideoOrMusicUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        String u = url.trim();
        if (u.contains("ignoreVideo=true") || u.contains("#ignoreMusic=true#")) return false;
        if (u.contains("isVideo=true") || u.contains("isMusic=true")) return true;
        String low = u.toLowerCase();
        if (low.startsWith("x5play://")) return true;
        if (u.contains("@rule=") || u.contains("@lazyRule=")) return false;
        if (low.startsWith("rtmp://") || low.startsWith("rtsp://") || u.contains("video://")) return true;
        if (low.contains(".mp4.jp") || low.contains(".mp4.png")) return false;
        boolean media = looksLikeMediaUrl(u);
        // 诊断（定根账可观测性缺口）：后缀门是电台流被判网页的第一道关，此前
        // 无日志。只记判定布尔与后缀形态，不记完整地址；后缀表本体不动。
        try {
            SpiderDebug.log("hk", "suffixGate hit=%b ext=%s", media, suffixShape(u));
        } catch (Throwable ignored) {
        }
        return media;
    }

    /** 诊断用：只取地址的后缀形态（最后一个点后、问号/井号前的短串），不记完整地址。 */
    private static String suffixShape(String url) {
        try {
            String s = url;
            int h = s.indexOf('#');
            if (h >= 0) s = s.substring(0, h);
            int q = s.indexOf('?');
            if (q >= 0) s = s.substring(0, q);
            int slash = s.lastIndexOf('/');
            int dot = s.lastIndexOf('.');
            if (dot > slash && dot < s.length() - 1) {
                String ext = s.substring(dot + 1).toLowerCase();
                return ext.length() <= 10 ? ext : ext.substring(0, 10);
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /**
     * P2P 正表单支（照官 ThunderManager.isMagnetOrTorrent 前两支，官包
     * ThunderManager.smali:6228，内 magnet: 全形在 :6236）：magnet: 全形
     * （带不带问号同支）或 thunder://。全树分发面（dealKind／fragment／
     * simply／搜索）磁力支共用此一处，不许再各写一份前缀判据
     * （D 组双表漂移的根）。
     * 大小写口径：官 startsWith$default 的 ignoreCase=false，即大小写敏感，
     * 单参版即此口径；双参版只供播放门函数族内部传不敏感口径，分发面不许传 true。
     */
    public static boolean isMagnetLink(String url) {
        return isMagnetLink(url, false);
    }

    public static boolean isMagnetLink(String url, boolean ignoreCase) {
        if (url == null) return false;
        String u = ignoreCase ? url.toLowerCase() : url;
        return u.startsWith("magnet:") || u.startsWith("thunder://");
    }

    /**
     * P2P 正表单支（照官 ThunderManager.isMagnetOrTorrent 第三支）：
     * 以 ";" 切首段以 .torrent 结尾（带 ;{…} 增强段的种子址同形）。
     * 全树分发面种子支共用此一处。大小写口径同 isMagnetLink（官敏感）。
     */
    public static boolean isTorrentLink(String url) {
        return isTorrentLink(url, false);
    }

    public static boolean isTorrentLink(String url, boolean ignoreCase) {
        if (url == null) return false;
        String first = url.split(";")[0];
        return ignoreCase ? first.toLowerCase().endsWith(".torrent") : first.endsWith(".torrent");
    }

    /**
     * 磁力/种子合判正表（照官 ThunderManager.isMagnetOrTorrent 本体三支合形，
     * 大小写敏感）：桥层 DetailBridge.isMagnetOrTorrent 直调此一处，
     * 不许再存第二份手写副本。
     */
    public static boolean isMagnetOrTorrent(String url) {
        return isMagnetLink(url) || isTorrentLink(url);
    }

    /**
     * 是否为动作 URL（8.83 StringUtil 动作 URL 表同款：toast://、copy://、input://、
     * confirm://、select://、download:// 并列）。
     * 这类 URL 只应走 handleActionUrl，绝不能被当成直接播放地址或进 V4。
     */
    public static boolean isActionUrl(String url) {
        if (url == null) return false;
        String l = url.trim().toLowerCase();
        return l.startsWith("toast://") || l.startsWith("copy://")
                || l.startsWith("input://") || l.startsWith("confirm://")
                || l.startsWith("select://") || l.startsWith("download://")
                || l.startsWith("hiker://empty");
    }

    /**
     * dealWithUrl 分流（官方 ArticleListFragment.dealWithUrl 顺序）：
     * video=直接播放 / pics=漫画 / x5=webview规则 / web=网页 / image=图片 / magnet=分享 / ""=未知。
     */
    public static String dealKind(String url) {
        if (url == null || url.trim().isEmpty()) return "";
        String u = url.trim();
        // 8.83 H2：@lazyRule= 优先（dealLazyRule），绝不按普通 URL 分流；
        // 只看 @rule= 前段是否含 @lazyRule=（与 clickItem 顺序一致）。
        int ar = u.indexOf("@rule=");
        String pre = ar >= 0 ? u.substring(0, ar) : u;
        if (pre.contains("@lazyRule=")) return "";
        String low = u.toLowerCase();
        if (low.startsWith("pics://")) return "pics";
        if (low.startsWith("x5://")) return "x5";
        // 支序照官序（官包 8.83 ArticleListFragment.smali dealWithUrl:6368-8297）：
        // pics 支后即磁力支（子链 :7281-7382）→ isImage 支 → isVideoOrMusic 支
        // （:7863 区）→ 尾链 http/file 网页支。磁力判据收归正表
        // （isP2pPlayLink 函数族 scheme 四类＋isTorrentLink 种子支），种子亦归 magnet 名义，
        // 由 HkPageActivity 面 magnet 支首段分流 startParseTorrent（与 fragment 面同落点）。
        // 大小写口径归官分发面：官 dealWithUrl 本体 Java startsWith 大小写敏感、
        // ThunderManager.isMagnetOrTorrent 亦敏感，故本面传 ignoreCase=false；
        // 单参 isP2pPlayLink 的不敏感口径只属播放门（SiteApi/HkPageActivity 二段门），
        // 两口径同族双参共存，不许再各写一份判据（复核账 hiker-route-media-fixreview 不过条二）。
        if (isP2pPlayLink(u, false) || isTorrentLink(u)) return "magnet";
        // 图片判据直调正表 HkUrlDetector.isImage（官 UrlDetector 唯一一张 IMAGES 表），
        // 不许再内嵌简化版（旧内嵌版多 .bmp、漏 .ico/.svg/.avif、不排 x5Play 伪形，已删）。
        if (com.fongmi.android.tv.ui.hk.HkUrlDetector.isImage(u)) return "image";
        if (isVideoOrMusicUrl(u)) return "video";
        if (low.startsWith("http://") || low.startsWith("https://") || low.startsWith("file://")) return "web";
        return "";
    }

    /**
     * 用户拍板（相对原版的有意分叉）：海阔磁力／ed2k／thunder／ftp 链接一律走星辰播放器
     * （经 SiteApi.hkPlayerContent 的 Source.fetch → Thunder.fetch 迅雷转换），
     * 不维持原版、不走系统选应用。口径只认 scheme 前缀（magnet:/ed2k:/thunder:/ftp://），
     * 不要求 "//"，与 Thunder.match 的 scheme 判定、SiteApi.isThunderId、
     * VideoActivity.isThunderUrl 看齐。.torrent 不入本门（SiteApi 播放门不扩面），
     * 种子判据单列 isTorrentLink、只供分发面（dealKind/fragment）用。上游 Thunder SCHEMES 本就含 ftp，活链
     * XLTaskHelper.addThunderTask 内 ftp:// 走 P2sp 任务，故 ftp 不再留给系统选应用。
     */
    public static boolean isP2pPlayLink(String url) {
        return isP2pPlayLink(url, true);
    }

    /**
     * P2P scheme 四类判据的唯一实现（函数族本体）：ignoreCase=true 为播放门口径
     * （与 VideoActivity.isThunderUrl／Thunder.match 的 scheme 小写归一看齐），
     * false 为分发面口径（官 dealWithUrl／ThunderManager 大小写敏感，dealKind 用）。
     * 两口径只许在此一处分叉，不许调用方再自写前缀判据。
     */
    public static boolean isP2pPlayLink(String url, boolean ignoreCase) {
        if (url == null) return false;
        String u = url.trim();
        if (ignoreCase) u = u.toLowerCase();
        return u.startsWith("magnet:") || u.startsWith("ed2k:") || u.startsWith("thunder:") || u.startsWith("ftp://");
    }

    private String cleanDetailUrl(String url) {
        if (url == null) return "";
        String u = url.trim();
        int hash = u.indexOf('#');
        if (hash >= 0) u = u.substring(0, hash).trim();
        return u;
    }

    /** 带按源定向打点的取数：请求归属当前规则（kind=hk、id=规则名），进出恢复旧标记。 */
    private String getMarked(String url) throws IOException {
        SourceGate.Mark prev = SourceGate.current();
        SourceGate.mark("hk", rule.getTitle());
        try {
            return HkHttp.get(url, rule.resolvedUa());
        } finally {
            if (prev != null) SourceGate.mark(prev.kind, prev.id);
            else SourceGate.clear();
        }
    }

    private List<HkDetailItem> fromJsDetail(String jsCode, String url, String defaultColType) throws Exception {
        // R2：8.83 详情 JS 求值前先抓详情 HTML，getResCode()/getCode() 可取正文
        String fetchUrl = cleanDetailUrl(url);
        boolean prefetchFailed = false;
        if (!fetchUrl.isEmpty() && (fetchUrl.startsWith("http://") || fetchUrl.startsWith("https://"))) {
            try {
                String body = getMarked(fetchUrl);
                if (body != null && !body.isEmpty()) engine.getJsRuntime().setEvalResCodeBody(body);
            } catch (Throwable e) {
                // 预抓失败则详情 JS 拿不到正文、解析可能为空，此前无痕与「规则本身
                // 出空」不可区分；按本文件既有失败日志形（lazy/js play failed）留痕，
                // 并置标：下方求值若因此出空，按失败抛、不冒充规则真空（批 residual）。
                prefetchFailed = true;
                SpiderDebug.log("hk", "detail prefetch failed rule=%s host=%s err=%s",
                        rule.getTitle(), hostOf(fetchUrl), String.valueOf(e));
            }
        }
        try {
            List<Map<String, String>> raw = engine.getJsRuntime().parseDetailRaw(jsCode, url);
            List<HkDetailItem> items = new ArrayList<>();
            for (Map<String, String> m : raw) {
                HkDetailItem it = new HkDetailItem();
                it.setTitle(str(m, "title"));
                it.setUrl(str(m, "url"));
                String pic = str(m, "pic_url");
                if (pic.isEmpty()) pic = str(m, "pic");
                if (pic.isEmpty()) pic = str(m, "img");
                if (pic.isEmpty()) pic = str(m, "image");
                if (pic.isEmpty()) pic = str(m, "cover");
                if (pic.isEmpty()) pic = str(m, "thumbnail");
                it.setPic(pic);
                it.setDesc(str(m, "desc"));
                it.setLine(str(m, "line"));
                String col = str(m, "col_type");
                if (col.isEmpty()) col = str(m, "colType");
                // 官方 dealRule：detail_col_type 作为整页默认样式，无 col_type 的条目回退到它
                if (col.isEmpty()) col = defaultColType;
                it.setColType(col);
                items.add(it);
            }
            if (prefetchFailed && items.isEmpty()) {
                throw new IllegalStateException("详情正文预抓失败，JS 详情无正文可解析");
            }
            return items;
        } finally {
            engine.getJsRuntime().setEvalResCodeBody(null);
        }
    }

    private static String str(Map<String, String> m, String key) {
        String v = m.get(key);
        return v == null ? "" : v;
    }

}
