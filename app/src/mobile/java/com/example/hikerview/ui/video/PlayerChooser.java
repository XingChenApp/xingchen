package com.example.hikerview.ui.video;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.LongSparseArray;
import android.webkit.CookieManager;

import com.alibaba.fastjson.JSON;
import com.annimon.stream.function.Consumer;
import com.example.hikerview.model.ViewCollection;
import com.example.hikerview.model.ViewCollectionExtraData;
import com.example.hikerview.ui.ActivityManager;
import com.example.hikerview.ui.browser.model.UrlDetector;
import com.example.hikerview.ui.browser.util.CollectionUtil;
import com.example.hikerview.utils.DataTransferUtils;
import com.example.hikerview.utils.DisplayUtil;
import com.example.hikerview.utils.FileUtil;
import com.example.hikerview.utils.FilesInAppUtil;
import com.example.hikerview.utils.PreferenceMgr;
import com.example.hikerview.utils.StringUtil;
import com.example.hikerview.utils.ToastMgr;
import com.fongmi.android.tv.api.hk.HkDlanState;
import com.fongmi.android.tv.api.hk.HkHttpParser;
import com.fongmi.android.tv.api.hk.HkProxyServer;
import com.fongmi.android.tv.ui.hk.HkArticleFragment;
import com.fongmi.android.tv.ui.hk.HkDetailBridge;
import com.fongmi.android.tv.ui.hk.HkPlayerChooser;
import com.fongmi.android.tv.ui.hk.HkWebCarrier;
import com.fongmi.android.tv.ui.hk.HkWebViewWrapper;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.util.XPopupUtils;

import java.net.URLEncoder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import timber.log.Timber;

/**
 * 官 {@code com.example.hikerview.ui.video.PlayerChooser} 的插件面适配。
 *
 * <p>官本体是「播放器偏好选择＋VideoPlayerActivity 意图拼装」，星辰的等价
 * 落点是 {@code ui.hk.HkDetailBridge} 的 startPlayerSingle/startPlayerChapters
 * （宿主 HkListActivity 实现，最终走 VideoActivity.startHkPlay），本类只做
 * 签名对齐、官原样前置校验与 VideoChapter → HkChapter 的字段转换。
 * 官的播放器偏好分流（third player 等）在星辰无对应物，不在此复刻。
 * 官 list 版的集名公共前缀裁剪（findSameCharStartCount 写 st）由星辰
 * startPlayerChapters 侧的标题处理承接，本类不重复做。
 *
 * <p>未落记名（依赖在星辰无同职责等价物，见对官补缺口回执）：
 * decorateHeader(Activity 版）已于 136 批补落、E1 尾巴于 136b 簇2 接上可替换
 * 来源口（见法注：官 WebViewHelper 挂 WebView 的请求头表/referer 策略在咱家
 * 是 HkWebkitHolder 私有 Wrapper，经公开面只能走到 HkWebkitHolder、最后一跳
 * 无公开取口，来源经 HkPlayerChooser.currentWrapper 的 provider/登记口取，
 * 登记一行待主线在 HkWebkitHolder.initWebView 补、见 fill-136b-c2 分账）。
 * startPlayer 八参已于 136 批补落分发本体、余 15 支第三方播放器起播本体于
 * 136b 簇2 逐支照官转写落齐（见各 start* 法注与 fill-136b-c2 分账）。
 * startBolanAssist/startReex 及必需链段（initSubtitleUrl 字幕支、
 * startPlayerById 两形）已补落：官 RemoteServerManager/DlanUrlDTO/AndServer
 * 一面接本树既有等价物 HkDlanState（DTO 存储）＋HkProxyServer（起服/服务址，
 * NanoHTTPD）＋HkDlanController（/file 端点），逐项映射见各法注与分团账；
 * AndServer 经核不引（等价物在树，详分团账）。
 * checkSize/checkPicsSize 已随 DataTransferUtils 落树补落；setDefaultPlayer
 * 两重载已随①团 PlayerEnum/ViewCollection/ViewCollectionExtraData 与本树
 * PreferenceMgr 落齐补落（连带私有 setPlayer/checkPlayerExist 照官落）。
 */
public class PlayerChooser {

    /** 官 chapterMap 静态字段（smali <clinit>）：putChapters/getChapterMap 的进程内集表槽。 */
    public static LongSparseArray<List<VideoChapter>> chapterMap = new LongSparseArray<>();

    public static void startPlayer(Context context, List<VideoChapter> list) {
        startPlayer(context, list, null, null, null);
    }

    public static void startPlayer(Context context, List<VideoChapter> list,
                                   String cUrl, String mTitle, Bundle bundle) {
        if (context == null) {
            return;
        }
        if (list == null || list.size() <= 0) {
            ToastMgr.shortBottomCenter(context, "选集有误");
            return;
        }
        boolean hasUse = false;
        for (VideoChapter chapter : list) {
            if (chapter != null && chapter.isUse()) {
                hasUse = true;
                break;
            }
        }
        if (!hasUse) {
            ToastMgr.shortBottomCenter(context, "没有选中的集数");
            return;
        }
        Activity activity = activityOf(context);
        if (activity == null) {
            return;
        }
        List<HkArticleFragment.HkChapter> chapters = new ArrayList<>(list.size());
        for (VideoChapter chapter : list) {
            if (chapter == null) continue;
            HkArticleFragment.HkChapter hc = new HkArticleFragment.HkChapter();
            hc.title = chapter.getTitle();
            hc.memoryTitle = chapter.getMemoryTitle();
            hc.url = chapter.getUrl();
            hc.originalUrl = chapter.getOriginalUrl();
            hc.extra = chapter.getExtra();
            hc.pic = chapter.getPicUrl();
            hc.codeAndHeader = chapter.getCodeAndHeader();
            hc.realPos = chapter.getRealPos();
            hc.use = chapter.isUse();
            chapters.add(hc);
        }
        new HkDetailBridge().startPlayerChapters(activity, chapters, cUrl, mTitle, bundle);
    }

    public static void startPlayer(Context context, String title, String url) {
        if (context == null) {
            return;
        }
        Activity activity = activityOf(context);
        if (activity == null) {
            return;
        }
        new HkDetailBridge().startPlayerSingle(activity, title, url);
    }

