package com.example.hikerview.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.example.hikerview.constants.JSONPreFilter;
import com.example.hikerview.model.PlayerPosHis;
import com.example.hikerview.model.ViewCollection;
import com.example.hikerview.model.ViewCollectionExtraData;
import com.example.hikerview.model.ViewHistory;
import com.example.hikerview.service.parser.HttpParser;
import com.example.hikerview.ui.browser.data.TabHistory;
import com.example.hikerview.ui.browser.util.CollectionUtil;
import com.example.hikerview.ui.browser.webview.MultiWindowManager;
import com.example.hikerview.ui.home.model.ArticleListRule;
import com.example.hikerview.ui.view.HorizontalWebView;
import com.example.hikerview.ui.webdlan.LocalServerParser;
import com.fongmi.android.tv.App;

import org.litepal.LitePal;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import kotlinx.coroutines.Job;
import timber.log.Timber;

/**
 * 照官 8.83 本体逐字转写（官源 smali_classes2/com/example/hikerview/utils/HeavyTaskUtil.smali，
 * ①团全量解锁 18 名＋私有 saveHisSync/savePlayerPos0/getTabHistory/getUrlForPosMemory/isCleared）。
 * 存储统一走①团 LitePal 层（官 model 落库），不与既有键值面并行。
 * 接法记名：①官 Application.getContext() 接本树 App.get()（任务令既定等价）；
 * ②官 StringUtils.equals（commons-lang3 未引）以 java.util.Objects.equals 落（入参皆
 * String、语义同）；③launch/cancel 走真 kotlinx Job（ThreadTool.executeNewTask 已改官
 * Job 返回型，协程 1.6.1）；④官外部合成 lambda 在本类以 Java lambda 直落其本体。
 */
public class HeavyTaskUtil {

    private static final String TAG = "HeavyTaskUtil";
    private static final String[] playPosFixWhiteList = {"m3u8.htv009.com", "127.0.0.1", ":11111/"};
    private static final ExecutorService threadPool = new ThreadPoolExecutor(0, 1, 3, TimeUnit.SECONDS, new LinkedBlockingQueue());

    public static void postTaskToQueue(Runnable runnable) {
        threadPool.execute(runnable);
    }

    public static void executeNewTask(Runnable runnable) {
        ThreadTool.INSTANCE.executeNewTask(runnable);
    }

    public static Job launch(Runnable runnable) {
        return ThreadTool.INSTANCE.executeNewTask(runnable);
    }

    public static void cancel(Job job) {
        if (job != null) {
            try {
                if (!job.isCancelled()) {
                    job.cancel(new CancellationException());
                }
            } catch (Throwable th) {
                Timber.e(th);
            }
        }
    }

    // ---- 播放进度（PlayerPosHis） ----

    public static void saveNowPlayerPos(Context context, String url, long pos) {
        String realUrl = LocalServerParser.getUrlForPos(context, url);
        saveNowPlayerPos(context, realUrl, (int) pos, true);
    }

    public static void saveNowPlayerPos(Context context, String url, long pos, boolean force) {
        String realUrl = LocalServerParser.getUrlForPos(context, url);
        saveNowPlayerPos(context, realUrl, (int) pos, force);
    }

    public static void saveNowPlayerPos(Context context, String url, int pos, boolean force) {
        if (StringUtil.isEmpty(url)) {
            return;
        }
        String filtered = HttpParser.getRealUrlFilterHeaders(url);
        String localUrl = LocalServerParser.getUrlForPos(context, filtered);
        String memoryUrl = getUrlForPosMemory(localUrl);
        savePlayerPos0(context, memoryUrl, pos, force);
    }

    private static void savePlayerPos0(Context context, String url, int pos, boolean force) {
        if (pos <= 0) {
            return;
        }
        postTaskToQueue(() -> lambda$savePlayerPos0$0(url, pos, force));
    }

