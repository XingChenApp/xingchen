package com.example.hikerview.utils;

import android.app.Activity;

import com.example.hikerview.service.parser.PageParser;
import com.example.hikerview.ui.ActivityManager;
import com.fongmi.android.tv.api.hk.HkJsRuntime;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.ui.hk.HkColType;
import com.fongmi.android.tv.ui.hk.HkDetailBridge;
import com.fongmi.android.tv.ui.hk.HkListActivity;

/**
 * 官 {@code com.example.hikerview.utils.AutoImportHelper} 的插件面适配。
 *
 * <p>只落插件（hikerPop.js 的 toNextPage）实调的一个合成方法
 * {@code lambda$couldCloudImport$2(String)}：官义为「云口令求值结果分发器」，
 * 插件借它打开 hiker://page/ 子页。分发顺序照官 jadx 原样：
 * error: → 失败提示；空/undefined → 未处理；toast:// → 提示；
 * web:// → 网页；x5:// → 临时规则页；hiker://page/ → 开子页并 return；
 * 其余 → checkText 口令分发；未处理 → 成功提示。
 *
 * <p>各支的星辰落点：toast 走本树 ToastMgr；web 走 HkDetailBridge.goWeb
 * （＝官 WebUtil.goWeb 的星辰转写宿主钩子）；x5 按星辰 HkArticleFragment
 * 既有的 x5 子页构造法（临时规则＋findRule JS＋X5_WEB_VIEW 列型）经
 * HkListActivity.startRule 打开，标题照官为「云口令」；page 支走宿主
 * HkListActivity.openPageUrlFromJs（以本页当前规则经 HkPageParser 组页）；
 * checkText 走 HkDetailBridge.hooks（星辰早已转写的同一官方法）。
 * 官 couldCloudImport 本体（口令弹窗链）插件不调，未落。
 */
public class AutoImportHelper {

    public static void lambda$couldCloudImport$2(String str) {
        Activity currentActivity = ActivityManager.getInstance().getCurrentActivity();
        if (StringUtil.isNotEmpty(str) && str.startsWith("error:")) {
            ToastMgr.shortBottomCenter(currentActivity, "云口令导入失败");
            return;
        }
        boolean z;
        if (!StringUtil.isNotEmpty(str) || "undefined".equalsIgnoreCase(str)) {
            z = false;
        } else {
            z = true;
            if (str.startsWith(StringUtil.SCHEME_TOAST)) {
                // 官为 StringUtils.replaceOnceIgnoreCase；前置 startsWith 已定原样前缀，截头等义
                ToastMgr.shortBottomCenter(currentActivity,
                        str.substring(StringUtil.SCHEME_TOAST.length()));
            } else if (str.startsWith(StringUtil.SCHEME_WEB)) {
                new HkDetailBridge().goWeb(currentActivity,
                        firstPageUrl(str));
            } else if (str.startsWith("x5://")) {
                String inner = str.substring("x5://".length());
                HkRule rule = new HkRule();
                rule.setUrl("hiker://empty");
                rule.setFindRule("js:setResult([{\n    url:\""
                        + HkJsRuntime.escapeJsString(inner)
                        + "\",\ncol_type:\"x5_webview_single\",\ndesc:\"100%&&float\",\nextra:{canBack: true}\n}]);");
                rule.setColType(HkColType.X5_WEB_VIEW.code);
                rule.setTitle("云口令");
                if (currentActivity != null) {
                    HkListActivity.startRule(currentActivity, rule, "云口令");
                }
            } else {
                if (PageParser.isPageUrl(str)) {
                    if (currentActivity instanceof HkListActivity) {
                        ((HkListActivity) currentActivity).openPageUrlFromJs(str);
                    }
                    return;
                }
                z = HkDetailBridge.hooks.checkText(currentActivity, str);
            }
        }
        if (z) {
            return;
        }
        ToastMgr.shortBottomCenter(currentActivity, "云口令导入成功");
    }

    /** 官支：HttpParser.getFirstPageUrl(replaceOnce(str, SCHEME_WEB, ""))，
     *  经星辰已转写的同名口径（HkDetailBridge.getFirstPageUrl）。 */
    private static String firstPageUrl(String str) {
        String body = str.substring(StringUtil.SCHEME_WEB.length());
        return new HkDetailBridge().getFirstPageUrl(body);
    }
}