    /**
     * 官 appInstalledOrNot（官 smali ui/video/PlayerChooser .line 1314-1335）：
     * 先遍历 getInstalledPackages(0) 比对包名，异常 Timber 后继续；再以
     * getPackageInfo(pkg, 1) 复核，不抛即在场，NameNotFound 不打日志回 false，
     * 其余异常 Timber 后回 false。
     * （HkJsRuntime 私有 appInstalled 是 openAppIntent 的装机门，语义不同段，不复用。）
     */
    public static boolean appInstalledOrNot(Context context, String pkg) {
        try {
            List<PackageInfo> packages = context.getPackageManager().getInstalledPackages(0);
            if (packages != null) {
                for (int i = 0; i < packages.size(); i++) {
                    if (pkg.equals(packages.get(i).packageName)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Timber.e(e);
        }
        try {
            PackageManager pm = context.getPackageManager();
            try {
                pm.getPackageInfo(pkg, 1);
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
            return true;
        } catch (Exception e) {
            Timber.e(e);
        }
        return false;
    }

    /** 官 getChapterMap（.line 82）：回 chapterMap 本体。 */
    public static LongSparseArray<List<VideoChapter>> getChapterMap() {
        return chapterMap;
    }

    /** 官 putChapters（.line 652-653）：以当前毫秒为键存入 chapterMap 并回该键。 */
    public static long putChapters(List<VideoChapter> list) {
        long now = System.currentTimeMillis();
        chapterMap.put(now, list);
        return now;
    }

    /** 官 getThirdPlaySource（.line 903-907）：空回原样，非空取 ";" 首段。 */
    public static String getThirdPlaySource(String url) {
        if (StringUtil.isEmpty(url)) {
            return url;
        }
        return url.split(";")[0];
    }

    /**
     * 官 decorateHeader(Map 版，.line 320-330)：首个 String 参在官本体被覆写为
     * cookie 槽、不入逻辑；referer 仅用于 url 的 cookie 为空时兜底取 cookie，
     * 最终走 decorateHeader0(map, null, url, cookie)。异常 Timber 后照常走尾步。
     * getReferer/decorateHeader0 接 com.fongmi.android.tv.ui.hk.HkPlayerChooser 同名转写。
     */
    public static String decorateHeader(Map<String, String> map, String ignored,
                                        String pageUrl, String url) {
        String cookie = "";
        try {
            String referer = HkPlayerChooser.getReferer(map, null, pageUrl, url);
            cookie = CookieManager.getInstance().getCookie(url);
            if (StringUtil.isEmpty(cookie) && StringUtil.isNotEmpty(referer)) {
                cookie = CookieManager.getInstance().getCookie(referer);
            }
        } catch (Exception e) {
            Timber.e(e);
        }
        return HkPlayerChooser.decorateHeader0(map, null, url, cookie);
    }

    /**
     * 官 decorateHeaderWithReferer（.line 334-344）：与 Map 版 decorateHeader 的
     * 差别只在 referer 计算值作为 decorateHeader0 的第二参传入（而非 null）。
     * 异常时 referer 寄存器保留入参 pageUrl（照官寄存器语义置初值）。
     * getReferer/decorateHeader0 接 HkPlayerChooser 同名转写。
     */
    public static String decorateHeaderWithReferer(Map<String, String> map, String policy,
                                                   String pageUrl, String url) {
        String cookie = "";
        String referer = pageUrl;
        try {
            referer = HkPlayerChooser.getReferer(map, policy, pageUrl, url);
            cookie = CookieManager.getInstance().getCookie(url);
            if (StringUtil.isEmpty(cookie) && StringUtil.isNotEmpty(referer)) {
                cookie = CookieManager.getInstance().getCookie(referer);
            }
        } catch (Exception e) {
            Timber.e(e);
        }
        return HkPlayerChooser.decorateHeader0(map, referer, url, cookie);
    }

    /**
     * 官 checkSize(Intent)（.line 219-229）：extras 为空或无 "rule" 键即回；
     * rule 字节长 Timber 打点，&gt;0x19000 时从 Intent 摘除并转存
     * {@link DataTransferUtils}（键 "tempVideoRule"）。
     */
    public static void checkSize(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) {
            return;
        }
        if (!extras.containsKey("rule")) {
            return;
        }
        String rule = extras.getString("rule");
        int length = rule.getBytes().length;
        Timber.d("变量传递, length: %s", length);
        if (length > 0x19000) {
            intent.removeExtra("rule");
            DataTransferUtils.INSTANCE.putCacheString(rule, "tempVideoRule");
        }
    }

    /** 官 checkSize(Bundle) 私有形（.line 253-261）：同上，摘除走 Bundle.remove。 */
    private static void checkSize(Bundle bundle) {
        if (bundle == null) {
            return;
        }
        if (!bundle.containsKey("rule")) {
            return;
        }
        String rule = bundle.getString("rule");
        int length = rule.getBytes().length;
        Timber.d("变量传递, length: %s", length);
        if (length > 0x19000) {
            bundle.remove("rule");
            DataTransferUtils.INSTANCE.putCacheString(rule, "tempVideoRule");
        }
    }

    /**
     * 官 checkPicsSize(Intent)（.line 234-247）：extras 为空或无 "pics" 键即回；
     * 逐条字节长求和 &gt;0x19000 时从 Intent 摘除并转存 {@link DataTransferUtils#putPics}。
     */
    public static void checkPicsSize(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) {
            return;
        }
        if (!extras.containsKey("pics")) {
            return;
        }
        ArrayList<String> pics = extras.getStringArrayList("pics");
        if (CollectionUtil.isNotEmpty(pics)) {
            long total = 0;
            for (String pic : pics) {
                total += pic.getBytes().length;
            }
            if (total > 0x19000) {
                intent.removeExtra("pics");
                DataTransferUtils.INSTANCE.putPics(pics);
            }
        }
    }

    /** 官 setDefaultPlayer(Activity, String)（.line 697）：转三参形、collection 传 null。 */
    public static void setDefaultPlayer(Activity activity, String from) {
        setDefaultPlayer(activity, from, null);
    }

    /**
     * 官 setDefaultPlayer(Activity, String, ViewCollection)（.line 702-802）：
     * from="settingBack" 时候选表剔除 SYSTEM/UC_PLAYER/QQ_PLAYER/UC_INTL_PLAYER
     * （按 code 比对）四员、偏好键 "player2"，余形候选全表、键 "player"；当前值：
     * collection 为 null 读偏好，否则读 ViewCollectionExtraData（isCustomPlayer
     * 在场取其 player、否则读偏好），默认 PLAYER_TWO 的 code。XPopup 底部列表
     * （标题照官、最高 0.85 屏高、16dp 圆角、isDestroyOnDismiss）选中回调走
     * 官 lambda$setDefaultPlayer$4 分发。偏好读写接本树 PreferenceMgr（官同名类，
     * 文件 share_data）；其 remove 法未落，「清除备用」一支直连同一
     * SharedPreferences 摘键、同文件同效果，记名。
     */
    public static void setDefaultPlayer(Activity activity, String from, ViewCollection collection) {
        boolean settingBack = "settingBack".equals(from);
        PlayerEnum[] all = PlayerEnum.values();
        PlayerEnum[] players;
        if (settingBack) {
            PlayerEnum[] excluded = {PlayerEnum.SYSTEM, PlayerEnum.UC_PLAYER,
                    PlayerEnum.QQ_PLAYER, PlayerEnum.UC_INTL_PLAYER};
            players = new PlayerEnum[all.length - excluded.length];
            int idx = 0;
            for (PlayerEnum player : all) {
                boolean skip = false;
                for (PlayerEnum ex : excluded) {
                    if (ex.getCode() == player.getCode()) {
                        skip = true;
                        break;
                    }
                }
                if (!skip) {
                    players[idx++] = player;
                }
            }
        } else {
            players = all;
        }
        String prefKey = settingBack ? "player2" : "player";
        int current;
        ViewCollectionExtraData extraData = null;
        if (collection == null) {
            current = PreferenceMgr.getInt(activity, prefKey, PlayerEnum.PLAYER_TWO.getCode());
        } else {
            ViewCollectionExtraData data;
            try {
                data = ViewCollectionExtraData.fromJson(collection.getExtraData());
                if (data == null) {
                    data = new ViewCollectionExtraData(collection.getId());
                }
            } catch (Exception e) {
                data = new ViewCollectionExtraData(collection.getId());
                Timber.e(e);
            }
            extraData = data;
            current = data.isCustomPlayer() ? data.getPlayer()
                    : PreferenceMgr.getInt(activity, prefKey, PlayerEnum.PLAYER_TWO.getCode());
        }
        String[] names = new String[players.length];
        int checked = 0;
        for (int i = 0; i < players.length; i++) {
            names[i] = players[i].getName();
            if (players[i].getCode() == current) {
                checked = i;
            }
        }
        final ViewCollectionExtraData fExtraData = extraData;
        new XPopup.Builder(activity)
                .maxHeight((int) (XPopupUtils.getScreenHeight(activity) * 0.85f))
                .borderRadius((float) DisplayUtil.dpToPx(activity, 16))
                .isDestroyOnDismiss(true)
                .asBottomList("选择播放器（请确保已安装相应软件）", names, null, checked,
                        (position, text) -> onDefaultPlayerSelected(from, players, settingBack,
                                activity, prefKey, collection, fExtraData, position))
                .show();
    }

    /**
     * 官 lambda$setDefaultPlayer$4（.line 769-798）：按 from 分发——
     * "collection"：setPlayer 写片单＋checkPlayerExist；"settingBack"/"setting"：
     * 选中 PLAYER_TWO 且 settingBack 时摘偏好键并 toast「已清除备用播放器设置」，
     * 余形写偏好键并 toast（备用/全局）「播放器已设置为X」，后 checkPlayerExist；
     * 官尾支「settingBack 且宿主是 VideoPlayerActivity 时调 updatePlayer2Text」——
     * 星辰无 VideoPlayerActivity 类（播放宿主为 fongmi VideoActivity、无此法），
     * 该 instanceof 支恒假、无对位，略并记名。"videoPlayer"：XPopup 确认
     * 「温馨提示」后走官 lambda$setDefaultPlayer$3（setPlayer＋finish＋对当前
     * Activity checkPlayerExist）。
     */
    private static void onDefaultPlayerSelected(String from, PlayerEnum[] players, boolean settingBack,
                                                Activity activity, String prefKey, ViewCollection collection,
                                                ViewCollectionExtraData extraData, int position) {
        switch (from) {
            case "collection":
                setPlayer(activity, collection, extraData, players[position]);
                checkPlayerExist(activity, players[position]);
                break;
            case "videoPlayer":
                new XPopup.Builder(activity)
                        .borderRadius((float) DisplayUtil.dpToPx(activity, 16))
                        .asConfirm("温馨提示", "当前正在播放器页面，必须退出当前页面才能使设置生效，是否退出当前页面并且设置播放器为"
                                        + players[position].getName() + "？",
                                () -> {
                                    setPlayer(activity, collection, extraData, players[position]);
                                    activity.finish();
                                    checkPlayerExist(ActivityManager.getInstance().getCurrentActivity(), players[position]);
                                })
                        .show();
                break;
            case "settingBack":
            case "setting":
                if (players[position].getCode() == PlayerEnum.PLAYER_TWO.getCode() && settingBack) {
                    activity.getSharedPreferences("share_data", 0).edit().remove(prefKey).apply();
                    ToastMgr.shortBottomCenter(activity, "已清除备用播放器设置");
                    checkPlayerExist(activity, players[position]);
                } else {
                    PreferenceMgr.put(activity, prefKey, players[position].getCode());
                    ToastMgr.shortBottomCenter(activity, (settingBack ? "备用" : "全局")
                            + "播放器已设置为" + players[position].getName());
                    checkPlayerExist(activity, players[position]);
                }
                break;
            default:
                break;
        }
    }

    /**
     * 官 setPlayer 私有（.line 889-899）：collection 在场则置 extraData 三字段、
     * 回写 collection 并 save，toast「当前片单播放器已设置为X」；否则写全局偏好
     * 键 "player"，toast「全局播放器已设置为X」。
     */
    private static void setPlayer(Activity activity, ViewCollection collection,
                                  ViewCollectionExtraData extraData, PlayerEnum player) {
        String msg;
        if (collection != null) {
            extraData.setCustomPlayer(true);
            extraData.setPlayer(player.getCode());
            extraData.setCollectionId(collection.getId());
            collection.setExtraData(extraData.toJson());
            collection.save();
            msg = "当前片单播放器已设置为" + player.getName();
        } else {
            PreferenceMgr.put(activity, "player", player.getCode());
            msg = "全局播放器已设置为" + player.getName();
        }
        ToastMgr.shortBottomCenter(activity, msg);
    }

    /**
     * 官 checkPlayerExist 私有（.line 855-882）：按 PlayerEnum 查包名（备选包/
     * 下载址/口令照官 $SwitchMap 表）；未装且备选包也未装时 XPopup 确认
     * （16dp 圆角）「检测到你没有安装X，是否跳转下载对应APP」，确认走官
     * lambda$checkPlayerExist$5。PLAYER_TWO/SYSTEM 官表内直接回；
     * NEXT_PLAYER 官 $SwitchMap 无此员、同归无包名直接回。
     * goWeb 接本树既有决策口的前后段（HkWebCarrier.loadIntoExisting 先行、
     * 无承载回系统浏览器）；中间段 HkListActivity.openX5PlayPage 为 hk 包
     * 包内私有、本包不可达，记名。剪贴板官 ClipboardUtil.copyToClipboard
     * 三参形未落，以 ClipboardManager 同效直落，记名。
     */
    private static void checkPlayerExist(Activity activity, PlayerEnum player) {
        String pkg = null;
        String altPkg = null;
        String url = null;
        String pwd = null;
        switch (player) {
            case PLAYER_TWO:
            case SYSTEM:
            case NEXT_PLAYER:
                return;
            case BO_LAN_ASSIST:
                pkg = "com.hiker.bolanassist";
                url = "https://www.123pan.com/s/fajA-YlLQh";
                break;
            case REEX:
                pkg = "xyz.re.player.ex";
                url = "https://wwu.lanzouy.com/b01np165c";
                pwd = "qazw";
                break;
            case MX_PLAYER:
                pkg = "com.mxtech.videoplayer.ad";
                altPkg = "com.mxtech.videoplayer.pro";
                break;
            case X_PLAYER:
                pkg = "video.player.videoplayer";
                break;
            case KM_PLAYER:
                pkg = "com.kmplayerpro";
                altPkg = "com.kmplayer";
                break;
            case MO_BO_PLAYER:
                pkg = "com.clov4r.android.nil.noad";
                altPkg = "com.clov4r.android.nil";
                break;
            case QQ_PLAYER:
                pkg = "com.tencent.mtt";
                break;
            case VLC_PLAYER:
                pkg = "org.videolan.vlc";
                break;
            case UC_PLAYER:
                pkg = "com.UCMobile";
                break;
            case UC_INTL_PLAYER:
                pkg = "com.UCMobile.intl";
                break;
            case DAN_DAN_PLAYER:
                pkg = "com.xyoye.dandanplay";
                url = "https://gitee.com/xyoye/DanDanPlayForAndroid/releases";
                break;
            case N_PLAYER:
                pkg = "com.newin.nplayer.pro";
                break;
            case WEB_VIDEO_CASTER:
                pkg = "com.instantbits.cast.webvideo";
                break;
            case LUA_PLAYER:
                pkg = "ms.dev.luaplayer_pro";
                break;
            case KODI:
                pkg = "org.xbmc.kodi";
                break;
        }
        String name = player.getName();
        if (pkg != null && !appInstalledOrNot(activity, pkg)) {
            if (StringUtil.isNotEmpty(altPkg) && appInstalledOrNot(activity, altPkg)) {
                return;
            }
            final String fUrl = url;
            final String fPwd = pwd;
            new XPopup.Builder(activity)
                    .borderRadius((float) DisplayUtil.dpToPx(activity, 16))
                    .asConfirm("温馨提示", "检测到你没有安装" + name + "，是否跳转下载对应APP",
                            () -> onPlayerDownloadConfirm(fUrl, activity, name, fPwd))
                    .show();
        }
    }

    /** 官 lambda$checkPlayerExist$5：有下载址 goWeb、无址 bing 搜索；有口令 toast 并复制。 */
    private static void onPlayerDownloadConfirm(String url, Activity activity, String name, String pwd) {
        if (StringUtil.isNotEmpty(url)) {
            goWeb(activity, url);
        } else {
            goWeb(activity, "https://cn.bing.com/search?q=" + URLEncoder.encode(name + " 下载"));
        }
        if (StringUtil.isNotEmpty(pwd)) {
            ToastMgr.shortBottomCenter(activity, "密码为" + pwd + "，已复制到剪贴板");
            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText(null, pwd));
            }
        }
    }

