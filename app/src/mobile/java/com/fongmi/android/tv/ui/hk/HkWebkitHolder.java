package com.fongmi.android.tv.ui.hk;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.HttpAuthHandler;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.ui.activity.DownloadActivity;
import com.fongmi.android.tv.ui.activity.HistoryActivity;
import com.fongmi.android.tv.api.hk.HkJsRuntime;
import com.fongmi.android.tv.api.hk.HkLoading;
import com.fongmi.android.tv.api.hk.event.OnBackEvent;
import com.fongmi.android.tv.api.hk.event.home.OnRefreshPageEvent;
import com.fongmi.android.tv.api.hk.event.home.ShowFileChooserEvent;
import com.fongmi.android.tv.api.hk.event.rule.OnRefreshX5HeightEvent;
import com.fongmi.android.tv.api.hk.event.web.BlobDownloadEvent;
import com.fongmi.android.tv.api.hk.event.web.BlobDownloadProgressEvent;
import com.fongmi.android.tv.api.hk.event.web.FindMagnetsEvent;
import com.fongmi.android.tv.api.hk.event.web.OnSaveAdBlockRuleEvent;
import com.fongmi.android.tv.api.hk.event.web.OnSetAdBlockEvent;
import com.fongmi.android.tv.api.hk.event.web.OnSetWebTitleEvent;
import com.fongmi.android.tv.api.hk.event.web.ShowSearchEvent;
import com.fongmi.android.tv.api.hk.event.web.ShowTranslateEvent;
import com.fongmi.android.tv.api.hk.util.HkCodeUtil;

import org.greenrobot.eventbus.EventBus;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import android.util.Log;

/**
 * 照 8.83 IArticleWebHolder + ArticleWebkitHolder（含内部类 $1 WebChromeClient / $2 WebViewClient /
 * $3 WebViewWrapper）合并转写，系统 WebView 版（对应 WebkitParser 的 holder；HkX5WebViewParser 亦共用，
 * 理由见该类注释）。
 * 桥：initWebView 里经 Wrapper 注册 fy_bridge_app 与 via（原版 JsBridgeHolder.init 注册
 * VideoInterface 与 ViaInterface）；VideoInterface 77 法 / 72 名已照官全量转写（G43，含 $1 委托面），
 * 执行器面另经 {@link #registerExecutorBridge()} 补注册 fy_bridge_appd（原版 WebkitExecutor 形态）。
 * 子系统接缝（默认等价/空实现，宿主阶段可换）：JsPluginHelper.loadMyJs 与 AdBlockModel.getBlockJs 走
 * pluginHooks（内置三段 videoUrls 桩/theme/adTouch 已按官形赋默认实现，见 pluginHooks 字段注；
 * global/按域用户插件段咱家无 JSManager 用户脚本存储与管理面，保持空缺记名）；BrowserProxy.proxy
 * 走 browserProxyHook；UAModel 走 uaHook；dealLoadUrl 的 folder/history/download/func 分支走
 * dealLoadHooks（默认实现逐支裁决见 dealLoadHooks 字段注）；setUrl 的 home 域名→IP 替换是 8.83 自有服务
 * （home.haikuoshijie.cn→159.75.120.248），星辰无此资源与场景，未转写（见 setUrl 注释）。
 */
public class HkWebkitHolder implements HkWebViewHelper.ServiceWorkerInterceptor {
    private static final String TAG = "HkWebkitHolder";

    // —— 子系统接缝 ——

    /**
     * JsPluginHelper.loadMyJs + AdBlockModel.getBlockJs 的等价接缝。
     * loadMyJs 照官六参本体对位（官另有五参便捷重载、内部以末参 true 转调六参；承载
     * ArticleWebkitHolder.loadAllJs 直调六参且末参传 false）：末参 globalForAllUrls 只作用于
     * global 用户插件段的 scheme 门——true 时不限 scheme 注入，false 时仅 http(s) 址注入
     * （含 /android_asset/ 的址两形都跳过该段）；onPageEnd 透传 JSManager.getJsByDom 的
     * pageEnd 过滤（false 时只取 start 型插件）。内置三段不消费这两参。
     */
    public interface WebPluginHooks {
        void loadMyJs(Context context, Consumer<String> jsConsumer, String url,
                      Supplier<String> videoUrlsProvider, boolean onPageEnd, boolean globalForAllUrls);

        String getBlockJs(String url);
    }

    /** BrowserProxy.proxy 的等价接缝：返回非 null 即以该响应接管请求。 */
    public interface BrowserProxyHook {
        WebResourceResponse proxy(String myUrl, WebResourceRequest request,
                                  Map<String, Map<String, String>> requestHeaderMap);
    }

    /** UAModel 的等价接缝（按域名调整 UA 的浏览器子系统）。 */
    public interface UaHook {
        /**
         * 官 UAModel.getAdjustUa(url, systemUA) 双参形（WebViewHelper$5.smali:827：
         * 调用方传 systemUA 恒非空，getAdjustUaItem 的 baidu/google 两支吃此参）。
         */
        String getAdjustUa(String url, String systemUA);

        String getUseUa();
    }

    /** dealLoadUrl 中 folder://、history://、download://、func://background 四支的宿主接缝。 */
    public interface DealLoadHooks {
        void openFolder(Activity activity);

        void openHistory(Activity activity);

        void openDownloads(Activity activity);

        void background(Activity activity);
    }

    /**
     * pluginHooks 唯一赋值点（承载初始化路径：本类加载即在位，loadAllJs 两拍恒消费）。
     * 默认实现照官 JsPluginHelper.loadMyJs 六参本体的内置三段组装，资产直读（与官包
     * theme.js/adTouch.js md5 逐字节同，读法同 FyBridge.getInternalJs 的资产直读路）：
     * ①window.videoUrls 桩（provider 供串，官承载 lambda 恒回 ""）→ ②theme.js（立
     * window.fba 别名）→ ③adTouch.js，非空才经 consumer 注入。④global/按域用户插件段：
     * 咱家无 JSManager 用户脚本存储与管理面，保持空缺记名（onPageEnd/globalForAllUrls
     * 两参只作用于该段）；用户覆盖 theme/adTouch 亦随该面缺失不生效（同 getInternalJs 记名）。
     * getBlockJs：官 AdBlockModel 按用户广告规则生成元素隐藏 JS，咱家无其存储面，回 null
     * 记名（网址级拦截已由 HkAdUrlBlocker 在 shouldInterceptRequest 承接，不在此面）。
     */
    public static volatile WebPluginHooks pluginHooks = new WebPluginHooks() {
        @Override
        public void loadMyJs(Context context, Consumer<String> jsConsumer, String url,
                             Supplier<String> videoUrlsProvider, boolean onPageEnd,
                             boolean globalForAllUrls) {
            if (jsConsumer == null) return;
            String videoUrls = videoUrlsProvider == null ? null : videoUrlsProvider.get();
            jsConsumer.accept("(function(){\n\twindow.videoUrls = \""
                    + (videoUrls == null ? "" : videoUrls) + "\";\n})();");
            String theme = readBuiltinAsset("theme.js");
            if (!TextUtils.isEmpty(theme)) jsConsumer.accept(theme);
            String adTouch = readBuiltinAsset("adTouch.js");
            if (!TextUtils.isEmpty(adTouch)) jsConsumer.accept(adTouch);
        }

        @Override
        public String getBlockJs(String url) {
            return null;
        }
    };
    public static volatile BrowserProxyHook browserProxyHook;

    /**
     * UaHook 默认实现（环26 落地面）：adjustUa 走 HkUaModel 按域存档
     * （官 UAModel.getAdjustUaItem 同口，三落点＋尾段三支，C1 复核订正）；
     * getUseUa 读 HkUaModel.useUa 静态位（官 UAModel.getUseUa 同位），
     * 咱家无其设置面恒 ""——此为空值不是两支的关门：官 baidu/google 支
     * 前置正是 useUa 为空且第二参（systemUA）非空（旧注读反已订正）。
     * 仍可由宿主整体替换（volatile 语义不变）。
     */
    public static volatile UaHook uaHook = new UaHook() {
        @Override
        public String getAdjustUa(String url, String systemUA) {
            return HkUaModel.getAdjustUa(url, systemUA);
        }

        @Override
        public String getUseUa() {
            return HkUaModel.getUseUa();
        }
    };

    /**
     * dealLoadHooks 唯一赋值点，四支逐支裁决（官 IArticleWebHolder.dealLoadUrl 落点对位）：
     * folder:// 官落 BookmarkActivity（浏览器书签页）——咱家无书签页与书签存储面，不接页面，
     * 按路由层 openBookmarkPage 的既定口径回「该功能暂不支持」记名；
     * history:// 官落 HistoryListActivity——咱家承载面 HistoryActivity（路由层 openHistory 同面），接；
     * download:// 官落 DownloadRecordsActivity（extra downloaded=true）——咱家承载面
     * DownloadActivity（路由层 openDownloadRecords 同面，其 start 无该 extra 概念），接；
     * func://background 官在宿主为 FilmListActivity 时调其 background()——咱家对应宿主回调
     * HkArticleFragment.HostListener.onBackground（HkListActivity 已实现，含「不支持退到后台」
     * 的既定裁决，账 hk-task-single-20261103），宿主非该型时官亦无动作，接。
     */
    public static volatile DealLoadHooks dealLoadHooks = new DealLoadHooks() {
        @Override
        public void openFolder(Activity activity) {
            HkRouteBlocker.toastShortBottom(activity, "该功能暂不支持");
        }

        @Override
        public void openHistory(Activity activity) {
            HistoryActivity.start(activity);
        }

        @Override
        public void openDownloads(Activity activity) {
            DownloadActivity.start(activity);
        }

        @Override
        public void background(Activity activity) {
            if (activity instanceof HkArticleFragment.HostListener) {
                ((HkArticleFragment.HostListener) activity).onBackground();
            }
        }
    };

