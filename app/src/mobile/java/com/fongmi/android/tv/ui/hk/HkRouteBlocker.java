package com.fongmi.android.tv.ui.hk;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import androidx.preference.PreferenceManager;

import com.fongmi.android.tv.api.hk.HkItem;
import com.fongmi.android.tv.api.hk.HkJsRuntime;
import com.fongmi.android.tv.api.hk.HkPageParser;
import com.fongmi.android.tv.api.hk.HkParserKit;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.api.hk.HkRuleManager;
import com.fongmi.android.tv.ui.activity.AdBlockActivity;
import com.fongmi.android.tv.ui.activity.DownloadActivity;
import com.fongmi.android.tv.ui.activity.FileActivity;
import com.fongmi.android.tv.ui.activity.HistoryActivity;
import com.fongmi.android.tv.ui.activity.HkFavoriteActivity;
import com.fongmi.android.tv.ui.activity.HkPageActivity;
import com.fongmi.android.tv.ui.activity.KeepActivity;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

import android.util.Log;

/**
 * 照抄 8.83 RouteBlocker（smali_classes2/ui/home/model/RouteBlocker.smali）：
 * isRoute 的前置检查、分支判定与"命中 hiker:// 路由恒吞掉（返回 true）"语义逐字转写；
 * 各目标页（书签/工具/文件浏览/历史/收藏/规则管理/全局搜索等）在星辰由宿主钩子承接，未接时只吞不跳。
 * webdav / webRule 两支按既定决策不移植（WebDAV 与远程编辑子系统不搬），命中仍返回 true。
 */
public final class HkRouteBlocker {
    private static final String TAG = "HkRouteBlocker";

    private HkRouteBlocker() {
    }

    /** 路由目标页与规则库查询的宿主钩子（宿主阶段绑定真实现）。 */
    public interface RouteHooks {
        /** bookmarkGroup：展示某分组的书签，点中后以书签 url 回调（原版 consumer → WebUtil.goWeb）。 */
        default void showBookmarks(Activity activity, String group, Consumer<String> onUrl) {
        }

        /** tools：MoreSettingOfficer.showToolsView。 */
        default void showTools(Activity activity) {
        }

        /** webSearch：WebUtil.goWebHomeAndSearch。 */
        default void goWebHomeAndSearch(Activity activity) {
        }

        /** 打开网页（原版 WebUtil.goWeb，bookmarkGroup 回调与 home 路由 http 候选共用）。 */
        default void goWeb(Context context, String url) {
        }

        /** explore / download(home_late) / localMedia：文件浏览与本地媒体页。 */
        default void openFileBrowser(Activity activity, String path) {
        }

        default void openLocalMedia(Activity activity) {
        }

        /** rulesGroup 后台任务（lambda$isRoute$3）：查组内规则、滤隐私规则、按 article_list_order 排序后以书签式列表展示。 */
        default void showRulesGroup(Activity activity, String group) {
        }

        /** home 路由：打开规则管理页（原版 ArticleListRuleMagActivity）。 */
        default void openRuleManager(Activity activity) {
        }

        /** home@规则名：原版按标题查规则库，存在才发 OnHomePageChangedEvent。 */
        default boolean ruleExistsByTitle(String title) {
            return false;
        }

        /** home@规则名：切换小程序首页到该规则（原版 OnHomePageChangedEvent 就地切换；星辰新开规则页）。 */
        default void onHomePageChanged(Activity activity, String ruleTitle) {
        }

        /** 当前活动是否为小程序主页（原版 MainActivity / RuleWindowActivity 型判断，决定 home 路由后是否 finish）。 */
        default boolean isHomeActivity(Activity activity) {
            return false;
        }

        /** bookmark / js / downloaded / history / collection / adUrl / adRule 目标页。 */
        default void openBookmarkPage(Activity activity) {
        }

        default void openJsList(Activity activity) {
        }

        default void openDownloadRecords(Activity activity) {
        }

        default void openHistory(Activity activity, int fromActivity, String search) {
        }

        default void openCollection(Activity activity, int fromActivity, String group, String search) {
        }

        default void openAdUrlList(Activity activity) {
        }

        default void openAdRuleList(Activity activity) {
        }

        /** setting / settingMore：原版 EventBus ShowSettingMenuEvent / ShowMoreSettingMenuEvent。 */
        default void showSettingMenu(Activity activity) {
        }