    /** 官 WebUtil.goWeb 的本树决策口（同 HkDetailBridge/HkRouteBlocker 既有口径的前段＋末段）。 */
    private static void goWeb(Context context, String url) {
        if (HkWebCarrier.loadIntoExisting(url)) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    /**
     * 官 startReex（smali_classes2/com/example/hikerview/ui/video/PlayerChooser.smali
     * .line 1177-1185）：请求头非空以 "reex.extra.http_header" 键 put fastjson 串；
     * 字幕非空经 initSubtitleUrl 换址后以 "reex.extra.subtitle" 键 put；尾走
     * startPlayerById（名 "Reex"、包/类 xyz.re.player.ex/.MainActivity）。
     */
    public static boolean startReex(Context context, String title, String url,
                                    Map<String, String> headers, String subtitle) {
        Map<String, String> extras = new HashMap<>();
        if (headers != null) {
            extras.put("reex.extra.http_header", JSON.toJSONString(headers));
        }
        if (StringUtil.isNotEmpty(subtitle)) {
            String subtitleUrl = initSubtitleUrl(context, title, url, headers, subtitle);
            extras.put("reex.extra.subtitle", subtitleUrl);
        }
        return startPlayerById(context, title, url, "Reex", extras,
                "xyz.re.player.ex", "xyz.re.player.ex.MainActivity");
    }

    /**
     * 官 startBolanAssist（同上 .line 1189-1202）：url 以 "file:"/"content"/"/"
     * 任一开头的先经 FilesInAppUtil.getUri 换串、余形原样，以 "url" 键 put；
     * 请求头非空以 "header" 键 put fastjson 串；字幕非空经 initSubtitleUrl
     * 换址后以 "subtitle" 键 put（initSubtitleUrl 与 startPlayerById 的 url
     * 入参均为原始 url，非换串后的 playUrl，照官寄存器原样）；尾走
     * startPlayerById（名「波澜工具箱」、包 com.hiker.bolanassist、类
     * com.hiker.bolanassist.ui.player.VideoPlayerActivity）。
     */
    public static boolean startBolanAssist(Context context, String title, String url,
                                           Map<String, String> headers, String subtitle) {
        Map<String, String> extras = new HashMap<>();
        String playUrl;
        if (url.startsWith("file:") || url.startsWith("content") || url.startsWith("/")) {
            playUrl = FilesInAppUtil.getUri(context, url).toString();
        } else {
            playUrl = url;
        }
        extras.put("url", playUrl);
        if (headers != null) {
            extras.put("header", JSON.toJSONString(headers));
        }
        if (StringUtil.isNotEmpty(subtitle)) {
            String subtitleUrl = initSubtitleUrl(context, title, url, headers, subtitle);
            extras.put("subtitle", subtitleUrl);
        }
        return startPlayerById(context, title, url, "波澜工具箱", extras,
                "com.hiker.bolanassist", "com.hiker.bolanassist.ui.player.VideoPlayerActivity");
    }

    /**
     * 官 initSubtitleUrl（同上 .line 1122-1153）——本法本体属官宿主自有面、
     * 非交付目标，此处仅作 startBolanAssist/startReex 字幕支的必需链段照官
     * 落，不新接任何入口：字幕为 null 或不以 "hiker"/"file" 开头原样回；
     * 否则现存 DTO 为空或其 subtitle 与入参不等时，新建 DTO（url/headers/
     * 跳过时长 0,0＋title＋subtitle）写入并起服（异常 Timber.e）；尾恒拼
     * 服务址＋"/file?name=subtitle."＋FileUtil.getExtension(字幕）回。
     * 等价映射记名：官 RemoteServerManager.getUrlDTO/setUrlDTO→
     * HkDlanState.getDto/setUrlDTO（DlanUrlDTO 八字段与 HkDlanState.Dto 逐项
     * 对位）；startServer＋空回调 PlayerChooser$1（onStarted/onStopped/
     * onException 三法皆空）→HkProxyServer 起服（未跑才走 startWithFallback，
     * 其端口递增回退即官 $1 监听的 Address already in use 递增语义落点，
     * 无回调可通知）；getServerUrl(Application.application)→
     * HkProxyServer.getLanServerUrl（同为局域网 IP 拼址；官 IP 空回 null、
     * 等价物 IP 空回本机回环址，此一处从等价物既有口径、不改其本体）；
     * 官 StringUtils.equals（commons-lang3 不在咱家依赖声明）→Objects.equals
     * （本点两参皆 String、同义）。
     */
    public static String initSubtitleUrl(Context context, String title, String url,
                                         Map<String, String> headers, String subtitle) {
        if (subtitle == null || (!subtitle.startsWith("hiker") && !subtitle.startsWith("file"))) {
            return subtitle;
        }
        HkDlanState.Dto current = HkDlanState.getDto();
        if (current == null || !Objects.equals(subtitle, current.subtitle)) {
            HkDlanState.Dto dto = new HkDlanState.Dto();
            dto.url = url;
            dto.headers = headers;
            dto.jumpStartDuration = 0;
            dto.jumpEndDuration = 0;
            dto.title = title;
            dto.subtitle = subtitle;
            HkDlanState.setUrlDTO(dto);
            try {
                if (!HkProxyServer.instance().isRunning()) {
                    HkProxyServer.startWithFallback();
                }
            } catch (Exception e) {
                Timber.e(e);
            }
        }
        return HkProxyServer.instance().getLanServerUrl()
                + "/file?name=subtitle." + FileUtil.getExtension(subtitle);
    }

    /** 官 startPlayerById 七参形（同上 .line 1276）：转八参形、Consumer 传 null。 */
    private static boolean startPlayerById(Context context, String title, String url,
                                           String playerName, Map<String, String> extras,
                                           String pkg, String activity) {
        return startPlayerById(context, title, url, playerName, extras, pkg, activity, null);
    }

    /** 官 startPlayerById 六参形（同上 smali:6265，.line 1271）：转七参形、extras 传 null。 */
    private static boolean startPlayerById(Context context, String title, String url,
                                           String playerName, String pkg, String activity) {
        return startPlayerById(context, title, url, playerName, null, pkg, activity);
    }

    /**
     * 官各直建支共用的意图前段（startMxPlayer/startKMPlayer/startMoboPlayer 等
     * 逐支同形段的合写）：ACTION_VIEW＋FilesInAppUtil.getUri 定 data、
     * UrlDetector.isMusic 定 type（audio/* 或 video/*）、put "title"/"name"
     * 两键（皆 title）、addFlags(0x10008000)。url 须已由调用支照官先过
     * getThirdPlaySource（startPlayerById 本体亦自带此步、直建支在官本体逐支自带）。
     */
    private static Intent buildStandardViewIntent(Context context, String title, String url) {
        Intent intent = new Intent();
        intent.setAction("android.intent.action.VIEW");
        intent.setDataAndType(FilesInAppUtil.getUri(context, url),
                UrlDetector.isMusic(title, url) ? "audio/*" : "video/*");
        intent.putExtra("title", title);
        intent.putExtra("name", title);
        intent.addFlags(0x10008000);
        return intent;
    }

    /**
     * 官 startPlayerById 八参形（同上 .line 1282-1308）：url 先 getThirdPlaySource；
     * ACTION_VIEW 意图以 FilesInAppUtil.getUri＋UrlDetector.isMusic 定 data/type
     * （audio/* 或 video/*)，put "title"/"name" 两键（皆 title)，extras 非空逐项
     * putExtra, addFlags(0x10008000)；目标已装时先 Consumer.accept（在场才调）
     * 再 setComponent＋startActivity 回 true，异常 Timber.e 后携其 message 落尾；
     * 未装或异常时 toast「没有安装X！」＋message（未装时为空串）回 false。
     * Consumer 接本树同包补齐类 com.annimon.stream.function.Consumer。
     */
    private static boolean startPlayerById(Context context, String title, String url,
                                           String playerName, Map<String, String> extras,
                                           String pkg, String activity,
                                           Consumer<Intent> consumer) {
        url = getThirdPlaySource(url);
        Intent intent = new Intent();
        intent.setAction("android.intent.action.VIEW");
        intent.setDataAndType(FilesInAppUtil.getUri(context, url),
                UrlDetector.isMusic(title, url) ? "audio/*" : "video/*");
        intent.putExtra("title", title);
        intent.putExtra("name", title);
        if (extras != null) {
            for (Map.Entry<String, String> entry : extras.entrySet()) {
                intent.putExtra(entry.getKey(), entry.getValue());
            }
        }
        intent.addFlags(0x10008000);
        String msg = "";
        if (appInstalledOrNot(context, pkg)) {
            try {
                if (consumer != null) {
                    consumer.accept(intent);
                }
                intent.setComponent(new ComponentName(pkg, activity));
                context.startActivity(intent);
                return true;
            } catch (Exception e) {
                Timber.e(e);
                msg = e.getMessage();
            }
        }
        ToastMgr.shortBottomCenter(context, "没有安装" + playerName + "！" + msg);
        return false;
    }

    /**
     * 官 decorateHeader(Activity,String,String)（PlayerChooser.smali:626，
     * .line 304-316）照官转写，第二参为页面址、第三参为资源址：当前 WebView
     * 经 MultiWindowManager.getCurrentWebView 取、请求头表经
     * WebViewHelper.getRequestHeaderMap(webView, url) 取（官静态形：WebView/
     * helper 缺位回 null 表，WebViewHelper.smali:1014-1043）；try 内以 WebView
     * 的 referer 策略算 referer、CookieManager 取 url 的 cookie、空且 referer
     * 非空时改取 referer 的 cookie，异常只 Timber；尾 decorateHeader0(map,
     * null, url, cookie)。getReferer/decorateHeader0 接 HkPlayerChooser 同名
     * 转写（与本类 Map 版同一链；HkWebkitHolder.BridgeObject.getHeaderUrl
     * :1885-1900 正是同链的咱家在位用例）。
     * E1 尾巴（136b 簇2）：咱家等价持有件是 HkWebkitHolder 的私有内部类
     * Wrapper（HkWebViewWrapper 实现：getRequestHeaderMap 回全表、
     * getRefererPolicy 回策略）。公开面可达链已逐跳核过——Activity→
     * FragmentManager→HkArticleFragment.getWebViewHolder()（公开）→
     * HkWebViewHolder.getWebkitHolder()（公开）→HkWebkitHolder 到此止步：
     * jsBridgeHolder 为 protected 字段、无公开 getter，Wrapper 实例只在
     * FyBridge 内（其 getWrapper() 公开但 FyBridge 本体不可达），
     * requestHeaderMap/refererPolicy 在 Holder 上亦无公开 getter。故来源改走
     * HkPlayerChooser 的可替换口（currentWrapper：provider 优先、登记表兜底），
     * 主线只须在 HkWebkitHolder.initWebView 建 FyBridge 后补一行登记
     * （HkPlayerChooser.registerCurrentWrapper(activity,
     * jsBridgeHolder.getWrapper())，见 fill-136b-c2 分账「主线缝」），本法与
     * HkPlayerChooser 一字不用再改；登记未补/当前页无 Wrapper 时 map 与策略
     * 皆 null，与官 helper 缺位形逐字同终局。
     */
    public static String decorateHeader(Activity activity, String pageUrl, String url) {
        HkWebViewWrapper wrapper = HkPlayerChooser.currentWrapper(activity);
        Map<String, String> map = null;
        if (wrapper != null) {
            Map<String, Map<String, String>> all = wrapper.getRequestHeaderMap();
            map = all == null ? null : all.get(url);
        }
        String cookie = "";
        try {
            String policy = wrapper == null ? null : wrapper.getRefererPolicy();
            String referer = HkPlayerChooser.getReferer(map, policy, pageUrl, url);
            cookie = CookieManager.getInstance().getCookie(url);
            if (StringUtil.isEmpty(cookie) && StringUtil.isNotEmpty(referer)) {
                cookie = CookieManager.getInstance().getCookie(referer);
            }
        } catch (Exception e) {
            Timber.e(e);
        }
        return HkPlayerChooser.decorateHeader0(map, null, url, cookie);
    }

    /**
     * 官 startMxPlayer（PlayerChooser.smali:4058，.line 996-1041）照官转写：
     * headers 非空时逐项另建两表——编码表（encodeUrl(k)+"="+encodeUrl(v)，
     * 官寄存器在 putExtra 时被 "headers" 键覆写、该表实际未用，照官保留其
     * 构建）与原样交替表（k,v,k,v…），"headers" 键 put 的是原样交替表的
     * String[]（CollectionUtil.toStrArray）；字幕非空经 initSubtitleUrl 换址后
     * put "subs"（Parcelable[]{Uri}）、"subs.name"（String[]{"外挂字幕"}）、
     * "subs.enable"（Parcelable[]{Uri}）三键（MX 私有数组形逐字照官）。
     * 目标两级：com.mxtech.videoplayer.ad/.ActivityScreen（包内类名
     * com.mxtech.videoplayer.ad.ActivityScreen）先行，未装或起动异常（Timber
     * 后留其 message）续试 com.mxtech.videoplayer.pro/
     * com.mxtech.videoplayer.ActivityScreen，第二级异常时 message 拼
     * 前值+"____"+新 message；终局 toast「没有安装MXPlayer！」+message 回
     * false。encodeUrl 接本树 HttpParser 同名转写 HkHttpParser.encodeUrl。
     */
    private static boolean startMxPlayer(Context context, String title, String url,
                                         Map<String, String> headers, String subtitle) {
        url = getThirdPlaySource(url);
        Intent intent = new Intent();
        if (headers != null && !headers.isEmpty()) {
            List<String> encodedPairs = new ArrayList<>();
            List<String> rawPairs = new ArrayList<>();
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                encodedPairs.add(HkHttpParser.encodeUrl(entry.getKey())
                        + "=" + HkHttpParser.encodeUrl(entry.getValue()));
                rawPairs.add(entry.getKey());
                rawPairs.add(entry.getValue());
            }
            intent.putExtra("headers", CollectionUtil.toStrArray(rawPairs));
        }
        if (StringUtil.isNotEmpty(subtitle)) {
            String subtitleUrl = initSubtitleUrl(context, title, url, headers, subtitle);
            intent.putExtra("subs", new Parcelable[]{Uri.parse(subtitleUrl)});
            intent.putExtra("subs.name", new String[]{"外挂字幕"});
            intent.putExtra("subs.enable", new Parcelable[]{Uri.parse(subtitleUrl)});
        }
        intent.setAction("android.intent.action.VIEW");
        intent.setDataAndType(FilesInAppUtil.getUri(context, url),
                UrlDetector.isMusic(title, url) ? "audio/*" : "video/*");
        intent.putExtra("title", title);
        intent.putExtra("name", title);
        intent.addFlags(0x10008000);
        String msg;
        String adPkg = "com.mxtech.videoplayer.ad";
        if (appInstalledOrNot(context, adPkg)) {
            try {
                intent.setComponent(new ComponentName(adPkg, "com.mxtech.videoplayer.ad.ActivityScreen"));
                context.startActivity(intent);
                return true;
            } catch (Exception e) {
                Timber.e(e);
                msg = e.getMessage();
            }
        } else {
            msg = "";
        }
        String proPkg = "com.mxtech.videoplayer.pro";
        if (appInstalledOrNot(context, proPkg)) {
            try {
                intent.setComponent(new ComponentName(proPkg, "com.mxtech.videoplayer.ActivityScreen"));
                context.startActivity(intent);
                return true;
            } catch (Exception e) {
                Timber.e(e);
                msg = msg + "____" + e.getMessage();
            }
        }
        ToastMgr.shortBottomCenter(context, "没有安装MXPlayer！" + msg);
        return false;
    }