    /** 内置资产直读（供 pluginHooks 默认实现；同 FyBridge.readAsset 口径，失败回 ""）。 */
    private static String readBuiltinAsset(String name) {
        try {
            java.io.InputStream in = App.get().getAssets().open(name);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    public interface ProgressListener {
        void onPageStarted(String url);

        void onPageFinished(String url);

        void onProgressChanged(String url, int progress);
    }

    // —— IArticleWebHolder 字段 ——
    protected String extra;
    protected Consumer<String> finishPageConsumer;
    protected FyBridge jsBridgeHolder;
    protected String lastDom = "";
    protected Consumer<String> lazyRuleConsumer;
    protected WeakReference<Activity> reference;
    protected String refererPolicy;
    protected int setSystemUiVisibility;
    protected String systemUA = "";
    protected HkX5Extra x5Extra;

    /** 8.83 shouldOverrideUrlLoading 同款：x5Extra.urlInterceptor 的求值器（JSEngine.evalJS(code, url)）。 */
    public interface UrlInterceptorEvaluator {
        String eval(String code, String url);
    }

    private UrlInterceptorEvaluator urlInterceptorEvaluator;

    public void setUrlInterceptorEvaluator(UrlInterceptorEvaluator e) {
        this.urlInterceptorEvaluator = e;
    }

    /** 原版 BridgeListener.open 同款：fy_bridge_app.open({title,url}) 的页面打开接缝。 */
    public interface BridgeOpenHandler {
        void openBridgePage(String title, String url);

        /**
         * 环26 残差三件落地面（官 $1.open(DetailPage) → toNextPage／直组支的
         * picUrl、newWindow、windowId）：picUrl＝DetailPage.getImageUrl()
         * （pic_url＞pic＞picUrl 序）、extraJson＝DetailPage.extra 的 JSON 串
         * （newWindow/windowId 在其中，toNextPage 的 getNextPage 亦吃整串）。
         * 未覆写的宿主回落两参形（既有行为不变）。
         */
        default void openBridgePage(String title, String url, String picUrl, String extraJson) {
            openBridgePage(title, url);
        }

        /**
         * 官 $1.open(DetailPage) → toNextPage λ16 的 pages 覆写件（C1 复核订正）：
         * pages＝DetailPage.getPages() 原串；宿主在 getNextPage 组出的子规则上、
         * pages 非空时 rule.setPages(pages) 覆写（λ16 smali:1801 区）。
         * 未覆写的宿主回落四参形（pages 覆写不生效，属未接宿主自担）。
         */
        default void openBridgePage(String title, String url, String picUrl, String extraJson,
                                    String pages) {
            openBridgePage(title, url, picUrl, extraJson);
        }

        /** 原版 $1.toDetailPage / newPage 同款：以一条临时规则打开列表页（page 链由实现方分发）。 */
        default void openDetailRule(String url, String title, String findRule,
                                    String colType, String group, String preRule) {
        }
    }

    private BridgeOpenHandler bridgeOpenHandler;

    public void setBridgeOpenHandler(BridgeOpenHandler h) {
        this.bridgeOpenHandler = h;
        if (jsBridgeHolder != null) jsBridgeHolder.setOpenHandler(h);
    }

    /**
     * 网页桥的引擎接缝（原版 JsBridgeHolder$1 直调进程单例 JSEngine；咱家运行时按规则分实例，
     * 由宿主以当前页规则的运行时供给）。未接时桥的引擎面按原版「无上下文回空」口径返回。
     */
    public interface BridgeEngine {
        HkJsRuntime runtime();
    }

    private BridgeEngine bridgeEngine;

    public void setBridgeEngine(BridgeEngine e) {
        this.bridgeEngine = e;
        if (jsBridgeHolder != null) jsBridgeHolder.setEngine(e);
    }

    /** 网页桥的图片查看接缝（原版 showPic 走 MyXpopup 查看器、setImgUrls 走 PictureListActivity）。 */
    public interface BridgePicHandler {
        void showPic(String url);

        void showPics(List<String> urls, String pageUrl);
    }

    private BridgePicHandler bridgePicHandler;

    public void setBridgePicHandler(BridgePicHandler h) {
        this.bridgePicHandler = h;
        if (jsBridgeHolder != null) jsBridgeHolder.setPicHandler(h);
    }

    /**
     * 原版 WebkitExecutor 在 ArticleWebkitHolder 之外另注册的一面：fy_bridge_appd
     * （WebkitExecutorBridge，仅 finishParse）。解析器 holder 在 initWebView 后调用。
     */
    public void registerExecutorBridge() {
        if (jsBridgeHolder != null) jsBridgeHolder.registerAppd();
    }

    /**
     * 网页桥的播放接缝（原版 JsBridgeHolder.startPlayVideo → PlayerChooser.startPlayer）。
     * 桥内前置步（静音脚本注入、isOnPause 门、网页标题计算）照官在本类完成，
     * 真正起播交宿主；未接时只走前置步（记名，见第八阶段回执）。
     */
    public interface BridgePlayHandler {
        void playVideo(String title, String url);

        void playVideos(String json);
    }

    private BridgePlayHandler bridgePlayHandler;

    public void setBridgePlayHandler(BridgePlayHandler h) {
        this.bridgePlayHandler = h;
        if (jsBridgeHolder != null) jsBridgeHolder.setPlayHandler(h);
    }

    // —— ArticleWebkitHolder 字段 ——
    /**
     * 嗅探模式：只有两个隐藏解析器（HkX5WebViewParser / HkWebkitParser）在建 holder 后置位，
     * 可见网页浏览不置。置位后 shouldInterceptRequest 额外套用 SNIFF_BLOCK_RULES，
     * 且 onPageCommitVisible 提前注入 x5Extra 的轮询脚本（脚本自带 window._cc 守卫，
     * 与 onPageFinished 的注入互为幂等）。来由（账 bestjav-play-freeze-20261101 续单）：
     * 8.83 的同位 holder 有 AdblockPlus 引擎在拦截层把广告/追踪子资源掐掉，播放页的
     * load 事件不被广告网络绑架；星辰转写时该层只剩空接缝（pluginHooks 从未接线），
     * 嗅探页整页等广告子资源、onPageFinished 迟迟不来、轮询永不启动。此处用域名级
     * 清单在嗅探模式下复现其对嗅探链的效果（清单取自 BestJav /p/ 页实测子资源普查
     * 与嗅探模块自带的 isBad 域名族），不动可见浏览行为。
     */
    private boolean sniffMode;
    private boolean interceptFailNotified;

    public boolean isSniffMode() {
        return sniffMode;
    }

    public void setSniffMode(boolean sniffMode) {
        this.sniffMode = sniffMode;
    }

    /** 嗅探模式内置拦截清单：广告/追踪网络与弹窗源。经 HkAdUrlBlocker.shouldBlockSniff
     * 按条目形态分判：裸域名条目只走主机锚（退出子串源、正片参数含域名子串不误杀）、
     * 余条目走官规则语法子串判（补遗七，口径经 G62 复核订正）。 */
    private static final java.util.List<String> SNIFF_BLOCK_RULES = java.util.Arrays.asList(
            "googletagmanager.com", "google-analytics.com", "doubleclick.net",
            "googlesyndication.com", "jads.co", "juicyads.com", "wpadmngr.com",
            "vivodemisrentas.net", "exoclick.com", "tsyndicate.com", "trafficjunky.com",
            "adsterra", "popads.net", "magsrv.com", "smartpop",
            "stripchat.com", "chaturbate.com", "tapioni", "mavrtracktor",
            "imasdk", "javhd-trk", "sandwichconscientiousroadside");

    private final AtomicBoolean hasLoadJsOnPageEnd = new AtomicBoolean(false);
    private int height;
    private String myUrl = "";
    private ProgressListener progressListener;
    private final Map<String, Map<String, String>> requestHeaderMap = new HashMap<>();
    private String url;
    private List<String> urls = Collections.synchronizedList(new ArrayList<>());
    private WebView webView;

    public HkWebkitHolder() {
    }

    public HkWebkitHolder(WebView webView) {
        this.webView = webView;
    }

    // —— IArticleWebHolder 方法 ——

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public Consumer<String> getFinishPageConsumer() {
        return finishPageConsumer;
    }

    public void setFinishPageConsumer(Consumer<String> finishPageConsumer) {
        this.finishPageConsumer = finishPageConsumer;
    }

    public Consumer<String> getLazyRuleConsumer() {
        return lazyRuleConsumer;
    }

    public void setLazyRuleConsumer(Consumer<String> lazyRuleConsumer) {
        this.lazyRuleConsumer = lazyRuleConsumer;
    }

    public HkX5Extra getX5Extra() {
        return x5Extra;
    }

    public void setX5Extra(HkX5Extra x5Extra) {
        this.x5Extra = x5Extra;
    }

    protected String getExtraUa() {
        return x5Extra == null ? null : x5Extra.getUa();
    }

    protected String getUserAgentString() {
        if (webView != null) return webView.getSettings().getUserAgentString();
        return "";
    }

    protected void setUserAgentString(String ua) {
        if (webView != null) webView.getSettings().setUserAgentString(ua);
    }

    /**
     * 照 WebViewHelper$5.onPageStarted 的 UA 段（smali:827 区）：dom 变化即
     * 以 systemUA 为第二参调 UAModel.getAdjustUa，非空用之，否则回落
     * useUa/systemUA。官此门只有 dom 非空且与 lastDom 不同两件——旧形另加
     * 的 getExtraUa() 非空前置官无此门（extra UA 是另一路的入参、不是读链
     * 开关），C1 复核订正已摘。
     */
    protected void checkUa(String url) {
        String dom = HkStringUtil.getDom(url);
        if (dom != null && !dom.equals(lastDom)) {
            UaHook hook = uaHook;
            String adjustUa = hook == null ? null : hook.getAdjustUa(url, systemUA);
            if (!TextUtils.isEmpty(adjustUa)) {
                if (!adjustUa.equals(getUserAgentString())) {
                    Log.d(TAG, "onPageStarted: getAdjustUa");
                    setUserAgentString(adjustUa);
                }
            } else {
                String useUa = hook == null ? "" : hook.getUseUa();
                if (!TextUtils.isEmpty(useUa)) {
                    if (!useUa.equals(getUserAgentString())) {
                        Log.d(TAG, "onPageStarted: getUseUa");
                        setUserAgentString(useUa);
                    }
                } else if (HkStringUtil.isNotEmpty(systemUA) && !systemUA.equals(getUserAgentString())) {
                    Log.d(TAG, "onPageStarted: systemUA");
                    setUserAgentString(systemUA);
                }
            }
        }
        lastDom = dom;
    }

    /**
     * 官 WebViewHelper$5.lambda$onPageFinished$2 转写（环26 落地面）：后台取
     * 本页已存表单项，官门＝非空且条目数 &gt;1 才回填；脚本逐字照官原文
     * （aaaa1 函数：key 按 ',' 切，'#'/'tagInput,'/name 三支，立即执行＋
     * setTimeout 500 再执行），JSON 串经 escapeJavaScriptString 后嵌
     * JSON.parse，注入走 view.post（官同形）。
     */
    private void fillFormInputs(WebView view, String url) {
        com.fongmi.android.tv.utils.Task.execute(() -> {
            java.util.Map<String, String> inputs = HkFormInputStore.getFormInputs(url);
            if (inputs == null || inputs.isEmpty() || inputs.size() <= 1) return;
            String json = new org.json.JSONObject(inputs).toString();
            String script = "(function(){\nfunction aaaa1(){\n   let json1230 = JSON.parse(\""
                    + FyBridge.escapeJavaScriptString(json)
                    + "\");\n   for(let key of Object.keys(json1230)) {\n    let v = json1230[key];\n    let b = key.split(',');\n    let index = (b.length > 1 ? parseInt(b[1]) : 0) || 0;\n    if(key.startsWith('#')) {\n      document.querySelectorAll(b[0])[index].value = v;\n    } else if(key.startsWith('tagInput,')) {\n      document.querySelectorAll('input')[index].value = v;\n    } else {\n      document.querySelectorAll('input[name=' + b[0] + ']')[index].value = v;\n    }\n   }\n}\naaaa1();\nsetTimeout(aaaa1, 500);\n})();";
            view.post(() -> view.evaluateJavascript(script, null));
        });
    }

    /** 照 IArticleWebHolder.dealLoadUrl：非 http 链接在 WebView 内的分发。 */
    protected void dealLoadUrl(String url) {
        if (url.contains("@lazyRule=")) {
            if (lazyRuleConsumer != null) lazyRuleConsumer.accept(url);
            return;
        }
        if (url.startsWith("hiker://")) {
            String route = url.replace("hiker://", "");
            Activity act = reference == null ? null : reference.get();
            if (route.startsWith("home") || route.startsWith("search")) {
                String decoded = url;
                try {
                    decoded = URLDecoder.decode(url, "UTF-8");
                } catch (UnsupportedEncodingException ignored) {
                }
                HkRouteBlocker.isRoute(act, decoded);
            } else {
                HkRouteBlocker.isRoute(act, url);
            }
            return;
        }
        Activity act = reference == null ? null : reference.get();
        DealLoadHooks hooks = dealLoadHooks;
        if (url.equals("folder://")) {
            if (hooks != null && act != null) hooks.openFolder(act);
            return;
        }
        if (url.equals("history://")) {
            if (hooks != null && act != null) hooks.openHistory(act);
            return;
        }
        if (url.equals("download://")) {
            if (hooks != null && act != null) hooks.openDownloads(act);
            return;
        }
        if (url.startsWith("web://")) {
            Context ctx = act != null ? act : App.get();
            HkFuck.GoWebHook hook = HkFuck.goWebHook;
            if (hook != null) hook.goWeb(ctx, HkStringUtil.replaceOnce(url, "web://", ""));
            return;
        }
        if (url.startsWith("func://background")) {
            if (hooks != null && act != null) hooks.background(act);
        }
    }

    // —— ArticleWebkitHolder 方法 ——

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public ProgressListener getProgressListener() {
        return progressListener;
    }

    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    public String getUrl() {
        return url;
    }

    /**
     * 照 ArticleWebkitHolder.setUrl。原版在赋值前有一段自有服务处理：当前 Activity 资源串
     * home_domain(home.haikuoshijie.cn) 与 url 域名不等时……实为相等时把域名替换为 home_ip(159.75.120.248)，
     * 属 8.83 官网域名直连 IP 的处理，星辰无此资源/场景，此处只保留赋值本体。
     */
    public void setUrl(String url) {
        this.url = url;
    }

    public List<String> getUrls() {
        return urls;
    }

    public void setUrls(List<String> urls) {
        this.urls = urls;
    }

    public WebView getWebView() {
        return webView;
    }

    public void setWebView(WebView webView) {
        this.webView = webView;
    }

    public void initWebView(Activity activity) {
        if (reference != null) reference.clear();
        reference = new WeakReference<>(activity);
        if (Build.VERSION.SDK_INT >= 28) {
            String processName = android.app.Application.getProcessName();
            if (!activity.getPackageName().equals(processName)) {
                WebView.setDataDirectorySuffix(processName);
            }
        }
        initWebSettings(webView.getSettings());
        systemUA = webView.getSettings().getUserAgentString();
        webView.setWebChromeClient(new ChromeClient());
        webView.setWebViewClient(new Client());
        jsBridgeHolder = new FyBridge(reference, new Wrapper());
        HkPlayerChooser.registerCurrentWrapper(activity, jsBridgeHolder.getWrapper());
        if (bridgeOpenHandler != null) jsBridgeHolder.setOpenHandler(bridgeOpenHandler);
        if (bridgeEngine != null) jsBridgeHolder.setEngine(bridgeEngine);
        if (bridgePicHandler != null) jsBridgeHolder.setPicHandler(bridgePicHandler);
        if (bridgePlayHandler != null) jsBridgeHolder.setPlayHandler(bridgePlayHandler);
    }

    public void onDestroy() {
        HkWebViewHelper.removeServiceWorkerClient(this);
        // 核查 136b E1 补注销：initWebView 登记的当前页 Wrapper 在此摘除
        // （unregister 仅当登记在案的正是本 Wrapper 才清，不误清别页新登记）；
        // 不摘时宿主存活而 holder 已毁的窗内，头表来源口会读到旧 Wrapper。
        try {
            Activity a = reference == null ? null : reference.get();
            if (a != null && jsBridgeHolder != null) {
                HkPlayerChooser.unregisterCurrentWrapper(a, jsBridgeHolder.getWrapper());
            }
        } catch (Throwable ignored) {
        }
        if (getWebView() == null) return;
        getWebView().onPause();
        getWebView().destroy();
    }

    private void initWebSettings(WebSettings settings) {
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setUseWideViewPort(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(-1);
        settings.setMixedContentMode(0);
        HkWebViewHelper.addServiceWorkerClient(this);
    }

    /** 照 ArticleWebkitHolder.loadAllJs：JsPluginHelper.loadMyJs 六参（末参官承载恒传 false） + AdBlockModel.getBlockJs 注入。 */
    private void loadAllJs(WebView view, String url, boolean onPageEnd) {
        Context context = reference == null ? null : reference.get();
        Consumer<String> consumer = js -> view.evaluateJavascript(js, null);
        Supplier<String> provider = () -> "";
        WebPluginHooks hooks = pluginHooks;
        if (hooks != null) hooks.loadMyJs(context, consumer, url, provider, onPageEnd, false);
        String blockJs = hooks == null ? null : hooks.getBlockJs(url);
        if (!TextUtils.isEmpty(blockJs)) {
            view.evaluateJavascript(blockJs, null);
        }
    }

    /** 照 ArticleWebkitHolder.shouldInterceptRequest（同时是 ServiceWorker 拦截入口）。 */
    @Override
    public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
        try {
            Activity act = reference == null ? null : reference.get();
            if (webView == null || act == null || act.isFinishing()) {
                return HkWebViewHelper.RESPONSE_IGNORE;
            }
            String reqUrl = request.getUrl().toString();
            requestHeaderMap.put(reqUrl, request.getRequestHeaders());
            urls.add(reqUrl);
            if (getX5Extra() != null && com.fongmi.android.tv.api.hk.HkParserKit.isNotEmpty(getX5Extra().getBlockRules())) {
                if (HkAdUrlBlocker.shouldBlock(getX5Extra().getBlockRules(), lastDom, reqUrl)) {
                    return new WebResourceResponse(null, null, (java.io.InputStream) null);
                }
            }
            // 全局拦截表（G48）：导入规则附带 adBlockUrls 经确认并入的存量，
            // 官 AdUrlBlocker 单例表在网页层与规则自带 blockRules 并列消费、不限嗅探模式。
            if (HkAdUrlBlocker.shouldBlockGlobal(lastDom, reqUrl)) {
                return new WebResourceResponse(null, null, (java.io.InputStream) null);
            }
            if (sniffMode && HkAdUrlBlocker.shouldBlockSniff(SNIFF_BLOCK_RULES, lastDom, reqUrl)) {
                return new WebResourceResponse(null, null, (java.io.InputStream) null);
            }
        } catch (Exception e) {
            // 拦截处理失败不再照常放行装没事（残项处置 2026-11-14）：页面
            // 仍走下方代理放行，但失败每页只提示一次，广告拦截/嗅探记录
            // 可能失效这一事实必须显形。
            Log.e(TAG, "shouldInterceptRequest 拦截处理失败：" + request.getUrl(), e);
            if (!interceptFailNotified) {
                interceptFailNotified = true;
                Activity act2 = reference == null ? null : reference.get();
                if (act2 != null) {
                    HkRouteBlocker.toastShortBottom(act2, "资源拦截处理失败，广告拦截可能未生效");
                }
            }
        }
        BrowserProxyHook hook = browserProxyHook;
        if (hook != null) return hook.proxy(myUrl, request, requestHeaderMap);
        // 网页代理规则的默认消费面（8.83 BrowserProxy.proxy 等价）：规则 JS 经 addWebProxyRule
        // 注册的规则由 api/hk/browser/HkBrowserProxy 维护，hook 未被外部接管时回落到此；
        // 命中规则（match 全串正则、静态资源只认 force）由宿主抓取改写，未命中回 null 走原生加载。
        return com.fongmi.android.tv.api.hk.browser.HkBrowserProxy
                .proxyRequest(myUrl, request, request.getRequestHeaders());
    }

    // —— $2 WebViewClient ——

    private class Client extends WebViewClient {

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            myUrl = url;
            interceptFailNotified = false;
            if (progressListener != null) progressListener.onPageStarted(url);
            requestHeaderMap.clear();
            refererPolicy = null;
            urls.clear();
            hasLoadJsOnPageEnd.set(false);
            checkUa(url);
            loadAllJs(view, view.getUrl(), false);
            if (x5Extra != null && HkStringUtil.isNotEmpty(x5Extra.getJs()) && x5Extra.isJsLoadingInject()) {
                view.evaluateJavascript(x5Extra.getJs(), null);
            }
            super.onPageStarted(view, url, favicon);
        }

        /**
         * 嗅探模式提前注入：主文档一提交渲染就把轮询脚本注进去，不再单等整页
         * load 完（onPageFinished）——播放器脚本是同步脚本、主文档提交时多半已
         * 执行，轮询式自身的 250ms×120 节奏负责等播放器就绪；脚本带
         * window._cc 守卫，与 onPageFinished 的重复注入幂等。8.83 本体的
         * onPageCommitVisible 只转发监听、不注脚本，此为星辰嗅探链的刻意加项
         *（账 bestjav-play-freeze-20261101 续单），仅 sniffMode 生效。
         */
        @Override
        public void onPageCommitVisible(WebView view, String url) {
            if (sniffMode && x5Extra != null && HkStringUtil.isNotEmpty(x5Extra.getJs())) {
                view.evaluateJavascript(x5Extra.getJs(), null);
            }
            super.onPageCommitVisible(view, url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (progressListener != null) progressListener.onPageFinished(url);
            if (!hasLoadJsOnPageEnd.get()) {
                hasLoadJsOnPageEnd.set(true);
                loadAllJs(view, url, true);
            }
            if (x5Extra != null && HkStringUtil.isNotEmpty(x5Extra.getJs())) {
                view.evaluateJavascript(x5Extra.getJs(), null);
            }
            // 环26 落地面（官 WebViewHelper$5.onPageFinished 同位）：saveForm 官
            // 设置默认开、咱家无其设置面故恒开（设置面记名）——先注 forminput.js
            // （资产与官逐字节同，钩表单输入经桥 saveFormInputItem 存档），
            // 再后台回填已存表单项（fillFormInputs，官 lambda$onPageFinished$2）。
            String formJs = readBuiltinAsset("forminput.js");
            if (!TextUtils.isEmpty(formJs)) view.evaluateJavascript(formJs, null);
            fillFormInputs(view, url);
            if (finishPageConsumer != null) finishPageConsumer.accept(url);
            super.onPageFinished(view, url);
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.proceed();
        }

        @Override
        public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
            String username = null;
            String password = null;
            if (handler.useHttpAuthUsernamePassword() && view != null) {
                String[] creds = view.getHttpAuthUsernamePassword(host, realm);
                if (creds != null && creds.length == 2) {
                    username = creds[0];
                    password = creds[1];
                }
            }
            if (username != null && password != null) {
                handler.proceed(username, password);
                return;
            }
            Activity act = reference == null ? null : reference.get();
            if (view != null && act != null && !act.isFinishing()) {
                showHttpAuthDialog(view, handler, host, realm, act);
            } else {
                handler.cancel();
            }
        }

        /** 原版 InputPopup("网页登录", 用户名/密码) 的原生等价：确定 → 存凭据并 proceed，取消 → handler.cancel()。 */
        private void showHttpAuthDialog(WebView view, HttpAuthHandler handler, String host, String realm, Activity act) {
            LinearLayout box = new LinearLayout(act);
            box.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (16 * act.getResources().getDisplayMetrics().density);
            box.setPadding(pad, pad, pad, 0);
            EditText userEdit = new EditText(act);
            userEdit.setHint("用户名");
            userEdit.setInputType(InputType.TYPE_CLASS_TEXT);
            EditText passEdit = new EditText(act);
            passEdit.setHint("密码");
            passEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            box.addView(userEdit);
            box.addView(passEdit);
            new HkDialogs(act)
                    .setTitle("网页登录")
                    .setView(box)
                    .setCancelable(false)
                    .setPositiveButton("确定", (dialog, which) -> {
                        String user = userEdit.getText().toString();
                        String pass = passEdit.getText().toString();
                        view.setHttpAuthUsernamePassword(host, realm, user, pass);
                        handler.proceed(user, pass);
                    })
                    .setNegativeButton("取消", (dialog, which) -> handler.cancel())
                    .show();
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            WebResourceResponse response = HkWebkitHolder.this.shouldInterceptRequest(request);
            if (response != null) return response;
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            // 8.83 ArticleWebViewHolder.shouldOverrideUrlLoading：x5Extra.urlInterceptor 优先
            String x5Code = x5Extra != null ? x5Extra.getUrlInterceptor() : null;
            if (x5Code != null && !x5Code.isEmpty()) {
                String result = "";
                try {
                    if (urlInterceptorEvaluator != null) result = urlInterceptorEvaluator.eval(x5Code, url);
                } catch (Throwable ignored) {
                }
                if (result != null && !result.isEmpty() && !"null".equals(result)
                        && !"undefined".equals(result) && !"false".equals(result)) {
                    if (!"true".equals(result)) {
                        // 有代码执行
                        view.evaluateJavascript(result, null);
                    }
                    return true;
                }
            }
            if (url.startsWith("http")) {
                if (request.isRedirect() && x5Extra != null && !x5Extra.isRedirect()) {
                    return true;
                }
                if (Build.VERSION.SDK_INT < 26) {
                    view.loadUrl(url);
                    return true;
                }
                return false;
            }
            dealLoadUrl(url);
            return true;
        }
    }

    // —— $1 WebChromeClient ——

    private class ChromeClient extends WebChromeClient {

        /**
         * 行内 WebView 文件上传（<input type=file>）：官 WebChromeClient 同名覆写把
         * (holder, callback) 包成 ShowFileChooserEvent 发 EventBus，列表页 Fragment
         * 订阅后存回调并起系统选择器（官 ArticleListFragment.onShowFileChooser，
         * 源行 5319-5326）；咱家身份以 WebView 实例承载（见 ShowFileChooserEvent 注）。
         * 回 true 表示已接管；不接管时系统 WebView 无默认选择器、上传恒死。
         */
        @Override
        public boolean onShowFileChooser(WebView webView,
                                         android.webkit.ValueCallback<android.net.Uri[]> filePathCallback,
                                         FileChooserParams fileChooserParams) {
            EventBus.getDefault().post(new ShowFileChooserEvent(webView, filePathCallback, fileChooserParams));
            return true;
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (progressListener != null) progressListener.onProgressChanged(view.getUrl(), newProgress);
            super.onProgressChanged(view, newProgress);
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            Activity act = reference == null ? null : reference.get();
            if (act == null || act.isFinishing() || message == null
                    || message.contains("彩票") || message.contains("野战")) {
                result.cancel();
                return true;
            }
            if (message.contains("当前网页已弹出prompt弹窗") || message.contains("是否忽略后续该弹窗")) {
                result.confirm();
                return true;
            }
            showJsConfirmDialog(act, message, result);
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
            Activity act = reference == null ? null : reference.get();
            if (act == null || act.isFinishing() || message == null) {
                result.cancel();
                return true;
            }
            if (message.contains("当前网页已弹出prompt弹窗") || message.contains("是否忽略后续该弹窗")) {
                result.confirm();
                return true;
            }
            showJsConfirmDialog(act, message, result);
            return true;
        }

        @Override
        public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
            Activity act = reference == null ? null : reference.get();
            if (act == null || act.isFinishing() || message == null) {
                result.cancel();
                return true;
            }
            EditText input = new EditText(act);
            if (defaultValue != null) {
                input.setText(defaultValue);
                input.setSelection(input.getText().length());
            }
            int pad = (int) (16 * act.getResources().getDisplayMetrics().density);
            input.setPadding(pad, pad / 2, pad, 0);
            new HkDialogs(act)
                    .setTitle("来自网页的输入请求")
                    .setMessage(message)
                    .setView(input)
                    .setCancelable(false)
                    .setPositiveButton("确定", (dialog, which) -> result.confirm(input.getText().toString()))
                    .setNegativeButton("取消", (dialog, which) -> result.cancel())
                    .show();
            return true;
        }

        /** 原版 XPopup asConfirm("网页提示", message)（16dp 圆角、不可返回/点外关闭）的原生等价。 */
        private void showJsConfirmDialog(Activity act, String message, JsResult result) {
            new HkDialogs(act)
                    .setTitle("网页提示")
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton("确定", (dialog, which) -> result.confirm())
                    .setNegativeButton("取消", (dialog, which) -> result.cancel())
                    .show();
        }
    }

