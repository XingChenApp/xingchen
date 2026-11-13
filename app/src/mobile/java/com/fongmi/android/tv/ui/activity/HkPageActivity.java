package com.fongmi.android.tv.ui.activity;
import com.fongmi.android.tv.utils.ToastUtil;

import android.app.Activity;
import android.app.Dialog;
import androidx.appcompat.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.hk.HkDetail;
import com.fongmi.android.tv.api.hk.HkDetailItem;
import com.fongmi.android.tv.api.hk.HkChapters;
import com.fongmi.android.tv.api.hk.HkEpisodeQualifier;
import com.fongmi.android.tv.api.hk.HkErrorUtil;
import com.fongmi.android.tv.api.hk.HkFavoriteStore;
import com.fongmi.android.tv.api.hk.HkItem;
import com.fongmi.android.tv.api.hk.HkJsRuntime;
import com.fongmi.android.tv.api.hk.HkLazyRuleParser;
import com.fongmi.android.tv.api.hk.HkLoading;
import com.fongmi.android.tv.api.hk.HkPlay;
import com.fongmi.android.tv.read.ReadingRouter;
import com.fongmi.android.tv.read.ReadingSession;
import com.fongmi.android.tv.read.ReadingStore;
import com.fongmi.android.tv.api.hk.HkRouter;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.api.hk.HkRuleManager;
import com.fongmi.android.tv.api.hk.SearchResult;
import com.fongmi.android.tv.databinding.ActivityHkBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.XcFadeTransition;
import com.xingchen.tv.theme.FrostedGlass;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.fongmi.android.tv.ui.hk.HkAdUrlBlocker;
import com.fongmi.android.tv.ui.hk.HkDetailBridge;
import com.fongmi.android.tv.ui.hk.HkDialogs;
import com.fongmi.android.tv.ui.hk.HkDynamicBgView;
import com.fongmi.android.tv.ui.hk.HkGlassDrawable;
import com.fongmi.android.tv.ui.hk.HkImageUrlMap;
import com.fongmi.android.tv.ui.hk.HkPressFeedback;
import com.fongmi.android.tv.ui.hk.HkSearchActivity;
import com.fongmi.android.tv.ui.hk.HkWebViewHolder;
import com.fongmi.android.tv.ui.hk.HkWebkitHolder;
import com.fongmi.android.tv.ui.hk.HkX5Extra;
import java.util.Arrays;

/**
 * 海阔小程序单页（M3）：V1 规则列表 / V2 分类+列表 / V3 搜索结果 / V4 详情，三视图栈式切换。
 * 点条目 → V4 海阔详情（标题/封面/简介/线路/选集）；点选集 → VideoActivity 直接播放。
 */
public class HkPageActivity extends BaseActivity {

    private static final int V_RULES = 0;
    private static final int V_CONTENT = 1;
    private static final int V_SEARCH = 2;
    private static final int V_DETAIL = 3;
    private static final int REQ_IMPORT_FILE = 0x101;
    /** 8.83 JSEngine.startQRScanPage 的 startActivityForResult 请求码（8.83 用 0x961）。 */
    private static final int REQ_QR_SCAN = 0x961;
    private static final String PREFS = "xingchen";
    private static final String KEY_HIST = "hk_search_history";
    private static final String KEY_LAST_CLIP = "hk_last_clip_prompt";
    /** F-12 在线更新：规则来源地址（云口令）按标题落盘，供日后按来源拉新版比 version。 */
    private static final String KEY_RULE_SRC_PREFIX = "hk_rule_source_";
    private static final String KEY_UPDATE_LAST_CHECK = "hk_rule_update_last_check";
    private boolean ruleUpdateChecking = false;
    private static final String KEY_RULE_SPAN = "hk_rule_span_count";
    /** 自定义分组：有序分组名列表 / 规则标题→自定义分组（""=显式无分组）/ 自定义顺序（规则标题有序列表） */
    private static final String KEY_CUSTOM_GROUPS = "hk_custom_groups";
    private static final String KEY_RULE_GROUPS = "hk_rule_group_map";
    private static final String KEY_RULE_ORDER = "hk_rule_order";
    /** 导入时分组选择器的标准分组（8.83 默认分组名，去掉①②序号）。 */
    private static final String[] STD_GROUPS = {"聚合", "视频", "资讯", "图集", "漫画", "其它", "工具", "音频"};
    /** 8.83 ArticleColTypeEnum 的全部 col_type（表单编辑显示样式选择器用）。 */
    private static final String[] COL_TYPE_OPTIONS = {
            "movie_3", "movie_2", "movie_1", "movie_1_left_pic", "movie_1_vertical_pic",
            "movie_1_vertical_pic_blur", "movie_3_marquee",
            "pic_3", "pic_2", "pic_1", "pic_1_full", "pic_1_center", "pic_3_square", "pic_1_card",
            "card_pic_3", "card_pic_2", "card_pic_2_2", "card_pic_2_2_left", "card_pic_3_center", "card_pic_1",
            "text_3", "text_2", "text_1", "text_4", "text_5", "text_center_1", "text_icon",
            "icon_4", "icon_3_fill", "icon_3_round_fill", "icon_2", "icon_2_round", "icon_5",
            "icon_4_card", "icon_5_no_crop", "icon_round_4", "icon_round_small_4",
            "icon_small_3", "icon_small_4", "icon_1_left_pic", "icon_1_search",
            "long_text", "rich_text", "line", "line_blank", "blank_block", "big_blank_block",
            "big_big_blank_block", "header", "footer", "input", "scroll_button", "flex_button", "avatar"
    };

    public static void start(Activity activity) {
        // 裸启动，不带任何清栈旗：照 8.83 原版结构，小程序列表就是主任务里的
        // 普通页（原版长在 MainActivity 内）；规则页由 HkListActivity.startRule
        // 以 ruleOpenBy=2「新页面」形态同任务压在本页之上（fix5 的独立任务窗口
        // 形态已整件退役，账 hk-task-single-20261103 订正节）。落点由同任务栈
        // 保证：退出海阔走返回链逐层弹栈、规则页随之 finish，再进海阔时本页
        // 之上不可能压着已退出的规则页；JS backToHome 的清栈口径（子页与搜索
        // 页自退）亦不变。此前一修的 CLEAR_TOP、二修的 singleTask＋onNewIntent
        // 复刻都是往主任务里打的补丁，早已退役、不复活。
        Intent intent = new Intent(activity, HkPageActivity.class);
        activity.startActivity(intent);
    }

    /** 带分组筛选的小程序管理页入口（hiker://rulesGroup@分组 路由用）。 */
    public static void startWithGroup(Activity activity, String group) {
        Intent intent = new Intent(activity, HkPageActivity.class);
        if (!TextUtils.isEmpty(group)) intent.putExtra("rule_group", group);
        activity.startActivity(intent);
    }

    private ActivityHkBinding binding;
    private final Deque<Integer> stack = new ArrayDeque<>();
    private RuleAdapter ruleAdapter;
    private GridLayoutManager ruleGrid;
    private final List<HkRule> rules = new ArrayList<>();
    /** 本页内存列表对应的 HkRuleManager 落盘戳；戳变了（别处导入/删改）才整表重读。 */
    private long rulesStampSeen = HkRuleManager.saveStamp();
    /** 小程序列表：名称排序开关 / 搜索关键字 / 管理模式（编辑按钮切换）/ 分组筛选（""=全部） */
    private boolean ruleSortByName = false;
    private String ruleKeyword = "";
    private boolean ruleManageMode = false;
    /** 多选模式（长按「小程序」标题进入）：勾选卡片批量删除。 */
    private boolean ruleSelectMode = false;
    private final java.util.Set<String> ruleSelectedTitles = new java.util.HashSet<>();
    private String ruleGroup = "";
    /** 列表页「管理规则-编辑规则」直达（extra edit_rule_title）：规则加载完后打开该规则的表单编辑器。 */
    private String pendingEditRuleTitle;
    /** 自定义分组（本地 prefs，不改 rule.json）：有序分组名 / 标题→分组覆盖 / 自定义顺序（null=未自定义） */
    private final List<String> customGroups = new ArrayList<>();
    private final Map<String, String> ruleGroupMap = new HashMap<>();
    // 分组存档解析失败标记：置位时保存口拒绝覆盖 prefs 原文（防按空分组把用户自建分组冲掉）
    private boolean customGroupsLoadFailed;
    private boolean ruleGroupsLoadFailed;
    private List<String> customOrder = null;
    private ItemTouchHelper ruleTouchHelper;
    // 编辑模式拖拽到分组 pills：拖拽状态（排序快照用于投放分组时回滚换位）
    private RecyclerView.ViewHolder ruleDragHolder;
    private int ruleDragFromPos = RecyclerView.NO_POSITION;
    private String ruleDragTitle;
    private List<String> ruleDragOrderSnapshot;
    private ChipAdapter groupChipAdapter;
    private List<String[]> groupPairs;
    private int groupDropTargetPos = -1;

    private ContentAdapter contentAdapter;
    /** 详情页选集选中跟踪：最近一次点播的集（url 归一化做 key），选中态金底深字（定稿图7）。 */
    private String lastEpKey = null;
    private final List<HkItem> videos = new ArrayList<>();
    private GridLayoutManager contentGrid;
    private HkRule currentRule;
    /** 内容页标题（顶栏已删除，用字段保存子列表栈恢复用）。 */
    private String contentTitle = "";
    private final List<HkRule> miniappRules = new ArrayList<>();
    private HkRouter router;
    /** 实际抓取的页面 URL（图片自动 Referer 用，对齐 8.83 GlideUtil.getGlideUrl 语义）。
     * 规则静态 URL 常为 hiker://empty## 模板，与图片域名对不上会导致防盗链 403；
     * loadContent/detail 后取引擎最后一次 fetch 的真实 URL。volatile：后台线程写、UI 线程读。 */
    private volatile String hkPageUrl;
    /** preRule loading 弹窗（8.83 showLoading/hideLoading 等价）：规则 JS 调 showLoading("正在获取域名，请稍候") 时弹出。 */
    private android.app.Dialog hkLoadingDialog;
    private TextView hkLoadingText;
    /** 8.83 MainActivity.isOnPause 等价：onLoading 开头 isOnPause 直接 return，后台时不弹框。 */
    private boolean hkPaused;
    /** 8.83 canTouchUI 等价：只响应当前规则的 showLoading，避免切规则后旧规则 JS 弹框。 */
    private final HkLoading.Listener hkLoadingListener = new HkLoading.Listener() {
        @Override
        public void onShowLoading(String ruleTitle, String text) {
            if (hkPaused) return;
            if (currentRule == null || ruleTitle == null || !ruleTitle.equals(currentRule.getTitle())) return;
            showHkLoading(text);
        }

        @Override
        public void onHideLoading(String ruleTitle) {
            if (hkPaused) return;
            if (currentRule == null || ruleTitle == null || !ruleTitle.equals(currentRule.getTitle())) return;
            hideHkLoading();
        }
    };
    private String cls = "", area = "", year = "", sort = "";
    /** 每行分类 tab 当前选中的名字（8.83 ArticleList.setPic 等价，用于选中高亮）。 */
    private String clsName = "", areaName = "", yearName = "", sortName = "";
    private int page = 1;
    private boolean loading, noMore;
    private final Map<String, Integer> scrollMem = new HashMap<>();

    private VideoAdapter searchAdapter;
    private final List<SearchResult> searchResults = new ArrayList<>();
    /** 跨小程序搜索的结果来源标注（SearchResult 保持 8.83 原版纯净，来源放外部映射）。 */
    private final Map<SearchResult, String> searchSrcRules =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private String keyword = "";
    private int searchPage = 1;
    private boolean searchLoading, searchNoMore;
    /** 8.83 fileSelect://：文件选择后的 JS 回调与条目位置（对齐 ArticleListFragment.fileSelectJs/fileSelectPosition）。 */
    private String fileSelectJs = "";
    private int fileSelectPosition = -1;
    private static final int REQ_FILE_SELECT = 0x2385;
    /** 跨小程序搜索代际：新搜索开始时自增，旧搜索的迟到结果直接丢弃。 */
    private final AtomicInteger crossSearchGen = new AtomicInteger(0);
    /** 搜索页：true = 小程序列表入口的整页内容搜索；false = 内容页内单规则搜索 */
    private boolean searchDualMode = false;

    private HkItem detailItem;
    private boolean detailFromSearch;

    private HkDetail currentDetail;
    /** 环39 订阅冷启一轮的进程内只排一次旗（官 MainActivity 冷启语义对位）。 */
    private static boolean subCheckPosted;
    // 8.83 原版：详情页走通用列表渲染器，不再用专用线路/选集模型
    private final List<HkItem> detailItems = new ArrayList<>();
    // 内容列表里当前那一块「自绘详情」的条目（.rule() 页面函数 detailFn merge 进来的整块，
    // 按对象身份认）：切线路重渲时 merge 前先摘掉旧块，防越点越叠（橘汁实案，
    // 诊断账 juzhi-hk-diag-20261031.md）。普通加载更多追加不进此表。
    private final List<HkItem> detailBlockItems = new ArrayList<>();
    private ContentAdapter detailAdapter;
    private GridLayoutManager detailGrid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 8.83 startQRScanPage：JS 扫码 API 的宿主回调（HkJsRuntime.QrScanHost）
        com.fongmi.android.tv.api.hk.HkJsRuntime.setQrScanHost(qrScanHost);
        // 海阔页浅色主题：状态栏/导航栏用深色图标（BaseActivity 统一强制了白色图标，会盖掉主题的 windowLightStatusBar）
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        // 液态玻璃动态背景：decor 注入，画到状态栏底下（XML 首孩子在 fitsSystemWindows 根里盖不住状态栏区，见 activity_hk.xml）
        HkDynamicBgView.attachTo(this);
        // 历史数据迁移（一次性）：此前误入主 App Keep 的海阔收藏搬回海阔侧（后台线程，幂等）
        HkFavoriteStore.migrateIfNeeded();
        // 环39 订阅冷启触发（官 MainActivity.onCreate：宿主 init 后 postDelayed 7s
        // 起 batchCheckUpdate 协程）：本宿主即海阔首页宿主，进程内只排一次。
        if (!subCheckPosted) {
            subCheckPosted = true;
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    com.fongmi.android.tv.api.hk.HkHomeSubService::batchCheckUpdate, 7000);
        }
        // 主导航整页跳转（主页↔小程序）：进出都走页签交叉淡出（250ms＋缓动），
        // 覆盖 BaseActivity 的全 App 通用 200ms 线性对，与底栏页签互切同款。
        overridePendingTransition(R.anim.xc_tab_fade_in, R.anim.xc_tab_fade_out);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.xc_tab_fade_in, R.anim.xc_tab_fade_out);
    }

    @Override
    protected ViewBinding getBinding() {
        binding = ActivityHkBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        String groupExtra = getIntent().getStringExtra("rule_group");
        if (!TextUtils.isEmpty(groupExtra)) ruleGroup = groupExtra;
        String editExtra = getIntent().getStringExtra("edit_rule_title");
        if (!TextUtils.isEmpty(editExtra)) pendingEditRuleTitle = editExtra;
        initRulesView();
        stack.push(V_RULES);
        showView(V_RULES);
        refreshRules();
        handleOpenFileIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 官 ResolveIntentActivity 为 singleTask、二次递送靠 onNewIntent；
        // 咱家宿主启动模式不动（记名），实例被系统复用时在此补承接，
        // 避免新递送的 intent 被丢弃（G53 复核订正）。
        setIntent(intent);
        handleOpenFileIntent(intent);
    }

    /**
     * 外部进入承接分发（G53 登记面＋环2 官分发表，8.83 ResolveIntentActivity →
     * dealIntentUrl → MainActivity.checkIntentUrl，dig2-entry-formats §2.1）：
     * 官分发只看文件名后缀、不看 mime。ACTION_VIEW 与 ACTION_SEND 的
     * EXTRA_STREAM 汇入同一 handleExternalUri（官 SEND-STREAM 支同汇
     * dealIntentUrl）；ACTION_SEND 带 EXTRA_TEXT 走文本路：magnet/ed2k/ftp
     * 三前缀照官 canDeal 集合进迅雷支（VIEW 面 ftp 无 scheme 登记、
     * 到达不了本面，是已定记名缺口，只此文本面补齐）。.js 文件先走 intent#js# 官链
     * （见 handleExternalJsFile，dig2 §5.2 定谳），不回导入路。
     * 导入三步（选文件→选分组→「是否打开」）与既有 importRuleFiles 一概不动。
     */
    private void handleOpenFileIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            setIntent(new Intent()); // 消费掉，避免旋转重建时重复导入
            Uri stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (stream != null) {
                handleExternalUri(stream);
                return;
            }
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (!TextUtils.isEmpty(text)) {
                String trimmed = text.trim();
                if (trimmed.startsWith("magnet:") || trimmed.startsWith("ed2k:")
                        || trimmed.startsWith("ftp:")) {
                    playExternalThunderLink(trimmed);
                    return;
                }
                importRuleText(text, null);
            }
            return;
        }
        if (!Intent.ACTION_VIEW.equals(action)) return;
        Uri uri = intent.getData();
        if (uri == null) return;
        setIntent(new Intent()); // 消费掉，避免旋转重建时重复导入
        handleExternalUri(uri);
    }

    /**
     * 单个外部 Uri 的后缀分发（官 checkIntentUrl 逐支同序）：
     * ①magnet/ed2k（F22 scheme 口）先于一切文件支进迅雷支（.line 2369）；
     * ②.js 走 intent#js# 官链（.line 2376）；③规则文件后缀（文本类
     * .hiker/.hiker..bin/.txt/.json＋归档 .zip/.hkzip/.hkpkg/.rar/.7z）
     * 走既有导入路（.line 2449/2478）；④.epub 转阅读面（.line 2415，咱家
     * 阅读面＝LocalBookOpenActivity，官的「确定阅读」确认面差记名）；
     * ⑤余者走媒体/网页尾判（openExternalBySuffix）。改名 apk（.apk/.apk.1）
     * 维持 G53 记名不接：不进新分发、仍落既有导入路收尾。
     * ftp scheme 不补登记（主会话已定，记名缺口），本面无 ftp 到达点。
     */
    private void handleExternalUri(Uri uri) {
        String url = uri.toString();
        if (url.startsWith("magnet:") || url.startsWith("ed2k:")) {
            playExternalThunderLink(url);
            return;
        }
        String displayName = getDisplayName(uri);
        String lower = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        // 官各文件支全带「非 http」守卫：http(s) 递送只剩尾判（媒体→播放器、余→网页面）
        boolean http = url.startsWith("http://") || url.startsWith("https://");
        if (!http && lower.endsWith(".js")) {
            handleExternalJsFile(uri, displayName);
            return;
        }
        if (!http && (lower.endsWith(".apk") || lower.endsWith(".apk.1") || isRuleFileName(lower))) {
            List<Uri> uris = new ArrayList<>();
            uris.add(uri);
            importRuleFiles(uris);
            return;
        }
        if (!http && lower.endsWith(".epub")) {
            try {
                Intent it = new Intent(Intent.ACTION_VIEW, uri);
                it.setClass(this, com.fongmi.android.tv.localbook.LocalBookOpenActivity.class);
                it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(it);
            } catch (Exception e) {
                Notify.show("无法打开此文件");
            }
            return;
        }
        openExternalBySuffix(uri, displayName);
    }

    /** 规则文件后缀判（官 checkIntentUrl 文本支＋归档支的后缀集，阶梯登记口同集）。 */
    private static boolean isRuleFileName(String lower) {
        String[] suffixes = {".json", ".zip", ".hkzip", ".hkpkg", ".txt",
                ".hiker", ".hiker..bin", ".rar", ".7z"};
        for (String s : suffixes) if (lower.endsWith(s)) return true;
        return false;
    }

    /**
     * 媒体/网页尾判（官 checkIntentUrl .line 2535-2580）：判据只认
     * UrlDetector.isVideoOrMusic（咱家转写 HkUrlDetector 同表）——真→播放器，
     * 假→网页面（html/xhtml/mht/xml、图片与未知后缀同落此面，官总入口无图片面）。
     * content:// 恒先拷到 <根>/_cache/<显示名> 再以 file:// 拷贝路径重跑同一
     * 判据（官 $10 支：媒体以拷贝路径进播放器、title＝文件名；拷贝失败只收尾
     * 无提示，官 $10.failed 只 hideLoading）；file/http(s) 直达不拷贝
     * （官 PlayerChooser 三参版 (ctx, url, url) 同形，咱家 startHkPlay 同位）。
     */
    private void openExternalBySuffix(Uri uri, String displayName) {
        String name = displayName == null ? "" : displayName;
        if ("content".equals(uri.getScheme())) {
            new Thread(() -> {
                File target = new File(HkJsRuntime.getFilePath("hiker://files/_cache/" + name));
                boolean ok = false;
                try {
                    File parent = target.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    try (InputStream in = getContentResolver().openInputStream(uri);
                         FileOutputStream out = new FileOutputStream(target)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    }
                    ok = target.isFile();
                } catch (Exception ignored) {
                }
                final boolean okF = ok;
                App.post(() -> {
                    if (HkDialogs.isHostGone(this)) return;
                    if (!okF) return;
                    String fileUrl = "file://" + target.getAbsolutePath();
                    if (com.fongmi.android.tv.ui.hk.HkUrlDetector.isVideoOrMusic(fileUrl)) {
                        VideoActivity.startHkPlay(this, "", "", fileUrl, name, name, "");
                    } else {
                        new HkDetailBridge().goWeb(this, fileUrl);
                    }
                });
            }).start();
            return;
        }
        String url = uri.toString();
        if (com.fongmi.android.tv.ui.hk.HkUrlDetector.isVideoOrMusic(url)) {
            VideoActivity.startHkPlay(this, "", "", url, name, name, "");
            return;
        }
        new HkDetailBridge().goWeb(this, url);
    }

    /**
     * 外部 magnet/ed2k 迅雷支（官 checkIntentUrl .line 2369-2374 →
     * ThunderManager 两参版同位）：咱家无独立迅雷面板，承接形与
     * HkListActivity.playThunderLink 单条形同路——单集交接
     * VideoActivity.startHkPlay，播放器内 Source.fetch 的 Thunder.Parser
     * 解析出文件列表（多文件成选集）；标题取法与 thunderLinkName 同
     * （magnet 的 dn、ed2k 的文件名段）。官引擎偏好 copy/third-app 两档
     * 咱家无此面，记名差（dig2 §2.3 表 #1）。
     */
    private void playExternalThunderLink(String url) {
        String name = externalThunderName(url);
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("name", name);
            o.put("url", url);
            o.put("pic", "");
            arr.put(o);
            VideoActivity.startHkPlay(this, "", "默认", url, name, name, "",
                    arr.toString(), 0);
        } catch (Throwable e) {
            ToastUtil.show(this, "无法播放此链接", android.widget.Toast.LENGTH_SHORT);
        }
    }

    /** 外部迅雷链标题（HkListActivity.thunderLinkName 的 magnet/ed2k 两支同形）。 */
    private static String externalThunderName(String url) {
        try {
            String u = url.split(";")[0];
            if (u.startsWith("magnet:")) {
                String dn = Uri.parse(u).getQueryParameter("dn");
                return TextUtils.isEmpty(dn) ? "磁力链接" : dn;
            }
            if (u.startsWith("ed2k:")) {
                String[] segs = u.split("\\|");
                if (segs.length > 2 && !TextUtils.isEmpty(segs[2])) {
                    return java.net.URLDecoder.decode(segs[2], "UTF-8");
                }
                return "ed2k 链接";
            }
        } catch (Exception ignored) {
        }
        return "播放";
    }

    /**
     * 外部 .js 文件官链（8.83 MainActivity.checkIntentUrl .js 支＋MainActivity$5，
     * dig2-entry-formats §5.2 定谳）：入口先拷到 <根>/_cache/<文件名>（官
     * checkIntentUrl .line 2376-2381 拷贝在 $5 分叉之前），拷后按 $5.success
     * 原序三分叉——
     * ①文件名以 global 开头（$5 .line 2387 先判）：确认面「确定从外部导入
     * "<名>"脚本插件？」→确认才拷进 JS 插件目录（hiker://files/rules/js/，官
     * JSManager.getJsDirPath 同位；咱家消费方按需直读、无 reloadJSFile 式缓存
     * 可刷）＋toast「导入成功」，取消无事；名为 global_*.user.js 者走此支；
     * ②文件名以 .user.js 结尾（$5 .line 2401 后判）：拷后静默丢弃，不回落任何面；
     * ③其余普通 .js：intent#js# 枚举——扫已装规则 pages 含 intent#js# 子页者
     * （name 或 path 全等，同 title 后者覆盖）；无命中静默终局（文件只留在
     * _cache，不走插件导入、无 toast 无 UI）；恰 1 条弹确认面「是否使用"<title>"
     * 小程序处理"<名>"文件？」；多条弹底部列表「选择处理文件的小程序」；两面
     * 取消都＝关面终局（dig2 定谳覆盖 G53 旧形「取消回导入路」）。拷贝失败 toast
     * 「出错：<msg>」（官 $5.failed 同形）。
     */
    private void handleExternalJsFile(Uri uri, String displayName) {
        new Thread(() -> {
            File target = new File(HkJsRuntime.getFilePath("hiker://files/_cache/" + displayName));
            String error = null;
            try {
                File parent = target.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(target)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
            } catch (Exception e) {
                error = e.getMessage();
            }
            final String errorF = error;
            App.post(() -> {
                if (HkDialogs.isHostGone(this)) return;
                if (errorF != null) {
                    Notify.show("出错：" + errorF);
                    return;
                }
                if (displayName.startsWith("global")) {
                    showGlobalJsImportConfirm(target, displayName);
                    return;
                }
                // 官 $5 .line 2401：global 判后才判 .user.js——已先拷 _cache，到此静默弃
                if (displayName.toLowerCase(Locale.ROOT).endsWith(".user.js")) return;
                showIntentJsHandlers(target, displayName);
            });
        }).start();
    }

    /** global_ 脚本插件导入确认（官 MainActivity$5 第一支）。 */
    private void showGlobalJsImportConfirm(File cacheFile, String displayName) {
        new HkDialogs(this)
                .setTitle("温馨提示")
                .setMessage("确定从外部导入\"" + displayName + "\"脚本插件？")
                .setPositiveButton("确定", (d, w) -> new Thread(() -> {
                    try {
                        File dir = new File(HkJsRuntime.getFilePath("hiker://files/rules/js/"));
                        if (!dir.exists()) dir.mkdirs();
                        File dst = new File(dir, displayName);
                        try (InputStream in = new java.io.FileInputStream(cacheFile);
                             FileOutputStream out = new FileOutputStream(dst)) {
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        }
                        App.post(() -> Notify.show("导入成功"));
                    } catch (Exception e) {
                        App.post(() -> Notify.show("出错：" + e.getMessage()));
                    }
                }).start())
                .setNegativeButton("取消", null)
                .show();
    }

    /** intent#js# 处理器枚举与选择面（官 showRulesToDealFile）。 */
    private void showIntentJsHandlers(File cacheFile, String displayName) {
        // 同 title 后者覆盖（官两张 HashMap 同步 put）；键序用插入序近似（官 HashMap 无序，不作判据）
        java.util.LinkedHashMap<String, Object[]> handlers = new java.util.LinkedHashMap<>();
        for (HkRule r : HkRuleManager.get().getRules()) {
            for (com.fongmi.android.tv.api.hk.HkPageParser.HkPage p
                    : com.fongmi.android.tv.api.hk.HkPageParser.getPageList(r)) {
                if ("intent#js#".equals(p.getName()) || "intent#js#".equals(p.getPath())) {
                    handlers.put(r.getTitle(), new Object[]{r, p});
                    break; // 每条规则只取首个命中子页（官同形）
                }
            }
        }
        if (handlers.isEmpty()) return; // 无处理器静默终局（官同形）
        List<Object[]> matches = new ArrayList<>(handlers.values());
        if (matches.size() == 1) {
            HkRule owner = (HkRule) matches.get(0)[0];
            com.fongmi.android.tv.api.hk.HkPageParser.HkPage page =
                    (com.fongmi.android.tv.api.hk.HkPageParser.HkPage) matches.get(0)[1];
            new HkDialogs(this)
                    .setTitle("温馨提示")
                    .setMessage("是否使用\"" + owner.getTitle() + "\"小程序处理\"" + displayName + "\"文件？")
                    .setPositiveButton("确定", (d, w) -> runIntentJsPage(owner, page, cacheFile))
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        String[] titles = new String[matches.size()];
        for (int i = 0; i < matches.size(); i++) titles[i] = ((HkRule) matches.get(i)[0]).getTitle();
        HkDialogs.cards(this, "选择处理文件的小程序", titles, "取消", (d, w) -> {
            HkRule owner = (HkRule) matches.get(w)[0];
            com.fongmi.android.tv.api.hk.HkPageParser.HkPage page =
                    (com.fongmi.android.tv.api.hk.HkPageParser.HkPage) matches.get(w)[1];
            runIntentJsPage(owner, page, cacheFile);
        });
    }

    /** intent#js# 子页无头执行：input 注入 _cache 拷贝路径，子页代码自理后续（官约）。 */
    private void runIntentJsPage(HkRule owner, com.fongmi.android.tv.api.hk.HkPageParser.HkPage page, File file) {
        new Thread(() -> {
            try {
                HkJsRuntime rt = com.fongmi.android.tv.ui.hk.HkEngineManager.runtimeFor(owner);
                if (rt == null) {
                    App.post(() -> Notify.show("小程序运行时不可用"));
                    return;
                }
                com.fongmi.android.tv.ui.hk.HkEngineManager.ensureInit(rt);
                String res = rt.evalJS(HkJsRuntime.getMyRule(owner) + (page.getRule() == null ? "" : page.getRule()),
                        file.getAbsolutePath());
                // evalJS 的 JS 异常以 "error:" 前缀串回（不抛），须出面提示，
                // 不静默吞（G53 复核订正）。
                if (res != null && res.startsWith("error:")) {
                    App.post(() -> Notify.show("处理失败：" + res.substring(6).trim()));
                }
            } catch (Exception e) {
                App.post(() -> Notify.show("处理失败：" + e.getMessage()));
            }
        }).start();
    }

    @Override
    protected void onBackInvoked() {
        // inline 搜索条展开时按返回：先收起搜索条，不退出页面
        if (stack.size() == 1 && stack.peek() == V_RULES
                && binding.inlineSearchBar.getVisibility() == View.VISIBLE) {
            closeInlineSearch();
            return;
        }
        // 多选模式中按返回：先退出多选模式，不退出页面
        if (stack.size() == 1 && stack.peek() == V_RULES && ruleSelectMode) {
            exitRuleSelectMode();
            return;
        }
        // 编辑模式中按返回：先退出编辑模式（已即时保存），不退出页面
        if (stack.size() == 1 && stack.peek() == V_RULES && ruleManageMode) {
            toggleRuleManageMode();
            return;
        }
        if (stack.size() > 1) {
            // 子列表返回：先恢复父列表，不弹视图栈（官方 FilmListActivity 返回即回父列表）
            if (stack.peek() == V_CONTENT && !subStack.isEmpty()) {
                backSubList();
                return;
            }
            if (stack.peek() == V_CONTENT && currentRule != null && contentGrid != null) {
                scrollMem.put(currentRule.getTitle(), contentGrid.findFirstVisibleItemPosition());
            }
            stack.pop();
            showView(stack.peek());
        } else {
            // 内部栈退完，退出契约归 BaseActivity 缺省路一处所有
            // （任务根自救回点播首页，他 2026-11-13 返回规矩）；
            // 本页早先自备的那份已摘，不许两处同管一件事。
            super.onBackInvoked();
        }
    }

    @Override
    protected void onDestroy() {
        com.fongmi.android.tv.api.hk.HkJsRuntime.setQrScanHost(null);
        HkLoading.clearListener(hkLoadingListener);
        hideHkLoading();
        // T_WEB 全承载 holder 随页销毁（双承载收归后行内 WebView 不再是布局裸件，须显式收）
        if (contentAdapter != null) contentAdapter.destroyWebHolder();
        if (detailAdapter != null) detailAdapter.destroyWebHolder();
        destroyRouterAsync();
        super.onDestroy();
    }

    /** 8.83 JSEngine.startQRScanPage(25977) 对齐：JS 扫码 API 宿主实现。 */
    private final com.fongmi.android.tv.api.hk.HkJsRuntime.QrScanHost qrScanHost =
            new com.fongmi.android.tv.api.hk.HkJsRuntime.QrScanHost() {
                @Override
                public void startQrScan() {
                    try {
                        Intent intent = new Intent(HkPageActivity.this, ScanActivity.class);
                        startActivityForResult(intent, REQ_QR_SCAN);
                    } catch (Throwable ignored) {
                    }
                }
            };

    /**
     * 异步销毁旧路由：HkJsRuntime.destroy() 会 submit(...).get() 等 JS 单线程池清空，
     * 前一个规则的 JS 任务若还在跑（慢网络/动态域名），同步调会冻住主线程几秒。
     * 切到后台线程销毁，点击进 V2 不再卡顿。
     */
    private void destroyRouterAsync() {
        HkRouter old = router;
        router = null;
        if (old == null) return;
        new Thread(() -> {
            try {
                old.destroy();
            } catch (Throwable ignored) {
            }
        }).start();
    }

    /**
     * 跨小程序搜索结果点击：静默切换到来源小程序（只换 currentRule + 重建 router，
     * 不切视图不 reload 内容页），供后续 openDetail 用来源规则的引擎解析详情。
     */
    private void switchRuleForDetail(com.fongmi.android.tv.api.hk.HkRule target) {
        if (target == null) return;
        if (currentRule != null && target.getTitle().equals(currentRule.getTitle())) return;
        destroyRouterAsync();
        currentRule = target;
        hkPageUrl = null; // 切规则：实际抓取页 URL 失效
        // 预热新规则的 runtime（图片 @js= 求值等依赖当前 runtime 登记）
        try {
            getRouter();
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hkPaused = false;
        // 本页不在前台时别处也能改规则落盘（规则页内口令导入、分享导入等）：
        // 落盘戳变了才整表重读换新对象，没变不重读（避免每次回页重建丢滚动位置）。
        if (HkRuleManager.saveStamp() != rulesStampSeen) refreshRules();
        // 液态玻璃：页面回可见，恢复动态背景（兜底，View 自停为主；背景是 decor 注入挂在 content 根，
        // 不在 activity_hk.xml 的 binding 里，用注入时打的 id 找）
        HkDynamicBgView bg = findViewById(R.id.hk_dynamic_bg);
        if (bg != null) bg.resume();
    }

    @Override
    protected void onPause() {
        hkPaused = true;
        // 液态玻璃：页面不可见，停动态背景（decor 注入视图，用 id 找）
        HkDynamicBgView bg = findViewById(R.id.hk_dynamic_bg);
        if (bg != null) bg.pause();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // 在窗口获得焦点后延迟检查剪贴板：Android 10+ 限制后台读取剪贴板，
        // onResume 时窗口焦点可能尚未就绪，onWindowFocusChanged 是可靠时机。
        if (hasFocus && binding != null && binding.getRoot() != null) {
            binding.getRoot().postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) checkClipboardForCloudCode();
            }, 200);
        }
    }

    /**
     * 进入小程序时自动识别剪贴板里的云口令（官方 App 同款行为）：
     * 复制口令后打开本页，自动弹出导入确认。同一口令只提示一次。
     */
    private void checkClipboardForCloudCode() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return;
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return;
            CharSequence text = clip.getItemAt(0).coerceToText(this);
            if (TextUtils.isEmpty(text)) return;
            String code = extractCloudCode(text.toString());
            if (code == null) return;
            // 同一口令只提示一次
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            if (code.equals(sp.getString(KEY_LAST_CLIP, ""))) return;
            new HkDialogs(this)
                    .setTitle("检测到云口令")
                    .setMessage("剪贴板中有海阔云口令，是否立即导入？\n\n" + code)
                    .setPositiveButton("导入", (d, w) -> importByCloudCode(code))
                    .setNegativeButton("取消", null)
                    .show();
            sp.edit().putString(KEY_LAST_CLIP, code).apply();
        } catch (Exception e) {
            android.util.Log.e("HkPage", "checkClipboardForCloudCode failed", e);
        }
    }

    /** 从文本中提取云口令（云1~云10 开头，取连续的一段）。 */
    private static String extractCloudCode(String text) {
        if (TextUtils.isEmpty(text)) return null;
        // 按空白切分，找第一段云口令
        for (String part : text.trim().split("\\s+")) {
            if (HkRuleManager.isCloudCode(part)) return part;
        }
        return null;
    }

    private void showView(int v) {
        // 内页切换统一走渐入渐出：新页淡入、旧页淡出（并行，不阻塞加载）
        android.view.View show = v == V_RULES ? binding.viewRules
                : v == V_CONTENT ? binding.viewContent
                : v == V_SEARCH ? binding.viewSearch
                : v == V_DETAIL ? binding.viewDetail : null;
        // 同页守门：目标已是当前可见页时直接返回，避免 swapViews 把它 fadeIn 清零重升闪一次
        if (show != null && show.getVisibility() == View.VISIBLE) {
            boolean othersHidden = true;
            android.view.View[] all = {binding.viewRules, binding.viewContent,
                    binding.viewSearch, binding.viewDetail};
            for (android.view.View o : all) {
                if (o != null && o != show && o.getVisibility() == View.VISIBLE) {
                    othersHidden = false;
                    break;
                }
            }
            if (othersHidden) return;
        }
        XcFadeTransition.swapViews(show, binding.viewRules, binding.viewContent,
                binding.viewSearch, binding.viewDetail);
    }

    private void buildCategoryRow() {
    }

    private void buildFilterRows() {
    }

    private void pushView(int v) {
        stack.push(v);
        showView(v);
    }

    // ================= V1 规则列表 =================

    private void initRulesView() {
        int span = getPrefs().getInt(KEY_RULE_SPAN, 3);
        ruleGrid = new GridLayoutManager(this, span);
        binding.rvRules.setLayoutManager(ruleGrid);
        binding.rvRules.addItemDecoration(new GridSpace());
        ruleAdapter = new RuleAdapter();
        binding.rvRules.setAdapter(ruleAdapter);
        binding.tvTitleRule.setOnClickListener(v -> showColumnSwitchPopup());
        binding.tvTitleRule.setOnLongClickListener(v -> {
            enterRuleSelectMode();
            return true;
        });
        binding.btnSelectAllRules.setOnClickListener(v -> toggleSelectAllRules());
        binding.btnDeleteSelectedRules.setOnClickListener(v -> showDeleteSelectedConfirm());
        binding.btnSelectDone.setOnClickListener(v -> exitRuleSelectMode());
        // 点 🔍：搜规则（首页直接 inline 按名过滤小程序卡片）；长按 🔍：搜内容（全局搜索）
        // 用户拍板（2026-10-24）：不许再弹「搜内容/搜规则」二选一弹窗，多此一举
        binding.btnSearchRule.setOnClickListener(v -> toggleInlineSearch());
        binding.btnSearchRule.setOnLongClickListener(v -> {
            openContentSearch();
            return true;
        });
        binding.btnSortRule.setOnClickListener(v -> {
            ruleSortByName = !ruleSortByName;
            ruleAdapter.rebuildSections();
            ruleAdapter.notifyDataSetChanged();
        });
        binding.btnEditRule.setOnClickListener(v -> toggleRuleManageMode());
        // 用户拍板：收藏入口放编辑左边（搜索/筛选 … 收藏/编辑），进海阔自己的收藏页
        binding.btnFavoriteRule.setOnClickListener(v -> HkFavoriteActivity.start(this));
        // 用户拍板（2026-10-23）：历史入口放收藏左边，进海阔自己的播放历史页
        binding.btnHistoryRule.setOnClickListener(v -> HkHistoryActivity.start(this));
        binding.btnGroupManage.setOnClickListener(v -> showGroupManageDialog());
        binding.btnImport.setOnClickListener(v -> showImportDialog());
        binding.rvRuleGroup.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        applyLiquidGlass();
        // 液态玻璃顶栏：只裁剪背景缓冲，内容层（文字/按钮）永远不糊，不存在旧 blur 把字糊住的问题。
        binding.etInlineSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                ruleKeyword = s.toString().trim();
                ruleAdapter.rebuildSections();
                ruleAdapter.notifyDataSetChanged();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        binding.etInlineSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                return true;
            }
            return false;
        });
        binding.btnInlineClose.setOnClickListener(v -> closeInlineSearch());
        updateRulesTopPadding();
        loadCustomRuleData();
        // 编辑模式拖拽排序：长按卡片拖动（仅编辑模式启用）
        // 拖到顶部悬浮栏的分组 pills 上松手 = 把卡片分配进该分组（换位回滚，仅做分组）
        ruleTouchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                if (!ruleManageMode) return 0;
                int drag = ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
                return makeMovementFlags(drag, 0);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                int from = vh.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION || from == to) return false;
                ruleAdapter.moveItem(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return ruleManageMode;
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder vh, int actionState) {
                super.onSelectedChanged(vh, actionState);
                if (!ruleManageMode) return;
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && vh != null) {
                    ruleDragHolder = vh;
                    ruleDragFromPos = vh.getBindingAdapterPosition();
                    ruleDragTitle = null;
                    if (ruleDragFromPos != RecyclerView.NO_POSITION
                            && ruleDragFromPos < ruleAdapter.items.size()) {
                        Object o = ruleAdapter.items.get(ruleDragFromPos);
                        if (o instanceof HkRule) ruleDragTitle = ((HkRule) o).getTitle();
                    }
                    ruleDragOrderSnapshot = new ArrayList<>();
                    for (HkRule r : rules) ruleDragOrderSnapshot.add(r.getTitle());
                    // 抬高被拖卡片并放开祖先裁剪，让它能浮到悬浮顶栏的分组 pills 之上
                    vh.itemView.setElevation(dp(12));
                    setRulesClipChildren(false);
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv,
                                    @NonNull RecyclerView.ViewHolder vh,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive);
                if (ruleManageMode && isCurrentlyActive
                        && actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    updateGroupDropTarget(vh.itemView);
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                vh.itemView.setElevation(0);
                setRulesClipChildren(true);
                int target = groupDropTargetPos;
                setGroupDropTarget(-1);
                if (ruleManageMode && target >= 0 && groupPairs != null
                        && target < groupPairs.size() && ruleDragTitle != null) {
                    HkRule rule = findRuleByTitle(ruleDragTitle);
                    restoreDragOrderSnapshot();
                    if (rule != null) {
                        String g = groupPairs.get(target)[1];
                        assignRuleGroup(rule, g);
                        Notify.show("已移入「" + groupPairs.get(target)[0] + "」");
                    }
                }
                ruleDragHolder = null;
                ruleDragFromPos = RecyclerView.NO_POSITION;
                ruleDragTitle = null;
                ruleDragOrderSnapshot = null;
            }
        });
        ruleTouchHelper.attachToRecyclerView(binding.rvRules);
    }

    /** 悬浮磨砂顶栏高度变化时，同步列表顶部内边距（内容滚动时从顶栏下穿过）。 */
    /** 悬浮顶栏总高度 = 顶栏56 + 间距8 + 分组行(48) + inline搜索条(56)，列表 paddingTop 跟随，避免被遮挡。 */
    private void updateRulesTopPadding() {
        boolean groups = binding.rvRuleGroup.getVisibility() == View.VISIBLE;
        boolean search = binding.inlineSearchBar.getVisibility() == View.VISIBLE;
        int top = dp(56) + dp(8) + (groups ? dp(48) : 0) + (search ? dp(56) : 0);
        binding.rvRules.setPadding(0, top, 0, dp(8));
    }

    /** 液态玻璃（P1 小程序页）：顶栏/圆钮/底钮换玻璃件＋按压反馈，布局不动。 */
    private void applyLiquidGlass() {
        // 悬浮玻璃顶栏：圆角 24dp，左右 18dp 悬浮边距见布局
        binding.headerCluster.setBackground(new HkGlassDrawable(this, 24));
        // 搜索/漏斗图标：玻璃圆钮（小圆玻璃底＋深色图标，图标 src 不动）
        binding.btnSearchRule.setBackground(new HkGlassDrawable(this, 16));
        binding.btnSortRule.setBackground(new HkGlassDrawable(this, 16));
        HkPressFeedback.attach(binding.btnSearchRule);
        HkPressFeedback.attach(binding.btnSortRule);
        HkPressFeedback.attach(binding.btnFavoriteRule);
        HkPressFeedback.attach(binding.btnHistoryRule);
        // 底部导入钮：奶油玻璃胶囊（奶油渐变＋1dp 金描边）＋深金字
        binding.btnImport.setBackground(HkGlassDrawable.creamButton(this, 26));
        binding.btnImport.setTextColor(0xFF7A5A00);
        HkPressFeedback.attach(binding.btnImport);
    }

    /** 顶部「编辑」按钮：进入排列+分组编辑模式（长按拖拽排序，点卡片分配分组；按钮文字 编辑/完成 互换）。 */
    private void toggleRuleManageMode() {
        ruleManageMode = !ruleManageMode;
        binding.btnEditRule.setText(ruleManageMode ? "完成" : "编辑");
        binding.btnGroupManage.setVisibility(ruleManageMode ? View.VISIBLE : View.GONE);
        binding.btnSearchRule.setVisibility(ruleManageMode ? View.GONE : View.VISIBLE);
        binding.btnSortRule.setVisibility(ruleManageMode ? View.GONE : View.VISIBLE);
        binding.btnFavoriteRule.setVisibility(ruleManageMode ? View.GONE : View.VISIBLE);
        binding.btnHistoryRule.setVisibility(ruleManageMode ? View.GONE : View.VISIBLE);
        // 编辑模式下收起 inline 搜索（搜索图标已隐藏）
        if (ruleManageMode && binding.inlineSearchBar.getVisibility() == View.VISIBLE) {
            binding.inlineSearchBar.setVisibility(View.GONE);
            binding.etInlineSearch.setText("");
            ruleKeyword = "";
            updateRulesTopPadding();
        }
        if (ruleManageMode && customOrder == null) {
            customOrder = new ArrayList<>();
            for (HkRule r : rules) customOrder.add(r.getTitle());
            saveCustomOrder();
        }
        ruleAdapter.rebuildSections();
        ruleAdapter.notifyDataSetChanged();
    }

    /** 长按「小程序」标题进入多选模式：卡片点按改为勾选，顶栏换成 全选/删除/完成。 */
    private void enterRuleSelectMode() {
        if (ruleSelectMode) return;
        if (rules.isEmpty()) {
            Notify.show("暂无小程序");
            return;
        }
        if (ruleManageMode) toggleRuleManageMode();
        if (binding.inlineSearchBar.getVisibility() == View.VISIBLE) closeInlineSearch();
        ruleSelectMode = true;
        ruleSelectedTitles.clear();
        binding.btnSearchRule.setVisibility(View.GONE);
        binding.btnSortRule.setVisibility(View.GONE);
        binding.btnEditRule.setVisibility(View.GONE);
        binding.btnFavoriteRule.setVisibility(View.GONE);
        binding.btnHistoryRule.setVisibility(View.GONE);
        binding.btnSelectAllRules.setVisibility(View.VISIBLE);
        binding.btnDeleteSelectedRules.setVisibility(View.VISIBLE);
        binding.btnSelectDone.setVisibility(View.VISIBLE);
        updateSelectBar();
        ruleAdapter.notifyDataSetChanged();
    }

    private void exitRuleSelectMode() {
        if (!ruleSelectMode) return;
        ruleSelectMode = false;
        ruleSelectedTitles.clear();
        binding.btnSearchRule.setVisibility(View.VISIBLE);
        binding.btnSortRule.setVisibility(View.VISIBLE);
        binding.btnEditRule.setVisibility(View.VISIBLE);
        binding.btnFavoriteRule.setVisibility(View.VISIBLE);
        binding.btnHistoryRule.setVisibility(View.VISIBLE);
        binding.btnSelectAllRules.setVisibility(View.GONE);
        binding.btnDeleteSelectedRules.setVisibility(View.GONE);
        binding.btnSelectDone.setVisibility(View.GONE);
        ruleAdapter.notifyDataSetChanged();
    }

    private void updateSelectBar() {
        int n = ruleSelectedTitles.size();
        binding.btnDeleteSelectedRules.setText(n > 0 ? "删除（" + n + "）" : "删除");
        boolean all = n > 0 && n == rules.size();
        binding.btnSelectAllRules.setText(all ? "取消全选" : "全选");
    }

    private void toggleRuleSelect(HkRule rule) {
        String t = rule.getTitle();
        if (!ruleSelectedTitles.remove(t)) ruleSelectedTitles.add(t);
        updateSelectBar();
        ruleAdapter.notifyDataSetChanged();
    }

    private void toggleSelectAllRules() {
        if (ruleSelectedTitles.size() == rules.size()) {
            ruleSelectedTitles.clear();
        } else {
            for (HkRule r : rules) ruleSelectedTitles.add(r.getTitle());
        }
        updateSelectBar();
        ruleAdapter.notifyDataSetChanged();
    }

    private void showDeleteSelectedConfirm() {
        if (ruleSelectedTitles.isEmpty()) {
            Notify.show("先选要删的小程序");
            return;
        }
        final List<String> targets = new ArrayList<>(ruleSelectedTitles);
        new HkDialogs(this)
                .setTitle("删除小程序")
                .setMessage("确定删除选中的 " + targets.size() + " 个小程序吗？删了要重新导入。")
                .setPositiveButton("删除", (d, w) -> new Thread(() -> {
                    for (String t : targets) HkRuleManager.get().delete(t);
                    ruleGroupMap.keySet().removeAll(targets);
                    saveRuleGroupMap();
                    if (customOrder != null) {
                        customOrder.removeAll(targets);
                        saveCustomOrder();
                    }
                    App.post(() -> {
                        exitRuleSelectMode();
                        refreshRules();
                        Notify.show("已删除 " + targets.size() + " 个小程序");
                    });
                }).start())
                .setNegativeButton("取消", null)
                .show();
    }

    /** 多选模式下卡片右上角的勾选标记（代码添加，不改卡片布局）：未选空心圈、已选黄底白勾。 */
    private void bindSelectBadge(View itemView, HkRule rule) {
        if (!(itemView instanceof ViewGroup)) return;
        ViewGroup root = (ViewGroup) itemView;
        android.widget.TextView badge =
                (android.widget.TextView) root.findViewWithTag("hk_select_badge");
        if (!ruleSelectMode) {
            if (badge != null) badge.setVisibility(View.GONE);
            return;
        }
        if (badge == null) {
            badge = new android.widget.TextView(this);
            badge.setTag("hk_select_badge");
            badge.setGravity(Gravity.CENTER);
            badge.setTextSize(13);
            android.widget.FrameLayout.LayoutParams lp =
                    new android.widget.FrameLayout.LayoutParams(dp(22), dp(22));
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.topMargin = dp(6);
            lp.setMarginEnd(dp(6));
            root.addView(badge, lp);
        }
        boolean checked = ruleSelectedTitles.contains(rule.getTitle());
        badge.setVisibility(View.VISIBLE);
        badge.setText(checked ? "✓" : "");
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        if (checked) {
            // 液态玻璃：已选态换金底（金色语言对差）
            gd.setColor(0xFFD9A420);
            gd.setStroke(dp(1), 0xFFD9A420);
        } else {
            gd.setColor(0xFFFFFFFF);
            gd.setStroke(dp(1), 0xFFB9BEC7);
        }
        badge.setTextColor(0xFFFFFFFF);
        badge.setBackground(gd);
    }

    // ================= 编辑模式：拖拽卡片到分组 pills 分配分组 =================

    /** 放开/恢复 rvRules 祖先链的裁剪，让被拖卡片能浮到悬浮顶栏之上。 */
    private void setRulesClipChildren(boolean clip) {
        binding.rvRules.setClipChildren(clip);
        ViewParent p = binding.rvRules.getParent();
        int depth = 0;
        while (p instanceof ViewGroup && depth < 6) {
            ((ViewGroup) p).setClipChildren(clip);
            p = p.getParent();
            depth++;
        }
    }

    /** 拖拽中：被拖卡片中心落到哪个分组 pill 上就高亮哪个（-1=无）。 */
    private void updateGroupDropTarget(View dragView) {
        int pos = -1;
        if (binding.rvRuleGroup.getVisibility() == View.VISIBLE && groupChipAdapter != null) {
            int[] loc = new int[2];
            dragView.getLocationOnScreen(loc);
            float cx = loc[0] + dragView.getWidth() / 2f;
            float cy = loc[1] + dragView.getHeight() / 2f;
            int[] ploc = new int[2];
            binding.rvRuleGroup.getLocationOnScreen(ploc);
            View pill = binding.rvRuleGroup.findChildViewUnder(cx - ploc[0], cy - ploc[1]);
            if (pill != null) pos = binding.rvRuleGroup.getChildAdapterPosition(pill);
        }
        setGroupDropTarget(pos);
    }

    private void setGroupDropTarget(int pos) {
        if (groupDropTargetPos == pos) return;
        groupDropTargetPos = pos;
        if (groupChipAdapter != null) groupChipAdapter.setDropTarget(pos);
    }

    private HkRule findRuleByTitle(String title) {
        if (title == null) return null;
        for (HkRule r : rules) if (title.equals(r.getTitle())) return r;
        return null;
    }

    /** 投放分组时回滚本次拖拽造成的换位（拖拽只做分组，不做排序）。 */
    private void restoreDragOrderSnapshot() {
        if (ruleDragOrderSnapshot == null) return;
        Map<String, HkRule> byTitle = new HashMap<>();
        for (HkRule r : rules) byTitle.put(r.getTitle(), r);
        List<HkRule> restored = new ArrayList<>();
        for (String t : ruleDragOrderSnapshot) {
            HkRule r = byTitle.remove(t);
            if (r != null) restored.add(r);
        }
        restored.addAll(byTitle.values());
        rules.clear();
        rules.addAll(restored);
        customOrder = new ArrayList<>(ruleDragOrderSnapshot);
        for (HkRule r : rules) if (!customOrder.contains(r.getTitle())) customOrder.add(r.getTitle());
        saveCustomOrder();
    }

    /** 分配规则到分组 g（""=无分组/跟随文件）：拖放与分组弹窗共用同一逻辑。 */
    private void assignRuleGroup(HkRule rule, String g) {
        String fileGroup = rule.getGroup() == null ? "" : rule.getGroup();
        if (g.equals(fileGroup)) ruleGroupMap.remove(rule.getTitle());
        else ruleGroupMap.put(rule.getTitle(), g);
        saveRuleGroupMap();
        buildGroupFilterRow();
        ruleAdapter.rebuildSections();
        ruleAdapter.notifyDataSetChanged();
    }

    // ================= 小程序自定义分组（本地 prefs，不改 rule.json 原文件） =================

    /** 规则的实际分组：prefs 自定义覆盖优先，否则取 rule.json 的 group 字段。 */
    private String effectiveGroup(HkRule r) {
        if (ruleGroupMap.containsKey(r.getTitle())) {
            String g = ruleGroupMap.get(r.getTitle());
            return g == null ? "" : g;
        }
        String g = r.getGroup();
        return g == null ? "" : g;
    }

    private void loadCustomRuleData() {
        // 先解析到临时表、成功才换入：解析失败不许按空分组覆盖用户自建分组，
        // 置失败标记让保存口拒绝回写（存档原文留在 prefs 里不丢），并用本页既有 toast 显形。
        try {
            JSONArray arr = new JSONArray(getPrefs().getString(KEY_CUSTOM_GROUPS, "[]"));
            List<String> parsedGroups = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                String g = arr.optString(i);
                if (!TextUtils.isEmpty(g) && !parsedGroups.contains(g)) parsedGroups.add(g);
            }
            customGroups.clear();
            customGroups.addAll(parsedGroups);
            customGroupsLoadFailed = false;
        } catch (Exception e) {
            customGroupsLoadFailed = true;
            ToastUtil.show(this, "自定义分组读取失败", android.widget.Toast.LENGTH_SHORT);
        }
        try {
            JSONObject obj = new JSONObject(getPrefs().getString(KEY_RULE_GROUPS, "{}"));
            Map<String, String> parsedMap = new HashMap<>();
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                parsedMap.put(k, obj.optString(k, ""));
            }
            ruleGroupMap.clear();
            ruleGroupMap.putAll(parsedMap);
            ruleGroupsLoadFailed = false;
        } catch (Exception e) {
            ruleGroupsLoadFailed = true;
            ToastUtil.show(this, "规则分组读取失败", android.widget.Toast.LENGTH_SHORT);
        }
        customOrder = null;
        try {
            String s = getPrefs().getString(KEY_RULE_ORDER, null);
            if (s != null) {
                customOrder = new ArrayList<>();
                JSONArray arr = new JSONArray(s);
                for (int i = 0; i < arr.length(); i++) customOrder.add(arr.optString(i));
            }
        } catch (Exception e) {
            customOrder = null;
        }
    }

    private void saveCustomGroups() {
        if (customGroupsLoadFailed) {
            ToastUtil.show(this, "分组读取失败，本次修改无法保存", android.widget.Toast.LENGTH_SHORT);
            return;
        }
        getPrefs().edit().putString(KEY_CUSTOM_GROUPS, new JSONArray(customGroups).toString()).apply();
    }

    private void saveRuleGroupMap() {
        if (ruleGroupsLoadFailed) {
            ToastUtil.show(this, "分组读取失败，本次修改无法保存", android.widget.Toast.LENGTH_SHORT);
            return;
        }
        try {
            JSONObject obj = new JSONObject();
            for (Map.Entry<String, String> e : ruleGroupMap.entrySet()) obj.put(e.getKey(), e.getValue());
            getPrefs().edit().putString(KEY_RULE_GROUPS, obj.toString()).apply();
        } catch (Exception e) {
            ToastUtil.show(this, "规则分组保存失败", android.widget.Toast.LENGTH_SHORT);
        }
    }

    private void saveCustomOrder() {
        if (customOrder == null) return;
        getPrefs().edit().putString(KEY_RULE_ORDER, new JSONArray(customOrder).toString()).apply();
    }

    /** 刷新后同步自定义顺序：删掉已删规则，末尾追加新规则，按自定义顺序重排 rules。 */
    private void syncCustomOrder() {
        if (customOrder == null) return;
        java.util.Set<String> have = new java.util.HashSet<>();
        for (HkRule r : rules) have.add(r.getTitle());
        for (int i = customOrder.size() - 1; i >= 0; i--) {
            if (!have.contains(customOrder.get(i))) customOrder.remove(i);
        }
        for (HkRule r : rules) {
            if (!customOrder.contains(r.getTitle())) customOrder.add(r.getTitle());
        }
        final Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < customOrder.size(); i++) idx.put(customOrder.get(i), i);
        Collections.sort(rules, (a, b) -> Integer.compare(
                idx.getOrDefault(a.getTitle(), Integer.MAX_VALUE),
                idx.getOrDefault(b.getTitle(), Integer.MAX_VALUE)));
        saveCustomOrder();
    }

    /** 点「小程序」标题：弹出 2 列 / 3 列切换小窗（独立卡片选项+间距，当前选中金色加粗）。 */
    private void showColumnSwitchPopup() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));
        TextView title = new TextView(this);
        title.setText("小程序列数");
        title.setTextColor(0xFFF2F4F8);
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        int current = getPrefs().getInt(KEY_RULE_SPAN, 3);
        String[] labels = {"2 列", "3 列"};
        int[] spans = {2, 3};
        AlertDialog dialog = new HkDialogs(this).setView(root).create();
        for (int i = 0; i < labels.length; i++) {
            TextView tv = new TextView(this);
            tv.setText(labels[i]);
            boolean sel = spans[i] == current;
            tv.setTextColor(sel ? 0xFFB8890F : 0xFF1A1D24);
            tv.setTypeface(null, sel ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            tv.setTextSize(16);
            tv.setGravity(Gravity.CENTER);
            tv.setBackgroundResource(R.drawable.dialog_option_card_light);
            tv.setPadding(dp(16), dp(14), dp(16), dp(14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(10);
            final int span = spans[i];
            tv.setOnClickListener(v -> {
                dialog.dismiss();
                if (span == ruleGrid.getSpanCount()) return;
                getPrefs().edit().putInt(KEY_RULE_SPAN, span).apply();
                ruleGrid.setSpanCount(span);
                // 列数变化时布局类型（普通/紧凑）可能变化，重建卡片视图
                ruleAdapter.notifyDataSetChanged();
                binding.rvRules.scrollToPosition(0);
            });
            root.addView(tv, lp);
        }
        dialog.show();
    }

    /** 分组筛选行：全部 | 自定义分组（用户顺序）| 规则文件分组（①②序号排序），显示时去序号。 */
    private void buildGroupFilterRow() {
        List<String> groups = new ArrayList<>();
        for (String g : customGroups) {
            if (!TextUtils.isEmpty(g) && !groups.contains(g)) groups.add(g);
        }
        List<String> fileGroups = new ArrayList<>();
        for (HkRule r : rules) {
            String g = effectiveGroup(r);
            if (TextUtils.isEmpty(g) || groups.contains(g) || fileGroups.contains(g)) continue;
            fileGroups.add(g);
        }
        Collections.sort(fileGroups, (a, b) -> Integer.compare(groupSortKey(a), groupSortKey(b)));
        groups.addAll(fileGroups);
        boolean show = !groups.isEmpty();
        binding.rvRuleGroup.setVisibility(show ? View.VISIBLE : View.GONE);
        updateRulesTopPadding();
        if (!show) return;
        List<String[]> pairs = new ArrayList<>();
        pairs.add(new String[]{"全部", ""});
        for (String g : groups) pairs.add(new String[]{stripGroupPrefix(g), g});
        int sel = 0;
        if (!ruleGroup.isEmpty()) {
            if (groups.contains(ruleGroup)) {
                sel = groups.indexOf(ruleGroup) + 1;
            } else {
                ruleGroup = ""; // 当前选中的分组已被删/改名，回退到全部
            }
        }
        binding.rvRuleGroup.setAdapter(new ChipAdapter(pairs, sel, value -> {
            ruleGroup = value;
            ruleAdapter.rebuildSections();
            ruleAdapter.notifyDataSetChanged();
        }));
        groupPairs = pairs;
        groupChipAdapter = (ChipAdapter) binding.rvRuleGroup.getAdapter();
        groupDropTargetPos = -1;
    }

    /** 分组排序键：前导①②..序号，没有则排最后。 */
    private static int groupSortKey(String g) {
        if (g == null || g.isEmpty()) return Integer.MAX_VALUE;
        int i = 0, n = 0;
        while (i < g.length()) {
            int d = circledDigit(g.codePointAt(i));
            if (d < 0) break;
            n = n * 10 + d;
            i += Character.charCount(g.codePointAt(i));
        }
        return i == 0 ? Integer.MAX_VALUE : n;
    }

    private static int circledDigit(int cp) {
        if (cp >= 0x2460 && cp <= 0x2473) return cp - 0x2460 + 1; // ①..⑳
        if (cp >= 0x3251 && cp <= 0x325F) return cp - 0x3251 + 21; // ㉑..㉟
        return -1;
    }

    /** 显示用：去掉前导①②序号。 */
    private static String stripGroupPrefix(String g) {
        if (g == null) return "";
        int i = 0;
        while (i < g.length() && circledDigit(g.codePointAt(i)) >= 0) i += Character.charCount(g.codePointAt(i));
        return i == 0 ? g : g.substring(i);
    }

    /** 编辑模式点卡片：选择分组（无分组 / 自定义分组 / 规则文件分组），即时保存。 */
    private void showGroupPicker(HkRule rule) {
        List<String> values = new ArrayList<>();
        values.add(""); // 无分组
        for (String g : customGroups) if (!values.contains(g)) values.add(g);
        for (HkRule r : rules) {
            String g = r.getGroup();
            if (!TextUtils.isEmpty(g) && !values.contains(g)) values.add(g);
        }
        String current = effectiveGroup(rule);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));
        TextView title = new TextView(this);
        title.setText(rule.getTitle());
        title.setTextColor(0xFFF2F4F8);
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView sub = new TextView(this);
        sub.setText("选择分组");
        sub.setTextColor(0xFFA8B0C0);
        sub.setTextSize(13);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(4);
        root.addView(sub, subLp);
        ScrollView sv = new ScrollView(this);
        LinearLayout opts = new LinearLayout(this);
        opts.setOrientation(LinearLayout.VERTICAL);
        AlertDialog dialog = new HkDialogs(this).setView(root).create();
        for (String g : values) {
            TextView tv = new TextView(this);
            tv.setText(TextUtils.isEmpty(g) ? "无分组" : stripGroupPrefix(g));
            boolean sel = g.equals(current);
            tv.setTextColor(sel ? 0xFFB8890F : 0xFF1A1D24);
            tv.setTypeface(null, sel ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            tv.setTextSize(16);
            tv.setGravity(Gravity.CENTER);
            tv.setBackgroundResource(R.drawable.dialog_option_card_light);
            tv.setPadding(dp(16), dp(14), dp(16), dp(14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(10);
            tv.setOnClickListener(v -> {
                dialog.dismiss();
                assignRuleGroup(rule, g);
            });
            opts.addView(tv, lp);
        }
        sv.addView(opts);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        svLp.topMargin = dp(4);
        if (values.size() > 6) svLp.height = dp(340);
        root.addView(sv, svLp);
        dialog.show();
    }

    /** 导入时分组选择回调：group 为 "" 表示不分组（归"全部"）。 */
    private interface OnImportGroupPicked {
        void onPicked(String group);
    }

    /**
     * 导入时弹分组选择器：解析完成后、落盘前让用户手动选分组。
     * 选项：全部（默认选中）| 标准分组 | 用户自定义分组。忽略规则文件自带的野分组名。
     * 用户点选项或取消/点外部，均视为选"全部"（""）。
     * UI 线程调用。
     */
    private void showImportGroupPicker(String titleText, OnImportGroupPicked cb) {
        if (isFinishing() || isDestroyed()) return;
        List<String> values = new ArrayList<>();
        values.add(""); // 全部（默认）
        for (String g : STD_GROUPS) if (!values.contains(g)) values.add(g);
        for (String g : customGroups) {
            if (!TextUtils.isEmpty(g) && !values.contains(g)) values.add(g);
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(0xFFF2F4F8);
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView sub = new TextView(this);
        sub.setText("选择分组（忽略规则自带的分组名）");
        sub.setTextColor(0xFFA8B0C0);
        sub.setTextSize(13);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(4);
        root.addView(sub, subLp);
        ScrollView sv = new ScrollView(this);
        LinearLayout opts = new LinearLayout(this);
        opts.setOrientation(LinearLayout.VERTICAL);
        AlertDialog dialog = new HkDialogs(this).setView(root).create();
        dialog.setOnCancelListener(d -> cb.onPicked(""));
        for (String g : values) {
            TextView tv = new TextView(this);
            tv.setText(TextUtils.isEmpty(g) ? "全部" : g);
            boolean sel = TextUtils.isEmpty(g); // 默认选中"全部"
            tv.setTextColor(sel ? 0xFFB8890F : 0xFF1A1D24);
            tv.setTypeface(null, sel ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            tv.setTextSize(16);
            tv.setGravity(Gravity.CENTER);
            tv.setBackgroundResource(R.drawable.dialog_option_card_light);
            tv.setPadding(dp(16), dp(14), dp(16), dp(14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(10);
            tv.setOnClickListener(v -> {
                dialog.dismiss();
                cb.onPicked(g);
            });
            opts.addView(tv, lp);
        }
        sv.addView(opts);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        svLp.topMargin = dp(4);
        if (values.size() > 6) svLp.height = dp(340);
        root.addView(sv, svLp);
        dialog.show();
    }

    /**
     * 导入落盘后应用用户选择的分组：走 hk_rule_group_map prefs 覆盖，不改 rule.json 原文件。
     * 只对新导入的规则生效（已安装的更新保持原有分组映射不动）。
     * UI 线程调用。
     */
    private void applyImportGroup(List<HkRule> saved, List<Boolean> isNew, String group) {
        if (saved == null || saved.isEmpty()) return;
        String g = group == null ? "" : group;
        for (int i = 0; i < saved.size(); i++) {
            if (isNew != null && i < isNew.size() && !isNew.get(i)) continue;
            HkRule r = saved.get(i);
            String fileGroup = r.getGroup() == null ? "" : r.getGroup();
            if (g.equals(fileGroup)) ruleGroupMap.remove(r.getTitle());
            else ruleGroupMap.put(r.getTitle(), g);
        }
        saveRuleGroupMap();
    }

    /** 分组管理：新建 / 改名 / 删除 / 排序自定义分组（只管自定义分组，不碰规则文件）。 */
    private void showGroupManageDialog() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));
        TextView title = new TextView(this);
        title.setText("分组管理");
        title.setTextColor(0xFFF2F4F8);
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView sub = new TextView(this);
        sub.setText("自定义分组保存在本地，不修改规则文件");
        sub.setTextColor(0xFFA8B0C0);
        sub.setTextSize(12);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(4);
        root.addView(sub, subLp);
        ScrollView sv = new ScrollView(this);
        LinearLayout listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        sv.addView(listBox);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        svLp.topMargin = dp(8);
        svLp.height = dp(300);
        root.addView(sv, svLp);
        TextView addBtn = new TextView(this);
        addBtn.setText("＋ 新建分组");
        addBtn.setTextColor(0xFFB8890F);
        addBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        addBtn.setTextSize(15);
        addBtn.setGravity(Gravity.CENTER);
        addBtn.setBackgroundResource(R.drawable.dialog_option_card_light);
        addBtn.setPadding(dp(16), dp(13), dp(16), dp(13));
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        addLp.topMargin = dp(10);
        AlertDialog dialog = new HkDialogs(this).setView(root).create();
        addBtn.setOnClickListener(v -> showGroupNameInput(null, name -> {
            customGroups.add(name);
            saveCustomGroups();
            renderGroupRows(listBox, dialog);
            buildGroupFilterRow();
        }));
        root.addView(addBtn, addLp);
        renderGroupRows(listBox, dialog);
        dialog.setOnDismissListener(d -> {
            buildGroupFilterRow();
            ruleAdapter.rebuildSections();
            ruleAdapter.notifyDataSetChanged();
        });
        dialog.show();
    }

    /** 分组管理列表行：名称 + ▲▼排序 + 改名 + 删除。 */
    private void renderGroupRows(LinearLayout box, AlertDialog dialog) {
        box.removeAllViews();
        if (customGroups.isEmpty()) {
            TextView hint = new TextView(this);
            hint.setText("暂无自定义分组，点下方新建");
            hint.setTextColor(0xFFA8B0C0);
            hint.setTextSize(14);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, dp(24), 0, dp(24));
            box.addView(hint);
            return;
        }
        for (int i = 0; i < customGroups.size(); i++) {
            final int pos = i;
            String g = customGroups.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(10), dp(4), dp(10));
            TextView name = new TextView(this);
            name.setText(stripGroupPrefix(g));
            name.setTextColor(0xFFF2F4F8);
            name.setTextSize(15);
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            row.addView(name, nameLp);
            row.addView(groupRowBtn("▲", v -> {
                if (pos <= 0) return;
                Collections.swap(customGroups, pos, pos - 1);
                saveCustomGroups();
                renderGroupRows(box, dialog);
                buildGroupFilterRow();
            }));
            row.addView(groupRowBtn("▼", v -> {
                if (pos >= customGroups.size() - 1) return;
                Collections.swap(customGroups, pos, pos + 1);
                saveCustomGroups();
                renderGroupRows(box, dialog);
                buildGroupFilterRow();
            }));
            row.addView(groupRowBtn("改名", v -> showGroupNameInput(g, name2 -> {
                customGroups.set(pos, name2);
                for (Map.Entry<String, String> e : ruleGroupMap.entrySet()) {
                    if (g.equals(e.getValue())) e.setValue(name2);
                }
                saveCustomGroups();
                saveRuleGroupMap();
                renderGroupRows(box, dialog);
                buildGroupFilterRow();
                ruleAdapter.rebuildSections();
                ruleAdapter.notifyDataSetChanged();
            })));
            row.addView(groupRowBtn("删除", v -> new HkDialogs(this)
                    .setTitle("删除分组")
                    .setMessage("删除「" + stripGroupPrefix(g) + "」？组内小程序将恢复按规则文件分组。")
                    .setPositiveButton("删除", (d, w) -> {
                        customGroups.remove(pos);
                        Iterator<Map.Entry<String, String>> it = ruleGroupMap.entrySet().iterator();
                        while (it.hasNext()) {
                            if (g.equals(it.next().getValue())) it.remove();
                        }
                        saveCustomGroups();
                        saveRuleGroupMap();
                        if (g.equals(ruleGroup)) ruleGroup = "";
                        renderGroupRows(box, dialog);
                        buildGroupFilterRow();
                        ruleAdapter.rebuildSections();
                        ruleAdapter.notifyDataSetChanged();
                    })
                    .setNegativeButton("取消", null)
                    .show()));
            box.addView(row);
            if (i < customGroups.size() - 1) {
                View div = new View(this);
                div.setBackgroundColor(0x33FFFFFF);
                box.addView(div, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
            }
        }
    }

    private TextView groupRowBtn(String text, View.OnClickListener l) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFFB8890F);
        tv.setTextSize(14);
        tv.setPadding(dp(10), dp(8), dp(10), dp(8));
        tv.setOnClickListener(l);
        return tv;
    }

    /** 分组名输入框：新建时 oldName=null，改名时带原名；重名/空名拒绝。 */
    private void showGroupNameInput(String oldName, java.util.function.Consumer<String> onOk) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));
        EditText et = new EditText(this);
        et.setHint(oldName == null ? "分组名，如：追剧" : "新分组名");
        if (oldName != null) et.setText(oldName);
        et.setSingleLine(true);
        et.setBackgroundResource(R.drawable.xc_input_bg);
        et.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(et, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        new HkDialogs(this)
                .setTitle(oldName == null ? "新建分组" : "改名")
                .setView(root)
                .setPositiveButton("确定", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) {
                        Notify.show("分组名不能为空");
                        return;
                    }
                    if (!name.equals(oldName) && customGroups.contains(name)) {
                        Notify.show("分组已存在");
                        return;
                    }
                    onOk.accept(name);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 导入/更新后把新规则并入内存列表：同标题必须替换旧对象，不能只 add。
     * 只 add 的旧口径下，列表里攥着导入前载入的旧 HkRule 对象，openRule 把它
     * 序列化传给内容页——导入后从列表点开还是旧版（在线更新路径 applyRuleUpdates
     * 一直是替换口径，三条导入路径漏了）。
     */
    private void mergeRulesIntoList(List<HkRule> fresh) {
        for (HkRule r : fresh) {
            boolean replaced = false;
            for (int i = 0; i < rules.size(); i++) {
                if (r.getTitle().equals(rules.get(i).getTitle())) {
                    rules.set(i, r);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) rules.add(r);
        }
        rulesStampSeen = HkRuleManager.saveStamp();
    }

    private void refreshRules() {
        new Thread(() -> {
            List<HkRule> list = HkRuleManager.get().getRules();
            App.post(() -> {
                rules.clear();
                rules.addAll(list);
                rulesStampSeen = HkRuleManager.saveStamp();
                syncCustomOrder();
                ruleAdapter.rebuildSections();
                ruleAdapter.notifyDataSetChanged();
                buildGroupFilterRow();
                boolean empty = ruleAdapter.getItemCount() == 0;
                binding.emptyRules.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.rvRules.setVisibility(empty ? View.GONE : View.VISIBLE);
                // F-12：列表加载后顺带做一次在线更新检查（24 小时节流、无来源/无新版时静默）
                maybeCheckRuleUpdates();
                if (pendingEditRuleTitle != null) {
                    String editTitle = pendingEditRuleTitle;
                    pendingEditRuleTitle = null;
                    for (HkRule r : rules) {
                        if (editTitle.equals(r.getTitle())) {
                            showRuleFormEditor(r);
                            break;
                        }
                    }
                }
            });
        }).start();
    }

    /** 导入入口：三个选项各做成独立卡片框，有间距（选项类弹窗统一按此样式）。 */
    private void showImportDialog() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));
        String[] labels = {"从文件导入", "从云口令导入", "粘贴规则 JSON"};
        AlertDialog dialog = new HkDialogs(this).setView(root).create();
        for (int i = 0; i < labels.length; i++) {
            TextView tv = new TextView(this);
            tv.setText(labels[i]);
            tv.setTextColor(0xFF1A1D24);
            tv.setTextSize(16);
            tv.setBackgroundResource(R.drawable.dialog_option_card_light);
            tv.setPadding(dp(16), dp(14), dp(16), dp(14));
            final int which = i;
            tv.setOnClickListener(v -> {
                dialog.dismiss();
                if (which == 0) pickRuleFile();
                else if (which == 1) showCloudCodeDialog(null);
                else showPasteDialog();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.topMargin = dp(10);
            root.addView(tv, lp);
        }
        dialog.show();
    }

    /** 导入带密码分享时是否自动再问一次密码（可关，默认开）。 */
    private static final String PREF_IMPORT_PWD_PROMPT = "hk_import_pwd_prompt";

    private boolean importPwdPromptEnabled() {
        return getSharedPreferences("hk_page", MODE_PRIVATE).getBoolean(PREF_IMPORT_PWD_PROMPT, true);
    }

    private void setImportPwdPromptEnabled(boolean on) {
        getSharedPreferences("hk_page", MODE_PRIVATE).edit().putBoolean(PREF_IMPORT_PWD_PROMPT, on).apply();
    }

    /** 云口令导入对话框（支持 云1~云10 开头，如 云6oooole/apidb/xxxx）。带密码的分享在第二个格里填密码。 */
    private void showCloudCodeDialog(String preset) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        EditText et = new EditText(this);
        et.setHint("粘贴云口令，如：云6oooole/apidb/xxxx");
        et.setSingleLine(true);
        et.setTextColor(0xFFF2F4F8);
        et.setHintTextColor(0xFF9AA1B0);
        if (!TextUtils.isEmpty(preset)) et.setText(preset);
        box.addView(et);
        EditText etPwd = new EditText(this);
        etPwd.setHint("密码（分享人设了密码才填，没有可不填）");
        etPwd.setSingleLine(true);
        etPwd.setTextColor(0xFFF2F4F8);
        etPwd.setHintTextColor(0xFF9AA1B0);
        etPwd.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(etPwd);
        CheckBox cb = new CheckBox(this);
        cb.setText("导入失败时，再问我一次密码");
        cb.setTextColor(0xFFF2F4F8);
        cb.setChecked(importPwdPromptEnabled());
        cb.setOnCheckedChangeListener((v, checked) -> setImportPwdPromptEnabled(checked));
        box.addView(cb);
        new HkDialogs(this)
                .setTitle("从云口令导入")
                .setView(box)
                .setPositiveButton("导入", (d, w) -> importByCloudCode(et.getText().toString(), etPwd.getText().toString().trim(), false))
                .setNegativeButton("取消", null)
                .show();
    }

    /** 云口令导入：云端解析 → 弹分组选择器 → 落盘（忽略规则自带的野分组名）。 */
    private void importByCloudCode(String code) {
        importByCloudCode(code, "", false);
    }

    private void importByCloudCode(String code, String password, boolean prompted) {
        if (TextUtils.isEmpty(code)) {
            Notify.show("口令为空");
            return;
        }
        Notify.show("正在从云端获取规则…");
        new Thread(() -> {
            try {
                HkRuleManager.ParsedRule p = HkRuleManager.get().parseByCloudCode(code, password);
                // G48：云端规则同样可能附带 adBlockUrls，与文件/文本导入同闸
                App.post(() -> {
                    if (runWithAdBlockGate(
                            HkAdUrlBlocker.splitAdBlockUrls(p.rule.getAdBlockUrls()),
                            () -> showImportGroupPicker(p.rule.getTitle(),
                                    group -> finishCloudImport(p, group, code)))) return;
                    showImportGroupPicker(p.rule.getTitle(), group -> finishCloudImport(p, group, code));
                });
            } catch (Exception e) {
                // 失败先分类：网络类失败（IOException 及其子类：连不上/超时/DNS/SSL）是
                // 云端取不到，与密码无关，直接报网络问题——旧逻辑不分青红皂白弹密码补问框，
                // 真机实案里 note.ms 取件超时后误弹密码框，纯属误导（潇洒哥亲点）。
                // 只有取件成功但内容不对/通道明确要密码这类，才走下面的密码补问逻辑。
                if (e instanceof java.io.IOException) {
                    App.post(() -> Notify.show("导入失败：网络问题，云端取不到（站点连不上或超时），请稍后再试"));
                } else if (TextUtils.isEmpty(password) && !prompted && importPwdPromptEnabled()) {
                    // 没带密码、还没问过、开关开着：多半是分享设了密码，再问一次
                    App.post(() -> showImportPasswordDialog(code));
                } else {
                    App.post(() -> Notify.show("导入失败：" + e.getMessage()));
                }
            }
        }).start();
    }

    /** 带密码分享的补问框：输了密码带着重试。 */
    private void showImportPasswordDialog(String code) {
        if (isFinishing() || isDestroyed()) return;
        EditText et = new EditText(this);
        et.setHint("输入分享人给的密码");
        et.setSingleLine(true);
        et.setTextColor(0xFFF2F4F8);
        et.setHintTextColor(0xFF9AA1B0);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new HkDialogs(this)
                .setTitle("这个分享可能设了密码")
                .setMessage("没填密码导入失败了。分享人要是给了密码，填这里再试一次。")
                .setView(et)
                .setPositiveButton("导入", (d, w) -> importByCloudCode(code, et.getText().toString().trim(), true))
                .setNegativeButton("取消", null)
                .show();
    }

    /** 云口令导入：分组选择后的落盘（后台线程）。sourceCode 为来源云口令，落盘供 F-12 在线更新按来源重拉。 */
    private void finishCloudImport(HkRuleManager.ParsedRule p, String group, String sourceCode) {
        new Thread(() -> {
            try {
                boolean isNew = HkRuleManager.get().getInstalledRule(p.rule.getTitle()) == null;
                HkRuleManager.get().saveRule(p.rule, p.json);
                if (!TextUtils.isEmpty(sourceCode)) {
                    getPrefs().edit().putString(KEY_RULE_SRC_PREFIX + p.rule.getTitle(), sourceCode).apply();
                }
                List<HkRule> one = Collections.singletonList(p.rule);
                App.post(() -> {
                    applyImportGroup(one, Collections.singletonList(isNew), group);
                    Notify.show("导入成功：" + p.rule.getTitle());
                    // 并入内存列表：同标题替换旧对象（见 mergeRulesIntoList 注释）
                    mergeRulesIntoList(one);
                    syncCustomOrder();
                    ruleAdapter.rebuildSections();
                    ruleAdapter.notifyDataSetChanged();
                    buildGroupFilterRow();
                    boolean empty = ruleAdapter.getItemCount() == 0;
                    binding.emptyRules.setVisibility(empty ? View.VISIBLE : View.GONE);
                    binding.rvRules.setVisibility(empty ? View.GONE : View.VISIBLE);
                    // 导入成功后提示"是否打开"（像海阔原版）
                    showOpenAfterImportDialog(one);
                });
            } catch (Exception e) {
                App.post(() -> Notify.show("导入失败：" + e.getMessage()));
            }
        }).start();
    }

    /**
     * 多文件导入的勾选项：解析结果 + 其所属 .hkzip 的附带资源（非 hkzip 时为 null）。
     * 附带资源只给本条目选中的规则落盘，避免多包混淆。
     */
    private static class PickEntry {
        final HkRuleManager.ParsedRule parsed;
        final HkRuleManager.HkZipData hkZipData;

        PickEntry(HkRuleManager.ParsedRule parsed, HkRuleManager.HkZipData hkZipData) {
            this.parsed = parsed;
            this.hkZipData = hkZipData;
        }
    }

    private void pickRuleFile() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            // 不设 EXTRA_MIME_TYPES 白名单：.hkzip 无注册 MIME 类型会被选择器
            // 按 MIME 灰置不可选（他真机亲证改后缀.zip 才导得进，账
            // hk-hkzip-home-missing-dig-20261111 §一.3）；选文件口不预筛，
            // 导入后由 collectFromFile 按内容分发。
            // 支持一次选多个文件（系统文件选择器长按多选）
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, REQ_IMPORT_FILE);
        } catch (Exception e) {
            Notify.show("无法打开文件选择器");
        }
    }

    private void showPasteDialog() {
        EditText et = new EditText(this);
        et.setHint("粘贴 rule.json / 规则数组 / js: 规则内容");
        et.setMinLines(4);
        et.setTextColor(0xFFF2F4F8);
        et.setHintTextColor(0xFF9AA1B0);
        new HkDialogs(this)
                .setTitle("从口令导入")
                .setView(et)
                .setPositiveButton("导入", (d, w) -> importRuleText(et.getText().toString(), null))
                .setNegativeButton("取消", null)
                .show();
    }

    private void importRuleJson(String json) {
        importRuleText(json, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 8.83 onActivityResult(5273)：fileSelect 文件选择结果，文件名注入 _fileSelect_<name> 后执行 JS
        if (requestCode == REQ_FILE_SELECT) {
            String js = fileSelectJs;
            fileSelectJs = "";
            if (js != null && !js.isEmpty() && resultCode == RESULT_OK && data != null && data.getData() != null) {
                android.net.Uri uri = data.getData();
                String name = "";
                try {
                    android.database.Cursor c = getContentResolver().query(uri, null, null, null, null);
                    if (c != null) {
                        int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                        if (c.moveToFirst() && idx >= 0) name = c.getString(idx);
                        c.close();
                    }
                } catch (Throwable ignored) {
                }
                if (name.isEmpty()) name = uri.getLastPathSegment();
                final String varName = "_fileSelect_" + (name == null ? "" : name);
                final String code = js;
                // 把文件内容读出来注入变量（8.83 用 UriUtils 读文件，这里简化：只注入文件名变量，JS 可按需用 _fileSelect_ 取）
                new Thread(() -> {
                    try {
                        HkJsRuntime rt = getRouter().getEngine().getJsRuntime();
                        rt.putMyVar(varName, uri.toString());
                        rt.eval(code, "");
                    } catch (Throwable e) {
                        android.util.Log.d("HkPage", "fileSelect eval failed: " + e.getMessage());
                    }
                }).start();
            }
            return;
        }
        if (requestCode == REQ_QR_SCAN) {
            // 8.83 onActivityResult → dealQRContent(1054)：扫码结果处理。
            // 8.83 用 "codedContent"，星辰 ScanActivity 返回 "address"，此处适配。
            if (resultCode == RESULT_OK && data != null) {
                String content = data.getStringExtra("address");
                if (content == null || content.isEmpty()) {
                    content = data.getStringExtra("codedContent");
                }
                if (content != null && !content.isEmpty()) {
                    com.fongmi.android.tv.api.hk.HkQrUtil.dealQRContent(this, content);
                }
            }
            return;
        }
        if (requestCode == REQ_IMPORT_FILE && resultCode == RESULT_OK && data != null) {
            List<Uri> uris = new ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) {
                // 多选：ClipData 里每个 item 是一个文件
                for (int i = 0; i < clip.getItemCount(); i++) {
                    Uri u = clip.getItemAt(i).getUri();
                    if (u != null) uris.add(u);
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (!uris.isEmpty()) importRuleFiles(uris);
        }
    }

    /**
     * 多文件导入：逐个解析（zip/.hkzip/JSON/JSON 数组/js:），全部合并后走统一勾选弹窗（带全选）。
     * 单个文件解析失败只跳过该文件，不影响其他文件。
     *
     * @param uris 待导入的文件 Uri 列表
     */
    private void importRuleFiles(List<Uri> uris) {
        if (uris.size() > 1) Notify.show("正在解析 " + uris.size() + " 个文件…");
        new Thread(() -> {
            List<PickEntry> entries = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            List<File> collectionDirs = new ArrayList<>();
            for (Uri uri : uris) {
                File tmp = null;
                String displayName = getDisplayName(uri);
                try {
                    // 先落到临时文件：需要按后缀/魔数判断容器类型
                    tmp = copyToTemp(uri);
                    collectFromFile(tmp, displayName, entries, errors, collectionDirs);
                } catch (Exception e) {
                    errors.add((displayName == null ? "文件" : displayName) + "：" + e.getMessage());
                } finally {
                    if (tmp != null) tmp.delete();
                }
            }
            final List<PickEntry> result = entries;
            final List<String> failList = errors;
            final List<File> collections = collectionDirs;
            App.post(() -> {
                if (!failList.isEmpty()) {
                    Notify.show("跳过 " + failList.size() + " 个失败文件：" + TextUtils.join("；", failList));
                }
                if (result.isEmpty()) {
                    if (failList.isEmpty() && collections.isEmpty()) Notify.show("未解析到任何规则");
                } else if (result.size() == 1) {
                    // 单个规则直接导入（.hkzip 的附带资源一并落盘）
                    importPickedRules(result);
                } else {
                    showPickRulesDialog(result);
                }
                // 合集包照官：解压落盘后 toast＋开浏览面让用户逐个点选（官
                // importPackageByZip 的 lambda$6 同位：toast「请选择要导入的合集内容」）
                for (File dir : collections) {
                    Notify.show("请选择要导入的合集内容");
                    openCollectionBrowser(dir);
                }
            });
        }).start();
    }

    /**
     * 合集浏览面（8.83 FileBrowserActivity 同位，dig2-entry-formats §5.1）：合集
     * 解压目录逐层点选——点目录进下一层、点文件走 handleCollectionEntry 分发；
     * 本面打开本身不导入、不执行任何条目（官 importPackageByZip 链在此终结、
     * 后续全靠用户点选）。
     */
    private void openCollectionBrowser(File dir) {
        if (dir == null || HkDialogs.isHostGone(this)) return;
        File[] kids = dir.listFiles();
        List<File> items = new ArrayList<>();
        if (kids != null) {
            for (File k : kids) items.add(k);
        }
        items.sort((a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        if (items.isEmpty()) {
            Notify.show("合集目录是空的");
            return;
        }
        String[] labels = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            labels[i] = items.get(i).getName() + (items.get(i).isDirectory() ? "/" : "");
        }
        HkDialogs.cards(this, dir.getName(), labels, "取消", (d, w) -> {
            File picked = items.get(w);
            if (picked.isDirectory()) openCollectionBrowser(picked);
            else handleCollectionEntry(picked);
        });
    }

    /**
     * 合集目录内条目点选分发（8.83 FileBrowserActivity$1.onClick 分发表，
     * dig2 §5.1）：分发表首前置支——.mht/.mhtml 走 WebUtil.goWeb 打开网页
     * （官 $1.smali:680/690 .line 279-280，咱家同位路 HkDetailBridge.goWeb）；
     * 路径含 .hk合集 时——txt/json/hiker 走文本导入链；hkzip 或
     * （zip 且路径含 .hk小程序）走单包导入；hkpkg 或（zip 且路径含 .hk合集）
     * 递归解压再开浏览面；.hkshell 走确认面（见 showHkShellConfirm）。
     * 其余形：zip/7z/rar 走既有解包分发、媒体走播放器、图片走系统看图、
     * .epub 与未知后缀走系统选择器（.epub 的阅读面承接差记名不动，见 census 环5）。
     */
    private void handleCollectionEntry(File f) {
        String name = f.getName();
        String lower = name.toLowerCase(Locale.ROOT);
        String path = f.getAbsolutePath();
        boolean inCollection = path.contains(".hk合集");
        String ext = "";
        int dot = lower.lastIndexOf('.');
        if (dot >= 0) ext = lower.substring(dot + 1);
        if (ext.equals("mht") || ext.equals("mhtml")) {
            new HkDetailBridge().goWeb(this, "file://" + path);
            return;
        }
        if (lower.endsWith(".hkshell")) {
            showHkShellConfirm(f);
            return;
        }
        if (inCollection && (ext.equals("txt") || ext.equals("json") || ext.equals("hiker"))) {
            importTextFile(f);
            return;
        }
        if (inCollection && (ext.equals("hkzip") || (ext.equals("zip") && path.contains(".hk小程序")))) {
            importCollectedFile(f);
            return;
        }
        if (inCollection && (ext.equals("hkpkg") || (ext.equals("zip") && path.contains(".hk合集")))) {
            new Thread(() -> {
                try {
                    File dir = HkRuleManager.get().extractCollectionPackage(f);
                    App.post(() -> {
                        if (dir == null) return;
                        Notify.show("请选择要导入的合集内容");
                        openCollectionBrowser(dir);
                    });
                } catch (Exception e) {
                    App.post(() -> Notify.show(e.getMessage()));
                }
            }).start();
            return;
        }
        if (ext.equals("hiker") || ext.equals("txt")) {
            importTextFile(f);
            return;
        }
        if (ext.equals("zip") || ext.equals("hkzip") || ext.equals("7z") || ext.equals("rar")) {
            importCollectedFile(f);
            return;
        }
        if (isHkMediaName(lower)) {
            VideoActivity.startHkPlay(this, "", "", "file://" + path, name, name, "");
            return;
        }
        openWithSystem(f, isHkImageName(lower) ? "image/*" : "*/*");
    }

    /** 文本形条目导入（官 checkAutoTextFromFile 同位：读文本走既有文本导入链）。 */
    private void importTextFile(File f) {
        new Thread(() -> {
            try {
                String text = readAll(new java.io.FileInputStream(f));
                importRuleText(text, f.getName());
            } catch (Exception e) {
                App.post(() -> Notify.show("导入失败：" + e.getMessage()));
            }
        }).start();
    }

    /** 包形条目导入：走 collectFromFile 收集后按单条直导/多条勾选的既有尾形收口。 */
    private void importCollectedFile(File f) {
        new Thread(() -> {
            List<PickEntry> entries = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            List<File> collectionDirs = new ArrayList<>();
            try {
                collectFromFile(f, f.getName(), entries, errors, collectionDirs);
            } catch (Exception e) {
                errors.add(f.getName() + "：" + e.getMessage());
            }
            final List<PickEntry> result = entries;
            final List<String> failList = errors;
            final List<File> collections = collectionDirs;
            App.post(() -> {
                if (!failList.isEmpty()) {
                    Notify.show("跳过 " + failList.size() + " 个失败文件：" + TextUtils.join("；", failList));
                }
                if (result.size() == 1) {
                    importPickedRules(result);
                } else if (result.size() > 1) {
                    showPickRulesDialog(result);
                } else if (failList.isEmpty() && collections.isEmpty()) {
                    Notify.show("未解析到任何规则");
                }
                for (File dir : collections) {
                    Notify.show("请选择要导入的合集内容");
                    openCollectionBrowser(dir);
                }
            });
        }).start();
    }

    /**
     * .hkshell 确认面（8.83 FileBrowserActivity$1 对 .hkshell 的 XPopup.asInputConfirm，
     * dig2 §5.1）：标题「执行脚本」、正文「确认要执行以下脚本内容吗？」、输入框预填
     * 文件全文可编辑；确认——空→toast「脚本内容不能为空」；js: 形→runHkShellJs；
     * 否则→executeHkShellText（COPY 形）＋toast「执行完成，已拷贝<N>个文件」；
     * 取消→无副作用。
     */
    private void showHkShellConfirm(File shellFile) {
        new Thread(() -> {
            String text;
            try {
                text = readAll(new java.io.FileInputStream(shellFile));
            } catch (Exception e) {
                App.post(() -> Notify.show("出错：" + e.getMessage()));
                return;
            }
            final String fullText = text;
            App.post(() -> {
                if (HkDialogs.isHostGone(this)) return;
                EditText et = new EditText(this);
                et.setText(fullText);
                et.setTextColor(0xFF101216);
                et.setTextSize(13);
                et.setMinLines(4);
                et.setMaxLines(12);
                new HkDialogs(this)
                        .setTitle("执行脚本")
                        .setMessage("确认要执行以下脚本内容吗？")
                        .setView(et)
                        .setPositiveButton("确定", (d, w) -> {
                            String input = et.getText().toString();
                            if (input.isEmpty()) {
                                Notify.show("脚本内容不能为空");
                                return;
                            }
                            File baseDir = shellFile.getParentFile() == null
                                    ? shellFile : shellFile.getParentFile();
                            if (HkRuleManager.isJsShell(input)) {
                                runHkShellJs(input, baseDir);
                            } else {
                                new Thread(() -> {
                                    int n = HkRuleManager.executeHkShellText(input, baseDir);
                                    App.post(() -> Notify.show("执行完成，已拷贝" + n + "个文件"));
                                }).start();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });
        }).start();
    }

    /**
     * .hkshell 的 js: 形执行（8.83 FileBrowserActivity.executeShellByJs＋lambda$16，
     * dig2 §5.1 落码级）：组码＝getMyRule(null)＋generateMY("MY_DIR", 父目录绝对
     * 路径)＋脚本文本（原文含 js: 前缀，JS 里是标签形、官同式直拼），evalJS 的
     * input＝父目录绝对路径。结果处置：error: 前缀出面提示、toast:// 直 toast
     * （页面形结果处置与 intent#js# 同口径未逐形接，记名见 census 环5）。
     */
    private void runHkShellJs(String text, File baseDir) {
        new Thread(() -> {
            try {
                HkJsRuntime rt = com.fongmi.android.tv.ui.hk.HkEngineManager.runtimeFor(null);
                if (rt == null) {
                    App.post(() -> Notify.show("小程序运行时不可用"));
                    return;
                }
                com.fongmi.android.tv.ui.hk.HkEngineManager.ensureInit(rt);
                String dirPath = baseDir.getAbsolutePath();
                // 官 executeShellByJs 组码同式：generateMY("MY_DIR", 路径)——走树内
                // 官同位实现（HkLazyRuleParser.generateMY，含 escapeJavaScriptString
                // 全转义），不再手拼（复核订正：旧手拼只处理 \\ 与 ' 两形）
                String myDir = HkLazyRuleParser.generateMY("MY_DIR", dirPath);
                String res = rt.evalJS(HkJsRuntime.getMyRule(null) + myDir + text, dirPath);
                if (res != null && res.startsWith("error:")) {
                    App.post(() -> Notify.show("处理失败：" + res.substring(6).trim()));
                } else if (res != null && res.startsWith("toast://")) {
                    App.post(() -> Notify.show(res.substring("toast://".length())));
                }
            } catch (Exception e) {
                App.post(() -> Notify.show("处理失败：" + e.getMessage()));
            }
        }).start();
    }

    /** 媒体后缀判（8.83 UrlDetector 媒体表＋FileBrowser 的 .ts，dig2 §5.1 分发表）。 */
    private static boolean isHkMediaName(String lower) {
        String[] suffixes = {".mp4", ".m3u8", ".flv", ".avi", ".3gp", ".mpeg", ".wmv",
                ".mov", ".rmvb", ".dat", ".mkv", ".ts", ".mp3", ".wav", ".ogg",
                ".flac", ".m4a"};
        for (String s : suffixes) if (lower.endsWith(s)) return true;
        return false;
    }

    /** 图片后缀判（8.83 FileBrowser isImage 同位）。 */
    private static boolean isHkImageName(String lower) {
        String[] suffixes = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".ico",
                ".svg", ".avif"};
        for (String s : suffixes) if (lower.endsWith(s)) return true;
        return false;
    }

    /** 系统选择器外发（8.83 ShareUtil.findChooserToDeal 同位：未知后缀与 .epub 形）。 */
    private void openWithSystem(File f, String mime) {
        try {
            Intent it = new Intent(Intent.ACTION_VIEW);
            it.setDataAndType(com.fongmi.android.tv.utils.FileUtil.getShareUri(f), mime);
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(it, f.getName()));
        } catch (Exception e) {
            Notify.show("无法打开此文件");
        }
    }

    /** 解包暂存序号（同毫秒多次/嵌套解包不撞名，与 HkRuleManager 合集暂存同型）。 */
    private static final java.util.concurrent.atomic.AtomicLong ARC_STAGE_SEQ =
            new java.util.concurrent.atomic.AtomicLong();

    /** 解包安全上限（与合集路 unzip 的条目量级同级，防压缩炸弹写满 cache）。 */
    private static final long ARC_MAX_ENTRY_BYTES = 64L * 1024 * 1024;
    private static final long ARC_MAX_TOTAL_BYTES = 256L * 1024 * 1024;
    private static final int ARC_MAX_ENTRIES = 1000;

    /**
     * 单文件分发（importRuleFiles 与 7z/rar 解包条目共用）：
     * .hkpkg 走合集整包路（8.83 importPackageByZip 改官形，批C3：只解压落盘到
     * _cache/<净名>.hk合集、目录收进 collectionDirs 待开浏览面逐个点选，不自动
     * 导入/执行任何条目）；7z/rar 容器解包后逐条目递归本分发；zip 走
     * parseHkZip/parseZip；其余按文本走 js:/JSON 解析。
     */
    private void collectFromFile(File f, String displayName, List<PickEntry> entries,
                                 List<String> errors, List<File> collectionDirs) throws Exception {
        String lowerName = displayName == null ? "" : displayName.toLowerCase();
        if (HkRuleManager.isCollectionPackage(f, lowerName)) {
            File dir = HkRuleManager.get().extractCollectionPackage(f);
            if (dir != null) collectionDirs.add(dir);
            return;
        }
        if (isArchiveFile(f, displayName)) {
            File stage = new File(getCacheDir(), "hk_arc_" + System.currentTimeMillis()
                    + "_" + ARC_STAGE_SEQ.incrementAndGet());
            try {
                List<File> files = extractArchive(f, stage);
                // 包内只要有 rule.json（任意深度），整包就是一个规则包：摊开的
                // data.zip/libs.zip 是它的附带资源——整目录回打包走 zip 正路
                // （parseHkZip 的解析＋资源 sink），不逐条喂免得资源丢失＋对
                // data.zip 之类报假「没有找到 rule.json」（G53 复核订正）。
                boolean hasRuleJson = false;
                for (File ef : files) {
                    if ("rule.json".equalsIgnoreCase(ef.getName())) {
                        hasRuleJson = true;
                        break;
                    }
                }
                if (hasRuleJson) {
                    File repacked = new File(getCacheDir(), "hk_arc_pkg_"
                            + ARC_STAGE_SEQ.incrementAndGet() + ".zip");
                    try {
                        zipDirectory(stage, repacked);
                        HkRuleManager.HkZipData zipData = HkRuleManager.get().parseHkZip(repacked);
                        for (HkRuleManager.ParsedRule p : zipData.rules) {
                            entries.add(new PickEntry(p, zipData));
                        }
                    } finally {
                        repacked.delete();
                    }
                    return;
                }
                files.sort((a, b) -> a.getAbsolutePath().compareTo(b.getAbsolutePath()));
                for (File ef : files) {
                    try {
                        collectFromFile(ef, ef.getName(), entries, errors, collectionDirs);
                    } catch (Exception e) {
                        errors.add(ef.getName() + "：" + e.getMessage());
                    }
                }
            } finally {
                deleteRecursiveQuietly(stage);
            }
            return;
        }
        if (isZipFile(f, displayName)) {
            // 多规则包：先全部解析 → 弹窗让用户勾选 → 只导入选中的
            // 包型按内容认（isHkZipPackage：内含 require.json/libs.zip/data.zip
            // 或根 rule.json 即 hkzip 包），文件名只作参考——递送名丢后缀时
            // 旧形按名判假落 parseZip、require.json 与 libs.zip 被静默丢弃
            // （账 hk-hkzip-home-missing-dig-20261111 §一.4）；.hkzip 后缀
            // 本身仍直判（包内结构异常时不改其原有落点）。
            boolean pkg = lowerName.endsWith(".hkzip") || HkRuleManager.isHkZipPackage(f);
            if (pkg) {
                HkRuleManager.HkZipData zipData = HkRuleManager.get().parseHkZip(f);
                for (HkRuleManager.ParsedRule p : zipData.rules) entries.add(new PickEntry(p, zipData));
            } else {
                for (HkRuleManager.ParsedRule p : HkRuleManager.get().parseZip(f)) {
                    entries.add(new PickEntry(p, null));
                }
            }
        } else {
            String text = readAll(new FileInputStream(f));
            if (HkRuleManager.isJsRuleText(text)) {
                entries.add(new PickEntry(HkRuleManager.get().parseJsRule(text, displayName), null));
            } else {
                for (HkRuleManager.ParsedRule p : HkRuleManager.get().parseJsonList(text)) {
                    entries.add(new PickEntry(p, null));
                }
            }
        }
    }

    /** 7z/rar 容器判：后缀或魔数（7z 37 7A BC AF 27 1C／rar "Rar!" 1A 07）。 */
    private boolean isArchiveFile(File f, String displayName) {
        if (displayName != null) {
            String lower = displayName.toLowerCase();
            if (lower.endsWith(".7z") || lower.endsWith(".rar")) return true;
        }
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] magic = new byte[7];
            int n = in.read(magic);
            if (n >= 6 && (magic[0] & 0xFF) == 0x37 && (magic[1] & 0xFF) == 0x7A
                    && (magic[2] & 0xFF) == 0xBC && (magic[3] & 0xFF) == 0xAF
                    && (magic[4] & 0xFF) == 0x27 && (magic[5] & 0xFF) == 0x1C) return true;
            return n >= 7 && magic[0] == 'R' && magic[1] == 'a' && magic[2] == 'r' && magic[3] == '!'
                    && (magic[4] & 0xFF) == 0x1A && (magic[5] & 0xFF) == 0x07;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 7z/rar 解包到 stage 目录（防穿越：条目名归一后含 ".." 段或绝对路径跳过），
     * 返回落盘的普通文件清单。7z 走 commons-compress SevenZFile、rar 走 junrar
     * Archive（两依赖在树，LocalSevenZ/LocalRar 有先例）。
     */
    private List<File> extractArchive(File f, File stage) throws Exception {
        List<File> out = new ArrayList<>();
        if (!stage.exists()) stage.mkdirs();
        byte[] head = new byte[6];
        try (FileInputStream in = new FileInputStream(f)) {
            int n = in.read(head);
            if (n < 6) return out;
        }
        boolean is7z = (head[0] & 0xFF) == 0x37 && (head[1] & 0xFF) == 0x7A
                && (head[2] & 0xFF) == 0xBC && (head[3] & 0xFF) == 0xAF;
        long[] total = {0};
        if (is7z) {
            try (org.apache.commons.compress.archivers.sevenz.SevenZFile zf =
                         new org.apache.commons.compress.archivers.sevenz.SevenZFile(f)) {
                org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry e;
                while ((e = zf.getNextEntry()) != null) {
                    if (e.isDirectory()) continue;
                    if (out.size() >= ARC_MAX_ENTRIES) throw new Exception("压缩包条目过多");
                    File target = archiveEntryTarget(stage, e.getName());
                    if (target == null) continue;
                    if (target.getParentFile() != null) target.getParentFile().mkdirs();
                    long written = 0;
                    try (FileOutputStream os = new FileOutputStream(target)) {
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = zf.read(buf, 0, buf.length)) > 0) {
                            written += r;
                            total[0] += r;
                            if (written > ARC_MAX_ENTRY_BYTES) throw new Exception("压缩包条目过大");
                            if (total[0] > ARC_MAX_TOTAL_BYTES) throw new Exception("压缩包总大小过大");
                            os.write(buf, 0, r);
                        }
                    }
                    out.add(target);
                }
            }
        } else {
            try (com.github.junrar.Archive ar = new com.github.junrar.Archive(f)) {
                for (com.github.junrar.rarfile.FileHeader fh : ar.getFileHeaders()) {
                    if (fh.isDirectory()) continue;
                    if (out.size() >= ARC_MAX_ENTRIES) throw new Exception("压缩包条目过多");
                    File target = archiveEntryTarget(stage, fh.getFileNameString());
                    if (target == null) continue;
                    if (target.getParentFile() != null) target.getParentFile().mkdirs();
                    // junrar extractFile 无计数口：以计数流在写满上限时抛断。
                    java.io.OutputStream counting = new java.io.FilterOutputStream(
                            new FileOutputStream(target)) {
                        long written = 0;

                        @Override
                        public void write(byte[] b, int off, int len) throws java.io.IOException {
                            written += len;
                            total[0] += len;
                            if (written > ARC_MAX_ENTRY_BYTES) throw new java.io.IOException("压缩包条目过大");
                            if (total[0] > ARC_MAX_TOTAL_BYTES) throw new java.io.IOException("压缩包总大小过大");
                            super.write(b, off, len);
                        }
                    };
                    try (java.io.OutputStream os = counting) {
                        ar.extractFile(fh, os);
                    }
                    out.add(target);
                }
            }
        }
        return out;
    }

    /** 整目录回打包成 zip（条目名为相对路径），供 7z/rar 规则包转走 zip 正路。 */
    private void zipDirectory(File dir, File zipFile) throws Exception {
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                new FileOutputStream(zipFile))) {
            zipInto(dir, dir, zos);
        }
    }

    private void zipInto(File root, File cur, java.util.zip.ZipOutputStream zos) throws Exception {
        File[] kids = cur.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (k.isDirectory()) {
                zipInto(root, k, zos);
                continue;
            }
            String rel = root.toURI().relativize(k.toURI()).getPath();
            zos.putNextEntry(new java.util.zip.ZipEntry(rel));
            try (FileInputStream in = new FileInputStream(k)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
            }
            zos.closeEntry();
        }
    }

    /** 解包条目落点：归一路径后落在 stage 内才返回，穿越/绝对路径回 null 跳过。 */
    private File archiveEntryTarget(File stage, String entryName) {
        if (entryName == null) return null;
        String name = entryName.replace('\\', '/');
        File target = new File(stage, name);
        try {
            String stagePath = stage.getCanonicalPath();
            String targetPath = target.getCanonicalPath();
            if (!targetPath.startsWith(stagePath + File.separator)) return null;
        } catch (Exception e) {
            return null;
        }
        return target;
    }

    private void deleteRecursiveQuietly(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursiveQuietly(k);
        }
        f.delete();
    }

    /** content uri 落盘到临时文件（便于按魔数判断 zip）。 */
    private File copyToTemp(Uri uri) throws Exception {
        File tmp = File.createTempFile("hkimport", ".tmp", getCacheDir());
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return tmp;
    }

    /**
     * 统一文本导入路由：js: 开头 → JS 规则包装导入；否则按 JSON（单个对象或数组）导入。
     *
     * @param text     规则文本
     * @param fileName 来源文件名（可为 null），js: 规则用于取标题
     */
    private void importRuleText(String text, String fileName) {
        if (TextUtils.isEmpty(text)) {
            App.post(() -> Notify.show("内容为空"));
            return;
        }
        new Thread(() -> {
            try {
                if (HkRuleManager.isJsRuleText(text)) {
                    HkRuleManager.ParsedRule p = HkRuleManager.get().parseJsRule(text, fileName);
                    App.post(() -> showImportGroupPicker(p.rule.getTitle(), group -> finishJsImport(p, group)));
                } else {
                    // 一键导入：先全部解析，多个则弹窗让用户勾选要导入哪些（带全选）
                    List<HkRuleManager.ParsedRule> parsed = HkRuleManager.get().parseJsonList(text);
                    final List<PickEntry> entries = wrapParsed(parsed, null);
                    App.post(() -> {
                        if (entries.size() == 1) {
                            importPickedRules(entries);
                        } else {
                            showPickRulesDialog(entries);
                        }
                    });
                }
            } catch (Exception e) {
                App.post(() -> Notify.show("导入失败：" + e.getMessage()));
            }
        }).start();
    }

    /** js: 规则导入：分组选择后的落盘（后台线程）。 */
    private void finishJsImport(HkRuleManager.ParsedRule p, String group) {
        new Thread(() -> {
            try {
                boolean isNew = HkRuleManager.get().getInstalledRule(p.rule.getTitle()) == null;
                HkRuleManager.get().saveRule(p.rule, p.json);
                List<HkRule> one = Collections.singletonList(p.rule);
                App.post(() -> {
                    applyImportGroup(one, Collections.singletonList(isNew), group);
                    Notify.show("导入成功：" + p.rule.getTitle());
                    // 并入内存列表：同标题替换旧对象（见 mergeRulesIntoList 注释）
                    mergeRulesIntoList(one);
                    syncCustomOrder();
                    ruleAdapter.rebuildSections();
                    ruleAdapter.notifyDataSetChanged();
                    buildGroupFilterRow();
                    boolean empty = ruleAdapter.getItemCount() == 0;
                    binding.emptyRules.setVisibility(empty ? View.VISIBLE : View.GONE);
                    binding.rvRules.setVisibility(empty ? View.GONE : View.VISIBLE);
                    showOpenAfterImportDialog(one);
                });
            } catch (Exception e) {
                App.post(() -> Notify.show("导入失败：" + e.getMessage()));
            }
        }).start();
    }

    /** 把解析结果包装成勾选项（附带资源统一传入，传 null 表示无）。 */
    private static List<PickEntry> wrapParsed(List<HkRuleManager.ParsedRule> parsed, HkRuleManager.HkZipData hkZipData) {
        List<PickEntry> list = new ArrayList<>(parsed.size());
        for (HkRuleManager.ParsedRule p : parsed) list.add(new PickEntry(p, hkZipData));
        return list;
    }

    /**
     * 把选中的解析结果落盘导入（各 .hkzip 的附带资源按所属包分别落盘）。
     * <p>
     * D4 规则更新检查：先做版本号比对——
     * <ul>
     * <li>导入版本 &gt; 已安装版本：直接更新（成功提示带 v旧→v新）；</li>
     * <li>导入版本 &le; 已安装版本：弹确认框问是否覆盖，避免静默降级。</li>
     * </ul>
     *
     * @param picked 选中的勾选项（UI 线程调用）
     */
    private void importPickedRules(List<PickEntry> picked) {
        if (isFinishing() || isDestroyed()) return;
        if (picked.isEmpty()) {
            Notify.show("未选中任何规则");
            return;
        }
        // G48：任一规则附带 adBlockUrls 时先过广告拦截询问弹窗（官
        // AutoImportHelper 同位、导入执行之前）；无附带则直落原流程，
        // 现行导入三步（选分组→落盘→是否打开）一步不动。
        if (runWithAdBlockGate(collectAdBlockUrls(picked),
                () -> importPickedRulesAfterAdGate(picked))) return;
        importPickedRulesAfterAdGate(picked);
    }

    /** 汇集本批规则附带的广告拦截 URL（官 adBlockUrls 以 "##" 分隔）。 */
    private static List<String> collectAdBlockUrls(List<PickEntry> picked) {
        List<String> out = new ArrayList<>();
        for (PickEntry e : picked) {
            out.addAll(HkAdUrlBlocker.splitAdBlockUrls(e.parsed.rule.getAdBlockUrls()));
        }
        return out;
    }

    /**
     * G48 广告拦截附带询问（官 showWithAdUrlsDialog 的三出口一一对应）：
     * 「确定」＝附带拦截串并入全局拦截表（HkAdUrlBlocker.addUrls）后继续
     * 导入；「不附带拦截」＝只导入规则本体；「取消」＝整单中止导入（官取消
     * 即不导入）。官原形是弹窗内 with/without 切换钮＋确定两步，咱家按本
     * 链弹窗惯例收成三钮一步，出口语义不变。
     *
     * @return true＝已弹窗接管，调用方停手；false＝无附带、未弹窗
     */
    private boolean runWithAdBlockGate(List<String> adUrls, Runnable proceed) {
        if (adUrls == null || adUrls.isEmpty()) return false;
        if (isFinishing() || isDestroyed()) return true;
        new HkDialogs(this)
                .setTitle("温馨提示")
                .setMessage("检测到该小程序规则附带了广告拦截规则，确定立即导入规则吗？")
                .setPositiveButton("确定", (d, w) -> {
                    new Thread(() -> HkAdUrlBlocker.addUrls(adUrls)).start();
                    proceed.run();
                })
                .setNeutralButton("不附带拦截", (d, w) -> proceed.run())
                .setNegativeButton("取消", null)
                .show();
        return true;
    }

    /** importPickedRules 的原体（版本比对→选分组→落盘），广告闸放行后走此。 */
    private void importPickedRulesAfterAdGate(List<PickEntry> picked) {
        if (isFinishing() || isDestroyed()) return;
        final List<PickEntry> downgrade = new ArrayList<>();
        final StringBuilder sb = new StringBuilder();
        for (PickEntry e : picked) {
            HkRule installed = HkRuleManager.get().getInstalledRule(e.parsed.rule.getTitle());
            if (installed != null && HkRule.compareVersions(e.parsed.rule.getVersionRaw(), installed.getVersionRaw()) <= 0) {
                downgrade.add(e);
                if (sb.length() > 0) sb.append('\n');
                sb.append(e.parsed.rule.getTitle())
                        .append("（已安装 v").append(installed.getVersionLabel())
                        .append("，导入 v").append(e.parsed.rule.getVersionLabel()).append("）");
            }
        }
        if (!downgrade.isEmpty()) {
            new HkDialogs(this)
                    .setTitle("版本未更新，是否覆盖？")
                    .setMessage("以下小程序的导入版本不高于已安装版本：\n" + sb + "\n\n覆盖可能导致降级。")
                    .setPositiveButton("全部覆盖", (d, w) -> pickGroupThenImport(picked))
                    .setNegativeButton("跳过这些", (d, w) -> {
                        List<PickEntry> rest = new ArrayList<>(picked);
                        rest.removeAll(downgrade);
                        if (rest.isEmpty()) {
                            Notify.show("已跳过，无需导入");
                            return;
                        }
                        pickGroupThenImport(rest);
                    })
                    .setNeutralButton("取消", null)
                    .show();
            return;
        }
        pickGroupThenImport(picked);
    }

    /**
     * 导入前选分组（忽略规则自带的野分组名），再落盘。UI 线程调用。
     * 多个规则共用一次选择，统一应用。
     */
    private void pickGroupThenImport(List<PickEntry> picked) {
        String title = picked.size() == 1
                ? picked.get(0).parsed.rule.getTitle()
                : "导入 " + picked.size() + " 个小程序";
        showImportGroupPicker(title, group -> doImportPickedRules(picked, group));
    }

    /** importPickedRules 的落盘实现（后台线程）。 */
    private void doImportPickedRules(List<PickEntry> picked, String group) {
        new Thread(() -> {
            try {
                List<HkRule> rules = new ArrayList<>();
                List<Boolean> isNewList = new ArrayList<>();
                List<String> updatedVer = new ArrayList<>();
                Map<HkRuleManager.HkZipData, List<HkRule>> assets = new HashMap<>();
                for (PickEntry e : picked) {
                    HkRule installed = HkRuleManager.get().getInstalledRule(e.parsed.rule.getTitle());
                    boolean isUpdate = installed != null && HkRule.compareVersions(e.parsed.rule.getVersionRaw(), installed.getVersionRaw()) > 0;
                    isNewList.add(installed == null);
                    HkRuleManager.get().saveRule(e.parsed.rule, e.parsed.json);
                    rules.add(e.parsed.rule);
                    if (isUpdate) {
                        updatedVer.add(e.parsed.rule.getTitle() + " v" + installed.getVersionLabel() + "→v" + e.parsed.rule.getVersionLabel());
                    }
                    if (e.hkZipData != null) {
                        List<HkRule> g = assets.get(e.hkZipData);
                        if (g == null) {
                            g = new ArrayList<>();
                            assets.put(e.hkZipData, g);
                        }
                        g.add(e.parsed.rule);
                    }
                }
                for (Map.Entry<HkRuleManager.HkZipData, List<HkRule>> en : assets.entrySet()) {
                    HkRuleManager.get().saveHkZipAssets(en.getKey(), en.getValue());
                }
                boolean wasUpdate = !updatedVer.isEmpty();
                String msg = rules.size() == 1
                        ? (wasUpdate ? "更新成功：" + updatedVer.get(0) : "导入成功：" + rules.get(0).getTitle())
                        : (wasUpdate ? "更新成功 " : "导入成功 ") + rules.size() + " 个小程序";
                final String pickedGroup = group == null ? "" : group;
                App.post(() -> {
                    // 只对新导入的规则应用分组选择；已安装的更新保持原有分组映射不动
                    applyImportGroup(rules, isNewList, pickedGroup);
                    Notify.show(msg);
                    // 并入内存列表：同标题替换旧对象（见 mergeRulesIntoList 注释；
                    // 不走 refreshRules 磁盘重读是怕 getRules() 异常时列表空，
                    // 但只 add 不替换会让更新导入永远摸不到列表里的旧对象）
                    mergeRulesIntoList(rules);
                    syncCustomOrder();
                    ruleAdapter.rebuildSections();
                    ruleAdapter.notifyDataSetChanged();
                    buildGroupFilterRow();
                    boolean empty = ruleAdapter.getItemCount() == 0;
                    binding.emptyRules.setVisibility(empty ? View.VISIBLE : View.GONE);
                    binding.rvRules.setVisibility(empty ? View.GONE : View.VISIBLE);
                    // 更新/添加成功后提示"是否打开"（像海阔原版）
                    showOpenAfterImportDialog(rules);
                });
            } catch (Exception e) {
                App.post(() -> Notify.show("导入失败：" + e.getMessage()));
            }
        }).start();
    }

    /**
     * 导入/更新成功后提示"是否打开"（海阔原版行为）。
     * 单个规则：直接问是否打开该规则；多个：列出供选择。
     */
    private void showOpenAfterImportDialog(List<HkRule> rules) {
        if (rules == null || rules.isEmpty()) return;
        // 在任何页面（列表/内容/详情/搜索）都要能弹出：用当前前台 Activity 的 context，
        // Activity 正在销毁时不弹，避免 WindowManager$BadTokenException。
        if (isFinishing() || isDestroyed()) return;
        if (rules.size() == 1) {
            HkRule rule = rules.get(0);
            new HkDialogs(this)
                    .setTitle(rule.getTitle())
                    .setMessage("是否打开该小程序？")
                    .setPositiveButton("打开", (d, w) -> openRule(rule))
                    .setNegativeButton("取消", null)
                    .show();
        } else {
            String[] titles = new String[rules.size()];
            for (int i = 0; i < rules.size(); i++) titles[i] = rules.get(i).getTitle();
            HkDialogs.cards(this, "是否打开小程序？", titles, "取消",
                    (d, which) -> openRule(rules.get(which)));
        }
    }

    // ================= F-12 规则在线更新 =================
    // 行为口径（他定，MEMORY 明文）：规则有版本号时提示更新（像海阔原版）。
    // 8.83 源码快照（~/workspace/hikerView-source）无整块可搬的规则在线更新实现
    // （ui/search 与 ui/home/model 等文件不在该快照内），以下按口径用现树件接：
    // 来源地址＝云口令导入时落盘的口令，或规则 raw JSON 自带的 updateUrl/update_url；
    // 拉取复用 HkRuleManager.parseByCloudCode / parseJsonList 与 HkHttp.get；
    // 比对复用导入链同款 version 口径（HkRule.compareVersions 分段比原始串，兼容 v 前缀/小数）；
    // 提示与成功后「是否打开」复用 HkDialogs（深色毛玻璃、非白底）+ Notify + showOpenAfterImportDialog。

    /** 规则的在线更新来源：落盘的云口令优先，其次 raw JSON 自带的 updateUrl/update_url；无来源返回 ""。 */
    private String ruleSourceOf(HkRule rule) {
        if (rule == null || TextUtils.isEmpty(rule.getTitle())) return "";
        String code = getPrefs().getString(KEY_RULE_SRC_PREFIX + rule.getTitle(), "");
        if (!TextUtils.isEmpty(code)) return code;
        try {
            String raw = HkRuleManager.get().loadRuleJson(rule.getTitle());
            if (!TextUtils.isEmpty(raw)) {
                JSONObject obj = new JSONObject(raw);
                String u = obj.optString("updateUrl", "");
                if (TextUtils.isEmpty(u)) u = obj.optString("update_url", "");
                if (!TextUtils.isEmpty(u)) return u;
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /** 按来源拉远程规则（须在后台线程调用）：http(s) 地址拉 JSON（数组按标题取同名条）；其余按云口令解析。 */
    private HkRuleManager.ParsedRule fetchRemoteRule(HkRule installed, String source) throws Exception {
        if (source.startsWith("http://") || source.startsWith("https://")) {
            String text = com.fongmi.android.tv.api.hk.HkHttp.get(
                    source, installed == null ? null : installed.resolvedUa(), 15000);
            if (TextUtils.isEmpty(text)) throw new IllegalArgumentException("更新地址返回为空");
            if (HkRuleManager.isJsRuleText(text)) {
                return HkRuleManager.get().parseJsRule(text, installed == null ? null : installed.getTitle());
            }
            List<HkRuleManager.ParsedRule> list = HkRuleManager.get().parseJsonList(text);
            if (list.isEmpty()) throw new IllegalArgumentException("更新地址未解析到规则");
            if (installed != null) {
                for (HkRuleManager.ParsedRule p : list) {
                    if (installed.getTitle().equals(p.rule.getTitle())) return p;
                }
            }
            return list.get(0);
        }
        return HkRuleManager.get().parseByCloudCode(source, "");
    }

    /** 手动检查单个规则更新（卡片长按菜单「检查更新」）：无来源/失败/已最新都给人话提示。 */
    private void checkRuleUpdate(HkRule rule) {
        if (rule == null) return;
        String source = ruleSourceOf(rule);
        if (TextUtils.isEmpty(source)) {
            Notify.show("该小程序没有可更新的来源（不是云口令导入的，也没有自带更新地址）");
            return;
        }
        Notify.show("正在检查更新…");
        new Thread(() -> {
            try {
                HkRuleManager.ParsedRule remote = fetchRemoteRule(rule, source);
                App.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (remote == null || remote.rule == null) {
                        Notify.show("检查更新失败：没拉到规则");
                    } else if (HkRule.compareVersions(remote.rule.getVersionRaw(), rule.getVersionRaw()) > 0) {
                        showRuleUpdateDialog(Collections.singletonList(remote));
                    } else {
                        Notify.show("已是最新版（v" + rule.getVersionLabel() + "）");
                    }
                });
            } catch (Exception e) {
                App.post(() -> Notify.show("检查更新失败：" + e.getMessage()));
            }
        }).start();
    }

    /** 自动检查（列表加载后调，24 小时节流）：有来源的规则逐个拉新版比 version，有新版合并成一个提示弹窗。 */
    private void maybeCheckRuleUpdates() {
        if (ruleUpdateChecking || rules.isEmpty()) return;
        long last = getPrefs().getLong(KEY_UPDATE_LAST_CHECK, 0);
        if (System.currentTimeMillis() - last < 24L * 60 * 60 * 1000) return;
        getPrefs().edit().putLong(KEY_UPDATE_LAST_CHECK, System.currentTimeMillis()).apply();
        ruleUpdateChecking = true;
        final List<HkRule> snapshot = new ArrayList<>(rules);
        new Thread(() -> {
            List<HkRuleManager.ParsedRule> updates = new ArrayList<>();
            for (HkRule r : snapshot) {
                try {
                    String source = ruleSourceOf(r);
                    if (TextUtils.isEmpty(source)) continue;
                    HkRuleManager.ParsedRule remote = fetchRemoteRule(r, source);
                    if (remote != null && remote.rule != null
                            && HkRule.compareVersions(remote.rule.getVersionRaw(), r.getVersionRaw()) > 0) {
                        updates.add(remote);
                    }
                } catch (Throwable ignored) {
                    // 单条拉取失败静默跳过，不打扰列表使用
                }
            }
            App.post(() -> {
                ruleUpdateChecking = false;
                if (!updates.isEmpty() && !isFinishing() && !isDestroyed()) {
                    showRuleUpdateDialog(updates);
                }
            });
        }).start();
    }

    /** 发现新版提示（深色毛玻璃弹窗、字看得清）：列出 v旧→v新，确认后统一落盘更新。 */
    private void showRuleUpdateDialog(List<HkRuleManager.ParsedRule> remotes) {
        if (remotes == null || remotes.isEmpty() || isFinishing() || isDestroyed()) return;
        StringBuilder sb = new StringBuilder();
        for (HkRuleManager.ParsedRule p : remotes) {
            HkRule installed = HkRuleManager.get().getInstalledRule(p.rule.getTitle());
            String oldV = installed == null ? "0" : installed.getVersionLabel();
            if (sb.length() > 0) sb.append('\n');
            sb.append(p.rule.getTitle()).append("（v").append(oldV)
                    .append(" → v").append(p.rule.getVersionLabel()).append("）");
        }
        new HkDialogs(this)
                .setTitle("发现新版")
                .setMessage("以下小程序有新版：\n" + sb + "\n\n是否更新？")
                .setPositiveButton("更新", (d, w) -> applyRuleUpdates(remotes))
                .setNegativeButton("取消", null)
                .show();
    }

    /** 落盘更新（后台线程）：saveRule 后合并进内存列表刷新，并按原版口径问「是否打开」。 */
    private void applyRuleUpdates(List<HkRuleManager.ParsedRule> remotes) {
        new Thread(() -> {
            try {
                List<HkRule> updated = new ArrayList<>();
                for (HkRuleManager.ParsedRule p : remotes) {
                    HkRuleManager.get().saveRule(p.rule, p.json);
                    updated.add(p.rule);
                }
                App.post(() -> {
                    mergeRulesIntoList(updated);
                    syncCustomOrder();
                    ruleAdapter.rebuildSections();
                    ruleAdapter.notifyDataSetChanged();
                    buildGroupFilterRow();
                    String msg = updated.size() == 1
                            ? "更新成功：" + updated.get(0).getTitle()
                            : "更新成功 " + updated.size() + " 个小程序";
                    Notify.show(msg);
                    showOpenAfterImportDialog(updated);
                });
            } catch (Exception e) {
                App.post(() -> Notify.show("更新失败：" + e.getMessage()));
            }
        }).start();
    }

    /**
     * 一键导入勾选弹窗：多选列表（标题+作者）+ 顶部"全选"复选框。
     * 确定后只导入勾选中的规则。
     *
     * @param entries 解析好的全部勾选项
     */
    private void showPickRulesDialog(List<PickEntry> entries) {
        if (isFinishing() || isDestroyed()) return;
        float density = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * density);
        root.setPadding(pad, pad / 2, pad, 0);

        CheckBox cbAll = new CheckBox(this);
        cbAll.setText("全选");
        cbAll.setChecked(true);
        cbAll.setTextColor(0xFFF2F4F8);
        root.addView(cbAll);

        List<String> labels = new ArrayList<>();
        for (PickEntry e : entries) {
            HkRuleManager.ParsedRule p = e.parsed;
            String author = p.rule.getAuthor();
            labels.add(TextUtils.isEmpty(author) ? p.rule.getTitle() : p.rule.getTitle() + "（" + author + "）");
        }
        ListView listView = new ListView(this);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                R.layout.item_hk_pick, labels);
        listView.setAdapter(adapter);
        for (int i = 0; i < labels.size(); i++) listView.setItemChecked(i, true);
        int rowPx = (int) (64 * density);
        int listH = Math.min(rowPx * labels.size(), rowPx * 7);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, listH);
        lp.topMargin = (int) (8 * density);
        listView.setLayoutParams(lp);
        root.addView(listView);

        cbAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (int i = 0; i < labels.size(); i++) listView.setItemChecked(i, isChecked);
        });

        new HkDialogs(this)
                .setTitle("选择要导入的小程序（" + labels.size() + "）")
                .setView(root)
                .setPositiveButton("导入", (d, w) -> {
                    SparseBooleanArray checked = listView.getCheckedItemPositions();
                    List<PickEntry> picked = new ArrayList<>();
                    for (int i = 0; i < labels.size(); i++) {
                        if (checked.get(i)) picked.add(entries.get(i));
                    }
                    importPickedRules(picked);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 取 content uri 的显示文件名（可能为 null）。 */
    private String getDisplayName(Uri uri) {
        try (android.database.Cursor c = getContentResolver().query(
                uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        }
        // DISPLAY_NAME 取不到时以路径末段兜底（file:// 递送常见）：没有名字的
        // .js 会绕过 intent#js# 直落导入（G53 复核订正；ImportActivity 同口径）。
        String segment = uri == null ? null : uri.getLastPathSegment();
        if (segment != null) {
            int slash = segment.lastIndexOf('/');
            if (slash >= 0) segment = segment.substring(slash + 1);
        }
        return segment;
    }

    /** 按后缀或 zip 魔数（PK\x03\x04）判断是否为 zip 包。 */
    private boolean isZipFile(File f, String displayName) {
        if (displayName != null && displayName.toLowerCase().endsWith(".zip")) return true;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] magic = new byte[4];
            int n = in.read(magic);
            return n == 4 && magic[0] == 'P' && magic[1] == 'K' && magic[2] == 3 && magic[3] == 4;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }

    /** 导出规则为云口令：上传云端后显示口令，可复制分享（官方 App 也可导入）。 */
    private void exportAsCloudCode(HkRule rule) {
        Notify.show("正在上传云端…");
        new Thread(() -> {
            try {
                String code = HkRuleManager.get().exportAsCloudCode(rule.getTitle());
                App.post(() -> showExportCodeDialog(rule.getTitle(), code));
            } catch (Exception e) {
                App.post(() -> Notify.show("导出失败：" + e.getMessage()));
            }
        }).start();
    }

    /** 导出为云5短口令：走 cmd.im，生成 云5oooole/{id} 短格式。 */
    private void exportAsShortCloudCode(HkRule rule) {
        Notify.show("正在上传云端…");
        new Thread(() -> {
            try {
                String code = HkRuleManager.get().exportAsCmdImCode(rule.getTitle());
                App.post(() -> showExportCodeDialog(rule.getTitle(), code));
            } catch (Exception e) {
                App.post(() -> Notify.show("导出失败：" + e.getMessage()));
            }
        }).start();
    }

    private void showExportCodeDialog(String title, String code) {
        if (isFinishing() || isDestroyed()) return;
        String full = code + "\n\n小程序：" + title;
        TextView tv = new TextView(this);
        tv.setText(full);
        tv.setTextIsSelectable(true);
        tv.setTextColor(0xFFF2F4F8);
        tv.setPadding(dp(8), dp(8), dp(8), dp(8));
        new HkDialogs(this)
                .setTitle("「" + title + "」的云口令")
                .setMessage("口令已生成，可复制分享给他人导入：")
                .setView(tv)
                .setPositiveButton("复制", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("hk_cloud_code", full));
                        Notify.show("口令已复制");
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    /** 导出为文件：弹窗选择格式（.json / .hkzip / .zip），保存到 Download 目录并调起分享。卡片风（P7）。 */
    private void exportAsFile(HkRule rule) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));
        TextView title = new TextView(this);
        title.setText("选择导出格式");
        title.setTextColor(0xFFF2F4F8);
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        String[] labels = {"导出为 .json 文件", "导出为 .hkzip 包", "导出为 .zip 包"};
        AlertDialog dialog = new HkDialogs(this).setView(root).create();
        for (int i = 0; i < labels.length; i++) {
            TextView tv = new TextView(this);
            tv.setText(labels[i]);
            tv.setTextColor(0xFF1A1D24);
            tv.setTextSize(15);
            tv.setGravity(Gravity.CENTER);
            tv.setBackgroundResource(R.drawable.dialog_option_card_light);
            tv.setPadding(dp(16), dp(14), dp(16), dp(14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(10);
            tv.setLayoutParams(lp);
            final int which = i;
            tv.setOnClickListener(v -> {
                dialog.dismiss();
                if (which == 0) doExportAsJsonFile(rule);
                else if (which == 1) doExportAsHkZip(rule);
                else doExportAsZip(rule);
            });
            root.addView(tv);
        }
        dialog.show();
    }

    /** 导出规则为文件：保存到 Download 目录并调起分享。 */
    private void doExportAsJsonFile(HkRule rule) {
        new Thread(() -> {
            try {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                java.io.File out = HkRuleManager.get().exportAsFile(rule.getTitle(), dir);
                App.post(() -> {
                    Notify.show("已导出到：" + out.getAbsolutePath());
                    try {
                        Intent share = new Intent(Intent.ACTION_SEND);
                        share.setType("application/json");
                        share.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(out));
                        startActivity(Intent.createChooser(share, "分享规则文件"));
                    } catch (Exception e) {
                        android.util.Log.e("HkPage", "export share failed", e);
                        Notify.show("文件已导出，但分享打开失败");
                    }
                });
            } catch (Exception e) {
                App.post(() -> Notify.show("导出失败：" + e.getMessage()));
            }
        }).start();
    }

    /** 导出规则为 .hkzip 包（含附带资源）：保存到 Download 目录并调起分享。 */
    private void doExportAsHkZip(HkRule rule) {
        new Thread(() -> {
            try {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                java.io.File out = HkRuleManager.get().exportAsHkZip(rule.getTitle(), dir);
                App.post(() -> {
                    Notify.show("已导出到：" + out.getAbsolutePath());
                    try {
                        Intent share = new Intent(Intent.ACTION_SEND);
                        share.setType("application/zip");
                        share.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(out));
                        startActivity(Intent.createChooser(share, "分享规则包"));
                    } catch (Exception e) {
                        android.util.Log.e("HkPage", "export share failed", e);
                        Notify.show("文件已导出，但分享打开失败");
                    }
                });
            } catch (Exception e) {
                App.post(() -> Notify.show("导出失败：" + e.getMessage()));
            }
        }).start();
    }

    /** 导出规则为 .zip 包（内容同 .hkzip）：保存到 Download 目录并调起分享。 */
    private void doExportAsZip(HkRule rule) {
        new Thread(() -> {
            try {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                java.io.File out = HkRuleManager.get().exportAsZip(rule.getTitle(), dir);
                App.post(() -> {
                    Notify.show("已导出到：" + out.getAbsolutePath());
                    try {
                        Intent share = new Intent(Intent.ACTION_SEND);
                        share.setType("application/zip");
                        share.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(out));
                        startActivity(Intent.createChooser(share, "分享规则包"));
                    } catch (Exception e) {
                        android.util.Log.e("HkPage", "export share failed", e);
                        Notify.show("文件已导出，但分享打开失败");
                    }
                });
            } catch (Exception e) {
                App.post(() -> Notify.show("导出失败：" + e.getMessage()));
            }
        }).start();
    }

    private void showDeleteRuleConfirm(HkRule rule) {        new HkDialogs(this)
                .setTitle("删除规则")
                .setMessage("确定删除「" + rule.getTitle() + "」吗？")
                .setPositiveButton("删除", (d, w) -> new Thread(() -> {
                    HkRuleManager.get().delete(rule.getTitle());
                    App.post(this::refreshRules);
                }).start())
                .setNegativeButton("取消", null)
                .show();
    }

    private class RuleAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_RULE = 1;
        private static final int TYPE_RULE_COMPACT = 2;
        private final List<Object> items = new ArrayList<>();

        /** 平铺规则：分组筛选（实际分组=自定义覆盖优先） + 关键字过滤 + 名称排序开关（编辑模式下按拖拽顺序，不做名称排序）。 */
        /** 搜索页直接设置数据（不过滤，调用方已过滤）。 */
        void setItems(List<HkRule> list) {
            items.clear();
            items.addAll(list);
        }

        void rebuildSections() {
            items.clear();
            List<HkRule> src = new ArrayList<>(rules);
            if (!TextUtils.isEmpty(ruleGroup)) {
                Iterator<HkRule> it = src.iterator();
                while (it.hasNext()) {
                    HkRule r = it.next();
                    if (!ruleGroup.equals(effectiveGroup(r))) it.remove();
                }
            }
            if (!TextUtils.isEmpty(ruleKeyword)) {
                String kw = ruleKeyword.toLowerCase(Locale.getDefault());
                Iterator<HkRule> it = src.iterator();
                while (it.hasNext()) {
                    HkRule r = it.next();
                    String t = r.getTitle() == null ? "" : r.getTitle().toLowerCase(Locale.getDefault());
                    if (!t.contains(kw)) it.remove();
                }
            }
            if (ruleSortByName && !ruleManageMode) {
                Collections.sort(src, (a, b) -> {
                    String ta = a.getTitle() == null ? "" : a.getTitle();
                    String tb = b.getTitle() == null ? "" : b.getTitle();
                    return ta.compareTo(tb);
                });
            }
            items.addAll(src);
        }

        /** 拖拽换位：同步 adapter 显示列表与底层 rules 列表，持久化自定义顺序。 */
        void moveItem(int from, int to) {
            HkRule moved = (HkRule) items.get(from);
            HkRule other = (HkRule) items.get(to);
            Collections.swap(items, from, to);
            notifyItemMoved(from, to);
            int ix = -1, iy = -1;
            for (int i = 0; i < rules.size(); i++) {
                String t = rules.get(i).getTitle();
                if (t.equals(moved.getTitle())) ix = i;
                if (t.equals(other.getTitle())) iy = i;
            }
            if (ix >= 0 && iy >= 0 && ix != iy) {
                Collections.swap(rules, ix, iy);
                customOrder = new ArrayList<>();
                for (HkRule r : rules) customOrder.add(r.getTitle());
                saveCustomOrder();
            }
        }

        class Holder extends RecyclerView.ViewHolder {
            TextView icon, name, tag;
            android.widget.ImageView ivIcon;
            SwitchCompat sw;
            ImageButton more;
            View cardBg;

            Holder(View v) {
                super(v);
                icon = v.findViewById(R.id.tv_icon);
                ivIcon = v.findViewById(R.id.iv_icon);
                name = v.findViewById(R.id.tv_name);
                tag = v.findViewById(R.id.tv_group_tag);
                sw = v.findViewById(R.id.sw_enable);
                more = v.findViewById(R.id.btn_more);
                cardBg = v.findViewById(R.id.card_bg);
            }
        }

        @Override
        public int getItemViewType(int position) {
            // 3列模式卡片窄，用紧凑布局保证名字可读；2列用原布局
            return ruleGrid != null && ruleGrid.getSpanCount() == 3 ? TYPE_RULE_COMPACT : TYPE_RULE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout = viewType == TYPE_RULE_COMPACT ? R.layout.item_hk_rule_compact : R.layout.item_hk_rule;
            View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
            // 液态玻璃：卡片按压反馈（发亮＋微缩 6%＋回弹），背景在 onBind 换玻璃件
            HkPressFeedback.attach(v);
            return new Holder(v);
        }

        /** 图标：URL 则加载图片，# 开头则按主色做渐变瓷砖，否则默认"海"字（v5 质感）。 */
        private void applyIconBg(Holder h, String title, String iconDef) {
            TextView icon = h.icon;
            android.widget.ImageView ivIcon = h.ivIcon;
            // 查表顺序照 8.83 GlideUtil.getGlideUrl：先查内置图映射（hiker://images/*），
            // 命中直接摆内置图；未命中再走 URL 链；都落空才走文字兜底。
            if (!TextUtils.isEmpty(iconDef) && iconDef.startsWith("hiker://images/")) {
                int builtinId = HkImageUrlMap.getIdByUrl(ivIcon.getContext(), iconDef);
                if (builtinId > 0) {
                    icon.setVisibility(View.GONE);
                    ivIcon.setVisibility(View.VISIBLE);
                    ivIcon.setClipToOutline(true);
                    ivIcon.setBackgroundResource(R.drawable.hk_miniapp_icon_bg);
                    // holder 复用时可能有在飞的 Glide 请求，先清掉防旧图回写，再摆确定的本地资源
                    com.bumptech.glide.Glide.with(ivIcon).clear(ivIcon);
                    ivIcon.setImageResource(builtinId);
                    return;
                }
            }
            // 图标形态：http(s) 地址、data: 内嵌图（很多规则的图标是 base64 内嵌，原先被当非图丢了）、
            // hiker://files/ 本地文件（先按引擎同一映射解析成真实路径）。其余才走文字兜底。
            String imgSrc = null;
            if (!TextUtils.isEmpty(iconDef)) {
                if (iconDef.startsWith("http://") || iconDef.startsWith("https://") || iconDef.startsWith("data:")) {
                    imgSrc = iconDef;
                } else if (iconDef.startsWith("hiker://files/")) {
                    String p = com.fongmi.android.tv.api.hk.HkJsRuntime.getFilePath(iconDef);
                    if (!TextUtils.isEmpty(p) && new java.io.File(p).exists()) imgSrc = p;
                }
            }
            if (imgSrc != null) {
                // 图片图标：显示图片，隐藏"海"字
                icon.setVisibility(View.GONE);
                ivIcon.setVisibility(View.VISIBLE);
                // 圆角裁剪跟文字版一致
                ivIcon.setClipToOutline(true);
                ivIcon.setBackgroundResource(R.drawable.hk_miniapp_icon_bg);
                // 小程序列表图标走 8.83 同款链：图标自身 URL 当 ruleUrl
                // （getGlideUrl(getIcon(), getIcon())：@js= 剥离+流式解密、各 @后缀剥离组装），
                // 异步失败回退默认图标（loadRuleIcon 内部兜底，8.83"永不空白砖"同款）。
                // "海"字只留给 icon 字段本来就没可用 URL 的规则（下面的分支），图片分支不再回退"海"字。
                com.fongmi.android.tv.utils.ImgUtil.loadRuleIcon(title, imgSrc, ivIcon);
                return;
            }
            // 非 URL：显示"海"字（v5 质感：渐变底＋顶部内高光）
            ivIcon.setVisibility(View.GONE);
            icon.setVisibility(View.VISIBLE);
            int color = parseIconColor(iconDef);
            if (color == -1) color = 0xFFF6EED6;
            icon.setBackground(iconTileDrawable(color));
        }

        /** 图标主色："#" 开头解析色值；图片图标/无定义返回 -1（调用方用标题首字色板）。 */
        private int parseIconColor(String iconDef) {
            if (!TextUtils.isEmpty(iconDef) && iconDef.startsWith("#")) {
                try {
                    return android.graphics.Color.parseColor(iconDef);
                } catch (Exception ignored) {
                }
            }
            return -1;
        }

        /** 把 tint 按 ratio 染到 base 上（0=全 base，1=全 tint）。 */
        private static int blend(int base, int tint, float ratio) {
            float r = Math.max(0f, Math.min(1f, ratio));
            int red = Math.round(android.graphics.Color.red(base) * (1 - r) + android.graphics.Color.red(tint) * r);
            int green = Math.round(android.graphics.Color.green(base) * (1 - r) + android.graphics.Color.green(tint) * r);
            int blue = Math.round(android.graphics.Color.blue(base) * (1 - r) + android.graphics.Color.blue(tint) * r);
            return android.graphics.Color.argb(0xFF, red, green, blue);
        }

        /** 主色提亮（瓷砖渐变上端）。 */
        private static int lighten(int color, float ratio) {
            return blend(0xFFFFFFFF, color, ratio);
        }

        /** 图标瓷砖 drawable：渐变底（提亮→主色）＋顶部内高光，圆角 10dp。 */
        private android.graphics.drawable.Drawable iconTileDrawable(int color) {
            android.graphics.drawable.GradientDrawable base = new android.graphics.drawable.GradientDrawable();
            base.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM);
            base.setColors(new int[]{lighten(color, 0.45f), color});
            base.setCornerRadius(dp(10));
            android.graphics.drawable.GradientDrawable gloss = new android.graphics.drawable.GradientDrawable();
            gloss.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM);
            gloss.setColors(new int[]{0x59FFFFFF, 0x00FFFFFF});
            gloss.setCornerRadius(dp(10));
            return new android.graphics.drawable.LayerDrawable(
                    new android.graphics.drawable.Drawable[]{base, gloss});
        }

        /** 淡染主色板：图片图标/无色值定义时，按标题首字哈希取，保证图标砖有主色可用（卡面淡染已随液态玻璃去掉）。 */
        private static final int[] TINT_PALETTE = {
                0xFFC9970D, 0xFF2E7FD9, 0xFF35A06B, 0xFFD95F4B, 0xFF8E6BC8, 0xFF2AA5A0
        };

        private static int paletteColorFor(String title) {
            int h = 0;
            if (title != null && !title.isEmpty()) h = Math.abs(title.charAt(0));
            return TINT_PALETTE[h % TINT_PALETTE.length];
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder vh, int position) {
            Holder h = (Holder) vh;
            HkRule rule = (HkRule) items.get(position);
            String title = rule.getTitle();
            h.name.setText(android.text.TextUtils.isEmpty(title) ? "未命名规则" : title);
            applyIconBg(h, title, rule.getIcon());
            // 液态玻璃卡面：共享缓冲裁剪（圆角 20dp），整卡淡染已去（样张是白透玻璃＋彩色图标砖）；图标砖取色保留
            if (h.cardBg != null) {
                if (!(h.cardBg.getBackground() instanceof HkGlassDrawable))
                    h.cardBg.setBackground(new HkGlassDrawable(h.cardBg.getContext(), 20));
            } else if (h.itemView instanceof androidx.cardview.widget.CardView) {
                androidx.cardview.widget.CardView cv = (androidx.cardview.widget.CardView) h.itemView;
                cv.setCardElevation(0);
                cv.setCardBackgroundColor(android.graphics.Color.TRANSPARENT);
                if (!(cv.getBackground() instanceof HkGlassDrawable))
                    cv.setBackground(new HkGlassDrawable(cv.getContext(), 20));
            }
            float alpha = rule.isEnabled() ? 1f : 0.5f;
            h.icon.setAlpha(alpha);
            if (h.ivIcon != null) h.ivIcon.setAlpha(alpha);
            // 禁用态：图标半透示意，文字保持可读（0.75f），深色字必须看得清
            h.name.setAlpha(rule.isEnabled() ? 1f : 0.75f);
            // 管理模式：显示启用开关 + 分组标签；⋮ 按钮已去掉（长按卡片出菜单）
            h.sw.setVisibility(ruleManageMode ? View.VISIBLE : View.GONE);
            h.more.setVisibility(View.GONE);
            h.tag.setVisibility(ruleManageMode ? View.VISIBLE : View.GONE);
            if (ruleManageMode) {
                String g = effectiveGroup(rule);
                if (TextUtils.isEmpty(g)) {
                    h.tag.setText("未分组");
                    h.tag.setTextColor(0xFF8A8F98);
                    h.tag.setBackgroundResource(R.drawable.hk_group_tag_empty);
                } else {
                    h.tag.setText(stripGroupPrefix(g));
                    h.tag.setTextColor(0xFF8A6D00);
                    h.tag.setBackgroundResource(R.drawable.hk_group_tag);
                }
            }
            h.sw.setOnCheckedChangeListener(null);
            h.sw.setChecked(rule.isEnabled());
            h.sw.setOnCheckedChangeListener((btn, checked) -> {
                int pos = h.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                Object o = items.get(pos);
                if (!(o instanceof HkRule)) return;
                HkRule r = (HkRule) o;
                r.setEnabled(checked);
                HkRuleManager.get().setEnabled(r.getTitle(), checked);
                notifyItemChanged(pos);
            });
            bindSelectBadge(h.itemView, rule);
            // 多选模式：点/长按卡片都改为勾选；编辑模式：点卡片分配分组（长按拖拽排序）；
            // 普通模式：点卡片打开小程序，长按弹出菜单（编辑/分享/删除）
            if (ruleSelectMode) {
                h.itemView.setOnClickListener(v -> toggleRuleSelect(rule));
                h.itemView.setOnLongClickListener(v -> {
                    toggleRuleSelect(rule);
                    return true;
                });
            } else if (ruleManageMode) {
                h.itemView.setOnClickListener(v -> showGroupPicker(rule));
                h.itemView.setOnLongClickListener(null);
            } else {
                h.itemView.setOnClickListener(v -> openRule(rule));
                h.itemView.setOnLongClickListener(v -> {
                    showRuleCardMenu(rule);
                    return true;
                });
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    /** 卡片 ⋮ 菜单：编辑 / 检查更新 / 分享 / 删除（选项独立卡片框、有间距）。 */
    private void showRuleCardMenu(HkRule rule) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));
        TextView title = new TextView(this);
        title.setText(rule.getTitle());
        title.setTextColor(0xFFF2F4F8);
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        String[] labels = {"编辑", "检查更新", "分享", "删除"};
        AlertDialog dialog = new HkDialogs(this).setView(root).create();
        for (int i = 0; i < labels.length; i++) {
            TextView tv = new TextView(this);
            tv.setText(labels[i]);
            tv.setTextColor(0xFF1A1D24);
            tv.setTextSize(16);
            tv.setGravity(Gravity.CENTER);
            tv.setBackgroundResource(R.drawable.dialog_option_card_light);
            tv.setPadding(dp(16), dp(14), dp(16), dp(14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(10);
            final int which = i;
            tv.setOnClickListener(v -> {
                dialog.dismiss();
                if (which == 0) showRuleFormEditor(rule);
                else if (which == 1) checkRuleUpdate(rule);
                else if (which == 2) showRuleShareOptions(rule);
                else showDeleteRuleConfirm(rule);
            });
            root.addView(tv, lp);
        }
        dialog.show();
    }

    /** 分享：云口令分享 / 文件分享（独立卡片选项）。 */
    private void showRuleShareOptions(HkRule rule) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));
        String[] labels = {"云口令分享", "文件分享"};
        AlertDialog dialog = new HkDialogs(this).setView(root).create();
        for (int i = 0; i < labels.length; i++) {
            TextView tv = new TextView(this);
            tv.setText(labels[i]);
            tv.setTextColor(0xFF1A1D24);
            tv.setTextSize(16);
            tv.setGravity(Gravity.CENTER);
            tv.setBackgroundResource(R.drawable.dialog_option_card_light);
            tv.setPadding(dp(16), dp(14), dp(16), dp(14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.topMargin = dp(10);
            final int which = i;
            tv.setOnClickListener(v -> {
                dialog.dismiss();
                if (which == 0) showCloudChannelPicker(rule);
                else exportAsFile(rule);
            });
            root.addView(tv, lp);
        }
        dialog.show();
    }

    /** 云口令分享：先选择云通道（对照 8.83 的 7 个云剪贴板），再走对应上传流程。 */
    private void showCloudChannelPicker(HkRule rule) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));
        TextView title = new TextView(this);
        title.setText("选择云口令通道");
        title.setTextColor(0xFFA8B0C0);
        title.setTextSize(13);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(10));
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        // 8.83 原文的 7 个云剪贴板：云1 pastebin、云2 textdb、云5 cmd.im、云6 pasteme、云7 note.ms、云9 txtpad、云10 hastebin
        String[] labels = {"云1 · pastebin", "云2 · textdb（官方通用）", "云5 · cmd.im（短口令）",
                "云6 · pasteme（白阑）", "云7 · note.ms", "云9 · txtpad", "云10 · hastebin"};
        int[] clipNums = {1, 2, 5, 6, 7, 9, 10};
        AlertDialog dialog = new HkDialogs(this).setView(root).create();
        for (int i = 0; i < labels.length; i++) {
            TextView tv = new TextView(this);
            tv.setText(labels[i]);
            tv.setTextColor(0xFF1A1D24);
            tv.setTextSize(16);
            tv.setGravity(Gravity.CENTER);
            tv.setBackgroundResource(R.drawable.dialog_option_card_light);
            tv.setPadding(dp(16), dp(14), dp(16), dp(14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(10);
            final int which = i;
            tv.setOnClickListener(v -> {
                dialog.dismiss();
                int clipNum = clipNums[which];
                if (clipNum == 2) exportAsCloudCode(rule);
                else if (clipNum == 5) exportAsShortCloudCode(rule);
                else exportAsCloudClipCode(rule, clipNum);
            });
            root.addView(tv, lp);
        }
        dialog.show();
    }

    /**
     * 导出为云口令（云1/6/7/9/10）：走 HkCloudClip 上传，生成 8.83 格式口令，
     * 8.83 官方 App 可导入。
     */
    private void exportAsCloudClipCode(HkRule rule, int clipNum) {
        Notify.show("正在上传云端…");
        new Thread(() -> {
            try {
                String json = HkRuleManager.get().loadRuleJson(rule.getTitle());
                if (json == null || json.trim().isEmpty())
                    throw new IllegalArgumentException("规则不存在：" + rule.getTitle());
                String url = com.fongmi.android.tv.api.hk.HkShareHelper.shareToCloudClip(json, clipNum);
                String code = buildCloudCode(clipNum, url);
                App.post(() -> showExportCodeDialog(rule.getTitle(), code));
            } catch (Exception e) {
                App.post(() -> Notify.show("导出失败：" + e.getMessage()));
            }
        }).start();
    }

    /** 按 8.83 分享格式，由上传返回的 URL 生成云口令（8.83 parse 原文的逆操作）。 */
    private String buildCloudCode(int clipNum, String url) {
        String id = url.substring(url.lastIndexOf('/') + 1).split("[\\s?#]")[0];
        switch (clipNum) {
            case 1: return "云1oooole/raw/" + id;         // 8.83 PastebinImporter.parse: 云1oooole→https://pastebin.com，要求 https://pastebin.com/raw/ 开头
            case 6: return "云6oooole/xxxxxx/" + id;       // 8.83 BailanImporter: 云6oooole/xxxxxx/{path}(@{pwd})
            case 7: return "云7oooole/" + id;              // 8.83 NotesImporter.share: https://note.ms/→云7oooole
            case 9: return "云9oooole/" + id;              // 8.83 TxtPadImporter.parse: 云9oooole→https://txtpbbd.cn
            case 10: return "云10oooole/share/a/b/" + id; // 8.83 HastebinImporter: 云10oooole/share/a/b/
            default: return url;
        }
    }

    /** 表单式编辑小程序（按 8.83 ArticleListRuleEditActivity）：四个可折叠分区，保存写回 rule.json。 */
    private void showRuleFormEditor(HkRule rule) {
        if (isFinishing() || isDestroyed()) return;
        final JSONObject obj;
        try {
            String raw = HkRuleManager.get().loadRuleJson(rule.getTitle());
            if (raw == null || raw.trim().isEmpty()) throw new IllegalArgumentException("规则文件不存在");
            obj = new JSONObject(raw);
        } catch (Exception e) {
            com.fongmi.android.tv.utils.ToastUtil.show(this, "规则读取失败：" + e.getMessage());
            return;
        }
        final String oldTitle = rule.getTitle();
        final Map<String, EditText> fields = new HashMap<>();

        final Dialog dialog = new Dialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0x00000000);

        // ---- 顶栏：取消 / 编辑小程序 / 保存 ----
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0x00000000);
        bar.setPadding(dp(6), dp(8), dp(6), dp(8));
        TextView btnCancel = new TextView(this);
        btnCancel.setText("取消");
        btnCancel.setTextColor(0xFF8A8F98);
        btnCancel.setTextSize(16);
        btnCancel.setPadding(dp(14), dp(10), dp(14), dp(10));
        TextView tvTitle = new TextView(this);
        tvTitle.setText("编辑小程序");
        tvTitle.setTextColor(0xFFF2F4F8);
        tvTitle.setTextSize(17);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        TextView btnSave = new TextView(this);
        btnSave.setText("保存");
        btnSave.setTextColor(0xFFB8890F);
        btnSave.setTextSize(16);
        btnSave.setTypeface(null, android.graphics.Typeface.BOLD);
        btnSave.setPadding(dp(14), dp(10), dp(14), dp(10));
        bar.addView(btnCancel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        bar.addView(tvTitle, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(btnSave, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        View divider = new View(this);
        divider.setBackgroundColor(0x33FFFFFF);
        root.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        // ---- 滚动表单 ----
        ScrollView sv = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(8), dp(4), dp(32));
        sv.addView(form, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        int text = EditorInfo.TYPE_CLASS_TEXT;
        int ml = EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;

        // 基础规则
        LinearLayout secBasic = addFormSection(form, "基础规则");
        addFormField(secBasic, fields, "title", "小程序名称", "小程序名称（title）", obj, null, false, text);
        addFormPicker(secBasic, fields, "type", "小程序类别", "小程序类别（type）", obj, "其它",
                new String[]{"视频", "音乐", "漫画", "小说", "图片", "工具", "其它"});
        addFormField(secBasic, fields, "author", "小程序作者", "小程序作者（author）", obj, null, false, text);
        addFormField(secBasic, fields, "version", "小程序版本", "小程序版本（version）", obj, "1", false, EditorInfo.TYPE_CLASS_NUMBER);
        addFormField(secBasic, fields, "group", "小程序分组", "小程序分组（名称前加①、②等序号可排序）", obj, null, false, text);
        addFormField(secBasic, fields, "icon", "小程序图标地址", "小程序图标地址（支持色值）", obj, null, false, text);
        addFormField(secBasic, fields, "url", "小程序链接", "小程序链接（可不填，可只填搜索相关内容）", obj, null, false, text);
        addFormPicker(secBasic, fields, "col_type", "显示样式", "显示样式（col_type）", obj, "movie_3", COL_TYPE_OPTIONS);

        // 分类规则
        LinearLayout secClass = addFormSection(form, "分类规则");
        addFormField(secClass, fields, "class_name", "分类名称", "分类名称（class_name）", obj, null, false, text);
        addFormField(secClass, fields, "class_url", "分类替换词", "分类替换词（class_url）", obj, null, false, text);
        addFormField(secClass, fields, "area_name", "地区名称", "地区名称（area_name）", obj, null, false, text);
        addFormField(secClass, fields, "area_url", "地区替换词", "地区替换词（area_url）", obj, null, false, text);
        addFormField(secClass, fields, "year_name", "年代名称", "年代名称（year_name）", obj, null, false, text);
        addFormField(secClass, fields, "year_url", "年代替换词", "年代替换词（year_url）", obj, null, false, text);
        addFormField(secClass, fields, "sort_name", "排序名称", "排序名称（sort_name）", obj, null, false, text);
        addFormField(secClass, fields, "sort_url", "排序替换词", "排序替换词（sort_url）", obj, null, false, text);

        // 解析规则
        LinearLayout secJiexi = addFormSection(form, "解析规则");
        addFormField(secJiexi, fields, "find_rule", "解析规则", "解析规则（列表;标题;图片;描述;链接）", obj, null, true, ml);
        addFormField(secJiexi, fields, "search_url", "搜索链接", "搜索链接（**作为占位符，支持/开头不写域名）", obj, null, false, text);
        addFormField(secJiexi, fields, "searchFind", "搜索解析规则", "搜索解析规则（列表;标题;链接;描述;详情;图片）", obj, null, true, ml);
        addFormPicker(secJiexi, fields, "detail_col_type", "二级列表显示样式", "二级列表显示样式（detail_col_type）", obj, "movie_1", COL_TYPE_OPTIONS);
        addFormField(secJiexi, fields, "detail_find_rule", "二级列表解析规则", "二级列表解析规则（列表;标题;图片;描述;链接）", obj, null, true, ml);
        addFormPicker(secJiexi, fields, "sdetail_col_type", "搜索二级显示样式", "搜索二级列表显示样式（sdetail_col_type）", obj, "movie_1", COL_TYPE_OPTIONS);
        addFormField(secJiexi, fields, "sdetail_find_rule", "搜索二级解析规则", "搜索二级列表解析规则（列表;标题;图片;描述;链接）", obj, null, true, ml);
        addFormField(secJiexi, fields, "last_chapter_rule", "最新章节解析规则", "最新章节解析规则", obj, null, true, ml);

        // 其它规则
        LinearLayout secOther = addFormSection(form, "其它规则");
        addFormPicker(secOther, fields, "ua", "全局UA", "全局UA（以url里面的ua优先）", obj, "auto",
                new String[]{"auto", "mobile", "pc"});
        addFormField(secOther, fields, "preRule", "JS预处理", "JS预处理（在解析规则和搜索解析规则执行前执行）", obj, null, true, ml);
        addFormField(secOther, fields, "proxy", "网络请求代理", "网络请求代理（一行一个，替换词=>代理）", obj, null, true, ml);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> saveRuleForm(dialog, obj, fields, oldTitle));

        dialog.setContentView(root);
        android.view.Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setBackgroundDrawableResource(R.drawable.dialog_glass_dark);
            w.getDecorView().setPadding(0, 0, 0, 0);
            w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();
    }

    /** 表单分区（8.83 风格）：灰色标题可折叠 + 白色圆角卡片。 */
    private LinearLayout addFormSection(LinearLayout parent, String title) {
        final TextView header = new TextView(this);
        header.setTextColor(0xFFA8B0C0);
        header.setTextSize(14);
        header.setPadding(dp(16), dp(14), dp(16), dp(6));
        parent.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.dialog_option_card_light);
        card.setPadding(dp(14), dp(4), dp(14), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(12);
        lp.rightMargin = dp(12);
        parent.addView(card, lp);
        final boolean[] expanded = {true};
        header.setText(title + "  ▲");
        header.setOnClickListener(v -> {
            expanded[0] = !expanded[0];
            card.setVisibility(expanded[0] ? View.VISIBLE : View.GONE);
            header.setText(title + (expanded[0] ? "  ▲" : "  ▼"));
        });
        return card;
    }

    /** 表单字段：小标签 + 输入框（值缺失时用 defVal）。 */
    private EditText addFormField(LinearLayout parent, Map<String, EditText> fields, String key,
                                  String label, String hint, JSONObject obj, String defVal,
                                  boolean multiLine, int inputType) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(0xFF8A8F98);
        tv.setTextSize(12);
        tv.setPadding(0, dp(10), 0, dp(4));
        parent.addView(tv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        EditText et = new EditText(this);
        et.setBackgroundResource(R.drawable.bg_input_light);
        et.setPadding(dp(12), dp(10), dp(12), dp(10));
        et.setTextSize(14);
        et.setTextColor(0xFFF2F4F8);
        et.setHint(hint);
        et.setHintTextColor(0xFFB0B6BE);
        et.setInputType(inputType);
        if (multiLine) {
            et.setSingleLine(false);
            et.setMinLines(3);
            et.setGravity(Gravity.START | Gravity.TOP);
        }
        String val = obj.has(key) ? obj.optString(key, "") : (defVal == null ? "" : defVal);
        et.setText(val);
        parent.addView(et, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        fields.put(key, et);
        return et;
    }

    /** 表单选择器：不可编辑的输入框，点击弹出选项卡片（独立卡片+间距，当前选中金色加粗）。 */
    private void addFormPicker(LinearLayout parent, Map<String, EditText> fields, String key,
                               String label, String hint, JSONObject obj, String defVal, String[] options) {
        final EditText et = addFormField(parent, fields, key, label, hint, obj, defVal,
                false, EditorInfo.TYPE_CLASS_TEXT);
        et.setFocusable(false);
        et.setFocusableInTouchMode(false);
        et.setClickable(true);
        et.setOnClickListener(v -> {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(20), dp(16), dp(20), dp(16));
            TextView tvTitle = new TextView(this);
            tvTitle.setText(label);
            tvTitle.setTextColor(0xFFF2F4F8);
            tvTitle.setTextSize(16);
            tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            tvTitle.setGravity(Gravity.CENTER);
            root.addView(tvTitle, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            AlertDialog dlg = new HkDialogs(this).setView(root).create();
            ScrollView sv = new ScrollView(this);
            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            sv.addView(list, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(420));
            svLp.topMargin = dp(10);
            root.addView(sv, svLp);
            String cur = et.getText().toString();
            for (String op : options) {
                TextView tv = new TextView(this);
                tv.setText(op);
                boolean sel = op.equals(cur);
                tv.setTextColor(sel ? 0xFFB8890F : 0xFF1A1D24);
                tv.setTypeface(null, sel ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                tv.setTextSize(15);
                tv.setGravity(Gravity.CENTER);
                tv.setBackgroundResource(R.drawable.dialog_option_card_light);
                tv.setPadding(dp(16), dp(12), dp(16), dp(12));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(8);
                tv.setOnClickListener(v2 -> {
                    dlg.dismiss();
                    et.setText(op);
                });
                list.addView(tv, lp);
            }
            dlg.show();
        });
    }

    /** 表单保存：写回 JSON → importJson 校验落盘 → 改名则删旧文件并迁移分组/排序/开关 → 刷新列表。 */
    private void saveRuleForm(Dialog dialog, JSONObject obj, Map<String, EditText> fields, String oldTitle) {
        String newTitle = fields.get("title").getText().toString().trim();
        if (newTitle.isEmpty()) {
            com.fongmi.android.tv.utils.ToastUtil.show(this, "请完善规则，title不能为空");
            return;
        }
        if (!newTitle.matches("[^/:*?\"<>|]+")) {
            com.fongmi.android.tv.utils.ToastUtil.show(this, "规则名不能包含/:*?\"<>|等特殊字符");
            return;
        }
        try {
            for (Map.Entry<String, EditText> e : fields.entrySet()) {
                String k = e.getKey();
                String val = e.getValue().getText().toString();
                if ("version".equals(k)) {
                    int vv = obj.optInt("version", 1);
                    try {
                        vv = Integer.parseInt(val.trim());
                    } catch (Exception ignored) {
                    }
                    obj.put(k, vv);
                } else {
                    obj.put(k, val);
                }
            }
        } catch (Exception e) {
            com.fongmi.android.tv.utils.ToastUtil.show(this, "保存失败：" + e.getMessage());
            return;
        }
        final String json = obj.toString();
        final boolean wasEnabled = HkRuleManager.get().isEnabled(oldTitle);
        new Thread(() -> {
            try {
                HkRuleManager.get().importJson(json);
                if (!oldTitle.equals(newTitle)) {
                    HkRuleManager.get().delete(oldTitle);
                    HkRuleManager.get().setEnabled(newTitle, wasEnabled);
                    App.post(() -> migrateRuleTitle(oldTitle, newTitle));
                }
                App.post(() -> {
                    dialog.dismiss();
                    refreshRules();
                    com.fongmi.android.tv.utils.ToastUtil.show(HkPageActivity.this, "已保存");
                });
            } catch (Exception e) {
                App.post(() -> com.fongmi.android.tv.utils.ToastUtil.show(
                        HkPageActivity.this, "保存失败：" + e.getMessage()));
            }
        }).start();
    }

    /** 表单编辑改名后：迁移分组映射与自定义排序。 */
    private void migrateRuleTitle(String oldTitle, String newTitle) {
        if (ruleGroupMap.containsKey(oldTitle)) {
            ruleGroupMap.put(newTitle, ruleGroupMap.remove(oldTitle));
            saveRuleGroupMap();
        }
        if (customOrder != null) {
            int i = customOrder.indexOf(oldTitle);
            if (i >= 0) {
                customOrder.set(i, newTitle);
                saveCustomOrder();
            }
        }
        buildGroupFilterRow();
    }

    // ================= V2 分类+列表 =================


    /**
     * 8.83 LoadingEvent 弹窗等价实现（XPopup asLoading 同款）：
     * 小居中盒（#212121 实底 + 16dp 圆角 + 22dp 内边距 + 28dp 菊花 + 14sp #EEEEEE 单行字）
     * + 全屏 50% 黑阴影（XPopup 默认 shadowBgColor #7F000000）。
     * 之前把深色底设在了全屏 root 上，导致整屏近黑——这就是"满屏"bug 的根因。
     * 对齐 8.83 MainActivity.showLoading 的 XPopup 默认行为：
     * 1) 未调 dismissOnTouchOutside/dismissOnBackPressed → 默认都为 true，点外部/返回键可关闭；
     * 2) asLoading(title) 里 title 为空时只显示菊花、标题 TextView gone，不补默认文案；
     * 3) 已显示时只 setTitle 更新文字（此处复用 dialog + setText 等价）。
     * 规则 preRule 里调 showLoading(text) 时弹出，hideLoading() 时关闭。
     */
    private void showHkLoading(String text) {
        try {
            if (hkLoadingDialog == null) {
                // 外层：透明全屏，只做居中
                LinearLayout root = new LinearLayout(this);
                root.setOrientation(LinearLayout.VERTICAL);
                root.setGravity(Gravity.CENTER);
                // 内层小盒：wrap_content，深色底只画在这个小盒上
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
                // 对齐 XPopup 默认：dismissOnTouchOutside/dismissOnBackPressed 均为 true
                hkLoadingDialog.setCancelable(true);
                hkLoadingDialog.setCanceledOnTouchOutside(true);
                if (hkLoadingDialog.getWindow() != null) {
                    hkLoadingDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                    hkLoadingDialog.getWindow().setDimAmount(0.5f);
                }
            }
            // 对齐 XPopup LoadingPopupView.setup()：title 为空时标题 gone 只显示菊花，不补默认文案
            if (TextUtils.isEmpty(text)) {
                hkLoadingText.setVisibility(View.GONE);
            } else {
                hkLoadingText.setVisibility(View.VISIBLE);
                hkLoadingText.setText(text);
            }
            if (!hkLoadingDialog.isShowing() && !isFinishing()) hkLoadingDialog.show();
            // 转圈互斥：弹窗转圈出现时压掉页面内转圈，一个页面只许一个转圈（用户点名）
            syncInlineSpinner(true);
            // 兜底：弹窗转圈最长 60 秒自动关（脚本中途死掉没人关时，不许它永远转）
            hkLoadingWatchdog.removeCallbacks(hkLoadingWatchdogRun);
            hkLoadingWatchdog.postDelayed(hkLoadingWatchdogRun, 60000);
        } catch (Throwable ignored) {
        }
    }

    private final android.os.Handler hkLoadingWatchdog =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable hkLoadingWatchdogRun = this::hideHkLoading;

    /** 弹窗转圈与页面内转圈互斥：弹窗在时页面转圈藏起；弹窗关时若页面仍在加载则恢复页面转圈 */
    private void syncInlineSpinner(boolean dialogShowing) {
        // 旧内页已删：页面内转圈无宿主，保持空转（弹窗转圈不受影响）
    }

    private void hideHkLoading() {
        try {
            hkLoadingWatchdog.removeCallbacks(hkLoadingWatchdogRun);
            if (hkLoadingDialog != null && hkLoadingDialog.isShowing()) hkLoadingDialog.dismiss();
            syncInlineSpinner(false);
        } catch (Throwable ignored) {
        }
    }

    private void openRule(HkRule rule) {
        // "我的主页"与普通规则同一条路：照常经 startRule 打开（home.js 在引擎
        // 作用域跑通、main/setuppage 可达，dig2-bridge-home 账已证伪旧拦截判据）。
        // 【重做切换】进规则改走 8.83 式新模块（HkListActivity 独立页，ui/hk）；
        // 本页只保留小程序首页（V_RULES 规则列表，冻结不动）。下方旧内页流程不再可达，
        // 旧 Fragment（HkContentFragment/HkDetailFragment/HkSearchFragment）的宿主回调
        // 只由它们自身触发、随之成为死代码，待整体删除时一并清理。
        if (rule != null) {
            com.fongmi.android.tv.ui.hk.HkListActivity.startRule(this, rule, rule.getTitle());
            return;
        }
        destroyRouterAsync();
        subStack.clear(); // 切规则时丢弃子列表栈
        currentRule = rule;
        hkPageUrl = null; // 切规则：实际抓取页 URL 失效，下次 loadContent 重记
        // preRule loading 桥：先注册再 loadContent（引擎 init 里跑 preRule，规则调 showLoading 时弹窗）。
        // 切规则时旧弹窗先关，避免残留。
        hideHkLoading();
        HkLoading.setListener(hkLoadingListener);
        cls = "";
        area = "";
        year = "";
        sort = "";
        clsName = "";
        areaName = "";
        yearName = "";
        sortName = "";
        page = 1;
        noMore = false;
        loading = false;
        contentGen++;
        videos.clear();
        contentTitle = rule.getTitle();
        // 旧视图已迁入 Fragment：只做视图切换，内容加载由 HkContentFragment 经 hostLoadContent 触发
        if (stack.isEmpty() || stack.peek() != V_CONTENT) {
            pushView(V_CONTENT);
        } else {
            showView(V_CONTENT);
        }
    }

    /**
     * 8.83 同款：内容页顶部小程序切换 tabs。
     * 横向显示已安装的小程序，点击切换规则。
     */

    /**
     * 小程序切换：顶部 tabs 栏最右「全部」按钮弹窗。
     * 星辰风格：毛玻璃底 + 独立选项卡片。8.83 内容页无此功能。
     */
    private void showMiniappSwitchDialog() {
        if (miniappRules == null || miniappRules.size() <= 1) return;
        final String curTitle = currentRule == null ? null : currentRule.getTitle();

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
            if (target != null && currentRule != null && !target.getTitle().equals(currentRule.getTitle())) {
                openRule(target);
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

    /** 「全部」弹窗的 2 列选项卡片适配器：独立卡片框、有间距，当前选中高亮。 */
    private static class MiniappSwitchAdapter extends RecyclerView.Adapter<MiniappSwitchAdapter.Holder> {
        interface OnPick {
            void onPick(HkRule rule);
        }

        private final List<HkRule> rules;
        private final String curTitle;
        private final OnPick listener;

        MiniappSwitchAdapter(List<HkRule> rules, String curTitle, OnPick listener) {
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
            Context ctx = parent.getContext();
            float d = ctx.getResources().getDisplayMetrics().density;
            TextView tv = new TextView(ctx);
            tv.setBackgroundResource(R.drawable.dialog_option_card_light);
            tv.setGravity(Gravity.CENTER);
            tv.setMaxLines(1);
            tv.setEllipsize(TextUtils.TruncateAt.END);
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

    /**
     * 8.83 同款：内容页内嵌搜索栏。
     * 规则有搜索能力时显示，输入关键词点搜索跳搜索页。
     */

    /** 规则内容里是否自带 input 搜索框（col_type="input"）。 */
    private boolean hasRuleInputBox() {
        try {
            for (HkItem it : videos) {
                String ct = it.getColType();
                if (ct != null && "input".equals(ct.trim().toLowerCase())) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** 内嵌搜索条显隐：规则有搜索能力且内容里没有自带 input 框时才显示。 */


    /** 内容页内嵌搜索栏：带关键词直接打开搜索页并执行搜索。 */
    private void openSearchWithKeyword(String kw) {
        searchDualMode = false;
        keyword = kw != null ? kw : "";
        searchPage = 1;
        searchNoMore = false;
        searchResults.clear();
        searchSrcRules.clear();
        // 搜索 UI 已迁入 HkSearchActivity：带词直接进单规则搜索并执行（见 doSearch）
        doSearch(keyword);
    }

    /**
     * 8.83 同款静态分类 tab 行。
     * 原文（ArticleListFragment.smali .line 1272-1290）：class_name/area_name/year_name/sort_name
     * 非空即注入一行 header（type="header"，desc="class"/"area"/"year"/"sort"），title/url 按 "&" 切分；
     * ArticleListAdapter.bindForHeaderHolder（smali:2492，.line 1133-1242）渲染：横向 tab + 右侧 ">"，
     * ">" 点开"请选择分类" 3 列弹窗（lambda$bindForHeaderHolder$11，smali:10064）。
     * 点击 tab：onClassClick（ArticleListFragment$5.smali:306，.line 1434-1457）按 desc 把该 tab 的
     * url 值写入对应变量、page=1 重载；选中名记 pic 用于高亮（此处记 *Name 字段）。
     * 原版无"全部"tab，不自动添加。
     */

    /** 建一行分类 tab（横向 RecyclerView + 右侧 ">"），无数据返回 0。 */

    /**
     * 8.83 onClassClick 等价：按行 kind 把选中 tab 的 url 值写入对应变量
     * （fyclass/fyarea/fyyear/fysort 替换用），选中名记入 *Name（8.83 setPic 等价），
     * 重建 tab 行刷新高亮（8.83 updateHeaderHolder 等价），page=1 重载。
     */
    private void onCategoryTabPick(String kind, List<String[]> pairs, String url) {
        String name = "";
        for (String[] p : pairs) {
            if (p[1].equals(url)) {
                name = p[0];
                break;
            }
        }
        if (name.isEmpty() && !pairs.isEmpty()) name = pairs.get(0)[0];
        setValOf(kind, url);
        setNameOf(kind, name);
        buildCategoryRow();
    }

    private String valOf(String kind) {
        switch (kind) {
            case "area": return area;
            case "year": return year;
            case "sort": return sort;
            default: return cls;
        }
    }

    private String nameOf(String kind) {
        switch (kind) {
            case "area": return areaName;
            case "year": return yearName;
            case "sort": return sortName;
            default: return clsName;
        }
    }

    private void setValOf(String kind, String v) {
        switch (kind) {
            case "area": area = v; break;
            case "year": year = v; break;
            case "sort": sort = v; break;
            default: cls = v; break;
        }
    }

    private void setNameOf(String kind, String v) {
        switch (kind) {
            case "area": areaName = v; break;
            case "year": yearName = v; break;
            case "sort": sortName = v; break;
            default: clsName = v; break;
        }
    }

    /**
     * V2 动态分类 tab：规则 find_rule 返回的 scroll_button/flex_button 条目。
     * 8.83 原文行为（ArticleListAdapter.bindForHeaderHolder）：
     * 每条目独立成一行，title/url 按 "&" 切分为行内 tabs，每行右侧带 ">" 按钮，
     * 点击 ">" 弹出"请选择分类"（3 列竖向网格）。
     * 标题去 HTML 标签；含高亮色（#ff1493/#FF5D50 等）的为当前选中。
     * 点击 tab 求值其 lazyRule（putMyVar 设分类变量），再刷新列表。
     */

    /** tab 是否为选中态：标题含高亮色标记（8.83 规则常用 #ff1493/#FF5D50/#ff0000 等）。 */
    private boolean isTabSelected(String rawTitle) {
        if (rawTitle == null) return false;
        String l = rawTitle.toLowerCase();
        return l.contains("#ff1493") || l.contains("#ff5d50") || l.contains("#ff0000")
                || l.contains("color=\"red\"") || l.contains("color='red'");
    }

    /**
     * 8.83 同款"请选择分类"弹窗：居中，3 列竖向网格。
     * （ArticleListAdapter.lambda$bindForHeaderHolder$11，CustomCenterRecyclerViewPopup，
     *  item_rect_radius.xml center=true 浅色模式）
     */
    private void showCategoryPopup(List<String[]> pairs, int selected, ChipAdapter.OnPick listener) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("请选择分类");
        title.setTextColor(0xFFF2F4F8);
        title.setTextSize(20);
        title.setMaxLines(1);
        title.setGravity(android.view.Gravity.CENTER);
        int tp = dp(15);
        title.setPadding(tp, tp, tp, tp);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new GridLayoutManager(this, 3));
        int rp = dp(10);
        rv.setPadding(rp, rp, rp, rp);
        rv.setClipToPadding(false);
        root.addView(rv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 选项卡片风格弹窗（与现有选项弹窗统一：独立卡片+间距）
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(root)
                .create();
        // 点选项后关闭弹窗
        rv.setAdapter(new CategoryAdapter(pairs, selected, url -> {
            dialog.dismiss();
            listener.onPick(url);
        }));
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_glass_dark);
        }
        dialog.show();
    }

    /** 8.83 同款分类弹窗按钮适配器（对照 item_rect_radius.xml）。 */
    private static class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.Holder> {
        private final List<String[]> pairs;
        private final int selected;
        private final ChipAdapter.OnPick listener;

        CategoryAdapter(List<String[]> pairs, int selected, ChipAdapter.OnPick listener) {
            this.pairs = pairs;
            this.selected = selected;
            this.listener = listener;
        }

        static class Holder extends RecyclerView.ViewHolder {
            TextView text;
            Holder(View v) {
                super(v);
                text = v.findViewById(R.id.tv_category);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hk_category, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            String[] pair = pairs.get(position);
            h.text.setText(pair[0]);
            // 8.83：选中项文字变红（redAction #ffed424b）
            h.text.setTextColor(position == selected ? 0xFFED424B : 0xFF222222);
            h.itemView.setOnClickListener(v -> {
                try {
                    int pos = h.getAdapterPosition();
                    if (pos >= 0 && pos < pairs.size() && listener != null) {
                        listener.onPick(pairs.get(pos)[1]);
                    }
                } catch (Exception e) {
                    android.util.Log.e("HkPage", "CategoryAdapter click failed", e);
                }
            });
        }

        @Override
        public int getItemCount() {
            return pairs.size();
        }
    }

    /**
     * 8.83 原文没有筛选行：地区/年份/类型就是分类 tab 行（class/area/year/sort 四行 header，
     * 见 buildCategoryRow）。此方法保留空实现（调用点不动），避免与 tab 行重复显示。
     */


    private HkRouter getRouter() throws Exception {
        if (router == null) router = new HkRouter(currentRule);
        // 图片 @js= 求值依赖当前规则的 $.require/pages，登记当前 runtime（8.83 JSEngine.getInstance() 同款语义）
        com.fongmi.android.tv.api.hk.HkImage.setCurrentRuntime(router.getEngine().getJsRuntime());
        return router;
    }

    /** 当前规则页 URL（8.83 GlideUtil.getGlideUrl 的 ruleUrl 参数：自动 Referer 用）。
     * 优先返回 loadContent/detail 时实际抓取的页面 URL（与图片同域时自动带 Referer，防盗链不 403）；
     * 拿不到时回退规则静态 URL。 */
    private String hkRuleUrl() {
        if (!TextUtils.isEmpty(hkPageUrl)) return hkPageUrl;
        return currentRule == null ? null : currentRule.getUrl();
    }

    /**
     * 漫画 pics 分支的页面地址：实际抓取页（hkPageUrl）优先；记录页缺失时
     * 回退规则地址，但规则 host 必须与至少一张图的 host 同域相关（相等或互含，
     * 与 ImgUtil 自动 Referer 的域判定同口径）才认——无关域宁可返回空串，
     * 让组装层走自动判定，不拿规则文件地址冒充页面上下文。
     */
    private String hkComicPageUrl(java.util.List<String> pics) {
        if (!TextUtils.isEmpty(hkPageUrl)) return hkPageUrl;
        String ruleUrl = currentRule == null ? "" : currentRule.getUrl();
        if (TextUtils.isEmpty(ruleUrl)) return "";
        String ruleHost = hostOfUrl(ruleUrl);
        if (ruleHost.isEmpty() || pics == null) return "";
        for (String p : pics) {
            String h = hostOfUrl(p);
            if (!h.isEmpty() && (ruleHost.equals(h) || ruleHost.contains(h) || h.contains(ruleHost))) {
                return ruleUrl;
            }
        }
        return "";
    }

    private static String hostOfUrl(String url) {
        try {
            String h = android.net.Uri.parse(url).getHost();
            return h == null ? "" : h;
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** 记录实际抓取的页面 URL（图片 Referer 用）。JS 规则取引擎最后一次 fetch 的真实 URL；非 JS 规则用展开后的模板 URL。 */
    private void recordHkPageUrl(int p, String c, String a, String y, String s) {
        try {
            String u = getRouter().getEngine().getJsRuntime().getLastUrl();
            if (!TextUtils.isEmpty(u) && (u.startsWith("http://") || u.startsWith("https://"))) {
                hkPageUrl = u;
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (currentRule != null) {
                hkPageUrl = com.fongmi.android.tv.api.hk.HkHttp.expandUrl(currentRule.getUrl(), c, a, y, s, p);
            }
        } catch (Throwable ignored) {
        }
    }

    private int contentGen = 0;

    /**
     * 8.83 对齐（ArticleListFragment.lambda$initView$3）：滚动自动翻页仅当规则支持分页。
     * url 含 fypage（或 #autoPage#/#autoReader#）→ 支持；js: 规则靠 MY_PAGE 分页
     *（url 常为 hiker://empty## 不带 fypage 标记，如 find_rule 为 js:$.require("engine").home()）。
     * 不支持时直接标到底，不发无效请求（8.83 finishLoadMore 同款，避免把第 1 页重复追加）。
     */
    private boolean canAutoPage() {
        if (currentRule == null) return false;
        String u = currentRule.getUrl();
        if (u != null && (u.contains("fypage") || u.contains("#autoPage#") || u.contains("#autoReader#"))) return true;
        String fr = currentRule.getFindRule();
        return fr != null && fr.trim().startsWith("js:");
    }


    private void updateFooter() {
        // 搬不动：8.83 的页脚视图在本页布局里已随内容页迁出（现树内容页为 HkListActivity/
        // HkArticleFragment，页脚由其自身管理），本宿主无对应 View 可刷；唯一调用方
        // mergeActionItems 同属已迁出的旧内容页链（contentAdapter 恒为 null，实际不可达）。
    }

    // ================= V3 搜索 =================


    private void openSearch() {
        searchDualMode = false;
        keyword = "";
        searchPage = 1;
        searchNoMore = false;
        searchResults.clear();
        searchSrcRules.clear();
        // 搜索 UI 已迁入 HkSearchActivity（8.83 SearchActivity/SearchInOneRuleActivity 等价）：
        // 单规则模式进单规则搜索页，不再 push 本页的空 V_SEARCH 容器。
        if (currentRule != null) {
            HkSearchActivity.startSearchInOneRule(this, currentRule, "");
        } else {
            HkSearchActivity.startGlobalSearch(this, "");
        }
    }

    /** 小程序内搜索的输入框 hint：带上规则名，如"搜索一个APP"。 */
    private String searchHint() {
        if (!searchDualMode && currentRule != null && currentRule.getTitle() != null) {
            return "搜索" + currentRule.getTitle();
        }
        return "输入关键词搜索";
    }

    /** V_SEARCH 作用域标识：单规则模式显示"在「X」内搜索"，跨搜模式隐藏。 */
    private void updateSearchScopeLabel() {
        // 搬不动：作用域标识视图已随搜索页迁入 HkSearchActivity（其分组/模式标识即等价物），
        // 本宿主布局无此 View；searchHint()/histKey() 仍按 searchDualMode + currentRule 计算。
    }

    /** 搜索历史按作用域隔离：小程序内搜索用各规则独立的 key，跨搜用全局 key。 */
    private String histKey() {
        if (!searchDualMode && currentRule != null && currentRule.getTitle() != null
                && !currentRule.getTitle().isEmpty()) {
            return KEY_HIST + "_" + currentRule.getTitle();
        }
        return KEY_HIST;
    }

    /**
     * 点 🔍：顶栏下方展开 inline 磨砂搜索条，输入实时过滤首页小程序卡片，不跳页；再点收起。
     */
    private void toggleInlineSearch() {
        if (binding.inlineSearchBar.getVisibility() == View.VISIBLE) {
            closeInlineSearch();
        } else {
            binding.inlineSearchBar.setVisibility(View.VISIBLE);
            updateRulesTopPadding();
            binding.etInlineSearch.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(binding.etInlineSearch, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    /** 关闭 inline 搜索：清空关键词、恢复全部卡片、收起键盘。 */
    private void closeInlineSearch() {
        binding.inlineSearchBar.setVisibility(View.GONE);
        binding.etInlineSearch.setText("");
        ruleKeyword = "";
        ruleAdapter.rebuildSections();
        ruleAdapter.notifyDataSetChanged();
        updateRulesTopPadding();
        hideKeyboard();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(binding.etInlineSearch.getWindowToken(), 0);
        binding.etInlineSearch.clearFocus();
    }

    /**
     * 长按 🔍：跨小程序内容搜索（只留"搜内容"模式）。
     * 8.83 口径：跨搜即打开聚合搜索页（SearchActivity）——全部带 search_url 的规则各成一个
     * 发动机（HkSearchEngine.fromRule）、每发动机一个结果页（HkSearchFragment 经
     * HkSearchModel 按页拉取聚合）、分组筛选与精准/默认模式在搜索页内切换。
     * 现树该页已由 HkSearchActivity 整块承接，本宿主只负责以跨搜模式打开它，
     * 不再在本页 push 空的 V_SEARCH 容器（旧 doCrossSearch 的本页并发聚合实现随之作废，
     * 其结果分组/来源标注/分页语义在 HkSearchActivity + HkSearchFragment 内等价实现）。
     */
    private void openContentSearch() {
        searchDualMode = true;
        keyword = "";
        searchPage = 1;
        searchNoMore = false;
        searchResults.clear();
        searchSrcRules.clear();
        HkSearchActivity.startGlobalSearch(this, "");
    }

    /**
     * 搜内容：跨小程序内容搜索（8.83 聚合搜索口径）。
     * 并发聚合、按小程序分组、来源标注、点条目切来源规则进详情，全部由 HkSearchActivity
     * 的多发动机 ViewPager 承接（每发动机一个 HkSearchFragment，状态条标注发动机名即来源，
     * 条目点击经 SearchHost.openSearchRulePage 以来源规则开页）；本宿主只递交关键词。
     */
    private void doCrossSearch(String kw) {
        searchDualMode = true;
        keyword = kw != null ? kw : "";
        searchPage = 1;
        searchNoMore = false;
        searchLoading = false;
        searchResults.clear();
        searchSrcRules.clear();
        crossSearchGen.incrementAndGet();
        if (!keyword.isEmpty()) saveHistory(keyword);
        HkSearchActivity.startGlobalSearch(this, keyword);
    }

    /** 执行搜索：跨搜走 doCrossSearch；单规则走单规则搜索页（当前规则为唯一发动机）。 */
    private void doSearch(String kw) {
        if (searchDualMode) {
            doCrossSearch(kw);
            return;
        }
        keyword = kw != null ? kw : "";
        searchPage = 1;
        searchNoMore = false;
        searchLoading = false;
        searchResults.clear();
        searchSrcRules.clear();
        if (!keyword.isEmpty()) saveHistory(keyword);
        if (currentRule != null) {
            HkSearchActivity.startSearchInOneRule(this, currentRule, keyword);
        } else {
            HkSearchActivity.startGlobalSearch(this, keyword);
        }
    }

    private void doSearchPage() {
        // 搬不动（宿主侧）：分页已随搜索页迁入 HkSearchFragment——search_url 含 fypage 才挂
        // loadMore、页码自增后经 HkSearchModel 拉下一页、到底态见 F-13；
        // 本宿主无结果列表与滚动容器，不存在可翻的页。保留方法仅为旧调用链签名兼容。
        if (searchNoMore || searchLoading) return;
        searchPage++;
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private List<String> loadHistory() {
        List<String> out = new ArrayList<>();
        String s = getPrefs().getString(histKey(), "");
        if (!s.isEmpty()) {
            for (String part : s.split("")) {
                if (!part.isEmpty()) out.add(part);
            }
        }
        return out;
    }

    private void saveHistory(String kw) {
        List<String> hist = loadHistory();
        hist.remove(kw);
        hist.add(0, kw);
        while (hist.size() > 20) hist.remove(hist.size() - 1);
        getPrefs().edit().putString(histKey(), TextUtils.join("", hist)).apply();
        refreshHistory();
    }

    private void refreshHistory() {
        // 搬不动：历史词 UI 已随搜索页迁出本宿主（本页布局无历史列表视图）；
        // 历史的存/取仍由 saveHistory/loadHistory 按 histKey() 作用域落盘，本方法无视图可刷。
    }

    // ================= V4 详情 =================


    private void openDetail(HkItem item, boolean fromSearch) {
        openDetail(item, fromSearch, false);
    }

    /**
     * 点封面后的下方小字"加载中"提示（海阔原版 toast 风格，不挡封面）。
     * 进播放器/推 V4/报错时自动消失。
     */
    private void showLoadingHint() {
        hideLoadingHint();
        com.fongmi.android.tv.utils.ToastUtil.show(this, "加载中…", android.widget.Toast.LENGTH_LONG);
    }

    private void hideLoadingHint() {
        com.fongmi.android.tv.utils.ToastUtil.dismiss();
    }

    /**
     * V4 详情。
     *
     * @param lazyPrecheck true=条目 URL 自带 {@code @lazyRule=}（如粉嫩小BB点封面即播）：
     *                     先后台求值，不预推 V4；若判定为直接播放则根本不进 V4，
     *                     避免 V4 loading 闪一下。非直接播放时再补推 V4 展示结果。
     */
    private void openDetail(HkItem item, boolean fromSearch, boolean lazyPrecheck) {
        // 官方 clickItem：@rule= 条目进子列表页（dealRule），不进 V4；各调用方统一在此拦截。
        if (item != null && !TextUtils.isEmpty(item.getUrl())) {
            String u = item.getUrl().trim();
            // P1：hiker://page/ 子页面走 openPage（官方 toNextPage），不进 V4
            if (u.startsWith("hiker://page/") && openPage(item, u)) return;
            int ar = u.indexOf("@rule=");
            if (ar >= 0 && !u.substring(0, ar).contains("@lazyRule=")) {
                if (openSubList(item, u)) return;
            }
        }
        detailItem = item;
        detailFromSearch = fromSearch;
        // 旧详情页已删：详情由新链承载，此处仅保留状态
    }

    /**
     * 直接播放：只把被点击的这 1 条目作为选集传给播放器。
     * 列表页其他视频不是当前视频的选集，不能伪装成多集（否则出现 1-20/21-40 等假选集）。
     */
    private void startHkDirectPlay(HkItem item, String directUrl) {
        org.json.JSONArray arr = new org.json.JSONArray();
        try {
            if (item != null && !TextUtils.isEmpty(item.getUrl())) {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("name", stripHtml(item.getTitle()));
                o.put("url", item.getUrl());
                o.put("pic", item.getPic() == null ? "" : item.getPic());
                arr.put(o);
            }
        } catch (Throwable ignored) {
        }
        VideoActivity.startHkPlay(HkPageActivity.this,
                currentRule == null ? "" : currentRule.getTitle(), "默认",
                directUrl, item.getTitle(), item.getTitle(), item.getPic(),
                arr.toString(), 0);
    }

    /**
     * dealWithUrl 分流处理（官方 ArticleListFragment.dealWithUrl）：
     * pics=漫画图片列表 / x5=webview规则 / web=网页 / image=图片查看 / magnet=分享。
     */
    private void handleDealUrl(String kind, String url, HkItem item) {
        if (isFinishing() || isDestroyed()) return;
        if (TextUtils.isEmpty(url)) return;
        try {
            switch (kind) {
                case "pics": {
                    // pics://url1&&url2... → 直接进漫画阅读器（阶段二：不再只弹第一张图）
                    // 拆分收口共用 HkRouter.parsePics（清标记、按行再按 "&&" 拆、
                    // trim 滤空段，与 SiteApi 链同口径，不许再各自裸 split）。
                    java.util.ArrayList<String> urls =
                            new java.util.ArrayList<>(HkRouter.parsePics(url));
                    if (!HkRouter.hasLoadablePic(urls)) {
                        // 入口无图不许拦（fix4）：阅读器自带回退解析（会话图→章节串
                        // →router.play 重取→书源），入口一拦全灭——1.4.19 真机
                        // 「漫画详情页都进不去」的根。只强制留底，照常组会话进阅读器。
                        com.github.catvod.crawler.SpiderDebug.forceLog("hk",
                                "pics empty at page entry (proceed) rule=%s pics=%d page=%s",
                                currentRule == null ? "" : currentRule.getTitle(),
                                urls.size(), hkPageUrl);
                    }
                    ReadingSession s = new ReadingSession();
                    s.kind = ReadingSession.Kind.COMIC;
                    String ruleTitle = currentRule == null ? "" : currentRule.getTitle();
                    s.sourceName = ruleTitle;
                    s.ruleTitle = ruleTitle;
                    s.bookTitle = stripHtml(item.getTitle());
                    s.images = urls;
                    s.referer = hkRuleUrl();
                    // 页面地址另存 pageUrl（图片组装的真上下文）：优先实际抓取页；
                    // 记录页缺失回退规则地址时先验同域——规则文件常托管在无关域
                    // （规则 CDN），拿它当页面地址只会算出假域，无关时宁可留空
                    // 走自动判定（复核报告 §四附带风险），不再无条件冒充。
                    s.pageUrl = hkComicPageUrl(urls);
                    // 本入口没有 play 解析，实际有的请求头只有页面 Referer：
                    // 喂进 playHeaders，让阅读器组图的整套头补缺对跨域防盗链图
                    // 也有数据可补（此前三入口 playHeaders 恒为空集，复核报告 §4）。
                    if (!TextUtils.isEmpty(s.pageUrl)) s.playHeaders.put("Referer", s.pageUrl);
                    ReadingSession.Chapter c = new ReadingSession.Chapter(s.bookTitle, url);
                    c.pic = item.getPic() == null ? "" : item.getPic();
                    s.chapters.add(c);
                    s.chapterIndex = 0;
                    // 有阅读进度则从上次位置继续
                    ReadingStore.Record prog = ReadingStore.find("COMIC", ruleTitle, s.bookTitle);
                    int startPage = (prog != null && prog.position > 0) ? prog.position : 0;
                    ReadingRouter.openReadingWithStartPage(this, s, startPage);
                    return;
                }
                case "x5": {
                    // x5://url → 内嵌 webview 规则页
                    String target = url.replaceFirst("(?i)^x5://", "").trim();
                    if (!target.isEmpty()) showWebViewDialog(target);
                    return;
                }
                case "web": {
                    // 电台问询前置（定根账候选二）：电台规则的条目被 dealKind 判成
                    // web 时，先给电台路由器一次认领机会，不直接开网页。认领信号
                    // 两路（claimSignal）：规则名命中交 playContentDirect（其内部
                    // startHkPlay 电台先于音乐问）；条目标题命中（规则名不含电台
                    // 字时，如「一个APP」里的电台条目）由本处直问
                    // routeHkWithTitles。本落点拿不到子页标题，页标题传空串、
                    // 只用规则名＋条目标题两路，不硬凑。都没中或直问未成，照旧
                    // 开网页，一行不变。
                    String rt = currentRule == null ? "" : currentRule.getTitle();
                    String iTitle = item == null || item.getTitle() == null ? "" : item.getTitle();
                    int sig = com.fongmi.android.tv.ui.HkRadioRouter.claimSignal(rt, "", iTitle);
                    if (sig == 1) {
                        com.github.catvod.crawler.SpiderDebug.log("hk", "page web->radio-ask rule=%s", rt);
                        playContentDirect(item, url);
                        return;
                    }
                    if (sig > 1) {
                        com.github.catvod.crawler.SpiderDebug.log("hk", "page web->radio-ask-title rule=%s sig=%d", rt, sig);
                        try {
                            org.json.JSONArray arr = new org.json.JSONArray();
                            org.json.JSONObject o = new org.json.JSONObject();
                            o.put("name", iTitle);
                            o.put("url", url);
                            o.put("pic", item.getPic() == null ? "" : item.getPic());
                            arr.put(o);
                            String line = item.getLine() == null || item.getLine().isEmpty()
                                    ? "默认" : item.getLine();
                            if (com.fongmi.android.tv.ui.HkRadioRouter.routeHkWithTitles(this,
                                    rt, "", iTitle, line, url, iTitle, iTitle,
                                    item.getPic() == null ? "" : item.getPic(),
                                    arr.toString(), 0)) {
                                return;
                            }
                        } catch (Throwable ignored) {
                        }
                        // 直问未成：落回下面的网页支
                    }
                    com.github.catvod.crawler.SpiderDebug.log("hk", "page web-landing rule=%s", rt);
                    showWebViewDialog(url);
                    return;
                }
                case "image": {
                    android.widget.ImageView iv = new android.widget.ImageView(this);
                    com.fongmi.android.tv.utils.ImgUtil.load(item.getTitle(), url, iv, false, 0, 0, hkRuleUrl());
                    new HkDialogs(this)
                            .setView(iv)
                            .setPositiveButton("关闭", (d, w) -> d.dismiss())
                            .show();
                    return;
                }
                case "magnet": {
                    if (HkRouter.isTorrentLink(url)) {
                        // 种子支：与 fragment 面同落点 startParseTorrent（宿主 hooks →
                        // playThunderLink 迅雷链）；dealKind 已把 http .torrent 归 magnet
                        // 名义，本支须在 isP2pPlayLink 门（只认 scheme 四类）之前分流。
                        new HkDetailBridge().startParseTorrent(this, url);
                        return;
                    }
                    if (HkRouter.isP2pPlayLink(url)) {
                        // 用户拍板（相对原版的有意分叉）：列表点磁力/电驴进自家播放器播，
                        // 走迅雷引擎；标题用条目自带名字，不做文件名清理。
                        playP2pInApp(item, url);
                        return;
                    }
                    // 走到这里说明该 magnet 类地址未进 isP2pPlayLink 门（现门已含
                    // magnet:/ed2k:/thunder:/ftp://，此分支只作兜底）：仍走系统选应用。
                    android.content.Intent it2 = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(url));
                    try {
                        startActivity(android.content.Intent.createChooser(it2, "选择应用打开"));
                    } catch (Throwable e) {
                        ToastUtil.show(this, "无应用可打开", android.widget.Toast.LENGTH_SHORT);
                    }
                    return;
                }
                default:
                    break;
            }
        } catch (Throwable e) {
            android.util.Log.d("HkPage", "handleDealUrl failed: " + e.getMessage());
        }
        // 未知种类：回退 V4
        pushView(V_DETAIL);
        showDetailResult(null);
    }

    /**
     * V4 结果展示：8.83 原版走通用列表渲染器（FilmListActivity + ArticleListAdapter），
     * 详情条目按 col_type 直接渲染，不做线路/选集分类。
     */
    private void showDetailResult(HkDetail result) {
    }

    /**
     * 8.83 原版：详情原始条目直接丢进通用 ContentAdapter 按 col_type 渲染。
     * - 头部：首条不是 header 型时，用点击条目的标题/封面合成 detail_header（标题左+封面右）
     * - 简介：无文本条目但有 content 时，补 long_text
     * - 播放按钮：原始条目原样渲染，点击在详情页内直接播放（不套娃进详情）
     */
    private void bindDetailItems(HkDetail detail) {
    }

    /** 详情原始条目 → 通用列表条目（8.83 原版：不做线路/选集分类，原样渲染）。 */
    private List<HkItem> buildDetailHkItems(HkDetail detail) {
        List<HkItem> out = new ArrayList<>();
        List<HkDetailItem> raw = detail.getRawItems();
        // 1. 头部：首条不是 header 型时合成
        boolean hasHeader = !raw.isEmpty() && isHeaderColType(raw.get(0).getColType());
        if (!hasHeader && (!TextUtils.isEmpty(detail.getTitle()) || !TextUtils.isEmpty(detail.getPic()))) {
            HkItem header = new HkItem();
            header.setColType("detail_header");
            header.setTitle(detail.getTitle());
            header.setPic(detail.getPic());
            out.add(header);
        }
        // 2. 原始条目原样转换
        boolean hasText = false;
        for (HkDetailItem di : raw) {
            HkItem hi = new HkItem();
            hi.setTitle(di.getTitle());
            hi.setUrl(di.getUrl());
            hi.setPic(di.getPic());
            hi.setDesc(di.getDesc());
            hi.setColType(di.getColType());
            hi.setLine(di.getLine());
            out.add(hi);
            String ct = di.getColType() == null ? "" : di.getColType().toLowerCase();
            if (ct.contains("long_text") || ct.startsWith("text_") || ct.contains("rich_text")) hasText = true;
        }
        // 3. 简介回退：无文本条目但有 content 时补 long_text
        if (!hasText && !TextUtils.isEmpty(detail.getContent())) {
            HkItem syn = new HkItem();
            syn.setColType("long_text");
            syn.setDesc(detail.getContent());
            out.add(out.isEmpty() ? 0 : 1, syn);
        }
        return out;
    }

    /** 详情头型 col_type：已有头部条目时不再合成。口径唯一实现在 HkEpisodeQualifier。 */
    private boolean isHeaderColType(String colType) {
        return HkEpisodeQualifier.isHeaderColType(colType);
    }

    /**
     * 8.83 原版 dealWithUrl：详情页内点可播 URL 直接播放，不套娃进详情。
     * 先过 HkRouter.play() 解析（处理 @lazyRule=/@rule=js: 等），再进播放器；
     * 选集列表取当前详情所有可播条目，支持播放器内切集。
     */
    private void playDetailEpisode(HkItem it, String url) {
        new Thread(() -> {
            String playUrl = url;
            boolean parseFailed = false;
            try {
                if (url.contains("@lazyRule=") || url.contains("@rule=") || url.startsWith("js:")) {
                    HkPlay play = getRouter().play(url, it.getExtra());
                    if (!play.getUrl().isEmpty()) playUrl = play.getUrl();
                }
            } catch (Throwable e) {
                // 解析抛错不许拿未解析的原串顶替播放地址：置空走下方既有「解析失败」提示
                parseFailed = true;
            }
            if (parseFailed) playUrl = "";
            // 8.83 getChapters 官形（HkChapters 移植，账 §十）：以被点条目为锚
            // 只收相邻同 col_type 连续段（或 cls 含 playlist 的同组），段外条目
            // 结构上进不来——旧「整页扫＋逐条资格判定」必然拉进别的区块、
            // 只能不断加判定堵漏（他点名「遇一个补一个」），整段换官形。
            int clickedPos = -1;
            for (int i = 0; i < detailItems.size(); i++) {
                if (detailItems.get(i) == it) {
                    clickedPos = i;
                    break;
                }
            }
            HkChapters.Collected chapters = HkChapters.collect(detailItems, clickedPos);
            org.json.JSONArray arr = new org.json.JSONArray();
            int selIdx = Math.max(0, chapters.selectedIndex);
            for (HkItem di : chapters.items) {
                String du = di.getUrl() == null ? "" : di.getUrl().trim();
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("name", stripHtml(di.getTitle()));
                    o.put("url", du);
                    o.put("pic", di.getPic() == null ? "" : di.getPic());
                    arr.put(o);
                } catch (Throwable ignored) {
                }
            }
            final String pu = playUrl;
            final String eps = arr.toString();
            final int sel = selIdx;
            App.post(() -> {
                if (TextUtils.isEmpty(pu)) {
                    ToastUtil.show(HkPageActivity.this, "解析失败，换一条试试",
                            android.widget.Toast.LENGTH_SHORT);
                    return;
                }
                String title = detailItem != null && !TextUtils.isEmpty(detailItem.getTitle())
                        ? detailItem.getTitle() : "";
                String pic = detailItem != null ? detailItem.getPic() : "";
                String lineName = it.getLine() == null || it.getLine().isEmpty() ? "默认" : it.getLine();
                VideoActivity.startHkPlay(HkPageActivity.this,
                        currentRule == null ? "" : currentRule.getTitle(), lineName,
                        pu, it.getTitle(), title, pic, eps, sel);
            });
        }).start();
    }

    /**
     * 内容列表状态下点 @lazyRule= 选集（8.83 dealLazyRule 无条件优先，诊断账
     * juzhi-hk-diag-20261031.md）：详情靠 .rule() 页面函数自绘的 fypage 型规则页面永不进
     * V_DETAIL，选集只能从当前内容列表收集同组条目进播放器。解析与起播零件同
     * playDetailEpisode，数据源从 detailItems（老详情链、已无人写入）换成当前内容列表；
     * 剧名/封面取详情块头部条目，无头部时用被点条目自身。懒函数取流失败会返回
     * toast:// 等动作串（HkRouter.play 会把它当地址塞回），先过 handleActionUrl 弹提示，
     * 不进播放器。
     */
    private void playContentEpisode(HkItem it, String url) {
        // 先快照当前列表：解析在后台线程跑，期间列表可能被刷新替换，迭代活列表会并发改错
        final List<HkItem> snapshot = new ArrayList<>(videos);
        new Thread(() -> {
            String playUrl = url;
            boolean parseFailed = false;
            try {
                HkPlay play = getRouter().play(url, it.getExtra());
                if (!play.getUrl().isEmpty()) playUrl = play.getUrl();
            } catch (Throwable e) {
                parseFailed = true;
            }
            if (parseFailed) playUrl = "";
            // 8.83 getChapters 官形（同 playDetailEpisode，HkChapters 移植，账 §十）。
            int clickedPos = -1;
            for (int i = 0; i < snapshot.size(); i++) {
                if (snapshot.get(i) == it) {
                    clickedPos = i;
                    break;
                }
            }
            HkChapters.Collected chapters = HkChapters.collect(snapshot, clickedPos);
            org.json.JSONArray arr = new org.json.JSONArray();
            int selIdx = Math.max(0, chapters.selectedIndex);
            for (HkItem di : chapters.items) {
                String du = di.getUrl() == null ? "" : di.getUrl().trim();
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("name", stripHtml(di.getTitle()));
                    o.put("url", du);
                    o.put("pic", di.getPic() == null ? "" : di.getPic());
                    arr.put(o);
                } catch (Throwable ignored) {
                }
            }
            // 剧名/封面：详情块头部条目（movie_1_left_pic 等）优先，否则被点条目自身
            String title = it.getTitle() == null ? "" : it.getTitle();
            String pic = it.getPic() == null ? "" : it.getPic();
            for (HkItem di : snapshot) {
                if (isHeaderColType(di.getColType())) {
                    if (!TextUtils.isEmpty(di.getTitle())) title = di.getTitle();
                    if (!TextUtils.isEmpty(di.getPic())) pic = di.getPic();
                    break;
                }
            }
            final String pu = playUrl;
            final String eps = arr.toString();
            final int sel = selIdx;
            final String vTitle = title;
            final String vPic = pic;
            App.post(() -> {
                if (TextUtils.isEmpty(pu)) {
                    ToastUtil.show(HkPageActivity.this, "解析失败，换一条试试",
                            android.widget.Toast.LENGTH_SHORT);
                    return;
                }
                // 懒函数失败返回的 toast:// 等动作串先过动作协议（弹提示），不当播放地址
                if (HkRouter.isActionUrl(pu) && handleActionUrl(pu, it)) return;
                String lineName = it.getLine() == null || it.getLine().isEmpty() ? "默认" : it.getLine();
                VideoActivity.startHkPlay(HkPageActivity.this,
                        currentRule == null ? "" : currentRule.getTitle(), lineName,
                        pu, it.getTitle(), vTitle, vPic, eps, sel);
            });
        }).start();
    }

    // ================= 通用组件 =================

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * 内容网格间距：标准均分公式，保证 N 列总宽严格等于 RecyclerView 宽、不溢出。
     * 每列 i（0-based）：left = i*S/N，right = S-(i+1)*S/N；列间距 S=12dp。
     * 外边距由父容器 16dp 提供，decoration 不再加外边距。
     * 兼容 60-span 内容网格与 3-span 搜索网格。
     */
    private class GridSpace extends RecyclerView.ItemDecoration {
        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int pos = parent.getChildAdapterPosition(view);
            if (pos < 0) return;
            int total = 60, span = 60, spanIndex = 0;
            if (parent.getLayoutManager() instanceof GridLayoutManager) {
                total = ((GridLayoutManager) parent.getLayoutManager()).getSpanCount();
            }
            RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) view.getLayoutParams();
            if (lp instanceof GridLayoutManager.LayoutParams) {
                GridLayoutManager.LayoutParams glp = (GridLayoutManager.LayoutParams) lp;
                span = glp.getSpanSize();
                if (glp.getSpanIndex() >= 0) spanIndex = glp.getSpanIndex();
            }
            if (span >= total) {
                outRect.left = 0;
                outRect.right = 0;
                if (pos > 0) outRect.top = dp(12);
                return;
            }
            int perRow = Math.max(1, total / Math.max(1, span));
            int col = span > 0 ? spanIndex / span : 0;
            if (col < 0) col = 0;
            if (col >= perRow) col = perRow - 1;
            int s = dp(12);
            outRect.left = col * s / perRow;
            outRect.right = s - (col + 1) * s / perRow;
            if (pos >= perRow) outRect.top = dp(12);
        }
    }

    private class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.Holder> {
        private final List<SearchResult> items;
        private final boolean fromContent;
        private final int imgHeight;

        VideoAdapter(List<SearchResult> items, boolean fromContent) {
            this.items = items;
            this.fromContent = fromContent;
            // 8.83 item_movie_tools：封面容器固定 150dp
            imgHeight = dp(150);
        }

        class Holder extends RecyclerView.ViewHolder {
            ImageView cover;
            TextView title, desc;

            Holder(View v) {
                super(v);
                cover = v.findViewById(R.id.iv_cover);
                title = v.findViewById(R.id.tv_title);
                desc = v.findViewById(R.id.tv_desc);
                View container = v.findViewById(R.id.cover_container);
                if (container != null) {
                    ViewGroup.LayoutParams lp = container.getLayoutParams();
                    lp.height = imgHeight;
                    container.setLayoutParams(lp);
                } else {
                    ViewGroup.LayoutParams lp = cover.getLayoutParams();
                    lp.height = imgHeight;
                    cover.setLayoutParams(lp);
                }
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hk_video, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            SearchResult sr = items.get(position);
            h.title.setText(sr.getTitle());
            // 跨小程序搜索结果：在 desc 前缀标注来源小程序
            String srcRule = searchSrcRules.get(sr);
            String desc = sr.getDesc() == null ? "" : sr.getDesc();
            if (!TextUtils.isEmpty(srcRule)) {
                desc = ("".equals(desc) ? "" : desc + "  ") + "【" + srcRule + "】";
            }
            h.desc.setText(desc);
            h.desc.setVisibility(TextUtils.isEmpty(desc) ? View.GONE : View.VISIBLE);
            ImgUtil.load(sr.getTitle(), sr.getImg(), h.cover, hkRuleUrl());
            h.itemView.setOnClickListener(v -> {
                int pos = h.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                SearchResult s = items.get(pos);
                HkItem it = HkItem.fromSearchResult(s);
                // 跨搜索结果：先切到来源小程序，再走正常详情流程
                String itSrc = searchSrcRules.get(s);
                if (!TextUtils.isEmpty(itSrc)
                        && (currentRule == null || !itSrc.equals(currentRule.getTitle()))) {
                    com.fongmi.android.tv.api.hk.HkRule target =
                            com.fongmi.android.tv.api.hk.HkRuleManager.get().getRule(itSrc);
                    if (target != null) {
                        switchRuleForDetail(target);
                    } else {
                        return;
                    }
                }
                if (currentRule == null) return;
                String mu = it.getUrl() == null ? "" : it.getUrl();
                // 动作协议优先：@inputRule= 口令弹窗、toast:// 等；之前缺这行导致口令点了没反应
                if (handleActionUrl(mu.trim(), it)) return;
                // P1：hiker://page/ 子页面走 openPage，不进 V4
                if (mu.trim().startsWith("hiker://page/") && openPage(it, mu)) return;
                openDetail(it, !fromContent, mu.contains("@lazyRule="));
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    /** col_type 小写形式（js 结果里填充，未设置则回退到规则级 col_type）。 */
    private String colTypeOf(HkItem it) {
        String ct = it.getColType() == null ? "" : it.getColType().trim();
        if (ct.isEmpty() && currentRule != null) {
            // 主列表条目无 col_type 时回退到规则级 col_type（非 detail_col_type）
            String ruleCt = currentRule.getColType();
            if (ruleCt != null && !ruleCt.trim().isEmpty()) ct = ruleCt.trim();
        }
        return ct.toLowerCase();
    }

    /** 去 HTML 标签（col_type 标题里常带 <font> 等），逻辑同 buildDynamicTabs。 */
    private String stripHtml(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]*>", "").replace("‘", "").replace("’", "")
                .replace("“", "").replace("”", "").trim()
                // 规则里常把标题包在引号里（"""最新上传"""），去掉首尾引号
                .replaceAll("^\"+|\"+$", "").replaceAll("^'+|'+$", "").trim();
    }

    /**
     * 标题颜色标记（官方 TextViewUtils.setSpanText 逻辑）：
     * ""xxx""（U+201C×2 开 / U+201D×2 合）→ 红色 #FF0000
     * ''xxx''（U+2018×2 开 / U+2019×2 合）→ 橙色 #f0983c
     * split 内联处理，标题中间也能变色；返回带 <font> 标签的 HTML，无标记返回 null。
     */
    private String applyTitleMarkHtml(String raw) {
        if (raw == null) return null;
        String r = applyMark(raw, "““", "””", "#FF0000");
        if (r != null) return r;
        // 兼容 ASCII 三引号 """xxx"""（部分规则用直引号，如口令弹窗标题）
        r = applyMark(raw, "\"\"\"", "\"\"\"", "#FF0000");
        if (r != null) return r;
        return applyMark(raw, "‘‘", "’’", "#f0983c");
    }

    /** 对文本做开/合标记的 split 内联变色，找到完整标记返回 HTML，否则返回 null。 */
    private String applyMark(String text, String open, String close, String color) {
        if (text == null || !text.contains(open)) return null;
        String[] parts = text.split(java.util.regex.Pattern.quote(open), -1);
        if (parts.length < 2) return null;
        StringBuilder sb = new StringBuilder();
        sb.append(parts[0]);
        boolean marked = false;
        for (int i = 1; i < parts.length; i++) {
            String seg = parts[i];
            int idx = seg.indexOf(close);
            if (idx >= 0) {
                marked = true;
                sb.append("<font color=\"").append(color).append("\">")
                        .append(seg, 0, idx)
                        .append("</font>")
                        .append(seg.substring(idx + close.length()));
            } else {
                // 无闭合标记：把 open 还原，避免吞字
                sb.append(open).append(seg);
            }
        }
        return marked ? sb.toString() : null;
    }

    private CharSequence titleSpan(String raw) {
        if (raw == null) raw = "";
        String markedHtml = applyTitleMarkHtml(raw);
        if (markedHtml != null) {
            try {
                return Html.fromHtml(markedHtml, Html.FROM_HTML_MODE_LEGACY);
            } catch (Throwable t) {
                // fallback 到 strip
            }
        }
        return stripHtml(raw);
    }

    /** col_type → viewType：空/movie_3/未知 → 视频卡片（官方 getItemTypeByCode 未知回退 MOVIE_3）。
     * 8.83 原版：每个 col_type 独立 viewType，不合并（对照 ArticleColTypeEnum）。 */
    private int contentTypeOf(HkItem it) {
        String ct = colTypeOf(it);
        switch (ct) {
            case "text_1":
                // 8.83 TextOneColHolder＋item_movie_one_col_nopic（照官
                // 接线，08 销账表第三节）：复刻布局 T_NOPIC 一直在树
                // 里（span/holder/布局全接好）但本映射从没返回过它，
                // text_1 长期走 T_TEXT_1 的另一套数值（14sp/margin 18）。
                return ContentAdapter.T_NOPIC;
            case "text_center_1":
                // 8.83 item_movie_one_col_nopic_center：同上，T_NOPIC_CENTER。
                return ContentAdapter.T_NOPIC_CENTER;
            case "long_text":
                return ContentAdapter.T_LONG_TEXT;
            case "detail_header":
                // 8.83 原版详情页头部（item_movie_one_col）：标题左 + 封面右
                return ContentAdapter.T_DETAIL_HEADER;
            case "header":
                // 8.83 bindForHeaderHolder：规则自带 header 渲染成横向可点 tab（M2）
                return ContentAdapter.T_HEADER_TABS;
            case "footer":
                return ContentAdapter.T_FOOTER;
            case "text_icon":
                return ContentAdapter.T_TEXT_ICON;
            case "rich_text":
                return ContentAdapter.T_RICH;
            case "text_2":
                // 8.83 原版：text_2 → ChannelHolder（item_card_rect 矩形频道条目）
                return ContentAdapter.T_CHANNEL;
            case "text_3":
                return ContentAdapter.T_TEXT_3;
            case "text_4":
                return ContentAdapter.T_TEXT_4;
            case "text_5":
                return ContentAdapter.T_TEXT_5;
            case "avatar":
                return ContentAdapter.T_AVATAR;
            case "input":
                return ContentAdapter.T_INPUT;
            case "line":
            case "line_blank":
                // 8.83 原版：line/line_blank → LineHolder（分隔线/空白块）
                return ContentAdapter.T_LINE;
            case "x5_webview_single":
                return ContentAdapter.T_WEB;
            case "video":
                // 8.83 onCreateViewHolder：video → 行内嵌 WebView（X5WebViewHolder）；
                // 星辰无 X5，系统 WebView 全承载（HkWebViewHolder）经 T_WEB/bindWeb 渲染
                return ContentAdapter.T_WEB;
            case "scroll_button":
                // 8.83 原版：scroll_button → ScrollButtonHolder（横向滚动按钮组）
                return ContentAdapter.T_SCROLL_BUTTON;
            case "flex_button":
                // 8.83 原版：flex_button → FlexButtonHolder（流式按钮组）
                return ContentAdapter.T_FLEX_BUTTON;
            case "card_pic_1":
                return ContentAdapter.T_CARD_PIC_1;
            case "card_pic_2":
                return ContentAdapter.T_CARD_PIC_2;
            case "card_pic_2_2":
                return ContentAdapter.T_CARD_PIC_2_2;
            case "card_pic_2_2_left":
                return ContentAdapter.T_CARD_PIC_2_2_LEFT;
            case "card_pic_3":
                return ContentAdapter.T_CARD_PIC_3;
            case "card_pic_3_center":
                return ContentAdapter.T_CARD_PIC_3_CENTER;
            case "":
            case "movie_3":
                // 8.83 原版：movie_3 → ToolsHolder（item_movie_tools 影片工具卡片）
                return ContentAdapter.T_TOOLS;
            case "movie_1":
                // 8.83 item_movie_one_col：文字左 + 封面右（130×90dp），T_MOVIE 专用
                return ContentAdapter.T_MOVIE;
            case "movie_1_left_pic":
                // 8.83 item_movie_one_col_left_pic：封面左 + 文字右
                return ContentAdapter.T_MOVIE_LEFT_PIC;
            case "movie_1_vertical_pic":
                // 官方 movie_1_vertical_pic span=60 全宽左竖海报右文（D3）
                return ContentAdapter.T_MOVIE_VERTICAL;
            case "movie_1_vertical_pic_blur":
                // 官方 movie_1_vertical_pic_blur span=60 毛玻璃背景 + 竖海报（D3）
                return ContentAdapter.T_MOVIE_VERTICAL_BLUR;
            case "movie_3_marquee":
                // 8.83 原版：movie_3_marquee → ToolsHolder（item_movie_tools_marquee，标题跑马灯）
                return ContentAdapter.T_TOOLS;
            case "movie_2":
                // 官方 movie_2 span=30 两列（走 T_VIDEO，spanFor 按 col_type 给 30）
                return ContentAdapter.T_VIDEO;
            default:
                if (ct.startsWith("icon")) {
                    // 8.83 各 icon_* 子类型走独立布局（smali 实测 itemType→layout 映射），
                    // 之前全挤在一个通用布局里是"图标错位"的根因。
                    if ("icon_3_fill".equals(ct) || "icon_3_round_fill".equals(ct)) return ContentAdapter.T_ICON_FILL;
                    if ("icon_2_round".equals(ct)) return ContentAdapter.T_ICON_2_ROUND;
                    if ("icon_2".equals(ct)) return ContentAdapter.T_ICON_2;
                    if ("icon_4_card".equals(ct)) return ContentAdapter.T_ICON_4_CARD;
                    if ("icon_small_3".equals(ct)) return ContentAdapter.T_ICON_3_SMALL;
                    if ("icon_1_search".equals(ct)) return ContentAdapter.T_ICON_1_SEARCH;
                    if ("icon_1_left_pic".equals(ct)) return ContentAdapter.T_ICON_1_LEFT_PIC;
                    if ("icon_round_4".equals(ct)) return ContentAdapter.T_ICON_ROUND;
                    if ("icon_round_small_4".equals(ct)) return ContentAdapter.T_ICON_ROUND_SMALL;
                    if ("icon_small_4".equals(ct))
                        return ContentAdapter.T_ICON_4_SMALL;
                    // 8.83 原版：icon_5/icon_5_no_crop → IconFiveHolder（五列图标）
                    if ("icon_5".equals(ct) || "icon_5_no_crop".equals(ct))
                        return ContentAdapter.T_ICON_FIVE;
                    return ContentAdapter.T_ICON; // icon_4
                }
                // 8.83 原版：pic_* 各子类型独立 viewType
                if ("pic_1".equals(ct)) return ContentAdapter.T_PIC_1;
                if ("pic_1_card".equals(ct)) return ContentAdapter.T_PIC_1_CARD;
                if ("pic_1_center".equals(ct)) return ContentAdapter.T_PIC_1_CENTER;
                if ("pic_1_full".equals(ct)) return ContentAdapter.T_PIC_1_FULL;
                if ("pic_2".equals(ct)) return ContentAdapter.T_PIC_2;
                if ("pic_2_card".equals(ct)) return ContentAdapter.T_PIC_2_CARD;
                if ("pic_3".equals(ct)) return ContentAdapter.T_PIC_3;
                if ("pic_3_square".equals(ct)) return ContentAdapter.T_PIC_3_SQUARE;
                if (ct.startsWith("pic")) return ContentAdapter.T_PIC_1; // 未知 pic 回退
                // movie_1/movie_2 等按视频卡片网格渲染（全宽横向图文待后版）
                if (ct.startsWith("movie")) return ContentAdapter.T_VIDEO;
                return ContentAdapter.T_VIDEO;
        }
    }

    /** 选集 chip 唯一键：归一化后的 url。 */
    private static String epKey(HkItem it) {
        if (it == null || it.getUrl() == null) return "";
        return it.getUrl().trim();
    }

    /** 该 chip 是否为当前选中（正在播）的那一集。 */
    private boolean isEpSelected(HkItem it) {
        String k = epKey(it);
        return !k.isEmpty() && k.equals(lastEpKey);
    }

    /**
     * V2 非视频条目的统一点击：无 url 的纯展示行无反应；有 url 先过动作协议，
     * 未消费则走 openDetail。条目自带 {@code @lazyRule=} 时用 lazy 预检模式，
     * 直接播放就不推 V4（粉嫩小BB点封面即播，不闪 V4 loading）。
     */
    private void onContentItemClick(HkItem it) {
        if (it == null || currentRule == null) return;
        String url = it.getUrl() == null ? "" : it.getUrl().trim();
        // 诊断：点击落点（只记协议头，不记正文）
        String scheme = url;
        int pi = url.indexOf("://");
        if (pi > 0) scheme = url.substring(0, pi + 3);
        else if (scheme.length() > 24) scheme = scheme.substring(0, 24);
        com.github.catvod.crawler.SpiderDebug.log("hk", "itemClick title=%s scheme=%s len=%d",
                it.getTitle(), scheme, url.length());
        // 8.83 ArticleListFragment.clickItem(src 2917)：空链接 toast"链接为空，规则有误！"，不静默吞掉
        if (url.isEmpty()) {
            ToastUtil.show(this, "链接为空，规则有误！", android.widget.Toast.LENGTH_SHORT);
            return;
        }
        // 8.83 clickItem(src 2940)：base64:// 去前缀解码后再走分流
        if (url.startsWith("base64://")) {
            try {
                String decoded = new String(android.util.Base64.decode(
                        url.substring("base64://".length()).trim(), 2)).trim();
                url = decoded;
                it.setUrl(decoded);
            } catch (Throwable e) {
                ToastUtil.show(this, "链接解码失败，规则有误！", android.widget.Toast.LENGTH_SHORT);
                return;
            }
        }
        if (handleActionUrl(url, it)) return;
        // 8.83 clickItem(2948)：fileSelect://<js> 打开文件选择器，选完后把文件名注入 _fileSelect_<name> 并执行 JS
        if (url.startsWith("fileSelect://")) {
            fileSelectJs = url.substring("fileSelect://".length());
            fileSelectPosition = videos.indexOf(it);
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            try {
                startActivityForResult(android.content.Intent.createChooser(intent, "选择文件"), REQ_FILE_SELECT);
            } catch (Throwable t) {
                ToastUtil.show(this, "无法打开文件选择器", android.widget.Toast.LENGTH_SHORT);
            }
            return;
        }
        // P1：hiker://page/ 子页面走 openPage（官方 toNextPage），不进 V4
        if (url.startsWith("hiker://page/") && openPage(it, url)) return;
        // 官方 clickItem 顺序：@rule= 前段里的 @lazyRule= 优先（dealLazyRule），再是 @rule=（dealRule 子列表）
        int ar = url.indexOf("@rule=");
        String preRule = ar >= 0 ? url.substring(0, ar) : url;
        boolean hasLazy = preRule.contains("@lazyRule=");
        // 官方 clickItem：@rule= 条目进子列表页（dealRule 新建 ArticleListRule 打开 FilmListActivity），不进 V4
        if (ar >= 0 && !hasLazy && openSubList(it, url)) return;
        // 8.83 clickItem：@lazyRule= 无条件优先走 dealLazyRule，不看页面状态（官方 :1369）。
        // fypage 自绘详情（detail_find_rule 为空、详情摊在内容列表里）的页面永不进 V_DETAIL，
        // 旧分发让这类选集穿过全部分支落进 openDetail 空壳、点集不起播（橘汁实案，
        // 诊断账 juzhi-hk-diag-20261031.md）。本支只接管内容列表状态：解析+同组选集走
        // playContentEpisode；V_DETAIL 内仍走原 playDetailEpisode，老详情链一字不动。
        // 动作协议（handleActionUrl）在本支之前已处理，优先级不变。
        if (hasLazy && (stack.isEmpty() || stack.peek() != V_DETAIL)) {
            lastEpKey = epKey(it);
            if (contentAdapter != null) contentAdapter.notifyDataSetChanged();
            playContentEpisode(it, url);
            return;
        }
        // 8.83 原版 dealWithUrl：详情页内可播 URL 直接播放，不套娃进详情
        if (!stack.isEmpty() && stack.peek() == V_DETAIL) {
            // 8.83 H2：@lazyRule= 优先走 lazy 解析（dealLazyRule），绝不进 webview；
            // dealKind 会把含 @lazyRule= 的 http URL 误判为 "web"。
            if (hasLazy) {
                lastEpKey = epKey(it);
                if (contentAdapter != null) contentAdapter.notifyDataSetChanged();
                playDetailEpisode(it, url);
                return;
            }
            String kind = HkRouter.dealKind(url);
            if ("video".equals(kind)) {
                lastEpKey = epKey(it);
                if (contentAdapter != null) contentAdapter.notifyDataSetChanged();
                playDetailEpisode(it, url);
                return;
            }
            if (!kind.isEmpty()) {
                handleDealUrl(kind, url, it);
                return;
            }
        }
        // M9: 8.83 clickItem 3040-3043 - 仅当规则配置了 detail_find_rule 且 URL 非 video:// 开头，
        // 才进详情页；无 detail_find_rule 的规则，点可播 URL 直接进播放器，不进详情页（省一次点击）。
        // @lazyRule= 走 openDetail 的 lazyPrecheck（后台求值后直达播放，不推 V4），保持原样。
        if (!hasLazy) {
            String dfr = currentRule.getDetailFindRule();
            boolean hasDetailRule = dfr != null && !dfr.trim().isEmpty();
            if (!hasDetailRule || url.startsWith("video://")) {
                if (HkRouter.isVideoOrMusicUrl(url)) {
                    playContentDirect(it, url);
                    return;
                }
                String dk = HkRouter.dealKind(url);
                if (!dk.isEmpty()) {
                    if ("video".equals(dk)) {
                        playContentDirect(it, url);
                    } else {
                        handleDealUrl(dk, url, it);
                    }
                    return;
                }
            }
        }
        openDetail(it, true, hasLazy);
    }

    /**
     * M9: 内容页直接播放（无 detail_find_rule 的规则，可播 URL 不进 V4 详情页）。
     * 对齐 8.83 PlayerChooser.startPlayer：解析播放地址后直达播放器，选集列表仅含当前条目。
     */
    private void playContentDirect(HkItem it, String url) {
        new Thread(() -> {
            String playUrl = url;
            boolean parseFailed = false;
            try {
                if (url.contains("@lazyRule=") || url.contains("@rule=") || url.startsWith("js:")) {
                    HkPlay play = getRouter().play(url, it.getExtra());
                    if (!play.getUrl().isEmpty()) playUrl = play.getUrl();
                }
            } catch (Throwable e) {
                parseFailed = true;
            }
            if (parseFailed) playUrl = "";
            final String pu = playUrl;
            App.post(() -> {
                if (TextUtils.isEmpty(pu)) {
                    ToastUtil.show(HkPageActivity.this, "解析失败，换一条试试",
                            android.widget.Toast.LENGTH_SHORT);
                    return;
                }
                try {
                    org.json.JSONArray arr = new org.json.JSONArray();
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("name", stripHtml(it.getTitle()));
                    o.put("url", url);
                    o.put("pic", it.getPic() == null ? "" : it.getPic());
                    arr.put(o);
                    String lineName = it.getLine() == null || it.getLine().isEmpty() ? "默认" : it.getLine();
                    VideoActivity.startHkPlay(HkPageActivity.this,
                            currentRule == null ? "" : currentRule.getTitle(), lineName,
                            pu, it.getTitle(), it.getTitle(),
                            it.getPic() == null ? "" : it.getPic(),
                            arr.toString(), 0);
                } catch (Throwable e) {
                    ToastUtil.show(HkPageActivity.this, "无法播放此链接",
                            android.widget.Toast.LENGTH_SHORT);
                }
            });
        }).start();
    }

    /**
     * 列表点磁力/电驴进自家播放器（用户拍板，相对原版有意分叉）：
     * 把被点的这一条目当作单集传给播放器，走海阔播放链（SiteApi.hkPlayerContent →
     * HkRouter.play 透传 → 迅雷引擎），与详情选集磁力同一条路；标题用条目自带名字。
     */
    private void playP2pInApp(HkItem it, String url) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("name", stripHtml(it.getTitle()));
            o.put("url", url);
            o.put("pic", it.getPic() == null ? "" : it.getPic());
            arr.put(o);
            String lineName = it.getLine() == null || it.getLine().isEmpty() ? "默认" : it.getLine();
            VideoActivity.startHkPlay(HkPageActivity.this,
                    currentRule == null ? "" : currentRule.getTitle(), lineName,
                    url, it.getTitle(), it.getTitle(),
                    it.getPic() == null ? "" : it.getPic(),
                    arr.toString(), 0);
        } catch (Throwable e) {
            ToastUtil.show(HkPageActivity.this, "无法播放此链接", android.widget.Toast.LENGTH_SHORT);
        }
    }

    /** 子列表状态（@rule= 进子列表时压栈，返回时恢复）。 */
    private static class SubListState {
        HkRule rule;
        String title;
        int page;
        String cls, area, year, sort;
        String clsName, areaName, yearName, sortName;
        java.util.List<HkItem> videos;
        int scrollPos;
        int fromView;
    }

    private final java.util.Deque<SubListState> subStack = new java.util.ArrayDeque<>();

    /**
     * 官方 dealRule 语义：@rule= 条目打开子列表页。
     * 解析规则：r = @rule= 后全部；nextRule = r 按 "==>" 切第一段；6 段且非 js: 时第 6 段为 col_type；
     * 新规则 url=@rule= 前的 pageUrl，find_rule=nextRule(+==>后缀)，col_type=条目指定或父规则默认；
     * 其余（ua/group/preRule/title/pages/last_chapter_rule）继承父规则。
     */
    private boolean openSubList(HkItem it, String url) {
        try {
            int ar = url.indexOf("@rule=");
            String pageUrl = url.substring(0, ar).trim();
            // 官方 StringUtil.arrayToString(urlRule, 1, "@rule=")：拼接所有后续段
            String[] parts = url.split("@rule=");
            StringBuilder rb = new StringBuilder();
            for (int i = 1; i < parts.length; i++) {
                if (i > 1) rb.append("@rule=");
                rb.append(parts[i]);
            }
            String r = rb.toString().trim();
            if (r.isEmpty()) return false;
            String[] arrow = r.split("==>");
            String nextRule = arrow[0];
            StringBuilder s1 = new StringBuilder();
            for (int i = 1; i < arrow.length; i++) s1.append("==>").append(arrow[i]);
            String[] rules = nextRule.split(";");
            String colType = currentRule.getColType();
            if (rules.length == 6 && !r.startsWith("js:")) {
                colType = rules[5].trim();
                StringBuilder nb = new StringBuilder();
                for (int i = 0; i < 5; i++) {
                    if (i > 0) nb.append(";");
                    nb.append(rules[i]);
                }
                nextRule = nb.toString();
            }
            String findRule = nextRule + s1.toString();
            if (findRule.trim().isEmpty()) return false;
            // goTo 预检：@rule=js: 可能是导航动作（如 xhotview 点封面调 goTo('hiker://page/detail...') 跳 V4），
            // 不是子列表。先在后台求值，若规则调了 goTo，按目标 URL 导航，不建子列表。
            String frTrim = findRule.trim();
            if (frTrim.startsWith("js:")) {
                final String jsCode = frTrim;
                final HkItem item = it;
                final String fPageUrl = pageUrl;
                final String fFindRule = findRule;
                final String fColType = colType;
                new Thread(() -> {
                    String target = "";
                    boolean needRefresh = false;
                    boolean toTop = false;
                    int before = 0;
                    List<HkItem> after = null;
                    try {
                        HkJsRuntime rt = getRouter().getEngine().getJsRuntime();
                        before = rt.actionResultCount();
                        // 探针专用求值：不 drain 共用 results 表——evalLazy 的 drain
                        // 会把 JS 刚交出的条目连同判定依据一起清掉（花都死路）。
                        rt.evalActionProbe(jsCode, fPageUrl);
                        target = rt.consumeGoToRequest();
                        // 动作回调（如同意按钮 putMyVar+refreshPage）：无 goTo 但请求了刷新，
                        // 必须取走 refreshPage 标记直接重刷当前列表，不进子列表流程
                        //（否则 flag 被丢弃、router 被销毁、myVars 丢失，表现为"点了没反应"）。
                        needRefresh = rt.consumeRefreshRequest();
                        toTop = rt.isRefreshToTop();
                        after = rt.snapshotActionResults();
                    } catch (Throwable ignored) {
                    }
                    final String t = target == null ? "" : target.trim();
                    final boolean refresh = needRefresh;
                    final boolean scrollTop = toTop;
                    final List<HkItem> act = after;
                    final int n0 = before;
                    App.post(() -> {
                        if (!t.isEmpty()) {
                            handleGoToUrl(t, item);
                        } else if (refresh) {
                            // 旧内容页已删：刷新请求无处消费
                        } else if (act != null && act.size() > n0) {
                            // 规则自绘"加载更多"按钮：JS 用 addItemBefore 就地追加了条目，
                            // 8.83 同款就地更新当前列表，不进详情/子列表。
                            mergeActionItems(item, act);
                        } else {
                            // 无 goTo、无刷新、无产出：照 8.83 dealRule 原形继续子列表
                            // 流程（派生子规则开子列表，加载时由解析器重跑这段 JS 产出
                            // 条目）。旧路在此退化 openDetail——那是只剩状态赋值的空壳
                            // 落点，详情型 @rule=js 条目（detail() 返回空、条目靠
                            // setResult 交出，又被旧探针的 drain 清掉）必落空壳（花都案）。
                            enterSubList(item, fPageUrl, fFindRule, fColType);
                        }
                    });
                }).start();
                return true;
            }
            return enterSubList(it, pageUrl, findRule, colType);
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * 子列表进入本体（官方 dealRule 的咱家载体形）：压栈保存父列表状态、派生子规则、
     * 换规则并重建分类行。非 js 的 @rule= 在 openSubList 里直达此处；@rule=js: 探针
     * 无 goTo/无刷新/无产出时也回到此处（见探针 else 支），不许再退化成空壳详情。
     * 只许在 UI 线程调用。
     */
    private boolean enterSubList(HkItem it, String pageUrl, String findRule, String colType) {
        try {
            // 压栈保存父列表状态
            SubListState st = new SubListState();
            st.rule = currentRule;
            st.title = contentTitle;
            st.page = page;
            st.cls = cls;
            st.area = area;
            st.year = year;
            st.sort = sort;
            st.clsName = clsName;
            st.areaName = areaName;
            st.yearName = yearName;
            st.sortName = sortName;
            st.videos = new java.util.ArrayList<>(videos);
            st.scrollPos = contentGrid == null ? 0 : contentGrid.findFirstVisibleItemPosition();
            st.fromView = stack.isEmpty() ? V_CONTENT : stack.peek();
            subStack.push(st);
            // 派生子规则并加载（官方 dealRule：新规则不带分类导航字段）
            HkRule sub = currentRule.deriveSubRule(pageUrl, findRule, colType);
            sub.clearNav();
            destroyRouterAsync();
            currentRule = sub;
            cls = "";
            area = "";
            year = "";
            sort = "";
            clsName = "";
            areaName = "";
            yearName = "";
            sortName = "";
            page = 1;
            noMore = false;
            loading = false;
            contentGen++;
            videos.clear();
            String itemTitle = stripHtml(it.getTitle());
            contentTitle = itemTitle.isEmpty() ? st.title : itemTitle;
            buildCategoryRow();
            buildFilterRows();
            // 已在列表页时直接替换内容，不重复压栈；从搜索等其他视图进入时才压栈
            if (st.fromView != V_CONTENT) pushView(V_CONTENT);
            else showView(V_CONTENT);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * 规则自绘"加载更多"按钮的就地更新（8.83 addItemBefore/deleteItemByCls 同款语义）：
     * 把 JS 追加的新条目（按 url+title 去重）插到被点按钮之前，按钮保留以便继续翻页。
     */
    private void mergeActionItems(HkItem button, List<HkItem> after) {
        if (after == null || after.isEmpty() || contentAdapter == null) return;
        // 详情块重渲（结果里带详情头部条目＝detailFn 整块重画，如橘汁切线路）：
        // 先摘掉上一块详情条目、新块插回旧块原位，防越点越叠、整表反复重刷。
        // 只认带头部条目的整块重渲；普通「加载更多」追加（无头部条目）不走此路，口径不变。
        boolean detailRender = false;
        for (HkItem a : after) {
            if (a != null && isHeaderColType(a.getColType())) { detailRender = true; break; }
        }
        int blockAt = -1;
        List<HkItem> removedBlock = null;
        if (detailRender) {
            if (!detailBlockItems.isEmpty()) {
                for (int i = 0; i < videos.size(); i++) {
                    if (detailBlockItems.contains(videos.get(i))) { blockAt = i; break; }
                }
                if (blockAt >= 0) {
                    removedBlock = new ArrayList<>(detailBlockItems);
                    videos.removeAll(detailBlockItems);
                }
            }
            detailBlockItems.clear();
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (HkItem v : videos) seen.add(itemKey(v));
        List<HkItem> delta = new ArrayList<>();
        for (HkItem it : after) {
            if (seen.add(itemKey(it))) delta.add(it);
        }
        if (delta.isEmpty()) {
            // 摘完旧块却没有新条目可落：原样还回旧块，不让详情凭空消失
            if (removedBlock != null) {
                videos.addAll(Math.min(blockAt, videos.size()), removedBlock);
                detailBlockItems.addAll(removedBlock);
            }
            return;
        }
        int at = -1;
        if (button != null) {
            String bk = itemKey(button);
            for (int i = 0; i < videos.size(); i++) {
                if (bk.equals(itemKey(videos.get(i)))) { at = i; break; }
            }
        }
        // 被点的是旧块内的线路 tab（已随旧块摘除）：新块插回旧块原位
        if (at < 0 && blockAt >= 0) at = Math.min(blockAt, videos.size());
        if (at < 0) videos.addAll(delta);
        else videos.addAll(at, delta);
        if (detailRender) detailBlockItems.addAll(delta);
        contentAdapter.refreshGroups();
        contentAdapter.notifyDataSetChanged();
        updateFooter();
    }

    private static String itemKey(HkItem it) {
        if (it == null) return "";
        String u = it.getUrl() == null ? "" : it.getUrl();
        String t = it.getTitle() == null ? "" : it.getTitle();
        return u + "‖" + t;
    }

    /** 子列表返回：恢复父列表状态；若从非列表视图进入则回到原视图。 */
    /**
     * 处理 goTo(url) 导航请求（规则在 @rule=js: 回调里调 goTo 跳转）。
     * hiker://page/detail?rule=X&u=BASE64 → 解析出详情 URL，打开 V4 详情页；
     * 其他 URL 按普通条目点击处理。
     */
    private void handleGoToUrl(String url, HkItem srcItem) {
        if (url == null) return;
        String u = url.trim();
        if (u.isEmpty()) return;
        try {
            if (u.startsWith("hiker://page/detail")) {
                // 解析 u= 参数（base64 编码的详情页 URL）
                String detailUrl = "";
                int qi = u.indexOf('?');
                if (qi >= 0) {
                    String query = u.substring(qi + 1);
                    for (String kv : query.split("&")) {
                        int eq = kv.indexOf('=');
                        if (eq > 0 && "u".equals(kv.substring(0, eq).trim())) {
                            String b64 = kv.substring(eq + 1).trim();
                            try {
                                // URL 安全 base64 → 标准 base64
                                b64 = b64.replace('-', '+').replace('_', '/');
                                while (b64.length() % 4 != 0) b64 += "=";
                                detailUrl = new String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), "UTF-8").trim();
                            } catch (Throwable ignored) {
                            }
                            break;
                        }
                    }
                }
                if (!detailUrl.isEmpty()) {
                    // 用详情 URL 构造条目，走 V4 详情页
                    HkItem di = new HkItem();
                    di.setTitle(srcItem != null ? srcItem.getTitle() : "");
                    di.setPic(srcItem != null ? srcItem.getPic() : "");
                    di.setUrl(detailUrl);
                    openDetail(di, false);
                } else if (srcItem != null) {
                    // 解析失败时退化为直接打开源条目详情
                    openDetail(srcItem, true);
                }
                return;
            }
            // hiker://page/ 其他子页面
            if (u.startsWith("hiker://page/") && srcItem != null && openPage(srcItem, u)) return;
            // 其他：按 URL 分流
            if (srcItem != null) {
                HkItem di = new HkItem();
                di.setTitle(srcItem.getTitle());
                di.setPic(srcItem.getPic());
                di.setUrl(u);
                if (!handleActionUrl(u, di)) openDetail(di, false);
            }
        } catch (Throwable e) {
            android.util.Log.d("HkPage", "handleGoToUrl failed: " + e.getMessage());
        }
    }

    private boolean backSubList() {
        if (subStack.isEmpty()) return false;
        SubListState st = subStack.pop();
        destroyRouterAsync();
        currentRule = st.rule;
        page = st.page;
        cls = st.cls;
        area = st.area;
        year = st.year;
        sort = st.sort;
        clsName = st.clsName == null ? "" : st.clsName;
        areaName = st.areaName == null ? "" : st.areaName;
        yearName = st.yearName == null ? "" : st.yearName;
        sortName = st.sortName == null ? "" : st.sortName;
        noMore = false;
        loading = false;
        contentGen++;
        videos.clear();
        videos.addAll(st.videos);
        contentTitle = st.title;
        if (st.fromView != V_CONTENT && stack.size() > 1) {
            stack.pop();
            showView(stack.peek());
        }
        return true;
    }

    /**
     * P1：hiker://page/ 子页面（官方 PageParser.getNextPage/toNextPage 语义）。
     * 条目 URL 如 hiker://page/erji1?page=fypage#noHistory# 时，不进 V4 详情，
     * 而是按 path 在规则 pages 里找对应页面，用其 rule/col_type 开新列表页，
     * 条目 extra 注入为新页面的 MY_PARAMS。返回 true 表示已消费。
     */
    private boolean openPage(HkItem it, String url) {
        try {
            if (it == null || currentRule == null || TextUtils.isEmpty(url)) return false;
            String u = url.trim();
            if (!u.startsWith("hiker://page/")) return false;
            // 子规则拼装照官走 HkPageParser.getNextPage 一口（D7 收口，
            // 08 销账表）：url 取序（query url＞extra url＞原串）、extra
            // 的 rule 键换规则、col_type "*" 回退、params 不继承（extra
            // 是 {...} 才写）全在里面——旧形在本方法里自拼一套：url 只
            // 用原串、rule 键不认、"*" 不回退、params 靠 deriveSubRule
            // 继承父规则，与官形四处不符（一个APP 类 V4 子页重灾区）。
            // 拼装失败（找不到页/规则）抛 ParseException，外层 catch
            // 回 false，与旧形 pageObj==null 的落点一致。
            HkRule sub = com.fongmi.android.tv.api.hk.HkPageParser
                    .getNextPage(currentRule, u, it.getExtra());
            if (sub == null) return false;
            // 官 toNextPage λ16：DetailPage.pages 非空时覆写组出规则的 pages
            // （条目 pages 位即 DetailPage.pages 对位，HkItem:48；桥 open 路与
            // 列表条目同口，C1 复核订正）。
            if (!TextUtils.isEmpty(it.getPages())) sub.setPages(it.getPages());
            // 页名只作标题显示用：在当前规则 pages 里按 path 找 name
            // （拼装本体已由 getNextPage 判存亡，此处找不到名就走
            // 条目标题/父标题回退链）。
            String pageTitle = "";
            try {
                String path = u.substring("hiker://page/".length());
                int q = path.indexOf('?');
                if (q >= 0) path = path.substring(0, q);
                int hh = path.indexOf('#');
                if (hh >= 0) path = path.substring(0, hh);
                String pagesJson = currentRule.getPages();
                if (!TextUtils.isEmpty(pagesJson)) {
                    org.json.JSONArray pages = new org.json.JSONArray(pagesJson);
                    for (int i = 0; i < pages.length(); i++) {
                        org.json.JSONObject p = pages.optJSONObject(i);
                        if (p != null && path.trim().equals(p.optString("path"))) {
                            pageTitle = p.optString("name");
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            // 压栈保存父列表状态（复用子列表机制，返回时恢复）
            SubListState st = new SubListState();
            st.rule = currentRule;
            st.title = contentTitle;
            st.page = page;
            st.cls = cls;
            st.area = area;
            st.year = year;
            st.sort = sort;
            st.clsName = clsName;
            st.areaName = areaName;
            st.yearName = yearName;
            st.sortName = sortName;
            st.videos = new java.util.ArrayList<>(videos);
            st.scrollPos = contentGrid == null ? 0 : contentGrid.findFirstVisibleItemPosition();
            st.fromView = stack.isEmpty() ? V_CONTENT : stack.peek();
            subStack.push(st);
            // 子规则已由 getNextPage 拼装（见上）：find_rule、url、
            // col_type、params 全按官形落定，params 只在 extra 为
            // {...} 时写（D6），不继承父规则。
            sub.clearNav();
            destroyRouterAsync();
            currentRule = sub;
            cls = "";
            area = "";
            year = "";
            sort = "";
            clsName = "";
            areaName = "";
            yearName = "";
            sortName = "";
            page = 1;
            noMore = false;
            loading = false;
            contentGen++;
            videos.clear();
            String itemTitle = stripHtml(it.getTitle());
            contentTitle = !pageTitle.isEmpty() ? pageTitle : (itemTitle.isEmpty() ? st.title : itemTitle);
            buildCategoryRow();
            buildFilterRows();
            if (st.fromView != V_CONTENT) pushView(V_CONTENT);
            else showView(V_CONTENT);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * P2：条目 URL 动作协议的基本处理。返回 true 表示已消费，不再走 openDetail。
     * <ul>
     *   <li>{@code toast://文本} → Toast 显示</li>
     *   <li>{@code copy://文本} → 复制到剪贴板</li>
     *   <li>{@code input://...} → 输入框（解析规则的 hint/js，提交后求值，见 handleListInput）</li>
     *   <li>{@code select://...} → 单选框（解析规则的 options/js，选中后求值，见 handleListSelect）</li>
     *   <li>{@code confirm://...} → 对应对话框（基本处理）</li>
     *   <li>{@code msg@confirmRule=js:...} → $().confirm 序列化：弹确认框，确认后求值 JS</li>
     *   <li>{@code @inputRule=.js:...} → $().input 序列化：弹输入框，输入后求值 JS（input 为输入值）</li>
     *   <li>{@code @x5Rule=js:...} → $().x5Rule 序列化：求值取 URL，用 WebView 打开</li>
     * </ul>
     */
    private boolean handleActionUrl(String url, HkItem it) {
        if (isFinishing() || isDestroyed()) return true;
        try {
            if (url.startsWith("toast://")) {
                ToastUtil.show(this, url.substring(8), android.widget.Toast.LENGTH_SHORT);
                return true;
            }
            if (url.startsWith("copy://")) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("hk", url.substring(7)));
                ToastUtil.show(this, "已复制", android.widget.Toast.LENGTH_SHORT);
                return true;
            }
            if (url.startsWith("download://")) {
                try {
                    String realUrl = url.substring(11).trim();
                    String title = it != null && !TextUtils.isEmpty(it.getTitle()) ? stripHtml(it.getTitle()) : "下载";
                    java.util.Map<String, String> headers = new java.util.HashMap<>();
                    headers.put("User-Agent", com.fongmi.android.tv.player.PlayerHelper.getUa());
                    com.fongmi.android.tv.utils.download.DownloadTask task =
                            com.fongmi.android.tv.utils.download.DownloadManager.get().add(realUrl, title, headers);
                    if (task != null) ToastUtil.show(this, "开始下载", android.widget.Toast.LENGTH_SHORT);
                } catch (Throwable t) {
                    ToastUtil.show(this, "下载失败", android.widget.Toast.LENGTH_SHORT);
                }
                return true;
            }
            if (url.startsWith("confirm://")) {
                String msg = url.substring(10);
                int q = msg.indexOf('?');
                if (q >= 0) msg = msg.substring(0, q);
                new HkDialogs(this)
                        .setMessage(msg.isEmpty() ? "确认？" : msg)
                        .setPositiveButton("确定", (d, w) -> d.dismiss())
                        .setNegativeButton("取消", (d, w) -> d.dismiss())
                        .show();
                return true;
            }
            if (url.startsWith("input://")) {
                // 原版 DetailUIHelper.dealInput 同语义：解析 JSON/传统两种形态，弹框用规则的 hint，
                // 提交后求值规则 js（input=输入值），走既有动作分发（toast://、refresh 等）
                handleListInput(url);
                return true;
            }
            if (url.startsWith("select://")) {
                // 原版 DetailUIHelper.dealSelect 同语义（简化为单选对话框）：解析选项，
                // 选中后求值规则 js（input=选中项标题，MY_INDEX=序号），走既有动作分发
                handleListSelect(url);
                return true;
            }
            int ci = url.indexOf("@confirmRule=");
            if (ci >= 0) {
                String msg = url.substring(0, ci);
                String js = url.substring(ci + 13).trim();
                if (js.startsWith("js:")) js = js.substring(3);
                final String code = js;
                new HkDialogs(this)
                        .setMessage(msg.isEmpty() ? "确认？" : msg)
                        .setPositiveButton("确定", (d, w) -> {
                            d.dismiss();
                            evalActionJs(code, "");
                        })
                        .setNegativeButton("取消", (d, w) -> d.dismiss())
                        .show();
                return true;
            }
            int ii = url.indexOf("@inputRule=");
            if (ii >= 0) {
                String js = url.substring(ii + 11).trim();
                if (js.startsWith(".js:")) js = js.substring(4);
                else if (js.startsWith("js:")) js = js.substring(3);
                final String code = js;
                showSimpleInputDialog(it.getTitle(), "", input -> evalActionJs(code, input));
                return true;
            }
            int xi = url.indexOf("@x5Rule=");
            if (xi >= 0) {
                String js = url.substring(xi + 8).trim();
                if (js.startsWith("js:")) js = js.substring(3);
                final String code = js;
                new Thread(() -> {
                    String target = "";
                    String evalErr = null;
                    try {
                        target = getRouter().getEngine().getJsRuntime().evalLazy(code, "");
                    } catch (Throwable e) {
                        evalErr = e.getMessage() == null || e.getMessage().isEmpty()
                                ? "执行失败" : e.getMessage();
                    }
                    final String t = target == null ? "" : target.trim();
                    final String fe = evalErr;
                    App.post(() -> {
                        if (fe != null) {
                            ToastUtil.show(this, "执行失败：" + fe, android.widget.Toast.LENGTH_SHORT);
                        } else if (t.startsWith("http")) showWebViewDialog(t);
                        else if (!t.isEmpty())
                            ToastUtil.show(this, t, android.widget.Toast.LENGTH_SHORT);
                    });
                }).start();
                return true;
            }
        } catch (Throwable e) {
            android.util.Log.d("HkPage", "handleActionUrl failed: " + e.getMessage());
        }
        return false;
    }

    /** 在 JS 线程求值动作 JS（@confirmRule/@inputRule 回调），input 为输入值。 */
    private void evalActionJs(String code, String input) {
        new Thread(() -> {
            String errMsg = null;
            String result = "";
            boolean needRefresh = false;
            boolean toTop = false;
            try {
                HkJsRuntime rt = getRouter().getEngine().getJsRuntime();
                // 官方语义：input 全局注入后直接求值。不用 evalLazy + 局部 var 包裹，
                // 否则规则顶层语句在 return(...) 包裹下报语法错误，异常被静默吞掉导致"点了没反应"。
                result = rt.eval(code, input);
                // 取走 refreshPage 请求标记：规则（如口令正确时 setItem 后调 refreshPage）要求刷新，
                // 必须在回调返回 toast:// 等动作后依然执行，否则页面不刷新（如星集口令正确却进不去）。
                needRefresh = rt.consumeRefreshRequest();
                toTop = rt.isRefreshToTop();
                String jsErr = rt.getError();
                if (jsErr != null && !jsErr.isEmpty()) errMsg = jsErr;
            } catch (Throwable e) {
                errMsg = e.getMessage() == null || e.getMessage().isEmpty() ? "执行失败" : e.getMessage();
                android.util.Log.d("HkPage", "evalActionJs failed: " + e.getMessage());
            }
            String r0 = result == null ? "" : result.trim();
            final String r = ("undefined".equals(r0) || "null".equals(r0)) ? "" : r0;
            final String em = errMsg;
            final boolean refresh = needRefresh;
            final boolean scrollTop = toTop;
            String rHead = r.length() > 40 ? r.substring(0, 40) : r;
            com.github.catvod.crawler.SpiderDebug.log("hk", "evalActionJs done resultHead=%s refresh=%s err=%s",
                    rHead, refresh, em == null ? "" : em);
            App.post(() -> {
                if (em != null && !em.isEmpty()) {
                    ToastUtil.show(HkPageActivity.this,
                            "执行失败：" + em, android.widget.Toast.LENGTH_SHORT);
                }
                // 回调返回可导航目标才跳转；普通成功标记字符串（如 "ok"/"1"）不是 URL，
                // 之前会被当成 URL 推开一个空白 V4，导致列表看起来"没刷新"（如口令正确却不刷新）。
                if (isNavigableResult(r)) {
                    HkItem nav = new HkItem();
                    nav.setTitle("");
                    nav.setUrl(r);
                    if (handleActionUrl(r, nav)) {
                        // toast:// 等动作已消费，但规则若请求了 refreshPage（如口令正确），仍需刷新列表
                        if (refresh) {
                        }
                        return;
                    }
                    onContentItemClick(nav);
                    return;
                }
            });
        }).start();
    }

    /**
     * inputRule/confirmRule 回调返回值是否可导航：只有带 scheme 的 URL
     *（http/https/magnet/thunder/pics/x5/web/toast/copy/...）、含 @ 规则标记
     * 或 js: 前缀的才跳转；hiker://empty 与 hiker://* 为官方/内部"无跳转"标记。
     * 规则返回的普通字符串（如口令正确时的 "ok"/"1"）只是成功标记，必须走列表刷新。
     */
    private boolean isNavigableResult(String r) {
        if (r == null) return false;
        String t = r.trim();
        if (t.isEmpty()) return false;
        if ("hiker://empty".equals(t) || t.startsWith("hiker://")) return false;
        String low = t.toLowerCase();
        if (low.matches("^[a-z][a-z0-9+.-]*:.*")) return true;
        if (t.contains("@lazyRule=") || t.contains("@rule=") || t.contains("@inputRule=")
                || t.contains("@confirmRule=") || t.contains("@x5Rule=")) return true;
        return low.startsWith("js:");
    }

    /**
     * 列表页 input://（原版 DetailUIHelper.dealInput 同语义）。
     * 两种形态：JSON {"value","hint","js"}；传统 value////hint.js:js。
     * 弹框用规则的 hint 与预填值，提交后走 evalActionJs(js, 输入值)，动作分发与其它分支一致。
     */
    private void handleListInput(String url) {
        String body = url.substring("input://".length());
        HkDetailBridge.InputExtra extra;
        if (body.startsWith("{") && body.endsWith("}")
                && (body.contains("\"js\"") || body.contains("'js'"))) {
            extra = HkDetailBridge.InputExtra.parseJson(body);
        } else {
            String[] parts = body.split("\\.js:");
            extra = new HkDetailBridge.InputExtra();
            extra.js = com.fongmi.android.tv.api.hk.HkParserKit.arrayToString(parts, 1, ".js:");
            String[] head = parts[0].split("////");
            extra.hint = head.length > 1 ? head[head.length - 1] : null;
            extra.value = com.fongmi.android.tv.api.hk.HkParserKit.arrayToString(head, 0, head.length - 1, "////");
        }
        final String js = extra.js == null ? "" : extra.js;
        boolean isJson = body.startsWith("{");
        com.github.catvod.crawler.SpiderDebug.log("hk", "listInput dialog hint=%s jsLen=%d json=%s",
                extra.hint, js.length(), isJson);
        showSimpleInputDialog("请输入", extra.hint, extra.value, input -> {
            com.github.catvod.crawler.SpiderDebug.log("hk", "listInput submit inputLen=%d", input == null ? -1 : input.length());
            evalActionJs(js, input);
        });
    }

    /**
     * 列表页 select://（原版 DetailUIHelper.dealSelect 同语义，简化为单选对话框；
     * 原版的 attachView/bottom/宫格等展示形态在列表页不展开）。
     * 两种形态：JSON {"options","options2","title","selectedIndex","js"}；传统 opt1&&opt2.js:js。
     * 选中后走 evalActionJs(MY_INDEX 注入 + js, 选中项标题)，动作分发与其它分支一致。
     */
    private void handleListSelect(String url) {
        String body = url.substring("select://".length());
        HkDetailBridge.SelectExtra extra;
        try {
            if (body.startsWith("{") && body.endsWith("}") && body.contains("options")) {
                extra = HkDetailBridge.SelectExtra.parse(body);
            } else {
                String[] parts = body.split("\\.js:");
                extra = new HkDetailBridge.SelectExtra();
                extra.js = com.fongmi.android.tv.api.hk.HkParserKit.arrayToString(parts, 1, ".js:");
                extra.options = new ArrayList<>(Arrays.asList(parts[0].split("&&")));
            }
        } catch (Throwable e) {
            android.util.Log.d("HkPage", "handleListSelect parse failed: " + e.getMessage());
            return;
        }
        final java.util.List<String> titles = new ArrayList<>();
        if (extra.options2 != null && !extra.options2.isEmpty()) {
            for (HkDetailBridge.SelectOption o : extra.options2) titles.add(o.title);
        } else if (extra.options != null && !extra.options.isEmpty()) {
            titles.addAll(extra.options);
        }
        if (titles.isEmpty()) return;
        final String js = extra.js == null ? "" : extra.js;
        String dlgTitle = extra.title != null && !extra.title.isEmpty() ? extra.title : "请选择";
        HkDialogs.cards(this, dlgTitle, titles.toArray(new String[0]), extra.selectedIndex, "取消",
                        (d, which) -> {
                            String code = HkLazyRuleParser.generateMY("MY_INDEX", which) + js;
                            evalActionJs(code, titles.get(which));
                        });
    }

    /** 简单输入对话框（input:// 与 @inputRule= 共用）。 */
    private void showSimpleInputDialog(String title, String def, java.util.function.Consumer<String> cb) {
        showSimpleInputDialog(title, null, def, cb);
    }

    /** 简单输入对话框（带规则 hint）。 */
    private void showSimpleInputDialog(String title, String hint, String def, java.util.function.Consumer<String> cb) {
        final EditText et = new EditText(this);
        et.setHint(TextUtils.isEmpty(hint) ? "请输入" : hint);
        if (!TextUtils.isEmpty(def)) et.setText(def);
        et.setTextColor(0xFFF2F4F8);
        int pad = dp(16);
        et.setPadding(pad, pad, pad, pad);
        // 弹窗标题也走标题颜色标记逻辑（弯引号/ASCII三引号 → 红色）
        CharSequence dialogTitle = "输入";
        if (title != null && !title.isEmpty()) {
            String markedHtml = applyTitleMarkHtml(title);
            if (markedHtml != null) {
                try {
                    dialogTitle = android.text.Html.fromHtml(markedHtml, android.text.Html.FROM_HTML_MODE_LEGACY);
                } catch (Throwable t) {
                    dialogTitle = stripHtml(title);
                }
            } else {
                dialogTitle = stripHtml(title);
            }
        }
        new HkDialogs(this)
                .setTitle(dialogTitle)
                .setView(et)
                .setPositiveButton("确定", (d, w) -> {
                    d.dismiss();
                    cb.accept(et.getText().toString().trim());
                })
                .setNegativeButton("取消", (d, w) -> d.dismiss())
                .show();
    }

    // —— 网页承载收归（2026-11-11 双承载收归）——
    // 本宿主此前有第二副裸承载（ContentAdapter 的 T_WEB/bindWeb 只 loadUrl 且截断
    // url、extra/desc/ua/js 全丢；showWebViewDialog 亦裸 WebView 无桥无注入），
    // x5_webview_single 条目经此面渲染时 extra.js 永不执行。收归后行内与弹窗
    // 一律走 HkWebViewHolder＋HkWebkitHolder 全承载（fy_bridge_app 全桥＋
    // loadAllJs 两拍＋extra/ua/js 透传），组装口径照片段面 updateWebViewHolder /
    // updateModeByDesc：url 全串、desc 定模式、extra 原始 JSON 解析 HkX5Extra。

    /**
     * 条目字段 → holder 组装（照片段 updateWebViewHolder）：video 条目记 videoUrl＋空页＋extra.headers；余者记 url 全串＋X5Extra 全字段。
     * 清场（3-B）：页级 holder 单副复用，每次组装先把上一条目的全部分量清净再按新条目赋值——
     * x5Extra（ua/js 全字段）两支都先置 null、非 video 支随后按本条目 extra 重写（解析失败保持 null）；
     * videoHeaders 两支都先置 null、video 支随后按本条目 extra.headers 重写（extra 空/畸形保持 null）；
     * videoUrl 两支都先置 null、video 支随后写本条目 url（非 video 支恒空）；
     * url/extra(desc) 由本条目 url 与 applyWebMode 覆写（video 支恒空页＋"float&&0"）；
     * mode 不是全量覆写：applyWebMode 与片段 updateModeByDesc 同形，只在 desc 含 FLOAT/LIST
     * 段时 setMode、无模式段保留旧 mode（复核记名账面订正，行为照参照不动）。
     * WebView settings 里的 UA 副本不在 holder 字段层清场内：由 ensurePageWeb 已建面支
     * 对称处理（新条目带 UA 设其 UA、无 UA 恢复建面缺省，见 pageWebDefaultUa）。
     */
    private void configureWebHolder(HkWebViewHolder holder, String colType, String url, String desc, String extra) {
        holder.setX5Extra(null);
        holder.setVideoHeaders(null);
        holder.setVideoUrl(null);
        if ("video".equals(colType)) {
            holder.setVideoUrl(url);
            if (!TextUtils.isEmpty(extra)) {
                try {
                    HkX5Extra x5 = new com.google.gson.Gson().fromJson(extra, HkX5Extra.class);
                    if (x5 != null) holder.setVideoHeaders(x5.getHeaders());
                } catch (Exception ignored) {
                }
            }
            holder.setUrl("file:///android_asset/empty.html");
            applyWebMode(holder, "float&&0");
            return;
        }
        holder.setUrl(url);
        applyWebMode(holder, desc);
        HkX5Extra parsed = null;
        if (!TextUtils.isEmpty(extra)) {
            try {
                parsed = new com.google.gson.Gson().fromJson(extra, HkX5Extra.class);
            } catch (Exception ignored) {
            }
        }
        holder.setX5Extra(parsed);
    }

    /** 照片段 updateModeByDesc：先 setExtra(desc)，再按 "&&" 分段匹配 Mode 名写回 holder。 */
    private void applyWebMode(HkWebViewHolder holder, String desc) {
        holder.setExtra(desc);
        if (TextUtils.isEmpty(desc)) return;
        for (String raw : desc.split("&&")) {
            String seg = raw.trim();
            for (HkWebViewHolder.Mode mode : HkWebViewHolder.Mode.values()) {
                if (seg.equalsIgnoreCase(mode.name())) holder.setMode(mode);
            }
        }
    }

    /** 全承载接线（照片段 wireWebViewInterception 的宿主等价面）：桥引擎/链接拦截走本页规则运行时，懒规则回吐走本页条目分发，播放/图片走 HkDetailBridge 既有出口。 */
    private void wireWebHolder(HkWebViewHolder holder) {
        try {
            com.fongmi.android.tv.api.hk.HkJsRuntime rt = null;
            if (currentRule != null) {
                rt = com.fongmi.android.tv.ui.hk.HkEngineManager.runtimeIfPresent(currentRule);
            }
            if (rt == null && currentRule != null) rt = getRouter().getEngine().getJsRuntime();
            if (rt != null) {
                final com.fongmi.android.tv.api.hk.HkJsRuntime frt = rt;
                holder.setBridgeEngine(() -> frt);
                holder.setUrlInterceptorEvaluator((code, u) -> {
                    try {
                        return frt.evalJS(code, u, true);
                    } catch (Throwable t) {
                        return "";
                    }
                });
            }
        } catch (Throwable e) {
            ToastUtil.show(this, "打开网页失败", android.widget.Toast.LENGTH_SHORT);
        }
        holder.setLazyRuleConsumer(u -> {
            try {
                HkItem nav = new HkItem();
                nav.setUrl(u);
                onContentItemClick(nav);
            } catch (Throwable e) {
                ToastUtil.show(HkPageActivity.this, "无法打开链接", android.widget.Toast.LENGTH_SHORT);
            }
        });
        holder.setBridgeOpenHandler(new HkWebkitHolder.BridgeOpenHandler() {
            @Override
            public void openBridgePage(String title, String url) {
                try {
                    HkItem nav = new HkItem();
                    nav.setTitle(title);
                    nav.setUrl(url);
                    onContentItemClick(nav);
                } catch (Throwable e) {
                    ToastUtil.show(HkPageActivity.this, "无法打开链接", android.widget.Toast.LENGTH_SHORT);
                }
            }

            @Override
            public void openBridgePage(String title, String url, String picUrl, String extraJson) {
                openBridgePage(title, url, picUrl, extraJson, null);
            }

            @Override
            public void openBridgePage(String title, String url, String picUrl, String extraJson,
                                       String pages) {
                try {
                    HkItem nav = new HkItem();
                    nav.setTitle(title);
                    nav.setUrl(url);
                    // 环26 三件：pic 入条目 pic 位（点击链详情/页头既有消费）；
                    // extraJson 整串入条目 extra——openPage 的 getNextPage(currentRule,
                    // url, it.getExtra()) 正是官 toNextPage 吃 extraJson 的同口，
                    // newWindow/windowId 亦在串内随条目走（本宿主开页形无窗口协议，同账退役口径）。
                    // C1 复核订正：DetailPage.pages 入条目 pages 位，openPage 在
                    // getNextPage 后非空覆写子规则（官 toNextPage λ16 同步）。
                    if (!TextUtils.isEmpty(picUrl)) nav.setPic(picUrl);
                    if (!TextUtils.isEmpty(extraJson)) nav.setExtra(extraJson);
                    if (!TextUtils.isEmpty(pages)) nav.setPages(pages);
                    onContentItemClick(nav);
                } catch (Throwable e) {
                    ToastUtil.show(HkPageActivity.this, "无法打开链接", android.widget.Toast.LENGTH_SHORT);
                }
            }
        });
        holder.setBridgePicHandler(new HkWebkitHolder.BridgePicHandler() {
            @Override
            public void showPic(String url) {
                if (url == null) return;
                java.util.List<String> urls = new java.util.ArrayList<>();
                urls.add(url);
                try {
                    new HkDetailBridge().showImageViewer(urls, 0, null, null);
                } catch (Throwable e) {
                    ToastUtil.show(HkPageActivity.this, "图片打开失败", android.widget.Toast.LENGTH_SHORT);
                }
            }

            @Override
            public void showPics(java.util.List<String> urls, String pageUrl) {
                if (urls == null) return;
                try {
                    com.fongmi.android.tv.ui.hk.HkArticleFragment.PictureListRequest req =
                            new com.fongmi.android.tv.ui.hk.HkArticleFragment.PictureListRequest();
                    req.pics = new java.util.ArrayList<>(urls);
                    req.url = pageUrl;
                    new HkDetailBridge().openPictureList(HkPageActivity.this, req);
                } catch (Throwable e) {
                    ToastUtil.show(HkPageActivity.this, "图片打开失败", android.widget.Toast.LENGTH_SHORT);
                }
            }
        });
        holder.setBridgePlayHandler(new HkWebkitHolder.BridgePlayHandler() {
            @Override
            public void playVideo(String title, String url) {
                try {
                    new HkDetailBridge().startPlayerSingle(HkPageActivity.this, title, url);
                } catch (Throwable e) {
                    ToastUtil.show(HkPageActivity.this, "无法播放此链接", android.widget.Toast.LENGTH_SHORT);
                }
            }

            @Override
            public void playVideos(String json) {
                if (json == null) return;
                try {
                    org.json.JSONArray arr = new org.json.JSONArray(json);
                    java.util.List<com.fongmi.android.tv.ui.hk.HkArticleFragment.HkChapter> chapters =
                            new java.util.ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject o = arr.optJSONObject(i);
                        if (o == null) continue;
                        com.fongmi.android.tv.ui.hk.HkArticleFragment.HkChapter ch =
                                new com.fongmi.android.tv.ui.hk.HkArticleFragment.HkChapter();
                        ch.title = o.optString("title", o.optString("name"));
                        ch.url = o.optString("url");
                        ch.pic = o.optString("pic");
                        chapters.add(ch);
                    }
                    new HkDetailBridge().startPlayerChapters(HkPageActivity.this, chapters, null, null, null);
                } catch (Throwable e) {
                    ToastUtil.show(HkPageActivity.this, "无法播放此链接", android.widget.Toast.LENGTH_SHORT);
                }
            }
        });
    }

    /** WebView 弹窗（@x5Rule= 与 x5_webview_single 共用）。 */
    private void showWebViewDialog(String url) {
        showWebViewDialog(url, null);
    }

    /** WebView 弹窗全承载版：WebView 由 HkWebViewHolder/HkWebkitHolder 创建（全桥＋loadAllJs＋extra/ua/js 透传），关窗销毁 holder。 */
    private void showWebViewDialog(String url, HkItem item) {
        if (isFinishing() || isDestroyed()) return;
        try {
            HkWebViewHolder holder = new HkWebViewHolder();
            wireWebHolder(holder);
            if (item != null) {
                configureWebHolder(holder, colTypeOf(item),
                        item.getUrl() == null ? "" : item.getUrl(),
                        item.getDesc() == null ? "" : item.getDesc(),
                        item.getExtra() == null ? "" : item.getExtra());
                // 条目自带 url 与点开目标不一致时以目标为准（extra/desc 仍随条目）
                if (url != null && !url.isEmpty() && !url.equals(holder.getUrl())
                        && !"video".equals(colTypeOf(item))) {
                    holder.setUrl(url);
                }
            } else {
                holder.setUrl(url);
            }
            holder.ensureWebView(this);
            android.webkit.WebView wv = holder.getWebView();
            if (wv == null) {
                ToastUtil.show(this, "打开网页失败", android.widget.Toast.LENGTH_SHORT);
                return;
            }
            wv.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT));
            androidx.appcompat.app.AlertDialog dlg = new HkDialogs(this)
                    .setView(wv)
                    .setPositiveButton("关闭", (d, w) -> d.dismiss())
                    .show();
            // 销毁统一走 dismiss（关闭钮/点外/返回都经此一口，防 WebView 重复 destroy）
            dlg.setOnDismissListener(d -> {
                if (holder.getWebkitHolder() != null) {
                    try { holder.getWebkitHolder().onDestroy(); } catch (Throwable ignored) {}
                }
            });
            // 全屏窗口（定根账 xbw-hk-investigation B）：AlertDialog 默认
            // 按内容 wrap 高，WebView 视口成了矮条——整页型网页（小霸王
            // yikm /play 对移动端无条件 CSS rotate(90deg) 伪横屏，成立
            // 前提是视口＝满屏竖屏）旋转公式当场算错，内容只剩一条、
            // 字全竖排（他 2026-10-31 录屏实证）。8.83 开网页恒走全屏
            // WebViewActivity、视口满屏；此处把弹窗窗口拉满全屏对齐
            // 其视口前提，WebView 补宽视口两项。关闭钮与销毁链不动。
            android.view.Window win = dlg.getWindow();
            if (win != null) {
                win.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT);
            }
        } catch (Throwable e) {
            ToastUtil.show(this, "打开网页失败", android.widget.Toast.LENGTH_SHORT);
        }
    }

    /** V2 内容多 viewType 适配器：视频卡片/小图标占 1 列，其余占满 3 列。 */
    private class ContentAdapter extends RecyclerView.Adapter<ContentAdapter.Holder> {
        static final int T_VIDEO = 0;
        static final int T_TEXT = 1;
        static final int T_RICH = 2;
        static final int T_COLS = 3;
        static final int T_AVATAR = 4;
        static final int T_ICON = 5;
        static final int T_PIC = 6;
        static final int T_MOVIE = 7;
        static final int T_INPUT = 8;
        static final int T_WEB = 10;
        static final int T_BUTTONS = 11;
        static final int T_ICON_FILL = 12;
        static final int T_TEXT_ICON = 13;
        static final int T_CARD = 14;
        static final int T_MOVIE_LEFT_PIC = 15;
        static final int T_ICON_2_ROUND = 16;
        // 图标子类型（对照 8.83 ArticleListAdapter 各 icon_* 独立布局）：
        static final int T_ICON_4_SMALL = 17;   // icon_small_4/icon_5/icon_5_no_crop：32dp 图上文下
        static final int T_ICON_ROUND = 18;     // icon_round_4：42dp 图上文下 + 圆形裁剪
        static final int T_ICON_ROUND_SMALL = 19; // icon_round_small_4：32dp 图上文下 + 圆形裁剪
        static final int T_ICON_2 = 20;         // icon_2：左 28dp 图右文字横向卡片
        static final int T_ICON_4_CARD = 21;    // icon_4_card：44dp 圆角卡片图标 + 文字
        static final int T_ICON_3_SMALL = 22;   // icon_small_3：图标左文字右同行
        static final int T_ICON_1_SEARCH = 23;  // icon_1_search：搜索条
        static final int T_ICON_1_LEFT_PIC = 24; // icon_1_left_pic：左 60dp 图右文全宽行
        static final int T_MOVIE_VERTICAL = 25; // movie_1_vertical_pic：全宽左竖海报右文（8.83 item_movie_one_col_vertical_pic）
        static final int T_MOVIE_VERTICAL_BLUR = 26; // movie_1_vertical_pic_blur：毛玻璃背景 + 竖海报
        static final int T_EMPTY = 27; // 8.83 原版：poly 分组内非首个条目占位的空行（getItemViewType 返回 -1）
        // 8.83 原版：以下条目类型各自独立 viewType（对照 ArticleColTypeEnum），不合并
        static final int T_TEXT_1 = 28;
        static final int T_TEXT_CENTER_1 = 29;
        static final int T_LONG_TEXT = 30;
        static final int T_HEADER = 31;
        static final int T_FOOTER = 32;
        static final int T_TEXT_2 = 33;
        static final int T_TEXT_3 = 34;
        static final int T_TEXT_4 = 35;
        static final int T_TEXT_5 = 36;
        static final int T_CARD_PIC_1 = 37;
        static final int T_CARD_PIC_2 = 38;
        static final int T_CARD_PIC_2_2 = 39;
        static final int T_CARD_PIC_2_2_LEFT = 40;
        static final int T_CARD_PIC_3 = 41;
        static final int T_CARD_PIC_3_CENTER = 42;
        static final int T_PIC_1 = 43;
        static final int T_PIC_1_CARD = 44;
        static final int T_PIC_1_CENTER = 45;
        static final int T_PIC_1_FULL = 46;
        static final int T_PIC_2 = 47;
        static final int T_PIC_2_CARD = 48;
        static final int T_PIC_3 = 49;
        static final int T_PIC_3_SQUARE = 50;
        static final int T_DETAIL_HEADER = 51; // 8.83 原版详情页头部：movie_one_col（标题左+封面右）
        static final int T_HEADER_TABS = 52; // 8.83 bindForHeaderHolder：规则 header 条目横向分类 tab（title/url 按 & 切分）
        // 8.83 第一批补齐（对照 ArticleListAdapter 46 种 ViewHolder）：
        static final int T_NOPIC = 53;        // movie_one_col_nopic：无图影片条目（text_1 用）
        static final int T_NOPIC_CENTER = 54; // movie_one_col_nopic_center：无图居中（text_center_1 用）
        static final int T_LINE = 55;         // line/line_blank：分隔线/空白块（LineHolder）
        static final int T_SCROLL_BUTTON = 56; // scroll_button：横向滚动按钮组（ScrollButtonHolder）
        static final int T_FLEX_BUTTON = 57;   // flex_button：流式按钮组（FlexButtonHolder）
        static final int T_CHANNEL = 58;       // text_2：矩形频道条目（ChannelHolder，item_card_rect）
        static final int T_TOOLS = 59;         // movie_3/movie_3_marquee：影片工具卡片（ToolsHolder）
        static final int T_ICON_FIVE = 60;     // icon_5/icon_5_no_crop：五列图标（IconFiveHolder）

        private final List<HkItem> source;
        /** 展示列表（与 source 同一引用；8.83 原版 getItemCount 直接返回 list.size()，不做快照）。 */
        private List<HkItem> items;
        /** T_WEB 全承载 holder（照片段面：一页一副 holder，条目字段在 bind 时组装；宿主 onDestroy 时销毁）。 */
        private HkWebViewHolder pageWebHolder;
        /** 已组装条目的键（colType|url|desc|extra）：键不变不重装不重载，防 RecyclerView 反复 bind 把网页刷回起点。 */
        private String pageWebKey;
        /** 页级 WebView 建面时的缺省 UA（3-B 续）：建面后、首次条目 UA 覆写前抓存于 ensurePageWeb 新建支；换条目无 UA 时 settings UA 恢复为此值（不写死 UA 串），holder 销毁时随之清掉、下副 holder 重新抓存。 */
        private String pageWebDefaultUa;
        /** FLOAT 页级容器（3-A，对应片段面 fragment_article_list 的 webview_container）：懒建后插到列表同级、列表之前；FLOAT 条目 WebView 挂此定高，非 FLOAT 时清空。 */
        private FrameLayout pageWebFloatBox;
        /** 本适配器附着的列表（页级容器插位用）。 */
        private RecyclerView pageWebList;
        /** 8.83 updatePolyList：连续相同 col_type 条目的分组。 */
        private final java.util.List<java.util.List<HkItem>> polyList = new java.util.ArrayList<>();
        /** 8.83 polyPositionMap：条目下标 → 所属分组下标。 */
        private final java.util.Map<Integer, Integer> polyPositionMap = new java.util.HashMap<>();
        private int videoImgH;
        private final int picH;

        ContentAdapter(List<HkItem> items) {
            this.source = items;
            this.items = items;
            updatePolyList();
            DisplayMetrics dm = getResources().getDisplayMetrics();
            picH = (dm.widthPixels - dp(16) * 2) * 9 / 16;
            updateVideoImgH();
        }

        /**
         * 视频卡片图高：对齐 8.83 item_movie_tools，封面容器固定 150dp。
         * （之前用列宽*3/2≈180dp，比 8.83 高 20%，centerCrop 过度放大导致封面显"歪"。）
         */
        void updateVideoImgH() {
            videoImgH = dp(150);
        }

        /**
         * videos 增删后重算 poly 分组（对照 8.83 updatePolyList 调用点）。
         */
        public void refreshGroups() {
            updatePolyList();
        }

        /**
         * 对照 8.83 ArticleListAdapter.updatePolyList()（反编译第 190-213 行）：
         * 连续相同 col_type 的条目自动合并为一组，polyPositionMap 记录条目下标→分组下标。
         * 不造假 col_type，不合成条目。
         */
        private void updatePolyList() {
            polyList.clear();
            polyPositionMap.clear();
            if (source == null || source.isEmpty()) return;
            for (int i = 0; i < source.size(); i++) {
                if (polyList.isEmpty()) polyList.add(new ArrayList<>());
                List<HkItem> last = polyList.get(polyList.size() - 1);
                if (last.isEmpty()) {
                    last.add(source.get(i));
                    polyPositionMap.put(i, 0);
                } else if (colTypeOf(last.get(0)).equals(colTypeOf(source.get(i)))) {
                    last.add(source.get(i));
                    polyPositionMap.put(i, polyList.size() - 1);
                } else {
                    List<HkItem> g = new ArrayList<>();
                    g.add(source.get(i));
                    polyList.add(g);
                    polyPositionMap.put(i, polyList.size() - 1);
                }
            }
        }

        /**
         * 官方 60 列栅格 span（8.83 ArticleColTypeEnum.spanCount 反编译实测）：
         * text_2/pic_2/icon_2(2/行)=30；text_3/pic_3/icon_3(3/行)=20；
         * text_4/icon_4(4/行)=15；text_5/icon_5(5/行)=12；其余全宽=60。
         */
        public int spanFor(int position) {
            if (position < 0 || position >= items.size()) return 60;
            HkItem it = items.get(position);
            int t = getItemViewType(position);
            String ct = colTypeOf(it);
            switch (t) {
                case T_VIDEO:
                    // 官方：movie_2 span=30（2/行）；movie_3 span=20（3/行）
                    if ("movie_2".equals(ct)) return 30;
                    return 20;
                case T_COLS:
                case T_TEXT_2:
                    // text_2=30（2/行）
                    if (ct.endsWith("_2")) return 30;
                    if (ct.endsWith("_3")) return 20;
                    if (ct.endsWith("_4")) return 15;
                    if (ct.endsWith("_5")) return 12;
                    return 30;
                case T_TEXT_3:
                    return 20; // text_3=20（3/行）
                case T_TEXT_4:
                    return 15; // text_4=15（4/行）
                case T_TEXT_5:
                    return 12; // text_5=12（5/行）
                case T_ICON:
                case T_ICON_ROUND:
                    return 15; // icon_4 / icon_round_4：4/行（官方 spanCount=15）
                case T_ICON_4_SMALL:
                case T_ICON_ROUND_SMALL:
                    if ("icon_5".equals(ct) || "icon_5_no_crop".equals(ct)) return 12; // 官方 spanCount=12
                    return 15; // icon_small_4 / icon_round_small_4：4/行
                case T_ICON_4_CARD:
                    return 15; // icon_4_card：4/行（官方 spanCount=15）
                case T_ICON_FILL:
                    return 20; // icon_3_fill / icon_3_round_fill：3/行
                case T_ICON_2:
                case T_ICON_2_ROUND:
                    return 30; // icon_2 / icon_2_round：2/行（官方 spanCount=30）
                case T_ICON_3_SMALL:
                    return 20; // icon_small_3：3/行（官方 spanCount=20）
                case T_ICON_1_SEARCH:
                case T_ICON_1_LEFT_PIC:
                    return 60; // icon_1_search / icon_1_left_pic 全宽（官方 spanCount=60）
                case T_TEXT_ICON:
                    return 60; // text_icon 全宽
                case T_CARD:
                    if ("card_pic_2".equals(ct) || "card_pic_2_2".equals(ct)
                            || "card_pic_2_2_left".equals(ct)) return 30;
                    if ("card_pic_3".equals(ct) || "card_pic_3_center".equals(ct)) return 20;
                    return 60; // card_pic_1 全宽
                case T_CARD_PIC_1:
                    return 60; // card_pic_1 全宽
                case T_CARD_PIC_2:
                case T_CARD_PIC_2_2:
                case T_CARD_PIC_2_2_LEFT:
                    return 30; // 2/行
                case T_CARD_PIC_3:
                case T_CARD_PIC_3_CENTER:
                    return 20; // 3/行
                case T_MOVIE_LEFT_PIC:
                    return 60; // movie_1 左图右文全宽
                case T_MOVIE_VERTICAL:
                case T_MOVIE_VERTICAL_BLUR:
                    return 60; // movie_1_vertical_pic(_blur) 全宽（D3）
                case T_PIC:
                    if (ct.startsWith("pic_2") || "card_pic_2".equals(ct)
                            || "card_pic_2_2".equals(ct) || "card_pic_2_2_left".equals(ct)) return 30;
                    if (ct.startsWith("pic_3") || "card_pic_3".equals(ct) || "card_pic_3_center".equals(ct)) return 20;
                    return 60; // pic_1 / pic_1_full / pic_1_center / pic_1_card / card_pic_1
                case T_PIC_1:
                case T_PIC_1_CARD:
                case T_PIC_1_CENTER:
                case T_PIC_1_FULL:
                    return 60; // pic_1 系列全宽
                case T_PIC_2:
                case T_PIC_2_CARD:
                    return 30; // pic_2 系列 2/行
                case T_PIC_3:
                case T_PIC_3_SQUARE:
                    return 20; // pic_3 系列 3/行
                case T_DETAIL_HEADER:
                    return 60; // 详情页头部全宽
                case T_NOPIC:
                case T_NOPIC_CENTER:
                case T_LINE:
                case T_SCROLL_BUTTON:
                case T_FLEX_BUTTON:
                case T_CHANNEL:
                    return 60; // 全宽
                case T_TOOLS:
                    return 20; // movie_3：3/行（官方 spanCount=20）
                case T_ICON_FIVE:
                    return 12; // icon_5：5/行（官方 spanCount=12）
                default:
                    return 60;
            }
        }

        class Holder extends RecyclerView.ViewHolder {
            ImageView cover;
            TextView title, desc, overlay;
            LinearLayout cols;
            EditText input;
            FrameLayout webBox;
            // 8.83 第一批补齐新增：
            android.widget.HorizontalScrollView hsvButtons; // T_SCROLL_BUTTON
            LinearLayout llButtons;                          // T_SCROLL_BUTTON
            TextView tvExpand;                               // T_SCROLL_BUTTON
            com.google.android.flexbox.FlexboxLayout flexbox; // T_FLEX_BUTTON

            Holder(View v, int type) {
                super(v);
                switch (type) {
                    case T_VIDEO:
                        cover = v.findViewById(R.id.iv_cover);
                        title = v.findViewById(R.id.tv_title);
                        desc = v.findViewById(R.id.tv_desc);
                        overlay = v.findViewById(R.id.tv_overlay);
                        View containerV = v.findViewById(R.id.cover_container);
                        if (containerV != null) {
                            ViewGroup.LayoutParams vlp = containerV.getLayoutParams();
                            vlp.height = videoImgH;
                            containerV.setLayoutParams(vlp);
                        } else {
                            ViewGroup.LayoutParams vlp = cover.getLayoutParams();
                            vlp.height = videoImgH;
                            cover.setLayoutParams(vlp);
                        }
                        break;
                    case T_TEXT:
                    case T_TEXT_1:
                    case T_TEXT_CENTER_1:
                    case T_HEADER:
                    case T_FOOTER:
                    case T_RICH:
                        title = v.findViewById(R.id.tv_title);
                        desc = v.findViewById(R.id.tv_desc);
                        break;
                    case T_LONG_TEXT:
                        title = v.findViewById(R.id.tv_title);
                        break;
                    case T_COLS:
                    case T_TEXT_2:
                    case T_TEXT_3:
                    case T_TEXT_4:
                    case T_TEXT_5:
                    case T_BUTTONS:
                    case T_HEADER_TABS:
                        cols = v.findViewById(R.id.cols_container);
                        break;
                    case T_AVATAR:
                        cover = v.findViewById(R.id.iv_avatar);
                        title = v.findViewById(R.id.tv_title);
                        cover.setClipToOutline(true);
                        cover.setOutlineProvider(new ViewOutlineProvider() {
                            @Override
                            public void getOutline(View view, Outline outline) {
                                outline.setOval(0, 0, view.getWidth(), view.getHeight());
                            }
                        });
                        break;
                    case T_ICON:
                    case T_ICON_FILL:
                    case T_ICON_2_ROUND:
                    case T_ICON_4_SMALL:
                    case T_ICON_ROUND:
                    case T_ICON_ROUND_SMALL:
                    case T_ICON_2:
                    case T_ICON_4_CARD:
                    case T_ICON_3_SMALL:
                    case T_ICON_1_SEARCH:
                        cover = v.findViewById(R.id.iv_icon);
                        title = v.findViewById(R.id.tv_title);
                        break;
                    case T_ICON_1_LEFT_PIC:
                        cover = v.findViewById(R.id.iv_icon);
                        title = v.findViewById(R.id.tv_title);
                        desc = v.findViewById(R.id.tv_desc);
                        break;
                    case T_TEXT_ICON:
                        cover = v.findViewById(R.id.iv_icon);
                        title = v.findViewById(R.id.tv_title);
                        break;
                    case T_CARD:
                    case T_CARD_PIC_1:
                    case T_CARD_PIC_2:
                    case T_CARD_PIC_2_2:
                    case T_CARD_PIC_2_2_LEFT:
                    case T_CARD_PIC_3:
                    case T_CARD_PIC_3_CENTER:
                        cover = v.findViewById(R.id.iv_cover);
                        title = v.findViewById(R.id.tv_title);
                        overlay = v.findViewById(R.id.tv_overlay);
                        break;
                    case T_MOVIE_LEFT_PIC:
                        cover = v.findViewById(R.id.iv_cover);
                        title = v.findViewById(R.id.tv_title);
                        desc = v.findViewById(R.id.tv_desc);
                        break;
                    case T_MOVIE_VERTICAL:
                        cover = v.findViewById(R.id.iv_cover);
                        title = v.findViewById(R.id.tv_title);
                        desc = v.findViewById(R.id.tv_desc);
                        break;
                    case T_MOVIE_VERTICAL_BLUR:
                        cover = v.findViewById(R.id.iv_cover);
                        title = v.findViewById(R.id.tv_title);
                        desc = v.findViewById(R.id.tv_desc);
                        break;
                    case T_PIC:
                    case T_PIC_1:
                    case T_PIC_1_CARD:
                    case T_PIC_1_CENTER:
                    case T_PIC_1_FULL:
                    case T_PIC_2:
                    case T_PIC_2_CARD:
                    case T_PIC_3:
                    case T_PIC_3_SQUARE:
                        cover = v.findViewById(R.id.iv_pic);
                        ViewGroup.LayoutParams plp = cover.getLayoutParams();
                        plp.height = picH;
                        cover.setLayoutParams(plp);
                        break;
                    case T_MOVIE:
                        cover = v.findViewById(R.id.iv_cover);
                        title = v.findViewById(R.id.tv_title);
                        desc = v.findViewById(R.id.tv_desc);
                        break;
                    case T_INPUT:
                        input = v.findViewById(R.id.et_input);
                        break;
                    case T_WEB:
                        // 只取挂载容器；WebView 本体由全承载 holder 创建后挂入（见 bindWeb），此处不再裸建/裸配 WebView
                        webBox = v.findViewById(R.id.wv_page);
                        break;
                    case T_DETAIL_HEADER:
                        // 8.83 原版详情页头部（item_movie_one_col）：标题左 + 封面右
                        cover = v.findViewById(R.id.dh_cover);
                        title = v.findViewById(R.id.dh_title);
                        desc = v.findViewById(R.id.dh_desc);
                        break;
                    case T_NOPIC:
                    case T_NOPIC_CENTER:
                        // 8.83 item_movie_one_col_nopic(_center)：无图影片条目
                        title = v.findViewById(R.id.tv_title);
                        desc = v.findViewById(R.id.tv_desc);
                        break;
                    case T_LINE:
                        // 8.83 LineHolder：分隔线/空白块，无需绑定子 view
                        break;
                    case T_SCROLL_BUTTON:
                        // 8.83 ScrollButtonHolder：横向滚动按钮组
                        hsvButtons = v.findViewById(R.id.hsv_buttons);
                        llButtons = v.findViewById(R.id.ll_buttons);
                        tvExpand = v.findViewById(R.id.tv_expand);
                        break;
                    case T_FLEX_BUTTON:
                        // 8.83 FlexButtonHolder：流式按钮组
                        flexbox = v.findViewById(R.id.flexboxLayout);
                        break;
                    case T_CHANNEL:
                        // 8.83 ChannelHolder（item_card_rect）：矩形频道条目
                        title = v.findViewById(R.id.tv_title);
                        break;
                    case T_TOOLS:
                        // 8.83 ToolsHolder（item_movie_tools）：影片工具卡片
                        cover = v.findViewById(R.id.iv_cover);
                        title = v.findViewById(R.id.tv_title);
                        desc = v.findViewById(R.id.tv_desc);
                        break;
                    case T_ICON_FIVE:
                        // 8.83 IconFiveHolder：五列图标
                        cover = v.findViewById(R.id.iv_icon);
                        title = v.findViewById(R.id.tv_title);
                        break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position < 0 || position >= items.size()) return T_VIDEO;
            int t = contentTypeOf(items.get(position));
            // 对照 8.83 getItemViewType（反编译第 309-321 行）：
            // scroll_button/flex_button 连续组内非首个条目 → 空行（8.83 返回 -1）
            if (t == T_BUTTONS) {
                Integer gi = polyPositionMap.get(position);
                if (gi != null && gi >= 0 && gi < polyList.size()) {
                    List<HkItem> g = polyList.get(gi);
                    if (g != null && !g.isEmpty() && g.get(0) != items.get(position)) {
                        return T_EMPTY;
                    }
                }
            }
            return t;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // 8.83 原版：poly 分组内非首个条目返回 -1，对应空行占位
            if (viewType == T_EMPTY) {
                android.widget.Space v = new android.widget.Space(parent.getContext());
                v.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0));
                return new Holder(v, viewType);
            }
            int layout;
            switch (viewType) {
                case T_TEXT:
                case T_TEXT_1:
                case T_TEXT_CENTER_1:
                case T_HEADER:
                case T_FOOTER:
                case T_RICH:
                    layout = R.layout.item_hk_text;
                    break;
                case T_LONG_TEXT:
                    // 8.83 item_long_text：裸文本无底板（同 HkDetailFragment 的修正）
                    layout = R.layout.item_hk_text_long;
                    break;
                case T_COLS:
                case T_TEXT_2:
                case T_TEXT_3:
                case T_TEXT_4:
                case T_TEXT_5:
                    layout = R.layout.item_hk_cols;
                    break;
                case T_BUTTONS:
                    layout = R.layout.item_hk_cols;
                    break;
                case T_HEADER_TABS:
                    layout = R.layout.item_hk_cols;
                    break;
                case T_AVATAR:
                    layout = R.layout.item_hk_avatar;
                    break;
                case T_ICON:
                    layout = R.layout.item_hk_icon_four_col; // icon_4：42dp 图上文下（对照 8.83 item_icon_four_col）
                    break;
                case T_ICON_4_SMALL:
                    layout = R.layout.item_hk_icon_four_small; // icon_small_4/icon_5：32dp 图上文下
                    break;
                case T_ICON_ROUND:
                    layout = R.layout.item_hk_icon_four_col; // icon_round_4：42dp，bind 时圆形裁剪
                    break;
                case T_ICON_ROUND_SMALL:
                    layout = R.layout.item_hk_icon_four_small; // icon_round_small_4：32dp，bind 时圆形裁剪
                    break;
                case T_ICON_2:
                    layout = R.layout.item_hk_icon_two_col; // icon_2：左 28dp 图右文字横向卡片
                    break;
                case T_ICON_4_CARD:
                    layout = R.layout.item_hk_icon_four_card; // icon_4_card：44dp 圆角图标 + 文字
                    break;
                case T_ICON_3_SMALL:
                    layout = R.layout.item_hk_icon_three_small; // icon_small_3：图标左文字右同行
                    break;
                case T_ICON_1_SEARCH:
                    layout = R.layout.item_hk_icon_search_col; // icon_1_search：搜索条
                    break;
                case T_ICON_1_LEFT_PIC:
                    layout = R.layout.item_hk_icon_left_pic; // icon_1_left_pic：左 60dp 图右文全宽
                    break;
                case T_ICON_FILL:
                    layout = R.layout.item_hk_icon_3_fill;
                    break;
                case T_ICON_2_ROUND:
                    layout = R.layout.item_hk_icon_2_round;
                    break;
                case T_TEXT_ICON:
                    layout = R.layout.item_hk_text_icon;
                    break;
                case T_CARD:
                case T_CARD_PIC_1:
                case T_CARD_PIC_2:
                case T_CARD_PIC_2_2:
                case T_CARD_PIC_2_2_LEFT:
                case T_CARD_PIC_3:
                case T_CARD_PIC_3_CENTER:
                    // 此前误用 item_hk_card_rect（集数 chip 布局，无 iv_cover/tv_title/tv_overlay），
                    // Holder 取回 null，bindCard 一绑定就 NPE——用 card_pic 的规则一点就崩。
                    // 改用控件齐全的 item_hk_video（封面+标题+角标），列宽仍由 spanFor 按类型给。
                    layout = R.layout.item_hk_video;
                    break;
                case T_MOVIE_LEFT_PIC:
                    layout = R.layout.item_hk_movie_left_pic;
                    break;
                case T_MOVIE_VERTICAL:
                    layout = R.layout.item_hk_movie_vertical;
                    break;
                case T_MOVIE_VERTICAL_BLUR:
                    layout = R.layout.item_hk_movie_vertical_blur;
                    break;
                case T_PIC:
                case T_PIC_1:
                case T_PIC_1_CARD:
                case T_PIC_1_CENTER:
                case T_PIC_1_FULL:
                case T_PIC_2:
                case T_PIC_2_CARD:
                case T_PIC_3:
                case T_PIC_3_SQUARE:
                    layout = R.layout.item_hk_pic;
                    break;
                case T_MOVIE:
                    layout = R.layout.item_hk_movie;
                    break;
                case T_INPUT:
                    layout = R.layout.item_hk_input;
                    break;
                case T_WEB:
                    layout = R.layout.item_hk_web;
                    break;
                case T_DETAIL_HEADER:
                    layout = R.layout.item_hk_detail_header;
                    break;
                case T_NOPIC:
                    layout = R.layout.item_hk_movie_nopic;
                    break;
                case T_NOPIC_CENTER:
                    layout = R.layout.item_hk_movie_nopic_center;
                    break;
                case T_LINE:
                    // line 用细线，line_blank 用空白块——按 col_type 区分在 bind 时处理，
                    // 此处默认细线布局
                    layout = R.layout.item_hk_line;
                    break;
                case T_SCROLL_BUTTON:
                    layout = R.layout.item_hk_scroll_button;
                    break;
                case T_FLEX_BUTTON:
                    layout = R.layout.item_hk_flex_button;
                    break;
                case T_CHANNEL:
                    layout = R.layout.item_hk_channel;
                    break;
                case T_TOOLS:
                    layout = R.layout.item_hk_tools;
                    break;
                case T_ICON_FIVE:
                    layout = R.layout.item_hk_icon_five;
                    break;
                default:
                    layout = R.layout.item_hk_video;
                    break;
            }
            View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
            return new Holder(v, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            if (position < 0 || position >= items.size()) return;
            HkItem item = items.get(position);
            // 按 Holder 创建时的 viewType 来 bind：布局过程中若 viewType 发生变化，
            // 用 position 查到的新 viewType 去 bind 会与 Holder 已初始化的 view 不一致，
            // 导致 TextView 为 null 崩溃（movie_1/movie_2 改 span 后复现）。
            switch (h.getItemViewType()) {
                case T_VIDEO:
                    bindVideo(h, item);
                    break;
                case T_TEXT:
                case T_TEXT_1:
                case T_TEXT_CENTER_1:
                case T_HEADER:
                case T_FOOTER:
                    bindText(h, item);
                    break;
                case T_LONG_TEXT:
                    bindLongText(h, item);
                    break;
                case T_RICH:
                    bindRich(h, item);
                    break;
                case T_COLS:
                case T_TEXT_2:
                case T_TEXT_3:
                case T_TEXT_4:
                case T_TEXT_5:
                    bindCols(h, item);
                    break;
                case T_BUTTONS:
                    bindButtons(h, position);
                    break;
                case T_HEADER_TABS:
                    bindHeaderTabs(h, item);
                    break;
                case T_AVATAR:
                    bindAvatar(h, item);
                    break;
                case T_ICON:
                case T_ICON_FILL:
                case T_ICON_2_ROUND:
                case T_ICON_4_SMALL:
                case T_ICON_ROUND:
                case T_ICON_ROUND_SMALL:
                case T_ICON_2:
                case T_ICON_4_CARD:
                case T_ICON_3_SMALL:
                case T_ICON_1_SEARCH:
                case T_ICON_1_LEFT_PIC:
                    bindIcon(h, item);
                    break;
                case T_TEXT_ICON:
                    bindTextIcon(h, item);
                    break;
                case T_CARD:
                case T_CARD_PIC_1:
                case T_CARD_PIC_2:
                case T_CARD_PIC_2_2:
                case T_CARD_PIC_2_2_LEFT:
                case T_CARD_PIC_3:
                case T_CARD_PIC_3_CENTER:
                    bindCard(h, item);
                    break;
                case T_MOVIE_LEFT_PIC:
                    bindMovie(h, item);
                    break;
                case T_MOVIE_VERTICAL:
                    bindMovieVertical(h, item, false);
                    break;
                case T_MOVIE_VERTICAL_BLUR:
                    bindMovieVertical(h, item, true);
                    break;
                case T_PIC:
                case T_PIC_1:
                case T_PIC_1_CARD:
                case T_PIC_1_CENTER:
                case T_PIC_1_FULL:
                case T_PIC_2:
                case T_PIC_2_CARD:
                case T_PIC_3:
                case T_PIC_3_SQUARE:
                    bindPic(h, item);
                    break;
                case T_MOVIE:
                    bindMovie(h, item);
                    break;
                case T_INPUT:
                    bindInput(h, item);
                    break;
                case T_EMPTY:
                    // poly 分组内非首个条目的空行占位，无需绑定
                    break;
                case T_WEB:
                    bindWeb(h, item);
                    break;
                case T_DETAIL_HEADER:
                    bindDetailHeader(h, item);
                    break;
                case T_NOPIC:
                case T_NOPIC_CENTER:
                    // 8.83 无图影片条目：样式全在复刻布局里，bind 只
                    // 设文本（照官 TextOneColHolder）——旧形走 bindText
                    // 会以 13/14sp、藏 desc 等代码值覆掉布局官值。
                    bindNopic(h, item);
                    break;
                case T_LINE:
                    // 8.83 LineHolder：分隔线/空白块，无需绑定
                    break;
                case T_SCROLL_BUTTON:
                    bindScrollButtons(h, position);
                    break;
                case T_FLEX_BUTTON:
                    bindFlexButtons(h, position);
                    break;
                case T_CHANNEL:
                    // 8.83 ChannelHolder：矩形频道条目，标题居中
                    if (h.title != null) {
                        h.title.setText(titleSpan(item.getTitle()));
                    }
                    break;
                case T_TOOLS:
                    // 8.83 ToolsHolder：影片工具卡片，复用 bindVideo（cover/title/desc）
                    bindVideo(h, item);
                    break;
                case T_ICON_FIVE:
                    // 8.83 IconFiveHolder：五列图标，复用 bindIcon
                    bindIcon(h, item);
                    break;
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        /** 视频卡片：保持原 VideoAdapter 行为；desc 压在封面底部（原版 overlay 样式）。 */
        private void bindVideo(Holder h, HkItem item) {
            if (h.title == null || h.cover == null) return; // 防 ViewHolder 类型错配
            h.title.setText(titleSpan(item.getTitle()));
            // movie_3_marquee：标题跑马灯（对照 8.83 item_movie_tools_marquee，marqueeRepeatLimit=3）；
            // 非 marquee 时复位（防复用串台）
            String ct = item.getColType() == null ? "" : item.getColType().trim().toLowerCase();
            if ("movie_3_marquee".equals(ct)) {
                h.title.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                h.title.setSingleLine(true);
                h.title.setMarqueeRepeatLimit(3);
                h.title.setSelected(true);
            } else {
                // 8.83 原版：标题单行截断（item_movie_tools reult_tools_title maxLines=1）
                h.title.setEllipsize(TextUtils.TruncateAt.END);
                h.title.setSingleLine(true);
                h.title.setSelected(false);
            }
            // desc 压在封面上（原版样式），不再放标题下面
            String d = stripHtml(item.getDesc());
            if (h.overlay != null) {
                h.overlay.setText(d);
                h.overlay.setVisibility(TextUtils.isEmpty(d) ? View.GONE : View.VISIBLE);
            }
            h.desc.setVisibility(View.GONE);
            ImgUtil.load(item.getTitle(), item.getPic(), h.cover, hkRuleUrl());
            // 8.83：所有条目类型走同一条 onClick→clickItem→dealWithUrl 链，
            // video 也不单开直调详情（否则动作协议/@rule=分流/详情内直播等都被跳过）
            h.itemView.setOnClickListener(v -> {
                try {
                    int pos = h.getAdapterPosition();
                    if (pos >= 0 && pos < items.size() && currentRule != null) {
                        onContentItemClick(items.get(pos));
                    }
                } catch (Exception e) {
                    android.util.Log.e("HkPage", "bindVideo click failed", e);
                }
            });
        }

        /** 文本行：text_center_1 居中灰字；text_1 去标签标题+描述；long_text 多行。 */
        /** 8.83 LongTextHolder：只设文本，样式全在布局（item_long_text）里 */
        private void bindLongText(Holder h, HkItem item) {
            if (h.title != null) h.title.setText(titleSpan(item.getTitle()));
        }

        /** 8.83 无图条目（text_1 / text_center_1）：布局
         * item_hk_movie_nopic(_center) 已逐值复刻（无背景、标题
         * 16.5sp、居中型的标题与描述都居中），bind 只设标题与
         * 描述文本，desc 空才藏——旧 bindText 的居中支把 desc
         * 直接藏掉、与官形不符（08 销账表第三节）。 */
        private void bindNopic(Holder h, HkItem item) {
            if (h.title != null) h.title.setText(titleSpan(item.getTitle()));
            if (h.desc == null) return;
            String rawD = item.getDesc();
            if (TextUtils.isEmpty(rawD)) {
                h.desc.setVisibility(View.GONE);
            } else {
                h.desc.setVisibility(View.VISIBLE);
                h.desc.setText(stripHtml(rawD));
            }
        }

        private void bindText(Holder h, HkItem item) {
            String ct = colTypeOf(item);
            if ("text_center_1".equals(ct)) {
                h.title.setGravity(Gravity.CENTER);
                h.title.setTextColor(0xFF101216);
                h.title.setTextSize(13);
                h.title.setMaxLines(4);
                h.title.setText(titleSpan(item.getTitle()));
                h.desc.setVisibility(View.GONE);
            } else {
                boolean longText = "long_text".equals(ct);
                h.title.setGravity(Gravity.START);
                h.title.setTextColor(0xFF1A1D24);
                h.title.setTextSize(14);
                h.title.setMaxLines(longText ? 30 : 3);
                h.title.setText(titleSpan(item.getTitle()));
                String rawD = item.getDesc();
                if (TextUtils.isEmpty(rawD)) {
                    h.desc.setVisibility(View.GONE);
                } else {
                    h.desc.setVisibility(View.VISIBLE);
                    if (longText) {
                        try {
                            String htmlD = rawD.replace("\n", "<br>");
                            h.desc.setText(Html.fromHtml(htmlD, Html.FROM_HTML_MODE_LEGACY));
                        } catch (Throwable t) {
                            h.desc.setText(stripHtml(rawD));
                        }
                    } else {
                        h.desc.setText(stripHtml(rawD));
                    }
                }
                h.desc.setMaxLines(longText ? 60 : 5);
            }
            // 简介折叠：long_text 长文本点击切换折叠/展开
            if ("long_text".equals(ct) && h.desc != null) {
                h.desc.setTag(R.id.tv_desc, Boolean.FALSE);
                h.desc.setOnClickListener(v -> {
                    Boolean expanded = (Boolean) v.getTag(R.id.tv_desc);
                    boolean isExpanded = expanded != null && expanded;
                    v.setTag(R.id.tv_desc, !isExpanded);
                    ((TextView) v).setMaxLines(isExpanded ? 5 : 60);
                });
            }
            setContentClick(h, item);
        }

        /** 富文本行：Html.fromHtml 显示，失败回退去标签；标题颜色标记（弯引号 ""红/''橙）同样生效。 */
        private void bindRich(Holder h, HkItem item) {
            h.title.setGravity(Gravity.START);
            h.title.setTextColor(0xFF1A1D24);
            h.title.setTextSize(14);
            h.title.setMaxLines(30);
            String rawTitle = item.getTitle() == null ? "" : item.getTitle();
            String markedHtml = applyTitleMarkHtml(rawTitle);
            try {
                CharSequence cs = Html.fromHtml(markedHtml != null ? markedHtml : rawTitle, Html.FROM_HTML_MODE_LEGACY);
                h.title.setText(cs);
            } catch (Throwable t) {
                h.title.setText(titleSpan(item.getTitle()));
            }
            String d = item.getDesc();
            if (TextUtils.isEmpty(d)) {
                h.desc.setVisibility(View.GONE);
            } else {
                h.desc.setVisibility(View.VISIBLE);
                try {
                    h.desc.setText(Html.fromHtml(d, Html.FROM_HTML_MODE_LEGACY));
                } catch (Throwable t) {
                    h.desc.setText(stripHtml(d));
                }
            }
            setContentClick(h, item);
        }

        /**
         * 多列文本格：官方语义是一个条目占一格，N 个条目一行（text_2=2/行 … text_5=5/行，
         * span 由 spanFor 按 60 列栅格给出）。标题居中显示一格，不再切分标题。
         */
        private void bindCols(Holder h, HkItem item) {
            h.cols.removeAllViews();
            Context ctx = h.itemView.getContext();
            TextView tv = new TextView(ctx);
            tv.setText(titleSpan(item.getTitle()));
            tv.setTextColor(0xFF1A1D24);
            tv.setTextSize(15);
            tv.setGravity(Gravity.CENTER);
            tv.setMaxLines(1);
            tv.setLines(1);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            int pad = dp(5);
            tv.setPadding(pad, dp(10), pad, dp(10));
            // 8.83 text_2~text_5 用 item_card_rect 系列布局，带 tab_item_default 底框（分类格子有框）
            try {
                tv.setBackgroundResource(R.drawable.tab_item_default);
            } catch (Throwable ignored) {}
            tv.setMinHeight(dp(40));
            h.cols.addView(tv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            setContentClick(h, item);
        }

        /**
         * 流式胶囊按钮组：对照 8.83 bindForScrollButtonHolder，
         * 从 polyList 取整组渲染（position 为组内首个条目）。
         */
        private void bindButtons(Holder h, int position) {
            h.cols.removeAllViews();
            Integer gi = polyPositionMap.get(position);
            List<HkItem> subs = (gi == null || gi < 0 || gi >= polyList.size()) ? null : polyList.get(gi);
            if (subs == null || subs.isEmpty()) return;
            Context ctx = h.itemView.getContext();
            int parentW = h.cols.getWidth();
            if (parentW <= 0) {
                parentW = ctx.getResources().getDisplayMetrics().widthPixels - dp(16) * 2;
            }
            LinearLayout flow = new LinearLayout(ctx);
            flow.setOrientation(LinearLayout.VERTICAL);
            LinearLayout row = newButtonRow(ctx);
            int rowW = 0;
            int padH = dp(14), padV = dp(7);
            for (HkItem sub : subs) {
                TextView tv = new TextView(ctx);
                tv.setText(titleSpan(sub.getTitle()));
                tv.setTextColor(0xFF1A1D24);
                tv.setTextSize(13);
                tv.setGravity(Gravity.CENTER);
                tv.setMaxLines(1);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                tv.setPadding(padH, padV, padH, padV);
                GradientDrawable bg = new GradientDrawable();
                if (isEpSelected(sub)) {
                    // 选中金底：#D9A420 金底＋深金字（液态玻璃对差）
                    bg.setColor(0xFFD9A420);
                    bg.setCornerRadius(dp(14));
                    tv.setTextColor(0xFF7A5A00);
                } else {
                    // 液态玻璃：白透玻璃底＋细灰边（未选中态）
                    bg.setColor(0xC2FFFFFF);
                    bg.setStroke(dp(1), 0xFFE2E5EA);
                    bg.setCornerRadius(dp(14));
                    tv.setTextColor(0xFF1A1D24);
                }
                tv.setBackground(bg);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.rightMargin = dp(8);
                lp.bottomMargin = dp(8);
                tv.setLayoutParams(lp);
                tv.setOnClickListener(v -> onContentItemClick(sub));
                tv.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                int w = tv.getMeasuredWidth() + dp(8);
                if (rowW + w > parentW && rowW > 0) {
                    flow.addView(row, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    row = newButtonRow(ctx);
                    rowW = 0;
                }
                row.addView(tv);
                rowW += w;
            }
            flow.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            h.cols.addView(flow, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        private LinearLayout newButtonRow(Context ctx) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            return row;
        }

        /**
         * 8.83 ScrollButtonHolder：横向滚动按钮组。
         * 从 poly 分组取同组条目，横向排列在 HorizontalScrollView 内。
         */
        private void bindScrollButtons(Holder h, int position) {
            if (h.llButtons == null) return;
            h.llButtons.removeAllViews();
            Integer gi = polyPositionMap.get(position);
            List<HkItem> subs = (gi == null || gi < 0 || gi >= polyList.size()) ? null : polyList.get(gi);
            if (subs == null || subs.isEmpty()) {
                // 非分组场景：只渲染当前条目
                subs = new ArrayList<>();
                subs.add(items.get(position));
            }
            Context ctx = h.itemView.getContext();
            int padH = dp(14), padV = dp(7);
            for (HkItem sub : subs) {
                TextView tv = new TextView(ctx);
                tv.setText(titleSpan(sub.getTitle()));
                tv.setTextColor(0xFF1A1D24);
                tv.setTextSize(13);
                tv.setGravity(Gravity.CENTER);
                tv.setMaxLines(1);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                tv.setPadding(padH, padV, padH, padV);
                GradientDrawable bg = new GradientDrawable();
                if (isEpSelected(sub)) {
                    // 选中金底：#D9A420 金底＋深金字（液态玻璃对差）
                    bg.setColor(0xFFD9A420);
                    bg.setCornerRadius(dp(14));
                    tv.setTextColor(0xFF7A5A00);
                } else {
                    // 液态玻璃：白透玻璃底＋细灰边（未选中态）
                    bg.setColor(0xC2FFFFFF);
                    bg.setStroke(dp(1), 0xFFE2E5EA);
                    bg.setCornerRadius(dp(14));
                    tv.setTextColor(0xFF1A1D24);
                }
                tv.setBackground(bg);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.rightMargin = dp(8);
                tv.setLayoutParams(lp);
                tv.setOnClickListener(v -> onContentItemClick(sub));
                h.llButtons.addView(tv);
            }
        }

        /**
         * 8.83 FlexButtonHolder：流式按钮组（FlexboxLayout 自动换行）。
         */
        private void bindFlexButtons(Holder h, int position) {
            if (h.flexbox == null) return;
            h.flexbox.removeAllViews();
            Integer gi = polyPositionMap.get(position);
            List<HkItem> subs = (gi == null || gi < 0 || gi >= polyList.size()) ? null : polyList.get(gi);
            if (subs == null || subs.isEmpty()) {
                subs = new ArrayList<>();
                subs.add(items.get(position));
            }
            Context ctx = h.itemView.getContext();
            int padH = dp(14), padV = dp(7);
            for (HkItem sub : subs) {
                TextView tv = new TextView(ctx);
                tv.setText(titleSpan(sub.getTitle()));
                tv.setTextColor(0xFF1A1D24);
                tv.setTextSize(13);
                tv.setGravity(Gravity.CENTER);
                tv.setMaxLines(1);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                tv.setPadding(padH, padV, padH, padV);
                GradientDrawable bg = new GradientDrawable();
                if (isEpSelected(sub)) {
                    // 选中金底：#D9A420 金底＋深金字（液态玻璃对差）
                    bg.setColor(0xFFD9A420);
                    bg.setCornerRadius(dp(14));
                    tv.setTextColor(0xFF7A5A00);
                } else {
                    // 液态玻璃：白透玻璃底＋细灰边（未选中态）
                    bg.setColor(0xC2FFFFFF);
                    bg.setStroke(dp(1), 0xFFE2E5EA);
                    bg.setCornerRadius(dp(14));
                    tv.setTextColor(0xFF1A1D24);
                }
                tv.setBackground(bg);
                com.google.android.flexbox.FlexboxLayout.LayoutParams lp =
                        new com.google.android.flexbox.FlexboxLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.rightMargin = dp(8);
                lp.bottomMargin = dp(8);
                tv.setLayoutParams(lp);
                tv.setOnClickListener(v -> onContentItemClick(sub));
                h.flexbox.addView(tv);
            }
        }

        /**
         * 规则 header 横向分类 tab：对照 8.83 bindForHeaderHolder。
         * title 按 "&" 切分 tab 名，url 按 "&" 切分对应链接；点 tab → evalTab 求值后重刷；
         * ">" 按钮弹"请选择分类"弹窗。选中态：rule.url 含 tabUrl 且 firstHeader == desc。
         */
        private void bindHeaderTabs(Holder h, HkItem item) {
            h.cols.removeAllViews();
            Context ctx = h.itemView.getContext();
            String title = item.getTitle() == null ? "" : item.getTitle();
            String url = item.getUrl() == null ? "" : item.getUrl();
            String[] names = title.split("&");
            String[] urls = url.split("&");
            if (names.length == 0) return;
            final String desc = item.getDesc() == null ? "" : item.getDesc();
            final String ruleUrl = currentRule == null || currentRule.getUrl() == null ? "" : currentRule.getUrl();
            final String firstHeader = currentRule == null ? "" : currentRule.getFirstHeader();
            final List<String[]> pairs = new ArrayList<>();
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            for (int i = 0; i < names.length; i++) {
                final String name = stripHtml(names[i]).trim();
                final String tabUrl = i < urls.length ? urls[i].trim() : "";
                if (name.isEmpty()) continue;
                pairs.add(new String[]{name, tabUrl});
                TextView tv = new TextView(ctx);
                tv.setText(name);
                tv.setTextSize(14);
                tv.setGravity(Gravity.CENTER);
                tv.setSingleLine(true);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                // 8.83 选中态：rule.url 含 tabUrl 且 firstHeader == desc → tab_item_selected
                boolean selected = !tabUrl.isEmpty() && ruleUrl.contains(tabUrl) && firstHeader.equals(desc);
                if (isTabSelected(names[i])) selected = true;
                tv.setTextColor(selected ? 0xFFE8590C : 0xFF1A1D24);
                try {
                    tv.setBackgroundResource(selected ? R.drawable.tab_item_selected : R.drawable.tab_item_default);
                } catch (Throwable ignored) {}
                int padH = dp(12), padV = dp(8);
                tv.setPadding(padH, padV, padH, padV);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.rightMargin = dp(6);
                final int idx = pairs.size() - 1;
                tv.setOnClickListener(v -> onHeaderTabClick(pairs.get(idx)[1]));
                row.addView(tv, lp);
            }
            h.cols.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            // 8.83 ">" → "请选择分类"弹窗
            if (names.length > 1) {
                TextView btnExpand = new TextView(ctx);
                btnExpand.setText(">");
                btnExpand.setTextSize(16);
                btnExpand.setTextColor(0xFF666666);
                btnExpand.setGravity(Gravity.CENTER);
                try {
                    btnExpand.setBackgroundResource(R.drawable.tab_item_default);
                } catch (Throwable ignored) {}
                btnExpand.setPadding(dp(10), dp(4), dp(10), dp(4));
                LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                btnLp.leftMargin = dp(3);
                final List<String[]> finalPairs = pairs;
                btnExpand.setOnClickListener(v -> showCategoryPopup(finalPairs, -1, value -> onHeaderTabClick(value)));
                h.cols.addView(btnExpand, btnLp);
            }
            // header 条目本身不可点（8.83 点不动），只响应 tab 点击
            h.itemView.setOnClickListener(null);
            h.itemView.setClickable(false);
            h.itemView.setFocusable(false);
        }

        /** header tab 点击：8.83 onClassClick 等价 → evalTab 求值分类变量后重刷列表。 */
        private void onHeaderTabClick(String tabUrl) {
            if (tabUrl == null || tabUrl.isEmpty() || currentRule == null) return;
            new Thread(() -> {
                try {
                    getRouter().evalTab(tabUrl);
                } catch (Throwable e) {
                    ToastUtil.show(HkPageActivity.this, "页签切换失败", android.widget.Toast.LENGTH_SHORT);
                }
            }).start();
        }

        /** 头像行：圆形图 + 标题横向。 */
        private void bindAvatar(Holder h, HkItem item) {
            h.title.setText(titleSpan(item.getTitle()));
            ImgUtil.load(item.getTitle(), item.getPic(), h.cover, hkRuleUrl());
            setContentClick(h, item);
        }

        /** 图标：各 icon_* 子类型按 8.83 走独立布局；round 系列图标圆形裁剪（对照 8.83 bindCircleCropImageView）。 */
        private void bindIcon(Holder h, HkItem item) {
            if (h.title == null || h.cover == null) return; // 防 ViewHolder 类型错配
            h.title.setText(titleSpan(item.getTitle()));
            int vt = h.getItemViewType();
            if (vt == T_ICON_ROUND || vt == T_ICON_ROUND_SMALL || vt == T_ICON_2_ROUND) {
                ImgUtil.loadCircle(item.getTitle(), item.getPic(), h.cover, hkRuleUrl());
            } else {
                ImgUtil.load(item.getTitle(), item.getPic(), h.cover, hkRuleUrl());
            }
            if (vt == T_ICON_1_LEFT_PIC && h.desc != null) {
                String d = stripHtml(item.getDesc());
                h.desc.setText(d);
                h.desc.setVisibility(TextUtils.isEmpty(d) ? View.GONE : View.VISIBLE);
            }
            setContentClick(h, item);
        }

        /** 图文行：text_icon，左文字右图标（对照 8.83 item_text_icon）。 */
        private void bindTextIcon(Holder h, HkItem item) {
            h.title.setText(titleSpan(item.getTitle()));
            ImgUtil.load(item.getTitle(), item.getPic(), h.cover, hkRuleUrl());
            setContentClick(h, item);
        }

        /** 卡片：card_pic_* 系列，封面+标题+底部 overlay（对照 8.83 item_card_rect）。 */
        private void bindCard(Holder h, HkItem item) {
            h.title.setText(titleSpan(item.getTitle()));
            String d = stripHtml(item.getDesc());
            if (h.overlay != null) {
                h.overlay.setText(d);
                h.overlay.setVisibility(TextUtils.isEmpty(d) ? View.GONE : View.VISIBLE);
            }
            ImgUtil.load(item.getTitle(), item.getPic(), h.cover, hkRuleUrl());
            setContentClick(h, item);
        }

        /** 图片：pic_1系列全宽16:9；pic_2半宽16:9；pic_3/pic_3_square按列宽正方形。 */
        private void bindPic(Holder h, HkItem item) {
            String ct = colTypeOf(item);
            ViewGroup.LayoutParams plp = h.cover.getLayoutParams();
            if (plp != null) {
                DisplayMetrics dm = h.itemView.getContext().getResources().getDisplayMetrics();
                int W = dm.widthPixels - dp(16) * 2;
                if (ct.startsWith("pic_3")) {
                    plp.height = (W - dp(12) * 2) / 3; // 3列正方形
                } else if (ct.startsWith("pic_2")) {
                    int itemW = (W - dp(12)) / 2;
                    plp.height = itemW * 9 / 16;
                } else {
                    plp.height = picH; // 16:9 全宽
                }
                h.cover.setLayoutParams(plp);
            }
            ImgUtil.load(item.getTitle(), item.getPic(), h.cover, hkRuleUrl());
            h.cover.setContentDescription(stripHtml(item.getTitle()));
            setContentClick(h, item);
        }

        /** 横向图文：左图右文。 */
        private void bindMovie(Holder h, HkItem item) {
            if (h.title == null || h.desc == null || h.cover == null) return; // 防 ViewHolder 类型错配
            h.title.setText(titleSpan(item.getTitle()));
            String d = stripHtml(item.getDesc());
            h.desc.setText(d);
            h.desc.setVisibility(TextUtils.isEmpty(d) ? View.GONE : View.VISIBLE);
            ImgUtil.load(item.getTitle(), item.getPic(), h.cover, hkRuleUrl());
            setContentClick(h, item);
        }

        /**
         * 竖海报行（D3）：对照 8.83 item_movie_one_col_vertical_pic(_blur)。
         * 全宽左 95x130dp 竖海报右文；blur=true 时背景加毛玻璃（8.83 的 blur 头图）。
         */
        private void bindMovieVertical(Holder h, HkItem item, boolean blur) {
            if (h.title == null || h.desc == null || h.cover == null) return;
            h.title.setText(titleSpan(item.getTitle()));
            String d = stripHtml(item.getDesc());
            h.desc.setText(d);
            h.desc.setVisibility(TextUtils.isEmpty(d) ? View.GONE : View.VISIBLE);
            ImgUtil.load(item.getTitle(), item.getPic(), h.cover, hkRuleUrl());
            if (blur) {
                View bg = h.itemView.findViewById(R.id.iv_blur_bg);
                if (bg instanceof android.widget.ImageView) {
                    ImgUtil.load(item.getTitle(), item.getPic(), (android.widget.ImageView) bg, hkRuleUrl());
                    try {
                        com.xingchen.tv.theme.FrostedGlass.blurBackground(bg);
                    } catch (Throwable ignored) {
                    }
                }
            }
            setContentClick(h, item);
        }

        /**
         * 8.83 原版详情页头部（item_movie_one_col）：标题左 + 封面右（130×90dp，圆角 8dp）。
         * 标题 16sp 最多 2 行省略，描述最多 2 行底部对齐。点击走通用条目点击。
         */
        private void bindDetailHeader(Holder h, HkItem item) {
            // 白带修复（从简）：详情头背景透明，透出页面顶部渐变底色（#F4F6F8），不再盖白；
            // 原玻璃底在真机渲染为实白，与页顶接不上，按潇洒哥定案只换色、不动布局
            h.itemView.setBackground(null);
            if (h.title == null || h.cover == null) return;
            h.title.setText(titleSpan(item.getTitle()));
            String d = stripHtml(item.getDesc());
            if (h.desc != null) {
                h.desc.setText(d);
                h.desc.setVisibility(TextUtils.isEmpty(d) ? View.GONE : View.VISIBLE);
            }
            ImgUtil.load(item.getTitle(), item.getPic(), h.cover, hkRuleUrl());
            setContentClick(h, item);
        }

        /** 输入框行：hint 取标题；搜索键触发且有 url 时走点击处理。 */
        private void bindInput(Holder h, HkItem item) {
            h.input.setHint(stripHtml(item.getTitle()));
            h.input.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                    if (!TextUtils.isEmpty(item.getUrl())) {
                        // 官方语义（ArticleListFragment.clickItem）：input 条目把用户输入的文本
                        // 作为 input 全局变量注入后求值，而非 pageUrl。否则口令验证永远失败。
                        String key = h.input.getText() == null ? "" : h.input.getText().toString();
                        try {
                            getRouter().getEngine().getJsRuntime().setNextInputOverride(key);
                        } catch (Throwable e) {
                            // 输入没注入成功不许照常点进去：规则收不到输入只会静默无反应
                            ToastUtil.show(HkPageActivity.this, "输入传递失败，请重试",
                                    android.widget.Toast.LENGTH_SHORT);
                            return true;
                        }
                        onContentItemClick(item);
                    }
                    return true;
                }
                return false;
            });
        }

        /** T_WEB 行全承载销毁（宿主 onDestroy 调用）：holder 的 WebView 随页销毁。 */
        void destroyWebHolder() {
            clearPageFloat();
            if (pageWebHolder != null && pageWebHolder.getWebkitHolder() != null) {
                try {
                    pageWebHolder.getWebkitHolder().onDestroy();
                } catch (Throwable ignored) {
                }
            }
            pageWebHolder = null;
            pageWebKey = null;
            pageWebDefaultUa = null;
        }

        @Override
        public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
            pageWebList = recyclerView;
        }

        @Override
        public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
            super.onDetachedFromRecyclerView(recyclerView);
            if (pageWebList == recyclerView) pageWebList = null;
        }

        /** 页级 FLOAT 容器懒建：插到列表同级、列表之前（对应片段面 float_container>webview_container 位于列表上方的形态）。 */
        private FrameLayout ensurePageFloatBox() {
            if (pageWebFloatBox != null) return pageWebFloatBox;
            if (pageWebList == null || !(pageWebList.getParent() instanceof ViewGroup)) return null;
            ViewGroup parent = (ViewGroup) pageWebList.getParent();
            pageWebFloatBox = new FrameLayout(HkPageActivity.this);
            parent.addView(pageWebFloatBox, parent.indexOfChild(pageWebList),
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));
            pageWebFloatBox.setVisibility(View.GONE);
            return pageWebFloatBox;
        }

        /**
         * FLOAT 挂载/定高（3-A，照片段 HkArticleFragment.updateWebView 的 FLOAT 支与 applyFloatWebViewHeight）：
         * 页级容器高度＝HkWebViewHolder.getHeightByExtra(url, holder.extra)，负数哨兵（-1 全高/-2 自适应等）原样透传、
         * 正数 dp→px，并回写 holder 高度；WebView 未挂入时从旧父级摘下挂入，已挂入时只对齐高度。
         */
        private void mountPageFloat(android.webkit.WebView wv) {
            FrameLayout box = ensurePageFloatBox();
            if (box == null || pageWebHolder == null) return;
            int heightDp = HkWebViewHolder.getHeightByExtra(HkPageActivity.this,
                    pageWebHolder.getUrl(), pageWebHolder.getExtra());
            int heightPx = heightDp < 0 ? heightDp : dp(heightDp);
            ViewGroup.LayoutParams lp = box.getLayoutParams();
            if (box.getChildCount() <= 0) {
                lp.height = heightPx;
                box.setLayoutParams(lp);
                pageWebHolder.setHeight(lp.height);
                if (wv.getParent() != null) ((ViewGroup) wv.getParent()).removeView(wv);
                box.addView(wv, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            } else if (lp.height != heightPx) {
                lp.height = heightPx;
                box.setLayoutParams(lp);
                pageWebHolder.setHeight(heightPx);
            }
            box.setVisibility(View.VISIBLE);
        }

        /** 非 FLOAT 时清空页级容器（照片段 updateWebView 的 else 支：webViewContainer.removeAllViews）。 */
        private void clearPageFloat() {
            if (pageWebFloatBox == null) return;
            if (pageWebFloatBox.getChildCount() > 0) pageWebFloatBox.removeAllViews();
            pageWebFloatBox.setVisibility(View.GONE);
        }

        /**
         * x5_webview_single / video 行内网页（双承载收归后）：不再裸 loadUrl——
         * 条目 url 全串（不截断）、desc、extra（含 extra.js/ua）经 configureWebHolder
         * 组装进 HkWebViewHolder，由 HkWebkitHolder 全承载渲染（全桥＋loadAllJs 两拍
         * ＋extra.js 注入）；行内容器只作挂载点。点击整行开同承载弹窗（带条目 extra）。
         */
        private void bindWeb(Holder h, HkItem item) {
            if (h.webBox == null) return;
            ensurePageWeb(item);
            android.webkit.WebView wv = pageWebHolder == null ? null : pageWebHolder.getWebView();
            if (wv != null) {
                // 模式消费（3-A，照片段面现形对位）：FLOAT 条目 WebView 不进行内行——行内只清不挂
                // （HkArticleAdapter.bindForX5WebViewHolder 的 FLOAT 支），改挂页级容器并按
                // getHeightByExtra 定高（HkArticleFragment.updateWebView 的 FLOAT 支）；
                // 非 FLOAT 先清页级容器（updateWebView 的 else 支），再按 getHeightByExtra(url, desc)
                // 定行高后挂进行内（bindForX5WebViewHolder 的非 FLOAT 支），不再恒 420dp。
                if (pageWebHolder.getMode() == HkWebViewHolder.Mode.FLOAT) {
                    h.webBox.removeAllViews();
                    mountPageFloat(wv);
                } else {
                    clearPageFloat();
                    int heightDp = HkWebViewHolder.getHeightByExtra(HkPageActivity.this,
                            pageWebHolder.getUrl(), item.getDesc());
                    int heightPx = heightDp < 0 ? heightDp : dp(heightDp);
                    ViewGroup.LayoutParams blp = h.webBox.getLayoutParams();
                    if (blp != null && blp.height != heightPx) {
                        blp.height = heightPx;
                        h.webBox.setLayoutParams(blp);
                    }
                    pageWebHolder.setHeight(heightPx);
                    if (wv.getParent() instanceof ViewGroup) {
                        ((ViewGroup) wv.getParent()).removeView(wv);
                    }
                    h.webBox.removeAllViews();
                    h.webBox.addView(wv, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                }
            }
            h.itemView.setOnClickListener(v -> {
                int pos = h.getBindingAdapterPosition();
                if (pos < 0 || pos >= items.size()) return;
                HkItem cur = items.get(pos);
                String target = cur.getUrl() == null ? "" : cur.getUrl();
                if (target.startsWith("http")) showWebViewDialog(target, cur);
            });
        }

        /** 条目 → 页级 holder 组装＋地址同步：键未变直接回；已建承载换条目时新 extra/ua 推入 holder 后按地址加载/刷新（照片段 updateWebView 的地址同步口径）；UA 推送对称——带 UA 设新值、无 UA 恢复建面缺省（3-B 续，片段面仍单向、此为本面复用 holder 的独有收口）。 */
        private void ensurePageWeb(HkItem item) {
            String ct = colTypeOf(item);
            String url = item.getUrl() == null ? "" : item.getUrl();
            String desc = item.getDesc() == null ? "" : item.getDesc();
            String extra = item.getExtra() == null ? "" : item.getExtra();
            String ruleKey = currentRule == null ? "" : currentRule.getTitle();
            String key = ruleKey + "\u0001" + ct + "\u0001" + url + "\u0001" + desc + "\u0001" + extra;
            if (pageWebHolder != null && key.equals(pageWebKey)) return;
            if (pageWebHolder == null) pageWebHolder = new HkWebViewHolder();
            // 每次组装都重接线：桥引擎/拦截求值捕获的是当下规则运行时，规则切换后不许还绑旧运行时
            //（setBridgeEngine/setUrlInterceptorEvaluator 在 holder 已建时都会转发进 HkWebkitHolder）。
            wireWebHolder(pageWebHolder);
            pageWebKey = key;
            configureWebHolder(pageWebHolder, ct, url, desc, extra);
            if (pageWebHolder.getWebkitHolder() == null) {
                pageWebHolder.ensureWebView(HkPageActivity.this);
                // 建面缺省 UA 抓存（3-B 续）：ensureWebView 同步返回时 settings UA 尚未被
                // 任何条目 UA 覆写（initWebView 只读存 systemUA、不写 UA；条目 UA 推送只在
                // 下方已建面支；loadUrl 的 onPageStarted 回调在本主线程任务后才跑），此刻
                // 读到的即该 WebView 建面时的缺省值。建面失败（wv 为 null）保持 null。
                android.webkit.WebView freshWv = pageWebHolder.getWebView();
                if (freshWv != null) {
                    try {
                        pageWebDefaultUa = freshWv.getSettings().getUserAgentString();
                    } catch (Throwable ignored) {
                    }
                }
                return;
            }
            HkWebkitHolder kh = pageWebHolder.getWebkitHolder();
            kh.setX5Extra(pageWebHolder.getX5Extra());
            kh.setUrl(pageWebHolder.getUrl());
            android.webkit.WebView wv = kh.getWebView();
            if (wv == null) return;
            // UA 对称恢复（3-B 续）：与新开 holder 等价——新条目带 UA 设其 UA、无 UA 把
            // settings UA 恢复为建面缺省（抓存值），不再单向残留旧条目 UA。本体
            // HkWebkitHolder.checkUa 的回落终点是 useUa/systemUA、不认条目 extra UA
            // （其旧 extra UA 非空门已按官摘除，C1 复核订正），整页打开路不跨条目复用
            // 同一 WebView，此串台是本页级列表复用 holder 独有，故只收本面。
            HkX5Extra x5 = pageWebHolder.getX5Extra();
            if (x5 != null && !TextUtils.isEmpty(x5.getUa())) {
                try {
                    wv.getSettings().setUserAgentString(x5.getUa());
                } catch (Throwable ignored) {
                }
            } else if (!TextUtils.isEmpty(pageWebDefaultUa)) {
                try {
                    wv.getSettings().setUserAgentString(pageWebDefaultUa);
                } catch (Throwable ignored) {
                }
            }
            boolean videoItem = pageWebHolder.isVideoItem()
                    && pageWebHolder.getVideoUrl() != null && !pageWebHolder.getVideoUrl().isEmpty();
            String target = videoItem ? pageWebHolder.getVideoUrl() : pageWebHolder.getUrl();
            if (TextUtils.isEmpty(target)) return;
            if (!target.equals(wv.getUrl())) {
                java.util.Map<String, String> hdrs = videoItem ? pageWebHolder.getVideoHeaders() : null;
                if (hdrs != null && !hdrs.isEmpty()) wv.loadUrl(target, hdrs);
                else wv.loadUrl(target);
            } else {
                wv.reload();
            }
        }

        /**
         * 8.83：无 URL 的纯静态文本（如"使用前须知"）不可点击，无任何反馈。
         * 有 URL 的条目才绑点击（含空链接的异常情况由 onContentItemClick 处 toast）。
         * 点击时按 position 重取条目（8.83 clickItem src 2859 同款，防复用错位）；
         * 越界检查照抄 8.83 clickItem(src 2914)：position<0 或 >=size 直接 return。
         */
        private void setContentClick(Holder h, HkItem item) {
            String u = item == null || item.getUrl() == null ? "" : item.getUrl().trim();
            if (u.isEmpty()) {
                h.itemView.setOnClickListener(null);
                h.itemView.setOnLongClickListener(null);
                h.itemView.setClickable(false);
                h.itemView.setFocusable(false);
                return;
            }
            h.itemView.setClickable(true);
            h.itemView.setFocusable(true);
            h.itemView.setOnClickListener(v -> {
                try {
                    int pos = h.getBindingAdapterPosition();
                    if (pos < 0 || pos >= items.size() || currentRule == null) return;
                    onContentItemClick(items.get(pos));
                } catch (Exception e) {
                    android.util.Log.e("HkPage", "setContentClick failed", e);
                }
            });
            // 8.83 ArticleListFragment 长按菜单（dealLongMenuClick）：复制链接/访问链接/下载资源等
            h.itemView.setOnLongClickListener(v -> {
                try {
                    int pos = h.getBindingAdapterPosition();
                    if (pos < 0 || pos >= items.size()) return true;
                    showContentLongMenu(items.get(pos));
                } catch (Exception e) {
                    android.util.Log.e("HkPage", "longClick failed", e);
                }
                return true;
            });
        }
    }

    /**
     * 8.83 dealLongMenuClick：内容条目长按菜单。
     * 菜单项对照 8.83：复制链接 / 访问链接 / 下载资源 / 自由复制。
     */
    private void showContentLongMenu(HkItem item) {
        if (item == null) return;
        String url = item.getUrl() == null ? "" : item.getUrl().trim();
        String title = stripHtml(item.getTitle() == null ? "" : item.getTitle());
        String[] labels = {"复制链接", "访问链接", "下载资源", "自由复制"};
        HkDialogs.cards(this,
                title.isEmpty() ? "操作" : (title.length() > 20 ? title.substring(0, 20) + "…" : title),
                labels, null, (d, which) -> {
                    switch (which) {
                        case 0: { // 复制链接
                            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            cm.setPrimaryClip(ClipData.newPlainText("hk", url));
                            ToastUtil.show(this, "已复制链接", android.widget.Toast.LENGTH_SHORT);
                            break;
                        }
                        case 1: { // 访问链接：在系统浏览器打开 http(s) 链接
                            if (url.startsWith("http")) {
                                try {
                                    startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
                                } catch (Throwable t) {
                                    ToastUtil.show(this, "无法打开链接", android.widget.Toast.LENGTH_SHORT);
                                }
                            } else {
                                ToastUtil.show(this, "当前页面不支持此操作", android.widget.Toast.LENGTH_SHORT);
                            }
                            break;
                        }
                        case 2: { // 下载资源：走 download:// 动作
                            if (!url.isEmpty()) {
                                HkItem nav = new HkItem();
                                nav.setTitle(title);
                                nav.setUrl("download://" + url);
                                handleActionUrl("download://" + url, nav);
                            }
                            break;
                        }
                        case 3: { // 自由复制：复制标题+链接
                            ClipboardManager cm2 = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            cm2.setPrimaryClip(ClipData.newPlainText("hk", title + "\n" + url));
                            ToastUtil.show(this, "已复制", android.widget.Toast.LENGTH_SHORT);
                            break;
                        }
                    }
                });
    }

    private static class ChipAdapter extends RecyclerView.Adapter<ChipAdapter.Holder> {
        interface OnPick {
            void onPick(String value);
        }

        private final List<String[]> pairs;
        private int selected;
        private final OnPick listener;
        private int dropTarget = -1;
        private boolean goldMode = false;

        ChipAdapter(List<String[]> pairs, int selected, OnPick listener) {
            this.pairs = pairs;
            this.selected = selected;
            this.listener = listener;
        }

        /** 金色主题模式：小程序切换 tabs 用淡金底，与分类 pills（白底/绿选中）颜色区分，一眼看出是两排不同的东西。 */
        void setGoldMode(boolean goldMode) {
            this.goldMode = goldMode;
        }

        /** 拖拽投放高亮位置（-1=无），供编辑模式拖卡片到分组 pills 用。 */
        boolean setDropTarget(int pos) {
            if (dropTarget == pos) return false;
            int old = dropTarget;
            dropTarget = pos;
            if (old >= 0 && old < pairs.size()) notifyItemChanged(old);
            if (pos >= 0 && pos < pairs.size()) notifyItemChanged(pos);
            return true;
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
            boolean drop = position == dropTarget;
            if (goldMode) {
                // 金色主题：小程序切换 tabs 在圆角框内，透明底 + 金色系文字，与分类 pills 区分
                if (h.card != null) {
                    h.card.setCardBackgroundColor(android.graphics.Color.TRANSPARENT);
                    h.card.setStrokeWidth(0);
                }
                h.chip.setBackgroundResource(0);
                h.chip.setTypeface(null, sel ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                h.chip.setTextColor(sel ? 0xFF8A6D00 : 0xFF8A8F98);
            } else if (drop) {
                // 拖拽投放目标：金色加粗 + 选中底，提示松手即进该分组
                if (h.card != null) {
                    h.card.setCardBackgroundColor(0xC2FFFFFF);
                    float d2 = h.itemView.getContext().getResources().getDisplayMetrics().density;
                    h.card.setStrokeWidth((int) (1 * d2 + 0.5f));
                    h.card.setStrokeColor(android.content.res.ColorStateList.valueOf(0xFFE1E5EB));
                }
                h.chip.setTextColor(0xFFB8890F);
                h.chip.setTypeface(null, android.graphics.Typeface.BOLD);
                h.chip.setBackgroundResource(R.drawable.hk_ripple_glass_selected);
            } else {
                // 恢复卡片默认样式（防止从 goldMode 复用过来的 view 残留透明底）
                if (h.card != null) {
                    h.card.setCardBackgroundColor(0xC2FFFFFF);
                    float d = h.itemView.getContext().getResources().getDisplayMetrics().density;
                    h.card.setStrokeWidth((int) (1 * d + 0.5f));
                    h.card.setStrokeColor(android.content.res.ColorStateList.valueOf(0xFFE1E5EB));
                }
                h.chip.setTypeface(null, android.graphics.Typeface.NORMAL);
                // 液态玻璃 pills：选中=奶油渐变胶囊＋金下划线＋金字 #8A6400（照 v2 样张），未选中=白透玻璃 pill＋深灰字
                h.chip.setTextColor(sel ? 0xFF8A6400 : 0xFF222222);
                h.chip.setBackgroundResource(sel ? R.drawable.hk_chip_selected : R.drawable.hk_ripple_glass);
            }
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
}