    /** 官 startXPlayer（smali:7052，.line 1235）：六参 startPlayerById，名 "XPlayer"。 */
    private static boolean startXPlayer(Context context, String title, String url) {
        return startPlayerById(context, title, url, "XPlayer",
                "video.player.videoplayer", "com.inshot.xplayer.activities.PlayerActivity");
    }

    /**
     * 官 startKMPlayer（smali:3379，.line 1079-1117）照官转写：标准 VIEW 意图
     * 三级试——①com.kmplayerpro/com.kmplayer.activity.VideoPlayerActivity；
     * ②com.kmplayer/com.kmplayer.MainActivity（异常时 message 拼
     * 前值+"____"+新 message）；③再查 com.kmplayerpro 在装时以
     * （com.kmplayerpro, com.kmplayer.MainActivity）试（包/类跨配照官原文），
     * 其异常 message 直接覆写。终局 toast「没有安装KMPlayer！」+message 回 false。
     */
    private static boolean startKMPlayer(Context context, String title, String url) {
        url = getThirdPlaySource(url);
        Intent intent = buildStandardViewIntent(context, title, url);
        String msg;
        String proPkg = "com.kmplayerpro";
        if (appInstalledOrNot(context, proPkg)) {
            try {
                intent.setComponent(new ComponentName(proPkg, "com.kmplayer.activity.VideoPlayerActivity"));
                context.startActivity(intent);
                return true;
            } catch (Exception e) {
                Timber.e(e);
                msg = e.getMessage();
            }
        } else {
            msg = "";
        }
        String freePkg = "com.kmplayer";
        if (appInstalledOrNot(context, freePkg)) {
            try {
                intent.setComponent(new ComponentName(freePkg, "com.kmplayer.MainActivity"));
                context.startActivity(intent);
                return true;
            } catch (Exception e) {
                Timber.e(e);
                msg = msg + "____" + e.getMessage();
            }
        }
        if (appInstalledOrNot(context, proPkg)) {
            try {
                intent.setComponent(new ComponentName(proPkg, "com.kmplayer.MainActivity"));
                context.startActivity(intent);
                return true;
            } catch (Exception e) {
                Timber.e(e);
                msg = e.getMessage();
            }
        }
        ToastMgr.shortBottomCenter(context, "没有安装KMPlayer！" + msg);
        return false;
    }

