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
            String url = HkHttp.expandUrl(rule.getUrl(), cls, area, year, sort, page);
            String findRule = rule.getFindRule();
            if (HkSelector.isJsRule(findRule)) {
                return jsRuntime.parseList(findRule, url);
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
                return jsRuntime.parseSearch(searchFind, url, keyword);
            }
            String html = HkHttp.get(url, rule.resolvedUa());
            return selector.parseSearch(html, searchFind, url);
        } catch (Throwable e) {
            Logger.t(TAG).d("search failed: %s", e.getMessage());
            return new ArrayList<>();
        }
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
