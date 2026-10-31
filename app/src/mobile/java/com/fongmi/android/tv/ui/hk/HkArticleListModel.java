package com.fongmi.android.tv.ui.hk;

import android.content.Context;
import android.text.TextUtils;

import com.fongmi.android.tv.api.hk.HkHttpParser;
import com.fongmi.android.tv.clash.ReadingProxyGate;
import com.fongmi.android.tv.api.hk.HkItem;
import com.fongmi.android.tv.api.hk.HkRule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 照 8.83 ArticleListModel（ui/home/model）转写：
 * process 校验参数→打 ticket→WrapBaseCallback 包裹→后台线程跑 lambda$process$0：
 * 第一页且允许时先 parsePreRule（失败即 error 三段同文 + log 并中止），
 * 再 parse()：拼 UA、#memoryPage# 页码记忆、CommonParser 算分页 URL、proxyUrl、
 * HttpParser.parseSearchUrlForHtml 取 HTML，成功后后台 HomeParser.findList 解析回灌。
 * 引擎触点（JSEngine/CommonParser/HeavyTaskUtil/SettingConfig）经 EngineBridge 接缝，
 * 随引擎单例化阶段绑定到转写后的实现；隐私模式（shouldReplacePrivacyRule/指纹解锁）星辰无此子系统，恒走普通规则。
 */
public class HkArticleListModel {

    /** 对应原版 ArticleListModel$OnUrlParseCallBack。 */
    public interface OnUrlParseCallBack {
        void storeUrl(String url);
    }

    /** 对应原版 BaseCallback&lt;ArticleList&gt;。 */
    public interface Callback {
        void bindArrayToView(String key, List<HkItem> list);

        void bindObjectToView(String key, HkItem item);

        void error(String title, String msg, String code, Exception e);

        void loading(boolean show);
    }

    /** 引擎接缝：对应原版 JSEngine/HomeParser/CommonParser/HeavyTaskUtil/SettingConfig 的静态调用面。 */
    public interface EngineBridge {
        void parsePreRule(HkRule rule) throws Exception;

        String proxyUrl(String url, String proxyConfig);

        void log(Object msg, Object extra);

        String parsePageClassUrl(String url, int page, HkRule rule, Map<String, Object> options);

        void findList(String url, String actionType, HkRule rule, String findRule, String html,
                      int page, Map<String, Object> injectMap, Callback callback);

        int getMemoryPage(String url, String title);

        void updateMemoryPage(String url, String title, int page);

        /** 对应原版 JSEngine.getInstance().evalJS(getMyRule(rule) + js, input)。 */
        String evalWithRule(HkRule rule, String js, String input);

        /**
         * 对应原版 ArticleListFragment.lambda$initData$11 的缓存代码重建链：
         * evalJS(getMyRule(rule) + code, url) 求值缓存代码（cacheCode(...) 声明的那段），
         * 结果为 JSON 数组串时 convertArticleLists 转条目；求值无数组产出/失败返回 null，
         * 调用方回落缓存 data。默认无实现（宿主未绑定引擎时缓存只走 data 路径）。
         */
        default List<HkItem> evalCacheCode(HkRule rule, String code, String url) {
            return null;
        }

        /** 对应原版 $15 的 fileSelect 回流求值：evalJS(getMyRule + generateMY(MY_URL=fragment.myUrl) + generateMY(MY_PATH=文件真实路径) + js, input)；默认实现退化为 evalWithRule，引擎绑定阶段覆盖以注入 MY_URL/MY_PATH。 */
        default String evalFileSelect(HkRule rule, String js, String input, String myUrl, String myPath) {
            return evalWithRule(rule, js, input);
        }

        /** 对应原版 PageParser.getUrl(url, extra)：hiker://page/ 链接按页面配置解析为真实 url；默认原样返回，引擎绑定阶段覆盖。 */
        default String pageParserGetUrl(String url, String extraJson) {
            return url;
        }

        /** 对应原版 LazyRuleParser.parse(activity, rule, lazyParts, position, codeAndHeader, myUrl, callback)。 */
        void parseLazyRule(HkRule rule, String[] lazyParts, int position, String codeAndHeader,
                           String myUrl, LazyCallback callback);
    }

    /** 对应原版 BaseParseCallback&lt;String&gt;。 */
    public interface LazyCallback {
        void start();

        void success(String result);

        void error(String msg);
    }

    private static EngineBridge bridge;

