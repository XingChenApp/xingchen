package com.fongmi.android.tv.api.hk;

import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.SourceGate;
import com.orhanobut.logger.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 海阔规则引擎（M2 最小可用版）。
 *
 * <p>把 {@link HkRule}、{@link HkJsRuntime}、{@link HkSelector}、{@link HkHttp} 串起来：
 * home/search 时按规则形态自动分流——{@code js:} 走 JS 运行时，否则走选择器。
 * M3 的 HkRouter 会在此基础上扩展 detail/play 与单页对接。</p>
 */
public class HkEngine {

    private static final String TAG = "HkEngine";

    private final HkRule rule;
    private final HkJsRuntime jsRuntime;
    private final HkSelector selector;
    /**
     * 本引擎（＝一个宿主页面面）的列表求值顶替身份：顶替只许在本面的新发之间
     * 发生，跨面提交不再把本面首拉顶成空（账 hk-import-firstload-empty-dig §六.1）。
     */
    private final Object listEvalScope = new Object();
    /**
     * true = 包裹常驻运行时（只借用）：不重复 init、destroy() 是空操作，
     * 生命周期归 HkEngineManager 管。见修法 A（b12 分类点不进去定案）。
     */
    private final boolean adopted;

    public HkEngine(HkRule rule) throws Exception {
        this.rule = rule;
        this.adopted = false;
        try {
            this.jsRuntime = new HkJsRuntime(rule);
            this.jsRuntime.init();
        } catch (Exception e) {
            // 引擎建不起来时以前只有 logcat 有痕迹，调试日志里查不到；接进文件日志
            SpiderDebug.log("hk", "engine init failed rule=%s err=%s", rule.getTitle(), String.valueOf(e));
            throw e;
        }
        // 图片 @js= 求值依赖当前规则的 JS 运行时；HkPageActivity.getRouter() 也会设置，
        // 这里兜底保证引擎创建时即登记（8.83 JSEngine.getInstance() 单例语义）。
        HkImage.setCurrentRuntime(this.jsRuntime);
        HkJsRuntime.setActiveInstance(this.jsRuntime);
        HkImage.setCurrentTitle(rule.getTitle());
        this.selector = jsRuntime.newSelector();
    }

    /**
     * 修法 A：包裹常驻运行时（HkEngineManager.runtimeFor），不新建不 init，
     * destroy() 空操作——与 8.83 JSEngine 单例语义一致：写读同一张变量表，
     * 点击求值的 putMyVar 才能被页面刷新求值读到。
     * 调用方须先 HkEngineManager.ensureInit(rt)（只跑一次，不清变量表）。
     */
    private HkEngine(HkRule rule, HkJsRuntime resident) {
        this.rule = rule;
        this.adopted = true;
        this.jsRuntime = resident;
        // 图片 @js= 求值依赖当前规则的 JS 运行时；与原构造同款兜底登记
        HkImage.setCurrentRuntime(this.jsRuntime);
        HkJsRuntime.setActiveInstance(this.jsRuntime);
        HkImage.setCurrentTitle(rule.getTitle());
        this.selector = resident.newSelector();
    }

    /** 修法 A 工厂：HkRouter 取常驻运行时后用此构造包裹。 */
    public static HkEngine wrapResident(HkRule rule, HkJsRuntime resident) {
        if (rule == null || resident == null) throw new IllegalArgumentException("rule/resident null");
        return new HkEngine(rule, resident);
    }

    /**
     * 首页/分类列表。
     */
    public List<HkItem> home(int page, String cls, String area, String year, String sort) {
        try {
            rule.validate();
            String findRule = rule.getFindRule();
            boolean js = HkSelector.isJsRule(findRule);
            // js: 规则的 MY_URL 必须是原始 url（保留 hiker://empty# 前缀），
            // 规则 JS 里常写 MY_URL.replace("hiker://empty##", host) 做替换。
            String url = js
                    ? HkHttp.expandUrl(rule.getUrl(), cls, area, year, sort, page, false)
                    : HkHttp.expandUrl(rule.getUrl(), cls, area, year, sort, page);
            if (js) {
                // P1：注入 MY_TYPE/MY_CLASS_URL/MY_CLASS_NAME 等官方变量
                // MY_PARAMS 照 8.83 统一取规则的 params 字段（子页参数由 PageParser 写进该字段）
                jsRuntime.setListContext("home", safe(cls), classNameOf(cls), rule.getParams(), safe(area), safe(year), safe(sort));
                // R1: 8.83 所有 js: 规则 Java 层先抓页（JSEngine.smali:20401），正文进 resCodeMap，
                // getResCode()/getCode() 可取。星辰之前完全不预抓，依赖 getResCode() 的规则必空。
                String fetchUrl = stripEmptyPrefix(url);
                String preFetched = "";
                if (fetchUrl.startsWith("http://") || fetchUrl.startsWith("https://")) {
                    try {
                        // 8.83 对齐：预抓失败走失败路径——真因进引擎错误通道供空态展示，
                        // 绝不在空正文上照常求值（自创的"吞失败"行为，已删）
                        preFetched = prefetchBody(fetchUrl);
                    } catch (Throwable t) {
                        String msg = String.valueOf(t);
                        SpiderDebug.log("hk", "home prefetch failed rule=%s err=%s", rule.getTitle(), msg);
                        jsRuntime.noteFetchFailure(msg);
                        return new ArrayList<>();
                    }
                }
                jsRuntime.setEvalResCodeBody(preFetched);
                try {
                    return jsRuntime.parseList(findRule, url, page, listEvalScope);
                } finally {
                    jsRuntime.setEvalResCodeBody(null);
                }
            }
            String html = getMarked(url);
            return selector.parseList(html, findRule, url);
        } catch (HkJsRuntime.ListEvalSupersededException cancelled) {
            // 被本面更新的加载顶替（取消语义）：不记 home failed——继任发自会
            // 交付；本 API 无 throws，收口形态与本方法既有失败收口同为回空表，
            // 但语义是调用方丢弃、不是运行时伪造结果（账 §六.1）。
            SpiderDebug.log("hk", "home superseded rule=%s", rule.getTitle());
            return new ArrayList<>();
        } catch (Throwable e) {
            Logger.t(TAG).d("home failed: %s", e.getMessage());
            // 死因隐身器拆掉：home 的异常以前只进 logcat，文件日志里必须留原文
            SpiderDebug.log("hk", "home failed rule=%s err=%s", rule.getTitle(), String.valueOf(e));
            return new ArrayList<>();
        }
    }

