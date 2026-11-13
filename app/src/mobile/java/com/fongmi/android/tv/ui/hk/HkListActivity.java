package com.fongmi.android.tv.ui.hk;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.hk.HkCommonParser;
import com.fongmi.android.tv.api.hk.HkEpisodeMerger;
import com.fongmi.android.tv.api.hk.HkJsRuntime;
import com.fongmi.android.tv.api.hk.HkHttpParser;
import com.fongmi.android.tv.api.hk.HkItem;
import com.fongmi.android.tv.api.hk.HkLoading;
import com.fongmi.android.tv.api.hk.HkPageParser;
import com.fongmi.android.tv.api.hk.HkPlayData;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.api.hk.HkRuleManager;
import com.fongmi.android.tv.read.ReadingGate;
import com.fongmi.android.tv.read.ReadingRouter;
import com.fongmi.android.tv.read.ReadingSession;
import com.fongmi.android.tv.read.ReadingStore;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.utils.ExitContract;
import com.fongmi.android.tv.utils.download.DownloadManager;
import com.fongmi.android.tv.utils.XcFadeTransition;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import android.util.Base64;
import android.util.Log;

/**
 * 照 8.83 FilmListActivity 的宿主职责转写（星辰等价）：每开一级子列表/详情就新开一个本活动实例
 * （standard 启动、系统返回栈），规则经 putTempRule 等价通道传递（Intent "data" 小数据直传、
 * 大于 0x32000 字节走静态槽 + 缓存文件兜底，数据全丢时弹「页面数据被系统回收」退出）。
 * 本活动同时实现 Fragment 的 HostListener 与 PageOpener，并负责全套静态接线的一次性初始化：
 * 引擎桥（单例）、DetailBridge、两处 activityProvider、HkFuck.loadList()。
 * 原版宿主的收藏星标/页面背景图/双击回顶等纯装饰机能未搬（不影响列表机能，宿主视觉保持星辰海阔页浅色风格）。
 */