        default void showMoreSettingMenu(Activity activity) {
        }

        /** 全局搜索弹窗（原版 GlobalSearchPopup.startSearch(activity, engine, keyword, "main", color)；color 为 8.83 资源色，宿主自理）。 */
        default void startGlobalSearch(Activity activity, String engine, String keyword) {
        }

        /** 按标题查规则（原版 LitePal title = ?），供 search 路由解析引擎/分组；无则 null。 */
        default HkRule findRuleByTitle(String title) {
            return null;
        }

        /** search 路由的委托搜索（原版 startSearchByDelegateRules）。 */
        default void startDelegateSearch(Activity activity, String keyword, HkRule rule, String rules, boolean delegateOnly) {
        }

        /** search 路由 rule 非空且 simple 为空时：原版 SearchModel.getEngines 异步选引擎（RouteBlocker$3）。 */
        default void loadSearchEngines(Activity activity, String ruleName, String keyword) {
        }

        /** search 路由 simple 非空：原版 SearchInOneRuleActivity（extras wd / rule）。 */
        default void openSearchInOneRule(Activity activity, String keyword, String ruleName) {
        }
    }

    public static volatile RouteHooks hooks = new RouteHooks() {
    };

    private static RouteHooks hooks() {
        RouteHooks h = hooks;
        return h != null ? h : new RouteHooks() {
        };
    }

