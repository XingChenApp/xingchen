package com.fongmi.android.tv.hongguo.ui;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.os.Looper;
import android.view.Gravity;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
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

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.HongguoDanmakuApi;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.hongguo.api.HgCallback;
import com.fongmi.android.tv.hongguo.drama.DramaCache;
import com.fongmi.android.tv.hongguo.drama.DramaClient;
import com.fongmi.android.tv.hongguo.drama.DramaDetail;
import com.fongmi.android.tv.hongguo.drama.DramaResolver;
import com.fongmi.android.tv.hongguo.drama.DramaVideoModel;
import com.fongmi.android.tv.player.engine.ExoPlayerEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 红果横屏页（2026-10-30 重建阶段8）：真独立 Activity（清单
 * userLandscape＋singleTop＋configChanges 全套＋PiP 声明）。播放器
 * 走 DramaPlayerPool 迁移：进页领走竖屏页泊出的同一播放器同流续播，
 * 退出（返回键/传感器转竖）写回交接单泊回，竖屏页领回对位。
 * 横屏锁只活在本页实例内（官方跨页单例，已知差异记账）。页面构成
 * 从简：画面＋顶行（返回/剧名/集号）＋底行（播停/进度/选集）＋锁钮
 * ＋右侧评论入口（点赞原位，他 2026-10-31 14:54 亲定；点赞已摘）。
 */
public class HongguoLandActivity extends AppCompatActivity {

