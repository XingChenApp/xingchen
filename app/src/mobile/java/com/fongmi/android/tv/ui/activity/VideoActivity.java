package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.mpvplayer.MpvPlayer;
import androidx.media3.ui.PlayerView;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.ChangeBounds;
import androidx.transition.TransitionManager;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.request.transition.Transition;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.DanmakuApi;
import com.fongmi.android.tv.api.HongguoCommentApi;
import com.fongmi.android.tv.api.HongguoDanmakuApi;
import com.fongmi.android.tv.hongguo.drama.DramaCache;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.hk.HkDetail;
import com.fongmi.android.tv.api.hk.HkDetailBridge;
import com.fongmi.android.tv.api.hk.HkFavorite;
import com.fongmi.android.tv.api.hk.HkFavoriteStore;
import com.fongmi.android.tv.api.hk.HkItem;
import com.fongmi.android.tv.api.hk.HkProxyPrewarm;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.api.hk.HkRuleManager;
import com.fongmi.android.tv.read.ReadingDetailDivert;
import com.fongmi.android.tv.read.ReadingDetailJudge;
import com.fongmi.android.tv.read.ReadingGate;
import com.fongmi.android.tv.read.ReadingManual;
import com.fongmi.android.tv.read.ReadingRouter;
import com.fongmi.android.tv.read.ReadingSession;
import com.fongmi.android.tv.bean.CastVideo;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.SkipSegment;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.databinding.DialogPlayerMoreBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.emby.EmbyApi;
import com.fongmi.android.tv.emby.EmbyItem;
import com.fongmi.android.tv.emby.EmbyNavigator;
import com.fongmi.android.tv.emby.EmbySpider;
import com.fongmi.android.tv.emby.EmbyStore;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.CustomTarget;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.playback.PlaybackEventCollector;
import com.fongmi.android.tv.playback.PlaybackOrientation;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.player.karaoke.KaraokeController;
import com.fongmi.android.tv.player.karaoke.KaraokePitchTrackGenerator;
import com.fongmi.android.tv.player.karaoke.KaraokeResult;
import com.fongmi.android.tv.player.karaoke.KaraokeTrackRepository;
import com.fongmi.android.tv.player.lyrics.AudioPlaylistStore;
import com.fongmi.android.tv.player.lyrics.LyricsController;
import com.fongmi.android.tv.player.lyrics.LyricsLine;
import com.fongmi.android.tv.player.lyrics.LyricsRepository;
import com.fongmi.android.tv.player.lyrics.LyricsRequest;
import com.fongmi.android.tv.player.lyrics.LyricsResult;
import com.fongmi.android.tv.player.lut.LutPreset;
import com.fongmi.android.tv.player.lut.LutSetting;
import com.fongmi.android.tv.player.lut.LutStore;
import com.fongmi.android.tv.player.mpv.MpvConfigStore;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.LyricsSetting;
import com.fongmi.android.tv.setting.PlayerButtonSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.subtitle.SubtitleManager;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.ui.MusicVodRouter;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.EpisodeGroupAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.adapter.ParseAdapter;
import com.fongmi.android.tv.ui.adapter.QualityAdapter;
import com.fongmi.android.tv.ui.adapter.QuickAdapter;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.hk.HkFavoriteDialogs;
import com.fongmi.android.tv.ui.custom.AudioPlayerBackgroundDrawable;
import com.fongmi.android.tv.ui.custom.CustomKeyDown;
import com.fongmi.android.tv.ui.custom.CustomMovement;
import com.fongmi.android.tv.ui.custom.CustomSeekView;
import com.fongmi.android.tv.ui.custom.KaraokeResultView;
import com.fongmi.android.tv.ui.custom.PlayerOsdController;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.custom.TopProgressRule;
import com.fongmi.android.tv.ui.custom.TopProgressView;
import com.fongmi.android.tv.ui.dialog.CastDialog;
import com.fongmi.android.tv.ui.dialog.ChoiceDialog;
import com.fongmi.android.tv.ui.dialog.CodecCapabilityDialog;
import com.fongmi.android.tv.ui.dialog.ControlDialog;
import com.fongmi.android.tv.ui.dialog.DanmakuDialog;
import com.fongmi.android.tv.ui.dialog.DanmakuSettingDialog;
import com.fongmi.android.tv.ui.dialog.EmbyQualityDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeGridDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeListDialog;
import com.fongmi.android.tv.ui.dialog.HongguoCommentDialog;
import com.fongmi.android.tv.ui.dialog.InfoDialog;
import com.fongmi.android.tv.ui.dialog.LutPanelDialog;
import com.fongmi.android.tv.ui.dialog.PlayerKernelDialog;
import com.fongmi.android.tv.ui.dialog.QuickSearchDialog;
import com.fongmi.android.tv.ui.dialog.ReceiveDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleSearchDialog;
import com.fongmi.android.tv.ui.dialog.TitleDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.ui.dialog.VideoContentDialog;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.XcFadeTransition;
import com.fongmi.android.tv.utils.download.DownloadManager;
import com.fongmi.android.tv.utils.EpisodeTitleCompact;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.CoverRatioCache;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.PiP;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Timer;
import com.fongmi.android.tv.utils.Traffic;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.utils.VodDetailCache;
import com.github.catvod.crawler.SpiderDebug;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import com.fongmi.android.tv.event.PlaySourceUpdateEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fongmi.android.tv.exception.ExtractException;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.MoreExecutors;

public class VideoActivity extends PlaybackActivity implements Clock.Callback, CustomKeyDown.Listener, TrackDialog.Listener, ControlDialog.Listener, DanmakuDialog.Host, FlagAdapter.OnClickListener, EpisodeAdapter.OnClickListener, EpisodeGroupAdapter.OnClickListener, QualityAdapter.OnClickListener, QuickAdapter.OnClickListener, ParseAdapter.OnClickListener, CastDialog.Listener, InfoDialog.Listener {

    /**
     * 播放器是否存活（对齐海阔 8.83 PlayerChooser.hasPlayer）。
     * 为 true 时点播放走 EventBus 原地换源，不 startActivity，避免多跳转一个页面。
     * 多实例交叠时以 sPlayerOwner 为准：只有属主实例的 onDestroy 才许清标志，
     * 旧实例后销毁不许把新实例存活期的标志误清。
     */
    public static volatile boolean hasPlayer = false;

    private static volatile VideoActivity sPlayerOwner;

    private static final String SIZE_TAG = "MPV_SIZE";
    private static final long LYRICS_OFFSET_MIN_MS = -5000L;
    private static final long LYRICS_OFFSET_MAX_MS = 5000L;
    private static final long LYRICS_OFFSET_STEP_MS = 500L;
    private static final long KARAOKE_DELAY_MIN_MS = -1000L;
    private static final long KARAOKE_DELAY_MAX_MS = 1000L;
    private static final long KARAOKE_DELAY_STEP_MS = 100L;
    private static final int LYRICS_TAB_LYRICS = 0;
    private static final int LYRICS_TAB_KARAOKE = 1;
    private static final int LYRICS_TAB_TRACK = 2;
    private static final int AUDIO_QUEUE_TAB_CURRENT = 0;
    private static final int AUDIO_QUEUE_TAB_SEARCH = 1;
    private static final String STATE_KARAOKE_RESULT = "karaoke_result";
    private static final String STATE_KARAOKE_RESULT_ACTION = "karaoke_result_action";
    private static final int KARAOKE_RESULT_ACTION_NONE = 0;
    private static final int KARAOKE_RESULT_ACTION_NEXT = 1;
    private static final int KARAOKE_RESULT_ACTION_NEXT_SILENT = 2;
    private static final int KARAOKE_RESULT_ACTION_FINISH = 3;
    private static final int KARAOKE_RESULT_ACTION_SYSTEM_BACK = 4;
    private static final int SHEET_BUTTON_RADIUS_DP = 6;
    private static final int SHEET_SEGMENT_RADIUS_DP = 5;
    private static final int SHEET_TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int SHEET_TEXT_SECONDARY = 0xD9FFFFFF;
    private static final int SHEET_TEXT_MUTED = 0x8CFFFFFF;
    private static final int SHEET_CONTROL_BG = 0x1FFFFFFF;
    private static final int SHEET_CONTROL_BG_SELECTED = 0x3DFFFFFF;
    private static final int SHEET_CONTROL_BG_SUBTLE = 0x12FFFFFF;
    private static final int SHEET_CONTROL_STROKE = 0x24FFFFFF;
    private static final int SHEET_CONTROL_STROKE_SELECTED = 0x4DFFFFFF;

    private ActivityVideoBinding mBinding;
    private ViewGroup.LayoutParams mFrameParams;
    private int mFrameHeight;
    private Observer<Result> mObserveDetail;
    private Observer<Result> mObserveSearch;
    private EpisodeAdapter mEpisodeAdapter;
    private EpisodeGroupAdapter mEpisodeGroupAdapter;
    private SpaceItemDecoration mEpisodeDecoration;
    private QualityAdapter mQualityAdapter;
    private QuickAdapter mQuickAdapter;
    private QuickSearchDialog mQuickSearchDialog;
    private String mQuickSearchKeyword;
    private ParseAdapter mParseAdapter;
    private LyricsController mLyrics;
    private KaraokeController mKaraoke;
    private boolean mAudioStageVisible;
    private boolean mAudioLightEffectAnimated;
    private boolean mKaraokeResultShown;
    private boolean mUserExitedFullscreenForThisVideo;
    private boolean mShortDramaMode;
    // 红果评论/弹幕（壳子直抓）：识别闸通过才预抓，预抓真拿到评论才露入口；状态按 historyKey 隔离
    private final ExecutorService mHongguoExecutor = Executors.newSingleThreadExecutor();
    private String mHongguoSeriesId = "";
    private boolean mHongguoGate;
    private boolean mCommentPrefetching;
    private boolean mCommentAvailable;
    private String mCommentPrefetchKey = "";
    private HongguoCommentApi.Page mCommentSeriesPage;
    private HongguoCommentApi.Page mCommentEpisodePage;
    private String mCommentEpisodeVid = "";
    private String mHongguoDanmakuVid = "";
    private String mHongguoDanmakuPending = "";
    private String mHongguoDanmakuPrefetchVid = "";
    private boolean mContentExpanded = false;
    // 外部 VIEW 本地媒体认领中（独立外部门投递）：权限门未回前 checkId 让路
    private boolean mExternalClaimPending;
    private int mKaraokeResultAction;
    private KaraokeResult mPendingKaraokeResult;
    private AlertDialog mKaraokeResultDialog;
    private static final java.util.Map<String, String> HK_EPISODES_STORE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicInteger HK_EPISODES_SEQ = new java.util.concurrent.atomic.AtomicInteger();
    // 选集仓只进不出会无限涨（深挖 L-05）：按放入顺序保留最近 16 条，淘汰最旧
    private static final java.util.Deque<String> HK_EPISODES_ORDER = new java.util.concurrent.ConcurrentLinkedDeque<>();
    private static final int HK_EPISODES_MAX = 16;

    private static void parkHkEpisodes(String ref, String episodesJson) {
        HK_EPISODES_STORE.put(ref, episodesJson);
        HK_EPISODES_ORDER.remove(ref);
        HK_EPISODES_ORDER.addLast(ref);
        while (HK_EPISODES_STORE.size() > HK_EPISODES_MAX) {
            String eldest = HK_EPISODES_ORDER.pollFirst();
            if (eldest == null) break;
            HK_EPISODES_STORE.remove(eldest);
        }
    }
    private boolean mSuppressKaraokeResultAction;
    private boolean mRestoringConfigurationPlayback;
    /** 阶段二自动播点火期间为真：点火只负责起播，抑制 shouldEnterFullscreen 的进全屏副作用（finally 复位，手动点集路径不受影响）。 */
    private boolean mAutoIgniting;
    private boolean mSkipKaraokeTrackAutoLoad;
    private BottomSheetDialog mLyricsResultDialog;
    private BottomSheetDialog mAudioQueueDialog;
    private BottomSheetDialog mKaraokePitchDialog;
    private ProgressBar mKaraokePitchProgress;
    private TextView mKaraokePitchMessage;
    private Future<?> mKaraokePitchFuture;
    private AtomicBoolean mKaraokePitchCancel;
    private ObjectAnimator mAudioCoverAnimator;
    private LinearLayout mLyricsResultList;
    private RecyclerView mAudioQueueList;
    private AudioQueueAdapter mAudioQueueAdapter;
    private LinearLayout mAudioQueueSearchList;
    private TextView mAudioQueueStatus;
    private List<LyricsResult> mLyricsSearchResults;
    private String mLyricsSearchKeyword;
    private String mLyricsLastSearchSignature;
    private String mLyricsLastSearchKeyword;
    private String mLyricsSelectedResultKey;
    private String mDetailLyrics;
    private String mInlineLyrics;
    private long mLyricsLoopLastPlayerPosition = C.TIME_UNSET;
    private boolean mLyricsLoopLastPlaying;
    private String mPlaybackEpisodeKey;
    private String mArtworkRequestUrl;
    private String mArtworkRequestOwner;
    private Vod mPendingDetailVod;
    private boolean mPendingDetailDeferLists;
    private long detailFinishTime;
    private long detailOpenTime; // 详情页打开时刻（checkId 入口），供「点开→发起请求」计时
    private boolean markEpisodeRender;
    private Vod mAiVod; // P3：当前影片详情，供 AI 剧情问答取元数据
    private boolean mDetailCacheShown;
    private Result mLastPlayerResult;
    // 服务未就绪窗口的待点火请求（不是结果槽）：服务连上后由 onServiceConnected
    // 点火重跑解析，结果到时服务已在，不进槽。上游无槽结构，请求级暂存是
    // 绕不过 PlaybackActivity.player() 直取 mService.player() 的最小等效处理。
    private String mPendingPlayKey;
    private String mPendingPlayFlag;
    private String mPendingPlayUrl;
    private Result mPreparsedHistoryResult;
    private String mPreparsedHistorySpec;
    private ListenableFuture<Result> mPreparsedHistoryFuture; // 在途预解析：getPlayer 可等待复用，消灭双跑
    private final NextEpisodePreloader mNextPreloader = new NextEpisodePreloader();
    private int mAudioArtworkColor = Color.rgb(55, 45, 68);
    private final Map<String, String> mAudioQueueFlags = new HashMap<>();
    private final Map<String, String> mAudioQueueTitles = new HashMap<>();
    private final Map<String, String> mAudioQueueArtists = new HashMap<>();
    private final Map<String, String> mAudioQueuePics = new HashMap<>();
    private final Map<String, String> mAudioQueueLyrics = new HashMap<>();
    private Map<String, View> mActionButtons;
    private final List<View> mCustomActionViews = new ArrayList<>();
    private HorizontalScrollView mCustomLeftButtons;
    private HorizontalScrollView mCustomRightButtons;
    private HorizontalScrollView mCustomPortraitButtons;
    private LinearLayout mCustomLeftButtonRow;
    private LinearLayout mCustomRightButtonRow;
    private LinearLayout mCustomPortraitButtonRow;
    private SiteViewModel mViewModel;
    private FlagAdapter mFlagAdapter;
    private PlayerOsdController mOsd;
    private CustomKeyDown mKeyDown;
    // 短剧滑动预览层：盖在视频面上（控件/进度层之下），只在跟手滑动期间露相邻集封面；切集成立即撤，不参与切集装载期
    private ViewGroup mSwitchCover;
    private ImageView mSwitchCoverImage;
    private TextView mSwitchCoverLabel;
    private int mSwitchCoverDir;
    private List<String> mBroken;
    private History mHistory;
    private boolean fullscreen;
    private boolean initAuto;
    private boolean autoMode;
    private boolean revealManualSearch;
    private boolean useParse;
    private boolean rotate;
    private boolean detailHealthRecorded;
    private boolean playHealthRecorded;
    private boolean playerKernelSwitchRefreshing;
    private boolean decodeSwitchRefreshing;
    private int deferredFullscreenOrientation = Configuration.ORIENTATION_UNDEFINED;
    private int mEpisodeSpanCount;
    private int mStatusBarInset;
    private int mEpisodeBottomInset;
    private int mNavigationRightInset;
    private int mLyricsSearchSeq;
    private int mAudioQueueSearchSeq;
    private int mAudioPlaylistCurrentIndex = -1;
    private int mEpisodeMaxHeight;
    private int mAudioBackgroundRandomNonce;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private Clock mClock;
    // ---- Emby 播放钩子（实测语义见 VERIFY.md F2；上报全部走子线程，不阻塞播放） ----
    private boolean mEmbyPlayingReported;
    private boolean mEmbyEndedReported;
    private boolean mEmbyStoppedReported;
    private long mEmbyLastProgressMs;
    private List<Sub> mEmbySubs;
    private int mEmbySubSelected = -1;
    /** Emby 直链 410 自救：同一集重试计数（上限 2 次）＋归属键（换集自动清零）。 */
    private int mEmby410Retry;
    private String mEmby410Key = "";
    /** 当前在播的 Emby 版本选项（410 重取时按"同一源→同分辨率档"优先匹配）。 */
    private EmbyApi.PlayOption mEmbyCurrentOpt;
    /**
     * 详情页 Emby 画质行：当前集的 MediaSources 候选（后台拉的）与用户选择。
     * key = sid:iid + "|D"(直放线路) / "|T"(转码线路)；选择只活在本次详情会话。
     */
    private List<EmbyApi.PlayOption> mEmbyQOptions;
    private final Map<String, EmbyApi.PlayOption> mEmbyQPick = new HashMap<>();
    private String mEmbyQKey = "";
    private long mEmbyQSeq = 0;
    private boolean mEmbyQShowing = false;
    /** 详情页 Emby 收藏按钮态：null=还没拉到，防详情切换串台用 gen。 */
    private Boolean mEmbyFavState;
    private String mEmbyFavSid;
    private String mEmbyFavIid;
    private int mEmbyFavGen;
    /** 详情页 Emby 标记已看按钮态：与收藏共用一次 getItem 拉取。 */
    private Boolean mEmbyPlayedState;
    private MpvPlayer mDiscMenuPlayer;
    private MpvPlayer mCustomButtonPlayer;
    private final Runnable mCustomButtonStateListener = this::updateCustomButtonStates;
    private final Runnable mDiscMenuStateListener = this::updateDiscMenuTools;
    private PiP mPiP;
    private float mSpeedUpOriginSpeed = 1f;
    private boolean mSpeedUpActive = false;
    private String mContextWallUrl;
    private String mContextWallLockedUrl;
    private String playHealthKey;
    private long detailStartTime;
    private long mDetailParseSeq;
    private long playerStartTime;
    private boolean pendingLutImport;
    private boolean skipPausePiP;

    private final ActivityResultLauncher<Intent> mLutDir = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        LutStore.setUserDir(result.getData().getData(), result.getData().getFlags());
        Notify.show(R.string.lut_directory_selected);
        if (hasLutQuick()) mBinding.lutQuick.refreshList();
        if (pendingLutImport) {
            pendingLutImport = false;
            chooseLutFile();
        }
    });

    private final ActivityResultLauncher<Intent> mLutFile = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        String path = FileChooser.getPathFromUri(result.getData().getData());
        if (TextUtils.isEmpty(path)) {
            Notify.show(R.string.lut_import_failed);
            return;
        }
        Task.execute(() -> {
            try {
                LutPreset preset = LutStore.importFile(path);
                App.post(() -> {
                    Notify.show(R.string.lut_imported);
                    if (isFullscreen() && hasLutQuick()) mBinding.lutQuick.selectImported(preset, player(), mBinding.exo, this::onLutChanged);
                    else onLutSelected(preset);
                });
            } catch (Exception e) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "import failed path=%s error=%s", path, e.getMessage());
                App.post(() -> Notify.show(Notify.getError(R.string.lut_import_failed, e)));
            }
        });
    });

    private final ActivityResultLauncher<Intent> mKaraokeTrackFile = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null || service() == null) return;
        String path = FileChooser.getPathFromUri(result.getData().getData());
        if (TextUtils.isEmpty(path)) {
            Notify.show(R.string.player_karaoke_track_import_failed);
            return;
        }
        Task.execute(() -> {
            KaraokeTrackRepository.ImportResult imported;
            try {
                File file = new File(path);
                imported = KaraokeTrackRepository.importFile(player(), file);
            } catch (Exception e) {
                imported = KaraokeTrackRepository.ImportResult.fail(e.getMessage());
            }
            KaraokeTrackRepository.ImportResult finalImported = imported;
            App.post(() -> onKaraokeTrackImported(finalImported));
        });
    });

    public static void push(FragmentActivity activity, String text) {
        if (FileChooser.isValid(activity, Uri.parse(text))) file(activity, FileChooser.getPathFromUri(Uri.parse(text)));
        else start(activity, Sniffer.getUrl(text));
    }

    public static void file(FragmentActivity activity, String path) {
        if (TextUtils.isEmpty(path)) return;
        String name = new File(path).getName();
        start(activity, SiteApi.PUSH, "file://" + path, name);
    }

    public static void cast(Activity activity, History history) {
        start(activity, history.getSiteKey(), history.getVodId(), history.getVodName(), history.getVodPic(), null, history.getWallPic());
    }

    public static void collect(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null, true);
    }

    /**
     * 海阔分支入口（M3）：单页点条目打开星辰详情页。
     * extras: hk_rule=规则名, hk_item_url=条目URL；key 命名空间为 hk_&lt;规则名&gt;。
     */
    public static void startHk(Activity activity, String ruleTitle, String itemUrl, String name, String pic) {
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("key", HkDetailBridge.siteKey(ruleTitle));
        intent.putExtra("id", itemUrl);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("hk_rule", ruleTitle);
        intent.putExtra("hk_item_url", itemUrl);
        activity.startActivity(intent);
    }

    /**
     * 海阔 V4 直接播放：跳过详情页，选集直达播放器。
     * 解析走 SiteApi.playerContent 的 hk_ 拦截（HkRouter.play），headers 保留。
     */
    public static void startHkPlay(Activity activity, String ruleTitle, String lineName, String episodeUrl, String episodeName, String name, String pic) {
        startHkPlay(activity, ruleTitle, lineName, episodeUrl, episodeName, name, pic, "", 0);
    }

    /**
     * 海阔直接播放（带选集）：episodesJson 为 [{name,url,pic}]，selIdx 为当前集下标。
     * 播放器内显示线路/选集（官方 getChapters：列表条目即选集）。
     */
    public static void startHkPlay(Activity activity, String ruleTitle, String lineName, String episodeUrl, String episodeName, String name, String pic, String episodesJson, int selIdx) {
        // 电台先于音乐问（识别表里「电台」同在音乐关键词内，顺序反了电台永远进不了听书）
        if (com.fongmi.android.tv.ui.HkRadioRouter.routeHk(activity, ruleTitle, lineName, episodeUrl, episodeName, name, pic, episodesJson, selIdx)) return;
        if (MusicVodRouter.routeHk(activity, ruleTitle, lineName, episodeUrl, name, pic, episodesJson, selIdx)) return;
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("key", HkDetailBridge.siteKey(ruleTitle));
        intent.putExtra("id", episodeUrl);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("hk_rule", ruleTitle);
        intent.putExtra("hk_line", lineName);
        intent.putExtra("hk_episode", episodeName);
        intent.putExtra("hk_direct_play", true);
        // 选集串过大会把 startActivity 的 Binder 事务撑爆（河马剧场点击播放崩溃
        // "Failure from system" 真机实证）：大的先停进程内仓、Intent 只带凭据，
        // 与原版 putTempRule 同思路。
        if (episodesJson != null && episodesJson.length() > 100_000) {
            String ref = "e" + HK_EPISODES_SEQ.incrementAndGet();
            parkHkEpisodes(ref, episodesJson);
            intent.putExtra("hk_episodes", "");
            intent.putExtra("hk_episodes_ref", ref);
        } else {
            intent.putExtra("hk_episodes", episodesJson == null ? "" : episodesJson);
        }
        intent.putExtra("hk_episode_idx", selIdx);
        // 对齐海阔 8.83：播放器已在就走 EventBus 原地换源，不 startActivity，避免多跳转一个页面
        if (hasPlayer) {
            EventBus.getDefault().post(new PlaySourceUpdateEvent(activity, intent));
            return;
        }
        activity.startActivity(intent);
    }

    /**
     * txt 混合链接播放：episodesJson 为 [{name,url}]，走通用选集 UI + PUSH 播放链。
     * 需求（2026-10-10）：txt 文件导入播放（磁力/HTTP/m3u8/ed2k 混合）。
     * 切集走 playerContent(PUSH) → Source.fetch 解析器（magnet/ed2k/thunder 等）。
     */
    public static void startTxt(Activity activity, String name, String episodesJson) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray(episodesJson);
            if (arr.length() == 0) return;
            Intent intent = new Intent(activity, VideoActivity.class);
            intent.putExtra("key", SiteApi.PUSH);
            intent.putExtra("id", arr.optJSONObject(0).optString("url"));
            intent.putExtra("name", name);
            intent.putExtra("txt_episodes", episodesJson);
            intent.putExtra("txt_episode_idx", 0);
            activity.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 本地媒体文件直达播放：单集、线路名「本地」、标题与集名均为文件名全名。
     * 选集载荷用 setupDirectEpisodes 的对象形态（flags 自带线路名），不走
     * SiteApi 的推送详情（那条会把线路标成「推送」、还要再手点一次选集）。
     */
    public static void startLocal(Activity activity, String uri, String name) {
        try {
            if (TextUtils.isEmpty(uri)) return;
            String title = TextUtils.isEmpty(name) ? uri : name;
            org.json.JSONObject episode = new org.json.JSONObject();
            episode.put("name", title);
            episode.put("url", uri);
            org.json.JSONObject flag = new org.json.JSONObject();
            flag.put("name", "本地");
            flag.put("episodes", new org.json.JSONArray().put(episode));
            org.json.JSONObject root = new org.json.JSONObject();
            root.put("flags", new org.json.JSONArray().put(flag));
            Intent intent = new Intent(activity, VideoActivity.class);
            intent.putExtra("key", SiteApi.PUSH);
            intent.putExtra("id", uri);
            intent.putExtra("name", title);
            intent.putExtra("local_episodes", root.toString());
            intent.putExtra("local_episode_idx", 0);
            if (uri.startsWith("content://")) intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 115 网盘文件直达播放：不走 SiteApi 的推送详情合成，播放会话归网盘自己。
     * episodesJson 形态与 startLocal 同一套（flags 自带线路名）：单线路「115网盘」，
     * 选集即画质档（原画/转码两条，默认起播原画），每集 url 是已包本机代理的地址；
     * url 参数＝默认起播档的地址（与 episodesJson 中 selIdx 那集一致），
     * 点播走 playerContent(PUSH) 裸地址分支由 Source.fetch 直通
     * （代理地址的 m3u8 标记在 Source.fetch 里按 Proxy115 登记判定）。
     */
    public static void startPan115(Activity activity, String url, String name, String episodesJson, int selIdx) {
        if (TextUtils.isEmpty(url)) return;
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("key", SiteApi.PUSH);
        intent.putExtra("id", url);
        intent.putExtra("name", name);
        intent.putExtra("pan115_episodes", episodesJson);
        intent.putExtra("pan115_episode_idx", selIdx);
        activity.startActivity(intent);
    }

    public static void collect(Activity activity, String key, String id, String name, String pic, String wallPic) {
        start(activity, key, id, name, pic, null, true, wallPic);
    }

    public static void start(Activity activity, String url) {
        start(activity, SiteApi.PUSH, url, url);
    }

    public static void start(Activity activity, String key, String id, String name) {
        start(activity, key, id, name, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark) {
        start(activity, key, id, name, pic, mark, false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, String wallPic) {
        start(activity, key, id, name, pic, mark, false, wallPic);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, String wallPic, String content) {
        start(activity, key, id, name, pic, mark, false, wallPic, content);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect) {
        start(activity, key, id, name, pic, mark, collect, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, String wallPic) {
        start(activity, key, id, name, pic, mark, collect, wallPic, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, String wallPic, String content) {
        if (MusicVodRouter.routeVod(activity, key, id, name, pic)) return;
        // 猫源配置站点的动作项：点击的本意是开网页，直接开页，详情页压根不创建（默影视 CatActionContentHandler 同口径，星辰无该分发框架，落在开始点总口）
        if (com.fongmi.android.tv.api.CatAction.openWebsite(key, id, pic)) return;
        ImgUtil.preload(activity, pic);
        if (Setting.isPlaybackArtworkWall() && !TextUtils.isEmpty(wallPic) && !TextUtils.equals(wallPic, pic)) ImgUtil.preload(activity, wallPic);
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("collect", collect);
        intent.putExtra("mark", mark);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("wallPic", wallPic);
        intent.putExtra("content", content);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        activity.startActivity(intent);
    }

    public static void startShortDrama(Activity activity, String key, String id, String name, String pic) {
        if (MusicVodRouter.routeVod(activity, key, id, name, pic)) return;
        ImgUtil.preload(activity, pic);
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        intent.putExtra("short_drama", true);
        activity.startActivity(intent);
    }

    private static boolean isShortDramaType(String typeName) {
        if (typeName == null || typeName.isEmpty()) return false;
        if (typeName.contains("短剧") || typeName.contains("竖屏") || typeName.contains("微剧")) return true;
        if (typeName.toLowerCase().contains("micro")) return true;
        if (typeName.contains("电影") || typeName.contains("电视剧") || typeName.contains("综艺") || typeName.contains("动漫")) return false;
        return false;
    }

    private String getName() {
        return Objects.toString(getIntent().getStringExtra("name"), "");
    }

    private String getPic() {
        return Objects.toString(getIntent().getStringExtra("pic"), "");
    }

    private String getWallPic() {
        return Objects.toString(getIntent().getStringExtra("wallPic"), "");
    }

    private String getContent() {
        return Objects.toString(getIntent().getStringExtra("content"), "");
    }

    private String getMark() {
        return Objects.toString(getIntent().getStringExtra("mark"), "");
    }

    private String getKey() {
        return Objects.toString(getIntent().getStringExtra("key"), "");
    }

    private String getId() {
        return Objects.toString(getIntent().getStringExtra("id"), "");
    }

    private String getHistoryKey() {
        return getKey().concat(AppDatabase.SYMBOL).concat(getId()).concat(AppDatabase.SYMBOL) + VodConfig.getCid();
    }

    @Override
    protected String getDetailVodId() {
        return getId();
    }

    private Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    private Flag getFlag() {
        return mFlagAdapter == null || mFlagAdapter.isEmpty() ? null : mFlagAdapter.getActivated();
    }

    private Episode getEpisode() {
        Flag flag = getFlag();
        if (flag != null) {
            List<Episode> items = flag.getEpisodes();
            for (Episode item : items) if (item.isSelected()) return item;
            // 无选中接住 EpisodeAdapter 的 -1／null 口径：不再回落第 1 集，与网格无金底一致。
            // 起播缺省第 1 集是播放入口的显式决定，只在 onRefresh 一处做，不藏进“当前集”里。
            return null;
        }
        return mEpisodeAdapter == null || mEpisodeAdapter.isEmpty() ? null : mEpisodeAdapter.getActivated();
    }

    private String getEpisodePlayFlag(Flag flag, Episode episode) {
        String value = mAudioQueueFlags.get(audioQueueEpisodeKey(episode));
        return TextUtils.isEmpty(value) ? flag == null ? "" : flag.getFlag() : value;
    }

    private boolean isAudioQueueEpisode(Episode episode) {
        return !TextUtils.isEmpty(mAudioQueueFlags.get(audioQueueEpisodeKey(episode)));
    }

    private String audioQueueEpisodeKey(Episode episode) {
        if (episode == null) return "";
        return episode.getName().concat("|").concat(episode.getUrl());
    }

    private String getOsdTitle() {
        String name = getName();
        if (mEpisodeAdapter == null || mEpisodeAdapter.isEmpty()) return name;
        Episode current = getEpisode();
        String episode = Objects.toString(current == null ? null : current.getDisplayName(), "");
        if (TextUtils.isEmpty(episode) || TextUtils.equals(name, episode)) return name;
        return TextUtils.isEmpty(name) ? episode : name + " " + episode;
    }

    private int getScale() {
        return mHistory != null && mHistory.getScale() != -1 ? mHistory.getScale() : PlayerSetting.getScale();
    }

    private boolean isReplay() {
        return Setting.getReset() == 1;
    }

    private boolean isFromCollect() {
        return getIntent().getBooleanExtra("collect", false);
    }

    private boolean isAutoRotate() {
        return Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
    }

    private boolean isLand() {
        return mBinding.getRoot().getTag().equals("land");
    }

    private boolean isPort() {
        return mBinding.getRoot().getTag().equals("port");
    }

    // 实际方向：旋转不重建时布局 tag 不变，凡按「当前屏幕方向」选控件套/排布的地方用这个，别用 isLand()/isPort() 的布局身份
    private boolean isActualLand() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityVideoBinding.inflate(getLayoutInflater());
    }

    @Override
    protected PlaybackService.NavigationCallback getNavigationCallback() {
        return mNavigationCallback;
    }

    @Override
    protected PlayerView getExoView() {
        return mBinding.exo;
    }

    @Override
    protected CustomSeekView getSeekView() {
        return mBinding.control.seek;
    }

    @Override
    protected TopProgressView getTopProgressView() {
        return mBinding == null ? null : mBinding.topProgress;
    }

    /** 顶部细进度条显隐：控制栏藏着＋全屏＋点播（有时长）才显示。 */
    private void updateTopProgress() {
        if (mBinding == null) return;
        // 平板布局无 topProgress id：只判 mBinding 不够，字段本身也要判空（与 getTopProgressView 同口径）
        TopProgressView topProgress = getTopProgressView();
        if (topProgress == null) return;
        View controlRoot = mBinding.control.getRoot();
        // 淡出途中算未显示：控制栏是淡出后才 GONE（XcFadeTransition），若只看 visibility，
        // hideControl 调用本方法时栏还 VISIBLE、淡出结束又无人重判，顶部条会被永久压住
        boolean controlsVisible = controlRoot.getVisibility() == View.VISIBLE && !XcFadeTransition.isFadingOut(controlRoot);
        boolean hasDuration = service() != null && !player().isEmpty() && player().isVod();
        topProgress.setVisibility(TopProgressRule.shouldShow(isFullscreen(), controlsVisible, hasDuration) ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onServiceConnected() {
        // 上游形：onServiceConnected 只做播放服务就绪通知。本树无 Vod 控制器，
        // 下列皆为与送达无关的 fork 增量（播放器 UI 接线／状态文案同步／方向），保留并注明：
        player().setDanmakuController(mBinding.exo.getDanmakuController());
        syncDesktopLyricsAudioContent();
        setPlayerKernel();
        setDecode();
        setLut();
        applyDeferredFullscreenOrientation();
        checkLand();
        // 服务就绪通知的等效点：补调服务未就绪时攒下的工作（详情阶段二＋待点火解析请求），
        // 再无播放结果暂存槽；点火后跳过 checkId，避免直接播放会话重复点火。
        if (consumePendingOnServiceReady()) return;
        // 外部本地媒体认领未回：原始 VIEW 意图没有可播 id，这一轮先让路，
        // 认领回调把载荷经 onNewIntent 送回本页时自会 checkId
        if (mExternalClaimPending) return;
        checkId();
    }

    @Override
    protected void onControllerReady(Player controller) {
        mBinding.audioSeek.setPlayer(controller);
    }

    @Override
    protected void onPlayerRebuilt() {
        setPlayerKernel();
        setDecode();
        setLut();
        refreshControlDialog();
        updateTopProgress();
    }

    private void refreshControlDialog() {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof ControlDialog dialog) dialog.setPlayer();
        }
    }

    /**
     * 外部本地媒体认领（独立外部门）：外部 VIEW 打开 content/file 的音视频时，
     * 清单把意图直接投到本页（独立 taskAffinity），不再先唤醒首页主任务。
     * 判别与分发复用 HomeActivity 那套（isLocalMediaIntent/dispatchLocalMedia）：
     * 先过文件权限门，回调里以本页为上下文 dispatch——其内部的 startLocal 把
     * 载荷意图发向本页，本页是 singleTop 且就在外部门任务顶，经 onNewIntent
     * 回到同一实例，与应用内起播同形、走同一条 checkId。认领未完成前
     * mExternalClaimPending 挡住 onServiceConnected 的 checkId，避免拿原始
     * 意图的空 id 先跑一轮空详情。应用内起播意图不带 VIEW+data，判别天然
     * 不命中，不受影响。
     */
    private boolean claimExternalMedia(Intent intent) {
        if (intent == null || intent.getStringExtra("key") != null) return false;
        if (!HomeActivity.isLocalMediaIntent(this, intent)) return false;
        mExternalClaimPending = true;
        PermissionUtil.requestFile(this, allGranted -> {
            mExternalClaimPending = false;
            if (isFinishing() || isDestroyed()) return;
            HomeActivity.dispatchLocalMedia(this, intent);
        });
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (claimExternalMedia(intent)) return;
        String oldId = getId();
        super.onNewIntent(intent);
        String id = Objects.toString(intent.getStringExtra("id"), "");
        if (TextUtils.isEmpty(id) || id.equals(oldId)) return;
        mBinding.swipeLayout.setRefreshing(true);
        saveHistory();
        getIntent().putExtras(intent);
        // Emby AI 链：换了片就重配选集单（新 intent 的 emby_episodes）
        if (getIntent().getStringExtra(EmbyNavigator.EXTRA_EPISODES) != null) mEmbyAiEpisodesBound = false;
        setOrient();
        checkId();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        // 外部本地媒体直达（独立外部门投递的原始 VIEW 意图）：先置认领中挡住
        // 首轮 checkId，认领本体排到页面建完再跑——权限已授予时回调是同步的，
        // 若在 initView 半途经 onNewIntent 重入，选集适配器还没建好会踩空
        if (HomeActivity.isLocalMediaIntent(this, getIntent())) {
            mExternalClaimPending = true;
            mBinding.getRoot().post(() -> claimExternalMedia(getIntent()));
        }
        sPlayerOwner = this;
        hasPlayer = true;
        mRestoringConfigurationPlayback = savedInstanceState != null;
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.getRoot(), (v, insets) -> setStatusBar(insets));
        mKeyDown = CustomKeyDown.create(this, mBinding.exo);
        createSwitchCover();
        mFrameParams = mBinding.video.getLayoutParams();
        mFrameHeight = mFrameParams.height;
        mBinding.swipeLayout.setEnabled(false);
        setupAudioStageOverlay();
        configureAudioLandscapeActions();
        mObserveDetail = this::setDetail;
        // 标题跑马灯：选中后超长标题自动横向滚动，短标题保持不动
        mBinding.name.setSelected(true);
        mObserveSearch = this::setSearch;
        mBroken = new ArrayList<>();
        mClock = Clock.create();
        mBinding.audioLyrics.setAudioStageMode(true);
        mBinding.audioLyrics.setSeekListener(this::onAudioLyricsSeek);
        mBinding.audioLyrics.setSuppressed(true);
        if (PlayerSetting.isImmersiveAudioMode()) ensureImmersiveAudioControllers();
        mR1 = this::hideControl;
        mR2 = this::setTraffic;
        mR3 = this::setOrient;
        mR4 = this::showEmpty;
        mPiP = new PiP();
        mPiP.useXingchenBackgroundMode();
        checkDanmakuImg();
        setRecyclerView();
        mOsd = new PlayerOsdController(mBinding.osd.getRoot(), mBinding.osd.osdTopLeft, mBinding.osd.osdTopRight, mBinding.osd.osdBottomLeft, mBinding.osd.osdBottomRight, mBinding.osd.osdDiagnostics, mBinding.osd.osdMiniProgress, new PlayerOsdController.Source() {
            @Override
            public PlayerManager getPlayer() {
                return service() == null ? null : player();
            }

            @Override
            public String getTitle() {
                return getOsdTitle();
            }
        }, 12f);
        setVideoView();
        setViewModel();
        if (shouldUseImmersiveAudio()) {
            setAudioStageVisible(true);
            mBinding.progressLayout.showContent();
        } else if (hasInitialPreview()) {
            showInitialPreview();
        } else {
            mBinding.progressLayout.showProgress();
        }
        showProgress();
        restoreKaraokeResultDialog(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mPendingKaraokeResult == null) return;
        outState.putSerializable(STATE_KARAOKE_RESULT, mPendingKaraokeResult);
        outState.putInt(STATE_KARAOKE_RESULT_ACTION, mKaraokeResultAction);
        SpiderDebug.log("karaoke-result", "save action=%d dialog=%s changing=%s", mKaraokeResultAction, mKaraokeResultDialog != null && mKaraokeResultDialog.isShowing(), isChangingConfigurations());
    }

    @SuppressWarnings("deprecation")
    private void restoreKaraokeResultDialog(Bundle state) {
        KaraokeResult result = null;
        int action = KARAOKE_RESULT_ACTION_NONE;
        if (state != null) {
            Object saved = state.getSerializable(STATE_KARAOKE_RESULT);
            if (saved instanceof KaraokeResult value) result = value;
            action = state.getInt(STATE_KARAOKE_RESULT_ACTION, KARAOKE_RESULT_ACTION_NONE);
        }
        if (result == null && mViewModel != null) {
            result = mViewModel.getKaraokeResult();
            action = mViewModel.getKaraokeResultAction();
        }
        if (result == null) return;
        mPendingKaraokeResult = result;
        mKaraokeResultAction = action;
        mKaraokeResultShown = true;
        KaraokeResult restored = result;
        int restoredAction = action;
        SpiderDebug.log("karaoke-result", "restore action=%d bundle=%s", action, state != null);
        mBinding.getRoot().post(() -> showKaraokeResultDialog(restored, restoredAction));
    }

    private void ensureImmersiveAudioControllers() {
        if (!PlayerSetting.isImmersiveAudioMode() || mLyrics != null || mBinding == null) return;
        mLyrics = new LyricsController(mBinding.lyrics);
        mLyrics.setSecondaryView(mBinding.audioLyrics);
        mLyrics.setListener((result, lines) -> {
            if (service() != null) service().setDesktopLyricsSnapshot(result, lines);
        });
        mKaraoke = new KaraokeController();
        mKaraoke.setListener((status, track, sample, snapshot) -> {
            boolean playing = service() != null && player().isPlaying();
            if (mBinding.karaoke != null) mBinding.karaoke.setPlaying(playing);
            mBinding.audioKaraoke.setPlaying(playing);
            if (mBinding.karaoke != null) mBinding.karaoke.setState(status, track, sample, snapshot);
            mBinding.audioKaraoke.setState(status, track, sample, snapshot);
            syncKaraokeStageVisibility();
        });
    }

    private boolean shouldUseImmersiveAudio() {
        return PlayerSetting.isImmersiveAudioMode() && (isAudioOnly() || isMusicLike());
    }

    private void setupAudioStageOverlay() {
        ViewGroup parent = (ViewGroup) mBinding.audioStage.getParent();
        if (parent != null) parent.removeView(mBinding.audioStage);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        ((ViewGroup) mBinding.getRoot()).addView(mBinding.audioStage, params);
        mBinding.audioStage.bringToFront();
    }

    private void configureAudioLandscapeActions() {
        if (mBinding == null || !ResUtil.isLand(this)) return;
        if (mBinding.audioTransport instanceof ViewGroup transport) {
            for (int i = 0; i < transport.getChildCount(); i++) {
                View child = transport.getChildAt(i);
                if (!(child instanceof ViewGroup group)) continue;
                for (int j = 0; j < group.getChildCount(); j++) {
                    View item = group.getChildAt(j);
                    if (item instanceof TextView) item.setVisibility(View.GONE);
                    else if (item instanceof ImageView) {
                        ViewGroup.LayoutParams params = item.getLayoutParams();
                        params.width = ResUtil.dp2px(40);
                        params.height = ResUtil.dp2px(40);
                        item.setLayoutParams(params);
                    }
                }
            }
        }
        normalizeLandscapeAudioAction(mBinding.audioLyricsAction);
        normalizeLandscapeAudioAction(mBinding.audioKaraokeAction);
        normalizeLandscapeAudioAction(mBinding.audioMoreAction);
    }

    private void normalizeLandscapeAudioAction(TextView view) {
        if (view == null) return;
        view.setText("");
        view.setGravity(Gravity.CENTER);
        view.setCompoundDrawablePadding(0);
        view.setPadding(ResUtil.dp2px(8), ResUtil.dp2px(8), ResUtil.dp2px(8), ResUtil.dp2px(8));
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.more.setOnClickListener(view -> onMore());
        // 评论入口只在竖屏布局有此 id（占原一起看按钮位），横屏/平板布局为 null，判空写法同 embyFavBtn
        if (mBinding.commentDetail != null) mBinding.commentDetail.setOnClickListener(view -> onComment());
        mBinding.keepBtn.setOnClickListener(view -> onKeep());
        mBinding.downloadBtn.setOnClickListener(view -> onDownload());
        mBinding.castBtn.setOnClickListener(view -> onCast());
        mBinding.settingBtn.setOnClickListener(view -> onSetting());
        // 横屏／平板布局无此二 id（同文件 8048、8170 行的判空写法），无判空则横屏启动即 NPE
        if (mBinding.embyFavBtn != null) mBinding.embyFavBtn.setOnClickListener(view -> onEmbyFavToggle());
        if (mBinding.embyPlayedBtn != null) mBinding.embyPlayedBtn.setOnClickListener(view -> onEmbyPlayedToggle());
        mBinding.content.setOnClickListener(view -> onContent());
        mBinding.reverse.setOnClickListener(view -> onReverse());
        mBinding.name.setOnLongClickListener(view -> onChange());
        mBinding.content.setOnLongClickListener(view -> onCopy());
        mBinding.control.back.setOnClickListener(view -> onBack());
        mBinding.control.cast.setOnClickListener(view -> onCast());
        mBinding.control.info.setOnClickListener(view -> onInfo());
        mBinding.control.keep.setOnClickListener(view -> onKeep());
        mBinding.control.osdDiagnostics.setOnClickListener(view -> onOsdDiagnostics());
        mBinding.control.play.setOnClickListener(view -> checkPlay());
        mBinding.control.next.setOnClickListener(view -> checkNext());
        mBinding.control.prev.setOnClickListener(view -> checkPrev());
        mBinding.control.setting.setOnClickListener(view -> onSetting());
        mBinding.control.title.setOnLongClickListener(view -> onChange());
        mBinding.widget.error.setOnClickListener(view -> onRefresh());
        mBinding.control.right.lock.setOnClickListener(view -> onLock());
        mBinding.control.right.rotate.setOnClickListener(view -> onRotate());
        mBinding.control.right.episodesVertical.setOnClickListener(view -> onEpisodes());
        mBinding.control.right.commentVertical.setOnClickListener(view -> onComment());
        mBinding.control.right.castVertical.setOnClickListener(view -> onCast());
        mBinding.control.fullscreen.setOnClickListener(view -> onFullscreen());
        mBinding.control.danmaku.setOnClickListener(view -> onDanmakuShow());
        mBinding.control.fineSetting.setOnClickListener(view -> onFineSetting());
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.reset.setOnClickListener(view -> onReset());
        mBinding.discTools.fullscreen.setOnClickListener(view -> onFullscreen());
        mBinding.control.action.player.setOnClickListener(view -> onPlayerKernel());
        mBinding.control.action.player.setOnLongClickListener(view -> onChooseLong());
        mBinding.control.action.decode.setOnClickListener(view -> onDecode());
        mBinding.control.action.endingItem.setOnClickListener(view -> onEnding());
        mBinding.control.action.repeat.setOnClickListener(view -> onRepeat());
        mBinding.control.action.openingItem.setOnClickListener(view -> onOpening());
        mBinding.control.action.danmakuText.setOnClickListener(view -> onDanmaku());
        mBinding.control.action.moreText.setOnClickListener(view -> onMorePanel());
        mBinding.control.action.episodes.setOnClickListener(view -> onEpisodes());
        mBinding.control.action.commentAction.setOnClickListener(view -> onComment());
        mBinding.audioPlay.setOnClickListener(view -> checkPlay());
        mBinding.audioNext.setOnClickListener(view -> checkNext());
        mBinding.audioPrev.setOnClickListener(view -> checkPrev());
        mBinding.audioRepeatAction.setOnClickListener(view -> onRepeat());
        mBinding.audioLyricsAction.setOnClickListener(view -> onLyricsSearch());
        mBinding.audioQueueAction.setOnClickListener(view -> onAudioQueue());
        mBinding.audioCastAction.setOnClickListener(view -> onCast());
        mBinding.audioKeepAction.setOnClickListener(view -> onKeep());
        mBinding.audioSettingAction.setOnClickListener(view -> onSetting());
        mBinding.audioKaraokeAction.setOnClickListener(view -> onKaraokeMode());
        mBinding.audioBackgroundAction.setOnClickListener(view -> randomizeAudioBackgroundMix(false));
        mBinding.audioMoreAction.setOnClickListener(view -> onAudioMore());
        mBinding.audioTrackAction.setOnClickListener(view -> onTrack(C.TRACK_TYPE_AUDIO));
        mBinding.audioSubtitleAction.setOnClickListener(view -> onTrack(C.TRACK_TYPE_TEXT));
        mBinding.audioInfoAction.setOnClickListener(view -> onInfo());
        mBinding.control.action.text.setOnLongClickListener(view -> onTextLong());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.action.reset.setOnLongClickListener(view -> onResetToggle());
        mBinding.control.action.endingItem.setOnLongClickListener(view -> onEndingReset());
        mBinding.control.action.openingItem.setOnLongClickListener(view -> onOpeningReset());
        mBinding.video.setOnTouchListener((view, event) ->
                (!isVisible(mBinding.control.getRoot()) && dispatchDiscMenuTouch(event))
                        || mKeyDown.onTouchEvent(event));
        mBinding.control.action.getRoot().setOnTouchListener(this::onActionTouch);
        mBinding.swipeLayout.setOnRefreshListener(this::onSwipeRefresh);
        // 选集区滑动不许拉出下拉刷新：手指落在选集列表上就先关掉刷新，抬手再恢复
        //（他真机实证：滑选集滑到顶会误触整页刷新）
        mBinding.episode.addOnItemTouchListener(new androidx.recyclerview.widget.RecyclerView.OnItemTouchListener() {
            private boolean disabledByEpisode;

            @Override
            public boolean onInterceptTouchEvent(androidx.recyclerview.widget.RecyclerView rv, android.view.MotionEvent e) {
                if (e.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    if (mBinding.swipeLayout.isEnabled()) {
                        mBinding.swipeLayout.setEnabled(false);
                        disabledByEpisode = true;
                    }
                } else if (e.getAction() == android.view.MotionEvent.ACTION_UP || e.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                    if (disabledByEpisode) {
                        mBinding.swipeLayout.setEnabled(true);
                        disabledByEpisode = false;
                    }
                }
                return false;
            }

            @Override
            public void onTouchEvent(androidx.recyclerview.widget.RecyclerView rv, android.view.MotionEvent e) {
            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            }
        });
    }

    private WindowInsetsCompat setStatusBar(WindowInsetsCompat insets) {
        int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
        Insets nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
        int bottom = nav.bottom;
        mStatusBarInset = top;
        mNavigationRightInset = nav.right;
        applyStatusBarSpacer();
        ViewGroup.LayoutParams lp = mBinding.statusBar.getLayoutParams();
        lp.height = mAudioStageVisible ? 0 : top;
        mBinding.statusBar.setLayoutParams(lp);
        setEpisodeBottomInset(bottom);
        return insets;
    }

    private void applyStatusBarSpacer() {
        if (mBinding == null) return;
        ViewGroup.LayoutParams lp = mBinding.statusBar.getLayoutParams();
        int height = mAudioStageVisible ? 0 : mStatusBarInset;
        if (lp.height == height) return;
        lp.height = height;
        mBinding.statusBar.setLayoutParams(lp);
    }

    private void setEpisodeBottomInset(int bottom) {
        mEpisodeBottomInset = bottom;
        int padding = ResUtil.dp2px(12);
        mBinding.episode.setPaddingRelative(mBinding.episode.getPaddingStart(), mBinding.episode.getPaddingTop(), mBinding.episode.getPaddingEnd(), padding);
        applyAudioStageInsets();
        mBinding.episode.post(this::updateEpisodeViewportHeight);
    }

    private void applyAudioStageInsets() {
        if (mBinding == null) return;
        mBinding.audioStage.setPaddingRelative(mBinding.audioStage.getPaddingStart(), ResUtil.dp2px(18) + mStatusBarInset, mBinding.audioStage.getPaddingEnd(), ResUtil.dp2px(14) + mEpisodeBottomInset);
        applyAudioBackgroundActionInsets();
    }

    private void applyAudioBackgroundActionInsets() {
        ViewGroup.LayoutParams raw = mBinding.audioBackgroundAction.getLayoutParams();
        if (!(raw instanceof FrameLayout.LayoutParams params)) return;
        int top = -mBinding.audioStage.getPaddingTop();
        int end = -mBinding.audioStage.getPaddingEnd() - ResUtil.dp2px(4);
        if (params.topMargin == top && params.getMarginEnd() == end) return;
        params.topMargin = top;
        params.setMarginEnd(end);
        mBinding.audioBackgroundAction.setLayoutParams(params);
    }

    private void updateEpisodeViewportHeight() {
        if (mBinding.episode.getVisibility() != View.VISIBLE || mBinding.getRoot().getHeight() <= 0) return;
        int[] root = new int[2];
        int[] episode = new int[2];
        mBinding.getRoot().getLocationOnScreen(root);
        mBinding.episode.getLocationOnScreen(episode);
        int available = root[1] + mBinding.getRoot().getHeight() - mEpisodeBottomInset - ResUtil.dp2px(8) - episode[1];
        if (available <= 0 || available == mEpisodeMaxHeight) return;
        mEpisodeMaxHeight = available;
        mBinding.episode.setMaxHeight(available);
        mBinding.episode.requestLayout();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mAudioStageVisible && isSystemNavigationTouch(event)) return false;
        if (dispatchAudioStageTouch(event)) return true;
        return super.dispatchTouchEvent(event);
    }

    private boolean dispatchAudioStageTouch(MotionEvent event) {
        if (!mAudioStageVisible || mBinding == null || event == null) return false;
        if (!isPointInside(mBinding.audioStage, event)) return false;
        if (isAudioStageInteractiveTouch(event)) return false;
        return true;
    }

    private boolean isAudioStageInteractiveTouch(MotionEvent event) {
        return mBinding.audioLyrics.isAudioStageTouchPoint(event.getRawX(), event.getRawY())
                || isPointInside(mBinding.audioSeek, event)
                || isPointInside(mBinding.audioRepeatAction, event)
                || isPointInside(mBinding.audioPrev, event)
                || isPointInside(mBinding.audioPlay, event)
                || isPointInside(mBinding.audioNext, event)
                || isPointInside(mBinding.audioQueueAction, event)
                || isPointInside(mBinding.audioLyricsAction, event)
                || isPointInside(mBinding.audioKaraokeAction, event)
                || isPointInside(mBinding.audioMoreAction, event)
                || isPointInside(mBinding.audioCastAction, event)
                || isPointInside(mBinding.audioKeepAction, event)
                || isPointInside(mBinding.audioSettingAction, event)
                || isPointInside(mBinding.audioTrackAction, event)
                || isPointInside(mBinding.audioSubtitleAction, event)
                || isPointInside(mBinding.audioInfoAction, event)
                || isPointInside(mBinding.audioBackgroundAction, event);
    }

    private boolean isSystemNavigationTouch(MotionEvent event) {
        if (mNavigationRightInset <= 0 && mEpisodeBottomInset <= 0) return false;
        Rect rect = new Rect();
        if (!mBinding.getRoot().getGlobalVisibleRect(rect)) return false;
        return (mNavigationRightInset > 0 && event.getRawX() >= rect.right - mNavigationRightInset)
                || (mEpisodeBottomInset > 0 && event.getRawY() >= rect.bottom - mEpisodeBottomInset);
    }

    private boolean isPointInside(View view, MotionEvent event) {
        if (view == null || view.getVisibility() != View.VISIBLE) return false;
        Rect rect = new Rect();
        return view.getGlobalVisibleRect(rect) && rect.contains((int) event.getRawX(), (int) event.getRawY());
    }

    private void setRecyclerView() {
        mBinding.flag.setHasFixedSize(true);
        mBinding.flag.setItemAnimator(null);
        mBinding.flag.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.flag.setAdapter(mFlagAdapter = new FlagAdapter(this));
        mBinding.quick.setAdapter(mQuickAdapter = new QuickAdapter(this));
        mBinding.episodeGroup.setHasFixedSize(true);
        mBinding.episodeGroup.setItemAnimator(null);
        mBinding.episodeGroup.setAdapter(mEpisodeGroupAdapter = new EpisodeGroupAdapter(this));
        mEpisodeSpanCount = getEpisodeSpanCount();
        mBinding.episode.setNestedScrollingEnabled(false);
        mBinding.episode.setHasFixedSize(false);
        mBinding.episode.setItemAnimator(null);
        mBinding.episode.setLayoutManager(new GridLayoutManager(this, mEpisodeSpanCount));
        mBinding.episode.addItemDecoration(mEpisodeDecoration = new SpaceItemDecoration(mEpisodeSpanCount, 8));
        mBinding.episode.setAdapter(mEpisodeAdapter = new EpisodeAdapter(this, ViewType.GRID));
        mBinding.episode.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                syncEpisodeGroupByScroll();
            }
        });
        mBinding.quality.setHasFixedSize(true);
        mBinding.quality.setItemAnimator(null);
        mBinding.quality.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.quality.setAdapter(mQualityAdapter = new QualityAdapter(this));
        mBinding.control.parse.setHasFixedSize(true);
        mBinding.control.parse.setItemAnimator(null);
        mBinding.control.parse.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.control.parse.setAdapter(mParseAdapter = new ParseAdapter(this, ViewType.DARK));
    }

    private int getEpisodeSpanCount() {
        return EpisodeGridLayoutPolicy.getMaxSpan(isActualLand(), ResUtil.isPad());
    }

    private void setVideoView() {
        mBinding.control.action.danmakuText.setVisibility(View.VISIBLE);
        setupActionButtons();
        mBinding.video.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            mPiP.update(this, view);
            updateCustomButtonLayout();
            Log.d(SIZE_TAG, "video layout new=" + (right - left) + "x" + (bottom - top)
                    + " old=" + (oldRight - oldLeft) + "x" + (oldBottom - oldTop)
                    + " fullscreen=" + isFullscreen()
                    + " land=" + isLand()
                    + " scale=" + getScale()
                    + " player=" + (service() == null ? "none" : player().getPlayerText()));
        });
    }

    private void setupActionButtons() {
        mActionButtons = new HashMap<>();
        addActionButton(PlayerButtonSetting.PLAYER, mBinding.control.action.player);
        addActionButton(PlayerButtonSetting.DECODE, mBinding.control.action.decode);
        addActionButton(PlayerButtonSetting.SPEED, mBinding.control.action.speed);
        addActionButton(PlayerButtonSetting.TEXT, mBinding.control.action.text);
        addActionButton(PlayerButtonSetting.DANMAKU, mBinding.control.action.danmakuText);
        addActionButton(PlayerButtonSetting.OPENING, mBinding.control.action.openingItem);
        addActionButton(PlayerButtonSetting.ENDING, mBinding.control.action.endingItem);
        addActionButton(PlayerButtonSetting.EPISODES, mBinding.control.action.episodes);
        PlayerButtonSetting.applyOrder(mBinding.control.action.container, mActionButtons);
        setupCustomActionButtons();
    }

    private void addActionButton(String id, View view) {
        mActionButtons.put(id, view);
    }

    private void setupCustomActionButtons() {
        ensureCustomButtonContainers();
        for (View view : mCustomActionViews) {
            ViewParent parent = view.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(view);
        }
        mCustomActionViews.clear();
        mCustomLeftButtonRow.removeAllViews();
        mCustomRightButtonRow.removeAllViews();
        mCustomPortraitButtonRow.removeAllViews();
        List<MpvConfigStore.CustomButton> buttons = MpvConfigStore.customButtons();
        boolean landscape = isActualLand();
        for (int index = 0; index < buttons.size(); index++) {
            MpvConfigStore.CustomButton button = buttons.get(index);
            if (!button.isButtonVisible()) continue;
            TextView view = new TextView(this);
            view.setTextSize(13);
            view.setTextColor(Color.WHITE);
            view.setGravity(Gravity.CENTER);
            view.setMinHeight(ResUtil.dp2px(40));
            view.setMinWidth(ResUtil.dp2px(56));
            view.setPadding(ResUtil.dp2px(10), ResUtil.dp2px(4), ResUtil.dp2px(10), ResUtil.dp2px(4));
            view.setBackgroundResource(R.drawable.selector_control_sheet_button);
            view.setText(button.title);
            view.setSingleLine(true);
            view.setMaxWidth(ResUtil.dp2px(144));
            view.setEllipsize(TextUtils.TruncateAt.END);
            view.setContentDescription(button.title);
            view.setTag(button.id);
            view.setOnClickListener(item -> {
                player().sendMpvCustomButton(button.id, false);
                setR1Callback();
            });
            view.setOnLongClickListener(item -> {
                player().sendMpvCustomButton(button.id, true);
                setR1Callback();
                return true;
            });
            if (Util.isLeanback()) {
                view.setFocusable(true);
                view.setFocusableInTouchMode(true);
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(40));
            params.setMargins(ResUtil.dp2px(4), ResUtil.dp2px(2), ResUtil.dp2px(4), ResUtil.dp2px(2));
            LinearLayout target = landscape
                    ? (index < 4 ? mCustomLeftButtonRow : mCustomRightButtonRow)
                    : mCustomPortraitButtonRow;
            target.addView(view, params);
            mCustomActionViews.add(view);
        }
        updateCustomButtonLayout();
        updateCustomButtonVisibility();
    }

    private void updateCustomButtonStates() {
        MpvPlayer mpv = service() != null && isOwner()
                && player().getPlayer() instanceof MpvPlayer active ? active : null;
        if (mCustomButtonPlayer != mpv) {
            if (mCustomButtonPlayer != null) mCustomButtonPlayer.removeCustomButtonStateListener(mCustomButtonStateListener);
            mCustomButtonPlayer = mpv;
            if (mpv != null) mpv.addCustomButtonStateListener(mCustomButtonStateListener);
        }
        for (View view : mCustomActionViews) {
            view.setSelected(mpv != null && mpv.isCustomButtonActive((String) view.getTag()));
        }
    }

    private void ensureCustomButtonContainers() {
        if (mCustomLeftButtons != null) return;
        mCustomLeftButtons = createCustomButtonScroll();
        mCustomRightButtons = createCustomButtonScroll();
        mCustomPortraitButtons = createCustomButtonScroll();
        mCustomLeftButtonRow = createCustomButtonRow(mCustomLeftButtons);
        mCustomRightButtonRow = createCustomButtonRow(mCustomRightButtons);
        mCustomPortraitButtonRow = createCustomButtonRow(mCustomPortraitButtons);
        mCustomRightButtons.setFillViewport(true);
        mCustomRightButtonRow.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        mCustomRightButtonRow.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;

        FrameLayout.LayoutParams leftParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.TOP);
        FrameLayout.LayoutParams rightParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.TOP);
        int margin = ResUtil.dp2px(8);
        leftParams.setMargins(margin, 0, margin, 0);
        rightParams.setMargins(margin, 0, margin, 0);
        mBinding.video.addView(mCustomLeftButtons, leftParams);
        mBinding.video.addView(mCustomRightButtons, rightParams);

        RelativeLayout.LayoutParams portraitParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        portraitParams.addRule(RelativeLayout.ABOVE, R.id.bottom);
        portraitParams.setMargins(margin, 0, margin, ResUtil.dp2px(2));
        ((ViewGroup) mBinding.control.getRoot()).addView(mCustomPortraitButtons, portraitParams);
    }

    private HorizontalScrollView createCustomButtonScroll() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        return scroll;
    }

    private LinearLayout createCustomButtonRow(HorizontalScrollView scroll) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        scroll.addView(row, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void updateCustomButtonLayout() {
        if (mCustomLeftButtons == null || mBinding.video.getWidth() <= 0) return;
        boolean landscape = isActualLand();
        if (!landscape) return;

        int margin = ResUtil.dp2px(8);
        int width = Math.max(ResUtil.dp2px(96), mBinding.video.getWidth() / 2 - margin * 2);
        int height = Math.max(mCustomLeftButtons.getMeasuredHeight(), ResUtil.dp2px(40));
        int top = Math.max(margin, Math.round(mBinding.video.getHeight() * 0.65f - height / 2f));
        FrameLayout.LayoutParams leftParams = (FrameLayout.LayoutParams) mCustomLeftButtons.getLayoutParams();
        FrameLayout.LayoutParams rightParams = (FrameLayout.LayoutParams) mCustomRightButtons.getLayoutParams();
        leftParams.width = width;
        rightParams.width = width;
        leftParams.topMargin = top;
        rightParams.topMargin = top;
        mCustomLeftButtons.setLayoutParams(leftParams);
        mCustomRightButtons.setLayoutParams(rightParams);
    }

    private void updateCustomButtonVisibility() {
        updateCustomButtonStates();
        boolean visible = service() != null && player().isMpv() && isVisible(mBinding.control.getRoot());
        boolean land = isActualLand();
        if (mCustomLeftButtons != null) mCustomLeftButtons.setVisibility(visible && land ? View.VISIBLE : View.GONE);
        if (mCustomRightButtons != null) mCustomRightButtons.setVisibility(visible && land ? View.VISIBLE : View.GONE);
        if (mCustomPortraitButtons != null) mCustomPortraitButtons.setVisibility(visible && !land ? View.VISIBLE : View.GONE);
        updateCustomButtonLayout();
    }

    private void applyActionButtonVisibility() {
        if (mActionButtons != null) PlayerButtonSetting.applyVisibility(mActionButtons);
        applyPortraitActionRow();
        updateCustomButtonVisibility();
    }

    // 竖屏全屏底部行只留 6 项（内核/硬解/倍速/片头/片尾/更多）：字幕、弹幕移入更多面板，选集与右侧竖栏重复，一并收起
    // 三项显隐在此按方向统一裁定（盖过内容侧置位）；离开竖屏全屏时按各自原规则还原，用户在按钮设置里关掉的仍保持隐藏
    // 竖屏套判定与 showControl 同口径：实际屏幕方向，不看视频宽高（竖屏短剧转横屏后底部行走横屏原构成）
    // 片头/片尾是给多集打点用的，另按集数裁定：集数<2（单片，与选集同一判定）横竖两行都藏；恢复显示仍先过用户按钮设置
    private void applyPortraitActionRow() {
        if (player() == null) return;
        boolean hasEpisodes = mEpisodeAdapter != null && getEpisodeCount() >= 2;
        mBinding.control.action.openingItem.setVisibility(hasEpisodes && PlayerButtonSetting.isVisible(PlayerButtonSetting.OPENING) ? View.VISIBLE : View.GONE);
        mBinding.control.action.endingItem.setVisibility(hasEpisodes && PlayerButtonSetting.isVisible(PlayerButtonSetting.ENDING) ? View.VISIBLE : View.GONE);
        if (isFullscreen() && !isActualLand()) {
            mBinding.control.action.text.setVisibility(View.GONE);
            mBinding.control.action.danmakuText.setVisibility(View.GONE);
            mBinding.control.action.episodes.setVisibility(View.GONE);
            return;
        }
        if (PlayerButtonSetting.isVisible(PlayerButtonSetting.DANMAKU)) mBinding.control.action.danmakuText.setVisibility(View.VISIBLE);
        if (PlayerButtonSetting.isVisible(PlayerButtonSetting.TEXT)) mBinding.control.action.text.setVisibility(player().haveTrack(C.TRACK_TYPE_TEXT) || player().isVod() ? View.VISIBLE : View.GONE);
        if (PlayerButtonSetting.isVisible(PlayerButtonSetting.EPISODES) && mEpisodeAdapter != null) mBinding.control.action.episodes.setVisibility(getEpisodeCount() < 2 ? View.GONE : View.VISIBLE);
    }

    private void setVideoView(boolean isInPictureInPictureMode) {
        if (isInPictureInPictureMode) {
            mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        } else {
            applyAudioStageLayout(mAudioStageVisible);
            restoreContextWall();
        }
    }

    private void setDecode() {
        mBinding.control.action.decode.setText(player().getDecodeText());
    }

    private void setDecodeSwitchPending(boolean pending) {
        mBinding.control.action.decode.setEnabled(!pending);
        mBinding.control.action.decode.setAlpha(pending ? 0.65f : 1.0f);
    }

    private void setNextDecodeText() {
        int next = player().isHardDecode() ? PlayerEngine.SOFT : PlayerEngine.HARD;
        mBinding.control.action.decode.setText(ResUtil.getStringArray(R.array.select_decode)[next]);
    }

    private void setPlayerKernel() {
        mBinding.control.action.player.setText(player().getPlayerText());
    }

    private void setScale(int scale) {
        if (mHistory != null) mHistory.setScale(scale);
        if (SiteApi.PUSH.equals(getKey())) PlayerSetting.putScale(scale);
        applyResizeMode(scale);
        mBinding.exo.post(() -> applyResizeMode(scale));
    }

    private void setLut() {
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observeForever(mObserveDetail);
        mViewModel.getSearch().observeForever(mObserveSearch);
    }

    private void checkId() {
        detailOpenTime = System.currentTimeMillis();
        mShortDramaMode = getIntent().getBooleanExtra("short_drama", false);
        // 短剧模式：一进来就直接全屏，不闪详情页
        if (mShortDramaMode) {
            getWindow().getDecorView().post(() -> {
                if (!isFullscreen()) enterFullscreen();
            });
        }
        if (!TextUtils.isEmpty(getIntent().getStringExtra("local_episodes"))) {
            playLocalDirect();
            return;
        }
        if (!TextUtils.isEmpty(getIntent().getStringExtra("pan115_episodes"))) {
            playPan115Direct();
            return;
        }
        if (!TextUtils.isEmpty(getIntent().getStringExtra("txt_episodes"))) {
            playTxtDirect();
            return;
        }
        if (!TextUtils.isEmpty(getIntent().getStringExtra("hk_rule"))) {
            if (getIntent().getBooleanExtra("hk_direct_play", false)) playHkDirect();
            else getHkDetail();
            return;
        }
        if (getId().startsWith("push://")) getIntent().putExtra("key", SiteApi.PUSH).putExtra("id", getId().substring(7));
        if (getId().isEmpty() || getId().startsWith("msearch:")) setEmpty(false);
        else getDetail();
    }

    /**
     * 海阔分支：HkRouter.detail() 取数 → HkDetailBridge 转 Vod → 走现有详情渲染。
     * 引擎走 SiteApi 的 hkRouterCache 复用（原版单例语义）：同规则同会话只初始化
     * 一次，不再每次 new+destroy；详情结果不做会话缓存，每次进详情都实时取
     * （与 8.83 原版一致），重复进出同一条目会重新执行规则取数。
     * 播放拦截是 M4 内容，本期点选集走原有播放链（可能失败，属预期）。
     */
    private void getHkDetail() {
        detailStartTime = System.currentTimeMillis();
        mBinding.swipeLayout.setRefreshing(true);
        new Thread(() -> {
            try {
                String ruleTitle = getIntent().getStringExtra("hk_rule");
                String itemUrl = getIntent().getStringExtra("hk_item_url");
                HkRule rule = HkRuleManager.get().getRule(ruleTitle);
                if (rule == null) throw new IllegalStateException("hk rule missing: " + ruleTitle);
                HkItem item = new HkItem(getName(), itemUrl, getPic(), getContent());
                HkDetail detail = SiteApi.getHkRouter(ruleTitle).detail(itemUrl, item, false);
                Vod vod = HkDetailBridge.toVod(rule, getId(), detail);
                App.post(() -> {
                    mBinding.swipeLayout.setRefreshing(false);
                    if (detail.isEmpty()) {
                        showError(getString(R.string.error_detail));
                        mBinding.progressLayout.showEmpty();
                    } else {
                        long cost = System.currentTimeMillis() - detailStartTime;
                        SpiderDebug.log("video-flow", "detail finish cost=%dms hk rule=%s", cost, ruleTitle);
                        detailFinishTime = System.currentTimeMillis();
                        // 海阔电台改道：电台规则整表交听书播放器（优先于音乐改道，
                        // 「电台」同在音乐识别表里）
                        if (com.fongmi.android.tv.ui.HkRadioRouter.routeHkDetail(VideoActivity.this, ruleTitle, vod)) {
                            finish();
                            return;
                        }
                        // 海阔音乐改道：规则名识别为音乐就整表交音乐播放器、不落视频详情页
                        // （否则这页点选集会在视频播放器里播，绕开音乐播放器）。桥接点在
                        // 本页、不碰海阔引擎；非音乐规则照旧走详情渲染。
                        if (MusicVodRouter.routeHkDetail(VideoActivity.this, ruleTitle, vod)) {
                            finish();
                            return;
                        }
                        markEpisodeRender = true;
                        setDetail(vod);
                        backfillHkFavoritePic(ruleTitle, getId(), vod.getPic());
                    }
                });
            } catch (Throwable e) {
                App.post(() -> {
                    mBinding.swipeLayout.setRefreshing(false);
                    showError(getString(R.string.error_detail));
                    mBinding.progressLayout.showEmpty();
                });
            }
        }).start();
    }

    /**
     * 海阔 V4 直接播放：不加载详情，直接走播放链。
     * SiteApi.playerContent 的 hk_ 拦截会调 HkRouter.play() 解析出真地址（含 headers）。
     */
    private void playHkDirect() {
        showProgress();
        // 直接播放带选集（HkPageActivity 拼的列表选集）：先渲染线路/选集 UI，再解析当前集播放
        setupDirectEpisodes("hk_episodes", "hk_episode_idx");
        String flag = Objects.toString(getIntent().getStringExtra("hk_line"), "");
        // P0 红果类预热：后台预解析当前集懒地址，命中本地代理则预下加密文件
        // （等价文章页 hgWarm 的下载部分；只对 127.0.0.1 代理形态生效，其它规则 no-op）
        HkProxyPrewarm.prewarm(getKey(), getId());
        playDirect(getKey(), flag, getId());
    }

    /**
     * txt 混合链接直接播放：不加载详情，直接走播放链。
     * 选集 UI 复用 setupDirectEpisodes，切集走 playerContent(PUSH) → Source.fetch 解析器（magnet/ed2k/thunder 等）。
     */
    private void playTxtDirect() {
        showProgress();
        setupDirectEpisodes("txt_episodes", "txt_episode_idx");
        playDirect(getKey(), "", getId());
    }

    /**
     * 本地媒体文件直接播放：不加载详情、不等配置。
     * 选集 UI 复用 setupDirectEpisodes（线路名「本地」、单集为文件名全名），
     * 点播走 playerContent(PUSH) 裸地址分支——file/content 地址无提取器认领，
     * 在 Source.fetch 原样直通给播放器。
     */
    private void playLocalDirect() {
        showProgress();
        setupDirectEpisodes("local_episodes", "local_episode_idx");
        playDirect(getKey(), "", getId());
    }

    /**
     * 115 网盘文件直接播放：不加载详情，选集 UI 复用 setupDirectEpisodes
     * （线路「115网盘」、选集为画质档），点播走 playerContent(PUSH) 裸地址分支，
     * 代理地址由 Source.fetch 直通（m3u8 标记按 Proxy115 登记判定）。
     */
    private void playPan115Direct() {
        showProgress();
        setupDirectEpisodes("pan115_episodes", "pan115_episode_idx");
        playDirect(getKey(), "", getId());
    }

    /**
     * 直接播放的选集 UI：intent 的 episodesJson 转为 Vod。
     * 两种载荷：扁平 [{name,url,pic}] → 单线路；{flags:[{name,episodes:[...]}]} → 多线路
     * （海阔播放数据按 names 展开的线路，集名各线路一致、切线路保持集序）。
     * 当前集按 idx 选中。海阔走 playerContent → HkRouter.play 解析；txt 走 playerContent(PUSH)。
     */
    private void setupDirectEpisodes(String jsonKey, String idxKey) {
        try {
            String json = getIntent().getStringExtra(jsonKey);
            String ref = getIntent().getStringExtra(jsonKey + "_ref");
            if (ref != null && !ref.isEmpty()) {
                String parked = HK_EPISODES_STORE.get(ref);
                if (parked != null) json = parked;
                else if (TextUtils.isEmpty(json)) {
                    // 仓里取不到（进程重启/已被淘汰）不许静默无集：给可见提示（深挖 L-05）
                    Notify.show("选集数据已失效，请返回重新打开");
                    return;
                }
            }
            if (TextUtils.isEmpty(json)) return;
            int selIdx = getIntent().getIntExtra(idxKey, 0);
            Vod vod = new Vod();
            vod.setName(getName());
            vod.setPic(getPic());
            java.util.List<Flag> flags = new java.util.ArrayList<>();
            if (json.trim().startsWith("{")) {
                org.json.JSONObject root = new org.json.JSONObject(json);
                org.json.JSONArray flagArr = root.optJSONArray("flags");
                if (flagArr == null || flagArr.length() == 0) return;
                for (int fi = 0; fi < flagArr.length(); fi++) {
                    org.json.JSONObject fo = flagArr.optJSONObject(fi);
                    if (fo == null) continue;
                    String fname = fo.optString("name", "");
                    if (TextUtils.isEmpty(fname)) fname = "线路" + (fi + 1);
                    Flag flag = Flag.create(fname);
                    org.json.JSONArray eps = fo.optJSONArray("episodes");
                    if (eps != null) {
                        for (int i = 0; i < eps.length(); i++) {
                            org.json.JSONObject o = eps.optJSONObject(i);
                            if (o == null) continue;
                            String url = o.optString("url", "");
                            if (TextUtils.isEmpty(url)) continue;
                            String nm = o.optString("name", "");
                            boolean fallbackName = TextUtils.isEmpty(nm);
                            if (fallbackName) nm = String.format(java.util.Locale.getDefault(), "%02d", flag.getEpisodes().size() + 1);
                            Episode ep = Episode.create(nm, url);
                            if (fallbackName) ep.setNumberName(true);
                            if (fi == 0 && i == selIdx) ep.setSelected(true);
                            flag.getEpisodes().add(ep);
                        }
                    }
                    if (!flag.getEpisodes().isEmpty()) flags.add(flag);
                }
                if (flags.isEmpty()) return;
                vod.setFlags(flags);
                setDetail(vod);
                return;
            }
            org.json.JSONArray arr = new org.json.JSONArray(json);
            if (arr.length() == 0) return;
            Flag flag = Flag.create("默认");
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String url = o.optString("url", "");
                if (TextUtils.isEmpty(url)) continue;
                String nm = o.optString("name", "");
                boolean fallbackName = TextUtils.isEmpty(nm);
                if (fallbackName) nm = String.format(java.util.Locale.getDefault(), "%02d", flag.getEpisodes().size() + 1);
                Episode ep = Episode.create(nm, url);
                if (fallbackName) ep.setNumberName(true);
                if (i == selIdx) ep.setSelected(true);
                flag.getEpisodes().add(ep);
            }
            if (flag.getEpisodes().isEmpty()) return;
            flags.add(flag);
            vod.setFlags(flags);
            setDetail(vod);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void checkLand() {
        if (isPort() && ResUtil.isLand(this)) enterFullscreen();
    }

    private void getDetail() {
        detailStartTime = System.currentTimeMillis();
        // 方案 A：补「点开→发起请求」盲区计时。detailOpenTime 由 checkId 入口设置，
        // 首次 getDetail() 消费后清零，刷新/重试路径不再打（语义：一次打开）。
        if (detailOpenTime > 0) {
            SpiderDebug.log("video-flow", "detail open cost=%dms key=%s id=%s", System.currentTimeMillis() - detailOpenTime, getKey(), getId());
            detailOpenTime = 0;
        }
        detailHealthRecorded = false;
        mDetailParseSeq++;
        mReadingDetailDiverting = false;
        mReadingDetailDivertedOnce = false;
        mNextPreloader.clear();
        mPreparsedHistoryResult = null;
        mPreparsedHistorySpec = null;
        mPreparsedHistoryFuture = null;
        SpiderDebug.log("video-flow", "detail start key=%s id=%s name=%s", getKey(), getId(), getName());
        mDetailCacheShown = false;
        Vod cached = VodDetailCache.get(this, getKey(), getId());
        if (cached != null) {
            mDetailCacheShown = true;
            SpiderDebug.log("video-flow", "detail cache hit key=%s id=%s", getKey(), getId());
            setDetail(cached);
        }
        // 轻量取数：跳过选集展开（磁力 DHT 最长 60 秒），主数据一到先画线路/选集，
        // 展开由 startSourceParse 在后台跑完再补，不拖住上屏。
        mViewModel.detailContentLight(getKey(), getId());
    }

    private void getDetail(Vod item) {
        revealManualSearch = false;
        if (!isAutoMode()) mViewModel.stopSearch();
        saveHistory();
        getIntent().putExtra("key", item.getSiteKey());
        getIntent().putExtra("pic", item.getPic());
        getIntent().putExtra("id", item.getId());
        mBinding.swipeLayout.setRefreshing(true);
        mBinding.swipeLayout.setEnabled(false);
        mBinding.scroll.scrollTo(0, 0);
        mClock.setCallback(null);
        clearLyrics();
        updateNavigationKey();
        if (service() != null) {
            player().reset();
            player().stop();
        }
        getDetail();
    }

    private void setDetail(Result result) {
        long cost = System.currentTimeMillis() - detailStartTime;
        SpiderDebug.log("video-flow", "detail finish cost=%dms empty=%s msg=%s", cost, result.getList().isEmpty(), result.getMsg());
        detailFinishTime = System.currentTimeMillis();
        markEpisodeRender = true;
        recordDetailHealth(result, cost);
        mBinding.swipeLayout.setRefreshing(false);
        if (SiteApi.isActionStub(getKey(), getId())) {
            // 动作条目桩（旧代 jar 包已在 detail 调用内弹自有 UI）：不渲染空详情，直接退回列表。
            SpiderDebug.log("video-flow", "action stub detail, finish key=%s id=%s", getKey(), getId());
            finish();
            return;
        }
        // 猫源设置项：点击的本意是开网页，detail 只是副产物。留在这儿会让内嵌页背后压着空白播放页，
        // 也不能走 setEmpty——它在 intent 带 name 时会拿动作名去别的站搜索。
        if (com.fongmi.android.tv.api.CatAction.shouldYieldDetail(getKey(), detailStartTime, result)) {
            SpiderDebug.log("video-flow", "detail yield to cat webview key=%s id=%s", getKey(), getId());
            finish();
            return;
        }
        if (result.getList().isEmpty()) {
            if (mDetailCacheShown) return;
            setEmpty(result.hasMsg());
        } else {
            // 缓存命中时已展示过展开好的列表：新数据先只刷新标题/元信息，线路/选集等
            // 后台展开完成再整体换，避免列表先"缩水"（裸磁力链）再恢复。无缓存时直接画。
            boolean hadCache = mDetailCacheShown;
            mDetailCacheShown = false;
            Vod vod = result.getVod();
            sanitizeDetailFallback(vod);
            setDetail(vod, hadCache);
            // 选集展开后置：磁力/网盘展开好了再补列表并写缓存
            startSourceParse(vod, hadCache);
        }
        Notify.show(result.getMsg());
    }

    /**
     * 详情回退守卫：源脚本详情解析失败时，常把站名填进 vod_name 做回退
     * （如河马影视源返回 vod_name="河马影视"，其它字段全空）。片名恰好等于
     * 站名、又与用户点开的标题不一致时，判定同条记录不可信：保留列表带过
     * 来的正确标题与封面，不让回退覆盖首屏（封面链：checkPic 只补空不盖
     * 非空，回退的错误封面会经 setArtwork 铺到海报位）。空名仍走 checkName
     * 的原有回填口径，这里不碰；列表没带封面时封面不动。
     */
    private void sanitizeDetailFallback(Vod vod) {
        if (vod == null) return;
        String requested = getName();
        String returned = vod.getName();
        String siteName = getSite() != null ? getSite().getName() : "";
        if (requested.isEmpty() || returned.isEmpty() || siteName.isEmpty()) return;
        if (returned.equals(siteName) && !requested.equals(siteName)) {
            SpiderDebug.log("video-flow", "detail name is site fallback, keep requested name key=%s id=%s", getKey(), getId());
            vod.setName(requested);
            String requestedPic = getPic();
            if (!requestedPic.isEmpty() && !requestedPic.equals(vod.getPic())) {
                SpiderDebug.log("video-flow", "detail pic follows site fallback, keep requested pic key=%s id=%s", getKey(), getId());
                vod.setPic(requestedPic);
            }
        }
    }

    /**
     * 详情先画后展：选集展开在独立后台线程跑，完成后回主线程补列表。
     * 世代号防串台：切源/刷新后，旧一轮的回调直接丢弃，不碰新一轮的界面。
     */
    private void startSourceParse(Vod vod, boolean deferred) {
        long seq = mDetailParseSeq;
        Source.get().parseAsync(vod, (parsed, expanded) -> onSourceParseDone(parsed, expanded, deferred, seq));
    }

    private void onSourceParseDone(Vod vod, boolean expanded, boolean deferred, long seq) {
        if (seq != mDetailParseSeq || isFinishing() || isDestroyed()) return;
        VodDetailCache.put(this, getKey(), getId(), vod);
        if (deferred) {
            // 后台展开返回空列表时保留现状：addAll 会先 clear 再加，空列表会把缓存渲染出的线路行清空，
            // 造成"片在播、选集在"线路行却被清空，与 setDetail(Vod, deferLists) 注释的初衷相反。
            if (vod.getFlags().isEmpty()) {
                SpiderDebug.log("video-flow", "deferred parse empty flags, keep current lists key=%s", getKey());
            } else {
                mFlagAdapter.addAll(vod.getFlags());
                // 只渲染不点火：自动播点火是 bindDetailService（缓存路）/
                // consumePendingOnServiceReady（无缓存或服务后连路）的职责。走 checkFlag 会因
                // addAll 换了新 Flags 丢了选中态，seamless 再 onItemClick 一遍，onRefresh
                // stop+clear 重起——用户体感「暂停一下再播」（双 input.open）。
                renderFlagSelection(vod);
                // Emby 画质行：线路/选集后补上时刷新
                refreshEmbyQualityRow();
                // 不漏认两层：缓存路后台展开完成后，用完整选集表再判一次。
                // 探针是最终仲裁（确认书形内容才改道），误判视频不会被改道。
                maybeDivertDetailToReading(vod.getTypeName());
            }
            return;
        }
        if (!expanded) return;
        Flag flag = getFlag();
        if (flag == null || mEpisodeAdapter == null || mFlagAdapter == null) return;
        syncSelectedEpisode(flag);
        setEpisodeAdapter(flag.getEpisodes());
        mFlagAdapter.notifyDataSetChanged();
        SpiderDebug.log("video-flow", "detail parse done key=%s", getKey());
    }

    private void setEmpty(boolean finish) {
        if (isFromCollect() || finish) {
            finish();
        } else if (getName().isEmpty()) {
            showEmpty();
        } else {
            if (!TextUtils.equals(mBinding.name.getText(), getName())) mBinding.name.setText(getName());
            App.post(mR4, 10000);
            checkSearch(false);
        }
    }

    private void showEmpty() {
        showError(getString(R.string.error_detail));
        mBinding.swipeLayout.setEnabled(true);
        mBinding.progressLayout.showEmpty();
    }

    private void setDetail(Vod item) {
        setDetail(item, false);
    }

    /**
     * @param deferLists 为 true 时先不画线路/选集（缓存已展示过展开好的列表时用），
     *                   等后台展开完成再由 onSourceParseDone 整体换，避免列表先"缩水"再恢复。
     *                   标题/简介/历史/收藏等其余信息照常立即刷新。
     *
     * 渲染分两阶段：阶段一纯 UI 先画（不依赖 PlaybackService，数据一到就执行）；
     * 阶段二需要播放器服务的（历史的播放器设置、自动播放启动）等服务连上再补。
     * 服务已连上时两阶段紧挨着执行，与原来一次走完等价；自动播放语义、seamless、
     * 410 自救均不碰。
     */
    private void setDetail(Vod item, boolean deferLists) {
        prepareHongguoComments(item);
        renderDetailUi(item, deferLists);
        if (service() == null) {
            mPendingDetailVod = item;
            mPendingDetailDeferLists = deferLists;
            return;
        }
        bindDetailService(item, deferLists);
    }

    /** 阶段一：纯 UI 渲染。不碰 PlaybackService / Player，服务未连上时也可先画。 */
    private void renderDetailUi(Vod item, boolean deferLists) {
        mAiVod = item; // P3：记录当前影片，供 AI 剧情问答
        if (!mShortDramaMode) mShortDramaMode = isShortDramaType(item.getTypeName());
        item.checkPic(getPic());
        item.checkName(getName());
        item.checkContent(getContent());
        // 文本未变不重设：重设会重启标题跑马灯，用户看到字跳一下
        if (!TextUtils.equals(mBinding.name.getText(), item.getName())) mBinding.name.setText(item.getName());
        if (!deferLists) mFlagAdapter.addAll(item.getFlags());
        App.removeCallbacks(mR4);
        checkHistoryUi(item);
        // 详情页背景：全局星辰壁纸，不铺海报墙
        mDetailWallOff = true;
        hideContextWall();
        setAudioStageVisible(shouldUseImmersiveAudio());
        mBinding.progressLayout.showContent();
        if (!deferLists) renderFlagSelection(item);
        checkKeepImg();
        setText(item);
        updateKeep();
        embyBindDetailExtras(item);
        bindEmbyAiEpisodes();
        // 不漏认两层：详情打开时整页改道判定（选集已同步画出时）。
        // 命中后抑制 bindDetailService 的自动播点火，探针确认内容后开阅读器。
        if (!deferLists) maybeDivertDetailToReading(item.getTypeName());
    }

    /** 阶段二：需要播放器服务的部分。服务连上后由 consumePendingOnServiceReady 补调。 */
    private void bindDetailService(Vod item, boolean deferLists) {
        checkHistoryPlayer(item);
        if (deferLists) return;
        // 上游自动播点火形核对：旗只抑制点火本身，不抑制历史／播放器设置；
        // 点火被拦住的那次由探针否定后 resumeAutoPlayAfterProbe() 补上，不吞点火。
        if (mReadingDetailDiverting) return;
        boolean preservePlayback = mRestoringConfigurationPlayback && !player().isEmpty();
        if (item.getFlags().isEmpty()) {
            if (!preservePlayback) startFlow();
        } else if (!preservePlayback) {
            // 等价于 08786a51 前 checkFlag→onItemClick→seamless 的自动播语义。
            // 注意不能照字面调 onItemClick(mHistory.getFlag())：阶段一 syncSelectedEpisode 已把历史集标为选中，
            // 空壳经 setSelected 灌入真列表后 seamless 的预选守卫会直接 return，自动播依然点不着（实测代码确认）。
            // 这里直接点播阶段一定好的那一集（历史集）；直接播放流（海阔选集/txt 走 setupDirectEpisodes，
            // playHkDirect/playTxtDirect 随后自行 playerContent）不再点火，避免重播。
            if (!isDirectPlaySession()) {
                scrollToPosition(mBinding.flag, mFlagAdapter.getPosition());
                Episode episode = getEpisode();
                if (episode != null) igniteEpisode(episode);
            }
        }
        if (preservePlayback) SpiderDebug.log("karaoke-result", "configuration restore preserved playback key=%s episode=%s", player().getKey(), mHistory.getVodRemarks());
        mRestoringConfigurationPlayback = false;
    }

    /**
     * 阶段二自动播点火：只负责起播，不触发进全屏。
     * 1.3.30 语义里自动播从不走 shouldEnterFullscreen（seamless 只在集未选中时才 onItemClick）；
     * 而点火时的集已被阶段一标为选中，直接 onItemClick 会在元数据到达前瞬间 enterFullscreen，
     * resolveOrientation 落到默认 SENSOR_LANDSCAPE——每个视频点开先强制横屏的真凶。
     * 点火沿 onItemClick(Episode) 完整主流程（emby:410 复位/EmbyAI/多版本/红果守卫/起播语义一字不动），
     * 仅用瞬态标记摘掉「进全屏」这一个副作用；方向纠正留给起播后 onSizeChanged→checkOrientation。
     */
    private void igniteEpisode(Episode item) {
        mAutoIgniting = true;
        try {
            onItemClick(item);
        } finally {
            mAutoIgniting = false;
        }
    }

    /** 直接播放会话（海阔选集/txt 走 setupDirectEpisodes）：选集已预选，playHkDirect/playTxtDirect
     * 随后自行 playerContent，阶段二不再点火，避免重播。 */
    private boolean isDirectPlaySession() {
        if (!TextUtils.isEmpty(getIntent().getStringExtra("local_episodes"))) return true;
        if (!TextUtils.isEmpty(getIntent().getStringExtra("pan115_episodes"))) return true;
        if (!TextUtils.isEmpty(getIntent().getStringExtra("txt_episodes"))) return true;
        return getIntent().getBooleanExtra("hk_direct_play", false) && !TextUtils.isEmpty(getIntent().getStringExtra("hk_rule"));
    }

    /** checkFlag 的纯渲染版：只画线路/选集 UI，不启动自动播放（播放动作等 bindDetailService）。 */
    private void renderFlagSelection(Vod item) {
        boolean empty = item.getFlags().isEmpty();
        mBinding.flag.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) return;
        restoreFlagSelectionWithoutPlayback();
    }

    private void setText(Vod item) {
        setText(mBinding.content, R.string.detail_content, item.getContent());
        updateContentFold();
        setDetailLyrics(item.getContent());
        setMeta(item);
        updateAudioStageText();
        if (mAudioStageVisible) applyAudioPageMode(true);
    }

    private void setText(TextView view, int resId, String text) {
        if (TextUtils.isEmpty(text)) {
            view.setText("");
            view.setVisibility(View.GONE);
            return;
        }
        // 文本未变不重建 Spannable：详情两阶段渲染（缓存→新数据）各跑一遍，重建会让简介跳动
        String target = resId > 0 ? getString(resId, text) : text;
        if (TextUtils.equals(view.getText(), target) && view.getVisibility() == View.VISIBLE) return;
        view.setText(Sniffer.buildClickable(target, this::clickableSpan), TextView.BufferType.SPANNABLE);
        view.setVisibility(View.VISIBLE);
        view.setLinkTextColor(Color.WHITE);
        CustomMovement.bind(view);
    }

    private ClickableSpan clickableSpan(Result result) {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                FolderActivity.start(getActivity(), getKey(), result);
                ((TextView) view).setMaxLines(Integer.MAX_VALUE);
                setRedirect(true);
            }
        };
    }

    private void setMeta(Vod item) {
        StringBuilder line1 = new StringBuilder();
        if (!item.getDirector().isEmpty()) line1.append(getString(R.string.detail_director, item.getDirector())).append("  ");
        if (!item.getActor().isEmpty()) line1.append(getString(R.string.detail_actor, item.getActor()));
        StringBuilder line2 = new StringBuilder();
        if (!item.getYear().isEmpty()) line2.append(getString(R.string.detail_year, item.getYear())).append("  ");
        if (!item.getArea().isEmpty()) line2.append(getString(R.string.detail_area, item.getArea())).append("  ");
        if (!item.getTypeName().isEmpty()) line2.append(getString(R.string.detail_type, item.getTypeName()));
        StringBuilder line3 = new StringBuilder();
        if (!item.getRemarks().isEmpty()) line3.append(getString(R.string.detail_remarks, item.getRemarks()));
        StringBuilder sb = new StringBuilder();
        if (line1.length() > 0) sb.append(line1.toString().trim());
        if (line2.length() > 0) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(line2.toString().trim());
        }
        if (line3.length() > 0) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(line3.toString().trim());
        }
        // 文本未变不重建 Spannable：两阶段渲染各跑一遍，重建会让元数据行跳动
        String target = sb.toString();
        boolean show = target.length() > 0;
        if (show && TextUtils.equals(mBinding.meta.getText(), target) && mBinding.meta.getVisibility() == View.VISIBLE) return;
        mBinding.meta.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        // 导演/演员等字段可能带 [a=cr:{...}]名字[/a] 标记（源数据原样），照上游 setText 的做法过 buildClickable：
        // 标记转成可点名字（点击按标记里的规则走搜索），不再把标记原文当纯文本显示。
        mBinding.meta.setText(Sniffer.buildClickable(target, this::clickableSpan), TextView.BufferType.SPANNABLE);
        mBinding.meta.setLinkTextColor(Color.WHITE);
        CustomMovement.bind(mBinding.meta);
    }

    // 播放器标题（照上游 getPlaybackControlTitle）：片名打头、真集名拼在后面（片名 : 集名）；
    // 集名为空白或占位名（播放/正片，多为播放钮标签、不是集名）时只用片名。
    private String getPlaybackName() {
        CharSequence name = mBinding == null || mBinding.name == null ? "" : mBinding.name.getText();
        if (!TextUtils.isEmpty(name)) return name.toString();
        String vodName = getName();
        if (TextUtils.isEmpty(vodName) && mHistory != null) vodName = mHistory.getVodName();
        return TextUtils.isEmpty(vodName) ? "" : vodName;
    }

    private String getEpisodeTitle(Episode episode) {
        if (episode == null) return "";
        String title = episode.getDisplayName();
        return isPlaceholderTitle(title) ? "" : title;
    }

    private CharSequence getPlaybackControlTitle() {
        return getPlaybackControlTitle(mEpisodeAdapter == null || mEpisodeAdapter.isEmpty() ? null : getEpisode());
    }

    private CharSequence getPlaybackControlTitle(Episode episode) {
        String name = getPlaybackName();
        String title = getEpisodeTitle(episode);
        return TextUtils.isEmpty(title) || TextUtils.equals(name, title) ? name : getString(R.string.detail_title, name, title);
    }

    private boolean isPlaceholderTitle(String name) {
        if (name == null) return true;
        String trimmed = name.trim();
        return trimmed.isEmpty() || "播放".equals(trimmed) || "正片".equals(trimmed);
    }

    private void getPlayer(Flag flag, Episode episode) {
        // 切集不盖图：滑动预览层在切集成立时立即撤下，下一集已由预下备好、切过来直接起播出画
        hideSwitchCover();
        // 起播：详情页的无海报背景门控解除，后续走播放页原有背景逻辑
        mDetailWallOff = false;
        mBinding.control.title.setText(getPlaybackControlTitle(episode));
        playerStartTime = System.currentTimeMillis();
        beginPlayHealth();
        String playFlag = getEpisodePlayFlag(flag, episode);
        String previousEpisodeKey = Objects.toString(mPlaybackEpisodeKey, "");
        mPlaybackEpisodeKey = audioQueueEpisodeKey(episode);
        mSkipKaraokeTrackAutoLoad = isMusicLike() && !TextUtils.isEmpty(previousEpisodeKey) && !TextUtils.equals(previousEpisodeKey, mPlaybackEpisodeKey);
        SpiderDebug.log("video-flow", "player start key=%s flag=%s episode=%s url=%s", getKey(), playFlag, episode.getName(), episode.getUrl());
        mInlineLyrics = getEpisodeInlineLyrics(episode);
        applyPlaybackArtwork(episode);
        clearLyrics();
        clearKaraokeState();
        if (shouldUseImmersiveAudio()) setAudioStageVisible(true);
        // 首集加速：surface 提前挂载（Exo only），与解析链并行
        if (!player().isMpv()) attachSurface();
        // 预解析命中检查：历史预解析 / 下一集预加载
        Result preparsed = consumePreparsed(playFlag, episode.getUrl());
        if (preparsed != null) {
            beginParseUi(episode);
            if (service() == null) {
                SpiderDebug.log("video-flow", "preparse deferred until service ready key=%s", getKey());
                deferPlayRequest(getKey(), playFlag, episode.getUrl());
            } else setPlayer(preparsed);
            return;
        }
        // 在途预解析复用：同一集的预解析还在跑就等它，不另开一轮解析（消灭双跑）
        ListenableFuture<Result> inflight = pollInflightPreparse(playFlag, episode.getUrl());
        if (inflight != null) {
            SpiderDebug.log("video-flow", "history preparse wait key=%s", getKey());
            beginParseUi(episode);
            // 不再占共用池线程阻塞干等（旧形在 Task.execute 里 get(25 秒)，白占一条池线程）：
            // 完成走回调、等满走调度器兜底，先到者赢；超时不取消在途预解析本体（与旧口径一致）。
            AtomicBoolean settled = new AtomicBoolean(false);
            Futures.addCallback(inflight, Task.callback(
                    r -> {
                        if (!settled.compareAndSet(false, true)) return;
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            if (isUsableParseResult(r)) {
                                if (service() == null) {
                                    SpiderDebug.log("video-flow", "inflight preparse deferred until service ready key=%s", getKey());
                                    deferPlayRequest(getKey(), playFlag, episode.getUrl());
                                } else setPlayer(r);
                            } else startNormalParse(flag, episode, playFlag);
                        });
                    },
                    t -> {
                        if (!settled.compareAndSet(false, true)) return;
                        SpiderDebug.log("video-flow", "history preparse wait fail key=%s err=%s", getKey(), t.getClass().getSimpleName());
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            startNormalParse(flag, episode, playFlag);
                        });
                    }
            ), MoreExecutors.directExecutor());
            Task.scheduler().schedule(() -> {
                if (!settled.compareAndSet(false, true)) return;
                SpiderDebug.log("video-flow", "history preparse wait timeout key=%s", getKey());
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    startNormalParse(flag, episode, playFlag);
                });
            }, PREPARSE_WAIT_MS, TimeUnit.MILLISECONDS);
            return;
        }
        startNormalParse(flag, episode, playFlag);
    }

    /** 在途预解析等待上限：与解析超时同量级，超了回退走正常解析链，不更慢。 */
    private static final long PREPARSE_WAIT_MS = 25000;

    /**
     * 直接起播路（上游 startPlayback 直调 startPlayer 形）：播放结果不再经
     * ViewModel 排队——后台跑 SiteApi.playerContent，主线程直交 setPlayer，
     * 错误同样直交 setPlayer（Result.error → startPlayer 的 onError 可见错）。
     * 超时口径沿用 ViewModel 旧值 Constant.TIMEOUT_VOD，不发明新量级。
     * 绕不过服务绑定的一步：PlaybackActivity.player() 直取 mService.player()，
     * 服务为空时 setPlayer／startPlayer／onError 全会 NPE，故服务未就绪时只
     * 暂存请求三元组（key／flag／url），服务就绪后重跑解析——结果从不进槽。
     */
    /**
     * 服务未就绪窗口的请求暂存统一入口：只存请求三元组（key／flag／url），结果从不进槽；
     * 可见提示＋留转圈，服务就绪后由 consumePendingOnServiceReady 经 playDirect 重跑解析。
     * 直通路派发前、解析结果到达时、预解析／重播竞态一律走这里，不留“提示＋用户再点”尾巴。
     */
    private void deferPlayRequest(String key, String flag, String url) {
        mPendingPlayKey = key;
        mPendingPlayFlag = flag;
        mPendingPlayUrl = url;
        Notify.show("播放器服务未就绪，服务就绪后自动播放");
        showProgress();
    }

    private void playDirect(String key, String flag, String url) {
        if (service() == null) {
            SpiderDebug.log("video-flow", "play deferred until service ready key=%s", key);
            deferPlayRequest(key, flag, url);
            return;
        }
        // 超时从开跑算（同 SiteViewModel 口径）：先在池里占住线程，真正开跑那刻才建计时
        // future 起表；旧形 submit 后立即 withTimeout，池排队时间也吃解析预算，忙时假超时。
        Task.execute(() -> {
            ListenableFutureTask<Result> task = ListenableFutureTask.create(() -> SiteApi.playerContent(key, flag, url));
            FluentFuture<Result> future = FluentFuture.from(task).withTimeout(Constant.TIMEOUT_VOD, TimeUnit.MILLISECONDS, Task.scheduler());
            future.addCallback(Task.callback(
                result -> App.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (service() == null) {
                        SpiderDebug.log("video-flow", "play result arrived without service, request re-deferred key=%s", key);
                        deferPlayRequest(key, flag, url);
                        return;
                    }
                    setPlayer(result);
                }),
                error -> App.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (service() == null) {
                        SpiderDebug.log("video-flow", "play error arrived without service, request re-deferred key=%s", key);
                        deferPlayRequest(key, flag, url);
                        return;
                    }
                    setPlayer(toErrorResult(error));
                })
            ), MoreExecutors.directExecutor());
            task.run();
        });
    }

    private static Result toErrorResult(Throwable error) {
        if (error instanceof ExtractException) return Result.error(error.getMessage());
        if (error instanceof TimeoutException) return Result.error(ResUtil.getString(R.string.error_play_timeout));
        return Result.error(error.getMessage() != null ? error.getMessage() : "播放失败，请重试");
    }

    /** 正常解析链：直接起播路跑 playerContent，结果直交 setPlayer。 */
    private void startNormalParse(Flag flag, Episode episode, String playFlag) {
        playDirect(getKey(), playFlag, episode.getUrl());
        beginParseUi(episode);
    }

    /** 点集后三条路共用的 UI 准备：标题选中/历史/进度/字幕自动匹配。 */
    private void beginParseUi(Episode episode) {
        mBinding.control.title.setSelected(true);
        updateHistory(episode);
        showProgress();
        // 字幕自动匹配：按片名搜索并加载最佳字幕
        try {
            String videoTitle = mBinding.name.getText().toString();
            SubtitleManager.autoMatch(player(), videoTitle);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 在途预解析取用：只有同 spec、未完成才等；取用即摘除防重入。
     * 排除：雷磁类不复用（预解析内 Source.stop 会杀死在播引擎，真机实证）。
     */
    private ListenableFuture<Result> pollInflightPreparse(String playFlag, String episodeUrl) {
        ListenableFuture<Result> future = mPreparsedHistoryFuture;
        if (future == null || future.isDone()) return null;
        String spec = getKey() + "|" + playFlag + "|" + episodeUrl;
        if (!spec.equals(mPreparsedHistorySpec)) return null;
        if (isThunderUrl(episodeUrl)) return null;
        mPreparsedHistoryFuture = null;
        mPreparsedHistorySpec = null;
        return future;
    }

    private boolean isUsableParseResult(Result r) {
        // 阅读会话不进视频预解析复用：改道只发生在用户点集后的 setPlayer，
        // 打开详情时的后台预解析命中阅读会话也不跳转（避免打开漫画详情就被拽进阅读器）。
        return r != null && r.getUrl() != null && !TextUtils.isEmpty(r.getUrl().v())
                && ReadingGate.extract(r) == null;
    }

    /**
     * 消费预解析结果：先查历史预解析，再查下一集预加载。
     * 命中返回 Result，未命中返回 null（走正常解析链）。
     */
    private Result consumePreparsed(String playFlag, String episodeUrl) {
        // 与 pollInflightPreparse 对齐：雷磁类不消费预解析结果——历史预解析
        // 不复核任务活否/有无字节，命中即 setPlayer 会把死地址递给播放器。
        // 直接返回 null 走正常解析链（Thunder.fetch 的字节门禁在那里裁决）。
        // 下一集预加载侧本就禁雷磁类（预加载守卫），此处早返不另损行为。
        if (isThunderUrl(episodeUrl)) {
            SpiderDebug.log("video-flow", "thunder preparse skipped, use normal parse chain");
            return null;
        }
        String spec = getKey() + "|" + playFlag + "|" + episodeUrl;
        if (spec.equals(mPreparsedHistorySpec) && mPreparsedHistoryResult != null) {
            Result r = mPreparsedHistoryResult;
            mPreparsedHistoryResult = null;
            mPreparsedHistorySpec = null;
            SpiderDebug.log("video-flow", "history preparse hit key=%s", getKey());
            return r;
        }
        Result next = mNextPreloader.consumeIfMatch(getKey(), playFlag, episodeUrl);
        if (next != null) {
            SpiderDebug.log("video-flow", "next-episode preload hit key=%s", getKey());
        }
        return next;
    }

    private void setPlayer(Result result) {
        setPlayer(result, false);
    }

    /**
     * 阅读改道：判别层命中的漫画/小说 Result 在此改道阅读器。
     * <p>
     * 补齐阅读器需要的上下文（书名/章节名/章节表）：VideoActivity 手里有详情上下文，
     * 判别层只带了数据本身。章节表取当前线路/选集列表。
     * <p>
     * 不写视频历史、不进播放器；改道后详情页保留在阅读器之下，返回落回详情页。
     */
    private boolean divertToReader(Result result) {
        ReadingSession rs;
        try {
            rs = ReadingGate.extract(result);
        } catch (Throwable t) {
            return false;
        }
        if (rs == null) return false;
        return openReadingSession(rs, false);
    }

    /**
     * 阅读器打开统一收口（自动改道 / 手动入口 / 详情级改道共用）。
     * 补齐阅读器需要的上下文（书名/封面/章节表）：判别层/手动入口只带了数据本身。
     * 不写视频历史、不进播放器；打开阅读器后不 finish——详情页留在阅读器下面，
     * 从阅读器返回自然落回详情页（与视频「播放器返回回详情页」对称）。
     * 防回弹的一次性抑制由调用方（详情级改道）负责，这里只管打开。
     */
    private boolean openReadingSession(ReadingSession rs, boolean manual) {
        try {
            if (TextUtils.isEmpty(rs.bookTitle) && mHistory != null) {
                rs.bookTitle = mHistory.getVodName();
            }
            if (TextUtils.isEmpty(rs.bookTitle)) rs.bookTitle = getName();
            if (TextUtils.isEmpty(rs.coverUrl)) rs.coverUrl = getPic();
            // 章节表：没带全时，用当前选集列表补（py 源章节即选集）。
            if (rs.chapters.size() <= 1) {
                List<Episode> eps = episodeListForReader();
                if (eps != null && eps.size() > 1) {
                    ReadingSession.Chapter cur = rs.currentChapter();
                    String curUrl = cur.url;
                    rs.chapters.clear();
                    int idx = 0;
                    for (int i = 0; i < eps.size(); i++) {
                        Episode e = eps.get(i);
                        rs.chapters.add(new ReadingSession.Chapter(e.getName(), e.getUrl()));
                        if (!TextUtils.isEmpty(curUrl) && curUrl.equals(e.getUrl())) idx = i;
                        else if (e.isSelected()) idx = i;
                    }
                    rs.chapterIndex = idx;
                }
            }
            ReadingSession.Chapter cur = rs.currentChapter();
            if (TextUtils.isEmpty(rs.chapterUrl)) rs.chapterUrl = cur.url;
            // py 小说/漫画：记下书的详情 id 与线路，供书架重建章节表时重拉详情用
            // （漫画此前只记小说，重开只能单章、不能切章）
            if (!TextUtils.isEmpty(rs.siteKey)
                    && TextUtils.isEmpty(rs.legadoKey) && TextUtils.isEmpty(rs.localId)
                    && TextUtils.isEmpty(rs.ruleTitle)) {
                rs.detailId = getId();
                Flag f = getFlag();
                rs.playFlag = f == null || f.getFlag() == null ? "" : f.getFlag();
            }
            SpiderDebug.log("video-flow", manual
                            ? "reading manual open kind=%s book=%s chapters=%d"
                            : "reading divert open kind=%s book=%s chapters=%d",
                    rs.kind, rs.bookTitle, rs.chapters.size());
            ReadingRouter.openReading(this, rs);
        } catch (Throwable t) {
            SpiderDebug.log("video-flow", "reading open failed: %s", t.getMessage());
            return false;
        }
        // 详情页保留在阅读器之下：从阅读器返回落回详情页，不 finish。
        return true;
    }

    // ================= 阅读不漏认两层（源级标记 / 详情级判别） =================
    // 「用阅读器打开」手动兜底已摘除（2026-10-18 他拍板）：识别靠自动判别层做准。

    /**
     * 详情级改道进行中（不漏认两层）：源标记 / 整书判别命中后，
     * 后台探针确认内容期间为 true——抑制自动播点火与选集点击，
     * 探针无结果则复位回落详情页。
     */
    private volatile boolean mReadingDetailDiverting = false;

    /**
     * 本次详情访问已自动改道过一次：抑制详情级改道的二次自动触发
     * （回到前台后不再自动跳阅读器）。用户手动点集不受影响，
     * 仍可再进。生命周期与 mReadingDetailDiverting 同步，getDetail() 换书时复位。
     */
    private volatile boolean mReadingDetailDivertedOnce = false;

    private String siteNameOf() {
        try {
            Site site = VodConfig.get().getSite(getKey());
            if (site != null && !TextUtils.isEmpty(site.getName())) return site.getName();
        } catch (Throwable ignored) {
        }
        return "";
    }

    // ================= 阅读不漏认两层（源级标记 / 详情级判别） =================
    // 优先级：源标记 ＞ 详情判别 ＞ 内容判别（playerContent 内）。
    // 命中→后台探针确认内容形态→开阅读器整页改道。

    /**
     * 详情级改道判定（详情打开 / 缓存展开后调用）。
     *
     * @param typeName 详情分类/类型名
     * @return true=已启动改道（调用方不再自动播）
     */
    private boolean maybeDivertDetailToReading(String typeName) {
        if (mReadingDetailDiverting) return true;
        if (mReadingDetailDivertedOnce) return false;  // 本次访问已改道过一次：回前台后不再自动改道
        try {
            if (isEmbyPlayback()) return false;           // 红线：Emby 纯视频
            String key = getKey();
            if (SiteApi.PUSH.equals(key)) return false;   // push_agent 走 txt 混合链
            if (isDirectPlaySession()) return false;      // 海阔直播/直播会话自有链
            List<Episode> eps = episodeListForReader();
            if (eps == null || eps.isEmpty()) return false;
            ReadingDetailJudge.Verdict v = ReadingDetailJudge.judgeDetail(ReadingDetailDivert.chapterNames(eps), typeName);
            if (v == null || !v.isBook) return false;  // fail-open：判不中原样
            if (ReadingDetailDivert.hasAudioChapterUrl(ReadingDetailDivert.toChapters(eps))) return false;
            mReadingDetailDiverting = true;
            startDetailReadingProbe(key, eps, "detail-judge:" + v.reason);
            return true;
        } catch (Throwable t) {
            mReadingDetailDiverting = false;
            return false;
        }
    }

    /** 后台探针确认内容后开阅读器；探针未确认则复位回落详情页，不打扰用户。 */
    private void startDetailReadingProbe(String key, List<Episode> eps, String why) {
        Flag flag = getFlag();
        Episode target = getEpisode();
        if (target == null || TextUtils.isEmpty(target.getUrl())) {
            target = null;
            for (Episode e : eps) {
                if (e != null && !TextUtils.isEmpty(e.getUrl())) {
                    target = e;
                    break;
                }
            }
        }
        if (target == null) {
            mReadingDetailDiverting = false;
            return;
        }
        final String episodeName = target.getName();
        final String chapterId = target.getUrl();
        final String playFlag = getEpisodePlayFlag(flag, target);
        final String siteName = siteNameOf();
        final String ruleTitle = HkDetailBridge.isHkKey(key) ? key.substring(HkDetailBridge.KEY_PREFIX.length()) : "";
        final long probeSeq = mDetailParseSeq;
        new Thread(() -> {
            try {
                final ReadingSession rs = ReadingManual.probeSession(key, playFlag, siteName, ruleTitle, episodeName, chapterId);
                App.post(() -> {
                    if (isFinishing() || isDestroyed()) {
                        mReadingDetailDiverting = false;
                        return;
                    }
                    if (rs == null) {
                        SpiderDebug.log("reading-nomiss", "detail probe no session key=%s why=%s", key, why);
                        mReadingDetailDiverting = false;
                        resumeAutoPlayAfterProbe(key, probeSeq);
                        return;
                    }
                    SpiderDebug.log("reading-nomiss", "detail divert key=%s why=%s kind=%s", key, why, rs.kind);
                    // 探针结束：选集点击恢复；一次性抑制：回到详情页后不再自动改道。
                    // 用户手动点集/点手动入口仍可再进；详情页不 finish，返回落回本页。
                    mReadingDetailDiverting = false;
                    mReadingDetailDivertedOnce = true;
                    openReadingSession(rs, false);
                });
            } catch (Throwable t) {
                SpiderDebug.log("reading-nomiss", "detail probe failed key=%s err=%s", key, String.valueOf(t.getMessage()));
                App.post(() -> {
                    mReadingDetailDiverting = false;
                    resumeAutoPlayAfterProbe(key, probeSeq);
                });
            }
        }).start();
        // 只修卡住：探针线程永久挂起时旗在超时后复位，改道功能本身不动。
        // 超时值出处：本文件 PREPARSE_WAIT_MS（25000，注释“与解析超时同量级”），
        // 不发明新量级。只复位旗（探针否定路径同款静默回落，不补点火、不提示，
        // 用户可手动点集；迟到的探针成功仍按原路开阅读器）。
        // 世代守卫：换书时 getDetail() 递增 mDetailParseSeq 并自清旗，旧定时不动作。
        App.post(() -> {
            if (probeSeq != mDetailParseSeq) return;
            if (!mReadingDetailDiverting) return;
            SpiderDebug.log("reading-nomiss", "detail probe timeout reset key=%s", key);
            mReadingDetailDiverting = false;
        }, PREPARSE_WAIT_MS);
    }

    /**
     * 探针否定后自动播补点火：旗拦住的那次 bindDetailService 点火不能就此吞掉
     * （上游形：自动播点火无条件发生，旗只拦不吞）。守卫：仍是同一详情
     * （世代未变）、服务已就绪、当前无播放——用户已手动播或已切走时不重播。
     */
    private void resumeAutoPlayAfterProbe(String probeKey, long probeSeq) {
        if (probeSeq != mDetailParseSeq) return;
        if (!TextUtils.equals(probeKey, getKey())) return;
        if (isFinishing() || isDestroyed()) return;
        if (service() == null || !player().isEmpty()) return;
        Vod item = mAiVod;
        if (item == null) return;
        bindDetailService(item, false);
    }

    /** 按章节地址找回 Episode（取其线路 flag 用；找不到回 null）。 */
    private Episode findEpisode(String chapterId) {
        try {
            List<Episode> eps = episodeListForReader();
            if (eps != null) {
                for (Episode e : eps) {
                    if (e != null && TextUtils.equals(e.getUrl(), chapterId)) return e;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 阅读器用章节表：当前线路的选集列表。 */
    private List<Episode> episodeListForReader() {
        try {
            Flag flag = getFlag();
            if (flag != null && flag.getEpisodes() != null && !flag.getEpisodes().isEmpty()) {
                return flag.getEpisodes();
            }
            if (mEpisodeAdapter != null && !mEpisodeAdapter.isEmpty()) {
                return mEpisodeAdapter.getItems();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * 打点说明：replay=true 只用于 onReclaim 重播旧解析结果。此时 playerStartTime 是过期
     * 时间戳（上次 getPlayer 留的），不能再拿它算耗时——否则会算出几十秒的虚假 cost。
     * 回放单独记一行 player replay，不复用 player finish。
     */
    private void setPlayer(Result result, boolean replay) {
        if (isFinishing() || isDestroyed()) return;
        // 重播记录（原 ViewModel player LiveData 值的等效存放）：onReclaim 只读它，
        // 服务就绪点火从不读它——它不是暂存槽。
        mLastPlayerResult = result;
        // 阅读分流：判别层命中的漫画/小说改道阅读器，不进播放器。
        // replay（进程重建回放）不改道：Result 序列化会丢 transient 会话。
        if (!replay && divertToReader(result)) return;
        mUserExitedFullscreenForThisVideo = false;
        if (service() == null) {
            // 统一口径：派发时服务不可用一律存请求、就绪后重跑，不留“提示＋用户再点”尾巴。
            // 已知调用点（直通路／预解析／在途预解析）均已在派发前显式暂存三元组；走到这里
            // 只剩未传三元组的兜底（重播）：按当前选集／历史集推导请求。推导不出（无选集
            // 且历史集无地址）时无可重跑的请求，才保留可见提示。
            Episode current = getEpisode();
            if (current == null && mHistory != null) current = mHistory.getEpisode();
            if (current != null && !TextUtils.isEmpty(current.getUrl())) {
                SpiderDebug.log("video-flow", "player result without service, request deferred from selection key=%s id=%s", getKey(), getId());
                deferPlayRequest(getKey(), getEpisodePlayFlag(getFlag(), current), current.getUrl());
                return;
            }
            SpiderDebug.log("video-flow", "player result without service, no selectable episode key=%s id=%s", getKey(), getId());
            Notify.show("播放器服务未就绪，请稍后再试");
            return;
        }
        if (replay) {
            SpiderDebug.log("video-flow", "player replay (reclaim) key=%s", getKey());
        } else {
            SpiderDebug.log("video-flow", "player finish cost=%dms useParse=%s multi=%s msg=%s", System.currentTimeMillis() - playerStartTime, result.shouldUseParse(), result.getUrl().isMulti(), result.getMsg());
        }
        mQualityAdapter.addAll(result);
        setUseParse(result.shouldUseParse());
        mBinding.swipeLayout.setRefreshing(false);
        setQualityVisible(result.getUrl().isMulti());
        result.getUrl().set(mQualityAdapter.getPosition());
        if (result.hasArtwork() && !shouldKeepPushArtwork()) setArtwork(result.getArtwork());
        else applyPlaybackArtwork(getPlaybackEpisode());
        if (result.hasPosition()) mHistory.setPosition(result.getPosition());
        if (result.hasDesc()) {
            setText(mBinding.content, R.string.detail_content, result.getDesc());
            updateContentFold();
            setPlaybackLyrics(result.getDesc());
        }
        updateAudioStageText();
        mBinding.control.parse.setVisibility(isUseParse() ? View.VISIBLE : View.GONE);
        List<Danmaku> siteDanmakus = result.getDanmaku();
        // 直达播放（115/本地/推送）不经 getPlayer，标题在此统一补刷，否则标题文本一直为空、
        // setSizeText 按「无标题」把它保持隐藏，小窗顶栏只剩分辨率一行。
        mBinding.control.title.setText(getPlaybackControlTitle());
        setSizeText();
        startPlayer(getHistoryKey(), result, isUseParse(), getSite().getTimeout(), buildMetadata());
        Episode playEpisode = getEpisode();
        if (DanmakuApi.canAutoSearch(siteDanmakus) && playEpisode != null)
            DanmakuApi.search(mHistory.getVodName(), playEpisode.getName(), player()::setDanmaku);
        onHongguoPlaybackStarted(result, playEpisode);
        if (mShortDramaMode && !isFullscreen()) enterFullscreen();
        preloadNextEpisode();
    }

    // ---------- 红果评论/弹幕（壳子直抓，与 HongguoCommentApi/HongguoDanmakuApi 配套） ----------

    /** 识别闸（设计 §4.2）：id 清洗后是纯数字，且站名含「红果」或任一选集带 hg-episode-v1: token。 */
    private boolean computeHongguoGate(List<Flag> flags) {
        if (HongguoCommentApi.cleanSeriesId(getId()).isEmpty()) return false;
        Site site = getSite();
        if (site != null && site.getName() != null && site.getName().contains("红果")) return true;
        if (flags != null) for (Flag flag : flags) {
            if (flag == null || flag.getEpisodes() == null) continue;
            for (Episode episode : flag.getEpisodes()) {
                if (episode != null && !HongguoCommentApi.episodeVid(episode.getUrl()).isEmpty()) return true;
            }
        }
        return false;
    }

    private void resetHongguoState(String key) {
        mCommentPrefetchKey = key;
        mHongguoSeriesId = "";
        mHongguoGate = false;
        mCommentPrefetching = false;
        mCommentAvailable = false;
        mCommentSeriesPage = null;
        mCommentEpisodePage = null;
        mCommentEpisodeVid = "";
        mHongguoDanmakuVid = "";
        mHongguoDanmakuPending = "";
        mHongguoDanmakuPrefetchVid = "";
    }

    /** 详情到达即判识别闸并预抓（短剧直进路径在 setPlayer 起播点补判）。 */
    private void prepareHongguoComments(Vod item) {
        String key = getHistoryKey();
        if (!key.equals(mCommentPrefetchKey)) resetHongguoState(key);
        mHongguoSeriesId = HongguoCommentApi.cleanSeriesId(getId());
        mHongguoGate = computeHongguoGate(item == null ? null : item.getFlags());
        if (mHongguoGate) prefetchComments();
        updateCommentEntries();
    }

    /** 预抓：先试剧评首屏，剧评真为空再试当前集评论首屏；任一真拿到评论才露入口（用户定调：抓到才出现）。 */
    private void prefetchComments() {
        if (mCommentPrefetching || mCommentAvailable || mHongguoSeriesId.isEmpty()) return;
        mCommentPrefetching = true;
        String key = mCommentPrefetchKey;
        String seriesId = mHongguoSeriesId;
        Episode episode = getEpisode();
        String vid = episode == null ? "" : HongguoCommentApi.episodeVid(episode.getUrl());
        mHongguoExecutor.execute(() -> {
            HongguoCommentApi.Page series = HongguoCommentApi.seriesComments(seriesId, "");
            HongguoCommentApi.Page episodePage = null;
            boolean available = series != null && (series.total > 0 || !series.items.isEmpty());
            if (!available && !vid.isEmpty()) {
                episodePage = HongguoCommentApi.episodeComments(seriesId, vid, "");
                available = episodePage != null && (episodePage.total > 0 || !episodePage.items.isEmpty());
            }
            HongguoCommentApi.Page fSeries = series;
            HongguoCommentApi.Page fEpisode = episodePage;
            boolean fAvailable = available;
            App.post(() -> {
                if (isFinishing() || isDestroyed() || !key.equals(mCommentPrefetchKey)) return;
                mCommentPrefetching = false;
                mCommentSeriesPage = fSeries;
                if (fEpisode != null) {
                    mCommentEpisodePage = fEpisode;
                    mCommentEpisodeVid = vid;
                }
                mCommentAvailable = fAvailable;
                updateCommentEntries();
            });
        });
    }

    /** 起播点：直进全屏等无详情路径在此补判识别闸；已开着的评论面板跟随切集；顺手备本集红果弹幕。 */
    private void onHongguoPlaybackStarted(Result result, Episode playEpisode) {
        if (playEpisode == null) return;
        String key = getHistoryKey();
        if (!key.equals(mCommentPrefetchKey)) resetHongguoState(key);
        String vid = HongguoCommentApi.episodeVid(playEpisode.getUrl());
        if (!mHongguoGate) {
            mHongguoSeriesId = HongguoCommentApi.cleanSeriesId(getId());
            Site site = getSite();
            boolean named = site != null && site.getName() != null && site.getName().contains("红果");
            mHongguoGate = !mHongguoSeriesId.isEmpty() && (named || !vid.isEmpty());
        }
        if (!mHongguoGate) return;
        prefetchComments();
        for (androidx.fragment.app.Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof HongguoCommentDialog) ((HongguoCommentDialog) fragment).refreshEpisode(vid);
        }
        prepareHongguoDanmaku(result, vid);
        prefetchNextHongguoDanmaku(playEpisode);
    }

    /**
     * 红果弹幕制备：识别为红果的剧起播时按集增量拉弹幕转本地 XML——首窗拿到就落盘挂载，
     * 后续窗有新增重写同一文件、已挂的就重读。备好后进本集弹幕源列表（与 Result 同一列表引用，PlaySpec 共用）。
     * 标记纪律：mHongguoDanmakuVid 只在制备真出货时置位，失败不置——同集下次起播/切集还能重试
     * （旧口径发出制备前就置位，失败后本集永不再试，是「没弹幕」三脆弱点之一）。
     */
    private void prepareHongguoDanmaku(Result result, String vid) {
        if (vid.isEmpty()) return;
        if (vid.equals(mHongguoDanmakuVid)) return;
        if (vid.equals(mHongguoDanmakuPending)) return;
        if (!DanmakuSetting.isLoad() || !DanmakuSetting.isShow()) return;
        mHongguoDanmakuPending = vid;
        String key = mCommentPrefetchKey;
        String seriesId = mHongguoSeriesId;
        List<Danmaku> siteDanmakus = result.getDanmaku();
        SpiderDebug.log("hongguo-danmaku", "prepare submit vid=%s series=%s", vid, seriesId);
        mHongguoExecutor.execute(() -> {
            // 时长传缓存模型的 video_duration 真值（A-20）；本面走通用
            // 播放链、模型多半不在 DramaCache，在手才有真值、否则 0=未知。
            Danmaku item = HongguoDanmakuApi.fetchToFile(seriesId, vid, DramaCache.durationMs(vid),
                    partial -> App.post(() -> onHongguoDanmakuReady(key, vid, siteDanmakus, partial)));
            App.post(() -> {
                if (vid.equals(mHongguoDanmakuPending)) mHongguoDanmakuPending = "";
                SpiderDebug.log("hongguo-danmaku", "prepare done vid=%s item=%s", vid, item != null);
                if (item == null) return;
                mHongguoDanmakuVid = vid;
                onHongguoDanmakuReady(key, vid, siteDanmakus, item);
            });
        });
    }

    /**
     * 下一集弹幕预拉（用户口径：跟预加载一样，下一集提前拉）：当前集制备提交之后，把紧邻
     * 下一集的弹幕顺手排进同一条单线程队列——排在本集制备之后，不抢本集的窗请求。
     * 只落盘、永不挂载：预拉任务只调 fetchToFile 把文件备好，不碰 mHongguoDanmakuVid/
     * Pending、不调 onHongguoDanmakuReady；切过去时走既有制备路，完整缓存直接用、
     * 半截继续增量补。只预拉一集、不连环；连切多集时每轮起播按新位置重排新目标，
     * 旧目标没拉完的只留半截文件（按 vid 落盘，将来真看到那集还能续），挂错集无路可走：
     * 挂载唯一入口 onHongguoDanmakuReady 还要复核产出 vid 与当前集 vid 一致才挂。
     * 双开关关着不预拉；预拉失败静默，切过去再走正常制备。
     */
    private void prefetchNextHongguoDanmaku(Episode current) {
        if (!DanmakuSetting.isLoad() || !DanmakuSetting.isShow()) return;
        if (!hasAdjacentEpisode(1)) return;
        Episode next = getAdjacentEpisode(1);
        if (next == null || next.isSelected() || TextUtils.isEmpty(next.getUrl())) return;
        if (current != null && next.getUrl().equals(current.getUrl())) return;
        String nextVid = HongguoCommentApi.episodeVid(next.getUrl());
        if (nextVid.isEmpty() || nextVid.equals(mHongguoDanmakuPrefetchVid)) return;
        mHongguoDanmakuPrefetchVid = nextVid;
        String seriesId = mHongguoSeriesId;
        SpiderDebug.log("hongguo-danmaku", "prefetch submit vid=%s series=%s", nextVid, seriesId);
        mHongguoExecutor.execute(() -> HongguoDanmakuApi.fetchToFile(seriesId, nextVid, DramaCache.durationMs(nextVid)));
    }

    /** 制备产出到达（首份/增量新版/最终版走同一处理）：已挂的正是这份就重读文件；还没挂按源弹幕优先口径裁决。 */
    private void onHongguoDanmakuReady(String key, String vid, List<Danmaku> siteDanmakus, Danmaku item) {
        if (isFinishing() || isDestroyed() || !key.equals(mCommentPrefetchKey)) return;
        Episode current = getEpisode();
        if (current == null || !vid.equals(HongguoCommentApi.episodeVid(current.getUrl()))) return;
        SpiderDebug.log("hongguo-danmaku", "ready vid=%s selected=%s siteEmpty=%s", vid, isHongguoDanmakuSelected(item), siteDanmakus.isEmpty());
        if (isHongguoDanmakuSelected(item)) {
            // 文件已增量长大：强制重读，用户正看的这份才跟得上
            player().reloadDanmaku(item);
            return;
        }
        // 源弹幕优先的既有口径：本站没给弹幕、且当前没挂任何弹幕时才自动挂上；否则只进列表供用户在弹幕面板里选
        if (siteDanmakus.isEmpty() && !player().haveDanmaku()) {
            SpiderDebug.log("hongguo-danmaku", "attach vid=%s", vid);
            player().setDanmaku(item);
        }
        else if (!siteDanmakus.contains(item)) siteDanmakus.add(item);
    }

    /** 当前选中的弹幕是不是这份红果文件（按 url 对，PlaySpec 选中标记同口径）。 */
    private boolean isHongguoDanmakuSelected(Danmaku item) {
        PlaySpec spec = player().getSpec();
        if (spec == null || spec.getDanmakus() == null) return false;
        for (Danmaku danmaku : spec.getDanmakus()) {
            if (danmaku != null && danmaku.isSelected() && item.getUrl().equals(danmaku.getUrl())) return true;
        }
        return false;
    }

    /** 三处入口（详情页标题右/竖屏右侧栏/横屏底排）同一裁定：没抓到评论一律不露。 */
    private void updateCommentEntries() {
        boolean show = mCommentAvailable;
        if (mBinding.commentDetail != null) mBinding.commentDetail.setVisibility(show ? View.VISIBLE : View.GONE);
        boolean verticalFullscreen = isFullscreen() && !isActualLand();
        mBinding.control.right.commentVertical.setVisibility(show && verticalFullscreen && !isLock() ? View.VISIBLE : View.GONE);
        // 竖屏全屏时底排让位给右侧栏（与选集同口径），横屏走底排
        mBinding.control.action.commentAction.setVisibility(show && !verticalFullscreen ? View.VISIBLE : View.GONE);
    }

    private void onComment() {
        if (!mCommentAvailable || mHongguoSeriesId.isEmpty()) return;
        Episode episode = getEpisode();
        String vid = episode == null ? "" : HongguoCommentApi.episodeVid(episode.getUrl());
        // 预抓的单集首屏只在集对得上时才给面板，错集不喂
        HongguoCommentApi.Page episodePage = vid.equals(mCommentEpisodeVid) ? mCommentEpisodePage : null;
        HongguoCommentDialog.create()
                .seriesId(mHongguoSeriesId)
                .episode(vid)
                .rightSide(isFullscreen() && isActualLand())
                .cached(mCommentSeriesPage, episodePage)
                .show(this);
        hideControl();
    }

    /**
     * 下一集预加载：当前集开始播后，后台提前解析下一集 URL。
     * 只走 ExoPlayer 路径；最后一集不预加载。
     */
    private void preloadNextEpisode() {
        if (player().isMpv()) return;
        try {
            Episode next = getAdjacentEpisode(1);
            if (next == null || next.isSelected() || TextUtils.isEmpty(next.getUrl())) return;
            Episode current = getEpisode();
            // 单集时 getAdjacentEpisode 会钳位回当前集自己，预加载自己毫无意义（还白跑一遍解析）
            if (current != null && next.getUrl().equals(current.getUrl())) return;
            // 磁力/ed2k/迅雷类走迅雷提取器：同一时刻只养一个任务，后台预加载解析会经
            // playerContent 的 Source.stop() 把正在播的任务删掉并释放整个引擎，播放器手里
            // 的本地地址瞬间死亡（真机实证：推送磁力开播约 80KB/s 后流量归零直至失败）
            if (isThunderUrl(next.getUrl()) || (current != null && isThunderUrl(current.getUrl()))) return;
            String playFlag = getEpisodePlayFlag(getFlag(), next);
            // 数据预下要的探针与上下文：当前集进度（闸门/过半复查）、短剧标记、画质档位
            mNextPreloader.configureDataPrefetch(new NextEpisodePreloader.PlaybackProbe() {
                @Override
                public long positionMs() {
                    try {
                        return player().getPosition();
                    } catch (Throwable e) {
                        return -1;
                    }
                }

                @Override
                public long durationMs() {
                    try {
                        return player().getDuration();
                    } catch (Throwable e) {
                        return -1;
                    }
                }

                @Override
                public int bufferedPercent() {
                    try {
                        return player().getBufferedPercentage();
                    } catch (Throwable e) {
                        return -1;
                    }
                }

                @Override
                public boolean loading() {
                    try {
                        return player().isLoading();
                    } catch (Throwable e) {
                        return false;
                    }
                }
            }, mShortDramaMode, mQualityAdapter.getPosition());
            mNextPreloader.preload(getKey(), playFlag, next);
        } catch (Throwable ignored) {
        }
    }

    private boolean isThunderUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        // ftp 并入迅雷线：上游 Thunder SCHEMES 含 ftp，ftp 集同样禁后台预加载／
        // 预解析复用（预加载解析经 Source.stop() 会删掉在播任务），与磁力同款守卫。
        return lower.startsWith("magnet:") || lower.startsWith("ed2k:") || lower.startsWith("thunder:") || lower.startsWith("ftp://");
    }

    private boolean consumePendingOnServiceReady() {
        boolean consumed = false;
        Vod detail = mPendingDetailVod;
        if (detail != null) {
            mPendingDetailVod = null;
            // 阶段一（纯 UI）已在数据到达时画过，这里只补阶段二（需要播放器服务的部分）
            bindDetailService(detail, mPendingDetailDeferLists);
            consumed = true;
        }
        // 上游 onPlaybackServiceReady 语义在本树的对应关系：
        // ①先渲染已缓冲 metadata：本树无服务前 metadata 缓冲（metadata 在 setPlayer／
        // startPlayer 时由当前历史＋选集现算），待点火集的标题／封面已在 getPlayer 起播
        // 时画过，无需重渲染；②取 playing request：即下面这组待点火三元组；
        // ③有会话走预加载、否则恢复选集重请求：会话已在播该三元组时只做下一集预加载，
        // 不重解析；否则选集本就当前（点击时已切选），直接 playDirect 重跑解析。
        // 本方法由 onServiceConnected 在每次服务就绪时调用，不是只跑一次。
        if (mPendingPlayKey != null) {
            String key = mPendingPlayKey;
            String flag = mPendingPlayFlag;
            String url = mPendingPlayUrl;
            mPendingPlayKey = null;
            mPendingPlayFlag = null;
            mPendingPlayUrl = null;
            if (service() != null && !player().isEmpty() && TextUtils.equals(url, player().getUrl())) {
                SpiderDebug.log("video-flow", "pending play already served by session, preload only key=%s", key);
                preloadNextEpisode();
            } else {
                playDirect(key, flag, url);
            }
            consumed = true;
        }
        if (consumed && !player().isEmpty()) {
            refreshLyrics();
            syncKaraokePosition();
        }
        return consumed;
    }

    private void recordDetailHealth(Result result, long cost) {
        if (detailHealthRecorded) return;
        detailHealthRecorded = true;
        boolean success = result != null && !result.getList().isEmpty();
        String error = result == null ? "" : result.hasMsg() ? result.getMsg() : success ? "" : "empty";
        SiteHealthStore.recordDetail(getKey(), success, cost, error);
    }

    private void beginPlayHealth() {
        playHealthKey = getKey();
        playHealthRecorded = false;
    }

    private void recordPlayHealth(boolean success, String error) {
        if (playHealthRecorded) return;
        playHealthRecorded = true;
        SiteHealthStore.recordPlay(TextUtils.isEmpty(playHealthKey) ? getKey() : playHealthKey, success, error);
    }

    @Override
    public void onItemClick(Flag item) {
        if (item.isSelected()) return;
        mFlagAdapter.setSelected(item);
        scrollToPosition(mBinding.flag, mFlagAdapter.getPosition());
        setEpisodeAdapter(item.getEpisodes());
        scrollEpisodeToSelected();
        setQualityVisible(false);
        seamless(item);
        // Emby 画质行跟线路走：换线路重拉本集版本
        refreshEmbyQualityRow();
    }

    @Override
    public void onItemClick(Episode item) {
        // B3：不漏认两层改道中，探针确认前仍不点火（阅读改道功能本身不动），
        // 但不许静默吞点击：用本文件既有 Notify 机制给可见提示，用户可再点。
        if (mReadingDetailDiverting) {
            SpiderDebug.log("reading-nomiss", "episode click held by divert probe key=%s", getKey());
            Notify.show("正在确认内容类型，请稍后再点");
            return;
        }
        if (item != null && item.getUrl() != null && item.getUrl().startsWith("emby:")) {
            resetEmby410(EmbyNavigator.serverIdOf(item.getUrl()), EmbyNavigator.itemIdOf(item.getUrl()));
        }
        if (switchEmbyAiEpisode(item)) return;
        if (startEmbyVersionPick(item)) return;
        clickEpisode(item);
    }

    /** 原选集点击主流程（抽出来，给多版本单选回调用）。 */
    private void clickEpisode(Episode item) {
        if (shouldEnterFullscreen(item)) return;
        syncCurrentAudioPlaylistMetadata();
        Flag flag = getFlag();
        if (mFlagAdapter != null) mFlagAdapter.toggle(item);
        if (flag != null) setEpisodeAdapter(flag.getEpisodes());
        applyAudioQueueMetadata(item);
        if (isFullscreen()) Notify.show(getString(R.string.play_ready, item.getName()));
        onRefresh();
    }

    /**
     * Emby 多版本选集：同一条目有多个可直放版本（4K/1080 等）时，
     * 点集后直接按默认规则选版本开播（分辨率最高优先，同分辨率比码率），不再弹框。
     * 换版本走播放器二级菜单「画质」入口（onEmbyQualityPanel）。
     * 只看直放线路；转码线路走原画质机制；AI 链已在 EmbyNavigator 选过版本，不进这里。
     * 单版本/失败 → 回主线程走原点击流程。
     */
    private boolean startEmbyVersionPick(Episode item) {
        if (isEmbyPlayback()) return false;
        if (item == null || item.getUrl() == null || !item.getUrl().startsWith("emby:")) return false;
        Flag flag = getFlag();
        String flagName = flag == null ? "" : flag.getFlag();
        if (flagName.contains("转码")) return false;
        String sid = EmbyNavigator.serverIdOf(item.getUrl());
        String iid = EmbyNavigator.itemIdOf(item.getUrl());
        if (TextUtils.isEmpty(sid) || TextUtils.isEmpty(iid)) return false;
        // 画质行已选过本集：直接用行里的版本，不再重拉
        EmbyApi.PlayOption picked = mEmbyQPick.get(sid + ":" + iid + "|D");
        if (picked != null && !TextUtils.isEmpty(picked.url)) {
            playPickedEmbyVersion(item, picked);
            return true;
        }
        Notify.show(getString(R.string.emby_quality_loading));
        Task.execute(() -> {
            try {
                EmbyStore.EmbyServer s = EmbyStore.getServer(sid);
                if (s == null || !s.isLogin()) throw new Exception("未登录");
                EmbyApi api = new EmbyApi(s.baseUrl, s.token, s.userId, s.id);
                EmbyApi.PlaybackCandidates cand = api.playbackCandidates(iid, item.getName());
                List<EmbyApi.PlayOption> direct = new ArrayList<>();
                for (EmbyApi.PlayOption o : cand.options) {
                    if (o.direct && !TextUtils.isEmpty(o.url)) direct.add(o);
                }
                if (direct.size() <= 1) {
                    App.post(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        clickEpisode(item);
                    });
                    return;
                }
                EmbyApi.PlayOption best = EmbyApi.pickDefaultDirect(direct);
                if (best == null || TextUtils.isEmpty(best.url)) {
                    App.post(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        clickEpisode(item);
                    });
                    return;
                }
                final EmbyApi.PlayOption pick = best;
                App.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    playPickedEmbyVersion(item, pick);
                });
            } catch (Exception e) {
                // Emby 失败分支只弹提示、对齐普通源点播失败留详情页的行为：
                // 不再调 clickEpisode(item)——它会走 shouldEnterFullscreen 把人扔进横屏全屏黑屏。
                // 无收尾可清：本分支只发过一条 toast，未弹对话框、未改选集状态。
                App.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Notify.show("Emby 播放失败：" + e.getMessage());
                });
            }
        });
        return true;
    }

    /** 播选中的版本：UI 更新走原点击流程，播放走 PUSH 直链（版本地址已解析好）。 */
    private void playPickedEmbyVersion(Episode item, EmbyApi.PlayOption opt) {
        if (shouldEnterFullscreen(item)) return;
        mDetailWallOff = false;
        resetEmby410(EmbyNavigator.serverIdOf(item.getUrl()), EmbyNavigator.itemIdOf(item.getUrl()));
        mEmbyCurrentOpt = opt;
        syncCurrentAudioPlaylistMetadata();
        Flag flag = getFlag();
        if (mFlagAdapter != null) mFlagAdapter.toggle(item);
        if (flag != null) setEpisodeAdapter(flag.getEpisodes());
        applyAudioQueueMetadata(item);
        if (isFullscreen()) Notify.show(getString(R.string.play_ready, item.getName()));
        mBinding.control.title.setText(getPlaybackControlTitle(item));
        saveHistory();
        updateHistory(item);
        try {
            player().stop();
            player().clear();
        } catch (Throwable ignored) {
        }
        mClock.setCallback(null);
        clearLyrics();
        showProgress();
        getIntent().putExtra("id", opt.url);
        // 固定走 PUSH 直链口径：版本地址已解析好，不进 spider
        playDirect(SiteApi.PUSH, "", opt.url);
    }

    /**
     * Emby 详情页画质行：线路行下面，多版本（MediaSources）各一档。
     * 单版本/取数失败/非 Emby → 按"该藏的藏"不露脸。
     * 默认选中沿用现链默认（直放线路=pickDefaultDirect，转码线路=首个非直放），不自创优先级。
     */
    private void refreshEmbyQualityRow() {
        Vod item = mAiVod;
        boolean emby = item != null && isEmbyDetail(item);
        if (!emby) {
            clearEmbyQualityRow();
            return;
        }
        Flag flag = getFlag();
        Episode ep = getEpisode();
        if (flag == null || ep == null || ep.getUrl() == null || !ep.getUrl().startsWith("emby:")) {
            clearEmbyQualityRow();
            return;
        }
        String sid = EmbyNavigator.serverIdOf(ep.getUrl());
        String iid = EmbyNavigator.itemIdOf(ep.getUrl());
        if (TextUtils.isEmpty(sid) || TextUtils.isEmpty(iid)) {
            clearEmbyQualityRow();
            return;
        }
        boolean transcode = flag.getFlag() != null && flag.getFlag().contains("转码");
        String key = sid + ":" + iid + "|" + (transcode ? "T" : "D");
        if (key.equals(mEmbyQKey) && mEmbyQOptions != null) {
            renderEmbyQualityRow();
            return;
        }
        mEmbyQKey = key;
        mEmbyQOptions = null;
        if (mEmbyQShowing) setQualityVisible(false);
        if (transcode) {
            // 转码线路走 spider：先清掉旧选择，取数完成前点播走现链默认
            EmbySpider.setPreferredSource("emby:" + sid + ":" + iid, null);
        }
        long seq = ++mEmbyQSeq;
        final String fSid = sid;
        final String fIid = iid;
        final boolean fTrans = transcode;
        final String fKey = key;
        Task.execute(() -> {
            List<EmbyApi.PlayOption> options = null;
            try {
                EmbyStore.EmbyServer s = EmbyStore.getServer(fSid);
                if (s == null || !s.isLogin()) throw new Exception("未登录");
                EmbyApi api = new EmbyApi(s.baseUrl, s.token, s.userId, s.id);
                options = api.playbackCandidates(fIid, "").options;
            } catch (Throwable ignored) {
            }
            final List<EmbyApi.PlayOption> opts = options;
            App.post(() -> {
                if (seq != mEmbyQSeq || isFinishing() || isDestroyed()) return;
                if (!fKey.equals(mEmbyQKey)) return;
                if (opts == null || opts.size() <= 1) {
                    clearEmbyQualityRow();
                    return;
                }
                if (player() != null && !player().isEmpty()) return;
                int def = defaultEmbyQualityIndex(opts, fTrans);
                mEmbyQOptions = opts;
                EmbyApi.PlayOption pick = opts.get(def);
                mEmbyQPick.put(fKey, pick);
                if (fTrans) {
                    // 转码线路走 spider：把默认版本记过去，播放按行选择走
                    EmbySpider.setPreferredSource("emby:" + fSid + ":" + fIid, pick.mediaSourceId);
                }
                renderEmbyQualityRow();
            });
        });
    }

    /** 现链默认：直放线路取 pickDefaultDirect 在列表中的下标；转码线路取首个非直放，否则 0。 */
    private int defaultEmbyQualityIndex(List<EmbyApi.PlayOption> opts, boolean transcode) {
        if (transcode) {
            for (int i = 0; i < opts.size(); i++) if (!opts.get(i).direct) return i;
            return 0;
        }
        List<EmbyApi.PlayOption> direct = new ArrayList<>();
        for (EmbyApi.PlayOption o : opts) if (o.direct) direct.add(o);
        EmbyApi.PlayOption best = EmbyApi.pickDefaultDirect(direct);
        if (best != null) {
            for (int i = 0; i < opts.size(); i++) if (opts.get(i) == best) return i;
        }
        return 0;
    }

    private void renderEmbyQualityRow() {
        if (mEmbyQOptions == null || mEmbyQOptions.size() <= 1 || mQualityAdapter == null) {
            clearEmbyQualityRow();
            return;
        }
        boolean transcode = mEmbyQKey.endsWith("|T");
        List<String> labels = new ArrayList<>();
        for (EmbyApi.PlayOption o : mEmbyQOptions) labels.add(o.qualityRowLabel(transcode));
        int sel = defaultEmbyQualityIndex(mEmbyQOptions, transcode);
        EmbyApi.PlayOption pick = mEmbyQPick.get(mEmbyQKey);
        if (pick != null) {
            for (int i = 0; i < mEmbyQOptions.size(); i++) {
                if (pick.mediaSourceId.equals(mEmbyQOptions.get(i).mediaSourceId)) {
                    sel = i;
                    break;
                }
            }
        }
        mQualityAdapter.setSimpleItems(labels, sel, this::onEmbyQualityPick);
        mEmbyQShowing = true;
        setQualityVisible(true);
    }

    /** 画质行点选：记下本集本线路的选择；转码线路同步给 spider。 */
    private void onEmbyQualityPick(int pos) {
        if (mEmbyQOptions == null || pos < 0 || pos >= mEmbyQOptions.size() || TextUtils.isEmpty(mEmbyQKey)) return;
        EmbyApi.PlayOption opt = mEmbyQOptions.get(pos);
        mEmbyQPick.put(mEmbyQKey, opt);
        int p = mEmbyQKey.indexOf('|');
        String epId = p > 0 ? mEmbyQKey.substring(0, p) : "";
        if (mEmbyQKey.endsWith("|T") && epId.startsWith("emby:")) {
            EmbySpider.setPreferredSource(epId, opt.mediaSourceId);
        }
        renderEmbyQualityRow();
    }

    /** 非 Emby / 单版本 / 失败时收起画质行；非 Emby 时不动通用 Result 模式。 */
    private void clearEmbyQualityRow() {
        mEmbyQOptions = null;
        mEmbyQKey = "";
        if (mEmbyQShowing && mQualityAdapter != null) {
            mQualityAdapter.setSimpleItems(null, 0, null);
            mEmbyQShowing = false;
            setQualityVisible(false);
        }
    }

    @Override
    public void onItemClick(EpisodeGroupAdapter.Group item) {
        mEpisodeGroupAdapter.setSelected(item);
        scrollEpisodeToPosition(item.start);
        scrollToPosition(mBinding.episodeGroup, mEpisodeGroupAdapter.getPosition());
    }

    /**
     * 选集行 Emby 集长按：标记已看/取消已看（P0-5）。
     * EpisodeAdapter 的 onItemLongClick 钩子 holders 已接好，这里只处理 emby: 开头的集，
     * 其它源保持原样（默认空实现）。
     */
    @Override
    public void onItemLongClick(Episode item) {
        if (item == null || item.getUrl() == null || !item.getUrl().startsWith("emby:")) return;
        String sid = EmbyNavigator.serverIdOf(item.getUrl());
        String iid = EmbyNavigator.itemIdOf(item.getUrl());
        if (TextUtils.isEmpty(sid) || TextUtils.isEmpty(iid)) return;
        Notify.show("正在查询…");
        Task.execute(() -> {
            Boolean played = null;
            try {
                EmbyStore.EmbyServer s = EmbyStore.getServer(sid);
                if (s != null && s.isLogin()) {
                    EmbyItem ei = new EmbyApi(s.baseUrl, s.token, s.userId, s.id).getItem(iid);
                    if (ei != null) played = ei.played;
                }
            } catch (Exception ignored) {
            }
            final Boolean p = played;
            App.post(() -> {
                if (p == null) {
                    Notify.show(getString(R.string.emby_action_fail));
                    return;
                }
                String[] titles = {
                        p ? getString(R.string.emby_menu_unmark_played) : getString(R.string.emby_menu_mark_played)
                };
                new AlertDialog.Builder(this, R.style.ThemeOverlay_XingChen_GlassDialog_Dark).setItems(titles, (d, which) ->
                        toggleEmbyEpisodePlayed(sid, iid, !p)).show();
            });
        });
    }

    private void toggleEmbyEpisodePlayed(String sid, String iid, boolean toPlayed) {
        Task.execute(() -> {
            boolean ok = false;
            try {
                EmbyStore.EmbyServer s = EmbyStore.getServer(sid);
                if (s != null && s.isLogin()) {
                    EmbyApi api = new EmbyApi(s.baseUrl, s.token, s.userId, s.id);
                    if (toPlayed) api.markPlayed(iid);
                    else api.unmarkPlayed(iid);
                    ok = true;
                }
            } catch (Exception ignored) {
            }
            final boolean r = ok;
            App.post(() -> Notify.show(r
                    ? getString(toPlayed ? R.string.emby_action_mark_played_ok : R.string.emby_action_unmark_played_ok)
                    : getString(R.string.emby_action_fail)));
        });
    }

    @Override
    public void onItemClick(Result result) {
        beginPlayHealth();
        startPlayer(getHistoryKey(), result, isUseParse(), getSite().getTimeout(), buildMetadata());
    }

    @Override
    public void onItemClick(Vod item) {
        setAutoMode(false);
        // 与其它入口同口径：音乐先走音乐分流，不许直达视频详情页（深挖 F-04，违音乐直通口径）
        if (MusicVodRouter.routeVod(this, item.getSiteKey(), item.getId(), item.getName(), item.getPic())) return;
        applySearchArtwork(item);
        // 短剧判定三路并集：插件/站点标记优先于封面比例，不许用封面一路直接赋值冲掉标记（深挖 F-04）
        mShortDramaMode = com.fongmi.android.tv.utils.ShortDramaHelper.shouldDirectPlay(item.getSiteKey(), null, item.getTypeName())
                || CoverRatioCache.isShortDramaCover(item.getPic());
        getDetail(item);
    }

    @Override
    public void onItemClick(Parse item) {
        setParse(item);
        onRefresh();
    }

    private void setParse(Parse item) {
        VodConfig.get().setParse(item);
        notifyItemChanged(mBinding.control.parse, mParseAdapter);
    }

    private void setEpisodeAdapter(List<Episode> items) {
        // 打点：detail 返回 → 首次选集渲染的耗时（video-flow 既有体系，真机对时序用）
        if (markEpisodeRender) {
            markEpisodeRender = false;
            SpiderDebug.log("video-flow", "episode render delay=%dms size=%d", System.currentTimeMillis() - detailFinishTime, items.size());
        }
        int size = items.size();
        mBinding.control.action.episodes.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        applyActionButtonVisibility();
        mBinding.control.next.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.prev.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        mBinding.reverse.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        mBinding.episode.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.more.setVisibility(View.GONE);
        List<EpisodeGroupAdapter.Group> groups = EpisodeGroupAdapter.build(size, getSelectedEpisodePosition(items), mHistory != null && mHistory.isRevSort());
        mEpisodeGroupAdapter.addAll(groups);
        mBinding.episodeGroup.setVisibility(groups.size() > 1 ? View.VISIBLE : View.GONE);
        setEpisodeItems(items);
        mBinding.episode.post(this::updateEpisodeViewportHeight);
        if (mAudioStageVisible) applyAudioPageMode(true);
        updateAudioStageControls();
    }

    private void setEpisodeItems(List<Episode> items) {
        mEpisodeAdapter.setCompactEnabled(!HkDetailBridge.isHkKey(getKey()));
        updateEpisodeSpan(items);
        mEpisodeAdapter.addAll(items);
        stripVodNamePrefix(items);
        selectEpisodeGroupByPosition(mEpisodeAdapter.getPosition());
    }

    /**
     * 选集钮删片名（显示层）：集名以详情片名开头时剥掉片名那截，剥完为空显示「播放地址」。
     * 锚定详情 vodName 归一化全等比较；只改 displayName，不动原始名与 URL。
     */
    private void stripVodNamePrefix(List<Episode> items) {
        if (items == null || items.isEmpty()) return;
        String anchor = normTitle(mHistory == null ? null : mHistory.getVodName());
        if (TextUtils.isEmpty(anchor)) return;
        boolean changed = false;
        for (Episode item : items) {
            if (item == null || item.isSingleEpisodeTitle()) continue;
            String name = normTitle(item.getDisplayName());
            if (TextUtils.isEmpty(name) || !name.startsWith(anchor)) continue;
            String rest = name.substring(anchor.length()).replaceAll("^[\\s\\-–—▶:：]+", "").trim();
            if (rest.isEmpty()) rest = "播放地址";
            if (!rest.equals(item.getDisplayName())) {
                item.setDisplayName(rest);
                changed = true;
            }
        }
        if (changed) mEpisodeAdapter.notifyDataSetChanged();
    }

    private static String normTitle(String s) {
        if (s == null) return "";
        return s.replace('\u3000', ' ').trim();
    }

    private void syncEpisodeGroupByScroll() {
        RecyclerView.LayoutManager manager = mBinding.episode.getLayoutManager();
        if (!(manager instanceof GridLayoutManager)) return;
        int position = getEpisodeGroupSyncPosition((GridLayoutManager) manager);
        if (position == RecyclerView.NO_POSITION) return;
        selectEpisodeGroupByPosition(position);
    }

    private int getEpisodeGroupSyncPosition(GridLayoutManager manager) {
        if (!mBinding.episode.canScrollVertically(1) && mBinding.episode.canScrollVertically(-1)) {
            return manager.findLastVisibleItemPosition();
        }
        return manager.findFirstVisibleItemPosition();
    }

    private void selectEpisodeGroupByPosition(int position) {
        // 无选中（getPosition() == -1）时不选任何分组，不再按第 1 集定位
        if (position < 0) return;
        if (mEpisodeGroupAdapter == null || mEpisodeGroupAdapter.isEmpty()) return;
        int current = mEpisodeGroupAdapter.getPosition();
        List<EpisodeGroupAdapter.Group> groups = mEpisodeGroupAdapter.getItems();
        for (int i = 0; i < groups.size(); i++) {
            EpisodeGroupAdapter.Group group = groups.get(i);
            if (position < group.start || position >= group.end) continue;
            if (i != current) {
                mEpisodeGroupAdapter.setSelected(group);
                mBinding.episodeGroup.scrollToPosition(i);
            }
            return;
        }
    }

    private void scrollEpisodeToPosition(int position) {
        // 无选中（getPosition() == -1）时不滚动，不再滚到第 1 集，也避免把 -1 传给 LayoutManager
        if (position < 0) return;
        RecyclerView.LayoutManager manager = mBinding.episode.getLayoutManager();
        if (manager instanceof GridLayoutManager) {
            int rowStart = getEpisodeRowStart((GridLayoutManager) manager, position);
            int offset = rowStart >= ((GridLayoutManager) manager).getSpanCount() ? -ResUtil.dp2px(4) : 0;
            ((GridLayoutManager) manager).scrollToPositionWithOffset(rowStart, offset);
        }
        else mBinding.episode.scrollToPosition(position);
    }

    private void scrollEpisodeToSelected() {
        mBinding.episode.post(() -> scrollEpisodeToPosition(mEpisodeAdapter.getPosition()));
    }

    private int getEpisodeRowStart(GridLayoutManager manager, int position) {
        int span = Math.max(1, manager.getSpanCount());
        return Math.max(0, position - position % span);
    }

    private void updateEpisodeSpan(List<Episode> items) {
        int span = getEpisodeSpan(items);
        if (span == mEpisodeSpanCount) return;
        mEpisodeSpanCount = span;
        mBinding.episode.setLayoutManager(new GridLayoutManager(this, mEpisodeSpanCount));
        if (mEpisodeDecoration != null) mBinding.episode.removeItemDecoration(mEpisodeDecoration);
        mBinding.episode.addItemDecoration(mEpisodeDecoration = new SpaceItemDecoration(mEpisodeSpanCount, 8));
    }

    private int getEpisodeSpan(List<Episode> items) {
        if (!HkDetailBridge.isHkKey(getKey())) EpisodeTitleCompact.apply(items);
        if (items.size() == 1) return 1;
        int maxLen = 0;
        for (Episode item : items) maxLen = Math.max(maxLen, item.getDisplayName().length());
        if (maxLen >= 12) return PlayerSetting.getEpisodeColumn();
        int ideal = maxLen >= 10 ? 130 : maxLen >= 7 ? 104 : 80;
        int width = EpisodeGridLayoutPolicy.getAvailableWidth(
                mBinding.episode.getWidth(),
                ResUtil.getScreenWidth(this),
                ResUtil.getScreenHeight(this),
                ResUtil.dp2px(32),
                isLand(),
                ResUtil.isLand(this));
        int span = width / ResUtil.dp2px(ideal);
        return Math.max(2, Math.min(getEpisodeSpanCount(), span));
    }

    private int getSelectedEpisodePosition(List<Episode> items) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).isSelected()) return i;
        return 0;
    }

    /** 已有预选中集（直接播放流按用户点击集预选）：不吃历史回选，保留用户点的那一集。 */
    private boolean hasPreselectedEpisode(Flag flag) {
        if (flag == null) return false;
        for (Episode ep : flag.getEpisodes()) if (ep.isSelected()) return true;
        return false;
    }

    private void syncSelectedEpisode(Flag flag) {
        if (flag == null || mHistory == null) return;
        // 直接播放流（海阔选集/txt）已预选中第 N 集：不再用历史集覆盖，否则显示被切回第 1 集
        if (hasPreselectedEpisode(flag)) return;
        Episode episode = flag.find(mHistory.getEpisode(), false);
        if (episode != null) flag.toggle(true, episode);
    }

    private int getEpisodeCount() {
        Flag flag = getFlag();
        return flag == null ? mEpisodeAdapter.getItemCount() : flag.getEpisodes() == null ? 0 : flag.getEpisodes().size();
    }

    private void seamless(Flag flag) {
        // 直接播放流（海阔选集/txt/推送）已按用户点击集预选中，不吃历史回选；
        // 否则点的第 N 集会被新剧默认历史（第 1 集）切回第 1 集（红果 1.3.30 真机日志实证）
        if (hasPreselectedEpisode(flag)) return;
        Episode episode = getMark().isEmpty() ? flag.find(mHistory.getEpisode(), true) : flag.find(mHistory.getVodRemarks(), false);
        setQualityVisible(episode != null && episode.isSelected() && mQualityAdapter.getItemCount() > 1);
        if (episode == null || episode.isSelected()) return;
        mHistory.setVodRemarks(episode.getName());
        mHistory.setEpisodeUrl(episode.getUrl());
        onItemClick(episode);
    }

    private void setQualityVisible(boolean visible) {
        mBinding.qualityText.setVisibility(visible && !mAudioStageVisible ? View.VISIBLE : View.GONE);
        mBinding.quality.setVisibility(visible && !mAudioStageVisible ? View.VISIBLE : View.GONE);
    }

    private void reverseEpisode(boolean scroll) {
        Flag flag = getFlag();
        if (flag == null) return;
        mFlagAdapter.reverse();
        setEpisodeAdapter(flag.getEpisodes());
        if (scroll) scrollEpisodeToSelected();
    }

    private void onName() {
        String name = mBinding.name.getText().toString();
        Notify.show(getString(R.string.detail_search, name));
        showQuickSearch(name);
        initSearch(name, false);
    }

    private void onSearch() {
        if (onLyricsSearch()) return;
        onName();
    }

    private boolean onLyricsSearch() {
        if (!isLyricsSearchAvailable()) return false;
        showLyricsSearchSheet(getLyricsSearchKeyword(), getLyricsSearchSuggestions());
        return true;
    }

    private void showLyricsSearchSheet(String keyword, List<String> suggestions) {
        int searchSeqAtOpen = mLyricsSearchSeq;
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        root.addView(createAudioSheetTitle(getString(R.string.player_lyrics_reload)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(34)));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextInputLayout layout = new TextInputLayout(this);
        styleAudioSheetInput(layout, getString(R.string.player_lyrics_keyword));
        TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setSingleLine(true);
        input.setMaxLines(1);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0x70FFFFFF);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setText(TextUtils.isEmpty(keyword) ? "" : keyword);
        if (input.getText() != null) input.setSelection(input.getText().length());
        layout.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(layout, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(ResUtil.dp2px(50), ResUtil.dp2px(50));
        searchParams.leftMargin = ResUtil.dp2px(10);
        row.addView(createAudioSheetIconButton(R.drawable.ic_action_search, () -> submitLyricsSearchSheet(dialog, input)), searchParams);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = ResUtil.dp2px(12);
        root.addView(row, inputParams);
        addLyricsSearchSuggestions(root, input, suggestions);

        dialog.setContentView(root);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            submitLyricsSearchSheet(dialog, input);
            return true;
        });
        showCompactPlaybackSheet(dialog);
        String autoKeyword = firstLyricsSearchSuggestion(suggestions);
        input.post(() -> {
            if (!dialog.isShowing() || mLyricsSearchSeq != searchSeqAtOpen) return;
            String current = input.getText() == null ? "" : input.getText().toString();
            if (!TextUtils.isEmpty(autoKeyword) && (TextUtils.isEmpty(current) || TextUtils.equals(current, keyword))) {
                input.setText(autoKeyword);
                if (input.getText() != null) input.setSelection(input.getText().length());
                Util.hideKeyboard(input);
                SpiderDebug.log("lyrics-ui", "mobile auto search suggestion=%s", autoKeyword);
                searchLyrics(autoKeyword);
            } else {
                Util.showKeyboard(input);
            }
        });
    }

    private String firstLyricsSearchSuggestion(List<String> suggestions) {
        if (suggestions == null) return "";
        for (String suggestion : suggestions) {
            String value = Objects.toString(suggestion, "").trim();
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private void addLyricsSearchSuggestions(LinearLayout root, TextInputEditText input, List<String> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) return;
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(HorizontalScrollView.OVER_SCROLL_NEVER);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int count = Math.min(8, suggestions.size());
        for (int i = 0; i < count; i++) {
            String text = suggestions.get(i);
            if (TextUtils.isEmpty(text)) continue;
            TextView chip = createLyricsSearchSuggestionChip(input, text);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(32));
            if (row.getChildCount() > 0) params.leftMargin = ResUtil.dp2px(6);
            row.addView(chip, params);
        }
        if (row.getChildCount() == 0) return;
        scroll.addView(row, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(32)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(32));
        params.topMargin = ResUtil.dp2px(8);
        root.addView(scroll, params);
    }

    private TextView createLyricsSearchSuggestionChip(TextInputEditText input, String text) {
        TextView chip = createAudioSheetText(text, 13, false);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setPadding(ResUtil.dp2px(10), 0, ResUtil.dp2px(10), 0);
        chip.setTextColor(SHEET_TEXT_SECONDARY);
        chip.setBackground(roundRect(SHEET_CONTROL_BG_SUBTLE, SHEET_BUTTON_RADIUS_DP, 1, SHEET_CONTROL_STROKE));
        chip.setOnClickListener(v -> {
            input.setText(text);
            if (input.getText() != null) input.setSelection(input.getText().length());
            input.requestFocus();
            Util.showKeyboard(input);
        });
        return chip;
    }

    private void submitLyricsSearchSheet(BottomSheetDialog dialog, TextInputEditText input) {
        String keyword = input.getText() == null ? "" : input.getText().toString().trim();
        if (TextUtils.isEmpty(keyword)) {
            input.setError(getString(R.string.player_lyrics_keyword_required));
            return;
        }
        Util.hideKeyboard(input);
        dialog.dismiss();
        searchLyrics(keyword);
    }

    private void onAudioLyricsSeek(long positionMs) {
        if (service() == null || player().isEmpty()) return;
        long duration = player().getDuration();
        long target = duration > 0 ? Math.min(Math.max(0, positionMs), Math.max(0, duration - 500)) : Math.max(0, positionMs);
        player().seekTo(target);
        if (mHistory != null) mHistory.setPosition(target);
        if (mLyrics != null) mLyrics.update(target);
    }

    private void onMore() {
        Flag flag = getFlag();
        if (flag == null) return;
        syncSelectedEpisode(flag);
        EpisodeGridDialog.create().reverse(mHistory.isRevSort()).episodes(flag.getEpisodes()).shortDrama(mShortDramaMode).show(this);
    }

    private void onContent() {
        CharSequence content = mBinding.content.getText();
        if (TextUtils.isEmpty(content)) return;
        // 简介折叠/展开切换：短文本不处理，长文本点击切换
        if (content.length() < 100) {
            // 短简介：保持原有弹窗行为
            VideoContentDialog.create().content(content).show(this);
            return;
        }
        mContentExpanded = !mContentExpanded;
        mBinding.content.setMaxLines(mContentExpanded ? Integer.MAX_VALUE : 3);
    }

    /** 简介折叠初始化：长文本默认折叠为3行，点击展开/收起 */
    private void updateContentFold() {
        mContentExpanded = false;
        CharSequence content = mBinding.content.getText();
        if (!TextUtils.isEmpty(content) && content.length() >= 100) {
            mBinding.content.setMaxLines(3);
            mBinding.content.setEllipsize(TextUtils.TruncateAt.END);
        } else {
            mBinding.content.setMaxLines(Integer.MAX_VALUE);
        }
    }

    private void showQuickSearch(String keyword) {
        mQuickSearchKeyword = TextUtils.isEmpty(mQuickSearchKeyword) ? keyword : mQuickSearchKeyword;
        mQuickSearchDialog = QuickSearchDialog.create()
                .title(getString(R.string.detail_search, mQuickSearchKeyword))
                .keyword(mQuickSearchKeyword)
                .listener(this)
                .searchListener(this::onQuickSearch)
                .items(mQuickAdapter.getItems());
        mQuickSearchDialog.show(this);
    }

    private void onQuickSearch(String keyword) {
        initSearch(keyword, false);
    }

    private void onReverse() {
        mHistory.setRevSort(!mHistory.isRevSort());
        reverseEpisode(false);
    }

    private boolean onChange() {
        checkSearch(true);
        return true;
    }

    private boolean onCopy() {
        Util.copy(mBinding.content.getText().toString());
        return true;
    }

    private void onBack() {
        if (mShortDramaMode && isFullscreen()) finishVideoPlayback();
        else if (isFullscreen()) exitFullscreen();
        else finishVideoPlayback();
    }

    private void finishVideoPlayback() {
        if (showKaraokeResultIfNeeded(KARAOKE_RESULT_ACTION_FINISH)) return;
        finishVideoPlaybackNow();
    }

    private void finishVideoPlaybackNow() {
        saveHistory(true);
        finishPlayback();
    }

    private void onCast() {
        if (mHistory == null || TextUtils.isEmpty(mHistory.getVodId()) || service() == null || player().isEmpty() || TextUtils.isEmpty(player().getUrl())) {
            Notify.show(R.string.cast_not_ready);
            return;
        }
        CastVideo video = new CastVideo(mBinding.name.getText().toString(), player().getUrl(), player().getPosition(), player().getHeaders());
        CastDialog.create().history(mHistory).video(video).fm(true).show(this);
    }

    private void onInfo() {
        InfoDialog.create().title(mBinding.control.title.getText()).headers(player().getHeaders()).url(player().getUrl()).show(this);
    }

    private void onKeep() {
        // 海阔交接详情：对齐 8.83，收藏进海阔侧自己的收藏体系，不再混入主 App 的 Keep（首页收藏）
        if (HkDetailBridge.isHkKey(getKey())) {
            onHkKeep();
            return;
        }
        Keep keep = Keep.find(getHistoryKey());
        Notify.show(keep != null ? R.string.keep_del : R.string.keep_add);
        if (keep != null) keep.delete();
        else createKeep();
        checkKeepImg();
    }

    /** 海阔详情的规则标题（收藏记录用）。 */
    private String hkRuleTitle() {
        String t = getIntent().getStringExtra("hk_rule");
        if (t == null || t.isEmpty()) t = getKey().substring(HkDetailBridge.KEY_PREFIX.length());
        return t == null ? "" : t;
    }

    /** 海阔详情星标：对齐 8.83 FilmListActivity 的收藏交互（新增/更新/取消收藏）。 */
    private void onHkKeep() {
        String ruleTitle = hkRuleTitle();
        String episodeUrl = getId();
        HkFavorite exist = HkFavoriteStore.find(ruleTitle, episodeUrl);
        if (exist != null) {
            HkFavoriteDialogs.showOptions(this, "请选择操作", new String[]{"取消收藏", "更新收藏"}, which -> {
                if (which == 0) {
                    HkFavoriteStore.remove(ruleTitle, episodeUrl);
                    Notify.show("已取消收藏");
                } else {
                    showHkFavoriteEdit(exist);
                    return;
                }
                checkKeepImg();
            });
            return;
        }
        // 新增收藏直接存（2026-10-23 定案）：点一下就存好，不先弹「新增收藏」表单；
        // 改名/分组走收藏页长按的「更新收藏」。标题/封面取值链见 hkKeepTitle/hkKeepPic。
        HkFavorite f = new HkFavorite();
        f.setRuleTitle(ruleTitle);
        f.setEpisodeUrl(episodeUrl);
        f.setTitle(hkKeepTitle());
        f.setPicUrl(hkKeepPic());
        f.setGroup(HkFavoriteStore.DEFAULT_GROUP);
        f.setTime(System.currentTimeMillis());
        HkRule rule = HkRuleManager.get().getInstalledRule(ruleTitle);
        if (rule != null) f.setRuleUrl(rule.getUrl());
        HkFavoriteStore.addOrUpdate(f);
        Notify.show("已加入收藏");
        checkKeepImg();
    }

    /** 收藏标题：已加载详情标题 → 入口条目名 → 历史记录名，取第一个非空。 */
    private String hkKeepTitle() {
        if (mAiVod != null && !TextUtils.isEmpty(mAiVod.getName())) return mAiVod.getName();
        String name = getIntent().getStringExtra("name");
        if (!TextUtils.isEmpty(name)) return name;
        return mHistory != null && mHistory.getVodName() != null ? mHistory.getVodName() : "";
    }

    /**
     * 收藏封面取值链（2026-10-23 定案，覆盖两条入口）：
     * 已加载 Vod 的封面（有详情页时）→ 历史记录里的 vodPic → 入口条目带的 pic 参数
     * （没详情页、从列表直接进播放时条目自带的封面），取第一个非空。
     * 旧实现只取 intent 的 pic：海阔详情进来时该参数为空，封面被存成空串，收藏页只剩首字占位块。
     */
    private String hkKeepPic() {
        if (mAiVod != null && !TextUtils.isEmpty(mAiVod.getPic())) return mAiVod.getPic();
        if (mHistory != null && !TextUtils.isEmpty(mHistory.getVodPic())) return mHistory.getVodPic();
        String pic = getIntent().getStringExtra("pic");
        return pic == null ? "" : pic;
    }

    /**
     * 老收藏补封面：早期存下的收藏 picUrl 为空，打开它的详情、封面加载到后顺手补存（只补空、不覆盖）。
     * 在海阔详情加载成功处调用（收藏页点条目也走同一条详情链，故老收藏点开即补）。
     */
    private void backfillHkFavoritePic(String ruleTitle, String episodeUrl, String pic) {
        if (TextUtils.isEmpty(pic)) return;
        try {
            HkFavorite fav = HkFavoriteStore.find(ruleTitle, episodeUrl);
            if (fav != null && TextUtils.isEmpty(fav.getPicUrl())) {
                fav.setPicUrl(pic);
                HkFavoriteStore.addOrUpdate(fav);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 海阔收藏编辑弹窗：标题可改、分组可选（对齐原版 BookmarkEditPopup）。 */
    private void showHkFavoriteEdit(HkFavorite f) {
        boolean isNew = HkFavoriteStore.find(f.getRuleTitle(), f.getEpisodeUrl()) == null;
        HkFavoriteDialogs.showEdit(this, isNew ? "新增收藏" : "更新收藏", f.getTitle(), f.getGroup(), (title, group) -> {
            f.setTitle(title);
            f.setGroup(group);
            f.setTime(System.currentTimeMillis());
            HkFavoriteStore.addOrUpdate(f);
            Notify.show(isNew ? "已加入收藏" : "收藏已更新");
            checkKeepImg();
        });
    }

    private void onDownload() {
        // Emby 内容一律绝对禁下载（防封号口径），不留开关不留后门
        if (isEmbyPlayback()) {
            com.fongmi.android.tv.utils.Notify.show("Emby 内容不允许下载");
            return;
        }
        String url = player().getUrl();
        String name = mBinding.name.getText().toString();
        if (url == null || url.isEmpty()) {
            com.fongmi.android.tv.utils.Notify.show("没有可下载的地址");
            return;
        }
        // 磁力/ed2k/thunder 走迅雷，不进下载器
        String scheme = com.fongmi.android.tv.utils.UrlUtil.scheme(url);
        if ("magnet".equals(scheme) || "ed2k".equals(scheme) || "thunder".equals(scheme)) {
            com.fongmi.android.tv.utils.Notify.show("磁力/ed2k 请用播放器直接播放");
            return;
        }
        try {
            com.fongmi.android.tv.utils.download.DownloadManager.get().add(url, name, player().getHeaders());
            DownloadActivity.start(getActivity());
        } catch (IllegalArgumentException e) {
            // Emby 总闸：同 HomeActivity 的同款写法，add() 撞 Emby 特征 URL 抛错时只提示、不闪退
            com.fongmi.android.tv.utils.Notify.show(e.getMessage());
        }
    }

    private void checkPlay() {
        setR1Callback();
        debugPlaybackControl("checkPlay");
        // 服务被回收未绑回时 player() 为 null：点播放直接吞掉，不许裸解崩溃（深挖 S1）
        if (player() == null) return;
        if (player().isPlaying()) onPaused();
        else if (player().isEmpty()) onRefresh();
        else onPlay();
    }

    private void checkNext() {
        checkNext(true);
    }

    private void checkNext(boolean notify) {
        setR1Callback();
        Episode item = getAdjacentEpisode(1);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("audio-auto-next", "fallback next notify=%s selected=%s name=%s adapter=%d", notify, item.isSelected(), item.getName(), mEpisodeAdapter.getItemCount());
        if (!item.isSelected()) onItemClick(item);
        else if (notify) Notify.show(R.string.error_play_next);
    }

    private void checkPrev() {
        setR1Callback();
        Episode item = getAdjacentEpisode(-1);
        if (!item.isSelected()) onItemClick(item);
        else Notify.show(R.string.error_play_prev);
    }

    private Episode getAdjacentEpisode(int offset) {
        Flag flag = getFlag();
        List<Episode> items = mAudioStageVisible ? mEpisodeAdapter.getItems() : flag == null ? mEpisodeAdapter.getItems() : flag.getEpisodes();
        if (items.isEmpty()) return new Episode();
        int position = getSelectedEpisodePosition(items) + offset;
        position = Math.max(0, Math.min(position, items.size() - 1));
        return items.get(position);
    }

    private boolean hasAdjacentEpisode(int offset) {
        Flag flag = getFlag();
        List<Episode> items = mAudioStageVisible ? mEpisodeAdapter.getItems() : flag == null ? mEpisodeAdapter.getItems() : flag.getEpisodes();
        if (items.isEmpty()) return false;
        int position = getSelectedEpisodePosition(items) + offset;
        return position >= 0 && position < items.size();
    }

    private void onSetting() {
        setTrackVisible();
        ControlDialog.create().parent(mBinding).history(mHistory).parse(isUseParse()).player(player())
                .embyPlayback(isEmbyPlayback() || isEmbyEpisode()).show(this);
    }

    /** 更多面板：9 宫格，每格接现有行为方法（定稿口径 4；与详情选集区旧 onMore() 同名冲突故改名） */
    private void onMorePanel() {
        DialogPlayerMoreBinding binding = DialogPlayerMoreBinding.inflate(getLayoutInflater());
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        // 底部面板进出统一口径：滑上滑下＋淡入淡出 200ms（与 BaseBottomSheetDialog 基类同一套）
        if (dialog.getWindow() != null) dialog.getWindow().setWindowAnimations(R.style.XcBottomSheetSlide);
        dialog.setContentView(binding.getRoot());
        binding.moreClose.setOnClickListener(v -> dialog.dismiss());
        binding.morePlayParams.setOnClickListener(v -> { dialog.dismiss(); onPlayParams(); });
        binding.moreScale.setOnClickListener(v -> { dialog.dismiss(); onScale(); });
        binding.moreLut.setOnClickListener(v -> { dialog.dismiss(); onLut(); });
        binding.moreReset.setOnClickListener(v -> { dialog.dismiss(); onReset(); });
        binding.moreRepeat.setOnClickListener(v -> { dialog.dismiss(); onRepeat(); });
        binding.moreAudio.setOnClickListener(v -> { dialog.dismiss(); onTrack(C.TRACK_TYPE_AUDIO); });
        binding.moreVideo.setOnClickListener(v -> { dialog.dismiss(); onTrack(C.TRACK_TYPE_VIDEO); });
        binding.moreSubtitleSetting.setOnClickListener(v -> { dialog.dismiss(); onSubtitleClick(); });
        binding.moreTextTrack.setOnClickListener(v -> { dialog.dismiss(); onTrack(C.TRACK_TYPE_TEXT); });
        binding.moreTextTrack.setOnLongClickListener(v -> { dialog.dismiss(); return onTextLong(); });
        binding.moreDanmaku.setOnClickListener(v -> { dialog.dismiss(); onDanmaku(); });
        // 音轨/视轨无轨时隐藏（原 setTrackVisible 逻辑）；字幕设置同字幕键闸门：没字幕轨且非点播不露
        binding.moreAudio.setVisibility(player().haveTrack(C.TRACK_TYPE_AUDIO) ? View.VISIBLE : View.GONE);
        binding.moreVideo.setVisibility(player().haveTrack(C.TRACK_TYPE_VIDEO) ? View.VISIBLE : View.GONE);
        binding.moreSubtitleSetting.setVisibility(player().haveTrack(C.TRACK_TYPE_TEXT) || player().isVod() ? View.VISIBLE : View.GONE);
        // 三格全藏时整行一并收起，不许留一条带 12dp 上距的空行
        binding.moreTrackRow.setVisibility(binding.moreAudio.getVisibility() == View.VISIBLE || binding.moreVideo.getVisibility() == View.VISIBLE || binding.moreSubtitleSetting.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
        // 竖屏全屏专属：底部行收起的字幕（选轨）/弹幕（换源）落在这里；横屏底部行仍有这两项，面板不重复出现
        // 竖屏套判定与 showControl 同口径：实际屏幕方向，不看视频宽高
        boolean verticalFullscreen = isFullscreen() && !isActualLand();
        // 整行连同 12dp 上距一起收放：只藏格子会把空行和上距留在面板底部（横屏凭空多一条空行）
        binding.morePortraitRow.setVisibility(verticalFullscreen ? View.VISIBLE : View.GONE);
        binding.moreTextTrack.setVisibility(verticalFullscreen && (player().haveTrack(C.TRACK_TYPE_TEXT) || player().isVod()) ? View.VISIBLE : View.GONE);
        binding.moreDanmaku.setVisibility(verticalFullscreen ? View.VISIBLE : View.GONE);
        // 裸 BottomSheetDialog 在横屏会停在折叠态：自动 peek 按 16:9 公式算约等于 0，只露出标题行、内容全在折线下（真机实证面板是空的）
        // 与本页其它播放弹窗同款处理（showAudioSheet）：show 时强制展开并跳过折叠态；behavior 未关联时降级为裸 show，不崩
        dialog.setOnShowListener(d -> {
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet == null) return;
            try {
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            } catch (IllegalArgumentException e) {
                SpiderDebug.log("bottomsheet", "more panel behavior unavailable, degraded: %s", e.getMessage());
            }
        });
        dialog.show();
        hideControl();
    }

    /** 新增：右上角弹幕/字幕细设入口（横屏全屏＋竖屏全屏） */
    private void onFineSetting() {
        // 细设只列当前真有的：没弹幕不列弹幕细设；没字幕轨且非点播不列字幕细设（与字幕键同一闸门）；只剩一项直接打开
        // 直播弹幕（js_simple_live 等源经本页播直播）不在点播弹幕源列表里，haveDanmaku() 恒假——须并上 haveLiveDanmaku()，否则直播态点齿轮空转
        boolean hasDanmaku = player().haveDanmaku() || player().haveLiveDanmaku();
        boolean hasSubtitle = player().haveTrack(C.TRACK_TYPE_TEXT) || player().isVod();
        if (hasDanmaku && !hasSubtitle) {
            DanmakuSettingDialog.create().player(player()).show(this);
            hideControl();
            return;
        }
        if (!hasDanmaku && hasSubtitle) {
            onSubtitleClick();
            return;
        }
        if (!hasDanmaku) {
            hideControl();
            return;
        }
        ChoiceDialog.showSingle(this, R.string.player_fine_setting,
                new CharSequence[]{"弹幕设置", "字幕设置"}, -1, which -> {
                    if (which == 0) DanmakuSettingDialog.create().player(player()).show(this);
                    else onSubtitleClick();
                });
        hideControl();
    }

    private void onAudioQueue() {
        restoreActiveAudioPlaylist();
        showAudioQueueSheet(getAudioStageTitle());
    }

    private void showAudioQueueSheet(String keyword) {
        showAudioQueueSheet(keyword, AUDIO_QUEUE_TAB_CURRENT, false);
    }

    private void showAudioQueueSheet(String keyword, int selectedTab, boolean focusSearch) {
        if (mAudioQueueDialog != null && mAudioQueueDialog.isShowing()) mAudioQueueDialog.dismiss();
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        int tab = selectedTab == AUDIO_QUEUE_TAB_SEARCH ? AUDIO_QUEUE_TAB_SEARCH : AUDIO_QUEUE_TAB_CURRENT;
        if (tab == AUDIO_QUEUE_TAB_SEARCH) {
            root.addView(createAudioQueueSearchHeader(dialog), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(34)));
        } else {
            root.addView(createAudioPlaylistHeader(dialog), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(42)));
        }

        TextInputEditText input = null;
        if (tab == AUDIO_QUEUE_TAB_SEARCH) {
            ScrollView scroll = new ScrollView(this);
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextInputLayout layout = new TextInputLayout(this);
            styleAudioSheetInput(layout, getString(R.string.player_audio_playlist_search_hint));
            input = new TextInputEditText(layout.getContext());
            input.setSingleLine(true);
            input.setMaxLines(1);
            input.setTextColor(Color.WHITE);
            input.setHintTextColor(0x70FFFFFF);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);
            input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            input.setText(TextUtils.isEmpty(keyword) ? "" : keyword);
            if (input.getText() != null) input.setSelection(input.getText().length());
            layout.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(layout, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextInputEditText finalInput = input;
            LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(ResUtil.dp2px(46), ResUtil.dp2px(46));
            searchParams.leftMargin = ResUtil.dp2px(8);
            row.addView(createAudioSheetIconButton(R.drawable.ic_action_search, () -> submitAudioQueueSearch(finalInput)), searchParams);
            root.addView(row, audioSheetWrapTopParams(8));

            mAudioQueueStatus = createAudioSheetText("", 13, false);
            mAudioQueueStatus.setTextColor(SHEET_TEXT_MUTED);
            root.addView(mAudioQueueStatus, audioSheetTopParams(4, 24));
            content.addView(createAudioSheetSection(getString(R.string.player_audio_playlist_results)));
            mAudioQueueSearchList = new LinearLayout(this);
            mAudioQueueSearchList.setOrientation(LinearLayout.VERTICAL);
            content.addView(mAudioQueueSearchList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, audioQueueContentHeight(tab)));
        } else {
            mAudioQueueList = new RecyclerView(this);
            mAudioQueueList.setOverScrollMode(View.OVER_SCROLL_NEVER);
            mAudioQueueList.setItemAnimator(null);
            mAudioQueueList.setLayoutManager(new LinearLayoutManager(this));
            mAudioQueueList.setAdapter(mAudioQueueAdapter = new AudioQueueAdapter());
            root.addView(mAudioQueueList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, audioQueueContentHeight(tab)));
        }

        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> {
            if (mAudioQueueDialog == dialog) {
                mAudioQueueDialog = null;
                mAudioQueueList = null;
                mAudioQueueAdapter = null;
                mAudioQueueSearchList = null;
                mAudioQueueStatus = null;
                mAudioQueueSearchSeq++;
            }
        });
        mAudioQueueDialog = dialog;
        renderAudioQueueList();
        if (input != null) {
            TextInputEditText finalInput = input;
            input.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
                submitAudioQueueSearch(finalInput);
                return true;
            });
        }
        showCompactPlaybackSheet(dialog);
        if (focusSearch && input != null) {
            TextInputEditText finalInput = input;
            input.post(() -> Util.showKeyboard(finalInput));
        }
    }

    private TextView createAudioSheetSection(String label) {
        TextView view = createAudioSheetText(label, 13, true);
        view.setTextColor(0xB8FFFFFF);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(ResUtil.dp2px(2), ResUtil.dp2px(8), ResUtil.dp2px(2), ResUtil.dp2px(2));
        return view;
    }

    private void submitAudioQueueSearch(TextInputEditText input) {
        String keyword = input.getText() == null ? "" : input.getText().toString().trim();
        if (TextUtils.isEmpty(keyword)) {
            input.setError(getString(R.string.player_audio_playlist_search_required));
            return;
        }
        Util.hideKeyboard(input);
        searchAudioQueue(keyword);
    }

    private void searchAudioQueue(String keyword) {
        int seq = ++mAudioQueueSearchSeq;
        setAudioQueueStatus(getString(R.string.search_loading));
        if (mAudioQueueSearchList != null) mAudioQueueSearchList.removeAllViews();
        Task.execute(() -> {
            try {
                Result result = SiteApi.searchContent(getSite(), keyword, false, "1");
                List<Vod> items = result.getList();
                items.removeIf(item -> TextUtils.isEmpty(item.getId()));
                App.post(() -> showAudioQueueSearchResults(seq, items));
            } catch (Exception e) {
                App.post(() -> {
                    if (seq == mAudioQueueSearchSeq) setAudioQueueStatus(Notify.getError(R.string.player_audio_playlist_search_failed, e));
                });
            }
        });
    }

    private void showAudioQueueSearchResults(int seq, List<Vod> items) {
        if (seq != mAudioQueueSearchSeq || mAudioQueueSearchList == null) return;
        mAudioQueueSearchList.removeAllViews();
        if (items == null || items.isEmpty()) {
            setAudioQueueStatus(getString(R.string.player_audio_playlist_no_results));
            return;
        }
        setAudioQueueStatus(getString(R.string.player_audio_playlist_result_count, items.size()));
        for (int i = 0; i < items.size(); i++) {
            Vod item = items.get(i);
            TextView view = createAudioSheetItem(audioQueueVodLabel(item), () -> addAudioQueueVod(item));
            mAudioQueueSearchList.addView(view, audioSheetTopParams(i == 0 ? 4 : 0, 50));
        }
    }

    private String audioQueueVodLabel(Vod item) {
        String name = item == null ? "" : item.getName();
        String remark = item == null ? "" : item.getRemarks();
        String site = item == null ? "" : item.getSiteName();
        String sub = TextUtils.isEmpty(remark) ? site : TextUtils.isEmpty(site) ? remark : remark + " · " + site;
        return TextUtils.isEmpty(sub) ? name : name + "\n" + sub;
    }

    private void addAudioQueueVod(Vod item) {
        if (item == null || TextUtils.isEmpty(item.getId())) return;
        int seq = ++mAudioQueueSearchSeq;
        setAudioQueueStatus(getString(R.string.player_audio_playlist_adding, item.getName()));
        Task.execute(() -> {
            try {
                String key = TextUtils.isEmpty(item.getSiteKey()) ? getKey() : item.getSiteKey();
                Vod vod = SiteApi.detailContent(key, item.getId()).getVod();
                App.post(() -> appendAudioQueueVod(seq, vod));
            } catch (Exception e) {
                App.post(() -> {
                    if (seq == mAudioQueueSearchSeq) setAudioQueueStatus(Notify.getError(R.string.player_audio_playlist_add_failed, e));
                });
            }
        });
    }

    private void appendAudioQueueVod(int seq, Vod vod) {
        if (seq != mAudioQueueSearchSeq || vod == null) return;
        Flag queue = getFlag();
        if (queue == null || vod.getFlags().isEmpty()) {
            setAudioQueueStatus(getString(R.string.player_audio_playlist_add_empty));
            return;
        }
        int added = 0;
        for (Flag source : vod.getFlags()) {
            for (Episode item : source.getEpisodes()) {
                if (TextUtils.isEmpty(item.getUrl())) continue;
                Episode episode = Episode.create(audioQueueEpisodeName(vod, item, source), item.getUrl());
                if (containsAudioQueueEpisode(queue.getEpisodes(), episode)) continue;
                queue.getEpisodes().add(episode);
                putAudioQueueMetadata(episode, vod, item, source);
                added++;
            }
        }
        setEpisodeAdapter(queue.getEpisodes());
        renderAudioQueueList();
        setAudioQueueStatus(added > 0 ? getString(R.string.player_audio_playlist_added, added) : getString(R.string.player_audio_playlist_exists));
    }

    private View createAudioPlaylistHeader(BottomSheetDialog dialog) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout titleGroup = new LinearLayout(this);
        titleGroup.setGravity(Gravity.CENTER_VERTICAL);
        titleGroup.setOrientation(LinearLayout.VERTICAL);
        TextView title = createAudioSheetText(getString(R.string.player_audio_playlist), 17, true);
        title.setSingleLine(true);
        TextView subtitle = createAudioSheetText(AudioPlaylistStore.active().name, 12, false);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        subtitle.setTextColor(SHEET_TEXT_MUTED);
        titleGroup.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        titleGroup.addView(subtitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(titleGroup, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        row.addView(createAudioSheetMiniButton(getString(R.string.play_search), false, () -> {
            dialog.dismiss();
            showAudioQueueSheet(getAudioStageTitle(), AUDIO_QUEUE_TAB_SEARCH, true);
        }), audioSheetMiniButtonParams(58, false));
        row.addView(createAudioSheetMiniButton(getString(R.string.player_audio_playlist_switch), false, this::showAudioPlaylistSwitchSheet), audioSheetMiniButtonParams(58, true));
        row.addView(createAudioSheetMiniButton(getString(R.string.player_audio_playlist_create), false, this::showAudioPlaylistCreateSheet), audioSheetMiniButtonParams(54, true));
        return row;
    }

    private View createAudioQueueSearchHeader(BottomSheetDialog dialog) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(createAudioSheetTitle(getString(R.string.play_search)), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        row.addView(createAudioSheetMiniButton(getString(R.string.player_audio_playlist), false, () -> {
            dialog.dismiss();
            showAudioQueueSheet("", AUDIO_QUEUE_TAB_CURRENT, false);
        }), audioSheetMiniButtonParams(58, false));
        return row;
    }

    private void restoreActiveAudioPlaylist() {
        Flag queue = getFlag();
        if (queue == null) return;
        List<Episode> items = queue.getEpisodes();
        String selectedKey = audioQueueEpisodeKey(getEpisode());
        for (int i = items.size() - 1; i >= 0; i--) {
            Episode item = items.get(i);
            if (!isAudioQueueEpisode(item)) continue;
            items.remove(i);
            removeAudioQueueMetadata(item);
        }
        AudioPlaylistStore.Playlist playlist = AudioPlaylistStore.active();
        for (AudioPlaylistStore.Entry entry : playlist.items) {
            if (entry == null || TextUtils.isEmpty(entry.url)) continue;
            Episode episode = Episode.create(TextUtils.isEmpty(entry.name) ? entry.title : entry.name, entry.url);
            if (containsAudioQueueEpisode(items, episode)) continue;
            items.add(episode);
            putAudioQueueMetadata(episode, entry);
            if (TextUtils.equals(audioQueueEpisodeKey(episode), selectedKey)) episode.setSelected(true);
        }
        setEpisodeAdapter(items);
        renderAudioQueueList();
    }

    private void showAudioPlaylistSwitchSheet() {
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        root.addView(createAudioSheetTitle(getString(R.string.player_audio_playlist_switch)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(32)));
        AudioPlaylistStore.Playlist active = AudioPlaylistStore.active();
        List<AudioPlaylistStore.Playlist> playlists = AudioPlaylistStore.list();
        for (int i = 0; i < playlists.size(); i++) {
            AudioPlaylistStore.Playlist playlist = playlists.get(i);
            TextView item = createAudioSheetItem(playlist.name + " · " + playlist.items.size(), () -> {
                AudioPlaylistStore.setActive(playlist.id);
                restoreActiveAudioPlaylist();
                dialog.dismiss();
                if (mAudioQueueDialog != null) {
                    mAudioQueueDialog.dismiss();
                    showAudioQueueSheet("", AUDIO_QUEUE_TAB_CURRENT, false);
                }
            });
            boolean selected = TextUtils.equals(active.id, playlist.id);
            item.setTextColor(selected ? SHEET_TEXT_PRIMARY : SHEET_TEXT_SECONDARY);
            item.setBackground(audioSheetItemBackground(selected));
            root.addView(item, audioSheetTopParams(i == 0 ? 8 : 0, 50));
        }
        dialog.setContentView(root);
        showCompactPlaybackSheet(dialog);
    }

    private void showAudioPlaylistCreateSheet() {
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        root.addView(createAudioSheetTitle(getString(R.string.player_audio_playlist_create)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(32)));
        TextInputLayout layout = new TextInputLayout(this);
        styleAudioSheetInput(layout, getString(R.string.player_audio_playlist_name_hint));
        TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setSingleLine(true);
        input.setMaxLines(1);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0x70FFFFFF);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        layout.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(layout, audioSheetTopParams(12, 62));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(createAudioSheetButton(getString(R.string.dialog_positive), true, () -> {
            String name = input.getText() == null ? "" : input.getText().toString().trim();
            AudioPlaylistStore.create(name);
            restoreActiveAudioPlaylist();
            dialog.dismiss();
            if (mAudioQueueDialog != null) {
                mAudioQueueDialog.dismiss();
                showAudioQueueSheet("", AUDIO_QUEUE_TAB_CURRENT, false);
            }
        }), audioSheetButtonParams(false));
        root.addView(actions, audioSheetTopParams(12, 44));
        dialog.setContentView(root);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            AudioPlaylistStore.create(input.getText() == null ? "" : input.getText().toString().trim());
            restoreActiveAudioPlaylist();
            dialog.dismiss();
            if (mAudioQueueDialog != null) {
                mAudioQueueDialog.dismiss();
                showAudioQueueSheet("", AUDIO_QUEUE_TAB_CURRENT, false);
            }
            return true;
        });
        showCompactPlaybackSheet(dialog);
        input.post(() -> Util.showKeyboard(input));
    }

    private String audioQueueEpisodeName(Vod vod, Episode episode, Flag flag) {
        String song = vod.getName();
        String name = episode.getName();
        boolean single = flag.getEpisodes().size() <= 1;
        if (TextUtils.isEmpty(song)) return name;
        if (single || TextUtils.isEmpty(name) || name.matches("\\d+")) return song;
        if (name.contains(song)) return name;
        return song + " - " + name;
    }

    private boolean containsAudioQueueEpisode(List<Episode> items, Episode target) {
        for (Episode item : items) {
            if (!TextUtils.isEmpty(item.getUrl()) && item.getUrl().equals(target.getUrl())) return true;
            if (item.matches(target)) return true;
        }
        return false;
    }

    private void renderAudioQueueList() {
        if (mAudioQueueAdapter == null) return;
        Flag flag = getFlag();
        List<Episode> items = flag == null ? new ArrayList<>() : flag.getEpisodes();
        restoreLearnedAudioQueueMetadata(items);
        int selected = getSelectedEpisodePosition(items);
        mAudioQueueAdapter.setItems(items, selected);
        if (mAudioQueueList != null && selected >= 0) {
            mAudioQueueList.post(() -> mAudioQueueList.scrollToPosition(selected));
        }
    }

    private void restoreLearnedAudioQueueMetadata(List<Episode> items) {
        if (items == null) return;
        for (Episode item : items) {
            AudioPlaylistStore.Metadata metadata = AudioPlaylistStore.getMetadata(item.getUrl());
            if (metadata == null || TextUtils.isEmpty(metadata.title)) continue;
            String key = audioQueueEpisodeKey(item);
            mAudioQueueTitles.put(key, metadata.title);
            if (!TextUtils.isEmpty(metadata.artist)) mAudioQueueArtists.put(key, metadata.artist);
        }
    }

    private class AudioQueueAdapter extends RecyclerView.Adapter<AudioQueueAdapter.Holder> {

        private final List<Episode> items = new ArrayList<>();
        private int selected = -1;

        private void setItems(List<Episode> next, int selected) {
            items.clear();
            if (next != null) items.addAll(next);
            this.selected = selected;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return Math.max(1, items.size());
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(44)));

            TextView title = createAudioSheetText("", 14, false);
            title.setGravity(Gravity.CENTER_VERTICAL);
            title.setSingleLine(true);
            title.setMaxLines(1);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setBackground(null);
            row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

            ImageView remove = createAudioSheetInlineIconButton(R.drawable.ic_action_delete, () -> {
            });
            row.addView(remove, new LinearLayout.LayoutParams(ResUtil.dp2px(36), ResUtil.dp2px(36)));
            return new Holder(row, title, remove);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            if (items.isEmpty()) {
                holder.title.setText(getString(R.string.player_audio_playlist_empty));
                holder.title.setTextColor(0x99FFFFFF);
                holder.remove.setVisibility(View.GONE);
                holder.row.setBackground(null);
                holder.row.setOnClickListener(null);
                holder.row.setOnLongClickListener(null);
                return;
            }
            Episode item = items.get(position);
            boolean active = position == selected || TextUtils.equals(audioQueueEpisodeKey(item), Objects.toString(mPlaybackEpisodeKey, ""));
            holder.title.setText((position + 1) + ". " + getAudioQueueDisplayName(item, active));
            holder.title.setTextColor(active ? SHEET_TEXT_PRIMARY : SHEET_TEXT_SECONDARY);
            holder.remove.setVisibility(View.VISIBLE);
            holder.remove.setOnClickListener(v -> removeAudioQueueEpisode(item));
            holder.row.setBackground(audioSheetItemBackground(active));
            holder.row.setOnClickListener(v -> playAudioQueueEpisode(item));
            holder.row.setOnLongClickListener(v -> {
                removeAudioQueueEpisode(item);
                return true;
            });
        }

        private class Holder extends RecyclerView.ViewHolder {

            private final LinearLayout row;
            private final TextView title;
            private final ImageView remove;

            private Holder(@NonNull LinearLayout row, TextView title, ImageView remove) {
                super(row);
                this.row = row;
                this.title = title;
                this.remove = remove;
            }
        }
    }

    private void playAudioQueueEpisode(Episode item) {
        if (item == null) return;
        mAudioPlaylistCurrentIndex = findAudioPlaylistIndex(item.getUrl());
        if (mAudioQueueDialog != null) mAudioQueueDialog.dismiss();
        onItemClick(item);
    }

    private int findAudioPlaylistIndex(String url) {
        AudioPlaylistStore.Playlist playlist = AudioPlaylistStore.active();
        if (playlist == null || playlist.items == null) return -1;
        for (int i = 0; i < playlist.items.size(); i++) {
            AudioPlaylistStore.Entry entry = playlist.items.get(i);
            if (entry != null && TextUtils.equals(entry.url, url)) return i;
        }
        return -1;
    }

    private void removeAudioQueueEpisode(Episode target) {
        Flag queue = getFlag();
        if (queue == null || target == null) return;
        List<Episode> items = queue.getEpisodes();
        if (items.size() <= 1) {
            setAudioQueueStatus(getString(R.string.player_audio_playlist_keep_one));
            return;
        }
        int index = indexOfAudioQueueEpisode(items, target);
        if (index < 0) return;
        Episode removed = items.get(index);
        boolean selected = removed.isSelected();
        Episode next = selected ? items.get(index + 1 < items.size() ? index + 1 : index - 1) : null;
        items.remove(index);
        removeAudioQueueMetadata(removed);
        AudioPlaylistStore.removeItem(removed.getUrl());
        if (selected && next != null) onItemClick(next);
        else setEpisodeAdapter(items);
        renderAudioQueueList();
        setAudioQueueStatus(getString(R.string.player_audio_playlist_removed, removed.getDisplayName()));
    }

    private int indexOfAudioQueueEpisode(List<Episode> items, Episode target) {
        for (int i = 0; i < items.size(); i++) {
            Episode item = items.get(i);
            if (!TextUtils.isEmpty(item.getUrl()) && item.getUrl().equals(target.getUrl())) return i;
            if (item.matches(target)) return i;
        }
        return -1;
    }

    private void putAudioQueueMetadata(Episode episode, Vod vod, Episode sourceEpisode, Flag source) {
        String key = audioQueueEpisodeKey(episode);
        mAudioQueueFlags.put(key, source.getFlag());
        String songTitle = getAudioQueueSongTitle(vod.getName(), sourceEpisode.getName());
        mAudioQueueTitles.put(key, songTitle);
        mAudioQueuePics.put(key, vod.getPic());
        mAudioQueueLyrics.put(key, getTimedLyrics(vod.getContent()));
        String artist = TextUtils.isEmpty(vod.getActor()) ? getArtistFromEpisode(songTitle, sourceEpisode.getName()) : vod.getActor();
        if (!TextUtils.isEmpty(artist)) mAudioQueueArtists.put(key, artist);
        AudioPlaylistStore.Entry entry = new AudioPlaylistStore.Entry();
        entry.name = episode.getName();
        entry.url = episode.getUrl();
        entry.playFlag = source.getFlag();
        entry.title = songTitle;
        entry.artist = artist;
        entry.pic = vod.getPic();
        entry.lyrics = getTimedLyrics(vod.getContent());
        AudioPlaylistStore.upsertItem(entry);
    }

    private String getAudioQueueSongTitle(String collection, String episode) {
        String title = Objects.toString(episode, "").trim();
        String parent = Objects.toString(collection, "").trim();
        if (title.isEmpty() || title.matches("\\d+")) return parent;
        for (String separator : new String[]{" - ", " – ", " — "}) {
            if (!parent.isEmpty() && title.startsWith(parent + separator)) return title.substring(parent.length() + separator.length()).trim();
        }
        return title;
    }

    private void putAudioQueueMetadata(Episode episode, AudioPlaylistStore.Entry entry) {
        String key = audioQueueEpisodeKey(episode);
        Flag flag = getFlag();
        String playFlag = TextUtils.isEmpty(entry.playFlag) && flag != null ? flag.getFlag() : entry.playFlag;
        mAudioQueueFlags.put(key, playFlag);
        String title = entry.title;
        if (TextUtils.isEmpty(title) || mHistory != null && TextUtils.equals(title, mHistory.getVodName())) title = getAudioQueueSongTitle(title, entry.name);
        mAudioQueueTitles.put(key, title);
        mAudioQueuePics.put(key, entry.pic);
        mAudioQueueLyrics.put(key, entry.lyrics);
        if (!TextUtils.isEmpty(entry.artist)) mAudioQueueArtists.put(key, entry.artist);
    }

    private void removeAudioQueueMetadata(Episode episode) {
        String key = audioQueueEpisodeKey(episode);
        mAudioQueueFlags.remove(key);
        mAudioQueueTitles.remove(key);
        mAudioQueueArtists.remove(key);
        mAudioQueuePics.remove(key);
        mAudioQueueLyrics.remove(key);
    }

    private void applyAudioQueueMetadata(Episode item) {
        if (!isAudioQueueEpisode(item)) {
            updateAudioStageText();
            return;
        }
        updateAudioStageText();
    }

    private void setAudioQueueStatus(String text) {
        if (mAudioQueueStatus == null) {
            Notify.show(text);
            return;
        }
        mAudioQueueStatus.setText(Objects.toString(text, ""));
    }

    private void onAudioMore() {
        ArrayList<String> items = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();
        addAudioMoreItem(items, actions, getString(R.string.keep), this::onKeep);
        addAudioMoreItem(items, actions, getString(R.string.nav_setting), this::onSetting);
        addAudioMoreItem(items, actions, getString(R.string.player_audio_background), this::showAudioBackgroundPanel);
        if (service() != null && !player().isEmpty()) addAudioMoreItem(items, actions, getString(R.string.player_osd), this::onInfo);
        if (service() != null && player().haveTrack(C.TRACK_TYPE_AUDIO)) addAudioMoreItem(items, actions, getString(R.string.play_track_audio), () -> onTrack(C.TRACK_TYPE_AUDIO));
        addAudioMoreItem(items, actions, getString(R.string.play_cast), this::onCast);
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        root.addView(createAudioSheetTitle(getString(R.string.player_audio_more)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(32)));
        root.addView(createKaraokeActionGrid(dialog, true, items.toArray(new String[0]), actions.toArray(new Runnable[0]), 3), karaokeActionGridParams(10));
        dialog.setContentView(root);
        showCompactPlaybackSheet(dialog);
    }

    private void addAudioMoreItem(List<String> items, List<Runnable> actions, String label, Runnable action) {
        items.add(label);
        actions.add(action);
    }

    private void showAudioBackgroundPanel() {
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        LinearLayout[] gridRef = new LinearLayout[1];
        String[] labels = new String[]{
                getString(PlayerSetting.isAudioBackgroundDecorated() ? R.string.player_audio_background_decorated_turn_off : R.string.player_audio_background_decorated_turn_on),
                getString(PlayerSetting.isAudioBackgroundLightEffect() ? R.string.player_audio_background_light_effect_on : R.string.player_audio_background_light_effect_off),
                getString(R.string.player_audio_background_random_plain),
                getString(R.string.player_audio_background_random_decoration),
        };
        Runnable[] actions = new Runnable[]{
                () -> {
                    toggleAudioBackgroundDecorated();
                    updateAudioBackgroundPanel(gridRef[0]);
                },
                () -> {
                    toggleAudioBackgroundLightEffect();
                    updateAudioBackgroundPanel(gridRef[0]);
                },
                () -> {
                    randomizeAudioPlainBackground();
                    updateAudioBackgroundPanel(gridRef[0]);
                },
                () -> {
                    randomizeAudioBackgroundDecoration();
                    updateAudioBackgroundPanel(gridRef[0]);
                },
        };
        root.addView(createAudioSheetTitle(getString(R.string.player_audio_background)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(32)));
        gridRef[0] = createKaraokeActionGrid(dialog, true, labels, actions, 2, false);
        root.addView(gridRef[0], karaokeActionGridParams(10));
        dialog.setContentView(root);
        showAudioBackgroundSheet(dialog);
    }

    private void updateAudioBackgroundPanel(LinearLayout grid) {
        if (grid == null || grid.getChildCount() == 0 || !(grid.getChildAt(0) instanceof ViewGroup row)) return;
        if (row.getChildCount() > 0 && row.getChildAt(0) instanceof TextView button) button.setText(getString(PlayerSetting.isAudioBackgroundDecorated() ? R.string.player_audio_background_decorated_turn_off : R.string.player_audio_background_decorated_turn_on));
        if (row.getChildCount() > 1 && row.getChildAt(1) instanceof TextView button) button.setText(getString(PlayerSetting.isAudioBackgroundLightEffect() ? R.string.player_audio_background_light_effect_on : R.string.player_audio_background_light_effect_off));
    }

    private void toggleAudioBackgroundDecorated() {
        boolean decorated = !PlayerSetting.isAudioBackgroundDecorated();
        PlayerSetting.putAudioBackgroundDecorated(decorated);
        applyAudioBackground();
        Notify.show(getString(decorated ? R.string.player_audio_background_decorated_on : R.string.player_audio_background_decorated_off));
    }

    private void toggleAudioBackgroundLightEffect() {
        boolean lightEffect = !PlayerSetting.isAudioBackgroundLightEffect();
        PlayerSetting.putAudioBackgroundLightEffect(lightEffect);
        applyAudioBackground();
        Notify.show(getString(lightEffect ? R.string.player_audio_background_light_effect_on : R.string.player_audio_background_light_effect_off));
    }

    private void randomizeAudioPlainBackground() {
        PlayerSetting.putAudioBackground(PlayerSetting.AUDIO_BACKGROUND_RANDOM);
        PlayerSetting.putAudioBackgroundSeed(newAudioBackgroundSeed(0, PlayerSetting.getAudioBackgroundSeed()));
        applyAudioBackground();
        Notify.show(getString(R.string.player_audio_background_random_plain_done));
    }

    private void randomizeAudioBackgroundDecoration() {
        PlayerSetting.putAudioBackground(PlayerSetting.AUDIO_BACKGROUND_RANDOM);
        PlayerSetting.putAudioBackgroundDecorated(true);
        PlayerSetting.putAudioBackgroundDecorationSeed(newAudioBackgroundDecorationSeed());
        applyAudioBackground();
        Notify.show(getString(R.string.player_audio_background_random_decoration_done));
    }

    private void randomizeAudioBackgroundMix(boolean notify) {
        PlayerSetting.putAudioBackground(PlayerSetting.AUDIO_BACKGROUND_RANDOM);
        PlayerSetting.putAudioBackgroundDecorated(true);
        PlayerSetting.putAudioBackgroundSeed(newAudioBackgroundSeed(2, PlayerSetting.getAudioBackgroundSeed()));
        PlayerSetting.putAudioBackgroundDecorationSeed(newAudioBackgroundDecorationSeed());
        applyAudioBackground();
        if (notify) Notify.show(getString(R.string.player_audio_background_random_mix_done));
    }

    private int newAudioBackgroundDecorationSeed() {
        int previous = PlayerSetting.getAudioBackgroundDecorationSeed();
        int previousMotif = audioBackgroundDecorationMotif(previous);
        for (int i = 0; i < 8; i++) {
            int seed = newAudioBackgroundSeed(10 + i, previous);
            if (audioBackgroundDecorationMotif(seed) != previousMotif) return seed;
        }
        return newAudioBackgroundSeed(31, previous);
    }

    private int newAudioBackgroundSeed(int salt, int previous) {
        int previousHue = audioBackgroundHue(previous);
        for (int i = 0; i < 8; i++) {
            int seed = mixAudioBackgroundSeed((int) System.nanoTime() ^ (int) System.currentTimeMillis() ^ (++mAudioBackgroundRandomNonce * 0x9E3779B9) ^ salt * 0x45D9F3B);
            if (seed != 0 && seed != previous && hueDistance(audioBackgroundHue(seed), previousHue) >= 36) return seed;
        }
        return mixAudioBackgroundSeed(previous ^ (++mAudioBackgroundRandomNonce * 0x7FEB352D) ^ salt * 0x846CA68B);
    }

    private int audioBackgroundDecorationMotif(int seed) {
        return Math.floorMod(mixAudioBackgroundSeed(seed == 0 ? 0x5A17B3 : seed), 24);
    }

    private int audioBackgroundHue(int seed) {
        return Math.floorMod(mixAudioBackgroundSeed((seed == 0 ? 0x5A17B3 : seed)), 360);
    }

    private int hueDistance(int a, int b) {
        int distance = Math.abs(a - b);
        return Math.min(distance, 360 - distance);
    }

    private int mixAudioBackgroundSeed(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value;
    }

    private TextView createAudioMoreItem(BottomSheetDialog dialog, String label, Runnable action) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(Color.WHITE);
        view.setTextSize(16);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setSingleLine(true);
        view.setPadding(ResUtil.dp2px(6), 0, ResUtil.dp2px(6), 0);
        view.setBackground(audioSheetItemBackground(false));
        view.setOnClickListener(v -> {
            dialog.dismiss();
            action.run();
        });
        return view;
    }

    private LinearLayout.LayoutParams audioMoreItemParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(44));
        params.topMargin = ResUtil.dp2px(first ? 8 : 2);
        return params;
    }

    private void onLock() {
        setLock(!isLock());
        mKeyDown.setLock(isLock());
        checkLockImg();
        showControl();
    }

    private void onRotate() {
        setR1Callback();
        setRotate(!isRotate());
        // 先藏控件再请求方向：系统旋转转场会冻取发起那刻的整窗拷贝盖在新界面上，
        // 控件在拷贝里就会成顶部残影条。现成 hideControl() 是 200ms 淡出而非同步消失，
        // 必须等它定格（控件根 GONE）后的第一帧才发请求，拷贝里只剩干净视频帧。
        hideControl();
        requestRotateWhenControlGone();
    }

    private void requestRotateWhenControlGone() {
        View controlRoot = mBinding.control.getRoot();
        if (controlRoot.getVisibility() == View.GONE) {
            setRequestedOrientation(PlaybackOrientation.getRotateOrientation(isRotate()));
            return;
        }
        controlRoot.postOnAnimation(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) return;
                if (controlRoot.getVisibility() == View.GONE) setRequestedOrientation(PlaybackOrientation.getRotateOrientation(isRotate()));
                else controlRoot.postOnAnimation(this);
            }
        });
    }

    private void onFullscreen() {
        if (isFullscreen()) exitFullscreen();
        else enterFullscreen();
        showControl();
    }

    private void onTrack(View view) {
        Object tag = view.getTag();
        if (tag == null) return;
        int type;
        try {
            type = Integer.parseInt(tag.toString());
        } catch (NumberFormatException e) {
            return;
        }
        TrackDialog dialog = TrackDialog.create().type(type).player(player());
        attachEmbySubs(dialog, type);
        attachEmbyTracks(dialog, type);
        dialog.show(this);
        hideControl();
    }

    private void onTrack(int type) {
        TrackDialog dialog = TrackDialog.create().type(type).player(player());
        attachEmbySubs(dialog, type);
        attachEmbyTracks(dialog, type);
        dialog.show(this);
        hideControl();
    }

    @Override
    public void onTrackPanel(int type) {
        TrackDialog dialog = TrackDialog.create().type(type).player(player());
        attachEmbySubs(dialog, type);
        attachEmbyTracks(dialog, type);
        dialog.show(this);
    }

    private void onTitle() {
        TitleDialog.create().player(player()).show(this);
        hideControl();
    }

    @Override
    public void onTitlePanel() {
        TitleDialog.create().player(player()).show(this);
    }

    private void onDanmaku() {
        DanmakuDialog.create().player(player()).show(this);
        hideControl();
    }

    @Override
    public void onDanmakuPanel() {
        DanmakuDialog.create().player(player()).show(this);
    }

    @Override
    public void onImmersiveAudioModeChanged() {
        boolean enabled = PlayerSetting.isImmersiveAudioMode();
        boolean wasAudioStageVisible = mAudioStageVisible;
        updateAudioOnlyState();
        if (enabled) {
            ensureImmersiveAudioControllers();
            applyPlaybackArtwork(getPlaybackEpisode());
            refreshLyrics();
            reloadKaraokeTrack();
        } else if (wasAudioStageVisible) {
            restoreVideoTrackAfterAudioStage();
        }
        SpiderDebug.log("audio-mode", "toggle enabled=%s stage=%s artwork=%s owner=%s", enabled, mAudioStageVisible, !TextUtils.isEmpty(mArtworkRequestUrl), mPlaybackEpisodeKey);
    }

    private void restoreVideoTrackAfterAudioStage() {
        mBinding.video.postDelayed(() -> {
            if (service() == null || mAudioStageVisible || PlayerSetting.isImmersiveAudioMode() || !player().haveTrack(C.TRACK_TYPE_VIDEO)) return;
            player().restoreVideoTrack();
            SpiderDebug.log("audio-mode", "restore video track player=%s position=%d", player().getPlayerText(), player().getPosition());
        }, 200);
    }

    @Override
    public void onKaraokeModeChanged() {
        setKaraokeActionState();
        syncKaraokeStageVisibility();
        if (PlayerSetting.isKaraokeMode()) {
            mKaraokeResultShown = false;
            refreshLyrics();
        }
        else if (mKaraoke != null) mKaraoke.clear();
    }

    private void onKaraokeMode() {
        showKaraokeModePanel();
    }

    private void setKaraokeMode(boolean enable) {
        if (PlayerSetting.isKaraokeMode() == enable) return;
        PlayerSetting.putKaraokeMode(enable);
        onKaraokeModeChanged();
        showControl();
    }

    @Override
    public void onKaraokeTrackPanel() {
        showLyricsSettingsPanel(LYRICS_TAB_TRACK);
    }

    private void showKaraokeModePanel() {
        showLyricsSettingsPanel(LYRICS_TAB_LYRICS);
    }

    private void showLyricsSettingsPanel() {
        showLyricsSettingsPanel(LYRICS_TAB_LYRICS);
    }

    private void showLyricsSettingsPanel(int selectedTab) {
        if (service() == null) return;
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        int tab = Math.max(LYRICS_TAB_LYRICS, Math.min(LYRICS_TAB_TRACK, selectedTab));
        root.addView(createLyricsSettingsTabs(dialog, tab), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(34)));
        if (tab == LYRICS_TAB_KARAOKE) {
            root.addView(createKaraokeModeHeader(), lyricsSettingRowParams(8, 38));
            root.addView(createKaraokeDelayControl(), lyricsSettingRowParams(6, 42));
            root.addView(createKaraokeActionGrid(dialog, true,
                    new String[]{getString(R.string.player_karaoke_difficulty) + " " + karaokeDifficultyText()},
                    new Runnable[]{this::showKaraokeDifficultyPanel},
                    3), karaokeActionGridParams(8));
        } else if (tab == LYRICS_TAB_TRACK) {
            root.addView(createKaraokeActionGrid(dialog, true,
                    new String[]{
                            getString(R.string.player_karaoke_track_generate_pitch),
                            getString(R.string.player_karaoke_track_clear),
                            getString(R.string.player_karaoke_track_search),
                            getString(R.string.player_karaoke_track_import_file),
                            getString(R.string.player_karaoke_track_import_url),
                            getString(R.string.player_karaoke_track_sources),
                            getKaraokeBasicPitchLabel()
                    },
                    new Runnable[]{
                            this::generateKaraokePitchTrack,
                            this::clearKaraokeTrackBinding,
                            this::showKaraokeTrackSearchDialog,
                            this::chooseKaraokeTrackFile,
                            this::showKaraokeTrackUrlDialog,
                            this::showKaraokeTrackSourcesDialog,
                            this::toggleKaraokeBasicPitchTfliteFromSettings
                    },
                    new boolean[]{true, false, true, true, true, true, true},
                    3), karaokeActionGridParams(8));
        } else {
            root.addView(createLyricsOffsetControl(), lyricsSettingRowParams(8, 42));
            root.addView(createKaraokeActionGrid(dialog, true,
                    new String[]{
                            getString(R.string.player_lyrics_rows) + " " + getLyricsRowsText(),
                            getString(R.string.player_lyrics_size) + " " + lyricsSizeText(),
                            getString(R.string.player_lyrics_source) + " " + lyricsSourceText(),
                            getString(R.string.player_lyrics_search),
                            getString(R.string.player_desktop_lyrics) + " " + getSwitch(PlayerSetting.isDesktopLyrics()),
                            getString(R.string.player_lyrics_cache) + " " + getString(R.string.player_lyrics_cache_value, LyricsRepository.cacheCount())
                    },
                    new Runnable[]{
                            this::showLyricsRowsPanel,
                            this::showLyricsSizePanel,
                            this::showLyricsSourcePanel,
                            this::openLyricsSearchFromSettings,
                            this::toggleDesktopLyrics,
                            this::clearLyricsCacheFromSettings
                    },
                    3), karaokeActionGridParams(8));
        }
        dialog.setContentView(root);
        showLyricsSettingsSheet(dialog);
    }

    private void showLyricsRowsPanel() {
        String[] items = new String[5];
        for (int i = 0; i < items.length; i++) items[i] = getString(R.string.player_lyrics_rows_value, i + 1);
        showLyricsChoicePanel(getString(R.string.player_lyrics_rows), items, PlayerSetting.getLyricsRows() - 1, which -> {
            PlayerSetting.putLyricsRows(which + 1);
            applyLyricsRuntimeSettings();
        }, LYRICS_TAB_LYRICS);
    }

    private void showLyricsSizePanel() {
        showLyricsChoicePanel(getString(R.string.player_lyrics_size), ResUtil.getStringArray(R.array.select_lyrics_size), PlayerSetting.getLyricsTextSizeOption(), which -> {
            PlayerSetting.putLyricsTextSizeOption(which);
            applyLyricsRuntimeSettings();
        }, LYRICS_TAB_LYRICS);
    }

    private void showLyricsSourcePanel() {
        showLyricsChoicePanel(getString(R.string.player_lyrics_source), ResUtil.getStringArray(R.array.select_lyrics_source), LyricsSetting.getSourceMode(), which -> {
            LyricsSetting.putSourceMode(which);
            if (mLyrics != null) mLyrics.clear();
            refreshLyrics();
        }, LYRICS_TAB_LYRICS);
    }

    private void showKaraokeDifficultyPanel() {
        showLyricsChoicePanel(getString(R.string.player_karaoke_difficulty), ResUtil.getStringArray(R.array.select_karaoke_difficulty), PlayerSetting.getKaraokeDifficulty(), which -> {
            PlayerSetting.putKaraokeDifficulty(which);
            reloadKaraokeTrack();
        }, LYRICS_TAB_KARAOKE);
    }

    private void showLyricsChoicePanel(String title, String[] items, int selected, LyricsChoiceHandler handler, int returnTab) {
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        root.addView(createKaraokeSheetHeader(dialog, title, () -> showLyricsSettingsPanel(returnTab)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(34)));
        root.addView(createLyricsChoiceGrid(dialog, items, selected, handler, returnTab), karaokeActionGridParams(8));
        dialog.setContentView(root);
        showLyricsSettingsSheet(dialog);
    }

    private LinearLayout createLyricsChoiceGrid(BottomSheetDialog dialog, String[] items, int selected, LyricsChoiceHandler handler, int returnTab) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        int columns = 3;
        for (int i = 0; i < items.length; i++) {
            if (i % columns == 0) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(40));
                if (i > 0) rowParams.topMargin = ResUtil.dp2px(6);
                grid.addView(row, rowParams);
            }
            LinearLayout row = (LinearLayout) grid.getChildAt(grid.getChildCount() - 1);
            final int index = i;
            row.addView(createLyricsChoiceItem(items[i], i == selected, () -> {
                dialog.dismiss();
                handler.onChoice(index);
                showLyricsSettingsPanel(returnTab);
            }), karaokeActionButtonParams(i % columns > 0));
        }
        return grid;
    }

    private interface LyricsChoiceHandler {
        void onChoice(int which);
    }

    private LinearLayout createLyricsSettingsTabs(BottomSheetDialog dialog, int selectedTab) {
        return createSegmentedControl(
                new String[]{getString(R.string.player_audio_badge_lyrics), getString(R.string.player_karaoke_mode), getString(R.string.player_karaoke_track)},
                selectedTab,
                index -> {
                    if (index == selectedTab) return;
                    dialog.dismiss();
                    showLyricsSettingsPanel(index);
                });
    }

    private void showKaraokeTrackAdvancedPanel() {
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        root.addView(createKaraokeSheetHeader(dialog, getString(R.string.player_karaoke_track_advanced), this::showKaraokeModePanel), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(34)));
        root.addView(createAudioSheetSection(getString(R.string.player_karaoke_track)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(40)));
        root.addView(createKaraokeActionGrid(dialog, true,
                new String[]{
                        getKaraokeBasicPitchLabel(),
                        getString(R.string.player_karaoke_track_search),
                        getString(R.string.player_karaoke_track_import_file),
                        getString(R.string.player_karaoke_track_import_url),
                        getString(R.string.player_karaoke_track_sources)
                },
                new Runnable[]{
                        this::toggleKaraokeBasicPitchTflite,
                        this::showKaraokeTrackSearchDialog,
                        this::chooseKaraokeTrackFile,
                        this::showKaraokeTrackUrlDialog,
                        this::showKaraokeTrackSourcesDialog
                },
                2), karaokeActionGridParams(6));
        dialog.setContentView(root);
        showLyricsSettingsSheet(dialog);
    }

    private String getKaraokeBasicPitchLabel() {
        return getString(R.string.player_karaoke_track_basic_pitch_tflite, getString(PlayerSetting.isKaraokeBasicPitchTflite() ? R.string.player_karaoke_track_option_enabled : R.string.player_karaoke_track_option_disabled));
    }

    private void toggleKaraokeBasicPitchTflite() {
        PlayerSetting.putKaraokeBasicPitchTflite(!PlayerSetting.isKaraokeBasicPitchTflite());
        showKaraokeTrackAdvancedPanel();
    }

    private void toggleKaraokeBasicPitchTfliteFromSettings() {
        PlayerSetting.putKaraokeBasicPitchTflite(!PlayerSetting.isKaraokeBasicPitchTflite());
        showLyricsSettingsPanel(LYRICS_TAB_TRACK);
    }

    private LinearLayout createKaraokeSheetHeader(BottomSheetDialog dialog, String title, Runnable backAction) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView titleView = createAudioSheetTitle(title);
        row.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        row.addView(createKaraokeHeaderButton(dialog, getString(R.string.player_karaoke_track_back), backAction), new LinearLayout.LayoutParams(ResUtil.dp2px(76), ResUtil.dp2px(32)));
        return row;
    }

    private TextView createKaraokeHeaderButton(BottomSheetDialog dialog, String label, Runnable action) {
        TextView view = createAudioSheetText(label, 14, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setTextColor(0xE6FFFFFF);
        view.setBackground(roundRect(0x12FFFFFF, SHEET_BUTTON_RADIUS_DP, 1, 0x24FFFFFF));
        view.setOnClickListener(v -> {
            dialog.dismiss();
            action.run();
        });
        return view;
    }

    private LinearLayout createKaraokeActionGrid(BottomSheetDialog dialog, boolean compact, String[] labels, Runnable[] actions, int columns) {
        return createKaraokeActionGrid(dialog, compact, labels, actions, columns, true);
    }

    private LinearLayout createKaraokeActionGrid(BottomSheetDialog dialog, boolean compact, String[] labels, Runnable[] actions, int columns, boolean dismissOnClick) {
        return createKaraokeActionGrid(dialog, compact, labels, actions, null, columns, dismissOnClick);
    }

    private LinearLayout createKaraokeActionGrid(BottomSheetDialog dialog, boolean compact, String[] labels, Runnable[] actions, boolean[] dismissOnClicks, int columns) {
        return createKaraokeActionGrid(dialog, compact, labels, actions, dismissOnClicks, columns, true);
    }

    private LinearLayout createKaraokeActionGrid(BottomSheetDialog dialog, boolean compact, String[] labels, Runnable[] actions, @Nullable boolean[] dismissOnClicks, int columns, boolean dismissOnClick) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        int safeColumns = Math.max(1, columns);
        for (int i = 0; i < labels.length; i += safeColumns) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            boolean fullRow = !compact && i + 1 == labels.length;
            for (int j = 0; j < safeColumns; j++) {
                int index = i + j;
                if (index >= labels.length) break;
                boolean dismiss = dismissOnClicks == null || index >= dismissOnClicks.length ? dismissOnClick : dismissOnClicks[index];
                row.addView(createKaraokeActionButton(dialog, labels[index], actions[index], compact, dismiss), fullRow ? karaokeActionButtonFullParams() : karaokeActionButtonParams(j > 0));
                if (fullRow) break;
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(compact ? 46 : 48));
            if (i > 0) rowParams.topMargin = ResUtil.dp2px(8);
            grid.addView(row, rowParams);
        }
        return grid;
    }

    private TextView createKaraokeActionButton(BottomSheetDialog dialog, String label, Runnable action, boolean compact) {
        return createKaraokeActionButton(dialog, label, action, compact, true);
    }

    private TextView createKaraokeActionButton(BottomSheetDialog dialog, String label, Runnable action, boolean compact, boolean dismissOnClick) {
        TextView view = createAudioSheetText(label, compact ? 14 : 15, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setPadding(ResUtil.dp2px(10), 0, ResUtil.dp2px(10), 0);
        view.setTextColor(0xF2FFFFFF);
        view.setBackground(roundRect(0x14FFFFFF, SHEET_BUTTON_RADIUS_DP, 1, 0x22FFFFFF));
        view.setOnClickListener(v -> {
            if (dismissOnClick) dialog.dismiss();
            action.run();
        });
        return view;
    }

    private LinearLayout.LayoutParams karaokeActionGridParams(int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = ResUtil.dp2px(topMarginDp);
        return params;
    }

    private LinearLayout.LayoutParams karaokeActionButtonParams(boolean withStartMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        if (withStartMargin) params.leftMargin = ResUtil.dp2px(10);
        return params;
    }

    private LinearLayout.LayoutParams karaokeActionButtonFullParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private View createKaraokeModeHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, 0);
        boolean enabled = PlayerSetting.isKaraokeMode();

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.HORIZONTAL);
        text.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = createAudioSheetText(getString(R.string.player_karaoke_mode), 15, true);
        TextView status = createAudioSheetText(getString(enabled ? R.string.player_karaoke_mode_enabled : R.string.player_karaoke_mode_disabled), 13, false);
        title.setTextColor(Color.WHITE);
        title.setSingleLine(true);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        status.setTextColor(enabled ? SHEET_TEXT_SECONDARY : SHEET_TEXT_MUTED);
        text.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        statusParams.leftMargin = ResUtil.dp2px(10);
        text.addView(status, statusParams);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        FrameLayout toggle = createKaraokeModeToggle(enabled);
        row.setOnClickListener(v -> {
            boolean next = !PlayerSetting.isKaraokeMode();
            setKaraokeMode(next);
            status.setText(getString(next ? R.string.player_karaoke_mode_enabled : R.string.player_karaoke_mode_disabled));
            status.setTextColor(next ? SHEET_TEXT_SECONDARY : SHEET_TEXT_MUTED);
            updateKaraokeModeToggle(toggle, next);
        });
        row.addView(toggle, new LinearLayout.LayoutParams(ResUtil.dp2px(50), ResUtil.dp2px(28)));
        return row;
    }

    private FrameLayout createKaraokeModeToggle(boolean enabled) {
        FrameLayout toggle = new FrameLayout(this);
        updateKaraokeModeToggle(toggle, enabled);
        return toggle;
    }

    private void updateKaraokeModeToggle(FrameLayout toggle, boolean enabled) {
        toggle.removeAllViews();
        toggle.setBackground(roundRect(enabled ? SHEET_CONTROL_BG_SELECTED : 0x18FFFFFF, 8, 1, enabled ? SHEET_CONTROL_STROKE_SELECTED : 0x2EFFFFFF));
        View knob = new View(this);
        knob.setBackground(roundRect(enabled ? SHEET_TEXT_PRIMARY : 0xFFE6E8EE, 6, 0, 0));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ResUtil.dp2px(22), ResUtil.dp2px(22), enabled ? Gravity.RIGHT | Gravity.CENTER_VERTICAL : Gravity.LEFT | Gravity.CENTER_VERTICAL);
        params.leftMargin = ResUtil.dp2px(3);
        params.rightMargin = ResUtil.dp2px(3);
        toggle.addView(knob, params);
    }

    private View createLyricsOffsetControl() {
        return createLyricsStepControl(getString(R.string.player_lyrics_offset), getLyricsOffsetText(), "-0.5s", "0", "+0.5s",
                value -> PlayerSetting.putLyricsTimeOffsetMs(value),
                () -> PlayerSetting.getLyricsTimeOffsetMs(),
                LYRICS_OFFSET_MIN_MS,
                LYRICS_OFFSET_MAX_MS,
                LYRICS_OFFSET_STEP_MS,
                this::applyLyricsRuntimeSettings);
    }

    private View createKaraokeDelayControl() {
        return createLyricsStepControl(getString(R.string.player_karaoke_mic_delay), getKaraokeDelayText(), "-0.1s", "0", "+0.1s",
                value -> PlayerSetting.putKaraokeMicDelayMs(value),
                () -> PlayerSetting.getKaraokeMicDelayMs(),
                KARAOKE_DELAY_MIN_MS,
                KARAOKE_DELAY_MAX_MS,
                KARAOKE_DELAY_STEP_MS,
                this::reloadKaraokeTrack);
    }

    private View createLyricsStepControl(String label, String valueText, String minus, String reset, String plus, LyricsLongSetter setter, LyricsLongGetter getter, long min, long max, long step, Runnable afterChange) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(ResUtil.dp2px(12), 0, ResUtil.dp2px(10), 0);
        row.setBackground(roundRect(0x12FFFFFF, SHEET_BUTTON_RADIUS_DP, 1, 0x22FFFFFF));

        LinearLayout text = new LinearLayout(this);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = createAudioSheetText(label, 15, false);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        TextView value = createAudioSheetText(valueText, 13, true);
        value.setSingleLine(true);
        value.setTextColor(SHEET_TEXT_SECONDARY);
        text.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.leftMargin = ResUtil.dp2px(10);
        text.addView(value, valueParams);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.addView(createLyricsStepButton(minus, () -> applyLyricsLongSetting(setter, getter, min, max, -step, value, afterChange)), lyricsStepButtonParams(false));
        buttons.addView(createLyricsStepButton(reset, () -> applyLyricsLongSetting(setter, () -> 0L, min, max, 0, value, afterChange)), lyricsStepButtonParams(true));
        buttons.addView(createLyricsStepButton(plus, () -> applyLyricsLongSetting(setter, getter, min, max, step, value, afterChange)), lyricsStepButtonParams(true));
        row.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void applyLyricsLongSetting(LyricsLongSetter setter, LyricsLongGetter getter, long min, long max, long delta, TextView value, Runnable afterChange) {
        long next = Math.min(Math.max(getter.get() + delta, min), max);
        setter.set(next);
        value.setText(formatLyricsOffset(getter.get()));
        if (afterChange != null) afterChange.run();
    }

    private TextView createLyricsStepButton(String label, Runnable action) {
        TextView view = createAudioSheetText(label, 13, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setTextColor(0xF2FFFFFF);
        view.setBackground(roundRect(0x16FFFFFF, SHEET_BUTTON_RADIUS_DP, 1, 0x28FFFFFF));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private LinearLayout.LayoutParams lyricsStepButtonParams(boolean withStartMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ResUtil.dp2px(52), ResUtil.dp2px(34));
        if (withStartMargin) params.leftMargin = ResUtil.dp2px(6);
        return params;
    }

    private TextView createLyricsChoiceItem(String label, boolean selected, Runnable action) {
        TextView item = createAudioSheetText(label, 15, selected);
        item.setGravity(Gravity.CENTER);
        item.setPadding(ResUtil.dp2px(14), 0, ResUtil.dp2px(14), 0);
        item.setSingleLine(true);
        item.setEllipsize(TextUtils.TruncateAt.END);
        item.setTextColor(selected ? SHEET_TEXT_PRIMARY : SHEET_TEXT_SECONDARY);
        item.setBackground(lyricsResultItemBackground(selected));
        item.setOnClickListener(v -> action.run());
        return item;
    }

    private LinearLayout.LayoutParams lyricsSettingRowParams(int topDp, int heightDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(heightDp));
        params.topMargin = ResUtil.dp2px(topDp);
        return params;
    }

    private void openLyricsSearchFromSettings() {
        if (!onLyricsSearch()) Notify.show(R.string.player_lyrics_not_found);
    }

    private void clearLyricsCacheFromSettings() {
        LyricsRepository.clearCache();
        Notify.show(R.string.player_lyrics_cache_cleared);
    }

    private void applyLyricsRuntimeSettings() {
        if (service() == null || player().isEmpty()) return;
        if (mLyrics != null) {
            mLyrics.refreshStyle();
            mLyrics.update(player());
        }
        syncKaraokePosition();
        if (mKaraoke != null) mKaraoke.update(player(), mLyrics == null ? null : mLyrics.getLines());
    }

    private boolean toggleDesktopLyrics() {
        boolean enabled = !PlayerSetting.isDesktopLyrics();
        PlayerSetting.putDesktopLyrics(enabled);
        if (enabled && !canDrawOverlays()) openOverlayPermission();
        return enabled;
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void openOverlayPermission() {
        Notify.show(R.string.player_desktop_lyrics_permission);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    private String lyricsSizeText() {
        String[] items = ResUtil.getStringArray(R.array.select_lyrics_size);
        return items[PlayerSetting.getLyricsTextSizeOption()];
    }

    private String lyricsSourceText() {
        String[] items = ResUtil.getStringArray(R.array.select_lyrics_source);
        return items[LyricsSetting.getSourceMode()];
    }

    private String karaokeDifficultyText() {
        String[] items = ResUtil.getStringArray(R.array.select_karaoke_difficulty);
        return items[PlayerSetting.getKaraokeDifficulty()];
    }

    private String getLyricsRowsText() {
        return getString(R.string.player_lyrics_rows_value, PlayerSetting.getLyricsRows());
    }

    private String getLyricsOffsetText() {
        return formatLyricsOffset(PlayerSetting.getLyricsTimeOffsetMs());
    }

    private String getKaraokeDelayText() {
        return formatLyricsOffset(PlayerSetting.getKaraokeMicDelayMs());
    }

    private String formatLyricsOffset(long valueMs) {
        if (valueMs == 0) return "0s";
        return String.format(Locale.getDefault(), "%+.1fs", valueMs / 1000f);
    }

    private interface LyricsLongSetter {
        void set(long value);
    }

    private interface LyricsLongGetter {
        long get();
    }

    private void chooseKaraokeTrackFile() {
        FileChooser.from(mKaraokeTrackFile).show("*/*", new String[]{"text/plain", "audio/midi", "audio/x-midi", "application/octet-stream", "*/*"});
    }

    private void showKaraokeTrackUrlDialog() {
        showAudioTextInputSheet(R.string.player_karaoke_track_import_url, R.string.player_karaoke_track_url_hint, "", true, 2,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                EditorInfo.IME_ACTION_DONE,
                this::importKaraokeTrackUrl);
    }

    private void showKaraokeTrackSourcesDialog() {
        showAudioTextInputSheet(R.string.player_karaoke_track_sources, R.string.player_karaoke_track_sources_hint, PlayerSetting.getKaraokeGithubSources(), true, 4,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                EditorInfo.IME_ACTION_DONE,
                this::saveKaraokeTrackSources);
    }

    private void saveKaraokeTrackSources(String sources) {
        PlayerSetting.putKaraokeGithubSources(sources);
        KaraokeTrackRepository.clearSearchCache();
        Notify.show(R.string.player_karaoke_track_sources_saved);
    }

    private void showKaraokeTrackSearchDialog() {
        showAudioTextInputSheet(R.string.player_karaoke_track_search, R.string.player_karaoke_track_keyword, KaraokeTrackRepository.defaultKeyword(player()), false, 1,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                EditorInfo.IME_ACTION_SEARCH,
                this::searchKaraokeTrack);
    }

    private void showAudioTextInputSheet(int titleRes, int hintRes, String text, boolean multiLine, int minLines, int inputType, int imeAction, AudioTextInputHandler handler) {
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        root.addView(createAudioSheetTitle(getString(titleRes)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(32)));
        TextInputLayout layout = new TextInputLayout(this);
        styleAudioSheetInput(layout, getString(hintRes));
        TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0x70FFFFFF);
        input.setInputType(inputType);
        input.setImeOptions(imeAction);
        input.setText(Objects.toString(text, ""));
        input.setSelectAllOnFocus(!multiLine);
        if (multiLine) {
            input.setSingleLine(false);
            input.setMinLines(minLines);
            input.setMaxLines(Math.max(minLines, 4));
        } else {
            input.setSingleLine(true);
            input.setMaxLines(1);
        }
        if (input.getText() != null) input.setSelection(input.getText().length());
        layout.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(layout, audioSheetWrapTopParams(10));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(createAudioSheetButton(getString(R.string.dialog_negative), false, dialog::dismiss), audioSheetButtonParams(false));
        actions.addView(createAudioSheetButton(getString(R.string.dialog_positive), true, () -> {
            Util.hideKeyboard(input);
            dialog.dismiss();
            handler.onSubmit(input.getText() == null ? "" : input.getText().toString().trim());
        }), audioSheetButtonParams(true));
        root.addView(actions, audioSheetTopParams(10, 42));
        dialog.setContentView(root);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (multiLine || actionId != imeAction) return false;
            Util.hideKeyboard(input);
            dialog.dismiss();
            handler.onSubmit(input.getText() == null ? "" : input.getText().toString().trim());
            return true;
        });
        showLyricsSettingsSheet(dialog);
        input.post(() -> Util.showKeyboard(input));
    }

    private interface AudioTextInputHandler {
        void onSubmit(String text);
    }

    private void searchKaraokeTrack(String keyword) {
        if (service() == null || TextUtils.isEmpty(keyword)) return;
        Notify.show(R.string.player_karaoke_track_searching);
        KaraokeTrackRepository.search(player(), keyword, results -> {
            if (results == null || results.isEmpty()) {
                Notify.show(R.string.player_karaoke_track_not_found);
                return;
            }
            showKaraokeTrackResults(results);
        });
    }

    private void generateKaraokeTrack() {
        List<LyricsLine> lines = getKaraokeGenerationLines();
        android.util.Log.i("karaoke-generate", "rhythm requested lines=" + lines.size() + " inline=" + (!TextUtils.isEmpty(mInlineLyrics)) + " service=" + (service() != null));
        if (service() == null || !KaraokeTrackRepository.canGenerate(lines)) {
            Notify.show(R.string.player_karaoke_track_generate_no_lyrics);
            return;
        }
        onKaraokeTrackGenerated(KaraokeTrackRepository.importGenerated(player(), lines));
    }

    private void onKaraokeTrackGenerated(KaraokeTrackRepository.ImportResult result) {
        if (result != null && result.isSuccess()) {
            Notify.show(R.string.player_karaoke_track_generated);
            applyKaraokeTrackChange(true);
            restartKaraokePlaybackAfterGeneration();
        } else {
            String error = result == null ? "" : result.getError();
            Notify.show(getString(R.string.player_karaoke_track_generate_failed) + (TextUtils.isEmpty(error) ? "" : "\n" + error));
        }
    }

    private void generateKaraokePitchTrack() {
        List<LyricsLine> lines = getKaraokeGenerationLines();
        KaraokeTrackRepository.MediaInput input = service() == null ? null : KaraokeTrackRepository.snapshot(player());
        android.util.Log.i("karaoke-generate", "pitch requested basicPitch=" + PlayerSetting.isKaraokeBasicPitchTflite() + " lines=" + lines.size() + " input=" + (input == null ? "null" : input.getUrl()));
        if (!KaraokeTrackRepository.canGeneratePitch(input, lines)) {
            Notify.show(R.string.player_karaoke_track_generate_no_lyrics);
            return;
        }
        cancelKaraokePitchGeneration(false);
        AtomicBoolean cancel = new AtomicBoolean(false);
        mKaraokePitchCancel = cancel;
        showKaraokePitchProgress();
        mKaraokePitchFuture = Task.submit(() -> {
            KaraokeTrackRepository.ImportResult result = KaraokeTrackRepository.importGeneratedPitch(input, lines, (percent, stage, elapsedMs, remainingMs) -> {
                if (cancel.get() || Thread.currentThread().isInterrupted()) throw new CancellationException("cancelled");
                App.post(() -> updateKaraokePitchProgress(percent, stage, remainingMs));
            });
            App.post(() -> onKaraokePitchTrackGenerated(result, cancel));
        });
    }

    private List<LyricsLine> getKaraokeGenerationLines() {
        List<LyricsLine> lines = mLyrics == null ? null : mLyrics.getLines();
        if (lines != null && !lines.isEmpty()) return lines;
        String raw = !TextUtils.isEmpty(mInlineLyrics) ? mInlineLyrics : mDetailLyrics;
        if (!LyricsController.hasTimedLyrics(raw)) return new ArrayList<>();
        LyricsResult result = new LyricsResult("Inline", getAudioStageTitle(), getAudioStageArtist(getAudioStageTitle()), "", raw, player().getDuration(), true, 100);
        return new ArrayList<>(result.getLines(player().getDuration()));
    }

    private void onKaraokePitchTrackGenerated(KaraokeTrackRepository.ImportResult result, AtomicBoolean cancel) {
        if (cancel != null && cancel.get()) {
            if (mKaraokePitchCancel == cancel) {
                mKaraokePitchFuture = null;
                mKaraokePitchCancel = null;
                dismissKaraokePitchProgress();
            }
            return;
        }
        mKaraokePitchFuture = null;
        if (mKaraokePitchCancel == cancel) mKaraokePitchCancel = null;
        dismissKaraokePitchProgress();
        if (result != null && result.isSuccess()) {
            applyKaraokeTrackChange(true);
            restartKaraokePlaybackAfterGeneration();
            showKaraokePitchResult(R.string.player_karaoke_track_generated_pitch, getString(R.string.player_karaoke_track_generated_pitch_message));
        } else {
            String error = result == null ? "" : result.getError();
            showKaraokePitchResult(R.string.player_karaoke_track_generate_pitch_failed, getString(R.string.player_karaoke_track_generate_pitch_failed_message, getKaraokePitchFailureMessage(error)));
        }
    }

    private String getKaraokePitchFailureMessage(String error) {
        if (KaraokeTrackRepository.isUnsupportedPitchSourceError(error)) return getString(R.string.player_karaoke_track_generate_pitch_unsupported_source);
        return TextUtils.isEmpty(error) ? getString(R.string.player_karaoke_track_generate_pitch_failed) : error;
    }

    private void restartKaraokePlaybackAfterGeneration() {
        if (service() == null || player().isEmpty()) return;
        player().seekTo(0);
        if (mHistory != null) mHistory.setPosition(0);
        if (mLyrics != null) mLyrics.update(0);
        syncKaraokePosition();
    }

    private void showKaraokePitchProgress() {
        dismissKaraokePitchProgress();
        if (isFinishing() || isDestroyed()) {
            Notify.show(R.string.player_karaoke_track_generating_pitch);
            return;
        }
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        root.addView(createAudioSheetTitle(getString(R.string.player_karaoke_track_generating_pitch)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(32)));
        TextView message = createAudioSheetText(getString(R.string.player_karaoke_track_generating_pitch_message), 14, false);
        message.setTextColor(0xCCFFFFFF);
        message.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(message, audioSheetTopParams(10, 46));
        mKaraokePitchMessage = createAudioSheetText("", 15, true);
        mKaraokePitchMessage.setTextColor(SHEET_TEXT_SECONDARY);
        mKaraokePitchMessage.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(mKaraokePitchMessage, audioSheetTopParams(4, 36));
        mKaraokePitchProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        mKaraokePitchProgress.setIndeterminate(false);
        mKaraokePitchProgress.setMax(100);
        mKaraokePitchProgress.setProgressTintList(ColorStateList.valueOf(0xE6FFFFFF));
        mKaraokePitchProgress.setProgressBackgroundTintList(ColorStateList.valueOf(0x2AFFFFFF));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(6));
        params.topMargin = ResUtil.dp2px(8);
        root.addView(mKaraokePitchProgress, params);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(createAudioSheetButton(getString(R.string.player_karaoke_track_generation_stop), false, () -> cancelKaraokePitchGeneration(true)), audioSheetButtonParams(false));
        root.addView(actions, audioSheetTopParams(12, 40));
        dialog.setContentView(root);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnCancelListener(d -> cancelKaraokePitchGeneration(true));
        mKaraokePitchDialog = dialog;
        showAudioSheet(dialog, false);
        updateKaraokePitchProgress(1, KaraokePitchTrackGenerator.STAGE_PREPARE, -1);
    }

    private void updateKaraokePitchProgress(int percent, int stage, long remainingMs) {
        if (mKaraokePitchProgress == null || mKaraokePitchMessage == null) return;
        int safePercent = Math.max(0, Math.min(100, percent));
        mKaraokePitchProgress.setProgress(safePercent);
        mKaraokePitchMessage.setText(getString(R.string.player_karaoke_track_generating_pitch_progress, safePercent, getKaraokePitchStageName(stage), formatKaraokePitchRemaining(remainingMs)));
    }

    private String getKaraokePitchStageName(int stage) {
        if (stage == KaraokePitchTrackGenerator.STAGE_DECODE) return getString(R.string.player_karaoke_track_pitch_stage_decode);
        if (stage == KaraokePitchTrackGenerator.STAGE_ANALYZE) return getString(R.string.player_karaoke_track_pitch_stage_analyze);
        if (stage == KaraokePitchTrackGenerator.STAGE_WRITE) return getString(R.string.player_karaoke_track_pitch_stage_write);
        if (stage == KaraokePitchTrackGenerator.STAGE_FINISH) return getString(R.string.player_karaoke_track_pitch_stage_finish);
        return getString(R.string.player_karaoke_track_pitch_stage_prepare);
    }

    private String formatKaraokePitchRemaining(long remainingMs) {
        if (remainingMs <= 0) return getString(R.string.player_karaoke_track_pitch_remaining_unknown);
        long seconds = Math.max(1, Math.round(remainingMs / 1000.0));
        if (seconds < 60) return getString(R.string.player_karaoke_track_pitch_remaining_seconds, seconds);
        return getString(R.string.player_karaoke_track_pitch_remaining_minutes, seconds / 60, seconds % 60);
    }

    private void dismissKaraokePitchProgress() {
        if (mKaraokePitchDialog != null) {
            try {
                if (mKaraokePitchDialog.isShowing()) mKaraokePitchDialog.dismiss();
            } catch (Exception ignored) {
            }
        }
        mKaraokePitchDialog = null;
        mKaraokePitchProgress = null;
        mKaraokePitchMessage = null;
    }

    private void cancelKaraokePitchGeneration(boolean notify) {
        AtomicBoolean cancel = mKaraokePitchCancel;
        if (cancel != null) cancel.set(true);
        Future<?> future = mKaraokePitchFuture;
        if (future != null) future.cancel(true);
        mKaraokePitchFuture = null;
        mKaraokePitchCancel = null;
        dismissKaraokePitchProgress();
        if (notify) Notify.show(R.string.player_karaoke_track_generation_stopped);
    }

    private void showKaraokePitchResult(int title, String message) {
        if (isFinishing() || isDestroyed()) {
            Notify.show(message);
            return;
        }
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        root.addView(createAudioSheetTitle(getString(title)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(32)));
        TextView text = createAudioSheetText(message, 15, false);
        text.setTextColor(0xD9FFFFFF);
        text.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(text, audioSheetTopParams(12, 58));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.addView(createAudioSheetButton(getString(R.string.dialog_positive), true, dialog::dismiss), audioSheetButtonParams(false));
        root.addView(actions, audioSheetTopParams(12, 44));
        dialog.setContentView(root);
        showCompactPlaybackSheet(dialog);
    }

    private void showKaraokeTrackResults(List<KaraokeTrackRepository.SearchResult> results) {
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        root.addView(createAudioSheetTitle(getString(R.string.player_karaoke_track_select)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(32)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < results.size(); i++) {
            KaraokeTrackRepository.SearchResult result = results.get(i);
            String source = result.getSource() + (result.isLoginRequired() ? getString(R.string.player_karaoke_track_source_login) : "");
            String label = getString(R.string.player_karaoke_track_result_item, source, result.getArtist(), result.getTitle(), result.getNote());
            content.addView(createKaraokeTrackResultItem(label, () -> {
                dialog.dismiss();
                importKaraokeTrackUrl(result.getUrl());
            }), audioSheetTopParams(i == 0 ? 8 : 6, 76));
        }
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, karaokeTrackResultSheetHeight(results.size())));
        dialog.setContentView(root);
        showLyricsSettingsSheet(dialog);
    }

    private void importKaraokeTrackUrl(String url) {
        if (service() == null || TextUtils.isEmpty(url)) return;
        KaraokeTrackRepository.importUrl(player(), url, this::onKaraokeTrackImported);
    }

    private void onKaraokeTrackImported(KaraokeTrackRepository.ImportResult result) {
        if (result != null && result.isSuccess()) {
            Notify.show(R.string.player_karaoke_track_imported);
            applyKaraokeTrackChange(true);
        } else {
            String error = result == null ? "" : result.getError();
            Notify.show(getString(R.string.player_karaoke_track_import_failed) + (TextUtils.isEmpty(error) ? "" : "\n" + error));
        }
    }

    private void clearKaraokeTrackBinding() {
        if (service() == null) return;
        boolean cleared = KaraokeTrackRepository.clearBinding(player());
        Notify.show(cleared ? R.string.player_karaoke_track_cleared : R.string.player_karaoke_track_none);
        applyKaraokeTrackChange(false);
    }

    private void setKaraokeActionState() {
        if (mBinding.audioKaraokeAction != null) mBinding.audioKaraokeAction.setSelected(PlayerSetting.isKaraokeMode());
        applyActionButtonVisibility();
    }

    private void applyKaraokeTrackChange(boolean enableMode) {
        if (enableMode && !PlayerSetting.isKaraokeMode()) {
            PlayerSetting.putKaraokeMode(true);
            setKaraokeActionState();
        }
        refreshLyrics();
        reloadKaraokeTrack();
    }

    private void reloadKaraokeTrack() {
        if (mKaraoke == null || service() == null) return;
        updateAudioOnlyState();
        mKaraoke.reload(this, player(), isAudioOnly() || isMusicLike());
    }

    private boolean showKaraokeResultIfNeeded(int action) {
        if (mKaraoke == null || !mKaraoke.isActive() || mKaraokeResultShown || isFinishing() || isDestroyed()) return false;
        KaraokeResult result = mKaraoke.getResult();
        if (result == null) return false;
        mKaraokeResultShown = true;
        mPendingKaraokeResult = result;
        mKaraokeResultAction = action;
        if (mViewModel != null) mViewModel.setKaraokeResult(result, action);
        SpiderDebug.log("karaoke-result", "show action=%d", action);
        showKaraokeResultDialog(result, action);
        return true;
    }

    private void showKaraokeResultDialog(KaraokeResult result, int action) {
        if (result == null || isFinishing() || isDestroyed()) return;
        if (mKaraokeResultDialog != null && mKaraokeResultDialog.isShowing()) return;
        KaraokeResultView view = new KaraokeResultView(this).setResult(result);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_WebHTV_LightDialog).setView(view).create();
        view.setAction(() -> {
            dialog.dismiss();
            completeKaraokeResult(action);
        });
        dialog.setOnCancelListener(d -> {
            if (!isChangingConfigurations() && !mSuppressKaraokeResultAction) completeKaraokeResult(action);
        });
        dialog.setOnDismissListener(d -> {
            if (mKaraokeResultDialog == dialog) mKaraokeResultDialog = null;
        });
        mKaraokeResultDialog = dialog;
        configureKaraokeResultDialog(dialog, view);
        dialog.show();
    }

    private void configureKaraokeResultDialog(AlertDialog dialog, KaraokeResultView view) {
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                WindowManager.LayoutParams params = window.getAttributes();
                if (isLandscapeAudioSheet()) {
                    params.dimAmount = 0f;
                    params.gravity = Gravity.CENTER;
                    params.x = 0;
                    params.y = 0;
                    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                    window.setAttributes(params);
                    window.setLayout(view.getPreferredDialogWidth(), WindowManager.LayoutParams.WRAP_CONTENT);
                } else {
                    params.dimAmount = 0.62f;
                    params.gravity = Gravity.CENTER;
                    params.y = isLand() ? -ResUtil.dp2px(12) : 0;
                    window.setAttributes(params);
                    window.setLayout(view.getPreferredDialogWidth(), WindowManager.LayoutParams.WRAP_CONTENT);
                    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                }
            }
            view.requestActionFocus();
        });
    }

    private void completeKaraokeResult(int action) {
        mPendingKaraokeResult = null;
        mKaraokeResultAction = KARAOKE_RESULT_ACTION_NONE;
        if (mViewModel != null) mViewModel.clearKaraokeResult();
        switch (action) {
            case KARAOKE_RESULT_ACTION_NEXT -> {
                if (!playNextAudioPlaylistEntry()) checkNext(true);
            }
            case KARAOKE_RESULT_ACTION_NEXT_SILENT -> {
                if (!playNextAudioPlaylistEntry()) checkNext(false);
            }
            case KARAOKE_RESULT_ACTION_FINISH -> finishVideoPlaybackNow();
            case KARAOKE_RESULT_ACTION_SYSTEM_BACK -> finishVideoPlaybackFromSystemBack();
            default -> {
            }
        }
    }

    @Override
    public void onCodecCapabilityPanel() {
        CodecCapabilityDialog.show(this, player());
    }

    @Override
    public void onPlayParamsPanel() {
        onPlayParams();
    }

    @Override
    public ActivityVideoBinding getControlBinding() {
        return mBinding;
    }

    @Override
    public PlayerManager getControlPlayer() {
        return service() == null ? null : player();
    }

    @Override
    public History getControlHistory() {
        return mHistory;
    }

    @Override
    public boolean isControlParseEnabled() {
        return isUseParse();
    }

    @Override
    public boolean isControlAudioContent() {
        return isAudioOnly() || isMusicLike();
    }

    @Override
    public boolean isControlMultiEpisode() {
        return mEpisodeAdapter != null && getEpisodeCount() >= 2;
    }

    @Override
    public boolean isDanmakuFullscreen() {
        return isFullscreen();
    }

    private void onDanmakuShow() {
        DanmakuSetting.putShow(!DanmakuSetting.isShow());
        checkDanmakuImg();
        showDanmaku();
    }

    private void onRepeat() {
        player().setRepeatOne(!player().isRepeatOne());
        setAudioRepeatSelected(player().isRepeatOne());
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        setAudioRepeatSelected(player().isRepeatOne());
    }

    private void onScale() {
        int index = getScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        if (mKeyDown.getScale() != 1.0f) mKeyDown.resetScale();
        else setScale(index == array.length - 1 ? 0 : ++index);
        setR1Callback();
    }

    private void onLut() {
        if (hasLutQuick()) {
            mBinding.lutQuick.toggle(player(), mBinding.exo, this::onLutChanged, new com.fongmi.android.tv.ui.custom.LutQuickPanel.ImportCallback() {
                @Override
                public void onImportLut() {
                    onLutImport();
                }

                @Override
                public void onSelectLutDir() {
                    onLutDir();
                }
            });
        }
        else LutPanelDialog.create().player(player()).show(this);
        setR1Callback();
    }

    @Override
    public void onLutPanel() {
        if (isFullscreen() && hasLutQuick()) onLut();
        else LutPanelDialog.create().player(player()).show(this);
    }

    private boolean hasLutQuick() {
        return mBinding.lutQuick != null;
    }

    private void onLutChanged() {
        setLut();
    }

    @Override
    public void onLutImport() {
        if (!LutStore.hasUserDir()) {
            pendingLutImport = true;
            chooseLutDir();
            return;
        }
        chooseLutFile();
    }

    @Override
    public void onLutDir() {
        pendingLutImport = false;
        chooseLutDir();
    }

    private void chooseLutFile() {
        skipPausePiP = true;
        FileChooser.from(mLutFile).show("*/*", new String[]{"application/octet-stream", "text/*", "image/*", "*/*"});
    }

    private void chooseLutDir() {
        skipPausePiP = true;
        FileChooser.from(mLutDir).showDirectory();
    }

    @Override
    public void onLutSelected(LutPreset preset) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-ui", "activity select preset=%s enabledBefore=%s current=%s", preset == null ? "original" : preset.getId(), LutSetting.isEnabled(), LutSetting.getPresetId());
        if (!player().selectLut(preset, preset != null)) return;
        setLut();
        setR1Callback();
    }

    private void onSpeed() {
        mBinding.control.action.speed.setText(player().addSpeed());
        saveDefaultSpeed();
        setR1Callback();
    }

    private boolean onSpeedLong() {
        mBinding.control.action.speed.setText(player().toggleSpeed());
        saveDefaultSpeed();
        setR1Callback();
        return true;
    }

    private void saveDefaultSpeed() {
        PlayerSetting.putDefaultSpeed(player().getSpeed());
        mHistory.setSpeed(player().getSpeed());
    }

    private void onReset() {
        if (isReplay()) onReplay();
        else onRefresh();
    }

    private void onReplay() {
        mHistory.setPosition(C.TIME_UNSET);
        if (player().isEmpty()) onRefresh();
        else player().setMediaItem();
    }

    private void onRefresh() {
        saveHistory();
        player().stop();
        player().clear();
        mClock.setCallback(null);
        clearLyrics();
        clearKaraokeState();
        if (mFlagAdapter.isEmpty()) return;
        if (mEpisodeAdapter.isEmpty()) return;
        Flag flag = getFlag();
        if (flag != null) {
            // 播放入口的显式缺省：无选中时播第 1 集（旧 getEpisode 回落的唯一合法用途，挪到这里明写）
            Episode episode = getEpisode();
            if (episode == null && !flag.getEpisodes().isEmpty()) episode = flag.getEpisodes().get(0);
            if (episode != null) getPlayer(flag, episode);
        }
    }

    private boolean onResetToggle() {
        Setting.putReset(Math.abs(Setting.getReset() - 1));
        return true;
    }

    private void onDecode() {
        if (refreshAndSwitchDecode()) return;
        mClock.setCallback(null);
        clearLyrics();
        player().toggleDecode();
        setR1Callback();
        setDecode();
    }

    private boolean refreshAndSwitchDecode() {
        if (decodeSwitchRefreshing) return true;
        if (getFlag() == null || getEpisode() == null) return false;
        long position = player().getPosition();
        float speed = player().getSpeed();
        boolean repeat = player().isRepeatOne();
        String key = getKey();
        String flag = getFlag().getFlag();
        String episode = getEpisode().getUrl();
        MediaMetadata metadata = buildMetadata();
        decodeSwitchRefreshing = true;
        setNextDecodeText();
        setDecodeSwitchPending(true);
        mClock.setCallback(null);
        SpiderDebug.log("video-flow", "switch decode refresh start key=%s flag=%s episode=%s", key, flag, episode);
        Task.execute(() -> {
            try {
                Result result = SiteApi.playerContent(key, flag, episode);
                App.post(() -> switchDecodeWithResult(result, position, speed, repeat, metadata));
            } catch (Throwable e) {
                App.post(() -> {
                    decodeSwitchRefreshing = false;
                    setDecodeSwitchPending(false);
                    setDecode();
                    Notify.show(e.getMessage());
                });
            }
        });
        return true;
    }

    private void switchDecodeWithResult(Result result, long position, float speed, boolean repeat, MediaMetadata metadata) {
        decodeSwitchRefreshing = false;
        if (result == null || result.hasMsg() || result.getRealUrl().isEmpty()) {
            player().toggleDecode();
        } else {
            player().switchDecode(result, getHistoryKey(), metadata, isUseParse(), position, speed, repeat);
        }
        setR1Callback();
        setDecodeSwitchPending(false);
        setDecode();
    }

    private void onEnding() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetEnding(position, duration)) setEnding(duration - position);
        setR1Callback();
    }

    private boolean onEndingReset() {
        setR1Callback();
        setEnding(0);
        return true;
    }

    private void setEnding(long ending) {
        mHistory.setEnding(ending);
        refreshOpeningEnding();
    }

    /** 片头/片尾打点态：变金＋挂记住的时间；取消打点恢复 */
    private void refreshOpeningEnding() {
        boolean hasOpening = mHistory != null && mHistory.getOpening() > 0;
        boolean hasEnding = mHistory != null && mHistory.getEnding() > 0;
        int gold = getResources().getColor(R.color.player_gold);
        int white = getResources().getColor(R.color.white);
        mBinding.control.action.opening.setTextColor(hasOpening ? gold : white);
        mBinding.control.action.openingIcon.setColorFilter(hasOpening ? gold : white);
        mBinding.control.action.openingTime.setVisibility(hasOpening ? View.VISIBLE : View.GONE);
        if (hasOpening) mBinding.control.action.openingTime.setText(Util.timeMs(mHistory.getOpening()));
        mBinding.control.action.ending.setTextColor(hasEnding ? gold : white);
        mBinding.control.action.endingIcon.setColorFilter(hasEnding ? gold : white);
        mBinding.control.action.endingTime.setVisibility(hasEnding ? View.VISIBLE : View.GONE);
        if (hasEnding) mBinding.control.action.endingTime.setText(Util.timeMs(mHistory.getEnding()));
    }

    private void onOpening() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetOpening(position, duration)) setOpening(position);
        setR1Callback();
    }

    private boolean onOpeningReset() {
        setR1Callback();
        setOpening(0);
        return true;
    }

    private void setOpening(long opening) {
        mHistory.setOpening(opening);
        refreshOpeningEnding();
    }

    private void onEpisodes() {
        syncSelectedEpisode(getFlag());
        EpisodeListDialog.create().flags(mFlagAdapter.getItems()).reverse(mHistory.isRevSort()).shortDrama(mShortDramaMode).show(this);
    }

    private void onChoose() {
        PlayerHelper.choose(this, player().getUrl(), player().getHeaders(), player().isVod(), player().getPosition(), mBinding.control.title.getText());
        setRedirect(true);
    }

    private boolean onChooseLong() {
        onChoose();
        return true;
    }

    private void onPlayerKernel() {
        if (playerKernelSwitchRefreshing) return;
        int next = player().getPlayerType() == PlayerSetting.EXO ? PlayerSetting.MPV : PlayerSetting.EXO;
        switchPlayerKernel(next);
    }

    private void switchPlayerKernel(int type) {
        if (refreshAndSwitchPlayerKernel(type)) return;
        mClock.setCallback(null);
        clearLyrics();
        player().switchPlayer(type);
        setPlayerKernel();
        setDecode();
        setR1Callback();
    }

    private boolean refreshAndSwitchPlayerKernel(int type) {
        if (playerKernelSwitchRefreshing) return true;
        Flag currentFlag = getFlag();
        Episode currentEpisode = getEpisode();
        if (currentFlag == null || currentEpisode == null || TextUtils.isEmpty(currentFlag.getFlag()) || TextUtils.isEmpty(currentEpisode.getUrl())) return false;
        int nextType = PlayerSetting.sanitizePlayer(type);
        long position = getPlayerSwitchPosition();
        float speed = player().getSpeed();
        boolean repeat = player().isRepeatOne();
        String key = getKey();
        String flag = currentFlag.getFlag();
        String episode = currentEpisode.getUrl();
        MediaMetadata metadata = buildMetadata();
        playerKernelSwitchRefreshing = true;
        mClock.setCallback(null);
        SpiderDebug.log("video-flow", "switch player refresh start type=%d key=%s flag=%s episode=%s", nextType, key, flag, episode);
        Task.execute(() -> {
            try {
                Result result = SiteApi.playerContent(key, flag, episode, nextType);
                App.post(() -> switchPlayerKernelWithResult(nextType, result, position, speed, repeat, metadata));
            } catch (Throwable e) {
                App.post(() -> {
                    playerKernelSwitchRefreshing = false;
                    setPlayerKernel();
                    setDecode();
                    setR1Callback();
                    Notify.show(e.getMessage());
                });
            }
        });
        return true;
    }

    private long getPlayerSwitchPosition() {
        long position = Math.max(0, player().getPosition());
        long history = mHistory == null ? 0 : Math.max(0, mHistory.getPosition());
        if (mAudioStageVisible && position < 2000 && history > 5000) {
            SpiderDebug.log("video-flow", "switch player recover transient position current=%d history=%d", position, history);
            return history;
        }
        return position;
    }

    private void switchPlayerKernelWithResult(int type, Result result, long position, float speed, boolean repeat, MediaMetadata metadata) {
        playerKernelSwitchRefreshing = false;
        if (result == null || result.hasMsg() || result.getRealUrl().isEmpty()) {
            Notify.show(result != null && result.hasMsg() ? result.getMsg() : getString(R.string.error_play_url));
        } else {
            player().switchPlayer(type, result, getHistoryKey(), metadata, isUseParse(), position, speed, repeat);
        }
        setPlayerKernel();
        setDecode();
        setR1Callback();
    }

    private boolean onTextLong() {
        // 字幕长按＝在线搜索：有轨时直达轨列表搜索按钮那条路（SubtitleSearchDialog），不再开样式面板
        if (!player().haveTrack(C.TRACK_TYPE_TEXT)) {
            Notify.show("当前没有字幕轨");
            return true;
        }
        SubtitleSearchDialog.create().player(player()).restoreParent(true).show(this);
        return true;
    }

    private boolean onActionTouch(View v, MotionEvent e) {
        setR1Callback();
        return false;
    }

    private void onSwipeRefresh() {
        if (mBinding.progressLayout.isEmpty()) getDetail();
        else onRefresh();
    }

    private boolean shouldEnterFullscreen(Episode item) {
        // 点火期（mAutoIgniting）只摘副作用：手动点集的进全屏判定语义一字不动。
        boolean enter = !isFullscreen() && item.isSelected() && !mAutoIgniting;
        if (enter) enterFullscreen();
        return enter;
    }

    /**
     * 进入全屏时的方向判定（优先级从高到低）：
     * 1. 后台预取的真实视频宽高缓存（最准，无闪烁）
     * 2. 播放器已加载的元数据
     * 3. 短剧启发式（短剧源默认竖屏）
     */
    private boolean resolveOrientation() {
        try {
            // 1) 查预取缓存
            Episode ep = getEpisode();
            String url = ep == null ? null : ep.getUrl();
            if (!TextUtils.isEmpty(url)) {
                Boolean cached = com.fongmi.android.tv.utils.VideoDimensionCache.isPortrait(url);
                if (cached != null) return cached;
            }
        } catch (Exception ignored) {
        }
        try {
            // 2) 播放器元数据：宽高已知时才准；未知时 isPortrait() 恒返回 false，不可信，必须落到第 3 步启发式
            if (player() != null && player().getVideoWidth() > 0 && player().getVideoHeight() > 0) {
                return player().isPortrait();
            }
        } catch (Exception ignored) {
        }
        // 3) 启发式：短剧默认竖屏
        return mShortDramaMode;
    }

    private void enterFullscreen() {
        if (isFullscreen()) return;
        if (service() == null) {
            SpiderDebug.log("video-flow", "fullscreen enter deferred reason=player-not-ready");
            return;
        }
        coverVideoForFullscreenSwap();
        logVideoFrame("enterFullscreen before");
        setFullscreen(true);
        // 方向判定优先级：1) 后台预取的真实宽高缓存（最准） 2) 播放器元数据 3) 短剧启发式
        boolean isPortrait = resolveOrientation();
        // 尺寸过渡：横屏内容沿用原口径；竖屏内容在竖屏下进全屏（短剧式）同样给过渡，
        // 否则视频框从嵌入尺寸瞬跳满屏。嵌入态与横屏现状不受影响。
        if (isLand() && !player().isPortrait()) setTransition();
        else if (isPort() && isPortrait) setTransition();
        mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        setRequestedOrientation(PlaybackOrientation.getEnterFullscreenOrientation(isPortrait));
        mBinding.control.title.setVisibility(View.VISIBLE);
        setSizeText();
        setRotate(isPortrait);
        mKeyDown.resetScale();
        App.post(mR3, 2000);
        hideControl();
        logVideoFrame("enterFullscreen after");
        revealVideoAfterFullscreenSwap();
    }

    private void exitFullscreen() {
        if (!isFullscreen()) return;
        mUserExitedFullscreenForThisVideo = true;
        if (service() == null) {
            SpiderDebug.log("video-flow", "fullscreen exit deferred reason=player-not-ready");
            return;
        }
        coverVideoForFullscreenSwap();
        logVideoFrame("exitFullscreen before");
        setFullscreen(false);
        // Restore system bars: hideSystemUI() set FLAG_FULLSCREEN, must clear it
        // or the status-bar area stays black ("顶部残留") after rotating to portrait.
        try {
            com.fongmi.android.tv.utils.Util.showSystemUI(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 尺寸过渡口径与进全屏对称：横屏沿用原条件；竖屏全屏退出（当前竖屏＋竖屏内容）补上，
        // 视频框恢复嵌入尺寸时不再瞬跳。
        if (isLand() && !player().isPortrait()) setTransition();
        else if (isPort() && resolveOrientation()) setTransition();
        setRequestedOrientation(PlaybackOrientation.getExitFullscreenOrientation(isPort()));
        mBinding.episodeGroup.postDelayed(() -> mBinding.episodeGroup.scrollToPosition(mEpisodeGroupAdapter.getPosition()), 100);
        mBinding.episode.postDelayed(this::scrollEpisodeToSelected, 100);
        mBinding.control.title.setVisibility(View.INVISIBLE);
        setSizeText();
        mBinding.video.setLayoutParams(mFrameParams);
        mKeyDown.resetScale();
        App.post(mR3, 2000);
        setRotate(false);
        hideControl();
        logVideoFrame("exitFullscreen after");
        revealVideoAfterFullscreenSwap();
    }

    private View mFullscreenFadeCover;

    /**
     * 全屏进出渐变（只作用在容器层）：切换瞬间先用黑罩盖满视频容器，尺寸与方向的
     * 跳变在罩下完成，随后罩用全 App 统一的 200ms 淡出。黑罩只是视频容器里的普通
     * View，不碰播放器表面、解码与渲染路径，也不延迟任何切换动作；原生内核跳过尺寸
     * 补间时同样靠这一层兜住硬切。系统动画关闭时 reveal 直接摘罩、不留黑屏。
     */
    private void coverVideoForFullscreenSwap() {
        try {
            if (mFullscreenFadeCover == null) {
                mFullscreenFadeCover = new View(this);
                mFullscreenFadeCover.setBackgroundColor(Color.BLACK);
                mFullscreenFadeCover.setClickable(false);
                mFullscreenFadeCover.setFocusable(false);
            }
            View cover = mFullscreenFadeCover;
            if (cover.getParent() != mBinding.video) {
                if (cover.getParent() instanceof ViewGroup) ((ViewGroup) cover.getParent()).removeView(cover);
                mBinding.video.addView(cover, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            } else {
                cover.bringToFront();
            }
            cover.animate().cancel();
            cover.setAlpha(1f);
            cover.setVisibility(View.VISIBLE);
        } catch (Throwable ignored) {
        }
    }

    private void revealVideoAfterFullscreenSwap() {
        final View cover = mFullscreenFadeCover;
        if (cover == null || cover.getVisibility() != View.VISIBLE) return;
        if (!XcFadeTransition.isAnimEnabled(this)) {
            cover.setVisibility(View.GONE);
            return;
        }
        cover.animate().alpha(0f).setDuration(XcFadeTransition.DURATION_MS)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    if (mFullscreenFadeCover == cover) cover.setVisibility(View.GONE);
                })
                .start();
    }

    private void setTransition() {
        if (!shouldAnimateVideoFrameTransition()) {
            Log.d(SIZE_TAG, "video transition skipped native player=" + player().getPlayerText());
            return;
        }
        ChangeBounds transition = new ChangeBounds();
        transition.setDuration(150);
        ViewGroup parent = (ViewGroup) mBinding.video.getParent();
        TransitionManager.beginDelayedTransition(parent, transition);
    }

    private boolean shouldAnimateVideoFrameTransition() {
        return player() == null || !player().isNativePlayer();
    }

    private void showProgress() {
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
        App.post(mR2, 0);
        hideError();
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR2);
        Traffic.reset();
    }

    private void showError(String text) {
        mBinding.widget.error.setVisibility(View.VISIBLE);
        if (!TextUtils.isEmpty(text)) mBinding.widget.errorText.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
    }

    private void showDanmaku() {
        PlayerManager manager = player();
        if (manager != null) manager.setDanmakuEnabled(DanmakuSetting.isShow());
    }

    private void hideDanmaku() {
        PlayerManager manager = player();
        if (manager != null) manager.setDanmakuEnabled(false);
    }

    private void showControl() {
        if (service() == null || player() == null || isInPictureInPictureMode()) return;
        if (mAudioStageVisible && !isFullscreen()) {
            hideWidgetOverlay();
            hideControl();
            return;
        }
        setTrackVisible();
        hideWidgetOverlay();
        // 弹幕开关常驻（用户明令，「该藏的藏」只豁免这一个）：未锁屏就露脸，没弹幕/关着也露——关着时用户得能在这儿直接打开；图标两态由 checkDanmakuImg 按 isShow 刷
        mBinding.control.danmaku.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        // 竖屏套按实际屏幕方向定：屏幕横了走横屏套（哪怕视频本身是竖的短剧），不按视频宽高
        boolean isVerticalFullscreen = isFullscreen() && !isActualLand();
        mBinding.control.setting.setVisibility(isVerticalFullscreen && !isLock() ? View.VISIBLE : View.GONE);
        // 细设入口（右上角同款）：横屏全屏＋竖屏全屏＋竖屏非全屏（详情页内嵌播放）均显示——详情页无独立播放页，非全屏态够不着等于详情页够不着；细设两项（弹幕/字幕）都无对象时入口一并藏（该藏的藏）；直播弹幕经 haveLiveDanmaku() 并入弹幕对象（直播间在本页承载，见 onFineSetting 注）
        mBinding.control.fineSetting.setVisibility(!isLock() && (player().haveDanmaku() || player().haveLiveDanmaku() || player().haveTrack(C.TRACK_TYPE_TEXT) || player().isVod()) ? View.VISIBLE : View.GONE);
        mBinding.control.right.rotate.setVisibility(isFullscreen() && !isLock() ? View.VISIBLE : View.GONE);
        mBinding.control.fullscreen.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.keep.setVisibility(mHistory == null || isFullscreen() ? View.GONE : View.VISIBLE);
        mBinding.control.osdDiagnostics.setVisibility(PlayerSetting.isOsdDiagnostics() && PlayerButtonSetting.isVisible(PlayerButtonSetting.PLAY_PARAMS) && !player().isEmpty() ? View.VISIBLE : View.GONE);
        mBinding.control.osdDiagnostics.setAlpha(mOsd != null && mOsd.isDiagnosticsVisible() ? 1f : 0.30f);
        mBinding.control.parse.setVisibility(isFullscreen() && isUseParse() ? View.VISIBLE : View.GONE);
        mBinding.control.action.getRoot().setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        // 更多面板为独立弹窗（定稿 9 宫格），不再随竖屏全屏隐藏其中按钮
        // 竖排选集与底部行选集同一判定：集数<2（单片）不露，选集只对多集有意义
        mBinding.control.right.episodesVertical.setVisibility(isVerticalFullscreen && !isLock() && mEpisodeAdapter != null && getEpisodeCount() >= 2 ? View.VISIBLE : View.GONE);
        mBinding.control.right.castVertical.setVisibility(isVerticalFullscreen && !isLock() ? View.VISIBLE : View.GONE);
        updateCommentEntries();
        mBinding.control.right.lock.setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.info.setVisibility(player().isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.control.cast.setVisibility(View.GONE);
        mBinding.control.center.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.bottom.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.back.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.top.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        View controlRoot = mBinding.control.getRoot();
        if (controlRoot.getVisibility() != View.VISIBLE || controlRoot.getAlpha() < 1f) XcFadeTransition.fadeIn(controlRoot);
        updateDiscMenuTools();
        updateCustomButtonVisibility();
        if (mOsd != null) mOsd.setControlsVisible(true);
        checkFullscreenImg();
        checkDanmakuImg();
        setR1Callback();
        updateTopProgress();
    }

    private void hideControl() {
        XcFadeTransition.fadeOutThenGone(mBinding.control.getRoot());
        updateDiscMenuTools();
        updateCustomButtonVisibility();
        if (mOsd != null) mOsd.setControlsVisible(false);
        App.removeCallbacks(mR1);
        updateTopProgress();
    }

    private void onOsdDiagnostics() {
        if (mOsd == null) return;
        mOsd.toggleDiagnostics();
        hideControl();
    }

    private void onPlayParams() {
        if (mOsd == null) return;
        boolean visible = !mOsd.isDiagnosticsVisible();
        PlayerSetting.putOsdDiagnostics(visible);
        mOsd.setDiagnosticsVisible(visible);
        hideControl();
    }

    private void hideWidgetOverlay() {
        mBinding.widget.seek.setVisibility(View.GONE);
        mBinding.widget.speed.clearAnimation();
        mBinding.widget.speed.setVisibility(View.GONE);
        mBinding.widget.bright.setVisibility(View.GONE);
        mBinding.widget.volume.setVisibility(View.GONE);
    }

    private void hideSheet() {
        getSupportFragmentManager().getFragments().stream().filter(fragment -> fragment instanceof BottomSheetDialogFragment).map(fragment -> (BottomSheetDialogFragment) fragment).forEach(BottomSheetDialogFragment::dismiss);
    }

    private void setTraffic() {
        Traffic.setSpeed(mBinding.progress.traffic);
        App.post(mR2, 1000);
    }

    private void setOrient() {
        if (isPort() && isAutoRotate()) setRequestedOrientation(PlaybackOrientation.getPortAutoRotateOrientation());
        if (isLand() && isAutoRotate()) setRequestedOrientation(PlaybackOrientation.getLandAutoRotateOrientation());
    }

    private void setR1Callback() {
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void setArtwork(String url) {
        if (mHistory != null) mHistory.setVodPic(url);
        loadArtwork(url, mPlaybackEpisodeKey);
        setContextWall(getContextWall());
    }

    private void setArtwork() {
        if (mHistory == null) return;
        setArtwork(mHistory.getVodPic());
    }

    private void loadArtwork(String url) {
        loadArtwork(url, mPlaybackEpisodeKey);
    }

    private void loadArtwork(String url, String owner) {
        String requestUrl = Objects.toString(url, "");
        String requestOwner = Objects.toString(owner, "");
        mArtworkRequestUrl = requestUrl;
        mArtworkRequestOwner = requestOwner;
        if (TextUtils.isEmpty(requestUrl)) {
            mBinding.exo.setDefaultArtwork(null);
            mBinding.audioCover.setImageResource(R.drawable.artwork);
            updateAudioArtworkColor(null);
            return;
        }
        mBinding.audioCover.setImageResource(R.drawable.artwork);
        ImgUtil.load(this, requestUrl, new CustomTarget<>() {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                if (isFinishing() || isDestroyed()) return;
                if (!isCurrentArtworkRequest(requestUrl, requestOwner)) return;
                mBinding.exo.setDefaultArtwork(resource);
                ImgUtil.setWithTransition(mBinding.audioCover, resource, transition);
                updateAudioArtworkColor(resource);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                if (isFinishing() || isDestroyed()) return;
                if (!isCurrentArtworkRequest(requestUrl, requestOwner)) return;
                mBinding.exo.setDefaultArtwork(errorDrawable);
                if (errorDrawable == null) mBinding.audioCover.setImageResource(R.drawable.artwork);
                else mBinding.audioCover.setImageDrawable(errorDrawable);
                updateAudioArtworkColor(errorDrawable);
            }
        });
    }

    private boolean isCurrentArtworkRequest(String url, String owner) {
        return TextUtils.equals(mArtworkRequestUrl, url) && TextUtils.equals(mArtworkRequestOwner, owner);
    }

    private String getContextWall() {
        if (!TextUtils.isEmpty(getWallPic())) return getWallPic();
        return mHistory == null ? "" : mHistory.getWallPic();
    }

    private String lockContextWall(String url) {
        String wall = Objects.toString(url, "");
        if (mContextWallLockedUrl == null && !TextUtils.isEmpty(wall)) mContextWallLockedUrl = wall;
        return mContextWallLockedUrl == null ? wall : mContextWallLockedUrl;
    }

    private void setContextWall(String url) {
        if (!Setting.isPlaybackArtworkWall()) {
            mContextWallUrl = "";
            hideContextWall();
            return;
        }
        String wall = lockContextWall(url);
        if (TextUtils.isEmpty(wall)) {
            mContextWallUrl = "";
            hideContextWall();
            return;
        }
        if (Objects.equals(mContextWallUrl, wall)) return;
        mContextWallUrl = wall;
        resetContextWallAlpha();
        if (isGone(mBinding.contextWall)) {
            mBinding.contextWall.setBackgroundColor(0xFF000000);
            mBinding.contextWall.setVisibility(View.VISIBLE);
        }
        ImgUtil.load(this, wall, new CustomTarget<>() {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                if (!Objects.equals(mContextWallUrl, wall)) return;
                resetContextWallAlpha();
                mBinding.contextWall.setBackgroundColor(0x00000000);
                ImgUtil.setWithTransition(mBinding.contextWall, resource, transition);
                mBinding.contextWall.setVisibility(View.VISIBLE);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                if (!Objects.equals(mContextWallUrl, wall)) return;
                mContextWallUrl = "";
                hideContextWall();
            }
        });
    }

    private void resetContextWallAlpha() {
        mBinding.contextWall.animate().cancel();
        mBinding.contextWall.setAlpha(1f);
    }

    private void restoreContextWall() {
        // 详情页：不铺海报墙，露全局星辰壁纸（窗口底）；起播后走原逻辑
        if (mDetailWallOff) {
            hideContextWall();
            return;
        }
        if (!Setting.isPlaybackArtworkWall()) return;
        String wall = getContextWall();
        if (TextUtils.isEmpty(wall)) {
            hideContextWall();
        } else if (Objects.equals(mContextWallUrl, wall) && mBinding.contextWall.getDrawable() != null) {
            resetContextWallAlpha();
            mBinding.contextWall.setBackgroundColor(Color.TRANSPARENT);
            mBinding.contextWall.setVisibility(View.VISIBLE);
        } else {
            mContextWallUrl = "";
            setContextWall(wall);
        }
    }

    private void hideContextWall() {
        resetContextWallAlpha();
        mBinding.contextWall.setImageDrawable(null);
        mBinding.contextWall.setBackgroundColor(0x00000000);
        mBinding.contextWall.setVisibility(View.GONE);
    }

    private void checkFlag(Vod item) {
        boolean empty = item.getFlags().isEmpty();
        mBinding.flag.setVisibility(empty ? View.GONE : View.VISIBLE);
        boolean preservePlayback = mRestoringConfigurationPlayback && service() != null && !player().isEmpty();
        if (empty) {
            if (!preservePlayback) startFlow();
        } else if (preservePlayback) {
            restoreFlagSelectionWithoutPlayback();
        } else {
            onItemClick(mHistory.getFlag());
            if (mHistory.isRevSort()) reverseEpisode(true);
        }
        if (preservePlayback) SpiderDebug.log("karaoke-result", "configuration restore preserved playback key=%s episode=%s", player().getKey(), mHistory.getVodRemarks());
        mRestoringConfigurationPlayback = false;
    }

    private void restoreFlagSelectionWithoutPlayback() {
        mFlagAdapter.setSelected(mHistory.getFlag());
        Flag flag = getFlag();
        if (flag == null) return;
        syncSelectedEpisode(flag);
        setEpisodeAdapter(flag.getEpisodes());
        scrollEpisodeToSelected();
        setQualityVisible(false);
        if (mHistory.isRevSort()) reverseEpisode(true);
    }

    /**
     * checkHistory 里不碰播放器的部分：阶段一（纯 UI）可执行。
     * 原 checkHistory 唯一调用方已改为两阶段拆分，此处保留拆分后的两半。
     */
    private void checkHistoryUi(Vod item) {
        mHistory = History.find(getHistoryKey());
        mHistory = mHistory == null ? createHistory(item) : mHistory;
        if (!TextUtils.isEmpty(getWallPic())) mHistory.setWallPic(getWallPic());
        if (!TextUtils.isEmpty(getMark())) mHistory.setVodRemarks(getMark());
        if (Setting.isIncognito() && mHistory.getKey().equals(getHistoryKey())) mHistory.delete();
        refreshOpeningEnding();
        mHistory.setVodName(item.getName());
        PlaybackEventCollector.get().updateHistory(mHistory);
        setArtwork(getInitialArtwork(item));
        setScale(getScale());
    }

    /** checkHistory 里必须碰播放器的部分：阶段二（服务连上后）执行。 */
    private void checkHistoryPlayer(Vod item) {
        mBinding.control.action.speed.setText(player().setSpeed(PlayerSetting.getDefaultSpeed()));
        mHistory.setSpeed(player().getSpeed());
        preparseHistoryEpisode();
    }

    /**
     * 首集启动加速：历史集并行预解析。
     * checkHistory 拿到历史选集后，后台提前跑 playerContent 解析，
     * 与 checkFlag/seamless 的 UI 流程并行，命中时 getPlayer 可跳过解析链。
     * Exo 与 MPV 双内核通用：playerContent 三参版按 PlayerSetting.getPlayer()
     * 取当前引擎做提取，解析结果引擎对版；消费侧 consumePreparsed /
     * pollInflightPreparse 本来就不分引擎。雷磁类仍按既有规则排除
     * （在途等待不复用，真机实证预解析内 Source.stop 会杀死在播引擎）。
     */
    private void preparseHistoryEpisode() {
        mPreparsedHistoryResult = null;
        mPreparsedHistorySpec = null;
        mPreparsedHistoryFuture = null;
        String episodeUrl = mHistory.getEpisodeUrl();
        Flag historyFlag = mHistory.getFlag();
        if (TextUtils.isEmpty(episodeUrl) || historyFlag == null) return;
        String flag = historyFlag.getFlag();
        if (TextUtils.isEmpty(flag)) return;
        String spec = getKey() + "|" + flag + "|" + episodeUrl;
        mPreparsedHistorySpec = spec;
        mPreparsedHistoryFuture = Task.executor().submit(() -> {
            Result result = SiteApi.playerContentParseOnly(getKey(), flag, episodeUrl);
            if (spec.equals(mPreparsedHistorySpec)) {
                mPreparsedHistoryResult = result;
            }
            return result;
        });
    }

    private boolean shouldKeepPushArtwork() {
        return SiteApi.PUSH.equals(getKey()) && !TextUtils.isEmpty(getPic());
    }

    private String getInitialArtwork(Vod item) {
        return shouldKeepPushArtwork() ? getPic() : item.getPic();
    }

    private void applySearchArtwork(Vod item) {
        String pic = getSearchArtworkPic();
        if (!TextUtils.isEmpty(pic)) item.setPic(pic);
    }

    private String getSearchArtworkPic() {
        if (!TextUtils.isEmpty(getPic())) return getPic();
        if (mHistory != null && !TextUtils.isEmpty(mHistory.getVodPic())) return mHistory.getVodPic();
        return "";
    }

    private boolean hasInitialPreview() {
        return !getName().isEmpty() || !getPic().isEmpty() || !getWallPic().isEmpty();
    }

    /** 详情页不铺海报背景：showInitialPreview/setDetail 置 true，getPlayer（起播）清掉。 */
    private boolean mDetailWallOff;

    private void showInitialPreview() {
        mBinding.progressLayout.showContent();
        if (!TextUtils.equals(mBinding.name.getText(), getName())) mBinding.name.setText(getName());
        setText(mBinding.content, R.string.detail_content, getContent());
        updateContentFold();
        setDetailLyrics(getContent());
        // 详情页背景：用全局星辰壁纸（窗口底），不再拿海报/剧照当整页背景（共用页，所有源一致）
        mDetailWallOff = true;
        hideContextWall();
        if (!getPic().isEmpty()) {
            if (mHistory != null) mHistory.setVodPic(getPic());
            loadArtwork(getPic(), mPlaybackEpisodeKey);
        }
    }

    private History createHistory(Vod item) {
        History history = new History();
        history.setKey(getHistoryKey());
        history.setCid(VodConfig.getCid());
        history.setVodName(item.getName());
        history.setVodPic(getInitialArtwork(item));
        history.setWallPic(getWallPic());
        history.findEpisode(item.getFlags());
        return history;
    }

    private void saveHistory() {
        saveHistory(false);
    }

    private void saveHistory(boolean exit) {
        if (mHistory == null || Setting.isIncognito()) return;
        if (service() != null && isOwner()) {
            updatePlaybackHistoryPosition();
            mHistory.setCreateTime(System.currentTimeMillis());
        }
        if (exit && service() != null) PlaybackEventCollector.get().onStop(player());
        if (!canSavePlaybackHistory(mHistory)) return;
        History history = mHistory.copy();
        Task.execute(() -> {
            if (history.getDuration() > 0) history.merge().save();
            else history.save();
            if (exit) RefreshEvent.history();
        });
    }

    private void syncHistory() {
        if (mHistory == null || Setting.isIncognito()) return;
        History history = mHistory.copy();
        Task.execute(history::save);
    }

    private void updateHistory(Episode item) {
        Flag flag = getFlag();
        String vodFlag = flag == null ? "" : flag.getFlag();
        boolean sameEpisode = item.matchesName(mHistory.getEpisode());
        boolean sameFlag = TextUtils.equals(mHistory.getVodFlag(), vodFlag);
        if ((!sameEpisode || !sameFlag) && service() != null) {
            updatePlaybackHistoryPosition();
            PlaybackEventCollector.get().onStop(player());
        }
        mHistory.setPosition(sameEpisode ? mHistory.getPosition() : C.TIME_UNSET);
        if (!sameEpisode) mHistory.setDuration(C.TIME_UNSET);
        mHistory.setVodFlag(vodFlag);
        mHistory.setVodRemarks(item.getName());
        mHistory.setEpisodeUrl(item.getUrl());
        PlaybackEventCollector.get().updateHistory(mHistory);
    }

    private void checkControl() {
        if (isVisible(mBinding.control.getRoot())) showControl();
    }

    private void checkKeepImg() {
        // 海阔交接详情：星标态读海阔收藏（原版 ViewCollection 语义），不读主 App Keep
        boolean kept = HkDetailBridge.isHkKey(getKey())
                ? HkFavoriteStore.isFavorite(hkRuleTitle(), getId())
                : Keep.find(getHistoryKey()) != null;
        mBinding.control.keep.setImageResource(kept ? R.drawable.ic_player_star_on : R.drawable.ic_player_star);
        mBinding.audioKeepAction.setSelected(kept);
        if (mBinding.keepBtn != null) mBinding.keepBtn.setIconResource(kept ? R.drawable.ic_control_keep_on : R.drawable.ic_control_keep_off);
    }

    private void checkLockImg() {
        mBinding.control.right.lock.setImageResource(isLock() ? R.drawable.ic_player_lock_on : R.drawable.ic_player_lock);
    }

    private void checkFullscreenImg() {
        mBinding.control.fullscreen.setImageResource(isFullscreen() ? R.drawable.ic_player_full_exit : R.drawable.ic_player_full);
    }

    private void checkDanmakuImg() {
        mBinding.control.danmaku.setImageResource(DanmakuSetting.isShow() ? R.drawable.ic_control_danmaku_on : R.drawable.ic_control_danmaku_off);
    }

    private void createKeep() {
        // 海阔交接详情不写主 App Keep（走海阔收藏），防御性拦截
        if (HkDetailBridge.isHkKey(getKey())) return;
        Keep keep = new Keep();
        keep.setKey(getHistoryKey());
        keep.setCid(VodConfig.getCid());
        keep.setVodPic(mHistory.getVodPic());
        keep.setVodName(mHistory.getVodName());
        keep.setSiteName(getSite().getName());
        keep.setCreateTime(System.currentTimeMillis());
        keep.save();
    }

    private void updateKeep() {
        Keep keep = Keep.find(getHistoryKey());
        if (keep != null) {
            keep.setVodName(mHistory.getVodName());
            keep.setVodPic(mHistory.getVodPic());
            keep.save();
        }
    }

    private void updateVod(Vod item) {
        boolean id = !item.getId().isEmpty();
        boolean pic = !item.getPic().isEmpty();
        boolean name = !item.getName().isEmpty();
        if (id) getIntent().putExtra("id", item.getId());
        if (id) mHistory.replace(getHistoryKey());
        if (name) mHistory.setVodName(item.getName());
        if (name && !TextUtils.equals(mBinding.name.getText(), item.getName())) mBinding.name.setText(item.getName());
        if (name) mBinding.control.title.setText(getPlaybackControlTitle());
        if (name) updateAudioStageText();
        updateFlag(getFlag(), item.getFlags());
        if (pic) setArtwork(item.getPic());
        if (pic || name) setMetadata();
        if (pic || name) syncHistory();
        if (pic || name) updateKeep();
        if (id) updateNavigationKey();
        PlaybackEventCollector.get().updateHistory(mHistory);
        setText(item);
    }

    private void updateFlag(Flag activated, List<Flag> items) {
        items.forEach(item -> mFlagAdapter.getItems().stream()
                .filter(item::equals).findFirst().ifPresentOrElse(target -> {
                    target.mergeEpisodes(item.getEpisodes(), mHistory.isRevSort());
                    if (target.equals(activated)) setEpisodeAdapter(target.getEpisodes());
                }, () -> mFlagAdapter.add(item)));
    }

    private final PlaybackService.NavigationCallback mNavigationCallback = new PlaybackService.NavigationCallback() {
        @Override
        public void onNext() {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("audio-auto-next", "activity onNext audioStage=%s episode=%s", mAudioStageVisible, getEpisode() == null ? "null" : getEpisode().getName());
            if (!mAudioStageVisible || !playNextAudioPlaylistEntry()) checkNext();
        }

        @Override
        public void onPrev() {
            checkPrev();
        }

        @Override
        public void onStop() {
            finish();
        }

        @Override
        public void onReplay() {
            VideoActivity.this.onReplay();
        }

        @Override
        public void onAudio() {
            setAudioOnly(true);
            syncPiPForPlaybackMode();
            Util.moveToBackground(VideoActivity.this);
        }
    };

    @Override
    protected String getPlaybackKey() {
        return getHistoryKey();
    }

    @Override
    protected void onPrepare() {
        setDecode();
        setLut();
        setPosition();
        refreshLyrics();
    }

    @Override
    protected void onTracksChanged() {
        updateAudioOnlyState();
        syncPiPForPlaybackMode();
        refreshLyrics();
        setTrackVisible();
        mClock.setCallback(this);
        updateTopProgress();
    }

    private void updateAudioOnlyState() {
        if (service() == null) return;
        setAudioOnly(LyricsController.isAudioOnly(player()));
        syncDesktopLyricsAudioContent();
        setAudioStageVisible(shouldUseImmersiveAudio());
        setKaraokeActionState();
    }

    private void syncDesktopLyricsAudioContent() {
        if (service() != null) service().setDesktopLyricsAudioContent(isAudioOnly() || isMusicLike());
    }

    private void setAudioStageVisible(boolean visible) {
        boolean immersiveEnabled = PlayerSetting.isImmersiveAudioMode();
        visible = visible && immersiveEnabled;
        if (visible) ensureImmersiveAudioControllers();
        if (visible && isAutoRotate() && !isLock() && !isRotate()) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        if (mAudioStageVisible == visible) {
            syncPiPForPlaybackMode();
            updateAudioStageText();
            updateAudioStageControls();
            return;
        }
        mAudioStageVisible = visible;
        syncPiPForPlaybackMode();
        if (!visible) mAudioLightEffectAnimated = false;
        mBinding.audioStage.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) mBinding.audioStage.bringToFront();
        if (visible) applyAudioBackground();
        mBinding.lyrics.setSuppressed(!immersiveEnabled || visible);
        mBinding.audioLyrics.setSuppressed(!visible);
        syncKaraokeStageVisibility();
        applyAudioStageLayout(visible);
        applyAudioPageMode(visible);
        updateAudioStageText();
        updateAudioStageControls();
    }

    private void syncKaraokeStageVisibility() {
        if (mBinding == null) return;
        if (!PlayerSetting.isImmersiveAudioMode()) {
            if (mBinding.karaoke != null) mBinding.karaoke.setVisibility(View.GONE);
            mBinding.audioKaraoke.setSpectrumMode(false);
            mBinding.audioKaraoke.setVisibility(View.GONE);
            return;
        }
        if (mAudioStageVisible) {
            if (mBinding.karaoke != null) mBinding.karaoke.setVisibility(View.GONE);
            boolean karaokeMode = PlayerSetting.isKaraokeMode();
            mBinding.audioKaraoke.setSpectrumMode(!karaokeMode);
            if (karaokeMode && mBinding.audioKaraoke.getVisibility() == View.GONE) mBinding.audioKaraoke.setVisibility(View.INVISIBLE);
        } else {
            mBinding.audioKaraoke.setSpectrumMode(false);
            mBinding.audioKaraoke.setVisibility(View.GONE);
        }
    }

    private void applyAudioStageLayout(boolean visible) {
        if (isFullscreen() || isInPictureInPictureMode()) return;
        if (!visible) {
            if (mFrameHeight > 0) mFrameParams.height = mFrameHeight;
        } else if (isPort()) {
            mFrameParams.height = 0;
        } else {
            mFrameParams.height = mFrameHeight;
        }
        mBinding.video.setLayoutParams(mFrameParams);
    }

    private void applyAudioPageMode(boolean visible) {
        if (mBinding.videoShadow != null) mBinding.videoShadow.setVisibility(visible ? View.GONE : View.VISIBLE);
        mBinding.name.setVisibility(visible ? View.GONE : View.VISIBLE);
        mBinding.content.setVisibility(visible ? View.GONE : mBinding.content.getText().length() == 0 ? View.GONE : View.VISIBLE);
        mBinding.meta.setVisibility(visible ? View.GONE : mBinding.meta.getText().length() == 0 ? View.GONE : View.VISIBLE);
        mBinding.buttonRow.setVisibility(visible ? View.GONE : View.VISIBLE);
        mBinding.flag.setVisibility(visible || mFlagAdapter == null || mFlagAdapter.isEmpty() ? View.GONE : View.VISIBLE);
        boolean qualityVisible = mQualityAdapter != null && mQualityAdapter.getItemCount() > 1;
        boolean episodeGroupVisible = mEpisodeGroupAdapter != null && mEpisodeGroupAdapter.getItemCount() > 1;
        boolean episodeVisible = mEpisodeAdapter != null && mEpisodeAdapter.getItemCount() > 0;
        boolean quickVisible = mQuickAdapter != null && mQuickAdapter.getItemCount() > 0;
        mBinding.qualityText.setVisibility(visible || !qualityVisible ? View.GONE : View.VISIBLE);
        mBinding.quality.setVisibility(visible || !qualityVisible ? View.GONE : View.VISIBLE);
        mBinding.episodeGroup.setVisibility(visible || !episodeGroupVisible ? View.GONE : View.VISIBLE);
        mBinding.episode.setVisibility(visible || !episodeVisible ? View.GONE : View.VISIBLE);
        mBinding.quick.setVisibility(visible || !quickVisible ? View.GONE : View.VISIBLE);
    }

    private void updateAudioStageText() {
        if (mBinding == null) return;
        String title = getAudioStageTitle();
        String subtitle = getAudioStageArtist(title);
        mBinding.audioTitle.setText(TextUtils.isEmpty(title) ? getString(R.string.player_audio_badge_audio) : title);
        mBinding.audioSubtitle.setText(subtitle);
        mBinding.audioSubtitle.setVisibility(TextUtils.isEmpty(subtitle) ? View.GONE : View.VISIBLE);
        mBinding.audioBadgeLyrics.setText(PlayerSetting.isKaraokeMode() ? getString(R.string.player_karaoke_mode) : getString(R.string.player_audio_badge_lyrics));
    }

    private void updateAudioStageControls() {
        if (mBinding == null) return;
        if (mAudioStageVisible) applyAudioPageMode(true);
        boolean hasPrev = hasAdjacentEpisode(-1);
        boolean hasNext = hasAdjacentEpisode(1);
        mBinding.audioPrev.setEnabled(hasPrev);
        mBinding.audioPrev.setAlpha(hasPrev ? 1f : 0.35f);
        mBinding.audioNext.setEnabled(hasNext);
        mBinding.audioNext.setAlpha(hasNext ? 1f : 0.35f);
        mBinding.audioQueueAction.setEnabled(true);
        mBinding.audioQueueAction.setAlpha(1f);
        setAudioRepeatSelected(service() != null && player().isRepeatOne());
        mBinding.audioKaraokeAction.setSelected(PlayerSetting.isKaraokeMode());
        mBinding.audioKeepAction.setSelected(Keep.find(getHistoryKey()) != null);
        checkAudioPlayImg(service() != null && player().isPlaying());
        syncAudioCoverRotation();
    }

    private void applyAudioBackground() {
        if (mBinding == null) return;
        mAudioLightEffectAnimated = service() != null && player().isPlaying();
        AudioPlayerBackgroundDrawable drawable = new AudioPlayerBackgroundDrawable(PlayerSetting.getAudioBackground(), mAudioArtworkColor, PlayerSetting.isAudioBackgroundDecorated(), PlayerSetting.isAudioBackgroundLightEffect(), mAudioLightEffectAnimated, PlayerSetting.getAudioBackgroundSeed(), PlayerSetting.getAudioBackgroundDecorationSeed());
        syncAudioBackgroundHalo(drawable);
        mBinding.audioStage.setBackground(drawable);
        scheduleAudioBackgroundHaloSync(drawable);
        mBinding.audioStage.invalidate();
    }

    private void scheduleAudioBackgroundHaloSync(AudioPlayerBackgroundDrawable drawable) {
        mBinding.audioStage.post(() -> syncAudioBackgroundHalo(drawable));
        mBinding.audioStage.postDelayed(() -> syncAudioBackgroundHalo(drawable), 120);
        mBinding.audioStage.postDelayed(() -> syncAudioBackgroundHalo(drawable), 360);
    }

    private void syncAudioBackgroundHalo(AudioPlayerBackgroundDrawable drawable) {
        if (mBinding == null || drawable == null) return;
        View anchor = mBinding.audioCover != null ? mBinding.audioCover : mBinding.audioDisc;
        if (anchor == null || mBinding.audioStage.getWidth() <= 0 || anchor.getWidth() <= 0 || anchor.getHeight() <= 0) return;
        if (mBinding.audioStage.getBackground() != drawable) return;
        Rect bounds = new Rect(0, 0, anchor.getWidth(), anchor.getHeight());
        mBinding.audioStage.offsetDescendantRectToMyCoords(anchor, bounds);
        float cx = bounds.exactCenterX();
        float cy = bounds.exactCenterY();
        float radius = Math.max(anchor.getWidth(), anchor.getHeight()) * 0.56f;
        drawable.setRecordHaloAnchor(cx, cy, radius);
    }

    private void updateAudioArtworkColor(@Nullable Drawable drawable) {
        mAudioArtworkColor = extractAudioArtworkColor(drawable);
        if (mAudioStageVisible && PlayerSetting.getAudioBackground() == PlayerSetting.AUDIO_BACKGROUND_ARTWORK) applyAudioBackground();
    }

    private int extractAudioArtworkColor(@Nullable Drawable drawable) {
        if (drawable == null) return Color.rgb(255, 111, 145);
        Bitmap bitmap = null;
        try {
            bitmap = createPaletteBitmap(drawable);
            Palette palette = Palette.from(bitmap).maximumColorCount(8).generate();
            Palette.Swatch swatch = palette.getVibrantSwatch();
            if (swatch == null) swatch = palette.getLightVibrantSwatch();
            if (swatch == null) swatch = palette.getDominantSwatch();
            return swatch == null ? Color.rgb(255, 111, 145) : swatch.getRgb();
        } catch (Exception ignored) {
            return Color.rgb(255, 111, 145);
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    private Bitmap createPaletteBitmap(Drawable drawable) {
        int width = 72;
        int height = 72;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    private void setAudioRepeatSelected(boolean selected) {
        if (mBinding == null) return;
        mBinding.audioRepeatAction.setSelected(selected);
        mBinding.audioRepeatAction.setAlpha(selected ? 1f : 0.62f);
    }

    private void syncAudioCoverRotation() {
        if (!mAudioStageVisible || service() == null || !player().isPlaying()) {
            stopAudioCoverRotation();
            return;
        }
        if (mAudioCoverAnimator == null) {
            mAudioCoverAnimator = ObjectAnimator.ofFloat(mBinding.audioCover, View.ROTATION, mBinding.audioCover.getRotation(), mBinding.audioCover.getRotation() + 360f);
            mAudioCoverAnimator.setDuration(20000);
            mAudioCoverAnimator.setInterpolator(new LinearInterpolator());
            mAudioCoverAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            mAudioCoverAnimator.setRepeatMode(ObjectAnimator.RESTART);
        }
        if (!mAudioCoverAnimator.isStarted()) mAudioCoverAnimator.start();
    }

    private void stopAudioCoverRotation() {
        if (mAudioCoverAnimator == null) return;
        mAudioCoverAnimator.cancel();
        mAudioCoverAnimator = null;
    }

    private String getAudioStageTitle() {
        String currentTrack = getCurrentTrackMetadata();
        if (!TextUtils.isEmpty(currentTrack)) return splitCurrentTrack(currentTrack)[0];
        Episode episode = getEpisode();
        String queuedTitle = mAudioQueueTitles.get(audioQueueEpisodeKey(episode));
        if (!TextUtils.isEmpty(queuedTitle)) return queuedTitle;
        AudioPlaylistStore.Entry entry = findCurrentAudioPlaylistEntry(episode);
        if (entry != null) return getAudioQueueSongTitle(entry.title, entry.name);
        if (isAudioQueueEpisode(episode) && !TextUtils.isEmpty(episode.getDisplayName())) return episode.getDisplayName();
        if (mHistory != null && !TextUtils.isEmpty(mHistory.getVodName())) return mHistory.getVodName();
        if (!TextUtils.isEmpty(getName())) return getName();
        CharSequence text = mBinding.name.getText();
        return text == null ? "" : text.toString();
    }

    private String getAudioStageArtist(String title) {
        String currentTrack = getCurrentTrackMetadata();
        if (!TextUtils.isEmpty(currentTrack)) return splitCurrentTrack(currentTrack)[1];
        Episode item = getEpisode();
        String queuedArtist = mAudioQueueArtists.get(audioQueueEpisodeKey(item));
        if (!TextUtils.isEmpty(queuedArtist)) return queuedArtist;
        AudioPlaylistStore.Entry entry = findCurrentAudioPlaylistEntry(item);
        if (entry != null && !TextUtils.isEmpty(entry.artist)) return entry.artist;
        String episode = item == null ? "" : item.getName();
        String artist = getArtistFromEpisode(title, cleanAudioEpisodeForArtist(episode));
        return TextUtils.equals(artist, title) ? "" : artist;
    }

    private String getCurrentTrackMetadata() {
        if (service() == null || player().getMetadata() == null) return "";
        MediaMetadata metadata = player().getMetadata();
        if (metadata.subtitle != null && !TextUtils.isEmpty(metadata.subtitle.toString().trim())) return metadata.subtitle.toString().trim();
        if (metadata.artist != null && !TextUtils.isEmpty(metadata.artist.toString().trim())) return metadata.artist.toString().trim();
        return "";
    }

    private void syncCurrentAudioPlaylistMetadata() {
        if (!mAudioStageVisible || service() == null) return;
        Episode episode = getPlaybackEpisode();
        if (episode == null) return;
        AudioPlaylistStore.Entry entry = findCurrentAudioPlaylistEntry(episode);
        String track = getCurrentTrackMetadata();
        if (TextUtils.isEmpty(track) || !isCurrentAudioQueueTrack(track, episode)) return;
        String[] parts = splitCurrentTrack(track);
        String title = parts[0];
        String artist = parts[1];
        if (!isUsefulAudioQueueTitle(title, episode)) return;
        String key = audioQueueEpisodeKey(episode);
        boolean changed = !TextUtils.equals(title, mAudioQueueTitles.get(key));
        if (!TextUtils.isEmpty(artist) && !TextUtils.equals(artist, mAudioQueueArtists.get(key))) changed = true;
        if (!changed) return;
        mAudioQueueTitles.put(key, title);
        if (!TextUtils.isEmpty(artist)) mAudioQueueArtists.put(key, artist);
        AudioPlaylistStore.putMetadata(episode.getUrl(), title, artist);
        if (entry != null) {
            entry.title = title;
            if (!TextUtils.isEmpty(artist)) entry.artist = artist;
            AudioPlaylistStore.upsertItem(entry);
        }
        if (mAudioQueueAdapter != null) mAudioQueueAdapter.notifyDataSetChanged();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("audio-playlist", "metadata learned name=%s title=%s artist=%s", episode.getName(), title, artist);
    }

    private String[] splitCurrentTrack(String value) {
        String text = Objects.toString(value, "").replaceFirst("^\\s*\\d+[.、\\s]+", "").trim();
        for (String separator : new String[]{" - ", " – ", " — "}) {
            int index = text.lastIndexOf(separator);
            if (index > 0 && index + separator.length() < text.length()) {
                return new String[]{text.substring(0, index).trim(), text.substring(index + separator.length()).trim()};
            }
        }
        return new String[]{text, ""};
    }

    private String getAudioQueueDisplayName(Episode episode, boolean active) {
        String fallback = episode == null ? "" : episode.getDisplayName();
        if (!isAudioQueuePlaceholderName(fallback)) return fallback;
        String title = mAudioQueueTitles.get(audioQueueEpisodeKey(episode));
        String artist = mAudioQueueArtists.get(audioQueueEpisodeKey(episode));
        if (active) {
            String track = getCurrentTrackMetadata();
            if (!TextUtils.isEmpty(track) && isCurrentAudioQueueTrack(track, episode)) {
                String[] parts = splitCurrentTrack(track);
                if (isUsefulAudioQueueTitle(parts[0], episode)) {
                    title = parts[0];
                    artist = parts[1];
                }
            }
        }
        if (!isUsefulAudioQueueTitle(title, episode)) return fallback;
        if (TextUtils.isEmpty(artist) || title.contains(artist)) return title;
        return title + " - " + artist;
    }

    private boolean isUsefulAudioQueueTitle(String title, Episode episode) {
        String value = Objects.toString(title, "").trim();
        if (TextUtils.isEmpty(value) || isAudioQueuePlaceholderName(value)) return false;
        if (episode == null || !isAudioQueuePlaceholderName(episode.getDisplayName())) return true;
        String collection = mHistory == null ? "" : Objects.toString(mHistory.getVodName(), "").trim();
        if (!TextUtils.isEmpty(collection) && TextUtils.equals(value, collection)) return false;
        if (!TextUtils.isEmpty(getName()) && TextUtils.equals(value, getName())) return false;
        return !isAudioQueueCollectionTitle(value);
    }

    private boolean isAudioQueuePlaceholderName(String name) {
        String value = Objects.toString(name, "").trim();
        return value.matches("(?i)^(?:\\[?p\\s*0*\\d{1,4}\\]?|0*\\d{1,4})[.．、_\\-\\s]*$");
    }

    private boolean isCurrentAudioQueueTrack(String track, Episode episode) {
        String episodeNumber = audioQueueTrackNumber(episode == null ? "" : episode.getDisplayName(), true);
        if (TextUtils.isEmpty(episodeNumber)) return true;
        String trackNumber = audioQueueTrackNumber(track, false);
        return TextUtils.isEmpty(trackNumber) || TextUtils.equals(trackNumber, episodeNumber);
    }

    private String audioQueueTrackNumber(String text, boolean placeholder) {
        String value = Objects.toString(text, "").trim();
        String number;
        if (placeholder) {
            if (!isAudioQueuePlaceholderName(value)) return "";
            number = value.replaceAll("\\D", "");
        } else {
            number = value.replaceFirst("^\\s*(\\d{1,4})[.．、\\s]+.*$", "$1");
            if (TextUtils.equals(number, value)) return "";
        }
        number = number.replaceFirst("^0+(?!$)", "");
        return number;
    }

    private boolean isAudioQueueCollectionTitle(String title) {
        String value = Objects.toString(title, "").trim().toLowerCase(Locale.ROOT);
        return value.contains("合集")
                || value.contains("歌单")
                || value.contains("排行榜")
                || value.contains("热门歌曲")
                || value.contains("最好听")
                || value.contains("精选")
                || value.matches(".*\\d+\\s*(首|曲).*");
    }

    private AudioPlaylistStore.Entry findCurrentAudioPlaylistEntry(Episode episode) {
        String url = episode == null ? "" : Objects.toString(episode.getUrl(), "");
        if (TextUtils.isEmpty(url)) return null;
        AudioPlaylistStore.Playlist playlist = AudioPlaylistStore.active();
        if (playlist == null || playlist.items == null) return null;
        for (AudioPlaylistStore.Entry entry : playlist.items) {
            if (entry != null && TextUtils.equals(entry.url, url)) return entry;
        }
        return null;
    }

    private String getEpisodeArtwork(Episode episode) {
        String queuedPic = mAudioQueuePics.get(audioQueueEpisodeKey(episode));
        if (!TextUtils.isEmpty(queuedPic)) return queuedPic;
        return mHistory == null ? "" : mHistory.getVodPic();
    }

    private Episode getPlaybackEpisode() {
        String key = Objects.toString(mPlaybackEpisodeKey, "");
        Flag flag = getFlag();
        if (TextUtils.isEmpty(key) || flag == null) return getEpisode();
        for (Episode episode : flag.getEpisodes()) {
            if (TextUtils.equals(audioQueueEpisodeKey(episode), key)) return episode;
        }
        return getEpisode();
    }

    private String getEpisodeInlineLyrics(Episode episode) {
        if (isAudioQueueEpisode(episode)) return Objects.toString(mAudioQueueLyrics.get(audioQueueEpisodeKey(episode)), "");
        return mDetailLyrics;
    }

    private void applyPlaybackArtwork(Episode episode) {
        loadArtwork(getEpisodeArtwork(episode), audioQueueEpisodeKey(episode));
    }

    private void restorePlaybackArtwork() {
        Episode episode = getPlaybackEpisode();
        String owner = audioQueueEpisodeKey(episode);
        String url = getEpisodeArtwork(episode);
        if (TextUtils.isEmpty(url) && TextUtils.equals(owner, mArtworkRequestOwner)) url = mArtworkRequestUrl;
        SpiderDebug.log("audio-artwork", "restore owner=%s url=%s requestOwner=%s request=%s", owner, !TextUtils.isEmpty(url), mArtworkRequestOwner, !TextUtils.isEmpty(mArtworkRequestUrl));
        loadArtwork(url, owner);
    }

    private String cleanAudioEpisodeForArtist(String episode) {
        String value = Objects.toString(episode, "").trim();
        if (value.isEmpty()) return "";
        String[] parts = value.split("[|｜]");
        return parts.length == 0 ? value : parts[parts.length - 1].trim();
    }

    private void refreshLyrics() {
        if (mLyrics == null || service() == null) return;
        debugLyricsLoop("refreshLyrics", true);
        updateAudioOnlyState();
        boolean audioContent = isAudioOnly() || isMusicLike();
        if (showInlineLyrics()) {
            refreshKaraoke(audioContent);
            return;
        }
        mLyrics.refresh(player(), audioContent);
        refreshKaraoke(audioContent);
    }

    private void refreshKaraoke(boolean audioContent) {
        if (mKaraoke == null || service() == null) return;
        boolean loadTrack = !mSkipKaraokeTrackAutoLoad;
        mKaraoke.refresh(this, player(), audioContent, loadTrack);
    }

    private void debugPlaybackControl(String event) {
        if (!SpiderDebug.isEnabled()) return;
        if (service() == null || player().isEmpty()) {
            SpiderDebug.log("playback-control", "video.%s noPlayer owner=%s service=%s", event, isOwner(), service() != null);
            return;
        }
        SpiderDebug.log("playback-control", "video.%s pos=%d dur=%d state=%d playing=%s playWhenReady=%s repeat=%s owner=%s audioStage=%s controller=%s",
                event, player().getPosition(), player().getDuration(), player().getPlaybackState(), player().isPlaying(), player().getPlayer().getPlayWhenReady(), player().isRepeatOne(), isOwner(), mAudioStageVisible, controller() != null);
    }

    private void debugLyricsLoop(String event, boolean force) {
        if (!SpiderDebug.isEnabled()) return;
        if (service() == null || player().isEmpty()) {
            if (force) SpiderDebug.log("lyrics-loop", "video.%s noPlayer owner=%s service=%s", event, isOwner(), service() != null);
            return;
        }
        long position = Math.max(0, player().getPosition());
        long duration = player().getDuration();
        boolean playing = player().isPlaying();
        boolean nearStart = position <= 5000;
        boolean nearEnd = duration > 0 && duration - position <= 5000;
        boolean backward = mLyricsLoopLastPlayerPosition != C.TIME_UNSET && position + 1200 < mLyricsLoopLastPlayerPosition;
        boolean playingChanged = playing != mLyricsLoopLastPlaying;
        if (force || nearStart || nearEnd || backward || playingChanged) {
            SpiderDebug.log("lyrics-loop", "video.%s pos=%d last=%d dur=%d state=%d playing=%s playWhenReady=%s repeat=%s nearStart=%s nearEnd=%s backward=%s lyricsLines=%d main={%s} audio={%s} karaokePos=%d",
                    event, position, mLyricsLoopLastPlayerPosition, duration, player().getPlaybackState(), playing, player().getPlayer().getPlayWhenReady(), player().isRepeatOne(), nearStart, nearEnd, backward,
                    mLyrics == null ? -1 : mLyrics.getLines().size(),
                    mBinding == null || mBinding.lyrics == null ? "null" : mBinding.lyrics.debugState(),
                    mBinding == null || mBinding.audioLyrics == null ? "null" : mBinding.audioLyrics.debugState(),
                    mKaraoke == null || mKaraoke.getSnapshot() == null ? -1 : mKaraoke.getSnapshot().getPositionMs());
        }
        mLyricsLoopLastPlayerPosition = position;
        mLyricsLoopLastPlaying = playing;
    }

    private boolean isLyricsSearchAvailable() {
        if (mLyrics == null || service() == null) return false;
        updateAudioOnlyState();
        return isAudioOnly() || isMusicLike();
    }

    private String getLyricsSearchKeyword() {
        if (service() == null) return getName();
        LyricsRequest request = LyricsRequest.from(player());
        return request.displayKeyword();
    }

    private List<String> getLyricsSearchSuggestions() {
        if (service() == null) return withLastLyricsSearchSuggestion(LyricsRequest.searchSuggestions(getName()), getName());
        LyricsRequest request = LyricsRequest.from(player());
        return withLastLyricsSearchSuggestion(request.searchSuggestions(), request.stableSignature());
    }

    private String getLyricsSearchCacheKey(String keyword) {
        if (service() == null) return keyword;
        return LyricsRequest.from(player()).withKeyword(keyword).signature();
    }

    private void rememberLyricsSearchKeyword(String keyword) {
        String value = Objects.toString(keyword, "").trim();
        if (TextUtils.isEmpty(value)) return;
        mLyricsLastSearchSignature = getLyricsSearchSignature();
        mLyricsLastSearchKeyword = value;
    }

    private String getLyricsSearchSignature() {
        if (service() == null) return getName();
        return LyricsRequest.from(player()).stableSignature();
    }

    private List<String> withLastLyricsSearchSuggestion(List<String> suggestions, String signature) {
        String keyword = Objects.toString(mLyricsLastSearchKeyword, "").trim();
        if (TextUtils.isEmpty(keyword) || !TextUtils.equals(mLyricsLastSearchSignature, signature)) return suggestions;
        List<String> values = new ArrayList<>();
        values.add(keyword);
        for (String suggestion : suggestions) {
            String value = Objects.toString(suggestion, "").trim();
            if (TextUtils.isEmpty(value) || containsLyricsSearchSuggestion(values, value)) continue;
            values.add(value);
            if (values.size() >= 8) break;
        }
        return values;
    }

    private boolean containsLyricsSearchSuggestion(List<String> suggestions, String keyword) {
        for (String suggestion : suggestions) if (suggestion.equalsIgnoreCase(keyword)) return true;
        return false;
    }

    private void searchLyrics(String keyword) {
        if (mLyrics == null || service() == null) return;
        updateAudioOnlyState();
        int seq = ++mLyricsSearchSeq;
        String cacheKey = getLyricsSearchCacheKey(keyword);
        rememberLyricsSearchKeyword(keyword);
        if (TextUtils.equals(mLyricsSearchKeyword, cacheKey) && mLyricsSearchResults != null && !mLyricsSearchResults.isEmpty()) {
            showLyricsResults(seq, cacheKey, mLyricsSearchResults, true);
            return;
        }
        showLyricsSearching(seq);
        mLyrics.search(player(), isAudioOnly() || isMusicLike(), keyword, (results, complete) -> showLyricsResults(seq, cacheKey, results, complete));
    }

    private void showLyricsSearching(int seq) {
        dismissLyricsResultDialog();
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        root.addView(createAudioSheetTitle(getString(R.string.player_lyrics_search)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(34)));
        TextView message = createAudioSheetText(getString(R.string.player_lyrics_searching), 15, false);
        root.addView(message, audioSheetTopParams(14, 44));
        TextView cancel = createAudioSheetButton(getString(R.string.dialog_cancel), false, () -> {
            if (seq == mLyricsSearchSeq) mLyricsSearchSeq++;
            dialog.dismiss();
        });
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.addView(cancel, audioSheetButtonParams(false));
        root.addView(actions, audioSheetTopParams(10, 44));
        dialog.setContentView(root);
        dialog.setOnCancelListener(d -> {
            if (seq == mLyricsSearchSeq) mLyricsSearchSeq++;
        });
        dialog.setOnDismissListener(d -> {
            if (mLyricsResultDialog == dialog) mLyricsResultDialog = null;
        });
        mLyricsResultDialog = dialog;
        showAudioSheet(dialog);
    }

    private void showLyricsResults(int seq, String cacheKey, List<LyricsResult> results, boolean complete) {
        if (seq != mLyricsSearchSeq) return;
        if (isFinishing()) return;
        if (results == null || results.isEmpty()) {
            if (complete) {
                dismissLyricsResultDialog();
                Notify.show(R.string.player_lyrics_not_found);
            }
            return;
        }
        mLyricsSearchResults = results;
        mLyricsSearchKeyword = cacheKey;
        String[] labels = new String[results.size()];
        for (int i = 0; i < results.size(); i++) labels[i] = getLyricsResultLabel(results.get(i));
        if (mLyricsResultDialog != null && mLyricsResultList != null && mLyricsResultDialog.isShowing()) {
            updateLyricsResultList(labels);
            updateLyricsResultSheetHeight(labels.length);
            return;
        }
        dismissLyricsResultDialog();
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        root.addView(createAudioSheetTitle(getString(R.string.player_lyrics_select)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(34)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        mLyricsResultList = new LinearLayout(this);
        mLyricsResultList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(mLyricsResultList, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, lyricsResultSheetHeight(labels.length)));
        dialog.setContentView(root);
        dialog.setOnCancelListener(d -> {
            if (seq == mLyricsSearchSeq) mLyricsSearchSeq++;
        });
        dialog.setOnDismissListener(d -> {
            if (mLyricsResultDialog == dialog) {
                mLyricsResultDialog = null;
                mLyricsResultList = null;
            }
        });
        mLyricsResultDialog = dialog;
        updateLyricsResultList(labels);
        showCompactPlaybackSheet(dialog, false);
    }

    private BottomSheetDialog createAudioSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        // 底部面板进出统一口径：滑上滑下＋淡入淡出 200ms（与 BaseBottomSheetDialog 基类同一套）
        if (dialog.getWindow() != null) dialog.getWindow().setWindowAnimations(R.style.XcBottomSheetSlide);
        return dialog;
    }

    private LinearLayout createAudioSheetRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(ResUtil.dp2px(24), ResUtil.dp2px(10), ResUtil.dp2px(24), ResUtil.dp2px(18) + mEpisodeBottomInset);
        root.setBackground(audioSheetGlassBackground());
        View handle = new View(this);
        handle.setBackground(roundRect(0x55FFFFFF, 2, 0, 0));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(ResUtil.dp2px(38), ResUtil.dp2px(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = ResUtil.dp2px(14);
        root.addView(handle, handleParams);
        if (isLandscapeAudioSheet()) styleAudioDrawerRoot(root);
        return root;
    }

    private boolean isLandscapeAudioSheet() {
        return mAudioStageVisible && ResUtil.isLand(this);
    }

    private void styleAudioDrawerRoot(LinearLayout root) {
        root.setPadding(ResUtil.dp2px(22), ResUtil.dp2px(10), ResUtil.dp2px(22), ResUtil.dp2px(14));
        root.setMinimumHeight(audioDrawerHeight());
        root.setBackground(audioDrawerBackground());
    }

    private TextView createAudioSheetTitle(String text) {
        TextView title = createAudioSheetText(text, 17, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        return title;
    }

    private TextView createAudioSheetText(String text, int sizeSp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(sizeSp);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private TextView createAudioSheetItem(String label, Runnable action) {
        TextView view = createAudioSheetText(label, 15, false);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(ResUtil.dp2px(12), 0, ResUtil.dp2px(12), 0);
        view.setBackground(audioSheetItemBackground(false));
        view.setSingleLine(false);
        view.setMaxLines(2);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private TextView createAudioSheetButton(String label, boolean primary, Runnable action) {
        TextView view = createAudioSheetText(label, 15, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setTextColor(SHEET_TEXT_PRIMARY);
        view.setBackground(roundRect(primary ? SHEET_CONTROL_BG_SELECTED : SHEET_CONTROL_BG, SHEET_BUTTON_RADIUS_DP, 1, primary ? SHEET_CONTROL_STROKE_SELECTED : 0x32FFFFFF));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private TextView createAudioSheetMiniButton(String label, boolean primary, Runnable action) {
        TextView view = createAudioSheetText(label, 13, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setTextColor(SHEET_TEXT_PRIMARY);
        view.setBackground(roundRect(primary ? SHEET_CONTROL_BG_SELECTED : SHEET_CONTROL_BG, SHEET_BUTTON_RADIUS_DP, 1, primary ? SHEET_CONTROL_STROKE_SELECTED : SHEET_CONTROL_STROKE));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private ImageView createAudioSheetIconButton(int resId, Runnable action) {
        ImageView view = new ImageView(this);
        view.setImageResource(resId);
        view.setColorFilter(SHEET_TEXT_SECONDARY);
        view.setPadding(ResUtil.dp2px(12), ResUtil.dp2px(12), ResUtil.dp2px(12), ResUtil.dp2px(12));
        view.setBackground(roundRect(0x16FFFFFF, SHEET_BUTTON_RADIUS_DP, 1, 0x32FFFFFF));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private ImageView createAudioSheetInlineIconButton(int resId, Runnable action) {
        ImageView view = new ImageView(this);
        view.setImageResource(resId);
        view.setColorFilter(SHEET_TEXT_SECONDARY);
        view.setPadding(ResUtil.dp2px(9), ResUtil.dp2px(9), ResUtil.dp2px(9), ResUtil.dp2px(9));
        view.setBackground(roundRect(0x10FFFFFF, SHEET_BUTTON_RADIUS_DP, 1, 0x22FFFFFF));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private LinearLayout.LayoutParams audioSheetButtonParams(boolean withStartMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ResUtil.dp2px(108), ViewGroup.LayoutParams.MATCH_PARENT);
        if (withStartMargin) params.leftMargin = ResUtil.dp2px(10);
        return params;
    }

    private LinearLayout.LayoutParams audioSheetSmallButtonParams() {
        return new LinearLayout.LayoutParams(ResUtil.dp2px(78), ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private LinearLayout.LayoutParams audioSheetMiniButtonParams(int widthDp, boolean withStartMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ResUtil.dp2px(widthDp), ResUtil.dp2px(32));
        if (withStartMargin) params.leftMargin = ResUtil.dp2px(6);
        return params;
    }

    private LinearLayout createSegmentedControl(String[] labels, int selectedIndex, SegmentClickHandler handler) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(ResUtil.dp2px(2), ResUtil.dp2px(2), ResUtil.dp2px(2), ResUtil.dp2px(2));
        row.setBackground(roundRect(0x12FFFFFF, SHEET_BUTTON_RADIUS_DP, 1, 0x24FFFFFF));
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            boolean selected = index == selectedIndex;
            TextView item = createAudioSheetText(labels[i], 13, true);
            item.setGravity(Gravity.CENTER);
            item.setSingleLine(true);
            item.setEllipsize(TextUtils.TruncateAt.END);
            item.setPadding(ResUtil.dp2px(6), 0, ResUtil.dp2px(6), 0);
            item.setTextColor(selected ? SHEET_TEXT_PRIMARY : 0xE6FFFFFF);
            item.setBackground(roundRect(selected ? SHEET_CONTROL_BG_SELECTED : 0x00000000, SHEET_SEGMENT_RADIUS_DP, 0, 0));
            item.setOnClickListener(v -> handler.onClick(index));
            row.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
        return row;
    }

    private interface SegmentClickHandler {
        void onClick(int index);
    }

    private GradientDrawable audioSheetItemBackground(boolean selected) {
        return roundRect(selected ? SHEET_CONTROL_BG_SELECTED : 0x00000000, SHEET_BUTTON_RADIUS_DP, selected ? 1 : 0, selected ? SHEET_CONTROL_STROKE_SELECTED : 0);
    }

    private void styleAudioSheetInput(TextInputLayout layout, String hint) {
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setBoxBackgroundColor(0x14FFFFFF);
        layout.setBoxStrokeColor(0x66FFFFFF);
        layout.setDefaultHintTextColor(ColorStateList.valueOf(0xA6FFFFFF));
        layout.setHintTextColor(ColorStateList.valueOf(0xD9FFFFFF));
        layout.setHint(hint);
    }

    private GradientDrawable roundRect(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(ResUtil.dp2px(radiusDp));
        if (strokeDp > 0) drawable.setStroke(ResUtil.dp2px(strokeDp), strokeColor);
        return drawable;
    }

    private GradientDrawable audioDrawerBackground() {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, audioGlassColors());
        drawable.setCornerRadius(ResUtil.dp2px(18));
        drawable.setStroke(ResUtil.dp2px(1), 0x66FFFFFF);
        return drawable;
    }

    private GradientDrawable audioSheetGlassBackground() {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, audioGlassColors());
        float radius = ResUtil.dp2px(22);
        drawable.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        drawable.setStroke(ResUtil.dp2px(1), 0x66FFFFFF);
        return drawable;
    }

    private int[] audioGlassColors() {
        return new int[]{0xB22F315E, 0x96282955, 0x82303463};
    }

    private LinearLayout.LayoutParams audioSheetTopParams(int topDp, int heightDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(heightDp));
        params.topMargin = ResUtil.dp2px(topDp);
        return params;
    }

    private LinearLayout.LayoutParams audioSheetWrapTopParams(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = ResUtil.dp2px(topDp);
        return params;
    }

    private int audioQueueContentHeight(int tab) {
        if (tab == AUDIO_QUEUE_TAB_SEARCH) {
            int max = isLandscapeAudioSheet() ? audioDrawerListMaxHeight() : ResUtil.getScreenHeight(this) * (ResUtil.isLand(this) ? 32 : 28) / 100;
            int desired = isLandscapeAudioSheet() ? 320 : ResUtil.isLand(this) ? 150 : 170;
            return Math.max(ResUtil.dp2px(126), Math.min(ResUtil.dp2px(desired), max));
        }
        int max = isLandscapeAudioSheet() ? audioDrawerListMaxHeight() : ResUtil.getScreenHeight(this) * (ResUtil.isLand(this) ? 46 : 56) / 100;
        Flag flag = getFlag();
        int count = flag == null ? 1 : Math.max(1, Math.min(isLandscapeAudioSheet() ? 12 : 8, flag.getEpisodes().size()));
        int desired = 8 + count * 46;
        return Math.max(ResUtil.dp2px(102), Math.min(ResUtil.dp2px(desired), max));
    }

    private int lyricsResultSheetHeight(int count) {
        if (isLandscapeAudioSheet()) {
            int rows = Math.max(1, Math.min(7, count));
            return Math.max(ResUtil.dp2px(126), Math.min(ResUtil.dp2px(rows * 64 + 8), audioDrawerListMaxHeight()));
        }
        int rows = Math.max(1, Math.min(3, count));
        return ResUtil.dp2px(rows * 64 + 8);
    }

    private int karaokeTrackResultSheetHeight(int count) {
        if (isLandscapeAudioSheet()) {
            int rows = Math.max(1, Math.min(5, count));
            return Math.max(ResUtil.dp2px(160), Math.min(ResUtil.dp2px(rows * 82 + 8), audioDrawerListMaxHeight()));
        }
        int rows = Math.max(1, Math.min(3, count));
        return ResUtil.dp2px(rows * 82 + 8);
    }

    private void showAudioSheet(BottomSheetDialog dialog) {
        showAudioSheet(dialog, true);
    }

    private void showAudioSheet(BottomSheetDialog dialog, boolean draggable) {
        showAudioSheet(dialog, draggable, false);
    }

    private void showAudioSheet(BottomSheetDialog dialog, boolean draggable, boolean drawerAtStart) {
        if (isLandscapeAudioSheet()) {
            showAudioDrawerSheet(dialog, drawerAtStart);
            return;
        }
        dialog.setOnShowListener(d -> {
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet == null) return;
            sheet.setBackgroundColor(Color.TRANSPARENT);
            // 基类 behaviorOf() 为 protected、Activity 调不到：这里包 try/catch 同款兜底，未关联即降级、不强制展开
            BottomSheetBehavior<FrameLayout> behavior;
            try {
                behavior = BottomSheetBehavior.from(sheet);
            } catch (IllegalArgumentException e) {
                SpiderDebug.log("bottomsheet", "behavior unavailable, degraded: %s", e.getMessage());
                return;
            }
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
            behavior.setDraggable(draggable);
        });
        dialog.show();
        applyAudioSheetWindowGlass(dialog);
    }

    private void showCompactPlaybackSheet(BottomSheetDialog dialog) {
        showCompactPlaybackSheet(dialog, true);
    }

    private void showCompactPlaybackSheet(BottomSheetDialog dialog, boolean draggable) {
        showCompactPlaybackSheet(dialog, draggable, false);
    }

    private void showCompactPlaybackSheet(BottomSheetDialog dialog, boolean draggable, boolean drawerAtStart) {
        showAudioSheet(dialog, draggable, drawerAtStart);
        Window window = dialog.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0f;
        window.setAttributes(params);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    }

    private void showAudioDrawerSheet(BottomSheetDialog dialog, boolean atStart) {
        dialog.setOnShowListener(d -> {
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet == null) return;
            sheet.setBackgroundColor(Color.TRANSPARENT);
            int height = audioDrawerHeight();
            int bottomMargin = audioDrawerBottomMargin();
            ViewGroup.LayoutParams raw = sheet.getLayoutParams();
            raw.width = audioDrawerWidth();
            raw.height = height;
            if (raw instanceof CoordinatorLayout.LayoutParams params) {
                params.gravity = (atStart ? Gravity.START : Gravity.END) | Gravity.BOTTOM;
                params.setMargins(atStart ? ResUtil.dp2px(16) : 0, mStatusBarInset + ResUtil.dp2px(16), atStart ? 0 : ResUtil.dp2px(16), bottomMargin);
            } else if (raw instanceof ViewGroup.MarginLayoutParams params) {
                params.setMargins(atStart ? ResUtil.dp2px(16) : 0, mStatusBarInset + ResUtil.dp2px(16), atStart ? 0 : ResUtil.dp2px(16), bottomMargin);
            }
            sheet.setLayoutParams(raw);
            // 同上：from() 未关联抛 IllegalArgumentException 时降级，对话框照常可用
            BottomSheetBehavior<FrameLayout> behavior;
            try {
                behavior = BottomSheetBehavior.from(sheet);
            } catch (IllegalArgumentException e) {
                SpiderDebug.log("bottomsheet", "behavior unavailable, degraded: %s", e.getMessage());
                return;
            }
            behavior.setFitToContents(false);
            behavior.setExpandedOffset(Math.max(0, ResUtil.getScreenHeight(this) - height - bottomMargin));
            behavior.setPeekHeight(height);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
            behavior.setDraggable(false);
        });
        dialog.show();
        applyAudioSheetWindowGlass(dialog);
    }

    private void applyAudioSheetWindowGlass(BottomSheetDialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0f;
        window.setAttributes(params);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
    }

    private int audioDrawerWidth() {
        return clamp(Math.round(ResUtil.getScreenWidth(this) * 0.42f), ResUtil.dp2px(380), ResUtil.dp2px(560));
    }

    private int audioDrawerHeight() {
        int screenHeight = ResUtil.getScreenHeight(this);
        int topMargin = mStatusBarInset + ResUtil.dp2px(16);
        int bottomMargin = audioDrawerBottomMargin();
        int max = Math.max(ResUtil.dp2px(320), screenHeight - topMargin - bottomMargin);
        return clamp(Math.round(screenHeight * 0.84f), ResUtil.dp2px(320), max);
    }

    private int audioDrawerBottomMargin() {
        return ResUtil.dp2px(16) + mEpisodeBottomInset;
    }

    private int audioDrawerListMaxHeight() {
        return Math.max(ResUtil.dp2px(126), audioDrawerHeight() - ResUtil.dp2px(88));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void showLyricsSettingsSheet(BottomSheetDialog dialog) {
        showCompactPlaybackSheet(dialog, false, true);
    }

    private void showAudioBackgroundSheet(BottomSheetDialog dialog) {
        showAudioSheet(dialog);
        Window window = dialog.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0f;
        window.setAttributes(params);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    }

    private void updateLyricsResultList(String[] labels) {
        if (mLyricsResultList == null) return;
        mLyricsResultList.removeAllViews();
        int selected = getLyricsSelectedIndex();
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView item = createLyricsResultItem(labels[i], i == selected, () -> {
                if (index >= 0 && index < mLyricsSearchResults.size()) applyLyrics(mLyricsSearchResults.get(index));
            });
            mLyricsResultList.addView(item, lyricsResultItemParams(i == 0));
        }
    }

    private void updateLyricsResultSheetHeight(int count) {
        if (mLyricsResultList == null) return;
        if (!(mLyricsResultList.getParent() instanceof View scroll)) return;
        ViewGroup.LayoutParams params = scroll.getLayoutParams();
        int height = lyricsResultSheetHeight(count);
        if (params != null && params.height != height) {
            params.height = height;
            scroll.setLayoutParams(params);
        }
        scroll.requestLayout();
        mLyricsResultList.requestLayout();
        if (mLyricsResultDialog == null) return;
        FrameLayout sheet = mLyricsResultDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        sheet.requestLayout();
        sheet.post(() -> {
            if (mLyricsResultDialog == null || !mLyricsResultDialog.isShowing()) return;
            FrameLayout current = mLyricsResultDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (current == null) return;
            // 同上：裸 from() 包 try/catch，未关联时只跳过强制展开、不崩
            try {
                BottomSheetBehavior.from(current).setState(BottomSheetBehavior.STATE_EXPANDED);
            } catch (IllegalArgumentException e) {
                SpiderDebug.log("bottomsheet", "behavior unavailable, degraded: %s", e.getMessage());
            }
        });
    }

    private TextView createLyricsResultItem(String label, boolean selected, Runnable action) {
        TextView item = createAudioSheetText(label, 15, false);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(ResUtil.dp2px(14), 0, ResUtil.dp2px(14), 0);
        item.setSingleLine(false);
        item.setMaxLines(2);
        item.setLineSpacing(ResUtil.dp2px(2), 1.0f);
        item.setTextColor(selected ? SHEET_TEXT_PRIMARY : SHEET_TEXT_SECONDARY);
        item.setBackground(lyricsResultItemBackground(selected));
        item.setOnClickListener(v -> action.run());
        return item;
    }

    private LinearLayout.LayoutParams lyricsResultItemParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(58));
        params.topMargin = ResUtil.dp2px(first ? 8 : 6);
        return params;
    }

    private TextView createKaraokeTrackResultItem(String label, Runnable action) {
        TextView item = createAudioSheetText(label, 14, false);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(ResUtil.dp2px(14), 0, ResUtil.dp2px(14), 0);
        item.setSingleLine(false);
        item.setMaxLines(3);
        item.setLineSpacing(ResUtil.dp2px(2), 1.0f);
        item.setTextColor(Color.WHITE);
        item.setBackground(lyricsResultItemBackground(false));
        item.setOnClickListener(v -> action.run());
        return item;
    }

    private GradientDrawable lyricsResultItemBackground(boolean selected) {
        return roundRect(selected ? SHEET_CONTROL_BG_SELECTED : SHEET_CONTROL_BG_SUBTLE, SHEET_BUTTON_RADIUS_DP, 1, selected ? SHEET_CONTROL_STROKE_SELECTED : SHEET_CONTROL_STROKE);
    }

    private void applyLyrics(LyricsResult result) {
        if (mLyrics == null || service() == null) return;
        mLyricsSearchSeq++;
        mInlineLyrics = "";
        mLyrics.apply(player(), result, true, applied -> {
            if (applied != null) {
                mLyricsSelectedResultKey = getLyricsResultKey(applied);
            }
            updateLyricsResultSelection();
            Notify.show(applied == null ? getString(R.string.player_lyrics_not_found) : getString(R.string.player_lyrics_loaded, applied.getSource()));
        });
    }

    private void updateLyricsResultSelection() {
        if (mLyricsSearchResults == null) return;
        String[] labels = new String[mLyricsSearchResults.size()];
        for (int i = 0; i < mLyricsSearchResults.size(); i++) labels[i] = getLyricsResultLabel(mLyricsSearchResults.get(i));
        updateLyricsResultList(labels);
    }

    private int getLyricsSelectedIndex() {
        if (TextUtils.isEmpty(mLyricsSelectedResultKey) || mLyricsSearchResults == null) return -1;
        for (int i = 0; i < mLyricsSearchResults.size(); i++) {
            if (TextUtils.equals(mLyricsSelectedResultKey, getLyricsResultKey(mLyricsSearchResults.get(i)))) return i;
        }
        return -1;
    }

    private String getLyricsResultLabel(LyricsResult result) {
        String title = TextUtils.isEmpty(result.getTrackName()) ? getString(R.string.player_lyrics_unknown) : result.getTrackName();
        String artist = TextUtils.isEmpty(result.getArtistName()) ? getString(R.string.player_lyrics_unknown) : result.getArtistName();
        String type = result.hasWordTiming() ? getString(R.string.player_lyrics_word) : result.isSynced() ? getString(R.string.player_lyrics_synced) : getString(R.string.player_lyrics_plain);
        return getString(R.string.player_lyrics_result_item, result.getSource(), type, result.getScore(), title, artist);
    }

    private String getLyricsResultKey(LyricsResult result) {
        if (result == null) return "";
        return TextUtils.join("|", new String[]{
                String.valueOf(result.getSource()),
                String.valueOf(result.getTrackName()),
                String.valueOf(result.getArtistName()),
                String.valueOf(Math.round(result.getDurationMs() / 1000.0)),
                String.valueOf(result.hasWordTiming()),
                String.valueOf(result.getLyrics() == null ? 0 : result.getLyrics().hashCode())
        });
    }

    private void clearLyrics() {
        if (mLyrics != null) mLyrics.clear();
    }

    private void clearKaraokeState() {
        mKaraokeResultShown = false;
        mPendingKaraokeResult = null;
        mKaraokeResultAction = KARAOKE_RESULT_ACTION_NONE;
        if (mViewModel != null) mViewModel.clearKaraokeResult();
        if (mKaraokeResultDialog != null) {
            mSuppressKaraokeResultAction = true;
            mKaraokeResultDialog.dismiss();
            mSuppressKaraokeResultAction = false;
            mKaraokeResultDialog = null;
        }
        if (mKaraoke != null) mKaraoke.clear();
    }

    private void dismissLyricsResultDialog() {
        if (mLyricsResultDialog == null) return;
        mLyricsResultDialog.dismiss();
        mLyricsResultDialog = null;
        mLyricsResultList = null;
    }

    private void setDetailLyrics(String text) {
        mDetailLyrics = getTimedLyrics(text);
        mInlineLyrics = mDetailLyrics;
    }

    private void setPlaybackLyrics(String text) {
        String lyrics = getTimedLyrics(text);
        if (!TextUtils.isEmpty(lyrics)) mInlineLyrics = lyrics;
    }

    private String getTimedLyrics(String text) {
        return LyricsController.hasTimedLyrics(text) ? text : "";
    }

    private boolean showInlineLyrics() {
        if (TextUtils.isEmpty(mInlineLyrics) || !LyricsController.hasTimedLyrics(mInlineLyrics)) return false;
        String title = getAudioStageTitle();
        String artist = getAudioStageArtist(title);
        Episode current = getEpisode();
        String signature = getHistoryKey() + "|" + (current == null ? "" : current.getName());
        return mLyrics.setInlineLyrics(signature, title, artist, mInlineLyrics, player().getDuration(), player().getPosition());
    }

    private boolean isMusicLike() {
        Flag current = getFlag();
        String flag = current == null ? "" : current.getShow();
        Site site = getSite();
        String text = (getKey() + " " + (site == null ? "" : site.getKey()) + " " + (site == null ? "" : site.getName()) + " " + flag + " " + getName());
        return LyricsController.isMusicLikeText(text);
    }

    private String getLyricsArtist(String title) {
        Episode current = getEpisode();
        return getArtistFromEpisode(title, current == null ? "" : current.getName());
    }

    private String getArtistFromEpisode(String title, String episode) {
        String name = Objects.toString(title, "").trim();
        String value = Objects.toString(episode, "").trim();
        if (name.isEmpty() || value.isEmpty() || TextUtils.equals(name, value)) return "";
        for (String separator : new String[]{" - ", " – ", " — ", "-"}) {
            if (value.startsWith(name + separator) && value.length() > name.length() + separator.length()) {
                return value.substring(name.length() + separator.length()).trim();
            }
            if (value.endsWith(separator + name) && value.length() > name.length() + separator.length()) {
                return value.substring(0, value.length() - name.length() - separator.length()).trim();
            }
        }
        return value;
    }

    @Override
    protected void onTitlesChanged() {
        setTitleVisible();
    }

    @Override
    protected void onError(String msg) {
        recordPlayHealth(false, msg);
        mBinding.swipeLayout.setEnabled(true);
        PlayerManager manager = player();
        if (manager != null) Track.delete(manager.getKey());
        mClock.setCallback(null);
        clearLyrics();
        clearKaraokeState();
        if (manager != null) {
            manager.resetTrack();
            manager.reset();
            manager.stop();
        }
        hideSwitchCover();
        showError(msg);
        startFlow();
    }

    @Override
    protected void onReload(String msg) {
        if (PlayerManager.RELOAD_LUT_WARMUP.equals(msg)) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-ui", "auto refresh after lut warmup playback failure key=%s episode=%s", getKey(), getEpisode() == null ? null : getEpisode().getName());
            onRefresh();
            return;
        }
        super.onReload(msg);
    }

    @Override
    protected void onReclaim() {
        Result result = mLastPlayerResult;
        if (result != null) setPlayer(result, true);
    }

    @Override
    protected void onStateChanged(int state) {
        debugPlaybackControl("stateChanged=" + state);
        debugLyricsLoop("stateChanged=" + state, true);
        switch (state) {
            case Player.STATE_BUFFERING:
                showProgress();
                break;
            case Player.STATE_READY:
                if (mPendingKaraokeResult == null) mKaraokeResultShown = false;
                // 纯音频内容没有视频首帧可等，READY 即撤盖图；有视频时仍只认首帧回调
                if (isAudioOnly()) hideSwitchCover();
                recordPlayHealth(true, "");
                hideProgress();
                checkControl();
                refreshLyrics();
                player().reset();
                embyOnPlaying();
                break;
            case Player.STATE_ENDED:
                embyOnEnded();
                checkEnded(true);
                updatePlayControl(false, syncPiPForPlaybackMode());
                break;
        }
    }

    @Override
    protected void onPlayingChanged(boolean isPlaying) {
        debugPlaybackControl("playingChanged=" + isPlaying);
        debugLyricsLoop("playingChanged=" + isPlaying, true);
        syncLyricsPlaybackState(isPlaying);
        syncKaraokePosition();
        boolean audioMode = syncPiPForPlaybackMode();
        if (isPlaying || isPaused()) updatePlayControl(isPlaying, audioMode);
    }

    private void updatePlayControl(boolean isPlaying, boolean audioMode) {
        if (!audioMode) mPiP.update(this, isPlaying);
        mBinding.control.play.setImageResource(isPlaying ? androidx.media3.ui.R.drawable.exo_icon_pause : androidx.media3.ui.R.drawable.exo_icon_play);
        checkAudioPlayImg(isPlaying);
    }

    @Override
    protected void onPlayerPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
        debugPlaybackControl("positionDiscontinuity=" + reason + " old=" + oldPosition.positionMs + " new=" + newPosition.positionMs);
        debugLyricsLoop("positionDiscontinuity=" + reason, true);
        syncLyricsPlaybackState(player().isPlaying());
        syncKaraokePosition();
        if (mKaraoke != null) mKaraoke.update(player(), mLyrics == null ? null : mLyrics.getLines());
    }

    private void syncLyricsPlaybackState() {
        if (mLyrics == null || service() == null || player().isEmpty()) return;
        debugLyricsLoop("syncLyricsPlaybackState", false);
        mLyrics.update(player());
    }

    private void syncLyricsPlaybackState(boolean isPlaying) {
        if (mLyrics == null || service() == null || player().isEmpty()) return;
        debugLyricsLoop("syncLyricsPlaybackState=" + isPlaying, false);
        mLyrics.update(player(), isPlaying);
    }

    private void checkAudioPlayImg(boolean isPlaying) {
        mBinding.audioPlay.setImageResource(isPlaying ? androidx.media3.ui.R.drawable.exo_icon_pause : androidx.media3.ui.R.drawable.exo_icon_play);
        mBinding.audioKaraoke.setPlaying(isPlaying);
        updateAudioLightEffectAnimation(isPlaying);
        syncAudioCoverRotation();
    }

    private void updateAudioLightEffectAnimation(boolean animated) {
        if (!mAudioStageVisible || !PlayerSetting.isAudioBackgroundLightEffect() || mAudioLightEffectAnimated == animated) return;
        mAudioLightEffectAnimated = animated;
        Drawable background = mBinding.audioStage.getBackground();
        if (background instanceof AudioPlayerBackgroundDrawable drawable) drawable.setAnimated(animated);
        else applyAudioBackground();
    }

    private void syncKaraokePosition() {
        if (service() == null || player().isEmpty()) return;
        long position = Math.max(0, player().getPosition() + PlayerSetting.getLyricsTimeOffsetMs());
        boolean playing = player().isPlaying();
        debugLyricsLoop("syncKaraokePosition", false);
        if (mBinding.karaoke != null) mBinding.karaoke.syncPosition(position, playing);
        mBinding.audioKaraoke.syncPosition(position, playing);
    }

    @Override
    protected void onSizeChanged(VideoSize size) {
        logVideoFrame("onSizeChanged before size=" + size.width + "x" + size.height);
        mPiP.update(this, size.width, size.height, getScale());
        setSizeText();
        updateVideoHeight();
        applyResizeMode(getScale());
        checkOrientation(size);
        logVideoFrame("onSizeChanged after size=" + size.width + "x" + size.height);
    }

    @Override
    protected void onSurfaceAttached() {
        logVideoFrame("onSurfaceAttached before");
        applyResizeMode(getScale());
        logVideoFrame("onSurfaceAttached after");
    }

    @Override
    public void onSubtitleClick() {
        // 绑定 AI 本地字幕控制器
        try {
            com.fongmi.android.tv.ai.asr.AiAsrController.get()
                    .bindSubtitleView(mBinding.exo.getSubtitleView());
        } catch (Exception ignored) {}
        bindAiAsrMpvProvider();
        SubtitleDialog.create().view(mBinding.exo.getSubtitleView()).player(player()).show(this);
        hideControl();
    }

    /**
     * 向 AI 本地字幕控制器注入 MPV 播放信息。
     * MPV 内核时 libmpv 在 native 层输出音频，Java 层无钩子，控制器需要 URL/headers
     * 起影子播放器采集音频；Exo 内核时走音频链注入，不需要。
     */
    private void bindAiAsrMpvProvider() {
        try {
            com.fongmi.android.tv.ai.asr.AiAsrController.get()
                    .setMpvInfoProvider(new com.fongmi.android.tv.ai.asr.AiAsrController.MpvInfoProvider() {
                        @Override
                        public boolean isMpvActive() {
                            try {
                                return player() != null && player().isMpv();
                            } catch (Exception e) {
                                return false;
                            }
                        }

                        @Override
                        public String getMediaUrl() {
                            try {
                                com.fongmi.android.tv.player.engine.PlaySpec spec = player().getSpec();
                                return spec == null ? null : spec.getUrl();
                            } catch (Exception e) {
                                return null;
                            }
                        }

                        @Override
                        public java.util.Map<String, String> getMediaHeaders() {
                            try {
                                com.fongmi.android.tv.player.engine.PlaySpec spec = player().getSpec();
                                return spec == null ? null : spec.getHeaders();
                            } catch (Exception e) {
                                return null;
                            }
                        }

                        @Override
                        public long getPositionMs() {
                            try {
                                androidx.media3.common.Player p = player().getPlayer();
                                return p == null ? 0 : p.getCurrentPosition();
                            } catch (Exception e) {
                                return 0;
                            }
                        }

                        @Override
                        public boolean isPlaying() {
                            try {
                                androidx.media3.common.Player p = player().getPlayer();
                                return p != null && p.isPlaying();
                            } catch (Exception e) {
                                return false;
                            }
                        }
                    });
        } catch (Exception ignored) {}
    }

    @Override
    public void onTimeChanged(long time) {
        if (!isOwner()) return;
        long position, duration;
        mHistory.setCreateTime(time);
        if (hasDiscNavigationTimeline()) {
            if (canSavePlaybackHistory(mHistory) && mHistory.canSync()) syncHistory();
            return;
        }
        updatePlaybackHistoryPosition();
        syncCurrentAudioPlaylistMetadata();
        debugLyricsLoop("clock", false);
        syncKaraokePosition();
        // AI 本地字幕 MPV 影子采集同步（每秒）：漂移校正 + 播放状态镜像 + 内核切换自愈
        try {
            com.fongmi.android.tv.ai.asr.AiAsrController.get().syncMpvShadow();
        } catch (Exception ignored) {}
        if (mLyrics != null) mLyrics.update(player());
        if (mKaraoke != null) mKaraoke.update(player(), mLyrics == null ? null : mLyrics.getLines());
        position = mHistory.getPosition();
        duration = mHistory.getDuration();
        PlaybackEventCollector.get().onProgress(mHistory, player());
        if (mHistory.canSave() && mHistory.canSync()) syncHistory();
        embyOnTick();
        if (isAutoSkipIntro() && mHistory.getEnding() > 0 && duration > 0 && mHistory.getEnding() + position >= duration) {
            checkEnded(false);
        } else {
            SkipSegment segment = activeSkipSegment(position);
            if (segment != null) {
                if (segment.isEnding()) checkEnded(false);
                else if (segment.getEnd() > position) controller().seekTo(segment.getEnd());
            }
        }
    }

    /** 设置页「自动跳过片头片尾」开关（skip_intro）：默认开＝保持打点自动跳的现行行为，关掉后已标记片段也不自动跳。 */
    private boolean isAutoSkipIntro() {
        return getSharedPreferences("xingchen", MODE_PRIVATE).getBoolean("skip_intro", true);
    }

    /**
     * 源在播放结果里自带的跳过片段：当前播放位置命中的那段（与 VodSkipPolicy 同口径）。
     * 片中段恒生效；片头/片尾段只在用户没打过对应点（History 值为 AUTO_SKIP 语义）时生效，打点优先。
     */
    private SkipSegment activeSkipSegment(long position) {
        if (!isAutoSkipIntro() || mLastPlayerResult == null || mHistory == null || position < 0) return null;
        for (SkipSegment segment : mLastPlayerResult.getSkipSegments()) {
            if (segment == null || !segment.contains(position)) continue;
            if (segment.isMiddle()) return segment;
            if (segment.isOpening() && History.isAutoSkip(mHistory.getOpening())) return segment;
            if (segment.isEnding() && History.isAutoSkip(mHistory.getEnding())) return segment;
        }
        return null;
    }

    private void updatePlaybackHistoryPosition() {
        if (mHistory == null || hasDiscNavigationTimeline()) return;
        long position = player().getPosition();
        long duration = player().getDuration();
        if (position > 0) mHistory.setPosition(position);
        if (duration > 0) mHistory.setDuration(duration);
        else if (mHistory.getDuration() < 0) mHistory.setDuration(0);
        PlaybackEventCollector.get().updateHistory(mHistory);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        if (isRedirect()) return;
        ReceiveDialog.create().event(event).show(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (isRedirect()) return;
        if (event.getType() == RefreshEvent.Type.DETAIL) getDetail();
        else if (event.getType() == RefreshEvent.Type.PLAYER) onRefresh();
        else if (event.getType() == RefreshEvent.Type.VOD) updateVod(event.getVod());
        else if (event.getType() == RefreshEvent.Type.SUBTITLE) player().setSub(Sub.from(event.getPath()));
        else if (event.getType() == RefreshEvent.Type.DANMAKU) player().reloadDanmaku(Danmaku.from(event.getPath()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (isRedirect() || !event.isVod() || mParseAdapter == null) return;
        mParseAdapter.reload();
    }

    /**
     * 猫源开了内嵌设置页：这次点击的本意就是开网页，本页立刻退场。
     *
     * <p>不能等 detail 结果再判定——那份结果可能被主线程堵住好几秒，这段时间里按返回就会
     * 落回本页（空白播放页）。用请求时刻和本次 detail 起始时间比，确认是自己触发的才退。
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCatWebEvent(com.fongmi.android.tv.event.CatWebEvent event) {
        if (isRedirect() || !event.after(detailStartTime)) return;
        SpiderDebug.log("video-flow", "detail yield to cat webview (event) key=%s id=%s", getKey(), getId());
        finish();
    }

    /**
     * 播放器已存在时原地换源（对齐海阔 8.83 VideoPlayerActivity.onPlaySourceUpdated）。
     * 8.83 逻辑：player 为空或特殊模式（PiP/音乐/迅雷）时回退 startActivity，
     * 否则 setIntent + 更新标题 + updateUrl，不启动新页面。
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlaySourceUpdated(PlaySourceUpdateEvent event) {
        Intent intent = event.getIntent();
        if (intent == null) return;
        // 多实例交叠时只有属主响应换源，正在销毁的旧实例不许抢着换
        if (sPlayerOwner != null && sPlayerOwner != this) return;
        // 复用 onNewIntent 的换源逻辑：保存历史、合并 extras、重新 checkId 拉起播放
        onNewIntent(intent);
    }

    /**
     * 海阔 refreshVideoUrl 的消费面（对齐 8.83 VideoPlayerActivity.refreshVideoUrl）：
     * 规则 JS 在播放中拿到新直链时原地换址。只认海阔来源的播放（isHkKey 门，与原版
     * 「事件的有无订阅者即播放页在场」同义）；restartPosition=false 保进度续播。
     * 原版换址后还会按新址重取字幕/弹幕，星辰字幕/弹幕走自有链，此处不连动。
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayDataUpdate(com.fongmi.android.tv.api.hk.event.video.PlayDataUpdateEvent event) {
        if (event == null || isRedirect()) return;
        if (!HkDetailBridge.isHkKey(getKey())) return;
        if (service() == null || TextUtils.isEmpty(event.getUrl())) return;
        if (sPlayerOwner != null && sPlayerOwner != this) return;
        player().refreshUrl(event.getUrl(), event.isRestartPosition());
    }

    private void setPosition() {
        if (mHistory == null || hasDiscMenu()) return;
        // Emby 断点续播：以服务器 UserData.PlaybackPositionTicks 为准，不吃本地历史
        if (isEmbyPlayback()) {
            long startMs = getIntent().getLongExtra(EmbyNavigator.EXTRA_START_MS, 0);
            if (startMs > 0 && service() != null) player().seekTo(startMs);
            return;
        }
        if (mHistory.isNearEnding()) {
            SpiderDebug.log("video-flow", "reset near-end history position=%d duration=%d key=%s", mHistory.getPosition(), mHistory.getDuration(), getHistoryKey());
            mHistory.resetPlaybackPosition();
            syncHistory();
        }
        long position = Math.max(isAutoSkipIntro() ? mHistory.getOpening() : 0, mHistory.getPosition());
        if (isAutoSkipIntro() && History.isAutoSkip(mHistory.getOpening()) && mLastPlayerResult != null) {
            for (SkipSegment segment : mLastPlayerResult.getSkipSegments()) {
                if (segment != null && segment.isOpening() && segment.isValid() && segment.getStart() == 0) position = Math.max(position, segment.getEnd());
            }
        }
        if (position > 0) player().seekTo(position);
    }

    // ================= Emby 播放钩子 =================
    // 实测语义（emby-server/VERIFY.md F2）：
    // 播放开始 POST /Sessions/Playing；播放中每 10 秒 POST /Sessions/Playing/Progress
    // （必须带 PlaySessionId，否则 400）；退出/播完 POST /Sessions/Playing/Stopped
    // （服务端据此写 Played、超 90% 清续播点；只调 Progress 续播点不落盘）。
    // 全部子线程上报，不阻塞播放；任一环节异常静默丢弃。

    private boolean isEmbyPlayback() {
        return !TextUtils.isEmpty(getIntent().getStringExtra(EmbyNavigator.EXTRA_SERVER))
                && !TextUtils.isEmpty(getIntent().getStringExtra(EmbyNavigator.EXTRA_ITEM));
    }

    private EmbyApi embyApi() {
        try {
            EmbyStore.EmbyServer s = EmbyStore.getServer(getIntent().getStringExtra(EmbyNavigator.EXTRA_SERVER));
            if (s == null || !s.isLogin()) return null;
            return new EmbyApi(s.baseUrl, s.token, s.userId, s.id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 播放开始：上报 Playing + 挂载默认外挂字幕。 */
    private void embyOnPlaying() {
        if (!isEmbyPlayback() || mEmbyPlayingReported) return;
        mEmbyPlayingReported = true;
        mountEmbySubtitles();
        String itemId = getIntent().getStringExtra(EmbyNavigator.EXTRA_ITEM);
        String sessionId = getIntent().getStringExtra(EmbyNavigator.EXTRA_SESSION);
        Task.execute(() -> {
            EmbyApi api = embyApi();
            if (api == null) return;
            api.reportPlaying(itemId, sessionId);
        });
    }

    /** 每秒 tick：每 10 秒上报一次进度。 */
    private void embyOnTick() {
        if (!isEmbyPlayback() || !mEmbyPlayingReported || mEmbyStoppedReported) return;
        if (service() == null) return;
        long now = System.currentTimeMillis();
        if (now - mEmbyLastProgressMs < 10_000) return;
        mEmbyLastProgressMs = now;
        long pos;
        boolean paused;
        try {
            pos = player().getPosition();
            paused = !player().isPlaying();
        } catch (Throwable ignored) {
            return;
        }
        String itemId = getIntent().getStringExtra(EmbyNavigator.EXTRA_ITEM);
        String sessionId = getIntent().getStringExtra(EmbyNavigator.EXTRA_SESSION);
        Task.execute(() -> {
            EmbyApi api = embyApi();
            if (api == null) return;
            api.reportProgress(itemId, sessionId, pos, paused);
        });
    }

    /** 播完：上报 Stopped + 显式标记看完（兜底）。 */
    private void embyOnEnded() {
        if (!isEmbyPlayback() || !mEmbyPlayingReported || mEmbyEndedReported) return;
        mEmbyEndedReported = true;
        mEmbyStoppedReported = true;
        long pos = 0;
        try {
            if (service() != null) pos = player().getPosition();
        } catch (Throwable ignored) {
        }
        long position = pos;
        String itemId = getIntent().getStringExtra(EmbyNavigator.EXTRA_ITEM);
        String sessionId = getIntent().getStringExtra(EmbyNavigator.EXTRA_SESSION);
        Task.execute(() -> {
            EmbyApi api = embyApi();
            if (api == null) return;
            api.reportStopped(itemId, sessionId, position);
            try {
                api.markPlayed(itemId);
            } catch (Throwable ignored) {
            }
        });
    }

    /** 中途退出：只上报 Stopped（不断点清零，服务端保留续播点）。 */
    private void embyOnExit() {
        if (!isEmbyPlayback() || !mEmbyPlayingReported || mEmbyStoppedReported) return;
        mEmbyStoppedReported = true;
        long pos = 0;
        try {
            if (service() != null) pos = player().getPosition();
        } catch (Throwable ignored) {
        }
        long position = pos;
        String itemId = getIntent().getStringExtra(EmbyNavigator.EXTRA_ITEM);
        String sessionId = getIntent().getStringExtra(EmbyNavigator.EXTRA_SESSION);
        Task.execute(() -> {
            EmbyApi api = embyApi();
            if (api == null) return;
            api.reportStopped(itemId, sessionId, position);
        });
    }

    /** 外挂字幕挂载：走现有 player().setSub 入口，默认挂第一条。 */
    private void mountEmbySubtitles() {
        if (mEmbySubs != null || !isEmbyPlayback() || service() == null) return;
        mEmbySubs = parseEmbySubs(getIntent().getStringExtra(EmbyNavigator.EXTRA_SUBTITLES));
        if (mEmbySubs.isEmpty()) return;
        int def = getIntent().getIntExtra(EmbyNavigator.EXTRA_DEFAULT_SUB, 0);
        if (def < 0 || def >= mEmbySubs.size()) def = 0;
        mEmbySubSelected = def;
        try {
            player().setSub(mEmbySubs.get(def));
        } catch (Throwable ignored) {
        }
    }

    private List<Sub> parseEmbySubs(String json) {
        List<Sub> list = new ArrayList<>();
        if (TextUtils.isEmpty(json)) return list;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String title = o.optString("title", "");
                String url = o.optString("url", "");
                if (TextUtils.isEmpty(url)) continue;
                if (TextUtils.isEmpty(title)) title = "字幕" + (i + 1);
                list.add(Sub.create(title, url, "", PlayerHelper.getSubtitleMimeType(url)));
            }
        } catch (Throwable ignored) {
        }
        return list;
    }

    /** Emby 外挂字幕切换（二级页 TrackDialog 回调进来）。 */
    private void onEmbySubSelected(int index) {
        if (mEmbySubs == null || index < 0 || index >= mEmbySubs.size() || service() == null) return;
        mEmbySubSelected = index;
        try {
            player().setSub(mEmbySubs.get(index));
            Notify.show(mEmbySubs.get(index).getName());
        } catch (Throwable ignored) {
        }
    }

    /** 字幕轨弹窗：Emby 源时把外挂字幕并入现有 TrackDialog（二级页样式不变）。 */
    private void attachEmbySubs(TrackDialog dialog, int type) {
        try {
            if (type != C.TRACK_TYPE_TEXT) return;
            if (!isEmbyPlayback()) return;
            if (mEmbySubs == null) {
                mEmbySubs = parseEmbySubs(getIntent().getStringExtra(EmbyNavigator.EXTRA_SUBTITLES));
                mEmbySubSelected = getIntent().getIntExtra(EmbyNavigator.EXTRA_DEFAULT_SUB, 0);
            }
            if (mEmbySubs == null || mEmbySubs.isEmpty()) return;
            dialog.embySubs(mEmbySubs, mEmbySubSelected, this::onEmbySubSelected);
        } catch (Throwable ignored) {
        }
    }

    /** Emby 音轨元数据进音频轨列表：只换名，切换走原生（对不上数量就不碰）。 */
    private void attachEmbyTracks(TrackDialog dialog, int type) {
        try {
            if (type != C.TRACK_TYPE_AUDIO) return;
            if (!isEmbyPlayback()) return;
            String json = getIntent().getStringExtra(EmbyNavigator.EXTRA_TRACKS);
            if (TextUtils.isEmpty(json)) return;
            dialog.embyTracks(json);
        } catch (Throwable ignored) {
        }
    }

    // ================= Emby 播放中换源（画质/转码/AI 链换集共用） =================
    // Result/playerContent 只支持单地址（Result.setUrl(String)），多地址数组要动播放器主链，
    // 风险大——走退回方案：二级页「画质」入口复用 EmbyQualityDialog，换源保进度。

    /** 当前在播的集是不是 Emby 集（详情链：episode url 为 emby: 开头）。 */
    private boolean isEmbyEpisode() {
        try {
            Episode ep = getEpisode();
            return ep != null && ep.getUrl() != null && ep.getUrl().startsWith("emby:");
        } catch (Throwable t) {
            return false;
        }
    }

    /** 二级页 Emby「画质」入口：复用 EmbyQualityDialog 换源，保进度。 */
    @Override
    public void onEmbyQualityPanel() {
        String sid = getIntent().getStringExtra(EmbyNavigator.EXTRA_SERVER);
        String iid = getIntent().getStringExtra(EmbyNavigator.EXTRA_ITEM);
        if (TextUtils.isEmpty(sid) || TextUtils.isEmpty(iid)) {
            Episode ep = getEpisode();
            if (ep != null && ep.getUrl() != null && ep.getUrl().startsWith("emby:")) {
                sid = EmbyNavigator.serverIdOf(ep.getUrl());
                iid = EmbyNavigator.itemIdOf(ep.getUrl());
            }
        }
        if (TextUtils.isEmpty(sid) || TextUtils.isEmpty(iid)) {
            Notify.show("当前不是 Emby 播放");
            return;
        }
        hideControl();
        final String s = sid;
        final String id = iid;
        EmbyQualityDialog.showForItem(this, s, id, getName(), opt -> {
            if (opt == null || TextUtils.isEmpty(opt.url)) return;
            resetEmby410(s, id);
            mEmbyCurrentOpt = opt;
            switchEmbySource(opt.url, opt.sessionId, s, id, -1);
        });
    }

    /**
     * Emby 换源：旧会话 Stopped 落盘 → 直接走 playerContent(PUSH) 播新地址。
     * 进度：AI 链走 intent EXTRA_START_MS（onPrepare→setPosition 照旧）；
     * 详情链走本地历史（setPosition 原逻辑），这里先把当前位置写进历史。
     * startMs<0 = 用当前位置。
     */
    private void switchEmbySource(String url, String sessionId, String sid, String iid, long startMs) {
        if (TextUtils.isEmpty(url)) return;
        long pos = startMs >= 0 ? startMs : currentEmbyPosition();
        // 旧会话落盘（AI 链才有上报；详情链上报本来就没接，不硬凑）
        if (isEmbyPlayback() && mEmbyPlayingReported && !mEmbyStoppedReported) {
            try {
                EmbyApi api = embyApi();
                String oIid = getIntent().getStringExtra(EmbyNavigator.EXTRA_ITEM);
                String oSess = getIntent().getStringExtra(EmbyNavigator.EXTRA_SESSION);
                if (api != null) {
                    final long oPos = pos;
                    Task.execute(() -> api.reportStopped(oIid, oSess, oPos));
                }
            } catch (Throwable ignored) {
            }
        }
        mEmbyPlayingReported = false;
        mEmbyEndedReported = false;
        mEmbyStoppedReported = false;
        mEmbySubs = null;
        getIntent().putExtra("id", url);
        if (isEmbyPlayback()) {
            getIntent().putExtra(EmbyNavigator.EXTRA_SESSION, sessionId == null ? "" : sessionId);
            if (!TextUtils.isEmpty(iid)) getIntent().putExtra(EmbyNavigator.EXTRA_ITEM, iid);
            getIntent().putExtra(EmbyNavigator.EXTRA_START_MS, pos);
        } else if (mHistory != null) {
            mHistory.setPosition(pos);
        }
        saveHistory();
        try {
            player().stop();
            player().clear();
        } catch (Throwable ignored) {
        }
        mClock.setCallback(null);
        clearLyrics();
        showProgress();
        // 固定走 PUSH 直链口径：Emby spider 的 playerContent 只认 emby: id，
        // 用详情链的 key（csp_Emby）传裸 URL 会解析崩，这里换源的都是已解析好的直链。
        playDirect(SiteApi.PUSH, "", url);
    }

    private long currentEmbyPosition() {
        try {
            if (service() != null) return Math.max(0, player().getPosition());
        } catch (Throwable ignored) {
        }
        return 0;
    }

    /**
     * Emby 直链 410 自救：直链是短命链，播中途被服务器收回（HTTP 410 Gone）时，
     * 重拉一次 PlaybackInfo 拿新鲜候选换链续播，而不是停播装死。
     * 匹配顺序：同一 MediaSource → 同分辨率档（档内码率最高）→ 默认规则（分辨率优先）。
     * 同一集上限 2 次，超了走原报错；进度保留（复用 switchEmbySource）。
     * PlaySessionId：switchEmbySource 已把新链的 sessionId 写进 EXTRA_SESSION，
     * 旧会话按原逻辑 Stopped 落盘、新会话 READY 后重新 Playing 上报，这里不另起一套。
     */
    @Override
    protected boolean onHttpGoneRetry() {
        return retryEmby410Gone();
    }

    /** 新起一部 Emby 播放时调用：410 重试计数清零并换归属键。 */
    private void resetEmby410(String sid, String iid) {
        mEmby410Key = sid + ":" + iid;
        mEmby410Retry = 0;
    }

    private boolean retryEmby410Gone() {
        String sid = null;
        String iid = null;
        if (isEmbyPlayback()) {
            sid = getIntent().getStringExtra(EmbyNavigator.EXTRA_SERVER);
            iid = getIntent().getStringExtra(EmbyNavigator.EXTRA_ITEM);
        }
        if (TextUtils.isEmpty(sid) || TextUtils.isEmpty(iid)) {
            Episode ep = getEpisode();
            if (ep != null && ep.getUrl() != null && ep.getUrl().startsWith("emby:")) {
                sid = EmbyNavigator.serverIdOf(ep.getUrl());
                iid = EmbyNavigator.itemIdOf(ep.getUrl());
            }
        }
        if (TextUtils.isEmpty(sid) || TextUtils.isEmpty(iid)) return false;
        String key = sid + ":" + iid;
        if (!key.equals(mEmby410Key)) {
            mEmby410Key = key;
            mEmby410Retry = 0;
        }
        if (mEmby410Retry >= 2) return false;
        mEmby410Retry++;
        final String s = sid;
        final String id = iid;
        final int attempt = mEmby410Retry;
        Notify.show("直链已过期，正在换链续播…");
        Task.execute(() -> {
            try {
                EmbyStore.EmbyServer server = EmbyStore.getServer(s);
                if (server == null || !server.isLogin()) throw new Exception("未登录");
                EmbyApi api = new EmbyApi(server.baseUrl, server.token, server.userId, server.id);
                EmbyApi.PlaybackCandidates cand = api.playbackCandidates(id, getName());
                List<EmbyApi.PlayOption> direct = new ArrayList<>();
                for (EmbyApi.PlayOption o : cand.options) {
                    if (o.direct && !TextUtils.isEmpty(o.url)) direct.add(o);
                }
                if (direct.isEmpty()) throw new Exception("没有可直放的版本");
                EmbyApi.PlayOption pick = matchEmby410Option(direct, mEmbyCurrentOpt);
                if (pick == null || TextUtils.isEmpty(pick.url)) throw new Exception("没有可用的版本");
                final EmbyApi.PlayOption p = pick;
                App.post(() -> {
                    mEmbyCurrentOpt = p;
                    if (SpiderDebug.isEnabled()) {
                        SpiderDebug.log("emby-play", "410 retry attempt=%d pick=%s", attempt, p.label);
                    }
                    switchEmbySource(p.url, p.sessionId, s, id, -1);
                });
            } catch (Exception ex) {
                App.post(() -> onError(getString(R.string.error_play_url)));
            }
        });
        return true;
    }

    /** 410 重取后的版本匹配：同一 MediaSource → 同分辨率档内码率最高 → 默认规则。 */
    private static EmbyApi.PlayOption matchEmby410Option(List<EmbyApi.PlayOption> direct, EmbyApi.PlayOption current) {
        if (direct == null || direct.isEmpty()) return null;
        if (current != null) {
            if (!TextUtils.isEmpty(current.mediaSourceId)) {
                for (EmbyApi.PlayOption o : direct) {
                    if (current.mediaSourceId.equals(o.mediaSourceId)) return o;
                }
            }
            if (current.width > 0 && current.height > 0) {
                EmbyApi.PlayOption best = null;
                for (EmbyApi.PlayOption o : direct) {
                    if (o.width == current.width && o.height == current.height) {
                        if (best == null || o.bitrate > best.bitrate) best = o;
                    }
                }
                if (best != null) return best;
            }
        }
        return EmbyApi.pickDefaultDirect(direct);
    }

    /**
     * AI 直播链的 Emby 集（emby: id）：经 EmbyApi 解析出直链再换源，选集/下一集可用。
     * 详情链的集走原有 spider playerContent，不进这里。
     */
    private boolean switchEmbyAiEpisode(Episode item) {
        if (!isEmbyPlayback() || item == null || item.getUrl() == null || !item.getUrl().startsWith("emby:"))
            return false;
        if (mFlagAdapter != null) mFlagAdapter.toggle(item);
        if (getFlag() != null) setEpisodeAdapter(getFlag().getEpisodes());
        updateHistory(item);
        String sid = EmbyNavigator.serverIdOf(item.getUrl());
        String iid = EmbyNavigator.itemIdOf(item.getUrl());
        Notify.show(getString(R.string.emby_quality_loading));
        Task.execute(() -> {
            try {
                EmbyStore.EmbyServer s = EmbyStore.getServer(sid);
                if (s == null || !s.isLogin()) throw new Exception("未登录");
                EmbyApi api = new EmbyApi(s.baseUrl, s.token, s.userId, s.id);
                EmbyApi.PlaybackCandidates cand = api.playbackCandidates(iid, item.getName());
                String url = cand.options.get(0).url;
                final EmbyApi.PlayOption picked = cand.options.get(0);
                App.post(() -> {
                    resetEmby410(sid, iid);
                    mEmbyCurrentOpt = picked;
                    switchEmbySource(url, cand.playSessionId, sid, iid, 0);
                });
            } catch (Exception e) {
                App.post(() -> Notify.show("Emby 播放失败：" + e.getMessage()));
            }
        });
        return true;
    }

    /** AI 直播链剧集：intent 带 emby_episodes 时配好选集 UI（下一集/选集可用）。 */
    private boolean mEmbyAiEpisodesBound;

    private void bindEmbyAiEpisodes() {
        if (!isEmbyPlayback() || mEmbyAiEpisodesBound) return;
        String json = getIntent().getStringExtra(EmbyNavigator.EXTRA_EPISODES);
        if (TextUtils.isEmpty(json)) return;
        try {
            JSONArray arr = new JSONArray(json);
            if (arr.length() == 0) return;
            String curIid = getIntent().getStringExtra(EmbyNavigator.EXTRA_ITEM);
            Flag flag = Flag.create("选集");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String eid = o.optString("id", "");
                String nm = o.optString("name", "");
                if (eid.isEmpty()) continue;
                Episode ep = Episode.create(nm.isEmpty() ? ("第" + (i + 1) + "集") : nm, eid);
                if (!TextUtils.isEmpty(curIid) && eid.endsWith(":" + curIid)) ep.setSelected(true);
                flag.getEpisodes().add(ep);
            }
            if (flag.getEpisodes().isEmpty()) return;
            boolean anySel = false;
            for (Episode e : flag.getEpisodes()) if (e.isSelected()) anySel = true;
            if (!anySel) flag.getEpisodes().get(0).setSelected(true);
            List<Flag> flags = new ArrayList<>();
            flags.add(flag);
            mFlagAdapter.addAll(flags);
            mFlagAdapter.setSelected(flag);
            setEpisodeAdapter(flag.getEpisodes());
            mBinding.flag.setVisibility(View.VISIBLE);
            // 历史对齐到起始集：后面点线路旗走 seamless 能按 emby: id 找回本集，不会误播
            for (Episode e : flag.getEpisodes()) {
                if (e.isSelected() && mHistory != null) {
                    mHistory.setVodRemarks(e.getName());
                    mHistory.setEpisodeUrl(e.getUrl());
                    break;
                }
            }
            mEmbyAiEpisodesBound = true;
        } catch (Throwable ignored) {
        }
    }

    // ================= Emby 详情扩展 =================

    private boolean isEmbyDetail(Vod item) {
        return item != null && item.getId() != null && item.getId().startsWith("emby:");
    }

    /**
     * Emby 专属字段补进详情信息区（只加不改原有布局结构）：
     * 简介/演职员/评分走 Vod 既有字段（spider 已映射）；这里补
     * Backdrop（无 wallPic 时做上下文壁纸）、类型行、同类推荐（可点行）。
     * Similar 无元数据时服务端 500，spider 侧已容错（失败当无推荐）。
     */
    private void embyBindDetailExtras(Vod item) {
        boolean emby = isEmbyDetail(item);
        // Emby 收藏切换按钮：只在 Emby 详情露脸，样式与旁边四个按钮统一
        if (mBinding.embyFavBtn != null) mBinding.embyFavBtn.setVisibility(emby ? View.VISIBLE : View.GONE);
        // Emby 标记已看按钮：同上
        if (mBinding.embyPlayedBtn != null) mBinding.embyPlayedBtn.setVisibility(emby ? View.VISIBLE : View.GONE);
        // Emby 红线：下载按钮直接不露脸（不是点了才拦）；本地收藏与服务器收藏重复，Emby 详情只留服务器收藏
        if (mBinding.downloadBtn != null) mBinding.downloadBtn.setVisibility(emby ? View.GONE : View.VISIBLE);
        if (mBinding.keepBtn != null) mBinding.keepBtn.setVisibility(emby ? View.GONE : View.VISIBLE);
        if (!emby) return;
        bindEmbyFavBtn(EmbyNavigator.serverIdOf(item.getId()), EmbyNavigator.itemIdOf(item.getId()));
        try {
            // 详情页背景纯深色：不再拿 Emby Backdrop 当整页背景（共用页口径）
            SpannableStringBuilder sb = new SpannableStringBuilder(mBinding.meta.getText());
            boolean appended = false;
            if (!TextUtils.isEmpty(item.getEmbyGenres())) {
                sb.append("\n类型：").append(item.getEmbyGenres());
                appended = true;
            }
            if (!TextUtils.isEmpty(item.getEmbyDuration())) {
                sb.append("\n").append(getString(R.string.emby_duration, item.getEmbyDuration()));
                appended = true;
            }
            List<Vod.EmbyPerson> people = item.getEmbyPeople();
            if (people != null && !people.isEmpty()) {
                sb.append("\n演员：");
                boolean first = true;
                int shown = 0;
                for (Vod.EmbyPerson p : people) {
                    if (p == null || TextUtils.isEmpty(p.id) || TextUtils.isEmpty(p.name)) continue;
                    if (shown >= 12) break;
                    if (!first) sb.append(" / ");
                    first = false;
                    shown++;
                    int start = sb.length();
                    sb.append(p.name);
                    String sid = EmbyNavigator.serverIdOf(item.getId());
                    String pid = p.id;
                    String pname = p.name;
                    sb.setSpan(new ClickableSpan() {
                        @Override
                        public void onClick(@NonNull android.view.View view) {
                            try {
                                EmbyLibraryActivity.start(getActivity(), sid,
                                        EmbyLibraryActivity.MODE_PERSON, pid, pname);
                            } catch (Throwable t) {
                                Notify.show("打不开演员作品");
                            }
                        }
                    }, start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                appended = true;
            }
            List<Vod.Similar> similar = item.getEmbySimilar();
            if (similar != null && !similar.isEmpty()) {
                sb.append("\n同类推荐：");
                boolean first = true;
                for (Vod.Similar s : similar) {
                    if (s == null || TextUtils.isEmpty(s.id) || TextUtils.isEmpty(s.name)) continue;
                    if (!first) sb.append(" / ");
                    first = false;
                    int start = sb.length();
                    sb.append(s.name);
                    String sid = EmbyNavigator.serverIdOf(s.id);
                    String iid = EmbyNavigator.itemIdOf(s.id);
                    sb.setSpan(new ClickableSpan() {
                        @Override
                        public void onClick(@NonNull android.view.View view) {
                            EmbyNavigator.openDetail(getActivity(), sid, iid);
                        }
                    }, start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                appended = true;
            }
            if (appended) {
                mBinding.meta.setText(sb, TextView.BufferType.SPANNABLE);
                mBinding.meta.setLinkTextColor(android.graphics.Color.WHITE);
                CustomMovement.bind(mBinding.meta);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        // Emby 画质行：线路行下面，多版本各一档；单版本/失败不露脸
        refreshEmbyQualityRow();
    }

    /**
     * 详情页 Emby 收藏按钮：先置为未拉到态，异步 getItem 拿 UserData.IsFavorite，再刷新文案图标。
     * gen 防详情切换串台。顺手把 Played 也拉回来给「标记已看」按钮用。
     */
    private void bindEmbyFavBtn(String sid, String iid) {
        mEmbyFavState = null;
        mEmbyFavSid = sid;
        mEmbyFavIid = iid;
        mEmbyPlayedState = null;
        updateEmbyFavBtn();
        updateEmbyPlayedBtn();
        final int gen = ++mEmbyFavGen;
        Task.execute(() -> {
            Boolean fav = null;
            Boolean played = null;
            try {
                EmbyStore.EmbyServer s = EmbyStore.getServer(sid);
                if (s != null && s.isLogin()) {
                    EmbyItem ei = new EmbyApi(s.baseUrl, s.token, s.userId).getItem(iid);
                    if (ei != null) {
                        fav = ei.favorite;
                        played = ei.played;
                    }
                }
            } catch (Exception ignored) {
            }
            final Boolean r = fav;
            final Boolean rp = played;
            App.post(() -> {
                if (gen != mEmbyFavGen) return;
                mEmbyFavState = r;
                mEmbyPlayedState = rp;
                updateEmbyFavBtn();
                updateEmbyPlayedBtn();
            });
        });
    }

    private void updateEmbyFavBtn() {
        if (mBinding.embyFavBtn == null) return;
        boolean on = Boolean.TRUE.equals(mEmbyFavState);
        mBinding.embyFavBtn.setText(on ? R.string.emby_fav_on : R.string.emby_fav);
        mBinding.embyFavBtn.setIconResource(on ? R.drawable.ic_control_keep_on : R.drawable.ic_control_keep_off);
    }

    private void updateEmbyPlayedBtn() {
        if (mBinding.embyPlayedBtn == null) return;
        boolean on = Boolean.TRUE.equals(mEmbyPlayedState);
        mBinding.embyPlayedBtn.setText(on ? R.string.emby_unmark_played : R.string.emby_mark_played);
    }

    /** 详情页 Emby 标记已看按钮点击：切换已看/取消已看，失败给中文提示。 */
    private void onEmbyPlayedToggle() {
        if (TextUtils.isEmpty(mEmbyFavSid) || TextUtils.isEmpty(mEmbyFavIid)) return;
        final boolean toPlayed = !Boolean.TRUE.equals(mEmbyPlayedState);
        final int gen = mEmbyFavGen;
        final String sid = mEmbyFavSid;
        final String iid = mEmbyFavIid;
        Task.execute(() -> {
            boolean ok = false;
            try {
                EmbyStore.EmbyServer s = EmbyStore.getServer(sid);
                if (s != null && s.isLogin()) {
                    EmbyApi api = new EmbyApi(s.baseUrl, s.token, s.userId);
                    if (toPlayed) api.markPlayed(iid);
                    else api.unmarkPlayed(iid);
                    ok = true;
                }
            } catch (Exception ignored) {
            }
            final boolean r = ok;
            App.post(() -> {
                if (gen != mEmbyFavGen) return;
                if (r) {
                    mEmbyPlayedState = toPlayed;
                    updateEmbyPlayedBtn();
                    Notify.show(toPlayed ? getString(R.string.emby_action_mark_played_ok) : getString(R.string.emby_action_unmark_played_ok));
                } else {
                    Notify.show(getString(R.string.emby_action_fail));
                }
            });
        });
    }

    /** 详情页 Emby 收藏按钮点击：切换收藏/取消收藏，失败给中文提示。 */
    private void onEmbyFavToggle() {
        if (TextUtils.isEmpty(mEmbyFavSid) || TextUtils.isEmpty(mEmbyFavIid)) return;
        final boolean toFav = !Boolean.TRUE.equals(mEmbyFavState);
        final int gen = mEmbyFavGen;
        final String sid = mEmbyFavSid;
        final String iid = mEmbyFavIid;
        Task.execute(() -> {
            boolean ok = false;
            try {
                EmbyStore.EmbyServer s = EmbyStore.getServer(sid);
                if (s != null && s.isLogin()) {
                    EmbyApi api = new EmbyApi(s.baseUrl, s.token, s.userId);
                    if (toFav) api.favorite(iid);
                    else api.unfavorite(iid);
                    ok = true;
                }
            } catch (Exception ignored) {
            }
            final boolean r = ok;
            App.post(() -> {
                if (gen != mEmbyFavGen) return;
                if (r) {
                    mEmbyFavState = toFav;
                    updateEmbyFavBtn();
                    Notify.show(toFav ? getString(R.string.emby_action_fav_ok) : getString(R.string.emby_action_unfav_ok));
                } else {
                    Notify.show(getString(R.string.emby_action_fail));
                }
            });
        });
    }

    /**
     * 元数据到达后的方向纠正。
     * 判定优先用这次收到的真实尺寸：player().isPortrait() 读的是 PlayerManager 的缓存字段，
     * Activity 与 PlayerManager 是 Exo 的两个独立监听器，触发顺序不保证——先到的一方看到的
     * 可能是旧值/0x0（实证：onVideoSizeChanged 1080x1920 已到，playerView 被 pillarbox 压扁，
     * Activity 全程没转）。
     */
    private void checkOrientation(VideoSize freshSize) {
        boolean portrait = freshSize != null ? freshSize.height > freshSize.width : player().isPortrait();
        boolean landscape = freshSize != null ? freshSize.width > freshSize.height : player().isLandscape();
        if (isFullscreen() && !isRotate() && portrait) {
            setRequestedOrientation(PlaybackOrientation.getPortraitVideoSizeOrientation());
            setRotate(true);
        } else if (isFullscreen() && isRotate() && landscape) {
            setRequestedOrientation(PlaybackOrientation.getLandscapeVideoSizeOrientation());
            setRotate(false);
        } else if (!isFullscreen() && !mUserExitedFullscreenForThisVideo && mShortDramaMode) {
            // 只有短剧才自动进入全屏，普通视频不自动全屏
            enterFullscreen();
        }
    }

    private void updateVideoHeight() {
        if (isLand() || isFullscreen() || isInPictureInPictureMode()) return;
        if (mAudioStageVisible) return;
        if (mFrameHeight <= 0 || mFrameParams.height == mFrameHeight) return;
        logVideoFrame("updateVideoHeight restore from=" + mFrameParams.height + " to=" + mFrameHeight);
        mFrameParams.height = mFrameHeight;
        mBinding.video.setLayoutParams(mFrameParams);
    }

    private void logVideoFrame(String step) {
        if (mBinding == null) return;
        Log.d(SIZE_TAG, "video " + step
                + " frameParam=" + (mFrameParams == null ? "null" : mFrameParams.width + "x" + mFrameParams.height)
                + " frameHeight=" + mFrameHeight
                + " video=" + viewSize(mBinding.video)
                + " exo=" + viewSize(mBinding.exo)
                + " fullscreen=" + isFullscreen()
                + " land=" + isLand()
                + " scale=" + getScale()
                + " player=" + (service() == null ? "none" : player().getPlayerText()));
    }

    private static String viewSize(View view) {
        if (view == null) return "null";
        return view.getWidth() + "x" + view.getHeight();
    }

    private void checkEnded(boolean notify) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("audio-auto-next", "checkEnded notify=%s audioStage=%s adapter=%d", notify, mAudioStageVisible, mEpisodeAdapter == null ? -1 : mEpisodeAdapter.getItemCount());
        if (showKaraokeResultIfNeeded(notify ? KARAOKE_RESULT_ACTION_NEXT : KARAOKE_RESULT_ACTION_NEXT_SILENT)) return;
        if (mAudioStageVisible && playNextAudioPlaylistEntry()) return;
        checkNext(notify);
    }

    private boolean playNextAudioPlaylistEntry() {
        AudioPlaylistStore.Playlist playlist = AudioPlaylistStore.active();
        if (playlist == null || playlist.items == null || playlist.items.size() < 2) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("audio-auto-next", "playlist unavailable");
            return false;
        }
        int current = mAudioPlaylistCurrentIndex;
        if (current < 0) current = findAudioPlaylistIndex(getEpisode() == null ? "" : getEpisode().getUrl());
        if (current < 0) current = findAudioPlaylistIndexByMetadata();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("audio-auto-next", "playlist=%s size=%d current=%d", playlist.name, playlist.items.size(), current);
        if (current < 0 || current + 1 >= playlist.items.size()) return false;
        int nextIndex = current + 1;
        AudioPlaylistStore.Entry next = playlist.items.get(nextIndex);
        if (next == null || TextUtils.isEmpty(next.url)) return false;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("audio-auto-next", "play next index=%d name=%s", nextIndex, next.name);
        Episode episode = Episode.create(TextUtils.isEmpty(next.name) ? next.title : next.name, next.url);
        putAudioQueueMetadata(episode, next);
        mAudioPlaylistCurrentIndex = nextIndex;
        playAudioQueueEpisode(episode);
        return true;
    }

    private int findAudioPlaylistIndexByMetadata() {
        String track = getCurrentTrackMetadata();
        String title = splitCurrentTrack(track)[0];
        if (TextUtils.isEmpty(track) && TextUtils.isEmpty(title)) return -1;
        AudioPlaylistStore.Playlist playlist = AudioPlaylistStore.active();
        if (playlist == null || playlist.items == null) return -1;
        for (int i = 0; i < playlist.items.size(); i++) {
            AudioPlaylistStore.Entry entry = playlist.items.get(i);
            if (entry == null) continue;
            String name = Objects.toString(entry.name, "");
            String savedTitle = Objects.toString(entry.title, "");
            if (TextUtils.equals(name, track) || TextUtils.equals(savedTitle, title) || name.contains(title)) return i;
        }
        return -1;
    }

    private boolean hasNextEpisode() {
        return !getAdjacentEpisode(1).isSelected();
    }

    private void setTrackVisible() {
        updateDiscMenuTools();
        mBinding.control.action.text.setVisibility(player().haveTrack(C.TRACK_TYPE_TEXT) || player().isVod() ? View.VISIBLE : View.GONE);
        // 音轨/视轨已移至更多面板，显隐在打开面板时应用（见 onMore）
        applyActionButtonVisibility();
        updateAudioStageControls();
    }

    private void updateDiscMenuTools() {
        updateCustomButtonStates();
        MpvPlayer mpv = service() != null && isOwner()
                && player().getPlayer() instanceof MpvPlayer active ? active : null;
        if (mDiscMenuPlayer != mpv) {
            if (mDiscMenuPlayer != null) mDiscMenuPlayer.removeDiscMenuStateListener(mDiscMenuStateListener);
            mDiscMenuPlayer = mpv;
            if (mpv != null) mpv.addDiscMenuStateListener(mDiscMenuStateListener);
        }
        boolean visible = mpv != null && mpv.isDiscMenuActive() && !isLock() && !isInPictureInPictureMode()
                && !mAudioStageVisible && !isVisible(mBinding.control.getRoot());
        mBinding.discTools.getRoot().setVisibility(visible ? View.VISIBLE : View.GONE);
        mBinding.discTools.fullscreen.setImageResource(isFullscreen()
                ? R.drawable.ic_control_fullscreen_exit : R.drawable.ic_control_fullscreen);
        mBinding.discTools.fullscreen.setContentDescription(getString(isFullscreen()
                ? R.string.play_exit_fullscreen : R.string.play_fullscreen));
    }

    private void setTitleVisible() {
        applyActionButtonVisibility();
    }

    private void setSizeText() {
        String text = player().getSizeText();
        boolean hasTitle = !TextUtils.isEmpty(mBinding.control.title.getText());
        mBinding.control.title.setVisibility(hasTitle ? View.VISIBLE : View.INVISIBLE);
        mBinding.control.size.setText(text);
        mBinding.control.size.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private MediaMetadata buildMetadata() {
        String title = getAudioStageTitle();
        String artist = getAudioStageArtist(title);
        return PlayerManager.buildMetadata(title, artist, getEpisodeArtwork(getEpisode()));
    }

    private void setMetadata() {
        player().setMetadata(buildMetadata());
    }

    private void startFlow() {
        if (!PlayerSetting.isAutoChange()) return;
        if (!getSite().isChangeable()) return;
        if (isUseParse()) checkParse();
        else checkFlag();
    }

    private void checkParse() {
        int position = mParseAdapter.getPosition();
        boolean last = position == mParseAdapter.getItemCount() - 1;
        boolean pass = position == 0 || last;
        if (last) initParse();
        if (pass) checkFlag();
        else nextParse(position);
    }

    private void initParse() {
        if (mParseAdapter.isEmpty()) return;
        setParse(mParseAdapter.first());
    }

    private void checkFlag() {
        // 当前线路位只认适配器选中项，不认线路栏可见性：音频态下线路栏是隐藏的但线路还在，
        // 按可见性取 -1 会撞上「-1≠末位→nextFlag(-1)→第 1 条已选中早退」，自动换线/换源整链断掉。
        // 无线路（适配器为空）时维持 -1＝末位口径，直接走换源。
        int position = mFlagAdapter.getItemCount() == 0 ? -1 : mFlagAdapter.getPosition();
        if (position == mFlagAdapter.getItemCount() - 1) checkSearch(false);
        else nextFlag(position);
    }

    private void checkSearch(boolean force) {
        if (!force && !PlayerSetting.isAutoChange()) return;
        if (mQuickAdapter.isEmpty()) initSearch(mBinding.name.getText().toString(), true);
        else if (isAutoMode() || force) nextSite();
    }

    private void initSearch(String keyword, boolean auto) {
        setAutoMode(auto);
        setInitAuto(auto);
        revealManualSearch = !auto;
        startSearch(keyword);
    }

    private boolean isPass(Site item) {
        if (isAutoMode() && !item.isChangeable()) return false;
        return item.isSearchable();
    }

    private void startSearch(String keyword) {
        mQuickSearchKeyword = keyword;
        mQuickAdapter.clear();
        mBinding.quick.setVisibility(View.GONE);
        if (isQuickSearchVisible()) mQuickSearchDialog.clear();
        List<Site> sites = new ArrayList<>();
        for (Site item : VodConfig.get().getSites()) if (isPass(item)) sites.add(item);
        SiteHealthStore.sortSites(sites);
        mViewModel.searchContent(sites, keyword, true);
    }

    private void setSearch(Result result) {
        List<Vod> items = result.getList();
        items.removeIf(this::mismatch);
        mBinding.quick.setVisibility(View.GONE);
        mQuickAdapter.addAll(items);
        if (isQuickSearchVisible()) mQuickSearchDialog.addAll(items);
        if (revealManualSearch && !items.isEmpty()) revealManualSearch = false;
        if (isInitAuto() && PlayerSetting.isAutoChange()) nextSite();
        if (items.isEmpty()) return;
        App.removeCallbacks(mR4);
    }

    private boolean isQuickSearchVisible() {
        return mQuickSearchDialog != null && mQuickSearchDialog.isActive();
    }

    private boolean mismatch(Vod item) {
        if (getId().equals(item.getId())) return true;
        if (mBroken.contains(item.getId())) return true;
        String keyword = TextUtils.isEmpty(mQuickSearchKeyword) ? mBinding.name.getText().toString() : mQuickSearchKeyword;
        if (isAutoMode()) return !item.getName().equals(keyword);
        else return !item.getName().contains(keyword);
    }

    private void nextParse(int position) {
        Parse parse = mParseAdapter.get(position + 1);
        Notify.show(getString(R.string.play_switch_parse, parse.getName()));
        onItemClick(parse);
    }

    private void nextFlag(int position) {
        Flag flag = mFlagAdapter.get(position + 1);
        Notify.show(getString(R.string.play_switch_flag, flag.getFlag()));
        onItemClick(flag);
    }

    private void nextSite() {
        if (mQuickAdapter.isEmpty()) return;
        int position = mQuickAdapter.getBestPosition();
        Vod item = mQuickAdapter.get(position);
        Notify.show(getString(R.string.play_switch_site, item.getSiteName()));
        mQuickAdapter.remove(position);
        mBroken.add(getId());
        setInitAuto(false);
        applySearchArtwork(item);
        getDetail(item);
    }

    private void onPaused() {
        debugPlaybackControl("onPaused");
        controller().pause();
    }

    private void onPlay() {
        debugPlaybackControl("onPlay.before");
        if (mHistory != null && isEnded()) controller().seekTo(mHistory.getOpening());
        if (!player().isEmpty() && isIdle()) controller().prepare();
        controller().play();
        debugPlaybackControl("onPlay.after");
    }

    private boolean isFullscreen() {
        return fullscreen;
    }

    private void setFullscreen(boolean fullscreen) {
        Util.toggleFullscreen(this, this.fullscreen = fullscreen);
        updateDiscMenuTools();
    }

    // ---------- AI 语音控制（P1） ----------

    /** AI：下一集 */
    public void aiNext() {
        runOnUiThread(this::checkNext);
    }

    /** AI：上一集 */
    public void aiPrev() {
        runOnUiThread(this::checkPrev);
    }

    /** AI：暂停/播放切换 */
    public void aiTogglePlay() {
        runOnUiThread(() -> {
            if (player().isPlaying()) onPaused();
            else onPlay();
        });
    }

    /** AI：快进/快退（毫秒，正数快进负数快退） */
    public void aiSeekBy(long ms) {
        runOnUiThread(() -> {
            try {
                long pos = player().getPosition() + ms;
                if (pos < 0) pos = 0;
                long dur = player().getDuration();
                if (dur > 0 && pos > dur) pos = dur;
                controller().seekTo(pos);
            } catch (Exception ignored) {
            }
        });
    }

    /** AI：音量+ */
    public void aiVolumeUp() {
        runOnUiThread(() -> adjustVolume(1));
    }

    /** AI：音量- */
    public void aiVolumeDown() {
        runOnUiThread(() -> adjustVolume(-1));
    }

    private void adjustVolume(int delta) {
        try {
            android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
            int dir = delta > 0 ? android.media.AudioManager.ADJUST_RAISE
                    : android.media.AudioManager.ADJUST_LOWER;
            am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, dir, android.media.AudioManager.FLAG_SHOW_UI);
        } catch (Exception ignored) {
        }
    }

    /** AI：进入全屏 */
    public void aiEnterFullscreen() {
        runOnUiThread(() -> setFullscreen(true));
    }

    /** AI：退出全屏 */
    public void aiExitFullscreen() {
        runOnUiThread(() -> setFullscreen(false));
    }

    /** AI：倍速+ */
    public void aiSpeedUp() {
        runOnUiThread(() -> {
            try {
                player().addSpeed(0.25f);
                saveDefaultSpeed();
            } catch (Exception ignored) {
            }
        });
    }

    /** AI：倍速- */
    public void aiSpeedDown() {
        runOnUiThread(() -> {
            try {
                player().subSpeed(0.25f);
                saveDefaultSpeed();
            } catch (Exception ignored) {
            }
        });
    }

    /** AI：设置倍速 */
    public void aiSetSpeed(float speed) {
        runOnUiThread(() -> {
            try {
                player().setSpeed(speed);
                saveDefaultSpeed();
            } catch (Exception ignored) {
            }
        });
    }

    /** AI：屏幕亮度+ */
    public void aiBrightUp() {
        runOnUiThread(() -> adjustBrightness(0.1f));
    }

    /** AI：屏幕亮度- */
    public void aiBrightDown() {
        runOnUiThread(() -> adjustBrightness(-0.1f));
    }

    private void adjustBrightness(float delta) {
        try {
            android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
            float b = lp.screenBrightness;
            if (b < 0) b = 0.5f;
            b += delta;
            if (b > 1) b = 1;
            if (b < 0.05f) b = 0.05f;
            lp.screenBrightness = b;
            getWindow().setAttributes(lp);
        } catch (Exception ignored) {
        }
    }

    /** AI：弹幕开关（mode: 1=开，0=关，-1=切换） */
    public void aiDanmakuSet(int mode) {
        runOnUiThread(() -> {
            try {
                boolean show = mode == 1 || (mode == -1 && !com.fongmi.android.tv.setting.DanmakuSetting.isShow());
                com.fongmi.android.tv.setting.DanmakuSetting.putShow(show);
                checkDanmakuImg();
                showDanmaku();
            } catch (Exception ignored) {
            }
        });
    }

    /** AI：字幕开关（mode: 1=开，0=关，-1=切换） */
    public void aiSubtitleSet(int mode) {
        runOnUiThread(() -> {
            try {
                android.view.View sv = mBinding.exo.getSubtitleView();
                if (sv == null) return;
                int vis = sv.getVisibility();
                boolean show = mode == 1 || (mode == -1 && vis != android.view.View.VISIBLE);
                sv.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
            } catch (Exception ignored) {
            }
        });
    }

    /** AI：本地 AI 字幕开关（mode: 1=开，0=关，-1=切换） */
    public void aiAiSubtitleSet(int mode) {
        runOnUiThread(() -> {
            try {
                com.fongmi.android.tv.ai.asr.AiAsrController controller =
                        com.fongmi.android.tv.ai.asr.AiAsrController.get();
                // 绑定字幕 View 与 MPV 播放信息（MPV 影子采集需要）
                controller.bindSubtitleView(mBinding.exo.getSubtitleView());
                bindAiAsrMpvProvider();
                boolean currentlyOn = controller.isEnabled(this);
                boolean target = mode == 1 || (mode == -1 && !currentlyOn);
                if (mode == 0) target = false;
                if (target == currentlyOn) return;
                if (target) {
                    controller.enable(this, new com.fongmi.android.tv.ai.asr.AiAsrController.ToggleCallback() {
                        @Override public void onPreparing(String msg) {
                            com.fongmi.android.tv.utils.Notify.show(msg);
                        }
                        @Override public void onEnabled() {
                            com.fongmi.android.tv.utils.Notify.show("AI 本地字幕已开启");
                        }
                        @Override public void onDisabled() {}
                        @Override public void onError(String msg) {
                            com.fongmi.android.tv.utils.Notify.show(msg);
                        }
                    });
                } else {
                    controller.disable(this);
                    com.fongmi.android.tv.utils.Notify.show("AI 本地字幕已关闭");
                }
            } catch (Exception ignored) {
            }
        });
    }

    /** AI：播放第N集（1-based） */
    public void aiPlayEpisode(int n) {
        runOnUiThread(() -> {
            try {
                com.fongmi.android.tv.bean.Flag flag = getFlag();
                if (flag == null) return;
                java.util.List<com.fongmi.android.tv.bean.Episode> items = flag.getEpisodes();
                int idx = n - 1;
                if (idx < 0 || idx >= items.size()) return;
                onItemClick(items.get(idx));
            } catch (Exception ignored) {
            }
        });
    }

    /** AI：投屏 */
    public void aiCast() {
        runOnUiThread(() -> {
            try {
                onCast();
            } catch (Exception ignored) {
            }
        });
    }

    /** AI：下载本集 */
    public void aiDownloadEpisode() {
        runOnUiThread(() -> {
            try {
                onDownload();
            } catch (Exception ignored) {
            }
        });
    }

    // ---------- AI 剧情问答（P3） ----------

    /** AI：当前影片名（详情未加载时回退 intent 里的名字） */
    public String aiGetVodName() {
        try {
            if (mAiVod != null && !TextUtils.isEmpty(mAiVod.getName())) return mAiVod.getName();
        } catch (Exception ignored) {
        }
        return getName();
    }

    /** AI：当前影片元数据文本，供拼 system prompt */
    public String aiGetVodMeta() {
        StringBuilder sb = new StringBuilder();
        try {
            Vod v = mAiVod;
            if (v == null) {
                appendAiMeta(sb, "片名", getName());
                appendAiMeta(sb, "简介", getContent());
                return sb.toString();
            }
            appendAiMeta(sb, "片名", v.getName());
            try {
                Episode ep = getEpisode();
                if (ep != null) appendAiMeta(sb, "当前集", ep.getName());
            } catch (Exception ignored) {
            }
            appendAiMeta(sb, "类型", v.getTypeName());
            appendAiMeta(sb, "年份", v.getYear());
            appendAiMeta(sb, "地区", v.getArea());
            appendAiMeta(sb, "导演", v.getDirector());
            appendAiMeta(sb, "演员", v.getActor());
            appendAiMeta(sb, "简介", v.getContent());
        } catch (Exception ignored) {
        }
        return sb.toString();
    }

    private void appendAiMeta(StringBuilder sb, String label, String value) {
        if (TextUtils.isEmpty(value)) return;
        if (sb.length() > 0) sb.append("\n");
        sb.append(label).append("：").append(value);
    }

    private boolean isInitAuto() {
        return initAuto;
    }

    private void setInitAuto(boolean initAuto) {
        this.initAuto = initAuto;
    }

    private boolean isAutoMode() {
        return autoMode;
    }

    private void setAutoMode(boolean autoMode) {
        this.autoMode = autoMode;
    }

    public boolean isUseParse() {
        return useParse;
    }

    public void setUseParse(boolean useParse) {
        this.useParse = useParse;
    }

    public boolean isRotate() {
        return rotate;
    }

    public void setRotate(boolean rotate) {
        this.rotate = rotate;
        if (fullscreen && !rotate) setPadding(mBinding.control.getRoot());
        else noPadding(mBinding.control.getRoot());
    }

    private void notifyItemChanged(RecyclerView view, RecyclerView.Adapter<?> adapter) {
        view.post(() -> adapter.notifyItemRangeChanged(0, adapter.getItemCount()));
    }

    private void scrollToPosition(RecyclerView view, int position) {
        view.post(() -> view.scrollToPosition(position));
    }

    @Override
    public void onCasted() {
        clearLyrics();
        clearKaraokeState();
        player().stop();
    }

    @Override
    public void onScale(int tag) {
        mKeyDown.resetScale();
        setScale(tag);
    }

    @Override
    public void onEpisodeColumn(int column) {
        PlayerSetting.putEpisodeColumn(column);
        refreshEpisodeTitles();
    }

    @Override
    public void onCompactEpisodeTitleChanged() {
        refreshEpisodeTitles();
    }

    private void refreshEpisodeTitles() {
        if (mEpisodeAdapter == null) return;
        if (mFlagAdapter == null || mFlagAdapter.isEmpty()) {
            updateEpisodeSpan(mEpisodeAdapter.getItems());
            mEpisodeAdapter.notifyItemRangeChanged(0, mEpisodeAdapter.getItemCount());
        } else {
            Flag flag = getFlag();
            if (flag != null) setEpisodeItems(flag.getEpisodes());
        }
        scrollEpisodeToSelected();
        mBinding.episode.post(this::updateEpisodeViewportHeight);
    }

    @Override
    public void onParse(Parse item) {
        onItemClick(item);
    }

    @Override
    public boolean isLongPressSpeedEnabled() {
        // 播放设置「长按倍速」只认用户明确选过的值：选过「关」(0) 才关，没选过保持原有长按加速
        android.content.SharedPreferences prefs = getSharedPreferences("xingchen", MODE_PRIVATE);
        return !prefs.contains("longpress_speed") || prefs.getFloat("longpress_speed", 0f) > 0f;
    }

    private float longPressSpeed() {
        float lpSpeed = getSharedPreferences("xingchen", MODE_PRIVATE).getFloat("longpress_speed", 0f);
        return lpSpeed > 0f ? lpSpeed : PlayerSetting.getSpeed();
    }

    @Override
    public void onSpeedUp() {
        if (!player().isPlaying()) return;
        mSpeedUpOriginSpeed = player().getSpeed();
        mSpeedUpActive = true;
        mBinding.widget.speed.setVisibility(View.VISIBLE);
        mBinding.widget.speed.startAnimation(ResUtil.getAnim(R.anim.forward));
        mBinding.control.action.speed.setText(player().setSpeed(longPressSpeed()));
    }

    @Override
    public void onSpeedEnd() {
        mBinding.widget.speed.clearAnimation();
        if (mSpeedUpActive) {
            mSpeedUpActive = false;
            mBinding.control.action.speed.setText(player().setSpeed(mSpeedUpOriginSpeed));
        }
        mHistory.setSpeed(player().getSpeed());
    }

    @Override
    public void onBright(int progress) {
        mBinding.widget.bright.setVisibility(View.VISIBLE);
        mBinding.widget.brightProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_low);
        else if (progress < 70) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_medium);
        else mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_high);
    }

    @Override
    public void onVolume(int progress) {
        mBinding.widget.volume.setVisibility(View.VISIBLE);
        mBinding.widget.volumeProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_low);
        else if (progress < 70) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_medium);
        else mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_high);
    }

    @Override
    public void onFlingUp() {
        if (getEpisodeCount() == 1) onRefresh();
        else checkNext();
    }

    @Override
    public void onFlingDown() {
        if (getEpisodeCount() == 1) onRefresh();
        else checkPrev();
    }

    // ===== 短剧滑动切集预览 =====

    private void createSwitchCover() {
        FrameLayout cover = new FrameLayout(this);
        cover.setBackgroundColor(Color.BLACK);
        cover.setVisibility(View.GONE);
        mSwitchCoverImage = new ImageView(this);
        mSwitchCoverImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.addView(mSwitchCoverImage, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mSwitchCoverLabel = new TextView(this);
        mSwitchCoverLabel.setTextColor(Color.WHITE);
        mSwitchCoverLabel.setTextSize(15);
        mSwitchCoverLabel.setGravity(Gravity.CENTER);
        mSwitchCoverLabel.setShadowLayer(8, 2, 2, Color.BLACK);
        mSwitchCoverLabel.setVisibility(View.GONE);
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        labelParams.bottomMargin = ResUtil.dp2px(36);
        cover.addView(mSwitchCoverLabel, labelParams);
        // 插在视频面正上方：控件、进度（含网速）、OSD 都在它之上，预览只盖视频区
        ViewGroup parent = (ViewGroup) mBinding.exo.getParent();
        parent.addView(cover, parent.indexOfChild(mBinding.exo) + 1, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mSwitchCover = cover;
    }

    // 预览层撤除：切集成立（getPlayer）、首帧到达、报错、拖动未吸附时都走这里
    private void hideSwitchCover() {
        mSwitchCoverDir = 0;
        if (mSwitchCover != null) {
            mSwitchCover.setVisibility(View.GONE);
            mSwitchCover.setTranslationY(0);
        }
        if (mSwitchCoverImage != null) mSwitchCoverImage.setImageDrawable(null);
    }

    @Override
    protected void onVideoFirstFrame() {
        hideSwitchCover();
    }

    @Override
    public boolean isPageSwipeEnabled() {
        return mShortDramaMode && isFullscreen();
    }

    @Override
    public boolean canPageUp() {
        return hasAdjacentEpisode(1);
    }

    @Override
    public boolean canPageDown() {
        return hasAdjacentEpisode(-1);
    }

    @Override
    public void onPageDrag(float dy) {
        positionSwitchPreview(dy);
    }

    @Override
    public void onPageCommit(boolean up) {
        // 跟手预览到切集成立为止：切集链同步走到 getPlayer 时立即撤层，下一集直接起播出画
        if (up) onFlingUp();
        else onFlingDown();
        // 视频面归位
        mBinding.exo.setTranslationY(0);
    }

    @Override
    public void onPageDragEnd(boolean committed) {
        if (!committed) hideSwitchCover();
    }

    private void positionSwitchPreview(float dy) {
        if (mSwitchCover == null) return;
        int height = mBinding.video.getHeight();
        if (height <= 0) return;
        if (dy == 0) {
            // 回弹末帧：预览按当前方向停回屏外，等手势收尾撤除
            if (mSwitchCoverDir != 0) mSwitchCover.setTranslationY((float) mSwitchCoverDir * height);
            return;
        }
        int dir = dy < 0 ? 1 : -1;
        // 无相邻集的方向只有阻尼回弹，不露预览（getAdjacentEpisode 会钳到本集，露出来是假相邻）
        if (dir > 0 ? !canPageUp() : !canPageDown()) return;
        if (mSwitchCoverDir != dir) {
            mSwitchCoverDir = dir;
            ImgUtil.load(getName(), getPic(), mSwitchCoverImage, true);
            mSwitchCoverLabel.setText(getAdjacentEpisode(dir).getDisplayName());
            mSwitchCoverLabel.setVisibility(View.VISIBLE);
        }
        mSwitchCover.setVisibility(View.VISIBLE);
        mSwitchCover.setTranslationY(dy + dir * height);
    }

    @Override
    public void onSeeking(long time) {
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        mBinding.widget.time.setText(player().getPositionTime(time));
        mBinding.widget.seek.setVisibility(View.VISIBLE);
        hideProgress();
    }

    @Override
    public void onSeekEnd(long time) {
        seekTo(time);
    }

    @Override
    public void onSingleTap() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else showControl();
    }

    @Override
    public void onDoubleTap() {
        if (isLock()) return;
        if (!isFullscreen()) {
            enterFullscreen();
        } else if (player().isPlaying()) {
            showControl();
            onPaused();
        } else {
            hideControl();
            onPlay();
        }
    }

    @Override
    public void onTouchEnd() {
        mBinding.widget.seek.setVisibility(View.GONE);
        mBinding.widget.speed.setVisibility(View.GONE);
        mBinding.widget.bright.setVisibility(View.GONE);
        mBinding.widget.volume.setVisibility(View.GONE);
    }

    @Override
    public void onShare(CharSequence title) {
        PlayerHelper.share(this, player().getUrl(), player().getHeaders(), title);
        setRedirect(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == 1001) PlayerHelper.onExternalResult(data, service()::dispatchNext, controller()::seekTo);
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        String mode = getBgPipMode();
        if (!"pip".equals(mode)) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            preparePiP("userLeaveHint");
        } else {
            requestPiP("userLeaveHint");
        }
    }

    @Override
    public void onBackPressed() {
        String mode = getBgPipMode();
        if ("pip".equals(mode)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (preparePiP("backPress")) return;
            } else {
                if (requestPiP("backPress")) return;
            }
        } else if ("bg".equals(mode)) {
            moveTaskToBack(true);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onPictureInPictureRequested() {
        String mode = getSharedPreferences("xingchen", MODE_PRIVATE).getString("bg_pip_mode", "off");
        if (!"pip".equals(mode)) return false;
        return requestPiP("systemRequest");
    }

    private String getBgPipMode() {
        return getSharedPreferences("xingchen", MODE_PRIVATE).getString("bg_pip_mode", "off");
    }

    private boolean preparePiP(String reason) {
        if (isRedirect() || isPlaybackExiting()) return false;
        if (syncPiPForPlaybackMode()) return false;
        if (service() == null || !player().haveTrack(C.TRACK_TYPE_VIDEO)) return false;
        mPiP.update(this, player().getVideoWidth(), player().getVideoHeight(), getScale());
        return true;
    }

    private boolean requestPiP(String reason) {
        if (!preparePiP(reason)) return false;
        if (isLock()) App.post(this::onLock, 500);
        return enterPiP(reason);
    }

    private boolean enterPiP(String reason) {
        if (syncPiPForPlaybackMode()) return false;
        if (service() == null || !player().haveTrack(C.TRACK_TYPE_VIDEO)) return false;
        return mPiP.enter(this, player().getVideoWidth(), player().getVideoHeight(), getScale());
    }

    private boolean syncPiPForPlaybackMode() {
        boolean audioMode = isAudioBackgroundMode();
        if (mPiP != null) mPiP.setAudioMode(this, audioMode);
        return audioMode;
    }

    private boolean isAudioBackgroundMode() {
        return mAudioStageVisible || isAudioOnly();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (!isFullscreen()) setVideoView(isInPictureInPictureMode);
        if (isInPictureInPictureMode) {
            hideControl();
            hideDanmaku();
            hideSheet();
        } else {
            showDanmaku();
            restoreContextWall();
            if (isStop()) finish();
        }
        updateDiscMenuTools();
    }

    @Override
    protected void onResume() {
        super.onResume();
        restoreContextWall();
        if (mAudioStageVisible) restorePlaybackArtwork();
        if (mAudioStageVisible) applyAudioBackground();
        syncLyricsPlaybackState();
        syncKaraokePosition();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (shouldRecreateAudioStageForOrientation(newConfig)) {
            setAudioOnly(true);
            recreate();
            return;
        }
        syncFullscreenForOrientation(newConfig.orientation);
        setupCustomActionButtons();
        if (isFullscreen()) Util.hideSystemUI(this);
        refreshControlForOrientation();
    }

    // 旋转不重建页面，控件层没人重算会定格在旋转前的方向套上：底部行与右栏竖屏专属件（⚙/竖排选集/竖排投屏）按实际方向重判一次
    // 只重算显隐，不走 showControl（它会重播渐入动画并重置自动隐藏计时）；控件没露面时跳过，下次 showControl 现算即是新方向
    private void refreshControlForOrientation() {
        if (service() == null || player() == null || isInPictureInPictureMode()) return;
        if (mBinding.control.getRoot().getVisibility() != View.VISIBLE) return;
        applyActionButtonVisibility();
        boolean isVerticalFullscreen = isFullscreen() && !isActualLand();
        mBinding.control.setting.setVisibility(isVerticalFullscreen && !isLock() ? View.VISIBLE : View.GONE);
        mBinding.control.right.episodesVertical.setVisibility(isVerticalFullscreen && !isLock() && mEpisodeAdapter != null && getEpisodeCount() >= 2 ? View.VISIBLE : View.GONE);
        mBinding.control.right.castVertical.setVisibility(isVerticalFullscreen && !isLock() ? View.VISIBLE : View.GONE);
        updateCommentEntries();
    }

    private void syncFullscreenForOrientation(int orientation) {
        if (!isAutoRotate() || !isPort()) {
            deferredFullscreenOrientation = Configuration.ORIENTATION_UNDEFINED;
            return;
        }
        if (service() == null) {
            deferredFullscreenOrientation = orientation;
            SpiderDebug.log("video-flow", "fullscreen orientation deferred orientation=%d reason=player-not-ready", orientation);
            return;
        }
        deferredFullscreenOrientation = Configuration.ORIENTATION_UNDEFINED;
        if (orientation == Configuration.ORIENTATION_PORTRAIT && !isRotate() && !isLock()) exitFullscreen();
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) enterFullscreen();
    }

    private void applyDeferredFullscreenOrientation() {
        int orientation = deferredFullscreenOrientation;
        if (orientation == Configuration.ORIENTATION_UNDEFINED) return;
        SpiderDebug.log("video-flow", "fullscreen orientation resume orientation=%d", orientation);
        syncFullscreenForOrientation(orientation);
    }

    private boolean shouldRecreateAudioStageForOrientation(Configuration config) {
        if (!mAudioStageVisible || config == null) return false;
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) return isPort();
        if (config.orientation == Configuration.ORIENTATION_PORTRAIT) return isLand();
        return false;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (isFullscreen() && hasFocus) Util.hideSystemUI(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mClock.stop().start();
        if (mOsd != null) mOsd.start();
        setAudioOnly(false);
        setStop(false);
        if (service() != null) refreshLyrics();
        syncLyricsPlaybackState();
        syncKaraokePosition();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mOsd != null) mOsd.stop();
        if (mKaraoke != null) mKaraoke.clear();
        if (PlayerSetting.isBackgroundOff()) mClock.stop();
        if (!isAudioOnly()) setStop(true);
    }

    @Override
    protected void onBackInvoked() {
        if (hasLutQuick() && mBinding.lutQuick.hideIfVisible()) {
            return;
        } else if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isFullscreen() && !isLock()) {
            exitFullscreen();
        } else if (!isLock()) {
            if (showKaraokeResultIfNeeded(KARAOKE_RESULT_ACTION_SYSTEM_BACK)) return;
            finishVideoPlaybackFromSystemBack();
        }
    }

    private void finishVideoPlaybackFromSystemBack() {
        mViewModel.stopSearch();
        saveHistory(true);
        embyOnExit();
        markPlaybackExiting();
        stopPlayback();
        if (isTaskRoot()) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        super.onBackInvoked();
    }

    @Override
    protected void onDestroy() {
        if (sPlayerOwner == this) {
            sPlayerOwner = null;
            hasPlayer = false;
        }
        // Emby 画质行选择只活在本次详情会话，销毁时清掉
        try {
            EmbySpider.clearPreferredSources();
        } catch (Throwable ignored) {
        }
        // 释放 AI 本地字幕
        try {
            com.fongmi.android.tv.ai.asr.AiAsrController.get().onPlayerRelease(this);
            com.fongmi.android.tv.ai.asr.AiAsrController.get().unbind();
        } catch (Exception ignored) {}
        if (mCustomButtonPlayer != null) {
            mCustomButtonPlayer.removeCustomButtonStateListener(mCustomButtonStateListener);
            mCustomButtonPlayer = null;
        }
        if (mDiscMenuPlayer != null) {
            mDiscMenuPlayer.removeDiscMenuStateListener(mDiscMenuStateListener);
            mDiscMenuPlayer = null;
        }
        dismissKaraokeResultDialogForRecreation();
        mLyricsSearchSeq++;
        cancelKaraokePitchGeneration(false);
        dismissLyricsResultDialog();
        stopAudioCoverRotation();
        if (mLyrics != null) {
            mLyrics.setListener(null);
            mLyrics.release();
        }
        if (mKaraoke != null) mKaraoke.release();
        mClock.release();
        saveHistory(true);
        embyOnExit();
        Timer.get().reset();
        DanmakuApi.cancel();
        mHongguoExecutor.shutdownNow();
        RefreshEvent.keep();
        mNextPreloader.clear();
        App.removeCallbacks(mR1, mR2, mR3, mR4);
        if (mOsd != null) mOsd.release();
        mViewModel.getResult().removeObserver(mObserveDetail);
        mViewModel.getSearch().removeObserver(mObserveSearch);
        SiteHealthStore.flush();
        super.onDestroy();
    }

    private void dismissKaraokeResultDialogForRecreation() {
        if (!isChangingConfigurations() || mKaraokeResultDialog == null) return;
        mSuppressKaraokeResultAction = true;
        mKaraokeResultDialog.dismiss();
        mSuppressKaraokeResultAction = false;
        mKaraokeResultDialog = null;
        SpiderDebug.log("karaoke-result", "dismiss old window for configuration change");
    }
}