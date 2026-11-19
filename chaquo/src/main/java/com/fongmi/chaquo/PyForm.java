package com.fongmi.chaquo;

import java.util.regex.Pattern;

/**
 * py 形态判据（新老分发，落点在 Loader.spider(api, siteKey)）：按源码特征判，
 * 不靠文件名/站点猜。判据全部来自新老契约的代码实证（账 fongmi-newpy）：
 *
 * 新形态强标记（老基类/老桥下不存在、写了必崩，故出现即新形态）：
 * 四个注入面 self.net / self.local / self.js / self.dialog / self.runtime 的
 * 属性使用；新桥独有入口 def danmaku( / def proxy(（老桥只调 localProxy）；
 * 新基类独有工具 self.proxyUrl( / self.danmakuUrl( / self.page( / Spider.page(。
 *
 * 老形态否决项（新基类已删除、老桥独有，出现即老形态，优先于新标记）：
 * def localProxy(（老桥代理入口）、def getName(（新契约已废除）、老基类工具
 * 方法 self.fetch/post/html/getCache/setCache/delCache/getProxyUrl/regStr/
 * removeHtmlTags/cleanText/str2json/json2str 的调用。
 *
 * 规则：命中任一新标记且无否决项 → 新路；其余一律老路。存量老源的形态特征
 * 都带否决项或无新标记，永不误分；新源无标记时落老路＝维持现状，不构成回归。
 */
public final class PyForm {

    private static final Pattern NEW_MARKERS = Pattern.compile(
            "self\\.(?:net|local|js|dialog|runtime)\\s*[.\\[]"
                    + "|self\\.dialog\\s*\\("
                    + "|def\\s+(?:danmaku|proxy)\\s*\\("
                    + "|self\\.(?:proxyUrl|danmakuUrl|page)\\s*\\("
                    + "|Spider\\.page\\s*\\(");

    private static final Pattern OLD_VETOES = Pattern.compile(
            "def\\s+(?:localProxy|getName)\\s*\\("
                    + "|self\\.(?:fetch|post|html|getCache|setCache|delCache|getProxyUrl|regStr|removeHtmlTags|cleanText|str2json|json2str)\\s*\\(");

    private PyForm() {
    }

    public static boolean isNewForm(String source) {
        if (source == null || source.isEmpty()) return false;
        if (OLD_VETOES.matcher(source).find()) return false;
        return NEW_MARKERS.matcher(source).find();
    }
}
