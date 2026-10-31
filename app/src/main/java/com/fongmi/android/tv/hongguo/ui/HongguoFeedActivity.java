package com.fongmi.android.tv.hongguo.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.HongguoDanmakuApi;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.hongguo.api.HgAccountStore;
import com.fongmi.android.tv.hongguo.api.HgApiClient;
import com.fongmi.android.tv.hongguo.api.HgCallback;
import com.fongmi.android.tv.hongguo.drama.DramaAlbumDetail;
import com.fongmi.android.tv.hongguo.drama.DramaCache;
import com.fongmi.android.tv.hongguo.drama.DramaClient;
import com.fongmi.android.tv.hongguo.drama.DramaDetail;
import com.fongmi.android.tv.hongguo.drama.DramaItem;
import com.fongmi.android.tv.hongguo.drama.DramaItemKind;
import com.fongmi.android.tv.hongguo.drama.DramaResolver;
import com.fongmi.android.tv.hongguo.drama.DramaSeriesPrefetch;
import com.fongmi.android.tv.hongguo.drama.DramaVideoModel;
import com.fongmi.android.tv.hongguo.model.HgContinuation;
import com.fongmi.android.tv.hongguo.ui.HgPanelLogic;
import com.fongmi.android.tv.player.engine.ExoPlayerEngine;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.setting.PlayerSetting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 红果竖屏流播放页（2026-10-30 从零重建；同日 UI 同步批按定稿改）：
 * 首页混合单流（MODE_FEED）、单剧集流（MODE_SERIES，详情/搜索/我的页
 * 进入）、视频页（MODE_VIDEO，合集队列/单条短片）与旧 UGC 单条
 * （MODE_UGC，作者作品等旧入口）共用一页。
 *
 * <p>形态（定稿口径，2026-10-30 晚页面层重做）：全屏竖滑 Pager＋
 * 单 PlayerView 随页迁移。首页顶左只有三条杠（抽屉＝片库两行直达＋
 * 播放设置，浅色面），底部两层分色——观看完整剧条＝灰带 #3A3A40、
 * 细进度线贴剧条下缘、底栏黑 #202023 纯文字两签（他亲口定案，两层
 * 必须分得开）；条目左下只有剧名＋题材标签筹＋AI 声明（数据驱动），
 * 右列头像在最上＋收藏＋评论裸图标。播放页顶栏玻璃圆返回＋「第N集」
 * ＋玻璃圆更多钮（倍速只走更多面板，顶栏倍速字钮已摘），整宽选集条
 * 贴底（清屏钮入条右端、只此一面），横屏入口两路：画面下方居中
 * 「全屏观看」钮＋传感器横转直进（UI同步C 曾摘传感器路，
 * 2026-10-31 他拍板加回、按官方直接旋转）。视频页左下作者行＋标题＋文案，
 * 底部合集条＋进度线。手势：单击播放/暂停、长按中部开更多面板、
 * 长按其余处 2 倍速（松手恢复）。弹幕走 PlayerView 自带层（数据源
 * HongguoDanmakuApi 落盘 XML）；「发弹幕」药丸整件已摘（他定版：
 * 弹幕只看不发，账 triage2 §4.1/§4.2）。</p>
 *
 * <p>数据：频道/详情走 DramaClient（共享优先＋冷却降级）；解链走
 * DramaResolver；历史/断点/收藏走 HgStore 本地。右列点赞已接
 * （HgStore 本地镜像，登录后 HgAccountSync 拉取对齐）；追更/预约
 * 入口在详情页，本页不设。</p>
 */