    /**
     * 对齐 8.83 的派发面：原版 process 与 $2.onSuccess 都走 HeavyTaskUtil.executeNewTask，
     * 其本体是 ThreadTool.executeNewTask＝GlobalScope.launch(Dispatchers.IO)——弹性 IO 池，
     * 堵一发不占坑。此前借 Task.execute（全 App 共享的固定 5 线程池）：列表求值的调用方
     * 要在 Future.get 上长等（最长 150 秒），几发加载就把共享池占满，连 process 本身
     * 都排不进来（2026-10-31 橘汁真机案放大器之一）。此处用等价的缓存弹性池（守护线程），
     * 只服务本模型的加载链。
     */
    private static final java.util.concurrent.ExecutorService IO_EXECUTOR =
            java.util.concurrent.Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "hk-list-io");
                t.setDaemon(true);
                return t;
            });

    public static void setEngineBridge(EngineBridge engineBridge) {
        bridge = engineBridge;
    }

    public static EngineBridge getEngineBridge() {
        return bridge;
    }

    private int lastPage = -2;
    private long lastPageTicket;
    private final Object lock = new Object();
    private OnUrlParseCallBack urlParseCallBack;
    private volatile long validTicket;

    private Object[] mParams;

    public HkArticleListModel withUrlParseCallBack(OnUrlParseCallBack callBack) {
        this.urlParseCallBack = callBack;
        return this;
    }

    /** 对应原版 BaseModel.params(Context, Object...)：只存参数。 */
    public HkArticleListModel params(Context context, Object... args) {
        this.mParams = args;
        return this;
    }

    public int getLastPage() {
        return lastPage;
    }

    public void setLastPage(int lastPage) {
        this.lastPage = lastPage;
    }

    /** 照抄原版 process。 */
    public void process(String key, Callback callback) {
        if (mParams == null || mParams.length < 3) {
            callback.error("参数错误", "mParams格式不正确", "404", null);
            return;
        }
        synchronized (lock) {
            validTicket = System.currentTimeMillis();
        }
        WrapBaseCallback wrap = new WrapBaseCallback(callback, validTicket);
        IO_EXECUTOR.execute(() -> processInBackground(wrap, key));
    }

    /** 照抄原版 lambda$process$0。 */
    private void processInBackground(WrapBaseCallback wrap, String key) {
        final int page;
        final HkRule rule;
        try {
            page = (Integer) mParams[0];
            Boolean isRefresh = (Boolean) mParams[1];
            rule = (HkRule) mParams[2];
        } catch (Exception e) {
            wrap.error("参数错误", e.getMessage(), "404", e);
            return;
        }
        // 诊断：新链加载入口（旧 load start 已不可达），只记日志不改行为
        if (bridge != null) bridge.log("hk: load start rule=" + rule.getTitle()
                + " page=" + page + " key=" + key, rule);
        // 定向认领提前登记（1.4.22 真机实证）：首发请求走 enqueue 在调度线程判定，
        // 此前两处登记（JS 首次求值、带标记 fetch）都晚于它 3 秒以上，认领表为空
        // 判 no-mark 直连、连接回池后整页绕过网关。拿到 rule 先登记主域名，
        // 认领只给归属、走不走仍由逐源开关裁决，没开定向的照旧直连。
        try {
            ReadingProxyGate.recordUrl(rule.getUrl(), "hk", rule.getTitle());
        } catch (Throwable ignored) {
        }
        if (page == 1 && mParams.length >= 4
                && Boolean.FALSE.toString().equals(String.valueOf(mParams[3]))
                && !TextUtils.isEmpty(rule.getPreRule())) {
            try {
                if (bridge != null) bridge.parsePreRule(rule);
            } catch (Exception e) {
                wrap.error(e.getMessage(), e.getMessage(), e.getMessage(), e);
                if (bridge != null) bridge.log(e.getMessage(), rule);
                return;
            }
        }
        boolean fromHistory = mParams.length > 4
                && Boolean.TRUE.toString().equals(String.valueOf(mParams[4]));
        parse(key, page, rule, wrap, fromHistory);
    }

    /** 照抄原版 parse。 */
    private void parse(String key, int page, HkRule rule, Callback callback, boolean fromHistory) {
        String urlWithUa = HkHttpParser.getUrlAppendUA(rule.getUrl(), rule.getUa());
        try {
            Callback effectiveCallback = callback;
            if (!TextUtils.isEmpty(rule.getUrl()) && rule.getUrl().contains("#memoryPage#")) {
                long ticket = System.currentTimeMillis();
                synchronized (lock) {
                    lastPageTicket = ticket;
                }
                if (lastPage == -2 && page == 1) {
                    page = bridge == null ? 1 : bridge.getMemoryPage(rule.getUrl(), rule.getTitle());
                    lastPage = page;
                    HkItem item = new HkItem();
                    item.setTitle(String.valueOf(page));
                    callback.bindObjectToView("updatePage", item);
                } else if (lastPage == page - 1) {
                    lastPage = page;
                    effectiveCallback = new MemoryPageCallback(ticket, rule, page, callback);
                } else {
                    lastPage = page;
                    if (bridge != null) bridge.updateMemoryPage(rule.getUrl(), rule.getTitle(), -1);
                }
            }
            Map<String, Object> options = new HashMap<>();
            options.put("FROM_HISTORY", fromHistory);
            String pageUrl = bridge == null ? urlWithUa
                    : bridge.parsePageClassUrl(urlWithUa, page, rule, options);
            if (bridge != null) pageUrl = bridge.proxyUrl(pageUrl, rule.getProxy());
            // 补登记最终请求 host：processInBackground 登记的是规则主域名（rule.getUrl），
            // parsePageClassUrl/proxyUrl 改写后最终请求 host 可能与主域名不同（如规则自带
            // proxy 改写、镜像域），只登主域名时 explicitProxy/认领按最终 host 查表落空、
            // 整页又绕回直连。发请求前把最终 URL 的 host 也登记给本规则（归属口径不变，
            // 走不走仍由逐源开关裁决）。
            try {
                ReadingProxyGate.recordUrl(pageUrl, "hk", rule.getTitle());
            } catch (Throwable ignored) {
            }
            String findRule = rule.getFindRule();
            final Callback target = effectiveCallback;
            final int finalPage = page;
            final String finalFindRule = findRule;
            HkHttpParser.parseSearchUrlForHtml(pageUrl, new HkHttpParser.OnSearchCallBack() {
                @Override
                public void onFailure(int code, String msg) {
                    target.error("", "ArticleListModel-HttpRequestError-msg：" + msg,
                            String.valueOf(code), null);
                    if (bridge != null) bridge.log(msg, rule);
                }

                @Override
                public void onSuccess(String url, String html) {
                    if (urlParseCallBack != null) {
                        urlParseCallBack.storeUrl(url == null ? "" : url);
                    }
                    IO_EXECUTOR.execute(() -> {
                        try {
                            if (bridge != null) {
                                bridge.findList(url, key, rule, finalFindRule, html, finalPage,
                                        options, target);
                            }
                        } catch (Exception e) {
                            target.error("", "ArticleListModel-解析失败：" + e.toString(), "", e);
                        }
                    });
                }
            });
        } catch (Exception e) {
            callback.error("", "ArticleListModel-HttpRequestError-msg：" + e.toString(), "", e);
            if (bridge != null) bridge.log(e.getMessage(), rule);
        }
    }

    /** 照抄原版 WrapBaseCallback：ticket 失效的回调一律吞掉。 */
    private class WrapBaseCallback implements Callback {
        private final Callback baseCallback;
        private final long ticket;

        WrapBaseCallback(Callback baseCallback, long ticket) {
            this.baseCallback = baseCallback;
            this.ticket = ticket;
        }

        private boolean isValid() {
            synchronized (lock) {
                return ticket == validTicket;
            }
        }

        @Override
        public void bindArrayToView(String key, List<HkItem> list) {
            if (isValid()) baseCallback.bindArrayToView(key, list);
        }

        @Override
        public void bindObjectToView(String key, HkItem item) {
            if (isValid()) baseCallback.bindObjectToView(key, item);
        }

        @Override
        public void error(String title, String msg, String code, Exception e) {
            if (isValid()) baseCallback.error(title, msg, code, e);
        }

        @Override
        public void loading(boolean show) {
            if (isValid()) baseCallback.loading(show);
        }
    }

    /** 照抄原版 ArticleListModel$1：#memoryPage# 续页回调，拿到非空列表且 ticket 有效时回写记忆页码，转发恒执行。 */
    private class MemoryPageCallback implements Callback {
        private final long ticket;
        private final HkRule rule;
        private final int finalPage;
        private final Callback baseCallback;

        MemoryPageCallback(long ticket, HkRule rule, int page, Callback baseCallback) {
            this.ticket = ticket;
            this.rule = rule;
            this.finalPage = page;
            this.baseCallback = baseCallback;
        }

        private boolean isValid() {
            synchronized (lock) {
                return lastPageTicket == ticket;
            }
        }

        @Override
        public void bindArrayToView(String key, List<HkItem> list) {
            if (list != null && !list.isEmpty() && isValid() && bridge != null) {
                bridge.updateMemoryPage(rule.getUrl(), rule.getTitle(), finalPage);
            }
            baseCallback.bindArrayToView(key, list);
        }

        @Override
        public void bindObjectToView(String key, HkItem item) {
            baseCallback.bindObjectToView(key, item);
        }

        @Override
        public void error(String title, String msg, String code, Exception e) {
            baseCallback.error(title, msg, code, e);
        }

        @Override
        public void loading(boolean show) {
            baseCallback.loading(show);
        }
    }
}
