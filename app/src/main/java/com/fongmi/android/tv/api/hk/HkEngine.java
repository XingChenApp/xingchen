package com.fongmi.android.tv.api.hk;

import android.text.TextUtils;

import com.orhanobut.logger.Logger;

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

    public HkEngine(HkRule rule) throws Exception {
        this.rule = rule;
        this.jsRuntime = new HkJsRuntime(rule);
        this.jsRuntime.init();
        this.selector = jsRuntime.newSelector();
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
                // pageParams：hiker://page/ 子页面由点击条目的 extra 注入 MY_PARAMS
                jsRuntime.setListContext("home", safe(cls), classNameOf(cls), rule.getPageParams(), safe(area), safe(year), safe(sort));
                return jsRuntime.parseList(findRule, url, page);
            }
            String html = HkHttp.get(url, rule.resolvedUa());
            return selector.parseList(html, findRule, url);
        } catch (Throwable e) {
            Logger.t(TAG).d("home failed: %s", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 搜索（单页内置搜索用）。
     */
    public List<HkItem> search(String keyword, int page) {
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
                return jsRuntime.parseSearch(searchFind, rawUrl, keyword, page);
            }
            String html = HkHttp.get(url, rule.resolvedUa());
            return selector.parseSearch(html, searchFind, url);
        } catch (Throwable e) {
            Logger.t(TAG).d("search failed: %s", e.getMessage());
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
        jsRuntime.destroy();
    }
}