    /**
     * 官 startMoboPlayer（摩播，smali:3740，.line 1046-1074）照官转写：标准
     * VIEW 意图两级试，同类 com.clov4r.android.nil.ui.activity.MainActivity——
     * ①包 com.clov4r.android.nil.noad；②包 com.clov4r.android.nil（异常时
     * message 拼前值+"____"+新 message）。终局 toast「没有安装MoboPlayer！」
     * +message 回 false。
     */
    private static boolean startMoboPlayer(Context context, String title, String url) {
        url = getThirdPlaySource(url);
        Intent intent = buildStandardViewIntent(context, title, url);
        String msg;
        String cls = "com.clov4r.android.nil.ui.activity.MainActivity";
        String noadPkg = "com.clov4r.android.nil.noad";
        if (appInstalledOrNot(context, noadPkg)) {
            try {
                intent.setComponent(new ComponentName(noadPkg, cls));
                context.startActivity(intent);
                return true;
            } catch (Exception e) {
                Timber.e(e);
                msg = e.getMessage();
            }
        } else {
            msg = "";
        }
        String plainPkg = "com.clov4r.android.nil";
        if (appInstalledOrNot(context, plainPkg)) {
            try {
                intent.setComponent(new ComponentName(plainPkg, cls));
                context.startActivity(intent);
                return true;
            } catch (Exception e) {
                Timber.e(e);
                msg = msg + "____" + e.getMessage();
            }
        }
        ToastMgr.shortBottomCenter(context, "没有安装MoboPlayer！" + msg);
        return false;
    }