    /**
     * 搜索（单页内置搜索用）。8.83 同款：返回独立 SearchResult。
     */
    public List<SearchResult> search(String keyword, int page) {
        try {
            rule.validate();
            if (!rule.hasSearch()) return new ArrayList<>();
            String url = HkHttp.expandSearchUrl(rule.getSearchUrl(), keyword, page);
            String searchFind = rule.getSearchFind();
            if (TextUtils.isEmpty(searchFind)) return new ArrayList<>();
            if (HkSelector.isJsRule(searchFind)) {
                // js: 搜索同理：MY_URL 传原始 url，保留 hiker://empty# 前缀。
                String rawUrl = HkHttp.expandSearchUrl(rule.getSearchUrl(), keyword, page, false);
                jsRuntime.setListContext("search", "", "", "", "", "", "");
                // R1: 8.83 js: 搜索也先抓页，正文进 resCodeMap
                String fetchUrl = stripEmptyPrefix(rawUrl);
                String preFetched = "";
                if (fetchUrl.startsWith("http://") || fetchUrl.startsWith("https://")) {
                    try {
                        // 8.83 对齐：预抓失败走失败路径，绝不在空正文上照常求值（自创行为，已删）
                        preFetched = prefetchBody(fetchUrl);
                    } catch (Throwable t) {
                        String msg = String.valueOf(t);
                        SpiderDebug.log("hk", "search prefetch failed rule=%s err=%s", rule.getTitle(), msg);
                        jsRuntime.noteFetchFailure(msg);
                        return new ArrayList<>();
                    }
                }
                jsRuntime.setEvalResCodeBody(preFetched);
                try {
                    return jsRuntime.parseSearch(searchFind, rawUrl, keyword, page);
                } finally {
                    jsRuntime.setEvalResCodeBody(null);
                }
            }
            String html = getMarked(url);
            return selector.parseSearch(html, searchFind, url);
        } catch (Throwable e) {
            Logger.t(TAG).d("search failed: %s", e.getMessage());
            SpiderDebug.log("hk", "search failed rule=%s err=%s", rule.getTitle(), String.valueOf(e));
            return new ArrayList<>();
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /** 按 class_url 反查 class_name（MY_CLASS_NAME 注入用）。 */
    private String classNameOf(String cls) {
        if (cls == null || cls.isEmpty()) return "";
        for (String[] p : rule.getClassPairs()) {
            if (cls.equals(p[1])) return p[0];
        }
        return "";
    }

    public String getError() {
        return jsRuntime.getError();
    }

    /**
     * R1 预抓取数（8.83 JSEngine.parseHomeCallBack 同款语义）：成功返回正文；
     * 失败或正文为空直接抛异常——调用方按现有失败路径处理，绝不在空正文上求值。
     */
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

    private String prefetchBody(String fetchUrl) throws IOException {
        String body = getMarked(fetchUrl);
        if (body == null || body.isEmpty()) {
            // 加固：预抓路径下正文为空直接按取数失败处理，避免次生 TypeError 盖住真因
            throw new IOException("取数失败：正文为空");
        }
        return body;
    }

    /**
     * R1: 剥离 hiker://empty# 前缀取真实 URL，用于预抓取。
     */
    private static String stripEmptyPrefix(String url) {
        if (url == null) return "";
        String p = "hiker://empty#";
        if (url.startsWith(p)) return url.substring(p.length());
        // 兼容双 # 写法 hiker://empty##
        if (url.startsWith("hiker://empty##")) return url.substring("hiker://empty##".length());
        return url;
    }

    public HkRule getRule() {
        return rule;
    }

    public HkJsRuntime getJsRuntime() {
        return jsRuntime;
    }

    public HkSelector getSelector() {
        return selector;
    }

    public void destroy() {
        // 修法 A：包裹常驻运行时时绝不能销毁它（原版单例语义：常驻引擎生命周期
        // 归 HkEngineManager 管，进程内永不销毁）；旧独立引擎才走原销毁。
        if (!adopted) jsRuntime.destroy();
    }
}