    private static void lambda$savePlayerPos0$0(String url, int pos, boolean force) {
        try {
            PlayerPosHis his = LitePal.where("playUrl = ?", url).findFirst(PlayerPosHis.class);
            if (his != null) {
                if (pos > his.getPos() || force) {
                    his.setPos(pos);
                    his.save();
                }
            } else {
                int count = 0;
                try {
                    count = LitePal.count(PlayerPosHis.class);
                } catch (Exception e) {
                    Timber.e(e);
                }
                if (count > 600) {
                    PlayerPosHis first = null;
                    try {
                        first = LitePal.order("id").findFirst(PlayerPosHis.class);
                    } catch (Exception e) {
                        Timber.e(e);
                    }
                    if (first != null) {
                        Timber.d("saveNowPlayerPos: delete=>%s", first.getPlayUrl());
                        first.delete();
                    }
                }
                PlayerPosHis newHis = new PlayerPosHis();
                newHis.setPlayUrl(url);
                newHis.setPos(pos);
                newHis.save();
            }
        } catch (Exception e) {
            Log.e(TAG, "saveNowPlayerPos: " + e.getMessage(), e);
        }
    }

    public static int getPlayerPos(Context context, String url) {
        try {
            if (StringUtil.isEmpty(url)) {
                return 0;
            }
            String filtered = HttpParser.getRealUrlFilterHeaders(url);
            String localUrl = LocalServerParser.getUrlForPos(context, filtered);
            String memoryUrl = getUrlForPosMemory(localUrl);
            PlayerPosHis his = LitePal.where("playUrl = ?", memoryUrl).findFirst(PlayerPosHis.class);
            if (his != null) {
                return his.getPos();
            }
        } catch (Exception e) {
            Log.e(TAG, "getPlayerPos: " + e.getMessage(), e);
        }
        return 0;
    }

