package com.fongmi.android.tv.ui.hk;

import com.fongmi.android.tv.api.hk.HkCommonParser;
import com.fongmi.android.tv.clash.ReadingProxyGate;
import com.fongmi.android.tv.api.hk.HkHttpParser;
import com.fongmi.android.tv.api.hk.HkJsRuntime;
import com.fongmi.android.tv.api.hk.HkSearchEngine;
import com.fongmi.android.tv.api.hk.SearchResult;
import com.fongmi.android.tv.utils.Task;
import com.google.gson.Gson;

import java.util.List;

import android.util.Log;

/**
 * 照 8.83 SearchModel（ui/search/model/SearchModel.smali，292 行）转写：
 * params = [keyword, engine, page, 可选 loadPreRule 标志]；
 * process 后台执行：page==1 且带标志且标志 JSON 化为 "true" 且 engine.preRule 非空
 * → parsePreRule（异常 → error(三段同为 e.getMessage()) 并中止）；
 * url 模板（search_url + UA 后缀）为空 → error("链接为空"×3)；
 * pageUrl = CommonParser.parsePageClassUrl(模板, page, null) → proxyUrl → 隐私命中改 "hiker://empty"；
 * HttpParser.parseSearchUrlForHtml(keyword, url) 取页：成功先 storeUrl 再后台
 * SearchParser.findList 回灌（key 原样透传），失败 error(msg, "SearchModel-HttpRequestError-msg：" + msg, code) 并记日志。
 * 引擎触点（parsePreRule/proxyUrl/log）经 SearchBridge 接缝由 HkEngineBridge 绑定。
 */
public class HkSearchModel {
    private static final String TAG = "HkSearchModel";

    public static final String SEARCH_BY_RULE = "SEARCH_BY_RULE";

    /** 对应原版 BaseCallback<SearchResult> 在本链路用到的三法。 */
    public interface Callback {
        void bindArrayToView(String key, List<SearchResult> list);

        void bindObjectToView(String key, SearchResult item);

        void error(String title, String msg, String code, Exception e);
    }

    /** 引擎接缝：对应 JSEngine 的 parsePreRule(SearchEngine) / proxyUrl / log。 */
    public interface SearchBridge {
        void parsePreRule(HkSearchEngine engine) throws Exception;

        String proxyUrl(String url, String proxy);

        void log(Object msg, Object rule);
    }

    public static volatile SearchBridge bridge;

    private Object[] mParams;
    private HkArticleListModel.OnUrlParseCallBack urlParseCallBack;

    public HkSearchModel params(Object... args) {
        this.mParams = args;
        return this;
    }

    public Object[] getParams() {
        return mParams;
    }

    public HkSearchModel withUrlParseCallBack(HkArticleListModel.OnUrlParseCallBack callBack) {
        this.urlParseCallBack = callBack;
        return this;
    }

    /** 对应原版 process(key, callback)：HeavyTaskUtil.executeNewTask 后台跑。 */
    public void process(String key, Callback callback) {
        Task.execute(() -> processInBackground(key, callback));
    }

    private void processInBackground(String key, Callback callback) {
        // 参数拆包照 HkArticleListModel 口径加守卫：错位/错型时转 error 回调，不直崩后台任务。
        final String keyword;
        final HkSearchEngine engine;
        final int page;
        try {
            keyword = (String) mParams[0];
            engine = (HkSearchEngine) mParams[1];
            page = (Integer) mParams[2];
        } catch (Exception e) {
            callback.error("参数错误", e.getMessage(), "404", e);
            return;
        }
        // 定向认领提前登记（同 HkArticleListModel 口径）：搜索首发请求同样走
        // enqueue 在调度线程判定，登记须赶在发请求之前；只给归属不决定走向。
        try {
            ReadingProxyGate.recordUrl(engine.getSearch_url(), "hk", engine.getTitle());
        } catch (Throwable ignored) {
        }
        String url = HkHttpParser.getUrlAppendUA(engine.getSearch_url(), engine.getUa());
        if (page == 1 && mParams.length >= 4
                && "true".equals(new Gson().toJson(mParams[3]))
                && HkStringUtil.isNotEmpty(engine.getPreRule())) {
            try {
                SearchBridge b = bridge;
                if (b != null) b.parsePreRule(engine);
            } catch (Exception e) {
                callback.error(e.getMessage(), e.getMessage(), e.getMessage(), e);
                return;
            }
        }
        if (HkStringUtil.isEmpty(url)) {
            callback.error("链接为空", "链接为空", "链接为空", new Exception("链接为空"));
            return;
        }
        String pageUrl = HkCommonParser.parsePageClassUrl(url, page, null, null);
        SearchBridge b = bridge;
        pageUrl = b != null ? b.proxyUrl(pageUrl, engine.getProxy()) : pageUrl;
        if (HkSearchParser.privacyReplacer != null && HkSearchParser.privacyReplacer.test(engine.getTitle())) {
            pageUrl = "hiker://empty";
        }
        final String requestUrl = pageUrl;
        // 补登记最终请求 host：入口处登记的是 search_url 模板的 host，模板经
        // parsePageClassUrl/proxyUrl 改写后最终请求 host 可能与模板不同，届时认领
        // 按最终 host 查表落空、搜索首发又绕回直连（与列表路同口径补登）。
        try {
            ReadingProxyGate.recordUrl(requestUrl, "hk", engine.getTitle());
        } catch (Throwable ignored) {
        }
        HkHttpParser.parseSearchUrlForHtml(keyword, requestUrl, new HkHttpParser.OnSearchCallBack() {
            @Override
            public void onSuccess(String successUrl, String html) {
                if (urlParseCallBack != null) {
                    urlParseCallBack.storeUrl(successUrl == null ? "" : successUrl);
                }
                Task.execute(() -> HkSearchParser.findList(successUrl, keyword, engine, html, page,
                        new HkSearchParser.SearchCallback() {
                            @Override
                            public void showData(List<SearchResult> list) {
                                callback.bindArrayToView(key, list);
                            }

                            @Override
                            public void showErr(String msg) {
                                callback.error(msg, msg, msg, new Exception(msg));
                            }

                            @Override
                            public void onUpdate(String updateKey, String value) {
                                SearchResult item = new SearchResult();
                                item.setTitle(value);
                                callback.bindObjectToView(updateKey, item);
                            }
                        }));
            }

            @Override
            public void onFailure(int code, String msg) {
                Log.d(TAG, String.format("SearchModel-HttpRequestError-msg：%s", msg));
                callback.error(msg, "SearchModel-HttpRequestError-msg：" + msg,
                        String.valueOf(code), new Exception(msg));
                SearchBridge br = bridge;
                if (br != null) br.log(msg, engine);
            }
        });
    }
}
