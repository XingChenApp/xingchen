package com.fongmi.android.tv.ui.hk;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.hk.HkItem;
import com.github.catvod.crawler.SpiderDebug;
import com.fongmi.android.tv.api.hk.HkJsRuntime;
import com.fongmi.android.tv.api.hk.HkLazyRuleParser;
import com.fongmi.android.tv.api.hk.HkParserKit;
import com.fongmi.android.tv.api.hk.HkRouter;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.ui.hk.view.HkCenterListAdapter;
import com.fongmi.android.tv.ui.hk.view.HkCenterListPopup;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

import android.util.Log;

/**
 * DetailUIHelper（smali_classes2/ui/detail/DetailUIHelper.smali）的星辰实现：
 * dealUrlSimply 分支顺序与语义逐条照抄；getCodeAndHeader / getActivityTitle / showInput 一并转写。
 * XPopup 系弹窗（input/confirm/select）按本工程先例以原生 Dialog/PopupWindow 承载，内容与回调链不变；
 * 规则导入、网页、播放器、迅雷、下载对话框等宿主子系统走 HostHooks，宿主阶段绑定真实现。
 */
public class HkDetailBridge implements HkArticleFragment.DetailBridge {
    private static final String TAG = "HkDetailBridge";

    /** 原版 HeavyTaskUtil.executeNewTask 的等价后台池。 */
    private static final ExecutorService BG = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "hk-detail-task");
        t.setDaemon(true);
        return t;
    });

    /** 当前列表页活动供给（宿主注入，与 HkEngineBridge.activityProvider 同源），供无 Context 入参的接缝使用。 */
    public static volatile Supplier<Activity> activityProvider;

    /** 原版 OnRefreshWebViewEvent 的等价事件（x5WebView:// 支）：行内 WebView 宿主订阅后刷新目标页。 */
    public static class RefreshWebViewEvent {
        public final String url;

        public RefreshWebViewEvent(String url) {
            this.url = url;
        }
    }

    /** editFile:// 的 ArticleListIsland 等价面：宿主活动实现后可登记 js 回调。 */
    public interface FileEditCallbackHost {
        void addFileEditCallbackJs(String js);
    }

    /** 宿主子系统钩子（未绑定时为默认实现：网页走系统浏览器，其余静默不做）。 */
    public interface HostHooks {
        /** AutoImportHelper.checkText：识别口令/JSON 文本并导入（返回值原版在 code:// 支被忽略）。 */
        default boolean checkText(Activity activity, String text) {
            return false;
        }

        /** AutoImportHelper.checkAutoText：识别海阔视界口令文本，成功返回 true。 */
        default boolean checkAutoText(Context context, String text) {
            return false;
        }

        /**
         * WebUtil.goWeb 两形（环29 D29-1）：栈内已有网页承载（HkWebCarrier 登记
         * 的 HkWebViewHolder WebView）→ 回该 WebView loadUrl，脚本在既有页面
         * DOM 执行（javascript: 就地求值落点）；无承载才新开行内 x5 页
         * （HkListActivity.openX5PlayPage 同构）。旧形恒新开页：javascript: 在
         * 新页 about:blank 求值、与官终态分叉。非 Activity 上下文回退系统
         * 浏览器（javascript: 在该路上无解，形态差记名——官恒在自家页执行）。
         */
        default void goWeb(Context context, String url) {
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

        /** WebUtil.goWeb 带 extraData 版。 */
        default void goWebWithExtraData(Context context, String url, Bundle bundle) {
            goWeb(context, url);
        }

        /** ShareUtil.findChooserToSend：分享本地文件（入参为绝对路径）。 */
        default void shareFile(Context context, String absolutePath) {
        }

        /** HomeHtmlEditActivity：编辑文件页（原版 startActivityForResult 0x4e16）。 */
        default void openFileEditor(Activity activity, String existPath) {
        }

        /** DownloadRecordsFragment.checkDownload。 */
        default void checkDownloadedFile(File file) {
        }

        /** DownloadDialogUtil.showEditDialog（download:// 与 apk 支）。headers 为地址 ;{...} 块解析出的请求头，可为 null。 */
        default void showDownloadDialog(Activity activity, String title, String url, String film, java.util.Map<String, String> headers) {
        }

        /** 批量快速下载入队（原版按 defaultDownloader 偏好选下载器直接建任务）。 */
        default void startFastDownload(Context context, String title, String url, String film) {
        }

        /** PlayerChooser 单集直达。 */
        default void startPlayerSingle(Activity activity, String title, String url) {
        }

        /** PlayerChooser 选集交接。 */
        default void startPlayerChapters(Activity activity, List<HkArticleFragment.HkChapter> chapters,
                                         String pageUrl, String mTitle, Bundle extraData) {
        }

        /** PictureListActivity 图集页。 */
        default void openPictureList(Activity activity, HkArticleFragment.PictureListRequest request) {
        }

        default void showImageViewer(List<String> urls, int selectPos, Map<Integer, Integer> posMap, ImageView srcView) {
        }

        default void showSingleImage(String url, ImageView srcView) {
        }

        /** ThunderManager 两参版。 */
        default void startDownloadMagnet(Context context, String url) {
        }

        default void startParseTorrent(Context context, String url) {
        }

        default void startParseFTPOrEd2k(Context context, String url, Consumer<String> consumer) {
        }

        /** ThunderManager 带选集列表三参版。 */
        default void startDownloadMagnetList(Context context, String url, List<HkArticleFragment.HkChapter> chapters) {
        }

        default void startParseTorrentList(Context context, String url, List<HkArticleFragment.HkChapter> chapters) {
        }

        default void startParseFTPOrEd2kList(Context context, String url, List<HkArticleFragment.HkChapter> chapters) {
        }

        /** ShareRuleUtil.shareRule。 */
        default void shareRule(HkRule rule, boolean moreWays) {
        }

        /** FileDetailPopup：条目信息详情。 */
        default void showPosDetail(Activity activity, HkItem item, HkRule rule, int position) {
        }

        /** 批量下载/投屏收尾。 */
        default void onBatchModeFinish(String mode, int count) {
        }
    }

    public static volatile HostHooks hooks = new HostHooks() {
    };

    private static HostHooks hooks() {
        HostHooks h = hooks;
        return h != null ? h : new HostHooks() {
        };
    }

    // ------------------------------------------------------------------
    // DetailBridge：路由 / 简单链接 / 工具
    // ------------------------------------------------------------------

    @Override
    public boolean isRoute(Activity activity, String url, HkRule rule, HkItem item) {
        return HkRouteBlocker.isRoute(activity, url, rule, item);
    }

    /** 照抄 DetailUIHelper.getCodeAndHeader（smali:1850）。 */
    @Override
    public String getCodeAndHeader(String urlWithUa, String[] lazyParts) {
        String url = urlWithUa == null ? "" : urlWithUa;
        String[] parts = url.split(";");
        String codeAndHeader = "";
        if (parts.length > 1) {
            codeAndHeader = ";" + HkParserKit.arrayToString(parts, 1, ";");
            codeAndHeader = codeAndHeader.replace(";post", ";get").replace(";POST", ";get");
        }
        if (lazyParts != null && lazyParts.length > 1 && codeAndHeader.isEmpty()) return ";get";
        return codeAndHeader;
    }

    /** 照抄 DetailUIHelper.getActivityTitle（smali:1813）。 */
    public static String getActivityTitle(Activity activity) {
        if (activity == null) return "";
        String title = activity.getIntent() == null ? null : activity.getIntent().getStringExtra("title");
        return TextUtils.isEmpty(title) ? "" : title;
    }

    @Override
    public String getTitleText(String title) {
        return HkStringUtil.getTitleText(title);
    }

    /**
     * 照抄 DetailUIHelper.getViewCollectionExtraData（smali:2306）的宿主面：
     * 星辰无 ViewCollection 子系统（原版仅当宿主为 FilmListActivity 且条目开了自定义跳转/播放器时才有值），恒回 null。
     */
    @Override
    public String getViewCollectionExtraData(Activity activity, String ruleUrl) {
        return null;
    }

    @Override
    public boolean isImage(String url) {
        return HkUrlDetector.isImage(url);
    }

    @Override
    public boolean isVideoOrMusic(String url) {
        return HkUrlDetector.isVideoOrMusic(url);
    }

    @Override
    public String clearTag(String url) {
        return HkUrlDetector.clearTag(url);
    }

    @Override
    public String getFirstPageUrl(String url) {
        return HkStringUtil.getFirstPageUrl(url);
    }

    @Override
    public String getHomeUrl(String url) {
        return HkStringUtil.getHomeUrl(url);
    }

    @Override
    public String autoFixUrl(String base, String url) {
        return HkStringUtil.autoFixUrl(base, url);
    }

    /** 官 ThunderManager.isMagnetOrTorrent 的桥面：直调 HkRouter 正表（全树判据实现只此一份，不存手写副本）。 */
    @Override
    public boolean isMagnetOrTorrent(String url) {
        return HkRouter.isMagnetOrTorrent(url);
    }

    /** 照抄 ThunderManager.isFTPOrEd2k（Kotlin）：(ftp: 且为视频/音乐) 或 ed2k:。 */
    @Override
    public boolean isFTPOrEd2k(String url) {
        if (url == null || url.length() == 0) return false;
        return (url.startsWith("ftp:") && HkUrlDetector.isVideoOrMusic(url)) || url.startsWith("ed2k:");
    }

    @Override
    public boolean isCannotHandleScheme(String url) {
        return HkStringUtil.isCannotHandleScheme(url);
    }

    @Override
    public boolean isScheme(String url) {
        return HkStringUtil.isScheme(url);
    }

    /** 原版同名调用为 android.webkit.MimeTypeMap.getFileExtensionFromUrl，直委托。 */
    @Override
    public String getFileExtensionFromUrl(String url) {
        if (url == null) return "";
        String ext = android.webkit.MimeTypeMap.getFileExtensionFromUrl(url);
        return ext == null ? "" : ext;
    }

    @Override
    public void toastBottom(String text) {
        HkRouteBlocker.toastShortBottom(App.get(), text);
    }

    // ------------------------------------------------------------------
    // DetailBridge：宿主钩子直转
    // ------------------------------------------------------------------

    @Override
    public void showDownloadDialog(Activity activity, String title, String url, String film, java.util.Map<String, String> headers) {
        hooks().showDownloadDialog(activity, title, url, film, headers);
    }

    @Override
    public void startFastDownload(Context context, String title, String url, String film) {
        hooks().startFastDownload(context, title, url, film);
    }

    /** 照 8.83 DownloadDialogUtil.System_DOWNLOAD 后缀表（30 项，clinit 逐项提取）。 */
    private static final String[] SYSTEM_DOWNLOAD = {
            ".epub", ".zip", ".rar", ".txt", ".exe", ".pdf", ".doc", ".wps", ".ttf", ".otf",
            ".sfnt", ".woff2", ".woff", ".gz", ".7z", ".docx", ".ppt", ".pptx", ".ass", ".srt",
            ".vtt", ".xls", ".xlsx", ".tar", ".hiker", ".json", ".apk", ".msi", ".torrent", ".md"};

    /**
     * 照 8.83 DownloadDialogUtil.useSystemDownload(title, url)：标题以系统下载后缀结尾 → true；
     * 否则 url 非视频/音乐时取其 "/" 末段判后缀。供长按「下载资源」的格式守卫用。
     */
    public static boolean useSystemDownload(String title, String url) {
        if (HkStringUtil.isNotEmpty(title)) {
            for (String ext : SYSTEM_DOWNLOAD) {
                if (title.endsWith(ext)) return true;
            }
        }
        if (HkStringUtil.isNotEmpty(url) && !HkUrlDetector.isVideoOrMusic(url)) {
            String[] parts = url.split("/");
            String last = parts.length == 0 ? "" : parts[parts.length - 1];
            for (String ext : SYSTEM_DOWNLOAD) {
                if (last.endsWith(ext)) return true;
            }
        }
        return false;
    }

    // —— WebView 嗅探解析：直转 HkX5WebViewParser / HkWebkitParser（照原版 ArticleListFragment 直调 Parser 静态法）——

    @Override
    public boolean canParseX5(String url) {
        return HkX5WebViewParser.canParse(url);
    }

    @Override
    public boolean canParseWebkit(String url) {
        return HkWebkitParser.canParse(url);
    }

    @Override
    public boolean parseX5(Activity activity, String url, String extraJson, Consumer<String> consumer) {
        return parseWithTimeout(activity, consumer,
                c -> HkX5WebViewParser.parse0(activity, url, extraJson, c),
                HkX5WebViewParser::destroy0);
    }

    @Override
    public boolean parseWebkit(Activity activity, String url, String extraJson, Consumer<String> consumer) {
        return parseWithTimeout(activity, consumer,
                c -> HkWebkitParser.parse0(activity, url, extraJson, c),
                HkWebkitParser::destroy0);
    }

    // —— 嗅探解析有界守卫（壳子侧，2026-11-01 BestJav8 点播放卡死案）——
    // 原解析链（照 8.83）只有页面内轮询脚本自带约 30 秒上限，且要等目标页
    // onPageFinished 注入后才开始计；页面加载不完时轮询根本不启动，Java 侧
    // 从解析器到列表页又没有任何超时，等待方（不可取消的「动态解析规则中」
    // 弹窗）只能等桥回调，回调不来即无限挂（真机实证：进入 x5parse 分支后
    // 21 秒进程被终结，日志再无下文）。此处给两条嗅探统一加总时长帽：
    // 超时按规则体系自带的失败出口收尾——销毁解析器＋以空结果回调用方
    // （与页面轮询耗尽后 finishParse("") 的下游状态逐点一致），迟到的真
    // 回调被 fired 拦下丢弃（解析器侧 ticket 校验另有一道）。
    private static final long SNIFF_PARSE_TIMEOUT_MS = 45_000L;
    private static final Handler SNIFF_HANDLER = new Handler(Looper.getMainLooper());

    private boolean parseWithTimeout(Activity activity, Consumer<String> consumer,
                                     java.util.function.Function<Consumer<String>, Boolean> starter,
                                     Runnable destroyer) {
        final java.util.concurrent.atomic.AtomicBoolean fired =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        Consumer<String> guarded = result -> {
            if (fired.compareAndSet(false, true)) consumer.accept(result);
        };
        boolean started = Boolean.TRUE.equals(starter.apply(guarded));
        if (!started) return false;
        SNIFF_HANDLER.postDelayed(() -> {
            if (!fired.compareAndSet(false, true)) return;
            try {
                destroyer.run();
            } catch (Throwable ignored) {
            }
            SpiderDebug.log("hk", "sniff parse timeout branch-guard fired");
            if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                consumer.accept("");
                HkRouteBlocker.toastShortBottom(activity, "解析失败，链接为空");
            }
        }, SNIFF_PARSE_TIMEOUT_MS);
        return true;
    }

    @Override
    public void startPlayerSingle(Activity activity, String title, String url) {
        hooks().startPlayerSingle(activity, title, url);
    }

    @Override
    public void startPlayerChapters(Activity activity, List<HkArticleFragment.HkChapter> chapters,
                                    String pageUrl, String mTitle, Bundle extraData) {
        hooks().startPlayerChapters(activity, chapters, pageUrl, mTitle, extraData);
    }

    @Override
    public void openPictureList(Activity activity, HkArticleFragment.PictureListRequest request) {
        hooks().openPictureList(activity, request);
    }

    @Override
    public void showImageViewer(List<String> urls, int selectPos, Map<Integer, Integer> posMap, ImageView srcView) {
        hooks().showImageViewer(urls, selectPos, posMap, srcView);
    }

    @Override
    public void showSingleImage(String url, ImageView srcView) {
        hooks().showSingleImage(url, srcView);
    }

    @Override
    public void goWeb(Context context, String url) {
        hooks().goWeb(context, url);
    }

    @Override
    public void goWebWithExtraData(Context context, String url, Bundle bundle) {
        hooks().goWebWithExtraData(context, url, bundle);
    }

    /** 照 ShareUtil.findChooserToDeal：ACTION_VIEW 拉起系统选择器处理。 */
    @Override
    public void findChooserToDeal(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(Intent.createChooser(intent, null));
        } catch (Exception e) {
            Log.e(TAG, "hk error", e);
        }
    }

    @Override
    public void startParseTorrent(Context context, String url) {
        hooks().startParseTorrent(context, url);
    }

    @Override
    public void startParseFTPOrEd2k(Context context, String url, Consumer<String> consumer) {
        hooks().startParseFTPOrEd2k(context, url, consumer);
    }

    @Override
    public void startDownloadMagnet(Context context, String url) {
        hooks().startDownloadMagnet(context, url);
    }

    @Override
    public void startDownloadMagnetList(Context context, String url, List<HkArticleFragment.HkChapter> chapters) {
        hooks().startDownloadMagnetList(context, url, chapters);
    }

    @Override
    public void startParseTorrentList(Context context, String url, List<HkArticleFragment.HkChapter> chapters) {
        hooks().startParseTorrentList(context, url, chapters);
    }

    @Override
    public void startParseFTPOrEd2kList(Context context, String url, List<HkArticleFragment.HkChapter> chapters) {
        hooks().startParseFTPOrEd2kList(context, url, chapters);
    }

    @Override
    public void shareRule(HkRule rule, boolean moreWays) {
        hooks().shareRule(rule, moreWays);
    }

    @Override
    public void showPosDetail(Activity activity, HkItem item, HkRule rule, int position) {
        hooks().showPosDetail(activity, item, rule, position);
    }

    @Override
    public void onBatchModeFinish(String mode, int count) {
        hooks().onBatchModeFinish(mode, count);
    }

    /** 照 CustomCopyPopup 的等价实现：弹窗展示全文 + 一键复制（需活动上下文，取自 activityProvider）。 */
    @Override
    public void showCopyPopup(String text) {
        Activity activity = activityProvider == null ? null : activityProvider.get();
        if (activity == null) {
            copyToClipboardForce(App.get(), text);
            HkRouteBlocker.toastShortBottom(App.get(), "已复制到剪贴板");
            return;
        }
        TextView content = new TextView(activity);
        content.setText(text);
        content.setTextIsSelectable(true);
        int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad / 2, pad, 0);
        new HkDialogs(activity)
                .setView(content)
                .setPositiveButton("复制", (d, w) -> copyToClipboardForce(activity, text))
                .setNegativeButton("取消", null)
                .show();
    }

    // ------------------------------------------------------------------
    // DetailUIHelper.dealUrlSimply（7 参版，smali:54）逐条转写
    // ------------------------------------------------------------------

    @Override
    public boolean dealUrlSimply(Activity activity, View view, HkRule rule, HkItem item, String url,
                                 Consumer<String> resultConsumer, Consumer<String> magnetConsumer) {
        if (HkStringUtil.isEmpty(url)) {
            HkRouteBlocker.toastShortBottom(activity, "链接为空！");
            return true;
        }
        if (url.startsWith("code://")) {
            String body = HkStringUtil.replaceOnce(url, "code://", "").trim();
            if (body.length() > 6 && !"海阔视界".equals(body.substring(1, 5))) {
                body = body.substring(1);
            }
            hooks().checkText(activity, body);
            return true;
        }
        Log.d(TAG, String.format("dealWithUrl: %s", url));
        String lower = url.toLowerCase();
        if (lower.startsWith("toast://")) {
            HkRouteBlocker.toastShortBottom(activity,
                    HkStringUtil.replaceOnceIgnoreCase(url, "toast://", ""));
            return true;
        }
        if (url.startsWith("web://")) {
            hooks().goWeb(activity, HkStringUtil.getFirstPageUrl(
                    HkStringUtil.replaceOnce(url, "web://", "")));
            return true;
        }
        if (url.startsWith("share://")) {
            String path = HkJsRuntime.getFilePath(url.replace("share://", ""));
            File file = new File(path);
            if (!file.exists()) {
                HkRouteBlocker.toastShortBottom(activity, "文件不存在" + file.getAbsolutePath());
            } else {
                hooks().shareFile(activity, file.getAbsolutePath());
            }
            return true;
        }
        if ("hiker://empty".equals(lower)) {
            return true;
        }
        if (url.startsWith("x5WebView://")) {
            EventBus.getDefault().post(new RefreshWebViewEvent(
                    HkStringUtil.replaceOnceIgnoreCase(url, "x5WebView://", "")));
            return true;
        }
        if (url.startsWith("input://")) {
            SpiderDebug.log("hk", "itemClick scheme=input:// urlLen=%d", url.length());
            dealInput(activity, view, rule, item, url, resultConsumer);
            return true;
        }
        if (url.startsWith("confirm://")) {
            dealConfirm(activity, view, rule, item, url, resultConsumer);
            return true;
        }
        if (url.startsWith("select://")) {
            dealSelect(activity, view, rule, item, url, resultConsumer);
            return true;
        }
        if (url.startsWith("copy://")) {
            dealCopy(activity, view, rule, item, url, resultConsumer);
            return true;
        }
        if (url.startsWith("editFile://")) {
            dealEditFile(activity, url);
            return true;
        }
        if (url.startsWith("openFile://")) {
            dealOpenFile(activity, url);
            return true;
        }
        if (lower.startsWith("rule://") || url.startsWith("海阔视界")) {
            if (url.startsWith("海阔视界")) {
                hooks().checkAutoText(activity, url);
                return true;
            }
            try {
                String stripped = HkStringUtil.replaceOnceIgnoreCase(url, "rule://", "");
                String decoded = new String(Base64.decode(
                        HkStringUtil.replaceLineBlank(stripped), Base64.URL_SAFE));
                if (HkStringUtil.isEmpty(decoded) || !hooks().checkAutoText(activity, decoded)) {
                    HkRouteBlocker.toastShortBottom(activity, "获取小程序口令失败");
                }
            } catch (Exception e) {
                HkRouteBlocker.toastShortBottom(activity, "解析小程序口令失败：" + e.getMessage());
            }
            return true;
        }
        if (HkRouteBlocker.isRoute(activity, url, rule, item)) {
            return true;
        }
        if (url.contains("@lazyRule=") || url.contains("@rule=")) {
            return false;
        }
        if (HkRouter.isMagnetLink(url)) {
            if (magnetConsumer != null) magnetConsumer.accept(url);
            else hooks().startDownloadMagnet(activity, url);
            return true;
        }
        if (HkRouter.isTorrentLink(url)) {
            if (magnetConsumer != null) magnetConsumer.accept(url);
            else hooks().startParseTorrent(activity, url);
            return true;
        }
        if (isFTPOrEd2k(url)) {
            if (magnetConsumer != null) magnetConsumer.accept(url);
            else hooks().startParseFTPOrEd2k(activity, url, null);
            return true;
        }
        if (HkStringUtil.isCannotHandleScheme(url)) {
            findChooserToDeal(activity, url);
            return true;
        }
        return false;
    }

    /** input:// 支（原版 DetailUIHelper.smali .line 132-153）。 */
    private void dealInput(Activity activity, View view, HkRule rule, HkItem item, String url,
                           Consumer<String> resultConsumer) {
        String body = HkStringUtil.replaceOnceIgnoreCase(url, "input://", "");
        InputExtra extra;
        if (body.startsWith("{") && body.endsWith("}")
                && (body.contains("\"js\"") || body.contains("'js'"))) {
            extra = InputExtra.parseJson(body);
        } else {
            String[] parts = body.split("\\.js:");
            InputExtra parsed = new InputExtra();
            parsed.js = HkParserKit.arrayToString(parts, 1, ".js:");
            String[] head = parts[0].split("////");
            parsed.hint = head.length > 1 ? head[head.length - 1] : null;
            parsed.value = HkParserKit.arrayToString(head, 0, head.length - 1, "////");
            extra = parsed;
        }
        if ("{{clipboard}}".equals(extra.value) && activity.getWindow() != null) {
            // 原版 ClipboardUtil.getText(activity, decorView, lambda$dealUrlSimply$0, 200ms)：延迟取剪贴板后以其为预填值弹输入框
            getClipboardText(activity, 200, text ->
                    showInput(activity, view, rule, item, extra.hint, text, extra.js, resultConsumer));
        } else {
            showInput(activity, view, rule, item, extra.hint, extra.value, extra.js, resultConsumer);
        }
    }

    /** confirm:// 支（原版 .line 155-183）。 */
    private void dealConfirm(Activity activity, View view, HkRule rule, HkItem item, String url,
                             Consumer<String> resultConsumer) {
        String body = HkStringUtil.replaceOnceIgnoreCase(url, "confirm://", "");
        String[] parts = body.split("\\.js:");
        if (parts.length < 2) {
            HkRouteBlocker.toastShortBottom(activity, "规则有误：" + url);
            return;
        }
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        String js = HkParserKit.arrayToString(parts, 1, ".js:");
        new HkDialogs(activity)
                .setTitle("小程序提示")
                .setMessage(parts[0])
                .setPositiveButton("确定", (dialog, which) -> BG.execute(() -> {
                    String result = evalRuleJs(rule, js, "");
                    if (!activity.isFinishing()) {
                        activity.runOnUiThread(() ->
                                dispatchResult(activity, view, rule, item, result, resultConsumer, false));
                    }
                }))
                .setNegativeButton("取消", null)
                .show();
    }

    /** select:// 支（原版 .line 185-300，整段 try/catch）。 */
    private void dealSelect(Activity activity, View view, HkRule rule, HkItem item, String url,
                            Consumer<String> resultConsumer) {
        String body = HkStringUtil.replaceOnceIgnoreCase(url, "select://", "");
        try {
            SelectExtra extra;
            if (body.startsWith("{") && body.endsWith("}") && body.contains("options")) {
                extra = SelectExtra.parse(body);
            } else {
                String[] parts = body.split("\\.js:");
                extra = new SelectExtra();
                extra.js = HkParserKit.arrayToString(parts, 1, ".js:");
                extra.options = new ArrayList<>(Arrays.asList(parts[0].split("&&")));
            }
            showSelect(activity, view, rule, item, extra, resultConsumer);
        } catch (Exception e) {
            Log.e(TAG, "hk error", e);
            if (activity != null) {
                HkRouteBlocker.toastShortCenter(activity, "出错：" + e.getMessage());
            }
        }
    }

    /** copy:// 支（原版 .line 302-312）。 */
    private void dealCopy(Activity activity, View view, HkRule rule, HkItem item, String url,
                          Consumer<String> resultConsumer) {
        String body = HkStringUtil.replaceOnceIgnoreCase(url, "copy://", "");
        String[] parts = body.split("\\.js:");
        if (parts.length < 2) {
            copyToClipboardForce(activity, body);
            return;
        }
        copyToClipboardForce(activity, parts[0]);
        String js = parts[1];
        String input = parts[0];
        BG.execute(() -> {
            String result = evalRuleJs(rule, js, input);
            if (activity != null && !activity.isFinishing()) {
                activity.runOnUiThread(() ->
                        dispatchResult(activity, view, rule, item, result, resultConsumer, true));
            }
        });
    }

    /** editFile:// 支（原版 .line 325-340）：HomeHtmlEditActivity 在星辰走宿主钩子，请求码 0x4e16 由宿主承接。 */
    private void dealEditFile(Activity activity, String url) {
        String body = HkStringUtil.replaceOnceIgnoreCase(url, "editFile://", "");
        String[] parts = body.split("@js=");
        String path = body;
        if (parts.length > 1) {
            path = parts[0];
            if (activity instanceof FileEditCallbackHost) {
                ((FileEditCallbackHost) activity).addFileEditCallbackJs(
                        HkParserKit.arrayToString(parts, 1, "@js="));
            }
        }
        String exist = getExistFilePath(path);
        if (HkStringUtil.isEmpty(exist)) {
            HkRouteBlocker.toastShortBottom(activity, "无法识别的文件地址：" + path);
        } else {
            hooks().openFileEditor(activity, exist);
        }
    }

    /** openFile:// 支（原版 .line 342-353）。 */
    private void dealOpenFile(Activity activity, String url) {
        String body = HkStringUtil.replaceOnceIgnoreCase(url, "openFile://", "");
        String exist = getExistFilePath(body);
        if (HkStringUtil.isEmpty(exist)) {
            HkRouteBlocker.toastShortBottom(activity, "无法识别的文件地址：" + body);
        } else {
            hooks().checkDownloadedFile(new File(exist));
            findChooserToDeal(activity, "file://" + exist);
        }
    }

    /**
     * 照抄 DetailUIHelper.showInput（smali:3021）：标题"请输入"、hint、预填值；
     * 确认后后台 evalJS(getMyRule + js, input=输入文本)，回主线程走结果分发器（lambda$showInput$12 型）。
     * 原版承载为 XPopup asInputConfirm（圆角 16dp），星辰以原生 AlertDialog 承载。
     */
    private void showInput(Activity activity, View view, HkRule rule, HkItem item,
                           String hint, String value, String js, Consumer<String> resultConsumer) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        EditText edit = new EditText(activity);
        if (hint != null) edit.setHint(hint);
        if (value != null) edit.setText(value);
        int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
        // 原先底部内边距为 0，下划线直接压在文字上（潇洒哥 1.3.1 真机截图）；上下都留足间距
        edit.setPadding(pad, pad / 2, pad, pad / 2);
        new HkDialogs(activity)
                .setTitle("请输入")
                .setView(edit)
                .setPositiveButton("确定", (dialog, which) -> {
                    String input = edit.getText().toString();
                    BG.execute(() -> {
                        // 诊断：只记长度，不记口令值
                        SpiderDebug.log("hk", "listInput submit jsLen=%d inputLen=%d",
                                js == null ? -1 : js.length(), input == null ? -1 : input.length());
                        String result = evalRuleJs(rule, js, input);
                        if (!activity.isFinishing()) {
                            activity.runOnUiThread(() ->
                                    dispatchResult(activity, view, rule, item, result, resultConsumer, false));
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 结果分发器（原版 lambda$dealUrlSimply$1 / $4 / lambda$showInput$12 同型）：
     * 结果非空且非 "undefined" 时，有 consumer 交 consumer，否则以结果递归 dealUrlSimply（consumer 皆 null）。
     * skipCopyPrefix=true 为 copy 支的 $10 型：结果以 copy:// 开头时不再分发。
     */
    private void dispatchResult(Activity activity, View view, HkRule rule, HkItem item,
                                String result, Consumer<String> resultConsumer, boolean skipCopyPrefix) {
        if (HkStringUtil.isNotEmpty(result) && !"undefined".equalsIgnoreCase(result)) {
            if (skipCopyPrefix && result.startsWith("copy://")) return;
            if (resultConsumer != null) resultConsumer.accept(result);
            else dealUrlSimply(activity, view, rule, item, result, null, null);
        }
    }

    /** 后台求值：evalJS(getMyRule(rule) + js, input)，异常回 null（原版 JSEngine.evalJS 内部吞异常回空串近似）。 */
    private static String evalRuleJs(HkRule rule, String js, String input) {
        try {
            HkJsRuntime rt = HkEngineManager.runtimeFor(rule);
            if (rt == null) {
                SpiderDebug.log("hk", "listInput eval abort: runtime null");
                return null;
            }
            HkEngineManager.ensureInit(rt);
            String result = rt.evalJS(HkJsRuntime.getMyRule(rule) + js, input);
            String head = result == null ? "null" : (result.length() > 80 ? result.substring(0, 80) : result);
            SpiderDebug.log("hk", "listInput eval done resultHead=%s refresh=%s", head, rt.isRefreshRequested());
            return result;
        } catch (Exception e) {
            Log.e(TAG, "hk error", e);
            SpiderDebug.log("hk", "listInput eval error: %s", String.valueOf(e));
            return null;
        }
    }

    /** 剪贴板读取（原版 ClipboardUtil.getText 的延迟读取语义）。 */
    private static void getClipboardText(Context context, long delayMs, Consumer<String> consumer) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            String text = "";
            try {
                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
                    CharSequence cs = cm.getPrimaryClip().getItemAt(0).getText();
                    if (cs != null) text = cs.toString();
                }
            } catch (Exception e) {
                Log.e(TAG, "hk error", e);
            }
            consumer.accept(text);
        }, delayMs);
    }

    /** 照 ClipboardUtil.copyToClipboardForce：直接写主剪贴板。 */
    static void copyToClipboardForce(Context context, String text) {
        if (context == null) return;
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(null, text));
        } catch (Exception e) {
            Log.e(TAG, "hk error", e);
        }
    }

    /**
     * 照 FileUtil.getExistFilePath（FileUtil.smali）：hiker://files/ 与 file:// 两形，
     * 文件存在回绝对路径、否则回 null。根目录与 HkJsRuntime.getFilePath 的星辰约定一致（Documents 目录）。
     */
    static String getExistFilePath(String path) {
        if (path == null) return null;
        if (path.startsWith("hiker://files/")) {
            String rest = path.replace("hiker://files/", "");
            java.io.File docs = App.get().getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS);
            if (docs == null) return null;
            File file = new File(docs, rest);
            return file.exists() ? file.getAbsolutePath() : null;
        }
        if (path.startsWith("file://")) {
            File file = new File(path.replace("file://", ""));
            return file.exists() ? file.getAbsolutePath() : null;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // select:// 的模型与弹窗群（SelectExtra / SelectOption 照 detail/model 转写）
    // ------------------------------------------------------------------

    /** 照抄 detail/model/InputExtra（字段 hint/js/value）+ fastjson 解析语义。列表页亦复用，故公开。 */
    public static final class InputExtra {
        public String hint;
        public String js;
        public String value;

        public static InputExtra parseJson(String body) {
            InputExtra extra = new InputExtra();
            try {
                JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
                extra.hint = jsonStr(obj, "hint");
                extra.js = jsonStr(obj, "js");
                extra.value = jsonStr(obj, "value");
            } catch (Exception e) {
                Log.e(TAG, "hk error", e);
            }
            return extra;
        }
    }

    /** 照抄 detail/model/SelectOption（字段 icon/title）。列表页亦复用，故公开。 */
    public static final class SelectOption {
        public String icon;
        public String title;
    }

    /**
     * 照抄 detail/model/SelectExtra：默认值 col=3、selectedIndex=-1、attachView/bottom=false；
     * toSelectExtra 语义：options 数组首项为对象时整组转入 options2。
     */
    public static final class SelectExtra {
        public boolean attachView;
        public boolean bottom;
        public int col = 3;
        public String js;
        public List<String> options;
        public List<SelectOption> options2;
        public int selectedIndex = -1;
        public String title;

        public static SelectExtra parse(String json) {
            SelectExtra extra = new SelectExtra();
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            JsonArray optionsArr = obj.has("options") && obj.get("options").isJsonArray()
                    ? obj.getAsJsonArray("options") : null;
            if (optionsArr != null && optionsArr.size() > 0 && optionsArr.get(0).isJsonObject()) {
                extra.options2 = parseOptions2(optionsArr);
            } else if (optionsArr != null) {
                extra.options = new ArrayList<>();
                for (JsonElement el : optionsArr) {
                    extra.options.add(el.isJsonPrimitive() ? el.getAsString() : String.valueOf(el));
                }
            }
            if (obj.has("options2") && obj.get("options2").isJsonArray()) {
                extra.options2 = parseOptions2(obj.getAsJsonArray("options2"));
            }
            if (obj.has("col") && obj.get("col").isJsonPrimitive()) extra.col = obj.get("col").getAsInt();
            if (obj.has("selectedIndex") && obj.get("selectedIndex").isJsonPrimitive()) {
                extra.selectedIndex = obj.get("selectedIndex").getAsInt();
            }
            if (obj.has("attachView") && obj.get("attachView").isJsonPrimitive()) {
                extra.attachView = obj.get("attachView").getAsBoolean();
            }
            if (obj.has("bottom") && obj.get("bottom").isJsonPrimitive()) {
                extra.bottom = obj.get("bottom").getAsBoolean();
            }
            extra.title = jsonStr(obj, "title");
            extra.js = jsonStr(obj, "js");
            return extra;
        }

        private static List<SelectOption> parseOptions2(JsonArray arr) {
            List<SelectOption> list = new ArrayList<>();
            for (JsonElement el : arr) {
                SelectOption option = new SelectOption();
                if (el.isJsonObject()) {
                    option.title = jsonStr(el.getAsJsonObject(), "title");
                    option.icon = jsonStr(el.getAsJsonObject(), "icon");
                }
                list.add(option);
            }
            return list;
        }
    }

    private static String jsonStr(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return null;
        JsonElement el = obj.get(key);
        return el.isJsonPrimitive() ? el.getAsString() : el.toString();
    }

    /** select 选中后的双参回调（原版 ConsumerTwo：标题 + 下标）。 */
    private interface SelectTwo {
        void accept(String title, int index);
    }

    /**
     * 照抄 select:// 的 UI 分发：options2 有值走 attach 列表或 BookmarkFolderPopup 型网格；
     * options 有值走 attach 列表 / 底部弹窗 / 居中弹窗（HkCenterListPopup）。
     * 选中后（lambda$dealUrlSimply$6 型）：后台 evalJS(getMyRule + MY_INDEX 注入 + js, input=标题），回主线程分发。
     */
    private void showSelect(Activity activity, View view, HkRule rule, HkItem item,
                            SelectExtra extra, Consumer<String> resultConsumer) {
        SelectTwo two = (title, index) -> {
            if (activity == null || activity.isFinishing()) return;
            BG.execute(() -> {
                String js = HkLazyRuleParser.generateMY("MY_INDEX", index) + (extra.js == null ? "" : extra.js);
                String result = evalRuleJs(rule, js, title);
                if (!activity.isFinishing()) {
                    activity.runOnUiThread(() ->
                            dispatchResult(activity, view, rule, item, result, resultConsumer, false));
                }
            });
        };
        String title = HkStringUtil.isEmpty(extra.title) ? "请选择" : extra.title;
        if (extra.options2 != null && !extra.options2.isEmpty()) {
            List<String> titles = new ArrayList<>();
            for (SelectOption option : extra.options2) titles.add(option.title);
            if (extra.attachView && view != null) {
                showAttachList(activity, view, titles, two);
            } else {
                showBookmarkGrid(activity, title, extra, two);
            }
        } else if (extra.options != null && !extra.options.isEmpty()) {
            if (extra.attachView && view != null) {
                showAttachList(activity, view, extra.options, two);
            } else if (extra.bottom) {
                showBottomList(activity, title, extra, two);
            } else {
                int span = extra.col > 0 ? extra.col : 3;
                new HkCenterListPopup(activity)
                        .withTitle(title)
                        .withSelectIndex(extra.selectedIndex)
                        .with(extra.options, span, new HkCenterListPopup.ClickListener() {
                            @Override
                            public void click(String text, int position) {
                                two.accept(text, position);
                            }

                            @Override
                            public void onLongClick(String text, int position) {
                            }
                        })
                        .show();
            }
        }
    }

    /** 原版 XPopup.asAttachList 的等价实现：锚定在条目 View 上的下拉列表。 */
    private void showAttachList(Activity activity, View anchor, List<String> options, SelectTwo two) {
        RecyclerView recyclerView = new RecyclerView(activity);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        PopupWindow popup = new PopupWindow(recyclerView,
                anchor.getWidth() > 0 ? anchor.getWidth() : (int) (200 * activity.getResources().getDisplayMetrics().density),
                WindowManager.LayoutParams.WRAP_CONTENT, true);
        popup.setAnimationStyle(R.style.XcPopupFade);
        HkCenterListAdapter adapter = new HkCenterListAdapter(activity, options,
                new HkCenterListAdapter.OnItemClickListener() {
                    @Override
                    public void onClick(View v, int position) {
                        popup.dismiss();
                        two.accept(options.get(position), position);
                    }

                    @Override
                    public void onLongClick(View v, int position) {
                    }
                });
        recyclerView.setAdapter(adapter);
        popup.setBackgroundDrawable(androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.bg_round_all));
        popup.setOutsideTouchable(true);
        popup.showAsDropDown(anchor);
    }

    /** 原版 CustomBottomRecyclerViewPopup 的等价实现：底部滑出、标题 + 网格列表（span = col>0?col:3）。 */
    private void showBottomList(Activity activity, String title, SelectExtra extra, SelectTwo two) {
        Dialog dialog = new Dialog(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.bg_round_all));
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTextColor(0xFF222222);
        root.addView(titleView);
        RecyclerView recyclerView = new RecyclerView(activity);
        int span = extra.col > 0 ? extra.col : 3;
        recyclerView.setLayoutManager(new GridLayoutManager(activity, span));
        HkCenterListAdapter adapter = new HkCenterListAdapter(activity, extra.options,
                new HkCenterListAdapter.OnItemClickListener() {
                    @Override
                    public void onClick(View v, int position) {
                        dialog.dismiss();
                        two.accept(extra.options.get(position), position);
                    }

                    @Override
                    public void onLongClick(View v, int position) {
                    }
                });
        adapter.setSelectIndex(extra.selectedIndex);
        recyclerView.setAdapter(adapter);
        root.addView(recyclerView);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    /**
     * 原版 BookmarkFolderPopup 的等价实现：options2 以书签式网格（图标 + 标题，span=2）居中展示，
     * 条目对应 SelectExtra.mockBookmarks() 的 Bookmark{title, dir=false, url="select://"+title, icon}。
     */
    private void showBookmarkGrid(Activity activity, String title, SelectExtra extra, SelectTwo two) {
        Dialog dialog = new Dialog(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.bg_round_all));
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTextColor(0xFF222222);
        root.addView(titleView);
        RecyclerView recyclerView = new RecyclerView(activity);
        recyclerView.setLayoutManager(new GridLayoutManager(activity, 2));
        recyclerView.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int rowPad = (int) (8 * activity.getResources().getDisplayMetrics().density);
                row.setPadding(rowPad, rowPad, rowPad, rowPad);
                ImageView icon = new ImageView(activity);
                int iconSize = (int) (28 * activity.getResources().getDisplayMetrics().density);
                row.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));
                TextView text = new TextView(activity);
                text.setTextSize(15);
                text.setTextColor(0xFF222222);
                LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                textParams.leftMargin = rowPad;
                row.addView(text, textParams);
                RecyclerView.ViewHolder holder = new RecyclerView.ViewHolder(row) {
                };
                row.setTag(new View[]{icon, text});
                return holder;
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                SelectOption option = extra.options2.get(position);
                View[] views = (View[]) holder.itemView.getTag();
                ImageView icon = (ImageView) views[0];
                TextView text = (TextView) views[1];
                text.setText(option.title);
                if (HkStringUtil.isNotEmpty(option.icon)) {
                    icon.setVisibility(View.VISIBLE);
                    com.bumptech.glide.Glide.with(activity)
                            .load(com.fongmi.android.tv.utils.ImgUtil.getUrl(option.icon))
                            .into(icon);
                } else {
                    icon.setVisibility(View.GONE);
                }
                holder.itemView.setOnClickListener(v -> {
                    dialog.dismiss();
                    two.accept(option.title, position);
                });
            }

            @Override
            public int getItemCount() {
                return extra.options2.size();
            }
        });
        root.addView(recyclerView);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
            window.setLayout((int) (screenWidth * 0.9f), WindowManager.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }
}