    // —— $3 WebViewWrapper ——

    /** WebView 方法只能在主线程调：已在主线程直接执行，否则投递主线队列；webView 未就绪时静默放弃。 */
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static void runOnMain(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else MAIN_HANDLER.post(r);
    }

    private class Wrapper implements HkWebViewWrapper {

        @Override
        public void addJavascriptInterface(Object object, String name) {
            WebView wv = webView;
            if (wv == null) return;
            runOnMain(() -> wv.addJavascriptInterface(object, name));
        }

        @Override
        public void evaluateJavascript(String js) {
            WebView wv = webView;
            if (wv == null) return;
            runOnMain(() -> wv.evaluateJavascript(js, null));
        }

        @Override
        public String getCookie(String url) {
            // 处置订正（2026-11-14）：不再吞异常回 null 与无 Cookie 同形——
            // 异常穿出给调用方既有失败出口（JS 桥 getCookie 的 catch 回
            // "error:..." 给网页侧；其余两个调用点自有 try 承接）。
            return CookieManager.getInstance().getCookie(url);
        }

        @Override
        public String getMyUrl() {
            return getStrOnUiThread(holder -> holder.accept(HkWebkitHolder.this.myUrl));
        }

        @Override
        public String getRefererPolicy() {
            return refererPolicy;
        }