    private PlayerView playerView;
    private ExoPlayerEngine engine;
    private Player player;
    private final DramaResolver resolver = new DramaResolver();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "hg-land");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean destroyed;

    /** 池投递唯一收口（与竖屏页同型，P0 定根见 HongguoFeedActivity.safeExecute）。 */
    private void safeExecute(Runnable task) {
        if (destroyed || worker.isShutdown()) return;
        try {
            worker.execute(task);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
        }
    }

    private String seriesId;
    private String seriesTitle;
    private int contentType;
    private String curVid;
    private int curEp = 1;
    private DramaDetail detail;
    /** 取链窗口去重（resolveWithWindow，与刷剧页同口径）：已发窗口的起始位。 */
    private int windowStart = -1;
    private String danmakuVid;
    private boolean locked;
    private boolean exited;
    /** 引擎已泊回池里（exitLand/销毁兜底）：销毁关流时要给泊回的
     * 引擎正播的那条流留命，见 onDestroy。 */
    private boolean parkedOut;
    private boolean scrubbing;
    private long createdAtMs;

    private View topBar;
    private View bottomBar;
    /** 发弹幕入口：底行左侧输入条（#21 乙加、件五改形入底行）。 */
    private TextView dmSend;
    /** 当前弹幕列表留存（自己发的回显并入用）。 */
    private final java.util.List<androidx.media3.ui.danmaku.Danmaku> dmItems =
            new java.util.ArrayList<>();
    private ImageView lockBtn;
    private ImageView playBtn;
    private TextView epView;
    /** 评论入口（他 14:54 亲定落点赞原位）：图标＋计数竖排件。 */
    private LinearLayout commentBox;
    private TextView commentCount;
    /** 横屏评论面板（官方形态：右侧框出、画面挤小）与画面框联动。 */
    private FrameLayout stageRoot;
    private HgLandCommentsPanel commentPanel;
    private boolean commentPanelOpen;
    /** 横屏选集页内层（右侧停靠，官方页内集数层形态）与开合态。 */
    private HgLandEpisodesPanel episodePanel;
    private boolean episodePanelOpen;
    private android.animation.ValueAnimator stageAnimator;
    /** 当前挤占进度 0..1（0＝全宽、1＝面板全开），动画与落位共用。 */
    private float stageSqueeze01;
    /** 实流尺寸（onVideoSizeChanged 回报，画面框计算的单一数据源）。 */
    private int videoW;
    private int videoH;
    /** 系统栏与挖孔让位（insets 监听捕获）。 */
    private int barsTop;
    private int barsBottom;
    private int cutoutLeft;
    private int cutoutRight;
    /** 竖滑切集已触发时吞掉随之而来的点按（防 chrome 跟着翻）。 */
    private boolean suppressTap;
    private TextView timeView;
    private TextView timeDurView;
    private FrameLayout progressWrap;
    private View progressFill;
    private View progressThumb;
    private float progressFraction;
    private ProgressBar spin;
    private TextView failView;
    private OrientationEventListener orientationListener;
    /** 件六：退横屏竖带内停留计时（首帧入带记时刻，≥400ms 才触发）。 */
    private long portraitBandSince;
    /** 切集顺滑（与竖屏同口径）：末帧冻结层/帧图/代次＋转圈延迟闸。 */
    private ImageView freezeView;
    private Bitmap freezeBitmap;
    private int freezeSeq;
    private String switchPendingVid;
    private Runnable spinDelayRunnable;

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (player != null && !scrubbing) {
                long pos = player.getCurrentPosition();
                long dur = player.getDuration();
                if (dur > 0) {
                    setProgressFraction((float) pos / (float) dur);
                    timeView.setText(formatTime(pos));
                    timeDurView.setText(formatTime(dur));
                }
            }
            main.postDelayed(this, 500);
        }
    };

    private final Runnable hideChrome = () -> setChrome(false);

    private static String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        return String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60);
    }

    /** 细轨进度落位：已走宽＝轨宽×分数，白点骑在已走末端。 */
    private void setProgressFraction(float f) {
        progressFraction = Math.max(0f, Math.min(1f, f));
        applyProgress();
    }

    private void applyProgress() {
        if (progressWrap == null || progressFill == null || progressThumb == null) return;
        int w = progressWrap.getWidth();
        if (w <= 0) return;
        int fillW = (int) (w * progressFraction);
        ViewGroup.LayoutParams flp = progressFill.getLayoutParams();
        if (flp.width != fillW) {
            flp.width = fillW;
            progressFill.setLayoutParams(flp);
        }
        FrameLayout.LayoutParams tlp = (FrameLayout.LayoutParams) progressThumb.getLayoutParams();
        int left = Math.max(0, Math.min(w - dp(11), fillW - dp(5)));
        if (tlp.leftMargin != left) {
            tlp.leftMargin = left;
            progressThumb.setLayoutParams(tlp);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createdAtMs = System.currentTimeMillis();
        HgWindowStyle.apply(this, false, false);

        DramaPlayerPool.Bag bag = DramaPlayerPool.peekBag();
        engine = DramaPlayerPool.takeEngine();
        if (bag == null || engine == null) {
            if (engine != null) {
                try {
                    engine.release();
                } catch (Throwable ignored) {
                }
                engine = null;
            }
            finish();
            return;
        }
        seriesId = bag.seriesId;
        seriesTitle = bag.seriesTitle;
        contentType = bag.contentType;
        curVid = bag.vid;
        curEp = bag.episodeIndex;
        player = engine.getPlayer();
        player.addListener(playerListener);

        FrameLayout root = new FrameLayout(this);
        stageRoot = root;
        root.setBackgroundColor(Color.BLACK);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars());
            androidx.core.graphics.Insets cut = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            // 画面本身铺满整屏（沉浸式，不内缩）；数值只存下，控件
            // 落位由 layoutChromeToFrame 按画面框统一算（#17）。
            barsTop = bars.top;
            barsBottom = bars.bottom;
            cutoutLeft = cut.left;
            cutoutRight = cut.right;
            layoutChromeToFrame();
            if (commentPanel != null) commentPanel.setBottomInset(barsBottom);
            if (stageSqueeze01 > 0f) applyStage(stageSqueeze01);
            return insets;
        });

        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setShutterBackgroundColor(Color.BLACK);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setDanmakuEnabled(bag.danmakuOn);
        playerView.getDanmakuController().setConfig(danmakuConfig());
        playerView.setPlayer(player);
        playerView.setOnClickListener(v -> onSurfaceTap());
        // 上下滑切集（#17，此前横屏手动切集从未接）：竖直位移过
        // 64dp 且纵大于横判切集——上滑下一集、下滑上一集（与竖屏
        // 流的上滑看下一条同向）；触发后随之而来的点按吞掉，防
        // chrome 跟着翻。锁定态不理。不消费事件、点按链原样走。
        final float[] touchDown = new float[2];
        playerView.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    touchDown[0] = ev.getX();
                    touchDown[1] = ev.getY();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                    if (locked || detail == null) break;
                    float dx = ev.getX() - touchDown[0];
                    float dy = ev.getY() - touchDown[1];
                    if (Math.abs(dy) > dp(64) && Math.abs(dy) > Math.abs(dx)) {
                        suppressTap = true;
                        if (dy < 0) switchEpisode(curEp + 1, false);
                        else switchEpisode(curEp - 1, false);
                    }
                    break;
                default:
                    break;
            }
            return false;
        });
        playerView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or2, ob) ->
                layoutChromeToFrame());
        root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        // 切集末帧冻结层：盖播放面、衬在控件之下；切集时留上一
        // 集末帧到新集首帧接上，平时藏起（切集顺滑，与竖屏同口径）
        freezeView = new ImageView(this);
        freezeView.setScaleType(ImageView.ScaleType.FIT_XY);
        freezeView.setVisibility(View.GONE);
        root.addView(freezeView, new FrameLayout.LayoutParams(-1, -1));

        spin = new ProgressBar(this);
        FrameLayout.LayoutParams spinLp = new FrameLayout.LayoutParams(dp(40), dp(40));
        // 落位不走屏中心 gravity：由 layoutChromeToFrame 按画面框
        // 中心给边距（锚系统一，见该法注），gravity 只作左上锚点
        spinLp.gravity = Gravity.TOP | Gravity.START;
        root.addView(spin, spinLp);
        failView = new TextView(this);
        failView.setText("播放失败，点按重试");
        failView.setTextColor(Color.WHITE);
        failView.setTextSize(14);
        failView.setGravity(Gravity.CENTER);
        failView.setVisibility(View.GONE);
        failView.setOnClickListener(v -> {
            failView.setVisibility(View.GONE);
            switchEpisode(curEp, true);
        });
        FrameLayout.LayoutParams failLp = new FrameLayout.LayoutParams(-1, -1);
        root.addView(failView, failLp);

        // 锁钮（左侧中部）
        lockBtn = new ImageView(this);
        lockBtn.setImageResource(R.drawable.ic_drama_lock);
        lockBtn.setColorFilter(0xFFBDBDBD);
        lockBtn.setPadding(dp(10), dp(10), dp(10), dp(10));
        lockBtn.setOnClickListener(v -> {
            locked = !locked;
            lockBtn.setColorFilter(locked ? 0xFFE63A2E : 0xFFBDBDBD);
            Toast.makeText(this, locked ? "已锁定" : "已解锁", Toast.LENGTH_SHORT).show();
            pokeChrome();
        });
        FrameLayout.LayoutParams lockLp = new FrameLayout.LayoutParams(dp(44), dp(44));
        lockLp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        lockLp.leftMargin = dp(10);
        root.addView(lockBtn, lockLp);

        // 点赞钮已按 #17 亲定整颗摘除（横屏不要点赞）；它原来的
        // 位置（右中、与锁钮对称）现归评论入口（他 2026-10-31
        // 14:54 亲定：「评论按钮就放在点赞原来的位置」）。形态与
        // 竖屏右列评论件同款：裸图标 ic_drama_comment 白色化
        // 30dp＋计数签（12sp 粗体白字、有数显数、无数显「评论」），
        // 点开右侧停靠评论面板（官方横屏形态：框出＋画面挤小，
        // 他 15:49 亲定，不再走底部弹层）。不许与底行再留第二个
        // 评论入口。
        commentBox = new LinearLayout(this);
        commentBox.setOrientation(LinearLayout.VERTICAL);
        commentBox.setGravity(Gravity.CENTER_HORIZONTAL);
        commentBox.setPadding(dp(7), dp(7), dp(7), dp(7));
        ImageView commentIcon = new ImageView(this);
        commentIcon.setImageResource(R.drawable.ic_drama_comment);
        commentIcon.setColorFilter(Color.WHITE);
        commentBox.addView(commentIcon, new LinearLayout.LayoutParams(dp(30), dp(30)));
        commentCount = new TextView(this);
        commentCount.setText("评论");
        commentCount.setTextColor(Color.WHITE);
        commentCount.setTextSize(12);
        commentCount.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        commentCount.setGravity(Gravity.CENTER);
        commentCount.setShadowLayer(dp(2), 0, dp(1), 0xB3000000);
        LinearLayout.LayoutParams ccLp = new LinearLayout.LayoutParams(-2, -2);
        ccLp.topMargin = dp(3);
        commentBox.addView(commentCount, ccLp);
        commentBox.setOnClickListener(v -> openCommentPanel());
        FrameLayout.LayoutParams commentLp = new FrameLayout.LayoutParams(-2, -2);
        commentLp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        commentLp.rightMargin = dp(10);
        root.addView(commentBox, commentLp);

        // 顶行
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(14), dp(8), dp(16), dp(8));
        // 返回：38dp 玻璃圆钮（与播放页 .iconbtn 同形态）；箭头字形
        // 换官方包原样 ic_back（#17，官方包 drawable-xxhdpi 抠出、
        // 白色着色沿用），归属待真机对帧。
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.hg_ic_back_official);
        back.setColorFilter(Color.WHITE);
        android.graphics.drawable.GradientDrawable backBg =
                new android.graphics.drawable.GradientDrawable();
        backBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        backBg.setColor(0x52141212);
        backBg.setStroke(1, 0x2EFFFFFF);
        back.setBackground(backBg);
        back.setPadding(dp(9), dp(9), dp(9), dp(9));
        back.setOnClickListener(v -> exitLand());
        top.addView(back, new LinearLayout.LayoutParams(dp(38), dp(38)));
        // #17：横屏不挂标题，只留集数。
        epView = new TextView(this);
        epView.setTextColor(0xFFD8D8DC);
        epView.setTextSize(13);
        epView.setText("第" + curEp + "集");
        LinearLayout.LayoutParams epLp = new LinearLayout.LayoutParams(-2, -2);
        epLp.leftMargin = dp(10);
        top.addView(epView, epLp);
        // 「···」更多钮（#22，他 2026-10-31 14:53 亲令）：右上角，
        // 与左端返回＋「第X集」以弹性空段隔开；形态同竖屏播放页
        // 右上更多钮（38dp 玻璃圆＋ic_drama_more、与本页返回钮同
        // 玻璃底）。点开横屏更多面板（倍速/清晰度/弹幕开关＋设置）。
        View topSpacer = new View(this);
        top.addView(topSpacer, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView moreBtn = new ImageView(this);
        moreBtn.setImageResource(R.drawable.ic_drama_more);
        moreBtn.setColorFilter(Color.WHITE);
        android.graphics.drawable.GradientDrawable moreBg =
                new android.graphics.drawable.GradientDrawable();
        moreBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        moreBg.setColor(0x52141212);
        moreBg.setStroke(1, 0x2EFFFFFF);
        moreBtn.setBackground(moreBg);
        moreBtn.setPadding(dp(9), dp(9), dp(9), dp(9));
        moreBtn.setOnClickListener(v -> {
            HgFeedPanels.showMoreLand(this, landHost());
            pokeChrome();
        });
        top.addView(moreBtn, new LinearLayout.LayoutParams(dp(38), dp(38)));
        topBar = top;
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(-1, -2);
        topLp.gravity = Gravity.TOP;
        root.addView(top, topLp);

        // 底行
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(16), dp(6), dp(16), dp(10));
        playBtn = new ImageView(this);
        playBtn.setImageResource(R.drawable.ic_drama_pause);
        playBtn.setColorFilter(Color.WHITE);
        playBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
        playBtn.setOnClickListener(v -> {
            if (player == null) return;
            if (player.isPlaying() || player.getPlayWhenReady()) {
                player.pause();
                playBtn.setImageResource(R.drawable.ic_drama_play);
            } else {
                player.play();
                playBtn.setImageResource(R.drawable.ic_drama_pause);
            }
            pokeChrome();
        });
        bottom.addView(playBtn, new LinearLayout.LayoutParams(dp(42), dp(42)));
        // 时间签左右分置＋细轨（定稿 .p-progress）：当前在左、时长
        // 在右，11sp rgba(255,255,255,.75)；轨 3dp rgba(.25)、已走
        // rgba(.92)、11dp 白点。系统 SeekBar 粗轨大拇指整件弃用，
        // 拖动在本控件触摸里 seek（ scrubbing 语义与旧监听一致）。
        timeView = new TextView(this);
        timeView.setTextColor(0xBFFFFFFF);
        timeView.setTextSize(11);
        timeView.setText("0:00");
        LinearLayout.LayoutParams curLp = new LinearLayout.LayoutParams(-2, -2);
        curLp.leftMargin = dp(10);
        bottom.addView(timeView, curLp);
        progressWrap = new FrameLayout(this);
        View track = new View(this);
        android.graphics.drawable.GradientDrawable trackBg =
                new android.graphics.drawable.GradientDrawable();
        trackBg.setColor(0x40FFFFFF);
        trackBg.setCornerRadius(dp(2));
        track.setBackground(trackBg);
        FrameLayout.LayoutParams trackLp = new FrameLayout.LayoutParams(-1, dp(3));
        trackLp.gravity = Gravity.CENTER_VERTICAL;
        progressWrap.addView(track, trackLp);
        progressFill = new View(this);
        android.graphics.drawable.GradientDrawable fillBg =
                new android.graphics.drawable.GradientDrawable();
        fillBg.setColor(0xEBFFFFFF);
        fillBg.setCornerRadius(dp(2));
        progressFill.setBackground(fillBg);
        FrameLayout.LayoutParams fillLp = new FrameLayout.LayoutParams(0, dp(3));
        fillLp.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        progressWrap.addView(progressFill, fillLp);
        progressThumb = new View(this);
        android.graphics.drawable.GradientDrawable thumbBg =
                new android.graphics.drawable.GradientDrawable();
        thumbBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        thumbBg.setColor(Color.WHITE);
        progressThumb.setBackground(thumbBg);
        FrameLayout.LayoutParams thumbLp = new FrameLayout.LayoutParams(dp(11), dp(11));
        thumbLp.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        progressWrap.addView(progressThumb, thumbLp);
        progressWrap.setOnTouchListener((v, ev) -> {
            if (player == null) return true;
            long dur = player.getDuration();
            if (dur <= 0) return true;
            float f = Math.max(0f, Math.min(1f,
                    ev.getX() / Math.max(1, v.getWidth())));
            switch (ev.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    scrubbing = true;
                    setProgressFraction(f);
                    timeView.setText(formatTime((long) (f * dur)));
                    return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    setProgressFraction(f);
                    timeView.setText(formatTime((long) (f * dur)));
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    scrubbing = false;
                    player.seekTo((long) (f * dur));
                    pokeChrome();
                    return true;
                default:
                    return true;
            }
        });
        progressWrap.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or2, ob) ->
                applyProgress());
        LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(0, dp(30), 1f);
        wrapLp.leftMargin = dp(10);
        wrapLp.rightMargin = dp(10);
        bottom.addView(progressWrap, wrapLp);
        timeDurView = new TextView(this);
        timeDurView.setTextColor(0xBFFFFFFF);
        timeDurView.setTextSize(11);
        timeDurView.setText("0:00");
        bottom.addView(timeDurView);
        TextView eps = new TextView(this);
        eps.setText("选集");
        eps.setTextColor(Color.WHITE);
        eps.setTextSize(13.5f);
        eps.setPadding(dp(14), dp(8), dp(6), dp(8));
        eps.setOnClickListener(v -> showEpisodes());
        bottom.addView(eps);
        // 评论入口不在底行——在他亲定的点赞原位（右中，见锁钮后
        // 的装配），此处不许再留第二个。
        bottomBar = bottom;
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(-1, -2);
        bottomLp.gravity = Gravity.BOTTOM;
        root.addView(bottom, bottomLp);

        // 发弹幕入口（#21 乙横屏同加；件五落位纠正）：官方横屏发
        // 弹幕入口是底行左侧的输入条（他发的官方图口径），不是悬
        // 在底行上方的一颗「弹」标——#21 乙旧落位把它做成 root 级
        // 浮标（底行上方 64dp），他 17:2x 点名放错。本件改形入
        // 座：作底行第一件、左侧输入条，文案照官方入口资源
        // 「发条友善的弹幕吧」（普查卡 21：cn4/f_0），点条开编辑
        // 层；入底行后随 layoutChromeToFrame 的底行框重排自动跟
        // 框，原浮标的重排段已摘。显隐跟弹幕开关＋chrome 同步、
        // 点击先过登录门、发送链一字未动（与竖屏同口径）。条宽
        // 160dp 为自家定值（官方横屏条宽未抠到、待裁）。
        dmSend = new TextView(this);
        dmSend.setText("发条友善的弹幕吧");
        dmSend.setTextColor(0xBFFFFFFF);
        dmSend.setTextSize(12.5f);
        dmSend.setSingleLine(true);
        dmSend.setEllipsize(android.text.TextUtils.TruncateAt.END);
        dmSend.setGravity(Gravity.CENTER_VERTICAL);
        android.graphics.drawable.GradientDrawable dmBg =
                new android.graphics.drawable.GradientDrawable();
        dmBg.setColor(0x59000000);
        dmBg.setCornerRadius(dp(16));
        dmSend.setBackground(dmBg);
        dmSend.setPadding(dp(12), dp(7), dp(12), dp(7));
        dmSend.setOnClickListener(v -> onDanmakuSendTap());
        dmSend.setVisibility(HgStore.isDanmakuOn() ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams dmlp = new LinearLayout.LayoutParams(dp(160), -2);
        dmlp.rightMargin = dp(10);
        bottom.addView(dmSend, 0, dmlp);

        // 横屏评论面板（官方形态：右侧框出、画面挤小，他 15:49
        // 亲定）：常驻 root 最上层、缺省藏起；开合经 applyStage
        // 与播放面同宽联动，计数经 Host.onTotal 回写评论钮计数签
        // （与此前评论单 OnTotal 同源同法）。
        commentPanel = new HgLandCommentsPanel(this, new HgLandCommentsPanel.Host() {
            @Override
            public void onTotal(long total) {
                if (commentCount != null) {
                    commentCount.setText(total > 0
                            ? HongguoFeedActivity.HgTimeTextCount.format(total)
                            : "评论");
                }
            }

            @Override
            public void onClose() {
                closeCommentPanel();
            }
        });
        commentPanel.view().setVisibility(View.GONE);
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(0, -1);
        panelLp.gravity = Gravity.END;
        root.addView(commentPanel.view(), panelLp);

        // 横屏选集页内层（官方形态是页内集数层，他横屏里不再弹
        // 竖屏同款底部单——HgEpisodeSheet 自此只供竖屏）：常驻
        // root 上层、缺省藏起，选集钮改开它；数据链与竖屏单同
        // 源（集表＋本地断点），点格回宿主走既有 switchEpisode。
        episodePanel = new HgLandEpisodesPanel(this, new HgLandEpisodesPanel.Host() {
            @Override
            public void onPick(int episodeIndex) {
                closeEpisodePanel();
                switchEpisode(episodeIndex, false);
            }

            @Override
            public void onClose() {
                closeEpisodePanel();
            }
        });
        episodePanel.view().setVisibility(View.GONE);
        FrameLayout.LayoutParams epPanelLp = new FrameLayout.LayoutParams(0, -1);
        epPanelLp.gravity = Gravity.END;
        root.addView(episodePanel.view(), epPanelLp);

        setContentView(root);
        androidx.core.view.ViewCompat.requestApplyInsets(root);
        main.postDelayed(ticker, 300);
        pokeChrome();
        startDanmaku(curVid);
        fetchDetail();

        orientationListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                long now = System.currentTimeMillis();
                if (now - createdAtMs < 600) return; // 出生去抖（保留，自家值）
                // 件六（自家定值）：退横屏加带内停留 ≥400ms 才受理，
                // 与竖屏页进横屏共用 DramaLandLogic 互锁（触发后反向
                // 1000ms 不受理＋过中间带才重武装）；竖带口径不变。
                // 监听只在系统自动旋转开着时武装（onResume 现读），
                // 关＝不自动退、走返回键手动路。
                boolean portrait = DramaLandLogic.orientationBand(orientation) == 0;
                if (portrait) {
                    if (!DramaLandLogic.sensorDirectionArmed(-1, now)) {
                        portraitBandSince = 0;
                        return;
                    }
                    if (portraitBandSince == 0) {
                        portraitBandSince = now;
                        return;
                    }
                    if (now - portraitBandSince >= 400) {
                        portraitBandSince = 0;
                        if (DramaLandLogic.shouldSensorExitLand(true, locked)) {
                            DramaLandLogic.sensorNoteTrigger(-1, now);
                            exitLand();
                        }
                    }
                } else {
                    portraitBandSince = 0;
                    if (DramaLandLogic.orientationBand(orientation) == -1) {
                        DramaLandLogic.sensorNoteMiddleBand();
                    }
                }
            }
        };
    }

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_READY) {
                cancelSpinDelay();
                spin.setVisibility(View.GONE);
            } else if (state == Player.STATE_BUFFERING) {
                // 切集闸未到期时不抢亮转圈（闸到点自会亮）；非切集
                // 的中途缓冲照旧即时亮
                if (spinDelayRunnable == null) spin.setVisibility(View.VISIBLE);
            } else if (state == Player.STATE_ENDED) {
                if (HgStore.isAutoNext() && detail != null) {
                    switchEpisode(curEp + 1, false);
                }
            }
        }

        @Override
        public void onVideoSizeChanged(@NonNull androidx.media3.common.VideoSize size) {
            // 画面框的单一数据源（#17）：像素比折算后的实显示尺寸
            videoW = Math.round(size.width * size.pixelWidthHeightRatio);
            videoH = size.height;
            layoutChromeToFrame();
        }

        @Override
        public void onRenderedFirstFrame() {
            switchPendingVid = null;
            cancelSpinDelay();
            hideFreeze();
            spin.setVisibility(View.GONE);
        }

        @Override
        public void onPlayerError(@NonNull androidx.media3.common.PlaybackException error) {
            switchPendingVid = null;
            cancelSpinDelay();
            hideFreeze();
            spin.setVisibility(View.GONE);
            failView.setVisibility(View.VISIBLE);
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            playBtn.setImageResource(isPlaying
                    ? R.drawable.ic_drama_pause : R.drawable.ic_drama_play);
            // 播时常亮、停时放行：与主播放器 PlaybackActivity 同口径
            // （本页此前没设过这面旗，播着也会按系统超时息屏）
            if (isPlaying) {
                getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else if (player == null
                    || player.getPlaybackState() != Player.STATE_BUFFERING) {
                getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }
    };

    private void fetchDetail() {
        if (seriesId == null) return;
        DramaClient.fetchDetail(seriesId, new HgCallback<DramaDetail>() {
            @Override
            public void onSuccess(DramaDetail d) {
                runOnUiThread(() -> {
                    detail = d;
                    refreshCommentCount();
                });
            }

            @Override
            public void onError(int code, String msg) {
            }
        });
    }

    private void showEpisodes() {
        if (detail == null) {
            Toast.makeText(this, "选集加载中", Toast.LENGTH_SHORT).show();
            fetchDetail();
            return;
        }
        // 评论面板与选集层同为右侧停靠，二者互斥：先收评论面板
        if (commentPanelOpen) closeCommentPanel();
        episodePanel.bind(detail, curEp);
        openEpisodePanel();
    }

    /** 选集层开合：自右滑入/滑出 200ms（与评论面板同速同曲线）。 */
    private void openEpisodePanel() {
        if (episodePanel == null || episodePanelOpen) return;
        episodePanelOpen = true;
        View pv = episodePanel.view();
        pv.animate().cancel();
        int rootW = stageRoot != null && stageRoot.getWidth() > 0
                ? stageRoot.getWidth()
                : getResources().getDisplayMetrics().widthPixels;
        int panelW = Math.round(rootW * HgLandEpisodesPanel.WIDTH_RATIO);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pv.getLayoutParams();
        if (lp.width != panelW) {
            lp.width = panelW;
            pv.setLayoutParams(lp);
        }
        pv.setVisibility(View.VISIBLE);
        pv.setTranslationX(panelW);
        pv.animate().translationX(0f).setDuration(200)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
        setChrome(false);
    }

    private void closeEpisodePanel() {
        if (!episodePanelOpen) return;
        episodePanelOpen = false;
        View pv = episodePanel.view();
        pv.animate().cancel();
        int panelW = pv.getWidth();
        if (panelW <= 0) {
            pv.setVisibility(View.GONE);
            pokeChrome();
            return;
        }
        pv.animate().translationX(panelW).setDuration(200)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> {
                    // 动画途中又重开则不藏（开合态以旗为准）
                    if (!episodePanelOpen) pv.setVisibility(View.GONE);
                })
                .start();
        pokeChrome();
    }

    /** 横屏更多面板宿主（#22）：与竖屏 host 同一回调面。 */
    private HgFeedPanels.Host landHost() {
        return new HgFeedPanels.Host() {
            @Override
            public Player player() {
                return player;
            }

            @Override
            public String vid() {
                return curVid;
            }

            @Override
            public String seriesId() {
                return seriesId;
            }

            @Override
            public DramaVideoModel currentModel() {
                return curVid == null ? null : DramaCache.get(curVid);
            }

            @Override
            public String currentDefinition() {
                return resolver.currentDefinition(curVid);
            }

            @Override
            public void changeDefinition(String definition) {
                if (curVid == null || player == null) return;
                final long pos = player.getCurrentPosition();
                resolver.resolveAs(curVid, contentType, definition,
                        new DramaResolver.Listener() {
                            @Override
                            public void onReady(String videoId, String url, int w, int h) {
                                if (!videoId.equals(curVid) || player == null) return;
                                player.setMediaItem(MediaItem.fromUri(url));
                                player.prepare();
                                if (pos > 0) player.seekTo(pos);
                                player.setPlayWhenReady(true);
                            }

                            @Override
                            public void onFailed(String videoId, String code, String message) {
                                Toast.makeText(HongguoLandActivity.this,
                                        "切换清晰度失败", Toast.LENGTH_SHORT).show();
                            }
                        });
            }

            @Override
            public void setSpeed(float speed) {
                HgStore.setSpeed(speed);
                if (player != null) player.setPlaybackSpeed(speed);
            }

            @Override
            public void openEpisodes() {
                showEpisodes();
            }

            @Override
            public void openDanmakuSettings() {
                HgFeedPanels.showDanmakuSettings(HongguoLandActivity.this, this);
            }

            @Override
            public void onDanmakuOptionsChanged() {
                applyDanmakuOptions();
            }
        };
    }

    /**
     * 弹幕设置变更落效（#22，与竖屏 onDanmakuOptionsChanged 同口径）：
     * 重算配置、开关落到弹幕层与发弹幕标、刚打开且无货时补拉当前集。
     */
    private void applyDanmakuOptions() {
        if (playerView != null) {
            playerView.getDanmakuController().setConfig(danmakuConfig());
            playerView.setDanmakuEnabled(HgStore.isDanmakuOn());
        }
        if (dmSend != null) {
            dmSend.setVisibility(HgStore.isDanmakuOn() ? View.VISIBLE : View.GONE);
        }
        if (HgStore.isDanmakuOn() && curVid != null && dmItems.isEmpty()) {
            startDanmaku(curVid);
        }
    }

    private void switchEpisode(int episodeIndex, boolean retry) {
        if (detail == null) return;
        DramaDetail.Episode target = null;
        for (DramaDetail.Episode e : detail.getEpisodes()) {
            if (e.getIndex() == episodeIndex) {
                target = e;
                break;
            }
        }
        if (target == null || target.getVideoId() == null) {
            if (!retry) Toast.makeText(this, "没有这一集", Toast.LENGTH_SHORT).show();
            return;
        }
        final String vid = target.getVideoId();
        // 切集顺滑（与竖屏同口径）：先抓当前帧留屏、转圈延迟显；
        // 抓帧必须赶在换源之前，代次按目标 vid 对位
        switchPendingVid = vid;
        initiateFreezeCapture(vid);
        curEp = episodeIndex;
        curVid = vid;
        epView.setText("第" + curEp + "集");
        refreshCommentCount();
        // 评论面板跟随切集：绑过才跟（没开过的面板开时自会绑当前集）
        if (commentPanel != null && commentPanel.isBound()) {
            commentPanel.bind(seriesId, vid);
        }
        // 选集层开着切集（层外竖滑）：当前格高亮跟随新集
        if (episodePanel != null && episodePanelOpen) {
            episodePanel.bind(detail, curEp);
        }
        gateSpinForSwitch(vid);
        failView.setVisibility(View.GONE);
        DramaResolver.Listener listener = new DramaResolver.Listener() {
            @Override
            public void onReady(String videoId, String localUrl, int w, int h) {
                if (!videoId.equals(curVid) || player == null) return;
                player.setMediaItem(MediaItem.fromUri(localUrl));
                player.prepare();
                player.setPlayWhenReady(true);
                startDanmaku(videoId);
            }

            @Override
            public void onFailed(String videoId, String code, String message) {
                if (!videoId.equals(curVid)) return;
                switchPendingVid = null;
                cancelSpinDelay();
                hideFreeze();
                spin.setVisibility(View.GONE);
                failView.setVisibility(View.VISIBLE);
            }
        };
        resolveWithWindow(vid, contentType, listener);
    }

    /** 发起抓帧：把播放面当前帧（上一集末帧）复制下来挂冻结层。 */
    private void initiateFreezeCapture(String forVid) {
        if (playerView == null || player == null) return;
        final int seq = ++freezeSeq;
        HgFrameCapture.capture(playerView, main, bmp -> {
            if (bmp == null) return;
            if (seq != freezeSeq || !forVid.equals(switchPendingVid)) {
                bmp.recycle();
                return;
            }
            clearFreezeBitmap();
            freezeBitmap = bmp;
            if (freezeView != null) {
                freezeView.setImageBitmap(bmp);
                freezeView.setVisibility(View.VISIBLE);
            }
        });
    }

    /** 切集转圈闸：先压转圈，350ms 后仍未出首帧（还卡在缓冲）才亮。 */
    private void gateSpinForSwitch(String vid) {
        cancelSpinDelay();
        spin.setVisibility(View.GONE);
        spinDelayRunnable = () -> {
            spinDelayRunnable = null;
            if (!vid.equals(switchPendingVid) || player == null) return;
            if (player.getPlaybackState() == Player.STATE_READY) return;
            spin.setVisibility(View.VISIBLE);
        };
        main.postDelayed(spinDelayRunnable, 350);
    }

    private void cancelSpinDelay() {
        if (spinDelayRunnable != null) {
            main.removeCallbacks(spinDelayRunnable);
            spinDelayRunnable = null;
        }
    }

    private void clearFreezeBitmap() {
        if (freezeView != null) {
            freezeView.setImageBitmap(null);
            freezeView.setVisibility(View.GONE);
        }
        if (freezeBitmap != null) {
            freezeBitmap.recycle();
            freezeBitmap = null;
        }
    }

    /** 新集首帧已接上（或失败收口）：冻结帧撤下回收、在途抓帧作废。 */
    private void hideFreeze() {
        freezeSeq++;
        clearFreezeBitmap();
    }

    /**
     * 取链窗口（与刷剧页 HongguoFeedActivity.resolveWithWindow 同口径，
     * 2026-10-31）：服务端对 video_model 家族每身份只放行少数发，
     * 单集逐发会把放行发浪费在一集上。当前集未命中缓存且不在已发
     * 窗口内时，先用一发批量把「当前集起 20 集」取回落缓存再 resolve；
     * 窗内切集全走缓存、不另发网。成败都回 resolve，不堵播放。
     */
    private void resolveWithWindow(String vid, int contentType,
                                   DramaResolver.Listener listener) {
        if (detail == null) {
            resolver.resolve(vid, contentType, listener);
            return;
        }
        DramaVideoModel cached = DramaCache.get(vid);
        if (cached != null && cached.firstPlayableEntry() != null) {
            resolver.resolve(vid, contentType, listener);
            return;
        }
        List<DramaDetail.Episode> eps = detail.getEpisodes();
        int pos = -1;
        for (int i = 0; i < eps.size(); i++) {
            if (vid.equals(eps.get(i).getVideoId())) {
                pos = i;
                break;
            }
        }
        if (pos < 0 || (windowStart >= 0 && pos >= windowStart && pos < windowStart + 20)) {
            resolver.resolve(vid, contentType, listener);
            return;
        }
        windowStart = pos;
        int ct = detail.getContentType();
        List<DramaClient.ModelRequest> reqs = new ArrayList<>();
        for (int i = pos; i < Math.min(pos + 20, eps.size()); i++) {
            String ev = eps.get(i).getVideoId();
            if (ev != null) reqs.add(new DramaClient.ModelRequest(ev, ct));
        }
        if (reqs.isEmpty()) {
            resolver.resolve(vid, contentType, listener);
            return;
        }
        DramaClient.fetchModels(reqs, new HgCallback<Map<String, DramaVideoModel>>() {
            @Override
            public void onSuccess(Map<String, DramaVideoModel> r) {
                resolver.resolve(vid, contentType, listener);
            }

            @Override
            public void onError(int c, String m) {
                resolver.resolve(vid, contentType, listener);
            }
        }, false);
    }

    /**
     * 弹幕配置与竖屏页同一套档表（HgPanelLogic/HgStore）；横屏显示区域
     * 不走竖屏行数，走行高档（lineSpace）：档比折算滚动行数，缺省
     * QUARTER＝1/4 屏（官方横屏口径）。置顶/置底行数仍沿用竖屏行数键。
     */
    private androidx.media3.ui.danmaku.DanmakuConfig danmakuConfig() {
        int lines = HgStore.getDanmakuLines();
        float areaRatio = HgPanelLogic.danmakuAreaRatio(HgStore.getDanmakuLineSpaceIndex());
        androidx.media3.ui.danmaku.DanmakuConfig.Builder builder =
                androidx.media3.ui.danmaku.DanmakuConfig.DEFAULT.buildUpon()
                .setTransparency(HgPanelLogic.danmakuTransparency(HgStore.getDanmakuOpacity()))
                .setScrollSpeedFactor(HgPanelLogic.danmakuSpeedFactor(HgStore.getDanmakuSpeedIndex()))
                .setTextScale(HgPanelLogic.danmakuFontScale(HgStore.getDanmakuFontIndex()))
                .setTextSizeSp(HgPanelLogic.DM_TEXT_SIZE_SP)
                .setDurationMs(HgPanelLogic.DM_DURATION_MS_LANDSCAPE)
                .setMaxTopLines(lines)
                .setMaxBottomLines(lines);
        if (areaRatio < 0) {
            // SINGLE 档＝单行哨兵（HgPanelLogic.danmakuAreaRatio 定版）：滚动区钉 1 行
            builder.setMaxScrollLines(1);
        } else {
            // 其余档：滚动行数＝⌊可排轨道×屏高比⌋（引擎 TrackManager
            // configuredCount），maxScrollLines 置 0＝不另封顶
            builder.setScrollAreaRatio(areaRatio).setMaxScrollLines(0);
        }
        return builder.build();
    }

    // ---------- 弹幕（与竖屏页同源：HongguoDanmakuApi 落盘 XML） ----------

    private void startDanmaku(String vid) {
        danmakuVid = vid;
        dmItems.clear();
        if (playerView != null) playerView.getDanmakuController().clearItems();
        if (dmSend != null) {
            dmSend.setVisibility(HgStore.isDanmakuOn() ? View.VISIBLE : View.GONE);
        }
        if (!HgStore.isDanmakuOn() || seriesId == null || vid == null) return;
        final String sid = seriesId;
        safeExecute(() -> HongguoDanmakuApi.fetchToFile(sid, vid, 0, item -> {
            if (!vid.equals(danmakuVid)) return;
            loadDanmakuFile(vid, item);
        }));
    }

    private void loadDanmakuFile(String vid, Danmaku item) {
        if (item == null || item.getUrl() == null) return;
        safeExecute(() -> {
            List<androidx.media3.ui.danmaku.Danmaku> parsed =
                    HongguoFeedActivity.parseDanmakuXml(item.getRealUrl());
            if (parsed == null) return;
            runOnUiThread(() -> {
                if (!vid.equals(danmakuVid) || playerView == null) return;
                dmItems.clear();
                dmItems.addAll(parsed);
                playerView.getDanmakuController().setItems(new ArrayList<>(dmItems));
            });
        });
    }

    // ---------- 显隐与退出 ----------

    // ---------- 横屏评论面板（右侧框出、画面挤小） ----------

    private void openCommentPanel() {
        if (seriesId == null || curVid == null || commentPanel == null) return;
        // 与选集层互斥的反向补齐（showEpisodes 开选集前先收评论
        // 面板，此前开评论面板不收选集层——选集层开着时 chrome 恒
        // 隐、评论钮点不到，UI 上不可达，此为防御对齐：两层同为右
        // 侧停靠，任一开前先收另一层，账 fix168-comment-surfaces）
        if (episodePanelOpen) closeEpisodePanel();
        commentPanel.bind(seriesId, curVid);
        if (commentPanelOpen) return;
        commentPanelOpen = true;
        setChrome(false);
        animateStage(true);
    }

    private void closeCommentPanel() {
        if (!commentPanelOpen) return;
        commentPanelOpen = false;
        animateStage(false);
        pokeChrome();
    }

    /** 开合动画：挤占进度 0↔1，200ms 减速；播放面与面板同速联动。 */
    private void animateStage(boolean open) {
        if (stageAnimator != null) stageAnimator.cancel();
        final float from = stageSqueeze01;
        final float to = open ? 1f : 0f;
        if (from == to) {
            applyStage(to);
            return;
        }
        if (open && commentPanel != null) {
            commentPanel.view().setVisibility(View.VISIBLE);
        }
        android.animation.ValueAnimator va =
                android.animation.ValueAnimator.ofFloat(from, to);
        va.setDuration(200);
        va.setInterpolator(new android.view.animation.DecelerateInterpolator());
        va.addUpdateListener(a -> applyStage((float) a.getAnimatedValue()));
        stageAnimator = va;
        va.start();
    }

    /**
     * 画面框联动落位：播放面宽＝全宽−面板挤占，面板自右滑入同
     * 速跟随。只改播放面布局宽——不重挂播放器、不动 Surface
     * 归属，播放面在新框内按既有 FIT 自行重排（官方截图挤小后
     * 上下留黑即此形态）；其余全部 chrome（顶/底行、锁钮、评论
     * 钮、转圈、失败提示、冻结层）经 playerView 布局监听走
     * layoutChromeToFrame 按新框统一重算——锚系统一后本法不再
     * 单独挪任何一颗（旧写法在此给转圈 leftMargin＝−squeeze，
     * 整幅偏出挤小后框中心 0.5×面板宽，已摘）。
     */
    private void applyStage(float frac) {
        stageSqueeze01 = frac;
        if (stageRoot == null || playerView == null) return;
        int rootW = stageRoot.getWidth();
        if (rootW <= 0) rootW = getResources().getDisplayMetrics().widthPixels;
        int panelW = Math.round(rootW * HgLandCommentsPanel.WIDTH_RATIO);
        int squeeze = Math.round(panelW * frac);
        setStageWidth(playerView, rootW - squeeze);
        if (commentPanel != null) {
            View pv = commentPanel.view();
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pv.getLayoutParams();
            if (lp.width != panelW) {
                lp.width = panelW;
                pv.setLayoutParams(lp);
            }
            pv.setTranslationX(panelW - squeeze);
            pv.setVisibility(frac > 0f || commentPanelOpen ? View.VISIBLE : View.GONE);
        }
    }

    private void setStageWidth(View v, int w) {
        if (v == null) return;
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp != null && lp.width != w) {
            lp.width = w;
            v.setLayoutParams(lp);
        }
    }

    private void onSurfaceTap() {
        if (commentPanelOpen) return; // 面板开着时画面点按不翻 chrome（面板自有关闭口）
        if (episodePanelOpen) return; // 选集层同理（层自有关闭口）
        if (suppressTap) {
            suppressTap = false;
            return;
        }
        if (locked) {
            lockBtn.setVisibility(lockBtn.getVisibility() == View.VISIBLE
                    ? View.GONE : View.VISIBLE);
            return;
        }
        boolean show = topBar.getVisibility() != View.VISIBLE;
        setChrome(show);
        if (show) pokeChrome();
    }

    private void setChrome(boolean show) {
        // 评论面板/选集层开着时 chrome 恒隐（官方同形态：画面区只留视频）
        if (commentPanelOpen || episodePanelOpen) show = false;
        int vis = show ? View.VISIBLE : View.GONE;
        topBar.setVisibility(vis);
        bottomBar.setVisibility(vis);
        if (commentBox != null) commentBox.setVisibility(vis);
        if (!locked) lockBtn.setVisibility(vis);
        if (dmSend != null) {
            dmSend.setVisibility(show && HgStore.isDanmakuOn() && !locked
                    ? View.VISIBLE : View.GONE);
        }
    }

    /** 点发弹幕入口（#21 乙＋账号终定口径，与竖屏同门）：未登录
     * 跳登录页，登录后开编辑层；发表走 DramaDanmakuSender 定版
     * 载荷，成功把自己这条并入弹幕列表回显。 */
    private void onDanmakuSendTap() {
        if (!com.fongmi.android.tv.hongguo.api.HgAccountStore.isLoggedIn()) {
            HongguoLoginActivity.start(this);
            return;
        }
        if (curVid == null || seriesId == null || player == null) return;
        final String vid = curVid;
        final String sid = seriesId;
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
                                    if (vid.equals(curVid) && playerView != null) {
                                        dmItems.add(new androidx.media3.ui.danmaku.Danmaku(
                                                text, offset,
                                                androidx.media3.ui.danmaku.Danmaku.TYPE_SCROLL,
                                                Color.WHITE, 0f, 0, null, 0));
                                        playerView.getDanmakuController()
                                                .setItems(new java.util.ArrayList<>(dmItems));
                                    }
                                    Toast.makeText(HongguoLandActivity.this,
                                            "已发送", Toast.LENGTH_SHORT).show();
                                });
                            }

                            @Override
                            public void onError(int c, String m) {
                                runOnUiThread(() -> {
                                    ok.setEnabled(true);
                                    Toast.makeText(HongguoLandActivity.this,
                                            "没发出去，再试试", Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
            });
        });
        dialog.show();
    }

    /**
     * 控件按画面框落位（#17 立框锚，2026-10-31 锚系统一收口）：
     * 横屏全部 chrome 只认一个坐标源——实流尺寸
     * （onVideoSizeChanged）与播放面尺寸算出的 FIT 画面框
     * （left/top/W/H 四值），水平＋垂直都按框算。此前三套锚
     * 并存是「所有按钮都错位」的总差源（账
     * hongguo-land-layout-audit-20261031）：顶/底行与锁钮框锚、
     * 评论钮/转圈/失败提示屏锚挂 root 没人管、弹层屏级。本法
     * 收口为一套：
     * - 顶/底行：宽与左右边距收进框内（左右端再并入挖孔让位，
     *   取框缘与挖孔缘的内侧交界）；纵向贴框上下缘，系统栏让
     *   位与框边距取大不叠加（框已让开的黑带里不再重复让）。
     * - 锁钮/评论钮：左右成对同规则——锁贴框左缘内 10dp、评论
     *   贴框右缘内 10dp，挖孔让位同法；此前评论钮屏锚，黑边内
     *   容时悬在画面外与底行选集上下错开约 6.9% 屏宽。
     * - 转圈：按框中心落位（边距＝框中心−半钮）；评论面板挤压
     *   时框随播放面宽重算，转圈自动跟到挤小后的框中心（旧
     *   applyStage 的整幅 −squeeze 偏移已摘）。
     * - 失败提示：盒收进框内，文字在框内居中（旧为全屏居中）。
     * - 切集冻结层：盒与框逐字同位——抓帧位图就是视频面本身
     *   （HgFrameCapture 只取 Surface/Texture 面），旧写法全
     *   stage 尺寸 FIT_XY 把帧横向拉满、冻结瞬间画面胀一下。
     * - 发弹幕输入条在底行内（件五），随底行框重排走。
     * 播放面尺寸为 0/实流未到时框＝播放面全幅，不除零、不负宽。
     */
    private void layoutChromeToFrame() {
        if (playerView == null) return;
        int vw = playerView.getWidth();
        int vh = playerView.getHeight();
        if (vw <= 0 || vh <= 0) return;
        int frameLeft = 0;
        int frameTop = 0;
        int frameW = vw;
        int frameH = vh;
        if (videoW > 0 && videoH > 0) {
            float scale = Math.min((float) vw / videoW, (float) vh / videoH);
            frameW = Math.round(videoW * scale);
            frameH = Math.round(videoH * scale);
            frameLeft = (vw - frameW) / 2;
            frameTop = (vh - frameH) / 2;
        }
        // 顶/底行的有效横向区间：框区间与挖孔让位取交集
        int effLeft = Math.max(frameLeft, cutoutLeft);
        int effRight = Math.min(frameLeft + frameW, vw - cutoutRight);
        int effW = Math.max(0, effRight - effLeft);
        if (topBar != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) topBar.getLayoutParams();
            lp.width = effW;
            lp.leftMargin = effLeft;
            lp.topMargin = Math.max(frameTop, barsTop);
            topBar.setLayoutParams(lp);
        }
        if (bottomBar != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) bottomBar.getLayoutParams();
            lp.width = effW;
            lp.leftMargin = effLeft;
            lp.bottomMargin = Math.max(vh - frameTop - frameH, barsBottom);
            bottomBar.setLayoutParams(lp);
        }
        if (lockBtn != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) lockBtn.getLayoutParams();
            lp.leftMargin = Math.max(frameLeft, cutoutLeft) + dp(10);
            lockBtn.setLayoutParams(lp);
        }
        if (commentBox != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) commentBox.getLayoutParams();
            int want = (vw - effRight) + dp(10);
            if (lp.rightMargin != want) {
                lp.rightMargin = want;
                commentBox.setLayoutParams(lp);
            }
        }
        if (spin != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) spin.getLayoutParams();
            int wantL = Math.max(0, frameLeft + frameW / 2 - dp(20));
            int wantT = Math.max(0, frameTop + frameH / 2 - dp(20));
            if (lp.leftMargin != wantL || lp.topMargin != wantT) {
                lp.leftMargin = wantL;
                lp.topMargin = wantT;
                spin.setLayoutParams(lp);
            }
        }
        if (failView != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) failView.getLayoutParams();
            if (lp.width != frameW || lp.height != frameH
                    || lp.leftMargin != frameLeft || lp.topMargin != frameTop) {
                lp.width = frameW;
                lp.height = frameH;
                lp.leftMargin = frameLeft;
                lp.topMargin = frameTop;
                failView.setLayoutParams(lp);
            }
        }
        if (freezeView != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) freezeView.getLayoutParams();
            if (lp.width != frameW || lp.height != frameH
                    || lp.leftMargin != frameLeft || lp.topMargin != frameTop) {
                lp.width = frameW;
                lp.height = frameH;
                lp.leftMargin = frameLeft;
                lp.topMargin = frameTop;
                freezeView.setLayoutParams(lp);
            }
        }
    }

    /**
     * 评论数随集补拉（与竖屏 ensureCommentCount 同口同法）：计数签
     * 有数显数、无数回「评论」原文（竖屏右列计数签同口径）。
     */
    private void refreshCommentCount() {
        if (commentCount == null || seriesId == null || curVid == null) return;
        final String sid = seriesId;
        final String vid = curVid;
        commentCount.setText("评论");
        safeExecute(() -> {
            com.fongmi.android.tv.api.HongguoCommentApi.Page page =
                    com.fongmi.android.tv.api.HongguoCommentApi.episodeComments(sid, vid, "");
            runOnUiThread(() -> {
                if (commentCount == null || !vid.equals(curVid)) return;
                if (page != null && page.total > 0) {
                    commentCount.setText(
                            HongguoFeedActivity.HgTimeTextCount.format(page.total));
                }
            });
        });
    }

    private void pokeChrome() {
        main.removeCallbacks(hideChrome);
        main.postDelayed(hideChrome, 3500);
    }

    private void exitLand() {
        if (exited) return;
        exited = true;
        parkBack();
        finish();
    }

    private void parkBack() {
        if (engine == null || player == null) return;
        DramaPlayerPool.Bag bag = new DramaPlayerPool.Bag();
        bag.seriesId = seriesId;
        bag.vid = curVid;
        bag.episodeIndex = curEp;
        bag.positionMs = player.getCurrentPosition();
        bag.playWhenReady = player.getPlayWhenReady();
        bag.danmakuOn = HgStore.isDanmakuOn();
        bag.speed = player.getPlaybackParameters().speed;
        bag.seriesTitle = seriesTitle;
        bag.contentType = contentType;
        player.removeListener(playerListener);
        if (playerView != null) playerView.setPlayer(null);
        DramaPlayerPool.park(bag, engine);
        parkedOut = true;
        engine = null;
        player = null;
    }

    @Override
    public void finish() {
        super.finish();
        HgTransition.applyFadeExit(this);
    }

    @Override
    public void onBackPressed() {
        if (episodePanelOpen) {
            closeEpisodePanel();
            return;
        }
        if (commentPanelOpen) {
            closeCommentPanel();
            return;
        }
        exitLand();
    }

    /** 件六：系统自动旋转开关现读，读失败按关（与竖屏页同口径）。 */
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
        // #18：横屏是本页自己强制的（机主系统自动旋转关着也一样）；
        // 切后台再回来时把强制横屏重新申明一遍，不许被竖屏页带回去。
        setRequestedOrientation(
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE);
        // 件六：页内强制横屏不动（上一段口径）；传感器退横屏则随
        // 系统开关——关＝不武装，退出走返回键手动路。
        if (orientationListener != null) {
            if (systemAutoRotateOn()) orientationListener.enable();
            else orientationListener.disable();
        }
    }

    @Override
    protected void onPause() {
        if (orientationListener != null) orientationListener.disable();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        main.removeCallbacks(ticker);
        main.removeCallbacks(hideChrome);
        cancelSpinDelay();
        if (stageAnimator != null) stageAnimator.cancel();
        if (episodePanel != null) episodePanel.view().animate().cancel();
        if (commentPanel != null) commentPanel.destroy();
        clearFreezeBitmap();
        if (!exited) parkBack(); // 异常退出兜底：把播放器泊回，不许泄漏
        // 引擎泊回后销毁关流的口径（交接缝定根）：横屏里切过集时，
        // 引擎正播的流是本页 resolver 注册的——整表 closeAll 会摘
        // 规格＋关活流，把竖屏页领回的续播当场掐断（缓冲耗尽即断
        // 流报错）。泊出过就只关其余集的流，当前集那条留给领回页
        // reclaim 时 adoptStream 接账；没泊出（早退/无引擎）整表关。
        if (parkedOut) resolver.closeAllExcept(curVid);
        else resolver.closeAll();
        // 先摘本页主队列残留回调、后关池（P0 对齐，同竖屏页）
        main.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        super.onDestroy();
    }
}