    /** 官 startQQPlayer（smali:6527，.line 1243）：六参 startPlayerById，名「QQ浏览器」。 */
    private static boolean startQQPlayer(Context context, String title, String url) {
        return startPlayerById(context, title, url, "QQ浏览器",
                "com.tencent.mtt", "com.tencent.mtt.browser.video.H5VideoThrdcallActivity");
    }

    /** 官 startVLC（smali:6758，.line 1239）：六参 startPlayerById，名 "VLC"。 */
    private static boolean startVLC(Context context, String title, String url) {
        return startPlayerById(context, title, url, "VLC",
                "org.videolan.vlc", "org.videolan.vlc.StartActivity");
    }

    /** 官 startUCPlayer（smali:6735，.line 1227）：六参 startPlayerById，名「UC浏览器」。 */
    private static boolean startUCPlayer(Context context, String title, String url) {
        return startPlayerById(context, title, url, "UC浏览器",
                "com.UCMobile", "com.UCMobile.main.UCMobile");
    }

    /** 官 startUCIntlPlayer（smali:6712，.line 1231）：六参 startPlayerById，名「UC国际版」、类与 UC 支同名。 */
    private static boolean startUCIntlPlayer(Context context, String title, String url) {
        return startPlayerById(context, title, url, "UC国际版",
                "com.UCMobile.intl", "com.UCMobile.main.UCMobile");
    }

