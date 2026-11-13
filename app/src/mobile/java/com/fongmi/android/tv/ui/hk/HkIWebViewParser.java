package com.fongmi.android.tv.ui.hk;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.hk.HkParserKit;
import com.google.gson.Gson;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 照 8.83 com.example.hikerview.service.parser.IWebViewParser 转写。
 * generateExtra 把条目 extra(JSON) 映射为 HkX5Extra（只拷 ua/blockRules/disableX5/urlInterceptor/
 * referer/jsLoadingInject/redirect 七项 + js 尾巴，余字段保持默认，原版如此），
 * 并把 generateExtraJS 生成的轮询脚本（250ms 一次、上限 120 次、调 fy_bridge_app.finishParse(url, type, ticket)）
 * 拼在规则 js 之前作为最终注入脚本。
 * 与原版的一处刻意偏差（账 bestjav-play-freeze-20261101 续单）：轮询续行条件原版是
 * url != null，星辰加 url !== ''——第三方嗅探模块（如 BestJav8 的 Apollo.getSniffCode）的
 * 返回契约是「没找到回空串」，且其式内有「先点播放钮、再扫 performance 记录」的二段式
 * 设计，空串若按原版语义在首轮 250ms 即 finishParse('') 收口，该模块永远跑不通；
 * 空串按「未找到、继续轮询」处理后，耗尽仍走原版 _cc>120 的 finishParse('') 失败出口，下游不变。
 * 另一处刻意加项（同账续单，2026-11-01 晚）：generateExtra 在原版脚本尾部再拼一段壳子侧
 * 「播放器列表补采」（generateFallbackJS）——部分规则的嗅探式只认带 .m3u8/.mp4 后缀的地址，
 * 而目标站播放器列表里的正片地址是无后缀签名地址（如 /media/video/<id>/720p?expires&signature），
 * 式内三段（列表/video.src/performance）被同一匹配器锁死、跑满 120 轮也永远回空；原版此时页面
 * 就在用户眼前、可手动点播/开嗅探列表，星辰的嗅探是黑盒弹窗、失败即死路。故补采只做一件事：
 * 页面自身播放器（JW）列表首项地址即正片地址（按构造定义、非广告位），路径带媒体段
 * （/media/、/video/ 等）时直接经同一 fy_bridge_app.finishParse＋同一 ticket 收口；
 * 其余候选（列表非媒体形地址、DOM video 地址）须 fetch 探到媒体内容类型
 * （video 或 audio 开头、或含 mpegurl／m3u8 字样）才收。规则嗅探式优先级不变（其 250ms 节奏远快于补采的 2 秒起步），
 * 谁先 finishParse 谁生效，下游 ticket/fired 双守卫与原版一致。
 * G62 收口权改造（账 bestjav-sniff-retrace-20261110）：此前轮询跑满 120 轮（≈30s）
 * 即发 finishParse('') 终局，而补采须等播放器装配完才有列表可采——装配慢过 ≈28s
 * 时补采窗口为零、每次必空，45 秒守卫排在 30 秒后救不了。现收口权归补采：
 * 补采以 window._hkFbState 标存活（'alive'→截止/收口置 'dead'），轮询耗尽时
 * 补采未死则以 1 秒慢拍续等、补采死后才发空终局；补采在 JW 出现后节拍由
 * 1.5s 加密到 300ms 抢装配后窗口。补采未注入的链（executeWebRule 等）无
 * _hkFbState、维持原版直发 '' 不变。
 * escapeJavaScriptString 照 org.adblockplus.libadblockplus.android.Utils 同名方法转写。
 */
public abstract class HkIWebViewParser {

    protected Consumer<String> consumer;

    public abstract void destroy();

    public abstract boolean finishParseInner(Context context, String url);

    public abstract boolean parse(Activity activity, String url, String extra, Consumer<String> consumer);