        @Override
        public Map<String, Map<String, String>> getRequestHeaderMap() {
            return requestHeaderMap;
        }

        @Override
        public String getSystemUa() {
            return systemUA;
        }

        @Override
        public String getTitle() {
            WebView wv = webView;
            if (wv == null) return null;
            return getStrOnUiThread(holder -> holder.accept(wv.getTitle()));
        }

        @Override
        public String getUrl() {
            WebView wv = webView;
            if (wv == null) return null;
            return getStrOnUiThread(holder -> holder.accept(wv.getUrl()));
        }

        @Override
        public List<String> getUrls() {
            return urls;
        }

        @Override
        public String getUserAgentString() {
            return getStrOnUiThread(holder ->
                    holder.accept(webView == null ? null : webView.getSettings().getUserAgentString()));
        }

        @Override
        public boolean isOnPause() {
            Activity act = reference == null ? null : reference.get();
            return act != null && act.isFinishing();
        }

        @Override
        public void loadUrl(String url) {
            WebView wv = webView;
            if (wv == null) return;
            runOnMain(() -> wv.loadUrl(url));
        }

        @Override
        public void openThirdApp(String url) {
        }

        @Override
        public void reload() {
            WebView wv = webView;
            if (wv == null) return;
            runOnMain(() -> wv.reload());
        }

        @Override
        public void setAppBarColor(String color1, String color2) {
        }

        @Override
        public void setUserAgentString(String ua) {
            WebView wv = webView;
            if (wv == null) return;
            runOnMain(() -> wv.getSettings().setUserAgentString(ua));
        }

        @Override
        public void storeFaviconUrl(String url) {
        }

        @Override
        public void storeRefererPolicy(String policy) {
            refererPolicy = policy;
        }

        @Override
        public void updateLastDom(String dom) {
            lastDom = dom;
        }
    }