public class HongguoFeedActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "hg_mode";
    public static final String EXTRA_SERIES_ID = "hg_series_id";
    public static final String EXTRA_TITLE = "hg_title";
    public static final String EXTRA_EPISODE = "hg_episode";
    /** 进页起播位（毫秒）：播完自动进剧的高光支带入（官方 currentPlaybackTime）；手动路不传。 */
    public static final String EXTRA_START_MS = "hg_start_ms";
    public static final int MODE_FEED = 0;
    public static final int MODE_SERIES = 1;
    public static final int MODE_UGC = 2;
    /** 视频页（定稿第 7 条）：与短剧播放页分开的独立页形态，合集队列或单条。 */
    public static final int MODE_VIDEO = 3;
    public static final String EXTRA_UGC_VID = "hg_ugc_vid";
    public static final String EXTRA_UGC_SERIES = "hg_ugc_series";
    public static final String EXTRA_ALBUM_ID = "hg_album_id";
    public static final String EXTRA_AUTHOR_UID = "hg_author_uid";
    public static final String EXTRA_AUTHOR_NAME = "hg_author_name";
    public static final String EXTRA_AUTHOR_AVATAR = "hg_author_avatar";

    /** 首页单流频道：混合发现流（正剧＋漫剧＋合集＋短片），与剧场视频签同口。 */
    private static final int[] HOME_CHANNEL = {-1, 16};
    private static final String HOME_POS_KEY = "首页";

    public static void start(Context context) {
        // 常驻口径（他 2026-10-30 定）：只要人不退出星辰，短剧首页
        // 就不算后台——已有实例直接提到前台复用，不新建、不重拉，
        // 数据/pager 位置/播放状态全留住；没有实例才新建。
        Intent intent = new Intent(context, HongguoFeedActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        HgTransition.fade(context, intent); // 签位口径：淡入复用，不硬切
    }

    /** 视频页·合集（定稿第 7 条）：按 album 拉全合集队列，底部合集条。 */
    public static void startVideoAlbum(Context context, DramaItem item) {
        Intent intent = new Intent(context, HongguoFeedActivity.class);
        intent.putExtra(EXTRA_MODE, MODE_VIDEO);
        // #10：albumId 缺失不许拿 seriesId 顶替——顶替后 fetchAlbumDetail
        // 拿剧 id 当专辑 id 拉回空/错详情，合集整页假死。缺 id 就传
        // null，视频页按单条路（入口 vid）起播，是诚实降级。
        intent.putExtra(EXTRA_ALBUM_ID, item.getAlbumId());
        intent.putExtra(EXTRA_UGC_VID, item.getVid());
        intent.putExtra(EXTRA_TITLE, item.getTitle());
        intent.putExtra(EXTRA_COVER, item.getCoverUrl());
        HgTransition.push(context, intent);
    }

    /**
     * 视频页·单条（定稿第 7 条）：一条短片，作者行＋文案，无合集条。
     * 例外（他 2026-10-31 点名「视频的合集没做」）：条目本身带 albumId
     * （是合集里的一集）时走合集入口——官方同口径：点合集里的视频
     * 进合集队列、落点在这集、底部有合集条，不再单条成页丢掉合集。
     */
    public static void startVideo(Context context, DramaItem item) {
        if (item.getAlbumId() != null && !item.getAlbumId().isEmpty()) {
            startVideoAlbum(context, item);
            return;
        }
        Intent intent = new Intent(context, HongguoFeedActivity.class);
        intent.putExtra(EXTRA_MODE, MODE_VIDEO);
        intent.putExtra(EXTRA_UGC_VID, item.getVid());
        intent.putExtra(EXTRA_UGC_SERIES, item.getSeriesId());
        intent.putExtra(EXTRA_TITLE, item.getTitle());
        intent.putExtra(EXTRA_COVER, item.getCoverUrl());
        intent.putExtra(EXTRA_CONTENT_TYPE, item.contentType);
        intent.putExtra(EXTRA_AUTHOR_UID, item.getAuthorUid());
        intent.putExtra(EXTRA_AUTHOR_NAME, item.getAuthorName());
        intent.putExtra(EXTRA_AUTHOR_AVATAR, item.getAuthorAvatar());
        HgTransition.push(context, intent);
    }

    /** UGC 单条直开（作者作品/动态条目）：一页一条，播 item 自带 vid。 */
    public static void startUgc(Context context, String vid, String seriesId, String title,
                                String cover, int contentType) {
        Intent intent = new Intent(context, HongguoFeedActivity.class);
        intent.putExtra(EXTRA_MODE, MODE_UGC);
        intent.putExtra(EXTRA_UGC_VID, vid);
        intent.putExtra(EXTRA_UGC_SERIES, seriesId);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_COVER, cover);
        intent.putExtra(EXTRA_CONTENT_TYPE, contentType);
        HgTransition.push(context, intent);
    }

    public static final String EXTRA_COVER = "hg_cover";
    public static final String EXTRA_CONTENT_TYPE = "hg_content_type";

    public static void startSeries(Context context, String seriesId, String title, int episodeIndex) {
        startSeries(context, seriesId, title, episodeIndex, -1);
    }

    /**
     * 进播放页并钉起播位：startMs&gt;=0 时首集起播位置以它为准（盖过
     * 本地断点存档，官方 ForceStartTime 口径）；手动路走四参版、
     * 不带起播位，行为不变。
     */
    public static void startSeries(Context context, String seriesId, String title,
                                   int episodeIndex, long startMs) {
        Intent intent = new Intent(context, HongguoFeedActivity.class);
        intent.putExtra(EXTRA_MODE, MODE_SERIES);
        intent.putExtra(EXTRA_SERIES_ID, seriesId);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_EPISODE, episodeIndex);
        if (startMs >= 0) intent.putExtra(EXTRA_START_MS, startMs);
        HgTransition.push(context, intent);
    }

    // ---------- 视图 ----------
    private FrameLayout root;
    private RecyclerView pager;
    private FeedAdapter adapter;
    private PlayerView playerView;
    private View topBar;
    private View seriesBar;
    private TextView seriesTitleView;
    /** 当前模式的底部整块（首页三层块/播放页选集条块/视频页合集条块）。 */
    private View bottomStack;
    private TextView watchText;
    private View watchBar;
    private TextView epBarText;
    private TextView albumBarText;
    private View albumBar;
    /** 已组装的进度行（FEED/UGC 是单线 wrap；SERIES/VIDEO 是含时间签的行）。 */
    private View progressRow;
    private View progressWrap;
    private View progressFill;
    private int progressFillColor;
    private View progressDot;
    /** 进度行两侧时间签（只有 SERIES/VIDEO 行建，tick 里同步刷新）。 */
    private TextView timeCurView;
    private TextView timeDurView;
    /** 清屏钮只在正剧播放页一面建（ImageView 图标钮，其余模式恒 null）。 */
    private ImageView cleanBtn;
    private ImageView centerFlash;
    private View stateView;
    private TextView stateText;
    private ProgressBar stateSpin;
    private View drawerScrim;
    private View drawerPanel;
    /** 抽屉两开关本体（openDrawer 每次按存档回读复位，防别处改键后显旧态）。 */
    private HgSwitch drawerDirectSwitch;
    private HgSwitch drawerDanmakuSwitch;
    /** 抽屉底部登录状态行（openDrawer 每次刷新，登录态可能在别处变）。 */
    private TextView drawerLoginText;
    /** 抽屉宽＝屏宽 79%（定稿 .drawer），开合位移按此值走。 */
    private int drawerWidthPx;

    // ---------- 播放 ----------
    private ExoPlayerEngine engine;
    private Player player;
    private final DramaResolver resolver = new DramaResolver();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "hg-feed");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean destroyed;

    /**
     * 池投递唯一收口（P0 崩溃定根，账 fix168-remedies-20261031 P0）：
     * 1.4.68 真机崩 RejectedExecutionException——onDestroy 已
     * shutdownNow 后，主队列里残留的 settleTo 投递才触发，经
     * ensureCommentCount 往死池 execute、无守卫无 catch 当场崩。
     * 销毁后/池已关的迟到投递一律静默丢弃；check 与 execute 之间
     * 的竞态由 catch 兜底。
     */
    private void safeExecute(Runnable task) {
        if (destroyed || worker.isShutdown()) return;
        try {
            worker.execute(task);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
        }
    }

    // ---------- 会话 ----------
    private int mode = MODE_FEED;
    private final List<DramaItem> items = new ArrayList<>();
    // 视频页（MODE_VIDEO）会话：合集详情与作者面（单条走 intent 附加）
    private String videoAlbumId;
    private DramaAlbumDetail videoAlbum;
    private String videoAuthorUid;
    private String videoAuthorName;
    private String videoAuthorAvatar;
    /** 清屏钮基准位（按模式在建 UI 时定，防烧屏轮位在此基础上偏移）。 */
    private int cleanBaseBottom;
    private int cleanBaseRight = 12;
    private final Map<String, DramaDetail> detailCache = new HashMap<>();
    private HgContinuation continuation;
    private boolean hasMore;
    /** 进程重建恢复目标条位（B-23）：存档条位落在已拉页之外时，
     * 沿续拉链逐页补到够得着再落位；-1＝不在恢复中。 */
    private int restoreTarget = -1;
    private int restorePulls;
    private static final int RESTORE_MAX_PULLS = 20;
    private boolean loadingPage;
    private int currentIndex = -1;
    private String currentVid;
    private String currentSeriesId;
    private int currentEpisodeIndex = 1;
    private String seriesIdArg;
    private List<DramaDetail.Episode> episodes = new ArrayList<>();
    /** 取链窗口去重（resolveWithWindow）：已发过窗口批量的剧与起始下标。 */
    private String windowSeriesId;
    private int windowStart = -1;
    private boolean cleanMode;
    private boolean prefetchedNext;
    /** FEED 当前条实际在播的集序（被推集 vidIndex，D3 记账口径）。 */
    private int feedEpIndex = 1;
    /** 进页起播位待办（EXTRA_START_MS）：集号＋毫秒，首集 playEpisode
     * 消费一次即清；-1＝无（手动路恒无）。 */
    private int entrySeekEp = -1;
    private long entrySeekMs = -1;
    /** 选集弹窗跳集待办集号：滑页 settle 落位后消费（从头播）。 */
    private int sheetJumpEp;
    /** 播完连播滑页待办集号：settle 落位后消费（从头播）。 */
    private int autoNextEp;
    /** 当前条被拖过进度（官方播完判序的循环路；流内暂无拖条
     * 控件，此旗随每次起播复位、判定件先落位）。 */
    private boolean scrubbedCurrent;
    /** D7 历史进度线已对位的条目序号／是否已按时长定版。 */
    private int feedLineIndex = -1;
    private boolean feedLineDone;
    private long lastSaveMs;
    private String danmakuVid;
    private final List<androidx.media3.ui.danmaku.Danmaku> danmakuItems = new ArrayList<>();
    private boolean firstFrameForVid;
    /** 切集转圈延迟显的待发任务（切集顺滑：快切不许转圈闪）。 */
    private Runnable spinDelayRunnable;
    /** 切集末帧冻结：上一集帧图＋展示层＋代次（切集顺滑）。 */
    private ImageView freezeView;
    private Bitmap freezeBitmap;
    private int freezeSeq;
    private int freezeForIndex = -1;
    private boolean freezeArmed;
    /**
     * settle 等帧续程（件二时序定序）：settleTo 发起抓帧后不立即
     * 搬面，等这发抓帧落定（回调/超时先到为准）才走 proceedSettle。
     * awaitSeq＝在等的那发代次（0＝没在等）；settledSeq＝最近落定
     * 的代次（TextureView 同步回调在等帧登记前就到，靠它识别）。
     */
    private int settleAwaitSeq;
    private int settleAwaitIndex = -1;
    private int freezeSettledSeq;
    private Runnable settleAwaitTimeout;
    /** 等帧上限：PixelCopy 正常一两帧内回，超此走无冻结常路。 */
    private static final long FREEZE_WAIT_MS = 250;
    private boolean feedStartPending = true;
    /** 首页流起播失败记账（条目键＝seriesId/vid）：失败条只呈失败态，封面不许回潮当内容（账 hongguo-feed-noposter-20261030）。 */
    private final java.util.Set<String> feedFailedKeys = new java.util.HashSet<>();
    /** 首页流起播看门狗：超时未出首帧按失败呈现（不自动跳条，用户一划就走）。 */
    private Runnable feedWatchdog;
    /** 高光片段到点守卫已触发的 vid（防 tick 重复走完播路）；playVid 换条即失效（按 vid 比对）。 */
    private String highlightEndFiredVid;
    private static final long FEED_WATCHDOG_MS = 20_000;
    // 横屏交接（阶段8）：泊出旗/列表静止旗/最近一集画幅/弹窗守卫/朝向监视
    private boolean engineParked;
    /** 流→播放页承接（切换手感头号缝）：入站时池里泊着的包（同剧同 vid 才领）。 */
    private DramaPlayerPool.Bag inboundBag;
    /** 本实例领过承接引擎，退出时要在 finish 前泊回给流页。 */
    private boolean tookHandoff;
    /** 离开本页前是否在播（回来续播守卫，见 onPause/onResume）。 */
    private boolean resumeOnReturn;
    /** 本页是否在前台（onResume/onPause 落）：解链迟到回包落点判定
     * 用——被盖住时回包只备媒体不起播，不许在后台出声。 */
    private boolean activityResumed;
    private boolean pagerIdle = true;
    private int lastVideoW;
    private int lastVideoH;
    private long panelGuardUntil;
    private OrientationEventListener orientationListener;
    /** 件六：进横屏窄带内停留计时（首帧入带记时刻，≥400ms 才触发）。 */
    private long landBandSince;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HgWindowStyle.apply(this, false, false);
        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_FEED);
        seriesIdArg = getIntent().getStringExtra(EXTRA_SERIES_ID);
        videoAlbumId = getIntent().getStringExtra(EXTRA_ALBUM_ID);
        videoAuthorUid = getIntent().getStringExtra(EXTRA_AUTHOR_UID);
        videoAuthorName = getIntent().getStringExtra(EXTRA_AUTHOR_NAME);
        videoAuthorAvatar = getIntent().getStringExtra(EXTRA_AUTHOR_AVATAR);
        buildUi();
        setContentView(root);
        watchNavInsets();
        if (mode == MODE_SERIES && seriesIdArg != null) {
            // 承接入站预检：流页泊来的包同剧且带 vid 才认（领取引擎
            // 延到 settleTo 落点对上那一集时，见 adoptHandoff）。
            DramaPlayerPool.Bag bag = DramaPlayerPool.peekBag();
            if (bag != null && seriesIdArg.equals(bag.seriesId) && bag.vid != null) {
                inboundBag = bag;
            }
        }
        if (mode == MODE_FEED) {
            seriesBar.setVisibility(View.GONE);
            loadChannel(true);
        } else {
            topBar.setVisibility(View.GONE);
            String t = getIntent().getStringExtra(EXTRA_TITLE);
            seriesTitleView.setText(t == null ? "" : t);
            if (mode == MODE_SERIES) loadSeries();
            else if (mode == MODE_VIDEO) loadVideo();
            else loadUgc();
        }
        main.post(ticker);
        // 传感器横转直进横屏（他 2026-10-31 拍板加回的接法）＋件六
        // 防误触（自家定值，官方度数带未抠到）：进带收窄到离全横置
        // ≤15° 的窄带（DramaLandLogic.isEnterLandBand）、带内停留
        // ≥400ms 才走 enterLand(false)；触发后互锁（反向 1000ms＋
        // 过中间带才重武装）在 DramaLandLogic 共享。监听只在系统
        // 自动旋转开着时武装（onResume 现读开关），关＝整条不武装、
        // 只剩全屏钮手动路。横版门＋总闸在 enterLand 内与钮路同口径。
        orientationListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                long now = System.currentTimeMillis();
                if (DramaLandLogic.isEnterLandBand(orientation)) {
                    if (!DramaLandLogic.sensorDirectionArmed(1, now)) {
                        landBandSince = 0;
                        return;
                    }
                    if (landBandSince == 0) {
                        landBandSince = now;
                        return;
                    }
                    if (now - landBandSince >= 400) {
                        landBandSince = 0;
                        DramaLandLogic.sensorNoteTrigger(1, now);
                        enterLand(false);
                    }
                } else {
                    landBandSince = 0;
                    if (DramaLandLogic.orientationBand(orientation) == -1) {
                        DramaLandLogic.sensorNoteMiddleBand();
                    }
                }
            }
        };
    }

    // ---------- 横屏交接（阶段8） ----------

    /**
     * 进横屏：横版门（条目 vertical 旗优先、未知时看画幅）＋总闸
     * （DramaLandLogic.canEnterLand）双过，才把播放器泊进
     * DramaPlayerPool 并拉起独立横屏页同流续播。无剧短片不进横屏。
     */
    /**
     * 横屏资格三分支（#11：全屏钮显隐与进横屏的门共用同一判定，
     * 不许再各写一套）：条目 vertical 旗优先——FALSE 横版、TRUE
     * 竖版；旗缺失时回落解码实测画幅（lastVideoW/H）。短片恒无资格。
     */
    private boolean isLandItem(DramaItem item) {
        if (item == null || item.kind == DramaItemKind.UGC_CLIP) return false;
        if (Boolean.FALSE.equals(item.getVertical())) return true;
        if (Boolean.TRUE.equals(item.getVertical())) return false;
        return DramaLandLogic.isLandEligible(lastVideoW, lastVideoH);
    }

    /** 尺寸落定后补一次全屏钮显隐复核（旗缺失的条目靠实测画幅定，
     * 绑定时尺寸可能还没到，到了必须补显——#11 钉死点）。 */
    private void refreshFullPillVisibility() {
        FeedAdapter.Holder h = currentHolder();
        if (h == null || h.fullPill == null) return;
        DramaItem item = currentIndex >= 0 && currentIndex < items.size()
                ? items.get(currentIndex) : null;
        h.fullPill.setVisibility(isLandItem(item) && mode != MODE_VIDEO
                ? View.VISIBLE : View.GONE);
    }

    private void enterLand(boolean fromButton) {
        if (engineParked || player == null || currentVid == null) return;
        if (currentIndex < 0 || currentIndex >= items.size()) return;
        DramaItem item = items.get(currentIndex);
        if (!isLandItem(item)) return;
        boolean popup = (drawerPanel != null && drawerPanel.getVisibility() == View.VISIBLE)
                || System.currentTimeMillis() < panelGuardUntil;
        if (!DramaLandLogic.canEnterLand(pagerIdle, firstFrameForVid, false,
                popup, fromButton)) return;
        DramaPlayerPool.Bag bag = new DramaPlayerPool.Bag();
        bag.seriesId = currentSeriesId != null ? currentSeriesId : item.getSeriesId();
        bag.vid = currentVid;
        bag.episodeIndex = currentEpisodeIndex;
        bag.positionMs = player.getCurrentPosition();
        bag.playWhenReady = player.getPlayWhenReady();
        bag.danmakuOn = HgStore.isDanmakuOn();
        bag.speed = player.getPlaybackParameters().speed;
        bag.seriesTitle = item.getTitle();
        bag.contentType = item.contentType;
        saveProgressNow();
        player.removeListener(playerListener);
        playerView.setPlayer(null);
        DramaPlayerPool.park(bag, engine);
        engineParked = true;
        HgTransition.fade(this, new Intent(this, HongguoLandActivity.class));
    }

    /** 领回重开本条的起播点：与 playFeedItem 同口径——高光片段从
     * 片段头起、其余从头（D3：流内从头播被推集）。 */
    private long feedRestartMs(DramaItem item) {
        return item != null && item.isHighlightFragment()
                && item.highlightStartMs > 0 ? item.highlightStartMs : 0;
    }

    /** 横屏页退回后领回播放器：按 DramaLandLogic.reclaimDecision 对位。 */
    private void reclaimFromLand() {
        engineParked = false;
        DramaPlayerPool.Bag bag = DramaPlayerPool.peekBag();
        DramaItem item = currentIndex >= 0 && currentIndex < items.size()
                ? items.get(currentIndex) : null;
        String ctxSeries = mode == MODE_SERIES ? seriesIdArg
                : (item == null ? null : item.getSeriesId());
        int decision = bag == null ? DramaLandLogic.RECLAIM_MISMATCH
                : DramaLandLogic.reclaimDecision(mode == MODE_SERIES, bag.seriesId,
                        ctxSeries, bag.vid, currentVid, bag.episodeIndex,
                        currentEpisodeIndex);
        if (decision == DramaLandLogic.RECLAIM_MISMATCH) {
            DramaPlayerPool.releaseParked();
            engine = null;
            player = null;
            if (currentVid != null && item != null) {
                playVid(currentVid, item.contentType, feedRestartMs(item));
            }
            return;
        }
        engine = DramaPlayerPool.takeEngine();
        player = engine == null ? null : engine.getPlayer();
        if (player == null) {
            if (currentVid != null && item != null) {
                playVid(currentVid, item.contentType, feedRestartMs(item));
            }
            return;
        }
        player.addListener(playerListener);
        playerView.setPlayer(player);
        applyDanmakuVisibility();
        adoptCurrentStream(bag.vid);
        if (decision == DramaLandLogic.RECLAIM_SERIES_JUMP) {
            // 横屏里换集看的位置只活在交接单里（bag.positionMs 此
            // 前只写不读）：不钉住，跳集落位走的是选集口径从头播
            // ——横屏看到一半的集回竖屏整集重播、断点存档随后还被
            // 新位置覆盖。钉进进页钉位：settle 落位时 playEpisode
            // 优先消费（盖过从头与断点存档两口径）。
            if (bag.positionMs > 0) {
                entrySeekEp = bag.episodeIndex;
                entrySeekMs = bag.positionMs;
            }
            playEpisodeFromSheet(bag.episodeIndex);
        } else if (decision == DramaLandLogic.RECLAIM_FEED_DIFF) {
            if (item != null) playVid(currentVid, item.contentType, feedRestartMs(item));
        } else {
            player.setPlayWhenReady(bag.playWhenReady);
        }
    }

    /**
     * 领回时把引擎正播那条流的账接到本页 resolver：流若是泊出页
     * （横屏/播放页）的 resolver 注册的，本页账上没有这笔——不接
     * 过来，它就只剩泊出页销毁时 closeAllExcept 留的一条命、永远
     * 无人关也无人重解。URL 从播放器当前媒体项取（本地链里带
     * token），自家解的流 resolver 已有记录、adoptStream 自跳过。
     */
    private void adoptCurrentStream(String vid) {
        if (vid == null || player == null) return;
        androidx.media3.common.MediaItem mi = player.getCurrentMediaItem();
        if (mi == null || mi.localConfiguration == null
                || mi.localConfiguration.uri == null) return;
        resolver.adoptStream(vid, mi.localConfiguration.uri.toString());
    }

    /**
     * 回前台／从横屏领回后的画面层收口（#1、#18 同一口径）：切后台
     * 期间 SurfaceView 的画面层被销毁、或播放视图还挂在旧 holder 上，
     * 声音能续上、画面却回不来（#1 黑屏、#18 落竖屏条只剩封面）。
     * resume 路径此前只续播不重挂，这里强制把播放视图重挂进当前条
     * 的 holder，首帧旗复位——重挂后走黑场＋转圈，首帧渲染时收口
     *（与 #8 的起播占位形态同口径，不亮封面）。
     */
    private void remountSurfaceAfterReturn() {
        if (player == null || playerView == null) return;
        if (currentIndex < 0 || currentIndex >= items.size()) return;
        firstFrameForVid = false;
        mountPlayerView(currentIndex);
        // 件七（逐帧账 D1/D9 定段）：1.4.68 的重挂修法在真机未生效
        // ——录屏四次回前台全是「转场中借窗口淡入透出旧帧、落稳硬
        // 切黑、永不恢复」，且黑屏态进度线为白色（tick 口径＝
        // isPlaying 为真）＋原症有声：播放器没停，断的是渲染器与
        // 画面层的交付。重挂只把 PlayerView 物理摘下重挂、循环了
        // SurfaceView 的面，但全程没有任何一处让播放器把「当前活
        // 面」重新交给渲染器——普通回前台分支正是缺了领回分支
        // （reclaimFromLand）一直在做的 setPlayer 重设。补齐同一
        // 动作：setPlayer(null) 先让播放器 clearVideoSurfaceView
        // 摘掉旧面登记，再 setPlayer(player) 以 setVideoSurfaceView
        // 把当前活面重新登记给渲染器；换面后渲染器会重报首帧
        // （MediaCodecVideoRenderer 对已报过首帧的面有重报路），
        // onRenderedFirstFrame 的收口（摘转圈/冻结、首帧旗）照旧
        // 接得住。两分支（领回后/普通回前台）共用此收口。
        playerView.setPlayer(null);
        playerView.setPlayer(player);
    }

    /**
     * 件六主修：系统自动旋转开关现读（每次 onResume 现判、不缓存，
     * 他中途改开关回页即生效）。官方整套旋转策略建立在系统开关之
     * 上、咱家此前全树不读——他关着开关也被歪一下送进横屏的头号
     * 症即此。读失败（异常/未设置）按关处理（保守侧、与他设备现
     * 状一致）。关＝传感器链整条不武装，手动全屏钮路不受影响。
     */
    private boolean systemAutoRotateOn() {
        try {
            return Settings.System.getInt(getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION) == 1;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        if (orientationListener != null) {
            if (systemAutoRotateOn()) orientationListener.enable();
            else orientationListener.disable();
        }
        if (engineParked) {
            reclaimFromLand();
            remountSurfaceAfterReturn();
            return;
        }
        // 切后台/被别的页盖住再回来：不重拉、位置不动，只把离开前
        // 正在播的续上（离开前就是暂停的保持暂停）；进度在原播放器
        // 里留着，续播不重头。
        remountSurfaceAfterReturn();
        if (resumeOnReturn && player != null) player.setPlayWhenReady(true);
        resumeOnReturn = false;
        // D5：详情页等外页改过收藏，回到常驻的流/播放页要立即同源
        // ——重绑当前条右列（星色/已收藏签/计数），不等下一次 settle
        if (currentIndex >= 0 && currentIndex < items.size()) {
            bindRailState(currentIndex);
            adapter.notifyItemChanged(currentIndex);
        }
    }

    /**
     * 首页模式（MODE_FEED）按返回不销毁本页：把壳子首页提到前台，
     * 本页留在任务栈里常驻（口径见 start()），再点短剧签原样回来。
     * 其它模式（播放页/视频页/单条）是内容页，返回照常关页回来源。
     */
    @Override
    public void finish() {
        // 承接来的引擎先泊回再退场：流页 onResume 领回时包已在池
        // 里（泊回晚于 resume 就会领空、被迫另起引擎——横屏
        // exitLand 先泊后 finish 是同一条教训）。
        if (mode == MODE_SERIES && tookHandoff && !engineParked
                && engine != null && player != null) {
            parkBackToFeed();
        }
        super.finish();
        HgTransition.applyPushExit(this);
    }

    @Override
    public void onBackPressed() {
        if (mode == MODE_FEED) {
            Intent home = new Intent();
            home.setClassName(getPackageName(),
                    "com.fongmi.android.tv.ui.activity.HomeActivity");
            home.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(home);
            return;
        }
        super.onBackPressed();
    }

    // ---------- UI 构建 ----------

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        pager = new RecyclerView(this);
        pager.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        adapter = new FeedAdapter();
        pager.setAdapter(adapter);
        PagerSnapHelper snap = new PagerSnapHelper();
        snap.attachToRecyclerView(pager);
        pager.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                pagerIdle = state == RecyclerView.SCROLL_STATE_IDLE;
                if (state == RecyclerView.SCROLL_STATE_IDLE) {
                    View v = snap.findSnapView(pager.getLayoutManager());
                    if (v != null) settleTo(pager.getChildAdapterPosition(v));
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 0 && !loadingPage && hasMore && currentIndex >= items.size() - 3) {
                    loadChannel(false);
                }
            }
        });
        root.addView(pager, match());

        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setShutterBackgroundColor(Color.BLACK);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setDanmakuEnabled(HgStore.isDanmakuOn());
        playerView.getDanmakuController().setConfig(danmakuConfig());
        // 全屏钮跟着视频走（他 2026-10-31 连点四遍）：画面视图的
        // 实际边框一变（比例/尺寸落定），钮就按画面下缘重摆——
        // 读的是真渲染出来的画面框，不拿数据里的宽高算死位置。
        View surfaceView = playerView.getVideoSurfaceView();
        if (surfaceView != null) {
            surfaceView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> {
                if (r - l != orr - ol || b - t != ob - ot) anchorFullPill();
            });
        }

        buildTopBar();
        buildSeriesBar();
        buildBottomAssemblies();
        buildOverlays();
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    private GradientDrawable scrim(boolean top) {
        return new GradientDrawable(
                top ? GradientDrawable.Orientation.TOP_BOTTOM : GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0x99000000, 0x00000000});
    }

    private void buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(scrim(true));
        int sb = statusBarHeight();
        bar.setPadding(dp(12), sb, dp(12), 0);

        // 定稿：首页左上只有三条杠、无任何字标（频道签已摘，首页单流）
        ImageView menu = glassIconBtn(R.drawable.ic_drama_menu, v -> openDrawer());
        bar.addView(menu, new LinearLayout.LayoutParams(dp(38), dp(38)));
        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, -2, 1f));

        // 他亲定的入口形态：右上角圆形搜索钮（玻璃圆＋放大镜），顶栏只留这一个搜索入口
        ImageView search = glassIconBtn(R.drawable.ic_drama_search,
                v -> HgTransition.push(this, new Intent(this, HongguoSearchActivity.class)));
        bar.addView(search, new LinearLayout.LayoutParams(dp(38), dp(38)));

        topBar = bar;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, dp(52) + sb);
        lp.gravity = Gravity.TOP;
        root.addView(bar, lp);
    }

    private void buildSeriesBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(scrim(true));
        bar.setPadding(dp(12), statusBarHeight(), dp(12), 0);
        // 返回标记按官方（他 2026-10-31 点名）：裸折角 <，不套圆圈、
        // 不要带杆的箭头（ic_drama_back 那支他说难看）。
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_drama_chev);
        back.setPadding(dp(7), dp(7), dp(7), dp(7));
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(38), dp(38)));
        seriesTitleView = new TextView(this);
        seriesTitleView.setTextColor(Color.WHITE);
        seriesTitleView.setTextSize(15.5f);
        seriesTitleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        seriesTitleView.setMaxLines(1);
        seriesTitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        seriesTitleView.setShadowLayer(dp(2), 0, dp(1), 0x99000000);
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(0, -2, 1f);
        stlp.leftMargin = dp(10);
        bar.addView(seriesTitleView, stlp);
        // 定稿：播放页右上只「···」玻璃圆更多钮（倍速只走更多面板 seg，
        // 顶栏倍速字钮已摘——不在草稿里）
        ImageView more = glassIconBtn(R.drawable.ic_drama_more, v -> showMorePanel());
        bar.addView(more, new LinearLayout.LayoutParams(dp(38), dp(38)));
        seriesBar = bar;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, dp(52) + statusBarHeight());
        lp.gravity = Gravity.TOP;
        root.addView(bar, lp);
    }

    // ---------- 底部整块（按模式三选一，进度线都夹在条与内容之间） ----------

    /** 当前模式底部整块总高（含导航区让位），供信息摞/清屏钮算落位。 */
    private int bottomBlockH() {
        int nav = navBarHeight();
        switch (mode) {
            case MODE_FEED:
                return dp(42) + dp(3) + dp(56) + nav;
            case MODE_SERIES:
                return dp(8) + dp(50) + nav;
            case MODE_VIDEO:
                return dp(8) + (videoAlbumId != null ? dp(50) : 0) + nav;
            default:
                return nav;
        }
    }

    /** 细进度线（定稿 .pg-track：轨 rgba(白,.22)＋已走 rgba(白,.92)＋白点 9dp，禁红）。 */
    private View buildProgressLine() {
        FrameLayout track = new FrameLayout(this);
        View bg = new View(this);
        bg.setBackgroundColor(0x38FFFFFF);
        track.addView(bg, match());
        progressFill = new View(this);
        progressFill.setBackgroundColor(0xEBFFFFFF);
        track.addView(progressFill, new FrameLayout.LayoutParams(0, -1));
        progressDot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(Color.WHITE);
        progressDot.setBackground(dotBg);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(9), dp(9));
        dlp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        track.addView(progressDot, dlp);
        progressWrap = track;
        return track;
    }

    /**
     * 进度行：就是一根带白点的线、左右与下方的条对齐（官方图 113744
     * 形态——没有两头的时间字）。原写法两头各摆一个 0:00，是他
     * 2026-10-31 点名的那根「进度条」问题：播不起来时两头时间看不见、
     * 线又被挤在中间。现在线拉满整行；timeCur/Dur 字段留空，tick 里
     * 有判空守卫，不受影响。
     */
    private View buildProgressRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        // 件九底行量值对齐：官方帧 113561 进度线几乎贴着选集条
        // （线底 y≈94.5%、条顶 y≈94.6%，间隙≈1dp）；旧落码行高
        // 25dp＋上下垫 10/12 把线悬在条上方约 10dp（咱家帧 113848
        // 实测线底 93.5% 对条顶 94.5%）。行高改 8dp、无上下垫，
        // 线（3dp）居中后线底距条顶约 2.5dp。行高改动须与
        // bottomBlockH 的 dp(25)→dp(8) 同步（信息摞/清屏钮基线）。
        row.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0, dp(3), 1f);
        row.addView(buildProgressLine(), llp);
        progressRow = row;
        return row;
    }

    private void buildBottomAssemblies() {
        int nav = navBarHeight();
        if (mode == MODE_FEED) {
            LinearLayout stack = new LinearLayout(this);
            stack.setOrientation(LinearLayout.VERTICAL);
            stack.setBackgroundColor(Color.TRANSPARENT);
            // 剧条（他 2026-10-31 发官方图 113756 放大核定）：满宽通
            // 栏、不留左右边、与底栏不隔开；只有下面两个角是圆角（圆
            // 12），上两角直角；进度线在条内下部、两头留边 14；播放
            // 图标白底实心圆角方块＋深色三角（原样）。
            LinearLayout watchWrap = new LinearLayout(this);
            watchWrap.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable dockBg = new GradientDrawable();
            dockBg.setColor(0xFF3A3A40);
            float dockR = dp(12);
            dockBg.setCornerRadii(new float[]{0, 0, 0, 0, dockR, dockR, dockR, dockR});
            watchWrap.setBackground(dockBg);
            LinearLayout watch = new LinearLayout(this);
            watch.setOrientation(LinearLayout.HORIZONTAL);
            watch.setGravity(Gravity.CENTER_VERTICAL);
            watch.setPadding(dp(18), 0, dp(12), 0);
            ImageView playBox = new ImageView(this);
            playBox.setImageResource(R.drawable.ic_drama_play);
            playBox.setColorFilter(0xFF19191C);
            playBox.setBackground(roundBg(Color.WHITE, dp(7)));
            playBox.setPadding(dp(6), dp(6), dp(6), dp(6));
            watch.addView(playBox, new LinearLayout.LayoutParams(dp(24), dp(24)));
            watchText = new TextView(this);
            watchText.setTextColor(Color.WHITE);
            watchText.setTextSize(15);
            watchText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            watchText.setMaxLines(1);
            watchText.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(0, -2, 1f);
            wlp.leftMargin = dp(10);
            watch.addView(watchText, wlp);
            ImageView arrow = new ImageView(this);
            arrow.setImageResource(R.drawable.ic_drama_back);
            arrow.setColorFilter(Color.WHITE);
            arrow.setRotation(180);
            watch.addView(arrow, new LinearLayout.LayoutParams(dp(14), dp(14)));
            watch.setOnClickListener(v -> onWatchBarClick());
            watchWrap.addView(watch, new LinearLayout.LayoutParams(-1, dp(42)));
            watchWrap.addView(buildProgressLine(), new LinearLayout.LayoutParams(-1, dp(3)));
            watchBar = watchWrap;
            stack.addView(watchWrap);
            // 底栏纯文字三签（黑 #202023；选中白＋粗、未选 rgba(白,.55)）；
            // 定稿 .tabbar 总高 56＋手势区让位，签在高内垂直居中。
            // 三签均分全宽（件四，他 17:2x 点名分布不均）：旧落码左右
            // 各垫 64dp 把签组收在中间、与定稿 v6 的均分三等分对不上，
            // 左右垫摘除、三格 weight 等分锚满整宽（与我的页/剧场同口径）。
            LinearLayout bar = new LinearLayout(this);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setBackgroundColor(0xFF202023);
            bar.setPadding(0, 0, 0, nav);
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, -2, 1f);
            bar.addView(bottomCell("首页", true, null), cell);
            bar.addView(bottomCell("剧场", false,
                    v -> HongguoTheaterActivity.start(this)), cell);
            bar.addView(bottomCell("我的", false,
                    v -> HongguoMineActivity.startTab(this)), cell);
            stack.addView(bar, new LinearLayout.LayoutParams(-1, dp(56) + nav));
            bottomStack = stack;
            cleanBaseBottom = bottomBlockH() + dp(12);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
            lp.gravity = Gravity.BOTTOM;
            root.addView(stack, lp);
        } else if (mode == MODE_SERIES) {
            LinearLayout stack = new LinearLayout(this);
            stack.setOrientation(LinearLayout.VERTICAL);
            stack.addView(buildProgressRow(), new LinearLayout.LayoutParams(-1, dp(8)));
            // 选集条（他 2026-10-31 发官方图定形）：左右留边 14 的圆角悬浮
            // 条（圆 12、深灰 .94），不是满宽直角贴底；chevron 朝上在条内
            // 右端；清屏钮是条外右侧一个独立圆角方钮（浮层落位见
            // buildOverlays），与条之间留 8dp，不许再压在条上。
            // 行外右 padding＝14（边距）＋46（清屏钮）＋8（间隔）＝68。
            LinearLayout epWrap = new LinearLayout(this);
            epWrap.setOrientation(LinearLayout.HORIZONTAL);
            epWrap.setPadding(dp(14), 0, dp(68), nav);
            GradientDrawable epBg = new GradientDrawable();
            epBg.setColor(0xF0262629);
            epBg.setCornerRadius(dp(12));
            LinearLayout epBar = new LinearLayout(this);
            epBar.setOrientation(LinearLayout.HORIZONTAL);
            epBar.setGravity(Gravity.CENTER_VERTICAL);
            epBar.setBackground(epBg);
            epBar.setPadding(dp(18), 0, dp(12), 0);
            epBarText = new TextView(this);
            epBarText.setTextColor(Color.WHITE);
            epBarText.setTextSize(15.5f);
            epBarText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            epBarText.setMaxLines(1);
            epBarText.setEllipsize(android.text.TextUtils.TruncateAt.END);
            epBar.addView(epBarText, new LinearLayout.LayoutParams(0, -2, 1f));
            // chevron 朝上（ic_drama_back 左箭 rotation 90＝朝上；原 -90 朝下是反的）
            ImageView epChev = new ImageView(this);
            epChev.setImageResource(R.drawable.ic_drama_chev);
            epChev.setColorFilter(Color.WHITE);
            epChev.setRotation(90);
            LinearLayout.LayoutParams ecp = new LinearLayout.LayoutParams(dp(19), dp(19));
            ecp.leftMargin = dp(6);
            epBar.addView(epChev, ecp);
            epBar.setOnClickListener(v -> showEpisodeSheet());
            epWrap.addView(epBar, new LinearLayout.LayoutParams(-1, dp(50)));
            stack.addView(epWrap, new LinearLayout.LayoutParams(-1, dp(50) + nav));
            bottomStack = stack;
            cleanBaseBottom = nav;
            cleanBaseRight = 14;
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
            lp.gravity = Gravity.BOTTOM;
            root.addView(stack, lp);
        } else if (mode == MODE_VIDEO) {
            LinearLayout stack = new LinearLayout(this);
            stack.setOrientation(LinearLayout.VERTICAL);
            stack.addView(buildProgressRow(), new LinearLayout.LayoutParams(-1, dp(8)));
            if (videoAlbumId != null) {
                // 合集条（他 2026-10-31 点名与选集条同改）：左右留边 14 的
                // 圆角悬浮条、chevron 朝上（原 -90 朝下是反的）、清屏钮条外
                // 独立成钮（落位在 buildOverlays，本模式自此也有清屏钮）。
                LinearLayout abWrap = new LinearLayout(this);
                abWrap.setOrientation(LinearLayout.HORIZONTAL);
                abWrap.setPadding(dp(14), 0, dp(68), nav);
                GradientDrawable abBg = new GradientDrawable();
                abBg.setColor(0xF0262629);
                abBg.setCornerRadius(dp(12));
                LinearLayout bar = new LinearLayout(this);
                bar.setOrientation(LinearLayout.HORIZONTAL);
                bar.setGravity(Gravity.CENTER_VERTICAL);
                bar.setBackground(abBg);
                bar.setPadding(dp(18), 0, dp(12), 0);
                TextView badge = new TextView(this);
                badge.setText("集");
                badge.setTextColor(Color.WHITE);
                badge.setTextSize(11);
                badge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                badge.setGravity(Gravity.CENTER);
                GradientDrawable badgeBg = new GradientDrawable();
                badgeBg.setColor(Color.TRANSPARENT);
                badgeBg.setStroke(dp(1.4f), 0xD9FFFFFF);
                badgeBg.setCornerRadius(dp(5));
                badge.setBackground(badgeBg);
                bar.addView(badge, new LinearLayout.LayoutParams(dp(21), dp(21)));
                albumBarText = new TextView(this);
                albumBarText.setTextColor(Color.WHITE);
                albumBarText.setTextSize(15.5f);
                albumBarText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                albumBarText.setMaxLines(1);
                albumBarText.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams alp2 = new LinearLayout.LayoutParams(0, -2, 1f);
                alp2.leftMargin = dp(10);
                bar.addView(albumBarText, alp2);
                ImageView abChev = new ImageView(this);
                abChev.setImageResource(R.drawable.ic_drama_chev);
                abChev.setColorFilter(Color.WHITE);
                abChev.setRotation(90);
                LinearLayout.LayoutParams acp = new LinearLayout.LayoutParams(dp(14), dp(14));
                acp.leftMargin = dp(6);
                bar.addView(abChev, acp);
                bar.setOnClickListener(v -> openAlbumSheet());
                abWrap.addView(bar, new LinearLayout.LayoutParams(-1, dp(50)));
                stack.addView(abWrap, new LinearLayout.LayoutParams(-1, dp(50) + nav));
                albumBar = bar;
                cleanBaseBottom = nav;
                cleanBaseRight = 14;
            }
            bottomStack = stack;
            if (videoAlbumId == null) cleanBaseBottom = bottomBlockH() + dp(12);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
            lp.gravity = Gravity.BOTTOM;
            root.addView(stack, lp);
        } else {
            // MODE_UGC 旧入口：进度线贴底（线高随定稿改 3dp）
            View line = buildProgressLine();
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, dp(3));
            lp.gravity = Gravity.BOTTOM;
            lp.bottomMargin = nav;
            root.addView(line, lp);
            // 清屏钮基线（B-08）：旧值 148 落在右列底端（railBase
            // 140＋心件高）正上方、与点赞心同位相叠——钮一直没敢建。
            // 改贴底线之上 12：右列从 140 起向上排，与钮（12..62）
            // 不相交，钮按下去全隐后只剩它自己也不压任何件。
            cleanBaseBottom = nav + dp(12);
        }
    }

    /** 底栏纯文字签（他 21:02 定案：无图标；选中靠字色＋字重分）。 */
    private View bottomCell(String label, boolean selected, View.OnClickListener click) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(16.5f);
        t.setGravity(Gravity.CENTER);
        t.setLetterSpacing(0.04f);
        t.setTextColor(selected ? Color.WHITE : 0x8CFFFFFF);
        if (selected) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        if (click != null) t.setOnClickListener(click);
        return t;
    }

    /** 首页观看完整条点击：正剧进播放页从断点开播、合集进视频页。 */
    private void onWatchBarClick() {
        if (currentIndex < 0 || currentIndex >= items.size()) return;
        DramaItem item = items.get(currentIndex);
        if (item.kind == DramaItemKind.ALBUM) {
            startVideoAlbum(this, item);
        } else if (item.kind == DramaItemKind.SERIES && item.getSeriesId() != null) {
            openSeries(item, 0);
        }
    }

    /**
     * 进播放页（承接版）：点的就是流里正在播的这条时，先把引擎
     * 泊进池里再开页——播放页领同一台引擎同 vid 续播，不重起、
     * 不重取链、不闪封面（与横屏 park/take 同口径；此前此路直
     * 接开新实例另起引擎重取链，是切换手感头号硬切）。不是当前
     * 条或引擎不在手时走原路。
     */
    private void openSeries(DramaItem item, int episodeIndex) {
        openSeries(item, episodeIndex, -1);
    }

    private void openSeries(DramaItem item, int episodeIndex, long startMs) {
        if (item == null || item.getSeriesId() == null) return;
        if (mode == MODE_FEED && !engineParked && player != null && currentVid != null
                && currentIndex >= 0 && currentIndex < items.size()
                && items.get(currentIndex) == item) {
            parkForSeries(item);
        }
        startSeries(this, item.getSeriesId(), item.getTitle(), episodeIndex, startMs);
    }

    /** 泊出给播放页领（enterLand 泊车同形）。 */
    private void parkForSeries(DramaItem item) {
        DramaPlayerPool.Bag bag = new DramaPlayerPool.Bag();
        bag.seriesId = item.getSeriesId();
        bag.vid = currentVid;
        bag.episodeIndex = currentEpisodeIndex;
        bag.positionMs = player.getCurrentPosition();
        bag.playWhenReady = player.getPlayWhenReady();
        bag.danmakuOn = HgStore.isDanmakuOn();
        bag.speed = player.getPlaybackParameters().speed;
        bag.seriesTitle = item.getTitle();
        bag.contentType = item.contentType;
        saveProgressNow();
        player.removeListener(playerListener);
        playerView.setPlayer(null);
        DramaPlayerPool.park(bag, engine);
        engineParked = true;
    }

    /** 当前条的观看完整条文案（短片条无「完整」概念，整条隐藏）。 */
    private void updateWatchBar() {
        if (mode != MODE_FEED || watchBar == null) return;
        if (currentIndex < 0 || currentIndex >= items.size()) {
            watchBar.setVisibility(View.GONE);
            return;
        }
        DramaItem item = items.get(currentIndex);
        if (item.kind == DramaItemKind.UGC_CLIP) {
            watchBar.setVisibility(View.GONE);
            return;
        }
        watchBar.setVisibility(View.VISIBLE);
        // 带作者的条目剧名已下沉本条：文案走服务端底条分段
        // （bottomBarText 原序以「·」相接，内含剧名段，存档回包
        // 实证）；没带分段或无作者时仍走原生成文案
        boolean hasAuthor = item.getAuthorUid() != null && !item.getAuthorUid().isEmpty()
                && item.getAuthorName() != null && !item.getAuthorName().isEmpty();
        if (item.getAuthorUid() != null && !item.getAuthorUid().isEmpty()
                && item.bottomBarText != null && !item.bottomBarText.isEmpty()) {
            watchText.setText(android.text.TextUtils.join("·", item.bottomBarText));
        } else if (hasAuthor) {
            // B-07 兜底：带作者但服务端没给底条分段时，生成文案不带
            // 剧名、信息摞大行又是 @作者名——剧名全页无处可认（全域
            // 账 B 片定格）。生成文案前冠剧名段（与分段同「·」接法），
            // 剧名至少在本条认得出；分段一到仍走上面的服务端原文。
            int total = item.getEpisodeCount();
            if (total <= 0 && item.isMultiSeason()) {
                int seasonTotal = seasonCountFor(item);
                if (seasonTotal > 0) total = seasonTotal;
            }
            String gen = HgPanelLogic.watchBarText(
                    item.kind == DramaItemKind.ALBUM, item.contentType,
                    item.isFinished(), total);
            String title = item.getTitle();
            watchText.setText(title != null && !title.isEmpty()
                    ? title + " · " + gen : gen);
        } else {
            // 集数：条目直读优先；多季条目直读缺失时回查专辑季表
            // （S-03，series_list[].episode_cnt 是集数正位）
            int total = item.getEpisodeCount();
            if (total <= 0 && item.isMultiSeason()) {
                int seasonTotal = seasonCountFor(item);
                if (seasonTotal > 0) total = seasonTotal;
            }
            watchText.setText(HgPanelLogic.watchBarText(
                    item.kind == DramaItemKind.ALBUM, item.contentType,
                    item.isFinished(), total));
        }
    }

    private void buildOverlays() {
        // 进度线已随底部整块在 buildBottomAssemblies 建好（progressWrap）
        // 清屏钮：正剧播放页＋合集页两面建（他 2026-10-31 点名合集页
        // 也得有；原 triage2 §4.1 只给正剧页的口径就此扩面）。形态＝选集
        // 条/合集条外右侧的独立圆角方钮，按下清屏、全隐只剩它且变淡
        // （alpha .42）。簇七扩面（全域账 B-13/B-08）：首页流与 UGC
        // 单条页此前缺席——两面的基线在 buildBottomAssemblies 已按
        // 各自底部整块预先算好（FEED＝底整块＋12、与右列 248 基线
        // 不相交；UGC＝贴底线＋12、右列 140 基线不相交），就此补建；
        // 单条视频页（MODE_VIDEO 无合集）仍不建（无条可依的旧口径）。
        if (mode == MODE_SERIES || mode == MODE_FEED || mode == MODE_UGC
                || (mode == MODE_VIDEO && videoAlbumId != null)) {
            cleanBtn = new ImageView(this);
            cleanBtn.setImageResource(R.drawable.ic_drama_exitfull);
            cleanBtn.setColorFilter(Color.WHITE);
            // 独立圆角方钮（官方图定形）：自带深灰圆角底，与选集条同色分开摆
            GradientDrawable cleanBg = new GradientDrawable();
            cleanBg.setColor(0xF0262629);
            cleanBg.setCornerRadius(dp(12));
            cleanBtn.setBackground(cleanBg);
            cleanBtn.setPadding(dp(11), dp(13), dp(11), dp(13));
            cleanBtn.setOnClickListener(v -> setCleanMode(!cleanMode));
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(46), dp(50));
            clp.gravity = Gravity.END | Gravity.BOTTOM;
            clp.rightMargin = dp(cleanBaseRight);
            clp.bottomMargin = cleanBaseBottom;
            root.addView(cleanBtn, clp);
        }

        // 中央播放/暂停闪标（定稿：暂停时显的 74dp 玻璃圆，此处是点按闪烁件）
        centerFlash = new ImageView(this);
        centerFlash.setColorFilter(0xE6FFFFFF);
        GradientDrawable cfBg = new GradientDrawable();
        cfBg.setShape(GradientDrawable.OVAL);
        cfBg.setColor(0x57140C0C);
        cfBg.setStroke(dp(1), 0x2EFFFFFF);
        centerFlash.setBackground(cfBg);
        centerFlash.setPadding(dp(16), dp(16), dp(16), dp(16));
        centerFlash.setVisibility(View.GONE);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(dp(74), dp(74));
        flp.gravity = Gravity.CENTER;
        root.addView(centerFlash, flp);

        // 整页状态（加载/失败/空，点按重试）
        LinearLayout state = new LinearLayout(this);
        state.setOrientation(LinearLayout.VERTICAL);
        state.setGravity(Gravity.CENTER);
        stateSpin = new ProgressBar(this);
        state.addView(stateSpin);
        stateText = new TextView(this);
        stateText.setTextColor(0xFFCCCCCC);
        stateText.setTextSize(14);
        stateText.setGravity(Gravity.CENTER);
        stateText.setPadding(dp(24), dp(12), dp(24), 0);
        state.addView(stateText);
        state.setVisibility(View.GONE);
        state.setOnClickListener(v -> {
            if (items.isEmpty()) {
                if (mode == MODE_SERIES) loadSeries();
                else if (mode == MODE_VIDEO) loadVideo();
                else if (mode == MODE_UGC) loadUgc();
                else loadChannel(true);
            }
        });
        stateView = state;
        root.addView(state, match());

        buildDrawer();
    }

    private void buildDrawer() {
        // 定稿 .drawer：宽 79%、浅底 #FBFAF8、右侧圆角 26、遮罩
        // rgba(10,8,8,.48)；整块黑是他 20:48 点名的错、已弃
        drawerScrim = new View(this);
        drawerScrim.setBackgroundColor(0x7A0A0808);
        drawerScrim.setVisibility(View.GONE);
        drawerScrim.setOnClickListener(v -> closeDrawer());
        root.addView(drawerScrim, match());

        drawerWidthPx = (int) (getResources().getDisplayMetrics().widthPixels * 0.79f);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(0xFFFBFAF8);
        float pr = dp(26);
        panelBg.setCornerRadii(new float[]{0, 0, pr, pr, pr, pr, 0, 0});
        panel.setBackground(panelBg);
        panel.setPadding(dp(20), statusBarHeight() + dp(32), dp(20), dp(18));
        // 台面只许他定的几样（昨夜最终定版）：播放设置三项（启动
        // 直达短剧/弹幕两开关＋清红果缓存）＋底部登录状态行；片库
        // 两行（观看历史/我的收藏）已撤——与我的页五格重复（他亲口
        // 点），无「我的」大卡、无小说历史行
        panel.addView(drawerHead("播放设置", true));
        panel.addView(drawerSwitch(R.drawable.ic_drama_play, "启动直达短剧",
                HgStore.isLaunchDirect(), on -> HgStore.setLaunchDirect(on),
                sw -> drawerDirectSwitch = sw));
        panel.addView(drawerSwitch(R.drawable.ic_drama_comment, "弹幕",
                HgStore.isDanmakuOn(), on -> {
                    HgStore.setDanmakuOn(on);
                    applyDanmakuVisibility();
                }, sw -> drawerDanmakuSwitch = sw));
        // 「自动连播」开关行整行摘除（他 2026-10-30 21:42 亲令）：
        // 连播改内置常开、不再给开关（HgStore.isAutoNext 恒真）
        panel.addView(drawerAction(R.drawable.ic_drama_close, "清红果缓存", v -> {
            DramaCache.clear();
            closeDrawer();
            Toast.makeText(this, "缓存已清", Toast.LENGTH_SHORT).show();
        }));
        View spacer = new View(this);
        panel.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1f));
        // 面板最底：登录状态行（字号/灰度照旧 foot 文案口径 12sp、
        // #8A8A93）；未登录可点进登录页，已登录纯状态显示不响应
        drawerLoginText = new TextView(this);
        drawerLoginText.setTextColor(0xFF8A8A93);
        drawerLoginText.setTextSize(12);
        refreshDrawerLoginText();
        panel.addView(drawerLoginText);
        drawerPanel = panel;
        panel.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(drawerWidthPx, -1);
        lp.gravity = Gravity.START;
        root.addView(panel, lp);
    }

    private TextView drawerHead(String text, boolean gapAbove) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFF8A8A93);
        t.setTextSize(12);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.14f);
        t.setPadding(dp(2), gapAbove ? dp(20) : 0, 0, dp(4));
        return t;
    }

    private interface BoolSetter {
        void set(boolean on);
    }

    /** 抽屉行左段：34dp 图标盒（定稿 .d-ic 近似——草稿是定制线图标，树里没有，用现有 drawable 近似，账里已记）。 */
    private FrameLayout drawerIconBox(int res) {
        FrameLayout box = new FrameLayout(this);
        box.setBackground(roundBg(0x0D1B1B1F, dp(10)));
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setColorFilter(0xFF55555E);
        iv.setPadding(dp(8), dp(8), dp(8), dp(8));
        box.addView(iv, new FrameLayout.LayoutParams(dp(34), dp(34)));
        return box;
    }

    private View drawerSwitch(int icon, String label, boolean initial, BoolSetter setter,
                              java.util.function.Consumer<HgSwitch> capture) {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(13), dp(2), dp(13));
        row.addView(drawerIconBox(icon), new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(0xFF1B1B1F);
        t.setTextSize(14.5f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.leftMargin = dp(12);
        row.addView(t, tlp);
        HgSwitch sw = new HgSwitch(this);
        sw.setChecked(initial, false);
        sw.setOnToggleListener(setter::set);
        if (capture != null) capture.accept(sw);
        row.addView(sw, new LinearLayout.LayoutParams(dp(46), dp(27)));
        outer.addView(row);
        View div = new View(this);
        div.setBackgroundColor(0x121B1B1F);
        outer.addView(div, new LinearLayout.LayoutParams(-1, 1));
        return outer;
    }

    private View drawerAction(int icon, String label, View.OnClickListener click) {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(13), dp(2), dp(13));
        row.addView(drawerIconBox(icon), new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(0xFF1B1B1F);
        t.setTextSize(14.5f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.leftMargin = dp(12);
        row.addView(t, tlp);
        outer.addView(row);
        View div = new View(this);
        div.setBackgroundColor(0x121B1B1F);
        outer.addView(div, new LinearLayout.LayoutParams(-1, 1));
        outer.setOnClickListener(click);
        return outer;
    }

    /** 登录状态行刷新：未登录恒「登录状态：未登录」且可点进登录页；
     * 已登录显「登录状态：已登录」，会话 name 非空时显 name，不响应点击。 */
    private void refreshDrawerLoginText() {
        if (drawerLoginText == null) return;
        if (!HgAccountStore.isLoggedIn()) {
            drawerLoginText.setText("登录状态：未登录");
            drawerLoginText.setOnClickListener(v -> {
                closeDrawer();
                HongguoLoginActivity.start(this);
            });
        } else {
            HgAccountStore.Session session = HgAccountStore.load();
            String name = session == null ? null : session.name;
            drawerLoginText.setText(name != null && !name.isEmpty()
                    ? "登录状态：" + name : "登录状态：已登录");
            drawerLoginText.setOnClickListener(null);
            drawerLoginText.setClickable(false);
        }
    }

    /** 抽屉开合不许硬切：面板左滑入＋暗幕淡入，关时反向（HgTransition 同族口径）。 */
    private void openDrawer() {
        refreshDrawerLoginText();
        // 开关状态回读（#21）：抽屉只在 onCreate 建一次，更多面板等
        // 别处改过同一键后抽屉里的开关会停在旧态、点下去写的与眼
        // 见的相反（全域账 D 片 4.4）。开抽屉时按存档值复位两开关。
        if (drawerDirectSwitch != null) {
            drawerDirectSwitch.setChecked(HgStore.isLaunchDirect(), false);
        }
        if (drawerDanmakuSwitch != null) {
            drawerDanmakuSwitch.setChecked(HgStore.isDanmakuOn(), false);
        }
        drawerScrim.animate().cancel();
        drawerPanel.animate().cancel();
        drawerScrim.setVisibility(View.VISIBLE);
        drawerScrim.setAlpha(0f);
        drawerScrim.animate().alpha(1f).setDuration(200)
                .setInterpolator(new DecelerateInterpolator());
        drawerPanel.setVisibility(View.VISIBLE);
        drawerPanel.setTranslationX(-drawerWidthPx);
        drawerPanel.animate().translationX(0f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator());
    }

    private void closeDrawerNow() {
        drawerScrim.animate().cancel();
        drawerPanel.animate().cancel();
        drawerScrim.setVisibility(View.GONE);
        drawerPanel.setVisibility(View.GONE);
        drawerPanel.setTranslationX(0f);
        drawerScrim.setAlpha(1f);
    }

    private void closeDrawer() {
        if (drawerPanel.getVisibility() != View.VISIBLE) return;
        drawerScrim.animate().alpha(0f).setDuration(180)
                .setInterpolator(new AccelerateInterpolator());
        drawerPanel.animate().translationX(-drawerWidthPx).setDuration(180)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    drawerScrim.setVisibility(View.GONE);
                    drawerPanel.setVisibility(View.GONE);
                    drawerPanel.setTranslationX(0f);
                    drawerScrim.setAlpha(1f);
                });
    }

    private ImageView iconBtn(int res, View.OnClickListener click) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setColorFilter(Color.WHITE);
        iv.setPadding(dp(8), dp(8), dp(8), dp(8));
        iv.setOnClickListener(click);
        return iv;
    }

    /** 定稿玻璃圆钮（.glass/.p-back-btn）：底 rgba(14,12,12,.34)＋内环 rgba(255,255,255,.18)，38dp。 */
    private ImageView glassIconBtn(int res, View.OnClickListener click) {
        ImageView iv = new ImageView(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0x570E0C0C);
        g.setStroke(dp(1), 0x2EFFFFFF);
        iv.setBackground(g);
        iv.setImageResource(res);
        iv.setColorFilter(Color.WHITE);
        iv.setPadding(dp(9), dp(9), dp(9), dp(9));
        iv.setOnClickListener(click);
        return iv;
    }

    /** 进度行时间签文案（定稿 .p-time）：m:ss。 */
    private static String fmtTime(long ms) {
        long s = Math.max(0, ms) / 1000;
        return (s / 60) + ":" + String.format(java.util.Locale.US, "%02d", s % 60);
    }

    private GradientDrawable roundBg(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
    }

    /** 真实手势区高（窗口 insets 捕获，未捕获前为 -1）。 */
    private int realNavInset = -1;

    private int navBarHeight() {
        // #4b：让位优先读真实手势区高——静态 dimen 在手势导航下
        // 与真值对不上；insets 未到时才回落 dimen。
        if (realNavInset >= 0) return realNavInset;
        int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    /** 捕获窗口底部真实 insets（bind 期 bottomBlockH 等活算处随即用真值）。 */
    private void watchNavInsets() {
        getWindow().getDecorView().setOnApplyWindowInsetsListener((v, insets) -> {
            realNavInset = insets.getSystemWindowInsetBottom();
            return insets;
        });
    }

    private void showState(String text, boolean spin) {
        stateText.setText(text);
        stateSpin.setVisibility(spin ? View.VISIBLE : View.GONE);
        stateView.setVisibility(View.VISIBLE);
    }

    private void hideState() {
        stateView.setVisibility(View.GONE);
    }

    // ---------- 数据 ----------

    private void loadChannel(boolean first) {
        if (loadingPage) return;
        if (first) {
            items.clear();
            feedFailedKeys.clear();
            adapter.notifyDataSetChanged();
            currentIndex = -1;
            continuation = null;
            hasMore = false;
            showState("加载中…", true);
        }
        loadingPage = true;
        if (first || continuation == null) {
            DramaClient.fetchChannel(HOME_CHANNEL[0], HOME_CHANNEL[1], new HgCallback<com.fongmi.android.tv.hongguo.drama.DramaCellPage>() {
                @Override
                public void onSuccess(com.fongmi.android.tv.hongguo.drama.DramaCellPage page) {
                    runOnUiThread(() -> onChannelPage(page, first));
                }

                @Override
                public void onError(int code, String msg) {
                    runOnUiThread(() -> {
                        loadingPage = false;
                        if (items.isEmpty()) showState("取数据失败，点按重试", false);
                    });
                }
            }, true);
        } else {
            fetchMorePage(false);
        }
    }

    /**
     * 频道续拉（#13）：失败不再全静默——留一行日志，并自动重试
     * 一次（续拉失败此前只把 loadingPage 放下，流就永远停在第一
     * 页条数、用户只见「滑不动」）。重试仍失败才收口。
     */
    private void fetchMorePage(boolean isRetry) {
        DramaClient.fetchChannelMore(continuation, new HgCallback<com.fongmi.android.tv.hongguo.drama.DramaCellPage>() {
            @Override
            public void onSuccess(com.fongmi.android.tv.hongguo.drama.DramaCellPage page) {
                runOnUiThread(() -> onChannelPage(page, false));
            }

            @Override
            public void onError(int code, String msg) {
                android.util.Log.w("HongguoFeed", "频道续拉失败 code=" + code
                        + " msg=" + msg + " isRetry=" + isRetry);
                runOnUiThread(() -> {
                    if (!isRetry) fetchMorePage(true);
                    else loadingPage = false;
                });
            }
        }, true);
    }

    private void onChannelPage(com.fongmi.android.tv.hongguo.drama.DramaCellPage page, boolean first) {
        loadingPage = false;
        // 续拉触发门改以 offset 推进为准（#13 活体定段，账
        // fix168-remedies-20261031 件一）：固定脸实拉 bookmall/tab，
        // 开页 tab16 的 has_more 在场但恒 false、next_offset 在场（0），
        // 旧门 hasMore＝page.isHasMore() 在开页就把续拉焊死（凭证与
        // offset 都在、门不开，与「只有四条」同形）。新判据：凭证带
        // 着 next_offset 且较本页所用 offset 前进了就还有下页——
        // has_more 缺失/为 false 不再单独否决；offset 不前进（同值/
        // 缺失）即停，防服务端卡住时同页死循环。首轮开页无所用
        // offset，凭证在位即算有下页（与剧场 advanceContinuation 的
        // 首轮口径一致）。
        String usedOffset = continuation != null ? continuation.getOffset() : null;
        if (page.getContinuation() != null) continuation = page.getContinuation();
        else if (page.getNextOffset() != null && continuation != null) {
            continuation = new HgContinuation(continuation.getCellId(), page.getSessionId(),
                    continuation.getClientTemplate(), continuation.getShowType(),
                    continuation.getTabType(), page.getNextOffset());
        }
        String nextOffset = continuation != null ? continuation.getOffset() : null;
        hasMore = nextOffset != null && !nextOffset.isEmpty()
                && (usedOffset == null || !nextOffset.equals(usedOffset));
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (DramaItem it : items) if (it.getSeriesId() != null) seen.add(it.getSeriesId() + "/" + it.getVid());
        int added = 0;
        for (DramaItem it : page.getItems()) {
            String key = it.getSeriesId() + "/" + it.getVid();
            if (it.getVid() == null && it.getSeriesId() == null) continue;
            // 官方口径：推荐流里每条都是能直接起播的画面，海报形态不许
            // 进流——未上线预约条（带 appointment，播不了）、无内联 vid
            // 的合集条（身份在专辑、流里定不了播）、无 vid 的短片条，
            // 进流前滤掉（账 hongguo-feed-noposter-20261030）。无 vid 的
            // 正剧条不滤：settle 时走详情定首集（见 playFeedItem）。
            if (it.hasAppointment()) continue;
            if (it.kind == DramaItemKind.ALBUM && it.getVid() == null) continue;
            if (it.kind == DramaItemKind.UGC_CLIP && it.getVid() == null) continue;
            if (!seen.add(key)) continue;
            items.add(it);
            added++;
        }
        adapter.notifyDataSetChanged();
        if (items.isEmpty()) {
            showState("暂时没有内容，点按重试", false);
            return;
        }
        hideState();
        if (first) {
            int start = HgStore.feedPosIndex(HOME_POS_KEY);
            if (start > 0 && start >= items.size() && hasMore) {
                // 进程重建恢复（B-23）：存档条位在首批之外——旧写法
                // 直接夹回 0，已拉页与位置一起丢。沿续拉链逐页补到
                // 存档位够得着再落位（补拉封顶防服务端异常时无限追
                // 页）；补拉途中用户先滑走（settle 已接管
                // currentIndex）即弃恢复。冷启断点续看同走此路
                // （feedPos 存档的本义就是续看位置）。
                restoreTarget = start;
                restorePulls = 0;
                showState("加载中…", true);
                loadChannel(false);
                return;
            }
            if (start < 0 || start >= items.size()) start = 0;
            feedStartPending = true;
            pager.scrollToPosition(start);
            final int startPos = start;
            main.post(() -> settleTo(startPos));
        } else if (restoreTarget >= 0) {
            if (currentIndex >= 0) {
                restoreTarget = -1;
            } else if (items.size() > restoreTarget || !hasMore
                    || ++restorePulls >= RESTORE_MAX_PULLS) {
                int start = Math.min(restoreTarget, items.size() - 1);
                restoreTarget = -1;
                if (start >= 0) {
                    feedStartPending = true;
                    pager.scrollToPosition(start);
                    final int startPos = start;
                    main.post(() -> {
                        if (currentIndex < 0) settleTo(startPos);
                    });
                }
            } else {
                showState("加载中…", true);
                loadChannel(false);
            }
        }
    }

    private void loadUgc() {
        String vid = getIntent().getStringExtra(EXTRA_UGC_VID);
        String sid = getIntent().getStringExtra(EXTRA_UGC_SERIES);
        if (vid == null || vid.isEmpty()) {
            showState("内容不存在", false);
            return;
        }
        hideState();
        items.clear();
        items.add(DramaItem.fromDetail(sid == null ? vid : sid,
                getIntent().getStringExtra(EXTRA_TITLE),
                getIntent().getStringExtra(EXTRA_COVER), null, 0, vid, null,
                null, null, null, getIntent().getIntExtra(EXTRA_CONTENT_TYPE, 1001), -1, -1));
        adapter.notifyDataSetChanged();
        feedStartPending = true;
        pager.scrollToPosition(0);
        main.post(() -> settleTo(0));
    }

    /** 视频页加载：合集走 album_detail 拉全队列；单条照 intent 一条成页。 */
    private void loadVideo() {
        if (videoAlbumId == null || videoAlbumId.isEmpty()) {
            loadUgcIntoVideo();
            return;
        }
        showState("加载中…", true);
        DramaClient.fetchAlbumDetail(videoAlbumId, new HgCallback<DramaAlbumDetail>() {
            @Override
            public void onSuccess(DramaAlbumDetail d) {
                runOnUiThread(() -> {
                    videoAlbum = d;
                    videoAuthorUid = d.authorUid;
                    videoAuthorName = d.authorName;
                    videoAuthorAvatar = d.authorAvatar;
                    if (d.title != null && !d.title.isEmpty()) {
                        seriesTitleView.setText(d.title);
                    }
                    items.clear();
                    items.addAll(d.rows);
                    adapter.notifyDataSetChanged();
                    if (albumBarText != null) {
                        boolean finished = d.seriesStatus != null && d.seriesStatus == 1;
                        int total = d.episodeCount > 0 ? d.episodeCount : d.rows.size();
                        String count = HgPanelLogic.countText(finished, total);
                        albumBarText.setText("合集 · " + (d.title == null ? "" : d.title)
                                + (count.isEmpty() ? "" : " · " + count));
                    }
                    if (items.isEmpty()) {
                        showState("内容不存在", false);
                        return;
                    }
                    hideState();
                    // 落点：带入口 vid（点的是合集里某一集）就落那集，
                    // 找不到才回第一集（原口径）。
                    int startAt = 0;
                    String entryVid = getIntent().getStringExtra(EXTRA_UGC_VID);
                    if (entryVid != null && !entryVid.isEmpty()) {
                        for (int i = 0; i < items.size(); i++) {
                            if (entryVid.equals(items.get(i).getVid())) {
                                startAt = i;
                                break;
                            }
                        }
                    }
                    final int startIdx = startAt;
                    feedStartPending = true;
                    pager.scrollToPosition(startIdx);
                    main.post(() -> settleTo(startIdx));
                });
            }

            @Override
            public void onError(int code, String msg) {
                runOnUiThread(() -> showState("取数据失败，点按重试", false));
            }
        });
    }

    private void loadUgcIntoVideo() {
        String vid = getIntent().getStringExtra(EXTRA_UGC_VID);
        String sid = getIntent().getStringExtra(EXTRA_UGC_SERIES);
        if (vid == null || vid.isEmpty()) {
            showState("内容不存在", false);
            return;
        }
        hideState();
        items.clear();
        DramaItem item = DramaItem.fromDetail(sid == null ? vid : sid,
                getIntent().getStringExtra(EXTRA_TITLE),
                getIntent().getStringExtra(EXTRA_COVER), null, 0, vid, null,
                videoAuthorName, videoAuthorAvatar, videoAuthorUid,
                getIntent().getIntExtra(EXTRA_CONTENT_TYPE, 1001), -1, -1);
        // 文案：单条入口没有 desc 附加，标题即文案（bind 时 desc 空回退标题）
        items.add(item);
        adapter.notifyDataSetChanged();
        feedStartPending = true;
        pager.scrollToPosition(0);
        main.post(() -> settleTo(0));
    }

    /**
     * 合集行播放：行是剧集卡（无 vid），先取该行详情定首集 vid 再播
     * （UGC 合集每行＝一条单集视频的 series，详情回包带其 vid/模型）。
     */
    private void playAlbumRow(DramaItem item) {
        if (item.getVid() != null && !item.getVid().isEmpty()) {
            playVid(item.getVid(), item.contentType, 0);
            return;
        }
        String sid = item.getSeriesId();
        if (sid == null) {
            FeedAdapter.Holder h = currentHolder();
            if (h != null) {
                h.spin.setVisibility(View.GONE);
                h.fail.setVisibility(View.VISIBLE);
            }
            return;
        }
        ensureDetail(sid, detail -> {
            if (detail == null || detail.getEpisodes().isEmpty()) {
                FeedAdapter.Holder h = currentHolder();
                if (h != null) {
                    h.spin.setVisibility(View.GONE);
                    h.fail.setVisibility(View.VISIBLE);
                }
                return;
            }
            playVid(detail.getEpisodes().get(0).getVideoId(), detail.getContentType(), 0);
        });
    }

    /** 合集条点开：同系列列表，点行跳播。 */
    private void openAlbumSheet() {
        panelGuardUntil = System.currentTimeMillis() + 1500;
        if (videoAlbum == null) return;
        HgAlbumSheet.show(this, videoAlbum, currentIndex,
                idx -> {
                    if (idx >= 0 && idx < items.size()) {
                        pager.scrollToPosition(idx);
                        main.post(() -> settleTo(idx));
                    }
                });
    }

    private void loadSeries() {
        showState("加载中…", true);
        if (seriesIdArg == null || seriesIdArg.isEmpty()) {
            showState("剧集不存在", false);
            return;
        }
        DramaClient.fetchDetail(seriesIdArg, new HgCallback<DramaDetail>() {
            @Override
            public void onSuccess(DramaDetail detail) {
                runOnUiThread(() -> {
                    detailCache.put(seriesIdArg, detail);
                    episodes = detail.getEpisodes();
                    items.clear();
                    // 逐集分页（D4）：一集一条目一页，上下滑即换集；
                    // 条目除 vid 外同源（剧名/封面/作者都取详情）
                    for (DramaDetail.Episode ep : episodes) {
                        items.add(DramaItem.fromDetail(detail.getSeriesId(), detail.getTitle(),
                                detail.getCoverUrl(), detail.getDesc(), detail.getEpisodeCount(),
                                ep.getVideoId(), detail.getSeriesStatus(), detail.getAuthorName(),
                                null, detail.getAuthorUid(), detail.getContentType(), -1,
                                ep.getDiggCount(), detail.getFollowedCnt()));
                    }
                    if (items.isEmpty()) {
                        items.add(DramaItem.fromDetail(detail.getSeriesId(), detail.getTitle(),
                                detail.getCoverUrl(), detail.getDesc(), detail.getEpisodeCount(),
                                null, detail.getSeriesStatus(), detail.getAuthorName(),
                                null, detail.getAuthorUid(), detail.getContentType(), -1, -1,
                                detail.getFollowedCnt()));
                    }
                    adapter.notifyDataSetChanged();
                    hideState();
                    int startEp = getIntent().getIntExtra(EXTRA_EPISODE, 0);
                    if (startEp <= 0) {
                        HgStore.Progress p = HgStore.progressOf(seriesIdArg);
                        startEp = p != null ? p.episodeIndex : 1;
                    }
                    startEp = Math.max(1, Math.min(startEp, Math.max(1, items.size())));
                    // 进页钉位（自动进剧高光支带入）：登记到起始集，
                    // 首集 playEpisode 时消费（盖过断点存档）
                    long startMs = getIntent().getLongExtra(EXTRA_START_MS, -1);
                    if (startMs >= 0) {
                        entrySeekEp = startEp;
                        entrySeekMs = startMs;
                    }
                    currentEpisodeIndex = startEp;
                    feedStartPending = true;
                    final int startPos = startEp - 1;
                    pager.scrollToPosition(startPos);
                    main.post(() -> settleTo(startPos));
                });
            }

            @Override
            public void onError(int code, String msg) {
                runOnUiThread(() -> showState("取数据失败，点按重试", false));
            }
        });
    }

    private void saveFeedPos() {
        if (mode == MODE_FEED && currentIndex >= 0) {
            HgStore.saveFeedPos(HOME_POS_KEY, currentIndex);
        }
    }

    // ---------- 播放会话 ----------

    private void settleTo(int index) {
        if (index < 0 || index >= items.size()) return;
        if (index == currentIndex && currentVid != null && player != null) return;
        saveProgressNow();
        int prevIndex = currentIndex;
        currentIndex = index;
        boolean freezeWait = false;
        if (mode == MODE_SERIES && prevIndex != index
                && firstFrameForVid && player != null) {
            // 切集顺滑（末帧冻结）：趁播放面还挂在上一集的页上、
            // 还留着上一集的帧，先抓下来；mount 搬面之后由
            // playVid 把冻结图挂到新页（抓帧点必须在搬面之前）。
            // 时序定序（件二，账 fix168-remedies-20261031）：旧写法
            // 发起抓帧与 mountPlayerView 搬面同栈先后跑——PixelCopy
            // 是异步拷贝，请求刚发出播放面就被摘下重挂、旧 Surface
            // 随之销毁，拷贝执行时源面存亡全凭系统时序，真机成败
            // 不可证。改确定性顺序：先发起抓帧、本方法的后半程
            // （proceedSettle：搬面＋起播）等这发抓帧落定才走；
            // 等待期间播放器与播放面一律不动、上一集照常播，不是
            // 卡住切集；超时（FREEZE_WAIT_MS）走无冻结常路。
            initiateFreezeCapture(index);
            freezeArmed = true;
            freezeWait = true;
        }
        if (mode == MODE_FEED) {
            // D7：流内线是本条的历史进度线，先清零，时长到手后按
            // 存档定版（tick 内一次性），不跟实时播放走
            feedLineIndex = index;
            feedLineDone = false;
            applyFeedLine(0f);
        }
        if (freezeWait) {
            awaitFreezeThenSettle(index);
            return;
        }
        proceedSettle(index);
    }

    /** settleTo 的后半程：搬面＋按模式起播（等帧时由续程接力）。 */
    private void proceedSettle(int index) {
        DramaItem item = items.get(index);
        currentSeriesId = item.getSeriesId();
        ensureCommentCount(index, item);
        mountPlayerView(index);
        bindRailState(index);
        updateWatchBar();
        if (mode == MODE_FEED && item.isMultiSeason() && item.getEpisodeCount() <= 0
                && seasonCountFor(item) <= 0) {
            // S-03 集数回查：多季条目直读集数缺失时，观看完整条只剩
            // 剧种头没集数——集数正位在专辑 series_list[].episode_cnt，
            // 名单到手后若眼前仍是本条，重算一次条文案（名单同时是
            // S-01 季单的货源，一发两用）。
            ensureSeasonRows(item, () -> {
                if (currentIndex >= 0 && currentIndex < items.size()
                        && items.get(currentIndex) == item) {
                    updateWatchBar();
                }
            });
        }
        if (mode == MODE_SERIES) {
            // 逐集分页（D4）：页序号即集序；选集跳集/播完连播滑来的
            // 页从头播，手动滑来/初入的页按断点续播
            boolean fromHead = sheetJumpEp == index + 1 || autoNextEp == index + 1;
            sheetJumpEp = 0;
            autoNextEp = 0;
            if (inboundBag != null && inboundBag.vid != null
                    && inboundBag.vid.equals(item.getVid())) {
                // 承接落点对上：领引擎续播这一集，不走 playEpisode
                // 的重取链（切换手感头号缝的入站半程）。
                adoptHandoff(index);
            } else {
                if (inboundBag != null) {
                    // 落点集与泊来 vid 对不上：泊车作废、走常路起播
                    DramaPlayerPool.releaseParked();
                    inboundBag = null;
                }
                playEpisode(index + 1, !fromHead);
            }
        } else if (mode == MODE_VIDEO && videoAlbumId != null) {
            currentEpisodeIndex = 1;
            playAlbumRow(item);
        } else if (mode == MODE_UGC || mode == MODE_VIDEO) {
            currentEpisodeIndex = 1;
            playVid(item.getVid(), item.contentType, 0);
        } else {
            playFeedItem(item);
        }
        schedulePrefetch(index);
        if (mode == MODE_FEED) saveFeedPosDelayed();
    }

    /** 评论数补拉在途标记（seriesId|vid），防同一条重复拉。 */
    private final java.util.Set<String> countInFlight = new java.util.HashSet<>();

    /**
     * 评论数主动补拉（他 2026-10-31 点名：推荐流也不主动显示、播放页
     * 非要点进去才有数）：条目落到眼前时若没带计数，走评论只读口拉
     * 一次真 total 回写本条并重绑右列。评论口是匿名身份、与播放脸
     * 分家，不烧播放的量；已有数或在途都不重复发。
     */
    private void ensureCommentCount(int index, DramaItem item) {
        if (item.getCommentCount() > 0) return;
        String sid = item.getSeriesId();
        String vid = item.getVid();
        if (sid == null || sid.isEmpty()) return;
        if (vid != null && !vid.isEmpty()) {
            pullCommentCount(index, item, sid, vid);
            return;
        }
        // 无内联 vid 的剧头条（B-27）：旧门槛要求 sid+vid 双非空，
        // 这类条目恒不拉、评论数恒显「评论」原文。头条起播走详情定
        // 首集（playFeedItem 同路），计数同口径——取详情首集 vid 拉
        // 本集计数；ensureDetail 有缓存、与起播共用一份详情不重取。
        String rkey = sid + "|resolve";
        synchronized (countInFlight) {
            if (!countInFlight.add(rkey)) return;
        }
        ensureDetail(sid, detail -> {
            synchronized (countInFlight) {
                countInFlight.remove(rkey);
            }
            if (detail == null || detail.getEpisodes().isEmpty()) return;
            String epVid = detail.getEpisodes().get(0).getVideoId();
            if (epVid == null || epVid.isEmpty()) return;
            if (index < 0 || index >= items.size() || items.get(index) != item) return;
            pullCommentCount(index, item, sid, epVid);
        });
    }

    /** 按（剧＋集）拉一次评论真 total 回写条目并重绑右列。 */
    private void pullCommentCount(int index, DramaItem item, String sid, String vid) {
        String key = sid + "|" + vid;
        synchronized (countInFlight) {
            if (!countInFlight.add(key)) return;
        }
        safeExecute(() -> {
            com.fongmi.android.tv.api.HongguoCommentApi.Page page =
                    com.fongmi.android.tv.api.HongguoCommentApi.episodeComments(sid, vid, "");
            runOnUiThread(() -> {
                synchronized (countInFlight) {
                    countInFlight.remove(key);
                }
                if (destroyed) return;
                if (page != null && page.total > 0
                        && index >= 0 && index < items.size()
                        && items.get(index) == item
                        && item.getCommentCount() != page.total) {
                    item.setCommentCount(page.total);
                    adapter.notifyItemChanged(index);
                }
            });
        });
    }

    /** 季名单缓存（albumId → 季行表）与在途/等待者（簇七 S-01/S-03 共用一本账）。 */
    private final java.util.Map<String, java.util.List<DramaItem>> seasonRowsByAlbum =
            new java.util.HashMap<>();
    private final java.util.Set<String> seasonInFlight = new java.util.HashSet<>();
    private final java.util.Map<String, java.util.List<Runnable>> seasonWaiters =
            new java.util.HashMap<>();

    /**
     * 季名单现成路（S-01）：条目自带 siblings（回包 album_data_list
     * 解析）优先、其次本页缓存的专辑详情名单；两路都只认 ≥2 行
     * （单行就是它自己、不成其为名单）。没有返回 null。
     */
    private java.util.List<DramaItem> seasonRowsOf(DramaItem item) {
        if (item == null) return null;
        if (item.getSiblings() != null && item.getSiblings().size() > 1) {
            return item.getSiblings();
        }
        String albumId = item.getAlbumId();
        if (albumId == null || albumId.isEmpty()) return null;
        java.util.List<DramaItem> rows = seasonRowsByAlbum.get(albumId);
        return rows != null && rows.size() > 1 ? rows : null;
    }

    /**
     * 季名单备齐（S-01/S-03 共用）：现成有就同步回调；没有且有
     * albumId 就拉一发专辑详情（同 album 只在途一发，后来者排队
     * 等同一发），落缓存后逐个通知等待者。拉败不缓存、下次再试。
     */
    private void ensureSeasonRows(DramaItem item, Runnable onReady) {
        if (item == null) return;
        if (seasonRowsOf(item) != null) {
            if (onReady != null) onReady.run();
            return;
        }
        final String albumId = item.getAlbumId();
        if (albumId == null || albumId.isEmpty()) return;
        synchronized (seasonInFlight) {
            if (!seasonInFlight.add(albumId)) {
                if (onReady != null) {
                    seasonWaiters.computeIfAbsent(albumId,
                            k -> new java.util.ArrayList<>()).add(onReady);
                }
                return;
            }
        }
        DramaClient.fetchAlbumDetail(albumId, new HgCallback<DramaAlbumDetail>() {
            @Override
            public void onSuccess(DramaAlbumDetail d) {
                runOnUiThread(() -> {
                    java.util.List<Runnable> waiters;
                    synchronized (seasonInFlight) {
                        seasonInFlight.remove(albumId);
                        waiters = seasonWaiters.remove(albumId);
                    }
                    if (destroyed) return;
                    if (d != null && d.rows != null && d.rows.size() > 1) {
                        seasonRowsByAlbum.put(albumId, d.rows);
                    }
                    if (onReady != null) onReady.run();
                    if (waiters != null) {
                        for (Runnable r : waiters) r.run();
                    }
                });
            }

            @Override
            public void onError(int code, String msg) {
                runOnUiThread(() -> {
                    synchronized (seasonInFlight) {
                        seasonInFlight.remove(albumId);
                        seasonWaiters.remove(albumId);
                    }
                });
            }
        });
    }

    /** 当季集数回查（S-03）：季行表里与本条同 seriesId 行的 episode_cnt；查不到 -1。 */
    private int seasonCountFor(DramaItem item) {
        java.util.List<DramaItem> rows = seasonRowsOf(item);
        if (rows == null || item.getSeriesId() == null) return -1;
        for (DramaItem row : rows) {
            if (item.getSeriesId().equals(row.getSeriesId())) {
                return row.getEpisodeCount();
            }
        }
        return -1;
    }

    /**
     * 系列剧药丸点击分发（S-01；B-01 落点留账）：多季剧先弹季名单
     * 选季进剧——名单是现成机器（siblings/专辑详情 series_list）；
     * 单季/无专辑身份维持原进剧路。官方药丸点开形态未抠到
     * （hongguo-official-values 在抠），抠值回后若形态不同，
     * 只校本分发一处。
     */
    private void onSeriesPillClick(DramaItem item) {
        if (item == null || item.getSeriesId() == null) return;
        if (!item.isMultiSeason() || item.getAlbumSerialCount() == 1) {
            openSeries(item, 0);
            return;
        }
        java.util.List<DramaItem> rows = seasonRowsOf(item);
        if (rows != null) {
            showSeasonSheet(item, rows);
            return;
        }
        if (item.getAlbumId() == null || item.getAlbumId().isEmpty()) {
            openSeries(item, 0);
            return;
        }
        ensureSeasonRows(item, () -> {
            java.util.List<DramaItem> r = seasonRowsOf(item);
            if (r != null) {
                showSeasonSheet(item, r);
            } else {
                // 名单拉不到/只有一季：点击不许落空，退回直接进剧
                openSeries(item, 0);
            }
        });
    }

    private void showSeasonSheet(DramaItem item, java.util.List<DramaItem> rows) {
        panelGuardUntil = System.currentTimeMillis() + 1500;
        HgSeasonSheet.show(this, item.getTitle(), rows, item.getSeriesId(),
                item.getAlbumSerialCount(),
                picked -> {
                    if (picked != null && picked.getSeriesId() != null) {
                        openSeries(picked, 0);
                    }
                });
    }

    private void saveFeedPosDelayed() {
        main.removeCallbacks(savePosRunnable);
        main.postDelayed(savePosRunnable, 600);
    }

    private final Runnable savePosRunnable = this::saveFeedPos;

    private void mountPlayerView(int index) {
        RecyclerView.ViewHolder vh = pager.findViewHolderForAdapterPosition(index);
        if (vh instanceof FeedAdapter.Holder) {
            FeedAdapter.Holder h = (FeedAdapter.Holder) vh;
            if (playerView.getParent() != null) {
                ((ViewGroup) playerView.getParent()).removeView(playerView);
            }
            h.surface.addView(playerView, match());
            // #8：起播空窗不许亮封面——挂载只亮转圈，画面出来前是
            // 黑场；封面只给未起播条目作静态呈现（绑定侧管）。
            h.cover.setVisibility(View.GONE);
            h.spin.setVisibility(View.VISIBLE);
            h.fail.setVisibility(View.GONE);
            // 停稳即是一次新起播尝试：旧失败记账摘除，成败由本次判定
            if (mode == MODE_FEED && index >= 0 && index < items.size()) {
                feedFailedKeys.remove(feedKeyOf(items.get(index)));
            }
            // 切集冻结图若已先到（同步抓帧），搬面后重新提到
            // playerView 之上（addView 后到者在上）
            if (freezeBitmap != null && freezeForIndex == index) attachFreeze();
        }
    }

    private FeedAdapter.Holder currentHolder() {
        RecyclerView.ViewHolder vh = pager.findViewHolderForAdapterPosition(currentIndex);
        return vh instanceof FeedAdapter.Holder ? (FeedAdapter.Holder) vh : null;
    }

    /**
     * 全屏观看钮跟着视频走（他 2026-10-31 连点四遍）：读画面视图
     * 在当前页里的真实下缘，钮悬在下缘**之下** 15dp、水平居中
     * （官方帧 113476 量值：画面下沿 y≈59.8%、药丸顶 y≈61.8%，
     * 间隙≈屏高 2.0%，与 feed-aspect-bar 旧账定版 15dp 同值；
     * 件九，他 17:35 点名落位不对）。
     * 旧落码 want＝离页底距＋10dp：bottomMargin 是钮底到页底的距、
     * 越大越往上——那把钮推进了画面内侧（底沿还高出画面下沿
     * 10dp、整颗压在画面上），与「下缘下方」正相反，符号反了。
     * 正解：want＝离页底距－15dp－钮高（钮顶落在下缘下 15dp 处）。
     * 画面尺寸一变布局监听就重摆，量不到就不动。
     */
    private void anchorFullPill() {
        FeedAdapter.Holder h = currentHolder();
        if (h == null || h.fullPill == null || playerView == null) return;
        View sv = playerView.getVideoSurfaceView();
        if (sv == null || sv.getWidth() <= 0 || sv.getHeight() <= 0) return;
        int pageH = h.itemView.getHeight();
        if (pageH <= 0) return;
        int[] pageLoc = new int[2];
        int[] surfLoc = new int[2];
        h.itemView.getLocationInWindow(pageLoc);
        sv.getLocationInWindow(surfLoc);
        int videoBottomInPage = surfLoc[1] + sv.getHeight() - pageLoc[1];
        ViewGroup.LayoutParams lp = h.fullPill.getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
            int pillH = h.fullPill.getHeight();
            if (pillH <= 0) {
                h.fullPill.measure(
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                pillH = h.fullPill.getMeasuredHeight();
            }
            if (pillH <= 0) pillH = dp(37);
            int want = pageH - videoBottomInPage - dp(15) - pillH;
            if (want < 0) want = 0;
            if (flp.bottomMargin != want) {
                flp.bottomMargin = want;
                h.fullPill.setLayoutParams(flp);
            }
        }
    }

    private void playFeedItem(DramaItem item) {
        String sid = item.getSeriesId();
        // D3（互动账 §2）：流内恒播条目自带 vid 本身（服务端推的
        // 那一集），不许换成断点集、也不按断点秒数起播——断点只
        // 用于手动进剧。进度按被推集集序记账：vidIndex 现成优先，
        // 缺失时按 vid 在详情集表定位（只为记账，不改播什么）。
        feedEpIndex = item.vidIndex > 0 ? item.vidIndex : 1;
        currentEpisodeIndex = feedEpIndex;
        if (item.getVid() == null) {
            // 正剧条没带内联 vid：走详情定首集（与合集行同路）；详情
            // 空/失败落失败呈现，封面不许当内容停在流里。
            if (sid == null) {
                showPlayFailure();
                return;
            }
            ensureDetail(sid, detail -> {
                if (detail == null || detail.getEpisodes().isEmpty()) {
                    showPlayFailure();
                    return;
                }
                feedEpIndex = 1;
                currentEpisodeIndex = 1;
                playVid(detail.getEpisodes().get(0).getVideoId(),
                        detail.getContentType(), 0);
            });
            return;
        }
        if (item.vidIndex <= 0 && sid != null) {
            final String vid = item.getVid();
            ensureDetail(sid, detail -> {
                if (detail == null || !vid.equals(currentVid)) return;
                for (int i = 0; i < detail.getEpisodes().size(); i++) {
                    if (vid.equals(detail.getEpisodes().get(i).getVideoId())) {
                        feedEpIndex = i + 1;
                        currentEpisodeIndex = feedEpIndex;
                        break;
                    }
                }
            });
        }
        // 高光片段流内裁剪（他 2026-10-31 拍板并进 1.4.66）：高光
        // 条目起播跳到片段头（playVid 的 seekMs 通道），片段尾由
        // tick 的到点守卫走既有完播路；判定与完播落点同源
        // （DramaItem.isHighlightFragment 原式）。非高光条目恒 0，
        // D3「流内从头播被推集」口径不变。
        long hlStart = item.isHighlightFragment() && item.highlightStartMs > 0
                ? item.highlightStartMs : 0;
        playVid(item.getVid(), item.contentType, hlStart);
    }

    private String feedKeyOf(DramaItem item) {
        return item == null ? null : item.getSeriesId() + "/" + item.getVid();
    }

    /**
     * 起播失败统一出口：转圈收起、失败钮点亮。首页流（MODE_FEED）额外
     * 把当前条记账并收起封面——播不了的条在流里只留失败态（黑底＋
     * 失败钮），不许拿封面＋剧名当内容杵着；不自动跳条，用户一划就
     * 走、点失败钮可重试。其余模式维持原呈现（封面留作过渡底）。
     */
    private void showPlayFailure() {
        cancelSpinDelay();
        hideFreeze();
        FeedAdapter.Holder h = currentHolder();
        if (h != null) {
            h.spin.setVisibility(View.GONE);
            h.fail.setVisibility(View.VISIBLE);
        }
        if (mode != MODE_FEED) return;
        String key = feedKeyOf(currentIndex >= 0 && currentIndex < items.size()
                ? items.get(currentIndex) : null);
        if (key != null) feedFailedKeys.add(key);
        if (h != null) h.cover.setVisibility(View.GONE);
    }

    /** 首页流起播看门狗：起播后超时仍未出首帧，按失败呈现收口。 */
    private void armFeedWatchdog(String vid) {
        if (feedWatchdog != null) main.removeCallbacks(feedWatchdog);
        feedWatchdog = () -> {
            if (mode != MODE_FEED || vid == null || !vid.equals(currentVid)
                    || firstFrameForVid) return;
            if (player != null && player.isPlaying()) return;
            showPlayFailure();
        };
        main.postDelayed(feedWatchdog, FEED_WATCHDOG_MS);
    }

    /**
     * 承接落点：领池中引擎续播当前 vid。播放器里正播的就是这一
     * 集——不 stop、不重取链、不重挂媒体项，只把本页的记账
     * （集序/当前 vid/首帧旗/弹幕/条）对上。领不到（池被别人动
     * 过）退回常路 playEpisode。
     */
    private void adoptHandoff(int index) {
        DramaPlayerPool.Bag bag = inboundBag;
        inboundBag = null;
        ExoPlayerEngine taken = bag == null ? null : DramaPlayerPool.takeEngine();
        if (taken == null || bag == null) {
            entrySeekEp = -1;
            entrySeekMs = -1;
            playEpisode(index + 1, false);
            return;
        }
        engine = taken;
        player = engine.getPlayer();
        player.addListener(playerListener);
        playerView.setPlayer(player);
        tookHandoff = true;
        if (player.getCurrentMediaItem() == null) {
            // 泊出那一刻，流页这集的解链还没落（起播转圈中、甚至
            // 已经失败）就点了进剧：泊来的引擎是空的、没装媒体。
            // 照旧领法只会对着空引擎置真首帧旗——没人再给它装
            // 媒体，转圈无人收（看门狗只管 MODE_FEED）、失败无人
            // 报，播放页整页空转到用户退出。退回常路自解这一集：
            // 进页钉位（高光续进毫秒）不摘、由 playEpisode 消费；
            // 没钉位按断点存档续。泊出页那发迟到回包已在 playVid
            // 监听里按 engineParked 断了手，不会再来抢这台引擎。
            applyDanmakuVisibility();
            playEpisode(index + 1, true);
            return;
        }
        entrySeekEp = -1;
        entrySeekMs = -1;
        currentVid = bag.vid;
        currentEpisodeIndex = index + 1;
        firstFrameForVid = true;
        if (bag.speed > 0) player.setPlaybackSpeed(bag.speed);
        player.setPlayWhenReady(bag.playWhenReady);
        applyDanmakuVisibility();
        startDanmaku(currentVid);
        refreshStrip();
    }

    /** 退出播放页前把承接来的引擎泊回给流页领（先泊后 finish，与横屏 exitLand 同序）。 */
    private void parkBackToFeed() {
        DramaPlayerPool.Bag bag = new DramaPlayerPool.Bag();
        bag.seriesId = seriesIdArg;
        bag.vid = currentVid;
        bag.episodeIndex = currentEpisodeIndex;
        bag.positionMs = player.getCurrentPosition();
        bag.playWhenReady = player.getPlayWhenReady();
        bag.danmakuOn = HgStore.isDanmakuOn();
        bag.speed = player.getPlaybackParameters().speed;
        bag.seriesTitle = seriesTitleView == null ? null
                : seriesTitleView.getText().toString();
        DramaDetail d = detailCache.get(seriesIdArg);
        bag.contentType = d == null ? 0 : d.getContentType();
        saveProgressNow();
        player.removeListener(playerListener);
        playerView.setPlayer(null);
        DramaPlayerPool.park(bag, engine);
        engineParked = true;
    }

    private void playEpisode(int episodeIndex, boolean resume) {
        if (episodes.isEmpty()) return;
        int idx = Math.min(Math.max(1, episodeIndex), episodes.size());
        currentEpisodeIndex = idx;
        DramaDetail.Episode ep = episodes.get(idx - 1);
        long seek = 0;
        if (entrySeekEp == idx && entrySeekMs >= 0) {
            // 进页钉位（自动进剧高光支带入的流内当前毫秒）：盖过
            // 断点存档（官方 ForceStartTime 优先于本地存档），消费即清
            seek = entrySeekMs;
            entrySeekEp = -1;
            entrySeekMs = -1;
        } else if (resume && seriesIdArg != null) {
            HgStore.Progress p = HgStore.progressOf(seriesIdArg);
            if (p != null && p.episodeIndex == idx) seek = p.positionMs;
        }
        DramaDetail d = detailCache.get(seriesIdArg);
        playVid(ep.getVideoId(), d == null ? 0 : d.getContentType(), seek);
        refreshStrip();
    }

    /** 播放页顶栏「← 第N集」与选集条文案随集刷新（定稿形态）。 */
    private void updateSeriesChrome() {
        if (mode != MODE_SERIES) return;
        seriesTitleView.setText("第" + currentEpisodeIndex + "集");
        if (epBarText != null) {
            DramaDetail d = detailCache.get(seriesIdArg);
            boolean finished = d != null && d.getSeriesStatus() != null && d.getSeriesStatus() == 1;
            int total = !episodes.isEmpty() ? episodes.size()
                    : d == null ? 0 : d.getEpisodeCount();
            epBarText.setText(HgPanelLogic.episodeBarText(finished, total));
        }
    }

    public void playEpisodeFromSheet(int episodeIndex) {
        if (mode == MODE_SERIES) {
            // 逐集分页（D4）：跳集＝滑到那一集的页，settle 落位后
            // 从头播；已在该页则直接从头重播
            int target = episodeIndex - 1;
            if (target == currentIndex) {
                playEpisode(episodeIndex, false);
            } else {
                sheetJumpEp = episodeIndex;
                pager.scrollToPosition(target);
            }
        } else {
            // 频道流：当前条的选集——取详情后在本页切到该集播
            String sid = currentSeriesId;
            if (sid == null) return;
            ensureDetail(sid, detail -> {
                if (detail == null || detail.getEpisodes().size() < episodeIndex) return;
                episodes = detail.getEpisodes();
                currentEpisodeIndex = episodeIndex;
                playVid(detail.getEpisodes().get(episodeIndex - 1).getVideoId(),
                        detail.getContentType(), 0);
            });
        }
    }

    private interface DetailCb {
        void onDetail(DramaDetail detail);
    }

    private void ensureDetail(String seriesId, DetailCb cb) {
        DramaDetail cached = detailCache.get(seriesId);
        if (cached != null) {
            cb.onDetail(cached);
            return;
        }
        DramaClient.fetchDetail(seriesId, new HgCallback<DramaDetail>() {
            @Override
            public void onSuccess(DramaDetail detail) {
                runOnUiThread(() -> {
                    detailCache.put(seriesId, detail);
                    cb.onDetail(detail);
                });
            }

            @Override
            public void onError(int code, String msg) {
                runOnUiThread(() -> cb.onDetail(null));
            }
        });
    }

    private void ensurePlayer() {
        if (player != null) return;
        int decode = PlayerSetting.isVideoPrefer(PlayerSetting.EXO) ? PlayerEngine.HARD : PlayerEngine.SOFT;
        engine = new ExoPlayerEngine(decode, new Player.Listener() {
        });
        player = engine.getPlayer();
        player.addListener(playerListener);
        playerView.setPlayer(player);
        player.setPlaybackSpeed(HgStore.getSpeed());
    }

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_READY) {
                cancelSpinDelay();
                FeedAdapter.Holder h = currentHolder();
                if (h != null) h.spin.setVisibility(View.GONE);
            } else if (state == Player.STATE_ENDED) {
                onPlayEnded();
            }
        }

        // 播时常亮、停时放行：与主播放器 PlaybackActivity 同口径。
        // 本页自建播放页、没走 PlaybackActivity，此前全页没人设过
        // 这面旗，播着也会按系统超时息屏（他 2026-10-31 真机报）。
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            if (isPlaying) {
                getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else if (player == null
                    || player.getPlaybackState() != Player.STATE_BUFFERING) {
                getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }

        @Override
        public void onRenderedFirstFrame() {
            cancelSpinDelay();
            hideFreeze();
            FeedAdapter.Holder h = currentHolder();
            if (h != null) {
                h.cover.setVisibility(View.GONE);
                h.spin.setVisibility(View.GONE);
                // 迟到的首帧也要收口：看门狗先报的失败态在此撤销
                h.fail.setVisibility(View.GONE);
            }
            firstFrameForVid = true;
        }

        /**
         * #7：FIT/ZOOM 选支改认解码实测尺寸。此前只有 resolver 预报
         * 的模型宽高参与判定，预报与实流不一致时整支选反（裁了不该
         * 裁的、或留了不该留的边）；实测一到就以实测为准重选，并把
         * lastVideoW/H 更新为实测值（横屏资格判定同源）。
         */
        @Override
        public void onVideoSizeChanged(@NonNull androidx.media3.common.VideoSize size) {
            int w = size.width;
            int h = size.height;
            if (size.unappliedRotationDegrees == 90
                    || size.unappliedRotationDegrees == 270) {
                int t = w;
                w = h;
                h = t;
            }
            if (size.pixelWidthHeightRatio > 0f && size.pixelWidthHeightRatio != 1f) {
                w = Math.round(w * size.pixelWidthHeightRatio);
            }
            if (w <= 0 || h <= 0) return;
            lastVideoW = w;
            lastVideoH = h;
            applyResizeModeFor(w, h);
            refreshFullPillVisibility();
        }

        @Override
        public void onPlayerError(@NonNull androidx.media3.common.PlaybackException error) {
            android.util.Log.w("HongguoFeed", "player error vid=" + currentVid
                    + " code=" + error.errorCode);
            showPlayFailure();
        }
    };

    /**
     * 短片内容判定：短片页（MODE_VIDEO）、单条（MODE_UGC）整页都是
     * 短片；首页流（MODE_FEED）看当前条目类型，合集/短片条目算短片、
     * 正剧条目不算。短剧播放页（MODE_SERIES）播的是剧集，恒不算。
     * 短片按原始比例 fit 播；正剧的竖屏满屏、横版 fit 是定稿，不走本判定。
     */
    /**
     * 画面适配选支（#7）：短片一律按原始比例 fit（留黑边可以，不许
     * 裁满全屏）；正剧维持定稿——横版 fit 居中黑边、竖版 ZOOM 满屏。
     * 无任何拉伸模式。onReady（预报尺寸）与 onVideoSizeChanged
     *（实测尺寸）共用本口，实测到后以实测重选为准。
     */
    private void applyResizeModeFor(int w, int h) {
        if (playerView == null) return;
        if (w <= 0 || h <= 0) {
            // 预报缺失（逐帧 D14 的陈旧判支残口）：fplay 直取的集不
            // 落 DramaCache（A-12），这类 vid 走 Resolver 缓存直出路
            // 时预报恒 0——旧写法直接返回、上一条的 resizeMode 原样
            // 留着，新条尺寸判支实际用的是上一条的结论（上一条竖版
            // ZOOM 遇新条横版即先按裁切渲）。未知时先落 FIT（不裁、
            // 安全侧），实测尺寸一到由 onVideoSizeChanged 按定稿重选。
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            return;
        }
        playerView.setResizeMode(playingClipContent() || w >= h
                ? AspectRatioFrameLayout.RESIZE_MODE_FIT
                : AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
    }

    private boolean playingClipContent() {
        if (mode == MODE_VIDEO || mode == MODE_UGC) return true;
        if (mode == MODE_SERIES) return false;
        DramaItem item = currentIndex >= 0 && currentIndex < items.size()
                ? items.get(currentIndex) : null;
        return item != null && item.kind != DramaItemKind.SERIES;
    }

    /**
     * 切集转圈闸：压下当前页转圈，350ms 后若这一集仍未出首帧
     * 才亮。mountPlayerView 在 settle 同栈里已把转圈设 VISIBLE，
     * 本法在同一调用栈内压下，不会真画出一帧。冷进页（非切集）
     * 不走本闸、转圈照旧即时亮。
     */
    private void gateSpinForSwitch(String vid) {
        cancelSpinDelay();
        FeedAdapter.Holder h = currentHolder();
        if (h != null) h.spin.setVisibility(View.GONE);
        spinDelayRunnable = () -> {
            spinDelayRunnable = null;
            if (!vid.equals(currentVid) || firstFrameForVid) return;
            FeedAdapter.Holder hh = currentHolder();
            if (hh != null) hh.spin.setVisibility(View.VISIBLE);
        };
        main.postDelayed(spinDelayRunnable, 350);
    }

    private void cancelSpinDelay() {
        if (spinDelayRunnable != null) {
            main.removeCallbacks(spinDelayRunnable);
            spinDelayRunnable = null;
        }
    }

    /**
     * 发起一次抓帧：把播放面当前这一帧（上一集的末帧）复制下来，
     * 到手后暂存并尝试挂到目标页。代次对位——连切只认最后一发；
     * 新集首帧已先到（预取集快切）则弃图不挂。无论成败，落定都
     * 过 onFreezeSettled 记一笔——settle 等帧续程靠它接力。
     */
    private void initiateFreezeCapture(int targetIndex) {
        if (playerView == null || player == null) return;
        freezeForIndex = targetIndex;
        final int seq = ++freezeSeq;
        HgFrameCapture.capture(playerView, main, bmp -> {
            if (bmp != null) {
                if (seq != freezeSeq || firstFrameForVid) {
                    bmp.recycle();
                } else {
                    clearFreezeBitmap();
                    freezeBitmap = bmp;
                    attachFreeze();
                }
            }
            onFreezeSettled(seq);
        });
    }

    /**
     * settle 等帧登记：抓帧落定（onFreezeSettled）或超时先到为准，
     * 到了才走 proceedSettle 搬面起播。TextureView 是同步回调、
     * 登记前就已落定（settledSeq 已对上）——直接接力不空等。
     * 连切时新一发 settle 会顶掉旧登记（撤旧超时、awaitSeq 换新），
     * 旧续程永不执行；页面销毁由 hideFreeze 顶代次＋onDestroy
     * 撤超时双保险，续程的代次/下标双守卫是最后一道。
     */
    private void awaitFreezeThenSettle(int index) {
        if (settleAwaitTimeout != null) {
            main.removeCallbacks(settleAwaitTimeout);
            settleAwaitTimeout = null;
        }
        final int seq = freezeSeq;
        settleAwaitSeq = seq;
        settleAwaitIndex = index;
        if (freezeSettledSeq == seq) {
            onFreezeSettled(seq);
            return;
        }
        settleAwaitTimeout = () -> {
            if (settleAwaitSeq != seq || seq != freezeSeq) return;
            settleAwaitSeq = 0;
            settleAwaitTimeout = null;
            // 超时：作废这发在途抓帧（迟到回调只回收不挂图），
            // 按无冻结常路把切集走完，不许拖住。
            freezeSeq++;
            android.util.Log.i(HgFrameCapture.TAG,
                    "capture fail: timeout seq=" + seq + " (proceed without freeze)");
            if (settleAwaitIndex == currentIndex) proceedSettle(settleAwaitIndex);
        };
        main.postDelayed(settleAwaitTimeout, FREEZE_WAIT_MS);
    }

    /** 一发抓帧落定：若 settle 正等它，撤超时并接力后半程。 */
    private void onFreezeSettled(int seq) {
        freezeSettledSeq = seq;
        if (settleAwaitSeq != seq) return;
        settleAwaitSeq = 0;
        if (settleAwaitTimeout != null) {
            main.removeCallbacks(settleAwaitTimeout);
            settleAwaitTimeout = null;
        }
        if (settleAwaitIndex == currentIndex) proceedSettle(settleAwaitIndex);
    }

    /** 把暂存的冻结图挂到目标页的画面层（playerView 之上）。 */
    private void attachFreeze() {
        if (freezeBitmap == null || freezeForIndex != currentIndex) return;
        FeedAdapter.Holder h = currentHolder();
        if (h == null) return;
        if (freezeView == null) {
            freezeView = new ImageView(this);
            // 帧图就是播放面原尺寸的像素复刻，FIT_XY 逐点对位
            freezeView.setScaleType(ImageView.ScaleType.FIT_XY);
        }
        if (freezeView.getParent() != null) {
            ((ViewGroup) freezeView.getParent()).removeView(freezeView);
        }
        freezeView.setImageBitmap(freezeBitmap);
        h.surface.addView(freezeView, match());
        freezeView.setVisibility(View.VISIBLE);
    }

    private void clearFreezeBitmap() {
        if (freezeView != null) {
            freezeView.setImageBitmap(null);
            if (freezeView.getParent() != null) {
                ((ViewGroup) freezeView.getParent()).removeView(freezeView);
            }
        }
        if (freezeBitmap != null) {
            freezeBitmap.recycle();
            freezeBitmap = null;
        }
        freezeForIndex = -1;
    }

    /** 新集首帧已接上（或失败收口）：冻结帧撤下回收、在途抓帧作废。 */
    private void hideFreeze() {
        freezeSeq++;
        freezeArmed = false;
        clearFreezeBitmap();
    }

    private void playVid(String vid, int contentType, long seekMs) {
        if (vid == null || vid.isEmpty()) {
            showPlayFailure();
            return;
        }
        if (mode == MODE_SERIES && firstFrameForVid && player != null) {
            // 末帧冻结抓帧点：settle 路已在搬面之前发起（freezeArmed
            // 只消费不重发）；同页直切（选集同集重播等）在此补发。
            // 两路都必须赶在下面的 player.stop() 之前。
            if (freezeArmed) {
                freezeArmed = false;
            } else {
                initiateFreezeCapture(currentIndex);
            }
        }
        ensurePlayer();
        // 切集顺滑（转圈延迟）：播放页内切集时，若是从正在播的
        // 一集切走（firstFrameForVid 尚为真），先把转圈压下——
        // 已预取的集首帧快到，转圈不该闪出来；350ms 还没出首帧
        // 才亮转圈（按 vid 对位，连切只认最后一发）。
        boolean switching = mode == MODE_SERIES && firstFrameForVid;
        currentVid = vid;
        scrubbedCurrent = false;
        firstFrameForVid = false;
        if (switching) gateSpinForSwitch(vid);
        if (mode == MODE_SERIES) attachFreeze();
        if (mode == MODE_FEED) armFeedWatchdog(vid);
        prefetchedNext = false;
        danmakuVid = null;
        player.stop();
        DramaResolver.Listener playListener = new DramaResolver.Listener() {
            @Override
            public void onReady(String videoId, String localUrl, int w, int h) {
                if (!videoId.equals(currentVid) || player == null) return;
                // 引擎已泊出（承接进剧/进横屏）：这台引擎归领走方，
                // 本页迟到回包不许再碰它——媒体由领走方自解
                // （adoptHandoff 的空引擎退路），成败也归领走方报。
                if (engineParked) return;
                lastVideoW = w;
                lastVideoH = h;
                refreshFullPillVisibility();
                player.setMediaItem(MediaItem.fromUri(localUrl));
                // 选支先按预报尺寸落，实测尺寸一到由 onVideoSizeChanged
                // 以实测重选（#7）。
                applyResizeModeFor(w, h);
                player.prepare();
                if (seekMs > 0) player.seekTo(seekMs);
                // 本页被别的页盖住时回包落点：媒体备好、起播等回
                // 页（onResume 按 resumeOnReturn 续），不许在后台
                // 出声——旧写法无条件 setPlayWhenReady(true)，解链
                // 慢一拍就成了盖着详情/作者页照样响。
                if (activityResumed) {
                    player.setPlayWhenReady(true);
                } else {
                    resumeOnReturn = true;
                }
                startDanmaku(videoId);
                maybePrefetchSeries(videoId);
            }

            @Override
            public void onFailed(String videoId, String code, String message) {
                if (!videoId.equals(currentVid)) return;
                // 引擎已泊出：失败归领走方自解时报，本页不许把已
                // 让出的条目标成失败（回流后会顶着失败态）。
                if (engineParked) return;
                // 失败码留痕（E1 取模型/E2 无档/E3 链空/E4 DEK/E5 注册）：
                // 失败态 UI 不分码，下次真机复现靠这行定段（账 triage2 二）。
                android.util.Log.w("HongguoFeed", "resolve failed vid=" + videoId
                        + " code=" + code + " msg=" + message);
                showPlayFailure();
            }
        };
        resolveWithWindow(vid, contentType, playListener);
    }

    /**
     * 系列起播取链（2026-10-31 改，账 hongguo-play-once-verdict）：播放
     * 取链现状是新脸第一发之后即回空——原链「当前集先单发解链＋全集
     * 预取再逐批单发」把能成的那一发只用在当前集一集上，后面全灭。
     * 改成：当前集模型未落缓存时，先用一发 multi 把「当前集起 20 集」
     * 窗口一次取回落缓存，再走既有 resolve（当前集命中缓存出链，
     * 后续窗内切集全命中）。窗口按（剧、起始下标）去重，窗内不重发；
     * 滚到窗外再开新窗。非系列模式/已缓存/找不到集位时直走 resolve。
     */
    private void resolveWithWindow(String vid, int contentType,
                                   DramaResolver.Listener listener) {
        if (mode != MODE_SERIES || seriesIdArg == null || episodes.isEmpty()) {
            resolver.resolve(vid, contentType, listener);
            return;
        }
        DramaVideoModel cached = DramaCache.get(vid);
        if (cached != null && cached.firstPlayableEntry() != null) {
            resolver.resolve(vid, contentType, listener);
            return;
        }
        int idx = -1;
        for (int i = 0; i < episodes.size(); i++) {
            if (vid.equals(episodes.get(i).getVideoId())) {
                idx = i;
                break;
            }
        }
        if (idx < 0 || (seriesIdArg.equals(windowSeriesId)
                && idx >= windowStart && idx < windowStart + 20)) {
            resolver.resolve(vid, contentType, listener);
            return;
        }
        windowSeriesId = seriesIdArg;
        windowStart = idx;
        DramaDetail d = detailCache.get(seriesIdArg);
        int ct = d == null ? contentType : d.getContentType();
        List<DramaClient.ModelRequest> reqs = new ArrayList<>();
        for (int i = idx; i < Math.min(idx + 20, episodes.size()); i++) {
            String ev = episodes.get(i).getVideoId();
            if (ev != null) reqs.add(new DramaClient.ModelRequest(ev, ct));
        }
        if (reqs.isEmpty()) {
            resolver.resolve(vid, contentType, listener);
            return;
        }
        DramaClient.fetchWindowModels(reqs, new HgCallback<Map<String, DramaVideoModel>>() {
            @Override
            public void onSuccess(Map<String, DramaVideoModel> r) {
                resolver.resolve(vid, contentType, listener);
            }

            @Override
            public void onError(int c, String m) {
                resolver.resolve(vid, contentType, listener);
            }
        });
    }

    /**
     * 进播放页预取（#19 恢复，深度＝当前集之后两集）：只在播放页
     *（MODE_SERIES）、且当前集解链成功后才开启——当前集模型此时
     * 已落缓存，预取不抢当前集；切集时重复调用由 DramaSeriesPrefetch
     * 按窗口键滚动换窗。视频页（短片/合集）不走本路。
     */
    private void maybePrefetchSeries(String vid) {
        // #19 恢复（2026-10-31 他亲定）：播放已通、取链形势恢复，
        // 按原通路恢复预取；深度不再是全集——只备当前集之后两集，
        // 窗口口径在 DramaSeriesPrefetch.start 里收（窗口随切集滚动，
        // 已缓存的集由 fetchModels 发批前自查、不重复发网）。预取只
        // 在后台下数据落缓存、不占屏，与 #8 的起播不亮封面不冲突。
        if (mode != MODE_SERIES || seriesIdArg == null) return;
        DramaDetail d = detailCache.get(seriesIdArg);
        if (d == null || episodes.isEmpty()) return;
        DramaSeriesPrefetch.start(seriesIdArg, episodes, d.getContentType(), vid);
    }

    private void onPlayEnded() {
        saveProgressNow();
        // 定时关闭「播完本剧」：本剧最后一集播完即停，不连播不进剧
        if (HgStore.getTimerMode() == HgPanelLogic.TIMER_SERIES_END
                && mode == MODE_SERIES && currentEpisodeIndex >= episodes.size()) {
            HgStore.clearTimer();
            Toast.makeText(this, "定时关闭：已播完本剧", Toast.LENGTH_SHORT).show();
            return;
        }
        // 播完分流（互动账 §2 官方 T1/J8 判序的咱家落位）：
        // completeMode 是服务端逐页下发值、咱家取数面未收，按
        // 条目类型定默认分支；isAutoNext 在流里的门控已摘除——
        // 连集是播放页内置常开行为，流里不许拿它滑下一条。
        if (mode == MODE_SERIES) {
            // 播放页连集（D4 分页形态）：滑到下一集页，settle
            // 落位从头播；最后一集播完停住（定时「播完本剧」
            // 已在上方拦截）
            if (currentIndex + 1 < items.size()) {
                autoNextEp = currentIndex + 2;
                pager.smoothScrollToPosition(currentIndex + 1);
            }
            return;
        }
        if (mode == MODE_VIDEO) {
            if (videoAlbumId != null) {
                // 合集完播：停在本页、不跳页不滑走（官方合集完播门）
                if (player != null) player.setPlayWhenReady(false);
            } else {
                // 单条短片页：循环重播
                replayCurrent();
            }
            return;
        }
        if (mode == MODE_UGC) {
            // UGC 带合集条 → 循环重播（官方 T1 第 5 路）
            replayCurrent();
            return;
        }
        // MODE_FEED
        DramaItem item = currentIndex >= 0 && currentIndex < items.size()
                ? items.get(currentIndex) : null;
        if (item == null) return;
        if (scrubbedCurrent || loadingPage) {
            // 拖过进度／流刷新（续页）中：循环重播（官方 T1 第 2/4 路）
            replayCurrent();
            return;
        }
        switch (item.kind) {
            case SERIES:
                // D1（按官方包选集规则，互动账 §五.1；他先前「从第一
                // 集」口径已被包内逐行实证覆盖、22:36 他拍板按包规则
                // 改）：落点按被推集 vidIndex 与 isNext 分——isNext
                // 为真（正片集素材及其余条目）落下一集从头、已是末集
                // 回落同一集；isNext 为假（高光/片段类素材，判定见
                // DramaItem.isHighlightFragment 原式）落同一集、带流
                // 里当前毫秒续看。不滑下一条流条目、不在流里连集。
                if (item.getSeriesId() != null) {
                    int total = item.getEpisodeCount();
                    int vi = item.vidIndex;
                    // 被推集集序原值缺失时以 D3 记账集序兜底
                    // （feedEpIndex 已按 vid 在集表定位过）
                    int pushed = vi > 0 ? vi : Math.max(1, feedEpIndex);
                    if (vi > 0 && total > 0 && vi >= total) {
                        // 官方 P8/Q8：被推集正是末集时弹（官方两句
                        // 按完结态分：完结＝最后一集、连载＝最新一集）
                        Toast.makeText(this, item.isFinished()
                                        ? "当前已是最后一集" : "当前已是最新一集",
                                Toast.LENGTH_SHORT).show();
                    }
                    if (item.isHighlightFragment()) {
                        long ms = player != null
                                ? Math.max(0, player.getCurrentPosition()) : 0;
                        // 高光片段同集续进：走承接口（同 vid 泊领、
                        // 位置天然连续），领不到才按 startMs 钉位。
                        openSeries(item, pushed, ms);
                    } else if (total > 0 && pushed >= total) {
                        startSeries(this, item.getSeriesId(), item.getTitle(), pushed);
                    } else {
                        startSeries(this, item.getSeriesId(), item.getTitle(), pushed + 1);
                    }
                }
                break;
            case ALBUM:
                // 合集完播：停在当前条、不跳页不滑走
                if (player != null) player.setPlayWhenReady(false);
                break;
            default:
                // UGC 短片（带合集条与否同路）：循环重播——流里
                // 没有它的下一集可连，进剧路只属于正剧
                replayCurrent();
                break;
        }
    }

    /** 循环重播当前条（官方播完判序的循环路各分支共用）。 */
    private void replayCurrent() {
        if (player == null) return;
        // 高光条目的循环路（流内续页中等官方循环支）：回片段头
        // 重播、到点守卫重新武装，不许从整集 0 秒重播。
        long start = 0;
        if (mode == MODE_FEED) {
            DramaItem hl = currentIndex >= 0 && currentIndex < items.size()
                    ? items.get(currentIndex) : null;
            if (hl != null && hl.isHighlightFragment()) {
                start = Math.max(0, hl.highlightStartMs);
                highlightEndFiredVid = null;
            }
        }
        player.seekTo(start);
        player.setPlayWhenReady(true);
    }

    private int prefetchIndex = -1;

    private final Runnable prefetchRunnable = () -> {
        if (prefetchIndex == currentIndex) prefetchNeighbors(prefetchIndex);
    };

    /**
     * 邻条备链排程：停稳后延迟 2.5 秒才发（快刷连划自动作废旧排程），
     * 且走推测口——备链与正片取链同秒对撞、又替正片把自家面打进
     * 冷却，是「第一条能播、后面全灭」的两段成因（账 triage2 五）。
     */
    private void schedulePrefetch(int index) {
        prefetchIndex = index;
        main.removeCallbacks(prefetchRunnable);
        main.postDelayed(prefetchRunnable, 2500);
    }

    private void prefetchNeighbors(int index) {
        // 2026-10-31 停用（账 hongguo-play-once-verdict）：刷流停住预取
        // 后三条播放模型＝每停一次多烧一发取链，当前形势下会把用户真
        // 要点播的那一发先烧掉。只停播放模型预取，条目本身照常滑动
        // 起播（起播走 playVid 正链）。后三条口径（他 2026-10-30 议定）
        // 待取链形势恢复常态后再恢复本方法。
    }

    /** D7 历史进度线落位（FEED 剧条下缘那条）：按存档比例设填充宽＋点位。 */
    private void applyFeedLine(float pct) {
        if (progressWrap == null || progressFill == null) return;
        progressWrap.post(() -> {
            if (progressFill == null) return;
            int w = progressWrap.getWidth();
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) progressFill.getLayoutParams();
            lp.width = (int) (w * Math.max(0f, Math.min(1f, pct)));
            progressFill.setLayoutParams(lp);
            if (progressDot != null) {
                FrameLayout.LayoutParams dlp = (FrameLayout.LayoutParams) progressDot.getLayoutParams();
                dlp.leftMargin = Math.max(0, lp.width - dp(4));
                progressDot.setLayoutParams(dlp);
            }
        });
    }

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            main.postDelayed(this, 500);
            tick();
        }
    };

    private void tick() {
        if (player == null || currentVid == null) return;
        // 官方进度线颜色随播放态（他 2026-10-31 发官方暂停图点名）：
        // 播放中白、暂停变黄橙（官方图采样 #E67F32）；圆点恒白。
        if (progressFill != null) {
            int want = player.isPlaying() ? 0xEBFFFFFF : 0xFFE67F32;
            if (progressFillColor != want) {
                progressFillColor = want;
                progressFill.setBackgroundColor(want);
            }
        }
        // 定时关闭到点：停播并清档（只报一次）
        long deadline = HgStore.getTimerDeadline();
        if (deadline > 0 && System.currentTimeMillis() >= deadline) {
            HgStore.clearTimer();
            if (player.isPlaying()) player.pause();
            Toast.makeText(this, "定时关闭：时间到", Toast.LENGTH_SHORT).show();
        }
        long pos = player.getCurrentPosition();
        // 高光片段到点守卫（FEED 高光条目）：播到片段尾即按完播
        // 收口，走 onPlayEnded 既有分流（高光支落同一集、带当场
        // 毫秒续看——当场毫秒即片段终点，与官方续看位同义）；
        // 按 vid 单次触发，换条 vid 变了自然重新武装。
        if (mode == MODE_FEED && player.isPlaying()
                && currentVid != null && !currentVid.equals(highlightEndFiredVid)) {
            DramaItem hl = currentIndex >= 0 && currentIndex < items.size()
                    ? items.get(currentIndex) : null;
            if (hl != null && hl.isHighlightFragment()
                    && hl.highlightEndMs > 0 && pos >= hl.highlightEndMs) {
                highlightEndFiredVid = currentVid;
                onPlayEnded();
                return;
            }
        }
        long dur = player.getDuration();
        if (dur > 0) {
            // 播放页/视频页进度行两侧时间签同步（FEED/UGC 无此行、字段为 null）
            if (timeCurView != null) timeCurView.setText(fmtTime(pos));
            if (timeDurView != null) timeDurView.setText(fmtTime(dur));
            if (mode == MODE_FEED) {
                // D7：流内线只画本条历史进度（存档位÷本集时长），
                // 时长到手后定版一次，之后不跟实时播放走——底部
                // 全局实时线是已摘的自创形态
                if (!feedLineDone && feedLineIndex == currentIndex) {
                    feedLineDone = true;
                    float pct = 0f;
                    HgStore.Progress hp = currentSeriesId == null ? null
                            : HgStore.progressOf(currentSeriesId);
                    if (hp != null && hp.episodeIndex == feedEpIndex && hp.positionMs > 0) {
                        pct = Math.min(1f, hp.positionMs * 1f / dur);
                    }
                    applyFeedLine(pct);
                }
            } else {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) progressFill.getLayoutParams();
                int w = progressWrap.getWidth();
                lp.width = (int) (w * Math.min(1f, pos * 1f / dur));
                progressFill.setLayoutParams(lp);
                if (progressDot != null) {
                    FrameLayout.LayoutParams dlp2 = (FrameLayout.LayoutParams) progressDot.getLayoutParams();
                    dlp2.leftMargin = Math.max(0, lp.width - dp(4));
                    progressDot.setLayoutParams(dlp2);
                }
            }
            // 播中尾段预取下一集（isPlaying＋剩余≤30s 或进度≥85%，单次记账）
            if (player.isPlaying() && !prefetchedNext
                    && (dur - pos <= 30_000 || pos * 1f / dur >= 0.85f)) {
                prefetchedNext = true;
                prefetchNextEpisode();
            }
        }
        long now = System.currentTimeMillis();
        if (now - lastSaveMs > 5000) {
            lastSaveMs = now;
            saveProgressNow();
        }
        if (cleanMode) shiftCleanBtn();
    }

    private void prefetchNextEpisode() {
        if (mode == MODE_SERIES) {
            if (currentEpisodeIndex < episodes.size()) {
                DramaDetail d = detailCache.get(seriesIdArg);
                resolver.warm(episodes.get(currentEpisodeIndex).getVideoId(),
                        d == null ? 0 : d.getContentType());
            }
        } else if (currentSeriesId != null) {
            ensureDetail(currentSeriesId, detail -> {
                if (detail == null) return;
                // 刷流的「下一集」按被推集集序算（D3 记账口径同源）
                int cur = mode == MODE_FEED ? feedEpIndex : currentEpisodeIndex;
                if (cur < detail.getEpisodes().size()) {
                    resolver.warm(detail.getEpisodes().get(cur).getVideoId(),
                            detail.getContentType());
                }
            });
        }
    }

    private void saveProgressNow() {
        if (player == null || currentSeriesId == null || currentVid == null) return;
        long pos = Math.max(0, player.getCurrentPosition());
        if (pos <= 0) return;
        DramaItem item = currentIndex >= 0 && currentIndex < items.size() ? items.get(currentIndex) : null;
        if (item != null) {
            HgStore.putMeta(currentSeriesId, item.getTitle(), item.getCoverUrl(),
                    item.getEpisodeCount());
            HgStore.putType(currentSeriesId, HgStore.typeCodeOf(item));
        }
        // 记账集序：播放页＝当前集；刷流＝被推集集序（D3，旧实现
        // 恒记第 1 集、把被推集进度记错集）；其余面维持当前集序
        int epForSave = mode == MODE_FEED ? feedEpIndex : currentEpisodeIndex;
        // #14：落盘全进口（他 2026-10-31 15:07 终裁「短片进历史」）——
        // 5241cb1c0 的分口（正剧/合集走正口、纯短片 UGC_CLIP 走 Quiet）
        // 就此取消：全内容走带历史索引的 saveProgress，看过的都进得去
        // 历史页。此前全走 Quiet 时索引恒空、历史页永远空白。
        HgStore.saveProgress(currentSeriesId, epForSave, pos);
    }

    // ---------- 手势/清屏 ----------

    private void onPageTap(View pageRoot) {
        if (cleanMode) return;
        if (player == null) return;
        if (player.isPlaying()) {
            player.pause();
            flashCenter(R.drawable.ic_drama_pause);
        } else {
            player.play();
            flashCenter(R.drawable.ic_drama_play);
        }
    }

    private void flashCenter(int icon) {
        centerFlash.setImageResource(icon);
        centerFlash.setVisibility(View.VISIBLE);
        centerFlash.setAlpha(1f);
        centerFlash.animate().alpha(0f).setDuration(450).withEndAction(() -> {
            centerFlash.setVisibility(View.GONE);
            centerFlash.setAlpha(1f);
        }).start();
    }

    private void onPageLongPress(boolean center) {
        // 长按只留中部更多面板一件（播放页规格、只 MODE_SERIES）：
        // 非中部的 2 倍速快进他 2026-10-31 亲令整支删除——普通触摸
        // 就会被长按定时器武装挂上、松不开，不调阈值、整支摘除。
        if (mode != MODE_SERIES) return;
        if (cleanMode) return;
        if (center) {
            showMorePanel();
        }
    }

    private void setCleanMode(boolean on) {
        cleanMode = on;
        int vis = on ? View.GONE : View.VISIBLE;
        topBar.setVisibility(mode == MODE_FEED ? vis : View.GONE);
        seriesBar.setVisibility(mode != MODE_FEED ? vis : View.GONE);
        if (bottomStack != null) bottomStack.setVisibility(vis);
        if (mode == MODE_UGC) progressWrap.setVisibility(vis);
        FeedAdapter.Holder h = currentHolder();
        if (h != null) h.chrome.setVisibility(vis);
        if (cleanBtn != null) cleanBtn.setAlpha(on ? 0.42f : 1f);
        applyDanmakuVisibility();
    }

    private int cleanShiftStep;

    private void shiftCleanBtn() {
        // 防烧屏：清屏态下钮位每 30 秒在 4 个近位间轮换（tick 500ms 计步）
        if (cleanBtn == null) return;
        if (++cleanShiftStep % 60 != 0) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) cleanBtn.getLayoutParams();
        int phase = (cleanShiftStep / 60) % 4;
        lp.rightMargin = dp(cleanBaseRight) + (phase == 1 ? dp(2) : phase == 3 ? -dp(2) : 0);
        lp.bottomMargin = cleanBaseBottom + (phase == 2 ? dp(2) : 0);
        cleanBtn.setLayoutParams(lp);
    }

    // ---------- 弹幕 ----------

    private androidx.media3.ui.danmaku.DanmakuConfig danmakuConfig() {
        int lines = HgStore.getDanmakuLines();
        return androidx.media3.ui.danmaku.DanmakuConfig.DEFAULT.buildUpon()
                .setTransparency(HgPanelLogic.danmakuTransparency(HgStore.getDanmakuOpacity()))
                .setScrollSpeedFactor(HgPanelLogic.danmakuSpeedFactor(HgStore.getDanmakuSpeedIndex()))
                .setTextScale(HgPanelLogic.danmakuFontScale(HgStore.getDanmakuFontIndex()))
                .setTextSizeSp(HgPanelLogic.DM_TEXT_SIZE_SP)
                .setDurationMs(HgPanelLogic.DM_DURATION_MS_PORTRAIT)
                .setMaxTopLines(lines)
                .setMaxBottomLines(lines)
                .setMaxScrollLines(lines)
                .build();
    }

    void applyDanmakuConfig() {
        if (playerView != null) {
            playerView.getDanmakuController().setConfig(danmakuConfig());
        }
    }

    private void applyDanmakuVisibility() {
        // 弹幕显示层＋发弹幕入口同闸：开关/清屏落到 PlayerView 弹幕
        // 层，入口显隐跟开关走（#21 乙，官方门同口径）。
        boolean on = HgStore.isDanmakuOn() && (!cleanMode || HgStore.getDanmakuShowInClean());
        if (playerView != null) playerView.setDanmakuEnabled(on);
        FeedAdapter.Holder h = currentHolder();
        if (h != null && h.dmSend != null) {
            DramaItem item = currentIndex >= 0 && currentIndex < items.size()
                    ? items.get(currentIndex) : null;
            h.dmSend.setVisibility(danmakuEntranceVisible(item) ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * 发弹幕入口显隐（#21 乙，照官方多重门收）：弹幕开着、且条目是
     * 有剧归属的正剧/合集（短片无弹幕面）、在刷流与播放页两形态显。
     */
    private boolean danmakuEntranceVisible(DramaItem item) {
        if (item == null || item.kind == DramaItemKind.UGC_CLIP) return false;
        if (mode != MODE_SERIES && mode != MODE_FEED) return false;
        return HgStore.isDanmakuOn();
    }

    /**
     * 点发弹幕入口（#21 乙 + 账号终定口径）：未登录一律先跳登录页
     *（官方原行为、与点赞/收藏同门），登录后才开编辑层。
     */
    private void onDanmakuSendTap() {
        if (!com.fongmi.android.tv.hongguo.api.HgAccountStore.isLoggedIn()) {
            HongguoLoginActivity.start(this);
            return;
        }
        showDanmakuSendDialog();
    }

    /** 发弹幕编辑层：占位文案照官方资源（发条友善的弹幕吧）；发表
     * 走 DramaDanmakuSender 定版载荷，成功把自己这条并入当前弹幕
     * 列表回显，失败留层重试（A25 旧行为同口径）。 */
    private void showDanmakuSendDialog() {
        if (currentVid == null || currentSeriesId == null || player == null) return;
        final String vid = currentVid;
        final String sid = currentSeriesId;
        final long offset = Math.max(0, player.getCurrentPosition());
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("发条友善的弹幕吧");
        input.setTextColor(0xFF1B1B1F);
        input.setHintTextColor(0xFF8A8A93);
        input.setTextSize(14.5f);
        input.setSingleLine(true);
        androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("发弹幕")
                        .setView(input)
                        .setNegativeButton("取消", null)
                        .setPositiveButton("发表", null)
                        .create();
        dialog.setOnShowListener(d -> {
            android.widget.Button ok = dialog.getButton(
                    androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            ok.setOnClickListener(v -> {
                String text = input.getText() == null ? ""
                        : input.getText().toString().trim();
                if (text.isEmpty()) {
                    input.setError("先写点什么");
                    return;
                }
                ok.setEnabled(false);
                com.fongmi.android.tv.hongguo.drama.DramaDanmakuSender.publish(
                        sid, vid, text, offset, new HgCallback<Void>() {
                            @Override
                            public void onSuccess(Void r) {
                                runOnUiThread(() -> {
                                    dialog.dismiss();
                                    if (vid.equals(currentVid) && playerView != null) {
                                        danmakuItems.add(new androidx.media3.ui.danmaku.Danmaku(
                                                text, offset,
                                                androidx.media3.ui.danmaku.Danmaku.TYPE_SCROLL,
                                                Color.WHITE, 0f, 0, null, 0));
                                        playerView.getDanmakuController()
                                                .setItems(new ArrayList<>(danmakuItems));
                                    }
                                    Toast.makeText(HongguoFeedActivity.this,
                                            "已发送", Toast.LENGTH_SHORT).show();
                                });
                            }

                            @Override
                            public void onError(int c, String m) {
                                runOnUiThread(() -> {
                                    ok.setEnabled(true);
                                    Toast.makeText(HongguoFeedActivity.this,
                                            "没发出去，再试试", Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
            });
        });
        dialog.show();
    }

    private void startDanmaku(String vid) {
        danmakuVid = vid;
        danmakuItems.clear();
        if (playerView != null) playerView.getDanmakuController().clearItems();
        if (!HgStore.isDanmakuOn() || currentSeriesId == null) return;
        final String sid = currentSeriesId;
        safeExecute(() -> {
            // 时长传缓存模型的 video_duration 真值（A-20）：终止线与
            // playlet_item_duration 自此有据；模型不在手仍传 0=未知。
            HongguoDanmakuApi.fetchToFile(sid, vid, DramaCache.durationMs(vid), item -> {
                if (!vid.equals(danmakuVid)) return;
                loadDanmakuFile(vid, item);
            });
        });
    }

    private void loadDanmakuFile(String vid, Danmaku item) {
        if (item == null || item.getUrl() == null) return;
        safeExecute(() -> {
            List<androidx.media3.ui.danmaku.Danmaku> parsed = parseDanmakuXml(item.getRealUrl());
            if (parsed == null) return;
            runOnUiThread(() -> {
                if (!vid.equals(danmakuVid) || playerView == null) return;
                danmakuItems.clear();
                danmakuItems.addAll(parsed);
                playerView.getDanmakuController().setItems(new ArrayList<>(danmakuItems));
            });
        });
    }

    /** Bilibili 形弹幕 XML（HongguoDanmakuApi 落盘格式）→ media3 条目（全滚动白字）。 */
    static List<androidx.media3.ui.danmaku.Danmaku> parseDanmakuXml(String fileUrl) {
        try {
            String path = fileUrl.startsWith("file:") ? android.net.Uri.parse(fileUrl).getPath() : fileUrl;
            java.io.File f = new java.io.File(path);
            if (!f.exists()) return null;
            byte[] bytes = new byte[(int) f.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                int off = 0;
                while (off < bytes.length) {
                    int n = in.read(bytes, off, bytes.length - off);
                    if (n < 0) break;
                    off += n;
                }
            }
            String xml = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            List<androidx.media3.ui.danmaku.Danmaku> out = new ArrayList<>();
            // p 属性第四段是颜色（十进制 RGB）；屏蔽彩色弹幕开关
            // （HgStore.getDanmakuBlockColorful）此前只写存档、解析
            // 一律白字——开关白写。现在接上：颜色照 p 段还原，开屏蔽
            // 时彩色条回落白字（判定走 HgPanelLogic.isColorfulDanmaku）。
            boolean blockColorful = HgStore.getDanmakuBlockColorful();
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("<d p=\"([0-9]+(?:\\.[0-9]+)?),[0-9]+,[0-9]+,([0-9]+)[^\"]*\">(.*?)</d>", java.util.regex.Pattern.DOTALL)
                    .matcher(xml);
            while (m.find()) {
                long at = (long) (Double.parseDouble(m.group(1)) * 1000);
                String text = android.text.Html.fromHtml(m.group(3), android.text.Html.FROM_HTML_MODE_LEGACY).toString();
                if (!text.isEmpty()) {
                    int color = Color.WHITE;
                    try {
                        int rgb = Integer.parseInt(m.group(2));
                        String hex = String.format("%06X", rgb & 0xFFFFFF);
                        if (!blockColorful || !HgPanelLogic.isColorfulDanmaku(hex)) {
                            color = 0xFF000000 | (rgb & 0xFFFFFF);
                        }
                    } catch (Exception ignored) {
                    }
                    out.add(new androidx.media3.ui.danmaku.Danmaku(text, at,
                            androidx.media3.ui.danmaku.Danmaku.TYPE_SCROLL, color, 0f, 0, null, 0));
                }
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- 面板宿主（HgFeedPanels 回调面） ----------

    private HgFeedPanels.Host host() {
        return new HgFeedPanels.Host() {
            @Override
            public DramaVideoModel currentModel() {
                return currentVid == null ? null : DramaCache.get(currentVid);
            }

            @Override
            public String currentDefinition() {
                return resolver.currentDefinition(currentVid);
            }

            @Override
            public void changeDefinition(String definition) {
                // B-14 守卫：档名空/当前位越界（列表在面板存续期被
                // 刷新收窄）直接返回，不许 items.get 裸奔抛越界。
                if (currentVid == null || player == null
                        || definition == null || definition.isEmpty()) return;
                if (currentIndex < 0 || currentIndex >= items.size()) return;
                long pos = player.getCurrentPosition();
                DramaItem item = items.get(currentIndex);
                resolver.resolveAs(currentVid, item.contentType, definition, new DramaResolver.Listener() {
                    @Override
                    public void onReady(String videoId, String url, int w, int h) {
                        if (!videoId.equals(currentVid) || player == null) return;
                        // 与 playVid 监听同口径：引擎已泊出不许碰、
                        // 被盖住只备媒体不起播（防后台出声）。
                        if (engineParked) return;
                        player.setMediaItem(MediaItem.fromUri(url));
                        player.prepare();
                        if (pos > 0) player.seekTo(pos);
                        if (activityResumed) {
                            player.setPlayWhenReady(true);
                        } else {
                            resumeOnReturn = true;
                        }
                    }

                    @Override
                    public void onFailed(String videoId, String code, String message) {
                        Toast.makeText(HongguoFeedActivity.this, "切换清晰度失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void setSpeed(float speed) {
                HgStore.setSpeed(speed);
                if (player != null) player.setPlaybackSpeed(speed);
            }

            @Override
            public void openDanmakuSettings() {
                HgFeedPanels.showDanmakuSettings(HongguoFeedActivity.this, this);
            }

            @Override
            public void onTimerChanged() {
                Toast.makeText(HongguoFeedActivity.this,
                        "定时关闭：" + HgPanelLogic.timerStateText(
                                HgStore.getTimerMode(), HgStore.getTimerMinutes()),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDanmakuOptionsChanged() {
                applyDanmakuConfig();
                applyDanmakuVisibility();
                // 关着起播时根本没发取数请求，面板里刚打开必须补拉
                // 当前条，否则层亮了也没货（#21 同链断点）。
                if (HgStore.isDanmakuOn() && currentVid != null && danmakuItems.isEmpty()) {
                    startDanmaku(currentVid);
                }
            }
        };
    }

    private void showMorePanel() {
        panelGuardUntil = System.currentTimeMillis() + 1500;
        HgFeedPanels.showMore(this, host());
    }

    public void showEpisodeSheet() {
        panelGuardUntil = System.currentTimeMillis() + 1500;
        String sid = mode == MODE_SERIES ? seriesIdArg : currentSeriesId;
        if (sid == null) return;
        ensureDetail(sid, detail -> {
            if (detail == null) {
                Toast.makeText(this, "选集加载失败", Toast.LENGTH_SHORT).show();
                return;
            }
            HgEpisodeSheet.show(this, detail, currentEpisodeIndex,
                    idx -> playEpisodeFromSheet(idx));
        });
    }

    /** 收藏键：视频页合集模式按 album 存，其余按剧 id。 */
    private String favKeyOf(DramaItem item) {
        if (mode == MODE_VIDEO && videoAlbumId != null) return videoAlbumId;
        return item.getSeriesId();
    }

    private void bindRailState(int index) {
        FeedAdapter.Holder h = currentHolder();
        if (h == null || index >= items.size()) return;
        DramaItem item = items.get(index);
        String key = favKeyOf(item);
        boolean faved = key != null && HgStore.isFaved(key);
        h.favIcon.setColorFilter(faved ? 0xFFFFB03A : Color.WHITE);
        if (h.favLabel != null) h.favLabel.setText(faved ? "已收藏" : "收藏");
        bindLikeState(h, item);
    }

    /** 点赞心激活色（树内激活红，与横屏锁钮激活同值）。 */
    static final int LIKE_ON_COLOR = 0xFFE63A2E;

    /**
     * 右列点赞态重绑（官方右列序：头像/收藏星/评论/点赞心/分享——
     * 分享已按定案摘除，点赞落在评论之后）：已赞红心、未赞白心；
     * 计数＝服务端 digg 数＋本地已赞补 1，无 digg 数据整行不显
     * （官方「计数有数据才显」同口径）。
     */
    private void bindLikeState(FeedAdapter.Holder h, DramaItem item) {
        if (h.likeIcon == null) return;
        String key = favKeyOf(item);
        boolean liked = key != null && HgStore.isLiked(key);
        h.likeIcon.setColorFilter(liked ? LIKE_ON_COLOR : Color.WHITE);
        long digg = item.getDiggCount();
        if (digg >= 0) {
            h.likeCount.setText(HgTimeTextCount.format(digg + (liked ? 1 : 0)));
            h.likeCount.setVisibility(View.VISIBLE);
        } else {
            h.likeCount.setVisibility(View.GONE);
        }
    }

    // ---------- 生命周期 ----------

    @Override
    protected void onPause() {
        super.onPause();
        activityResumed = false;
        if (orientationListener != null) orientationListener.disable();
        saveProgressNow();
        saveFeedPos();
        if (player != null && !engineParked) {
            resumeOnReturn = player.getPlayWhenReady();
            player.pause();
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        main.removeCallbacks(ticker);
        cancelSpinDelay();
        if (settleAwaitTimeout != null) {
            main.removeCallbacks(settleAwaitTimeout);
            settleAwaitTimeout = null;
        }
        settleAwaitSeq = 0;
        hideFreeze();
        main.removeCallbacks(savePosRunnable);
        main.removeCallbacks(prefetchRunnable);
        if (feedWatchdog != null) main.removeCallbacks(feedWatchdog);
        saveProgressNow();
        if (engine != null && !engineParked) {
            try {
                engine.release();
            } catch (Throwable ignored) {
            }
        }
        // 退出播放页：停掉本剧没拉完的全集预取（只停本页这部剧的会话）。
        // 泊出横屏时本页被回收不算离开这部剧，不停、让它拉完。
        if (mode == MODE_SERIES && !engineParked) DramaSeriesPrefetch.stopFor(seriesIdArg);
        // 泊出中（横屏页在前台）：本实例的解链流正被横屏页用着，不许关。
        if (!engineParked) resolver.closeAll();
        // 关池前先把本页主队列未触发的回调整表摘除（P0 对齐）：
        // settleTo 的六处 main.post 是匿名 lambda、逐个 removeCallbacks
        // 摘不掉，残留触发即是 1.4.68 崩溃的 jb.run 本人。关池顺序
        // 必须是「先摘回调、后关池」，迟到的跨线程投递由 safeExecute 兜。
        main.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        super.onDestroy();
    }

    public void retryCurrent() {
        if (mode == MODE_FEED && currentIndex >= 0 && currentIndex < items.size()) {
            // 首页流重试走整条重播路（含无 vid 正剧的详情定首集）；
            // 过渡走黑场＋转圈，不亮封面（#8 同口径）
            DramaItem it = items.get(currentIndex);
            feedFailedKeys.remove(feedKeyOf(it));
            FeedAdapter.Holder h = currentHolder();
            if (h != null) {
                h.fail.setVisibility(View.GONE);
                h.cover.setVisibility(View.GONE);
                h.spin.setVisibility(View.VISIBLE);
            }
            playFeedItem(it);
            return;
        }
        if (currentVid == null) return;
        FeedAdapter.Holder h = currentHolder();
        if (h != null) {
            h.fail.setVisibility(View.GONE);
            h.spin.setVisibility(View.VISIBLE);
        }
        DramaItem item = currentIndex >= 0 && currentIndex < items.size() ? items.get(currentIndex) : null;
        resolver.close(currentVid);
        playVid(currentVid, item == null ? 0 : item.contentType, 0);
    }

    public void openComments() {
        panelGuardUntil = System.currentTimeMillis() + 1500;
        if (currentSeriesId == null || currentVid == null) return;
        DramaItem item = currentIndex >= 0 && currentIndex < items.size() ? items.get(currentIndex) : null;
        final int atIndex = currentIndex;
        HgCommentsSheet.show(this, currentSeriesId, currentVid,
                item == null ? "" : item.getTitle(), total -> {
                    // D6：面板真实 total 回写打开面板时那一条并重绑
                    // 其右列（面板还开着，先写数；关面板后即同源值）
                    if (atIndex >= 0 && atIndex < items.size()) {
                        DramaItem target = items.get(atIndex);
                        if (target.getCommentCount() != total) {
                            target.setCommentCount(total);
                            adapter.notifyItemChanged(atIndex);
                        }
                    }
                });
    }

    public void refreshStrip() {
        updateSeriesChrome();
        adapter.notifyItemChanged(currentIndex);
    }

    // ---------- Pager 适配器 ----------

    private class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.Holder> {

        class Holder extends RecyclerView.ViewHolder {
            final FrameLayout surface;
            final ImageView cover;
            final ProgressBar spin;
            final TextView fail;
            final View chrome;
            final TextView title;
            final ImageView titleChev;
            final LinearLayout recRow;
            final TextView badgeSign;
            final LinearLayout tagsRow;
            final TextView statement;
            final TextView authorLine;
            final TextView desc;
            final LinearLayout vAuthorRow;
            final ImageView vAvatar;
            final TextView vName;
            final TextView pill;
            final TextView strip;
            final ImageView avatar;
            final ImageView favIcon;
            final TextView favCount;
            final TextView commentCount;
            final ImageView likeIcon;
            final TextView likeCount;
            /** 圆形态（播放页/视频页）两签：裸形态无此件、为 null。 */
            final TextView favLabel;
            final TextView commentLabel;
            final TextView fullPill;
            /** 发弹幕入口「弹」标（#21 乙）。 */
            final TextView dmSend;
            final View rail;
            int boundIndex = -1;

            Holder(FrameLayout page) {
                super(page);
                surface = page.findViewWithTag("surface");
                cover = page.findViewWithTag("cover");
                spin = page.findViewWithTag("spin");
                fail = page.findViewWithTag("fail");
                chrome = page.findViewWithTag("chrome");
                title = page.findViewWithTag("title");
                titleChev = page.findViewWithTag("titlechev");
                recRow = page.findViewWithTag("recrow");
                badgeSign = page.findViewWithTag("badge");
                tagsRow = page.findViewWithTag("tags");
                statement = page.findViewWithTag("statement");
                authorLine = page.findViewWithTag("author");
                desc = page.findViewWithTag("desc");
                vAuthorRow = page.findViewWithTag("vauthor");
                vAvatar = page.findViewWithTag("vavatar");
                vName = page.findViewWithTag("vname");
                pill = page.findViewWithTag("pill");
                strip = page.findViewWithTag("strip");
                avatar = page.findViewWithTag("avatar");
                favIcon = page.findViewWithTag("fav");
                favCount = page.findViewWithTag("fcount");
                commentCount = page.findViewWithTag("ccount");
                likeIcon = page.findViewWithTag("like");
                likeCount = page.findViewWithTag("lcount");
                favLabel = page.findViewWithTag("favlab");
                commentLabel = page.findViewWithTag("cmtlab");
                fullPill = page.findViewWithTag("fullpill");
                dmSend = page.findViewWithTag("dmsend");
                rail = page.findViewWithTag("rail");
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout page = new FrameLayout(HongguoFeedActivity.this);
            page.setLayoutParams(new RecyclerView.LayoutParams(-1, -1));
            page.setBackgroundColor(Color.BLACK);

            FrameLayout surface = new FrameLayout(HongguoFeedActivity.this);
            surface.setTag("surface");
            page.addView(surface, match());

            ImageView cover = new ImageView(HongguoFeedActivity.this);
            cover.setTag("cover");
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            page.addView(cover, match());

            ProgressBar spin = new ProgressBar(HongguoFeedActivity.this);
            spin.setTag("spin");
            FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(dp(40), dp(40));
            slp.gravity = Gravity.CENTER;
            page.addView(spin, slp);

            TextView fail = new TextView(HongguoFeedActivity.this);
            fail.setTag("fail");
            fail.setText("播放失败，点按重试");
            fail.setTextColor(0xFFDDDDDD);
            fail.setTextSize(13.5f);
            fail.setBackground(roundBg(0x66000000, dp(16)));
            fail.setPadding(dp(16), dp(9), dp(16), dp(9));
            fail.setVisibility(View.GONE);
            fail.setOnClickListener(v -> retryCurrent());
            FrameLayout.LayoutParams flp2 = new FrameLayout.LayoutParams(-2, -2);
            flp2.gravity = Gravity.CENTER;
            page.addView(fail, flp2);

            // chrome：底部渐变＋信息摞＋右列（形态按模式在建时定型——
            // 模式在本页生命周期内不变，bind 只管文字与显隐）
            FrameLayout chrome = new FrameLayout(HongguoFeedActivity.this);
            chrome.setTag("chrome");
            int navH = navBarHeight();
            final int infoBase;
            final int railBase;
            switch (mode) {
                case MODE_FEED:
                    infoBase = bottomBlockH() + dp(13);
                    railBase = dp(248) + navH;
                    break;
                case MODE_SERIES:
                    infoBase = bottomBlockH() + dp(12);
                    railBase = dp(158) + navH;
                    break;
                case MODE_VIDEO:
                    infoBase = bottomBlockH() + dp(12);
                    railBase = dp(264) + navH;
                    break;
                default:
                    infoBase = dp(140) + navH;
                    railBase = dp(140) + navH;
                    break;
            }
            // 定稿双 scrim 的底部渐变（transparent 40%→rgba(6,5,5,.88)，
            // 近似取整段渐变高 45% 屏高；清屏时随 chrome 一起隐）
            View scrimB = new View(HongguoFeedActivity.this);
            scrimB.setTag("scrim");
            scrimB.setBackground(new GradientDrawable(
                    GradientDrawable.Orientation.BOTTOM_TOP,
                    new int[]{0xE0060505, 0x00060505}));
            FrameLayout.LayoutParams sblp = new FrameLayout.LayoutParams(-1,
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.45f));
            sblp.gravity = Gravity.BOTTOM;
            chrome.addView(scrimB, sblp);

            LinearLayout info = new LinearLayout(HongguoFeedActivity.this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(18), 0, 0, 0);
            // 推荐药丸行（仅首页，标题上方：系列剧/榜单药丸二选一，
            // bind 时按数据建件；没数据整行 GONE）
            LinearLayout recRow = new LinearLayout(HongguoFeedActivity.this);
            recRow.setTag("recrow");
            recRow.setOrientation(LinearLayout.HORIZONTAL);
            recRow.setGravity(Gravity.CENTER_VERTICAL);
            recRow.setVisibility(View.GONE);
            LinearLayout.LayoutParams rrlp = new LinearLayout.LayoutParams(-2, -2);
            rrlp.bottomMargin = dp(8);
            info.addView(recRow, rrlp);
            // 剧名行（横排：剧名＋chevron 图标，播放页点进详情；chevron
            // 替原先拼在文案尾的「 ›」字形——triage2 口径字形图标化）
            LinearLayout titleRow = new LinearLayout(HongguoFeedActivity.this);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = new TextView(HongguoFeedActivity.this);
            title.setTag("title");
            title.setTextColor(Color.WHITE);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setShadowLayer(dp(2), 0, dp(1), 0xB3000000);
            if (mode == MODE_FEED) {
                // 官值 X-07（账 hongguo-official-values-20261031 §一）：
                // 信息摞剧名 @id/d4＝@dimen/a1u 16.0sp、textStyle bold、
                // 无 fontFamily（系统默认体加粗）、单行省略；官方全链
                // 无行距设置。旧自家定稿 Serif 27sp＋lineSpacing 1.02f
                // 无官出处，已撤。行数他 2026-10-31 定：官方剧名再长
                // 也只一行、超出隐藏，不开第二行（全模式同）
                title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                title.setTextSize(16);
                title.setMaxLines(1);
            } else if (mode == MODE_SERIES) {
                // 定稿 .p-title-link：Serif 19/900
                title.setTypeface(android.graphics.Typeface.create(
                        android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD));
                title.setTextSize(19);
                title.setMaxLines(1);
                // #16 剧名加框（他 13:47–13:50 亲定，只播放页）：透明
                // 白薄底＋方形带圆角＋边缘亮边，框连右侧小箭头一起包
                // ——底挂在标题行上、行宽改贴合内容（下方 addView 处
                // 按模式给宽），内距留字呼吸位。白字本色不动，透明
                // 度压在不抢画面、字仍清楚一档。
                android.graphics.drawable.GradientDrawable titleFrame =
                        new android.graphics.drawable.GradientDrawable();
                titleFrame.setColor(0x2EFFFFFF);
                titleFrame.setCornerRadius(dp(8));
                titleFrame.setStroke(dp(1), 0x8CFFFFFF);
                titleRow.setBackground(titleFrame);
                titleRow.setPadding(dp(10), dp(5), dp(10), dp(5));
            } else if (mode == MODE_VIDEO) {
                // 定稿 .s-title：15.5/700
                title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                title.setTextSize(15.5f);
                title.setMaxLines(1);
            } else {
                title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                title.setTextSize(17);
                title.setMaxLines(1);
            }
            // 爆剧/新剧签（仅首页，剧名行最左、与剧名同行；bind 按数据点亮）
            TextView badgeSign = new TextView(HongguoFeedActivity.this);
            badgeSign.setTag("badge");
            badgeSign.setTextColor(Color.WHITE);
            badgeSign.setTextSize(11);
            badgeSign.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            badgeSign.setPadding(dp(5), dp(2), dp(5), dp(2));
            badgeSign.setShadowLayer(dp(2), 0, dp(1), 0xB3000000);
            badgeSign.setVisibility(View.GONE);
            LinearLayout.LayoutParams bslp = new LinearLayout.LayoutParams(-2, -2);
            bslp.rightMargin = dp(6);
            titleRow.addView(badgeSign, bslp);
            // 剧名按文字宽度走（不许 weight 顶满整行）：短剧名时 chevron
            // 紧贴在剧名后头（官方图形态），长剧名量到行宽为止自动省略、
            // chevron 仍紧跟。他 2026-10-31 点名原写法箭头飘在行尾。
            titleRow.addView(title, new LinearLayout.LayoutParams(-2, -2));
            ImageView titleChev = new ImageView(HongguoFeedActivity.this);
            titleChev.setTag("titlechev");
            titleChev.setImageResource(R.drawable.ic_drama_chev);
            titleChev.setColorFilter(Color.WHITE);
            titleChev.setRotation(180);
            titleChev.setVisibility(View.GONE);
            LinearLayout.LayoutParams tclp2 = new LinearLayout.LayoutParams(dp(17), dp(17));
            tclp2.leftMargin = dp(5);
            titleRow.addView(titleChev, tclp2);
            // #16：播放页标题行带框、宽只贴合内容（剧名＋箭头），
            // 其余模式维持整行宽。
            info.addView(titleRow, new LinearLayout.LayoutParams(
                    mode == MODE_SERIES ? -2 : -1, -2));
            // 题材标签筹排（仅首页，bind 时按数据填）
            LinearLayout tags = new LinearLayout(HongguoFeedActivity.this);
            tags.setTag("tags");
            tags.setOrientation(LinearLayout.HORIZONTAL);
            tags.setVisibility(View.GONE);
            LinearLayout.LayoutParams tglp = new LinearLayout.LayoutParams(-2, -2);
            tglp.topMargin = dp(10);
            info.addView(tags, tglp);
            // AI 声明行（仅首页，数据驱动；定稿色 rgba(255,255,255,.55)）
            TextView statement = new TextView(HongguoFeedActivity.this);
            statement.setTag("statement");
            statement.setTextColor(0x8CFFFFFF);
            statement.setTextSize(11.5f);
            statement.setShadowLayer(dp(2), 0, dp(1), 0xB3000000);
            statement.setVisibility(View.GONE);
            LinearLayout.LayoutParams stlp2 = new LinearLayout.LayoutParams(-2, -2);
            stlp2.topMargin = dp(6);
            info.addView(statement, stlp2);
            // 视频页作者行（头像＋名字，仅 MODE_VIDEO；定稿：头像 34 白环）
            LinearLayout vAuthor = new LinearLayout(HongguoFeedActivity.this);
            vAuthor.setTag("vauthor");
            vAuthor.setOrientation(LinearLayout.HORIZONTAL);
            vAuthor.setGravity(Gravity.CENTER_VERTICAL);
            vAuthor.setVisibility(View.GONE);
            LinearLayout.LayoutParams valp = new LinearLayout.LayoutParams(-2, -2);
            valp.bottomMargin = dp(10);
            ImageView vAvatar = new ImageView(HongguoFeedActivity.this);
            vAvatar.setTag("vavatar");
            vAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable vRing = new GradientDrawable();
            vRing.setShape(GradientDrawable.OVAL);
            vRing.setColor(Color.WHITE);
            vAvatar.setBackground(vRing);
            vAvatar.setPadding(dp(2), dp(2), dp(2), dp(2));
            vAuthor.addView(vAvatar, new LinearLayout.LayoutParams(dp(34), dp(34)));
            TextView vName = new TextView(HongguoFeedActivity.this);
            vName.setTag("vname");
            vName.setTextColor(Color.WHITE);
            vName.setTextSize(15);
            vName.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            vName.setShadowLayer(dp(2), 0, dp(1), 0xB3000000);
            LinearLayout.LayoutParams vnlp = new LinearLayout.LayoutParams(-2, -2);
            vnlp.leftMargin = dp(9);
            vAuthor.addView(vName, vnlp);
            // 短片页信息摞顺序（定稿）：作者行在标题之上——本件在其余
            // 模式恒 GONE，插到信息列首位只影响短片页的可见次序
            info.addView(vAuthor, 0, valp);
            TextView author = new TextView(HongguoFeedActivity.this);
            author.setTag("author");
            author.setTextColor(0xE6FFFFFF);
            author.setTextSize(12.5f);
            author.setShadowLayer(dp(2), 0, dp(1), 0xB3000000);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-2, -2);
            alp.topMargin = dp(5);
            info.addView(author, alp);
            TextView desc = new TextView(HongguoFeedActivity.this);
            desc.setTag("desc");
            desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
            desc.setShadowLayer(dp(2), 0, dp(1), 0xB3000000);
            if (mode == MODE_VIDEO) {
                // 定稿 .s-desc：13sp 行高 1.65（倍率近似）三行、rgba(白,.88)
                desc.setTextColor(0xE0FFFFFF);
                desc.setTextSize(13);
                desc.setLineSpacing(0, 1.38f);
                desc.setMaxLines(3);
            } else {
                desc.setTextColor(0xD9FFFFFF);
                desc.setTextSize(12.5f);
                desc.setMaxLines(2);
            }
            LinearLayout.LayoutParams dlp2 = new LinearLayout.LayoutParams(-2, -2);
            dlp2.topMargin = dp(5);
            info.addView(desc, dlp2);

            LinearLayout pillRow = new LinearLayout(HongguoFeedActivity.this);
            pillRow.setOrientation(LinearLayout.HORIZONTAL);
            pillRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams prlp = new LinearLayout.LayoutParams(-2, -2);
            prlp.topMargin = dp(10);
            TextView pill = new TextView(HongguoFeedActivity.this);
            pill.setTag("pill");
            pill.setTextSize(12.5f);
            pill.setPadding(dp(12), dp(6), dp(12), dp(6));
            pillRow.addView(pill);
            TextView strip = new TextView(HongguoFeedActivity.this);
            strip.setTag("strip");
            strip.setTextColor(0xF2FFFFFF);
            strip.setTextSize(12.5f);
            strip.setBackground(roundBg(0x59000000, dp(15)));
            strip.setPadding(dp(12), dp(6), dp(12), dp(6));
            LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(-2, -2);
            stlp.leftMargin = dp(8);
            pillRow.addView(strip, stlp);
            info.addView(pillRow, prlp);

            // 发弹幕入口（#21 乙按官方加回；件五落位纠正）：官方
            // PublishDanmakuEntranceView 是信息层 cuc 里 @id/cpp 横排
            // 的第一件——位于信息摞最上方（dig-feed-drawer §2.5.1、
            // 普查卡 21、官方帧 113561 实拍「弹」在剧名行正上方）。
            // #21 乙旧落位把它加在信息摞最下行（pillRow 之下），与
            // 官方上下正相反，他 17:2x 点名放错——本件挪到信息摞
            // 首位（index 0，cuc 序），左端起排；显隐跟弹幕开关走
            // （官方门：关弹幕即不显），点击先过登录门再开编辑层
            // （见 onDanmakuSendTap），发送链与门一字未动。字形精
            // 确度量官方未抠到、形态待裁（沿旧标）。
            TextView dmSend = new TextView(HongguoFeedActivity.this);
            dmSend.setTag("dmsend");
            dmSend.setText("弹");
            dmSend.setTextColor(0xF2FFFFFF);
            dmSend.setTextSize(12.5f);
            dmSend.setBackground(roundBg(0x59000000, dp(13)));
            dmSend.setPadding(dp(11), dp(5), dp(11), dp(5));
            dmSend.setVisibility(View.GONE);
            LinearLayout.LayoutParams dmlp = new LinearLayout.LayoutParams(-2, -2);
            dmlp.bottomMargin = dp(8);
            info.addView(dmSend, 0, dmlp);

            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(-1, -2);
            ilp.gravity = Gravity.START | Gravity.BOTTOM;
            ilp.rightMargin = dp(76);
            ilp.bottomMargin = infoBase;
            chrome.addView(info, ilp);

            // 右列两形态（定稿）：刷流/UGC＝裸图标＋计数（.frail-btn），
            // 播放页/视频页＝47dp 玻璃圆＋11sp 签（.rail-btn）
            LinearLayout rail = new LinearLayout(HongguoFeedActivity.this);
            rail.setTag("rail");
            rail.setOrientation(LinearLayout.VERTICAL);
            rail.setGravity(Gravity.CENTER_HORIZONTAL);
            if (mode == MODE_FEED || mode == MODE_UGC) {
                // 作者头像在最上：45dp 白环（有作者身份才显，bind 时分流）
                ImageView avatar = new ImageView(HongguoFeedActivity.this);
                avatar.setTag("avatar");
                avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
                GradientDrawable avRing = new GradientDrawable();
                avRing.setShape(GradientDrawable.OVAL);
                avRing.setColor(Color.WHITE);
                avatar.setBackground(avRing);
                avatar.setPadding(dp(2), dp(2), dp(2), dp(2));
                avatar.setVisibility(View.GONE);
                rail.addView(avatar, new LinearLayout.LayoutParams(dp(45), dp(45)));
                ImageView fav = railIcon(R.drawable.ic_drama_star, 0);
                fav.setTag("fav");
                LinearLayout.LayoutParams favlp = new LinearLayout.LayoutParams(dp(31), dp(31));
                favlp.topMargin = dp(19);
                rail.addView(fav, favlp);
                TextView fcount = new TextView(HongguoFeedActivity.this);
                fcount.setTag("fcount");
                fcount.setTextColor(Color.WHITE);
                fcount.setTextSize(12);
                fcount.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                fcount.setGravity(Gravity.CENTER);
                fcount.setShadowLayer(dp(2), 0, dp(1), 0xB3000000);
                fcount.setVisibility(View.GONE);
                LinearLayout.LayoutParams fclp = new LinearLayout.LayoutParams(-2, -2);
                fclp.topMargin = dp(3);
                rail.addView(fcount, fclp);
                ImageView comment = railIcon(R.drawable.ic_drama_comment, 0);
                comment.setTag("comment");
                LinearLayout.LayoutParams comlp = new LinearLayout.LayoutParams(dp(30), dp(30));
                comlp.topMargin = dp(19);
                rail.addView(comment, comlp);
                TextView ccount = new TextView(HongguoFeedActivity.this);
                ccount.setTag("ccount");
                ccount.setTextColor(Color.WHITE);
                ccount.setTextSize(12);
                ccount.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                ccount.setGravity(Gravity.CENTER);
                ccount.setShadowLayer(dp(2), 0, dp(1), 0xB3000000);
                LinearLayout.LayoutParams cclp = new LinearLayout.LayoutParams(-2, -2);
                cclp.topMargin = dp(3);
                rail.addView(ccount, cclp);
                addRailLike(rail);
            } else {
                // 右列与首页统一（他 2026-10-31 点名「首页跟播放页还
                // 不一样」）：裸图标＋计数，与刷流形态同一套装配，
                // 玻璃圆＋签的旧形态整套摘除；头像字段留游离占位
                // （本形态右列不显头像，Holder 引用不断）。
                ImageView fav = railIcon(R.drawable.ic_drama_star, 0);
                fav.setTag("fav");
                rail.addView(fav, new LinearLayout.LayoutParams(dp(31), dp(31)));
                TextView fcount = new TextView(HongguoFeedActivity.this);
                fcount.setTag("fcount");
                fcount.setTextColor(Color.WHITE);
                fcount.setTextSize(12);
                fcount.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                fcount.setGravity(Gravity.CENTER);
                fcount.setShadowLayer(dp(2), 0, dp(1), 0xB3000000);
                fcount.setVisibility(View.GONE);
                LinearLayout.LayoutParams fclp = new LinearLayout.LayoutParams(-2, -2);
                fclp.topMargin = dp(3);
                rail.addView(fcount, fclp);
                ImageView comment = railIcon(R.drawable.ic_drama_comment, 0);
                comment.setTag("comment");
                LinearLayout.LayoutParams comlp = new LinearLayout.LayoutParams(dp(30), dp(30));
                comlp.topMargin = dp(19);
                rail.addView(comment, comlp);
                TextView ccount = new TextView(HongguoFeedActivity.this);
                ccount.setTag("ccount");
                ccount.setTextColor(Color.WHITE);
                ccount.setTextSize(12);
                ccount.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                ccount.setGravity(Gravity.CENTER);
                ccount.setShadowLayer(dp(2), 0, dp(1), 0xB3000000);
                LinearLayout.LayoutParams cclp = new LinearLayout.LayoutParams(-2, -2);
                cclp.topMargin = dp(3);
                rail.addView(ccount, cclp);
                addRailLike(rail);
                ImageView dummyAvatar = new ImageView(HongguoFeedActivity.this);
                dummyAvatar.setTag("avatar");
                dummyAvatar.setVisibility(View.GONE);
                chrome.addView(dummyAvatar, new FrameLayout.LayoutParams(0, 0));
            }
            FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(-2, -2);
            rlp.gravity = Gravity.END | Gravity.BOTTOM;
            rlp.rightMargin = dp(12);
            rlp.bottomMargin = railBase;
            chrome.addView(rail, rlp);

            // 全屏观看钮：横版剧画面下方居中（定稿 .p-fs-btn：底
            // rgba(46,46,50,.92)、13.5sp、top 63.25%+14——原生侧按底
            // 边距换算近似；横屏入口只此一个）
            TextView fullPill = new TextView(HongguoFeedActivity.this);
            fullPill.setTag("fullpill");
            fullPill.setText("全屏观看");
            fullPill.setTextColor(0xF2FFFFFF);
            fullPill.setTextSize(13.5f);
            fullPill.setBackground(roundBg(0xE82E2E32, dp(17)));
            fullPill.setPadding(dp(16), dp(9), dp(16), dp(9));
            fullPill.setVisibility(View.GONE);
            FrameLayout.LayoutParams fplp = new FrameLayout.LayoutParams(-2, -2);
            fplp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            // 落位不在这儿定死：全屏钮跟着视频走（他 2026-10-31 点名），
            // 画面落定后由 anchorFullPill 按真实下缘摆。
            chrome.addView(fullPill, fplp);

            page.addView(chrome, match());

            // 手势层：不消费事件（竖滑照常），只认单击/长按
            final android.view.GestureDetector gd = new android.view.GestureDetector(
                    HongguoFeedActivity.this, new android.view.GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    onPageTap(page);
                    return false;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    boolean center = e.getX() > page.getWidth() / 3f && e.getX() < page.getWidth() * 2 / 3f
                            && e.getY() > page.getHeight() / 3f && e.getY() < page.getHeight() * 2 / 3f;
                    onPageLongPress(center);
                }
            });
            page.setOnTouchListener((v, e) -> {
                gd.onTouchEvent(e);
                return false;
            });
            return new Holder(page);
        }

        private ImageView railIcon(int res, int padDp) {
            ImageView iv = new ImageView(HongguoFeedActivity.this);
            iv.setImageResource(res);
            iv.setColorFilter(Color.WHITE);
            iv.setPadding(dp(padDp), dp(padDp), dp(padDp), dp(padDp));
            return iv;
        }

        /** 右列点赞件（官方序在评论之后）：心形裸图标＋计数，与评论件同装配口径。 */
        private void addRailLike(LinearLayout rail) {
            ImageView like = railIcon(R.drawable.ic_mine_heart, 0);
            like.setTag("like");
            LinearLayout.LayoutParams likelp = new LinearLayout.LayoutParams(dp(30), dp(30));
            likelp.topMargin = dp(19);
            rail.addView(like, likelp);
            TextView lcount = new TextView(HongguoFeedActivity.this);
            lcount.setTag("lcount");
            lcount.setTextColor(Color.WHITE);
            lcount.setTextSize(12);
            lcount.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            lcount.setGravity(Gravity.CENTER);
            lcount.setShadowLayer(dp(2), 0, dp(1), 0xB3000000);
            lcount.setVisibility(View.GONE);
            LinearLayout.LayoutParams lclp = new LinearLayout.LayoutParams(-2, -2);
            lclp.topMargin = dp(3);
            rail.addView(lcount, lclp);
        }


        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            DramaItem item = items.get(position);
            h.boundIndex = position;
            if (item.getCoverUrl() != null && !item.getCoverUrl().isEmpty()) {
                Glide.with(HongguoFeedActivity.this).load(item.getCoverUrl()).into(h.cover);
            } else {
                h.cover.setImageDrawable(null);
            }
            // #8：当前条（起播中/在播）永不露封面，空窗走黑场＋转圈；
            // 非当前条封面作静态呈现保留（刷流浏览形态）。
            h.cover.setVisibility(position == currentIndex ? View.GONE : View.VISIBLE);
            h.spin.setVisibility(View.GONE);
            h.fail.setVisibility(View.GONE);
            if (mode == MODE_FEED && position != currentIndex
                    && feedFailedKeys.contains(feedKeyOf(item))) {
                // 已记账的失败条回收重绑：封面不许回潮当内容（黑页滑过）
                h.cover.setVisibility(View.GONE);
            }
            String favKey = favKeyOf(item);
            boolean faved = favKey != null && HgStore.isFaved(favKey);
            h.favIcon.setColorFilter(faved ? 0xFFFFB03A : Color.WHITE);
            h.favIcon.setOnClickListener(v -> toggleFav(item, h));
            bindLikeState(h, item);
            h.likeIcon.setOnClickListener(v -> toggleLike(item, h));
            // 圆形态两签（定稿 JS 口径）：评论签填计数（cmtWan）、
            // 收藏签有「已收藏」态；无计数数据时回「评论」原文
            if (h.favLabel != null) h.favLabel.setText(faved ? "已收藏" : "收藏");
            if (h.commentLabel != null) {
                h.commentLabel.setText(item.getCommentCount() > 0
                        ? HgTimeTextCount.format(item.getCommentCount()) : "评论");
            }
            h.commentCount.setText(item.getCommentCount() > 0
                    ? HgTimeTextCount.format(item.getCommentCount()) : "评论");
            // 右列已全形态统一为裸形态：计数件全模式都显（没数时
            // ensureCommentCount 补拉，拉到前先显「评论」原文）
            h.commentCount.setVisibility(View.VISIBLE);
            h.itemView.findViewWithTag("comment").setOnClickListener(v -> {
                if (position == currentIndex) openComments();
            });
            boolean landscape = isLandItem(item);
            h.fullPill.setVisibility(landscape && mode != MODE_VIDEO ? View.VISIBLE : View.GONE);
            h.fullPill.setOnClickListener(v -> {
                if (position == currentIndex) enterLand(true);
            });
            h.dmSend.setVisibility(danmakuEntranceVisible(item) ? View.VISIBLE : View.GONE);
            h.dmSend.setOnClickListener(v -> {
                if (position == currentIndex) onDanmakuSendTap();
            });
            resetInfo(h);
            if (mode == MODE_FEED) bindFeedChrome(h, item, position);
            else if (mode == MODE_SERIES) bindSeriesChrome(h, item, position);
            else if (mode == MODE_VIDEO) bindVideoChrome(h, item);
            else bindUgcChrome(h, item, faved);
            h.chrome.setVisibility(cleanMode ? View.GONE : View.VISIBLE);
        }

        /** 复位全部可选信息件（防回收串显），各模式分支再点亮自己的。 */
        private void resetInfo(Holder h) {
            h.title.setVisibility(View.VISIBLE);
            h.title.setOnClickListener(null);
            h.titleChev.setVisibility(View.GONE);
            h.titleChev.setOnClickListener(null);
            h.recRow.setVisibility(View.GONE);
            h.recRow.removeAllViews();
            h.badgeSign.setVisibility(View.GONE);
            h.tagsRow.setVisibility(View.GONE);
            h.statement.setVisibility(View.GONE);
            h.authorLine.setVisibility(View.GONE);
            h.desc.setVisibility(View.GONE);
            h.vAuthorRow.setVisibility(View.GONE);
            h.pill.setVisibility(View.GONE);
            h.strip.setVisibility(View.GONE);
            h.avatar.setVisibility(View.GONE);
            h.favCount.setVisibility(View.GONE);
        }

        private void toggleFav(DramaItem item, Holder h) {
            String key = favKeyOf(item);
            if (key == null) return;
            // 账号终定口径（#22）：收藏绑账号、官方无本地收藏——
            // 未登录点了一律跳登录页，不落任何本地状态。
            if (!com.fongmi.android.tv.hongguo.api.HgAccountStore.isLoggedIn()) {
                HongguoLoginActivity.start(HongguoFeedActivity.this);
                return;
            }
            boolean now = HgStore.toggleFaved(key);
            if (mode == MODE_VIDEO && videoAlbum != null) {
                HgStore.putMeta(key, videoAlbum.title, videoAlbum.cover,
                        videoAlbum.episodeCount);
                HgStore.putType(key, HgStore.TYPE_ALBUM);
            } else {
                HgStore.putMeta(key, item.getTitle(), item.getCoverUrl(),
                        item.getEpisodeCount());
                HgStore.putType(key, HgStore.typeCodeOf(item));
            }
            h.favIcon.setColorFilter(now ? 0xFFFFB03A : Color.WHITE);
            if (h.favLabel != null) h.favLabel.setText(now ? "已收藏" : "收藏");
            // 收藏数随动作增减并即时重画（普查两断之一：此前 toggle
            // 不动 followedCnt、数字永远停在进页那一刻）。没带数
            // （-1）不动，保持未知不冒充。
            if (item.followedCnt >= 0) {
                item.followedCnt = Math.max(0, item.followedCnt + (now ? 1 : -1));
                if (h.favCount != null) {
                    if (item.followedCnt > 0) {
                        h.favCount.setText(HgTimeTextCount.format(item.followedCnt));
                        h.favCount.setVisibility(View.VISIBLE);
                    } else {
                        h.favCount.setVisibility(View.GONE);
                    }
                }
            }
            styleFavPill(h, now);
            // 账号动作上报追剧架（#22：未登录已在入口跳登录页、到不
            // 了这里；本地集只是账号镜像，失败静默、同步拉取收口）
            com.fongmi.android.tv.hongguo.api.HgAccountSync.reportShelf(key, now);
        }

        /** 点赞键：与收藏同 key 口径（视频页合集按 album、其余按剧 id），落 liked 集＋元信息。 */
        private void toggleLike(DramaItem item, Holder h) {
            String key = favKeyOf(item);
            if (key == null) return;
            // 账号终定口径（#22）：点赞绑账号——未登录点了跳登录页
            //（他亲述的官方原行为），不落任何本地状态。
            if (!com.fongmi.android.tv.hongguo.api.HgAccountStore.isLoggedIn()) {
                HongguoLoginActivity.start(HongguoFeedActivity.this);
                return;
            }
            HgStore.toggleLiked(key);
            if (mode == MODE_VIDEO && videoAlbum != null) {
                HgStore.putMeta(key, videoAlbum.title, videoAlbum.cover,
                        videoAlbum.episodeCount);
                HgStore.putType(key, HgStore.TYPE_ALBUM);
            } else {
                HgStore.putMeta(key, item.getTitle(), item.getCoverUrl(),
                        item.getEpisodeCount());
                HgStore.putType(key, HgStore.typeCodeOf(item));
            }
            bindLikeState(h, item);
            // 账号动作上报点赞口（簇四，与收藏的 reportShelf 同路）：
            // 粒度＝当前集 vid（官方 caller 同构），currentVid 缺时
            // 回落条目 vid；都缺只落本地镜像、不发。本地镜像先行、
            // 上报失败静默，登录同步拉取收口。
            String likeVid = currentVid != null && !currentVid.isEmpty()
                    ? currentVid : item.getVid();
            com.fongmi.android.tv.hongguo.api.HgAccountSync.reportLike(
                    likeVid, HgStore.isLiked(key));
        }

        private void styleFavPill(Holder h, boolean faved) {
            h.pill.setText(faved ? "★ 已收藏" : "☆ 收藏");
            h.pill.setTextColor(faved ? 0xFF3D2E00 : Color.WHITE);
            h.pill.setBackground(roundBg(faved ? 0xFFFFB03A : 0x59000000, dp(15)));
        }

        /** 无头像图时的字母头像底色（与评论面 AV_COLORS 同源同值，草稿 cmtHTML 口径）。 */
        private final int[] AVATAR_COLORS = {
                0xFFE63A2E, 0xFFE8A13A, 0xFF3E8E6E, 0xFF4A6FD4,
                0xFF8A5FC0, 0xFFC2507A, 0xFF2E8EA6, 0xFF7A6A53};

        /** 字母头像位图：色块（按名首字＋名长取色，与评论面同法）＋白色粗体首字。 */
        private android.graphics.Bitmap letterAvatarBitmap(String name) {
            int size = dp(90);
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                    size, size, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
            int seed = name.charAt(0) + name.length();
            android.graphics.Paint paint =
                    new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            paint.setColor(AVATAR_COLORS[seed % AVATAR_COLORS.length]);
            canvas.drawRect(0, 0, size, size, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(size * 0.5f);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextAlign(android.graphics.Paint.Align.CENTER);
            android.graphics.Paint.FontMetrics fm = paint.getFontMetrics();
            canvas.drawText(name.substring(0, 1), size / 2f,
                    size / 2f - (fm.ascent + fm.descent) / 2f, paint);
            return bmp;
        }

        private void bindRailAvatar(Holder h, String uid, String name, String avatar) {
            if (uid == null || uid.isEmpty()) {
                h.avatar.setVisibility(View.GONE);
                return;
            }
            if (avatar != null && !avatar.isEmpty()) {
                h.avatar.setVisibility(View.VISIBLE);
                Glide.with(HongguoFeedActivity.this).load(avatar)
                        .circleCrop().into(h.avatar);
            } else if (name != null && !name.isEmpty()) {
                // B-10：uid 有、头像 URL 空时旧写法只清图，白环底＋
                // 2dp 内距留成一只空白白环（全域账 B 片定格）。改走
                // 字母头像兜底（评论面同口径），环内是名首字色块、
                // 不再是空环；点击进作者页不变。
                h.avatar.setVisibility(View.VISIBLE);
                Glide.with(HongguoFeedActivity.this).load(letterAvatarBitmap(name))
                        .circleCrop().into(h.avatar);
            } else {
                // 无图又无名：没什么可显，整件收起（标题行在 hasAuthor
                // 时另有作者入口，无名条目本就没有）
                h.avatar.setVisibility(View.GONE);
                h.avatar.setOnClickListener(null);
                return;
            }
            h.avatar.setOnClickListener(v -> HongguoAuthorActivity.start(
                    HongguoFeedActivity.this, uid, name, avatar));
        }

        /**
         * 推荐药丸（标题上方一行，数据带才显）：系列剧（rec_type=15）
         * 优先、其次榜单——回包里两型同条共存零例，同位二选一。
         * 系列剧＝两段文案＋尾箭，点击走 onSeriesPillClick 分发
         * （多季弹季名单 S-01、其余进剧；B-01 落点留账）；榜单＝
         * 渐变底＋奖杯图标＋两段文案（解析侧已合成），仍只渲染不挂点击。
         */
        private void bindRecPill(Holder h, DramaItem item) {
            boolean series = item.seriesReason != null && !item.seriesReason.isEmpty();
            boolean rank = item.rankText != null && !item.rankText.isEmpty();
            if (!series && !rank) return;
            LinearLayout pill = new LinearLayout(HongguoFeedActivity.this);
            pill.setOrientation(LinearLayout.HORIZONTAL);
            pill.setGravity(Gravity.CENTER_VERTICAL);
            pill.setPadding(dp(8), dp(3), dp(8), dp(3));
            if (series) {
                pill.setBackground(roundBg(seriesPillColor(item), dp(9)));
                String text = item.seriesReason;
                if (item.seriesContent != null && !item.seriesContent.isEmpty()) {
                    text += "｜" + item.seriesContent;
                }
                pill.addView(recPillText(text));
                // 系列药丸带 › 形、本就是可点入口（#20 甲：此前不挂
                // 点击点了没反应）——点击走 onSeriesPillClick 分发：
                // 多季先弹季名单（S-01）、其余进剧（B-01 落点留账，
                // 官方形态抠值回后只校分发一处）。
                if (item.getSeriesId() != null) {
                    pill.setOnClickListener(v -> onSeriesPillClick(item));
                }
                ImageView chev = new ImageView(HongguoFeedActivity.this);
                chev.setImageResource(R.drawable.ic_drama_chev);
                chev.setColorFilter(Color.WHITE);
                chev.setRotation(180);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(11), dp(11));
                clp.leftMargin = dp(3);
                pill.addView(chev, clp);
            } else {
                pill.setBackground(rankPillBg(item));
                if (item.rankIconUrl != null && !item.rankIconUrl.isEmpty()) {
                    ImageView icon = new ImageView(HongguoFeedActivity.this);
                    Glide.with(HongguoFeedActivity.this).load(item.rankIconUrl).into(icon);
                    LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(13), dp(13));
                    ilp.rightMargin = dp(4);
                    pill.addView(icon, ilp);
                }
                pill.addView(recPillText(item.rankText));
            }
            h.recRow.addView(pill);
            h.recRow.setVisibility(View.VISIBLE);
        }

        private TextView recPillText(String text) {
            TextView t = new TextView(HongguoFeedActivity.this);
            t.setText(text);
            t.setTextColor(Color.WHITE);
            t.setTextSize(11);
            t.setMaxLines(1);
            t.setShadowLayer(dp(2), 0, dp(1), 0xB3000000);
            return t;
        }

        /** 系列剧药丸底色：数据自带单色＋透明度合成一个 ARGB；缺色退通用深透底。 */
        private int seriesPillColor(DramaItem item) {
            Integer rgb = parseHexColor(item.seriesColor);
            if (rgb == null) return 0x59000000;
            int alpha = 0xFF;
            if (item.seriesAlpha != null) {
                try {
                    float a = Float.parseFloat(item.seriesAlpha.trim());
                    if (a >= 0f && a <= 1f) alpha = Math.round(a * 255f);
                } catch (NumberFormatException ignored) {
                }
            }
            return (alpha << 24) | (rgb & 0xFFFFFF);
        }

        /** 榜单药丸底：rankColors 原样色组横向渐变；缺失/解不开退通用深透底。 */
        private GradientDrawable rankPillBg(DramaItem item) {
            if (item.rankColors != null && !item.rankColors.isEmpty()) {
                int[] cols = new int[item.rankColors.size()];
                boolean ok = true;
                for (int i = 0; i < cols.length; i++) {
                    Integer c = parseHexColor(item.rankColors.get(i));
                    if (c == null) {
                        ok = false;
                        break;
                    }
                    cols[i] = c;
                }
                if (ok && cols.length == 1) return roundBg(cols[0], dp(9));
                if (ok) {
                    GradientDrawable g = new GradientDrawable(
                            GradientDrawable.Orientation.LEFT_RIGHT, cols);
                    g.setCornerRadius(dp(9));
                    return g;
                }
            }
            return roundBg(0x59000000, dp(9));
        }

        /** #RRGGBB/#AARRGGBB 串转色值；解不开返回 null。 */
        private static Integer parseHexColor(String s) {
            if (s == null) return null;
            try {
                return Color.parseColor(s.trim());
            } catch (Exception e) {
                return null;
            }
        }

        /** 首页条目：药丸＋角标＋剧名（带作者显 @作者名）＋筹＋AI 声明；右列头像顶＋收藏数。 */
        private void bindFeedChrome(Holder h, DramaItem item, int position) {
            bindRecPill(h, item);
            // 爆剧/新剧签：文案与底色同源（tag_info），两样齐才显
            Integer badgeColor = parseHexColor(item.badgeColor);
            if (item.getBadge() != null && !item.getBadge().isEmpty() && badgeColor != null) {
                h.badgeSign.setText(item.getBadge());
                h.badgeSign.setBackground(roundBg(badgeColor, dp(4)));
                h.badgeSign.setVisibility(View.VISIBLE);
            }
            // 标题分支（官方形态）：带作者的条目大行显 @作者名（点进
            // 作者页，与头像同一路），剧名下沉底条（updateWatchBar 走
            // bottomBarText 分段）；正剧显剧名（点进详情）
            boolean hasAuthor = item.getAuthorUid() != null && !item.getAuthorUid().isEmpty()
                    && item.getAuthorName() != null && !item.getAuthorName().isEmpty();
            if (hasAuthor) {
                h.title.setText("@" + item.getAuthorName());
                h.title.setOnClickListener(v -> {
                    if (position == currentIndex) {
                        HongguoAuthorActivity.start(HongguoFeedActivity.this,
                                item.getAuthorUid(), item.getAuthorName(),
                                item.getAuthorAvatar());
                    }
                });
            } else {
                h.title.setText(item.getTitle() == null ? "" : item.getTitle());
                h.title.setOnClickListener(v -> {
                    if (position != currentIndex) return;
                    if (item.kind == DramaItemKind.SERIES
                            && item.getSeriesId() != null) {
                        HongguoDetailActivity.start(HongguoFeedActivity.this,
                                item.getSeriesId(), item.getTitle(), item.getAlbumId());
                    } else if (item.kind == DramaItemKind.ALBUM) {
                        // B-09：合集条的标题行此前落空（只 SERIES 分支
                        // 有落点）——合集本体在流内除了底条没有第二个
                        // 入口，标题行补同落点（与观看完整条同路进
                        // 合集队列页）。
                        startVideoAlbum(HongguoFeedActivity.this, item);
                    }
                });
            }
            h.tagsRow.removeAllViews();
            // 筹行：infoChips 优先（secondary_infos 官方筹序，搜索页同
            // 口径），没带才回落分类标签
            List<String> tags = item.infoChips != null && !item.infoChips.isEmpty()
                    ? item.infoChips : item.getTags();
            // 宽度预算：整排只许占屏宽减左缩进与右列预留，放不下
            // 的标签直接不加——此前无预算地全加，横排测宽时最后
            // 一枚只剩一字宽、文字被逐字折成竖排（他 113816 实证）。
            // 预算与已用宽提出来与后面的热度筹共用一本账。
            int budget = getResources().getDisplayMetrics().widthPixels
                    - dp(18) - dp(88);
            int used = 0;
            if (tags != null && !tags.isEmpty()) {
                h.tagsRow.setVisibility(View.VISIBLE);
                android.graphics.Paint tagPaint = new android.graphics.Paint();
                tagPaint.setTextSize(11.5f * getResources().getDisplayMetrics().scaledDensity);
                for (String tag : tags) {
                    int w = (int) Math.ceil(tagPaint.measureText(tag)) + dp(16) + dp(6);
                    if (used > 0 && used + w > budget) break;
                    // 标签按官方（他 2026-10-31 点名）：深灰实底、无边、
                    // 小圆角方片（不是全圆药丸），官方图采样底色约 #3A3A3F
                    TextView chip = new TextView(HongguoFeedActivity.this);
                    chip.setText(tag);
                    chip.setTextColor(Color.WHITE);
                    chip.setTextSize(11.5f);
                    chip.setMaxLines(1);
                    chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    chip.setMaxWidth(budget);
                    GradientDrawable chipBg = new GradientDrawable();
                    chipBg.setColor(0xFF3A3A3F);
                    chipBg.setCornerRadius(dp(5));
                    chip.setBackground(chipBg);
                    chip.setPadding(dp(8), dp(3), dp(8), dp(3));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                    lp.rightMargin = dp(6);
                    h.tagsRow.addView(chip, lp);
                    used += w;
                }
            }
            // 热度筹（普查接线）：recText 剧场/搜索都在显、首页流
            // 此前零调用——同排末尾补一枚原文筹（同形态同色），
            // 宽度预算与标签共用、放不下不加。
            if (item.getRecText() != null && !item.getRecText().isEmpty()) {
                android.graphics.Paint heatPaint = new android.graphics.Paint();
                heatPaint.setTextSize(11.5f * getResources().getDisplayMetrics().scaledDensity);
                int heatW = (int) Math.ceil(heatPaint.measureText(item.getRecText()))
                        + dp(16) + dp(6);
                if (used + heatW <= budget) {
                    h.tagsRow.setVisibility(View.VISIBLE);
                    TextView heat = new TextView(HongguoFeedActivity.this);
                    heat.setText(item.getRecText());
                    heat.setTextColor(Color.WHITE);
                    heat.setTextSize(11.5f);
                    heat.setMaxLines(1);
                    heat.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    GradientDrawable heatBg = new GradientDrawable();
                    heatBg.setColor(0xFF3A3A3F);
                    heatBg.setCornerRadius(dp(5));
                    heat.setBackground(heatBg);
                    heat.setPadding(dp(8), dp(3), dp(8), dp(3));
                    LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-2, -2);
                    hlp.rightMargin = dp(6);
                    h.tagsRow.addView(heat, hlp);
                }
            }
            if (item.getStatement() != null && !item.getStatement().isEmpty()) {
                h.statement.setText("ⓘ 作者声明：" + item.getStatement());
                h.statement.setVisibility(View.VISIBLE);
            }
            bindRailAvatar(h, item.getAuthorUid(), item.getAuthorName(), item.getAuthorAvatar());
            if (item.followedCnt > 0) {
                h.favCount.setText(HgTimeTextCount.format(item.followedCnt));
                h.favCount.setVisibility(View.VISIBLE);
            }
            // 收藏药丸（B-12）：pill 此前只有 UGC 单条页点亮，本面
            // toggleFav 虽调 styleFavPill 刷文案底色、件本身恒 GONE
            // 永不显。与 UGC 同装配：显形＋按收藏态上色＋点击同路。
            String favKey = favKeyOf(item);
            h.pill.setVisibility(View.VISIBLE);
            styleFavPill(h, favKey != null && HgStore.isFaved(favKey));
            h.pill.setOnClickListener(v -> toggleFav(item, h));
        }

        /** 短剧播放页条目：左下只带箭头剧名（点进详情），无其余信息件。 */
        private void bindSeriesChrome(Holder h, DramaItem item, int position) {
            String t = item.getTitle() == null ? "" : item.getTitle();
            h.title.setText(t);
            View.OnClickListener openDetail = v -> {
                if (position == currentIndex && seriesIdArg != null) {
                    HongguoDetailActivity.start(HongguoFeedActivity.this,
                            seriesIdArg, item.getTitle(), null);
                }
            };
            h.title.setOnClickListener(openDetail);
            // 箭头是独立图标件（原先拼在文案尾的「 ›」字形已摘）
            h.titleChev.setVisibility(View.VISIBLE);
            h.titleChev.setOnClickListener(openDetail);
        }

        /** 视频页条目：左下作者行＋标题＋文案（定稿三行分开，不合并回退），右列只收藏/评论。 */
        private void bindVideoChrome(Holder h, DramaItem item) {
            h.title.setText(item.getTitle() == null ? "" : item.getTitle());
            h.title.setVisibility(item.getTitle() == null || item.getTitle().isEmpty()
                    ? View.GONE : View.VISIBLE);
            String name = videoAuthorName != null ? videoAuthorName : item.getAuthorName();
            String avatar = videoAuthorAvatar != null ? videoAuthorAvatar : item.getAuthorAvatar();
            String uid = videoAuthorUid != null ? videoAuthorUid : item.getAuthorUid();
            if (name != null || avatar != null) {
                h.vAuthorRow.setVisibility(View.VISIBLE);
                h.vName.setText(name == null ? "" : name);
                if (avatar != null && !avatar.isEmpty()) {
                    Glide.with(HongguoFeedActivity.this).load(avatar)
                            .circleCrop().into(h.vAvatar);
                    h.vAvatar.setVisibility(View.VISIBLE);
                } else {
                    h.vAvatar.setVisibility(View.GONE);
                }
                h.vAuthorRow.setOnClickListener(v -> {
                    if (uid != null && !uid.isEmpty()) {
                        HongguoAuthorActivity.start(HongguoFeedActivity.this, uid, name, avatar);
                    }
                });
            }
            String caption = item.getDesc();
            h.desc.setText(caption == null ? "" : caption);
            h.desc.setVisibility(caption == null || caption.isEmpty()
                    ? View.GONE : View.VISIBLE);
            // 收藏药丸（B-12）：与首页流同装配点亮（favKeyOf 在合集
            // 模式按 album 取键，与 toggleFav 同口径）。
            String favKey = favKeyOf(item);
            h.pill.setVisibility(View.VISIBLE);
            styleFavPill(h, favKey != null && HgStore.isFaved(favKey));
            h.pill.setOnClickListener(v -> toggleFav(item, h));
        }

        /** 旧 UGC 单条（作者作品等旧入口）：原形态不动。 */
        private void bindUgcChrome(Holder h, DramaItem item, boolean faved) {
            h.title.setText(item.getTitle() == null ? "" : item.getTitle());
            String authorName = item.getAuthorName() != null ? item.getAuthorName() : item.getPublisher();
            h.authorLine.setText(authorName == null ? "" : "@" + authorName);
            h.authorLine.setVisibility(authorName == null ? View.GONE : View.VISIBLE);
            h.authorLine.setOnClickListener(v -> {
                if (item.getAuthorUid() != null && !item.getAuthorUid().isEmpty()) {
                    HongguoAuthorActivity.start(HongguoFeedActivity.this,
                            item.getAuthorUid(), item.getAuthorName(), item.getAuthorAvatar());
                }
            });
            h.desc.setText(item.getDesc() == null ? "" : item.getDesc());
            h.desc.setVisibility(item.getDesc() == null || item.getDesc().isEmpty()
                    ? View.GONE : View.VISIBLE);
            h.pill.setVisibility(View.VISIBLE);
            styleFavPill(h, faved);
            h.pill.setOnClickListener(v -> toggleFav(item, h));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    /** 计数简写（万级一位小数去尾零）。 */
    static final class HgTimeTextCount {
        static String format(long n) {
            if (n < 10000) return String.valueOf(n);
            double w = n / 10000.0;
            String s = w >= 100 ? String.valueOf(Math.round(w)) : String.format(
                    java.util.Locale.US, "%.1f", w).replaceAll("\\.0$", "");
            return s + "万";
        }
    }
}