    // —— 嗅探承载两面共用收口（2026-11-11 双承载收归）——
    // 官嗅探只有一副承载（解析器＋ArticleWebkitHolder）；咱家此前有两副：
    // UI 点击路走本类两解析器（全承载：fy_bridge_app 全桥＋loadAllJs 两拍＋
    // extra/ua/js 透传），经 HkRouter 播放路却走 HkWebkitFetcher 自建的裸
    // WebView（只有 finishParse 一法、无注入无全桥，依赖桥/theme 的嗅探式
    // 120 拍耗尽回 ""）。收归后 Router 路与 UI 路共用同一入口：
    // parseSync 按前缀选解析器（type 按来路真值：x5Rule→"x5"、webRule→
    // "webkit"，不再写死），dispatchFinishParse 是 finishParse 事件的唯一
    // 路由本体（片段订阅与 parseSync 自带订阅共用，ticket 仍由解析器校验）。

    /** finishParse 事件路由本体：mode="webkit" 入 Webkit 解析器，余入 X5 解析器（与官 DestroyEvent 分发同形）。 */
    public static boolean dispatchFinishParse(Context context, HkDestroyEvent event) {
        if (event == null) return false;
        if ("webkit".equals(event.getMode())) {
            return HkWebkitParser.finishParse(context, event.getUrl(), event.getTicket());
        }
        return HkX5WebViewParser.finishParse(context, event.getUrl(), event.getTicket());
    }

    /** parseSync 的事件路由订阅者：存活期与片段订阅并存，重复投递在解析器侧幂等（首个生效、后续 holder 已毁回 false）。 */
    public static class FinishParseRouter {
        private final Context context;

        public FinishParseRouter(Context context) {
            this.context = context;
        }

        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onDestroyEvent(HkDestroyEvent event) {
            dispatchFinishParse(context, event);
        }
    }

    /** 与 UI 路 HkDetailBridge.parseWithTimeout 同顶：页面轮询（约 30 秒）＋补采慢等尾巴之上的总帽。 */
    private static final long SYNC_PARSE_TIMEOUT_MS = 45_000L;