    /**
     * 一次性装配全部 RouteHooks（搜索 + 目标页），供 HkListActivity.ensureStatics 调用；幂等。
     * 搜索钩子逻辑与 HkSearchActivity 原 wireRouteHooks 同源；目标页钩子按 8.83 语义接到星辰现成页面；
     * 星辰无对应页面的钩子给「该功能暂不支持」兜底、不再静默吞（webdav/webRule 按既定决策保持仅吞路由）。
     */
    public static void wireAllHooks() {
        hooks = new RouteHooks() {
            // ---------------- 目标页（8.83 RouteBlocker 各分支） ----------------

            @Override
            public void showBookmarks(Activity activity, String group, Consumer<String> onUrl) {
                // 8.83 书签分组列表弹窗；星辰无浏览器书签存储
                toastShortBottom(activity, "该功能暂不支持");
            }

            @Override
            public void showTools(Activity activity) {
                // 8.83 MoreSettingOfficer.showToolsView；星辰无对应工具视图
                toastShortBottom(activity, "该功能暂不支持");
            }

            @Override
            public void goWebHomeAndSearch(Activity activity) {
                // 8.83 WebUtil.goWebHomeAndSearch（浏览器主页搜索）；星辰无浏览器主页
                toastShortBottom(activity, "该功能暂不支持");
            }

            @Override
            public void goWeb(Context context, String url) {
                // 环29 D29-1 同口径（大扫查同型收齐）：先查既有网页承载
                // （HkWebCarrier 两形），无承载才开行内 x5 页；非 Activity
                // 上下文回退系统浏览器。与 HkDetailBridge.goWeb 同一决策口。
                if (HkWebCarrier.loadIntoExisting(url)) return;
                try {
                    if (context instanceof Activity) {
                        HkListActivity.openX5PlayPage((Activity) context, "", url);
                        return;
                    }
                } catch (Exception ignored) {
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "hk error", e);
                }
            }

            @Override
            public void openFileBrowser(Activity activity, String path) {
                FileActivity.start(activity, path);
            }

            @Override
            public void openLocalMedia(Activity activity) {
                // 8.83 本地媒体页；星辰以文件浏览页承接
                FileActivity.start(activity, null);
            }

            @Override
            public void showRulesGroup(Activity activity, String group) {
                HkPageActivity.startWithGroup(activity, group);
            }

            @Override
            public void openRuleManager(Activity activity) {
                HkPageActivity.start(activity);
            }

            @Override
            public boolean ruleExistsByTitle(String title) {
                return findRuleByTitle(title) != null;
            }

            @Override
            public void onHomePageChanged(Activity activity, String ruleTitle) {
                // 8.83 发 OnHomePageChangedEvent 就地切换；星辰语义是新开规则页，
                // 旧页由 isRoute 的 home 分支在 handled 后 finish（已在小程序列表时不关）
                HkRule rule = findRuleByTitle(ruleTitle);
                if (rule != null) HkListActivity.startRule(activity, rule, rule.getTitle());
                else toastShortBottom(activity, "首页路由规则有误");
            }

            @Override
            public boolean isHomeActivity(Activity activity) {
                // 星辰的 hk 主页即小程序列表页（HkPageActivity）；「我的主页」规则已照官放行（环41 摘入口拦截）
                return activity instanceof HkPageActivity;
            }

            @Override
            public void openBookmarkPage(Activity activity) {
                // 8.83 书签页；星辰无浏览器书签页
                toastShortBottom(activity, "该功能暂不支持");
            }

            @Override
            public void openJsList(Activity activity) {
                // 8.83 js 列表页；星辰无对应页面（PluginActivity 管的是 py 插件）
                toastShortBottom(activity, "该功能暂不支持");
            }

            @Override
            public void openDownloadRecords(Activity activity) {
                DownloadActivity.start(activity);
            }

            @Override
            public void openHistory(Activity activity, int fromActivity, String search) {
                HistoryActivity.start(activity);
            }

            @Override
            public void openCollection(Activity activity, int fromActivity, String group, String search) {
                // 对齐 8.83：收藏是海阔侧自己的体系（ViewCollection），不再进主 App 的 Keep（首页收藏）
                HkFavoriteActivity.start(activity, group);
            }

            @Override
            public void openAdUrlList(Activity activity) {
                // 8.83 广告网址列表；星辰唯一的广告页
                AdBlockActivity.start(activity);
            }

            @Override
            public void openAdRuleList(Activity activity) {
                // 8.83 广告规则列表；星辰唯一的广告页
                AdBlockActivity.start(activity);
            }

            @Override
            public void showSettingMenu(Activity activity) {
                // 8.83 EventBus ShowSettingMenuEvent（小程序设置菜单）；星辰无 hk 设置菜单
                toastShortBottom(activity, "该功能暂不支持");
            }

            @Override
            public void showMoreSettingMenu(Activity activity) {
                // 8.83 EventBus ShowMoreSettingMenuEvent（小程序更多设置）；星辰无 hk 设置菜单
                toastShortBottom(activity, "该功能暂不支持");
            }

            // ---------------- 搜索（与 HkSearchActivity 原逻辑同源） ----------------

            @Override
            public void startGlobalSearch(Activity activity, String engine, String keyword) {
                if (engine != null && !engine.isEmpty()) {
                    HkRule rule = HkRuleManager.get().getRule(engine);
                    if (rule != null) {
                        HkSearchActivity.startSearchInOneRule(activity, rule, keyword);
                        return;
                    }
                }
                HkSearchActivity.startGlobalSearch(activity, keyword);
            }

            @Override
            public HkRule findRuleByTitle(String title) {
                if (title == null) return null;
                HkRule rule = HkRuleManager.get().getRule(title);
                return rule != null ? rule : HkRuleManager.get().getInstalledRule(title);
            }

            @Override
            public void openSearchInOneRule(Activity activity, String kw, String ruleName) {
                HkRule rule = findRuleByTitle(ruleName);
                if (rule != null) HkSearchActivity.startSearchInOneRule(activity, rule, kw);
                else HkSearchActivity.startGlobalSearch(activity, kw);
            }

            @Override
            public void loadSearchEngines(Activity activity, String ruleName, String kw) {
                HkRule rule = findRuleByTitle(ruleName);
                ArrayList<String> titles = new ArrayList<>();
                if (rule != null) {
                    String group = rule.getGroup() == null ? "" : rule.getGroup();
                    for (HkRule r : HkRuleManager.get().getRules()) {
                        if (r != null && r.hasSearch()
                                && group.equals(r.getGroup() == null ? "" : r.getGroup())) {
                            titles.add(r.getTitle());
                        }
                    }
                    if (titles.isEmpty()) titles.add(rule.getTitle());
                }
                HkSearchActivity.startWithEngines(activity, titles, kw, false);
            }

            @Override
            public void startDelegateSearch(Activity activity, String kw, HkRule rule, String rules, boolean delegateOnly) {
                // 环20 _displayName 生产链第1段「物化」（官 RouteBlocker
                // .startSearchByDelegateRules，源行499-531）：rules 空直接返回；
                // 后台以父规则运行时求值 getMyRule(父规则)+rules 表达式得规则
                // 数组串，物化为 HkRule 列表后回主线程起搜索页（分组名＝父规则
                // title、空回落「虚拟分组」）。官以 _rules.tmp/_parent_rules.tmp
                // 两文件传递，咱家等价落点＝HkSearchActivity 静态槽（见该类注）。
                // 旧形只把 rules 串拆成标题字符串交 startWithEngines——表达式
                // 从未求值、被委托规则从未物化，别名步无从发生。
                if (rules == null || rules.isEmpty() || rule == null) return;
                final String groupName = rule.getTitle() == null || rule.getTitle().isEmpty()
                        ? "虚拟分组" : rule.getTitle();
                new Thread(() -> {
                    String result = null;
                    try {
                        HkJsRuntime rt = HkEngineManager.runtimeFor(rule);
                        HkEngineManager.ensureInit(rt);
                        result = rt.evalJS(HkJsRuntime.getMyRule(rule) + rules, kw);
                    } catch (Throwable ignored) {
                    }
                    final String res = result;
                    activity.runOnUiThread(() -> {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        String t = res == null ? "" : res.trim();
                        if (t.isEmpty() || "undefined".equalsIgnoreCase(t) || "[]".equals(t)) {
                            toastShortBottom(activity, "获取规则列表失败");
                            return;
                        }
                        List<HkRule> materialized;
                        try {
                            materialized = new com.google.gson.Gson().fromJson(t,
                                    new com.google.gson.reflect.TypeToken<List<HkRule>>() {
                                    }.getType());
                        } catch (Throwable e) {
                            materialized = null;
                        }
                        if (materialized == null) {
                            toastShortBottom(activity, "获取规则列表失败");
                            return;
                        }
                        HkSearchActivity.startDelegateSearch(activity, materialized,
                                groupName, kw, delegateOnly, rule);
                    });
                }).start();
            }
        };
    }

    /** 照抄 RouteBlocker.isRoute(Activity, String)：转四参版（rule/item 为 null）。 */
    public static boolean isRoute(Activity activity, String url) {
        return isRoute(activity, url, null, null);
    }

    /** 照抄 RouteBlocker.isRoute(Activity, String, Object, BaseResultItem)（smali:29）。 */
    public static boolean isRoute(Activity activity, String url, HkRule rule, HkItem item) {
        if (url == null) return false;
        if (url.startsWith("hiker://empty")) return false;
        if (!url.startsWith("hiker://")) return false;
        if (HkPageParser.isPageUrl(url)) return false;
        if (HkUrlDetector.getImageIdByUrl(url) > 0) return false;
        String route = HkStringUtil.replaceOnce(url, "hiker://", "");
        RouteHooks h = hooks();

        if (route.startsWith("bookmarkGroup")) {
            String group = route.replace("bookmarkGroup@", "");
            // 原版 consumer（lambda$isRoute$0）：WebUtil.goWeb(activity, url, false)
            h.showBookmarks(activity, group, target -> h.goWeb(activity, target));
            return true;
        }
        if (route.equals("tools")) {
            h.showTools(activity);
            return true;
        }
        if (route.equals("webSearch")) {
            h.goWebHomeAndSearch(activity);
            return true;
        }
        if (route.startsWith("explore")) {
            String path = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
            String[] segs = route.split("path=");
            if (segs.length > 1) {
                path = com.fongmi.android.tv.api.hk.HkJsRuntime.getFilePath(
                        HkParserKit.arrayToString(segs, 1, "path="));
            }
            h.openFileBrowser(activity, path);
            return true;
        }
        if (route.startsWith("localMedia")) {
            h.openLocalMedia(activity);
            return true;
        }
        if (route.equals("download") && item != null
                && "hiker://images/home_late".equals(item.getPic())) {
            h.openFileBrowser(activity,
                    android.os.Environment.getExternalStorageDirectory().getAbsolutePath());
            return true;
        }
        if (route.startsWith("rulesGroup")) {
            String group = route.replace("rulesGroup@", "");
            // 原版 HeavyTaskUtil.executeNewTask(lambda$isRoute$3)：后台查组内规则后回主线程展示；
            // 展示必须回主线程且宿主还活着，不许在后台线程直接拉页面
            new Thread(() -> {
                try {
                    if (activity == null) return;
                    activity.runOnUiThread(() -> {
                        if (!activity.isFinishing() && !activity.isDestroyed()) {
                            h.showRulesGroup(activity, group);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "hk error", e);
                }
            }, "hk-rulesGroup").start();
            return true;
        }
        if (route.startsWith("home")) {
            if (route.equals("home")) {
                h.openRuleManager(activity);
            } else if (route.contains("@")) {
                String[] parts = route.split("@");
                boolean handled = false;
                if (parts.length == 2 && !TextUtils.isEmpty(parts[1])) {
                    String[] candidates = parts[1].split("\\|\\|");
                    if (candidates.length > 1) {
                        for (String candidate : candidates) {
                            if (candidate.startsWith("http")) {
                                h.goWeb(activity, candidate);
                                handled = true;
                                break;
                            }
                            if (h.ruleExistsByTitle(candidate)) {
                                h.onHomePageChanged(activity, candidate);
                                handled = true;
                                break;
                            }
                        }
                    } else {
                        // 照 8.83：单候选也要逐个试（http→开网页；规则名命中规则库→切换），
                        // 全不中 handled 保持 false 走下面的报错分支；
                        // 旧代码无条件调 onHomePageChanged 且 handled=true，
                        // 写错的 home@ 路由会静默关页面、原版是弹「首页路由规则有误」。
                        String candidate = parts[1];
                        if (candidate.startsWith("http")) {
                            h.goWeb(activity, candidate);
                            handled = true;
                        } else if (h.ruleExistsByTitle(candidate)) {
                            h.onHomePageChanged(activity, candidate);
                            handled = true;
                        }
                    }
                }
                if (!handled) {
                    toastShortBottom(activity, "首页路由规则有误");
                } else if (!h.isHomeActivity(activity)) {
                    activity.finish();
                }
            } else {
                toastShortBottom(activity, "首页路由规则有误");
            }
            return true;
        }
        if (route.equals("searchSetting")) {
            toastShortBottom(activity, "该页面已下线");
            return true;
        }
        if (route.startsWith("bookmark")) {
            h.openBookmarkPage(activity);
            return true;
        }
        if (route.startsWith("js")) {
            h.openJsList(activity);
            return true;
        }
        if (route.startsWith("download")) {
            // 原版此处复用寄存器里的 "download" 做 startsWith（cond_15），故 download* 一律落下载记录页
            h.openDownloadRecords(activity);
            return true;
        }
        if (route.startsWith("history")) {
            String search = null;
            String param = android.net.Uri.parse("hiker://" + route).getQueryParameter("rule");
            if (!TextUtils.isEmpty(param)) {
                try {
                    search = URLDecoder.decode(param, "utf-8");
                } catch (Exception e) {
                    Log.e(TAG, "hk error", e);
                    search = param;
                }
            }
            h.openHistory(activity, activity.hashCode(), search);
            return true;
        }
        if (route.startsWith("collection")) {
            android.net.Uri uri = android.net.Uri.parse("hiker://" + route);
            String group = decodeQuery(uri.getQueryParameter("group"));
            String search = decodeQuery(uri.getQueryParameter("rule"));
            h.openCollection(activity, activity.hashCode(), group, search);
            return true;
        }
        if (route.equals("adUrl")) {
            h.openAdUrlList(activity);
            return true;
        }
        if (route.equals("adRule")) {
            h.openAdRuleList(activity);
            return true;
        }
        if (route.equals("setting")) {
            h.showSettingMenu(activity);
            return true;
        }
        if (route.equals("webdav")) {
            // WebDAV 子系统按既定决策不移植：原版弹备份文件名输入框（lambda$isRoute$7/$4），星辰仅吞路由
            return true;
        }
        if (route.equals("webRule")) {
            // 远程规则编辑子系统（RemoteServerManager）星辰无对应物：原版复制本机编辑链接并 toast，星辰仅吞路由
            return true;
        }
        if (route.equals("settingMore")) {
            h.showMoreSettingMenu(activity);
            return true;
        }
        if (route.startsWith("search")) {
            startSearchByRoute(activity, route, rule, item);
        }
        // 原版：通过前置检查的 hiker:// 路由最终恒返回 true（未匹配分支也被吞掉）
        return true;
    }

    private static String decodeQuery(String value) {
        if (TextUtils.isEmpty(value)) return value;
        try {
            return URLDecoder.decode(value, "utf-8");
        } catch (Exception e) {
            Log.e(TAG, "hk error", e);
            return value;
        }
    }

    /** 照抄 RouteBlocker.startSearchByRoute（smali:1929）：解析查询串、偏好读写、引擎/分组解析，分发走钩子。 */
    private static void startSearchByRoute(Activity activity, String route, HkRule ruleObj, HkItem item) {
        RouteHooks h = hooks();
        String s = null;
        String ruleName = null;
        String group = null;
        String simple = null;
        String[] parts = route.split("search\\?");
        if (parts.length > 1) {
            for (String param : parts[1].split("&")) {
                String[] kv = param.split("=");
                if (kv.length < 2) continue;
                String key = kv[0];
                String value = HkParserKit.arrayToString(kv, 1, "=");
                if ("s".equals(key)) s = value;
                else if ("rule".equals(key)) ruleName = value;
                else if ("simple".equals(key)) simple = value;
                else if ("ruleGroup".equals(key) || "group".equals(key)) group = value;
            }
        } else {
            // 原版 cond_11：无查询串时仅当活动为小程序主页才打开全局搜索
            if (h.isHomeActivity(activity)) {
                h.startGlobalSearch(activity, prefEngine(activity), s);
            }
            return;
        }
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
        if (HkStringUtil.isEmpty(s)) {
            String engine = prefEngine(activity);
            if (HkStringUtil.isNotEmpty(ruleName)) {
                try {
                    HkRule found = h.findRuleByTitle(ruleName);
                    if (found != null) {
                        try {
                            sp.edit().putString("searchEngine", ruleName).apply();
                            if (HkStringUtil.isNotEmpty(found.getGroup())) group = found.getGroup();
                        } catch (Exception e) {
                            Log.e(TAG, "hk error", e);
                        }
                    } else {
                        ruleName = engine;
                    }
                } catch (Exception e) {
                    ruleName = engine;
                    Log.e(TAG, "hk error", e);
                }
                engine = ruleName;
            }
            if (HkStringUtil.isNotEmpty(group)) {
                sp.edit().putString("searchGroup", group).apply();
            }
            h.startGlobalSearch(activity, engine, "");
            return;
        }
        // s 非空
        if (ruleObj != null && item != null) {
            String rules = item.getExtra("rules");
            if (HkStringUtil.isNotEmpty(rules)) {
                boolean delegateOnly = "true".equals(item.getExtra("delegateOnlySearch"));
                h.startDelegateSearch(activity, s, ruleObj, rules, delegateOnly);
                return;
            }
        }
        if (HkStringUtil.isEmpty(ruleName) && HkStringUtil.isEmpty(group)) {
            h.startGlobalSearch(activity, prefEngine(activity), s);
            return;
        }
        if (HkStringUtil.isEmpty(ruleName)) {
            // 原版 cond_10：只有分组时存分组、用偏好引擎全局搜索
            if (HkStringUtil.isNotEmpty(group)) {
                sp.edit().putString("searchGroup", group).apply();
                h.startGlobalSearch(activity, prefEngine(activity), s);
            }
            return;
        }
        // rule 非空
        if (HkStringUtil.isNotEmpty(simple) || "true".equals(simple)) {
            h.openSearchInOneRule(activity, s, ruleName);
            return;
        }
        sp.edit().putString("searchEngine", ruleName).apply();
        h.loadSearchEngines(activity, ruleName, s);
    }

    private static String prefEngine(Activity activity) {
        return PreferenceManager.getDefaultSharedPreferences(activity)
                .getString("searchEngine", "百度");
    }

    /** 照抄 ToastMgr.shortBottomCenter 的等价实现（底部居中短 toast，与 Fragment.toastBottom 同款）。 */
    static void toastShortBottom(Context context, String text) {
        if (context == null) return;
        com.fongmi.android.tv.utils.ToastUtil.show(context, text, android.widget.Toast.LENGTH_SHORT);
    }

    static void toastShortCenter(Context context, String text) {
        if (context == null) return;
        com.fongmi.android.tv.utils.ToastUtil.show(context, text, android.widget.Toast.LENGTH_SHORT);
    }

    static void toastLongCenter(Context context, String text) {
        if (context == null) return;
        com.fongmi.android.tv.utils.ToastUtil.show(context, text, android.widget.Toast.LENGTH_LONG);
    }
}