    /**
     * 官 startDDPlayPlayer（弹弹，smali:3210，.line 958-991）照官转写：VIEW
     * 意图只 put "video_title"（不 put title/name）；包 com.xyoye.dandanplay
     * 在装时同包三类依次试——①com.xyoye.player_component.ui.activities.
     * player_intent.PlayerIntentActivity、②com.xyoye.dandanplay.ui.activities.
     * play.PlayerManagerActivity、③com.xyoye.dandanplay.ui.activities.
     * PlayerManagerActivity，前两级异常只 Timber（message 不留）、第三级留
     * message；任一级起动成功回 true。终局 toast「没有安装弹弹Play！」+message
     * 回 false。
     */
    private static boolean startDDPlayPlayer(Context context, String title, String url) {
        url = getThirdPlaySource(url);
        Intent intent = new Intent();
        intent.setAction("android.intent.action.VIEW");
        intent.setDataAndType(FilesInAppUtil.getUri(context, url),
                UrlDetector.isMusic(title, url) ? "audio/*" : "video/*");
        intent.putExtra("video_title", title);
        intent.addFlags(0x10008000);
        String pkg = "com.xyoye.dandanplay";
        String msg = "";
        if (appInstalledOrNot(context, pkg)) {
            String[] classes = {
                    "com.xyoye.player_component.ui.activities.player_intent.PlayerIntentActivity",
                    "com.xyoye.dandanplay.ui.activities.play.PlayerManagerActivity",
                    "com.xyoye.dandanplay.ui.activities.PlayerManagerActivity"
            };
            for (int i = 0; i < classes.length; i++) {
                try {
                    intent.setComponent(new ComponentName(pkg, classes[i]));
                    context.startActivity(intent);
                    return true;
                } catch (Exception e) {
                    Timber.e(e);
                    if (i == classes.length - 1) {
                        msg = e.getMessage();
                    }
                }
            }
        }
        ToastMgr.shortBottomCenter(context, "没有安装弹弹Play！" + msg);
        return false;
    }

    /**
     * 官 startNPlayPlayer（smali:4421，.line 934-953）照官转写：VIEW 意图 put
     * "fileName"/"file_name"（皆 title）与 "playPath"（url 串本体、非 Uri）；
     * 包 com.newin.nplayer.pro、类 com.newin.nplayer.activities.BridgeActivity
     * 单级试。终局 toast「没有安装nPlayer！」+message 回 false。
     */
    private static boolean startNPlayPlayer(Context context, String title, String url) {
        url = getThirdPlaySource(url);
        Intent intent = new Intent();
        intent.setAction("android.intent.action.VIEW");
        intent.setDataAndType(FilesInAppUtil.getUri(context, url),
                UrlDetector.isMusic(title, url) ? "audio/*" : "video/*");
        intent.putExtra("fileName", title);
        intent.putExtra("file_name", title);
        intent.putExtra("playPath", url);
        intent.addFlags(0x10008000);
        String msg;
        String pkg = "com.newin.nplayer.pro";
        if (appInstalledOrNot(context, pkg)) {
            try {
                intent.setComponent(new ComponentName(pkg, "com.newin.nplayer.activities.BridgeActivity"));
                context.startActivity(intent);
                return true;
            } catch (Exception e) {
                Timber.e(e);
                msg = e.getMessage();
            }
        } else {
            msg = "";
        }
        ToastMgr.shortBottomCenter(context, "没有安装nPlayer！" + msg);
        return false;
    }