    /** ThreadTool.getStrOnUIThread 的等价：主线程直取，子线程投递主线程并阻塞等结果。 */
    private static String getStrOnUiThread(Consumer<Consumer<String>> task) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            String[] box = new String[1];
            task.accept(v -> box[0] = v);
            return box[0];
        }
        String[] box = new String[1];
        boolean[] done = new boolean[1];
        Object lock = new Object();
        MAIN_HANDLER.post(() -> {
            try {
                task.accept(v -> box[0] = v);
            } finally {
                synchronized (lock) {
                    done[0] = true;
                    lock.notifyAll();
                }
            }
        });
        synchronized (lock) {
            try {
                // 带完成旗等待：主线程先跑完时立刻返回，不再白等满 2 秒；只有主线程真卡住才等到超时
                if (!done[0]) lock.wait(2000);
            } catch (InterruptedException ignored) {
            }
        }
        return box[0];
    }

    // —— fy_bridge_app 桥（原版 JsBridgeHolder + VideoInterface + JsBridgeHolder$1） ——

    /**
     * 照 8.83 JsBridgeHolder 转写：构造时经 Wrapper 注册 VideoInterface（"fy_bridge_app"，77 法/72 名）
     * 与 ViaInterface（"via"，仅 addon）；执行器面 fy_bridge_appd 由 {@link #registerAppd()} 另注册
     * （原版 WebkitExecutor 形态）。VideoInterface 本体自办名在本类直落，$1 委托名在本类以
     * wrapper + 引擎接缝（BridgeEngine）+ EventBus 事件面承接；宿主接缝（open/图片/播放）沿既有格局。
     */
    public static class FyBridge {

        private static final java.util.concurrent.ExecutorService BRIDGE_TASKS =
                java.util.concurrent.Executors.newCachedThreadPool();

        private final WeakReference<Activity> reference;
        private final HkWebViewWrapper wrapper;
        private BridgeOpenHandler openHandler;
        private BridgeEngine engine;
        private BridgePicHandler picHandler;
        private BridgePlayHandler playHandler;

        // JsBridgeHolder 同名状态：异步 fetch 结果表、油猴菜单表、三件资产缓存
        private final Map<String, String> requestAsyncMap = new HashMap<>();
        private final Map<String, List<String[]>> greasyForkMenuMap = new HashMap<>();
        private String bindDataToHtml;
        private String vConsole;
        private String jquery;
        private String muteJs;
        private long lastBlockCopyToastAt;

        public FyBridge(WeakReference<Activity> reference, HkWebViewWrapper wrapper) {
            this.reference = reference;
            this.wrapper = wrapper;
            wrapper.addJavascriptInterface(new BridgeObject(), "fy_bridge_app");
            wrapper.addJavascriptInterface(new ViaObject(), "via");
        }

        public void setOpenHandler(BridgeOpenHandler h) {
            this.openHandler = h;
        }

        public void setEngine(BridgeEngine e) {
            this.engine = e;
        }

        public void setPicHandler(BridgePicHandler h) {
            this.picHandler = h;
        }

        public void setPlayHandler(BridgePlayHandler h) {
            this.playHandler = h;
        }

        public HkWebViewWrapper getWrapper() {
            return wrapper;
        }

        public WeakReference<Activity> getReference() {
            return reference;
        }

        /** 油猴菜单表（原版 greasyForkMenuMap）：registerMenuCommand 写入，宿主菜单消费面暂缺（记名）。 */
        public Map<String, List<String[]>> getGreasyForkMenuMap() {
            return greasyForkMenuMap;
        }

        /** WebkitExecutorBridge 等价：只有 finishParse 一法的小面。 */
        public void registerAppd() {
            wrapper.addJavascriptInterface(new AppdObject(), "fy_bridge_appd");
        }

        public class AppdObject {

            @JavascriptInterface
            public void finishParse(String url, String type, String ticket) {
                EventBus.getDefault().post(new HkDestroyEvent(url, type, ticket));
            }
        }

        /** ViaInterface 等价：addon(text) 一法。 */
        public class ViaObject {

            /**
             * 照 JsBridgeHolder 的 via 监听链（lambda$init$1 → lambda$init$0）：
             * text 非空且 Activity 未 finishing → Base64 解码 → UI 线程 → wrapper 未暂停时
             * 打开 JSEditActivity(via=true, viaJs=解码串)。咱家无 JSEditActivity 对应面，
             * 解码与门照走、开编辑页一步无落点（记名，见第八阶段回执）。
             */
            @JavascriptInterface
            public void addon(String text) {
                if (TextUtils.isEmpty(text)) return;
                Activity act = reference == null ? null : reference.get();
                if (act == null || act.isFinishing()) return;
                String decoded = new String(android.util.Base64.decode(text, 2));
                act.runOnUiThread(() -> {
                    if (wrapper.isOnPause()) return;
                    // 官此处 startActivity(JSEditActivity, via/viaJs)；咱家无对应编辑页，止于此（记名）。
                });
            }
        }

        // —— 桥内共用帮手 ——

        private Activity act() {
            return reference == null ? null : reference.get();
        }

        private boolean alive() {
            Activity a = act();
            return a != null && !a.isFinishing();
        }

        private HkJsRuntime rt() {
            BridgeEngine e = engine;
            if (e == null) return null;
            try {
                return e.runtime();
            } catch (Throwable t) {
                return null;
            }
        }

        private String ruleTitle() {
            try {
                com.fongmi.android.tv.api.hk.HkRule cur =
                        com.fongmi.android.tv.ui.hk.HkEngineManager.currentRule();
                if (cur != null && cur.getTitle() != null) return cur.getTitle();
            } catch (Throwable ignored) {
            }
            return "";
        }

        private void toast(String msg) {
            Activity a = act();
            Context ctx = a != null ? a : App.get();
            runOnMain(() -> HkRouteBlocker.toastShortBottom(ctx, msg));
        }

        /** org.adblockplus.libadblockplus.android.Utils.escapeJavaScriptString 转写。 */
        static String escapeJavaScriptString(String s) {
            if (s == null) return null;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '\n') sb.append("\\n");
                else if (c == '\r') sb.append("\\r");
                else if (c == '"' || c == '\'' || c == '\\') sb.append('\\').append(c);
                else sb.append(c);
            }
            return sb.toString().replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
        }

        /** StringUtil.md5 转写（十六进制小写）。 */
        static String md5Hex(String s) {
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
                byte[] digest = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : digest) sb.append(String.format("%02x", b & 0xff));
                return sb.toString();
            } catch (Exception e) {
                return "";
            }
        }

        private String readAsset(String name) {
            try {
                java.io.InputStream in = App.get().getAssets().open(name);
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                in.close();
                return out.toString("UTF-8");
            } catch (Exception e) {
                return "";
            }
        }

        private static String readFileText(String path) {
            try {
                java.io.FileInputStream in = new java.io.FileInputStream(path);
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                in.close();
                return out.toString("UTF-8");
            } catch (Exception e) {
                return "";
            }
        }

        /** JSEngine.getFilesDir() 的桥侧等价（根目录/rules/files/），作 fetch/writeFile 的封锁锚。 */
        private static String filesDirAnchor() {
            return HkJsRuntime.getFilePath("hiker://files/rules/files/");
        }

        /** JsBridgeHolder.getWebTitle：页面标题去空格、截 85 字。 */
        private String webTitle() {
            String t = wrapper.getTitle();
            if (t == null) t = "";
            t = t.replace(" ", "");
            if (t.length() > 0x55) t = t.substring(0, 0x55);
            return t;
        }

        /**
         * ClipboardUtil.copyToClipboardForce(ctx, text, showToast=true) 转写：
         * 空串回落官的 ClearDetectedEvent 一步（咱家无该事件类，略，记名）；
         * SDK>=29 走官的 EditText 聚焦点击变通，低版直设剪贴板，非空且 showToast 时 Toast「复制成功」。
         */
        private void copyToClipboardForce(String text) {
            if (text == null) return;
            Activity a = act();
            Context ctx = a != null ? a : App.get();
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                runOnMain(() -> {
                    android.widget.EditText et = new android.widget.EditText(ctx);
                    et.requestFocus();
                    et.setOnClickListener(v -> setClip(ctx, text, true));
                    et.performClick();
                });
                return;
            }
            setClip(ctx, text, true);
        }

        private void setClip(Context ctx, String text, boolean showToast) {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Label", text));
            }
            if (!TextUtils.isEmpty(text) && showToast) {
                HkRouteBlocker.toastShortBottom(ctx, "复制成功");
            }
        }

        /** JsBridgeHolder.startPlayVideo(String)：静音脚本先行、isOnPause 门、标题走 webTitle。 */
        private void startPlayVideo(String url) {
            Activity a = act();
            if (a == null || a.isFinishing()) return;
            if (muteJs == null) muteJs = readAsset("mute.js");
            if (!TextUtils.isEmpty(muteJs)) wrapper.evaluateJavascript(muteJs);
            if (wrapper.isOnPause()) return;
            // 官此处另有 DetectorManager.putIntoXiuTanLiked 与 HeavyTaskUtil.updateHistoryVideoUrl
            // 两笔浏览器历史侧写，咱家无对应存储（记名）。
            BridgePlayHandler h = playHandler;
            if (h != null) h.playVideo(webTitle(), url);
        }

        /** JsBridgeHolder.startPlayVideo(List)：前置同上，整表交播放接缝（章节解析在宿主侧）。 */
        private void startPlayVideos(String json) {
            Activity a = act();
            if (a == null || a.isFinishing()) return;
            if (muteJs == null) muteJs = readAsset("mute.js");
            if (!TextUtils.isEmpty(muteJs)) wrapper.evaluateJavascript(muteJs);
            if (wrapper.isOnPause()) return;
            BridgePlayHandler h = playHandler;
            if (h != null) h.playVideos(json);
        }

        /**
         * $1.parseLazy(input, pageUrl) 转写：@lazyRule=.js: 段经引擎 evalJS（注入 MY_URL）。
         * 环26 残差订正：MY_URL 拼串改走官同文的 generateMY（JSEngine.generateMY，
         * 咱转写在 HkLazyRuleParser）——旧自拼形把 null 页址拼成 ''、官 generateMY
         * 的 null 支产 `var MY_URL = null;`；余段剥离改 replaceOnce（官原文
         * StringUtils.replaceOnce(parts[1], ".js:", "")，与 startsWith 前提下
         * substring(4) 同果、形态归官）。
         */
        private String parseLazy(String input, String pageUrl) {
            if (TextUtils.isEmpty(input) || !input.contains("@lazyRule=")) return input;
            String[] parts = input.split("@lazyRule=");
            if (parts.length < 2) return input;
            if (!parts[1].startsWith(".js:")) return input;
            HkJsRuntime rt = rt();
            if (rt == null) return input;
            String script = com.fongmi.android.tv.api.hk.HkLazyRuleParser.generateMY("MY_URL", pageUrl)
                    + com.fongmi.android.tv.api.hk.HkParserKit.replaceOnce(parts[1], ".js:", "");
            return rt.evalJS(script, parts[0]);
        }

        public class BridgeObject {

            // —— VideoInterface 本体自办名 ——

            @JavascriptInterface
            public String base64Decode(String s) {
                if (TextUtils.isEmpty(s)) return s;
                return new String(android.util.Base64.decode(s, 2));
            }

            @JavascriptInterface
            public String base64Encode(String s) {
                if (TextUtils.isEmpty(s)) return s;
                return android.util.Base64.encodeToString(s.getBytes(), 2);
            }

            @JavascriptInterface
            public String blockCopy(String url) {
                // DomainConfigKt.blockCopyMode：咱家无 DomainConfig 域配置存储，恒缺省 0（记名）；
                // 模式为 1 且距上次提示超 10 秒时弹「网页写入剪贴板行为被拦截」，结构照官保留。
                int mode = 0;
                String modeStr = String.valueOf(mode);
                if ("1".equals(modeStr)) {
                    long now = System.currentTimeMillis();
                    if (now - lastBlockCopyToastAt > 10000) {
                        lastBlockCopyToastAt = now;
                        toast("网页写入剪贴板行为被拦截");
                    }
                }
                return modeStr;
            }

            @JavascriptInterface
            public String blockUrl(String url, String referer) {
                if (TextUtils.isEmpty(url) || TextUtils.isEmpty(referer)) return "";
                if (HkStringUtil.getSecondDom(url).equals(HkStringUtil.getSecondDom(referer))) return "";
                // DomainConfigKt.getOverrideLoadMode：无域配置存储，恒缺省 0 → 回 ""（记名）。
                int mode = 0;
                if (mode == 1) return "ask";
                if (mode == 2) return "yes";
                return "";
            }

            @JavascriptInterface
            public void clearVar(String key) {
                HkJsRuntime rt = rt();
                if (rt != null) rt.clearVar(key);
            }

            @JavascriptInterface
            public void copy(String msg) {
                copyToClipboardForce(msg);
            }

            @JavascriptInterface
            public void downloadBlob(String url, String fileName, String headerMap, String result) {
                if (TextUtils.isEmpty(url)) return;
                EventBus.getDefault().post(new BlobDownloadEvent(url, fileName, headerMap, result));
            }

            @JavascriptInterface
            public void downloadBlobUpdate(String url, String progress) {
                EventBus.getDefault().post(new BlobDownloadProgressEvent(url, progress));
            }

            @JavascriptInterface
            public String escapeJavaScriptString(String s) {
                return FyBridge.escapeJavaScriptString(s);
            }

            @JavascriptInterface
            public void findMagnetsNotify(String data) {
                if (data != null && data.startsWith("\"[{") && data.endsWith("}]\"")) {
                    data = data.substring(1, data.length() - 1).replace("\\\"", "\"");
                }
                EventBus.getDefault().post(new FindMagnetsEvent(data));
            }

            @JavascriptInterface
            public String getItem(String bucket, String field) {
                HkJsRuntime rt = rt();
                if (rt == null) return null;
                return rt.bridgeKvGet(bucket, field);
            }

            @JavascriptInterface
            public String getRemoteEngines() {
                // RemoteConfig.getEngines：咱家无远程配置服务，回空数组（记名）。
                return "[]";
            }

            @JavascriptInterface
            public String getResourceUrl(String url) {
                try {
                    String name = "_fileSelect_" + md5Hex(url) + "." + fileExtension(url);
                    String path = HkJsRuntime.getFilePath("hiker://files/cache/" + name);
                    if (!new java.io.File(path).exists()) {
                        HkCodeUtil.downloadSync(url, path, null);
                    }
                    // 官尾步 LocalServerParser.getRealUrlForRemotedPlay：本树无该层、既有转写恒等直通
                    // （HkLivePlayerHelper 同口径），此处回 file:// 直址（记名）。
                    return "file://" + path;
                } catch (Exception e) {
                    return url;
                }
            }

            @JavascriptInterface
            public String getSearchEngines() {
                try {
                    org.json.JSONArray arr = new org.json.JSONArray();
                    for (String[] e : SEARCH_ENGINES) {
                        org.json.JSONObject o = new org.json.JSONObject();
                        o.put("name", e[0]);
                        o.put("url", e[1]);
                        arr.put(o);
                    }
                    return arr.toString();
                } catch (Exception e) {
                    return "[]";
                }
            }

            @JavascriptInterface
            public void hideLoading() {
                HkLoading.hide(ruleTitle());
            }

            @JavascriptInterface
            public boolean isVideoOrMusic(String url) {
                if (TextUtils.isEmpty(url)) return false;
                return HkUrlDetector.isVideoOrMusic(url);
            }

            @JavascriptInterface
            public String listItems(String bucket) {
                HkJsRuntime rt = rt();
                if (rt == null) return "[]";
                try {
                    return new org.json.JSONArray(rt.bridgeKvKeys(bucket)).toString();
                } catch (Exception e) {
                    return "[]";
                }
            }

            /** 原版 VideoInterface.log：JSEngine.log(msg, null)；无引擎上下文时回落安卓日志（原既有形）。 */
            @JavascriptInterface
            public void log(String msg) {
                HkJsRuntime rt = rt();
                if (rt != null) {
                    rt.log(msg, null);
                } else {
                    android.util.Log.d("HkJs", msg == null ? "" : msg);
                }
            }

            @JavascriptInterface
            public String md5(String s) {
                return md5Hex(s == null ? "" : s);
            }

            @JavascriptInterface
            public String parsePaste(String text) {
                HkJsRuntime rt = rt();
                if (rt == null) return "";
                String r = rt.parsePaste(text);
                return r == null ? "" : r;
            }

            @JavascriptInterface
            public void refreshX5Desc(String desc) {
                EventBus.getDefault().post(new OnRefreshX5HeightEvent(desc));
            }

            @JavascriptInterface
            public void removeItem(String bucket, String field) {
                HkJsRuntime rt = rt();
                if (rt != null) rt.bridgeKvRemove(bucket, field);
            }

            @JavascriptInterface
            public void saveFormInputItem(String url, String key, String value) {
                if (TextUtils.isEmpty(url) || TextUtils.isEmpty(key) || TextUtils.isEmpty(value)) return;
                // 官走 HeavyTaskUtil 后台 BigTextDO.saveFormInputs（form@ 桶）；
                // 环26 落地面：咱家委托 HkFormInputStore（同桶键/同值闸），后台形照官。
                BRIDGE_TASKS.execute(() -> {
                    if (!HkFormInputStore.saveFormInputs(url, key, value)) {
                        toast("表单保存失败");
                    }
                });
            }

            @JavascriptInterface
            public void setItem(String bucket, String field, String value) {
                HkJsRuntime rt = rt();
                if (rt != null) rt.bridgeKvSet(bucket, field, value);
            }

            @JavascriptInterface
            public void showLoading(String msg) {
                HkLoading.show(ruleTitle(), msg);
            }

            @JavascriptInterface
            public void toast(String msg) {
                FyBridge.this.toast(msg);
            }

            // —— JsBridgeHolder$1 委托名 ——

            @JavascriptInterface
            public void allowSelect() {
                // $1.allowSelect 只做 finishing 检查、无实际动作，照官空转。
                alive();
            }

            @JavascriptInterface
            public void back() {
                back(true);
            }

            @JavascriptInterface
            public void back(boolean refreshPage) {
                if (EventBus.getDefault().hasSubscriberForEvent(OnBackEvent.class)) {
                    EventBus.getDefault().post(new OnBackEvent(refreshPage, false));
                }
            }

            @JavascriptInterface
            public String clearM3u8Ad(String url) {
                Map<String, String> headerMap = wrapper.getRequestHeaderMap().get(url);
                // 官 $1.clearM3u8Ad：cookie 初值 ""，myUrl 为空时不取 cookie。
                String cookie = "";
                String myUrl = wrapper.getMyUrl();
                if (!TextUtils.isEmpty(myUrl)) {
                    try {
                        String c = wrapper.getCookie(myUrl);
                        if (c != null) cookie = c;
                    } catch (Exception ignored) {
                    }
                }
                Map<String, String> headers = HkPlayerChooser.getHeaderMap(headerMap, null, url, cookie);
                String headersJson;
                try {
                    headersJson = new org.json.JSONObject(new HashMap<>(headers)).toString();
                } catch (Exception e) {
                    headersJson = "{}";
                }
                // generateMY 的 MY_HEADERS0 支尾带换行（官原文），拼串照此。
                String lazy = url + "@lazyRule=.js:"
                        + "var MY_HEADERS0 = '" + escapeJavaScriptString(headersJson) + "';\n"
                        + "var MY_HEADERS = JSON.parse(MY_HEADERS0); "
                        + "clearM3u8Ad(input, {headers: MY_HEADERS})";
                return parseLazy(lazy, myUrl);
            }

            @JavascriptInterface
            public String fetch(String url) {
                return fetch(url, null);
            }

            @JavascriptInterface
            public String fetch(String url, String optionsJson) {
                Activity a = act();
                if (a == null || a.isFinishing()) return "";
                if (url == null) return "";
                // Tampermonkey GM 脚本原始址改写（$1.fetch 特判）
                if (url.equals("https://raw.githubusercontent.com/Tampermonkey/utils/refs/heads/main/requires/gh_2215_make_GM_xhr_more_parallel_again.js")) {
                    url = "hiker://files/gh_2215_make_GM_xhr_more_parallel_again.js";
                }
                if (url.equals("hiker://jsfile/hiker-immersive-translate")) {
                    // BrowserOfficerKt.getImmersiveTranslateCode：咱家无沉浸翻译代码件（记名）。
                    return "";
                }
                if (url.startsWith("hiker://jsfile/")) {
                    // JSManager.getJSFileContent：咱家无用户脚本管理器（记名）。
                    return "";
                }
                if (url.startsWith("hiker://files/")) {
                    return fetchFilesAsset(url.substring("hiker://files/".length()));
                }
                if (url.startsWith("file://")) {
                    String path = url.replace("file://", "");
                    // 官查 File.getAbsolutePath().contains(getFilesDir())（复核 L4）。
                    if (new java.io.File(path).getAbsolutePath().contains(filesDirAnchor())) return "";
                    return readFileText(path);
                }
                return fetchRemote(url, optionsJson);
            }

            /** $1.fetch 的 hiker://files/ 支：具名资产直读（三件带缓存），余按根目录文件读、空再回落资产。 */
            private String fetchFilesAsset(String name) {
                switch (name) {
                    case "bindDataToHtml.js":
                        if (bindDataToHtml == null) bindDataToHtml = readAsset(name);
                        return bindDataToHtml;
                    case "vConsole.js":
                        if (vConsole == null) vConsole = readAsset(name);
                        return vConsole;
                    case "jquery.min.js":
                        if (jquery == null) jquery = readAsset(name);
                        return jquery;
                    case "aes.js":
                    case "Readability.js":
                    case "highlight.min.js":
                    case "greasyfork.js":
                    case "gh_2215_make_GM_xhr_more_parallel_again.js":
                        return readAsset(name);
                    default:
                        // 官缺省支只查根目录文件、不存在回 ""，不回落资产（复核 M3）。
                        String path = HkJsRuntime.getFilePath("hiker://files/" + name);
                        if (new java.io.File(path).getAbsolutePath().contains(filesDirAnchor())) return "";
                        return readFileText(path);
                }
            }

            /** $1.fetch 一般路：cookie 注入、stream、FETCH_CACHE、缺省三支。 */
            private String fetchRemote(String url, String optionsJson) {
                HkJsRuntime rt = rt();
                if (rt == null) return "";
                org.json.JSONObject options;
                try {
                    options = TextUtils.isEmpty(optionsJson)
                            ? new org.json.JSONObject() : new org.json.JSONObject(optionsJson);
                } catch (Exception e) {
                    options = new org.json.JSONObject();
                }
                try {
                    // headers 的 cookie 值为空且 referer 与 url 同二级域名时，从 CookieManager 补 referer 的 cookie
                    // （官判值空非键存、且回值非空才 put——复核 L4）。
                    org.json.JSONObject headers = options.optJSONObject("headers");
                    if (headers == null) headers = new org.json.JSONObject();
                    String cookieVal = headers.optString("cookie", headers.optString("Cookie", ""));
                    String referer = headers.optString("referer", headers.optString("Referer", ""));
                    if (TextUtils.isEmpty(cookieVal) && !TextUtils.isEmpty(referer)
                            && HkStringUtil.getSecondDom(referer).equals(HkStringUtil.getSecondDom(url))) {
                        String cookie = CookieManager.getInstance().getCookie(referer);
                        if (!TextUtils.isEmpty(cookie)) {
                            headers.put("Cookie", cookie);
                            options.put("headers", headers);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "fetchRemote cookie inject failed", e);
                    return "";
                }
                String responseType = options.optString("responseType", "");
                if ("stream".equals(responseType)) {
                    try {
                        options.put("inputStream", true);
                        Object r = rt.fetch(url, options, null);
                        org.json.JSONObject resp = toJsonObject(r);
                        if (resp == null) return "";
                        Object body = resp.opt("body");
                        if (body instanceof java.io.InputStream) {
                            java.io.InputStream in = (java.io.InputStream) body;
                            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                            try {
                                in.close();
                            } catch (Exception ignored) {
                            }
                            resp.put("body", android.util.Base64.encodeToString(out.toByteArray(), 2));
                        }
                        return resp.toString();
                    } catch (Exception e) {
                        return "";
                    }
                }
                if ("true".equals(options.optString("FETCH_CACHE", ""))) {
                    try {
                        options.remove("FETCH_CACHE");
                        java.io.File cacheFile = new java.io.File(
                                HkJsRuntime.getFilePath("hiker://files/cache/greasyfork/" + md5Hex(url)));
                        if (cacheFile.exists()) {
                            long age = System.currentTimeMillis() - cacheFile.lastModified();
                            if (age > 0x5265c00L) {
                                final org.json.JSONObject opts = options;
                                BRIDGE_TASKS.execute(() -> cacheFetch(rt, url, opts, cacheFile));
                            }
                            return readFileText(cacheFile.getAbsolutePath());
                        }
                        Object r = rt.fetch(url, options, null);
                        String result = r == null ? "" : String.valueOf(r);
                        // 官 fetch 支直调 cacheFetchResult、由其本体再包一层后台写盘（见该法注）。
                        cacheFetchResult(cacheFile, result);
                        return result;
                    } catch (Exception e) {
                        return "";
                    }
                }
                Object r = rt.fetch(url, options, null);
                return r == null ? "" : (String) r;
            }

            private org.json.JSONObject toJsonObject(Object r) {
                if (r == null) return null;
                if (r instanceof org.json.JSONObject) return (org.json.JSONObject) r;
                if (r instanceof Map) {
                    try {
                        return new org.json.JSONObject((Map<?, ?>) r);
                    } catch (Exception e) {
                        return null;
                    }
                }
                if (r instanceof String) {
                    try {
                        return new org.json.JSONObject((String) r);
                    } catch (Exception e) {
                        return null;
                    }
                }
                return null;
            }

            /**
             * $1.cacheFetch 本体转写（环26 残差订正）：本体只把活包成后台任务
             * （官 HeavyTaskUtil.executeNewTask），后台体同步 fetch 后非空才写盘；
             * fetch 抛错整条后台任务无声终止（官 try 只包写盘段）。咱家调用点
             * 已在 fetch 支以 BRIDGE_TASKS 包住本法调用，与官「本体包任务」同形。
             */
            private void cacheFetch(HkJsRuntime rt, String url, org.json.JSONObject options,
                                    java.io.File cacheFile) {
                Object r = rt.fetch(url, options, null);
                if (r != null) {
                    String result = String.valueOf(r);
                    if (!result.isEmpty()) writeCacheFile(cacheFile, result);
                }
            }

            /**
             * $1.cacheFetchResult 本体转写（环26 残差订正）：官本体先判
             * StringUtil.isNotEmpty——空串直接不做（旧形对空串也写盘、把缓存
             * 文件写空，已订正）；非空才再包一层后台任务写盘，写盘 try 只
             * 记错、不回告不重试。
             */
            private void cacheFetchResult(java.io.File cacheFile, String content) {
                if (content == null || content.isEmpty()) return;
                BRIDGE_TASKS.execute(() -> writeCacheFile(cacheFile, content));
            }

            /** FileUtil.stringToFile 的桥侧写盘：异常只吞（官 Timber.e 同位）。 */
            private void writeCacheFile(java.io.File cacheFile, String content) {
                try {
                    java.io.File parent = cacheFile.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    java.io.FileOutputStream out = new java.io.FileOutputStream(cacheFile);
                    out.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    out.close();
                } catch (Exception ignored) {
                }
            }

            @JavascriptInterface
            public void fetchAsync(String url, String key, String callbackName) {
                fetchAsync(url, null, key, callbackName);
            }

            @JavascriptInterface
            public void fetchAsync(String url, String optionsJson, String key, String callbackName) {
                // 官 $1.fetchAsync 入口先判 reference/finishing，无则整件不做（复核 L1）。
                Activity a0 = act();
                if (a0 == null || a0.isFinishing()) return;
                BRIDGE_TASKS.execute(() -> {
                    String result = fetch(url, optionsJson);
                    requestAsyncMap.put(key, result);
                    Activity a = act();
                    if (a == null) return;
                    a.runOnUiThread(() -> wrapper.evaluateJavascript(
                            "(function() {" + callbackName + "('" + key
                                    + "', fy_bridge_app.getResultByKey('" + key + "'))})();"));
                });
            }

            @JavascriptInterface
            public void finishParse(String url, String type, String ticket) {
                EventBus.getDefault().post(new HkDestroyEvent(url, type, ticket));
            }

            @JavascriptInterface
            public String getCookie(String url) {
                String myUrl = wrapper.getMyUrl();
                if (TextUtils.isEmpty(url)) {
                    url = HkStringUtil.getDom(myUrl) + "/";
                }
                if (!TextUtils.equals(HkStringUtil.getDom(url), HkStringUtil.getDom(myUrl))) {
                    return "error:cannot get cookie from another domain";
                }
                try {
                    String cookie = wrapper.getCookie(url);
                    return cookie == null ? "" : cookie;
                } catch (Exception e) {
                    return "error:" + e.getMessage();
                }
            }

            @JavascriptInterface
            public String getHeaderUrl(String url) {
                Map<String, String> map = wrapper.getRequestHeaderMap().get(url);
                // 官 $1.getHeaderUrl：getReferer+getCookie 包在 try 里，异常时 cookie 保持 "" 继续（复核 L2）。
                String cookie = "";
                try {
                    String referer = HkPlayerChooser.getReferer(map, wrapper.getRefererPolicy(),
                            wrapper.getMyUrl(), url);
                    if (!TextUtils.isEmpty(referer)) {
                        String c = wrapper.getCookie(referer);
                        if (c != null) cookie = c;
                    }
                } catch (Exception ignored) {
                }
                return HkPlayerChooser.decorateHeader0(map, null, url, cookie);
            }

            @JavascriptInterface
            public String getInternalJs() {
                // JsPluginHelper.getInternalJs = JSManager 的 theme + adTouch 两件拼接；
                // 咱家无 JSManager 用户脚本层，两件均按资产读（用户覆盖不生效，记名）。
                String theme = readAsset("theme.js");
                String adTouch = readAsset("adTouch.js");
                return (theme == null ? "" : theme) + (adTouch == null ? "" : adTouch);
            }

            @JavascriptInterface
            public String getNetworkRecords() {
                // DetectorManager.getDetectedMediaResults：咱家无进程级嗅探结果存储（记名）。
                return "[]";
            }

            @JavascriptInterface
            public String getRequestHeaders0() {
                try {
                    return new org.json.JSONObject(new HashMap<>(wrapper.getRequestHeaderMap())).toString();
                } catch (Exception e) {
                    return "{}";
                }
            }

            @JavascriptInterface
            public String getResultByKey(String key) {
                if (requestAsyncMap.containsKey(key)) {
                    String v = requestAsyncMap.remove(key);
                    return v == null ? "" : v;
                }
                return "";
            }

            @JavascriptInterface
            public String getUa() {
                String ua = wrapper.getUserAgentString();
                return ua == null ? "" : ua;
            }

            @JavascriptInterface
            public String getUrls() {
                try {
                    return new org.json.JSONArray(wrapper.getUrls()).toString();
                } catch (Exception e) {
                    return "[]";
                }
            }

            @JavascriptInterface
            public String getVar(String key) {
                HkJsRuntime rt = rt();
                if (rt == null) return "";
                Object v = rt.getVar(key, "");
                return v == null ? "" : String.valueOf(v);
            }

            @JavascriptInterface
            public void importRule(String text) {
                Activity a = act();
                if (a == null || a.isFinishing() || wrapper.isOnPause()) return;
                a.runOnUiThread(() -> {
                    if (HkDetailBridge.hooks != null) {
                        HkDetailBridge.hooks.checkText(a, text);
                    }
                });
            }

            @JavascriptInterface
            public String isPc() {
                String ua = wrapper.getUserAgentString();
                return String.valueOf(PC_UA.equals(ua));
            }

            @JavascriptInterface
            public void newPage(String url, String unused) {
                BridgeOpenHandler h = openHandler;
                if (h == null || !alive()) return;
                String findRule = "js:setResult([{\n url:\"" + HkJsRuntime.escapeJsString(url) + "\",\ndesc:\"100%&&float\"\n}]);";
                Activity a = act();
                a.runOnUiThread(() -> h.openDetailRule("hiker://empty", url, findRule,
                        HkColType.X5_WEB_VIEW.code, null, null));
            }

            /**
             * 原版 VideoInterface.open 同款：入参 JSON 经 fastjson 解成 DetailPage
             * （title/url/pic/picUrl/pic_url/extra/pages/…）交 BridgeListener.open。
             * 环26 残差三件：picUrl 按 DetailPage.getImageUrl() 序（pic_url＞pic＞
             * picUrl）取出、extra 整串透传给宿主接缝（官 toNextPage 把 extraJson
             * 交 getNextPage、lambda$14 再读其中 newWindow/windowId）。
             * C1 复核订正：DetailPage.pages 一并透传，宿主在 getNextPage 组出的
             * 子规则上非空覆写（官 toNextPage λ16 的 rule.setPages(detailPage.pages)）。
             */
            @JavascriptInterface
            public void open(String json) {
                try {
                    if (TextUtils.isEmpty(json) || openHandler == null) return;
                    org.json.JSONObject o = new org.json.JSONObject(json);
                    String url = o.optString("url");
                    if (TextUtils.isEmpty(url)) return;
                    String title = o.optString("title");
                    String picUrl = o.optString("pic_url");
                    if (TextUtils.isEmpty(picUrl)) picUrl = o.optString("pic");
                    if (TextUtils.isEmpty(picUrl)) picUrl = o.optString("picUrl");
                    org.json.JSONObject extra = o.optJSONObject("extra");
                    final String pic = picUrl;
                    final String extraJson = extra == null ? null : extra.toString();
                    final String pages = o.optString("pages");
                    final BridgeOpenHandler h = openHandler;
                    Activity act = reference.get();
                    if (act == null) return;
                    act.runOnUiThread(() -> h.openBridgePage(title, url, pic, extraJson, pages));
                } catch (Throwable e) {
                    Log.e(TAG, "bridge open failed", e);
                    toast("打开页面失败");
                }
            }

            /** 原版 VideoInterface.openThirdApp 同款：唤起第三方应用（既有形，保持原样）。 */
            @JavascriptInterface
            public void openThirdApp(String url) {
                try {
                    Activity act = reference.get();
                    if (act == null || TextUtils.isEmpty(url)) return;
                    android.content.Intent intent = new android.content.Intent(
                            android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url));
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    act.startActivity(intent);
                } catch (Throwable e) {
                    Log.e(TAG, "openThirdApp failed", e);
                    toast("无法打开外部应用");
                }
            }

            @JavascriptInterface
            public String parseDomForArray(String html, String selector) {
                HkJsRuntime rt = rt();
                if (rt == null) return "";
                Object r = rt.parseDomForArray(html, selector);
                return r == null ? "" : String.valueOf(r);
            }

            @JavascriptInterface
            public String parseDomForHtml(String html, String selector) {
                HkJsRuntime rt = rt();
                if (rt == null) return "";
                Object r = rt.parseDomForHtml(html, selector);
                return r == null ? "" : String.valueOf(r);
            }

            @JavascriptInterface
            public String parseLazyRule(String js) {
                return parseLazy(js, wrapper.getMyUrl());
            }

            @JavascriptInterface
            public void parseLazyRuleAsync(String js, String callback) {
                if (TextUtils.isEmpty(callback)) return;
                String myUrl = wrapper.getMyUrl();
                BRIDGE_TASKS.execute(() -> {
                    String result = parseLazy(js, myUrl);
                    Activity a = act();
                    if (a == null || a.isFinishing()) return;
                    a.runOnUiThread(() -> {
                        try {
                            wrapper.evaluateJavascript("(function() {var input = '"
                                    + escapeJavaScriptString(result)
                                    + "'; eval('" + escapeJavaScriptString(callback) + "')})();");
                        } catch (Exception e) {
                            Log.e(TAG, "parseLazyRuleAsync callback failed", e);
                            toast("规则执行失败");
                        }
                    });
                });
            }

            @JavascriptInterface
            public void playVideo(String url) {
                Activity a = act();
                if (a == null) return;
                a.runOnUiThread(() -> {
                    if (!wrapper.isOnPause()) startPlayVideo(url);
                });
            }

            @JavascriptInterface
            public void playVideo(String url, String extra) {
                // 官 $1 二参形只用第二参起播（一参被弃），照官。
                Activity a = act();
                if (a == null) return;
                a.runOnUiThread(() -> {
                    if (!wrapper.isOnPause()) startPlayVideo(extra);
                });
            }

            @JavascriptInterface
            public void playVideos(String json) {
                Activity a = act();
                if (a == null) return;
                a.runOnUiThread(() -> {
                    if (!wrapper.isOnPause()) startPlayVideos(json);
                });
            }

            @JavascriptInterface
            public void putVar(String key, String value) {
                HkJsRuntime rt = rt();
                if (rt == null) return;
                rt.putVar(key, value);
            }

            @JavascriptInterface
            public void refreshPage(boolean z) {
                if (EventBus.getDefault().hasSubscriberForEvent(OnRefreshPageEvent.class)) {
                    EventBus.getDefault().post(new OnRefreshPageEvent(z));
                }
            }

            @JavascriptInterface
            public void registerMenuCommand(String scriptKey, String name, String func) {
                List<String[]> list = greasyForkMenuMap.get(scriptKey);
                if (list == null) {
                    list = new ArrayList<>();
                    greasyForkMenuMap.put(scriptKey, list);
                }
                for (String[] menu : list) {
                    if (menu[0] != null && menu[0].equals(name)) return;
                }
                list.add(new String[]{name, func});
            }

            @JavascriptInterface
            public void unregisterMenuCommand(String scriptKey, String arg) {
                List<String[]> list = greasyForkMenuMap.get(scriptKey);
                if (list == null) return;
                for (int i = 0; i < list.size(); i++) {
                    if (arg != null && arg.equals(list.get(i)[1])) {
                        list.remove(i);
                        return;
                    }
                }
                for (int i = 0; i < list.size(); i++) {
                    if (arg != null && arg.equals(list.get(i)[0])) {
                        list.remove(i);
                        return;
                    }
                }
            }

            @JavascriptInterface
            public void saveAdBlockRule(String rule) {
                EventBus.getDefault().post(new OnSaveAdBlockRuleEvent(rule));
            }

            @JavascriptInterface
            public void saveImage(String url, String path) {
                if (TextUtils.isEmpty(url)) {
                    toast("图片地址不能为空");
                    return;
                }
                boolean pathValid = isValidSavePath(path);
                boolean urlValid = isValidSavePath(url);
                String resolved = pathValid ? HkJsRuntime.getFilePath(path) : null;
                if (pathValid && urlValid && !TextUtils.isEmpty(resolved)) {
                    BRIDGE_TASKS.execute(() -> downloadImageToFile(url, resolved));
                } else {
                    BRIDGE_TASKS.execute(() -> saveImageToGallery(url));
                }
            }

            /** VideoInterface.saveImage 的 path/url 有效性判据：非空且不等于 "undefined"/"null"（忽略大小写）。 */
            private boolean isValidSavePath(String s) {
                return !TextUtils.isEmpty(s)
                        && !"undefined".equalsIgnoreCase(s)
                        && !"null".equalsIgnoreCase(s);
            }

            /** ImgUtil.downloadImgByGlide 的等价落盘：取图字节写入目标文件。 */
            private void downloadImageToFile(String url, String path) {
                try {
                    byte[] bytes = fetchImageBytes(url);
                    if (bytes == null) {
                        toast("图片保存失败");
                        return;
                    }
                    java.io.File f = new java.io.File(path);
                    java.io.File parent = f.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    java.io.FileOutputStream out = new java.io.FileOutputStream(f);
                    out.write(bytes);
                    out.close();
                } catch (Exception e) {
                    Log.e(TAG, "downloadImageToFile failed", e);
                    toast("图片保存失败");
                }
            }

            /** ImgUtil.savePic2Gallery 的等价：取图字节写入系统图库。 */
            private void saveImageToGallery(String url) {
                try {
                    byte[] bytes = fetchImageBytes(url);
                    if (bytes == null) {
                        toast("图片保存失败");
                        return;
                    }
                    Activity a = act();
                    Context ctx = a != null ? a : App.get();
                    android.content.ContentValues values = new android.content.ContentValues();
                    values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                            "hk_" + System.currentTimeMillis() + ".jpg");
                    values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                    android.net.Uri uri = ctx.getContentResolver().insert(
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) {
                        toast("图片保存失败");
                        return;
                    }
                    java.io.OutputStream out = ctx.getContentResolver().openOutputStream(uri);
                    if (out == null) {
                        toast("图片保存失败");
                        return;
                    }
                    out.write(bytes);
                    out.close();
                } catch (Exception e) {
                    Log.e(TAG, "saveImageToGallery failed", e);
                    toast("图片保存失败");
                }
            }

            private byte[] fetchImageBytes(String url) {
                try {
                    Object r = HkGlideUtil.getGlideUrl(wrapper.getUrl(), url);
                    String real = r == null ? url : String.valueOf(r);
                    java.net.URL u = new java.net.URL(real.startsWith("http") ? real : url);
                    java.io.InputStream in = u.openStream();
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    in.close();
                    byte[] bytes = out.toByteArray();
                    // 空字节与抓取失败同判（残项处置 2026-11-14）：此前空图被
                    // 当成功写进图库/文件，调用方的失败提示永远触发不到。
                    if (bytes.length == 0) {
                        Log.e(TAG, "fetchImageBytes 取到空字节：" + url);
                        return null;
                    }
                    return bytes;
                } catch (Exception e) {
                    Log.e(TAG, "fetchImageBytes failed: " + url, e);
                    return null;
                }
            }

            @JavascriptInterface
            public void searchBySelect(String text) {
                if (TextUtils.isEmpty(text)) return;
                EventBus.getDefault().post(new ShowSearchEvent(text));
            }

            @JavascriptInterface
            public void setAdBlock(String html, String rule) {
                Activity a = act();
                if (a == null || a.isFinishing() || wrapper.isOnPause()) return;
                EventBus.getDefault().post(new OnSetAdBlockEvent(html, rule));
            }

            @JavascriptInterface
            public void setAppBarColor(String color) {
                setAppBarColor(color, "false");
            }

            @JavascriptInterface
            public void setAppBarColor(String color1, String color2) {
                wrapper.setAppBarColor(color1, color2);
            }

            @JavascriptInterface
            public void setImgHref(String s) {
                HkJsRuntime rt = rt();
                if (rt != null) rt.putVar("#setImgHref", s);
            }

            @JavascriptInterface
            public void setImgUrls(String s) {
                Activity a = act();
                if (a == null) return;
                a.runOnUiThread(() -> {
                    if (TextUtils.isEmpty(s)) return;
                    List<String> pics = new ArrayList<>();
                    for (String item : s.split("&&")) {
                        pics.add(resolveHttpUrl(wrapper.getUrl(), item));
                    }
                    // 官尾步 PlayerChooser.checkPicsSize 在宿主侧（图片面接缝内处理，记名）。
                    BridgePicHandler h = picHandler;
                    if (h != null) h.showPics(pics, wrapper.getUrl());
                });
            }

            @JavascriptInterface
            public void setWebTitle(String title) {
                EventBus.getDefault().post(new OnSetWebTitleEvent(title));
            }

            @JavascriptInterface
            public void setWebUa(String ua) {
                Activity a = act();
                if (a == null) return;
                a.runOnUiThread(() -> {
                    String current = wrapper.getUserAgentString();
                    if (TextUtils.isEmpty(ua) && wrapper.getSystemUa().equals(current)) return;
                    String target = TextUtils.isEmpty(ua) ? wrapper.getSystemUa() : ua;
                    if (target != null && target.equals(current)) return;
                    wrapper.updateLastDom("");
                    wrapper.setUserAgentString(target);
                    wrapper.reload();
                    // 官尾步 UAModel.updateUa(wrapper.getUrl(), ua)：环26 落地面，
                    // 按域存档走 HkUaModel（setWebUa 的 ua 原参，非 target）。
                    if (!HkUaModel.updateUa(wrapper.getUrl(), ua)) {
                        toast("UA 保存失败，重启后将失效");
                    }
                });
            }

            @JavascriptInterface
            public void showPic(String url) {
                Activity a = act();
                if (a == null) return;
                a.runOnUiThread(() -> {
                    if (wrapper.isOnPause()) return;
                    BridgePicHandler h = picHandler;
                    if (h != null) h.showPic(url);
                });
            }

            @JavascriptInterface
            public void storeFaviconUrl(String url) {
                wrapper.storeFaviconUrl(url);
            }

            @JavascriptInterface
            public void storeRefererPolicy(String policy) {
                wrapper.storeRefererPolicy(policy);
            }

            @JavascriptInterface
            public void toDetailPage(String url, String title, String findRule,
                                     String colType, String group, String preRule) {
                BridgeOpenHandler h = openHandler;
                Activity a = act();
                if (h == null || a == null) return;
                a.runOnUiThread(() -> h.openDetailRule(url, title, findRule, colType, group, preRule));
            }

            @JavascriptInterface
            public void translate(String text) {
                if (TextUtils.isEmpty(text)) return;
                EventBus.getDefault().post(new ShowTranslateEvent(text));
            }

            @JavascriptInterface
            public void translateBolan(String text) {
                if (TextUtils.isEmpty(text)) return;
                Activity a = act();
                if (a == null) return;
                a.runOnUiThread(() -> {
                    Context ctx = a;
                    boolean installed;
                    try {
                        ctx.getPackageManager().getPackageInfo("com.hiker.bolanassist", 0);
                        installed = true;
                    } catch (Exception e) {
                        installed = false;
                    }
                    if (installed) {
                        try {
                            android.content.Intent intent = new android.content.Intent();
                            intent.setAction("android.intent.action.PROCESS_TEXT");
                            intent.putExtra("android.intent.extra.TEXT", text);
                            intent.addFlags(0x10008000);
                            intent.setComponent(new android.content.ComponentName(
                                    "com.hiker.bolanassist",
                                    "com.hiker.bolanassist.ui.main.TranslateIntentActivity"));
                            a.startActivity(intent);
                        } catch (Exception ignored) {
                        }
                    } else {
                        toast("未安装波澜工具箱");
                        // 官此处另 loadUrl 一条资源串（波澜下载址，咱家无该资源，记名略）。
                    }
                });
            }

            @JavascriptInterface
            public void writeFile(String path, String content) {
                // 官 $1.writeFile 入口门：reference 空或 Activity finishing 直接 return（复核 M2）。
                Activity a = act();
                if (a == null || a.isFinishing()) return;
                if (path == null) return;
                String resolved = HkJsRuntime.getFilePath(path);
                String root = HkJsRuntime.getFilePath("hiker://files/");
                if (!resolved.startsWith(root)) return;
                if (resolved.contains(filesDirAnchor())) return;
                // 第二道封锁是 JSEngine.inJsDir 而非整目录：含 js 目录且基名（去扩展名）
                // 含 "." 或以 "global" 开头才封锁，foo.js 这类单点名可写（复核 M2）。
                if (resolved.contains(HkJsRuntime.getFilePath("hiker://files/rules/js/"))) {
                    String name = new java.io.File(resolved).getName();
                    int dot = name.lastIndexOf('.');
                    String base = dot > 0 ? name.substring(0, dot) : name;
                    if (base.contains(".") || base.startsWith("global")) return;
                }
                try {
                    java.io.File f = new java.io.File(resolved);
                    java.io.File parent = f.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    java.io.FileOutputStream out = new java.io.FileOutputStream(f);
                    out.write((content == null ? "" : content).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    out.close();
                } catch (java.io.IOException e) {
                    Log.e(TAG, "writeFile failed", e);
                    toast("文件写入失败");
                }
            }
        }

        /** HttpUtil.getRealUrl(base, url) 的桥侧等价：相对址按页面址补全（setImgUrls 用）。 */
        static String resolveHttpUrl(String base, String url) {
            if (url == null) return null;
            if (url.startsWith("http")) return url;
            if (base == null) return url;
            if (url.startsWith("//")) {
                int p = base.indexOf("://");
                return (p > 0 ? base.substring(0, p) : "https") + ":" + url;
            }
            if (url.startsWith("/")) return HkStringUtil.getHome(base) + url;
            int slash = base.lastIndexOf('/');
            if (slash > 0) return base.substring(0, slash + 1) + url;
            return url;
        }

        /** FileUtil.getExtension 的桥侧等价：末段路径里最后一个点后的扩展名，无则空串。 */
        static String fileExtension(String url) {
            if (url == null) return "";
            String s = url;
            int q = s.indexOf('?');
            if (q >= 0) s = s.substring(0, q);
            int slash = s.lastIndexOf('/');
            if (slash >= 0) s = s.substring(slash + 1);
            int dot = s.lastIndexOf('.');
            if (dot < 0 || dot == s.length() - 1) return "";
            return s.substring(dot + 1);
        }

        /** UAModel.WebUA.PC.getContent()（= UAEnum.PC 串，与 HkUaEnum/HkRule.PC_UA 同值）。 */
        private static final String PC_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

        /** SearchModel.EngineEnum 全表（name, url），getSearchEngines 的数据源，照官 clinit 顺序。 */
        private static final String[][] SEARCH_ENGINES = {
                {"百度", "https://m.baidu.com/s?word=**&t_kt=0&ie=utf-8&sa=ib&tn=simple&tj=1"},
                {"搜狗", "https://m.sogou.com/web/searchList.jsp?s_from=pcsearch&keyword=**"},
                {"必应", "https://www.bing.com/search?q=**"},
                {"360", "https://m.so.com/s?ie=utf-8&fr=none&ssid=&q=**"},
                {"谷歌", "https://www.google.com.hk/search?q=**"},
                {"夸克", "https://quark.sm.cn/s?q=**"},
                {"Yep", "https://yep.com/web?q=**"},
                {"Yandex", "https://yandex.com/search/?text=**"},
                {"DuckDuckGo", "https://duckduckgo.com/?q=**&t=h_"},
                {"神马", "https://m.sm.cn/s?q=**"},
                {"秘塔 AI", "https://metaso.cn/?q=**"},
                {"百度AI助手", "https://chat.baidu.com/search?word=**"},
                {"Phind AI", "https://phind-ai.com/zh/search?q=**"},
                {"Felo", "https://felo.ai/search?q=**"},
                {"iAsk", "https://iask.ai/q?mode=question&options[detail_level]=detailed&q=**"},
                {"百度百科", "https://baike.baidu.com/item/**?fromtitle=**"},
                {"百度文库", "https://wk.baidu.com/search?word=**"},
                {"百度学术", "https://xueshu.baidu.com/s?wd=**"},
                {"丁香实验", "https://www.biomart.cn/experiment/s?wd=**"},
                {"360文库", "http://www.360doc.cn/search.aspx?word=**"},
                {"豆丁", "https://www.docin.com/touch_new/search.do?rkey=**"},
                {"知网", "https://wap.cnki.net/touch/web/Article/SearchIndex"},
                {"知乎", "https://www.zhihu.com/search?q=**"},
                {"头条", "https://m.toutiao.com/search?keyword=**"},
                {"微博", "https://m.weibo.cn/search?containerid=100103type=1&q=**"},
                {"微信", "https://weixin.sogou.com/weixin?type=2&s_from=input&query=**"},
        };
    }
}