    /**
     * 同步嗅探入口（HkRouter 播放路专用，与 UI 点击路共用同一解析器承载）：
     * 在主线程起解析器（全承载），调用线程同步等回址；超时/中断同收尾销毁本路解析器回 ""。
     * 契约与旧 HkWebkitFetcher.parseWebRule 同：成功回真实地址，空/超时/异常回 ""。
     * 主线程调用直接回 ""——回址正是经主线程投递的桥回调，同步等必死锁。
     */
    public static String parseSync(String url, String extraJson) {
        final boolean x5 = HkX5WebViewParser.canParse(url);
        if (!x5 && !HkWebkitParser.canParse(url)) return "";
        if (Looper.myLooper() == Looper.getMainLooper()) return "";
        Activity activity = App.activity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return "";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>("");
        AtomicBoolean fired = new AtomicBoolean(false);
        Consumer<String> consumer = r -> {
            if (fired.compareAndSet(false, true)) {
                result.set(r == null ? "" : r);
                latch.countDown();
            }
        };
        FinishParseRouter router = new FinishParseRouter(App.get());
        EventBus.getDefault().register(router);
        try {
            new Handler(Looper.getMainLooper()).post(() -> {
                boolean started = x5
                        ? HkX5WebViewParser.parse0(activity, url, extraJson, consumer)
                        : HkWebkitParser.parse0(activity, url, extraJson, consumer);
                if (!started && fired.compareAndSet(false, true)) latch.countDown();
            });
            boolean done = false;
            try {
                done = latch.await(SYNC_PARSE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // 中断支与超时支同收尾（2-B）：同样销毁本路解析器，不许只回 "" 留单例/WebView 待下次 parse0 才收
                Thread.currentThread().interrupt();
                if (fired.compareAndSet(false, true)) destroyParserOnMain(x5);
                return "";
            }
            if (!done && fired.compareAndSet(false, true)) {
                destroyParserOnMain(x5);
                return "";
            }
        } finally {
            EventBus.getDefault().unregister(router);
        }
        String r = result.get();
        return r == null ? "" : r;
    }

    /**
     * 超时/中断的收尾销毁（2-A，与 UI 路 HkDetailBridge.parseWithTimeout 同口径）：
     * 经主线程 postDelayed 执行并包 try/catch——不许在调用方后台线程直调 destroy0，
     * 销毁异常也不许击穿 parseSync「超时/中断回 ""」的契约。
     */
    private static void destroyParserOnMain(boolean x5) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (x5) HkX5WebViewParser.destroy0(); else HkWebkitParser.destroy0();
            } catch (Throwable ignored) {
            }
        }, 0L);
    }

    protected HkX5Extra generateExtra(String extraJson, String jsExpr, String type, String ticket) {
        HkX5Extra extra = new HkX5Extra();
        String jsStr = "";
        if (HkStringUtil.isNotEmpty(extraJson)) {
            HkX5Extra parsed = null;
            try {
                parsed = new Gson().fromJson(extraJson, HkX5Extra.class);
            } catch (Exception ignored) {
                // extra 来自规则作者数据，畸形 JSON 或字段类型不对时按无 extra 处理，不许沿点击链上抛崩页
            }
            if (parsed != null) {
                if (HkStringUtil.isNotEmpty(parsed.getUa())) extra.setUa(parsed.getUa());
                if (HkParserKit.isNotEmpty(parsed.getBlockRules())) extra.setBlockRules(parsed.getBlockRules());
                if (parsed.isDisableX5()) extra.setDisableX5(true);
                if (HkStringUtil.isNotEmpty(parsed.getUrlInterceptor())) {
                    extra.setUrlInterceptor(parsed.getUrlInterceptor());
                }
                extra.setReferer(parsed.getReferer());
                extra.setJsLoadingInject(parsed.isJsLoadingInject());
                if (HkStringUtil.isNotEmpty(parsed.getJs())) jsStr = parsed.getJs();
                extra.setRedirect(parsed.isRedirect());
            }
        }
        extra.setJs(generateExtraJS(jsExpr, type, ticket) + jsStr + generateFallbackJS(type, ticket));
        return extra;
    }

    /**
     * 壳子侧播放器列表补采脚本（非 8.83 原文，见类头注释）：与轮询脚本同注同桥，
     * window._hkFb 守卫保证多次注入只跑一份。候选与收口口径见类头，此处只落码。
     */
    private String generateFallbackJS(String type, String ticket) {
        String js = """
                if (window._hkFb == null) {
                  window._hkFb = 1;
                  window._hkFbState = 'alive';
                  (function () {
                    var TYPE = '__TYPE__', TICKET = '__TICKET__', done = false, probes = {}, lastHasJw = false;
                    function fin(u) { if (done) return; done = true; window._hkFbState = 'dead'; try { fy_bridge_app.finishParse(u, TYPE, TICKET); } catch (e) {} }
                    function isHttp(u) { return /^https?:\\/\\//i.test(u); }
                    function mediaPath(u) { return /\\/(?:media|videos?|hls|vod|streams?)\\//i.test(u); }
                    function cands() {
                      var out = [], hasJw = false;
                      try {
                        if (typeof window.jwplayer === 'function') {
                          hasJw = true;
                          var pl = null;
                          try { pl = window.jwplayer().getPlaylist(); } catch (e) {}
                          if (pl && pl.length) {
                            var f = pl[0] && (pl[0].file || pl[0].src);
                            if (f) { try { out.push({ u: new URL(f, location.href).href, jw: true }); } catch (e) {} }
                          }
                        }
                      } catch (e) {}
                      if (!hasJw) {
                        try {
                          var vs = document.querySelectorAll('video');
                          for (var j = 0; j < vs.length; j++) {
                            var s = vs[j].currentSrc || vs[j].src;
                            if (s && isHttp(s) && (isNaN(vs[j].duration) || vs[j].duration > 120)) out.push({ u: s, jw: false });
                          }
                        } catch (e) {}
                      }
                      lastHasJw = hasJw;
                      return out;
                    }
                    function probe(u) {
                      probes[u] = (probes[u] || 0) + 1;
                      if (probes[u] > 3) return;
                      try {
                        var ac = ('AbortController' in window) ? new AbortController() : null;
                        var opt = { headers: { 'Range': 'bytes=0-0' }, credentials: 'include' };
                        if (ac) opt.signal = ac.signal;
                        fetch(u, opt).then(function (r) {
                          var ct = (r.headers.get('content-type') || '').toLowerCase();
                          if (ac) { try { ac.abort(); } catch (e) {} }
                          if (/^video\\//.test(ct) || /^audio\\//.test(ct) || ct.indexOf('mpegurl') >= 0 || ct.indexOf('m3u8') >= 0) fin(u);
                        }).catch(function () {});
                      } catch (e) {}
                    }
                    // 截止按时间界不按拍数：首拍在注入后 2s、末界与原 26 拍×1.5s
                    // 同长（≈41s）。节拍加密（JW 出现后 300ms）只加密采样、不缩
                    // 总窗——jwplayer 函数先于列表就绪出现是装配常态，按拍数计
                    // 上限会把总窗烧到十秒级（G62 复核订正）。
                    var deadline = Date.now() + 41000;
                    function tick() {
                      if (done) return;
                      var cs = cands();
                      for (var i = 0; i < cs.length; i++) {
                        var c = cs[i];
                        if (!isHttp(c.u) || c.u === location.href) continue;
                        if (c.jw && mediaPath(c.u)) { fin(c.u); return; }
                        probe(c.u);
                      }
                      // 截止判在采样之后：压线拍（名义 41s、实际必迟到）先采样
                      // 再置 dead，40.x 秒压线就绪的列表不被截止误杀；置 dead
                      // 后轮询的慢等尾巴据此收口发空终局（G62 复核再订正）。
                      if (Date.now() > deadline) { window._hkFbState = 'dead'; return; }
                      // JW 已装配后节拍加密（1.5s→300ms），抢装配完成后的出址窗口（G62）。
                      setTimeout(tick, lastHasJw ? 300 : 1500);
                    }
                    setTimeout(tick, 2000);
                  })();
                }
                """;
        return js.replace("__TYPE__", type).replace("__TICKET__", ticket);
    }

    private String generateExtraJS(String jsExpr, String type, String ticket) {
        StringBuilder sb = new StringBuilder();
        sb.append("      function getUrl() {\n        try {         return eval('");
        sb.append(escapeJavaScriptString(jsExpr));
        sb.append("');\n        } catch(e) {\n         return null;\n        }      }\nif(window._cc == null){\n            window._cc = 0;            function check() {\n                if (_cc > 120){\n");
        // G62：轮询耗尽不得抢先发空终局——补采（generateFallbackJS）仍在存活期
        // （window._hkFbState 未置 'dead'）时以 1 秒慢拍续等，收口权归补采截止；
        // 补采未注入（_hkFbState 不存在，如 executeWebRule 链）时维持原版直发 ''。
        // 最终兜底仍有宿主 45 秒守卫（HkDetailBridge.parseWithTimeout）裁决。
        sb.append("                   if (window._hkFbState && window._hkFbState !== 'dead'){                   setTimeout(check, 1000);\n                   return;                }                fy_bridge_app.finishParse('', '");
        sb.append(type);
        sb.append("', '");
        sb.append(ticket);
        sb.append("');\n                   return;                }                _cc++;                setTimeout(() => {\n                    var url = getUrl();\n                    if (url != null && url !== '') {\n                        fy_bridge_app.finishParse(url, '");
        sb.append(type);
        sb.append("', '");
        sb.append(ticket);
        sb.append("');\n                    } else {\n                        check();\n                    }\n                }, 250)\n            }\n            check();\n}\n");
        return sb.toString();
    }

    /** 照 adblockplus Utils.escapeJavaScriptString：\n→\\n、\r→\\r、" ' \ 前加反斜杠，末尾把 U+2028/U+2029 换成字面转义。 */
    public static String escapeJavaScriptString(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '"' || c == '\'' || c == '\\') {
                sb.append('\\').append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString().replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
    }
}
