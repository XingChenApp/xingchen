package com.fongmi.android.tv.api.hk;

import android.text.TextUtils;

import com.orhanobut.logger.Logger;

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
        this.engine = new HkEngine(rule);
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

    /** 单页内置搜索。 */
    public List<HkItem> search(String keyword, int page) {
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
            String u = itemUrl == null ? "" : itemUrl.trim();

            // ① @lazyRule= 条目：先求值 → dealWithUrl 分流（官方：不进 V4）
            int lr = u.indexOf("@lazyRule=");
            if (lr >= 0) {
                String v = evalEntryLazy(u, lr);
                if (v != null) {
                    String vt = v.trim();
                    if (!vt.isEmpty() && !"hiker://empty".equals(vt)) {
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
                        // 未知种类：回退旧逻辑（无详情规则则直接播放，否则继续 V4 流程）
                        if (vt.contains("#isVideo=true#") || isEmptyDetailRule() || looksLikeMediaUrl(vt)) {
                            detail.setDirectPlayUrl(vt);
                            return detail;
                        }
                    }
                }
                u = (v == null || v.trim().isEmpty()) ? u.substring(0, lr).trim() : v.trim();
            }

            // ② 纯 js: 条目（$.toString 生成）：求值得到真实 URL
            if (u.startsWith("js:")) {
                String v = evalEntryJs(u);
                if (v != null && !v.trim().isEmpty()) u = v.trim();
            }

            // ③ @rule=js: 条目：pageUrl 为 MY_URL，js 部分求值出详情
            int ar = u.indexOf("@rule=");
            if (ar >= 0) {
                String pageUrl = cleanDetailUrl(u.substring(0, ar).trim());
                String entryRule = u.substring(ar + 6).trim();
                if (entryRule.startsWith("js:")) {
                    buildDetail(detail, fromJsDetail(entryRule, pageUrl));
                    return detail;
                }
                u = pageUrl;
            }

            // ④ 原有 detail_find_rule 流程
            String ruleText = effectiveDetailRule(fromSearch);
            if (ruleText == null || ruleText.trim().isEmpty()) {
                // 无详情规则：若条目 URL 本身形如媒体直链 → 直接播放，避免 V4"加载失败"
                String cu = u.trim();
                if (looksLikeMediaUrl(cu)) detail.setDirectPlayUrl(cu);
                return detail;
            }
            String url = cleanDetailUrl(u);
            List<HkDetailItem> items;
            if (HkSelector.isJsRule(ruleText)) {
                items = fromJsDetail(ruleText, url);
            } else {
                String html = HkHttp.get(url, rule.resolvedUa());
                items = engine.getSelector().parseDetail(html, ruleText, url);
            }
            buildDetail(detail, items);
        } catch (Throwable e) {
            Logger.t(TAG).d("detail failed: %s", e.getMessage());
        }
        return detail;
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
            String html = HkHttp.get(pageUrl, rule.resolvedUa());
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
     */
    public HkPlay play(String episodeUrl) {
        HkPlay out = new HkPlay();
        if (episodeUrl == null) return out;
        String u = episodeUrl.trim();
        // 0. @lazyRule= 优先提取（必须在 splitEnhancement 之前：JS 代码里含 {}/; 等字符）
        int lr0 = u.indexOf("@lazyRule=");
        String lazyRuleText = null;
        String lazyPageUrl = null;
        if (lr0 >= 0) {
            lazyPageUrl = u.substring(0, lr0).trim();
            lazyRuleText = decodeConflict(u.substring(lr0 + 10).trim());
            u = lazyPageUrl;
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
        // 1. # 页面标识
        int hash = u.indexOf('#');
        if (hash >= 0) u = u.substring(0, hash).trim();
        // 1.5 P1：pics:// 漫画多图模式（非视频，记入 pics 列表，UI 提示不支持但不崩）
        if (u.startsWith("pics://")) {
            String rest = u.substring(7).trim();
            for (String p : rest.split("[\\n&&]+")) {
                String t = p.trim();
                if (!t.isEmpty()) out.addPic(t);
            }
            return out;
        }
        // 1.6 P1：特殊协议识别（ed2k/magnet/thunder/ftp），原样透传给播放器
        String low = u.toLowerCase();
        if (low.startsWith("ed2k://")) out.setPlayType("ed2k");
        else if (low.startsWith("magnet:")) out.setPlayType("magnet");
        else if (low.startsWith("thunder://")) out.setPlayType("thunder");
        else if (low.startsWith("ftp://")) out.setPlayType("ftp");
        // 2. ; URL 增强
        u = splitEnhancement(u, out);
        // 3. video://
        if (u.startsWith("video://")) u = u.substring(8).trim();
        // 4. @lazyRule=（已在步骤0提取）
        if (lazyRuleText != null) {
            resolveLazyRule(lazyPageUrl, lazyRuleText, out);
            if (!out.isEmpty()) return out;
            // lazyRule 未解析出地址时，继续用 pageUrl 走后续分流
        }
        // 5. @rule=js:
        int ar = u.indexOf("@rule=");
        if (ar >= 0) {
            String pageUrl = u.substring(0, ar).trim();
            String ruleText = u.substring(ar + 6).trim();
            if (ruleText.startsWith("js:")) {
                String real = evalJsPlay(ruleText.substring(3), pageUrl);
                if (!real.isEmpty()) {
                    out.setUrl(real);
                    return out;
                }
            }
            u = pageUrl;
        }
        // 6. js: 整段
        if (u.startsWith("js:")) {
            String real = evalJsPlay(u.substring(3), u);
            if (!real.isEmpty()) {
                out.setUrl(real);
                return out;
            }
        }
        // 7. {urls:[]} JSON
        if (u.startsWith("{") && u.contains("\"urls\"")) {
            if (parsePlayJson(u, out)) return out;
        }
        // 8. 兜底
        out.setUrl(u);
        return out;
    }

    /**
     * V2 分类 tab 点击：求值条目 url 里的 {@code @lazyRule=}，只为其副作用
     * （如 {@code putMyVar} 设置分类变量），不取播放地址。调用后由上层重新
     * {@code loadContent(true)} 刷新列表。求值异常只打日志，不抛给上层。
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
            Logger.t(TAG).d("tab eval (side effects applied): %s", e.getMessage());
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
                if (at > 0) out.putHeader(kv.substring(0, at).trim(), kv.substring(at + 1).trim());
            }
            u = (u.substring(0, brace) + u.substring(braceEnd + 1)).trim();
        }
        int semi = u.indexOf(';');
        if (semi >= 0) u = u.substring(0, semi).trim();
        return u;
    }

    /**
     * 解析 {@code @lazyRule=}：{@code .js:}/{@code js:} 开头走 JS 独立作用域求值，
     * 否则把规则当选择器对剧集页提单个地址（{@code .js:} 值后缀由选择器内部处理）。
     */
    private void resolveLazyRule(String pageUrl, String ruleText, HkPlay out) {
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
                String html = HkHttp.get(pageUrl, rule.resolvedUa());
                v = engine.getSelector().evalField(html, r, pageUrl);
            }
            if (!TextUtils.isEmpty(v)) out.setUrl(v.trim());
        } catch (Throwable e) {
            Logger.t(TAG).d("lazyRule failed: %s", e.getMessage());
        }
    }

    private String evalJsPlay(String code, String pageUrl) {
        try {
            return engine.getJsRuntime().evalLazy(code, pageUrl);
        } catch (Throwable e) {
            Logger.t(TAG).d("js play failed: %s", e.getMessage());
            return "";
        }
    }

    /**
     * 解析多线路/字幕 JSON：{@code {urls:[], names:[], headers:[], subtitle:'...'}}。
     * headers 元素形如 {@code "Cookie@xxx"}。P1：同时解析 audioUrls（音视频分离）。
     */
    private boolean parsePlayJson(String u, HkPlay out) {
        try {
            JSONObject o = new JSONObject(u);
            JSONArray urls = o.optJSONArray("urls");
            if (urls != null && urls.length() > 0) out.setUrl(urls.optString(0, "").trim());
            // P1：音视频分离
            JSONArray audioUrls = o.optJSONArray("audioUrls");
            if (audioUrls != null && audioUrls.length() > 0)
                out.setAudioUrl(audioUrls.optString(0, "").trim());
            JSONArray headers = o.optJSONArray("headers");
            if (headers != null) {
                for (int i = 0; i < headers.length(); i++) {
                    String kv = headers.optString(i, "");
                    int at = kv.indexOf('@');
                    if (at > 0) out.putHeader(kv.substring(0, at).trim(), kv.substring(at + 1).trim());
                }
            }
            String sub = o.optString("subtitle", "").trim();
            if (!sub.isEmpty()) out.setSubtitle(sub);
            return !out.isEmpty();
        } catch (Throwable e) {
            Logger.t(TAG).d("play json failed: %s", e.getMessage());
            return false;
        }
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

    private String effectiveDetailRule(boolean fromSearch) {
        String r = null;
        if (fromSearch) {
            r = rule.getSdetailFindRule();
            if (r == null || r.trim().isEmpty() || "*".equals(r.trim())) r = null;
        }
        if (r == null) r = rule.getDetailFindRule();
        if (r == null || r.trim().isEmpty() || "*".equals(r.trim())) return null;
        return r;
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
        return looksLikeMediaUrl(u);
    }

    /** 是否为图片 URL（静态后缀判定）。 */
    public static boolean isImageUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        String u = url.trim().toLowerCase();
        if (u.contains("@rule=") || u.contains("@lazyRule=")) return false;
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        return u.endsWith(".png") || u.endsWith(".jpg") || u.endsWith(".jpeg")
                || u.endsWith(".gif") || u.endsWith(".webp") || u.endsWith(".bmp");
    }

    /**
     * dealWithUrl 分流（官方 ArticleListFragment.dealWithUrl 顺序）：
     * video=直接播放 / pics=漫画 / x5=webview规则 / web=网页 / image=图片 / magnet=分享 / ""=未知。
     */
    public static String dealKind(String url) {
        if (url == null || url.trim().isEmpty()) return "";
        String u = url.trim();
        String low = u.toLowerCase();
        if (low.startsWith("pics://")) return "pics";
        if (low.startsWith("x5://")) return "x5";
        if (isVideoOrMusicUrl(u)) return "video";
        if (low.startsWith("magnet:") || low.startsWith("thunder://")
                || low.startsWith("ftp://") || low.startsWith("ed2k://")) return "magnet";
        if (isImageUrl(u)) return "image";
        if (low.startsWith("http://") || low.startsWith("https://") || low.startsWith("file://")) return "web";
        return "";
    }

    private String cleanDetailUrl(String url) {
        if (url == null) return "";
        String u = url.trim();
        int hash = u.indexOf('#');
        if (hash >= 0) u = u.substring(0, hash).trim();
        return u;
    }

    private List<HkDetailItem> fromJsDetail(String jsCode, String url) throws Exception {
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
            it.setColType(col);
            items.add(it);
        }
        return items;
    }

    private static String str(Map<String, String> m, String key) {
        String v = m.get(key);
        return v == null ? "" : v;
    }

    /**
     * 组装 HkDetail：
     * - {@code copy://} → 复制按钮；
     * - col_type 含 long_text / text_* / rich_text / text_center → 简介内容区（换行拼接）；
     * - 纯展示项（无可播链接）→ 标题/封面回退；
     * - 其余（含 pic_1 播放按钮、带 lazyRule 地址的条目）按 line 分组为线路/选集；
     * - 选集名为空时 Line.addEpisode 自动回退为"第N集"；line 为空的并入"默认"线路。
     */
    private void buildDetail(HkDetail detail, List<HkDetailItem> items) {
        if (items == null) return;
        for (HkDetailItem it : items) {
            String col = it.getColType() == null ? "" : it.getColType().toLowerCase();
            String url = it.getUrl() == null ? "" : it.getUrl().trim();
            // copy:// → 复制按钮
            if (url.startsWith("copy://")) {
                detail.addCopyItem(it.getTitle(), url.substring(7));
                continue;
            }
            // 文本类 col_type → 简介内容区，不进选集
            if (col.contains("long_text") || col.startsWith("text_") || col.contains("text_center")
                    || col.contains("rich_text")) {
                String c = (it.getTitle() + it.getDesc()).trim();
                if (!c.isEmpty()) detail.appendContent(c);
                if (!it.getPic().isEmpty() && detail.getPic().isEmpty()) detail.setPic(it.getPic());
                continue;
            }
            if (it.isDisplayOnly()) {
                if (!it.getPic().isEmpty()) detail.setPic(it.getPic());
                if (!it.getTitle().isEmpty() && detail.getTitle().isEmpty()) detail.setTitle(it.getTitle());
                if (!it.getDesc().isEmpty() && detail.getContent().isEmpty()) detail.setContent(it.getDesc());
                continue;
            }
            detail.getOrCreateLine(it.getLine()).addEpisode(it.getTitle(), it.getUrl());
        }
    }
}
