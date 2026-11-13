package com.fongmi.android.tv.api.hk;

import java.util.List;
import java.util.Locale;

/**
 * 选集收编的干跑分类（钉根账 playpage-episode-pollution-dig-20261111 §九）：
 * 形态判定只能按条目自带声明剔「即时动作」基址，基址是真实页面、函数
 * 体里现拼动作串的解析型条目形态上与真集数无别——127 真机《蜘蛛侠：
 * 崭新之日》选集混入海阔搜索/聚阅/香情/排序钮即此漏。§十换官形后本类
 * 只服务 toVod 的段内复核（页面收集器已改走 HkChapters 连续段）。
 * 本类在收编时以干跑求值（{@link HkJsRuntime#evalLazyDry}：远程 fetch 回空、
 * 变量写隔离、results 只取增量）实测每个解析型条目的 lazy 函数体去向，
 * 按返回值/事件判三类：
 *
 * <ul>
 *   <li>ACTION：求值调了 refreshPage，或返回/产出动作·导航串（动作 scheme
 *       正表、hiker:// 内部导航、javascript:、share/web/x5 系）——非集数，
 *       收编剔除，点击仍走详情页原分发；</li>
 *   <li>PLAYABLE：返回/产出可播串（http(s)、磁力三形、#isVideo=true#、
 *       再解析型 @lazyRule=、video://）——集数候选保留；</li>
 *   <li>UNKNOWN：抛错、无果、或只回空串——不确定，保留（不误杀）。</li>
 * </ul>
 *
 * <p>非对称安全性：框架动作 lazy 全是纯串拼接、零网络即出结果；真集数
 * lazy 需 fetch 取址，干跑下只会落 UNKNOWN。特别地，**空串不判 ACTION**:
 * 它与「集数 lazy 因 fetch 回空而无果」不可区分，按账 §九 的安全约束
 * 归 UNKNOWN——账面初稿曾把空列进动作串，以安全约束为准，此处订正。</p>
 */
public final class HkEpisodeDryClassifier {

    public enum Verdict {
        ACTION, PLAYABLE, UNKNOWN
    }

    private HkEpisodeDryClassifier() {
    }

    /** 纯函数：按干跑证据判类（可离线单测）。 */
    public static Verdict classify(String returnValue, boolean refreshRequested, List<String> resultUrls) {
        if (refreshRequested) return Verdict.ACTION;
        String rv = returnValue == null ? "" : returnValue.trim();
        if (!rv.isEmpty() && !"undefined".equals(rv) && !"null".equals(rv)) {
            if (isActionResult(rv)) return Verdict.ACTION;
            if (isPlayableResult(rv)) return Verdict.PLAYABLE;
        }
        if (resultUrls != null) {
            boolean anyPlayable = false;
            for (String u : resultUrls) {
                if (u == null) continue;
                String t = u.trim();
                if (t.isEmpty()) continue;
                if (isActionResult(t)) return Verdict.ACTION;
                if (isPlayableResult(t)) anyPlayable = true;
            }
            if (anyPlayable) return Verdict.PLAYABLE;
        }
        return Verdict.UNKNOWN;
    }

    /** 动作/导航串正表：动作 scheme 复用 HkRouter.isActionUrl，再补内部导航与分发系。 */
    public static boolean isActionResult(String v) {
        if (v == null) return false;
        String t = v.trim();
        String low = t.toLowerCase(Locale.ROOT);
        if (HkRouter.isActionUrl(t)) return true;
        // hiker:// 内部导航全形（page/search/home/empty 等）都是页面动作、不是播放地址
        if (low.startsWith("hiker://")) return true;
        return low.startsWith("javascript:")
                || low.startsWith("share://")
                || low.startsWith("web://")
                || low.startsWith("x5://")
                || low.startsWith("x5webview://")
                || low.startsWith("pics://")
                || low.startsWith("code://")
                || low.startsWith("rule://");
    }

    /** 可播串正表：与资格判定的放行口径同族（http/磁力三形）＋再解析型与视频标记。 */
    public static boolean isPlayableResult(String v) {
        if (v == null) return false;
        String t = v.trim();
        String low = t.toLowerCase(Locale.ROOT);
        if (low.contains("#isvideo=true#")) return true;
        if (t.contains("@lazyRule=")) return true;
        if (low.startsWith("magnet:") || low.startsWith("ed2k:") || low.startsWith("thunder:")) {
            return true;
        }
        if (low.startsWith("video://")) return true;
        return low.startsWith("http://") || low.startsWith("https://");
    }

    /** 条目 url 是否为可干跑的 JS 型 lazy（@lazyRule=.js:/js: 或纯 js: 条目）。 */
    public static boolean isJsLazy(String url) {
        if (url == null) return false;
        String u = url.trim();
        if (u.startsWith("js:")) return true;
        int lr = u.indexOf("@lazyRule=");
        if (lr < 0) return false;
        String body = u.substring(lr + "@lazyRule=".length()).trim();
        return body.startsWith(".js:") || body.startsWith("js:");
    }

    /**
     * 对单个条目 url 干跑分类：提取 JS lazy 函数体（与 HkRouter.evalEntryLazy
     * 同解析口径：decodeConflict＋剥 .js:/js: 前缀），在给定运行时上干跑求值。
     * 非 JS 型 lazy（选择器求值需先 fetch 页面）回 UNKNOWN——干跑不取数、判不了。
     */
    public static Verdict classifyLazyItem(HkJsRuntime rt, String itemUrl, String pageUrl) {
        if (rt == null || itemUrl == null) return Verdict.UNKNOWN;
        String u = itemUrl.trim();
        String jsCode;
        String evalPageUrl;
        int lr = u.indexOf("@lazyRule=");
        if (lr >= 0) {
            String body = u.substring(lr + "@lazyRule=".length()).trim();
            if (body.startsWith(".js:")) {
                jsCode = HkSelector.decodeConflict(body.substring(4).trim());
            } else if (body.startsWith("js:")) {
                jsCode = HkSelector.decodeConflict(body.substring(3).trim());
            } else {
                return Verdict.UNKNOWN;
            }
            evalPageUrl = u.substring(0, lr).trim();
        } else if (u.startsWith("js:")) {
            jsCode = u.substring(3);
            evalPageUrl = pageUrl == null || pageUrl.isEmpty() ? u : pageUrl;
        } else {
            return Verdict.UNKNOWN;
        }
        if (jsCode.isEmpty()) return Verdict.UNKNOWN;
        HkJsRuntime.DryEvalResult r = rt.evalLazyDry(jsCode, evalPageUrl);
        if (r.error) return Verdict.UNKNOWN;
        return classify(r.returnValue, r.refreshRequested, r.resultUrls);
    }
}