public class HkListActivity extends AppCompatActivity
        implements HkArticleFragment.HostListener, HkArticleFragment.PageOpener,
        HkArticleFragment.PageUrlOpener,
        HkDetailBridge.FileEditCallbackHost {
    private static final String TAG = "HkListActivity";

    private static final int TEMP_RULE_LIMIT = 0x32000;
    private static final String PREF_LAST_CLICK = "hk_last_click";

    /** 原版 Application.tempRuleData 等价：大数据规则 JSON 的进程内静态槽。 */
    private static volatile String tempRuleData;
    /** 静态槽/缓存文件里那份规则的指纹（putTempRule 写入、onCreate 回退消费时核对）：
     * 没有指纹或对不上就绝不消费——此前无条件回退会把上一个大规则的 body
     * 静默安到本次开页头上（标题对、内容全错的串页，1.3.11 花都影视实证）。 */
    private static volatile String tempRuleKey;
    /** 原版 FilmListActivity.tempParentData 等价：#autoPage# 子页的父页续页数据静态槽（消费后清空）。 */
    private static volatile HkArticleFragment.AutoPageData tempParentData;

    /**
     * 官 FilmListActivity.tempParentData 为 public static 字段，官
     * PageParser.goPageByPageUrl 在目标 url 含 #autoPage# 时直写 null 清槽
     * （防上一父页的续页数据串入本次开页）；本树字段私有，以此公开清槽口
     * 对位（2026-11-14 海阔缺口③团 PageParser.goPageByPageUrl 接缝）。
     */
    public static void clearTempParentData() {
        tempParentData = null;
    }
    private static final AtomicBoolean STATICS_READY = new AtomicBoolean(false);
    private static volatile WeakReference<Activity> currentRef;

    /** 环29 复核订正 B1：专用网页页标记（openX5PlayPage 开页时经 Intent 传入）。 */
    public static final String EXTRA_WEB_CARRIER_PAGE = "hkWebCarrierPage";

    private HkArticleFragment fragment;
    private HkRule rule;
    private String pageTitle;
    /** 本页是否为专用网页页（goWeb 承载登记的唯一合法宿主，见 HkWebCarrier）。 */
    private boolean webCarrierPage;

    /** 本页是否为专用网页页（openX5PlayPage 新开的网页页）。 */
    public boolean isWebCarrierPage() {
        return webCarrierPage;
    }
    /** 照原版 FilmListActivity.isOnPause：onResume 置假、onPause 置真，供 back() 订阅守卫。 */
    private boolean isOnPause;
    /** 顶部小程序切换 tabs 的规则集（自旧内容页迁入，行为不变）。 */
    private final java.util.List<HkRule> miniappRules = new java.util.ArrayList<>();
    private boolean childHasWebView;
    /** editFile:// 支登记的保存后回调 JS（原版宿主同名字段族），编辑页以 0x4e16 回流后逐个求值。 */
    private final java.util.List<String> fileEditCallbackJs = new java.util.ArrayList<>();

    /** 规则 showLoading/hideLoading 弹窗（8.83 LoadingEvent 等价）：此前只有旧内页
     * HkPageActivity 注册过监听，而打开规则早已改走本页，监听从未注册、弹窗从未出现
     * （18AV「正在获取域名」在星辰里看不到就是这个原因）。 */
    private android.app.Dialog hkLoadingDialog;
    private TextView hkLoadingText;
    private final Handler hkLoadingWatchdog = new Handler(Looper.getMainLooper());
    private final Runnable hkLoadingWatchdogRun = this::hideHkLoading;
    private final HkLoading.Listener hkLoadingListener = new HkLoading.Listener() {
        @Override
        public void onShowLoading(String ruleTitle, String text) {
            if (isFinishing()) return;
            if (rule == null || ruleTitle == null || !ruleTitle.equals(rule.getTitle())) return;
            showHkLoading(text);
        }

        @Override
        public void onHideLoading(String ruleTitle) {
            if (rule == null || ruleTitle == null || !ruleTitle.equals(rule.getTitle())) return;
            hideHkLoading();
        }
    };

    /** 与旧内页同款：深色小盒（#212121/圆角16）+ 菊花 + 文案，60 秒看门狗兜底关。 */
    private void showHkLoading(String text) {
        try {
            if (hkLoadingDialog == null) {
                LinearLayout root = new LinearLayout(this);
                root.setOrientation(LinearLayout.VERTICAL);
                root.setGravity(Gravity.CENTER);
                LinearLayout box = new LinearLayout(this);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setGravity(Gravity.CENTER);
                int pad = dp(22);
                box.setPadding(pad, pad, pad, pad);
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.RECTANGLE);
                bg.setColor(0xFF212121);
                bg.setCornerRadius(dp(16));
                box.setBackground(bg);

                ProgressBar bar = new ProgressBar(this);
                LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(28), dp(28));
                barLp.gravity = Gravity.CENTER_HORIZONTAL;
                box.addView(bar, barLp);

                hkLoadingText = new TextView(this);
                hkLoadingText.setTextColor(0xFFEEEEEE);
                hkLoadingText.setTextSize(14);
                hkLoadingText.setGravity(Gravity.CENTER);
                hkLoadingText.setMaxLines(1);
                hkLoadingText.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tvLp.gravity = Gravity.CENTER_HORIZONTAL;
                tvLp.topMargin = dp(8);
                box.addView(hkLoadingText, tvLp);

                root.addView(box, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                hkLoadingDialog = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
                hkLoadingDialog.setContentView(root);
                hkLoadingDialog.setCancelable(true);
                hkLoadingDialog.setCanceledOnTouchOutside(true);
                if (hkLoadingDialog.getWindow() != null) {
                    hkLoadingDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                    hkLoadingDialog.getWindow().setDimAmount(0.5f);
                }
            }
            if (TextUtils.isEmpty(text)) {
                hkLoadingText.setVisibility(View.GONE);
            } else {
                hkLoadingText.setVisibility(View.VISIBLE);
                hkLoadingText.setText(text);
            }
            if (!hkLoadingDialog.isShowing() && !isFinishing()) hkLoadingDialog.show();
            hkLoadingWatchdog.removeCallbacks(hkLoadingWatchdogRun);
            hkLoadingWatchdog.postDelayed(hkLoadingWatchdogRun, 60000);
        } catch (Throwable ignored) {
        }
    }

    private void hideHkLoading() {
        try {
            hkLoadingWatchdog.removeCallbacks(hkLoadingWatchdogRun);
            if (hkLoadingDialog != null && hkLoadingDialog.isShowing()) hkLoadingDialog.dismiss();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void addFileEditCallbackJs(String js) {
        if (js != null) fileEditCallbackJs.add(js);
    }

    /** 当前列表页活动（供两处 activityProvider 与 HkFuck 等无上下文接缝取用）。 */
    public static Activity currentActivity() {
        WeakReference<Activity> ref = currentRef;
        Activity activity = ref == null ? null : ref.get();
        return activity != null && !activity.isFinishing() ? activity : null;
    }

    /**
     * 照原版 FilmListActivity.putTempRule(Intent, String)：空串直接放 "data" extra；
     * 字节长大于 0x32000 时放静态槽并写缓存文件，Intent 不带数据；否则放 "data" extra。
     */
    public static void putTempRule(Intent intent, String ruleJson) {
        if (TextUtils.isEmpty(ruleJson)) {
            intent.putExtra("data", ruleJson);
            return;
        }
        // 每次开页都给 Intent 带上规则指纹：走静态槽/缓存文件的大规则靠它认领，
        // 别的开页（没经 putTempRule 的）没有指纹，永远认领不到残留（见 tempRuleKey）。
        String key = md5Hex(ruleJson);
        intent.putExtra("ruleKey", key);
        int length = ruleJson.getBytes(StandardCharsets.UTF_8).length;
        Log.d(TAG, String.format("变量传递, length: %s", length));
        if (length > TEMP_RULE_LIMIT) {
            tempRuleData = ruleJson;
            tempRuleKey = key;
            writeTempRuleCache(key, ruleJson);
        } else {
            intent.putExtra("data", ruleJson);
        }
    }

    private static String md5Hex(String text) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("MD5")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(text.hashCode());
        }
    }

    /**
     * 规则第一页入口（主页型宿主，listHost=false）：供规则列表/搜索等开页方调用。
     * 开页契约照 8.83 原版 RuleWindowActivity.start(activity, rule, false) 的
     * ruleOpenBy=2「新页面打开」支：无任何任务旗、同任务压栈——点规则就是在
     * 当前任务里新开一页，永不开新任务/新窗口，最近任务恒只有星辰主卡一张。
     * fix5 搬的 ruleOpenBy=1 窗口形态（NEW_TASK｜MULTIPLE_TASK｜NEW_DOCUMENT
     * 三旗＋windowId 登记册复用＋关闭侧 finishAndRemoveTask 摘卡）已整件退役：
     * 那只是原版的一个可选打开模式（缺省 0 主页内嵌、2 新页面），fix5 把它当
     * 结构本体才有「最近任务出现两张卡」与一串返回别扭（账
     * hk-task-single-20261103 订正节：用户实证原版任何状态都只有一张卡）。
     * 落点靠同任务栈本身：返回逐层弹栈回小程序页；再点同规则恒新开一页、
     * 不提旧页。调用方全为 Activity（列表页/搜索页/本页），裸 startActivity
     * 即落调用方所在任务。
     */
    public static void startRule(Context context, HkRule rule, String title) {
        startRule(context, rule, title, false);
    }

    /**
     * 环29 复核订正 B1：carrierPage=true 时本页即专用网页页（官 WebViewActivity
     * 等价物），其 WebView 持有方才许向 HkWebCarrier 登记；普通规则页恒 false。
     */
    public static void startRule(Context context, HkRule rule, String title, boolean carrierPage) {
        Intent intent = new Intent(context, HkListActivity.class);
        putTempRule(intent, new Gson().toJson(rule));
        String pageTitle = !TextUtils.isEmpty(title) ? title : rule.getTitle();
        intent.putExtra("title", pageTitle);
        intent.putExtra("listHost", false);
        if (carrierPage) intent.putExtra(EXTRA_WEB_CARRIER_PAGE, true);
        context.startActivity(intent);
    }

    /** pics 会话的页面地址判定：只认 http(s) 全地址（与 ReadingGate.isHttpPage 同口径）。 */
    private static boolean isHttpPage(String u) {
        return u != null && (u.startsWith("http://") || u.startsWith("https://"));
    }

    private static String hostOf(String u) {
        try {
            String h = android.net.Uri.parse(u == null ? "" : u).getHost();
            return h == null ? "" : h;
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** 页面地址归一化（章节匹配用）：剥 #标记、@rule=/@lazyRule= 尾巴与 # 片段后比对。 */
    private static String pageKeyOf(String u) {
        if (u == null) return "";
        String s = HkUrlDetector.clearTag(u);
        int at = s.indexOf("@rule=");
        if (at >= 0) s = s.substring(0, at);
        int lz = s.indexOf("@lazyRule=");
        if (lz >= 0) s = s.substring(0, lz);
        int h = s.indexOf('#');
        if (h >= 0) s = s.substring(0, h);
        return s.trim();
    }

    /** 全套静态接线一次性初始化（LitePal 存储层 + 引擎单例桥 + DetailBridge + 活动供给 + Fuck 名单）。 */
    /** 桥与静态接线（各海阔面进入时统一调用，故 public）。 */
    public static void ensureStatics() {
        if (!STATICS_READY.compareAndSet(false, true)) return;
        Context app = App.get();
        // 海阔存储层（LitePal）初始化收口在此（2026-11-14 口径）：官在
        // Application 初始化是因为官整包就是海阔；咱家海阔是模块，初始化不进
        // App 启动链，只在任一海阔面进入、经本一次性接线点时完成（CAS 保证
        // 只跑一次），且排在桥与引擎装配之前——模块内一切碰库代码都在接线
        // 之后。配置走 assets/litepal.xml（名单只列树内真实存在的类）。
        org.litepal.LitePal.initialize(app);
        org.litepal.LitePal.getDatabase().disableWriteAheadLogging();
        HkEngineBridge engineBridge = new HkEngineBridge(app);
        HkArticleListModel.setEngineBridge(engineBridge);
        HkCommonParser.setJsEval((rule, js, input) -> {
            HkRule effective = rule != null ? rule : HkEngineManager.currentRule();
            return effective != null ? engineBridge.evalWithRule(effective, js, input) : input;
        });
        HkArticleFragment.setDetailBridge(new HkDetailBridge());
        HkEngineBridge.activityProvider = HkListActivity::currentActivity;
        HkDetailBridge.activityProvider = HkListActivity::currentActivity;
        // DetailBridge 宿主钩子：文件编辑器（editFile:// 的 0x4e16 回流在本类闭环）、
        // 播放交接（F：PlayerChooser → VideoActivity.startHkPlay）、下载对话框/快速下载（G）。
        HkDetailBridge.hooks = new HkDetailBridge.HostHooks() {
            @Override
            public void openFileEditor(Activity activity, String path) {
                if (activity instanceof HkListActivity) {
                    ((HkListActivity) activity).openFileEditor(path);
                }
            }

            @Override
            public void startPlayerSingle(Activity activity, String title, String url) {
                if (activity == null || TextUtils.isEmpty(url)) return;
                String playUrl = url;
                // x5Play 分类（定根账 hk-sniff-route-miswire-20261113）：官家
                // x5Play 是播放器地址（PlayerChooser 强制内置播放器），壳里
                // 两形——余量是播放数据 JSON（"{…"，包装型规则 lazy 先解析
                // 成直链再打包的标准回址）时整串交下面 startHkPlay，播放器
                // 入口（HkRouter.play）剥壳解载荷直接播，绝不当网页打开
                // （126 真机：影视合集点集被送进空白承载页、全程没起播）；
                // 余量是网页地址时才走下面电台问询＋承载页顶替官家播放器
                // 内网页模式（他亲定 x5 走系统 WebView）。
                if (url.startsWith("x5Play://")
                        && !url.substring("x5Play://".length()).trim().startsWith("{")) {
                    String target = url.substring("x5Play://".length());
                    // 电台问询前置（定根账候选三）：电台规则的 x5Play 地址先问
                    // 路由器，不先开网页。认领信号两路（claimSignal）：规则名
                    // 命中剥壳后走下面的 startHkPlay（内有 routeHk）；页/条目
                    // 标题命中（规则名不含电台字时，如「一个APP」里的电台页）
                    // 由本处直问 routeHkWithTitles。都没中或直问未成，照旧开
                    // x5 承载页，一行不变。页标题取宿主活动标题，取不到为空
                    // 串、不硬凑。
                    String hTitle = hostRuleTitle(activity);
                    CharSequence at = activity.getTitle();
                    String pTitle = at == null ? "" : at.toString();
                    int sig = com.fongmi.android.tv.ui.HkRadioRouter.claimSignal(hTitle, pTitle, title);
                    if (sig == 0) {
                        openX5PlayPage(activity, title, target);
                        return;
                    }
                    if (sig > 1) {
                        try {
                            JSONArray one = new JSONArray();
                            JSONObject o = new JSONObject();
                            o.put("name", title == null ? "" : title);
                            o.put("url", target);
                            o.put("pic", "");
                            one.put(o);
                            // sig=4 是「播放音频」钮：专辑名用页标题（帖子名），
                            // 钮面文字只作章节名，不拿它当专辑名。
                            String albumName = sig == 4 && !pTitle.isEmpty() ? pTitle
                                    : (title == null ? "" : title);
                            if (com.fongmi.android.tv.ui.HkRadioRouter.routeHkWithTitles(activity,
                                    hTitle, pTitle, title, "默认", target, title, albumName, "",
                                    one.toString(), 0)) {
                                return;
                            }
                        } catch (Throwable ignored) {
                        }
                        openX5PlayPage(activity, title, target);
                        return;
                    }
                    com.github.catvod.crawler.SpiderDebug.log("hk", "x5 radio-ask single rule=%s", hTitle);
                    playUrl = target;
                }
                JSONArray arr = new JSONArray();
                try {
                    JSONObject o = new JSONObject();
                    o.put("name", title == null ? "" : title);
                    o.put("url", playUrl);
                    o.put("pic", "");
                    arr.put(o);
                } catch (Exception ignored) {
                }
                VideoActivity.startHkPlay(activity, hostRuleTitle(activity), "默认",
                        playUrl, title, title, "", arr.toString(), 0);
            }

            @Override
            public void startPlayerChapters(Activity activity, List<HkArticleFragment.HkChapter> chapters,
                                            String pageUrl, String mTitle, Bundle extraData) {
                if (activity == null || chapters == null || chapters.isEmpty()) return;
                String film = extraData == null ? null : extraData.getString("film");
                String ruleTitle = "";
                String ruleJson = extraData == null ? null : extraData.getString("rule");
                if (!TextUtils.isEmpty(ruleJson)) {
                    try {
                        HkRule r = new Gson().fromJson(ruleJson, HkRule.class);
                        if (r != null && r.getTitle() != null) ruleTitle = r.getTitle();
                    } catch (Exception ignored) {
                    }
                }
                if (TextUtils.isEmpty(ruleTitle)) {
                    ruleTitle = !TextUtils.isEmpty(film) ? film : hostRuleTitle(activity);
                }
                // 阅读模块阶段二：小说规则的章节表改道小说阅读器，不进 VideoActivity。
                // 视频规则原样走下（type 不是"小说"）。
                if (ReadingGate.isHkNovelRule(ruleTitle)) {
                    try {
                        ReadingSession ns = new ReadingSession();
                        ns.kind = ReadingSession.Kind.NOVEL;
                        ns.sourceName = ruleTitle;
                        ns.ruleTitle = ruleTitle;
                        ns.bookTitle = !TextUtils.isEmpty(mTitle) && !mTitle.equals(ruleTitle) ? mTitle : "";
                        for (HkArticleFragment.HkChapter ch : chapters) {
                            ReadingSession.Chapter rc = new ReadingSession.Chapter(
                                    !TextUtils.isEmpty(ch.title) ? ch.title
                                            : (ch.memoryTitle == null ? "" : ch.memoryTitle),
                                    playUrlForChapter(ch));
                            rc.pic = ch.pic == null ? "" : ch.pic;
                            ns.chapters.add(rc);
                        }
                        int nidx = 0;
                        for (int i = 0; i < chapters.size(); i++) {
                            if (chapters.get(i).use) {
                                nidx = i;
                                break;
                            }
                        }
                        ns.chapterIndex = nidx;
                        ReadingSession.Chapter cur = ns.currentChapter();
                        ns.chapterUrl = cur.url;
                        if (TextUtils.isEmpty(ns.bookTitle)) ns.bookTitle = cur.name;
                        // 有阅读进度则从上次章节/位置继续
                        ReadingStore.Record nprog = ReadingStore.find("NOVEL", ruleTitle, ns.bookTitle);
                        if (nprog != null && nprog.chapterIndex >= 0 && nprog.chapterIndex < ns.chapters.size()) {
                            ns.chapterIndex = nprog.chapterIndex;
                            ns.chapterUrl = ns.currentChapter().url;
                        }
                        ReadingRouter.openReading(activity, ns);
                    } catch (Throwable t) {
                        android.util.Log.e(TAG, "novel divert failed", t);
                    }
                    return;
                }
                int selIdx = 0;
                for (int i = 0; i < chapters.size(); i++) {
                    if (chapters.get(i).use) {
                        selIdx = i;
                        break;
                    }
                }
                HkArticleFragment.HkChapter sel = chapters.get(selIdx);
                String selUrl = playUrlForChapter(sel);
                String selTitle = !TextUtils.isEmpty(sel.memoryTitle) ? sel.memoryTitle
                        : (sel.title == null ? "" : sel.title);
                // 电台问询前置（定根账候选三）：认领信号两路（claimSignal，
                // 页标题取交接 bundle 的 film、条目标题取选中集名）。规则名
                // 命中（sig=1）：x5Play 地址不先开网页，剥壳后继续走下面的
                // startHkPlay 问路由器（整表地址在 rawUrls 组装处同步剥壳）。
                // 页/条目标题命中（sig=2/3，规则名不含电台字时）：由本处组
                // 扁平载荷直问 routeHkWithTitles。都没中或直问未成，照旧分流
                // （x5Play 开承载页、其余走 startHkPlay 原链），一行不变。
                int sig = com.fongmi.android.tv.ui.HkRadioRouter.claimSignal(ruleTitle, film, selTitle);
                boolean radioName = sig == 1;
                if (sig > 1) {
                    com.github.catvod.crawler.SpiderDebug.log("hk", "x5 radio-ask-title chapters rule=%s sig=%d", ruleTitle, sig);
                    String stripSel = selUrl.startsWith("x5Play://")
                            ? selUrl.substring("x5Play://".length()) : selUrl;
                    String albumName = !TextUtils.isEmpty(mTitle) && !mTitle.equals(ruleTitle) ? mTitle : selTitle;
                    try {
                        JSONArray arr = new JSONArray();
                        for (HkArticleFragment.HkChapter ch : chapters) {
                            String u = playUrlForChapter(ch);
                            if (u.startsWith("x5Play://")) u = u.substring("x5Play://".length());
                            JSONObject o = new JSONObject();
                            o.put("name", !TextUtils.isEmpty(ch.title) ? ch.title
                                    : (ch.memoryTitle == null ? "" : ch.memoryTitle));
                            o.put("url", u);
                            o.put("pic", ch.pic == null ? "" : ch.pic);
                            arr.put(o);
                        }
                        if (com.fongmi.android.tv.ui.HkRadioRouter.routeHkWithTitles(activity,
                                ruleTitle, film, selTitle, "默认", stripSel, selTitle,
                                albumName, sel.pic == null ? "" : sel.pic, arr.toString(), selIdx)) {
                            return;
                        }
                    } catch (Throwable t) {
                        com.github.catvod.crawler.SpiderDebug.log("hk",
                                "x5 radio-ask-title failed rule=%s err=%s", ruleTitle, t.toString());
                    }
                    // 直问未成：落回原分流（下面的 x5 网页支/startHkPlay 原链）
                }
                // x5Play 分类同 startPlayerSingle（定根账 hk-sniff-route-miswire-20261113）：
                // 余量是播放数据 JSON 的不分流承载页，照常走下方交接（播放器入口剥壳解载荷）
                if (selUrl.startsWith("x5Play://") && !radioName
                        && !selUrl.substring("x5Play://".length()).trim().startsWith("{")) {
                    openX5PlayPage(activity, sel.memoryTitle, selUrl.substring("x5Play://".length()));
                    return;
                }
                if (radioName) {
                    com.github.catvod.crawler.SpiderDebug.log("hk", "x5 radio-ask chapters rule=%s", ruleTitle);
                    if (selUrl.startsWith("x5Play://")) selUrl = selUrl.substring("x5Play://".length());
                }
                String selName = !TextUtils.isEmpty(sel.memoryTitle) ? sel.memoryTitle
                        : (sel.title == null ? "" : sel.title);
                // 播放器标题用页面标题：子列表页的活动标题就是被点视频名（dealRule 经 req.title 传入），
                // 原版 PlayerChooser 用的正是这个 MTitle；一级列表页活动标题与规则名相同、不可用，回退选中集标题
                String name = !TextUtils.isEmpty(mTitle) && !mTitle.equals(ruleTitle) ? mTitle : selName;
                // 同片多址归并（治本通用层，与详情桥链共用 HkEpisodeMerger）：规则把同一
                // 视频的一组候选节点逐条报成独立条目（本体同一播放页、仅差节点参数）时，
                // 交接前折成一集、组内地址合成多线路播放数据，走下面既有的多线路展开。
                // 判定窄口径、拿不准不并；不并时下面一切原样。
                java.util.List<String> rawUrls = new java.util.ArrayList<>();
                java.util.List<String> judgeUrls = new java.util.ArrayList<>();
                java.util.List<String> chapterTitles = new java.util.ArrayList<>();
                for (HkArticleFragment.HkChapter ch : chapters) {
                    String raw = playUrlForChapter(ch);
                    // 电台认领时整表同步剥 x5Play 壳，免得专辑章节带着伪协议地址进听书页
                    if (radioName && raw.startsWith("x5Play://")) raw = raw.substring("x5Play://".length());
                    rawUrls.add(raw);
                    // 判定输入归一化（法医定案，hk-episode-1418-forensics）：判定件
                    // 只收原始形态地址，被点章的现行地址已被懒解析换成纯地址、与
                    // 兄弟章的原始包装地址不同桶，会把同组劈成「一集＋多线」倒置。
                    // judgeUrls 只喂判定，归并产出的播放地址与下游仍用 rawUrls 原样。
                    judgeUrls.add(mergeJudgeUrlForChapter(ch));
                    chapterTitles.add(ch.memoryTitle == null ? "" : ch.memoryTitle);
                }
                HkEpisodeMerger.Plan mergePlan = HkEpisodeMerger.plan(judgeUrls, chapterTitles, selIdx);
                java.util.Map<Integer, HkPlayData> mergedPlayData = new java.util.HashMap<>();
                if (mergePlan.merge) {
                    int episodeCount = 0;
                    for (int v : mergePlan.episodeIndexOf) episodeCount = Math.max(episodeCount, v + 1);
                    boolean[] inGroup = new boolean[chapters.size()];
                    int[] repOf = new int[episodeCount];
                    java.util.Arrays.fill(repOf, -1);
                    for (HkEpisodeMerger.Group g : mergePlan.groups) {
                        int epIdx = mergePlan.episodeIndexOf[g.members[0]];
                        // 代表取线路序首位：被点地址已被判定件旋转到首位，它就是这一集
                        repOf[epIdx] = g.members[0];
                        for (int m : g.members) inGroup[m] = true;
                        HkPlayData pd = new HkPlayData();
                        java.util.List<String> pdUrls = new java.util.ArrayList<>();
                        for (int m : g.members) pdUrls.add(rawUrls.get(m));
                        pd.setUrls(pdUrls);
                        pd.setNames(new java.util.ArrayList<>(java.util.Arrays.asList(g.lineNames)));
                        mergedPlayData.put(epIdx, pd);
                    }
                    for (int i = 0; i < chapters.size(); i++) {
                        if (!inGroup[i] && repOf[mergePlan.episodeIndexOf[i]] < 0) {
                            repOf[mergePlan.episodeIndexOf[i]] = i;
                        }
                    }
                    java.util.List<HkArticleFragment.HkChapter> mergedChapters = new java.util.ArrayList<>();
                    java.util.List<String> mergedRawUrls = new java.util.ArrayList<>();
                    for (int j = 0; j < episodeCount; j++) {
                        mergedChapters.add(chapters.get(repOf[j]));
                        mergedRawUrls.add(rawUrls.get(repOf[j]));
                    }
                    selIdx = mergePlan.episodeIndexOf[selIdx];
                    chapters = mergedChapters;
                    rawUrls = mergedRawUrls;
                    try {
                        com.github.catvod.crawler.SpiderDebug.log("hk", "merge same-content chapters episodes=%d lines=%d",
                                episodeCount, mergePlan.lineNames == null ? 0 : mergePlan.lineNames.length);
                    } catch (Throwable ignored) {
                    }
                }
                // 多线路展开：章节地址是播放数据（urls/names/headers）时按 names 展开成线路，
                // 与 8.83 播放器内按集解析、线路序号对所有集统一的语义一致；普通集走原扁平载荷不变
                java.util.List<HkPlayData> playDatas = new java.util.ArrayList<>();
                for (int i = 0; i < chapters.size(); i++) {
                    HkPlayData merged = mergedPlayData.get(i);
                    playDatas.add(merged != null ? merged : playDataOf(rawUrls.get(i)));
                }
                java.util.List<String> lineNames = lineNamesOf(playDatas, selIdx);
                // C1（定根账 hiker-scheme-diag 问①）：lineNamesOf 只认 PlayData
                // JSON，页函数型详情（线路是详情函数的页内状态、集地址是懒函数
                // 串，如橘汁）恒落「默认」、连用户当前在哪条线都丢。这里从全表
                // 集地址的懒入参回收当前线名（回收法见 currentLineNameOf，全表
                // 一致才采信），只把这一条线的名字换真，不冒充多线；回收不到
                // 维持「默认」原样。
                if (lineNames.size() == 1 && "默认".equals(lineNames.get(0))) {
                    String curLine = currentLineNameOf(chapters, rawUrls);
                    if (!curLine.isEmpty()) {
                        lineNames = new java.util.ArrayList<>();
                        lineNames.add(curLine);
                        android.util.Log.i(TAG, "hk line name recovered from lazy input: " + curLine);
                    }
                }
                // —— G8 交接附带（enrichHandover 在详情页组 bundle 时塞入 extraData）——
                String detailPic = extraData == null ? "" : nz(extraData.getString("detailPic"));
                String detailContent = extraData == null ? "" : nz(extraData.getString("detailContent"));
                String detailRemarks = extraData == null ? "" : nz(extraData.getString("detailRemarks"));
                String detailActor = extraData == null ? "" : nz(extraData.getString("detailActor"));
                String detailDirector = extraData == null ? "" : nz(extraData.getString("detailDirector"));

                String payload;
                String firstLineUrl;
                String startLineName = lineNames.get(0);
                JSONObject rerunRoot = null;
                if (lineNames.size() <= 1) {
                    // —— G3 线组整组带过：单线（页函数型线路被压成一条线）且详情页
                    // 识别到成组线钮、当前线能对上其中一条时，按线组载荷交接：
                    // 当前线带真集置首（播放页只给首线标选中集、selIdx 以它为准），
                    // 其余线只带重跑入口，播放页切线时由 HkLineRerun 按该线重跑
                    // 详情函数取集。PlayData 多线型不进此分支，现行行为不变。
                    JSONArray groups = parseLineGroups(extraData);
                    int curPos = groups == null ? -1 : indexOfLineGroup(groups, startLineName);
                    if (curPos >= 0) {
                        String act = extraData == null ? "" : nz(extraData.getString("actTitle"));
                        rerunRoot = buildRerunFlagsPayload(chapters, rawUrls, startLineName, groups, curPos,
                                ruleJson, TextUtils.isEmpty(act) ? "" : act + "-");
                    }
                }
                if (rerunRoot != null) {
                    payload = rerunRoot.toString();
                    firstLineUrl = selUrl;
                } else if (lineNames.size() <= 1) {
                    JSONArray arr = new JSONArray();
                    for (int i = 0; i < chapters.size(); i++) {
                        try {
                            JSONObject o = new JSONObject();
                            HkArticleFragment.HkChapter ch = chapters.get(i);
                            o.put("name", !TextUtils.isEmpty(ch.title) ? ch.title
                                    : (ch.memoryTitle == null ? "" : ch.memoryTitle));
                            o.put("url", rawUrls.get(i));
                            o.put("pic", ch.pic == null ? "" : ch.pic);
                            arr.put(o);
                        } catch (Exception ignored) {
                        }
                    }
                    payload = arr.toString();
                    firstLineUrl = selUrl;
                } else {
                    JSONObject flagsRoot = buildFlagsPayload(chapters, rawUrls, playDatas, lineNames);
                    payload = flagsRoot == null ? "[]" : flagsRoot.toString();
                    String fromPayload = "";
                    if (flagsRoot != null) {
                        try {
                            fromPayload = flagsRoot.getJSONArray("flags").getJSONObject(0)
                                    .getJSONArray("episodes").getJSONObject(selIdx).optString("url", "");
                        } catch (Exception ignored) {
                        }
                    }
                    firstLineUrl = !TextUtils.isEmpty(fromPayload) ? fromPayload
                            : expandedUrl(playDatas.get(selIdx), rawUrls.get(selIdx), 0);
                }
                // G8：被点章无图时封面回采详情头图（交接带入的 detailPic 兜底）；
                // 简介/演职员/备注随交接带入播放页详情面（缺失为空串，不摆占位）。
                String handoverPic = !TextUtils.isEmpty(sel.pic) ? sel.pic : detailPic;
                VideoActivity.startHkPlay(activity, ruleTitle, startLineName, firstLineUrl, selName, name,
                        handoverPic, payload, selIdx, detailContent, detailActor, detailDirector, detailRemarks);
            }

            @Override
            public void showDownloadDialog(Activity activity, String title, String url, String film, java.util.Map<String, String> headers) {
                if (activity == null || TextUtils.isEmpty(url)) return;
                String name = !TextUtils.isEmpty(title) ? title : url;
                new HkDialogs(activity)
                        .setTitle("下载资源")
                        .setMessage(name + "\n" + url)
                        .setPositiveButton("下载", (d, w) -> {
                            // A11：照 C-01 移动端 VideoActivity onDownload 的 IAE 口径，加下载抛错时只提示、不闪退。
                            try {
                                DownloadManager.get().add(url, name, headers);
                                HkRouteBlocker.toastShortBottom(activity, "已加入下载：" + name);
                            } catch (IllegalArgumentException e) {
                                HkRouteBlocker.toastShortBottom(activity, e.getMessage());
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }

            @Override
            public void startFastDownload(Context context, String title, String url, String film) {
                if (TextUtils.isEmpty(url)) return;
                String name = !TextUtils.isEmpty(title) ? title : url;
                // A11：同上，照 C-01 口径包 IAE，抛错时只提示、不闪退、不报“已加入下载”。
                try {
                    DownloadManager.get().add(url, name);
                } catch (IllegalArgumentException e) {
                    HkRouteBlocker.toastShortBottom(context, e.getMessage());
                    return;
                }
                if (context != null) {
                    HkRouteBlocker.toastShortBottom(context, "已加入下载：" + name);
                }
            }

            /**
             * 阅读模块阶段二：文章页/搜索页的 pics:// 入口（此前是空实现，点了没反应）。
             * PictureListRequest（pics/chapters/nowPage/fromLastPage）直接转阅读会话，
             * 进漫画阅读器。章节表、当前页、从末页起读全部保留。
             */
            @Override
            public void openPictureList(Activity activity,
                                        HkArticleFragment.PictureListRequest request) {
                if (activity == null || request == null) return;
                try {
                    // 入口无图不许拦（fix4）：阅读器自带回退解析（会话图→章节串
                    // →router.play 重取→书源），入口一拦全灭——1.4.19 真机
                    // 「漫画详情页都进不去」的根。只强制留底，照常组会话进阅读器。
                    if (!com.fongmi.android.tv.api.hk.HkRouter.hasLoadablePic(request.pics)) {
                        com.github.catvod.crawler.SpiderDebug.forceLog("hk",
                                "pics empty at openPictureList (proceed) pics=%d reqUrl=%s",
                                request.pics == null ? 0 : request.pics.size(),
                                request.url == null ? "" : request.url);
                    }
                    ReadingSession s = new ReadingSession();
                    s.kind = ReadingSession.Kind.COMIC;
                    String ruleTitle = hostRuleTitle(activity);
                    if (!TextUtils.isEmpty(request.ruleJson)) {
                        try {
                            HkRule r = new Gson().fromJson(request.ruleJson, HkRule.class);
                            if (r != null && !TextUtils.isEmpty(r.getTitle())) ruleTitle = r.getTitle();
                        } catch (Exception ignored) {
                        }
                    }
                    s.sourceName = ruleTitle;
                    s.ruleTitle = ruleTitle;
                    s.bookTitle = !TextUtils.isEmpty(request.mTitle) ? request.mTitle
                            : (!TextUtils.isEmpty(request.title) ? request.title : "");
                    if (request.pics != null) s.images = new ArrayList<>(request.pics);
                    // req.url 口径：只认真实 http 页面地址（产出 pics 的那一页）。
                    // 改前无条件拿 req.url 当 pageUrl/Referer——生产方曾塞列表规则
                    // 地址，组装层据此算出假域、自动 Referer 恒不成立（漫小肆法医）；
                    // 不合格宁可留空走自动判定。referer 与 pageUrl 同值同口径。
                    String pageUrl = isHttpPage(request.url) ? request.url : "";
                    s.referer = pageUrl;
                    s.pageUrl = pageUrl;
                    // 本入口没有 play 解析，实际有的请求头只有本章页地址作 Referer：
                    // 喂进 playHeaders，让阅读器组图的整套头补缺对跨域防盗链图也有
                    // 数据可补（此前三入口 playHeaders 恒为空集，复核报告 §4）。
                    if (!TextUtils.isEmpty(s.pageUrl)) s.playHeaders.put("Referer", s.pageUrl);
                    int useIdx = -1;
                    if (request.chapters != null) {
                        for (HkArticleFragment.HkChapter ch : request.chapters) {
                            ReadingSession.Chapter c = new ReadingSession.Chapter(
                                    !TextUtils.isEmpty(ch.title) ? ch.title
                                            : (ch.memoryTitle == null ? "" : ch.memoryTitle),
                                    ch.url == null ? "" : ch.url);
                            c.pic = ch.pic == null ? "" : ch.pic;
                            s.chapters.add(c);
                            if (ch.use) useIdx = s.chapters.size() - 1;
                        }
                    }
                    // 当前章：生产方 use 标记优先（被点那一章）；没有标记时按归一化
                    // 页地址匹配章节表（剥标记后比对，旧口径拿原始串全等恒不命中）；
                    // 都匹配不上则单章模式（idx=0，会话图就是被点章的图）。
                    int idx = 0;
                    if (useIdx >= 0) {
                        idx = useIdx;
                    } else if (!pageUrl.isEmpty() && !s.chapters.isEmpty()) {
                        String key = pageKeyOf(pageUrl);
                        if (!key.isEmpty()) {
                            for (int i = 0; i < s.chapters.size(); i++) {
                                if (key.equals(pageKeyOf(s.chapters.get(i).url))) {
                                    idx = i;
                                    break;
                                }
                            }
                        }
                    }
                    s.chapterIndex = idx;
                    s.chapterUrl = request.url == null ? "" : request.url;
                    // 起始页：nowPage / 从末页起读（原版 PictureListActivity 入参语义）
                    int startPage = Math.max(0, request.nowPage);
                    if (request.fromLastPage && !s.images.isEmpty()) {
                        startPage = s.images.size() - 1;
                    }
                    ReadingStore.Record prog = ReadingStore.find("COMIC", ruleTitle, s.bookTitle);
                    if (prog != null && prog.position > 0) startPage = prog.position;
                    // 组会话打点闭环：出问题时凭这一行就能判 pics 是否为空、
                    // pageUrl 是否合格，不再靠下游零记录反推（漫小肆法医 §四缺口）。
                    com.github.catvod.crawler.SpiderDebug.forceLog("hk",
                            "openPictureList pics=%d firstHost=%s pageUrl=%s chapters=%d idx=%d",
                            s.images.size(),
                            s.images.isEmpty() ? "" : hostOf(s.images.get(0)),
                            s.pageUrl, s.chapters.size(), s.chapterIndex);
                    ReadingRouter.openReadingWithStartPage(activity, s, startPage);
                } catch (Throwable t) {
                    // 不许再只写 Log.e（调试日志里永远看不到）：强制留底。
                    com.github.catvod.crawler.SpiderDebug.forceLog("hk", t);
                }
            }

            // ---- 高-5 补绑（2026-10-23 全树重查）：以下 14 个钩子此前只有接口空默认，
            // 海阔里点磁力/种子/ed2k/看图/分享/口令导入全无反应。逐个接到星辰既有能力上，
            // 实现都在本类静态助手里，不另造平行实现。 ----

            @Override
            public boolean checkText(Activity activity, String text) {
                return importRuleText(activity, text);
            }

            @Override
            public boolean checkAutoText(Context context, String text) {
                Activity activity = context instanceof Activity ? (Activity) context : currentActivity();
                return importRuleText(activity, text);
            }

            @Override
            public void shareFile(Context context, String absolutePath) {
                shareLocalFile(context, absolutePath);
            }

            @Override
            public void checkDownloadedFile(File file) {
                checkDownloadedRuleFile(file);
            }

            @Override
            public void showImageViewer(java.util.List<String> urls, int selectPos,
                                        java.util.Map<Integer, Integer> posMap,
                                        android.widget.ImageView srcView) {
                // posMap 是原版图集页码映射，图集本身走 openPictureList 的阅读器，普通看图不消费；
                // srcView 只用来取宿主页面（原版的小图→大图共享元素动画不搬）。
                Activity activity = srcView != null && srcView.getContext() instanceof Activity
                        ? (Activity) srcView.getContext() : currentActivity();
                if (activity == null || urls == null || urls.isEmpty()) return;
                showImageDialog(activity, urls, selectPos);
            }

            @Override
            public void showSingleImage(String url, android.widget.ImageView srcView) {
                Activity activity = srcView != null && srcView.getContext() instanceof Activity
                        ? (Activity) srcView.getContext() : currentActivity();
                if (activity == null || TextUtils.isEmpty(url)) return;
                java.util.List<String> urls = new ArrayList<>();
                urls.add(url);
                showImageDialog(activity, urls, 0);
            }

            @Override
            public void startDownloadMagnet(Context context, String url) {
                playThunderLink(context, url, null);
            }

            @Override
            public void startParseTorrent(Context context, String url) {
                playThunderLink(context, url, null);
            }

            @Override
            public void startParseFTPOrEd2k(Context context, String url,
                                            java.util.function.Consumer<String> consumer) {
                // 原版 consumer 是把迅雷解析出的本地地址回点进播放器；星辰的解析在播放器内
                // 由 Source.fetch 的 Thunder.Parser 完成，没有播放链外的独立解析面，
                // 故直达播放器、不回调用 consumer（回点只会把同一条 ed2k 再送回本分支）。
                playThunderLink(context, url, null);
            }

            @Override
            public void startDownloadMagnetList(Context context, String url,
                                                java.util.List<HkArticleFragment.HkChapter> chapters) {
                playThunderLink(context, url, chapters);
            }

            @Override
            public void startParseTorrentList(Context context, String url,
                                              java.util.List<HkArticleFragment.HkChapter> chapters) {
                playThunderLink(context, url, chapters);
            }

            @Override
            public void startParseFTPOrEd2kList(Context context, String url,
                                                java.util.List<HkArticleFragment.HkChapter> chapters) {
                playThunderLink(context, url, chapters);
            }

            @Override
            public void shareRule(HkRule rule, boolean moreWays) {
                shareHkRule(rule, moreWays);
            }

            @Override
            public void showPosDetail(Activity activity, HkItem item, HkRule rule, int position) {
                showItemDetail(activity, item, rule);
            }
        };
        HkFuck.loadList();
        // hiker:// 目标页路由钩子（C1）：搜索 + 目标页全量装配，搜索页/列表页/小程序页共用
        HkRouteBlocker.wireAllHooks();
    }

    /** 本页规则标题（播放交接取 siteKey 用）。 */
    public String currentRuleTitle() {
        return rule != null && rule.getTitle() != null ? rule.getTitle() : "";
    }

    /** 播放交接时按宿主活动取规则标题：列表页取本页规则，搜索页取当前发动机。 */
    static String hostRuleTitle(Activity activity) {
        if (activity instanceof HkListActivity) {
            return ((HkListActivity) activity).currentRuleTitle();
        }
        if (activity instanceof HkSearchActivity) {
            return ((HkSearchActivity) activity).currentEngineTitle();
        }
        return "";
    }

    // ------------------------------------------------------------------
    // 高-5 补绑的宿主能力（2026-10-23）：全部复用既有入口——导入走 HkRuleManager 中央分发、
    // 迅雷链走 VideoActivity.startHkPlay（播放器内 Source.fetch 的 Thunder.Parser 解析）、
    // 分享走 HkRuleManager.buildShareCommand + 系统分享、看图走 HkDialogs + ImgUtil。
    // ------------------------------------------------------------------

    /**
     * 口令/规则文本导入（原版 AutoImportHelper.checkText/checkAutoText 的星辰落点）：
     * 识别与导入全走 HkRuleManager 中央分发（云口令/￥home_rule￥包装/base64 壳/裸 JSON/JS 规则），
     * 真导入了才返回 true。云口令要联网拉取：命中即认领、后台导入（原版 RuleImporterManager.parse
     * 命中即 true 同款），拉取失败由后台提示交代，不假报成功。
     */
    static boolean importRuleText(Activity activity, String text) {
        if (activity == null || TextUtils.isEmpty(text)) return false;
        String t = text.trim();
        if (HkRuleManager.isCloudCode(t)) {
            new Thread(() -> {
                try {
                    HkRule rule = HkRuleManager.get().importByCloudCode(t);
                    App.post(() -> onRuleImported(activity, rule));
                } catch (Exception e) {
                    App.post(() -> HkRouteBlocker.toastShortBottom(activity,
                            "口令导入失败：" + e.getMessage()));
                }
            }).start();
            return true;
        }
        try {
            HkRule imported;
            if (HkRuleManager.isJsRuleText(t)) {
                imported = HkRuleManager.get().importJsRule(t, "口令导入");
            } else {
                java.util.List<HkRule> list = HkRuleManager.get().importJsonList(t);
                if (list == null || list.isEmpty()) return false;
                imported = list.get(0);
            }
            if (imported == null) return false;
            onRuleImported(activity, imported);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 导入成功交代：像海阔原版一样问「是否打开」，打开走本页的规则入口。 */
    static void onRuleImported(Activity activity, HkRule rule) {
        if (activity == null || activity.isFinishing() || rule == null) return;
        String title = TextUtils.isEmpty(rule.getTitle()) ? "小程序" : rule.getTitle();
        try {
            new HkDialogs(activity)
                    .setTitle("导入成功")
                    .setMessage("已导入「" + title + "」，是否打开？")
                    .setPositiveButton("打开", (d, w) -> startRule(activity, rule, title))
                    .setNegativeButton("关闭", null)
                    .show();
        } catch (Exception ignored) {
        }
    }

    /**
     * openFile:// 的下载文件检查（原版 DownloadRecordsFragment.checkDownload 的口径）：
     * 小于 4MB 的 .txt/.json/.hiker，内容像规则（find_rule 特征/分享口令/云口令/JS 规则）时
     * 走导入并问是否打开；不是规则就静默放行——桥随后照原版用系统应用打开该文件。
     */
    static void checkDownloadedRuleFile(File file) {
        if (file == null || !file.exists()) return;
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        if (!(name.endsWith(".txt") || name.endsWith(".json") || name.endsWith(".hiker"))) return;
        if (file.length() >= 4L * 1024 * 1024) return;
        new Thread(() -> {
            try {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                }
                String text = bos.toString(StandardCharsets.UTF_8.name());
                if (TextUtils.isEmpty(text)) return;
                boolean ruleLike = text.contains("\"find_rule\"")
                        || HkRuleManager.isShareCommandText(text)
                        || HkRuleManager.isCloudCode(text)
                        || HkRuleManager.isJsRuleText(text);
                if (!ruleLike) return;
                App.post(() -> {
                    Activity activity = currentActivity();
                    if (activity != null) importRuleText(activity, text);
                });
            } catch (Exception e) {
                // 下载文件检测失败不再静默不弹导入（残项处置 2026-11-14）。
                Log.e(TAG, "checkDownloadedRuleFile failed", e);
                HkRouteBlocker.toastShortBottom(App.get(), "规则文件读取失败，无法自动导入");
            }
        }).start();
    }

    /**
     * 迅雷链播放（磁力/种子/ed2k/ftp，单条与带选集列表共用）：星辰没有独立的迅雷面板，
     * 既有能力就是播放器——集地址进 VideoActivity 后由 Source.fetch 的 Thunder.Parser
     * 解析出文件列表（多文件在播放器内成选集），与旧宿主 HkPageActivity.playP2pInApp 同路，
     * 交接面与 startPlayerSingle/startPlayerChapters 相同，不碰迅雷模块内部。
     */
    static void playThunderLink(Context context, String url,
                                java.util.List<HkArticleFragment.HkChapter> chapters) {
        if (TextUtils.isEmpty(url)) {
            HkRouteBlocker.toastShortBottom(context, "链接不能为空");
            return;
        }
        Activity activity = context instanceof Activity ? (Activity) context : currentActivity();
        if (activity == null) {
            HkRouteBlocker.toastShortBottom(context, "无法打开播放器");
            return;
        }
        String ruleTitle = hostRuleTitle(activity);
        try {
            if (chapters == null || chapters.isEmpty()) {
                String name = thunderLinkName(url);
                JSONArray arr = new JSONArray();
                JSONObject o = new JSONObject();
                o.put("name", name);
                o.put("url", url);
                o.put("pic", "");
                arr.put(o);
                VideoActivity.startHkPlay(activity, ruleTitle, "默认", url, name, name, "",
                        arr.toString(), 0);
                return;
            }
            JSONArray arr = new JSONArray();
            int selIdx = -1;
            for (int i = 0; i < chapters.size(); i++) {
                HkArticleFragment.HkChapter ch = chapters.get(i);
                String raw = playUrlForChapter(ch);
                if (selIdx < 0 && (url.equals(raw) || url.equals(ch.url))) selIdx = i;
                JSONObject o = new JSONObject();
                o.put("name", !TextUtils.isEmpty(ch.title) ? ch.title
                        : (ch.memoryTitle == null ? "" : ch.memoryTitle));
                o.put("url", raw);
                o.put("pic", ch.pic == null ? "" : ch.pic);
                arr.put(o);
            }
            if (selIdx < 0) {
                selIdx = 0;
                for (int i = 0; i < chapters.size(); i++) {
                    if (chapters.get(i).use) {
                        selIdx = i;
                        break;
                    }
                }
            }
            HkArticleFragment.HkChapter sel = chapters.get(selIdx);
            String selName = !TextUtils.isEmpty(sel.title) ? sel.title
                    : (sel.memoryTitle == null ? "" : sel.memoryTitle);
            VideoActivity.startHkPlay(activity, ruleTitle, "默认", playUrlForChapter(sel),
                    selName, selName, sel.pic == null ? "" : sel.pic, arr.toString(), selIdx);
        } catch (Exception e) {
            android.util.Log.e(TAG, "hk thunder play failed", e);
            HkRouteBlocker.toastShortBottom(activity, "无法播放此链接");
        }
    }

    /** 迅雷链链接的显示名：磁力取 dn=、ed2k 取文件名段、其余取地址文件名，都没有回「播放」。 */
    static String thunderLinkName(String url) {
        try {
            String u = url.split(";")[0];
            if (u.startsWith("magnet:")) {
                String dn = android.net.Uri.parse(u).getQueryParameter("dn");
                return TextUtils.isEmpty(dn) ? "磁力链接" : dn;
            }
            if (u.startsWith("ed2k://")) {
                String[] segs = u.split("\\|");
                if (segs.length > 2 && !TextUtils.isEmpty(segs[2])) {
                    return java.net.URLDecoder.decode(segs[2], "UTF-8");
                }
                return "ed2k 链接";
            }
            String path = android.net.Uri.parse(u).getLastPathSegment();
            if (!TextUtils.isEmpty(path)) return path;
        } catch (Exception ignored) {
        }
        return "播放";
    }

    /** share:// 分享本地文件（原版 ShareUtil.findChooserToSend）：系统分享，Uri 走 FileUtil 的 FileProvider 既有入口。 */
    static void shareLocalFile(Context context, String absolutePath) {
        if (context == null || TextUtils.isEmpty(absolutePath)) return;
        File file = new File(absolutePath);
        if (!file.exists()) {
            HkRouteBlocker.toastShortBottom(context, "文件不存在" + absolutePath);
            return;
        }
        try {
            String mime = java.net.URLConnection.guessContentTypeFromName(file.getName());
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(TextUtils.isEmpty(mime) ? "*/*" : mime);
            intent.putExtra(Intent.EXTRA_STREAM,
                    com.fongmi.android.tv.utils.FileUtil.getShareUri(file));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(intent, "分享文件");
            if (!(context instanceof Activity)) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } catch (Exception e) {
            HkRouteBlocker.toastShortBottom(context, "分享失败：" + e.getMessage());
        }
    }

    /**
     * 分享规则（原版 ShareRuleUtil.shareRule/shareRuleByMoreWay 的星辰落点）：
     * 普通分享＝明文口令（HkRuleManager.buildShareCommand 的 ￥home_rule￥ 格式，海阔官方可导入）
     * 走系统分享；更多分享＝卡片菜单三项（明文口令/完整编码/云剪贴板），全走既有件。
     * 原版菜单里的「提交到云仓库」依赖海阔官方服务端，星辰未实现（HkShareHelper 同注），不列。
     */
    static void shareHkRule(HkRule rule, boolean moreWays) {
        if (rule == null) return;
        Activity activity = currentActivity();
        String ruleJson = new Gson().toJson(rule);
        if (!moreWays) {
            shareRuleCommand(activity, ruleJson);
            return;
        }
        if (activity == null || activity.isFinishing()) return;
        String[] options = {"明文口令分享", "完整编码分享", "云剪贴板分享"};
        HkDialogs.cards(activity, "分享规则", options, "取消", (dialog, which) -> {
            if (which == 0) shareRuleCommand(activity, ruleJson);
            else if (which == 1) shareRuleEncoded(activity, ruleJson);
            else if (which == 2) shareRuleToCloud(activity, rule);
        });
    }

    static void shareRuleCommand(Activity activity, String ruleJson) {
        String command = HkRuleManager.buildShareCommand(ruleJson);
        if (TextUtils.isEmpty(command)) return;
        if (activity == null) {
            // 没有可用页面时退回既有剪贴板分享（与 HkShareHelper.shareAsCommand 同款口令）。
            com.fongmi.android.tv.api.hk.HkShareHelper.shareAsCommand(App.get(), ruleJson);
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, command);
            activity.startActivity(Intent.createChooser(intent, "分享规则"));
        } catch (Exception e) {
            HkRouteBlocker.toastShortBottom(activity, "分享失败：" + e.getMessage());
        }
    }

    /** 完整编码分享（原版同款）：明文口令整体 Base64 后以 rule:// 形式复制到剪贴板。 */
    static void shareRuleEncoded(Activity activity, String ruleJson) {
        String command = HkRuleManager.buildShareCommand(ruleJson);
        if (activity == null || TextUtils.isEmpty(command)) return;
        try {
            String encoded = android.util.Base64.encodeToString(
                    command.getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("",
                        "rule://" + encoded.replaceAll("\\s+", "")));
            }
            HkRouteBlocker.toastShortBottom(activity, "口令已复制到剪贴板");
        } catch (Exception e) {
            HkRouteBlocker.toastShortBottom(activity, "分享失败：" + e.getMessage());
        }
    }

    /** 云剪贴板分享：走 HkRuleManager.exportAsCloudCode（云2 textdb 通道，海阔官方 App 可导入）。 */
    static void shareRuleToCloud(Activity activity, HkRule rule) {
        if (activity == null || rule == null || TextUtils.isEmpty(rule.getTitle())) return;
        HkRouteBlocker.toastShortBottom(activity, "正在生成云口令…");
        String title = rule.getTitle();
        new Thread(() -> {
            try {
                String code = HkRuleManager.get().exportAsCloudCode(title);
                App.post(() -> {
                    if (activity.isFinishing()) return;
                    try {
                        new HkDialogs(activity)
                                .setTitle("云口令")
                                .setMessage(code)
                                .setPositiveButton("复制", (d, w) -> {
                                    android.content.ClipboardManager cm = (android.content.ClipboardManager)
                                            activity.getSystemService(Context.CLIPBOARD_SERVICE);
                                    if (cm != null) {
                                        cm.setPrimaryClip(android.content.ClipData.newPlainText("", code));
                                    }
                                    HkRouteBlocker.toastShortBottom(activity, "口令已复制到剪贴板");
                                })
                                .setNegativeButton("关闭", null)
                                .show();
                    } catch (Exception e) {
                        Log.e(TAG, "shareRuleToCloud dialog failed", e);
                        HkRouteBlocker.toastShortBottom(activity, "云口令弹窗失败");
                    }
                });
            } catch (Exception e) {
                App.post(() -> HkRouteBlocker.toastShortBottom(activity,
                        "生成云口令失败：" + e.getMessage()));
            }
        }).start();
    }

    /**
     * 看图（原版 BigImageViewPager 的星辰落点）：全树没有独立图片浏览器，
     * 照旧宿主 HkPageActivity 的 image 支同款——HkDialogs 承载 ImageView + ImgUtil 加载，
     * 多图加上/下一张切换。无 Referer 信息可用（钩子签名不带页面地址），个别防盗链图可能加载不出，
     * 按封面定案失败保持透明、不垫图。
     */
    static void showImageDialog(Activity activity, java.util.List<String> urls, int startPos) {
        if (activity == null || activity.isFinishing() || urls == null || urls.isEmpty()) return;
        final java.util.List<String> list = new ArrayList<>(urls);
        final int[] pos = {Math.max(0, Math.min(startPos, list.size() - 1))};
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        android.widget.ImageView iv = new android.widget.ImageView(activity);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        int screenH = activity.getResources().getDisplayMetrics().heightPixels;
        root.addView(iv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (screenH * 0.6f)));
        if (list.size() > 1) {
            LinearLayout bar = new LinearLayout(activity);
            bar.setGravity(Gravity.CENTER);
            bar.setPadding(0, (int) (8 * density), 0, 0);
            TextView prev = new TextView(activity);
            prev.setText("上一张");
            prev.setTextSize(15);
            prev.setPadding((int) (16 * density), (int) (8 * density), (int) (16 * density), (int) (8 * density));
            final TextView indicator = new TextView(activity);
            indicator.setTextSize(14);
            indicator.setPadding((int) (12 * density), 0, (int) (12 * density), 0);
            TextView next = new TextView(activity);
            next.setText("下一张");
            next.setTextSize(15);
            next.setPadding((int) (16 * density), (int) (8 * density), (int) (16 * density), (int) (8 * density));
            prev.setOnClickListener(v -> {
                pos[0] = (pos[0] - 1 + list.size()) % list.size();
                loadImageAt(iv, indicator, list, pos[0]);
            });
            next.setOnClickListener(v -> {
                pos[0] = (pos[0] + 1) % list.size();
                loadImageAt(iv, indicator, list, pos[0]);
            });
            bar.addView(prev);
            bar.addView(indicator);
            bar.addView(next);
            root.addView(bar);
            loadImageAt(iv, indicator, list, pos[0]);
        } else {
            loadImageAt(iv, null, list, pos[0]);
        }
        try {
            new HkDialogs(activity)
                    .setView(root)
                    .setPositiveButton("关闭", null)
                    .show();
        } catch (Exception ignored) {
        }
    }

    static void loadImageAt(android.widget.ImageView iv, TextView indicator,
                            java.util.List<String> urls, int pos) {
        com.fongmi.android.tv.utils.ImgUtil.load("", urls.get(pos), iv);
        if (indicator != null) indicator.setText((pos + 1) + " / " + urls.size());
    }

    /** 调试数据（原版 FileDetailPopup + ArticleListFragment.getDetailData 七行）：卡片弹窗列行，点行复制整行。 */
    static void showItemDetail(Activity activity, HkItem item, HkRule rule) {
        if (activity == null || activity.isFinishing() || item == null) return;
        String url = item.getUrl() == null ? "" : item.getUrl();
        String link = url.length() > 200 ? url.substring(0, 200) + "..." : url;
        final String[] data = {
                "页面：" + (rule == null ? "" : hkEmptyIfNull(rule.getUrl())),
                "标题：" + hkEmptyIfNull(item.getTitle()),
                "描述：" + hkEmptyIfNull(item.getDesc()),
                "类型：" + hkEmptyIfNull(item.getType()),
                "链接：" + link,
                "图片：" + hkEmptyIfNull(item.getPic()),
                "附加：" + hkEmptyIfNull(item.getExtra()),
        };
        HkDialogs.cards(activity, "调试数据", data, "关闭", (dialog, which) -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("", data[which]));
            }
            HkRouteBlocker.toastShortBottom(activity, "已复制");
        });
    }

    static String hkEmptyIfNull(String s) {
        return s == null ? "" : s;
    }

    /**
     * 照 8.83 VideoPlayerActivity 的 codeAndHeader 语义：集地址带 @lazyRule= 时把 codeAndHeader 拼回页面段，
     * 使引擎 HkRouter.play 取到的 lazyPageUrl 自带抓取指令（;get / ;{headers}），headers 随交接不丢。
     */
    static String playUrlForChapter(HkArticleFragment.HkChapter chapter) {
        String url = chapter.url == null ? "" : chapter.url;
        String cah = chapter.codeAndHeader;
        if (cah != null && !cah.isEmpty() && url.contains("@lazyRule=") && !url.contains(cah)) {
            int lr = url.indexOf("@lazyRule=");
            url = url.substring(0, lr) + cah + url.substring(lr);
        }
        return url;
    }

    /**
     * 归并判定专用地址（只作 HkEpisodeMerger.plan 的比对输入，绝不用于起播）：
     * 判定件只收原始形态地址（见 HkEpisodeMerger 类头入参契约）。被点章的 url
     * 已被点击流程的懒解析换成干净纯地址，与兄弟章的原始包装地址不同桶；收集层
     * 已为每章另存原始地址 originalUrl（codeAndHeader 非空时赋值，见
     * HkArticleFragment.getChapters/buildChapter），故此处 originalUrl 优先、
     * 为空回退现行地址 playUrlForChapter。归并产出的播放地址与下游起播仍走
     * playUrlForChapter 的现行形态，一字不动。
     */
    static String mergeJudgeUrlForChapter(HkArticleFragment.HkChapter chapter) {
        if (chapter == null) return "";
        if (chapter.originalUrl != null && !chapter.originalUrl.isEmpty()) return chapter.originalUrl;
        return playUrlForChapter(chapter);
    }

    /** 地址是 8.83 播放数据（{urls,...} 的 JSON 串）时返回解析结果（走已移植的 HttpParser.getPlayData），否则返回空。 */
    static HkPlayData playDataOf(String url) {
        if (url == null) return null;
        String t = url.trim();
        // x5Play:// 剥壳再认播放数据（同 HkRouter 入口口径，定根账
        // hk-sniff-route-miswire-20261113）：章节地址带壳时线路名/载荷也要认得出
        if (t.startsWith("x5Play://")) t = t.substring("x5Play://".length()).trim();
        if (!t.startsWith("{")) return null;
        HkPlayData pd = HkHttpParser.getPlayData(t);
        return pd != null && pd.getUrls() != null && !pd.getUrls().isEmpty() ? pd : null;
    }

    private static java.util.List<String> namesOf(HkPlayData pd) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (pd == null || pd.getNames() == null) return out;
        java.util.Set<String> used = new java.util.HashSet<>();
        java.util.List<String> names = pd.getNames();
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i) == null ? "" : names.get(i).trim();
            if (n.isEmpty()) n = "线路" + (i + 1);
            if (!used.add(n)) n = n + " " + (i + 1);
            used.add(n);
            out.add(n);
        }
        return out;
    }

    /**
     * 页函数型详情的当前线名回收（C1）：这类规则的线路只活在详情函数里，
     * 当前线的每一集地址都是 {@code hiker://empty@lazyRule=.js:(函数)("线名@@集源址")}
     * 形态——线名被规则自己写进懒函数的首入参（@@ 前一段，橘汁 lib.js 的
     * lineName@@pu）。逐集（现行地址＋原始地址两份）提取候选，至少一集读得出、
     * 且所有读得出的完全一致才采信；互相矛盾或全读不出回空串，交接侧维持
     * 「默认」，不猜。采信只影响播放器里的线路名显示，播放地址一字不动。
     * 读不出的形态（不在可得范围，维持「默认」）：入参没有 @@ 分隔的其他约定、
     * 懒文本不是 (函数)(实参) 调用形、b64 外层不是 eval(base64Decode("…")) 标准形。
     */
    static String currentLineNameOf(java.util.List<HkArticleFragment.HkChapter> chapters,
                                    java.util.List<String> rawUrls) {
        if (chapters == null) return "";
        String found = "";
        for (int i = 0; i < chapters.size(); i++) {
            HkArticleFragment.HkChapter ch = chapters.get(i);
            String[] cands = {rawUrls != null && i < rawUrls.size() ? rawUrls.get(i) : null,
                    ch == null ? null : ch.originalUrl};
            for (String cand : cands) {
                String name = lazyInputLineName(cand);
                if (name.isEmpty()) continue;
                if (found.isEmpty()) found = name;
                else if (!found.equals(name)) return "";
            }
        }
        return found;
    }

    /** 从一集的懒地址里读「线名@@…」入参的首段；读不出回空串。见 currentLineNameOf。 */
    private static String lazyInputLineName(String url) {
        if (url == null) return "";
        int lr = url.indexOf("@lazyRule=");
        if (lr < 0) return "";
        String text = url.substring(lr + "@lazyRule=".length()).trim();
        if (text.startsWith(".js:")) text = text.substring(4).trim();
        else if (text.startsWith("js:")) text = text.substring(3).trim();
        // b64 形态（$.b64() 开过）：eval(base64Decode("…"))——解开外层再按明文读
        if (text.startsWith("eval(base64Decode(")) {
            int q1 = text.indexOf('"');
            int q2 = q1 < 0 ? -1 : text.indexOf('"', q1 + 1);
            if (q2 < 0) return "";
            try {
                text = new String(android.util.Base64.decode(
                        text.substring(q1 + 1, q2), android.util.Base64.DEFAULT), "UTF-8");
            } catch (Throwable e) {
                return "";
            }
        }
        // 调用形以 (函数)("首入参"[, 其余入参]) 收尾（其后只许页面标记 #…#）：
        // 取首入参字符串字面量，JSON 口径反转义后再按 @@ 切首段
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"(?:\\s*,[^)]*)?\\)\\s*(#[^#]*#)?\\s*$").matcher(text);
        if (!m.find()) return "";
        String input;
        try {
            input = new JSONArray("[\"" + m.group(1) + "\"]").getString(0);
        } catch (Throwable e) {
            return "";
        }
        int at = input.indexOf("@@");
        if (at <= 0) return "";
        String head = input.substring(0, at).trim();
        if (head.isEmpty() || head.length() > 60) return "";
        if (head.contains("://") || head.contains("/") || head.contains("\n")
                || head.startsWith("{") || head.startsWith("[")) return "";
        return head;
    }

    /** 线路名：选中集的 names 有多条就用它；否则找任一集多条的；全是单地址回单个「默认」。 */
    static java.util.List<String> lineNamesOf(java.util.List<HkPlayData> playDatas, int selIdx) {
        java.util.List<String> names = namesOf(playDatas.get(selIdx));
        if (names.size() > 1) return names;
        for (HkPlayData pd : playDatas) {
            java.util.List<String> ns = namesOf(pd);
            if (ns.size() > 1) return ns;
        }
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("默认");
        return out;
    }

    /** 某集在某条线路下的实际地址：取 urls[线路]（越界回 urls[0]），该线路的请求头/字幕/分离音频/弹幕随址打包。 */
    static String expandedUrl(HkPlayData pd, String rawUrl, int line) {
        if (pd == null) return rawUrl;
        java.util.List<String> urls = pd.getUrls();
        if (urls == null || urls.isEmpty()) return rawUrl;
        String u = urls.get(line < urls.size() ? line : 0);
        u = u == null ? "" : u.trim();
        if (u.isEmpty()) return rawUrl;
        java.util.List<String> kvs = new java.util.ArrayList<>();
        java.util.List<Map<String, String>> headers = pd.getHeaders();
        if (headers != null && line < headers.size() && headers.get(line) != null) {
            for (Map.Entry<String, String> e : headers.get(line).entrySet()) {
                String k = e.getKey() == null ? "" : e.getKey().trim();
                String v = e.getValue() == null ? "" : e.getValue().trim();
                if (!k.isEmpty() && !v.isEmpty()) kvs.add(k + "@" + v);
            }
        }
        if (pd.getHeader() != null && line == 0) {
            for (Map.Entry<String, String> e : pd.getHeader().entrySet()) {
                String k = e.getKey() == null ? "" : e.getKey().trim();
                String v = e.getValue() == null ? "" : e.getValue().trim();
                if (!k.isEmpty() && !v.isEmpty()) kvs.add(k + "@" + v);
            }
        }
        // 附带字段随址打包（G49/G59）：subtitle/audioUrls/danmu 都在入参 pd 里
        // （HkPlayData 9 字段、解析时按名直填），从 pd 直读——旧形 pd 无此三
        // 字段才从原始串补读，字段补齐后补读绕路收净（F1 补改）。audioUrls
        // 按线路取（越界回首条，与 urls 同口径）；lyric 环34 起同路入 mini 包
        // ——回环解析 parsePlayJson 读 lyric 入 HkPlay、经 hkPlayerContent 入
        // Result/PlaySpec 播放层（旧形「无消费面不入包」随终端消费补齐收净）。
        String subtitle = pd.getSubtitle() == null ? "" : pd.getSubtitle().trim();
        String audio = "";
        java.util.List<String> audioUrls = pd.getAudioUrls();
        if (audioUrls != null && !audioUrls.isEmpty()) {
            String a = audioUrls.get(line < audioUrls.size() ? line : 0);
            audio = a == null ? "" : a.trim();
        }
        String danmu = pd.getDanmu() == null ? "" : pd.getDanmu().trim();
        String lyric = pd.getLyric() == null ? "" : pd.getLyric().trim();
        if (kvs.isEmpty() && subtitle.isEmpty() && audio.isEmpty() && danmu.isEmpty() && lyric.isEmpty()) return u;
        try {
            JSONObject mini = new JSONObject();
            JSONArray mu = new JSONArray();
            mu.put(u);
            mini.put("urls", mu);
            if (!kvs.isEmpty()) {
                JSONArray mh = new JSONArray();
                for (String kv : kvs) mh.put(kv);
                mini.put("headers", mh);
            }
            if (!subtitle.isEmpty()) mini.put("subtitle", subtitle);
            if (!danmu.isEmpty()) mini.put("danmu", danmu);
            if (!lyric.isEmpty()) mini.put("lyric", lyric);
            if (!audio.isEmpty()) {
                JSONArray ma = new JSONArray();
                ma.put(audio);
                mini.put("audioUrls", ma);
            }
            return mini.toString();
        } catch (Exception e) {
            return u;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // ================= G3 线组识别 / G8 详情回采（交接附带数据） =================

    /**
     * 交接附带数据组装（G3+G8）：详情页点集时由 HkArticleFragment 在组 extraData
     * 时调用，把①页函数型线路的线组描述（lineGroups + actTitle）与②详情头元数据
     * （封面/简介/备注/演职员）塞进 bundle，startPlayerChapters 消费。
     * 识别拿不准一律不写键——startPlayerChapters 无键即走现行单线/PlayData 路，
     * 行为与改造前逐字一致。
     */
    static void enrichHandover(Bundle bundle, java.util.List<HkItem> items,
                               java.util.List<HkArticleFragment.HkChapter> chapters, String actTitle) {
        if (bundle == null || items == null) return;
        try {
            if (!TextUtils.isEmpty(actTitle)) bundle.putString("actTitle", actTitle);
            java.util.List<String> rawUrls = new java.util.ArrayList<>();
            if (chapters != null) {
                for (HkArticleFragment.HkChapter ch : chapters) rawUrls.add(playUrlForChapter(ch));
            }
            String cur = chapters == null ? "" : currentLineNameOf(chapters, rawUrls);
            String groups = detectLineGroupsJson(items, cur);
            if (!groups.isEmpty()) bundle.putString("lineGroups", groups);
            String[] meta = extractDetailMeta(items);
            if (!meta[0].isEmpty()) bundle.putString("detailPic", meta[0]);
            if (!meta[1].isEmpty()) bundle.putString("detailContent", meta[1]);
            if (!meta[2].isEmpty()) bundle.putString("detailRemarks", meta[2]);
            if (!meta[3].isEmpty()) bundle.putString("detailActor", meta[3]);
            if (!meta[4].isEmpty()) bundle.putString("detailDirector", meta[4]);
        } catch (Throwable ignored) {
        }
    }

    /** js 调用尾（与 C1 的 lazyInputLineName 同形）：{@code (函数)("入参", …)} 收尾。 */
    private static final java.util.regex.Pattern RULE_CALL_TAIL = java.util.regex.Pattern.compile(
            "\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"(?:\\s*,[^)]*)?\\)\\s*(#[^#]*#)?\\s*$",
            java.util.regex.Pattern.DOTALL);

    /** 线钮入参契约：公共前缀 + 尾部整数（橘汁 detailFn 的 "vid|线序" 形）。 */
    private static final java.util.regex.Pattern LINE_INPUT_TAIL = java.util.regex.Pattern.compile(
            "^(.*?)(\\d+)$");

    private static final java.util.regex.Pattern B64_EVAL_WRAP = java.util.regex.Pattern.compile(
            "^eval\\s*\\(\\s*base64Decode\\s*\\(\\s*\"([A-Za-z0-9+/=]+)\"\\s*\\)\\s*\\)\\s*$",
            java.util.regex.Pattern.DOTALL);

    /**
     * 从条目地址里拆一发 @rule= 的 js 调用：返回 [函数文本键, 首入参]，拆不出回 null。
     * 只认 js: 序列化形（Hikerurl.js 的 .rule() 产物：尾段是
     * {@code (函数原文)("入参",…)}，b64 模式外包 eval(base64Decode("…")) 先解开）；
     * ";" 六段选择器形等非 js 调用形不在此契约内，回 null（不猜）。
     */
    private static String[] ruleCallOf(String url) {
        if (url == null) return null;
        int at = url.indexOf("@rule=");
        if (at < 0) return null;
        String tail = url.substring(at + "@rule=".length());
        String head = tail.split("==>", -1)[0];
        if (!head.startsWith("js:")) return null;
        String text = head.substring(3).trim();
        java.util.regex.Matcher wrap = B64_EVAL_WRAP.matcher(text);
        if (wrap.matches()) {
            try {
                text = new String(Base64.decode(wrap.group(1), Base64.DEFAULT), StandardCharsets.UTF_8).trim();
            } catch (Exception e) {
                return null;
            }
        }
        java.util.regex.Matcher m = RULE_CALL_TAIL.matcher(text);
        if (!m.find()) return null;
        String input;
        try {
            input = new JSONArray("[\"" + m.group(1) + "\"]").getString(0);
        } catch (Exception e) {
            return null;
        }
        String fnKey = text.substring(0, m.start()).trim();
        if (fnKey.isEmpty() || TextUtils.isEmpty(input)) return null;
        return new String[]{fnKey, input};
    }

    /**
     * 线组识别（窄口径，宁可不认不可认错）：详情页条目表里找一组成组线钮——
     * 同一详情函数的 @rule= 调用 ≥2 条、首入参同前缀只差尾部整数且整数互异、
     * 组员在表中成连续段（只许空地址/blank_block 中性条目间隔）、恰有一条的标题
     * 等于当前线名（C1 回收名，原文或清洗后比对）。两组同时合式视为歧义回 ""。
     * 命中回 [{name,url,extra}] 的 JSON 串（组序即页面序）。
     */
    static String detectLineGroupsJson(java.util.List<HkItem> items, String currentLineName) {
        if (items == null || items.isEmpty() || TextUtils.isEmpty(currentLineName)) return "";
        java.util.List<Integer> candIdx = new java.util.ArrayList<>();
        java.util.List<String[]> candCall = new java.util.ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            HkItem it = items.get(i);
            if (it == null) continue;
            String[] call = ruleCallOf(it.getUrl());
            if (call == null) continue;
            candIdx.add(i);
            candCall.add(call);
        }
        java.util.Map<String, java.util.List<Integer>> byFn = new java.util.LinkedHashMap<>();
        for (int k = 0; k < candIdx.size(); k++) {
            byFn.computeIfAbsent(candCall.get(k)[0], x -> new java.util.ArrayList<>()).add(k);
        }
        java.util.List<Integer> chosen = null;
        for (java.util.List<Integer> group : byFn.values()) {
            if (group.size() < 2) continue;
            String prefix = null;
            boolean ok = true;
            boolean anchor = false;
            java.util.Set<Integer> seenNums = new java.util.HashSet<>();
            for (int k : group) {
                java.util.regex.Matcher m = LINE_INPUT_TAIL.matcher(candCall.get(k)[1]);
                if (!m.matches() || m.group(1).isEmpty()) {
                    ok = false;
                    break;
                }
                if (prefix == null) prefix = m.group(1);
                else if (!prefix.equals(m.group(1))) {
                    ok = false;
                    break;
                }
                try {
                    if (!seenNums.add(Integer.parseInt(m.group(2)))) {
                        ok = false;
                        break;
                    }
                } catch (NumberFormatException e) {
                    ok = false;
                    break;
                }
                String t = items.get(candIdx.get(k)).getTitle();
                String raw = t == null ? "" : t.trim();
                if (currentLineName.equals(raw)
                        || currentLineName.equals(HkStringUtil.getTitleText(t == null ? "" : t))) {
                    anchor = true;
                }
            }
            if (!ok || !anchor) continue;
            int first = candIdx.get(group.get(0));
            int last = candIdx.get(group.get(group.size() - 1));
            java.util.Set<Integer> member = new java.util.HashSet<>();
            for (int k : group) member.add(candIdx.get(k));
            for (int i = first; i <= last && ok; i++) {
                if (member.contains(i)) continue;
                HkItem it = items.get(i);
                String u = it == null ? "" : it.getUrl();
                String ct = it == null ? "" : it.getColType();
                if (!TextUtils.isEmpty(u) && !"blank_block".equals(ct)) ok = false;
            }
            if (!ok) continue;
            if (chosen != null) return "";
            chosen = group;
        }
        if (chosen == null) return "";
        try {
            JSONArray arr = new JSONArray();
            for (int k : chosen) {
                HkItem it = items.get(candIdx.get(k));
                JSONObject o = new JSONObject();
                o.put("name", it.getTitle() == null ? "" : it.getTitle().trim());
                o.put("url", nz(it.getUrl()));
                o.put("extra", nz(it.getExtra()));
                arr.put(o);
            }
            return arr.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 详情头元数据回采（G8）：返回 [pic, content, remarks, actor, director]，取不到
     * 回空串（不摆占位）。头部卡＝col_type 以 movie_/icon_ 起、有图、地址为裸占位
     * 的条目（详情页头部本体）；简介＝首个 long_text 条目的正文，无则回落头部 desc；
     * 演职员只从头部 desc 的明示标签（导演/主演/演员…）里切，无标签留空不编。
     */
    static String[] extractDetailMeta(java.util.List<HkItem> items) {
        String[] out = {"", "", "", "", ""};
        if (items == null) return out;
        String headerDesc = "";
        for (HkItem it : items) {
            if (it == null) continue;
            String ct = it.getColType() == null ? "" : it.getColType();
            if (out[0].isEmpty() && (ct.startsWith("movie_") || ct.startsWith("icon_"))) {
                String pic = it.getPic();
                String u = it.getUrl() == null ? "" : it.getUrl().trim();
                if (!TextUtils.isEmpty(pic) && !"*".equals(pic)
                        && (u.isEmpty() || u.equals("hiker://empty")
                        || u.startsWith("hiker://empty#") || u.startsWith("hiker://empty?"))) {
                    out[0] = pic;
                    headerDesc = it.getDesc() == null ? "" : it.getDesc().trim();
                    out[2] = headerDesc;
                }
            }
            if (out[1].isEmpty() && "long_text".equals(ct)) {
                String c = !TextUtils.isEmpty(it.getContent()) ? it.getContent() : it.getTitle();
                if (!TextUtils.isEmpty(c)) out[1] = c.trim();
            }
            if (!out[0].isEmpty() && !out[1].isEmpty()) break;
        }
        if (out[1].isEmpty()) out[1] = out[2];
        if (!headerDesc.isEmpty()) {
            out[3] = metaSegment(headerDesc, new String[]{"领衔主演", "主演", "演员", "主持人", "主唱"});
            out[4] = metaSegment(headerDesc, new String[]{"总导演", "导演"});
        }
        return out;
    }

    /** 从 desc 文本里按「标签：」切一段值，切到下一个已知标签/换行为止；无标签回空。 */
    private static String metaSegment(String text, String[] labels) {
        String[] stops = {"导演", "总导演", "主演", "领衔主演", "演员", "主持人", "主唱",
                "简介", "剧情", "类型", "地区", "年代", "上映", "又名"};
        for (String label : labels) {
            for (String sep : new String[]{"：", ":"}) {
                int i = text.indexOf(label + sep);
                if (i < 0) continue;
                int start = i + label.length() + sep.length();
                int end = text.length();
                for (String s : stops) {
                    if (s.equals(label)) continue;
                    int j = text.indexOf(s + "：", start);
                    if (j < 0) j = text.indexOf(s + ":", start);
                    if (j >= 0 && j < end) end = j;
                }
                int nl = text.indexOf('\n', start);
                if (nl >= 0 && nl < end) end = nl;
                String v = text.substring(start, end).trim().replaceAll("[\\s·|/、]+$", "");
                if (!v.isEmpty() && v.length() <= 120) return v;
            }
        }
        return "";
    }

    private static JSONArray parseLineGroups(Bundle extraData) {
        if (extraData == null) return null;
        String s = extraData.getString("lineGroups");
        if (TextUtils.isEmpty(s)) return null;
        try {
            JSONArray a = new JSONArray(s);
            return a.length() >= 2 ? a : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static int indexOfLineGroup(JSONArray groups, String name) {
        if (groups == null || TextUtils.isEmpty(name)) return -1;
        for (int i = 0; i < groups.length(); i++) {
            JSONObject o = groups.optJSONObject(i);
            if (o != null && name.equals(o.optString("name", ""))) return i;
        }
        return -1;
    }

    /**
     * 线组交接载荷：{flags, hkRerunRule, hkPrefix}。当前线带真集置首（播放页
     * setupDirectEpisodes 只给首线标选中集、selIdx 以首线集序为准）；其余线
     * episodes 为空、带 rerun 入口（线钮原始地址）与 extra，播放页切到时重跑取集。
     */
    static JSONObject buildRerunFlagsPayload(java.util.List<HkArticleFragment.HkChapter> chapters,
                                             java.util.List<String> rawUrls, String curName,
                                             JSONArray groups, int curPos, String ruleJson, String prefix) {
        try {
            JSONObject root = new JSONObject();
            if (!TextUtils.isEmpty(ruleJson)) root.put("hkRerunRule", ruleJson);
            if (!TextUtils.isEmpty(prefix)) root.put("hkPrefix", prefix);
            JSONArray flags = new JSONArray();
            JSONObject cur = new JSONObject();
            cur.put("name", curName);
            JSONArray eps = new JSONArray();
            for (int i = 0; i < chapters.size(); i++) {
                HkArticleFragment.HkChapter ch = chapters.get(i);
                JSONObject o = new JSONObject();
                o.put("name", !TextUtils.isEmpty(ch.title) ? ch.title
                        : (ch.memoryTitle == null ? "" : ch.memoryTitle));
                o.put("url", rawUrls.get(i));
                o.put("pic", ch.pic == null ? "" : ch.pic);
                eps.put(o);
            }
            cur.put("episodes", eps);
            flags.put(cur);
            for (int gi = 0; gi < groups.length(); gi++) {
                if (gi == curPos) continue;
                JSONObject g = groups.getJSONObject(gi);
                JSONObject f = new JSONObject();
                f.put("name", g.optString("name", ""));
                f.put("rerun", g.optString("url", ""));
                String ex = g.optString("extra", "");
                if (!ex.isEmpty()) f.put("extra", ex);
                f.put("episodes", new JSONArray());
                flags.put(f);
            }
            root.put("flags", flags);
            return root;
        } catch (Exception e) {
            return null;
        }
    }

    /** 多线路载荷：{flags:[{name, episodes:[{name,url,pic}]}]}，播放页据此展开线路与选集。 */
    static JSONObject buildFlagsPayload(java.util.List<HkArticleFragment.HkChapter> chapters,
                                        java.util.List<String> rawUrls,
                                        java.util.List<HkPlayData> playDatas,
                                        java.util.List<String> lineNames) {
        try {
            JSONObject root = new JSONObject();
            JSONArray flags = new JSONArray();
            for (int line = 0; line < lineNames.size(); line++) {
                JSONObject f = new JSONObject();
                f.put("name", lineNames.get(line));
                JSONArray eps = new JSONArray();
                for (int i = 0; i < chapters.size(); i++) {
                    HkArticleFragment.HkChapter ch = chapters.get(i);
                    JSONObject o = new JSONObject();
                    o.put("name", !TextUtils.isEmpty(ch.title) ? ch.title
                            : (ch.memoryTitle == null ? "" : ch.memoryTitle));
                    o.put("url", expandedUrl(playDatas.get(i), rawUrls.get(i), line));
                    o.put("pic", ch.pic == null ? "" : ch.pic);
                    eps.put(o);
                }
                f.put("episodes", eps);
                flags.put(f);
            }
            root.put("flags", flags);
            return root;
        } catch (Exception e) {
            return null;
        }
    }

    /** x5Play:// 承载页（F3）：合成 x5_webview_single 规则开行内 WebView 页（与列表/搜索页 x5:// 支同构）。 */
    static void openX5PlayPage(Activity activity, String title, String target) {
        // 诊断（定根账可观测性缺口）：x5 承载页是点击落「网页」的总落点之一，
        // 此前无日志。只记标题，不记目标地址。
        try {
            com.github.catvod.crawler.SpiderDebug.log("hk", "x5 web-landing title=%s", title == null ? "" : title);
        } catch (Throwable ignored) {
        }
        HkRule rule = new HkRule();
        rule.setUrl("hiker://empty");
        rule.setFindRule("js:setResult([{\n    url:\"" + HkJsRuntime.escapeJsString(target)
                + "\",\ncol_type:\"x5_webview_single\",\ndesc:\"100%&&float\",\nextra:{canBack: true}\n}]);");
        rule.setColType(HkColType.X5_WEB_VIEW.code);
        rule.setTitle(title == null ? "" : title);
        // 环29 复核订正 B1：本页即专用网页页，新开的这一页打承载标记。
        startRule(activity, rule, title, true);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webCarrierPage = getIntent() != null && getIntent().getBooleanExtra(EXTRA_WEB_CARRIER_PAGE, false);
        // 窗口登记册（fix5 的 HkWindowRegistry）已随窗口形态整件退役：同任务
        // 压栈形态下没有窗口可登记、重开同规则恒新开一页（账
        // hk-task-single-20261103 订正节）。
        ensureStatics();
        // 照原版 MainActivity.initBackgroundTaskNotImportant 的机会性清理：
        // #autoCache# 页缓存过期/超量在此顺手清（clearCache 内部异步、幂等）。
        HkAutoCacheUtil.clearCache(this);
        // 照原版 initView 的规则摄入顺序：Intent "data" → 静态槽 → 缓存文件，全空走回收提示退出
        String data = getIntent().getStringExtra("data");
        if (TextUtils.isEmpty(data)) {
            // 回退消费必须凭 Intent 里的规则指纹认领，对不上就当没有（走回收提示），
            // 绝不把上一个大规则静默安过来——那正是串页（花都影视出 MissAV 内容）的机制。
            String key = getIntent().getStringExtra("ruleKey");
            if (key != null && key.equals(tempRuleKey)) data = tempRuleData;
            if (data == null && key != null) data = readTempRuleCache(key);
        }
        if (TextUtils.isEmpty(data)) {
            showRecycledAndExit();
            return;
        }
        try {
            rule = new Gson().fromJson(data, HkRule.class);
        } catch (Exception e) {
            Log.e(TAG, "hk error", e);
        }
        if (rule == null) {
            showRecycledAndExit();
            return;
        }
        // 照原版 initData：消费 tempParentData 静态槽（取走后清空）
        HkArticleFragment.AutoPageData parentData = tempParentData;
        tempParentData = null;
        pageTitle = getIntent().getStringExtra("title");
        if (TextUtils.isEmpty(pageTitle)) pageTitle = rule.getTitle();
        // 赶在列表加载触发引擎 init（跑 preRule）之前注册，否则第一声 showLoading 会漏掉
        HkLoading.setListener(hkLoadingListener);
        // 活动标题必须真实设置：HkFuck.inList(Activity) 与 Fragment.activityTitle() 都读它
        setTitle(pageTitle == null ? "" : pageTitle);
        setContentView(R.layout.activity_hk_list);
        // 液态玻璃动态背景（LinearLayout 根走 decor 注入，视觉等同 XML 首孩子）
        HkDynamicBgView.attachTo(this);
        // 海阔浅色页状态栏：透明＋深色图标，动态背景（decor 注入）画到顶，融成一片
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        buildMiniappTabs();
        // 照原版 FilmListActivity／RuleWindowActivity.initData：无条件 new
        // ArticleListFragment + instance(rule) + add，从不查 FragmentManager
        // 恢复出的旧 Fragment（BaseActivity.onCreate 对 savedInstanceState
        // 只查 recycleMe 旗，无恢复复用支）。咱家旧恢复支（带存档重建时补接线
        // 直接摆销毁前旧状态、不重跑规则）是自加短路、原版无此物，已整支摘除：
        // 页面被系统销毁后重建与「按返回关掉后从首页点进」同形，从头跑规则
        //（账 hk-reentry-reload-fix-20261108）。savedInstanceState 非空时容器里
        // 可能还挂着系统恢复出的旧 Fragment，落码用 replace 原子摘除再放新页，
        // 不许与新页同容器并存。
        fragment = new HkArticleFragment().instance(rule);
        fragment.setHostListener(this);
        fragment.setListHost(getIntent().getBooleanExtra("listHost", false));
        fragment.setPageOpener(this);
        fragment.setImmersiveTheme(false);
        // 照原版 FilmListActivity 同点位：setReadTheme(isReadTheme())——url 含
        // #readTheme# 或 #autoReader# 即阅读主题（官 FilmListActivity:1611/5370）。
        fragment.setReadTheme(isReadThemeUrl(rule.getUrl()));
        // 照原版 FilmListActivity.initData 同点位：initAutoCache(页面标题, 规则原始 url)，
        // #autoCache#/#cacheOnly# 页缓存的键与开关在此定死（赶在 Fragment.initData 之前）。
        fragment.initAutoCache(pageTitle, rule.getUrl());
        if (parentData != null) fragment.setAutoPageData(parentData);
        // 同步执行（commitNow）：异步 commit() 落地前，系统恢复出的旧 Fragment
        // 仍能走完 onViewCreated→initData 把空规则交给模型（2026-11-10 NPE 案）；
        // 同步替换当场摘除旧片，页面实例恒为 instance(rule) 的产物，与官形
        // 「规则是构造期输入」一致（账 hiker-npe-ruletitle-20261110 §六.2）。
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.xc_frag_fade_in, R.anim.xc_frag_fade_out)
                .replace(R.id.hk_list_container, fragment)
                .commitNow();
        // 精修 P4：与主 App 同一套渐入渐出（不改继承，只加两行）
        XcFadeTransition.applyEnter(this);
    }

    /** 照原版 FilmListActivity.isReadTheme()：规则 url 含 #readTheme# 或 #autoReader#。 */
    static boolean isReadThemeUrl(String url) {
        return !TextUtils.isEmpty(url) && (url.contains("#readTheme#") || url.contains("#autoReader#"));
    }

    /** 照原版 FilmListActivity.isOnPause()。 */
    public boolean isOnPause() {
        return isOnPause;
    }

    @Override
    public void onBackPressed() {
        // 照原版宿主分发：先问内容页的行内 WebView 要不要后退（能退且条目允许时先 goBack），
        // 它消费了就不出页；不消费才走原有返回。判定本体在 HkArticleFragment.onBackPressed。
        if (fragment != null && fragment.onBackPressed()) return;
        // 内容页不消费才出页：出页前过退出契约任务根自救（本页成
        // 任务根时裸退穿桌面，先回点播首页，ExitContract 一处所有）。
        ExitContract.rescueIfTaskRoot(this);
        super.onBackPressed();
    }

    @Override
    public void finish() {
        // 同任务压栈形态下 finish 就是普通弹栈：fix5 窗口形态的关闭侧
        // （hkWindow 判定＋摘册＋finishAndRemoveTask 摘卡）已随窗口协议整件
        // 退役——没有独立任务就没有卡可摘，返回落点由栈序本身保证（账
        // hk-task-single-20261103 订正节）。
        super.finish();
        XcFadeTransition.applyExit(this);
    }

    @Override
    protected void onResume() {
        isOnPause = false;
        super.onResume();
        currentRef = new WeakReference<>(this);
        HkLoading.setListener(hkLoadingListener);
    }

    @Override
    protected void onPause() {
        isOnPause = true;
        if (currentActivity() == this) currentRef = null;
        HkLoading.clearListener(hkLoadingListener);
        hideHkLoading();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        // 环29 复核订正 B1：承载登记与页面生命周期对齐——本页销毁即注销。
        HkWebCarrier.unregister(this);
        HkLoading.clearListener(hkLoadingListener);
        hideHkLoading();
        HkWebkitParser.destroy0();
        HkX5WebViewParser.destroy0();
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);
    }

    @Override
    protected void onStop() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
        super.onStop();
    }

    /** backToHome：8.83 BackMainEvent 清栈。星辰等价：子页级实例（listHost=true）直接退出，规则首页级留守。 */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onBackMainEvent(com.fongmi.android.tv.api.hk.event.video.BackMainEvent event) {
        if (getIntent().getBooleanExtra("listHost", false)) finish();
    }

    /** 照原版 initView 数据全丢分支：温馨提示「页面数据被系统回收，即将退出该页面。」确认后退出。 */
    private void showRecycledAndExit() {
        new HkDialogs(this)
                .setTitle("温馨提示")
                .setMessage("页面数据被系统回收，即将退出该页面。")
                .setCancelable(false)
                .setPositiveButton("知道了", (dialog, which) -> finish())
                .show();
    }

    // —— PageOpener（子列表新开实例 = 原版新开 FilmListActivity）——

    @Override
    public void openSubList(HkArticleFragment.SubListRequest req) {
        if (req == null || req.subRule == null) return;
        doOpenSubList(req);
    }

    /**
     * 子页打开本体：照 8.83——先开页、页内加载（取数失败在新页里报错），
     * 宿主不在开页前预抓、不拦点击（旧详情预取闸已整链拆除）。
     */
    private void doOpenSubList(HkArticleFragment.SubListRequest req) {
        Intent intent = new Intent(this, HkListActivity.class);
        putTempRule(intent, new Gson().toJson(req.subRule));
        intent.putExtra("title", req.title);
        intent.putExtra("parentTitle", req.parentTitle);
        intent.putExtra("parentUrl", req.parentUrl);
        intent.putExtra("picUrl", req.picUrl);
        intent.putExtra("fromActivity", req.fromActivity);
        intent.putExtra("fromLastClick", req.fromLastClick);
        intent.putExtra("listHost", true);
        if (req.autoPageData != null) tempParentData = req.autoPageData;
        // 子页参数已由 HkPageParser 写进子规则的 params 字段（可序列化），随规则 JSON 走，
        // 不再走静态槽交接（槽竞态会把参数串给别的页面，1.3.11 审计定案已撤）。
        // 照原版 dealRule：普通子页以请求码 2 在本任务内打开（子页可回传 refreshPage
        // 让本页刷新）；带 newWindow 标记的条目在窗口协议退役后同落本任务——
        // 不带请求码地新开一页（原窗口形态「不经请求码回传」的语义保留），不提
        // 旧页、不开新任务（账 hk-task-single-20261103 订正节）。windowId 只是
        // 条目自带数据、留在请求对象里，不再进 Intent（全树已无读取方）。
        if (req.newWindow) {
            startActivity(intent);
        } else {
            startActivityForResult(intent, 2);
        }
    }

    /**
     * Fragment.toNextPage 的开页接缝（摸鱼日报分类 lazy 返回 hiker://page/type1?fypage 走这里）。
     * 组页照 8.83 toNextPage：HkPageParser.getNextPage(rule, pageUrl, paramsJson) 组子规则，
     * 组不出页（返 null／抛 ParseException）就弹错留本页、绝不拿原规则回落开页——
     * 回落开出来的新页装的是原封不动的首页规则，就是「点二级又重开一个首页」的字面成因（W1）。
     * 开页经 openSubList 标准子页通道（返回栈/标题与现树一致）。
     */
    @Override
    public void openPageUrl(HkRule rule, String pageUrl, int position) {
        com.github.catvod.crawler.SpiderDebug.log("hk", "openPageUrl entry rule=%s pageUrl=%s pos=%d",
                rule == null ? "" : rule.getTitle(), pageUrl, position);
        try {
            String paramsJson = fragment == null ? "" : fragment.getItemExtraJson(position);
            String title = fragment == null ? "" : fragment.getItemTitle(position);
            HkRule next = HkPageParser.getNextPage(rule, pageUrl, paramsJson);
            com.github.catvod.crawler.SpiderDebug.log("hk", "openPageUrl next=%s", next != null);
            if (next == null) {
                // 照 8.83 lambda$toNextPage$35：失败弹错留本页（原文 ToastMgr.shortCenter
                // 弹异常消息），不开任何新页。getNextPage 仅对非 page url 返 null，
                // 本接缝调用方已先过 isPageUrl，此分支为防御性死路，文案走解析器同族口径。
                runOnUiThread(() -> HkRouteBlocker.toastShortBottom(this, "找不到对应的页面"));
                return;
            }
            HkArticleFragment.SubListRequest req = new HkArticleFragment.SubListRequest();
            req.subRule = next;
            req.title = title;
            req.parentTitle = pageTitle;
            req.parentUrl = rule == null ? "" : rule.getUrl();
            req.fromActivity = hashCode();
            // 直接开新页，由新页自己的顶部进度条接管加载（先开页、页内加载）。
            runOnUiThread(() -> doOpenSubList(req));
        } catch (Exception e) {
            Log.e(TAG, "openPageUrl", e);
            runOnUiThread(() -> HkRouteBlocker.toastShortBottom(this, String.valueOf(e.getMessage())));
        }
    }

    /**
     * 插件 JS 直调开页接缝：官 AutoImportHelper.lambda$couldCloudImport$2 的
     * hiker://page/ 支经 PageParser.goPageByPageUrl 以当前规则组页开页；
     * 星辰等价路即本页 openPageUrl（本页当前规则、无条目语境 position=-1，
     * 参数与标题取空——插件拼的 page 地址自带 ?rule= 指名子页）。
     */
    public void openPageUrlFromJs(String pageUrl) {
        openPageUrl(rule, pageUrl, -1);
    }

    /**
     * 照原版 FilmListActivity.onActivityResult 的宿主分支：请求码 2 子页回传 refreshPage → 本页刷新；
     * 0x961 扫码结果 → dealQRContent；0x4e16 文件编辑保存 → 逐个求值登记的回调 JS。
     * Fragment 自有请求码（0x2382/0x2383/0x2385）由 super 分发，不在此处理。
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 2) {
            if (data != null && data.getBooleanExtra("refreshPage", false) && fragment != null) {
                fragment.refreshFromHost(data.getBooleanExtra("scrollTop", false));
            }
            return;
        }
        if (requestCode == 0x961) {
            if (resultCode == RESULT_OK && data != null) {
                // 星辰 ScanActivity 的结果键是 "address"（原版扫码页是 "codedContent"，两侧都认）
                String content = data.getStringExtra("address");
                if (TextUtils.isEmpty(content)) content = data.getStringExtra("codedContent");
                if (!TextUtils.isEmpty(content)) dealQRContent(content);
            }
            return;
        }
        if (requestCode == 0x4e16) {
            if (resultCode == RESULT_OK && data != null
                    && data.getBooleanExtra("saved", false) && !fileEditCallbackJs.isEmpty()) {
                java.util.List<String> callbacks = new java.util.ArrayList<>(fileEditCallbackJs);
                fileEditCallbackJs.clear();
                HkRule currentRule = rule;
                new Thread(() -> {
                    int failed = 0;
                    for (String js : callbacks) {
                        try {
                            com.fongmi.android.tv.api.hk.HkJsRuntime rt =
                                    HkEngineManager.runtimeFor(currentRule);
                            HkEngineManager.ensureInit(rt);
                            rt.evalJS(com.fongmi.android.tv.api.hk.HkJsRuntime.getMyRule(currentRule) + js, "");
                        } catch (Exception e) {
                            failed++;
                            Log.e(TAG, "文件编辑回调 JS 求值失败", e);
                        }
                    }
                    if (failed > 0) {
                        // 回调失败不再静默不回灌（残项处置 2026-11-14）：汇总提示一次。
                        final int failedCount = failed;
                        runOnUiThread(() -> HkRouteBlocker.toastShortBottom(
                                HkListActivity.this,
                                "文件编辑回调执行失败（" + failedCount + " 条），页面可能未更新"));
                    }
                }, "hk-file-edit-callback").start();
            }
        }
    }

    /** 照原版宿主承接 IconOneSearch 的相机权限结果（请求码同为 0x961）：给权后开扫码页，拒绝弹「没有相机权限！」。 */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != 0x961) return;
        boolean granted = grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (granted) {
            startActivityForResult(new Intent(this,
                    com.fongmi.android.tv.ui.activity.ScanActivity.class), 0x961);
        } else {
            HkRouteBlocker.toastShortBottom(this, "没有相机权限！");
        }
    }

    /** 照原版 dealQRContent：http 开头走网页；其余弹内容框（确认键 = 复制全文）。 */
    private void dealQRContent(String content) {
        if (content.startsWith("http")) {
            new HkDetailBridge().goWeb(this, content);
            return;
        }
        new HkDialogs(this)
                .setMessage(content)
                .setPositiveButton("复制", (dialog, which) -> {
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", content));
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * editFile:// 的文件编辑器等价（原版 HomeHtmlEditActivity，请求码 0x4e16 回流）：
     * 读文件全文进编辑框，保存写回后直接在本页走 0x4e16 保存回流（求值登记的回调 JS）。
     */
    public void openFileEditor(String path) {
        String content;
        try {
            content = readFileText(new File(path));
        } catch (Exception e) {
            Log.e(TAG, "hk error", e);
            HkRouteBlocker.toastShortBottom(this, "无法打开文件：" + path);
            return;
        }
        EditText edit = new EditText(this);
        edit.setText(content);
        edit.setTextColor(0xFF222222);
        edit.setTextSize(13f);
        edit.setTypeface(android.graphics.Typeface.MONOSPACE);
        edit.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        int pad = (int) (16 * getResources().getDisplayMetrics().density + 0.5f);
        edit.setPadding(pad, pad, pad, pad);
        new HkDialogs(this)
                .setTitle(new File(path).getName())
                .setView(edit)
                .setPositiveButton("保存", (dialog, which) -> {
                    try (java.io.FileOutputStream out = new java.io.FileOutputStream(path)) {
                        out.write(edit.getText().toString().getBytes(StandardCharsets.UTF_8));
                    } catch (Exception e) {
                        Log.e(TAG, "hk error", e);
                        HkRouteBlocker.toastShortBottom(this, "保存失败：" + e.getMessage());
                        return;
                    }
                    onActivityResult(0x4e16, RESULT_OK, new Intent().putExtra("saved", true));
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 对应原版 HighLightViewActivity 看代码（标题固定「代码」）：只读滚动文本弹窗等价。 */
    @Override
    public void openCodeView(String code) {
        TextView text = new TextView(this);
        text.setText(code == null ? "" : code);
        text.setTextSize(13f);
        text.setTextColor(0xFF222222);
        text.setTypeface(android.graphics.Typeface.MONOSPACE);
        int pad = (int) (16 * getResources().getDisplayMetrics().density + 0.5f);
        text.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(text);
        new HkDialogs(this)
                .setTitle("代码")
                .setView(scroll)
                .setPositiveButton("关闭", null)
                .show();
    }

    /** 行内高亮编辑等价：可编辑文本弹窗，确定后回填（原版经 0x2382 结果槽回填，机能等价）。 */
    @Override
    public void openHighlightEditor(String text, Consumer<String> callback) {
        EditText edit = new EditText(this);
        edit.setText(text == null ? "" : text);
        edit.setTextColor(0xFF222222);
        edit.setTextSize(14f);
        int pad = (int) (16 * getResources().getDisplayMetrics().density + 0.5f);
        edit.setPadding(pad, pad, pad, pad);
        new HkDialogs(this)
                .setTitle("编辑")
                .setView(edit)
                .setPositiveButton("确定", (dialog, which) -> {
                    if (callback != null) callback.accept(edit.getText().toString());
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // —— HostListener ——

    @Override
    public void onChildChanged(HkArticleFragment fragment, boolean hasWebView) {
        childHasWebView = hasWebView;
    }

    @Override
    public void hideLoading() {
        // 本宿主无独立加载指示（Fragment 自带进度线），对应原版 hideLoading 的空等价
    }

    /** 续看位置记录：以（页面规则 url + 页面标题）为键存「条目标题@@下标」，供本页重开时恢复。 */
    @Override
    public void onMemoryPosition(int position) {
        if (fragment == null || rule == null) return;
        HkItem item = fragment.getItemAt(position);
        if (item == null) return;
        putLastClick(rule.getUrl(), pageTitle, item.getTitle() + "@@" + position);
    }

    /**
     * 照原版 background() 的非窗口支：同任务压栈形态下没有任何页面带
     * NEW_TASK，原版对这类页面的回答就是「当前页面不支持退到后台」——窗口
     * 支（moveTaskToBack 退整任务）随窗口协议一并退役（账
     * hk-task-single-20261103 订正节）。用户按 Home 切后台是系统行为、与本
     * 方法无关，回来栈顶仍是本页。
     */
    @Override
    public void onBackground() {
        HkRouteBlocker.toastShortBottom(this, "当前页面不支持退到后台");
    }

    @Override
    public String onQueryLastClick(String ruleUrl, String title) {
        return getLastClick(ruleUrl, title);
    }

    /** 对应原版 genLastClickText + 上次点击提示：取 "@@" 前标题文本作底部短提示。 */
    @Override
    public void onShowLastClick(String lastClick) {
        if (TextUtils.isEmpty(lastClick)) return;
        String text = HkStringUtil.getTitleText(lastClick.split("@@")[0]);
        if (!TextUtils.isEmpty(text)) HkRouteBlocker.toastShortBottom(this, text);
    }

    /** #autoPage# 续页时把父页的续看记录推进到当前章节（键 = 父页 url + 父页标题）。 */
    @Override
    public void onAutoPageLastClick(HkArticleFragment.AutoPageData data, HkItem item, int globalPos) {
        if (data == null || item == null) return;
        putLastClick(data.url, data.title, item.getTitle() + "@@" + globalPos);
    }

    // —— 续看记录存储（原版 ViewCollection/ViewHistory 的星辰等价，键序与原版查询一致：url + title）——

    private static String lastClickKey(String ruleUrl, String pageTitle) {
        return (ruleUrl == null ? "" : ruleUrl) + "\n" + (pageTitle == null ? "" : pageTitle);
    }

    private static void putLastClick(String ruleUrl, String pageTitle, String value) {
        try {
            App.get().getSharedPreferences(PREF_LAST_CLICK, Context.MODE_PRIVATE)
                    .edit().putString(lastClickKey(ruleUrl, pageTitle), value).apply();
        } catch (Exception ignored) {
        }
    }

    private static String getLastClick(String ruleUrl, String pageTitle) {
        try {
            SharedPreferences sp = App.get().getSharedPreferences(PREF_LAST_CLICK, Context.MODE_PRIVATE);
            String value = sp.getString(lastClickKey(ruleUrl, pageTitle), null);
            if (value != null) return value;
            if (!TextUtils.isEmpty(pageTitle)) return null;
            // 标题缺失时按 url 前缀兜底取一条（原版查询在标题为空时同样放宽）
            String prefix = (ruleUrl == null ? "" : ruleUrl) + "\n";
            for (Map.Entry<String, ?> entry : sp.getAll().entrySet()) {
                if (entry.getKey().startsWith(prefix) && entry.getValue() instanceof String) {
                    return (String) entry.getValue();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // —— tempRule 缓存文件（原版 DataTransferUtils "tempRule" 的等价兜底）——

    private static File tempRuleCacheFile() {
        return new File(App.get().getCacheDir(), "hk_temp_rule.txt");
    }

    private static void writeTempRuleCache(String key, String ruleJson) {
        // 用流而非 java.nio.file.Files：minSdk 24 的设备上后者不存在（API 26 才有）
        // 文件首行写规则指纹，读时核对，防跨规则认领（旧格式无指纹行自然核不上、作废一次自愈）
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(tempRuleCacheFile())) {
            out.write((key + "\n" + ruleJson).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    private static String readTempRuleCache(String expectedKey) {
        try {
            File file = tempRuleCacheFile();
            if (!file.exists() || expectedKey == null) return null;
            String text = readFileText(file);
            int nl = text.indexOf('\n');
            if (nl < 0) return null;
            if (!expectedKey.equals(text.substring(0, nl).trim())) return null;
            return text.substring(nl + 1);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String readFileText(File file) throws Exception {
        byte[] bytes = new byte[(int) file.length()];
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            int offset = 0;
            int read;
            while (offset < bytes.length && (read = in.read(bytes, offset, bytes.length - offset)) > 0) {
                offset += read;
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // ================= 顶部小程序切换 tabs（星辰自有功能，自旧内容页 HkContentFragment 原样迁入） =================

    /**
     * 内容页顶部小程序切换 tabs：横向显示已安装的小程序，点击切换规则。
     * 新架构下切换 = 以新页打开目标规则并关掉本页（返回栈回小程序首页，与开页导航一致）。
     * 仅规则首页级（listHost=false）显示；子列表级保持 8.83 原版页眉，不挂此栏。
     */
    private void buildMiniappTabs() {
        View row = findViewById(R.id.hk_miniapp_tabs_row);
        if (row == null) return;
        if (getIntent().getBooleanExtra("listHost", false)) {
            row.setVisibility(View.GONE);
            return;
        }
        // 液态玻璃：页签条换玻璃悬浮条（圆角 12dp）；选中奶油胶囊＋金下划线已随 hk_chip_selected 自动生效
        row.setBackground(new HkGlassDrawable(this, 12));
        try {
            List<HkRule> rules = HkRuleManager.get().getRules();
            java.util.List<HkRule> enabled = new java.util.ArrayList<>();
            for (HkRule r : rules) {
                // 标题为空（脏数据已在 getTitle 清洗）的规则不展示，避免空白 tab；
                // "我的主页"与普通规则同列展示（环41 放行，见 HkPageActivity.openRule 注）
                if (r.isEnabled() && !r.getTitle().isEmpty()) enabled.add(r);
            }
            // 当前规则若不在 tabs 列表里（未启用或刚导入），追加进去并选中，保证 tabs 跟随当前页面
            HkRule cur = rule;
            String curTitle = cur == null ? null : cur.getTitle();
            boolean hasCur = false;
            if (curTitle != null) {
                for (HkRule r : enabled) {
                    if (curTitle.equals(r.getTitle())) {
                        hasCur = true;
                        break;
                    }
                }
            }
            if (!hasCur && cur != null) {
                enabled.add(cur);
            }
            miniappRules.clear();
            miniappRules.addAll(enabled);
            if (enabled.size() <= 1) {
                row.setVisibility(View.GONE);
                return;
            }
            java.util.List<String[]> pairs = new java.util.ArrayList<>();
            int sel = 0;
            for (int i = 0; i < enabled.size(); i++) {
                HkRule r = enabled.get(i);
                pairs.add(new String[]{r.getTitle(), String.valueOf(i)});
                if (curTitle != null && curTitle.equals(r.getTitle())) sel = i;
            }
            final java.util.List<HkRule> finalEnabled = enabled;
            MiniappChipAdapter adapter = new MiniappChipAdapter(pairs, sel, idx -> {
                try {
                    int i = Integer.parseInt(idx);
                    if (i >= 0 && i < finalEnabled.size()) {
                        HkRule target = finalEnabled.get(i);
                        if (rule == null || !target.getTitle().equals(rule.getTitle())) {
                            switchRule(target);
                        }
                    }
                } catch (Throwable ignored) {
                }
            });
            RecyclerView tabsRv = findViewById(R.id.hk_rv_miniapp_tabs);
            tabsRv.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            // 横向 tab 行不抢垂直滚动：避免嵌套滚动冲突
            tabsRv.setHasFixedSize(true);
            tabsRv.setNestedScrollingEnabled(false);
            tabsRv.setAdapter(adapter);
            // 滚动到选中项，保证当前小程序的 tab 可见
            final int finalSel = sel;
            tabsRv.post(() -> {
                try {
                    ((LinearLayoutManager) tabsRv.getLayoutManager())
                            .scrollToPositionWithOffset(finalSel, 0);
                } catch (Throwable ignored) {
                }
            });
            View all = findViewById(R.id.hk_btn_miniapp_all);
            if (all != null) all.setOnClickListener(v -> showMiniappSwitchDialog());
            row.setVisibility(View.VISIBLE);
        } catch (Throwable t) {
            row.setVisibility(View.GONE);
        }
    }

    /** 切规则：新页打开目标规则、关掉本页（新架构的开页导航语义）。 */
    private void switchRule(HkRule target) {
        if (target == null) return;
        startRule(this, target, target.getTitle());
        finish();
    }

    /**
     * 小程序切换：顶部 tabs 栏最右「全部」按钮弹窗。
     * 星辰风格：毛玻璃底 + 独立选项卡片。8.83 内容页无此功能。
     */
    private void showMiniappSwitchDialog() {
        if (miniappRules.size() <= 1) return;
        final String curTitle = rule == null ? null : rule.getTitle();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.xc_dialog_light);
        int pad = dp(16);
        card.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("选择小程序");
        title.setTextColor(0xFF1A1D24);
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new GridLayoutManager(this, 2));
        rv.setClipToPadding(false);
        int rows = (miniappRules.size() + 1) / 2;
        int gridH = Math.min(rows * dp(66), dp(360));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, gridH);
        rlp.topMargin = dp(12);
        card.addView(rv, rlp);

        final AlertDialog[] holder = new AlertDialog[1];
        rv.setAdapter(new MiniappSwitchAdapter(miniappRules, curTitle, target -> {
            if (holder[0] != null) holder[0].dismiss();
            if (target != null && (rule == null || !target.getTitle().equals(rule.getTitle()))) {
                switchRule(target);
            }
        }));

        FrameLayout outer = new FrameLayout(this);
        int m = dp(24);
        outer.setPadding(m, 0, m, 0);
        outer.addView(card, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        AlertDialog dialog = new HkDialogs(this)
                .setView(outer).create();
        holder[0] = dialog;
        // 浅色卡片自成一面：清掉窗口暗色玻璃底，不许黑层从卡片边缘探出
        HkDialogs.clearWindowBackground(dialog);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 小程序切换 tabs 的横向 pill 适配器（旧内容页 ChipAdapter 的金色模式原样）。 */
    private static class MiniappChipAdapter extends RecyclerView.Adapter<MiniappChipAdapter.Holder> {
        interface OnPick {
            void onPick(String value);
        }

        private final java.util.List<String[]> pairs;
        private int selected;
        private final OnPick listener;

        MiniappChipAdapter(java.util.List<String[]> pairs, int selected, OnPick listener) {
            this.pairs = pairs;
            this.selected = selected;
            this.listener = listener;
        }

        static class Holder extends RecyclerView.ViewHolder {
            TextView chip;
            com.google.android.material.card.MaterialCardView card;

            Holder(View v) {
                super(v);
                chip = v.findViewById(R.id.tv_chip);
                android.view.View p = (android.view.View) chip.getParent();
                card = p instanceof com.google.android.material.card.MaterialCardView
                        ? (com.google.android.material.card.MaterialCardView) p : null;
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hk_chip, parent, false);
            RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) v.getLayoutParams();
            lp.rightMargin = (int) (8 * parent.getContext().getResources().getDisplayMetrics().density + 0.5f);
            v.setLayoutParams(lp);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            String[] pair = pairs.get(position);
            h.chip.setText(pair[0]);
            boolean sel = position == selected;
            h.chip.setSelected(sel);
            // 金色主题：小程序切换 tabs 在圆角框内，透明底 + 金色系文字，与分类 pills 区分
            if (h.card != null) {
                h.card.setCardBackgroundColor(android.graphics.Color.TRANSPARENT);
                h.card.setStrokeWidth(0);
            }
            // v5 基准：选中=奶油胶囊＋金下划线，未选中透明
            h.chip.setBackgroundResource(sel ? R.drawable.hk_chip_selected : 0);
            h.chip.setTypeface(null, sel ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            h.chip.setTextColor(sel ? 0xFF8A6D00 : 0xFF8A8F98);
            h.chip.setOnClickListener(v -> {
                int pos = h.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                selected = pos;
                notifyDataSetChanged();
                if (listener != null) listener.onPick(pairs.get(pos)[1]);
            });
        }

        @Override
        public int getItemCount() {
            return pairs.size();
        }
    }

    /** 「全部」弹窗的 2 列选项卡片适配器：独立卡片框、有间距，当前选中高亮（旧内容页原样）。 */
    private static class MiniappSwitchAdapter extends RecyclerView.Adapter<MiniappSwitchAdapter.Holder> {
        interface OnPick {
            void onPick(HkRule rule);
        }

        private final java.util.List<HkRule> rules;
        private final String curTitle;
        private final OnPick listener;

        MiniappSwitchAdapter(java.util.List<HkRule> rules, String curTitle, OnPick listener) {
            this.rules = rules;
            this.curTitle = curTitle;
            this.listener = listener;
        }

        static class Holder extends RecyclerView.ViewHolder {
            TextView name;

            Holder(View v) {
                super(v);
                name = (TextView) v;
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            android.content.Context ctx = parent.getContext();
            float d = ctx.getResources().getDisplayMetrics().density;
            TextView tv = new TextView(ctx);
            tv.setBackgroundResource(R.drawable.dialog_option_card_light);
            tv.setGravity(Gravity.CENTER);
            tv.setMaxLines(1);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tv.setTextSize(15);
            int p = (int) (14 * d + 0.5f);
            tv.setPadding(p, p, p, p);
            GridLayoutManager.LayoutParams lp = new GridLayoutManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int mg = (int) (6 * d + 0.5f);
            lp.setMargins(mg, mg, mg, mg);
            tv.setLayoutParams(lp);
            return new Holder(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            HkRule r = rules.get(position);
            h.name.setText(r.getTitle());
            boolean sel = curTitle != null && curTitle.equals(r.getTitle());
            h.name.setTextColor(sel ? 0xFFD9A420 : 0xFF1A1D24);
            h.name.setTypeface(null, sel ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            h.name.setOnClickListener(v -> {
                if (listener != null) listener.onPick(r);
            });
        }

        @Override
        public int getItemCount() {
            return rules.size();
        }
    }
}
