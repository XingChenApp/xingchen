package com.fongmi.android.tv.ui.hk;

import android.app.Activity;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.hk.HkHttpParser;
import com.fongmi.android.tv.api.hk.util.HkCodeUtil;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.utils.ToastUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 环42 danmu# 宿主承接：8.83 VideoPlayerActivity$12／FloatVideoController
 * $showDanmuSearchPopup$1 两个 DanmuSearchListener 实现＋两宿主同构的
 * loadDanmaku(String) 的转写，宿主＝咱家 VideoActivity（mobile）。
 *
 * <p>绑定公式照官 smali 本体（Float $1 click 源行 1702-1725）：当前集 cur
 * （集表里 isUse 的下标）为锚，sortMode 0/1 同式 bind(i)＝pos＋(i－cur)，
 * sortMode 2 反式 bind(i)＝pos＋(cur－i)，越界（&lt;0 或 ≥results.size）
 * 不写。注：dig2 账记「1/2 倒序」与官 smali 不符，以官包本体为准
 * （1＝「因当前顺序不对而倒序」只反列表、绑定仍顺式；2＝「为查看最新
 * 剧集而倒序」列表与绑定双反）。</p>
 *
 * <p>切集续绑的消费点照官 VideoPlayerActivity 源行 6725-6728：新集起播时
 * danmPopup 在场且 danmBindMap 非空 → 以 nowPos 查表，命中即回调用起面
 * fetchDanmu(绑定下标)（fetchDanmu 内先 startLoad 清表、再由 click 按新
 * 当前集重建绑定，官形自洽）。</p>
 *
 * <p>useDanmuku 的等价落点＝PlayerManager.setDanmaku（file:// 静态源）：
 * 空 url → Danmaku.empty() 关面；下载成功 → 落盘文件入面；下载失败 →
 * ToastMgr.shortBottomCenter(msg 原文)＋关面，与官 onFailure 两件同形。
 * 官 loadDanmaku 入口读的 danmuLine/danmuSpeed/danmuTrans/danmuMargin
 * 四偏好在咱家由播放器 DanmakuSetting 统一治理（渲染参数同一消费面），
 * 本承接不另读 share_data 的 danmu* 键（载体差记名）。官 web:// 支的
 * useWebDanmuku（网页弹幕面）咱播放器无此面，该段不接（记名）；
 * 且本链 dealUrl 已先行把 web:// 分流去网页，loadDanmaku 正常流向
 * 收不到 web://。</p>
 */
public final class HkDanmuBinder implements HkDanmuSearchDialog.DanmuSearchListener {

    /** 集表快照（官静态 VideoPlayerActivity.chapters 的宿主等价）：总数＋当前集下标（无当前集回 -1）。 */
    public interface EpisodeProvider {
        int count();

        int currentIndex();
    }

    /** 切集续绑的回调用起面（官宿主持 danmPopup 字段直调 fetchDanmu）。 */
    public interface Fetcher {
        void fetchDanmu(int pos);
    }

    private final Map<Integer, Integer> danmBindMap = new HashMap<>();
    private EpisodeProvider episodeProvider;
    private Fetcher fetcher;
    private Runnable popupDismiss;
    private PlayerManager player;
    private Activity activity;

    public void setEpisodeProvider(EpisodeProvider episodeProvider) {
        this.episodeProvider = episodeProvider;
    }

    public void setFetcher(Fetcher fetcher) {
        this.fetcher = fetcher;
    }

    public void setPopupDismiss(Runnable popupDismiss) {
        this.popupDismiss = popupDismiss;
    }

    /** 调起面 show 时由宿主注入当前播放器与 Activity（官宿主字段同位）。 */
    public void attach(PlayerManager player, Activity activity) {
        this.player = player;
        this.activity = activity;
    }

    /** 官 DanmuSearchListener.startLoad：清分集绑定表。 */
    @Override
    public void startLoad() {
        danmBindMap.clear();
    }

    /** 官 DanmuSearchListener.click：先 dismiss 调起面，再按当前集建绑定表，末尾 loadDanmaku(url)。 */
    @Override
    public void click(int pos, List<HkDanmuResult> results, String url, int sortMode) {
        if (popupDismiss != null) popupDismiss.run();
        int count = episodeProvider == null ? 0 : episodeProvider.count();
        if (count > 0 && results != null) {
            int cur = episodeProvider.currentIndex();
            if (cur >= 0) {
                for (int i = 0; i < count; i++) {
                    int bind = sortMode == 2 ? pos + (cur - i) : pos + (i - cur);
                    if (bind >= 0 && bind < results.size()) danmBindMap.put(i, bind);
                }
            }
        }
        loadDanmaku(url);
    }

    /** 官切集消费点：新集起播时以 nowPos 查绑定表，命中回调用起面 fetchDanmu。 */
    public void onEpisodeStarted(int nowPos) {
        if (fetcher == null || danmBindMap.isEmpty()) return;
        Integer idx = danmBindMap.get(nowPos);
        if (idx != null) fetcher.fetchDanmu(idx);
    }

    /** 测试/夹具只读口：绑定表快照。 */
    Map<Integer, Integer> bindMapSnapshot() {
        return new HashMap<>(danmBindMap);
    }

    /**
     * 官 loadDanmaku(String) 转写（Float:5573／Activity:18230 两处同构，
     * 取 Float lambda$loadDanmaku$47 的下载段）：格式判定只认 url 含
     * ".json"；目标文件恒 hiker://files/cache/danmu.json|danmu.xml；
     * 下载地址＝url 按 ";" split 取首段（尾空段丢弃，Java split 同义）；
     * 请求头＝HttpParser.getHeaders(原 url)。
     */
    public void loadDanmaku(String url) {
        final PlayerManager p = player;
        if (p == null) return;
        if (TextUtils.isEmpty(url)) {
            // 官：useDanmuku(false, null文件, …) 关弹幕面
            p.setDanmaku(Danmaku.empty());
            return;
        }
        if (url.startsWith("web://")) {
            // 官此支 useWebDanmuku(true, url, …)：咱播放器无网页弹幕面，
            // 该段不接（记名）——不许拿下载支硬凑。本链 dealUrl 已先行
            // 把 web:// 分流去网页，正常流向走不到这里。
            com.github.catvod.crawler.SpiderDebug.log("hk-danmu", "web danmaku not supported url=%s", url);
            return;
        }
        String format = url.contains(".json") ? "json" : "xml";
        String target = "hiker://files/cache/danmu." + format;
        String downloadUrl = url.split(";")[0];
        java.util.Map<String, String> headers = HkHttpParser.getHeaders(url);
        HkCodeUtil.download(downloadUrl, target, headers, new HkCodeUtil.OnCodeGetListener() {
            @Override
            public void onSuccess(String s) {
                App.post(() -> p.setDanmaku(Danmaku.from(s)));
            }

            @Override
            public void onFailure(int code, String msg) {
                App.post(() -> {
                    // 官 onFailure 两件：ToastMgr.shortBottomCenter(msg 原文)＋关弹幕面
                    if (activity != null) ToastUtil.show(activity, msg, android.widget.Toast.LENGTH_SHORT);
                    p.setDanmaku(Danmaku.empty());
                });
            }
        });
    }
}