    /**
     * 官 startWVCaster（smali:6874，.line 1247-1265）照官转写：extras 先照
     * 波澜同式拼 "url"（file:/content// 开头经 FilesInAppUtil.getUri 换串）；
     * headers 非空时逐项 putString 进新 Bundle、经 Consumer 在 startPlayerById
     * 起动前 putExtra("android.media.intent.extra.HTTP_HEADERS", bundle)
     * （官 lambda$startWVCaster$6，smali:2532）；字幕非空经 initSubtitleUrl
     * 换址后 put "subtitle"；尾走八参 startPlayerById（名 "Web Video Caster"、
     * 包 com.instantbits.cast.webvideo、类 com.instantbits.cast.webvideo.WebBrowser）。
     */
    private static boolean startWVCaster(Context context, String title, String url,
                                         Map<String, String> headers, String subtitle) {
        Map<String, String> extras = new HashMap<>();
        String playUrl;
        if (url.startsWith("file:") || url.startsWith("content") || url.startsWith("/")) {
            playUrl = FilesInAppUtil.getUri(context, url).toString();
        } else {
            playUrl = url;
        }
        extras.put("url", playUrl);
        Consumer<Intent> consumer = null;
        if (headers != null && !headers.isEmpty()) {
            Bundle headersBundle = new Bundle();
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                headersBundle.putString(entry.getKey(), entry.getValue());
            }
            consumer = intent -> intent.putExtra("android.media.intent.extra.HTTP_HEADERS", headersBundle);
        }
        if (StringUtil.isNotEmpty(subtitle)) {
            String subtitleUrl = initSubtitleUrl(context, title, url, headers, subtitle);
            extras.put("subtitle", subtitleUrl);
        }
        return startPlayerById(context, title, url, "Web Video Caster", extras,
                "com.instantbits.cast.webvideo", "com.instantbits.cast.webvideo.WebBrowser", consumer);
    }

    /**
     * 官 startLuaPlayer（smali:3618，.line 911-929）照官转写：VIEW 意图先 put
     * "url" 与 "externalPlay:extractText" 两键、值皆为 title（照官寄存器原文，
     * 非播放址）；再 setDataAndType＋addFlags(0x10008000)；包
     * ms.dev.luaplayer_pro、类 ms.dev.activity.AVExternalActivity 单级试。
     * 终局 toast「没有安装 Lua Player！」+message 回 false。
     */
    private static boolean startLuaPlayer(Context context, String title, String url) {
        url = getThirdPlaySource(url);
        Intent intent = new Intent();
        intent.setAction("android.intent.action.VIEW");
        intent.putExtra("url", title);
        intent.putExtra("externalPlay:extractText", title);
        intent.setDataAndType(FilesInAppUtil.getUri(context, url),
                UrlDetector.isMusic(title, url) ? "audio/*" : "video/*");
        intent.addFlags(0x10008000);
        String msg;
        String pkg = "ms.dev.luaplayer_pro";
        if (appInstalledOrNot(context, pkg)) {
            try {
                intent.setComponent(new ComponentName(pkg, "ms.dev.activity.AVExternalActivity"));
                context.startActivity(intent);
                return true;
            } catch (Exception e) {
                Timber.e(e);
                msg = e.getMessage();
            }
        } else {
            msg = "";
        }
        ToastMgr.shortBottomCenter(context, "没有安装 Lua Player！" + msg);
        return false;
    }

    /** 官 startKodi（smali:3595，.line 1206）：六参 startPlayerById，名 "Kodi"。 */
    private static boolean startKodi(Context context, String title, String url) {
        return startPlayerById(context, title, url, "Kodi",
                "org.xbmc.kodi", "org.xbmc.kodi.Splash");
    }

    /**
     * 官 startNextPlayer（smali:4548，.line 1210-1223）：与 startBolanAssist
     * 同式拼 extras（"url" 换串/"header" fastjson 串/"subtitle" 换址后），尾走
     * 七参 startPlayerById（名 "Next Player"、包 dev.anilbeesetti.nextplayer、
     * 类 dev.anilbeesetti.nextplayer.feature.player.PlayerActivity）。
     */
    private static boolean startNextPlayer(Context context, String title, String url,
                                           Map<String, String> headers, String subtitle) {
        Map<String, String> extras = new HashMap<>();
        String playUrl;
        if (url.startsWith("file:") || url.startsWith("content") || url.startsWith("/")) {
            playUrl = FilesInAppUtil.getUri(context, url).toString();
        } else {
            playUrl = url;
        }
        extras.put("url", playUrl);
        if (headers != null) {
            extras.put("header", JSON.toJSONString(headers));
        }
        if (StringUtil.isNotEmpty(subtitle)) {
            String subtitleUrl = initSubtitleUrl(context, title, url, headers, subtitle);
            extras.put("subtitle", subtitleUrl);
        }
        return startPlayerById(context, title, url, "Next Player", extras,
                "dev.anilbeesetti.nextplayer", "dev.anilbeesetti.nextplayer.feature.player.PlayerActivity");
    }

    /**
     * 官 startSystemPlayer（smali:6622，.line 1160-1171）照官转写：不用
     * startPlayerById、无装机查——try 内新 Intent put "title"/"name"、
     * setAction(ACTION_VIEW)、setFlags(3)（读/写 URI 授权位、是 setFlags 非
     * addFlags）、setDataAndType 后直 startActivity；headers/subtitle 两参官
     * 本体不用。异常 Timber 后 shortCenter toast「找不到系统播放器！」；
     * 官尾恒回 true（异常支亦然），照官保留此终局。
     */
    private static boolean startSystemPlayer(Context context, String title, String url,
                                             Map<String, String> headers, String subtitle) {
        try {
            Intent intent = new Intent();
            intent.putExtra("title", title);
            intent.putExtra("name", title);
            intent.setAction("android.intent.action.VIEW");
            intent.setFlags(3);
            intent.setDataAndType(FilesInAppUtil.getUri(context, url),
                    UrlDetector.isMusic(title, url) ? "audio/*" : "video/*");
            context.startActivity(intent);
        } catch (Exception e) {
            Timber.e(e);
            ToastMgr.shortCenter(context, "找不到系统播放器！");
        }
        return true;
    }

    /**
     * 官 startPlayer 八参（PlayerChooser.smali:5793，.line 569-620）分发本体
     * 照官转写，参义照官包内五参形调用点（smali:5723）：(Context, 播放器名,
     * title, url, originalUrl, headers, subtitle, bundle)——url 先
     * getThirdPlaySource＋getRealUrlForRemotedPlay 换播放址供第三方支，
     * 内嵌支的 videourl 用 originalUrl；播放器名与 PlayerEnum 显示名逐字
     * 比对分发（官为 playerName.equals(枚举名) 链）。
     * 咱家逐支落点（分发序与入参逐支照官 .line 570-603）：MX→startMxPlayer、
     * X→startXPlayer、KM→startKMPlayer、MO_BO→startMoboPlayer、QQ→
     * startQQPlayer、VLC→startVLC、UC→startUCPlayer、UC_INTL→
     * startUCIntlPlayer、DAN_DAN→startDDPlayPlayer、N→startNPlayPlayer、
     * WEB_VIDEO_CASTER→startWVCaster、LUA→startLuaPlayer、KODI→startKodi、
     * NEXT→startNextPlayer（以上 136b 簇2 逐支照官落，见各法注）、REEX→
     * startReex、BO_LAN_ASSIST→startBolanAssist、SYSTEM→startSystemPlayer；
     * 内嵌支（PLAYER_TWO 与未知名落穿）官开 VideoPlayerActivity（extras
     * title/videourl=originalUrl/fromActivity=ctx.hashCode()、bundle 非空经
     * checkSize 后存 extraDataBundle、UrlDetector.isMusic 时按
     * resolveActivity 加窗旗），咱家播放宿主等价面＝HkDetailBridge
     * .startPlayerSingle（与本类三参 startPlayer 同落点，bundle 的 extraData
     * 语义在该路自理、不在此重建，记名）。各第三方支的官终局照原样：目标
     * 未装回 false（SYSTEM 支官本体无装机查、恒回 true），不许落内嵌支冒充。
     * 注：咱家比对向与官相反（枚举名.equals(playerId) 对官 playerId.equals
     * (枚举名)）——playerId 为 null 时官抛 NPE、咱家落内嵌支，136 批既有口径，
     * 本簇不动此点、记名在分账。
     */
    public static boolean startPlayer(Context context, String playerId, String title, String url,
                                      String originalUrl, Map<String, String> headers,
                                      String subtitle, Bundle bundle) {
        String playUrl = getRealUrlForRemotedPlay(getThirdPlaySource(url));
        if (PlayerEnum.MX_PLAYER.getName().equals(playerId)) {
            return startMxPlayer(context, title, playUrl, headers, subtitle);
        }
        if (PlayerEnum.X_PLAYER.getName().equals(playerId)) {
            return startXPlayer(context, title, playUrl);
        }
        if (PlayerEnum.KM_PLAYER.getName().equals(playerId)) {
            return startKMPlayer(context, title, playUrl);
        }
        if (PlayerEnum.MO_BO_PLAYER.getName().equals(playerId)) {
            return startMoboPlayer(context, title, playUrl);
        }
        if (PlayerEnum.QQ_PLAYER.getName().equals(playerId)) {
            return startQQPlayer(context, title, playUrl);
        }
        if (PlayerEnum.VLC_PLAYER.getName().equals(playerId)) {
            return startVLC(context, title, playUrl);
        }
        if (PlayerEnum.UC_PLAYER.getName().equals(playerId)) {
            return startUCPlayer(context, title, playUrl);
        }
        if (PlayerEnum.UC_INTL_PLAYER.getName().equals(playerId)) {
            return startUCIntlPlayer(context, title, playUrl);
        }
        if (PlayerEnum.DAN_DAN_PLAYER.getName().equals(playerId)) {
            return startDDPlayPlayer(context, title, playUrl);
        }
        if (PlayerEnum.N_PLAYER.getName().equals(playerId)) {
            return startNPlayPlayer(context, title, playUrl);
        }
        if (PlayerEnum.WEB_VIDEO_CASTER.getName().equals(playerId)) {
            return startWVCaster(context, title, playUrl, headers, subtitle);
        }
        if (PlayerEnum.LUA_PLAYER.getName().equals(playerId)) {
            return startLuaPlayer(context, title, playUrl);
        }
        if (PlayerEnum.KODI.getName().equals(playerId)) {
            return startKodi(context, title, playUrl);
        }
        if (PlayerEnum.NEXT_PLAYER.getName().equals(playerId)) {
            return startNextPlayer(context, title, playUrl, headers, subtitle);
        }
        if (PlayerEnum.REEX.getName().equals(playerId)) {
            return startReex(context, title, playUrl, headers, subtitle);
        }
        if (PlayerEnum.BO_LAN_ASSIST.getName().equals(playerId)) {
            return startBolanAssist(context, title, playUrl, headers, subtitle);
        }
        if (PlayerEnum.SYSTEM.getName().equals(playerId)) {
            return startSystemPlayer(context, title, playUrl, headers, subtitle);
        }
        Activity activity = activityOf(context);
        if (activity == null) {
            return false;
        }
        new HkDetailBridge().startPlayerSingle(activity, title, originalUrl);
        return true;
    }

    /**
     * 官 LocalServerParser.getRealUrlForRemotedPlay 的咱家等价体
     * HkDlanController.realUrlForRemotedPlay（api.hk 包内私有、跨包不可达）
     * 的公式内联：空直回（null 回 ""）、video:// 剥前缀直回、http://127.0.0.1
     * 前缀换局域网 IP（HkProxyServer.lanIp 接同链）；其 file:// 文件服务支
     * （官本体在 DlanController 内亦无此支、由 getRealUrl 单独承接）不在此式内。
     */
    private static String getRealUrlForRemotedPlay(String url) {
        if (StringUtil.isEmpty(url)) {
            return url == null ? "" : url;
        }
        if (url.startsWith("video://")) {
            return url.substring("video://".length());
        }
        if (url.startsWith("http://127.0.0.1")) {
            String ip = HkProxyServer.lanIp();
            if (StringUtil.isNotEmpty(ip)) {
                return url.replace("127.0.0.1", ip);
            }
        }
        return url;
    }

    private static Activity activityOf(Context context) {
        if (context instanceof Activity) {
            return (Activity) context;
        }
        return ActivityManager.getInstance().getCurrentActivity();
    }
}