    public static void deletePlayerPos(Context context, String url) {
        try {
            if (StringUtil.isEmpty(url)) {
                return;
            }
            String filtered = HttpParser.getRealUrlFilterHeaders(url);
            String localUrl = LocalServerParser.getUrlForPos(context, filtered);
            String memoryUrl = getUrlForPosMemory(localUrl);
            LitePal.deleteAll(PlayerPosHis.class, "playUrl = ?", memoryUrl);
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    private static String getUrlForPosMemory(String url) {
        if (StringUtil.isEmpty(url)) {
            return url;
        }
        if (url.contains("memoryPosition=full") || url.contains("hiker://") || !url.startsWith("http")
                || url.contains(".php") || url.contains(".html") || url.contains(".aspx") || url.contains("/download.")) {
            return url;
        }
        for (String white : playPosFixWhiteList) {
            if (url.contains(white)) {
                return url;
            }
        }
        String[] split = url.split("\\?");
        if (split.length < 2) {
            return url;
        }
        String base = split[0];
        String[] schemeSplit = base.split("://");
        if (schemeSplit.length > 1) {
            String rest = StringUtil.arrayToString(schemeSplit, 1, "://");
            if (rest.endsWith("/")) {
                return url;
            }
            String[] segments = rest.split("/");
            String last = segments[segments.length - 1];
            if (!last.contains(".") || last.contains("index.m3u8")) {
                return url;
            }
        }
        return base;
    }

    // ---- 历史/收藏查询 ----

    public static ViewHistory findHistory(String url) {
        try {
            List<ViewHistory> list = LitePal.where("url = ?", url).limit(1).find(ViewHistory.class);
            if (!CollectionUtil.isEmpty(list)) {
                return list.get(0);
            }
        } catch (Exception e) {
            Timber.e(e);
        }
        return null;
    }

    public static ViewHistory findHistory(String title, String url) {
        if (title != null && url != null) {
            try {
                List<ViewHistory> list = LitePal.where("url = ? and title = ?", url, title).limit(1).find(ViewHistory.class);
                if (!CollectionUtil.isEmpty(list)) {
                    return list.get(0);
                }
            } catch (Exception e) {
                Timber.e(e);
            }
        }
        return null;
    }

    public static ViewCollection findCollection(String cUrl) {
        try {
            List<ViewCollection> list = LitePal.where("CUrl = ?", cUrl).limit(1).find(ViewCollection.class);
            if (!CollectionUtil.isEmpty(list)) {
                return list.get(0);
            }
        } catch (Exception e) {
            Timber.e(e);
        }
        return null;
    }

    public static ViewCollection findCollection(String mTitle, String cUrl) {
        if (mTitle != null && cUrl != null) {
            try {
                List<ViewCollection> list = LitePal.where("CUrl = ? and MTitle = ?", cUrl, mTitle).limit(1).find(ViewCollection.class);
                if (!CollectionUtil.isEmpty(list)) {
                    return list.get(0);
                }
            } catch (Exception e) {
                Timber.e(e);
            }
        }
        return null;
    }

    // ---- 跳过片头片尾（jump pos） ----

    public static ViewCollectionExtraData findJumpPos(Context context, String mTitle, String cUrl) {
        ViewCollection collection = findCollection(mTitle, cUrl);
        if (collection != null) {
            ViewCollectionExtraData data = ViewCollectionExtraData.fromJson(collection.getExtraData());
            if (data.getJumpStartDuration() >= 0 || data.getJumpEndDuration() > 0) {
                return data;
            }
        }
        ViewHistory history = findHistory(mTitle, cUrl);
        if (history != null) {
            ViewCollectionExtraData data = ViewCollectionExtraData.fromJson(history.getExtraData());
            if (data.getJumpStartDuration() >= 0 || data.getJumpEndDuration() > 0) {
                return data;
            }
        }
        ViewCollectionExtraData data = new ViewCollectionExtraData();
        if (StringUtil.isNotEmpty(mTitle) && StringUtil.isNotEmpty(cUrl)
                && !PreferenceMgr.getBoolean(context, "jumpsa", false)) {
            data.setJumpStartDuration(0);
            data.setJumpEndDuration(0);
            return data;
        }
        int start = PreferenceMgr.getInt(context, "jumpStartDuration", 0);
        int end = PreferenceMgr.getInt(context, "jumpEndDuration", 0);
        data.setJumpStartDuration(start);
        data.setJumpEndDuration(end);
        return data;
    }

    public static void updateJumpPos(String mTitle, String cUrl, int start, int end) {
        if (mTitle == null || cUrl == null) {
            PreferenceMgr.put(App.get(), "jumpStartDuration", Integer.valueOf(start));
            PreferenceMgr.put(App.get(), "jumpEndDuration", Integer.valueOf(end));
            return;
        }
        ViewCollection collection = findCollection(mTitle, cUrl);
        if (collection != null) {
            ViewCollectionExtraData data = ViewCollectionExtraData.fromJson(collection.getExtraData());
            data.setJumpStartDuration(start);
            data.setJumpEndDuration(end);
            collection.setExtraData(data.toJson());
            collection.save();
        }
        ViewHistory history = findHistory(mTitle, cUrl);
        if (history != null) {
            ViewCollectionExtraData data = ViewCollectionExtraData.fromJson(history.getExtraData());
            data.setJumpStartDuration(start);
            data.setJumpEndDuration(end);
            history.setExtraData(data.toJson());
            history.save();
        }
    }

    public static void clearJumpPos(String mTitle, String cUrl, int start, int end) {
        if (mTitle == null || cUrl == null) {
            PreferenceMgr.put(App.get(), "jumpStartDuration", Integer.valueOf(start));
            PreferenceMgr.put(App.get(), "jumpEndDuration", Integer.valueOf(end));
            return;
        }
        boolean cleared = false;
        ViewCollection collection = findCollection(mTitle, cUrl);
        if (collection != null) {
            ViewCollectionExtraData data = ViewCollectionExtraData.fromJson(collection.getExtraData());
            cleared = isCleared(start, end, data);
            data.setJumpStartDuration(start);
            data.setJumpEndDuration(end);
            collection.setExtraData(data.toJson());
            collection.save();
        }
        ViewHistory history = findHistory(mTitle, cUrl);
        if (history != null) {
            ViewCollectionExtraData data = ViewCollectionExtraData.fromJson(history.getExtraData());
            cleared = isCleared(start, end, data);
            data.setJumpStartDuration(start);
            data.setJumpEndDuration(end);
            history.setExtraData(data.toJson());
            history.save();
        }
        if (!cleared) {
            PreferenceMgr.put(App.get(), "jumpStartDuration", Integer.valueOf(start));
            PreferenceMgr.put(App.get(), "jumpEndDuration", Integer.valueOf(end));
        }
    }

    private static boolean isCleared(int start, int end, ViewCollectionExtraData data) {
        if (start <= 0 && data.getJumpStartDuration() > 0) {
            return true;
        }
        return end <= 0 && data.getJumpEndDuration() > 0;
    }

    // ---- 历史写入 ----

    public static void saveHistory(Activity activity, String type, String ruleBaseUrl, String url, String title) {
        if (url != null && url.startsWith("http")) {
            List<TabHistory> tabHistory = getTabHistory(activity);
            executeNewTask(() -> lambda$saveHistory$1(type, ruleBaseUrl, url, title, activity, tabHistory));
        }
    }

    private static void lambda$saveHistory$1(String type, String ruleBaseUrl, String url, String title, Activity activity, List<TabHistory> tabHistory) {
        try {
            saveHisSync(type, ruleBaseUrl, url, title, null, null, null);
        } catch (Exception e) {
            Timber.e(e);
        }
        PreferenceMgr.put(activity, "vip", "lastTab", JSON.toJSONString(tabHistory));
    }

    public static void saveHistory(String url, String title, ArticleListRule rule, Intent intent, String group, String picUrl) {
        executeNewTask(() -> lambda$saveHistory$2(rule, intent, url, title, group, picUrl));
    }

    private static void lambda$saveHistory$2(ArticleListRule rule, Intent intent, String url, String title, String group, String picUrl) {
        try {
            String params = buildRuleToParams(rule);
            boolean newWindow = intent.getBooleanExtra("newWindow", false);
            String windowId = intent.getStringExtra("windowId");
            saveHisSync("二级列表", StringUtil.getHomeUrl(url), url, title, params, group, picUrl, newWindow, windowId);
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    public static void saveHistory(String url, String title, String params, String group, String picUrl) {
        executeNewTask(() -> lambda$saveHistory$3(url, title, params, group, picUrl));
    }

    private static void lambda$saveHistory$3(String url, String title, String params, String group, String picUrl) {
        try {
            saveHisSync("二级列表", StringUtil.getHomeUrl(url), url, title, params, group, picUrl);
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    private static synchronized void saveHisSync(String type, String ruleBaseUrl, String url, String title, String params, String group, String picUrl) {
        saveHisSync(type, ruleBaseUrl, url, title, params, group, picUrl, false, null);
    }

    private static synchronized void saveHisSync(String type, String ruleBaseUrl, String url, String title, String params, String group, String picUrl, boolean newWindow, String windowId) {
        int historyCount = PreferenceMgr.getInt(App.get(), PreferenceMgr.SETTING_CONFIG, "historyCount", 300);
        if (historyCount <= 0) {
            return;
        }
        List<ViewHistory> list = null;
        try {
            if ("二级列表".equals(type)) {
                list = LitePal.where("url = ? and title = ? and type = ?", url, title, "二级列表").limit(1).find(ViewHistory.class);
            } else {
                list = LitePal.where("url = ? and type = ?", url, "网页浏览").limit(1).find(ViewHistory.class);
            }
        } catch (Exception e) {
            Timber.e(e);
            list = null;
        }
        if (!CollectionUtil.isEmpty(list)) {
            ViewHistory history = list.get(0);
            history.setTime(new Date());
            history.setTitle(title);
            history.setParams(params);
            history.setGroup(group);
            if ("二级列表".equals(type)) {
                ViewCollectionExtraData extra = ViewCollectionExtraData.fromJson(history.getExtraData());
                extra.setNewWindow(newWindow);
                extra.setWindowId(windowId);
                history.setExtraData(extra.toJson());
            }
            if (StringUtil.isNotEmpty(picUrl)) {
                history.setPicUrl(picUrl);
            }
            history.save();
            return;
        }
        int count = 0;
        try {
            count = LitePal.count(ViewHistory.class);
        } catch (Exception e) {
            Timber.e(e);
        }
        if (count >= historyCount) {
            ViewHistory first = null;
            try {
                first = LitePal.findFirst(ViewHistory.class);
            } catch (Exception e) {
                Timber.e(e);
            }
            if (first != null) {
                first.delete();
            }
        }
        ViewHistory history = new ViewHistory();
        history.setTime(new Date());
        history.setTitle(title);
        history.setUrl(url);
        history.setRuleBaseUrl(ruleBaseUrl);
        history.setType(type);
        history.setParams(params);
        history.setGroup(group);
        history.setPicUrl(picUrl);
        if (newWindow) {
            ViewCollectionExtraData extra = new ViewCollectionExtraData();
            extra.setWindowId(windowId);
            extra.setNewWindow(true);
            history.setExtraData(extra.toJson());
        }
        history.save();
    }

    public static void updateHistoryLastClick(String title, String url, String lastClick) {
        Log.d(TAG, "updateHistoryLastClick: " + lastClick);
        postTaskToQueue(() -> lambda$updateHistoryLastClick$4(title, url, lastClick));
    }

    private static void lambda$updateHistoryLastClick$4(String title, String url, String lastClick) {
        ViewHistory history = findHistory(title, url);
        if (history != null) {
            history.setLastClick(lastClick);
            history.setTime(new Date());
            history.save();
        }
    }

    public static void updateHistoryVideoUrl(String url, String videoUrl) {
        executeNewTask(() -> lambda$updateHistoryVideoUrl$5(url, videoUrl));
    }

    private static void lambda$updateHistoryVideoUrl$5(String url, String videoUrl) {
        List<ViewHistory> histories = null;
        try {
            histories = LitePal.where("url = ? and type = ?", url, "网页浏览").limit(1).find(ViewHistory.class);
        } catch (Exception e) {
            Timber.e(e);
            histories = null;
        }
        if (!CollectionUtil.isEmpty(histories)) {
            ViewHistory history = histories.get(0);
            history.setVideoUrl(videoUrl);
            history.setTime(new Date());
            history.save();
        }
        List<ViewCollection> collections = null;
        try {
            collections = LitePal.where("CUrl = ?", url).limit(1).find(ViewCollection.class);
        } catch (Exception e) {
            Timber.e(e);
        }
        if (!CollectionUtil.isEmpty(collections)) {
            ViewCollection collection = collections.get(0);
            collection.setVideoUrl(videoUrl);
            collection.setTime(new Date());
            collection.save();
        }
    }

    // ---- 页码记忆（PlayerPosHis 复用） ----

    public static int getMemoryPage(String url, String title) {
        PlayerPosHis his = LitePal.where("playUrl = ?", title + "@@" + url).findFirst(PlayerPosHis.class);
        if (his != null && his.getPos() > 0) {
            return his.getPos();
        }
        return 1;
    }

    public static void updateMemoryPage(String url, String title, int page) {
        if (page <= 0) {
            PlayerPosHis his = LitePal.where("playUrl = ?", title + "@@" + url).findFirst(PlayerPosHis.class);
            if (his != null) {
                his.delete();
            }
        } else {
            savePlayerPos0(App.get(), title + "@@" + url, page, true);
        }
    }

    // ---- 规则参数互转 ----

    public static String buildRuleToParams(ArticleListRule rule) {
        try {
            ArticleListRule clone = rule.clone();
            ArticleListRule local = LitePal.where("title = ?", clone.getTitle()).findFirst(ArticleListRule.class);
            if (local != null) {
                boolean equals = Objects.equals(clone.getFind_rule(), local.getFind_rule());
                try {
                    if (equals) {
                        clone.setFind_rule("localRule@" + clone.getTitle());
                    }
                    if (Objects.equals(clone.getDetail_find_rule(), local.getDetail_find_rule())) {
                        clone.setDetail_find_rule("localRule@" + clone.getTitle());
                    }
                    if (Objects.equals(clone.getSearchFind(), local.getSearchFind())) {
                        clone.setSearchFind("localRule@" + clone.getTitle());
                    }
                    if (Objects.equals(clone.getSdetail_find_rule(), local.getSdetail_find_rule())) {
                        clone.setSdetail_find_rule("localRule@" + clone.getTitle());
                    }
                    if (Objects.equals(clone.getPreRule(), local.getPreRule())) {
                        clone.setPreRule("localRule@" + clone.getTitle());
                    }
                    if (Objects.equals(clone.getProxy(), local.getProxy())) {
                        clone.setProxy("localRule@" + clone.getTitle());
                    }
                    if (Objects.equals(clone.getPages(), local.getPages())) {
                        clone.setPages("localRule@" + clone.getTitle());
                    }
                } catch (Exception e) {
                    Timber.e(e);
                    return JSON.toJSONString(rule, JSONPreFilter.getShareFilter());
                }
            }
            return JSON.toJSONString(clone, JSONPreFilter.getShareFilter());
        } catch (Exception e) {
            Timber.e(e);
            return JSON.toJSONString(rule, JSONPreFilter.getShareFilter());
        }
    }

    public static ArticleListRule revertParamsToRule(String params) {
        ArticleListRule rule = JSON.parseObject(params, ArticleListRule.class);
        try {
            ArticleListRule local = LitePal.where("title = ?", rule.getTitle()).findFirst(ArticleListRule.class);
            if (local != null) {
                if (rule.getFind_rule() != null && rule.getFind_rule().startsWith("localRule@")) {
                    rule.setFind_rule(local.getFind_rule());
                }
                if (rule.getDetail_find_rule() != null && rule.getDetail_find_rule().startsWith("localRule@")) {
                    rule.setDetail_find_rule(local.getDetail_find_rule());
                }
                if (rule.getSearchFind() != null && rule.getSearchFind().startsWith("localRule@")) {
                    rule.setSearchFind(local.getSearchFind());
                }
                if (rule.getSdetail_find_rule() != null && rule.getSdetail_find_rule().startsWith("localRule@")) {
                    rule.setSdetail_find_rule(local.getSdetail_find_rule());
                }
                if (rule.getPreRule() != null && rule.getPreRule().startsWith("localRule@")) {
                    rule.setPreRule(local.getPreRule());
                }
                if (rule.getProxy() != null && rule.getProxy().startsWith("localRule@")) {
                    rule.setProxy(local.getProxy());
                }
                if (rule.getPages() != null && rule.getPages().startsWith("localRule@")) {
                    rule.setPages(local.getPages());
                }
            }
        } catch (Exception e) {
            Timber.e(e);
        }
        return rule;
    }

    // ---- 标签页历史 ----

    public static void saveTabHistory(Activity activity) {
        List<TabHistory> tabHistory = getTabHistory(activity);
        executeNewTask(() -> lambda$saveTabHistory$6(activity, tabHistory));
    }

    private static void lambda$saveTabHistory$6(Activity activity, List<TabHistory> tabHistory) {
        PreferenceMgr.put(activity, "vip", "lastTab", JSON.toJSONString(tabHistory));
    }

    private static List<TabHistory> getTabHistory(Activity activity) {
        ArrayList<TabHistory> list = new ArrayList<>();
        List<HorizontalWebView> webViews = MultiWindowManager.instance(activity).getUsedWebViewList();
        if (CollectionUtil.isNotEmpty(webViews)) {
            int start = webViews.size() <= 50 ? 0 : webViews.size() - 50;
            for (int i = start; i < webViews.size(); i++) {
                HorizontalWebView webView = webViews.get(i);
                if (StringUtil.isEmpty(webView.getUrl())) {
                    list.add(new TabHistory("", webView.isUsed()));
                } else {
                    list.add(new TabHistory(webView.getUrl(), webView.isUsed()));
                }
            }
        }
        if (list.size() == 1 && StringUtil.isEmpty(list.get(0).getUrl())) {
            list.clear();
        }
        return list;
    }
}
