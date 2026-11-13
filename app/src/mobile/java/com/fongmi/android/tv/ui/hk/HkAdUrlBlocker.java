package com.fongmi.android.tv.ui.hk;

import java.util.List;

/**
 * 照 8.83 com.example.hikerview.ui.browser.model.AdUrlBlocker 的 shouldBlock/checkBlockRule/includes 转写。
 * 规则语义（照原文）：blockForward@ 开头恒不拦；@domain= 后段要求当前域名包含该段才继续判定；
 * "!!" 开头是例外（白名单）规则——任一片段命中回 false（不拦），全不命中回 true；
 * 片段 "$dom" 比对请求域名包含当前域名、"$topdom" 比对请求域名包含当前域名的二级域名、余为通配 includes。
 * includes 为按 "*" 切段后的顺序子串匹配（原版 >2 段/2 段/1 段三支语义经核等价于同一顺序匹配）。
 * <p>
 * 全局存量（G48 补）：官 AdUrlBlocker 是单例存量——addUrls 把导入规则附带的
 * 拦截 URL 去重并入 blockUrls 持久表、网页层拦截时与规则自带 blockRules 并列
 * 消费。旧转写只有匹配引擎、无存量，导入附带串无处可并。咱家存量落
 * SharedPreferences「hk_ad_block」（条目集），addUrls 语义照官：跳空、按串
 * 去重、返回新增条数。
 */
public class HkAdUrlBlocker {

    private static final String PREFS = "hk_ad_block";
    private static final String KEY_URLS = "urls";

    /**
     * 官构造内置两条（AdUrlBlocker.smali 构造 :71/:82）——注意形态：不是拦截条目，
     * 是白名单两面：whiteList＝{"cdn.bootcss.com"}（请求址含之恒不拦，shouldBlock
     * :1410 区）、whiteDomainList＝{"haikuoshijie.cn"}（页面址含之恒不拦，shouldBlock
     * :1375 区／getOverrideMode :1042 区）。审计 E 环8 只记「内置默认条目含两条」、
     * 合成账写作「并入 adBlockUrls 初始集」，按官包本体订正为白名单落：两条是
     * 类内置常量、恒在位，不落 SP 存量——无存量/有存量两形下都先于拦截表生效。
     */
    static final String[] WHITE_LIST = {"cdn.bootcss.com"};
    static final String[] WHITE_DOMAIN_LIST = {"haikuoshijie.cn"};

    /** 官 shouldBlock 的白名单前置：页面址命中 whiteDomainList 或请求址命中 whiteList 即不拦。 */
    static boolean isWhitelisted(String lastDom, String url) {
        if (lastDom != null) {
            for (String d : WHITE_DOMAIN_LIST) {
                if (lastDom.contains(d)) return true;
            }
        }
        if (url != null) {
            for (String w : WHITE_LIST) {
                if (url.contains(w)) return true;
            }
        }
        return false;
    }

    /** 内存缓存（进程内单例语义）；null 表示尚未从持久层加载。 */
    private static List<String> globalUrls;

    /** 官 adBlockUrls 的分隔形：多条以 "##" 连接（AutoImportHelper split("##")）。 */
    public static List<String> splitAdBlockUrls(String adBlockUrls) {
        List<String> out = new java.util.ArrayList<>();
        if (adBlockUrls == null) return out;
        for (String s : adBlockUrls.split("##")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static synchronized List<String> loadGlobal() {
        if (globalUrls == null) {
            globalUrls = new java.util.ArrayList<>();
            try {
                android.content.SharedPreferences sp = com.fongmi.android.tv.App.get()
                        .getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
                java.util.Set<String> set = sp.getStringSet(KEY_URLS, null);
                if (set != null) globalUrls.addAll(set);
            } catch (Throwable t) {
                // 名单加载失败与「名单真空」要可区分：记日志（批 fix349）。
                android.util.Log.e("HkAdUrlBlocker", "广告拦截名单加载失败", t);
            }
        }
        return globalUrls;
    }

    /** 全局拦截表当前条目（副本）。 */
    public static List<String> getGlobalUrls() {
        return new java.util.ArrayList<>(loadGlobal());
    }

    /**
     * 并入全局拦截表（官 AdUrlBlocker.addUrls 同语义）：跳过空串、与既有条目
     * 按串去重，只追加新条目并落盘，返回新增条数。
     */
    public static synchronized int addUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) return 0;
        List<String> cur = loadGlobal();
        java.util.Set<String> seen = new java.util.HashSet<>(cur);
        java.util.List<String> addedList = new java.util.ArrayList<>();
        for (String u : urls) {
            if (u == null) continue;
            String t = u.trim();
            if (t.isEmpty() || !seen.add(t)) continue;
            cur.add(t);
            addedList.add(t);
        }
        int added = addedList.size();
        if (added > 0) {
            try {
                com.fongmi.android.tv.App.get()
                        .getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                        .edit().putStringSet(KEY_URLS, new java.util.HashSet<>(cur)).apply();
            } catch (Throwable t) {
                // 落盘失败不许假报新增数：回滚内存新增并回 0（批 fix349）。
                android.util.Log.e("HkAdUrlBlocker", "广告拦截名单保存失败，已回滚", t);
                cur.removeAll(addedList);
                return 0;
            }
        }
        return added;
    }

    /** 全局拦截表判拦（网页层与规则自带 blockRules 并列消费）。 */
    public static boolean shouldBlockGlobal(String lastDom, String url) {
        List<String> snapshot;
        synchronized (HkAdUrlBlocker.class) {
            snapshot = new java.util.ArrayList<>(loadGlobal());
        }
        if (snapshot.isEmpty()) return false;
        return shouldBlock(snapshot, lastDom, url);
    }

    public static boolean shouldBlock(List<String> rules, String lastDom, String url) {
        if (rules == null || rules.isEmpty()) return false;
        if (url == null) return false;
        // 官 shouldBlock 先过白名单两面（whiteDomainList 查页面址、whiteList 查请求址）才进 blockUrls 循环。
        if (isWhitelisted(lastDom, url)) return false;
        String dom = HkStringUtil.getDom(lastDom);
        for (String rule : rules) {
            if (checkBlockRule(url, dom, rule)) return true;
        }
        return false;
    }

    /**
     * 嗅探模式请求层判杀（补遗七，账 bestjav-sniff-retrace-20261110 并入 G62；
     * 判定口径经 G62 复核订正）：按条目形态分判——
     * ① 裸域名条目只走主机锚：请求主机等于该域或其子域才判（AdblockPlus
     *    ||domain^ 的等价形），退出子串源——正片地址的路径/参数里恰含该域名
     *    子串不再误杀（初版双源同判时子串源把锚的收窄抵消，复核点名）；
     * ② 非裸域名条目（通配/作用域形与裸词条）经 checkBlockRule 的官规则语法
     *    子串判，与 shouldBlock 同判据。
     * 仅嗅探模式走此面，可见浏览不改。
     */
    public static boolean shouldBlockSniff(List<String> rules, String lastDom, String url) {
        if (rules == null || rules.isEmpty() || url == null) return false;
        String host = HkStringUtil.getDom(url);
        if (host != null) {
            int at = host.lastIndexOf('@');
            if (at >= 0) host = host.substring(at + 1);
            int colon = host.indexOf(':');
            if (colon >= 0) host = host.substring(0, colon);
        }
        String dom = HkStringUtil.getDom(lastDom);
        for (String rule : rules) {
            if (isBareDomain(rule)) {
                if (host != null && (host.equals(rule) || host.endsWith("." + rule))) {
                    return true;
                }
            } else if (checkBlockRule(url, dom, rule)) {
                return true;
            }
        }
        return false;
    }

    /** 裸域名形（无通配/无作用域符/无路径段）才可作主机锚。 */
    private static boolean isBareDomain(String rule) {
        if (rule == null || rule.isEmpty()) return false;
        if (rule.indexOf('*') >= 0 || rule.indexOf('@') >= 0 || rule.indexOf('!') >= 0
                || rule.indexOf('/') >= 0 || rule.indexOf(' ') >= 0) return false;
        int dot = rule.lastIndexOf('.');
        return dot > 0 && dot < rule.length() - 1;
    }

    public static boolean checkBlockRule(String url, String dom, String rule) {
        if (HkStringUtil.isEmpty(rule) || HkStringUtil.isEmpty(url)) return false;
        if (rule.startsWith("blockForward@")) return false;
        String[] domainParts = rule.split("@domain=");
        String r = rule;
        if (domainParts.length >= 2) {
            r = domainParts[0];
            if (dom == null || !dom.contains(domainParts[1])) return false;
        }
        if (r.startsWith("!!")) {
            String sub = r.substring(2);
            String[] segments = sub.split("!!");
            boolean matched = false;
            for (String seg : segments) {
                if ("$dom".equals(seg)) {
                    String reqDom = HkStringUtil.getDom(url);
                    if (reqDom != null && dom != null && reqDom.contains(dom)) {
                        matched = true;
                        break;
                    }
                    if (includes(url, dom)) {
                        matched = true;
                        break;
                    }
                } else if ("$topdom".equals(seg)) {
                    String second = HkStringUtil.getSecondDom(dom);
                    String reqDom = HkStringUtil.getDom(url);
                    if (reqDom != null && second != null && reqDom.contains(second)) {
                        matched = true;
                        break;
                    }
                } else {
                    if (includes(url, seg)) {
                        matched = true;
                        break;
                    }
                }
            }
            return !matched;
        }
        return includes(url, r);
    }

    private static boolean includes(String url, String pattern) {
        if (url == null || pattern == null) return false;
        String[] parts = pattern.split("\\*");
        String rest = url;
        for (String part : parts) {
            int idx = rest.indexOf(part);
            if (idx < 0) return false;
            rest = rest.substring(idx + part.length());
        }
        return true;
    }
}
